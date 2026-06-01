package com.example.multiapp.common.ratelimit;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryRateLimiter implements RateLimiter {
    private static final String NO_TENANT = "no-tenant";

    private final RateLimitProperties properties;
    private final RateLimitPolicyMatcher matcher;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger checksSinceCleanup = new AtomicInteger();

    public InMemoryRateLimiter(RateLimitProperties properties, RateLimitPolicyMatcher matcher) {
        this.properties = properties;
        this.matcher = matcher;
    }

    @Override
    public RateLimitDecision check(RateLimitRequest request) {
        RateLimitProperties.Policy policy = matcher.match(request);
        validate(policy);
        long nowNanos = System.nanoTime();
        String key = bucketKey(policy, request);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> Bucket.full(policy, nowNanos));
        RateLimitDecision decision = bucket.consume(policy, nowNanos);
        cleanupOccasionally(nowNanos);
        return decision;
    }

    int bucketCount() {
        return buckets.size();
    }

    private String bucketKey(RateLimitProperties.Policy policy, RateLimitRequest request) {
        String tenant = request.tenantId() == null || request.tenantId().isBlank()
                ? NO_TENANT : request.tenantId().strip().toLowerCase(Locale.ROOT);
        String identity = request.identity() == null || request.identity().isBlank()
                ? "unknown" : request.identity().strip().toLowerCase(Locale.ROOT);
        return policy.getName() + "|" + tenant + "|" + identity;
    }

    private void cleanupOccasionally(long nowNanos) {
        int checks = checksSinceCleanup.incrementAndGet();
        if (checks < 512 && buckets.size() <= properties.getMaxBuckets()) {
            return;
        }
        if (!checksSinceCleanup.compareAndSet(checks, 0)) {
            return;
        }
        long idleTtl = safeDurationNanos(properties.getBucketIdleTtl(), Duration.ofMinutes(10));
        buckets.entrySet().removeIf(entry -> entry.getValue().isRemovable(nowNanos, idleTtl)
                || buckets.size() > properties.getMaxBuckets() && entry.getValue().isIdle(nowNanos, idleTtl / 2));
    }

    private void validate(RateLimitProperties.Policy policy) {
        if (policy.getName() == null || policy.getName().isBlank()) {
            throw new IllegalStateException("Rate limit policy name must not be blank");
        }
        if (policy.getCapacity() <= 0 || policy.getRefillTokens() <= 0) {
            throw new IllegalStateException("Rate limit policy capacity/refillTokens must be positive: "
                    + policy.getName());
        }
        if (policy.getRefillPeriod() == null || policy.getRefillPeriod().isZero()
                || policy.getRefillPeriod().isNegative()) {
            throw new IllegalStateException("Rate limit policy refillPeriod must be positive: "
                    + policy.getName());
        }
    }

    private static long safeDurationNanos(Duration duration, Duration fallback) {
        Duration value = duration == null || duration.isZero() || duration.isNegative() ? fallback : duration;
        return value.toNanos();
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;
        private long lastSeenNanos;
        private long blockedUntilNanos;

        private static Bucket full(RateLimitProperties.Policy policy, long nowNanos) {
            Bucket bucket = new Bucket();
            bucket.tokens = policy.getCapacity();
            bucket.lastRefillNanos = nowNanos;
            bucket.lastSeenNanos = nowNanos;
            return bucket;
        }

        private synchronized RateLimitDecision consume(RateLimitProperties.Policy policy, long nowNanos) {
            lastSeenNanos = nowNanos;
            if (blockedUntilNanos > nowNanos) {
                return RateLimitDecision.rejected(policy.getName(), policy.getCapacity(),
                        Duration.ofNanos(blockedUntilNanos - nowNanos));
            }

            refill(policy, nowNanos);
            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return RateLimitDecision.allowed(policy.getName(), policy.getCapacity(), (int) Math.floor(tokens));
            }

            Duration penalty = policy.getPenaltyDuration();
            blockedUntilNanos = nowNanos + safeDurationNanos(penalty, Duration.ofSeconds(30));
            return RateLimitDecision.rejected(policy.getName(), policy.getCapacity(),
                    Duration.ofNanos(blockedUntilNanos - nowNanos));
        }

        private void refill(RateLimitProperties.Policy policy, long nowNanos) {
            long elapsed = nowNanos - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double refillPerNano = (double) policy.getRefillTokens() / policy.getRefillPeriod().toNanos();
            tokens = Math.min(policy.getCapacity(), tokens + (elapsed * refillPerNano));
            lastRefillNanos = nowNanos;
        }

        private synchronized boolean isRemovable(long nowNanos, long idleTtlNanos) {
            return isIdle(nowNanos, idleTtlNanos)
                    && blockedUntilNanos <= nowNanos
                    && tokens >= 1.0d;
        }

        private synchronized boolean isIdle(long nowNanos, long idleTtlNanos) {
            return nowNanos - lastSeenNanos > idleTtlNanos;
        }
    }
}
