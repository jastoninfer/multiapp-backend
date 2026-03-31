package com.example.multiapp.common.outbox;

import com.example.multiapp.idempotency.codec.IdempotencyResponseCodec;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.outbox.repo.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class OutboxPublisherImpl implements OutboxPublisher{
    private final OutboxEventRepository outboxRepo;
    private final IdempotencyResponseCodec codec;

    @Override
    @Transactional
    public int publish(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        return outboxRepo.insertDedupNew(
                event.getId().getTenantId(),
                event.getId().getId(),
                event.getDedupKey(),
                event.getEventType(),
                codec.write(event.getPayloadJson()));
    }
}
