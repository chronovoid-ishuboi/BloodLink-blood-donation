package com.bloodlink.view.shell;

import com.bloodlink.util.Icons;
import com.bloodlink.util.Motion;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A page split into sections you step into, rather than one long scroll of
 * everything.
 * <p>
 * Used by Profile, where the alternative is a single column mixing your name,
 * your medical screening answers, your password and deleting your account --
 * settings of wildly different consequence sitting next to each other with
 * nothing to separate them. Each section is its own destination with its own
 * description, so the weight of what you are about to change is visible
 * before you get there.
 */
public final class SectionSwitcher extends VBox {

    private record Section(String title, String subtitle, String icon, Node content) { }

    private final List<Section> sections = new ArrayList<>();
    private final List<HBox> rows = new ArrayList<>();
    private final VBox menu = new VBox(12);
    private final StackPane contentHost = new StackPane();
    private int current = -1;

    public SectionSwitcher() {
        setSpacing(18);
        getChildren().addAll(menu, contentHost);
    }

    public void addSection(String title, String subtitle, String icon, Node content) {
        int index = sections.size();
        sections.add(new Section(title, subtitle, icon, content));

        StackPane well = new StackPane(new Group(Icons.icon(icon, 17, "action-tile-icon")));
        well.getStyleClass().add("action-tile-well");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("action-tile-title");
        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("action-tile-sub");
        subLabel.setWrapText(true);

        VBox text = new VBox(2, titleLabel, subLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(14, well, text, spacer,
                new Group(Icons.icon(Icons.CHEVRON_RIGHT, 13, "section-row-chevron")));
        row.getStyleClass().add("section-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOnMouseClicked(event -> select(index));
        Motion.attachHoverLift(row, 2);

        rows.add(row);
        menu.getChildren().add(row);

        content.setVisible(false);
        content.setManaged(false);
        contentHost.getChildren().add(content);

        if (index == 0) select(0);
    }

    public void select(int index) {
        if (index < 0 || index >= sections.size() || index == current) return;
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).getStyleClass().remove("section-row-active");
            Node content = sections.get(i).content();
            content.setVisible(false);
            content.setManaged(false);
        }
        rows.get(index).getStyleClass().add("section-row-active");

        Node content = sections.get(index).content();
        content.setVisible(true);
        content.setManaged(true);
        content.setOpacity(0);
        content.setTranslateY(12);
        javafx.animation.FadeTransition fade =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), content);
        fade.setToValue(1);
        javafx.animation.TranslateTransition rise =
                new javafx.animation.TranslateTransition(javafx.util.Duration.millis(220), content);
        rise.setToY(0);
        new javafx.animation.ParallelTransition(fade, rise).play();

        current = index;
    }
}
