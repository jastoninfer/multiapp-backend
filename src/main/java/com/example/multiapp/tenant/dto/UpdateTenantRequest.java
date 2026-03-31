package com.example.multiapp.tenant.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @Pattern(
                regexp = "^[\\p{L}\\p{N}](?:[\\p{L}\\p{N} .,&()'\\-_/]*[\\p{L}\\p{N}])?$",
                message = "invalid tenant name"
        )
        @Size(max = 50) String name
) {
    public UpdateTenantRequest {
            // 检验前normalize, lowercase放到入库前才做
            if (name != null) {
                    name = name.strip().replaceAll("\\s+", " ");
            }
    }
}
