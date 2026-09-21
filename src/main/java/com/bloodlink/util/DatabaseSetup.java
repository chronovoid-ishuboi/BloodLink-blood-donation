package com.bloodlink.util;

import com.bloodlink.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

public final class DatabaseSetup {
    private static final String SCHEMA_RESOURCE = "/com/bloodlink/sql/schema.sql";

    private DatabaseSetup() { }

    public static void main(String[] args) {
        try {
            setup();
            System.out.println("BloodLink database setup completed successfully.");
            System.out.println("Admin: admin@bloodlink.local / Admin@123");
            System.out.println("Donor: donor.opos@bloodlink.local / Donor@123");
            System.out.println("Requester: requester@bloodlink.local / Request@123");
        } catch (Exception e) {
            System.err.println("Database setup failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Brings the database up to the current schema on every start, not just
     * on a first install.
     * <p>
     * This used to run the schema only when the {@code users} table was
     * missing and otherwise skip straight to seeding. That meant a schema
     * change -- a new table, a new column -- reached a fresh install but
     * never an existing database, and the app would then fail every query
     * against the new tables while looking like it had started fine. Because
     * {@link #applySchema} now tolerates "already exists" per statement, it
     * is safe to run every time, which is what actually makes new tables
     * arrive on an existing install.
     */
    public static synchronized void ensureInitialized() {
        try {
            if (!tablesExist()) {
                setup();
            } else if (schemaNeedsApplying()) {
                applySchema();
                repairExistingInstall();
                recordSchemaVersion();
                seedDemoData();
            }
        } catch (Exception e) {
            System.err.println("Warning: Database auto-initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean tablesExist() {
        try (Connection conn = DBConnection.getRawConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM users LIMIT 1")) {
            try (ResultSet rs = stmt.executeQuery()) {
                return true;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public static void setup() throws IOException, SQLException {
        try (Connection test = DBConnection.getRawConnection()) { }
        ensureDatabaseExists();
        applySchema();
        seedDemoData();
    }

    private static void ensureDatabaseExists() throws SQLException {
        String dbUrl = DBConnection.getActiveUrl();
        if (dbUrl != null && dbUrl.startsWith("jdbc:h2:")) {
            // H2 automatically creates the database file; no CREATE DATABASE needed.
            return;
        }
        try {
            JdbcTarget target = JdbcTarget.parse(dbUrl);
            try (Connection connection = DriverManager.getConnection(
                    target.serverUrl(), AppConfig.get("db.username"), AppConfig.get("db.password"));
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + target.databaseName()
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
        } catch (Exception e) {
            System.err.println("Could not ensure MySQL database exists: " + e.getMessage());
        }
    }

    /**
     * Applies schema.sql statement by statement, skipping the ones that fail
     * only because what they create is already there.
     * <p>
     * schema.sql is written as a full description of the finished schema, and
     * most of it is already idempotent ({@code CREATE TABLE IF NOT EXISTS},
     * {@code INSERT IGNORE}). The exceptions are the bare
     * {@code ALTER TABLE ... ADD CONSTRAINT/COLUMN/INDEX} statements, which
     * have no IF NOT EXISTS form in MySQL and abort the whole run on a
     * database where they have already been applied. Tolerating exactly those
     * "already exists" failures -- and no others -- is what lets this run on
     * every start, which is how schema changes reach an existing install at
     * all. A genuine error (bad SQL, missing table, permission) still throws.
     */
    private static void applySchema() throws IOException, SQLException {
        String script = readResource(SCHEMA_RESOURCE);
        List<String> statements = splitStatements(script);
        String dbUrl = DBConnection.getActiveUrl();
        boolean isH2 = dbUrl != null && dbUrl.startsWith("jdbc:h2:");
        try (Connection connection = DBConnection.getRawConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                if (isH2) {
                    sql = sql.replaceAll("(?i)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", "");
                }
                if (sql.isBlank()) continue;
                try {
                    statement.execute(sql);
                } catch (SQLException e) {
                    if (!isAlreadyApplied(e)) throw e;
                }
            }
        }
    }

    /**
     * Whether schema.sql still has anything to do on this database.
     * <p>
     * Applying it unconditionally on every start was correct but slow: it is
     * dozens of statements against a cloud database, several seconds added to
     * every single launch before the login screen could be used. The content
     * of schema.sql is hashed and the hash stored in the database, so the
     * work happens exactly once after the schema actually changes and is
     * skipped on every launch after that. Failing open (returning true) is
     * deliberate -- a missed migration is a far worse outcome than a slow
     * start.
     */
    private static boolean schemaNeedsApplying() {
        try {
            String current = schemaFingerprint();
            if (current == null) return true;
            try (Connection connection = DBConnection.getRawConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS schema_state (
                            id INT PRIMARY KEY,
                            fingerprint VARCHAR(64) NOT NULL,
                            applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        )""");
                try (ResultSet rs = statement.executeQuery("SELECT fingerprint FROM schema_state WHERE id = 1")) {
                    return !rs.next() || !current.equals(rs.getString(1));
                }
            }
        } catch (Exception e) {
            return true;
        }
    }

    private static void recordSchemaVersion() {
        String fingerprint = schemaFingerprint();
        if (fingerprint == null) return;
        try (Connection connection = DBConnection.getRawConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO schema_state(id, fingerprint) VALUES (1, ?)
                     ON DUPLICATE KEY UPDATE fingerprint = VALUES(fingerprint), applied_at = CURRENT_TIMESTAMP""")) {
            statement.setString(1, fingerprint);
            statement.executeUpdate();
        } catch (SQLException e) {
            // H2 has no ON DUPLICATE KEY UPDATE; fall back to delete-then-insert.
            try (Connection connection = DBConnection.getRawConnection();
                 Statement delete = connection.createStatement();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO schema_state(id, fingerprint) VALUES (1, ?)")) {
                delete.executeUpdate("DELETE FROM schema_state WHERE id = 1");
                insert.setString(1, fingerprint);
                insert.executeUpdate();
            } catch (SQLException ignored) {
                // Worst case the schema is applied again next launch.
            }
        }
    }

    private static String schemaFingerprint() {
        try {
            String script = readResource(SCHEMA_RESOURCE);
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(script.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * One-off repairs an existing database needs that a declarative schema
     * cannot express.
     * <p>
     * These live here rather than in schema.sql deliberately: schema.sql is a
     * description of the finished schema and is asserted as such by
     * SchemaCompatibilityTest, which applies it to a blank database and
     * expects every statement to succeed. Imperative "fix what an older
     * install already has" work fails that expectation by nature -- it is a
     * no-op against a fresh database, and its whole purpose is to run against
     * one that is already populated.
     * <p>
     * Everything here must be safe to run repeatedly.
     */
    private static void repairExistingInstall() throws SQLException {
        try (Connection connection = DBConnection.getRawConnection(); Statement statement = connection.createStatement()) {
            // vouchers gained uq_voucher_partner_title only after the seed
            // catalogue shipped, so a database seeded twice before that holds
            // duplicate rows the new key cannot be added over. Keep the
            // earliest row of each (partner, title) pair. The derived-table
            // wrapper is required by MySQL, which otherwise refuses to read
            // from the table it is deleting from.
            try {
                statement.executeUpdate("""
                        DELETE FROM vouchers WHERE id NOT IN (
                            SELECT keep_id FROM (
                                SELECT MIN(id) AS keep_id FROM vouchers GROUP BY partner_id, title
                            ) AS keep
                        )""");
                statement.execute("ALTER TABLE vouchers ADD CONSTRAINT uq_voucher_partner_title UNIQUE (partner_id, title)");
            } catch (SQLException e) {
                if (!isAlreadyApplied(e)) throw e;
            }
        }
    }

    /** MySQL/H2 codes and messages meaning "this object already exists", i.e. this statement has already been applied. */
    private static boolean isAlreadyApplied(SQLException e) {
        switch (e.getErrorCode()) {
            case 1022:  // duplicate key
            case 1050:  // table already exists
            case 1060:  // duplicate column name
            case 1061:  // duplicate key name
            case 1826:  // duplicate foreign key constraint name
            case 3822:  // duplicate check constraint name
            // A DROP INDEX for an index that is already gone. Needed because
            // schema.sql is re-run whole whenever its hash changes, so a
            // "drop the old key, add the corrected one" migration must be
            // survivable on a database that has already had it applied.
            case 1091:  // can't DROP; check that column/key exists
                return true;
            default:
                break;
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("already exists") || message.contains("duplicate");
    }

    private static void seedDemoData() throws SQLException {
        try (Connection connection = DBConnection.getRawConnection()) {
            connection.setAutoCommit(false);
            try {
                long adminId = upsertUser(connection, "BloodLink Administrator", "admin@bloodlink.local",
                        "Admin@123", "01700000001", "Dhaka", "IUT Demo Administration", Role.ADMIN, true, true);
                long requesterId = upsertUser(connection, "Demo Requester", "requester@bloodlink.local",
                        "Request@123", "01700000002", "Dhaka", "Uttara, Dhaka", Role.REQUESTER, true, true);

                long donorONeg = upsertDonor(connection, "O Negative Donor", "donor.oneg@bloodlink.local",
                        "01700000011", "Dhaka", BloodGroup.O_NEGATIVE, LocalDate.of(1996, 3, 15), 68,
                        LocalDate.now().minusDays(120), AvailabilityStatus.AVAILABLE, 7);
                long donorOPos = upsertDonor(connection, "O Positive Donor", "donor.opos@bloodlink.local",
                        "01700000012", "Dhaka", BloodGroup.O_POSITIVE, LocalDate.of(1998, 8, 21), 74,
                        LocalDate.now().minusDays(95), AvailabilityStatus.AVAILABLE, 4);
                long donorANeg = upsertDonor(connection, "A Negative Donor", "donor.aneg@bloodlink.local",
                        "01700000013", "Gazipur", BloodGroup.A_NEGATIVE, LocalDate.of(1995, 1, 12), 62,
                        LocalDate.now().minusDays(80), AvailabilityStatus.AVAILABLE, 2);
                long donorAPos = upsertDonor(connection, "A Positive Donor", "donor.apos@bloodlink.local",
                        "01700000014", "Dhaka", BloodGroup.A_POSITIVE, LocalDate.of(1997, 11, 5), 70,
                        LocalDate.now().minusDays(70), AvailabilityStatus.AVAILABLE, 3);
                long donorBNeg = upsertDonor(connection, "B Negative Donor", "donor.bneg@bloodlink.local",
                        "01700000015", "Narayanganj", BloodGroup.B_NEGATIVE, LocalDate.of(1994, 5, 9), 66,
                        LocalDate.now().minusDays(100), AvailabilityStatus.OUT_OF_TOWN, 1);
                long donorBPos = upsertDonor(connection, "B Positive Donor", "donor.bpos@bloodlink.local",
                        "01700000016", "Dhaka", BloodGroup.B_POSITIVE, LocalDate.of(1999, 4, 17), 72,
                        LocalDate.now().minusDays(90), AvailabilityStatus.AVAILABLE, 5);
                long donorABNeg = upsertDonor(connection, "AB Negative Donor", "donor.abneg@bloodlink.local",
                        "01700000017", "Dhaka", BloodGroup.AB_NEGATIVE, LocalDate.of(1993, 12, 1), 64,
                        LocalDate.now().minusDays(30), AvailabilityStatus.MEDICAL_HOLD, 8);
                long donorABPos = upsertDonor(connection, "AB Positive Donor", "donor.abpos@bloodlink.local",
                        "01700000018", "Dhaka", BloodGroup.AB_POSITIVE, LocalDate.of(1996, 9, 27), 77,
                        null, AvailabilityStatus.AVAILABLE, 0);

                upsertDonor(connection, "Woasif Mehmud Issmam", "issmam@bloodlink.local",
                        "01700000019", "Dhaka", BloodGroup.O_POSITIVE, LocalDate.of(1998, 5, 20), 75,
                        LocalDate.now().minusDays(100), AvailabilityStatus.AVAILABLE, 5);

                upsertDonor(connection, "Woasif Mehmud Issmam", "woasif0@example.com",
                        "01700000020", "Dhaka", BloodGroup.O_POSITIVE, LocalDate.of(1998, 5, 20), 75,
                        LocalDate.now().minusDays(100), AvailabilityStatus.AVAILABLE, 5);

                if (!demoRequestsExist(connection, requesterId)) {
                    long pending = insertRequest(connection, requesterId, BloodGroup.AB_NEGATIVE, 2, Urgency.CRITICAL,
                            "Dhaka Medical College Hospital", "Dhaka", LocalDate.now().plusDays(1),
                            "[DEMO] Rare-group emergency request", RequestStatus.PENDING, null);
                    insertHistory(connection, pending, null, RequestStatus.PENDING, requesterId, "Demo request submitted");

                    long matched = insertRequest(connection, requesterId, BloodGroup.A_POSITIVE, 1, Urgency.URGENT,
                            "Square Hospital", "Dhaka", LocalDate.now().plusDays(2),
                            "[DEMO] Awaiting donor response", RequestStatus.MATCHED, null);
                    insertHistory(connection, matched, null, RequestStatus.PENDING, requesterId, "Demo request submitted");
                    insertHistory(connection, matched, RequestStatus.PENDING, RequestStatus.MATCHED, requesterId,
                            "Eligible donors ranked");
                    insertMatch(connection, matched, donorAPos, 98.0, "exact blood group, same district, cooldown complete", MatchStatus.NOTIFIED);
                    insertMatch(connection, matched, donorONeg, 86.0, "compatible blood group, same district, cooldown complete", MatchStatus.NOTIFIED);
                    insertNotification(connection, donorAPos, "Urgent blood match", "Demo request #" + matched + " is waiting for your response.", "MATCH", matched);

                    long accepted = insertRequest(connection, requesterId, BloodGroup.O_POSITIVE, 2, Urgency.URGENT,
                            "United Hospital", "Dhaka", LocalDate.now().plusDays(1),
                            "[DEMO] Accepted donor coordination", RequestStatus.ACCEPTED, donorOPos);
                    insertHistory(connection, accepted, null, RequestStatus.PENDING, requesterId, "Demo request submitted");
                    insertHistory(connection, accepted, RequestStatus.PENDING, RequestStatus.MATCHED, requesterId,
                            "Eligible donors ranked");
                    insertHistory(connection, accepted, RequestStatus.MATCHED, RequestStatus.ACCEPTED, donorOPos,
                            "Donor accepted the request");
                    insertMatch(connection, accepted, donorOPos, 96.0, "exact blood group, same district", MatchStatus.ACCEPTED);
                    insertNotification(connection, requesterId, "Donor accepted", "A donor accepted demo request #" + accepted + ".", "RESPONSE", accepted);

                    long fulfilled = insertRequest(connection, requesterId, BloodGroup.B_POSITIVE, 1, Urgency.NORMAL,
                            "Evercare Hospital Dhaka", "Dhaka", LocalDate.now().minusDays(15),
                            "[DEMO] Completed request", RequestStatus.FULFILLED, donorBPos);
                    insertHistory(connection, fulfilled, null, RequestStatus.PENDING, requesterId, "Demo request submitted");
                    insertHistory(connection, fulfilled, RequestStatus.PENDING, RequestStatus.MATCHED, requesterId,
                            "Eligible donors ranked");
                    insertHistory(connection, fulfilled, RequestStatus.MATCHED, RequestStatus.ACCEPTED, donorBPos,
                            "Donor accepted the request");
                    insertHistory(connection, fulfilled, RequestStatus.ACCEPTED, RequestStatus.FULFILLED, requesterId,
                            "Requester confirmed fulfillment");
                    insertMatch(connection, fulfilled, donorBPos, 95.0, "exact blood group, same district", MatchStatus.ACCEPTED);
                    insertDonation(connection, donorBPos, fulfilled, LocalDate.now().minusDays(16),
                            "Evercare Hospital Dhaka", BloodGroup.B_POSITIVE, 1);
                    insertNotification(connection, donorBPos, "Donation verified", "Your demo donation was verified.", "FULFILLED", fulfilled);

                    // Patch 3b applied: link the demo requests above (and any other existing
                    // requests) to their curated hospital record now that both exist, matching
                    // on exact name+district.
                    try (PreparedStatement backfill = connection.prepareStatement(
                            "UPDATE blood_requests br SET hospital_id = (" +
                            "  SELECT h.id FROM hospitals h WHERE h.name=br.hospital_name AND h.district=br.district" +
                            ") WHERE br.hospital_id IS NULL AND EXISTS (" +
                            "  SELECT 1 FROM hospitals h WHERE h.name=br.hospital_name AND h.district=br.district" +
                            ")")) {
                        backfill.executeUpdate();
                    }

                    // Patch 3c applied: demo data for this session's newer features
                    // (multi-donor progress, handshake states, mutual reviews, donor
                    // reference hospital). Self-contained -- skips gracefully if the
                    // hospitals table isn't populated yet.
                    seedAdvancedFeatureDemoData(connection, requesterId, donorOPos, donorONeg, donorAPos, donorANeg);
                    
                    seedHistoricalRequests(connection, requesterId, donorOPos, donorAPos, donorBPos);

                    insertAudit(connection, adminId, "SEED_DATA", "SYSTEM", null,
                            "Inserted BloodLink demonstration accounts and lifecycle data");
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Demo data for this session's features: a donor with a reference hospital set, a
     * 3-unit request sitting at PARTIALLY_FULFILLED (one donor fully confirmed, one
     * accepted-but-pending, one still notified), and a second FULFILLED request with a
     * genuine mutual review already on both sides.
     */
    private static void seedAdvancedFeatureDemoData(Connection connection, long requesterId,
                                                     long donorOPos, long donorONeg, long donorAPos, long donorANeg) throws SQLException {
        Long dhakaMedicalId = findHospitalId(connection, "Dhaka Medical College Hospital", "Dhaka");
        if (dhakaMedicalId != null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE donor_profiles SET reference_hospital_id=? WHERE user_id=?")) {
                statement.setLong(1, dhakaMedicalId);
                statement.setLong(2, donorOPos);
                statement.executeUpdate();
            }
        }

        long multiDonorRequest = insertRequest(connection, requesterId, BloodGroup.O_POSITIVE, 3, Urgency.URGENT,
                "Evercare Hospital Dhaka", "Dhaka", LocalDate.now().plusDays(3),
                "[DEMO] Multi-donor partial fulfillment example", RequestStatus.PARTIALLY_FULFILLED, null);
        insertHistory(connection, multiDonorRequest, null, RequestStatus.PENDING, requesterId, "Demo request submitted");
        insertHistory(connection, multiDonorRequest, RequestStatus.PENDING, RequestStatus.MATCHED, requesterId, "Eligible donors ranked");
        insertHistory(connection, multiDonorRequest, RequestStatus.MATCHED, RequestStatus.PARTIALLY_FULFILLED, requesterId, "First donor confirmed");

        Timestamp twoHoursAgo = Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(2));
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status,responded_at,donor_confirmed_at,requester_confirmed_at) " +
                        "VALUES(?,?,?,?,'ACCEPTED',?,?,?)")) {
            statement.setLong(1, multiDonorRequest);
            statement.setLong(2, donorOPos);
            statement.setDouble(3, 97.0);
            statement.setString(4, "exact blood group, same district, cooldown complete");
            statement.setTimestamp(5, twoHoursAgo);
            statement.setTimestamp(6, twoHoursAgo);
            statement.setTimestamp(7, twoHoursAgo);
            statement.executeUpdate();
        }
        insertDonation(connection, donorOPos, multiDonorRequest, LocalDate.now().minusDays(1),
                "Evercare Hospital Dhaka", BloodGroup.O_POSITIVE, 1);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE blood_requests SET units_fulfilled=1 WHERE id=?")) {
            statement.setLong(1, multiDonorRequest);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status,responded_at) VALUES(?,?,?,?,'ACCEPTED',?)")) {
            statement.setLong(1, multiDonorRequest);
            statement.setLong(2, donorONeg);
            statement.setDouble(3, 88.0);
            statement.setString(4, "compatible blood group, same district");
            statement.setTimestamp(5, Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(30)));
            statement.executeUpdate();
        }

        insertMatch(connection, multiDonorRequest, donorAPos, 74.0, "compatible blood group, different district", MatchStatus.NOTIFIED);
        insertNotification(connection, donorAPos, "Urgent blood match",
                "Demo request #" + multiDonorRequest + " matches your profile.", "MATCH", multiDonorRequest);

        long reviewedRequest = insertRequest(connection, requesterId, BloodGroup.A_NEGATIVE, 1, Urgency.NORMAL,
                "Square Hospital", "Dhaka", LocalDate.now().minusDays(10),
                "[DEMO] Completed request with mutual review", RequestStatus.FULFILLED, null);
        insertHistory(connection, reviewedRequest, null, RequestStatus.PENDING, requesterId, "Demo request submitted");
        insertHistory(connection, reviewedRequest, RequestStatus.PENDING, RequestStatus.MATCHED, requesterId, "Eligible donors ranked");
        insertHistory(connection, reviewedRequest, RequestStatus.MATCHED, RequestStatus.FULFILLED, requesterId, "Both parties confirmed the donation");

        Timestamp confirmedAt = Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(9));
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status,responded_at,donor_confirmed_at,requester_confirmed_at) " +
                        "VALUES(?,?,?,?,'ACCEPTED',?,?,?)")) {
            statement.setLong(1, reviewedRequest);
            statement.setLong(2, donorANeg);
            statement.setDouble(3, 99.0);
            statement.setString(4, "exact blood group, same district, cooldown complete");
            statement.setTimestamp(5, confirmedAt);
            statement.setTimestamp(6, confirmedAt);
            statement.setTimestamp(7, confirmedAt);
            statement.executeUpdate();
        }
        insertDonation(connection, donorANeg, reviewedRequest, LocalDate.now().minusDays(9), "Square Hospital", BloodGroup.A_NEGATIVE, 1);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE blood_requests SET units_fulfilled=1 WHERE id=?")) {
            statement.setLong(1, reviewedRequest);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO reviews(request_id,reviewer_id,reviewed_id,rating,tags,comment) VALUES(?,?,?,?,?,?)")) {
            statement.setLong(1, reviewedRequest);
            statement.setLong(2, requesterId);
            statement.setLong(3, donorANeg);
            statement.setInt(4, 5);
            statement.setString(5, "RELIABLE,PUNCTUAL");
            statement.setString(6, "Arrived quickly and was very kind throughout.");
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO reviews(request_id,reviewer_id,reviewed_id,rating,tags,comment) VALUES(?,?,?,?,?,?)")) {
            statement.setLong(1, reviewedRequest);
            statement.setLong(2, donorANeg);
            statement.setLong(3, requesterId);
            statement.setInt(4, 5);
            statement.setString(5, "RESPONSIVE,COOPERATIVE");
            statement.setString(6, "Clear communication and a smooth process.");
            statement.executeUpdate();
        }
    }
    
    private static void seedHistoricalRequests(Connection connection, long requesterId, long d1, long d2, long d3) throws SQLException {
        // Generate 30 requests spread across the last 6 months for chart visualization
        java.util.Random rnd = new java.util.Random(42);
        BloodGroup[] groups = BloodGroup.values();
        Urgency[] urgencies = Urgency.values();
        String[] hospitals = {"Dhaka Medical College Hospital", "Square Hospital", "United Hospital", "Evercare Hospital Dhaka", "BIRDEM General Hospital"};
        
        for (int i = 0; i < 30; i++) {
            int daysAgo = rnd.nextInt(180);
            LocalDate date = LocalDate.now().minusDays(daysAgo);
            BloodGroup group = groups[rnd.nextInt(groups.length)];
            Urgency urgency = urgencies[rnd.nextInt(urgencies.length)];
            String hospital = hospitals[rnd.nextInt(hospitals.length)];
            int units = rnd.nextInt(3) + 1;
            
            long reqId = insertRequest(connection, requesterId, group, units, urgency, hospital, "Dhaka", date.plusDays(2), "[DEMO CHART] Historical request " + i, RequestStatus.FULFILLED, (i%3==0?d1:(i%3==1?d2:d3)));
            
            // Backdate the created_at timestamp
            try (PreparedStatement stmt = connection.prepareStatement("UPDATE blood_requests SET created_at = ? WHERE id = ?")) {
                stmt.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
                stmt.setLong(2, reqId);
                stmt.executeUpdate();
            }
        }
    }

    /** Returns null (not an exception) if the hospitals table isn't populated yet -- this demo addition must never fail the rest of seeding over it. */
    private static Long findHospitalId(Connection connection, String name, String district) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM hospitals WHERE name=? AND district=?")) {
            statement.setString(1, name);
            statement.setString(2, district);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private static long upsertDonor(Connection connection, String name, String email, String phone, String district,
                                    BloodGroup group, LocalDate birthDate, double weight, LocalDate lastDonation,
                                    AvailabilityStatus availability, int verifiedDonations) throws SQLException {
        long id = upsertUser(connection, name, email, "Donor@123", phone, district,
                district + " demo address", Role.DONOR, true, true);
        try (PreparedStatement check = connection.prepareStatement("SELECT user_id FROM donor_profiles WHERE user_id=?")) {
            check.setLong(1, id);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return id;
                }
            }
        }
        String sql = """
                INSERT INTO donor_profiles(user_id,blood_group,birth_date,weight_kg,last_donation_date,
                                           availability_status,verified_donation_count)
                VALUES(?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, group.name());
            statement.setObject(3, birthDate);
            statement.setDouble(4, weight);
            statement.setObject(5, lastDonation);
            statement.setString(6, availability.name());
            statement.setInt(7, verifiedDonations);
            statement.executeUpdate();
        }
        return id;
    }

    private static long upsertUser(Connection connection, String name, String email, String password, String phone,
                                   String district, String address, Role role, boolean approved, boolean active) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement("SELECT id FROM users WHERE email=?")) {
            check.setString(1, email.toLowerCase(Locale.ROOT));
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        String sql = """
                INSERT INTO users(full_name,email,password_hash,phone,district,address,role,approved,active,photo)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, email.toLowerCase(Locale.ROOT));
            statement.setString(3, PasswordUtil.hash(password));
            statement.setString(4, phone);
            statement.setString(5, district);
            statement.setString(6, address);
            statement.setString(7, role.name());
            statement.setBoolean(8, approved);
            statement.setBoolean(9, active);
            statement.setBytes(10, generateRandomPhoto(name));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE email=?")) {
            statement.setString(1, email.toLowerCase(Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new SQLException("Unable to retrieve seeded user: " + email);
                return rs.getLong(1);
            }
        }
    }

    private static boolean demoRequestsExist(Connection connection, long requesterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM blood_requests WHERE requester_id=? AND notes LIKE '[DEMO CHART]%'")) {
            statement.setLong(1, requesterId);
            try (ResultSet rs = statement.executeQuery()) { rs.next(); return rs.getLong(1) > 0; }
        }
    }

    private static long insertRequest(Connection connection, long requesterId, BloodGroup group, int units,
                                      Urgency urgency, String hospital, String district, LocalDate deadline,
                                      String notes, RequestStatus status, Long donorId) throws SQLException {
        String sql = """
                INSERT INTO blood_requests(requester_id,blood_group,units_needed,urgency,hospital_name,district,
                                           deadline,notes,status,accepted_donor_id)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, requesterId);
            statement.setString(2, group.name());
            statement.setInt(3, units);
            statement.setString(4, urgency.name());
            statement.setString(5, hospital);
            statement.setString(6, district);
            statement.setObject(7, deadline);
            statement.setString(8, notes);
            statement.setString(9, status.name());
            if (donorId == null) statement.setNull(10, Types.BIGINT); else statement.setLong(10, donorId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Unable to create demo request.");
                return keys.getLong(1);
            }
        }
    }

    private static void insertMatch(Connection connection, long requestId, long donorId, double score,
                                    String reason, MatchStatus status) throws SQLException {
        String sql = "INSERT INTO request_matches(request_id,donor_id,match_score,match_reason,status,responded_at) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            statement.setLong(2, donorId);
            statement.setDouble(3, score);
            statement.setString(4, reason);
            statement.setString(5, status.name());
            if (status == MatchStatus.NOTIFIED) statement.setNull(6, Types.TIMESTAMP);
            else statement.setTimestamp(6, Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(2)));
            statement.executeUpdate();
        }
    }

    private static void insertHistory(Connection connection, long requestId, RequestStatus from, RequestStatus to,
                                      Long changedBy, String note) throws SQLException {
        String sql = "INSERT INTO request_status_history(request_id,from_status,to_status,changed_by,note) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, requestId);
            if (from == null) statement.setNull(2, Types.VARCHAR); else statement.setString(2, from.name());
            statement.setString(3, to.name());
            if (changedBy == null) statement.setNull(4, Types.BIGINT); else statement.setLong(4, changedBy);
            statement.setString(5, note);
            statement.executeUpdate();
        }
    }

    private static void insertNotification(Connection connection, long userId, String title, String message,
                                           String type, Long requestId) throws SQLException {
        String sql = "INSERT INTO notifications(user_id,title,message,type,related_request_id) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, title);
            statement.setString(3, message);
            statement.setString(4, type);
            if (requestId == null) statement.setNull(5, Types.BIGINT); else statement.setLong(5, requestId);
            statement.executeUpdate();
        }
    }

    private static void insertDonation(Connection connection, long donorId, long requestId, LocalDate date,
                                       String hospital, BloodGroup group, int units) throws SQLException {
        String sql = """
                INSERT INTO donation_history(donor_id,request_id,donation_date,hospital_name,blood_group,units,verified)
                VALUES(?,?,?,?,?,?,TRUE)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            statement.setLong(2, requestId);
            statement.setObject(3, date);
            statement.setString(4, hospital);
            statement.setString(5, group.name());
            statement.setInt(6, units);
            statement.executeUpdate();
        }
    }

    private static void insertAudit(Connection connection, Long actorId, String action, String entityType,
                                    Long entityId, String details) throws SQLException {
        String sql = "INSERT INTO audit_logs(actor_user_id,action,entity_type,entity_id,details) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (actorId == null) statement.setNull(1, Types.BIGINT); else statement.setLong(1, actorId);
            statement.setString(2, action);
            statement.setString(3, entityType);
            if (entityId == null) statement.setNull(4, Types.BIGINT); else statement.setLong(4, entityId);
            statement.setString(5, details);
            statement.executeUpdate();
        }
    }

    private static byte[] generateRandomPhoto(String name) {
        try {
            int size = 150;
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            // Random vibrant background color
            float hue = (float) Math.random();
            g2d.setColor(Color.getHSBColor(hue, 0.6f, 0.8f));
            g2d.fillRect(0, 0, size, size);
            
            // Draw initials
            g2d.setColor(Color.WHITE);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 60));
            String initials = name != null && name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase() : "U";
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            int x = (size - fm.stringWidth(initials)) / 2;
            int y = (fm.getAscent() + (size - (fm.getAscent() + fm.getDescent())) / 2);
            g2d.drawString(initials, x, y);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream input = DatabaseSetup.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing SQL resource: " + resource);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().reduce("", (a, b) -> a + b + System.lineSeparator());
            }
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = current.toString().trim();
                statements.add(sql.substring(0, sql.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) statements.add(current.toString().trim());
        return statements;
    }

    private record JdbcTarget(String serverUrl, String databaseName) {
        static JdbcTarget parse(String url) {
            if (url == null || !url.startsWith("jdbc:mysql://"))
                throw new IllegalArgumentException("DB_URL must start with jdbc:mysql://");
            int queryIndex = url.indexOf('?');
            String withoutQuery = queryIndex >= 0 ? url.substring(0, queryIndex) : url;
            String query = queryIndex >= 0 ? url.substring(queryIndex) : "";
            int slash = withoutQuery.indexOf('/', "jdbc:mysql://".length());
            if (slash < 0 || slash == withoutQuery.length() - 1)
                throw new IllegalArgumentException("DB_URL must include a database name.");
            String database = withoutQuery.substring(slash + 1);
            if (!database.matches("[A-Za-z0-9_]+"))
                throw new IllegalArgumentException("Database name contains unsupported characters.");
            String server = withoutQuery.substring(0, slash + 1) + query;
            return new JdbcTarget(server, database);
        }
    }
}
