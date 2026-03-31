package com.example.multiapp.comment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentSummary(
        UUID id,
        UUID actorUserId,
        String authorName, // 跨表
        String body,
        OffsetDateTime createdAt,
        OffsetDateTime editedAt
) {
}
