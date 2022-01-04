package ua.itaysonlab.redesign;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;

import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringListener;

public class BottomSheetSwipeComponent extends FrameLayout {
    public final static SpringConfig SPRING_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(95, 13);
    /**
     * View touch slop
     */
    private final int touchSlop;
    /**
     * Reusable rects
     */
    private final Rect ignoreRect = new Rect();
    /**
     * Flag, that indicates if animation is in progress
     */
    private boolean animationInProgress;
    /**
     * Flag, that indicates if swipe is processing now
     */
    private boolean processingSwipeNow;
    /**
     * Flag, that indicates if swipe is disallowed now
     */
    private boolean swipeDisallowed;
    /**
     * Linked actionsheet
     */
    private BottomSlideFragment fragment;
    /**
     * Current swipe progress
     */
    private float currentProgress;
    /**
     * Current measured sheet height
     */
    private int sheetHeight;

    private final GestureDetectorCompat detector = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (swipeDisallowed || animationInProgress)
                return false;

            if (!processingSwipeNow) {
                if (distanceY > -touchSlop || Math.abs(distanceY) * 0.5f <= Math.abs(distanceX)) {
                    swipeDisallowed = true;
                    return false;
                }

                boolean processed = false;
                if (distanceY <= -touchSlop && Math.abs(distanceY) * 0.5f > Math.abs(distanceX)) {
                    View v = getChildAt(1);
                    if (isIgnoredView((ViewGroup) v, e2, ignoreRect)) {
                        swipeDisallowed = true;
                        return false;
                    }

                    int h = v.getMeasuredHeight();
                    if (h <= 0) {
                        Point p = new Point();
                        ((WindowManager) v.getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(p);

                        v.measure(MeasureSpec.makeMeasureSpec(p.x, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(p.y, MeasureSpec.AT_MOST));
                        h = v.getMeasuredHeight();
                    }
                    sheetHeight = h;
                    processingSwipeNow = true;

                    for (int i = 0; i < getChildCount(); i++)
                        getChildAt(i).dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, e2.getX(), e2.getY(), 0));

                    requestLayout();
                    processed = true;
                }

                if (!processed) {
                    swipeDisallowed = true;
                    return false;
                }
            }

            currentProgress = Math.min(sheetHeight, Math.max(0, e2.getY() - e1.getY())) / (float) sheetHeight;
            invalidateProgress();

            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (swipeDisallowed || animationInProgress || !processingSwipeNow)
                return false;

            boolean t = velocityY >= 1200;
            if (t) {
                animationInProgress = false;
                getChildAt(1).dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
                clear();
                animateClose();
                return true;
            }

            return false;
        }
    });

    {
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public BottomSheetSwipeComponent(@NonNull Context context) {
        super(context);
    }

    public BottomSheetSwipeComponent(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BottomSheetSwipeComponent(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BottomSheetSwipeComponent(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Sets current progress
     *
     * @param currentProgress Currently progress
     */
    public void setCurrentProgress(float currentProgress) {
        this.currentProgress = currentProgress;
        invalidateProgress();
    }

    /**
     * Sets calculated sheet height
     *
     * @param sheetHeight Calculated sheet height
     */
    public void setSheetHeight(int sheetHeight) {
        this.sheetHeight = sheetHeight;
    }

    /**
     * Invalidates offsets
     */
    private void invalidateProgress() {
        getChildAt(0).setAlpha(1f - currentProgress);
        if (fragment != null)
            fragment.onSlide(currentProgress);
    }

    /**
     * Sets new linked bottom fragment
     *
     * @param fragment Bottom fragment
     */
    public void initWith(BottomSlideFragment fragment) {
        this.fragment = fragment;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (processTouchEvent(ev))
            return true;

        if (!super.dispatchTouchEvent(ev)) {
            int act = ev.getAction() & MotionEvent.ACTION_MASK;
            return act == MotionEvent.ACTION_DOWN;
        } else return true;
    }

    /**
     * Clears flags
     */
    private void clear() {
        swipeDisallowed = false;
        processingSwipeNow = false;
        animationInProgress = false;
    }

    private void animateReset() {
        animationInProgress = true;
        Spring s = fragment.springSystem.createSpring();
        s.setSpringConfig(SPRING_CONFIG);
        s.addListener(new SpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                double val = Math.max(0, spring.getCurrentValue());

                currentProgress = (float) val;
                invalidateProgress();

                if (val <= 0) {
                    spring.destroy();
                    animationInProgress = false;
                    clear();
                }
            }

            @Override
            public void onSpringAtRest(Spring spring) {
            }

            @Override
            public void onSpringActivate(Spring spring) {
            }

            @Override
            public void onSpringEndStateChange(Spring spring) {
            }
        });
        s.setCurrentValue(currentProgress, true);
        s.setEndValue(0);
    }

    /**
     * Animates close
     */
    private void animateClose() {
        animationInProgress = true;
        fragment.dismiss();
    }

    private boolean processTouchEvent(MotionEvent ev) {
        if (fragment == null)
            return false;

        int act = ev.getAction() & MotionEvent.ACTION_MASK;
        if (animationInProgress)
            return true;

        if (!detector.onTouchEvent(ev)) {
            switch (act) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (processingSwipeNow) {
                        clear();
                        if (currentProgress <= 0.5f) {
                            animateReset();
                        } else {
                            animationInProgress = false;
                            animateClose();
                        }
                    } else if (swipeDisallowed) clear();
            }
        }
        return false;
    }

    /**
     * @param root - Root to check childs from
     * @param e    - Event to check
     * @param rect - Reusable rect
     * @return If this view should be ignored on swipe
     */
    private boolean isIgnoredView(ViewGroup root, MotionEvent e, Rect rect) {
        if (root == null) return false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View ch = root.getChildAt(i);
            if (ch.getVisibility() != View.VISIBLE)
                continue;

            if (isIgnoredView0(ch, e, rect))
                return true;

            if (ch instanceof ViewGroup) {
                if (ch.getVisibility() != View.VISIBLE)
                    continue;

                if (isIgnoredView((ViewGroup) ch, e, rect))
                    return true;
            }
        }
        return isIgnoredView0(root, e, rect);
    }

    private boolean isIgnoredView0(View v, MotionEvent e, Rect rect) {
        v.getGlobalVisibleRect(rect);
        if (v.getVisibility() != View.VISIBLE || !rect.contains((int) e.getX(), (int) e.getY()))
            return false;

        return v.canScrollVertically(-1);
    }
}