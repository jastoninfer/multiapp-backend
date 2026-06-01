package com.example.multiapp.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int maxBuckets = 20_000;
    private Duration bucketIdleTtl = Duration.ofMinutes(10);
    private List<String> excludedPaths = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    ));
    private List<Policy> policies = defaultPolicies();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxBuckets() {
        return maxBuckets;
    }

    public void setMaxBuckets(int maxBuckets) {
        this.maxBuckets = maxBuckets;
    }

    public Duration getBucketIdleTtl() {
        return bucketIdleTtl;
    }

    public void setBucketIdleTtl(Duration bucketIdleTtl) {
        this.bucketIdleTtl = bucketIdleTtl;
    }

    public List<String> getExcludedPaths() {
        return excludedPaths;
    }

    public void setExcludedPaths(List<String> excludedPaths) {
        this.excludedPaths = excludedPaths == null ? List.of() : excludedPaths;
    }

    public List<Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<Policy> policies) {
        this.policies = policies == null || policies.isEmpty() ? defaultPolicies() : policies;
    }

    private static List<Policy> defaultPolicies() {
        return List.of(
                Policy.of("claim-consume", List.of(HttpMethod.POST.name()), List.of("/contacts/claim"),
                        5, 5, Duration.ofMinutes(1), Duration.ofMinutes(1)),
                Policy.of("claim-issue", List.of(HttpMethod.POST.name()), List.of("/contacts/*/claim-codes"),
                        10, 10, Duration.ofMinutes(1), Duration.ofMinutes(1)),
                Policy.of("attachment-upload", List.of(HttpMethod.POST.name()), List.of("/tickets/*/attachments/**"),
                        10, 10, Duration.ofMinutes(1), Duration.ofMinutes(1)),
                Policy.of("write-api", List.of("POST", "PUT", "PATCH", "DELETE"), List.of("/**"),
                        40, 40, Duration.ofMinutes(1), Duration.ofSeconds(30)),
                Policy.of("read-api", List.of(HttpMethod.GET.name()), List.of("/**"),
                        180, 180, Duration.ofMinutes(1), Duration.ofSeconds(15)),
                Policy.of("default-api", List.of("*"), List.of("/**"),
                        120, 120, Duration.ofMinutes(1), Duration.ofSeconds(30))
        );
    }

    public static class Policy {
        private String name;
        private List<String> methods = List.of("*");
        private List<String> pathPatterns = List.of("/**");
        private int capacity = 120;
        private int refillTokens = 120;
        private Duration refillPeriod = Duration.ofMinutes(1);
        private Duration penaltyDuration = Duration.ofSeconds(30);

        public static Policy of(String name, List<String> methods, List<String> pathPatterns,
                                int capacity, int refillTokens, Duration refillPeriod,
                                Duration penaltyDuration) {
            Policy policy = new Policy();
            policy.name = name;
            policy.methods = methods;
            policy.pathPatterns = pathPatterns;
            policy.capacity = capacity;
            policy.refillTokens = refillTokens;
            policy.refillPeriod = refillPeriod;
            policy.penaltyDuration = penaltyDuration;
            return policy;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods == null || methods.isEmpty() ? List.of("*") : methods;
        }

        public List<String> getPathPatterns() {
            return pathPatterns;
        }

        public void setPathPatterns(List<String> pathPatterns) {
            this.pathPatterns = pathPatterns == null || pathPatterns.isEmpty()
                    ? List.of("/**") : pathPatterns;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(int refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }

        public Duration getPenaltyDuration() {
            return penaltyDuration;
        }

        public void setPenaltyDuration(Duration penaltyDuration) {
            this.penaltyDuration = penaltyDuration;
        }
    }
}
