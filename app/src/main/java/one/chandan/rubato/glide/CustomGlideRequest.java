package one.chandan.rubato.glide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import one.chandan.rubato.App;
import one.chandan.rubato.R;
import one.chandan.rubato.jellyfin.JellyfinMediaUtil;
import one.chandan.rubato.util.NetworkUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.util.SearchIndexUtil;
import one.chandan.rubato.util.Util;
import com.google.android.material.elevation.SurfaceColors;

import java.util.Map;

public class CustomGlideRequest {
    private static final String TAG = "CustomGlideRequest";

    public static final DiskCacheStrategy DEFAULT_DISK_CACHE_STRATEGY = DiskCacheStrategy.ALL;

    public enum ResourceType {
        Unknown,
        Album,
        Artist,
        Folder,
        Directory,
        Playlist,
        Podcast,
        Radio,
        Song,
        NowPlaying,
    }

    public static RequestOptions createRequestOptions(Context context, String item, ResourceType type) {
        int cornerRadius = getCornerRadius(type);
        Drawable placeholder = wrapRounded(new ColorDrawable(SurfaceColors.SURFACE_5.getColor(context)), cornerRadius);
        Drawable fallback = wrapRounded(getPlaceholder(context, type), cornerRadius);
        RequestOptions options = new RequestOptions()
                .placeholder(placeholder)
                .fallback(fallback)
                .error(fallback)
                .diskCacheStrategy(DEFAULT_DISK_CACHE_STRATEGY)
                .signature(new ObjectKey(item != null ? item : 0));

        if (cornerRadius > 0) {
            options = options.transform(new CenterCrop(), new RoundedCorners(cornerRadius));
        } else {
            options = options.transform(new CenterCrop());
        }

        if (NetworkUtil.isOffline()) {
            options = options.onlyRetrieveFromCache(true);
        }

        return options;
    }

    @Nullable
    private static Drawable wrapRounded(@Nullable Drawable drawable, int radius) {
        if (drawable == null || radius <= 0) {
            return drawable;
        }
        return new RoundedDrawable(drawable, radius);
    }

    @Nullable
    private static Drawable getPlaceholder(Context context, ResourceType type) {
        switch (type) {
            case Album:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_album);
            case Artist:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_artist);
            case Folder:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_folder);
            case Directory:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_directory);
            case Playlist:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_playlist);
            case Podcast:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_podcast);
            case Radio:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_radio);
            case Song:
            case NowPlaying:
                return AppCompatResources.getDrawable(context, R.drawable.ic_placeholder_song);
            default:
            case Unknown:
                return new ColorDrawable(SurfaceColors.SURFACE_5.getColor(context));
        }
    }

    public static String createUrl(String item, int size) {
        if (item == null) return null;
        if (isLocalUri(item)) {
            return item;
        }
        if (SearchIndexUtil.isJellyfinTagged(item)) {
            return JellyfinMediaUtil.buildImageUrl(item, size);
        }
        Map<String, String> params = App.getSubsonicClientInstance(false).getParams();

        StringBuilder uri = new StringBuilder();

        uri.append(App.getSubsonicClientInstance(false).getUrl());
        uri.append("getCoverArt");

        if (params.containsKey("u") && params.get("u") != null)
            uri.append("?u=").append(Util.encode(params.get("u")));
        if (params.containsKey("p") && params.get("p") != null)
            uri.append("&p=").append(params.get("p"));
        if (params.containsKey("s") && params.get("s") != null)
            uri.append("&s=").append(params.get("s"));
        if (params.containsKey("t") && params.get("t") != null)
            uri.append("&t=").append(params.get("t"));
        if (params.containsKey("v") && params.get("v") != null)
            uri.append("&v=").append(params.get("v"));
        if (params.containsKey("c") && params.get("c") != null)
            uri.append("&c=").append(params.get("c"));
        if (size != -1)
            uri.append("&size=").append(size);

        uri.append("&id=").append(item);

        Log.d(TAG, "createUrl() " + uri);

        return uri.toString();
    }

    public static class Builder {
        private final RequestManager requestManager;
        private Object item;

        private Builder(Context context, String item, ResourceType type) {
            this.requestManager = Glide.with(context);

            if (item != null) {
                if (isLocalUri(item)) {
                    this.item = item;
                } else if (!Preferences.isDataSavingMode()) {
                    this.item = createUrl(item, Preferences.getImageSize());
                }
            }

            requestManager.applyDefaultRequestOptions(createRequestOptions(context, item, type));
        }

        public static Builder from(Context context, String item, ResourceType type) {
            return new Builder(context, item, type);
        }

        public RequestBuilder<Drawable> build() {
            return requestManager
                    .load(item)
                    .transition(DrawableTransitionOptions.withCrossFade());
        }
    }

    private static final class RoundedDrawable extends Drawable {
        private final Drawable inner;
        private final float radius;
        private final RectF rect = new RectF();
        private final Path path = new Path();

        private RoundedDrawable(Drawable inner, float radius) {
            this.inner = inner;
            this.radius = radius;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            inner.setBounds(bounds);
            rect.set(bounds);
            path.reset();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        }

        @Override
        public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(path);
            inner.draw(canvas);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            inner.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            inner.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return inner.getOpacity();
        }

        @Override
        public int getIntrinsicWidth() {
            return inner.getIntrinsicWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return inner.getIntrinsicHeight();
        }
    }

    private static boolean isLocalUri(String item) {
        return item.startsWith("content://")
                || item.startsWith("file://")
                || item.startsWith("android.resource://");
    }

    public static int getCornerRadius(ResourceType type) {
        int radius = Preferences.getRoundedCornerSize();
        if (radius <= 0) {
            radius = 1;
        }

        int pxRadius = Math.round(radius * App.getInstance().getResources().getDisplayMetrics().density);
        if (pxRadius <= 0) {
            pxRadius = 1;
        }

        if (type == ResourceType.NowPlaying) {
            return 0;
        }

        if (type == ResourceType.Folder || type == ResourceType.Directory) {
            return Preferences.isCornerRoundingEnabled() ? pxRadius : 1;
        }

        if (!Preferences.isCornerRoundingEnabled()) {
            return 1;
        }

        if (type == ResourceType.Album) {
            return Math.max(1, pxRadius * 2);
        }

        return pxRadius;
    }
}
