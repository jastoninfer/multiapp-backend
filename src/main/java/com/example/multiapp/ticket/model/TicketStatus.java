package com.example.multiapp.ticket.model;

import jakarta.validation.constraints.NotNull;

public enum TicketStatus {
    NEW, IN_PROGRESS, CLOSED, REOPENED;

    public static boolean isAllowedTransition(TicketStatus from, TicketStatus to) {
        return switch (from) {
            case NEW, REOPENED -> to == IN_PROGRESS;
            case IN_PROGRESS -> to == CLOSED;
            case CLOSED -> to == REOPENED;
        };
    }
}
