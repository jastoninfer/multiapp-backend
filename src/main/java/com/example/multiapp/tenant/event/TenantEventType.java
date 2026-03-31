package com.example.multiapp.tenant.event;

import com.example.multiapp.common.event.DomainEventType;

public enum TenantEventType implements DomainEventType {
    TENANT_CREATED, TENANT_UPDATED, TENANT_STATUS_CHANGED;

    @Override
    public String key() {
        return name();
    }
}
