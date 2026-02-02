package one.chandan.rubato.service;

import android.content.ComponentName;

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
import one.chandan.rubato.util.MappingUtil;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.widget.WidgetUpdateHelper;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MediaManager {
    private static final String TAG = "MediaManager";
    private static final List<String> PLAY_NEXT_QUEUE = new ArrayList<>();
    private static final List<String> COMING_UP_QUEUE = new ArrayList<>();

    public static void reset(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (mediaBrowserListenableFuture.get().isPlaying()) {
                            mediaBrowserListenableFuture.get().pause();
                        }

                        mediaBrowserListenableFuture.get().stop();
                        mediaBrowserListenableFuture.get().clearMediaItems();
                        PLAY_NEXT_QUEUE.clear();
                        COMING_UP_QUEUE.clear();
                        clearDatabase();
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void hide(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (mediaBrowserListenableFuture.get().isPlaying()) {
                            mediaBrowserListenableFuture.get().pause();
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void check(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (mediaBrowserListenableFuture.get().getMediaItemCount() < 1) {
                            List<Child> media = getQueueRepository().getMedia();
                            if (media != null && media.size() >= 1) {
                                init(mediaBrowserListenableFuture, media);
                            }
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void init(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        PLAY_NEXT_QUEUE.clear();
                        COMING_UP_QUEUE.clear();
                        mediaBrowserListenableFuture.get().clearMediaItems();
                        mediaBrowserListenableFuture.get().setMediaItems(MappingUtil.mapMediaItems(media));
                        mediaBrowserListenableFuture.get().seekTo(getQueueRepository().getLastPlayedMediaIndex(), getQueueRepository().getLastPlayedMediaTimestamp());
                        mediaBrowserListenableFuture.get().prepare();
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        PLAY_NEXT_QUEUE.clear();
                        COMING_UP_QUEUE.clear();
                        mediaBrowserListenableFuture.get().clearMediaItems();
                        mediaBrowserListenableFuture.get().setMediaItems(MappingUtil.mapMediaItems(media));
                        mediaBrowserListenableFuture.get().prepare();
                        mediaBrowserListenableFuture.get().seekTo(startIndex, 0);
                        mediaBrowserListenableFuture.get().play();
                        enqueueDatabase(media, true, 0);
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        PLAY_NEXT_QUEUE.clear();
                        COMING_UP_QUEUE.clear();
                        mediaBrowserListenableFuture.get().clearMediaItems();
                        mediaBrowserListenableFuture.get().setMediaItem(MappingUtil.mapMediaItem(media));
                        mediaBrowserListenableFuture.get().prepare();
                        mediaBrowserListenableFuture.get().play();
                        enqueueDatabase(media, true, 0);
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startRadio(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, InternetRadioStation internetRadioStation) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        mediaBrowserListenableFuture.get().clearMediaItems();
                        mediaBrowserListenableFuture.get().setMediaItem(MappingUtil.mapInternetRadioStation(internetRadioStation));
                        mediaBrowserListenableFuture.get().prepare();
                        mediaBrowserListenableFuture.get().play();
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startPodcast(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, PodcastEpisode podcastEpisode) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        mediaBrowserListenableFuture.get().clearMediaItems();
                        mediaBrowserListenableFuture.get().setMediaItem(MappingUtil.mapMediaItem(podcastEpisode));
                        mediaBrowserListenableFuture.get().prepare();
                        mediaBrowserListenableFuture.get().play();
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, boolean playImmediatelyAfter) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        if (playImmediatelyAfter) {
                            int insertIndex = getPlayNextInsertIndex(mediaBrowser);
                            enqueueDatabase(media, false, insertIndex);
                            mediaBrowser.addMediaItems(insertIndex, MappingUtil.mapMediaItems(media));
                            addToPlayNextQueue(media);
                        } else {
                            int insertIndex = getComingUpInsertIndex(mediaBrowser);
                            enqueueDatabase(media, false, insertIndex);
                            mediaBrowser.addMediaItems(insertIndex, MappingUtil.mapMediaItems(media));
                            addToComingUpQueue(media);
                        }
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, boolean playImmediatelyAfter) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        if (playImmediatelyAfter) {
                            int insertIndex = getPlayNextInsertIndex(mediaBrowser);
                            enqueueDatabase(media, false, insertIndex);
                            mediaBrowser.addMediaItem(insertIndex, MappingUtil.mapMediaItem(media));
                            addToPlayNextQueue(media);
                        } else {
                            int insertIndex = getComingUpInsertIndex(mediaBrowser);
                            enqueueDatabase(media, false, insertIndex);
                            mediaBrowser.addMediaItem(insertIndex, MappingUtil.mapMediaItem(media));
                            addToComingUpQueue(media);
                        }
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void shuffle(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex, int endIndex) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        mediaBrowserListenableFuture.get().removeMediaItems(startIndex, endIndex + 1);
                        mediaBrowserListenableFuture.get().addMediaItems(MappingUtil.mapMediaItems(media).subList(startIndex, endIndex + 1));
                        swapDatabase(media);
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void swap(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int from, int to) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        mediaBrowserListenableFuture.get().moveMediaItem(from, to);
                        swapDatabase(media);
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void remove(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (mediaBrowserListenableFuture.get().getMediaItemCount() > 1 && mediaBrowserListenableFuture.get().getCurrentMediaItemIndex() != toRemove) {
                            mediaBrowserListenableFuture.get().removeMediaItem(toRemove);
                            removeDatabase(media, toRemove);
                            refreshQueues(mediaBrowserListenableFuture.get());
                        } else {
                            removeDatabase(media, -1);
                        }
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void removeAt(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        int itemCount = mediaBrowser.getMediaItemCount();
                        if (toRemove >= 0 && toRemove < itemCount) {
                            mediaBrowser.removeMediaItem(toRemove);
                        }
                        getQueueRepository().insertAll(media, true, 0);
                        refreshQueues(mediaBrowser);
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void insertAt(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, int index) {
        if (mediaBrowserListenableFuture != null && media != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        int itemCount = mediaBrowser.getMediaItemCount();
                        int safeIndex = Math.max(0, Math.min(index, itemCount));
                        mediaBrowser.addMediaItem(safeIndex, MappingUtil.mapMediaItem(media));
                        enqueueDatabase(media, false, safeIndex);
                        refreshQueues(mediaBrowser);
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void removeRange(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int fromItem, int toItem) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        mediaBrowserListenableFuture.get().removeMediaItems(fromItem, toItem);
                        removeRangeDatabase(media, fromItem, toItem);
                        refreshQueues(mediaBrowserListenableFuture.get());
                        notifyWidgetUpdate();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    private static void addToPlayNextQueue(List<Child> media) {
        if (media == null || media.isEmpty()) return;
        for (Child item : media) {
            addToPlayNextQueue(item);
        }
    }

    private static void addToPlayNextQueue(Child media) {
        if (media == null || media.getId() == null) return;
        PLAY_NEXT_QUEUE.add(media.getId());
    }

    private static void addToComingUpQueue(List<Child> media) {
        if (media == null || media.isEmpty()) return;
        for (Child item : media) {
            addToComingUpQueue(item);
        }
    }

    private static void addToComingUpQueue(Child media) {
        if (media == null || media.getId() == null) return;
        COMING_UP_QUEUE.add(media.getId());
    }

    private static int getPlayNextInsertIndex(MediaBrowser mediaBrowser) {
        if (mediaBrowser == null) return 0;

        int itemCount = mediaBrowser.getMediaItemCount();
        if (itemCount == 0) return 0;

        int currentIndex = mediaBrowser.getCurrentMediaItemIndex();
        if (currentIndex < 0) currentIndex = -1;

        refreshQueues(mediaBrowser);
        int insertIndex = currentIndex + 1 + PLAY_NEXT_QUEUE.size();

        if (insertIndex < 0) insertIndex = 0;
        if (insertIndex > itemCount) insertIndex = itemCount;

        return insertIndex;
    }

    private static int getComingUpInsertIndex(MediaBrowser mediaBrowser) {
        if (mediaBrowser == null) return 0;

        int itemCount = mediaBrowser.getMediaItemCount();
        if (itemCount == 0) return 0;

        int currentIndex = mediaBrowser.getCurrentMediaItemIndex();
        if (currentIndex < 0) currentIndex = -1;

        refreshQueues(mediaBrowser);
        int insertIndex = currentIndex + 1 + PLAY_NEXT_QUEUE.size() + COMING_UP_QUEUE.size();

        if (insertIndex < 0) insertIndex = 0;
        if (insertIndex > itemCount) insertIndex = itemCount;

        return insertIndex;
    }

    private static void refreshQueues(MediaBrowser mediaBrowser) {
        if (mediaBrowser == null) return;
        int currentIndex = mediaBrowser.getCurrentMediaItemIndex();
        refreshQueueList(mediaBrowser, PLAY_NEXT_QUEUE, currentIndex);
        refreshQueueList(mediaBrowser, COMING_UP_QUEUE, currentIndex);
    }

    private static void refreshQueueList(MediaBrowser mediaBrowser, List<String> queue, int currentIndex) {
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

    private static int findMediaItemIndex(MediaBrowser mediaBrowser, String mediaId) {
        if (mediaBrowser == null || mediaId == null) return -1;
        int count = mediaBrowser.getMediaItemCount();
        for (int i = 0; i < count; i++) {
            if (mediaId.equals(mediaBrowser.getMediaItemAt(i).mediaId)) {
                return i;
            }
        }
        return -1;
    }

    public static void getCurrentIndex(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaIndexCallback callback) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        callback.onRecovery(mediaBrowserListenableFuture.get().getCurrentMediaItemIndex());
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void setLastPlayedTimestamp(MediaItem mediaItem) {
        if (mediaItem != null) getQueueRepository().setLastPlayedTimestamp(mediaItem.mediaId);
    }

    public static void setPlayingPausedTimestamp(MediaItem mediaItem, long ms) {
        if (mediaItem != null)
            getQueueRepository().setPlayingPausedTimestamp(mediaItem.mediaId, ms);
    }

    public static void scrobble(MediaItem mediaItem, boolean submission) {
        if (mediaItem != null && Preferences.isScrobblingEnabled()) {
            getSongRepository().scrobble(mediaItem.mediaMetadata.extras.getString("id"), submission);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void continuousPlay(MediaItem mediaItem) {
        if (mediaItem != null && Preferences.isContinuousPlayEnabled() && Preferences.isInstantMixUsable()) {
            Preferences.setLastInstantMix();

            LiveData<List<Child>> instantMix = getSongRepository().getInstantMix(mediaItem.mediaId, 10);
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

    public static void saveChronology(MediaItem mediaItem) {
        if (mediaItem != null) {
            getChronologyRepository().insert(new Chronology(mediaItem));
        }
    }

    private static QueueRepository getQueueRepository() {
        return new QueueRepository();
    }

    private static SongRepository getSongRepository() {
        return new SongRepository();
    }

    private static ChronologyRepository getChronologyRepository() {
        return new ChronologyRepository();
    }

    private static void enqueueDatabase(List<Child> media, boolean reset, int afterIndex) {
        getQueueRepository().insertAll(media, reset, afterIndex);
    }

    private static void enqueueDatabase(Child media, boolean reset, int afterIndex) {
        getQueueRepository().insert(media, reset, afterIndex);
    }

    private static void swapDatabase(List<Child> media) {
        getQueueRepository().insertAll(media, true, 0);
    }

    private static void removeDatabase(List<Child> media, int toRemove) {
        if (toRemove != -1) {
            media.remove(toRemove);
            getQueueRepository().insertAll(media, true, 0);
        }
    }

    private static void removeRangeDatabase(List<Child> media, int fromItem, int toItem) {
        List<Child> toRemove = media.subList(fromItem, toItem);

        media.removeAll(toRemove);

        getQueueRepository().insertAll(media, true, 0);
    }

    public static void clearDatabase() {
        getQueueRepository().deleteAll();
    }

    private static void notifyWidgetUpdate() {
        WidgetUpdateHelper.requestUpdate(App.getContext());
    }
}
