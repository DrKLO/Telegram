package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
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
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.EncryptionKeyEmojifier;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.DarkAlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.voip.AcceptDeclineView;
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialog;
import org.telegram.ui.Components.voip.VoIPButtonsLayout;
import org.telegram.ui.Components.voip.VoIPFloatingLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Components.voip.VoIPNotificationsLayout;
import org.telegram.ui.Components.voip.VoIPOverlayBackground;
import org.telegram.ui.Components.voip.VoIPPiPView;
import org.telegram.ui.Components.voip.VoIPStatusTextView;
import org.telegram.ui.Components.voip.VoIPTextureView;
import org.telegram.ui.Components.voip.VoIPToggleButton;
import org.telegram.ui.Components.voip.VoIPWindowView;
import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.io.ByteArrayOutputStream;

public class VoIPFragment implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate {

    private final static int STATE_GONE = 0;
    private final static int STATE_FULLSCREEN = 1;
    private final static int STATE_FLOATING = 2;

    private final int currentAccount;

    Activity activity;

    TLRPC.User currentUser;
    TLRPC.User callingUser;

    VoIPToggleButton[] bottomButtons = new VoIPToggleButton[4];

    private ViewGroup fragmentView;
    private VoIPOverlayBackground overlayBackground;

    private BackupImageView callingUserPhotoView;
    private BackupImageView callingUserPhotoViewMini;

    private TextView callingUserTitle;

    private VoIPStatusTextView statusTextView;
    private ImageView backIcon;
    private ImageView speakerPhoneIcon;

    LinearLayout emojiLayout;
    TextView emojiRationalTextView;
    ImageView[] emojiViews = new ImageView[4];
    Emoji.EmojiDrawable[] emojiDrawables = new Emoji.EmojiDrawable[4];
    LinearLayout statusLayout;
    private VoIPFloatingLayout currentUserCameraFloatingLayout;
    private VoIPFloatingLayout callingUserMiniFloatingLayout;

    private TextureViewRenderer callingUserMiniTextureRenderer;
    private VoIPTextureView callingUserTextureView;
    private VoIPTextureView currentUserTextureView;

    private AcceptDeclineView acceptDeclineView;

    View bottomShadow;
    View topShadow;

    private VoIPButtonsLayout buttonsLayout;
    Paint overlayPaint = new Paint();
    Paint overlayBottomPaint = new Paint();

    boolean isOutgoing;
    boolean callingUserIsVideo;
    boolean currentUserIsVideo;

    private PrivateVideoPreviewDialog previewDialog;

    private int currentState;
    private int previousState;
    private WindowInsets lastInsets;

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
    int animationIndex = -1;
    VoIPNotificationsLayout notificationsLayout;

    HintView tapToVideoTooltip;

    ValueAnimator uiVisibilityAnimator;
    ValueAnimator.AnimatorUpdateListener statusbarAnimatorListener = valueAnimator -> {
        uiVisibilityAlpha = (float) valueAnimator.getAnimatedValue();
        updateSystemBarColors();
    };

    float fillNaviagtionBarValue;
    boolean fillNaviagtionBar;
    ValueAnimator naviagtionBarAnimator;
    ValueAnimator.AnimatorUpdateListener navigationBarAnimationListener = valueAnimator -> {
        fillNaviagtionBarValue = (float) valueAnimator.getAnimatedValue();
        updateSystemBarColors();
    };

    boolean hideUiRunnableWaiting;
    Runnable hideUIRunnable = () -> {
        hideUiRunnableWaiting = false;
        if (canHideUI && uiVisible && !emojiExpanded) {
            lastContentTapTime = System.currentTimeMillis();
            showUi(false);
            previousState = currentState;
            updateViewState();
        }
    };
    private boolean lockOnScreen;
    private boolean screenWasWakeup;
    private boolean isVideoCall;

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
            previousState = currentState;
            updateViewState();
            return;
        }
        if (emojiExpanded) {
            expandEmoji(false);
        } else {
            if (emojiRationalTextView.getVisibility() != View.GONE) {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                    h -= instance.lastInsets.getSystemWindowInsetBottom();
                }
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
        ((FrameLayout.LayoutParams) topShadow.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) statusLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(68) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) emojiLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(17) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) callingUserPhotoViewMini.getLayoutParams()).topMargin = AndroidUtilities.dp(68) + lastInsets.getSystemWindowInsetTop();

        ((FrameLayout.LayoutParams) currentUserCameraFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) callingUserMiniFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) callingUserTextureView.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) notificationsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();

        ((FrameLayout.LayoutParams) bottomShadow.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeInCallActivity);
    }

    private void destroy() {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeInCallActivity);
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
        }
    }

    @Override
    public void onSignalBarsCountChanged(int count) {
        if (statusTextView != null) {
            statusTextView.setSignalBarCount(count);
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
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), lastInsets.getSystemWindowInsetTop(), overlayPaint);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, getMeasuredHeight() - lastInsets.getSystemWindowInsetBottom(), getMeasuredWidth(), getMeasuredHeight(), overlayBottomPaint);
                }
            }

            float pressedX;
            float pressedY;
            boolean check;
            long pressedTime;

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressedX = event.getX();
                        pressedY = event.getY();
                        check = true;
                        pressedTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        check = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (check) {
                            float dx = event.getX() - pressedX;
                            float dy = event.getY() - pressedY;
                            long currentTime = System.currentTimeMillis();
                            if (dx * dx + dy * dy < touchSlop * touchSlop && currentTime - pressedTime < 300 && currentTime - lastContentTapTime > 300) {
                                lastContentTapTime = System.currentTimeMillis();
                                if (emojiExpanded) {
                                    expandEmoji(false);
                                } else if (canHideUI) {
                                    showUi(!uiVisible);
                                    previousState = currentState;
                                    updateViewState();
                                }
                            }
                            check = false;
                        }
                        break;
                }
                return check;
            }
        };
        frameLayout.setClipToPadding(false);
        frameLayout.setClipChildren(false);
        updateSystemBarColors();
        fragmentView = frameLayout;
        frameLayout.setFitsSystemWindows(true);
        callingUserPhotoView = new BackupImageView(context) {

            int blackoutColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.3f));

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawColor(blackoutColor);
            }
        };
        callingUserTextureView = new VoIPTextureView(context, false, true, false, false);
        callingUserTextureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        callingUserTextureView.renderer.setEnableHardwareScaler(true);
        callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
   //     callingUserTextureView.attachBackgroundRenderer();

        frameLayout.addView(callingUserPhotoView);
        frameLayout.addView(callingUserTextureView);


        final BackgroundGradientDrawable gradientDrawable = new BackgroundGradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1b354e, 0xFF255b7d});
        final BackgroundGradientDrawable.Sizes sizes = BackgroundGradientDrawable.Sizes.ofDeviceScreen(BackgroundGradientDrawable.Sizes.Orientation.PORTRAIT);
        gradientDrawable.startDithering(sizes, new BackgroundGradientDrawable.ListenerAdapter() {
            @Override
            public void onAllSizesReady() {
                callingUserPhotoView.invalidate();
            }
        });
        overlayBackground = new VoIPOverlayBackground(context);
        overlayBackground.setVisibility(View.GONE);

        callingUserPhotoView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            ImageReceiver.BitmapHolder bmp = imageReceiver.getBitmapSafe();
            if (bmp != null) {
                overlayBackground.setBackground(bmp);
            }
        });

        callingUserPhotoView.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_BIG), null, gradientDrawable, callingUser);

        currentUserCameraFloatingLayout = new VoIPFloatingLayout(context);
        currentUserCameraFloatingLayout.setDelegate((progress, value) -> currentUserTextureView.setScreenshareMiniProgress(progress, value));
        currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
        currentUserTextureView = new VoIPTextureView(context, true, false);
        currentUserTextureView.renderer.setIsCamera(true);
        currentUserTextureView.renderer.setUseCameraRotation(true);
        currentUserCameraFloatingLayout.setOnTapListener(view -> {
            if (currentUserIsVideo && callingUserIsVideo && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                callingUserMiniFloatingLayout.setRelativePosition(currentUserCameraFloatingLayout);
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
        callingUserMiniFloatingLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        callingUserMiniFloatingLayout.addView(callingUserMiniTextureRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        callingUserMiniFloatingLayout.setOnTapListener(view -> {
            if (cameraForceExpanded && System.currentTimeMillis() - lastContentTapTime > 500) {
                AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
                hideUiRunnableWaiting = false;
                lastContentTapTime = System.currentTimeMillis();
                currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
                cameraForceExpanded = false;
                previousState = currentState;
                updateViewState();
            }
        });
        callingUserMiniFloatingLayout.setVisibility(View.GONE);

        frameLayout.addView(currentUserCameraFloatingLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        frameLayout.addView(callingUserMiniFloatingLayout);
        frameLayout.addView(overlayBackground);


        bottomShadow = new View(context);
        bottomShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.5f))}));
        frameLayout.addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.BOTTOM));

        topShadow = new View(context);
        topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f)), Color.TRANSPARENT}));
        frameLayout.addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.TOP));


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
            if (emojiLoaded) {
                expandEmoji(!emojiExpanded);
            }
        });

        emojiRationalTextView = new TextView(context);
        emojiRationalTextView.setText(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, UserObject.getFirstName(callingUser)));
        emojiRationalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiRationalTextView.setTextColor(Color.WHITE);
        emojiRationalTextView.setGravity(Gravity.CENTER);
        emojiRationalTextView.setVisibility(View.GONE);

        for (int i = 0; i < 4; i++) {
            emojiViews[i] = new ImageView(context);
            emojiViews[i].setScaleType(ImageView.ScaleType.FIT_XY);
            emojiLayout.addView(emojiViews[i], LayoutHelper.createLinear(22, 22, i == 0 ? 0 : 4, 0, 0, 0));
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

        callingUserPhotoViewMini = new BackupImageView(context);
        callingUserPhotoViewMini.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_SMALL), null, Theme.createCircleDrawable(AndroidUtilities.dp(135), 0xFF000000), callingUser);
        callingUserPhotoViewMini.setRoundRadius(AndroidUtilities.dp(135) / 2);
        callingUserPhotoViewMini.setVisibility(View.GONE);

        callingUserTitle = new TextView(context);
        callingUserTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        callingUserTitle.setText(ContactsController.formatName(callingUser.first_name, callingUser.last_name));
        callingUserTitle.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(.666666667f), 0x4C000000);
        callingUserTitle.setTextColor(Color.WHITE);
        callingUserTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        callingUserTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        statusLayout.addView(callingUserTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusTextView = new VoIPStatusTextView(context);
        ViewCompat.setImportantForAccessibility(statusTextView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        statusLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusLayout.setClipChildren(false);
        statusLayout.setClipToPadding(false);
        statusLayout.setPadding(0, 0, 0, AndroidUtilities.dp(15));

        frameLayout.addView(callingUserPhotoViewMini, LayoutHelper.createFrame(135, 135, Gravity.CENTER_HORIZONTAL, 0, 68, 0, 0));
        frameLayout.addView(statusLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 68, 0, 0));
        frameLayout.addView(emojiLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 17, 0, 0));
        frameLayout.addView(emojiRationalTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 32, 24, 0));

        buttonsLayout = new VoIPButtonsLayout(context);
        for (int i = 0; i < 4; i++) {
            bottomButtons[i] = new VoIPToggleButton(context);
            buttonsLayout.addView(bottomButtons[i]);
        }
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
                            VoIPService.getSharedInstance().acceptIncomingCall();
                            if (currentUserIsVideo) {
                                VoIPService.getSharedInstance().requestVideoCall(false);
                            }
                        }
                    }
                }
            }

            @Override
            public void onDicline() {
                if (currentState == VoIPService.STATE_BUSY) {
                    windowView.finish();
                } else {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().declineIncomingCall();
                    }
                }
            }
        });
        acceptDeclineView.setScreenWasWakeup(screenWasWakeup);

        frameLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        frameLayout.addView(acceptDeclineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 186, Gravity.BOTTOM));

        backIcon = new ImageView(context);
        backIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        backIcon.setImageResource(R.drawable.ic_ab_back);
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
        speakerPhoneIcon.setOnClickListener(view -> {
            if (speakerPhoneIcon.getTag() == null) {
                return;
            }
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
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

        notificationsLayout = new VoIPNotificationsLayout(context);
        notificationsLayout.setGravity(Gravity.BOTTOM);
        notificationsLayout.setOnViewsUpdated(() -> {
            previousState = currentState;
            updateViewState();
        });
        frameLayout.addView(notificationsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM, 16, 0, 16, 0));

        tapToVideoTooltip = new HintView(context, 4);
        tapToVideoTooltip.setText(LocaleController.getString("TapToTurnCamera", R.string.TapToTurnCamera));
        frameLayout.addView(tapToVideoTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 8));
        tapToVideoTooltip.setBottomOffset(AndroidUtilities.dp(4));
        tapToVideoTooltip.setVisibility(View.GONE);

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
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
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                VoIPPiPView.getInstance().windowView.setAlpha(1f);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
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
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        AndroidUtilities.runOnUIThread(() -> {
            windowView.setAlpha(1f);
            Animator animator = createPiPTransition(true);

            backIcon.setAlpha(0f);
            emojiLayout.setAlpha(0f);
            statusLayout.setAlpha(0f);
            buttonsLayout.setAlpha(0f);
            bottomShadow.setAlpha(0f);
            topShadow.setAlpha(0f);
            speakerPhoneIcon.setAlpha(0f);
            notificationsLayout.setAlpha(0f);
            callingUserPhotoView.setAlpha(0f);

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
                callingUserPhotoView.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
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

            callingUserPhotoView.setAlpha(0f);
            callingUserPhotoView.setScaleX(callingUserToScale);
            callingUserPhotoView.setScaleY(callingUserToScale);
            callingUserPhotoView.setTranslationX(callingUserToX);
            callingUserPhotoView.setTranslationY(callingUserToY);
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

            callingUserPhotoView.setScaleX(callingUserScale);
            callingUserPhotoView.setScaleY(callingUserScale);
            callingUserPhotoView.setTranslationX(tx);
            callingUserPhotoView.setTranslationY(ty);
            callingUserPhotoView.setAlpha(1f - v);
        });
        return animator;
    }

    private void expandEmoji(boolean expanded) {
        if (!emojiLoaded || emojiExpanded == expanded || !uiVisible) {
            return;
        }
        emojiExpanded = expanded;
        if (expanded) {
            AndroidUtilities.runOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
            float s1 = emojiLayout.getMeasuredWidth();
            float s2 = windowView.getMeasuredWidth() - AndroidUtilities.dp(128);
            float scale = s2 / s1;
            emojiLayout.animate().scaleX(scale).scaleY(scale)
                    .translationY(windowView.getHeight() / 2f - emojiLayout.getBottom())
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .setDuration(250)
                    .start();
            emojiRationalTextView.animate().setListener(null).cancel();
            if (emojiRationalTextView.getVisibility() != View.VISIBLE) {
                emojiRationalTextView.setVisibility(View.VISIBLE);
                emojiRationalTextView.setAlpha(0f);
            }
            emojiRationalTextView.animate().alpha(1f).setDuration(150).start();

            overlayBackground.animate().setListener(null).cancel();
            if (overlayBackground.getVisibility() != View.VISIBLE) {
                overlayBackground.setVisibility(View.VISIBLE);
                overlayBackground.setAlpha(0f);
                overlayBackground.setShowBlackout(currentUserIsVideo || callingUserIsVideo, false);
            }
            overlayBackground.animate().alpha(1f).setDuration(150).start();
        } else {
            emojiLayout.animate().scaleX(1f).scaleY(1f)
                    .translationY(0)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .setDuration(150)
                    .start();

            if (emojiRationalTextView.getVisibility() != View.GONE) {
                emojiRationalTextView.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        VoIPService service = VoIPService.getSharedInstance();
                        if (canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
                            AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
                            hideUiRunnableWaiting = true;
                        }
                        emojiRationalTextView.setVisibility(View.GONE);
                    }
                }).setDuration(150).start();

                overlayBackground.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        overlayBackground.setVisibility(View.GONE);
                    }
                }).setDuration(150).start();
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
        boolean showCallingAvatarMini = false;
        int statusLayoutOffset = 0;
        VoIPService service = VoIPService.getSharedInstance();

        switch (currentState) {
            case VoIPService.STATE_WAITING_INCOMING:
                showAcceptDeclineView = true;
                lockOnScreen = true;
                statusLayoutOffset = AndroidUtilities.dp(24);
                acceptDeclineView.setRetryMod(false);
                if (service != null && service.privateCall.video) {
                    if (currentUserIsVideo && callingUser.photo != null) {
                        showCallingAvatarMini = true;
                    } else {
                        showCallingAvatarMini = false;
                    }
                    statusTextView.setText(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding), true, animated);
                    acceptDeclineView.setTranslationY(-AndroidUtilities.dp(60));
                } else {
                    statusTextView.setText(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding), true, animated);
                    acceptDeclineView.setTranslationY(0);
                }
                break;
            case VoIPService.STATE_WAIT_INIT:
            case VoIPService.STATE_WAIT_INIT_ACK:
                statusTextView.setText(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true, animated);
                break;
            case VoIPService.STATE_EXCHANGING_KEYS:
                statusTextView.setText(LocaleController.getString("VoipExchangingKeys", R.string.VoipExchangingKeys), true, animated);
                break;
            case VoIPService.STATE_WAITING:
                statusTextView.setText(LocaleController.getString("VoipWaiting", R.string.VoipWaiting), true, animated);
                break;
            case VoIPService.STATE_RINGING:
                statusTextView.setText(LocaleController.getString("VoipRinging", R.string.VoipRinging), true, animated);
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
                showTimer = true;
                if (currentState == VoIPService.STATE_RECONNECTING) {
                    showReconnecting = true;
                }
                break;
            case VoIPService.STATE_ENDED:
                currentUserTextureView.saveCameraLastBitmap();
                AndroidUtilities.runOnUIThread(() -> windowView.finish(), 200);
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

        if (service != null) {
            callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE;
            currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;
            if (currentUserIsVideo && !isVideoCall) {
                isVideoCall = true;
            }
        }

        if (animated) {
            currentUserCameraFloatingLayout.saveRelatedPosition();
            callingUserMiniFloatingLayout.saveRelatedPosition();
        }

        if (callingUserIsVideo) {
            if (!switchingToPip) {
                callingUserPhotoView.setAlpha(1f);
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
            fillNavigationBar(true, animated);
        } else {
            fillNavigationBar(false, animated);
            callingUserPhotoView.setVisibility(View.VISIBLE);
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

        showCallingUserAvatarMini(showCallingAvatarMini, animated);
        statusLayoutOffset += callingUserPhotoViewMini.getTag() == null ? 0 : AndroidUtilities.dp(135) + AndroidUtilities.dp(12);
        showAcceptDeclineView(showAcceptDeclineView, animated);
        windowView.setLockOnScreen(lockOnScreen || deviceIsLocked);
        canHideUI = (currentState == VoIPService.STATE_ESTABLISHED) && (currentUserIsVideo || callingUserIsVideo);
        if (!canHideUI && !uiVisible) {
            showUi(true);
        }

        if (uiVisible && canHideUI && !hideUiRunnableWaiting && service != null && !service.isMicMute()) {
            AndroidUtilities.runOnUIThread(hideUIRunnable, 3000);
            hideUiRunnableWaiting = true;
        } else if (service != null && service.isMicMute()) {
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
        }
        if (!uiVisible) {
            statusLayoutOffset -= AndroidUtilities.dp(50);
        }

        if (animated) {
            if (lockOnScreen || !uiVisible) {
                if (backIcon.getVisibility() != View.VISIBLE) {
                    backIcon.setVisibility(View.VISIBLE);
                    backIcon.setAlpha(0f);
                }
                backIcon.animate().alpha(0f).start();
            } else {
                backIcon.animate().alpha(1f).start();
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

        if (animated) {
            if (statusLayoutOffset != statusLayoutAnimateToOffset) {
                statusLayout.animate().translationY(statusLayoutOffset).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        } else {
            statusLayout.setTranslationY(statusLayoutOffset);
        }
        statusLayoutAnimateToOffset = statusLayoutOffset;
        overlayBackground.setShowBlackout(currentUserIsVideo || callingUserIsVideo, animated);
        canSwitchToPip = (currentState != VoIPService.STATE_ENDED && currentState != VoIPService.STATE_BUSY) && (currentUserIsVideo || callingUserIsVideo);

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
            if ((currentUserIsVideo || callingUserIsVideo) && (currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING) && service.getCallDuration() > 500) {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(R.drawable.calls_mute_mini, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                if (service.getRemoteVideoState() == Instance.VIDEO_STATE_INACTIVE) {
                    notificationsLayout.addNotification(R.drawable.calls_camera_mini, LocaleController.formatString("VoipUserCameraIsOff", R.string.VoipUserCameraIsOff, UserObject.getFirstName(callingUser)), "video", animated);
                } else {
                    notificationsLayout.removeNotification("video");
                }
            } else {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(R.drawable.calls_mute_mini, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                notificationsLayout.removeNotification("video");
            }

            if (notificationsLayout.getChildCount() == 0 && callingUserIsVideo && service.privateCall != null && !service.privateCall.video && !service.sharedUIParams.tapToVideoTooltipWasShowed) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
                tapToVideoTooltip.showForView(bottomButtons[1], true);
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
            callingUserMiniFloatingLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setStartDelay(150).start();
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
    }

    private void fillNavigationBar(boolean fill, boolean animated) {
        if (switchingToPip) {
            return;
        }
        if (!animated) {
            if (naviagtionBarAnimator != null) {
                naviagtionBarAnimator.cancel();
            }
            fillNaviagtionBarValue = fill ? 1 : 0;
            overlayBottomPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * (fill ? 1f : 0.5f))));
        } else if (fill != fillNaviagtionBar) {
            if (naviagtionBarAnimator != null) {
                naviagtionBarAnimator.cancel();
            }
            naviagtionBarAnimator = ValueAnimator.ofFloat(fillNaviagtionBarValue, fill ? 1 : 0);
            naviagtionBarAnimator.addUpdateListener(navigationBarAnimationListener);
            naviagtionBarAnimator.setDuration(300);
            naviagtionBarAnimator.setInterpolator(new LinearInterpolator());
            naviagtionBarAnimator.start();
        }
        fillNaviagtionBar = fill;
    }

    private void showUi(boolean show) {
        if (uiVisibilityAnimator != null) {
            uiVisibilityAnimator.cancel();
        }

        if (!show && uiVisible) {
            speakerPhoneIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiLayout.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusLayout.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.animate().alpha(0).translationY(AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            bottomShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            topShadow.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            uiVisibilityAnimator = ValueAnimator.ofFloat(uiVisibilityAlpha, 0);
            uiVisibilityAnimator.addUpdateListener(statusbarAnimatorListener);
            uiVisibilityAnimator.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
            uiVisibilityAnimator.start();
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUiRunnableWaiting = false;
            buttonsLayout.setEnabled(false);
        } else if (show && !uiVisible) {
            tapToVideoTooltip.hide();
            speakerPhoneIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            emojiLayout.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            statusLayout.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
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
        notificationsLayout.animate().translationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(80) : 0)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
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
            boolean swtichToFloatAnimated = animated;
            if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                swtichToFloatAnimated = false;
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
            }
            currentUserCameraFloatingLayout.setFloatingMode(state == STATE_FLOATING, swtichToFloatAnimated);
        }
        currentUserCameraFloatingLayout.setTag(state);
    }

    private void showCallingUserAvatarMini(boolean show, boolean animated) {
        if (animated) {
            if (show && callingUserPhotoViewMini.getTag() == null) {
                callingUserPhotoViewMini.animate().setListener(null).cancel();
                callingUserPhotoViewMini.setVisibility(View.VISIBLE);
                callingUserPhotoViewMini.setAlpha(0);
                callingUserPhotoViewMini.setTranslationY(-AndroidUtilities.dp(135));
                callingUserPhotoViewMini.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            } else if (!show && callingUserPhotoViewMini.getTag() != null) {
                callingUserPhotoViewMini.animate().setListener(null).cancel();
                callingUserPhotoViewMini.animate().alpha(0).translationY(-AndroidUtilities.dp(135)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
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
            callingUserPhotoViewMini.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        callingUserPhotoViewMini.setTag(show ? 1 : null);
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
        for (int i = 0; i < 4; i++) {
            Emoji.preloadEmoji(emoji[i]);
            Emoji.EmojiDrawable drawable = Emoji.getEmojiDrawable(emoji[i]);
            if (drawable != null) {
                drawable.setBounds(0, 0, AndroidUtilities.dp(22), AndroidUtilities.dp(22));
                drawable.preload();
                emojiViews[i].setImageDrawable(drawable);
                emojiViews[i].setContentDescription(emoji[i]);
                emojiViews[i].setVisibility(View.GONE);
            }
            emojiDrawables[i] = drawable;
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
                        emojiViews[i].setTranslationY(AndroidUtilities.dp(30));
                        emojiViews[i].animate().alpha(1f).translationY(0f).setDuration(200).setStartDelay(20 * i).start();
                    }
                }
            }
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
                    ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, AndroidUtilities.dp(100), 0);
                    if (view instanceof VoIPToggleButton) {
                        view.setTranslationY(AndroidUtilities.dp(100));
                        animator.setStartDelay(((VoIPToggleButton) view).animationDelay);
                    }
                    return animator;
                }

                @Override
                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.getTranslationY(), AndroidUtilities.dp(100));
                }
            };
            transitionSet
                    .addTransition(visibility.setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT))
                    .addTransition(new ChangeBounds().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT));
            transitionSet.excludeChildren(VoIPToggleButton.class, true);
            TransitionManager.beginDelayedTransition(buttonsLayout, transitionSet);
        }

        if (currentState == VoIPService.STATE_WAITING_INCOMING || currentState == VoIPService.STATE_BUSY) {
            if (service.privateCall != null && service.privateCall.video && currentState == VoIPService.STATE_WAITING_INCOMING) {
                if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                    setFrontalCameraAction(bottomButtons[0], service, animated);
                    if (uiVisible) {
                        speakerPhoneIcon.animate().alpha(1f).start();
                    }
                } else {
                    setSpeakerPhoneAction(bottomButtons[0], service, animated);
                    speakerPhoneIcon.animate().alpha(0).start();
                }
                setVideoAction(bottomButtons[1], service, animated);
                setMicrohoneAction(bottomButtons[2], service, animated);
            } else {
                bottomButtons[0].setVisibility(View.GONE);
                bottomButtons[1].setVisibility(View.GONE);
                bottomButtons[2].setVisibility(View.GONE);
            }
            bottomButtons[3].setVisibility(View.GONE);
        } else {
            if (instance == null) {
                return;
            }
            if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                setFrontalCameraAction(bottomButtons[0], service, animated);
                if (uiVisible) {
                    speakerPhoneIcon.setTag(1);
                    speakerPhoneIcon.animate().alpha(1f).start();
                }
            } else {
                setSpeakerPhoneAction(bottomButtons[0], service, animated);
                speakerPhoneIcon.setTag(null);
                speakerPhoneIcon.animate().alpha(0f).start();
            }
            setVideoAction(bottomButtons[1], service, animated);
            setMicrohoneAction(bottomButtons[2], service, animated);

            bottomButtons[3].setData(R.drawable.calls_decline, Color.WHITE, 0xFFF01D2C, LocaleController.getString("VoipEndCall", R.string.VoipEndCall), false, animated);
            bottomButtons[3].setOnClickListener(view -> {
                if (VoIPService.getSharedInstance() != null) {
                    VoIPService.getSharedInstance().hangUp();
                }
            });
        }

        int animationDelay = 0;
        for (int i = 0; i < 4; i++) {
            if (bottomButtons[i].getVisibility() == View.VISIBLE) {
                bottomButtons[i].animationDelay = animationDelay;
                animationDelay += 16;
            }
        }
        updateSpeakerPhoneIcon();
    }

    private void setMicrohoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (service.isMicMute()) {
            bottomButton.setData(R.drawable.calls_unmute, Color.BLACK, Color.WHITE, LocaleController.getString("VoipUnmute", R.string.VoipUnmute), true, animated);
        } else {
            bottomButton.setData(R.drawable.calls_unmute, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipMute", R.string.VoipMute), false, animated);
        }
        currentUserCameraFloatingLayout.setMuted(service.isMicMute(), animated);
        bottomButton.setOnClickListener(view -> {
            final VoIPService serviceInstance = VoIPService.getSharedInstance();
            if (serviceInstance != null) {
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

    private void setVideoAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        boolean isVideoAvailable;
        if (currentUserIsVideo || callingUserIsVideo) {
            isVideoAvailable = true;
        } else {
            isVideoAvailable = service.isVideoAvailable();
        }
        if (isVideoAvailable) {
            if (currentUserIsVideo) {
                bottomButton.setData(service.isScreencast() ? R.drawable.calls_sharescreen : R.drawable.calls_video, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipStopVideo", R.string.VoipStopVideo), false, animated);
            } else {
                bottomButton.setData(R.drawable.calls_video, Color.BLACK, Color.WHITE, LocaleController.getString("VoipStartVideo", R.string.VoipStartVideo), true, animated);
            }
            bottomButton.setCrossOffset(-AndroidUtilities.dpf2(3.5f));
            bottomButton.setOnClickListener(view -> {
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
            bottomButton.setData(R.drawable.calls_video, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), "Video", false, animated);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        }
    }

    private void updateSpeakerPhoneIcon() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (service.isBluetoothOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_bluetooth);
        } else if (service.isSpeakerphoneOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_speaker);
        } else {
            if (service.isHeadsetPlugged()) {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_headset);
            } else {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_phone);
            }
        }
    }

    private void setSpeakerPhoneAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (service.isBluetoothOn()) {
            bottomButton.setData(R.drawable.calls_bluetooth, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth), false, animated);
            bottomButton.setChecked(false, animated);
        } else if (service.isSpeakerphoneOn()) {
            bottomButton.setData(R.drawable.calls_speaker, Color.BLACK, Color.WHITE, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), false, animated);
            bottomButton.setChecked(true, animated);
        } else {
            bottomButton.setData(R.drawable.calls_speaker, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), false, animated);
            bottomButton.setChecked(false, animated);
        }
        bottomButton.setCheckableForAccessibility(true);
        bottomButton.setEnabled(true);
        bottomButton.setOnClickListener(view -> {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
            }
        });
    }

    private void setFrontalCameraAction(VoIPToggleButton bottomButton, VoIPService service, boolean animated) {
        if (!currentUserIsVideo) {
            bottomButton.setData(R.drawable.calls_flip,  ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.5f)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipFlip", R.string.VoipFlip), false, animated);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
        } else {
            bottomButton.setEnabled(true);
            if (!service.isFrontFaceCamera()) {
                bottomButton.setData(R.drawable.calls_flip, Color.BLACK, Color.WHITE, LocaleController.getString("VoipFlip", R.string.VoipFlip), false, animated);
            } else {
                bottomButton.setData(R.drawable.calls_flip, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.12f)), LocaleController.getString("VoipFlip", R.string.VoipFlip), false, animated);
            }

            bottomButton.setOnClickListener(view -> {
                final VoIPService serviceInstance = VoIPService.getSharedInstance();
                if (serviceInstance != null) {
                    if (accessibilityManager.isTouchExplorationEnabled()) {
                        final String text;
                        if (service.isFrontFaceCamera()) {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToBack", R.string.AccDescrVoipCamSwitchedToBack);
                        } else {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToFront", R.string.AccDescrVoipCamSwitchedToFront);
                        }
                        view.announceForAccessibility(text);
                    }
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
                        previewDialog = new PrivateVideoPreviewDialog(fragmentView.getContext(), false, true) {
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
                                    }
                                } else {
                                    if (service != null) {
                                        service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                    }
                                }
                                previousState = currentState;
                                updateViewState();
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
                VoIPService.getSharedInstance().acceptIncomingCall();
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
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
}
