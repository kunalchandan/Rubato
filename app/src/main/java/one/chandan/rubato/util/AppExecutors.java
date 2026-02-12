package one.chandan.rubato.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppExecutors {
    private static final ExecutorService IO = Executors.newCachedThreadPool(new NamedThreadFactory("rubato-io"));
    private static final ExecutorService DOWNLOADS = Executors.newFixedThreadPool(6, new NamedThreadFactory("rubato-download"));
    private static final ExecutorService QUEUE = Executors.newSingleThreadExecutor(new NamedThreadFactory("rubato-queue"));
    private static final ExecutorService LOCAL_MUSIC = Executors.newSingleThreadExecutor(new NamedThreadFactory("rubato-local-music"));
    private static final ExecutorService TELEMETRY = Executors.newSingleThreadExecutor(new NamedThreadFactory("rubato-telemetry"));
    private static final ExecutorService COVER_ART = Executors.newSingleThreadExecutor(new NamedThreadFactory("rubato-cover-art"));
    private static final ExecutorService EXPORT = Executors.newSingleThreadExecutor(new NamedThreadFactory("rubato-export"));

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO;
    }

    public static ExecutorService downloads() {
        return DOWNLOADS;
    }

    public static ExecutorService queue() {
        return QUEUE;
    }

    public static ExecutorService localMusic() {
        return LOCAL_MUSIC;
    }

    public static ExecutorService telemetry() {
        return TELEMETRY;
    }

    public static ExecutorService coverArt() {
        return COVER_ART;
    }

    public static ExecutorService export() {
        return EXPORT;
    }

    public static ExecutorService newSingleThreadExecutor(String prefix) {
        return Executors.newSingleThreadExecutor(new NamedThreadFactory(prefix));
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
