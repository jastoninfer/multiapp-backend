package com.example.multiapp.user.dto;

import com.example.multiapp.tenant.model.TenantStatus;
import com.example.multiapp.user.model.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserTransitionRequest(
        @NotNull UserStatus fromStatus,
        @NotNull UserStatus toStatus
) {}
