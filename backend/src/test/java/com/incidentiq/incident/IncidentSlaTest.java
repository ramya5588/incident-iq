package com.incidentiq.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.incidentiq.incident.IncidentSla.Clock;
import com.incidentiq.incident.IncidentSla.State;

class IncidentSlaTest {

    private static final Instant START = Instant.parse("2026-01-05T09:00:00Z");
    private static final Instant DUE = START.plus(Duration.ofHours(4));

    private static Clock at(Duration elapsed, Instant completedAt) {
        return Clock.of(START, DUE, completedAt, START.plus(elapsed), SlaCalendar.ALWAYS);
    }

    @Test
    void onTrackUntilOnlyAQuarterOfTheTimeIsLeft() {
        assertThat(at(Duration.ZERO, null).state()).isEqualTo(State.ON_TRACK);
        assertThat(at(Duration.ofMinutes(179), null).state()).isEqualTo(State.ON_TRACK);
        // 4h window: at 3h exactly one quarter (1h) is left.
        assertThat(at(Duration.ofHours(3), null).state()).isEqualTo(State.AT_RISK);
        assertThat(at(Duration.ofMinutes(239), null).state()).isEqualTo(State.AT_RISK);
    }

    @Test
    void breachedFromTheDeadlineOnwardsWhileNotDone() {
        Clock atDeadline = at(Duration.ofHours(4), null);
        assertThat(atDeadline.state()).isEqualTo(State.BREACHED);
        assertThat(atDeadline.completedAt()).isNull();
        assertThat(at(Duration.ofDays(3), null).state()).isEqualTo(State.BREACHED);
    }

    @Test
    void doneOnTimeIsMetAndDoneLateIsBreachedRegardlessOfNow() {
        Clock met = at(Duration.ofDays(10), DUE.minusSeconds(1));
        assertThat(met.state()).isEqualTo(State.MET);
        assertThat(met.completedAt()).isEqualTo(DUE.minusSeconds(1));

        assertThat(at(Duration.ofDays(10), DUE).state()).isEqualTo(State.MET);
        assertThat(at(Duration.ofDays(10), DUE.plusSeconds(1)).state()).isEqualTo(State.BREACHED);
    }

    @Test
    void slaPropertiesRequireEveryPriorityAndSaneTargets() {
        Map<IncidentPriority, SlaProperties.Target> targets = new EnumMap<>(IncidentPriority.class);
        for (IncidentPriority priority : IncidentPriority.values()) {
            targets.put(priority, new SlaProperties.Target(Duration.ofHours(1), Duration.ofHours(2)));
        }
        assertThat(new SlaProperties(targets).targetFor(IncidentPriority.LOW).resolve())
                .isEqualTo(Duration.ofHours(2));

        Map<IncidentPriority, SlaProperties.Target> missing = new EnumMap<>(targets);
        missing.remove(IncidentPriority.CRITICAL);
        assertThatThrownBy(() -> new SlaProperties(missing))
                .hasMessageContaining("CRITICAL");

        Map<IncidentPriority, SlaProperties.Target> backwards = new EnumMap<>(targets);
        backwards.put(IncidentPriority.HIGH,
                new SlaProperties.Target(Duration.ofHours(3), Duration.ofHours(1)));
        assertThatThrownBy(() -> new SlaProperties(backwards))
                .hasMessageContaining("HIGH");
    }
}
