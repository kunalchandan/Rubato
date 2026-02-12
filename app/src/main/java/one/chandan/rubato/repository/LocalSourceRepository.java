package one.chandan.rubato.repository;

import androidx.lifecycle.LiveData;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.LocalSourceDao;
import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.util.AppExecutors;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class LocalSourceRepository {
    private final LocalSourceDao localSourceDao = AppDatabase.getInstance().localSourceDao();

    public LiveData<List<LocalSource>> getLiveSources() {
        return localSourceDao.getAll();
    }

    public List<LocalSource> getSourcesSync() {
        Future<List<LocalSource>> future = AppExecutors.io().submit(localSourceDao::getAllSync);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public void insert(LocalSource source) {
        AppExecutors.io().execute(() -> localSourceDao.insert(source));
    }

    public void delete(LocalSource source) {
        if (source == null) return;
        AppExecutors.io().execute(() -> localSourceDao.delete(source));
    }
}
