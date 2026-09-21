package com.bloodlink.controller;

import com.bloodlink.dao.HospitalDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;
import com.bloodlink.service.*;
import com.bloodlink.util.*;
import com.bloodlink.view.components.EmptyState;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import com.bloodlink.util.GeoIPService;

public final class RequesterDashboardController {

    /** Destination order in the rail; the PageHost's children are in the same order. */
    private static final int PAGE_HOME = 0, PAGE_NEW_REQUEST = 1, PAGE_REQUESTS = 2, PAGE_FAVORITES = 3,
            PAGE_LEADERBOARD = 4, PAGE_NOTIFICATIONS = 5, PAGE_NEWS = 6, PAGE_HELP = 7,
            PAGE_PROFILE = 8, PAGE_ABOUT = 9;

    @FXML private javafx.scene.layout.StackPane navRailHost;
    @FXML private com.bloodlink.view.shell.PageHost pageHost;

    @FXML private javafx.scene.layout.VBox homeHeroHost;
    @FXML private javafx.scene.layout.HBox homeMetricsRow;
    @FXML private javafx.scene.layout.VBox homeActionsHost;
    @FXML private javafx.scene.layout.VBox newRequestHeroHost;
    @FXML private javafx.scene.layout.VBox newRequestLocationHost;
    @FXML private javafx.scene.layout.VBox requestsHeroHost;
    @FXML private javafx.scene.layout.VBox favoritesHeroHost;
    @FXML private javafx.scene.layout.VBox leaderboardHeroHost;
    @FXML private javafx.scene.layout.VBox leaderboardHost;
    @FXML private javafx.scene.layout.VBox notificationsHeroHost;
    @FXML private javafx.scene.layout.VBox newsHeroHost;
    @FXML private javafx.scene.layout.VBox newsHost;
    @FXML private javafx.scene.layout.VBox awarenessHost;
    @FXML private javafx.scene.layout.VBox helpHeroHost;
    @FXML private javafx.scene.layout.VBox profileHeroHost;
    @FXML private javafx.scene.layout.VBox aboutHeroHost;
    @FXML private javafx.scene.layout.VBox aboutHost;
    @FXML private ScrollPane pageHome;
    @FXML private ScrollPane pageNewRequest;
    @FXML private ScrollPane pageRequests;
    @FXML private ScrollPane pageFavorites;
    @FXML private ScrollPane pageLeaderboard;
    @FXML private ScrollPane pageNotifications;
    @FXML private ScrollPane pageNews;
    @FXML private ScrollPane pageHelp;
    @FXML private ScrollPane pageProfile;
    @FXML private ScrollPane pageAbout;

    @FXML private Label openRequestsLabel;
    @FXML private Label unitsSecuredLabel;
    @FXML private Label savedDonorsLabel;
    @FXML private Label fulfilledLabel;

    @FXML private ComboBox<BloodGroup> bloodGroupCombo;
    @FXML private Spinner<Integer> unitsSpinner;
    @FXML private ComboBox<Urgency> urgencyCombo;
    @FXML private ComboBox<Hospital> hospitalCombo;
    @FXML private TextField requestDistrictField;
    @FXML private DatePicker deadlinePicker;
    @FXML private TextArea notesArea;
    @FXML private Label requestMessageLabel;

    @FXML private TableView<BloodRequest> requestTable;
    @FXML private TableColumn<BloodRequest, Long> requestIdColumn;
    @FXML private TableColumn<BloodRequest, BloodGroup> requestBloodColumn;
    @FXML private TableColumn<BloodRequest, Integer> unitsColumn;
    @FXML private TableColumn<BloodRequest, String> progressColumn;
    @FXML private TableColumn<BloodRequest, Urgency> urgencyColumn;
    @FXML private TableColumn<BloodRequest, String> hospitalColumn;
    @FXML private TableColumn<BloodRequest, String> districtColumn;
    @FXML private TableColumn<BloodRequest, LocalDate> deadlineColumn;
    @FXML private TableColumn<BloodRequest, RequestStatus> statusColumn;

    @FXML private ListView<MatchCandidate> matchList;
    @FXML private WebView mapView;
    
    @FXML private TableView<RequestStatusHistoryEntry> historyTable;
    @FXML private TableColumn<RequestStatusHistoryEntry, RequestStatus> historyFromColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, RequestStatus> historyToColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, String> historyActorColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, String> historyNoteColumn;
    @FXML private TableColumn<RequestStatusHistoryEntry, LocalDateTime> historyTimeColumn;

    @FXML private ListView<Notification> notificationList;
    @FXML private ListView<FavoriteDonorView> favoriteDonorList;
    @FXML private javafx.scene.layout.VBox helpContainer;
    @FXML private TextField nameField;
    @FXML private TextField phoneField;
    @FXML private TextField profileDistrictField;
    @FXML private TextArea addressArea;
    @FXML private javafx.scene.image.ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private Button uploadPhotoButton;
    @FXML private Button removePhotoButton;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileMessageLabel;
    @FXML private Label nidLabel;
    @FXML private Label emailLabel;
    @FXML private TextField guardianNameField;
    @FXML private TextField guardianPhoneField;

    private final RequestDAO requestDAO = new RequestDAO();
    private final HospitalDAO hospitalDAO = new HospitalDAO();
    private final RequestService requestService = new RequestService();
    private final MatchingService matchingService = new MatchingService();
    private final NotificationService notificationService = new NotificationService();
    private final ProfileService profileService = new ProfileService();
    private final ReviewService reviewService = new ReviewService();
    private final LocationService locationService = new LocationService();
    private final FavoriteDonorService favoriteDonorService = new FavoriteDonorService();
    private Requester requester;
    private com.bloodlink.view.shell.NavRail navRail;
    private com.bloodlink.view.components.HelpFaqView helpView;
    private com.bloodlink.view.shell.LeaderboardPanel leaderboardPanel;
    private com.bloodlink.view.shell.NewsCarousel newsCarousel;
    private com.bloodlink.view.shell.AwarenessPanel awarenessPanel;
    private Timeline refreshTimeline;
    private boolean suppressHospitalSearch = false;
    private volatile boolean refreshInFlight = false;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Requester currentRequester)) {
            throw new IllegalStateException("RequesterDashboardController loaded without an active Requester session.");
        }
        this.requester = currentRequester;
        buildShell();
        helpView = new com.bloodlink.view.components.HelpFaqView(
                null, com.bloodlink.view.components.BloodCompatibilityView.Mode.RECIPIENT);
        helpContainer.getChildren().add(helpView);
        PushClient.getInstance().connect(requester.getId());
        PushClient.getInstance().onRefresh(this::refreshAll);
        bloodGroupCombo.getItems().setAll(BloodGroup.values());
        urgencyCombo.getItems().setAll(Urgency.values());
        urgencyCombo.setValue(Urgency.URGENT);
        unitsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 1));
        requestDistrictField.setText(requester.getDistrict());
        deadlinePicker.setValue(LocalDate.now());
        configureHospitalPicker();
        configureTables();
        populateProfile();
        favoriteDonorList.setPlaceholder(emptyState("No favorite donors yet",
                "Star a matched donor to save them here for next time."));
        favoriteDonorList.setCellFactory(lv -> new com.bloodlink.view.components.FavoriteDonorCell(this::refreshAll));
        requestTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            loadMatches(newValue);
            updateMapMarkers();
            // The Help tab's compatibility chart answers "who can give ME blood",
            // so it follows whichever request is currently selected rather than
            // showing a single fixed group -- a requester can have requests open
            // for more than one blood group at a time.
            if (newValue == null || oldValue == null || newValue.bloodGroup() != oldValue.bloodGroup()) {
                helpView.setGroup(newValue == null ? null : newValue.bloodGroup());
            }
        });
        notificationList.setOnMouseClicked(event -> markSelectedNotificationRead());
        refreshAll();
        int seconds = Math.max(5, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
        
        initializeMap();
    }

    /** Assembles the sidebar shell: rail destinations, per-page heroes, home quick actions and reveals. */
    private void buildShell() {
        com.bloodlink.view.shell.NavRail rail = new com.bloodlink.view.shell.NavRail(
                "BloodLink", requester.getFullName(), "Requester", requester.getId(), this::logout);
        rail.addSection("Overview");
        rail.addItem("Home", Icons.HOME);
        rail.addItem("New request", Icons.PLUS_CIRCLE);
        rail.addItem("My requests", Icons.LIST);
        rail.addSection("People");
        rail.addItem("Favourite donors", Icons.STAR);
        rail.addItem("Leaderboard", Icons.CHART);
        rail.addItem("Notifications", Icons.BELL);
        rail.addSection("More");
        rail.addItem("News and impact", Icons.PULSE);
        rail.addItem("Help", Icons.SHIELD_CHECK);
        rail.addItem("Profile", Icons.USER);
        rail.addItem("About", Icons.USERS);
        rail.setOnSelect(index -> { pageHost.show(index); onPageShown(index); });
        navRail = rail;
        navRailHost.getChildren().add(rail);

        pageHost.initialize();
        rail.select(PAGE_HOME);

        homeHeroHost.getChildren().add(com.bloodlink.view.shell.UI.heroWithArt(
                "Welcome back, " + firstName(requester.getFullName()),
                "Post an emergency request and BloodLink ranks eligible donors near your hospital in seconds.",
                new com.bloodlink.view.shell.ImageSlot(
                        com.bloodlink.view.shell.ImageSlot.Names.REQUESTER_HERO, "Hero image", 340, 210),
                com.bloodlink.view.shell.UI.pill("Matching is live in " + requester.getDistrict())));

        newRequestLocationHost.getChildren().add(new com.bloodlink.view.shell.LocationBar(
                requester.getId(), requester.getDistrict(), this::updateMapMarkers));

        newRequestHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("New request",
                "The more precise the hospital and deadline, the better BloodLink can rank donors for you."));
        requestsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("My requests",
                "Select a request to see its ranked donors, their distance, and its full lifecycle."));
        favoritesHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Favourite donors",
                "Donors you starred. Saving a donor also awards them bonus points."));
        leaderboardHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Leaderboard",
                "Donors ranked on verified donations only. Their standing is public; their contact details are not."));
        leaderboardPanel = new com.bloodlink.view.shell.LeaderboardPanel();
        leaderboardHost.getChildren().add(leaderboardPanel);

        notificationsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Notifications",
                "Donor responses, handshake updates and request outcomes."));
        newsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("News and impact",
                "Stories worth reading, and the published figures behind them."));
        newsCarousel = new com.bloodlink.view.shell.NewsCarousel(1000, 420);
        newsHost.getChildren().addAll(
                com.bloodlink.view.shell.UI.sectionHead("In the news", ""), newsCarousel);
        awarenessPanel = new com.bloodlink.view.shell.AwarenessPanel();
        awarenessHost.getChildren().add(awarenessPanel);

        helpHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Help",
                "The compatibility chart for your selected request, plus the usual questions."));
        profileHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Profile",
                "What matched donors see when they accept your request."));
        aboutHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("About",
                "Who built BloodLink, what it is for, and what it does with your data."));
        aboutHost.getChildren().add(new com.bloodlink.view.shell.AboutPanel());

        homeActionsHost.getChildren().addAll(
                com.bloodlink.view.shell.UI.sectionHead("Jump straight in",
                        "The things requesters do most, one click from here."),
                com.bloodlink.view.shell.UI.equalRow(16,
                        com.bloodlink.view.shell.UI.actionTile(Icons.PLUS_CIRCLE, "Request blood",
                                "Post a new emergency", true, () -> navRail.select(PAGE_NEW_REQUEST)),
                        com.bloodlink.view.shell.UI.actionTile(Icons.LIST, "My requests",
                                "Track donors and status", false, () -> navRail.select(PAGE_REQUESTS))),
                com.bloodlink.view.shell.UI.equalRow(16,
                        com.bloodlink.view.shell.UI.actionTile(Icons.STAR, "Favourite donors",
                                "People you saved", false, () -> navRail.select(PAGE_FAVORITES)),
                        com.bloodlink.view.shell.UI.actionTile(Icons.SHIELD_CHECK, "Who can donate to me?",
                                "Your compatibility chart", false, () -> navRail.select(PAGE_HELP))));

        com.bloodlink.view.shell.Reveal reveal = new com.bloodlink.view.shell.Reveal(pageHome);
        reveal.watchAll(70, homeMetricsRow, homeActionsHost);

        com.bloodlink.view.shell.Reveal newsReveal = new com.bloodlink.view.shell.Reveal(pageNews);
        newsReveal.watchThen(awarenessHost, 0, awarenessPanel::play);
    }

    /** Restarts a page's animation each time that page is opened. See the donor controller for why. */
    private void onPageShown(int index) {
        if (index == PAGE_NEWS && awarenessPanel != null) awarenessPanel.replay();
    }

    /** First name only, so the greeting reads like a greeting rather than a record lookup. */
    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        return fullName.trim().split("\\s+")[0];
    }

    private void initializeMap() {
        if (mapView != null) {
            java.net.URL mapUrl = getClass().getResource("/com/bloodlink/view/map.html");
            if (mapUrl != null) {
                mapView.getEngine().load(mapUrl.toExternalForm());
                mapView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        invalidateMapSize();
                        updateMapMarkers();
                        javafx.application.Platform.runLater(() -> {
                            invalidateMapSize();
                            updateMapMarkers();
                        });
                    }
                });

                // Auto-invalidate map size when WebView or parent container resizes
                mapView.widthProperty().addListener((obs, oldVal, newVal) -> invalidateMapSize());
                mapView.heightProperty().addListener((obs, oldVal, newVal) -> invalidateMapSize());

                // A WebView laid out while its page was hidden comes back with a
                // stale size, so the map is re-invalidated when this page shows.
                pageRequests.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
                    if (isVisible) javafx.application.Platform.runLater(this::invalidateMapSize);
                });

                matchList.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
                    updateMapMarkers();
                });
            }
        }
    }

    private void invalidateMapSize() {
        if (mapView != null && mapView.getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
            try {
                mapView.getEngine().executeScript("if (typeof resizeMap === 'function') resizeMap();");
            } catch (Exception ignored) {}
        }
    }

    private void updateMapMarkers() {
        if (mapView == null || mapView.getEngine().getLoadWorker().getState() != javafx.concurrent.Worker.State.SUCCEEDED) return;
        invalidateMapSize();
        mapView.getEngine().executeScript("clearMarkers();");

        BloodRequest selectedRequest = requestTable.getSelectionModel().getSelectedItem();
        MatchCandidate selectedDonor = matchList.getSelectionModel().getSelectedItem();

        boolean hasHospital = false;
        double hLat = 0, hLng = 0;
        if (selectedRequest != null) {
            if (selectedRequest.hasKnownHospitalLocation()) {
                hLat = selectedRequest.hospitalLatitude();
                hLng = selectedRequest.hospitalLongitude();
                hasHospital = true;
            } else if (selectedRequest.hospitalName() != null) {
                try {
                    Hospital h = hospitalDAO.findByName(selectedRequest.hospitalName());
                    if (h != null) {
                        hLat = h.latitude();
                        hLng = h.longitude();
                        hasHospital = true;
                    }
                } catch (SQLException ignored) {}
            }
            if (!hasHospital && selectedRequest.district() != null) {
                java.util.Optional<double[]> pt = locationService.districtReferencePoint(selectedRequest.district());
                if (pt.isPresent()) {
                    hLat = pt.get()[0];
                    hLng = pt.get()[1];
                    hasHospital = true;
                }
            }
            if (hasHospital) {
                String title = com.bloodlink.util.JsStrings.escape(
                        selectedRequest.hospitalName() != null ? selectedRequest.hospitalName() : "Request Hospital");
                String subtitle = com.bloodlink.util.JsStrings.escape(selectedRequest.district());
                mapView.getEngine().executeScript(String.format("addMarker(%f, %f, '%s', '%s', '%s');",
                        hLat, hLng, title, subtitle, "Hospital"));
            }
        }

        if (selectedDonor != null && selectedDonor.donorName() != null) {
            double dLat = 23.8103, dLng = 90.4125;
            // The donor's real position when they have set one, falling back to
            // the district estimate otherwise. The subtitle carries which of
            // the two this is, because "this donor is 3km away" and "someone in
            // this district is 3km away" are very different claims to act on.
            String locationNote = selectedDonor.district();
            java.util.Optional<com.bloodlink.model.UserLocation> resolved =
                    locationService.resolvedLocation(selectedDonor.donorId(), selectedDonor.district(), null);
            if (resolved.isPresent()) {
                com.bloodlink.model.UserLocation location = resolved.get();
                dLat = location.latitude();
                dLng = location.longitude();
                locationNote = location.source() == com.bloodlink.model.UserLocation.Source.DISTRICT
                        ? selectedDonor.district() + " · district estimate"
                        : selectedDonor.district() + " · " + location.source().getLabel();
            }
            String title = com.bloodlink.util.JsStrings.escape(selectedDonor.donorName());
            String subtitle = com.bloodlink.util.JsStrings.escape(locationNote);
            mapView.getEngine().executeScript(String.format("addMarker(%f, %f, '%s', '%s', '%s');",
                    dLat, dLng, title, subtitle, "Donor"));

            if (hasHospital) {
                mapView.getEngine().executeScript("fitBounds();");
            } else {
                mapView.getEngine().executeScript(String.format("setView(%f, %f, 13);", dLat, dLng));
            }
        } else if (hasHospital) {
            mapView.getEngine().executeScript(String.format("setView(%f, %f, 13);", hLat, hLng));
        }
    }

    /** Opens the pin picker so matched donors see exactly where this requester is. */
    @FXML private void setExactLocation() {
        com.bloodlink.view.components.LocationPickerDialog.openForCurrentUser(
                matchList, requester.getDistrict(), this::updateMapMarkers);
    }

    /**
     * Saves a coarse network-derived position, labelled as approximate. See the
     * donor dashboard's equivalent for why it is stored rather than only drawn.
     */
    @FXML private void detectArea() {
        new Thread(() -> {
            Optional<GeoIPService.GeoLocation> locOpt = GeoIPService.detectLocation();
            javafx.application.Platform.runLater(() -> {
                if (locOpt.isEmpty()) {
                    com.bloodlink.util.AlertUtil.error("Detection Failed", "Could not detect area from IP.");
                    return;
                }
                GeoIPService.GeoLocation loc = locOpt.get();
                try {
                    locationService.saveLocation(requester.getId(), loc.lat(), loc.lon(),
                            com.bloodlink.model.UserLocation.Source.IP);
                } catch (SQLException e) {
                    com.bloodlink.util.AlertUtil.error("Could not save area", e.getMessage());
                    return;
                }
                updateMapMarkers();
                com.bloodlink.util.AlertUtil.info("Area detected",
                        "Saved an approximate location: " + loc.city() + ", " + loc.region()
                                + ".\n\nThis is a network estimate and can be well off. Use "
                                + "\"Set my exact location\" to drop a precise pin.");
            });
        }).start();
    }


    /**
     * Searchable hospital picker: an editable ComboBox backed by HospitalDAO.search().
     * Selecting a real entry links the request to a curated hospital (enables real
     * distance matching); typing a hospital that is not in the directory is still
     * accepted as free text, matching the previous behavior, but that request will
     * show "distance unavailable" until it is later matched to a known hospital.
     */
    private void configureHospitalPicker() {
        hospitalCombo.setEditable(true);
        hospitalCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Hospital hospital) { return hospital == null ? "" : hospital.name(); }
            @Override public Hospital fromString(String text) {
                if (text == null || text.isBlank()) return null;
                for (Hospital h : hospitalCombo.getItems()) {
                    if (h.name().equalsIgnoreCase(text.trim())) return h;
                }
                return null;
            }
        });
        hospitalCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Hospital hospital, boolean empty) {
                super.updateItem(hospital, empty);
                if (empty || hospital == null) {
                    setText(null);
                } else {
                    setText(hospital.toString());
                }
            }
        });
        searchHospitals("");
        hospitalCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (suppressHospitalSearch) return;
            Hospital selected = hospitalCombo.getSelectionModel().getSelectedItem();
            if (selected != null && selected.name().equalsIgnoreCase(newText != null ? newText.trim() : "")) {
                return;
            }
            searchHospitals(newText != null ? newText.trim() : "");
        });
        hospitalCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) return;
            suppressHospitalSearch = true;
            try {
                hospitalCombo.getEditor().setText(newValue.name());
                if (newValue.district() != null && !newValue.district().isBlank()) {
                    requestDistrictField.setText(newValue.district());
                }
            } finally {
                suppressHospitalSearch = false;
            }
        });
    }

    private void searchHospitals(String query) {
        if (suppressHospitalSearch) return;
        suppressHospitalSearch = true;
        try {
            java.util.List<Hospital> results = hospitalDAO.search(query, 15);
            Hospital currentSelection = hospitalCombo.getSelectionModel().getSelectedItem();
            hospitalCombo.getItems().setAll(results);
            if (currentSelection != null) {
                for (Hospital h : results) {
                    if (h.id() == currentSelection.id()) {
                        hospitalCombo.getSelectionModel().select(h);
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            // Search-as-you-type failure should not block the requester from typing a hospital name manually.
        } finally {
            suppressHospitalSearch = false;
        }
    }

    private void configureTables() {
        requestIdColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().id()));
        requestBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        unitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsNeeded()));
        progressColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().unitsFulfilled() + " / " + v.getValue().unitsNeeded() + " units"));
        urgencyColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().urgency()));
        hospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        districtColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().district()));
        deadlineColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().deadline()));
        statusColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().status()));
        historyFromColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().fromStatus()));
        historyToColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().toStatus()));
        historyActorColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().changedByName() == null ? "System" : v.getValue().changedByName()));
        historyNoteColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().note()));
        historyTimeColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().changedAt()));

        urgencyColumn.setCellFactory(ChipTableCells.forValues());
        statusColumn.setCellFactory(ChipTableCells.forValues());
        historyFromColumn.setCellFactory(ChipTableCells.forValues());
        historyToColumn.setCellFactory(ChipTableCells.forValues());

        requestTable.setPlaceholder(emptyState("No requests yet",
                "Submit one from the New Request tab and matched donors will appear here."));
        matchList.setPlaceholder(emptyState("No donors matched yet",
                "Select one of your requests above to see the donors ranked for it."));
        matchList.setCellFactory(lv -> new com.bloodlink.view.components.RequesterMatchCell(() -> {
            BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
            return selected == null ? null : selected.id();
        }));
        historyTable.setPlaceholder(emptyState("Nothing to show",
                "Select a request above to follow how its status changed."));
        notificationList.setPlaceholder(emptyState("You are all caught up",
                "Updates about your requests and matched donors land here."));
    }

    private javafx.scene.Node emptyState(String text) {
        return EmptyState.of(text);
    }

    private javafx.scene.Node emptyState(String title, String hint) {
        return EmptyState.of(title, hint);
    }

    private void populateProfile() {
        nameField.setText(requester.getFullName()); phoneField.setText(requester.getPhone());
        profileDistrictField.setText(requester.getDistrict()); addressArea.setText(requester.getAddress());
        nidLabel.setText(requester.getNidNumber() != null ? requester.getNidNumber() : "Not Provided");
        emailLabel.setText(requester.getEmail() != null ? requester.getEmail() : "Not Provided");
        guardianNameField.setText(requester.getGuardianName());
        guardianPhoneField.setText(requester.getGuardianPhone());
        applyProfilePhoto();
    }

    /**
     * Same pattern as DonorDashboardController's version: loaded via the dedicated
     * ProfileService.loadPhoto(), never as part of the routine session fetch. Falls
     * back to an initials badge (no image asset needed) when no photo is set.
     */
    private void applyProfilePhoto() {
        java.util.Optional<byte[]> photo = profileService.loadPhoto(requester.getId());
        if (photo.isPresent()) {
            try {
                profilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(photo.get())));
                profilePhotoView.setClip(new javafx.scene.shape.Circle(42, 42, 42));
                profilePhotoView.setVisible(true);
                profilePhotoView.setManaged(true);
                profileInitialsLabel.setVisible(false);
                profileInitialsLabel.setManaged(false);
                return;
            } catch (RuntimeException e) {
                // Stored bytes weren't a decodable image -- fall through to the initials badge below.
            }
        }
        profilePhotoView.setImage(null);
        profilePhotoView.setVisible(false);
        profilePhotoView.setManaged(false);
        profileInitialsLabel.setVisible(true);
        profileInitialsLabel.setManaged(true);
        profileInitialsLabel.setText(initialsOf(requester.getFullName()));
    }

    private String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    @FXML private void uploadPhoto() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select a profile photo");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        java.io.File file = chooser.showOpenDialog(uploadPhotoButton.getScene().getWindow());
        if (file == null) return;
        BackgroundTasks.run(
                () -> profileService.updatePhoto(requester.getId(), java.nio.file.Files.readAllBytes(file.toPath())),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Could not read that file: " + error.getMessage()));
    }

    @FXML private void removePhoto() {
        BackgroundTasks.run(
                () -> profileService.updatePhoto(requester.getId(), null),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Photo could not be removed: " + error.getMessage()));
    }

    @FXML private void createRequest() {
        String typedHospitalName = hospitalCombo.getEditor().getText() != null
                ? hospitalCombo.getEditor().getText().trim() : "";
        Hospital selectedHospital = hospitalCombo.getSelectionModel().getSelectedItem();
        if (selectedHospital == null) {
            selectedHospital = hospitalCombo.getValue();
        }
        Long hospitalId = null;
        if (selectedHospital != null && selectedHospital.name().equalsIgnoreCase(typedHospitalName)) {
            hospitalId = selectedHospital.id();
        } else if (!typedHospitalName.isBlank()) {
            try {
                Hospital found = hospitalDAO.findByName(typedHospitalName);
                if (found != null) hospitalId = found.id();
            } catch (SQLException ignored) {}
        }
        ServiceResult<Long> result = requestService.create(requester.getId(), bloodGroupCombo.getValue(), unitsSpinner.getValue(),
                urgencyCombo.getValue(), typedHospitalName, hospitalId, requestDistrictField.getText(), deadlinePicker.getValue(), notesArea.getText());
        requestMessageLabel.setText(result.message());
        if (result.success()) {
            suppressHospitalSearch = true;
            try {
                hospitalCombo.getEditor().clear();
                hospitalCombo.setValue(null);
                hospitalCombo.getSelectionModel().clearSelection();
            } finally {
                suppressHospitalSearch = false;
            }
            notesArea.clear();
            refreshAll();
            requestTable.getItems().stream().filter(r -> r.id() == result.data()).findFirst()
                    .ifPresent(r -> requestTable.getSelectionModel().select(r));
        }
    }

    @FXML private void rematchSelected() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No request selected", "Select a request first."); return; }
        if (!(selected.status() == RequestStatus.PENDING || selected.status() == RequestStatus.MATCHED
                || selected.status() == RequestStatus.DECLINED || selected.status() == RequestStatus.ESCALATED
                || selected.status() == RequestStatus.ACCEPTED || selected.status() == RequestStatus.PARTIALLY_FULFILLED)) {
            AlertUtil.warning("Request cannot be rematched", "This request is already fulfilled or cancelled.");
            return;
        }
        ServiceResult<java.util.List<MatchCandidate>> result = matchingService.match(selected.id(), requester.getId());
        if (result.success()) AlertUtil.info("Matching complete", result.message()); else AlertUtil.error("Matching failed", result.message());
        refreshAll();
    }

    /**
     * A request can need several donors now, so confirmation happens per donor: select
     * the specific ACCEPTED row in the matched-donors table, not just the request.
     */
    @FXML private void confirmReceived() {
        BloodRequest selectedRequest = requestTable.getSelectionModel().getSelectedItem();
        MatchCandidate selectedDonor = matchList.getSelectionModel().getSelectedItem();
        if (selectedRequest == null || selectedDonor == null) {
            AlertUtil.warning("No donor selected", "Select the specific donor row (in Matched Donors) you want to confirm.");
            return;
        }
        if (selectedDonor.matchStatus() != MatchStatus.ACCEPTED) {
            AlertUtil.warning("Not awaiting confirmation", "This donor has not accepted, or is no longer awaiting confirmation.");
            return;
        }
        if (selectedDonor.requesterConfirmed()) {
            AlertUtil.info("Already confirmed", "You already confirmed this donor's donation. Waiting on their side.");
            return;
        }
        if (!AlertUtil.confirm("Confirm receipt", "Confirm that you received the donation from " + selectedDonor.donorName() + "?")) return;
        showResult(requestService.confirmReceived(selectedRequest.id(), requester.getId(), selectedDonor.donorId()));
        refreshAll();
    }

    /**
     * Reviewable per donor once THAT donor's handshake is fully confirmed -- not gated
     * on the whole request being FULFILLED, since other donors on the same request may
     * still be in progress. ReviewService independently enforces the same rule.
     */
    @FXML private void rateDonor() {
        BloodRequest selectedRequest = requestTable.getSelectionModel().getSelectedItem();
        MatchCandidate selectedDonor = matchList.getSelectionModel().getSelectedItem();
        if (selectedRequest == null || selectedDonor == null) {
            AlertUtil.warning("No donor selected", "Select the specific donor row (in Matched Donors) you want to rate.");
            return;
        }
        if (!(selectedDonor.donorConfirmed() && selectedDonor.requesterConfirmed())) {
            AlertUtil.warning("Not yet reviewable", "This donor's donation is not a verified completed donation yet.");
            return;
        }
        // Per donor, not per request: one request can be filled by several
        // donors and each of them is rated separately.
        if (reviewService.hasReviewed(selectedRequest.id(), requester.getId(), selectedDonor.donorId())) {
            AlertUtil.info("Already reviewed", "You already rated " + selectedDonor.donorName() + " for this request.");
            return;
        }
        ReviewDialog.show("Rate donor", selectedDonor.donorName()).ifPresent(input -> {
            ServiceResult<Void> result = reviewService.submit(selectedRequest.id(), requester.getId(),
                    selectedDonor.donorId(), input.rating(), input.tags(), input.comment());
            showResult(result);
            if (result.success()) refreshAll();
        });
    }

    @FXML private void cancelSelected() {
        BloodRequest selected = requestTable.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No request selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Cancel request", "Cancel request #" + selected.id() + "?")) return;
        showResult(requestService.cancel(selected.id(), requester.getId())); refreshAll();
    }

    /**
     * Runs the dashboard's periodic refresh off the JavaFX Application Thread.
     * {@code refreshInFlight} skips a tick rather than queuing another background
     * fetch if the previous one hasn't finished yet (e.g. a slow connection).
     */
    @FXML private void refreshAll() {
        if (refreshInFlight) return;
        refreshInFlight = true;
        BackgroundTasks.run(this::loadDashboardData,
                data -> { applyDashboardData(data); refreshInFlight = false; },
                error -> { requestMessageLabel.setText("Refresh failed: " + error.getMessage()); refreshInFlight = false; });
    }

    private RequesterDashboardData loadDashboardData() throws SQLException {
        return new RequesterDashboardData(
                requestDAO.findByRequester(requester.getId()),
                notificationService.list(requester.getId()),
                notificationService.unreadCount(requester.getId()),
                favoriteDonorService.favoritesOf(requester.getId()));
    }

    private void applyDashboardData(RequesterDashboardData data) {
        Long selectedId = requestTable.getSelectionModel().getSelectedItem() == null ? null : requestTable.getSelectionModel().getSelectedItem().id();
        requestTable.setItems(FXCollections.observableArrayList(data.requests()));
        if (selectedId != null) requestTable.getItems().stream().filter(r -> r.id() == selectedId).findFirst()
                .ifPresent(r -> requestTable.getSelectionModel().select(r));
        notificationList.setItems(FXCollections.observableArrayList(data.notifications()));
        navRail.setBadge(PAGE_NOTIFICATIONS, data.unreadCount());
        favoriteDonorList.setItems(FXCollections.observableArrayList(data.favoriteDonors()));
        applyHomeMetrics(data);
    }

    /** Home page headline numbers, derived from the request list already fetched this refresh. */
    private void applyHomeMetrics(RequesterDashboardData data) {
        long open = data.requests().stream()
                .filter(request -> request.status() != RequestStatus.FULFILLED
                        && request.status() != RequestStatus.CANCELLED)
                .count();
        int secured = data.requests().stream().mapToInt(BloodRequest::unitsFulfilled).sum();
        long fulfilled = data.requests().stream()
                .filter(request -> request.status() == RequestStatus.FULFILLED)
                .count();
        openRequestsLabel.setText(String.valueOf(open));
        unitsSecuredLabel.setText(String.valueOf(secured));
        savedDonorsLabel.setText(String.valueOf(data.favoriteDonors().size()));
        fulfilledLabel.setText(String.valueOf(fulfilled));
    }

    private record RequesterDashboardData(java.util.List<BloodRequest> requests, java.util.List<Notification> notifications,
                                          long unreadCount, java.util.List<FavoriteDonorView> favoriteDonors) { }

    private void loadMatches(BloodRequest request) {
        if (request == null) {
            matchList.getItems().clear();
            historyTable.getItems().clear();
            return;
        }
        long requestId = request.id();
        BackgroundTasks.run(() -> new MatchDetails(requestDAO.findMatchesForRequest(requestId),
                        requestDAO.findStatusHistory(requestId, requester.getId())),
                details -> {
                    matchList.setItems(FXCollections.observableArrayList(details.matches()));
                    historyTable.setItems(FXCollections.observableArrayList(details.history()));
                    updateMapMarkers();
                },
                error -> requestMessageLabel.setText("Could not load request details: " + error.getMessage()));
    }

    private record MatchDetails(java.util.List<MatchCandidate> matches, java.util.List<RequestStatusHistoryEntry> history) { }

    @FXML private void saveProfile() {
        ServiceResult<User> result = profileService.updateProfile(requester.getId(), nameField.getText(), phoneField.getText(),
                profileDistrictField.getText(), addressArea.getText(), guardianNameField.getText(), guardianPhoneField.getText());
        if (result.success()) {
            requester.setFullName(result.data().getFullName()); requester.setPhone(result.data().getPhone());
            requester.setDistrict(result.data().getDistrict()); requester.setAddress(result.data().getAddress());
            requester.setGuardianName(result.data().getGuardianName()); requester.setGuardianPhone(result.data().getGuardianPhone());
        }
        profileMessageLabel.setText(result.message());
    }

    @FXML private void changePassword() {
        ServiceResult<Void> result = profileService.changePassword(requester.getId(), oldPasswordField.getText(),
                newPasswordField.getText(), confirmPasswordField.getText());
        profileMessageLabel.setText(result.message());
        if (result.success()) { oldPasswordField.clear(); newPasswordField.clear(); confirmPasswordField.clear(); }
    }

    @FXML private void markAllNotificationsRead() {
        try { notificationService.markAllRead(requester.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
    }

    private void markSelectedNotificationRead() {
        Notification selected = notificationList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.read()) return;
        try { notificationService.markRead(selected.id(), requester.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
    }

    private void showResult(ServiceResult<Void> result) {
        if (result.success()) AlertUtil.info("Success", result.message()); else AlertUtil.error("Action failed", result.message());
    }

    @FXML private void logout() {
        if (refreshTimeline != null) refreshTimeline.stop();
        PushClient.getInstance().disconnect();
        SceneManager.logout();
    }

    @FXML private void changeProfilePhoto() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        java.io.File file = chooser.showOpenDialog(profilePhotoView.getScene().getWindow());
        if (file != null) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                ServiceResult<Void> result = new ProfileService().updatePhoto(requester.getId(), bytes);
                if (result.success()) {
                    profilePhotoView.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
                    AlertUtil.info("Photo Updated", "Profile photo updated.");
                } else {
                    AlertUtil.error("Update Failed", result.message());
                }
            } catch (Exception e) {
                AlertUtil.error("Error", "Failed to read photo: " + e.getMessage());
            }
        }
    }
}
