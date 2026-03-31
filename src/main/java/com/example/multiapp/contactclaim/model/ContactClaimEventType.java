package com.example.multiapp.contactclaim.model;

import com.example.multiapp.common.event.DomainEventType;

public enum ContactClaimEventType implements DomainEventType {
    CONTACT_CLAIMED,
    CONTACT_CLAIM_CONSUMED;

    @Override
    public String key() {
        return name();
    }
}
