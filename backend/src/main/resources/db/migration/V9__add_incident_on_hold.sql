-- "On hold": work is waiting on someone outside the team, so the
-- resolution SLA clock is paused. Time spent on hold is added to the
-- resolution deadline when the incident comes off hold.
ALTER TABLE incidents
    DROP CONSTRAINT incidents_status_check;

ALTER TABLE incidents
    ADD CONSTRAINT incidents_status_check
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED')),
    ADD COLUMN hold_reason VARCHAR(30),
    ADD COLUMN hold_note VARCHAR(200),
    ADD COLUMN on_hold_since TIMESTAMP WITH TIME ZONE,
    -- Total time spent on hold in earlier (finished) holds, in microseconds.
    ADD COLUMN sla_paused_micros BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT incidents_hold_reason_check
        CHECK (hold_reason IN ('WAITING_ON_REPORTER', 'WAITING_ON_THIRD_PARTY',
                               'SCHEDULED', 'OTHER')),
    ADD CONSTRAINT incidents_on_hold_check
        CHECK ((status = 'ON_HOLD') = (on_hold_since IS NOT NULL AND hold_reason IS NOT NULL)),
    ADD CONSTRAINT incidents_sla_paused_check
        CHECK (sla_paused_micros >= 0);
