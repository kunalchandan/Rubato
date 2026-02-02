package one.chandan.rubato.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerStatus {
    private static final AtomicBoolean reachable = new AtomicBoolean(true);
    private static final MutableLiveData<Boolean> reachableLive = new MutableLiveData<>(true);

    private ServerStatus() {
    }

    public static boolean isReachable() {
        return reachable.get();
    }

    public static LiveData<Boolean> getReachableLive() {
        return reachableLive;
    }

    public static void markReachable() {
        update(true);
    }

    public static void markUnreachable() {
        update(false);
    }

    private static void update(boolean next) {
        if (reachable.getAndSet(next) != next) {
            reachableLive.postValue(next);
        } else {
            reachableLive.postValue(next);
        }
    }
}
