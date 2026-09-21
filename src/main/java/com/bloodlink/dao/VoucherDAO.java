package com.bloodlink.dao;

import com.bloodlink.model.Voucher;
import com.bloodlink.model.VoucherPartner;
import com.bloodlink.model.VoucherRedemption;
import com.bloodlink.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class VoucherDAO {

    public List<VoucherPartner> findActivePartners() throws SQLException {
        String sql = "SELECT id, name, category, logo_path, description, active FROM voucher_partners WHERE active=TRUE ORDER BY name";
        List<VoucherPartner> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) rows.add(mapPartner(rs));
        }
        return rows;
    }

    public List<Voucher> findActiveVouchers() throws SQLException {
        String sql = """
                SELECT v.id, v.partner_id, p.name partner_name, p.logo_path partner_logo_path,
                       v.title, v.description, v.points_cost, v.active
                FROM vouchers v JOIN voucher_partners p ON p.id = v.partner_id
                WHERE v.active = TRUE AND p.active = TRUE
                ORDER BY p.name, v.points_cost
                """;
        List<Voucher> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) rows.add(mapVoucher(rs));
        }
        return rows;
    }

    public Voucher findVoucherById(long voucherId) throws SQLException {
        String sql = """
                SELECT v.id, v.partner_id, p.name partner_name, p.logo_path partner_logo_path,
                       v.title, v.description, v.points_cost, v.active
                FROM vouchers v JOIN voucher_partners p ON p.id = v.partner_id
                WHERE v.id = ?
                """;
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, voucherId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapVoucher(rs) : null;
            }
        }
    }

    /** Locks the voucher row so a concurrent redemption of the last available copy of a limited voucher (a future extension) would serialize correctly. */
    public Voucher lockVoucherById(Connection connection, long voucherId) throws SQLException {
        String sql = """
                SELECT v.id, v.partner_id, p.name partner_name, p.logo_path partner_logo_path,
                       v.title, v.description, v.points_cost, v.active
                FROM vouchers v JOIN voucher_partners p ON p.id = v.partner_id
                WHERE v.id = ? FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, voucherId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? mapVoucher(rs) : null;
            }
        }
    }

    public void insertRedemption(Connection connection, long donorId, long voucherId, int pointsSpent, String redemptionCode) throws SQLException {
        String sql = "INSERT INTO voucher_redemptions(donor_id, voucher_id, points_spent, redemption_code) VALUES (?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            statement.setLong(2, voucherId);
            statement.setInt(3, pointsSpent);
            statement.setString(4, redemptionCode);
            statement.executeUpdate();
        }
    }

    public List<VoucherRedemption> findRedemptionsFor(long donorId) throws SQLException {
        String sql = """
                SELECT r.id, r.donor_id, r.voucher_id, v.title voucher_title, p.name partner_name,
                       r.points_spent, r.redemption_code, r.redeemed_at
                FROM voucher_redemptions r
                JOIN vouchers v ON v.id = r.voucher_id
                JOIN voucher_partners p ON p.id = v.partner_id
                WHERE r.donor_id = ?
                ORDER BY r.redeemed_at DESC
                """;
        List<VoucherRedemption> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, donorId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new VoucherRedemption(rs.getLong("id"), rs.getLong("donor_id"), rs.getLong("voucher_id"),
                            rs.getString("voucher_title"), rs.getString("partner_name"), rs.getInt("points_spent"),
                            rs.getString("redemption_code"), rs.getTimestamp("redeemed_at").toLocalDateTime()));
                }
            }
        }
        return rows;
    }

    private VoucherPartner mapPartner(ResultSet rs) throws SQLException {
        return new VoucherPartner(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                rs.getString("logo_path"), rs.getString("description"), rs.getBoolean("active"));
    }

    private Voucher mapVoucher(ResultSet rs) throws SQLException {
        return new Voucher(rs.getLong("id"), rs.getLong("partner_id"), rs.getString("partner_name"),
                rs.getString("partner_logo_path"), rs.getString("title"), rs.getString("description"),
                rs.getInt("points_cost"), rs.getBoolean("active"));
    }
}
