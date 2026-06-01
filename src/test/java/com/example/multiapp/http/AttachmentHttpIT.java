package com.example.multiapp.http;

import com.example.multiapp.testinfra.TestRequestContextWebConfig;
import com.example.multiapp.testinfra.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@Import({TestSecurityConfig.class, TestRequestContextWebConfig.class})
// attachments: upload + download Content-Disposition
public class AttachmentHttpIT extends AbstractHttpIT {
    @Test
    void uploadAndDownload_shouldReturnContentDispositionAttachment() throws Exception {
        UUID ticketId = createdTicket();
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.png", "image/png", "hello".getBytes()
        );
        MvcResult r = mvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", AGENT_ID)
                        .header("X-Role", "AGENT")
                        .header("X-Request-Id", "it-att-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentId", notNullValue()))
                .andExpect(jsonPath("$.downloadUrl", notNullValue()))
                .andReturn();
        String attachmentId = om.readTree(r.getResponse().getContentAsString()).get("attachmentId").asText();

        mvc.perform(get("/tickets/{ticketId}/attachments/{attachmentId}/download", ticketId, attachmentId)
                        .header("X-Tenant-Id", TENANT_ID)
                        .header("X-User-Id", AGENT_ID)
                        .header("X-Role", "AGENT"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().bytes("hello".getBytes()));

    }
}
