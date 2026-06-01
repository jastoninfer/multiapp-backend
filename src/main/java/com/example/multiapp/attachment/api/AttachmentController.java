package com.example.multiapp.attachment.api;

import com.example.multiapp.attachment.dto.AttachmentResponse;
import com.example.multiapp.attachment.dto.DownloadFile;
import com.example.multiapp.attachment.service.AttachmentService;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
@RequiredArgsConstructor

public class AttachmentController {
    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AttachmentResponse> uploadOne(
            @PathVariable UUID ticketId,
            @RequestPart("file")MultipartFile file,
            HttpServletRequest req) throws IOException {
        RequestContext ctx = RequestContexts.require(req);
        AttachmentResponse resp = attachmentService.uploadOne(ctx, ticketId, file);
        URI location = URI.create("/tickets/" + ticketId + "/attachments/"
                + resp.attachmentId() + "/download");
        return ResponseEntity.created(location).body(resp);
    }

    @PostMapping(path="/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                        produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AttachmentResponse>> uploadBatch(
            @PathVariable UUID ticketId,
            @RequestPart("files") List<MultipartFile> files,
            HttpServletRequest req) throws IOException {
        RequestContext ctx = RequestContexts.require(req);
        List<AttachmentResponse> out = new ArrayList<>();
        for (MultipartFile f : files) out.add(attachmentService.uploadOne(ctx, ticketId, f));
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    @GetMapping(path = "/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID ticketId,
            @PathVariable UUID attachmentId,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        DownloadFile f = attachmentService.download(ctx, ticketId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment().filename(f.filename()).build().toString())
                .contentLength(f.sizeBytes())
                .body(f.resource());
    }
}
