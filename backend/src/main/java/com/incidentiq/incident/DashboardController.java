package com.incidentiq.incident;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** Agents and admins (SecurityConfig). */
    @GetMapping("/my-work")
    public DashboardService.MyWorkResponse myWork(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.myWork(me.id());
    }

    /** Anyone signed in: the incidents they reported. */
    @GetMapping("/my-incidents")
    public List<IncidentResponse> myIncidents(@AuthenticationPrincipal AppUserPrincipal me) {
        return service.myIncidents(me.id());
    }
}
