package com.incidentiq.incident;

import java.time.Instant;
import java.util.UUID;

import com.incidentiq.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A file attached to an incident. Listing uses a projection without the bytes. */
@Entity
@Table(name = "incident_attachments")
public class IncidentAttachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false, updatable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false, updatable = false)
    private UserAccount uploadedBy;

    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(nullable = false, updatable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncidentAttachment() {
    }

    IncidentAttachment(Incident incident, UserAccount uploadedBy, String fileName,
            String contentType, byte[] data, Instant createdAt) {
        // Random, so attachment URLs can't be guessed (they also need sign-in).
        this.id = UUID.randomUUID();
        this.incident = incident;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = data.length;
        this.data = data;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getFileName() {
        return fileName;
    }

    String getContentType() {
        return contentType;
    }

    byte[] getData() {
        return data;
    }
}
