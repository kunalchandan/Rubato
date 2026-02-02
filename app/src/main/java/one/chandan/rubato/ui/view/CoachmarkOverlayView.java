package one.chandan.rubato.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import com.google.android.material.color.MaterialColors;

public class CoachmarkOverlayView extends View {
    private static final long PULSE_DURATION_MS = 1200L;
    private static final int DIM_ALPHA = 180;

    private final RectF targetRect = new RectF();
    private final Paint dimPaint = new Paint();
    private final Paint clearPaint = new Paint();
    private final Paint ringPaint = new Paint();
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String label = "";
    private float pulse = 0f;
    private ValueAnimator pulseAnimator;

    private final float ringPadding;
    private final float pulseRange;
    private final float labelPadding;
    private final float labelMaxWidth;

    public CoachmarkOverlayView(Context context) {
        this(context, null);
    }

    public CoachmarkOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CoachmarkOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        dimPaint.setColor(0xFF000000);
        dimPaint.setAlpha(DIM_ALPHA);
        dimPaint.setStyle(Paint.Style.FILL);

        clearPaint.setAntiAlias(true);
        clearPaint.setStyle(Paint.Style.FILL);
        clearPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0xFF00C853));

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(sp(14));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        labelBackgroundPaint.setColor(0xCC000000);

        ringPadding = dp(10);
        pulseRange = dp(8);
        labelPadding = dp(12);
        labelMaxWidth = dp(280);
    }

    public void setTargetRect(Rect rect) {
        if (rect == null) return;
        targetRect.set(rect);
        invalidate();
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startPulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPulse();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        if (targetRect.isEmpty()) return;

        float cx = targetRect.centerX();
        float cy = targetRect.centerY();
        float baseRadius = Math.max(targetRect.width(), targetRect.height()) / 2f + ringPadding;

        canvas.drawCircle(cx, cy, baseRadius, clearPaint);

        int ringAlpha = (int) (120 + 135 * (1f - pulse));
        ringPaint.setAlpha(Math.max(0, Math.min(255, ringAlpha)));
        canvas.drawCircle(cx, cy, baseRadius + pulseRange * pulse, ringPaint);

        drawLabel(canvas, cx, cy);
    }

    private void drawLabel(Canvas canvas, float cx, float cy) {
        if (label == null || label.isEmpty()) return;

        int availableWidth = (int) Math.min(labelMaxWidth, getWidth() - 2 * labelPadding);
        StaticLayout layout = StaticLayout.Builder.obtain(label, 0, label.length(), textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.0f)
                .setIncludePad(false)
                .build();

        float x = clamp(cx - availableWidth / 2f, labelPadding, getWidth() - availableWidth - labelPadding);
        float y = targetRect.bottom + dp(16);

        if (y + layout.getHeight() > getHeight() - labelPadding) {
            y = targetRect.top - dp(16) - layout.getHeight();
        }

        float bgLeft = x - labelPadding;
        float bgTop = y - labelPadding;
        float bgRight = x + availableWidth + labelPadding;
        float bgBottom = y + layout.getHeight() + labelPadding;

        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, dp(12), dp(12), labelBackgroundPaint);

        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }

    private void startPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(PULSE_DURATION_MS);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
        pulseAnimator.addUpdateListener(animation -> {
            pulse = (float) animation.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
