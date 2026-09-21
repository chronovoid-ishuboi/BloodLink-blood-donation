package com.bloodlink.view.shell;

import com.bloodlink.util.Icons;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Rotating awareness stories on the home page: an image with the headline
 * over it, advancing on its own and clickable through to the source.
 *
 * <h2>Editing it</h2>
 * The content lives in {@code resources/com/bloodlink/news/news.json} and is
 * read at runtime, so adding a story is editing that file -- no code change.
 * Each entry names an image file to place in
 * {@code resources/com/bloodlink/images/news/}; an entry whose image is not
 * there yet still shows, with a labelled placeholder naming the file it
 * wants, so the carousel is never broken by missing art.
 * <p>
 * The same data-driven shape as {@code badges.json}, for the same reason:
 * content someone will want to change should not require a rebuild.
 */
public final class NewsCarousel extends VBox {

    private static final Duration DWELL = Duration.seconds(6);

    private record Story(String title, String source, String image, String url) { }

    private final List<Story> stories = new ArrayList<>();
    private final StackPane stage = new StackPane();
    private final HBox dots = new HBox(6);
    private int index = 0;
    private Timeline rotation;

    public NewsCarousel(double width, double height) {
        getStyleClass().add("news-carousel");
        setSpacing(10);
        load();

        stage.setMinSize(width, height);
        stage.setPrefSize(width, height);
        stage.setMaxSize(width, height);
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        stage.setClip(clip);

        dots.setAlignment(Pos.CENTER);

        getChildren().addAll(stage, dots);

        if (stories.isEmpty()) {
            stage.getChildren().add(new ImageSlot("news-1.png", "News image", width, height));
            return;
        }
        render();
        startRotation();
    }

    private void load() {
        try (InputStream input = NewsCarousel.class.getResourceAsStream("/com/bloodlink/news/news.json")) {
            if (input == null) return;
            JSONObject root = new JSONObject(new JSONTokener(input));
            JSONArray items = root.optJSONArray("items");
            if (items == null) return;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                stories.add(new Story(
                        item.optString("title", ""),
                        item.optString("source", ""),
                        item.optString("image", ""),
                        item.optString("url", "")));
            }
        } catch (Exception e) {
            // A malformed or missing file must not take the home page down;
            // the placeholder above stands in instead.
            stories.clear();
        }
    }

    private void render() {
        Story story = stories.get(index);
        Node card = card(story);
        card.setOpacity(0);
        stage.getChildren().setAll(card);
        FadeTransition fade = new FadeTransition(Duration.millis(420), card);
        fade.setToValue(1);
        fade.play();
        renderDots();
    }

    private Node card(Story story) {
        StackPane holder = new StackPane();
        holder.setAlignment(Pos.BOTTOM_LEFT);

        double width = stage.getPrefWidth();
        double height = stage.getPrefHeight();

        Image image = loadImage(story.image());
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(width);
            view.setFitHeight(height);
            view.setPreserveRatio(false);
            view.setSmooth(true);
            holder.getChildren().add(view);
        } else {
            holder.getChildren().add(new ImageSlot(story.image(), "News image", width, height));
        }

        // A scrim so white headline type stays readable over any photograph,
        // rather than depending on the image happening to be dark at the
        // bottom.
        Region scrim = new Region();
        scrim.getStyleClass().add("news-scrim");
        scrim.setMinHeight(height);
        holder.getChildren().add(scrim);

        Label source = new Label(story.source());
        source.getStyleClass().add("news-source");

        Label title = new Label(story.title());
        title.getStyleClass().add("news-title");
        title.setWrapText(true);
        title.setMaxWidth(width - 44);

        VBox text = new VBox(4, source, title);
        text.getStyleClass().add("news-text");
        // A StackPane stretches its children, so without pinning the height to
        // the content the text block fills the card and its BOTTOM_LEFT
        // alignment does nothing -- the headline ends up at the top, over the
        // transparent end of the scrim, which is where it is least readable.
        text.setMaxHeight(Region.USE_PREF_SIZE);

        if (story.url() != null && !story.url().isBlank()) {
            HBox link = new HBox(6, new Label("Read the source"),
                    new Group(Icons.icon(Icons.CHEVRON_RIGHT, 10, "news-link-icon")));
            link.setAlignment(Pos.CENTER_LEFT);
            link.getStyleClass().add("news-link");
            text.getChildren().add(link);

            holder.setOnMouseClicked(event -> openUrl(story.url()));
            holder.setStyle("-fx-cursor: hand;");
        }

        holder.getChildren().add(text);
        return holder;
    }

    private void renderDots() {
        dots.getChildren().clear();
        for (int i = 0; i < stories.size(); i++) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add(i == index ? "news-dot-active" : "news-dot");
            int target = i;
            dot.setOnMouseClicked(event -> {
                index = target;
                render();
                restartRotation();
            });
            dot.setStyle("-fx-cursor: hand;");
            dots.getChildren().add(dot);
        }
    }

    private void startRotation() {
        if (stories.size() < 2) return;
        rotation = new Timeline(new KeyFrame(DWELL, event -> {
            index = (index + 1) % stories.size();
            render();
        }));
        rotation.setCycleCount(Animation.INDEFINITE);
        rotation.play();
    }

    private void restartRotation() {
        if (rotation != null) rotation.playFromStart();
    }

    private Image loadImage(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        try (InputStream input = NewsCarousel.class.getResourceAsStream("/com/bloodlink/images/news/" + fileName)) {
            if (input == null) return null;
            Image image = new Image(input);
            return image.isError() ? null : image;
        } catch (Exception e) {
            return null;
        }
    }

    private void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception e) {
            com.bloodlink.util.AlertUtil.warning("Could not open the link",
                    "Open this address in your browser:\n\n" + url);
        }
    }

    /** Stops the rotation timer; call when the page holding this goes away for good. */
    public void stop() {
        if (rotation != null) rotation.stop();
    }
}
