package one.chandan.rubato.repository;

import androidx.lifecycle.LiveData;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.DownloadDao;
import one.chandan.rubato.database.dao.FavoriteDao;
import one.chandan.rubato.model.Download;
import one.chandan.rubato.model.Favorite;
import one.chandan.rubato.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DownloadRepository {
    private final DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();

    public LiveData<List<Download>> getLiveDownload() {
        return downloadDao.getAll();
    }

    public Download getDownload(String id) {
        Future<Download> future = AppExecutors.io().submit(() -> downloadDao.getOne(id));
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public void insert(Download download) {
        AppExecutors.io().execute(() -> downloadDao.insert(download));
    }

    public void update(String id) {
        AppExecutors.io().execute(() -> downloadDao.update(id));
    }

    public void updateDownloadUri(String id, String uri) {
        AppExecutors.io().execute(() -> downloadDao.updateDownloadUri(id, uri));
    }

    public void insertAll(List<Download> downloads) {
        AppExecutors.io().execute(() -> downloadDao.insertAll(downloads));
    }

    public void deleteAll() {
        AppExecutors.io().execute(downloadDao::deleteAll);
    }

    public void delete(String id) {
        AppExecutors.io().execute(() -> downloadDao.delete(id));
    }
}
