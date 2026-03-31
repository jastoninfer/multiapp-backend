package com.example.multiapp.resource.entity;

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
public class ResourceWorkingHoursId implements Serializable {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "resource_user_id", nullable = false)
    private UUID resourceUserId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    public ResourceWorkingHoursId(UUID tenantId, UUID resourceUserId, int dayOfWeek) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.resourceUserId = Objects.requireNonNull(resourceUserId, "resourceUserId");
        this.dayOfWeek = legitDayOfWeek(dayOfWeek);
    }

    private static int legitDayOfWeek(int d) {
        if(d > 7 || d < 1) throw new IllegalArgumentException("day of week must be between 1 and 7");
        return d;
    }
}
