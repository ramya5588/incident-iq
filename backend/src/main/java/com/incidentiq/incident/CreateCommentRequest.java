package com.incidentiq.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The author is the signed-in user, not something the client sends. */
public record CreateCommentRequest(

        @NotBlank(message = "Comment cannot be empty") @Size(max = 2000, message = "Comment must not exceed 2000 characters") String body) {

    public CreateCommentRequest {
        body = body == null ? null : body.trim();
    }
}
