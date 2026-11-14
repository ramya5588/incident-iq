package com.incidentiq.incident;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports for agents and admins. {@code from} and {@code to} are inclusive
 * dates in {@code timeZone} (the viewer's); {@code priority} is optional.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ReportSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "UTC") String timeZone,
            @RequestParam(required = false) IncidentPriority priority) {

        return service.summary(from, to, timeZone, priority);
    }

    @GetMapping("/incidents.csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "UTC") String timeZone,
            @RequestParam(required = false) IncidentPriority priority) {

        byte[] body = service.csv(from, to, timeZone, priority).getBytes(StandardCharsets.UTF_8);
        String name = "incidents-" + from + "-to-" + to
                + (priority == null ? "" : "-" + priority.name().toLowerCase()) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }
}
