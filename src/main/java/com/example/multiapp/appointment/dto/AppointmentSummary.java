package com.example.multiapp.appointment.dto;

import com.example.multiapp.appointment.model.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentSummary(
        UUID id,
        long version,
        UUID ticketId,
        String ticketTitle,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        AppointmentStatus status,
        UUID resourceUserId,
        String resourceUserName, // 跨表查询
        String addressText
) { }
