package com.incidentiq.reply;

/** No such reply, or someone else's personal one (404). */
public class SavedReplyNotFoundException extends RuntimeException {

    public SavedReplyNotFoundException() {
        super("Saved reply not found");
    }
}
