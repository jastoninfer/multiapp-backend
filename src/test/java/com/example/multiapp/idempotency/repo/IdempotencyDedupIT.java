package com.example.multiapp.idempotency.repo;

import com.example.multiapp.idempotency.entity.IdempotencyId;
import com.example.multiapp.idempotency.entity.IdempotencyRecord;
import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.repo.TenantRepository;
import com.example.multiapp.testinfra.PostgresContainerBase;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketType;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.repo.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@ActiveProfiles("test")
public class IdempotencyDedupIT extends PostgresContainerBase {

    @Autowired
    private TenantRepository tenantRepo;
    @Autowired private AppUserRepository appUserRepo;
    @Autowired private TenantMembershipRepository membershipRepo;
    @Autowired private IdempotencyRecordRepository idemRepo;

    UUID tenantId;
    UUID customerUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        Tenant tenant = Tenant.create("tenant");
        tenantRepo.save(tenant);
        tenantRepo.flush();
        tenantId = tenant.getId();

        AppUser customerUser = AppUser.create("issuer-customer", "sub-customer",
                "email-customer", "displayName-customer", null, false);
        appUserRepo.save(customerUser);
        appUserRepo.flush();
        customerUserId = customerUser.getId();
        TenantMembership customerMembership =  TenantMembership.create(tenantId, customerUserId,
                MembershipRole.CUSTOMER, false);
        membershipRepo.save(customerMembership);
        membershipRepo.flush();
    }

    @Test
    @Transactional
    void sameIdemKeyShouldBeUnique() {
        int v1 = idemRepo.tryInsert(tenantId, customerUserId, "idem-key-1", "request-hash-1");
        assertEquals(1, v1);
        idemRepo.flush();
        int v2 = idemRepo.tryInsert(tenantId, customerUserId, "idem-key-1", "request-hash-2");
        assertEquals(0, v2);
    }
}
