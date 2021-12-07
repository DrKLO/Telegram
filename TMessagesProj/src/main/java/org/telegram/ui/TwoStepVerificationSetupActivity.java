/*
 * This is the source code of Telegram for Android v. 3.x.x
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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;

public class TwoStepVerificationSetupActivity extends BaseFragment {

    private RLottieImageView imageView;
    private TextView buttonTextView;
    private TextView titleTextView;
    private TextView descriptionText;
    private TextView descriptionText2;
    private TextView descriptionText3;
    private TextView topButton;
    private EditTextBoldCursor passwordEditText;
    private ScrollView scrollView;
    private View actionBarBackground;
    private ImageView showPasswordButton;
    private int otherwiseReloginDays = -1;

    private AnimatorSet buttonAnimation;

    private ArrayList<BaseFragment> fragmentsToClose = new ArrayList<>();

    private AnimatorSet actionBarAnimator;

    private AlertDialog progressDialog;

    private boolean ignoreTextChange;

    private boolean doneAfterPasswordLoad;

    private int currentType;
    private int emailCodeLength = 6;
    private String firstPassword;
    private String hint;
    private String email;
    private boolean paused;
    private boolean waitingForEmail;
    private TLRPC.TL_account_password currentPassword;
    private byte[] currentPasswordHash = new byte[0];
    private long currentSecretId;
    private byte[] currentSecret;
    private boolean closeAfterSet;
    private boolean emailOnly;
    private String emailCode;

    private RLottieDrawable[] animationDrawables;
    private Runnable setAnimationRunnable;

    private Runnable finishCallback = () -> {
        if (passwordEditText == null) {
            return;
        }
        if (passwordEditText.length() != 0) {
            animationDrawables[2].setCustomEndFrame(49);
            animationDrawables[2].setProgress(0.0f, false);
            imageView.playAnimation();
        } else {
            setRandomMonkeyIdleAnimation(true);
        }
    };

    public static final int TYPE_ENTER_FIRST = 0;
    public static final int TYPE_ENTER_SECOND = 1;
    public static final int TYPE_ENTER_HINT = 2;
    public static final int TYPE_ENTER_EMAIL = 3;
    public static final int TYPE_EMAIL_RECOVERY = 4;
    public static final int TYPE_EMAIL_CONFIRM = 5;
    public static final int TYPE_INTRO = 6;
    public static final int TYPE_PASSWORD_SET = 7;
    public static final int TYPE_VERIFY = 8;
    public static final int TYPE_VERIFY_OK = 9;

    private static final int item_abort = 1;
    private static final int item_resend = 2;

    public TwoStepVerificationSetupActivity(int type, TLRPC.TL_account_password password) {
        super();
        currentType = type;
        currentPassword = password;
        if (currentPassword == null && (currentType == TYPE_INTRO || currentType == TYPE_VERIFY)) {
            loadPasswordInfo();
        } else {
            waitingForEmail = !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern);
        }
    }

    public TwoStepVerificationSetupActivity(int account, int type, TLRPC.TL_account_password password) {
        super();
        currentAccount = account;
        currentType = type;
        currentPassword = password;
        waitingForEmail = !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern);
        if (currentPassword == null && (currentType == TYPE_INTRO || currentType == TYPE_VERIFY)) {
            loadPasswordInfo();
        }
    }

    public void setCurrentPasswordParams(byte[] passwordHash, long secretId, byte[] secret, boolean email) {
        currentPasswordHash = passwordHash;
        currentSecret = secret;
        currentSecretId = secretId;
        emailOnly = email;
    }

    public void setCurrentEmailCode(String code) {
        emailCode = code;
    }

    public void addFragmentToClose(BaseFragment fragment) {
        fragmentsToClose.add(fragment);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        doneAfterPasswordLoad = false;
        if (setAnimationRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(setAnimationRunnable);
            setAnimationRunnable = null;
        }
        if (animationDrawables != null) {
            for (int a = 0; a < animationDrawables.length; a++) {
                animationDrawables[a].recycle();
            }
            animationDrawables = null;
        }
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            progressDialog = null;
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundDrawable(null);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        if (!AndroidUtilities.isTablet()) {
            actionBar.showActionModeTop();
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (otherwiseReloginDays >= 0 && parentLayout.fragmentsStack.size() == 1) {
                        showSetForcePasswordAlert();
                    } else {
                        finishFragment();
                    }
                } else if (id == item_resend) {
                    TLRPC.TL_account_resendPasswordEmail req = new TLRPC.TL_account_resendPasswordEmail();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                    });
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("ResendCodeInfo", R.string.ResendCodeInfo));
                    builder.setTitle(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    showDialog(builder.create());
                } else if (id == item_abort) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    String text;
                    if (currentPassword != null && currentPassword.has_password) {
                        text = LocaleController.getString("CancelEmailQuestion", R.string.CancelEmailQuestion);
                    } else {
                        text = LocaleController.getString("CancelPasswordQuestion", R.string.CancelPasswordQuestion);
                    }
                    String title = LocaleController.getString("CancelEmailQuestionTitle", R.string.CancelEmailQuestionTitle);
                    String buttonText = LocaleController.getString("Abort", R.string.Abort);
                    builder.setMessage(text);
                    builder.setTitle(title);
                    builder.setPositiveButton(buttonText, (dialogInterface, i) -> setNewPassword(true));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder.create();
                    showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
                }
            }
        });

        if (currentType == TYPE_EMAIL_CONFIRM) {
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
            item.addSubItem(item_resend, LocaleController.getString("ResendCode", R.string.ResendCode));
            item.addSubItem(item_abort, LocaleController.getString("AbortPasswordMenu", R.string.AbortPasswordMenu));
        }

        topButton = new TextView(context);
        topButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
        topButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        topButton.setGravity(Gravity.CENTER_VERTICAL);
        topButton.setVisibility(View.GONE);
        actionBar.addView(topButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT, 0, 0, 22, 0));
        topButton.setOnClickListener(v -> {
            if (currentType == TYPE_ENTER_FIRST) {
                needShowProgress();
                TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
                req.code = emailCode;
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    needHideProgress();
                    if (error == null) {
                        getMessagesController().removeSuggestion(0, "VALIDATE_PASSWORD");
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                            for (int a = 0, N = fragmentsToClose.size(); a < N; a++) {
                                fragmentsToClose.get(a).removeSelfFromStack();
                            }
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.twoStepPasswordChanged);
                            finishFragment();
                        });
                        builder.setMessage(LocaleController.getString("PasswordReset", R.string.PasswordReset));
                        builder.setTitle(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle));
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
                            showAlertWithText(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            showAlertWithText(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle), error.text);
                        }
                    }
                }));
            } else if (currentType == TYPE_ENTER_EMAIL) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("YourEmailSkipWarningText", R.string.YourEmailSkipWarningText));
                builder.setTitle(LocaleController.getString("YourEmailSkipWarning", R.string.YourEmailSkipWarning));
                builder.setPositiveButton(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip), (dialogInterface, i) -> {
                    email = "";
                    setNewPassword(false);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            } else if (currentType == TYPE_ENTER_HINT) {
                onHintDone();
            }
        });

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);

        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        descriptionText.setVisibility(View.GONE);
        descriptionText.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);

        descriptionText2 = new TextView(context);
        descriptionText2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText2.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionText2.setLineSpacing(AndroidUtilities.dp(2), 1);
        descriptionText2.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        descriptionText2.setVisibility(View.GONE);
        descriptionText2.setOnClickListener(v -> {
            if (currentType == TYPE_VERIFY) {
                TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                fragment.setForgotPasswordOnShow();
                fragment.setPassword(currentPassword);
                fragment.setBlockingAlert(otherwiseReloginDays);
                presentFragment(fragment, true);
            }
        });

        buttonTextView = new TextView(context);
        buttonTextView.setMinWidth(AndroidUtilities.dp(220));
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        buttonTextView.setOnClickListener(v -> {
            if (getParentActivity() == null) {
                return;
            }
            switch (currentType) {
                case TYPE_INTRO: {
                    if (currentPassword == null) {
                        needShowProgress();
                        doneAfterPasswordLoad = true;
                        return;
                    }
                    TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TYPE_ENTER_FIRST, currentPassword);
                    fragment.closeAfterSet = closeAfterSet;
                    fragment.setBlockingAlert(otherwiseReloginDays);
                    presentFragment(fragment, true);
                    break;
                }
                case TYPE_PASSWORD_SET: {
                    if (closeAfterSet) {
                        finishFragment();
                    } else {
                        TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                        fragment.setCurrentPasswordParams(currentPassword, currentPasswordHash, currentSecretId, currentSecret);
                        fragment.setBlockingAlert(otherwiseReloginDays);
                        presentFragment(fragment, true);
                    }
                    break;
                }
                case TYPE_VERIFY_OK: {
                    finishFragment();
                    break;
                }
                case TYPE_VERIFY: {
                    if (currentPassword == null) {
                        needShowProgress();
                        doneAfterPasswordLoad = true;
                        return;
                    }
                    String oldPassword = passwordEditText.getText().toString();
                    if (oldPassword.length() == 0) {
                        onFieldError(passwordEditText, false);
                        return;
                    }
                    final byte[] oldPasswordBytes = AndroidUtilities.getStringBytes(oldPassword);

                    needShowProgress();
                    Utilities.globalQueue.postRunnable(() -> {
                        final TLRPC.TL_account_getPasswordSettings req = new TLRPC.TL_account_getPasswordSettings();
                        final byte[] x_bytes;
                        if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                            x_bytes = SRPHelper.getX(oldPasswordBytes, algo);
                        } else {
                            x_bytes = null;
                        }

                        RequestDelegate requestDelegate = (response, error) -> {
                            if (error == null) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    needHideProgress();
                                    currentPasswordHash = x_bytes;
                                    getMessagesController().removeSuggestion(0, "VALIDATE_PASSWORD");
                                    TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(TYPE_VERIFY_OK, currentPassword);
                                    fragment.setBlockingAlert(otherwiseReloginDays);
                                    presentFragment(fragment, true);
                                });
                            } else {
                                AndroidUtilities.runOnUIThread(() -> {
                                    if ("SRP_ID_INVALID".equals(error.text)) {
                                        TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                                        ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                            if (error2 == null) {
                                                currentPassword = (TLRPC.TL_account_password) response2;
                                                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                                                buttonTextView.callOnClick();
                                            }
                                        }), ConnectionsManager.RequestFlagWithoutLogin);
                                        return;
                                    }
                                    needHideProgress();
                                    if ("PASSWORD_HASH_INVALID".equals(error.text)) {
                                        descriptionText.setText(LocaleController.getString("CheckPasswordWrong", R.string.CheckPasswordWrong));
                                        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                                        onFieldError(passwordEditText, true);
                                        showDoneButton(false);
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        int time = Utilities.parseInt(error.text);
                                        String timeString;
                                        if (time < 60) {
                                            timeString = LocaleController.formatPluralString("Seconds", time);
                                        } else {
                                            timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                        }
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                    } else {
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                    }
                                });
                            }
                        };

                        if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
                            req.password = SRPHelper.startCheck(x_bytes, currentPassword.srp_id, currentPassword.srp_B, algo);
                            if (req.password == null) {
                                TLRPC.TL_error error = new TLRPC.TL_error();
                                error.text = "ALGO_INVALID";
                                requestDelegate.run(null, error);
                                return;
                            }
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                        } else {
                            TLRPC.TL_error error = new TLRPC.TL_error();
                            error.text = "PASSWORD_HASH_INVALID";
                            requestDelegate.run(null, error);
                        }
                    });
                    break;
                }
                case TYPE_ENTER_FIRST: {
                    if (passwordEditText.length() == 0) {
                        onFieldError(passwordEditText, false);
                        return;
                    }
                    TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TYPE_ENTER_SECOND, currentPassword);
                    fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, emailOnly);
                    fragment.setCurrentEmailCode(emailCode);
                    fragment.firstPassword = passwordEditText.getText().toString();
                    fragment.fragmentsToClose.addAll(fragmentsToClose);
                    fragment.fragmentsToClose.add(this);
                    fragment.closeAfterSet = closeAfterSet;
                    fragment.setBlockingAlert(otherwiseReloginDays);
                    presentFragment(fragment);
                    break;
                }
                case TYPE_ENTER_SECOND: {
                    if (!firstPassword.equals(passwordEditText.getText().toString())) {
                        try {
                            Toast.makeText(getParentActivity(), LocaleController.getString("PasswordDoNotMatch", R.string.PasswordDoNotMatch), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        onFieldError(passwordEditText, false);
                        return;
                    }
                    TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TYPE_ENTER_HINT, currentPassword);
                    fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, emailOnly);
                    fragment.setCurrentEmailCode(emailCode);
                    fragment.firstPassword = firstPassword;
                    fragment.fragmentsToClose.addAll(fragmentsToClose);
                    fragment.fragmentsToClose.add(this);
                    fragment.closeAfterSet = closeAfterSet;
                    fragment.setBlockingAlert(otherwiseReloginDays);
                    presentFragment(fragment);
                    break;
                }
                case TYPE_ENTER_HINT: {
                    String hint = passwordEditText.getText().toString();
                    if (hint.toLowerCase().equals(firstPassword.toLowerCase())) {
                        try {
                            Toast.makeText(getParentActivity(), LocaleController.getString("PasswordAsHintError", R.string.PasswordAsHintError), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        onFieldError(passwordEditText, false);
                        return;
                    }
                    onHintDone();
                    break;
                }
                case TYPE_ENTER_EMAIL: {
                    email = passwordEditText.getText().toString();
                    if (!isValidEmail(email)) {
                        onFieldError(passwordEditText, false);
                        return;
                    }
                    setNewPassword(false);
                    break;
                }
                case TYPE_EMAIL_RECOVERY: {
                    String code = passwordEditText.getText().toString();
                    if (code.length() == 0) {
                        onFieldError(passwordEditText, false);
                        return;
                    }
                    TLRPC.TL_auth_checkRecoveryPassword req = new TLRPC.TL_auth_checkRecoveryPassword();
                    req.code = code;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_boolTrue) {
                            TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TYPE_ENTER_FIRST, currentPassword);
                            fragment.fragmentsToClose.addAll(fragmentsToClose);
                            fragment.addFragmentToClose(TwoStepVerificationSetupActivity.this);
                            fragment.setCurrentEmailCode(code);
                            fragment.setBlockingAlert(otherwiseReloginDays);
                            presentFragment(fragment, true);
                        } else {
                            if (error == null || error.text.startsWith("CODE_INVALID")) {
                                onFieldError(passwordEditText, true);
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                                int time = Utilities.parseInt(error.text);
                                String timeString;
                                if (time < 60) {
                                    timeString = LocaleController.formatPluralString("Seconds", time);
                                } else {
                                    timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                }
                                showAlertWithText(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                            } else {
                                showAlertWithText(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle), error.text);
                            }
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    break;
                }
                case TYPE_EMAIL_CONFIRM: {
                    if (passwordEditText.length() == 0) {
                        onFieldError(passwordEditText, false);
                        return;
                    }
                    TLRPC.TL_account_confirmPasswordEmail req = new TLRPC.TL_account_confirmPasswordEmail();
                    req.code = passwordEditText.getText().toString();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        needHideProgress();
                        if (error == null) {
                            if (getParentActivity() == null) {
                                return;
                            }
                            if (currentPassword.has_password) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                                    for (int a = 0, N = fragmentsToClose.size(); a < N; a++) {
                                        fragmentsToClose.get(a).removeSelfFromStack();
                                    }
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.twoStepPasswordChanged, currentPasswordHash, currentPassword.new_algo, currentPassword.new_secure_algo, currentPassword.secure_random, email, hint, null, firstPassword);
                                    TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                                    currentPassword.has_password = true;
                                    currentPassword.has_recovery = true;
                                    currentPassword.email_unconfirmed_pattern = "";
                                    fragment.setCurrentPasswordParams(currentPassword, currentPasswordHash, currentSecretId, currentSecret);
                                    fragment.setBlockingAlert(otherwiseReloginDays);
                                    presentFragment(fragment, true);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                                });
                                if (currentPassword.has_recovery) {
                                    builder.setMessage(LocaleController.getString("YourEmailSuccessChangedText", R.string.YourEmailSuccessChangedText));
                                } else {
                                    builder.setMessage(LocaleController.getString("YourEmailSuccessText", R.string.YourEmailSuccessText));
                                }
                                builder.setTitle(LocaleController.getString("YourPasswordSuccess", R.string.YourPasswordSuccess));
                                Dialog dialog = showDialog(builder.create());
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                            } else {
                                for (int a = 0, N = fragmentsToClose.size(); a < N; a++) {
                                    fragmentsToClose.get(a).removeSelfFromStack();
                                }
                                currentPassword.has_password = true;
                                currentPassword.has_recovery = true;
                                currentPassword.email_unconfirmed_pattern = "";
                                TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(TYPE_PASSWORD_SET, currentPassword);
                                fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, emailOnly);
                                fragment.fragmentsToClose.addAll(fragmentsToClose);
                                fragment.closeAfterSet = closeAfterSet;
                                fragment.setBlockingAlert(otherwiseReloginDays);
                                presentFragment(fragment, true);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.twoStepPasswordChanged, currentPasswordHash, currentPassword.new_algo, currentPassword.new_secure_algo, currentPassword.secure_random, email, hint, null, firstPassword);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                            }
                        } else {
                            if (error.text.startsWith("CODE_INVALID")) {
                                onFieldError(passwordEditText, true);
                            } else if (error.text.startsWith("FLOOD_WAIT")) {
                                int time = Utilities.parseInt(error.text);
                                String timeString;
                                if (time < 60) {
                                    timeString = LocaleController.formatPluralString("Seconds", time);
                                } else {
                                    timeString = LocaleController.formatPluralString("Minutes", time / 60);
                                }
                                showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                            } else {
                                showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                            }
                        }
                    }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    needShowProgress();
                }
            }
        });

        switch (currentType) {
            case TYPE_INTRO:
            case TYPE_PASSWORD_SET:
            case TYPE_VERIFY_OK: {
                ViewGroup container = new ViewGroup(context) {

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int width = MeasureSpec.getSize(widthMeasureSpec);
                        int height = MeasureSpec.getSize(heightMeasureSpec);

                        actionBar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);

                        if (width > height) {
                            imageView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.45f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.68f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText2.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        } else {
                            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.399f), MeasureSpec.EXACTLY));
                            titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            descriptionText2.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                            buttonTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(42), MeasureSpec.EXACTLY));
                        }

                        setMeasuredDimension(width, height);
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        actionBar.layout(0, 0, r, actionBar.getMeasuredHeight());

                        int width = r - l;
                        int height = b - t;

                        if (r > b) {
                            int y = (height - imageView.getMeasuredHeight()) / 2;
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            int x = (int) (width * 0.4f);
                            y = (int) (height * 0.22f);
                            titleTextView.layout(x, y, x + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            x = (int) (width * 0.4f);
                            y = (int) (height * 0.39f);
                            descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            x = (int) (width * 0.4f + (width * 0.6f - buttonTextView.getMeasuredWidth()) / 2);
                            y = (int) (height * 0.64f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        } else {
                            int y = (int) (height * 0.148f);
                            imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                            y = (int) (height * 0.458f);
                            titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                            y += titleTextView.getMeasuredHeight() + AndroidUtilities.dp(12);
                            descriptionText.layout(0, y, descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                            int x = (width - buttonTextView.getMeasuredWidth()) / 2;
                            y = (int) (height * 0.791f);
                            buttonTextView.layout(x, y, x + buttonTextView.getMeasuredWidth(), y + buttonTextView.getMeasuredHeight());
                        }
                    }
                };
                container.setOnTouchListener((v, event) -> true);
                container.addView(actionBar);
                container.addView(imageView);
                container.addView(titleTextView);
                container.addView(descriptionText);
                container.addView(buttonTextView);
                fragmentView = container;
                break;
            }
            case TYPE_VERIFY:
            case TYPE_ENTER_FIRST:
            case TYPE_ENTER_SECOND:
            case TYPE_EMAIL_CONFIRM:
            case TYPE_EMAIL_RECOVERY:
            case TYPE_ENTER_HINT:
            case TYPE_ENTER_EMAIL: {
                ViewGroup container = new ViewGroup(context) {

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int width = MeasureSpec.getSize(widthMeasureSpec);
                        int height = MeasureSpec.getSize(heightMeasureSpec);

                        if (topButton != null) {
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) topButton.getLayoutParams();
                            layoutParams.topMargin = AndroidUtilities.statusBarHeight;
                        }

                        actionBar.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);
                        actionBarBackground.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(actionBar.getMeasuredHeight() + AndroidUtilities.dp(3), MeasureSpec.EXACTLY));
                        scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec);

                        setMeasuredDimension(width, height);
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        actionBar.layout(0, 0, actionBar.getMeasuredWidth(), actionBar.getMeasuredHeight());
                        actionBarBackground.layout(0, 0, actionBarBackground.getMeasuredWidth(), actionBarBackground.getMeasuredHeight());
                        scrollView.layout(0, 0, scrollView.getMeasuredWidth(), scrollView.getMeasuredHeight());
                    }
                };

                scrollView = new ScrollView(context) {

                    private int[] location = new int[2];
                    private Rect tempRect = new Rect();
                    private boolean isLayoutDirty = true;
                    private int scrollingUp;

                    @Override
                    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                        super.onScrollChanged(l, t, oldl, oldt);

                        if (titleTextView == null) {
                            return;
                        }
                        titleTextView.getLocationOnScreen(location);
                        boolean show = location[1] + titleTextView.getMeasuredHeight() < actionBar.getBottom();
                        boolean visible = titleTextView.getTag() == null;
                        if (show != visible) {
                            titleTextView.setTag(show ? null : 1);
                            if (actionBarAnimator != null) {
                                actionBarAnimator.cancel();
                                actionBarAnimator = null;
                            }
                            actionBarAnimator = new AnimatorSet();
                            actionBarAnimator.playTogether(
                                    ObjectAnimator.ofFloat(actionBarBackground, View.ALPHA, show ? 1.0f : 0.0f),
                                    ObjectAnimator.ofFloat(actionBar.getTitleTextView(), View.ALPHA, show ? 1.0f : 0.0f)
                            );
                            actionBarAnimator.setDuration(150);
                            actionBarAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (animation.equals(actionBarAnimator)) {
                                        actionBarAnimator = null;
                                    }
                                }
                            });
                            actionBarAnimator.start();
                        }
                    }

                    @Override
                    public void scrollToDescendant(View child) {
                        child.getDrawingRect(tempRect);
                        offsetDescendantRectToMyCoords(child, tempRect);

                        tempRect.bottom += AndroidUtilities.dp(120);

                        int scrollDelta = computeScrollDeltaToGetChildRectOnScreen(tempRect);
                        if (scrollDelta < 0) {
                            scrollDelta -= (scrollingUp = (getMeasuredHeight() - child.getMeasuredHeight()) / 2);
                        } else {
                            scrollingUp = 0;
                        }
                        if (scrollDelta != 0) {
                            smoothScrollBy(0, scrollDelta);
                        }
                    }

                    @Override
                    public void requestChildFocus(View child, View focused) {
                        if (Build.VERSION.SDK_INT < 29) {
                            if (focused != null && !isLayoutDirty) {
                                scrollToDescendant(focused);
                            }
                        }
                        super.requestChildFocus(child, focused);
                    }

                    @Override
                    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                        if (Build.VERSION.SDK_INT < 23) {

                            rectangle.bottom += AndroidUtilities.dp(120);

                            if (scrollingUp != 0) {
                                rectangle.top -= scrollingUp;
                                rectangle.bottom -= scrollingUp;
                                scrollingUp = 0;
                            }
                        }
                        return super.requestChildRectangleOnScreen(child, rectangle, immediate);
                    }

                    @Override
                    public void requestLayout() {
                        isLayoutDirty = true;
                        super.requestLayout();
                    }

                    @Override
                    protected void onLayout(boolean changed, int l, int t, int r, int b) {
                        isLayoutDirty = false;
                        super.onLayout(changed, l, t, r, b);
                    }
                };
                scrollView.setVerticalScrollBarEnabled(false);
                container.addView(scrollView);

                LinearLayout scrollViewLinearLayout = new LinearLayout(context);
                scrollViewLinearLayout.setOrientation(LinearLayout.VERTICAL);
                scrollView.addView(scrollViewLinearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

                scrollViewLinearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 69, 0, 0));
                scrollViewLinearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));
                scrollViewLinearLayout.addView(descriptionText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 9, 0, 0));

                FrameLayout frameLayout = new FrameLayout(context);
                scrollViewLinearLayout.addView(frameLayout, LayoutHelper.createLinear(220, 36, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 40, 32, 40, 0));

                passwordEditText = new EditTextBoldCursor(context);
                passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                passwordEditText.setPadding(0, AndroidUtilities.dp(2), 0, 0);
                passwordEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                passwordEditText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
                passwordEditText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
                passwordEditText.setMaxLines(1);
                passwordEditText.setLines(1);
                passwordEditText.setGravity(Gravity.LEFT);
                passwordEditText.setCursorSize(AndroidUtilities.dp(20));
                passwordEditText.setSingleLine(true);
                passwordEditText.setCursorWidth(1.5f);
                frameLayout.addView(passwordEditText, LayoutHelper.createFrame(220, 36, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
                passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
                    if (i == EditorInfo.IME_ACTION_NEXT || i == EditorInfo.IME_ACTION_DONE) {
                        buttonTextView.callOnClick();
                        return true;
                    }
                    return false;
                });
                passwordEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    public void onDestroyActionMode(ActionMode mode) {
                    }

                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        return false;
                    }
                });

                showPasswordButton = new ImageView(context) {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(info);
                        info.setCheckable(true);
                        info.setChecked(passwordEditText.getTransformationMethod() == null);
                    }
                };
                showPasswordButton.setImageResource(R.drawable.msg_message);
                showPasswordButton.setScaleType(ImageView.ScaleType.CENTER);
                showPasswordButton.setContentDescription(LocaleController.getString("TwoStepVerificationShowPassword", R.string.TwoStepVerificationShowPassword));
                if (Build.VERSION.SDK_INT >= 21) {
                    showPasswordButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
                }
                showPasswordButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
                showPasswordButton.setVisibility(View.GONE);
                frameLayout.addView(showPasswordButton, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 0, -5, 0, 0));
                showPasswordButton.setOnClickListener(v -> {
                    ignoreTextChange = true;
                    if (passwordEditText.getTransformationMethod() == null) {
                        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        showPasswordButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
                        if (currentType == TYPE_ENTER_FIRST) {
                            if (passwordEditText.length() > 0) {
                                animationDrawables[3].setCustomEndFrame(-1);
                                if (imageView.getAnimatedDrawable() != animationDrawables[3]) {
                                    imageView.setAnimation(animationDrawables[3]);
                                    animationDrawables[3].setCurrentFrame(18, false);
                                }
                                imageView.playAnimation();
                            }
                        }
                    } else {
                        passwordEditText.setTransformationMethod(null);
                        showPasswordButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend), PorterDuff.Mode.MULTIPLY));
                        if (currentType == TYPE_ENTER_FIRST) {
                            if (passwordEditText.length() > 0) {
                                animationDrawables[3].setCustomEndFrame(18);
                                if (imageView.getAnimatedDrawable() != animationDrawables[3]) {
                                    imageView.setAnimation(animationDrawables[3]);
                                }
                                animationDrawables[3].setProgress(0.0f, false);
                                imageView.playAnimation();
                            }
                        }
                    }
                    passwordEditText.setSelection(passwordEditText.length());
                    ignoreTextChange = false;
                });

                FrameLayout frameLayout2 = new FrameLayout(context);
                scrollViewLinearLayout.addView(frameLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 36, 0, 22));

                frameLayout2.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 42, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
                frameLayout2.addView(descriptionText2, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

                if (currentType == TYPE_EMAIL_RECOVERY) {
                    descriptionText3 = new TextView(context);
                    descriptionText3.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
                    descriptionText3.setGravity(Gravity.CENTER_HORIZONTAL);
                    descriptionText3.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    descriptionText3.setLineSpacing(AndroidUtilities.dp(2), 1);
                    descriptionText3.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
                    descriptionText3.setText(LocaleController.getString("RestoreEmailTroubleNoEmail", R.string.RestoreEmailTroubleNoEmail));
                    scrollViewLinearLayout.addView(descriptionText3, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 25));
                    descriptionText3.setOnClickListener(v -> {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialog, which) -> {
                            onReset();
                            finishFragment();
                        });
                        builder.setTitle(LocaleController.getString("ResetPassword", R.string.ResetPassword));
                        builder.setMessage(LocaleController.getString("RestoreEmailTroubleText2", R.string.RestoreEmailTroubleText2));
                        showDialog(builder.create());
                    });
                }

                fragmentView = container;

                actionBarBackground = new View(context) {

                    private Paint paint = new Paint();

                    @Override
                    protected void onDraw(Canvas canvas) {
                        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        int h = getMeasuredHeight() - AndroidUtilities.dp(3);
                        canvas.drawRect(0, 0, getMeasuredWidth(), h, paint);
                        parentLayout.drawHeaderShadow(canvas, h);
                    }
                };
                actionBarBackground.setAlpha(0.0f);
                container.addView(actionBarBackground);
                container.addView(actionBar);
                break;
            }
        }

        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        switch (currentType) {
            case TYPE_INTRO: {
                titleTextView.setText(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle));
                descriptionText.setText(LocaleController.getString("SetAdditionalPasswordInfo", R.string.SetAdditionalPasswordInfo));
                buttonTextView.setText(LocaleController.getString("TwoStepVerificationSetPassword", R.string.TwoStepVerificationSetPassword));
                descriptionText.setVisibility(View.VISIBLE);

                imageView.setAnimation(R.raw.tsv_setup_intro, 120, 120);
                imageView.playAnimation();
                break;
            }
            case TYPE_PASSWORD_SET: {
                titleTextView.setText(LocaleController.getString("TwoStepVerificationPasswordSet", R.string.TwoStepVerificationPasswordSet));
                descriptionText.setText(LocaleController.getString("TwoStepVerificationPasswordSetInfo", R.string.TwoStepVerificationPasswordSetInfo));
                if (closeAfterSet) {
                    buttonTextView.setText(LocaleController.getString("TwoStepVerificationPasswordReturnPassport", R.string.TwoStepVerificationPasswordReturnPassport));
                } else {
                    buttonTextView.setText(LocaleController.getString("TwoStepVerificationPasswordReturnSettings", R.string.TwoStepVerificationPasswordReturnSettings));
                }
                descriptionText.setVisibility(View.VISIBLE);

                imageView.setAnimation(R.raw.wallet_allset, 120, 120);
                imageView.playAnimation();
                break;
            }
            case TYPE_VERIFY_OK: {
                titleTextView.setText(LocaleController.getString("CheckPasswordPerfect", R.string.CheckPasswordPerfect));
                descriptionText.setText(LocaleController.getString("CheckPasswordPerfectInfo", R.string.CheckPasswordPerfectInfo));
                buttonTextView.setText(LocaleController.getString("CheckPasswordBackToSettings", R.string.CheckPasswordBackToSettings));
                descriptionText.setVisibility(View.VISIBLE);

                imageView.setAnimation(R.raw.wallet_perfect, 120, 120);
                imageView.playAnimation();
                break;
            }
            case TYPE_VERIFY: {
                actionBar.setTitle(LocaleController.getString("PleaseEnterCurrentPassword", R.string.PleaseEnterCurrentPassword));
                titleTextView.setText(LocaleController.getString("PleaseEnterCurrentPassword", R.string.PleaseEnterCurrentPassword));
                descriptionText.setText(LocaleController.getString("CheckPasswordInfo", R.string.CheckPasswordInfo));

                descriptionText.setVisibility(View.VISIBLE);
                actionBar.getTitleTextView().setAlpha(0.0f);
                buttonTextView.setText(LocaleController.getString("CheckPassword", R.string.CheckPassword));
                descriptionText2.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
                descriptionText2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
                passwordEditText.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordEditText.setTypeface(Typeface.DEFAULT);
                passwordEditText.setPadding(0, AndroidUtilities.dp(2), AndroidUtilities.dp(36), 0);

                imageView.setAnimation(R.raw.wallet_science, 120, 120);
                imageView.playAnimation();
                break;
            }
            case TYPE_ENTER_FIRST: {
                if (currentPassword.has_password) {
                    actionBar.setTitle(LocaleController.getString("PleaseEnterNewFirstPassword", R.string.PleaseEnterNewFirstPassword));
                    titleTextView.setText(LocaleController.getString("PleaseEnterNewFirstPassword", R.string.PleaseEnterNewFirstPassword));
                } else {
                    actionBar.setTitle(LocaleController.getString("PleaseEnterFirstPassword", R.string.PleaseEnterFirstPassword));
                    titleTextView.setText(LocaleController.getString("PleaseEnterFirstPassword", R.string.PleaseEnterFirstPassword));
                }
                if (!TextUtils.isEmpty(emailCode)) {
                    topButton.setVisibility(View.VISIBLE);
                    topButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
                }
                actionBar.getTitleTextView().setAlpha(0.0f);
                buttonTextView.setText(LocaleController.getString("Continue", R.string.Continue));
                passwordEditText.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordEditText.setTypeface(Typeface.DEFAULT);
                showPasswordButton.setVisibility(View.VISIBLE);
                passwordEditText.setPadding(0, AndroidUtilities.dp(2), AndroidUtilities.dp(36), 0);

                animationDrawables = new RLottieDrawable[6];
                animationDrawables[0] = new RLottieDrawable(R.raw.tsv_setup_monkey_idle1, "" + R.raw.tsv_setup_monkey_idle1, AndroidUtilities.dp(120), AndroidUtilities.dp(120), true, null);
                animationDrawables[1] = new RLottieDrawable(R.raw.tsv_setup_monkey_idle2, "" + R.raw.tsv_setup_monkey_idle2, AndroidUtilities.dp(120), AndroidUtilities.dp(120), true, null);
                animationDrawables[2] = new RLottieDrawable(R.raw.tsv_monkey_close, "" + R.raw.tsv_monkey_close, AndroidUtilities.dp(120), AndroidUtilities.dp(120), true, null);
                animationDrawables[3] = new RLottieDrawable(R.raw.tsv_setup_monkey_peek, "" + R.raw.tsv_setup_monkey_peek, AndroidUtilities.dp(120), AndroidUtilities.dp(120), true, null);
                animationDrawables[4] = new RLottieDrawable(R.raw.tsv_setup_monkey_close_and_peek_to_idle, "" + R.raw.tsv_setup_monkey_close_and_peek_to_idle, AndroidUtilities.dp(120), AndroidUtilities.dp(120), true, null);
                animationDrawables[5] = new RLottieDrawable(R.raw.tsv_setup_monkey_close_and_peek, "" + R.raw.tsv_setup_monkey_close_and_peek, AndroidUtilities.dp(120), AndroidUtilities.dp(120), true, null);
                animationDrawables[2].setOnFinishCallback(finishCallback, 97);
                setRandomMonkeyIdleAnimation(true);
                break;
            }
            case TYPE_ENTER_SECOND: {
                actionBar.setTitle(LocaleController.getString("PleaseReEnterPassword", R.string.PleaseReEnterPassword));
                actionBar.getTitleTextView().setAlpha(0.0f);
                titleTextView.setText(LocaleController.getString("PleaseReEnterPassword", R.string.PleaseReEnterPassword));
                buttonTextView.setText(LocaleController.getString("Continue", R.string.Continue));
                passwordEditText.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordEditText.setTypeface(Typeface.DEFAULT);
                showPasswordButton.setVisibility(View.VISIBLE);
                passwordEditText.setPadding(0, AndroidUtilities.dp(2), AndroidUtilities.dp(36), 0);

                animationDrawables = new RLottieDrawable[1];
                animationDrawables[0] = new RLottieDrawable(R.raw.tsv_setup_monkey_tracking, "" + R.raw.tsv_setup_monkey_tracking, AndroidUtilities.dp(120), AndroidUtilities.dp(120), true, null);
                animationDrawables[0].setPlayInDirectionOfCustomEndFrame(true);
                animationDrawables[0].setCustomEndFrame(19);
                imageView.setAnimation(animationDrawables[0]);
                imageView.playAnimation();
                break;
            }
            case TYPE_ENTER_HINT: {
                actionBar.setTitle(LocaleController.getString("PasswordHint", R.string.PasswordHint));
                actionBar.getTitleTextView().setAlpha(0.0f);
                topButton.setVisibility(View.VISIBLE);
                topButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
                titleTextView.setText(LocaleController.getString("PasswordHint", R.string.PasswordHint));
                buttonTextView.setText(LocaleController.getString("Continue", R.string.Continue));
                passwordEditText.setHint(LocaleController.getString("PasswordHintPlaceholder", R.string.PasswordHintPlaceholder));
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);

                imageView.setAnimation(R.raw.tsv_setup_hint, 120, 120);
                imageView.playAnimation();
                break;
            }
            case TYPE_ENTER_EMAIL: {
                actionBar.setTitle(LocaleController.getString("RecoveryEmailTitle", R.string.RecoveryEmailTitle));
                actionBar.getTitleTextView().setAlpha(0.0f);
                if (!emailOnly) {
                    topButton.setVisibility(View.VISIBLE);
                    topButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
                }
                titleTextView.setText(LocaleController.getString("RecoveryEmailTitle", R.string.RecoveryEmailTitle));
                buttonTextView.setText(LocaleController.getString("Continue", R.string.Continue));
                passwordEditText.setHint(LocaleController.getString("PaymentShippingEmailPlaceholder", R.string.PaymentShippingEmailPlaceholder));
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                passwordEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

                imageView.setAnimation(R.raw.tsv_setup_email_sent, 120, 120);
                imageView.playAnimation();
                break;
            }
            case TYPE_EMAIL_CONFIRM: {
                actionBar.setTitle(LocaleController.getString("VerificationCode", R.string.VerificationCode));
                actionBar.getTitleTextView().setAlpha(0.0f);
                titleTextView.setText(LocaleController.getString("VerificationCode", R.string.VerificationCode));
                buttonTextView.setText(LocaleController.getString("Continue", R.string.Continue));
                passwordEditText.setHint(LocaleController.getString("EnterCode", R.string.EnterCode));
                passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                descriptionText2.setText(LocaleController.formatString("EmailPasswordConfirmText2", R.string.EmailPasswordConfirmText2, currentPassword.email_unconfirmed_pattern != null ? currentPassword.email_unconfirmed_pattern : ""));
                descriptionText2.setVisibility(View.VISIBLE);
                buttonTextView.setVisibility(View.INVISIBLE);
                buttonTextView.setAlpha(0.0f);
                buttonTextView.setScaleX(0.9f);
                buttonTextView.setScaleY(0.9f);

                imageView.setAnimation(R.raw.tsv_setup_mail, 120, 120);
                imageView.playAnimation();
                break;
            }
            case TYPE_EMAIL_RECOVERY: {
                actionBar.setTitle(LocaleController.getString("PasswordRecovery", R.string.PasswordRecovery));
                actionBar.getTitleTextView().setAlpha(0.0f);
                titleTextView.setText(LocaleController.getString("PasswordRecovery", R.string.PasswordRecovery));
                buttonTextView.setText(LocaleController.getString("Continue", R.string.Continue));
                passwordEditText.setHint(LocaleController.getString("EnterCode", R.string.EnterCode));
                passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
                passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                descriptionText2.setText(LocaleController.formatString("RestoreEmailSent", R.string.RestoreEmailSent, currentPassword.email_unconfirmed_pattern != null ? currentPassword.email_unconfirmed_pattern : ""));
                descriptionText2.setVisibility(View.VISIBLE);
                buttonTextView.setVisibility(View.INVISIBLE);
                buttonTextView.setAlpha(0.0f);
                buttonTextView.setScaleX(0.9f);
                buttonTextView.setScaleY(0.9f);

                imageView.setAnimation(R.raw.tsv_setup_mail, 120, 120);
                imageView.playAnimation();
                break;
            }
        }

        if (passwordEditText != null) {
            passwordEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreTextChange) {
                        return;
                    }
                    if (currentType == TYPE_ENTER_FIRST) {
                        RLottieDrawable currentDrawable = imageView.getAnimatedDrawable();
                        if (passwordEditText.length() > 0) {
                            if (passwordEditText.getTransformationMethod() == null) {
                                if (currentDrawable != animationDrawables[3] && currentDrawable != animationDrawables[5]) {
                                    imageView.setAnimation(animationDrawables[5]);
                                    animationDrawables[5].setProgress(0.0f, false);
                                    imageView.playAnimation();
                                }
                            } else {
                                if (currentDrawable != animationDrawables[3]) {
                                    if (currentDrawable != animationDrawables[2]) {
                                        imageView.setAnimation(animationDrawables[2]);
                                        animationDrawables[2].setCustomEndFrame(49);
                                        animationDrawables[2].setProgress(0.0f, false);
                                        imageView.playAnimation();
                                    } else {
                                        if (animationDrawables[2].getCurrentFrame() < 49) {
                                            animationDrawables[2].setCustomEndFrame(49);
                                        }
                                    }
                                }
                            }
                        } else {
                            if (currentDrawable == animationDrawables[3] && passwordEditText.getTransformationMethod() == null || currentDrawable == animationDrawables[5]) {
                                imageView.setAnimation(animationDrawables[4]);
                                animationDrawables[4].setProgress(0.0f, false);
                                imageView.playAnimation();
                            } else {
                                animationDrawables[2].setCustomEndFrame(-1);
                                if (currentDrawable != animationDrawables[2]) {
                                    imageView.setAnimation(animationDrawables[2]);
                                    animationDrawables[2].setCurrentFrame(49, false);
                                }
                                imageView.playAnimation();
                            }
                        }
                    } else if (currentType == TYPE_ENTER_SECOND) {
                        try {
                            float progress = Math.min(1.0f, passwordEditText.getLayout().getLineWidth(0) / passwordEditText.getWidth());
                            animationDrawables[0].setCustomEndFrame((int) (18 + progress * (160 - 18)));
                            imageView.playAnimation();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    } else if (currentType == TYPE_EMAIL_CONFIRM || currentType == TYPE_EMAIL_RECOVERY) {
                        if (emailCodeLength != 0 && s.length() == emailCodeLength) {
                            buttonTextView.callOnClick();
                        }
                        showDoneButton(s.length() > 0);
                    } else if (currentType == TYPE_VERIFY) {
                        if (s.length() > 0) {
                            showDoneButton(true);
                        }
                    }
                }
            });
        }

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        paused = false;
        if (passwordEditText != null && passwordEditText.getVisibility() == View.VISIBLE) {
            AndroidUtilities.runOnUIThread(() -> {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
            }, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    protected boolean hideKeyboardOnShow() {
        return currentType == TYPE_PASSWORD_SET || currentType == TYPE_VERIFY_OK;
    }

    private void onHintDone() {
        if (!currentPassword.has_recovery) {
            TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TYPE_ENTER_EMAIL, currentPassword);
            fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, emailOnly);
            fragment.firstPassword = firstPassword;
            fragment.hint = hint;
            fragment.fragmentsToClose.addAll(fragmentsToClose);
            fragment.fragmentsToClose.add(this);
            fragment.closeAfterSet = closeAfterSet;
            fragment.setBlockingAlert(otherwiseReloginDays);
            presentFragment(fragment);
        } else {
            email = "";
            setNewPassword(false);
        }
    }

    private void showDoneButton(boolean show) {
        if (show == (buttonTextView.getTag() != null)) {
            return;
        }
        if (buttonAnimation != null) {
            buttonAnimation.cancel();
        }
        buttonTextView.setTag(show ? 1 : null);
        buttonAnimation = new AnimatorSet();
        if (show) {
            buttonTextView.setVisibility(View.VISIBLE);
            buttonAnimation.playTogether(
                    ObjectAnimator.ofFloat(descriptionText2, View.SCALE_X, 0.9f),
                    ObjectAnimator.ofFloat(descriptionText2, View.SCALE_Y, 0.9f),
                    ObjectAnimator.ofFloat(descriptionText2, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(buttonTextView, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(buttonTextView, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(buttonTextView, View.ALPHA, 1.0f));
        } else {
            descriptionText2.setVisibility(View.VISIBLE);
            buttonAnimation.playTogether(
                    ObjectAnimator.ofFloat(buttonTextView, View.SCALE_X, 0.9f),
                    ObjectAnimator.ofFloat(buttonTextView, View.SCALE_Y, 0.9f),
                    ObjectAnimator.ofFloat(buttonTextView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(descriptionText2, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(descriptionText2, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(descriptionText2, View.ALPHA, 1.0f));
        }
        buttonAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (buttonAnimation != null && buttonAnimation.equals(animation)) {
                    if (show) {
                        descriptionText2.setVisibility(View.INVISIBLE);
                    } else {
                        buttonTextView.setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (buttonAnimation != null && buttonAnimation.equals(animation)) {
                    buttonAnimation = null;
                }
            }
        });
        buttonAnimation.setDuration(150);
        buttonAnimation.start();
    }

    private void setRandomMonkeyIdleAnimation(boolean first) {
        if (currentType != TYPE_ENTER_FIRST) {
            return;
        }
        if (setAnimationRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(setAnimationRunnable);
        }
        RLottieDrawable currentAnimation = imageView.getAnimatedDrawable();
        if (first || (currentAnimation == animationDrawables[0] || currentAnimation == animationDrawables[1]) || passwordEditText.length() == 0 && (currentAnimation == null || !currentAnimation.isRunning())) {
            if (Utilities.random.nextInt() % 2 == 0) {
                imageView.setAnimation(animationDrawables[0]);
                animationDrawables[0].setProgress(0.0f);
            } else {
                imageView.setAnimation(animationDrawables[1]);
                animationDrawables[1].setProgress(0.0f);
            }
            if (!first) {
                imageView.playAnimation();
            }
        }
        AndroidUtilities.runOnUIThread(setAnimationRunnable = () -> {
            if (setAnimationRunnable == null) {
                return;
            }
            setRandomMonkeyIdleAnimation(false);
        }, Utilities.random.nextInt(2000) + 5000);
    }

    public void setCloseAfterSet(boolean value) {
        closeAfterSet = value;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            if (passwordEditText != null && passwordEditText.getVisibility() == View.VISIBLE) {
                AndroidUtilities.showKeyboard(passwordEditText);
            }
        }
    }

    private void loadPasswordInfo() {
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                currentPassword = (TLRPC.TL_account_password) response;
                if (!TwoStepVerificationActivity.canHandleCurrentPassword(currentPassword, false)) {
                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                    return;
                }
                waitingForEmail = !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern);
                TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                if (!paused && closeAfterSet && currentPassword.has_password) {
                    TLRPC.PasswordKdfAlgo pendingCurrentAlgo = currentPassword.current_algo;
                    TLRPC.SecurePasswordKdfAlgo pendingNewSecureAlgo = currentPassword.new_secure_algo;
                    byte[] pendingSecureRandom = currentPassword.secure_random;
                    String pendingEmail = currentPassword.has_recovery ? "1" : null;
                    String pendingHint = currentPassword.hint != null ? currentPassword.hint : "";

                    if (!waitingForEmail && pendingCurrentAlgo != null) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.twoStepPasswordChanged, null, pendingCurrentAlgo, pendingNewSecureAlgo, pendingSecureRandom, pendingEmail, pendingHint, null, null);
                        finishFragment();
                    }
                }
                if (doneAfterPasswordLoad) {
                    needHideProgress();
                    buttonTextView.callOnClick();
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
    }

    protected void needHideProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            FileLog.e(e);
        }
        progressDialog = null;
    }

    private boolean isValidEmail(String text) {
        if (text == null || text.length() < 3) {
            return false;
        }
        int dot = text.lastIndexOf('.');
        int dog = text.lastIndexOf('@');
        return !(dog < 0 || dot < dog);
    }

    private void showAlertWithText(String title, String text) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setTitle(title);
        builder.setMessage(text);
        showDialog(builder.create());
    }

    private void setNewPassword(final boolean clear) {
        if (clear && waitingForEmail && currentPassword.has_password) {
            needShowProgress();
            TLRPC.TL_account_cancelPasswordEmail req = new TLRPC.TL_account_cancelPasswordEmail();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress();
                if (error == null) {
                    TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                    currentPassword.has_recovery = false;
                    currentPassword.email_unconfirmed_pattern = "";
                    fragment.setCurrentPasswordParams(currentPassword, currentPasswordHash, currentSecretId, currentSecret);
                    fragment.setBlockingAlert(otherwiseReloginDays);
                    presentFragment(fragment, true);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didRemoveTwoStepPassword);
                }
            }));
            return;
        }
        final String password = firstPassword;

        TLRPC.TL_account_passwordInputSettings new_settings = new TLRPC.TL_account_passwordInputSettings();
        if (clear) {
            UserConfig.getInstance(currentAccount).resetSavedPassword();
            currentSecret = null;
            if (waitingForEmail) {
                new_settings.flags = 2;
                new_settings.email = "";
            } else {
                new_settings.flags = 3;
                new_settings.hint = "";
                new_settings.new_password_hash = new byte[0];
                new_settings.new_algo = new TLRPC.TL_passwordKdfAlgoUnknown();
                new_settings.email = "";
            }
        } else {
            if (hint == null && currentPassword != null) {
                hint = currentPassword.hint;
            }
            if (hint == null) {
                hint = "";
            }
            if (password != null) {
                new_settings.flags |= 1;
                new_settings.hint = hint;
                new_settings.new_algo = currentPassword.new_algo;
            }
            if (email.length() > 0) {
                new_settings.flags |= 2;
                new_settings.email = email.trim();
            }
        }

        TLObject request;
        if (emailCode != null) {
            TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
            req.code = emailCode;
            req.new_settings = new_settings;
            req.flags |= 1;
            request = req;
        } else {
            TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
            if (currentPasswordHash == null || currentPasswordHash.length == 0 || clear && waitingForEmail) {
                req.password = new TLRPC.TL_inputCheckPasswordEmpty();
            }
            req.new_settings = new_settings;
            request = req;
        }

        needShowProgress();
        Utilities.globalQueue.postRunnable(() -> {
            if (request instanceof TLRPC.TL_account_updatePasswordSettings) {
                TLRPC.TL_account_updatePasswordSettings req = (TLRPC.TL_account_updatePasswordSettings) request;
                if (req.password == null) {
                    req.password = getNewSrpPassword();
                }
            }

            byte[] newPasswordBytes;
            byte[] newPasswordHash;
            if (!clear && password != null) {
                newPasswordBytes = AndroidUtilities.getStringBytes(password);
                if (currentPassword.new_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.new_algo;
                    newPasswordHash = SRPHelper.getX(newPasswordBytes, algo);
                } else {
                    newPasswordHash = null;
                }
            } else {
                newPasswordBytes = null;
                newPasswordHash = null;
            }

            RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                    TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            currentPassword = (TLRPC.TL_account_password) response2;
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            setNewPassword(clear);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                    return;
                }
                needHideProgress();
                if (error == null && (response instanceof TLRPC.TL_boolTrue || response instanceof TLRPC.auth_Authorization)) {
                    getMessagesController().removeSuggestion(0, "VALIDATE_PASSWORD");
                    if (clear) {
                        for (int a = 0, N = fragmentsToClose.size(); a < N; a++) {
                            fragmentsToClose.get(a).removeSelfFromStack();
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didRemoveTwoStepPassword);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword);
                        finishFragment();
                    } else {
                        if (getParentActivity() == null) {
                            return;
                        }
                        if (currentPassword.has_password) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                                for (int a = 0, N = fragmentsToClose.size(); a < N; a++) {
                                    fragmentsToClose.get(a).removeSelfFromStack();
                                }
                                TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                                currentPassword.has_password = true;
                                if (!currentPassword.has_recovery) {
                                    currentPassword.has_recovery = !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern);
                                }
                                fragment.setCurrentPasswordParams(currentPassword, newPasswordHash != null ? newPasswordHash : currentPasswordHash, currentSecretId, currentSecret);
                                fragment.setBlockingAlert(otherwiseReloginDays);
                                presentFragment(fragment, true);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                            });
                            if (password == null && currentPassword != null && currentPassword.has_password) {
                                builder.setMessage(LocaleController.getString("YourEmailSuccessText", R.string.YourEmailSuccessText));
                            } else {
                                builder.setMessage(LocaleController.getString("YourPasswordChangedSuccessText", R.string.YourPasswordChangedSuccessText));
                            }
                            builder.setTitle(LocaleController.getString("YourPasswordSuccess", R.string.YourPasswordSuccess));
                            Dialog dialog = showDialog(builder.create());
                            if (dialog != null) {
                                dialog.setCanceledOnTouchOutside(false);
                                dialog.setCancelable(false);
                            }
                        } else {
                            for (int a = 0, N = fragmentsToClose.size(); a < N; a++) {
                                fragmentsToClose.get(a).removeSelfFromStack();
                            }
                            currentPassword.has_password = true;
                            if (!currentPassword.has_recovery) {
                                currentPassword.has_recovery = !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern);
                            }
                            if (closeAfterSet) {
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.twoStepPasswordChanged);
                            }
                            TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(TYPE_PASSWORD_SET, currentPassword);
                            fragment.setCurrentPasswordParams(newPasswordHash != null ? newPasswordHash : currentPasswordHash, currentSecretId, currentSecret, emailOnly);
                            fragment.closeAfterSet = closeAfterSet;
                            fragment.setBlockingAlert(otherwiseReloginDays);
                            presentFragment(fragment, true);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                        }
                    }
                } else if (error != null) {
                    if ("EMAIL_UNCONFIRMED".equals(error.text) || error.text.startsWith("EMAIL_UNCONFIRMED_")) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.twoStepPasswordChanged);
                        for (int a = 0, N = fragmentsToClose.size(); a < N; a++) {
                            fragmentsToClose.get(a).removeSelfFromStack();
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.twoStepPasswordChanged, newPasswordHash, new_settings.new_algo, currentPassword.new_secure_algo, currentPassword.secure_random, email, hint, email, firstPassword);
                        currentPassword.email_unconfirmed_pattern = email;
                        TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM, currentPassword);
                        fragment.setCurrentPasswordParams(newPasswordHash != null ? newPasswordHash : currentPasswordHash, currentSecretId, currentSecret, emailOnly);
                        fragment.closeAfterSet = closeAfterSet;
                        fragment.setBlockingAlert(otherwiseReloginDays);
                        presentFragment(fragment, true);
                    } else {
                        if ("EMAIL_INVALID".equals(error.text)) {
                            showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("PasswordEmailInvalid", R.string.PasswordEmailInvalid));
                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                            int time = Utilities.parseInt(error.text);
                            String timeString;
                            if (time < 60) {
                                timeString = LocaleController.formatPluralString("Seconds", time);
                            } else {
                                timeString = LocaleController.formatPluralString("Minutes", time / 60);
                            }
                            showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                        } else {
                            showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                        }
                    }
                }
            });

            if (!clear) {
                if (password != null && currentSecret != null && currentSecret.length == 32) {
                    if (currentPassword.new_secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) {
                        TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000 newAlgo = (TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) currentPassword.new_secure_algo;

                        byte[] passwordHash = Utilities.computePBKDF2(newPasswordBytes, newAlgo.salt);
                        byte[] key = new byte[32];
                        System.arraycopy(passwordHash, 0, key, 0, 32);
                        byte[] iv = new byte[16];
                        System.arraycopy(passwordHash, 32, iv, 0, 16);

                        byte[] encryptedSecret = new byte[32];
                        System.arraycopy(currentSecret, 0, encryptedSecret, 0, 32);
                        Utilities.aesCbcEncryptionByteArraySafe(encryptedSecret, key, iv, 0, encryptedSecret.length, 0, 1);

                        new_settings.new_secure_settings = new TLRPC.TL_secureSecretSettings();
                        new_settings.new_secure_settings.secure_algo = newAlgo;
                        new_settings.new_secure_settings.secure_secret = encryptedSecret;
                        new_settings.new_secure_settings.secure_secret_id = currentSecretId;
                        new_settings.flags |= 4;
                    }
                }

                if (currentPassword.new_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
                    if (password != null) {
                        TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.new_algo;
                        new_settings.new_password_hash = SRPHelper.getVBytes(newPasswordBytes, algo);
                        if (new_settings.new_password_hash == null) {
                            TLRPC.TL_error error = new TLRPC.TL_error();
                            error.text = "ALGO_INVALID";
                            requestDelegate.run(null, error);
                        }
                    }
                    ConnectionsManager.getInstance(currentAccount).sendRequest(request, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.text = "PASSWORD_HASH_INVALID";
                    requestDelegate.run(null, error);
                }
            } else {
                ConnectionsManager.getInstance(currentAccount).sendRequest(request, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            }
        });
    }

    protected TLRPC.TL_inputCheckPasswordSRP getNewSrpPassword() {
        if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
            return SRPHelper.startCheck(currentPasswordHash, currentPassword.srp_id, currentPassword.srp_B, algo);
        }
        return null;
    }

    protected void onReset() {

    }

    private void onFieldError(TextView field, boolean clear) {
        if (getParentActivity() == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        if (clear) {
            field.setText("");
        }
        AndroidUtilities.shakeView(field, 2, 0);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        return themeDescriptions;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (otherwiseReloginDays >= 0 && parentLayout.fragmentsStack.size() == 1) {
            return false;
        }
        return super.isSwipeBackEnabled(event);
    }

    @Override
    public boolean onBackPressed() {
        if (otherwiseReloginDays >= 0 && parentLayout.fragmentsStack.size() == 1) {
            showSetForcePasswordAlert();
            return false;
        }
        return super.onBackPressed();
    }

    private void showSetForcePasswordAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("Warning", R.string.Warning));
        builder.setMessage(LocaleController.formatPluralString("ForceSetPasswordAlertMessage", otherwiseReloginDays));
        builder.setPositiveButton(LocaleController.getString("ForceSetPasswordContinue", R.string.ForceSetPasswordContinue), (a1, a2) -> {

        });

        builder.setNegativeButton(LocaleController.getString("ForceSetPasswordCancel", R.string.ForceSetPasswordCancel), (a1, a2) -> {
            finishFragment();
        });
        AlertDialog alertDialog = builder.show();
        ((TextView)alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)).setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
    }

    public void setBlockingAlert(int otherwiseRelogin) {
        otherwiseReloginDays = otherwiseRelogin;
    }

    @Override
    public void finishFragment() {
        if (otherwiseReloginDays >= 0 && parentLayout.fragmentsStack.size() == 1) {
                final Bundle args = new Bundle();
                args.putBoolean("afterSignup", true);
                presentFragment(new DialogsActivity(args), true);
        } else {
            super.finishFragment();
        }
    }
}
