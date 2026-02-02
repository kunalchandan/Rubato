package one.chandan.rubato.service;

import android.media.audiofx.Visualizer;

import androidx.annotation.Nullable;
import androidx.media3.common.C;

public class VisualizerManager {
    public interface Listener {
        void onWaveformData(byte[] waveform, int samplingRate);
        void onError(Exception exception);
    }

    private static final int DEFAULT_CAPTURE_SIZE = 256;

    private Visualizer visualizer;
    private Listener listener;
    private int audioSessionId = C.AUDIO_SESSION_ID_UNSET;
    private int captureSize = DEFAULT_CAPTURE_SIZE;
    private int targetFps = 45;
    private boolean enabled = false;

    private final Visualizer.OnDataCaptureListener captureListener = new Visualizer.OnDataCaptureListener() {
        @Override
        public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
            if (listener != null && waveform != null) {
                listener.onWaveformData(waveform, samplingRate);
            }
        }

        @Override
        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            // Not used.
        }
    };

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setAudioSessionId(int audioSessionId) {
        if (this.audioSessionId == audioSessionId) return;
        this.audioSessionId = audioSessionId;
        if (enabled) recreate();
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            recreate();
        } else {
            release();
        }
    }

    public void setCaptureSize(int captureSize) {
        if (captureSize <= 0) return;
        this.captureSize = captureSize;
        if (visualizer != null) applyCaptureSize();
    }

    public void setTargetFps(int targetFps) {
        if (targetFps <= 0) return;
        this.targetFps = targetFps;
        if (visualizer != null) applyCaptureRate();
    }

    public boolean isActive() {
        return visualizer != null;
    }

    public void release() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
            } catch (Exception ignored) {
                // Ignore disable errors.
            }
            try {
                visualizer.release();
            } catch (Exception ignored) {
                // Ignore release errors.
            }
            visualizer = null;
        }
    }

    private void recreate() {
        release();
        if (!enabled) return;
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return;

        try {
            visualizer = new Visualizer(audioSessionId);
            visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
            applyCaptureSize();
            applyCaptureRate();
            visualizer.setEnabled(true);
        } catch (Exception exception) {
            release();
            if (listener != null) {
                listener.onError(exception);
            }
        }
    }

    private void applyCaptureSize() {
        if (visualizer == null) return;
        int[] range = Visualizer.getCaptureSizeRange();
        int min = range[0];
        int max = range[1];
        int target = Math.max(min, Math.min(max, captureSize));
        if (target % 2 != 0) {
            target -= 1;
        }
        if (target < min) target = min;
        if (target > max) target = max;

        try {
            visualizer.setCaptureSize(target);
        } catch (Exception exception) {
            if (listener != null) {
                listener.onError(exception);
            }
        }
    }

    private void applyCaptureRate() {
        if (visualizer == null) return;
        int maxRate = Visualizer.getMaxCaptureRate();
        int requestedRate = Math.max(1000, targetFps * 1000);
        int rate = Math.min(maxRate, requestedRate);
        try {
            visualizer.setDataCaptureListener(captureListener, rate, true, false);
        } catch (Exception exception) {
            if (listener != null) {
                listener.onError(exception);
            }
        }
    }
}
