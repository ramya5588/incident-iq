package com.incidentiq.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds the incident list query from whichever filters are present, so one
 * code path serves every combination of status, priority and search text.
 */
final class IncidentSpecifications {

    /** "42", "042", "INC-42", "inc42" all mean incident number 42. */
    private static final Pattern INCIDENT_NUMBER =
            Pattern.compile("^(?:inc-?)?0*(\\d{1,18})$", Pattern.CASE_INSENSITIVE);

    private static final char LIKE_ESCAPE = '\\';

    private IncidentSpecifications() {
    }

    static Specification<Incident> filter(
            IncidentStatus status,
            IncidentPriority priority,
            String search) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }

            String term = search == null ? "" : search.trim();
            if (!term.isEmpty()) {
                String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";

                List<Predicate> matches = new ArrayList<>(List.of(
                        cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("category")), pattern, LIKE_ESCAPE)));

                Matcher number = INCIDENT_NUMBER.matcher(term);
                if (number.matches()) {
                    matches.add(cb.equal(
                            root.get("incidentNumber"), Long.parseLong(number.group(1))));
                }

                predicates.add(cb.or(matches.toArray(Predicate[]::new)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Treat %, _ and the escape character itself as plain text. */
    static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == '%' || c == '_' || c == LIKE_ESCAPE) {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
