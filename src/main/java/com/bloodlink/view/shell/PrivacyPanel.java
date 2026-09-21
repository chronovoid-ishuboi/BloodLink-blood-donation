package com.bloodlink.view.shell;

import com.bloodlink.service.LeaderboardService;
import com.bloodlink.service.ProfileService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.Motion;
import com.bloodlink.util.SessionManager;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Privacy, visibility and the irreversible end of the account.
 * <p>
 * Deactivation and deletion are deliberately not the same control.
 * Deactivating takes you out of matching and can be undone by signing in
 * again; deleting cannot be undone at all, so it asks you to type your email
 * to confirm rather than accepting a single click on a red button. Anything
 * that cannot be taken back should be harder to do by accident than
 * something that can.
 */
public final class PrivacyPanel extends VBox {

    private final LeaderboardService leaderboardService = new LeaderboardService();
    private final ProfileService profileService = new ProfileService();
    private final long userId;
    private final String email;
    private final Runnable onAccountClosed;

    public PrivacyPanel(long userId, String email, Runnable onAccountClosed) {
        this.userId = userId;
        this.email = email;
        this.onAccountClosed = onAccountClosed;
        setSpacing(18);
        getChildren().addAll(leaderboardVisibility(), dataSection(), dangerZone());
    }

    private Node leaderboardVisibility() {
        Label title = new Label("Leaderboard visibility");
        title.getStyleClass().add("section-head-title");

        CheckBox anonymous = new CheckBox("Appear as \"Anonymous donor\" on the leaderboard");
        anonymous.setSelected(leaderboardService.isAnonymous(userId));

        Label note = new Label("Your rank and donation count still count exactly the same. "
                + "Only your name and district are withheld from other people.");
        note.getStyleClass().add("section-head-sub");
        note.setWrapText(true);

        anonymous.setOnAction(event -> {
            ServiceResult<Void> result = leaderboardService.setAnonymous(anonymous.isSelected());
            if (!result.success()) {
                anonymous.setSelected(!anonymous.isSelected());
                AlertUtil.error("Could not change visibility", result.message());
                return;
            }
            AlertUtil.info("Visibility updated", result.message());
        });

        VBox card = new VBox(10, title, anonymous, note);
        card.getStyleClass().add("surface-card");
        return card;
    }

    private Node dataSection() {
        Label title = new Label("Your data");
        title.getStyleClass().add("section-head-title");
        Label note = new Label("Download everything BloodLink holds about you: your profile, "
                + "requests, verified donation history and reward activity.");
        note.getStyleClass().add("section-head-sub");
        note.setWrapText(true);

        Button export = new Button("Export my data (JSON)");
        export.getStyleClass().add("button-pill");
        export.setOnAction(event -> exportData(export));
        Motion.attachHoverLift(export, 2);

        VBox card = new VBox(10, title, note, export);
        card.getStyleClass().add("surface-card");
        return card;
    }

    private void exportData(Button trigger) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save your BloodLink data");
        chooser.setInitialFileName("bloodlink-data.json");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File target = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (target == null) return;

        trigger.setDisable(true);
        BackgroundTasks.<String>run(() -> profileService.exportAccountData(userId),
                (String json) -> {
                    trigger.setDisable(false);
                    try {
                        java.nio.file.Files.writeString(target.toPath(), json,
                                java.nio.charset.StandardCharsets.UTF_8);
                        AlertUtil.info("Export saved", "Your data was written to:\n" + target.getAbsolutePath());
                    } catch (Exception e) {
                        AlertUtil.error("Could not save the file", e.getMessage());
                    }
                },
                error -> {
                    trigger.setDisable(false);
                    AlertUtil.error("Export failed", error.getMessage());
                });
    }

    private Node dangerZone() {
        Label title = new Label("Closing your account");
        title.getStyleClass().add("danger-zone-title");

        Label deactivateNote = new Label("Deactivating takes you out of donor matching and hides your "
                + "profile. Signing in again brings it back.");
        deactivateNote.getStyleClass().add("section-head-sub");
        deactivateNote.setWrapText(true);

        Button deactivate = new Button("Deactivate my account");
        deactivate.getStyleClass().add("button-ghost");
        deactivate.setOnAction(event -> deactivate());

        Label deleteNote = new Label("Deleting is permanent. Your profile, verified donation history and "
                + "reward balance are removed and cannot be restored.");
        deleteNote.getStyleClass().add("section-head-sub");
        deleteNote.setWrapText(true);

        Button delete = new Button("Delete my account permanently");
        delete.getStyleClass().add("button-danger");
        delete.setOnAction(event -> deleteAccount());

        VBox card = new VBox(12, title, deactivateNote, deactivate,
                new Region(), deleteNote, delete);
        card.getStyleClass().add("danger-zone");
        return card;
    }

    private void deactivate() {
        if (!AlertUtil.confirm("Deactivate account",
                "You will stop appearing in donor matching and your profile will be hidden.\n\n"
                        + "Signing in again reactivates it. Continue?")) return;
        ServiceResult<Void> result = profileService.deactivateAccount(userId);
        if (!result.success()) {
            AlertUtil.error("Could not deactivate", result.message());
            return;
        }
        AlertUtil.info("Account deactivated", result.message());
        if (onAccountClosed != null) onAccountClosed.run();
    }

    /**
     * Typing the account's own email is the confirmation, rather than a
     * second yes/no box. A confirmation you can clear by clicking the same
     * spot twice is not a confirmation for something that cannot be undone.
     */
    private void deleteAccount() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Delete account permanently");
        dialog.setHeaderText("This cannot be undone.");
        dialog.setContentText("Type " + email + " to confirm:");
        AlertUtil.applyTheme(dialog.getDialogPane());

        Optional<String> typed = dialog.showAndWait();
        if (typed.isEmpty()) return;
        if (!email.equalsIgnoreCase(typed.get().trim())) {
            AlertUtil.warning("Not deleted", "That did not match your email address, so nothing was changed.");
            return;
        }
        ServiceResult<Void> result = profileService.deleteAccount(userId);
        if (!result.success()) {
            AlertUtil.error("Could not delete the account", result.message());
            return;
        }
        AlertUtil.info("Account deleted", result.message());
        if (onAccountClosed != null) onAccountClosed.run();
    }
}
