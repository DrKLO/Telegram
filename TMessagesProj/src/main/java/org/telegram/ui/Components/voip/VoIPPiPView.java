package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.VoIPFragment;

public class VoIPPiPView implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate {

    public final static int ANIMATION_ENTER_TYPE_SCALE = 0;
    public final static int ANIMATION_ENTER_TYPE_TRANSITION = 1;
    public final static int ANIMATION_ENTER_TYPE_NONE = 3;

    private final static float SCALE_NORMAL = 0.25f;
    private final static float SCALE_EXPANDED = 0.4f;

    public FrameLayout windowView;
    public static boolean switchingToPip = false;
    FloatingView floatingView;

    private static VoIPPiPView instance;
    private static VoIPPiPView expandedInstance;
    public boolean expanded;
    private boolean expandedAnimationInProgress;
    private WindowManager windowManager;
    public WindowManager.LayoutParams windowLayoutParams;

    public final int parentWidth;
    public final int parentHeight;

    public static int topInset;
    public static int bottomInset;

    ImageView closeIcon;
    ImageView enlargeIcon;
    View topShadow;

    ValueAnimator expandAnimator;

    public final VoIPTextureView currentUserTextureView;
    public final VoIPTextureView callingUserTextureView;

    float progressToCameraMini;
    ValueAnimator animatorToCameraMini;
    ValueAnimator.AnimatorUpdateListener animatorToCameraMiniUpdater = valueAnimator -> {
        progressToCameraMini = (float) valueAnimator.getAnimatedValue();
        floatingView.invalidate();
    };

    float[] point = new float[2];

    public int xOffset;
    public int yOffset;

    boolean currentUserIsVideo;
    boolean callingUserIsVideo;

    float startX;
    float startY;
    boolean moving;
    long startTime;
    int animationIndex = -1;

    private int currentAccount;

    Runnable collapseRunnable = new Runnable() {
        @Override
        public void run() {
            if (instance != null) {
                instance.floatingView.expand(false);
            }
        }
    };

    AnimatorSet moveToBoundsAnimator;
    private ValueAnimator.AnimatorUpdateListener updateXlistener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            float x = (float) valueAnimator.getAnimatedValue();
            windowLayoutParams.x = (int) x;
            if (windowView.getParent() != null) {
                windowManager.updateViewLayout(windowView, windowLayoutParams);
            }
        }
    };

    private ValueAnimator.AnimatorUpdateListener updateYlistener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            float y = (float) valueAnimator.getAnimatedValue();
            windowLayoutParams.y = (int) y;
            if (windowView.getParent() != null) {
                windowManager.updateViewLayout(windowView, windowLayoutParams);
            }
        }
    };

    public static void show(Activity activity, int account, int parentWidth, int parentHeight, int animationType) {
        if (instance != null || VideoCapturerDevice.eglBase == null) {
            return;
        }
        WindowManager.LayoutParams windowLayoutParams = createWindowLayoutParams(activity, parentWidth, parentHeight, SCALE_NORMAL);
        instance = new VoIPPiPView(activity, parentWidth, parentHeight, false);

        WindowManager wm;
        if (AndroidUtilities.checkInlinePermissions(activity)) {
            wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        } else {
            wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        }
        instance.currentAccount = account;
        instance.windowManager = wm;
        instance.windowLayoutParams = windowLayoutParams;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("voippipconfig", Context.MODE_PRIVATE);
        float x = preferences.getFloat("relativeX", 1f);
        float y = preferences.getFloat("relativeY", 0f);

        instance.setRelativePosition(x, y);
        NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.didEndCall);
        wm.addView(instance.windowView, windowLayoutParams);

        instance.currentUserTextureView.renderer.init(VideoCapturerDevice.eglBase.getEglBaseContext(), null);
        instance.callingUserTextureView.renderer.init(VideoCapturerDevice.eglBase.getEglBaseContext(), null);

        if (animationType == ANIMATION_ENTER_TYPE_SCALE) {
            instance.windowView.setScaleX(0.5f);
            instance.windowView.setScaleY(0.5f);
            instance.windowView.setAlpha(0f);
            instance.windowView.animate().alpha(1f).scaleY(1f).scaleX(1f).start();

            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().setSinks(instance.currentUserTextureView.renderer, instance.callingUserTextureView.renderer);
            }

        } else if (animationType == ANIMATION_ENTER_TYPE_TRANSITION) {
            instance.windowView.setAlpha(0f);

            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().setBackgroundSinks(instance.currentUserTextureView.renderer, instance.callingUserTextureView.renderer);
            }
        }
    }

    private static WindowManager.LayoutParams createWindowLayoutParams(Context context, int parentWidth, int parentHeight, float scale) {
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();

        int topPadding = (int) (parentHeight * SCALE_EXPANDED * 1.05f - parentHeight * SCALE_EXPANDED) / 2;
        int leftPadding = (int) (parentWidth * SCALE_EXPANDED * 1.05f - parentWidth * SCALE_EXPANDED) / 2;

        windowLayoutParams.height = (int) (parentHeight * scale + 2 * topPadding);
        windowLayoutParams.width = (int) (parentWidth * scale + 2 * leftPadding);

        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;

        if (AndroidUtilities.checkInlinePermissions(context)) {
            if (Build.VERSION.SDK_INT >= 26) {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        } else {
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        }

        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        return windowLayoutParams;
    }

    public static void prepareForTransition() {
        if (expandedInstance != null) {
            instance.expandAnimator.cancel();
        }
    }

    public static void finish() {
        if (switchingToPip) {
            return;
        }
        if (expandedInstance != null) {
            expandedInstance.finishInternal();
        }
        if (instance != null) {
            instance.finishInternal();
        }
        expandedInstance = null;
        instance = null;
    }

    public static boolean isExpanding() {
        return instance.expanded;
    }


    private void setRelativePosition(float x, float y) {
        float width = AndroidUtilities.displaySize.x;
        float height = AndroidUtilities.displaySize.y;

        float leftPadding = AndroidUtilities.dp(16);
        float rightPadding = AndroidUtilities.dp(16);
        float topPadding = AndroidUtilities.dp(60);
        float bottomPadding = AndroidUtilities.dp(16);

        float widthNormal = parentWidth * SCALE_NORMAL;
        float heightNormal = parentHeight * SCALE_NORMAL;

        float floatingWidth = floatingView.getMeasuredWidth() == 0 ? widthNormal : floatingView.getMeasuredWidth();
        float floatingHeight = floatingView.getMeasuredWidth() == 0 ? heightNormal : floatingView.getMeasuredHeight();

        windowLayoutParams.x = (int) (x * (width - leftPadding - rightPadding - floatingWidth) - (xOffset - leftPadding));
        windowLayoutParams.y = (int) (y * (height - topPadding - bottomPadding - floatingHeight) - (yOffset - topPadding));

        if (windowView.getParent() != null) {
            windowManager.updateViewLayout(windowView, windowLayoutParams);
        }
    }

    public static VoIPPiPView getInstance() {
        if (expandedInstance != null) {
            return expandedInstance;
        }
        return instance;
    }

    public VoIPPiPView(@NonNull Context context, int parentWidth, int parentHeight, boolean expanded) {
        this.parentWidth = parentWidth;
        this.parentHeight = parentHeight;

        yOffset = (int) (parentHeight * SCALE_EXPANDED * 1.05f - parentHeight * SCALE_EXPANDED) / 2;
        xOffset = (int) (parentWidth * SCALE_EXPANDED * 1.05f - parentWidth * SCALE_EXPANDED) / 2;

        Drawable outerDrawable = ContextCompat.getDrawable(context, R.drawable.calls_pip_outershadow);
        windowView = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.scale(floatingView.getScaleX(), floatingView.getScaleY(), floatingView.getLeft() + floatingView.getPivotX(), floatingView.getTop() + floatingView.getPivotY());
                outerDrawable.setBounds(
                        floatingView.getLeft() - AndroidUtilities.dp(2),
                        floatingView.getTop() - AndroidUtilities.dp(2),
                        floatingView.getRight() + AndroidUtilities.dp(2),
                        floatingView.getBottom() + AndroidUtilities.dp(2)
                );
                outerDrawable.draw(canvas);
                canvas.restore();
                super.onDraw(canvas);

            }
        };
        windowView.setWillNotDraw(false);
        windowView.setPadding(xOffset, yOffset, xOffset, yOffset);
        floatingView = new FloatingView(context);


        callingUserTextureView = new VoIPTextureView(context, false, true);
        callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_NONE;
        currentUserTextureView = new VoIPTextureView(context, false, true);
        currentUserTextureView.renderer.setMirror(true);

        floatingView.addView(callingUserTextureView);
        floatingView.addView(currentUserTextureView);
        floatingView.setBackgroundColor(Color.GRAY);
        windowView.addView(floatingView);
        windowView.setClipChildren(false);
        windowView.setClipToPadding(false);

        if (expanded) {
            topShadow = new View(context);
            topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.3f)), Color.TRANSPARENT}));
            floatingView.addView(topShadow, FrameLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(60));

            closeIcon = new ImageView(context);
            closeIcon.setImageResource(R.drawable.pip_close);
            closeIcon.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            closeIcon.setContentDescription(LocaleController.getString("Close", R.string.Close));
            floatingView.addView(closeIcon, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 4, 4, 4, 0));

            enlargeIcon = new ImageView(context);
            enlargeIcon.setImageResource(R.drawable.pip_enlarge);
            enlargeIcon.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            enlargeIcon.setContentDescription(LocaleController.getString("Open", R.string.Open));
            floatingView.addView(enlargeIcon, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.LEFT, 4, 4, 4, 0));

            closeIcon.setOnClickListener((v) -> {
                VoIPService service = VoIPService.getSharedInstance();
                if (service != null) {
                    service.hangUp();
                } else {
                    finish();
                }
            });


            enlargeIcon.setOnClickListener((v) -> {
                if (context instanceof LaunchActivity && !ApplicationLoader.mainInterfacePaused) {
                    VoIPFragment.show((Activity) context, currentAccount);
                } else if (context instanceof LaunchActivity) {
                    Intent intent = new Intent(context, LaunchActivity.class);
                    intent.setAction("voip");
                    context.startActivity(intent);
                }
            });
        }

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.registerStateListener(this);
        }
        updateViewState();
    }

    private void finishInternal() {
        currentUserTextureView.renderer.release();
        callingUserTextureView.renderer.release();
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }
        windowView.setVisibility(View.GONE);
        if (windowView.getParent() != null) {
            floatingView.getRelativePosition(point);
            float x = Math.min(1f, Math.max(0f, point[0]));
            float y = Math.min(1f, Math.max(0f, point[1]));
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("voippipconfig", Context.MODE_PRIVATE);
            preferences.edit()
                    .putFloat("relativeX", x)
                    .putFloat("relativeY", y)
                    .apply();

            try {
                windowManager.removeView(windowView);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didEndCall);
    }

    @Override
    public void onStateChanged(int state) {
        if (state == VoIPService.STATE_ENDED || state == VoIPService.STATE_BUSY || state == VoIPService.STATE_FAILED || state == VoIPService.STATE_HANGING_UP) {
            AndroidUtilities.runOnUIThread(VoIPPiPView::finish, 200);
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            finish();
            return;
        }
        if (state == VoIPService.STATE_ESTABLISHED && !service.isVideoAvailable()) {
            finish();
            return;
        }
        updateViewState();
    }

    @Override
    public void onSignalBarsCountChanged(int count) {

    }

    @Override
    public void onAudioSettingsChanged() {

    }

    @Override
    public void onMediaStateUpdated(int audioState, int videoState) {
        updateViewState();
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        updateViewState();
    }

    @Override
    public void onVideoAvailableChange(boolean isAvailable) {

    }

    @Override
    public void onScreenOnChange(boolean screenOn) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (!screenOn && currentUserIsVideo) {
            service.setVideoState(false, Instance.VIDEO_STATE_PAUSED);
        } else if (screenOn && service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
            service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
        }
    }

    private void updateViewState() {
        boolean animated = floatingView.getMeasuredWidth() != 0;
        boolean callingUserWasVideo = callingUserIsVideo;

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE;
            currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;
            currentUserTextureView.renderer.setMirror(service.isFrontFaceCamera());
            currentUserTextureView.setIsScreencast(service.isScreencast());
            currentUserTextureView.setScreenshareMiniProgress(1.0f, false);
        }

        if (!animated) {
            progressToCameraMini = callingUserIsVideo ? 1f : 0f;
        } else {
            if (callingUserWasVideo != callingUserIsVideo) {
                if (animatorToCameraMini != null) {
                    animatorToCameraMini.cancel();
                }
                animatorToCameraMini = ValueAnimator.ofFloat(progressToCameraMini, callingUserIsVideo ? 1f : 0f);
                animatorToCameraMini.addUpdateListener(animatorToCameraMiniUpdater);
                animatorToCameraMini.setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT);
                animatorToCameraMini.start();
            }
        }

    }

    public void onTransitionEnd() {
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().swapSinks();
        }
    }

    public void onPause() {
        if (windowLayoutParams.type == WindowManager.LayoutParams.LAST_APPLICATION_WINDOW) {
            VoIPService service = VoIPService.getSharedInstance();
            if (currentUserIsVideo) {
                service.setVideoState(false, Instance.VIDEO_STATE_PAUSED);
            }
        }
    }


    public void onResume() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
            service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didEndCall) {
            finish();
        }
    }

    private class FloatingView extends FrameLayout {

        float touchSlop;

        float leftPadding;
        float rightPadding;
        float topPadding;
        float bottomPadding;

        public FloatingView(@NonNull Context context) {
            super(context);

            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), 1f / view.getScaleX() * AndroidUtilities.dp(4));
                    }
                });
                setClipToOutline(true);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            leftPadding = AndroidUtilities.dp(16);
            rightPadding = AndroidUtilities.dp(16);
            topPadding = AndroidUtilities.dp(60);
            bottomPadding = AndroidUtilities.dp(16);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            currentUserTextureView.setPivotX(callingUserTextureView.getMeasuredWidth());
            currentUserTextureView.setPivotY(callingUserTextureView.getMeasuredHeight());
            currentUserTextureView.setTranslationX(-AndroidUtilities.dp(4) * (1f / getScaleX()) * progressToCameraMini);
            currentUserTextureView.setTranslationY(-AndroidUtilities.dp(4) * (1f / getScaleY()) * progressToCameraMini);
            currentUserTextureView.setRoundCorners(AndroidUtilities.dp(8) * (1f / getScaleY()) * progressToCameraMini);
            currentUserTextureView.setScaleX(0.4f + 0.6f * (1f - progressToCameraMini));
            currentUserTextureView.setScaleY(0.4f + 0.6f * (1f - progressToCameraMini));
            currentUserTextureView.setAlpha(Math.min(1f, 1f - progressToCameraMini));
            super.dispatchDraw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (expandedAnimationInProgress || switchingToPip || instance == null) {
                return false;
            }
            AndroidUtilities.cancelRunOnUIThread(collapseRunnable);
            float x = event.getRawX();
            float y = event.getRawY();
            ViewParent parent = getParent();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = x;
                    startY = y;
                    startTime = System.currentTimeMillis();
                    //  animate().scaleY(1.05f).scaleX(1.05f).setDuration(150).start();
                    if (moveToBoundsAnimator != null) {
                        moveToBoundsAnimator.cancel();
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = x - startX;
                    float dy = y - startY;
                    if (!moving && (dx * dx + dy * dy) > touchSlop * touchSlop) {
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        moving = true;
                        startX = x;
                        startY = y;
                        dx = 0;
                        dy = 0;
                    }
                    if (moving) {
                        windowLayoutParams.x += dx;
                        windowLayoutParams.y += dy;
                        startX = x;
                        startY = y;
                        if (windowView.getParent() != null) {
                            windowManager.updateViewLayout(windowView, windowLayoutParams);
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    //     animate().scaleX(1f).scaleY(1f).start();
                    if (moveToBoundsAnimator != null) {
                        moveToBoundsAnimator.cancel();
                    }
                    if (event.getAction() == MotionEvent.ACTION_UP && !moving && System.currentTimeMillis() - startTime < 150) {
                        Context context = getContext();
                        if (context instanceof LaunchActivity && !ApplicationLoader.mainInterfacePaused) {
                            VoIPFragment.show((Activity) context, currentAccount);
                        } else if (context instanceof LaunchActivity) {
                            Intent intent = new Intent(context, LaunchActivity.class);
                            intent.setAction("voip");
                            context.startActivity(intent);
                        }
                        moving = false;
                        return false;
                    }
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false);

                        int parentWidth = AndroidUtilities.displaySize.x;
                        int parentHeight = AndroidUtilities.displaySize.y + topInset;

                        float maxTop = topPadding;
                        float maxBottom = bottomPadding;

                        float left = windowLayoutParams.x + floatingView.getLeft();
                        float right = left + floatingView.getMeasuredWidth();
                        float top = windowLayoutParams.y + floatingView.getTop();
                        float bottom = top + floatingView.getMeasuredHeight();

                        moveToBoundsAnimator = new AnimatorSet();

                        if (left < leftPadding) {
                            ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.x, leftPadding - floatingView.getLeft());
                            animator.addUpdateListener(updateXlistener);
                            moveToBoundsAnimator.playTogether(animator);
                        } else if (right > parentWidth - rightPadding) {
                            ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.x, parentWidth - floatingView.getRight() - rightPadding);
                            animator.addUpdateListener(updateXlistener);
                            moveToBoundsAnimator.playTogether(animator);
                        }

                        if (top < maxTop) {
                            ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.y, maxTop - floatingView.getTop());
                            animator.addUpdateListener(updateYlistener);
                            moveToBoundsAnimator.playTogether(animator);
                        } else if (bottom > parentHeight - maxBottom) {
                            ValueAnimator animator = ValueAnimator.ofFloat(windowLayoutParams.y, parentHeight - floatingView.getMeasuredHeight() - maxBottom);
                            animator.addUpdateListener(updateYlistener);
                            moveToBoundsAnimator.playTogether(animator);
                        }
                        moveToBoundsAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
                        moveToBoundsAnimator.start();
                    }
                    moving = false;
                    if (instance.expanded) {
                        AndroidUtilities.runOnUIThread(collapseRunnable, 3000);
                    }
                    break;
            }
            return true;
        }

        private void getRelativePosition(float[] point) {
            float width = AndroidUtilities.displaySize.x;
            float height = AndroidUtilities.displaySize.y;

            point[0] = (windowLayoutParams.x + floatingView.getLeft() - leftPadding) / (width - leftPadding - rightPadding - floatingView.getMeasuredWidth());
            point[1] = (windowLayoutParams.y + floatingView.getTop() - topPadding) / (height - topPadding - bottomPadding - floatingView.getMeasuredHeight());
            point[0] = Math.min(1f, Math.max(0, point[0]));
            point[1] = Math.min(1f, Math.max(0, point[1]));
        }

        private void expand(boolean expanded) {
            AndroidUtilities.cancelRunOnUIThread(collapseRunnable);
            if (instance == null || expandedAnimationInProgress || instance.expanded == expanded) {
                return;
            }
            instance.expanded = expanded;

            float widthNormal = parentWidth * SCALE_NORMAL + 2 * xOffset;
            float heightNormal = parentHeight * SCALE_NORMAL + 2 * yOffset;

            float widthExpanded = parentWidth * SCALE_EXPANDED + 2 * xOffset;
            float heightExpanded = parentHeight * SCALE_EXPANDED + 2 * yOffset;

            expandedAnimationInProgress = true;
            if (expanded) {
                WindowManager.LayoutParams layoutParams = createWindowLayoutParams(instance.windowView.getContext(), parentWidth, parentHeight, SCALE_EXPANDED);
                VoIPPiPView pipViewExpanded = new VoIPPiPView(getContext(), parentWidth, parentHeight, true);

                getRelativePosition(point);
                float cX = point[0];
                float cY = point[1];

                layoutParams.x = (int) (windowLayoutParams.x - (widthExpanded - widthNormal) * cX);
                layoutParams.y = (int) (windowLayoutParams.y - (heightExpanded - heightNormal) * cY);

                windowManager.addView(pipViewExpanded.windowView, layoutParams);
                pipViewExpanded.windowView.setAlpha(1f);
                pipViewExpanded.windowLayoutParams = layoutParams;
                pipViewExpanded.windowManager = windowManager;
                expandedInstance = pipViewExpanded;

                swapRender(instance, expandedInstance);

                float scale = SCALE_NORMAL / SCALE_EXPANDED * floatingView.getScaleX();

                pipViewExpanded.floatingView.setPivotX(cX * parentWidth * SCALE_EXPANDED);
                pipViewExpanded.floatingView.setPivotY(cY * parentHeight * SCALE_EXPANDED);
                pipViewExpanded.floatingView.setScaleX(scale);
                pipViewExpanded.floatingView.setScaleY(scale);
                expandedInstance.topShadow.setAlpha(0f);
                expandedInstance.closeIcon.setAlpha(0f);
                expandedInstance.enlargeIcon.setAlpha(0f);

                AndroidUtilities.runOnUIThread(() -> {
                    if (expandedInstance == null) {
                        return;
                    }

                    windowView.setAlpha(0f);
                    try {
                        windowManager.removeView(windowView);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    animate().cancel();

                    float animateToScale = 1f;

                    showUi(true);
                    ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                    valueAnimator.addUpdateListener(a -> {
                        float v = (float) a.getAnimatedValue();
                        float sc = scale * (1f - v) + animateToScale * v;
                        pipViewExpanded.floatingView.setScaleX(sc);
                        pipViewExpanded.floatingView.setScaleY(sc);
                        pipViewExpanded.floatingView.invalidate();
                        pipViewExpanded.windowView.invalidate();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            pipViewExpanded.floatingView.invalidateOutline();
                        }
                    });
                    valueAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            expandedAnimationInProgress = false;
                        }
                    });
                    valueAnimator.setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT);
                    valueAnimator.start();
                    expandAnimator = valueAnimator;
                }, 64);
            } else {
                if (expandedInstance == null) {
                    return;
                }
                expandedInstance.floatingView.getRelativePosition(point);
                float cX = point[0];
                float cY = point[1];

                instance.windowLayoutParams.x = (int) (expandedInstance.windowLayoutParams.x + (widthExpanded - widthNormal) * cX);
                instance.windowLayoutParams.y = (int) (expandedInstance.windowLayoutParams.y + (heightExpanded - heightNormal) * cY);

                float scale = SCALE_NORMAL / SCALE_EXPANDED * floatingView.getScaleX();

                expandedInstance.floatingView.setPivotX(cX * parentWidth * SCALE_EXPANDED);
                expandedInstance.floatingView.setPivotY(cY * parentHeight * SCALE_EXPANDED);

                showUi(false);
                ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
                valueAnimator.addUpdateListener(a -> {
                    float v = (float) a.getAnimatedValue();
                    float sc = (1f - v) + scale * v;
                    if (expandedInstance != null) {
                        expandedInstance.floatingView.setScaleX(sc);
                        expandedInstance.floatingView.setScaleY(sc);
                        expandedInstance.floatingView.invalidate();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            expandedInstance.floatingView.invalidateOutline();
                        }
                        expandedInstance.windowView.invalidate();
                    }
                });
                valueAnimator.setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT);
                valueAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (expandedInstance == null) {
                            return;
                        }
                        swapRender(expandedInstance, instance);
                        instance.windowView.setAlpha(1f);
                        windowManager.addView(instance.windowView, instance.windowLayoutParams);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (instance == null || expandedInstance == null) {
                                return;
                            }
                            expandedInstance.windowView.setAlpha(0);
                            expandedInstance.finishInternal();
                            expandedAnimationInProgress = false;
                            if (expanded) {
                                AndroidUtilities.runOnUIThread(collapseRunnable, 3000);
                            }
                        }, 64);
                    }
                });
                valueAnimator.start();
                expandAnimator = valueAnimator;
            }
        }

        private void showUi(boolean show) {
            if (expandedInstance == null) {
                return;
            }
            if (show) {
                expandedInstance.topShadow.setAlpha(0f);
                expandedInstance.closeIcon.setAlpha(0f);
                expandedInstance.enlargeIcon.setAlpha(0f);
            }
            expandedInstance.topShadow.animate().alpha(show ? 1f : 0).setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            expandedInstance.closeIcon.animate().alpha(show ? 1f : 0).setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            expandedInstance.enlargeIcon.animate().alpha(show ? 1f : 0).setDuration(300).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }

        private void swapRender(VoIPPiPView from, VoIPPiPView to) {
            to.currentUserTextureView.setStub(from.currentUserTextureView);
            to.callingUserTextureView.setStub(from.callingUserTextureView);
            from.currentUserTextureView.renderer.release();
            from.callingUserTextureView.renderer.release();
            if (VideoCapturerDevice.eglBase == null) {
                return;
            }
            to.currentUserTextureView.renderer.init(VideoCapturerDevice.eglBase.getEglBaseContext(), null);
            to.callingUserTextureView.renderer.init(VideoCapturerDevice.eglBase.getEglBaseContext(), null);

            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().setSinks(to.currentUserTextureView.renderer, to.callingUserTextureView.renderer);
            }
        }
    }
}
