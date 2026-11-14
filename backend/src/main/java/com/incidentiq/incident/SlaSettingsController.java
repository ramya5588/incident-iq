package com.incidentiq.incident;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.auth.AppUserPrincipal;

import jakarta.validation.Valid;

/** SLA business hours: everyone can read them; only admins change them. */
@RestController
@RequestMapping("/api/sla/business-hours")
public class SlaSettingsController {

    private final BusinessHoursService service;

    public SlaSettingsController(BusinessHoursService service) {
        this.service = service;
    }

    @GetMapping
    public BusinessHoursDto get() {
        return service.get();
    }

    @PutMapping
    public BusinessHoursDto update(
            @Valid @RequestBody BusinessHoursDto request,
            @AuthenticationPrincipal AppUserPrincipal admin) {

        return service.update(request, admin.id());
    }
}
