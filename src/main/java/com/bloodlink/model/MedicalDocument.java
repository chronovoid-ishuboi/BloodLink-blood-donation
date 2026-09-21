package com.bloodlink.model;

import java.time.LocalDateTime;

/**
 * A medical report or prescription a donor attached at registration.
 *
 * <p>Metadata only. The file itself is fetched separately by id, so listing a
 * donor's documents in the admin table does not drag several megabytes of
 * scans across the network for rows nobody has opened.
 */
public record MedicalDocument(
        long id,
        long userId,
        String fileName,
        String contentType,
        int sizeBytes,
        LocalDateTime uploadedAt) {

    /** Size as a person reads it, for the admin list. */
    public String readableSize() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) return String.format("%.0f KB", sizeBytes / 1024.0);
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    /** True when this can be shown inline as a picture rather than saved out to be opened. */
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
}
