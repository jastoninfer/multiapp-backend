package com.example.multiapp.user.dto;

import java.util.UUID;

public record MeTenantResponse(UUID tenantId, String name,
                               String role, boolean isDefault) {}