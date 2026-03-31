package com.example.multiapp.tenant.dto;

import com.example.multiapp.tenant.model.TenantStatus;
import jakarta.validation.constraints.NotNull;

public record TenantTransitionRequest(
        @NotNull TenantStatus fromStatus,
        @NotNull TenantStatus toStatus
) {}
