package com.incidentiq;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.incidentiq.incident.SlaProperties;

/**
 * The clock used for SLA deadlines and incident timestamps. Tests replace it
 * with a controllable one so deadlines can be checked exactly. Also turns on
 * scheduled jobs (the SLA breach check).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SlaProperties.class)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
