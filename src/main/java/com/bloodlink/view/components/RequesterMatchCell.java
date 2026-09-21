package com.bloodlink.view.components;

import com.bloodlink.model.MatchCandidate;
import com.bloodlink.service.FavoriteDonorService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.Icons;
import com.bloodlink.util.PhotoCache;
import com.bloodlink.util.SessionManager;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.Locale;

/**
 * One ranked donor, as shown to a requester in the matched-donors list.
 * <p>
 * All colour and layout now comes from the {@code .match-card*} classes in
 * {@code theme.css} instead of the inline {@code setStyle()} calls this class
 * used to carry, so the palette lives in one file. The tier badge is a real
 * {@link BadgeView} rather than the raw enum constant ("PLATINUM") it used to
 * print.
 * <p>
 * <b>Opening the profile.</b> The requester toolbar's Confirm Received and Rate
 * Donor actions both operate on the list <em>selection</em>, so opening a modal
 * profile on every single click would make those buttons unusable. Instead the
 * card keeps single-click selection and offers the profile two ways that cannot
 * collide with it: the "Profile" affordance on the right of the card, and a
 * double-click anywhere on the card.
 */
public final class RequesterMatchCell extends ListCell<MatchCandidate> {

    private final java.util.function.Supplier<Long> selectedRequestId;

    /** @param selectedRequestId supplies the request these matches belong to, or null when nothing is selected. */
    public RequesterMatchCell(java.util.function.Supplier<Long> selectedRequestId) {
        this.selectedRequestId = selectedRequestId;
    }

    @Override
    protected void updateItem(MatchCandidate match, boolean empty) {
        super.updateItem(match, empty);
        setText(null);
        if (empty || match == null) {
            setGraphic(null);
            return;
        }

        HBox card = new HBox(14);
        card.getStyleClass().add("match-card");
        card.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().add(avatar(match));

        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label name = new Label(match.donorName() == null ? "Unknown donor" : match.donorName());
        name.getStyleClass().add("match-card-name");

        javafx.scene.layout.FlowPane details = new javafx.scene.layout.FlowPane(10, 4);
        details.setAlignment(Pos.CENTER_LEFT);
        details.setPrefWrapLength(330);
        details.getChildren().add(iconText(Icons.DROPLET,
                match.bloodGroup() == null ? "—" : match.bloodGroup().getDisplayName()));
        details.getChildren().add(iconText(Icons.MAP_PIN, blankToDash(match.district())));
        // A null distance is genuinely unknown -- never rendered as a number.
        details.getChildren().add(iconText(Icons.HOSPITAL, match.distanceKm() == null
                ? "distance unavailable"
                : String.format(Locale.ENGLISH, "%.1f km away", match.distanceKm())));

        // averageRating is null when this donor has no reviews; StarRating renders
        // that as "No reviews yet" rather than zero stars.
        info.getChildren().addAll(name, details, new StarRating(match.averageRating(), true, 14));

        VBox status = new VBox(6);
        status.setAlignment(Pos.TOP_RIGHT);
        Label matchStatus = new Label(match.matchStatus().name());
        matchStatus.getStyleClass().addAll("chip", "chip-" + match.matchStatus().name().toLowerCase(Locale.ROOT));
        status.getChildren().addAll(matchStatus, new BadgeView(match.badgeTier()), favoriteToggle(match),
                chatAffordance(match), profileAffordance(match));

        card.getChildren().addAll(info, status);

        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) openProfile(match);
        });
        com.bloodlink.util.Motion.attachHoverLift(card, 3);

        setGraphic(card);
    }

    private Node avatar(MatchCandidate match) {
        StackPane container = new StackPane();
        Label initials = new Label(DonorProfileDialog.initialsOf(match.donorName()));
        initials.getStyleClass().addAll("avatar-initials", "avatar-initials-sm");
        container.getChildren().add(initials);

        // Shows initials immediately; a cache miss swaps the photo in once the
        // background BLOB fetch completes instead of blocking this render.
        PhotoCache.getPhotoAsync(match.donorId(), photo -> {
            if (photo == null) return;
            ImageView view = new ImageView(photo);
            view.setFitWidth(48);
            view.setFitHeight(48);
            view.setPreserveRatio(false);
            // ImageView is not a Region, so the circular crop must be a clip, not CSS.
            view.setClip(com.bloodlink.util.Avatars.squareClip(48));
            view.getStyleClass().add("avatar-photo");
            container.getChildren().setAll(view);
        });
        return container;
    }

    /**
     * Favoriting also awards the donor bonus points (see FavoriteDonorService),
     * so this is a real action with a real effect on the other side, not just
     * a personal bookmark -- worth an explicit confirmation-free but visible
     * success alert on the way in, though not on the way out (unfavoriting
     * doesn't take the points back, so there is nothing consequential to
     * announce there).
     */
    private Node favoriteToggle(MatchCandidate match) {
        FavoriteDonorService favoriteService = new FavoriteDonorService();
        long requesterId = SessionManager.getInstance().getCurrentUser().getId();
        boolean[] favorited = { favoriteService.isFavorite(requesterId, match.donorId()) };
        Label star = new Label(favorited[0] ? "★" : "☆");
        star.getStyleClass().add(favorited[0] ? "favorite-star-active" : "favorite-star");
        star.setStyle("-fx-cursor: hand; -fx-font-size: 15px;");
        star.setOnMouseClicked(event -> {
            event.consume();
            ServiceResult<Void> result = favorited[0]
                    ? favoriteService.removeFavorite(match.donorId())
                    : favoriteService.addFavorite(match.donorId());
            if (!result.success()) {
                AlertUtil.error("Couldn't update favorites", result.message());
                return;
            }
            favorited[0] = !favorited[0];
            star.setText(favorited[0] ? "★" : "☆");
            star.getStyleClass().setAll(favorited[0] ? "favorite-star-active" : "favorite-star");
            if (favorited[0]) AlertUtil.info("Added to favorites", result.message());
        });
        return star;
    }

    /**
     * A MatchCandidate carries no request id -- it describes a donor, and the
     * same donor can be ranked on several of this requester's requests -- so
     * the id comes from a supplier the dashboard fills with whichever request
     * is currently selected, which is the same request this list is showing
     * matches for.
     * <p>
     * Hidden until the donor accepts, matching DonorMatchCell: before that
     * there is no agreed connection, and ChatService would refuse the thread.
     */
    private Node chatAffordance(MatchCandidate match) {
        if (match.matchStatus() != com.bloodlink.model.MatchStatus.ACCEPTED) return new HBox();
        Long requestId = selectedRequestId == null ? null : selectedRequestId.get();
        if (requestId == null) return new HBox();

        HBox link = new HBox(3);
        link.setAlignment(Pos.CENTER_RIGHT);
        Label label = new Label("Message");
        label.getStyleClass().add("match-card-hint");
        link.getChildren().addAll(Icons.icon(Icons.MAIL, 11, "match-card-hint-icon"), label);
        link.setStyle("-fx-cursor: hand;");
        link.setOnMouseClicked(event -> {
            event.consume();
            if (getScene() == null) return;
            ChatDialog.open(getScene().getWindow(), requestId, match.donorId(), match.donorName());
        });
        return link;
    }

    private Node profileAffordance(MatchCandidate match) {
        HBox link = new HBox(3);
        link.setAlignment(Pos.CENTER_RIGHT);
        Label label = new Label("Profile");
        label.getStyleClass().add("match-card-hint");
        link.getChildren().addAll(label, Icons.icon(Icons.CHEVRON_RIGHT, 11, "match-card-hint-icon"));
        link.setStyle("-fx-cursor: hand;");
        link.setOnMouseClicked(event -> {
            // Stops the click from also reaching the card's double-click handler.
            event.consume();
            openProfile(match);
        });
        return link;
    }

    private void openProfile(MatchCandidate match) {
        if (getScene() == null) return;
        DonorProfileDialog.show(getScene().getWindow(), match.donorId(), match.matchStatus(), match.distanceKm());
    }

    private static Node iconText(String iconPath, String text) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(text);
        label.getStyleClass().add("match-card-detail");
        row.getChildren().addAll(Icons.icon(iconPath, 12, "match-card-icon"), label);
        return row;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
