package com.bloodlink.view.shell;

import com.bloodlink.util.Avatars;
import com.bloodlink.util.Icons;
import com.bloodlink.util.Motion;
import com.bloodlink.util.PhotoCache;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The left navigation rail that replaced the old tab bar.
 * <p>
 * Two things here are hand-built rather than styled controls, both because
 * CSS cannot express them:
 * <ul>
 *   <li>The active state is a single highlight pill that <em>moves</em>
 *       between items instead of each item painting its own background, so
 *       the rail reads as one continuous control and its motion ties to the
 *       page transition happening beside it.</li>
 *   <li>The rail collapses to an icon strip and back, animating its width,
 *       with a nudging arrow while collapsed so the way to bring it back is
 *       visible rather than something to discover.</li>
 * </ul>
 */
public final class NavRail extends VBox {

    private static final double ITEM_HEIGHT = 44;
    private static final double ITEM_GAP = 4;
    private static final double WIDTH_EXPANDED = 244;
    private static final double WIDTH_COLLAPSED = 74;
    /** A little larger than the old mark, so a real logo has room to read. */
    private static final double LOGO_SIZE = 34;

    private final Pane itemLayer = new Pane();
    private final Region highlight = new Region();
    private final List<HBox> items = new ArrayList<>();
    private final List<Label> itemLabels = new ArrayList<>();
    private final List<Label> sectionLabels = new ArrayList<>();

    private final HBox brandRow;
    private final Node brandMarkHolder;
    private final Label wordmarkLabel;
    private final VBox footerIdentity;
    private final javafx.scene.control.Button logoutButton;
    private final javafx.scene.control.Button themeButton;
    private HBox footerButtons;
    private final StackPane avatarHolder;
    private final Label collapseArrow;
    private Timeline arrowNudge;

    private Consumer<Integer> onSelect;
    private int selectedIndex = -1;
    private double nextY = 0;
    private boolean collapsed = false;

    public NavRail(String wordmark, String userName, String roleLabel, long userId, Runnable onLogout) {
        getStyleClass().add("nav-rail");
        setMinWidth(WIDTH_EXPANDED);
        setPrefWidth(WIDTH_EXPANDED);
        setMaxWidth(WIDTH_EXPANDED);

        wordmarkLabel = new Label(wordmark);
        wordmarkLabel.getStyleClass().add("nav-rail-wordmark");

        collapseArrow = new Label("‹");
        collapseArrow.getStyleClass().add("nav-collapse-arrow");
        collapseArrow.setTooltip(new Tooltip("Collapse the sidebar"));
        collapseArrow.setOnMouseClicked(event -> toggleCollapsed());

        Region brandSpacer = new Region();
        HBox.setHgrow(brandSpacer, Priority.ALWAYS);
        brandMarkHolder = brandMark();
        brandRow = new HBox(brandMarkHolder, wordmarkLabel, brandSpacer, collapseArrow);
        brandRow.getStyleClass().add("nav-rail-brand");
        brandRow.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(brandRow);

        highlight.getStyleClass().add("nav-highlight");
        highlight.setPrefHeight(ITEM_HEIGHT);
        highlight.setVisible(false);
        itemLayer.getChildren().add(highlight);
        itemLayer.widthProperty().addListener((obs, old, width) -> highlight.setPrefWidth(width.doubleValue()));
        getChildren().add(itemLayer);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        avatarHolder = new StackPane();
        avatarHolder.getStyleClass().add("nav-avatar-holder");
        applyAvatar(userId, userName);

        Label name = new Label(userName);
        name.getStyleClass().add("nav-rail-user");
        Label role = new Label(roleLabel);
        role.getStyleClass().add("nav-rail-role");
        footerIdentity = new VBox(1, name, role);
        HBox.setHgrow(footerIdentity, Priority.ALWAYS);

        logoutButton = new javafx.scene.control.Button("Log out");
        logoutButton.getStyleClass().add("nav-logout");
        logoutButton.setOnAction(event -> onLogout.run());
        Motion.attachHoverLift(logoutButton, 2);

        themeButton = new javafx.scene.control.Button();
        themeButton.getStyleClass().add("nav-logout");
        themeButton.setOnAction(event -> {
            com.bloodlink.util.Theme.toggle();
            updateThemeButton();
        });
        updateThemeButton();
        Motion.attachHoverLift(themeButton, 2);

        HBox identityRow = new HBox(10, avatarHolder, footerIdentity);
        identityRow.setAlignment(Pos.CENTER_LEFT);
        footerButtons = new HBox(8, themeButton, logoutButton);
        footerButtons.setAlignment(Pos.CENTER_LEFT);
        VBox footer = new VBox(12, identityRow, footerButtons);
        footer.getStyleClass().add("nav-rail-footer");

        getChildren().addAll(spacer, footer);
    }

    public void setOnSelect(Consumer<Integer> onSelect) {
        this.onSelect = onSelect;
    }

    /**
     * Shows the user's own profile picture, falling back to a drawn
     * person glyph when they have not uploaded one -- never an empty box.
     */
    private void applyAvatar(long userId, String userName) {
        Node placeholder = new Group(Icons.icon(Icons.USER, 19, "nav-avatar-placeholder-icon"));
        StackPane placeholderBox = new StackPane(placeholder);
        placeholderBox.getStyleClass().add("nav-avatar-placeholder");
        avatarHolder.getChildren().setAll(placeholderBox);

        PhotoCache.getPhotoAsync(userId, photo -> {
            if (photo == null) return;
            avatarHolder.getChildren().setAll(avatarView(photo));
        });
    }

    private ImageView avatarView(Image photo) {
        ImageView view = new ImageView(photo);
        view.setFitWidth(38);
        view.setFitHeight(38);
        view.setPreserveRatio(false);
        view.setClip(Avatars.squareClip(38));
        return view;
    }

    /** Refreshes the footer picture after the user changes it, without rebuilding the rail. */
    public void refreshAvatar(long userId, String userName) {
        PhotoCache.invalidate(userId);
        applyAvatar(userId, userName);
    }

    public void addSection(String title) {
        Label label = new Label(title.toUpperCase(java.util.Locale.ROOT));
        label.getStyleClass().add("nav-rail-section");
        label.setLayoutY(nextY);
        label.setPrefHeight(32);
        sectionLabels.add(label);
        itemLayer.getChildren().add(label);
        nextY += 32;
    }

    public int addItem(String title, String iconPath) {
        int index = items.size();

        Node icon = Icons.icon(iconPath, 16, "nav-item-icon");
        Label label = new Label(title);
        label.getStyleClass().add("nav-item-label");
        itemLabels.add(label);

        HBox row = new HBox(new Group(icon), label);
        row.getStyleClass().add("nav-item");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefHeight(ITEM_HEIGHT);
        row.setLayoutY(nextY);
        row.prefWidthProperty().bind(itemLayer.widthProperty());
        row.setOnMouseClicked(event -> select(index));
        // The label disappears when collapsed, so the name has to survive
        // somewhere -- otherwise the strip is eight unlabelled glyphs.
        Tooltip.install(row, new Tooltip(title));
        // Deliberately no hover scale: scaling a row re-rasterises its text
        // every frame, which both costs and softens the glyphs. Hover feedback
        // comes from a CSS background swap instead.

        items.add(row);
        itemLayer.getChildren().add(row);
        nextY += ITEM_HEIGHT + ITEM_GAP;
        itemLayer.setPrefHeight(nextY);
        itemLayer.setMinHeight(nextY);
        return index;
    }

    public void setBadge(int index, long count) {
        HBox row = items.get(index);
        row.getChildren().removeIf(node -> node instanceof Label label && label.getStyleClass().contains("nav-badge"));
        if (count <= 0) return;
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label(String.valueOf(count));
        badge.getStyleClass().add("nav-badge");
        badge.setVisible(!collapsed);
        badge.setManaged(!collapsed);
        row.getChildren().addAll(spacer, badge);
    }

    public void select(int index) {
        if (index < 0 || index >= items.size()) return;
        for (HBox item : items) {
            item.getStyleClass().remove("nav-item-active");
        }
        items.get(index).getStyleClass().add("nav-item-active");

        double targetY = items.get(index).getLayoutY();
        if (!highlight.isVisible()) {
            highlight.setVisible(true);
            highlight.setLayoutY(targetY);
            highlight.setPrefWidth(itemLayer.getWidth());
        } else {
            new Timeline(new KeyFrame(Duration.millis(280),
                    new KeyValue(highlight.layoutYProperty(), targetY, Interpolator.SPLINE(0.22, 1, 0.36, 1)))).play();
        }
        highlight.toBack();

        boolean changed = selectedIndex != index;
        selectedIndex = index;
        if (changed && onSelect != null) onSelect.accept(index);
    }

    /**
     * Collapses to an icon strip, or expands back. The width is animated so
     * the content beside it reflows smoothly instead of jumping, and the
     * arrow starts nudging while collapsed so getting the rail back is
     * signposted rather than guesswork.
     */
    public void toggleCollapsed() {
        collapsed = !collapsed;
        double target = collapsed ? WIDTH_COLLAPSED : WIDTH_EXPANDED;

        for (Label label : itemLabels) {
            label.setVisible(!collapsed);
            label.setManaged(!collapsed);
        }
        for (Label label : sectionLabels) {
            label.setVisible(!collapsed);
            label.setManaged(!collapsed);
        }
        for (HBox row : items) {
            row.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
            row.getChildren().stream()
                    .filter(node -> node instanceof Label label && label.getStyleClass().contains("nav-badge"))
                    .forEach(node -> {
                        node.setVisible(!collapsed);
                        node.setManaged(!collapsed);
                    });
        }
        wordmarkLabel.setVisible(!collapsed);
        wordmarkLabel.setManaged(!collapsed);
        // The collapsed rail is only wide enough for one thing on this row.
        // Keeping the logo would push the arrow off the edge, leaving no way
        // to expand again -- so while collapsed the row is the arrow alone.
        brandMarkHolder.setVisible(!collapsed);
        brandMarkHolder.setManaged(!collapsed);
        footerIdentity.setVisible(!collapsed);
        footerIdentity.setManaged(!collapsed);
        logoutButton.setVisible(!collapsed);
        logoutButton.setManaged(!collapsed);
        // The theme toggle survives collapsing as a glyph, since it is the one
        // footer control with no equivalent anywhere else in the app.
        themeButton.setText(collapsed ? themeGlyph() : themeLabel());
        footerButtons.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        brandRow.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        collapseArrow.setText(collapsed ? "›" : "‹");
        collapseArrow.getTooltip().setText(collapsed ? "Expand the sidebar" : "Collapse the sidebar");

        Timeline resize = new Timeline(new KeyFrame(Duration.millis(240),
                new KeyValue(minWidthProperty(), target, Interpolator.SPLINE(0.22, 1, 0.36, 1)),
                new KeyValue(prefWidthProperty(), target, Interpolator.SPLINE(0.22, 1, 0.36, 1)),
                new KeyValue(maxWidthProperty(), target, Interpolator.SPLINE(0.22, 1, 0.36, 1))));
        resize.play();

        if (collapsed) startArrowNudge(); else stopArrowNudge();
    }

    private void updateThemeButton() {
        themeButton.setText(collapsed ? themeGlyph() : themeLabel());
        themeButton.setTooltip(new Tooltip(com.bloodlink.util.Theme.isDark()
                ? "Switch to the light theme" : "Switch to the dark theme"));
    }

    private String themeLabel() {
        return com.bloodlink.util.Theme.isDark() ? "Light" : "Dark";
    }

    private String themeGlyph() {
        return com.bloodlink.util.Theme.isDark() ? "2600" : "263D";
    }

    /** A small repeating drift outward -- the visual cue that the rail can be pulled back open. */
    private void startArrowNudge() {
        stopArrowNudge();
        arrowNudge = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(collapseArrow.translateXProperty(), 0)),
                new KeyFrame(Duration.millis(620), new KeyValue(collapseArrow.translateXProperty(), 4,
                        Interpolator.SPLINE(0.4, 0, 0.6, 1))));
        arrowNudge.setAutoReverse(true);
        arrowNudge.setCycleCount(Animation.INDEFINITE);
        arrowNudge.play();
    }

    private void stopArrowNudge() {
        if (arrowNudge != null) {
            arrowNudge.stop();
            arrowNudge = null;
        }
        collapseArrow.setTranslateX(0);
    }

    /**
     * Slot for the organisation's logo.
     * <p>
     * Deliberately empty until a real logo is supplied -- an invented mark
     * sitting next to the wordmark would read as the brand, and a stand-in
     * brand is worse than none. Drop a file at
     * {@code resources/com/bloodlink/images/company-logo.png} and it appears
     * on the next run; until then the slot holds its space so adding the
     * logo does not shift the layout.
     */
    private Node brandMark() {
        return BrandMark.node(LOGO_SIZE);
    }
}
