package com.example.multiapp.resource.entity;

import com.example.multiapp.common.jpa.AuditedEntity;
import com.example.multiapp.resource.auth.ResourceBlockAuthorizer;
import com.example.multiapp.resource.dto.CreateResourceBlockRequest;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "resource_block", schema = "app")
public class ResourceBlock extends AuditedEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private ResourceBlockId id;

    @Column(name = "resource_user_id", nullable = false)
    private UUID resourceUserId;

    @Column(name = "resource_user_id", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "deleted_at")
    private  OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private static ResourceBlock create(
            UUID tenantId, UUID resourceUserId, OffsetDateTime startAt, OffsetDateTime endAt,
            String reason) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(endAt, "endAt");
        if(!endAt.isAfter(startAt)) throw new IllegalArgumentException("endAt must be after startAt");
        if(reason == null || reason.isBlank()) throw new IllegalArgumentException("reason cannot be blank");
        ResourceBlock b = new ResourceBlock();
        b.id = new ResourceBlockId(tenantId, UUID.randomUUID());
        b.resourceUserId = resourceUserId;
        b.startAt = startAt;
        b.endAt = endAt;
        b.reason = reason.strip();
        return b;
    }

    public static ResourceBlock from(UUID tenantId, UUID resourceUserId, CreateResourceBlockRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        Objects.requireNonNull(req, "CreateResourceBlockRequest");
        Objects.requireNonNull(req.reason(), "req.reason");
        ResourceBlock b = new ResourceBlock();
        b.id = new ResourceBlockId(tenantId, UUID.randomUUID());
        b.resourceUserId = resourceUserId;
        b.startAt = req.startAt();
        b.endAt = req.endAt();
        b.reason = req.reason().strip();
        return b;
    }

    public void softDelete() {
        if(this.deletedAt != null) throw new IllegalArgumentException("resource block already deleted");
        this.deletedAt = OffsetDateTime.now();
    }
}
