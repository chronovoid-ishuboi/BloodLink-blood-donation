package com.bloodlink.util;

import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Loads the bundled Poppins weights via {@link Font#loadFont}, not CSS
 * {@code @font-face} -- JavaFX's CSS parser rejects a {@code font-weight}
 * descriptor inside {@code @font-face} outright (logs "Unexpected TOKEN" and
 * drops the whole declaration), so there is no way to register six
 * weight-specific TTFs under one family name from CSS alone. Without this
 * call, theme.css's references to "Poppins Medium"/"SemiBold"/"ExtraBold"/
 * "Black" would resolve to whatever those names happen to mean on the host
 * OS (usually nothing, falling back to plain Poppins/Segoe UI at the wrong
 * weight) instead of these bundled files.
 * <p>
 * Call {@link #load()} once before the first Scene is created.
 */
public final class Fonts {
    private Fonts() { }

    private static final String[] FILES = {
            "Poppins-Regular.ttf",
            "Poppins-Medium.ttf",
            "Poppins-SemiBold.ttf",
            "Poppins-Bold.ttf",
            "Poppins-ExtraBold.ttf",
            "Poppins-Black.ttf",
    };

    private static boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        for (String file : FILES) {
            try (InputStream input = Fonts.class.getResourceAsStream("/com/bloodlink/fonts/" + file)) {
                if (input == null) {
                    System.err.println("Warning: font resource missing: " + file);
                    continue;
                }
                if (Font.loadFont(input, 12) == null) {
                    System.err.println("Warning: JavaFX could not parse font file: " + file);
                }
            } catch (Exception e) {
                System.err.println("Warning: failed to load font " + file + ": " + e.getMessage());
            }
        }
    }
}
