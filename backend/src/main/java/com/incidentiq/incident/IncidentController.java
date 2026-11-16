package com.incidentiq.incident;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.incidentiq.auth.AppUserPrincipal;
import com.incidentiq.user.UserRole;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService service;
    private final BulkIncidentService bulk;
    private final WatcherService watchers;
    private final MergeService merges;

    public IncidentController(
            IncidentService service, BulkIncidentService bulk, WatcherService watchers,
            MergeService merges) {
        this.service = service;
        this.bulk = bulk;
        this.watchers = watchers;
        this.merges = merges;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @Valid @RequestBody CreateIncidentRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        IncidentResponse response = service.createIncident(request, actor.id());
        URI location = URI.create("/api/incidents/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/stats")
    public IncidentStatsResponse getStats(
            @RequestParam(required = false) Instant resolvedSince) {

        return service.getStats(resolvedSince);
    }

    @GetMapping("/workload")
    public List<AssigneeWorkloadResponse> getWorkload() {
        return service.getWorkload();
    }

    @GetMapping("/{incidentId}")
    public IncidentResponse getIncident(
            @PathVariable UUID incidentId) {

        return service.getIncident(incidentId);
    }

    @GetMapping
    public Page<IncidentResponse> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentPriority priority,
            @RequestParam(name = "q", required = false)
            @Size(max = 200, message = "Search must not exceed 200 characters") String search,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "false") boolean watching,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return service.getIncidents(
                status, priority, search, overdue, watching ? actor.id() : null, pageable);
    }

    /** Closes this incident as a duplicate of another (agents and admins). */
    @PutMapping("/{incidentId}/duplicate-of")
    public IncidentResponse markDuplicate(
            @PathVariable UUID incidentId,
            @Valid @RequestBody MarkDuplicateRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return merges.markDuplicate(incidentId, request.mainIncidentId(), actor.id());
    }

    /** Undoes it: no longer a duplicate, and reopened. */
    @DeleteMapping("/{incidentId}/duplicate-of")
    public IncidentResponse undoDuplicate(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return merges.undo(incidentId, actor.id());
    }

    /** The incidents merged into this one. */
    @GetMapping("/{incidentId}/duplicates")
    public List<MergeService.DuplicateResponse> duplicates(@PathVariable UUID incidentId) {
        return merges.duplicates(incidentId);
    }

    /** The reporter rates a resolved incident, 1 to 5 (can be changed later). */
    @PutMapping("/{incidentId}/rating")
    public IncidentResponse rate(
            @PathVariable UUID incidentId,
            @Valid @RequestBody RateIncidentRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return service.rate(incidentId, request, actor.id());
    }

    /** Whether you watch it and how many do; agents and admins also see who. */
    @GetMapping("/{incidentId}/watchers")
    public WatchersResponse getWatchers(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return watchers.get(incidentId, actor.id(), actor.role() != UserRole.USER);
    }

    @PutMapping("/{incidentId}/watchers/me")
    public WatchersResponse watch(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return watchers.watch(incidentId, actor.id(), actor.role() != UserRole.USER);
    }

    @DeleteMapping("/{incidentId}/watchers/me")
    public WatchersResponse stopWatching(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return watchers.stopWatching(incidentId, actor.id(), actor.role() != UserRole.USER);
    }

    /** The same status, priority or assignee change for several incidents. */
    @PatchMapping("/bulk")
    public BulkUpdateResponse bulkUpdate(
            @Valid @RequestBody BulkUpdateRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return bulk.update(request, actor.id());
    }

    @PatchMapping("/{incidentId}")
    public IncidentResponse updateIncident(
            @PathVariable UUID incidentId,
            @Valid @RequestBody UpdateIncidentRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return service.updateIncident(incidentId, request, actor.id());
    }

    @PostMapping("/{incidentId}/triage")
    public IncidentResponse triageIncident(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return service.triageIncident(incidentId, actor.id());
    }

    @PatchMapping("/{incidentId}/status")
    public IncidentResponse updateStatus(
            @PathVariable UUID incidentId,
            @Valid @RequestBody UpdateIncidentStatusRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return service.updateStatus(incidentId, request, actor.id());
    }

    @GetMapping("/{incidentId}/events")
    public List<IncidentEventResponse> getEvents(
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        // Reporters never receive internal notes.
        return service.getEvents(incidentId, actor.role() != UserRole.USER);
    }

    @PostMapping("/{incidentId}/comments")
    public ResponseEntity<IncidentEventResponse> addComment(
            @PathVariable UUID incidentId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        IncidentEventResponse comment = service.addComment(incidentId, request, actor.id());

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @PatchMapping("/{incidentId}/assignee")
    public IncidentResponse updateAssignee(
            @PathVariable UUID incidentId,
            @RequestBody UpdateIncidentAssigneeRequest request,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return service.updateAssignee(incidentId, request, actor.id());
    }
}