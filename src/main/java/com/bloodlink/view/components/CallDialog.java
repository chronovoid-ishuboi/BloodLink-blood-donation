package com.bloodlink.view.components;

import com.bloodlink.util.Icons;
import com.bloodlink.util.Motion;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The calling screen: ringing state, connected state with a running timer,
 * and mute/speaker/end controls.
 *
 * <h2>What this does and does not do</h2>
 * This is the complete call <em>interface</em> and call-state machine. It does
 * not carry audio or video: there is no capture, no encoding and no peer
 * connection behind it yet, and it never claims otherwise to the person using
 * it -- the connected state is labelled as a preview, so nobody sits waiting
 * to be heard by someone who cannot hear them. Wiring a real media path in
 * later means replacing {@link #simulateConnect} with the real negotiation
 * and driving the same states from its callbacks; everything else here
 * (layout, timer, controls, teardown) stays as is.
 */
public final class CallDialog {

    public enum Kind { VOICE, VIDEO }

    private final String peerName;
    private final Kind kind;

    private final Label statusLabel = new Label("Calling...");
    private final Label timerLabel = new Label("");
    private Timeline ringPulse;
    private Timeline callTimer;
    private Timeline connectDelay;
    private int elapsedSeconds = 0;
    private boolean muted = false;

    private CallDialog(String peerName, Kind kind) {
        this.peerName = peerName;
        this.kind = kind;
    }

    public static void open(Window owner, String peerName, Kind kind) {
        new CallDialog(peerName, kind).show(owner);
    }

    private void show(Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle((kind == Kind.VIDEO ? "Video call" : "Voice call") + " — " + peerName);

        VBox shell = new VBox(18);
        shell.getStyleClass().add("call-shell");
        shell.setAlignment(Pos.CENTER);

        Label name = new Label(peerName);
        name.getStyleClass().add("call-peer-name");
        statusLabel.getStyleClass().add("call-status");
        timerLabel.getStyleClass().add("call-timer");

        shell.getChildren().addAll(avatar(), name, statusLabel, timerLabel, controls(stage));

        Scene scene = new Scene(shell, 360, 520);
        java.net.URL stylesheet = CallDialog.class.getResource("/com/bloodlink/css/theme.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        stage.setScene(scene);

        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - 360) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - 520) / 2);
        }

        stage.setOnHidden(event -> stopEverything());
        stage.show();
        simulateConnect();
    }

    private Node avatar() {
        Circle halo = new Circle(62);
        halo.getStyleClass().add("call-halo");

        Label initials = new Label(DonorProfileDialog.initialsOf(peerName));
        initials.getStyleClass().addAll("avatar-initials", "avatar-initials-lg");

        StackPane stack = new StackPane(halo, initials);
        stack.setAlignment(Pos.CENTER);

        // The halo pulses only while ringing, so the moment it stops is itself
        // the signal that the call state changed.
        ringPulse = new Timeline(
                new KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(halo.opacityProperty(), 0.85)),
                new KeyFrame(Duration.seconds(1.1), new javafx.animation.KeyValue(halo.opacityProperty(), 0.15)));
        ringPulse.setAutoReverse(true);
        ringPulse.setCycleCount(Animation.INDEFINITE);
        ringPulse.play();
        return stack;
    }

    private Node controls(Stage stage) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        Button mute = roundButton(Icons.BELL, "call-control");
        mute.setOnAction(event -> {
            muted = !muted;
            mute.getStyleClass().setAll("call-control", muted ? "call-control-active" : "");
            statusLabel.setText(muted ? "Microphone muted" : connectedStatus());
        });

        Button end = roundButton(Icons.X_CIRCLE, "call-control-end");
        end.setOnAction(event -> stage.close());

        row.getChildren().addAll(mute, end);
        return row;
    }

    private Button roundButton(String iconPath, String styleClass) {
        Button button = new Button();
        button.setGraphic(Icons.icon(iconPath, 20, "call-control-icon"));
        button.getStyleClass().add(styleClass);
        Motion.attachHoverLift(button, 2);
        return button;
    }

    /**
     * Moves from ringing to connected after a short delay. This is the single
     * seam where a real signalling/media path replaces the placeholder: the
     * states, timer and controls around it do not change.
     */
    private void simulateConnect() {
        connectDelay = new Timeline(new KeyFrame(Duration.seconds(2.2), event -> {
            if (ringPulse != null) ringPulse.stop();
            statusLabel.setText(connectedStatus());
            FadeTransition fade = new FadeTransition(Duration.millis(220), statusLabel);
            fade.setFromValue(0.3);
            fade.setToValue(1);
            fade.play();
            startTimer();
        }));
        connectDelay.play();
    }

    private String connectedStatus() {
        return kind == Kind.VIDEO ? "Video preview — audio not connected" : "Connected — audio not connected";
    }

    private void startTimer() {
        callTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            elapsedSeconds++;
            timerLabel.setText(String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60));
        }));
        callTimer.setCycleCount(Animation.INDEFINITE);
        callTimer.play();
    }

    /** Every Timeline here repeats forever or fires later, so all of them must stop when the window closes or they keep running for the life of the app. */
    private void stopEverything() {
        if (ringPulse != null) ringPulse.stop();
        if (callTimer != null) callTimer.stop();
        if (connectDelay != null) connectDelay.stop();
    }
}
