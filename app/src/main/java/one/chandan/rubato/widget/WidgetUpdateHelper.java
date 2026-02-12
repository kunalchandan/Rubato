package one.chandan.rubato.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import one.chandan.rubato.R;
import one.chandan.rubato.repository.ChronologyRepository;
import one.chandan.rubato.repository.QueueRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.model.Chronology;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.service.MediaService;

public final class WidgetUpdateHelper {
    private static final int MAX_RECOMMENDATIONS = 4;

    private WidgetUpdateHelper() {
    }

    public static void requestUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        updateNowPlayingWidgets(context, manager, manager.getAppWidgetIds(new ComponentName(context, NowPlayingWidgetProvider.class)));
        updateCircleWidgets(context, manager, manager.getAppWidgetIds(new ComponentName(context, CircleNowPlayingWidgetProvider.class)));
    }

    public static void updateNowPlayingWidgets(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        for (int appWidgetId : appWidgetIds) {
            updateNowPlayingWidget(context, manager, appWidgetId);
        }
    }

    public static void updateCircleWidgets(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        for (int appWidgetId : appWidgetIds) {
            updateCircleWidget(context, manager, appWidgetId);
        }
    }

    private static void updateNowPlayingWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        WidgetState state = buildFallbackState(context, appWidgetId);
        applyNowPlayingState(context, manager, appWidgetId, state);
        applyControllerState(context, manager, appWidgetId, state, false);
    }

    private static void updateCircleWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        WidgetState state = buildFallbackState(context, appWidgetId);
        applyCircleState(context, manager, appWidgetId, state);
        applyControllerState(context, manager, appWidgetId, state, true);
    }

    private static WidgetState buildFallbackState(Context context, int appWidgetId) {
        WidgetState state = new WidgetState();
        QueueRepository queueRepository = new QueueRepository();
        List<Child> queue = queueRepository.getMedia();
        state.queue = queue != null ? queue : Collections.emptyList();
        state.currentIndex = resolveLastPlayedIndex(queueRepository, state.queue);

        if (state.currentIndex >= 0 && state.currentIndex < state.queue.size()) {
            state.current = state.queue.get(state.currentIndex);
        }

        state.title = state.current != null ? state.current.getTitle() : null;
        state.artist = state.current != null ? state.current.getArtist() : null;
        state.coverArtId = state.current != null ? state.current.getCoverArtId() : null;
        state.isPlaying = false;

        state.recommendationSource = WidgetPreferences.getRecommendationSource(context, appWidgetId);
        state.recommendations = resolveRecommendations(context, state);
        return state;
    }

    private static void applyControllerState(Context context,
                                             AppWidgetManager manager,
                                             int appWidgetId,
                                             WidgetState fallbackState,
                                             boolean circleWidget) {
        ListenableFuture<MediaController> controllerFuture = new MediaController.Builder(
                context,
                new SessionToken(context, new ComponentName(context, MediaService.class))
        ).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                MediaController controller = controllerFuture.get();
                if (controller == null) return;

                MediaItem currentItem = controller.getCurrentMediaItem();
                if (currentItem != null) {
                    WidgetState state = fallbackState.copy();
                    state.isPlaying = controller.isPlaying();
                    state.title = safeText(currentItem.mediaMetadata.title, state.title);
                    state.artist = safeText(currentItem.mediaMetadata.artist, state.artist);
                    state.coverArtId = resolveCoverArtId(currentItem, state.coverArtId);
                    state.currentMediaId = currentItem.mediaId;
                    state.current = state.current != null && state.current.getId().equals(currentItem.mediaId)
                            ? state.current
                            : findInQueue(state.queue, currentItem.mediaId);
                    if (state.current == null) {
                        state.current = buildChildFromMediaItem(currentItem);
                    }
                    state.currentIndex = resolveCurrentIndex(state.queue, currentItem.mediaId, state.currentIndex);
                    state.recommendations = resolveRecommendations(context, state);

                    if (circleWidget) {
                        applyCircleState(context, manager, appWidgetId, state);
                    } else {
                        applyNowPlayingState(context, manager, appWidgetId, state);
                    }
                } else {
                    WidgetState state = fallbackState.copy();
                    state.isPlaying = controller.isPlaying();
                    if (circleWidget) {
                        applyCircleState(context, manager, appWidgetId, state);
                    } else {
                        applyNowPlayingState(context, manager, appWidgetId, state);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                MediaController.releaseFuture(controllerFuture);
            }
        }, MoreExecutors.directExecutor());
    }

    private static void applyNowPlayingState(Context context, AppWidgetManager manager, int appWidgetId, WidgetState state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_now_playing);
        String title = TextUtils.isEmpty(state.title) ? context.getString(R.string.widget_not_playing) : state.title;
        String artist = TextUtils.isEmpty(state.artist) ? context.getString(R.string.widget_open_app) : state.artist;
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_artist, artist);

        views.setImageViewResource(R.id.widget_play_pause, state.isPlaying ? R.drawable.widget_ic_pause : R.drawable.widget_ic_play);

        PendingIntent openAppIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                new Intent(context, one.chandan.rubato.ui.activity.MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent);

        views.setOnClickPendingIntent(R.id.widget_prev, WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_PREVIOUS, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_next, WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_NEXT, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_play_pause, WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE, appWidgetId));

        applyRecommendations(context, views, appWidgetId, state);
        manager.updateAppWidget(appWidgetId, views);

        WidgetImageLoader.loadCover(context, appWidgetId, views, R.id.widget_cover, state.coverArtId, one.chandan.rubato.glide.CustomGlideRequest.ResourceType.Album, false);
    }

    private static void applyCircleState(Context context, AppWidgetManager manager, int appWidgetId, WidgetState state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_now_playing_circle);

        PendingIntent openAppIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                new Intent(context, one.chandan.rubato.ui.activity.MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_circle_root, openAppIntent);

        views.setImageViewResource(R.id.widget_circle_play_pause, state.isPlaying ? R.drawable.widget_ic_pause : R.drawable.widget_ic_play);
        views.setImageViewResource(R.id.widget_circle_favorite, isStarred(state.current) ? R.drawable.widget_ic_favorite : R.drawable.widget_ic_favorites_outlined);

        views.setOnClickPendingIntent(R.id.widget_circle_prev, WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_PREVIOUS, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_circle_next, WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_NEXT, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_circle_play_pause, WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_PLAY_PAUSE, appWidgetId));
        views.setOnClickPendingIntent(R.id.widget_circle_favorite, WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_TOGGLE_FAVORITE, appWidgetId));

        manager.updateAppWidget(appWidgetId, views);
        String coverArtId = state.coverArtId;
        if (TextUtils.isEmpty(coverArtId)) {
            coverArtId = resolveFallbackCover(state);
        }
        if (TextUtils.isEmpty(coverArtId)) {
            views.setImageViewResource(R.id.widget_circle_cover, R.drawable.ic_rubato_logo);
            manager.updateAppWidget(appWidgetId, views);
            return;
        }
        WidgetImageLoader.loadCover(
                context,
                appWidgetId,
                views,
                R.id.widget_circle_cover,
                coverArtId,
                one.chandan.rubato.glide.CustomGlideRequest.ResourceType.Album,
                true,
                R.drawable.ic_rubato_logo
        );
    }

    private static void applyRecommendations(Context context, RemoteViews views, int appWidgetId, WidgetState state) {
        int[] viewIds = new int[]{
                R.id.widget_rec_1,
                R.id.widget_rec_2,
                R.id.widget_rec_3,
                R.id.widget_rec_4
        };

        boolean showRow = state.recommendations != null && !state.recommendations.isEmpty() && !WidgetPreferences.SOURCE_NONE.equals(state.recommendationSource);
        views.setViewVisibility(R.id.widget_recommendations_row, showRow ? View.VISIBLE : View.GONE);

        for (int i = 0; i < viewIds.length; i++) {
            if (!showRow || state.recommendations == null || i >= state.recommendations.size()) {
                views.setViewVisibility(viewIds[i], View.INVISIBLE);
                continue;
            }

            RecommendationItem item = state.recommendations.get(i);
            views.setViewVisibility(viewIds[i], View.VISIBLE);
            if (item.seekIndex >= 0) {
                views.setOnClickPendingIntent(viewIds[i], WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_SEEK, appWidgetId, item.seekIndex, null));
            } else if (item.media != null) {
                views.setOnClickPendingIntent(viewIds[i], WidgetActionReceiver.buildActionPendingIntent(context, WidgetActionReceiver.ACTION_PLAY_MEDIA, appWidgetId, -1, item.media));
            } else {
                views.setOnClickPendingIntent(viewIds[i], PendingIntent.getActivity(
                        context,
                        appWidgetId + i,
                        new Intent(context, one.chandan.rubato.ui.activity.MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                ));
            }
            String coverArtId = item.media != null ? item.media.getCoverArtId() : null;
            WidgetImageLoader.loadCover(context, appWidgetId, views, viewIds[i], coverArtId, one.chandan.rubato.glide.CustomGlideRequest.ResourceType.Album, false);
        }
    }

    private static boolean isStarred(@Nullable Child child) {
        return child != null && child.getStarred() != null;
    }

    private static int resolveLastPlayedIndex(QueueRepository queueRepository, List<Child> queue) {
        if (queue == null || queue.isEmpty()) return -1;
        try {
            int index = queueRepository.getLastPlayedMediaIndex();
            if (index >= 0 && index < queue.size()) {
                return index;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int resolveCurrentIndex(List<Child> queue, String mediaId, int fallback) {
        if (queue == null || queue.isEmpty() || mediaId == null) return fallback;
        for (int i = 0; i < queue.size(); i++) {
            Child child = queue.get(i);
            if (child != null && mediaId.equals(child.getId())) return i;
        }
        return fallback;
    }

    @Nullable
    private static Child findInQueue(List<Child> queue, String mediaId) {
        if (queue == null || mediaId == null) return null;
        for (Child child : queue) {
            if (child != null && mediaId.equals(child.getId())) return child;
        }
        return null;
    }

    private static List<RecommendationItem> resolveRecommendations(Context context, WidgetState state) {
        if (WidgetPreferences.SOURCE_NONE.equals(state.recommendationSource)) {
            return Collections.emptyList();
        }
        if (WidgetPreferences.SOURCE_RECENT.equals(state.recommendationSource)) {
            List<Chronology> recent = new ChronologyRepository().getLastPlayedSimple(Preferences.getServerId(), MAX_RECOMMENDATIONS);
            List<RecommendationItem> items = new ArrayList<>();
            if (recent != null) {
                for (Chronology child : recent) {
                    items.add(new RecommendationItem(child, -1));
                }
            }
            return items;
        }

        List<RecommendationItem> items = new ArrayList<>();
        if (state.queue == null || state.queue.isEmpty()) {
            return items;
        }

        int start = Math.max(state.currentIndex + 1, 0);
        for (int i = start; i < state.queue.size() && items.size() < MAX_RECOMMENDATIONS; i++) {
            Child child = state.queue.get(i);
            if (child == null) continue;
            items.add(new RecommendationItem(child, i));
        }
        return items;
    }

    @Nullable
    private static String resolveFallbackCover(WidgetState state) {
        if (state == null || state.recommendations == null) return null;
        for (RecommendationItem item : state.recommendations) {
            if (item == null || item.media == null) continue;
            String coverArtId = item.media.getCoverArtId();
            if (!TextUtils.isEmpty(coverArtId)) {
                return coverArtId;
            }
        }
        return null;
    }

    @Nullable
    private static String resolveCoverArtId(MediaItem mediaItem, @Nullable String fallback) {
        if (mediaItem == null) return fallback;
        Bundle extras = mediaItem.mediaMetadata.extras;
        if (extras != null) {
            String coverArtId = extras.getString("coverArtId");
            if (!TextUtils.isEmpty(coverArtId)) return coverArtId;
        }
        return fallback;
    }

    private static String safeText(@Nullable CharSequence text, @Nullable String fallback) {
        if (text == null) return fallback;
        String value = text.toString();
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    @Nullable
    private static Child buildChildFromMediaItem(MediaItem mediaItem) {
        if (mediaItem == null) return null;
        Child child = createEmptyChild(mediaItem.mediaId);
        Bundle extras = mediaItem.mediaMetadata.extras;
        if (extras != null) {
            child.setTitle(extras.getString("title"));
            child.setArtist(extras.getString("artist"));
            child.setAlbum(extras.getString("album"));
            child.setCoverArtId(extras.getString("coverArtId"));
            long starred = extras.getLong("starred");
            if (starred > 0) {
                child.setStarred(new java.util.Date(starred));
            }
        }
        return child;
    }

    private static Child createEmptyChild(String id) {
        return new Child(
                id,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static final class RecommendationItem {
        private final Child media;
        private final int seekIndex;

        private RecommendationItem(Child media, int seekIndex) {
            this.media = media;
            this.seekIndex = seekIndex;
        }
    }

    private static final class WidgetState {
        private List<Child> queue = Collections.emptyList();
        private Child current;
        private int currentIndex = -1;
        private String currentMediaId;
        private String title;
        private String artist;
        private String coverArtId;
        private boolean isPlaying;
        private String recommendationSource = WidgetPreferences.SOURCE_QUEUE;
        private List<RecommendationItem> recommendations = Collections.emptyList();

        private WidgetState copy() {
            WidgetState copy = new WidgetState();
            copy.queue = this.queue;
            copy.current = this.current;
            copy.currentIndex = this.currentIndex;
            copy.currentMediaId = this.currentMediaId;
            copy.title = this.title;
            copy.artist = this.artist;
            copy.coverArtId = this.coverArtId;
            copy.isPlaying = this.isPlaying;
            copy.recommendationSource = this.recommendationSource;
            copy.recommendations = this.recommendations;
            return copy;
        }
    }
}
