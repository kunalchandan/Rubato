package one.chandan.rubato.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

import one.chandan.rubato.R;

public class VisualizerView extends View {
    public static final int COLOR_MODE_ACCENT = 0;
    public static final int COLOR_MODE_GRADIENT = 1;
    public static final int MODE_BARS = 0;
    public static final int MODE_LINE = 1;
    public static final int MODE_DOTS = 2;

    private static final float BASELINE = 0.02f;
    private static final float PEAK_FALLOFF = 0.015f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Object waveformLock = new Object();

    private final Choreographer choreographer = Choreographer.getInstance();
    private final Choreographer.FrameCallback frameCallback = this::onFrame;

    private byte[] waveformBuffer;
    private boolean waveformDirty = false;

    private float[] targetAmplitudes = new float[0];
    private float[] currentAmplitudes = new float[0];
    private float[] peakAmplitudes = new float[0];
    private float[] barLeft = new float[0];
    private float[] barRight = new float[0];
    private float[] barCenter = new float[0];
    private float[] linePoints = new float[0];

    private int barCount = 48;
    private float opacity = 0.55f;
    private float smoothing = 0.6f;
    private float scale = 1.0f;
    private boolean peakCaps = false;
    private int fps = 45;
    private int colorMode = COLOR_MODE_ACCENT;
    private int mode = MODE_BARS;

    private long frameIntervalMs = 1000L / 45L;
    private long lastFrameTimeMs = 0L;
    private boolean active = false;
    private boolean running = false;

    private LinearGradient gradientShader;
    private int accentColor;
    private final float peakCapHeight;
    private final float lineWidth;
    private final float dotRadius;

    public VisualizerView(Context context) {
        this(context, null);
    }

    public VisualizerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        accentColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.theme_custom_primary));

        barPaint.setStyle(Paint.Style.FILL);
        peakPaint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
        peakCapHeight = dp(2);
        lineWidth = dp(2);
        dotRadius = dp(2.5f);
        linePaint.setStrokeWidth(lineWidth);
        updatePaints();
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (active) {
            start();
        } else if (!hasEnergy()) {
            stop();
        }
    }

    public void setConfig(int barCount, float opacity, float smoothing, float scale, int fps, int colorMode, boolean peakCaps, int mode) {
        this.barCount = Math.max(12, barCount);
        this.opacity = clamp(opacity, 0.1f, 1f);
        this.smoothing = clamp(smoothing, 0.05f, 0.95f);
        this.scale = clamp(scale, 0.5f, 3f);
        this.fps = Math.max(15, fps);
        this.colorMode = colorMode;
        this.peakCaps = peakCaps;
        this.mode = mode;
        frameIntervalMs = Math.max(16L, 1000L / this.fps);
        rebuildArrays();
        updatePaints();
        invalidate();
    }

    public void setWaveform(byte[] waveform) {
        if (waveform == null || waveform.length == 0) return;
        synchronized (waveformLock) {
            if (waveformBuffer == null || waveformBuffer.length != waveform.length) {
                waveformBuffer = new byte[waveform.length];
            }
            System.arraycopy(waveform, 0, waveformBuffer, 0, waveform.length);
            waveformDirty = true;
        }
        if (active) start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (active) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && active) {
            start();
        } else if (visibility != VISIBLE) {
            stop();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildBarPositions();
        updateShader();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        if (height == 0 || currentAmplitudes.length == 0) return;

        if (mode == MODE_LINE) {
            drawLine(canvas, height);
            return;
        }

        if (mode == MODE_DOTS) {
            drawDots(canvas, height);
            return;
        }

        drawBars(canvas, height);
    }

    private void onFrame(long frameTimeNanos) {
        if (!running) return;
        long nowMs = frameTimeNanos / 1_000_000L;
        if (lastFrameTimeMs == 0L || nowMs - lastFrameTimeMs >= frameIntervalMs) {
            lastFrameTimeMs = nowMs;
            updateAmplitudes();
            invalidate();
        }

        if (!active && !hasEnergy()) {
            stop();
            return;
        }
        choreographer.postFrameCallback(frameCallback);
    }

    private void start() {
        if (running) return;
        running = true;
        lastFrameTimeMs = 0L;
        choreographer.postFrameCallback(frameCallback);
    }

    private void stop() {
        if (!running) return;
        running = false;
        choreographer.removeFrameCallback(frameCallback);
    }

    private void rebuildArrays() {
        targetAmplitudes = new float[barCount];
        currentAmplitudes = new float[barCount];
        peakAmplitudes = new float[barCount];
        barLeft = new float[barCount];
        barRight = new float[barCount];
        barCenter = new float[barCount];
        linePoints = barCount > 1 ? new float[(barCount - 1) * 4] : new float[0];
        rebuildBarPositions();
    }

    private void rebuildBarPositions() {
        if (barCount == 0) return;
        float width = getWidth();
        if (width == 0f) return;
        float spacing = Math.max(1f, width / (barCount * 10f));
        float totalSpacing = spacing * (barCount - 1);
        float barWidth = (width - totalSpacing) / barCount;
        if (barWidth < dp(1)) {
            barWidth = dp(1);
            totalSpacing = width - barWidth * barCount;
            spacing = totalSpacing / Math.max(1, barCount - 1);
        }
        float x = 0f;
        for (int i = 0; i < barCount; i++) {
            barLeft[i] = x;
            barRight[i] = x + barWidth;
            barCenter[i] = x + barWidth / 2f;
            x += barWidth + spacing;
        }

        if (barCount > 1) {
            for (int i = 0; i < barCount - 1; i++) {
                int idx = i * 4;
                linePoints[idx] = barCenter[i];
                linePoints[idx + 2] = barCenter[i + 1];
            }
        }
    }

    private void updateAmplitudes() {
        boolean hasWaveform = false;
        synchronized (waveformLock) {
            if (waveformDirty && waveformBuffer != null) {
                computeTargetAmplitudes(waveformBuffer);
                waveformDirty = false;
                hasWaveform = true;
            }
        }

        float response = 1f - smoothing;
        for (int i = 0; i < barCount; i++) {
            float target = active && hasWaveform ? targetAmplitudes[i] : BASELINE;
            float current = currentAmplitudes[i] + (target - currentAmplitudes[i]) * response;
            currentAmplitudes[i] = current;

            if (peakCaps) {
                float peak = peakAmplitudes[i];
                if (current > peak) {
                    peakAmplitudes[i] = current;
                } else {
                    peakAmplitudes[i] = Math.max(BASELINE, peak - PEAK_FALLOFF);
                }
            }
        }
    }

    private void computeTargetAmplitudes(byte[] waveform) {
        int samples = waveform.length;
        if (samples == 0 || barCount == 0) return;
        int samplesPerBar = Math.max(1, samples / barCount);

        for (int i = 0; i < barCount; i++) {
            int start = i * samplesPerBar;
            int end = Math.min(samples, start + samplesPerBar);
            float max = 0f;
            for (int j = start; j < end; j++) {
                float value = Math.abs(waveform[j]) / 128f;
                if (value > max) max = value;
            }
            float scaled = Math.min(1f, max * scale);
            targetAmplitudes[i] = Math.max(BASELINE, scaled);
        }
    }

    private void updatePaints() {
        int alpha = Math.round(opacity * 255);
        barPaint.setAlpha(alpha);
        peakPaint.setAlpha(Math.min(255, alpha + 40));
        linePaint.setAlpha(alpha);
        dotPaint.setAlpha(alpha);
        linePaint.setStrokeWidth(lineWidth);
        if (colorMode == COLOR_MODE_GRADIENT) {
            updateShader();
        } else {
            barPaint.setShader(null);
            barPaint.setColor(accentColor);
            peakPaint.setColor(accentColor);
            linePaint.setShader(null);
            dotPaint.setShader(null);
            linePaint.setColor(accentColor);
            dotPaint.setColor(accentColor);
        }
    }

    private void updateShader() {
        if (colorMode != COLOR_MODE_GRADIENT) return;
        if (getHeight() == 0) return;
        int top = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(getContext(), R.color.theme_custom_primary));
        int bottom = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary,
                ContextCompat.getColor(getContext(), R.color.theme_custom_secondary));
        gradientShader = new LinearGradient(
                0f,
                0f,
                0f,
                getHeight(),
                new int[]{top, bottom},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        );
        barPaint.setShader(gradientShader);
        peakPaint.setShader(gradientShader);
        linePaint.setShader(gradientShader);
        dotPaint.setShader(gradientShader);
    }

    private void drawBars(Canvas canvas, int height) {
        for (int i = 0; i < barCount; i++) {
            float amplitude = currentAmplitudes[i];
            float barHeight = amplitude * height;
            float left = barLeft[i];
            float right = barRight[i];
            float top = height - barHeight;
            canvas.drawRect(left, top, right, height, barPaint);

            if (peakCaps) {
                float peakHeight = peakAmplitudes[i] * height;
                float capTop = height - peakHeight - peakCapHeight;
                canvas.drawRect(left, capTop, right, capTop + peakCapHeight, peakPaint);
            }
        }
    }

    private void drawLine(Canvas canvas, int height) {
        if (linePoints.length == 0) return;
        updateLinePoints(height);
        canvas.drawLines(linePoints, linePaint);
    }

    private void drawDots(Canvas canvas, int height) {
        for (int i = 0; i < barCount; i++) {
            float amplitude = currentAmplitudes[i];
            float y = height - (amplitude * height);
            float radius = Math.min(dotRadius, (barRight[i] - barLeft[i]) / 2f);
            canvas.drawCircle(barCenter[i], y, radius, dotPaint);
        }
    }

    private void updateLinePoints(int height) {
        for (int i = 0; i < barCount - 1; i++) {
            int idx = i * 4;
            linePoints[idx + 1] = height - (currentAmplitudes[i] * height);
            linePoints[idx + 3] = height - (currentAmplitudes[i + 1] * height);
        }
    }

    private boolean hasEnergy() {
        for (float amplitude : currentAmplitudes) {
            if (amplitude > BASELINE + 0.01f) return true;
        }
        return false;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
