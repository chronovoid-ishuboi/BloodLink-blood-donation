package com.bloodlink.view.components;

import com.bloodlink.model.VoucherRedemption;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

public final class RedemptionHistoryCell extends ListCell<VoucherRedemption> {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");

    @Override
    protected void updateItem(VoucherRedemption redemption, boolean empty) {
        super.updateItem(redemption, empty);
        setText(null);
        if (empty || redemption == null) {
            setGraphic(null);
            return;
        }

        HBox card = new HBox(14);
        card.getStyleClass().add("timeline-card");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(redemption.voucherTitle() + " — " + redemption.partnerName());
        title.getStyleClass().add("timeline-title");
        Label date = new Label(redemption.redeemedAt().format(FORMAT) + " · " + redemption.pointsSpent() + " pts spent");
        date.getStyleClass().add("timeline-meta");
        info.getChildren().addAll(title, date);

        Label code = new Label(redemption.redemptionCode());
        code.getStyleClass().add("chip-blood-group");

        card.getChildren().addAll(info, code);
        setGraphic(card);
    }
}
