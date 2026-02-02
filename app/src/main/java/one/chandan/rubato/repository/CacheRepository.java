package one.chandan.rubato.repository;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.CachedResponseDao;
import one.chandan.rubato.model.CachedResponse;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.List;

public class CacheRepository {
    private final CachedResponseDao cachedResponseDao = AppDatabase.getInstance().cachedResponseDao();
    private final Gson gson = new Gson();

    public void save(String key, Object data) {
        if (data == null) return;
        new Thread(() -> cachedResponseDao.insert(
                new CachedResponse(key, gson.toJson(data), System.currentTimeMillis())
        )).start();
    }

    public <T> void load(String key, Type type, CacheResult<T> callback) {
        new Thread(() -> {
            CachedResponse cached = cachedResponseDao.get(key);
            if (cached == null || cached.getPayload() == null) return;
            T value = gson.fromJson(cached.getPayload(), type);
            if (value != null) {
                callback.onLoaded(value);
            }
        }).start();
    }

    public <T> void loadOrNull(String key, Type type, CacheResult<T> callback) {
        new Thread(() -> {
            CachedResponse cached = cachedResponseDao.get(key);
            if (cached == null || cached.getPayload() == null) {
                callback.onLoaded(null);
                return;
            }
            T value = gson.fromJson(cached.getPayload(), type);
            callback.onLoaded(value);
        }).start();
    }

    public void loadPayloadSize(List<String> keys, CacheResult<Long> callback) {
        new Thread(() -> {
            Long size = cachedResponseDao.getPayloadSize(keys);
            callback.onLoaded(size != null ? size : 0L);
        }).start();
    }

    public void loadPayloadSizeLike(String prefix, CacheResult<Long> callback) {
        new Thread(() -> {
            Long size = cachedResponseDao.getPayloadSizeLike(prefix);
            callback.onLoaded(size != null ? size : 0L);
        }).start();
    }

    public interface CacheResult<T> {
        void onLoaded(T value);
    }
}
