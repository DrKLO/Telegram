/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;

public class ChangeUsernameActivity extends BaseFragment {

    private EditText firstNameField;
    private View doneButton;
    private TextView checkTextView;
    private long checkReqId = 0;
    private String lastCheckName = null;
    private Runnable checkRunnable = null;
    private boolean lastNameAvailable = false;

    private final static int done_button = 1;

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("Username", R.string.Username));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        saveName();
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

            TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
            if (user == null) {
                user = UserConfig.getCurrentUser();
            }

            fragmentView = new LinearLayout(getParentActivity());
            fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ((LinearLayout) fragmentView).setOrientation(LinearLayout.VERTICAL);
            fragmentView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            firstNameField = new EditText(getParentActivity());
            firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            firstNameField.setHintTextColor(0xff979797);
            firstNameField.setTextColor(0xff212121);
            firstNameField.setMaxLines(1);
            firstNameField.setLines(1);
            firstNameField.setPadding(0, 0, 0, 0);
            firstNameField.setSingleLine(true);
            firstNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
            firstNameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
            firstNameField.setHint(LocaleController.getString("UsernamePlaceholder", R.string.UsernamePlaceholder));
            AndroidUtilities.clearCursorDrawable(firstNameField);
            firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                        doneButton.performClick();
                        return true;
                    }
                    return false;
                }
            });

            ((LinearLayout) fragmentView).addView(firstNameField);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)firstNameField.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(24);
            layoutParams.height = AndroidUtilities.dp(36);
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
            firstNameField.setLayoutParams(layoutParams);

            if (user != null && user.username != null && user.username.length() > 0) {
                firstNameField.setText(user.username);
                firstNameField.setSelection(firstNameField.length());
            }

            checkTextView = new TextView(getParentActivity());
            checkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            checkTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            ((LinearLayout) fragmentView).addView(checkTextView);
            layoutParams = (LinearLayout.LayoutParams)checkTextView.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(12);
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            checkTextView.setLayoutParams(layoutParams);

            TextView helpTextView = new TextView(getParentActivity());
            helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            helpTextView.setTextColor(0xff6d6d72);
            helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            helpTextView.setText(Html.fromHtml(LocaleController.getString("UsernameHelp", R.string.UsernameHelp)));
            ((LinearLayout) fragmentView).addView(helpTextView);
            layoutParams = (LinearLayout.LayoutParams)helpTextView.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(10);
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            helpTextView.setLayoutParams(layoutParams);

            firstNameField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    checkUserName(firstNameField.getText().toString(), false);
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });

            checkTextView.setVisibility(View.GONE);
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }
    }

    private void showErrorAlert(String error) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        if (error.equals("USERNAME_INVALID")) {
            builder.setMessage(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
        } else if (error.equals("USERNAME_OCCUPIED")) {
            builder.setMessage(LocaleController.getString("UsernameInUse", R.string.UsernameInUse));
        } else if (error.equals("USERNAMES_UNAVAILABLE")) {
            builder.setMessage(LocaleController.getString("FeatureUnavailable", R.string.FeatureUnavailable));
        } else {
            builder.setMessage(LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showAlertDialog(builder);
    }

    private boolean checkUserName(final String name, boolean alert) {
        if (name != null && name.length() > 0) {
            checkTextView.setVisibility(View.VISIBLE);
        } else {
            checkTextView.setVisibility(View.GONE);
        }
        if (alert && name.length() == 0) {
            return true;
        }
        if (checkRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            checkRunnable = null;
            lastCheckName = null;
            if (checkReqId != 0) {
                ConnectionsManager.getInstance().cancelRpc(checkReqId, true);
            }
        }
        lastNameAvailable = false;
        if (name != null) {
            if (name.startsWith("_") || name.endsWith("_")) {
                checkTextView.setText(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                checkTextView.setTextColor(0xffcf3030);
                return false;
            }
            for (int a = 0; a < name.length(); a++) {
                char ch = name.charAt(a);
                if (a == 0 && ch >= '0' && ch <= '9') {
                    if (alert) {
                        showErrorAlert(LocaleController.getString("UsernameInvalidStartNumber", R.string.UsernameInvalidStartNumber));
                    } else {
                        checkTextView.setText(LocaleController.getString("UsernameInvalidStartNumber", R.string.UsernameInvalidStartNumber));
                        checkTextView.setTextColor(0xffcf3030);
                    }
                    return false;
                }
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    if (alert) {
                        showErrorAlert(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                    } else {
                        checkTextView.setText(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                        checkTextView.setTextColor(0xffcf3030);
                    }
                    return false;
                }
            }
        }
        if (name == null || name.length() < 5) {
            if (alert) {
                showErrorAlert(LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
            } else {
                checkTextView.setText(LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
                checkTextView.setTextColor(0xffcf3030);
            }
            return false;
        }
        if (name.length() > 32) {
            if (alert) {
                showErrorAlert(LocaleController.getString("UsernameInvalidLong", R.string.UsernameInvalidLong));
            } else {
                checkTextView.setText(LocaleController.getString("UsernameInvalidLong", R.string.UsernameInvalidLong));
                checkTextView.setTextColor(0xffcf3030);
            }
            return false;
        }

        if (!alert) {
            String currentName = UserConfig.getCurrentUser().username;
            if (currentName == null) {
                currentName = "";
            }
            if (name.equals(currentName)) {
                checkTextView.setText(LocaleController.formatString("UsernameAvailable", R.string.UsernameAvailable, name));
                checkTextView.setTextColor(0xff26972c);
                return true;
            }

            checkTextView.setText(LocaleController.getString("UsernameChecking", R.string.UsernameChecking));
            checkTextView.setTextColor(0xff6d6d72);
            lastCheckName = name;
            checkRunnable = new Runnable() {
                @Override
                public void run() {
                    TLRPC.TL_account_checkUsername req = new TLRPC.TL_account_checkUsername();
                    req.username = name;
                    checkReqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(final TLObject response, final TLRPC.TL_error error) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    checkReqId = 0;
                                    if (lastCheckName != null && lastCheckName.equals(name)) {
                                        if (error == null && response instanceof TLRPC.TL_boolTrue) {
                                            checkTextView.setText(LocaleController.formatString("UsernameAvailable", R.string.UsernameAvailable, name));
                                            checkTextView.setTextColor(0xff26972c);
                                            lastNameAvailable = true;
                                        } else {
                                            checkTextView.setText(LocaleController.getString("UsernameInUse", R.string.UsernameInUse));
                                            checkTextView.setTextColor(0xffcf3030);
                                            lastNameAvailable = false;
                                        }
                                    }
                                }
                            });
                        }
                    }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                }
            };
            AndroidUtilities.runOnUIThread(checkRunnable, 300);
        }
        return true;
    }

    private void saveName() {
        if (!checkUserName(firstNameField.getText().toString(), true)) {
            return;
        }
        TLRPC.User user = UserConfig.getCurrentUser();
        if (getParentActivity() == null || user == null) {
            return;
        }
        String currentName = user.username;
        if (currentName == null) {
            currentName = "";
        }
        String newName = firstNameField.getText().toString();
        if (currentName.equals(newName)) {
            finishFragment();
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(getParentActivity());
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);

        TLRPC.TL_account_updateUsername req = new TLRPC.TL_account_updateUsername();
        req.username = newName;

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        final long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, final TLRPC.TL_error error) {
                if (error == null) {
                    final TLRPC.User user = (TLRPC.User)response;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(user);
                            MessagesController.getInstance().putUsers(users, false);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                            UserConfig.saveConfig(true);
                            finishFragment();
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            showErrorAlert(error.text);
                        }
                    });
                }
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);

        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ConnectionsManager.getInstance().cancelRpc(reqId, true);
                try {
                    dialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
        progressDialog.show();
    }

    @Override
    public void onOpenAnimationEnd() {
        firstNameField.requestFocus();
        AndroidUtilities.showKeyboard(firstNameField);
    }
}
