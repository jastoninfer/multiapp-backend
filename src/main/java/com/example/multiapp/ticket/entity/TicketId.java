package com.example.multiapp.ticket.entity;

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
public class TicketId implements Serializable {
    @Column(name="tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "id", nullable = false)
    private UUID id;

    public TicketId(UUID tenantId, UUID id) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.id = Objects.requireNonNull(id, "id");
    }
}
