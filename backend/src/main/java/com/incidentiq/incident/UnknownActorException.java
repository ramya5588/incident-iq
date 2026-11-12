package com.incidentiq.incident;

import java.util.UUID;

/**
 * The X-User-Id header named a user that does not exist. This is a bad
 * request (400), unlike an unknown user in the path or body (404).
 */
public class UnknownActorException extends RuntimeException {

    public UnknownActorException(UUID userId) {
        super("Unknown acting user in " + ActorHeader.NAME + ": " + userId);
    }
}
