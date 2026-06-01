package com.example.multiapp.comment.api;

import com.example.multiapp.comment.dto.CommentResponse;
import com.example.multiapp.comment.dto.CommentSummary;
import com.example.multiapp.comment.dto.CreateCommentRequest;
import com.example.multiapp.comment.service.CommentService;
import com.example.multiapp.common.api.PageResponse;
import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.common.web.RequestContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    @PostMapping()
    public ResponseEntity<Void> post(
            @PathVariable UUID ticketId,
            @Valid @RequestBody CreateCommentRequest body,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        commentService.post(ctx, ticketId, body);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PageResponse<CommentSummary> list(
            @PathVariable UUID ticketId,
            @PageableDefault(size = 25)Pageable pageable,
            HttpServletRequest req
    ) {
        RequestContext ctx = RequestContexts.require(req);
        return PageResponse.from(commentService.list(ctx, ticketId, pageable));
    }
}
