package com.bloodlink.view.shell;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * A number that counts up to its value instead of simply being there.
 * <p>
 * Used for the awareness figures on the home page. The motion is the point:
 * these are numbers about people, and watching one climb to 12,000 lands
 * differently from reading "12,000" already printed on the screen.
 * <p>
 * Eased rather than linear, so it rushes and then settles instead of
 * ticking mechanically, and it only ever runs once per appearance -- a
 * counter that restarted on every scroll would be noise.
 */
public final class CountUp extends Label {

    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.ENGLISH);

    private final SimpleDoubleProperty shown = new SimpleDoubleProperty(0);
    private final long target;
    private final String suffix;
    private boolean started = false;
    private Timeline running;

    public CountUp(long target, String suffix) {
        this.target = target;
        this.suffix = suffix == null ? "" : suffix;
        setText("0" + this.suffix);
        shown.addListener((obs, old, value) -> setText(FORMAT.format(Math.round(value.doubleValue())) + this.suffix));
    }

    /**
     * Runs the count once. Further calls are ignored -- this is what scroll
     * reveals use, where re-running on every pass would be flicker.
     */
    public void play() {
        if (started) return;
        started = true;
        run();
    }

    /**
     * Runs the count again from zero, whether or not it has run before.
     * <p>
     * Used when someone deliberately opens the section again, as opposed to
     * scrolling past it: the count is the presentation, so arriving at the
     * page to find the numbers already landed shows nothing.
     */
    public void replay() {
        started = true;
        if (running != null) running.stop();
        shown.set(0);
        run();
    }

    private void run() {
        // Longer numbers get a little longer to travel, so a figure in the
        // thousands does not blur past while a small one crawls.
        double seconds = target >= 10_000 ? 2.0 : target >= 1_000 ? 1.6 : 1.2;
        running = new Timeline(new KeyFrame(Duration.seconds(seconds),
                new KeyValue(shown, target, Interpolator.SPLINE(0.12, 0.8, 0.2, 1))));
        running.play();
    }
}
