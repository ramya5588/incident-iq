package com.incidentiq.incident;

/** Only the reporter can rate their incident (403). */
public class RatingNotAllowedException extends RuntimeException {

    public RatingNotAllowedException() {
        super("Only the person who reported an incident can rate it.");
    }
}
