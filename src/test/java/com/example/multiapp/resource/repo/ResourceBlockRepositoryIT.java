package com.example.multiapp.resource.repo;

import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.resource.dto.CreateResourceBlockRequest;
import com.example.multiapp.resource.entity.ResourceBlock;
import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.repo.TenantRepository;
import com.example.multiapp.testinfra.PostgresContainerBase;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.repo.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@ActiveProfiles("test")
public class ResourceBlockRepositoryIT extends PostgresContainerBase {

    @Autowired
    ResourceBlockRepository blockRepo;

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    AppUserRepository appUserRepo;

    @Autowired
    TenantMembershipRepository tenantMembershipRepo;

    UUID tenantId;
    UUID resourceUserId;


    @BeforeEach
    @Transactional
    void setUp() {
        Tenant tenant = Tenant.create("tenant");
        tenantRepo.save(tenant);
        tenantRepo.flush();
        tenantId = tenant.getId();
        AppUser resourceUser = AppUser.create("issuer-resource", "sub-resource",
                "email-resource", "displayName-resource", null, false);
        appUserRepo.save(resourceUser);
        appUserRepo.flush();
        resourceUserId = resourceUser.getId();
        TenantMembership resourceMembership =  TenantMembership.create(tenantId, resourceUserId,
                MembershipRole.RESOURCE_USER, false);
        tenantMembershipRepo.save(resourceMembership);
        tenantMembershipRepo.flush();
    }

    @Test
    @Transactional
    void listInRange_shouldReturnOnlyOverlappingBlocks() {
        // block1: 10-11
        ResourceBlock block1 = ResourceBlock.from(
                tenantId, resourceUserId, new CreateResourceBlockRequest(
                        OffsetDateTime.parse("2026-04-10T10:00:00+08:00"),
                        OffsetDateTime.parse("2026-04-10T11:00:00+08:00"),
                        "reason"
                )
        );
        // block2: 12-13
        ResourceBlock block2 = ResourceBlock.from(
                tenantId, resourceUserId, new CreateResourceBlockRequest(
                        OffsetDateTime.parse("2026-04-10T12:00:00+08:00"),
                        OffsetDateTime.parse("2026-04-10T13:00:00+08:00"),
                        "reason"
                )
        );
        blockRepo.save(block1);
        blockRepo.save(block2);
        blockRepo.flush();

        // query: 10:30-11:30, 应该只命中block1
        OffsetDateTime from = OffsetDateTime.parse("2026-04-10T10:30:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-04-10T11:30:00+08:00");
        List<ResourceBlock> hits = blockRepo.listInRange(
                tenantId, resourceUserId, from, to
        );
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).getStartAt().isEqual(block1.getStartAt()));

        // query: 10:30-12:30, 应该命中block1+block2
        OffsetDateTime to2 = OffsetDateTime.parse("2026-04-10T12:30:00+08:00");
        List<ResourceBlock> hits2 = blockRepo.listInRange(tenantId,
                resourceUserId, from, to2);
        assertEquals(2, hits2.size());

        // query: 8:30-9:30, 应该命中0
        List<ResourceBlock> hits3 = blockRepo.listInRange(
                tenantId, resourceUserId,
                OffsetDateTime.parse("2026-04-10T08:30:00+08:00"),
                OffsetDateTime.parse("2026-04-10T09:30:00+08:00")
        );
        assertEquals(0, hits3.size());
    }
}
