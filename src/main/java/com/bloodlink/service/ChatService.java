package com.bloodlink.service;

import com.bloodlink.dao.MessageDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.BloodRequest;
import com.bloodlink.model.ChatMessage;
import com.bloodlink.model.User;
import com.bloodlink.util.PushClient;
import com.bloodlink.util.SessionManager;

import java.sql.SQLException;
import java.util.List;

/**
 * Messaging between the two people on one blood request. Deliberately not a
 * general inbox: every read and write is checked against the request itself,
 * so a conversation can only exist between that request's requester and a
 * donor who is actually matched to it. Without that check this would be a
 * way for any account to message any other account, using a request id it
 * had nothing to do with as the excuse.
 * <p>
 * Delivery is database-first: the message is persisted here, then
 * {@link PushClient#ping} nudges the recipient to reload. If the push server
 * is down the message is still saved and still appears when the recipient
 * next opens the conversation -- the nudge only affects how quickly, never
 * whether.
 */
public final class ChatService {
    private final MessageDAO messageDAO = new MessageDAO();
    private final RequestDAO requestDAO = new RequestDAO();

    public ServiceResult<List<ChatMessage>> thread(long requestId, long otherUserId) {
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null) return ServiceResult.failure("Unauthorized: no active session.");
        try {
            if (!canConverse(requestId, current.getId(), otherUserId))
                return ServiceResult.failure("You are not part of this conversation.");
            List<ChatMessage> messages = messageDAO.findThread(requestId, current.getId(), otherUserId);
            messageDAO.markThreadRead(requestId, current.getId(), otherUserId);
            return ServiceResult.success("Loaded.", messages);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    public ServiceResult<Void> send(long requestId, long recipientId, String body) {
        User current = SessionManager.getInstance().getCurrentUser();
        if (current == null) return ServiceResult.failure("Unauthorized: no active session.");
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) return ServiceResult.failure("Message is empty.");
        if (trimmed.length() > 2000) return ServiceResult.failure("Message is too long (2000 characters max).");
        try {
            if (!canConverse(requestId, current.getId(), recipientId))
                return ServiceResult.failure("You are not part of this conversation.");
            messageDAO.send(requestId, current.getId(), recipientId, trimmed);
            PushClient.getInstance().pingChat(recipientId);
            return ServiceResult.success("Sent.", null);
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    public long unreadCount(long userId) {
        try {
            return messageDAO.unreadCount(userId);
        } catch (SQLException e) {
            return 0;
        }
    }

    /** True only when one of the pair is the request's requester and the other is a donor matched to it. */
    private boolean canConverse(long requestId, long userA, long userB) throws SQLException {
        BloodRequest request = requestDAO.findById(requestId).orElse(null);
        if (request == null) return false;
        java.util.Set<Long> matchedDonors = requestDAO.findMatchedDonorIds(requestId);
        long requesterId = request.requesterId();
        if (userA == requesterId) return matchedDonors.contains(userB);
        if (userB == requesterId) return matchedDonors.contains(userA);
        return false;
    }
}
