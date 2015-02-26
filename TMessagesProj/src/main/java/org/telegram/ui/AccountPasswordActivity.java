/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
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
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextFieldCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;

public class AccountPasswordActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private TextFieldCell oldPasswordCell;
    private TextFieldCell newPasswordCell;
    private TextFieldCell verifyPasswordCell;
    private TextFieldCell hintPasswordCell;
    private View doneButton;
    private ProgressDialog progressDialog;

    private int type;
    private boolean hasPassword;
    private boolean loading;
    private byte[] new_salt;
    private String hint;
    private byte[] current_salt;

    private int changePasswordSectionRow;
    private int oldPasswordRow;
    private int newPasswordRow;
    private int verifyPasswordRow;
    private int hintRow;
    private int passwordDetailRow;
    private int deleteAccountSection;
    private int deleteAccountRow;
    private int deleteAccountDetailRow;
    private int rowCount;

    private final static int done_button = 1;

    public AccountPasswordActivity(int type) {
        super();
        this.type = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        getCurrentPassword();

        return true;
    }

    @Override
    public View createView(LayoutInflater inflater) {
        if (fragmentView == null) {
            if (type == 0) {
                actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            }
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("Password", R.string.Password));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == done_button) {
                        doneWithPassword();
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
            doneButton.setVisibility(loading ? View.GONE : View.VISIBLE);

            if (type == 0) {
                oldPasswordCell = new TextFieldCell(getParentActivity());
                oldPasswordCell.setFieldTitleAndHint(LocaleController.getString("OldPassword", R.string.OldPassword), LocaleController.getString("EnterOldPassword", R.string.EnterOldPassword), AndroidUtilities.dp(10), true);
                oldPasswordCell.setBackgroundColor(0xffffffff);
                newPasswordCell = new TextFieldCell(getParentActivity());
                newPasswordCell.setFieldTitleAndHint(LocaleController.getString("NewPassword", R.string.NewPassword), LocaleController.getString("EnterNewPassword", R.string.EnterNewPassword), 0, true);
                newPasswordCell.setBackgroundColor(0xffffffff);
                verifyPasswordCell = new TextFieldCell(getParentActivity());
                verifyPasswordCell.setFieldTitleAndHint(null, LocaleController.getString("VerifyNewPassword", R.string.VerifyNewPassword), AndroidUtilities.dp(10), true);
                verifyPasswordCell.setBackgroundColor(0xffffffff);
                hintPasswordCell = new TextFieldCell(getParentActivity());
                hintPasswordCell.setFieldTitleAndHint(LocaleController.getString("PasswordHint", R.string.PasswordHint), LocaleController.getString("EnterHint", R.string.EnterHint), AndroidUtilities.dp(22), false);
                hintPasswordCell.setBackgroundColor(0xffffffff);
                if (hint != null) {
                    hintPasswordCell.setFieldText(hint);
                }
            } else if (type == 1) {
                oldPasswordCell = new TextFieldCell(getParentActivity());
                oldPasswordCell.setFieldTitleAndHint(null, LocaleController.getString("EnterYourPassword", R.string.EnterYourPassword), AndroidUtilities.dp(22), true);
                oldPasswordCell.setBackgroundColor(0xffffffff);
            }

            listAdapter = new ListAdapter(getParentActivity());

            fragmentView = new FrameLayout(getParentActivity());
            FrameLayout frameLayout = (FrameLayout) fragmentView;
            frameLayout.setBackgroundColor(0xfff0f0f0);

            FrameLayout progressView = new FrameLayout(getParentActivity());
            frameLayout.addView(progressView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            progressView.setLayoutParams(layoutParams);

            ProgressBar progressBar = new ProgressBar(getParentActivity());
            progressView.addView(progressBar);
            layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.CENTER;
            progressView.setLayoutParams(layoutParams);

            ListView listView = new ListView(getParentActivity());
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDrawSelectorOnTop(true);
            listView.setEmptyView(progressView);
            frameLayout.addView(listView);
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (i == deleteAccountRow) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSureDeleteAccount", R.string.AreYouSureDeleteAccount));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setMessage(LocaleController.getString("AreYouSureDeleteAccount2", R.string.AreYouSureDeleteAccount2));
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                                        req.reason = "Forgot password";
                                        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                            @Override
                                            public void run(TLObject response, TLRPC.TL_error error) {
                                                if (error == null) {
                                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                                            SharedPreferences.Editor editor = preferences.edit();
                                                            editor.clear().commit();
                                                            MessagesController.getInstance().unregistedPush();
                                                            MessagesController.getInstance().logOut();
                                                            UserConfig.clearConfig();
                                                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.appDidLogout);
                                                            MessagesStorage.getInstance().cleanUp(false);
                                                            MessagesController.getInstance().cleanUp();
                                                            ContactsController.getInstance().deleteAllAppAccounts();
                                                        }
                                                    });
                                                }
                                            }
                                        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassWithoutLogin);
                                    }
                                });
                                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                showAlertDialog(builder);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    }
                }
            });

            updateRows();
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
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void updateRows() {
        rowCount = 0;
        if (!loading) {
            if (type == 0) {
                changePasswordSectionRow = rowCount++;
                oldPasswordRow = hasPassword ? rowCount++ : -1;
                newPasswordRow = rowCount++;
                verifyPasswordRow = rowCount++;
                hintRow = rowCount++;
                passwordDetailRow = rowCount++;
                deleteAccountSection = -1;
                deleteAccountRow = -1;
                deleteAccountDetailRow = -1;
            } else if (type == 1) {
                changePasswordSectionRow = rowCount++;
                oldPasswordRow = rowCount++;
                passwordDetailRow = rowCount++;
                deleteAccountSection = rowCount++;
                deleteAccountDetailRow = rowCount++;
                verifyPasswordRow = -1;
                newPasswordRow = -1;
                hintRow = -1;
                deleteAccountRow = -1;
            }
            doneButton.setVisibility(View.VISIBLE);
        }
        listAdapter.notifyDataSetChanged();
    }

    private void ShowAlert(final String text) {
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showAlertDialog(builder);
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

    private void getCurrentPassword() {
        loading = true;
        TLRPC.TL_account_getPassword req = new TLRPC.TL_account_getPassword();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        TLRPC.account_Password res = (TLRPC.account_Password) response;
                        if (res instanceof TLRPC.TL_account_noPassword) {
                            hasPassword = false;
                            new_salt = res.new_salt;
                            hint = null;
                            current_salt = null;
                        } else if (res instanceof TLRPC.TL_account_password) {
                            hasPassword = true;
                            new_salt = res.new_salt;
                            hint = res.hint;
                            current_salt = res.current_salt;
                        } else {
                            new_salt = null;
                            hint = null;
                            current_salt = null;
                        }
                        if (new_salt != null) {
                            byte[] salt = new byte[new_salt.length + 16];
                            Utilities.random.nextBytes(salt);
                            System.arraycopy(new_salt, 0, salt, 0, new_salt.length);
                            new_salt = salt;
                        }
                        if (type == 0 && hintPasswordCell != null && hint != null) {
                            hintPasswordCell.setFieldText(hint);
                        }
                        updateRows();
                    }
                });
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors | RPCRequest.RPCRequestClassWithoutLogin);
    }

    private void doneWithPassword() {
        if (type == 0) {
            String oldPassword = oldPasswordCell.getFieldText();
            String newPassword = newPasswordCell.getFieldText();
            String verifyPasswrod = verifyPasswordCell.getFieldText();
            String hint = hintPasswordCell.getFieldText();
            if (hasPassword) {
                if (oldPassword.length() == 0) {
                    ShowAlert(LocaleController.getString("PasswordOldIncorrect", R.string.PasswordOldIncorrect));
                    return;
                }
            }
            if (newPassword.length() == 0) {
                ShowAlert(LocaleController.getString("PasswordNewIncorrect", R.string.PasswordNewIncorrect));
                return;
            }
            if (!newPassword.equals(verifyPasswrod)) {
                ShowAlert(LocaleController.getString("PasswordDoNotMatch", R.string.PasswordDoNotMatch));
                return;
            }
            if (hint.toLowerCase().contains(newPassword.toLowerCase())) {
                ShowAlert(LocaleController.getString("HintIncorrect", R.string.HintIncorrect));
                return;
            }
            byte[] oldPasswordBytes = null;
            byte[] newPasswordBytes = null;
            try {
                oldPasswordBytes = oldPassword.getBytes("UTF-8");
                newPasswordBytes = newPassword.getBytes("UTF-8");
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            TLRPC.TL_account_setPassword req = new TLRPC.TL_account_setPassword();
            req.hint = hintPasswordCell.getFieldText();
            if (req.hint == null) {
                req.hint = "";
            }
            if (hasPassword) {
                byte[] hash = new byte[current_salt.length * 2 + oldPasswordBytes.length];
                System.arraycopy(current_salt, 0, hash, 0, current_salt.length);
                System.arraycopy(oldPasswordBytes, 0, hash, oldPasswordBytes.length, oldPasswordBytes.length);
                System.arraycopy(current_salt, 0, hash, hash.length - current_salt.length, current_salt.length);
                req.current_password_hash = Utilities.computeSHA256(hash, 0, hash.length);
            } else {
                req.current_password_hash = new byte[0];
            }

            needShowProgress();
            byte[] hash = new byte[new_salt.length * 2 + newPasswordBytes.length];
            System.arraycopy(new_salt, 0, hash, 0, new_salt.length);
            System.arraycopy(newPasswordBytes, 0, hash, newPasswordBytes.length, newPasswordBytes.length);
            System.arraycopy(new_salt, 0, hash, hash.length - new_salt.length, new_salt.length);
            req.new_password_hash = Utilities.computeSHA256(hash, 0, hash.length);
            req.new_salt = new_salt;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            needHideProgress();
                            if (error == null) {
                                UserConfig.registeredForPush = false;
                                UserConfig.registeredForInternalPush = false;
                                UserConfig.saveConfig(false);
                                MessagesController.getInstance().registerForPush(UserConfig.pushString);
                                ConnectionsManager.getInstance().initPushConnection();
                                finishFragment();
                            } else {
                                if (error.text.contains("PASSWORD_HASH_INVALID")) {
                                    ShowAlert(LocaleController.getString("PasswordOldIncorrect", R.string.PasswordOldIncorrect));
                                } else if (error.text.contains("NEW_PASSWORD_BAD")) {
                                    ShowAlert(LocaleController.getString("PasswordNewIncorrect", R.string.PasswordNewIncorrect));
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    ShowAlert(LocaleController.getString("FloodWait", R.string.FloodWait));
                                } else {
                                    ShowAlert(error.text);
                                }
                            }
                        }
                    });
                }
            }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
        } else if (type == 1) {
            String oldPassword = oldPasswordCell.getFieldText();
            if (oldPassword.length() == 0) {
                ShowAlert(LocaleController.getString("PasswordIncorrect", R.string.PasswordIncorrect));
                return;
            }
            byte[] oldPasswordBytes = null;
            try {
                oldPasswordBytes = oldPassword.getBytes("UTF-8");
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            needShowProgress();
            byte[] hash = new byte[current_salt.length * 2 + oldPasswordBytes.length];
            System.arraycopy(current_salt, 0, hash, 0, current_salt.length);
            System.arraycopy(oldPasswordBytes, 0, hash, oldPasswordBytes.length, oldPasswordBytes.length);
            System.arraycopy(current_salt, 0, hash, hash.length - current_salt.length, current_salt.length);

            TLRPC.TL_auth_checkPassword req = new TLRPC.TL_auth_checkPassword();
            req.password_hash = Utilities.computeSHA256(hash, 0, hash.length);
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            needHideProgress();
                            if (error == null) {
                                if (UserConfig.isClientActivated()) {
                                    presentFragment(new MessagesActivity(null), true);
                                    UserConfig.registeredForPush = false;
                                    UserConfig.registeredForInternalPush = false;
                                    UserConfig.saveConfig(false);
                                    MessagesController.getInstance().registerForPush(UserConfig.pushString);
                                    ConnectionsManager.getInstance().initPushConnection();
                                } else {
                                    TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization)response;
                                    UserConfig.clearConfig();
                                    MessagesController.getInstance().cleanUp();
                                    UserConfig.setCurrentUser(res.user);
                                    UserConfig.saveConfig(true);
                                    MessagesStorage.getInstance().cleanUp(true);
                                    ArrayList<TLRPC.User> users = new ArrayList<>();
                                    users.add(res.user);
                                    MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                                    MessagesController.getInstance().putUser(res.user, false);
                                    ContactsController.getInstance().checkAppAccount();
                                    MessagesController.getInstance().getBlockedUsers(true);
                                    presentFragment(new MessagesActivity(null), true);
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                                    ConnectionsManager.getInstance().initPushConnection();
                                }
                            } else {
                                if (error.text.contains("PASSWORD_HASH_INVALID")) {
                                    ShowAlert(LocaleController.getString("PasswordOldIncorrect", R.string.PasswordOldIncorrect));
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    ShowAlert(LocaleController.getString("FloodWait", R.string.FloodWait));
                                } else {
                                    ShowAlert(error.text);
                                }
                            }
                        }
                    });
                }
            }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors | RPCRequest.RPCRequestClassWithoutLogin);
        }
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
            return i == deleteAccountRow;
        }

        @Override
        public int getCount() {
            return rowCount;
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
                    view = new TextInfoPrivacyCell(mContext);
                }
                if (i == passwordDetailRow) {
                    if (type == 0) {
                        ((TextInfoPrivacyCell) view).setText(LocaleController.getString("PasswordImportant", R.string.PasswordImportant));
                    } else if (type == 1) {
                        ((TextInfoPrivacyCell) view).setText(hint == null || hint.length() == 0 ? "" : LocaleController.formatString("PasswordHintDetail", R.string.PasswordHintDetail, hint));
                    }
                    ((TextInfoPrivacyCell) view).setTextColor(0xffcf3030);
                    if (deleteAccountDetailRow != -1) {
                        view.setBackgroundResource(R.drawable.greydivider);
                    } else {
                        view.setBackgroundResource(R.drawable.greydivider_bottom);
                    }
                } else if (i == deleteAccountDetailRow) {
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("DeleteAccountImportant", R.string.DeleteAccountImportant));
                    ((TextInfoPrivacyCell) view).setTextColor(0xffcf3030);
                    view.setBackgroundResource(R.drawable.greydivider_bottom);
                }
            } else if (viewType == 1) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == changePasswordSectionRow) {
                    if (type == 0) {
                        ((HeaderCell) view).setText(LocaleController.getString("ChangePassword", R.string.ChangePassword));
                    } else if (type == 1) {
                        ((HeaderCell) view).setText(LocaleController.getString("EnterPassword", R.string.EnterPassword));
                    }
                } else if (i == deleteAccountSection) {
                    ((HeaderCell) view).setText(LocaleController.getString("PasswordDeleteAccountTitle", R.string.PasswordDeleteAccountTitle));
                }
            } else if (viewType == 2) {
                return newPasswordCell;
            } else if (viewType == 3) {
                return oldPasswordCell;
            } else if (viewType == 4) {
                return verifyPasswordCell;
            } else if (viewType == 5) {
                return hintPasswordCell;
            } else if (viewType == 6) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == deleteAccountRow) {
                    textCell.setText(LocaleController.getString("PasswordDeleteAccount", R.string.PasswordDeleteAccount), false);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == passwordDetailRow || i == deleteAccountDetailRow) {
                return 0;
            } else if (i == changePasswordSectionRow || i == deleteAccountSection) {
                return 1;
            } else if (i == newPasswordRow) {
                return 2;
            } else if (i == oldPasswordRow) {
                return 3;
            } else if (i == verifyPasswordRow) {
                return 4;
            } else if (i == hintRow) {
                return 5;
            } else if (i == deleteAccountRow) {
                return 6;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 7;
        }

        @Override
        public boolean isEmpty() {
            return rowCount == 0;
        }
    }
}
