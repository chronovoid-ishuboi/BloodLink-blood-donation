package com.bloodlink.view.shell;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Holds every page of a workspace and swaps between them with a directional
 * slide-and-fade rather than an instant show/hide.
 * <p>
 * Pages are all children of this pane from the start and only one is visible
 * at a time. They are kept (not rebuilt) across switches so a page's scroll
 * position, selection and in-flight background loads survive navigating away
 * and back -- rebuilding on every switch would reset all three and make the
 * app feel like it was reloading itself.
 * <p>
 * Direction comes from the destination's index relative to the current one,
 * so moving down the rail slides content up and moving back up slides it
 * down; the motion matches the direction of travel in the nav instead of
 * always going the same way.
 */
public final class PageHost extends StackPane {

    private static final Duration DURATION = Duration.millis(300);
    private static final Interpolator EASE = Interpolator.SPLINE(0.22, 1, 0.36, 1);

    private Node current;
    private int currentIndex = -1;
    private ParallelTransition running;

    public PageHost() {
        getStyleClass().add("page-host");
    }

    /**
     * Takes over pages already declared as this host's children in FXML:
     * hides all of them, then shows the first with no animation. Called once
     * from the controller's initialize().
     */
    public void initialize() {
        for (Node page : getChildren()) {
            page.setVisible(false);
            page.setManaged(false);
        }
        if (getChildren().isEmpty()) return;
        Node first = getChildren().get(0);
        current = first;
        currentIndex = 0;
        first.setVisible(true);
        first.setManaged(true);
        first.setOpacity(1);
        first.setTranslateY(0);
    }

    /** Switches to the page at {@code index} among this host's children. */
    public void show(int index) {
        if (index < 0 || index >= getChildren().size()) return;
        show(getChildren().get(index), index);
    }

    public void show(Node page, int index) {
        if (page == current) return;
        if (running != null) running.stop();

        boolean forward = index >= currentIndex;
        double offset = forward ? 26 : -26;

        Node outgoing = current;
        current = page;
        currentIndex = index;

        page.setVisible(true);
        page.setManaged(true);
        page.setOpacity(0);
        page.setTranslateY(offset);

        FadeTransition fadeIn = new FadeTransition(DURATION, page);
        fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(DURATION, page);
        slideIn.setToY(0);
        slideIn.setInterpolator(EASE);

        running = new ParallelTransition(fadeIn, slideIn);

        if (outgoing != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(170), outgoing);
            fadeOut.setToValue(0);
            running.getChildren().add(fadeOut);
            // Only un-manage the old page once it has finished fading, or it
            // vanishes instantly and the crossfade has nothing to cross with.
            running.setOnFinished(event -> {
                outgoing.setVisible(false);
                outgoing.setManaged(false);
                outgoing.setTranslateY(0);
            });
        }
        running.play();
    }
}
