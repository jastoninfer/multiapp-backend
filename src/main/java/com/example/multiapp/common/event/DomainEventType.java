package com.example.multiapp.common.event;

public interface DomainEventType {
    String key(); // 稳定字符串, 持久化到outbox_event.event_type与audit_log.action
}
