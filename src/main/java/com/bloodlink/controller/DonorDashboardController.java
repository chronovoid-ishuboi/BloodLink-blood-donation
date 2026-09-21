package com.bloodlink.controller;

import com.bloodlink.dao.DonorDAO;
import com.bloodlink.dao.HospitalDAO;
import com.bloodlink.dao.RequestDAO;
import com.bloodlink.model.*;
import com.bloodlink.service.*;
import com.bloodlink.util.LogoManager;
import com.bloodlink.util.*;
import com.bloodlink.view.components.BadgeView;
import com.bloodlink.view.components.EmptyState;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DonorDashboardController {

    /** Destination order in the rail; the PageHost's children are in the same order. */
    private static final int PAGE_HOME = 0, PAGE_REQUESTS = 1, PAGE_HOSPITALS = 2, PAGE_HISTORY = 3,
            PAGE_REWARDS = 4, PAGE_LEADERBOARD = 5, PAGE_NEWS = 6, PAGE_NOTIFICATIONS = 7,
            PAGE_HELP = 8, PAGE_PROFILE = 9, PAGE_ABOUT = 10;

    @FXML private StackPane navRailHost;
    @FXML private com.bloodlink.view.shell.PageHost pageHost;

    @FXML private VBox homeHeroHost;
    @FXML private HBox homeMetricsRow;
    @FXML private Label homePointsLabel;
    @FXML private VBox homeLocationHost;
    @FXML private VBox homeActionsHost;
    @FXML private VBox newsHeroHost;
    @FXML private VBox newsHost;
    @FXML private VBox awarenessHost;
    @FXML private VBox homeImpactHost;
    @FXML private VBox aboutHeroHost;
    @FXML private VBox aboutHost;
    @FXML private VBox requestsHeroHost;
    @FXML private VBox hospitalsHeroHost;
    @FXML private VBox historyHeroHost;
    @FXML private HBox historyMetricsRow;
    @FXML private VBox rewardsHeroHost;
    @FXML private VBox rewardsBannerHost;
    @FXML private VBox rewardsHeadHost;
    @FXML private VBox leaderboardHeroHost;
    @FXML private VBox leaderboardHost;
    @FXML private VBox notificationsHeroHost;
    @FXML private VBox helpHeroHost;
    @FXML private VBox profileHeroHost;
    @FXML private VBox profileSectionsHost;
    @FXML private VBox profileDetailsSection;
    @FXML private ScrollPane pageHome;
    @FXML private ScrollPane pageRequests;
    @FXML private ScrollPane pageHospitals;
    @FXML private ScrollPane pageHistory;
    @FXML private ScrollPane pageRewards;
    @FXML private ScrollPane pageLeaderboard;
    @FXML private ScrollPane pageNews;
    @FXML private ScrollPane pageNotifications;
    @FXML private ScrollPane pageHelp;
    @FXML private ScrollPane pageProfile;
    @FXML private ScrollPane pageAbout;

    @FXML private Label bloodGroupLabel;
    @FXML private Label profileBloodGroupLabel;
    @FXML private Label badgeLabel;
    @FXML private Label eligibilityLabel;
    @FXML private Label cooldownLabel;
    @FXML private ProgressBar cooldownProgress;
    @FXML private ComboBox<AvailabilityStatus> availabilityCombo;

    @FXML private Label impactDonationsLabel;
    @FXML private Label impactUnitsLabel;
    @FXML private Label impactHospitalsLabel;
    @FXML private Label impactSinceLabel;
    @FXML private Label impactRatingLabel;

    @FXML private TableView<HospitalWithDistance> nearbyHospitalTable;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalNameColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalDistrictColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalAreaColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalPhoneColumn;
    @FXML private TableColumn<HospitalWithDistance, String> nearbyHospitalDistanceColumn;

    @FXML private ListView<DonorMatchView> matchList;
    @FXML private WebView mapView;

    @FXML private TableView<DonationRecord> donationTable;
    @FXML private TableColumn<DonationRecord, LocalDate> donationDateColumn;
    @FXML private TableColumn<DonationRecord, String> donationHospitalColumn;
    @FXML private TableColumn<DonationRecord, BloodGroup> donationBloodColumn;
    @FXML private TableColumn<DonationRecord, Integer> donationUnitsColumn;
    @FXML private TableColumn<DonationRecord, String> donationVerifiedColumn;

    @FXML private ListView<Notification> notificationList;

    @FXML private javafx.scene.layout.VBox helpContainer;

    @FXML private Label pointsBalanceLabel;
    @FXML private ListView<Voucher> voucherList;
    @FXML private ListView<VoucherRedemption> redemptionHistoryList;

    @FXML private TextField nameField;
    @FXML private Label nidLabel;
    @FXML private TextField phoneField;
    @FXML private Label emailLabel;
    @FXML private TextField guardianNameField;
    @FXML private TextField guardianPhoneField;
    @FXML private TextField districtField;
    @FXML private TextArea addressArea;
    @FXML private ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private Button uploadPhotoButton;
    @FXML private Button removePhotoButton;
    @FXML private TextField weightField;
    @FXML private TextField heightField;
    @FXML private DatePicker lastDonationPicker;
    @FXML private TextField chronicConditionsField;
    @FXML private CheckBox recentSurgeryCheck;
    @FXML private TextField recentSurgeryDetailsField;
    @FXML private CheckBox recentTattooCheck;
    @FXML private TextField recentTattooDetailsField;
    @FXML private CheckBox currentMedicationsCheck;
    @FXML private TextField currentMedicationsDetailsField;
    @FXML private CheckBox recentIllnessCheck;
    @FXML private TextField recentIllnessDetailsField;
    @FXML private CheckBox recentPregnancyCheck;
    @FXML private TextField recentPregnancyDetailsField;
    @FXML private ComboBox<Hospital> referenceHospitalCombo;
    @FXML private Label referenceHospitalHelperLabel;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label profileMessageLabel;

    private final DonorDAO donorDAO = new DonorDAO();
    private final HospitalDAO hospitalDAO = new HospitalDAO();
    private final RequestDAO requestDAO = new RequestDAO();
    private final NotificationService notificationService = new NotificationService();
    private final DonorService donorService = new DonorService();
    private final RequestService requestService = new RequestService();
    private final ProfileService profileService = new ProfileService();
    private final EligibilityService eligibilityService = new EligibilityService();
    private final ReviewService reviewService = new ReviewService();
    private final LocationService locationService = new LocationService();
    private final VoucherService voucherService = new VoucherService();
    private final PointsService pointsService = new PointsService();
    private Donor donor;
    private com.bloodlink.view.shell.NavRail navRail;
    private com.bloodlink.view.shell.LocationBar locationBar;
    private com.bloodlink.view.shell.NewsCarousel newsCarousel;
    private com.bloodlink.view.shell.AwarenessPanel awarenessPanel;
    private com.bloodlink.view.shell.LeaderboardPanel leaderboardPanel;
    private com.bloodlink.view.shell.PreferencesPanel preferencesPanel;
    private Timeline refreshTimeline;
    private volatile boolean refreshInFlight = false;
    private java.util.Set<Long> reviewedRequestIds = java.util.Set.of();
    private boolean suppressReferenceHospitalSearch = false;

    @FXML private void initialize() {
        if (!(SessionManager.getInstance().getCurrentUser() instanceof Donor currentDonor)) {
            SceneManager.showLogin(); return;
        }
        this.donor = currentDonor;
        buildShell();
        helpContainer.getChildren().add(new com.bloodlink.view.components.HelpFaqView(
                donor.getBloodGroup(), com.bloodlink.view.components.BloodCompatibilityView.Mode.DONOR));
        new ProfileService().loadPhoto(donor.getId()).ifPresent(bytes -> {
            try {
                profilePhotoView.setImage(new Image(new ByteArrayInputStream(bytes)));
                profilePhotoView.setVisible(true);
                profilePhotoView.setManaged(true);
                profileInitialsLabel.setVisible(false);
                profileInitialsLabel.setManaged(false);
            } catch (Exception ignored) {}
        });
        configureTables();
        configureReferenceHospitalPicker();
        PushClient.getInstance().connect(donor.getId());
        PushClient.getInstance().onRefresh(this::refreshAll);
        availabilityCombo.getItems().setAll(AvailabilityStatus.values());
        availabilityCombo.setValue(donor.getAvailabilityStatus());
        availabilityCombo.setOnAction(event -> updateAvailability());
        notificationList.setOnMouseClicked(event -> markSelectedNotificationRead());
        populateProfile();
        refreshAll();
        int seconds = Math.max(5, AppConfig.getInt("ui.auto-refresh-seconds"));
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(seconds), event -> refreshAll()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
        
        initializeMap();
    }

    /**
     * Assembles the sidebar shell: the rail's destinations, each page's hero,
     * the home quick-action tiles, and the scroll-reveal wiring. Everything
     * here is structural chrome built in Java because it either animates
     * (the rail's sliding highlight, the marquee, reveals) or is repeated
     * often enough that eight hand-written FXML copies would drift apart.
     */
    private void buildShell() {
        com.bloodlink.view.shell.NavRail rail = new com.bloodlink.view.shell.NavRail(
                "BloodLink", donor.getFullName(), "Donor", donor.getId(), this::logout);
        rail.addSection("Overview");
        rail.addItem("Home", Icons.HOME);
        rail.addItem("Requests", Icons.DROPLET);
        rail.addItem("Hospitals", Icons.HOSPITAL);
        rail.addSection("You");
        rail.addItem("Donation history", Icons.CLOCK);
        rail.addItem("Rewards", Icons.MEDAL);
        rail.addItem("Leaderboard", Icons.CHART);
        rail.addItem("News and impact", Icons.PULSE);
        rail.addItem("Notifications", Icons.BELL);
        rail.addSection("More");
        rail.addItem("Help", Icons.SHIELD_CHECK);
        rail.addItem("Profile", Icons.USER);
        rail.addItem("About", Icons.USERS);
        // Selecting a destination shows its page and, for the pages whose
        // content is an animation, restarts that animation -- see onPageShown.
        rail.setOnSelect(index -> { pageHost.show(index); onPageShown(index); });
        navRail = rail;
        navRailHost.getChildren().add(rail);

        pageHost.initialize();
        rail.select(PAGE_HOME);

        // No pill here. It said "You are a O+ donor" next to a card already
        // headed BLOOD GROUP showing O+ -- the same fact twice, one of them
        // padded into a sentence.
        homeHeroHost.getChildren().add(com.bloodlink.view.shell.UI.heroWithArt(
                "Welcome back, " + firstName(donor.getFullName()),
                "Every unit you give is matched to a real, verified emergency near you.",
                new com.bloodlink.view.shell.ImageSlot(
                        com.bloodlink.view.shell.ImageSlot.Names.DONOR_HERO, "Hero image", 340, 210)));

        requestsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Requests",
                "Requests matched to you. Accept to share your contact details and open a direct conversation."));
        hospitalsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Hospitals",
                "Every hospital in BloodLink's directory, sorted by distance from your registered location."));
        historyHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Donation history",
                "Counts here come only from donations both you and the requester confirmed."));
        rewardsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Rewards",
                "Points earned for verified donations and for being saved as a favourite donor."));
        leaderboardHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Leaderboard",
                "Ranked on verified donations only. Opting out of being named never costs you your place."));
        leaderboardPanel = new com.bloodlink.view.shell.LeaderboardPanel();
        leaderboardHost.getChildren().add(leaderboardPanel);

        notificationsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Notifications",
                "Match alerts, handshake updates and request outcomes."));
        helpHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Help",
                "Your compatibility chart, the donation rules, and answers to the usual questions."));
        profileHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("Profile",
                "What requesters see, and the screening answers that decide your eligibility."));
        aboutHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("About",
                "Who built BloodLink, what it is for, and what it does with your data."));
        aboutHost.getChildren().add(new com.bloodlink.view.shell.AboutPanel());

        homeActionsHost.getChildren().addAll(
                com.bloodlink.view.shell.UI.sectionHead("Quick actions", ""),
                com.bloodlink.view.shell.UI.equalRow(16,
                        com.bloodlink.view.shell.UI.actionTile(Icons.DROPLET, "Matched requests",
                                "See who needs your blood", true, () -> navRail.select(PAGE_REQUESTS)),
                        com.bloodlink.view.shell.UI.actionTile(Icons.HOSPITAL, "Nearby hospitals",
                                "Browse the directory", false, () -> navRail.select(PAGE_HOSPITALS))),
                com.bloodlink.view.shell.UI.equalRow(16,
                        com.bloodlink.view.shell.UI.actionTile(Icons.MEDAL, "Rewards",
                                "Spend your points", false, () -> navRail.select(PAGE_REWARDS)),
                        com.bloodlink.view.shell.UI.actionTile(Icons.SHIELD_CHECK, "Who can I donate to?",
                                "Your compatibility chart", false, () -> navRail.select(PAGE_HELP))));

        homeImpactHost.getChildren().add(com.bloodlink.view.shell.UI.sectionHead("Your impact", "Verified donations only."));

        rewardsBannerHost.getChildren().add(new com.bloodlink.view.shell.ImageSlot(
                com.bloodlink.view.shell.ImageSlot.Names.REWARDS_BANNER, "Rewards banner", 620, 132));
        rewardsHeadHost.getChildren().add(com.bloodlink.view.shell.UI.sectionHead("Partner vouchers",
                "Redeem points with BloodLink's partners. Your code appears instantly."));

        // The location strip sits above everything a donor acts on, because
        // an unset location silently makes all of it worse.
        locationBar = new com.bloodlink.view.shell.LocationBar(
                donor.getId(), donor.getDistrict(), this::updateMapMarkers);
        homeLocationHost.getChildren().add(locationBar);

        newsHeroHost.getChildren().add(com.bloodlink.view.shell.UI.hero("News and impact",
                "Stories worth reading, and the published figures behind them."));

        // Full width here rather than a third of the home page: a story is
        // worth reading, not glancing at.
        newsCarousel = new com.bloodlink.view.shell.NewsCarousel(1000, 420);
        newsHost.getChildren().addAll(
                com.bloodlink.view.shell.UI.sectionHead("In the news", ""), newsCarousel);

        awarenessPanel = new com.bloodlink.view.shell.AwarenessPanel();
        awarenessHost.getChildren().add(awarenessPanel);

        buildProfileSections();

        com.bloodlink.view.shell.Reveal reveal = new com.bloodlink.view.shell.Reveal(pageHome);
        reveal.watchAll(70, homeMetricsRow, homeActionsHost, homeImpactHost);

        // The figures count when the news page is actually looked at, so the
        // motion is seen rather than finishing unwatched on another page.
        com.bloodlink.view.shell.Reveal newsReveal = new com.bloodlink.view.shell.Reveal(pageNews);
        newsReveal.watchThen(awarenessHost, 0, awarenessPanel::play);
    }

    /**
     * Restarts a page's animation each time that page is opened.
     * <p>
     * The counters used to run once for the lifetime of the window, on the
     * theory that a number re-counting on every scroll would be noise. That
     * was right for scrolling and wrong for navigation: coming back to News
     * and impact showed six final figures already sitting there, and the
     * animation -- which is the whole reason the figures are presented this
     * way -- was visible exactly once per session. Returning to a section is a
     * deliberate act, not an accidental scroll, so it replays.
     */
    private void onPageShown(int index) {
        if (index == PAGE_NEWS && awarenessPanel != null) awarenessPanel.replay();
    }

    /** First name only, so the greeting reads like a greeting rather than a record lookup. */
    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        String first = fullName.trim().split("\\s+")[0];
        return first;
    }

    /**
     * Splits Profile into sections instead of one long column.
     * <p>
     * The details form is the FXML that was already there; it is moved under
     * the switcher rather than rebuilt, so every field, handler and fx:id it
     * carries keeps working untouched. The other two sections are built here
     * because they are new.
     */
    private void buildProfileSections() {
        com.bloodlink.view.shell.SectionSwitcher switcher = new com.bloodlink.view.shell.SectionSwitcher();

        preferencesPanel = new com.bloodlink.view.shell.PreferencesPanel(
                donor.getId(), donor.getDistrict(), this::updateMapMarkers);

        com.bloodlink.view.shell.PrivacyPanel privacy = new com.bloodlink.view.shell.PrivacyPanel(
                donor.getId(), donor.getEmail(), this::logout);

        // The details form lives in the FXML; pull it out of the page and hand
        // it to the switcher as section one.
        profileDetailsSection.getStyleClass().remove("page-gutter");
        ((VBox) profileDetailsSection.getParent()).getChildren().remove(profileDetailsSection);

        switcher.addSection("Account details",
                "Your name, contact details, health screening and password", Icons.USER, profileDetailsSection);
        switcher.addSection("Preferences",
                "Location and how far you are willing to travel", Icons.MAP_PIN, preferencesPanel);
        switcher.addSection("Privacy and availability",
                "Leaderboard visibility, your data, and closing your account", Icons.LOCK, privacy);

        profileSectionsHost.getChildren().add(switcher);
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
                // stale size, so the map is re-invalidated whenever the Requests
                // page becomes visible again.
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

        boolean hasDonorLoc = false;
        double dLat = 0, dLng = 0;
        // Labelled with where the position came from, so a district estimate is
        // never shown as though it were this donor's actual doorstep.
        String myLocationLabel = donor == null ? "" : donor.getDistrict();
        if (donor != null) {
            java.util.Optional<com.bloodlink.model.UserLocation> resolved =
                    locationService.resolvedLocation(donor.getId(), donor.getDistrict(), donor.getReferenceHospitalId());
            if (resolved.isPresent()) {
                com.bloodlink.model.UserLocation location = resolved.get();
                dLat = location.latitude();
                dLng = location.longitude();
                hasDonorLoc = true;
                myLocationLabel = location.source().getLabel();
            }
        }

        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.hospitalName() != null) {
            double hLat = 23.8103, hLng = 90.4125;
            boolean hasHospital = false;
            try {
                Hospital h = hospitalDAO.findByName(selected.hospitalName());
                if (h != null) {
                    hLat = h.latitude();
                    hLng = h.longitude();
                    hasHospital = true;
                }
            } catch (SQLException ignored) {}
            if (!hasHospital && selected.district() != null) {
                java.util.Optional<double[]> pt = locationService.districtReferencePoint(selected.district());
                if (pt.isPresent()) {
                    hLat = pt.get()[0];
                    hLng = pt.get()[1];
                    hasHospital = true;
                }
            }

            String title = com.bloodlink.util.JsStrings.escape(selected.hospitalName());
            String subtitle = com.bloodlink.util.JsStrings.escape(selected.district());
            mapView.getEngine().executeScript(String.format("addMarker(%f, %f, '%s', '%s', '%s');",
                    hLat, hLng, title, subtitle, "Hospital"));

            if (hasDonorLoc) {
                mapView.getEngine().executeScript(String.format("addMarker(%f, %f, 'My Location', '%s', '%s');",
                        dLat, dLng, com.bloodlink.util.JsStrings.escape(myLocationLabel), "Me"));
                mapView.getEngine().executeScript("fitBounds();");
            } else {
                mapView.getEngine().executeScript(String.format("setView(%f, %f, 13);", hLat, hLng));
            }
        } else if (hasDonorLoc) {
            mapView.getEngine().executeScript(String.format("addMarker(%f, %f, 'My Location', '%s', '%s');",
                    dLat, dLng, com.bloodlink.util.JsStrings.escape(myLocationLabel), "Me"));
            mapView.getEngine().executeScript(String.format("setView(%f, %f, 12);", dLat, dLng));
        }
    }

    /**
     * Opens the pin picker so this donor can establish exactly where they are.
     * Until they do, requesters only ever see the coarse district estimate for
     * them -- the same point every other donor in that district resolves to.
     */
    @FXML private void setExactLocation() {
        com.bloodlink.view.components.LocationPickerDialog.openForCurrentUser(
                matchList, donor.getDistrict(), this::updateMapMarkers);
    }

    /**
     * Detects a coarse position from the network address and stores it as this
     * donor's location.
     * <p>
     * It is saved, not just drawn, so requesters actually benefit from it --
     * but saved as {@link UserLocation.Source#IP}, which the map labels as
     * approximate, because a network estimate can be tens of kilometres out.
     * Setting an exact pin overrides it.
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
                    locationService.saveLocation(donor.getId(), loc.lat(), loc.lon(), UserLocation.Source.IP);
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


    private void configureTables() {

        donationDateColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().donationDate()));
        donationHospitalColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospitalName()));
        donationBloodColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().bloodGroup()));
        donationUnitsColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().units()));
        donationVerifiedColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().verified() ? "Verified" : "Pending"));


        donationVerifiedColumn.setCellFactory(ChipTableCells.forValues());

        matchList.setPlaceholder(emptyState("No matching emergency requests are waiting for you."));
        matchList.setCellFactory(lv -> new com.bloodlink.view.components.DonorMatchCell());
        donationTable.setPlaceholder(emptyState("No verified donation history is available yet."));
        voucherList.setPlaceholder(emptyState("No vouchers are available right now."));
        voucherList.setCellFactory(lv -> new com.bloodlink.view.components.VoucherCell(this::refreshAll));
        redemptionHistoryList.setPlaceholder(emptyState("You haven't redeemed anything yet."));
        redemptionHistoryList.setCellFactory(lv -> new com.bloodlink.view.components.RedemptionHistoryCell());
        notificationList.setPlaceholder(emptyState("You have no notifications."));
        nearbyHospitalNameColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().name()));
        nearbyHospitalDistrictColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().district()));
        nearbyHospitalAreaColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(v.getValue().hospital().area()));
        nearbyHospitalPhoneColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(
                v.getValue().hospital().phone() == null || v.getValue().hospital().phone().isBlank() ? "—" : v.getValue().hospital().phone()));
        nearbyHospitalDistanceColumn.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(formatDistance(v.getValue().distanceKm())));
        nearbyHospitalTable.setPlaceholder(emptyState("No hospitals are in the directory yet."));
    }

    private String formatDistance(Double distanceKm) {
        return distanceKm == null ? "—" : String.format("~%.1f km", distanceKm);
    }

    private String formatRating(Double averageRating, long reviewCount) {
        return averageRating == null ? "No reviews yet" : String.format("\u2605 %.1f (%d)", averageRating, reviewCount);
    }



    /**
     * Same searchable-picker pattern as the requester's hospital field, repurposed so
     * a donor can choose their own precise location stand-in -- see LocationService.
     */
    private void configureReferenceHospitalPicker() {
        referenceHospitalCombo.setEditable(true);
        referenceHospitalCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Hospital hospital) { return hospital == null ? "" : hospital.name(); }
            @Override public Hospital fromString(String text) { return null; }
        });
        searchReferenceHospitals("");
        referenceHospitalCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (suppressReferenceHospitalSearch) return;
            searchReferenceHospitals(newText);
        });
    }

    private void searchReferenceHospitals(String query) {
        try {
            referenceHospitalCombo.getItems().setAll(hospitalDAO.search(query, 15));
        } catch (SQLException e) {
            // Search-as-you-type failure should not block the donor from using the field.
        }
    }

    /**
     * Renders the donor's tier through {@link BadgeView} instead of printing the
     * raw enum constant, which is what {@code badgeTier + " donor"} used to put on
     * screen ("PLATINUM donor"). The label text, icon and colour all come from
     * the editable badge manifest.
     */
    private void applyBadge() {
        badgeLabel.setText(null);
        badgeLabel.setGraphic(new BadgeView(donor.getBadgeTier(), 22, false));
    }

    private javafx.scene.Node emptyState(String text) {
        return EmptyState.of(text);
    }

    private javafx.scene.Node emptyState(String title, String hint) {
        return EmptyState.of(title, hint);
    }

    private void populateProfile() {
        String bloodGroup = donor.getBloodGroup() == null ? "—" : donor.getBloodGroup().toString();
        bloodGroupLabel.setText(bloodGroup);
        profileBloodGroupLabel.setText(bloodGroup);
        applyBadge();
        
        // Personal Information
        nameField.setText(donor.getFullName());
        String nid = donor.getNidNumber();
        if (nid != null && nid.length() > 4) {
            nidLabel.setText("*" + nid.substring(nid.length() - 4));
        } else {
            nidLabel.setText(nid != null ? nid : "Not Provided");
        }
        
        // Contact Information
        phoneField.setText(donor.getPhone());
        emailLabel.setText(donor.getEmail());
        guardianNameField.setText(donor.getGuardianName());
        guardianPhoneField.setText(donor.getGuardianPhone());
        districtField.setText(donor.getDistrict());
        addressArea.setText(donor.getAddress());
        
        // Health & Eligibility
        weightField.setText(String.valueOf(donor.getWeightKg()));
        heightField.setText(donor.getHeightCm() != null ? String.valueOf(donor.getHeightCm()) : "");
        lastDonationPicker.setValue(donor.getLastDonationDate());
        
        // Screening
        chronicConditionsField.setText(donor.getChronicConditions());
        recentSurgeryCheck.setSelected(donor.getRecentSurgeryDetails() != null && !donor.getRecentSurgeryDetails().isBlank());
        recentSurgeryDetailsField.setText(donor.getRecentSurgeryDetails());
        recentTattooCheck.setSelected(donor.getRecentTattooDetails() != null && !donor.getRecentTattooDetails().isBlank());
        recentTattooDetailsField.setText(donor.getRecentTattooDetails());
        currentMedicationsCheck.setSelected(donor.getCurrentMedicationsDetails() != null && !donor.getCurrentMedicationsDetails().isBlank());
        currentMedicationsDetailsField.setText(donor.getCurrentMedicationsDetails());
        recentIllnessCheck.setSelected(donor.getRecentIllnessDetails() != null && !donor.getRecentIllnessDetails().isBlank());
        recentIllnessDetailsField.setText(donor.getRecentIllnessDetails());
        recentPregnancyCheck.setSelected(donor.getRecentPregnancyDetails() != null && !donor.getRecentPregnancyDetails().isBlank());
        recentPregnancyDetailsField.setText(donor.getRecentPregnancyDetails());

        populateReferenceHospital();
        applyProfilePhoto();
        updateEligibilityCard();
    }

    /**
     * Loaded via the dedicated ProfileService.loadPhoto(), never as part of the
     * routine donor/session fetch -- see UserDAO.findPhoto's Javadoc for why. Falls
     * back to an initials badge (no image asset, no image-generation tool available
     * in this environment -- this is a real, working substitute, not a placeholder)
     * when no photo is set or the stored bytes can't be decoded as an image.
     */
    private void applyProfilePhoto() {
        java.util.Optional<byte[]> photo = profileService.loadPhoto(donor.getId());
        if (photo.isPresent()) {
            try {
                profilePhotoView.setImage(new Image(new ByteArrayInputStream(photo.get())));
                profilePhotoView.setClip(com.bloodlink.util.Avatars.squareClip(96));
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
        profileInitialsLabel.setText(initialsOf(donor.getFullName()));
    }

    private String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    @FXML private void uploadPhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a profile photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(uploadPhotoButton.getScene().getWindow());
        if (file == null) return;
        BackgroundTasks.run(
                () -> profileService.updatePhoto(donor.getId(), Files.readAllBytes(file.toPath())),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Could not read that file: " + error.getMessage()));
    }

    @FXML private void removePhoto() {
        BackgroundTasks.run(
                () -> profileService.updatePhoto(donor.getId(), null),
                result -> { profileMessageLabel.setText(result.message()); if (result.success()) applyProfilePhoto(); },
                error -> profileMessageLabel.setText("Photo could not be removed: " + error.getMessage()));
    }

    private void populateReferenceHospital() {
        suppressReferenceHospitalSearch = true;
        if (donor.getReferenceHospitalId() == null) {
            referenceHospitalCombo.setValue(null);
            referenceHospitalCombo.getEditor().clear();
            referenceHospitalHelperLabel.setText("Not set -- distance in your matches uses your district instead.");
        } else {
            try {
                hospitalDAO.findById(donor.getReferenceHospitalId()).ifPresentOrElse(
                        hospital -> {
                            referenceHospitalCombo.setValue(hospital);
                            referenceHospitalCombo.getEditor().setText(hospital.name());
                            referenceHospitalHelperLabel.setText("Distance in your matches is measured from here.");
                        },
                        () -> referenceHospitalHelperLabel.setText("Your saved reference hospital is no longer active; distance falls back to your district."));
            } catch (SQLException e) {
                referenceHospitalHelperLabel.setText("Could not load your reference hospital: " + e.getMessage());
            }
        }
        suppressReferenceHospitalSearch = false;
    }

    private void updateEligibilityCard() {
        EligibilityService.EligibilityResult result = eligibilityService.evaluate(donor);
        eligibilityLabel.setText(result.eligible() ? "READY" : "NOT ELIGIBLE");
        cooldownLabel.setText(result.reason());
        cooldownProgress.setProgress(result.cooldownDaysRemaining() == 0 ? 1.0 : 1.0 - result.cooldownDaysRemaining() / 56.0);
        eligibilityLabel.getStyleClass().removeAll("status-success", "status-warning");
        eligibilityLabel.getStyleClass().add(result.eligible() ? "status-success" : "status-warning");
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
                error -> { profileMessageLabel.setText("Refresh failed: " + error.getMessage()); refreshInFlight = false; });
    }

    private DonorDashboardData loadDashboardData() throws SQLException {
        java.util.List<DonationRecord> donations = donorDAO.findDonationHistory(donor.getId());
        return new DonorDashboardData(
                requestDAO.findMatchesForDonor(donor.getId(), donor.getDistrict(), donor.getReferenceHospitalId()),
                donations,
                notificationService.list(donor.getId()),
                notificationService.unreadCount(donor.getId()),
                reviewService.reviewedRequestIdsBy(donor.getId()),
                reviewService.reputationOf(donor.getId()),
                loadNearbyHospitals(),
                pointsService.balanceOf(donor.getId()),
                voucherService.listVouchers(),
                voucherService.redemptionHistory(donor.getId()));
    }

    /**
     * The general "browse hospitals" view the matched-requests list can't cover, since
     * that only ever shows hospitals tied to an actual active request. Sorted by
     * distance from the donor (nulls -- unknown distance -- sorted last, never treated
     * as "far" the way the matching radius filter treats them, since this is just a
     * browsing list, not an inclusion decision).
     */
    private java.util.List<HospitalWithDistance> loadNearbyHospitals() throws SQLException {
        java.util.List<HospitalWithDistance> rows = new java.util.ArrayList<>();
        for (Hospital hospital : hospitalDAO.findAll()) {
            Double distanceKm = locationService.distanceKm(donor.getDistrict(), donor.getReferenceHospitalId(),
                    hospital.latitude(), hospital.longitude()).orElse(null);
            rows.add(new HospitalWithDistance(hospital, distanceKm));
        }
        rows.sort(java.util.Comparator.comparing(HospitalWithDistance::distanceKm,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return rows;
    }

    private record HospitalWithDistance(Hospital hospital, Double distanceKm) { }

    private void applyDashboardData(DonorDashboardData data) {
        matchList.setItems(FXCollections.observableArrayList(data.matches()));
        donationTable.setItems(FXCollections.observableArrayList(data.donations()));
        notificationList.setItems(FXCollections.observableArrayList(data.notifications()));
        navRail.setBadge(PAGE_NOTIFICATIONS, data.unreadCount());
        reviewedRequestIds = data.reviewedRequestIds();
        donationTable.refresh();
        applyImpactSummary(data.donations(), data.reputation());
        nearbyHospitalTable.setItems(FXCollections.observableArrayList(data.nearbyHospitals()));
        pointsBalanceLabel.setText(data.pointsBalance() + " pts");
        homePointsLabel.setText(data.pointsBalance() + " pts");
        applyHomeImpact(data);
        voucherList.setItems(FXCollections.observableArrayList(data.vouchers()));
        redemptionHistoryList.setItems(FXCollections.observableArrayList(data.redemptions()));
    }

    /**
     * The home page's impact panel: the same verified figures as the History
     * page, but drawn as filling bars so the page has something that animates
     * on arrival. Each bar is scaled against the next tier boundary rather
     * than an invented target, so the fill means something real -- progress
     * toward the next donor tier, not a made-up quota.
     */
    private void applyHomeImpact(DonorDashboardData data) {
        homeImpactHost.getChildren().removeIf(node -> node.getStyleClass().contains("impact-bar"));
        int donations = data.donations().size();
        int units = data.donations().stream().mapToInt(DonationRecord::units).sum();
        long hospitals = data.donations().stream().map(DonationRecord::hospitalName).distinct().count();

        BadgeTier next = nextTierAfter(donations);
        int target = next == null ? Math.max(donations, 1) : next.getMinimumDonations();

        javafx.scene.Node donationBar = com.bloodlink.view.shell.UI.progressRow(
                "Verified donations", donations + (next == null ? "" : " / " + target),
                target == 0 ? 1 : (double) donations / target, false);
        javafx.scene.Node unitBar = com.bloodlink.view.shell.UI.progressRow(
                "Units donated", String.valueOf(units), Math.min(1, units / 20.0), true);
        javafx.scene.Node hospitalBar = com.bloodlink.view.shell.UI.progressRow(
                "Hospitals helped", String.valueOf(hospitals), Math.min(1, hospitals / 10.0), false);
        for (javafx.scene.Node bar : java.util.List.of(donationBar, unitBar, hospitalBar)) {
            bar.getStyleClass().add("impact-bar");
            homeImpactHost.getChildren().add(bar);
        }
    }

    private BadgeTier nextTierAfter(int donations) {
        BadgeTier best = null;
        for (BadgeTier tier : BadgeTier.values()) {
            if (tier.getMinimumDonations() > donations && (best == null || tier.getMinimumDonations() < best.getMinimumDonations())) {
                best = tier;
            }
        }
        return best;
    }

    /**
     * Computed from the donation history already fetched every refresh, rather than a
     * separate aggregate query -- this data is already in memory, so there's no reason
     * to hit the database again just to summarize it. Every figure here is a real,
     * verifiable count from donation_history; deliberately no "lives saved" style
     * multiplier, since that's an estimate this app has no basis to assert as fact
     * about a specific donor's specific donations.
     */
    private void applyImpactSummary(java.util.List<DonationRecord> donations, ReputationSummary reputation) {
        if (donations.isEmpty()) {
            impactDonationsLabel.setText("0");
            impactUnitsLabel.setText("0");
            impactHospitalsLabel.setText("0");
            impactSinceLabel.setText("No verified donations yet");
        } else {
            int totalUnits = donations.stream().mapToInt(DonationRecord::units).sum();
            long distinctHospitals = donations.stream().map(DonationRecord::hospitalName).distinct().count();
            LocalDate first = donations.stream().map(DonationRecord::donationDate)
                    .min(LocalDate::compareTo).orElse(null);
            impactDonationsLabel.setText(String.valueOf(donations.size()));
            impactUnitsLabel.setText(String.valueOf(totalUnits));
            impactHospitalsLabel.setText(String.valueOf(distinctHospitals));
            impactSinceLabel.setText(first == null ? "—" : "Donating since " + first);
        }
        impactRatingLabel.setText(formatRating(reputation.hasReviews() ? reputation.averageRating() : null, reputation.reviewCount()));
    }

    private record DonorDashboardData(java.util.List<DonorMatchView> matches, java.util.List<DonationRecord> donations,
                                      java.util.List<Notification> notifications, long unreadCount,
                                      java.util.Set<Long> reviewedRequestIds, ReputationSummary reputation,
                                      java.util.List<HospitalWithDistance> nearbyHospitals,
                                      int pointsBalance, java.util.List<Voucher> vouchers,
                                      java.util.List<VoucherRedemption> redemptions) { }

    @FXML private void acceptSelected() {
        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (selected.matchStatus() != MatchStatus.NOTIFIED) { AlertUtil.warning("Already answered", "This match is no longer awaiting a response."); return; }
        if (!AlertUtil.confirm("Accept request", "Accept blood request #" + selected.requestId() + "?")) return;
        showResult(requestService.accept(selected.requestId(), donor.getId()));
        refreshAll();
    }

    @FXML private void declineSelected() {
        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (!AlertUtil.confirm("Decline match", "Decline request #" + selected.requestId() + "?")) return;
        showResult(requestService.decline(selected.requestId(), donor.getId()));
        refreshAll();
    }

    /**
     * The donor's half of the two-sided handshake. A donor can only confirm a request
     * they personally accepted and that is still sitting in ACCEPTED or
     * PARTIALLY_FULFILLED (another donor on the same multi-unit request may have
     * already completed their own handshake, moving the overall status along, while
     * this donor's own match is still waiting) -- both checked here and, more
     * importantly, again in RequestDAO.confirmDonorSide, since this button being
     * visible is not itself authorization.
     */
    @FXML private void confirmDonated() {
        DonorMatchView selected = matchList.getSelectionModel().getSelectedItem();
        if (selected == null) { AlertUtil.warning("No match selected", "Select a request first."); return; }
        if (selected.matchStatus() != MatchStatus.ACCEPTED
                || !(selected.requestStatus() == RequestStatus.ACCEPTED || selected.requestStatus() == RequestStatus.PARTIALLY_FULFILLED)) {
            AlertUtil.warning("Not ready to confirm", "You can only confirm a donation for a request you've accepted that is still awaiting confirmation.");
            return;
        }
        if (selected.donorConfirmed()) {
            AlertUtil.info("Already confirmed", "You already confirmed this donation. Waiting on the requester's side.");
            return;
        }
        if (!AlertUtil.confirm("Confirm donation", "Confirm that you donated blood for request #" + selected.requestId() + "?")) return;
        showResult(requestService.confirmDonated(selected.requestId(), donor.getId()));
        refreshAll();
    }

    private void updateAvailability() {
        AvailabilityStatus selected = availabilityCombo.getValue();
        if (selected == donor.getAvailabilityStatus()) return;
        ServiceResult<Void> result = donorService.updateAvailability(donor.getId(), selected);
        if (result.success()) donor.setAvailabilityStatus(selected);
        else AlertUtil.error("Update failed", result.message());
    }

    @FXML private void saveProfile() {
        ServiceResult<User> result = profileService.updateProfile(donor.getId(), nameField.getText(), phoneField.getText(),
                districtField.getText(), addressArea.getText(), guardianNameField.getText(), guardianPhoneField.getText());
        if (!result.success()) { profileMessageLabel.setText(result.message()); return; }
        donor.setFullName(result.data().getFullName()); donor.setPhone(result.data().getPhone());
        donor.setDistrict(result.data().getDistrict()); donor.setAddress(result.data().getAddress());
        donor.setGuardianName(result.data().getGuardianName()); donor.setGuardianPhone(result.data().getGuardianPhone());
        profileMessageLabel.setText(result.message()); populateProfile();
    }

    @FXML private void saveHealth() {
        ServiceResult<Void> result = donorService.updateHealth(
                donor.getId(),
                weightField.getText(),
                heightField.getText(),
                lastDonationPicker.getValue(),
                chronicConditionsField.getText(),
                recentSurgeryCheck.isSelected() ? recentSurgeryDetailsField.getText() : null,
                recentTattooCheck.isSelected() ? recentTattooDetailsField.getText() : null,
                currentMedicationsCheck.isSelected() ? currentMedicationsDetailsField.getText() : null,
                recentIllnessCheck.isSelected() ? recentIllnessDetailsField.getText() : null,
                recentPregnancyCheck.isSelected() ? recentPregnancyDetailsField.getText() : null
        );
        if (result.success()) {
            if (!weightField.getText().isBlank()) donor.setWeightKg(Double.parseDouble(weightField.getText().trim()));
            if (!heightField.getText().isBlank()) donor.setHeightCm(Double.parseDouble(heightField.getText().trim()));
            donor.setLastDonationDate(lastDonationPicker.getValue());
            donor.setChronicConditions(chronicConditionsField.getText());
            donor.setRecentSurgeryDetails(recentSurgeryCheck.isSelected() ? recentSurgeryDetailsField.getText() : null);
            donor.setRecentTattooDetails(recentTattooCheck.isSelected() ? recentTattooDetailsField.getText() : null);
            donor.setCurrentMedicationsDetails(currentMedicationsCheck.isSelected() ? currentMedicationsDetailsField.getText() : null);
            donor.setRecentIllnessDetails(recentIllnessCheck.isSelected() ? recentIllnessDetailsField.getText() : null);
            donor.setRecentPregnancyDetails(recentPregnancyCheck.isSelected() ? recentPregnancyDetailsField.getText() : null);
            updateEligibilityCard();
        }
        profileMessageLabel.setText(result.message());
    }

    /**
     * Saves whichever hospital the donor picked from the searchable list as their
     * reference point. Free-typed text that doesn't match a real selection is
     * rejected rather than silently ignored, since an unresolved reference would
     * leave the donor thinking their distance is precise when it fell back silently.
     */
    @FXML private void saveReferenceHospital() {
        Hospital selected = referenceHospitalCombo.getValue();
        String typedText = referenceHospitalCombo.getEditor().getText();
        if (typedText == null || typedText.isBlank()) {
            applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), null));
            return;
        }
        if (selected == null || !selected.name().equals(typedText)) {
            profileMessageLabel.setText("Pick a hospital from the dropdown list, or clear the field to remove your reference hospital.");
            return;
        }
        applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), selected.id()));
    }

    @FXML private void clearReferenceHospital() {
        referenceHospitalCombo.setValue(null);
        referenceHospitalCombo.getEditor().clear();
        applyReferenceHospitalResult(donorService.updateReferenceHospital(donor.getId(), null));
    }

    private void applyReferenceHospitalResult(ServiceResult<Void> result) {
        if (result.success()) donor.setReferenceHospitalId(referenceHospitalCombo.getValue() == null ? null : referenceHospitalCombo.getValue().id());
        profileMessageLabel.setText(result.message());
        populateReferenceHospital();
    }

    @FXML private void changePassword() {
        ServiceResult<Void> result = profileService.changePassword(donor.getId(), oldPasswordField.getText(),
                newPasswordField.getText(), confirmPasswordField.getText());
        profileMessageLabel.setText(result.message());
        if (result.success()) { oldPasswordField.clear(); newPasswordField.clear(); confirmPasswordField.clear(); }
    }

    @FXML private void markAllNotificationsRead() {
        try { notificationService.markAllRead(donor.getId()); refreshAll(); }
        catch (SQLException e) { AlertUtil.error("Notification error", e.getMessage()); }
    }

    private void markSelectedNotificationRead() {
        Notification selected = notificationList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.read()) return;
        try { notificationService.markRead(selected.id(), donor.getId()); refreshAll(); }
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
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(profilePhotoView.getScene().getWindow());
        if (file != null) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                ServiceResult<Void> result = new ProfileService().updatePhoto(donor.getId(), bytes);
                if (result.success()) {
                    PhotoCache.invalidate(donor.getId());
                    applyProfilePhoto();
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
