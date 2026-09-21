package com.bloodlink.model;

import java.time.LocalDateTime;

/**
 * Where a user actually is, and how confidently we know it.
 *
 * @param source how the position was obtained. This travels with the
 *               coordinates everywhere and is rendered next to the pin,
 *               because a city-level IP estimate and a pin someone dropped on
 *               their own street are both "a position" but mean very
 *               different things to a requester judging whether that donor
 *               can reach a hospital in time.
 */
public record UserLocation(double latitude, double longitude, Source source, LocalDateTime updatedAt) {

    public enum Source {
        /** The user placed the pin themselves: precise, and the only source treated as exact. */
        MAP_PIN("Pinned by the user", true),
        /** Auto-detected from the network address: city-level at best, often tens of km out. */
        IP("Approximate (network)", false),
        /** No stored position at all -- the district's reference point stood in. */
        DISTRICT("District estimate", false);

        private final String label;
        private final boolean precise;

        Source(String label, boolean precise) {
            this.label = label;
            this.precise = precise;
        }

        public String getLabel() { return label; }
        public boolean isPrecise() { return precise; }

        public static Source parse(String raw) {
            if (raw == null) return DISTRICT;
            try {
                return valueOf(raw);
            } catch (IllegalArgumentException e) {
                return DISTRICT;
            }
        }
    }
}
