package com.example.multiapp.appointment.entity;


import com.example.multiapp.appointment.dto.CreateAppointmentRequest;
import com.example.multiapp.appointment.model.AppointmentStatus;
import com.example.multiapp.common.jpa.AuditedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "appointment", schema = "app")
public class Appointment extends AuditedEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private AppointmentId id;

    @ToString.Include
    @EqualsAndHashCode.Include
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @ToString.Include
    @Column(name = "resource_user_id", nullable = false)
    private UUID resourceUserId;

    @ToString.Include
    @Column(name = "customer_user_id")
    private UUID customerUserId;

    @ToString.Include
    @Column(name = "customer_contact_id")
    private UUID customerContactId;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    // DB generated column (tstzrange). 只读映射, 不参与insert/update
//    @Column(name = "time_range", columnDefinition = "tstzrange", insertable = false, updatable = false)
//    private String timeRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Column(name = "address_text")
    private String addressText;

    @Column(name = "notes")
    private String notes;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private static Appointment create(
            UUID tenantId, UUID ticketId, UUID resourceUserId, UUID customerUserId, UUID customerContactId,
            OffsetDateTime startAt, OffsetDateTime endAt, String addressText, String notes) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(resourceUserId, "resourceUserId");
        if(Objects.isNull(customerUserId) == Objects.isNull(customerContactId)) {
            throw new IllegalArgumentException(
                    "exactly one of customerUserId and customerContactId should be null");
        }
        Objects.requireNonNull(startAt, "start at");
        Objects.requireNonNull(endAt, "end at");
        if(!endAt.isAfter(startAt)) throw new IllegalArgumentException("endAt must be after startAt");
        Appointment a = new Appointment();
        a.id = new AppointmentId(tenantId, UUID.randomUUID());
        a.ticketId = ticketId;
        a.resourceUserId = resourceUserId;
        a.customerContactId = customerContactId;
        a.customerUserId = customerUserId;
        a.startAt = startAt;
        a.endAt = endAt;
        a.status = AppointmentStatus.BOOKED;
        a.addressText = normalizeOptional(addressText);
        a.notes = normalizeOptional(notes);
        return a;
    }

    public void reschedule(OffsetDateTime startAt, OffsetDateTime endAt) {
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(endAt, "endAt");
//        if(this.startAt.equals(startAt) && this.endAt.equals(endAt)) return;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = AppointmentStatus.RESCHEDULED;
//        this.addressText = normalizeOptional(addressText);
//        this.notes = normalizeOptional(notes);
    }

    public void cancel() {
        if(this.status != AppointmentStatus.BOOKED && this.status != AppointmentStatus.RESCHEDULED) {
            throw new IllegalArgumentException("appointment is: [%s] cannot be cancelled".formatted(
                    this.status.name()));
        }
        // 取消前进行状态检查, 什么样的状态可以cancel?
        this.status = AppointmentStatus.CANCELLED;
//        this.notes = normalizeOptional(notes);
    }

    public void complete() {
        // 完成前进行状态检查, 什么样的状态可以complete?
        if(this.status != AppointmentStatus.BOOKED && this.status != AppointmentStatus.RESCHEDULED) {
            throw new IllegalArgumentException("appointment is: [%s] cannot be completed".formatted(
                    this.status.name()));
        }
        if(this.arrivedAt == null) {
            throw new IllegalArgumentException("cannot complete appointment without arrival");
        }
        this.status = AppointmentStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
//        this.notes = normalizeOptional(notes);
    }

    public void updateNotes(@NotBlank String notes) {
        Objects.requireNonNull(notes, "notes");
        this.notes = normalizeOptional(notes);
    }

    public void updateAddressText(@NotBlank String addressText) {
        Objects.requireNonNull(addressText, "addressText");
        this.addressText = normalizeOptional(addressText);
    }

    public void markArrived() {
        // 行状态检查, 什么样的状态可以mark arrived?
        if(this.arrivedAt != null) {
            throw new IllegalArgumentException("appointment marked arrived already");
        }
        if(this.status != AppointmentStatus.BOOKED && this.status != AppointmentStatus.RESCHEDULED) {
            throw new IllegalArgumentException("appointment is: [%s] cannot be mark arrived".formatted(
                    this.status.name()));
        }
        this.arrivedAt = OffsetDateTime.now();
    }

    private static String normalizeOptional(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    public static Appointment from(UUID tenantId, UUID ticketId, CreateAppointmentRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(req, "CreateAppointmentRequest");
        return create(tenantId, ticketId, req.resourceUserId(), req.customerUserId(),
                req.customerContactId(), req.startAt(), req.endAt(), req.addressText(), req.notes());
    }
}