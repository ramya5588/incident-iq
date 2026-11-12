package com.incidentiq.incident;

/**
 * Header the frontend uses to say who is making a change, until there is
 * real authentication. It is trusted as-is: attribution, not security.
 */
public final class ActorHeader {

    public static final String NAME = "X-User-Id";

    private ActorHeader() {
    }
}
