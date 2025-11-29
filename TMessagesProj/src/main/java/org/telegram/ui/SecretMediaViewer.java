/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.util.Property;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Scroller;
import org.telegram.ui.Components.TimerParticles;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Components.VideoPlayerSeekBar;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.HintView2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class SecretMediaViewer implements NotificationCenter.NotificationCenterDelegate, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            processTouchEvent(event);
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            SecretMediaViewer.this.onDraw(canvas);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return child != aspectRatioFrameLayout && super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            centerImage.onAttachedToWindow();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            centerImage.onDetachedFromWindow();
        }
    }

    private class SecretDeleteTimer extends FrameLayout {

        private Paint afterDeleteProgressPaint;
        private Paint circlePaint;
        private Paint particlePaint;
        private RectF deleteProgressRect = new RectF();
        private TimerParticles timerParticles = new TimerParticles();

        private boolean once;
        private long destroyTime;
        private long destroyTtl;
        private boolean useVideoProgress;

        private RLottieDrawable drawable;

        private TextPaint oncePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private StaticLayout onceLayout;
        private float onceLayoutWidth, onceLayoutHeight;

        public SecretDeleteTimer(Context context) {
            super(context);
            setWillNotDraw(false);

            particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            particlePaint.setStrokeWidth(dp(1.5f));
            particlePaint.setColor(0xffe6e6e6);
            particlePaint.setStrokeCap(Paint.Cap.ROUND);
            particlePaint.setStyle(Paint.Style.STROKE);

            afterDeleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            afterDeleteProgressPaint.setStyle(Paint.Style.STROKE);
            afterDeleteProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            afterDeleteProgressPaint.setColor(0xffe6e6e6);
            afterDeleteProgressPaint.setStrokeWidth(dp(2));

            circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(0x7f000000);

            drawable = new RLottieDrawable(R.raw.fire_on, "" + R.raw.fire_on, dp(16), dp(16));
            drawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            drawable.setMasterParent(this);
            drawable.start();
        }

        private void setDestroyTime(long time, long ttl, boolean videoProgress) {
            once = false;
            destroyTime = time;
            destroyTtl = ttl;
            useVideoProgress = videoProgress;
            drawable.start();
            invalidate();
        }

        private void setOnce() {
            once = true;
            oncePaint.setTextSize(dp(13));
            oncePaint.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            oncePaint.setColor(Color.WHITE);
            onceLayout = new StaticLayout("1", oncePaint, 999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            onceLayoutWidth = onceLayout.getLineCount() > 0 ? onceLayout.getLineWidth(0) : 0;
            onceLayoutHeight = onceLayout.getHeight();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            final float cx = getMeasuredWidth() - dp(35);
            final float cy = getMeasuredHeight() / 2f;
            final float r = dpf2(10.5f);
            deleteProgressRect.set(cx - r, cy - r, cx + r, cy + r);
            setPivotX(cx);
            setPivotY(cy);
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            if (currentMessageObject == null || currentMessageObject.messageOwner.destroyTime == 0 && currentMessageObject.messageOwner.ttl != 0x7FFFFFFF) {
                return;
            }

            float progress;

            if (useVideoProgress) {
                if (videoPlayer != null) {
                    long duration = videoPlayer.getDuration();
                    long position = videoPlayer.getCurrentPosition();
                    if (duration != C.TIME_UNSET && position != C.TIME_UNSET) {
                        progress = 1.0f - (position / (float) duration);
                    } else {
                        progress = 1f;
                    }
                } else {
                    progress = 1f;
                }
            } else {
                if (destroyTime == 0) {
                    progress = 1f;
                } else {
                    long msTime = System.currentTimeMillis() + ConnectionsManager.getInstance(currentAccount).getTimeDifference() * 1000;
                    progress = Math.max(0, destroyTime - msTime) / (destroyTtl * 1000.0f);
                }

            }

            if (once) {
                canvas.save();
                canvas.translate(deleteProgressRect.centerX() - onceLayoutWidth / 2f, deleteProgressRect.centerY() - onceLayoutHeight / 2f);
                onceLayout.draw(canvas);
                canvas.restore();

                canvas.drawArc(deleteProgressRect, 90, 180, false, afterDeleteProgressPaint);
                final int dashes = 5;
                final int gaps = dashes + 1;
                final float dashWeight = 1f, gapWeight = 1.5f;
                final float dashSweep = dashWeight / (dashes * dashWeight + gaps * gapWeight) * 180;
                final float gapSweep = gapWeight / (dashes * dashWeight + gaps * gapWeight) * 180;
                float a = gapSweep;
                for (int i = 0; i < dashes; ++i) {
                    canvas.drawArc(deleteProgressRect, 270 + a, dashSweep, false, afterDeleteProgressPaint);
                    a += dashSweep + gapSweep;
                }
                timerParticles.draw(canvas, particlePaint, deleteProgressRect, 0, 1.0f);
            } else {
                final float cx = deleteProgressRect.centerX();
                final float cy = deleteProgressRect.centerY() - dp(1);
                final float r = dp(8);
                drawable.setBounds((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
                drawable.draw(canvas);
                float radProgress = -360 * progress;
                canvas.drawArc(deleteProgressRect, -90, radProgress, false, afterDeleteProgressPaint);
                timerParticles.draw(canvas, particlePaint, deleteProgressRect, radProgress, 1.0f);
            }
            invalidate();
        }
    }

    private class PhotoBackgroundDrawable extends ColorDrawable {

        private Runnable drawRunnable;
        private int frame;

        public PhotoBackgroundDrawable(int color) {
            super(color);
        }

        @Keep
        @Override
        public void setAlpha(int alpha) {
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).drawerLayoutContainer.setAllowDrawContent(!isPhotoVisible || alpha != 255);
            }
            super.setAlpha(alpha);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            if (getAlpha() != 0) {
                if (frame == 2 && drawRunnable != null) {
                    drawRunnable.run();
                    drawRunnable = null;
                } else {
                    invalidateSelf();
                }
                frame++;
            }
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(left, top, right, bottom + AndroidUtilities.navigationBarHeight);
        }

        @Override
        public void setBounds(@NonNull Rect bounds) {
            bounds.bottom += AndroidUtilities.navigationBarHeight;
            super.setBounds(bounds);
        }
    }

    private int currentAccount;
    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayoutDrawer containerView;
    private View navigationBar;
    private ImageReceiver centerImage = new ImageReceiver();
    private SecretDeleteTimer secretDeleteTimer;
    private HintView2 secretHint;
    private boolean isVisible;
    private long currentDialogId;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private TextureView videoTextureView;
    private VideoPlayer videoPlayer;
    private boolean isPlaying;
    private ActionBar actionBar;
    private AnimatorSet currentActionBarAnimation;
    private boolean videoWatchedOneTime;
    private boolean closeVideoAfterWatch;
    private boolean isVideo;
    private long openTime;
    private long closeTime;
    private boolean disableShowCheck;
    private PhotoViewer.PhotoViewerProvider currentProvider;
    private int videoWidth, videoHeight;

    private VideoPlayerSeekBar seekbar;
    private View seekbarView;
    private SimpleTextView videoPlayerTime;
    private View seekbarBackground;
    private VideoPlayerControlFrameLayout seekbarContainer;
    private ImageView playButton;
    private PlayPauseDrawable playButtonDrawable;

    private FrameLayout captionContainer;
    private TextSelectionHelper.SimpleTextSelectionHelper textSelectionHelper;
    private PhotoViewer.CaptionTextViewSwitcher captionTextViewSwitcher;
    private PhotoViewer.CaptionScrollView captionScrollView;

    private int playerRetryPlayCount;

    private boolean textureUploaded;
    private boolean videoCrossfadeStarted;
    private float videoCrossfadeAlpha;
    private long videoCrossfadeAlphaLastTime;

    private Object lastInsets;

    private MessageObject currentMessageObject;
    private ImageReceiver.BitmapHolder currentThumb;

    private int[] coords = new int[2];

    private boolean isPhotoVisible;

    private boolean isActionBarVisible = true;

    private PhotoBackgroundDrawable photoBackgroundDrawable = new PhotoBackgroundDrawable(0xff000000);
    private Paint blackPaint = new Paint();

    private int photoAnimationInProgress;
    private long photoTransitionAnimationStartTime;
    private Runnable photoAnimationEndRunnable;

    private boolean draggingDown;
    private float dragY;
    private float clipTop;
    private float clipBottom;
    private float clipTopOrigin;
    private float clipBottomOrigin;
    private float clipHorizontal;
    private float translationX;
    private float translationY;
    private float scale = 1;
    private boolean useOvershootForScale;
    private float animateToX;
    private float animateToY;
    private float animateToScale;
    private float animateToClipTop;
    private float animateToClipBottom;
    private float animateToClipTopOrigin;
    private float animateToClipBottomOrigin;
    private float animateToClipHorizontal;
    private int[] animateFromRadius;
    private boolean animateToRadius;
    @Keep
    private float animationValue;
    private int currentRotation;
    private long animationStartTime;
    private AnimatorSet imageMoveAnimation;
    private GestureDetector gestureDetector;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator(1.5f);
    private float pinchStartDistance;
    private float pinchStartScale = 1;
    private float pinchCenterX;
    private float pinchCenterY;
    private float pinchStartX;
    private float pinchStartY;
    private float moveStartX;
    private float moveStartY;
    private float minX;
    private float maxX;
    private float minY;
    private float maxY;
    private boolean zooming;
    private boolean moving;
    private boolean doubleTap;
    private boolean invalidCoords;
    private boolean canDragDown = true;
    private boolean zoomAnimation;
    private boolean discardTap;
    private VelocityTracker velocityTracker;
    private Scroller scroller;

    private boolean closeAfterAnimation;

    @SuppressLint("StaticFieldLeak")
    private static volatile SecretMediaViewer Instance = null;
    public static SecretMediaViewer getInstance() {
        SecretMediaViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (PhotoViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new SecretMediaViewer();
                }
            }
        }
        return localInstance;
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDeleted) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            if (currentMessageObject == null) {
                return;
            }
            long channelId = (Long) args[1];
            if (channelId != 0) {
                return;
            }
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
            if (markAsDeletedMessages.contains(currentMessageObject.getId())) {
                if (isVideo && !videoWatchedOneTime) {
                    closeVideoAfterWatch = true;
                } else {
                    if (!closePhoto(true, true)) {
                        closeAfterAnimation = true;
                    }
                }
            }
        } else if (id == NotificationCenter.didCreatedNewDeleteTask) {
            if (currentMessageObject == null || secretDeleteTimer == null) {
                return;
            }
            long dialogId = (long) args[0];
            if (dialogId != currentDialogId) {
                return;
            }
            SparseArray<ArrayList<Integer>> mids = (SparseArray<ArrayList<Integer>>) args[1];
            for (int i = 0; i < mids.size(); i++) {
                int key = mids.keyAt(i);
                ArrayList<Integer> arr = mids.get(key);
                for (int a = 0; a < arr.size(); a++) {
                    long mid = arr.get(a);
                    if (currentMessageObject.getId() == mid) {
                        currentMessageObject.messageOwner.destroyTime = key;
                        secretDeleteTimer.invalidate();
                        return;
                    }
                }
            }
        } else if (id == NotificationCenter.updateMessageMedia) {
            TLRPC.Message message = (TLRPC.Message) args[0];
            if (currentMessageObject.getId() == message.id) {
                if (isVideo && !videoWatchedOneTime) {
                    closeVideoAfterWatch = true;
                } else {
                    if (!closePhoto(true, true)) {
                        closeAfterAnimation = true;
                    }
                }
            }
        }
    }

    private void preparePlayer(File file) {
        if (parentActivity == null) {
            return;
        }
        releasePlayer();
        if (videoTextureView == null) {
            aspectRatioFrameLayout = new AspectRatioFrameLayout(parentActivity);
            aspectRatioFrameLayout.setVisibility(View.VISIBLE);
            containerView.addView(aspectRatioFrameLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            videoTextureView = new TextureView(parentActivity);
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }
        textureUploaded = false;
        videoCrossfadeStarted = false;
//        videoTextureView.setAlpha(videoCrossfadeAlpha = 0.0f);
        videoTextureView.setAlpha(1f);
        if (videoPlayer == null) {
            videoPlayer = new VideoPlayer() {
                @Override
                public void setPlayWhenReady(boolean playWhenReady) {
                    super.setPlayWhenReady(playWhenReady);
                    playButtonDrawable.setPause(playWhenReady);
                }

                @Override
                public void play() {
                    super.play();
                    playButtonDrawable.setPause(true);
                }

                @Override
                public void pause() {
                    super.pause();
                    playButtonDrawable.setPause(false);
                }
            };
            videoPlayer.setTextureView(videoTextureView);
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (videoPlayer == null || currentMessageObject == null) {
                        return;
                    }
                    AndroidUtilities.cancelRunOnUIThread(updateProgressRunnable);
                    AndroidUtilities.runOnUIThread(updateProgressRunnable);
                    if (playbackState != ExoPlayer.STATE_ENDED && playbackState != ExoPlayer.STATE_IDLE) {
                        try {
                            parentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else {
                        try {
                            parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    if (playbackState == ExoPlayer.STATE_READY && aspectRatioFrameLayout.getVisibility() != View.VISIBLE) {
                        aspectRatioFrameLayout.setVisibility(View.VISIBLE);
                    }
                    if (videoPlayer.isPlaying() && playbackState != ExoPlayer.STATE_ENDED) {
                        if (!isPlaying) {
                            isPlaying = true;
                        }
                    } else if (isPlaying) {
                        isPlaying = false;
                        if (playbackState == ExoPlayer.STATE_ENDED) {
                            videoWatchedOneTime = true;
                            if (closeVideoAfterWatch) {
                                closePhoto(true, !ignoreDelete);
                            } else {
                                videoPlayer.seekTo(0);
                                videoPlayer.play();
                            }
                        }
                    }
                }

                @Override
                public void onError(VideoPlayer player, Exception e) {
                    if (playerRetryPlayCount > 0) {
                        playerRetryPlayCount--;
                        AndroidUtilities.runOnUIThread(() -> preparePlayer(file), 100);
                    } else {
                        FileLog.e(e);
                    }
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    if (aspectRatioFrameLayout != null) {
                        if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                            int temp = width;
                            width = height;
                            height = temp;
                        }
                        aspectRatioFrameLayout.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height, unappliedRotationDegrees);
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    if (!textureUploaded) {
                        textureUploaded = true;
                        containerView.invalidate();
                    }
                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }
            });
        }
        videoPlayer.preparePlayer(Uri.fromFile(file), "other");
        videoPlayer.setPlayWhenReady(true);
        playButtonDrawable.setPause(true);
    }

    private final Runnable updateProgressRunnable = () -> {
        if (videoPlayer == null) {
            return;
        }

        long pos = videoPlayer.getCurrentPosition();
        long duration = videoPlayer.getDuration();

        if (duration == C.TIME_UNSET) {
            pos = duration = 0;
        }
        if (duration > 0 && !seekbar.isDragging()) {
            seekbar.setProgress(pos / (float) duration);
            seekbarView.invalidate();
        }
        updateVideoPlayerTime();

        if (videoPlayer.isPlaying()) {
            AndroidUtilities.runOnUIThread(this.updateProgressRunnable, 17);
        }
    };

    private final int[] videoPlayerCurrentTime = new int[2];
    private final int[] videoPlayerTotalTime = new int[2];
    private void updateVideoPlayerTime() {
        Arrays.fill(videoPlayerCurrentTime, 0);
        Arrays.fill(videoPlayerTotalTime, 0);
        if (videoPlayer != null) {
            long current = Math.max(0, videoPlayer.getCurrentPosition());
            long total = Math.max(0, videoPlayer.getDuration());
            current /= 1000;
            total /= 1000;
            videoPlayerCurrentTime[0] = (int) (current / 60);
            videoPlayerCurrentTime[1] = (int) (current % 60);
            videoPlayerTotalTime[0] = (int) (total / 60);
            videoPlayerTotalTime[1] = (int) (total % 60);
        }
        String current, total;
        if (videoPlayerCurrentTime[0] >= 60) {
            current = String.format(Locale.ROOT, "%02d:%02d:%02d", videoPlayerCurrentTime[0] / 60, videoPlayerCurrentTime[0] % 60, videoPlayerCurrentTime[1]);
        } else {
            current = String.format(Locale.ROOT, "%02d:%02d", videoPlayerCurrentTime[0], videoPlayerCurrentTime[1]);
        }
        if (videoPlayerTotalTime[0] >= 60) {
            total = String.format(Locale.ROOT, "%02d:%02d:%02d", videoPlayerTotalTime[0] / 60, videoPlayerTotalTime[0] % 60, videoPlayerTotalTime[1]);
        } else {
            total = String.format(Locale.ROOT, "%02d:%02d", videoPlayerTotalTime[0], videoPlayerTotalTime[1]);
        }
        videoPlayerTime.setText(String.format(Locale.ROOT, "%s / %s", current, total));
    };

    private void releasePlayer() {
        if (videoPlayer != null) {
            playerRetryPlayCount = 0;
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        }
        try {
            if (parentActivity != null) {
                parentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (aspectRatioFrameLayout != null) {
            containerView.removeView(aspectRatioFrameLayout);
            aspectRatioFrameLayout = null;
        }
        if (videoTextureView != null) {
            videoTextureView = null;
        }
        isPlaying = false;
    }

    public void setParentActivity(Activity activity) {
        currentAccount = UserConfig.selectedAccount;
        centerImage.setCurrentAccount(currentAccount);
        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;

        scroller = new Scroller(activity);

        windowView = new FrameLayout(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                if (lastInsets != null) {
                    WindowInsets insets = (WindowInsets) lastInsets;
                    if (AndroidUtilities.incorrectDisplaySizeFix) {
                        if (heightSize > AndroidUtilities.displaySize.y) {
                            heightSize = AndroidUtilities.displaySize.y;
                        }
                        heightSize += AndroidUtilities.statusBarHeight;
                    }
                    heightSize -= insets.getSystemWindowInsetBottom();
                    widthSize -= insets.getSystemWindowInsetRight();
                } else {
                    if (heightSize > AndroidUtilities.displaySize.y) {
                        heightSize = AndroidUtilities.displaySize.y;
                    }
                }
                setMeasuredDimension(widthSize, heightSize);
                if (lastInsets != null) {
                    widthSize -= ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
                }
                containerView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
            }

            @SuppressWarnings("DrawAllocation")
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int x = 0;
                if (lastInsets != null) {
                    x += ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
                }
                containerView.layout(x, 0, x + containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
                if (changed) {
                    if (imageMoveAnimation == null) {
                        scale = 1;
                        translationX = 0;
                        translationY = 0;
                    }
                    updateMinMax(scale);
                }
            }
        };
        windowView.setBackgroundDrawable(photoBackgroundDrawable);
        windowView.setFocusable(true);
        windowView.setFocusableInTouchMode(true);
        windowView.setClipChildren(false);
        windowView.setClipToPadding(false);

        containerView = new FrameLayoutDrawer(activity) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (secretDeleteTimer != null) {
                    int y = (ActionBar.getCurrentActionBarHeight() - secretDeleteTimer.getMeasuredHeight()) / 2 + AndroidUtilities.statusBarHeight;
                    secretDeleteTimer.layout(secretDeleteTimer.getLeft(), y, secretDeleteTimer.getRight(), y + secretDeleteTimer.getMeasuredHeight());
                }
                if (secretHint != null && secretDeleteTimer != null) {
                    int y = (ActionBar.getCurrentActionBarHeight() - secretDeleteTimer.getMeasuredHeight()) / 2 + AndroidUtilities.statusBarHeight + secretDeleteTimer.getMeasuredHeight() - dp(10);
                    secretHint.layout(secretHint.getLeft(), y, secretHint.getRight(), y + secretHint.getMeasuredHeight());
                }
                if (captionScrollView != null) {
                    int y = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
                    captionScrollView.layout(captionScrollView.getLeft(), y, captionScrollView.getRight(), y + captionScrollView.getMeasuredHeight());
                }
                if (navigationBar != null) {
                    navigationBar.layout(0, bottom - top, right - left, bottom - top + AndroidUtilities.navigationBarHeight);
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = getMeasuredWidth();
                int height = getMeasuredHeight();
                if (captionScrollView != null) {
                    captionScrollView.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight - (seekbarContainer.getVisibility() != View.VISIBLE ? 0 : seekbarContainer.getMeasuredHeight()), MeasureSpec.EXACTLY)
                    );
                }
                if (navigationBar != null) {
                    navigationBar.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.navigationBarHeight, MeasureSpec.EXACTLY)
                    );
                }
            }
        };
        navigationBar = new View(activity);
        navigationBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        containerView.addView(navigationBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        containerView.setFocusable(false);
        windowView.addView(containerView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerView.setLayoutParams(layoutParams);
        containerView.setFitsSystemWindows(true);
        containerView.setOnApplyWindowInsetsListener((v, insets) -> {
            WindowInsets oldInsets = (WindowInsets) lastInsets;
            lastInsets = insets;
            if (oldInsets == null || !oldInsets.toString().equals(insets.toString())) {
                windowView.requestLayout();
            }
            if (Build.VERSION.SDK_INT >= 30) {
                return WindowInsets.CONSUMED;
            } else {
                return insets.consumeSystemWindowInsets();
            }
        });
        containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        gestureDetector = new GestureDetector(containerView.getContext(), this);
        gestureDetector.setOnDoubleTapListener(this);

        actionBar = new ActionBar(activity) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                secretHint.setAlpha(alpha);
                secretDeleteTimer.setAlpha(alpha);
            }
        };
        actionBar.setTitleColor(0xffffffff);
        actionBar.setSubtitleColor(0xffffffff);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        actionBar.setOccupyStatusBar(true);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, false);
        actionBar.setItemsColor(Color.WHITE, false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitleRightMargin(dp(70));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closePhoto(true, false);
                }
            }
        });

        secretHint = new HintView2(activity, HintView2.DIRECTION_TOP);
        secretHint.setJoint(1, -26);
        secretHint.setPadding(dp(8), dp(8), dp(8), dp(8));
        containerView.addView(secretHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.TOP | Gravity.RIGHT, 0, 48, 0, 0));

        secretDeleteTimer = new SecretDeleteTimer(activity);
        containerView.addView(secretDeleteTimer, LayoutHelper.createFrame(119, 48, Gravity.TOP | Gravity.RIGHT, 0, 0, 0, 0));

        final VideoPlayerSeekBar.SeekBarDelegate seekBarDelegate = new VideoPlayerSeekBar.SeekBarDelegate() {
            @Override
            public void onSeekBarDrag(float progress) {
                if (videoPlayer != null) {
                    long duration = videoPlayer.getDuration();
                    if (duration != C.TIME_UNSET) {
                        videoPlayer.seekTo((long) (progress * duration), false);
                    }
                    videoPlayer.play();
                }
            }

            @Override
            public void onSeekBarContinuousDrag(float progress) {
                if (videoPlayer != null) {
                    videoPlayer.pause();
                    long duration = videoPlayer.getDuration();
                    if (duration != C.TIME_UNSET) {
                        videoPlayer.seekTo((long) (progress * duration), false);
                    }
                }
            }
        };
        seekbarContainer = new VideoPlayerControlFrameLayout(activity);
        seekbarBackground = new View(activity);
        seekbarBackground.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        seekbarContainer.addView(seekbarBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        videoPlayerTime = new SimpleTextView(containerView.getContext());
        videoPlayerTime.setTextColor(0xffffffff);
        videoPlayerTime.setGravity(Gravity.RIGHT | Gravity.TOP);
        videoPlayerTime.setTextSize(14);
        videoPlayerTime.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        seekbarContainer.addView(videoPlayerTime, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 15, 12, 0));
        seekbarView = new View(activity) {
            @Override
            protected void onDraw(Canvas canvas) {
                seekbar.draw(canvas, this);
            }
        };
        seekbar = new VideoPlayerSeekBar(seekbarView);
        seekbar.setHorizontalPadding(dp(2));
        seekbar.setColors(0x33ffffff, 0x33ffffff, Color.WHITE, Color.WHITE, Color.WHITE, 0x59ffffff);
        seekbar.setDelegate(seekBarDelegate);
        seekbarContainer.addView(seekbarView);
        containerView.addView(seekbarContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

        textSelectionHelper = new TextSelectionHelper.SimpleTextSelectionHelper(null, new DarkThemeResourceProvider()) {
            @Override
            public int getParentBottomPadding() {
                return 0;//AndroidUtilities.dp(80);
            }
        };
        textSelectionHelper.allowScrollPrentRelative = true;
        textSelectionHelper.useMovingOffset = false;

        captionTextViewSwitcher = new PhotoViewer.CaptionTextViewSwitcher(containerView.getContext());
        captionTextViewSwitcher.setFactory(() -> new PhotoViewer.CaptionTextView(activity, captionScrollView, textSelectionHelper, this::onLinkClick, this::onLinkLongPress));
        captionTextViewSwitcher.setVisibility(View.INVISIBLE);
        setCaptionHwLayerEnabled(true);

        playButton = new ImageView(activity);
        playButton.setBackground(Theme.createCircleDrawable(dp(64), 0x66000000));
        playButtonDrawable = new PlayPauseDrawable(28);
        playButtonDrawable.setCallback(playButton);
        playButton.setImageDrawable(playButtonDrawable);
        playButton.setScaleType(ImageView.ScaleType.CENTER);
        playButton.setScaleX(.6f);
        playButton.setScaleY(.6f);
        playButton.setAlpha(0f);
        playButton.setPivotX(dp(32));
        playButton.setPivotY(dp(32));
        containerView.addView(playButton, LayoutHelper.createFrame(64, 64, Gravity.CENTER));

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        windowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        AndroidUtilities.logFlagSecure();
        centerImage.setParentView(containerView);
        centerImage.setForceCrossfade(true);

        View overlay = textSelectionHelper.getOverlayView(windowView.getContext());
        if (overlay != null) {
            AndroidUtilities.removeFromParent(overlay);
            containerView.addView(overlay);
        }
        textSelectionHelper.setParentView(containerView);
        textSelectionHelper.setInvalidateParent();
    }

    private void setCurrentCaption(MessageObject messageObject, final CharSequence _caption, boolean translating, boolean animated) {
        final CharSequence caption = AnimatedEmojiSpan.cloneSpans(_caption, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW);
        if (captionScrollView == null) {
            captionContainer = new FrameLayout(containerView.getContext());
            captionTextViewSwitcher.setContainer(captionContainer);
            captionScrollView = new PhotoViewer.CaptionScrollView(containerView.getContext(), captionTextViewSwitcher, captionContainer) {
                @Override
                protected void onScrollStart() {
                    AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
                }

                @Override
                protected void onScrollUpdate() {
                    if (imageMoveAnimation == null) {
                        showPlayButton(getScrollY() < getMeasuredHeight() / 3f && isActionBarVisible, true);
                    }
                }

                @Override
                protected void onScrollEnd() {
                    if (isVideo && getScrollY() <= 0) {
                        AndroidUtilities.runOnUIThread(hideActionBarRunnable, 3000);
                    }
                }
            };
            captionTextViewSwitcher.setScrollView(captionScrollView);
            captionContainer.setClipChildren(false);
            captionScrollView.addView(captionContainer, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            containerView.addView(captionScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM, 0, 0, 0, 0));
            textSelectionHelper.getOverlayView(containerView.getContext()).bringToFront();
        }
        if (captionTextViewSwitcher.getParent() != captionContainer) {
            captionTextViewSwitcher.setMeasureAllChildren(true);
            captionContainer.addView(captionTextViewSwitcher, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        }

        final boolean isCaptionEmpty = TextUtils.isEmpty(caption);
        final boolean isCurrentCaptionEmpty = TextUtils.isEmpty(captionTextViewSwitcher.getCurrentView().getText());

        TextView captionTextView = animated ? captionTextViewSwitcher.getNextView() : captionTextViewSwitcher.getCurrentView();

//        if (isVideo) {
//            if (captionTextView.getMaxLines() != 1) {
//                captionTextViewSwitcher.getCurrentView().setMaxLines(1);
//                captionTextViewSwitcher.getNextView().setMaxLines(1);
//                captionTextViewSwitcher.getCurrentView().setSingleLine(true);
//                captionTextViewSwitcher.getNextView().setSingleLine(true);
//                captionTextViewSwitcher.getCurrentView().setEllipsize(TextUtils.TruncateAt.END);
//                captionTextViewSwitcher.getNextView().setEllipsize(TextUtils.TruncateAt.END);
//            }
//        } else {
            final int maxLines = captionTextView.getMaxLines();
            if (maxLines == 1) {
                captionTextViewSwitcher.getCurrentView().setSingleLine(false);
                captionTextViewSwitcher.getNextView().setSingleLine(false);
            }
            final int newCount = Integer.MAX_VALUE;
            if (maxLines != newCount) {
                captionTextViewSwitcher.getCurrentView().setMaxLines(newCount);
                captionTextViewSwitcher.getNextView().setMaxLines(newCount);
                captionTextViewSwitcher.getCurrentView().setEllipsize(null);
                captionTextViewSwitcher.getNextView().setEllipsize(null);
            }
//        }

        captionTextView.setScrollX(0);
//        dontChangeCaptionPosition = animated && isCaptionEmpty;
        boolean withTransition = false;
        captionScrollView.dontChangeTopMargin = false;

        if (animated) {
            withTransition = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                TransitionManager.endTransitions(captionScrollView);
            }
            final TransitionSet transition = new TransitionSet()
                    .addTransition(new Fade(Fade.OUT) {
                        @Override
                        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                            final Animator animator = super.onDisappear(sceneRoot, view, startValues, endValues);
                            if (!isCurrentCaptionEmpty && isCaptionEmpty && view == captionTextViewSwitcher) {
                                animator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        captionScrollView.setVisibility(View.INVISIBLE);
                                        captionScrollView.backgroundAlpha = 1f;
                                    }
                                });
                                ((ObjectAnimator) animator).addUpdateListener(animation -> {
                                    captionScrollView.backgroundAlpha = (float) animation.getAnimatedValue();
                                    captionScrollView.invalidate();
                                });
                            }
                            return animator;
                        }
                    })
                    .addTransition(new Fade(Fade.IN) {
                        @Override
                        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                            final Animator animator = super.onAppear(sceneRoot, view, startValues, endValues);
                            if (isCurrentCaptionEmpty && !isCaptionEmpty && view == captionTextViewSwitcher) {
                                animator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        captionScrollView.backgroundAlpha = 1f;
                                    }
                                });
                                ((ObjectAnimator) animator).addUpdateListener(animation -> {
                                    captionScrollView.backgroundAlpha = (float) animation.getAnimatedValue();
                                    captionScrollView.invalidate();
                                });
                            }
                            return animator;
                        }
                    })
                    .setDuration(200);

            if (!isCurrentCaptionEmpty) {
                captionScrollView.dontChangeTopMargin = true;
                transition.addTransition(new Transition() {
                    @Override
                    public void captureStartValues(TransitionValues transitionValues) {
                        if (transitionValues.view == captionScrollView) {
                            transitionValues.values.put("scrollY", captionScrollView.getScrollY());
                        }
                    }

                    @Override
                    public void captureEndValues(TransitionValues transitionValues) {
                        if (transitionValues.view == captionTextViewSwitcher) {
                            transitionValues.values.put("translationY", captionScrollView.getPendingMarginTopDiff());
                        }
                    }

                    @Override
                    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
                        if (startValues.view == captionScrollView) {
                            final ValueAnimator animator = ValueAnimator.ofInt((Integer) startValues.values.get("scrollY"), 0);
                            animator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    captionTextViewSwitcher.getNextView().setText(null);
                                    captionScrollView.applyPendingTopMargin();
                                }

                                @Override
                                public void onAnimationStart(Animator animation) {
                                    captionScrollView.stopScrolling();
                                }
                            });
                            animator.addUpdateListener(a -> captionScrollView.scrollTo(0, (Integer) a.getAnimatedValue()));
                            return animator;
                        } else if (endValues.view == captionTextViewSwitcher) {
                            final int endValue = (int) endValues.values.get("translationY");
                            if (endValue != 0) {
                                final ObjectAnimator animator = ObjectAnimator.ofFloat(captionTextViewSwitcher, View.TRANSLATION_Y, 0, endValue);
                                animator.addListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        captionTextViewSwitcher.setTranslationY(0);
                                    }
                                });
                                return animator;
                            }
                        }
                        return null;
                    }
                });
            }

            if (isCurrentCaptionEmpty && !isCaptionEmpty) {
                transition.addTarget(captionTextViewSwitcher);
            }

            TransitionManager.beginDelayedTransition(captionScrollView, transition);
        } else {
            captionTextViewSwitcher.getCurrentView().setText(null);
            if (captionScrollView != null) {
                captionScrollView.scrollTo(0, 0);
            }
        }

        boolean switchedToNext = false;
        if (!isCaptionEmpty) {
            Theme.createChatResources(null, true);
            CharSequence str;
            if (messageObject != null /*&& captionTranslated*/ && messageObject.messageOwner != null && messageObject.messageOwner.translatedText != null && TextUtils.equals(messageObject.messageOwner.translatedToLanguage, TranslateAlert2.getToLanguage())) {
                str = caption;
            } else if (messageObject != null && !messageObject.messageOwner.entities.isEmpty()) {
                Spannable spannableString = new SpannableString(caption);
                messageObject.addEntitiesToText(spannableString, true, false);
                if (messageObject.isVideo()) {
                    MessageObject.addUrlsByPattern(messageObject.isOutOwner(), spannableString, false, 3, (int) messageObject.getDuration(), false);
                }
                str = Emoji.replaceEmoji(spannableString, captionTextView.getPaint().getFontMetricsInt(), false);
            } else {
                str = Emoji.replaceEmoji(new SpannableStringBuilder(caption), captionTextView.getPaint().getFontMetricsInt(), false);
            }
            captionTextViewSwitcher.setTag(str);
            try {
                switchedToNext = captionTextViewSwitcher.setText(str, animated, false);
                if (captionScrollView != null) {
                    captionScrollView.updateTopMargin();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            captionTextView.setScrollY(0);
            captionTextView.setTextColor(0xffffffff);
            captionTextViewSwitcher.setVisibility(isActionBarVisible ? View.VISIBLE : View.INVISIBLE);
        } else {
            captionTextViewSwitcher.setText(null, animated);
            captionTextViewSwitcher.getCurrentView().setTextColor(0xffffffff);
            captionTextViewSwitcher.setVisibility(View.INVISIBLE, !withTransition || isCurrentCaptionEmpty);
            captionTextViewSwitcher.setTag(null);
        }
        if (captionTextViewSwitcher.getCurrentView() instanceof PhotoViewer.CaptionTextView) {
            ((PhotoViewer.CaptionTextView) captionTextViewSwitcher.getCurrentView()).setLoading(translating);
        }
    }

    private void onLinkClick(ClickableSpan link, TextView widget) {
//        if (widget != null && link instanceof URLSpan) {
//            String url = ((URLSpan) link).getURL();
//            if (url.startsWith("video")) {
//                if (videoPlayer != null && currentMessageObject != null) {
//                    int seconds = Utilities.parseInt(url);
//                    if (videoPlayer.getDuration() == C.TIME_UNSET) {
//                        seekToProgressPending = seconds / (float) currentMessageObject.getDuration();
//                    } else {
//                        videoPlayer.seekTo(seconds * 1000L);
//                        videoPlayerSeekbar.setProgress(seconds * 1000L / (float) videoPlayer.getDuration(), true);
//                        videoPlayerSeekbarView.invalidate();
//                    }
//                }
//            } else if (url.startsWith("#")) {
//                if (parentActivity instanceof LaunchActivity) {
//                    DialogsActivity fragment = new DialogsActivity(null);
//                    fragment.setSearchString(url);
//                    ((LaunchActivity) parentActivity).presentFragment(fragment, false, true);
//                    closePhoto(false, false);
//                }
//            } else if (activi != null && (link instanceof URLSpanReplacement || AndroidUtilities.shouldShowUrlInAlert(url))) {
//                AlertsCreator.showOpenUrlAlert(parentChatActivity, url, true, true);
//            } else {
//                link.onClick(widget);
//            }
//        } else {
//            link.onClick(widget);
//        }
    }

    private void onLinkLongPress(ClickableSpan link, TextView widget, Runnable onDismiss) {
//        int timestamp = -1;
//        BottomSheet.Builder builder = new BottomSheet.Builder(parentActivity, false, resourcesProvider, 0xff1C2229);
//        if (link.getURL().startsWith("video?")) {
//            try {
//                String timestampStr = link.getURL().substring(link.getURL().indexOf('?') + 1);
//                timestamp = Integer.parseInt(timestampStr);
//            } catch (Throwable ignore) {
//
//            }
//        }
//        if (timestamp >= 0) {
//            builder.setTitle(AndroidUtilities.formatDuration(timestamp, false));
//        } else {
//            builder.setTitle(link.getURL());
//        }
//        final int finalTimestamp = timestamp;
//        builder.setItems(new CharSequence[]{LocaleController.getString(R.string.Open), LocaleController.getString(R.string.Copy)}, (dialog, which) -> {
//            if (which == 0) {
//                onLinkClick(link, widget);
//            } else if (which == 1) {
//                String url1 = link.getURL();
//                boolean tel = false;
//                if (url1.startsWith("mailto:")) {
//                    url1 = url1.substring(7);
//                } else if (url1.startsWith("tel:")) {
//                    url1 = url1.substring(4);
//                    tel = true;
//                } else if (finalTimestamp >= 0) {
//                    if (currentMessageObject != null && !currentMessageObject.scheduled) {
//                        MessageObject messageObject1 = currentMessageObject;
//                        boolean isMedia = currentMessageObject.isVideo() || currentMessageObject.isRoundVideo() || currentMessageObject.isVoice() || currentMessageObject.isMusic();
//                        if (!isMedia && currentMessageObject.replyMessageObject != null) {
//                            messageObject1 = currentMessageObject.replyMessageObject;
//                        }
//                        long dialogId = messageObject1.getDialogId();
//                        int messageId = messageObject1.getId();
//
//                        if (messageObject1.messageOwner.fwd_from != null) {
//                            if (messageObject1.messageOwner.fwd_from.saved_from_peer != null) {
//                                dialogId = MessageObject.getPeerId(messageObject1.messageOwner.fwd_from.saved_from_peer);
//                                messageId = messageObject1.messageOwner.fwd_from.saved_from_msg_id;
//                            } else if (messageObject1.messageOwner.fwd_from.from_id != null) {
//                                dialogId = MessageObject.getPeerId(messageObject1.messageOwner.fwd_from.from_id);
//                                messageId = messageObject1.messageOwner.fwd_from.channel_post;
//                            }
//                        }
//
//                        if (DialogObject.isChatDialog(dialogId)) {
//                            TLRPC.Chat currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
//                            String username = ChatObject.getPublicUsername(currentChat);
//                            if (username != null) {
//                                url1 = "https://t.me/" + username + "/" + messageId + "?t=" + finalTimestamp;
//                            }
//                        } else {
//                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
//                            String username = UserObject.getPublicUsername(user);
//                            if (user != null && username != null) {
//                                url1 = "https://t.me/" + username + "/" + messageId + "?t=" + finalTimestamp;
//                            }
//                        }
//                    }
//                }
//                AndroidUtilities.addToClipboard(url1);
//                String bulletinMessage;
//                if (tel) {
//                    bulletinMessage = LocaleController.getString(R.string.PhoneCopied);
//                } else if (url1.startsWith("#")) {
//                    bulletinMessage = LocaleController.getString(R.string.HashtagCopied);
//                } else if (url1.startsWith("@")) {
//                    bulletinMessage = LocaleController.getString(R.string.UsernameCopied);
//                } else {
//                    bulletinMessage = LocaleController.getString(R.string.LinkCopied);
//                }
//                if (AndroidUtilities.shouldShowClipboardToast()) {
//                    BulletinFactory.of(containerView, resourcesProvider).createSimpleBulletin(R.raw.voip_invite, bulletinMessage).show();
//                }
//            }
//        });
//        builder.setOnPreDismissListener(di -> onDismiss.run());
//        BottomSheet bottomSheet = builder.create();
//        bottomSheet.scrollNavBar = true;
//        bottomSheet.show();
//        try {
//            containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
//        } catch (Exception ignore) {}
//        bottomSheet.setItemColor(0,0xffffffff, 0xffffffff);
//        bottomSheet.setItemColor(1,0xffffffff, 0xffffffff);
//        bottomSheet.setBackgroundColor(0xff1C2229);
//        bottomSheet.setTitleColor(0xff8A8A8A);
//        bottomSheet.setCalcMandatoryInsets(true);
//        AndroidUtilities.setNavigationBarColor(bottomSheet.getWindow(), 0xff1C2229, false);
//        AndroidUtilities.setLightNavigationBar(bottomSheet.getWindow(), false);
//        bottomSheet.scrollNavBar = true;
    }

    private boolean playButtonShown;
    private void showPlayButton(boolean show, boolean animated) {
        show = isVideo && show;
        if (playButtonShown == show && animated) {
            return;
        }

        playButtonShown = show;
        playButton.animate().cancel();
        if (animated) {
            playButton.animate()
                    .scaleX(show ? 1f : .6f)
                    .scaleY(show ? 1f : .6f)
                    .alpha(show ? 1f : 0f)
                    .setDuration(340)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        } else {
            playButton.setScaleX(show ? 1f : .6f);
            playButton.setScaleY(show ? 1f : .6f);
            playButton.setAlpha(show ? 1f : 0f);
        }
    }

    private void showSecretHint() {
        secretHint.setMultilineText(true);
        CharSequence text = LocaleController.getString(isVideo ? R.string.VideoShownOnce : R.string.PhotoShownOnce);
        secretHint.setMaxWidthPx(HintView2.cutInFancyHalf(text, secretHint.getTextPaint()));
        secretHint.setText(text);
        secretHint.setInnerPadding(12, 7, 11, 7);
        secretHint.setIconMargin(2);
        secretHint.setIconTranslate(0, 0);
        secretHint.setIcon(R.raw.fire_on);
        secretHint.show();
        MessagesController.getGlobalMainSettings().edit().putInt("viewoncehint", MessagesController.getGlobalMainSettings().getInt("viewoncehint", 0) + 1).commit();
    }

    private int wasNavigationBarColor;
    private boolean wasLightNavigationBar;

    private Runnable onClose;
    private boolean ignoreDelete;

    public void openMedia(MessageObject messageObject, PhotoViewer.PhotoViewerProvider provider, Runnable onOpen, Runnable onClose) {
        if (parentActivity == null || messageObject == null || !messageObject.needDrawBluredPreview() || provider == null) {
            return;
        }
        final PhotoViewer.PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, null, 0, true, false);
        if (object == null) {
            return;
        }

        //messageObject.messageOwner.destroyTime = (int) (System.currentTimeMillis() / 1000 + ConnectionsManager.getInstance().getTimeDifference()) + 4;

        ignoreDelete = messageObject.messageOwner.ttl == 0x7FFFFFFF;
        this.onClose = onClose;

        currentProvider = provider;
        openTime = System.currentTimeMillis();
        closeTime = 0;
        isActionBarVisible = true;
        isPhotoVisible = true;
        draggingDown = false;
        if (aspectRatioFrameLayout != null) {
            aspectRatioFrameLayout.setVisibility(View.INVISIBLE);
        }
        releasePlayer();

        pinchStartDistance = 0;
        pinchStartScale = 1;
        pinchCenterX = 0;
        pinchCenterY = 0;
        pinchStartX = 0;
        pinchStartY = 0;
        moveStartX = 0;
        moveStartY = 0;
        zooming = false;
        moving = false;
        doubleTap = false;
        invalidCoords = false;
        canDragDown = true;
        updateMinMax(scale);
        photoBackgroundDrawable.setAlpha(0);
        containerView.setAlpha(1.0f);
        containerView.setVisibility(View.VISIBLE);
        secretDeleteTimer.setAlpha(1.0f);
        isVideo = false;
        videoWatchedOneTime = false;
        closeVideoAfterWatch = false;
        disableShowCheck = true;
        centerImage.setManualAlphaAnimator(false);
        videoWidth = 0;
        videoHeight = 0;

        final RectF _drawRegion = object.imageReceiver.getDrawRegion();
        RectF drawRegion = new RectF(_drawRegion);
        drawRegion.left =   Math.max(drawRegion.left,   object.imageReceiver.getImageX());
        drawRegion.top =    Math.max(drawRegion.top,    object.imageReceiver.getImageY());
        drawRegion.right =  Math.min(drawRegion.right,  object.imageReceiver.getImageX2());
        drawRegion.bottom = Math.min(drawRegion.bottom, object.imageReceiver.getImageY2());

        float width = drawRegion.width();
        float height = drawRegion.height();
        int viewWidth = AndroidUtilities.displaySize.x;
        int viewHeight = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight;
        scale = Math.max(width / viewWidth, height / viewHeight);

        if (object.radius != null) {
            animateFromRadius = new int[object.radius.length];
            for (int i = 0; i < object.radius.length; ++i) {
                animateFromRadius[i] = object.radius[i];
            }
        } else {
            animateFromRadius = null;
        }
        translationX = object.viewX + drawRegion.left + width / 2 -  viewWidth / 2;
        translationY = object.viewY + drawRegion.top + height / 2 - viewHeight / 2;
        clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
        int clipVertical = (int) Math.abs(drawRegion.top - object.imageReceiver.getImageY());
        int[] coords2 = new int[2];
        object.parentView.getLocationInWindow(coords2);
        clipTop = coords2[1] - 0 - (object.viewY + drawRegion.top) + object.clipTopAddition;
        clipTop = Math.max(0, Math.max(clipTop, clipVertical));
        clipBottom = object.viewY + drawRegion.top + (int) height - (coords2[1] + object.parentView.getHeight() - 0) + object.clipBottomAddition;
        clipBottom = Math.max(0, Math.max(clipBottom, clipVertical));

        clipTopOrigin = 0;//coords2[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + drawRegion.top) + object.clipTopAddition;
        clipTopOrigin = Math.max(0, Math.max(clipTopOrigin, clipVertical));
        clipBottomOrigin = 0;//(object.viewY + drawRegion.top + (int) height) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition;
        clipBottomOrigin = Math.max(0, Math.max(clipBottomOrigin, clipVertical));

        animationStartTime = System.currentTimeMillis();
        animateToX = 0;
        animateToY = 0;
        animateToClipBottom = 0;
        animateToClipBottomOrigin = 0;
        animateToClipHorizontal = 0;
        animateToClipTop = 0;
        animateToClipTopOrigin = 0;
        animateToScale = 1.0f;
        animateToRadius = true;
        zoomAnimation = true;

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        currentDialogId = MessageObject.getPeerId(messageObject.messageOwner.peer_id);
        currentMessageObject = messageObject;
        TLRPC.Document document = messageObject.getDocument();
        if (currentThumb != null) {
            currentThumb.release();
            currentThumb = null;
        }
        currentThumb = object.imageReceiver.getThumbBitmapSafe();
        seekbarContainer.setVisibility(View.GONE);
        if (document != null) {
            for (int i = 0; i < document.attributes.size(); ++i) {
                TLRPC.DocumentAttribute attr = document.attributes.get(i);
                if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                    TLRPC.TL_documentAttributeVideo attrVideo = (TLRPC.TL_documentAttributeVideo) attr;
                    videoWidth = attrVideo.w;
                    videoHeight = attrVideo.h;
                    break;
                }
            }
            if (MessageObject.isGifDocument(document)) {
                actionBar.setTitle(LocaleController.getString(R.string.DisappearingGif));
                ImageLocation location;
                if (messageObject.messageOwner.attachPath != null && messageObject.attachPathExists) {
                    location = ImageLocation.getForPath(messageObject.messageOwner.attachPath);
                } else {
                    location =ImageLocation.getForDocument(document);
                }
                centerImage.setImage(location, null, currentThumb != null ? new BitmapDrawable(currentThumb.bitmap) : null, -1, null, messageObject, 1);
            } else {
                playerRetryPlayCount = 1;
                actionBar.setTitle(LocaleController.getString(R.string.DisappearingVideo));
                File f = new File(messageObject.messageOwner.attachPath);
                if (f.exists()) {
                    preparePlayer(f);
                } else {
                    File file = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner);
                    File encryptedFile = new File(file.getAbsolutePath() + ".enc");
                    if (encryptedFile.exists()) {
                        file = encryptedFile;
                    }
                    preparePlayer(file);
                }
                isVideo = true;
                seekbarContainer.setVisibility(View.VISIBLE);
                centerImage.setImage(null, null, currentThumb != null ? new BitmapDrawable(currentThumb.bitmap) : null, -1, null, messageObject, 2);
            }
        } else {
            actionBar.setTitle(LocaleController.getString(R.string.DisappearingPhoto));
            TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
            centerImage.setImage(ImageLocation.getForObject(sizeFull, messageObject.photoThumbsObject), null, currentThumb != null ? new BitmapDrawable(currentThumb.bitmap) : null, -1, null, messageObject, 2);

            if (sizeFull != null) {
                videoWidth = sizeFull.w;
                videoHeight = sizeFull.h;
            }
        }
        setCurrentCaption(messageObject, "", false, false);
        setCurrentCaption(messageObject, messageObject.caption, false, true);
        toggleActionBar(true, false);
        showPlayButton(false, false);
        playButtonDrawable.setPause(true);

        if (ignoreDelete) {
            secretDeleteTimer.setOnce();
            secretDeleteTimer.setOnClickListener(v -> {
                if (currentMessageObject == null || currentMessageObject.messageOwner.destroyTime == 0 && currentMessageObject.messageOwner.ttl != 0x7FFFFFFF) {
                    return;
                }
                if (secretHint.shown()) {
                    secretHint.hide();
                    return;
                }
                showSecretHint();
            });
        } else {
            secretDeleteTimer.setOnClickListener(null);
        }
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeView(windowView);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
        wm.addView(windowView, windowLayoutParams);
        secretDeleteTimer.invalidate();
        isVisible = true;

        final Window window = parentActivity.getWindow();
        wasLightNavigationBar = AndroidUtilities.getLightNavigationBar(window);
        AndroidUtilities.setLightNavigationBar(parentActivity, false);
        AndroidUtilities.setLightNavigationBar(windowView, false);
        if (parentActivity instanceof LaunchActivity) {
            wasNavigationBarColor = ((LaunchActivity) parentActivity).getNavigationBarColor();
            ((LaunchActivity) parentActivity).animateNavigationBarColor(0xff000000);
        } else {
            wasNavigationBarColor = window.getNavigationBarColor();
            AndroidUtilities.setNavigationBarColor(parentActivity, 0xff000000);
        }

        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(
                ObjectAnimator.ofFloat(actionBar, View.ALPHA, 0, 1.0f),
                ObjectAnimator.ofFloat(captionScrollView, View.ALPHA, 0, 1f),
                ObjectAnimator.ofFloat(secretHint, View.ALPHA, 0, 1.0f),
                ObjectAnimator.ofInt(photoBackgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0, 255),
                ObjectAnimator.ofFloat(this, ANIMATION_VALUE, 0, 1),
                ObjectAnimator.ofFloat(seekbarContainer, seekbarContainer.SEEKBAR_ALPHA, 1.0f),
                ObjectAnimator.ofFloat(seekbarContainer, View.ALPHA, isVideo ? 1f : 0f)
        );
        photoAnimationInProgress = 3;
        final Runnable openRunnable = onOpen;
        photoAnimationEndRunnable = () -> {
            photoAnimationInProgress = 0;
            imageMoveAnimation = null;
            if (openRunnable != null) {
                openRunnable.run();
            }
            if (containerView == null) {
                return;
            }
            containerView.setLayerType(View.LAYER_TYPE_NONE, null);
            containerView.invalidate();
            secretDeleteTimer.setDestroyTime(messageObject.messageOwner.destroyTimeMillis, messageObject.messageOwner.ttl, false);
            if (closeAfterAnimation) {
                closePhoto(true, true);
            } else {
                if (ignoreDelete && MessagesController.getGlobalMainSettings().getInt("viewoncehint", 0) < 3) {
                    showSecretHint();
                }
            }
        };
        imageMoveAnimation.setDuration(250);
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (photoAnimationEndRunnable != null) {
                    photoAnimationEndRunnable.run();
                    photoAnimationEndRunnable = null;
                }
            }
        });
        photoTransitionAnimationStartTime = System.currentTimeMillis();
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        imageMoveAnimation.setInterpolator(new DecelerateInterpolator());
        photoBackgroundDrawable.frame = 0;
        photoBackgroundDrawable.drawRunnable = () -> {
            disableShowCheck = false;
            object.imageReceiver.setVisible(false, true);
        };
        imageMoveAnimation.start();
    }

    public boolean isShowingImage(MessageObject object) {
        return isVisible && !disableShowCheck && object != null && currentMessageObject != null && currentMessageObject.getId() == object.getId();
    }

    private final Runnable hideActionBarRunnable = () -> {
        toggleActionBar(false, true);
    };

    private void toggleActionBar(boolean show, final boolean animated) {
        AndroidUtilities.cancelRunOnUIThread(hideActionBarRunnable);
        if (show && isVideo) {
            AndroidUtilities.runOnUIThread(hideActionBarRunnable, 3000);
        }
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
        }
        actionBar.setEnabled(show);
        isActionBarVisible = show;

        showPlayButton(show, animated);
        if (animated) {
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f));
            arrayList.add(ObjectAnimator.ofFloat(seekbarContainer, seekbarContainer.SEEKBAR_ALPHA, show ? 1f : 0f));
            arrayList.add(ObjectAnimator.ofFloat(captionScrollView, View.ALPHA, show ? 1f : 0f));
            arrayList.add(ObjectAnimator.ofFloat(seekbarBackground, View.ALPHA, show ? 1f : 0));
            arrayList.add(ObjectAnimator.ofFloat(navigationBar, View.ALPHA, show ? 1f : 0));
            currentActionBarAnimation = new AnimatorSet();
            currentActionBarAnimation.playTogether(arrayList);
            if (!show) {
                currentActionBarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentActionBarAnimation != null && currentActionBarAnimation.equals(animation)) {
                            actionBar.setVisibility(View.GONE);
                            currentActionBarAnimation = null;
                            captionScrollView.scrollTo(0, 0);
                        }
                    }
                });
            }

            currentActionBarAnimation.setDuration(200);
            currentActionBarAnimation.start();
        } else {
            actionBar.setAlpha(show ? 1.0f : 0.0f);
            captionScrollView.setAlpha(show ? 1f : 0f);
            seekbarBackground.setAlpha(show ? 1f : 0f);
            navigationBar.setAlpha(show ? 1f : 0f);
            if (!show) {
                actionBar.setVisibility(View.GONE);
                captionScrollView.scrollTo(0, 0);
            }
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    public void destroyPhotoViewer() {
        if (onClose != null) {
            onClose.run();
            onClose = null;
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        isVisible = false;
        currentProvider = null;
        if (currentThumb != null) {
            currentThumb.release();
            currentThumb = null;
        }
        releasePlayer();
        if (parentActivity != null && windowView != null) {
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeViewImmediate(windowView);
                }
                windowView = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Instance = null;

    }

    private float[] currentRadii;
    private Path roundRectPath = new Path();

    private void onDraw(Canvas canvas) {
        if (!isPhotoVisible) {
            return;
        }

        float currentTranslationY;
        float currentTranslationX;
        float currentScale;
        float currentClipTop;
        float currentClipBottom;
        float currentClipTopOrigin;
        float currentClipBottomOrigin;
        float currentClipHorizontal;
        float aty = -1;

        if (imageMoveAnimation != null) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }

            if (useOvershootForScale) {
                final float overshoot = 1.02f;
                final float overshootTime = 0.9f;
                float av;
                if (animationValue < overshootTime) {
                    av = animationValue / overshootTime;
                    currentScale = scale + (animateToScale * overshoot - scale) * av;
                } else {
                    av = 1;
                    currentScale = animateToScale + (animateToScale * (overshoot - 1.0f)) * (1.0f - (animationValue - overshootTime) / (1.0f - overshootTime));
                }
                currentTranslationY = translationY + (animateToY - translationY) * av;
                currentTranslationX = translationX + (animateToX - translationX) * av;
                currentClipTop = clipTop + (animateToClipTop - clipTop) * av;
                currentClipBottom = clipBottom + (animateToClipBottom - clipBottom) * av;
                currentClipTopOrigin = clipTopOrigin + (animateToClipTopOrigin - clipTopOrigin) * av;
                currentClipBottomOrigin = clipBottomOrigin + (animateToClipBottomOrigin - clipBottomOrigin) * av;
                currentClipHorizontal = clipHorizontal + (animateToClipHorizontal - clipHorizontal) * av;
            } else {
                currentScale = scale + (animateToScale - scale) * animationValue;
                currentTranslationY = translationY + (animateToY - translationY) * animationValue;
                currentTranslationX = translationX + (animateToX - translationX) * animationValue;
                currentClipTop = clipTop + (animateToClipTop - clipTop) * animationValue;
                currentClipBottom = clipBottom + (animateToClipBottom - clipBottom) * animationValue;
                currentClipTopOrigin = clipTopOrigin + (animateToClipTopOrigin - clipTopOrigin) * animationValue;
                currentClipBottomOrigin = clipBottomOrigin + (animateToClipBottomOrigin - clipBottomOrigin) * animationValue;
                currentClipHorizontal = clipHorizontal + (animateToClipHorizontal - clipHorizontal) * animationValue;
            }

            if (animateToScale == 1 && scale == 1 && translationX == 0) {
                aty = currentTranslationY;
            }

            containerView.invalidate();
        } else {
            if (animationStartTime != 0) {
                translationX = animateToX;
                translationY = animateToY;
                clipBottom = animateToClipBottom;
                clipTop = animateToClipTop;
                clipTopOrigin = animateToClipTopOrigin;
                clipBottomOrigin = animateToClipBottomOrigin;
                clipHorizontal = animateToClipHorizontal;
                scale = animateToScale;
                animationStartTime = 0;
                updateMinMax(scale);
                zoomAnimation = false;
                useOvershootForScale = false;
            }
            if (!scroller.isFinished()) {
                if (scroller.computeScrollOffset()) {
                    if (scroller.getStartX() < maxX && scroller.getStartX() > minX) {
                        translationX = scroller.getCurrX();
                    }
                    if (scroller.getStartY() < maxY && scroller.getStartY() > minY) {
                        translationY = scroller.getCurrY();
                    }
                    containerView.invalidate();
                }
            }
            currentScale = scale;
            currentTranslationY = translationY;
            currentTranslationX = translationX;
            currentClipTop = clipTop;
            currentClipBottom = clipBottom;
            currentClipTopOrigin = clipTopOrigin;
            currentClipBottomOrigin = clipBottomOrigin;
            currentClipHorizontal = clipHorizontal;
            if (!moving) {
                aty = translationY;
            }
        }

        boolean zeroRadius = true;
        if (animateFromRadius != null) {
            if (currentRadii == null) {
                currentRadii = new float[8];
            }
            float t = animateToRadius ? animationValue : 1f - animationValue;
            zeroRadius = true;
            for (int i = 0; i < 8; i += 2) {
                currentRadii[i] = currentRadii[i + 1] = AndroidUtilities.lerp((float) animateFromRadius[i / 2] * 2, 0, t);
                if (currentRadii[i] > 0) {
                    zeroRadius = false;
                }
            }
        }

        float translateX = currentTranslationX;
        float scaleDiff = 0;
        float alpha = 1;
        if (photoAnimationInProgress != 3) {
            if (scale == 1 && aty != -1 && !zoomAnimation) {
                float maxValue = getContainerViewHeight() / 4.0f;
                photoBackgroundDrawable.setAlpha((int) Math.max(127, 255 * (1.0f - (Math.min(Math.abs(aty), maxValue) / maxValue))));
            } else {
                photoBackgroundDrawable.setAlpha(255);
            }
            if (!zoomAnimation && translateX > maxX) {
                alpha = Math.min(1.0f, (translateX - maxX) / canvas.getWidth());
                scaleDiff = alpha * 0.3f;
                alpha = 1.0f - alpha;
                translateX = maxX;
            }
        }

        boolean drawTextureView = aspectRatioFrameLayout != null && aspectRatioFrameLayout.getVisibility() == View.VISIBLE;
        canvas.save();
        float sc = currentScale - scaleDiff;
        canvas.translate(getContainerViewWidth() / 2 + translateX, getContainerViewHeight() / 2 + currentTranslationY);
        canvas.scale(sc, sc);

        int bitmapWidth = centerImage.getBitmapWidth();
        int bitmapHeight = centerImage.getBitmapHeight();
        if (videoWidth != 0 && videoHeight != 0) {
            bitmapWidth = videoWidth;
            bitmapHeight = videoHeight;
        }
        if (drawTextureView && textureUploaded) {
            float scale1 = bitmapWidth / (float) bitmapHeight;
            float scale2 = videoTextureView.getMeasuredWidth() / (float) videoTextureView.getMeasuredHeight();
            if (Math.abs(scale1 - scale2) > 0.01f) {
                bitmapWidth = videoTextureView.getMeasuredWidth();
                bitmapHeight = videoTextureView.getMeasuredHeight();
            }
        }

        float scale = Math.min((float) getContainerViewHeight() / (float) bitmapHeight, (float) getContainerViewWidth() / (float) bitmapWidth);
        int width = (int) (bitmapWidth * scale);
        int height = (int) (bitmapHeight * scale);

        canvas.clipRect(-width / 2 + currentClipHorizontal / sc, -height / 2 + currentClipTop / sc, width / 2 - currentClipHorizontal / sc, height / 2 - currentClipBottom / sc);
        if (!zeroRadius) {
            roundRectPath.reset();
            AndroidUtilities.rectTmp.set(-width / 2 + currentClipHorizontal / sc, -height / 2 + currentClipTopOrigin / sc, width / 2 - currentClipHorizontal / sc, height / 2 - currentClipBottomOrigin / sc);
            roundRectPath.addRoundRect(AndroidUtilities.rectTmp, currentRadii, Path.Direction.CW);
            canvas.clipPath(roundRectPath);
        }

        if (!drawTextureView || !textureUploaded || !videoCrossfadeStarted || videoCrossfadeAlpha != 1.0f) {
            centerImage.setAlpha(alpha);
            centerImage.setImageCoords(-width / 2, -height / 2, width, height);
            centerImage.draw(canvas);
        }
        if (drawTextureView) {
            if (!videoCrossfadeStarted && textureUploaded) {
                videoCrossfadeStarted = true;
                videoCrossfadeAlpha = 0.0f;
                videoCrossfadeAlphaLastTime = System.currentTimeMillis();
            }
            canvas.translate(-width / 2, -height / 2);
            videoTextureView.setAlpha(alpha * videoCrossfadeAlpha);
            aspectRatioFrameLayout.draw(canvas);
            if (videoCrossfadeStarted && videoCrossfadeAlpha < 1.0f) {
                long newUpdateTime = System.currentTimeMillis();
                long dt = newUpdateTime - videoCrossfadeAlphaLastTime;
                videoCrossfadeAlphaLastTime = newUpdateTime;
                videoCrossfadeAlpha += dt / 200.0f;
                containerView.invalidate();
                if (videoCrossfadeAlpha > 1.0f) {
                    videoCrossfadeAlpha = 1.0f;
                }
            }
        }
        canvas.restore();
    }

    public final Property<SecretMediaViewer, Float> VIDEO_CROSSFADE_ALPHA = new AnimationProperties.FloatProperty<SecretMediaViewer>("videoCrossfadeAlpha") {
        @Override
        public void setValue(SecretMediaViewer object, float value) {
            object.setVideoCrossfadeAlpha(value);
        }

        @Override
        public Float get(SecretMediaViewer object) {
            return object.getVideoCrossfadeAlpha();
        }
    };

    @Keep
    public float getVideoCrossfadeAlpha() {
        return videoCrossfadeAlpha;
    }

    @Keep
    public void setVideoCrossfadeAlpha(float value) {
        videoCrossfadeAlpha = value;
        containerView.invalidate();
    }

    private boolean checkPhotoAnimation() {
        if (photoAnimationInProgress != 0) {
            if (Math.abs(photoTransitionAnimationStartTime - System.currentTimeMillis()) >= 500) {
                if (photoAnimationEndRunnable != null) {
                    photoAnimationEndRunnable.run();
                    photoAnimationEndRunnable = null;
                }
                photoAnimationInProgress = 0;
            }
        }
        return photoAnimationInProgress != 0;
    }

    public long getOpenTime() {
        return openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public MessageObject getCurrentMessageObject() {
        return currentMessageObject;
    }

    public boolean closePhoto(boolean animated, boolean byDelete) {
        if (parentActivity == null || !isPhotoVisible || checkPhotoAnimation()) {
            return false;
        }

        if (ignoreDelete && byDelete) {
            return false;
        }

        if (parentActivity != null) {
            AndroidUtilities.setLightNavigationBar(parentActivity, wasLightNavigationBar);
            AndroidUtilities.setNavigationBarColor(parentActivity, wasNavigationBarColor);
            if (parentActivity instanceof LaunchActivity) {
                ((LaunchActivity) parentActivity).animateNavigationBarColor(wasNavigationBarColor);
            } else {
                AndroidUtilities.setNavigationBarColor(parentActivity, wasNavigationBarColor);
            }
        }

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);

        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        closeTime = System.currentTimeMillis();
        final PhotoViewer.PlaceProviderObject object;
        if (currentProvider == null || currentMessageObject.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty || currentMessageObject.messageOwner.media.document instanceof TLRPC.TL_documentEmpty) {
            object = null;
        } else {
            object = currentProvider.getPlaceForPhoto(currentMessageObject, null, 0, true, false);
        }
        if (videoPlayer != null) {
            videoPlayer.pause();
        }

        if (animated) {
            photoAnimationInProgress = 3;
            containerView.invalidate();

            imageMoveAnimation = new AnimatorSet();

            if (object != null && object.imageReceiver.getThumbBitmap() != null && !byDelete && onClose == null) {
                object.imageReceiver.setVisible(false, true);

                final RectF drawRegion = object.imageReceiver.getDrawRegion();

                float width = (drawRegion.right - drawRegion.left);
                float height = (drawRegion.bottom - drawRegion.top);
                int viewWidth = AndroidUtilities.displaySize.x;
                int viewHeight = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight;
                animateToScale = Math.max(width / viewWidth, height / viewHeight);
                animateToX = object.viewX + drawRegion.left + width / 2 -  viewWidth / 2;
                animateToY = object.viewY + drawRegion.top + height / 2 - viewHeight / 2;
                animateToClipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
                int clipVertical =( int) Math.abs(drawRegion.top - object.imageReceiver.getImageY());
                int[] coords2 = new int[2];
                object.parentView.getLocationInWindow(coords2);
                animateToClipTop = coords2[1] - 0 - (object.viewY + drawRegion.top) + object.clipTopAddition;
                animateToClipTop = Math.max(0, Math.max(animateToClipTop, clipVertical));
                animateToClipBottom = object.viewY + drawRegion.top + (int) height - (coords2[1] + object.parentView.getHeight() - 0) + object.clipBottomAddition;
                animateToClipBottom = Math.max(0, Math.max(animateToClipBottom, clipVertical));

                animateToClipTopOrigin = 0; // coords2[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + drawRegion.top) + object.clipTopAddition;
                animateToClipTopOrigin = Math.max(0, Math.max(animateToClipTopOrigin, clipVertical));
                animateToClipBottomOrigin = 0; // (object.viewY + drawRegion.top + (int) height) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition;
                animateToClipBottomOrigin = Math.max(0, Math.max(animateToClipBottomOrigin, clipVertical));

                animationStartTime = System.currentTimeMillis();
                zoomAnimation = true;
            } else {
                int h = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight;
                animateToY = translationY >= 0 ? h : -h;
            }
            animateToRadius = false;
            showPlayButton(false, true);
            if (isVideo) {
                videoCrossfadeStarted = false;
                textureUploaded = false;
                imageMoveAnimation.playTogether(
                        ObjectAnimator.ofInt(photoBackgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                        ObjectAnimator.ofFloat(this, ANIMATION_VALUE, 0, 1),
                        ObjectAnimator.ofFloat(actionBar, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(captionScrollView, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(navigationBar, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(seekbarContainer, seekbarContainer.SEEKBAR_ALPHA, 0f),
                        ObjectAnimator.ofFloat(seekbarContainer, View.ALPHA, 0f),
                        ObjectAnimator.ofFloat(secretHint, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(this, VIDEO_CROSSFADE_ALPHA, 0)
                );
            } else {
                centerImage.setManualAlphaAnimator(true);
                imageMoveAnimation.playTogether(
                        ObjectAnimator.ofInt(photoBackgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                        ObjectAnimator.ofFloat(this, ANIMATION_VALUE, 0, 1),
                        ObjectAnimator.ofFloat(actionBar, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(captionScrollView, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(navigationBar, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(seekbarContainer, seekbarContainer.SEEKBAR_ALPHA, 0f),
                        ObjectAnimator.ofFloat(seekbarContainer, View.ALPHA, 0f),
                        ObjectAnimator.ofFloat(secretHint, View.ALPHA, 0),
                        ObjectAnimator.ofFloat(centerImage, AnimationProperties.IMAGE_RECEIVER_ALPHA, 0.0f)
                );
            }

            photoAnimationEndRunnable = () -> {
                imageMoveAnimation = null;
                photoAnimationInProgress = 0;
                containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                containerView.setVisibility(View.INVISIBLE);
                onPhotoClosed(object);
            };

            imageMoveAnimation.setInterpolator(new DecelerateInterpolator());
            imageMoveAnimation.setDuration(250);
            imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (object != null) {
                        object.imageReceiver.setVisible(true, true);
                    }
                    isVisible = false;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (photoAnimationEndRunnable != null) {
                            photoAnimationEndRunnable.run();
                            photoAnimationEndRunnable = null;
                        }
                    });
                }
            });
            photoTransitionAnimationStartTime = System.currentTimeMillis();
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            imageMoveAnimation.start();
        } else {
            showPlayButton(false, true);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(containerView, View.SCALE_X, 0.9f),
                    ObjectAnimator.ofFloat(containerView, View.SCALE_Y, 0.9f),
                    ObjectAnimator.ofInt(photoBackgroundDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                    ObjectAnimator.ofFloat(actionBar, View.ALPHA, 0),
                    ObjectAnimator.ofFloat(captionScrollView, View.ALPHA, 0),
                    ObjectAnimator.ofFloat(navigationBar, View.ALPHA, 0),
                    ObjectAnimator.ofFloat(seekbarContainer, seekbarContainer.SEEKBAR_ALPHA, 0f),
                    ObjectAnimator.ofFloat(seekbarContainer, View.ALPHA, 0f)
            );
            photoAnimationInProgress = 2;
            photoAnimationEndRunnable = () -> {
                if (containerView == null) {
                    return;
                }
                containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                containerView.setVisibility(View.INVISIBLE);
                photoAnimationInProgress = 0;
                onPhotoClosed(object);
                containerView.setScaleX(1.0f);
                containerView.setScaleY(1.0f);
            };
            animatorSet.setDuration(200);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (photoAnimationEndRunnable != null) {
                        photoAnimationEndRunnable.run();
                        photoAnimationEndRunnable = null;
                    }
                }
            });
            photoTransitionAnimationStartTime = System.currentTimeMillis();
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            animatorSet.start();
        }
        if (onClose != null) {
            onClose.run();
            onClose = null;
        }
        return true;
    }

    private void onPhotoClosed(PhotoViewer.PlaceProviderObject object) {
        isVisible = false;
        currentProvider = null;
        disableShowCheck = false;
        releasePlayer();
        ArrayList<File> filesToDelete = new ArrayList<>();
        AndroidUtilities.runOnUIThread(() -> {
            if (currentThumb != null) {
                currentThumb.release();
                currentThumb = null;
            }
            centerImage.setImageBitmap((Bitmap) null);
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(windowView);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            isPhotoVisible = false;
        }, 50);
    }

    private void updateMinMax(float scale) {
        int maxW = (int) (centerImage.getImageWidth() * scale - getContainerViewWidth()) / 2;
        int maxH = (int) (centerImage.getImageHeight() * scale - getContainerViewHeight()) / 2;
        if (maxW > 0) {
            minX = -maxW;
            maxX = maxW;
        } else {
            minX = maxX = 0;
        }
        if (maxH > 0) {
            minY = -maxH;
            maxY = maxH;
        } else {
            minY = maxY = 0;
        }
    }

    private int getContainerViewWidth() {
        return containerView.getWidth();
    }

    private int getContainerViewHeight() {
        return containerView.getHeight();
    }

    private boolean processTouchEvent(MotionEvent ev) {
        if (photoAnimationInProgress != 0 || animationStartTime != 0) {
            return false;
        }

        if (ev.getPointerCount() == 1 && gestureDetector.onTouchEvent(ev) && doubleTap) {
            doubleTap = false;
            moving = false;
            zooming = false;
            checkMinMax(false);
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            discardTap = false;
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            if (!draggingDown) {
                if (ev.getPointerCount() == 2) {
                    pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                    pinchStartScale = scale;
                    pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                    pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                    pinchStartX = translationX;
                    pinchStartY = translationY;
                    zooming = true;
                    moving = false;
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (ev.getPointerCount() == 1) {
                    moveStartX = ev.getX();
                    dragY = moveStartY = ev.getY();
                    draggingDown = false;
                    canDragDown = true;
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (ev.getPointerCount() == 2 && !draggingDown && zooming) {
                discardTap = true;
                scale = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0)) / pinchStartDistance * pinchStartScale;
                translationX = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - pinchStartX) * (scale / pinchStartScale);
                translationY = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - pinchStartY) * (scale / pinchStartScale);
                updateMinMax(scale);
                containerView.invalidate();
            } else if (ev.getPointerCount() == 1) {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                float dx = Math.abs(ev.getX() - moveStartX);
                float dy = Math.abs(ev.getY() - dragY);
                if (dx > dp(3) || dy > dp(3)) {
                    discardTap = true;
                }
                if (canDragDown && !draggingDown && scale == 1 && dy >= dp(30) && dy / 2 > dx) {
                    draggingDown = true;
                    moving = false;
                    dragY = ev.getY();
                    if (isActionBarVisible) {
                        toggleActionBar(false, true);
                    }
                    return true;
                } else if (draggingDown) {
                    translationY = ev.getY() - dragY;
                    containerView.invalidate();
                } else if (!invalidCoords && animationStartTime == 0) {
                    float moveDx = moveStartX - ev.getX();
                    float moveDy = moveStartY - ev.getY();
                    if (moving || scale == 1 && Math.abs(moveDy) + dp(12) < Math.abs(moveDx) || scale != 1) {
                        if (!moving) {
                            moveDx = 0;
                            moveDy = 0;
                            moving = true;
                            canDragDown = false;
                        }

                        moveStartX = ev.getX();
                        moveStartY = ev.getY();
                        updateMinMax(scale);
                        if (translationX < minX || translationX > maxX) {
                            moveDx /= 3.0f;
                        }
                        if (maxY == 0 && minY == 0) {
                            if (translationY - moveDy < minY) {
                                translationY = minY;
                                moveDy = 0;
                            } else if (translationY - moveDy > maxY) {
                                translationY = maxY;
                                moveDy = 0;
                            }
                        } else {
                            if (translationY < minY || translationY > maxY) {
                                moveDy /= 3.0f;
                            }
                        }

                        translationX -= moveDx;
                        if (scale != 1) {
                            translationY -= moveDy;
                        }
                        containerView.invalidate();
                    }
                } else {
                    invalidCoords = false;
                    moveStartX = ev.getX();
                    moveStartY = ev.getY();
                }
            }
        } else if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL || ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            if (zooming) {
                invalidCoords = true;
                if (scale < 1.0f) {
                    updateMinMax(1.0f);
                    animateTo(1.0f, 0, 0, true);
                } else if (scale > 3.0f) {
                    float atx = (pinchCenterX - getContainerViewWidth() / 2) - ((pinchCenterX - getContainerViewWidth() / 2) - pinchStartX) * (3.0f / pinchStartScale);
                    float aty = (pinchCenterY - getContainerViewHeight() / 2) - ((pinchCenterY - getContainerViewHeight() / 2) - pinchStartY) * (3.0f / pinchStartScale);
                    updateMinMax(3.0f);
                    if (atx < minX) {
                        atx = minX;
                    } else if (atx > maxX) {
                        atx = maxX;
                    }
                    if (aty < minY) {
                        aty = minY;
                    } else if (aty > maxY) {
                        aty = maxY;
                    }
                    animateTo(3.0f, atx, aty, true);
                } else {
                    checkMinMax(true);
                }
                zooming = false;
            } else if (draggingDown) {
                if (Math.abs(dragY - ev.getY()) > getContainerViewHeight() / 6.0f) {
                    closePhoto(true, false);
                } else {
                    animateTo(1, 0, 0, false);
                }
                draggingDown = false;
            } else if (moving) {
                float moveToX = translationX;
                float moveToY = translationY;
                updateMinMax(scale);
                moving = false;
                canDragDown = true;
                if (velocityTracker != null && scale == 1) {
                    velocityTracker.computeCurrentVelocity(1000);
                }
                if (translationX < minX) {
                    moveToX = minX;
                } else if (translationX > maxX) {
                    moveToX = maxX;
                }
                if (translationY < minY) {
                    moveToY = minY;
                } else if (translationY > maxY) {
                    moveToY = maxY;
                }
                animateTo(scale, moveToX, moveToY, false);
            }
        }
        return false;
    }

    private void checkMinMax(boolean zoom) {
        float moveToX = translationX;
        float moveToY = translationY;
        updateMinMax(scale);
        if (translationX < minX) {
            moveToX = minX;
        } else if (translationX > maxX) {
            moveToX = maxX;
        }
        if (translationY < minY) {
            moveToY = minY;
        } else if (translationY > maxY) {
            moveToY = maxY;
        }
        animateTo(scale, moveToX, moveToY, zoom);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom) {
        animateTo(newScale, newTx, newTy, isZoom, 250);
    }

    private void animateTo(float newScale, float newTx, float newTy, boolean isZoom, int duration) {
        if (scale == newScale && translationX == newTx && translationY == newTy) {
            return;
        }
        zoomAnimation = isZoom;
        animateToScale = newScale;
        animateToX = newTx;
        animateToY = newTy;
        animationStartTime = System.currentTimeMillis();
        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(
                ObjectAnimator.ofFloat(this, "animationValue", 0, 1)
        );
        imageMoveAnimation.setInterpolator(interpolator);
        imageMoveAnimation.setDuration(duration);
        imageMoveAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                imageMoveAnimation = null;
                containerView.invalidate();
            }
        });
        imageMoveAnimation.start();
    }

    public final Property<SecretMediaViewer, Float> ANIMATION_VALUE = new AnimationProperties.FloatProperty<SecretMediaViewer>("animationValue") {
        @Override
        public void setValue(SecretMediaViewer object, float value) {
            object.setAnimationValue(value);
        }

        @Override
        public Float get(SecretMediaViewer object) {
            return object.getAnimationValue();
        }
    };

    @Keep
    public void setAnimationValue(float value) {
        animationValue = value;
        containerView.invalidate();
    }

    @Keep
    public float getAnimationValue() {
        return animationValue;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (scale != 1) {
            scroller.abortAnimation();
            scroller.fling(Math.round(translationX), Math.round(translationY), Math.round(velocityX), Math.round(velocityY), (int) minX, (int) maxX, (int) minY, (int) maxY);
            containerView.postInvalidate();
        }
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (discardTap) {
            return false;
        }
        if (videoPlayer != null && isActionBarVisible && (
            e.getX() >= playButton.getX() &&
            e.getY() >= playButton.getY() &&
            e.getX() <= playButton.getX() + playButton.getMeasuredWidth() &&
            e.getX() <= playButton.getX() + playButton.getMeasuredWidth()
        )) {
            videoPlayer.setPlayWhenReady(!videoPlayer.getPlayWhenReady());
            if (videoPlayer.getPlayWhenReady()) {
                toggleActionBar(true, true);
            } else {
                showPlayButton(true, true);
            }
        } else {
            toggleActionBar(!isActionBarVisible, true);
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (scale == 1.0f && (translationY != 0 || translationX != 0)) {
            return false;
        }
        if (animationStartTime != 0 || photoAnimationInProgress != 0) {
            return false;
        }
        if (scale == 1.0f) {
            float atx = (e.getX() - getContainerViewWidth() / 2) - ((e.getX() - getContainerViewWidth() / 2) - translationX) * (3.0f / scale);
            float aty = (e.getY() - getContainerViewHeight() / 2) - ((e.getY() - getContainerViewHeight() / 2) - translationY) * (3.0f / scale);
            updateMinMax(3.0f);
            if (atx < minX) {
                atx = minX;
            } else if (atx > maxX) {
                atx = maxX;
            }
            if (aty < minY) {
                aty = minY;
            } else if (aty > maxY) {
                aty = maxY;
            }
            animateTo(3.0f, atx, aty, true);
        } else {
            animateTo(1.0f, 0, 0, true);
        }
        doubleTap = true;
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private boolean scaleToFill() {
        return false;
    }


    private class VideoPlayerControlFrameLayout extends FrameLayout {

        private float progress = 1f;
        private boolean seekBarTransitionEnabled = true;
        private boolean translationYAnimationEnabled = true;
        private boolean ignoreLayout;
        private int parentWidth;
        private int parentHeight;

        private int lastTimeWidth;
        private FloatValueHolder timeValue = new FloatValueHolder(0);
        private SpringAnimation timeSpring = new SpringAnimation(timeValue)
                .setSpring(new SpringForce(0)
                        .setStiffness(750f)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                .addUpdateListener((animation, value, velocity) -> {
                    int extraWidth;
                    if (parentWidth > parentHeight) {
                        extraWidth = dp(48);
                    } else {
                        extraWidth = 0;
                    }

                    seekbar.setSize((int) (getMeasuredWidth() - dp(2 + 14) - value - extraWidth), getMeasuredHeight());
                });

        public VideoPlayerControlFrameLayout(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (progress < 1f) {
                return false;
            }
            if (seekbar.onTouch(event.getAction(), event.getX() - dp(2), event.getY())) {
                getParent().requestDisallowInterceptTouchEvent(true);
                seekbarView.invalidate();
                return true;
            }
            return true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();

            timeValue.setValue(0);
            lastTimeWidth = 0;
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int extraWidth;
            ignoreLayout = true;
            LayoutParams layoutParams = (LayoutParams) videoPlayerTime.getLayoutParams();
            if (parentWidth > parentHeight) {
                extraWidth = dp(48);
                layoutParams.rightMargin = dp(47);
            } else {
                extraWidth = 0;
                layoutParams.rightMargin = dp(12);
            }
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            long duration;
            if (videoPlayer != null) {
                duration = videoPlayer.getDuration();
                if (duration == C.TIME_UNSET) {
                    duration = 0;
                }
            } else {
                duration = 0;
            }
            duration /= 1000;

            String durationStr;
            if (duration / 60 > 60) {
                durationStr = String.format(Locale.ROOT, "%02d:%02d:%02d", (duration / 60) / 60, (duration / 60) % 60, duration % 60);
            } else {
                durationStr = String.format(Locale.ROOT, "%02d:%02d", duration / 60, duration % 60);
            }

            int size = (int) Math.ceil(videoPlayerTime.getPaint().measureText(String.format(Locale.ROOT, "%1$s / %1$s", durationStr)));
            timeSpring.cancel();
            if (lastTimeWidth != 0 && timeValue.getValue() != size) {
                timeSpring.getSpring().setFinalPosition(size);
                timeSpring.start();
            } else {
                seekbar.setSize(getMeasuredWidth() - dp(2 + 14) - size - extraWidth, getMeasuredHeight());
                timeValue.setValue(size);
            }
            lastTimeWidth = size;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            float progress = 0;
            if (videoPlayer != null) {
                progress = videoPlayer.getCurrentPosition() / (float) videoPlayer.getDuration();
            }
            seekbar.setProgress(progress);
        }

        public float getProgress() {
            return progress;
        }

        public void setProgress(float progress) {
            if (this.progress != progress) {
                this.progress = progress;
                onProgressChanged(progress);
            }
        }

        public final Property<VideoPlayerControlFrameLayout, Float> SEEKBAR_ALPHA = new AnimationProperties.FloatProperty<VideoPlayerControlFrameLayout>("progress") {
            @Override
            public void setValue(VideoPlayerControlFrameLayout object, float value) {
                object.setProgress(value);
            }

            @Override
            public Float get(VideoPlayerControlFrameLayout object) {
                return object.getProgress();
            }
        };

        private void onProgressChanged(float progress) {
            videoPlayerTime.setAlpha(progress);
            if (seekBarTransitionEnabled) {
                videoPlayerTime.setPivotX(videoPlayerTime.getWidth());
                videoPlayerTime.setPivotY(videoPlayerTime.getHeight());
                videoPlayerTime.setScaleX(1f - 0.1f * (1f - progress));
                videoPlayerTime.setScaleY(1f - 0.1f * (1f - progress));
                seekbar.setTransitionProgress(1f - progress);
            } else {
                if (translationYAnimationEnabled) {
                    setTranslationY(AndroidUtilities.dpf2(24) * (1f - progress));
                }
                seekbarView.setAlpha(progress);
            }
        }

        public boolean isSeekBarTransitionEnabled() {
            return seekBarTransitionEnabled;
        }

        public void setSeekBarTransitionEnabled(boolean seekBarTransitionEnabled) {
            if (this.seekBarTransitionEnabled != seekBarTransitionEnabled) {
                this.seekBarTransitionEnabled = seekBarTransitionEnabled;
                if (seekBarTransitionEnabled) {
                    setTranslationY(0);
                    seekbarView.setAlpha(1f);
                } else {
                    videoPlayerTime.setScaleX(1f);
                    videoPlayerTime.setScaleY(1f);
                    seekbar.setTransitionProgress(0f);
                }
                onProgressChanged(progress);
            }
        }

        public void setTranslationYAnimationEnabled(boolean translationYAnimationEnabled) {
            if (this.translationYAnimationEnabled != translationYAnimationEnabled) {
                this.translationYAnimationEnabled = translationYAnimationEnabled;
                if (!translationYAnimationEnabled) {
                    setTranslationY(0);
                }
                onProgressChanged(progress);
            }
        }
    }


    private boolean captionHwLayerEnabled;

    private void setCaptionHwLayerEnabled(boolean enabled) {
        if (captionHwLayerEnabled != enabled) {
            captionHwLayerEnabled = enabled;
            captionTextViewSwitcher.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            captionTextViewSwitcher.getCurrentView().setLayerType(View.LAYER_TYPE_HARDWARE, null);
            captionTextViewSwitcher.getNextView().setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }
}
