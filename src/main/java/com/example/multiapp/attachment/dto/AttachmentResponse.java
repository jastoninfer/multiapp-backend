package com.example.multiapp.attachment.dto;

import com.example.multiapp.attachment.entity.Attachment;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record AttachmentResponse(
        UUID attachmentId,
        UUID ticketId,
        String filename,
        String contentType,
        long sizeBytes,
        OffsetDateTime createdAt,
        String downloadUrl
) {
    public static AttachmentResponse from(Attachment a, String downloadUrl) {
        Objects.requireNonNull(a, "attachment");
        Objects.requireNonNull(downloadUrl, "downloadUrl");
        return new AttachmentResponse(
                a.getId().getId(),
                a.getTicketId(),
                a.getFilename(),
                a.getContentType(),
                a.getSizeBytes(),
                a.getCreatedAt(), // 这个值不一定可靠, 可能为null
                downloadUrl
        );
    }
}
