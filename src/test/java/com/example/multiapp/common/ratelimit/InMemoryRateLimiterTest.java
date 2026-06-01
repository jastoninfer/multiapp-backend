package com.example.multiapp.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    @Test
    void rejectsAfterPolicyCapacityAndReturnsRetryAfter() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setPolicies(List.of(RateLimitProperties.Policy.of(
                "strict-write",
                List.of("POST"),
                List.of("/tickets"),
                2,
                2,
                Duration.ofMinutes(1),
                Duration.ofSeconds(20)
        )));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(properties,
                new RateLimitPolicyMatcher(properties));
        RateLimitRequest request = new RateLimitRequest("POST", "/tickets",
                "tenant-1", "jwt:user-1");

        assertThat(limiter.check(request).allowed()).isTrue();
        assertThat(limiter.check(request).allowed()).isTrue();

        RateLimitDecision rejected = limiter.check(request);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.policyName()).isEqualTo("strict-write");
        assertThat(rejected.retryAfter()).isGreaterThan(Duration.ofSeconds(0));
    }

    @Test
    void usesSeparateBucketsForDifferentIdentities() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setPolicies(List.of(RateLimitProperties.Policy.of(
                "per-user",
                List.of("GET"),
                List.of("/tickets/**"),
                1,
                1,
                Duration.ofMinutes(1),
                Duration.ofSeconds(10)
        )));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(properties,
                new RateLimitPolicyMatcher(properties));

        assertThat(limiter.check(new RateLimitRequest("GET", "/tickets/1",
                "tenant-1", "jwt:user-1")).allowed()).isTrue();
        assertThat(limiter.check(new RateLimitRequest("GET", "/tickets/2",
                "tenant-1", "jwt:user-1")).allowed()).isFalse();
        assertThat(limiter.check(new RateLimitRequest("GET", "/tickets/2",
                "tenant-1", "jwt:user-2")).allowed()).isTrue();
    }
}
