package com.example.multiapp.idempotency.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

public interface IdempotencyResponseCodec {
    <T> T read(String json, Class<T> type);
    String write(Object value);
}

@Component
class JacksonIdempotencyResponseCodec implements IdempotencyResponseCodec {
    private final ObjectMapper objectMapper;
    @Autowired
    JacksonIdempotencyResponseCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T read(String json, Class<T> type) {
        if(json == null || json.isBlank()){
            throw new IllegalStateException("COMPLETED idempotency record has empty response_json");
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupted response_json in idempotency record", e);
        }
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to serialize response_json", e);
        }
    }
}
