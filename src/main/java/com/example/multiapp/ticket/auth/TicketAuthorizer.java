package com.example.multiapp.ticket.auth;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.ticket.dto.CreateTicketRequest;
import com.example.multiapp.ticket.dto.TicketQuery;
import com.example.multiapp.ticket.dto.UpdateTicketRequest;
import com.example.multiapp.ticket.model.TicketStatus;

import java.util.UUID;

public interface TicketAuthorizer {
    void requireCreate(RequestContext ctx, CreateTicketRequest req);
    void requireList(RequestContext ctx, TicketQuery query);
    void requireRead(RequestContext ctx, UUID ticketId, boolean needWrite);
    void requireUpdate(RequestContext ctx, UUID ticketId, UpdateTicketRequest req);
    void requireTransition(RequestContext ctx, UUID ticketId, TicketStatus newStatus);
    void requireReassign(RequestContext ctx, UUID ticketId, UUID newAssigneeId);
}
