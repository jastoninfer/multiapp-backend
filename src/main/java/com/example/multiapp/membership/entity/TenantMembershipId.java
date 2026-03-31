package com.example.multiapp.membership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;


@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class TenantMembershipId implements Serializable {
    @Column(name="tenant_id", nullable = false) private UUID tenantId;
    @Column(name="user_id", nullable = false) private UUID userId;

    public TenantMembershipId(UUID tenantId, UUID userId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.userId = Objects.requireNonNull(userId, "userId");
    }
}
