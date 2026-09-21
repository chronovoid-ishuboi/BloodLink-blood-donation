package com.bloodlink.service;

import com.bloodlink.dao.AuditDAO;
import com.bloodlink.dao.PointsDAO;
import com.bloodlink.dao.VoucherDAO;
import com.bloodlink.model.Voucher;
import com.bloodlink.model.VoucherPartner;
import com.bloodlink.model.VoucherRedemption;
import com.bloodlink.util.DBConnection;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public final class VoucherService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VoucherDAO voucherDAO = new VoucherDAO();
    private final PointsDAO pointsDAO = new PointsDAO();
    private final AuthorizationService authorizationService = new AuthorizationService();

    public List<VoucherPartner> listPartners() {
        try {
            return voucherDAO.findActivePartners();
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public List<Voucher> listVouchers() {
        try {
            return voucherDAO.findActiveVouchers();
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    public List<VoucherRedemption> redemptionHistory(long donorId) {
        try {
            return voucherDAO.findRedemptionsFor(donorId);
        } catch (SQLException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Locks both the voucher (in case a future admin action deactivates it
     * mid-flight) and the donor's balance row for the duration of this
     * transaction, so two redemptions submitted at nearly the same moment
     * cannot both read the same starting balance and both succeed when only
     * one should.
     */
    public ServiceResult<VoucherRedemption> redeem(long donorId, long voucherId) {
        try {
            authorizationService.requireSelfOrAdmin(donorId);
            try (Connection connection = DBConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Voucher voucher = voucherDAO.lockVoucherById(connection, voucherId);
                    if (voucher == null || !voucher.active()) {
                        connection.rollback();
                        return ServiceResult.failure("This voucher is no longer available.");
                    }
                    int balance = pointsDAO.lockBalance(connection, donorId);
                    if (balance < voucher.pointsCost()) {
                        connection.rollback();
                        return ServiceResult.failure("Not enough points. You need " + voucher.pointsCost()
                                + " but have " + balance + ".");
                    }
                    String code = generateRedemptionCode();
                    voucherDAO.insertRedemption(connection, donorId, voucherId, voucher.pointsCost(), code);
                    pointsDAO.award(connection, donorId, -voucher.pointsCost(), "VOUCHER_REDEEMED", null,
                            "Redeemed: " + voucher.title() + " (" + voucher.partnerName() + ")");
                    new AuditDAO().log(connection, donorId, "REDEEM_VOUCHER", "VOUCHER", voucherId,
                            "Redeemed \"" + voucher.title() + "\" for " + voucher.pointsCost() + " points, code " + code);
                    connection.commit();
                    return ServiceResult.success("Redeemed! Show code " + code + " to " + voucher.partnerName() + ".",
                            new VoucherRedemption(0, donorId, voucherId, voucher.title(), voucher.partnerName(),
                                    voucher.pointsCost(), code, java.time.LocalDateTime.now()));
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            return ServiceResult.failure(e.getMessage());
        }
    }

    private String generateRedemptionCode() {
        StringBuilder code = new StringBuilder("BL-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) code.append('-');
            code.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }
}
