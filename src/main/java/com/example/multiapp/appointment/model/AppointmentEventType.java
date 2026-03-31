package com.example.multiapp.appointment.model;

import com.example.multiapp.common.event.DomainEventType;

public enum AppointmentEventType implements DomainEventType {
    APPOINTMENT_CREATED,
    APPOINTMENT_UPDATED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_COMPLETED,
    APPOINTMENT_MARK_ARRIVED;

    @Override
    public String key() {
        return name();
    }
}
