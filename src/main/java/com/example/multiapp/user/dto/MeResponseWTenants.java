package com.example.multiapp.user.dto;

import java.util.List;
import java.util.Objects;

public record MeResponseWTenants(MeResponse meResponse, List<MeTenantResponse> tenants) {
    public MeResponseWTenants {
        Objects.requireNonNull(meResponse, "meResponse");
        Objects.requireNonNull(tenants, "tenants");
    }
}


