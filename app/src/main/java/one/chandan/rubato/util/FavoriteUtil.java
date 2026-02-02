package one.chandan.rubato.util;

import android.content.Context;

import one.chandan.rubato.interfaces.StarCallback;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.repository.FavoriteRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.DownloadUtil;
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;

import java.util.Date;

public final class FavoriteUtil {
    private FavoriteUtil() {
    }

    public static boolean toggleFavorite(Context context, Child song) {
        if (song == null) return false;
        FavoriteRepository favoriteRepository = new FavoriteRepository();

        if (song.getStarred() != null) {
            if (NetworkUtil.isOffline()) {
                favoriteRepository.starLater(song.getId(), null, null, false);
            } else {
                favoriteRepository.unstar(song.getId(), null, null, new StarCallback() {
                    @Override
                    public void onError() {
                        favoriteRepository.starLater(song.getId(), null, null, false);
                    }
                });
            }
            song.setStarred(null);
            return false;
        }

        if (NetworkUtil.isOffline()) {
            favoriteRepository.starLater(song.getId(), null, null, true);
        } else {
            favoriteRepository.star(song.getId(), null, null, new StarCallback() {
                @Override
                public void onError() {
                    favoriteRepository.starLater(song.getId(), null, null, true);
                }
            });
        }

        song.setStarred(new Date());
        if (context != null && Preferences.isStarredSyncEnabled()) {
            DownloadUtil.getDownloadTracker(context).download(
                MappingUtil.mapDownload(song),
                new Download(song)
            );
        }
        return true;
    }
}
