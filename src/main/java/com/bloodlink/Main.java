package com.bloodlink;

import com.bloodlink.dao.UserDAO;
import com.bloodlink.util.AlertUtil;
import com.bloodlink.util.AppConfig;
import com.bloodlink.util.DatabaseSetup;
import com.bloodlink.util.DBConnection;
import com.bloodlink.util.PhotoCache;
import com.bloodlink.util.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public final class Main extends Application {
    @Override public void start(Stage stage) {
        com.bloodlink.util.Fonts.load();
        // Initialize PhotoCache with a UserDAO instance to fetch images lazily
        PhotoCache.setUserDAO(new UserDAO());
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("crash.log", true));
                pw.println("Uncaught exception in thread " + thread.getName() + " at " + java.time.LocalDateTime.now());
                throwable.printStackTrace(pw);
                pw.close();
            } catch (Exception e) {}
            throwable.printStackTrace();
            Platform.runLater(() -> AlertUtil.error("Unexpected error", "An unexpected error occurred. See the application log for details."));
        });
        SceneManager.initialize(stage);
        DatabaseSetup.ensureInitialized();
        SceneManager.showLogin();
        if (!DBConnection.testConnection()) {
            Platform.runLater(() -> AlertUtil.warning("Database connection unavailable",
                    "BloodLink could not reach the database at " + AppConfig.get("db.url") + ".\n\n"
                    + "Check your network connection and DB_URL/DB_USERNAME/DB_PASSWORD settings, then restart the application."));
        }
    }

    /**
     * Rendering properties, set before {@code launch()} because Prism reads
     * them once while the toolkit starts and ignores later changes.
     * <p>
     * {@code prism.lcdtext=false} is the one that matters for how the app
     * looks: JavaFX defaults to LCD subpixel antialiasing, which puts colour
     * fringes on glyph edges and reads as "pixelated" text, and it degrades
     * badly over the coloured and gradient backgrounds this UI uses. Greyscale
     * antialiasing is what the design was drawn against.
     * <p>
     * The others ask for the hardware pipeline explicitly and raise the tile
     * cache, since this UI scrolls large areas of rounded, shadowed cards.
     */
    private static void applyRenderingHints() {
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        System.setProperty("prism.order", "d3d,sw");
        System.setProperty("prism.vsync", "true");
        System.setProperty("prism.maxvram", "512m");
    }

    public static void main(String[] args) {
        applyRenderingHints();
        launch(args);
    }
}
