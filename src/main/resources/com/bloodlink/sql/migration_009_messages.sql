-- Migration 009: direct messages between a requester and a matched donor.
--
-- Scoped to a request rather than being a free-form inbox: a conversation
-- only exists in the context of a specific blood request the two people are
-- both party to, which is also what ChatService checks before letting either
-- side read or write. That keeps the app from becoming a general messaging
-- service where any user can contact any other.
--
-- Message bodies live here, not in the push layer -- PushServer deliberately
-- holds no state and never touches the database, so it only ever carries a
-- "you have a new message" nudge and the recipient reloads from here.
--
-- Like every other migration_0XX file, this is the historical record;
-- DatabaseSetup only reads schema.sql, so this DDL is folded in there too.

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    CONSTRAINT fk_message_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_message_thread (request_id, sent_at),
    INDEX idx_message_unread (recipient_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
