package com.bloodlink.view.shell;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

/**
 * The app's pulse mark, drawn large and alive: an ECG trace that draws itself
 * in from the left, then clears itself the same way, over and over.
 *
 * <h2>How the motion is done</h2>
 * A dash pattern of {@code [length, length]} on a line exactly {@code length}
 * long means the line is either fully covered by one dash or fully inside one
 * gap, depending on the offset. Sweeping the offset from {@code +length} to
 * {@code -length} therefore walks the dash across the trace: the first half of
 * the sweep draws the line on, the second half wipes it off, both left to
 * right. One property, one timeline, no per-frame geometry.
 * <p>
 * The length has to be exact or the dash pattern will not line up with the
 * trace and the sweep visibly stutters at the seam, so the geometry is built
 * from a point list in Java and measured segment by segment rather than
 * declared as an SVG string and estimated from its bounds.
 * <p>
 * A dim copy of the same trace sits underneath so the shape is legible even at
 * the moment the bright line has just cleared, rather than the panel appearing
 * to go empty.
 */
public final class Heartbeat extends Pane {

    /**
     * One ECG cycle, then a second: baseline, P wave, the QRS spike, T wave.
     * Authored in a 440x120 box and scaled to whatever size is asked for.
     */
    private static final double[] POINTS = {
            0, 64, 56, 64,
            66, 54, 76, 64,
            88, 64,
            96, 76, 108, 14, 120, 104, 130, 64,
            154, 64,
            168, 50, 182, 64,
            220, 64,
            276, 64,
            286, 54, 296, 64,
            308, 64,
            316, 76, 328, 14, 340, 104, 350, 64,
            374, 64,
            388, 50, 402, 64,
            440, 64
    };

    private static final double SOURCE_WIDTH = 440;
    private static final double SOURCE_HEIGHT = 120;

    private final Timeline sweep;

    public Heartbeat(double width, double height, double strokeWidth) {
        double scaleX = width / SOURCE_WIDTH;
        double scaleY = height / SOURCE_HEIGHT;

        Polyline ghost = trace(scaleX, scaleY, strokeWidth);
        ghost.getStyleClass().add("heartbeat-ghost");

        Polyline live = trace(scaleX, scaleY, strokeWidth);
        live.getStyleClass().add("heartbeat-line");

        double length = length(scaleX, scaleY);
        live.getStrokeDashArray().setAll(length, length);
        live.setStrokeDashOffset(length);

        sweep = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(live.strokeDashOffsetProperty(), length)),
                new KeyFrame(Duration.seconds(2.8), new KeyValue(
                        live.strokeDashOffsetProperty(), -length, Interpolator.LINEAR)));
        sweep.setCycleCount(Animation.INDEFINITE);
        sweep.play();

        getChildren().addAll(ghost, live);
        setPrefSize(width, height);
        setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setPickOnBounds(false);
    }

    private static Polyline trace(double scaleX, double scaleY, double strokeWidth) {
        Polyline line = new Polyline();
        for (int i = 0; i < POINTS.length; i += 2) {
            line.getPoints().addAll(POINTS[i] * scaleX, POINTS[i + 1] * scaleY);
        }
        line.setStrokeWidth(strokeWidth);
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        line.setStrokeLineJoin(StrokeLineJoin.ROUND);
        line.setFill(null);
        return line;
    }

    private static double length(double scaleX, double scaleY) {
        double total = 0;
        for (int i = 2; i < POINTS.length; i += 2) {
            double dx = (POINTS[i] - POINTS[i - 2]) * scaleX;
            double dy = (POINTS[i + 1] - POINTS[i - 1]) * scaleY;
            total += Math.hypot(dx, dy);
        }
        return total;
    }

    /** Stops the sweep; call when the screen holding this goes away for good. */
    public void stop() {
        sweep.stop();
    }
}
