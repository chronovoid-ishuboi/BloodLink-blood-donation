package com.bloodlink.view.components;

import com.bloodlink.model.BloodGroup;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.List;
import java.util.Set;

/**
 * The "who can give to whom" chart, drawn and animated rather than shown as a
 * static table: a blood bag at the top with one curve running to each of the
 * eight groups, the compatible ones drawing themselves in sequence while the
 * incompatible ones stay muted and flat.
 * <p>
 * Two modes, because the same picture answers a different question for each
 * side of the app:
 * <ul>
 *   <li>{@link Mode#DONOR} -- "your blood can go to these people", the view a
 *       donor sees for their own group.</li>
 *   <li>{@link Mode#RECIPIENT} -- "these donors can give you blood", the view
 *       a requester sees for the group they just requested.</li>
 * </ul>
 * The curve-drawing effect is a stroke-dash animation: each curve's dash
 * pattern is set to its own length so the whole line is one dash, then the
 * dash offset is animated from that length down to zero, which walks the
 * visible segment along the path from start to end.
 */
public final class BloodCompatibilityView extends VBox {

    public enum Mode { DONOR, RECIPIENT }

    private static final double WIDTH = 520;
    private static final double HEIGHT = 340;
    private static final double BAG_X = WIDTH / 2;
    private static final double BAG_Y = 74;

    private final Pane canvas = new Pane();
    private Animation running;

    public BloodCompatibilityView(BloodGroup group, Mode mode) {
        setSpacing(12);
        setAlignment(Pos.TOP_CENTER);
        getStyleClass().add("compatibility-view");

        Label heading = new Label(mode == Mode.DONOR
                ? "Your " + group.getDisplayName() + " blood can go to"
                : "These donors can give " + group.getDisplayName() + " blood");
        heading.getStyleClass().add("section-title");

        Label caption = new Label(mode == Mode.DONOR
                ? "Highlighted groups can safely receive from you."
                : "Highlighted groups can safely donate to this request.");
        caption.getStyleClass().add("helper-text");

        canvas.setPrefSize(WIDTH, HEIGHT);
        canvas.setMinSize(WIDTH, HEIGHT);
        canvas.setMaxSize(WIDTH, HEIGHT);

        getChildren().addAll(heading, caption, canvas);
        build(group, mode);
    }

    private void build(BloodGroup group, Mode mode) {
        Set<BloodGroup> compatible = mode == Mode.DONOR ? group.compatibleRecipients() : group.compatibleDonors();
        List<BloodGroup> all = List.of(BloodGroup.values());

        canvas.getChildren().add(bag(group));

        SequentialTransition sequence = new SequentialTransition();
        // Left column takes the first half, right column the second, so the two
        // sides fill evenly rather than by positive/negative -- which would put
        // all four of one rhesus sign on one side and read as if the sign itself
        // were the grouping rule.
        for (int i = 0; i < all.size(); i++) {
            BloodGroup target = all.get(i);
            boolean leftSide = i % 2 == 0;
            int rowIndex = i / 2;
            double y = 132 + rowIndex * 56;
            double x = leftSide ? 96 : WIDTH - 96;
            boolean isCompatible = compatible.contains(target);

            CubicCurve curve = curve(x, y, isCompatible);
            Label label = groupLabel(target, x, y, isCompatible);
            canvas.getChildren().addAll(curve, label);

            if (isCompatible) sequence.getChildren().add(drawCurve(curve, label));
        }

        running = sequence;
        sequence.play();
    }

    private Group bag(BloodGroup group) {
        SVGPath droplet = new SVGPath();
        droplet.setContent("M28,0 C28,0 8,26 8,38 A20,20 0 0 0 48,38 C48,26 28,0 28,0 Z");
        droplet.getStyleClass().add("compatibility-bag");

        Label label = new Label(group.getDisplayName());
        label.getStyleClass().add("compatibility-bag-label");

        StackPane stack = new StackPane(droplet, label);
        stack.setLayoutX(BAG_X - 28);
        stack.setLayoutY(BAG_Y - 46);
        return new Group(stack);
    }

    private CubicCurve curve(double endX, double endY, boolean compatible) {
        CubicCurve curve = new CubicCurve();
        curve.setStartX(BAG_X);
        curve.setStartY(BAG_Y);
        curve.setControlX1(BAG_X);
        curve.setControlY1(BAG_Y + 48);
        curve.setControlX2(endX);
        curve.setControlY2(endY - 48);
        curve.setEndX(endX < BAG_X ? endX + 34 : endX - 34);
        curve.setEndY(endY);
        curve.getStyleClass().add(compatible ? "compatibility-curve-active" : "compatibility-curve-muted");
        if (compatible) {
            // One dash as long as the whole curve, then walk its offset to zero
            // so the stroke appears to draw itself from the bag outward.
            double length = estimateLength(curve);
            curve.getStrokeDashArray().setAll(length, length);
            curve.setStrokeDashOffset(length);
        }
        return curve;
    }

    /** Chord-plus-control-net approximation -- exact arc length of a cubic has no closed form, and this only needs to be long enough that the dash covers the path. */
    private double estimateLength(CubicCurve curve) {
        double chord = Math.hypot(curve.getEndX() - curve.getStartX(), curve.getEndY() - curve.getStartY());
        double net = Math.hypot(curve.getControlX1() - curve.getStartX(), curve.getControlY1() - curve.getStartY())
                + Math.hypot(curve.getControlX2() - curve.getControlX1(), curve.getControlY2() - curve.getControlY1())
                + Math.hypot(curve.getEndX() - curve.getControlX2(), curve.getEndY() - curve.getControlY2());
        return (chord + net) / 2 + 20;
    }

    private Animation drawCurve(CubicCurve curve, Label label) {
        Timeline draw = new Timeline(new KeyFrame(Duration.millis(420),
                new KeyValue(curve.strokeDashOffsetProperty(), 0, Interpolator.EASE_OUT)));
        FadeTransition pop = new FadeTransition(Duration.millis(260), label);
        pop.setFromValue(0.35);
        pop.setToValue(1.0);
        return new ParallelTransition(draw, pop);
    }

    private Label groupLabel(BloodGroup group, double centerX, double centerY, boolean compatible) {
        Label label = new Label(group.getDisplayName());
        label.getStyleClass().add(compatible ? "compatibility-group-active" : "compatibility-group-muted");
        label.setPrefSize(58, 34);
        label.setAlignment(Pos.CENTER);
        label.setLayoutX(centerX - 29);
        label.setLayoutY(centerY - 17);
        if (compatible) label.setOpacity(0.35);
        return label;
    }

    /** Stops the entry animation -- call when the view is removed while still playing. */
    public void stop() {
        if (running != null) running.stop();
    }
}
