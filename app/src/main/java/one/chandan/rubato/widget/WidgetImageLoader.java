package one.chandan.rubato.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.AppWidgetTarget;

import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.util.Preferences;

public final class WidgetImageLoader {
    private WidgetImageLoader() {
    }

    public static void loadCover(Context context,
                                 int appWidgetId,
                                 RemoteViews views,
                                 int viewId,
                                 String coverArtId,
                                 CustomGlideRequest.ResourceType type,
                                 boolean circle) {
        loadCover(context, appWidgetId, views, viewId, coverArtId, type, circle, 0);
    }

    public static void loadCover(Context context,
                                 int appWidgetId,
                                 RemoteViews views,
                                 int viewId,
                                 String coverArtId,
                                 CustomGlideRequest.ResourceType type,
                                 boolean circle,
                                 int fallbackResId) {
        Context appContext = context.getApplicationContext();
        String url = null;
        if (coverArtId != null) {
            if (!Preferences.isDataSavingMode() || isLocalUri(coverArtId)) {
                url = CustomGlideRequest.createUrl(coverArtId, Preferences.getImageSize());
            }
        }
        RequestOptions options = CustomGlideRequest.createRequestOptions(appContext, coverArtId, type);
        if (fallbackResId != 0) {
            options = options.fallback(fallbackResId).error(fallbackResId).placeholder(fallbackResId);
        }
        if (circle) {
            options = options.transform(new CircleCrop());
        }

        AppWidgetTarget target = new AppWidgetTarget(appContext, viewId, views, appWidgetId);
        Glide.with(appContext)
                .asBitmap()
                .load(url)
                .apply(options)
                .into(target);
    }

    private static boolean isLocalUri(String value) {
        return value.startsWith("content://")
                || value.startsWith("file://")
                || value.startsWith("android.resource://");
    }
}
