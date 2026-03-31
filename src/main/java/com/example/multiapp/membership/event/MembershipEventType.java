package com.example.multiapp.membership.event;

import com.example.multiapp.common.event.DomainEventType;

public enum MembershipEventType implements DomainEventType {
    MEMBERSHIP_CREATED,
    MEMBERSHIP_UPDATED,
    MEMBERSHIP_DELETED;

    @Override
    public String key() {
        return name();
    }
}
