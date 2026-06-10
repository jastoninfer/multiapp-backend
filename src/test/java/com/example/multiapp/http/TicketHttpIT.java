package com.example.multiapp.http;

import com.example.multiapp.testinfra.TestRequestContextWebConfig;
import com.example.multiapp.testinfra.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@Import({TestSecurityConfig.class, TestRequestContextWebConfig.class})
public class TicketHttpIT extends AbstractHttpIT {

    @Test
    void postTickets_sameIdempotencyKeyDifferentBody_returns409() throws Exception {
        String idemKey = UUID.randomUUID().toString();
        String firstBody = ticketBody("IT ticket title");
        String changedBody = ticketBody("Different IT ticket title");

        mvc.perform(post("/tickets")
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", CUSTOMER_USER_ID)
                        .header("X-Role", "CUSTOMER")
                        .header("X-Request-Id", "it-ticket-idem-1")
                        .header("Idempotency-Key", idemKey)
                        .contentType("application/json")
                        .content(firstBody))
                .andExpect(status().isCreated());

        mvc.perform(post("/tickets")
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", CUSTOMER_USER_ID)
                        .header("X-Role", "CUSTOMER")
                        .header("X-Request-Id", "it-ticket-idem-2")
                        .header("Idempotency-Key", idemKey)
                        .contentType("application/json")
                        .content(changedBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private String ticketBody(String title) {
        return """
            {
              "requesterUserId": "%s",
              "requesterContactId": null,
              "title": "%s",
              "description": "desc",
              "ticketType": "INCIDENT",
              "locationText": "Darwin"
            }
            """.formatted(CUSTOMER_USER_ID, title);
    }
}
