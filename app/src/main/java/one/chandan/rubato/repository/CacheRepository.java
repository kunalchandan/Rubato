package one.chandan.rubato.repository;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.CachedResponseDao;
import one.chandan.rubato.model.CachedResponse;
import one.chandan.rubato.util.AppExecutors;
import android.util.Log;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CacheRepository {
    private static final String TAG = "CacheRepository";
    private static final long MAX_PAYLOAD_CHARS_DEFAULT = 1_000_000L;
    private static final long MAX_PAYLOAD_CHARS_LARGE = 1_000_000L;
    private static final long MAX_PAYLOAD_CHARS_XL = 1_000_000L;
    private static final long MAX_CHUNK_PAYLOAD_CHARS = 1_000_000L;
    private static final String CHUNK_SUFFIX = "_chunk_";
    private final CachedResponseDao cachedResponseDao = AppDatabase.getInstance().cachedResponseDao();
    private final Gson gson = new Gson();

    public void save(String key, Object data) {
        if (data == null) return;
        AppExecutors.io().execute(() -> {
            Object payload = data;
            if (data instanceof Collection) {
                Object[] snapshot = ((Collection<?>) data).toArray();
                payload = Arrays.asList(snapshot);
            } else if (data instanceof Map) {
                payload = new LinkedHashMap<>((Map<?, ?>) data);
            }
            String json = gson.toJson(payload);
            if (data instanceof Collection && json.length() > MAX_CHUNK_PAYLOAD_CHARS) {
                saveChunked(key, (Collection<?>) data);
                return;
            }
            if (isPayloadTooLarge(key, json.length())) {
                Log.w(TAG, "Skipping cache save for " + key + " payload chars=" + json.length());
                cachedResponseDao.delete(key);
                return;
            }
            cachedResponseDao.insert(new CachedResponse(key, json, System.currentTimeMillis()));
        });
    }

    public <T> void load(String key, Type type, CacheResult<T> callback) {
        AppExecutors.io().execute(() -> {
            T value = loadChunked(key, type);
            if (value != null) {
                callback.onLoaded(value);
                return;
            }
            if (isPayloadTooLarge(key)) {
                return;
            }
            CachedResponse cached = cachedResponseDao.get(key);
            if (cached == null || cached.getPayload() == null) return;
            if (shouldSkipForMemory(key, cached.getPayload().length())) {
                cachedResponseDao.delete(key);
                return;
            }
            try {
                T parsed = gson.fromJson(cached.getPayload(), type);
                if (parsed != null) {
                    callback.onLoaded(parsed);
                }
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "Cache parse OOM for " + key + " payload chars=" + cached.getPayload().length(), oom);
                cachedResponseDao.delete(key);
            } catch (RuntimeException e) {
                Log.w(TAG, "Cache parse failed for " + key, e);
                cachedResponseDao.delete(key);
            }
        });
    }

    public <T> void loadOrNull(String key, Type type, CacheResult<T> callback) {
        AppExecutors.io().execute(() -> {
            T value = loadChunked(key, type);
            if (value != null) {
                callback.onLoaded(value);
                return;
            }
            if (isPayloadTooLarge(key)) {
                callback.onLoaded(null);
                return;
            }
            CachedResponse cached = cachedResponseDao.get(key);
            if (cached == null || cached.getPayload() == null) {
                callback.onLoaded(null);
                return;
            }
            if (shouldSkipForMemory(key, cached.getPayload().length())) {
                cachedResponseDao.delete(key);
                callback.onLoaded(null);
                return;
            }
            try {
                T parsed = gson.fromJson(cached.getPayload(), type);
                callback.onLoaded(parsed);
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "Cache parse OOM for " + key + " payload chars=" + cached.getPayload().length(), oom);
                cachedResponseDao.delete(key);
                callback.onLoaded(null);
            } catch (RuntimeException e) {
                Log.w(TAG, "Cache parse failed for " + key, e);
                cachedResponseDao.delete(key);
                callback.onLoaded(null);
            }
        });
    }

    public <T> T loadBlocking(String key, Type type) {
        T value = loadChunked(key, type);
        if (value != null) {
            return value;
        }
        if (isPayloadTooLarge(key)) {
            return null;
        }
        CachedResponse cached = cachedResponseDao.get(key);
        if (cached == null || cached.getPayload() == null) {
            return null;
        }
        if (shouldSkipForMemory(key, cached.getPayload().length())) {
            cachedResponseDao.delete(key);
            return null;
        }
        try {
            return gson.fromJson(cached.getPayload(), type);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "Cache parse OOM for " + key + " payload chars=" + cached.getPayload().length(), oom);
            cachedResponseDao.delete(key);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cache parse failed for " + key, e);
            cachedResponseDao.delete(key);
        }
        return null;
    }

    public void loadPayloadSize(List<String> keys, CacheResult<Long> callback) {
        AppExecutors.io().execute(() -> {
            if (keys == null || keys.isEmpty()) {
                callback.onLoaded(0L);
                return;
            }
            long total = 0L;
            List<String> standardKeys = new ArrayList<>();
            for (String key : keys) {
                Long chunkSize = cachedResponseDao.getPayloadSizeLike(chunkPrefix(key) + "%");
                if (chunkSize != null && chunkSize > 0) {
                    total += chunkSize;
                } else {
                    standardKeys.add(key);
                }
            }
            if (!standardKeys.isEmpty()) {
                Long size = cachedResponseDao.getPayloadSize(standardKeys);
                if (size != null) total += size;
            }
            callback.onLoaded(total);
        });
    }

    public void loadPayloadSizeLike(String prefix, CacheResult<Long> callback) {
        AppExecutors.io().execute(() -> {
            Long size = cachedResponseDao.getPayloadSizeLike(prefix);
            callback.onLoaded(size != null ? size : 0L);
        });
    }

    public void loadPayloadSize(String key, CacheResult<Long> callback) {
        AppExecutors.io().execute(() -> {
            Long chunkSize = cachedResponseDao.getPayloadSizeLike(chunkPrefix(key) + "%");
            if (chunkSize != null && chunkSize > 0) {
                callback.onLoaded(chunkSize);
                return;
            }
            Long size = cachedResponseDao.getPayloadSize(key);
            callback.onLoaded(size != null ? size : 0L);
        });
    }

    private boolean isPayloadTooLarge(String key) {
        Long size = cachedResponseDao.getPayloadSizeLike(chunkPrefix(key) + "%");
        if (size == null || size <= 0) {
            size = cachedResponseDao.getPayloadSize(key);
        }
        if (size == null) return false;
        return isPayloadTooLarge(key, size);
    }

    private boolean isPayloadTooLarge(String key, long size) {
        long limit = maxPayloadForKey(key);
        if (size > limit) {
            Log.w(TAG, "Skipping cache load for " + key + " payload chars=" + size);
            cachedResponseDao.delete(key);
            return true;
        }
        return false;
    }

    private long maxPayloadForKey(String key) {
        if (key == null || key.isEmpty()) return MAX_PAYLOAD_CHARS_DEFAULT;
        if (key.endsWith("artists_all")
                || key.endsWith("albums_all")
                || key.endsWith("genres_all")
                || key.endsWith("playlists")) {
            return MAX_PAYLOAD_CHARS_LARGE;
        }
        if (key.endsWith("songs_all")) {
            return MAX_PAYLOAD_CHARS_XL;
        }
        return MAX_PAYLOAD_CHARS_DEFAULT;
    }

    private String chunkPrefix(String key) {
        return key + CHUNK_SUFFIX;
    }

    private void saveChunked(String key, Collection<?> data) {
        if (key == null || key.isEmpty()) return;
        String prefix = chunkPrefix(key);
        cachedResponseDao.deleteLike(prefix + "%");
        cachedResponseDao.delete(key);
        if (data == null || data.isEmpty()) return;
        List<Object> chunk = new ArrayList<>();
        long chunkChars = 2;
        int index = 0;
        for (Object item : data) {
            String json = gson.toJson(item);
            long itemChars = json != null ? json.length() : 4;
            long nextChars = chunkChars + itemChars + (chunk.isEmpty() ? 0 : 1);
            if (!chunk.isEmpty() && nextChars > MAX_CHUNK_PAYLOAD_CHARS) {
                storeChunk(prefix, index++, chunk);
                chunk.clear();
                chunkChars = 2;
            }
            chunk.add(item);
            chunkChars += itemChars + (chunk.size() == 1 ? 0 : 1);
        }
        if (!chunk.isEmpty()) {
            storeChunk(prefix, index, chunk);
        }
    }

    private void storeChunk(String prefix, int index, List<Object> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        String json = gson.toJson(chunk);
        if (json == null) return;
        if (json.length() > MAX_CHUNK_PAYLOAD_CHARS) {
            Log.w(TAG, "Chunk payload too large for " + prefix + " index=" + index + " chars=" + json.length());
            return;
        }
        String chunkKey = prefix + String.format(Locale.US, "%04d", index);
        cachedResponseDao.insert(new CachedResponse(chunkKey, json, System.currentTimeMillis()));
    }

    @SuppressWarnings("unchecked")
    private <T> T loadChunked(String key, Type type) {
        if (key == null || key.isEmpty()) return null;
        String prefix = chunkPrefix(key);
        Long size = cachedResponseDao.getPayloadSizeLike(prefix + "%");
        if (size == null || size <= 0) {
            return null;
        }
        if (size != null && size > 0 && shouldSkipForMemory(key, size)) {
            cachedResponseDao.deleteLike(prefix + "%");
            return null;
        }
        List<CachedResponse> chunks = cachedResponseDao.getLike(prefix + "%");
        if (chunks == null || chunks.isEmpty()) return null;
        List<Object> combined = new ArrayList<>();
        for (CachedResponse chunk : chunks) {
            if (chunk == null || chunk.getPayload() == null) continue;
            try {
                List<?> parsed = gson.fromJson(chunk.getPayload(), type);
                if (parsed != null) {
                    combined.addAll(parsed);
                }
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "Chunk parse OOM for " + prefix, oom);
                cachedResponseDao.deleteLike(prefix + "%");
                return null;
            } catch (RuntimeException e) {
                Log.w(TAG, "Chunk parse failed for " + prefix, e);
                cachedResponseDao.deleteLike(prefix + "%");
                return null;
            }
        }
        return (T) combined;
    }

    private boolean shouldSkipForMemory(String key, long payloadChars) {
        if (payloadChars <= 0) return false;
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long available = Math.max(0L, maxMemory - (totalMemory - freeMemory));
        long estimatedBytes = payloadChars * 2L;
        if (estimatedBytes > maxMemory / 4) {
            Log.w(TAG, "Skipping cache parse for " + key + " payload chars=" + payloadChars + " exceeds heap budget");
            return true;
        }
        if (estimatedBytes > available / 2) {
            Log.w(TAG, "Skipping cache parse for " + key + " payload chars=" + payloadChars + " low memory");
            return true;
        }
        return false;
    }

    public interface CacheResult<T> {
        void onLoaded(T value);
    }
}
