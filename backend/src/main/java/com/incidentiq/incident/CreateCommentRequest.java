package com.incidentiq.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The author is the signed-in user, not something the client sends.
 * {@code internal}: an internal note (agents and admins only); omitted means
 * a normal comment.
 */
public record CreateCommentRequest(

        @NotBlank(message = "Comment cannot be empty") @Size(max = 4000, message = "Comment must not exceed 2000 characters") String body,
        Boolean internal) {

    public CreateCommentRequest {
        body = body == null ? null : body.trim();
    }

    public CreateCommentRequest(String body) {
        this(body, null);
    }

    public boolean isInternal() {
        return Boolean.TRUE.equals(internal);
    }
}
