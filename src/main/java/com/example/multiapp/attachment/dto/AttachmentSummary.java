package com.example.multiapp.attachment.dto;

import com.example.multiapp.attachment.entity.Attachment;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public record AttachmentSummary(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String storageProvider,
        String storageKey,
        String downloadUrl,
        UUID uploadedByUserId,
        String uploadedByUserName,
        OffsetDateTime createdAt
) {
    public static AttachmentSummary from(AttachmentSummary a, String downloadUrl) {
        Objects.requireNonNull(a, "attachment");
        return new AttachmentSummary(
                a.id,
                a.filename,
                a.contentType,
                a.sizeBytes,
                a.storageProvider,
                a.storageKey,
                downloadUrl,
                a.uploadedByUserId,
                a.uploadedByUserName,
                a.createdAt
        );
    }
}
