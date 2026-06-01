package com.example.multiapp.appointment.service;

import com.example.multiapp.appointment.auth.AppointmentAuthorizer;
import com.example.multiapp.appointment.dto.*;
import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.model.AppointmentEventType;
import com.example.multiapp.appointment.model.AppointmentStatus;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.api.PageNormalizer;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.outbox.DedupKeyFactory;
import com.example.multiapp.common.outbox.OutboxPublisher;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.IfMatchPreconditions;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.user.service.UserGuard;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentService {
    private final AppointmentRepository appointmentRepo;
    private final AppointmentAuthorizer appointmentAuth;
    private final AuditWriter auditWriter;
    private final OutboxPublisher outboxPublisher;
    private final UserGuard userGuard;
    private final AvailabilityValidator availabilityValidator;
    private final AppointmentReader appointmentReader;

    @Transactional
    public AppointmentCreatedResponse createForTicket(
            RequestContext ctx, UUID ticketId, CreateAppointmentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(req, "CreateAppointmentRequest");
        appointmentAuth.requireCreate(ctx, ticketId, req);
        // 需要在某个ticket下创建appointment, 那么自然得, ctx需要有对ticket起码的可见权
        validateAppointmentDuration(req.startAt(), req.endAt());
        availabilityValidator.validate(ctx.tenantId(), req.resourceUserId(), req.startAt(), req.endAt());
        DomainEventType eventType = AppointmentEventType.APPOINTMENT_CREATED;
        userGuard.requireActiveUser(req.customerUserId());
        userGuard.requireActiveUser(req.resourceUserId());
        Appointment a = Appointment.from(ctx.tenantId(), ticketId, req);
        appointmentRepo.save(a);
        UUID appointmentId = a.getId().getId();
        JsonNode payloadData = AuditPayloadBuilder.forEntity(appointmentId, eventType)
                .addField("resourceUserId", null, a.getResourceUserId())
                .addField("customerUserId", null, a.getCustomerUserId())
                .addField("customerContactId", null, a.getCustomerContactId())
                .addField("startAt", null, a.getStartAt())
                .addField("endAt", null, a.getEndAt())
                .addField("addressText", null, a.getAddressText())
                .addField("notes", null, a.getNotes())
                .build();
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.APPOINTMENT, appointmentId,
                eventType, DomainEventPayloads.envelopFrom(ctx, appointmentId, payloadData));
        auditWriter.append(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forCreate(
                ctx.requestId(), eventType)));
        return AppointmentCreatedResponse.from(a);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentSummary> list(RequestContext ctx, AppointmentQuery query, Pageable p) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(query, "query");
        appointmentAuth.requireSearch(ctx, query);
        Pageable pageable = PageNormalizer.normalize(p, 40, 20, Sort.by(
                Sort.Order.desc("startAt"), Sort.Order.desc("id.id")),
                Set.of("startAt", "id.id", "endAt", "status", "resourceUserId"));
        // agent/admin/resourceUser
        UUID tenantId = ctx.tenantId();
        AppointmentQuery effectiveQuery = ctx.role() == MembershipRole.RESOURCE_USER ?
                query.withResourceUserId(ctx.userId()) :
                (ctx.role() == MembershipRole.AGENT ? query.withTicketOwnerId(ctx.userId()) :
                        query);
        return appointmentRepo.search(tenantId, toSearchQuery(effectiveQuery), pageable);
//        return ctx.role() == MembershipRole.RESOURCE_USER ?
//                appointmentRepo.searchAsResourceUser(tenantId, ctx.userId(), query, pageable)
//                : appointmentRepo.search(tenantId, query, pageable);
    }

    private AppointmentSearchQuery toSearchQuery(AppointmentQuery query) {
        return new AppointmentSearchQuery(
                query.resourceUserId(),
                query.ticketOwnerId(),
                query.ticketId(),
                query.from(),
                query.to(),
                query.status() == null ? null : query.status().name()
        );
    }

    @Transactional(readOnly = true)
    public AppointmentDetailResponse get(RequestContext ctx, UUID appointmentId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(appointmentId, "appointmentId");
        appointmentAuth.requireRead(ctx);
        // resourceUserId只能查看自己的
        Optional<AppointmentDetailResponse> resp = ctx.role() == MembershipRole.RESOURCE_USER ?
                appointmentRepo.findDetailByTenantIdAndIdIdAndResourceUserId(
                        ctx.tenantId(), appointmentId, ctx.userId()) :
                (ctx.role() == MembershipRole.AGENT ?
                        appointmentRepo.findDetailByTenantIdAndIdIdAndTicketOwnerId(ctx.tenantId(),
                                appointmentId, ctx.userId()) :
                        appointmentRepo.findDetailByTenantIdAndIdId(ctx.tenantId(), appointmentId));
        return resp.orElseThrow(() -> new NotFoundException("appointment not found"));
    }

    @Transactional
    public void update(RequestContext ctx, UUID appointmentId, @NotBlank String ifMatch,
                       @NotNull UpdateAppointmentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(appointmentId, "appointmentId");
        Objects.requireNonNull(ifMatch, "ifMatch");
        Objects.requireNonNull(req, "UpdateAppointmentRequest");
        Appointment a = appointmentRepo.findByIdTenantIdAndIdId(ctx.tenantId(), appointmentId)
                        .orElseThrow(() -> new NotFoundException("appointment: [%s] not found".
                                formatted(appointmentId)));
        // 可见性是进行下一步的必要条件
//        appointmentReader.requireVisible(ctx, a);
        IfMatchPreconditions.require(ifMatch, a.getVersion());
        appointmentAuth.requireUpdate(ctx, a, req);
        DomainEventType eventType = AppointmentEventType.APPOINTMENT_UPDATED;
        AuditPayloadBuilder builder = AuditPayloadBuilder.forEntity(appointmentId, eventType);


        // 逐字段检查
        boolean updated = false;
        // status, startAt, endAt, addressText, notes, arrivedAt
        switch (ctx.role()) {
            case RESOURCE_USER -> {
                // resource_user 修改的字段很有限, 包括status -> COMPLETED, 但需要保证arrivedAt不为空,
                // 且早于现在; 前置状态为BOOKED/RESCHEDULED
                // 修改notes, arrivedAt等
                if(req.status() == AppointmentStatus.COMPLETED) {
                    builder.addField("status", a.getStatus(), req.status());
                    a.complete();
                    updated = true;
                    eventType = AppointmentEventType.APPOINTMENT_COMPLETED;
                }
            }
            case ADMIN, AGENT -> {
                if(req.status() != null) {
                    switch (req.status()) {
                        case RESCHEDULED -> {
                            if(req.startAt() != null && req.endAt() != null &&
                            !(req.startAt().equals(a.getStartAt()) &&
                                    req.endAt().equals(a.getEndAt()))) {
                                validateAppointmentDuration(req.startAt(), req.endAt());
                                builder.addField("status", a.getStatus(), req.status());
                                builder.addField("startAt", a.getStartAt(), req.startAt());
                                builder.addField("endAt", a.getEndAt(), req.endAt());
                                a.reschedule(req.startAt(), req.endAt());
                                updated = true;
                                eventType = AppointmentEventType.APPOINTMENT_RESCHEDULED;
                            }
                        }
                        case COMPLETED -> {
                            builder.addField("status", a.getStatus(), req.status());
                            a.complete();
                            updated = true;
                            eventType = AppointmentEventType.APPOINTMENT_COMPLETED;
                        }
                        case CANCELLED -> {
                            builder.addField("status", a.getStatus(), req.status());
                            a.cancel();
                            updated = true;
                            eventType = AppointmentEventType.APPOINTMENT_CANCELLED;
                        }
                        default -> throw new IllegalArgumentException("Unhandled status: " + req.status());
                    }
                }
                if(req.addressText() != null && !req.addressText().isBlank() &&
                    !req.addressText().equals(a.getAddressText())) {
                    builder.addField("addressText", a.getAddressText(), req.addressText());
                    a.updateAddressText(req.addressText());
                    updated = true;
                }
            }
            default -> throw new IllegalArgumentException("Unhandled role: " + ctx.role());
        }
        // 更新notes
        if(req.notes() != null && !req.notes().isBlank() && !req.notes().equals(a.getNotes())) {
            builder.addField("notes", a.getNotes(), req.notes());
            a.updateNotes(req.notes());
            updated = true;
        }
        // 更新arrivedAt
        if (req.arrivedAt() != null) {
            builder.addField("arrivedAt", a.getArrivedAt(), req.arrivedAt());
            a.markArrived(); // 前端传回的时间被忽略, 后端自己生成时间戳
            updated = true;
            eventType = AppointmentEventType.APPOINTMENT_MARK_ARRIVED;
        }
        if(!updated) return;
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.APPOINTMENT, appointmentId, eventType,
                DomainEventPayloads.envelopFrom(ctx, appointmentId, builder.build()));
        auditWriter.append(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
    }

    private static void validateAppointmentDuration(OffsetDateTime startAt, OffsetDateTime endAt) {
        if(!endAt.isAfter(startAt)) throw new IllegalArgumentException("endAt must be after startAt");
        long minutes = Duration.between(startAt, endAt).toMinutes();
        if(minutes <= 0 || minutes > 8 * 60)
            throw new IllegalArgumentException("duration should be between 0 and 8 hours");
    }
}
