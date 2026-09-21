CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    district VARCHAR(80) NOT NULL,
    address VARCHAR(255) NOT NULL DEFAULT '',
    photo MEDIUMBLOB NULL,
    nid_number VARCHAR(100) NULL,
    guardian_name VARCHAR(120) NULL,
    guardian_phone VARCHAR(30) NULL,
    role ENUM('DONOR','REQUESTER','ADMIN') NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role_state (role, approved, active),
    INDEX idx_users_district (district)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A real position for this user, when one is known. See
-- migration_010_precise_user_location.sql: location_source records how it was
-- obtained (MAP_PIN or IP) because the UI labels pins with it rather than
-- presenting a city-level estimate as if it were exact.
--
-- Declared as ALTERs rather than columns in the CREATE above, deliberately:
-- CREATE TABLE IF NOT EXISTS is skipped outright on a database that already
-- has the table, so columns added there never reach an existing install --
-- exactly the trap that kept these four from arriving the first time. The
-- same reason fk_donor_reference_hospital below is an ALTER. Re-running is
-- safe: DatabaseSetup.applySchema tolerates "duplicate column".
ALTER TABLE users ADD COLUMN latitude DECIMAL(10,7) NULL;
ALTER TABLE users ADD COLUMN longitude DECIMAL(10,7) NULL;
ALTER TABLE users ADD COLUMN location_source VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN location_updated_at TIMESTAMP NULL;
-- How far this user is willing to travel / wants to be alerted within.
-- Drives the radius ring on the map and ranks nearby requests first.
ALTER TABLE users ADD COLUMN travel_radius_km INT NOT NULL DEFAULT 15;
-- Opt-out of being named on the public leaderboard. The rank still counts;
-- only the identity is withheld, so opting out never costs a donor standing.
ALTER TABLE users ADD COLUMN leaderboard_anonymous BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS donor_profiles (
    user_id BIGINT PRIMARY KEY,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    birth_date DATE NOT NULL,
    weight_kg DECIMAL(5,2) NOT NULL,
    last_donation_date DATE NULL,
    availability_status ENUM('AVAILABLE','BUSY','OUT_OF_TOWN','MEDICAL_HOLD') NOT NULL DEFAULT 'BUSY',
    verified_donation_count INT NOT NULL DEFAULT 0,
    reference_hospital_id BIGINT NULL,
    height_cm DECIMAL(5,2) NULL,
    chronic_conditions VARCHAR(500) NOT NULL DEFAULT '',
    recent_surgery BOOLEAN NOT NULL DEFAULT FALSE,
    recent_surgery_details VARCHAR(500) NOT NULL DEFAULT '',
    recent_tattoo BOOLEAN NOT NULL DEFAULT FALSE,
    recent_tattoo_details VARCHAR(500) NOT NULL DEFAULT '',
    current_medications BOOLEAN NOT NULL DEFAULT FALSE,
    current_medications_details VARCHAR(500) NOT NULL DEFAULT '',
    recent_illness BOOLEAN NOT NULL DEFAULT FALSE,
    recent_illness_details VARCHAR(500) NOT NULL DEFAULT '',
    recent_pregnancy BOOLEAN NOT NULL DEFAULT FALSE,
    recent_pregnancy_details VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT chk_donor_weight CHECK (weight_kg BETWEEN 35 AND 250),
    CONSTRAINT chk_verified_donations CHECK (verified_donation_count >= 0),
    CONSTRAINT fk_donor_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_donor_matching (availability_status, blood_group, last_donation_date),
    INDEX idx_donor_donation_count (verified_donation_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Curated hospital directory with real coordinates, used for the searchable
-- hospital picker and for distance-aware donor matching. Must be created
-- before blood_requests, which references it.
CREATE TABLE IF NOT EXISTS hospitals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(180) NOT NULL,
    district VARCHAR(80) NOT NULL,
    area VARCHAR(120) NOT NULL DEFAULT '',
    address VARCHAR(255) NOT NULL DEFAULT '',
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    phone VARCHAR(30) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_hospital_name_district (name, district),
    INDEX idx_hospital_district (district, active),
    INDEX idx_hospital_search (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reward points balance. An ALTER, not a column in the CREATE above, for the
-- same reason as the users location columns: CREATE TABLE IF NOT EXISTS is
-- skipped entirely on a database that already has the table, so this column
-- never arrived on the live database and every points read silently returned
-- zero because the service catches SQLException. Verified by querying it.
ALTER TABLE donor_profiles ADD COLUMN points_balance INT NOT NULL DEFAULT 0;

-- Deferred until here because hospitals must exist first: a donor may
-- optionally choose the hospital nearest to where they actually are as a
-- precise stand-in for their location, instead of the coarser
-- district-level default (see LocationService.java).
ALTER TABLE donor_profiles
    ADD CONSTRAINT fk_donor_reference_hospital FOREIGN KEY (reference_hospital_id)
        REFERENCES hospitals(id) ON DELETE SET NULL;
ALTER TABLE donor_profiles
    ADD INDEX idx_donor_reference_hospital (reference_hospital_id);

CREATE TABLE IF NOT EXISTS blood_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    units_needed INT NOT NULL,
    units_fulfilled INT NOT NULL DEFAULT 0,
    urgency ENUM('NORMAL','URGENT','CRITICAL') NOT NULL,
    hospital_name VARCHAR(180) NOT NULL,
    hospital_id BIGINT NULL,
    district VARCHAR(80) NOT NULL,
    deadline DATE NOT NULL,
    notes TEXT NOT NULL,
    status ENUM('PENDING','MATCHED','ACCEPTED','PARTIALLY_FULFILLED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NOT NULL DEFAULT 'PENDING',
    accepted_donor_id BIGINT NULL,
    donor_confirmed_at TIMESTAMP NULL,
    requester_confirmed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_request_units CHECK (units_needed BETWEEN 1 AND 20),
    CONSTRAINT fk_request_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_donor FOREIGN KEY (accepted_donor_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_request_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id) ON DELETE SET NULL,
    INDEX idx_request_queue (status, urgency, deadline),
    INDEX idx_request_group_district (blood_group, district),
    INDEX idx_request_requester (requester_id, created_at),
    INDEX idx_request_hospital (hospital_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_matches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    donor_id BIGINT NOT NULL,
    match_score DECIMAL(6,2) NOT NULL,
    match_reason VARCHAR(500) NOT NULL,
    status ENUM('NOTIFIED','ACCEPTED','DECLINED','EXPIRED') NOT NULL DEFAULT 'NOTIFIED',
    matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP NULL,
    donor_confirmed_at TIMESTAMP NULL,
    requester_confirmed_at TIMESTAMP NULL,
    CONSTRAINT fk_match_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_request_donor UNIQUE (request_id, donor_id),
    INDEX idx_match_donor_state (donor_id, status, matched_at),
    INDEX idx_match_request_score (request_id, match_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS request_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    from_status ENUM('PENDING','MATCHED','ACCEPTED','PARTIALLY_FULFILLED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NULL,
    to_status ENUM('PENDING','MATCHED','ACCEPTED','PARTIALLY_FULFILLED','DECLINED','FULFILLED','CANCELLED','ESCALATED') NOT NULL,
    changed_by BIGINT NULL,
    note VARCHAR(500) NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_actor FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_history_request_time (request_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(140) NOT NULL,
    message VARCHAR(700) NOT NULL,
    type VARCHAR(40) NOT NULL,
    related_request_id BIGINT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_request FOREIGN KEY (related_request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    INDEX idx_notification_inbox (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS donation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    donor_id BIGINT NOT NULL,
    request_id BIGINT NULL UNIQUE,
    donation_date DATE NOT NULL,
    hospital_name VARCHAR(180) NOT NULL,
    blood_group ENUM('O_NEGATIVE','O_POSITIVE','A_NEGATIVE','A_POSITIVE','B_NEGATIVE','B_POSITIVE','AB_NEGATIVE','AB_POSITIVE') NOT NULL,
    units INT NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_donation_units CHECK (units BETWEEN 1 AND 20),
    CONSTRAINT fk_donation_donor FOREIGN KEY (donor_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_donation_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE SET NULL,
    INDEX idx_donation_donor_date (donor_id, donation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NULL,
    details VARCHAR(700) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_audit_time (created_at),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A review may only exist against a request that reached FULFILLED (both
-- donor and requester confirmed the donation happened) and only once per
-- reviewer per request -- enforced in ReviewService, backstopped here by
-- the unique key.
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewed_id BIGINT NOT NULL,
    rating TINYINT NOT NULL,
    tags VARCHAR(300) NOT NULL DEFAULT '',
    comment VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_request FOREIGN KEY (request_id) REFERENCES blood_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewed FOREIGN KEY (reviewed_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_review_once UNIQUE (request_id, reviewer_id),
    INDEX idx_review_reviewed (reviewed_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- See migration_008_points_vouchers_favorites.sql for the full rationale.
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
-- logo supplied, the UI falls back to a drawn initial-letter badge.
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
    -- Without this the INSERT IGNORE seeding below has nothing to collide
    -- with, so re-applying the schema inserts the whole catalogue a second
    -- time. Titles are unique per partner by design.
    UNIQUE KEY uq_voucher_partner_title (partner_id, title),
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
-- points (see PointsService.FAVORITED_BONUS), enforced once per pair by the
-- unique key so re-favoriting cannot be used to farm points.
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

-- Direct messages, scoped to a request both parties belong to. See
-- migration_009_messages.sql for why bodies live here and not in the push
-- layer.
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

-- Real, sourced coordinates -- see
-- migration_002_hospitals_and_distance.sql for citations. The first four
-- names match this project's existing demo blood_requests data exactly.
-- INSERT IGNORE + the unique key above keep this block safe to run more
-- than once (schema.sql is not otherwise guarded against re-execution).
INSERT IGNORE INTO hospitals (name, district, area, address, latitude, longitude, phone, active) VALUES
('Dhaka Medical College Hospital', 'Dhaka', 'Shahbagh', 'Secretariat Road, Shahbagh, Dhaka', 23.7257000, 90.3971000, '', TRUE),
('Square Hospital', 'Dhaka', 'West Panthapath', '18/F Bir Uttam Qazi Nuruzzaman Sarak, West Panthapath, Dhaka 1205', 23.7519000, 90.3855000, '', TRUE),
('United Hospital', 'Dhaka', 'Gulshan-2', 'Plot 15, Road 71, Gulshan-2, Dhaka', 23.8046000, 90.4158000, '', TRUE),
('Evercare Hospital Dhaka', 'Dhaka', 'Bashundhara R/A', 'Plot 81, Block E, Bashundhara R/A, Dhaka', 23.8108000, 90.4319000, '+880 9666-710678', TRUE),
('Shaheed Suhrawardy Medical College Hospital', 'Dhaka', 'Sher-e-Bangla Nagar', 'Sher-e-Bangla Nagar, Dhaka', 23.7685000, 90.3717000, '', TRUE),
('Chittagong Medical College Hospital', 'Chittagong', 'Chandanpura', 'K.B. Fazlul Kader Road, Chittagong', 22.3593000, 91.8307000, '', TRUE),
('Sylhet MAG Osmani Medical College Hospital', 'Sylhet', 'Sylhet Sadar', 'Sylhet Sadar, Sylhet', 24.9022000, 91.8535000, '', TRUE),
('Rajshahi Medical College Hospital', 'Rajshahi', 'Rajshahi Sadar', 'Laxmipur, Rajshahi', 24.3723000, 88.5857000, '', TRUE),
('Khulna Medical College Hospital', 'Khulna', 'Choto Boyra', 'Choto Boyra, Khulna', 22.8291000, 89.5370000, '', TRUE),
('Rangpur Medical College Hospital', 'Rangpur', 'Dhap', 'Dhap, Rangpur', 25.7667000, 89.2342000, '', TRUE),
('Sher-e-Bangla Medical College Hospital', 'Barisal', 'Barisal Sadar', 'Kirtonkhola River Bank, Barisal', 22.6880000, 90.3610000, '', TRUE),
('Mymensingh Medical College Hospital', 'Mymensingh', 'Charpara', 'Medical College Road, Charpara, Mymensingh', 24.7416000, 90.4093000, '', TRUE),
('Cumilla Medical College Hospital', 'Cumilla', 'Kuchaitoli', 'Dr Akhtar Hameed Khan Road, Kuchaitoli, Cumilla', 23.4511000, 91.2022000, '081-65401', TRUE),
('Shaheed Ziaur Rahman Medical College Hospital', 'Bogura', 'Silimpur', 'Bogura City Bypass, Silimpur, Bogura', 24.8280000, 89.3531000, '', TRUE),
('Shaheed M. Monsur Ali Medical College Hospital', 'Sirajganj', 'Sirajganj Sadar', 'Sirajganj Sadar, Sirajganj', 24.4488000, 89.6738000, '', TRUE),
('Dinajpur Medical College Hospital', 'Dinajpur', 'Dinajpur Sadar', 'Dinajpur Sadar, Dinajpur', 25.6106000, 88.6551000, '', TRUE),
('Cox''s Bazar Medical College Hospital', 'Cox''s Bazar', 'Jhilongja', 'Jhilongja, Cox''s Bazar', 21.4206000, 92.0149000, '', TRUE),
('Jashore Medical College Hospital', 'Jashore', 'Chanchra', 'Horinar Beel, Chanchra, Jashore', 23.1690000, 89.2090000, '', TRUE),
('Pabna General Hospital', 'Pabna', 'Hemayetpur', 'Hemayetpur, Pabna', 24.0046000, 89.2090000, '', TRUE),
('Noakhali Medical College Hospital', 'Noakhali', 'Chowmuhoni', 'Begumganj, Chowmuhoni, Noakhali', 22.9510000, 91.1040000, '', TRUE),
('Kushtia Medical College Hospital', 'Kushtia', 'Kushtia Sadar', 'Kushtia-Dhaka Highway, Kushtia', 23.9009000, 89.1233000, '', TRUE),
('Kurmitola General Hospital', 'Dhaka', 'Kurmitola', 'Kurmitola, Dhaka-1206', 23.8194400, 90.4092900, '', TRUE),
('Shaheed Ahsan Ullah Master General Hospital', 'Gazipur', 'Tongi', 'Station Road, Tongi, Gazipur', 23.8934000, 90.4022000, '', TRUE),
('Narayanganj 300 Bed Hospital', 'Narayanganj', 'Khanpur', 'Khanpur, Narayanganj Sadar, Narayanganj', 23.6264000, 90.5062000, '', TRUE);

-- ============================================================================
-- Medical documents supplied at donor registration.
--
-- A donor attaches at least one prescription or report when they sign up, and
-- an admin reads it before approving the account. That is the whole point of
-- the table: it is evidence for a human decision, not something the app
-- interprets.
--
-- A new table, so CREATE TABLE IF NOT EXISTS is the right form here -- unlike
-- adding a column, which this file learned the hard way must be an ALTER,
-- because IF NOT EXISTS skips the whole statement for a table that already
-- exists and the column silently never arrives.
--
-- The file is stored in the row rather than on disk. This app has no file
-- server and runs from several machines against one cloud database; a path
-- would resolve on the uploader's machine and nowhere else. MEDIUMBLOB holds
-- 16MB and the upload is capped well below that.
--
-- ON DELETE CASCADE: a medical report is the most sensitive thing here, so it
-- must not outlive the account it belongs to.
-- ============================================================================
CREATE TABLE IF NOT EXISTS medical_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT '',
    size_bytes INT NOT NULL DEFAULT 0,
    content MEDIUMBLOB NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_medical_document_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_medical_document_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- Reviews: one per donor per request, not one per request.
--
-- uq_review_once was (request_id, reviewer_id), which came from the model
-- where a request had exactly one donor. A request can now be filled by
-- several donors, each with their own confirmed handshake, so that key let a
-- requester rate the first donor and then silently refused the second --
-- reported back as "You already reviewed this donation."
--
-- ALTER, not a change inside CREATE TABLE IF NOT EXISTS: that form is skipped
-- entirely for a table that already exists, so the constraint would never have
-- changed on any live database.
--
-- The new key is added BEFORE the old one is dropped, and that order is not
-- cosmetic. InnoDB requires an index whose leading column is the foreign key
-- column, and uq_review_once (request_id, reviewer_id) was the only thing
-- satisfying that for fk_review_request. Dropping it first fails outright with
-- "needed in a foreign key constraint". The new key also leads with
-- request_id, so once it exists the old one is free to go.
-- ----------------------------------------------------------------------------
ALTER TABLE reviews ADD CONSTRAINT uq_review_once_per_donor UNIQUE (request_id, reviewer_id, reviewed_id);
ALTER TABLE reviews DROP INDEX uq_review_once;
