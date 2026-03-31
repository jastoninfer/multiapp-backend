package com.example.multiapp.contact.dto;

import com.example.multiapp.contact.model.ContactType;
import jakarta.annotation.Nullable;
import jakarta.persistence.EmbeddedId;
import jakarta.validation.constraints.*;

import java.util.Objects;

public record CreateContactRequest(
        @Nullable ContactType contactType,
        @Nullable
        @Email(message = "email must be a valid address")
        String email,
        @Nullable
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format")
        String phone,
        @NotNull @NotBlank @Size(max=20)
        // 禁止ascii控制字符 + 不允许换行
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid name")
        String displayName
) {
    public CreateContactRequest {
        Objects.requireNonNull(displayName, "displayName");
        if(isBlank(email) && isBlank(phone)) {
            throw new IllegalArgumentException("at least one of [email] or [phone] is not null");
        }
        if(contactType == null) {
            // fallback to default
            contactType = ContactType.PERSON;
        }
        phone = normalizeOptional(phone);
        email = normalizeOptional(email);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String normalizeOptional(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }
}
