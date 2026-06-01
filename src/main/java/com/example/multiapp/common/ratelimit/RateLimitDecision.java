package com.example.multiapp.common.ratelimit;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        String policyName,
        int limit,
        int remaining,
        Duration retryAfter
) {
    public static RateLimitDecision allowed(String policyName, int limit, int remaining) {
        return new RateLimitDecision(true, policyName, limit, remaining, Duration.ZERO);
    }

    public static RateLimitDecision rejected(String policyName, int limit, Duration retryAfter) {
        return new RateLimitDecision(false, policyName, limit, 0, retryAfter);
    }
}
