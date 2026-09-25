package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.utils.settings.SharedSettings;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.utils.camera.roundvideo.RoundVideoSession;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Telegram UI adapter for the standalone Camera2 round-video session. */
public final class InstantCameraView2 extends InstantCameraViewBase {

    private static final long MINIMUM_DURATION_MS = 800L;
    private static final float ZOOM_GESTURE_EFFORT = 1.5f;
    private static final int SWITCH_PREVIEW_BITMAP_SIZE = 180;
    private static final long UI_FRAME_FALLBACK_DELAY_NS = 70_000_000L;

    private final InstantCameraView.Delegate delegate;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int currentAccount = UserConfig.selectedAccount;
    private final int recordingGuid;
    private final boolean secretChat;
    private final View parentView;
    private final CameraContainer cameraContainer;
    private final RoundVideoProgressView progressView;
    private final FrameLayout previewContainer;
    private final TextureView textureView;
    private final BackupImageView textureOverlayView;
    private final LinearLayout buttonsLayout;
    private final FlashViews.ImageViewInvertable switchCameraButton;
    private final FlashViews.ImageViewInvertable flashButton;
    private final ImageView muteImageView;
    private final FlashViews flashViews;
    private final int[] screenPosition = new int[2];
    private final Matrix previewBitmapMatrix = new Matrix();
    private final float[] previewBitmapMatrixValues = new float[9];
    private final Paint previewBitmapPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
    );
    private final int buttonIconSize;

    private RoundVideoSession session;
    private RoundVideoSession.OutputResolution activeOutputResolution;
    private RoundVideoSession.StateInfo stateInfo;
    private RoundVideoSession.CameraCapabilities capabilities;
    private TelegramRoundVideoUpload upload;
    private VideoEditedInfo videoEditedInfo;
    private SendOptions pendingSend;
    private AnimatorSet visibilityAnimator;
    private ValueAnimator zoomResetAnimator;
    private RLottieDrawable switchCameraDrawable;
    private RLottieDrawable flashOnDrawable;
    private RLottieDrawable flashOffDrawable;
    private boolean startedNotificationSent;
    private boolean resumeNotificationPending;
    private boolean stopNotificationSent;
    private boolean muted;
    private boolean sent;
    private boolean opened;
    private boolean setVisibilityFromPause;
    private boolean drawInitialPlaceholder;
    private boolean switchPlaceholderVisible;
    private Bitmap lastBitmap;
    private long lastVisibleDurationMs;
    private float panTranslationY;
    private float animationTranslationY;
    private float pinchStartDistance;
    private float zoomProgress;
    private int zoomPointerId1 = -1;
    private int zoomPointerId2 = -1;
    private boolean pinchZoomActive;
    private boolean heavyOperationsStopped;
    private float previousWindowBrightness = Float.NaN;
    private int textureViewSize;
    private long lastPreviewUiFrameRealtimeNs;
    private boolean recordingUiFallbackRegistered;
    private boolean handlingPreviewTextureInvalidate;

    private final Runnable recordingUiFallback = () -> {
        if (!isRecordingState()) return;
        long nowNs = SystemClock.elapsedRealtimeNanos();
        if (lastPreviewUiFrameRealtimeNs == 0L
                || nowNs - lastPreviewUiFrameRealtimeNs >= UI_FRAME_FALLBACK_DELAY_NS) {
            updateRecordingUiFrame();
        }
    };

    public InstantCameraView2(
            Context context,
            InstantCameraView.Delegate delegate,
            Theme.ResourcesProvider resourcesProvider,
            boolean isNewDesign
    ) {
        super(context);
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;
        recordingGuid = delegate.getClassGuid();
        secretChat = delegate.isSecretChat();
        parentView = delegate.getFragmentView();
        setWillNotDraw(false);

        flashViews = new FlashViews(context, null, this, null);
        flashViews.setWarmth(0.5f);
        addView(flashViews.backgroundView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));

        cameraContainer = new CameraContainer(context);
        progressView = new RoundVideoProgressView(context);
        previewContainer = new FrameLayout(context);
        textureView = createTextureView(context);
        previewContainer.addView(textureView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));
        Paint placeholderDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        placeholderDimPaint.setColor(Color.argb(40, 0, 0, 0));
        textureOverlayView = new BackupImageView(context) {
            private final CellFlickerDrawable flickerDrawable = new CellFlickerDrawable();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (!drawInitialPlaceholder) return;
                float radius = Math.min(getWidth(), getHeight()) * 0.5f;
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, placeholderDimPaint);
                AndroidUtilities.rectTmp.inset(dp(1), dp(1));
                flickerDrawable.setParentWidth(getWidth());
                flickerDrawable.draw(canvas, AndroidUtilities.rectTmp, radius, null);
                invalidate();
            }
        };
        textureOverlayView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View target, Outline outline) {
                outline.setOval(0, 0, target.getWidth(), target.getHeight());
            }
        });
        textureOverlayView.setClipToOutline(true);
        previewContainer.addView(textureOverlayView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));
        progressView.addView(previewContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL,
                14,
                14,
                14,
                14
        ));
        cameraContainer.addView(progressView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));
        addView(cameraContainer, new LayoutParams(
                AndroidUtilities.roundPlayingMessageSize,
                AndroidUtilities.roundPlayingMessageSize,
                Gravity.CENTER
        ));
        addView(flashViews.foregroundView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL
        ));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setPadding(dp(6), dp(6), dp(6), dp(6));
        addView(buttonsLayout, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                56,
                Gravity.LEFT | Gravity.BOTTOM,
                1,
                0,
                0,
                0
        ));

        switchCameraButton = new FlashViews.ImageViewInvertable(context);
        switchCameraButton.setScaleType(ImageView.ScaleType.CENTER);
        switchCameraButton.setContentDescription(
                LocaleController.getString(R.string.AccDescrSwitchCamera)
        );
        buttonsLayout.addView(switchCameraButton, LayoutHelper.createLinear(44, 44));
        switchCameraButton.setOnClickListener(view -> switchCamera());

        flashButton = new FlashViews.ImageViewInvertable(context);
        flashButton.setScaleType(ImageView.ScaleType.CENTER);
        buttonsLayout.addView(flashButton, LayoutHelper.createLinear(44, 44));
        flashButton.setOnClickListener(view -> toggleFlash());

        buttonIconSize = dp(isNewDesign ? 24 : 28);
        switchCameraDrawable = new RLottieDrawable(
                R.raw.roundcamera_flip,
                buttonIconSize,
                buttonIconSize
        );
        switchCameraDrawable.setCallback(switchCameraButton);
        switchCameraDrawable.setCurrentFrame(switchCameraDrawable.getFramesCount() - 1);
        switchCameraButton.setImageDrawable(switchCameraDrawable);
        updateFlashIcon();

        if (!isNewDesign) {
            flashViews.add(switchCameraButton);
            flashViews.add(flashButton);
        } else if (resourcesProvider != null && !resourcesProvider.isDark()) {
            switchCameraButton.setInvert(0.6f);
            flashButton.setInvert(0.6f);
        }

        muteImageView = new ImageView(context);
        muteImageView.setScaleType(ImageView.ScaleType.CENTER);
        muteImageView.setImageResource(R.drawable.video_mute);
        muteImageView.setAlpha(0f);
        addView(muteImageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        textureView.setOnTouchListener(this::handleZoomTouch);
        super.setVisibility(INVISIBLE);
    }

    @Override
    public void setButtonsBackground(
            BlurredBackgroundDrawableViewFactory factory,
            BlurredBackgroundColorProvider colorProvider
    ) {
        BlurredBackgroundDrawable drawable = factory.create(buttonsLayout, colorProvider);
        drawable.setPadding(dp(6));
        drawable.setRadius(dp(21));
        buttonsLayout.setBackground(drawable);
    }

    @Override
    public void setInternalPadding(int padding) {
        setPadding(0, 0, 0, padding);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        buttonsLayout.setAlpha(0f);
        cameraContainer.setAlpha(0f);
        cameraContainer.setScaleX(setVisibilityFromPause ? 1f : 0.1f);
        cameraContainer.setScaleY(setVisibilityFromPause ? 1f : 0.1f);
        cameraContainer.setTranslationX(0f);
        muteImageView.setAlpha(0f);
        muteImageView.setScaleX(1f);
        muteImageView.setScaleY(1f);
        progressView.getPaint().setAlpha(0);
        try {
            Activity activity = (Activity) getContext();
            if (visibility == VISIBLE) {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void destroy(boolean async) {
        setRecordingUiFrameClockActive(false);
        if (zoomResetAnimator != null) zoomResetAnimator.cancel();
        if (session != null) {
            session.release();
            session = null;
        }
        if (upload != null) {
            upload.release(!sent);
            upload = null;
        }
        setScreenFlashEnabled(false);
        MediaController.getInstance().requestRecordAudioFocus(false);
        startHeavyOperations();
    }

    @Override
    public void togglePause() {
        if (session == null || stateInfo == null) return;
        if (stateInfo.getState() == RoundVideoSession.State.RECORDING) {
            pauseForPreview();
        } else if (stateInfo.getState() == RoundVideoSession.State.PREVIEWING) {
            videoEditedInfo = null;
            resumeNotificationPending = true;
            stopNotificationSent = false;
            session.resumeRecording();
        }
    }

    @Override
    public boolean isPaused() {
        return stateInfo != null && (
                stateInfo.getState() == RoundVideoSession.State.PAUSING
                        || stateInfo.getState() == RoundVideoSession.State.PREVIEWING
                        || stateInfo.getState() == RoundVideoSession.State.RESUMING
        );
    }

    @Override
    public void showCamera(boolean fromPaused) {
        if (session != null) return;
        setVisibilityFromPause = fromPaused;
        setVisibility(VISIBLE);
        progressView.getPaint().setAlpha(255);
        sent = false;
        muted = false;
        startedNotificationSent = false;
        stopNotificationSent = false;
        lastVisibleDurationMs = 0L;
        progressView.setProgress(0f);
        showInitialPlaceholder();
        upload = new TelegramRoundVideoUpload(currentAccount, secretChat);
        activeOutputResolution = SharedSettings.roundVideoOutputResolution.get();
        session = new RoundVideoSession.Builder(getContext(), textureView)
                .setInitialFacing(SharedSettings.roundVideoLastCamera.get())
                .setOutputResolution(activeOutputResolution)
                .setVideoBitrate(SharedSettings.roundVideoVideoBitrate.get())
                .setCameraResolution(SharedSettings.roundVideoCameraResolution.get())
                .setFrameRate(SharedSettings.roundVideoFrameRate.get())
                .setCompositionEnabled(SharedSettings.roundVideoComposition.get())
                .setListener(sessionListener)
                .setOutputListener(upload)
                .setScreenFlashController(this::setScreenFlashEnabled)
                .build();
        MediaController.getInstance().requestRecordAudioFocus(true);
        session.start();
        startAnimation(true, fromPaused);
    }

    @Override
    public void startAnimation(boolean open, boolean fromPaused) {
        dispatchAnimationState(open, fromPaused);
        if (visibilityAnimator != null) {
            visibilityAnimator.removeAllListeners();
            visibilityAnimator.cancel();
        }
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        if (pipRoundVideoView != null) pipRoundVideoView.showTemporary(!open);
        if (open && !opened) {
            cameraContainer.setTranslationX(0f);
            animationTranslationY = fromPaused ? 0f : getMeasuredHeight() * 0.5f;
            updateTranslationY();
        }
        opened = open;
        if (parentView != null) parentView.invalidate();
        float targetTranslationX = !open
                && Math.max(getCurrentDurationMs(), lastVisibleDurationMs) > 300L
                ? dp(24) - getMeasuredWidth() * 0.5f
                : 0f;
        ValueAnimator translationYAnimator = ValueAnimator.ofFloat(
                open ? 1f : 0f,
                open ? 0f : 1f
        );
        translationYAnimator.addUpdateListener(animation -> {
            animationTranslationY = fromPaused
                    ? 0f
                    : getMeasuredHeight() * 0.5f * (float) animation.getAnimatedValue();
            updateTranslationY();
        });
        visibilityAnimator = new AnimatorSet();
        visibilityAnimator.playTogether(
                ObjectAnimator.ofFloat(buttonsLayout, View.ALPHA, open ? 1f : 0f),
                ObjectAnimator.ofFloat(cameraContainer, View.ALPHA, open ? 1f : 0f),
                ObjectAnimator.ofFloat(cameraContainer, View.SCALE_X, open ? 1f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, View.SCALE_Y, open ? 1f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, View.TRANSLATION_X, targetTranslationX),
                ObjectAnimator.ofFloat(muteImageView, View.ALPHA, muted && open ? 1f : 0f),
                translationYAnimator
        );
        visibilityAnimator.setDuration(180L);
        visibilityAnimator.setInterpolator(new DecelerateInterpolator());
        if (!open) {
            visibilityAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation == visibilityAnimator) {
                        hideCamera(true);
                        setVisibilityFromPause = false;
                        setVisibility(INVISIBLE);
                    }
                }
            });
        } else {
            setTranslationX(0f);
        }
        visibilityAnimator.start();
    }

    @Override
    public RectF getCameraRect() {
        textureView.getLocationOnScreen(screenPosition);
        return new RectF(
                screenPosition[0],
                screenPosition[1],
                screenPosition[0] + textureView.getWidth(),
                screenPosition[1] + textureView.getHeight()
        );
    }

    @Override
    public void changeVideoPreviewState(int state, float progress) {
        if (session == null || stateInfo == null
                || stateInfo.getState() != RoundVideoSession.State.PREVIEWING) {
            return;
        }
        applyExternalTrim();
        if (state == 0) {
            session.playPreview();
        } else if (state == 1) {
            session.pausePreview();
        } else if (state == 2) {
            session.seekPreview((long) (progress * stateInfo.getRecordedDurationMs()));
        }
    }

    @Override
    public void send(
            int state,
            boolean notify,
            int scheduleDate,
            int scheduleRepeatPeriod,
            int ttl,
            long effectId,
            long stars
    ) {
        if (session == null || stateInfo == null) return;
        if (state == 3) {
            pauseForPreview();
            return;
        }
        if (state != 1 && state != 4) return;
        long duration = getCurrentDurationMs();
        if (duration < MINIMUM_DURATION_MS) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.audioRecordTooShort,
                    recordingGuid,
                    true,
                    (int) duration
            );
            cancel(false);
            return;
        }
        if (stateInfo.getState() == RoundVideoSession.State.RECORDING) {
            postRecordStopped(5);
        } else {
            applyExternalTrim();
        }
        pendingSend = new SendOptions(
                notify,
                scheduleDate,
                scheduleRepeatPeriod,
                ttl,
                effectId,
                stars
        );
        session.finish(!muted);
    }

    @Override
    public void cancel(boolean byGesture) {
        if (session == null) return;
        postRecordStopped(byGesture ? 0 : 6);
        session.cancel();
        if (upload != null) upload.release(true);
        upload = null;
        MediaController.getInstance().requestRecordAudioFocus(false);
        startAnimation(false, false);
    }

    @Override
    public View getButtonsLayout() {
        return buttonsLayout;
    }

    @Override
    public View getMuteImageView() {
        return muteImageView;
    }

    @Override
    public Paint getPaint() {
        return progressView.getPaint();
    }

    @Override
    public void hideCamera(boolean async) {
        destroy(async);
        cameraContainer.setTranslationX(0f);
        animationTranslationY = 0f;
        updateTranslationY();
        cameraContainer.setImageReceiver(null);
        MediaController.getInstance().resumeByRewind();
    }

    @Override
    public TextureView getTextureView() {
        return textureView;
    }

    @Override
    public InstantCameraViewBase.InstantViewCameraContainer getCameraContainer() {
        return cameraContainer;
    }

    @Override
    public void setIsMessageTransition(boolean messageTransition) {
        cameraContainer.messageTransition = messageTransition;
    }

    @Override
    public void resetCameraFile() {
    }

    @Override
    public void onPanTranslationUpdate(float translationY) {
        panTranslationY = translationY * 0.5f;
        updateTranslationY();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && event.getY() > getMeasuredHeight() - getPaddingBottom()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        return super.onInterceptTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int newSize = MeasureSpec.getSize(heightMeasureSpec) - getPaddingBottom()
                > MeasureSpec.getSize(widthMeasureSpec) * 1.3f
                ? AndroidUtilities.roundPlayingMessageSize
                : AndroidUtilities.roundMessageSize;
        if (textureViewSize != newSize) {
            textureViewSize = newSize;
            cameraContainer.getLayoutParams().width = newSize + dp(28);
            cameraContainer.getLayoutParams().height = newSize + dp(28);
            ((LayoutParams) muteImageView.getLayoutParams()).topMargin = newSize / 2 - dp(24);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        int height = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        flashViews.backgroundView.measure(width, height);
        flashViews.foregroundView.measure(width, height);
    }

    private boolean isRecordingState() {
        return stateInfo != null
                && stateInfo.getState() == RoundVideoSession.State.RECORDING;
    }

    private void setRecordingUiFrameClockActive(boolean active) {
        if (active == recordingUiFallbackRegistered) return;
        recordingUiFallbackRegistered = active;
        lastPreviewUiFrameRealtimeNs = 0L;
        if (active) {
            Choreographer60FpsContent.getInstance().addFrameCallback(
                    recordingUiFallback,
                    30
            );
        } else {
            Choreographer60FpsContent.getInstance().removeFrameCallback(recordingUiFallback);
        }
        dispatchRecordingUiFrameClockActive(active);
    }

    private void updateRecordingUiFrame() {
        RoundVideoSession.StateInfo info = stateInfo;
        if (info == null || info.getState() != RoundVideoSession.State.RECORDING) return;
        long duration = Math.min(
                info.getMaximumDurationMs(),
                info.getRecordedDurationMs()
                        + SystemClock.elapsedRealtime()
                        - info.getRecordingStartedAtRealtimeMs()
        );
        lastVisibleDurationMs = duration;
        progressView.setProgress(duration / (float) info.getMaximumDurationMs());
        dispatchRecordingUiFrame(duration);
    }

    private final RoundVideoSession.Listener sessionListener = new RoundVideoSession.Listener() {
        @Override
        public void onStateChanged(@NonNull RoundVideoSession.StateInfo info) {
            RoundVideoSession.State previous = stateInfo == null ? null : stateInfo.getState();
            stateInfo = info;
            if (previous == RoundVideoSession.State.RECORDING
                    && info.getState() != RoundVideoSession.State.RECORDING) {
                saveLastCameraBitmap(true);
            }
            lastVisibleDurationMs = Math.max(lastVisibleDurationMs, info.getRecordedDurationMs());
            if (info.getState() == RoundVideoSession.State.RECORDING) {
                stopHeavyOperations();
                hideInitialPlaceholder();
                setRecordingUiFrameClockActive(true);
                updateRecordingUiFrame();
                if (!startedNotificationSent) {
                    startedNotificationSent = true;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(
                            NotificationCenter.recordStarted,
                            recordingGuid,
                            false
                    );
                } else if (resumeNotificationPending) {
                    resumeNotificationPending = false;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(
                            NotificationCenter.recordResumed
                    );
                }
            } else {
                setRecordingUiFrameClockActive(false);
                progressView.setProgress(
                        info.getRecordedDurationMs() / (float) info.getMaximumDurationMs()
                );
            }
            if (info.getState() == RoundVideoSession.State.COMPLETED
                    || info.getState() == RoundVideoSession.State.ERROR
                    || info.getState() == RoundVideoSession.State.RELEASED) {
                startHeavyOperations();
            }
            if (previous == RoundVideoSession.State.RECORDING
                    && info.getState() == RoundVideoSession.State.PAUSING) {
                postRecordStopped(2);
            }
            updateButtons();
        }

        @Override
        public void onCameraCapabilitiesChanged(
                @NonNull RoundVideoSession.CameraCapabilities cameraCapabilities
        ) {
            capabilities = cameraCapabilities;
            if (cameraCapabilities.getActiveFacing() != null) {
                SharedSettings.roundVideoLastCamera.set(cameraCapabilities.getActiveFacing());
            }
            updateButtons();
        }

        @Override
        public void onCameraSwitchStarted(@NonNull RoundVideoSession.CameraFacing facing) {
            showCameraSwitchPlaceholder();
            previewContainer.animate().cancel();
            previewContainer.setCameraDistance(previewContainer.getMeasuredHeight() * 8f);
            previewContainer.animate().rotationY(90f).setDuration(120L).start();
        }

        @Override
        public void onCameraSwitchCompleted(@NonNull RoundVideoSession.CameraFacing facing) {
            previewContainer.animate().cancel();
            previewContainer.setRotationY(-90f);
            previewContainer.animate().rotationY(0f).setDuration(140L).start();
        }

        @Override
        public void onCameraSwitchFirstFrame() {
            hideCameraSwitchPlaceholder();
        }

        @Override
        public void onPreviewReady(long durationMs, long trimStartMs, long trimEndMs) {
            progressView.setProgress(durationMs / 60_000f);
            muted = false;
            muteImageView.setAlpha(0f);
            File previewFile = session == null ? null : session.getPreviewFile();
            if (previewFile == null) return;
            videoEditedInfo = createVideoEditedInfo(previewFile, durationMs, null);
            videoEditedInfo.startTime = trimStartMs > 0 ? trimStartMs : -1;
            videoEditedInfo.endTime = trimEndMs < durationMs ? trimEndMs : -1;
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.audioDidSent,
                    recordingGuid,
                    videoEditedInfo,
                    previewFile.getAbsolutePath(),
                    new ArrayList<>()
            );
            float duration = Math.max(1L, durationMs);
            dispatchTrimRange(trimStartMs / duration, trimEndMs / duration);
        }

        @Override
        public void onPreviewFirstFrame() {
            hideInitialPlaceholder();
        }

        @Override
        public void onCompleted(@NonNull RoundVideoSession.Result result) {
            completeSend(result);
        }

        @Override
        public void onError(@NonNull Exception error) {
            startHeavyOperations();
            FileLog.e(error);
            if (upload != null) upload.release(true);
            upload = null;
            NotificationCenter.getInstance(currentAccount).postNotificationName(
                    NotificationCenter.recordStartError,
                    recordingGuid
            );
        }
    };

    private void stopHeavyOperations() {
        if (heavyOperationsStopped) return;
        heavyOperationsStopped = true;
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.stopAllHeavyOperations,
                512
        );
    }

    private void startHeavyOperations() {
        if (!heavyOperationsStopped) return;
        heavyOperationsStopped = false;
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.startAllHeavyOperations,
                512
        );
    }

    private void pauseForPreview() {
        if (session == null || stateInfo == null
                || stateInfo.getState() != RoundVideoSession.State.RECORDING) {
            return;
        }
        postRecordStopped(2);
        session.pauseRecording();
    }

    private void postRecordStopped(int reason) {
        if (stopNotificationSent && reason == 2) return;
        stopNotificationSent = true;
        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.recordStopped,
                recordingGuid,
                reason
        );
    }

    private long getCurrentDurationMs() {
        if (session == null || stateInfo == null) return 0L;
        if (stateInfo.getState() == RoundVideoSession.State.RECORDING) {
            return session.getRecordedDurationMs();
        }
        return stateInfo.getRecordedDurationMs();
    }

    private void applyExternalTrim() {
        if (session == null || stateInfo == null || videoEditedInfo == null) return;
        long duration = stateInfo.getRecordedDurationMs();
        long start = Math.max(0L, videoEditedInfo.startTime);
        long end = videoEditedInfo.endTime < 0
                ? duration
                : Math.min(duration, videoEditedInfo.endTime);
        session.setTrimRange(start, end);
    }

    private void completeSend(@NonNull RoundVideoSession.Result result) {
        SendOptions options = pendingSend;
        if (options == null) return;
        pendingSend = null;
        sent = true;
        TelegramRoundVideoUpload.UploadInfo uploadInfo = upload == null
                ? null
                : upload.getUploadInfo(result.getOutputId(), result.getFile());
        VideoEditedInfo info = createVideoEditedInfo(
                result.getFile(),
                result.getDurationMs(),
                uploadInfo
        );
        info.muted = !result.hasAudio();
        MediaController.PhotoEntry entry = new MediaController.PhotoEntry(
                0,
                0,
                0,
                result.getFile().getAbsolutePath(),
                0,
                true,
                0,
                0,
                0
        );
        entry.ttl = options.ttl;
        entry.effectId = options.effectId;
        delegate.sendMedia(
                entry,
                info,
                options.notify,
                options.scheduleDate,
                options.scheduleRepeatPeriod,
                false,
                options.stars
        );
        if (upload != null) upload.release(false);
        upload = null;
        MediaController.getInstance().requestRecordAudioFocus(false);
    }

    private VideoEditedInfo createVideoEditedInfo(
            @NonNull File file,
            long durationMs,
            TelegramRoundVideoUpload.UploadInfo uploadInfo
    ) {
        VideoEditedInfo info = new VideoEditedInfo();
        info.startTime = -1;
        info.endTime = -1;
        info.estimatedDuration = durationMs;
        info.estimatedSize = Math.max(1L, uploadInfo == null ? file.length() : uploadInfo.size);
        info.roundVideo = true;
        info.framerate = session == null || session.getActiveFrameRate() == null
                ? RoundVideoSession.FrameRate.FPS_30.getValue()
                : session.getActiveFrameRate().getValue();
        int outputSize = activeOutputResolution == null
                ? RoundVideoSession.OutputResolution.P480.getSize()
                : activeOutputResolution.getSize();
        info.resultWidth = info.originalWidth = outputSize;
        info.resultHeight = info.originalHeight = outputSize;
        info.originalPath = file.getAbsolutePath();
        if (uploadInfo != null) {
            info.file = uploadInfo.inputFile;
            info.encryptedFile = uploadInfo.encryptedFile;
            info.key = uploadInfo.key;
            info.iv = uploadInfo.iv;
        }
        return info;
    }

    private void switchCamera() {
        if (session == null || stateInfo == null || capabilities == null
                || !stateInfo.canControlCamera()) {
            return;
        }
        RoundVideoSession.CameraFacing facing =
                capabilities.getRequestedFacing() == RoundVideoSession.CameraFacing.FRONT
                        ? RoundVideoSession.CameraFacing.BACK
                        : RoundVideoSession.CameraFacing.FRONT;
        if (session.setCameraFacing(facing)) {
            switchCameraDrawable.setCurrentFrame(0);
            switchCameraDrawable.start();
        }
    }

    private void toggleFlash() {
        if (session == null || stateInfo == null || capabilities == null) return;
        if (session.setFlashEnabled(!stateInfo.isFlashEnabled())) {
            updateFlashIcon();
        }
    }

    private void updateFlashIcon() {
        boolean enabled = stateInfo != null && stateInfo.isFlashEnabled();
        if (enabled) {
            if (flashOffDrawable == null) {
                flashOffDrawable = new RLottieDrawable(
                        R.raw.roundcamera_flash_off,
                        buttonIconSize,
                        buttonIconSize
                );
                flashOffDrawable.setCallback(flashButton);
            }
            flashButton.setImageDrawable(flashOffDrawable);
        } else {
            if (flashOnDrawable == null) {
                flashOnDrawable = new RLottieDrawable(
                        R.raw.roundcamera_flash_on,
                        buttonIconSize,
                        buttonIconSize
                );
                flashOnDrawable.setCallback(flashButton);
            }
            flashButton.setImageDrawable(flashOnDrawable);
        }
    }

    private void updateButtons() {
        boolean cameraControls = stateInfo != null && stateInfo.canControlCamera();
        switchCameraButton.setEnabled(cameraControls);
        flashButton.setEnabled(cameraControls
                && capabilities != null
                && capabilities.getFlashType() != RoundVideoSession.FlashType.NONE);
        updateFlashIcon();
    }

    private void togglePreviewMuted() {
        if (session == null || stateInfo == null
                || stateInfo.getState() != RoundVideoSession.State.PREVIEWING) {
            return;
        }
        muted = !muted;
        session.setPreviewMuted(muted);
        if (videoEditedInfo != null) videoEditedInfo.muted = muted;
        muteImageView.animate().cancel();
        muteImageView.animate().alpha(muted ? 1f : 0f).setDuration(180L).start();
    }

    private boolean handleZoomTouch(View view, MotionEvent event) {
        if (session == null || stateInfo == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (zoomResetAnimator != null) zoomResetAnimator.cancel();
            pinchZoomActive = false;
            zoomPointerId1 = event.getPointerId(0);
            zoomPointerId2 = -1;
            if (stateInfo.getState() == RoundVideoSession.State.PREVIEWING) {
                togglePreviewMuted();
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                && event.getPointerCount() == 2
                && stateInfo.canControlCamera()) {
            zoomPointerId1 = event.getPointerId(0);
            zoomPointerId2 = event.getPointerId(1);
            pinchStartDistance = pointerDistance(event, 0, 1);
            pinchZoomActive = pinchStartDistance > 0f;
            zoomProgress = 0f;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pinchZoomActive) {
            int index1 = event.findPointerIndex(zoomPointerId1);
            int index2 = event.findPointerIndex(zoomPointerId2);
            if (index1 < 0 || index2 < 0) {
                finishZoomGesture();
                return true;
            }
            float scale = pointerDistance(event, index1, index2) / pinchStartDistance;
            float maximumZoomRatio = capabilities == null
                    ? 1f
                    : capabilities.getMaximumZoomRatio();
            float requestedZoomRatio = 1f
                    + Math.max(0f, scale - 1f) / ZOOM_GESTURE_EFFORT;
            zoomProgress = maximumZoomRatio <= 1f
                    ? 0f
                    : clamp(
                            (requestedZoomRatio - 1f) / (maximumZoomRatio - 1f),
                            0f,
                            1f
                    );
            session.setZoom(zoomProgress);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP && pinchZoomActive) {
            int pointerId = event.getPointerId(event.getActionIndex());
            if (pointerId == zoomPointerId1 || pointerId == zoomPointerId2) {
                finishZoomGesture();
            }
            return true;
        }
        if ((event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) && pinchZoomActive) {
            finishZoomGesture();
            return true;
        }
        return true;
    }

    private static float pointerDistance(MotionEvent event, int index1, int index2) {
        return (float) Math.hypot(
                event.getX(index2) - event.getX(index1),
                event.getY(index2) - event.getY(index1)
        );
    }

    private void finishZoomGesture() {
        if (!pinchZoomActive) return;
        pinchZoomActive = false;
        zoomPointerId1 = -1;
        zoomPointerId2 = -1;
        animateZoomReset();
    }

    private void animateZoomReset() {
        if (session == null) return;
        if (zoomResetAnimator != null) zoomResetAnimator.cancel();
        zoomResetAnimator = ValueAnimator.ofFloat(zoomProgress, 0f);
        zoomResetAnimator.setDuration(350L);
        zoomResetAnimator.addUpdateListener(animation -> {
            zoomProgress = (float) animation.getAnimatedValue();
            if (session != null) session.setZoom(zoomProgress);
        });
        zoomResetAnimator.start();
    }

    private void setScreenFlashEnabled(boolean enabled) {
        Activity activity = delegate.getParentActivity();
        if (activity == null) return;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        if (enabled) {
            if (Float.isNaN(previousWindowBrightness)) {
                previousWindowBrightness = attributes.screenBrightness;
            }
            attributes.screenBrightness = 1f;
            flashViews.flashIn(null);
        } else {
            if (!Float.isNaN(previousWindowBrightness)) {
                attributes.screenBrightness = previousWindowBrightness;
                previousWindowBrightness = Float.NaN;
            }
            flashViews.flashOut();
        }
        activity.getWindow().setAttributes(attributes);
    }

    private void updateTranslationY() {
        cameraContainer.setTranslationY(animationTranslationY + panTranslationY);
    }

    private void showInitialPlaceholder() {
        if (drawInitialPlaceholder) return;
        if (lastBitmap == null) {
            try {
                File file = new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg");
                lastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            } catch (Throwable ignore) {
            }
        }
        if (lastBitmap != null) {
            textureOverlayView.setImageBitmap(lastBitmap);
        } else {
            textureOverlayView.setImageResource(R.drawable.icplaceholder);
        }
        drawInitialPlaceholder = true;
        textureOverlayView.animate().cancel();
        textureOverlayView.setAlpha(1f);
        textureOverlayView.invalidate();
    }

    private void hideInitialPlaceholder() {
        if (!drawInitialPlaceholder) return;
        drawInitialPlaceholder = false;
        textureOverlayView.invalidate();
        textureOverlayView.animate().cancel();
        textureOverlayView.animate()
                .alpha(0f)
                .setDuration(120L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void showCameraSwitchPlaceholder() {
        saveLastCameraBitmap(false);
        if (lastBitmap == null) return;
        textureOverlayView.setImageBitmap(lastBitmap);
        switchPlaceholderVisible = true;
        textureOverlayView.animate().cancel();
        textureOverlayView.setAlpha(1f);
    }

    private void hideCameraSwitchPlaceholder() {
        if (!switchPlaceholderVisible) return;
        switchPlaceholderVisible = false;
        textureOverlayView.animate().cancel();
        textureOverlayView.animate()
                .alpha(0f)
                .setDuration(100L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void saveLastCameraBitmap(boolean persist) {
        if (!textureView.isAvailable()) return;
        Bitmap bitmap = textureView.getBitmap(
                SWITCH_PREVIEW_BITMAP_SIZE,
                SWITCH_PREVIEW_BITMAP_SIZE
        );
        if (bitmap == null) return;
        try {
            if (bitmap.getWidth() == 0
                    || bitmap.getHeight() == 0
                    || bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2) == 0) {
                return;
            }
            bitmap = applyPreviewBitmapTransform(bitmap);
            Utilities.stackBlurBitmap(bitmap, 15);
            Bitmap previous = lastBitmap;
            lastBitmap = bitmap;
            if (previous != null && previous != bitmap && !previous.isRecycled()) {
                previous.recycle();
            }
            if (persist) {
                try (FileOutputStream stream = new FileOutputStream(
                        new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg")
                )) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                } catch (Throwable ignore) {
                }
            }
        } finally {
            if (bitmap != lastBitmap) bitmap.recycle();
        }
    }

    private Bitmap applyPreviewBitmapTransform(@NonNull Bitmap source) {
        if (textureView.getWidth() <= 0 || textureView.getHeight() <= 0) return source;
        textureView.getTransform(previewBitmapMatrix);
        if (previewBitmapMatrix.isIdentity()) return source;

        previewBitmapMatrix.getValues(previewBitmapMatrixValues);
        float scaleX = source.getWidth() / (float) textureView.getWidth();
        float scaleY = source.getHeight() / (float) textureView.getHeight();
        previewBitmapMatrixValues[Matrix.MSKEW_X] *= scaleX / scaleY;
        previewBitmapMatrixValues[Matrix.MTRANS_X] *= scaleX;
        previewBitmapMatrixValues[Matrix.MSKEW_Y] *= scaleY / scaleX;
        previewBitmapMatrixValues[Matrix.MTRANS_Y] *= scaleY;
        previewBitmapMatrixValues[Matrix.MPERSP_0] /= scaleX;
        previewBitmapMatrixValues[Matrix.MPERSP_1] /= scaleY;
        previewBitmapMatrix.setValues(previewBitmapMatrixValues);

        Bitmap transformed = Bitmap.createBitmap(
                source.getWidth(),
                source.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(transformed);
        canvas.drawBitmap(source, previewBitmapMatrix, previewBitmapPaint);
        source.recycle();
        return transformed;
    }

    private TextureView createTextureView(Context context) {
        TextureView view = new TextureView(context) {
            @Override
            public void invalidate() {
                onPreviewTextureInvalidated();
                super.invalidate();
            }
        };
        view.setOpaque(true);
        view.setClickable(true);
        view.setCameraDistance(dp(8000));
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View target, Outline outline) {
                outline.setOval(0, 0, target.getWidth(), target.getHeight());
            }
        });
        view.setClipToOutline(true);
        return view;
    }

    private void onPreviewTextureInvalidated() {
        if (handlingPreviewTextureInvalidate || !isRecordingState()) return;
        handlingPreviewTextureInvalidate = true;
        try {
            lastPreviewUiFrameRealtimeNs = SystemClock.elapsedRealtimeNanos();
            updateRecordingUiFrame();
        } finally {
            handlingPreviewTextureInvalidate = false;
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private final class CameraContainer extends InstantCameraViewBase.InstantViewCameraContainer {
        private ImageReceiver imageReceiver;
        private float imageProgress;
        private boolean messageTransition;

        CameraContainer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public void setImageReceiver(ImageReceiver imageReceiver) {
            if (this.imageReceiver == null) imageProgress = 0f;
            this.imageReceiver = imageReceiver;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (imageReceiver == null) return;
            if (imageProgress < 1f) {
                imageProgress = Math.min(1f, imageProgress + 16f / 250f);
                invalidate();
            }
            canvas.save();
            canvas.translate(
                    progressView.getLeft() + previewContainer.getLeft() + textureView.getLeft(),
                    progressView.getTop() + previewContainer.getTop() + textureView.getTop()
            );
            if (imageReceiver.getImageWidth() != textureView.getWidth()) {
                float scale = textureView.getWidth() / imageReceiver.getImageWidth();
                canvas.scale(scale, scale);
            }
            canvas.translate(-imageReceiver.getImageX(), -imageReceiver.getImageY());
            float oldAlpha = imageReceiver.getAlpha();
            imageReceiver.setAlpha(imageProgress);
            imageReceiver.draw(canvas);
            imageReceiver.setAlpha(oldAlpha);
            canvas.restore();
        }
    }

    private static final class SendOptions {
        final boolean notify;
        final int scheduleDate;
        final int scheduleRepeatPeriod;
        final int ttl;
        final long effectId;
        final long stars;

        SendOptions(
                boolean notify,
                int scheduleDate,
                int scheduleRepeatPeriod,
                int ttl,
                long effectId,
                long stars
        ) {
            this.notify = notify;
            this.scheduleDate = scheduleDate;
            this.scheduleRepeatPeriod = scheduleRepeatPeriod;
            this.ttl = ttl;
            this.effectId = effectId;
            this.stars = stars;
        }
    }
}
