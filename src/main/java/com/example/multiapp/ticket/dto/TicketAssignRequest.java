package com.example.multiapp.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TicketAssignRequest(
        @NotNull UUID newAssigneeId) {}
