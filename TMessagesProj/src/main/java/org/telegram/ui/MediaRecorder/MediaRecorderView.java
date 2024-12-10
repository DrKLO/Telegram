package org.telegram.ui.MediaRecorder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GestureDetectorFixDoubleTap;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.touchSlop;

public class MediaRecorderView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final long STATE_SWITCH_DURATION_MS = 350L;
    private static final float DISMISS_RATIO = 0.10F; // translation of 10% of view height makes recorder shrink

    protected final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();

    protected final Context context;
    private final Params params;

//    private final MediaPreviewView mediaPreviewer;

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

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
    }

    public MediaRecorderView(Context context, Params params, boolean isPreviewMode) {
        super(context);

        this.context = context;
        this.params = params;

        setFocusable(true);

        gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureListener());

//        mediaPreviewer = new MediaPreviewView(this.context);
//        addView(mediaPreviewer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setBackgroundColor(Color.argb(150, 255, 0, 0));

        this.state = isPreviewMode ? SHRINK_STATE : EXPANDED_STATE;

//        if (isPreviewMode) {
//            ImageView cameraIcon = new ImageView(this.context);
//            cameraIcon.setScaleType(ImageView.ScaleType.CENTER);
//            cameraIcon.setBackgroundColor(context.getResources().getColor(android.R.color.holo_blue_light));
//            addView(cameraIcon, LayoutHelper.createFrame(80, 80));
//        }
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

        private float ty, sty;

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
            ty = getTranslationY() - distanceY;

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

    private static class MediaPreviewView extends FrameLayout {

        private StoryRecorder.Touchable previewTouchable;

        boolean skipValidation;

        /* Views */
        private ImageView cameraViewThumb;

        MediaPreviewView(Context context) {
            super(context);

            cameraViewThumb = new ImageView(context);
            cameraViewThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cameraViewThumb.setOnClickListener(v -> {
//                if (noCameraPermission) {
//                    requestCameraPermission(true);
//                }
            });
            cameraViewThumb.setClickable(true);
            cameraViewThumb.setImageDrawable(getCameraThumb());

            addView(cameraViewThumb, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (previewTouchable != null) {
                previewTouchable.onTouch(event);
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);


        }

        private final Rect leftExclRect = new Rect();
        private final Rect rightExclRect = new Rect();

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final int w = right - left;
                final int h = bottom - top;
                leftExclRect.set(0, h - dp(120), dp(40), h);
                rightExclRect.set(w - dp(40), h - dp(120), w, h);
                setSystemGestureExclusionRects(Arrays.asList(leftExclRect, rightExclRect));
            }
        }

        @Override
        public void invalidate() {
            if (skipValidation) {
                return;
            }
            super.invalidate();
        }

        private Drawable getCameraThumb() {
            Bitmap bitmap = null;
            try {
                File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            } catch (Throwable ignore) {
            }
            if (bitmap != null) {
                return new BitmapDrawable(bitmap);
            } else {
                return getContext().getResources().getDrawable(R.drawable.icplaceholder);
            }
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
