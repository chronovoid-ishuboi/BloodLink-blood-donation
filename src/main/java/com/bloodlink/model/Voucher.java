package com.bloodlink.model;

/** partnerName/partnerLogoPath are carried denormalized from a join so a voucher tile never needs a second query. */
public record Voucher(long id, long partnerId, String partnerName, String partnerLogoPath,
                       String title, String description, int pointsCost, boolean active) { }
