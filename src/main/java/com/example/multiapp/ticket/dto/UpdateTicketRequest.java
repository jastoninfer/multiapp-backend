package com.example.multiapp.ticket.dto;

import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketType;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTicketRequest(
        @Nullable
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid title")
        @Size(max = 200)
        String title,
        @Nullable
        @Pattern(regexp = "^(?:[^\\p{Cc}]|[\\r\\n\\t])*$", message = "invalid description")
        @Size(max = 4000)
        String description,
        @Nullable
        TicketPriority priority,
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid location text")
        @Nullable  @Size(max=100)
        String locationText,
        @Nullable
        TicketType ticketType
) {
    public UpdateTicketRequest {
        title = normalizeOptional(title);
        description = normalizeOptional(description);
        locationText = normalizeOptional(locationText);
    }
    private static String normalizeOptional(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }
}
