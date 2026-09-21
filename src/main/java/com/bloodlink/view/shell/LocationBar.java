package com.bloodlink.view.shell;

import com.bloodlink.model.UserLocation;
import com.bloodlink.service.GeocodingService;
import com.bloodlink.service.LocationService;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.Icons;
import com.bloodlink.util.Motion;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * The always-visible "where you are" strip at the top of the home page, in
 * the spirit of a delivery app's address bar: your current location, how
 * precise it is, and a prominent control to change it.
 * <p>
 * Front and centre rather than buried in settings because it is the single
 * setting that decides whether this app works for you. A donor whose
 * location is a district estimate is matched by a point shared with everyone
 * else in that district; a donor who has pinned where they actually are can
 * be matched to the hospital ten minutes away. Making that visible -- and
 * visibly wrong when it is a guess -- is what gets it corrected.
 */
public final class LocationBar extends HBox {

    private final LocationService locationService = new LocationService();
    private final GeocodingService geocoding = new GeocodingService();
    private final long userId;
    private final String district;
    private final Runnable onChanged;

    private final Label placeLabel = new Label("Locating...");
    private final Label precisionLabel = new Label();

    public LocationBar(long userId, String district, Runnable onChanged) {
        this.userId = userId;
        this.district = district;
        this.onChanged = onChanged;

        getStyleClass().add("location-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(14);

        StackPane pin = new StackPane(new Group(Icons.icon(Icons.MAP_PIN, 17, "location-bar-pin")));
        pin.getStyleClass().add("location-bar-pin-well");

        placeLabel.getStyleClass().add("location-bar-place");
        precisionLabel.getStyleClass().add("location-bar-precision");
        VBox text = new VBox(1, placeLabel, precisionLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button update = new Button("Update my location");
        update.getStyleClass().add("location-bar-button");
        update.setGraphic(new Group(Icons.icon(Icons.MAP_PIN, 13, "location-bar-button-icon")));
        update.setOnAction(event -> openPicker());
        Motion.attachHoverLift(update, 2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(pin, text, spacer, update);
        refresh();
    }

    /** Re-reads the stored location and re-describes it. Safe to call after the picker saves. */
    public void refresh() {
        BackgroundTasks.run(
                () -> {
                    Optional<UserLocation> location = locationService.resolvedLocation(userId, district, null);
                    if (location.isEmpty()) return new Resolved(null, "Location not set", "Set it so donors near you can be matched");
                    UserLocation resolved = location.get();
                    // Only look up a street address for a position precise
                    // enough for one to mean anything. Reverse-geocoding a
                    // district centroid would print a confident-looking
                    // address for a point nobody is actually at.
                    String place = resolved.source().isPrecise()
                            ? geocoding.describe(resolved.latitude(), resolved.longitude()).orElse(district)
                            : district;
                    return new Resolved(resolved, shorten(place), describePrecision(resolved));
                },
                result -> {
                    placeLabel.setText(result.place());
                    precisionLabel.setText(result.precision());
                    precisionLabel.getStyleClass().removeAll("location-bar-precision-warn");
                    if (result.location() == null || !result.location().source().isPrecise()) {
                        precisionLabel.getStyleClass().add("location-bar-precision-warn");
                    }
                },
                error -> {
                    placeLabel.setText(district == null ? "Location unavailable" : district);
                    precisionLabel.setText("Could not read your saved location");
                });
    }

    private record Resolved(UserLocation location, String place, String precision) { }

    private String describePrecision(UserLocation location) {
        return switch (location.source()) {
            case MAP_PIN -> "Exact location you set";
            case IP -> "Approximate — tap update to set it exactly";
            case DISTRICT -> "District estimate only — tap update to set it exactly";
        };
    }

    /** Full Nominatim names run to eight comma-separated parts; the first three locate you. */
    private String shorten(String place) {
        if (place == null || place.isBlank()) return district == null ? "Location not set" : district;
        String[] parts = place.split(",");
        if (parts.length <= 3) return place.trim();
        return (parts[0].trim() + ", " + parts[1].trim() + ", " + parts[2].trim());
    }

    private void openPicker() {
        Node anchor = this;
        com.bloodlink.view.components.LocationPickerDialog.openForCurrentUser(anchor, district, () -> {
            refresh();
            if (onChanged != null) onChanged.run();
        });
    }
}
