package com.example.multiapp.comment.entity;

import com.example.multiapp.comment.dto.CommentSummary;
import com.example.multiapp.comment.dto.CreateCommentRequest;
import com.example.multiapp.comment.model.CommentVisibility;
import com.example.multiapp.common.jpa.AuditedEntity;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.coyote.Request;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter @Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "ticket_comment", schema = "app")
public class Comment extends AuditedEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private CommentId id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visbility", nullable = false)
    private CommentVisibility visibility;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "edited_at")
    private OffsetDateTime editedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public static Comment createFrom(RequestContext ctx, UUID ticketId, CreateCommentRequest req) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(req, "req");
        Comment c = new Comment();
        c.id = new CommentId(ctx.tenantId(), UUID.randomUUID());
        c.ticketId = ticketId;
        c.authorUserId = ctx.userId();
        c.visibility = req.visibility();
        c.body = req.body();
        return c;
    }
}
