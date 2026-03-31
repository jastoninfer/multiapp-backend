package com.example.multiapp.appointment.dto;

import com.example.multiapp.appointment.model.AppointmentStatus;
import jakarta.annotation.Nullable;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentQuery(
        @Setter
        @Nullable UUID resourceUserId,
        @Nullable UUID ticketId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        // startAtFrom
        @Nullable OffsetDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        // startAtTo
        @Nullable OffsetDateTime to,
        @Nullable AppointmentStatus status
) {
}
