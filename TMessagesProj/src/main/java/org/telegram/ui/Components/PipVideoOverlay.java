package org.telegram.ui.Components;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.List;

public class PipVideoOverlay {
    public final static boolean IS_TRANSITION_ANIMATION_SUPPORTED = true;
    public final static float ROUNDED_CORNERS_DP = 10;

    private final static float SIDE_PADDING_DP = 16;
    private final static FloatPropertyCompat<PipVideoOverlay> PIP_X_PROPERTY = new SimpleFloatPropertyCompat<>("pipX", obj -> obj.pipX, (obj, value) -> {
        obj.windowLayoutParams.x = (int) (obj.pipX = value);
        try {
            obj.windowManager.updateViewLayout(obj.contentView, obj.windowLayoutParams);
        } catch (IllegalArgumentException e) {
            obj.pipXSpring.cancel();
        }
    }), PIP_Y_PROPERTY = new SimpleFloatPropertyCompat<>("pipY", obj -> obj.pipY, (obj, value) -> {
        obj.windowLayoutParams.y = (int) (obj.pipY = value);

        try {
            obj.windowManager.updateViewLayout(obj.contentView, obj.windowLayoutParams);
        } catch (IllegalArgumentException e) {
            obj.pipYSpring.cancel();
        }
    });

    @SuppressLint("StaticFieldLeak")
    private static PipVideoOverlay instance = new PipVideoOverlay();

    private float minScaleFactor = 0.75f, maxScaleFactor = 1.4f;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowLayoutParams;
    private ViewGroup contentView;
    private FrameLayout contentFrameLayout;
    private View innerView;
    private FrameLayout controlsView;

    private boolean isWebView;
    private PhotoViewerWebView photoViewerWebView;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetectorFixDoubleTap gestureDetector;
    private boolean isScrolling;
    private boolean isScrollDisallowed;
    private View consumingChild;
    private boolean isShowingControls;
    private ValueAnimator controlsAnimator;

    private PipConfig pipConfig;
    private int pipWidth, pipHeight;
    private float scaleFactor = 1f;
    private float pipX, pipY;
    private SpringAnimation pipXSpring, pipYSpring;
    private Float aspectRatio;

    private boolean isVisible;

    private VideoForwardDrawable videoForwardDrawable = new VideoForwardDrawable(false);
    private int mVideoWidth, mVideoHeight;
    private EmbedBottomSheet parentSheet;
    private PhotoViewer photoViewer;
    private ImageView playPauseButton;
    private boolean isVideoCompleted;
    private float videoProgress, bufferProgress;
    private VideoProgressView videoProgressView;
    private boolean isDismissing;
    private boolean onSideToDismiss;
    private Runnable progressRunnable = () -> {
        if (photoViewer == null) {
            return;
        }

        if (photoViewerWebView != null) {
            videoProgress = photoViewerWebView.getCurrentPosition() / (float) photoViewerWebView.getVideoDuration();
            bufferProgress = photoViewerWebView.getBufferedPosition();
        } else {
            VideoPlayer videoPlayer = photoViewer.getVideoPlayer();
            if (videoPlayer == null) {
                return;
            }

            long duration = getDuration();
            videoProgress = videoPlayer.getCurrentPosition() / (float) duration;
            bufferProgress = videoPlayer.getBufferedPosition() / (float) duration;
        }
        videoProgressView.invalidate();

        AndroidUtilities.runOnUIThread(this.progressRunnable, 500);
    };
    private boolean canLongClick;
    private float[] longClickStartPoint = new float[2];
    private Runnable longClickCallback = this::onLongClick;

    private boolean postedDismissControls;
    private Runnable dismissControlsCallback = () -> {
        if (photoViewer != null && photoViewer.getVideoPlayerRewinder().rewindCount > 0) {
            AndroidUtilities.runOnUIThread(this.dismissControlsCallback, 1500);
            return;
        }
        toggleControls(isShowingControls = false);
        postedDismissControls = false;
    };

    public static void onRewindCanceled() {
        instance.onRewindCanceledInternal();
    }

    private void onRewindCanceledInternal() {
        videoForwardDrawable.setShowing(false);
    }

    public static void onUpdateRewindProgressUi(long timeDiff, float progress, boolean rewindByBackSeek) {
        instance.onUpdateRewindProgressUiInternal(timeDiff, progress, rewindByBackSeek);
    }

    private void onUpdateRewindProgressUiInternal(long timeDiff, float progress, boolean rewindByBackSeek) {
        videoForwardDrawable.setTime(0);
        if (rewindByBackSeek) {
            videoProgress = progress;

            if (videoProgressView != null) {
                videoProgressView.invalidate();
            }
            if (controlsView != null) {
                controlsView.invalidate();
            }
        }
    }

    public static void onRewindStart(boolean rewindForward) {
        instance.onRewindStartInternal(rewindForward);
    }

    private void onRewindStartInternal(boolean rewindForward) {
        videoForwardDrawable.setOneShootAnimation(false);
        videoForwardDrawable.setLeftSide(!rewindForward);
        videoForwardDrawable.setShowing(true);
        if (videoProgressView != null) {
            videoProgressView.invalidate();
        }
        if (controlsView != null) {
            controlsView.invalidate();
        }
    }

    private long getCurrentPosition() {
        if (photoViewerWebView != null) {
            return photoViewerWebView.getCurrentPosition();
        } else {
            VideoPlayer player = photoViewer.getVideoPlayer();
            if (player == null) {
                return 0;
            }
            return player.getCurrentPosition();
        }
    }

    private void seekTo(long position) {
        if (photoViewerWebView != null) {
            photoViewerWebView.seekTo(position);
        } else {
            VideoPlayer player = photoViewer.getVideoPlayer();
            if (player == null) {
                return;
            }
            player.seekTo(position);
        }
    }

    private long getDuration() {
        if (photoViewerWebView != null) {
            return photoViewerWebView.getVideoDuration();
        } else {
            VideoPlayer player = photoViewer.getVideoPlayer();
            if (player == null) {
                return 0;
            }
            return player.getDuration();
        }
    }

    protected void onLongClick() {
        if (photoViewer == null || photoViewer.getVideoPlayer() == null && photoViewerWebView == null || isDismissing || isVideoCompleted || isScrolling || scaleGestureDetector.isInProgress() || !canLongClick) {
            return;
        }

        VideoPlayer videoPlayer = photoViewer.getVideoPlayer();
        boolean forward = longClickStartPoint[0] >= getSuggestedWidth() * scaleFactor * 0.5f;

        long current = getCurrentPosition();
        long total = getDuration();
        if (current == C.TIME_UNSET || total < 15 * 1000) {
            return;
        }

        if (photoViewerWebView != null) {
            photoViewer.getVideoPlayerRewinder().startRewind(photoViewerWebView, forward, photoViewer.getCurrentVideoSpeed());
        } else {
            photoViewer.getVideoPlayerRewinder().startRewind(videoPlayer, forward, photoViewer.getCurrentVideoSpeed());
        }

        if (!isShowingControls) {
            toggleControls(isShowingControls = true);
            if (!postedDismissControls) {
                AndroidUtilities.runOnUIThread(dismissControlsCallback, 1500);
                postedDismissControls = true;
            }
        }
    }

    private PipConfig getPipConfig() {
        if (pipConfig == null) {
            pipConfig = new PipConfig(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        }
        return pipConfig;
    }

    public static boolean isVisible() {
        return instance.isVisible;
    }

    private int getSuggestedWidth() {
        return getSuggestedWidth(getRatio());
    }

    private static int getSuggestedWidth(float ratio) {
        if (ratio >= 1) {
            return (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.35f);
        }
        return (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.6f);
    }

    private int getSuggestedHeight() {
        return getSuggestedHeight(getRatio());
    }

    private static int getSuggestedHeight(float ratio) {
        return (int) (getSuggestedWidth(ratio) * ratio);
    }

    private float getRatio() {
        if (aspectRatio == null) {
            aspectRatio = mVideoHeight / (float) mVideoWidth;

            maxScaleFactor = (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(SIDE_PADDING_DP * 2)) / (float) getSuggestedWidth();
            videoForwardDrawable.setPlayScaleFactor(aspectRatio < 1 ? 0.6f : 0.45f);
        }
        return aspectRatio;
    }

    private void toggleControls(boolean show) {
        controlsAnimator = ValueAnimator.ofFloat(show ? 0 : 1, show ? 1 : 0f).setDuration(200);
        controlsAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        controlsAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            controlsView.setAlpha(value);
        });
        controlsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                controlsAnimator = null;
            }
        });
        controlsAnimator.start();
    }

    public static void dimissAndDestroy() {
        if (instance.parentSheet != null) {
            instance.parentSheet.destroy();
        } else if (instance.photoViewer != null) {
            instance.photoViewer.destroyPhotoViewer();
        }
        dismiss();
    }

    public static void dismiss() {
        dismiss(false);
    }

    public static void dismiss(boolean animate) {
        instance.dismissInternal(animate);
    }

    private void dismissInternal(boolean animate) {
        if (isDismissing) {
            return;
        }
        isDismissing = true;

        if (controlsAnimator != null) {
            controlsAnimator.cancel();
        }

        if (postedDismissControls) {
            AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
            postedDismissControls = false;
        }

        if (pipXSpring != null) {
            pipXSpring.cancel();
            pipYSpring.cancel();
        }

        // Animate is a flag for PhotoViewer transition, not ours
        if (animate || contentView == null) {
            AndroidUtilities.runOnUIThread(this::onDismissedInternal, 100);
        } else {
            AnimatorSet set = new AnimatorSet();
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
                    onDismissedInternal();
                }
            });
            set.start();
        }
    }

    private void onDismissedInternal() {
        try {
            if (contentView != null && contentView.getParent() != null) {
                windowManager.removeViewImmediate(contentView);
            }
        } catch (Exception ignored) {}

        if (photoViewerWebView != null) {
            photoViewerWebView.showControls();
        }

        videoProgressView = null;
        innerView = null;
        photoViewer = null;
        photoViewerWebView = null;
        parentSheet = null;
        consumingChild = null;
        isScrolling = false;
        isVisible = false;
        isDismissing = false;
        canLongClick = false;

        cancelRewind();
        AndroidUtilities.cancelRunOnUIThread(longClickCallback);
    }

    public static View getInnerView() {
        return instance.innerView;
    }

    private void cancelRewind() {
        if (photoViewer == null) {
            return;
        }

        if (photoViewer.getVideoPlayerRewinder().rewindCount > 0) {
            photoViewer.getVideoPlayerRewinder().cancelRewind();
        }
    }

    public static void updatePlayButton() {
        instance.updatePlayButtonInternal();
    }

    private void updatePlayButtonInternal() {
        if (photoViewer == null || playPauseButton == null) {
            return;
        }

        boolean isPlaying;
        if (photoViewerWebView != null) {
            isPlaying = photoViewerWebView.isPlaying();
        } else {
            VideoPlayer videoPlayer = photoViewer.getVideoPlayer();
            if (videoPlayer == null) {
                return;
            }

            isPlaying = videoPlayer.isPlaying();
        }
        AndroidUtilities.cancelRunOnUIThread(progressRunnable);
        if (!isPlaying) {
            if (isVideoCompleted) {
                playPauseButton.setImageResource(R.drawable.pip_replay_large);
            } else {
                playPauseButton.setImageResource(R.drawable.pip_play_large);
            }
        } else {
            playPauseButton.setImageResource(R.drawable.pip_pause_large);
            AndroidUtilities.runOnUIThread(progressRunnable, 500);
        }
    }

    public static void onVideoCompleted() {
        instance.onVideoCompletedInternal();
    }

    private void onVideoCompletedInternal() {
        if (!isVisible || videoProgressView == null) {
            return;
        }
        isVideoCompleted = true;
        videoProgress = 0f;
        bufferProgress = 0f;
        if (videoProgressView != null) {
            videoProgressView.invalidate();
        }

        updatePlayButtonInternal();
        AndroidUtilities.cancelRunOnUIThread(progressRunnable);
        if (!isShowingControls) {
            toggleControls(true);
            AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
        }
    }

    public static void setBufferedProgress(float progress) {
        instance.bufferProgress = progress;
        if (instance.videoProgressView != null) {
            instance.videoProgressView.invalidate();
        }
    }

    public static void setParentSheet(EmbedBottomSheet parentSheet) {
        instance.parentSheet = parentSheet;
    }

    public static void setPhotoViewer(PhotoViewer photoViewer) {
        instance.photoViewer = photoViewer;
        instance.updatePlayButtonInternal();
    }

    public static Rect getPipRect(boolean inAnimation, float aspectRatio) {
        Rect rect = new Rect();
        float ratio = 1f / aspectRatio;
        if (!instance.isVisible || inAnimation) {
            float savedPipX = instance.getPipConfig().getPipX(), savedPipY = instance.getPipConfig().getPipY();
            float scaleFactor = instance.getPipConfig().getScaleFactor();

            rect.width = getSuggestedWidth(ratio) * scaleFactor;
            rect.height = getSuggestedHeight(ratio) * scaleFactor;
            if (savedPipX != -1) {
                rect.x = savedPipX + rect.width / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - rect.width - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP);
            } else {
                rect.x = AndroidUtilities.displaySize.x - rect.width - AndroidUtilities.dp(SIDE_PADDING_DP);
            }
            if (savedPipY != -1) {
                rect.y = MathUtils.clamp(savedPipY, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - AndroidUtilities.dp(SIDE_PADDING_DP) - rect.height) + AndroidUtilities.statusBarHeight;
            } else {
                rect.y = AndroidUtilities.dp(SIDE_PADDING_DP) + AndroidUtilities.statusBarHeight;
            }
            return rect;
        }

        rect.x = instance.pipX;
        rect.y = instance.pipY + AndroidUtilities.statusBarHeight;
        rect.width = instance.pipWidth;
        rect.height = instance.pipHeight;
        return rect;
    }

    public static boolean show(boolean inAppOnly, Activity activity, View pipContentView, int videoWidth, int videoHeight) {
        return show(inAppOnly, activity, pipContentView, videoWidth, videoHeight, false);
    }

    public static boolean show(boolean inAppOnly, Activity activity, View pipContentView, int videoWidth, int videoHeight, boolean animate) {
        return show(inAppOnly, activity, null, pipContentView, videoWidth, videoHeight, animate);
    }

    public static boolean show(boolean inAppOnly, Activity activity, PhotoViewerWebView viewerWebView, View pipContentView, int videoWidth, int videoHeight, boolean animate) {
        return instance.showInternal(inAppOnly, activity, pipContentView, viewerWebView, videoWidth, videoHeight, animate);
    }

    private boolean showInternal(boolean inAppOnly, Activity activity, View pipContentView, PhotoViewerWebView viewerWebView, int videoWidth, int videoHeight, boolean animate) {
        if (isVisible) {
            return false;
        }
        isVisible = true;

        mVideoWidth = videoWidth;
        mVideoHeight = videoHeight;
        aspectRatio = null;
        if (viewerWebView != null && viewerWebView.isControllable()) {
            photoViewerWebView = viewerWebView;
            photoViewerWebView.hideControls();
        } else {
            photoViewerWebView = null;
        }

        float savedPipX = getPipConfig().getPipX(), savedPipY = getPipConfig().getPipY();
        scaleFactor = getPipConfig().getScaleFactor();

        pipWidth = (int) (getSuggestedWidth() * scaleFactor);
        pipHeight = (int) (getSuggestedHeight() * scaleFactor);
        isShowingControls = false;

        float stiffness = 650f;
        pipXSpring = new SpringAnimation(this, PIP_X_PROPERTY)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(stiffness))
                .addEndListener((animation, canceled, value, velocity) -> getPipConfig().setPipX(value));
        pipYSpring = new SpringAnimation(this, PIP_Y_PROPERTY)
                .setSpring(new SpringForce()
                        .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
                        .setStiffness(stiffness))
                .addEndListener((animation, canceled, value, velocity) -> getPipConfig().setPipY(value));

        Context context = ApplicationLoader.applicationContext;
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor = MathUtils.clamp(scaleFactor * detector.getScaleFactor(), minScaleFactor, maxScaleFactor);
                pipWidth = (int) (getSuggestedWidth() * scaleFactor);
                pipHeight = (int) (getSuggestedHeight() * scaleFactor);
                AndroidUtilities.runOnUIThread(()->{
                    contentView.invalidate();
                    contentFrameLayout.requestLayout();
                });

                float finalX = detector.getFocusX() >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP);
                if (!pipXSpring.isRunning()) {
                    pipXSpring.setStartValue(pipX)
                            .getSpring()
                            .setFinalPosition(finalX);
                } else {
                    pipXSpring.getSpring().setFinalPosition(finalX);
                }
                pipXSpring.start();

                float finalY = MathUtils.clamp(detector.getFocusY() - pipHeight / 2f, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - pipHeight - AndroidUtilities.dp(SIDE_PADDING_DP));
                if (!pipYSpring.isRunning()) {
                    pipYSpring.setStartValue(pipY)
                            .getSpring()
                            .setFinalPosition(finalY);
                } else {
                    pipYSpring.getSpring().setFinalPosition(finalY);
                }
                pipYSpring.start();

                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (isScrolling) {
                    isScrolling = false;
                    canLongClick = false;
                    cancelRewind();
                    AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                }
                isScrollDisallowed = true;
                windowLayoutParams.width = (int) (getSuggestedWidth() * maxScaleFactor);
                windowLayoutParams.height = (int) (getSuggestedHeight() * maxScaleFactor);
                windowManager.updateViewLayout(contentView, windowLayoutParams);

                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (pipXSpring.isRunning() || pipYSpring.isRunning()) {
                    List<SpringAnimation> springs = new ArrayList<>();
                    DynamicAnimation.OnAnimationEndListener endListener = new DynamicAnimation.OnAnimationEndListener() {
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
                try {
                    windowManager.updateViewLayout(contentView, windowLayoutParams);
                } catch (IllegalArgumentException ignored) {}
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            scaleGestureDetector.setQuickScaleEnabled(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scaleGestureDetector.setStylusScaleEnabled(false);
        }
        gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureDetectorFixDoubleTap.OnGestureListener() {
            private float startPipX, startPipY;

            @Override
            public boolean onDown(MotionEvent e) {
                if (isShowingControls) {
                    for (int i = 1; i < contentFrameLayout.getChildCount(); i++) {
                        View child = contentFrameLayout.getChildAt(i);
                        boolean consumed = child.dispatchTouchEvent(e);
                        if (consumed) {
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
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (controlsAnimator != null) {
                    return true;
                }

                if (postedDismissControls) {
                    AndroidUtilities.cancelRunOnUIThread(dismissControlsCallback);
                    postedDismissControls = false;
                }

                isShowingControls = !isShowingControls;
                toggleControls(isShowingControls);

                if (isShowingControls && !postedDismissControls) {
                    AndroidUtilities.runOnUIThread(dismissControlsCallback, 2500);
                    postedDismissControls = true;
                }

                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (photoViewer == null || photoViewer.getVideoPlayer() == null && photoViewerWebView == null || isDismissing || isVideoCompleted || isScrolling || scaleGestureDetector.isInProgress() || !canLongClick) {
                    return false;
                }

                VideoPlayer videoPlayer = photoViewer.getVideoPlayer();
                boolean forward = e.getX() >= getSuggestedWidth() * scaleFactor * 0.5f;

                long current = getCurrentPosition();
                long total = getDuration();
                if (current == C.TIME_UNSET || total < 15 * 1000) {
                    return false;
                }

                long old = current;
                if (forward) {
                    current += 10000;
                } else {
                    current -= 10000;
                }
                if (old != current) {
                    boolean apply = true;
                    if (current > total) {
                        current = total;
                    } else if (current < 0) {
                        if (current < -9000) {
                            apply = false;
                        }
                        current = 0;
                    }
                    if (apply) {
                        videoForwardDrawable.setOneShootAnimation(true);
                        videoForwardDrawable.setLeftSide(!forward);
                        videoForwardDrawable.addTime(10000);
                        seekTo(current);
                        onUpdateRewindProgressUiInternal(forward ? 10000 : -10000, current / (float) total, true);
                        if (!isShowingControls) {
                            toggleControls(isShowingControls = true);
                            if (!postedDismissControls) {
                                postedDismissControls = true;
                                AndroidUtilities.runOnUIThread(dismissControlsCallback, 2500);
                            }
                        }
                    }
                    return true;
                }

                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!hasDoubleTap()) {
                    return onSingleTapConfirmed(e);
                }
                return super.onSingleTapUp(e);
            }

            @Override
            public boolean hasDoubleTap() {
                if (photoViewer == null || photoViewer.getVideoPlayer() == null && photoViewerWebView == null || isDismissing || isVideoCompleted || isScrolling || scaleGestureDetector.isInProgress() || !canLongClick) {
                    return false;
                }

                long current = getCurrentPosition();
                long total = getDuration();
                return current != C.TIME_UNSET && total >= 15 * 1000;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isScrolling && !isScrollDisallowed) {
                    pipXSpring.setStartVelocity(velocityX)
                            .setStartValue(pipX)
                            .getSpring()
                            .setFinalPosition(pipX + pipWidth / 2f + velocityX / 7f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
                    pipXSpring.start();

                    pipYSpring.setStartVelocity(velocityX)
                            .setStartValue(pipY)
                            .getSpring()
                            .setFinalPosition(MathUtils.clamp(pipY + velocityY / 10f, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - pipHeight - AndroidUtilities.dp(SIDE_PADDING_DP)));
                    pipYSpring.start();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!isScrolling && controlsAnimator == null && !isScrollDisallowed) {
                    if (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop) {
                        isScrolling = true;

                        pipXSpring.cancel();
                        pipYSpring.cancel();

                        canLongClick = false;
                        cancelRewind();
                        AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                    }
                }
                if (isScrolling) {
                    float wasPipX = pipX;
                    float newPipX = startPipX + e2.getRawX() - e1.getRawX();
                    pipY = startPipY + e2.getRawY() - e1.getRawY();
                    if (newPipX <= -pipWidth * 0.25f || newPipX >= AndroidUtilities.displaySize.x - pipWidth * 0.75f) {
                        if (!onSideToDismiss) {
                            pipXSpring.setStartValue(wasPipX)
                                    .getSpring()
                                    .setFinalPosition(newPipX + pipWidth / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP) - pipWidth);
                            pipXSpring.start();
                        }
                        onSideToDismiss = true;
                    } else if (onSideToDismiss) {
                        if (onSideToDismiss) {
                            pipXSpring.addEndListener((animation, canceled, value, velocity) -> {
                                if (!canceled) {
                                    pipXSpring.getSpring().setFinalPosition(newPipX + pipWidth / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
                                }
                            });

                            pipXSpring.setStartValue(wasPipX)
                                    .getSpring()
                                    .setFinalPosition(newPipX);
                            pipXSpring.start();
                        }
                        onSideToDismiss = false;
                    } else {
                        if (pipXSpring.isRunning()) {
                            pipXSpring.getSpring().setFinalPosition(newPipX);
                        } else {
                            windowLayoutParams.x = (int) (pipX = newPipX);
                            getPipConfig().setPipX(newPipX);
                        }
                        windowLayoutParams.y = (int) pipY;
                        getPipConfig().setPipY(pipY);
                        windowManager.updateViewLayout(contentView, windowLayoutParams);
                    }
                }
                return true;
            }
        });
        contentFrameLayout = new FrameLayout(context) {
            private Path path = new Path();

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                    if (ev.getPointerCount() == 1) {
                        canLongClick = true;
                        longClickStartPoint = new float[]{ev.getX(), ev.getY()};
                        AndroidUtilities.runOnUIThread(longClickCallback, 500);
                    } else {
                        canLongClick = false;
                        cancelRewind();
                        AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                    }
                }

                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
                    canLongClick = false;
                    cancelRewind();
                    AndroidUtilities.cancelRunOnUIThread(longClickCallback);
                }

                if (consumingChild != null) {
                    MotionEvent newEvent = MotionEvent.obtain(ev);
                    newEvent.offsetLocation(consumingChild.getX(), consumingChild.getY());
                    boolean consumed = consumingChild.dispatchTouchEvent(ev);
                    newEvent.recycle();

                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
                        consumingChild = null;
                    }

                    if (consumed) {
                        return true;
                    }
                }
                MotionEvent temp = MotionEvent.obtain(ev);
                temp.offsetLocation(ev.getRawX() - ev.getX(), ev.getRawY() - ev.getY());
                boolean scaleDetector = scaleGestureDetector.onTouchEvent(temp);
                temp.recycle();
                boolean detector = !scaleGestureDetector.isInProgress() && gestureDetector.onTouchEvent(ev);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
                    isScrolling = false;
                    isScrollDisallowed = false;

                    if (onSideToDismiss) {
                        onSideToDismiss = false;

                        dimissAndDestroy();
                    } else {
                        if (!pipXSpring.isRunning()) {
                            pipXSpring.setStartValue(pipX)
                                    .getSpring()
                                    .setFinalPosition(pipX + pipWidth / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
                            pipXSpring.start();
                        }

                        if (!pipYSpring.isRunning()) {
                            pipYSpring.setStartValue(pipY)
                                    .getSpring()
                                    .setFinalPosition(MathUtils.clamp(pipY, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - pipHeight - AndroidUtilities.dp(SIDE_PADDING_DP)));
                            pipYSpring.start();
                        }
                    }
                }
                return scaleDetector || detector;
            }

            @Override
            protected void onConfigurationChanged(Configuration newConfig) {
                AndroidUtilities.checkDisplaySize(getContext(), newConfig);
                pipConfig = null;

                if (pipWidth != getSuggestedWidth() * scaleFactor || pipHeight != getSuggestedHeight() * scaleFactor) {
                    windowLayoutParams.width = pipWidth = (int) (getSuggestedWidth() * scaleFactor);
                    windowLayoutParams.height = pipHeight = (int) (getSuggestedHeight() * scaleFactor);
                    windowManager.updateViewLayout(contentView, windowLayoutParams);

                    pipXSpring.setStartValue(pipX)
                            .getSpring()
                            .setFinalPosition(pipX + (getSuggestedWidth() * scaleFactor) / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - (getSuggestedWidth() * scaleFactor) - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
                    pipXSpring.start();

                    pipYSpring.setStartValue(pipY)
                            .getSpring()
                            .setFinalPosition(MathUtils.clamp(pipY, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - (getSuggestedHeight() * scaleFactor) - AndroidUtilities.dp(SIDE_PADDING_DP)));
                    pipYSpring.start();
                }
            }

            @Override
            public void draw(Canvas canvas) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    super.draw(canvas);
                } else {
                    canvas.save();
                    canvas.clipPath(path);
                    super.draw(canvas);
                    canvas.restore();
                }
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);

                path.rewind();
                AndroidUtilities.rectTmp.set(0, 0, w, h);
                path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(ROUNDED_CORNERS_DP), AndroidUtilities.dp(ROUNDED_CORNERS_DP), Path.Direction.CW);
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
            public void draw(Canvas canvas) {
                canvas.save();
                canvas.scale(pipWidth / (float)contentFrameLayout.getWidth(), pipHeight / (float)contentFrameLayout.getHeight());
                super.draw(canvas);
                canvas.restore();
            }
        };
        contentView.addView(contentFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            contentFrameLayout.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dp(ROUNDED_CORNERS_DP));
                }
            });
            contentFrameLayout.setClipToOutline(true);
        }
        contentFrameLayout.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_actionBar));

        innerView = pipContentView;
        if (innerView.getParent() != null) {
            ((ViewGroup)innerView.getParent()).removeView(innerView);
        }
        contentFrameLayout.addView(innerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
            protected void onDraw(Canvas canvas) {
                if (videoForwardDrawable.isAnimating()) {
                    videoForwardDrawable.setBounds(getLeft(), getTop(), getRight(), getBottom());
                    videoForwardDrawable.draw(canvas);
                }
            }
        };
        controlsView.setWillNotDraw(false);
        controlsView.setAlpha(0f);
        View scrim = new View(context);
        scrim.setBackgroundColor(0x4C000000);
        controlsView.addView(scrim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        int padding = AndroidUtilities.dp(8);
        int margin = 4;
        int buttonSize = 38;

        ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.pip_video_close);
        closeButton.setColorFilter(Theme.getColor(Theme.key_voipgroup_actionBarItems), PorterDuff.Mode.MULTIPLY);
        closeButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        closeButton.setPadding(padding, padding, padding, padding);
        closeButton.setOnClickListener(v -> dimissAndDestroy());
        controlsView.addView(closeButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.RIGHT, 0, margin, margin, 0));

        ImageView expandButton = new ImageView(context);
        expandButton.setImageResource(R.drawable.pip_video_expand);
        expandButton.setColorFilter(Theme.getColor(Theme.key_voipgroup_actionBarItems), PorterDuff.Mode.MULTIPLY);
        expandButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        expandButton.setPadding(padding, padding, padding, padding);
        expandButton.setOnClickListener(v -> {
            boolean isResumedByActivityManager = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ActivityManager activityManager = (ActivityManager) v.getContext().getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> appProcessInfos = activityManager.getRunningAppProcesses();
                if (appProcessInfos != null && !appProcessInfos.isEmpty()) {
                    isResumedByActivityManager = appProcessInfos.get(0).importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
                }
            }

            if (!inAppOnly && (!isResumedByActivityManager || !LaunchActivity.isResumed)) {
                LaunchActivity.onResumeStaticCallback = v::callOnClick;

                Context ctx = ApplicationLoader.applicationContext;
                Intent intent = new Intent(ctx, LaunchActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(intent);
            } else {
                if (parentSheet != null) {
                    parentSheet.exitFromPip();
                } else if (photoViewer != null) {
                    photoViewer.exitFromPip();
                }
            }
        });
        controlsView.addView(expandButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.RIGHT, 0, margin, buttonSize + margin + 6, 0));

        playPauseButton = new ImageView(context);
        playPauseButton.setColorFilter(Theme.getColor(Theme.key_voipgroup_actionBarItems), PorterDuff.Mode.MULTIPLY);
        playPauseButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
        playPauseButton.setOnClickListener(v -> {
            if (photoViewer == null) {
                return;
            }
            if (photoViewerWebView != null) {
                if (photoViewerWebView.isPlaying()) {
                    photoViewerWebView.pauseVideo();
                } else {
                    photoViewerWebView.playVideo();
                }
            } else {
                VideoPlayer videoPlayer = photoViewer.getVideoPlayer();
                if (videoPlayer == null) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                } else {
                    videoPlayer.play();
                }
            }
            updatePlayButton();
        });
        isWebView = innerView instanceof WebView || innerView instanceof PhotoViewerWebView;
        playPauseButton.setVisibility(isWebView && (photoViewerWebView == null || !photoViewerWebView.isControllable()) ? View.GONE : View.VISIBLE);
        controlsView.addView(playPauseButton, LayoutHelper.createFrame(buttonSize, buttonSize, Gravity.CENTER));

        videoProgressView = new VideoProgressView(context);
        controlsView.addView(videoProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        contentFrameLayout.addView(controlsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        windowManager = (WindowManager) (inAppOnly ? activity : ApplicationLoader.applicationContext).getSystemService(Context.WINDOW_SERVICE);

        windowLayoutParams = createWindowLayoutParams(inAppOnly);
        windowLayoutParams.width = pipWidth;
        windowLayoutParams.height = pipHeight;
        if (savedPipX != -1) {
            windowLayoutParams.x = (int) (pipX = savedPipX + pipWidth / 2f >= AndroidUtilities.displaySize.x / 2f ? AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP) : AndroidUtilities.dp(SIDE_PADDING_DP));
        } else {
            windowLayoutParams.x = (int) (pipX = AndroidUtilities.displaySize.x - pipWidth - AndroidUtilities.dp(SIDE_PADDING_DP));
        }
        if (savedPipY != -1) {
            windowLayoutParams.y = (int) (pipY = MathUtils.clamp(savedPipY, AndroidUtilities.dp(SIDE_PADDING_DP), AndroidUtilities.displaySize.y - AndroidUtilities.dp(SIDE_PADDING_DP) - pipHeight));
        } else {
            windowLayoutParams.y = (int) (pipY = AndroidUtilities.dp(SIDE_PADDING_DP));
        }
        windowLayoutParams.dimAmount = 0f;
        windowLayoutParams.flags = FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        // Animate is a flag for PhotoViewer transition, not ours
        if (animate) {
            windowManager.addView(contentView, windowLayoutParams);
        } else {
            contentView.setAlpha(0f);
            contentView.setScaleX(0.1f);
            contentView.setScaleY(0.1f);
            windowManager.addView(contentView, windowLayoutParams);

            AnimatorSet set = new AnimatorSet();
            set.setDuration(250);
            set.setInterpolator(CubicBezierInterpolator.DEFAULT);
            set.playTogether(
                    ObjectAnimator.ofFloat(contentView, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(contentView, View.SCALE_X, 1f),
                    ObjectAnimator.ofFloat(contentView, View.SCALE_Y, 1f)
            );
            set.start();
        }
        return true;
    }

    @SuppressLint("WrongConstant")
    private WindowManager.LayoutParams createWindowLayoutParams(boolean inAppOnly) {
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;

        if (!inAppOnly && AndroidUtilities.checkInlinePermissions(ApplicationLoader.applicationContext)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        } else {
            windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        }

        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        return windowLayoutParams;
    }

    private final class VideoProgressView extends View {
        private Paint progressPaint = new Paint(), bufferPaint = new Paint();

        public VideoProgressView(Context context) {
            super(context);

            progressPaint.setColor(Color.WHITE);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
            bufferPaint.setColor(progressPaint.getColor());
            bufferPaint.setAlpha((int) (progressPaint.getAlpha() * 0.3f));
            bufferPaint.setStyle(Paint.Style.STROKE);
            bufferPaint.setStrokeCap(Paint.Cap.ROUND);
            bufferPaint.setStrokeWidth(AndroidUtilities.dp(2));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (isWebView && (photoViewerWebView == null || !photoViewerWebView.isControllable())) {
                return;
            }

            int width = getWidth();

            int progressSidePadding = AndroidUtilities.dp(10);

            int progressLeft = progressSidePadding;
            int progressRight = progressLeft + (int) ((width - progressLeft - progressSidePadding) * videoProgress);
            float y = getHeight() - AndroidUtilities.dp(8);
            if (bufferProgress != 0) {
                canvas.drawLine(progressLeft, y, progressLeft + (width - progressLeft - progressSidePadding) * bufferProgress, y, bufferPaint);
            }
            canvas.drawLine(progressLeft, y, progressRight, y, progressPaint);
        }
    }

    private final static class PipConfig {
        private SharedPreferences mPrefs;

        private PipConfig(int width, int height) {
            mPrefs = ApplicationLoader.applicationContext.getSharedPreferences("pip_layout_" + width + "_" + height, Context.MODE_PRIVATE);
        }

        private void setPipX(float x) {
            mPrefs.edit().putFloat("x", x).apply();
        }

        private void setPipY(float y) {
            mPrefs.edit().putFloat("y", y).apply();
        }

        private void setScaleFactor(float scaleFactor) {
            mPrefs.edit().putFloat("scale_factor", scaleFactor).apply();
        }

        private float getScaleFactor() {
            return mPrefs.getFloat("scale_factor", 1f);
        }

        private float getPipX() {
            return mPrefs.getFloat("x", -1);
        }

        private float getPipY() {
            return mPrefs.getFloat("y", -1);
        }
    }
}