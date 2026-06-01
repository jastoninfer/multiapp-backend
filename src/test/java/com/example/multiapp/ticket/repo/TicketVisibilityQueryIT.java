package com.example.multiapp.ticket.repo;

import com.example.multiapp.contact.dto.CreateContactRequest;
import com.example.multiapp.contact.entity.Contact;
import com.example.multiapp.contact.model.ContactType;
import com.example.multiapp.contact.repo.ContactRepository;
import com.example.multiapp.membership.entity.TenantMembership;
import com.example.multiapp.membership.model.MembershipRole;
import com.example.multiapp.membership.repo.TenantMembershipRepository;
import com.example.multiapp.tenant.entity.Tenant;
import com.example.multiapp.tenant.repo.TenantRepository;
import com.example.multiapp.testinfra.PostgresContainerBase;
import com.example.multiapp.ticket.dto.TicketQuery;
import com.example.multiapp.ticket.dto.TicketSearchQuery;
import com.example.multiapp.ticket.entity.Ticket;
import com.example.multiapp.ticket.model.TicketPriority;
import com.example.multiapp.ticket.model.TicketType;
import com.example.multiapp.user.entity.AppUser;
import com.example.multiapp.user.repo.AppUserRepository;
import org.hibernate.validator.internal.constraintvalidators.bv.AssertTrueValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@ActiveProfiles("test")
public class TicketVisibilityQueryIT extends PostgresContainerBase {

    @Autowired
    TenantRepository tenantRepo;

    @Autowired
    AppUserRepository appUserRepo;

    @Autowired
    TenantMembershipRepository membershipRepo;

    @Autowired
    ContactRepository contactRepo;

    @Autowired
    private TicketRepository ticketRepo;

    UUID tenantId;
    UUID customerUserId;
    UUID anotherCustomerUserId;
    UUID contactUserWithLinkId;
    UUID contactUserWithoutLinkId;
    UUID agentUserId;

    @BeforeEach
    @Transactional
    void setUp() {
        Tenant tenant = Tenant.create("tenant");
        tenantRepo.save(tenant);
        tenantRepo.flush();
        tenantId = tenant.getId();

        AppUser customerUser = AppUser.create("issuer-customer", "sub-customer",
                "email-customer", "displayName-customer", null, false);
        AppUser anotherCustomerUser = AppUser.create("issuer-customer", "sub-customer-another",
                "email-customer-another", "displayName-customer-another", null, false);
        appUserRepo.save(customerUser);
        appUserRepo.save(anotherCustomerUser);

        AppUser agentUser = AppUser.create("issuer-agent", "sub-agent",
                "email-agent", "displayName-agent", null, false);
        appUserRepo.save(agentUser);
        appUserRepo.flush();
        customerUserId = customerUser.getId();
        anotherCustomerUserId = anotherCustomerUser.getId();
        agentUserId = agentUser.getId();

        Contact contactUserWithLink = Contact.from(tenantId, agentUserId,
                new CreateContactRequest(ContactType.PERSON,
                        "email-linked", null, "displayName-contact-Linked"));
        Contact contactUserWithoutLink = Contact.from(tenantId, agentUserId,
                new CreateContactRequest(ContactType.PERSON,
                        "email-notLinked", null, "displayName-contact-NotLinked"));
        contactRepo.save(contactUserWithLink);
        contactRepo.save(contactUserWithoutLink);
        contactRepo.flush();
        contactUserWithLinkId = contactUserWithLink.getId().getId();
        contactUserWithoutLinkId = contactUserWithoutLink.getId().getId();


        customerUserId = customerUser.getId();
        TenantMembership agentMembership =  TenantMembership.create(tenantId, agentUserId,
                MembershipRole.AGENT, false);
        TenantMembership customerMembership =  TenantMembership.create(tenantId, customerUserId,
                MembershipRole.CUSTOMER, false);
        TenantMembership customerMembership2 =  TenantMembership.create(tenantId, anotherCustomerUserId,
                MembershipRole.CUSTOMER, false);
        membershipRepo.save(agentMembership);
        membershipRepo.save(customerMembership);
        membershipRepo.save(customerMembership2);
        membershipRepo.flush();
        contactRepo.findByIdTenantIdAndIdId(tenantId, contactUserWithLinkId).
                ifPresent(c -> c.setLinkedUserId(customerUserId));
        contactRepo.flush();
    }

    @Test
    @Transactional
    void customerShouldSeeTicketsByRequesterUserOrLinkedContact() {
        // ticket A: requester_user_id = customerUserId
        Ticket a = Ticket.create(tenantId, customerUserId, customerUserId, null,
                TicketPriority.MEDIUM, TicketType.INCIDENT, "ticket-A", null, null);
        // ticket B: requester_contacter_id = c1, and c1.linked_user_id = customerUserId
        Ticket b = Ticket.create(tenantId, agentUserId, null, contactUserWithLinkId,
                TicketPriority.HIGH, TicketType.INCIDENT, "ticket-B", null, null);
        // ticket C: unrelated
        Ticket c = Ticket.create(tenantId, agentUserId, anotherCustomerUserId, null,
                TicketPriority.MEDIUM, TicketType.INCIDENT, "ticket-C", null, null);
        ticketRepo.save(a);
        ticketRepo.save(b);
        ticketRepo.save(c);
        ticketRepo.flush();
        Page<Ticket> page = ticketRepo.findForCustomer(tenantId, customerUserId, TicketSearchQuery.empty(),
                PageRequest.of(0, 20));
        List<UUID> ids = page.getContent().stream().map(t -> t.getId().getId()).toList();
        System.out.println(ids);
        assertTrue(ids.contains(a.getId().getId()));
        assertTrue(ids.contains(b.getId().getId()));
        assertFalse(ids.contains(c.getId().getId()));
    }
}
