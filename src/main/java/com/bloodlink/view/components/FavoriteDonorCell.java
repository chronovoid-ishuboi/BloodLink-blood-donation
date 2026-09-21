package com.bloodlink.view.components;

import com.bloodlink.model.FavoriteDonorView;
import com.bloodlink.service.FavoriteDonorService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.Icons;
import com.bloodlink.util.Motion;
import com.bloodlink.util.PhotoCache;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/** A requester's saved donor. Mirrors RequesterMatchCell's layout so a favorited donor looks like the same kind of card everywhere it appears. */
public final class FavoriteDonorCell extends ListCell<FavoriteDonorView> {
    private final FavoriteDonorService favoriteDonorService = new FavoriteDonorService();
    private final Runnable onRemoved;

    public FavoriteDonorCell(Runnable onRemoved) {
        this.onRemoved = onRemoved;
    }

    @Override
    protected void updateItem(FavoriteDonorView favorite, boolean empty) {
        super.updateItem(favorite, empty);
        setText(null);
        if (empty || favorite == null) {
            setGraphic(null);
            return;
        }

        HBox card = new HBox(14);
        card.getStyleClass().add("match-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(avatar(favorite));

        VBox info = new VBox(5);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(favorite.donorName());
        name.getStyleClass().add("match-card-name");
        javafx.scene.layout.FlowPane details = new javafx.scene.layout.FlowPane(10, 4);
        details.setAlignment(Pos.CENTER_LEFT);
        details.setPrefWrapLength(330);
        details.getChildren().add(iconText(Icons.DROPLET, favorite.bloodGroup().getDisplayName()));
        details.getChildren().add(iconText(Icons.MAP_PIN, favorite.district()));
        details.getChildren().add(iconText(Icons.PHONE, favorite.phone()));
        info.getChildren().addAll(name, details);

        VBox status = new VBox(6);
        status.setAlignment(Pos.TOP_RIGHT);
        Label availability = new Label(favorite.availabilityStatus().getLabel());
        availability.getStyleClass().addAll("availability-pill",
                "availability-" + favorite.availabilityStatus().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        Button remove = new Button("Remove");
        remove.getStyleClass().add("button-ghost");
        remove.setOnAction(event -> remove(favorite));
        status.getChildren().addAll(new BadgeView(favorite.badgeTier()), availability, remove);

        card.getChildren().addAll(info, status);
        Motion.attachHoverLift(card, 3);
        setGraphic(card);
    }

    private void remove(FavoriteDonorView favorite) {
        if (!AlertUtil.confirm("Remove favorite", "Remove " + favorite.donorName() + " from your favorites?")) return;
        ServiceResult<Void> result = favoriteDonorService.removeFavorite(favorite.donorId());
        if (result.success()) {
            if (onRemoved != null) onRemoved.run();
        } else {
            AlertUtil.error("Couldn't remove favorite", result.message());
        }
    }

    private Node avatar(FavoriteDonorView favorite) {
        StackPane container = new StackPane();
        Label initials = new Label(DonorProfileDialog.initialsOf(favorite.donorName()));
        initials.getStyleClass().addAll("avatar-initials", "avatar-initials-sm");
        container.getChildren().add(initials);
        PhotoCache.getPhotoAsync(favorite.donorId(), photo -> {
            if (photo == null) return;
            ImageView view = new ImageView(photo);
            view.setFitWidth(48);
            view.setFitHeight(48);
            view.setPreserveRatio(false);
            view.setClip(com.bloodlink.util.Avatars.squareClip(48));
            view.getStyleClass().add("avatar-photo");
            container.getChildren().setAll(view);
        });
        return container;
    }

    private static Node iconText(String iconPath, String text) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.getStyleClass().add("match-card-detail");
        row.getChildren().addAll(Icons.icon(iconPath, 12, "match-card-icon"), label);
        return row;
    }
}
