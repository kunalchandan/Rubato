package one.chandan.rubato;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.Manifest;
import android.app.UiAutomation;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import one.chandan.rubato.database.AppDatabase;
import one.chandan.rubato.model.LocalSource;
import one.chandan.rubato.repository.LocalMusicRepository;
import one.chandan.rubato.subsonic.models.Child;
import one.chandan.rubato.util.AppExecutors;
import one.chandan.rubato.util.LocalMusicPermissions;
import one.chandan.rubato.util.Preferences;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LocalSourceIntegrationTest {
    private String insertedSourceId;

    @After
    public void cleanup() throws Exception {
        if (insertedSourceId == null) {
            return;
        }
        CountDownLatch deleteLatch = new CountDownLatch(1);
        AppExecutors.io().execute(() -> {
            AppDatabase.getInstance().localSourceDao().deleteById(insertedSourceId);
            deleteLatch.countDown();
        });
        deleteLatch.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void localSourceInMusicFolderSurfacesLocalSong() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Preferences.setLocalMusicEnabled(true);
        ensureReadPermission();
        Assume.assumeTrue("Local music permission missing.", LocalMusicPermissions.hasReadPermission(context));

        ContentResolver resolver = context.getContentResolver();
        String[] projection;
        String selection;
        String[] args;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.RELATIVE_PATH
            };
            selection = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
            args = new String[]{"Music/%"};
        } else {
            projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DATA
            };
            selection = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DATA + " LIKE ?";
            args = new String[]{"%/Music/%"};
        }

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        long mediaId;
        String relativePath;
        try (Cursor cursor = resolver.query(uri, projection, selection, args, null)) {
            boolean hasSong = cursor != null && cursor.moveToFirst();
            Assume.assumeTrue("No test song found in Music folder on device.", hasSong);
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            mediaId = cursor.getLong(idCol);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int relCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);
                relativePath = cursor.getString(relCol);
            } else {
                int dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                String data = cursor.getString(dataCol);
                relativePath = extractRelativePathFromData(data);
            }
        }

        String normalized = normalizeRelativePath(relativePath);
        if (normalized == null) {
            normalized = "Music/";
        }

        insertedSourceId = "test:" + normalized + System.currentTimeMillis();
        LocalSource source = new LocalSource(
                insertedSourceId,
                insertedSourceId,
                "Test Music",
                normalized,
                null,
                System.currentTimeMillis()
        );

        CountDownLatch insertLatch = new CountDownLatch(1);
        AppExecutors.io().execute(() -> {
            AppDatabase.getInstance().localSourceDao().insert(source);
            insertLatch.countDown();
        });
        assertTrue("LocalSource insert timed out.", insertLatch.await(5, TimeUnit.SECONDS));

        LocalMusicRepository.invalidateCache();

        CountDownLatch loadLatch = new CountDownLatch(1);
        AtomicReference<LocalMusicRepository.LocalLibrary> ref = new AtomicReference<>();
        LocalMusicRepository.loadLibrary(context, library -> {
            ref.set(library);
            loadLatch.countDown();
        });

        assertTrue("Local library load timed out.", loadLatch.await(15, TimeUnit.SECONDS));
        LocalMusicRepository.LocalLibrary library = ref.get();
        assertNotNull("Local library was null.", library);

        String expectedId = LocalMusicRepository.LOCAL_SONG_PREFIX + mediaId;
        boolean found = false;
        for (Child song : library.songs) {
            if (song != null && expectedId.equals(song.getId())) {
                found = true;
                break;
            }
        }
        assertTrue("Local library did not include expected song id=" + expectedId + " relativePath=" + normalized, found);
    }

    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null) return null;
        String normalized = relativePath.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static String extractRelativePathFromData(String data) {
        if (data == null) return null;
        String normalized = data.replace("\\", "/");
        int idx = normalized.lastIndexOf("/Music/");
        if (idx < 0) return null;
        String rel = normalized.substring(idx + 1);
        int lastSlash = rel.lastIndexOf('/');
        if (lastSlash < 0) return null;
        return rel.substring(0, lastSlash + 1);
    }

    private static void ensureReadPermission() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (LocalMusicPermissions.hasReadPermission(context)) {
            return;
        }
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        if (automation != null && context.getPackageName() != null) {
            automation.grantRuntimePermission(context.getPackageName(), permission);
        }
    }
}
