package com.bloodlink.model;

import java.time.LocalDateTime;

/** senderName is carried denormalized from a join so a chat bubble never needs a second query. */
public record ChatMessage(long id, long requestId, long senderId, String senderName, long recipientId,
                           String body, LocalDateTime sentAt, LocalDateTime readAt) {

    public boolean isRead() { return readAt != null; }
}
