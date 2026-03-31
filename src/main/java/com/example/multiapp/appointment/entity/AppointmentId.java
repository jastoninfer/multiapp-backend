package com.example.multiapp.appointment.entity;

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
public class AppointmentId implements Serializable {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "id", nullable = false)
    private UUID id;
    public AppointmentId(UUID tenantId, UUID id) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.id = Objects.requireNonNull(id, "id");
    }
}
