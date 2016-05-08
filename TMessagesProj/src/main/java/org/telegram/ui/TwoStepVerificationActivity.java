/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

public class TwoStepVerificationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private ListView listView;
    private TextView titleTextView;
    private TextView bottomTextView;
    private TextView bottomButton;
    private EditText passwordEditText;
    private ProgressDialog progressDialog;
    private FrameLayout progressView;
    private ActionBarMenuItem doneItem;
    private ScrollView scrollView;

    private int type;
    private int passwordSetState;
    private String firstPassword;
    private String hint;
    private String email;
    private boolean emailOnly;
    private boolean loading;
    private boolean destroyed;
    private boolean waitingForEmail;
    private TLRPC.account_Password currentPassword;
    private boolean passwordEntered = true;
    private byte[] currentPasswordHash = new byte[0];
    private Runnable shortPollRunnable;

    private int setPasswordRow;
    private int setPasswordDetailRow;
    private int changePasswordRow;
    private int shadowRow;
    private int turnPasswordOffRow;
    private int setRecoveryEmailRow;
    private int changeRecoveryEmailRow;
    private int abortPasswordRow;
    private int passwordSetupDetailRow;
    private int passwordEnabledDetailRow;
    private int passwordEmailVerifyDetailRow;
    private int rowCount;

    private final static int done_button = 1;

    public TwoStepVerificationActivity(int type) {
        super();
        this.type = type;
        if (type == 0) {
            loadPasswordInfo(false);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        if (type == 0) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.didSetTwoStepPassword);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (type == 0) {
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didSetTwoStepPassword);
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
                FileLog.e("tmessages", e);
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
        frameLayout.setBackgroundColor(0xfff0f0f0);

        ActionBarMenu menu = actionBar.createMenu();
        doneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        frameLayout.addView(scrollView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) scrollView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        scrollView.setLayoutParams(layoutParams);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(linearLayout);
        ScrollView.LayoutParams layoutParams2 = (ScrollView.LayoutParams) linearLayout.getLayoutParams();
        layoutParams2.width = ScrollView.LayoutParams.MATCH_PARENT;
        layoutParams2.height = ScrollView.LayoutParams.WRAP_CONTENT;
        linearLayout.setLayoutParams(layoutParams2);

        titleTextView = new TextView(context);
        titleTextView.setTextColor(0xff757575);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(titleTextView);
        LinearLayout.LayoutParams layoutParams3 = (LinearLayout.LayoutParams) titleTextView.getLayoutParams();
        layoutParams3.width = LayoutHelper.WRAP_CONTENT;
        layoutParams3.height = LayoutHelper.WRAP_CONTENT;
        layoutParams3.gravity = Gravity.CENTER_HORIZONTAL;
        layoutParams3.topMargin = AndroidUtilities.dp(38);
        titleTextView.setLayoutParams(layoutParams3);

        passwordEditText = new EditText(context);
        passwordEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        passwordEditText.setTextColor(0xff000000);
        passwordEditText.setMaxLines(1);
        passwordEditText.setLines(1);
        passwordEditText.setGravity(Gravity.CENTER_HORIZONTAL);
        passwordEditText.setSingleLine(true);
        passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordEditText.setTypeface(Typeface.DEFAULT);
        AndroidUtilities.clearCursorDrawable(passwordEditText);
        linearLayout.addView(passwordEditText);
        layoutParams3 = (LinearLayout.LayoutParams) passwordEditText.getLayoutParams();
        layoutParams3.topMargin = AndroidUtilities.dp(32);
        layoutParams3.height = AndroidUtilities.dp(36);
        layoutParams3.leftMargin = AndroidUtilities.dp(40);
        layoutParams3.rightMargin = AndroidUtilities.dp(40);
        layoutParams3.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams3.width = LayoutHelper.MATCH_PARENT;
        passwordEditText.setLayoutParams(layoutParams3);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT || i == EditorInfo.IME_ACTION_DONE) {
                    processDone();
                    return true;
                }
                return false;
            }
        });
        if (android.os.Build.VERSION.SDK_INT < 11) {
            passwordEditText.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                    menu.clear();
                }
            });
        } else {
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
        }

        bottomTextView = new TextView(context);
        bottomTextView.setTextColor(0xff757575);
        bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bottomTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        bottomTextView.setText(LocaleController.getString("YourEmailInfo", R.string.YourEmailInfo));
        linearLayout.addView(bottomTextView);
        layoutParams3 = (LinearLayout.LayoutParams) bottomTextView.getLayoutParams();
        layoutParams3.width = LayoutHelper.WRAP_CONTENT;
        layoutParams3.height = LayoutHelper.WRAP_CONTENT;
        layoutParams3.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        layoutParams3.topMargin = AndroidUtilities.dp(30);
        layoutParams3.leftMargin = AndroidUtilities.dp(40);
        layoutParams3.rightMargin = AndroidUtilities.dp(40);
        bottomTextView.setLayoutParams(layoutParams3);

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        linearLayout.addView(linearLayout2);
        layoutParams3 = (LinearLayout.LayoutParams) linearLayout2.getLayoutParams();
        layoutParams3.width = LayoutHelper.MATCH_PARENT;
        layoutParams3.height = LayoutHelper.MATCH_PARENT;
        linearLayout2.setLayoutParams(layoutParams3);

        bottomButton = new TextView(context);
        bottomButton.setTextColor(0xff4d83b3);
        bottomButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bottomButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
        bottomButton.setText(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip));
        bottomButton.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        linearLayout2.addView(bottomButton);
        layoutParams3 = (LinearLayout.LayoutParams) bottomButton.getLayoutParams();
        layoutParams3.width = LayoutHelper.WRAP_CONTENT;
        layoutParams3.height = LayoutHelper.WRAP_CONTENT;
        layoutParams3.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM;
        layoutParams3.bottomMargin = AndroidUtilities.dp(14);
        layoutParams3.leftMargin = AndroidUtilities.dp(40);
        layoutParams3.rightMargin = AndroidUtilities.dp(40);
        bottomButton.setLayoutParams(layoutParams3);
        bottomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (type == 0) {
                    if (currentPassword.has_recovery) {
                        needShowProgress();
                        TLRPC.TL_auth_requestPasswordRecovery req = new TLRPC.TL_auth_requestPasswordRecovery();
                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                            @Override
                            public void run(final TLObject response, final TLRPC.TL_error error) {
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        needHideProgress();
                                        if (error == null) {
                                            final TLRPC.TL_auth_passwordRecovery res = (TLRPC.TL_auth_passwordRecovery) response;
                                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                            builder.setMessage(LocaleController.formatString("RestoreEmailSent", R.string.RestoreEmailSent, res.email_pattern));
                                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(1);
                                                    fragment.currentPassword = currentPassword;
                                                    fragment.currentPassword.email_unconfirmed_pattern = res.email_pattern;
                                                    fragment.passwordSetState = 4;
                                                    presentFragment(fragment);
                                                }
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
                                    }
                                });
                            }
                        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        showAlertWithText(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle), LocaleController.getString("RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
                    }
                } else {
                    if (passwordSetState == 4) {
                        showAlertWithText(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle), LocaleController.getString("RestoreEmailTroubleText", R.string.RestoreEmailTroubleText));
                    } else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("YourEmailSkipWarningText", R.string.YourEmailSkipWarningText));
                        builder.setTitle(LocaleController.getString("YourEmailSkipWarning", R.string.YourEmailSkipWarning));
                        builder.setPositiveButton(LocaleController.getString("YourEmailSkip", R.string.YourEmailSkip), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                email = "";
                                setNewPassword(false);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                }
            }
        });

        if (type == 0) {
            progressView = new FrameLayout(context);
            frameLayout.addView(progressView);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.height = LayoutHelper.MATCH_PARENT;
            progressView.setLayoutParams(layoutParams);
            progressView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            ProgressBar progressBar = new ProgressBar(context);
            progressView.addView(progressBar);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER;
            progressView.setLayoutParams(layoutParams);

            listView = new ListView(context);
            listView.setDivider(null);
            listView.setEmptyView(progressView);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDrawSelectorOnTop(true);
            frameLayout.addView(listView);
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.height = LayoutHelper.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(listAdapter = new ListAdapter(context));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (i == setPasswordRow || i == changePasswordRow) {
                        TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(1);
                        fragment.currentPasswordHash = currentPasswordHash;
                        fragment.currentPassword = currentPassword;
                        presentFragment(fragment);
                    } else if (i == setRecoveryEmailRow || i == changeRecoveryEmailRow) {
                        TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(1);
                        fragment.currentPasswordHash = currentPasswordHash;
                        fragment.currentPassword = currentPassword;
                        fragment.emailOnly = true;
                        fragment.passwordSetState = 3;
                        presentFragment(fragment);
                    } else if (i == turnPasswordOffRow || i == abortPasswordRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("TurnPasswordOffQuestion", R.string.TurnPasswordOffQuestion));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                setNewPassword(true);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                }
            });

            updateRows();

            actionBar.setTitle(LocaleController.getString("TwoStepVerification", R.string.TwoStepVerification));
            titleTextView.setText(LocaleController.getString("PleaseEnterCurrentPassword", R.string.PleaseEnterCurrentPassword));
        } else if (type == 1) {
            setPasswordSetState(passwordSetState);
        }

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.didSetTwoStepPassword) {
            if (args != null && args.length > 0 && args[0] != null) {
                currentPasswordHash = (byte[]) args[0];
            }
            loadPasswordInfo(false);
            updateRows();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (type == 1) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (passwordEditText != null) {
                        passwordEditText.requestFocus();
                        AndroidUtilities.showKeyboard(passwordEditText);
                    }
                }
            }, 200);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && type == 1) {
            AndroidUtilities.showKeyboard(passwordEditText);
        }
    }

    private void loadPasswordInfo(final boolean silent) {
        if (!silent) {
            loading = true;
        }
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        if (error == null) {
                            if (!silent) {
                                passwordEntered = currentPassword != null || response instanceof TLRPC.TL_account_noPassword;
                            }
                            currentPassword = (TLRPC.account_Password) response;
                            waitingForEmail = currentPassword.email_unconfirmed_pattern.length() > 0;
                            byte[] salt = new byte[currentPassword.new_salt.length + 8];
                            Utilities.random.nextBytes(salt);
                            System.arraycopy(currentPassword.new_salt, 0, salt, 0, currentPassword.new_salt.length);
                            currentPassword.new_salt = salt;
                        }
                        if (type == 0 && !destroyed && shortPollRunnable == null) {
                            shortPollRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (shortPollRunnable == null) {
                                        return;
                                    }
                                    loadPasswordInfo(true);
                                    shortPollRunnable = null;
                                }
                            };
                            AndroidUtilities.runOnUIThread(shortPollRunnable, 5000);
                        }
                        updateRows();
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void setPasswordSetState(int state) {
        if (passwordEditText == null) {
            return;
        }
        passwordSetState = state;
        if (passwordSetState == 0) {
            actionBar.setTitle(LocaleController.getString("YourPassword", R.string.YourPassword));
            if (currentPassword instanceof TLRPC.TL_account_noPassword) {
                titleTextView.setText(LocaleController.getString("PleaseEnterFirstPassword", R.string.PleaseEnterFirstPassword));
            } else {
                titleTextView.setText(LocaleController.getString("PleaseEnterPassword", R.string.PleaseEnterPassword));
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
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            passwordEditText.setTransformationMethod(null);
            passwordEditText.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            bottomTextView.setVisibility(View.VISIBLE);
            bottomButton.setVisibility(emailOnly ? View.INVISIBLE : View.VISIBLE);
        } else if (passwordSetState == 4) {
            actionBar.setTitle(LocaleController.getString("PasswordRecovery", R.string.PasswordRecovery));
            titleTextView.setText(LocaleController.getString("PasswordCode", R.string.PasswordCode));
            bottomTextView.setText(LocaleController.getString("RestoreEmailSentInfo", R.string.RestoreEmailSentInfo));
            bottomButton.setText(LocaleController.formatString("RestoreEmailTrouble", R.string.RestoreEmailTrouble, currentPassword.email_unconfirmed_pattern));
            passwordEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            passwordEditText.setTransformationMethod(null);
            passwordEditText.setInputType(InputType.TYPE_CLASS_PHONE);
            bottomTextView.setVisibility(View.VISIBLE);
            bottomButton.setVisibility(View.VISIBLE);
        }
        passwordEditText.setText("");
    }

    private void updateRows() {
        rowCount = 0;
        setPasswordRow = -1;
        setPasswordDetailRow = -1;
        changePasswordRow = -1;
        turnPasswordOffRow = -1;
        setRecoveryEmailRow = -1;
        changeRecoveryEmailRow = -1;
        abortPasswordRow = -1;
        passwordSetupDetailRow = -1;
        passwordEnabledDetailRow = -1;
        passwordEmailVerifyDetailRow = -1;
        shadowRow = -1;
        if (!loading && currentPassword != null) {
            if (currentPassword instanceof TLRPC.TL_account_noPassword) {
                if (waitingForEmail) {
                    passwordSetupDetailRow = rowCount++;
                    abortPasswordRow = rowCount++;
                    shadowRow = rowCount++;
                } else {
                    setPasswordRow = rowCount++;
                    setPasswordDetailRow = rowCount++;
                }
            } else if (currentPassword instanceof TLRPC.TL_account_password) {
                changePasswordRow = rowCount++;
                turnPasswordOffRow = rowCount++;
                if (currentPassword.has_recovery) {
                    changeRecoveryEmailRow = rowCount++;
                } else {
                    setRecoveryEmailRow = rowCount++;
                }
                if (waitingForEmail) {
                    passwordEmailVerifyDetailRow = rowCount++;
                } else {
                    passwordEnabledDetailRow = rowCount++;
                }
            }
        }

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (passwordEntered) {
            if (listView != null) {
                listView.setVisibility(View.VISIBLE);
                scrollView.setVisibility(View.INVISIBLE);
                progressView.setVisibility(View.VISIBLE);
                listView.setEmptyView(progressView);
            }
            if (passwordEditText != null) {
                doneItem.setVisibility(View.GONE);
                passwordEditText.setVisibility(View.INVISIBLE);
                titleTextView.setVisibility(View.INVISIBLE);
                bottomTextView.setVisibility(View.INVISIBLE);
                bottomButton.setVisibility(View.INVISIBLE);
            }
        } else {
            if (listView != null) {
                listView.setEmptyView(null);
                listView.setVisibility(View.INVISIBLE);
                scrollView.setVisibility(View.VISIBLE);
                progressView.setVisibility(View.INVISIBLE);
            }
            if (passwordEditText != null) {
                doneItem.setVisibility(View.VISIBLE);
                passwordEditText.setVisibility(View.VISIBLE);
                titleTextView.setVisibility(View.VISIBLE);
                bottomButton.setVisibility(View.VISIBLE);
                bottomTextView.setVisibility(View.INVISIBLE);
                bottomButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
                if (currentPassword.hint != null && currentPassword.hint.length() > 0) {
                    passwordEditText.setHint(currentPassword.hint);
                } else {
                    passwordEditText.setHint("");
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (passwordEditText != null) {
                            passwordEditText.requestFocus();
                            AndroidUtilities.showKeyboard(passwordEditText);
                        }
                    }
                }, 200);
            }
        }
    }

    private void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new ProgressDialog(getParentActivity());
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void needHideProgress() {
        if (progressDialog == null) {
            return;
        }
        try {
            progressDialog.dismiss();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        progressDialog = null;
    }

    private boolean isValidEmail(String text) {
        if (text == null || text.length() < 3) {
            return false;
        }
        int dot = text.lastIndexOf('.');
        int dog = text.lastIndexOf('@');
        return !(dot < 0 || dog < 0 || dot < dog);
    }

    private void showAlertWithText(String title, String text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setTitle(title);
        builder.setMessage(text);
        showDialog(builder.create());
    }

    private void setNewPassword(final boolean clear) {
        final TLRPC.TL_account_updatePasswordSettings req = new TLRPC.TL_account_updatePasswordSettings();
        req.current_password_hash = currentPasswordHash;
        req.new_settings = new TLRPC.TL_account_passwordInputSettings();
        if (clear) {
            if (waitingForEmail && currentPassword instanceof TLRPC.TL_account_noPassword) {
                req.new_settings.flags = 2;
                req.new_settings.email = "";
                req.current_password_hash = new byte[0];
            } else {
                req.new_settings.flags = 3;
                req.new_settings.hint = "";
                req.new_settings.new_password_hash = new byte[0];
                req.new_settings.new_salt = new byte[0];
                req.new_settings.email = "";
            }
        } else {
            if (firstPassword != null && firstPassword.length() > 0) {
                byte[] newPasswordBytes = null;
                try {
                    newPasswordBytes = firstPassword.getBytes("UTF-8");
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }

                byte[] new_salt = currentPassword.new_salt;
                byte[] hash = new byte[new_salt.length * 2 + newPasswordBytes.length];
                System.arraycopy(new_salt, 0, hash, 0, new_salt.length);
                System.arraycopy(newPasswordBytes, 0, hash, new_salt.length, newPasswordBytes.length);
                System.arraycopy(new_salt, 0, hash, hash.length - new_salt.length, new_salt.length);
                req.new_settings.flags |= 1;
                req.new_settings.hint = hint;
                req.new_settings.new_password_hash = Utilities.computeSHA256(hash, 0, hash.length);
                req.new_settings.new_salt = new_salt;
            }
            if (email.length() > 0) {
                req.new_settings.flags |= 2;
                req.new_settings.email = email;
            }
        }
        needShowProgress();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        needHideProgress();
                        if (error == null && response instanceof TLRPC.TL_boolTrue) {
                            if (clear) {
                                currentPassword = null;
                                currentPasswordHash = new byte[0];
                                loadPasswordInfo(false);
                                updateRows();
                            } else {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetTwoStepPassword, (Object) req.new_settings.new_password_hash);
                                        finishFragment();
                                    }
                                });
                                builder.setMessage(LocaleController.getString("YourPasswordSuccessText", R.string.YourPasswordSuccessText));
                                builder.setTitle(LocaleController.getString("YourPasswordSuccess", R.string.YourPasswordSuccess));
                                Dialog dialog = showDialog(builder.create());
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                            }
                        } else if (error != null) {
                            if (error.text.equals("EMAIL_UNCONFIRMED")) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetTwoStepPassword, (Object) req.new_settings.new_password_hash);
                                        finishFragment();
                                    }
                                });
                                builder.setMessage(LocaleController.getString("YourEmailAlmostThereText", R.string.YourEmailAlmostThereText));
                                builder.setTitle(LocaleController.getString("YourEmailAlmostThere", R.string.YourEmailAlmostThere));
                                Dialog dialog = showDialog(builder.create());
                                if (dialog != null) {
                                    dialog.setCanceledOnTouchOutside(false);
                                    dialog.setCancelable(false);
                                }
                            } else {
                                if (error.text.equals("EMAIL_INVALID")) {
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
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void processDone() {
        if (type == 0) {
            if (!passwordEntered) {
                String oldPassword = passwordEditText.getText().toString();
                if (oldPassword.length() == 0) {
                    onPasscodeError(false);
                    return;
                }
                byte[] oldPasswordBytes = null;
                try {
                    oldPasswordBytes = oldPassword.getBytes("UTF-8");
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }

                needShowProgress();
                byte[] hash = new byte[currentPassword.current_salt.length * 2 + oldPasswordBytes.length];
                System.arraycopy(currentPassword.current_salt, 0, hash, 0, currentPassword.current_salt.length);
                System.arraycopy(oldPasswordBytes, 0, hash, currentPassword.current_salt.length, oldPasswordBytes.length);
                System.arraycopy(currentPassword.current_salt, 0, hash, hash.length - currentPassword.current_salt.length, currentPassword.current_salt.length);

                final TLRPC.TL_account_getPasswordSettings req = new TLRPC.TL_account_getPasswordSettings();
                req.current_password_hash = Utilities.computeSHA256(hash, 0, hash.length);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                needHideProgress();
                                if (error == null) {
                                    currentPasswordHash = req.current_password_hash;
                                    passwordEntered = true;
                                    AndroidUtilities.hideKeyboard(passwordEditText);
                                    updateRows();
                                } else {
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
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                    } else {
                                        showAlertWithText(LocaleController.getString("AppName", R.string.AppName), error.text);
                                    }
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            }
        } else if (type == 1) {
            if (passwordSetState == 0) {
                if (passwordEditText.getText().length() == 0) {
                    onPasscodeError(false);
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
                        FileLog.e("tmessages", e);
                    }
                    onPasscodeError(true);
                    return;
                }
                setPasswordSetState(2);
            } else if (passwordSetState == 2) {
                hint = passwordEditText.getText().toString();
                if (hint.toLowerCase().equals(firstPassword.toLowerCase())) {
                    try {
                        Toast.makeText(getParentActivity(), LocaleController.getString("PasswordAsHintError", R.string.PasswordAsHintError), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    onPasscodeError(false);
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
                    onPasscodeError(false);
                    return;
                }
                setNewPassword(false);
            } else if (passwordSetState == 4) {
                String code = passwordEditText.getText().toString();
                if (code.length() == 0) {
                    onPasscodeError(false);
                    return;
                }
                TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
                req.code = code;
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (error == null) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetTwoStepPassword);
                                            finishFragment();
                                        }
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
                                        onPasscodeError(true);
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
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
            }
        }
    }

    private void onPasscodeError(boolean clear) {
        if (getParentActivity() == null) {
            return;
        }
        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(200);
        }
        if (clear) {
            passwordEditText.setText("");
        }
        AndroidUtilities.shakeView(titleTextView, 2, 0);
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return i != setPasswordDetailRow && i != shadowRow && i != passwordSetupDetailRow && i != passwordEmailVerifyDetailRow && i != passwordEnabledDetailRow;
        }

        @Override
        public int getCount() {
            return loading || currentPassword == null ? 0 : rowCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int viewType = getItemViewType(i);
            if (viewType == 0) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                textCell.setTextColor(0xff212121);
                if (i == changePasswordRow) {
                    textCell.setText(LocaleController.getString("ChangePassword", R.string.ChangePassword), true);
                } else if (i == setPasswordRow) {
                    textCell.setText(LocaleController.getString("SetAdditionalPassword", R.string.SetAdditionalPassword), true);
                } else if (i == turnPasswordOffRow) {
                    textCell.setText(LocaleController.getString("TurnPasswordOff", R.string.TurnPasswordOff), true);
                } else if (i == changeRecoveryEmailRow) {
                    textCell.setText(LocaleController.getString("ChangeRecoveryEmail", R.string.ChangeRecoveryEmail), abortPasswordRow != -1);
                } else if (i == setRecoveryEmailRow) {
                    textCell.setText(LocaleController.getString("SetRecoveryEmail", R.string.SetRecoveryEmail), false);
                } else if (i == abortPasswordRow) {
                    textCell.setTextColor(0xffd24949);
                    textCell.setText(LocaleController.getString("AbortPassword", R.string.AbortPassword), false);
                }
            } else if (viewType == 1) {
                if (view == null) {
                    view = new TextInfoPrivacyCell(mContext);
                }
                if (i == setPasswordDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("SetAdditionalPasswordInfo", R.string.SetAdditionalPasswordInfo));
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                } else if (i == shadowRow) {
                    ((TextInfoPrivacyCell) view).setText("");
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                } else if (i == passwordSetupDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.formatString("EmailPasswordConfirmText", R.string.EmailPasswordConfirmText, currentPassword.email_unconfirmed_pattern));
                    view.setBackgroundResource(R.drawable.greydivider_top);
                } else if (i == passwordEnabledDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("EnabledPasswordText", R.string.EnabledPasswordText));
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                } else if (i == passwordEmailVerifyDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.formatString("PendingEmailText", R.string.PendingEmailText, currentPassword.email_unconfirmed_pattern));
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == setPasswordDetailRow || i == shadowRow || i == passwordSetupDetailRow || i == passwordEnabledDetailRow || i == passwordEmailVerifyDetailRow) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return loading || currentPassword == null;
        }
    }
}
