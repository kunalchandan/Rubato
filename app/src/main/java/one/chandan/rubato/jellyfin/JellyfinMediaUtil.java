package one.chandan.rubato.jellyfin;

import androidx.annotation.Nullable;

import one.chandan.rubato.model.JellyfinServer;
import one.chandan.rubato.repository.JellyfinServerRepository;
import one.chandan.rubato.util.SearchIndexUtil;

public final class JellyfinMediaUtil {
    private JellyfinMediaUtil() {
    }

    @Nullable
    public static JellyfinTaggedId parseTaggedId(String taggedId) {
        if (taggedId == null || !SearchIndexUtil.isJellyfinTagged(taggedId)) {
            return null;
        }
        String raw = taggedId.substring((SearchIndexUtil.SOURCE_JELLYFIN + ":").length());
        int split = raw.indexOf(':');
        if (split <= 0 || split >= raw.length() - 1) {
            return null;
        }
        String serverId = raw.substring(0, split);
        String itemId = raw.substring(split + 1);
        return new JellyfinTaggedId(serverId, itemId);
    }

    @Nullable
    public static String buildStreamUrl(String taggedId) {
        JellyfinTaggedId parsed = parseTaggedId(taggedId);
        if (parsed == null) return null;
        JellyfinServer server = new JellyfinServerRepository().findById(parsed.serverId);
        if (server == null) return null;
        String base = normalizeBaseUrl(server.getAddress());
        return base + "Audio/" + parsed.itemId + "/stream?api_key=" + server.getAccessToken();
    }

    @Nullable
    public static String buildImageUrl(String taggedId, int size) {
        JellyfinTaggedId parsed = parseTaggedId(taggedId);
        if (parsed == null) return null;
        JellyfinServer server = new JellyfinServerRepository().findById(parsed.serverId);
        if (server == null) return null;
        String base = normalizeBaseUrl(server.getAddress());
        StringBuilder url = new StringBuilder();
        url.append(base).append("Items/").append(parsed.itemId).append("/Images/Primary");
        url.append("?api_key=").append(server.getAccessToken());
        if (size > 0) {
            url.append("&maxWidth=").append(size);
            url.append("&maxHeight=").append(size);
        }
        return url.toString();
    }

    private static String normalizeBaseUrl(String base) {
        if (base == null) return "";
        if (!base.endsWith("/")) {
            return base + "/";
        }
        return base;
    }

    public static final class JellyfinTaggedId {
        public final String serverId;
        public final String itemId;

        JellyfinTaggedId(String serverId, String itemId) {
            this.serverId = serverId;
            this.itemId = itemId;
        }
    }
}
