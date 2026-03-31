package com.example.multiapp.ticket.repo;

import com.example.multiapp.ticket.dto.TicketQuery;
import com.example.multiapp.ticket.dto.TicketResponse;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.entity.TicketId;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, TicketId> {
//    Page<Ticket> findByIdTenantId(UUID tenantId, Pageable pageable);
    String SELECT_TICKET_RESPONSE = """
    select new com.example.ticket.api.dto.TicketResponse(
        t.id.id,
        t.ticketNo,
        t.version,
        t.status,
        t.priority,
        t.ticketType,
        t.ownerUserId,
        owner.displayName,
        t.requesterUserId,
        t.requesterContactId,
        coalesce(reqUser.displayName, reqContact.displayName),
        t.title,
        t.createdAt,
        t.updatedAt,
        (select count(cm) from Comment cm
            where cm.tenantId = :tenantId and cm.ticketId = t.id.id),
        (select count(att) from Attachment att
            where att.tenantId = :tenantId and att.ticketId = t.id.id),
        (select min(a.startAt) from Appointment a
            where a.id.tenantId = :tenantId
                and a.ticketId = t.id.id
                and a.startAt >= CURRENT_TIMESTAMP
        )
    )
    from Ticket t
    left join AppUser owner on owner.id = t.ownerUserId
    left join AppUser reqUser on reqUser.id = t.requesterUserId
    left join Contact reqContact on reqContact.id.tenantId =: tenantId and reqContact.id.id = t.requesterContactId
    """;

    String OPTIONAL_FILTERS = """
    and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
    and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
    and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
    and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
    and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
    and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
    and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
    and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """;

    // Admin: tenant内所有ticket
    @Query(value = SELECT_TICKET_RESPONSE  + """
        where t.id.tenantId = :tenantId
        """ + OPTIONAL_FILTERS, countQuery = """
        select count(t) from Ticket t where t.id.tenantId =: tenantId
    """ + OPTIONAL_FILTERS)
    Page<TicketResponse> findForAdminResponse(
            @Param("tenantId") UUID tenantId,
            @Param("q") TicketQuery q,
            Pageable pageable
    );

    // Customer: requester_user_id = userId OR requester_contact_id 链接到linked_user_id=userId
    @Query(value = SELECT_TICKET_RESPONSE + """
        where t.id.tenantId = :tenantId
            and (
                t.requesterUserId = :userId
            or exists (
                select 1 from Contact c
                    where c.id.tenantId = :tenantId
                        and c.id.id = t.requesterContactId
                        and c.linkedUserId = :userId
                )
            )
    """ + OPTIONAL_FILTERS, countQuery = """
    select count(t) from Ticket t
        where t.id.tenantId = :tenantId
            and (
                t.requesterUserId = :userId
            or exists (
                select 1 from Contact c
                    where c.id.tenantId = :tenantId
                        and c.id.id = t.requesterContactId
                        and c.linkedUserId = :userId
                )
            )
    """ + OPTIONAL_FILTERS)
    Page<TicketResponse> findForCustomerResponse(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("q") TicketQuery q,
            Pageable pageable
    );

    // ResourceUser: Customer可见范围+appointment(resourceUserId=userId)关联到该ticket
    @Query(value = SELECT_TICKET_RESPONSE + """
        where t.id.tenantId = :tenantId
            and (
                t.requesterUserId = :userId
            or exists (
                select 1 from Contact c
                    where c.id.tenantId = :tenantId
                        and c.id.id = t.requesterContactId
                        and c.linkedUserId = :userId
                )
            or exists (
                select 1 from Appointment a
                    where a.id.tenantId = :tenantId
                        and a.resourceUserId = :userId
                        and a.ticketId = t.id.id
                )
            )
    """ + OPTIONAL_FILTERS, countQuery = """
    select count(t)
        from Ticket t
        where t.id.tenantId = :tenantId
            and (
                t.requesterUserId = :userId
                    or exists (
                        select 1 from Contact c
                            where c.id.tenantId = :tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                        )
                    or exists (
                        select 1 from Appointment a
                            where a.id.tenantId = :tenantId
                                and a.resourceUserId = :userId
                                and a.ticketId = t.id.id
                        )
                )
    """ + OPTIONAL_FILTERS)
    Page<TicketResponse> findForResourceUserResponse(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("q") TicketQuery q,
            Pageable pageable
    );

    // Agent: Customer可见范围+owner_user_id=userId
    @Query(value = SELECT_TICKET_RESPONSE + """
        where t.id.tenantId = :tenantId
            and (
                t.ownerUserId = :userId
                    or t.requesterUserId = :userId
                    or exists (
                        select 1 from Contact c
                            where c.id.tenantId = :tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                        )
                )
    """ + OPTIONAL_FILTERS, countQuery = """
    select count(t)
        from Ticket t
            where t.id.tenantId = :tenantId
                and (
                    t.ownerUserId = :userId
                        or t.requesterUserId = :userId
                        or exists (
                            select 1 from Contact c
                                where c.id.tenantId = :tenantId
                                    and c.id.id = t.requesterContactId
                                    and c.linkedUserId = :userId
                            )
                    )
    """ + OPTIONAL_FILTERS)
    Page<TicketResponse> findForAgentResponse(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("q") TicketQuery q,
      Pageable pageable
    );

    @Query(value = SELECT_TICKET_RESPONSE + """
        where t.id.tenantId = :tenantId
            and t.id.id = :id
    """)
    Optional<TicketResponse> findResponseByTenantIdAndId(
            @Param("tenantId") UUID tenantId,
            @Param("id") UUID id
    );

    Optional<Ticket> findByIdTenantIdAndIdId(UUID tenantId, UUID id);

    @Query(value = """
    select t
        from Ticket t
        where t.id.tenantId =:tenantId
            and (
                t.requesterUserId =: userId
                    or exists (
                        select 1
                            from Contact c
                            where c.id.tenantId =: tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                    )
            )
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """, countQuery = """
    select count(t)
        from Ticket t
        where t.id.tenantId =:tenantId
            and (
                t.requesterUserId =: userId
                    or exists (
                        select 1
                            from Contact c
                            where c.id.tenantId = :tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                    )
            )
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """)
    Page<Ticket> findForCustomer(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                                 @Param("q")TicketQuery q, Pageable pageable);


    @Query(value = """
    select t
        from Ticket t
        where t.id.tenantId = :tenantId
            and (
                t.requesterUserId = :userId
                    or exists (
                        select 1
                            from Contact c
                            where c.id.tenantId = :tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                        )
                    or exists (
                        select 1
                            from Appointment a
                            where a.id.tenantId = :tenantId
                                and a.resourceUserId = :userId
                                and a.ticketId = t.id.id
                    )
            )
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """, countQuery = """
    select count(t)
        from Ticket t
        where t.id.tenantId = :tenantId
            and (
                t.requesterUserId = :userId
                    or exists (
                        select 1
                            from Contact c
                            where c.id.tenantId = :tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                        )
                    or exists (
                        select 1
                            from Appointment a
                            where a.id.tenantId = :tenantId
                                and a.resourceUserId = :userId
                                and a.ticketId = t.id.id
                    )
            )
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """)
    Page<Ticket> findForResourceUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                                     @Param("q") TicketQuery q, Pageable pageable);

    @Query(value = """
    select t
        from Ticket t
        where t.id.tenantId =:tenantId
            and (
                t.requesterUserId =: userId
                    or exists (
                        select 1
                            from Contact c
                            where c.id.tenantId =: tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                    )
                    or t.ownerUserId = :userId
            )
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """, countQuery = """
    select count(t)
        from Ticket t
        where t.id.tenantId =:tenantId
            and (
                t.requesterUserId =: userId
                    or exists (
                        select 1
                            from Contact c
                            where c.id.tenantId =: tenantId
                                and c.id.id = t.requesterContactId
                                and c.linkedUserId = :userId
                    )
                    or t.ownerUserId = :userId
            )
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """)
    Page<Ticket> findForAgent(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                                     @Param("q") TicketQuery q, Pageable pageable);

    @Query(value = """
    select t
        from Ticket t
        where t.id.tenantId =:tenantId
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """, countQuery = """
    select count(t)
        from Ticket t
        where t.id.tenantId =:tenantId
            and (:#{#q.ticketStatus} is null or t.status = :#{#q.ticketStatus})
            and (:#{#q.ticketPriority} is null or t.priority = :#{#q.ticketPriority})
            and (:#{#q.ownerId} is null or t.ownerUserId = :#{#q.ownerId})
            and (:#{#q.ticketType} is null or t.ticketType = :#{#q.ticketType})
            and (:#{#q.createdFrom} is null or t.createdAt >= :#{#q.createdFrom})
            and (:#{#q.createdTo} is null or t.createdAt <= :#{#q.createdTo})
            and (:#{#q.requesterUserId} is null or t.requesterUserId = :#{#q.requesterUserId})
            and (:#{#q.requesterContactId} is null or t.requesterContactId = :#{#q.requesterContactId})
    """)
    Page<Ticket> findForAdmin(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                              @Param("q") TicketQuery q, Pageable pageable);
}
