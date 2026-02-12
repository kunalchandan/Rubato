package one.chandan.rubato.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SearchIndexUtil {
    public static final String SOURCE_SUBSONIC = "subsonic";
    public static final String SOURCE_JELLYFIN = "jellyfin";
    public static final String SOURCE_LOCAL = "local";

    public static final String TYPE_ARTIST = "artist";
    public static final String TYPE_ALBUM = "album";
    public static final String TYPE_SONG = "song";
    public static final String TYPE_PLAYLIST = "playlist";

    private SearchIndexUtil() {
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.toLowerCase(Locale.US);
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    public static String buildSearchText(String title, String artist, String album) {
        List<String> parts = new ArrayList<>();
        if (title != null && !title.trim().isEmpty()) parts.add(normalize(title));
        if (artist != null && !artist.trim().isEmpty()) parts.add(normalize(artist));
        if (album != null && !album.trim().isEmpty()) parts.add(normalize(album));
        if (parts.isEmpty()) return "";
        return String.join(" ", parts);
    }

    public static String buildUid(String source, String mediaType, String itemId, String title, String artist, String album) {
        String cleanedSource = source != null ? source.trim() : "";
        String cleanedType = mediaType != null ? mediaType.trim() : "";
        if (itemId != null && !itemId.trim().isEmpty()) {
            return cleanedSource + ":" + cleanedType + ":" + itemId.trim();
        }
        String seed = cleanedSource + ":" + cleanedType + ":" + buildSearchText(title, artist, album);
        return cleanedSource + ":" + cleanedType + ":" + Integer.toHexString(seed.hashCode());
    }

    public static String tagSourceId(String source, String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) return itemId;
        String cleanedSource = source != null ? source.trim() : "";
        if (SOURCE_SUBSONIC.equals(cleanedSource) || cleanedSource.isEmpty()) {
            return itemId;
        }
        if (itemId.startsWith(cleanedSource + ":")) {
            return itemId;
        }
        return cleanedSource + ":" + itemId;
    }

    public static boolean isSourceTagged(String itemId, String source) {
        if (itemId == null || source == null) return false;
        return itemId.startsWith(source + ":");
    }

    public static boolean isJellyfinTagged(String itemId) {
        if (itemId == null) return false;
        return itemId.startsWith(SOURCE_JELLYFIN + ":");
    }

    public static int sourcePriority(String source) {
        List<String> order = Preferences.getSourcePreferenceOrder();
        if (order != null) {
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).equals(source)) {
                    return i;
                }
            }
        }
        return order != null ? order.size() : Integer.MAX_VALUE;
    }
}
