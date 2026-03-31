package com.example.multiapp.user.event;

import com.example.multiapp.common.event.DomainEventType;

public enum UserEventType implements DomainEventType {
    USER_STATUS_CHANGED;
    @Override
    public String key() {
        return name();
    }
}
