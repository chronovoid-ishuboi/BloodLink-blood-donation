package com.bloodlink.view.shell;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Scroll-triggered entrance animation: a node starts slightly low and
 * transparent, and rises into place the first time it scrolls into view.
 * <p>
 * Each node reveals once and then stays put -- re-animating on every pass
 * would turn scrolling back up into a flicker of everything replaying.
 * <p>
 * Detection is a viewport-intersection check re-run on scroll rather than
 * anything more elaborate: JavaFX has no equivalent of an intersection
 * observer, and for a page's worth of cards a direct bounds comparison per
 * scroll event is cheaper than maintaining a watcher per node.
 */
public final class Reveal {

    private static final Duration DURATION = Duration.millis(520);
    private static final Interpolator EASE = Interpolator.SPLINE(0.16, 1, 0.3, 1);

    private final ScrollPane scroller;
    private final List<Pending> pending = new ArrayList<>();

    private record Pending(Node node, double delayMillis, Runnable onRevealed) { }

    public Reveal(ScrollPane scroller) {
        this.scroller = scroller;
        scroller.vvalueProperty().addListener((obs, old, value) -> check());
        scroller.viewportBoundsProperty().addListener((obs, old, value) -> check());
    }

    /** Registers a node to reveal when it first scrolls into view. Staggered by {@code delayMillis}. */
    public void watch(Node node, double delayMillis) {
        node.setOpacity(0);
        node.setTranslateY(22);
        pending.add(new Pending(node, delayMillis, null));
        // Anything already on screen at build time should reveal right away,
        // but only after layout has run and the node actually has bounds.
        javafx.application.Platform.runLater(this::check);
    }

    /** As {@link #watch}, plus something to run the moment the node appears -- used to start counters only once they are on screen. */
    public void watchThen(Node node, double delayMillis, Runnable onRevealed) {
        node.setOpacity(0);
        node.setTranslateY(22);
        pending.add(new Pending(node, delayMillis, onRevealed));
        javafx.application.Platform.runLater(this::check);
    }

    /** Registers several nodes, each staggered one step after the previous. */
    public void watchAll(double stepMillis, Node... nodes) {
        for (int i = 0; i < nodes.length; i++) {
            watch(nodes[i], i * stepMillis);
        }
    }

    private void check() {
        if (pending.isEmpty()) return;
        Bounds viewport = scroller.localToScene(scroller.getBoundsInLocal());
        pending.removeIf(entry -> {
            Bounds nodeBounds = entry.node().localToScene(entry.node().getBoundsInLocal());
            if (nodeBounds.getWidth() <= 0 && nodeBounds.getHeight() <= 0) return false;
            // Trigger slightly before the node's top edge reaches the fold, so
            // it finishes arriving rather than starting exactly as it appears.
            boolean visible = nodeBounds.getMinY() < viewport.getMaxY() - 40
                    && nodeBounds.getMaxY() > viewport.getMinY();
            if (!visible) return false;
            play(entry);
            return true;
        });
    }

    private void play(Pending entry) {
        FadeTransition fade = new FadeTransition(DURATION, entry.node());
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(DURATION, entry.node());
        rise.setToY(0);
        rise.setInterpolator(EASE);
        ParallelTransition animation = new ParallelTransition(fade, rise);
        animation.setDelay(Duration.millis(entry.delayMillis()));
        if (entry.onRevealed() != null) animation.setOnFinished(event -> entry.onRevealed().run());
        animation.play();
    }
}
