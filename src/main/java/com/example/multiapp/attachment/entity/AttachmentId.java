package com.example.multiapp.attachment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class AttachmentId implements Serializable {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "id", nullable = false)
    private UUID id;
    public AttachmentId(UUID tenantId, UUID id) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.id = Objects.requireNonNull(id, "id");
    }
}