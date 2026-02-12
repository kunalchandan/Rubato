package one.chandan.rubato.sync;

import one.chandan.rubato.model.JellyfinServer;
import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.repository.CacheRepository;
import one.chandan.rubato.repository.JellyfinLibraryRepository;
import one.chandan.rubato.repository.JellyfinServerRepository;
import one.chandan.rubato.repository.LibrarySearchIndexRepository;
import one.chandan.rubato.util.MetadataSyncManager;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.SearchIndexUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JellyfinSyncProvider {
    private static final int LOG_INTERVAL_SMALL = 10;

    private JellyfinSyncProvider() {
    }

    public static boolean sync(CacheRepository cacheRepository,
                               LibrarySearchIndexRepository searchIndexRepository,
                               Set<String> coverArtIds,
                               SyncMode mode) {
        if (cacheRepository == null || searchIndexRepository == null) return false;
        try {
            List<JellyfinServer> servers = new JellyfinServerRepository().getServersSnapshot();
            if (servers == null || servers.isEmpty()) return false;

            Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_JELLYFIN, 0, servers.size());
            logSync(MetadataSyncManager.STAGE_JELLYFIN, "Fetching Jellyfin libraries", false);

            JellyfinLibraryRepository repository = new JellyfinLibraryRepository();
            String signature = buildSignature(repository, servers);
            long now = System.currentTimeMillis();
            if (mode == SyncMode.DELTA
                    && signature != null
                    && signature.equals(Preferences.getMetadataSyncJellyfinSignature())
                    && !SyncDeltaPolicy.shouldForceFull(Preferences.getMetadataSyncJellyfinFull())) {
                logSync(MetadataSyncManager.STAGE_JELLYFIN, "Jellyfin delta: no changes", true);
                Preferences.setMetadataSyncJellyfinLast(now);
                return false;
            }

            boolean didWork = false;
            List<LibrarySearchEntry> entries = new ArrayList<>();
            int index = 0;
            for (JellyfinServer server : servers) {
                if (server == null) continue;
                index++;
                List<LibrarySearchEntry> serverEntries = repository.syncServer(server, cacheRepository);
                if (serverEntries != null && !serverEntries.isEmpty()) {
                    entries.addAll(serverEntries);
                }
                String name = server.getName() != null ? server.getName() : server.getId();
                logProgress(MetadataSyncManager.STAGE_JELLYFIN, index, servers.size(), "Jellyfin: " + name, LOG_INTERVAL_SMALL);
                Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_JELLYFIN, index, servers.size());
            }
            if (!entries.isEmpty()) {
                if (coverArtIds != null) {
                    for (LibrarySearchEntry entry : entries) {
                        if (entry == null) continue;
                        String coverArt = SearchIndexUtil.tagSourceId(SearchIndexUtil.SOURCE_JELLYFIN, entry.getCoverArt());
                        if (coverArt != null && !coverArt.isEmpty()) {
                            coverArtIds.add(coverArt);
                        }
                    }
                }
                searchIndexRepository.replaceSource(SearchIndexUtil.SOURCE_JELLYFIN, entries);
                logSync(MetadataSyncManager.STAGE_JELLYFIN, "Jellyfin cached (" + entries.size() + ")", true);
                didWork = true;
            }
            Preferences.setMetadataSyncJellyfinLast(now);
            Preferences.setMetadataSyncJellyfinFull(now);
            if (signature != null) {
                Preferences.setMetadataSyncJellyfinSignature(signature);
            }
            return didWork;
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void logSync(String stage, String message, boolean completed) {
        Preferences.appendMetadataSyncLog(message, stage, completed);
    }

    private static void logProgress(String stage, int index, int total, String message, int interval) {
        if (index <= 0 || total <= 0) return;
        if (index == 1 || index % interval == 0 || index == total) {
            logSync(stage, message + " (" + index + "/" + total + ")", false);
        }
    }

    private static String buildSignature(JellyfinLibraryRepository repository, List<JellyfinServer> servers) {
        if (repository == null || servers == null || servers.isEmpty()) return null;
        StringBuilder builder = new StringBuilder();
        for (JellyfinServer server : servers) {
            if (server == null) continue;
            JellyfinLibraryRepository.LibrarySignature signature = repository.fetchSignature(server);
            if (signature == null) return null;
            builder.append(server.getId())
                    .append(":")
                    .append(signature.asKey())
                    .append(";");
        }
        return builder.length() > 0 ? builder.toString() : null;
    }
}
