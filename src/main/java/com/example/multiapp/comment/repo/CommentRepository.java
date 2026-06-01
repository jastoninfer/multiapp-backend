package com.example.multiapp.comment.repo;

import com.example.multiapp.attachment.dto.AttachmentSummary;
import com.example.multiapp.attachment.entity.Attachment;
import com.example.multiapp.attachment.entity.AttachmentId;
import com.example.multiapp.comment.dto.CommentSummary;
import com.example.multiapp.comment.entity.Comment;
import com.example.multiapp.comment.entity.CommentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, CommentId> {
    @Query("""
    select new com.example.multiapp.comment.dto.CommentSummary(
        c.id.id, c.authorUserId, u.displayName, cast(m.role as string), c.body,
             cast(c.visibility as string),
                 c.createdAt, c.editedAt
    ) from Comment c left join AppUser u on u.id = c.authorUserId left join TenantMembership m
        on (c.authorUserId = m.id.userId and m.id.tenantId = :tenantId)
    where c.id.tenantId = :tenantId and c.ticketId = :ticketId and c.deletedAt is null
    """)
    List<CommentSummary> listSummariesByTicket(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId")UUID ticketId,
            Pageable pageable
    );

    @Query("""
    select new com.example.multiapp.comment.dto.CommentSummary(
        c.id.id, c.authorUserId, u.displayName, cast(m.role as string), c.body,
             cast(c.visibility as string),
                 c.createdAt, c.editedAt
    ) from Comment c left join AppUser u on u.id = c.authorUserId left join TenantMembership m
        on (c.authorUserId = m.id.userId and m.id.tenantId = :tenantId)
    where c.id.tenantId = :tenantId and c.ticketId = :ticketId and c.deletedAt is null
    """)
    Page<CommentSummary> listCommentsAllByTicket(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId") UUID ticketId,
            Pageable pageable
    );

    @Query("""
    select new com.example.multiapp.comment.dto.CommentSummary(
        c.id.id, c.authorUserId, u.displayName, cast(m.role as string), c.body,
             cast(c.visibility as string),
                 c.createdAt, c.editedAt
    ) from Comment c left join AppUser u on u.id = c.authorUserId left join TenantMembership m
        on (c.authorUserId = m.id.userId and m.id.tenantId = :tenantId)
    where c.id.tenantId = :tenantId and c.ticketId = :ticketId and c.deletedAt is null
        and c.visibility = 'PUBLIC'
    """)
    Page<CommentSummary> listCommentsPublicByTicket(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId") UUID ticketId,
            Pageable pageable
    );

    long countByIdTenantIdAndTicketIdAndDeletedAtIsNull(UUID tenantId, UUID ticketId);
}
