package com.example.multiapp.common.ratelimit;

public record RateLimitRequest(
        String method,
        String path,
        String tenantId,
        String identity
) {
}
