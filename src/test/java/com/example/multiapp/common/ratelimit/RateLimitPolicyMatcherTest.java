package com.example.multiapp.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyMatcherTest {

    @Test
    void matchesFirstSpecificPolicyBeforeGenericWritePolicy() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitPolicyMatcher matcher = new RateLimitPolicyMatcher(properties);

        RateLimitProperties.Policy upload = matcher.match(new RateLimitRequest(
                "POST",
                "/tickets/30000000-0000-0000-0000-000000000001/attachments/batch",
                "tenant-1",
                "jwt:user-1"
        ));
        RateLimitProperties.Policy write = matcher.match(new RateLimitRequest(
                "PATCH",
                "/tickets/30000000-0000-0000-0000-000000000001",
                "tenant-1",
                "jwt:user-1"
        ));

        assertThat(upload.getName()).isEqualTo("attachment-upload");
        assertThat(write.getName()).isEqualTo("write-api");
    }

    @Test
    void supportsConfiguredPolicyOverrides() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setPolicies(List.of(RateLimitProperties.Policy.of(
                "custom-contacts",
                List.of("GET"),
                List.of("/contacts/**"),
                3,
                3,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5)
        )));
        RateLimitPolicyMatcher matcher = new RateLimitPolicyMatcher(properties);

        RateLimitProperties.Policy policy = matcher.match(new RateLimitRequest(
                "GET",
                "/contacts",
                "tenant-1",
                "jwt:user-1"
        ));

        assertThat(policy.getName()).isEqualTo("custom-contacts");
    }
}
