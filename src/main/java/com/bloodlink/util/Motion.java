package com.bloodlink.util;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * JavaFX CSS has no {@code transition} property (see the note at the top of
 * theme.css) -- every hover/press effect in the stylesheet is a discrete rule
 * swap with no motion in between, which reads as flat and instant rather than
 * smooth. This class is the Java-side motion layer: a small lift on
 * hover, applied once per node via a mouse-entered/exited listener pair
 * rather than re-implemented at each call site.
 * <p>
 * {@link #applyTo(Parent)} walks a freshly loaded FXML root once and wires
 * every {@link Button} it finds -- this covers essentially every static
 * button in the app for free from the one call already added in
 * {@link SceneManager}. Nodes built dynamically in Java after that point
 * (list-cell match cards, dialog buttons) are outside that walk and call
 * {@link #attachHoverLift(Node, double)} directly at their construction site
 * instead.
 */
public final class Motion {
    private Motion() { }

    private static final Duration DURATION = Duration.millis(130);
    private static final Interpolator EASE = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);

    /** Wires the hover lift onto every {@link Button} under {@code root}, recursively. */
    public static void applyTo(Parent root) {
        walk(root);
    }

    private static void walk(Node node) {
        if (node instanceof Button button) {
            attachHoverLift(button, 2);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                walk(child);
            }
        }
    }

    /**
     * Scales {@code node} up to {@code targetScale} on hover and back to 1.0
     * on exit, both eased rather than snapped. Safe to call more than once on
     * the same node type across a session (e.g. a recycled list cell) since
     * each call only adds one more listener pair, each independently correct.
     */
    /**
     * Lifts {@code node} a couple of pixels on hover and settles it back on
     * exit.
     * <p>
     * A lift, not a scale, and that is the whole point: scaling a node
     * resamples everything inside it, so text under a hovered card is
     * re-rasterised at a fractional size every frame and comes out soft.
     * Translating by whole pixels moves the glyphs without touching how they
     * are rendered, so the card responds and the type stays crisp -- and it
     * costs far less, which is what makes running the mouse down a long list
     * feel immediate rather than heavy.
     */
    public static void attachHoverLift(Node node, double lift) {
        TranslateTransition rise = new TranslateTransition(DURATION, node);
        rise.setInterpolator(EASE);
        // addEventHandler rather than setOnMouseEntered/Exited so this never
        // clobbers a handler a caller already attached for its own purposes.
        node.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            rise.stop();
            rise.setToY(-lift);
            rise.playFromStart();
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, event -> {
            rise.stop();
            rise.setToY(0);
            rise.playFromStart();
        });
    }
}
