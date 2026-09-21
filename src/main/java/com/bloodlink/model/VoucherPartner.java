package com.bloodlink.model;

/**
 * logoPath is a resource/file path the UI resolves to an image, or
 * {@code null} when no logo has been supplied yet -- see
 * {@link com.bloodlink.util.LogoManager} for the same optional-image
 * convention already used for the BloodLink brand mark. A partner with no
 * logo renders as a drawn initial-letter badge instead of a broken image.
 */
public record VoucherPartner(long id, String name, String category, String logoPath,
                              String description, boolean active) { }
