package com.example.multiapp.contact.repo;

import com.example.multiapp.contact.dto.ContactQuery;
import com.example.multiapp.contact.dto.ContactResponse;
import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contact.entity.ContactId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, ContactId> {

    @Query(value = """
      select new com.example.multiapp.contact.dto.ContactResponse(
          c.id.tenantId, c.id.id, cast(c.contactType as string),
          c.email, c.phone, c.displayName, c.linkedUserId,
          au.displayName, c.createdByUserId,
          (
            select max(cc.expiresAt)
            from ContactClaim cc
            where cc.id.tenantId = c.id.tenantId
                and cc.contactId = c.id.id
                and cc.consumedAt is null
                and cc.expiresAt > CURRENT_TIMESTAMP
          ),
          c.version
          )  from Contact c left join AppUser au on (c.linkedUserId = au.id)
       where c.id.tenantId = :tenantId
         and (:#{#q.displayName == null} = true or lower(c.displayName)
             like lower(concat('%', :#{#q.displayName}, '%'))    escape '\\')
         and (:#{#q.email == null} = true or lower(c.emailNormalized)
             like lower(concat('%', :#{#q.email}, '%'))    escape '\\')
         and (:#{#q.phone == null} = true or c.phoneNormalized
             like concat('%', :#{#q.phone}, '%')   escape '\\')
         and (:#{#q.linked == null} = true
             or (:#{#q.linked} = true and c.linkedUserId is not null)
             or (:#{#q.linked} = false and c.linkedUserId is null))
    """, countQuery = """
      select count(c) from Contact c
       where c.id.tenantId = :tenantId
         and (:#{#q.displayName == null} = true or lower(c.displayName)
             like lower(concat('%', :#{#q.displayName}, '%'))    escape '\\')
         and (:#{#q.email == null} = true or lower(c.emailNormalized)
             like lower(concat('%', :#{#q.email}, '%'))    escape '\\')
         and (:#{#q.phone == null} = true or c.phoneNormalized
             like concat('%', :#{#q.phone}, '%')   escape '\\')
         and (:#{#q.linked == null} = true
             or (:#{#q.linked} = true and c.linkedUserId is not null)
             or (:#{#q.linked} = false and c.linkedUserId is null))
    """)
    Page<ContactResponse> searchContacts(
            @Param("tenantId") UUID tenantId,
            @Param("q")ContactQuery q,
            Pageable pageable);

    @Query(value = """
    select * from app.contact
        where tenant_id = :tenantId
            and id = :id
        limit 1
        for update
    """, nativeQuery = true)
    Optional<Contact> findByIdTenantIdAndIdIdForUpdate(UUID tenantId, UUID id);
    boolean existsByIdTenantIdAndIdId(UUID tenantId, UUID id);
    @Query(value = """
         select new com.example.multiapp.contact.dto.ContactResponse(
          c.id.tenantId, c.id.id, cast(c.contactType as string),
              c.email, c.phone, c.displayName, c.linkedUserId,
                  au.displayName,
                  c.createdByUserId,
              (
            select max(cc.expiresAt)
            from ContactClaim cc
            where cc.id.tenantId = c.id.tenantId
                and cc.contactId = c.id.id
                and cc.consumedAt is null
                and cc.expiresAt > CURRENT_TIMESTAMP
          ),
          c.version
          )  from Contact c left join AppUser au on (c.linkedUserId = au.id)
       where c.id.tenantId = :tenantId and c.id.id = :id
    """)
    Optional<ContactResponse> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Contact> findByIdTenantIdAndIdId(UUID tenantId, UUID id);
    boolean existsByIdTenantIdAndIdIdAndLinkedUserId(UUID tenantId, UUID id, UUID linkedUserId);
}
