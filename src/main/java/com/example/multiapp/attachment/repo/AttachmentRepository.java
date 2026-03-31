package com.example.multiapp.attachment.repo;

import com.example.multiapp.attachment.dto.AttachmentSummary;
import com.example.multiapp.attachment.entity.Attachment;
import com.example.multiapp.attachment.entity.AttachmentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, AttachmentId> {
    // downloadUrl: 这里置null, 具体service里生成
    @Query("""
    select new com.example.multiapp.attachment.dto.AttachmentSummary(
        a.id.id, a.filename, a.contentType, a.sizeBytes, cast(a.storageProvider as string),
        a.storageKey, null, a.uploadedByUserId, a.createdAt
    ) from Attachment a left join AppUser u on u.id = a.uploadedByUserId
    where a.id.tenantId = :tenantId and a.ticketId = :ticketId and a.deletedAt is null
    """)
    List<AttachmentSummary> listSummariesByTicket(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId")UUID ticketId,
            Pageable pageable
    );

    long countByIdTenantIdAndTicketIdAndDeletedAtIsNull(UUID tenantId, UUID ticketId);

    Optional<Attachment> findByIdTenantIdAndIdId(UUID tenantId, UUID attachmentId);

    // 防止“拿到 attachmentId 但 ticketId 不匹配”
    Optional<Attachment> findByIdTenantIdAndIdIdAndTicketId(UUID tenantId, UUID attachmentId, UUID ticketId);
}
