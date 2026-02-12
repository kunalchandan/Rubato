package one.chandan.rubato.util;

import one.chandan.rubato.repository.CacheRepository;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class MetadataStorageReporter {
    private MetadataStorageReporter() {
    }

    public static void refresh() {
        CacheRepository cacheRepository = new CacheRepository();
        List<String> keys = Arrays.asList("playlists", "artists_all", "genres_all", "albums_all", "songs_all");
        List<String> likeKeys = Arrays.asList(
                "lyrics_song_%",
                "album_tracks_%",
                "playlist_songs_%",
                "artist_info_%",
                "album_info_%",
                "jf_%"
        );
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicInteger pending = new AtomicInteger(1 + likeKeys.size());

        CacheRepository.CacheResult<Long> accumulator = size -> {
            if (size != null && size > 0) {
                totalBytes.addAndGet(size);
            }
            if (pending.decrementAndGet() == 0) {
                Preferences.setMetadataSyncStorageBytes(totalBytes.get());
                Preferences.setMetadataSyncStorageUpdated(System.currentTimeMillis());
            }
        };

        cacheRepository.loadPayloadSize(keys, accumulator);
        for (String likeKey : likeKeys) {
            cacheRepository.loadPayloadSizeLike(likeKey, accumulator);
        }
    }
}
