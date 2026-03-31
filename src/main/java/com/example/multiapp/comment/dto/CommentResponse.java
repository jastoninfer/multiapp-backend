package com.example.multiapp.comment.dto;

import com.example.multiapp.comment.entity.Comment;
import com.example.multiapp.comment.model.CommentVisibility;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID tenantId,
        UUID ticketId,
        UUID authorId,
        CommentVisibility visibility,
        String body,
        OffsetDateTime createdAt
) {
    public static CommentResponse from(Comment c) {
        Objects.requireNonNull(c, "comment");
        return new CommentResponse(
                c.getId().getId(),
                c.getId().getTenantId(),
                c.getTicketId(),
                c.getAuthorUserId(),
                c.getVisibility(),
                c.getBody(),
                c.getCreatedAt()
        );
    }
}
