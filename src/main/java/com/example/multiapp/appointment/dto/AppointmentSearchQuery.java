package com.example.multiapp.appointment.dto;

import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentSearchQuery(
        @Nullable UUID resourceUserId,
        @Nullable UUID ticketOwnerId,
        @Nullable UUID ticketId,
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to,
        @Nullable String status
) {
}
