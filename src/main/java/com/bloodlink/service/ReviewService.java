package com.bloodlink.service;

import com.bloodlink.dao.AuditDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.dao.ReviewDAO;
import com.bloodlink.model.*;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Ratings, which in this app run one way only: a requester rates the donor who
 * turned up for them.
 *
 * <h2>Why donors do not rate requesters</h2>
 * Reputation here exists to answer one question -- is this donor someone who
 * actually shows up. That is a real question, it is asked repeatedly about the
 * same donor, and a score helps answer it. There is no equivalent question
 * about a requester: they are a person in an emergency, they are usually in
 * the system once, and a low score would do nothing except follow someone
 * through the worst week of their life. A rating nobody acts on is not a
 * neutral feature, because the person being scored still sees it.
 * <p>
 * This is enforced here and not just by removing the button, since the score
 * is a public claim about a named person.
 *
 * <h2>The other conditions</h2>
 * A review may only be submitted about a request that has reached
 * {@link RequestStatus#FULFILLED} -- both sides confirmed the donation
 * happened via {@link RequestService#confirmDonated} /
 * {@link RequestService#confirmReceived} -- by the requester on that request,
 * and only once. A reputation claim must be backed by a real, verified
 * interaction.
 */
public final class ReviewService {
    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final AuthorizationService authorizationService = new AuthorizationService();

    public ServiceResult<Void> submit(long requestId, long reviewerId, long donorId,
                                      int rating, List<ReviewTag> tags, String comment) {
        if (rating < 1 || rating > 5) return ServiceResult.failure("Rating must be between 1 and 5.");
        try {
            authorizationService.requireSelfOrAdmin(reviewerId);
            BloodRequest request = requestDAO.findById(requestId).orElse(null);
            if (request == null) return ServiceResult.failure("Request not found.");
            if (request.status() != RequestStatus.FULFILLED)
                return ServiceResult.failure("This request is not a verified completed donation yet.");

            if (request.requesterId() != reviewerId) {
                boolean donorOnThisRequest = requestDAO.findMatchesForRequest(requestId).stream()
                        .anyMatch(candidate -> candidate.donorId() == reviewerId);
                return ServiceResult.failure(donorOnThisRequest
                        ? "BloodLink does not rate requesters. Ratings are for donors only."
                        : "You were not a party to this request.");
            }

            // The donor is resolved from request_matches, not from
            // blood_requests.accepted_donor_id.
            //
            // That column is a leftover from the single-donor model and
            // nothing has written to it since the multi-donor migration, so it
            // is null on every request created since. This method used to read
            // it, which meant resolveReviewedParty always returned null and
            // *no requester could rate any donor at all* -- the whole
            // reputation system was dead and said only "You were not a party
            // to this request". Found by the end-to-end simulation; it is not
            // visible from any single screen.
            Long reviewedId = requestDAO.findMatchesForRequest(requestId).stream()
                    .filter(candidate -> candidate.donorId() == donorId)
                    .filter(candidate -> candidate.matchStatus() == MatchStatus.ACCEPTED
                            && candidate.donorConfirmed() && candidate.requesterConfirmed())
                    .map(MatchCandidate::donorId)
                    .findFirst()
                    .orElse(null);
            if (reviewedId == null)
                return ServiceResult.failure("That donor did not complete a confirmed donation for this request.");

            if (reviewDAO.hasReviewed(requestId, reviewerId, reviewedId))
                return ServiceResult.failure("You already reviewed this donation.");

            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    reviewDAO.create(connection, requestId, reviewerId, reviewedId, rating, tags, comment);
                    new AuditDAO().log(connection, reviewerId, "SUBMIT_REVIEW", "USER", reviewedId,
                            "Rated " + rating + "/5 for request #" + requestId);
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
            return ServiceResult.success("Review submitted. Thank you for keeping BloodLink trustworthy.", null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }


    /** Whether this reviewer already rated this particular donor on this request. */
    public boolean hasReviewed(long requestId, long reviewerId, long donorId) {
        try {
            return reviewDAO.hasReviewed(requestId, reviewerId, donorId);
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean hasReviewed(long requestId, long reviewerId) {
        try {
            return reviewDAO.hasReviewed(requestId, reviewerId);
        } catch (SQLException e) {
            return false;
        }
    }

    public ReputationSummary reputationOf(long userId) {
        try {
            return reviewDAO.reputationOf(userId);
        } catch (SQLException e) {
            return ReputationSummary.none(userId);
        }
    }

    public List<Review> reviewsReceivedBy(long userId, int limit) throws SQLException {
        return reviewDAO.findReceivedBy(userId, limit);
    }

    public java.util.Set<Long> reviewedRequestIdsBy(long reviewerId) {
        try {
            return reviewDAO.reviewedRequestIdsBy(reviewerId);
        } catch (SQLException e) {
            return java.util.Collections.emptySet();
        }
    }
}
