package com.example.multiapp.contactclaim.service;

import com.example.multiapp.audit.entity.AuditLog;
import com.example.multiapp.audit.model.AuditEntityType;
import com.example.multiapp.common.aduit.AuditPayloadBuilder;
import com.example.multiapp.common.aduit.AuditWriter;
import com.example.multiapp.common.api.ConflictException;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.crypto.Hashing;
import com.example.multiapp.common.event.DomainEventPayloads;
import com.example.multiapp.common.event.DomainEventType;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contact.repo.ContactRepository;
import com.example.multiapp.contactclaim.auth.ContactClaimAuthorizer;
import com.example.multiapp.contactclaim.dto.ClaimCodeRequest;
import com.example.multiapp.contactclaim.dto.ClaimCodeResponse;
import com.example.multiapp.contactclaim.dto.ClaimRequest;
import com.example.multiapp.contactclaim.dto.ClaimResult;
import com.example.multiapp.contactclaim.entity.ContactClaim;
import com.example.multiapp.contactclaim.model.ContactClaimEventType;
import com.example.multiapp.contactclaim.repo.ContactClaimRepository;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactClaimService {
    private final ContactRepository contactRepo;
    private final ContactClaimRepository contactClaimRepo;
    private final TenantMembershipRepository membershipRepo;
    private final ContactClaimAuthorizer contactClaimAuth;
    private final AuditWriter auditWriter;

    @Transactional
    public ClaimCodeResponse issueClaimCode(RequestContext ctx, UUID contactId, ClaimCodeRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(contactId, "contactId");
        UUID tenantId = ctx.tenantId();
        // 权限: STAFF/ADMIN
        contactClaimAuth.requireIssueClaim(ctx, contactId);
        Contact contact = contactRepo.findByIdTenantIdAndIdId(tenantId, contactId)
                .orElseThrow(() -> new NotFoundException("contact: [%s] not found".formatted(contactId)));
        // 已绑定contact不再发码
        if(contact.getLinkedUserId() != null) throw new ConflictException("contact already linked");
        // 如已有有效claim, 直接拒绝或复用(建议拒绝并提示)
        contactClaimRepo.findLatestActiveByContact(tenantId, contactId).ifPresent(
                existing -> {
                    throw new ConflictException("active claim already exists");
                }
        );
        String code = generateCode();
        String hashed = Hashing.sha256Hex(code);
        int expiresInMinutes = req == null ? 0 : req.expiresInMinutes();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(
                expiresInMinutes <= 0 ? 60 : Math.min(180, expiresInMinutes));
        contactClaimRepo.save(ContactClaim.issue(
                tenantId, contactId, hashed, expiresAt, ctx.userId()));
        return new ClaimCodeResponse(code, expiresAt);
    }

    @Transactional
    public ClaimResult claim(RequestContext ctx, ClaimRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(req, "req");
        contactClaimAuth.requireConsumeClaim(ctx);
        String hashed = Hashing.sha256Hex(req.code().strip());
        // select for update 锁行
        ContactClaim claim = contactClaimRepo.findActiveByCodeHashForUpdate(hashed)
                .orElseThrow(() -> new NotFoundException("claim not found"));
        UUID tenantId = claim.getId().getTenantId();
        UUID contactId = claim.getContactId();
        // 锁住contact行(避免并发link/unlink),
        Contact contact = contactRepo.findByIdTenantIdAndIdIdForUpdate(tenantId, contactId)
                .orElseThrow(() -> new NotFoundException("contact not found"));
        // 校验contact未被别人绑定
        UUID linked = contact.getLinkedUserId();
        if(linked != null && !linked.equals(ctx.userId())) {
            throw new ConflictException("contact already linked");
        }
        // 校验email/phone 匹配contact, 至少传一个, 传了就必须匹配
        boolean matched = req.email() == null || req.email().equals(contact.getEmail());
        if(req.phone() != null && !req.phone().equals(contact.getPhone())) matched = false;
        if(!matched) throw new AccessDeniedException("claim identity mismatch");
        // 绑定contact
        if(linked == null) {
            contact.setLinkedUserId(ctx.userId());
//            contactRepo.save(contact);
            JsonNode payloadData = AuditPayloadBuilder.forEntity(contactId,
                    ContactClaimEventType.CONTACT_CLAIMED).addField("linked_to",
                    null, ctx.userId().toString()).build();
            auditWriter.append(AuditLog.from(ctx, AuditEntityType.CONTACT, contactId,
                    ContactClaimEventType.CONTACT_CLAIMED, DomainEventPayloads.envelopFrom(
                            ctx, contactId, payloadData)));
        }
        // 消费claim, 返回boolean, 为false表示已经消费过
        if(!claim.tryConsume(ctx.userId(), OffsetDateTime.now())) {
            // 已经消费过
            throw new IllegalArgumentException("claim consumed already");
        }
//        contactClaimRepo.save(claim);
        JsonNode payloadData = AuditPayloadBuilder.forEntity(claim.getId().getId(),
                ContactClaimEventType.CONTACT_CLAIM_CONSUMED).addField("consumed_by",
                null, ctx.userId().toString()).build();
        auditWriter.append(AuditLog.from(ctx, AuditEntityType.CONTACT_CLAIM, claim.getId().getId(),
                ContactClaimEventType.CONTACT_CLAIM_CONSUMED, DomainEventPayloads.envelopFrom(
                        ctx, claim.getId().getId(), payloadData)));
        return new ClaimResult(tenantId, contactId);
    }

    private static String generateCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                .toUpperCase(Locale.ROOT);
    }
}
