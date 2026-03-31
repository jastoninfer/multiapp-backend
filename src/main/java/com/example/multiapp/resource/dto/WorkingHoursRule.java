package com.example.multiapp.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkingHoursRule(
        @Max(value = 7)  @Min(value = 1)
        int dayOfWeek, // 1-Mon, 7-Sun
        @NotNull @NotBlank
        String startLocal, // 8:00
        @NotNull @NotBlank
        String endLocal, // 17:00
        String timezone
) {
}
