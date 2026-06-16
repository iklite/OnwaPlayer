package com.ikechi.studio.onwa.player.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import com.ikechi.studio.onwa.player.models.AudioItem;
import com.ikechi.studio.onwa.player.utils.MediaUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CoverFlowView — card‑triangle fan layout with reversed movement and
 * instant‑snap on manual drag.
 *
 * <h3>Visual behaviour</h3>
 * <ul>
 *   <li>Swipe left  → cards move left  → next track appears.</li>
 *   <li>Swipe right → cards move right → previous track appears.</li>
 * </ul>
 *
 * <h3>Drag vs. fling</h3>
 * <ul>
 *   <li>Manual drag (finger moves, then lifts slowly) → the card that is
 *       visually centred at the moment of release is instantly selected
 *       (no animation).</li>
 *   <li>Fling (quick swipe) → the carousel continues moving with inertia
 *       and smoothly snaps to the nearest card when it stops.</li>
 * </ul>
 *
 * <h3>Public API</h3>
 * All movement methods ({@link #moveToLeft()}, {@link #moveToRight()},
 * {@link #shiftToLeft(int)}, {@link #shiftToRight(int)},
 * {@link #smoothScrollTo(int)}) work as documented.
 *
 * <h3>Performance</h3>
 * Album art is pre‑loaded for all visible cards plus a buffer of
 * {@value #PRELOAD_BUFFER} on each side.
 */
public class CoverFlowView extends View {

    private static final String TAG = "CoverFlowView";

    // ── Visual constants ─────────────────────────────────────────────────────
    private static final float MAX_SCALE                  = 1.0f;
    private static final float MIN_SCALE                  = 0.60f;
    private static final float MAX_ALPHA                  = 1.0f;
    private static final float MIN_ALPHA                  = 0.50f;

    private static final int   VISIBLE_SIDES              = 4;
    private static final int   PRELOAD_BUFFER             = 6;

    private static final int   SNAP_DURATION_MS           = 280;
    private static final float CORNER_RADIUS_DP           = 12f;
    private static final float ROTATION_DEG_PER_STEP      = 20f;
    private static final float VERTICAL_DROP_PER_STEP_DP  = 20f;
    private static final float STEP_FRACTION              = 0.38f;

    // ── Data ─────────────────────────────────────────────────────────────────
    private List<AudioItem> mItems = new ArrayList<>();
    private int             mCenterIndex = 0;
    private float           mScrollX = 0f;   // continuous offset in pixels

    private final Scroller        mScroller;
    private final GestureDetector mGestureDetector;
    private final Handler         mUiHandler = new Handler(Looper.getMainLooper());

    private OnTrackChangeListener mListener;

    private final Paint mShadowPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTitlePaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArtistPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDefaultBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOverlayPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Map<String, Bitmap> mBitmapCache = new HashMap<>();

    // ── Layout values ───────────────────────────────────────────────────────
    private float mCardWidth;
    private float mCardHeight;
    private float mCornerRadius;
    private float mViewCenterX;
    private float mViewCenterY;
    private float mVerticalStep;
    private float mStep;

    private float mBaseCardWidth;
    private float mBaseCardHeight;

    // ── Drag detection ──────────────────────────────────────────────────────
    /** true while the user is manually scrolling (no fling). */
    private boolean mIsDragging = false;

    // ── Callback ─────────────────────────────────────────────────────────────
    public interface OnTrackChangeListener {
        void onTrackChanged(AudioItem item, int position);
    }

    // ── Constructors ─────────────────────────────────────────────────────────
    public CoverFlowView(Context context) {
        this(context, null);
    }

    public CoverFlowView(Context context, AttributeSet attrs) {
        super(context, attrs);

        float density = context.getResources().getDisplayMetrics().density;
        mBaseCardWidth  = 170f * density;
        mBaseCardHeight = 170f * density;
        mCornerRadius   = CORNER_RADIUS_DP * density;
        mVerticalStep   = VERTICAL_DROP_PER_STEP_DP * density;

        mScroller = new Scroller(context, new DecelerateInterpolator(2f));

        mGestureDetector = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) {
                    mScroller.forceFinished(true);
                    mIsDragging = false;
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float dx, float dy) {
                    mIsDragging = true;
                    // REVERSED: finger moves left (dx>0) → cards move left
                    mScrollX += dx;
                    clampScroll();
                    invalidate();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2,
                                       float vx, float vy) {
                    // vx negative = fling left → go to next track
                    int steps = Math.round(-vx / 1500f);
                    Log.d(TAG, "onFling vx=" + vx + " → steps=" + steps);
                    mIsDragging = false;   // not a static drag
                    if (steps == 0) {
                        snapToNearest();
                    } else if (steps > 0) {
                        shiftToRight(steps);
                    } else {
                        shiftToLeft(-steps);
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    int tapped = mCenterIndex
                        + Math.round((e.getX() - mViewCenterX) / mStep);
                    tapped = Math.max(0, Math.min(tapped, mItems.size() - 1));
                    if (tapped != mCenterIndex) {
                        smoothScrollTo(tapped);
                    }
                    return true;
                }
            });

        // Paints setup
        float density2 = context.getResources().getDisplayMetrics().density;
        mShadowPaint.setColor(0x40000000);
        mShadowPaint.setShadowLayer(14f, 0f, 7f, 0x80000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        mTitlePaint.setColor(Color.WHITE);
        mTitlePaint.setTextSize(13f * density2);
        mTitlePaint.setTextAlign(Paint.Align.CENTER);
        mTitlePaint.setFakeBoldText(true);
        mTitlePaint.setShadowLayer(3f, 0f, 2f, 0xC0000000);

        mArtistPaint.setColor(0xFFCCCCCC);
        mArtistPaint.setTextSize(11f * density2);
        mArtistPaint.setTextAlign(Paint.Align.CENTER);
        mArtistPaint.setShadowLayer(2f, 0f, 1f, 0xC0000000);

        mDefaultBgPaint.setColor(0xFF2A2A3A);
        mDefaultBgPaint.setStyle(Paint.Style.FILL);

        mOverlayPaint.setStyle(Paint.Style.FILL);

        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(1.5f);
        mBorderPaint.setColor(0x40FFFFFF);
        mBorderPaint.setAntiAlias(true);
    }

    // ── Public API (unchanged) ───────────────────────────────────────────────
    public void setOnTrackChangeListener(OnTrackChangeListener listener) {
        mListener = listener;
    }

    /** Sets the play queue and positions the carousel at the given index. */
    public void setItems(List<AudioItem> items, int centerIndex) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        mCenterIndex = Math.max(0, Math.min(centerIndex, mItems.size() - 1));
        mScrollX = 0f;
        mBitmapCache.clear();
        preloadArtAround(mCenterIndex, PRELOAD_BUFFER);
        invalidate();
    }

    /** Jumps to the given index without animation. */
    public void setCurrentIndex(int index) {
        if (index < 0 || index >= mItems.size()) return;
        mCenterIndex = index;
        mScrollX = 0f;
        preloadArtAround(mCenterIndex, PRELOAD_BUFFER);
        invalidate();
    }

    /**
     * Smoothly scrolls to the exact index.
     * This is the low‑level animation method used by all public helpers.
     */
    public void smoothScrollTo(int index) {
        Log.d(TAG, "smoothScrollTo(" + index + ")");
        if (index < 0 || index >= mItems.size()) return;
        float targetScroll = (index - mCenterIndex) * mStep;
        int dx = (int) (targetScroll - mScrollX);
        mScroller.startScroll((int) mScrollX, 0, dx, 0, SNAP_DURATION_MS);
        postInvalidate();
    }

    /** Move one card to the left (previous track). */
    public void moveToLeft() {
        Log.d(TAG, "moveToLeft()");
        if (mCenterIndex > 0) {
            smoothScrollTo(mCenterIndex - 1);
        }
    }

    /** Move one card to the right (next track). */
    public void moveToRight() {
        Log.d(TAG, "moveToRight()");
        if (mCenterIndex < mItems.size() - 1) {
            smoothScrollTo(mCenterIndex + 1);
        }
    }

    /** Shift by a given number of cards (negative = left, positive = right). */
    public void shift(int steps) {
        Log.d(TAG, "shift(" + steps + ")");
        int target = mCenterIndex + steps;
        target = Math.max(0, Math.min(target, mItems.size() - 1));
        if (target != mCenterIndex) {
            smoothScrollTo(target);
        }
    }

    /** Shift a specific number of cards to the left. */
    public void shiftToLeft(int steps) {
        Log.d(TAG, "shiftToLeft(" + steps + ")");
        shift(-Math.abs(steps));
    }

    /** Shift a specific number of cards to the right. */
    public void shiftToRight(int steps) {
        Log.d(TAG, "shiftToRight(" + steps + ")");
        shift(Math.abs(steps));
    }

    public int getCurrentIndex() {
        return mCenterIndex;
    }

    // ── Measure / Layout ─────────────────────────────────────────────────────
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int desiredH = (int) (mBaseCardHeight * 1.45f);
        setMeasuredDimension(w, resolveSize(desiredH, hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mViewCenterX = getWidth()  / 2f;
        mViewCenterY = getHeight() * 0.40f;
        mCardWidth   = Math.min(mBaseCardWidth, getWidth() * 0.52f);
        mCardHeight  = mBaseCardHeight * (mCardWidth / mBaseCardWidth);
        mStep        = mCardWidth * STEP_FRACTION;
    }

    // ── Drawing ──────────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mItems.isEmpty()) return;

        float virtualPos = mCenterIndex + mScrollX / mStep;

        // Draw furthest cards first, center on top
        for (int dist = VISIBLE_SIDES; dist >= 1; dist--) {
            drawCard(canvas, virtualPos, -dist);
            drawCard(canvas, virtualPos, +dist);
        }
        drawCard(canvas, virtualPos, 0);
    }

    private void drawCard(Canvas canvas, float virtualPos, int slot) {
        int dataIndex = Math.round(virtualPos) + slot;
        if (dataIndex < 0 || dataIndex >= mItems.size()) return;

        float dist = dataIndex - virtualPos;
        float absDist = Math.abs(dist);
        if (absDist > VISIBLE_SIDES + 0.5f) return;

        float t = Math.min(1f, absDist / VISIBLE_SIDES);
        float scale    = MAX_SCALE - (MAX_SCALE - MIN_SCALE) * t;
        float rotation = dist * ROTATION_DEG_PER_STEP;
        float vertDrop = absDist * mVerticalStep;

        float w  = mCardWidth  * scale;
        float h  = mCardHeight * scale;
        float cx = mViewCenterX + dist * mStep;
        float cy = mViewCenterY + vertDrop;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        canvas.translate(-w / 2f, -h / 2f);

        RectF cardRect = new RectF(0, 0, w, h);

        // Shadow
        RectF shadowRect = new RectF(4f, 4f, w + 4f, h + 4f);
        canvas.drawRoundRect(shadowRect, mCornerRadius, mCornerRadius, mShadowPaint);

        // Album art or default background
        Bitmap art = getArtForIndex(dataIndex);
        if (art != null) {
            Path clipPath = new Path();
            clipPath.addRoundRect(cardRect, mCornerRadius, mCornerRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawBitmap(art, null, cardRect, null);

            mOverlayPaint.setShader(new android.graphics.LinearGradient(
										0, h * 0.55f, 0, h,
										new int[]{0x00000000, 0xCC000000},
										null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, h * 0.55f, w, h, mOverlayPaint);
            mOverlayPaint.setShader(null);
            canvas.restore();
        } else {
            canvas.drawRoundRect(cardRect, mCornerRadius, mCornerRadius, mDefaultBgPaint);
        }

        // Dim side cards
        if (absDist > 0.05f) {
            mOverlayPaint.setColor((int)(0xAA * t) << 24);
            canvas.drawRoundRect(cardRect, mCornerRadius, mCornerRadius, mOverlayPaint);
        }

        // Border
        canvas.drawRoundRect(cardRect, mCornerRadius, mCornerRadius, mBorderPaint);

        canvas.restore();

        // Title & artist below center card
        if (slot == 0 && absDist < 0.4f) {
            AudioItem item = mItems.get(dataIndex);
            if (item != null) {
                float textCX = mViewCenterX + dist * mStep;
                float cardBottom = cy + h / 2f;
                mTitlePaint.setAlpha(255);
                mArtistPaint.setAlpha(200);
                canvas.drawText(trimText(item.getTitle(),  mTitlePaint,  w),
                                textCX, cardBottom + mTitlePaint.getTextSize() + 4f, mTitlePaint);
                canvas.drawText(trimText(item.getArtist(), mArtistPaint, w),
                                textCX, cardBottom + mTitlePaint.getTextSize()
                                + mArtistPaint.getTextSize() + 8f, mArtistPaint);
            }
        }
    }

    // ── Touch ────────────────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = mGestureDetector.onTouchEvent(ev);

        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mScroller.isFinished()) {
                if (mIsDragging) {
                    // Manual drag → instant snap to the visually centred card
                    instantSnap();
                } else {
                    snapToNearest();
                }
            }
            if (action == MotionEvent.ACTION_UP) {
                mIsDragging = false;
            }
        }
        return handled || true;
    }

    @Override
    public void computeScroll() {
        if (!mScroller.computeScrollOffset()) return;

        mScrollX = mScroller.getCurrX();
        int steps = Math.round(mScrollX / mStep);
        if (steps != 0) {
            int newCenter = mCenterIndex + steps;
            newCenter = Math.max(0, Math.min(newCenter, mItems.size() - 1));
            if (newCenter != mCenterIndex) {
                mScrollX -= (newCenter - mCenterIndex) * mStep;
                mCenterIndex = newCenter;
                preloadArtAround(mCenterIndex, PRELOAD_BUFFER);
                notifyTrackChanged();
            }
        }
        clampScroll();
        if (mScroller.isFinished()) {
            snapToNearest();
        }
        invalidate();
    }

    // ── Snap helpers ─────────────────────────────────────────────────────────
    private void instantSnap() {
        float virtualPos = mCenterIndex + mScrollX / mStep;
        int newCenter = Math.round(virtualPos);
        newCenter = Math.max(0, Math.min(newCenter, mItems.size() - 1));

        Log.d(TAG, "instantSnap to " + newCenter);
        if (newCenter != mCenterIndex) {
            mCenterIndex = newCenter;
            mScrollX = 0f;
            preloadArtAround(mCenterIndex, PRELOAD_BUFFER);
            notifyTrackChanged();
        } else {
            mScrollX = 0f;
        }
        invalidate();
    }

    private void snapToNearest() {
        Log.d(TAG, "snapToNearest");
        int nearest = Math.round(mScrollX / mStep);
        if (nearest == 0) {
            if (Math.abs(mScrollX) > 0.5f) {
                mScroller.startScroll((int) mScrollX, 0, -(int) mScrollX, 0, 180);
                postInvalidate();
            } else {
                mScrollX = 0f;
                invalidate();
            }
            return;
        }
        int newCenter = Math.max(0, Math.min(mCenterIndex + nearest, mItems.size() - 1));
        float targetScroll = (newCenter - mCenterIndex) * mStep;
        int dx = (int)(targetScroll - mScrollX);
        mScroller.startScroll((int) mScrollX, 0, dx, 0, SNAP_DURATION_MS);
        postInvalidate();
    }

    private void clampScroll() {
        float min = -mCenterIndex * mStep;
        float max = (mItems.size() - 1 - mCenterIndex) * mStep;
        if (mScrollX < min) mScrollX = min;
        if (mScrollX > max) mScrollX = max;
    }

    // ── Callback ─────────────────────────────────────────────────────────────
    private void notifyTrackChanged() {
        if (mListener == null || mCenterIndex >= mItems.size()) return;
        final AudioItem item = mItems.get(mCenterIndex);
        final int pos = mCenterIndex;
        mUiHandler.post(new Runnable() {
				@Override public void run() {
					mListener.onTrackChanged(item, pos);
				}
			});
    }

    // ── Bitmap loading & caching ─────────────────────────────────────────────
    private void preloadArtAround(int center, int buffer) {
        for (int i = center - buffer; i <= center + buffer; i++) {
            loadAlbumArt(i);
        }
    }

    private Bitmap getArtForIndex(int index) {
        if (index < 0 || index >= mItems.size()) return null;
        AudioItem item = mItems.get(index);
        byte[] bytes = item.getAlbumArtBytes();
        if (bytes != null && bytes.length > 0) {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        return mBitmapCache.get(item.getUri().toString());
    }

    private void loadAlbumArt(final int index) {
        if (index < 0 || index >= mItems.size()) return;
        final AudioItem item = mItems.get(index);
        final String key = item.getUri().toString();
        if (mBitmapCache.containsKey(key)) return;

        new Thread(new Runnable() {
				@Override public void run() {
					Bitmap art = MediaUtils.getAudioAlbumArtBitmap(getContext(), item.getUri());
					if (art != null) {
						mBitmapCache.put(key, art);
						mUiHandler.post(new Runnable() {
								@Override public void run() { invalidate(); }
							});
					}
				}
			}, "coverflow-art-loader").start();
    }

    // ── Utility ──────────────────────────────────────────────────────────────
    private String trimText(String text, Paint paint, float maxWidth) {
        if (text == null) return "";
        if (paint.measureText(text) <= maxWidth) return text;
        while (text.length() > 1 && paint.measureText(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }
}
