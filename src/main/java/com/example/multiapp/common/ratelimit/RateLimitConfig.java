package com.example.multiapp.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
    private static final int AFTER_SPRING_SECURITY_FILTER_ORDER = -90;

    @Bean
    public RateLimitPolicyMatcher rateLimitPolicyMatcher(RateLimitProperties properties) {
        return new RateLimitPolicyMatcher(properties);
    }

    @Bean
    public RateLimiter rateLimiter(RateLimitProperties properties, RateLimitPolicyMatcher matcher) {
        return new InMemoryRateLimiter(properties, matcher);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimitProperties properties,
            RateLimitPolicyMatcher matcher,
            RateLimiter rateLimiter
    ) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RateLimitFilter(properties, matcher, rateLimiter, new ObjectMapper()));
        bean.setOrder(AFTER_SPRING_SECURITY_FILTER_ORDER);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
