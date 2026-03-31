package com.example.multiapp.appointment.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull UUID resourceUserId,
        @Nullable UUID customerUserId,
        @Nullable UUID customerContactId,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid address text")
        @Nullable
        @Size(max=100) String addressText,
        @Pattern(regexp = "^(?:[^\\p{Cc}]|[\\r\\n\\t])*$", message = "invalid notes")
        @Nullable
        @Size(max = 500) String notes
) {
        public CreateAppointmentRequest {
                if(Objects.isNull(customerUserId) == Objects.isNull(customerContactId)) {
                        throw new IllegalArgumentException("exactly one of customer user id " +
                                "and customer contact id should be null");
                }
        }
}
