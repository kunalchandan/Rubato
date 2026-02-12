package one.chandan.rubato.sync;

import android.content.Context;

import one.chandan.rubato.model.LibrarySearchEntry;
import one.chandan.rubato.repository.LibrarySearchIndexRepository;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.util.MetadataSyncManager;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.SearchIndexBuilder;
import one.chandan.rubato.util.SearchIndexUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class LocalSyncProvider {
    private static final long LOAD_TIMEOUT_SECONDS = 20;

    private LocalSyncProvider() {
    }

    public static boolean sync(Context context, LibrarySearchIndexRepository searchIndexRepository) {
        return sync(context, searchIndexRepository, SyncMode.DELTA);
    }

    public static boolean sync(Context context, LibrarySearchIndexRepository searchIndexRepository, SyncMode mode) {
        if (context == null || searchIndexRepository == null) return false;
        if (!LocalMusicRepository.isEnabled(context)) {
            searchIndexRepository.replaceSource(SearchIndexUtil.SOURCE_LOCAL, Collections.emptyList());
            logSync("Local library disabled; cleared index", true);
            Preferences.setMetadataSyncLocalLast(System.currentTimeMillis());
            return false;
        }

        Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_LOCAL, 0, -1);
        logSync("Syncing local library", false);

        CountDownLatch latch = new CountDownLatch(1);
        LocalMusicRepository.LocalLibrary[] holder = new LocalMusicRepository.LocalLibrary[1];
        LocalMusicRepository.loadLibrary(context.getApplicationContext(), library -> {
            holder[0] = library;
            latch.countDown();
        });

        try {
            latch.await(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        LocalMusicRepository.LocalLibrary library = holder[0];
        if (library == null) {
            logSync("Local library sync skipped (timeout)", true);
            return false;
        }

        String signature = buildSignature(library);
        long now = System.currentTimeMillis();
        if (mode == SyncMode.DELTA
                && signature != null
                && signature.equals(Preferences.getMetadataSyncLocalSignature())
                && !SyncDeltaPolicy.shouldForceFull(Preferences.getMetadataSyncLocalFull())) {
            logSync("Local delta: no changes", true);
            Preferences.setMetadataSyncLocalLast(now);
            return false;
        }

        List<LibrarySearchEntry> entries = SearchIndexBuilder.buildFromSource(
                SearchIndexUtil.SOURCE_LOCAL,
                library.artists,
                library.albums,
                library.songs,
                null
        );
        searchIndexRepository.replaceSource(SearchIndexUtil.SOURCE_LOCAL, entries);
        Preferences.setMetadataSyncProgress(MetadataSyncManager.STAGE_LOCAL, entries.size(), entries.size());
        logSync("Local library cached (" + entries.size() + ")", true);
        Preferences.setMetadataSyncLocalLast(now);
        Preferences.setMetadataSyncLocalFull(now);
        if (signature != null) {
            Preferences.setMetadataSyncLocalSignature(signature);
        }
        return !entries.isEmpty();
    }

    private static void logSync(String message, boolean completed) {
        Preferences.appendMetadataSyncLog(message, MetadataSyncManager.STAGE_LOCAL, completed);
    }

    private static String buildSignature(LocalMusicRepository.LocalLibrary library) {
        if (library == null) return null;
        int songs = library.songs != null ? library.songs.size() : 0;
        int albums = library.albums != null ? library.albums.size() : 0;
        int artists = library.artists != null ? library.artists.size() : 0;
        int genres = library.genres != null ? library.genres.size() : 0;
        return songs + ":" + albums + ":" + artists + ":" + genres;
    }
}
