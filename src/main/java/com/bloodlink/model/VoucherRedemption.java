package com.bloodlink.model;

import java.time.LocalDateTime;

/** voucherTitle/partnerName are carried denormalized from a join for the same reason as Voucher's fields. */
public record VoucherRedemption(long id, long donorId, long voucherId, String voucherTitle, String partnerName,
                                 int pointsSpent, String redemptionCode, LocalDateTime redeemedAt) { }
