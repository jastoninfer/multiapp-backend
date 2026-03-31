package com.example.multiapp.contact.dto;

import com.example.multiapp.contact.model.ContactType;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.sql.Update;

public record UpdateContactRequest(
        @Nullable
        @Size(max=20)
        // 禁止ascii控制字符 + 不允许换行
        @Pattern(regexp = "^[^\\p{Cc}\\r\\n]+$", message = "invalid name")
        String displayName,
        @Nullable @Email(message = "email must be a valid address")
        String email,
        @Nullable
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phone must be in E.164 format")
        String phone,
        @Nullable ContactType contactType
        ) {
    public UpdateContactRequest {
        email = normalizeOptional(email);
        phone = normalizeOptional(phone);
        displayName = normalizeOptional(displayName);
    }
    private static String normalizeOptional(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }
}
