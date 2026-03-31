package com.example.multiapp.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateWorkingHoursRequest(
        @NotNull @NotBlank String timezone,
        @NotNull List<WorkingHoursRule> rules
) {
}
