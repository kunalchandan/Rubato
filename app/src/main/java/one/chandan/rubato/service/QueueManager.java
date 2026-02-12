package one.chandan.rubato.service;

import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import one.chandan.rubato.App;
import one.chandan.rubato.interfaces.MediaIndexCallback;
import one.chandan.rubato.model.Chronology;
import one.chandan.rubato.repository.ChronologyRepository;
import one.chandan.rubato.repository.QueueRepository;
import one.chandan.rubato.repository.SongRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.InternetRadioStation;
import one.chandan.rubato.subsonic.models.PodcastEpisode;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.util.MediaItemBuilder;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.widget.WidgetUpdateHelper;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class QueueManager {

    private final ExecutorService executor = AppExecutors.queue();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final QueueRepository queueRepository = new QueueRepository();
    private final SongRepository songRepository = new SongRepository();
    private final ChronologyRepository chronologyRepository = new ChronologyRepository();

    private final List<String> playNextQueue = new ArrayList<>();
    private final List<String> comingUpQueue = new ArrayList<>();

    public void reset(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            if (mediaBrowser.isPlaying()) {
                mediaBrowser.pause();
            }
            mediaBrowser.stop();
            mediaBrowser.clearMediaItems();
            playNextQueue.clear();
            comingUpQueue.clear();
            clearDatabase();
            notifyWidgetUpdate();
        });
    }

    public void hide(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            if (mediaBrowser.isPlaying()) {
                mediaBrowser.pause();
            }
        });
    }

    public void check(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            if (mediaBrowser.getMediaItemCount() < 1) {
                executor.execute(() -> {
                    List<Child> media = queueRepository.getMedia();
                    if (media != null && !media.isEmpty()) {
                        int lastIndex = queueRepository.getLastPlayedMediaIndex();
                        long lastTimestamp = queueRepository.getLastPlayedMediaTimestamp();
                        mainHandler.post(() -> initOnBrowser(mediaBrowser, media, lastIndex, lastTimestamp));
                    }
                });
            }
        });
    }

    public void init(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            executor.execute(() -> {
                int lastIndex = queueRepository.getLastPlayedMediaIndex();
                long lastTimestamp = queueRepository.getLastPlayedMediaTimestamp();
                mainHandler.post(() -> initOnBrowser(mediaBrowser, media, lastIndex, lastTimestamp));
            });
        });
    }

    public void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            playNextQueue.clear();
            comingUpQueue.clear();
            mediaBrowser.clearMediaItems();
            mediaBrowser.setMediaItems(MediaItemBuilder.fromChildren(media));
            mediaBrowser.prepare();
            mediaBrowser.seekTo(startIndex, 0);
            mediaBrowser.play();
            enqueueDatabase(media, true, 0);
            notifyWidgetUpdate();
        });
    }

    public void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            playNextQueue.clear();
            comingUpQueue.clear();
            mediaBrowser.clearMediaItems();
            mediaBrowser.setMediaItem(MediaItemBuilder.fromChild(media));
            mediaBrowser.prepare();
            mediaBrowser.play();
            enqueueDatabase(media, true, 0);
            notifyWidgetUpdate();
        });
    }

    public void startRadio(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, InternetRadioStation internetRadioStation) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            mediaBrowser.clearMediaItems();
            mediaBrowser.setMediaItem(MediaItemBuilder.fromInternetRadioStation(internetRadioStation));
            mediaBrowser.prepare();
            mediaBrowser.play();
            notifyWidgetUpdate();
        });
    }

    public void startPodcast(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, PodcastEpisode podcastEpisode) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            mediaBrowser.clearMediaItems();
            mediaBrowser.setMediaItem(MediaItemBuilder.fromPodcastEpisode(podcastEpisode));
            mediaBrowser.prepare();
            mediaBrowser.play();
            notifyWidgetUpdate();
        });
    }

    public void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, boolean playImmediatelyAfter) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            if (playImmediatelyAfter) {
                int insertIndex = getPlayNextInsertIndex(mediaBrowser);
                enqueueDatabase(media, false, insertIndex);
                mediaBrowser.addMediaItems(insertIndex, MediaItemBuilder.fromChildren(media));
                addToPlayNextQueue(media);
            } else {
                int insertIndex = getComingUpInsertIndex(mediaBrowser);
                enqueueDatabase(media, false, insertIndex);
                mediaBrowser.addMediaItems(insertIndex, MediaItemBuilder.fromChildren(media));
                addToComingUpQueue(media);
            }
            notifyWidgetUpdate();
        });
    }

    public void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, boolean playImmediatelyAfter) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            if (playImmediatelyAfter) {
                int insertIndex = getPlayNextInsertIndex(mediaBrowser);
                enqueueDatabase(media, false, insertIndex);
                mediaBrowser.addMediaItem(insertIndex, MediaItemBuilder.fromChild(media));
                addToPlayNextQueue(media);
            } else {
                int insertIndex = getComingUpInsertIndex(mediaBrowser);
                enqueueDatabase(media, false, insertIndex);
                mediaBrowser.addMediaItem(insertIndex, MediaItemBuilder.fromChild(media));
                addToComingUpQueue(media);
            }
            notifyWidgetUpdate();
        });
    }

    public void shuffle(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex, int endIndex) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            mediaBrowser.removeMediaItems(startIndex, endIndex + 1);
            mediaBrowser.addMediaItems(MediaItemBuilder.fromChildren(media).subList(startIndex, endIndex + 1));
            swapDatabase(media);
            notifyWidgetUpdate();
        });
    }

    public void swap(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int from, int to) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            mediaBrowser.moveMediaItem(from, to);
            swapDatabase(media);
            notifyWidgetUpdate();
        });
    }

    public void remove(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            if (mediaBrowser.getMediaItemCount() > 1 && mediaBrowser.getCurrentMediaItemIndex() != toRemove) {
                mediaBrowser.removeMediaItem(toRemove);
                removeDatabase(media, toRemove);
                refreshQueues(mediaBrowser);
            } else {
                removeDatabase(media, -1);
            }
            notifyWidgetUpdate();
        });
    }

    public void removeAt(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            int itemCount = mediaBrowser.getMediaItemCount();
            if (toRemove >= 0 && toRemove < itemCount) {
                mediaBrowser.removeMediaItem(toRemove);
            }
            replaceDatabase(media);
            refreshQueues(mediaBrowser);
            notifyWidgetUpdate();
        });
    }

    public void insertAt(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, int index) {
        if (media == null) return;
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            int itemCount = mediaBrowser.getMediaItemCount();
            int safeIndex = Math.max(0, Math.min(index, itemCount));
            mediaBrowser.addMediaItem(safeIndex, MediaItemBuilder.fromChild(media));
            enqueueDatabase(media, false, safeIndex);
            refreshQueues(mediaBrowser);
            notifyWidgetUpdate();
        });
    }

    public void removeRange(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int fromItem, int toItem) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> {
            mediaBrowser.removeMediaItems(fromItem, toItem);
            removeRangeDatabase(media, fromItem, toItem);
            refreshQueues(mediaBrowser);
            notifyWidgetUpdate();
        });
    }

    public void getCurrentIndex(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaIndexCallback callback) {
        runOnBrowser(mediaBrowserListenableFuture, mediaBrowser -> callback.onIndex(mediaBrowser.getCurrentMediaItemIndex()));
    }

    public void setLastPlayedTimestamp(MediaItem mediaItem) {
        if (mediaItem != null) queueRepository.setLastPlayedTimestamp(mediaItem.mediaId);
    }

    public void setPlayingPausedTimestamp(MediaItem mediaItem, long ms) {
        if (mediaItem != null) queueRepository.setPlayingPausedTimestamp(mediaItem.mediaId, ms);
    }

    public void scrobble(MediaItem mediaItem, boolean submission) {
        if (mediaItem != null && Preferences.isScrobblingEnabled()) {
            songRepository.scrobble(mediaItem.mediaMetadata.extras.getString("id"), submission);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public void continuousPlay(MediaItem mediaItem) {
        if (mediaItem != null && Preferences.isContinuousPlayEnabled() && Preferences.isInstantMixUsable()) {
            Preferences.setLastInstantMix();

            LiveData<List<Child>> instantMix = songRepository.getInstantMix(mediaItem.mediaId, 10);
            instantMix.observeForever(new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> media) {
                    if (media != null) {
                        ListenableFuture<MediaBrowser> mediaBrowserListenableFuture = new MediaBrowser.Builder(
                                App.getContext(),
                                new SessionToken(App.getContext(), new ComponentName(App.getContext(), MediaService.class))
                        ).buildAsync();

                        enqueue(mediaBrowserListenableFuture, media, true);
                    }

                    instantMix.removeObserver(this);
                }
            });
        }
    }

    public void saveChronology(MediaItem mediaItem) {
        if (mediaItem != null) {
            chronologyRepository.insert(new Chronology(mediaItem));
        }
    }

    public void clearDatabase() {
        queueRepository.deleteAll();
    }

    private void runOnBrowser(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, BrowserAction action) {
        if (mediaBrowserListenableFuture == null) return;
        mediaBrowserListenableFuture.addListener(() -> executor.execute(() -> {
            try {
                if (mediaBrowserListenableFuture.isDone()) {
                    MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                    mainHandler.post(() -> action.run(mediaBrowser));
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }), MoreExecutors.directExecutor());
    }

    private interface BrowserAction {
        void run(MediaBrowser mediaBrowser);
    }

    private void addToPlayNextQueue(List<Child> media) {
        if (media == null || media.isEmpty()) return;
        for (Child item : media) {
            addToPlayNextQueue(item);
        }
    }

    private void addToPlayNextQueue(Child media) {
        if (media == null || media.getId() == null) return;
        playNextQueue.add(media.getId());
    }

    private void addToComingUpQueue(List<Child> media) {
        if (media == null || media.isEmpty()) return;
        for (Child item : media) {
            addToComingUpQueue(item);
        }
    }

    private void addToComingUpQueue(Child media) {
        if (media == null || media.getId() == null) return;
        comingUpQueue.add(media.getId());
    }

    private int getPlayNextInsertIndex(MediaBrowser mediaBrowser) {
        if (mediaBrowser == null) return 0;

        int itemCount = mediaBrowser.getMediaItemCount();
        if (itemCount == 0) return 0;

        int currentIndex = mediaBrowser.getCurrentMediaItemIndex();
        if (currentIndex < 0) currentIndex = -1;

        refreshQueues(mediaBrowser);
        int insertIndex = currentIndex + 1 + playNextQueue.size();

        if (insertIndex < 0) insertIndex = 0;
        if (insertIndex > itemCount) insertIndex = itemCount;

        return insertIndex;
    }

    private int getComingUpInsertIndex(MediaBrowser mediaBrowser) {
        if (mediaBrowser == null) return 0;

        int itemCount = mediaBrowser.getMediaItemCount();
        if (itemCount == 0) return 0;

        int currentIndex = mediaBrowser.getCurrentMediaItemIndex();
        if (currentIndex < 0) currentIndex = -1;

        refreshQueues(mediaBrowser);
        int insertIndex = currentIndex + 1 + playNextQueue.size() + comingUpQueue.size();

        if (insertIndex < 0) insertIndex = 0;
        if (insertIndex > itemCount) insertIndex = itemCount;

        return insertIndex;
    }

    private void refreshQueues(MediaBrowser mediaBrowser) {
        if (mediaBrowser == null) return;
        int currentIndex = mediaBrowser.getCurrentMediaItemIndex();
        refreshQueueList(mediaBrowser, playNextQueue, currentIndex);
        refreshQueueList(mediaBrowser, comingUpQueue, currentIndex);
    }

    private void refreshQueueList(MediaBrowser mediaBrowser, List<String> queue, int currentIndex) {
        if (queue.isEmpty()) return;
        List<String> filtered = new ArrayList<>();

        for (String id : queue) {
            int position = findMediaItemIndex(mediaBrowser, id);
            if (position > currentIndex) {
                filtered.add(id);
            }
        }

        queue.clear();
        queue.addAll(filtered);
    }

    private int findMediaItemIndex(MediaBrowser mediaBrowser, String mediaId) {
        if (mediaBrowser == null || mediaId == null) return -1;
        int count = mediaBrowser.getMediaItemCount();
        for (int i = 0; i < count; i++) {
            if (mediaId.equals(mediaBrowser.getMediaItemAt(i).mediaId)) {
                return i;
            }
        }
        return -1;
    }

    private void enqueueDatabase(List<Child> media, boolean reset, int afterIndex) {
        List<Child> copy = media == null ? new ArrayList<>() : new ArrayList<>(media);
        executor.execute(() -> queueRepository.insertAll(copy, reset, afterIndex));
    }

    private void enqueueDatabase(Child media, boolean reset, int afterIndex) {
        executor.execute(() -> queueRepository.insert(media, reset, afterIndex));
    }

    private void swapDatabase(List<Child> media) {
        List<Child> copy = media == null ? new ArrayList<>() : new ArrayList<>(media);
        executor.execute(() -> queueRepository.insertAll(copy, true, 0));
    }

    private void removeDatabase(List<Child> media, int toRemove) {
        if (toRemove != -1) {
            List<Child> copy = media == null ? new ArrayList<>() : new ArrayList<>(media);
            if (toRemove >= 0 && toRemove < copy.size()) {
                copy.remove(toRemove);
            }
            executor.execute(() -> queueRepository.insertAll(copy, true, 0));
        }
    }

    private void removeRangeDatabase(List<Child> media, int fromItem, int toItem) {
        List<Child> copy = media == null ? new ArrayList<>() : new ArrayList<>(media);
        if (fromItem >= 0 && fromItem < toItem && toItem <= copy.size()) {
            List<Child> toRemove = new ArrayList<>(copy.subList(fromItem, toItem));
            copy.removeAll(toRemove);
        }
        executor.execute(() -> queueRepository.insertAll(copy, true, 0));
    }

    private void replaceDatabase(List<Child> media) {
        List<Child> copy = media == null ? new ArrayList<>() : new ArrayList<>(media);
        executor.execute(() -> queueRepository.insertAll(copy, true, 0));
    }

    private void initOnBrowser(MediaBrowser mediaBrowser, List<Child> media, int lastIndex, long lastTimestamp) {
        playNextQueue.clear();
        comingUpQueue.clear();
        mediaBrowser.clearMediaItems();
        mediaBrowser.setMediaItems(MediaItemBuilder.fromChildren(media));
        mediaBrowser.seekTo(lastIndex, lastTimestamp);
        mediaBrowser.prepare();
        notifyWidgetUpdate();
    }

    private void notifyWidgetUpdate() {
        WidgetUpdateHelper.requestUpdate(App.getContext());
    }
}
