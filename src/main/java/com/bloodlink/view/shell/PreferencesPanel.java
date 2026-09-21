package com.bloodlink.view.shell;

import com.bloodlink.service.LocationService;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.Motion;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Where you are and how far you will travel -- the two settings that decide
 * which requests reach you at all, kept together and away from the rest of
 * the profile form.
 */
public final class PreferencesPanel extends VBox {

    private static final int MIN_RADIUS_KM = 1;
    private static final int MAX_RADIUS_KM = 50;

    private final LocationService locationService = new LocationService();
    private final long userId;
    private final LocationBar locationBar;
    private final Label radiusValue = new Label();
    private final Slider radiusSlider = new Slider(MIN_RADIUS_KM, MAX_RADIUS_KM, 15);

    public PreferencesPanel(long userId, String district, Runnable onLocationChanged) {
        this.userId = userId;
        setSpacing(18);

        locationBar = new LocationBar(userId, district, onLocationChanged);

        VBox locationCard = new VBox(12,
                heading("Your location", "Donors and requesters are matched by how far apart you actually are."),
                locationBar);
        locationCard.getStyleClass().add("surface-card");

        getChildren().addAll(locationCard, radiusCard());
    }

    private Node radiusCard() {
        int saved = locationService.travelRadiusKm(userId);
        radiusSlider.setValue(saved);
        radiusValue.setText(saved + " km");
        radiusValue.getStyleClass().add("radius-value");

        radiusSlider.setMajorTickUnit(10);
        radiusSlider.setMinorTickCount(0);
        radiusSlider.setShowTickMarks(true);
        radiusSlider.setShowTickLabels(true);
        radiusSlider.valueProperty().addListener((obs, old, value) ->
                radiusValue.setText(value.intValue() + " km"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(10, heading("Travel radius",
                "Requests within this distance are ranked highest in your feed."), spacer, radiusValue);
        head.setAlignment(Pos.TOP_LEFT);

        Button save = new Button("Save radius");
        save.getStyleClass().add("button-pill");
        save.setOnAction(event -> save());
        Motion.attachHoverLift(save, 2);

        VBox card = new VBox(14, head, radiusSlider, save);
        card.getStyleClass().add("surface-card");
        return card;
    }

    private void save() {
        int radius = (int) Math.round(radiusSlider.getValue());
        try {
            locationService.saveTravelRadius(userId, radius);
            AlertUtil.info("Radius saved", "Requests within " + radius + " km now rank highest for you.");
        } catch (Exception e) {
            AlertUtil.error("Could not save the radius", e.getMessage());
        }
    }

    private Node heading(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-head-title");
        Label subLabel = new Label(subtitle);
        subLabel.getStyleClass().add("section-head-sub");
        subLabel.setWrapText(true);
        return new VBox(2, titleLabel, subLabel);
    }

    /** Re-reads the stored location, e.g. after the picker saves. */
    public void refresh() {
        locationBar.refresh();
    }
}
