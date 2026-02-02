package one.chandan.rubato.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.NotificationUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.media3.exoplayer.scheduler.Scheduler;

import one.chandan.rubato.R;
import one.chandan.rubato.repository.DownloadRepository;
import one.chandan.rubato.ui.activity.MainActivity;
import one.chandan.rubato.util.Constants;
import one.chandan.rubato.util.DownloadExportUtil;
import one.chandan.rubato.util.DownloadUtil;

import java.util.List;

@UnstableApi
public class DownloaderService extends androidx.media3.exoplayer.offline.DownloadService {

    private static final int JOB_ID = 1;
    private static final int FOREGROUND_NOTIFICATION_ID = 1;

    public DownloaderService() {
        super(FOREGROUND_NOTIFICATION_ID, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID, R.string.exo_download_notification_channel_name, 0);
    }

    @NonNull
    @Override
    protected DownloadManager getDownloadManager() {
        DownloadManager downloadManager = DownloadUtil.getDownloadManager(this);
        DownloadNotificationHelper downloadNotificationHelper = DownloadUtil.getDownloadNotificationHelper(this);
        downloadManager.addListener(new TerminalStateNotificationHelper(this, downloadNotificationHelper, FOREGROUND_NOTIFICATION_ID + 1));
        return downloadManager;
    }

    @NonNull
    @Override
    protected Scheduler getScheduler() {
        return new PlatformScheduler(this, JOB_ID);
    }

    @NonNull
    @Override
    protected Notification getForegroundNotification(@NonNull List<Download> downloads, @Requirements.RequirementFlags int notMetRequirements) {
        String message = null;
        PendingIntent contentIntent = null;
        if (!downloads.isEmpty()) {
            Download activeDownload = null;
            Download restartingDownload = null;
            Download queuedDownload = null;
            int completedCount = 0;
            int queuedCount = 0;
            int downloadingCount = 0;
            int restartingCount = 0;
            long activeUpdate = -1;
            long restartingUpdate = -1;
            for (Download download : downloads) {
                switch (download.state) {
                    case Download.STATE_DOWNLOADING:
                        long update = resolveUpdateTime(download);
                        if (activeDownload == null || update > activeUpdate) {
                            activeDownload = download;
                            activeUpdate = update;
                        }
                        downloadingCount++;
                        break;
                    case Download.STATE_RESTARTING:
                        long restartingUpdateTime = resolveUpdateTime(download);
                        if (restartingDownload == null || restartingUpdateTime > restartingUpdate) {
                            restartingDownload = download;
                            restartingUpdate = restartingUpdateTime;
                        }
                        restartingCount++;
                        break;
                    case Download.STATE_QUEUED:
                        if (queuedDownload == null) {
                            queuedDownload = download;
                        }
                        queuedCount++;
                        break;
                    case Download.STATE_COMPLETED:
                        completedCount++;
                        break;
                    default:
                        break;
                }
            }

            if (activeDownload == null) {
                activeDownload = restartingDownload != null ? restartingDownload : queuedDownload;
            }

            if (activeDownload != null) {
                one.chandan.rubato.model.Download downloadItem = getDownloadItem(activeDownload.request.id);
                String title = downloadItem != null ? downloadItem.getTitle() : DownloaderManager.getDownloadNotificationMessage(activeDownload.request.id);
                String artist = downloadItem != null ? downloadItem.getArtist() : null;
                String label = buildDownloadLabel(title, artist);
                if (label != null && !label.isEmpty()) {
                    int total = completedCount + queuedCount + downloadingCount + restartingCount;
                    int activeCount = downloadingCount + restartingCount;
                    int current = completedCount + (activeCount > 0 ? activeCount : (queuedCount > 0 ? 1 : 0));
                    if (total <= 1) {
                        message = getString(R.string.download_notification_in_progress_single, label);
                    } else {
                        message = getString(R.string.download_notification_in_progress_multi, label, current, total);
                    }
                }
                contentIntent = buildDownloadContentIntent(activeDownload.request.id);
            }

            if (message == null) {
                message = getString(R.string.download_notification_in_progress_generic, downloads.size());
            }
        }

        return DownloadUtil.getDownloadNotificationHelper(this).buildProgressNotification(this, R.drawable.ic_download, contentIntent, message, downloads, notMetRequirements);
    }

    private PendingIntent buildDownloadContentIntent(String downloadId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(Constants.DOWNLOAD_NOTIFICATION_ID, downloadId);

        return TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(downloadId.hashCode(), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static one.chandan.rubato.model.Download getDownloadItem(String downloadId) {
        return new DownloadRepository().getDownload(downloadId);
    }

    @Nullable
    private static String buildDownloadLabel(@Nullable String title, @Nullable String artist) {
        if (title == null || title.isEmpty()) return null;
        if (artist == null || artist.isEmpty()) return title;
        return title + " - " + artist;
    }

    private static long resolveUpdateTime(@NonNull Download download) {
        long updateTime = download.updateTimeMs;
        if (updateTime > 0) return updateTime;
        return download.startTimeMs;
    }

    @Nullable
    private static String buildExpandedText(@Nullable one.chandan.rubato.model.Download download) {
        if (download == null) return null;

        String artist = download.getArtist();
        String album = download.getAlbum();

        if (artist != null && !artist.isEmpty() && album != null && !album.isEmpty()) {
            return artist + " - " + album;
        }

        if (artist != null && !artist.isEmpty()) {
            return artist;
        }

        if (album != null && !album.isEmpty()) {
            return album;
        }

        return null;
    }

    private static final class TerminalStateNotificationHelper implements DownloadManager.Listener {
        private final Context context;
        private final DownloadNotificationHelper notificationHelper;

        private final Notification successfulDownloadGroupNotification;
        private final Notification failedDownloadGroupNotification;

        private final int successfulDownloadGroupNotificationId;
        private final int failedDownloadGroupNotificationId;

        private int nextNotificationId;

        public TerminalStateNotificationHelper(Context context, DownloadNotificationHelper notificationHelper, int firstNotificationId) {
            this.context = context.getApplicationContext();
            this.notificationHelper = notificationHelper;
            nextNotificationId = firstNotificationId;

            successfulDownloadGroupNotification = DownloadUtil.buildGroupSummaryNotification(
                    this.context,
                    DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                    DownloadUtil.DOWNLOAD_NOTIFICATION_SUCCESSFUL_GROUP,
                    R.drawable.ic_check_circle,
                    "Downloads completed"
            );

            failedDownloadGroupNotification = DownloadUtil.buildGroupSummaryNotification(
                    this.context,
                    DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                    DownloadUtil.DOWNLOAD_NOTIFICATION_FAILED_GROUP,
                    R.drawable.ic_error,
                    "Downloads failed"
            );

            successfulDownloadGroupNotificationId = nextNotificationId++;
            failedDownloadGroupNotificationId = nextNotificationId++;
        }

        @Override
        public void onDownloadChanged(@NonNull DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
            Notification notification;
            String itemTitle = DownloaderManager.getDownloadNotificationMessage(download.request.id);
            one.chandan.rubato.model.Download downloadItem = getDownloadItem(download.request.id);
            String expandedText = buildExpandedText(downloadItem);
            PendingIntent contentIntent = buildDownloadContentIntent(context, download.request.id);

            if (download.state == Download.STATE_COMPLETED) {
                notification = notificationHelper.buildDownloadCompletedNotification(context, R.drawable.ic_check_circle, null, itemTitle);
                Notification.Builder builder = Notification.Builder.recoverBuilder(context, notification);
                if (itemTitle != null && !itemTitle.isEmpty()) {
                    builder.setContentTitle(itemTitle);
                }
                if (expandedText != null) {
                    builder.setContentText(expandedText)
                            .setStyle(new Notification.BigTextStyle().bigText(expandedText));
                }
                builder.setContentIntent(contentIntent);
                notification = builder.setGroup(DownloadUtil.DOWNLOAD_NOTIFICATION_SUCCESSFUL_GROUP).build();
                NotificationUtil.setNotification(this.context, successfulDownloadGroupNotificationId, successfulDownloadGroupNotification);
                DownloaderManager.updateRequestDownload(download);
                DownloadExportUtil.exportIfNeeded(context, download, downloadItem);
            } else if (download.state == Download.STATE_FAILED) {
                notification = notificationHelper.buildDownloadFailedNotification(context, R.drawable.ic_error, null, itemTitle);
                Notification.Builder builder = Notification.Builder.recoverBuilder(context, notification);
                if (itemTitle != null && !itemTitle.isEmpty()) {
                    builder.setContentTitle(itemTitle);
                }
                if (expandedText != null) {
                    builder.setContentText(expandedText)
                            .setStyle(new Notification.BigTextStyle().bigText(expandedText));
                }
                builder.setContentIntent(contentIntent);
                notification = builder.setGroup(DownloadUtil.DOWNLOAD_NOTIFICATION_FAILED_GROUP).build();
                NotificationUtil.setNotification(this.context, failedDownloadGroupNotificationId, failedDownloadGroupNotification);
            } else {
                return;
            }

            NotificationUtil.setNotification(context, nextNotificationId++, notification);
        }

        @Override
        public void onDownloadRemoved(@NonNull DownloadManager downloadManager, Download download) {
            DownloaderManager.removeRequestDownload(download);
        }

        private PendingIntent buildDownloadContentIntent(Context context, String downloadId) {
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra(Constants.DOWNLOAD_NOTIFICATION_ID, downloadId);

            return TaskStackBuilder.create(context)
                    .addNextIntentWithParentStack(intent)
                    .getPendingIntent(downloadId.hashCode(), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }
}
