package com.bloodlink.util;

/**
 * Escapes a Java string for safe interpolation inside a single-quoted
 * JavaScript string literal, for use with {@code WebEngine.executeScript}.
 * <p>
 * The map controllers build script calls like {@code addMarker(1, 2, '%s')}
 * by substituting hospital names, districts, and city names (real data, not
 * attacker input, but not guaranteed free of quotes/backslashes either --
 * e.g. a hospital name containing an apostrophe). Escaping only the single
 * quote and not the backslash is order-dependent and easy to get subtly
 * wrong at each of the many call sites, which is exactly what had happened
 * here: every call site repeated its own {@code .replace("'", "\\'")} with
 * no backslash handling at all. Centralizing it in one place fixes that and
 * gives Phase 2/3 map work a single spot to extend.
 */
public final class JsStrings {
    private JsStrings() { }

    public static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
