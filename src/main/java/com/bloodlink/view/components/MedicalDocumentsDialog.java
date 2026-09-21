package com.bloodlink.view.components;

import com.bloodlink.model.MedicalDocument;
import com.bloodlink.service.MedicalDocumentService;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.Icons;
import com.bloodlink.util.Theme;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/**
 * The medical reports a donor attached at registration, shown to an
 * administrator deciding whether to approve the account.
 *
 * <h2>Images inline, everything else saved out</h2>
 * A JPEG or PNG is rendered here, because the whole job is "look at it and
 * decide" and making someone save a file to their downloads folder first is
 * friction on exactly the step that matters. A PDF cannot be rendered without
 * a PDF library this project does not have, so those get a Save button that
 * writes the file and hands it to the system viewer -- honest about what it
 * is doing rather than showing a broken preview.
 *
 * <h2>Authorization</h2>
 * Nothing here decides who may look. {@link MedicalDocumentService} re-checks
 * the live session against each document's real owner before returning any
 * bytes, so opening this dialog from somewhere it should not be opened
 * produces an empty list rather than a leak.
 */
public final class MedicalDocumentsDialog {

    private MedicalDocumentsDialog() { }

    public static void show(Window owner, long userId, String personName) {
        MedicalDocumentService service = new MedicalDocumentService();

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Medical documents — " + personName);

        Label heading = new Label("Medical documents");
        heading.getStyleClass().add("display-md");
        Label sub = new Label("Attached by " + personName + " at registration.");
        sub.getStyleClass().add("section-head-sub");
        sub.setWrapText(true);

        VBox list = new VBox(12);
        Label loading = new Label("Loading…");
        loading.getStyleClass().add("helper-text");
        list.getChildren().add(loading);

        ScrollPane scroller = new ScrollPane(list);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(scroller, Priority.ALWAYS);

        Button close = new Button("Close");
        close.getStyleClass().add("button-pill");
        close.setOnAction(event -> stage.close());
        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(16, new VBox(2, heading, sub), scroller, footer);
        root.getStyleClass().add("dialog-body");
        root.setPrefSize(720, 640);

        Scene scene = new Scene(root);
        Theme.register(scene);
        stage.setScene(scene);

        BackgroundTasks.run(
                () -> service.listFor(userId),
                documents -> list.getChildren().setAll(render(service, documents, stage)),
                error -> list.getChildren().setAll(note("Could not load the documents.")));

        stage.showAndWait();
    }

    private static List<javafx.scene.Node> render(MedicalDocumentService service,
                                                  List<MedicalDocument> documents, Stage stage) {
        if (documents.isEmpty()) {
            // Said plainly, because for an admin this is a finding, not an
            // empty state: a donor account with no report is one they should
            // probably not approve yet.
            return List.of(note("No documents attached. This account cannot be verified from documents alone."));
        }
        List<javafx.scene.Node> cards = new java.util.ArrayList<>();
        for (MedicalDocument document : documents) cards.add(card(service, document, stage));
        return cards;
    }

    private static javafx.scene.Node card(MedicalDocumentService service, MedicalDocument document, Stage stage) {
        Label name = new Label(document.fileName());
        name.getStyleClass().add("document-row-name");
        name.setWrapText(true);

        String uploaded = document.uploadedAt() == null ? ""
                : document.uploadedAt().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"));
        Label meta = new Label(document.readableSize() + (uploaded.isBlank() ? "" : "  ·  " + uploaded));
        meta.getStyleClass().add("helper-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button save = new Button("Save a copy");
        save.getStyleClass().add("button-ghost");
        save.setOnAction(event -> saveCopy(service, document, stage));

        HBox header = new HBox(12, new VBox(1, name, meta), spacer, save);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, header);
        card.getStyleClass().add("surface-card");

        if (document.isImage()) {
            StackPane preview = new StackPane(new Group(Icons.icon(Icons.CLOCK, 18, "action-tile-icon")));
            preview.setMinHeight(200);
            card.getChildren().add(preview);
            BackgroundTasks.run(
                    () -> service.open(document.id()).orElse(null),
                    bytes -> {
                        if (bytes == null) {
                            preview.getChildren().setAll(note("This file could not be opened."));
                            return;
                        }
                        Image image = new Image(new java.io.ByteArrayInputStream(bytes));
                        if (image.isError()) {
                            preview.getChildren().setAll(note("This file is not a readable image."));
                            return;
                        }
                        ImageView view = new ImageView(image);
                        view.setPreserveRatio(true);
                        view.setSmooth(true);
                        view.setFitWidth(620);
                        preview.getChildren().setAll(view);
                    },
                    error -> preview.getChildren().setAll(note("This file could not be opened.")));
        } else {
            Label hint = new Label("PDF — save a copy to open it in your PDF viewer.");
            hint.getStyleClass().add("helper-text");
            card.getChildren().add(hint);
        }
        return card;
    }

    private static void saveCopy(MedicalDocumentService service, MedicalDocument document, Stage stage) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save " + document.fileName());
        chooser.setInitialFileName(document.fileName());
        java.io.File target = chooser.showSaveDialog(stage);
        if (target == null) return;

        BackgroundTasks.run(
                () -> {
                    byte[] bytes = service.open(document.id())
                            .orElseThrow(() -> new IllegalStateException("The file could not be read."));
                    java.nio.file.Files.write(target.toPath(), bytes);
                    return target;
                },
                saved -> {
                    try {
                        java.awt.Desktop.getDesktop().open(saved);
                    } catch (Exception e) {
                        AlertUtil.info("Saved", "Saved to " + saved.getAbsolutePath());
                    }
                },
                error -> AlertUtil.warning("Could not save the file",
                        error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("helper-text");
        label.setWrapText(true);
        return label;
    }
}
