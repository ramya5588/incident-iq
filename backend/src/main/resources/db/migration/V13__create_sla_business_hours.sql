-- Business hours for SLA deadlines. One settings row; off until an admin
-- turns it on, so deadlines keep running around the clock by default.
CREATE TABLE sla_business_hours (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    day_start TIME NOT NULL DEFAULT '09:00',
    day_end TIME NOT NULL DEFAULT '17:00',
    -- Comma-separated DayOfWeek names.
    working_days VARCHAR(70) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY',
    -- Comma-separated priorities whose deadlines count business hours only.
    priorities VARCHAR(40) NOT NULL DEFAULT 'HIGH,MEDIUM,LOW',
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT sla_business_hours_day_check CHECK (day_start < day_end)
);

INSERT INTO sla_business_hours (id) VALUES (1);

-- Days that don't count, in the business-hours time zone.
CREATE TABLE sla_holidays (
    day DATE PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
