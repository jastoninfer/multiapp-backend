package com.example.multiapp.audit.repo;

import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.entity.AuditLogId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLogId> {
    Page<AuditLog> findByIdTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    Page<AuditLog> findByIdTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID tenantId, AuditEntityType entityType, UUID entityId, Pageable pageable
    );
    List<AuditLog> findByIdTenantIdAndRequestIdOrderByCreatedAtDesc(UUID tenantId, String requestId);
}
