package org.telegram.ui.MediaRecorder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GestureDetectorFixDoubleTap;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.touchSlop;

public class ChatMediaRecorderView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final long STATE_SWITCH_DURATION_MS = 350L;
    private static final float DISMISS_RATIO = 0.10F; // translation of 10% of view height makes recorder shrink
    private static final float CAMERA_ICON_ANIMATION_RATIO = 4;

    protected final Theme.ResourcesProvider resourcesProvider;

    protected final Context context;
    private final Params params;

    @IntDef({SHRINK_STATE, EXPANDED_STATE, SHRINKING_STATE, EXPANDING_STATE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    public static final int SHRINK_STATE = 0;
    public static final int SHRINKING_STATE = 1;
    public static final int EXPANDED_STATE = 2;
    public static final int EXPANDING_STATE = 3;

    private int state;
    private float animationProgress = 0F;

    @State
    public int getViewState() {
        return state;
    }

    public boolean isAnimating() {
        return state == SHRINKING_STATE || state == EXPANDING_STATE;
    }

    public boolean isExpanded() {
        return state == EXPANDED_STATE;
    }

    public boolean isExpanding() {
        return state == EXPANDING_STATE;
    }

    public boolean isShrink() {
        return state == SHRINK_STATE;
    }

    public boolean isShrinking() {
        return state == SHRINKING_STATE;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    private ValueAnimator openCloseAnimator;

    private final GestureDetectorFixDoubleTap gestureDetector;

    // View params before extended
    private float shrinkWidth = 0F;
    private float shrinkHeight = 0F;
    private float shrinkY = 0F;
    private float shrinkX = 0F;
    private float shrinkTranslationY = 0F;

    private float scale = 1F;
    private float dismissProgress = 0F;

    private final ImageView cameraIcon;
    private final Drawable cameraDrawable;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
    }

    public ChatMediaRecorderView(Context context, Params params, boolean isShrinkMode, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.context = context;
        this.params = params;

        this.resourcesProvider = resourcesProvider;

        setFocusable(true);

        gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureListener());

        setBackgroundColor(Color.argb(150, 255, 0, 0));

        this.state = isShrinkMode ? SHRINK_STATE : EXPANDED_STATE;

        cameraDrawable = ContextCompat.getDrawable(context, R.drawable.instant_camera).mutate();
        cameraIcon = new ImageView(this.context);
        cameraIcon.setScaleType(ImageView.ScaleType.CENTER);
        cameraIcon.setImageDrawable(cameraDrawable);

        if (this.state == SHRINK_STATE) {
            addView(cameraIcon, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    public boolean processTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (dismissProgress <= DISMISS_RATIO) {
                animateViewBack();
            } else {
                shrinkView();
            }
            dismissProgress = 0F;
        }
        return true;
    }

    public void updateColors() {
        Theme.setDrawableColor(cameraDrawable, Theme.getColor(Theme.key_dialogCameraIcon, resourcesProvider));
    }

    public void shrinkView() {
        if (state != EXPANDED_STATE) {
            return;
        }

        final float startWidth = getWidth();
        final float startHeight = getHeight();

        final float fromX = getX();
        final float fromY = getY();

        final float fromScale = scale;

        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }

        openCloseAnimator = ObjectAnimator.ofFloat(0F, 1F);
        openCloseAnimator.setDuration(200);
        openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);

        addView(cameraIcon);

        animationProgress = 0F;
        openCloseAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            animationProgress = animatedValue;

            setY(AndroidUtilities.lerp(fromY, shrinkY, animatedValue));
            setX(AndroidUtilities.lerp(fromX, shrinkX, animatedValue));

            setTranslationY(AndroidUtilities.lerp(getTranslationY(), shrinkTranslationY, animatedValue));

            scale = AndroidUtilities.lerp(fromScale, 1F, animatedValue);
            setScaleX(scale);
            setScaleY(scale);

            ViewGroup.LayoutParams lp = getLayoutParams();
            lp.width = (int) AndroidUtilities.lerp(startWidth, shrinkWidth, animatedValue);
            lp.height = (int) AndroidUtilities.lerp(startHeight, shrinkHeight, animatedValue);

            cameraIcon.setAlpha(Utilities.clamp(animatedValue * CAMERA_ICON_ANIMATION_RATIO, 1F, 0F));

            requestLayout();
        });
        openCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state = SHRINK_STATE;

                scale = 1F;
                shrinkWidth = 0F;
                shrinkHeight = 0F;
                shrinkY = 0F;
                shrinkX = 0F;
                shrinkTranslationY = 0F;

                if (Build.VERSION.SDK_INT >= 21 && params.isFullScreen) {
                    setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                }

                AndroidUtilities.unlockOrientation((Activity) getContext());
            }

            @Override
            public void onAnimationStart(Animator animation) {
                state = SHRINKING_STATE;
            }
        });
        openCloseAnimator.start();

        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    public void expandView(boolean animated, float targetX, float targetY, float targetWidth, float targetHeight, Runnable onOpened) {
        if (state != SHRINK_STATE) {
            return;
        }

        final float startWidth = this.shrinkWidth = getWidth();
        final float startHeight = this.shrinkHeight = getHeight();
        shrinkTranslationY = getTranslationY();

        final float fromX = this.shrinkX = getX();
        final float fromY = this.shrinkY = getY();

        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator = null;
        }

        openCloseAnimator = ObjectAnimator.ofFloat(0F, 1F);
        openCloseAnimator.setDuration(STATE_SWITCH_DURATION_MS);
        openCloseAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openCloseAnimator.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                state = EXPANDED_STATE;
                onOpened.run();

                removeView(cameraIcon);

                if (Build.VERSION.SDK_INT >= 21 && params.isFullScreen) {
                    setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
                }

                AndroidUtilities.lockOrientation((Activity) getContext(), ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                state = EXPANDING_STATE;
            }
        });

        animationProgress = 0F;
        openCloseAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            animationProgress = animatedValue;

            setY(AndroidUtilities.lerp(fromY, targetY, animatedValue));
            setX(AndroidUtilities.lerp(fromX, targetX, animatedValue));

            cameraIcon.setAlpha(Utilities.clamp(1F - animatedValue * CAMERA_ICON_ANIMATION_RATIO, 1F, 0F));

            ViewGroup.LayoutParams lp = getLayoutParams();
            lp.width = (int) AndroidUtilities.lerp(startWidth, targetWidth, animatedValue);
            lp.height = (int) AndroidUtilities.lerp(startHeight, targetHeight, animatedValue);

            requestLayout();
        });
        openCloseAnimator.start();

        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private AnimatorSet viewBackAnimator;

    private void animateViewBack() {
        if (isAnimating()) {
            return;
        }

        if (viewBackAnimator != null) {
            viewBackAnimator.cancel();
            viewBackAnimator = null;
        }
        viewBackAnimator = new AnimatorSet();

        ValueAnimator translationYAnimator = ObjectAnimator.ofFloat(getTranslationY(), 0F);
        translationYAnimator.addUpdateListener(animation -> setTranslationY((float) animation.getAnimatedValue()));

        ValueAnimator scaleAnimator = ObjectAnimator.ofFloat(scale, 1F);
        scaleAnimator.addUpdateListener(animation -> {
            scale = (float) animation.getAnimatedValue();
            setScaleX(scale);
            setScaleY(scale);
        });

        viewBackAnimator.playTogether(translationYAnimator, scaleAnimator);
        viewBackAnimator.setDuration(340);
        viewBackAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        viewBackAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                state = EXPANDED_STATE;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                state = EXPANDING_STATE;
            }
        });
        viewBackAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (openCloseAnimator != null) {
            openCloseAnimator.cancel();
            openCloseAnimator.removeAllUpdateListeners();
            openCloseAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    private class GestureListener extends GestureDetectorFixDoubleTap.OnGestureListener {

        private boolean scrollingY = false;

        private float sty;

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            sty = 0;
            return false;
        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            scrollingY = false;
            return false;
        }

        @Override
        public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            boolean handled = false;

            if (scrollingY && Math.abs(getTranslationY()) >= dp(1)) {
                if (velocityY > 0 && Math.abs(velocityY) > 2000 && Math.abs(velocityY) > Math.abs(velocityX) || dismissProgress > DISMISS_RATIO) {
                    shrinkView();
                } else {
                    animateViewBack();
                }
                dismissProgress = 0F;
                handled = true;
            }

            scrollingY = false;

            return handled;
        }

        @Override
        public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            if (state == SHRINKING_STATE) {
                return false;
            }

            sty += distanceY;
            if (!scrollingY && Math.abs(sty) >= touchSlop) {
                scrollingY = true;
            }
            if (scrollingY) {
                return handleYScroll(distanceY);
            }

            return false;
        }

        private boolean handleYScroll(float distanceY) {
            float ty = getTranslationY() - distanceY;

            if (ty >= 0) {
                dismissProgress = Utilities.clamp(ty / getMeasuredHeight(), 1, 0);
                scale = Utilities.clamp(1F - dismissProgress, 1F, 0.8F);

                setTranslationY(ty);
                setScaleX(scale);
                setScaleY(scale);

                return true;
            }

            return false;
        }
    }

    public static class Params {

        boolean isFullScreen = false;

        public Params(
                boolean isFullScreen
        ) {
            this.isFullScreen = isFullScreen;
        }

    }

    private static void log(String message) {
        Log.i("kirillNay", message);
    }

}
