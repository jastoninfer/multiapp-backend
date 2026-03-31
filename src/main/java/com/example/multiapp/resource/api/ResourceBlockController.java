package com.example.multiapp.resource.api;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import com.example.multiapp.resource.dto.CreateResourceBlockRequest;
import com.example.multiapp.resource.dto.ResourceBlockResponse;
import com.example.multiapp.resource.service.ResourceBlockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/resources/{resourceUserId}/blocks")
@RequiredArgsConstructor
public class ResourceBlockController {
    private final ResourceBlockService resourceBlockService;

    @PostMapping
    public ResponseEntity<ResourceBlockResponse> create(
            @PathVariable UUID resourceUserId,
            @Valid @RequestBody CreateResourceBlockRequest body,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        ResourceBlockResponse resp = resourceBlockService.create(ctx, resourceUserId, body);
        String etag = "\"" + resp.version() + "\"";
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag).body(resp);
    }

    @GetMapping
    public ResponseEntity<List<ResourceBlockResponse>> list(
            @PathVariable UUID resourceUserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        List<ResourceBlockResponse> blocks = resourceBlockService.list(ctx, resourceUserId, from, to);
        // 后续调用delete等写操作需要拿到version, 这里如果没有提供查询单个block接口
        // 那么前端应该主动从列表数据中的version字段解析, 并在后续写操作中带上作为ifmatch口令
        return ResponseEntity.ok().body(blocks);
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<Void> softDelete(
            @PathVariable UUID resourceUserId,
            @PathVariable UUID blockId,
            @NotNull @NotBlank
            @Pattern(regexp="^\"\\d+\"$", message="invalid If-Match")
            @RequestHeader(value = "If-Match", required = true)
            String ifMatch,
            HttpServletRequest req) {
        RequestContext ctx = RequestContexts.require(req);
        resourceBlockService.softDelete(ctx, resourceUserId, blockId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
