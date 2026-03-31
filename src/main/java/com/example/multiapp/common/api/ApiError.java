package com.example.multiapp.common.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(String code, String message, List<FieldViolation> violations,
                       OffsetDateTime timestamp) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of(), OffsetDateTime.now());
    }

    public static ApiError of(String code, String message, List<FieldViolation> v) {
        return new ApiError(code, message, v == null ? List.of() : v, OffsetDateTime.now());
    }

    public record FieldViolation(String field, String message) {}

}
