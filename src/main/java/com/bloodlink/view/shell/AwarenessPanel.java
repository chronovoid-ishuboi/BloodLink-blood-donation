package com.bloodlink.view.shell;

import com.bloodlink.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Why this app exists, stated in numbers that count themselves up.
 *
 * <h2>About these figures</h2>
 * Every figure here is attributed on screen to the body that publishes it,
 * and the wording says what it actually measures. That matters more than
 * usual for this subject: rounded, unsourced statistics about people dying
 * are exactly the kind of thing that spreads and turns out to be invented,
 * and an app asking someone to give blood should not be the place that
 * happens. Anything that could not be attributed was left out rather than
 * estimated.
 */
public final class AwarenessPanel extends VBox {

    private record Fact(String icon, long value, String suffix, String headline, String source, boolean accent) { }

    private static final List<Fact> FACTS = List.of(
            new Fact(Icons.DROPLET, 118, " million",
                    "Blood donations collected worldwide every year", "World Health Organization", true),
            new Fact(Icons.CLOCK, 2, " seconds",
                    "How often someone, somewhere, needs blood", "World Health Organization", false),
            new Fact(Icons.HEART, 900_000, " bags",
                    "Bangladesh's estimated annual need for blood", "Bangladesh Red Crescent Society", true),
            new Fact(Icons.USERS, 3, " lives",
                    "People a single donation can be separated into and help", "American Red Cross", false),
            new Fact(Icons.SHIELD_CHECK, 40, "%",
                    "Share of the world's blood collected in high-income countries, home to 16% of people",
                    "World Health Organization", false),
            new Fact(Icons.PLUS_CROSS, 56, " days",
                    "Minimum gap between whole-blood donations for most donors", "American Red Cross", false));

    private final List<CountUp> counters = new java.util.ArrayList<>();

    public AwarenessPanel() {
        setSpacing(16);

        Label heading = new Label("Why this matters");
        heading.getStyleClass().add("section-head-title");
        Label sub = new Label("Published figures, each attributed to its source.");
        sub.getStyleClass().add("section-head-sub");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3);
            grid.getColumnConstraints().add(column);
        }
        for (int i = 0; i < FACTS.size(); i++) {
            grid.add(card(FACTS.get(i)), i % 3, i / 3);
        }

        getChildren().addAll(new VBox(2, heading, sub), grid);
    }

    private Node card(Fact fact) {
        StackPane well = new StackPane(new Group(
                Icons.icon(fact.icon(), 17, fact.accent() ? "action-tile-icon-accent" : "action-tile-icon")));
        well.getStyleClass().add("action-tile-well");
        if (fact.accent()) well.getStyleClass().add("action-tile-well-accent");

        CountUp counter = new CountUp(fact.value(), fact.suffix());
        counter.getStyleClass().add("awareness-value");
        counters.add(counter);

        Label headline = new Label(fact.headline());
        headline.getStyleClass().add("awareness-headline");
        headline.setWrapText(true);

        Label source = new Label(fact.source());
        source.getStyleClass().add("awareness-source");
        source.setWrapText(true);

        VBox text = new VBox(3, counter, headline, source);
        VBox card = new VBox(12, well, text);
        card.getStyleClass().add("surface-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    /** Starts every counter. Called when the panel first scrolls into view. */
    public void play() {
        counters.forEach(CountUp::play);
    }

    /** Runs every counter again from zero. Called each time the section is opened. */
    public void replay() {
        counters.forEach(CountUp::replay);
    }
}
