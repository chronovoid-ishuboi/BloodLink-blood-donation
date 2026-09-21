package com.bloodlink.view.shell;

import com.bloodlink.util.Icons;
import com.bloodlink.util.Motion;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * The building blocks the new pages are assembled from -- hero slabs, metric
 * cards, quick-action tiles, progress rows, section headings and the marquee
 * strip. Keeping them here rather than repeating the node trees in each page
 * is what makes the pages look like one designed system instead of eight
 * separately hand-built screens.
 */
public final class UI {
    private UI() { }

    /**
     * Hero with artwork on the right. The text column keeps its own width so
     * the title wraps where it was written to wrap, and the art sits opposite
     * it rather than under it.
     */
    public static HBox heroWithArt(String title, String sub, Node art, Node... trailing) {
        VBox text = hero(title, sub, trailing);
        text.getStyleClass().remove("hero");
        text.setMaxWidth(Region.USE_PREF_SIZE);

        // The spacer, rather than growing the text column, is what pushes the
        // artwork to the far right. Letting the text column take the slack
        // instead leaves the art floating just past the end of the sentence,
        // wherever that happens to fall.
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        StackPane artHolder = new StackPane(art);
        artHolder.setMinWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(34, text, gap, artHolder);
        row.getStyleClass().add("hero");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Full-bleed deep-green slab carrying a page's name and what it is for.
     *
     * <h2>Two lines, not three</h2>
     * This used to take an eyebrow, an invented display headline and a
     * subtitle -- so "Leaderboard" sat above "Donors who keep showing up."
     * above the line that actually explained the page. The middle line was
     * copywriting with nothing to say: it never named the section and never
     * described it, it just filled the space between the two lines that did.
     * The header is now exactly the section's name and one line of what it
     * does, which is all it was ever communicating.
     */
    public static VBox hero(String title, String sub, Node... trailing) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("hero-title");
        titleLabel.setWrapText(true);

        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("hero-sub");
        subLabel.setWrapText(true);
        subLabel.setMaxWidth(620);

        // Spacing set here rather than left to the .hero rule: heroWithArt
        // strips that class off the text column, so the title and subtitle
        // were sitting flush against each other on exactly the two home pages
        // where the header is largest and the gap matters most.
        VBox box = new VBox(10, titleLabel, subLabel);
        box.getStyleClass().add("hero");
        box.getChildren().addAll(trailing);
        return box;
    }

    /** Pill chip with a leading dot, as used across the reference layouts. */
    public static HBox pill(String text) {
        Circle dot = new Circle(3.5);
        dot.getStyleClass().add("hero-pill-dot");
        Label label = new Label(text);
        label.getStyleClass().add("hero-pill-text");
        HBox box = new HBox(dot, label);
        box.getStyleClass().add("hero-pill");
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /** A number, its label, and an optional delta line. */
    public static VBox metric(String value, String label, String delta, boolean tinted) {
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");
        Label captionLabel = new Label(label);
        captionLabel.getStyleClass().add("metric-label");

        VBox box = new VBox(3, valueLabel, captionLabel);
        box.getStyleClass().add(tinted ? "surface-card-tint" : "surface-card");
        if (delta != null) {
            Label deltaLabel = new Label(delta);
            deltaLabel.getStyleClass().add("metric-delta");
            box.getChildren().add(deltaLabel);
        }
        return box;
    }

    /** Big rounded tile with an icon well -- the 2x2 grid from the mobile references. */
    public static HBox actionTile(String iconPath, String title, String subtitle, boolean accent, Runnable onClick) {
        StackPane well = new StackPane(new Group(
                Icons.icon(iconPath, 19, accent ? "action-tile-icon-accent" : "action-tile-icon")));
        well.getStyleClass().add("action-tile-well");
        if (accent) well.getStyleClass().add("action-tile-well-accent");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("action-tile-title");
        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("action-tile-sub");
        subLabel.setWrapText(true);

        VBox text = new VBox(2, titleLabel, subLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox tile = new HBox(well, text);
        tile.getStyleClass().add("action-tile");
        tile.setAlignment(Pos.CENTER_LEFT);
        if (onClick != null) tile.setOnMouseClicked(event -> onClick.run());
        Motion.attachHoverLift(tile, 4);
        return tile;
    }

    /**
     * Labelled bar that fills to {@code fraction} on first layout. The fill is
     * animated from zero rather than drawn at its final width so a page's
     * numbers visibly arrive instead of just being there.
     */
    public static VBox progressRow(String label, String value, double fraction, boolean accent) {
        Label name = new Label(label);
        name.getStyleClass().add("progress-row-label");
        Label amount = new Label(value);
        amount.getStyleClass().add("progress-row-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(name, spacer, amount);
        top.setAlignment(Pos.CENTER_LEFT);

        Region fill = new Region();
        fill.getStyleClass().add("progress-fill");
        if (accent) fill.getStyleClass().add("progress-fill-accent");
        fill.setPrefWidth(0);
        // A StackPane stretches its children to fill it, which would ignore
        // prefWidth entirely and draw every bar at 100%. Pinning maxWidth to
        // the preferred size is what makes the animated width actually show.
        fill.setMaxWidth(Region.USE_PREF_SIZE);

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("progress-track");
        track.setAlignment(Pos.CENTER_LEFT);
        track.widthProperty().addListener((obs, old, width) -> {
            double target = width.doubleValue() * Math.max(0, Math.min(1, fraction));
            Timeline grow = new Timeline(new KeyFrame(Duration.millis(700),
                    new KeyValue(fill.prefWidthProperty(), target, Interpolator.SPLINE(0.22, 1, 0.36, 1))));
            grow.play();
        });

        VBox row = new VBox(7, top, track);
        return row;
    }

    public static VBox sectionHead(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-head-title");
        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("section-head-sub");
        subLabel.setWrapText(true);
        VBox box = new VBox(titleLabel, subLabel);
        box.getStyleClass().add("section-head");
        return box;
    }

    /**
     * Row where every child gets exactly the same width. Uses a GridPane with
     * equal percentage columns rather than HBox+hgrow: hgrow distributes only
     * the <em>leftover</em> space, so children whose natural widths differ
     * (tiles with longer labels) end up different sizes, which reads as a
     * misaligned grid.
     */
    public static javafx.scene.layout.GridPane equalRow(double spacing, Node... children) {
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(spacing);
        double share = 100.0 / children.length;
        for (int i = 0; i < children.length; i++) {
            javafx.scene.layout.ColumnConstraints column = new javafx.scene.layout.ColumnConstraints();
            column.setPercentWidth(share);
            grid.getColumnConstraints().add(column);
            if (children[i] instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
            grid.add(children[i], i, 0);
        }
        return grid;
    }
}
