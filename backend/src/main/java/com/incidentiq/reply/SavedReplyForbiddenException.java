package com.incidentiq.reply;

/** Not allowed to add or change this reply (403). */
public class SavedReplyForbiddenException extends RuntimeException {

    public SavedReplyForbiddenException(String message) {
        super(message);
    }
}
