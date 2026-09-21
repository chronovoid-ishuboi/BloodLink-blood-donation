package com.bloodlink.service;

import com.bloodlink.dao.MedicalDocumentDAO;
import com.bloodlink.model.MedicalDocument;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The medical reports donors attach when they register, and the rules about
 * who may read them.
 *
 * <h2>Why the checks are here and not in the screens</h2>
 * These are the most sensitive records in the application -- a prescription
 * names a condition, and the person it names did not consent to anyone but an
 * approving administrator seeing it. So reading one is gated on the live
 * session being either the document's owner or an admin, checked against the
 * database, not on the fact that only the admin dashboard currently has a
 * button for it. A hidden button is not access control.
 *
 * <p>Requesters and other donors have no path to these at all, deliberately:
 * a donor's health history is not part of what a match reveals.
 */
public final class MedicalDocumentService {

    /**
     * Per-file ceiling. Kept far below MEDIUMBLOB's 16MB: a phone photograph
     * of a prescription is one to four megabytes, and anything much larger is
     * a mistake -- a video, or the wrong file -- that would otherwise be
     * pushed across a cloud connection before failing.
     */
    public static final int MAX_FILE_BYTES = 6 * 1024 * 1024;

    /** How many a donor may attach. Enough for a report plus a prescription or two. */
    public static final int MAX_FILES = 5;

    /** What the file chooser offers and what is accepted. */
    public static final List<String> ALLOWED_EXTENSIONS =
            List.of("pdf", "png", "jpg", "jpeg", "webp");

    private final MedicalDocumentDAO documentDAO = new MedicalDocumentDAO();
    private final AuthorizationService authorizationService = new AuthorizationService();

    /**
     * Writes the files a donor attached during registration.
     * <p>
     * Called immediately after the account row is created, with the id it was
     * given. Not authorization-checked against the session, because at this
     * point there is no session: the account was created seconds ago and
     * nobody is signed in. The id comes from the insert that just happened,
     * not from anything the caller supplied.
     *
     * @return how many were stored
     */
    public int storeForNewAccount(long userId, List<PendingDocument> documents) throws SQLException {
        int stored = 0;
        for (PendingDocument document : documents) {
            documentDAO.insert(userId, document.fileName(), document.contentType(), document.content());
            stored++;
        }
        return stored;
    }

    /** Metadata for one user's documents. Owner or admin only. */
    public List<MedicalDocument> listFor(long userId) {
        try {
            authorizationService.requireSelfOrAdmin(userId);
            return documentDAO.findByUser(userId);
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }

    /** The bytes of one document, re-checking who owns it. Owner or admin only. */
    public Optional<byte[]> open(long documentId) {
        try {
            Optional<Long> owner = documentDAO.ownerOf(documentId);
            if (owner.isEmpty()) return Optional.empty();
            // Authorized against the document's real owner, looked up now,
            // rather than against a user id the caller passed alongside it --
            // otherwise anyone able to name a document id could also name an
            // owner they are allowed to read.
            authorizationService.requireSelfOrAdmin(owner.get());
            return documentDAO.loadContent(documentId);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public int countFor(long userId) {
        try {
            return documentDAO.countForUser(userId);
        } catch (SQLException e) {
            return 0;
        }
    }

    /** A file chosen during registration, held in memory until the account exists. */
    public record PendingDocument(String fileName, String contentType, byte[] content) {

        public String readableSize() {
            int size = content.length;
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.0f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    /** Guesses a content type from the file name; the browser-ish types are all we store. */
    public static String contentTypeFor(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    public static boolean isAllowed(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        for (String extension : ALLOWED_EXTENSIONS) {
            if (lower.endsWith("." + extension)) return true;
        }
        return false;
    }
}
