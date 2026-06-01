package com.example.multiapp.common.ratelimit;

import com.example.multiapp.common.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

public class RateLimitFilter extends OncePerRequestFilter {
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final RateLimitProperties properties;
    private final RateLimitPolicyMatcher matcher;
    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitProperties properties, RateLimitPolicyMatcher matcher,
                           RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.properties = properties;
        this.matcher = matcher;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return matcher.isExcluded(pathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RateLimitRequest rateLimitRequest = new RateLimitRequest(
                request.getMethod(),
                pathWithinApplication(request),
                request.getHeader(TENANT_HEADER),
                identity(request)
        );
        RateLimitDecision decision = rateLimiter.check(rateLimitRequest);
        writeRateLimitHeaders(response, decision);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(decision.retryAfter())));
        ApiError error = ApiError.of("RATE_LIMITED",
                "Too many requests. Please retry after " + retryAfterSeconds(decision.retryAfter()) + " seconds.");
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private void writeRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Policy", decision.policyName());
        response.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
        if (!decision.allowed()) {
            response.setHeader("X-RateLimit-Reset", Long.toString(retryAfterSeconds(decision.retryAfter())));
        }
    }

    private long retryAfterSeconds(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            return 1;
        }
        return Math.max(1, (long) Math.ceil(retryAfter.toMillis() / 1000.0d));
    }

    private String identity(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String issuer = jwt.getIssuer() == null ? "unknown-issuer" : jwt.getIssuer().toString();
                return "jwt:" + issuer + ":" + jwt.getSubject();
            }
            if (auth.getName() != null && !auth.getName().isBlank()) {
                return "auth:" + auth.getName();
            }
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].strip().toLowerCase(Locale.ROOT);
        }
        return request.getRemoteAddr();
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
