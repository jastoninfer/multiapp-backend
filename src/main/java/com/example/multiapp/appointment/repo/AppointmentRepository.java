package com.example.multiapp.appointment.repo;

import com.example.multiapp.appointment.dto.AppointmentDetailResponse;
import com.example.multiapp.appointment.dto.AppointmentSearchQuery;
import com.example.multiapp.appointment.dto.AppointmentSummary;
import com.example.multiapp.appointment.entity.Appointment;
import com.example.multiapp.appointment.entity.AppointmentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends
        JpaRepository<Appointment, AppointmentId>{

    @Query(value = """
    select new com.example.multiapp.appointment.dto.AppointmentSummary(
        a.id.id, a.version, t.id.id, t.title, a.startAt, a.endAt, a.status, a.resourceUserId,
        u.displayName, a.addressText
    ) from Appointment a left join AppUser u on u.id = a.resourceUserId
        left join Ticket t on (t.id.tenantId = a.id.tenantId and t.id.id = a.ticketId)
    where a.id.tenantId = :tenantId
        and a.resourceUserId = :resourceUserId
        and (:#{#q.resourceUserId == null} = true or a.resourceUserId = :#{#q.resourceUserId})
        and (:#{#q.ticketId == null} = true or a.ticketId = :#{#q.ticketId})
        and (:#{#q.status == null} = true or cast(a.status as string) = :#{#q.status})
        and (:#{#q.from == null} = true or a.endAt >= :#{#q.from})
        and (:#{#q.to == null} = true or a.startAt <= :#{#q.to})
    """, countQuery = """
    select count(a) from Appointment a where a.id.tenantId = :tenantId
        and a.resourceUserId = :resourceUserId
        and (:#{#q.resourceUserId == null} = true or a.resourceUserId = :#{#q.resourceUserId})
        and (:#{#q.ticketId == null} = true or a.ticketId = :#{#q.ticketId})
        and (:#{#q.status == null} = true or cast(a.status as string) = :#{#q.status})
        and (:#{#q.from == null} = true or a.endAt >= :#{#q.from})
        and (:#{#q.to == null} = true or a.startAt <= :#{#q.to})
    """)
    Page<AppointmentSummary> searchAsResourceUser(
            @Param("tenantId") UUID tenantId,
            @Param("resourceUserId") UUID resourceUserId,
            @Param("q") AppointmentSearchQuery q,
            Pageable pageable);

    @Query(value = """
    select new com.example.multiapp.appointment.dto.AppointmentSummary(
        a.id.id, a.version, t.id.id, t.title, a.startAt, a.endAt, a.status, a.resourceUserId,
        u.displayName, a.addressText
    ) from Appointment a left join AppUser u on u.id = a.resourceUserId
        left join Ticket t on (t.id.id = a.ticketId and t.id.tenantId = :tenantId)
    where a.id.tenantId = :tenantId
        and (:#{#q.ticketOwnerId == null} = true or t.ownerUserId = :#{#q.ticketOwnerId})
        and (:#{#q.resourceUserId == null} = true or a.resourceUserId = :#{#q.resourceUserId})
        and (:#{#q.ticketId == null} = true or a.ticketId = :#{#q.ticketId})
        and (:#{#q.status == null} = true or cast(a.status as string) = :#{#q.status})
        and (:#{#q.from == null} = true or a.endAt >= :#{#q.from})
        and (:#{#q.to == null} = true or a.startAt <= :#{#q.to})
    """, countQuery = """
    select count(a) from Appointment a
        left join Ticket t on (t.id.id = a.ticketId and t.id.tenantId = a.id.tenantId)
            where a.id.tenantId = :tenantId
        and (:#{#q.ticketOwnerId == null} = true or t.ownerUserId = :#{#q.ticketOwnerId})
        and (:#{#q.resourceUserId == null} = true or a.resourceUserId = :#{#q.resourceUserId})
        and (:#{#q.ticketId == null} = true or a.ticketId = :#{#q.ticketId})
        and (:#{#q.status == null} = true or cast(a.status as string) = :#{#q.status})
        and (:#{#q.from == null} = true or a.endAt >= :#{#q.from})
        and (:#{#q.to == null} = true or a.startAt <= :#{#q.to})
    """)
    Page<AppointmentSummary> search(
            @Param("tenantId") UUID tenantId,
            @Param("q") AppointmentSearchQuery q,
            Pageable pageable);

    @Query("""
    select new com.example.multiapp.appointment.dto.AppointmentDetailResponse(
        a.id.id,
        a.version,
        t.id.id,
        t.title,
        a.startAt,
        a.endAt,
        a.status,
        a.resourceUserId,
        u.displayName,
        a.addressText,
        a.notes,
        a.arrivedAt,
        a.completedAt,
        a.createdAt,
        a.updatedAt
    ) from Appointment a left join AppUser u on u.id = a.resourceUserId
        left join Ticket t on (t.id.tenantId = a.id.tenantId and t.id.id = a.ticketId)
    where a.id.tenantId = :tenantId and a.id.id = :appointmentId
    """)
    Optional<AppointmentDetailResponse> findDetailByTenantIdAndIdId(
            @Param("tenantId") UUID tenantId,
            @Param("appointmentId") UUID appointmentId);

    Optional<Appointment> findByIdTenantIdAndIdId(UUID tenantId, UUID appointmentId);
    @Query("""
    select new com.example.multiapp.appointment.dto.AppointmentDetailResponse(
        a.id.id,
        a.version,
        t.id.id,
        t.title,
        a.startAt,
        a.endAt,
        a.status,
        a.resourceUserId,
        u.displayName,
        a.addressText,
        a.notes,
        a.arrivedAt,
        a.completedAt,
        a.createdAt,
        a.updatedAt
    ) from Appointment a left join AppUser u on u.id = a.resourceUserId
        left join Ticket t on (t.id.tenantId = a.id.tenantId and t.id.id = a.ticketId)
    where a.id.tenantId = :tenantId and a.id.id = :appointmentId
        and t.ownerUserId = :ticketOwnerId
    """)
    Optional<AppointmentDetailResponse> findDetailByTenantIdAndIdIdAndTicketOwnerId(
            @Param("tenantId") UUID tenantId,
            @Param("appointmentId") UUID appointmentId,
            @Param("ticketOwnerId") UUID ticketOwnerId);
    @Query("""
    select new com.example.multiapp.appointment.dto.AppointmentDetailResponse(
        a.id.id,
        a.version,
        t.id.id,
        t.title,
        a.startAt,
        a.endAt,
        a.status,
        a.resourceUserId,
        u.displayName,
        a.addressText,
        a.notes,
        a.arrivedAt,
        a.completedAt,
        a.createdAt,
        a.updatedAt
    ) from Appointment a left join AppUser u on u.id = a.resourceUserId
        left join Ticket t on (t.id.tenantId = a.id.tenantId and t.id.id = a.ticketId)
    where a.id.tenantId = :tenantId and a.id.id = :appointmentId
        and a.resourceUserId = :resourceUserId
    """)
    Optional<AppointmentDetailResponse> findDetailByTenantIdAndIdIdAndResourceUserId(
            @Param("tenantId") UUID tenantId,
            @Param("appointmentId") UUID appointmentId,
            @Param("resourceUserId") UUID resourceUserId);
    Optional<Appointment> findByIdTenantIdAndIdIdAndResourceUserId(
            UUID tenantId, UUID appointmentId, UUID resourceUserId);
    boolean existsByIdTenantIdAndTicketIdAndResourceUserId(UUID tenantId, UUID ticketId, UUID resourceUserId);

    @Query("""
        select new com.example.multiapp.appointment.dto.AppointmentSummary(
            a.id.id, a.version, t.id.id, t.title, a.startAt, a.endAt, a.status, a.resourceUserId,
                u.displayName, a.addressText
        ) from Appointment a left join AppUser u on u.id = a.resourceUserId
            left join Ticket t on (t.id.tenantId = a.id.tenantId and t.id.id = a.ticketId)
            where a.id.tenantId = :tenantId and a.ticketId = :ticketId
    """)
    List<AppointmentSummary> listSummariesByTicket(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId") UUID ticketId,
            Pageable pageable);

    @Query("""
    select new com.example.multiapp.appointment.dto.AppointmentSummary(
        a.id.id, a.version, t.id.id, t.title, a.startAt, a.endAt, a.status, a.resourceUserId,
                u.displayName, a.addressText
    ) from Appointment a left join AppUser u on u.id = a.resourceUserId
        left join Ticket t on (t.id.tenantId = a.id.tenantId and t.id.id = a.ticketId)
        where a.id.tenantId = :tenantId and a.ticketId = :ticketId
            and a.startAt >= CURRENT_TIMESTAMP
                order by a.startAt asc
    """)
    List<AppointmentSummary> listUpcomingByTicket(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId") UUID ticketId,
            Pageable pageable
    );

    @Query("""
    select new com.example.multiapp.appointment.dto.AppointmentSummary(
        a.id.id, a.version, t.id.id, t.title, a.startAt, a.endAt, a.status, a.resourceUserId,
                u.displayName, a.addressText
    ) from Appointment a left join AppUser u on u.id = a.resourceUserId
        left join Ticket t on (t.id.tenantId = a.id.tenantId and t.id.id = a.ticketId)
        where a.id.tenantId = :tenantId and a.ticketId = :ticketId
            and a.startAt < CURRENT_TIMESTAMP
                order by a.startAt desc
    """)
    List<AppointmentSummary> listRecentPastByTicket(
            @Param("tenantId") UUID tenantId,
            @Param("ticketId") UUID ticketId,
            Pageable pageable
    );

    long countByIdTenantIdAndTicketId(UUID tenantId, UUID ticketId);

    @Query("""
    select count(a) from Appointment a left join AppUser u on u.id = a.resourceUserId
        where a.id.tenantId = :tenantId and a.ticketId = :ticketId
            and a.startAt >= CURRENT_TIMESTAMP
    """)
    long countUpcomingByIdTenantIdAndTicketId(UUID tenantId, UUID ticketId);

    @Query("""
    select count(a) from Appointment a left join AppUser u on u.id = a.resourceUserId
        where a.id.tenantId = :tenantId and a.ticketId = :ticketId
            and a.startAt < CURRENT_TIMESTAMP
    """)
    long countPastByIdTenantIdAndTicketId(UUID tenantId, UUID ticketId);

}
