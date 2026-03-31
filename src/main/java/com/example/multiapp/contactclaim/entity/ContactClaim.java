package com.example.multiapp.contactclaim.entity;

import com.example.multiapp.common.jpa.CreatedOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "contact_claim", schema = "app")
public class ContactClaim extends CreatedOnlyEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private ContactClaimId id;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "consumed_by_user_id")
    private UUID consumedByUserId;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    public static ContactClaim issue(
            UUID tenantId,
            UUID contactId,
            String codeHash,
            OffsetDateTime expiresAt,
            UUID createdByUserId
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(contactId, "contactId");
        Objects.requireNonNull(codeHash, "codeHash");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdByUserId, "createByUserId");
        ContactClaim c = new ContactClaim();
        c.id = new ContactClaimId(tenantId, UUID.randomUUID());
        c.contactId = contactId;
        c.codeHash = codeHash;
        c.expiresAt = expiresAt;
        c.createdByUserId = createdByUserId;
        c.attempts = 0;
        return c;
    }

    public boolean tryConsume(UUID userId, OffsetDateTime now) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(now, "now");
        if(consumedAt != null) return false; // 幂等, 已消费则不重复写
        this.consumedAt = now;
        this.consumedByUserId = userId;
        return true;
    }

    public void recordAttempt(OffsetDateTime now) {
        Objects.requireNonNull(now, "now");
        this.attempts++;
        this.lastAttemptAt = now;
    }
}
