package com.example.multiapp.ticket.entity;

import com.example.multiapp.common.jpa.AuditedEntity;
import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketStatus;
import com.example.multiapp.ticket.model.TicketType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Entity
@Table(name = "ticket", schema = "app")
public class Ticket extends AuditedEntity {
    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private TicketId id;

    // 数据库生成, 禁止hibernate为此值默认打null
    @ToString.Include
    @Column(name = "ticket_no", nullable = false, insertable = false, updatable = false)
    private Long ticketNo;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "requester_user_id", updatable = false)
    private UUID requesterUserId;

    @Column(name = "requester_contact_id", updatable = false)
    private UUID requesterContactId;

    @Column(name="owner_user_id")
    private UUID ownerUserId;

    @ToString.Include
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false)
    private TicketType ticketType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "location_text")
    private String locationText;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Setter
    @Column(name = "first_response_at")
    private OffsetDateTime firstResponseAt;

    public static Ticket create(UUID tenantId, UUID actorUserId, UUID requesterUserId,
                                UUID requesterContactId, TicketPriority priority,
                                TicketType ticketType, @NotBlank String title,
                                String description, String locationText) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(ticketType, "ticketType");
        requireNonBlank(title, "title");
        boolean userPresent = requesterUserId != null;
        boolean contactPresent = requesterContactId != null;
        if (userPresent == contactPresent) {
            throw new IllegalArgumentException("exactly one of requesterUserId or requesterContactId must be set");
        }

        Ticket t = new Ticket();
        t.id = new TicketId(tenantId, UUID.randomUUID());
        t.createdByUserId = actorUserId;
        t.requesterUserId = requesterUserId;
        t.requesterContactId = requesterContactId;
        t.status = TicketStatus.NEW;
        t.ownerUserId = null;
        t.priority = priority;
        t.ticketType = ticketType;
        t.title = normalizeOptional(title, true);
        t.description = normalizeOptional(description, false);
        t.locationText = normalizeOptional(locationText, true);
        return t;
    }

    public void setRequesterUser(UUID requesterUserId){
        this.requesterUserId = requesterUserId;
    }

    public void transitionTo(TicketStatus to, OffsetDateTime now) {
        TicketStatus target = Objects.requireNonNull(to, "status must not be null");
        if(!TicketStatus.isAllowedTransition(this.status, target)){
            throw new IllegalArgumentException("Illegal ticket status transition: "
                    + this.status + " -> " + target);
        }
        this.status = target;
        if (target == TicketStatus.CLOSED)
            this.closedAt = Objects.requireNonNull(now, "now must not be null");;
        if (target == TicketStatus.REOPENED) this.closedAt = null;
//        if(to == TicketStatus.IN_PROGRESS && this.firstResponseAt == null) {
//            this.firstResponseAt = OffsetDateTime.now();
//        }
    }

//    public void setFirstResponseAt(OffsetDateTime time) {
//        this.firstResponseAt = time;
//    }

    public void setNewOwner(UUID newAssignee) {
        this.ownerUserId = newAssignee;
//        if(this.firstResponseAt == null) this.firstResponseAt = OffsetDateTime.now();
    }


    public void changeTitle(String newTitle) {
        this.title = newTitle;
    }

    public void changeType(TicketType type) {
        this.ticketType = type;
        if(this.firstResponseAt == null) this.firstResponseAt = OffsetDateTime.now();
    }

    public void changePriority(TicketPriority newPriority){
        this.priority = Objects.requireNonNull(newPriority);
        if(this.firstResponseAt == null) this.firstResponseAt = OffsetDateTime.now();
    }

    public void updateDescription(String newDescription){
        this.description = newDescription;
    }

    public void updateLocationText(String newLocationText){
        this.locationText = newLocationText;
    }

    private static String normalizeOptional(String s, boolean removeWhiteSpaces) {
        String normalized = (s == null || s.isBlank()) ? null : s.strip();
        if(removeWhiteSpaces && normalized != null)
            normalized = normalized.replaceAll("\\s+", " ");
        return normalized;
    }

    private static void requireNonBlank(String s, String name) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
    }
}
