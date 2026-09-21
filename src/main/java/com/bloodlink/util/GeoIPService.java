package com.bloodlink.util;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Rough location from the user's IP address, via ip-api.com.
 *
 * <h2>What this is and is not</h2>
 * This is a starting point for the map, not a location. IP geolocation in
 * Bangladesh typically resolves to the ISP's city -- often just "Dhaka" -- so
 * it can be tens of kilometres out and will happily be confidently wrong. It
 * exists so the draggable pin opens somewhere plausible rather than in the
 * middle of the ocean; the coordinate that gets saved is the one the person
 * drags the pin to. Never present what this returns as "your location".
 *
 * <h2>Free tier</h2>
 * No API key, 45 requests per minute, and the free endpoint is HTTP only --
 * ip-api.com charges for HTTPS. That is acceptable for a coarse city-level
 * hint that is about to be overridden by hand, and it is the reason nothing
 * sensitive is sent: the request has no body and no identifier beyond the IP
 * the connection already reveals.
 */
public class GeoIPService {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public record GeoLocation(double lat, double lon, String city, String region) { }

    /**
     * Approximate position for this machine's public IP, or empty if the
     * lookup failed, timed out, or the service reported no result.
     * <p>
     * Blocking. Callers are on a background thread already; if that ever
     * changes, this must not be moved onto the FX thread.
     */
    public static Optional<GeoLocation> detectLocation() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/?fields=status,message,lat,lon,city,regionName"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();

            // Parsed properly rather than scanned for substrings. The previous
            // version looked for "lat": and read to the next comma, which
            // gives a wrong answer the moment a field is null, the key order
            // changes, or a city name contains a comma -- and gives it
            // silently, as a coordinate, with no way to tell it apart from a
            // real one.
            JSONObject json = new JSONObject(response.body());
            if (!"success".equals(json.optString("status"))) return Optional.empty();
            if (!json.has("lat") || !json.has("lon")) return Optional.empty();

            double latitude = json.optDouble("lat", Double.NaN);
            double longitude = json.optDouble("lon", Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) return Optional.empty();
            // 0,0 is in the Gulf of Guinea and is what a failed lookup looks
            // like when it is reported as a success.
            if (latitude == 0 && longitude == 0) return Optional.empty();

            return Optional.of(new GeoLocation(
                    latitude,
                    longitude,
                    json.optString("city", ""),
                    json.optString("regionName", "")));
        } catch (Exception e) {
            // A location hint failing is not worth interrupting anyone over;
            // the caller falls back to the district centre.
            System.err.println("GeoIP detection failed: " + e.getMessage());
            return Optional.empty();
        }
    }
}
