package com.incidentiq.incident;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Works out what an uploaded file really is from its first bytes, never from
 * the name or the type the browser claims. Only a short allowlist is
 * accepted, and each is served back with a fixed, safe content type.
 */
final class AttachmentTypes {

    /** Text files are recognised by name and must be clean UTF-8. */
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "log", "csv", "json");

    record Detected(String contentType, boolean image) {
    }

    private AttachmentTypes() {
    }

    static Optional<Detected> detect(byte[] data, String fileName) {
        if (startsWith(data, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(new Detected("image/png", true));
        }
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(new Detected("image/jpeg", true));
        }
        if (startsWith(data, 'G', 'I', 'F', '8', '7', 'a') || startsWith(data, 'G', 'I', 'F', '8', '9', 'a')) {
            return Optional.of(new Detected("image/gif", true));
        }
        if (data.length >= 12 && startsWith(data, 'R', 'I', 'F', 'F')
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return Optional.of(new Detected("image/webp", true));
        }
        if (startsWith(data, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(new Detected("application/pdf", false));
        }
        if (TEXT_EXTENSIONS.contains(extension(fileName)) && isPlainUtf8(data)) {
            // Always plain text, so a ".txt" full of HTML is never rendered as a page.
            return Optional.of(new Detected("text/plain;charset=UTF-8", false));
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Valid UTF-8 with no NUL bytes (binary files nearly always have some). */
    private static boolean isPlainUtf8(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    /**
     * The name to store and offer for download: no folders, no control
     * characters, at most 255 characters (keeping the extension).
     */
    static String cleanFileName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "attachment";
        }
        if (name.length() > 255) {
            String ext = extension(name);
            String keep = ext.isEmpty() || ext.length() > 10 ? "" : "." + ext;
            name = name.substring(0, 255 - keep.length()) + keep;
        }
        return name;
    }
}
