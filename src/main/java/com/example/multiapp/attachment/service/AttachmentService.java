package com.example.multiapp.attachment.service;

import com.example.multiapp.attachment.dto.AttachmentResponse;
import com.example.multiapp.attachment.dto.AttachmentSummary;
import com.example.multiapp.attachment.dto.DownloadFile;
import com.example.multiapp.attachment.entity.Attachment;
import com.example.multiapp.attachment.entity.AttachmentId;
import com.example.multiapp.attachment.model.StorageProviderType;
import com.example.multiapp.attachment.repo.AttachmentRepository;
import com.example.multiapp.attachment.storage.AttachmentStorage;
import com.example.multiapp.common.api.ForbiddenException;
import com.example.multiapp.common.api.NotFoundException;
import com.example.multiapp.common.api.PageNormalizer;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.tenant.TenantContextInterceptor;
import com.example.multiapp.ticket.auth.TicketAuthorizer;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketStatus;
import com.example.multiapp.ticket.repo.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.internal.xml.mapping.MappingXmlParser;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor

public class AttachmentService {
    private static final long MAX_BYTES = 20 * 1024 * 1024; // 20MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "application/pdf"
    );
    private final AttachmentRepository attachmentRepo;
    private final TicketRepository ticketRepo;
    private final AttachmentStorage storage;
    private final TicketAuthorizer ticketAuth;

    @Transactional
    public AttachmentResponse uploadOne(RequestContext ctx, UUID ticketId, MultipartFile file)
        throws IOException {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Ticket t = ticketRepo.findByIdTenantIdAndIdId(ctx.tenantId(), ticketId).orElseThrow(
                () -> new NotFoundException("ticket not found")
        );
        if(t.getStatus() == TicketStatus.CLOSED) {
            throw new ForbiddenException("ticket has been closed");
        }
        validateFile(file);
        ticketAuth.requireRead(ctx, ticketId, true);
        // 不允许给CLOSED ticket上传附件, admin/agent/customer均不行
        Attachment attachment = Attachment.createFrom(ctx, ticketId, file, StorageProviderType.LOCAL);
        String key = storage.save(ctx.tenantId(), ticketId, attachment.getId().getId(), file);
        attachment.setStorageKey(key);
        attachmentRepo.save(attachment);
        // sha256 可选: 不算就保持null, 若计算建议流式计算避免内存暴涨
        return AttachmentResponse.from(attachment, downloadUrl(ticketId, attachment.getId().getId()));
    }

    @Transactional(readOnly = true)
    public List<AttachmentSummary> listByTicket(RequestContext ctx, UUID ticketId, Pageable p) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        ticketAuth.requireRead(ctx, ticketId, false);
        Pageable pageable = PageNormalizer.normalize(p, 25, 10, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id.id")),
                Set.of("createdAt", "id.id", "filename", "contentType"));
        return attachmentRepo.listSummariesByTicket(ctx.tenantId(),
                ticketId, pageable).stream().map(s -> AttachmentSummary.from(s,
                downloadUrl(ticketId, s.id()))).toList();
    }

    @Transactional(readOnly = true)
    public DownloadFile download(RequestContext ctx, UUID ticketId, UUID attachmentId) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(attachmentId, "attachmentId");
        ticketAuth.requireRead(ctx, ticketId, false);
        Attachment a = attachmentRepo.findByIdTenantIdAndIdIdAndTicketId(ctx.tenantId(),
                attachmentId, ticketId)
                .orElseThrow(() -> new NotFoundException("Attachment: [%s] not found".formatted(attachmentId)));
        Resource resource = storage.loadAsResource(a.getStorageKey());
        if(!resource.exists()) throw new NotFoundException("File missing");
        return DownloadFile.from(resource, a);
    }

    private static void validateFile(MultipartFile file) {
        Objects.requireNonNull(file, "file");
        if(file.isEmpty()) throw new IllegalArgumentException("file is empty");
        if(file.getSize() > MAX_BYTES) throw new IllegalArgumentException("file too large");
        String ct = file.getContentType();
        if(ct == null || !ALLOWED_TYPES.contains(ct))
            throw new IllegalArgumentException("unsupported content type: " + ct);
    }

    private static String downloadUrl(UUID ticketId, UUID attachmentId) {
        return "/tickets/" + ticketId + "/attachments/" + attachmentId + "/download";
    }
}
