package one.chandan.rubato.widget;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import one.chandan.rubato.service.MediaManager;
import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.FavoriteUtil;

public class WidgetActionReceiver extends BroadcastReceiver {
    public static final String ACTION_PLAY_PAUSE = "one.chandan.rubato.widget.PLAY_PAUSE";
    public static final String ACTION_NEXT = "one.chandan.rubato.widget.NEXT";
    public static final String ACTION_PREVIOUS = "one.chandan.rubato.widget.PREVIOUS";
    public static final String ACTION_TOGGLE_FAVORITE = "one.chandan.rubato.widget.TOGGLE_FAVORITE";
    public static final String ACTION_SEEK = "one.chandan.rubato.widget.SEEK";
    public static final String ACTION_PLAY_MEDIA = "one.chandan.rubato.widget.PLAY_MEDIA";

    public static final String EXTRA_WIDGET_ID = "extra_widget_id";
    public static final String EXTRA_SEEK_INDEX = "extra_seek_index";
    public static final String EXTRA_MEDIA = "extra_media";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if (ACTION_PLAY_MEDIA.equals(action)) {
            Child media = intent.getParcelableExtra(EXTRA_MEDIA);
            if (media == null) return;
            ListenableFuture<MediaBrowser> browserFuture = new MediaBrowser.Builder(
                    context,
                    new SessionToken(context, new ComponentName(context, MediaService.class))
            ).buildAsync();
            MediaManager.startQueue(browserFuture, media);
            WidgetUpdateHelper.requestUpdate(context);
            return;
        }

        ListenableFuture<MediaController> controllerFuture = new MediaController.Builder(
                context,
                new SessionToken(context, new ComponentName(context, MediaService.class))
        ).buildAsync();

        controllerFuture.addListener(() -> {
            try {
                MediaController controller = controllerFuture.get();
                if (controller == null) return;

                switch (action) {
                    case ACTION_PLAY_PAUSE:
                        if (controller.isPlaying()) {
                            controller.pause();
                        } else {
                            controller.play();
                        }
                        break;
                    case ACTION_NEXT:
                        controller.seekToNext();
                        break;
                    case ACTION_PREVIOUS:
                        controller.seekToPrevious();
                        break;
                    case ACTION_SEEK:
                        int index = intent.getIntExtra(EXTRA_SEEK_INDEX, -1);
                        if (index >= 0) {
                            controller.seekTo(index, 0);
                            controller.play();
                        }
                        break;
                    case ACTION_TOGGLE_FAVORITE:
                        toggleFavorite(context, controller);
                        break;
                    default:
                        break;
                }
            } catch (Exception ignored) {
            } finally {
                MediaController.releaseFuture(controllerFuture);
                WidgetUpdateHelper.requestUpdate(context);
            }
        }, MoreExecutors.directExecutor());
    }

    private void toggleFavorite(Context context, MediaController controller) {
        MediaItem mediaItem = controller.getCurrentMediaItem();
        if (mediaItem == null) return;
        Child child = buildChildFromMediaItem(mediaItem);
        if (child == null) return;
        FavoriteUtil.toggleFavorite(context, child);
    }

    @Nullable
    private Child buildChildFromMediaItem(MediaItem mediaItem) {
        if (mediaItem == null) return null;
        Child child = createEmptyChild(mediaItem.mediaId);
        Bundle extras = mediaItem.mediaMetadata.extras;
        if (extras != null) {
            child.setParentId(extras.getString("parentId"));
            child.setDir(extras.getBoolean("isDir"));
            child.setTitle(extras.getString("title"));
            child.setAlbum(extras.getString("album"));
            child.setArtist(extras.getString("artist"));
            child.setTrack(extras.getInt("track"));
            child.setYear(extras.getInt("year"));
            child.setGenre(extras.getString("genre"));
            child.setCoverArtId(extras.getString("coverArtId"));
            child.setSize(extras.getLong("size"));
            child.setContentType(extras.getString("contentType"));
            child.setSuffix(extras.getString("suffix"));
            child.setTranscodedContentType(extras.getString("transcodedContentType"));
            child.setTranscodedSuffix(extras.getString("transcodedSuffix"));
            child.setDuration(extras.getInt("duration"));
            child.setBitrate(extras.getInt("bitrate"));
            child.setPath(extras.getString("path"));
            child.setVideo(extras.getBoolean("isVideo"));
            child.setUserRating(extras.getInt("userRating"));
            child.setAverageRating(extras.getDouble("averageRating"));
            child.setPlayCount(extras.getLong("playCount"));
            child.setDiscNumber(extras.getInt("discNumber"));
            child.setAlbumId(extras.getString("albumId"));
            child.setArtistId(extras.getString("artistId"));
            child.setType(extras.getString("type"));
            child.setBookmarkPosition(extras.getLong("bookmarkPosition"));
            child.setOriginalWidth(extras.getInt("originalWidth"));
            child.setOriginalHeight(extras.getInt("originalHeight"));
            long starred = extras.getLong("starred");
            if (starred > 0) {
                child.setStarred(new java.util.Date(starred));
            }
        }
        return child;
    }

    private Child createEmptyChild(String id) {
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

    public static PendingIntent buildActionPendingIntent(Context context, String action, int widgetId) {
        return buildActionPendingIntent(context, action, widgetId, -1, null);
    }

    public static PendingIntent buildActionPendingIntent(Context context,
                                                         String action,
                                                         int widgetId,
                                                         int seekIndex,
                                                         @Nullable Child media) {
        Intent intent = new Intent(context, WidgetActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_WIDGET_ID, widgetId);
        if (seekIndex >= 0) {
            intent.putExtra(EXTRA_SEEK_INDEX, seekIndex);
        }
        if (media != null) {
            intent.putExtra(EXTRA_MEDIA, media);
        }
        int requestCode = buildRequestCode(action, widgetId, seekIndex);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int buildRequestCode(String action, int widgetId, int seekIndex) {
        int actionCode;
        switch (action) {
            case ACTION_PLAY_PAUSE:
                actionCode = 1;
                break;
            case ACTION_NEXT:
                actionCode = 2;
                break;
            case ACTION_PREVIOUS:
                actionCode = 3;
                break;
            case ACTION_TOGGLE_FAVORITE:
                actionCode = 4;
                break;
            case ACTION_SEEK:
                actionCode = 5;
                break;
            case ACTION_PLAY_MEDIA:
                actionCode = 6;
                break;
            default:
                actionCode = 9;
                break;
        }
        int indexPart = seekIndex >= 0 ? (seekIndex + 1) : 0;
        return Math.abs(widgetId * 100 + actionCode * 10 + indexPart);
    }
}
