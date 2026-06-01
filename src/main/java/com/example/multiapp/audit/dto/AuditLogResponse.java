package com.example.multiapp.audit.dto;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record AuditLogResponse(
        UUID tenantId,
        UUID auditLogId,
        UUID actorUserId,
        AuditEntityType entityType,
        UUID entityId,
        String action,
        JsonNode diffJson,
        String requestId,
        OffsetDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        Objects.requireNonNull(auditLog, "auditLog");
        return new AuditLogResponse(
                auditLog.getId().getTenantId(),
                auditLog.getId().getId(),
                auditLog.getActorUserId(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getAction(),
                auditLog.getDiffJson(),
                auditLog.getRequestId(),
                auditLog.getCreatedAt()
        );
    }
}
