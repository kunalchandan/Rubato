package one.chandan.rubato.service;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;

import one.chandan.rubato.interfaces.MediaIndexCallback;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.subsonic.models.InternetRadioStation;
import one.chandan.rubato.subsonic.models.PodcastEpisode;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

public class MediaManager {
    private static final QueueManager QUEUE_MANAGER = new QueueManager();

    public static void reset(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        QUEUE_MANAGER.reset(mediaBrowserListenableFuture);
    }

    public static void hide(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        QUEUE_MANAGER.hide(mediaBrowserListenableFuture);
    }

    public static void check(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        QUEUE_MANAGER.check(mediaBrowserListenableFuture);
    }

    public static void init(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media) {
        QUEUE_MANAGER.init(mediaBrowserListenableFuture, media);
    }

    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex) {
        QUEUE_MANAGER.startQueue(mediaBrowserListenableFuture, media, startIndex);
    }

    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media) {
        QUEUE_MANAGER.startQueue(mediaBrowserListenableFuture, media);
    }

    public static void startRadio(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, InternetRadioStation internetRadioStation) {
        QUEUE_MANAGER.startRadio(mediaBrowserListenableFuture, internetRadioStation);
    }

    public static void startPodcast(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, PodcastEpisode podcastEpisode) {
        QUEUE_MANAGER.startPodcast(mediaBrowserListenableFuture, podcastEpisode);
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, boolean playImmediatelyAfter) {
        QUEUE_MANAGER.enqueue(mediaBrowserListenableFuture, media, playImmediatelyAfter);
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, boolean playImmediatelyAfter) {
        QUEUE_MANAGER.enqueue(mediaBrowserListenableFuture, media, playImmediatelyAfter);
    }

    public static void shuffle(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex, int endIndex) {
        QUEUE_MANAGER.shuffle(mediaBrowserListenableFuture, media, startIndex, endIndex);
    }

    public static void swap(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int from, int to) {
        QUEUE_MANAGER.swap(mediaBrowserListenableFuture, media, from, to);
    }

    public static void remove(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        QUEUE_MANAGER.remove(mediaBrowserListenableFuture, media, toRemove);
    }

    public static void removeAt(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        QUEUE_MANAGER.removeAt(mediaBrowserListenableFuture, media, toRemove);
    }

    public static void insertAt(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, int index) {
        QUEUE_MANAGER.insertAt(mediaBrowserListenableFuture, media, index);
    }

    public static void removeRange(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int fromItem, int toItem) {
        QUEUE_MANAGER.removeRange(mediaBrowserListenableFuture, media, fromItem, toItem);
    }

    public static void getCurrentIndex(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaIndexCallback callback) {
        QUEUE_MANAGER.getCurrentIndex(mediaBrowserListenableFuture, callback);
    }

    public static void setLastPlayedTimestamp(MediaItem mediaItem) {
        QUEUE_MANAGER.setLastPlayedTimestamp(mediaItem);
    }

    public static void setPlayingPausedTimestamp(MediaItem mediaItem, long ms) {
        QUEUE_MANAGER.setPlayingPausedTimestamp(mediaItem, ms);
    }

    public static void scrobble(MediaItem mediaItem, boolean submission) {
        QUEUE_MANAGER.scrobble(mediaItem, submission);
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void continuousPlay(MediaItem mediaItem) {
        QUEUE_MANAGER.continuousPlay(mediaItem);
    }

    public static void saveChronology(MediaItem mediaItem) {
        QUEUE_MANAGER.saveChronology(mediaItem);
    }

    public static void clearDatabase() {
        QUEUE_MANAGER.clearDatabase();
    }
}
