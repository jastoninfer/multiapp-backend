package com.example.multiapp.comment.service;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.comment.auth.CommentAuthorizer;
import com.example.multiapp.comment.dto.CommentResponse;
import com.example.multiapp.comment.dto.CommentSummary;
import com.example.multiapp.comment.dto.CreateCommentRequest;
import com.example.multiapp.comment.entity.Comment;
import com.example.multiapp.comment.model.CommentEventType;
import com.example.multiapp.comment.model.CommentVisibility;
import com.example.multiapp.comment.repo.CommentRepository;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.api.ForbiddenException;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.api.PageNormalizer;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.outbox.DedupKeyFactory;
import com.example.multiapp.common.outbox.OutboxPublisher;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.ticket.auth.TicketAuthorizer;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketStatus;
import com.example.multiapp.ticket.repo.TicketRepository;
import com.example.multiapp.ticket.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentAuthorizer commentAuth;
    private final CommentRepository commentRepo;
    private final TicketAuthorizer ticketAuth;
    private final OutboxPublisher outboxPublisher;
    private final TicketRepository ticketRepo;
    private final TicketService ticketService;

    @Transactional
    public void post(RequestContext ctx, UUID ticketId, CreateCommentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(req, "req");
//        final UUID tenantId = ctx.tenantId();
        // 无论是谁, 不允许向关闭的ticket的添加评论
        Ticket t = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId).orElseThrow(
                () -> new NotFoundException("ticket id not found")
        );
        if(t.getStatus() == TicketStatus.CLOSED) {
            throw new ForbiddenException("ticket has been closed");
        }
        ticketAuth.requireRead(ctx, ticketId, true);
        // TODO:: 或者可以把CommentAuth所有逻辑移动到TicketAuth
        // 毕竟对comment的操作不应该脱离ticket, 权限上移是合理的
        commentAuth.requirePost(ctx, req);
        // 如果当前是agent + ticket owner, 发布评论可以出发ticket status的改动
        if(ctx.role() == MembershipRole.AGENT && ctx.userId().equals(t.getOwnerUserId())) {
            if(t.getStatus() == TicketStatus.NEW || t.getStatus() == TicketStatus.REOPENED) {
                t.transitionTo(TicketStatus.IN_PROGRESS, OffsetDateTime.now());
                if(t.getFirstResponseAt() == null) {
                    t.setFirstResponseAt(OffsetDateTime.now());
                }
            }
        }
        DomainEventType eventType = CommentEventType.COMMENT_CREATED;
        Comment comment = Comment.createFrom(ctx, ticketId, req);
        commentRepo.save(comment);
        UUID commentId = comment.getId().getId();
        JsonNode payloadData = AuditPayloadBuilder.forEntity(commentId, eventType)
                .addField("createdByUserId", null, comment.getAuthorUserId().toString())
                .addField("body", null, comment.getBody())
                .addField("visibility", null, comment.getVisibility().name())
                .addField("ticketId", null, ticketId.toString()).build();
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.COMMENT, commentId,
                eventType, DomainEventPayloads.envelopFrom(ctx, commentId, payloadData));
        outboxPublisher.publish(OutboxEvent.from(auditLog, DedupKeyFactory.forCreate(
                ctx.requestId(), eventType)));
//        return CommentResponse.from(comment);
    }

    @Transactional(readOnly = true)
    public Page<CommentSummary> list(RequestContext ctx, UUID ticketId, Pageable p) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        // TODO:: 或者可以把CommentAuth所有逻辑移动到TicketAuth
        ticketAuth.requireRead(ctx, ticketId, false);
        commentAuth.requireList(ctx);
        Pageable pageable = PageNormalizer.normalize(p, 100, 25, Sort.by(
                Sort.Order.asc("createdAt")), Set.of("createdAt"));
        UUID tenantId = ctx.tenantId();
        return ctx.role() == MembershipRole.ADMIN || ctx.role() == MembershipRole.AGENT ?
                commentRepo.listCommentsAllByTicket(tenantId, ticketId, pageable) :
                commentRepo.listCommentsPublicByTicket(tenantId, ticketId, pageable);
//        return comments.map(CommentResponse::from);
    }
}
