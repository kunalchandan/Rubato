package one.chandan.rubato.util;

import android.content.Context;

import one.chandan.rubato.glide.CustomGlideRequest;
import com.bumptech.glide.Glide;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CoverArtPrefetcher {
    private static final int MAX_PREFETCH = 3000;
    private static final Set<String> PREFETCHED = Collections.synchronizedSet(new HashSet<>());

    private CoverArtPrefetcher() {
    }

    public static void prefetch(Context context, String coverArtId, CustomGlideRequest.ResourceType type) {
        if (context == null || coverArtId == null || coverArtId.isEmpty()) return;
        if (Preferences.isDataSavingMode()) return;
        if (PREFETCHED.size() >= MAX_PREFETCH) return;

        String key = type.name() + ":" + coverArtId;
        if (!PREFETCHED.add(key)) return;

        CustomGlideRequest.Builder
                .from(context.getApplicationContext(), coverArtId, type)
                .build()
                .preload();
    }

    public static void prefetchAll(Context context, List<String> coverArtIds, CustomGlideRequest.ResourceType type) {
        if (coverArtIds == null || coverArtIds.isEmpty()) return;
        for (String coverArtId : coverArtIds) {
            prefetch(context, coverArtId, type);
        }
    }

    public static void prefetchUrl(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return;
        if (Preferences.isDataSavingMode()) return;
        if (PREFETCHED.size() >= MAX_PREFETCH) return;

        String key = "URL:" + url;
        if (!PREFETCHED.add(key)) return;

        Glide.with(context.getApplicationContext())
                .load(url)
                .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
                .preload();
    }

    public static void prefetchUrls(Context context, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        for (String url : urls) {
            prefetchUrl(context, url);
        }
    }
}
