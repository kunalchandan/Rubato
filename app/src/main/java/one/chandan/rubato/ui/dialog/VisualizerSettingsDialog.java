package one.chandan.rubato.ui.dialog;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import one.chandan.rubato.R;
import one.chandan.rubato.ui.view.VisualizerView;
import one.chandan.rubato.util.Preferences;

import java.util.Random;

public class VisualizerSettingsDialog extends BottomSheetDialogFragment {
    public static final String RESULT_KEY = "visualizer_settings_result";

    private TextView barCountLabel;
    private TextView opacityLabel;
    private TextView smoothingLabel;
    private TextView scaleLabel;
    private TextView fpsLabel;
    private VisualizerView previewView;
    private SwitchMaterial enabledSwitch;

    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final Random previewRandom = new Random();
    private final byte[] previewWaveform = new byte[128];
    private boolean previewRunning = false;
    private float previewPhase = 0f;
    private long previewIntervalMs = 33L;
    private final Runnable previewRunnable = new Runnable() {
        @Override
        public void run() {
            if (!previewRunning || previewView == null) return;
            buildPreviewWaveform();
            previewView.setWaveform(previewWaveform);
            previewHandler.postDelayed(this, previewIntervalMs);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_visualizer_settings, container, false);

        enabledSwitch = view.findViewById(R.id.visualizer_enable_switch);
        SwitchMaterial peakCapsSwitch = view.findViewById(R.id.visualizer_peak_caps_switch);

        barCountLabel = view.findViewById(R.id.visualizer_bar_count_label);
        opacityLabel = view.findViewById(R.id.visualizer_opacity_label);
        smoothingLabel = view.findViewById(R.id.visualizer_smoothing_label);
        scaleLabel = view.findViewById(R.id.visualizer_scale_label);
        fpsLabel = view.findViewById(R.id.visualizer_fps_label);
        previewView = view.findViewById(R.id.visualizer_preview);

        Slider barCountSlider = view.findViewById(R.id.visualizer_bar_count_slider);
        Slider opacitySlider = view.findViewById(R.id.visualizer_opacity_slider);
        Slider smoothingSlider = view.findViewById(R.id.visualizer_smoothing_slider);
        Slider scaleSlider = view.findViewById(R.id.visualizer_scale_slider);
        Slider fpsSlider = view.findViewById(R.id.visualizer_fps_slider);

        ChipGroup styleGroup = view.findViewById(R.id.visualizer_style_group);
        Chip barsChip = view.findViewById(R.id.visualizer_style_bars_chip);
        Chip lineChip = view.findViewById(R.id.visualizer_style_line_chip);
        Chip dotsChip = view.findViewById(R.id.visualizer_style_dots_chip);

        ChipGroup colorGroup = view.findViewById(R.id.visualizer_color_mode_group);
        Chip accentChip = view.findViewById(R.id.visualizer_color_accent_chip);
        Chip gradientChip = view.findViewById(R.id.visualizer_color_gradient_chip);

        enabledSwitch.setChecked(Preferences.isVisualizerEnabled());
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Preferences.setVisualizerEnabled(isChecked);
            setPreviewEnabled(isChecked);
            onSettingChanged();
        });

        peakCapsSwitch.setChecked(Preferences.isVisualizerPeakCapsEnabled());
        peakCapsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Preferences.setVisualizerPeakCapsEnabled(isChecked);
            onSettingChanged();
        });

        String styleMode = Preferences.getVisualizerMode();
        if ("line".equals(styleMode)) {
            lineChip.setChecked(true);
        } else if ("dots".equals(styleMode)) {
            dotsChip.setChecked(true);
        } else {
            barsChip.setChecked(true);
        }
        updatePeakCapsAvailability(peakCapsSwitch, styleMode);

        styleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selected = "bars";
            if (checkedId == R.id.visualizer_style_line_chip) {
                selected = "line";
            } else if (checkedId == R.id.visualizer_style_dots_chip) {
                selected = "dots";
            }
            Preferences.setVisualizerMode(selected);
            updatePeakCapsAvailability(peakCapsSwitch, selected);
            onSettingChanged();
        });

        barCountSlider.setValueFrom(24f);
        barCountSlider.setValueTo(96f);
        barCountSlider.setStepSize(4f);
        barCountSlider.setValue(Preferences.getVisualizerBarCount());
        barCountSlider.addOnChangeListener((slider, value, fromUser) -> {
            int barCount = Math.round(value);
            Preferences.setVisualizerBarCount(barCount);
            updateLabels();
            onSettingChanged();
        });

        opacitySlider.setValueFrom(0.2f);
        opacitySlider.setValueTo(0.8f);
        opacitySlider.setStepSize(0.05f);
        opacitySlider.setValue(Preferences.getVisualizerOpacity());
        opacitySlider.addOnChangeListener((slider, value, fromUser) -> {
            Preferences.setVisualizerOpacity(value);
            updateLabels();
            onSettingChanged();
        });

        smoothingSlider.setValueFrom(0.1f);
        smoothingSlider.setValueTo(0.9f);
        smoothingSlider.setStepSize(0.05f);
        smoothingSlider.setValue(Preferences.getVisualizerSmoothing());
        smoothingSlider.addOnChangeListener((slider, value, fromUser) -> {
            Preferences.setVisualizerSmoothing(value);
            updateLabels();
            onSettingChanged();
        });

        scaleSlider.setValueFrom(0.8f);
        scaleSlider.setValueTo(1.6f);
        scaleSlider.setStepSize(0.1f);
        scaleSlider.setValue(Preferences.getVisualizerScale());
        scaleSlider.addOnChangeListener((slider, value, fromUser) -> {
            Preferences.setVisualizerScale(value);
            updateLabels();
            onSettingChanged();
        });

        fpsSlider.setValueFrom(30f);
        fpsSlider.setValueTo(60f);
        fpsSlider.setStepSize(15f);
        fpsSlider.setValue(Preferences.getVisualizerFps());
        fpsSlider.addOnChangeListener((slider, value, fromUser) -> {
            Preferences.setVisualizerFps(Math.round(value));
            updateLabels();
            onSettingChanged();
        });

        String colorMode = Preferences.getVisualizerColorMode();
        if ("gradient".equals(colorMode)) {
            gradientChip.setChecked(true);
        } else {
            accentChip.setChecked(true);
        }

        colorGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.visualizer_color_gradient_chip) {
                Preferences.setVisualizerColorMode("gradient");
            } else {
                Preferences.setVisualizerColorMode("accent");
            }
            onSettingChanged();
        });

        view.findViewById(R.id.visualizer_settings_done).setOnClickListener(v -> dismiss());

        updateLabels();
        applyPreviewConfig();
        setPreviewEnabled(Preferences.isVisualizerEnabled());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        setPreviewEnabled(Preferences.isVisualizerEnabled());
    }

    @Override
    public void onStop() {
        stopPreview();
        super.onStop();
    }

    private void updatePeakCapsAvailability(SwitchMaterial peakCapsSwitch, String mode) {
        boolean enabled = "bars".equals(mode);
        peakCapsSwitch.setEnabled(enabled);
        peakCapsSwitch.setAlpha(enabled ? 1f : 0.5f);
    }

    private void updateLabels() {
        barCountLabel.setText(getString(R.string.visualizer_bar_count_label, Preferences.getVisualizerBarCount()));
        opacityLabel.setText(getString(R.string.visualizer_opacity_label, Math.round(Preferences.getVisualizerOpacity() * 100)));
        smoothingLabel.setText(getString(R.string.visualizer_smoothing_label, Math.round(Preferences.getVisualizerSmoothing() * 100)));
        scaleLabel.setText(getString(R.string.visualizer_scale_label, Preferences.getVisualizerScale()));
        fpsLabel.setText(getString(R.string.visualizer_fps_label, Preferences.getVisualizerFps()));
    }

    private void onSettingChanged() {
        applyPreviewConfig();
        notifyChanged();
    }

    private void notifyChanged() {
        getParentFragmentManager().setFragmentResult(RESULT_KEY, new Bundle());
    }

    private void applyPreviewConfig() {
        if (previewView == null) return;
        int barCount = Preferences.getVisualizerBarCount();
        float opacity = Preferences.getVisualizerOpacity();
        float smoothing = Preferences.getVisualizerSmoothing();
        float scale = Preferences.getVisualizerScale();
        int fps = Preferences.getVisualizerFps();
        int colorMode = "gradient".equals(Preferences.getVisualizerColorMode())
                ? VisualizerView.COLOR_MODE_GRADIENT
                : VisualizerView.COLOR_MODE_ACCENT;
        String modePreference = Preferences.getVisualizerMode();
        int mode = VisualizerView.MODE_BARS;
        if ("line".equals(modePreference)) {
            mode = VisualizerView.MODE_LINE;
        } else if ("dots".equals(modePreference)) {
            mode = VisualizerView.MODE_DOTS;
        }
        boolean peakCaps = Preferences.isVisualizerPeakCapsEnabled();
        previewIntervalMs = Math.max(16L, 1000L / Math.max(15, fps));
        previewView.setConfig(barCount, opacity, smoothing, scale, fps, colorMode, peakCaps, mode);
    }

    private void setPreviewEnabled(boolean enabled) {
        if (previewView != null) {
            previewView.setActive(enabled);
        }
        if (enabled) {
            startPreview();
        } else {
            stopPreview();
        }
    }

    private void startPreview() {
        if (previewRunning) return;
        previewRunning = true;
        previewHandler.post(previewRunnable);
    }

    private void stopPreview() {
        previewRunning = false;
        previewHandler.removeCallbacks(previewRunnable);
    }

    private void buildPreviewWaveform() {
        float phase = previewPhase;
        float speed = 0.22f;
        float twoPi = (float) (Math.PI * 2f);
        for (int i = 0; i < previewWaveform.length; i++) {
            float x = (float) i / (float) previewWaveform.length;
            float wave = (float) Math.sin(phase + x * twoPi * 2f);
            float noise = (previewRandom.nextFloat() - 0.5f) * 0.35f;
            float value = (wave + noise) * 0.9f;
            int sample = Math.round(value * 127f);
            if (sample > 127) sample = 127;
            if (sample < -128) sample = -128;
            previewWaveform[i] = (byte) sample;
        }
        previewPhase += speed;
    }
}
