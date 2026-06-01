package com.example.multiapp.attachment.entity;

import com.example.multiapp.attachment.model.StorageProviderType;
import com.example.multiapp.common.jpa.CreatedOnlyEntity;
import com.example.multiapp.common.tenant.RequestContext;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter @Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ticket_attachment", schema = "app")
public class Attachment extends CreatedOnlyEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private AttachmentId id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false)
    private StorageProviderType storageProvider;

    @Setter
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "sha256", columnDefinition = "char(64)")
    private String sha256;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;


    public static Attachment createFrom(RequestContext ctx, UUID ticketId, MultipartFile file,
                                        StorageProviderType storageType) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(storageType, "storageType");
        Attachment a = new Attachment();
        a.id = new AttachmentId(ctx.tenantId(), UUID.randomUUID());
        a.ticketId = ticketId;
        a.filename = safeFileName(file.getOriginalFilename());
        a.contentType = Objects.requireNonNullElse(file.getContentType(), "application/octet-stream");
        a.sizeBytes = file.getSize();
        a.storageProvider = storageType;
        a.uploadedByUserId = ctx.userId();
        return a;
    }

    private static String safeFileName(String name) {
        if(name == null || name.isBlank()) return "file";
        // 只做展示名, 避免路径分隔符
        String safeName = name.replace("\\", "_").replace("/", "_");
        int max_length = 255;
        if(safeName.length() <= max_length) return safeName;
        return safeName.substring(0, max_length);
    }

    public void softDelete() {
        if(this.deletedAt != null) {
            throw new IllegalStateException("cannot delete existing attachment");
        }
        this.deletedAt = OffsetDateTime.now();
    }
}
