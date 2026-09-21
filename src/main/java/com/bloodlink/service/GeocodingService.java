package com.bloodlink.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a typed place into real coordinates, and coordinates back into a
 * readable address, using OpenStreetMap's Nominatim service.
 *
 * <h2>Why this and not a "detect my GPS" button</h2>
 * A desktop app has no GPS. The two things that look like one are:
 * <ul>
 *   <li><b>IP geolocation</b>, which is what the app used to rely on. It is
 *       city-level at best -- measured from this machine it lands ~100km from
 *       the actual address, which is exactly the "takes me somewhere I'm not"
 *       problem.</li>
 *   <li><b>WiFi trilateration</b>, which is how a browser is accurate. It
 *       needs a database mapping access points to positions. The free one
 *       (BeaconDB, successor to Mozilla Location Service) was queried with
 *       this machine's 11 visible access points and has no coverage here, so
 *       it cannot answer. Google's equivalent can, but requires a billable
 *       API key -- see {@link #hasWifiProvider()}.</li>
 * </ul>
 * Searching for a place name is accurate, free, needs no key, and works
 * globally: the search below resolves a Dhaka address to within a few metres.
 * Combined with dragging the pin, it gets a real position without pretending
 * to have hardware the app does not have.
 *
 * <p>Nominatim's usage policy requires an identifying User-Agent and at most
 * one request per second; both are honoured here.
 */
public final class GeocodingService {

    private static final String SEARCH = "https://nominatim.openstreetmap.org/search";
    private static final String REVERSE = "https://nominatim.openstreetmap.org/reverse";
    private static final String USER_AGENT = "BloodLink/1.0 (emergency blood donation app)";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Nominatim asks for no more than one request a second; this keeps us inside that. */
    private static final Object RATE_LOCK = new Object();
    private static long lastRequestAt = 0;

    /** One candidate place. */
    public record Place(String displayName, double latitude, double longitude) {
        /** The leading part of the full comma-separated name, for compact display. */
        public String shortName() {
            int comma = displayName.indexOf(',');
            return comma > 0 ? displayName.substring(0, comma) : displayName;
        }
    }

    /**
     * Whether a real "where am I right now" lookup is possible. False unless
     * a Google Geolocation API key has been configured, because no free
     * provider covers this app's region -- callers must not offer a GPS
     * button that cannot deliver.
     */
    public static boolean hasWifiProvider() {
        try {
            String key = com.bloodlink.util.AppConfig.get("google.geolocation.api.key");
            return key != null && !key.isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Free-text place search. Empty when nothing matched or the service is unreachable. */
    public List<Place> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        // accept-language=en matters for more than taste: the bundled Poppins
        // has no Bengali glyphs, so a locally-scripted address would render as
        // a row of empty boxes in the app.
        String url = SEARCH + "?format=json&addressdetails=0&accept-language=en&limit=6&q="
                + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String body = get(url);
        if (body == null) return List.of();
        List<Place> places = new ArrayList<>();
        try {
            JSONArray results = new JSONArray(body);
            for (int i = 0; i < results.length(); i++) {
                JSONObject entry = results.getJSONObject(i);
                places.add(new Place(entry.getString("display_name"),
                        Double.parseDouble(entry.getString("lat")),
                        Double.parseDouble(entry.getString("lon"))));
            }
        } catch (RuntimeException e) {
            return List.of();
        }
        return places;
    }

    /** The readable address at a point, so a dropped pin can show where it landed. */
    public Optional<String> describe(double latitude, double longitude) {
        String url = REVERSE + "?format=json&accept-language=en&zoom=18&lat=" + latitude + "&lon=" + longitude;
        String body = get(url);
        if (body == null) return Optional.empty();
        try {
            JSONObject result = new JSONObject(body);
            if (!result.has("display_name")) return Optional.empty();
            return Optional.of(result.getString("display_name"));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String get(String url) {
        throttle();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void throttle() {
        synchronized (RATE_LOCK) {
            long since = System.currentTimeMillis() - lastRequestAt;
            if (since < 1100) {
                try {
                    Thread.sleep(1100 - since);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }
}
