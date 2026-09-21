-- Migration 008: donor points/vouchers and requester favorite donors.
--
-- points_balance lives directly on donor_profiles (a running total, like
-- verified_donation_count already does) rather than being computed from
-- point_transactions on every read; point_transactions is the audit ledger
-- that balance is derived from, in the same spirit as request_status_history
-- backstopping blood_requests.status.
--
-- Like every other migration_0XX file in this project, this is a historical
-- record of the change -- DatabaseSetup only ever reads schema.sql at
-- runtime, so this DDL is also folded directly into schema.sql.

ALTER TABLE donor_profiles
    ADD COLUMN points_balance INT NOT NULL DEFAULT 0 AFTER verified_donation_count;
ALTER TABLE donor_profiles
    ADD CONSTRAINT chk_points_balance_nonnegative CHECK (points_balance >= 0);

CREATE TABLE IF NOT EXISTS point_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(60) NOT NULL,
    related_request_id BIGINT NULL,
    note VARCHAR(300) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_point_tx_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_point_tx_request FOREIGN KEY (related_request_id) REFERENCES blood_requests(id) ON DELETE SET NULL,
    INDEX idx_point_tx_donor_time (donor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- logo_path is a relative resource/file path, deliberately nullable: with no
-- logo supplied, the UI falls back to a drawn initial-letter badge rather
-- than a broken image. See util.LogoManager for the equivalent pattern
-- already used for the BloodLink brand mark itself.
CREATE TABLE IF NOT EXISTS voucher_partners (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL DEFAULT '',
    logo_path VARCHAR(255) NULL,
    description VARCHAR(300) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_voucher_partner_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vouchers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(300) NOT NULL DEFAULT '',
    points_cost INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_voucher_cost CHECK (points_cost > 0),
    CONSTRAINT fk_voucher_partner FOREIGN KEY (partner_id) REFERENCES voucher_partners(id) ON DELETE CASCADE,
    INDEX idx_voucher_partner_active (partner_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS voucher_redemptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    points_spent INT NOT NULL,
    redemption_code VARCHAR(24) NOT NULL UNIQUE,
    redeemed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_redemption_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_redemption_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers(id) ON DELETE RESTRICT,
    INDEX idx_redemption_donor_time (donor_id, redeemed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A requester's saved donors. Favoriting a donor also awards them bonus
-- points (see PointsService.FAVORITED_BONUS) -- enforced once per pair by
-- the unique key, so re-favoriting an already-favorited donor cannot be used
-- to farm points.
CREATE TABLE IF NOT EXISTS favorite_donors (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    donor_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_favorite_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_favorite_pair UNIQUE (requester_id, donor_id),
    INDEX idx_favorite_requester (requester_id),
    INDEX idx_favorite_donor (donor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Starter partner/voucher catalog. logo_path is left NULL for every row --
-- see voucher_partners.logo_path -- so the UI's drawn-initial fallback is
-- what a fresh install shows until real logo images are supplied.
INSERT IGNORE INTO voucher_partners (name, category, description) VALUES
('Teer', 'Retail', 'Fashion and lifestyle retail chain.'),
('PRAN', 'Food & Beverage', 'Snacks, beverages and grocery products.'),
('Aftab', 'Automobiles', 'Automobiles and motorcycle dealership group.'),
('Foodpanda', 'Food Delivery', 'On-demand food delivery service.'),
('BD Travels', 'Travel', 'Domestic and international travel bookings.');

INSERT IGNORE INTO vouchers (partner_id, title, description, points_cost)
SELECT id, 'Tk 200 shopping voucher', 'Redeemable in-store or online.', 400 FROM voucher_partners WHERE name = 'Teer'
UNION ALL SELECT id, 'Tk 500 shopping voucher', 'Redeemable in-store or online.', 900 FROM voucher_partners WHERE name = 'Teer'
UNION ALL SELECT id, 'PRAN snack hamper', 'A hamper of assorted PRAN snacks and drinks.', 300 FROM voucher_partners WHERE name = 'PRAN'
UNION ALL SELECT id, 'Free vehicle service checkup', 'One complimentary service checkup.', 600 FROM voucher_partners WHERE name = 'Aftab'
UNION ALL SELECT id, 'Tk 150 delivery discount', 'Off your next Foodpanda order.', 200 FROM voucher_partners WHERE name = 'Foodpanda'
UNION ALL SELECT id, 'Tk 1000 travel discount', 'Off your next booking with BD Travels.', 1200 FROM voucher_partners WHERE name = 'BD Travels';
