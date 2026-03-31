package com.example.multiapp.contact.service;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.api.PageNormalizer;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.IfMatchPreconditions;
import com.example.multiapp.contact.auth.ContactAuthorizer;
import com.example.multiapp.contact.dto.ContactQuery;
import com.example.multiapp.contact.dto.ContactResponse;
import com.example.multiapp.contact.dto.CreateContactRequest;
import com.example.multiapp.contact.dto.UpdateContactRequest;
import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contact.entity.ContactId;
import com.example.multiapp.contact.model.ContactEventType;
import com.example.multiapp.contact.repo.ContactRepository;
import com.example.multiapp.ticket.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.handler.AbstractDetectingUrlHandlerMapping;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactService {
    private final ContactRepository contactRepo;
    private final ContactAuthorizer contactAuth;
    private final AuditWriter auditWriter;

    @Transactional
    public ContactResponse addContact(RequestContext ctx, CreateContactRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(req, "CreateContactRequest");
        UUID tenantId = ctx.tenantId();
        contactAuth.requireCreate(ctx);
        DomainEventType eventType = ContactEventType.CONTACT_CREATED;
        Contact contact = Contact.from(tenantId, ctx.userId(), req);
        contactRepo.save(contact);
        UUID contactId = contact.getId().getId();
        JsonNode payloadData = AuditPayloadBuilder.forEntity(contactId, eventType)
                .addField("createdByUserId", null, contact.getCreatedByUserId().toString())
                .addField("email", null, contact.getEmail())
                .addField("phone", null, contact.getPhone())
                .addField("displayName", null, contact.getDisplayName())
                .build();
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.CONTACT, contactId,
                eventType, DomainEventPayloads.envelopFrom(ctx, contactId, payloadData));
        auditWriter.append(auditLog);
        // 不写outbox
        return ContactResponse.from(contact);
    }

    @Transactional(readOnly = true)
    public Page<ContactResponse> list(RequestContext ctx, ContactQuery q, Pageable p) {
        Objects.requireNonNull(ctx, "ctx");
        contactAuth.requireList(ctx);
        Pageable pageable = PageNormalizer.normalize(p, 100, 20, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id.id")),
                Set.of("createdAt", "id.id", "displayName", "phone", "email", "contactType",
                        "linkedUserId", "createdByUserId"));
        UUID tenantId = ctx.tenantId();
        return contactRepo.searchContacts(tenantId, q, pageable).map(ContactResponse::from);
    }

    @Transactional(readOnly = true)
    public ContactResponse get(RequestContext ctx, UUID contactId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(contactId, "contactId");
        contactAuth.requireRead(ctx);
        UUID tenantId = ctx.tenantId();
        return ContactResponse.from(contactRepo.findByIdTenantIdAndIdId(tenantId, contactId)
                .orElseThrow(() -> new NotFoundException("contact: [%s] not found under tenant: [%s]"
                        .formatted(contactId, tenantId))));
    }

    @Transactional
    public void update(RequestContext ctx, UUID contactId, @NotBlank String ifMatch,
                       @NotNull UpdateContactRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ctx.tenantId(), "ctx.tenantId");
        Objects.requireNonNull(ctx.requestId(), "ctx.requestId");
        Objects.requireNonNull(ifMatch, "ifMatch");
        Objects.requireNonNull(contactId, "contactId");
        Objects.requireNonNull(req, "UpdateContactRequest");
        UUID tenantId = ctx.tenantId();
        Contact contact = contactRepo.findByIdTenantIdAndIdIdForUpdate(tenantId, contactId)
                .orElseThrow(() -> new NotFoundException("contact: [%s] not found under tenant: [%s]"
                        .formatted(contactId, tenantId)));
        IfMatchPreconditions.require(ifMatch, contact.getVersion());
        contactAuth.requireUpdate(ctx);
        DomainEventType eventType = ContactEventType.CONTACT_UPDATED;
        AuditPayloadBuilder builder = AuditPayloadBuilder.forEntity(contactId, eventType);
        boolean updated = false;
        if(req.contactType() != null && req.contactType() != contact.getContactType()) {
            String fromType = contact.getContactType().toString();
            String toType = req.contactType().toString();
            contact.changeContactType(req.contactType());
            builder.addField("contactType", fromType, toType);
            updated = true;
        }
        if(req.displayName() != null && !req.displayName().equals(contact.getDisplayName())) {
            builder.addField("displayName", contact.getDisplayName(), req.displayName());
            contact.changeDisplayName(req.displayName());
            updated = true;
        }
        if(req.phone() != null && !req.phone().equals(contact.getPhone())) {
            builder.addField("phone", contact.getPhone(), req.phone());
            updated = true;
        }
        if(req.email() != null && !req.email().equals(contact.getEmail())) {
            builder.addField("email", contact.getEmail(), req.email());
            updated = true;
        }
        if(!updated) return;
        AuditLog auditLog = AuditLog.from(ctx, AuditEntityType.CONTACT, contactId, eventType,
                DomainEventPayloads.envelopFrom(ctx, contactId, builder.build()));
        auditWriter.append(auditLog);
    }
}
