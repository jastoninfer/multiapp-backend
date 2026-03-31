package com.example.multiapp.ticket.dto;

import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketStatus;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record TicketCreatedResponse(
        UUID id,
        long version,
        TicketStatus status
) {
    public static TicketCreatedResponse from(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return new TicketCreatedResponse(
                ticket.getId().getId(),
                ticket.getVersion(),
                ticket.getStatus()
        );
    }
}
