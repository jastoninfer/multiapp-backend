package com.example.multiapp.tenant.auth;

import java.util.Objects;
import java.util.UUID;

public record TenantActionPayload(UUID tenantId) {
    public TenantActionPayload {
        Objects.requireNonNull(tenantId, "tenantId");
    }
}
