package com.bloodlink.view.components;

import com.bloodlink.model.ChatMessage;
import com.bloodlink.service.ChatService;
import com.bloodlink.service.ServiceResult;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.Icons;
import com.bloodlink.util.Motion;
import com.bloodlink.util.PushClient;
import com.bloodlink.util.SessionManager;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The conversation between a requester and a matched donor, in its own modal
 * window: a scrolling bubble thread, a composer, and the call controls.
 * <p>
 * Incoming messages arrive two ways, and it needs both: a
 * {@link PushClient#onChat} nudge reloads the thread the moment the other
 * side sends something, and the reload is also run on open. If the push
 * server isn't running the nudge simply never comes and the thread is still
 * correct on the next open -- the same best-effort contract the rest of the
 * push layer already has. The listener is removed on close, otherwise every
 * chat window ever opened in a session would keep reloading in the
 * background.
 */
public final class ChatDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    private final ChatService chatService = new ChatService();
    private final long requestId;
    private final long otherUserId;
    private final String otherUserName;
    private final long selfId;

    private final VBox thread = new VBox(8);
    private final ScrollPane scroller = new ScrollPane();

    private ChatDialog(long requestId, long otherUserId, String otherUserName) {
        this.requestId = requestId;
        this.otherUserId = otherUserId;
        this.otherUserName = otherUserName;
        this.selfId = SessionManager.getInstance().getCurrentUser().getId();
    }

    public static void open(Window owner, long requestId, long otherUserId, String otherUserName) {
        new ChatDialog(requestId, otherUserId, otherUserName).show(owner);
    }

    private void show(Window owner) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle("Chat with " + otherUserName);

        VBox shell = new VBox();
        shell.getStyleClass().add("chat-shell");
        shell.getChildren().addAll(header(stage), threadArea(), composer());

        Scene scene = new Scene(shell, 480, 620);
        java.net.URL stylesheet = ChatDialog.class.getResource("/com/bloodlink/css/theme.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        stage.setScene(scene);

        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - 480) / 2);
            stage.setY(owner.getY() + (owner.getHeight() - 620) / 2);
        }

        Runnable onIncoming = this::reload;
        PushClient.getInstance().onChat(onIncoming);
        stage.setOnHidden(event -> PushClient.getInstance().removeChatListener(onIncoming));

        stage.show();
        reload();
    }

    private Node header(Stage stage) {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("chat-header");
        bar.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        Label initials = new Label(DonorProfileDialog.initialsOf(otherUserName));
        initials.getStyleClass().addAll("avatar-initials", "avatar-initials-sm");
        avatar.getChildren().add(initials);

        VBox identity = new VBox(2);
        Label name = new Label(otherUserName);
        name.getStyleClass().add("chat-header-name");
        Label context = new Label("Request #" + requestId);
        context.getStyleClass().add("chat-header-context");
        identity.getChildren().addAll(name, context);

        Button voice = callButton(Icons.PHONE, "Voice call", CallDialog.Kind.VOICE, stage);
        Button video = callButton(Icons.USERS, "Video call", CallDialog.Kind.VIDEO, stage);

        bar.getChildren().addAll(avatar, identity, new Region(), voice, video);
        HBox.setHgrow(bar.getChildren().get(2), Priority.ALWAYS);
        return bar;
    }

    private Button callButton(String iconPath, String tooltip, CallDialog.Kind kind, Stage owner) {
        Button button = new Button();
        button.setGraphic(Icons.icon(iconPath, 15, "chat-call-icon"));
        button.getStyleClass().add("chat-call-button");
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setOnAction(event -> CallDialog.open(owner, otherUserName, kind));
        Motion.attachHoverLift(button, 2);
        return button;
    }

    private Node threadArea() {
        thread.setPadding(new Insets(16));
        scroller.setContent(thread);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().addAll("transparent-scroll", "chat-thread");
        VBox.setVgrow(scroller, Priority.ALWAYS);
        return scroller;
    }

    private Node composer() {
        HBox box = new HBox(10);
        box.getStyleClass().add("chat-composer");
        box.setAlignment(Pos.CENTER);

        TextField input = new TextField();
        input.setPromptText("Write a message...");
        HBox.setHgrow(input, Priority.ALWAYS);

        Button send = new Button("Send");
        send.getStyleClass().add("button-primary");
        Motion.attachHoverLift(send, 2);

        Runnable doSend = () -> {
            String body = input.getText();
            if (body == null || body.isBlank()) return;
            input.clear();
            ServiceResult<Void> result = chatService.send(requestId, otherUserId, body);
            if (!result.success()) {
                com.bloodlink.util.AlertUtil.error("Message not sent", result.message());
                input.setText(body);
                return;
            }
            reload();
        };
        send.setOnAction(event -> doSend.run());
        input.setOnAction(event -> doSend.run());

        box.getChildren().addAll(input, send);
        return box;
    }

    private void reload() {
        BackgroundTasks.run(() -> chatService.thread(requestId, otherUserId),
                result -> {
                    if (!result.success()) {
                        thread.getChildren().setAll(systemNote(result.message()));
                        return;
                    }
                    render(result.data());
                },
                error -> thread.getChildren().setAll(systemNote(error.getMessage())));
    }

    private void render(List<ChatMessage> messages) {
        thread.getChildren().clear();
        if (messages.isEmpty()) {
            thread.getChildren().add(systemNote("No messages yet. Say hello — you're both working on request #"
                    + requestId + "."));
            return;
        }
        for (ChatMessage message : messages) {
            thread.getChildren().add(bubble(message));
        }
        // Layout has not run yet for the nodes just added, so the scroll to the
        // bottom has to wait a pulse or it scrolls against the old height.
        javafx.application.Platform.runLater(() -> scroller.setVvalue(1.0));
    }

    private Node bubble(ChatMessage message) {
        boolean mine = message.senderId() == selfId;

        Label body = new Label(message.body());
        body.setWrapText(true);
        body.getStyleClass().add(mine ? "chat-bubble-mine" : "chat-bubble-theirs");
        body.setMaxWidth(300);

        Label meta = new Label(message.sentAt().format(TIME));
        meta.getStyleClass().add("chat-bubble-time");

        VBox stack = new VBox(3, body, meta);
        stack.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        HBox row = new HBox(stack);
        row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        FadeTransition fade = new FadeTransition(Duration.millis(180), row);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        return row;
    }

    private Node systemNote(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("helper-text");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
