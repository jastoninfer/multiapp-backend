package com.example.multiapp.audit.entity;

import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.jpa.AuditedEntity;
import com.example.multiapp.common.jpa.CreatedOnlyEntity;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.tenant.event.TenantEventType;
import com.example.multiapp.tenant.model.TenantStatus;
import com.example.multiapp.ticket.event.TicketEventType;
import com.example.multiapp.ticket.model.TicketStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_log", schema = "app")
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog extends CreatedOnlyEntity {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private AuditLogId id;

    // null 表示系统生成的审计日志
    @EqualsAndHashCode.Include
    @ToString.Include
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private AuditEntityType entityType;

    @EqualsAndHashCode.Include
    @ToString.Include
    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @EqualsAndHashCode.Include
    @ToString.Include
    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json", columnDefinition = "jsonb", updatable = false)
    private JsonNode diffJson;

    @Column(name = "request_id", updatable = false)
    private String requestId;
    public static AuditLog ticketCreated(UUID tenantId, UUID actorUserId,
                                         UUID ticketId, String requestId) {
        return AuditLog.of(
          tenantId,
          actorUserId,
          AuditEntityType.TICKET,
          ticketId,
          TicketEventType.TICKET_CREATED,
          null,
          requestId
        );
    }

    public static AuditLog ticketStatusChanged(UUID tenantId, UUID actorUserId,
                                               UUID ticketId, JsonNode payload, String requestId) {
        Objects.requireNonNull(payload, "payload");
        return AuditLog.of(
                tenantId,
                actorUserId,
                AuditEntityType.TICKET,
                ticketId,
                TicketEventType.TICKET_STATUS_CHANGED,
                payload,
                requestId
        );
    }

    public static AuditLog tenantCreated(UUID tenantId, UUID actorUserId, String requestId) {
        return AuditLog.of(
                tenantId,
                actorUserId,
                AuditEntityType.TENANT,
                tenantId,
                TenantEventType.TENANT_CREATED,
                null,
                requestId
        );
    }

    public static AuditLog tenantStatusChanged(UUID tenantId, UUID actorUserId,
                                               JsonNode payload,
                                               String requestId) {
        Objects.requireNonNull(payload, "payload");
        return AuditLog.of(tenantId, actorUserId, AuditEntityType.TENANT,
                tenantId, TenantEventType.TENANT_STATUS_CHANGED, payload, requestId);
    }

    public static AuditLog tenantUpdated(UUID tenantId, UUID actorUserId, JsonNode payload,
                                         String requestId) {
        Objects.requireNonNull(payload, "payload");
        return AuditLog.of(tenantId, actorUserId, AuditEntityType.TENANT,
                tenantId, TenantEventType.TENANT_UPDATED, payload, requestId);
    }

    public static AuditLog from(RequestContext ctx, AuditEntityType entityType, UUID entityId,
                              DomainEventType action, JsonNode diffJson) {
        Objects.requireNonNull(ctx, "ctx");
        return of(ctx.tenantId(), ctx.userId(), entityType, entityId, action, diffJson, ctx.requestId());
    }

    public static AuditLog of(
            UUID tenantId,
            UUID actorUserId,
            AuditEntityType entityType,
            UUID entityId,
            DomainEventType action,
            JsonNode diffJson,
            String requestId
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(action, "action");

        var a = new AuditLog();
        a.id = new AuditLogId(tenantId, UUID.randomUUID());
        a.actorUserId = actorUserId;
        a.entityId = entityId;
        a.entityType = entityType;
        a.action = action.key();
        a.diffJson = diffJson;
        a.requestId = requestId;
        return a;
    }
}
