package com.bloodlink.controller;

import com.bloodlink.model.Role;
import com.bloodlink.service.AuthService;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.SceneManager;
import com.bloodlink.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public final class LoginController {
    @FXML private javafx.scene.layout.HBox brandRow;
    @FXML private ComboBox<Role> roleCombo;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button signInButton;
    @FXML private javafx.scene.layout.VBox loginArtHost;
    @FXML private javafx.scene.layout.HBox authStatsHost;

    private final AuthService authService = new AuthService();

    @FXML private void initialize() {
        roleCombo.getItems().setAll(Role.DONOR, Role.REQUESTER, Role.ADMIN);
        roleCombo.setValue(Role.DONOR);
        brandRow.getChildren().add(0, com.bloodlink.view.shell.BrandMark.node(40));
        loginArtHost.getChildren().add(new com.bloodlink.view.shell.Heartbeat(424, 168, 4.2));
        errorLabel.setText("");
        passwordField.setOnAction(event -> signIn());
        loadCoverage();
    }

    /**
     * The directory's real size, counted from the database rather than written
     * into the layout. The figures moved around as the hospital import grew and
     * a hard-coded "24 hospitals" was simply wrong the moment it did; asking
     * the database costs one query on a screen that is already waiting for the
     * person to type.
     * <p>
     * Off the FX thread, and a failure leaves the strip empty rather than
     * blocking sign-in -- nobody is kept out of the app because a count did not
     * come back.
     */
    private void loadCoverage() {
        BackgroundTasks.run(() -> new com.bloodlink.dao.HospitalDAO().coverage(),
                coverage -> {
                    if (coverage.hospitals() <= 0) return;
                    authStatsHost.getChildren().setAll(
                            stat(coverage.hospitals(), " hospitals", "in the directory"),
                            stat(coverage.districts(), " districts", "covered nationwide"));
                },
                error -> { });
    }

    private javafx.scene.layout.VBox stat(long value, String suffix, String caption) {
        com.bloodlink.view.shell.CountUp counter = new com.bloodlink.view.shell.CountUp(value, suffix);
        counter.getStyleClass().add("auth-stat-value");
        counter.play();
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("auth-stat-label");
        return new javafx.scene.layout.VBox(1, counter, captionLabel);
    }

    /**
     * Credential checking is a database call, so this now runs off the JavaFX
     * Application Thread like every other DB-triggered action in the app --
     * the same fix applied to dashboard polling, just never carried back to
     * this screen until now.
     */
    @FXML private void signIn() {
        errorLabel.setText("");
        signInButton.setDisable(true);
        String email = emailField.getText();
        String password = passwordField.getText();
        Role role = roleCombo.getValue();
        BackgroundTasks.run(() -> authService.login(email, password, role),
                result -> {
                    signInButton.setDisable(false);
                    if (!result.success()) { errorLabel.setText(result.message()); return; }
                    SessionManager.getInstance().setCurrentUser(result.data());
                    SceneManager.showDashboard(result.data().getRole());
                },
                error -> {
                    signInButton.setDisable(false);
                    errorLabel.setText("Sign in failed: " + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
                });
    }

    @FXML private void openRegistration() { SceneManager.showRegister(); }
}
