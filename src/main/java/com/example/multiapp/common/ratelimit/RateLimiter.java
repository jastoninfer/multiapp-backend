package com.example.multiapp.common.ratelimit;

public interface RateLimiter {
    RateLimitDecision check(RateLimitRequest request);
}
