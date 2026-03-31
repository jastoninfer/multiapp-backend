package com.example.multiapp.appointment.dto;

import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.model.AppointmentStatus;

import java.util.Objects;
import java.util.UUID;

public record AppointmentCreatedResponse(
        UUID appointmentId,
        long version,
        AppointmentStatus status
) {
    public AppointmentCreatedResponse {
        Objects.requireNonNull(appointmentId, "appointmentId");
        Objects.requireNonNull(status, "appointment status");
    }

    public static AppointmentCreatedResponse from(Appointment a) {
        Objects.requireNonNull(a, "appointment");
        return new AppointmentCreatedResponse(
                a.getId().getId(),
                a.getVersion(),
                a.getStatus()
        );
    }
}
