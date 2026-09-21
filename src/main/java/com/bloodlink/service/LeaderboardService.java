package com.bloodlink.service;

import com.bloodlink.dao.LeaderboardDAO;
import com.bloodlink.model.LeaderboardEntry;
import com.bloodlink.util.SessionManager;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class LeaderboardService {

    private final LeaderboardDAO leaderboardDAO = new LeaderboardDAO();

    public List<LeaderboardEntry> topDonors(int limit) {
        try {
            long viewerId = viewerId();
            return leaderboardDAO.topDonors(limit, viewerId);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    /** The viewer own row, for pinning below the list when they are outside the top slice. */
    public Optional<com.bloodlink.model.LeaderboardEntry> myEntry() {
        try {
            long viewerId = viewerId();
            return viewerId < 0 ? Optional.empty() : leaderboardDAO.entryFor(viewerId);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> myRank() {
        try {
            long viewerId = viewerId();
            return viewerId < 0 ? Optional.empty() : leaderboardDAO.rankOf(viewerId);
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    public boolean isAnonymous(long userId) {
        try {
            return leaderboardDAO.isAnonymous(userId);
        } catch (SQLException e) {
            return false;
        }
    }

    /** Only ever changes the caller's own setting -- the id comes from the session, not the caller. */
    public ServiceResult<Void> setAnonymous(boolean anonymous) {
        long viewerId = viewerId();
        if (viewerId < 0) return ServiceResult.failure("Unauthorized: no active session.");
        try {
            leaderboardDAO.setAnonymous(viewerId, anonymous);
            return ServiceResult.success(anonymous
                    ? "You now appear as \"Anonymous donor\" on the leaderboard. Your rank is unchanged."
                    : "Your name is shown on the leaderboard again.", null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    private long viewerId() {
        var user = SessionManager.getInstance().getCurrentUser();
        return user == null ? -1 : user.getId();
    }
}
