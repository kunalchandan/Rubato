package one.chandan.rubato.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import one.chandan.rubato.glide.CustomGlideRequest;
import one.chandan.rubato.util.NetworkUtil;

import android.os.Handler;
import android.os.Looper;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;

public final class PlaylistCoverCache {
    private static final String DIR_NAME = "playlist_covers";
    private static final ExecutorService IO_EXECUTOR = AppExecutors.coverArt();
    private static final Set<String> IN_FLIGHT = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final int MAX_TILES = 4;
    private static final long LOAD_TIMEOUT_MS = 4000L;

    private PlaylistCoverCache() {
    }

    public static void save(Context context, String playlistId, Drawable drawable) {
        if (context == null || playlistId == null || playlistId.isEmpty() || drawable == null) return;
        if (!(drawable instanceof BitmapDrawable)) return;

        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap == null || bitmap.isRecycled()) return;

        File file = getCoverFile(context, playlistId);
        if (file == null) return;

        saveBitmap(file, bitmap);
    }

    public static void saveComposite(Context context, String playlistId, List<Drawable> drawables) {
        if (context == null || playlistId == null || playlistId.isEmpty() || drawables == null || drawables.isEmpty()) return;
        Bitmap composite = createCompositeBitmap(drawables);
        if (composite == null) return;
        File file = getCoverFile(context, playlistId);
        if (file == null) return;
        saveBitmap(file, composite);
    }

    @Nullable
    public static Drawable load(Context context, String playlistId) {
        if (context == null || playlistId == null || playlistId.isEmpty()) return null;

        File file = getCoverFile(context, playlistId);
        if (file == null || !file.exists()) return null;

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return null;

        RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
        rounded.setCornerRadius(CustomGlideRequest.getCornerRadiusPx(bitmap.getWidth(), bitmap.getHeight(), CustomGlideRequest.ResourceType.Playlist));
        rounded.setAntiAlias(true);
        return rounded;
    }

    public static boolean exists(Context context, String playlistId) {
        if (context == null || playlistId == null || playlistId.isEmpty()) return false;
        File file = getCoverFile(context, playlistId);
        return file != null && file.exists();
    }

    public static void requestComposite(Context context,
                                        String playlistId,
                                        List<String> coverArtIds,
                                        CompositeCallback callback) {
        if (context == null || playlistId == null || playlistId.isEmpty() || coverArtIds == null || coverArtIds.isEmpty()) {
            return;
        }
        if (!IN_FLIGHT.add(playlistId)) {
            return;
        }

        IO_EXECUTOR.execute(() -> {
            try {
                List<Drawable> drawables = new ArrayList<>();
                for (String coverArtId : coverArtIds) {
                    if (coverArtId == null || coverArtId.isEmpty()) continue;
                    RequestBuilder<Drawable> request = CustomGlideRequest.Builder
                            .from(context, coverArtId, CustomGlideRequest.ResourceType.Song)
                            .build();
                    if (NetworkUtil.isOffline()) {
                        request = request.apply(new RequestOptions().onlyRetrieveFromCache(true));
                    }
                    Future<Drawable> future = request.submit();
                    Drawable drawable = null;
                    try {
                        drawable = future.get(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (Exception ignored) {
                        future.cancel(true);
                    }
                    if (drawable != null) {
                        drawables.add(drawable);
                    }
                    if (drawables.size() >= MAX_TILES) {
                        break;
                    }
                }

                if (drawables.isEmpty()) {
                    return;
                }

                Bitmap composite = createCompositeBitmap(drawables);
                if (composite == null) {
                    return;
                }

                File file = getCoverFile(context, playlistId);
                if (file != null) {
                    saveBitmapSync(file, composite);
                }

                if (callback != null) {
                    Drawable drawable = RoundedBitmapDrawableFactory.create(context.getResources(), composite);
                    ((RoundedBitmapDrawable) drawable).setCornerRadius(
                            CustomGlideRequest.getCornerRadiusPx(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), CustomGlideRequest.ResourceType.Playlist)
                    );
                    ((RoundedBitmapDrawable) drawable).setAntiAlias(true);
                    new Handler(Looper.getMainLooper()).post(() -> callback.onCompositeReady(drawable));
                }
            } finally {
                IN_FLIGHT.remove(playlistId);
            }
        });
    }

    private static Bitmap createCompositeBitmap(List<Drawable> drawables) {
        if (drawables == null || drawables.isEmpty()) return null;
        List<Bitmap> bitmaps = new ArrayList<>();
        for (Drawable drawable : drawables) {
            Bitmap bitmap = drawableToBitmap(drawable);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmaps.add(bitmap);
            }
        }

        if (bitmaps.isEmpty()) return null;

        Bitmap first = bitmaps.get(0);
        int baseSize = Math.min(first.getWidth(), first.getHeight());
        if (baseSize <= 0) {
            baseSize = 256;
        }

        int tileSize = baseSize;
        Bitmap composite = Bitmap.createBitmap(tileSize * 2, tileSize * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(composite);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        int count = Math.min(MAX_TILES, bitmaps.size());
        for (int i = 0; i < MAX_TILES; i++) {
            Bitmap src = bitmaps.get(i % count);
            Bitmap scaled = src;
            if (src.getWidth() != tileSize || src.getHeight() != tileSize) {
                scaled = Bitmap.createScaledBitmap(src, tileSize, tileSize, true);
            }
            int left = (i % 2) * tileSize;
            int top = (i / 2) * tileSize;
            canvas.drawBitmap(scaled, left, top, paint);
        }

        return composite;
    }

    @Nullable
    private static File getCoverFile(Context context, String playlistId) {
        try {
            String key = hashId(playlistId);
            File dir = new File(context.getFilesDir(), DIR_NAME);
            return new File(dir, key + ".png");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void saveBitmap(File file, Bitmap bitmap) {
        IO_EXECUTOR.execute(() -> {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void saveBitmapSync(File file, Bitmap bitmap) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = Math.max(drawable.getIntrinsicWidth(), 1);
        int height = Math.max(drawable.getIntrinsicHeight(), 1);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private static String hashId(String playlistId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(playlistId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(b & 0xff);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(playlistId.hashCode());
        }
    }

    public interface CompositeCallback {
        void onCompositeReady(Drawable drawable);
    }
}
