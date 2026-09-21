package com.bloodlink.dao;

import com.bloodlink.model.BadgeTier;
import com.bloodlink.model.BloodGroup;
import com.bloodlink.model.LeaderboardEntry;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ranks donors by verified donations.
 * <p>
 * Only donations both sides confirmed count, because that is the only thing
 * this app treats as a donation anywhere else -- a leaderboard built on
 * self-reported numbers would reward claiming rather than giving.
 * <p>
 * A donor who has opted out of being named is still ranked; only their name
 * is withheld. Anonymity should never cost someone their standing, and the
 * substitution happens here rather than in the UI so no rendering path can
 * leak the name by forgetting to check the flag.
 */
public final class LeaderboardDAO {

    private static final String ANONYMOUS_NAME = "Anonymous donor";

    /**
     * Counted from donation_history rather than donor_profiles'
     * verified_donation_count.
     * <p>
     * The two disagree: measured against the live database, four of eight
     * donors had a counter that did not match their actual records, some
     * inflated by three. donation_history is the source of truth -- it is
     * what the donor's own impact panel and profile timeline show -- so
     * ranking on the counter would publish a table contradicting the numbers
     * those same donors see on their own screens. Counting directly is also
     * self-healing: it cannot drift again.
     */
    public List<LeaderboardEntry> topDonors(int limit, long viewerId) throws SQLException {
        String sql = """
                SELECT u.id, u.full_name, u.district, u.leaderboard_anonymous,
                       d.blood_group, d.points_balance,
                       (SELECT COUNT(*) FROM donation_history h
                         WHERE h.donor_id = u.id AND h.verified = TRUE) AS donations
                FROM donor_profiles d
                JOIN users u ON u.id = d.user_id
                WHERE u.active = TRUE
                HAVING donations > 0
                ORDER BY donations DESC, d.points_balance DESC, u.full_name ASC
                LIMIT ?
                """;
        List<LeaderboardEntry> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                int rank = 0;
                while (rs.next()) {
                    rank++;
                    long donorId = rs.getLong("id");
                    boolean anonymous = rs.getBoolean("leaderboard_anonymous");
                    boolean isViewer = donorId == viewerId;
                    // The viewer always sees their own real name, even when
                    // anonymous to everyone else -- otherwise they cannot find
                    // themselves on their own leaderboard.
                    String name = anonymous && !isViewer ? ANONYMOUS_NAME : rs.getString("full_name");
                    rows.add(new LeaderboardEntry(rank, donorId, name, anonymous,
                            anonymous && !isViewer ? "" : rs.getString("district"),
                            BloodGroup.valueOf(rs.getString("blood_group")),
                            BadgeTier.fromDonationCount(rs.getInt("donations")),
                            rs.getInt("donations"), rs.getInt("points_balance"), isViewer));
                }
            }
        }
        return rows;
    }

    /**
     * Where this donor sits overall, even when they are outside the visible
     * top slice -- so the board says "you are 142nd" rather than leaving
     * someone who has donated unable to find themselves at all.
     * Empty when they have no verified donations yet.
     */
    public java.util.Optional<Integer> rankOf(long donorId) throws SQLException {
        String sql = """
                SELECT COUNT(*) + 1 AS position FROM (
                    SELECT u.id, (SELECT COUNT(*) FROM donation_history h
                                   WHERE h.donor_id = u.id AND h.verified = TRUE) AS donations
                    FROM donor_profiles d JOIN users u ON u.id = d.user_id
                    WHERE u.active = TRUE
                ) ranked
                WHERE ranked.donations > (SELECT COUNT(*) FROM donation_history h
                                           WHERE h.donor_id = ? AND h.verified = TRUE)
                """;
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return java.util.Optional.empty();
                return java.util.Optional.of(rs.getInt("position"));
            }
        } catch (SQLException e) {
            // A donor with no profile row at all (a requester viewing the
            // board) has no rank rather than being an error.
            return java.util.Optional.empty();
        }
    }

    /**
     * The viewer's own row with its true overall rank, for when they fall
     * outside the visible top slice.
     * <p>
     * Someone ranked 100,000th still needs to see where they stand; showing
     * only the top fifty tells the vast majority of donors nothing about
     * themselves. Empty when they have no verified donations, or are not a
     * donor at all.
     */
    public java.util.Optional<LeaderboardEntry> entryFor(long donorId) throws SQLException {
        String sql = """
                SELECT u.id, u.full_name, u.district, u.leaderboard_anonymous,
                       d.blood_group, d.points_balance,
                       (SELECT COUNT(*) FROM donation_history h
                         WHERE h.donor_id = u.id AND h.verified = TRUE) AS donations
                FROM donor_profiles d
                JOIN users u ON u.id = d.user_id
                WHERE u.id = ?
                """;
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return java.util.Optional.empty();
                int donations = rs.getInt("donations");
                if (donations == 0) return java.util.Optional.empty();
                int rank = rankOf(donorId).orElse(0);
                return java.util.Optional.of(new LeaderboardEntry(rank, donorId,
                        rs.getString("full_name"), rs.getBoolean("leaderboard_anonymous"),
                        rs.getString("district"), BloodGroup.valueOf(rs.getString("blood_group")),
                        BadgeTier.fromDonationCount(donations), donations,
                        rs.getInt("points_balance"), true));
            }
        }
    }

    public boolean isAnonymous(long userId) throws SQLException {
        String sql = "SELECT leaderboard_anonymous FROM users WHERE id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean("leaderboard_anonymous");
            }
        }
    }

    public void setAnonymous(long userId, boolean anonymous) throws SQLException {
        String sql = "UPDATE users SET leaderboard_anonymous=? WHERE id=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, anonymous);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }
}
