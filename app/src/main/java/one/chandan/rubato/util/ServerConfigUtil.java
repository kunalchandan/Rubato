package one.chandan.rubato.util;

import one.chandan.rubato.repository.JellyfinServerRepository;

public final class ServerConfigUtil {
    private ServerConfigUtil() {
    }

    public static boolean hasAnyRemoteServer() {
        if (Preferences.hasRemoteServer()) {
            return true;
        }
        String serverId = Preferences.getServerId();
        if (serverId != null && !serverId.trim().isEmpty()) {
            return true;
        }
        return !new JellyfinServerRepository().getServersSnapshot().isEmpty();
    }
}
