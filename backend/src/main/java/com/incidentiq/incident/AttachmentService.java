package com.incidentiq.incident;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.incidentiq.user.UserAccount;
import com.incidentiq.user.UserAccountRepository;
import com.incidentiq.user.UserRole;

/**
 * Files on incidents. Anyone signed in can see them (like the incident).
 * Agents and admins can attach to any incident; reporters only to their own.
 * The uploader or an admin can remove one.
 */
@Service
public class AttachmentService {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_PER_INCIDENT = 10;
    static final String ALLOWED_TYPES = "images (PNG, JPEG, GIF, WebP), PDFs and text files (.txt, .log, .csv, .json)";

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final IncidentAttachmentRepository attachments;
    private final IncidentRepository incidents;
    private final IncidentEventRepository events;
    private final UserAccountRepository users;
    private final Clock clock;

    public AttachmentService(
            IncidentAttachmentRepository attachments,
            IncidentRepository incidents,
            IncidentEventRepository events,
            UserAccountRepository users,
            Clock clock) {
        this.attachments = attachments;
        this.incidents = incidents;
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    private Incident findIncident(UUID incidentId) {
        return incidents.findById(incidentId).orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }

    private UserAccount findUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID incidentId) {
        if (!incidents.existsById(incidentId)) {
            throw new IncidentNotFoundException(incidentId);
        }
        return attachments.findInfoByIncidentId(incidentId).stream().map(AttachmentResponse::from).toList();
    }

    @Transactional
    public AttachmentResponse upload(UUID incidentId, MultipartFile file, UUID actorId) {
        Incident incident = findIncident(incidentId);
        UserAccount actor = findUser(actorId);
        if (actor.getRole() == UserRole.USER && !incident.getCreatedBy().getId().equals(actorId)) {
            throw new AttachmentForbiddenException("You can only attach files to incidents you reported.");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidAttachmentException("Choose a file to attach. Empty files can't be attached.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidAttachmentException("Files can be at most 5 MB.");
        }
        if (attachments.countByIncidentId(incidentId) >= MAX_PER_INCIDENT) {
            throw new InvalidAttachmentException(
                    "An incident can have at most " + MAX_PER_INCIDENT + " attachments. Remove one first.");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        String fileName = AttachmentTypes.cleanFileName(file.getOriginalFilename());
        AttachmentTypes.Detected type = AttachmentTypes.detect(data, fileName)
                .orElseThrow(() -> new InvalidAttachmentException(
                        "That kind of file can't be attached. You can attach " + ALLOWED_TYPES + "."));

        IncidentAttachment saved = attachments.saveAndFlush(new IncidentAttachment(
                incident, actor, fileName, type.contentType(), data,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        events.save(IncidentEvent.attachment(incident, IncidentEventType.ATTACHMENT_ADDED, fileName, actor));
        log.info("User {} attached a {} ({} bytes) to incident {}", actorId, type.contentType(), data.length, incidentId);

        return AttachmentResponse.from(attachments.findInfo(incidentId, saved.getId()).orElseThrow());
    }

    /** The file itself, checked to belong to the incident in the URL. */
    @Transactional(readOnly = true)
    public IncidentAttachment download(UUID incidentId, UUID attachmentId) {
        return attachments.findByIdAndIncidentId(attachmentId, incidentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
    }

    @Transactional
    public void delete(UUID incidentId, UUID attachmentId, UUID actorId) {
        IncidentAttachmentRepository.Info info = attachments.findInfo(incidentId, attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        UserAccount actor = findUser(actorId);
        if (actor.getRole() != UserRole.ADMIN && !info.getUploadedById().equals(actorId)) {
            throw new AttachmentForbiddenException("Only the person who added a file, or an admin, can remove it.");
        }

        attachments.deleteByIdQuietly(attachmentId);
        Incident incident = findIncident(incidentId);
        events.save(IncidentEvent.attachment(
                incident, IncidentEventType.ATTACHMENT_REMOVED, info.getFileName(), findUser(actorId)));
        log.info("User {} removed attachment {} from incident {}", actorId, attachmentId, incidentId);
    }
}
