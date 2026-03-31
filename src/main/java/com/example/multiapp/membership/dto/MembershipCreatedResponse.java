package com.example.multiapp.membership.dto;

import com.example.multiapp.membership.entity.TenantMembership;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record MembershipCreatedResponse(UUID tenantId, UUID userId, String role,
                                        boolean isDefault, OffsetDateTime createdAt, long version) {
    public MembershipCreatedResponse {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }
    public static MembershipCreatedResponse from(TenantMembership m) {
        return new MembershipCreatedResponse(m.getId().getTenantId(), m.getId().getUserId(),
                m.getRole().name(), m.isDefault(), m.getCreatedAt() == null ? OffsetDateTime.now()
                : m.getCreatedAt(), m.getVersion());
    }

}
