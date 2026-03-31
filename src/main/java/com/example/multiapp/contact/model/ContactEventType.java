package com.example.multiapp.contact.model;

import com.example.multiapp.common.event.DomainEventType;

public enum ContactEventType implements DomainEventType {
    CONTACT_CREATED, CONTACT_UPDATED;

    @Override
    public String key() {
        return name();
    }
}
