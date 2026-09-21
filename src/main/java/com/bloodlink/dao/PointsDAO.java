package com.bloodlink.dao;

import com.bloodlink.model.PointTransaction;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * donor_profiles.points_balance is a running total, kept in sync with the
 * point_transactions ledger by {@link #award} rather than ever being derived
 * with a SUM() on read -- the same running-total-plus-audit-ledger shape
 * already used for verified_donation_count/donation_history.
 */
public final class PointsDAO {

    /** Awarded once per request when RequestDAO.finalizeIfBothConfirmed records a verified donation. */
    public static final int DONATION_COMPLETED_POINTS = 100;
    /** Awarded to a donor the moment a requester favorites them -- see migration_008's note on the unique key preventing farming. */
    public static final int FAVORITED_BONUS_POINTS = 25;

    public int balanceOf(long donorId) throws SQLException {
        String sql = "SELECT points_balance FROM donor_profiles WHERE user_id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("points_balance") : 0;
            }
        }
    }

    /** Row-locks the donor's balance for the duration of the caller's transaction, so two concurrent redemptions cannot both pass the same balance check before either commits. */
    public int lockBalance(Connection connection, long donorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT points_balance FROM donor_profiles WHERE user_id=? FOR UPDATE")) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("points_balance") : 0;
            }
        }
    }

    /**
     * Records {@code delta} (positive to award, negative to spend) against
     * {@code donorId} and updates their running balance, on the caller's
     * connection/transaction. The CHECK constraint on points_balance backstops
     * this against ever going negative if a caller's own spend-side validation
     * has a bug.
     */
    public void award(Connection connection, long donorId, int delta, String reason, Long relatedRequestId, String note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO point_transactions(donor_id, delta, reason, related_request_id, note) VALUES (?,?,?,?,?)")) {
            statement.setLong(1, donorId);
            statement.setInt(2, delta);
            statement.setString(3, reason);
            if (relatedRequestId == null) statement.setNull(4, Types.BIGINT); else statement.setLong(4, relatedRequestId);
            statement.setString(5, note == null ? "" : note);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE donor_profiles SET points_balance = points_balance + ? WHERE user_id=?")) {
            statement.setInt(1, delta);
            statement.setLong(2, donorId);
            statement.executeUpdate();
        }
    }

    /** Convenience wrapper for callers with no existing transaction to join (e.g. a one-off admin action). */
    public void award(long donorId, int delta, String reason, Long relatedRequestId, String note) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            award(connection, donorId, delta, reason, relatedRequestId, note);
        }
    }

    public List<PointTransaction> historyOf(long donorId, int limit) throws SQLException {
        String sql = "SELECT id, donor_id, delta, reason, related_request_id, note, created_at " +
                "FROM point_transactions WHERE donor_id=? ORDER BY created_at DESC LIMIT ?";
        List<PointTransaction> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long relatedRequestId = rs.getLong("related_request_id");
                    rows.add(new PointTransaction(rs.getLong("id"), rs.getLong("donor_id"), rs.getInt("delta"),
                            rs.getString("reason"), rs.wasNull() ? null : relatedRequestId, rs.getString("note"),
                            rs.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return rows;
    }
}
