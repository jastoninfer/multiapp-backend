package com.example.multiapp.outbox.entity;

public enum OutboxStatus {
    NEW, SENT, DEAD;
    public static boolean isAllowedTransition(OutboxStatus from, OutboxStatus to) {
        return from == NEW && (to == SENT || to == DEAD);
    }
}
