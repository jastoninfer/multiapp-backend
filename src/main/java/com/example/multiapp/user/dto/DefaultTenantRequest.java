package com.example.multiapp.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DefaultTenantRequest(
        @NotNull UUID tenantId
) {}
