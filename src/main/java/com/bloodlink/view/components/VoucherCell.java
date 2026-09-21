package com.bloodlink.view.components;

import com.bloodlink.model.Voucher;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.service.VoucherService;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.Motion;
import com.bloodlink.util.SessionManager;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One redeemable voucher, shown to a donor. Redemption happens directly from
 * here (calling {@link VoucherService} itself, not routed back through a
 * controller method) in the same spirit as {@link RequesterMatchCell}
 * opening {@link DonorProfileDialog} directly -- a list cell is already a
 * self-contained enough unit of UI to own this one action.
 */
public final class VoucherCell extends ListCell<Voucher> {
    private final VoucherService voucherService = new VoucherService();
    private final Runnable onRedeemed;

    /** @param onRedeemed run after a successful redemption, so the caller can refresh balance/history. */
    public VoucherCell(Runnable onRedeemed) {
        this.onRedeemed = onRedeemed;
    }

    @Override
    protected void updateItem(Voucher voucher, boolean empty) {
        super.updateItem(voucher, empty);
        setText(null);
        if (empty || voucher == null) {
            setGraphic(null);
            return;
        }

        HBox card = new HBox(14);
        card.getStyleClass().add("match-card");
        card.setAlignment(Pos.CENTER_LEFT);

        Label initials = new Label(initialsOf(voucher.partnerName()));
        initials.getStyleClass().addAll("avatar-initials", "avatar-initials-sm");

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(voucher.title());
        title.getStyleClass().add("match-card-name");
        Label partner = new Label(voucher.partnerName());
        partner.getStyleClass().add("match-card-detail");
        Label description = new Label(voucher.description());
        description.getStyleClass().add("helper-text");
        description.setWrapText(true);
        info.getChildren().addAll(title, partner, description);

        VBox action = new VBox(8);
        action.setAlignment(Pos.CENTER_RIGHT);
        Label cost = new Label(voucher.pointsCost() + " pts");
        cost.getStyleClass().add("chip-blood-group");
        Button redeem = new Button("Redeem");
        redeem.getStyleClass().add("button-primary");
        redeem.setOnAction(event -> redeem(voucher));
        action.getChildren().addAll(cost, redeem);

        card.getChildren().addAll(initials, info, action);
        Motion.attachHoverLift(card, 3);
        setGraphic(card);
    }

    private void redeem(Voucher voucher) {
        if (!AlertUtil.confirm("Redeem voucher",
                "Redeem \"" + voucher.title() + "\" for " + voucher.pointsCost() + " points?")) return;
        long donorId = SessionManager.getInstance().getCurrentUser().getId();
        ServiceResult<com.bloodlink.model.VoucherRedemption> result = voucherService.redeem(donorId, voucher.id());
        if (result.success()) {
            AlertUtil.info("Redeemed!", result.message());
            if (onRedeemed != null) onRedeemed.run();
        } else {
            AlertUtil.error("Redemption failed", result.message());
        }
    }

    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1 ? parts[0].substring(0, 1).toUpperCase()
                : (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
