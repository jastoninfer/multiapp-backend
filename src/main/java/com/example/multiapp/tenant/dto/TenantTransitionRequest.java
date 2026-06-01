package com.example.multiapp.tenant.dto;

import com.example.multiapp.tenant.model.TenantStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TenantTransitionRequest(
        @NotNull TenantStatus fromStatus,
        @NotNull TenantStatus toStatus,
        UUID targetTenantId
) {}
