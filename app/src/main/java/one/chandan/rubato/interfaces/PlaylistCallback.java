package one.chandan.rubato.interfaces;

import androidx.annotation.Keep;

@Keep
public interface PlaylistCallback {
    default void onDismiss() {}
}
