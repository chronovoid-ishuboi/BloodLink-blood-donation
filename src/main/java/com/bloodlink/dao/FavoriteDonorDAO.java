package com.bloodlink.dao;

import com.bloodlink.model.AvailabilityStatus;
import com.bloodlink.model.BadgeTier;
import com.bloodlink.model.BloodGroup;
import com.bloodlink.model.FavoriteDonorView;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class FavoriteDonorDAO {

    public boolean isFavorite(long requesterId, long donorId) throws SQLException {
        String sql = "SELECT 1 FROM favorite_donors WHERE requester_id=? AND donor_id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requesterId);
            statement.setLong(2, donorId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }

    /** Returns false without inserting a duplicate row if this pair is already favorited -- the unique key would reject it anyway, this avoids the exception path. */
    public boolean add(Connection connection, long requesterId, long donorId) throws SQLException {
        if (isFavoriteOnConnection(connection, requesterId, donorId)) return false;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO favorite_donors(requester_id, donor_id) VALUES (?,?)")) {
            statement.setLong(1, requesterId);
            statement.setLong(2, donorId);
            statement.executeUpdate();
        }
        return true;
    }

    public void remove(long requesterId, long donorId) throws SQLException {
        String sql = "DELETE FROM favorite_donors WHERE requester_id=? AND donor_id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requesterId);
            statement.setLong(2, donorId);
            statement.executeUpdate();
        }
    }

    public List<FavoriteDonorView> findFor(long requesterId) throws SQLException {
        String sql = """
                SELECT f.id favorite_id, f.donor_id, u.full_name, d.blood_group, u.district, u.phone,
                       d.availability_status, d.verified_donation_count, f.created_at
                FROM favorite_donors f
                JOIN users u ON u.id = f.donor_id
                JOIN donor_profiles d ON d.user_id = f.donor_id
                WHERE f.requester_id = ?
                ORDER BY f.created_at DESC
                """;
        List<FavoriteDonorView> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requesterId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new FavoriteDonorView(rs.getLong("favorite_id"), rs.getLong("donor_id"), rs.getString("full_name"),
                            BloodGroup.valueOf(rs.getString("blood_group")), rs.getString("district"), rs.getString("phone"),
                            AvailabilityStatus.valueOf(rs.getString("availability_status")),
                            BadgeTier.fromDonationCount(rs.getInt("verified_donation_count")),
                            rs.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return rows;
    }

    private boolean isFavoriteOnConnection(Connection connection, long requesterId, long donorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM favorite_donors WHERE requester_id=? AND donor_id=?")) {
            statement.setLong(1, requesterId);
            statement.setLong(2, donorId);
            try (ResultSet rs = statement.executeQuery()) { return rs.next(); }
        }
    }
}
