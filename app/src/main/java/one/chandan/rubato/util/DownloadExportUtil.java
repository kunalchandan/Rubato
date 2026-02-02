package one.chandan.rubato.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.offline.Download;

import one.chandan.rubato.repository.DownloadRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DownloadExportUtil {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private DownloadExportUtil() {
    }

    public static void exportIfNeeded(Context context, Download download, one.chandan.rubato.model.Download downloadItem) {
        if (context == null || download == null || downloadItem == null) return;
        if (!"shared_music".equals(Preferences.getDownloadExportMode())) return;

        String existing = downloadItem.getDownloadUri();
        if (existing != null && existing.startsWith("content://")) {
            return;
        }

        EXECUTOR.execute(() -> {
            Uri exported = exportToMediaStore(context.getApplicationContext(), download, downloadItem);
            if (exported != null) {
                new DownloadRepository().updateDownloadUri(download.request.id, exported.toString());
            }
        });
    }

    private static Uri exportToMediaStore(Context context, Download download, one.chandan.rubato.model.Download downloadItem) {
        String suffix = downloadItem.getSuffix();
        String mimeType = downloadItem.getContentType();
        String fileName = buildFileName(downloadItem, suffix);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName);
            if (mimeType != null) {
                values.put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mimeType);
            }
            values.put(android.provider.MediaStore.Audio.Media.IS_MUSIC, 1);
            if (downloadItem.getTitle() != null) {
                values.put(android.provider.MediaStore.Audio.Media.TITLE, downloadItem.getTitle());
            }
            if (downloadItem.getArtist() != null) {
                values.put(android.provider.MediaStore.Audio.Media.ARTIST, downloadItem.getArtist());
            }
            if (downloadItem.getAlbum() != null) {
                values.put(android.provider.MediaStore.Audio.Media.ALBUM, downloadItem.getAlbum());
            }
            if (downloadItem.getTrack() != null) {
                values.put(android.provider.MediaStore.Audio.Media.TRACK, downloadItem.getTrack());
            }
            if (downloadItem.getYear() != null) {
                values.put(android.provider.MediaStore.Audio.Media.YEAR, downloadItem.getYear());
            }

            String folder = sanitizeFolder(Preferences.getDownloadExportFolder());
            String relativePath = Environment.DIRECTORY_MUSIC + "/" + folder;
            values.put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, relativePath);
            values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1);

            Uri uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;

            boolean wrote = writeDownloadToUri(context, download, uri);
            values.clear();
            values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);

            return wrote ? uri : null;
        }

        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File targetDir = new File(musicDir, sanitizeFolder(Preferences.getDownloadExportFolder()));
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return null;
        }

        File outFile = new File(targetDir, fileName);
        boolean wrote = writeDownloadToFile(context, download, outFile);
        if (!wrote) return null;

        MediaScannerConnection.scanFile(
                context,
                new String[]{outFile.getAbsolutePath()},
                new String[]{mimeType},
                null
        );

        return Uri.fromFile(outFile);
    }

    private static boolean writeDownloadToUri(Context context, Download download, Uri uri) {
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) return false;
            return copyDownload(context, download, outputStream);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean writeDownloadToFile(Context context, Download download, File file) {
        try (OutputStream outputStream = new FileOutputStream(file)) {
            return copyDownload(context, download, outputStream);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean copyDownload(Context context, Download download, OutputStream outputStream) {
        DataSource dataSource = DownloadUtil.getDataSourceFactory(context).createDataSource();
        DataSpec dataSpec = new DataSpec.Builder()
                .setUri(download.request.uri)
                .setKey(download.request.customCacheKey)
                .build();
        try {
            dataSource.open(dataSpec);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = dataSource.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
                if (read > 0) {
                    outputStream.write(buffer, 0, read);
                }
            }
            outputStream.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                dataSource.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.trim().isEmpty()) return "Rubato";
        return folder.replaceAll("[^a-zA-Z0-9 _-]", "").trim();
    }

    private static String buildFileName(one.chandan.rubato.model.Download download, String suffix) {
        String artist = safe(download.getArtist());
        String title = safe(download.getTitle());
        String name = artist.isEmpty() ? title : artist + " - " + title;
        if (name.isEmpty()) {
            name = "Rubato Track";
        }

        String ext = suffix != null && !suffix.isEmpty() ? suffix : "mp3";
        String normalized = name.replaceAll("[\\/:*?\"<>|]", "_");
        return normalized + "." + ext.toLowerCase(Locale.getDefault());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
