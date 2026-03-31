package com.example.multiapp.attachment.dto;

import com.example.multiapp.attachment.entity.Attachment;
import org.springframework.core.io.Resource;

import java.util.Objects;

public record DownloadFile(
        Resource resource,
        String filename,
        String contentType,
        long sizeBytes
) {
    public static DownloadFile from(Resource resource, Attachment a) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(a, "attachment");
        return new DownloadFile(resource, a.getFilename(), a.getContentType(),
                a.getSizeBytes());
    }
}
