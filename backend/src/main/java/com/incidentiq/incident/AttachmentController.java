package com.incidentiq.incident;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.incidentiq.auth.AppUserPrincipal;

@RestController
@RequestMapping("/api/incidents/{incidentId}/attachments")
public class AttachmentController {

    /**
     * Served files can't run anything: no scripts, no plugins, no framing,
     * sandboxed even if opened directly. Images may show themselves.
     */
    static final String FILE_CSP =
            "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; frame-ancestors 'none'; sandbox";

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable UUID incidentId) {
        return service.list(incidentId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable UUID incidentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(incidentId, file, actor.id()));
    }

    /** Images open in the browser (for previews); everything else downloads. */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID incidentId, @PathVariable UUID attachmentId) {
        IncidentAttachment attachment = service.download(incidentId, attachmentId);
        boolean image = attachment.getContentType().startsWith("image/");
        ContentDisposition disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(attachment.getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("Content-Security-Policy", FILE_CSP)
                .header("X-Content-Type-Options", "nosniff")
                .body(attachment.getData());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID incidentId,
            @PathVariable UUID attachmentId,
            @AuthenticationPrincipal AppUserPrincipal actor) {

        service.delete(incidentId, attachmentId, actor.id());
        return ResponseEntity.noContent().build();
    }
}
