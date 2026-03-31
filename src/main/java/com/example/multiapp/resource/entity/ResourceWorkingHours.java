package com.example.multiapp.resource.entity;

import com.example.multiapp.resource.dto.WorkingHoursRule;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "resource_working_hours", schema = "app")
public class ResourceWorkingHours {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private ResourceWorkingHoursId id;

    @Column(name = "start_local", nullable = false)
    private LocalTime startLocal;

    @Column(name = "end_local", nullable = false)
    private LocalTime endLocal;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    public static ResourceWorkingHours create(
            UUID tenantId, UUID resourceUserId, WorkingHoursRule rule,
            LocalTime startLocal, LocalTime endLocal) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(startLocal, "start");
        Objects.requireNonNull(endLocal, "end");
        ResourceWorkingHours hour = new ResourceWorkingHours();
        hour.id = new ResourceWorkingHoursId(tenantId, resourceUserId, rule.dayOfWeek());
        hour.startLocal = startLocal;
        hour.endLocal = endLocal;
        hour.timezone = rule.timezone();
        return hour;
    }
}
