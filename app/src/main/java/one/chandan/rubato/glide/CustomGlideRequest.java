package one.chandan.rubato.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.Key;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
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
        float cornerRatio = getCornerRatio(type);
        Drawable placeholder = wrapRounded(new ColorDrawable(SurfaceColors.SURFACE_5.getColor(context)), cornerRatio);
        Drawable fallback = wrapRounded(getPlaceholder(context, type), cornerRatio);
        String normalizedItem = normalizeLocalTag(item);
        RequestOptions options = new RequestOptions()
                .placeholder(placeholder)
                .fallback(fallback)
                .error(fallback)
                .diskCacheStrategy(DEFAULT_DISK_CACHE_STRATEGY)
                .signature(new ObjectKey(normalizedItem != null ? normalizedItem : 0));

        if (cornerRatio > 0f) {
            options = options.transform(new CenterCrop(), new RelativeRoundedCorners(cornerRatio));
        } else {
            options = options.transform(new CenterCrop());
        }

        if (NetworkUtil.isOffline() && (normalizedItem == null || !isLocalUri(normalizedItem))) {
            options = options.onlyRetrieveFromCache(true);
        }

        return options;
    }

    @Nullable
    private static Drawable wrapRounded(@Nullable Drawable drawable, float ratio) {
        if (drawable == null || ratio <= 0f) {
            return drawable;
        }
        return new RoundedDrawable(drawable, ratio);
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

    @Nullable
    public static Drawable getPlaceholderDrawable(Context context, ResourceType type) {
        if (context == null) return null;
        return wrapRounded(getPlaceholder(context, type), getCornerRatio(type));
    }

    public static String createUrl(String item, int size) {
        if (item == null) return null;
        String normalizedItem = normalizeLocalTag(item);
        if (isLocalUri(normalizedItem)) {
            return normalizedItem;
        }
        if (SearchIndexUtil.isJellyfinTagged(normalizedItem)) {
            return JellyfinMediaUtil.buildImageUrl(normalizedItem, size);
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

        uri.append("&id=").append(normalizedItem);

        Log.d(TAG, "createUrl() " + uri);

        return uri.toString();
    }

    public static class Builder {
        private final RequestManager requestManager;
        private Object item;

        private Builder(Context context, String item, ResourceType type) {
            this.requestManager = Glide.with(context);
            String normalizedItem = normalizeLocalTag(item);

            if (normalizedItem != null) {
                if (isLocalUri(normalizedItem)) {
                    this.item = normalizedItem;
                } else if (!Preferences.isDataSavingMode()) {
                    this.item = createUrl(normalizedItem, Preferences.getImageSize());
                }
            }

            requestManager.applyDefaultRequestOptions(createRequestOptions(context, normalizedItem, type));
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
        private final float ratio;
        private float radius;
        private final RectF rect = new RectF();
        private final Path path = new Path();

        private RoundedDrawable(Drawable inner, float ratio) {
            this.inner = inner;
            this.ratio = ratio;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            inner.setBounds(bounds);
            rect.set(bounds);
            path.reset();
            float minSize = Math.min(rect.width(), rect.height());
            radius = Math.max(1f, minSize * ratio);
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

    private static String normalizeLocalTag(String item) {
        if (item == null) return null;
        if (SearchIndexUtil.isSourceTagged(item, SearchIndexUtil.SOURCE_LOCAL)) {
            String prefix = SearchIndexUtil.SOURCE_LOCAL + ":";
            return item.startsWith(prefix) ? item.substring(prefix.length()) : item;
        }
        return item;
    }

    public static float getCornerRatio(ResourceType type) {
        if (type == ResourceType.NowPlaying) {
            return 0f;
        }
        if (!Preferences.isCornerRoundingEnabled()) {
            return 0f;
        }
        int dp = Preferences.getRoundedCornerSize();
        float ratio = dp / 100f;
        ratio = Math.max(0.08f, Math.min(0.45f, ratio));
        if (type == ResourceType.Folder || type == ResourceType.Directory) {
            ratio = Math.max(0.04f, ratio * 0.8f);
        }
        return ratio;
    }

    public static int getCornerRadiusPx(int widthPx, int heightPx, ResourceType type) {
        float ratio = getCornerRatio(type);
        if (ratio <= 0f || widthPx <= 0 || heightPx <= 0) {
            return 0;
        }
        int radius = Math.round(Math.min(widthPx, heightPx) * ratio);
        return Math.max(1, radius);
    }

    private static final class RelativeRoundedCorners extends BitmapTransformation {
        private static final String ID = "one.chandan.rubato.glide.RelativeRoundedCorners";
        private final float ratio;

        RelativeRoundedCorners(float ratio) {
            this.ratio = ratio;
        }

        @Override
        protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
            if (ratio <= 0f) {
                return toTransform;
            }
            int radius = Math.max(1, Math.round(Math.min(toTransform.getWidth(), toTransform.getHeight()) * ratio));
            return TransformationUtils.roundedCorners(pool, toTransform, radius);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof RelativeRoundedCorners) {
                RelativeRoundedCorners other = (RelativeRoundedCorners) o;
                return Float.compare(other.ratio, ratio) == 0;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return ID.hashCode() * 31 + Float.floatToIntBits(ratio);
        }

        @Override
        public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
            messageDigest.update(ID.getBytes(Key.CHARSET));
            messageDigest.update(ByteBuffer.allocate(4).putFloat(ratio).array());
        }
    }
}
