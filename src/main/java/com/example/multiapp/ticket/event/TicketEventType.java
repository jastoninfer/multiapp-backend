package com.example.multiapp.ticket.event;

import com.example.multiapp.common.event.DomainEventType;

public enum TicketEventType implements DomainEventType {
    TICKET_CREATED,
    TICKET_STATUS_CHANGED,
    TICKET_ASSIGNEE_UPDATED,
    TICKET_UPDATED;

    @Override
    public String key() {
        return name();
    }
}
