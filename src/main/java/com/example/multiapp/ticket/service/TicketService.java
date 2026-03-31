package com.example.multiapp.ticket.service;

import com.example.multiapp.appointment.dto.AppointmentSummary;
import com.example.multiapp.appointment.repo.AppointmentRepository;
import com.example.multiapp.attachment.dto.AttachmentSummary;
import com.example.multiapp.attachment.repo.AttachmentRepository;
import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.audit.repo.AuditLogRepository;
import com.example.multiapp.comment.dto.CommentSummary;
import com.example.multiapp.comment.repo.CommentRepository;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.api.PageNormalizer;
import com.example.multiapp.common.api.PreconditionFailedException;
import com.example.multiapp.common.api.dto.SliceBlock;
import com.example.multiapp.common.crypto.Hashing;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.outbox.DedupKeyFactory;
import com.example.multiapp.common.outbox.OutboxPublisher;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.IdempotencyConflictException;
import com.example.multiapp.common.web.IfMatchPreconditions;
import com.example.multiapp.contact.repo.ContactRepository;
import com.example.multiapp.idempotency.codec.IdempotencyResponseCodec;
import com.example.multiapp.idempotency.entity.IdempotencyId;
import com.example.multiapp.idempotency.entity.IdempotencyRecord;
import com.example.multiapp.idempotency.model.IdempotencyStatus;
import com.example.multiapp.idempotency.repo.IdempotencyRecordRepository;
import com.example.multiapp.idempotency.service.IdempotencyService;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.outbox.repo.OutboxEventRepository;
import com.example.multiapp.ticket.api.assembler.TicketDetailAssembler;
import com.example.multiapp.ticket.auth.TicketAuthorizer;
import com.example.multiapp.ticket.dto.*;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.event.TicketEventType;
import com.example.multiapp.ticket.model.TicketStatus;
import com.example.multiapp.ticket.repo.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.DomainLoadStoreParameter;
import java.sql.BatchUpdateException;
import java.time.OffsetDateTime;
import java.util.*;

import static com.example.multiapp.membership.model.MembershipRole.*;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketAuthorizer ticketAuth;
    private final TicketRepository ticketRepo;
    private final IdempotencyService idemService;
    private final ContactRepository contactRepo;
    private final OutboxPublisher outboxPublisher;
    private final AppointmentRepository appointmentRepo;
    private final AttachmentRepository attachmentRepo;
    private final CommentRepository commentRepo;
    private final AuditWriter auditWriter;
    @Transactional
    public TicketCreatedResponse create(RequestContext ctx, String idemKey, CreateTicketRequest req) {
        Objects.requireNonNull(ctx, "RequestContext");
        Objects.requireNonNull(idemKey, "Idempotency-Key");
        Objects.requireNonNull(req, "CreateTicketRequest");
        final UUID tenantId = ctx.tenantId();
        final UUID actorUserId = ctx.userId();
        final String requestHash = Hashing.sha256Hex(req.toStableString());

        ticketAuth.requireCreate(ctx, req);
        final IdempotencyId idemId = new IdempotencyId(tenantId, actorUserId, idemKey);

        // 1: 抢占幂等键(同一事务: 失败回滚不留下IN_PROGRESS)
        Optional<TicketCreatedResponse> cached = idemService.tryInsert(tenantId, actorUserId, idemKey, requestHash,
                TicketCreatedResponse.class);
        if (cached.isPresent()) return cached.get();
        // 2: 业务写入, ticket_no使用DB_default
        DomainEventType eventType = TicketEventType.TICKET_CREATED;
        Ticket ticket = Ticket.create(tenantId, actorUserId, req.requesterUserId(),
                req.requesterContactId(), req.priority(), req.ticketType(),
                req.title(), req.description(), req.locationText());
        ticketRepo.save(ticket);
        UUID ticketId = ticket.getId().getId();
        JsonNode payloadData = AuditPayloadBuilder.forEntity(ticketId, eventType)
                .addField("createdByUserId", null, ticket.getCreatedByUserId().toString())
                .addField("requesterUserId", null, ticket.getRequesterUserId().toString())
                .addField("requesterContactId", null, ticket.getRequesterContactId().toString())
                .addField("priority", null, ticket.getPriority().name())
                .addField("title", null, ticket.getTitle())
                .addField("description", null, ticket.getDescription())
                .addField("location", null, ticket.getLocationText()).build();
        // 3: 审计, 同一事务
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.TICKET, ticketId,
                eventType, DomainEventPayloads.envelopFrom(ctx, ticketId, payloadData));
//        AuditLog auditLog = AuditLog.ticketCreated(tenantId, actorUserId,
//                ticket.getId().getId(), ctx.requestId());
//        auditRepo.save(auditLog);
        auditWriter.append(auditLog);
        // 4: outbox, 同一事务, dedupKey包含actor+idemKey
//        String dedupKey = idemKey + ":" + eventType.key();
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));

        // 5: 回写幂等记录, 必须成功, 否则回滚
        TicketCreatedResponse resp = TicketCreatedResponse.from(ticket);
        idemService.tryComplete(tenantId, actorUserId, idemKey, requestHash, resp);
        return resp;
//        String respJson = codec.write(resp);
//        int updated = idemRepo.complete(new IdempotencyId(tenantId, actorUserId, idemKey),
//                requestHash, respJson);
//        if (updated != 1) {
//            // 理论上不应发生：除非记录被删/状态不对/hash 不对
//            throw new IllegalStateException("Idempotency completion failed");
//        }
//        return resp;
    }

    /*
    * 不同用户角色的可见度不同, 选定tenant_id, 考虑角色CUSTOMER, RESOURCE_USER, AGENT, ADMIN
    * 清除不合法的query参数
    * */
    @Transactional(readOnly = true)
    public Page<TicketResponse> list(RequestContext ctx, TicketQuery query, Pageable p) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(query, "query");
        ticketAuth.requireList(ctx, query);
        // todo:: for
        Pageable pageable = PageNormalizer.normalize(p, 100, 20, Sort.by(
                        Sort.Order.desc("updatedAt"), Sort.Order.desc("id.id")),
                        Set.of("updatedAt", "id.id", "status", "priority", "ticket_type",
                                "title", "location_text"));
        UUID userId = ctx.userId();
        UUID tenantId = ctx.tenantId();
        // 主要的三个query字段:
        // - requster_user_id
        // - requester_contact_id
        // - owner_id
        switch (ctx.role()) {
            case CUSTOMER -> {
                // 基础用户, 可以直接忽略requester_user_id字段以及requester_contact_id字段
                // 使用本用户信息填充, 或者这里拦截错误也可以, 或者直接返回空最干脆, 最整洁, 不需要额外逻辑
//                if (query.requesterUserId() != null && !query.requesterUserId().equals(userId) ||
//                query.requesterContactId() != null && !contactRepo.existsByIdTenantIdAndIdIdAndLinkedUserId(
//                        tenantId, query.requesterContactId(), userId)) {
//                    // 参数不符合, 返回空表
//                    return Page.empty();
//                }
                return ticketRepo.findForCustomerResponse(tenantId, userId, query, pageable);
            }
            case RESOURCE_USER -> {
                return ticketRepo.findForResourceUserResponse(tenantId, userId, query, pageable);
            }
            case AGENT -> {
                return ticketRepo.findForAgentResponse(tenantId, userId, query, pageable);
            }
            case ADMIN -> {
                return ticketRepo.findForAdminResponse(tenantId, query, pageable);
            }
            default -> throw new IllegalArgumentException("Unhandled role: " + ctx.role());
        }
//        return ticketRepo.findByIdTenantId(ctx.tenantId(), pageable).map(TicketResponse::from);
    }

    // 查看详情, 应该返回TicketDetailResponse
    @Transactional(readOnly = true)
    public TicketDetailResponse get(RequestContext ctx, UUID ticketId) {
        // TODO:: 这里可能要考虑调用其他service返回appointments, attachments, comments等信息
        // TODO:: 所以可能要考虑扩充TicketResponse实体还是?
        // TODO:: 具体实现细节待考量
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        ticketAuth.requireRead(ctx, ticketId);
        UUID tenantId = ctx.tenantId();
        TicketResponse ticket = ticketRepo.findResponseByTenantIdAndId(tenantId, ticketId)
                .orElseThrow(() -> new NotFoundException("ticket not found"));
        SliceBlock<AppointmentSummary> upcoming = TicketDetailAssembler.sliceBlock(
                size -> appointmentRepo.listUpcomingByTicket(tenantId, ticketId,
                        PageRequest.of(0, size)),
                () -> appointmentRepo.countUpcomingByIdTenantIdAndTicketId(tenantId, ticketId),
                10
        );

        SliceBlock<AppointmentSummary> recentPast = TicketDetailAssembler.sliceBlock(
                size -> appointmentRepo.listRecentPastByTicket(tenantId, ticketId,
                        PageRequest.of(0, size)),
                () -> appointmentRepo.countPastByIdTenantIdAndTicketId(tenantId, ticketId),
                10
        );

        SliceBlock<CommentSummary> comments = TicketDetailAssembler.sliceBlock(
                size -> commentRepo.listSummariesByTicket(tenantId, ticketId,
                        PageRequest.of(0, size).withSort(Sort.by(Sort.Order.desc("createdAt")))),
                () -> commentRepo.countByIdTenantIdAndTicketIdAndDeletedAtIsNull(tenantId, ticketId),
                20
        );
        SliceBlock<AttachmentSummary> attachments = TicketDetailAssembler.sliceBlock(
                size -> attachmentRepo.listSummariesByTicket(tenantId, ticketId,
                        PageRequest.of(0, size).withSort(Sort.by(Sort.Order.desc("createdAt")))),
                () -> attachmentRepo.countByIdTenantIdAndTicketIdAndDeletedAtIsNull(tenantId, ticketId),
                50
        );
        return new TicketDetailResponse(ticket, upcoming, recentPast, comments, attachments);

//        return ticketRepo.findResponseByTenantIdAndId(ctx.tenantId(), ticketId)
//                .orElseThrow(() -> new NotFoundException("ticket not found"));
//        return TicketResponse.from(ticket);
    }

    @Transactional
    public void transition(RequestContext ctx, UUID ticketId,
                                     @NotBlank String ifMatch, TicketStatus toStatus) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ctx.tenantId(), "ctx.tenantId");
        Objects.requireNonNull(ctx.requestId(), "ctx.requestId");
        Objects.requireNonNull(ifMatch, "ifMatch");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(toStatus, "toStatus");
        Ticket ticket = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        IfMatchPreconditions.require(ifMatch, ticket.getVersion());
        ticketAuth.requireTransition(ctx, ticketId, toStatus);
//         If-Match 语义: 必须提供, controller已经校验过该参数格式
        // 支持If-Match: *表示只要资源存在就行

        TicketStatus fromStatus = Objects.requireNonNull(ticket.getStatus());
        if(fromStatus == toStatus) return;
        // transition 里不需要显式 ticketRepo.save(ticket)：JPA 在事务内对托管实体修改会在 flush/commit 时写回
        ticket.transitionTo(toStatus, OffsetDateTime.now());
        DomainEventType eventType = TicketEventType.TICKET_STATUS_CHANGED;
        JsonNode payloadData = AuditPayloadBuilder.forEntity(ticketId, eventType)
                .addField("status", fromStatus.name(), toStatus.name()).build();
        AuditLog auditLog = AuditLog.ticketStatusChanged(
                ctx.tenantId(), ctx.userId(), ticketId, payloadData, ctx.requestId());
        auditWriter.append(auditLog);
//        auditRepo.save(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));

//        return TicketResponse.from(ticket);
    }

    @Transactional
    public void assign(RequestContext ctx, UUID ticketId, @NotBlank String ifMatch,
                       UUID newAssignee) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ctx.tenantId(), "ctx.tenantId");
        Objects.requireNonNull(ctx.requestId(), "ctx.requestId");
        Objects.requireNonNull(ifMatch, "ifMatch");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(newAssignee, "newAssignee");
        Ticket ticket = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        IfMatchPreconditions.require(ifMatch, ticket.getVersion());
        ticketAuth.requireReassign(ctx, ticketId, newAssignee);
        UUID fromOwnerUserId = ticket.getOwnerUserId();
        if(fromOwnerUserId.equals(newAssignee)) return;
        ticket.setNewOwner(newAssignee);
        DomainEventType eventType = TicketEventType.TICKET_ASSIGNEE_UPDATED;
        JsonNode payloadData = AuditPayloadBuilder.forEntity(ticketId, eventType)
                .addField("ownerUserId", fromOwnerUserId.toString(),
                        newAssignee.toString()).build();
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.TICKET, ticketId, eventType,
                DomainEventPayloads.envelopFrom(ctx, ticketId, payloadData));
        auditWriter.append(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));

    }

    @Transactional
    public void update(RequestContext ctx, UUID ticketId, @NotBlank String ifMatch,
                       @NotNull UpdateTicketRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ctx.tenantId(), "ctx.tenantId");
        Objects.requireNonNull(ctx.requestId(), "ctx.requestId");
        Objects.requireNonNull(ifMatch, "ifMatch");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(req, "UpdateTicketRequest");
        Ticket ticket = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        IfMatchPreconditions.require(ifMatch, ticket.getVersion());
        ticketAuth.requireUpdate(ctx, ticketId, req);
        DomainEventType eventType = TicketEventType.TICKET_UPDATED;
        AuditPayloadBuilder builder = AuditPayloadBuilder.forEntity(ticketId, eventType);
        boolean updated = false;
        if(req.title() != null && !req.title().isBlank()) {
            String fromTitle = ticket.getTitle();
            String newTitle = req.title().strip();
            if(!newTitle.equals(fromTitle)) {
                ticket.changeTitle(newTitle);
                builder.addField("title", fromTitle, newTitle);
            }
            updated = true;
        }
        if(req.ticketType() != null && req.ticketType() != ticket.getTicketType()) {
            builder.addField("ticketType", ticket.getTicketType().toString(),
                    req.ticketType().toString());
            ticket.changeType(req.ticketType());
            updated = true;
        }
        if(req.priority() != null && req.priority() != ticket.getPriority()) {
            builder.addField("priority", ticket.getPriority().toString(),
                    req.priority().toString());
            ticket.changePriority(req.priority());
            updated = true;
        }
        if(req.description() != null && !req.description().isBlank()) {
            String fromDesc = ticket.getDescription();
            String newDesc = req.description().strip();
            if(!newDesc.equals(fromDesc)){
                builder.addField("description", fromDesc, newDesc);
                ticket.updateDescription(newDesc);
                updated = true;
            }
        }
        if(req.locationText() != null && !req.locationText().isBlank()) {
            String fromLoc = ticket.getLocationText();
            String newLoc = req.locationText().strip();
            if(!newLoc.equals(fromLoc)) {
                builder.addField("locationText", fromLoc, newLoc);
                ticket.updateLocationText(newLoc);
                updated = true;
            }
        }
        if(!updated) return;
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.TICKET, ticketId, eventType,
                DomainEventPayloads.envelopFrom(ctx, ticketId, builder.build()));
        auditWriter.append(auditLog);
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forRequestScopedUpdate(
                ctx.requestId(), eventType)));
    }


}
