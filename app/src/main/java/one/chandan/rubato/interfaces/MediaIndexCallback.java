package one.chandan.rubato.interfaces;

import androidx.annotation.Keep;

@Keep
@FunctionalInterface
public interface MediaIndexCallback {
    void onIndex(int index);

    default void onRecovery(int index) {
        onIndex(index);
    }
}
