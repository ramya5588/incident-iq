package com.incidentiq.incident;

import java.util.List;
import java.util.UUID;

/** {@code watchers} is null for reporters, who see only the count. */
public record WatchersResponse(boolean watching, int count, List<Watcher> watchers) {

    public record Watcher(UUID id, String displayName) {
    }
}
