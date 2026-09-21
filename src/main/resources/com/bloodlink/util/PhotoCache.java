package com.bloodlink.util;

import com.bloodlink.dao.UserDAO;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * An in-memory cache for user profile photos to prevent N+1 database blob fetches
 * and high memory usage when displaying multiple profile cards.
 */
public class PhotoCache {

    private static final Map<Long, Image> cache = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> noPhotoCache = new ConcurrentHashMap<>();
    private static UserDAO userDAO;

    public static void setUserDAO(UserDAO dao) {
        userDAO = dao;
    }

    /**
     * Retrieves the profile photo for a user, delivering the result via
     * {@code onLoaded} on the JavaFX Application Thread. A cache hit (including
     * a cached "no photo" result) calls back immediately and synchronously;
     * a cache miss runs the BLOB fetch on a background thread first, so callers
     * on the FX thread (list-cell rendering, dialog construction) never block
     * on a JDBC round trip the first time a given user's photo is shown.
     */
    public static void getPhotoAsync(long userId, Consumer<Image> onLoaded) {
        if (cache.containsKey(userId)) {
            onLoaded.accept(cache.get(userId));
            return;
        }
        if (noPhotoCache.containsKey(userId) || userDAO == null) {
            onLoaded.accept(null);
            return;
        }
        BackgroundTasks.run(
                () -> {
                    Optional<byte[]> photoBytesOpt = userDAO.findPhoto(userId);
                    if (photoBytesOpt.isPresent() && photoBytesOpt.get().length > 0) {
                        Image img = new Image(new ByteArrayInputStream(photoBytesOpt.get()));
                        cache.put(userId, img);
                        return img;
                    }
                    noPhotoCache.put(userId, true);
                    return null;
                },
                onLoaded,
                error -> {
                    System.err.println("Error fetching photo for user " + userId + ": " + error.getMessage());
                    onLoaded.accept(null);
                });
    }

    /**
     * Clears the cache for a specific user (e.g., when they upload a new photo).
     */
    public static void invalidate(long userId) {
        cache.remove(userId);
        noPhotoCache.remove(userId);
    }

    /**
     * Clears the entire cache.
     */
    public static void clear() {
        cache.clear();
        noPhotoCache.clear();
    }
}
