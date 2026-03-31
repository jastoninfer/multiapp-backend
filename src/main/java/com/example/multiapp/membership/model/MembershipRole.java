package com.example.multiapp.membership.model;

public enum MembershipRole {
    ADMIN, AGENT, RESOURCE_USER, CUSTOMER;
    public boolean canBeTicketOwner() {
        return this == ADMIN || this == AGENT;
    }
}