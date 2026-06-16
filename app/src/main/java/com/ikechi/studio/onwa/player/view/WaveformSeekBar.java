package com.ikechi.studio.onwa.player.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * WaveformSeekBar — full-width waveform progress bar.
 *
 * Bars are sized so they exactly fill the view width with no wasted space.
 * Played portion uses a rich cyan → violet → gold gradient.
 * Unplayed portion is a soft translucent white.
 * A glowing thumb circle marks the current position.
 */
public class WaveformSeekBar extends View {

    private static final int   BAR_COUNT         = 100;
    /** Gap between bars as a fraction of total width. */
    private static final float GAP_FRACTION      = 0.18f;   // 18 % of each slot is gap
    private static final float MIN_BAR_HEIGHT_DP = 2.5f;
    private static final float CORNER_RADIUS_DP  = 1.8f;
    private static final float THUMB_RADIUS_DP   = 6f;
    private static final float THUMB_GLOW_DP     = 10f;

    // Gradient stops — deep cyan → electric violet → warm gold
    private static final int COLOR_START = 0xFF00E5FF;   // cyan
    private static final int COLOR_MID1  = 0xFF651FFF;   // deep violet
    private static final int COLOR_MID2  = 0xFFD500F9;   // magenta-violet
    private static final int COLOR_END   = 0xFFFFAB00;   // amber-gold

    private static final int UNPLAYED_COLOR = 0x28FFFFFF; // faint white

    // -----------------------------------------------------------------------

    private float[] mWaveform  = new float[BAR_COUNT];
    private long    mDuration  = 0;
    private long    mPosition  = 0;
    private boolean mIsDragging = false;

    private final Paint mBarPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Cached gradient — rebuilt only when width changes. */
    private LinearGradient mPlayedGradient;
    private int            mLastGradientWidth = 0;

    private OnSeekListener mListener;

    private float mThumbRadius;
    private float mThumbGlow;
    private float mMinBarHeight;
    private float mCornerRadius;

    // Pre-computed per-bar geometry (updated in onSizeChanged)
    private float mSlotWidth;   // width of one bar slot (bar + gap)
    private float mBarWidth;    // actual drawn bar width

    public interface OnSeekListener {
        void onSeek(long position);
    }

    // -----------------------------------------------------------------------

    public WaveformSeekBar(Context context) {
        this(context, null);
    }

    public WaveformSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = context.getResources().getDisplayMetrics().density;
        mThumbRadius  = THUMB_RADIUS_DP   * density;
        mThumbGlow    = THUMB_GLOW_DP     * density;
        mMinBarHeight = MIN_BAR_HEIGHT_DP * density;
        mCornerRadius = CORNER_RADIUS_DP  * density;

        mThumbPaint.setColor(Color.WHITE);
        mThumbPaint.setStyle(Paint.Style.FILL);
        mThumbPaint.setShadowLayer(6f, 0f, 3f, 0x80000000);

        mThumbGlowPaint.setStyle(Paint.Style.FILL);
        mThumbGlowPaint.setColor(0x40FFFFFF);

        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // Seed with a realistic-looking random waveform
        for (int i = 0; i < BAR_COUNT; i++) {
            mWaveform[i] = 0.10f + (float) Math.random() * 0.55f;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void setOnSeekListener(OnSeekListener listener) {
        mListener = listener;
    }

	public void setPlayedColor(int color){
		
	}
	
    public void setDuration(long duration) {
        mDuration = duration;
        invalidate();
    }

    public void setPosition(long position) {
        if (!mIsDragging) {
            mPosition = position;
            invalidate();
        }
    }

    /**
     * Supplies real waveform amplitude data (values in 0..1).
     * The data is down-sampled / averaged to fit BAR_COUNT bars.
     */
    public void setWaveformData(float[] data) {
        if (data == null || data.length == 0) return;
        for (int i = 0; i < BAR_COUNT; i++) {
            int start = (int)((long) i * data.length / BAR_COUNT);
            int end   = (int)((long)(i + 1) * data.length / BAR_COUNT);
            if (end <= start) end = start + 1;
            float sum = 0f;
            for (int j = start; j < Math.min(end, data.length); j++) sum += data[j];
            float avg = sum / (end - start);
            // Smooth blend so sudden changes look natural
            mWaveform[i] = mWaveform[i] * 0.4f + avg * 0.6f;
        }
        invalidate();
    }

    // -----------------------------------------------------------------------
    // Size changes
    // -----------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w <= 0) return;

        // Each bar gets an equal slot; gap is a fraction of that slot.
        mSlotWidth = (float) w / BAR_COUNT;
        mBarWidth  = mSlotWidth * (1f - GAP_FRACTION);

        rebuildGradient(w);
    }

    private void rebuildGradient(int w) {
        if (w == mLastGradientWidth) return;
        mLastGradientWidth = w;
        mPlayedGradient = new LinearGradient(
            0, 0, w, 0,
            new int[]{ COLOR_START, COLOR_MID1, COLOR_MID2, COLOR_END },
            new float[]{ 0f, 0.35f, 0.65f, 1f },
            Shader.TileMode.CLAMP);
    }

    // -----------------------------------------------------------------------
    // Touch
    // -----------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float x        = ev.getX();
        float fraction = Math.max(0f, Math.min(1f, x / getWidth()));

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsDragging = true;
                // fall through
            case MotionEvent.ACTION_MOVE:
                if (mDuration > 0) {
                    mPosition = (long)(fraction * mDuration);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                if (mDuration > 0 && mListener != null) {
                    mListener.onSeek((long)(fraction * mDuration));
                }
                performClick();
                return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int   w              = getWidth();
        float h              = getHeight();
        if (w <= 0 || h <= 0) return;

        // Ensure gradient exists (guard against onSizeChanged not yet called)
        rebuildGradient(w);

        float cy             = h / 2f;
        float playedFraction = mDuration > 0 ? (float) mPosition / mDuration : 0f;
        float thumbX         = playedFraction * w;

        RectF rect = new RectF();

        for (int i = 0; i < BAR_COUNT; i++) {
            // Left edge of this slot — fills the entire width evenly
            float slotLeft = i * mSlotWidth;

            // Centre each bar within its slot
            float bx   = slotLeft + (mSlotWidth - mBarWidth) * 0.5f;
            float barH = Math.max(mMinBarHeight, mWaveform[i] * h * 0.82f);
            float by   = cy - barH / 2f;

            // A bar is "played" if its centre is to the left of the thumb
            float barCentre  = bx + mBarWidth / 2f;
            boolean isPlayed = barCentre <= thumbX;

            if (isPlayed) {
                mBarPaint.setShader(mPlayedGradient);
                mBarPaint.setAlpha(255);
            } else {
                mBarPaint.setShader(null);
                mBarPaint.setColor(UNPLAYED_COLOR);
            }

            rect.set(bx, by, bx + mBarWidth, by + barH);
            canvas.drawRoundRect(rect, mCornerRadius, mCornerRadius, mBarPaint);
        }

        mBarPaint.setShader(null);

        // Thumb glow
        canvas.drawCircle(thumbX, cy, mThumbGlow, mThumbGlowPaint);
        // Thumb core
        canvas.drawCircle(thumbX, cy, mThumbRadius, mThumbPaint);
    }
}

