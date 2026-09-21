package com.bloodlink.dao;

import com.bloodlink.model.MedicalDocument;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the medical reports donors attach at registration.
 *
 * <p>Every list query selects metadata only. {@link #loadContent} is the one
 * method that moves the file, and it is called for a single document that
 * somebody has actually asked to open.
 */
public final class MedicalDocumentDAO {

    /**
     * Stores one file against a user.
     *
     * @return the new document's id
     */
    public long insert(long userId, String fileName, String contentType, byte[] content) throws SQLException {
        String sql = """
                INSERT INTO medical_documents (user_id, file_name, content_type, size_bytes, content)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, userId);
            statement.setString(2, fileName);
            statement.setString(3, contentType == null ? "" : contentType);
            statement.setInt(4, content.length);
            statement.setBytes(5, content);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    /** Every document a user has attached, newest first. Metadata only. */
    public List<MedicalDocument> findByUser(long userId) throws SQLException {
        String sql = """
                SELECT id, user_id, file_name, content_type, size_bytes, uploaded_at
                FROM medical_documents WHERE user_id = ? ORDER BY uploaded_at DESC, id DESC
                """;
        List<MedicalDocument> documents = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) documents.add(map(rs));
            }
        }
        return documents;
    }

    /** How many documents a user has, without fetching any of them. */
    public int countForUser(long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM medical_documents WHERE user_id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** The bytes of one document. Empty if the row is gone. */
    public Optional<byte[]> loadContent(long documentId) throws SQLException {
        String sql = "SELECT content FROM medical_documents WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, documentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBytes("content")) : Optional.empty();
            }
        }
    }

    /** The user a document belongs to, so callers can authorize against it. */
    public Optional<Long> ownerOf(long documentId) throws SQLException {
        String sql = "SELECT user_id FROM medical_documents WHERE id = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, documentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong("user_id")) : Optional.empty();
            }
        }
    }

    private MedicalDocument map(ResultSet rs) throws SQLException {
        java.sql.Timestamp uploaded = rs.getTimestamp("uploaded_at");
        return new MedicalDocument(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("file_name"),
                rs.getString("content_type"),
                rs.getInt("size_bytes"),
                uploaded == null ? null : uploaded.toLocalDateTime());
    }
}
