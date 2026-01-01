package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Text;

public class PhotoVideoSwitcherView extends View implements FlashViews.Invertable {

    private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Text liveText, photoText, videoText;
    private float scrollWidth;

    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private float mLastX;
    private long mLastTouchTime;
    private boolean mIsScrolling, mIsTouch;
    private ValueAnimator animator;

    public PhotoVideoSwitcherView(Context context) {
        super(context);

        liveText =  new Text(getString(R.string.StoryLive),  14, AndroidUtilities.bold());
        photoText = new Text(getString(R.string.StoryPhoto), 14, AndroidUtilities.bold());
        videoText = new Text(getString(R.string.StoryVideo), 14, AndroidUtilities.bold());

        scrollWidth = dp(32) + liveText.getWidth() / 2 + photoText.getWidth() / 2 + videoText.getWidth() / 2;

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setInvert(0);
    }

    private float mode;

    private Utilities.Callback<Integer> onSwitchModeListener;
    private Utilities.Callback<Float> onSwitchingModeListener;
    public void setOnSwitchModeListener(Utilities.Callback<Integer> onSwitchModeListener) {
        this.onSwitchModeListener = onSwitchModeListener;
    }

    public void setOnSwitchingModeListener(Utilities.Callback<Float> onSwitchingModeListener) {
        this.onSwitchingModeListener = onSwitchingModeListener;
    }

    public void switchMode(int newMode) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(mode, newMode);
        animator.addUpdateListener(anm -> {
            mode = (float) anm.getAnimatedValue();
            if (onSwitchingModeListener != null) {
                onSwitchingModeListener.run(Utilities.clamp(mode, 1, -1));
            }
            invalidate();
        });
        animator.setDuration(320);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.start();
    }

    private final RectF liveRect = new RectF();
    private final RectF photoRect = new RectF();
    private final RectF videoRect = new RectF();
    private final RectF selectorRect = new RectF();

    private float getScrollCx() {
        return getWidth() / 2f + lerp(
            (dp(4 + 12) + photoText.getWidth() / 2),
            -(dp(4 + 12) + videoText.getWidth() / 2),
            mode
        );
    }

    public void scrollX(float dx) {
        if (!mIsScrolling && Math.abs(dx) > mTouchSlop) {
            mIsScrolling = true;
            modeAtTouchDown = mode;
        }
        if (mIsScrolling) {
            float overscrollFactor = 0.2f;
            if (mode <= -1 && dx < 0) {
                dx *= overscrollFactor;
            } else if (mode >= 1 && dx > 0) {
                dx *= overscrollFactor;
            }
            mode += dx / scrollWidth / 2.5f;
            mode = Utilities.clamp(mode, 1 + overscrollFactor, -1 - overscrollFactor);
            if (onSwitchingModeListener != null) {
                onSwitchingModeListener.run(Utilities.clamp(mode, 1, -1));
            }
            invalidate();
        }
    }

    public boolean stopScroll(float velocityX) {
        if (!mIsScrolling) {
            scrolledEnough = false;
            return false;
        }

        mIsScrolling = false;

        final int targetMode;
        if (Math.abs(velocityX) > 500) {
            targetMode = Utilities.clamp((int) Math.round(mode + (velocityX < 0 ? +1 : -1)), 1, -1);
        } else {
            targetMode = mode <= -0.5f ? -1 : mode > 0.5f ? 1 : 0;
        }

        switchMode(targetMode);
        if (onSwitchModeListener != null) {
            onSwitchModeListener.run(targetMode);
        }
        scrolledEnough = false;
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        float cy = getHeight() / 2f;
        float x = getScrollCx();

        final int oy = -dp(1);
        final int h = dp(26);
        liveRect.set(x - dp(4 + 12 + 12) - photoText.getWidth() - liveText.getWidth() - dp(4 + 12 + 12), cy - h / 2f + oy, x - dp(4 + 12 + 12) - photoText.getWidth() - dp(4), cy + h / 2f + oy);
        photoRect.set(x - dp(4 + 12 + 12) - photoText.getWidth(), cy - h / 2f + oy, x - dp(4), cy + h / 2f + oy);
        videoRect.set(x + dp(4), cy - h / 2f + oy, x + dp(4 + 12 + 12) + videoText.getWidth(), cy + h / 2f + oy);

        final float t = Utilities.clamp(mode, 1.025f, -1.025f);
        if (t < 0) {
            lerp(liveRect, photoRect, t + 1.0f, selectorRect);
        } else {
            lerp(photoRect, videoRect, t, selectorRect);
        }
        canvas.drawRoundRect(selectorRect, h / 2f, h / 2f, selectorPaint);

        liveText.draw(canvas,  x - dp(4 + 12 + 12) - photoText.getWidth() - liveText.getWidth() - dp(4 + 12), cy, 0xFFFFFFFF, 1.0f);
        photoText.draw(canvas, x - dp(4 + 12) - photoText.getWidth(), cy, 0xFFFFFFFF, 1.0f);
        videoText.draw(canvas, x + dp(4 + 12), cy, 0xFFFFFFFF, 1.0f);
    }

    protected boolean allowTouch() {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!allowTouch()) {
                    return false;
                }
                mIsTouch = true;
                modeAtTouchDown = mode;
                mLastTouchTime = System.currentTimeMillis();
                mLastX = event.getX();
                return true;

            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float deltaX = mLastX - x;
                scrollX(deltaX);
                mLastX = x;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsTouch = false;

                float xVelocity = 0;
                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    xVelocity = mVelocityTracker.getXVelocity();
                }

                if (!stopScroll(xVelocity) && (System.currentTimeMillis() - mLastTouchTime) <= ViewConfiguration.getTapTimeout() && Math.abs(event.getX() - mLastX) < AndroidUtilities.dp(4)) {
                    final int targetMode = (
                        event.getX() < photoRect.left ? -1 :
                        event.getX() > getScrollCx() ? 1 : 0
                    );
                    switchMode(targetMode);
                    if (onSwitchModeListener != null) {
                        onSwitchModeListener.run(targetMode);
                    }
                }

                mVelocityTracker.recycle();
                mVelocityTracker = null;
                scrolledEnough = false;
                break;
        }
        return super.onTouchEvent(event);
    }

    public boolean isTouch() {
        return mIsTouch;
    }

    public boolean isScrolling() {
        return mIsScrolling;
    }

    private float modeAtTouchDown;
    private boolean scrolledEnough;

    public boolean scrolledEnough() {
        return scrolledEnough || (scrolledEnough = Math.abs(mode - modeAtTouchDown) > .1f);
    }

    public void setInvert(float invert) {
        selectorPaint.setColor(ColorUtils.blendARGB(0x32ffffff, 0x20000000, invert));
        liveText.setColor(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert));
        photoText.setColor(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert));
        videoText.setColor(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert));
    }
}
