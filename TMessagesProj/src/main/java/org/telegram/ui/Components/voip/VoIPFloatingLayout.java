package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class VoIPFloatingLayout extends FrameLayout {

    private final float FLOATING_MODE_SCALE = 0.23f;
    float starX;
    float starY;
    float startMovingFromX;
    float startMovingFromY;
    boolean moving;

    int lastH;
    int lastW;

    WindowInsets lastInsets;
    float touchSlop;

    final Path path = new Path();
    final RectF rectF = new RectF();
    final Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint mutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Drawable mutedDrawable;

    public float relativePositionToSetX = -1f;
    float relativePositionToSetY = -1f;

    float leftPadding;
    float rightPadding;
    float topPadding;
    float bottomPadding;

    float toFloatingModeProgress = 0;
    float mutedProgress = 0;

    private boolean uiVisible;
    private boolean floatingMode;
    private boolean setedFloatingMode;
    private boolean switchingToFloatingMode;
    public boolean measuredAsFloatingMode;

    private float overrideCornerRadius = -1f;
    private boolean active = true;
    public boolean alwaysFloating;
    public int bottomOffset;
    public float savedRelativePositionX;
    public float savedRelativePositionY;
    public float updatePositionFromX;
    public float updatePositionFromY;
    public boolean switchingToPip;
    public boolean isAppearing;
    Drawable outerShadow;

    ValueAnimator switchToFloatingModeAnimator;
    private ValueAnimator.AnimatorUpdateListener progressUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            toFloatingModeProgress = (float) valueAnimator.getAnimatedValue();
            if (delegate != null) {
                delegate.onChange(toFloatingModeProgress, measuredAsFloatingMode);
            }
            invalidate();
        }
    };

    ValueAnimator mutedAnimator;
    private ValueAnimator.AnimatorUpdateListener mutedUpdateListener = valueAnimator -> {
        mutedProgress = (float) valueAnimator.getAnimatedValue();
        invalidate();
    };

    OnClickListener tapListener;

    private VoIPFloatingLayoutDelegate delegate;

    public interface VoIPFloatingLayoutDelegate {
        void onChange(float progress, boolean value);
    }

    public VoIPFloatingLayout(@NonNull Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    if (overrideCornerRadius >= 0) {
                        if (overrideCornerRadius < 1) {
                            outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                        } else {
                            outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), overrideCornerRadius);
                        }
                    } else {
                        if (!floatingMode) {
                            outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                        } else {
                            outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), floatingMode ? AndroidUtilities.dp(4) : 0);
                        }
                    }
                }
            });
            setClipToOutline(true);
        }
        mutedPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f)));
        mutedDrawable = ContextCompat.getDrawable(context, R.drawable.calls_mute_mini);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        measuredAsFloatingMode = false;
        if (floatingMode) {
            width *= FLOATING_MODE_SCALE;
            height *= FLOATING_MODE_SCALE;
            measuredAsFloatingMode = true;
        } else {
            if (!switchingToPip) {
                setTranslationX(0);
                setTranslationY(0);
            }
        }
        if (delegate != null) {
            delegate.onChange(toFloatingModeProgress, measuredAsFloatingMode);
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));

        if (getMeasuredHeight() != lastH && getMeasuredWidth() != lastW) {
            path.reset();
            rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            path.addRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Path.Direction.CW);
            path.toggleInverseFillType();
        }
        lastH = getMeasuredHeight();
        lastW = getMeasuredWidth();

        updatePadding();
    }

    private void updatePadding() {
        leftPadding = AndroidUtilities.dp(16);
        rightPadding = AndroidUtilities.dp(16);
        topPadding = uiVisible ? AndroidUtilities.dp(60) : AndroidUtilities.dp(16);
        bottomPadding = (uiVisible ? AndroidUtilities.dp(100) : AndroidUtilities.dp(16)) + bottomOffset;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    public void setDelegate(VoIPFloatingLayoutDelegate voIPFloatingLayoutDelegate) {
        delegate = voIPFloatingLayoutDelegate;
    }

    long startTime;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        ViewParent parent = getParent();
        if (!floatingMode || switchingToFloatingMode || !active) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (floatingMode && !switchingToFloatingMode) {
                    startTime = System.currentTimeMillis();
                    starX = event.getX() + getX();
                    starY = event.getY() + getY();
                    animate().setListener(null).cancel();
                    animate().scaleY(1.05f).scaleX(1.05f).alpha(1f).setStartDelay(0).start();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() + getX() - starX;
                float dy = event.getY() + getY() - starY;
                if (!moving && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    moving = true;
                    starX = event.getX() + getX();
                    starY = event.getY() + getY();
                    startMovingFromX = getTranslationX();
                    startMovingFromY = getTranslationY();
                    dx = 0;
                    dy = 0;
                }
                if (moving) {
                    setTranslationX(startMovingFromX + dx);
                    setTranslationY(startMovingFromY + dy);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (parent != null && floatingMode && !switchingToFloatingMode) {
                    parent.requestDisallowInterceptTouchEvent(false);
                    animate().setListener(null).cancel();
                    ViewPropertyAnimator animator = animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(0);

                    if (tapListener != null && !moving && System.currentTimeMillis() - startTime < 200) {
                        tapListener.onClick(this);
                    }

                    int parentWidth = ((View) getParent()).getMeasuredWidth();
                    int parentHeight = ((View) getParent()).getMeasuredHeight();

                    float maxTop = topPadding;
                    float maxBottom = bottomPadding;
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                        maxTop += lastInsets.getSystemWindowInsetTop();
                        maxBottom += lastInsets.getSystemWindowInsetBottom();
                    }

                    if (getX() < leftPadding) {
                        animator.translationX(leftPadding);
                    } else if (getX() + getMeasuredWidth() > parentWidth - rightPadding) {
                        animator.translationX(parentWidth - getMeasuredWidth() - rightPadding);
                    }

                    if (getY() < maxTop) {
                        animator.translationY(maxTop);
                    } else if (getY() + getMeasuredHeight() > parentHeight - maxBottom) {
                        animator.translationY(parentHeight - getMeasuredHeight() - maxBottom);
                    }
                    animator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                }
                moving = false;
                break;
        }
        return true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean animated = false;
        if (updatePositionFromX >= 0) {
            if(!isAppearing) {
                animate().setListener(null).cancel();
            }
            setTranslationX(updatePositionFromX);
            setTranslationY(updatePositionFromY);
            if(!isAppearing) {
                setScaleX(1f);
                setScaleY(1f);
                setAlpha(1f);
            }
            updatePositionFromX = -1f;
            updatePositionFromY = -1f;
        }

        if (relativePositionToSetX >= 0 && floatingMode && getMeasuredWidth() > 0) {
            setRelativePositionInternal(relativePositionToSetX, relativePositionToSetY, getMeasuredWidth(), getMeasuredHeight(), animated);
            relativePositionToSetX = -1f;
            relativePositionToSetY = -1f;
        }


        super.dispatchDraw(canvas);


        if (!switchingToFloatingMode && floatingMode != setedFloatingMode) {
            setFloatingMode(setedFloatingMode, true);
        }

        int cX = getMeasuredWidth() >> 1;
        int cY = getMeasuredHeight() - (int) (AndroidUtilities.dp(18) * 1f / getScaleY());
        canvas.save();
        float scaleX = 1f / getScaleX() * toFloatingModeProgress * mutedProgress;
        float scaleY = 1f / getScaleY() * toFloatingModeProgress * mutedProgress;
        canvas.scale(scaleX, scaleY, cX, cY);

        canvas.drawCircle(cX, cY, AndroidUtilities.dp(14), mutedPaint);
        mutedDrawable.setBounds(
                cX - mutedDrawable.getIntrinsicWidth() / 2, cY - mutedDrawable.getIntrinsicHeight() / 2,
                cX + mutedDrawable.getIntrinsicWidth() / 2, cY + mutedDrawable.getIntrinsicHeight() / 2
        );
        mutedDrawable.draw(canvas);
        canvas.restore();
        if (switchingToFloatingMode) {
            invalidate();
        }
    }

    public void setInsets(WindowInsets lastInsets) {
        this.lastInsets = lastInsets;
    }

    public void setRelativePosition(float x, float y) {
        ViewParent parent = getParent();
        if (!floatingMode || parent == null || ((View) parent).getMeasuredWidth() > 0 || getMeasuredWidth() == 0 || getMeasuredHeight() == 0) {
            relativePositionToSetX = x;
            relativePositionToSetY = y;
        } else {
            setRelativePositionInternal(x, y, getMeasuredWidth(), getMeasuredHeight(), true);
        }
    }

    public void setUiVisible(boolean uiVisible) {
        ViewParent parent = getParent();
        if (parent == null) {
            this.uiVisible = uiVisible;
            return;
        }
        this.uiVisible = uiVisible;
    }

    public void setBottomOffset(int bottomOffset, boolean animated) {
        ViewParent parent = getParent();
        if (parent == null || !animated) {
            this.bottomOffset = bottomOffset;
            return;
        }
        this.bottomOffset = bottomOffset;
    }

    private void setRelativePositionInternal(float xRelative, float yRelative, int width, int height, boolean animated) {
        ViewParent parent = getParent();
        if (parent == null || !floatingMode || switchingToFloatingMode || !active) {
            return;
        }

        float maxTop = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH || lastInsets == null ? 0 : lastInsets.getSystemWindowInsetTop() + topPadding);
        float maxBottom = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH || lastInsets == null ? 0 : lastInsets.getSystemWindowInsetBottom() + bottomPadding);

        float xPoint = leftPadding + (((View) parent).getMeasuredWidth() - leftPadding - rightPadding - width) * xRelative;
        float yPoint = maxTop + (((View) parent).getMeasuredHeight() - maxBottom - maxTop - height) * yRelative;

        if (animated) {
            animate().setListener(null).cancel();
            animate().scaleX(1f).scaleY(1f)
                    .translationX(xPoint)
                    .translationY(yPoint)
                    .alpha(1f)
                    .setStartDelay(uiVisible ? 0 : 150)
                    .setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            if (!alwaysFloating) {
                animate().setListener(null).cancel();
                setScaleX(1f);
                setScaleY(1f);
                animate().alpha(1f).setDuration(150).start();
            }
            setTranslationX(xPoint);
            setTranslationY(yPoint);
        }
    }

    public void setFloatingMode(boolean show, boolean animated) {
        if (getMeasuredWidth() <= 0 || getVisibility() != View.VISIBLE) {
            animated = false;
        }
        if (!animated) {
            if (floatingMode != show) {
                floatingMode = show;
                setedFloatingMode = show;
                toFloatingModeProgress = floatingMode ? 1f : 0f;
                requestLayout();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    invalidateOutline();
                }
            }
            return;
        }
        if (switchingToFloatingMode) {
            setedFloatingMode = show;
            return;
        }
        if (show && !floatingMode) {
            floatingMode = true;
            setedFloatingMode = show;
            updatePadding();
            if (relativePositionToSetX >= 0) {
                setRelativePositionInternal(relativePositionToSetX, relativePositionToSetY,
                        (int) (getMeasuredWidth() * FLOATING_MODE_SCALE), (int) (getMeasuredHeight() * FLOATING_MODE_SCALE), false);
            }
            floatingMode = false;
            switchingToFloatingMode = true;
            float toX = getTranslationX();
            float toY = getTranslationY();
            setTranslationX(0);
            setTranslationY(0);
            invalidate();
            if (switchToFloatingModeAnimator != null) {
                switchToFloatingModeAnimator.cancel();
            }
            switchToFloatingModeAnimator = ValueAnimator.ofFloat(toFloatingModeProgress, 1f);
            switchToFloatingModeAnimator.addUpdateListener(progressUpdateListener);
            switchToFloatingModeAnimator.setDuration(300);
            switchToFloatingModeAnimator.start();
            animate().setListener(null).cancel();
            animate().scaleX(FLOATING_MODE_SCALE).scaleY(FLOATING_MODE_SCALE)
                    .translationX(toX - (getMeasuredWidth() - getMeasuredWidth() * FLOATING_MODE_SCALE) / 2f)
                    .translationY(toY - (getMeasuredHeight() - getMeasuredHeight() * FLOATING_MODE_SCALE) / 2f)
                    .alpha(1f)
                    .setStartDelay(0)
                    .setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    switchingToFloatingMode = false;
                    floatingMode = true;
                    updatePositionFromX = toX;
                    updatePositionFromY = toY;
                    requestLayout();

                }
            }).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else if (!show && floatingMode) {
            setedFloatingMode = show;
            float fromX = getTranslationX();
            float fromY = getTranslationY();
            updatePadding();
            floatingMode = false;
            switchingToFloatingMode = true;
            requestLayout();
            animate().setListener(null).cancel();
            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (!measuredAsFloatingMode) {
                        if (switchToFloatingModeAnimator != null) {
                            switchToFloatingModeAnimator.cancel();
                        }
                        switchToFloatingModeAnimator = ValueAnimator.ofFloat(toFloatingModeProgress, 0f);
                        switchToFloatingModeAnimator.addUpdateListener(progressUpdateListener);
                        switchToFloatingModeAnimator.setDuration(300);
                        switchToFloatingModeAnimator.start();

                        float fromXfinal = fromX - (getMeasuredWidth() - getMeasuredWidth() * FLOATING_MODE_SCALE) / 2f;
                        float fromYfinal = fromY - (getMeasuredHeight() - getMeasuredHeight() * FLOATING_MODE_SCALE) / 2f;
                        getViewTreeObserver().removeOnPreDrawListener(this);
                        setTranslationX(fromXfinal);
                        setTranslationY(fromYfinal);
                        setScaleX(FLOATING_MODE_SCALE);
                        setScaleY(FLOATING_MODE_SCALE);
                        animate().setListener(null).cancel();
                        animate().setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                switchingToFloatingMode = false;
                                requestLayout();
                            }
                        }).scaleX(1f).scaleY(1f).translationX(0).translationY(0).alpha(1f).setDuration(300).setStartDelay(0).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    } else {
                        floatingMode = false;
                        requestLayout();
                    }
                    return false;
                }
            });
        } else {
            toFloatingModeProgress = floatingMode ? 1f : 0f;
            floatingMode = show;
            setedFloatingMode = floatingMode;
            requestLayout();
        }
    }

    public void setMuted(boolean muted, boolean animated) {
        if (!animated) {
            if (mutedAnimator != null) {
                mutedAnimator.cancel();
            }
            mutedProgress = muted ? 1f : 0;
            invalidate();
        } else {
            if (mutedAnimator != null) {
                mutedAnimator.cancel();
            }
            mutedAnimator = ValueAnimator.ofFloat(mutedProgress, muted ? 1f : 0);
            mutedAnimator.addUpdateListener(mutedUpdateListener);
            mutedAnimator.setDuration(150);
            mutedAnimator.start();
        }
    }

    public void setCornerRadius(float cornerRadius) {
        overrideCornerRadius = cornerRadius;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            invalidateOutline();
        }
    }

    public void setOnTapListener(OnClickListener tapListener) {
        this.tapListener = tapListener;
    }

    public void setRelativePosition(VoIPFloatingLayout fromLayout) {
        ViewParent parent = getParent();
        if (parent == null) {
            return;
        }

        float maxTop = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH || lastInsets == null ? 0 : lastInsets.getSystemWindowInsetTop() + topPadding);
        float maxBottom = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH || lastInsets == null ? 0 : lastInsets.getSystemWindowInsetBottom() + bottomPadding);

        float xRelative = (fromLayout.getTranslationX() - leftPadding) / (((View) parent).getMeasuredWidth() - leftPadding - rightPadding - fromLayout.getMeasuredWidth());
        float yRelative = (fromLayout.getTranslationY() - maxTop) / (((View) parent).getMeasuredHeight() - maxBottom - maxTop - fromLayout.getMeasuredHeight());

        xRelative = Math.min(1f, Math.max(0, xRelative));
        yRelative = Math.min(1f, Math.max(0, yRelative));

        setRelativePosition(xRelative, yRelative);
    }

    public void setIsActive(boolean b) {
        active = b;
    }

    public void saveRelativePosition() {
        if (getMeasuredWidth() > 0 && relativePositionToSetX < 0) {
            ViewParent parent = getParent();
            if (parent == null) {
                return;
            }
            float maxTop = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH || lastInsets == null ? 0 : lastInsets.getSystemWindowInsetTop() + topPadding);
            float maxBottom = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH || lastInsets == null ? 0 : lastInsets.getSystemWindowInsetBottom() + bottomPadding);

            savedRelativePositionX = (getTranslationX() - leftPadding) / (((View) parent).getMeasuredWidth() - leftPadding - rightPadding - getMeasuredWidth());
            savedRelativePositionY = (getTranslationY() - maxTop) / (((View) parent).getMeasuredHeight() - maxBottom - maxTop - getMeasuredHeight());
            savedRelativePositionX = Math.max(0, Math.min(1, savedRelativePositionX));
            savedRelativePositionY = Math.max(0, Math.min(1, savedRelativePositionY));
        } else {
            savedRelativePositionX = -1f;
            savedRelativePositionY = -1f;
        }
    }

    public void restoreRelativePosition() {
        updatePadding();
        if (savedRelativePositionX >= 0 && !switchingToFloatingMode) {
            setRelativePositionInternal(savedRelativePositionX, savedRelativePositionY, getMeasuredWidth(), getMeasuredHeight(), true);
            savedRelativePositionX = -1f;
            savedRelativePositionY = -1f;
        }
    }
}
