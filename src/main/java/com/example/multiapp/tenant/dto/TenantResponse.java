package com.example.multiapp.tenant.dto;

import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.model.TenantStatus;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record TenantResponse(UUID id, String name,
                             TenantStatus status, OffsetDateTime createdAt) {
    public static TenantResponse from(Tenant tenant) {
        Objects.requireNonNull(tenant, "tenant");
        return new TenantResponse(
                Objects.requireNonNull(tenant.getId(), "tenant.id"),
                Objects.requireNonNull(tenant.getName(), "tenant.name"),
                Objects.requireNonNull(tenant.getStatus(), "tenant.status"),
                tenant.getCreatedAt() //如果是刚创建, 没有flush可能为null
        );
    }
}
