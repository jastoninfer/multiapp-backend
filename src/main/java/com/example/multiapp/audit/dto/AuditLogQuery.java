package com.example.multiapp.audit.dto;

import com.example.multiapp.audit.model.AuditEntityType;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AuditLogQuery(
        AuditEntityType entityType,
        UUID entityId,
        @Size(max = 120) String action,
        @Size(max = 120) String requestId
) {
}
