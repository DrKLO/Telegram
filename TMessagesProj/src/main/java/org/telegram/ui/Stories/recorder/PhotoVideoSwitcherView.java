package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

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
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class PhotoVideoSwitcherView extends View implements FlashViews.Invertable {

    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private StaticLayout photoText, videoText;
    private float photoTextLeft, photoTextWidth, photoTextHeight;
    private float videoTextLeft, videoTextWidth, videoTextHeight;
    private float scrollWidth;

    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private float mLastX;
    private long mLastTouchTime;
    private boolean mIsScrolling, mIsTouch;
    private ValueAnimator animator;

    public PhotoVideoSwitcherView(Context context) {
        super(context);

        selectorPaint.setColor(0x32ffffff);
        textPaint.setColor(0xffffffff);

        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextSize(AndroidUtilities.dpf2(14));
        textPaint.setShadowLayer(AndroidUtilities.dpf2(1), 0, AndroidUtilities.dpf2(0.4f), 0x33000000);

        CharSequence text = LocaleController.getString("StoryPhoto");
        if (text == null) {
            text = "Photo";
        }
        photoText = new StaticLayout(text, textPaint, AndroidUtilities.displaySize.x / 2, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        photoTextLeft = photoText.getLineCount() > 0 ? photoText.getLineLeft(0) : 0;
        photoTextWidth = photoText.getLineCount() > 0 ? photoText.getLineWidth(0) : 0;
        photoTextHeight = photoText.getHeight();

        text = LocaleController.getString("StoryVideo");
        if (text == null) {
            text = "Video";
        }
        videoText = new StaticLayout(text, textPaint, AndroidUtilities.displaySize.x / 2, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        videoTextLeft = videoText.getLineCount() > 0 ? videoText.getLineLeft(0) : 0;
        videoTextWidth = videoText.getLineCount() > 0 ? videoText.getLineWidth(0) : 0;
        videoTextHeight = videoText.getHeight();

        scrollWidth = dp(32) + photoTextWidth / 2 + videoTextWidth / 2;

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private float mode;

    private Utilities.Callback<Boolean> onSwitchModeListener;
    private Utilities.Callback<Float> onSwitchingModeListener;
    public void setOnSwitchModeListener(Utilities.Callback<Boolean> onSwitchModeListener) {
        this.onSwitchModeListener = onSwitchModeListener;
    }

    public void setOnSwitchingModeListener(Utilities.Callback<Float> onSwitchingModeListener) {
        this.onSwitchingModeListener = onSwitchingModeListener;
    }

    public void switchMode(boolean modeIsVideo) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(mode, modeIsVideo ? 1 : 0);
        animator.addUpdateListener(anm -> {
            mode = (float) anm.getAnimatedValue();
            if (onSwitchingModeListener != null) {
                onSwitchingModeListener.run(Utilities.clamp(mode, 1, 0));
            }
            invalidate();
        });
        animator.setDuration(320);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.start();
    }

    private RectF photoRect = new RectF(), videoRect = new RectF(), selectorRect = new RectF();

    private float getScrollCx() {
        return getWidth() / 2f + AndroidUtilities.lerp(
            (dp(4 + 12) + photoTextWidth / 2),
            -(dp(4 + 12) + videoTextWidth / 2),
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
            if (mode <= 0 && dx < 0) {
                dx *= overscrollFactor;
            } else if (mode >= 1 && dx > 0) {
                dx *= overscrollFactor;
            }
            mode += dx / scrollWidth / 2.5f;
            mode = Utilities.clamp(mode, 1 + overscrollFactor, -overscrollFactor);
            if (onSwitchingModeListener != null) {
                onSwitchingModeListener.run(Utilities.clamp(mode, 1, 0));
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

        final boolean targetMode;
        if (Math.abs(velocityX) > 500) {
            targetMode = velocityX < 0;
        } else {
            targetMode = mode > 0.5f;
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
        photoRect.set(x - dp(4 + 12 + 12) - photoTextWidth, cy - h / 2f + oy, x - dp(4), cy + h / 2f + oy);
        videoRect.set(x + dp(4), cy - h / 2f + oy, x + dp(4 + 12 + 12) + videoTextWidth, cy + h / 2f + oy);
        AndroidUtilities.lerp(photoRect, videoRect, Utilities.clamp(mode, 1.025f, -.025f), selectorRect);
        canvas.drawRoundRect(selectorRect, h / 2f, h / 2f, selectorPaint);

        canvas.save();
        canvas.translate(x - dp(4 + 12) - photoTextWidth - photoTextLeft, cy - photoTextHeight / 2f + oy);
        photoText.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(x + dp(4 + 12) - videoTextLeft, cy - videoTextHeight / 2f + oy);
        videoText.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
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
                    final boolean targetMode = event.getX() > getScrollCx();
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
        textPaint.setColor(ColorUtils.blendARGB(0xffffffff, 0xff000000, invert));
    }
}
