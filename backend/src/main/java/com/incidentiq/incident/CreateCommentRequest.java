package com.incidentiq.incident;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(

        @NotNull(message = "Author is required") UUID authorId,

        @NotBlank(message = "Comment cannot be empty") @Size(max = 2000, message = "Comment must not exceed 2000 characters") String body) {

    public CreateCommentRequest {
        body = body == null ? null : body.trim();
    }
}
