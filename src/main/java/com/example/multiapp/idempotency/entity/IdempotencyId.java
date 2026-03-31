package com.example.multiapp.idempotency.entity;

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
public class IdempotencyId implements Serializable {
    @Column(name= "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
    @Column(name = "idempotency_key", nullable = false) private String idemKey;

    public IdempotencyId(UUID tenantId, UUID actorUserId, String idemKey) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        if(idemKey == null || idemKey.isBlank()) throw new IllegalArgumentException("idemKey");
        this.idemKey = idemKey;
    }
}
