package com.ikechi.studio.onwa.widgets;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.ikechi.studio.onwa.player.R;

public class MultiPanelLayout extends ViewGroup {

    public static final int DOCK_STACKED  = 0;
    public static final int DOCK_TABBED   = 1;

    public static final int PANEL_NETWORK = 0;
    public static final int PANEL_VIDEO   = 1;
    public static final int PANEL_AUDIO   = 2;
    public static final int PANEL_CHAT    = 3;

    private static final float MID_PCT = 0.5f;
    private static final long ANIM_MS = 500;
    private static final float EPSILON = 0.001f;

	private float EXPANDED_PCT = 0.72f;
    private View mTopView;
    private View[] mPanels;
    private int mNPanels;

    private int mDockStyle = DOCK_STACKED;
    private boolean mHideDockedInLandscape = true;

    private float mOffset = 0f;
    private int mActivePanel = -1;

    private boolean mIsLandscape;
    private boolean mLockInLandscape = false;
    private int mHandlePx;
    private int mGapPx;
    private int mTouchSlop;

    private int mTouchPanel = -1;
    private float mStartY;
    private float mStartOffset;

    private VelocityTracker mVelocityTracker;
    private ValueAnimator mAnimator;

    private boolean mPanelsHidden = false;

    public interface OnPanelStateChangeListener {
        void onPanelExpanded(int panelIndex);
        void onPanelCollapsed(int panelIndex);
    }

    private OnPanelStateChangeListener mListener;

    public MultiPanelLayout(Context c, AttributeSet a) {
        super(c, a);

        float d = c.getResources().getDisplayMetrics().density;
        mHandlePx = Math.round(48 * d);
        mGapPx = Math.round(5 * d);
        mTouchSlop = ViewConfiguration.get(c).getScaledTouchSlop();

        if (a != null) {
            TypedArray ta = c.obtainStyledAttributes(a, R.styleable.MultiPanelLayout);
            mDockStyle = ta.getInt(R.styleable.MultiPanelLayout_dockStyle, DOCK_STACKED);
            mHideDockedInLandscape = ta.getBoolean(
                R.styleable.MultiPanelLayout_hideDockedInLandscape, true);
            mLockInLandscape = ta.getBoolean(R.styleable.MultiPanelLayout_lockInLandscape, false);
            ta.recycle();
        }

        if (!mLockInLandscape) mHideDockedInLandscape = false;

        setWillNotDraw(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(true);
    }

    public void hideTabs() {
        cancelAnimation();
        mPanelsHidden = true;
        if (mPanels != null) {
            for (View panel : mPanels) {
                if (panel != null) panel.setVisibility(GONE);
            }
        }
        mActivePanel = -1;
        mOffset = 0f;
        requestLayout();
    }

    public void showTabs() {
        cancelAnimation();
        mPanelsHidden = false;
        if (mPanels != null) {
            for (View panel : mPanels) {
                if (panel != null) panel.setVisibility(VISIBLE);
            }
        }
        requestLayout();
    }

    private int dockH() {
        if (mPanelsHidden) return 0;
        return (mDockStyle == DOCK_TABBED) ? mHandlePx :
            mNPanels * mHandlePx + (mNPanels - 1) * mGapPx;
    }

    private int usableH(int H) {
        if (mPanelsHidden) return H;
        return (mIsLandscape && mHideDockedInLandscape) ? H : H - dockH();
    }

    private float expandedTop(int H) {
        return usableH(H) * (1f - EXPANDED_PCT);
    }

    private float collapsedTop(int H, int i) {
        if (mPanelsHidden) return H;
        if (mIsLandscape && mHideDockedInLandscape) return H;
        if (mDockStyle == DOCK_TABBED) return usableH(H);
        return usableH(H) + i * (mHandlePx + mGapPx);
    }

    @Override
    protected void onFinishInflate() {
        mTopView = getChildAt(0);
        mNPanels = getChildCount() - 1;
        mPanels = new View[mNPanels];
        for (int i = 0; i < mNPanels; i++) {
            mPanels[i] = getChildAt(i + 1);
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int W = MeasureSpec.getSize(wSpec);
        int H = MeasureSpec.getSize(hSpec);

        mIsLandscape = (W > H);

        int usable = usableH(H);
        int topH = (int)(usable * (1f - EXPANDED_PCT * mOffset));

        mTopView.measure(
            MeasureSpec.makeMeasureSpec(W, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(topH, MeasureSpec.EXACTLY));

        if (!mPanelsHidden) {
            for (int i = 0; i < mNPanels; i++) {
                int panelH;
				if (i == mActivePanel && mOffset > EPSILON) {
					// Panel fills from its current top to the screen bottom when expanded
					float collapsedTop = collapsedTop(H, i);
					float expandedTopPos = expandedTop(H);
					float currentTop = collapsedTop + (expandedTopPos - collapsedTop) * mOffset;
					panelH = H - (int)currentTop;
				} else {
					panelH = mHandlePx;
				}

                int pw;
                if (mDockStyle == DOCK_TABBED && mNPanels > 0) {
                    if (i == mActivePanel && mOffset > 0f) {
                        pw = W;
                    } else {
                        pw = W / mNPanels;
                    }
                } else {
                    pw = W;
                }

                mPanels[i].measure(
                    MeasureSpec.makeMeasureSpec(pw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(panelH, MeasureSpec.EXACTLY));
            }
        }

        setMeasuredDimension(W, H);
    }

    @Override
    protected void onLayout(boolean c, int l, int t, int r, int b) {
        int W = r - l;
        int H = b - t;

        int usable = usableH(H);
        float topH = usable * (1f - EXPANDED_PCT * mOffset);

        mTopView.layout(0, 0, W, (int)topH);

        if (mPanelsHidden) return;

        for (int i = 0; i < mNPanels; i++) {
            float collapsed = collapsedTop(H, i);
            float expandedTop = expandedTop(H);
            float top = (i == mActivePanel)
                ? collapsed + (expandedTop - collapsed) * mOffset
                : collapsed;

            int left, width;
            if (mDockStyle == DOCK_TABBED && mNPanels > 0) {
                if (i == mActivePanel && mOffset > 0f) {
                    left = 0;
                    width = W;
                } else {
                    width = W / mNPanels;
                    left = i * width;
                }
            } else {
                left = 0;
                width = W;
            }

            int measuredH = mPanels[i].getMeasuredHeight();
            // ★ FIX: When fully expanded, snap panel bottom to screen bottom
            int panelBottom;
            if (i == mActivePanel && mOffset > 0.99f) {
                panelBottom = H;
            } else {
                panelBottom = (int)top + measuredH;
            }

            mPanels[i].layout(left, (int)top, left + width, panelBottom);

            if (Build.VERSION.SDK_INT >= 21) {
                mPanels[i].setElevation(i == mActivePanel ? 10f : 0f);
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mIsLandscape && mLockInLandscape) return false;
        if (mPanelsHidden) return false;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelAnimation();

                mTouchPanel = findPanel(ev.getX(), ev.getY());

                if (mTouchPanel != -1 && mOffset > EPSILON && mActivePanel != mTouchPanel) {
                    mOffset = 0f;
                    mActivePanel = -1;
                    requestLayout();
                }

                mStartY = ev.getY();
                mStartOffset = mOffset;

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                }
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(ev);
                return false;

            case MotionEvent.ACTION_MOVE:
                if (mTouchPanel >= 0 &&
                    Math.abs(ev.getY() - mStartY) > mTouchSlop) {
                    return true;
                }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mIsLandscape && mLockInLandscape) return false;
        if (mPanelsHidden) return false;

        if (mVelocityTracker == null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mVelocityTracker = VelocityTracker.obtain();
            mTouchPanel = findPanel(ev.getX(), ev.getY());
            mStartY = ev.getY();
            mStartOffset = mOffset;
            cancelAnimation();
        }

        if (mVelocityTracker != null) {
            mVelocityTracker.addMovement(ev);
        }

        int H = getHeight();

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mTouchPanel >= 0) {
                    float dy = mStartY - ev.getY();
                    float range = usableH(H) * EXPANDED_PCT;
                    mOffset = Math.max(0f, Math.min(1f,
                                                    mStartOffset + dy / range));
                    mActivePanel = mTouchPanel;
                    requestLayout();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (mVelocityTracker != null && mTouchPanel >= 0) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float vy = mVelocityTracker.getYVelocity();

                    float target;
                    if (vy < -800) {
                        target = 1f;
                    } else if (vy > 800) {
                        target = 0f;
                    } else {
                        if (mOffset > 0.75f) target = 1f;
                        else if (mOffset > 0.25f) target = MID_PCT;
                        else target = 0f;
                    }

                    animateTo(target, mTouchPanel);
                }

                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                break;
        }
        return true;
    }

    private int findPanel(float x, float y) {
        if (mPanelsHidden) return -1;

        int H = getHeight();
        int W = getWidth();

        if (mActivePanel != -1 && mOffset > EPSILON) {
            float collapsed = collapsedTop(H, mActivePanel);
            float expandedTop = expandedTop(H);
            float currentTop = collapsed + (expandedTop - collapsed) * mOffset;
            if (y >= currentTop && y <= currentTop + mHandlePx) {
                return mActivePanel;
            }
        }

        if (mDockStyle == DOCK_TABBED && mNPanels > 0) {
            if (y >= usableH(H)) {
                int w = W / mNPanels;
                return Math.min(mNPanels - 1, (int)(x / w));
            }
        }

        for (int i = 0; i < mNPanels; i++) {
            float top = collapsedTop(H, i);
            if (y >= top && y <= top + mHandlePx) return i;
        }
        return -1;
    }

    private void animateTo(final float target, final int panel) {
        cancelAnimation();

        mAnimator = ValueAnimator.ofFloat(mOffset, target);
        mAnimator.setDuration(ANIM_MS);
        mAnimator.setInterpolator(new DecelerateInterpolator());

        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					mOffset = (Float) animation.getAnimatedValue();
					mActivePanel = panel;
					requestLayout();
				}
			});

        mAnimator.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					if (target >= 1f - EPSILON && mListener != null) {
						mListener.onPanelExpanded(panel);
					} else if (target <= EPSILON && mListener != null) {
						mListener.onPanelCollapsed(panel);
						mActivePanel = -1;
					}
					mAnimator = null;
				}
			});

        mAnimator.start();
    }

    private void cancelAnimation() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

	public void setFillMode(boolean fill){
		EXPANDED_PCT = fill ? 1.0f : 0.72f;
	}
	
    public boolean isPanelExpanded(int idx) {
        return mActivePanel == idx && mOffset >= (1f - EPSILON);
    }

    public void expandPanel(int idx) {
        if (mPanelsHidden) return;
        animateTo(1f, idx);
    }

    public void collapseAll() {
        if (mPanelsHidden) return;
        animateTo(0f, mActivePanel);
    }

    public void setOnPanelStateChangeListener(OnPanelStateChangeListener l) {
        mListener = l;
    }
}