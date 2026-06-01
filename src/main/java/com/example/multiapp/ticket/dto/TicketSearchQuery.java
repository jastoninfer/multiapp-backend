package com.example.multiapp.ticket.dto;

import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketSearchQuery(
        @Nullable String ticketStatus,
        @Nullable String ticketPriority,
        @Nullable UUID ownerId,
        @Nullable UUID requesterUserId,
        @Nullable UUID requesterContactId,
        @Nullable String ticketType,
        @Nullable String q,
        @Nullable OffsetDateTime createdFrom,
        @Nullable OffsetDateTime createdTo
) {
    public static TicketSearchQuery empty() {
        return new TicketSearchQuery(null, null, null, null,
                null, null, null, null,null);
    }
}
