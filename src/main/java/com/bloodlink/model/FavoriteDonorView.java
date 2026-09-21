package com.bloodlink.model;

import java.time.LocalDateTime;

/** A requester's saved donor, with just enough donor detail (via join) to render a list without a second query per row. */
public record FavoriteDonorView(long favoriteId, long donorId, String donorName, BloodGroup bloodGroup,
                                 String district, String phone, AvailabilityStatus availabilityStatus,
                                 BadgeTier badgeTier, LocalDateTime favoritedAt) { }
