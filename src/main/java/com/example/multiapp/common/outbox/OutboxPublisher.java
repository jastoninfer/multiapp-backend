package com.example.multiapp.common.outbox;

import com.example.multiapp.outbox.entity.OutboxEvent;

public interface OutboxPublisher {
    int publish(OutboxEvent outboxEvent);
}
