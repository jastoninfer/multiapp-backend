package com.example.multiapp.ticket.dto;

import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketStatus;
import com.example.multiapp.ticket.model.TicketType;
import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketQuery(
        @Nullable TicketStatus ticketStatus,
        @Nullable TicketPriority ticketPriority,
        @Nullable UUID ownerId,
        @Nullable UUID requesterUserId,
        @Nullable UUID requesterContactId, //requesterUserId+requesterContactId至多一个非null
        @Nullable TicketType ticketType,
        @Nullable OffsetDateTime createdFrom, // 创建时间
        @Nullable OffsetDateTime createdTo
        ) {
    public TicketQuery {
        if(requesterContactId != null && requesterUserId != null) {
            throw new IllegalArgumentException("at least one of requester [contactId] or [userId] should be null");
        }
    }
    public static TicketQuery empty() {
        return new TicketQuery(null, null, null, null,
                null, null, null, null);
    }
}
