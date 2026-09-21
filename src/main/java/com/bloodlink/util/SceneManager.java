package com.bloodlink.util;

import com.bloodlink.Main;
import com.bloodlink.model.Role;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;

public final class SceneManager {
    private static final double MIN_WIDTH = 1000;
    private static final double MIN_HEIGHT = 680;

    private static Stage stage;
    private SceneManager() { }

    public static void initialize(Stage primaryStage) { stage = primaryStage; }

    public static void showLogin() { setScene("login.fxml", "BloodLink — Sign in", 1060, 720); }
    public static void showRegister() { setScene("register.fxml", "BloodLink — Create account", 1120, 760); }

    public static void showDashboard(Role role) {
        switch (role) {
            case DONOR -> setScene("donor_dashboard.fxml", "BloodLink — Donor", 1380, 840);
            case REQUESTER -> setScene("requester_dashboard.fxml", "BloodLink — Requester", 1380, 840);
            case ADMIN -> setScene("admin_dashboard.fxml", "BloodLink — Admin", 1460, 900);
        }
    }

    public static void logout() {
        SessionManager.getInstance().clear();
        showLogin();
    }

    /**
     * Swaps the window's contents while leaving the window itself alone.
     *
     * <h2>Why the size is captured and put back</h2>
     * {@code new Scene(root, w, h)} hands the Stage a preferred size, and a
     * Stage that is already showing resizes itself to it. Every navigation --
     * sign in to create-account and back -- therefore snapped the window to
     * whatever literal that screen was declared with, undoing any resize or
     * maximise the person had done. The width/height arguments are now only a
     * <em>first</em> size: once there is a window on screen its geometry wins,
     * and the screen is laid out into it.
     * <p>
     * Position is preserved for the same reason, and centering only happens on
     * the very first screen -- re-centering a window the person has dragged
     * somewhere is the same bug wearing a different hat.
     */
    private static void setScene(String fxml, String title, double width, double height) {
        URL resource = Main.class.getResource("/com/bloodlink/view/" + fxml);
        if (resource == null) throw new IllegalStateException("Missing FXML resource: " + fxml);
        try {
            boolean alreadyOnScreen = stage.isShowing() && stage.getScene() != null;
            double keepWidth = stage.getWidth();
            double keepHeight = stage.getHeight();
            double keepX = stage.getX();
            double keepY = stage.getY();
            // Restoring an explicit width/height to a maximised or full-screen
            // window would silently un-maximise it, so those states are left to
            // manage themselves.
            boolean floating = !stage.isMaximized() && !stage.isFullScreen();

            Parent root = FXMLLoader.load(resource);
            Scene scene = alreadyOnScreen ? new Scene(root) : new Scene(root, width, height);
            Theme.register(scene);

            // Min sizes stay uniform across screens; per-screen minimums meant
            // one screen could force the window larger than the last.
            stage.setMinWidth(MIN_WIDTH);
            stage.setMinHeight(MIN_HEIGHT);
            stage.setScene(scene);
            stage.setTitle(title);

            if (alreadyOnScreen && floating) {
                stage.setWidth(keepWidth);
                stage.setHeight(keepHeight);
                stage.setX(keepX);
                stage.setY(keepY);
            } else if (!alreadyOnScreen) {
                stage.centerOnScreen();
            }
            stage.show();
            playEntrance(root);
        } catch (IOException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Unable to load screen: " + fxml + " (" + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()) + ")", cause);
        }
    }

    /**
     * Every screen swap is a hard cut with no transition at all today -- this
     * gives each new screen a brief fade-and-rise on entry so navigation reads
     * as one continuous app rather than a slideshow of static screens. A true
     * cross-fade would need the old and new roots layered in a shared StackPane
     * for the swap, which is a bigger structural change than this single-window,
     * whole-Scene-replacement navigator supports today; this is the change that
     * fits the current architecture and still fixes the "instant, jarring cut"
     * feel on every login/dashboard/logout transition.
     */
    private static void playEntrance(Parent root) {
        Motion.applyTo(root);
        root.setOpacity(0);
        root.setTranslateY(10);
        FadeTransition fade = new FadeTransition(Duration.millis(260), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(260), root);
        rise.setFromY(10);
        rise.setToY(0);
        rise.setInterpolator(Interpolator.SPLINE(0.2, 0.8, 0.2, 1));
        new ParallelTransition(fade, rise).play();
    }
}
