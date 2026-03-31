package com.example.multiapp.resource.dto;

import com.example.multiapp.appointment.dto.AppointmentSummary;

import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
        UUID resourceUserId,
        List<ResourceBlockResponse> blocks,
        List<AppointmentSummary> appointments,
        List<WorkingHoursRule> workingHours
) {
}
