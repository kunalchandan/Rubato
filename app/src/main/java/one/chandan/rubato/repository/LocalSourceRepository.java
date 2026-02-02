package one.chandan.rubato.repository;

import androidx.lifecycle.LiveData;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.LocalSourceDao;
import one.chandan.rubato.model.LocalSource;

import java.util.List;

public class LocalSourceRepository {
    private final LocalSourceDao localSourceDao = AppDatabase.getInstance().localSourceDao();

    public LiveData<List<LocalSource>> getLiveSources() {
        return localSourceDao.getAll();
    }

    public List<LocalSource> getSourcesSync() {
        GetAllThreadSafe getAll = new GetAllThreadSafe(localSourceDao);
        Thread thread = new Thread(getAll);
        thread.start();

        try {
            thread.join();
            return getAll.getSources();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void insert(LocalSource source) {
        InsertThreadSafe insert = new InsertThreadSafe(localSourceDao, source);
        Thread thread = new Thread(insert);
        thread.start();
    }

    public void delete(LocalSource source) {
        if (source == null) return;
        DeleteThreadSafe delete = new DeleteThreadSafe(localSourceDao, source);
        Thread thread = new Thread(delete);
        thread.start();
    }

    private static class InsertThreadSafe implements Runnable {
        private final LocalSourceDao localSourceDao;
        private final LocalSource source;

        public InsertThreadSafe(LocalSourceDao localSourceDao, LocalSource source) {
            this.localSourceDao = localSourceDao;
            this.source = source;
        }

        @Override
        public void run() {
            localSourceDao.insert(source);
        }
    }

    private static class DeleteThreadSafe implements Runnable {
        private final LocalSourceDao localSourceDao;
        private final LocalSource source;

        public DeleteThreadSafe(LocalSourceDao localSourceDao, LocalSource source) {
            this.localSourceDao = localSourceDao;
            this.source = source;
        }

        @Override
        public void run() {
            localSourceDao.delete(source);
        }
    }

    private static class GetAllThreadSafe implements Runnable {
        private final LocalSourceDao localSourceDao;
        private List<LocalSource> sources;

        public GetAllThreadSafe(LocalSourceDao localSourceDao) {
            this.localSourceDao = localSourceDao;
        }

        @Override
        public void run() {
            sources = localSourceDao.getAllSync();
        }

        public List<LocalSource> getSources() {
            return sources;
        }
    }
}
