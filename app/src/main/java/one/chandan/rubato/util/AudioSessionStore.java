package one.chandan.rubato.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.C;

public final class AudioSessionStore {
    private static final MutableLiveData<Integer> audioSessionId = new MutableLiveData<>(C.AUDIO_SESSION_ID_UNSET);

    private AudioSessionStore() {
    }

    public static LiveData<Integer> getAudioSessionId() {
        return audioSessionId;
    }

    public static int getAudioSessionIdValue() {
        Integer value = audioSessionId.getValue();
        return value != null ? value : C.AUDIO_SESSION_ID_UNSET;
    }

    public static void updateAudioSessionId(int value) {
        audioSessionId.postValue(value);
    }
}
