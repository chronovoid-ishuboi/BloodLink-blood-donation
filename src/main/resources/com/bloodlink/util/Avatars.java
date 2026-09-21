package com.bloodlink.util;

import javafx.scene.shape.Rectangle;

/**
 * Profile pictures are square with softened corners -- passport-shaped --
 * everywhere in the app, not circular.
 * <p>
 * An {@code ImageView} is not a {@code Region}, so CSS {@code -fx-background-radius}
 * does nothing to it and the rounded crop has to be a clip built in Java.
 * This exists so the same corner radius is not re-typed at each of the places
 * that renders an avatar, which is how they drift apart.
 */
public final class Avatars {
    private Avatars() { }

    /** Corner softening, proportional to size so a 48px and a 96px avatar look like the same shape. */
    private static final double CORNER_RATIO = 0.22;

    public static Rectangle squareClip(double size) {
        Rectangle clip = new Rectangle(size, size);
        double corner = size * CORNER_RATIO;
        clip.setArcWidth(corner);
        clip.setArcHeight(corner);
        return clip;
    }
}
