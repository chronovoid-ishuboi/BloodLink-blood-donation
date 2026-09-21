package com.bloodlink.service;

import com.bloodlink.dao.AuditDAO;
import com.bloodlink.dao.FavoriteDonorDAO;
import com.bloodlink.dao.PointsDAO;
import com.bloodlink.model.FavoriteDonorView;
import com.bloodlink.model.Role;
import com.bloodlink.model.User;
import com.bloodlink.util.DBConnection;
import com.bloodlink.util.SessionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * A requester's saved donors. Adding a favorite also awards the donor
 * {@link PointsDAO#FAVORITED_BONUS_POINTS} -- once per requester/donor pair,
 * enforced by favorite_donors' unique key rather than trusted to this
 * service alone, so retrying an add that already exists is a harmless no-op
 * rather than a way to farm points.
 */
public final class FavoriteDonorService {
    private final FavoriteDonorDAO favoriteDonorDAO = new FavoriteDonorDAO();
    private final PointsDAO pointsDAO = new PointsDAO();

    public ServiceResult<Void> addFavorite(long donorId) {
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null) return ServiceResult.failure("Unauthorized: no active session.");
        if (current.getRole() != Role.REQUESTER) return ServiceResult.failure("Only requesters can save favorite donors.");
        long requesterId = current.getId();
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean added = favoriteDonorDAO.add(connection, requesterId, donorId);
                if (!added) {
                    connection.rollback();
                    return ServiceResult.success("Already in your favorites.", null);
                }
                pointsDAO.award(connection, donorId, PointsDAO.FAVORITED_BONUS_POINTS, "FAVORITED_BY_REQUESTER",
                        null, "Added as a favorite donor by requester #" + requesterId);
                new AuditDAO().log(connection, requesterId, "ADD_FAVORITE_DONOR", "USER", donorId,
                        "Favorited donor, awarded " + PointsDAO.FAVORITED_BONUS_POINTS + " bonus points");
                connection.commit();
                return ServiceResult.success("Added to favorites. This donor earned a bonus for being trusted!", null);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    public ServiceResult<Void> removeFavorite(long donorId) {
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null) return ServiceResult.failure("Unauthorized: no active session.");
        try {
            favoriteDonorDAO.remove(current.getId(), donorId);
            return ServiceResult.success("Removed from favorites.", null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    public boolean isFavorite(long requesterId, long donorId) {
        try {
            return favoriteDonorDAO.isFavorite(requesterId, donorId);
        } catch (SQLException e) {
            return false;
        }
    }

    public List<FavoriteDonorView> favoritesOf(long requesterId) {
        try {
            return favoriteDonorDAO.findFor(requesterId);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }
}
