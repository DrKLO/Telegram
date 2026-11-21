package org.telegram.utils.pip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.media3.common.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GestureDetectorFixDoubleTap;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekSpeedDrawable;
import org.telegram.ui.Components.SimpleFloatPropertyCompat;
import org.telegram.ui.Components.VideoForwardDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating window with the video and its controls. The same view tree is used for
 * the overlay window (TYPE_APPLICATION_OVERLAY) and the in-app window (TYPE_APPLICATION);
 * changing the type is remove + add.
 */
final class FloatingHost {
    private static final float SIDE_PADDING_DP = 16;
    private static final float ROUNDED_CORNERS_DP = 10;
    private static final float MIN_SCALE_FACTOR = 0.75f;

    private static final FloatPropertyCompat<FloatingHost> PIP_X_PROPERTY = new SimpleFloatPropertyCompat<>("pipX", obj -> obj.pipX, (obj, value) -> {
        obj.windowLayoutParams.x = (int) (obj.pipX = value);
        obj.updateWindowLayout();
    }), PIP_Y_PROPERTY = new SimpleFloatPropertyCompat<>("pipY", obj -> obj.pipY, (obj, value) -> {
        obj.windowLayoutParams.y = (int) (obj.pipY = value);
        obj.updateWindowLayout();
    });

    private final PipSession session;
    private final PipContent content;
    private final Activity activity;

    private WindowManager windowManager;
    private final WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
    private boolean isOverlay;
    private boolean isWindowAdded;

    private ViewGroup contentView;
    private FrameLayout contentFrameLayout;
    private @Nullable PipTextureView renderTarget;
    private @Nullable View floatingView;
    private ImageView placeholderView;
    private FrameLayout controlsView;
    private ImageView playPauseButton;
    private VideoProgressView videoProgressView;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetectorFixDoubleTap gestureDetector;
    private boolean isScrolling;
    private boolean isScrollDisallowed;
    private View consumingChild;

    private boolean isShowingControls;
    private ValueAnimator controlsAnimator;
    private boolean postedDismissControls;

    private int pipWidth, pipHeight;
    private float scaleFactor = 1f;
    private float maxScaleFactor = 1.4f;
    private float pipX, pipY;
    private SpringAnimation pipXSpring, pipYSpring;

    private final VideoForwardDrawable videoForwardDrawable = new VideoForwardDrawable(false);
    private SeekSpeedDrawable seekSpeedDrawable;
    private boolean isVideoCompleted;
    private float videoProgress, bufferProgress;
    private boolean isDismissing;

    private boolean canLongClick;
    private final float[] longClickStartPoint = new float[2];
    private final Runnable longClickCallback = this::onLongClick;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (content.isPlaying()) {
                AndroidUtilities.runOnUIThread(this, 500);
            }
        }
    };

    private final Runnable dismissControlsCallback = new Runnable() {
        @Override
        public void run() {
            if (content.isRewinding()) {
                AndroidUtilities.runOnUIThread(this, 1500);
                return;
            }
            toggleControls(isShowingControls = false);
            postedDismissControls = false;
        }
    };

    FloatingHost(PipSession session, Activity activity) {
        this.session = session;
        this.content = session.content;
        this.activity = activity;
    }

    boolean isOverlay() {
        return isOverlay;
    }

    boolean hasFloatingView() {
        return floatingView != null;
    }

    private boolean shouldUseOverlay() {
        return content.isOverlayAllowed() && PipEnvironment.canDrawOverlays(activity);
    }

    static float getCornerRadius() {
        return dp(ROUNDED_CORNERS_DP);
    }

    /* Geometry */

    private static float getRatio(int contentWidth, int contentHeight) {
        if (contentWidth <= 0 || contentHeight <= 0) {
            return 9f / 16f;
        }
        return contentHeight / (float) contentWidth;
    }

    private static int getSuggestedWidth(float ratio) {
        final int min = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        return (int) (min * (ratio >= 1 ? 0.35f : 0.6f));
    }

    private static int getSuggestedHeight(float ratio) {
        return (int) (getSuggestedWidth(ratio) * ratio);
    }

    private float getRatio() {
        return getRatio(content.getContentWidth(), content.getContentHeight());
    }

    private int getSuggestedWidth() {
        return getSuggestedWidth(getRatio());
    }

    private int getSuggestedHeight() {
        return getSuggestedHeight(getRatio());
    }

    private static float sidePadding() {
        return dp(SIDE_PADDING_DP);
    }

    private static float snapX(float x, float width) {
        return x + width / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - width - sidePadding() : sidePadding();
    }

    private static float clampY(float y, float height) {
        return MathUtils.clamp(y, sidePadding(), AndroidUtilities.displaySize.y - height - sidePadding());
    }

    /** Target rect of a new window, in screen coordinates (window y is below the status bar). */
    static void getTargetRect(int contentWidth, int contentHeight, @NonNull RectF out) {
        final float ratio = getRatio(contentWidth, contentHeight);
        final PipConfig config = PipConfig.get();
        final float scale = config.getScaleFactor();
        final float width = getSuggestedWidth(ratio) * scale;
        final float height = getSuggestedHeight(ratio) * scale;
        final float savedX = config.getX(), savedY = config.getY();
        final float x = savedX != -1 ? snapX(savedX, width) : AndroidUtilities.displaySize.x - width - sidePadding();
        final float y = savedY != -1 ? clampY(savedY, height) : sidePadding();
        out.set(x, y + AndroidUtilities.statusBarHeight, x + width, y + AndroidUtilities.statusBarHeight + height);
    }

    void getRect(@NonNull RectF out) {
        if (contentView != null && contentView.isAttachedToWindow()) {
            final int[] location = new int[2];
            contentView.getLocationOnScreen(location);
            out.set(location[0], location[1], location[0] + pipWidth, location[1] + pipHeight);
        } else {
            out.set(pipX, pipY + AndroidUtilities.statusBarHeight, pipX + pipWidth, pipY + AndroidUtilities.statusBarHeight + pipHeight);
        }
    }

    /* Show / hide */

    @Nullable PipTextureView show(@Nullable Bitmap placeholder, boolean animateAppear) {
        final RectF rect = new RectF();
        getTargetRect(content.getContentWidth(), content.getContentHeight(), rect);
        scaleFactor = PipConfig.get().getScaleFactor();
        maxScaleFactor = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - sidePadding() * 2) / (float) getSuggestedWidth();
        pipWidth = (int) rect.width();
        pipHeight = (int) rect.height();
        pipX = rect.left;
        pipY = rect.top - AndroidUtilities.statusBarHeight;
        videoForwardDrawable.setPlayScaleFactor(getRatio() < 1 ? 0.6f : 0.45f);

        createSprings();
        createViews();

        floatingView = content.getFloatingView();
        if (floatingView != null) {
            AndroidUtilities.removeFromParent(floatingView);
            contentFrameLayout.addView(floatingView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            renderTarget = new PipTextureView(activity);
            contentFrameLayout.addView(renderTarget, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        if (placeholder != null && renderTarget != null) {
            placeholderView.setImageBitmap(placeholder);
            placeholderView.setVisibility(View.VISIBLE);
            renderTarget.setFirstFrameCallback(() -> AndroidUtilities.runOnUIThread(this::hidePlaceholder));
        }

        addWindow();
        if (animateAppear) {
            contentView.setAlpha(0f);
            contentView.setScaleX(0.1f);
            contentView.setScaleY(0.1f);
            final AnimatorSet set = new AnimatorSet();
            set.setDuration(250);
            set.setInterpolator(CubicBezierInterpolator.DEFAULT);
            set.playTogether(
                ObjectAnimator.ofFloat(contentView, View.ALPHA, 1f),
                ObjectAnimator.ofFloat(contentView, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(contentView, View.SCALE_Y, 1f)
            );
            set.start();
        }
        invalidateState();
        return renderTarget;
    }

    /**
     * The in-app window is hidden with the activity; its GL layer is destroyed and a paused
     * video would come back black. Keep the last frame as a placeholder until the next one.
     */
    void onAppHidden() {
        if (renderTarget == null || placeholderView == null || !renderTarget.isAvailable()) {
            return;
        }
        try {
            final Bitmap bitmap = renderTarget.getBitmap();
            if (bitmap != null) {
                placeholderView.setImageBitmap(bitmap);
                placeholderView.setVisibility(View.VISIBLE);
                // frames keep coming until the app is stopped, into a layer that is going away:
                // the placeholder goes only with a frame drawn after the app is back
                renderTarget.setFirstFrameCallback(null);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Back in the app: a paused video has to render its current frame into the new layer. */
    void onAppShown() {
        if (renderTarget == null) {
            return;
        }
        final PipTextureView target = renderTarget;
        // after the window has drawn again and the texture is back in a new layer
        target.post(() -> {
            if (renderTarget != target) {
                return;
            }
            if (placeholderView != null && placeholderView.getVisibility() == View.VISIBLE) {
                target.setFirstFrameCallback(() -> AndroidUtilities.runOnUIThread(this::hidePlaceholder));
            }
            if (content.resumesWithApp()) {
                content.play();
            } else if (!content.isPlaying()) {
                content.seekTo(content.getCurrentPosition());
            }
        });
    }

    private void hidePlaceholder() {
        if (placeholderView != null) {
            placeholderView.setVisibility(View.GONE);
            placeholderView.setImageDrawable(null);
        }
    }

    /** Moves the render target out of the window and removes the window (system PiP takes over). */
    @Nullable PipTextureView detachForSystem() {
        final PipTextureView target = renderTarget;
        renderTarget = null;
        cancelGestures();
        removeWindow();
        if (target != null) {
            AndroidUtilities.removeFromParent(target);
        }
        return target;
    }

    /** Back from system PiP: the window is shown again with the same render target. */
    void attachAfterSystem(@NonNull PipTextureView target) {
        renderTarget = target;
        AndroidUtilities.removeFromParent(target);
        contentFrameLayout.addView(target, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addWindow();
        invalidateState();
    }

    void removeForExpand(@NonNull Utilities.Callback<SurfaceTexture> onTexture) {
        isDismissing = true;
        cancelGestures();
        // let the content draw its placeholder above the window first
        AndroidUtilities.runOnUIThread(() -> {
            removeWindow();
            final PipTextureView target = renderTarget;
            renderTarget = null;
            SurfaceTexture texture = null;
            if (target != null) {
                AndroidUtilities.removeFromParent(target);
                texture = target.takeSurfaceTexture();
            }
            onTexture.run(texture);
        }, 100);
    }

    void dismiss(boolean animated, @NonNull Runnable onContentRelease) {
        if (isDismissing) {
            return;
        }
        isDismissing = true;
        cancelGestures();
        if (pipXSpring != null) {
            pipXSpring.cancel();
            pipYSpring.cancel();
        }
        if (!animated || !isWindowAdded) {
            finishDismiss(onContentRelease);
            return;
        }
        final AnimatorSet set = new AnimatorSet();
        set.setDuration(250);
        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
        set.playTogether(
            ObjectAnimator.ofFloat(contentView, View.ALPHA, 0f),
            ObjectAnimator.ofFloat(contentView, View.SCALE_X, 0.1f),
            ObjectAnimator.ofFloat(contentView, View.SCALE_Y, 0.1f)
        );
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishDismiss(onContentRelease);
            }
        });
        set.start();
    }

    private void finishDismiss(Runnable onContentRelease) {
        removeWindow();
        final PipTextureView target = renderTarget;
        renderTarget = null;
        if (target != null) {
            AndroidUtilities.removeFromParent(target);
        }
        if (floatingView != null) {
            // the content destroys its view itself
            AndroidUtilities.removeFromParent(floatingView);
            floatingView = null;
        }
        onContentRelease.run();
        if (target != null) {
            target.releaseSurfaceTexture();
        }
    }

    private void cancelGestures() {
        if (controlsAnimator != null) {
            controlsAnimator.cancel();
        }
        if (postedDismissControls) {
            AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
            postedDismissControls = false;
        }
        AndroidUtilities.cancelRunOnUIThread(progressRunnable);
        AndroidUtilities.cancelRunOnUIThread(longClickCallback);
        canLongClick = false;
        isScrolling = false;
        consumingChild = null;
        content.cancelRewind();
    }

    /* Window */

    private void addWindow() {
        if (isWindowAdded) {
            return;
        }
        isOverlay = shouldUseOverlay();
        final Context wmContext = isOverlay ? ApplicationLoader.applicationContext : activity;
        windowManager = (WindowManager) wmContext.getSystemService(Context.WINDOW_SERVICE);

        // the activity WindowManager stores its token in the params; an overlay with the activity
        // token is stopped together with the activity
        windowLayoutParams.token = null;
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        if (isOverlay) {
            windowLayoutParams.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        } else {
            windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
        }
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        windowLayoutParams.dimAmount = 0f;
        windowLayoutParams.width = pipWidth;
        windowLayoutParams.height = pipHeight;
        windowLayoutParams.x = (int) pipX;
        windowLayoutParams.y = (int) pipY;

        try {
            AndroidUtilities.setPreferredMaxRefreshRate(windowManager, contentView, windowLayoutParams);
            windowManager.addView(contentView, windowLayoutParams);
            isWindowAdded = true;
            PipCoordinator.log("floating window added, overlay=" + isOverlay);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void removeWindow() {
        if (!isWindowAdded) {
            return;
        }
        isWindowAdded = false;
        try {
            windowManager.removeViewImmediate(contentView);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void updateWindowLayout() {
        if (!isWindowAdded) {
            return;
        }
        try {
            windowManager.updateViewLayout(contentView, windowLayoutParams);
        } catch (IllegalArgumentException e) {
            if (pipXSpring != null) {
                pipXSpring.cancel();
                pipYSpring.cancel();
            }
        }
    }

    /** The overlay permission may have changed while the app was in background. */
    void checkWindowType() {
        if (!isWindowAdded || isDismissing) {
            return;
        }
        if (shouldUseOverlay() != isOverlay) {
            removeWindow();
            addWindow();
        }
    }

    void onConfigurationChanged() {
        if (contentView == null || isDismissing) {
            return;
        }
        AndroidUtilities.checkDisplaySize(activity, activity.getResources().getConfiguration());
        PipConfig.reset();
        maxScaleFactor = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - sidePadding() * 2) / (float) getSuggestedWidth();
        final int width = (int) (getSuggestedWidth() * scaleFactor);
        final int height = (int) (getSuggestedHeight() * scaleFactor);
        if (pipWidth != width || pipHeight != height) {
            windowLayoutParams.width = pipWidth = width;
            windowLayoutParams.height = pipHeight = height;
            updateWindowLayout();
        }
        pipXSpring.setStartValue(pipX).getSpring().setFinalPosition(snapX(pipX, pipWidth));
        pipXSpring.start();
        pipYSpring.setStartValue(pipY).getSpring().setFinalPosition(clampY(pipY, pipHeight));
        pipYSpring.start();
    }

    /* State from the content */

    void invalidateState() {
        if (content.isPlaying()) {
            isVideoCompleted = false;
        }
        updatePlayButton();
        updateProgress();
    }

    void onVideoCompleted() {
        if (videoProgressView == null) {
            return;
        }
        isVideoCompleted = true;
        videoProgress = 0f;
        bufferProgress = 0f;
        videoProgressView.invalidate();
        updatePlayButton();
        if (!isShowingControls) {
            toggleControls(isShowingControls = true);
            AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
            postedDismissControls = false;
        }
    }

    void onRewindStart(boolean forward) {
        videoForwardDrawable.setOneShootAnimation(false);
        videoForwardDrawable.setLeftSide(!forward);
        videoForwardDrawable.setShowing(true);
        invalidateControls();
    }

    void onRewindProgress(float progress, boolean byBackSeek) {
        videoForwardDrawable.setTime(0);
        if (byBackSeek) {
            videoProgress = progress;
            invalidateControls();
        }
    }

    void onRewindCanceled() {
        videoForwardDrawable.setShowing(false);
    }

    private void invalidateControls() {
        if (videoProgressView != null) {
            videoProgressView.invalidate();
        }
        if (controlsView != null) {
            controlsView.invalidate();
        }
    }

    private void updatePlayButton() {
        if (playPauseButton == null) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(progressRunnable);
        if (content.isPlaying()) {
            playPauseButton.setImageResource(R.drawable.pip_pause_large);
            AndroidUtilities.runOnUIThread(progressRunnable, 500);
        } else if (isVideoCompleted) {
            playPauseButton.setImageResource(R.drawable.pip_replay_large);
        } else {
            playPauseButton.setImageResource(R.drawable.pip_play_large);
        }
    }

    private void updateProgress() {
        if (videoProgressView == null || isVideoCompleted) {
            return;
        }
        final long duration = content.getDuration();
        if (duration > 0) {
            videoProgress = content.getCurrentPosition() / (float) duration;
        }
        bufferProgress = Math.max(bufferProgress, content.getBufferedProgress());
        videoProgressView.invalidate();
    }

    /* Controls */

    private void toggleControls(boolean show) {
        if (controlsView == null) {
            return;
        }
        controlsAnimator = ValueAnimator.ofFloat(show ? 0 : 1, show ? 1 : 0f).setDuration(200);
        controlsAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        controlsAnimator.addUpdateListener(animation -> controlsView.setAlpha((float) animation.getAnimatedValue()));
        controlsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                controlsAnimator = null;
            }
        });
        controlsAnimator.start();
    }

    private void showControlsTemporarily(long hideDelay) {
        if (!isShowingControls) {
            toggleControls(isShowingControls = true);
            if (!postedDismissControls) {
                postedDismissControls = true;
                AndroidUtilities.runOnUIThread(dismissControlsCallback, hideDelay);
            }
        }
    }

    private boolean canSeek() {
        if (!content.hasPlaybackControls()) {
            return false;
        }
        final long current = content.getCurrentPosition();
        final long total = content.getDuration();
        return current != C.TIME_UNSET && total >= 15 * 1000;
    }

    private boolean canHandleSeekGestures() {
        return !isDismissing && !isVideoCompleted && !isScrolling && !scaleGestureDetector.isInProgress() && canLongClick;
    }

    private void onLongClick() {
        if (!canHandleSeekGestures() || !canSeek()) {
            return;
        }
        final boolean forward = longClickStartPoint[0] >= pipWidth * 0.5f;
        if (content.startRewind(forward, longClickStartPoint[0], seekSpeedDrawable)) {
            showControlsTemporarily(1500);
        }
    }

    private boolean onDoubleTapSeek(MotionEvent e) {
        if (!canHandleSeekGestures() || !canSeek()) {
            return false;
        }
        final boolean forward = e.getX() >= pipWidth * 0.5f;
        final long total = content.getDuration();
        final long old = content.getCurrentPosition();
        long current = old + (forward ? 10000 : -10000);
        if (current > total) {
            current = total;
        } else if (current < 0) {
            if (current < -9000) {
                return true;
            }
            current = 0;
        }
        if (old == current) {
            return false;
        }
        videoForwardDrawable.setOneShootAnimation(true);
        videoForwardDrawable.setLeftSide(!forward);
        videoForwardDrawable.addTime(10000);
        content.seekTo(current);
        onRewindProgress(current / (float) total, true);
        showControlsTemporarily(2500);
        return true;
    }

    private void createSprings() {
        final float stiffness = 650f;
        pipXSpring = new SpringAnimation(this, PIP_X_PROPERTY)
            .setSpring(new SpringForce()
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                .setStiffness(stiffness))
            .addEndListener((animation, canceled, value, velocity) -> {
                PipConfig.get().setX(value);
                session.coordinator.invalidateSystem();
            });
        pipYSpring = new SpringAnimation(this, PIP_Y_PROPERTY)
            .setSpring(new SpringForce()
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                .setStiffness(stiffness))
            .addEndListener((animation, canceled, value, velocity) -> {
                PipConfig.get().setY(value);
                session.coordinator.invalidateSystem();
            });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createViews() {
        final Context context = activity;
        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                scaleFactor = MathUtils.clamp(scaleFactor * detector.getScaleFactor(), MIN_SCALE_FACTOR, maxScaleFactor);
                pipWidth = (int) (getSuggestedWidth() * scaleFactor);
                pipHeight = (int) (getSuggestedHeight() * scaleFactor);
                AndroidUtilities.runOnUIThread(() -> {
                    contentView.invalidate();
                    contentFrameLayout.requestLayout();
                });

                final float finalX = detector.getFocusX() >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - sidePadding() : sidePadding();
                if (!pipXSpring.isRunning()) {
                    pipXSpring.setStartValue(pipX).getSpring().setFinalPosition(finalX);
                } else {
                    pipXSpring.getSpring().setFinalPosition(finalX);
                }
                pipXSpring.start();

                final float finalY = clampY(detector.getFocusY() - pipHeight / 2f, pipHeight);
                if (!pipYSpring.isRunning()) {
                    pipYSpring.setStartValue(pipY).getSpring().setFinalPosition(finalY);
                } else {
                    pipYSpring.getSpring().setFinalPosition(finalY);
                }
                pipYSpring.start();
                return true;
            }

            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                if (isScrolling) {
                    isScrolling = false;
                    canLongClick = false;
                    content.cancelRewind();
                    AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                }
                isScrollDisallowed = true;
                windowLayoutParams.width = (int) (getSuggestedWidth() * maxScaleFactor);
                windowLayoutParams.height = (int) (getSuggestedHeight() * maxScaleFactor);
                updateWindowLayout();
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                PipConfig.get().setScaleFactor(scaleFactor);
                if (pipXSpring.isRunning() || pipYSpring.isRunning()) {
                    final List<SpringAnimation> springs = new ArrayList<>();
                    final DynamicAnimation.OnAnimationEndListener endListener = new DynamicAnimation.OnAnimationEndListener() {
                        @Override
                        public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                            animation.removeEndListener(this);
                            springs.add((SpringAnimation) animation);
                            if (springs.size() == 2) {
                                updateLayout();
                            }
                        }
                    };
                    if (!pipXSpring.isRunning()) {
                        springs.add(pipXSpring);
                    } else {
                        pipXSpring.addEndListener(endListener);
                    }
                    if (!pipYSpring.isRunning()) {
                        springs.add(pipYSpring);
                    } else {
                        pipYSpring.addEndListener(endListener);
                    }
                    return;
                }
                updateLayout();
            }

            private void updateLayout() {
                pipWidth = windowLayoutParams.width = (int) (getSuggestedWidth() * scaleFactor);
                pipHeight = windowLayoutParams.height = (int) (getSuggestedHeight() * scaleFactor);
                updateWindowLayout();
                session.coordinator.invalidateSystem();
            }
        });
        scaleGestureDetector.setQuickScaleEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scaleGestureDetector.setStylusScaleEnabled(false);
        }

        gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureDetectorFixDoubleTap.OnGestureListener() {
            private float startPipX, startPipY;

            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                if (isShowingControls) {
                    for (int i = 1; i < contentFrameLayout.getChildCount(); i++) {
                        final View child = contentFrameLayout.getChildAt(i);
                        if (child.dispatchTouchEvent(e)) {
                            consumingChild = child;
                            return true;
                        }
                    }
                }
                startPipX = pipX;
                startPipY = pipY;
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (controlsAnimator != null) {
                    return true;
                }
                if (postedDismissControls) {
                    AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
                    postedDismissControls = false;
                }
                isShowingControls = !isShowingControls;
                toggleControls(isShowingControls);
                if (isShowingControls) {
                    AndroidUtilities.runOnUIThread(dismissControlsCallback, 2500);
                    postedDismissControls = true;
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                return onDoubleTapSeek(e);
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                if (!hasDoubleTap(e)) {
                    return onSingleTapConfirmed(e);
                }
                return super.onSingleTapUp(e);
            }

            @Override
            public boolean hasDoubleTap(MotionEvent e) {
                // must not depend on canLongClick: it is reset on ACTION_UP before the detector runs,
                // which would confirm the single tap twice (on up and on the TAP timeout)
                return !isDismissing && !isVideoCompleted && canSeek();
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (isScrolling && !isScrollDisallowed) {
                    pipXSpring.setStartVelocity(velocityX)
                        .setStartValue(pipX)
                        .getSpring()
                        .setFinalPosition(snapX(pipX + velocityX / 7f, pipWidth));
                    pipXSpring.start();

                    pipYSpring.setStartVelocity(velocityY)
                        .setStartValue(pipY)
                        .getSpring()
                        .setFinalPosition(clampY(pipY + velocityY / 10f, pipHeight));
                    pipYSpring.start();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if (!isScrolling && controlsAnimator == null && !isScrollDisallowed) {
                    if (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop) {
                        isScrolling = true;
                        pipXSpring.cancel();
                        pipYSpring.cancel();
                        canLongClick = false;
                        content.cancelRewind();
                        AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                    }
                }
                if (isScrolling && e1 != null) {
                    // the window follows the finger anywhere, also past the screen edges: it snaps
                    // back to the nearest side on release and is never closed by being dragged out
                    final float newPipX = startPipX + e2.getRawX() - e1.getRawX();
                    pipY = startPipY + e2.getRawY() - e1.getRawY();
                    if (pipXSpring.isRunning()) {
                        pipXSpring.getSpring().setFinalPosition(newPipX);
                    } else {
                        windowLayoutParams.x = (int) (pipX = newPipX);
                    }
                    windowLayoutParams.y = (int) pipY;
                    updateWindowLayout();
                }
                return true;
            }
        });

        contentFrameLayout = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (isDismissing) {
                    return false;
                }
                final int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    if (ev.getPointerCount() == 1) {
                        canLongClick = true;
                        longClickStartPoint[0] = ev.getX();
                        longClickStartPoint[1] = ev.getY();
                        AndroidUtilities.runOnUIThread(longClickCallback, 500);
                    } else {
                        canLongClick = false;
                        content.cancelRewind();
                        AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                    }
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
                    canLongClick = false;
                    content.cancelRewind();
                    AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                } else if (action == MotionEvent.ACTION_MOVE && content.isRewinding()) {
                    content.updateRewind(ev.getX());
                }

                if (consumingChild != null) {
                    final MotionEvent childEvent = MotionEvent.obtain(ev);
                    childEvent.offsetLocation(-consumingChild.getX(), -consumingChild.getY());
                    final boolean consumed = consumingChild.dispatchTouchEvent(childEvent);
                    childEvent.recycle();
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
                        consumingChild = null;
                    }
                    if (consumed) {
                        return true;
                    }
                }

                final MotionEvent temp = MotionEvent.obtain(ev);
                temp.offsetLocation(ev.getRawX() - ev.getX(), ev.getRawY() - ev.getY());
                final boolean scaleDetector = scaleGestureDetector.onTouchEvent(temp);
                temp.recycle();
                final boolean detector = !scaleGestureDetector.isInProgress() && gestureDetector.onTouchEvent(ev);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
                    isScrolling = false;
                    isScrollDisallowed = false;

                    if (!pipXSpring.isRunning()) {
                        pipXSpring.setStartValue(pipX).getSpring().setFinalPosition(snapX(pipX, pipWidth));
                        pipXSpring.start();
                    }
                    if (!pipYSpring.isRunning()) {
                        pipYSpring.setStartValue(pipY).getSpring().setFinalPosition(clampY(pipY, pipHeight));
                        pipYSpring.start();
                    }
                }
                return scaleDetector || detector;
            }
        };

        contentView = new ViewGroup(context) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                contentFrameLayout.layout(0, 0, pipWidth, pipHeight);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                contentFrameLayout.measure(MeasureSpec.makeMeasureSpec(pipWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(pipHeight, MeasureSpec.EXACTLY));
            }

            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.save();
                canvas.scale(pipWidth / (float) contentFrameLayout.getWidth(), pipHeight / (float) contentFrameLayout.getHeight());
                super.draw(canvas);
                canvas.restore();
            }
        };
        contentView.addView(contentFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentFrameLayout.setOutlineProvider(ViewOutlineProviderImpl.boundsWithPaddingRoundRect(0, dp(ROUNDED_CORNERS_DP)));
        contentFrameLayout.setClipToOutline(true);
        ThemeBind.backgroundColor(contentFrameLayout, Theme.key_voipgroup_actionBar);

        placeholderView = new ImageView(context);
        placeholderView.setScaleType(ImageView.ScaleType.FIT_XY);
        placeholderView.setVisibility(View.GONE);
        contentFrameLayout.addView(placeholderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        videoForwardDrawable.setDelegate(new VideoForwardDrawable.VideoForwardDrawableDelegate() {
            @Override
            public void onAnimationEnd() {}

            @Override
            public void invalidate() {
                controlsView.invalidate();
            }
        });
        controlsView = new FrameLayout(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                if (videoForwardDrawable.isAnimating()) {
                    videoForwardDrawable.setBounds(getLeft(), getTop(), getRight(), getBottom());
                    videoForwardDrawable.draw(canvas);
                }
                canvas.save();
                canvas.translate(getLeft(), getTop());
                content.drawFloatingOverlay(canvas, getRight() - getLeft(), getBottom() - getTop());
                canvas.restore();
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (seekSpeedDrawable != null && seekSpeedDrawable.isShown()) {
                    seekSpeedDrawable.setBounds(getLeft(), getTop(), getRight(), getBottom());
                    seekSpeedDrawable.draw(canvas);
                }
            }
        };
        seekSpeedDrawable = new SeekSpeedDrawable(controlsView::invalidate, true, false);
        controlsView.setWillNotDraw(false);
        controlsView.setAlpha(0f);
        final View scrim = new View(context);
        scrim.setBackgroundColor(0x4C000000);
        controlsView.addView(scrim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final int padding = dp(8);
        final int margin = 4;
        final int buttonSize = 38;

        final ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.pip_video_close);
        ThemeBind.colorFilter(closeButton, Theme.key_voipgroup_actionBarItems, PorterDuff.Mode.MULTIPLY);
        ThemeBind.selectorBackground(closeButton, Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)), null, ThemeBind.of(Theme.key_listSelector));
        closeButton.setPadding(padding, padding, padding, padding);
        closeButton.setOnClickListener(v -> session.dismiss());
        controlsView.addView(closeButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.RIGHT, 0, margin, margin, 0));

        final ImageView expandButton = new ImageView(context);
        expandButton.setImageResource(R.drawable.pip_video_expand);
        ThemeBind.colorFilter(expandButton, Theme.key_voipgroup_actionBarItems, PorterDuff.Mode.MULTIPLY);
        ThemeBind.selectorBackground(expandButton, Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)), null, ThemeBind.of(Theme.key_listSelector));
        expandButton.setPadding(padding, padding, padding, padding);
        expandButton.setOnClickListener(v -> session.expand());
        controlsView.addView(expandButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.RIGHT, 0, margin, buttonSize + margin + 6, 0));

        playPauseButton = new ImageView(context);
        ThemeBind.colorFilter(playPauseButton, Theme.key_voipgroup_actionBarItems, PorterDuff.Mode.MULTIPLY);
        ThemeBind.selectorBackground(playPauseButton, Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)), null, ThemeBind.of(Theme.key_listSelector));
        playPauseButton.setOnClickListener(v -> {
            if (content.isPlaying()) {
                content.pause();
            } else {
                if (isVideoCompleted) {
                    content.seekTo(0);
                    isVideoCompleted = false;
                }
                content.play();
            }
            invalidateState();
            session.coordinator.invalidateSystem();
        });
        playPauseButton.setVisibility(content.hasPlaybackControls() ? View.VISIBLE : View.GONE);
        controlsView.addView(playPauseButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.CENTER));

        videoProgressView = new VideoProgressView(context);
        videoProgressView.setVisibility(content.hasPlaybackControls() ? View.VISIBLE : View.GONE);
        controlsView.addView(videoProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        contentFrameLayout.addView(controlsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private final class VideoProgressView extends View {
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bufferPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        VideoProgressView(Context context) {
            super(context);
            progressPaint.setColor(Color.WHITE);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(dp(2));
            bufferPaint.setColor(progressPaint.getColor());
            bufferPaint.setAlpha((int) (progressPaint.getAlpha() * 0.3f));
            bufferPaint.setStyle(Paint.Style.STROKE);
            bufferPaint.setStrokeCap(Paint.Cap.ROUND);
            bufferPaint.setStrokeWidth(dp(2));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final int width = getWidth();
            final float left = dp(10);
            final float available = width - left - dp(10);
            final float y = getHeight() - dp(8);
            if (bufferProgress != 0) {
                canvas.drawLine(left, y, left + available * bufferProgress, y, bufferPaint);
            }
            canvas.drawLine(left, y, left + available * videoProgress, y, progressPaint);
        }
    }

    /** Saved position and scale, per display size. */
    private static final class PipConfig {
        private static PipConfig instance;

        static PipConfig get() {
            if (instance == null) {
                instance = new PipConfig(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            }
            return instance;
        }

        static void reset() {
            instance = null;
        }

        private final SharedPreferences prefs;

        private PipConfig(int width, int height) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("pip_floating_" + width + "_" + height, Context.MODE_PRIVATE);
        }

        void setX(float x) {
            prefs.edit().putFloat("x", x).apply();
        }

        void setY(float y) {
            prefs.edit().putFloat("y", y).apply();
        }

        void setScaleFactor(float scaleFactor) {
            prefs.edit().putFloat("scale_factor", scaleFactor).apply();
        }

        float getScaleFactor() {
            return prefs.getFloat("scale_factor", 1f);
        }

        float getX() {
            return prefs.getFloat("x", -1);
        }

        float getY() {
            return prefs.getFloat("y", -1);
        }
    }
}
