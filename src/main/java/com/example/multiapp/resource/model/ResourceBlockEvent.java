package com.example.multiapp.resource.model;

import com.example.multiapp.common.event.DomainEventType;

public enum ResourceBlockEvent implements DomainEventType {
    RESOURCE_BLOCK_CREATED;

    @Override
    public String key() {
        return name();
    }
}
