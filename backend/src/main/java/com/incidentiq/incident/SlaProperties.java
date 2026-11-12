package com.incidentiq.incident;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SLA targets per priority, from {@code incidentiq.sla.targets.<PRIORITY>.*}
 * (see application.properties). Every priority must have both targets.
 */
@ConfigurationProperties("incidentiq.sla")
public record SlaProperties(Map<IncidentPriority, Target> targets) {

    /**
     * @param respond time allowed until someone first picks the incident up
     *        (assigns it or moves it out of Open)
     * @param resolve time allowed until it is resolved
     */
    public record Target(Duration respond, Duration resolve) {
    }

    public SlaProperties {
        Map<IncidentPriority, Target> checked = new EnumMap<>(IncidentPriority.class);
        for (IncidentPriority priority : IncidentPriority.values()) {
            Target target = targets == null ? null : targets.get(priority);
            if (target == null || target.respond() == null || target.resolve() == null) {
                throw new IllegalStateException(
                        "Missing SLA targets for " + priority + ": set incidentiq.sla.targets."
                                + priority + ".respond and .resolve");
            }
            if (target.respond().isNegative() || target.respond().isZero()
                    || target.resolve().compareTo(target.respond()) < 0) {
                throw new IllegalStateException(
                        "Invalid SLA targets for " + priority
                                + ": respond must be positive and no longer than resolve");
            }
            checked.put(priority, target);
        }
        targets = Map.copyOf(checked);
    }

    public Target targetFor(IncidentPriority priority) {
        return targets.get(priority);
    }
}
