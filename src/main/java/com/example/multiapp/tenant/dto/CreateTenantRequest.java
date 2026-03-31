package com.example.multiapp.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.UUID;

public record CreateTenantRequest(
        @Pattern(
                regexp = "^[\\p{L}\\p{N}](?:[\\p{L}\\p{N} .,&()'\\-_/]*[\\p{L}\\p{N}])?$",
                message = "invalid tenant name"
        )
        @NotNull @NotBlank @Size(max = 50) String name
) {
    public CreateTenantRequest {
        // 检验前normalize, lowercase放到入库前才做
        if (name != null) {
            name = name.strip().replaceAll("\\s+", " ");
        }
    }
    public String toStableString() {
        return "name=" + name.strip().toLowerCase(Locale.ROOT).
                replaceAll("\\s+", " ");
    }
}
