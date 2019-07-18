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
import android.graphics.Typeface;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SRPHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EditTextSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.math.BigInteger;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class TwoStepVerificationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private TextView titleTextView;
    private TextView bottomTextView;
    private TextView bottomButton;
    private EditTextBoldCursor passwordEditText;
    private AlertDialog progressDialog;
    private EmptyTextProgressView emptyView;
    private ActionBarMenuItem doneItem;
    private ContextProgressView progressView;
    private ScrollView scrollView;
    private EditTextSettingsCell codeFieldCell;

    private AnimatorSet doneItemAnimation;

    private int type;
    private int passwordSetState;
    private int emailCodeLength = 6;
    private String firstPassword;
    private String hint;
    private String email;
    private boolean emailOnly;
    private boolean loading;
    private boolean destroyed;
    private boolean paused;
    private boolean waitingForEmail;
    private TLRPC.TL_account_password currentPassword;
    private boolean passwordEntered = true;
    private byte[] currentPasswordHash = new byte[0];
    private long currentSecretId;
    private byte[] currentSecret;
    private Runnable shortPollRunnable;
    private boolean closeAfterSet;

    private int setPasswordRow;
    private int setPasswordDetailRow;
    private int changePasswordRow;
    private int shadowRow;
    private int turnPasswordOffRow;
    private int setRecoveryEmailRow;
    private int changeRecoveryEmailRow;
    private int resendCodeRow;
    private int abortPasswordRow;
    private int passwordSetupDetailRow;
    private int passwordCodeFieldRow;
    private int passwordEnabledDetailRow;
    private int rowCount;

    private TwoStepVerificationActivityDelegate delegate;

    public interface TwoStepVerificationActivityDelegate {
        void didEnterPassword(TLRPC.InputCheckPasswordSRP password);
    }

    private final static int done_button = 1;

    public TwoStepVerificationActivity(int type) {
        super();
        this.type = type;
        if (type == 0) {
            loadPasswordInfo(false);
        }
    }

    public TwoStepVerificationActivity(int account, int type) {
        super();
        currentAccount = account;
        this.type = type;
        if (type == 0) {
            loadPasswordInfo(false);
        }
    }

    protected void setRecoveryParams(TLRPC.TL_account_password password) {
        currentPassword = password;
        passwordSetState = 4;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        if (type == 0) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didSetTwoStepPassword);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == 0) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didSetTwoStepPassword);
            if (shortPollRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
                shortPollRunnable = null;
            }
            destroyed = true;
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
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        progressView = new ContextProgressView(context, 1);
        progressView.setAlpha(0.0f);
        progressView.setScaleX(0.1f);
        progressView.setScaleY(0.1f);
        progressView.setVisibility(View.INVISIBLE);
        doneItem.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        passwordEditText.setCursorSize(AndroidUtilities.dp(20));
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
        linearLayout2.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bottomButton = new TextView(context);
        bottomButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        bottomButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bottomButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
        bottomButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
        bottomButton.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        linearLayout2.addView(bottomButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 40, 0, 40, 14));
        bottomButton.setOnClickListener(v -> {
            if (type == 0) {
                if (currentPassword.has_recovery) {
                    needShowProgress();
                    TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        needHideProgress();
                        if (error == null) {
                            final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setMessage(LocaleController.formatString("RestoreEmailSent", R.string.RestoreEmailSent, res.email_pattern));
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                                TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(currentAccount, 1);
                                fragment.currentPassword = currentPassword;
                                fragment.currentPassword.email_unconfirmed_pattern = res.email_pattern;
                                fragment.currentSecretId = currentSecretId;
                                fragment.currentSecret = currentSecret;
                                fragment.passwordSetState = 4;
                                presentFragment(fragment);
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
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    builder.setNegativeButton(LocaleController.getString("RestorePasswordResetAccount", R.string.RestorePasswordResetAccount), (dialog, which) -> Browser.openUrl(getParentActivity(), "https://telegram.org/deactivate?phone=" + UserConfig.getInstance(currentAccount).getClientPhone()));
                    builder.setTitle(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle));
                    builder.setMessage(LocaleController.getString("RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
                    showDialog(builder.create());
                }
            } else {
                if (passwordSetState == 4) {
                    showAlertWithText(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle), LocaleController.getString("RestoreEmailTroubleText", R.string.RestoreEmailTroubleText));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("YourEmailSkipWarningText", R.string.YourEmailSkipWarningText));
                    builder.setTitle(LocaleController.getString("YourEmailSkipWarning", R.string.YourEmailSkipWarning));
                    builder.setPositiveButton(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip), (dialogInterface, i) -> {
                        email = "";
                        setNewPassword(false);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            }
        });

        if (type == 0) {
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
                    TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(currentAccount, 1);
                    fragment.currentPasswordHash = currentPasswordHash;
                    fragment.currentPassword = currentPassword;
                    fragment.currentSecretId = currentSecretId;
                    fragment.currentSecret = currentSecret;
                    presentFragment(fragment);
                } else if (position == setRecoveryEmailRow || position == changeRecoveryEmailRow) {
                    TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(currentAccount, 1);
                    fragment.currentPasswordHash = currentPasswordHash;
                    fragment.currentPassword = currentPassword;
                    fragment.currentSecretId = currentSecretId;
                    fragment.currentSecret = currentSecret;
                    fragment.emailOnly = true;
                    fragment.passwordSetState = 3;
                    presentFragment(fragment);
                } else if (position == turnPasswordOffRow || position == abortPasswordRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    String text;
                    if (position == abortPasswordRow) {
                        if (currentPassword != null && currentPassword.has_password) {
                            text = LocaleController.getString("CancelEmailQuestion", R.string.CancelEmailQuestion);
                        } else {
                            text = LocaleController.getString("CancelPasswordQuestion", R.string.CancelPasswordQuestion);
                        }
                    } else {
                        text = LocaleController.getString("TurnPasswordOffQuestion", R.string.TurnPasswordOffQuestion);
                        if (currentPassword.has_secure_values) {
                            text += "\n\n" + LocaleController.getString("TurnPasswordOffPassport", R.string.TurnPasswordOffPassport);
                        }
                    }
                    builder.setMessage(text);
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> setNewPassword(true));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                } else if (position == resendCodeRow) {
                    TLRPC.TL_account_resendPasswordEmail req = new TLRPC.TL_account_resendPasswordEmail();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                    });
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("ResendCodeInfo", R.string.ResendCodeInfo));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    showDialog(builder.create());
                }
            });

            codeFieldCell = new EditTextSettingsCell(context);
            codeFieldCell.setTextAndHint("", LocaleController.getString("PasswordCode", R.string.PasswordCode), false);
            codeFieldCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            EditTextBoldCursor editText = codeFieldCell.getTextView();
            editText.setInputType(InputType.TYPE_CLASS_PHONE);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    processDone();
                    return true;
                }
                return false;
            });
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (emailCodeLength != 0 && s.length() == emailCodeLength) {
                        processDone();
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
        } else if (type == 1) {
            setPasswordSetState(passwordSetState);
        }

        if (passwordEntered && type != 1) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            fragmentView.setTag(Theme.key_windowBackgroundGray);
        } else {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            fragmentView.setTag(Theme.key_windowBackgroundWhite);
        }

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetTwoStepPassword) {
            if (args != null && args.length > 0 && args[0] != null) {
                currentPasswordHash = (byte[]) args[0];
                if (closeAfterSet) {
                    String email = (String) args[4];
                    if (TextUtils.isEmpty(email) && closeAfterSet) {
                        removeSelfFromStack();
                    }
                }
            }
            loadPasswordInfo(false);
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
        if (type == 1) {
            AndroidUtilities.runOnUIThread(() -> {
                if (passwordEditText != null) {
                    passwordEditText.requestFocus();
                    AndroidUtilities.showKeyboard(passwordEditText);
                }
            }, 200);
        } else if (type == 0 && codeFieldCell != null && codeFieldCell.getVisibility() == View.VISIBLE) {
            AndroidUtilities.runOnUIThread(() -> {
                if (codeFieldCell != null) {
                    codeFieldCell.getTextView().requestFocus();
                    AndroidUtilities.showKeyboard(codeFieldCell.getTextView());
                }
            }, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    public void setCloseAfterSet(boolean value) {
        closeAfterSet = value;
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

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            if (type == 1) {
                AndroidUtilities.showKeyboard(passwordEditText);
            } else if (type == 0 && codeFieldCell != null && codeFieldCell.getVisibility() == View.VISIBLE) {
                AndroidUtilities.showKeyboard(codeFieldCell.getTextView());
            }
        }
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

    private void loadPasswordInfo(final boolean silent) {
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
                if (!silent) {
                    passwordEntered = currentPasswordHash != null && currentPasswordHash.length > 0 || !currentPassword.has_password;
                }
                waitingForEmail = !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern);
                initPasswordNewAlgo(currentPassword);
                if (!paused && closeAfterSet && currentPassword.has_password) {
                    TLRPC.PasswordKdfAlgo pendingCurrentAlgo = currentPassword.current_algo;
                    TLRPC.SecurePasswordKdfAlgo pendingNewSecureAlgo = currentPassword.new_secure_algo;
                    byte[] pendingSecureRandom = currentPassword.secure_random;
                    String pendingEmail = currentPassword.has_recovery ? "1" : null;
                    String pendingHint = currentPassword.hint != null ? currentPassword.hint : "";

                    if (!waitingForEmail && pendingCurrentAlgo != null) {
                        NotificationCenter.getInstance(currentAccount).removeObserver(TwoStepVerificationActivity.this, NotificationCenter.didSetTwoStepPassword);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword, null, pendingCurrentAlgo, pendingNewSecureAlgo, pendingSecureRandom, pendingEmail, pendingHint, null, null);
                        finishFragment();
                    }
                }
            }
            if (type == 0 && !destroyed && shortPollRunnable == null && currentPassword != null && !TextUtils.isEmpty(currentPassword.email_unconfirmed_pattern)) {
                startShortpoll();
            }
            updateRows();
        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void startShortpoll() {
        if (shortPollRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
        }
        shortPollRunnable = () -> {
            if (shortPollRunnable == null) {
                return;
            }
            loadPasswordInfo(true);
            shortPollRunnable = null;
        };
        AndroidUtilities.runOnUIThread(shortPollRunnable, 5000);
    }

    private void setPasswordSetState(int state) {
        if (passwordEditText == null) {
            return;
        }
        passwordSetState = state;
        if (passwordSetState == 0) {
            actionBar.setTitle(LocaleController.getString("YourPassword", R.string.YourPassword));
            if (currentPassword.has_password) {
                titleTextView.setText(LocaleController.getString("PleaseEnterPassword", R.string.PleaseEnterPassword));
            } else {
                titleTextView.setText(LocaleController.getString("PleaseEnterFirstPassword", R.string.PleaseEnterFirstPassword));
            }
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            bottomTextView.setVisibility(View.INVISIBLE);
            bottomButton.setVisibility(View.INVISIBLE);
        } else if (passwordSetState == 1) {
            actionBar.setTitle(LocaleController.getString("YourPassword", R.string.YourPassword));
            titleTextView.setText(LocaleController.getString("PleaseReEnterPassword", R.string.PleaseReEnterPassword));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            bottomTextView.setVisibility(View.INVISIBLE);
            bottomButton.setVisibility(View.INVISIBLE);
        } else if (passwordSetState == 2) {
            actionBar.setTitle(LocaleController.getString("PasswordHint", R.string.PasswordHint));
            titleTextView.setText(LocaleController.getString("PasswordHintText", R.string.PasswordHintText));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordEditText.setTransformationMethod(null);
            bottomTextView.setVisibility(View.INVISIBLE);
            bottomButton.setVisibility(View.INVISIBLE);
        } else if (passwordSetState == 3) {
            actionBar.setTitle(LocaleController.getString("RecoveryEmail", R.string.RecoveryEmail));
            titleTextView.setText(LocaleController.getString("YourEmail", R.string.YourEmail));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            passwordEditText.setTransformationMethod(null);
            passwordEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            bottomTextView.setVisibility(View.VISIBLE);
            bottomButton.setVisibility(emailOnly ? View.INVISIBLE : View.VISIBLE);
        } else if (passwordSetState == 4) {
            actionBar.setTitle(LocaleController.getString("PasswordRecovery", R.string.PasswordRecovery));
            titleTextView.setText(LocaleController.getString("PasswordCode", R.string.PasswordCode));
            bottomTextView.setText(LocaleController.getString("RestoreEmailSentInfo", R.string.RestoreEmailSentInfo));
            bottomButton.setText(LocaleController.formatString("RestoreEmailTrouble", R.string.RestoreEmailTrouble, currentPassword.email_unconfirmed_pattern != null ? currentPassword.email_unconfirmed_pattern : ""));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            passwordEditText.setTransformationMethod(null);
            passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
            bottomTextView.setVisibility(View.VISIBLE);
            bottomButton.setVisibility(View.VISIBLE);
        }
        passwordEditText.setText("");
    }

    private void updateRows() {
        StringBuilder lastValue = new StringBuilder();
        lastValue.append(setPasswordRow);
        lastValue.append(setPasswordDetailRow);
        lastValue.append(changePasswordRow);
        lastValue.append(turnPasswordOffRow);
        lastValue.append(setRecoveryEmailRow);
        lastValue.append(changeRecoveryEmailRow);
        lastValue.append(resendCodeRow);
        lastValue.append(abortPasswordRow);
        lastValue.append(passwordSetupDetailRow);
        lastValue.append(passwordCodeFieldRow);
        lastValue.append(passwordEnabledDetailRow);
        lastValue.append(shadowRow);
        lastValue.append(rowCount);

        boolean wasCodeField = passwordCodeFieldRow != -1;
        
        rowCount = 0;
        setPasswordRow = -1;
        setPasswordDetailRow = -1;
        changePasswordRow = -1;
        turnPasswordOffRow = -1;
        setRecoveryEmailRow = -1;
        changeRecoveryEmailRow = -1;
        abortPasswordRow = -1;
        resendCodeRow = -1;
        passwordSetupDetailRow = -1;
        passwordCodeFieldRow = -1;
        passwordEnabledDetailRow = -1;
        shadowRow = -1;
        if (!loading && currentPassword != null) {
            if (waitingForEmail) {
                passwordCodeFieldRow = rowCount++;
                passwordSetupDetailRow = rowCount++;
                resendCodeRow = rowCount++;
                abortPasswordRow = rowCount++;
                shadowRow = rowCount++;
            } else if (currentPassword.has_password) {
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
        newValue.append(resendCodeRow);
        newValue.append(abortPasswordRow);
        newValue.append(passwordSetupDetailRow);
        newValue.append(passwordCodeFieldRow);
        newValue.append(passwordEnabledDetailRow);
        newValue.append(shadowRow);
        newValue.append(rowCount);
        if (listAdapter != null && !lastValue.toString().equals(newValue.toString())) {
            listAdapter.notifyDataSetChanged();
            if (passwordCodeFieldRow == -1 && getParentActivity() != null && wasCodeField) {
                AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                codeFieldCell.setText("", false);
            }
        }
        if (fragmentView != null) {
            if (loading || passwordEntered) {
                if (listView != null) {
                    listView.setVisibility(View.VISIBLE);
                    scrollView.setVisibility(View.INVISIBLE);
                    listView.setEmptyView(emptyView);
                }
                if (waitingForEmail && currentPassword != null) {
                    doneItem.setVisibility(View.VISIBLE);
                } else if (passwordEditText != null) {
                    doneItem.setVisibility(View.GONE);
                    passwordEditText.setVisibility(View.INVISIBLE);
                    titleTextView.setVisibility(View.INVISIBLE);
                    bottomTextView.setVisibility(View.INVISIBLE);
                    bottomButton.setVisibility(View.INVISIBLE);
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
                    bottomTextView.setVisibility(View.INVISIBLE);
                    bottomButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
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

    private void showDoneProgress(final boolean show) {
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        doneItemAnimation = new AnimatorSet();
        if (show) {
            progressView.setVisibility(View.VISIBLE);
            doneItem.setEnabled(false);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "alpha", 0.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 1.0f));
        } else {
            doneItem.getContentView().setVisibility(View.VISIBLE);
            doneItem.setEnabled(true);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(doneItem.getContentView(), "alpha", 1.0f));
        }
        doneItemAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    if (!show) {
                        progressView.setVisibility(View.INVISIBLE);
                    } else {
                        doneItem.getContentView().setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    doneItemAnimation = null;
                }
            }
        });
        doneItemAnimation.setDuration(150);
        doneItemAnimation.start();
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
                    loadPasswordInfo(false);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didRemoveTwoStepPassword);
                    updateRows();
                }
            }));
            return;
        }
        final String password = firstPassword;
        final TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
        if (currentPasswordHash == null || currentPasswordHash.length == 0) {
            req.password = new TLRPC.TL_inputCheckPasswordEmpty();
        }
        req.new_settings = new TLRPC.TL_account_passwordInputSettings();
        if (clear) {
            UserConfig.getInstance(currentAccount).resetSavedPassword();
            currentSecret = null;
            if (waitingForEmail) {
                req.new_settings.flags = 2;
                req.new_settings.email = "";
                req.password = new TLRPC.TL_inputCheckPasswordEmpty();
            } else {
                req.new_settings.flags = 3;
                req.new_settings.hint = "";
                req.new_settings.new_password_hash = new byte[0];
                req.new_settings.new_algo = new TLRPC.TL_passwordKdfAlgoUnknown();
                req.new_settings.email = "";
            }
        } else {
            if (hint == null && currentPassword != null) {
                hint = currentPassword.hint;
            }
            if (hint == null) {
                hint = "";
            }
            if (password != null) {
                req.new_settings.flags |= 1;
                req.new_settings.hint = hint;
                req.new_settings.new_algo = currentPassword.new_algo;
            }
            if (email.length() > 0) {
                req.new_settings.flags |= 2;
                req.new_settings.email = email.trim();
            }
        }
        needShowProgress();
        Utilities.globalQueue.postRunnable(() -> {
            if (req.password == null) {
                req.password = getNewSrpPassword();
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
                            initPasswordNewAlgo(currentPassword);
                            setNewPassword(clear);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                    return;
                }
                needHideProgress();
                if (error == null && response instanceof TLRPC.TL_boolTrue) {
                    if (clear) {
                        currentPassword = null;
                        currentPasswordHash = new byte[0];
                        loadPasswordInfo(false);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didRemoveTwoStepPassword);
                        updateRows();
                    } else {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword, newPasswordHash, req.new_settings.new_algo, currentPassword.new_secure_algo, currentPassword.secure_random, email, hint, null, firstPassword);
                            finishFragment();
                        });
                        if (password == null && currentPassword != null && currentPassword.has_password) {
                            builder.setMessage(LocaleController.getString("YourEmailSuccessText", R.string.YourEmailSuccessText));
                        } else {
                            builder.setMessage(LocaleController.getString("YourPasswordSuccessText", R.string.YourPasswordSuccessText));
                        }
                        builder.setTitle(LocaleController.getString("YourPasswordSuccess", R.string.YourPasswordSuccess));
                        Dialog dialog = showDialog(builder.create());
                        if (dialog != null) {
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setCancelable(false);
                        }
                    }
                } else if (error != null) {
                    if ("EMAIL_UNCONFIRMED".equals(error.text) || error.text.startsWith("EMAIL_UNCONFIRMED_")) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword);
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                            if (closeAfterSet) {
                                TwoStepVerificationActivity activity = new TwoStepVerificationActivity(currentAccount, 0);
                                activity.setCloseAfterSet(true);
                                parentLayout.addFragmentToStack(activity, parentLayout.fragmentsStack.size() - 1);
                            }
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword, newPasswordHash, req.new_settings.new_algo, currentPassword.new_secure_algo, currentPassword.secure_random, email, hint, email, firstPassword);
                            finishFragment();
                        });
                        builder.setMessage(LocaleController.getString("YourEmailAlmostThereText", R.string.YourEmailAlmostThereText));
                        builder.setTitle(LocaleController.getString("YourEmailAlmostThere", R.string.YourEmailAlmostThere));
                        Dialog dialog = showDialog(builder.create());
                        if (dialog != null) {
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setCancelable(false);
                        }
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

                        req.new_settings.new_secure_settings = new TLRPC.TL_secureSecretSettings();
                        req.new_settings.new_secure_settings.secure_algo = newAlgo;
                        req.new_settings.new_secure_settings.secure_secret = encryptedSecret;
                        req.new_settings.new_secure_settings.secure_secret_id = currentSecretId;
                        req.new_settings.flags |= 4;
                    }
                }

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
            } else {
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
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

    private static byte[] getBigIntegerBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(bytes, 1, correctedAuth, 0, 256);
            return correctedAuth;
        }
        return bytes;
    }

    private void processDone() {
        if (type == 0) {
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
                                        AndroidUtilities.hideKeyboard(passwordEditText);
                                        if (delegate != null) {
                                            delegate.didEnterPassword(getNewSrpPassword());
                                        } else {
                                            updateRows();
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
            } else if (waitingForEmail && currentPassword != null) {
                if (codeFieldCell.length() == 0) {
                    onFieldError(codeFieldCell.getTextView(), false);
                    return;
                }
                sendEmailConfirm(codeFieldCell.getText());
                showDoneProgress(true);
            }
        } else if (type == 1) {
            if (passwordSetState == 0) {
                if (passwordEditText.getText().length() == 0) {
                    onFieldError(passwordEditText, false);
                    return;
                }
                titleTextView.setText(LocaleController.getString("ReEnterYourPasscode", R.string.ReEnterYourPasscode));
                firstPassword = passwordEditText.getText().toString();
                setPasswordSetState(1);
            } else if (passwordSetState == 1) {
                if (!firstPassword.equals(passwordEditText.getText().toString())) {
                    try {
                        Toast.makeText(getParentActivity(), LocaleController.getString("PasswordDoNotMatch", R.string.PasswordDoNotMatch), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    onFieldError(passwordEditText, true);
                    return;
                }
                setPasswordSetState(2);
            } else if (passwordSetState == 2) {
                hint = passwordEditText.getText().toString();
                if (hint.toLowerCase().equals(firstPassword.toLowerCase())) {
                    try {
                        Toast.makeText(getParentActivity(), LocaleController.getString("PasswordAsHintError", R.string.PasswordAsHintError), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    onFieldError(passwordEditText, false);
                    return;
                }
                if (!currentPassword.has_recovery) {
                    setPasswordSetState(3);
                } else {
                    email = "";
                    setNewPassword(false);
                }
            } else if (passwordSetState == 3) {
                email = passwordEditText.getText().toString();
                if (!isValidEmail(email)) {
                    onFieldError(passwordEditText, false);
                    return;
                }
                setNewPassword(false);
            } else if (passwordSetState == 4) {
                String code = passwordEditText.getText().toString();
                if (code.length() == 0) {
                    onFieldError(passwordEditText, false);
                    return;
                }
                TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
                req.code = code;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword);
                            finishFragment();
                        });
                        builder.setMessage(LocaleController.getString("PasswordReset", R.string.PasswordReset));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        Dialog dialog = showDialog(builder.create());
                        if (dialog != null) {
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setCancelable(false);
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
            }
        }
    }

    private void sendEmailConfirm(String code) {
        TLRPC.TL_account_confirmPasswordEmail req = new TLRPC.TL_account_confirmPasswordEmail();
        req.code = code;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (type == 0 && waitingForEmail) {
                showDoneProgress(false);
            }
            if (error == null) {
                if (getParentActivity() == null) {
                    return;
                }
                if (shortPollRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(shortPollRunnable);
                    shortPollRunnable = null;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                    if (type == 0) {
                        loadPasswordInfo(false);
                        doneItem.setVisibility(View.GONE);
                    } else {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didSetTwoStepPassword, currentPasswordHash, currentPassword.new_algo, currentPassword.new_secure_algo, currentPassword.secure_random, email, hint, null, firstPassword);
                        finishFragment();
                    }
                });
                if (currentPassword != null && currentPassword.has_password) {
                    builder.setMessage(LocaleController.getString("YourEmailSuccessText", R.string.YourEmailSuccessText));
                } else {
                    builder.setMessage(LocaleController.getString("YourPasswordSuccessText", R.string.YourPasswordSuccessText));
                }
                builder.setTitle(LocaleController.getString("YourPasswordSuccess", R.string.YourPasswordSuccess));
                Dialog dialog = showDialog(builder.create());
                if (dialog != null) {
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setCancelable(false);
                }
            } else {
                if (error.text.startsWith("CODE_INVALID")) {
                    onFieldError(waitingForEmail ? codeFieldCell.getTextView() : passwordEditText, true);
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
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                default:
                    view = codeFieldCell;
                    if (view.getParent() != null) {
                        ((ViewGroup) view.getParent()).removeView(view);
                    }
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
                        textCell.setText(LocaleController.getString("ChangeRecoveryEmail", R.string.ChangeRecoveryEmail), abortPasswordRow != -1);
                    } else if (position == resendCodeRow) {
                        textCell.setText(LocaleController.getString("ResendCode", R.string.ResendCode), true);
                    } else if (position == setRecoveryEmailRow) {
                        textCell.setText(LocaleController.getString("SetRecoveryEmail", R.string.SetRecoveryEmail), false);
                    } else if (position == abortPasswordRow) {
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText3);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
                        if (currentPassword != null && currentPassword.has_password) {
                            textCell.setText(LocaleController.getString("AbortEmail", R.string.AbortEmail), false);
                        } else {
                            textCell.setText(LocaleController.getString("AbortPassword", R.string.AbortPassword), false);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == setPasswordDetailRow) {
                        privacyCell.setText(LocaleController.getString("SetAdditionalPasswordInfo", R.string.SetAdditionalPasswordInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == shadowRow) {
                        privacyCell.setText("");
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordSetupDetailRow) {
                        if (currentPassword != null && currentPassword.has_password) {
                            privacyCell.setText(LocaleController.formatString("EmailPasswordConfirmText3", R.string.EmailPasswordConfirmText3, currentPassword.email_unconfirmed_pattern != null ? currentPassword.email_unconfirmed_pattern : ""));
                        } else {
                            privacyCell.setText(LocaleController.formatString("EmailPasswordConfirmText2", R.string.EmailPasswordConfirmText2, currentPassword.email_unconfirmed_pattern != null ? currentPassword.email_unconfirmed_pattern : ""));
                        }
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == passwordEnabledDetailRow) {
                        privacyCell.setText(LocaleController.getString("EnabledPasswordText", R.string.EnabledPasswordText));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == setPasswordDetailRow || position == shadowRow || position == passwordSetupDetailRow || position == passwordEnabledDetailRow) {
                return 1;
            } else if (position == passwordCodeFieldRow) {
                return 2;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, EditTextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText3),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
                new ThemeDescription(bottomTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6),
                new ThemeDescription(bottomButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(passwordEditText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),
        };
    }
}
