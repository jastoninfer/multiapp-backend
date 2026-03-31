package com.example.multiapp.contactclaim.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

import java.util.Objects;

public record ClaimRequest(
        @NotNull @NotBlank String code,
        @Nullable
        @Email(message = "email must be a valid address")
        String email,
        @Nullable
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format")
        String phone
) {
    public ClaimRequest {
        email = (email == null || email.strip().isBlank()) ? null : email.strip();
        phone = (phone == null || phone.strip().isBlank()) ? null : phone.strip();
        if(email == null && phone == null) {
            throw new IllegalArgumentException("at least one of email/phone should be provided to claim");
        }
    }
}
