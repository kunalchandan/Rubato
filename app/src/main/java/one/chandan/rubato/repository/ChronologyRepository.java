package one.chandan.rubato.repository;

import androidx.lifecycle.LiveData;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.ChronologyDao;
import one.chandan.rubato.model.Chronology;

import java.util.Calendar;
import java.util.List;

public class ChronologyRepository {
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();

    public LiveData<List<Chronology>> getChronology(String server, long start, long end) {
        return chronologyDao.getAllFrom(start, end, server);
    }

    public List<Chronology> getLastPlayedSimple(String server, int count) {
        GetLastPlayedThreadSafe task = new GetLastPlayedThreadSafe(chronologyDao, server, count);
        Thread thread = new Thread(task);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return task.getItems();
    }

    public void insert(Chronology item) {
        InsertThreadSafe insert = new InsertThreadSafe(chronologyDao, item);
        Thread thread = new Thread(insert);
        thread.start();
    }

    private static class InsertThreadSafe implements Runnable {
        private final ChronologyDao chronologyDao;
        private final Chronology item;

        public InsertThreadSafe(ChronologyDao chronologyDao, Chronology item) {
            this.chronologyDao = chronologyDao;
            this.item = item;
        }

        @Override
        public void run() {
            chronologyDao.insert(item);
        }
    }

    private static class GetLastPlayedThreadSafe implements Runnable {
        private final ChronologyDao chronologyDao;
        private final String server;
        private final int count;
        private List<Chronology> items;

        public GetLastPlayedThreadSafe(ChronologyDao chronologyDao, String server, int count) {
            this.chronologyDao = chronologyDao;
            this.server = server;
            this.count = count;
        }

        @Override
        public void run() {
            items = chronologyDao.getLastPlayedSimple(server, count);
        }

        public List<Chronology> getItems() {
            return items;
        }
    }
}
