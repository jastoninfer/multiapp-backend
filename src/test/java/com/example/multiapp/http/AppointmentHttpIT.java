package com.example.multiapp.http;


import com.example.multiapp.testinfra.TestRequestContextWebConfig;
import com.example.multiapp.testinfra.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@ActiveProfiles("test")
@Import({TestSecurityConfig.class, TestRequestContextWebConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
// appointments: 201/409/Etag/If-Match 409
public class AppointmentHttpIT extends AbstractHttpIT{
//    @MockitoBean
//    JwtDecoder jwtDecoderMock;
    @Test
    void postAppointments_success201_getHasEtag_patchIfMatchMismatch409() throws Exception {
        ensureResourceUserMembershipAndWorkingHoursUtc();
        UUID ticketId = createdTicket();
        // 1) 创建预约 (在working hours内)
        String body =
          """
            {
              "resourceUserId": "%s",
              "customerUserId": "%s",
              "startAt": "2026-04-10T10:00:00Z",
              "endAt": "2026-04-10T11:00:00Z",
              "addressText": "addr",
              "notes": "notes"
            }
          """.formatted(RESOURCE_ID, CUSTOMER_USER_ID);
        MvcResult r = mvc.perform(post("/tickets/{ticketId}/appointments", ticketId)
                    .header("X-Tenant-Id", TENANT_ID)
                    .header("X-User-Id", AGENT_ID)
                    .header("X-Role", "AGENT")
                    .header("X-Request-Id", "it-appt-1")
                    .contentType("application/json")
                    .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, notNullValue()))
                .andExpect(jsonPath("$.appointmentId").exists())
                .andReturn();
        String apptId = om.readTree(r.getResponse().getContentAsString()).get("appointmentId").asText();
        // 2) GET 详情: 必须有Etag
        MvcResult r2 = mvc.perform(get("/appointments/{appointmentId}", apptId)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", AGENT_ID)
                .header("X-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, notNullValue()))
                .andReturn();
        String etag = r2.getResponse().getHeader(HttpHeaders.ETAG);
        // 3) patch if-match 不匹配 -> 409
        mvc.perform(patch("/appointments/{id}", apptId)
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-User-Id", AGENT_ID)
                .header("X-Role", "AGENT")
                .header(HttpHeaders.IF_MATCH, "\"9\"")
                .contentType("application/json")
                .content("""
                        {"notes": "changed"}
                        """))
                .andExpect(status().is4xxClientError());
        if(etag != null) {
            mvc.perform(patch("/appointments/{id}", apptId)
                            .header("X-Tenant-Id", TENANT_ID)
                            .header("X-User-Id", AGENT_ID)
                            .header("X-Role", "AGENT")
                            .header(HttpHeaders.IF_MATCH, etag)
                            .contentType("application/json")
                            .content("""
                        {"notes": "changed"}
                        """))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Test
    void postAppointments_overlap409_outsideWorkingHours409() throws Exception {
        ensureResourceUserMembershipAndWorkingHoursUtc();
        UUID ticketId = createdTicket();
        // 先考虑建10-11
        mvc.perform(post("/tickets/{ticketId}/appointments", ticketId)
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", AGENT_ID)
                        .header("X-Role", "AGENT")
                        .contentType("application/json")
                        .content("""
              {"resourceUserId":"%s", "customerUserId":"%s", "startAt":"2026-04-10T10:00:00Z","endAt":"2026-04-10T11:00:00Z"}
            """.formatted(RESOURCE_ID, CUSTOMER_USER_ID)))
                .andExpect(status().isCreated());
        // 重叠 10:30-11:30 -> 409
        mvc.perform(post("/tickets/{ticketId}/appointments", ticketId)
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", AGENT_ID)
                        .header("X-Role", "AGENT")
                        .contentType("application/json")
                        .content("""
              {"resourceUserId":"%s","customerUserId":"%s", "startAt":"2026-04-10T10:30:00Z","endAt":"2026-04-10T11:30:00Z"}
            """.formatted(RESOURCE_ID, CUSTOMER_USER_ID)))
                .andExpect(status().is4xxClientError());
        // 不在working hours (我们设置09-17 UTC), 20-21 -> 409 [AvailabilityValidator]
        mvc.perform(post("/tickets/{ticketId}/appointments", ticketId)
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", AGENT_ID)
                        .header("X-Role", "AGENT")
                        .contentType("application/json")
                        .content("""
              {"resourceUserId":"%s","customerUserId":"%s", "startAt":"2026-04-10T20:00:00Z","endAt":"2026-04-10T21:00:00Z"}
            """.formatted(RESOURCE_ID, CUSTOMER_USER_ID)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void permission_shouldHideExistence40x() throws Exception {
        ensureResourceUserMembershipAndWorkingHoursUtc();
        UUID ticketId = createdTicket();
        // agent 创建一个预约
        MvcResult r = mvc.perform(post("/tickets/{ticketId}/appointments", ticketId)
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", AGENT_ID)
                        .header("X-Role", "AGENT")
                        .contentType("application/json")
                        .content("""
              {"resourceUserId":"%s","customerUserId":"%s", "startAt":"2026-04-10T10:00:00Z","endAt":"2026-04-10T11:00:00Z"}
            """.formatted(RESOURCE_ID, CUSTOMER_USER_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        String apptId = om.readTree(r.getResponse().getContentAsString()).get("appointmentId").asText();
        mvc.perform(get("/appointments/{id}", apptId)
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", OTHER_CUSTOMER_USER_ID)
                        .header("X-Role", "CUSTOMER"))
                .andExpect(status().is4xxClientError());
    }
}
