/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.ui.AspectRatioFrameLayout;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Scroller;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.ArrayList;

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
    }

    private class SecretDeleteTimer extends FrameLayout {

        private Paint deleteProgressPaint;
        private Paint afterDeleteProgressPaint;
        private Paint circlePaint;
        private Paint particlePaint;
        private RectF deleteProgressRect = new RectF();

        private long destroyTime;
        private long lastAnimationTime;
        private long destroyTtl;
        private boolean useVideoProgress;

        private class Particle {
            float x;
            float y;
            float vx;
            float vy;
            float velocity;
            float alpha;
            float lifeTime;
            float currentTime;
        }

        private ArrayList<Particle> particles = new ArrayList<>();
        private ArrayList<Particle> freeParticles = new ArrayList<>();

        private Drawable drawable;

        public SecretDeleteTimer(Context context) {
            super(context);
            setWillNotDraw(false);

            particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            particlePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
            particlePaint.setColor(0xffe6e6e6);
            particlePaint.setStrokeCap(Paint.Cap.ROUND);
            particlePaint.setStyle(Paint.Style.STROKE);

            deleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            deleteProgressPaint.setColor(0xffe6e6e6);

            afterDeleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            afterDeleteProgressPaint.setStyle(Paint.Style.STROKE);
            afterDeleteProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            afterDeleteProgressPaint.setColor(0xffe6e6e6);
            afterDeleteProgressPaint.setStrokeWidth(AndroidUtilities.dp(2));

            circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(0x7f000000);

            drawable = context.getResources().getDrawable(R.drawable.flame_small);
            for (int a = 0; a < 40; a++) {
                freeParticles.add(new Particle());
            }
        }

        private void setDestroyTime(long time, long ttl, boolean videoProgress) {
            destroyTime = time;
            destroyTtl = ttl;
            useVideoProgress = videoProgress;
            lastAnimationTime = System.currentTimeMillis();
            invalidate();
        }

        private void updateParticles(long dt) {
            int count = particles.size();
            for (int a = 0; a < count; a++) {
                Particle particle = particles.get(a);
                if (particle.currentTime >= particle.lifeTime) {
                    if (freeParticles.size() < 40) {
                        freeParticles.add(particle);
                    }
                    particles.remove(a);
                    a--;
                    count--;
                    continue;
                }
                particle.alpha = 1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(particle.currentTime / particle.lifeTime);
                particle.x += particle.vx * particle.velocity * dt / 500.0f;
                particle.y += particle.vy * particle.velocity * dt / 500.0f;
                particle.currentTime += dt;
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int y = getMeasuredHeight() / 2 - AndroidUtilities.dp(28) / 2;
            deleteProgressRect.set(getMeasuredWidth() - AndroidUtilities.dp(30 + 19), y, getMeasuredWidth() - AndroidUtilities.dp(2 + 19), y + AndroidUtilities.dp(28));
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            if (currentMessageObject == null || currentMessageObject.messageOwner.destroyTime == 0) {
                return;
            }

            canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(16 + 19), getMeasuredHeight() / 2, AndroidUtilities.dp(16), circlePaint);

            float progress;

            if (useVideoProgress) {
                if (videoPlayer != null) {
                    long duration = videoPlayer.getDuration();
                    long position = videoPlayer.getCurrentPosition();
                    if (duration != C.TIME_UNSET && position != C.TIME_UNSET) {
                        progress = 1.0f - (position / (float) duration);
                    } else {
                        progress = 1;
                    }
                } else {
                    progress = 1;
                }
            } else {
                long msTime = System.currentTimeMillis() + ConnectionsManager.getInstance().getTimeDifference() * 1000;
                progress = Math.max(0, destroyTime - msTime) / (destroyTtl * 1000.0f);
            }

            int x = getMeasuredWidth() - AndroidUtilities.dp(32 - 11 + 19);
            int y = (getMeasuredHeight() - AndroidUtilities.dp(14)) / 2 - AndroidUtilities.dp(0.5f);
            drawable.setBounds(x, y, x + AndroidUtilities.dp(10), y + AndroidUtilities.dp(14));
            drawable.draw(canvas);
            float radProgress = -360 * progress;
            canvas.drawArc(deleteProgressRect, -90, radProgress, false, afterDeleteProgressPaint);

            int count = particles.size();
            for (int a = 0; a < count; a++) {
                Particle particle = particles.get(a);
                particlePaint.setAlpha((int) (255 * particle.alpha));
                canvas.drawPoint(particle.x, particle.y, particlePaint);
            }

            double vx = Math.sin(Math.PI / 180.0 * (radProgress - 90));
            double vy = -Math.cos(Math.PI / 180.0 * (radProgress - 90));
            int rad = AndroidUtilities.dp(14);
            float cx = (float) (-vy * rad + deleteProgressRect.centerX());
            float cy = (float) (vx * rad + deleteProgressRect.centerY());
            for (int a = 0; a < 1; a++) {
                Particle newParticle;
                if (!freeParticles.isEmpty()) {
                    newParticle = freeParticles.get(0);
                    freeParticles.remove(0);
                } else {
                    newParticle = new Particle();
                }
                newParticle.x = cx;
                newParticle.y = cy;

                double angle = (Math.PI / 180.0) * (Utilities.random.nextInt(140) - 70);
                if (angle < 0) {
                    angle = Math.PI * 2 + angle;
                }
                newParticle.vx = (float) (vx * Math.cos(angle) - vy * Math.sin(angle));
                newParticle.vy = (float) (vx * Math.sin(angle) + vy * Math.cos(angle));

                newParticle.alpha = 1.0f;
                newParticle.currentTime = 0;

                newParticle.lifeTime = 400 + Utilities.random.nextInt(100);
                newParticle.velocity = 20.0f + Utilities.random.nextFloat() * 4.0f;
                particles.add(newParticle);
            }

            long newTime = System.currentTimeMillis();
            long dt = (newTime - lastAnimationTime);
            updateParticles(dt);
            lastAnimationTime = newTime;
            invalidate();
        }
    }

    private class PhotoBackgroundDrawable extends ColorDrawable {

        private Runnable drawRunnable;
        private int frame;

        public PhotoBackgroundDrawable(int color) {
            super(color);
        }

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
    }

    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayoutDrawer containerView;
    private ImageReceiver centerImage = new ImageReceiver();
    private SecretDeleteTimer secretDeleteTimer;
    private boolean isVisible;
    private int currentChannelId;
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

    private boolean textureUploaded;
    private boolean videoCrossfadeStarted;
    private float videoCrossfadeAlpha;
    private long videoCrossfadeAlphaLastTime;

    private Object lastInsets;

    private MessageObject currentMessageObject;

    private int coords[] = new int[2];

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
    private float animateToClipHorizontal;
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

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.messagesDeleted) {
            if (currentMessageObject == null) {
                return;
            }
            int channelId = (Integer) args[1];
            if (channelId != 0) {
                return;
            }
            ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>)args[0];
            if (markAsDeletedMessages.contains(currentMessageObject.getId())) {
                if (isVideo && !videoWatchedOneTime) {
                    closeVideoAfterWatch = true;
                } else {
                    closePhoto(true, true);
                }
            }
        } else if (id == NotificationCenter.didCreatedNewDeleteTask) {
            if (currentMessageObject == null || secretDeleteTimer == null) {
                return;
            }
            SparseArray<ArrayList<Long>> mids = (SparseArray<ArrayList<Long>>)args[0];
            for(int i = 0; i < mids.size(); i++) {
                int key = mids.keyAt(i);
                ArrayList<Long> arr = mids.get(key);
                for (int a = 0; a < arr.size(); a++) {
                    long mid = arr.get(a);
                    if (a == 0) {
                        int channelId = (int) (mid >> 32);
                        if (channelId < 0) {
                            channelId = 0;
                        }
                        if (channelId != currentChannelId) {
                            return;
                        }
                    }
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
                    closePhoto(true, true);
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
            aspectRatioFrameLayout.setVisibility(View.INVISIBLE);
            containerView.addView(aspectRatioFrameLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            videoTextureView = new TextureView(parentActivity);
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        }
        textureUploaded = false;
        videoCrossfadeStarted = false;
        videoTextureView.setAlpha(videoCrossfadeAlpha = 0.0f);
        if (videoPlayer == null) {
            videoPlayer = new VideoPlayer();
            videoPlayer.setTextureView(videoTextureView);
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (videoPlayer == null || currentMessageObject == null) {
                        return;
                    }
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
                                closePhoto(true, true);
                            } else {
                                videoPlayer.seekTo(0);
                                videoPlayer.play();
                            }
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    FileLog.e(e);
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
    }

    private void releasePlayer() {
        if (videoPlayer != null) {
            videoPlayer.releasePlayer();
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
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
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
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
                    widthSize -= ((WindowInsets) lastInsets).getSystemWindowInsetLeft();
                }
                containerView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
            }

            @SuppressWarnings("DrawAllocation")
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int x = 0;
                if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
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

        containerView = new FrameLayoutDrawer(activity) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (secretDeleteTimer != null) {
                    int y = (ActionBar.getCurrentActionBarHeight() - secretDeleteTimer.getMeasuredHeight()) / 2 + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                    secretDeleteTimer.layout(secretDeleteTimer.getLeft(), y, secretDeleteTimer.getRight(), y + secretDeleteTimer.getMeasuredHeight());
                }
            }
        };
        containerView.setFocusable(false);
        windowView.addView(containerView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerView.setLayoutParams(layoutParams);
        if (Build.VERSION.SDK_INT >= 21) {
            containerView.setFitsSystemWindows(true);
            containerView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @SuppressLint("NewApi")
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    WindowInsets oldInsets = (WindowInsets) lastInsets;
                    lastInsets = insets;
                    if (oldInsets == null || !oldInsets.toString().equals(insets.toString())) {
                        windowView.requestLayout();
                    }
                    return insets.consumeSystemWindowInsets();
                }
            });
            containerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        gestureDetector = new GestureDetector(containerView.getContext(), this);
        gestureDetector.setOnDoubleTapListener(this);

        actionBar = new ActionBar(activity);
        actionBar.setTitleColor(0xffffffff);
        actionBar.setSubtitleColor(0xffffffff);
        actionBar.setBackgroundColor(Theme.ACTION_BAR_PHOTO_VIEWER_COLOR);
        actionBar.setOccupyStatusBar(Build.VERSION.SDK_INT >= 21);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitleRightMargin(AndroidUtilities.dp(70));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    closePhoto(true, false);
                }
            }
        });

        secretDeleteTimer = new SecretDeleteTimer(activity);
        containerView.addView(secretDeleteTimer, LayoutHelper.createFrame(119, 48, Gravity.TOP | Gravity.RIGHT, 0, 0, 0, 0));

        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        centerImage.setParentView(containerView);
        centerImage.setForceCrossfade(true);
    }

    public void openMedia(MessageObject messageObject, PhotoViewer.PhotoViewerProvider provider) {
        if (parentActivity == null || messageObject == null || !messageObject.isSecretPhoto() || provider == null) {
            return;
        }
        final PhotoViewer.PlaceProviderObject object = provider.getPlaceForPhoto(messageObject, null, 0);
        if (object == null) {
            return;
        }

        //messageObject.messageOwner.destroyTime = (int) (System.currentTimeMillis() / 1000 + ConnectionsManager.getInstance().getTimeDifference()) + 4;

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

        final Rect drawRegion = object.imageReceiver.getDrawRegion();

        float width = (drawRegion.right - drawRegion.left);
        float height = (drawRegion.bottom - drawRegion.top);
        int viewWidth = AndroidUtilities.displaySize.x;
        int viewHeight = AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        scale = Math.max(width / viewWidth, height / viewHeight);

        translationX = object.viewX + drawRegion.left + width / 2 -  viewWidth / 2;
        translationY = object.viewY + drawRegion.top + height / 2 - viewHeight / 2;
        clipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
        int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());
        int coords2[] = new int[2];
        object.parentView.getLocationInWindow(coords2);
        clipTop = coords2[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + drawRegion.top) + object.clipTopAddition;
        if (clipTop < 0) {
            clipTop = 0;
        }
        clipBottom = (object.viewY + drawRegion.top + (int) height) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition;
        if (clipBottom < 0) {
            clipBottom = 0;
        }
        clipTop = Math.max(clipTop, clipVertical);
        clipBottom = Math.max(clipBottom, clipVertical);


        animationStartTime = System.currentTimeMillis();
        animateToX = 0;
        animateToY = 0;
        animateToClipBottom = 0;
        animateToClipHorizontal = 0;
        animateToClipTop = 0;
        animateToScale = 1.0f;
        zoomAnimation = true;

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        currentChannelId = messageObject.messageOwner.to_id != null ? messageObject.messageOwner.to_id.channel_id : 0;
        toggleActionBar(true, false);

        currentMessageObject = messageObject;
        TLRPC.Document document = messageObject.getDocument();
        Bitmap thumb = object.imageReceiver.getThumbBitmap();
        if (document != null) {
            actionBar.setTitle(LocaleController.getString("DisappearingVideo", R.string.DisappearingVideo));
            File f = new File(messageObject.messageOwner.attachPath);
            if (f.exists()) {
                preparePlayer(f);
            } else {
                File file = FileLoader.getPathToMessage(messageObject.messageOwner);
                File encryptedFile = new File(file.getAbsolutePath() + ".enc");
                if (encryptedFile.exists()) {
                    file = encryptedFile;
                }
                preparePlayer(file);
            }
            isVideo = true;
            centerImage.setImage(null, null, thumb != null ? new BitmapDrawable(thumb) : null, -1, null, 2);
            long destroyTime = (long) messageObject.messageOwner.destroyTime * 1000;
            long currentTime = System.currentTimeMillis() + ConnectionsManager.getInstance().getTimeDifference() * 1000;
            long timeToDestroy = destroyTime - currentTime;
            long duration = messageObject.getDuration() * 1000;
            if (duration > timeToDestroy) {
                secretDeleteTimer.setDestroyTime(-1, -1, true);
            } else {
                secretDeleteTimer.setDestroyTime((long) messageObject.messageOwner.destroyTime * 1000, messageObject.messageOwner.ttl, false);
            }
        } else {
            actionBar.setTitle(LocaleController.getString("DisappearingPhoto", R.string.DisappearingPhoto));
            TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
            centerImage.setImage(sizeFull.location, null, thumb != null ? new BitmapDrawable(thumb) : null, -1, null, 2);
            secretDeleteTimer.setDestroyTime((long) messageObject.messageOwner.destroyTime * 1000, messageObject.messageOwner.ttl, false);
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

        imageMoveAnimation = new AnimatorSet();
        imageMoveAnimation.playTogether(
                ObjectAnimator.ofFloat(actionBar, "alpha", 0, 1.0f),
                ObjectAnimator.ofFloat(secretDeleteTimer, "alpha", 0, 1.0f),
                ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0, 255),
                ObjectAnimator.ofFloat(secretDeleteTimer, "alpha", 0, 1.0f),
                ObjectAnimator.ofFloat(this, "animationValue", 0, 1)
        );
        photoAnimationInProgress = 3;
        photoAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                photoAnimationInProgress = 0;
                imageMoveAnimation = null;
                if (containerView == null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 18) {
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                containerView.invalidate();
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
        if (Build.VERSION.SDK_INT >= 18) {
            containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        imageMoveAnimation.setInterpolator(new DecelerateInterpolator());
        photoBackgroundDrawable.frame = 0;
        photoBackgroundDrawable.drawRunnable = new Runnable() {
            @Override
            public void run() {
                disableShowCheck = false;
                object.imageReceiver.setVisible(false, true);
            }
        };
        imageMoveAnimation.start();
    }

    public boolean isShowingImage(MessageObject object) {
        return isVisible && !disableShowCheck && object != null && currentMessageObject != null && currentMessageObject.getId() == object.getId();
    }

    private void toggleActionBar(boolean show, final boolean animated) {
        if (show) {
            actionBar.setVisibility(View.VISIBLE);
        }
        actionBar.setEnabled(show);
        isActionBarVisible = show;

        if (animated) {
            ArrayList<Animator> arrayList = new ArrayList<>();
            arrayList.add(ObjectAnimator.ofFloat(actionBar, "alpha", show ? 1.0f : 0.0f));
            currentActionBarAnimation = new AnimatorSet();
            currentActionBarAnimation.playTogether(arrayList);
            if (!show) {
                currentActionBarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentActionBarAnimation != null && currentActionBarAnimation.equals(animation)) {
                            actionBar.setVisibility(View.GONE);
                            currentActionBarAnimation = null;
                        }
                    }
                });
            }

            currentActionBarAnimation.setDuration(200);
            currentActionBarAnimation.start();
        } else {
            actionBar.setAlpha(show ? 1.0f : 0.0f);
            if (!show) {
                actionBar.setVisibility(View.GONE);
            }
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void destroyPhotoViewer() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);
        isVisible = false;
        currentProvider = null;
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

    private void onDraw(Canvas canvas) {
        if (!isPhotoVisible) {
            return;
        }

        float currentTranslationY;
        float currentTranslationX;
        float currentScale;
        float currentClipTop;
        float currentClipBottom;
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
                currentClipHorizontal = clipHorizontal + (animateToClipHorizontal - clipHorizontal) * av;
            } else {
                currentScale = scale + (animateToScale - scale) * animationValue;
                currentTranslationY = translationY + (animateToY - translationY) * animationValue;
                currentTranslationX = translationX + (animateToX - translationX) * animationValue;
                currentClipTop = clipTop + (animateToClipTop - clipTop) * animationValue;
                currentClipBottom = clipBottom + (animateToClipBottom - clipBottom) * animationValue;
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
            currentClipHorizontal = clipHorizontal;
            if (!moving) {
                aty = translationY;
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

    public float getVideoCrossfadeAlpha() {
        return videoCrossfadeAlpha;
    }

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

    public void closePhoto(boolean animated, boolean byDelete) {
        if (parentActivity == null || !isPhotoVisible || checkPhotoAnimation()) {
            return;
        }

        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagesDeleted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateMessageMedia);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didCreatedNewDeleteTask);

        isActionBarVisible = false;

        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        closeTime = System.currentTimeMillis();
        final PhotoViewer.PlaceProviderObject object;
        if (currentMessageObject.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty || currentMessageObject.messageOwner.media.document instanceof TLRPC.TL_documentEmpty) {
            object = null;
        } else {
            object = currentProvider.getPlaceForPhoto(currentMessageObject, null, 0);
        }
        if (videoPlayer != null) {
            videoPlayer.pause();
        }
        if (animated) {
            photoAnimationInProgress = 3;
            containerView.invalidate();

            imageMoveAnimation = new AnimatorSet();

            if (object != null && object.imageReceiver.getThumbBitmap() != null && !byDelete) {
                object.imageReceiver.setVisible(false, true);

                final Rect drawRegion = object.imageReceiver.getDrawRegion();

                float width = (drawRegion.right - drawRegion.left);
                float height = (drawRegion.bottom - drawRegion.top);
                int viewWidth = AndroidUtilities.displaySize.x;
                int viewHeight = AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                animateToScale = Math.max(width / viewWidth, height / viewHeight);
                animateToX = object.viewX + drawRegion.left + width / 2 -  viewWidth / 2;
                animateToY = object.viewY + drawRegion.top + height / 2 - viewHeight / 2;
                animateToClipHorizontal = Math.abs(drawRegion.left - object.imageReceiver.getImageX());
                int clipVertical = Math.abs(drawRegion.top - object.imageReceiver.getImageY());
                int coords2[] = new int[2];
                object.parentView.getLocationInWindow(coords2);
                animateToClipTop = coords2[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight) - (object.viewY + drawRegion.top) + object.clipTopAddition;
                if (animateToClipTop < 0) {
                    animateToClipTop = 0;
                }
                animateToClipBottom = (object.viewY + drawRegion.top + (int) height) - (coords2[1] + object.parentView.getHeight() - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight)) + object.clipBottomAddition;
                if (animateToClipBottom < 0) {
                    animateToClipBottom = 0;
                }
                animationStartTime = System.currentTimeMillis();
                animateToClipBottom = Math.max(animateToClipBottom, clipVertical);
                animateToClipTop = Math.max(animateToClipTop, clipVertical);
                zoomAnimation = true;
            } else {
                int h = (AndroidUtilities.displaySize.y + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0));
                animateToY = translationY >= 0 ? h : -h;
            }
            if (isVideo) {
                videoCrossfadeStarted = false;
                textureUploaded = false;
                imageMoveAnimation.playTogether(
                        ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0),
                        ObjectAnimator.ofFloat(this, "animationValue", 0, 1),
                        ObjectAnimator.ofFloat(actionBar, "alpha", 0),
                        ObjectAnimator.ofFloat(secretDeleteTimer, "alpha", 0),
                        ObjectAnimator.ofFloat(this, "videoCrossfadeAlpha", 0)
                );
            } else {
                centerImage.setManualAlphaAnimator(true);
                imageMoveAnimation.playTogether(
                        ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0),
                        ObjectAnimator.ofFloat(this, "animationValue", 0, 1),
                        ObjectAnimator.ofFloat(actionBar, "alpha", 0),
                        ObjectAnimator.ofFloat(secretDeleteTimer, "alpha", 0),
                        ObjectAnimator.ofFloat(centerImage, "currentAlpha", 0.0f)
                );
            }

            photoAnimationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    imageMoveAnimation = null;
                    photoAnimationInProgress = 0;
                    if (Build.VERSION.SDK_INT >= 18) {
                        containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    containerView.setVisibility(View.INVISIBLE);
                    onPhotoClosed(object);
                }
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (photoAnimationEndRunnable != null) {
                                photoAnimationEndRunnable.run();
                                photoAnimationEndRunnable = null;
                            }
                        }
                    });
                }
            });
            photoTransitionAnimationStartTime = System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            imageMoveAnimation.start();
        } else {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(containerView, "scaleX", 0.9f),
                    ObjectAnimator.ofFloat(containerView, "scaleY", 0.9f),
                    ObjectAnimator.ofInt(photoBackgroundDrawable, "alpha", 0),
                    ObjectAnimator.ofFloat(actionBar, "alpha", 0)
            );
            photoAnimationInProgress = 2;
            photoAnimationEndRunnable = new Runnable() {
                @Override
                public void run() {
                    if (containerView == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 18) {
                        containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                    containerView.setVisibility(View.INVISIBLE);
                    photoAnimationInProgress = 0;
                    onPhotoClosed(object);
                    containerView.setScaleX(1.0f);
                    containerView.setScaleY(1.0f);
                }
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
            if (Build.VERSION.SDK_INT >= 18) {
                containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            animatorSet.start();
        }
    }

    private void onPhotoClosed(PhotoViewer.PlaceProviderObject object) {
        isVisible = false;
        currentProvider = null;
        disableShowCheck = false;
        releasePlayer();
        ArrayList<File> filesToDelete = new ArrayList<>();
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
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
            }
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
                if (dx > AndroidUtilities.dp(3) || dy > AndroidUtilities.dp(3)) {
                    discardTap = true;
                }
                if (canDragDown && !draggingDown && scale == 1 && dy >= AndroidUtilities.dp(30) && dy / 2 > dx) {
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
                    if (moving || scale == 1 && Math.abs(moveDy) + AndroidUtilities.dp(12) < Math.abs(moveDx) || scale != 1) {
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

    public void setAnimationValue(float value) {
        animationValue = value;
        containerView.invalidate();
    }

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
        toggleActionBar(!isActionBarVisible, true);
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
}
