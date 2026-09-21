package com.bloodlink.view.shell;

import com.bloodlink.util.Icons;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;

/**
 * A reserved place for artwork that does not exist yet.
 *
 * <h2>How to add your own images</h2>
 * Drop a file into {@code src/main/resources/com/bloodlink/images/} using the
 * exact name the slot shows on screen, and it appears on the next run -- no
 * code change. Until then the slot draws itself as a labelled dashed frame,
 * so the layout is already the finished layout and nothing shifts when the
 * real art lands.
 * <p>
 * The name is also printed inside the placeholder rather than only living in
 * code, so the file to create is readable from the running app.
 *
 * <p>Slots currently used by the app are listed in {@link Names}.
 */
public final class ImageSlot extends StackPane {

    private static final String RESOURCE_DIR = "/com/bloodlink/images/";

    /** Every image the UI has a place for. Add the matching file to use one. */
    public static final class Names {
        private Names() { }
        public static final String DONOR_HERO = "donor-hero.jpg";
        public static final String REQUESTER_HERO = "requester-hero.jpg";
        public static final String LOGIN_ART = "login-art.jpg";
        public static final String REWARDS_BANNER = "rewards-banner.jpg";
        public static final String COMMUNITY_1 = "community-1.jpg";
        public static final String COMMUNITY_2 = "community-2.jpg";
        public static final String DONATION_STEP = "donation-step.jpg";
    }

    /**
     * @param fileName one of {@link Names}, or any file you place in the images folder
     * @param caption  shown in the placeholder so the slot's purpose is obvious on screen
     */
    public ImageSlot(String fileName, String caption, double width, double height) {
        this(fileName, caption, width, height, true);
    }

    /**
     * As above, but {@code showFileName} controls whether the placeholder
     * prints the file it is waiting for.
     *
     * <h2>Why that is a choice</h2>
     * Printing the path is the right behaviour on a work-in-progress screen:
     * it tells whoever is assembling the app exactly which file to create,
     * without them going back to the source. It is the wrong behaviour on the
     * About page, which is shown to an audience -- "team/member-1.jpg" on a
     * slide about the people who built this reads as an unfinished app, and
     * the person it is telling is not in the room.
     *
     * @param showFileName false on screens an end user or an audience sees
     */
    public ImageSlot(String fileName, String caption, double width, double height, boolean showFileName) {
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
        setAlignment(Pos.CENTER);

        Image image = load(fileName);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(width);
            view.setFitHeight(height);
            view.setPreserveRatio(false);
            view.setSmooth(true);
            // ImageView is not a Region, so the rounded corners have to be a clip.
            Rectangle clip = new Rectangle(width, height);
            clip.setArcWidth(40);
            clip.setArcHeight(40);
            view.setClip(clip);
            getChildren().add(view);
            return;
        }

        getStyleClass().add("image-slot");
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);

        if (showFileName) {
            Label title = new Label(caption);
            title.getStyleClass().add("image-slot-title");
            Label path = new Label(fileName);
            path.getStyleClass().add("image-slot-path");
            box.getChildren().addAll(new Group(Icons.icon(Icons.UPLOAD, 20, "image-slot-icon")), title, path);
        } else {
            // A quiet frame: the caption, and a picture glyph rather than an
            // upload arrow, so it reads as "a photo goes here" and not as an
            // instruction aimed at somebody who is not looking at it.
            Label title = new Label(caption);
            title.getStyleClass().add("image-slot-title");
            box.getChildren().addAll(new Group(Icons.icon(Icons.USER, 22, "image-slot-icon")), title);
        }
        getChildren().add(box);
    }

    private Image load(String fileName) {
        try (InputStream input = ImageSlot.class.getResourceAsStream(RESOURCE_DIR + fileName)) {
            if (input == null) return null;
            Image image = new Image(input);
            return image.isError() ? null : image;
        } catch (Exception e) {
            return null;
        }
    }
}
