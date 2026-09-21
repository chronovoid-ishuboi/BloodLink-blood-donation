package com.bloodlink.dao;

import com.bloodlink.model.ChatMessage;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class MessageDAO {

    /** The whole conversation for one request between exactly these two people, oldest first. */
    public List<ChatMessage> findThread(long requestId, long userA, long userB) throws SQLException {
        String sql = """
                SELECT m.id, m.request_id, m.sender_id, u.full_name sender_name, m.recipient_id,
                       m.body, m.sent_at, m.read_at
                FROM messages m JOIN users u ON u.id = m.sender_id
                WHERE m.request_id = ?
                  AND ((m.sender_id = ? AND m.recipient_id = ?) OR (m.sender_id = ? AND m.recipient_id = ?))
                ORDER BY m.sent_at
                """;
        List<ChatMessage> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, userA);
            statement.setLong(3, userB);
            statement.setLong(4, userB);
            statement.setLong(5, userA);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) rows.add(map(rs));
            }
        }
        return rows;
    }

    public void send(long requestId, long senderId, long recipientId, String body) throws SQLException {
        String sql = "INSERT INTO messages(request_id, sender_id, recipient_id, body) VALUES (?,?,?,?)";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, senderId);
            statement.setLong(3, recipientId);
            statement.setString(4, body);
            statement.executeUpdate();
        }
    }

    /** Marks everything the given user has received in this thread as read. */
    public void markThreadRead(long requestId, long recipientId, long senderId) throws SQLException {
        String sql = "UPDATE messages SET read_at = CURRENT_TIMESTAMP " +
                "WHERE request_id = ? AND recipient_id = ? AND sender_id = ? AND read_at IS NULL";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, recipientId);
            statement.setLong(3, senderId);
            statement.executeUpdate();
        }
    }

    public long unreadCount(long recipientId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages WHERE recipient_id = ? AND read_at IS NULL";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recipientId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private ChatMessage map(ResultSet rs) throws SQLException {
        Timestamp readAt = rs.getTimestamp("read_at");
        return new ChatMessage(rs.getLong("id"), rs.getLong("request_id"), rs.getLong("sender_id"),
                rs.getString("sender_name"), rs.getLong("recipient_id"), rs.getString("body"),
                rs.getTimestamp("sent_at").toLocalDateTime(), readAt == null ? null : readAt.toLocalDateTime());
    }
}
