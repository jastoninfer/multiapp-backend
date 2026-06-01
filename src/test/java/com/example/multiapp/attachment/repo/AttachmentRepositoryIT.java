package com.example.multiapp.attachment.repo;

import com.example.multiapp.attachment.dto.AttachmentSummary;
import com.example.multiapp.attachment.entity.Attachment;
import com.example.multiapp.attachment.model.StorageProviderType;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.repo.TenantRepository;
import com.example.multiapp.testinfra.PostgresContainerBase;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketType;
import com.example.multiapp.ticket.repo.TicketRepository;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.repo.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@ActiveProfiles("test")
public class AttachmentRepositoryIT extends PostgresContainerBase {
    @Autowired
    private AttachmentRepository attachmentRepo;
    @Autowired
    private TenantRepository tenantRepo;
    @Autowired
    private AppUserRepository appUserRepo;
    @Autowired
    private TenantMembershipRepository membershipRepo;
    @Autowired
    private TicketRepository ticketRepo;

    UUID tenantId;
    UUID customerUserId;
    UUID ticketId;

    @BeforeEach
    @Transactional
    // 插入一个ticket
    void setUp() {
        Tenant tenant = Tenant.create("tenant");
        tenantRepo.save(tenant);
        tenantRepo.flush();
        tenantId = tenant.getId();

        AppUser customerUser = AppUser.create("issuer-customer", "sub-customer",
                "email-customer", "displayName-customer", null, false);
        appUserRepo.save(customerUser);

//        AppUser agentUser = AppUser.create("issuer-agent", "sub-agent",
//                "email-agent", "displayName-agent", null, false);
//        appUserRepo.save(agentUser);
        appUserRepo.flush();

        customerUserId = customerUser.getId();
//        TenantMembership agentMembership =  TenantMembership.create(tenantId, agentUserId,
//                MembershipRole.AGENT, false);
        TenantMembership customerMembership =  TenantMembership.create(tenantId, customerUserId,
                MembershipRole.CUSTOMER, false);
//        membershipRepo.save(agentMembership);
        membershipRepo.save(customerMembership);
        membershipRepo.flush();
        Ticket a = Ticket.create(tenantId, customerUserId, customerUserId, null,
                TicketPriority.MEDIUM, TicketType.INCIDENT, "ticket-A", null, null);
        ticketRepo.save(a);
        ticketRepo.flush();
        ticketId = a.getId().getId();
    }

    @Test
    @Transactional
    void listSummariesByTicket_shouldExcludeSoftDeleted() {
        // 插入两个attachment, 其中一个deleted_at非空
        MultipartFile file1 = new MockMultipartFile("file", "file1.txt", "text/plain",
                "hello file1".getBytes(StandardCharsets.UTF_8));
        MultipartFile file2 = new MockMultipartFile("file", "file2.txt", "text/plain",
                "hello file2".getBytes(StandardCharsets.UTF_8));
        RequestContext ctx = new RequestContext(tenantId, customerUserId, false,
                MembershipRole.CUSTOMER, "issuer", "sub", "request-id");
        Attachment a1 = Attachment.createFrom(ctx, ticketId, file1, StorageProviderType.LOCAL);
        Attachment a2 = Attachment.createFrom(ctx, ticketId, file2, StorageProviderType.LOCAL);
        a1.setStorageKey("key1");
        a2.setStorageKey("key2");
        a2.softDelete();
        attachmentRepo.save(a1);
        attachmentRepo.save(a2);
        attachmentRepo.flush();
        List<AttachmentSummary> items = attachmentRepo.listSummariesByTicket(tenantId, ticketId,
                PageRequest.of(0, 20));
//        System.out.println(items);
        assertEquals(1, items.size());
        assertEquals(a1.getId().getId(), items.get(0).id());
    }
}
