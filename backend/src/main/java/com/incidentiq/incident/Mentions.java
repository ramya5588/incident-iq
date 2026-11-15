package com.incidentiq.incident;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.incidentiq.user.UserAccount;

/**
 * @mentions in comments, written as {@code @[Name](user-id)} so they keep
 * pointing at the right person. The client's name is never trusted: each
 * mention is rewritten with the person's real name, and one the author isn't
 * allowed to make (or of an unknown person) becomes plain text.
 */
final class Mentions {

    /** Most people one comment notifies by mention. */
    static final int MAX_MENTIONED = 20;

    private static final Pattern MENTION = Pattern.compile(
            "@\\[([^\\]\\n]{1,100})\\]\\(([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)");

    record Resolved(String body, List<UserAccount> mentioned) {
    }

    private Mentions() {
    }

    static Resolved resolve(
            String body,
            Function<UUID, Optional<UserAccount>> lookup,
            Predicate<UserAccount> allowed) {

        Map<UUID, UserAccount> mentioned = new LinkedHashMap<>();
        Matcher matcher = MENTION.matcher(body);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            UUID id = UUID.fromString(matcher.group(2));
            Optional<UserAccount> user = mentioned.containsKey(id)
                    ? Optional.of(mentioned.get(id))
                    : lookup.apply(id).filter(allowed);

            String replacement;
            if (user.isPresent() && (mentioned.containsKey(id) || mentioned.size() < MAX_MENTIONED)) {
                mentioned.putIfAbsent(id, user.get());
                replacement = "@[" + label(user.get().getDisplayName()) + "](" + id + ")";
            } else {
                replacement = "@" + matcher.group(1);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return new Resolved(result.toString(), new ArrayList<>(mentioned.values()));
    }

    /** The text as people read it: {@code @[Sam](id)} counts as {@code @Sam}. */
    static String visibleText(String body) {
        return MENTION.matcher(body).replaceAll(match -> Matcher.quoteReplacement("@" + match.group(1)));
    }

    private static String label(String displayName) {
        String clean = displayName.replace("]", "").replace("\n", " ").strip();
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }
}
