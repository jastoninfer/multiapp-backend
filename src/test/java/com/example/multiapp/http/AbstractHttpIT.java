package com.example.multiapp.http;

import com.example.multiapp.testinfra.PostgresContainerBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 通用helper: createTicket, ensureWorkingHours等
// 创建ticket + 准备 workingHours
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class AbstractHttpIT extends PostgresContainerBase {
    protected static final String TENANT_ID = "00000000-0000-0000-0000-000000030001";
    protected static final String AGENT_ID = "00000000-0000-0000-0000-200000000101";
    protected static final String CUSTOMER_USER_ID = "00000000-1000-0000-0000-000000000999";
    protected static final String OTHER_CUSTOMER_USER_ID = "00000000-1000-0000-0000-000000000777";
    protected static final String RESOURCE_ID = "00000600-0000-0000-0000-000000000201";

    @Autowired protected MockMvc mvc;
    protected ObjectMapper om = new ObjectMapper();
    @Autowired protected JdbcTemplate jdbc;
    protected UUID createdTicket() throws Exception {
        // 使用requesterUserId = CUSTOMER_USER_ID
        String json = """
            {
               "requesterUserId": "%s",
               "requesterContactId": null,
               "title": "IT ticket title",
               "description": "desc",
               "priority": "MEDIUM",
               "ticketType": "INCIDENT",
               "locationText": "Darwin"
             }
            """.formatted(CUSTOMER_USER_ID);
        var res = mvc.perform(post("/tickets")
                        .header("X-Tenant-Id", TENANT_ID)
                        // 用户自己创建工单?
                        .header("X-User-Id", CUSTOMER_USER_ID)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Role", "CUSTOMER")
                        .header("X-Request-Id", "it-ticket-1")
                        .contentType("application/json")
                        .content(json))
                .andReturn().getResponse();
        int status = res.getStatus();
        if(status < 200 || status >= 300) {
            throw new IllegalStateException("POST /tickets failed, status=" + status + ", body="
                    + res.getContentAsString());
        }
        String body = res.getContentAsString();
        if(!body.isBlank()) {
            JsonNode n = om.readTree(body);
            if(n.hasNonNull("id")) return UUID.fromString(n.get("id").asText());
        }
        throw new IllegalStateException("Cannot extract ticketId from response. body=" + body);
    }

    @BeforeEach
    protected void ensureTenantAndCustomerUserAndAgent() {
        // 1) tenant
        jdbc.update("""
        insert into app.tenant(id, name, status)
        values (?, 'test-tenant-1', 'ACTIVE')
        on conflict (id) do nothing
        """, UUID.fromString(TENANT_ID));
        // 2) agent
        jdbc.update("""
        insert into app.app_user(id, issuer, keycloak_sub, email, display_name, status)
        values (?, 'test', ?, ?, ?, 'ACTIVE')
        on conflict (id) do nothing
        """, UUID.fromString(AGENT_ID), "sub-agent-1", "agent-1@example.com", "Agent-1");
        jdbc.update("""
        insert into app.tenant_membership(tenant_id, user_id, role, is_default)
        values (?, ?, 'AGENT', false)
        on conflict (tenant_id, user_id) do nothing
        """, UUID.fromString(TENANT_ID), UUID.fromString(AGENT_ID));
        // 3) customer-user-1
        jdbc.update("""
        insert into app.app_user(id, issuer, keycloak_sub, email, display_name, status)
        values (?, 'test', ?, ?, ?, 'ACTIVE')
        on conflict (id) do nothing
        """, UUID.fromString(CUSTOMER_USER_ID), "sub-customer-1", "customer-1@example.com", "Customer-1");
        jdbc.update("""
        insert into app.tenant_membership(tenant_id, user_id, role, is_default)
        values (?, ?, 'CUSTOMER', false)
        on conflict (tenant_id, user_id) do nothing
        """, UUID.fromString(TENANT_ID), UUID.fromString(CUSTOMER_USER_ID));
        // 3) customer-user-2
        jdbc.update("""
        insert into app.app_user(id, issuer, keycloak_sub, email, display_name, status)
        values (?, 'test', ?, ?, ?, 'ACTIVE')
        on conflict (id) do nothing
        """, UUID.fromString(OTHER_CUSTOMER_USER_ID), "sub-customer-2", "customer-2@example.com", "Customer-2");
        jdbc.update("""
        insert into app.tenant_membership(tenant_id, user_id, role, is_default)
        values (?, ?, 'CUSTOMER', false)
        on conflict (tenant_id, user_id) do nothing
        """, UUID.fromString(TENANT_ID), UUID.fromString(OTHER_CUSTOMER_USER_ID));

    }

    @AfterEach
    protected void cleanup() {
        jdbc.update("""
        truncate table app.tenant
        restart identity
        cascade;
        """);
    }

    protected void ensureResourceUserMembershipAndWorkingHoursUtc() {
        // 1) app_user (resource)
        jdbc.update("""
        insert into app.app_user(id, issuer, keycloak_sub, email, display_name, status)
        values (?, 'test', ?, ?, ?, 'ACTIVE')
        on conflict (id) do nothing
        """, UUID.fromString(RESOURCE_ID), "sub-resource-1", "resource-1@example.com", "Resource-1");
        // 2) membership: RESOURCE_USER
        jdbc.update("""
        insert into app.tenant_membership(tenant_id, user_id, role, is_default)
        values (?, ?, 'RESOURCE_USER', false)
        on conflict (tenant_id, user_id) do nothing
        """, UUID.fromString(TENANT_ID), UUID.fromString(RESOURCE_ID));
        // 3) working hours: UTC 09:00-17:00
        jdbc.update("""
        delete from app.resource_working_hours
        where tenant_id = ? and resource_user_id = ?
        """, UUID.fromString(TENANT_ID), UUID.fromString(RESOURCE_ID));
        for (int dow = 1; dow <= 7; dow++) {
            jdbc.update("""
            insert into app.resource_working_hours(tenant_id, resource_user_id, day_of_week, start_local, end_local, timezone)
            values (?, ?, ?, '09:00', '17:00', 'UTC')
            """,  UUID.fromString(TENANT_ID), UUID.fromString(RESOURCE_ID), dow);
        }
    }

}
