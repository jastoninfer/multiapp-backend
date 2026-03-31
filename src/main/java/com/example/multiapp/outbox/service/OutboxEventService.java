package com.example.multiapp.outbox.service;

import com.example.multiapp.idempotency.codec.IdempotencyResponseCodec;
import com.example.multiapp.outbox.entity.OutboxEvent;
import com.example.multiapp.outbox.repo.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {
    private final OutboxEventRepository outboxRepo;
    private final IdempotencyResponseCodec codec;
}
