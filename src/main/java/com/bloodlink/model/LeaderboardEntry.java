package com.bloodlink.model;

/**
 * One row of the donor leaderboard.
 *
 * @param donorId    the real id, kept even when anonymous so the viewer's own
 *                   row can still be highlighted to them
 * @param displayName what to actually show. Already resolved: a donor who has
 *                   opted out of being named reads "Anonymous donor" here, so
 *                   no rendering path can leak the real name by forgetting to
 *                   check the flag
 * @param anonymous  whether this donor opted out of being named
 * @param isViewer   whether this row is the person looking at the board
 */
public record LeaderboardEntry(int rank, long donorId, String displayName, boolean anonymous,
                                String district, BloodGroup bloodGroup, BadgeTier badgeTier,
                                int verifiedDonations, int points, boolean isViewer) { }
