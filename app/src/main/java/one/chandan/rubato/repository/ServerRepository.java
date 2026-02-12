package one.chandan.rubato.repository;

import androidx.lifecycle.LiveData;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.ServerDao;
import one.chandan.rubato.model.Server;
import one.chandan.rubato.util.AppExecutors;

import java.util.List;

public class ServerRepository {
    private static final String TAG = "QueueRepository";

    private final ServerDao serverDao = AppDatabase.getInstance().serverDao();

    public LiveData<List<Server>> getLiveServer() {
        return serverDao.getAll();
    }

    public void insert(Server server) {
        AppExecutors.io().execute(() -> serverDao.insert(server));
    }

    public void delete(Server server) {
        AppExecutors.io().execute(() -> serverDao.delete(server));
    }
}
