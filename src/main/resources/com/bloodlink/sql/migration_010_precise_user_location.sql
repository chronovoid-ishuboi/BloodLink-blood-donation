-- Migration 010: a real, per-user position instead of a district centroid.
--
-- Until now "where is this donor" resolved to one of two approximations: the
-- coordinates of the first hospital seeded in their district, or the
-- coordinates of a reference hospital they picked. Neither is where the
-- person actually is, and both are shared by every user in that district, so
-- two donors in the same district rendered as the same pin.
--
-- location_source records HOW the position was obtained, and is not
-- decoration: the UI labels a pin with it, because a city-level IP estimate
-- and a pin the user placed on their own street are both "coordinates" but
-- mean very different things to a requester deciding who can reach a
-- hospital in time. Values: MAP_PIN (user placed it), IP (coarse
-- auto-detect), DISTRICT (fallback centroid, never stored -- computed).
--
-- Browser geolocation is deliberately not a source. JavaFX's WebView exposes
-- navigator.geolocation but never resolves the request (no permission
-- provider), verified by probing it from both about:blank and file: origins,
-- so a GPS source would be a promise this app cannot keep.
--
-- Like every migration_0XX file this is the historical record; DatabaseSetup
-- reads only schema.sql, so this DDL is folded in there too.

ALTER TABLE users ADD COLUMN latitude DECIMAL(10,7) NULL AFTER address;
ALTER TABLE users ADD COLUMN longitude DECIMAL(10,7) NULL AFTER latitude;
ALTER TABLE users ADD COLUMN location_source VARCHAR(20) NULL AFTER longitude;
ALTER TABLE users ADD COLUMN location_updated_at TIMESTAMP NULL AFTER location_source;
