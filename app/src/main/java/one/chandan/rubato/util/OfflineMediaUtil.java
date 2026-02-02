package one.chandan.rubato.util;

import android.content.Context;

import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.Child;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OfflineMediaUtil {
    private OfflineMediaUtil() {
    }

    public static boolean isPlayable(Context context, Child song) {
        if (song == null) return false;
        if (!NetworkUtil.isOffline()) return true;
        return isDownloadedOrLocal(context, song);
    }

    public static boolean hasPlayable(Context context, List<Child> songs) {
        if (songs == null || songs.isEmpty()) return false;
        if (!NetworkUtil.isOffline()) return true;
        for (Child song : songs) {
            if (isDownloadedOrLocal(context, song)) {
                return true;
            }
        }
        return false;
    }

    public static List<Child> filterPlayable(Context context, List<Child> songs) {
        if (songs == null) return Collections.emptyList();
        if (!NetworkUtil.isOffline()) return songs;
        List<Child> filtered = new ArrayList<>();
        for (Child song : songs) {
            if (isDownloadedOrLocal(context, song)) {
                filtered.add(song);
            }
        }
        return filtered;
    }

    private static boolean isDownloadedOrLocal(Context context, Child song) {
        if (song == null) return false;
        if (LocalMusicRepository.isLocalSong(song)) return true;
        if (song.getId() == null) return false;
        return DownloadUtil.getDownloadTracker(context).isDownloaded(song.getId());
    }
}
