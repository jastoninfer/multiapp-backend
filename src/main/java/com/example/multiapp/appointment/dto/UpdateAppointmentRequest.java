package com.example.multiapp.appointment.dto;

import com.example.multiapp.appointment.model.AppointmentStatus;
import com.example.multiapp.appointment.model.AppointmentUpdateField;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.EnumSet;
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
        public boolean isEmpty() {
                return status == null && startAt == null && endAt == null
                        && addressText == null && notes == null && arrivedAt == null;
        }

        public boolean hasNotesOnly() {
                EnumSet<AppointmentUpdateField> fields = requestedFields();
                boolean removed = fields.remove(AppointmentUpdateField.NOTES);
                return removed && fields.isEmpty();
        }

        public boolean hasArrivedAtOnly() {
                EnumSet<AppointmentUpdateField> fields = requestedFields();
                boolean removed = fields.remove(AppointmentUpdateField.ARRIVED_AT);
                return removed && fields.isEmpty();
        }

        public boolean hasMarkCompleteOnly() {
                EnumSet<AppointmentUpdateField> fields = requestedFields();
                boolean removed = fields.remove(AppointmentUpdateField.STATUS);
                return removed && fields.isEmpty() && status == AppointmentStatus.COMPLETED;
        }

        public boolean hasRescheduledOnly() {
                if (addressText == null && notes == null && arrivedAt == null) {
                        if(status == AppointmentStatus.RESCHEDULED && (startAt != null && endAt != null)) {
                                return true;
                        }
                }
                return false;
        }

        public EnumSet<AppointmentUpdateField> requestedFields() {
                EnumSet<AppointmentUpdateField> fields = EnumSet.noneOf(AppointmentUpdateField.class);
                if(status != null) fields.add(AppointmentUpdateField.STATUS);
                if(startAt != null) fields.add(AppointmentUpdateField.START_AT);
                if(endAt != null) fields.add(AppointmentUpdateField.END_AT);
                if(addressText != null) fields.add(AppointmentUpdateField.ADDRESS_TEXT);
                if(notes != null) fields.add(AppointmentUpdateField.NOTES);
                if(arrivedAt != null) fields.add(AppointmentUpdateField.ARRIVED_AT);
                return fields;
        }
}
