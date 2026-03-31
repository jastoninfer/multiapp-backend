package com.example.multiapp.contact.entity;

import com.example.multiapp.common.jpa.AuditedEntity;
import com.example.multiapp.contact.dto.CreateContactRequest;
import com.example.multiapp.contact.model.ContactType;
import com.example.multiapp.ticket.dto.CreateTicketRequest;
import com.nimbusds.jose.crypto.impl.ConcatKDF;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "contact", schema = "app")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class Contact extends AuditedEntity {
    @EmbeddedId
    @ToString.Include
    @EqualsAndHashCode.Include
    private ContactId id;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false)
    private ContactType contactType;

    @ToString.Include
    @Column(name = "email")
    private String email;

    @ToString.Include
    @Column(name = "phone")
    private String phone;

    @Column(name = "email_normalized", nullable = true)
    private String emailNormalized;

    @Column(name = "phone_normalized", nullable = true)
    private String phoneNormalized;

    @ToString.Include
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Setter
    @Column(name = "linked_user_id", nullable = true)
    private UUID linkedUserId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static Contact from(UUID tenantId, UUID actorUserId, CreateContactRequest req) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(req, "createTicketRequest");
        Contact contact = new Contact();
        contact.id = new ContactId(tenantId, UUID.randomUUID());
        contact.contactType = req.contactType();
        contact.phone = req.phone();
        contact.phoneNormalized = req.phone();
        contact.email = req.email();
        contact.emailNormalized = req.email();
        contact.displayName = req.displayName();
        contact.linkedUserId = null;
        contact.createdByUserId = actorUserId;
        return contact;
    }

    private static void requireNonBlank(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
    }

    public void changeDisplayName(String newName) {
        this.displayName = newName;
    }

    public void changePhone(String newPhone) {
        this.phone = newPhone;
        this.phoneNormalized = newPhone;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
        this.emailNormalized = newEmail;
    }

    public void changeContactType(ContactType newType) {
        this.contactType = newType;
    }
}
