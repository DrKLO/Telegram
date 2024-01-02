package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.isTablet;
import static org.telegram.ui.GroupCallActivity.TRANSITION_DURATION;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.EncryptionKeyEmojifier;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.messenger.voip.VoipAudioManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.DarkAlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.voip.AcceptDeclineView;
import org.telegram.ui.Components.voip.EmojiRationalLayout;
import org.telegram.ui.Components.voip.HideEmojiTextView;
import org.telegram.ui.Components.voip.ImageWithWavesView;
import org.telegram.ui.Components.voip.EndCloseLayout;
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialogNew;
import org.telegram.ui.Components.voip.RateCallLayout;
import org.telegram.ui.Components.voip.VoIPBackgroundProvider;
import org.telegram.ui.Components.voip.VoIPButtonsLayout;
import org.telegram.ui.Components.voip.VoIPFloatingLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Components.voip.VoIPNotificationsLayout;
import org.telegram.ui.Components.voip.VoIPPiPView;
import org.telegram.ui.Components.voip.VoIPStatusTextView;
import org.telegram.ui.Components.voip.VoIPTextureView;
import org.telegram.ui.Components.voip.VoIPToggleButton;
import org.telegram.ui.Components.voip.VoIPWindowView;
import org.telegram.ui.Components.voip.VoIpGradientLayout;
import org.telegram.ui.Components.voip.VoIpHintView;
import org.telegram.ui.Components.voip.VoIpSnowView;
import org.telegram.ui.Components.voip.VoIpSwitchLayout;
import org.telegram.ui.Stories.recorder.HintView2;
import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class VoIPFragment implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate {

    private final static int STATE_GONE = 0;
    private final static int STATE_FULLSCREEN = 1;
    private final static int STATE_FLOATING = 2;

    private final int currentAccount;

    Activity activity;

    TLRPC.User currentUser;
    TLRPC.User callingUser;

    private VoIpSwitchLayout bottomSpeakerBtn;
    private VoIpSwitchLayout bottomVideoBtn;
    private VoIpSwitchLayout bottomMuteBtn;
    private VoIPToggleButton bottomEndCallBtn;
    private final VoIPBackgroundProvider backgroundProvider = new VoIPBackgroundProvider();

    private ViewGroup fragmentView;

    private VoIpGradientLayout gradientLayout;
    private VoIpSnowView voIpSnowView;
    private ImageWithWavesView callingUserPhotoViewMini;

    private TextView callingUserTitle;

    private VoIPStatusTextView statusTextView;
    private ImageView backIcon;
    private ImageView speakerPhoneIcon;
    private int selectedRating;

    LinearLayout emojiLayout;
    FrameLayout hideEmojiLayout;
    TextView hideEmojiTextView;
    RateCallLayout rateCallLayout;
    LinearLayout emojiRationalLayout;
    TextView emojiRationalTopTextView;
    TextView emojiRationalTextView;
    EndCloseLayout endCloseLayout;
    BackupImageView[] emojiViews = new BackupImageView[4];
    Emoji.EmojiDrawable[] emojiDrawables = new Emoji.EmojiDrawable[4];
    LinearLayout statusLayout;
    private VoIPFloatingLayout currentUserCameraFloatingLayout;
    private VoIPFloatingLayout callingUserMiniFloatingLayout;
    private boolean currentUserCameraIsFullscreen;

    private TextureViewRenderer callingUserMiniTextureRenderer;
    private VoIPTextureView callingUserTextureView;
    private VoIPTextureView currentUserTextureView;

    private AcceptDeclineView acceptDeclineView;
    private boolean isNearEar;

    View bottomShadow;
    View topShadow;

    private VoIPButtonsLayout buttonsLayout;
    Paint overlayPaint = new Paint();
    Paint overlayBottomPaint = new Paint();

    boolean isOutgoing;
    boolean callingUserIsVideo;
    boolean currentUserIsVideo;

    private PrivateVideoPreviewDialogNew previewDialog;

    private int currentState;
    private int previousState;
    private WindowInsets lastInsets;
    private boolean wasEstablished;

    float touchSlop;

    private static VoIPFragment instance;
    private VoIPWindowView windowView;
    private int statusLayoutAnimateToOffset;

    private AccessibilityManager accessibilityManager;

    private boolean uiVisible = true;
    float uiVisibilityAlpha = 1f;
    private boolean canHideUI;
    private Animator cameraShowingAnimator;
    private boolean emojiLoaded;
    private boolean emojiExpanded;

    private boolean canSwitchToPip;
    private boolean switchingToPip;

    private float enterTransitionProgress;
    private boolean isFinished;
    boolean cameraForceExpanded;
    boolean enterFromPiP;
    private boolean deviceIsLocked;

    long lastContentTapTime;
    AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    VoIPNotificationsLayout notificationsLayout;

    HintView2 tapToVideoTooltip;
    HintView2 encryptionTooltip;

    ValueAnimator uiVisibilityAnimator;
    ValueAnimator.AnimatorUpdateListener statusbarAnimatorListener = valueAnimator -> {
        uiVisibilityAlpha = (float) valueAnimator.getAnimatedValue();
        updateSystemBarColors();
    };

    float fillNaviagtionBarValue;

    boolean hideUiRunnableWaiting;
    Runnable hideUIRunnable = () -> {
        hideUiRunnableWaiting = false;
        boolean tapToVideoTooltipVisible = tapToVideoTooltip != null && tapToVideoTooltip.shown();
        if (canHideUI && uiVisible && !emojiExpanded && !tapToVideoTooltipVisible) {
            lastContentTapTime = System.currentTimeMillis();
            showUi(false);
            previousState = currentState;
            updateViewState();
        }
    };

    Runnable stopAnimatingBgRunnable = () -> {
        if (currentState == VoIPService.STATE_ESTABLISHED) {
            callingUserPhotoViewMini.setMute(true, false);
            gradientLayout.pause();
        }
    };
    private boolean lockOnScreen;
    private boolean screenWasWakeup;
    private boolean isVideoCall;

    /* === pinch to zoom === */
    private float pinchStartCenterX;
    private float pinchStartCenterY;
    private float pinchStartDistance;
    private float pinchTranslationX;
    private float pinchTranslationY;
    private boolean isInPinchToZoomTouchMode;

    private float pinchCenterX;
    private float pinchCenterY;

    private int pointerId1, pointerId2;

    float pinchScale = 1f;
    private boolean zoomStarted;
    private boolean canZoomGesture;
    ValueAnimator zoomBackAnimator;
    /* === pinch to zoom === */

    public static void show(Activity activity, int account) {
        show(activity, false, account);
    }

    public static void show(Activity activity, boolean overlay, int account) {
        if (instance != null && instance.windowView.getParent() == null) {
            if (instance != null) {
                instance.callingUserTextureView.renderer.release();
                instance.currentUserTextureView.renderer.release();
                instance.callingUserMiniTextureRenderer.release();
                instance.destroy();
            }
            instance = null;
        }
        if (instance != null || activity.isFinishing()) {
            return;
        }
        boolean transitionFromPip = VoIPPiPView.getInstance() != null;
        if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().getUser() == null) {
            return;
        }
        VoIPFragment fragment = new VoIPFragment(account);
        fragment.activity = activity;
        instance = fragment;
        VoIPWindowView windowView = new VoIPWindowView(activity, !transitionFromPip) {

            private final Path clipPath = new Path();
            private final RectF rectF = new RectF();

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (fragment.isFinished || fragment.switchingToPip) {
                    return false;
                }
                final int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && !fragment.lockOnScreen) {
                    fragment.onBackPressed();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (fragment.currentState == VoIPService.STATE_WAITING_INCOMING) {
                        final VoIPService service = VoIPService.getSharedInstance();
                        if (service != null) {
                            service.stopRinging();
                            return true;
                        }
                    }
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (fragment.switchingToPip && getAlpha() != 0) {
                    float width = fragment.callingUserTextureView.getWidth() * fragment.callingUserTextureView.getScaleX();
                    float height = fragment.callingUserTextureView.getHeight() * fragment.callingUserTextureView.getScaleY();
                    float padX = (fragment.callingUserTextureView.getWidth() - width) / 2;
                    float padY = (fragment.callingUserTextureView.getHeight() - height) / 2;
                    float x = fragment.callingUserTextureView.getX() + padX;
                    float y = fragment.callingUserTextureView.getY() + padY;
                    canvas.save();
                    clipPath.rewind();
                    rectF.set(x, y, x + width, y + height);
                    float round = AndroidUtilities.dp(4);
                    clipPath.addRoundRect(rectF, round, round, Path.Direction.CW);
                    clipPath.close();
                    canvas.clipPath(clipPath);
                    super.dispatchDraw(canvas);
                    canvas.restore();
                } else {
                    super.dispatchDraw(canvas);
                }
            }
        };
        instance.deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();

        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }
        instance.screenWasWakeup = !screenOn;
        windowView.setLockOnScreen(instance.deviceIsLocked);
        fragment.windowView = windowView;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            windowView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    fragment.setInsets(windowInsets);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return windowInsets.consumeSystemWindowInsets();
                }
            });
        }

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = windowView.createWindowLayoutParams();
        if (overlay) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        }
        wm.addView(windowView, layoutParams);
        View view = fragment.createView(activity);
        windowView.addView(view);

        if (transitionFromPip) {
            fragment.enterTransitionProgress = 0f;
            fragment.startTransitionFromPiP();
        } else {
            fragment.enterTransitionProgress = 1f;
            fragment.updateSystemBarColors();
        }
    }

    private void onBackPressed() {
        if (isFinished || switchingToPip) {
            return;
        }
        if (previewDialog != null) {
            previewDialog.dismiss(false, false);
            return;
        }
        if (callingUserIsVideo && currentUserIsVideo && cameraForceExpanded) {
            cameraForceExpanded = false;
            currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
            currentUserCameraIsFullscreen = false;
            previousState = currentState;
            updateViewState();
            return;
        }
        if (emojiExpanded) {
            expandEmoji(false);
        } else {
            if (emojiRationalLayout.getVisibility() != View.GONE) {
                return;
            }
            if (canSwitchToPip && !lockOnScreen) {
                if (AndroidUtilities.checkInlinePermissions(activity)) {
                    switchToPip();
                } else {
                    requestInlinePermissions();
                }
            } else {
                windowView.finish();
            }
        }
    }

    public static void clearInstance() {
        if (instance != null) {
            if (VoIPService.getSharedInstance() != null) {
                int h = instance.windowView.getMeasuredHeight();
                if (instance.canSwitchToPip) {
                    VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                        VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                        VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
                    }
                }
            }
            instance.callingUserTextureView.renderer.release();
            instance.currentUserTextureView.renderer.release();
            instance.callingUserMiniTextureRenderer.release();
            instance.destroy();
        }
        instance = null;
    }

    public static VoIPFragment getInstance() {
        return instance;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setInsets(WindowInsets windowInsets) {
        lastInsets = windowInsets;
        ((FrameLayout.LayoutParams) buttonsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) acceptDeclineView.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) backIcon.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) speakerPhoneIcon.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) statusLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(135) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) emojiLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(17) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) callingUserPhotoViewMini.getLayoutParams()).topMargin = AndroidUtilities.dp(93) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) hideEmojiLayout.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) emojiRationalLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(118) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) rateCallLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(380) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) callingUserMiniFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) notificationsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        currentUserCameraFloatingLayout.setInsets(lastInsets);
        callingUserMiniFloatingLayout.setInsets(lastInsets);
        fragmentView.requestLayout();
        if (previewDialog != null) {
            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
        }
    }

    public VoIPFragment(int account) {
        currentAccount = account;
        currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        callingUser = VoIPService.getSharedInstance().getUser();
        VoIPService.getSharedInstance().registerStateListener(this);
        isOutgoing = VoIPService.getSharedInstance().isOutgoing();
        previousState = -1;
        currentState = VoIPService.getSharedInstance().getCallState();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.nearEarEvent);
    }

    private void destroy() {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.nearEarEvent);
    }

    @Override
    public void onStateChanged(int state) {
        if (currentState != state) {
            previousState = currentState;
            currentState = state;
            if (windowView != null) {
                updateViewState();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.voipServiceCreated) {
            if (currentState == VoIPService.STATE_BUSY && VoIPService.getSharedInstance() != null) {
                currentUserTextureView.renderer.release();
                callingUserTextureView.renderer.release();
                callingUserMiniTextureRenderer.release();
                initRenderers();
                VoIPService.getSharedInstance().registerStateListener(this);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            updateKeyView(true);
        } else if (id == NotificationCenter.closeInCallActivity) {
            windowView.finish();
        } else if (id == NotificationCenter.webRtcSpeakerAmplitudeEvent) {
            callingUserPhotoViewMini.setAmplitude((float) args[0] * 15.0f);
        } else if (id == NotificationCenter.nearEarEvent) {
            isNearEar = (boolean) args[0];
            if (isNearEar) {
                callingUserPhotoViewMini.setMute(true, true);
            }
        }
    }

    private boolean signalBarWasReceived;

    @Override
    public void onSignalBarsCountChanged(int count) {
        if (count > 0) {
            signalBarWasReceived = true;
        }
        if (statusTextView != null && gradientLayout != null && gradientLayout.isConnectedCalled() && signalBarWasReceived) {
            AndroidUtilities.runOnUIThread(() -> {
                statusTextView.setSignalBarCount(count);
                if (count <= 1) {
                    gradientLayout.showToBadConnection();
                    statusTextView.showBadConnection(true, true);
                } else {
                    gradientLayout.hideBadConnection();
                    statusTextView.showBadConnection(false, true);
                }
            }, 400);
        }
    }

    @Override
    public void onAudioSettingsChanged() {
        updateButtons(true);
    }

    @Override
    public void onMediaStateUpdated(int audioState, int videoState) {
        previousState = currentState;
        if (videoState == Instance.VIDEO_STATE_ACTIVE && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        previousState = currentState;
        updateViewState();
    }

    @Override
    public void onVideoAvailableChange(boolean isAvailable) {
        previousState = currentState;
        if (isAvailable && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onScreenOnChange(boolean screenOn) {

    }

    public View createView(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        accessibilityManager = ContextCompat.getSystemService(context, AccessibilityManager.class);

        FrameLayout frameLayout = new FrameLayout(context) {

            float pressedX;
            float pressedY;
            boolean check;
            long pressedTime;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                    callingUserPhotoViewMini.setMute(false, false);
                    gradientLayout.resume();
                    AndroidUtilities.cancelRunOnUIThread(stopAnimatingBgRunnable);
                    if (currentState == VoIPService.STATE_ESTABLISHED) {
                        AndroidUtilities.runOnUIThread(stopAnimatingBgRunnable, 10000);
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                    callingUserPhotoViewMini.setMute(false, false);
                    gradientLayout.resume();
                    AndroidUtilities.cancelRunOnUIThread(stopAnimatingBgRunnable);
                    if (currentState == VoIPService.STATE_ESTABLISHED) {
                        AndroidUtilities.runOnUIThread(stopAnimatingBgRunnable, 10000);
                    }
                }
                /* === pinch to zoom === */
                if (!canZoomGesture && !isInPinchToZoomTouchMode && !zoomStarted && ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    finishZoom();
                    return false;
                }
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    canZoomGesture = false;
                    isInPinchToZoomTouchMode = false;
                    zoomStarted = false;
                }
                VoIPTextureView currentTextureView = getFullscreenTextureView();

                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        AndroidUtilities.rectTmp.set(currentTextureView.getX(), currentTextureView.getY(), currentTextureView.getX() + currentTextureView.getMeasuredWidth(), currentTextureView.getY() + currentTextureView.getMeasuredHeight());
                        AndroidUtilities.rectTmp.inset((currentTextureView.getMeasuredHeight() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredHeight()) / 2, (currentTextureView.getMeasuredWidth() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredWidth()) / 2);
                        if (!GroupCallActivity.isLandscapeMode) {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.bottom = Math.min(AndroidUtilities.rectTmp.bottom, currentTextureView.getMeasuredHeight() - AndroidUtilities.dp(90));
                        } else {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.right = Math.min(AndroidUtilities.rectTmp.right, currentTextureView.getMeasuredWidth() - AndroidUtilities.dp(90));
                        }
                        canZoomGesture = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());
                        if (!canZoomGesture) {
                            finishZoom();
                        }
                    }
                    if (canZoomGesture && !isInPinchToZoomTouchMode && ev.getPointerCount() == 2) {
                        pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                        pinchStartCenterX = pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                        pinchStartCenterY = pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                        pinchScale = 1f;

                        pointerId1 = ev.getPointerId(0);
                        pointerId2 = ev.getPointerId(1);
                        isInPinchToZoomTouchMode = true;
                    }
                } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
                    int index1 = -1;
                    int index2 = -1;
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        if (pointerId1 == ev.getPointerId(i)) {
                            index1 = i;
                        }
                        if (pointerId2 == ev.getPointerId(i)) {
                            index2 = i;
                        }
                    }
                    if (index1 == -1 || index2 == -1) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                        finishZoom();
                    } else {
                        pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
                        if (pinchScale > 1.005f && !zoomStarted) {
                            pinchStartDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));
                            pinchStartCenterX = pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                            pinchStartCenterY = pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;
                            pinchScale = 1f;
                            pinchTranslationX = 0f;
                            pinchTranslationY = 0f;
                            getParent().requestDisallowInterceptTouchEvent(true);
                            zoomStarted = true;
                            isInPinchToZoomTouchMode = true;
                        }

                        float newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                        float newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;

                        float moveDx = pinchStartCenterX - newPinchCenterX;
                        float moveDy = pinchStartCenterY - newPinchCenterY;
                        pinchTranslationX = -moveDx / pinchScale;
                        pinchTranslationY = -moveDy / pinchScale;
                        invalidate();
                    }
                } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    finishZoom();
                }
                fragmentView.invalidate();

                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressedX = ev.getX();
                        pressedY = ev.getY();
                        check = true;
                        pressedTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        check = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (check) {
                            float dx = ev.getX() - pressedX;
                            float dy = ev.getY() - pressedY;
                            long currentTime = System.currentTimeMillis();
                            if (dx * dx + dy * dy < touchSlop * touchSlop && currentTime - pressedTime < 300 && currentTime - lastContentTapTime > 300) {
                                lastContentTapTime = System.currentTimeMillis();
                                if (emojiExpanded) {
                                    expandEmoji(false);
                                } else if (canHideUI) {
                                    showUi(!uiVisible);
                                    previousState = currentState;
                                    if (!uiVisible && tapToVideoTooltip != null && tapToVideoTooltip.shown()) {
                                        tapToVideoTooltip.hide();
                                    }
                                    updateViewState();
                                }
                            }
                            check = false;
                        }
                        break;
                }
                return canZoomGesture || check;
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == gradientLayout && (currentUserIsVideo || callingUserIsVideo)) {
                    return false;
                }
                if (
                        child == gradientLayout ||
                                child == callingUserTextureView ||
                                (child == currentUserCameraFloatingLayout && currentUserCameraIsFullscreen)
                ) {
                    if (zoomStarted || zoomBackAnimator != null) {
                        canvas.save();
                        canvas.scale(pinchScale, pinchScale, pinchCenterX, pinchCenterY);
                        canvas.translate(pinchTranslationX, pinchTranslationY);
                        boolean b = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        return b;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        frameLayout.setClipToPadding(false);
        frameLayout.setClipChildren(false);
        frameLayout.setBackgroundColor(0xff000000);
        updateSystemBarColors();
        fragmentView = frameLayout;
        frameLayout.setFitsSystemWindows(true);

        gradientLayout = new VoIpGradientLayout(context, backgroundProvider);
        callingUserTextureView = new VoIPTextureView(context, false, true, false, false);
        callingUserTextureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        callingUserTextureView.renderer.setEnableHardwareScaler(true);
        callingUserTextureView.renderer.setRotateTextureWithScreen(true);
        callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
        //     callingUserTextureView.attachBackgroundRenderer();

        frameLayout.addView(gradientLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(voIpSnowView = new VoIpSnowView(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 220));
        frameLayout.addView(callingUserTextureView);

        final BackgroundGradientDrawable gradientDrawable = new BackgroundGradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1b354e, 0xFF255b7d});
        final BackgroundGradientDrawable.Sizes sizes = BackgroundGradientDrawable.Sizes.ofDeviceScreen(BackgroundGradientDrawable.Sizes.Orientation.PORTRAIT);
        gradientDrawable.startDithering(sizes, new BackgroundGradientDrawable.ListenerAdapter() {
            @Override
            public void onAllSizesReady() {
                gradientLayout.invalidate();
            }
        });

        currentUserCameraFloatingLayout = new VoIPFloatingLayout(context);
        currentUserCameraFloatingLayout.setDelegate((progress, value) -> currentUserTextureView.setScreenshareMiniProgress(progress, value));
        currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
        currentUserCameraIsFullscreen = true;
        currentUserTextureView = new VoIPTextureView(context, true, false);
        currentUserTextureView.renderer.setIsCamera(true);
        currentUserTextureView.renderer.setUseCameraRotation(true);
        currentUserCameraFloatingLayout.setOnTapListener(view -> {
            if (currentUserIsVideo && callingUserIsVideo && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                callingUserMiniFloatingLayout.setRelativePosition(currentUserCameraFloatingLayout);
                currentUserCameraIsFullscreen = true;
                cameraForceExpanded = true;
                previousState = currentState;
                updateViewState();
            }
        });
        currentUserTextureView.renderer.setMirror(true);
        currentUserCameraFloatingLayout.addView(currentUserTextureView);

        callingUserMiniFloatingLayout = new VoIPFloatingLayout(context);
        callingUserMiniFloatingLayout.alwaysFloating = true;
        callingUserMiniFloatingLayout.setFloatingMode(true, false);
        callingUserMiniTextureRenderer = new TextureViewRenderer(context);
        callingUserMiniTextureRenderer.setEnableHardwareScaler(true);
        callingUserMiniTextureRenderer.setIsCamera(false);
        callingUserMiniTextureRenderer.setFpsReduction(30);
        callingUserMiniTextureRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        View backgroundView = new View(context);
        backgroundView.setBackgroundColor(0xff1b1f23);
        //callingUserMiniFloatingLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        callingUserMiniFloatingLayout.addView(callingUserMiniTextureRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        callingUserMiniFloatingLayout.setOnTapListener(view -> {
            if (cameraForceExpanded && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
                currentUserCameraIsFullscreen = false;
                cameraForceExpanded = false;
                previousState = currentState;
                updateViewState();
            }
        });
        callingUserMiniFloatingLayout.setVisibility(View.GONE);

        frameLayout.addView(currentUserCameraFloatingLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        frameLayout.addView(callingUserMiniFloatingLayout);

        bottomShadow = new View(context);
        bottomShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.5f))}));
        frameLayout.addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 160, Gravity.BOTTOM));

        topShadow = new View(context);
        topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f)), Color.TRANSPARENT}));
        frameLayout.addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 160, Gravity.TOP));

        emojiLayout = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setVisibleToUser(emojiLoaded);
            }
        };
        emojiLayout.setOrientation(LinearLayout.HORIZONTAL);
        emojiLayout.setPadding(0, 0, 0, AndroidUtilities.dp(30));
        emojiLayout.setClipToPadding(false);

        emojiLayout.setOnClickListener(view -> {
            if (System.currentTimeMillis() - lastContentTapTime < 500) {
                return;
            }
            lastContentTapTime = System.currentTimeMillis();
            if (emojiExpanded) return;
            if (emojiLoaded) {
                expandEmoji(!emojiExpanded);
            }
        });

        hideEmojiTextView = new HideEmojiTextView(context, backgroundProvider);
        hideEmojiLayout = new FrameLayout(context);
        hideEmojiLayout.addView(hideEmojiTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 16, 0, 0));
        hideEmojiLayout.setVisibility(View.GONE);
        hideEmojiLayout.setOnClickListener(v -> {
            if (System.currentTimeMillis() - lastContentTapTime < 500) {
                return;
            }
            lastContentTapTime = System.currentTimeMillis();
            if (emojiLoaded) {
                expandEmoji(!emojiExpanded);
            }
        });

        emojiRationalLayout = new EmojiRationalLayout(context, backgroundProvider);
        emojiRationalLayout.setOrientation(LinearLayout.VERTICAL);

        emojiRationalTopTextView = new TextView(context);
        emojiRationalTopTextView.setText(LocaleController.getString("VoipCallEncryptionEndToEnd", R.string.VoipCallEncryptionEndToEnd));
        emojiRationalTopTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emojiRationalTopTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        emojiRationalTopTextView.setTextColor(Color.WHITE);
        emojiRationalTopTextView.setGravity(Gravity.CENTER);

        emojiRationalTextView = new TextView(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (changed) {
                    updateViewState();
                }
            }
        };
        emojiRationalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emojiRationalTextView.setTextColor(Color.WHITE);
        emojiRationalTextView.setGravity(Gravity.CENTER);
        CharSequence ellipsizeName = TextUtils.ellipsize(UserObject.getFirstName(callingUser), emojiRationalTextView.getPaint(), dp(300), TextUtils.TruncateAt.END);
        emojiRationalTextView.setText(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, ellipsizeName));

        emojiRationalLayout.setVisibility(View.GONE);
        emojiRationalLayout.addView(emojiRationalTopTextView);
        emojiRationalLayout.addView(emojiRationalTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        emojiRationalLayout.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(80), AndroidUtilities.dp(18), AndroidUtilities.dp(18));

        for (int i = 0; i < 4; i++) {
            emojiViews[i] = new BackupImageView(context);
            emojiViews[i].getImageReceiver().setAspectFit(true);
            emojiLayout.addView(emojiViews[i], LayoutHelper.createLinear(25, 25, i == 0 ? 0 : 6, 0, 0, 0));
        }
        statusLayout = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                final VoIPService service = VoIPService.getSharedInstance();
                final CharSequence callingUserTitleText = callingUserTitle.getText();
                if (service != null && !TextUtils.isEmpty(callingUserTitleText)) {
                    final StringBuilder builder = new StringBuilder(callingUserTitleText);

                    builder.append(", ");
                    if (service.privateCall != null && service.privateCall.video) {
                        builder.append(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding));
                    } else {
                        builder.append(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
                    }

                    final long callDuration = service.getCallDuration();
                    if (callDuration > 0) {
                        builder.append(", ");
                        builder.append(LocaleController.formatDuration((int) (callDuration / 1000)));
                    }

                    info.setText(builder);
                }
            }
        };
        statusLayout.setOrientation(LinearLayout.VERTICAL);
        statusLayout.setFocusable(true);
        statusLayout.setFocusableInTouchMode(true);

        callingUserPhotoViewMini = new ImageWithWavesView(context);
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(callingUser);
        callingUserPhotoViewMini.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_BIG), null, avatarDrawable, callingUser);
        callingUserPhotoViewMini.setRoundRadius(AndroidUtilities.dp(135) / 2);

        callingUserTitle = new TextView(context);
        callingUserTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        CharSequence name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
        name = Emoji.replaceEmoji(name, callingUserTitle.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
        callingUserTitle.setText(name);
        callingUserTitle.setMaxLines(2);
        callingUserTitle.setEllipsize(TextUtils.TruncateAt.END);
        callingUserTitle.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        callingUserTitle.setTextColor(Color.WHITE);
        callingUserTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        callingUserTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        statusLayout.addView(callingUserTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 6));

        statusTextView = new VoIPStatusTextView(context, backgroundProvider);
        ViewCompat.setImportantForAccessibility(statusTextView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        statusLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusLayout.setClipChildren(false);
        statusLayout.setClipToPadding(false);
        statusLayout.setPadding(0, 0, 0, AndroidUtilities.dp(15));

        endCloseLayout = new EndCloseLayout(context);
        rateCallLayout = new RateCallLayout(context, backgroundProvider);
        endCloseLayout.setAlpha(0f);
        rateCallLayout.setVisibility(View.GONE);

        frameLayout.addView(callingUserPhotoViewMini, LayoutHelper.createFrame(204, 204, Gravity.CENTER_HORIZONTAL, 0, 93, 0, 0));
        frameLayout.addView(statusLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 135, 0, 0));
        frameLayout.addView(hideEmojiLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        frameLayout.addView(emojiRationalLayout, LayoutHelper.createFrame(304, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 118, 0, 0));
        frameLayout.addView(emojiLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        frameLayout.addView(endCloseLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52, Gravity.RIGHT, 0, 0, 0, 0));
        frameLayout.addView(rateCallLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 380, 0, 0));

        buttonsLayout = new VoIPButtonsLayout(context);
        bottomSpeakerBtn = new VoIpSwitchLayout(context, backgroundProvider);
        bottomVideoBtn = new VoIpSwitchLayout(context, backgroundProvider);
        bottomMuteBtn = new VoIpSwitchLayout(context, backgroundProvider);
        bottomEndCallBtn = new VoIPToggleButton(context) {
            @Override
            protected void dispatchSetPressed(boolean pressed) {
                super.dispatchSetPressed(pressed);
                setPressedBtn(pressed);
            }
        };
        int startDelay = 150;
        bottomSpeakerBtn.setTranslationY(AndroidUtilities.dp(100));
        bottomSpeakerBtn.setScaleX(0f);
        bottomSpeakerBtn.setScaleY(0f);
        bottomSpeakerBtn.animate().setStartDelay(startDelay).translationY(0).scaleY(1f).scaleX(1f).setDuration(250).start();
        bottomVideoBtn.setTranslationY(AndroidUtilities.dp(100));
        bottomVideoBtn.setScaleX(0f);
        bottomVideoBtn.setScaleY(0f);
        bottomVideoBtn.animate().setStartDelay(startDelay + 16).translationY(0).scaleY(1f).scaleX(1f).setDuration(250).start();
        bottomMuteBtn.setTranslationY(AndroidUtilities.dp(100));
        bottomMuteBtn.setScaleX(0f);
        bottomMuteBtn.setScaleY(0f);
        bottomMuteBtn.animate().setStartDelay(startDelay + 32).translationY(0).scaleY(1f).scaleX(1f).setDuration(250).start();
        bottomEndCallBtn.setTranslationY(AndroidUtilities.dp(100));
        bottomEndCallBtn.setScaleX(0f);
        bottomEndCallBtn.setScaleY(0f);
        bottomEndCallBtn.animate().setStartDelay(startDelay + 48).translationY(0).scaleY(1f).scaleX(1f).setDuration(250).start();

        buttonsLayout.addView(bottomSpeakerBtn);
        buttonsLayout.addView(bottomVideoBtn);
        buttonsLayout.addView(bottomMuteBtn);
        buttonsLayout.addView(bottomEndCallBtn);

        acceptDeclineView = new AcceptDeclineView(context);
        acceptDeclineView.setListener(new AcceptDeclineView.Listener() {

            @Override
            public void onAccept() {
                if (currentState == VoIPService.STATE_BUSY) {
                    Intent intent = new Intent(activity, VoIPService.class);
                    intent.putExtra("user_id", callingUser.id);
                    intent.putExtra("is_outgoing", true);
                    intent.putExtra("start_incall_activity", false);
                    intent.putExtra("video_call", isVideoCall);
                    intent.putExtra("can_video_call", isVideoCall);
                    intent.putExtra("account", currentAccount);
                    try {
                        activity.startService(intent);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                    } else {
                        if (VoIPService.getSharedInstance() != null) {
                            runAcceptCallAnimation(() -> {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().acceptIncomingCall();
                                    if (currentUserIsVideo) {
                                        VoIPService.getSharedInstance().requestVideoCall(false);
                                    }
                                }
                            });
                        }
                    }
                }
            }

            @Override
            public void onDecline() {
                if (currentState == VoIPService.STATE_BUSY) {
                    windowView.finish();
                } else {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().declineIncomingCall();
                    }
                }
            }
        });
        acceptDeclineView.setScaleX(1.15f);
        acceptDeclineView.setScaleY(1.15f);

        frameLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        int horizontalMargin = isTablet() ? 100 : 27;
        frameLayout.addView(acceptDeclineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 186, Gravity.BOTTOM, horizontalMargin, 0, horizontalMargin, 0));

        backIcon = new ImageView(context);
        backIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        backIcon.setImageResource(R.drawable.msg_call_minimize_shadow);
        backIcon.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        backIcon.setContentDescription(LocaleController.getString("Back", R.string.Back));
        frameLayout.addView(backIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));

        speakerPhoneIcon = new ImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setClassName(ToggleButton.class.getName());
                info.setCheckable(true);
                VoIPService service = VoIPService.getSharedInstance();
                if (service != null) {
                    info.setChecked(service.isSpeakerphoneOn());
                }
            }
        };
        speakerPhoneIcon.setContentDescription(LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker));
        speakerPhoneIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        speakerPhoneIcon.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        frameLayout.addView(speakerPhoneIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        speakerPhoneIcon.setAlpha(0f);
        speakerPhoneIcon.setOnClickListener(view -> {
            if (speakerPhoneIcon.getTag() == null) {
                return;
            }
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                startWaitingFoHideUi();
                final int selectedSpeaker;
                if (service.isBluetoothOn()) {
                    selectedSpeaker = 2;
                } else if (service.isSpeakerphoneOn()) {
                    selectedSpeaker = 0;
                } else {
                    selectedSpeaker = 1;
                }
                service.toggleSpeakerphoneOrShowRouteSheet(activity, false, selectedSpeaker);
            }
        });

        backIcon.setOnClickListener(view -> {
            if (!lockOnScreen) {
                onBackPressed();
            }
        });
        if (windowView.isLockOnScreen()) {
            backIcon.setVisibility(View.GONE);
        }

        notificationsLayout = new VoIPNotificationsLayout(context, backgroundProvider);
        notificationsLayout.setGravity(Gravity.BOTTOM);
        notificationsLayout.setOnViewsUpdated(() -> {
            previousState = currentState;
            updateViewState();
        });
        frameLayout.addView(notificationsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM, 16, 0, 16, 0));

        tapToVideoTooltip = new VoIpHintView(context, HintView2.DIRECTION_BOTTOM, backgroundProvider, true)
                .setMultilineText(true)
                .setTextAlign(Layout.Alignment.ALIGN_CENTER)
                .setDuration(-1)
                .setOnHiddenListener(this::startWaitingFoHideUi)
                .setHideByTouch(true)
                .setMaxWidth(320)
                .useScale(true)
                .setInnerPadding(10, 6, 10, 6)
                .setRounding(8);
        tapToVideoTooltip.setText(LocaleController.getString("TapToTurnCamera", R.string.TapToTurnCamera));
        frameLayout.addView(tapToVideoTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 19, 0, 19, 0));

        encryptionTooltip = new VoIpHintView(context, HintView2.DIRECTION_TOP, backgroundProvider, false)
                .setMultilineText(true)
                .setTextAlign(Layout.Alignment.ALIGN_CENTER)
                .setDuration(4000)
                .setHideByTouch(true)
                .setMaxWidth(320)
                .useScale(true)
                .setInnerPadding(10, 6, 10, 6)
                .setRounding(8);
        encryptionTooltip.setText(LocaleController.getString("VoipHintEncryptionKey", R.string.VoipHintEncryptionKey));
        frameLayout.addView(encryptionTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        updateViewState();

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (!isVideoCall) {
                isVideoCall = service.privateCall != null && service.privateCall.video;
            }
            initRenderers();
        }

        return frameLayout;
    }

    private void runAcceptCallAnimation(final Runnable after) {
        if (bottomVideoBtn.getVisibility() == View.VISIBLE) {
            int[] loc = new int[2];
            acceptDeclineView.getLocationOnScreen(loc);
            //callingUserPhotoView.switchToCallConnected(loc[0] + AndroidUtilities.dp(82), loc[1] + AndroidUtilities.dp(74));
            acceptDeclineView.stopAnimations();
            after.run();
            return;
        }

        bottomEndCallBtn.animate().cancel();
        bottomSpeakerBtn.animate().cancel();
        bottomMuteBtn.animate().cancel();
        bottomVideoBtn.animate().cancel();
        int[] loc = new int[2];
        acceptDeclineView.getLocationOnScreen(loc);
        acceptDeclineView.stopAnimations();
        //callingUserPhotoView.switchToCallConnected(loc[0] + AndroidUtilities.dp(82), loc[1] + AndroidUtilities.dp(74));
        bottomEndCallBtn.setData(R.drawable.calls_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("VoipEndCall2", R.string.VoipEndCall2), false, false);
        bottomSpeakerBtn.setType(VoIpSwitchLayout.Type.SPEAKER, false);
        bottomMuteBtn.setType(VoIpSwitchLayout.Type.MICRO, false);
        bottomVideoBtn.setType(VoIpSwitchLayout.Type.VIDEO, true);
        bottomEndCallBtn.setVisibility(View.VISIBLE);
        bottomSpeakerBtn.setVisibility(View.VISIBLE);
        bottomMuteBtn.setVisibility(View.VISIBLE);
        bottomVideoBtn.setVisibility(View.VISIBLE);
        bottomEndCallBtn.setAlpha(0f);
        bottomSpeakerBtn.setAlpha(0f);
        bottomMuteBtn.setAlpha(0f);
        bottomVideoBtn.setAlpha(0f);
        final ViewGroup.MarginLayoutParams lp = ((ViewGroup.MarginLayoutParams) acceptDeclineView.getLayoutParams());
        final int startMargin = lp.getMarginEnd();
        final int endMargin = AndroidUtilities.dp(52);
        final int endMarginFinal = AndroidUtilities.dp(24);
        final int transitionY = AndroidUtilities.dp(62);

        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator marginAnimator = ValueAnimator.ofFloat(0f, 1f);
        marginAnimator.addUpdateListener(valueAnimator -> {
            float percent = (float) valueAnimator.getAnimatedValue();
            float diff = transitionY * percent;
            acceptDeclineView.setTranslationY(diff);
            diff = startMargin - ((startMargin + endMarginFinal) * percent);
            lp.leftMargin = (int) diff;
            lp.rightMargin = (int) diff;
            acceptDeclineView.requestLayout();
        });
        final int totalDuration = 400;
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(acceptDeclineView, View.SCALE_X, acceptDeclineView.getScaleX(), 1f, 1f, 1f);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(acceptDeclineView, View.SCALE_Y, acceptDeclineView.getScaleY(), 1f, 1f, 1f);
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(acceptDeclineView, View.ALPHA, acceptDeclineView.getAlpha(), acceptDeclineView.getAlpha(), 0f, 0f);
        animatorSet.playTogether(marginAnimator, scaleXAnimator, scaleYAnimator, alphaAnimator);
        animatorSet.setDuration(totalDuration);
        animatorSet.setInterpolator(new LinearInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                after.run();
                acceptDeclineView.setScaleX(1.15f);
                acceptDeclineView.setScaleY(1.15f);
                final ViewGroup.MarginLayoutParams lp = ((ViewGroup.MarginLayoutParams) acceptDeclineView.getLayoutParams());
                lp.leftMargin = AndroidUtilities.dp(10);
                lp.rightMargin = AndroidUtilities.dp(10);
                acceptDeclineView.setVisibility(View.GONE);
            }
        });
        animatorSet.start();

        AndroidUtilities.runOnUIThread(() -> {
            int[] location = new int[2];
            acceptDeclineView.getLocationOnScreen(location);
            int rootX = location[0];
            int rootY = location[1];
            bottomSpeakerBtn.getLocationOnScreen(location);
            bottomSpeakerBtn.setTranslationX(rootX - location[0] + AndroidUtilities.dp(42));
            bottomSpeakerBtn.setTranslationY(rootY - location[1] + AndroidUtilities.dp(44));
            bottomMuteBtn.getLocationOnScreen(location);
            bottomMuteBtn.setTranslationX(rootX - location[0] + AndroidUtilities.dp(42));
            bottomMuteBtn.setTranslationY(rootY - location[1] + AndroidUtilities.dp(44));
            bottomVideoBtn.getLocationOnScreen(location);
            bottomVideoBtn.setTranslationX(rootX - location[0] + AndroidUtilities.dp(42));
            bottomVideoBtn.setTranslationY(rootY - location[1] + AndroidUtilities.dp(44));
            bottomEndCallBtn.getLocationOnScreen(location);
            bottomEndCallBtn.setTranslationX(rootX + acceptDeclineView.getWidth() - location[0] - AndroidUtilities.dp(49) - AndroidUtilities.dp(60));
            bottomEndCallBtn.setTranslationY(rootY - location[1] + AndroidUtilities.dp(44));

            bottomEndCallBtn.setAlpha(1f);
            bottomSpeakerBtn.setAlpha(1f);
            bottomMuteBtn.setAlpha(1f);
            bottomVideoBtn.setAlpha(1f);

            int halfDuration = totalDuration / 2;
            bottomEndCallBtn.animate().setStartDelay(0).translationY(0f).setInterpolator(new LinearInterpolator()).translationX(0f).setDuration(halfDuration).start();
            bottomSpeakerBtn.animate().setStartDelay(0).translationY(0f).setInterpolator(new LinearInterpolator()).translationX(0f).setDuration(halfDuration).start();
            bottomMuteBtn.animate().setStartDelay(0).translationY(0f).setInterpolator(new LinearInterpolator()).translationX(0f).setDuration(halfDuration).start();
            bottomVideoBtn.animate().setStartDelay(0).translationY(0f).setInterpolator(new LinearInterpolator()).translationX(0f).setDuration(halfDuration).start();
        }, totalDuration / 3);
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    private VoIPTextureView getFullscreenTextureView() {
        if (callingUserIsVideo) {
            return callingUserTextureView;
        }
        return currentUserTextureView;
    }

    private void finishZoom() {
        if (zoomStarted) {
            zoomStarted = false;
            zoomBackAnimator = ValueAnimator.ofFloat(1f, 0);

            float fromScale = pinchScale;
            float fromTranslateX = pinchTranslationX;
            float fromTranslateY = pinchTranslationY;
            zoomBackAnimator.addUpdateListener(valueAnimator -> {
                float v = (float) valueAnimator.getAnimatedValue();
                pinchScale = fromScale * v + 1f * (1f - v);
                pinchTranslationX = fromTranslateX * v;
                pinchTranslationY = fromTranslateY * v;
                fragmentView.invalidate();
            });

            zoomBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    zoomBackAnimator = null;
                    pinchScale = 1f;
                    pinchTranslationX = 0;
                    pinchTranslationY = 0;
                    fragmentView.invalidate();
                }
            });
            zoomBackAnimator.setDuration(TRANSITION_DURATION);
            zoomBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            zoomBackAnimator.start();
        }
        canZoomGesture = false;
        isInPinchToZoomTouchMode = false;
    }

    private void initRenderers() {
        currentUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        });
        callingUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        }, EglBase.CONFIG_PLAIN, new GlRectDrawer());

        callingUserMiniTextureRenderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), null);
    }

    public void switchToPip() {
        if (isFinished || !AndroidUtilities.checkInlinePermissions(activity) || instance == null) {
            return;
        }
        isFinished = true;
        if (VoIPService.getSharedInstance() != null) {
            int h = instance.windowView.getMeasuredHeight();
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_TRANSITION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }
        if (VoIPPiPView.getInstance() == null) {
            return;
        }

        speakerPhoneIcon.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        backIcon.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        emojiLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        statusLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        buttonsLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        bottomShadow.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        topShadow.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        callingUserMiniFloatingLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        notificationsLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

        VoIPPiPView.switchingToPip = true;
        switchingToPip = true;
        Animator animator = createPiPTransition(false);
        notificationsLocker.lock();
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                VoIPPiPView.getInstance().windowView.setAlpha(1f);
                AndroidUtilities.runOnUIThread(() -> {
                    notificationsLocker.unlock();
                    VoIPPiPView.getInstance().onTransitionEnd();
                    currentUserCameraFloatingLayout.setCornerRadius(-1f);
                    callingUserTextureView.renderer.release();
                    currentUserTextureView.renderer.release();
                    callingUserMiniTextureRenderer.release();
                    destroy();
                    windowView.finishImmediate();
                    VoIPPiPView.switchingToPip = false;
                    switchingToPip = false;
                    instance = null;
                }, 200);
            }
        });
        animator.setDuration(350);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void startTransitionFromPiP() {
        enterFromPiP = true;
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE) {
            callingUserTextureView.setStub(VoIPPiPView.getInstance().callingUserTextureView);
            currentUserTextureView.setStub(VoIPPiPView.getInstance().currentUserTextureView);
        }
        windowView.setAlpha(0f);
        updateViewState();
        switchingToPip = true;
        VoIPPiPView.switchingToPip = true;
        VoIPPiPView.prepareForTransition();
        notificationsLocker.lock();
        AndroidUtilities.runOnUIThread(() -> {
            windowView.setAlpha(1f);
            windowView.invalidate();
            Animator animator = createPiPTransition(true);
            backIcon.setAlpha(0f);
            emojiLayout.setAlpha(0f);
            statusLayout.setAlpha(0f);
            buttonsLayout.setAlpha(0f);
            bottomShadow.setAlpha(0f);
            topShadow.setAlpha(0f);
            speakerPhoneIcon.setAlpha(0f);
            notificationsLayout.setAlpha(0f);

            currentUserCameraFloatingLayout.switchingToPip = true;
            AndroidUtilities.runOnUIThread(() -> {
                VoIPPiPView.switchingToPip = false;
                VoIPPiPView.finish();

                speakerPhoneIcon.animate().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                backIcon.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                emojiLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                statusLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                buttonsLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                bottomShadow.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                topShadow.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                notificationsLayout.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        notificationsLocker.unlock();
                        currentUserCameraFloatingLayout.setCornerRadius(-1f);
                        switchingToPip = false;
                        currentUserCameraFloatingLayout.switchingToPip = false;
                        previousState = currentState;
                        updateViewState();
                    }
                });
                animator.setDuration(350);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.start();
            }, 32);
        }, 32);

    }

    public Animator createPiPTransition(boolean enter) {
        currentUserCameraFloatingLayout.animate().cancel();
        float toX = VoIPPiPView.getInstance().windowLayoutParams.x + VoIPPiPView.getInstance().xOffset;
        float toY = VoIPPiPView.getInstance().windowLayoutParams.y + VoIPPiPView.getInstance().yOffset;

        float cameraFromX = currentUserCameraFloatingLayout.getX();
        float cameraFromY = currentUserCameraFloatingLayout.getY();
        float cameraFromScale = currentUserCameraFloatingLayout.getScaleX();
        boolean animateCamera = true;

        float callingUserFromX = 0;
        float callingUserFromY = 0;
        float callingUserFromScale = 1f;
        float callingUserToScale, callingUserToX, callingUserToY;
        float cameraToScale, cameraToX, cameraToY;

        float pipScale = VoIPPiPView.isExpanding() ? 0.4f : 0.25f;
        callingUserToScale = pipScale;
        callingUserToX = toX - (callingUserTextureView.getMeasuredWidth() - callingUserTextureView.getMeasuredWidth() * callingUserToScale) / 2f;
        callingUserToY = toY - (callingUserTextureView.getMeasuredHeight() - callingUserTextureView.getMeasuredHeight() * callingUserToScale) / 2f;
        if (callingUserIsVideo) {
            int currentW = currentUserCameraFloatingLayout.getMeasuredWidth();
            if (currentUserIsVideo && currentW != 0) {
                cameraToScale = (windowView.getMeasuredWidth() / (float) currentW) * pipScale * 0.4f;
                cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f +
                        VoIPPiPView.getInstance().parentWidth * pipScale - VoIPPiPView.getInstance().parentWidth * pipScale * 0.4f - AndroidUtilities.dp(4);
                cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f +
                        VoIPPiPView.getInstance().parentHeight * pipScale - VoIPPiPView.getInstance().parentHeight * pipScale * 0.4f - AndroidUtilities.dp(4);
            } else {
                cameraToScale = 0;
                cameraToX = 1f;
                cameraToY = 1f;
                animateCamera = false;
            }
        } else {
            cameraToScale = pipScale;
            cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f;
            cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f;
        }

        float cameraCornerRadiusFrom = callingUserIsVideo ? AndroidUtilities.dp(4) : 0;
        float cameraCornerRadiusTo = AndroidUtilities.dp(4) * 1f / cameraToScale;

        float fromCameraAlpha = 1f;
        float toCameraAlpha = 1f;
        if (callingUserIsVideo) {
            fromCameraAlpha = VoIPPiPView.isExpanding() ? 1f : 0f;
        }

        if (enter) {
            if (animateCamera) {
                currentUserCameraFloatingLayout.setScaleX(cameraToScale);
                currentUserCameraFloatingLayout.setScaleY(cameraToScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraToX);
                currentUserCameraFloatingLayout.setTranslationY(cameraToY);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusTo);
                currentUserCameraFloatingLayout.setAlpha(fromCameraAlpha);
            }
            callingUserTextureView.setScaleX(callingUserToScale);
            callingUserTextureView.setScaleY(callingUserToScale);
            callingUserTextureView.setTranslationX(callingUserToX);
            callingUserTextureView.setTranslationY(callingUserToY);
            callingUserTextureView.setRoundCorners(AndroidUtilities.dp(6) * 1f / callingUserToScale);
        }
        ValueAnimator animator = ValueAnimator.ofFloat(enter ? 1f : 0, enter ? 0 : 1f);

        enterTransitionProgress = enter ? 0f : 1f;
        updateSystemBarColors();

        boolean finalAnimateCamera = animateCamera;
        float finalFromCameraAlpha = fromCameraAlpha;
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            enterTransitionProgress = 1f - v;
            updateSystemBarColors();

            if (finalAnimateCamera) {
                float cameraScale = cameraFromScale * (1f - v) + cameraToScale * v;
                currentUserCameraFloatingLayout.setScaleX(cameraScale);
                currentUserCameraFloatingLayout.setScaleY(cameraScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraFromX * (1f - v) + cameraToX * v);
                currentUserCameraFloatingLayout.setTranslationY(cameraFromY * (1f - v) + cameraToY * v);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusFrom * (1f - v) + cameraCornerRadiusTo * v);
                currentUserCameraFloatingLayout.setAlpha(toCameraAlpha * (1f - v) + finalFromCameraAlpha * v);
            }

            float callingUserScale = callingUserFromScale * (1f - v) + callingUserToScale * v;
            callingUserTextureView.setScaleX(callingUserScale);
            callingUserTextureView.setScaleY(callingUserScale);
            float tx = callingUserFromX * (1f - v) + callingUserToX * v;
            float ty = callingUserFromY * (1f - v) + callingUserToY * v;

            callingUserTextureView.setTranslationX(tx);
            callingUserTextureView.setTranslationY(ty);
            callingUserTextureView.setRoundCorners(v * AndroidUtilities.dp(4) * 1 / callingUserScale);
            if (!currentUserCameraFloatingLayout.measuredAsFloatingMode) {
                currentUserTextureView.setScreenshareMiniProgress(v, false);
            }
            windowView.invalidate();
        });
        return animator;
    }

    private void expandEmoji(boolean expanded) {
        if (!emojiLoaded || emojiExpanded == expanded || !uiVisible) {
            return;
        }
        emojiExpanded = expanded;
        if (expanded) {
            if (SharedConfig.callEncryptionHintDisplayedCount < 2) {
                SharedConfig.incrementCallEncryptionHintDisplayed(2);
            }
            encryptionTooltip.hide();
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;

            if (callingUserPhotoViewMini.getVisibility() == View.VISIBLE) {
                callingUserPhotoViewMini.animate().setStartDelay(0).translationY(AndroidUtilities.dp(48)).scaleY(0.1f).scaleX(0.1f).alpha(0f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }

            hideEmojiLayout.animate().setListener(null).cancel();
            hideEmojiLayout.setVisibility(View.VISIBLE);
            hideEmojiLayout.setAlpha(0f);
            hideEmojiLayout.setScaleX(0.3f);
            hideEmojiLayout.setScaleY(0.3f);
            hideEmojiLayout.animate().alpha(1f).scaleY(1f).scaleX(1f).setDuration(340).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();

            emojiLayout.animate().scaleX(1.72f).scaleY(1.72f)
                    .translationY(AndroidUtilities.dp(140))
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .setDuration(400)
                    .start();

            emojiRationalLayout.animate().setListener(null).cancel();
            emojiRationalLayout.setVisibility(View.VISIBLE);
            emojiRationalLayout.setTranslationY(-AndroidUtilities.dp(120));
            emojiRationalLayout.setScaleX(0.7f);
            emojiRationalLayout.setScaleY(0.7f);
            emojiRationalLayout.setAlpha(0f);
            emojiRationalLayout.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(400).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    for (BackupImageView emojiView : emojiViews) {
                        if (emojiView.animatedEmojiDrawable != null && emojiView.animatedEmojiDrawable.getImageReceiver() != null) {
                            emojiView.animatedEmojiDrawable.getImageReceiver().setAllowStartAnimation(true);
                            emojiView.animatedEmojiDrawable.getImageReceiver().startAnimation();
                        }
                    }
                }
            }).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            if (callingUserPhotoViewMini.getVisibility() == View.VISIBLE) {
                callingUserPhotoViewMini.animate().setStartDelay(50).translationY(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }

            hideEmojiLayout.animate().setListener(null).cancel();
            hideEmojiLayout.animate().alpha(0f).scaleY(0.3f).scaleX(0.3f).setDuration(230).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new HideViewAfterAnimation(hideEmojiLayout)).start();

            emojiLayout.animate().scaleX(1f).scaleY(1f)
                    .translationY(0)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .setDuration(280)
                    .start();

            emojiRationalLayout.animate().setListener(null).cancel();
            emojiRationalLayout.animate().alpha(0f).scaleY(0.7f).scaleX(0.7f).translationY(-AndroidUtilities.dp(120)).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    startWaitingFoHideUi();
                    for (BackupImageView emojiView : emojiViews) {
                        if (emojiView.animatedEmojiDrawable != null && emojiView.animatedEmojiDrawable.getImageReceiver() != null) {
                            emojiView.animatedEmojiDrawable.getImageReceiver().setAllowStartAnimation(false);
                            emojiView.animatedEmojiDrawable.getImageReceiver().stopAnimation();
                        }
                    }
                    emojiRationalLayout.setVisibility(View.GONE);
                }
            }).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        previousState = currentState;
        updateViewState();
    }

    private void startWaitingFoHideUi() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
            if (canHideUI && uiVisible) {
                AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
                hideUiRunnableWaiting = true;
            }
        }
    }

    private void updateViewState() {
        if (isFinished || switchingToPip) {
            return;
        }
        lockOnScreen = false;
        boolean animated = previousState != -1;
        boolean showAcceptDeclineView = false;
        boolean showTimer = false;
        boolean showReconnecting = false;
        int statusLayoutOffset = 0;
        final VoIPService service = VoIPService.getSharedInstance();

        switch (currentState) {
            case VoIPService.STATE_WAITING_INCOMING:
                showAcceptDeclineView = true;
                lockOnScreen = false;
                acceptDeclineView.setRetryMod(false);
                if (service != null && service.privateCall.video) {
                    statusTextView.setText(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding), false, animated);
                    acceptDeclineView.setTranslationY(-AndroidUtilities.dp(60));
                } else {
                    statusTextView.setText(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding), false, animated);
                    acceptDeclineView.setTranslationY(0);
                }
                break;
            case VoIPService.STATE_WAIT_INIT:
            case VoIPService.STATE_WAIT_INIT_ACK:
                statusTextView.setText(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true, animated);
                break;
            case VoIPService.STATE_EXCHANGING_KEYS:
                if (previousState != VoIPService.STATE_EXCHANGING_KEYS) {
                    statusTextView.setText(LocaleController.getString("VoipExchangingKeys", R.string.VoipExchangingKeys), true, animated);
                }
                break;
            case VoIPService.STATE_WAITING:
                statusTextView.setText(LocaleController.getString("VoipWaiting", R.string.VoipWaiting), true, animated);
                break;
            case VoIPService.STATE_RINGING:
                if (previousState != VoIPService.STATE_RINGING) {
                    statusTextView.setText(LocaleController.getString("VoipRinging", R.string.VoipRinging), true, animated);
                }
                break;
            case VoIPService.STATE_REQUESTING:
                statusTextView.setText(LocaleController.getString("VoipRequesting", R.string.VoipRequesting), true, animated);
                break;
            case VoIPService.STATE_HANGING_UP:
                break;
            case VoIPService.STATE_BUSY:
                showAcceptDeclineView = true;
                statusTextView.setText(LocaleController.getString("VoipBusy", R.string.VoipBusy), false, animated);
                acceptDeclineView.setRetryMod(true);
                currentUserIsVideo = false;
                callingUserIsVideo = false;
                break;
            case VoIPService.STATE_ESTABLISHED:
            case VoIPService.STATE_RECONNECTING:
                updateKeyView(animated);
                if (currentState == VoIPService.STATE_RECONNECTING) {
                    showReconnecting = wasEstablished;
                    if (!wasEstablished && previousState != VoIPService.STATE_RECONNECTING) {
                        statusTextView.setText(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true, animated);
                    }
                } else {
                    wasEstablished = true;
                    showTimer = true;
                }
                break;
            case VoIPService.STATE_ENDED:
                boolean hasRate = service != null && service.hasRate();
                currentUserTextureView.saveCameraLastBitmap();
                if (hasRate && !isFinished) {
                    final boolean uiVisibleLocal = uiVisible;
                    if (uiVisibleLocal) {
                        int[] locEndCall = new int[2];
                        int w = AndroidUtilities.displaySize.x;
                        bottomEndCallBtn.getLocationOnScreen(locEndCall);
                        int marginCloseBtn = w - locEndCall[0] - (((bottomEndCallBtn.getMeasuredWidth() - AndroidUtilities.dp(52)) / 2)) - AndroidUtilities.dp(52);
                        ViewGroup.MarginLayoutParams lpCloseBtn = (ViewGroup.MarginLayoutParams) endCloseLayout.getLayoutParams();
                        lpCloseBtn.rightMargin = marginCloseBtn;
                        lpCloseBtn.leftMargin = marginCloseBtn;
                        endCloseLayout.setTranslationY(locEndCall[1]);
                        endCloseLayout.setAlpha(1f);
                        endCloseLayout.setLayoutParams(lpCloseBtn);
                        buttonsLayout.animate().alpha(0f).setDuration(80).start();
                        AndroidUtilities.runOnUIThread(() -> endCloseLayout.switchToClose(v -> {
                            AndroidUtilities.runOnUIThread(() -> windowView.finish());
                            if (selectedRating > 0) {
                                service.sendCallRating(selectedRating);
                            }
                        }, true), 2);
                    } else {
                        buttonsLayout.setVisibility(View.GONE);
                        FrameLayout.LayoutParams lpCloseBtn = (FrameLayout.LayoutParams) endCloseLayout.getLayoutParams();
                        lpCloseBtn.rightMargin = AndroidUtilities.dp(18);
                        lpCloseBtn.leftMargin = AndroidUtilities.dp(18);
                        lpCloseBtn.bottomMargin = AndroidUtilities.dp(36);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                            lpCloseBtn.bottomMargin += lastInsets.getSystemWindowInsetBottom();
                        }
                        lpCloseBtn.gravity = Gravity.BOTTOM;
                        endCloseLayout.setLayoutParams(lpCloseBtn);
                        endCloseLayout.animate().alpha(1f).setDuration(250).start();
                        endCloseLayout.switchToClose(v -> {
                            AndroidUtilities.runOnUIThread(() -> windowView.finish());
                            if (selectedRating > 0) {
                                service.sendCallRating(selectedRating);
                            }
                        }, false);
                    }

                    rateCallLayout.setVisibility(View.VISIBLE);
                    rateCallLayout.show(count -> selectedRating = count);
                    if (emojiExpanded) {
                        emojiExpanded = false;
                        hideEmojiLayout.animate().alpha(0f).scaleY(0.3f).scaleX(0.3f).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).setListener(new HideViewAfterAnimation(hideEmojiLayout)).start();
                        emojiLayout.animate().scaleX(1f).scaleY(1f).translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250).start();
                        emojiRationalLayout.animate().alpha(0f).scaleY(0.7f).scaleX(0.7f).translationY(-AndroidUtilities.dp(120)).setListener(new HideViewAfterAnimation(hideEmojiLayout)).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    }
                    for (BackupImageView emojiView : emojiViews) {
                        emojiView.animate().alpha(0f).scaleX(0f).scaleY(0f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250).start();
                    }
                    callingUserTitle.animate().alpha(0f).setDuration(70).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            callingUserTitle.setText(LocaleController.getString("VoipCallEnded", R.string.VoipCallEnded));
                            callingUserTitle.animate().alpha(1f).setDuration(70).setListener(null).start();
                        }
                    }).start();
                    speakerPhoneIcon.animate().alpha(0f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250).start();
                    speakerPhoneIcon.setVisibility(View.GONE);
                    statusTextView.showReconnect(false, true);
                    statusTextView.showBadConnection(false, true);
                    statusTextView.setDrawCallIcon();
                    callingUserPhotoViewMini.onNeedRating();
                    updateButtons(true);
                    bottomEndCallBtn.setVisibility(View.INVISIBLE);
                    callingUserMiniFloatingLayout.setAlpha(0f);
                    callingUserMiniFloatingLayout.setVisibility(View.GONE);
                    currentUserCameraFloatingLayout.setAlpha(0f);
                    currentUserCameraFloatingLayout.setVisibility(View.GONE);
                    if (previewDialog != null) {
                        previewDialog.dismiss(false, false);
                    }
                    notificationsLayout.animate().alpha(0f).setDuration(250).start();
                } else {
                    AndroidUtilities.runOnUIThread(() -> windowView.finish(), 200);
                }
                break;
            case VoIPService.STATE_FAILED:
                statusTextView.setText(LocaleController.getString("VoipFailed", R.string.VoipFailed), false, animated);
                final VoIPService voipService = VoIPService.getSharedInstance();
                final String lastError = voipService != null ? voipService.getLastError() : Instance.ERROR_UNKNOWN;
                if (!TextUtils.equals(lastError, Instance.ERROR_UNKNOWN)) {
                    if (TextUtils.equals(lastError, Instance.ERROR_INCOMPATIBLE)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("VoipPeerIncompatible", R.string.VoipPeerIncompatible, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PEER_OUTDATED)) {
                        if (isVideoCall) {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerVideoOutdated", R.string.VoipPeerVideoOutdated, name);
                            boolean[] callAgain = new boolean[1];
                            AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                                    .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                                    .setMessage(AndroidUtilities.replaceTags(message))
                                    .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> windowView.finish())
                                    .setPositiveButton(LocaleController.getString("VoipPeerVideoOutdatedMakeVoice", R.string.VoipPeerVideoOutdatedMakeVoice), (dialogInterface, i) -> {
                                        callAgain[0] = true;
                                        currentState = VoIPService.STATE_BUSY;
                                        Intent intent = new Intent(activity, VoIPService.class);
                                        intent.putExtra("user_id", callingUser.id);
                                        intent.putExtra("is_outgoing", true);
                                        intent.putExtra("start_incall_activity", false);
                                        intent.putExtra("video_call", false);
                                        intent.putExtra("can_video_call", false);
                                        intent.putExtra("account", currentAccount);
                                        try {
                                            activity.startService(intent);
                                        } catch (Throwable e) {
                                            FileLog.e(e);
                                        }
                                    })
                                    .show();
                            dlg.setCanceledOnTouchOutside(true);
                            dlg.setOnDismissListener(dialog -> {
                                if (!callAgain[0]) {
                                    windowView.finish();
                                }
                            });
                        } else {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerOutdated", R.string.VoipPeerOutdated, name);
                            showErrorDialog(AndroidUtilities.replaceTags(message));
                        }
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PRIVACY)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_AUDIO_IO)) {
                        showErrorDialog("Error initializing audio hardware");
                    } else if (TextUtils.equals(lastError, Instance.ERROR_LOCALIZED)) {
                        windowView.finish();
                    } else if (TextUtils.equals(lastError, Instance.ERROR_CONNECTION_SERVICE)) {
                        showErrorDialog(LocaleController.getString("VoipErrorUnknown", R.string.VoipErrorUnknown));
                    } else {
                        AndroidUtilities.runOnUIThread(() -> windowView.finish(), 1000);
                    }
                } else {
                    AndroidUtilities.runOnUIThread(() -> windowView.finish(), 1000);
                }
                break;
        }
        if (previewDialog != null) {
            return;
        }

        boolean wasVideo = callingUserIsVideo || currentUserIsVideo;
        if (service != null) {
            callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE;
            currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;
            if (currentUserIsVideo && !isVideoCall) {
                isVideoCall = true;
            }
        }

        if (animated) {
            currentUserCameraFloatingLayout.saveRelativePosition();
            callingUserMiniFloatingLayout.saveRelativePosition();
        }

        if (callingUserIsVideo) {
            if (!switchingToPip) {
                gradientLayout.setAlpha(1f);
            }
            if (animated) {
                callingUserTextureView.animate().alpha(1f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(1f);
            }
            if (!callingUserTextureView.renderer.isFirstFrameRendered() && !enterFromPiP) {
                callingUserIsVideo = false;
            }
        }

        if (currentUserIsVideo || callingUserIsVideo) {
            gradientLayout.setVisibility(View.INVISIBLE);
        } else {
            gradientLayout.setVisibility(View.VISIBLE);
            if (animated) {
                callingUserTextureView.animate().alpha(0f).setDuration(250).start();
            } else {
                callingUserTextureView.animate().cancel();
                callingUserTextureView.setAlpha(0f);
            }
        }

        if (!currentUserIsVideo || !callingUserIsVideo) {
            cameraForceExpanded = false;
        }

        boolean showCallingUserVideoMini = currentUserIsVideo && cameraForceExpanded;

        showCallingUserAvatarMini(animated, wasVideo);
        statusLayoutOffset = callingUserPhotoViewMini.getTag() == null ? 0 : AndroidUtilities.dp(135) + AndroidUtilities.dp(12);
        showAcceptDeclineView(showAcceptDeclineView, animated);
        windowView.setLockOnScreen(lockOnScreen || deviceIsLocked);
        canHideUI = (currentState == VoIPService.STATE_ESTABLISHED) && (currentUserIsVideo || callingUserIsVideo);
        if (!canHideUI && !uiVisible) {
            showUi(true);
        }

        if (uiVisible && canHideUI && !hideUiRunnableWaiting && service != null) {
            AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
            hideUiRunnableWaiting = true;
        }

        if (animated) {
            if (currentState == VoIPService.STATE_ENDED) {
                backIcon.animate().alpha(0f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(250).start();
            } else {
                if (lockOnScreen || !uiVisible) {
                    if (backIcon.getVisibility() != View.VISIBLE) {
                        backIcon.setVisibility(View.VISIBLE);
                        backIcon.setAlpha(0f);
                    }
                    backIcon.animate().alpha(0f).start();
                } else {
                    backIcon.animate().alpha(1f).start();
                }
            }
            notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        } else {
            if (!lockOnScreen) {
                backIcon.setVisibility(View.VISIBLE);
            }
            backIcon.setAlpha(lockOnScreen ? 0 : 1f);
            notificationsLayout.setTranslationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0));
        }

        if (currentState != VoIPService.STATE_HANGING_UP && currentState != VoIPService.STATE_ENDED) {
            updateButtons(animated);
        }

        if (showTimer) {
            statusTextView.showTimer(animated);
        }

        statusTextView.showReconnect(showReconnecting, animated);

        if (callingUserPhotoViewMini.getVisibility() == View.VISIBLE && emojiExpanded) {
            statusLayoutOffset += AndroidUtilities.dp(24);
            Layout layout = emojiRationalTextView.getLayout();
            if (layout != null) {
                int lines = layout.getLineCount();
                if (lines > 2) {
                    statusLayoutOffset += AndroidUtilities.dp(20) * (lines - 2);
                }
            }
        }

        if (currentState == VoIPService.STATE_ENDED && (!currentUserIsVideo && !callingUserIsVideo)) {
            statusLayoutOffset -= AndroidUtilities.dp(24);
        }

        if (currentUserIsVideo || callingUserIsVideo) {
            statusLayoutOffset -= AndroidUtilities.dp(60);
        }

        if (animated) {
            if (emojiExpanded && (currentUserIsVideo || callingUserIsVideo)) {
                statusLayout.animate().setStartDelay(0).alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            } else {
                statusLayout.animate().setStartDelay(250).alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
            if (statusLayoutOffset != statusLayoutAnimateToOffset) {
                statusLayout.animate().setStartDelay(currentState == VoIPService.STATE_ENDED ? 250 : 0).translationY(statusLayoutOffset).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        } else {
            statusLayout.setTranslationY(statusLayoutOffset);
        }
        statusLayoutAnimateToOffset = statusLayoutOffset;
        boolean isScreencast = service != null && service.isScreencast();
        canSwitchToPip = (currentState != VoIPService.STATE_ENDED && currentState != VoIPService.STATE_BUSY) && ((currentUserIsVideo && !isScreencast) || callingUserIsVideo);

        int floatingViewsOffset;
        if (service != null) {
            if (currentUserIsVideo) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
            }
            currentUserTextureView.setIsScreencast(service.isScreencast());
            currentUserTextureView.renderer.setMirror(service.isFrontFaceCamera());
            service.setSinks(currentUserIsVideo && !service.isScreencast() ? currentUserTextureView.renderer : null, showCallingUserVideoMini ? callingUserMiniTextureRenderer : callingUserTextureView.renderer);

            if (animated) {
                notificationsLayout.beforeLayoutChanges();
            }
            if (service.isMicMute()) {
                notificationsLayout.addNotification(R.drawable.calls_mute_mini, LocaleController.getString(R.string.VoipMyMicrophoneState), "self-muted", animated);
            } else {
                notificationsLayout.removeNotification("self-muted");
            }
            if ((currentUserIsVideo || callingUserIsVideo) && (currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING) && service.getCallDuration() > 500) {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(R.drawable.calls_mute_mini, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, notificationsLayout.ellipsize(UserObject.getFirstName(callingUser))), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                if (service.getRemoteVideoState() == Instance.VIDEO_STATE_INACTIVE) {
                    notificationsLayout.addNotification(R.drawable.calls_camera_mini, LocaleController.formatString("VoipUserCameraIsOff", R.string.VoipUserCameraIsOff, notificationsLayout.ellipsize(UserObject.getFirstName(callingUser))), "video", animated);
                } else {
                    notificationsLayout.removeNotification("video");
                }
            } else {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(R.drawable.calls_mute_mini, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, notificationsLayout.ellipsize(UserObject.getFirstName(callingUser))), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                notificationsLayout.removeNotification("video");
            }

            if (notificationsLayout.getChildCount() == 0 && callingUserIsVideo && service.privateCall != null && !service.privateCall.video && !service.sharedUIParams.tapToVideoTooltipWasShowed) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
                tapToVideoTooltip.setTranslationY(-(fragmentView.getMeasuredHeight() - buttonsLayout.getY() + AndroidUtilities.dp(6)));
                tapToVideoTooltip.setJointPx(0, buttonsLayout.getX() + bottomVideoBtn.getX() + AndroidUtilities.dp(14));
                tapToVideoTooltip.show();
            } else if (notificationsLayout.getChildCount() != 0) {
                tapToVideoTooltip.hide();
            }

            if (animated) {
                notificationsLayout.animateLayoutChanges();
            }
        }

        floatingViewsOffset = notificationsLayout.getChildsHight();

        callingUserMiniFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        callingUserMiniFloatingLayout.setUiVisible(uiVisible);

        if (currentUserIsVideo) {
            if (!callingUserIsVideo || cameraForceExpanded) {
                showFloatingLayout(STATE_FULLSCREEN, animated);
            } else {
                showFloatingLayout(STATE_FLOATING, animated);
            }
        } else {
            showFloatingLayout(STATE_GONE, animated);
        }

        if (showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() == null) {
            callingUserMiniFloatingLayout.setIsActive(true);
            if (callingUserMiniFloatingLayout.getVisibility() != View.VISIBLE) {
                callingUserMiniFloatingLayout.setVisibility(View.VISIBLE);
                callingUserMiniFloatingLayout.setAlpha(0f);
                callingUserMiniFloatingLayout.setScaleX(0.5f);
                callingUserMiniFloatingLayout.setScaleY(0.5f);
            }
            callingUserMiniFloatingLayout.animate().setListener(null).cancel();
            callingUserMiniFloatingLayout.isAppearing = true;
            callingUserMiniFloatingLayout.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setStartDelay(150)
                    .withEndAction(() -> {
                        callingUserMiniFloatingLayout.isAppearing = false;
                        callingUserMiniFloatingLayout.invalidate();
                    }).start();
            callingUserMiniFloatingLayout.setTag(1);
        } else if (!showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() != null) {
            callingUserMiniFloatingLayout.setIsActive(false);
            callingUserMiniFloatingLayout.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (callingUserMiniFloatingLayout.getTag() == null) {
                        callingUserMiniFloatingLayout.setVisibility(View.GONE);
                    }
                }
            }).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            callingUserMiniFloatingLayout.setTag(null);
        }

        currentUserCameraFloatingLayout.restoreRelativePosition();
        callingUserMiniFloatingLayout.restoreRelativePosition();

        updateSpeakerPhoneIcon();

        if (currentState == VoIPService.STATE_ESTABLISHED) {
            callingUserPhotoViewMini.onConnected();
            if (!gradientLayout.isConnectedCalled()) {
                int[] loc = new int[2];
                callingUserPhotoViewMini.getLocationOnScreen(loc);
                boolean animatedSwitch = previousState != -1;
                gradientLayout.switchToCallConnected(loc[0] + AndroidUtilities.dp(106), loc[1] + AndroidUtilities.dp(106), animatedSwitch);
            }
        }
        boolean isVideoMode = currentUserIsVideo || callingUserIsVideo;
        voIpSnowView.setState(isVideoMode);
        backgroundProvider.setHasVideo(isVideoMode);

        if (callingUserIsVideo && !wasVideo && isNearEar) {
            isNearEar = false;
            if (service != null) {
                service.playStartRecordSound();
            }
        }

        if (!isVideoMode) {
            if (topShadow.getVisibility() != View.INVISIBLE) {
                topShadow.setVisibility(View.INVISIBLE);
                bottomShadow.setVisibility(View.INVISIBLE);
            }
        } else {
            if (topShadow.getVisibility() != View.VISIBLE) {
                topShadow.setVisibility(View.VISIBLE);
                bottomShadow.setVisibility(View.VISIBLE);
            }
        }
        AndroidUtilities.cancelRunOnUIThread(stopAnimatingBgRunnable);
        if (currentState == VoIPService.STATE_ESTABLISHED) {
            AndroidUtilities.runOnUIThread(stopAnimatingBgRunnable, 10000);
        }
    }

    private void showUi(boolean show) {
        if (uiVisibilityAnimator != null) {
            uiVisibilityAnimator.cancel();
        }

        int notificationsLayoutStartDelay = 0;
        if (!show && uiVisible) {
            notificationsLayoutStartDelay = 150;
            speakerPhoneIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(10)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(10)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiLayout.animate().alpha(0).translationY(-AndroidUtilities.dp(10)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            callingUserTitle.animate().alpha(0).setDuration(150).translationY(-AndroidUtilities.dp(10)).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusTextView.animate().alpha(0).setDuration(150).translationY(-AndroidUtilities.dp(10)).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.animate().alpha(0).translationY(AndroidUtilities.dp(10)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            bottomShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            topShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 0);
            uiVisibilityAnimator.addUpdateListener(statusbarAnimatorListener);
            uiVisibilityAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
            uiVisibilityAnimator.start();
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
            buttonsLayout.setEnabled(false);
            encryptionTooltip.hide();
        } else if (show && !uiVisible) {
            tapToVideoTooltip.hide();
            encryptionTooltip.hide();
            callingUserTitle.animate().alpha(1f).setDuration(150).translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusTextView.animate().alpha(1f).setDuration(150).translationY(0).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            speakerPhoneIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiLayout.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            bottomShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            topShadow.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 1f);
            uiVisibilityAnimator.addUpdateListener(statusbarAnimatorListener);
            uiVisibilityAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
            uiVisibilityAnimator.start();
            buttonsLayout.setEnabled(true);
        }

        uiVisible = show;
        windowView.requestFullscreen(!show);
        notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0)).setDuration(150).setStartDelay(notificationsLayoutStartDelay).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    private void showFloatingLayout(int state, boolean animated) {
        if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) {
            currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        }
        if (!animated && cameraShowingAnimator != null) {
            cameraShowingAnimator.removeAllListeners();
            cameraShowingAnimator.cancel();
        }
        if (state == STATE_GONE) {
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() != STATE_GONE) {
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, currentUserCameraFloatingLayout.getAlpha(), 0)
                    );
                    if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_FLOATING) {
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, currentUserCameraFloatingLayout.getScaleX(), 0.7f),
                                ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, currentUserCameraFloatingLayout.getScaleX(), 0.7f)
                        );
                    }
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            currentUserCameraFloatingLayout.setTranslationX(0);
                            currentUserCameraFloatingLayout.setTranslationY(0);
                            currentUserCameraFloatingLayout.setScaleY(1f);
                            currentUserCameraFloatingLayout.setScaleX(1f);
                            currentUserCameraFloatingLayout.setVisibility(View.GONE);
                        }
                    });
                    cameraShowingAnimator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
                    cameraShowingAnimator.setStartDelay(50);
                    cameraShowingAnimator.start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.GONE);
            }
        } else {
            boolean switchToFloatAnimated = animated;
            if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                switchToFloatAnimated = false;
            }
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                    if (currentUserCameraFloatingLayout.getVisibility() == View.GONE) {
                        currentUserCameraFloatingLayout.setAlpha(0f);
                        currentUserCameraFloatingLayout.setScaleX(0.7f);
                        currentUserCameraFloatingLayout.setScaleY(0.7f);
                        currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
                    }
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, 0.0f, 1f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, 0.7f, 1f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, 0.7f, 1f)
                    );
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.setDuration(150).start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
            }
            if ((currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) && currentUserCameraFloatingLayout.relativePositionToSetX < 0) {
                currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
                currentUserCameraIsFullscreen = true;
            }
            currentUserCameraFloatingLayout.setFloatingMode(state == STATE_FLOATING, switchToFloatAnimated);
            currentUserCameraIsFullscreen = state != STATE_FLOATING;
        }
        currentUserCameraFloatingLayout.setTag(state);
    }

    private void showCallingUserAvatarMini(boolean animated, boolean wasVideo) {
        boolean noVideo = !currentUserIsVideo && !callingUserIsVideo;
        if (animated) {
            if (noVideo && callingUserPhotoViewMini.getTag() == null) {
                callingUserPhotoViewMini.animate().setListener(null).cancel();
                callingUserPhotoViewMini.setVisibility(View.VISIBLE);
                if (!emojiExpanded) {
                    if (wasVideo) {
                        callingUserPhotoViewMini.setAlpha(0f);
                        callingUserPhotoViewMini.animate().alpha(1f).translationY(0).scaleY(1f).scaleX(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    } else {
                        callingUserPhotoViewMini.setAlpha(0);
                        callingUserPhotoViewMini.setTranslationY(-AndroidUtilities.dp(135));
                        callingUserPhotoViewMini.animate().alpha(1f).translationY(0).scaleY(1f).scaleX(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    }
                } else {
                    if (wasVideo) {
                        callingUserPhotoViewMini.setAlpha(0f);
                        callingUserPhotoViewMini.setTranslationY(AndroidUtilities.dp(48));
                        callingUserPhotoViewMini.setScaleX(0.1f);
                        callingUserPhotoViewMini.setScaleY(0.1f);
                    }
                }
            } else if (!noVideo && callingUserPhotoViewMini.getTag() != null) {
                callingUserPhotoViewMini.animate().setListener(null).cancel();
                callingUserPhotoViewMini.setTranslationY(0);
                callingUserPhotoViewMini.animate().alpha(0).setDuration(150).scaleX(0.1f).scaleY(0.1f).setInterpolator(CubicBezierInterpolator.DEFAULT)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                callingUserPhotoViewMini.setVisibility(View.GONE);
                            }
                        }).start();
            }
        } else {
            callingUserPhotoViewMini.animate().setListener(null).cancel();
            callingUserPhotoViewMini.setTranslationY(0);
            callingUserPhotoViewMini.setAlpha(1f);
            callingUserPhotoViewMini.setScaleX(1f);
            callingUserPhotoViewMini.setScaleY(1f);
            callingUserPhotoViewMini.setVisibility(noVideo ? View.VISIBLE : View.GONE);
        }
        callingUserPhotoViewMini.setTag(noVideo ? 1 : null);
    }

    private void updateKeyView(boolean animated) {
        if (emojiLoaded) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        byte[] auth_key = null;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(service.getEncryptionKey());
            buf.write(service.getGA());
            auth_key = buf.toByteArray();
        } catch (Exception checkedExceptionsAreBad) {
            FileLog.e(checkedExceptionsAreBad, false);
        }
        if (auth_key == null) {
            return;
        }
        byte[] sha256 = Utilities.computeSHA256(auth_key, 0, auth_key.length);
        String[] emoji = EncryptionKeyEmojifier.emojifyForCall(sha256);

        List<TLRPC.Document> documents = new ArrayList<>();
        List<Emoji.EmojiDrawable> drawables = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Emoji.preloadEmoji(emoji[i]);
            Emoji.EmojiDrawable drawable = Emoji.getEmojiDrawable(emoji[i]);
            if (drawable != null) {
                drawable.setBounds(0, 0, AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                drawable.preload();
                int[] emojiOnly = new int[1];
                TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                paint.setTextSize(AndroidUtilities.dp(28));
                CharSequence txt = emoji[i];
                txt = Emoji.replaceEmoji(txt, paint.getFontMetricsInt(), false, emojiOnly);
                TLRPC.Document doc1 = replaceEmojiToLottieFrame(txt, emojiOnly);
                drawables.add(drawable);
                if (doc1 != null) {
                    documents.add(doc1);
                }
                emojiViews[i].setVisibility(View.GONE);
            }
            emojiDrawables[i] = drawable;
        }
        if (documents.size() == 4) {
            for (int i = 0; i < documents.size(); i++) {
                emojiViews[i].setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_CALL, currentAccount, documents.get(i)));
                emojiViews[i].getImageReceiver().clearImage();
            }
        } else {
            for (int i = 0; i < drawables.size(); i++) {
                emojiViews[i].setImageDrawable(drawables.get(i));
            }
        }
        checkEmojiLoaded(animated);
    }

    private void checkEmojiLoaded(boolean animated) {
        int count = 0;

        for (int i = 0; i < 4; i++) {
            if (emojiDrawables[i] != null && emojiDrawables[i].isLoaded()) {
                count++;
            }
        }

        if (count == 4) {
            emojiLoaded = true;
            for (int i = 0; i < 4; i++) {
                if (emojiViews[i].getVisibility() != View.VISIBLE) {
                    emojiViews[i].setVisibility(View.VISIBLE);
                    if (animated) {
                        emojiViews[i].setAlpha(0f);
                        emojiViews[i].setScaleX(0f);
                        emojiViews[i].setScaleY(0f);
                        emojiViews[i].animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK).setDuration(250).start();
                    }
                }
            }
            encryptionTooltip.postDelayed(() -> {
                if (SharedConfig.callEncryptionHintDisplayedCount < 2) {
                    SharedConfig.incrementCallEncryptionHintDisplayed(1);
                    encryptionTooltip.setTranslationY(emojiLayout.getY() + AndroidUtilities.dp(36));
                    encryptionTooltip.show();
                }
            }, 1000);
        }
    }

    private void showAcceptDeclineView(boolean show, boolean animated) {
        if (!animated) {
            acceptDeclineView.setVisibility(show ? View.VISIBLE : View.GONE);
        } else {
            if (show && acceptDeclineView.getTag() == null) {
                acceptDeclineView.animate().setListener(null).cancel();
                if (acceptDeclineView.getVisibility() == View.GONE) {
                    acceptDeclineView.setVisibility(View.VISIBLE);
                    acceptDeclineView.setAlpha(0);
                }
                acceptDeclineView.animate().alpha(1f);
            }
            if (!show && acceptDeclineView.getTag() != null) {
                acceptDeclineView.animate().setListener(null).cancel();
                acceptDeclineView.animate().setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        acceptDeclineView.setVisibility(View.GONE);
                    }
                }).alpha(0f);
            }
        }

        acceptDeclineView.setEnabled(show);
        acceptDeclineView.setTag(show ? 1 : null);
    }

    private void updateButtons(boolean animated) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionSet transitionSet = new TransitionSet();
            Visibility visibility = new Visibility() {
                @Override
                public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    PropertyValuesHolder pvh1 = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, AndroidUtilities.dp(100), 0);
                    PropertyValuesHolder pvh2 = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f, 1f);
                    PropertyValuesHolder pvh3 = PropertyValuesHolder.ofFloat(View.SCALE_X, 0f, 1f);
                    ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view, pvh1, pvh2, pvh3);
                    if (view instanceof VoIPToggleButton) {
                        view.setTranslationY(AndroidUtilities.dp(100));
                        view.setScaleX(0f);
                        view.setScaleY(0f);
                        animator.setStartDelay(((VoIPToggleButton) view).animationDelay);
                    }
                    if (view instanceof VoIpSwitchLayout) {
                        view.setTranslationY(AndroidUtilities.dp(100));
                        view.setScaleX(0f);
                        view.setScaleY(0f);
                        animator.setStartDelay(((VoIpSwitchLayout) view).animationDelay);
                    }
                    return animator;
                }

                @Override
                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    PropertyValuesHolder pvh1 = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, view.getTranslationY(), AndroidUtilities.dp(100));
                    PropertyValuesHolder pvh2 = PropertyValuesHolder.ofFloat(View.SCALE_Y, view.getScaleY(), 0f);
                    PropertyValuesHolder pvh3 = PropertyValuesHolder.ofFloat(View.SCALE_X, view.getScaleX(), 0f);
                    return ObjectAnimator.ofPropertyValuesHolder(view, pvh1, pvh2, pvh3);
                }
            };
            transitionSet
                    .addTransition(visibility.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT))
                    .addTransition(new ChangeBounds().setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT));
            transitionSet.excludeChildren(VoIPToggleButton.class, true);
            transitionSet.excludeChildren(VoIpSwitchLayout.class, true);
            TransitionManager.beginDelayedTransition(buttonsLayout, transitionSet);
        }

        if (currentState == VoIPService.STATE_ENDED) {
            bottomSpeakerBtn.setVisibility(View.GONE);
            bottomVideoBtn.setVisibility(View.GONE);
            bottomMuteBtn.setVisibility(View.GONE);
            bottomEndCallBtn.setVisibility(View.GONE);
            return;
        }

        if (currentState == VoIPService.STATE_WAITING_INCOMING || currentState == VoIPService.STATE_BUSY) {
            if (service.privateCall != null && service.privateCall.video && currentState == VoIPService.STATE_WAITING_INCOMING) {
                if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                    setFrontalCameraAction(bottomSpeakerBtn, service, animated);
                    if (uiVisible) {
                        speakerPhoneIcon.animate().alpha(1f).start();
                    }
                } else {
                    setSpeakerPhoneAction(bottomSpeakerBtn, service, animated);
                    speakerPhoneIcon.animate().alpha(0).start();
                }
                setVideoAction(bottomVideoBtn, service, false);
                setMicrohoneAction(bottomMuteBtn, service, animated);
            } else {
                bottomSpeakerBtn.setVisibility(View.GONE);
                bottomVideoBtn.setVisibility(View.GONE);
                bottomMuteBtn.setVisibility(View.GONE);
            }
            bottomEndCallBtn.setVisibility(View.GONE);
        } else {
            if (instance == null) {
                return;
            }
            if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                setFrontalCameraAction(bottomSpeakerBtn, service, animated);
                if (uiVisible) {
                    speakerPhoneIcon.setTag(1);
                    speakerPhoneIcon.animate().alpha(1f).start();
                }
            } else {
                setSpeakerPhoneAction(bottomSpeakerBtn, service, animated);
                speakerPhoneIcon.setTag(null);
                speakerPhoneIcon.animate().alpha(0f).start();
            }
            setVideoAction(bottomVideoBtn, service, false);
            setMicrohoneAction(bottomMuteBtn, service, animated);

            bottomEndCallBtn.setData(R.drawable.calls_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("VoipEndCall2", R.string.VoipEndCall2), false, animated);
            bottomEndCallBtn.setOnClickListener(view -> {
                if (VoIPService.getSharedInstance() != null) {
                    AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                    hideUiRunnableWaiting = false;
                    VoIPService.getSharedInstance().hangUp();
                }
            });
        }

        int animationDelay = 0;
        if (bottomSpeakerBtn.getVisibility() == View.VISIBLE) {
            bottomSpeakerBtn.animationDelay = animationDelay;
            animationDelay += 16;
        }
        if (bottomVideoBtn.getVisibility() == View.VISIBLE) {
            bottomVideoBtn.animationDelay = animationDelay;
            animationDelay += 16;
        }
        if (bottomMuteBtn.getVisibility() == View.VISIBLE) {
            bottomMuteBtn.animationDelay = animationDelay;
            animationDelay += 16;
        }
        if (bottomEndCallBtn.getVisibility() == View.VISIBLE) {
            bottomEndCallBtn.animationDelay = animationDelay;
        }
        updateSpeakerPhoneIcon();
    }

    private void setMicrohoneAction(VoIpSwitchLayout bottomButton, VoIPService service, boolean animated) {
        bottomButton.setType(VoIpSwitchLayout.Type.MICRO, service.isMicMute());
        currentUserCameraFloatingLayout.setMuted(service.isMicMute(), animated);
        bottomButton.setOnBtnClickedListener((view) -> {
            final VoIPService serviceInstance = VoIPService.getSharedInstance();
            if (serviceInstance != null) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                final boolean micMute = !serviceInstance.isMicMute();
                if (accessibilityManager.isTouchExplorationEnabled()) {
                    final String text;
                    if (micMute) {
                        text = LocaleController.getString("AccDescrVoipMicOff", R.string.AccDescrVoipMicOff);
                    } else {
                        text = LocaleController.getString("AccDescrVoipMicOn", R.string.AccDescrVoipMicOn);
                    }
                    view.announceForAccessibility(text);
                }
                serviceInstance.setMicMute(micMute, false, true);
                previousState = currentState;
                updateViewState();
            }
        });
    }

    private void setVideoAction(VoIpSwitchLayout bottomButton, VoIPService service, boolean fast) {
        boolean isVideoAvailable;
        if (currentUserIsVideo || callingUserIsVideo) {
            isVideoAvailable = true;
        } else {
            isVideoAvailable = service.isVideoAvailable();
        }
        if (isVideoAvailable) {
            if (currentUserIsVideo) {
                if (service.isScreencast()) {
                    bottomButton.setType(VoIpSwitchLayout.Type.VIDEO, false, fast);
                } else {
                    bottomButton.setType(VoIpSwitchLayout.Type.VIDEO, false, fast);
                }
            } else {
                bottomButton.setType(VoIpSwitchLayout.Type.VIDEO, true, fast);
            }
            bottomButton.setOnBtnClickedListener(view -> {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 102);
                } else {
                    if (Build.VERSION.SDK_INT < 21 && service.privateCall != null && !service.privateCall.video && !callingUserIsVideo && !service.sharedUIParams.cameraAlertWasShowed) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage(LocaleController.getString("VoipSwitchToVideoCall", R.string.VoipSwitchToVideoCall));
                        builder.setPositiveButton(LocaleController.getString("VoipSwitch", R.string.VoipSwitch), (dialogInterface, i) -> {
                            service.sharedUIParams.cameraAlertWasShowed = true;
                            toggleCameraInput();
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.create().show();
                    } else {
                        toggleCameraInput();
                    }
                }
            });
            bottomButton.setEnabled(true);
        } else {
            bottomButton.setType(VoIpSwitchLayout.Type.VIDEO, true);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        }
    }

    private void updateSpeakerPhoneIcon() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        VoipAudioManager vam = VoipAudioManager.get();
        if (service.isBluetoothOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_bluetooth);
        } else if (vam.isSpeakerphoneOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_speaker);
        } else {
            if (service.isHeadsetPlugged()) {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_headset);
            } else {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_phone);
            }
        }
    }

    private void setSpeakerPhoneAction(VoIpSwitchLayout bottomButton, VoIPService service, boolean animated) {
        final int selectedSpeaker;
        VoipAudioManager vam = VoipAudioManager.get();
        if (service.isBluetoothOn()) {
            selectedSpeaker = 2;
            bottomButton.setType(VoIpSwitchLayout.Type.BLUETOOTH, false);
        } else if (vam.isSpeakerphoneOn()) {
            selectedSpeaker = 0;
            bottomButton.setType(VoIpSwitchLayout.Type.SPEAKER, true);
        } else {
            selectedSpeaker = 1;
            bottomButton.setType(VoIpSwitchLayout.Type.SPEAKER, false);
        }
        bottomButton.setEnabled(true);
        bottomButton.setOnBtnClickedListener(view -> {
            if (VoIPService.getSharedInstance() != null) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false, selectedSpeaker);
                setSpeakerPhoneAction(bottomButton, service, true);
            }
        });
    }

    private void setFrontalCameraAction(VoIpSwitchLayout bottomButton, VoIPService service, boolean animated) {
        if (!currentUserIsVideo) {
            bottomButton.setType(VoIpSwitchLayout.Type.CAMERA, false);
            bottomButton.setOnBtnClickedListener(null);
            bottomButton.setEnabled(false);
        } else {
            bottomButton.setEnabled(true);
            if (service.isFrontFaceCamera()) {
                bottomButton.setType(VoIpSwitchLayout.Type.CAMERA, !service.isSwitchingCamera());
            } else {
                bottomButton.setType(VoIpSwitchLayout.Type.CAMERA, service.isSwitchingCamera());
            }
            bottomButton.setOnBtnClickedListener(view -> {
                final VoIPService serviceInstance = VoIPService.getSharedInstance();
                if (serviceInstance != null) {
                    AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                    hideUiRunnableWaiting = false;
                    if (accessibilityManager.isTouchExplorationEnabled()) {
                        final String text;
                        if (service.isFrontFaceCamera()) {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToBack", R.string.AccDescrVoipCamSwitchedToBack);
                        } else {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToFront", R.string.AccDescrVoipCamSwitchedToFront);
                        }
                        view.announceForAccessibility(text);
                    }
                    bottomButton.setType(VoIpSwitchLayout.Type.CAMERA, !service.isFrontFaceCamera());
                    serviceInstance.switchCamera();
                }
            });
        }
    }

    public void onScreenCastStart() {
        if (previewDialog == null) {
            return;
        }
        previewDialog.dismiss(true, true);
    }

    private void toggleCameraInput() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (accessibilityManager.isTouchExplorationEnabled()) {
                final String text;
                if (!currentUserIsVideo) {
                    text = LocaleController.getString("AccDescrVoipCamOn", R.string.AccDescrVoipCamOn);
                } else {
                    text = LocaleController.getString("AccDescrVoipCamOff", R.string.AccDescrVoipCamOff);
                }
                fragmentView.announceForAccessibility(text);
            }
            if (!currentUserIsVideo) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if (previewDialog == null) {
                        service.createCaptureDevice(false);
                        if (!service.isFrontFaceCamera()) {
                            service.switchCamera();
                        }
                        windowView.setLockOnScreen(true);
                        int[] locVideoButton = new int[2];
                        bottomVideoBtn.getLocationOnScreen(locVideoButton);
                        previewDialog = new PrivateVideoPreviewDialogNew(fragmentView.getContext(), locVideoButton[0], locVideoButton[1]) {
                            @Override
                            public void onDismiss(boolean screencast, boolean apply) {
                                previewDialog = null;
                                VoIPService service = VoIPService.getSharedInstance();
                                windowView.setLockOnScreen(false);
                                if (apply) {
                                    currentUserIsVideo = true;
                                    if (service != null && !screencast) {
                                        service.requestVideoCall(false);
                                        service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                                        service.switchToSpeaker();
                                    }
                                    if (service != null) {
                                        setVideoAction(bottomVideoBtn, service, true);
                                    }
                                } else {
                                    if (service != null) {
                                        service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                    }
                                }
                                previousState = currentState;
                                updateViewState();
                            }

                            @Override
                            protected void afterOpened() {
                                gradientLayout.lockDrawing = true;
                                gradientLayout.invalidate();
                            }

                            @Override
                            protected void beforeClosed() {
                                gradientLayout.lockDrawing = false;
                                gradientLayout.invalidate();
                            }

                            @Override
                            protected int[] getFloatingViewLocation() {
                                int[] loc = new int[2];
                                int[] result = new int[3];
                                currentUserCameraFloatingLayout.getLocationOnScreen(loc);
                                result[0] = loc[0];
                                result[1] = loc[1];
                                result[2] = currentUserCameraFloatingLayout.getMeasuredWidth();
                                return result;
                            }

                            @Override
                            protected boolean isHasVideoOnMainScreen() {
                                return callingUserIsVideo;
                            }
                        };
                        if (lastInsets != null) {
                            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
                        }
                        fragmentView.addView(previewDialog);
                    }
                    return;
                } else {
                    currentUserIsVideo = true;
                    if (!service.isSpeakerphoneOn()) {
                        VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
                    }
                    service.requestVideoCall(false);
                    service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                }
            } else {
                currentUserTextureView.saveCameraLastBitmap();
                service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                if (Build.VERSION.SDK_INT >= 21) {
                    service.clearCamera();
                }
            }
            previousState = currentState;
            updateViewState();
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (instance != null) {
            instance.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runAcceptCallAnimation(() -> {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().acceptIncomingCall();
                    }
                });
            } else {
                if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    VoIPService.getSharedInstance().declineIncomingCall();
                    VoIPHelper.permissionDenied(activity, () -> windowView.finish(), requestCode);
                    return;
                }
            }
        }
        if (requestCode == 102) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleCameraInput();
            }
        }
    }

    private void updateSystemBarColors() {
        overlayPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f * uiVisibilityAlpha * enterTransitionProgress)));
        overlayBottomPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * (0.5f + 0.5f * fillNaviagtionBarValue) * enterTransitionProgress)));
        if (fragmentView != null) {
            fragmentView.invalidate();
        }
    }

    public static void onPause() {
        if (instance != null) {
            instance.onPauseInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onPause();
        }
    }

    public static void onResume() {
        if (instance != null) {
            instance.onResumeInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onResume();
        }
    }

    public void onPauseInternal() {
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);

        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }

        boolean hasPermissionsToPip = AndroidUtilities.checkInlinePermissions(activity);

        if (canSwitchToPip && hasPermissionsToPip) {
            int h = instance.windowView.getMeasuredHeight();
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }

        if (currentUserIsVideo && (!hasPermissionsToPip || !screenOn)) {
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                service.setVideoState(false, Instance.VIDEO_STATE_PAUSED);
            }
        }
    }

    public void onResumeInternal() {
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.finish();
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
                service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
            }
            updateViewState();
        } else {
            windowView.finish();
        }

        deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
    }

    private void showErrorDialog(CharSequence message) {
        if (activity.isFinishing()) {
            return;
        }
        AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                .setMessage(message)
                .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                .show();
        dlg.setCanceledOnTouchOutside(true);
        dlg.setOnDismissListener(dialog -> windowView.finish());
    }

    @SuppressLint("InlinedApi")
    private void requestInlinePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlertsCreator.createDrawOverlayPermissionDialog(activity, (dialogInterface, i) -> {
                if (windowView != null) {
                    windowView.finish();
                }
            }).show();
        }
    }

    public TLRPC.Document replaceEmojiToLottieFrame(CharSequence text, int[] emojiOnly) {
        if (!(text instanceof Spannable)) {
            return null;
        }
        Spannable spannable = (Spannable) text;
        Emoji.EmojiSpan[] spans = spannable.getSpans(0, spannable.length(), Emoji.EmojiSpan.class);
        AnimatedEmojiSpan[] aspans = spannable.getSpans(0, spannable.length(), AnimatedEmojiSpan.class);

        if (spans == null || (emojiOnly == null ? 0 : emojiOnly[0]) - spans.length - (aspans == null ? 0 : aspans.length) > 0) {
            return null;
        }

        for (Emoji.EmojiSpan span : spans) {
            return MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(span.emoji);
        }
        return null;
    }
}
