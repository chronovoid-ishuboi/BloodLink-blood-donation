package com.bloodlink.controller;

import com.bloodlink.model.*;
import com.bloodlink.service.AuthService;
import com.bloodlink.service.MedicalDocumentService;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.BackgroundTasks;
import com.bloodlink.util.NidScanDialog;
import com.bloodlink.util.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Stream;

public final class RegisterController {
    @FXML private ComboBox<Role> roleCombo;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextField districtField;
    @FXML private TextArea addressArea;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private PasswordField nidNumberField;
    @FXML private TextField guardianNameField;
    @FXML private TextField guardianPhoneField;
    @FXML private VBox donorFields;
    @FXML private ComboBox<BloodGroup> bloodGroupCombo;
    @FXML private DatePicker birthDatePicker;
    @FXML private TextField weightField;
    @FXML private TextField heightField;
    @FXML private DatePicker lastDonationPicker;
    @FXML private TextField chronicConditionsField;
    @FXML private CheckBox recentSurgeryCheck;
    @FXML private TextField recentSurgeryDetailsField;
    @FXML private CheckBox recentTattooCheck;
    @FXML private TextField recentTattooDetailsField;
    @FXML private CheckBox currentMedicationsCheck;
    @FXML private TextField currentMedicationsDetailsField;
    @FXML private CheckBox recentIllnessCheck;
    @FXML private TextField recentIllnessDetailsField;
    @FXML private CheckBox recentPregnancyCheck;
    @FXML private TextField recentPregnancyDetailsField;
    @FXML private Label errorLabel;
    @FXML private Button createButton;
    @FXML private Button scanNidButton;
    
    @FXML private ImageView profilePhotoView;
    @FXML private Label profileInitialsLabel;
    @FXML private javafx.scene.layout.HBox stepBar;
    @FXML private Label step1Label;
    @FXML private Label step2Label;
    @FXML private Label registerSubtitleLabel;
    @FXML private Button addDocumentButton;
    @FXML private Label documentHintLabel;
    @FXML private VBox documentListHost;

    private byte[] profilePhotoBytes = null;

    /**
     * Files chosen for upload, held here until the account row exists and can
     * own them. A List, and read in order, so the donor sees them in the order
     * they attached them.
     */
    private final List<MedicalDocumentService.PendingDocument> medicalDocuments = new java.util.ArrayList<>();

    private Runnable stepBarRefresh = () -> { };

    private final AuthService authService = new AuthService();
    private final MedicalDocumentService medicalDocumentService = new MedicalDocumentService();

    @FXML private javafx.scene.layout.HBox brandRow;

    @FXML private void initialize() {
        roleCombo.getItems().setAll(Role.DONOR, Role.REQUESTER);
        roleCombo.setValue(Role.DONOR);
        bloodGroupCombo.getItems().setAll(BloodGroup.values());
        errorLabel.setText("");
        brandRow.getChildren().add(com.bloodlink.view.shell.BrandMark.node(40));
        roleCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateDonorFields());
        updateDonorFields();
        setupToggleField(recentSurgeryCheck, recentSurgeryDetailsField);
        setupToggleField(recentTattooCheck, recentTattooDetailsField);
        setupToggleField(currentMedicationsCheck, currentMedicationsDetailsField);
        setupToggleField(recentIllnessCheck, recentIllnessDetailsField);
        setupToggleField(recentPregnancyCheck, recentPregnancyDetailsField);
        setupStepIndicator();
        renderDocumentList();
    }

    /**
     * Lights the step dots as each section of the form is filled in. The form is
     * one long scroll rather than a wizard, so this is progress feedback on a
     * 237-line form, not navigation -- nothing is gated on it, and a user can
     * still fill the sections in any order.
     *
     * <h2>What was wrong before</h2>
     * The dots were three fixed labels in the FXML and the third was lit by
     * {@code role != DONOR || healthFilled}. That is two bugs wearing one
     * expression. A requester saw dot 3 lit the instant the screen opened,
     * before typing anything, because "has no health section" was being
     * encoded as "has already finished it". A donor saw dot 3 unlit for the
     * whole form, which is correct but, sitting next to the requester's
     * always-green dot, reads as the indicator being broken for donors.
     * <p>
     * The dots are built here instead, from the sections the selected role
     * actually has: three for a donor, two for a requester. A dot is lit when
     * its own section is filled and never for any other reason, so an unlit
     * dot always means the same thing.
     */
    private void setupStepIndicator() {
        List<TextInputControl> identity = List.of(fullNameField);
        List<TextInputControl> contact = List.of(emailField, phoneField, districtField, passwordField);
        List<TextInputControl> health = List.of(weightField);

        Runnable refresh = () -> {
            List<Boolean> done = new java.util.ArrayList<>();
            done.add(allFilled(identity) && birthDatePicker.getValue() != null);
            done.add(allFilled(contact));
            if (roleCombo.getValue() == Role.DONOR) {
                done.add(allFilled(health) && !medicalDocuments.isEmpty());
            }
            renderStepBar(done);
        };

        Stream.of(identity, contact, health).flatMap(List::stream)
                .forEach(field -> field.textProperty().addListener((obs, old, value) -> refresh.run()));
        birthDatePicker.valueProperty().addListener((obs, old, value) -> refresh.run());
        roleCombo.valueProperty().addListener((obs, old, value) -> refresh.run());
        stepBarRefresh = refresh;
        refresh.run();
    }

    /** Rebuilds the dot row for however many sections the current role has. */
    private void renderStepBar(List<Boolean> done) {
        stepBar.getChildren().clear();
        for (int i = 0; i < done.size(); i++) {
            if (i > 0) {
                Region connector = new Region();
                connector.getStyleClass().add("step-connector");
                connector.setPrefWidth(46);
                stepBar.getChildren().add(connector);
            }
            Label dot = new Label(String.valueOf(i + 1));
            dot.getStyleClass().add("step-dot");
            if (done.get(i)) dot.getStyleClass().add("step-dot-active");
            stepBar.getChildren().add(dot);
        }
    }

    private static boolean allFilled(List<TextInputControl> fields) {
        return fields.stream().allMatch(field -> field.getText() != null && !field.getText().isBlank());
    }

    // ------------------------------------------------------ medical documents

    /**
     * Attaches a report or prescription.
     * <p>
     * The file is read into memory now rather than remembered as a path,
     * because the account it belongs to does not exist yet and will not until
     * the form is submitted -- and by then the person may well have moved or
     * renamed the file. Reading it at the moment they pick it is also the only
     * point at which "that file is 40MB" can be said while it still means
     * something to them.
     */
    @FXML private void addMedicalDocument() {
        if (medicalDocuments.size() >= MedicalDocumentService.MAX_FILES) {
            AlertUtil.info("That is enough",
                    "You can attach up to " + MedicalDocumentService.MAX_FILES + " files.");
            return;
        }

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Choose a medical report or prescription");
        List<String> patterns = new java.util.ArrayList<>();
        for (String extension : MedicalDocumentService.ALLOWED_EXTENSIONS) patterns.add("*." + extension);
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("Reports and images", patterns));

        java.io.File file = chooser.showOpenDialog(addDocumentButton.getScene().getWindow());
        if (file == null) return;

        if (!MedicalDocumentService.isAllowed(file.getName())) {
            AlertUtil.warning("Not a supported file",
                    "Attach a PDF or an image (" + String.join(", ", MedicalDocumentService.ALLOWED_EXTENSIONS) + ").");
            return;
        }
        if (file.length() > MedicalDocumentService.MAX_FILE_BYTES) {
            AlertUtil.warning("That file is too large",
                    String.format("%s is %.1f MB. The limit is %d MB per file -- a photo of the document is usually well under it.",
                            file.getName(), file.length() / (1024.0 * 1024.0),
                            MedicalDocumentService.MAX_FILE_BYTES / (1024 * 1024)));
            return;
        }

        try {
            byte[] content = java.nio.file.Files.readAllBytes(file.toPath());
            medicalDocuments.add(new MedicalDocumentService.PendingDocument(
                    file.getName(), MedicalDocumentService.contentTypeFor(file.getName()), content));
            renderDocumentList();
            stepBarRefresh.run();
        } catch (Exception e) {
            AlertUtil.warning("Could not read that file",
                    file.getName() + " could not be read: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void renderDocumentList() {
        documentListHost.getChildren().clear();
        for (int i = 0; i < medicalDocuments.size(); i++) {
            MedicalDocumentService.PendingDocument document = medicalDocuments.get(i);
            Label name = new Label(document.fileName());
            name.getStyleClass().add("document-row-name");
            Label size = new Label(document.readableSize());
            size.getStyleClass().add("helper-text");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button remove = new Button("Remove");
            remove.getStyleClass().add("button-ghost");
            int index = i;
            remove.setOnAction(event -> {
                medicalDocuments.remove(index);
                renderDocumentList();
                stepBarRefresh.run();
            });

            HBox row = new HBox(10, name, size, spacer, remove);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("document-row");
            documentListHost.getChildren().add(row);
        }
        updateDocumentHint();
    }

    private void updateDocumentHint() {
        if (medicalDocuments.isEmpty()) {
            documentHintLabel.setText("Required — at least one file");
            documentHintLabel.getStyleClass().removeAll("helper-text");
            if (!documentHintLabel.getStyleClass().contains("error-text")) {
                documentHintLabel.getStyleClass().add("error-text");
            }
        } else {
            documentHintLabel.setText(medicalDocuments.size() + " attached");
            documentHintLabel.getStyleClass().removeAll("error-text");
            if (!documentHintLabel.getStyleClass().contains("helper-text")) {
                documentHintLabel.getStyleClass().add("helper-text");
            }
        }
    }
    
    private void setupToggleField(CheckBox checkBox, TextField detailField) {
        detailField.visibleProperty().bind(checkBox.selectedProperty());
        detailField.managedProperty().bind(checkBox.selectedProperty());
    }

    private void updateDonorFields() {
        boolean donor = roleCombo.getValue() == Role.DONOR;
        donorFields.setVisible(donor);
        donorFields.setManaged(donor);

        // "STEP 1 OF 3" on a requester's form, which has two sections, is the
        // same wrong count the dot row was showing.
        int sections = donor ? 3 : 2;
        step1Label.setText("STEP 1 OF " + sections);
        step2Label.setText("STEP 2 OF " + sections);
        registerSubtitleLabel.setText(donor
                ? "Three short sections. Your details stay private until you accept a request."
                : "Two short sections. Your details stay private until a donor accepts your request.");

        // Switching to Requester leaves the health section hidden but its
        // attachments still in the list, which would then be written against a
        // requester account that was never asked for them.
        if (!donor && !medicalDocuments.isEmpty()) {
            medicalDocuments.clear();
            renderDocumentList();
        }
    }

    /**
     * Registration is a database write, so this now runs off the JavaFX
     * Application Thread like every other DB-triggered action in the app --
     * the same fix applied to dashboard polling, just never carried back to
     * this screen until now.
     */
    @FXML private void createAccount() {
        Double weight = null;
        Double height = null;
        if (roleCombo.getValue() == Role.DONOR) {
            // Checked before anything is written. An unapprovable account is
            // worse than a rejected form: the donor would be left waiting on a
            // decision an admin has no way to make.
            if (medicalDocuments.isEmpty()) {
                errorLabel.setText("Attach at least one medical report or prescription — "
                        + "an administrator needs it to approve your account.");
                documentListHost.requestFocus();
                return;
            }
            if (!weightField.getText().isBlank()) {
                try { weight = Double.parseDouble(weightField.getText().trim()); }
                catch (NumberFormatException e) { errorLabel.setText("Weight must be numeric."); return; }
            }
            if (!heightField.getText().isBlank()) {
                try { height = Double.parseDouble(heightField.getText().trim()); }
                catch (NumberFormatException e) { errorLabel.setText("Height must be numeric."); return; }
            }
        }
        RegistrationData data = new RegistrationData(roleCombo.getValue(), fullNameField.getText(), emailField.getText(),
                phoneField.getText(), districtField.getText(), addressArea.getText(), passwordField.getText(),
                nidNumberField.getText(), guardianNameField.getText(), guardianPhoneField.getText(),
                bloodGroupCombo.getValue(), birthDatePicker.getValue(), weight, lastDonationPicker.getValue(),
                height, chronicConditionsField.getText(),
                recentSurgeryCheck.isSelected(), recentSurgeryDetailsField.getText(),
                recentTattooCheck.isSelected(), recentTattooDetailsField.getText(),
                currentMedicationsCheck.isSelected(), currentMedicationsDetailsField.getText(),
                recentIllnessCheck.isSelected(), recentIllnessDetailsField.getText(),
                recentPregnancyCheck.isSelected(), recentPregnancyDetailsField.getText(),
                profilePhotoBytes);
        String confirmPassword = confirmPasswordField.getText();
        errorLabel.setText("");
        createButton.setDisable(true);
        // The documents are written on the same background task as the account
        // itself, straight after the insert that produced the id. Doing it in
        // the success callback instead would put a multi-megabyte upload on
        // the FX thread and freeze the window while it ran.
        List<MedicalDocumentService.PendingDocument> documents = List.copyOf(medicalDocuments);
        BackgroundTasks.run(
                () -> {
                    var registration = authService.register(data, confirmPassword);
                    if (registration.success() && registration.data() != null && !documents.isEmpty()) {
                        medicalDocumentService.storeForNewAccount(registration.data(), documents);
                    }
                    return registration;
                },
                result -> {
                    createButton.setDisable(false);
                    if (!result.success()) { errorLabel.setText(result.message()); return; }
                    AlertUtil.info("Account created", result.message());
                    SceneManager.showLogin();
                },
                error -> {
                    createButton.setDisable(false);
                    errorLabel.setText("Registration failed: " + (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName()));
                });
    }

    @FXML private void backToLogin() { SceneManager.showLogin(); }

    /**
     * Optional identity-registration assist, per the spec's required workflow:
     * upload -> OCR -> user reviews/edits -> user confirms -> only then does
     * anything touch a real form field. Nothing is auto-accepted and no
     * "verified" flag is ever set: this is a form-fill shortcut.
     * <p>
     * <b>Blood group is among the fields this pre-fills</b>, from the value the
     * user confirmed in the review dialog. The previous version of this comment
     * claimed it never did, which was simply not what the code below does --
     * worth correcting rather than leaving, because it is exactly the field where
     * a reader would want the documentation to be accurate. The review dialog
     * flags that field specifically, and the value stays editable on this form
     * afterwards; per {@link com.bloodlink.model.NidExtraction}, a scanned blood
     * group is never treated as proof of anything.
     * <p>
     * The scan itself runs off the JavaFX Application Thread, so this hands
     * {@link NidScanDialog} a callback rather than waiting on a return value.
     */
    @FXML private void scanNid() {
        NidScanDialog.show(scanNidButton.getScene().getWindow(), result -> {
            if (result.name() != null && !result.name().isBlank()) fullNameField.setText(result.name());
            if (result.birthDate() != null) birthDatePicker.setValue(result.birthDate());
            if (result.bloodGroup() != null) bloodGroupCombo.setValue(result.bloodGroup());
            if (result.address() != null && !result.address().isBlank()) addressArea.setText(result.address());
            if (result.nidNumber() != null && !result.nidNumber().isBlank()) nidNumberField.setText(result.nidNumber());
        });
    }

    @FXML private void uploadPhoto() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        java.io.File selectedFile = fileChooser.showOpenDialog(fullNameField.getScene().getWindow());
        if (selectedFile != null) {
            try {
                byte[] bytes = java.nio.file.Files.readAllBytes(selectedFile.toPath());
                if (bytes.length > 5 * 1024 * 1024) {
                    com.bloodlink.util.AlertUtil.error("File too large", "Profile photo must be smaller than 5 MB.");
                    return;
                }
                this.profilePhotoBytes = bytes;
                javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
                profilePhotoView.setImage(img);
                profilePhotoView.setVisible(true);
                profileInitialsLabel.setVisible(false);
            } catch (java.io.IOException e) {
                com.bloodlink.util.AlertUtil.error("Upload failed", "Could not read the selected image file.");
            }
        }
    }

    @FXML private void removePhoto() {
        this.profilePhotoBytes = null;
        profilePhotoView.setImage(null);
        profilePhotoView.setVisible(false);
        profileInitialsLabel.setText(fullNameField.getText().isBlank() ? "?" : fullNameField.getText().substring(0, 1).toUpperCase());
        profileInitialsLabel.setVisible(true);
    }
}
