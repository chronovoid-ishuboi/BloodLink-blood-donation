package com.bloodlink.view.shell;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.InputStream;

/**
 * The slot next to the BloodLink wordmark where the company logo goes.
 *
 * <h2>Why it is empty</h2>
 * Until {@code resources/com/bloodlink/images/company-logo.png} exists this
 * draws a dashed outline of the right size and nothing else. It deliberately
 * does not fall back to a drawn mark of my own: a stand-in logo sitting beside
 * the product name reads as the product's logo, and it would keep reading that
 * way for as long as nobody noticed it was a placeholder.
 *
 * <p>Shared by the sidebar and the sign-in screen so the two cannot drift to
 * different sizes or different fallback behaviour.
 */
public final class BrandMark {

    private BrandMark() { }

    public static Node node(double size) {
        StackPane holder = new StackPane();
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        holder.getStyleClass().add("nav-logo-slot");

        try (InputStream input = BrandMark.class.getResourceAsStream("/com/bloodlink/images/company-logo.png")) {
            if (input != null) {
                Image logo = new Image(input);
                if (!logo.isError()) {
                    ImageView view = new ImageView(logo);
                    view.setFitWidth(size);
                    view.setFitHeight(size);
                    view.setPreserveRatio(true);
                    holder.getChildren().add(view);
                    holder.getStyleClass().remove("nav-logo-slot");
                }
            }
        } catch (Exception ignored) {
            // Stays an empty slot.
        }
        return holder;
    }
}
