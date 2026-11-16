package com.incidentiq.reply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param personal true for a reply only you see; false (or left out) for a
 *        shared one, which only admins can add. Ignored when editing.
 */
public record SavedReplyRequest(
        @NotBlank(message = "Title cannot be empty")
        @Size(max = 100, message = "Title must be at most 100 characters")
        String title,
        @NotBlank(message = "Text cannot be empty")
        @Size(max = 2000, message = "Text must be at most 2000 characters")
        String body,
        Boolean personal) {

    public SavedReplyRequest {
        title = title == null ? null : title.trim();
        body = body == null ? null : body.strip();
    }

    boolean isPersonal() {
        return Boolean.TRUE.equals(personal);
    }
}
