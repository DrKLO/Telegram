/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EditTextSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Locale;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class TwoStepVerificationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private TextView bottomTextView;
    private SimpleTextView bottomButton;
    private TextView cancelResetButton;
    private EditTextBoldCursor passwordEditText;
    private AlertDialog progressDialog;
    private EmptyTextProgressView emptyView;
    private ActionBarMenuItem doneItem;
    private ScrollView scrollView;

    private String firstPassword;
    private String hint;
    private String email;
    private boolean emailOnly;
    private boolean loading;
    private boolean destroyed;
    private boolean paused;
    private TLRPC.TL_account_password currentPassword;
    private boolean passwordEntered = true;
    private byte[] currentPasswordHash = new byte[0];
    private long currentSecretId;
    private byte[] currentSecret;

    private boolean resetPasswordOnShow;

    private int setPasswordRow;
    private int setPasswordDetailRow;
    private int changePasswordRow;
    private int turnPasswordOffRow;
    private int setRecoveryEmailRow;
    private int changeRecoveryEmailRow;
    private int passwordEnabledDetailRow;
    private int rowCount;

    private boolean forgotPasswordOnShow;

    private TwoStepVerificationActivityDelegate delegate;

    public interface TwoStepVerificationActivityDelegate {
        void didEnterPassword(TLRPC.InputCheckPasswordSRP password);
    }

    private final static int done_button = 1;

    public TwoStepVerificationActivity() {
        super();

    }

    public TwoStepVerificationActivity(int account) {
        super();
        currentAccount = account;
    }

    public void setPassword(TLRPC.TL_account_password password) {
        currentPassword = password;
        passwordEntered = false;
    }

    public void setCurrentPasswordParams(TLRPC.TL_account_password password, byte[] passwordHash, long secretId, byte[] secret) {
        currentPassword = password;
        currentPasswordHash = passwordHash;
        currentSecret = secret;
        currentSecretId = secretId;
        passwordEntered = currentPasswordHash != null && currentPasswordHash.length > 0 || !currentPassword.has_password;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (currentPassword == null || currentPassword.current_algo == null || currentPasswordHash == null || currentPasswordHash.length <= 0) {
            loadPasswordInfo(true, currentPassword != null);
        }
        updateRows();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.twoStepPasswordChanged);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.cancelRunOnUIThread(updateTimeRunnable);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.twoStepPasswordChanged);
        destroyed = true;
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
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    processDone();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        linearLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

        passwordEditText = new EditTextBoldCursor(context);
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        passwordEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        passwordEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        passwordEditText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        passwordEditText.setMaxLines(1);
        passwordEditText.setLines(1);
        passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordEditText.setSingleLine(true);
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordEditText.setTypeface(Typeface.DEFAULT);
        passwordEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        passwordEditText.setCursorWidth(1.5f);
        linearLayout.addView(passwordEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 40, 32, 40, 0));
        passwordEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT || i == EditorInfo.IME_ACTION_DONE) {
                processDone();
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

        bottomTextView = new TextView(context);
        bottomTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bottomTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        bottomTextView.setText(LocaleController.getString("YourEmailInfo", R.string.YourEmailInfo));
        linearLayout.addView(bottomTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 40, 30, 40, 0));

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(LinearLayout.VERTICAL);
        linearLayout2.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        linearLayout2.setClipChildren(false);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bottomButton = new SimpleTextView(context);
        bottomButton.setTextSize(14);
        bottomButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
        bottomButton.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        linearLayout2.addView(bottomButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 40, 0, 40, 14));
        bottomButton.setOnClickListener(v -> onPasswordForgot());

        cancelResetButton = new TextView(context);
        cancelResetButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelResetButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
        cancelResetButton.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        cancelResetButton.setText(LocaleController.getString("CancelReset", R.string.CancelReset));
        cancelResetButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        linearLayout2.addView(cancelResetButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 40, 0, 40, 26));
        cancelResetButton.setOnClickListener(v -> cancelPasswordReset());

        emptyView = new EmptyTextProgressView(context);
        emptyView.showProgress();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setEmptyView(emptyView);
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position == setPasswordRow || position == changePasswordRow) {
                TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TwoStepVerificationSetupActivity.TYPE_ENTER_FIRST, currentPassword);
                fragment.addFragmentToClose(this);
                fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, false);
                presentFragment(fragment);
            } else if (position == setRecoveryEmailRow || position == changeRecoveryEmailRow) {
                TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TwoStepVerificationSetupActivity.TYPE_ENTER_EMAIL, currentPassword);
                fragment.addFragmentToClose(this);
                fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, true);
                presentFragment(fragment);
            } else if (position == turnPasswordOffRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                String text = LocaleController.getString("TurnPasswordOffQuestion", R.string.TurnPasswordOffQuestion);
                if (currentPassword.has_secure_values) {
                    text += "\n\n" + LocaleController.getString("TurnPasswordOffPassport", R.string.TurnPasswordOffPassport);
                }
                String title = LocaleController.getString("TurnPasswordOffQuestionTitle", R.string.TurnPasswordOffQuestionTitle);
                String buttonText = LocaleController.getString("Disable", R.string.Disable);

                builder.setMessage(text);
                builder.setTitle(title);
                builder.setPositiveButton(buttonText, (dialogInterface, i) -> clearPassword());
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                }
            }
        });

        updateRows();

        actionBar.setTitle(LocaleController.getString("TwoStepVerificationTitle", R.string.TwoStepVerificationTitle));
        if (delegate != null) {
            titleTextView.setText(LocaleController.getString("PleaseEnterCurrentPasswordTransfer", R.string.PleaseEnterCurrentPasswordTransfer));
        } else {
            titleTextView.setText(LocaleController.getString("PleaseEnterCurrentPassword", R.string.PleaseEnterCurrentPassword));
        }

        if (passwordEntered) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            fragmentView.setTag(Theme.key_windowBackgroundGray);
        } else {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            fragmentView.setTag(Theme.key_windowBackgroundWhite);
        }

        return fragmentView;
    }

    private Runnable updateTimeRunnable = this::updateBottomButton;

    private void cancelPasswordReset() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("CancelPasswordResetYes", R.string.CancelPasswordResetYes), (dialog, which) -> {
            TLRPC.TL_account_declinePasswordReset req = new TLRPC.TL_account_declinePasswordReset();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_boolTrue) {
                    currentPassword.pending_reset_date = 0;
                    updateBottomButton();
                }
            }));
        });
        builder.setNegativeButton(LocaleController.getString("CancelPasswordResetNo", R.string.CancelPasswordResetNo), null);
        builder.setTitle(LocaleController.getString("CancelReset", R.string.CancelReset));
        builder.setMessage(LocaleController.getString("CancelPasswordReset", R.string.CancelPasswordReset));
        showDialog(builder.create());
    }

    public void setForgotPasswordOnShow() {
        forgotPasswordOnShow = true;
    }

    private void resetPassword() {
        needShowProgress(true);
        TLRPC.TL_account_resetPassword req = new TLRPC.TL_account_resetPassword();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            needHideProgress();
            if (response instanceof TLRPC.TL_account_resetPasswordOk) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                builder.setTitle(LocaleController.getString("ResetPassword", R.string.ResetPassword));
                builder.setMessage(LocaleController.getString("RestorePasswordResetPasswordOk", R.string.RestorePasswordResetPasswordOk));
                showDialog(builder.create(), dialog -> {
                    getNotificationCenter().postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword);
                    finishFragment();
                });
            } else if (response instanceof TLRPC.TL_account_resetPasswordRequestedWait) {
                TLRPC.TL_account_resetPasswordRequestedWait res = (TLRPC.TL_account_resetPasswordRequestedWait) response;
                currentPassword.pending_reset_date = res.until_date;
                updateBottomButton();
            } else if (response instanceof TLRPC.TL_account_resetPasswordFailedWait) {
                TLRPC.TL_account_resetPasswordFailedWait res = (TLRPC.TL_account_resetPasswordFailedWait) response;
                int time = res.retry_date - getConnectionsManager().getCurrentTime();
                String timeString;
                if (time > 24 * 60 * 60) {
                    timeString = LocaleController.formatPluralString("Days", time / (24 * 60 * 60));
                } else if (time > 60 * 60) {
                    timeString = LocaleController.formatPluralString("Hours", time / (24 * 60 * 60));
                } else if (time > 60) {
                    timeString = LocaleController.formatPluralString("Minutes", time / 60);
                } else {
                    timeString = LocaleController.formatPluralString("Seconds", Math.max(1, time));
                }
                showAlertWithText(LocaleController.getString("ResetPassword", R.string.ResetPassword), LocaleController.formatString("ResetPasswordWait", R.string.ResetPasswordWait, timeString));
            }
        }));
    }

    private void updateBottomButton() {
        if (currentPassword == null || bottomButton == null || bottomButton.getVisibility() != View.VISIBLE) {
            AndroidUtilities.cancelRunOnUIThread(updateTimeRunnable);
            if (cancelResetButton != null) {
                cancelResetButton.setVisibility(View.GONE);
            }
            return;
        }
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) bottomButton.getLayoutParams();
        if (currentPassword.pending_reset_date == 0 || getConnectionsManager().getCurrentTime() > currentPassword.pending_reset_date) {
            if (currentPassword.pending_reset_date == 0) {
                bottomButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
                cancelResetButton.setVisibility(View.GONE);
                layoutParams.bottomMargin = AndroidUtilities.dp(14);
                layoutParams.height = AndroidUtilities.dp(40);
            } else {
                bottomButton.setText(LocaleController.getString("ResetPassword", R.string.ResetPassword));
                cancelResetButton.setVisibility(View.VISIBLE);
                layoutParams.bottomMargin = 0;
                layoutParams.height = AndroidUtilities.dp(22);
            }
            bottomButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            AndroidUtilities.cancelRunOnUIThread(updateTimeRunnable);
        } else {
            int t = Math.max(1, currentPassword.pending_reset_date - getConnectionsManager().getCurrentTime());
            String time;
            if (t > 24 * 60 * 60) {
                time = LocaleController.formatPluralString("Days", t / (24 * 60 * 60));
            } else if (t >= 60 * 60) {
                time = LocaleController.formatPluralString("Hours", t / (60 * 60));
            } else {
                time = String.format(Locale.US, "%02d:%02d", t / 60, t % 60);
            }
            bottomButton.setText(LocaleController.formatString("RestorePasswordResetIn", R.string.RestorePasswordResetIn, time));
            bottomButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            cancelResetButton.setVisibility(View.VISIBLE);
            layoutParams.bottomMargin = 0;
            layoutParams.height = AndroidUtilities.dp(22);
            AndroidUtilities.cancelRunOnUIThread(updateTimeRunnable);
            AndroidUtilities.runOnUIThread(updateTimeRunnable, 1000);
        }
        bottomButton.setLayoutParams(layoutParams);
    }

    private void onPasswordForgot() {
        if (currentPassword.pending_reset_date == 0 && currentPassword.has_recovery) {
            needShowProgress(true);
            TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress();
                if (error == null) {
                    final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                    currentPassword.email_unconfirmed_pattern = res.email_pattern;
                    TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TwoStepVerificationSetupActivity.TYPE_EMAIL_RECOVERY, currentPassword) {
                        @Override
                        protected void onReset() {
                            resetPasswordOnShow = true;
                        }
                    };
                    fragment.addFragmentToClose(this);
                    fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, false);
                    presentFragment(fragment);
                } else {
                    if (error.text.startsWith("FLOOD_WAIT")) {
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
        } else {
            if (getParentActivity() == null) {
                return;
            }
            if (currentPassword.pending_reset_date != 0) {
                if (getConnectionsManager().getCurrentTime() > currentPassword.pending_reset_date) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialog, which) -> resetPassword());
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setTitle(LocaleController.getString("ResetPassword", R.string.ResetPassword));
                    builder.setMessage(LocaleController.getString("RestorePasswordResetPasswordText", R.string.RestorePasswordResetPasswordText));
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
                } else {
                    cancelPasswordReset();
                }
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialog, which) -> resetPassword());
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                builder.setTitle(LocaleController.getString("ResetPassword", R.string.ResetPassword));
                builder.setMessage(LocaleController.getString("RestorePasswordNoEmailText2", R.string.RestorePasswordNoEmailText2));
                showDialog(builder.create());
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.twoStepPasswordChanged) {
            if (args != null && args.length > 0 && args[0] != null) {
                currentPasswordHash = (byte[]) args[0];
            }
            loadPasswordInfo(false, false);
            updateRows();
        }
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
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    public void setCurrentPasswordInfo(byte[] hash, TLRPC.TL_account_password password) {
        if (hash != null) {
            currentPasswordHash = hash;
        }
        currentPassword = password;
    }

    public void setDelegate(TwoStepVerificationActivityDelegate twoStepVerificationActivityDelegate) {
        delegate = twoStepVerificationActivityDelegate;
    }

    public static boolean canHandleCurrentPassword(TLRPC.TL_account_password password, boolean login) {
        if (login) {
            if (password.current_algo instanceof TLRPC.TL_passwordKdfAlgoUnknown) {
                return false;
            }
        } else {
            if (password.new_algo instanceof TLRPC.TL_passwordKdfAlgoUnknown ||
                    password.current_algo instanceof TLRPC.TL_passwordKdfAlgoUnknown ||
                    password.new_secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoUnknown) {
                return false;
            }
        }
        return true;
    }

    public static void initPasswordNewAlgo(TLRPC.TL_account_password password) {
        if (password.new_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) password.new_algo;
            byte[] salt = new byte[algo.salt1.length + 32];
            Utilities.random.nextBytes(salt);
            System.arraycopy(algo.salt1, 0, salt, 0, algo.salt1.length);
            algo.salt1 = salt;
        }
        if (password.new_secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) {
            TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000 algo = (TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) password.new_secure_algo;
            byte[] salt = new byte[algo.salt.length + 32];
            Utilities.random.nextBytes(salt);
            System.arraycopy(algo.salt, 0, salt, 0, algo.salt.length);
            algo.salt = salt;
        }
    }

    private void loadPasswordInfo(boolean first, final boolean silent) {
        if (!silent) {
            loading = true;
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                loading = false;
                currentPassword = (TLRPC.TL_account_password) response;
                if (!canHandleCurrentPassword(currentPassword, false)) {
                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                    return;
                }
                if (!silent || first) {
                    passwordEntered = currentPasswordHash != null && currentPasswordHash.length > 0 || !currentPassword.has_password;
                }
                initPasswordNewAlgo(currentPassword);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
            }
            updateRows();
        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) {
            if (forgotPasswordOnShow) {
                onPasswordForgot();
                forgotPasswordOnShow = false;
            } else if (resetPasswordOnShow) {
                resetPassword();
                resetPasswordOnShow = false;
            }
        }
    }

    private void updateRows() {
        StringBuilder lastValue = new StringBuilder();
        lastValue.append(setPasswordRow);
        lastValue.append(setPasswordDetailRow);
        lastValue.append(changePasswordRow);
        lastValue.append(turnPasswordOffRow);
        lastValue.append(setRecoveryEmailRow);
        lastValue.append(changeRecoveryEmailRow);
        lastValue.append(passwordEnabledDetailRow);
        lastValue.append(rowCount);

        rowCount = 0;
        setPasswordRow = -1;
        setPasswordDetailRow = -1;
        changePasswordRow = -1;
        turnPasswordOffRow = -1;
        setRecoveryEmailRow = -1;
        changeRecoveryEmailRow = -1;
        passwordEnabledDetailRow = -1;
        if (!loading && currentPassword != null && passwordEntered) {
            if (currentPassword.has_password) {
                changePasswordRow = rowCount++;
                turnPasswordOffRow = rowCount++;
                if (currentPassword.has_recovery) {
                    changeRecoveryEmailRow = rowCount++;
                } else {
                    setRecoveryEmailRow = rowCount++;
                }
                passwordEnabledDetailRow = rowCount++;
            } else {
                setPasswordRow = rowCount++;
                setPasswordDetailRow = rowCount++;
            }
        }
        StringBuilder newValue = new StringBuilder();
        newValue.append(setPasswordRow);
        newValue.append(setPasswordDetailRow);
        newValue.append(changePasswordRow);
        newValue.append(turnPasswordOffRow);
        newValue.append(setRecoveryEmailRow);
        newValue.append(changeRecoveryEmailRow);
        newValue.append(passwordEnabledDetailRow);
        newValue.append(rowCount);
        if (listAdapter != null && !lastValue.toString().equals(newValue.toString())) {
            listAdapter.notifyDataSetChanged();
        }
        if (fragmentView != null) {
            if (loading || passwordEntered) {
                if (listView != null) {
                    listView.setVisibility(View.VISIBLE);
                    scrollView.setVisibility(View.INVISIBLE);
                    listView.setEmptyView(emptyView);
                }
                if (passwordEditText != null) {
                    doneItem.setVisibility(View.GONE);
                    passwordEditText.setVisibility(View.INVISIBLE);
                    titleTextView.setVisibility(View.INVISIBLE);
                    bottomTextView.setVisibility(View.INVISIBLE);
                    bottomButton.setVisibility(View.INVISIBLE);
                    updateBottomButton();
                }
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                fragmentView.setTag(Theme.key_windowBackgroundGray);
            } else {
                if (listView != null) {
                    listView.setEmptyView(null);
                    listView.setVisibility(View.INVISIBLE);
                    scrollView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.INVISIBLE);
                }
                if (passwordEditText != null) {
                    doneItem.setVisibility(View.VISIBLE);
                    passwordEditText.setVisibility(View.VISIBLE);
                    fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    fragmentView.setTag(Theme.key_windowBackgroundWhite);
                    titleTextView.setVisibility(View.VISIBLE);
                    bottomButton.setVisibility(View.VISIBLE);
                    updateBottomButton();
                    bottomTextView.setVisibility(View.INVISIBLE);
                    if (!TextUtils.isEmpty(currentPassword.hint)) {
                        passwordEditText.setHint(currentPassword.hint);
                    } else {
                        passwordEditText.setHint("");
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!isFinishing() && !destroyed && passwordEditText != null) {
                            passwordEditText.requestFocus();
                            AndroidUtilities.showKeyboard(passwordEditText);
                        }
                    }, 200);
                }
            }
        }
    }

    private void needShowProgress() {
        needShowProgress(false);
    }

    private void needShowProgress(boolean delay) {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        if (delay) {
            progressDialog.showDelayed(300);
        } else {
            progressDialog.show();
        }
    }

    public void needHideProgress() {
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

    private void clearPassword() {
        final String password = firstPassword;
        final TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
        if (currentPasswordHash == null || currentPasswordHash.length == 0) {
            req.password = new TLRPC.TL_inputCheckPasswordEmpty();
        }
        req.new_settings = new TLRPC.TL_account_passwordInputSettings();

        UserConfig.getInstance(currentAccount).resetSavedPassword();
        currentSecret = null;
        req.new_settings.flags = 3;
        req.new_settings.hint = "";
        req.new_settings.new_password_hash = new byte[0];
        req.new_settings.new_algo = new TLRPC.TL_passwordKdfAlgoUnknown();
        req.new_settings.email = "";

        needShowProgress();
        Utilities.globalQueue.postRunnable(() -> {
            if (req.password == null) {
                if (currentPassword.current_algo == null) {
                    TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            currentPassword = (TLRPC.TL_account_password) response2;
                            initPasswordNewAlgo(currentPassword);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                            clearPassword();
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                    return;
                }
                req.password = getNewSrpPassword();
            }

            byte[] newPasswordBytes = null;
            byte[] newPasswordHash = null;

            RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error != null && "SRP_ID_INVALID".equals(error.text)) {
                    TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            currentPassword = (TLRPC.TL_account_password) response2;
                            initPasswordNewAlgo(currentPassword);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                            clearPassword();
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                    return;
                }
                needHideProgress();
                if (error == null && response instanceof TLRPC.TL_boolTrue) {
                    currentPassword = null;
                    currentPasswordHash = new byte[0];
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didRemoveTwoStepPassword);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword);
                    finishFragment();
                } else if (error != null) {
                    if (error.text.startsWith("FLOOD_WAIT")) {
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
            });
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        });
    }

    public TLRPC.TL_inputCheckPasswordSRP getNewSrpPassword() {
        if (currentPassword.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) {
            TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow algo = (TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow) currentPassword.current_algo;
            return SRPHelper.startCheck(currentPasswordHash, currentPassword.srp_id, currentPassword.srp_B, algo);
        }
        return null;
    }

    private boolean checkSecretValues(byte[] passwordBytes, TLRPC.TL_account_passwordSettings passwordSettings) {
        if (passwordSettings.secure_settings != null) {
            currentSecret = passwordSettings.secure_settings.secure_secret;
            byte[] passwordHash;
            if (passwordSettings.secure_settings.secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) {
                TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000 algo = (TLRPC.TL_securePasswordKdfAlgoPBKDF2HMACSHA512iter100000) passwordSettings.secure_settings.secure_algo;
                passwordHash = Utilities.computePBKDF2(passwordBytes, algo.salt);
            } else if (passwordSettings.secure_settings.secure_algo instanceof TLRPC.TL_securePasswordKdfAlgoSHA512) {
                TLRPC.TL_securePasswordKdfAlgoSHA512 algo = (TLRPC.TL_securePasswordKdfAlgoSHA512) passwordSettings.secure_settings.secure_algo;
                passwordHash = Utilities.computeSHA512(algo.salt, passwordBytes, algo.salt);
            } else {
                return false;
            }
            currentSecretId = passwordSettings.secure_settings.secure_secret_id;
            byte[] key = new byte[32];
            System.arraycopy(passwordHash, 0, key, 0, 32);
            byte[] iv = new byte[16];
            System.arraycopy(passwordHash, 32, iv, 0, 16);
            Utilities.aesCbcEncryptionByteArraySafe(currentSecret, key, iv, 0, currentSecret.length, 0, 0);
            if (!PassportActivity.checkSecret(passwordSettings.secure_settings.secure_secret, passwordSettings.secure_settings.secure_secret_id)) {
                TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
                req.password = getNewSrpPassword();
                req.new_settings = new TLRPC.TL_account_passwordInputSettings();
                req.new_settings.new_secure_settings = new TLRPC.TL_secureSecretSettings();
                req.new_settings.new_secure_settings.secure_secret = new byte[0];
                req.new_settings.new_secure_settings.secure_algo = new TLRPC.TL_securePasswordKdfAlgoUnknown();
                req.new_settings.new_secure_settings.secure_secret_id = 0;
                req.new_settings.flags |= 4;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                });
                currentSecret = null;
                currentSecretId = 0;
            }
        } else {
            currentSecret = null;
            currentSecretId = 0;
        }
        return true;
    }

    private void processDone() {
        if (!passwordEntered) {
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
                        Utilities.globalQueue.postRunnable(() -> {
                            boolean secretOk = checkSecretValues(oldPasswordBytes, (TLRPC.TL_account_passwordSettings) response);
                            AndroidUtilities.runOnUIThread(() -> {
                                if (delegate == null || !secretOk) {
                                    needHideProgress();
                                }
                                if (secretOk) {
                                    currentPasswordHash = x_bytes;
                                    passwordEntered = true;
                                    if (delegate != null) {
                                        AndroidUtilities.hideKeyboard(passwordEditText);
                                        delegate.didEnterPassword(getNewSrpPassword());
                                    } else {
                                        if (!TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern)) {
                                            TwoStepVerificationSetupActivity fragment = new TwoStepVerificationSetupActivity(currentAccount, TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM, currentPassword);
                                            fragment.setCurrentPasswordParams(currentPasswordHash, currentSecretId, currentSecret, true);
                                            presentFragment(fragment, true);
                                        } else {
                                            AndroidUtilities.hideKeyboard(passwordEditText);
                                            TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                                            fragment.passwordEntered = true;
                                            fragment.currentPasswordHash = currentPasswordHash;
                                            fragment.currentPassword = currentPassword;
                                            fragment.currentSecret = currentSecret;
                                            fragment.currentSecretId = currentSecretId;
                                            presentFragment(fragment, true);
                                        }
                                    }
                                } else {
                                    AlertsCreator.showUpdateAppAlert(getParentActivity(), LocaleController.getString("UpdateAppAlert", R.string.UpdateAppAlert), true);
                                }
                            });
                        });
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            if ("SRP_ID_INVALID".equals(error.text)) {
                                TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                                ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (error2 == null) {
                                        currentPassword = (TLRPC.TL_account_password) response2;
                                        initPasswordNewAlgo(currentPassword);
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetOrRemoveTwoStepPassword, currentPassword);
                                        processDone();
                                    }
                                }), ConnectionsManager.RequestFlagWithoutLogin);
                                return;
                            }
                            needHideProgress();
                            if ("PASSWORD_HASH_INVALID".equals(error.text)) {
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
        }
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

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0;
        }

        @Override
        public int getItemCount() {
            return loading || currentPassword == null ? 0 : rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == changePasswordRow) {
                        textCell.setText(LocaleController.getString("ChangePassword", R.string.ChangePassword), true);
                    } else if (position == setPasswordRow) {
                        textCell.setText(LocaleController.getString("SetAdditionalPassword", R.string.SetAdditionalPassword), true);
                    } else if (position == turnPasswordOffRow) {
                        textCell.setText(LocaleController.getString("TurnPasswordOff", R.string.TurnPasswordOff), true);
                    } else if (position == changeRecoveryEmailRow) {
                        textCell.setText(LocaleController.getString("ChangeRecoveryEmail", R.string.ChangeRecoveryEmail), false);
                    } else if (position == setRecoveryEmailRow) {
                        textCell.setText(LocaleController.getString("SetRecoveryEmail", R.string.SetRecoveryEmail), false);
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == setPasswordDetailRow) {
                        privacyCell.setText(LocaleController.getString("SetAdditionalPasswordInfo", R.string.SetAdditionalPasswordInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordEnabledDetailRow) {
                        privacyCell.setText(LocaleController.getString("EnabledPasswordText", R.string.EnabledPasswordText));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == setPasswordDetailRow || position == passwordEnabledDetailRow) {
                return 1;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, EditTextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText3));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(bottomTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        themeDescriptions.add(new ThemeDescription(bottomButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        return themeDescriptions;
    }
}
