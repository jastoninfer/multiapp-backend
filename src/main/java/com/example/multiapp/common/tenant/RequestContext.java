package com.example.multiapp.common.tenant;

import com.example.multiapp.membership.model.MembershipRole;

import java.util.Objects;
import java.util.UUID;

public record RequestContext(
    UUID tenantId, // tenant
    UUID userId, // registered user id
    boolean isPlatformAdmin, // is platform admin
    MembershipRole role, // not null
    String issuer, // keycloak realm
    String subject, // keycloak id
    String requestId // 常见: X-Request-Id, 追踪链路
) {
    public RequestContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(role, "role");
    }
}
