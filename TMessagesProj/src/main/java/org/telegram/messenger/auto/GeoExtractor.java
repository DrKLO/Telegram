package org.telegram.messenger.auto;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeoExtractor {

    private static final Pattern GOOGLE_MAPS_PATTERN = Pattern.compile(
            "(?:google\\.com/maps|maps\\.google\\.com|goo\\.gl/maps|maps\\.app\\.goo\\.gl)" +
            ".*?[/@](-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");
    private static final Pattern GEO_URI_PATTERN = Pattern.compile(
            "geo:(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");
    private static final Pattern COORDS_IN_URL_PATTERN = Pattern.compile(
            "(?:q=|@|ll=)(-?\\d+\\.\\d+)[,+](-?\\d+\\.\\d+)");

    public static class GeoResult {
        public final double lat;
        public final double lng;
        public final String label;

        public GeoResult(double lat, double lng, String label) {
            this.lat = lat;
            this.lng = lng;
            this.label = label;
        }
    }

    public static GeoResult extractFromMessages(ArrayList<MessageObject> messages, int maxLookback) {
        if (messages == null) return null;
        int limit = Math.min(messages.size(), maxLookback);
        for (int i = messages.size() - 1; i >= messages.size() - limit && i >= 0; i--) {
            MessageObject msg = messages.get(i);
            if (msg == null || msg.messageOwner == null) continue;
            GeoResult result = extractFromMessage(msg);
            if (result != null) return result;
        }
        return null;
    }

    private static GeoResult extractFromMessage(MessageObject msg) {
        TLRPC.Message message = msg.messageOwner;

        // Priority 1: Geo media (location, venue, live location)
        if (message.media != null) {
            TLRPC.GeoPoint geo = null;
            String label = null;
            if (message.media instanceof TLRPC.TL_messageMediaGeo && message.media.geo != null) {
                geo = message.media.geo;
            } else if (message.media instanceof TLRPC.TL_messageMediaVenue && message.media.geo != null) {
                geo = message.media.geo;
                label = message.media.title;
            } else if (message.media instanceof TLRPC.TL_messageMediaGeoLive && message.media.geo != null) {
                geo = message.media.geo;
            }
            if (geo != null && (geo.lat != 0 || geo._long != 0)) {
                return new GeoResult(geo.lat, geo._long,
                        label != null ? label : String.format("%.4f, %.4f", geo.lat, geo._long));
            }
        }

        // Priority 2: URL entities
        if (message.entities != null) {
            for (TLRPC.MessageEntity entity : message.entities) {
                String url = null;
                if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                    url = entity.url;
                } else if (entity instanceof TLRPC.TL_messageEntityUrl && message.message != null) {
                    int end = Math.min(entity.offset + entity.length, message.message.length());
                    if (entity.offset >= 0 && end <= message.message.length()) {
                        url = message.message.substring(entity.offset, end);
                    }
                }
                if (url != null) {
                    GeoResult result = extractFromUrl(url);
                    if (result != null) return result;
                }
            }
        }

        // Priority 3: Text content
        if (!TextUtils.isEmpty(message.message)) {
            return extractFromText(message.message);
        }

        return null;
    }

    private static GeoResult extractFromUrl(String url) {
        Matcher m = GOOGLE_MAPS_PATTERN.matcher(url);
        if (m.find()) {
            return parseCoords(m.group(1), m.group(2), null);
        }
        m = COORDS_IN_URL_PATTERN.matcher(url);
        if (m.find()) {
            return parseCoords(m.group(1), m.group(2), null);
        }
        m = GEO_URI_PATTERN.matcher(url);
        if (m.find()) {
            return parseCoords(m.group(1), m.group(2), null);
        }
        return null;
    }

    private static GeoResult extractFromText(String text) {
        Matcher m = GEO_URI_PATTERN.matcher(text);
        if (m.find()) {
            return parseCoords(m.group(1), m.group(2), null);
        }
        m = GOOGLE_MAPS_PATTERN.matcher(text);
        if (m.find()) {
            return parseCoords(m.group(1), m.group(2), null);
        }
        m = COORDS_IN_URL_PATTERN.matcher(text);
        if (m.find()) {
            return parseCoords(m.group(1), m.group(2), null);
        }
        return null;
    }

    private static GeoResult parseCoords(String latStr, String lngStr, String label) {
        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            if (lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                return new GeoResult(lat, lng,
                        label != null ? label : String.format("%.4f, %.4f", lat, lng));
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }
}
