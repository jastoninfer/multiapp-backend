package com.example.multiapp.resource.dto;

import com.example.multiapp.resource.entity.ResourceBlock;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResourceBlockResponse(
        UUID id,
        UUID resourceUserId,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String reason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
    public static ResourceBlockResponse from(ResourceBlock b) {
        return new ResourceBlockResponse(
                b.getId().getId(),
                b.getResourceUserId(),
                b.getStartAt(),
                b.getEndAt(),
                b.getReason(),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getVersion()
        );
    }
}
