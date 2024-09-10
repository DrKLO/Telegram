/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceArrows;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.tasks.Task;
import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.IntegrityTokenResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AuthTokensHelper;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.CallReceiver;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedPhoneNumberEditText;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPhoneKeyboardView;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.LoginOrView;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.ProxyDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SlideView;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TextViewSwitcher;
import org.telegram.ui.Components.TransformableLoginButtonView;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

@SuppressLint("HardwareIds")
public class LoginActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    public final static boolean ENABLE_PASTED_TEXT_PROCESSING = false;
    private final static int SHOW_DELAY = SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_AVERAGE ? 150 : 100;

    public final static int AUTH_TYPE_MESSAGE = 1,
            AUTH_TYPE_SMS = 2,
            AUTH_TYPE_FLASH_CALL = 3,
            AUTH_TYPE_CALL = 4,
            AUTH_TYPE_MISSED_CALL = 11,
            AUTH_TYPE_FRAGMENT_SMS = 15,
            AUTH_TYPE_WORD = 16,
            AUTH_TYPE_PHRASE = 17;

    private final static int MODE_LOGIN = 0,
            MODE_CANCEL_ACCOUNT_DELETION = 1,
            MODE_CHANGE_PHONE_NUMBER = 2,
            MODE_CHANGE_LOGIN_EMAIL = 3,
            MODE_BALANCE_PASSWORD = 4;

    private final static int VIEW_PHONE_INPUT = 0,
            VIEW_CODE_MESSAGE = 1,
            VIEW_CODE_SMS = 2,
            VIEW_CODE_FLASH_CALL = 3,
            VIEW_CODE_CALL = 4,
            VIEW_REGISTER = 5,
            VIEW_PASSWORD = 6,
            VIEW_RECOVER = 7,
            VIEW_RESET_WAIT = 8,
            VIEW_NEW_PASSWORD_STAGE_1 = 9,
            VIEW_NEW_PASSWORD_STAGE_2 = 10,
            VIEW_CODE_MISSED_CALL = 11,
            VIEW_ADD_EMAIL = 12,
            VIEW_CODE_EMAIL_SETUP = 13,
            VIEW_CODE_EMAIL = 14,
            VIEW_CODE_FRAGMENT_SMS = 15,
            VIEW_CODE_WORD = 16,
            VIEW_CODE_PHRASE = 17;

    public final static int COUNTRY_STATE_NOT_SET_OR_VALID = 0,
            COUNTRY_STATE_EMPTY = 1,
            COUNTRY_STATE_INVALID = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            AUTH_TYPE_MESSAGE,
            AUTH_TYPE_SMS,
            AUTH_TYPE_FLASH_CALL,
            AUTH_TYPE_CALL,
            AUTH_TYPE_MISSED_CALL,
            AUTH_TYPE_FRAGMENT_SMS,
            AUTH_TYPE_PHRASE,
            AUTH_TYPE_WORD
    })
    public @interface AuthType {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            MODE_LOGIN,
            MODE_CANCEL_ACCOUNT_DELETION,
            MODE_CHANGE_PHONE_NUMBER,
            MODE_CHANGE_LOGIN_EMAIL,
            MODE_BALANCE_PASSWORD
    })
    public @interface ActivityMode {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            VIEW_PHONE_INPUT,
            VIEW_CODE_MESSAGE,
            VIEW_CODE_SMS,
            VIEW_CODE_FLASH_CALL,
            VIEW_CODE_CALL,
            VIEW_REGISTER,
            VIEW_PASSWORD,
            VIEW_RECOVER,
            VIEW_RESET_WAIT,
            VIEW_NEW_PASSWORD_STAGE_1,
            VIEW_NEW_PASSWORD_STAGE_2,
            VIEW_CODE_MISSED_CALL,
            VIEW_ADD_EMAIL,
            VIEW_CODE_EMAIL_SETUP,
            VIEW_CODE_EMAIL,
            VIEW_CODE_FRAGMENT_SMS,
            VIEW_CODE_WORD,
            VIEW_CODE_PHRASE
    })
    private @interface ViewNumber {}

    @IntDef({
            COUNTRY_STATE_NOT_SET_OR_VALID,
            COUNTRY_STATE_EMPTY,
            COUNTRY_STATE_INVALID
    })
    private @interface CountryState {}

    @ViewNumber
    private int currentViewNum;
    private SlideView[] views = new SlideView[18];
    private CustomPhoneKeyboardView keyboardView;
    private ValueAnimator keyboardAnimator;

    private boolean restoringState;

    private Dialog permissionsDialog;
    private Dialog permissionsShowDialog;
    private ArrayList<String> permissionsItems = new ArrayList<>();
    private ArrayList<String> permissionsShowItems = new ArrayList<>();
    private boolean checkPermissions = true;
    private boolean checkShowPermissions = true;
    private boolean newAccount;
    private boolean syncContacts = true;
    private boolean testBackend = false;

    @ActivityMode
    private int activityMode = MODE_LOGIN;

    private String cancelDeletionPhone;
    private Bundle cancelDeletionParams;
    private TLRPC.TL_auth_sentCode cancelDeletionCode;

    private int currentDoneType;
    private AnimatorSet[] showDoneAnimation = new AnimatorSet[2];
    private AnimatorSet doneItemAnimation;
    private TransformableLoginButtonView floatingButtonIcon;
    private FrameLayout floatingButtonContainer;
    private VerticalPositionAutoAnimator floatingAutoAnimator;
    private RadialProgressView floatingProgressView;
    private int progressRequestId;
    private boolean[] doneButtonVisible = new boolean[] {true, false};

    private AlertDialog cancelDeleteProgressDialog;

    private SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private Runnable keyboardHideCallback;

    private ImageView backButtonView;
    private RadialProgressView radialProgressView;

    private ImageView proxyButtonView;
    private ProxyDrawable proxyDrawable;

    // Open animation stuff
    private LinearLayout keyboardLinearLayout;
    private FrameLayout slideViewsContainer;
    private View introView;
    private TextView startMessagingButton;

    private boolean customKeyboardWasVisible = false;

    private boolean isAnimatingIntro;
    private Runnable animationFinishCallback;

    private PhoneNumberConfirmView phoneNumberConfirmView;

    private static final int DONE_TYPE_FLOATING = 0;
    private static final int DONE_TYPE_ACTION = 1;

    private final static int done_button = 1;

    private boolean needRequestPermissions;

    private Runnable emailChangeFinishCallback;

    private boolean[] doneProgressVisible = new boolean[2];
    private Runnable[] editDoneCallback = new Runnable[2];
    private boolean[] postedEditDoneCallback = new boolean[2];

    private boolean forceDisableSafetyNet;

    private static class ProgressView extends View {

        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final RectF boundsRect = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

        private long startTime;
        private long duration;
        private boolean animating;

        private float radius;

        public ProgressView(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_login_progressInner));
            paint2.setColor(Theme.getColor(Theme.key_login_progressOuter));
        }

        public void startProgressAnimation(long duration) {
            this.animating = true;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            invalidate();
        }

        public void resetProgressAnimation() {
            duration = 0;
            startTime = 0;
            animating = false;
            invalidate();
        }

        public boolean isProgressAnimationRunning() {
            return animating;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            path.rewind();
            radius = h / 2f;
            boundsRect.set(0, 0, w, h);
            rect.set(boundsRect);
            path.addRoundRect(boundsRect, radius, radius, Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float progress;
            if (duration > 0) {
                progress = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) duration);
            } else {
                progress = 0f;
            }

            canvas.clipPath(path);
            canvas.drawRoundRect(boundsRect, radius, radius, paint);
            rect.right = boundsRect.right * progress;
            canvas.drawRoundRect(rect, radius, radius, paint2);

            if (animating &= duration > 0 && progress < 1f) {
                postInvalidateOnAnimation();
            }
        }
    }

    public LoginActivity() {
        super();
    }

    public LoginActivity(int account) {
        super();
        currentAccount = account;
        newAccount = true;
    }

    public LoginActivity changeEmail(Runnable onFinishCallback) {
        activityMode = MODE_CHANGE_LOGIN_EMAIL;
        currentViewNum = VIEW_ADD_EMAIL;
        emailChangeFinishCallback = onFinishCallback;
        return this;
    }

    public LoginActivity cancelAccountDeletion(String phone, Bundle params, TLRPC.TL_auth_sentCode sentCode) {
        cancelDeletionPhone = phone;
        cancelDeletionParams = params;
        cancelDeletionCode = sentCode;
        activityMode = MODE_CANCEL_ACCOUNT_DELETION;
        return this;
    }

    private TLRPC.InputChannel channel;
    private TLRPC.TL_account_password currentPassword;
    private Utilities.Callback2<TL_stats.TL_broadcastRevenueWithdrawalUrl, TLRPC.TL_error> passwordFinishCallback;

    public LoginActivity promptPassword(TLRPC.TL_account_password currentPassword, TLRPC.InputChannel channel, Utilities.Callback2<TL_stats.TL_broadcastRevenueWithdrawalUrl, TLRPC.TL_error> callback) {
        activityMode = MODE_BALANCE_PASSWORD;
        currentViewNum = VIEW_PASSWORD;
        this.channel = channel;
        this.currentPassword = currentPassword;
        passwordFinishCallback = callback;
        return this;
    }

    public LoginActivity changePhoneNumber() {
        activityMode = MODE_CHANGE_PHONE_NUMBER;
        return this;
    }

    private boolean isInCancelAccountDeletionMode() {
        return activityMode == MODE_CANCEL_ACCOUNT_DELETION;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        for (int a = 0; a < views.length; a++) {
            if (views[a] != null) {
                views[a].onDestroyActivity();
            }
        }
        if (cancelDeleteProgressDialog != null) {
            cancelDeleteProgressDialog.dismiss();
            cancelDeleteProgressDialog = null;
        }
        for (Runnable callback : editDoneCallback) {
            if (callback != null) {
                AndroidUtilities.cancelRunOnUIThread(callback);
            }
        }
        getNotificationCenter().removeObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.didUpdateConnectionState);
        return super.onFragmentCreate();
    }

    private View cachedFragmentView;
    @Override
    public View createView(Context context) {
        if (cachedFragmentView != null) {
            fragmentView = cachedFragmentView;
            cachedFragmentView = null;
            return fragmentView;
        }

        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == done_button) {
                    onDoneButtonPressed();
                } else if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                }
            }
        });

        currentDoneType = DONE_TYPE_FLOATING;
        doneButtonVisible[DONE_TYPE_FLOATING] = true;
        doneButtonVisible[DONE_TYPE_ACTION] = false;

        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                MarginLayoutParams marginLayoutParams = (MarginLayoutParams) floatingButtonContainer.getLayoutParams();
                int keyboardOffset = isCustomKeyboardVisible() ? AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) : 0;
                if (isCustomKeyboardVisible() && measureKeyboardHeight() > AndroidUtilities.dp(20)) {
                    keyboardOffset -= measureKeyboardHeight();
                }
                if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().isShowing()) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    marginLayoutParams.bottomMargin = AndroidUtilities.dp(16) + Bulletin.getVisibleBulletin().getLayout().getMeasuredHeight() - AndroidUtilities.dp(10) + keyboardOffset;
                } else {
                    marginLayoutParams.bottomMargin = AndroidUtilities.dp(16) + keyboardOffset;
                }

                int statusBarHeight = AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight;
                marginLayoutParams = (MarginLayoutParams) backButtonView.getLayoutParams();
                marginLayoutParams.topMargin = AndroidUtilities.dp(16) + statusBarHeight;

                marginLayoutParams = (MarginLayoutParams) proxyButtonView.getLayoutParams();
                marginLayoutParams.topMargin = AndroidUtilities.dp(16) + statusBarHeight;

                marginLayoutParams = (MarginLayoutParams) radialProgressView.getLayoutParams();
                marginLayoutParams.topMargin = AndroidUtilities.dp(16) + statusBarHeight;

                if (measureKeyboardHeight() > AndroidUtilities.dp(20) && keyboardView.getVisibility() != GONE && !isCustomKeyboardForceDisabled() && !customKeyboardWasVisible) {
                    if (keyboardAnimator != null) {
                        keyboardAnimator.cancel();
                    }
                    keyboardView.setVisibility(View.GONE);
                }

                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        sizeNotifierFrameLayout.setDelegate((keyboardHeight, isWidthGreater) -> {
            if (keyboardHeight > AndroidUtilities.dp(20) && isCustomKeyboardVisible()) {
                AndroidUtilities.hideKeyboard(fragmentView);
            }
            if (keyboardHeight <= AndroidUtilities.dp(20) && keyboardHideCallback != null) {
                keyboardHideCallback.run();
                keyboardHideCallback = null;
            }
        });
        fragmentView = sizeNotifierFrameLayout;

        ScrollView scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (currentViewNum == VIEW_CODE_MESSAGE || currentViewNum == VIEW_CODE_SMS || currentViewNum == VIEW_CODE_CALL) {
                    rectangle.bottom += AndroidUtilities.dp(40);
                }
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        scrollView.setFillViewport(true);
        sizeNotifierFrameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        keyboardLinearLayout = new LinearLayout(context);
        keyboardLinearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(keyboardLinearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        Space spacer = new Space(context);
        spacer.setMinimumHeight(AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight);
        keyboardLinearLayout.addView(spacer);
        slideViewsContainer = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                for (SlideView slideView : views) {
                    MarginLayoutParams params = (MarginLayoutParams) slideView.getLayoutParams();
                    int childBottom = getHeight() + AndroidUtilities.dp(16);
                    if (!slideView.hasCustomKeyboard() && keyboardView.getVisibility() == VISIBLE) {
                        childBottom += AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP);
                    }
                    slideView.layout(params.leftMargin, params.topMargin, getWidth() - params.rightMargin, childBottom);
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int width = getMeasuredWidth(), height = getMeasuredHeight();

                for (SlideView slideView : views) {
                    MarginLayoutParams params = (MarginLayoutParams) slideView.getLayoutParams();
                    int childHeight = height - params.topMargin + AndroidUtilities.dp(16);
                    if (!slideView.hasCustomKeyboard() && keyboardView.getVisibility() == VISIBLE) {
                        childHeight += AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP);
                    }
                    slideView.measure(MeasureSpec.makeMeasureSpec(width - params.rightMargin - params.leftMargin, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY));
                }
            }
        };
        keyboardLinearLayout.addView(slideViewsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        keyboardView = new CustomPhoneKeyboardView(context);
        keyboardView.setViewToFindFocus(slideViewsContainer);
        keyboardLinearLayout.addView(keyboardView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));

        views[VIEW_PHONE_INPUT] = new PhoneView(context);
        views[VIEW_CODE_MESSAGE] = new LoginActivitySmsView(context, AUTH_TYPE_MESSAGE);
        views[VIEW_CODE_SMS] = new LoginActivitySmsView(context, AUTH_TYPE_SMS);
        views[VIEW_CODE_FLASH_CALL] = new LoginActivitySmsView(context, AUTH_TYPE_FLASH_CALL);
        views[VIEW_CODE_CALL] = new LoginActivitySmsView(context, AUTH_TYPE_CALL);
        views[VIEW_REGISTER] = new LoginActivityRegisterView(context);
        views[VIEW_PASSWORD] = new LoginActivityPasswordView(context);
        views[VIEW_RECOVER] = new LoginActivityRecoverView(context);
        views[VIEW_RESET_WAIT] = new LoginActivityResetWaitView(context);
        views[VIEW_NEW_PASSWORD_STAGE_1] = new LoginActivityNewPasswordView(context, 0);
        views[VIEW_NEW_PASSWORD_STAGE_2] = new LoginActivityNewPasswordView(context, 1);
        views[VIEW_CODE_MISSED_CALL] = new LoginActivitySmsView(context, AUTH_TYPE_MISSED_CALL);
        views[VIEW_ADD_EMAIL] = new LoginActivitySetupEmail(context);
        views[VIEW_CODE_EMAIL_SETUP] = new LoginActivityEmailCodeView(context, true);
        views[VIEW_CODE_EMAIL] = new LoginActivityEmailCodeView(context, false);
        views[VIEW_CODE_FRAGMENT_SMS] = new LoginActivitySmsView(context, AUTH_TYPE_FRAGMENT_SMS);
        views[VIEW_CODE_WORD] = new LoginActivityPhraseView(context, AUTH_TYPE_WORD);
        views[VIEW_CODE_PHRASE] = new LoginActivityPhraseView(context, AUTH_TYPE_PHRASE);

        for (int a = 0; a < views.length; a++) {
            views[a].setVisibility(a == 0 ? View.VISIBLE : View.GONE);
            slideViewsContainer.addView(views[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, AndroidUtilities.isTablet() ? 26 : 18, 30, AndroidUtilities.isTablet() ? 26 : 18, 0));
        }

        Bundle savedInstanceState = activityMode == MODE_LOGIN ? loadCurrentState(newAccount, currentAccount) : null;
        if (savedInstanceState != null) {
            currentViewNum = savedInstanceState.getInt("currentViewNum", 0);
            syncContacts = savedInstanceState.getInt("syncContacts", 1) == 1;
            if (currentViewNum >= VIEW_CODE_MESSAGE && currentViewNum <= VIEW_CODE_CALL) {
                int time = savedInstanceState.getInt("open");
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    currentViewNum = VIEW_PHONE_INPUT;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            } else if (currentViewNum == VIEW_PASSWORD) {
                LoginActivityPasswordView view = (LoginActivityPasswordView) views[VIEW_PASSWORD];
                if (view.currentPassword == null) {
                    currentViewNum = VIEW_PHONE_INPUT;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            } else if (currentViewNum == VIEW_RECOVER) {
                LoginActivityRecoverView view = (LoginActivityRecoverView) views[VIEW_RECOVER];
                if (view.passwordString == null) {
                    currentViewNum = VIEW_PHONE_INPUT;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            }
        }

        floatingButtonContainer = new FrameLayout(context);
        floatingButtonContainer.setVisibility(doneButtonVisible[DONE_TYPE_FLOATING] ? View.VISIBLE : View.GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButtonContainer.setStateListAnimator(animator);
            floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        floatingAutoAnimator = VerticalPositionAutoAnimator.attach(floatingButtonContainer);
        sizeNotifierFrameLayout.addView(floatingButtonContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 24, 16));
//        ScaleStateListAnimator.apply(floatingButtonContainer, .1f, 1.4f);
        floatingButtonContainer.setOnClickListener(view -> onDoneButtonPressed());
        floatingAutoAnimator.addUpdateListener((animation, value, velocity) -> {
            if (phoneNumberConfirmView != null) {
                phoneNumberConfirmView.updateFabPosition();
            }
        });

        backButtonView = new ImageView(context);
        backButtonView.setImageResource(R.drawable.ic_ab_back);
        backButtonView.setOnClickListener(v -> {
            if (onBackPressed()) {
                finishFragment();
            }
        });
        backButtonView.setContentDescription(getString(R.string.Back));
        int padding = AndroidUtilities.dp(4);
        backButtonView.setPadding(padding, padding, padding, padding);
        sizeNotifierFrameLayout.addView(backButtonView, LayoutHelper.createFrame(32, 32, Gravity.LEFT | Gravity.TOP, 16, 16, 0, 0));

        proxyButtonView = new ImageView(context);
        proxyButtonView.setImageDrawable(proxyDrawable = new ProxyDrawable(context));
        proxyButtonView.setOnClickListener(v -> presentFragment(new ProxyListActivity()));
        proxyButtonView.setAlpha(0f);
        proxyButtonView.setVisibility(View.GONE);
        sizeNotifierFrameLayout.addView(proxyButtonView, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.TOP, 16, 16, 16, 16));
        updateProxyButton(false, true);

        radialProgressView = new RadialProgressView(context);
        radialProgressView.setSize(AndroidUtilities.dp(20));
        radialProgressView.setAlpha(0);
        radialProgressView.setScaleX(0.1f);
        radialProgressView.setScaleY(0.1f);
        sizeNotifierFrameLayout.addView(radialProgressView, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.TOP, 0, 16, 16, 0));

        floatingButtonIcon = new TransformableLoginButtonView(context);
        floatingButtonIcon.setTransformType(TransformableLoginButtonView.TRANSFORM_OPEN_ARROW);
        floatingButtonIcon.setProgress(1f);
        floatingButtonIcon.setDrawBackground(false);
        floatingButtonContainer.setContentDescription(getString("Done", R.string.Done));
        floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

        floatingProgressView = new RadialProgressView(context);
        floatingProgressView.setSize(AndroidUtilities.dp(22));
        floatingProgressView.setAlpha(0.0f);
        floatingProgressView.setScaleX(0.1f);
        floatingProgressView.setScaleY(0.1f);
        floatingProgressView.setVisibility(View.INVISIBLE);
        floatingButtonContainer.addView(floatingProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (savedInstanceState != null) {
            restoringState = true;
        }
        for (int a = 0; a < views.length; a++) {
            SlideView v = views[a];
            if (savedInstanceState != null) {
                if (a >= VIEW_CODE_MESSAGE && a <= VIEW_CODE_CALL) {
                    if (a == currentViewNum) {
                        v.restoreStateParams(savedInstanceState);
                    }
                } else {
                    v.restoreStateParams(savedInstanceState);
                }
            }
            if (currentViewNum == a) {
                backButtonView.setVisibility(v.needBackButton() || newAccount || activityMode == MODE_CHANGE_PHONE_NUMBER ? View.VISIBLE : View.GONE);
                v.setVisibility(View.VISIBLE);
                v.onShow();

                setCustomKeyboardVisible(v.hasCustomKeyboard(), false);

                currentDoneType = DONE_TYPE_FLOATING;
                boolean needFloatingButton = a == VIEW_PHONE_INPUT || a == VIEW_REGISTER ||
                        a == VIEW_PASSWORD || a == VIEW_NEW_PASSWORD_STAGE_1 || a == VIEW_NEW_PASSWORD_STAGE_2 ||
                        a == VIEW_ADD_EMAIL;
                showDoneButton(needFloatingButton, false);
                if (a == VIEW_CODE_MESSAGE || a == VIEW_CODE_SMS || a == VIEW_CODE_FLASH_CALL || a == VIEW_CODE_CALL) {
                    currentDoneType = DONE_TYPE_ACTION;
                }
            } else {
                if (v.getVisibility() != View.GONE) {
                    v.setVisibility(View.GONE);
                    v.onHide();
                }
            }
        }
        restoringState = false;

        updateColors();

        if (isInCancelAccountDeletionMode()) {
            fillNextCodeParams(cancelDeletionParams, cancelDeletionCode, false);
        }

        return fragmentView;
    }

    private boolean isCustomKeyboardForceDisabled() {
        return AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y || AndroidUtilities.isTablet() || AndroidUtilities.isAccessibilityTouchExplorationEnabled();
    }

    private boolean isCustomKeyboardVisible() {
        return views[currentViewNum].hasCustomKeyboard() && !isCustomKeyboardForceDisabled();
    }

    private void setCustomKeyboardVisible(boolean visible, boolean animate) {
        if (customKeyboardWasVisible == visible && animate) return;
        customKeyboardWasVisible = visible;

        if (isCustomKeyboardForceDisabled()) {
            visible = false;
        }

        if (visible) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
            if (animate) {
                keyboardAnimator = ValueAnimator.ofFloat(0, 1).setDuration(300);
                keyboardAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                keyboardAnimator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    keyboardView.setAlpha(val);
                    keyboardView.setTranslationY((1f - val) * AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                });
                keyboardAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        keyboardView.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (keyboardAnimator == animation) {
                            keyboardAnimator = null;
                        }
                    }
                });
                keyboardAnimator.start();
            } else {
                keyboardView.setVisibility(View.VISIBLE);
            }
        } else {
            AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
            if (animate) {
                keyboardAnimator = ValueAnimator.ofFloat(1, 0).setDuration(300);
                keyboardAnimator.setInterpolator(Easings.easeInOutQuad);
                keyboardAnimator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    keyboardView.setAlpha(val);
                    keyboardView.setTranslationY((1f - val) * AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP));
                });
                keyboardAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        keyboardView.setVisibility(View.GONE);

                        if (keyboardAnimator == animation) {
                            keyboardAnimator = null;
                        }
                    }
                });
                keyboardAnimator.start();
            } else {
                keyboardView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (newAccount) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
        }
        AndroidUtilities.removeAltFocusable(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (newAccount) {
            ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (fragmentView != null) {
            fragmentView.requestLayout();
        }
        try {
            if (currentViewNum >= VIEW_CODE_MESSAGE && currentViewNum <= VIEW_CODE_CALL && views[currentViewNum] instanceof LoginActivitySmsView) {
                int time = ((LoginActivitySmsView) views[currentViewNum]).openTime;
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    views[currentViewNum].onBackPressed(true);
                    setPage(VIEW_PHONE_INPUT, false, null, true);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (currentViewNum == VIEW_PHONE_INPUT && !needRequestPermissions) {
            SlideView view = views[currentViewNum];
            if (view != null) {
                view.onShow();
            }
        }

        if (isCustomKeyboardVisible()) {
            AndroidUtilities.hideKeyboard(fragmentView);
            AndroidUtilities.requestAltFocusable(getParentActivity(), classGuid);
        }

        if (currentViewNum >= 0 && currentViewNum < views.length) {
            views[currentViewNum].onResume();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setCustomKeyboardVisible(views[currentViewNum].hasCustomKeyboard(), false);
        if (phoneNumberConfirmView != null) {
            phoneNumberConfirmView.dismiss();
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length == 0 || grantResults.length == 0) return;

        boolean granted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == 6) {
            checkPermissions = false;
            if (currentViewNum == VIEW_PHONE_INPUT) {
                ((PhoneView)views[currentViewNum]).confirmedNumber = true;
                views[currentViewNum].onNextPressed(null);
            }
        } else if (requestCode == BasePermissionsActivity.REQUEST_CODE_CALLS) {
            checkShowPermissions = false;
            if (currentViewNum == VIEW_PHONE_INPUT) {
                ((PhoneView) views[currentViewNum]).fillNumber();
            }
        } else if (requestCode == BasePermissionsActivity.REQUEST_CODE_OPEN_CAMERA) {
            if (granted) {
                LoginActivityRegisterView registerView = (LoginActivityRegisterView) views[VIEW_REGISTER];
                registerView.imageUpdater.openCamera();
            }
        } else if (requestCode == BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE_FOR_AVATAR) {
            if (granted) {
                LoginActivityRegisterView registerView = (LoginActivityRegisterView) views[VIEW_REGISTER];
                registerView.post(() -> registerView.imageUpdater.openGallery());
            }
        }
    }

    public static Bundle loadCurrentState(boolean newAccount, int currentAccount) {
        try {
            Bundle bundle = new Bundle();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2" + (newAccount ? "_" + currentAccount : ""), Context.MODE_PRIVATE);
            Map<String, ?> params = preferences.getAll();
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String[] args = key.split("_\\|_");
                if (args.length == 1) {
                    if (value instanceof String) {
                        bundle.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        bundle.putInt(key, (Integer) value);
                    } else if (value instanceof Boolean) {
                        bundle.putBoolean(key, (Boolean) value);
                    }
                } else if (args.length == 2) {
                    Bundle inner = bundle.getBundle(args[0]);
                    if (inner == null) {
                        inner = new Bundle();
                        bundle.putBundle(args[0], inner);
                    }
                    if (value instanceof String) {
                        inner.putString(args[1], (String) value);
                    } else if (value instanceof Integer) {
                        inner.putInt(args[1], (Integer) value);
                    } else if (value instanceof Boolean) {
                        inner.putBoolean(args[1], (Boolean) value);
                    }
                }
            }
            return bundle;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void clearCurrentState() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2" + (newAccount ? "_" + currentAccount : ""), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }

    private void putBundleToEditor(Bundle bundle, SharedPreferences.Editor editor, String prefix) {
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object obj = bundle.get(key);
            if (obj instanceof String) {
                if (prefix != null) {
                    editor.putString(prefix + "_|_" + key, (String) obj);
                } else {
                    editor.putString(key, (String) obj);
                }
            } else if (obj instanceof Integer) {
                if (prefix != null) {
                    editor.putInt(prefix + "_|_" + key, (Integer) obj);
                } else {
                    editor.putInt(key, (Integer) obj);
                }
            } else if (obj instanceof Boolean) {
                if (prefix != null) {
                    editor.putBoolean(prefix + "_|_" + key, (Boolean) obj);
                } else {
                    editor.putBoolean(key, (Boolean) obj);
                }
            } else if (obj instanceof Bundle) {
                putBundleToEditor((Bundle) obj, editor, key);
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (dialog == permissionsDialog && !permissionsItems.isEmpty() && getParentActivity() != null) {
                try {
                    getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                } catch (Exception ignore) {

                }
            } else if (dialog == permissionsShowDialog && !permissionsShowItems.isEmpty() && getParentActivity() != null) {
                AndroidUtilities.runOnUIThread(() -> needRequestPermissions = false, 200);
                try {
                    getParentActivity().requestPermissions(permissionsShowItems.toArray(new String[0]), 7);
                } catch (Exception ignore) {

                }
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (currentViewNum == VIEW_PHONE_INPUT || activityMode == MODE_CHANGE_LOGIN_EMAIL && currentViewNum == VIEW_ADD_EMAIL || activityMode == MODE_BALANCE_PASSWORD && currentViewNum == VIEW_PASSWORD) {
            for (int a = 0; a < views.length; a++) {
                if (views[a] != null) {
                    views[a].onDestroyActivity();
                }
            }
            clearCurrentState();
            return true;
        } else if (currentViewNum == VIEW_PASSWORD) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_PHONE_INPUT, true, null, true);
        } else if (currentViewNum == VIEW_RECOVER || currentViewNum == VIEW_RESET_WAIT) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_PASSWORD, true, null, true);
        } else if ((currentViewNum >= VIEW_CODE_MESSAGE && currentViewNum <= VIEW_CODE_CALL) || currentViewNum == AUTH_TYPE_MISSED_CALL || currentViewNum == AUTH_TYPE_FRAGMENT_SMS) {
            if (views[currentViewNum].onBackPressed(false)) {
                setPage(VIEW_PHONE_INPUT, true, null, true);
            }
        } else if (currentViewNum == VIEW_REGISTER) {
            ((LoginActivityRegisterView) views[currentViewNum]).wrongNumber.callOnClick();
        } else if (currentViewNum == VIEW_NEW_PASSWORD_STAGE_1) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_RECOVER, true, null, true);
        } else if (currentViewNum == VIEW_NEW_PASSWORD_STAGE_2) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_NEW_PASSWORD_STAGE_1, true, null, true);
        } else if (currentViewNum == VIEW_CODE_EMAIL_SETUP) {
            views[currentViewNum].onBackPressed(true);
            setPage(VIEW_ADD_EMAIL, true, null, true);
        } else {
            if (views[currentViewNum].onBackPressed(true)) {
                setPage(VIEW_PHONE_INPUT, true, null, true);
            }
        }
        return false;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        LoginActivityRegisterView registerView = (LoginActivityRegisterView) views[VIEW_REGISTER];
        if (registerView != null) {
            registerView.imageUpdater.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void needShowAlert(String title, String text) {
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(text);
        builder.setPositiveButton(getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    private void onFieldError(View view, boolean allowErrorSelection) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        AndroidUtilities.shakeViewSpring(view, 3.5f);

        if (allowErrorSelection) {
            if (view instanceof OutlineTextContainerView) {
                Runnable callback = (Runnable) view.getTag(R.id.timeout_callback);
                if (callback != null) {
                    view.removeCallbacks(callback);
                }

                OutlineTextContainerView outlineTextContainerView = (OutlineTextContainerView) view;
                AtomicReference<Runnable> timeoutCallbackRef = new AtomicReference<>(); // We can't use timeoutCallback before declaration otherwise
                EditText editText = outlineTextContainerView.getAttachedEditText();
                TextWatcher textWatcher = new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        editText.post(()-> {
                            editText.removeTextChangedListener(this);
                            editText.removeCallbacks(timeoutCallbackRef.get());
                            timeoutCallbackRef.get().run();
                        });
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                };

                outlineTextContainerView.animateError(1f);
                Runnable timeoutCallback = () -> {
                    outlineTextContainerView.animateError(0f);
                    view.setTag(R.id.timeout_callback, null);
                    if (editText != null) {
                        editText.post(()-> editText.removeTextChangedListener(textWatcher));
                    }
                };
                timeoutCallbackRef.set(timeoutCallback);
                view.postDelayed(timeoutCallback, 2000);
                view.setTag(R.id.timeout_callback, timeoutCallback);

                if (editText != null) {
                    editText.addTextChangedListener(textWatcher);
                }
            }
        }
    }

    public static void needShowInvalidAlert(BaseFragment fragment, String phoneNumber, boolean banned) {
        needShowInvalidAlert(fragment, phoneNumber, null, banned);
    }

    public static void needShowInvalidAlert(BaseFragment fragment, String phoneNumber, PhoneInputData inputData, boolean banned) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        if (banned) {
            builder.setTitle(getString(R.string.RestorePasswordNoEmailTitle));
            builder.setMessage(getString("BannedPhoneNumber", R.string.BannedPhoneNumber));
        } else {
            if (inputData != null && inputData.patterns != null && !inputData.patterns.isEmpty() && inputData.country != null) {
                int patternLength = Integer.MAX_VALUE;
                for (String pattern : inputData.patterns) {
                    int length = pattern.replace(" ", "").length();
                    if (length < patternLength) {
                        patternLength = length;
                    }
                }
                if (PhoneFormat.stripExceptNumbers(phoneNumber).length() - inputData.country.code.length() < patternLength) {
                    builder.setTitle(getString(R.string.WrongNumberFormat));
                    builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ShortNumberInfo", R.string.ShortNumberInfo, inputData.country.name, inputData.phoneNumber)));
                } else {
                    builder.setTitle(getString(R.string.RestorePasswordNoEmailTitle));
                    builder.setMessage(getString(R.string.InvalidPhoneNumber));
                }
            } else {
                builder.setTitle(getString(R.string.RestorePasswordNoEmailTitle));
                builder.setMessage(getString(R.string.InvalidPhoneNumber));
            }
        }
        builder.setNeutralButton(getString("BotHelp", R.string.BotHelp), (dialog, which) -> {
            try {
                PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                Intent mailer = new Intent(Intent.ACTION_SENDTO);
                mailer.setData(Uri.parse("mailto:"));
                mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{banned ? "recover@telegram.org" : "login@stel.com"});
                if (banned) {
                    mailer.putExtra(Intent.EXTRA_SUBJECT, "Banned phone number: " + phoneNumber);
                    mailer.putExtra(Intent.EXTRA_TEXT, "I'm trying to use my mobile phone number: " + phoneNumber + "\nBut Telegram says it's banned. Please help.\n\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault());
                } else {
                    mailer.putExtra(Intent.EXTRA_SUBJECT, "Invalid phone number: " + phoneNumber);
                    mailer.putExtra(Intent.EXTRA_TEXT, "I'm trying to use my mobile phone number: " + phoneNumber + "\nBut Telegram says it's invalid. Please help.\n\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault());
                }
                fragment.getParentActivity().startActivity(Intent.createChooser(mailer, "Send email..."));
            } catch (Exception e) {
                AlertDialog.Builder builder2 = new AlertDialog.Builder(fragment.getParentActivity());
                builder2.setTitle(getString(R.string.RestorePasswordNoEmailTitle));
                builder2.setMessage(getString("NoMailInstalled", R.string.NoMailInstalled));
                builder2.setPositiveButton(getString("OK", R.string.OK), null);
                fragment.showDialog(builder2.create());
            }
        });
        builder.setPositiveButton(getString("OK", R.string.OK), null);
        fragment.showDialog(builder.create());
    }

    private void showDoneButton(boolean show, boolean animated) {
        boolean floating = currentDoneType == 0;
        if (doneButtonVisible[currentDoneType] == show) {
            return;
        }

        if (showDoneAnimation[currentDoneType] != null) {
            if (animated) {
                showDoneAnimation[currentDoneType].removeAllListeners();
            }
            showDoneAnimation[currentDoneType].cancel();
        }
        doneButtonVisible[currentDoneType] = show;
        if (animated) {
            showDoneAnimation[currentDoneType] = new AnimatorSet();
            if (show) {
                if (floating) {
                    if (floatingButtonContainer.getVisibility() != View.VISIBLE) {
                        floatingAutoAnimator.setOffsetY(AndroidUtilities.dpf2(70f));
                        floatingButtonContainer.setVisibility(View.VISIBLE);
                    }
                    ValueAnimator offsetAnimator = ValueAnimator.ofFloat(floatingAutoAnimator.getOffsetY(), 0);
                    offsetAnimator.addUpdateListener(animation -> {
                        float val = (Float) animation.getAnimatedValue();
                        floatingAutoAnimator.setOffsetY(val);
                        floatingButtonContainer.setAlpha(1f - (val / AndroidUtilities.dpf2(70f)));
                    });
                    showDoneAnimation[currentDoneType].play(offsetAnimator);
                }
            } else {
                if (floating) {
                    ValueAnimator offsetAnimator = ValueAnimator.ofFloat(floatingAutoAnimator.getOffsetY(), AndroidUtilities.dpf2(70f));
                    offsetAnimator.addUpdateListener(animation -> {
                        float val = (Float) animation.getAnimatedValue();
                        floatingAutoAnimator.setOffsetY(val);
                        floatingButtonContainer.setAlpha(1f - (val / AndroidUtilities.dpf2(70f)));
                    });
                    showDoneAnimation[currentDoneType].play(offsetAnimator);
                }
            }
            showDoneAnimation[currentDoneType].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (showDoneAnimation[floating ? 0 : 1] != null && showDoneAnimation[floating ? 0 : 1].equals(animation)) {
                        if (!show) {
                            if (floating) {
                                floatingButtonContainer.setVisibility(View.GONE);
                            }

                            if (floating && floatingButtonIcon.getAlpha() != 1f) {
                                floatingButtonIcon.setAlpha(1f);
                                floatingButtonIcon.setScaleX(1f);
                                floatingButtonIcon.setScaleY(1f);
                                floatingButtonIcon.setVisibility(View.VISIBLE);
                                floatingButtonContainer.setEnabled(true);
                                floatingProgressView.setAlpha(0f);
                                floatingProgressView.setScaleX(0.1f);
                                floatingProgressView.setScaleY(0.1f);
                                floatingProgressView.setVisibility(View.INVISIBLE);
                            }
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (showDoneAnimation[floating ? 0 : 1] != null && showDoneAnimation[floating ? 0 : 1].equals(animation)) {
                        showDoneAnimation[floating ? 0 : 1] = null;
                    }
                }
            });
            final int duration;
            final Interpolator interpolator;
            if (floating) {
                if (show) {
                    duration = 200;
                    interpolator = AndroidUtilities.decelerateInterpolator;
                } else {
                    duration = 150;
                    interpolator = AndroidUtilities.accelerateInterpolator;
                }
            } else {
                duration = 150;
                interpolator = null;
            }
            showDoneAnimation[currentDoneType].setDuration(duration);
            showDoneAnimation[currentDoneType].setInterpolator(interpolator);
            showDoneAnimation[currentDoneType].start();
        } else {
            if (show) {
                if (floating) {
                    floatingButtonContainer.setVisibility(View.VISIBLE);
                    floatingAutoAnimator.setOffsetY(0f);
                }
            } else {
                if (floating) {
                    floatingButtonContainer.setVisibility(View.GONE);
                    floatingAutoAnimator.setOffsetY(AndroidUtilities.dpf2(70f));
                }
            }
        }
    }

    private void onDoneButtonPressed() {
        if (!doneButtonVisible[currentDoneType]) {
            return;
        }
        if (radialProgressView.getTag() != null) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString("StopLoadingTitle", R.string.StopLoadingTitle));
            builder.setMessage(getString("StopLoading", R.string.StopLoading));
            builder.setPositiveButton(getString("WaitMore", R.string.WaitMore), null);
            builder.setNegativeButton(getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                views[currentViewNum].onCancelPressed();
                needHideProgress(true);
            });
            showDialog(builder.create());
        } else {
            views[currentViewNum].onNextPressed(null);
        }
    }

    private void showEditDoneProgress(boolean show, boolean animated) {
        showEditDoneProgress(show, animated, false);
    }

    private void showEditDoneProgress(boolean show, boolean animated, boolean fromCallback) {
        if (animated && doneProgressVisible[currentDoneType] == show && !fromCallback) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(() -> showEditDoneProgress(show, animated, fromCallback));
            return;
        }
        boolean floating = currentDoneType == DONE_TYPE_FLOATING;

        if (!fromCallback && !floating) {
            doneProgressVisible[currentDoneType] = show;
            int doneType = currentDoneType;
            if (animated) {
                if (postedEditDoneCallback[currentDoneType]) {
                    AndroidUtilities.cancelRunOnUIThread(editDoneCallback[currentDoneType]);
                    postedEditDoneCallback[currentDoneType] = false;
                    return;
                } else if (show) {
                    AndroidUtilities.runOnUIThread(editDoneCallback[currentDoneType] = () -> {
                        int type = currentDoneType;
                        currentDoneType = doneType;
                        showEditDoneProgress(show, animated, true);
                        currentDoneType = type;
                    }, 2000);
                    postedEditDoneCallback[currentDoneType] = true;
                    return;
                }
            }
        } else {
            postedEditDoneCallback[currentDoneType] = false;
            doneProgressVisible[currentDoneType] = show;
        }

        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }

        if (animated) {
            doneItemAnimation = new AnimatorSet();
            ValueAnimator animator = ValueAnimator.ofFloat(show ? 0 : 1, show ? 1 : 0);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (show) {
                        if (floating) {
                            floatingButtonIcon.setVisibility(View.VISIBLE);
                            floatingProgressView.setVisibility(View.VISIBLE);
                            floatingButtonContainer.setEnabled(false);
                        } else {
                            radialProgressView.setVisibility(View.VISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (floating) {
                        if (!show) {
                            floatingProgressView.setVisibility(View.INVISIBLE);
                            floatingButtonIcon.setVisibility(View.VISIBLE);
                            floatingButtonContainer.setEnabled(true);
                        } else {
                            floatingButtonIcon.setVisibility(View.INVISIBLE);
                            floatingProgressView.setVisibility(View.VISIBLE);
                        }
                    } else if (!show) {
                        radialProgressView.setVisibility(View.INVISIBLE);
                    }

                    if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                        doneItemAnimation = null;
                    }
                }
            });
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();

                if (floating) {
                    float scale = 0.1f + 0.9f * (1f - val);
                    floatingButtonIcon.setScaleX(scale);
                    floatingButtonIcon.setScaleY(scale);
                    floatingButtonIcon.setAlpha(1f - val);

                    scale = 0.1f + 0.9f * val;
                    floatingProgressView.setScaleX(scale);
                    floatingProgressView.setScaleY(scale);
                    floatingProgressView.setAlpha(val);
                } else {
                    float scale = 0.1f + 0.9f * val;
                    radialProgressView.setScaleX(scale);
                    radialProgressView.setScaleY(scale);
                    radialProgressView.setAlpha(val);
                }
            });
            doneItemAnimation.playTogether(animator);
            doneItemAnimation.setDuration(150);
            doneItemAnimation.start();
        } else {
            if (show) {
                if (floating) {
                    floatingProgressView.setVisibility(View.VISIBLE);
                    floatingButtonIcon.setVisibility(View.INVISIBLE);
                    floatingButtonContainer.setEnabled(false);
                    floatingButtonIcon.setScaleX(0.1f);
                    floatingButtonIcon.setScaleY(0.1f);
                    floatingButtonIcon.setAlpha(0.0f);
                    floatingProgressView.setScaleX(1.0f);
                    floatingProgressView.setScaleY(1.0f);
                    floatingProgressView.setAlpha(1.0f);
                } else {
                    radialProgressView.setVisibility(View.VISIBLE);
                    radialProgressView.setScaleX(1.0f);
                    radialProgressView.setScaleY(1.0f);
                    radialProgressView.setAlpha(1.0f);
                }
            } else {
                radialProgressView.setTag(null);
                if (floating) {
                    floatingProgressView.setVisibility(View.INVISIBLE);
                    floatingButtonIcon.setVisibility(View.VISIBLE);
                    floatingButtonContainer.setEnabled(true);
                    floatingProgressView.setScaleX(0.1f);
                    floatingProgressView.setScaleY(0.1f);
                    floatingProgressView.setAlpha(0.0f);
                    floatingButtonIcon.setScaleX(1.0f);
                    floatingButtonIcon.setScaleY(1.0f);
                    floatingButtonIcon.setAlpha(1.0f);
                } else {
                    radialProgressView.setVisibility(View.INVISIBLE);
                    radialProgressView.setScaleX(0.1f);
                    radialProgressView.setScaleY(0.1f);
                    radialProgressView.setAlpha(0.0f);
                }
            }
        }
    }

    private void needShowProgress(int requestId) {
        needShowProgress(requestId, true);
    }

    private void needShowProgress(int requestId, boolean animated) {
        if (isInCancelAccountDeletionMode() && requestId == 0) {
            if (cancelDeleteProgressDialog != null || getParentActivity() == null || getParentActivity().isFinishing()) {
                return;
            }

            cancelDeleteProgressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            cancelDeleteProgressDialog.setCanCancel(false);
            cancelDeleteProgressDialog.show();
            return;
        }

        progressRequestId = requestId;
        showEditDoneProgress(true, animated);
    }

    private void needHideProgress(boolean cancel) {
        needHideProgress(cancel, true);
    }

    private void needHideProgress(boolean cancel, boolean animated) {
        if (progressRequestId != 0) {
            if (cancel) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(progressRequestId, true);
            }
            progressRequestId = 0;
        }

        if (isInCancelAccountDeletionMode() && cancelDeleteProgressDialog != null) {
            cancelDeleteProgressDialog.dismiss();
            cancelDeleteProgressDialog = null;
        }

        showEditDoneProgress(false, animated);
    }

    public void setPage(@ViewNumber int page, boolean animated, Bundle params, boolean back) {
        boolean needFloatingButton = page == VIEW_PHONE_INPUT || page == VIEW_REGISTER || page == VIEW_PASSWORD ||
                page == VIEW_NEW_PASSWORD_STAGE_1 || page == VIEW_NEW_PASSWORD_STAGE_2 || page == VIEW_ADD_EMAIL || page == VIEW_CODE_PHRASE || page == VIEW_CODE_WORD;
        if (page == currentViewNum) {
            animated = false;
        }

        if (needFloatingButton) {
            if (page == VIEW_PHONE_INPUT) {
                checkPermissions = true;
                checkShowPermissions = true;
            }
            currentDoneType = DONE_TYPE_ACTION;
            showDoneButton(false, animated);
            // Force reset radial progress
            showEditDoneProgress(false, animated);
            currentDoneType = DONE_TYPE_FLOATING;
            showEditDoneProgress(false, animated);
            if (!animated) {
                showDoneButton(true, false);
            }
        } else {
            currentDoneType = DONE_TYPE_FLOATING;
            showDoneButton(false, animated);
            showEditDoneProgress(false, animated);
            if (page != VIEW_RESET_WAIT) {
                currentDoneType = DONE_TYPE_ACTION;
            }
        }
        if (animated) {
            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;
            backButtonView.setVisibility(newView.needBackButton() || newAccount ? View.VISIBLE : View.GONE);

            newView.setParams(params, false);
            setParentActivityTitle(newView.getHeaderName());
            newView.onShow();
            newView.setX(back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);
            newView.setVisibility(View.VISIBLE);

            AnimatorSet pagesAnimation = new AnimatorSet();
            pagesAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentDoneType == DONE_TYPE_FLOATING && needFloatingButton) {
                        showDoneButton(true, true);
                    }
                    outView.setVisibility(View.GONE);
                    outView.onHide();
                    outView.setX(0);
                }
            });
            pagesAnimation.playTogether(
                    ObjectAnimator.ofFloat(outView, View.TRANSLATION_X, back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x),
                    ObjectAnimator.ofFloat(newView, View.TRANSLATION_X, 0));
            pagesAnimation.setDuration(300);
            pagesAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            pagesAnimation.start();

            setCustomKeyboardVisible(newView.hasCustomKeyboard(), true);
        } else {
            backButtonView.setVisibility(views[page].needBackButton() || newAccount ? View.VISIBLE : View.GONE);
            views[currentViewNum].setVisibility(View.GONE);
            views[currentViewNum].onHide();
            currentViewNum = page;
            views[page].setParams(params, false);
            views[page].setVisibility(View.VISIBLE);
            setParentActivityTitle(views[page].getHeaderName());
            views[page].onShow();

            setCustomKeyboardVisible(views[page].hasCustomKeyboard(), false);
        }
    }

    @Override
    public void saveSelfArgs(Bundle outState) {
        try {
            Bundle bundle = new Bundle();
            bundle.putInt("currentViewNum", currentViewNum);
            bundle.putInt("syncContacts", syncContacts ? 1 : 0);
            for (int a = 0; a <= currentViewNum; a++) {
                SlideView v = views[a];
                if (v != null) {
                    v.saveStateParams(bundle);
                }
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2" + (newAccount ? "_" + currentAccount : ""), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            putBundleToEditor(bundle, editor, null);
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void needFinishActivity(boolean afterSignup, boolean showSetPasswordConfirm, int otherwiseRelogin) {
        if (getParentActivity() != null) {
            AndroidUtilities.setLightStatusBar(getParentActivity().getWindow(), false);
        }
        clearCurrentState();
        if (getParentActivity() instanceof LaunchActivity) {
            if (newAccount) {
                newAccount = false;
                pendingSwitchingAccount = true;
                ((LaunchActivity) getParentActivity()).switchToAccount(currentAccount, false, obj -> {
                    Bundle args = new Bundle();
                    args.putBoolean("afterSignup", afterSignup);
                    return new DialogsActivity(args);
                });
                pendingSwitchingAccount = false;
                finishFragment();
            } else {

                if (afterSignup && showSetPasswordConfirm) {
                    TwoStepVerificationSetupActivity twoStepVerification = new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null);
                    twoStepVerification.setBlockingAlert(otherwiseRelogin);
                    twoStepVerification.setFromRegistration(true);
                    presentFragment(twoStepVerification, true);
                } else {
                    Bundle args = new Bundle();
                    args.putBoolean("afterSignup", afterSignup);
                    DialogsActivity dialogsActivity = new DialogsActivity(args);
                    presentFragment(dialogsActivity, true);
                }

                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                LocaleController.getInstance().loadRemoteLanguages(currentAccount);
                RestrictedLanguagesSelectActivity.checkRestrictedLanguages(true);
            }
        } else if (getParentActivity() instanceof ExternalActionActivity) {
            ((ExternalActionActivity) getParentActivity()).onFinishLogin();
        }
    }

    private void fakeSuccess() {
        TLRPC.TL_auth_authorization res = new TLRPC.TL_auth_authorization();
        res.user = UserConfig.getInstance(0).getCurrentUser();
        onAuthSuccess(res);
    }

    private void onAuthSuccess(TLRPC.TL_auth_authorization res) {
        onAuthSuccess(res, false);
    }

    private boolean pendingSwitchingAccount;

    private void onAuthSuccess(TLRPC.TL_auth_authorization res, boolean afterSignup) {
        MessagesController.getInstance(currentAccount).cleanup();
        ConnectionsManager.getInstance(currentAccount).setUserId(res.user.id);
        UserConfig.getInstance(currentAccount).clearConfig();
        MessagesController.getInstance(currentAccount).cleanup();
        UserConfig.getInstance(currentAccount).syncContacts = syncContacts;
        UserConfig.getInstance(currentAccount).setCurrentUser(res.user);
        UserConfig.getInstance(currentAccount).saveConfig(true);
        MessagesStorage.getInstance(currentAccount).cleanup(true);
        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(res.user);
        MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, true, true);
        MessagesController.getInstance(currentAccount).putUser(res.user, false);
        ContactsController.getInstance(currentAccount).checkAppAccount();
        MessagesController.getInstance(currentAccount).checkPromoInfo(true);
        ConnectionsManager.getInstance(currentAccount).updateDcSettings();
        MessagesController.getInstance(currentAccount).loadAppConfig();
        MessagesController.getInstance(currentAccount).checkPeerColors(false);

        if (res.future_auth_token != null) {
            AuthTokensHelper.saveLogInToken(res);
        } else {
            FileLog.d("onAuthSuccess future_auth_token is empty");
        }

        if (afterSignup) {
            MessagesController.getInstance(currentAccount).putDialogsEndReachedAfterRegistration();
        }
        MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, true);

        needFinishActivity(afterSignup, res.setup_password_required, res.otherwise_relogin_days);
    }

    private void fillNextCodeParams(Bundle params, TLRPC.TL_account_sentEmailCode res) {
        params.putString("emailPattern", res.email_pattern);
        params.putInt("length", res.length);
        setPage(VIEW_CODE_EMAIL_SETUP, true, params, false);
    }

    private void fillNextCodeParams(Bundle params, TLRPC.auth_SentCode res) {
        fillNextCodeParams(params, res, true);
    }

    private void resendCodeFromSafetyNet(Bundle params, TLRPC.auth_SentCode res, String reason) {
        if (!isRequestingFirebaseSms) {
            return;
        }
        needHideProgress(false);
        isRequestingFirebaseSms = false;

        TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
        req.phone_number = params.getString("phoneFormated");
        req.phone_code_hash = res.phone_code_hash;
        if (reason != null) {
            req.flags |= 1;
            req.reason = reason;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null && !(((TLRPC.auth_SentCode) response).type instanceof TLRPC.TL_auth_sentCodeTypeFirebaseSms)) {
                AndroidUtilities.runOnUIThread(() -> fillNextCodeParams(params, (TLRPC.auth_SentCode) response));
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    if (getParentActivity() == null || getParentActivity().isFinishing() || getContext() == null) {
                        return;
                    }
                    new AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.RestorePasswordNoEmailTitle))
                            .setMessage(getString(R.string.SafetyNetErrorOccurred))
                            .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                                forceDisableSafetyNet = true;
                                if (currentViewNum != VIEW_PHONE_INPUT) {
                                    setPage(VIEW_PHONE_INPUT, true, null, true);
                                }
                            })
                            .show();
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    public static String errorString(Throwable e) {
        if (e == null) return "NULL";
        String str = "";
        if (e.getClass() != null && e.getClass().getSimpleName() != null) {
            str = e.getClass().getSimpleName();
            if (str == null) str = "";
        }
        if (e.getMessage() != null) {
            if (str.length() > 0) str += " ";
            str += e.getMessage();
        }
        return str.toUpperCase().replaceAll(" ", "_");
    }

    private boolean isRequestingFirebaseSms;
    private void fillNextCodeParams(Bundle params, TLRPC.auth_SentCode res, boolean animate) {
        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFirebaseSms && !res.type.verifiedFirebase && !isRequestingFirebaseSms) {
            if (PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices()) {
                TLRPC.TL_auth_sentCodeTypeFirebaseSms r = (TLRPC.TL_auth_sentCodeTypeFirebaseSms) res.type;
                needShowProgress(0);
                isRequestingFirebaseSms = true;
                final String phone = params.getString("phoneFormated");
                if (r.play_integrity_nonce != null) {
                    IntegrityManager integrityManager = IntegrityManagerFactory.create(getContext());
                    final String nonce = new String(Base64.encode(r.play_integrity_nonce, Base64.URL_SAFE));
                    FileLog.d("getting classic integrity with nonce = " + nonce);
                    Task<IntegrityTokenResponse> integrityTokenResponse = integrityManager.requestIntegrityToken(IntegrityTokenRequest.builder().setNonce(nonce).setCloudProjectNumber(r.play_integrity_project_id).build());
                    integrityTokenResponse
                        .addOnSuccessListener(result -> {
                            final String token = result.token();

                            if (token == null) {
                                FileLog.d("Resend firebase sms because integrity token = null");
                                resendCodeFromSafetyNet(params, res, "PLAYINTEGRITY_TOKEN_NULL");
                                return;
                            }

                            TLRPC.TL_auth_requestFirebaseSms req = new TLRPC.TL_auth_requestFirebaseSms();
                            req.phone_number = phone;
                            req.phone_code_hash = res.phone_code_hash;
                            req.play_integrity_token = token;
                            req.flags |= 4;

                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                if (response instanceof TLRPC.TL_boolTrue) {
                                    needHideProgress(false);
                                    isRequestingFirebaseSms = false;
                                    res.type.verifiedFirebase = true;
                                    AndroidUtilities.runOnUIThread(() -> fillNextCodeParams(params, res, animate));
                                } else {
                                    FileLog.d("{PLAYINTEGRITY_REQUESTFIREBASESMS_FALSE} Resend firebase sms because auth.requestFirebaseSms = false");
                                    resendCodeFromSafetyNet(params, res, "PLAYINTEGRITY_REQUESTFIREBASESMS_FALSE");
                                }
                            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        })
                        .addOnFailureListener(e -> {
                            final String reason = "PLAYINTEGRITY_EXCEPTION_" + errorString(e);
                            FileLog.e("{"+reason+"} Resend firebase sms because integrity threw error", e);
                            resendCodeFromSafetyNet(params, res, reason);
                        });
                } else {
                    SafetyNet.getClient(ApplicationLoader.applicationContext).attest(res.type.nonce, BuildVars.SAFETYNET_KEY)
                    .addOnSuccessListener(attestationResponse -> {
                        String jws = attestationResponse.getJwsResult();

                        if (jws != null) {
                            TLRPC.TL_auth_requestFirebaseSms req = new TLRPC.TL_auth_requestFirebaseSms();
                            req.phone_number = phone;
                            req.phone_code_hash = res.phone_code_hash;
                            req.safety_net_token = jws;
                            req.flags |= 1;

                            String[] spl = jws.split("\\.");
                            if (spl.length > 0) {
                                try {
                                    JSONObject obj = new JSONObject(new String(Base64.decode(spl[1].getBytes(StandardCharsets.UTF_8), 0)));
                                    final boolean basicIntegrity = obj.optBoolean("basicIntegrity");
                                    final boolean ctsProfileMatch = obj.optBoolean("ctsProfileMatch");
                                    if (basicIntegrity && ctsProfileMatch) {
                                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                            if (response instanceof TLRPC.TL_boolTrue) {
                                                needHideProgress(false);
                                                isRequestingFirebaseSms = false;
                                                res.type.verifiedFirebase = true;
                                                AndroidUtilities.runOnUIThread(() -> fillNextCodeParams(params, res, animate));
                                            } else {
                                                FileLog.d("{SAFETYNET_REQUESTFIREBASESMS_FALSE} Resend firebase sms because auth.requestFirebaseSms = false");
                                                resendCodeFromSafetyNet(params, res, "SAFETYNET_REQUESTFIREBASESMS_FALSE");
                                            }
                                        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                                    } else {
                                        if (!basicIntegrity && !ctsProfileMatch) {
                                            FileLog.d("{SAFETYNET_BASICINTEGRITY_CTSPROFILEMATCH_FALSE} Resend firebase sms because ctsProfileMatch = false and basicIntegrity = false");
                                            resendCodeFromSafetyNet(params, res, "SAFETYNET_BASICINTEGRITY_CTSPROFILEMATCH_FALSE");
                                        } else if (!basicIntegrity) {
                                            FileLog.d("{SAFETYNET_BASICINTEGRITY_FALSE} Resend firebase sms because basicIntegrity = false");
                                            resendCodeFromSafetyNet(params, res, "SAFETYNET_BASICINTEGRITY_FALSE");
                                        } else if (!ctsProfileMatch) {
                                            FileLog.d("{SAFETYNET_CTSPROFILEMATCH_FALSE} Resend firebase sms because ctsProfileMatch = false");
                                            resendCodeFromSafetyNet(params, res, "SAFETYNET_CTSPROFILEMATCH_FALSE");
                                        }
                                    }
                                } catch (JSONException e) {
                                    FileLog.e(e);

                                    FileLog.d("{SAFETYNET_JSON_EXCEPTION} Resend firebase sms because of exception");
                                    resendCodeFromSafetyNet(params, res, "SAFETYNET_JSON_EXCEPTION");
                                }
                            } else {
                                FileLog.d("{SAFETYNET_CANT_SPLIT} Resend firebase sms because can't split JWS token");
                                resendCodeFromSafetyNet(params, res, "SAFETYNET_CANT_SPLIT");
                            }
                        } else {
                            FileLog.d("{SAFETYNET_NULL_JWS} Resend firebase sms because JWS = null");
                            resendCodeFromSafetyNet(params, res, "SAFETYNET_NULL_JWS");
                        }
                    })
                    .addOnFailureListener(e -> {
                        FileLog.e(e);

                        final String reason = "SAFETYNET_EXCEPTION_" + errorString(e);
                        FileLog.d("{"+reason+"} Resend firebase sms because of safetynet exception");
                        resendCodeFromSafetyNet(params, res, reason);
                    });
                }
            } else {
                FileLog.d("{GOOGLE_PLAY_SERVICES_NOT_AVAILABLE} Resend firebase sms because firebase is not available");
                resendCodeFromSafetyNet(params, res, "GOOGLE_PLAY_SERVICES_NOT_AVAILABLE");
            }
            return;
        }

        params.putString("phoneHash", res.phone_code_hash);
        if (res.next_type instanceof TLRPC.TL_auth_codeTypeCall) {
            params.putInt("nextType", AUTH_TYPE_CALL);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeFlashCall) {
            params.putInt("nextType", AUTH_TYPE_FLASH_CALL);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeSms) {
            params.putInt("nextType", AUTH_TYPE_SMS);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeMissedCall) {
            params.putInt("nextType", AUTH_TYPE_MISSED_CALL);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeFragmentSms) {
            params.putInt("nextType", AUTH_TYPE_FRAGMENT_SMS);
        }
        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeApp) {
            params.putInt("type", AUTH_TYPE_MESSAGE);
            params.putInt("length", res.type.length);
            setPage(VIEW_CODE_MESSAGE, animate, params, false);
        } else {
            if (res.timeout == 0) {
                res.timeout = BuildVars.DEBUG_PRIVATE_VERSION ? 5 : 60;
            }
            params.putInt("timeout", res.timeout * 1000);
            if (res.type instanceof TLRPC.TL_auth_sentCodeTypeCall) {
                params.putInt("type", AUTH_TYPE_CALL);
                params.putInt("length", res.type.length);
                setPage(VIEW_CODE_CALL, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFlashCall) {
                params.putInt("type", AUTH_TYPE_FLASH_CALL);
                params.putString("pattern", res.type.pattern);
                setPage(VIEW_CODE_FLASH_CALL, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSms || res.type instanceof TLRPC.TL_auth_sentCodeTypeFirebaseSms) {
                params.putInt("type", AUTH_TYPE_SMS);
                params.putInt("length", res.type.length);
                params.putBoolean("firebase", res.type instanceof TLRPC.TL_auth_sentCodeTypeFirebaseSms);
                setPage(VIEW_CODE_SMS, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFragmentSms) {
                params.putInt("type", AUTH_TYPE_FRAGMENT_SMS);
                params.putString("url", res.type.url);
                params.putInt("length", res.type.length);
                setPage(VIEW_CODE_FRAGMENT_SMS, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeMissedCall) {
                params.putInt("type", AUTH_TYPE_MISSED_CALL);
                params.putInt("length", res.type.length);
                params.putString("prefix", res.type.prefix);
                setPage(VIEW_CODE_MISSED_CALL, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSetUpEmailRequired) {
                params.putBoolean("googleSignInAllowed", res.type.google_signin_allowed);
                setPage(VIEW_ADD_EMAIL, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeEmailCode) {
                params.putBoolean("googleSignInAllowed", res.type.google_signin_allowed);
                params.putString("emailPattern", res.type.email_pattern);
                params.putInt("length", res.type.length);
                params.putInt("nextPhoneLoginDate", res.type.next_phone_login_date);
                params.putInt("resetAvailablePeriod", res.type.reset_available_period);
                params.putInt("resetPendingDate", res.type.reset_pending_date);
                setPage(VIEW_CODE_EMAIL, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSmsWord) {
                if (res.type.beginning != null) {
                    params.putString("beginning", res.type.beginning);
                }
                setPage(VIEW_CODE_WORD, animate, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSmsPhrase) {
                if (res.type.beginning != null) {
                    params.putString("beginning", res.type.beginning);
                }
                setPage(VIEW_CODE_PHRASE, animate, params, false);
            }
        }
    }

    private TLRPC.TL_help_termsOfService currentTermsOfService;

    public class PhoneView extends SlideView implements AdapterView.OnItemSelectedListener, NotificationCenter.NotificationCenterDelegate {
        private AnimatedPhoneNumberEditText codeField;
        private AnimatedPhoneNumberEditText phoneField;
        private TextView titleView;
        private TextViewSwitcher countryButton;
        private OutlineTextContainerView countryOutlineView;
        private OutlineTextContainerView phoneOutlineView;
        private TextView plusTextView;
        private TextView subtitleView;
        private View codeDividerView;
        private ImageView chevronRight;
        private CheckBoxCell syncContactsBox;
        private CheckBoxCell testBackendCheckBox;

        @CountryState
        private int countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
        private CountrySelectActivity.Country currentCountry;

        private ArrayList<CountrySelectActivity.Country> countriesArray = new ArrayList<>();
        private HashMap<String, List<CountrySelectActivity.Country>> codesMap = new HashMap<>();
        private HashMap<String, List<String>> phoneFormatMap = new HashMap<>();

        private boolean ignoreSelection = false;
        private boolean ignoreOnTextChange = false;
        private boolean ignoreOnPhoneChange = false;
        private boolean ignoreOnPhoneChangePaste = false;
        private boolean nextPressed = false;
        private boolean confirmedNumber = false;

        public PhoneView(Context context) {
            super(context);

            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(getString(activityMode == MODE_CHANGE_PHONE_NUMBER ? R.string.ChangePhoneNewNumber : R.string.YourNumber));
            titleView.setGravity(Gravity.CENTER);
            titleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 0));

            subtitleView = new TextView(context);
            subtitleView.setText(getString(activityMode == MODE_CHANGE_PHONE_NUMBER ? R.string.ChangePhoneHelp : R.string.StartText));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 8, 32, 0));

            countryButton = new TextViewSwitcher(context);
            countryButton.setFactory(() -> {
                TextView tv = new TextView(context);
                tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                tv.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                tv.setMaxLines(1);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
                return tv;
            });

            Animation anim = AnimationUtils.loadAnimation(context, R.anim.text_in);
            anim.setInterpolator(Easings.easeInOutQuad);
            countryButton.setInAnimation(anim);

            chevronRight = new ImageView(context);
            chevronRight.setImageResource(R.drawable.msg_inputarrow);

            LinearLayout countryButtonLinearLayout = new LinearLayout(context);
            countryButtonLinearLayout.setOrientation(HORIZONTAL);
            countryButtonLinearLayout.setGravity(Gravity.CENTER_VERTICAL);
            countryButtonLinearLayout.addView(countryButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 0, 0));
            countryButtonLinearLayout.addView(chevronRight, LayoutHelper.createLinearRelatively(24, 24, 0, 0, 0, 14, 0));

            countryOutlineView = new OutlineTextContainerView(context);
            countryOutlineView.setText(getString(R.string.Country));
            countryOutlineView.addView(countryButtonLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 0));
            countryOutlineView.setForceUseCenter(true);
            countryOutlineView.setFocusable(true);
            countryOutlineView.setContentDescription(getString(R.string.Country));
            countryOutlineView.setOnFocusChangeListener((v, hasFocus) -> countryOutlineView.animateSelection(hasFocus ? 1 : 0));
            addView(countryOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 16, 24, 16, 14));
            countryOutlineView.setOnClickListener(view -> {
                CountrySelectActivity fragment = new CountrySelectActivity(true, countriesArray);
                fragment.setCountrySelectActivityDelegate((country) -> {
                    selectCountry(country);
                    AndroidUtilities.runOnUIThread(() -> showKeyboard(phoneField), 300);
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                });
                presentFragment(fragment);
            });

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);

            phoneOutlineView = new OutlineTextContainerView(context);
            phoneOutlineView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 8, 16, 8));
            phoneOutlineView.setText(getString(R.string.PhoneNumber));
            addView(phoneOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 16, 8, 16, 8));

            plusTextView = new TextView(context);
            plusTextView.setText("+");
            plusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            plusTextView.setFocusable(false);
            linearLayout.addView(plusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            codeField = new AnimatedPhoneNumberEditText(context) {
                @Override
                protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                    super.onFocusChanged(focused, direction, previouslyFocusedRect);
                    phoneOutlineView.animateSelection(focused || phoneField.isFocused() ? 1f : 0f);

                    if (focused) {
                        keyboardView.setEditText(this);
                    }
                }
            };
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setCursorSize(AndroidUtilities.dp(20));
            codeField.setCursorWidth(1.5f);
            codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            codeField.setMaxLines(1);
            codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setBackground(null);
//            codeField.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                codeField.setShowSoftInputOnFocus(!(hasCustomKeyboard() && !isCustomKeyboardForceDisabled()));
            }
            codeField.setContentDescription(getString(R.string.LoginAccessibilityCountryCode));
            linearLayout.addView(codeField, LayoutHelper.createLinear(55, 36, -9, 0, 0, 0));
            codeField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (ignoreOnTextChange) {
                        return;
                    }
                    ignoreOnTextChange = true;
                    String text = PhoneFormat.stripExceptNumbers(codeField.getText().toString());
                    codeField.setText(text);
                    if (text.length() == 0) {
                        setCountryButtonText(null);
                        phoneField.setHintText(null);
                        countryState = COUNTRY_STATE_EMPTY;
                    } else {
                        CountrySelectActivity.Country country;
                        boolean ok = false;
                        String textToSet = null;
                        if (text.length() > 4) {
                            for (int a = 4; a >= 1; a--) {
                                String sub = text.substring(0, a);

                                List<CountrySelectActivity.Country> list = codesMap.get(sub);
                                if (list == null) {
                                    country = null;
                                } else if (list.size() > 1) {
                                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                    String lastMatched = preferences.getString("phone_code_last_matched_" + sub, null);

                                    country = list.get(list.size() - 1);
                                    if (lastMatched != null) {
                                        for (CountrySelectActivity.Country c : countriesArray) {
                                            if (Objects.equals(c.shortname, lastMatched)) {
                                                country = c;
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    country = list.get(0);
                                }

                                if (country != null) {
                                    ok = true;
                                    textToSet = text.substring(a) + phoneField.getText().toString();
                                    codeField.setText(text = sub);
                                    break;
                                }
                            }
                            if (!ok) {
                                textToSet = text.substring(1) + phoneField.getText().toString();
                                codeField.setText(text = text.substring(0, 1));
                            }
                        }

                        CountrySelectActivity.Country lastMatchedCountry = null;
                        int matchedCountries = 0;
                        for (CountrySelectActivity.Country c : countriesArray) {
                            if (c.code.startsWith(text)) {
                                matchedCountries++;
                                if (c.code.equals(text)) {
                                    if (lastMatchedCountry != null && lastMatchedCountry.code.equals(c.code)) {
                                        matchedCountries--;
                                    }
                                    lastMatchedCountry = c;
                                }
                            }
                        }
                        if (matchedCountries == 1 && lastMatchedCountry != null && textToSet == null) {
                            textToSet = text.substring(lastMatchedCountry.code.length()) + phoneField.getText().toString();
                            codeField.setText(text = lastMatchedCountry.code);
                        }

                        List<CountrySelectActivity.Country> list = codesMap.get(text);
                        if (list == null) {
                            country = null;
                        } else if (list.size() > 1) {
                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                            String lastMatched = preferences.getString("phone_code_last_matched_" + text, null);

                            country = list.get(list.size() - 1);
                            if (lastMatched != null) {
                                for (CountrySelectActivity.Country c : countriesArray) {
                                    if (Objects.equals(c.shortname, lastMatched)) {
                                        country = c;
                                        break;
                                    }
                                }
                            }
                        } else {
                            country = list.get(0);
                        }

                        if (country != null) {
                            ignoreSelection = true;
                            currentCountry = country;
                            setCountryHint(text, country);
                            countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
                        } else {
                            setCountryButtonText(null);
                            phoneField.setHintText(null);
                            countryState = COUNTRY_STATE_INVALID;
                        }
                        if (!ok) {
                            codeField.setSelection(codeField.getText().length());
                        }
                        if (textToSet != null) {
                            phoneField.requestFocus();
                            phoneField.setText(textToSet);
                            phoneField.setSelection(phoneField.length());
                        }
                    }
                    ignoreOnTextChange = false;
                }
            });
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                    return true;
                }
                return false;
            });
            codeDividerView = new View(context);
            LayoutParams params = LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 4, 8, 12, 8);
            params.width = Math.max(2, AndroidUtilities.dp(0.5f));
            linearLayout.addView(codeDividerView, params);

            phoneField = new AnimatedPhoneNumberEditText(context) {

                @Override
                public boolean onKeyDown(int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DEL && phoneField.length() == 0) {
                        codeField.requestFocus();
                        codeField.setSelection(codeField.length());
                        codeField.dispatchKeyEvent(event);
                    }
                    return super.onKeyDown(keyCode, event);
                }


                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (!showKeyboard(this)) {
                            clearFocus();
                            requestFocus();
                        }
                    }
                    return super.onTouchEvent(event);
                }

                @Override
                protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                    super.onFocusChanged(focused, direction, previouslyFocusedRect);
                    phoneOutlineView.animateSelection(focused || codeField.isFocused() ? 1f : 0f);

                    if (focused) {
                        keyboardView.setEditText(this);
                        keyboardView.setDispatchBackWhenEmpty(true);

                        if (countryState == COUNTRY_STATE_INVALID) {
                            setCountryButtonText(getString(R.string.WrongCountry));
                        }
                    } else {
                        if (countryState == COUNTRY_STATE_INVALID) {
                            setCountryButtonText(null);
                        }
                    }
                }
            };
            phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
            phoneField.setPadding(0, 0, 0, 0);
            phoneField.setCursorSize(AndroidUtilities.dp(20));
            phoneField.setCursorWidth(1.5f);
            phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            phoneField.setMaxLines(1);
            phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            phoneField.setBackground(null);
//            phoneField.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                phoneField.setShowSoftInputOnFocus(!(hasCustomKeyboard() && !isCustomKeyboardForceDisabled()));
            }
            phoneField.setContentDescription(getString(R.string.PhoneNumber));
            linearLayout.addView(phoneField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
            phoneField.addTextChangedListener(new TextWatcher() {

                private int characterAction = -1;
                private int actionPosition;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (count == 0 && after == 1) {
                        characterAction = 1;
                    } else if (count == 1 && after == 0) {
                        if (s.charAt(start) == ' ' && start > 0) {
                            characterAction = 3;
                            actionPosition = start - 1;
                        } else {
                            characterAction = 2;
                        }
                    } else {
                        characterAction = -1;
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!ENABLE_PASTED_TEXT_PROCESSING || ignoreOnPhoneChange || ignoreOnPhoneChangePaste) {
                        return;
                    }

                    String str = s.toString().substring(start, start + count).replaceAll("[^\\d]+", "");
                    if (str.isEmpty()) {
                        return;
                    }

                    ignoreOnPhoneChangePaste = true;
                    for (int i = Math.min(3, str.length()); i >= 0; i--) {
                        String code = str.substring(0, i);

                        List<CountrySelectActivity.Country> list = codesMap.get(code);
                        if (list != null && !list.isEmpty()) {
                            List<String> patterns = phoneFormatMap.get(code);

                            if (patterns == null || patterns.isEmpty()) {
                                continue;
                            }

                            for (String pattern : patterns) {
                                String pat = pattern.replace(" ", "");
                                if (pat.length() == str.length() - i) {
                                    codeField.setText(code);
                                    ignoreOnTextChange = true;
                                    phoneField.setText(str.substring(i));
                                    ignoreOnTextChange = false;

                                    afterTextChanged(phoneField.getText());
                                    phoneField.setSelection(phoneField.getText().length(), phoneField.getText().length());
                                    break;
                                }
                            }
                        }
                    }
                    ignoreOnPhoneChangePaste = false;
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreOnPhoneChange) {
                        return;
                    }
                    int start = phoneField.getSelectionStart();
                    String phoneChars = "0123456789";
                    String str = phoneField.getText().toString();
                    if (characterAction == 3) {
                        str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
                        start--;
                    }
                    StringBuilder builder = new StringBuilder(str.length());
                    for (int a = 0; a < str.length(); a++) {
                        String ch = str.substring(a, a + 1);
                        if (phoneChars.contains(ch)) {
                            builder.append(ch);
                        }
                    }
                    ignoreOnPhoneChange = true;
                    String hint = phoneField.getHintText();
                    if (hint != null) {
                        for (int a = 0; a < builder.length(); a++) {
                            if (a < hint.length()) {
                                if (hint.charAt(a) == ' ') {
                                    builder.insert(a, ' ');
                                    a++;
                                    if (start == a && characterAction != 2 && characterAction != 3) {
                                        start++;
                                    }
                                }
                            } else {
                                builder.insert(a, ' ');
                                if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                    start++;
                                }
                                break;
                            }
                        }
                    }
                    s.replace(0, s.length(), builder);
                    if (start >= 0) {
                        phoneField.setSelection(Math.min(start, phoneField.length()));
                    }
                    phoneField.onTextChange();
                    invalidateCountryHint();
                    ignoreOnPhoneChange = false;
                }
            });
            phoneField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    if (phoneNumberConfirmView != null) {
                        phoneNumberConfirmView.popupFabContainer.callOnClick();
                        return true;
                    }
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            int bottomMargin = 72;
            if (newAccount && activityMode == MODE_LOGIN) {
                syncContactsBox = new CheckBoxCell(context, 2);
                syncContactsBox.setText(getString("SyncContacts", R.string.SyncContacts), "", syncContacts, false);
                addView(syncContactsBox, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 16, 0, 16 + (LocaleController.isRTL && AndroidUtilities.isSmallScreen() ? Build.VERSION.SDK_INT >= 21 ? 56 : 60 : 0), 0));
                bottomMargin -= 24;
                syncContactsBox.setOnClickListener(v -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    CheckBoxCell cell = (CheckBoxCell) v;
                    syncContacts = !syncContacts;
                    cell.setChecked(syncContacts, true);
                    if (syncContacts) {
                        BulletinFactory.of(slideViewsContainer, null).createSimpleBulletin(R.raw.contacts_sync_on, getString("SyncContactsOn", R.string.SyncContactsOn)).show();
                    } else {
                        BulletinFactory.of(slideViewsContainer, null).createSimpleBulletin(R.raw.contacts_sync_off, getString("SyncContactsOff", R.string.SyncContactsOff)).show();
                    }
                });
            }

            final boolean allowTestBackend = BuildVars.DEBUG_VERSION;
            if (allowTestBackend && activityMode == MODE_LOGIN) {
                testBackendCheckBox = new CheckBoxCell(context, 2);
                testBackendCheckBox.setText(getString(R.string.DebugTestBackend), "", testBackend = getConnectionsManager().isTestBackend(), false);
                addView(testBackendCheckBox, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 16, 0, 16 + (LocaleController.isRTL && AndroidUtilities.isSmallScreen() ? Build.VERSION.SDK_INT >= 21 ? 56 : 60 : 0), 0));
                bottomMargin -= 24;
                testBackendCheckBox.setOnClickListener(v -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    CheckBoxCell cell = (CheckBoxCell) v;
                    testBackend = !testBackend;
                    cell.setChecked(testBackend, true);

                    boolean testBackend = allowTestBackend && getConnectionsManager().isTestBackend();
                    if (testBackend != LoginActivity.this.testBackend) {
                        getConnectionsManager().switchBackend(false);
                    }
                    loadCountries();
                });
            }
            if (bottomMargin > 0 && !AndroidUtilities.isSmallScreen()) {
                Space bottomSpacer = new Space(context);
                bottomSpacer.setMinimumHeight(AndroidUtilities.dp(bottomMargin));
                addView(bottomSpacer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }

            HashMap<String, String> languageMap = new HashMap<>();

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().getAssets().open("countries.txt")));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] args = line.split(";");
                    CountrySelectActivity.Country countryWithCode = new CountrySelectActivity.Country();
                    countryWithCode.name = args[2];
                    countryWithCode.code = args[0];
                    countryWithCode.shortname = args[1];
                    countriesArray.add(0, countryWithCode);

                    List<CountrySelectActivity.Country> countryList = codesMap.get(args[0]);
                    if (countryList == null) {
                        codesMap.put(args[0], countryList = new ArrayList<>());
                    }
                    countryList.add(countryWithCode);

                    if (args.length > 3) {
                        phoneFormatMap.put(args[0], Collections.singletonList(args[3]));
                    }
                    languageMap.put(args[1], args[2]);
                }
                reader.close();
            } catch (Exception e) {
                FileLog.e(e);
            }

            Collections.sort(countriesArray, Comparator.comparing(o -> o.name));

            String country = null;

            try {
                TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    country = null;//telephonyManager.getSimCountryIso().toUpperCase();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (country != null) {
                setCountry(languageMap, country.toUpperCase());
            } else {
                TLRPC.TL_help_getNearestDc req = new TLRPC.TL_help_getNearestDc();
                getAccountInstance().getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response == null) {
                        return;
                    }
                    TLRPC.TL_nearestDc res = (TLRPC.TL_nearestDc) response;
                    if (codeField.length() == 0) {
                        setCountry(languageMap, res.country.toUpperCase());
                    }
                }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
            }
            if (codeField.length() == 0) {
                setCountryButtonText(null);
                phoneField.setHintText(null);
                countryState = COUNTRY_STATE_EMPTY;
            }

            if (codeField.length() != 0) {
                phoneField.requestFocus();
                phoneField.setSelection(phoneField.length());
            } else {
                codeField.requestFocus();
            }

            loadCountries();
        }

        private void loadCountries() {
            TLRPC.TL_help_getCountriesList req = new TLRPC.TL_help_getCountriesList();
            req.lang_code = LocaleController.getInstance().getCurrentLocaleInfo() != null ? LocaleController.getInstance().getCurrentLocaleInfo().getLangCode() : Locale.getDefault().getCountry();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        countriesArray.clear();
                        codesMap.clear();
                        phoneFormatMap.clear();

                        TLRPC.TL_help_countriesList help_countriesList = (TLRPC.TL_help_countriesList) response;
                        for (int i = 0; i < help_countriesList.countries.size(); i++) {
                            TLRPC.TL_help_country c = help_countriesList.countries.get(i);
                            for (int k = 0; k < c.country_codes.size(); k++) {
                                TLRPC.TL_help_countryCode countryCode = c.country_codes.get(k);
                                if (countryCode != null) {
                                    CountrySelectActivity.Country countryWithCode = new CountrySelectActivity.Country();
                                    countryWithCode.name = c.name;
                                    countryWithCode.defaultName = c.default_name;
                                    if (countryWithCode.name == null && countryWithCode.defaultName != null) {
                                        countryWithCode.name = countryWithCode.defaultName;
                                    }
                                    countryWithCode.code = countryCode.country_code;
                                    countryWithCode.shortname = c.iso2;

                                    countriesArray.add(countryWithCode);
                                    List<CountrySelectActivity.Country> countryList = codesMap.get(countryCode.country_code);
                                    if (countryList == null) {
                                        codesMap.put(countryCode.country_code, countryList = new ArrayList<>());
                                    }
                                    countryList.add(countryWithCode);
                                    if (countryCode.patterns.size() > 0) {
                                        phoneFormatMap.put(countryCode.country_code, countryCode.patterns);
                                    }
                                }
                            }
                        }

                        if (activityMode == MODE_CHANGE_PHONE_NUMBER) {
                            String number = PhoneFormat.stripExceptNumbers(UserConfig.getInstance(currentAccount).getClientPhone());
                            boolean ok = false;
                            if (!TextUtils.isEmpty(number)) {
                                if (number.length() > 4) {
                                    for (int a = 4; a >= 1; a--) {
                                        String sub = number.substring(0, a);

                                        CountrySelectActivity.Country country2;
                                        List<CountrySelectActivity.Country> list = codesMap.get(sub);
                                        if (list == null) {
                                            country2 = null;
                                        } else if (list.size() > 1) {
                                            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                            String lastMatched = preferences.getString("phone_code_last_matched_" + sub, null);

                                            if (lastMatched != null) {
                                                country2 = list.get(list.size() - 1);
                                                for (CountrySelectActivity.Country c : countriesArray) {
                                                    if (Objects.equals(c.shortname, lastMatched)) {
                                                        country2 = c;
                                                        break;
                                                    }
                                                }
                                            } else {
                                                country2 = list.get(list.size() - 1);
                                            }
                                        } else {
                                            country2 = list.get(0);
                                        }

                                        if (country2 != null) {
                                            ok = true;
                                            codeField.setText(sub);
                                            break;
                                        }
                                    }
                                    if (!ok) {
                                        codeField.setText(number.substring(0, 1));
                                    }
                                }
                            }
                        }
                    }
                });
            }, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            for (int i = 0; i < countryButton.getChildCount(); i++) {
                TextView textView = (TextView) countryButton.getChildAt(i);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            }

            chevronRight.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            chevronRight.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1));

            plusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

            codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));

            codeDividerView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));

            phoneField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            phoneField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            phoneField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));

            if (syncContactsBox != null) {
                syncContactsBox.setSquareCheckBoxColor(Theme.key_checkboxSquareUnchecked, Theme.key_checkboxSquareBackground, Theme.key_checkboxSquareCheck);
                syncContactsBox.updateTextColor();
            }
            if (testBackendCheckBox != null) {
                testBackendCheckBox.setSquareCheckBoxColor(Theme.key_checkboxSquareUnchecked, Theme.key_checkboxSquareBackground, Theme.key_checkboxSquareCheck);
                testBackendCheckBox.updateTextColor();
            }

            phoneOutlineView.updateColor();
            countryOutlineView.updateColor();
        }

        @Override
        public boolean hasCustomKeyboard() {
            return true;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        }

        public void selectCountry(CountrySelectActivity.Country country) {
            ignoreOnTextChange = true;
            String code = country.code;
            codeField.setText(code);
            setCountryHint(code, country);
            currentCountry = country;
            countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
            ignoreOnTextChange = false;

            MessagesController.getGlobalMainSettings().edit().putString("phone_code_last_matched_" + country.code, country.shortname).apply();
        }

        private String countryCodeForHint;
        private void setCountryHint(String code, CountrySelectActivity.Country country) {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            String flag = LocaleController.getLanguageFlag(country.shortname);
            if (flag != null) {
                sb.append(flag).append(" ");
                sb.setSpan(new ReplacementSpan() {
                    @Override
                    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                        return AndroidUtilities.dp(16);
                    }

                    @Override
                    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
                }, flag.length(), flag.length() + 1, 0);
            }
            sb.append(country.name);
            setCountryButtonText(Emoji.replaceEmoji(sb, countryButton.getCurrentView().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
            countryCodeForHint = code;
            wasCountryHintIndex = -1;
            invalidateCountryHint();
        }

        private int wasCountryHintIndex = -1;
        private void invalidateCountryHint() {
            String code = countryCodeForHint;
            String str = phoneField.getText() != null ? phoneField.getText().toString().replace(" ", "") : "";

            if (phoneFormatMap.get(code) != null && !phoneFormatMap.get(code).isEmpty()) {
                int index = -1;
                List<String> patterns = phoneFormatMap.get(code);
                if (!str.isEmpty()) {
                    for (int i = 0; i < patterns.size(); i++) {
                        String pattern = patterns.get(i);
                        if (str.startsWith(pattern.replace(" ", "").replace("X", "").replace("0", ""))) {
                            index = i;
                            break;
                        }
                    }
                }
                if (index == -1) {
                    for (int i = 0; i < patterns.size(); i++) {
                        String pattern = patterns.get(i);
                        if (pattern.startsWith("X") || pattern.startsWith("0")) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        index = 0;
                    }
                }

                if (wasCountryHintIndex != index) {
                    String hint = phoneFormatMap.get(code).get(index);
                    int ss = phoneField.getSelectionStart(), se = phoneField.getSelectionEnd();
                    phoneField.setHintText(hint != null ? hint.replace('X', '0') : null);
                    phoneField.setSelection(
                        Math.max(0, Math.min(phoneField.length(), ss)),
                        Math.max(0, Math.min(phoneField.length(), se))
                    );
                    wasCountryHintIndex = index;
                }
            } else if (wasCountryHintIndex != -1) {
                int ss = phoneField.getSelectionStart(), se = phoneField.getSelectionEnd();
                phoneField.setHintText(null);
                phoneField.setSelection(ss, se);
                wasCountryHintIndex = -1;
            }
        }

        private void setCountryButtonText(CharSequence cs) {
            Animation anim = AnimationUtils.loadAnimation(ApplicationLoader.applicationContext, countryButton.getCurrentView().getText() != null && cs == null ? R.anim.text_out_down : R.anim.text_out);
            anim.setInterpolator(Easings.easeInOutQuad);
            countryButton.setOutAnimation(anim);

            CharSequence prevText = countryButton.getCurrentView().getText();
            countryButton.setText(cs, !(TextUtils.isEmpty(cs) && TextUtils.isEmpty(prevText)) && !Objects.equals(prevText, cs));
            countryOutlineView.animateSelection(cs != null ? 1f : 0f);
        }

        private void setCountry(HashMap<String, String> languageMap, String country) {
            String name = languageMap.get(country);
            if (name != null && countriesArray != null) {
                CountrySelectActivity.Country countryWithCode = null;
                for (int i = 0; i < countriesArray.size(); i++) {
                    if (countriesArray.get(i) != null && countriesArray.get(i).name.equals(country)) {
                        countryWithCode = countriesArray.get(i);
                        break;
                    }
                }
                if (countryWithCode != null) {
                    codeField.setText(countryWithCode.code);
                    countryState = COUNTRY_STATE_NOT_SET_OR_VALID;
                }
            }
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (ignoreSelection) {
                ignoreSelection = false;
                return;
            }
            ignoreOnTextChange = true;
            CountrySelectActivity.Country countryWithCode = countriesArray.get(i);
            codeField.setText(countryWithCode.code);
            ignoreOnTextChange = false;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onNextPressed(String code) {
            if (getParentActivity() == null || nextPressed || isRequestingFirebaseSms) {
                return;
            }

            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("sim status = " + tm.getSimState());
            }
            if (codeField.length() == 0 || phoneField.length() == 0) {
                onFieldError(phoneOutlineView, false);
                return;
            }
            String phoneNumber = "+" + codeField.getText() + " " + phoneField.getText();
            if (!confirmedNumber) {
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y && !isCustomKeyboardVisible() && sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
                    keyboardHideCallback = () -> postDelayed(()-> onNextPressed(code), 200);
                    AndroidUtilities.hideKeyboard(fragmentView);
                    return;
                }

                phoneNumberConfirmView = new PhoneNumberConfirmView(fragmentView.getContext(), (ViewGroup) fragmentView, floatingButtonContainer, phoneNumber, new PhoneNumberConfirmView.IConfirmDialogCallback() {
                    @Override
                    public void onFabPressed(PhoneNumberConfirmView confirmView, TransformableLoginButtonView fab) {
                        onConfirm(confirmView);
                    }

                    @Override
                    public void onEditPressed(PhoneNumberConfirmView confirmView, TextView editTextView) {
                        confirmView.dismiss();
                    }

                    @Override
                    public void onConfirmPressed(PhoneNumberConfirmView confirmView, TextView confirmTextView) {
                        onConfirm(confirmView);
                    }

                    @Override
                    public void onDismiss(PhoneNumberConfirmView confirmView) {
                        phoneNumberConfirmView = null;
                    }

                    private void onConfirm(PhoneNumberConfirmView confirmView) {
                        confirmedNumber = true;
                        currentDoneType = DONE_TYPE_FLOATING;
                        needShowProgress(0, false);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && AndroidUtilities.isSimAvailable()) {
                            boolean allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                            boolean allowCancelCall = getParentActivity().checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
                            boolean allowReadCallLog = Build.VERSION.SDK_INT < Build.VERSION_CODES.P || getParentActivity().checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
                            boolean allowReadPhoneNumbers = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;;
                            if (codeField != null && "888".equals(codeField.getText())) {
                                allowCall = true;
                                allowCancelCall = true;
                                allowReadCallLog = true;
                                allowReadPhoneNumbers = true;
                            }
                            if (checkPermissions) {
                                permissionsItems.clear();
                                if (!allowCall) {
                                    permissionsItems.add(Manifest.permission.READ_PHONE_STATE);
                                }
                                if (!allowCancelCall) {
                                    permissionsItems.add(Manifest.permission.CALL_PHONE);
                                }
                                if (!allowReadCallLog) {
                                    permissionsItems.add(Manifest.permission.READ_CALL_LOG);
                                }
                                if (!allowReadPhoneNumbers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    permissionsItems.add(Manifest.permission.READ_PHONE_NUMBERS);
                                }
                                if (!permissionsItems.isEmpty()) {
                                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                    if (preferences.getBoolean("firstlogin", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                                        preferences.edit().putBoolean("firstlogin", false).commit();
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                                        builder.setPositiveButton(getString("Continue", R.string.Continue), null);
                                        int resId;
                                        if (!allowCall && (!allowCancelCall || !allowReadCallLog)) {
                                            builder.setMessage(getString("AllowReadCallAndLog", R.string.AllowReadCallAndLog));
                                            resId = R.raw.calls_log;
                                        } else if (!allowCancelCall || !allowReadCallLog) {
                                            builder.setMessage(getString("AllowReadCallLog", R.string.AllowReadCallLog));
                                            resId = R.raw.calls_log;
                                        } else {
                                            builder.setMessage(getString("AllowReadCall", R.string.AllowReadCall));
                                            resId = R.raw.incoming_calls;
                                        }
                                        builder.setTopAnimation(resId, 46, false, Theme.getColor(Theme.key_dialogTopBackground));
                                        permissionsDialog = showDialog(builder.create());
                                        confirmedNumber = true;
                                    } else {
                                        try {
                                            getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                    return;
                                }
                            }
                        }

                        confirmView.animateProgress(()->{
                            confirmView.dismiss();
                            AndroidUtilities.runOnUIThread(()-> {
                                onNextPressed(code);
                                floatingProgressView.sync(confirmView.floatingProgressView);
                            }, 150);
                        });
                    }
                });
                phoneNumberConfirmView.show();
                return;
            } else confirmedNumber = false;

            if (phoneNumberConfirmView != null) {
                phoneNumberConfirmView.dismiss();
            }

            boolean simcardAvailable = AndroidUtilities.isSimAvailable();
            boolean allowCall = true;
            boolean allowCancelCall = true;
            boolean allowReadCallLog = true;
            boolean allowReadPhoneNumbers = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && simcardAvailable) {
                allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                allowCancelCall = getParentActivity().checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
                allowReadCallLog = Build.VERSION.SDK_INT < Build.VERSION_CODES.P || getParentActivity().checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    allowReadPhoneNumbers = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;
                }
                if (checkPermissions) {
                    permissionsItems.clear();
                    if (!allowCall) {
                        permissionsItems.add(Manifest.permission.READ_PHONE_STATE);
                    }
                    if (!allowCancelCall) {
                        permissionsItems.add(Manifest.permission.CALL_PHONE);
                    }
                    if (!allowReadCallLog) {
                        permissionsItems.add(Manifest.permission.READ_CALL_LOG);
                    }
                    if (!allowReadPhoneNumbers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        permissionsItems.add(Manifest.permission.READ_PHONE_NUMBERS);
                    }
                    if (!permissionsItems.isEmpty()) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        if (preferences.getBoolean("firstlogin", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
                            preferences.edit().putBoolean("firstlogin", false).commit();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                            builder.setPositiveButton(getString("Continue", R.string.Continue), null);
                            int resId;
                            if (!allowCall && (!allowCancelCall || !allowReadCallLog)) {
                                builder.setMessage(getString("AllowReadCallAndLog", R.string.AllowReadCallAndLog));
                                resId = R.raw.calls_log;
                            } else if (!allowCancelCall || !allowReadCallLog) {
                                builder.setMessage(getString("AllowReadCallLog", R.string.AllowReadCallLog));
                                resId = R.raw.calls_log;
                            } else {
                                builder.setMessage(getString("AllowReadCall", R.string.AllowReadCall));
                                resId = R.raw.incoming_calls;
                            }
                            builder.setTopAnimation(resId, 46, false, Theme.getColor(Theme.key_dialogTopBackground));
                            permissionsDialog = showDialog(builder.create());
                            confirmedNumber = true;
                        } else {
                            try {
                                getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                        return;
                    }
                }
            }

            if (countryState == COUNTRY_STATE_EMPTY) {
                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("ChooseCountry", R.string.ChooseCountry));
                needHideProgress(false);
                return;
            } else if (countryState == COUNTRY_STATE_INVALID && !BuildVars.DEBUG_VERSION) {
                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("WrongCountry", R.string.WrongCountry));
                needHideProgress(false);
                return;
            }
            String phone = PhoneFormat.stripExceptNumbers("" + codeField.getText() + phoneField.getText());
            if (activityMode == MODE_LOGIN) {
                if (getParentActivity() instanceof LaunchActivity) {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        UserConfig userConfig = UserConfig.getInstance(a);
                        if (!userConfig.isClientActivated()) {
                            continue;
                        }
                        String userPhone = userConfig.getCurrentUser().phone;
                        if (PhoneNumberUtils.compare(phone, userPhone) && ConnectionsManager.getInstance(a).isTestBackend() == testBackend) {
                            final int num = a;
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(getString(R.string.AppName));
                            builder.setMessage(getString("AccountAlreadyLoggedIn", R.string.AccountAlreadyLoggedIn));
                            builder.setPositiveButton(getString("AccountSwitch", R.string.AccountSwitch), (dialog, which) -> {
                                if (UserConfig.selectedAccount != num) {
                                    ((LaunchActivity) getParentActivity()).switchToAccount(num, false);
                                }
                                finishFragment();
                            });
                            builder.setNegativeButton(getString("OK", R.string.OK), null);
                            showDialog(builder.create());
                            needHideProgress(false);
                            return;
                        }
                    }
                }
            }

            TLRPC.TL_codeSettings settings = new TLRPC.TL_codeSettings();
            settings.allow_flashcall = simcardAvailable && allowCall && allowCancelCall && allowReadCallLog;
            settings.allow_missed_call = simcardAvailable && allowCall;
            settings.allow_app_hash = settings.allow_firebase = PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices();
            if (forceDisableSafetyNet || TextUtils.isEmpty(BuildVars.SAFETYNET_KEY)) {
                settings.allow_firebase = false;
            }

            ArrayList<TLRPC.TL_auth_authorization> loginTokens = AuthTokensHelper.getSavedLogInTokens();
            if (loginTokens != null) {
                for (int i = 0; i < loginTokens.size(); i++) {
                    if (loginTokens.get(i).future_auth_token == null) {
                        continue;
                    }
                    if (settings.logout_tokens == null) {
                        settings.logout_tokens = new ArrayList<>();
                    }
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("login token to check " + new String(loginTokens.get(i).future_auth_token, StandardCharsets.UTF_8));
                    }
                    settings.logout_tokens.add(loginTokens.get(i).future_auth_token);
                    if (settings.logout_tokens.size() >= 20) {
                        break;
                    }
                }
            }
            ArrayList<TLRPC.TL_auth_loggedOut> tokens = AuthTokensHelper.getSavedLogOutTokens();
            if (tokens != null) {
                for (int i = 0; i < tokens.size(); i++) {
                    if (settings.logout_tokens == null) {
                        settings.logout_tokens = new ArrayList<>();
                    }
                    settings.logout_tokens.add(tokens.get(i).future_auth_token);
                    if (settings.logout_tokens.size() >= 20) {
                        break;
                    }
                }
                AuthTokensHelper.saveLogOutTokens(tokens);
            }
            if (settings.logout_tokens != null) {
                settings.flags |= 64;
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            preferences.edit().remove("sms_hash_code").apply();
            if (settings.allow_app_hash) {
                preferences.edit().putString("sms_hash", BuildVars.getSmsHash()).apply();
            } else {
                preferences.edit().remove("sms_hash").apply();
            }
            if (settings.allow_flashcall) {
                try {
                    String number = tm.getLine1Number();
                    if (!TextUtils.isEmpty(number)) {
                        settings.unknown_number = false;
                        settings.current_number = PhoneNumberUtils.compare(phone, number);
                    } else {
                        settings.unknown_number = true;
                        if (UserConfig.getActivatedAccountsCount() > 0) {
                            settings.allow_flashcall = false;
                        } else {
                            settings.current_number = false;
                        }
                    }
                } catch (Exception e) {
                    settings.unknown_number = true;
                    FileLog.e(e);
                }
            }

            TLObject req;
            if (activityMode == MODE_CHANGE_PHONE_NUMBER) {
                TLRPC.TL_account_sendChangePhoneCode changePhoneCode = new TLRPC.TL_account_sendChangePhoneCode();
                changePhoneCode.phone_number = phone;
                changePhoneCode.settings = settings;
                req = changePhoneCode;
            } else {
                ConnectionsManager.getInstance(currentAccount).cleanup(false);

                TLRPC.TL_auth_sendCode sendCode = new TLRPC.TL_auth_sendCode();
                sendCode.api_hash = BuildVars.APP_HASH;
                sendCode.api_id = BuildVars.APP_ID;
                sendCode.phone_number = phone;
                sendCode.settings = settings;
                req = sendCode;
            }

            Bundle params = new Bundle();
            params.putString("phone", "+" + codeField.getText() + " " + phoneField.getText());
            try {
                params.putString("ephone", "+" + PhoneFormat.stripExceptNumbers(codeField.getText().toString()) + " " + PhoneFormat.stripExceptNumbers(phoneField.getText().toString()));
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("ephone", "+" + phone);
            }
            params.putString("phoneFormated", phone);
            nextPressed = true;
            PhoneInputData phoneInputData = new PhoneInputData();
            phoneInputData.phoneNumber = "+" + codeField.getText() + " " + phoneField.getText();
            phoneInputData.country = currentCountry;
            phoneInputData.patterns = phoneFormatMap.get(codeField.getText().toString());
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    if (response instanceof TLRPC.TL_auth_sentCodeSuccess) {
                        TLRPC.auth_Authorization auth = ((TLRPC.TL_auth_sentCodeSuccess) response).authorization;
                        if (auth instanceof TLRPC.TL_auth_authorizationSignUpRequired) {
                            TLRPC.TL_auth_authorizationSignUpRequired authorization = (TLRPC.TL_auth_authorizationSignUpRequired) response;
                            if (authorization.terms_of_service != null) {
                                currentTermsOfService = authorization.terms_of_service;
                            }
                            setPage(VIEW_REGISTER, true, params, false);
                        } else {
                            onAuthSuccess((TLRPC.TL_auth_authorization) auth);
                        }
                    } else {
                        fillNextCodeParams(params, (TLRPC.auth_SentCode) response);
                    }
                } else {
                    if (error.text != null) {
                        if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                            TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                nextPressed = false;
                                showDoneButton(false, true);
                                if (error1 == null) {
                                    TLRPC.account_Password password = (TLRPC.account_Password) response1;
                                    if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                        AlertsCreator.showUpdateAppAlert(getParentActivity(), getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                        return;
                                    }
                                    Bundle bundle = new Bundle();
                                    SerializedData data = new SerializedData(password.getObjectSize());
                                    password.serializeToStream(data);
                                    bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                                    bundle.putString("phoneFormated", phone);
                                    setPage(VIEW_PASSWORD, true, bundle, false);
                                } else {
                                    needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error1.text);
                                }
                            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        } else if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowInvalidAlert(LoginActivity.this, phone, phoneInputData, false);
                        } else if (error.text.contains("PHONE_PASSWORD_FLOOD")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                        } else if (error.text.contains("PHONE_NUMBER_FLOOD")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("PhoneNumberFlood", R.string.PhoneNumberFlood));
                        } else if (error.text.contains("PHONE_NUMBER_BANNED")) {
                            needShowInvalidAlert(LoginActivity.this, phone, phoneInputData, true);
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidCode", R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            onBackPressed(true);
                            setPage(VIEW_PHONE_INPUT, true, null, true);
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                        } else if (error.code != -1000) {
                            AlertsCreator.processError(currentAccount, error, LoginActivity.this, req, phoneInputData.phoneNumber);
                        }
                    }
                }
                if (!isRequestingFirebaseSms) {
                    needHideProgress(false);
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagTryDifferentDc | ConnectionsManager.RequestFlagEnableUnauthorized);
            needShowProgress(reqId);
        }

        private boolean numberFilled;
        public void fillNumber() {
            if (numberFilled || activityMode != MODE_LOGIN) {
                return;
            }
            try {
                TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (AndroidUtilities.isSimAvailable()) {
                    boolean allowCall = true;
                    boolean allowReadPhoneNumbers = true;
                    if (Build.VERSION.SDK_INT >= 23) {
                        allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            allowReadPhoneNumbers = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;
                        }
                        if (checkShowPermissions && (!allowCall || !allowReadPhoneNumbers)) {
                            permissionsShowItems.clear();
                            if (!allowCall) {
                                permissionsShowItems.add(Manifest.permission.READ_PHONE_STATE);
                            }
                            if (!allowReadPhoneNumbers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                permissionsShowItems.add(Manifest.permission.READ_PHONE_NUMBERS);
                            }
                            if (!permissionsShowItems.isEmpty()) {
                                List<String> callbackPermissionItems = new ArrayList<>(permissionsShowItems);
                                Runnable r = () -> {
                                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                    if (preferences.getBoolean("firstloginshow", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                                        preferences.edit().putBoolean("firstloginshow", false).commit();
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                                        builder.setTopAnimation(R.raw.incoming_calls, 46, false, Theme.getColor(Theme.key_dialogTopBackground));
                                        builder.setPositiveButton(getString("Continue", R.string.Continue), null);
                                        builder.setMessage(getString("AllowFillNumber", R.string.AllowFillNumber));
                                        permissionsShowDialog = showDialog(builder.create(), true, null);
                                        needRequestPermissions = true;
                                    } else {
                                        getParentActivity().requestPermissions(callbackPermissionItems.toArray(new String[0]), BasePermissionsActivity.REQUEST_CODE_CALLS);
                                    }
                                };
                                if (isAnimatingIntro) {
                                    animationFinishCallback = r;
                                } else {
                                    r.run();
                                }
                            }
                            return;
                        }
                    }
                    numberFilled = true;
                    if (!newAccount && allowCall && allowReadPhoneNumbers) {
                        codeField.setAlpha(0);
                        phoneField.setAlpha(0);

                        String number = PhoneFormat.stripExceptNumbers(tm.getLine1Number());
                        String textToSet = null;
                        boolean ok = false;
                        if (!TextUtils.isEmpty(number)) {
                            if (number.length() > 4) {
                                for (int a = 4; a >= 1; a--) {
                                    String sub = number.substring(0, a);

                                    CountrySelectActivity.Country country;
                                    List<CountrySelectActivity.Country> list = codesMap.get(sub);
                                    if (list == null) {
                                        country = null;
                                    } else if (list.size() > 1) {
                                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                        String lastMatched = preferences.getString("phone_code_last_matched_" + sub, null);

                                        country = list.get(list.size() - 1);
                                        if (lastMatched != null) {
                                            for (CountrySelectActivity.Country c : countriesArray) {
                                                if (Objects.equals(c.shortname, lastMatched)) {
                                                    country = c;
                                                    break;
                                                }
                                            }
                                        }
                                    } else {
                                        country = list.get(0);
                                    }

                                    if (country != null) {
                                        ok = true;
                                        textToSet = number.substring(a);
                                        codeField.setText(sub);
                                        break;
                                    }
                                }
                                if (!ok) {
                                    textToSet = number.substring(1);
                                    codeField.setText(number.substring(0, 1));
                                }
                            }
                            if (textToSet != null) {
                                phoneField.requestFocus();
                                phoneField.setText(textToSet);
                                phoneField.setSelection(phoneField.length());
                            }
                        }

                        if (phoneField.length() > 0) {
                            AnimatorSet set = new AnimatorSet().setDuration(300);
                            set.playTogether(ObjectAnimator.ofFloat(codeField, View.ALPHA, 1f),
                                    ObjectAnimator.ofFloat(phoneField, View.ALPHA, 1f));
                            set.start();

                            confirmedNumber = true;
                        } else {
                            codeField.setAlpha(1);
                            phoneField.setAlpha(1);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onShow() {
            super.onShow();
            fillNumber();
            if (syncContactsBox != null) {
                syncContactsBox.setChecked(syncContacts, false);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (phoneField != null) {
                    if (needRequestPermissions) {
                        codeField.clearFocus();
                        phoneField.clearFocus();
                    } else {
                        if (codeField.length() != 0) {
                            phoneField.requestFocus();
                            if (!numberFilled) {
                                phoneField.setSelection(phoneField.length());
                            }
                            showKeyboard(phoneField);
                        } else {
                            codeField.requestFocus();
                            showKeyboard(codeField);
                        }
                    }
                }
            }, SHOW_DELAY);
        }

        @Override
        public String getHeaderName() {
            return getString("YourPhone", R.string.YourPhone);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("phoneview_code", code);
            }
            String phone = phoneField.getText().toString();
            if (phone.length() != 0) {
                bundle.putString("phoneview_phone", phone);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            String code = bundle.getString("phoneview_code");
            if (code != null) {
                codeField.setText(code);
            }
            String phone = bundle.getString("phoneview_phone");
            if (phone != null) {
                phoneField.setText(phone);
            }
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.emojiLoaded) {
                countryButton.getCurrentView().invalidate();
            }
        }
    }

    public class LoginActivitySmsView extends SlideView implements NotificationCenter.NotificationCenterDelegate {
        /* package */ RLottieDrawable hintDrawable;

        private String phone;
        private String phoneHash;
        private String requestPhone;
        private String emailPhone;
        private CodeFieldContainer codeFieldContainer;
        private TextView prevTypeTextView;
        private TextView confirmTextView;
        private TextView titleTextView;
        private ImageView blackImageView;
        private RLottieImageView blueImageView;
        private LoadingTextView timeText;

        private FrameLayout bottomContainer;
        private ViewSwitcher errorViewSwitcher;
        private LoadingTextView problemText;
        private FrameLayout problemFrame;
        private TextView wrongCode;
        private LinearLayout openFragmentButton;
        private RLottieImageView openFragmentImageView;
        private TextView openFragmentButtonText;

        private Bundle currentParams;
        private ProgressView progressView;
        private TextView prefixTextView;

        private TextView missedCallDescriptionSubtitle;
        private TextView missedCallDescriptionSubtitle2;
        private ImageView missedCallArrowIcon, missedCallPhoneIcon;

        private RLottieDrawable starsToDotsDrawable;
        private RLottieDrawable dotsDrawable;
        private RLottieDrawable dotsToStarsDrawable;
        private boolean isDotsAnimationVisible;

        private Timer timeTimer;
        private Timer codeTimer;
        private int openTime;
        private final Object timerSync = new Object();
        private int time = 60000;
        private int codeTime = 15000;
        private double lastCurrentTime;
        private double lastCodeTime;
        private boolean ignoreOnTextChange;
        private boolean waitingForEvent;
        private boolean nextPressed;
        private String lastError = "";

        @AuthType
        private int currentType;
        @AuthType
        private int nextType;
        @AuthType
        private int prevType;

        private boolean isResendingCode = false;

        private String pattern = "*";
        private String prefix = "";
        private String catchedPhone;
        private int length;
        private String url;

        private Bundle nextCodeParams;
        private TLRPC.TL_auth_sentCode nextCodeAuth;

        private boolean postedErrorColorTimeout;
        private Runnable errorColorTimeout = () -> {
            postedErrorColorTimeout = false;
            for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                codeFieldContainer.codeField[i].animateErrorProgress(0);
            }

            View v = currentType == AUTH_TYPE_FRAGMENT_SMS ? openFragmentButton : problemFrame;
            if (errorViewSwitcher.getCurrentView() != v) {
                errorViewSwitcher.showNext();
            }
        };

        public LoginActivitySmsView(Context context, @AuthType int type) {
            super(context);

            currentType = type;
            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);

            titleTextView = new TextView(context);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

            String overrideTitle;
            switch (activityMode) {
                default:
                case MODE_LOGIN:
                    overrideTitle = null;
                    break;
                case MODE_CANCEL_ACCOUNT_DELETION:
                    overrideTitle = getString(R.string.CancelAccountReset);
                    break;
            }
            FrameLayout centerContainer = null;
            if (currentType == AUTH_TYPE_MISSED_CALL) {
                titleTextView.setText(overrideTitle != null ? overrideTitle : getString(R.string.MissedCallDescriptionTitle));

                FrameLayout frameLayout = new FrameLayout(context);
                missedCallArrowIcon = new ImageView(context);
                missedCallPhoneIcon = new ImageView(context);
                frameLayout.addView(missedCallArrowIcon);
                frameLayout.addView(missedCallPhoneIcon);

                missedCallArrowIcon.setImageResource(R.drawable.login_arrow1);
                missedCallPhoneIcon.setImageResource(R.drawable.login_phone1);

                addView(frameLayout, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
                addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));

                missedCallDescriptionSubtitle = new TextView(context);
                missedCallDescriptionSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                missedCallDescriptionSubtitle.setGravity(Gravity.CENTER_HORIZONTAL);
                missedCallDescriptionSubtitle.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                missedCallDescriptionSubtitle.setText(AndroidUtilities.replaceTags(getString(R.string.MissedCallDescriptionSubtitle)));

                addView(missedCallDescriptionSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 36, 16, 36, 0));

                codeFieldContainer = new CodeFieldContainer(context) {
                    @Override
                    protected void processNextPressed() {
                        onNextPressed(null);
                    }
                };

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                prefixTextView = new TextView(context);
                prefixTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                prefixTextView.setMaxLines(1);
                prefixTextView.setTypeface(AndroidUtilities.bold());
                prefixTextView.setPadding(0, 0, 0, 0);
                prefixTextView.setGravity(Gravity.CENTER_VERTICAL);

                linearLayout.addView(prefixTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
                linearLayout.addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

                addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 34, Gravity.CENTER_HORIZONTAL, 0, 28, 0, 0));

                missedCallDescriptionSubtitle2 = new TextView(context);
                missedCallDescriptionSubtitle2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                missedCallDescriptionSubtitle2.setGravity(Gravity.CENTER_HORIZONTAL);
                missedCallDescriptionSubtitle2.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                missedCallDescriptionSubtitle2.setText(AndroidUtilities.replaceTags(getString(R.string.MissedCallDescriptionSubtitle2)));

                addView(missedCallDescriptionSubtitle2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 36, 28, 36, 12));
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                confirmTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                centerContainer = new FrameLayout(context);
                addView(centerContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

                LinearLayout innerLinearLayout = new LinearLayout(context);
                innerLinearLayout.setOrientation(VERTICAL);
                innerLinearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
                centerContainer.addView(innerLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) innerLinearLayout.getLayoutParams();
                layoutParams.bottomMargin = AndroidUtilities.isTablet() ? 0 : AndroidUtilities.statusBarHeight;

                FrameLayout frameLayout = new FrameLayout(context);
                innerLinearLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

                blueImageView = new RLottieImageView(context);
                hintDrawable = new RLottieDrawable(R.raw.phone_flash_call, String.valueOf(R.raw.phone_flash_call), AndroidUtilities.dp(64), AndroidUtilities.dp(64), true, null);
                blueImageView.setAnimation(hintDrawable);
                frameLayout.addView(blueImageView, LayoutHelper.createFrame(64, 64));

                titleTextView.setText(overrideTitle != null ? overrideTitle : getString(R.string.YourCode));
                innerLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
                innerLinearLayout.addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
            } else {
                confirmTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

                int size = currentType == AUTH_TYPE_MESSAGE ? 128 : 64;
                if (currentType == AUTH_TYPE_MESSAGE) {
                    hintDrawable = new RLottieDrawable(R.raw.code_laptop, String.valueOf(R.raw.code_laptop), AndroidUtilities.dp(size), AndroidUtilities.dp(size), true, null);
                } else {
                    hintDrawable = new RLottieDrawable(R.raw.sms_incoming_info, String.valueOf(R.raw.sms_incoming_info), AndroidUtilities.dp(size), AndroidUtilities.dp(size), true, null);

                    starsToDotsDrawable = new RLottieDrawable(R.raw.phone_stars_to_dots, String.valueOf(R.raw.phone_stars_to_dots), AndroidUtilities.dp(size), AndroidUtilities.dp(size), true, null);
                    dotsDrawable = new RLottieDrawable(R.raw.phone_dots, String.valueOf(R.raw.phone_dots), AndroidUtilities.dp(size), AndroidUtilities.dp(size), true, null);
                    dotsToStarsDrawable = new RLottieDrawable(R.raw.phone_dots_to_stars, String.valueOf(R.raw.phone_dots_to_stars), AndroidUtilities.dp(size), AndroidUtilities.dp(size), true, null);
                }
                blueImageView = new RLottieImageView(context);
                blueImageView.setAnimation(hintDrawable);
                if (currentType == AUTH_TYPE_MESSAGE && !AndroidUtilities.isSmallScreen()) {
                    blueImageView.setTranslationY(-AndroidUtilities.dp(24));
                }
                frameLayout.addView(blueImageView, LayoutHelper.createFrame(size, size, Gravity.LEFT | Gravity.TOP, 0, 0, 0, currentType == AUTH_TYPE_MESSAGE && !AndroidUtilities.isSmallScreen() ? -AndroidUtilities.dp(16) : 0));
                titleTextView.setText(overrideTitle != null ? overrideTitle : getString(currentType == AUTH_TYPE_MESSAGE ? R.string.SentAppCodeTitle : R.string.SentSmsCodeTitle));
                addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));
                int sideMargin = currentType == AUTH_TYPE_FRAGMENT_SMS ? 16 : 0;
                addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, sideMargin, 17, sideMargin, 0));
            }
            if (currentType != AUTH_TYPE_MISSED_CALL) {
                codeFieldContainer = new CodeFieldContainer(context) {
                    @Override
                    protected void processNextPressed() {
                        onNextPressed(null);
                    }
                };

                addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));
            }
            if (currentType == AUTH_TYPE_FLASH_CALL) {
                codeFieldContainer.setVisibility(GONE);
            }

            prevTypeTextView = new LoadingTextView(context);
            prevTypeTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            prevTypeTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteValueText));
            prevTypeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            prevTypeTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            prevTypeTextView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(16));
            prevTypeTextView.setOnClickListener(v -> onBackPressed(true));
            addView(prevTypeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));
            prevTypeTextView.setVisibility(View.GONE);

            problemFrame = new FrameLayout(context);

            timeText = new LoadingTextView(context) {
                @Override
                protected boolean isResendingCode() {
                    return isResendingCode;
                }
                @Override
                protected boolean isRippleEnabled() {
                    return getVisibility() == View.VISIBLE && !(time > 0 && timeTimer != null);
                }
            };
            timeText.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            timeText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(16));
            timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            timeText.setGravity(Gravity.TOP | Gravity.LEFT);
            timeText.setOnClickListener(v -> {
//                if (isRequestingFirebaseSms || isResendingCode) {
//                    return;
//                }
                if (time > 0 && timeTimer != null) {
                    return;
                }
                isResendingCode = true;
                timeText.invalidate();
                timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));

                if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD || nextType == AUTH_TYPE_MISSED_CALL || nextType == AUTH_TYPE_FRAGMENT_SMS) {
//                    timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                    if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                        timeText.setText(getString(R.string.Calling));
                    } else {
                        timeText.setText(getString(R.string.SendingSms));
                    }
                    Bundle params = new Bundle();
                    params.putString("phone", phone);
                    params.putString("ephone", emailPhone);
                    params.putString("phoneFormated", requestPhone);
                    params.putInt("prevType", currentType);

                    createCodeTimer();
                    TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
                    req.phone_number = requestPhone;
                    req.phone_code_hash = phoneHash;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                        if (response != null) {
                            AndroidUtilities.runOnUIThread(() -> {
                                nextCodeParams = params;
                                nextCodeAuth = (TLRPC.TL_auth_sentCode) response;
                                if (nextCodeAuth.type instanceof TLRPC.TL_auth_sentCodeTypeSmsPhrase) {
                                    nextType = AUTH_TYPE_PHRASE;
                                } else if (nextCodeAuth.type instanceof TLRPC.TL_auth_sentCodeTypeSmsWord) {
                                    nextType = AUTH_TYPE_WORD;
                                }
                                fillNextCodeParams(nextCodeParams, nextCodeAuth);
                            });
                        } else if (error != null && error.text != null) {
                            AndroidUtilities.runOnUIThread(() -> lastError = error.text);
                        }
                    }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else if (nextType == AUTH_TYPE_FLASH_CALL) {
                    AndroidUtilities.setWaitingForSms(false);
                    NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                    waitingForEvent = false;
                    destroyCodeTimer();
                    resendCode();
                }
            });
            problemFrame.addView(timeText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            errorViewSwitcher = new ViewSwitcher(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };

            if (currentType != AUTH_TYPE_FRAGMENT_SMS) {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.text_in);
                anim.setInterpolator(Easings.easeInOutQuad);
                errorViewSwitcher.setInAnimation(anim);

                anim = AnimationUtils.loadAnimation(context, R.anim.text_out);
                anim.setInterpolator(Easings.easeInOutQuad);
                errorViewSwitcher.setOutAnimation(anim);

                problemText = new LoadingTextView(context) {
                    @Override
                    protected boolean isResendingCode() {
                        return isResendingCode;
                    }
                    @Override
                    protected boolean isRippleEnabled() {
                        return isClickable() && getVisibility() == View.VISIBLE && !(nextPressed || timeText != null && timeText.getVisibility() != View.GONE || isResendingCode);
                    }
                };
                problemText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
                problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                problemText.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                problemText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(16));
                problemFrame.addView(problemText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                errorViewSwitcher.addView(problemFrame, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            } else {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.scale_in);
                anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
                errorViewSwitcher.setInAnimation(anim);

                anim = AnimationUtils.loadAnimation(context, R.anim.scale_out);
                anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
                errorViewSwitcher.setOutAnimation(anim);

                openFragmentButton = new LinearLayout(context);
                openFragmentButton.setOrientation(HORIZONTAL);
                openFragmentButton.setGravity(Gravity.CENTER);
                openFragmentButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
                openFragmentButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_changephoneinfo_image2), Theme.getColor(Theme.key_chats_actionPressedBackground)));
                openFragmentButton.setOnClickListener(v -> {
                    try {
                        getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                errorViewSwitcher.addView(openFragmentButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 52));

                openFragmentImageView = new RLottieImageView(context);
                openFragmentImageView.setAnimation(R.raw.fragment, 36, 36);
                openFragmentButton.addView(openFragmentImageView, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL, 0, 0, 2, 0));

                openFragmentButtonText = new TextView(context);
                openFragmentButtonText.setText(getString(R.string.OpenFragment));
                openFragmentButtonText.setTextColor(Color.WHITE);
                openFragmentButtonText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                openFragmentButtonText.setGravity(Gravity.CENTER);
                openFragmentButtonText.setTypeface(AndroidUtilities.bold());
                openFragmentButton.addView(openFragmentButtonText);
            }

            wrongCode = new TextView(context);
            wrongCode.setText(getString(R.string.WrongCode));
            wrongCode.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongCode.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            wrongCode.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            wrongCode.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
            errorViewSwitcher.addView(wrongCode, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            if (centerContainer == null) {
                bottomContainer = new FrameLayout(context);
                bottomContainer.addView(errorViewSwitcher, LayoutHelper.createFrame(currentType == VIEW_CODE_FRAGMENT_SMS ? LayoutHelper.MATCH_PARENT : LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 32));
                addView(bottomContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
            } else {
                centerContainer.addView(errorViewSwitcher, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 32));
            }
            VerticalPositionAutoAnimator.attach(errorViewSwitcher);

            if (currentType != AUTH_TYPE_FRAGMENT_SMS) {
                problemText.setOnClickListener(v -> {
                    if (nextCodeParams != null && nextCodeAuth != null) {
                        fillNextCodeParams(nextCodeParams, nextCodeAuth);
                        return;
                    }
                    if (nextPressed || timeText != null && timeText.getVisibility() != View.GONE || isResendingCode) {
                        return;
                    }
                    boolean email = nextType == 0;
                    if (!email) {
                        if (radialProgressView.getTag() != null) {
                            return;
                        }
                        resendCode();
                    } else {
                        TLRPC.TL_auth_reportMissingCode req = new TLRPC.TL_auth_reportMissingCode();
                        req.phone_number = requestPhone;
                        req.phone_code_hash = phoneHash;
                        req.mnc = "";
                        String networkOperator = null;
                        try {
                            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                            networkOperator = tm.getNetworkOperator();
                            if (!TextUtils.isEmpty(networkOperator)) {
                                final String mcc = networkOperator.substring(0, 3);
                                final String mnc = networkOperator.substring(3);
//                                req.mcc = mcc;
                                req.mnc = mnc;
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        String finalNetworkOperator = networkOperator;
                        getConnectionsManager().sendRequest(req, null, ConnectionsManager.RequestFlagWithoutLogin);
                        new AlertDialog.Builder(context)
                                .setTitle(getString(R.string.RestorePasswordNoEmailTitle))
                                .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DidNotGetTheCodeInfo", R.string.DidNotGetTheCodeInfo, phone)))
                                .setNeutralButton(getString(R.string.DidNotGetTheCodeHelpButton), (dialog, which) -> {
                                    try {
                                        PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                                        String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                                        Intent mailer = new Intent(Intent.ACTION_SENDTO);
                                        mailer.setData(Uri.parse("mailto:"));
                                        mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{"sms@telegram.org"});
                                        mailer.putExtra(Intent.EXTRA_SUBJECT, "Android registration/login issue " + version + " " + emailPhone);
                                        mailer.putExtra(Intent.EXTRA_TEXT, "Phone: " + requestPhone + "\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + (finalNetworkOperator != null ? "\nOperator: " + finalNetworkOperator : "") + "\nLocale: " + Locale.getDefault() + "\nError: " + lastError);
                                        getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
                                    } catch (Exception e) {
                                        needShowAlert(getString(R.string.AppName), getString("NoMailInstalled", R.string.NoMailInstalled));
                                    }
                                })
                                .setPositiveButton(getString(R.string.Close), null)
                                .setNegativeButton(getString(R.string.DidNotGetTheCodeEditNumberButton), (dialog, which) -> setPage(VIEW_PHONE_INPUT, true, null, true))
                                .show();
                    }
                });
            }
        }

        @Override
        public void updateColors() {
            confirmTextView.setTextColor(Theme.getColor(isInCancelAccountDeletionMode() ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setLinkTextColor(Theme.getColor(Theme.key_chats_actionBackground));
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

            if (currentType == AUTH_TYPE_MISSED_CALL) {
                missedCallDescriptionSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                missedCallDescriptionSubtitle2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                missedCallArrowIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), PorterDuff.Mode.SRC_IN));
                missedCallPhoneIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
                prefixTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }

            applyLottieColors(hintDrawable);
            applyLottieColors(starsToDotsDrawable);
            applyLottieColors(dotsDrawable);
            applyLottieColors(dotsToStarsDrawable);

            if (codeFieldContainer != null) {
                codeFieldContainer.invalidate();
            }

            Integer timeTextColorTag = (Integer) timeText.getTag();
            if (timeTextColorTag == null) {
                timeTextColorTag = Theme.key_windowBackgroundWhiteGrayText6;
            }
            timeText.setTextColor(Theme.getColor(timeTextColorTag));

            if (currentType != AUTH_TYPE_FRAGMENT_SMS) {
                problemText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            }
            wrongCode.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }

        private void applyLottieColors(RLottieDrawable drawable) {
            if (drawable != null) {
                drawable.setLayerColor("Bubble.**", Theme.getColor(Theme.key_chats_actionBackground));
                drawable.setLayerColor("Phone.**", Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                drawable.setLayerColor("Note.**", Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }

        @Override
        public boolean hasCustomKeyboard() {
            return currentType != AUTH_TYPE_FLASH_CALL;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        private void resendCode() {
            if (nextPressed || isResendingCode || isRequestingFirebaseSms) {
                return;
            }

            isResendingCode = true;
            timeText.invalidate();
            problemText.invalidate();

            final Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);
            params.putInt("prevType", currentType);

            nextPressed = true;

            TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    nextCodeParams = params;
                    nextCodeAuth = (TLRPC.TL_auth_sentCode) response;
                    if (nextCodeAuth.type instanceof TLRPC.TL_auth_sentCodeTypeSmsPhrase) {
                        nextType = AUTH_TYPE_PHRASE;
                    } else if (nextCodeAuth.type instanceof TLRPC.TL_auth_sentCodeTypeSmsWord) {
                        nextType = AUTH_TYPE_WORD;
                    }
                    fillNextCodeParams(nextCodeParams, nextCodeAuth);
                } else {
                    if (error.text != null) {
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            onBackPressed(true);
                            setPage(VIEW_PHONE_INPUT, true, null, true);
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.FloodWait));
                        } else if (error.code != -1000) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.ErrorOccurred) + "\n" + error.text);
                        }
                    }
                }
                tryHideProgress(false);
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            tryShowProgress(reqId);
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);

            if (codeFieldContainer != null && codeFieldContainer.codeField != null) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        f.setShowSoftInputOnFocusCompat(!(hasCustomKeyboard() && !isCustomKeyboardForceDisabled()));
                    }
                }
            }
        }

        private void tryShowProgress(int reqId) {
            tryShowProgress(reqId, true);
        }

        private void tryShowProgress(int reqId, boolean animate) {
            if (starsToDotsDrawable != null) {
                if (isDotsAnimationVisible) {
                    return;
                }
                isDotsAnimationVisible = true;
                if (hintDrawable.getCurrentFrame() != hintDrawable.getFramesCount() - 1) {
                    hintDrawable.setOnAnimationEndListener(()-> AndroidUtilities.runOnUIThread(()-> tryShowProgress(reqId, animate)));
                    return;
                }

                starsToDotsDrawable.setOnAnimationEndListener(()-> AndroidUtilities.runOnUIThread(()->{
                    blueImageView.setAutoRepeat(true);
                    dotsDrawable.setCurrentFrame(0, false);
                    dotsDrawable.setAutoRepeat(1);
                    blueImageView.setAnimation(dotsDrawable);
                    blueImageView.playAnimation();
                }));
                blueImageView.setAutoRepeat(false);
                starsToDotsDrawable.setCurrentFrame(0, false);
                blueImageView.setAnimation(starsToDotsDrawable);
                blueImageView.playAnimation();
                return;
            }
            needShowProgress(reqId, animate);
        }

        private void tryHideProgress(boolean cancel) {
            tryHideProgress(cancel, true);
        }

        private void tryHideProgress(boolean cancel, boolean animate) {
            if (starsToDotsDrawable != null) {
                if (!isDotsAnimationVisible) {
                    return;
                }
                isDotsAnimationVisible = false;
                blueImageView.setAutoRepeat(false);
                dotsDrawable.setAutoRepeat(0);
                dotsDrawable.setOnFinishCallback(()-> AndroidUtilities.runOnUIThread(()->{
                    dotsToStarsDrawable.setOnAnimationEndListener(()-> AndroidUtilities.runOnUIThread(()->{
                        blueImageView.setAutoRepeat(false);
                        blueImageView.setAnimation(hintDrawable);
                    }));

                    blueImageView.setAutoRepeat(false);
                    dotsToStarsDrawable.setCurrentFrame(0, false);
                    blueImageView.setAnimation(dotsToStarsDrawable);
                    blueImageView.playAnimation();
                }), dotsDrawable.getFramesCount() - 1);
                return;
            }
            needHideProgress(cancel, animate);
        }

        @Override
        public String getHeaderName() {
            if (currentType == AUTH_TYPE_FLASH_CALL || currentType == AUTH_TYPE_MISSED_CALL) {
                return phone;
            } else {
                return getString("YourCode", R.string.YourCode);
            }
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                if (nextCodeParams != null && nextCodeAuth != null) {
                    setProblemTextVisible(true);
                    timeText.setVisibility(GONE);
                    if (problemText != null) {
                        problemText.setVisibility(VISIBLE);
                        problemText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
                        int resId;
                        if (nextType == AUTH_TYPE_PHRASE) {
                            resId = R.string.ReturnEnteringPhrase;
                        } else if (nextType == AUTH_TYPE_WORD) {
                            resId = R.string.ReturnEnteringWord;
                        } else {
                            resId = R.string.ReturnEnteringSMS;
                        }
                        problemText.setText(AndroidUtilities.replaceArrows(getString(resId), true, dp(1), dp(1)));
                    }
                }
                return;
            }
            waitingForEvent = true;
            if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.setWaitingForCall(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveCall);
                if (restore) {
                    AndroidUtilities.runOnUIThread(() -> {
                        CallReceiver.checkLastReceivedCall();
                    });
                }
            }

            currentParams = params;
            phone = params.getString("phone");
            emailPhone = params.getString("ephone");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            time = params.getInt("timeout");
            openTime = (int) (System.currentTimeMillis() / 1000);
            nextType = params.getInt("nextType");
            pattern = params.getString("pattern");
            prefix = params.getString("prefix");
            length = params.getInt("length");
            prevType = params.getInt("prevType", 0);
            if (length == 0) {
                length = 5;
            }
            url = params.getString("url");

            nextCodeParams = null;
            nextCodeAuth = null;

            codeFieldContainer.setNumbersCount(length, currentType);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    f.setShowSoftInputOnFocusCompat(!(hasCustomKeyboard() && !isCustomKeyboardForceDisabled()));
                }
                f.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (postedErrorColorTimeout) {
                            removeCallbacks(errorColorTimeout);
                            errorColorTimeout.run();
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

                f.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        keyboardView.setEditText((EditText) v);
                        keyboardView.setDispatchBackWhenEmpty(true);
                    }
                });
            }

            if (prevType == AUTH_TYPE_PHRASE) {
                prevTypeTextView.setVisibility(View.VISIBLE);
                prevTypeTextView.setText(AndroidUtilities.replaceArrows(getString(R.string.BackEnteringPhrase), true, dp(-1), dp(1)));
            } else if (prevType == AUTH_TYPE_WORD) {
                prevTypeTextView.setVisibility(View.VISIBLE);
                prevTypeTextView.setText(AndroidUtilities.replaceArrows(getString(R.string.BackEnteringWord), true, dp(-1), dp(1)));
            } else {
                prevTypeTextView.setVisibility(View.GONE);
            }

            if (progressView != null) {
                progressView.setVisibility(nextType != 0 ? VISIBLE : GONE);
            }

            if (phone == null) {
                return;
            }

            String number = PhoneFormat.getInstance().format(phone);
            CharSequence str = "";
            if (isInCancelAccountDeletionMode()) {
                SpannableStringBuilder spanned = new SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.formatString("CancelAccountResetInfo2", R.string.CancelAccountResetInfo2, PhoneFormat.getInstance().format("+" + number))));

                int startIndex = TextUtils.indexOf(spanned, '*');
                int lastIndex = TextUtils.lastIndexOf(spanned, '*');
                if (startIndex != -1 && lastIndex != -1 && startIndex != lastIndex) {
                    confirmTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                    spanned.replace(lastIndex, lastIndex + 1, "");
                    spanned.replace(startIndex, startIndex + 1, "");
                    spanned.setSpan(new URLSpanNoUnderline("tg://settings/change_number"), startIndex, lastIndex - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                str = spanned;
            } else {
                if (currentType == AUTH_TYPE_MESSAGE) {
                    str = AndroidUtilities.replaceTags(LocaleController.formatString("SentAppCodeWithPhone", R.string.SentAppCodeWithPhone, LocaleController.addNbsp(number)));
                } else if (currentType == AUTH_TYPE_SMS) {
                    str = AndroidUtilities.replaceTags(LocaleController.formatString("SentSmsCode", R.string.SentSmsCode, LocaleController.addNbsp(number)));
                } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                    str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallCode", R.string.SentCallCode, LocaleController.addNbsp(number)));
                } else if (currentType == AUTH_TYPE_CALL) {
                    str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallOnly", R.string.SentCallOnly, LocaleController.addNbsp(number)));
                } else if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                    str = AndroidUtilities.replaceTags(LocaleController.formatString("SentFragmentCode", R.string.SentFragmentCode, LocaleController.addNbsp(number)));
                }
            }
            confirmTextView.setText(str);

            if (currentType != AUTH_TYPE_FRAGMENT_SMS) {
                if (currentType == AUTH_TYPE_MESSAGE) {
                    if (nextType == AUTH_TYPE_FLASH_CALL || nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                        problemText.setText(getString("DidNotGetTheCodePhone", R.string.DidNotGetTheCodePhone));
                    } else if (nextType == AUTH_TYPE_FRAGMENT_SMS) {
                        problemText.setText(getString("DidNotGetTheCodeFragment", R.string.DidNotGetTheCodeFragment));
                    } else if (nextType == 0) {
                        problemText.setText(getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
                    } else {
                        problemText.setText(getString("DidNotGetTheCodeSms", R.string.DidNotGetTheCodeSms));
                    }
                } else {
                    problemText.setText(getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
                }
            }

            if (currentType != AUTH_TYPE_FLASH_CALL) {
                showKeyboard(codeFieldContainer.codeField[0]);
                codeFieldContainer.codeField[0].requestFocus();
            } else {
                AndroidUtilities.hideKeyboard(codeFieldContainer.codeField[0]);
            }

            destroyTimer();
            destroyCodeTimer();

            lastCurrentTime = System.currentTimeMillis();
            if (currentType == AUTH_TYPE_MESSAGE) {
                setProblemTextVisible(true);
                timeText.setVisibility(GONE);
                if (problemText != null) {
                    problemText.setVisibility(VISIBLE);
                }
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD || nextType == AUTH_TYPE_MISSED_CALL) {
                    setProblemTextVisible(false);
                    timeText.setVisibility(VISIBLE);
                    problemText.setVisibility(GONE);
                    if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                        timeText.setText(LocaleController.formatString("CallAvailableIn", R.string.CallAvailableIn, 1, 0));
                    } else if (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) {
                        timeText.setText(LocaleController.formatString("SmsAvailableIn", R.string.SmsAvailableIn, 1, 0));
                    }
                }
                String callLogNumber = restore ? AndroidUtilities.obtainLoginPhoneCall(pattern) : null;
                if (callLogNumber != null) {
                    onNextPressed(callLogNumber);
                } else if (catchedPhone != null) {
                    onNextPressed(catchedPhone);
                } else if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD || nextType == AUTH_TYPE_MISSED_CALL) {
                    createTimer();
                }
            } else if (currentType == AUTH_TYPE_SMS && (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD || nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL)) {
                if (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) {
                    timeText.setText(LocaleController.formatString("SmsAvailableIn", R.string.SmsAvailableIn, 1, 0));
                } else {
                    timeText.setText(LocaleController.formatString("CallAvailableIn", R.string.CallAvailableIn, 2, 0));
                }
                setProblemTextVisible(time < 1000);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
                if (problemText != null) {
                    problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                }

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                String hash = preferences.getString("sms_hash", null);
                String savedCode = null;
                if (!TextUtils.isEmpty(hash)) {
                    savedCode = preferences.getString("sms_hash_code", null);
                    if (savedCode != null && savedCode.contains(hash + "|") && !newAccount) {
                        savedCode = savedCode.substring(savedCode.indexOf('|') + 1);
                    } else {
                        savedCode = null;
                    }
                }
                if (savedCode != null) {
                    codeFieldContainer.setCode(savedCode);
                    onNextPressed(null);
                } else {
                    createTimer();
                }
            } else if (currentType == AUTH_TYPE_CALL && (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_MISSED_CALL || nextType == AUTH_TYPE_WORD)) {
                if (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) {
                    timeText.setText(LocaleController.formatString("SmsAvailableIn", R.string.SmsAvailableIn, 1, 0));
                } else {
                    timeText.setText(LocaleController.formatString("CallAvailableIn", R.string.CallAvailableIn, 2, 0));
                }
                setProblemTextVisible(time < 1000);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
                if (problemText != null) {
                    problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                }
                createTimer();
            } else if (currentType == AUTH_TYPE_MISSED_CALL) {
                if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD || nextType == AUTH_TYPE_MISSED_CALL) {
                    setProblemTextVisible(false);
                    timeText.setVisibility(VISIBLE);
                    problemText.setVisibility(GONE);
                    if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                        timeText.setText(LocaleController.formatString("CallAvailableIn", R.string.CallAvailableIn, 1, 0));
                    } else if (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) {
                        timeText.setText(LocaleController.formatString("SmsAvailableIn", R.string.SmsAvailableIn, 1, 0));
                    }
                    createTimer();
                }
            } else {
                timeText.setVisibility(GONE);
                if (problemText != null) {
                    problemText.setVisibility(VISIBLE);
                }
                setProblemTextVisible(false);
                createCodeTimer();
            }

            if (currentType == AUTH_TYPE_MISSED_CALL) {
                String pref = prefix;
                for  (int i = 0; i < length; i++) {
                    pref += "0";
                }
                pref = PhoneFormat.getInstance().format("+" + pref);
                for  (int i = 0; i < length; i++) {
                    int index = pref.lastIndexOf("0");
                    if (index >= 0) {
                        pref = pref.substring(0,  index);
                    }
                }
                pref = pref.replaceAll("\\)", "");
                pref = pref.replaceAll("\\(", "");
                prefixTextView.setText(pref);
            }
        }

        private void setProblemTextVisible(boolean visible) {
            if (problemText == null) {
                return;
            }
            float newAlpha = visible ? 1f : 0f;
            if (problemText.getAlpha() != newAlpha) {
                problemText.animate().cancel();
                problemText.animate().alpha(newAlpha).setDuration(150).start();
            }
        }

        private void createCodeTimer() {
            if (codeTimer != null) {
                return;
            }
            codeTime = 15000;
            if (time > codeTime) {
                codeTime = time;
            }
            codeTimer = new Timer();
            lastCodeTime = System.currentTimeMillis();
            codeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(() -> {
                        double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCodeTime;
                        lastCodeTime = currentTime;
                        codeTime -= diff;
                        if (codeTime <= 1000) {
                            setProblemTextVisible(true);
                            timeText.setVisibility(GONE);
                            if (problemText != null) {
                                problemText.setVisibility(VISIBLE);
                            }
                            destroyCodeTimer();
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyCodeTimer() {
            try {
                synchronized (timerSync) {
                    if (codeTimer != null) {
                        codeTimer.cancel();
                        codeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private void createTimer() {
            if (timeTimer != null) {
                return;
            }
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            timeText.setTag(R.id.color_key_tag, Theme.key_windowBackgroundWhiteGrayText6);
            if (progressView != null) {
                progressView.resetProgressAnimation();
            }
            timeTimer = new Timer();
            timeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (timeTimer == null) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCurrentTime;
                        lastCurrentTime = currentTime;
                        time -= diff;
                        if (time >= 1000) {
                            int minutes = time / 1000 / 60;
                            int seconds = time / 1000 - minutes * 60;
                            if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                                timeText.setText(LocaleController.formatString("CallAvailableIn", R.string.CallAvailableIn, minutes, seconds));
                            } else if (currentType == AUTH_TYPE_SMS && (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD)) {
                                timeText.setText(LocaleController.formatString("ResendSmsAvailableIn", R.string.ResendSmsAvailableIn, minutes, seconds));
                            } else if (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) {
                                timeText.setText(LocaleController.formatString("SmsAvailableIn", R.string.SmsAvailableIn, minutes, seconds));
                            }
                            if (progressView != null && !progressView.isProgressAnimationRunning()) {
                                progressView.startProgressAnimation(time - 1000L);
                            }
                        } else {
                            destroyTimer();
                            if (nextType == AUTH_TYPE_FLASH_CALL || nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD || nextType == AUTH_TYPE_MISSED_CALL) {
                                if (nextType == AUTH_TYPE_CALL) {
                                    timeText.setText(getString("RequestCallButton", R.string.RequestCallButton));
                                } else if (nextType == AUTH_TYPE_MISSED_CALL || nextType == AUTH_TYPE_FLASH_CALL) {
                                    timeText.setText(getString("RequestMissedCall", R.string.RequestMissedCall));
                                } else {
                                    timeText.setText(getString("RequestSmsButton", R.string.RequestSmsButton));
                                }
                                timeText.setTextColor(Theme.getColor(Theme.key_chats_actionBackground));
                                timeText.setTag(R.id.color_key_tag, Theme.key_chats_actionBackground);
                            }
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyTimer() {
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            timeText.setTag(R.id.color_key_tag, Theme.key_windowBackgroundWhiteGrayText6);
            try {
                synchronized (timerSync) {
                    if (timeTimer != null) {
                        timeTimer.cancel();
                        timeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }


        @Override
        public void onNextPressed(String code) {
            if (currentViewNum == AUTH_TYPE_MISSED_CALL) {
                if (nextPressed) {
                    return;
                }
            } else {
                if (nextPressed || (currentViewNum < VIEW_CODE_MESSAGE || currentViewNum > VIEW_CODE_CALL) && currentViewNum != VIEW_CODE_FRAGMENT_SMS) {
                    return;
                }
            }

            if (code == null) {
                code = codeFieldContainer.getCode();
            }
            if (TextUtils.isEmpty(code)) {
                onFieldError(codeFieldContainer, false);
                return;
            }

            if (currentViewNum >= VIEW_CODE_MESSAGE && currentViewNum <= VIEW_CODE_CALL && codeFieldContainer.isFocusSuppressed) {
                return;
            }

            nextPressed = true;
            if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;

            switch (activityMode) {
                case MODE_CHANGE_PHONE_NUMBER: {
                    TLRPC.TL_account_changePhone req = new TLRPC.TL_account_changePhone();
                    req.phone_number = requestPhone;
                    req.phone_code = code;
                    req.phone_code_hash = phoneHash;
                    destroyTimer();

                    codeFieldContainer.isFocusSuppressed = true;
                    for (CodeNumberField f : codeFieldContainer.codeField) {
                        f.animateFocusedProgress(0);
                    }

                    int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        tryHideProgress(false, true);
                        nextPressed = false;
                        if (error == null) {
                            TLRPC.User user = (TLRPC.User) response;
                            destroyTimer();
                            destroyCodeTimer();
                            UserConfig.getInstance(currentAccount).setCurrentUser(user);
                            UserConfig.getInstance(currentAccount).saveConfig(true);
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(user);
                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance(currentAccount).putUser(user, false);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                            getMessagesController().removeSuggestion(0, "VALIDATE_PHONE_NUMBER");

                            if (currentType == AUTH_TYPE_FLASH_CALL) {
                                AndroidUtilities.endIncomingCall();
                            }

                            animateSuccess(()-> {
                                try {
                                    fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                } catch (Exception ignored) {}
                                new AlertDialog.Builder(getContext())
                                        .setTitle(getString(R.string.YourPasswordSuccess))
                                        .setMessage(LocaleController.formatString(R.string.ChangePhoneNumberSuccessWithPhone, PhoneFormat.getInstance().format("+" + requestPhone)))
                                        .setPositiveButton(getString(R.string.OK), null)
                                        .setOnDismissListener(dialog -> finishFragment())
                                        .show();
                            });
                        } else {
                            lastError = error.text;
                            nextPressed = false;
                            showDoneButton(false, true);
                            if (currentType == AUTH_TYPE_FLASH_CALL && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) || currentType == AUTH_TYPE_SMS && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL) || currentType == AUTH_TYPE_CALL && (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD)) {
                                createTimer();
                            }
                            if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                                NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                            } else if (currentType == AUTH_TYPE_SMS) {
                                AndroidUtilities.setWaitingForSms(true);
                                NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                                AndroidUtilities.setWaitingForCall(true);
                                NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                            }
                            waitingForEvent = true;
                            if (currentType != AUTH_TYPE_FLASH_CALL) {
                                boolean isWrongCode = false;
                                if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                    needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                    shakeWrongCode();
                                    isWrongCode = true;
                                } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                    onBackPressed(true);
                                    setPage(VIEW_PHONE_INPUT, true, null, true);
                                    needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                                } else {
                                    needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                                }

                                if (!isWrongCode) {
                                    for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                                        codeFieldContainer.codeField[a].setText("");
                                    }

                                    codeFieldContainer.isFocusSuppressed = false;
                                    codeFieldContainer.codeField[0].requestFocus();
                                }
                            }
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors);
                    tryShowProgress(reqId, true);
                    showDoneButton(true, true);
                    break;
                }
                case MODE_CANCEL_ACCOUNT_DELETION: {
                    requestPhone = cancelDeletionPhone;
                    TLRPC.TL_account_confirmPhone req = new TLRPC.TL_account_confirmPhone();
                    req.phone_code = code;
                    req.phone_code_hash = phoneHash;
                    destroyTimer();

                    codeFieldContainer.isFocusSuppressed = true;
                    for (CodeNumberField f : codeFieldContainer.codeField) {
                        f.animateFocusedProgress(0);
                    }

                    int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        tryHideProgress(false);
                        nextPressed = false;
                        if (error == null) {
                            Activity activity = getParentActivity();
                            if (activity == null) {
                                return;
                            }
                            animateSuccess(() -> new AlertDialog.Builder(activity)
                                    .setTitle(getString(R.string.CancelLinkSuccessTitle))
                                    .setMessage(LocaleController.formatString("CancelLinkSuccess", R.string.CancelLinkSuccess, PhoneFormat.getInstance().format("+" + phone)))
                                    .setPositiveButton(getString(R.string.Close), null)
                                    .setOnDismissListener(dialog -> finishFragment())
                                    .show());
                        } else {
                            lastError = error.text;
                            if (currentType == AUTH_TYPE_FLASH_CALL && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) || currentType == AUTH_TYPE_SMS &&
                                    (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL) || currentType == AUTH_TYPE_CALL && (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD)) {
                                createTimer();
                            }
                            if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                                NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                            } else if (currentType == AUTH_TYPE_SMS) {
                                AndroidUtilities.setWaitingForSms(true);
                                NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                                AndroidUtilities.setWaitingForCall(true);
                                NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                            }
                            waitingForEvent = true;
                            if (currentType != AUTH_TYPE_FLASH_CALL) {
                                AlertsCreator.processError(currentAccount, error, LoginActivity.this, req);
                            }
                            if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                shakeWrongCode();
                            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                onBackPressed(true);
                                setPage(VIEW_PHONE_INPUT, true, null, true);
                            }
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors);
                    tryShowProgress(reqId);
                    break;
                }
                default: {
                    TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
                    req.phone_number = requestPhone;
                    req.phone_code = code;
                    req.phone_code_hash = phoneHash;
                    req.flags |= 1;
                    destroyTimer();

                    codeFieldContainer.isFocusSuppressed = true;
                    for (CodeNumberField f : codeFieldContainer.codeField) {
                        f.animateFocusedProgress(0);
                    }

                    int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        tryHideProgress(false, true);

                        boolean ok = false;

                        if (error == null) {
                            nextPressed = false;
                            ok = true;
                            showDoneButton(false, true);
                            destroyTimer();
                            destroyCodeTimer();
                            if (response instanceof TLRPC.TL_auth_authorizationSignUpRequired) {
                                TLRPC.TL_auth_authorizationSignUpRequired authorization = (TLRPC.TL_auth_authorizationSignUpRequired) response;
                                if (authorization.terms_of_service != null) {
                                    currentTermsOfService = authorization.terms_of_service;
                                }
                                Bundle params = new Bundle();
                                params.putString("phoneFormated", requestPhone);
                                params.putString("phoneHash", phoneHash);
                                params.putString("code", req.phone_code);

                                animateSuccess(() -> setPage(VIEW_REGISTER, true, params, false));
                            } else {
                                animateSuccess(() -> onAuthSuccess((TLRPC.TL_auth_authorization) response));
                            }
                        } else {
                            lastError = error.text;
                            if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                                ok = true;
                                TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                    nextPressed = false;
                                    showDoneButton(false, true);
                                    if (error1 == null) {
                                        TLRPC.account_Password password = (TLRPC.account_Password) response1;
                                        if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                            AlertsCreator.showUpdateAppAlert(getParentActivity(), getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                            return;
                                        }
                                        Bundle bundle = new Bundle();
                                        SerializedData data = new SerializedData(password.getObjectSize());
                                        password.serializeToStream(data);
                                        bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                                        bundle.putString("phoneFormated", requestPhone);
                                        bundle.putString("phoneHash", phoneHash);
                                        bundle.putString("code", req.phone_code);

                                        animateSuccess(() -> setPage(VIEW_PASSWORD, true, bundle, false));
                                    } else {
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error1.text);
                                    }
                                }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                                destroyTimer();
                                destroyCodeTimer();
                            } else {
                                nextPressed = false;
                                showDoneButton(false, true);
                                if (currentType == AUTH_TYPE_FLASH_CALL && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD) || currentType == AUTH_TYPE_SMS && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL) || currentType == AUTH_TYPE_CALL && (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_PHRASE || nextType == AUTH_TYPE_WORD)) {
                                    createTimer();
                                }
                                if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                                    NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                                } else if (currentType == AUTH_TYPE_SMS) {
                                    AndroidUtilities.setWaitingForSms(true);
                                    NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                                } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                                    AndroidUtilities.setWaitingForCall(true);
                                    NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                                    AndroidUtilities.runOnUIThread(() -> {
                                        CallReceiver.checkLastReceivedCall();
                                    });
                                }
                                waitingForEvent = true;
                                if (currentType != AUTH_TYPE_FLASH_CALL) {
                                    boolean isWrongCode = false;
                                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                        shakeWrongCode();
                                        isWrongCode = true;
                                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                        onBackPressed(true);
                                        setPage(VIEW_PHONE_INPUT, true, null, true);
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                                    } else {
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                                    }

                                    if (!isWrongCode) {
                                        for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                                            codeFieldContainer.codeField[a].setText("");
                                        }

                                        codeFieldContainer.isFocusSuppressed = false;
                                        codeFieldContainer.codeField[0].requestFocus();
                                    }
                                }
                            }
                        }
                        if (ok) {
                            if (currentType == AUTH_TYPE_FLASH_CALL) {
                                AndroidUtilities.endIncomingCall();
                                AndroidUtilities.setWaitingForCall(false);
                            }
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    tryShowProgress(reqId, true);
                    showDoneButton(true, true);
                    break;
                }
            }
        }

        private void animateSuccess(Runnable callback) {
            if (currentType == AUTH_TYPE_FLASH_CALL) {
                callback.run();
                return;
            }
            for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                int finalI = i;
                codeFieldContainer.postDelayed(()-> codeFieldContainer.codeField[finalI].animateSuccessProgress(1f), i * 75L);
            }
            codeFieldContainer.postDelayed(()->{
                for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                    codeFieldContainer.codeField[i].animateSuccessProgress(0f);
                }
                callback.run();
                codeFieldContainer.isFocusSuppressed = false;
            }, codeFieldContainer.codeField.length * 75L + 400L);
        }

        private void shakeWrongCode() {
            try {
                codeFieldContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}

            for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                codeFieldContainer.codeField[a].setText("");
                codeFieldContainer.codeField[a].animateErrorProgress(1f);
            }
            if (errorViewSwitcher.getCurrentView() != wrongCode) {
                errorViewSwitcher.showNext();
            }
            codeFieldContainer.codeField[0].requestFocus();
            AndroidUtilities.shakeViewSpring(codeFieldContainer, currentType == AUTH_TYPE_MISSED_CALL ? 3.5f : 10f, () -> {
                postDelayed(()-> {
                    codeFieldContainer.isFocusSuppressed = false;
                    codeFieldContainer.codeField[0].requestFocus();

                    for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                        codeFieldContainer.codeField[a].animateErrorProgress(0f);
                    }
                }, 150);
            });
            removeCallbacks(errorColorTimeout);
            postDelayed(errorColorTimeout, 5000);
            postedErrorColorTimeout = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(errorColorTimeout);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (activityMode != MODE_LOGIN) {
                finishFragment();
                return false;
            }

            if (prevType != 0) {
                setPage(prevType, true, null, true);
                return false;
            }

            if (!force) {
                showDialog(new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString(R.string.EditNumber))
                        .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("EditNumberInfo", R.string.EditNumberInfo, phone)))
                        .setPositiveButton(getString(R.string.Close), null)
                        .setNegativeButton(getString(R.string.Edit), (dialogInterface, i) -> {
                            onBackPressed(true);
                            setPage(VIEW_PHONE_INPUT, true, null, true);
                        })
                        .create());
                return false;
            }
            nextPressed = false;
            tryHideProgress(true);
            TLRPC.TL_auth_cancelCode req = new TLRPC.TL_auth_cancelCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);

            destroyTimer();
            destroyCodeTimer();
            currentParams = null;
            if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            return true;
        }

        @Override
        public void onDestroyActivity() {
            super.onDestroyActivity();
            if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_SMS) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == AUTH_TYPE_FLASH_CALL) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            destroyTimer();
            destroyCodeTimer();
        }

        @Override
        public void onShow() {
            super.onShow();
            if (hintDrawable != null) {
                hintDrawable.setCurrentFrame(0);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (currentType != AUTH_TYPE_FLASH_CALL && codeFieldContainer.codeField != null) {
                    for (int a = codeFieldContainer.codeField.length - 1; a >= 0; a--) {
                        if (a == 0 || codeFieldContainer.codeField[a].length() != 0) {
                            codeFieldContainer.codeField[a].requestFocus();
                            codeFieldContainer.codeField[a].setSelection(codeFieldContainer.codeField[a].length());
                            showKeyboard(codeFieldContainer.codeField[a]);
                            break;
                        }
                    }
                }
                if (hintDrawable != null) {
                    hintDrawable.start();
                }
                if (currentType == AUTH_TYPE_FRAGMENT_SMS) {
                    openFragmentImageView.getAnimatedDrawable().setCurrentFrame(0, false);
                    openFragmentImageView.getAnimatedDrawable().start();
                }
            }, SHOW_DELAY);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (!waitingForEvent || codeFieldContainer.codeField == null) {
                return;
            }
            if (id == NotificationCenter.didReceiveSmsCode) {
                codeFieldContainer.setText("" + args[0]);
                onNextPressed(null);
            } else if (id == NotificationCenter.didReceiveCall) {
                String num = "" + args[0];
                if (!AndroidUtilities.checkPhonePattern(pattern, num)) {
                    return;
                }
                if (!pattern.equals("*")) {
                    catchedPhone = num;
                    AndroidUtilities.endIncomingCall();
                }
                onNextPressed(num);
                CallReceiver.clearLastCall();
            }
        }

        @Override
        public void onHide() {
            super.onHide();
            isResendingCode = false;
            nextPressed = false;
            if (prevType != 0 && currentParams != null) {
                currentParams.putInt("timeout", time);
            }
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeFieldContainer.getCode();
            if (code.length() != 0) {
                bundle.putString("smsview_code_" + currentType, code);
            }
            if (catchedPhone != null) {
                bundle.putString("catchedPhone", catchedPhone);
            }
            if (currentParams != null) {
                bundle.putBundle("smsview_params_" + currentType, currentParams);
            }
            if (time != 0) {
                bundle.putInt("time", time);
            }
            if (openTime != 0) {
                bundle.putInt("open", openTime);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("smsview_params_" + currentType);
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String catched = bundle.getString("catchedPhone");
            if (catched != null) {
                catchedPhone = catched;
            }
            String code = bundle.getString("smsview_code_" + currentType);
            if (code != null && codeFieldContainer.codeField != null) {
                codeFieldContainer.setText(code);
            }
            int t = bundle.getInt("time");
            if (t != 0) {
                time = t;
            }
            int t2 = bundle.getInt("open");
            if (t2 != 0) {
                openTime = t2;
            }
        }
    }


    public class LoadingTextView extends TextView {

        private final Drawable rippleDrawable = Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), .10f), Theme.RIPPLE_MASK_ROUNDRECT_6DP);
        public final LoadingDrawable loadingDrawable = new LoadingDrawable();

        public LoadingTextView(Context context) {
            super(context);
            rippleDrawable.setCallback(this);
            loadingDrawable.setAppearByGradient(true);
            loadingDrawable.setSpeed(.8f);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text, type);

            updateLoadingLayout();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            updateLoadingLayout();
        }

        private void updateLoadingLayout() {
            Layout layout = getLayout();
            if (layout == null) {
                return;
            }
            CharSequence text = layout.getText();
            if (text == null) {
                return;
            }
            LinkPath path = new LinkPath(true);
            path.setInset(AndroidUtilities.dp(3), AndroidUtilities.dp(6));
            int start = 0;
            int end = text.length();
            path.setCurrentLayout(layout, start, 0);
            layout.getSelectionPath(start, end, path);
            path.getBounds(AndroidUtilities.rectTmp);
            rippleDrawable.setBounds((int) AndroidUtilities.rectTmp.left, (int) AndroidUtilities.rectTmp.top, (int) AndroidUtilities.rectTmp.right, (int) AndroidUtilities.rectTmp.bottom);
            loadingDrawable.usePath(path);
            loadingDrawable.setRadiiDp(4);

            int color = getThemedColor(Theme.key_chat_linkSelectBackground);
            loadingDrawable.setColors(
                    Theme.multAlpha(color, 0.85f),
                    Theme.multAlpha(color, 2f),
                    Theme.multAlpha(color, 3.5f),
                    Theme.multAlpha(color, 6f)
            );

            loadingDrawable.updateBounds();
        }

        protected boolean isResendingCode() {
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            float offset = (getGravity() & Gravity.CENTER_VERTICAL) != 0 && getLayout() != null ? getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - getLayout().getHeight()) / 2f : getPaddingTop();
            canvas.translate(getPaddingLeft(), offset);
            rippleDrawable.draw(canvas);
            canvas.restore();

            super.onDraw(canvas);

            if (isResendingCode() || loadingDrawable.isDisappearing()) {
                canvas.save();
                canvas.translate(getPaddingLeft(), offset);
                loadingDrawable.draw(canvas);
                canvas.restore();
                invalidate();
            }
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == rippleDrawable || super.verifyDrawable(who);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isRippleEnabled() && event.getAction() == MotionEvent.ACTION_DOWN) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rippleDrawable.setHotspot(event.getX(), event.getY());
                }
                rippleDrawable.setState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed});
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_UP) {
                rippleDrawable.setState(new int[]{});
            }
            return super.onTouchEvent(event);
        }

        protected boolean isRippleEnabled() {
            return true;
        }
    }

    public class LoginActivityPasswordView extends SlideView {

        private EditTextBoldCursor codeField;
        private TextView confirmTextView;
        private TextView cancelButton;
        private TextView titleView;
        private RLottieImageView lockImageView;

        private Bundle currentParams;
        private boolean nextPressed;
        private TLRPC.account_Password currentPassword;
        private String passwordString;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;

        private OutlineTextContainerView outlineCodeField;

        public LoginActivityPasswordView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            FrameLayout lockFrameLayout = new FrameLayout(context);
            lockImageView = new RLottieImageView(context);
            lockImageView.setAnimation(R.raw.tsv_setup_intro, 120, 120);
            lockImageView.setAutoRepeat(false);
            lockFrameLayout.addView(lockImageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL));
            lockFrameLayout.setVisibility(AndroidUtilities.isSmallScreen() || (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y && !AndroidUtilities.isTablet()) ? GONE : VISIBLE);
            addView(lockFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(getString(R.string.YourPasswordHeader));
            titleView.setGravity(Gravity.CENTER);
            titleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

            confirmTextView = new TextView(context);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setText(getString(R.string.LoginPasswordTextShort));
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 8, 12, 0));

            outlineCodeField = new OutlineTextContainerView(context);
            outlineCodeField.setText(getString(R.string.EnterPassword));
            codeField = new EditTextBoldCursor(context);
            codeField.setCursorSize(AndroidUtilities.dp(20));
            codeField.setCursorWidth(1.5f);
            codeField.setBackground(null);
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            int padding = AndroidUtilities.dp(16);
            codeField.setPadding(padding, padding, padding, padding);
            codeField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            codeField.setTypeface(Typeface.DEFAULT);
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            codeField.setOnFocusChangeListener((v, hasFocus) -> outlineCodeField.animateSelection(hasFocus ? 1f : 0f));
            outlineCodeField.attachEditText(codeField);
            outlineCodeField.addView(codeField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });
            addView(outlineCodeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 32, 16, 0));

            cancelButton = new TextView(context);
            cancelButton.setGravity(Gravity.CENTER | Gravity.LEFT);
            cancelButton.setText(getString(R.string.ForgotPassword));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            FrameLayout bottomContainer = new FrameLayout(context);
            bottomContainer.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, (Build.VERSION.SDK_INT >= 21 ? 56 : 60), Gravity.BOTTOM, 0, 0, 0, 32));
            addView(bottomContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
            VerticalPositionAutoAnimator.attach(cancelButton);

            if (activityMode == MODE_BALANCE_PASSWORD) {
                cancelButton.setVisibility(View.GONE);
                currentPassword = LoginActivity.this.currentPassword;
                if (currentPassword != null && !TextUtils.isEmpty(currentPassword.hint)) {
                    codeField.setHint(currentPassword.hint);
                } else {
                    codeField.setHint(null);
                }
            } else {
                cancelButton.setOnClickListener(view -> {
                    if (radialProgressView.getTag() != null) {
                        return;
                    }
                    if (currentPassword.has_recovery) {
                        needShowProgress(0);
                        TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            needHideProgress(false);
                            if (error == null) {
                                final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                                String rawPattern = res.email_pattern;
                                SpannableStringBuilder emailPattern = SpannableStringBuilder.valueOf(rawPattern);
                                int startIndex = rawPattern.indexOf('*'), endIndex = rawPattern.lastIndexOf('*');
                                if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
                                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                                    run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                                    run.start = startIndex;
                                    run.end = endIndex + 1;
                                    emailPattern.setSpan(new TextStyleSpan(run), startIndex, endIndex + 1, 0);
                                }
                                builder.setMessage(AndroidUtilities.formatSpannable(getString(R.string.RestoreEmailSent), emailPattern));
                                builder.setTitle(getString("RestoreEmailSentTitle", R.string.RestoreEmailSentTitle));
                                builder.setPositiveButton(getString(R.string.Continue), (dialogInterface, i) -> {
                                    Bundle bundle = new Bundle();
                                    bundle.putString("email_unconfirmed_pattern", res.email_pattern);
                                    bundle.putString("password", passwordString);
                                    bundle.putString("requestPhone", requestPhone);
                                    bundle.putString("phoneHash", phoneHash);
                                    bundle.putString("phoneCode", phoneCode);
                                    setPage(VIEW_RECOVER, true, bundle, false);
                                });
                                Dialog dialog = showDialog(builder.create());
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                            } else {
                                if (error.text.startsWith("FLOOD_WAIT")) {
                                    int time = Utilities.parseInt(error.text);
                                    String timeString;
                                    if (time < 60) {
                                        timeString = LocaleController.formatPluralString("Seconds", time);
                                    } else {
                                        timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                    }
                                    needShowAlert(getString(R.string.WrongCodeTitle), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                } else {
                                    needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error.text);
                                }
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        AndroidUtilities.hideKeyboard(codeField);
                        new AlertDialog.Builder(context)
                                .setTitle(getString(R.string.RestorePasswordNoEmailTitle))
                                .setMessage(getString(R.string.RestorePasswordNoEmailText))
                                .setPositiveButton(getString(R.string.Close), null)
                                .setNegativeButton(getString(R.string.ResetAccount), (dialog, which) -> tryResetAccount(requestPhone, phoneHash, phoneCode))
                                .show();
                    }
                });
            }
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            outlineCodeField.updateColor();
        }

        @Override
        public String getHeaderName() {
            return getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            if (params.isEmpty()) {
                AndroidUtilities.hideKeyboard(codeField);
                return;
            }
            codeField.setText("");
            currentParams = params;
            passwordString = currentParams.getString("password");
            if (passwordString != null) {
                SerializedData data = new SerializedData(Utilities.hexToBytes(passwordString));
                currentPassword = TLRPC.account_Password.TLdeserialize(data, data.readInt32(false), false);
            }

            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phoneCode = params.getString("code");

            if (currentPassword != null && !TextUtils.isEmpty(currentPassword.hint)) {
                codeField.setHint(currentPassword.hint);
            } else {
                codeField.setHint(null);
            }
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            if (clear) {
                codeField.setText("");
            }
            onFieldError(outlineCodeField, true);
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            if (currentPassword == null) {
                return;
            }

            String oldPassword = codeField.getText().toString();
            if (oldPassword.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);

            Utilities.globalQueue.postRunnable(() -> {
                final byte[] x_bytes;

                TLRPC.PasswordKdfAlgo current_algo = currentPassword.current_algo;
                if (current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    byte[] passwordBytes = AndroidUtilities.getStringBytes(oldPassword);
                    TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) current_algo;
                    x_bytes = SRPHelper.getX(passwordBytes, algo);
                } else {
                    x_bytes = null;
                }


                RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    nextPressed = false;
                    if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.account_Password) response2;
                                onNextPressed(null);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }

                    if (response instanceof TL_stats.TL_broadcastRevenueWithdrawalUrl) {
                        passwordFinishCallback.run((TL_stats.TL_broadcastRevenueWithdrawalUrl) response, null);
                        finishFragment();
                    } else if (response instanceof TLRPC.TL_auth_authorization) {
                        showDoneButton(false, true);
                        postDelayed(() -> {
                            needHideProgress(false, false);
                            AndroidUtilities.hideKeyboard(codeField);
                            onAuthSuccess((TLRPC.TL_auth_authorization) response);
                        }, 150);
                    } else {
                        needHideProgress(false);
                        if (error.text.equals("PASSWORD_HASH_INVALID")) {
                            onPasscodeError(true);
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error.text);
                        }
                    }
                });

                if (current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    TLRPC.TL_inputCheckPasswordSRP password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) current_algo);
                    if (password == null) {
                        TLRPC.TL_error error = new TLRPC.TL_error();
                        error.text = "PASSWORD_HASH_INVALID";
                        requestDelegate.run(null, error);
                        return;
                    }
                    if (activityMode == MODE_BALANCE_PASSWORD) {
                        final TL_stats.TL_getBroadcastRevenueWithdrawalUrl req = new TL_stats.TL_getBroadcastRevenueWithdrawalUrl();
                        req.channel = channel;
                        req.password = password;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        final TLRPC.TL_auth_checkPassword req = new TLRPC.TL_auth_checkPassword();
                        req.password = password;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    }
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public boolean onBackPressed(boolean force) {
            nextPressed = false;
            needHideProgress(true);
            currentParams = null;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                    showKeyboard(codeField);
                    lockImageView.getAnimatedDrawable().setCurrentFrame(0, false);
                    lockImageView.playAnimation();
                }
            }, SHOW_DELAY);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("passview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("passview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("passview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String code = bundle.getString("passview_code");
            if (code != null) {
                codeField.setText(code);
            }
        }
    }

    public class LoginActivityResetWaitView extends SlideView {

        private RLottieImageView waitImageView;
        private TextView titleView;
        private TextView confirmTextView;
        private TextView resetAccountButton;
        private TextView resetAccountTime;
        private TextView resetAccountText;
        private Runnable timeRunnable;

        private Bundle currentParams;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;
        private int startTime;
        private int waitTime;

        private Boolean wasResetButtonActive;

        public LoginActivityResetWaitView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            LinearLayout innerLinearLayout = new LinearLayout(context);
            innerLinearLayout.setOrientation(VERTICAL);
            innerLinearLayout.setGravity(Gravity.CENTER);

            FrameLayout waitFrameLayout = new FrameLayout(context);
            waitImageView = new RLottieImageView(context);
            waitImageView.setAutoRepeat(true);
            waitImageView.setAnimation(R.raw.sandclock, 120, 120);
            waitFrameLayout.addView(waitImageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL));
            waitFrameLayout.setVisibility(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y && !AndroidUtilities.isTablet() ? GONE : VISIBLE);
            innerLinearLayout.addView(waitFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(getString(R.string.ResetAccount));
            titleView.setGravity(Gravity.CENTER);
            titleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            innerLinearLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

            confirmTextView = new TextView(context);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            innerLinearLayout.addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 8, 12, 0));

            addView(innerLinearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

            resetAccountText = new TextView(context);
            resetAccountText.setGravity(Gravity.CENTER_HORIZONTAL);
            resetAccountText.setText(getString("ResetAccountStatus", R.string.ResetAccountStatus));
            resetAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 24, 0, 0));

            resetAccountTime = new TextView(context);
            resetAccountTime.setGravity(Gravity.CENTER_HORIZONTAL);
            resetAccountTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            resetAccountTime.setTypeface(AndroidUtilities.bold());
            resetAccountTime.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountTime, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

            resetAccountButton = new TextView(context);
            resetAccountButton.setGravity(Gravity.CENTER);
            resetAccountButton.setText(getString(R.string.ResetAccount));
            resetAccountButton.setTypeface(AndroidUtilities.bold());
            resetAccountButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            resetAccountButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            resetAccountButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            resetAccountButton.setTextColor(Color.WHITE);
            addView(resetAccountButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.CENTER_HORIZONTAL, 16, 32, 16, 48));
            resetAccountButton.setOnClickListener(view -> {
                if (radialProgressView.getTag() != null) {
                    return;
                }
                showDialog(new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString("ResetMyAccountWarning", R.string.ResetMyAccountWarning))
                        .setMessage(getString("ResetMyAccountWarningText", R.string.ResetMyAccountWarningText))
                        .setPositiveButton(getString("ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset), (dialogInterface, i) -> {
                            needShowProgress(0);
                            TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                            req.reason = "Forgot password";
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                needHideProgress(false);
                                if (error == null) {
                                    if (requestPhone == null || phoneHash == null || phoneCode == null) {
                                        setPage(VIEW_PHONE_INPUT, true, null, true);
                                        return;
                                    }

                                    Bundle params = new Bundle();
                                    params.putString("phoneFormated", requestPhone);
                                    params.putString("phoneHash", phoneHash);
                                    params.putString("code", phoneCode);
                                    setPage(VIEW_REGISTER, true, params, false);
                                } else {
                                    if (error.text.equals("2FA_RECENT_CONFIRM")) {
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("ResetAccountCancelledAlert", R.string.ResetAccountCancelledAlert));
                                    } else {
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error.text);
                                    }
                                }
                            }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
                        })
                        .setNegativeButton(getString("Cancel", R.string.Cancel), null).create());
            });
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            resetAccountText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            resetAccountTime.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            resetAccountButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_changephoneinfo_image2), Theme.getColor(Theme.key_chats_actionPressedBackground)));
        }

        @Override
        public String getHeaderName() {
            return getString("ResetAccount", R.string.ResetAccount);
        }

        private void updateTimeText() {
            int timeLeft = Math.max(0, waitTime - (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - startTime));
            int days = timeLeft / 86400;
            int daysRounded = (int) Math.round(timeLeft / (float) 86400);
            int hours = timeLeft / 3600;
            int minutes = (timeLeft / 60) % 60;
            int seconds = timeLeft % 60;
            if (days >= 2) {
                resetAccountTime.setText(LocaleController.formatPluralString("Days", daysRounded));
            } else {
                resetAccountTime.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
            }

            boolean isResetButtonActive = timeLeft == 0;
            if (wasResetButtonActive == null || wasResetButtonActive != isResetButtonActive) {
                if (!isResetButtonActive) {
                    waitImageView.setAutoRepeat(true);
                    if (!waitImageView.isPlaying()) {
                        waitImageView.playAnimation();
                    }
                } else {
                    waitImageView.getAnimatedDrawable().setAutoRepeat(0);
                }

                resetAccountTime.setVisibility(isResetButtonActive ? INVISIBLE : VISIBLE);
                resetAccountText.setVisibility(isResetButtonActive ? INVISIBLE : VISIBLE);
                resetAccountButton.setVisibility(isResetButtonActive ? VISIBLE : INVISIBLE);

                wasResetButtonActive = isResetButtonActive;
            }
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            currentParams = params;
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phoneCode = params.getString("code");
            startTime = params.getInt("startTime");
            waitTime = params.getInt("waitTime");
            confirmTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("ResetAccountInfo", R.string.ResetAccountInfo, LocaleController.addNbsp(PhoneFormat.getInstance().format("+" + requestPhone)))));
            updateTimeText();
            timeRunnable = new Runnable() {
                @Override
                public void run() {
                    if (timeRunnable != this) {
                        return;
                    }
                    updateTimeText();
                    AndroidUtilities.runOnUIThread(timeRunnable, 1000);
                }
            };
            AndroidUtilities.runOnUIThread(timeRunnable, 1000);
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            AndroidUtilities.cancelRunOnUIThread(timeRunnable);
            timeRunnable = null;
            currentParams = null;
            return true;
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            if (currentParams != null) {
                bundle.putBundle("resetview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("resetview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
        }
    }

    public class LoginActivitySetupEmail extends SlideView {
        private OutlineTextContainerView emailOutlineView;
        private EditTextBoldCursor emailField;

        private TextView titleView;
        private TextView subtitleView;
        private TextView signInWithGoogleView;
        private LoginOrView loginOrView;
        private RLottieImageView inboxImageView;

        private Bundle currentParams;
        private boolean nextPressed;

        private String phone, emailPhone;
        private String requestPhone, phoneHash;

        private GoogleSignInAccount googleAccount;

        public LoginActivitySetupEmail(Context context) {
            super(context);

            setOrientation(VERTICAL);

            FrameLayout inboxFrameLayout = new FrameLayout(context);
            inboxImageView = new RLottieImageView(context);
            inboxImageView.setAnimation(R.raw.tsv_setup_mail, 120, 120);
            inboxImageView.setAutoRepeat(false);
            inboxFrameLayout.addView(inboxImageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL));
            inboxFrameLayout.setVisibility(AndroidUtilities.isSmallScreen() || (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y && !AndroidUtilities.isTablet()) ? GONE : VISIBLE);
            addView(inboxFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(getString(activityMode == MODE_CHANGE_LOGIN_EMAIL ? R.string.EnterNewEmail : R.string.AddEmailTitle));
            titleView.setGravity(Gravity.CENTER);
            titleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            subtitleView.setText(getString(R.string.AddEmailSubtitle));
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 8, 32, 0));

            emailOutlineView = new OutlineTextContainerView(context);
            emailOutlineView.setText(getString(activityMode == MODE_CHANGE_LOGIN_EMAIL ? R.string.YourNewEmail : R.string.YourEmail));

            emailField = new EditTextBoldCursor(context);
            emailField.setCursorSize(AndroidUtilities.dp(20));
            emailField.setCursorWidth(1.5f);
            emailField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            emailField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            emailField.setMaxLines(1);
            emailField.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            emailField.setOnFocusChangeListener((v, hasFocus) -> emailOutlineView.animateSelection(hasFocus ? 1f : 0f));
            emailField.setBackground(null);
            emailField.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

            emailOutlineView.attachEditText(emailField);
            emailOutlineView.addView(emailField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            emailField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            addView(emailOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 16, 24, 16, 0));

            signInWithGoogleView = new TextView(context);
            signInWithGoogleView.setGravity(Gravity.LEFT);
            signInWithGoogleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            signInWithGoogleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            signInWithGoogleView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            signInWithGoogleView.setMaxLines(2);

            SpannableStringBuilder str = new SpannableStringBuilder("d ");
            Drawable dr = ContextCompat.getDrawable(context, R.drawable.googleg_standard_color_18);
            dr.setBounds(0, AndroidUtilities.dp(9), AndroidUtilities.dp(18), AndroidUtilities.dp(18 + 9));
            str.setSpan(new ImageSpan(dr, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            str.setSpan(new ReplacementSpan() {
                @Override
                public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                    return AndroidUtilities.dp(12);
                }

                @Override
                public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
            }, 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            str.append(getString(R.string.SignInWithGoogle));
            signInWithGoogleView.setText(str);

            loginOrView = new LoginOrView(context);

            Space space = new Space(context);
            addView(space, LayoutHelper.createLinear(0, 0, 1f));

            FrameLayout bottomContainer = new FrameLayout(context);
            bottomContainer.addView(signInWithGoogleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 24));
            bottomContainer.addView(loginOrView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 16, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 70));
            loginOrView.setMeasureAfter(signInWithGoogleView);
            addView(bottomContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            VerticalPositionAutoAnimator.attach(bottomContainer);

            bottomContainer.setOnClickListener(view -> {
                NotificationCenter.getGlobalInstance().addObserver(new NotificationCenter.NotificationCenterDelegate() {
                    @Override
                    public void didReceivedNotification(int id, int account, Object... args) {
                        int request = (int) args[0];
                        int result = (int) args[1];
                        Intent data = (Intent) args[2];
                        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onActivityResultReceived);

                        if (request == BasePermissionsActivity.REQUEST_CODE_SIGN_IN_WITH_GOOGLE) {
                            try {
                                googleAccount = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
                                onNextPressed(null);
                            } catch (ApiException e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }, NotificationCenter.onActivityResultReceived);

                GoogleSignInClient googleClient = GoogleSignIn.getClient(getContext(), new GoogleSignInOptions.Builder()
                        .requestIdToken(BuildVars.GOOGLE_AUTH_CLIENT_ID)
                        .requestEmail()
                        .build());
                googleClient.signOut().addOnCompleteListener(command -> {
                    if (getParentActivity() == null || getParentActivity().isFinishing()) {
                        return;
                    }
                    getParentActivity().startActivityForResult(googleClient.getSignInIntent(), BasePermissionsActivity.REQUEST_CODE_SIGN_IN_WITH_GOOGLE);
                });
            });
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            emailField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            signInWithGoogleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            loginOrView.updateColors();

            emailOutlineView.invalidate();
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public String getHeaderName() {
            return getString("AddEmailTitle", R.string.AddEmailTitle);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            emailField.setText("");
            currentParams = params;
            phone = currentParams.getString("phone");
            emailPhone = currentParams.getString("ephone");
            requestPhone = currentParams.getString("phoneFormated");
            phoneHash = currentParams.getString("phoneHash");

            int v = params.getBoolean("googleSignInAllowed") && PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices() ? VISIBLE : GONE;
            loginOrView.setVisibility(v);
            signInWithGoogleView.setVisibility(v);

            showKeyboard(emailField);
            emailField.requestFocus();
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            try {
                emailOutlineView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            if (clear) {
                emailField.setText("");
            }
            emailField.requestFocus();

            onFieldError(emailOutlineView, true);
            postDelayed(()-> emailField.requestFocus(), 300);
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            String email = googleAccount != null ? googleAccount.getEmail() : emailField.getText().toString();
            Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);
            params.putString("phoneHash", phoneHash);
            params.putString("email", email);
            params.putBoolean("setup", true);

            if (googleAccount != null) {
                TLRPC.TL_account_verifyEmail verifyEmail = new TLRPC.TL_account_verifyEmail();
                if (activityMode == MODE_CHANGE_LOGIN_EMAIL) {
                    verifyEmail.purpose = new TLRPC.TL_emailVerifyPurposeLoginChange();
                } else {
                    TLRPC.TL_emailVerifyPurposeLoginSetup purpose = new TLRPC.TL_emailVerifyPurposeLoginSetup();
                    purpose.phone_number = requestPhone;
                    purpose.phone_code_hash = phoneHash;
                    verifyEmail.purpose = purpose;
                }
                TLRPC.TL_emailVerificationGoogle verificationGoogle = new TLRPC.TL_emailVerificationGoogle();
                verificationGoogle.token = googleAccount.getIdToken();
                verifyEmail.verification = verificationGoogle;

                googleAccount = null;
                ConnectionsManager.getInstance(currentAccount).sendRequest(verifyEmail, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TLRPC.TL_account_emailVerified && activityMode == MODE_CHANGE_LOGIN_EMAIL) {
                        finishFragment();
                        emailChangeFinishCallback.run();
                    } else if (response instanceof TLRPC.TL_account_emailVerifiedLogin) {
                        TLRPC.TL_account_emailVerifiedLogin emailVerifiedLogin = (TLRPC.TL_account_emailVerifiedLogin) response;

                        params.putString("email", emailVerifiedLogin.email);
                        fillNextCodeParams(params, emailVerifiedLogin.sent_code);
                    } else if (error != null) {
                        if (error.text.contains("EMAIL_NOT_ALLOWED")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.EmailNotAllowed));
                        } else if (error.text.contains("EMAIL_TOKEN_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.EmailTokenInvalid));
                        } else if (error.code != -1000) {
                            AlertsCreator.processError(currentAccount, error, LoginActivity.this, verifyEmail);
                        }
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);

                return;
            }

            if (TextUtils.isEmpty(email)) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);
            TLRPC.TL_account_sendVerifyEmailCode req = new TLRPC.TL_account_sendVerifyEmailCode();
            if (activityMode == MODE_CHANGE_LOGIN_EMAIL) {
                req.purpose = new TLRPC.TL_emailVerifyPurposeLoginChange();
            } else {
                TLRPC.TL_emailVerifyPurposeLoginSetup purpose = new TLRPC.TL_emailVerifyPurposeLoginSetup();
                purpose.phone_number = requestPhone;
                purpose.phone_code_hash = phoneHash;
                req.purpose = purpose;
            }
            req.email = email;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress(false);
                nextPressed = false;

                if (response instanceof TLRPC.TL_account_sentEmailCode) {
                    TLRPC.TL_account_sentEmailCode emailCode = (TLRPC.TL_account_sentEmailCode) response;
                    fillNextCodeParams(params, emailCode);
                } else if (error.text != null) {
                    if (error.text.contains("EMAIL_INVALID")) {
                        onPasscodeError(false);
                    } else if (error.text.contains("EMAIL_NOT_ALLOWED")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.EmailNotAllowed));
                    } else if (error.text.contains("PHONE_PASSWORD_FLOOD")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                    } else if (error.text.contains("PHONE_NUMBER_FLOOD")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("PhoneNumberFlood", R.string.PhoneNumberFlood));
                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidCode", R.string.InvalidCode));
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        onBackPressed(true);
                        setPage(VIEW_PHONE_INPUT, true, null, true);
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                    } else if (error.code != -1000) {
                        AlertsCreator.processError(currentAccount, error, LoginActivity.this, req, requestPhone);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                inboxImageView.getAnimatedDrawable().setCurrentFrame(0, false);
                inboxImageView.playAnimation();
                emailField.requestFocus();
                AndroidUtilities.showKeyboard(emailField);
            }, SHOW_DELAY);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String email = emailField.getText().toString();
            if (email != null && email.length() != 0) {
                bundle.putString("emailsetup_email", email);
            }
            if (currentParams != null) {
                bundle.putBundle("emailsetup_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("emailsetup_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String email = bundle.getString("emailsetup_email");
            if (email != null) {
                emailField.setText(email);
            }
        }
    }

    public class LoginActivityEmailCodeView extends SlideView {

        private CodeFieldContainer codeFieldContainer;
        private TextView titleView;
        private TextView confirmTextView;
        private TextView signInWithGoogleView;
        private FrameLayout resendFrameLayout;
        private TextView resendCodeView;
        private FrameLayout cantAccessEmailFrameLayout;
        private TextView cantAccessEmailView;
        private TextView emailResetInView;
        private TextView wrongCodeView;
        private LoginOrView loginOrView;
        private RLottieImageView inboxImageView;

        private boolean resetRequestPending;
        private Bundle currentParams;
        private boolean nextPressed;
        private GoogleSignInAccount googleAccount;

        private int resetAvailablePeriod, resetPendingDate;
        private String phone, emailPhone, email;
        private String requestPhone, phoneHash;
        private boolean isFromSetup;
        private int length;
        private boolean isSetup;

        private ViewSwitcher errorViewSwitcher;

        private boolean postedErrorColorTimeout;
        private Runnable errorColorTimeout = () -> {
            postedErrorColorTimeout = false;
            for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                codeFieldContainer.codeField[i].animateErrorProgress(0);
            }

            if (errorViewSwitcher.getCurrentView() != resendFrameLayout) {
                errorViewSwitcher.showNext();
                AndroidUtilities.updateViewVisibilityAnimated(cantAccessEmailFrameLayout, resendCodeView.getVisibility() != VISIBLE && activityMode != MODE_CHANGE_LOGIN_EMAIL && !isSetup, 1f, true);
            }
        };
        private Runnable resendCodeTimeout = () -> showResendCodeView(true);
        private Runnable updateResetPendingDateCallback = this::updateResetPendingDate;

        public LoginActivityEmailCodeView(Context context, boolean setup) {
            super(context);
            isSetup = setup;

            setOrientation(VERTICAL);

            FrameLayout inboxFrameLayout = new FrameLayout(context);
            inboxImageView = new RLottieImageView(context);
            if (!setup || activityMode == MODE_CHANGE_LOGIN_EMAIL) {
                inboxImageView.setAnimation(R.raw.email_check_inbox, 120, 120);
            } else {
                inboxImageView.setAnimation(R.raw.email_setup_heart, 120, 120);
            }
            inboxImageView.setAutoRepeat(false);
            inboxFrameLayout.addView(inboxImageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL));
            inboxFrameLayout.setVisibility(AndroidUtilities.isSmallScreen() || (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y && !AndroidUtilities.isTablet()) ? GONE : VISIBLE);
            addView(inboxFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(getString(activityMode == MODE_CHANGE_LOGIN_EMAIL ? R.string.CheckYourNewEmail : setup ? R.string.VerificationCode : R.string.CheckYourEmail));
            titleView.setGravity(Gravity.CENTER);
            titleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

            confirmTextView = new SpoilersTextView(context, false);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(Gravity.CENTER);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, 8, 24, 0));

            codeFieldContainer = new CodeFieldContainer(context) {
                @Override
                protected void processNextPressed() {
                    onNextPressed(null);
                }
            };

            addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL, 0, setup ? 48 : 32, 0, 0));

            signInWithGoogleView = new TextView(context);
            signInWithGoogleView.setGravity(Gravity.CENTER);
            signInWithGoogleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            signInWithGoogleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            signInWithGoogleView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            signInWithGoogleView.setMaxLines(2);

            SpannableStringBuilder str = new SpannableStringBuilder("d ");
            Drawable dr = ContextCompat.getDrawable(context, R.drawable.googleg_standard_color_18);
            dr.setBounds(0, AndroidUtilities.dp(9), AndroidUtilities.dp(18), AndroidUtilities.dp(18 + 9));
            str.setSpan(new ImageSpan(dr, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            str.setSpan(new ReplacementSpan() {
                @Override
                public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                    return AndroidUtilities.dp(12);
                }

                @Override
                public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
            }, 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            str.append(getString(R.string.SignInWithGoogle));
            signInWithGoogleView.setText(str);

            signInWithGoogleView.setOnClickListener(view -> {
                NotificationCenter.getGlobalInstance().addObserver(new NotificationCenter.NotificationCenterDelegate() {
                    @Override
                    public void didReceivedNotification(int id, int account, Object... args) {
                        int request = (int) args[0];
                        int result = (int) args[1];
                        Intent data = (Intent) args[2];
                        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onActivityResultReceived);

                        if (request == BasePermissionsActivity.REQUEST_CODE_SIGN_IN_WITH_GOOGLE) {
                            try {
                                googleAccount = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
                                onNextPressed(null);
                            } catch (ApiException e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }, NotificationCenter.onActivityResultReceived);

                GoogleSignInClient googleClient = GoogleSignIn.getClient(getContext(), new GoogleSignInOptions.Builder()
                                .requestIdToken(BuildVars.GOOGLE_AUTH_CLIENT_ID)
                                .requestEmail()
                                .build());
                googleClient.signOut().addOnCompleteListener(command -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    getParentActivity().startActivityForResult(googleClient.getSignInIntent(), BasePermissionsActivity.REQUEST_CODE_SIGN_IN_WITH_GOOGLE);
                });
            });

            cantAccessEmailFrameLayout = new FrameLayout(context);
            AndroidUtilities.updateViewVisibilityAnimated(cantAccessEmailFrameLayout, activityMode != MODE_CHANGE_LOGIN_EMAIL && !isSetup, 1f, false);

            cantAccessEmailView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            cantAccessEmailView.setText(getString(R.string.LoginCantAccessThisEmail));
            cantAccessEmailView.setGravity(Gravity.CENTER);
            cantAccessEmailView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cantAccessEmailView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            cantAccessEmailView.setMaxLines(2);
            cantAccessEmailView.setOnClickListener(v -> {
                String rawPattern = currentParams.getString("emailPattern");
                SpannableStringBuilder email = new SpannableStringBuilder(rawPattern);
                int startIndex = rawPattern.indexOf('*'), endIndex = rawPattern.lastIndexOf('*');
                if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                    run.start = startIndex;
                    run.end = endIndex + 1;
                    email.setSpan(new TextStyleSpan(run), startIndex, endIndex + 1, 0);
                }

                new AlertDialog.Builder(context)
                        .setTitle(getString(R.string.LoginEmailResetTitle))
                        .setMessage(AndroidUtilities.formatSpannable(AndroidUtilities.replaceTags(getString(R.string.LoginEmailResetMessage)), email, getTimePattern(resetAvailablePeriod)))
                        .setPositiveButton(getString(R.string.LoginEmailResetButton), (dialog, which) -> {
                            Bundle params = new Bundle();
                            params.putString("phone", phone);
                            params.putString("ephone", emailPhone);
                            params.putString("phoneFormated", requestPhone);

                            TLRPC.TL_auth_resetLoginEmail req = new TLRPC.TL_auth_resetLoginEmail();
                            req.phone_number = requestPhone;
                            req.phone_code_hash = phoneHash;
                            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                if (response instanceof TLRPC.TL_auth_sentCode) {
                                    TLRPC.TL_auth_sentCode sentCode = (TLRPC.TL_auth_sentCode) response;
                                    if (sentCode.type instanceof TLRPC.TL_auth_sentCodeTypeEmailCode) {
                                        sentCode.type.email_pattern = currentParams.getString("emailPattern");
                                        resetRequestPending = true;
                                    }
                                    fillNextCodeParams(params, sentCode);
                                } else if (error != null && error.text != null) {
                                    if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                        onBackPressed(true);
                                        setPage(VIEW_PHONE_INPUT, true, null, true);
                                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                                    } else {
                                        AlertsCreator.processError(currentAccount, error, LoginActivity.this, req);
                                    }
                                }
                            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .show();
            });
            cantAccessEmailFrameLayout.addView(cantAccessEmailView);

            emailResetInView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(MeasureSpec.getSize(heightMeasureSpec), AndroidUtilities.dp(100)), MeasureSpec.AT_MOST));
                }
            };
            emailResetInView.setGravity(Gravity.CENTER);
            emailResetInView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            emailResetInView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            emailResetInView.setMaxLines(3);
            emailResetInView.setOnClickListener(v -> requestEmailReset());
            emailResetInView.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
            emailResetInView.setVisibility(GONE);
            cantAccessEmailFrameLayout.addView(emailResetInView);

            resendCodeView = new TextView(context);
            resendCodeView.setGravity(Gravity.CENTER);
            resendCodeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resendCodeView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            resendCodeView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            resendCodeView.setMaxLines(2);
            resendCodeView.setText(getString(R.string.ResendCode));
            resendCodeView.setOnClickListener(v -> {
                if (resendCodeView.getVisibility() != View.VISIBLE || resendCodeView.getAlpha() != 1f) {
                    return;
                }

                showResendCodeView(false);

                TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
                req.phone_number = requestPhone;
                req.phone_code_hash = phoneHash;

                Bundle params = new Bundle();
                params.putString("phone", phone);
                params.putString("ephone", emailPhone);
                params.putString("phoneFormated", requestPhone);

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_auth_sentCode) {
                            TLRPC.TL_auth_sentCode sentCode = (TLRPC.TL_auth_sentCode) response;
                            fillNextCodeParams(params, sentCode);
                        } else if (error != null && error.text != null) {
                            AlertsCreator.processError(currentAccount, error, LoginActivity.this, req);
                        }
                    });
                }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            });
            AndroidUtilities.updateViewVisibilityAnimated(resendCodeView, false, 1f, false);

            loginOrView = new LoginOrView(context);
            VerticalPositionAutoAnimator.attach(loginOrView);

            errorViewSwitcher = new ViewSwitcher(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            Animation anim = AnimationUtils.loadAnimation(context, R.anim.text_in);
            anim.setInterpolator(Easings.easeInOutQuad);
            errorViewSwitcher.setInAnimation(anim);

            anim = AnimationUtils.loadAnimation(context, R.anim.text_out);
            anim.setInterpolator(Easings.easeInOutQuad);
            errorViewSwitcher.setOutAnimation(anim);

            resendFrameLayout = new FrameLayout(context);
            resendFrameLayout.addView(resendCodeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            errorViewSwitcher.addView(resendFrameLayout);

            wrongCodeView = new TextView(context);
            wrongCodeView.setText(getString("WrongCode", R.string.WrongCode));
            wrongCodeView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongCodeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            wrongCodeView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            wrongCodeView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            errorViewSwitcher.addView(wrongCodeView);

            FrameLayout bottomContainer = new FrameLayout(context);
            if (setup) {
                bottomContainer.addView(errorViewSwitcher, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 32));
            } else {
                bottomContainer.addView(errorViewSwitcher, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
                bottomContainer.addView(cantAccessEmailFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
                bottomContainer.addView(loginOrView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.CENTER, 0, 0, 0, 16));
                bottomContainer.addView(signInWithGoogleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 16));
            }
            addView(bottomContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        }

        private boolean requestingEmailReset;
        private void requestEmailReset() {
            if (requestingEmailReset) {
                return;
            }
            requestingEmailReset = true;

            Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);

            TLRPC.TL_auth_resetLoginEmail req = new TLRPC.TL_auth_resetLoginEmail();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (getParentActivity() == null) {
                    return;
                }
                requestingEmailReset = false;
                if (response instanceof TLRPC.TL_auth_sentCode) {
                    TLRPC.TL_auth_sentCode sentCode = (TLRPC.TL_auth_sentCode) response;
                    fillNextCodeParams(params, sentCode);
                } else if (error != null && error.text != null) {
                    if (error.text.contains("TASK_ALREADY_EXISTS")) {
                        new AlertDialog.Builder(getContext())
                                .setTitle(getString(R.string.LoginEmailResetPremiumRequiredTitle))
                                .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.LoginEmailResetPremiumRequiredMessage, LocaleController.addNbsp(PhoneFormat.getInstance().format("+" + requestPhone)))))
                                .setPositiveButton(getString(R.string.OK), null)
                                .show();
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        onBackPressed(true);
                        setPage(VIEW_PHONE_INPUT, true, null, true);
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                    } else {
                        AlertsCreator.processError(currentAccount, error, LoginActivity.this, req);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            signInWithGoogleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            loginOrView.updateColors();
            resendCodeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            cantAccessEmailView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            emailResetInView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            wrongCodeView.setTextColor(Theme.getColor(Theme.key_text_RedBold));

            codeFieldContainer.invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(errorColorTimeout);
            removeCallbacks(resendCodeTimeout);
        }

        private void showResendCodeView(boolean show) {
            AndroidUtilities.updateViewVisibilityAnimated(resendCodeView, show);
            AndroidUtilities.updateViewVisibilityAnimated(cantAccessEmailFrameLayout, !show && activityMode != MODE_CHANGE_LOGIN_EMAIL && !isSetup);

            if (loginOrView.getVisibility() != GONE) {
                loginOrView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.CENTER, 0, 0, 0, show ? 8 : 16));
                loginOrView.requestLayout();
            }
        }

        @Override
        public boolean hasCustomKeyboard() {
            return true;
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public String getHeaderName() {
            return getString(R.string.VerificationCode);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }

            currentParams = params;
            requestPhone = currentParams.getString("phoneFormated");
            phoneHash = currentParams.getString("phoneHash");
            phone = currentParams.getString("phone");
            emailPhone = currentParams.getString("ephone");
            isFromSetup = currentParams.getBoolean("setup");
            length = currentParams.getInt("length");
            email = currentParams.getString("email");
            resetAvailablePeriod = currentParams.getInt("resetAvailablePeriod");
            resetPendingDate = currentParams.getInt("resetPendingDate");

            if (activityMode == MODE_CHANGE_LOGIN_EMAIL) {
                confirmTextView.setText(LocaleController.formatString(R.string.CheckYourNewEmailSubtitle, email));
                AndroidUtilities.updateViewVisibilityAnimated(cantAccessEmailFrameLayout, false, 1f, false);
            } else if (isSetup) {
                confirmTextView.setText(LocaleController.formatString(R.string.VerificationCodeSubtitle, email));
                AndroidUtilities.updateViewVisibilityAnimated(cantAccessEmailFrameLayout, false, 1f, false);
            } else {
                AndroidUtilities.updateViewVisibilityAnimated(cantAccessEmailFrameLayout, true, 1f, false);

                cantAccessEmailView.setVisibility(resetPendingDate == 0 ? VISIBLE : GONE);
                emailResetInView.setVisibility(resetPendingDate != 0 ? VISIBLE : GONE);
                if (resetPendingDate != 0) {
                    updateResetPendingDate();
                }
            }

            codeFieldContainer.setNumbersCount(length, AUTH_TYPE_MESSAGE);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.setShowSoftInputOnFocusCompat(!(hasCustomKeyboard() && !isCustomKeyboardForceDisabled()));
                f.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (postedErrorColorTimeout) {
                            removeCallbacks(errorColorTimeout);
                            errorColorTimeout.run();
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
                f.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        keyboardView.setEditText((EditText) v);
                        keyboardView.setDispatchBackWhenEmpty(true);
                    }
                });
            }
            codeFieldContainer.setText("");

            if (!isFromSetup && activityMode != MODE_CHANGE_LOGIN_EMAIL) {
                String rawPattern = currentParams.getString("emailPattern");
                SpannableStringBuilder confirmText = new SpannableStringBuilder(rawPattern);
                int startIndex = rawPattern.indexOf('*'), endIndex = rawPattern.lastIndexOf('*');
                if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                    run.start = startIndex;
                    run.end = endIndex + 1;
                    confirmText.setSpan(new TextStyleSpan(run), startIndex, endIndex + 1, 0);
                }

                confirmTextView.setText(AndroidUtilities.formatSpannable(getString(R.string.CheckYourEmailSubtitle), confirmText));
            }

            int v = params.getBoolean("googleSignInAllowed") && PushListenerController.GooglePushListenerServiceProvider.INSTANCE.hasServices() ? VISIBLE : GONE;
            loginOrView.setVisibility(v);
            signInWithGoogleView.setVisibility(v);

            showKeyboard(codeFieldContainer.codeField[0]);
            codeFieldContainer.requestFocus();

            if (!restore && params.containsKey("nextType")) {
                AndroidUtilities.runOnUIThread(resendCodeTimeout, params.getInt("timeout"));
            }

            if (resetPendingDate != 0) {
                AndroidUtilities.runOnUIThread(updateResetPendingDateCallback, 1000);
            }
        }

        @Override
        public void onHide() {
            super.onHide();

            if (resetPendingDate != 0) {
                AndroidUtilities.cancelRunOnUIThread(updateResetPendingDateCallback);
            }
        }

        private String getTimePatternForTimer(int timeRemaining) {
            int days = timeRemaining / 86400;
            int hours = (timeRemaining % 86400) / 3600;
            int minutes = ((timeRemaining % 86400) % 3600) / 60;
            int seconds = ((timeRemaining % 86400) % 3600) % 60;

            if (hours >= 16) {
                days++;
            }

            String time;
            if (days != 0) {
                time = LocaleController.formatString(R.string.LoginEmailResetInSinglePattern, LocaleController.formatPluralString("Days", days));
            } else {
                String timer = (hours != 0 ? String.format(Locale.ROOT, "%02d:", hours) : "") + String.format(Locale.ROOT, "%02d:", minutes) + String.format(Locale.ROOT, "%02d", seconds);
                time = LocaleController.formatString(R.string.LoginEmailResetInSinglePattern, timer);
            }
            return time;
        }

        private String getTimePattern(int timeRemaining) {
            int days = timeRemaining / 86400;
            int hours = (timeRemaining % 86400) / 3600;
            int minutes = ((timeRemaining % 86400) % 3600) / 60;

            if (days == 0 && hours == 0) {
                minutes = Math.max(1, minutes);
            }

            String time;
            if (days != 0 && hours != 0) {
                time = LocaleController.formatString(R.string.LoginEmailResetInDoublePattern, LocaleController.formatPluralString("Days", days), LocaleController.formatPluralString("Hours", hours));
            } else if (hours != 0 && minutes != 0) {
                time = LocaleController.formatString(R.string.LoginEmailResetInDoublePattern, LocaleController.formatPluralString("Hours", hours), LocaleController.formatPluralString("Minutes", minutes));
            } else if (days != 0) {
                time = LocaleController.formatString(R.string.LoginEmailResetInSinglePattern, LocaleController.formatPluralString("Days", days));
            } else if (hours != 0) {
                time = LocaleController.formatString(R.string.LoginEmailResetInSinglePattern, LocaleController.formatPluralString("Hours", days));
            } else {
                time = LocaleController.formatString(R.string.LoginEmailResetInSinglePattern, LocaleController.formatPluralString("Minutes", minutes));
            }
            return time;
        }

        private void updateResetPendingDate() {
            int timeRemaining = (int) (resetPendingDate - System.currentTimeMillis() / 1000L);
            if (resetPendingDate <= 0 || timeRemaining <= 0) {
                emailResetInView.setVisibility(VISIBLE);
                emailResetInView.setText(getString(R.string.LoginEmailResetPleaseWait));
                AndroidUtilities.runOnUIThread(this::requestEmailReset, 1000);
                return;
            }
            String str = LocaleController.formatString(R.string.LoginEmailResetInTime, getTimePatternForTimer(timeRemaining));
            SpannableStringBuilder ssb = SpannableStringBuilder.valueOf(str);
            int startIndex = str.indexOf('*'), endIndex = str.lastIndexOf('*');
            if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
                ssb.replace(endIndex, endIndex + 1, "");
                ssb.replace(startIndex, startIndex + 1, "");
                ssb.setSpan(new ForegroundColorSpan(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4)), startIndex, endIndex - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            emailResetInView.setText(ssb);

            AndroidUtilities.runOnUIThread(updateResetPendingDateCallback, 1000);
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            try {
                codeFieldContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            if (clear) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setText("");
                }
            }
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateErrorProgress(1f);
            }
            codeFieldContainer.codeField[0].requestFocus();
            AndroidUtilities.shakeViewSpring(codeFieldContainer, () -> {
                postDelayed(()-> {
                    codeFieldContainer.isFocusSuppressed = false;
                    codeFieldContainer.codeField[0].requestFocus();

                    for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                        codeFieldContainer.codeField[a].animateErrorProgress(0f);
                    }
                }, 150);

                removeCallbacks(errorColorTimeout);
                postDelayed(errorColorTimeout, 3000);
                postedErrorColorTimeout = true;
            });
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(resendCodeTimeout);

            codeFieldContainer.isFocusSuppressed = true;
            if (codeFieldContainer.codeField != null) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.animateFocusedProgress(0);
                }
            }

            code = codeFieldContainer.getCode();
            if (code.length() == 0 && googleAccount == null) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);

            TLObject req;
            if (activityMode == MODE_CHANGE_LOGIN_EMAIL) {
                TLRPC.TL_account_verifyEmail request = new TLRPC.TL_account_verifyEmail();
                request.purpose = new TLRPC.TL_emailVerifyPurposeLoginChange();
                TLRPC.TL_emailVerificationCode verification = new TLRPC.TL_emailVerificationCode();
                verification.code = code;
                request.verification = verification;
                req = request;
            } else if (isFromSetup) {
                TLRPC.TL_account_verifyEmail request = new TLRPC.TL_account_verifyEmail();
                TLRPC.TL_emailVerifyPurposeLoginSetup setup = new TLRPC.TL_emailVerifyPurposeLoginSetup();
                setup.phone_number = requestPhone;
                setup.phone_code_hash = phoneHash;
                request.purpose = setup;
                TLRPC.TL_emailVerificationCode verificationCode = new TLRPC.TL_emailVerificationCode();
                verificationCode.code = code;
                request.verification = verificationCode;
                req = request;
            } else {
                TLRPC.TL_auth_signIn request = new TLRPC.TL_auth_signIn();
                request.phone_number = requestPhone;
                request.phone_code_hash = phoneHash;
                if (googleAccount != null) {
                    TLRPC.TL_emailVerificationGoogle verification = new TLRPC.TL_emailVerificationGoogle();
                    verification.token = googleAccount.getIdToken();
                    request.email_verification = verification;
                } else {
                    TLRPC.TL_emailVerificationCode verification = new TLRPC.TL_emailVerificationCode();
                    verification.code = code;
                    request.email_verification = verification;
                }
                request.flags |= 2;
                req = request;
            }

            codeFieldContainer.isFocusSuppressed = true;
            if (codeFieldContainer.codeField != null) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.animateFocusedProgress(0);
                }
            }

            String finalCode = code;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress(false);
                if (error == null) {
                    nextPressed = false;
                    showDoneButton(false, true);

                    Bundle params = new Bundle();
                    params.putString("phone", phone);
                    params.putString("ephone", emailPhone);
                    params.putString("phoneFormated", requestPhone);
                    params.putString("phoneHash", phoneHash);
                    params.putString("code", finalCode);


                    if (response instanceof TLRPC.TL_auth_authorizationSignUpRequired) {
                        TLRPC.TL_auth_authorizationSignUpRequired authorization = (TLRPC.TL_auth_authorizationSignUpRequired) response;
                        if (authorization.terms_of_service != null) {
                            currentTermsOfService = authorization.terms_of_service;
                        }
                        animateSuccess(() -> setPage(VIEW_REGISTER, true, params, false));
                    } else {
                        animateSuccess(() -> {
                            if (response instanceof TLRPC.TL_account_emailVerified && activityMode == MODE_CHANGE_LOGIN_EMAIL) {
                                finishFragment();
                                emailChangeFinishCallback.run();
                            } else if (response instanceof TLRPC.TL_account_emailVerifiedLogin) {
                                fillNextCodeParams(params, ((TLRPC.TL_account_emailVerifiedLogin) response).sent_code);
                            } else if (response instanceof TLRPC.TL_auth_authorization) {
                                onAuthSuccess((TLRPC.TL_auth_authorization) response);
                            }
                        });
                    }
                } else {
                    if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                        TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            nextPressed = false;
                            showDoneButton(false, true);
                            if (error1 == null) {
                                TLRPC.account_Password password = (TLRPC.account_Password) response1;
                                if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                    AlertsCreator.showUpdateAppAlert(getParentActivity(), getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                    return;
                                }
                                Bundle bundle = new Bundle();
                                SerializedData data = new SerializedData(password.getObjectSize());
                                password.serializeToStream(data);
                                bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                                bundle.putString("phoneFormated", requestPhone);
                                bundle.putString("phoneHash", phoneHash);
                                bundle.putString("code", finalCode);

                                animateSuccess(() -> setPage(VIEW_PASSWORD, true, bundle, false));
                            } else {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error1.text);
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        nextPressed = false;
                        showDoneButton(false, true);
                        boolean isWrongCode = false;
                        if (error.text.contains("EMAIL_ADDRESS_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.EmailAddressInvalid));
                        } else if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("CODE_EMPTY") || error.text.contains("CODE_INVALID") || error.text.contains("EMAIL_CODE_INVALID") || error.text.contains("PHONE_CODE_INVALID")) {
                            shakeWrongCode();
                            isWrongCode = true;
                        } else if (error.text.contains("EMAIL_TOKEN_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.EmailTokenInvalid));
                        } else if (error.text.contains("EMAIL_VERIFY_EXPIRED")) {
                            onBackPressed(true);
                            setPage(VIEW_PHONE_INPUT, true, null, true);
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                        } else {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                        }

                        if (!isWrongCode) {
                            if (codeFieldContainer.codeField != null) {
                                for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                                    codeFieldContainer.codeField[a].setText("");
                                }
                                codeFieldContainer.codeField[0].requestFocus();
                            }

                            codeFieldContainer.isFocusSuppressed = false;
                        }
                    }
                }
                googleAccount = null;
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        private void animateSuccess(Runnable callback) {
            if (googleAccount != null) {
                callback.run();
                return;
            }
            for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                int finalI = i;
                codeFieldContainer.postDelayed(()-> codeFieldContainer.codeField[finalI].animateSuccessProgress(1f), i * 75L);
            }
            codeFieldContainer.postDelayed(()->{
                for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                    codeFieldContainer.codeField[i].animateSuccessProgress(0f);
                }
                callback.run();
                codeFieldContainer.isFocusSuppressed = false;
            }, codeFieldContainer.codeField.length * 75L + 400L);
        }

        private void shakeWrongCode() {
            try {
                codeFieldContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}

            for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                codeFieldContainer.codeField[a].setText("");
                codeFieldContainer.codeField[a].animateErrorProgress(1f);
            }
            if (errorViewSwitcher.getCurrentView() == resendFrameLayout) {
                errorViewSwitcher.showNext();
                AndroidUtilities.updateViewVisibilityAnimated(cantAccessEmailFrameLayout, false, 1f, true);
            }
            codeFieldContainer.codeField[0].requestFocus();
            AndroidUtilities.shakeViewSpring(codeFieldContainer, 10f, () -> {
                postDelayed(()-> {
                    codeFieldContainer.isFocusSuppressed = false;
                    codeFieldContainer.codeField[0].requestFocus();

                    for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                        codeFieldContainer.codeField[a].animateErrorProgress(0f);
                    }
                }, 150);
            });
            removeCallbacks(errorColorTimeout);
            postDelayed(errorColorTimeout, 5000);
            postedErrorColorTimeout = true;
        }

        @Override
        public void onShow() {
            super.onShow();
            if (resetRequestPending) {
                resetRequestPending = false;
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                inboxImageView.getAnimatedDrawable().setCurrentFrame(0, false);
                inboxImageView.playAnimation();

                if (codeFieldContainer != null && codeFieldContainer.codeField != null) {
                    codeFieldContainer.setText("");
                    codeFieldContainer.codeField[0].requestFocus();
                }
            }, SHOW_DELAY);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeFieldContainer.getCode();
            if (code != null && code.length() != 0) {
                bundle.putString("emailcode_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("emailcode_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("emailcode_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String code = bundle.getString("emailcode_code");
            if (code != null) {
                codeFieldContainer.setText(code);
            }
        }
    }

    public class LoginActivityRecoverView extends SlideView {

        private CodeFieldContainer codeFieldContainer;
        private TextView titleView;
        private TextView confirmTextView;
        private TextView troubleButton;
        private RLottieImageView inboxImageView;

        private Bundle currentParams;
        private String passwordString;
        private boolean nextPressed;

        private String requestPhone, phoneHash, phoneCode;

        private boolean postedErrorColorTimeout;
        private Runnable errorColorTimeout = () -> {
            postedErrorColorTimeout = false;
            for (int i = 0; i < codeFieldContainer.codeField.length; i++) {
                codeFieldContainer.codeField[i].animateErrorProgress(0);
            }
        };

        public LoginActivityRecoverView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            FrameLayout inboxFrameLayout = new FrameLayout(context);
            inboxImageView = new RLottieImageView(context);
            inboxImageView.setAnimation(R.raw.tsv_setup_mail, 120, 120);
            inboxImageView.setAutoRepeat(false);
            inboxFrameLayout.addView(inboxImageView, LayoutHelper.createFrame(120, 120, Gravity.CENTER_HORIZONTAL));
            inboxFrameLayout.setVisibility(AndroidUtilities.isSmallScreen() || (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y && !AndroidUtilities.isTablet()) ? GONE : VISIBLE);
            addView(inboxFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(getString(R.string.EnterCode));
            titleView.setGravity(Gravity.CENTER);
            titleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

            confirmTextView = new TextView(context);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(Gravity.CENTER);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setText(getString(R.string.RestoreEmailSentInfo));
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 8, 12, 0));

            codeFieldContainer = new CodeFieldContainer(context) {
                @Override
                protected void processNextPressed() {
                    onNextPressed(null);
                }
            };
            codeFieldContainer.setNumbersCount(6, AUTH_TYPE_MESSAGE);
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.setShowSoftInputOnFocusCompat(!(hasCustomKeyboard() && !isCustomKeyboardForceDisabled()));
                f.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        if (postedErrorColorTimeout) {
                            removeCallbacks(errorColorTimeout);
                            errorColorTimeout.run();
                        }
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
                f.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        keyboardView.setEditText((EditText) v);
                        keyboardView.setDispatchBackWhenEmpty(true);
                    }
                });
            }

            addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0));

            troubleButton = new SpoilersTextView(context, false);
            troubleButton.setGravity(Gravity.CENTER);
            troubleButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            troubleButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            troubleButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            troubleButton.setMaxLines(2);

            troubleButton.setOnClickListener(view -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle))
                        .setMessage(getString("RestoreEmailTroubleText", R.string.RestoreEmailTroubleText))
                        .setPositiveButton(getString(R.string.OK), (dialogInterface, i) -> setPage(VIEW_PASSWORD, true, new Bundle(), true))
                        .setNegativeButton(getString(R.string.ResetAccount), (dialog, which) -> tryResetAccount(requestPhone, phoneHash, phoneCode));
                Dialog dialog = showDialog(builder.create());
                if (dialog != null) {
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);
                }
            });

            FrameLayout bottomContainer = new FrameLayout(context);
            bottomContainer.addView(troubleButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 32));
            addView(bottomContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
            VerticalPositionAutoAnimator.attach(troubleButton);
        }

        @Override
        public void updateColors() {
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            troubleButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));

            codeFieldContainer.invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(errorColorTimeout);
        }

        @Override
        public boolean hasCustomKeyboard() {
            return true;
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public String getHeaderName() {
            return getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            codeFieldContainer.setText("");
            currentParams = params;
            passwordString = currentParams.getString("password");
            requestPhone = currentParams.getString("requestPhone");
            phoneHash = currentParams.getString("phoneHash");
            phoneCode = currentParams.getString("phoneCode");
            String rawPattern = currentParams.getString("email_unconfirmed_pattern");
            SpannableStringBuilder unconfirmedPattern = SpannableStringBuilder.valueOf(rawPattern);
            int startIndex = rawPattern.indexOf('*'), endIndex = rawPattern.lastIndexOf('*');
            if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                run.start = startIndex;
                run.end = endIndex + 1;
                unconfirmedPattern.setSpan(new TextStyleSpan(run), startIndex, endIndex + 1, 0);
            }
            troubleButton.setText(AndroidUtilities.formatSpannable(getString(R.string.RestoreEmailNoAccess), unconfirmedPattern));

            showKeyboard(codeFieldContainer);
            codeFieldContainer.requestFocus();
        }

        private void onPasscodeError(boolean clear) {
            if (getParentActivity() == null) {
                return;
            }
            try {
                codeFieldContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            if (clear) {
                for (CodeNumberField f : codeFieldContainer.codeField) {
                    f.setText("");
                }
            }
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateErrorProgress(1f);
            }
            codeFieldContainer.codeField[0].requestFocus();
            AndroidUtilities.shakeViewSpring(codeFieldContainer, () -> {
                postDelayed(()-> {
                    codeFieldContainer.isFocusSuppressed = false;
                    codeFieldContainer.codeField[0].requestFocus();

                    for (int a = 0; a < codeFieldContainer.codeField.length; a++) {
                        codeFieldContainer.codeField[a].animateErrorProgress(0f);
                    }
                }, 150);

                removeCallbacks(errorColorTimeout);
                postDelayed(errorColorTimeout, 3000);
                postedErrorColorTimeout = true;
            });
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            codeFieldContainer.isFocusSuppressed = true;
            for (CodeNumberField f : codeFieldContainer.codeField) {
                f.animateFocusedProgress(0);
            }

            code = codeFieldContainer.getCode();
            if (code.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            needShowProgress(0);
            TLRPC.TL_auth_checkRecoveryPassword req = new TLRPC.TL_auth_checkRecoveryPassword();
            req.code = code;
            String finalCode = code;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress(false);
                nextPressed = false;
                if (response instanceof TLRPC.TL_boolTrue) {
                    Bundle params = new Bundle();
                    params.putString("emailCode", finalCode);
                    params.putString("password", passwordString);
                    setPage(VIEW_NEW_PASSWORD_STAGE_1, true, params, false);
                } else {
                    if (error == null || error.text.startsWith("CODE_INVALID")) {
                        onPasscodeError(true);
                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                        int time = Utilities.parseInt(error.text);
                        String timeString;
                        if (time < 60) {
                            timeString = LocaleController.formatPluralString("Seconds", time);
                        } else {
                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                        }
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                    } else {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            currentParams = null;
            nextPressed = false;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                inboxImageView.getAnimatedDrawable().setCurrentFrame(0, false);
                inboxImageView.playAnimation();
                if (codeFieldContainer != null) {
                    codeFieldContainer.codeField[0].requestFocus();
                }
            }, SHOW_DELAY);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeFieldContainer.getCode();
            if (code != null && code.length() != 0) {
                bundle.putString("recoveryview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("recoveryview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }
            String code = bundle.getString("recoveryview_code");
            if (code != null) {
                codeFieldContainer.setText(code);
            }
        }
    }

    public class LoginActivityNewPasswordView extends SlideView {

        private OutlineTextContainerView[] outlineFields;
        private EditTextBoldCursor[] codeField;
        private TextView titleTextView;
        private TextView confirmTextView;
        private TextView cancelButton;
        private ImageView passwordButton;

        private String emailCode;
        private String newPassword;
        private String passwordString;
        private TLRPC.account_Password currentPassword;
        private Bundle currentParams;
        private boolean nextPressed;
        private int currentStage;

        private boolean isPasswordVisible;

        public LoginActivityNewPasswordView(Context context, int stage) {
            super(context);
            currentStage = stage;

            setOrientation(VERTICAL);

            codeField = new EditTextBoldCursor[stage == 1 ? 1 : 2];
            outlineFields = new OutlineTextContainerView[codeField.length];

            titleTextView = new TextView(context);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            titleTextView.setText(getString(R.string.SetNewPassword));
            addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, AndroidUtilities.isSmallScreen() ? 16 : 72, 8, 0));

            confirmTextView = new TextView(context);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            confirmTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 6, 8, 16));

            for (int a = 0; a < codeField.length; a++) {
                OutlineTextContainerView outlineField = new OutlineTextContainerView(context);
                outlineFields[a] = outlineField;
                outlineField.setText(getString(stage == 0 ? a == 0 ? R.string.PleaseEnterNewFirstPasswordHint : R.string.PleaseEnterNewSecondPasswordHint : R.string.PasswordHintPlaceholder));

                codeField[a] = new EditTextBoldCursor(context);
                codeField[a].setCursorSize(AndroidUtilities.dp(20));
                codeField[a].setCursorWidth(1.5f);
                codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
                codeField[a].setMaxLines(1);
                codeField[a].setBackground(null);

                int padding = AndroidUtilities.dp(16);
                codeField[a].setPadding(padding, padding, padding, padding);
                if (stage == 0) {
                    codeField[a].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    codeField[a].setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                codeField[a].setTypeface(Typeface.DEFAULT);
                codeField[a].setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

                EditText field = codeField[a];
                boolean showPasswordButton = a == 0 && stage == 0;
                field.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (showPasswordButton) {
                            if (passwordButton.getVisibility() != VISIBLE && !TextUtils.isEmpty(s)) {
                                if (isPasswordVisible) {
                                    passwordButton.callOnClick();
                                }
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, true, 0.1f, true);
                            } else if (passwordButton.getVisibility() != GONE && TextUtils.isEmpty(s)) {
                                AndroidUtilities.updateViewVisibilityAnimated(passwordButton, false, 0.1f, true);
                            }
                        }
                    }
                });
                codeField[a].setOnFocusChangeListener((v, hasFocus) -> outlineField.animateSelection(hasFocus ? 1f : 0f));

                if (showPasswordButton) {
                    LinearLayout linearLayout = new LinearLayout(context);
                    linearLayout.setOrientation(HORIZONTAL);
                    linearLayout.setGravity(Gravity.CENTER_VERTICAL);
                    linearLayout.addView(codeField[a], LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                    passwordButton = new ImageView(context);
                    passwordButton.setImageResource(R.drawable.msg_message);
                    AndroidUtilities.updateViewVisibilityAnimated(passwordButton, true, 0.1f, false);
                    passwordButton.setOnClickListener(v -> {
                        isPasswordVisible = !isPasswordVisible;

                        for (int i = 0; i < codeField.length; i++) {
                            int selectionStart = codeField[i].getSelectionStart(), selectionEnd = codeField[i].getSelectionEnd();
                            codeField[i].setInputType(InputType.TYPE_CLASS_TEXT | (isPasswordVisible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                            codeField[i].setSelection(selectionStart, selectionEnd);
                        }

                        passwordButton.setTag(isPasswordVisible);
                        passwordButton.setColorFilter(Theme.getColor(isPasswordVisible ? Theme.key_windowBackgroundWhiteInputFieldActivated : Theme.key_windowBackgroundWhiteHintText));
                    });
                    linearLayout.addView(passwordButton, LayoutHelper.createLinearRelatively(24, 24, 0, 0, 0, 14, 0));

                    outlineField.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                } else {
                    outlineField.addView(codeField[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                }
                outlineField.attachEditText(codeField[a]);
                addView(outlineField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 16, 16, 0));
                int num = a;
                codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (num == 0 && codeField.length == 2) {
                        codeField[1].requestFocus();
                        return true;
                    } else if (i == EditorInfo.IME_ACTION_NEXT) {
                        onNextPressed(null);
                        return true;
                    }
                    return false;
                });
            }

            if (stage == 0) {
                confirmTextView.setText(getString("PleaseEnterNewFirstPasswordLogin", R.string.PleaseEnterNewFirstPasswordLogin));
            } else {
                confirmTextView.setText(getString("PasswordHintTextLogin", R.string.PasswordHintTextLogin));
            }

            cancelButton = new TextView(context);
            cancelButton.setGravity(Gravity.CENTER | Gravity.LEFT);
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            cancelButton.setText(getString(R.string.YourEmailSkip));

            FrameLayout bottomContainer = new FrameLayout(context);
            bottomContainer.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, (Build.VERSION.SDK_INT >= 21 ? 56 : 60), Gravity.BOTTOM, 0, 0, 0, 32));
            addView(bottomContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
            VerticalPositionAutoAnimator.attach(cancelButton);

            cancelButton.setOnClickListener(view -> {
                if (currentStage == 0) {
                    recoverPassword(null, null);
                } else {
                    recoverPassword(newPassword, null);
                }
            });
        }

        @Override
        public void updateColors() {
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            for (EditTextBoldCursor editText : codeField) {
                editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
            for (OutlineTextContainerView outlineField : outlineFields) {
                outlineField.updateColor();
            }
            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            if (passwordButton != null) {
                passwordButton.setColorFilter(Theme.getColor(isPasswordVisible ? Theme.key_windowBackgroundWhiteInputFieldActivated : Theme.key_windowBackgroundWhiteHintText));
                passwordButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1));
            }
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public String getHeaderName() {
            return getString("NewPassword", R.string.NewPassword);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            for (int a = 0; a < codeField.length; a++) {
                codeField[a].setText("");
            }
            currentParams = params;
            emailCode = currentParams.getString("emailCode");
            passwordString = currentParams.getString("password");
            if (passwordString != null) {
                SerializedData data = new SerializedData(Utilities.hexToBytes(passwordString));
                currentPassword = TLRPC.account_Password.TLdeserialize(data, data.readInt32(false), false);
                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
            }
            newPassword = currentParams.getString("new_password");

            showKeyboard(codeField[0]);
            codeField[0].requestFocus();
        }

        private void onPasscodeError(boolean clear, int num) {
            if (getParentActivity() == null) {
                return;
            }
            try {
                codeField[num].performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            AndroidUtilities.shakeView(codeField[num]);
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            code = codeField[0].getText().toString();
            if (code.length() == 0) {
                onPasscodeError(false, 0);
                return;
            }
            if (currentStage == 0) {
                if (!code.equals(codeField[1].getText().toString())) {
                    onPasscodeError(false, 1);
                    return;
                }
                Bundle params = new Bundle();
                params.putString("emailCode", emailCode);
                params.putString("new_password", code);
                params.putString("password", passwordString);
                setPage(VIEW_NEW_PASSWORD_STAGE_2, true, params, false);
            } else {
                nextPressed = true;
                needShowProgress(0);
                recoverPassword(newPassword, code);
            }
        }

        private void recoverPassword(String password, String hint) {
            TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
            req.code = emailCode;
            if (!TextUtils.isEmpty(password)) {
                req.flags |= 1;
                req.new_settings = new TLRPC.TL_account_passwordInputSettings();
                req.new_settings.flags |= 1;
                req.new_settings.hint = hint != null ? hint : "";
                req.new_settings.new_algo = currentPassword.new_algo;
            }
            Utilities.globalQueue.postRunnable(() -> {
                byte[] newPasswordBytes;
                if (password != null) {
                    newPasswordBytes = AndroidUtilities.getStringBytes(password);
                } else {
                    newPasswordBytes = null;
                }

                RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error != null && ("SRP_ID_INVALID".equals(error.text) || "NEW_SALT_INVALID".equals(error.text))) {
                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (error2 == null) {
                                currentPassword = (TLRPC.account_Password) response2;
                                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                recoverPassword(password, hint);
                            }
                        }), ConnectionsManager.RequestFlagWithoutLogin);
                        return;
                    }
                    needHideProgress(false);
                    if (response instanceof TLRPC.auth_Authorization) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(getString(R.string.Continue), (dialogInterface, i) -> onAuthSuccess((TLRPC.TL_auth_authorization) response));
                        if (TextUtils.isEmpty(password)) {
                            builder.setMessage(getString(R.string.YourPasswordReset));
                        } else {
                            builder.setMessage(getString(R.string.YourPasswordChangedSuccessText));
                        }
                        builder.setTitle(getString(R.string.TwoStepVerificationTitle));
                        Dialog dialog = showDialog(builder.create());
                        if (dialog != null) {
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setCancelable(false);
                        }
                    } else if (error != null) {
                        nextPressed = false;
                        if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error.text);
                        }
                    }
                });

                if (currentPassword.new_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    if (password != null) {
                        TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.new_algo;
                        req.new_settings.new_password_hash = SRPHelper.getVBytes(newPasswordBytes, algo);
                        if (req.new_settings.new_password_hash == null) {
                            TLRPC.TL_error error = new TLRPC.TL_error();
                            error.text = "ALGO_INVALID";
                            requestDelegate.run(null, error);
                        }
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.text = "PASSWORD_HASH_INVALID";
                    requestDelegate.run(null, error);
                }
            });
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            currentParams = null;
            nextPressed = false;
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField[0].requestFocus();
                    codeField[0].setSelection(codeField[0].length());
                    AndroidUtilities.showKeyboard(codeField[0]);
                }
            }, SHOW_DELAY);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            if (currentParams != null) {
                bundle.putBundle("recoveryview_params" + currentStage, currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_params" + currentStage);
            if (currentParams != null) {
                setParams(currentParams, true);
            }
        }
    }

    public class LoginActivityRegisterView extends SlideView implements ImageUpdater.ImageUpdaterDelegate {
        private OutlineTextContainerView firstNameOutlineView, lastNameOutlineView;

        private EditTextBoldCursor firstNameField;
        private EditTextBoldCursor lastNameField;
        private BackupImageView avatarImage;
        private AvatarDrawable avatarDrawable;
        private View avatarOverlay;
        private RLottieImageView avatarEditor;
        private RadialProgressView avatarProgressView;
        private AnimatorSet avatarAnimation;
        private TextView descriptionTextView;
        private TextView wrongNumber;
        private TextView privacyView;
        private TextView titleTextView;
        private FrameLayout editTextContainer;
        private String requestPhone;
        private String phoneHash;
        private Bundle currentParams;
        private boolean nextPressed = false;

        private RLottieDrawable cameraDrawable;
        private RLottieDrawable cameraWaitDrawable;
        private boolean isCameraWaitAnimationAllowed = true;

        private ImageUpdater imageUpdater;

        private TLRPC.FileLocation avatar;
        private TLRPC.FileLocation avatarBig;

        private boolean createAfterUpload;

        public class LinkSpan extends ClickableSpan {
            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }

            @Override
            public void onClick(View widget) {
                showTermsOfService(false);
            }
        }

        private void showTermsOfService(boolean needAccept) {
            if (currentTermsOfService == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(getString("TermsOfService", R.string.TermsOfService));

            if (needAccept) {
                builder.setPositiveButton(getString("Accept", R.string.Accept), (dialog, which) -> {
                    currentTermsOfService.popup = false;
                    onNextPressed(null);
                });
                builder.setNegativeButton(getString("Decline", R.string.Decline), (dialog, which) -> {
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setTitle(getString("TermsOfService", R.string.TermsOfService));
                    builder1.setMessage(getString("TosDecline", R.string.TosDecline));
                    builder1.setPositiveButton(getString("SignUp", R.string.SignUp), (dialog1, which1) -> {
                        currentTermsOfService.popup = false;
                        onNextPressed(null);
                    });
                    builder1.setNegativeButton(getString("Decline", R.string.Decline), (dialog12, which12) -> {
                        onBackPressed(true);
                        setPage(VIEW_PHONE_INPUT, true, null, true);
                    });
                    showDialog(builder1.create());
                });
            } else {
                builder.setPositiveButton(getString("OK", R.string.OK), null);
            }

            SpannableStringBuilder text = new SpannableStringBuilder(currentTermsOfService.text);
            MessageObject.addEntitiesToText(text, currentTermsOfService.entities, false, false, false, false);
            builder.setMessage(text);

            showDialog(builder.create());
        }

        public LoginActivityRegisterView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            imageUpdater = new ImageUpdater(false, ImageUpdater.FOR_TYPE_USER, false);
            imageUpdater.setOpenWithFrontfaceCamera(true);
            imageUpdater.setSearchAvailable(false);
            imageUpdater.setUploadAfterSelect(false);
            imageUpdater.parentFragment = LoginActivity.this;
            imageUpdater.setDelegate(this);

            FrameLayout avatarContainer = new FrameLayout(context);
            addView(avatarContainer, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL));

            avatarDrawable = new AvatarDrawable();

            avatarImage = new BackupImageView(context) {
                @Override
                public void invalidate() {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate();
                }

                @Override
                public void invalidate(int l, int t, int r, int b) {
                    if (avatarOverlay != null) {
                        avatarOverlay.invalidate();
                    }
                    super.invalidate(l, t, r, b);
                }
            };
            avatarImage.setRoundRadius(AndroidUtilities.dp(64));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REGISTER);
            avatarDrawable.setInfo(5, null, null);
            avatarImage.setImageDrawable(avatarDrawable);
            avatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0x55000000);

            avatarOverlay = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (avatarImage != null && avatarProgressView.getVisibility() == VISIBLE) {
                        paint.setAlpha((int) (0x55 * avatarImage.getImageReceiver().getCurrentAlpha() * avatarProgressView.getAlpha()));
                        canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, getMeasuredWidth() / 2.0f, paint);
                    }
                }
            };
            avatarContainer.addView(avatarOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            avatarOverlay.setOnClickListener(view -> {
                imageUpdater.openMenu(avatar != null, () -> {
                    avatar = null;
                    avatarBig = null;
                    showAvatarProgress(false, true);
                    avatarImage.setImage(null, null, avatarDrawable, null);
                    avatarEditor.setAnimation(cameraDrawable);
                    cameraDrawable.setCurrentFrame(0);
                    isCameraWaitAnimationAllowed = true;
                }, dialog -> {
                    if (!imageUpdater.isUploadingImage()) {
                        avatarEditor.setAnimation(cameraDrawable);
                        cameraDrawable.setCustomEndFrame(86);
                        avatarEditor.setOnAnimationEndListener(() -> isCameraWaitAnimationAllowed = true);
                        avatarEditor.playAnimation();
                    } else {
                        avatarEditor.setAnimation(cameraDrawable);
                        cameraDrawable.setCurrentFrame(0, false);
                        isCameraWaitAnimationAllowed = true;
                    }
                }, 0);
                isCameraWaitAnimationAllowed = false;
                avatarEditor.setAnimation(cameraDrawable);
                cameraDrawable.setCurrentFrame(0);
                cameraDrawable.setCustomEndFrame(43);
                avatarEditor.playAnimation();
            });

            cameraDrawable = new RLottieDrawable(R.raw.camera, String.valueOf(R.raw.camera), AndroidUtilities.dp(70), AndroidUtilities.dp(70), false, null);
            cameraWaitDrawable = new RLottieDrawable(R.raw.camera_wait, String.valueOf(R.raw.camera_wait), AndroidUtilities.dp(70), AndroidUtilities.dp(70), false, null);

            avatarEditor = new RLottieImageView(context) {
                @Override
                public void invalidate(int l, int t, int r, int b) {
                    super.invalidate(l, t, r, b);
                    avatarOverlay.invalidate();
                }

                @Override
                public void invalidate() {
                    super.invalidate();
                    avatarOverlay.invalidate();
                }
            };
            avatarEditor.setScaleType(ImageView.ScaleType.CENTER);
            avatarEditor.setAnimation(cameraDrawable);
            avatarEditor.setEnabled(false);
            avatarEditor.setClickable(false);
            avatarContainer.addView(avatarEditor, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            avatarEditor.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                private long lastRun = System.currentTimeMillis();
                private boolean isAttached;
                private Runnable cameraWaitCallback = () -> {
                    if (isAttached) {
                        if (isCameraWaitAnimationAllowed && System.currentTimeMillis() - lastRun >= 10000) {
                            avatarEditor.setAnimation(cameraWaitDrawable);
                            cameraWaitDrawable.setCurrentFrame(0, false);
                            cameraWaitDrawable.setOnAnimationEndListener(() -> AndroidUtilities.runOnUIThread(()->{
                                cameraDrawable.setCurrentFrame(0, false);
                                avatarEditor.setAnimation(cameraDrawable);
                            }));
                            avatarEditor.playAnimation();
                            lastRun = System.currentTimeMillis();
                        }

                        avatarEditor.postDelayed(this.cameraWaitCallback, 1000);
                    }
                };

                @Override
                public void onViewAttachedToWindow(View v) {
                    isAttached = true;
                    v.post(cameraWaitCallback);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    isAttached = false;
                    v.removeCallbacks(cameraWaitCallback);
                }
            });

            avatarProgressView = new RadialProgressView(context) {
                @Override
                public void setAlpha(float alpha) {
                    super.setAlpha(alpha);
                    avatarOverlay.invalidate();
                }
            };
            avatarProgressView.setSize(AndroidUtilities.dp(30));
            avatarProgressView.setProgressColor(0xffffffff);
            avatarContainer.addView(avatarProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            showAvatarProgress(false, false);

            titleTextView = new TextView(context);
            titleTextView.setText(getString(R.string.RegistrationProfileInfo));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 12, 8, 0));

            descriptionTextView = new TextView(context);
            descriptionTextView.setText(getString("RegisterText2", R.string.RegisterText2));
            descriptionTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descriptionTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(descriptionTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 6, 8, 0));

            editTextContainer = new FrameLayout(context);
            addView(editTextContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 21, 8, 0));

            firstNameOutlineView = new OutlineTextContainerView(context);
            firstNameOutlineView.setText(getString(R.string.FirstName));

            firstNameField = new EditTextBoldCursor(context);
            firstNameField.setCursorSize(AndroidUtilities.dp(20));
            firstNameField.setCursorWidth(1.5f);
            firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            firstNameField.setMaxLines(1);
            firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            firstNameField.setOnFocusChangeListener((v, hasFocus) -> firstNameOutlineView.animateSelection(hasFocus ? 1f : 0f));
            firstNameField.setBackground(null);
            firstNameField.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

            firstNameOutlineView.attachEditText(firstNameField);
            firstNameOutlineView.addView(firstNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            firstNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    lastNameField.requestFocus();
                    return true;
                }
                return false;
            });

            lastNameOutlineView = new OutlineTextContainerView(context);
            lastNameOutlineView.setText(getString(R.string.LastName));

            lastNameField = new EditTextBoldCursor(context);
            lastNameField.setCursorSize(AndroidUtilities.dp(20));
            lastNameField.setCursorWidth(1.5f);
            lastNameField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            lastNameField.setMaxLines(1);
            lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            lastNameField.setOnFocusChangeListener((v, hasFocus) -> lastNameOutlineView.animateSelection(hasFocus ? 1f : 0f));
            lastNameField.setBackground(null);
            lastNameField.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

            lastNameOutlineView.attachEditText(lastNameField);
            lastNameOutlineView.addView(lastNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            lastNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE || i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });
            buildEditTextLayout(AndroidUtilities.isSmallScreen());

            wrongNumber = new TextView(context);
            wrongNumber.setText(getString("CancelRegistration", R.string.CancelRegistration));
            wrongNumber.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
            wrongNumber.setVisibility(GONE);
            addView(wrongNumber, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 20, 0, 0));
            wrongNumber.setOnClickListener(view -> {
                if (radialProgressView.getTag() != null) {
                    return;
                }
                onBackPressed(false);
            });

            FrameLayout privacyLayout = new FrameLayout(context);
            addView(privacyLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM));

            privacyView = new TextView(context);
            privacyView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
            privacyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.isSmallScreen() ? 13 : 14);
            privacyView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            privacyView.setGravity(Gravity.CENTER_VERTICAL);
            privacyLayout.addView(privacyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? 56 : 60, Gravity.LEFT | Gravity.BOTTOM, 14, 0, 70, 32));
            VerticalPositionAutoAnimator.attach(privacyView);

            String str = getString("TermsOfServiceLogin", R.string.TermsOfServiceLogin);
            SpannableStringBuilder text = new SpannableStringBuilder(str);
            int index1 = str.indexOf('*');
            int index2 = str.lastIndexOf('*');
            if (index1 != -1 && index2 != -1 && index1 != index2) {
                text.replace(index2, index2 + 1, "");
                text.replace(index1, index1 + 1, "");
                text.setSpan(new LinkSpan(), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            privacyView.setText(text);
        }

        @Override
        public void updateColors() {
            avatarDrawable.invalidateSelf();
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            firstNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
            lastNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            lastNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
            wrongNumber.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            privacyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            privacyView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));

            firstNameOutlineView.updateColor();
            lastNameOutlineView.updateColor();
        }

        private void buildEditTextLayout(boolean small) {
            boolean firstHasFocus = firstNameField.hasFocus(), lastHasFocus = lastNameField.hasFocus();
            editTextContainer.removeAllViews();

            if (small) {
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(HORIZONTAL);

                firstNameOutlineView.setText(getString(R.string.FirstNameSmall));
                lastNameOutlineView.setText(getString(R.string.LastNameSmall));

                linearLayout.addView(firstNameOutlineView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));
                linearLayout.addView(lastNameOutlineView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 8, 0, 0, 0));

                editTextContainer.addView(linearLayout);

                if (firstHasFocus) {
                    firstNameField.requestFocus();
                    AndroidUtilities.showKeyboard(firstNameField);
                } else if (lastHasFocus) {
                    lastNameField.requestFocus();
                    AndroidUtilities.showKeyboard(lastNameField);
                }
            } else {
                firstNameOutlineView.setText(getString(R.string.FirstName));
                lastNameOutlineView.setText(getString(R.string.LastName));

                editTextContainer.addView(firstNameOutlineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 8, 0, 8, 0));
                editTextContainer.addView(lastNameOutlineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 8, 82, 8, 0));
            }
        }

        @Override
        public void didUploadPhoto(final TLRPC.InputFile photo, final TLRPC.InputFile video, double videoStartTimestamp, String videoPath, final TLRPC.PhotoSize bigSize, final TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
            AndroidUtilities.runOnUIThread(() -> {
                avatar = smallSize.location;
                avatarBig = bigSize.location;
                avatarImage.setImage(ImageLocation.getForLocal(avatar), "50_50", avatarDrawable, null);
            });
        }

        private void showAvatarProgress(boolean show, boolean animated) {
            if (avatarEditor == null) {
                return;
            }
            if (avatarAnimation != null) {
                avatarAnimation.cancel();
                avatarAnimation = null;
            }
            if (animated) {
                avatarAnimation = new AnimatorSet();
                if (show) {
                    avatarProgressView.setVisibility(View.VISIBLE);

                    avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 1.0f));
                } else {
                    avatarEditor.setVisibility(View.VISIBLE);

                    avatarAnimation.playTogether(ObjectAnimator.ofFloat(avatarEditor, View.ALPHA, 1.0f),
                            ObjectAnimator.ofFloat(avatarProgressView, View.ALPHA, 0.0f));
                }
                avatarAnimation.setDuration(180);
                avatarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (avatarAnimation == null || avatarEditor == null) {
                            return;
                        }
                        if (show) {
                            avatarEditor.setVisibility(View.INVISIBLE);
                        } else {
                            avatarProgressView.setVisibility(View.INVISIBLE);
                        }
                        avatarAnimation = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        avatarAnimation = null;
                    }
                });
                avatarAnimation.start();
            } else {
                if (show) {
                    avatarEditor.setAlpha(1.0f);
                    avatarEditor.setVisibility(View.INVISIBLE);
                    avatarProgressView.setAlpha(1.0f);
                    avatarProgressView.setVisibility(View.VISIBLE);
                } else {
                    avatarEditor.setAlpha(1.0f);
                    avatarEditor.setVisibility(View.VISIBLE);
                    avatarProgressView.setAlpha(0.0f);
                    avatarProgressView.setVisibility(View.INVISIBLE);
                }
            }
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (!force) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString(R.string.Warning));
                builder.setMessage(getString("AreYouSureRegistration", R.string.AreYouSureRegistration));
                builder.setNegativeButton(getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    onBackPressed(true);
                    setPage(VIEW_PHONE_INPUT, true, null, true);
                    hidePrivacyView();
                });
                builder.setPositiveButton(getString("Continue", R.string.Continue), null);
                showDialog(builder.create());
                return false;
            }
            needHideProgress(true);
            nextPressed = false;
            currentParams = null;
            return true;
        }

        @Override
        public String getHeaderName() {
            return getString("YourName", R.string.YourName);
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onShow() {
            super.onShow();
            if (privacyView != null) {
                if (restoringState) {
                    privacyView.setAlpha(1f);
                } else {
                    privacyView.setAlpha(0f);
                    privacyView.animate().alpha(1f).setDuration(200).setStartDelay(300).setInterpolator(AndroidUtilities.decelerateInterpolator).start();
                }
            }
            if (firstNameField != null) {
                firstNameField.requestFocus();
                firstNameField.setSelection(firstNameField.length());
                AndroidUtilities.showKeyboard(firstNameField);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (firstNameField != null) {
                    firstNameField.requestFocus();
                    firstNameField.setSelection(firstNameField.length());
                    AndroidUtilities.showKeyboard(firstNameField);
                }
            }, SHOW_DELAY);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            firstNameField.setText("");
            lastNameField.setText("");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            currentParams = params;
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }
            if (currentTermsOfService != null && currentTermsOfService.popup) {
                showTermsOfService(true);
                return;
            }
            if (firstNameField.length() == 0) {
                onFieldError(firstNameOutlineView, true);
                return;
            }
            nextPressed = true;
            TLRPC.TL_auth_signUp req = new TLRPC.TL_auth_signUp();
            req.phone_code_hash = phoneHash;
            req.phone_number = requestPhone;
            req.first_name = firstNameField.getText().toString();
            req.last_name = lastNameField.getText().toString();
            needShowProgress(0);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (response instanceof TLRPC.TL_auth_authorization) {
                    hidePrivacyView();
                    showDoneButton(false, true);
                    postDelayed(() -> {
                        needHideProgress(false, false);
                        AndroidUtilities.hideKeyboard(fragmentView.findFocus());
                        onAuthSuccess((TLRPC.TL_auth_authorization) response, true);
                        if (avatarBig != null) {
                            TLRPC.FileLocation avatar = avatarBig;
                            Utilities.cacheClearQueue.postRunnable(()-> MessagesController.getInstance(currentAccount).uploadAndApplyUserAvatar(avatar));
                        }
                    }, 150);
                } else {
                    needHideProgress(false);
                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidCode", R.string.InvalidCode));
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        onBackPressed(true);
                        setPage(VIEW_PHONE_INPUT, true, null, true);
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                    } else if (error.text.contains("FIRSTNAME_INVALID")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidFirstName", R.string.InvalidFirstName));
                    } else if (error.text.contains("LASTNAME_INVALID")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidLastName", R.string.InvalidLastName));
                    } else {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String first = firstNameField.getText().toString();
            if (first.length() != 0) {
                bundle.putString("registerview_first", first);
            }
            String last = lastNameField.getText().toString();
            if (last.length() != 0) {
                bundle.putString("registerview_last", last);
            }
            if (currentTermsOfService != null) {
                SerializedData data = new SerializedData(currentTermsOfService.getObjectSize());
                currentTermsOfService.serializeToStream(data);
                String str = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                bundle.putString("terms", str);
                data.cleanup();
            }
            if (currentParams != null) {
                bundle.putBundle("registerview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("registerview_params");
            if (currentParams != null) {
                setParams(currentParams, true);
            }

            try {
                String terms = bundle.getString("terms");
                if (terms != null) {
                    byte[] arr = Base64.decode(terms, Base64.DEFAULT);
                    if (arr != null) {
                        SerializedData data = new SerializedData(arr);
                        currentTermsOfService = TLRPC.TL_help_termsOfService.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            String first = bundle.getString("registerview_first");
            if (first != null) {
                firstNameField.setText(first);
            }
            String last = bundle.getString("registerview_last");
            if (last != null) {
                lastNameField.setText(last);
            }
        }

        private void hidePrivacyView() {
            privacyView.animate().alpha(0f).setDuration(150).setStartDelay(0).setInterpolator(AndroidUtilities.accelerateInterpolator).start();
        }
    }

    private boolean showKeyboard(View editText) {
        if (!isCustomKeyboardVisible()) {
            return AndroidUtilities.showKeyboard(editText);
        }
        return true;
    }

    public LoginActivity setIntroView(View intro, TextView startButton) {
        introView = intro;
        startMessagingButton = startButton;
        isAnimatingIntro = true;
        return this;
    }

    @Override
    public AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        if (isOpen && introView != null) {
            if (fragmentView.getParent() instanceof View) {
                ((View) fragmentView.getParent()).setTranslationX(0);
            }

            TransformableLoginButtonView transformButton = new TransformableLoginButtonView(fragmentView.getContext());
            transformButton.setButtonText(startMessagingButton.getPaint(), startMessagingButton.getText().toString());

            int oldTransformWidth = startMessagingButton.getWidth(), oldTransformHeight = startMessagingButton.getHeight();
            int newTransformSize = floatingButtonIcon.getLayoutParams().width;
            ViewGroup.MarginLayoutParams transformParams = new FrameLayout.LayoutParams(oldTransformWidth, oldTransformHeight);
            transformButton.setLayoutParams(transformParams);

            int[] loc = new int[2];
            fragmentView.getLocationInWindow(loc);
            int fragmentX = loc[0], fragmentY = loc[1];

            startMessagingButton.getLocationInWindow(loc);
            float fromX = loc[0] - fragmentX, fromY = loc[1] - fragmentY;
            transformButton.setTranslationX(fromX);
            transformButton.setTranslationY(fromY);

            int toX = getParentLayout().getView().getWidth() - floatingButtonIcon.getLayoutParams().width - ((ViewGroup.MarginLayoutParams)floatingButtonContainer.getLayoutParams()).rightMargin - getParentLayout().getView().getPaddingLeft() - getParentLayout().getView().getPaddingRight(),
                    toY = getParentLayout().getView().getHeight() - floatingButtonIcon.getLayoutParams().height - ((ViewGroup.MarginLayoutParams)floatingButtonContainer.getLayoutParams()).bottomMargin -
                            (isCustomKeyboardVisible() ? AndroidUtilities.dp(CustomPhoneKeyboardView.KEYBOARD_HEIGHT_DP) : 0) - getParentLayout().getView().getPaddingTop() - getParentLayout().getView().getPaddingBottom();

            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    floatingButtonContainer.setVisibility(View.INVISIBLE);
                    keyboardLinearLayout.setAlpha(0);
                    fragmentView.setBackgroundColor(Color.TRANSPARENT);
                    startMessagingButton.setVisibility(View.INVISIBLE);

                    FrameLayout frameLayout = (FrameLayout) fragmentView;
                    frameLayout.addView(transformButton);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    keyboardLinearLayout.setAlpha(1);
                    startMessagingButton.setVisibility(View.VISIBLE);
                    fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    floatingButtonContainer.setVisibility(View.VISIBLE);

                    FrameLayout frameLayout = (FrameLayout) fragmentView;
                    frameLayout.removeView(transformButton);

                    if (animationFinishCallback != null) {
                        AndroidUtilities.runOnUIThread(animationFinishCallback);
                        animationFinishCallback = null;
                    }
                    isAnimatingIntro = false;

                    callback.run();
                }
            });
            int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite);
            int initialAlpha = Color.alpha(bgColor);
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                keyboardLinearLayout.setAlpha(val);
                fragmentView.setBackgroundColor(ColorUtils.setAlphaComponent(bgColor, (int) (initialAlpha * val)));

                float inverted = 1f - val;
                slideViewsContainer.setTranslationY(AndroidUtilities.dp(20) * inverted);
                if (!isCustomKeyboardForceDisabled()) {
                    keyboardView.setTranslationY(keyboardView.getLayoutParams().height * inverted);
                    floatingButtonContainer.setTranslationY(keyboardView.getLayoutParams().height * inverted);
                }

                introView.setTranslationY(-AndroidUtilities.dp(20) * val);
                float sc = 0.95f + 0.05f * inverted;
                introView.setScaleX(sc);
                introView.setScaleY(sc);

                transformParams.width = (int) (oldTransformWidth + (newTransformSize - oldTransformWidth) * val);
                transformParams.height = (int) (oldTransformHeight + (newTransformSize - oldTransformHeight) * val);
                transformButton.requestLayout();

                transformButton.setProgress(val);
                transformButton.setTranslationX(fromX + (toX - fromX) * val);
                transformButton.setTranslationY(fromY + (toY - fromY) * val);
            });
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);

            AnimatorSet set = new AnimatorSet();
            set.setDuration(300);
            set.playTogether(animator);
            set.start();
            return set;
        }
        return null;
    }

    private void updateColors() {
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        Context context = getParentActivity();
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButtonContainer.setBackground(drawable);

        backButtonView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        backButtonView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));

        proxyDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        proxyDrawable.setColorKey(Theme.key_windowBackgroundWhiteBlackText);
        proxyButtonView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));

        radialProgressView.setProgressColor(Theme.getColor(Theme.key_chats_actionBackground));

        floatingButtonIcon.setColor(Theme.getColor(Theme.key_chats_actionIcon));
        floatingButtonIcon.setBackgroundColor(Theme.getColor(Theme.key_chats_actionBackground));

        floatingProgressView.setProgressColor(Theme.getColor(Theme.key_chats_actionIcon));

        for (SlideView slideView : views) {
            slideView.updateColors();
        }

        keyboardView.updateColors();
        if (phoneNumberConfirmView != null) {
            phoneNumberConfirmView.updateColors();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors, Theme.key_windowBackgroundWhiteBlackText, Theme.key_windowBackgroundWhiteGrayText6,
                Theme.key_windowBackgroundWhiteHintText, Theme.key_listSelector, Theme.key_chats_actionBackground, Theme.key_chats_actionIcon,
                Theme.key_windowBackgroundWhiteInputField, Theme.key_windowBackgroundWhiteInputFieldActivated, Theme.key_windowBackgroundWhiteValueText,
                Theme.key_text_RedBold, Theme.key_windowBackgroundWhiteGrayText, Theme.key_checkbox, Theme.key_windowBackgroundWhiteBlueText4,
                Theme.key_changephoneinfo_image2, Theme.key_chats_actionPressedBackground, Theme.key_text_RedRegular, Theme.key_windowBackgroundWhiteLinkText,
                Theme.key_checkboxSquareUnchecked, Theme.key_checkboxSquareBackground, Theme.key_checkboxSquareCheck, Theme.key_dialogBackground, Theme.key_dialogTextGray2,
                Theme.key_dialogTextBlack);
    }

    private void tryResetAccount(String requestPhone, String phoneHash, String phoneCode) {
        if (radialProgressView.getTag() != null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setMessage(getString("ResetMyAccountWarningText", R.string.ResetMyAccountWarningText));
        builder.setTitle(getString("ResetMyAccountWarning", R.string.ResetMyAccountWarning));
        builder.setPositiveButton(getString("ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset), (dialogInterface, i) -> {
            needShowProgress(0);
            TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
            req.reason = "Forgot password";
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress(false);
                if (error == null) {
                    if (requestPhone == null || phoneHash == null || phoneCode == null) {
                        setPage(VIEW_PHONE_INPUT, true, null, true);
                        return;
                    }
                    Bundle params = new Bundle();
                    params.putString("phoneFormated", requestPhone);
                    params.putString("phoneHash", phoneHash);
                    params.putString("code", phoneCode);
                    setPage(VIEW_REGISTER, true, params, false);
                } else {
                    if (error.text.equals("2FA_RECENT_CONFIRM")) {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("ResetAccountCancelledAlert", R.string.ResetAccountCancelledAlert));
                    } else if (error.text.startsWith("2FA_CONFIRM_WAIT_")) {
                        Bundle params = new Bundle();
                        params.putString("phoneFormated", requestPhone);
                        params.putString("phoneHash", phoneHash);
                        params.putString("code", phoneCode);
                        params.putInt("startTime", ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                        params.putInt("waitTime", Utilities.parseInt(error.text.replace("2FA_CONFIRM_WAIT_", "")));
                        setPage(VIEW_RESET_WAIT, true, params, false);
                    } else {
                        needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error.text);
                    }
                }
            }), ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        });
        builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private final static class PhoneNumberConfirmView extends FrameLayout {
        private IConfirmDialogCallback callback;
        private ViewGroup fragmentView;
        private View fabContainer;

        private View blurredView;
        private View dimmView;
        private TransformableLoginButtonView fabTransform;
        private RadialProgressView floatingProgressView;
        private FrameLayout popupFabContainer;

        private TextView confirmMessageView;
        private TextView numberView;
        private TextView editTextView;
        private TextView confirmTextView;

        private FrameLayout popupLayout;

        private boolean dismissed;

        private PhoneNumberConfirmView(@NonNull Context context, ViewGroup fragmentView, View fabContainer, String numberText, IConfirmDialogCallback callback) {
            super(context);

            this.fragmentView = fragmentView;
            this.fabContainer = fabContainer;
            this.callback = callback;

            blurredView = new View(getContext());
            blurredView.setOnClickListener(v -> dismiss());
            addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            dimmView = new View(getContext());
            dimmView.setBackgroundColor(0x40000000);
            dimmView.setAlpha(0);
            addView(dimmView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            fabTransform = new TransformableLoginButtonView(getContext());
            fabTransform.setTransformType(TransformableLoginButtonView.TRANSFORM_ARROW_CHECK);
            fabTransform.setDrawBackground(false);

            popupFabContainer = new FrameLayout(context);
            popupFabContainer.addView(fabTransform, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            popupFabContainer.setOnClickListener(v -> callback.onFabPressed(this, fabTransform));

            floatingProgressView = new RadialProgressView(context);
            floatingProgressView.setSize(AndroidUtilities.dp(22));
            floatingProgressView.setAlpha(0.0f);
            floatingProgressView.setScaleX(0.1f);
            floatingProgressView.setScaleY(0.1f);
            popupFabContainer.addView(floatingProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            popupFabContainer.setContentDescription(getString(R.string.Done));
            addView(popupFabContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

            popupLayout = new FrameLayout(context);

            addView(popupLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 24, 0, 24, 0));

            confirmMessageView = new TextView(context);
            confirmMessageView.setText(getString(R.string.ConfirmCorrectNumber));
            confirmMessageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmMessageView.setSingleLine();
            popupLayout.addView(confirmMessageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 20, 24, 0));

            numberView = new TextView(context);
            numberView.setText(numberText);
            numberView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            numberView.setTypeface(AndroidUtilities.bold());
            numberView.setSingleLine();
            popupLayout.addView(numberView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 48, 24, 0));

            int buttonPadding = AndroidUtilities.dp(16);
            int buttonMargin = 8;

            editTextView = new TextView(context);
            editTextView.setText(getString(R.string.Edit));
            editTextView.setSingleLine();
            editTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editTextView.setBackground(Theme.getRoundRectSelectorDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_changephoneinfo_image2)));
            editTextView.setOnClickListener(v -> callback.onEditPressed(this, editTextView));
            editTextView.setTypeface(Typeface.DEFAULT_BOLD);
            editTextView.setPadding(buttonPadding, buttonPadding / 2, buttonPadding, buttonPadding / 2);
            popupLayout.addView(editTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), buttonMargin, buttonMargin, buttonMargin, buttonMargin));

            confirmTextView = new TextView(context);
            confirmTextView.setText(getString(R.string.CheckPhoneNumberYes));
            confirmTextView.setSingleLine();
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            confirmTextView.setBackground(Theme.getRoundRectSelectorDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_changephoneinfo_image2)));
            confirmTextView.setOnClickListener(v -> callback.onConfirmPressed(this, confirmTextView));
            confirmTextView.setTypeface(Typeface.DEFAULT_BOLD);
            confirmTextView.setPadding(buttonPadding, buttonPadding / 2, buttonPadding, buttonPadding / 2);
            popupLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), buttonMargin, buttonMargin, buttonMargin, buttonMargin));

            updateFabPosition();
            updateColors();
        }

        private void updateFabPosition() {
            int[] loc = new int[2];
            fragmentView.getLocationInWindow(loc);
            int fragmentX = loc[0], fragmentY = loc[1];

            fabContainer.getLocationInWindow(loc);
            popupFabContainer.setTranslationX(loc[0] - fragmentX);
            popupFabContainer.setTranslationY(loc[1] - fragmentY);
            requestLayout();
        }

        private void updateColors() {
            fabTransform.setColor(Theme.getColor(Theme.key_chats_actionIcon));
            fabTransform.setBackgroundColor(Theme.getColor(Theme.key_chats_actionBackground));
            popupLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_dialogBackground)));
            confirmMessageView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            numberView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            editTextView.setTextColor(Theme.getColor(Theme.key_changephoneinfo_image2));
            confirmTextView.setTextColor(Theme.getColor(Theme.key_changephoneinfo_image2));
            popupFabContainer.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground)));
            floatingProgressView.setProgressColor(Theme.getColor(Theme.key_chats_actionIcon));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            int height = popupLayout.getMeasuredHeight();
            int popupBottom = (int) (popupFabContainer.getTranslationY() - AndroidUtilities.dp(32));
            popupLayout.layout(popupLayout.getLeft(), popupBottom - height, popupLayout.getRight(), popupBottom);
        }

        private void show() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ObjectAnimator.ofFloat(fabContainer, View.TRANSLATION_Z, fabContainer.getTranslationZ(), 0).setDuration(150).start();
            }

            ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(250);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    fabContainer.setVisibility(GONE);

                    // TODO: Generify this code, currently it's a clone
                    float scaleFactor = 10;
                    int w = (int) (fragmentView.getMeasuredWidth() / scaleFactor);
                    int h = (int) (fragmentView.getMeasuredHeight() / scaleFactor);
                    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.scale(1.0f / scaleFactor, 1.0f / scaleFactor);
                    canvas.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    fragmentView.draw(canvas);
                    Utilities.stackBlurBitmap(bitmap, Math.max(8, Math.max(w, h) / 150));
                    blurredView.setBackground(new BitmapDrawable(getContext().getResources(), bitmap));
                    blurredView.setAlpha(0.0f);
                    blurredView.setVisibility(View.VISIBLE);

                    fragmentView.addView(PhoneNumberConfirmView.this);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (AndroidUtilities.isAccessibilityTouchExplorationEnabled()) {
                        popupFabContainer.requestFocus();
                    }
                }
            });
            anim.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                fabTransform.setProgress(val);
                blurredView.setAlpha(val);
                dimmView.setAlpha(val);

                popupLayout.setAlpha(val);
                float scale = 0.5f + val * 0.5f;
                popupLayout.setScaleX(scale);
                popupLayout.setScaleY(scale);
            });
            anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
            anim.start();
        }

        private void animateProgress(Runnable callback) {
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    callback.run();
                }
            });
            animator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();

                float scale = 0.1f + 0.9f * (1f - val);
                fabTransform.setScaleX(scale);
                fabTransform.setScaleY(scale);
                fabTransform.setAlpha(1f - val);

                scale = 0.1f + 0.9f * val;
                floatingProgressView.setScaleX(scale);
                floatingProgressView.setScaleY(scale);
                floatingProgressView.setAlpha(val);
            });
            animator.setDuration(150);
            animator.start();
        }

        private void dismiss() {
            if (dismissed) return;
            dismissed = true;

            callback.onDismiss(this);

            ValueAnimator anim = ValueAnimator.ofFloat(1, 0).setDuration(250);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(PhoneNumberConfirmView.this);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ObjectAnimator.ofFloat(fabContainer, View.TRANSLATION_Z, 0, AndroidUtilities.dp(2)).setDuration(150).start();
                    }
                    fabContainer.setVisibility(VISIBLE);
                }
            });
            anim.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                blurredView.setAlpha(val);
                dimmView.setAlpha(val);
                fabTransform.setProgress(val);
                popupLayout.setAlpha(val);

                float scale = 0.5f + val * 0.5f;
                popupLayout.setScaleX(scale);
                popupLayout.setScaleY(scale);
            });
            anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
            anim.start();
        }

        private interface IConfirmDialogCallback {
            void onFabPressed(PhoneNumberConfirmView confirmView, TransformableLoginButtonView fab);
            void onEditPressed(PhoneNumberConfirmView confirmView, TextView editTextView);
            void onConfirmPressed(PhoneNumberConfirmView confirmView, TextView confirmTextView);
            void onDismiss(PhoneNumberConfirmView confirmView);
        }
    }

    private final static class PhoneInputData {
        private CountrySelectActivity.Country country;
        private List<String> patterns;
        private String phoneNumber;
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    private int currentConnectionState;

    private void updateProxyButton(boolean animated, boolean force) {
        if (proxyDrawable == null) {
            return;
        }
        int state = getConnectionsManager().getConnectionState();
        if (currentConnectionState == state && !force) {
            return;
        }
        currentConnectionState = state;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String proxyAddress = preferences.getString("proxy_ip", "");
        final boolean proxyEnabled = preferences.getBoolean("proxy_enabled", false) && !TextUtils.isEmpty(proxyAddress);
        final boolean connected = currentConnectionState == ConnectionsManager.ConnectionStateConnected || currentConnectionState == ConnectionsManager.ConnectionStateUpdating;
        final boolean connecting = currentConnectionState == ConnectionsManager.ConnectionStateConnecting || currentConnectionState == ConnectionsManager.ConnectionStateWaitingForNetwork || currentConnectionState == ConnectionsManager.ConnectionStateConnectingToProxy;
        if (proxyEnabled) {
            proxyDrawable.setConnected(true, connected, animated);
            showProxyButton(true, animated);
        } else if (getMessagesController().blockedCountry && !SharedConfig.proxyList.isEmpty() || connecting) {
            proxyDrawable.setConnected(true, connected, animated);
            showProxyButtonDelayed();
        } else {
            showProxyButton(false, animated);
        }
    }
    
    private boolean proxyButtonVisible;
    private Runnable showProxyButtonDelayed;
    private void showProxyButtonDelayed() {
        if (proxyButtonVisible) {
            return;
        }
        if (showProxyButtonDelayed != null) {
            AndroidUtilities.cancelRunOnUIThread(showProxyButtonDelayed);
        }
        proxyButtonVisible = true;
        AndroidUtilities.runOnUIThread(showProxyButtonDelayed = () -> {
            proxyButtonVisible = false;
            showProxyButton(true, true);
        }, 5000);
    }

    private void showProxyButton(boolean show, boolean animated) {
        if (show == proxyButtonVisible) {
            return;
        }
        if (showProxyButtonDelayed != null) {
            AndroidUtilities.cancelRunOnUIThread(showProxyButtonDelayed);
            showProxyButtonDelayed = null;
        }
        proxyButtonVisible = show;
        proxyButtonView.clearAnimation();
        if (animated) {
            proxyButtonView.setVisibility(View.VISIBLE);
            proxyButtonView.animate().alpha(show ? 1 : 0).withEndAction(() -> {
                if (!show) {
                    proxyButtonView.setVisibility(View.GONE);
                }
            }).start();
        } else {
            proxyButtonView.setVisibility(show ? View.VISIBLE : View.GONE);
            proxyButtonView.setAlpha(show ? 1f : 0f);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didUpdateConnectionState) {
            updateProxyButton(true, false);
        }
    }


    public class LoginActivityPhraseView extends SlideView {

        private final @AuthType int currentType;

        private final LinearLayout fieldContainer;
        private final OutlineTextContainerView outlineField;
        private final EditTextBoldCursor codeField;
        private final FrameLayout infoContainer;
        private final TextView prevTypeTextView;
        private final TextView errorTextView;
        private final TextView infoTextView;
        private final RLottieImageView imageView;
        private final TextView titleTextView;
        private final TextView confirmTextView;
        private final TextView pasteTextView;
        private final LoadingTextView timeText;
//        private TextView cancelButton;
        private boolean pasteShown = true;
        private boolean errorShown = false;
        private boolean pasting = false, pasted = false;

        private @AuthType int nextType;
        private @AuthType int prevType;
        private String requestPhone, phoneHash, emailPhone, phone;
        private String beginning;
        private Bundle currentParams;

        private boolean isResendingCode;
        private Timer timeTimer;
        private Timer codeTimer;
        private int openTime;
        private final Object timerSync = new Object();
        private int time = 60000;
        private int codeTime = 15000;
        private double lastCurrentTime;
        private double lastCodeTime;
        private boolean ignoreOnTextChange;
        private boolean waitingForEvent;
        private boolean nextPressed;
        private String lastError = "";

        private Bundle nextCodeParams;
        private TLRPC.TL_auth_sentCode nextCodeAuth;

        public LoginActivityPhraseView(Context context, @AuthType int type) {
            super(context);
            currentType = type;
            final int a;
            if (type == AUTH_TYPE_WORD) {
                a = 0;
            } else /* if (type == AUTH_TYPE_PHRASE) */ {
                a = 1;
            }

            setOrientation(VERTICAL);

            imageView = new RLottieImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setAnimation(R.raw.bubble, 95, 95);
            boolean hideImage = AndroidUtilities.isSmallScreen() || (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y && !AndroidUtilities.isTablet());
            imageView.setVisibility(hideImage ? GONE : VISIBLE);
            addView(imageView, LayoutHelper.createLinear(95, 95, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 5));

            titleTextView = new TextView(context);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            titleTextView.setText(getString(a == 0 ? R.string.SMSWordTitle : R.string.SMSPhraseTitle));
            addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, hideImage ? 25 : 0, 8, 0));

            confirmTextView = new TextView(context);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 5, 8, 16));

            outlineField = new OutlineTextContainerView(context);
            outlineField.setText(getString(a == 0 ? R.string.SMSWord : R.string.SMSPhrase));

            codeField = new EditTextBoldCursor(context) {
                @Override
                public boolean onTextContextMenuItem(int id) {
                    switch (id) {
                        case android.R.id.paste:
                        case android.R.id.pasteAsPlainText:
                            pasting = pasted = true;
                            postDelayed(() -> pasting = false, 1000);
                            break;
                    }
                    return super.onTextContextMenuItem(id);
                }
            };
            codeField.setSingleLine();
            codeField.setLines(1);
            codeField.setCursorSize(AndroidUtilities.dp(20));
            codeField.setCursorWidth(1.5f);
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setBackground(null);
            codeField.setHint(getString(a == 0 ? R.string.SMSWordHint : R.string.SMSPhraseHint));
            codeField.addTextChangedListener(new TextWatcher() {
                private boolean ignoreTextChange;
                private int trimmedLength;
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (ignoreTextChange) return;
                    if (s == null || beginning == null) return;
                    trimmedLength = trimLeft(s.toString()).length();
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (ignoreTextChange) return;
                }
                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreTextChange) return;
                    checkPaste(true);
                    AndroidUtilities.cancelRunOnUIThread(dismissField);
                    animateError(false);
                    if (TextUtils.isEmpty(s)) {
                        pasted = false;
                    }
                    if (!beginsOk(s.toString())) {
                        onInputError(true);
                        ignoreTextChange = true;
                        boolean selectedEnd = codeField.getSelectionEnd() >= codeField.getText().length();
                        if (!pasted) {
                            codeField.setText(beginning.substring(0, Utilities.clamp(trimmedLength, beginning.length(), 0)));
                            if (selectedEnd) {
                                codeField.setSelection(codeField.getText().length());
                            }
                        }
                        ignoreTextChange = false;
                    }
                }
            });
            codeField.setEllipsizeByGradient(true);
            codeField.setInputType(InputType.TYPE_CLASS_TEXT);

            codeField.setTypeface(Typeface.DEFAULT);
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            codeField.setOnFocusChangeListener((v, hasFocus) -> outlineField.animateSelection(hasFocus ? 1f : 0f));

            pasteTextView = new TextView(context);
            pasteTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            pasteTextView.setTypeface(AndroidUtilities.bold());
            pasteTextView.setText(getString(R.string.Paste));
            pasteTextView.setPadding(dp(10), 0, dp(10), 0);
            pasteTextView.setGravity(Gravity.CENTER);
            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourceProvider);
            pasteTextView.setTextColor(textColor);
            pasteTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(6), Theme.multAlpha(textColor, .12f), Theme.multAlpha(textColor, .15f)));
            ScaleStateListAnimator.apply(pasteTextView, .1f, 1.5f);
            codeField.setPadding(dp(16), dp(16 - 2.66f), dp(16), dp(16 - 2.66f));
            pasteTextView.setOnClickListener(v -> {
                ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                CharSequence text = null;
                try {
                    text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (text != null) {
                    Editable fieldText = codeField.getText();
                    pasting = pasted = true;
                    if (fieldText != null) {
                        final int start = Math.max(0, codeField.getSelectionStart());
                        final int end = Math.max(start, codeField.getSelectionEnd());
                        fieldText.replace(start, end, text);
                    }
                    pasting = false;
                }
                checkPaste(true);
            });

            outlineField.addView(codeField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 0, 0, 0, 0));
            outlineField.attachEditText(codeField);
            outlineField.addView(pasteTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            fieldContainer = new LinearLayout(context);
            fieldContainer.setOrientation(VERTICAL);
            fieldContainer.addView(outlineField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            addView(fieldContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 16, 3, 16, 0));
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });

            infoContainer = new FrameLayout(context);
            fieldContainer.addView(infoContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            prevTypeTextView = new LoadingTextView(context);
            prevTypeTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            prevTypeTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteValueText));
            prevTypeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            prevTypeTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            prevTypeTextView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(16));
            prevTypeTextView.setOnClickListener(v -> onBackPressed(true));
            addView(prevTypeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));
            prevTypeTextView.setVisibility(View.GONE);

            errorTextView = new TextView(context);
            errorTextView.setPivotX(0);
            errorTextView.setPivotY(0);
            errorTextView.setText(getString(a == 0 ? R.string.SMSWordError : R.string.SMSPhraseError));
            errorTextView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            errorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            infoContainer.addView(errorTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 16, 8, 16, 8));
            errorTextView.setAlpha(0f);
            errorTextView.setScaleX(.8f);
            errorTextView.setScaleY(.8f);
            errorTextView.setTranslationY(-dp(4));

            infoTextView = new TextView(context);
            infoTextView.setPivotX(0);
            infoTextView.setPivotY(0);
            infoTextView.setText(getString(a == 0 ? R.string.SMSWordPasteHint : R.string.SMSPhrasePasteHint));
            infoTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            infoContainer.addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 16, 8, 16, 8));

            timeText = new LoadingTextView(context) {
                @Override
                protected boolean isResendingCode() {
                    return isResendingCode;
                }
                @Override
                protected boolean isRippleEnabled() {
                    return getVisibility() == View.VISIBLE && !(time > 0 && timeTimer != null);
                }
            };
            timeText.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            timeText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(16));
            timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            timeText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            timeText.setOnClickListener(v -> {
//                if (isRequestingFirebaseSms || isResendingCode) {
//                    return;
//                }
                if (time > 0 && timeTimer != null) {
                    return;
                }
                if (nextCodeParams != null && nextCodeAuth != null) {
                    fillNextCodeParams(nextCodeParams, nextCodeAuth);
                    return;
                }

                if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_MISSED_CALL || nextType == AUTH_TYPE_FRAGMENT_SMS) {
                    isResendingCode = true;
                    timeText.invalidate();
                    timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
//                    timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
                    timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                        timeText.setText(getString(R.string.Calling));
                    } else {
                        timeText.setText(getString(R.string.SendingSms));
                    }
                    Bundle params = new Bundle();
                    params.putString("phone", phone);
                    params.putString("ephone", emailPhone);
                    params.putString("phoneFormated", requestPhone);
                    params.putInt("prevType", currentType);

//                    createCodeTimer();
                    TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
                    req.phone_number = requestPhone;
                    req.phone_code_hash = phoneHash;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        isResendingCode = false;
                        timeText.invalidate();
                        if (response != null) {
                            nextCodeParams = params;
                            nextCodeAuth = (TLRPC.TL_auth_sentCode) response;
                            fillNextCodeParams(nextCodeParams, nextCodeAuth);
                        } else if (error != null && error.text != null) {
                            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.InvalidPhoneNumber));
                            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.InvalidCode));
                            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                onBackPressed(true);
                                setPage(VIEW_PHONE_INPUT, true, null, true);
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.CodeExpired));
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.FloodWait));
                            } else if (error.code != -1000) {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.ErrorOccurred) + "\n" + error.text);
                            }
                            lastError = error.text;
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else if (nextType == AUTH_TYPE_FLASH_CALL) {
                    AndroidUtilities.setWaitingForSms(false);
//                    NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                    waitingForEvent = false;
//                    destroyCodeTimer();
                    resendCode();
                }
            });

//            cancelButton = new TextView(context);
//            cancelButton.setGravity(Gravity.CENTER | Gravity.LEFT);
//            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
//            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
//            cancelButton.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
//            cancelButton.setText(getString(R.string.YourEmailSkip));

            FrameLayout bottomContainer = new FrameLayout(context);
            bottomContainer.addView(timeText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, (Build.VERSION.SDK_INT >= 21 ? 56 : 60), Gravity.BOTTOM, 6, 0, 60, 28));
            addView(bottomContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
            VerticalPositionAutoAnimator.attach(timeText);

//            cancelButton.setOnClickListener(view -> {
//
//            });
        }

        private final Runnable checkPasteRunnable = () -> checkPaste(true);

        private void checkPaste(boolean animated) {
            AndroidUtilities.cancelRunOnUIThread(checkPasteRunnable);

            boolean show;
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            show = TextUtils.isEmpty(codeField.getText()) && clipboardManager != null && clipboardManager.hasPrimaryClip();

            if (pasteShown != show) {
                pasteShown = show;
                if (animated) {
                    pasteTextView.animate()
                        .alpha(show ? 1f : 0f)
                        .scaleX(show ? 1f : .7f)
                        .scaleY(show ? 1f : .7f)
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .setDuration(300)
                        .start();

                    infoTextView.animate()
                        .scaleX(pasteShown && !errorShown ? 1f : .9f)
                        .scaleY(pasteShown && !errorShown ? 1f : .9f)
                        .alpha(pasteShown && !errorShown ? 1f : 0f)
                        .translationY(pasteShown && !errorShown ? 0 : dp(errorShown ? 5 : -5))
                        .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                        .setDuration(300)
                        .start();
                } else {
                    pasteTextView.setAlpha(show ? 1f : 0f);
                    pasteTextView.setScaleX(show ? 1f : .7f);
                    pasteTextView.setScaleY(show ? 1f : .7f);

                    infoTextView.setScaleX(pasteShown && !errorShown ? 1f : .9f);
                    infoTextView.setScaleY(pasteShown && !errorShown ? 1f : .9f);
                    infoTextView.setAlpha(pasteShown && !errorShown ? 1f : 0f);
                    infoTextView.setTranslationY(pasteShown && !errorShown ? 0 : dp(errorShown ? 5 : -5));
                }
            }

            AndroidUtilities.runOnUIThread(checkPasteRunnable, 1000 * 5);
        }

        @Override
        public void updateColors() {
            titleTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            confirmTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText6));
            codeField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
            codeField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            outlineField.updateColor();
//            cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public String getHeaderName() {
            return getString("NewPassword", R.string.NewPassword);
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                if (nextCodeParams != null && nextCodeAuth != null) {
                    timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
                    int resId;
                    if (nextType == AUTH_TYPE_PHRASE) {
                        resId = R.string.ReturnEnteringPhrase;
                    } else if (nextType == AUTH_TYPE_WORD) {
                        resId = R.string.ReturnEnteringWord;
                    } else {
                        resId = R.string.ReturnEnteringSMS;
                    }
                    timeText.setText(AndroidUtilities.replaceArrows(getString(resId), true, dp(1), dp(1)));
                }
                return;
            }
            codeField.setText("");
            currentParams = params;
            beginning = null;
            nextType = params.getInt("nextType");
            prevType = params.getInt("prevType", 0);
            emailPhone = params.getString("ephone");
            if (currentParams.containsKey("beginning")) {
                beginning = currentParams.getString("beginning");
            }
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phone = currentParams.getString("phone");
            time = params.getInt("timeout");

            if (prevType == AUTH_TYPE_PHRASE) {
                prevTypeTextView.setVisibility(View.VISIBLE);
                prevTypeTextView.setText(AndroidUtilities.replaceArrows(getString(R.string.BackEnteringPhrase), true, dp(-1), dp(1)));
            } else if (prevType == AUTH_TYPE_WORD) {
                prevTypeTextView.setVisibility(View.VISIBLE);
                prevTypeTextView.setText(AndroidUtilities.replaceArrows(getString(R.string.BackEnteringWord), true, dp(-1), dp(1)));
            } else if (prevType == AUTH_TYPE_MESSAGE || prevType == AUTH_TYPE_SMS || prevType == AUTH_TYPE_CALL || prevType == AUTH_TYPE_FLASH_CALL || prevType == AUTH_TYPE_FRAGMENT_SMS) {
                prevTypeTextView.setVisibility(View.VISIBLE);
                prevTypeTextView.setText(AndroidUtilities.replaceArrows(getString(R.string.BackEnteringCode), true, dp(-1), dp(1)));
            } else {
                prevTypeTextView.setVisibility(View.GONE);
            }

            nextCodeParams = null;
            nextCodeAuth = null;
            nextPressed = false;
            isResendingCode = false;
            isRequestingFirebaseSms = false;
            timeText.invalidate();

            final int a = currentType == AUTH_TYPE_WORD ? 0 : 1;
            final String formattedPhone = "+" + PhoneFormat.getInstance().format(PhoneFormat.stripExceptNumbers(phone));
            if (beginning == null) {
                confirmTextView.setText(AndroidUtilities.replaceTags(formatString(a == 0 ? R.string.SMSWordText : R.string.SMSPhraseText, formattedPhone)));
            } else {
                confirmTextView.setText(AndroidUtilities.replaceTags(formatString(a == 0 ? R.string.SMSWordBeginningText : R.string.SMSPhraseBeginningText, formattedPhone, beginning)));
            }

            showKeyboard(codeField);
            codeField.requestFocus();

            if (imageView.getAnimatedDrawable() != null) {
                imageView.getAnimatedDrawable().setCurrentFrame(0, false);
            }
            AndroidUtilities.runOnUIThread(imageView::playAnimation, 500);

            checkPaste(false);
            animateError(false);

            lastCurrentTime = System.currentTimeMillis();
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            if (nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL) {
                createTimer();
            } else {
                timeText.setVisibility(GONE);
            }
        }

        private void animateError(boolean show) {
            errorShown = show;
            float value = show ? 1f : 0f;
            outlineField.animateError(value);
            errorTextView.animate().scaleX(.9f + .1f * value).scaleY(.9f + .1f * value).alpha(value).translationY((1f - value) * dp(-5)).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(290).start();

            boolean showPaste = pasteShown && !errorShown;
            value = showPaste ? 1f : 0f;
            infoTextView.animate().scaleX(.9f + .1f * value).scaleY(.9f + .1f * value).alpha(value).translationY((1f - value) * dp(errorShown ? 5 : -5)).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(290).start();
        }

        private final Runnable dismissField = () -> animateError(false);

        private float shiftDp = -3;
        private void onInputError(boolean asBeginning) {
            if (getParentActivity() == null) {
                return;
            }
            try {
                codeField.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            final int a = currentType == AUTH_TYPE_WORD ? 0 : 1;
            if (asBeginning) {
                errorTextView.setText(getString(a == 0 ? R.string.SMSWordBeginningError : R.string.SMSPhraseBeginningError));
            } else if (TextUtils.isEmpty(codeField.getText())) {
                errorTextView.setText("");
            } else {
                errorTextView.setText(getString(a == 0 ? R.string.SMSWordError : R.string.SMSPhraseError));
            }
            if (!errorShown && !pasted) {
                AndroidUtilities.shakeViewSpring(codeField, shiftDp);
                AndroidUtilities.shakeViewSpring(errorTextView, shiftDp);
            }
            AndroidUtilities.cancelRunOnUIThread(dismissField);
            animateError(true);
            AndroidUtilities.runOnUIThread(dismissField, 10_000);
            shiftDp = -shiftDp;
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }

            code = codeField.getText().toString();
            if (code.length() == 0) {
                onInputError(false);
                return;
            }

            if (!beginsOk(code)) {
                onInputError(true);
                return;
            }

            nextPressed = true;

            TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
            req.phone_number = requestPhone;
            req.phone_code = code;
            req.phone_code_hash = phoneHash;
            req.flags |= 1;
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress(false, true);

                boolean ok = false;

                if (error == null) {
                    nextPressed = false;
                    ok = true;
                    showDoneButton(false, true);
                    destroyTimer();
//                    destroyCodeTimer();
                    if (response instanceof TLRPC.TL_auth_authorizationSignUpRequired) {
                        TLRPC.TL_auth_authorizationSignUpRequired authorization = (TLRPC.TL_auth_authorizationSignUpRequired) response;
                        if (authorization.terms_of_service != null) {
                            currentTermsOfService = authorization.terms_of_service;
                        }
                        Bundle params = new Bundle();
                        params.putString("phoneFormated", requestPhone);
                        params.putString("phoneHash", phoneHash);
                        params.putString("code", req.phone_code);

                        setPage(VIEW_REGISTER, true, params, false);
                    } else {
                        onAuthSuccess((TLRPC.TL_auth_authorization) response);
                    }
                } else {
                    lastError = error.text;
                    if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                        ok = true;
                        TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            nextPressed = false;
                            showDoneButton(false, true);
                            if (error1 == null) {
                                TLRPC.account_Password password = (TLRPC.account_Password) response1;
                                if (!TwoStepVerificationActivity.canHandleCurrentPassword(password, true)) {
                                    AlertsCreator.showUpdateAppAlert(getParentActivity(), getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                    return;
                                }
                                Bundle bundle = new Bundle();
                                SerializedData data = new SerializedData(password.getObjectSize());
                                password.serializeToStream(data);
                                bundle.putString("password", Utilities.bytesToHex(data.toByteArray()));
                                bundle.putString("phoneFormated", requestPhone);
                                bundle.putString("phoneHash", phoneHash);
                                bundle.putString("code", req.phone_code);

                                setPage(VIEW_PASSWORD, true, bundle, false);
                            } else {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), error1.text);
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        destroyTimer();
//                        destroyCodeTimer();
                    } else {
                        nextPressed = false;
//                        showDoneButton(true, true);
//                        if (currentType == AUTH_TYPE_FLASH_CALL && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS) || currentType == AUTH_TYPE_SMS && (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL) || currentType == AUTH_TYPE_CALL && nextType == AUTH_TYPE_SMS) {
//                            createTimer();
//                        }
                        if (currentType != AUTH_TYPE_FLASH_CALL) {
                            boolean isWrongCode = false;
                            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                onInputError(false);
                                isWrongCode = true;
                            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                onBackPressed(true);
                                setPage(VIEW_PHONE_INPUT, true, null, true);
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("CodeExpired", R.string.CodeExpired));
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("FloodWait", R.string.FloodWait));
                            } else {
                                needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                            }

                            if (isWrongCode) {
                                codeField.post(() -> {
                                    codeField.requestFocus();
                                    if (beginning != null && beginning.length() > 1) {
                                        String text = codeField.getText().toString();
                                        int select = trimLeftLen(text) + beginning.length();
                                        boolean nextIsSpace = select >= 0 && select < text.length() && text.charAt(select) == ' ';
                                        codeField.setSelection(Utilities.clamp(select + (nextIsSpace ? 1 : 0), text.length(), 0), codeField.getText().length());
                                    } else {
                                        codeField.setSelection(0, codeField.getText().length());
                                    }
                                });
                            } else {
                                codeField.setText("");
                                codeField.requestFocus();
                            }
                        }
                    }
                }
                if (ok) {
                    if (currentType == AUTH_TYPE_FLASH_CALL) {
                        AndroidUtilities.endIncomingCall();
                        AndroidUtilities.setWaitingForCall(false);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            needShowProgress(reqId, true);
            showDoneButton(true, true);
        }


        private void resendCode() {
            if (nextPressed || isResendingCode || isRequestingFirebaseSms) {
                return;
            }

            isResendingCode = true;
            timeText.invalidate();
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
//            problemText.invalidate();

            final Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);

            nextPressed = true;

            TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                } else {
                    if (error.text != null) {
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            onBackPressed(true);
                            setPage(VIEW_PHONE_INPUT, true, null, true);
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.CodeExpired));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.FloodWait));
                        } else if (error.code != -1000) {
                            needShowAlert(getString(R.string.RestorePasswordNoEmailTitle), getString(R.string.ErrorOccurred) + "\n" + error.text);
                        }
                    }
                }
                needHideProgress(false);
            }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            needShowProgress(reqId);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            needHideProgress(true);
            if (prevType != 0) {
                setPage(prevType, true, null, true);
                return false;
            }
            currentParams = null;
            nextPressed = false;
            return true;
        }

        @Override
        public void onResume() {
            super.onResume();
            checkPaste(true);
        }

        @Override
        public void onShow() {
            super.onShow();
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                    AndroidUtilities.showKeyboard(codeField);
                }
            }, SHOW_DELAY);
        }

        @Override
        public void onHide() {
            super.onHide();
            AndroidUtilities.cancelRunOnUIThread(checkPasteRunnable);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            if (currentParams != null) {
                bundle.putBundle("recoveryview_word" + currentType, currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_word" + currentType);
            if (currentParams != null) {
                setParams(currentParams, true);
            }
        }

//        private void setProblemTextVisible(boolean visible) {
//            if (problemText == null) {
//                return;
//            }
//            float newAlpha = visible ? 1f : 0f;
//            if (problemText.getAlpha() != newAlpha) {
//                problemText.animate().cancel();
//                problemText.animate().alpha(newAlpha).setDuration(150).start();
//            }
//        }
//
//        private void createCodeTimer() {
//            if (codeTimer != null) {
//                return;
//            }
//            codeTime = 15000;
//            if (time > codeTime) {
//                codeTime = time;
//            }
//            codeTimer = new Timer();
//            lastCodeTime = System.currentTimeMillis();
//            codeTimer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    AndroidUtilities.runOnUIThread(() -> {
//                        double currentTime = System.currentTimeMillis();
//                        double diff = currentTime - lastCodeTime;
//                        lastCodeTime = currentTime;
//                        codeTime -= diff;
//                        if (codeTime <= 1000) {
//                            setProblemTextVisible(true);
//                            timeText.setVisibility(GONE);
//                            if (problemText != null) {
//                                problemText.setVisibility(VISIBLE);
//                            }
//                            destroyCodeTimer();
//                        }
//                    });
//                }
//            }, 0, 1000);
//        }

//
//        private void destroyCodeTimer() {
//            try {
//                synchronized (timerSync) {
//                    if (codeTimer != null) {
//                        codeTimer.cancel();
//                        codeTimer = null;
//                    }
//                }
//            } catch (Exception e) {
//                FileLog.e(e);
//            }
//        }

        private void createTimer() {
            if (timeTimer != null) {
                return;
            }
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timeText.setTag(R.id.color_key_tag, Theme.key_windowBackgroundWhiteGrayText);
//            if (progressView != null) {
//                progressView.resetProgressAnimation();
//            }
            timeTimer = new Timer();
            timeTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (timeTimer == null) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCurrentTime;
                        lastCurrentTime = currentTime;
                        time -= diff;
                        if (time >= 1000) {
                            int minutes = time / 1000 / 60;
                            int seconds = time / 1000 - minutes * 60;
                            timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                            if (nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_FLASH_CALL || nextType == AUTH_TYPE_MISSED_CALL) {
                                timeText.setText(LocaleController.formatString(R.string.CallAvailableIn2, minutes, seconds));
                            } else if (nextType == AUTH_TYPE_SMS) {
                                timeText.setText(LocaleController.formatString(R.string.SmsAvailableIn2, minutes, seconds));
                            }
//                            if (progressView != null && !progressView.isProgressAnimationRunning()) {
//                                progressView.startProgressAnimation(time - 1000L);
//                            }
                        } else {
                            destroyTimer();
                            if (nextType == AUTH_TYPE_FLASH_CALL || nextType == AUTH_TYPE_CALL || nextType == AUTH_TYPE_SMS || nextType == AUTH_TYPE_MISSED_CALL) {
                                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                                if (nextType == AUTH_TYPE_CALL) {
                                    timeText.setText(getString(R.string.RequestCallButton));
                                } else if (nextType == AUTH_TYPE_FRAGMENT_SMS) {
                                    timeText.setText(getString(R.string.DidNotGetTheCodeFragment));
                                } else if (nextType == AUTH_TYPE_MISSED_CALL || nextType == AUTH_TYPE_FLASH_CALL) {
                                    timeText.setText(getString(R.string.RequestMissedCall));
                                } else {
                                    timeText.setText(replaceArrows(getString(R.string.RequestAnotherSMS), true, 0, 0));
                                }
                                timeText.setTextColor(Theme.getColor(Theme.key_chats_actionBackground));
                                timeText.setTag(R.id.color_key_tag, Theme.key_chats_actionBackground);
                            }
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyTimer() {
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timeText.setTag(R.id.color_key_tag, Theme.key_windowBackgroundWhiteGrayText);
            try {
                synchronized (timerSync) {
                    if (timeTimer != null) {
                        timeTimer.cancel();
                        timeTimer = null;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private boolean beginsOk(String text) {
            if (beginning == null) {
                return true;
            }
            String lt = trimLeft(text).toLowerCase();
            String lb = beginning.toLowerCase();
            int len = Math.min(lt.length(), lb.length());
            if (len <= 0) return true;
            return TextUtils.equals(lt.substring(0, len), lb.substring(0, len));
        }

        private int trimLeftLen(String str) {
            int len = str.length();
            int st = 0;
            while ((st < len) && (str.charAt(st) <= ' ')) {
                st++;
            }
            return st;
        }

        private String trimLeft(String str) {
            int len = str.length();
            int st = 0;

            while ((st < len) && (str.charAt(st) <= ' ')) {
                st++;
            }
//                    while ((st < len) && (str.charAt(len - 1) <= ' ')) {
//                        len--;
//                    }
            return ((st > 0) || (len < str.length())) ? str.substring(st, len) : str;
        }
    }

    @Override
    public void clearViews() {
        if (fragmentView != null) {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                try {
                    onRemoveFromParent();
                    parent.removeViewInLayout(fragmentView);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (pendingSwitchingAccount) {
                cachedFragmentView = fragmentView;
            }
            fragmentView = null;
        }
        if (actionBar != null && !pendingSwitchingAccount) {
            ViewGroup parent = (ViewGroup) actionBar.getParent();
            if (parent != null) {
                try {
                    parent.removeViewInLayout(actionBar);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            actionBar = null;
        }
        clearSheets();
        parentLayout = null;
    }
}
