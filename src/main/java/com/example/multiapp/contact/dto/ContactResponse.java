package com.example.multiapp.contact.dto;

import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contact.model.ContactType;

import java.util.Objects;
import java.util.UUID;

public record ContactResponse(
        UUID tenantId,
        UUID contactId,
        String contactType,
        String email,
        String phone,
        String displayName,
        UUID linkedUserId,
        UUID createdByUserId,
        long version
) {
    public static ContactResponse from(Contact c) {
        Objects.requireNonNull(c, "contact");
        return new ContactResponse(
                c.getId().getTenantId(),
                c.getId().getId(),
                c.getContactType().toString(),
                c.getEmail(),
                c.getPhone(),
                c.getDisplayName(),
                c.getLinkedUserId(),
                c.getCreatedByUserId(),
                c.getVersion()
        );
    }
}
