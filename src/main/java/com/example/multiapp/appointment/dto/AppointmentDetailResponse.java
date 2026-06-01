package com.example.multiapp.appointment.dto;

import com.example.multiapp.appointment.model.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentDetailResponse(
        UUID id,
        long version,
        UUID ticketId,
        String ticketTitle,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        AppointmentStatus status,
        UUID resourceUserId,
        String resourceUserName, //跨表数据
        String addressText,
        String notes,
        OffsetDateTime arrivedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
