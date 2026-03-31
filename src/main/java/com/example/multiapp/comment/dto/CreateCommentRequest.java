package com.example.multiapp.comment.dto;

import com.example.multiapp.comment.model.CommentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotNull
        CommentVisibility visibility,
        @NotNull @NotBlank
        @Pattern(regexp = "^(?:[^\\p{Cc}]|[\\r\\n\\t])*$", message = "invalid comment")
        @Size(max = 4000)
        String body
) {
    public CreateCommentRequest {
        body = body.strip();
    }
}
