package com.example.multiapp.common.ratelimit;

import org.springframework.util.AntPathMatcher;

import java.util.Locale;

public class RateLimitPolicyMatcher {
    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitPolicyMatcher(RateLimitProperties properties) {
        this.properties = properties;
    }

    public boolean isExcluded(String path) {
        String normalizedPath = normalizePath(path);
        return properties.getExcludedPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, normalizedPath));
    }

    public RateLimitProperties.Policy match(RateLimitRequest request) {
        String method = request.method().toUpperCase(Locale.ROOT);
        String path = normalizePath(request.path());
        return properties.getPolicies().stream()
                .filter(policy -> methodMatches(policy, method))
                .filter(policy -> pathMatches(policy, path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No rate limit policy matched request"));
    }

    private boolean methodMatches(RateLimitProperties.Policy policy, String method) {
        return policy.getMethods().stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(value -> "*".equals(value) || value.equals(method));
    }

    private boolean pathMatches(RateLimitProperties.Policy policy, String path) {
        return policy.getPathPatterns().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.strip();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
