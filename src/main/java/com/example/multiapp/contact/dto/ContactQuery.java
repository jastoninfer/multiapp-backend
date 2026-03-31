package com.example.multiapp.contact.dto;

import jakarta.annotation.Nullable;

public record ContactQuery(
        @Nullable String displayName,
        @Nullable
        String phone,
        @Nullable
        String email,
        @Nullable
        Boolean linked
) {
    public ContactQuery {
        phone = (phone == null || phone.strip().isBlank()) ? null : escapeLike(phone.strip());
        email = (email == null || email.strip().isBlank()) ? null : escapeLike(email.strip());
        displayName = (displayName == null || displayName.strip().isBlank()) ?
                null : escapeLike(displayName.strip());
    }
    private static String escapeLike(String s) {
        if (s == null) return null;
        return s.replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_");
    }
}
