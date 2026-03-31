package com.example.multiapp.appointment.dto;

import com.example.multiapp.appointment.model.AppointmentStatus;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateAppointmentRequest(
        @Nullable AppointmentStatus status,
        @Nullable OffsetDateTime startAt,
        @Nullable OffsetDateTime endAt,
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid address text")
        @Size(max=100) @Nullable String addressText,
        @Pattern(regexp = "^(?:[^\\p{Cc}]|[\\r\\n\\t])*$", message = "invalid notes")
        @Size(max = 500) @Nullable String notes,
        @Nullable OffsetDateTime arrivedAt
//        @Nullable UUID resourceUserId 变更resource_user不可用, 需要先取消再重新预约
) {
}
