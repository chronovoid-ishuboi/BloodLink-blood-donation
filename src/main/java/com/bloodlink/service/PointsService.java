package com.bloodlink.service;

import com.bloodlink.dao.PointsDAO;
import com.bloodlink.model.PointTransaction;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Read-side access to a donor's points ledger. Points are only ever awarded
 * as the side effect of a real event -- a verified donation
 * (RequestDAO.finalizeIfBothConfirmed, PointsDAO.DONATION_COMPLETED_POINTS)
 * or being added as a favorite donor (FavoriteDonorService,
 * PointsDAO.FAVORITED_BONUS_POINTS) -- never awarded directly through this
 * service, so there is no "give myself points" method here by design.
 */
public final class PointsService {
    private final PointsDAO pointsDAO = new PointsDAO();

    public int balanceOf(long donorId) {
        try {
            return pointsDAO.balanceOf(donorId);
        } catch (SQLException e) {
            return 0;
        }
    }

    public List<PointTransaction> historyOf(long donorId, int limit) {
        try {
            return pointsDAO.historyOf(donorId, limit);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }
}
