package one.chandan.rubato.automotive;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;

import androidx.media3.common.MediaItem;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Callable;

import one.chandan.rubato.service.MediaService;
import one.chandan.rubato.util.Preferences;
import one.chandan.rubato.App;

@RunWith(AndroidJUnit4.class)
public class AutomotiveMediaBrowserTest {
    private MediaBrowser mediaBrowser;

    @Before
    public void setUp() {
        Preferences.setServer("http://127.0.0.1");
        Preferences.setUser("test");
        Preferences.setPassword("test");
        Preferences.setLowSecurity(true);
        App.refreshSubsonicClient();
    }

    @After
    public void tearDown() {
        if (mediaBrowser != null) {
            Handler browserHandler = new Handler(mediaBrowser.getApplicationLooper());
            try {
                runOnHandler(browserHandler, () -> mediaBrowser.release());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            mediaBrowser = null;
        }
    }

    @Test
    public void libraryRootAndChildrenAccessible() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SessionToken token = new SessionToken(context, new ComponentName(context, MediaService.class));
        ListenableFuture<MediaBrowser> future = new MediaBrowser.Builder(context, token).buildAsync();
        mediaBrowser = future.get(20, TimeUnit.SECONDS);
        Handler browserHandler = new Handler(mediaBrowser.getApplicationLooper());

        LibraryResult<MediaItem> rootResult = callOnHandler(browserHandler, () ->
                mediaBrowser.getLibraryRoot(null)
        ).get(10, TimeUnit.SECONDS);
        assertNotNull(rootResult);
        assertNotNull(rootResult.value);
        assertTrue(rootResult.resultCode == LibraryResult.RESULT_SUCCESS);

        String rootId = rootResult.value.mediaId;
        LibraryResult<ImmutableList<MediaItem>> childrenResult =
                callOnHandler(browserHandler, () -> mediaBrowser.getChildren(rootId, 0, 20, null))
                        .get(10, TimeUnit.SECONDS);
        assertNotNull(childrenResult);
        assertNotNull(childrenResult.value);
        assertTrue(childrenResult.resultCode == LibraryResult.RESULT_SUCCESS);
    }

    @Test
    public void searchReturnsResultsOrEmpty() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SessionToken token = new SessionToken(context, new ComponentName(context, MediaService.class));
        ListenableFuture<MediaBrowser> future = new MediaBrowser.Builder(context, token).buildAsync();
        mediaBrowser = future.get(20, TimeUnit.SECONDS);
        Handler browserHandler = new Handler(mediaBrowser.getApplicationLooper());

        LibraryResult<Void> searchResult = callOnHandler(browserHandler, () ->
                mediaBrowser.search("a", null)
        ).get(10, TimeUnit.SECONDS);
        assertNotNull(searchResult);
        assertTrue(searchResult.resultCode == LibraryResult.RESULT_SUCCESS
                || searchResult.resultCode == LibraryResult.RESULT_ERROR_IO
                || searchResult.resultCode == LibraryResult.RESULT_ERROR_BAD_VALUE);

        LibraryResult<ImmutableList<MediaItem>> results =
                callOnHandler(browserHandler, () -> mediaBrowser.getSearchResult("a", 0, 20, null))
                        .get(10, TimeUnit.SECONDS);
        assertNotNull(results);
        if (results.resultCode == LibraryResult.RESULT_SUCCESS) {
            assertNotNull(results.value);
        }
    }

    private <T> ListenableFuture<T> callOnHandler(Handler handler, Callable<ListenableFuture<T>> action) throws Exception {
        AtomicReference<ListenableFuture<T>> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        handler.post(() -> {
            try {
                result.set(action.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for controller thread");
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    private void runOnHandler(Handler handler, Runnable action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        handler.post(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for controller thread");
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }
}
