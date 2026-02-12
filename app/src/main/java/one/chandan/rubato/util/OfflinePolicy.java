package one.chandan.rubato.util;

import android.content.Context;

import java.util.List;

import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.Child;

/**
 * Offline-first contract:
 * - Lists/metadata/art should render from cache when offline.
 * - Streaming/queue actions are allowed offline only for local or downloaded media.
 * - Server-dependent actions (radio, rating, share, add-to-playlist, download) require online.
 */
public final class OfflinePolicy {
    private OfflinePolicy() {
    }

    public static boolean isOffline() {
        return NetworkUtil.isOffline();
    }

    public static boolean canPlayRadio() {
        return !isOffline();
    }

    public static boolean canPlayRandom() {
        return !isOffline();
    }

    public static boolean canRate() {
        return !isOffline();
    }

    public static boolean canShare() {
        return !isOffline();
    }

    public static boolean canAddToPlaylist() {
        return !isOffline();
    }

    public static boolean canDownload(Child song) {
        if (song == null) return false;
        if (LocalMusicRepository.isLocalSong(song)) return false;
        return !isOffline();
    }

    public static boolean canDownloadAll() {
        return !isOffline();
    }

    public static boolean canPlay(Context context, Child song) {
        return OfflineMediaUtil.isPlayable(context, song);
    }

    public static boolean canQueue(Context context, Child song) {
        return OfflineMediaUtil.isPlayable(context, song);
    }

    public static boolean hasPlayable(Context context, List<Child> songs) {
        return OfflineMediaUtil.hasPlayable(context, songs);
    }

    public static List<Child> filterPlayable(Context context, List<Child> songs) {
        return OfflineMediaUtil.filterPlayable(context, songs);
    }
}
