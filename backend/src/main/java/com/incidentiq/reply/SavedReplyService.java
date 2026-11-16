package com.incidentiq.reply;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.incidentiq.auth.AppUserPrincipal;
import com.incidentiq.user.UserRole;

/**
 * Saved replies for agents and admins (SecurityConfig keeps reporters out).
 * Admins manage the shared ones; everyone manages their own personal ones.
 * Someone else's personal reply is treated as not existing.
 */
@Service
public class SavedReplyService {

    /** Most personal replies one person can keep. */
    static final int MAX_PERSONAL = 50;

    private final SavedReplyRepository replies;
    private final Clock clock;

    public SavedReplyService(SavedReplyRepository replies, Clock clock) {
        this.replies = replies;
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    @Transactional(readOnly = true)
    public List<SavedReplyResponse> list(AppUserPrincipal me) {
        return replies.findVisibleTo(me.id()).stream()
                .map(reply -> SavedReplyResponse.of(reply, canEdit(reply, me)))
                .toList();
    }

    @Transactional
    public SavedReplyResponse create(SavedReplyRequest request, AppUserPrincipal me) {
        if (!request.isPersonal() && me.role() != UserRole.ADMIN) {
            throw new SavedReplyForbiddenException("Only admins can add shared replies. Add it as a personal reply instead.");
        }
        if (request.isPersonal()
                && replies.findVisibleTo(me.id()).stream().filter(r -> !r.isShared()).count() >= MAX_PERSONAL) {
            throw new SavedReplyForbiddenException("You can keep at most " + MAX_PERSONAL + " personal replies.");
        }
        SavedReply reply = replies.saveAndFlush(new SavedReply(
                request.title(), request.body(), request.isPersonal() ? me.id() : null, now()));
        return SavedReplyResponse.of(reply, true);
    }

    @Transactional
    public SavedReplyResponse update(UUID id, SavedReplyRequest request, AppUserPrincipal me) {
        SavedReply reply = findEditable(id, me);
        reply.update(request.title(), request.body(), now());
        return SavedReplyResponse.of(replies.saveAndFlush(reply), true);
    }

    @Transactional
    public void delete(UUID id, AppUserPrincipal me) {
        replies.delete(findEditable(id, me));
    }

    private SavedReply findEditable(UUID id, AppUserPrincipal me) {
        SavedReply reply = replies.findById(id)
                .filter(found -> found.isShared() || found.getOwnerId().equals(me.id()))
                .orElseThrow(SavedReplyNotFoundException::new);
        if (!canEdit(reply, me)) {
            throw new SavedReplyForbiddenException("Only admins can change shared replies.");
        }
        return reply;
    }

    private static boolean canEdit(SavedReply reply, AppUserPrincipal me) {
        return reply.isShared() ? me.role() == UserRole.ADMIN : reply.getOwnerId().equals(me.id());
    }
}
