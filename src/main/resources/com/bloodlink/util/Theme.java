package com.bloodlink.util;

import javafx.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Light or dark, applied across every window and remembered between runs.
 *
 * <h2>How it works</h2>
 * dark.css redefines only the colour tokens on {@code .root}, and every rule
 * in theme.css resolves its colours through those tokens. Layering it after
 * theme.css therefore re-skins the whole app without a parallel copy of the
 * layout and typography rules -- so a new component picks up dark mode for
 * free as long as it uses tokens rather than hard-coded hex.
 * <p>
 * Scenes register themselves here, including dialogs opened later, so
 * toggling updates windows that are already on screen instead of only the
 * next one opened. References are dropped when a scene's window closes.
 */
public final class Theme {

    public enum Mode { LIGHT, DARK }

    private static final String DARK_STYLESHEET = "/com/bloodlink/css/dark.css";
    private static final String PREF_KEY = "theme.mode";

    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static final List<java.lang.ref.WeakReference<Scene>> SCENES = new ArrayList<>();

    private static Mode current = load();

    private Theme() { }

    private static Mode load() {
        try {
            return Mode.valueOf(PREFS.get(PREF_KEY, Mode.LIGHT.name()));
        } catch (RuntimeException e) {
            return Mode.LIGHT;
        }
    }

    public static Mode current() {
        return current;
    }

    public static boolean isDark() {
        return current == Mode.DARK;
    }

    /**
     * Registers a scene and applies the current mode to it immediately. Safe
     * to call more than once for the same scene.
     */
    public static void register(Scene scene) {
        if (scene == null) return;
        SCENES.removeIf(reference -> reference.get() == null || reference.get() == scene);
        SCENES.add(new java.lang.ref.WeakReference<>(scene));
        apply(scene);
    }

    public static void toggle() {
        set(current == Mode.LIGHT ? Mode.DARK : Mode.LIGHT);
    }

    public static void set(Mode mode) {
        if (mode == current) return;
        current = mode;
        try {
            PREFS.put(PREF_KEY, mode.name());
        } catch (RuntimeException e) {
            // Not being able to remember the choice is not a reason to refuse it.
        }
        SCENES.removeIf(reference -> reference.get() == null);
        for (java.lang.ref.WeakReference<Scene> reference : SCENES) {
            Scene scene = reference.get();
            if (scene != null) apply(scene);
        }
    }

    /**
     * Applied to the root <em>Parent's</em> stylesheet list, not the Scene's.
     * <p>
     * This is not interchangeable. JavaFX resolves a Parent's stylesheets at a
     * higher precedence than the Scene's, and every screen declares theme.css
     * on its FXML root -- so a dark stylesheet added to the Scene loses to it
     * and the page stays light while only the Java-built rail changes, which
     * is exactly what happened first time. Appending to the same list
     * theme.css lives in, after it, is what makes the token overrides win.
     */
    private static void apply(Scene scene) {
        java.net.URL dark = Theme.class.getResource(DARK_STYLESHEET);
        if (dark == null) return;
        String url = dark.toExternalForm();

        List<String> target = scene.getRoot() == null
                ? scene.getStylesheets()
                : scene.getRoot().getStylesheets();
        target.remove(url);
        if (current == Mode.DARK) target.add(url);
    }
}
