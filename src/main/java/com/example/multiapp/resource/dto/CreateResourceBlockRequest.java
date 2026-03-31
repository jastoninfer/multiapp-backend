package com.example.multiapp.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateResourceBlockRequest(
        @NotNull
        OffsetDateTime startAt,
        @NotNull
        OffsetDateTime endAt,
        @NotNull @NotBlank
        @Pattern(regexp = "^(?:[^\\p{Cc}]|[\\r\\n\\t])*$", message = "invalid reason")
        @Size(max = 200)
        String reason
) {
}
