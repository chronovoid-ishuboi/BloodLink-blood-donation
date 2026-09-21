package com.bloodlink.view.components;

import com.bloodlink.model.UserLocation;
import com.bloodlink.service.GeocodingService;
import com.bloodlink.service.LocationService;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.Motion;
import com.bloodlink.util.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import netscape.javascript.JSObject;

import java.util.List;
import java.util.Optional;

/**
 * Where the user establishes their real position and how far they are willing
 * to travel.
 *
 * <h2>Why there is no "use my GPS" button</h2>
 * A desktop app has no GPS, and the two things that impersonate one both
 * fail here. JavaFX's WebView exposes {@code navigator.geolocation} but has no
 * permission provider behind it -- probed from an {@code about:blank} origin
 * it is denied, and from a {@code file:} origin it never calls back at all.
 * IP geolocation, which this app used to rely on, resolved this machine to a
 * point roughly 100km from its actual address: that is the "takes me
 * somewhere I'm not" problem. A free WiFi-trilateration provider was tried
 * (BeaconDB, with 11 real nearby access points) and has no coverage for this
 * region.
 * <p>
 * So instead: search for the place by name, which is exact, free and needs no
 * key, then drag the pin if you want it to the metre. If a Google Geolocation
 * key is configured the app can additionally do true WiFi positioning -- see
 * {@link GeocodingService#hasWifiProvider()} -- and the button appears only
 * then, rather than being offered and quietly failing.
 */
public final class LocationPickerDialog {

    private static final int MIN_RADIUS_KM = 1;
    private static final int MAX_RADIUS_KM = 50;

    private final LocationService locationService = new LocationService();
    private final GeocodingService geocoding = new GeocodingService();
    private final long userId;
    private final String district;
    private final Runnable onSaved;

    private WebView web;
    private Label readout;
    private Label addressLabel;
    private Button saveButton;
    private Slider radiusSlider;
    private Label radiusValue;
    private Double pickedLat;
    private Double pickedLng;

    /** Kept as a field: the JS side holds this object, and a collected bridge stops callbacks silently. */
    private Bridge bridge;

    private LocationPickerDialog(long userId, String district, Runnable onSaved) {
        this.userId = userId;
        this.district = district;
        this.onSaved = onSaved;
    }

    public static void open(Window owner, long userId, String district, Runnable onSaved) {
        new LocationPickerDialog(userId, district, onSaved).show(owner);
    }

    /** Called from JavaScript whenever the pin moves. Must be public for the WebView bridge. */
    public final class Bridge {
        public void onPinMoved(double lat, double lng) {
            Platform.runLater(() -> {
                pickedLat = lat;
                pickedLng = lng;
                readout.setText(String.format("%.5f, %.5f", lat, lng));
                saveButton.setDisable(false);
                describeAsync(lat, lng);
            });
        }
    }

    private void show(Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle("Location and travel radius");

        web = new WebView();
        VBox.setVgrow(web, Priority.ALWAYS);

        VBox shell = new VBox(searchBar(), web, controls(stage));
        Scene scene = new Scene(shell, 820, 720);
        java.net.URL stylesheet = LocationPickerDialog.class.getResource("/com/bloodlink/css/theme.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        stage.setScene(scene);

        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - 820) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - 720) / 2);
        }

        java.net.URL page = LocationPickerDialog.class.getResource("/com/bloodlink/view/location_picker.html");
        if (page == null) {
            AlertUtil.error("Map unavailable", "The location picker page is missing from this build.");
            return;
        }
        web.getEngine().load(page.toExternalForm());
        web.getEngine().getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) installBridgeAndStart();
        });

        stage.show();
    }

    /** Place search -- the accurate path, since there is no GPS to ask. */
    private Node searchBar() {
        TextField search = new TextField();
        search.setPromptText("Search a place, e.g. Japan Garden City, Dhaka");
        HBox.setHgrow(search, Priority.ALWAYS);

        Button go = new Button("Search");
        go.getStyleClass().add("button-pill");
        Motion.attachHoverLift(go, 2);

        ContextMenu results = new ContextMenu();
        Runnable run = () -> {
            String query = search.getText();
            if (query == null || query.isBlank()) return;
            go.setDisable(true);
            BackgroundTasks.run(() -> geocoding.search(query),
                    places -> {
                        go.setDisable(false);
                        showResults(results, places, search);
                    },
                    error -> {
                        go.setDisable(false);
                        AlertUtil.error("Search failed", "Could not reach the place search service.");
                    });
        };
        go.setOnAction(event -> run.run());
        search.setOnAction(event -> run.run());

        HBox bar = new HBox(10, search, go);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 14, 12, 14));
        bar.getStyleClass().add("chat-composer");
        return bar;
    }

    private void showResults(ContextMenu menu, List<GeocodingService.Place> places, TextField anchor) {
        menu.getItems().clear();
        if (places.isEmpty()) {
            MenuItem none = new MenuItem("No matching place found");
            none.setDisable(true);
            menu.getItems().add(none);
        } else {
            for (GeocodingService.Place place : places) {
                MenuItem item = new MenuItem(place.displayName());
                item.setOnAction(event -> {
                    // Zoomed in close: a searched address is precise, and
                    // dropping the user at region zoom would hide that.
                    web.getEngine().executeScript(String.format(
                            "startAt(%f, %f, 16, true);", place.latitude(), place.longitude()));
                });
                menu.getItems().add(item);
            }
        }
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private Node controls(Stage stage) {
        readout = new Label("No pin placed yet");
        readout.getStyleClass().add("metric-value-sm");
        addressLabel = new Label("Search a place above, or click the map.");
        addressLabel.getStyleClass().add("section-head-sub");
        addressLabel.setWrapText(true);
        addressLabel.setMaxWidth(430);

        VBox where = new VBox(2, readout, addressLabel);

        radiusValue = new Label("15 km");
        radiusValue.getStyleClass().add("metric-value-sm");

        radiusSlider = new Slider(MIN_RADIUS_KM, MAX_RADIUS_KM, 15);
        radiusSlider.setMajorTickUnit(10);
        radiusSlider.setMinorTickCount(0);
        radiusSlider.setShowTickMarks(true);
        radiusSlider.setShowTickLabels(true);
        radiusSlider.setPrefWidth(320);
        radiusSlider.valueProperty().addListener((obs, old, value) -> {
            int km = value.intValue();
            radiusValue.setText(km + " km");
            web.getEngine().executeScript("setRadiusKm(" + km + ");");
        });

        Label radiusCaption = new Label("Requests within this distance are ranked highest for you.");
        radiusCaption.getStyleClass().add("section-head-sub");

        HBox radiusHead = new HBox(10, new Label("TRAVEL / NOTIFICATION RADIUS"), new Region(), radiusValue);
        radiusHead.getChildren().get(0).getStyleClass().add("field-label");
        HBox.setHgrow(radiusHead.getChildren().get(1), Priority.ALWAYS);
        radiusHead.setAlignment(Pos.CENTER_LEFT);

        VBox radiusBox = new VBox(6, radiusHead, radiusSlider, radiusCaption);

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("button-ghost");
        cancel.setOnAction(event -> stage.close());

        saveButton = new Button("Save location and radius");
        saveButton.getStyleClass().add("button-pill");
        saveButton.setDisable(true);
        saveButton.setOnAction(event -> save(stage));
        Motion.attachHoverLift(saveButton, 2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancel, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(14, where, radiusBox, actions);
        panel.setPadding(new Insets(16, 18, 16, 18));
        panel.getStyleClass().add("chat-composer");
        return panel;
    }

    private void installBridgeAndStart() {
        bridge = new Bridge();
        JSObject window = (JSObject) web.getEngine().executeScript("window");
        window.setMember("bridge", bridge);

        int savedRadius = locationService.travelRadiusKm(userId);
        radiusSlider.setValue(savedRadius);
        radiusValue.setText(savedRadius + " km");
        web.getEngine().executeScript("setRadiusKm(" + savedRadius + ");");

        // Only pin a starting point that is genuinely this user's position: a
        // district estimate is a place to start looking, not their answer.
        Optional<UserLocation> existing = locationService.resolvedLocation(userId, district, null);
        if (existing.isPresent()) {
            UserLocation location = existing.get();
            boolean isTheirs = location.source() != UserLocation.Source.DISTRICT;
            web.getEngine().executeScript(String.format("startAt(%f, %f, %d, %s);",
                    location.latitude(), location.longitude(), isTheirs ? 15 : 11, isTheirs));
        }
    }

    /** Shows what address the pin actually landed on, so a mis-drop is obvious before saving. */
    private void describeAsync(double lat, double lng) {
        addressLabel.setText("Looking up this address...");
        BackgroundTasks.run(() -> geocoding.describe(lat, lng),
                address -> addressLabel.setText(address.orElse("Address unavailable for this point.")),
                error -> addressLabel.setText("Address lookup unavailable."));
    }

    private void save(Stage stage) {
        if (pickedLat == null || pickedLng == null) return;
        int radius = (int) Math.round(radiusSlider.getValue());
        try {
            locationService.saveLocation(userId, pickedLat, pickedLng, UserLocation.Source.MAP_PIN);
            locationService.saveTravelRadius(userId, radius);
            stage.close();
            AlertUtil.info("Location saved",
                    "Other users now see this exact point for you, and requests within "
                            + radius + " km rank highest in your feed.");
            if (onSaved != null) onSaved.run();
        } catch (Exception e) {
            AlertUtil.error("Could not save location", e.getMessage());
        }
    }

    public static void openForCurrentUser(Node anyNodeInScene, String district, Runnable onSaved) {
        var user = SessionManager.getInstance().getCurrentUser();
        if (user == null) return;
        Window owner = anyNodeInScene.getScene() == null ? null : anyNodeInScene.getScene().getWindow();
        open(owner, user.getId(), district, onSaved);
    }
}
