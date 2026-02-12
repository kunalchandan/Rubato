package one.chandan.rubato.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import one.chandan.rubato.App;
import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.database.dao.QueueDao;
import one.chandan.rubato.model.Queue;
import one.chandan.rubato.subsonic.base.ApiResponse;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.PlayQueue;
import one.chandan.rubato.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QueueRepository {
    private static final String TAG = "QueueRepository";

    private final QueueDao queueDao = AppDatabase.getInstance().queueDao();

    public LiveData<List<Queue>> getLiveQueue() {
        return queueDao.getAll();
    }

    public List<Child> getMedia() {
        List<Child> media = new ArrayList<>();

        Future<List<Queue>> future = AppExecutors.io().submit(queueDao::getAllSimple);
        try {
            List<Queue> queued = future.get();
            if (queued != null) {
                media = queued.stream()
                        .map(Child.class::cast)
                        .collect(Collectors.toList());
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }

        return media;
    }

    public MutableLiveData<PlayQueue> getPlayQueue() {
        MutableLiveData<PlayQueue> playQueue = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .getPlayQueue()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlayQueue() != null) {
                            playQueue.setValue(response.body().getSubsonicResponse().getPlayQueue());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        playQueue.setValue(null);
                    }
                });

        return playQueue;
    }

    public void savePlayQueue(List<String> ids, String current, long position) {
        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .savePlayQueue(ids, current, position)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void insert(Child media, boolean reset, int afterIndex) {
        Future<?> future = AppExecutors.io().submit(() -> {
            List<Queue> mediaList = new ArrayList<>();

            if (!reset) {
                List<Queue> queued = queueDao.getAllSimple();
                if (queued != null) {
                    mediaList = queued;
                }
            }

            Queue queueItem = new Queue(media);
            mediaList.add(afterIndex, queueItem);

            for (int i = 0; i < mediaList.size(); i++) {
                mediaList.get(i).setTrackOrder(i);
            }

            queueDao.deleteAll();
            queueDao.insertAll(mediaList);
        });
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void insertAll(List<Child> toAdd, boolean reset, int afterIndex) {
        Future<?> future = AppExecutors.io().submit(() -> {
            List<Queue> media = new ArrayList<>();

            if (!reset) {
                List<Queue> queued = queueDao.getAllSimple();
                if (queued != null) {
                    media = queued;
                }
            }

            for (int i = 0; i < toAdd.size(); i++) {
                Queue queueItem = new Queue(toAdd.get(i));
                media.add(afterIndex + i, queueItem);
            }

            for (int i = 0; i < media.size(); i++) {
                media.get(i).setTrackOrder(i);
            }

            queueDao.deleteAll();
            queueDao.insertAll(media);
        });
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void delete(int position) {
        AppExecutors.io().execute(() -> queueDao.delete(position));
    }

    public void deleteAll() {
        AppExecutors.io().execute(queueDao::deleteAll);
    }

    public int count() {
        Future<Integer> future = AppExecutors.io().submit(queueDao::count);
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    public void setLastPlayedTimestamp(String id) {
        AppExecutors.io().execute(() -> queueDao.setLastPlay(id, System.currentTimeMillis()));
    }

    public void setPlayingPausedTimestamp(String id, long ms) {
        AppExecutors.io().execute(() -> queueDao.setPlayingChanged(id, ms));
    }

    public int getLastPlayedMediaIndex() {
        Future<Queue> future = AppExecutors.io().submit(queueDao::getLastPlayed);
        try {
            Queue lastMediaPlayed = future.get();
            return lastMediaPlayed != null ? lastMediaPlayed.getTrackOrder() : 0;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    public long getLastPlayedMediaTimestamp() {
        Future<Queue> future = AppExecutors.io().submit(queueDao::getLastPlayed);
        try {
            Queue lastMediaPlayed = future.get();
            return lastMediaPlayed != null ? lastMediaPlayed.getPlayingChanged() : 0L;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return 0L;
    }
}
