package com.example.multiapp.contactclaim.dto;

import java.time.OffsetDateTime;
import java.util.Objects;

public record ClaimCodeResponse(
        String code,
        OffsetDateTime expiresAt
) {
    public ClaimCodeResponse {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
