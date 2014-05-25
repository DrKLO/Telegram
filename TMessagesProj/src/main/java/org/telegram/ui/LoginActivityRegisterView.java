/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.SlideView;

import java.util.ArrayList;

public class LoginActivityRegisterView extends SlideView {
    private EditText firstNameField;
    private EditText lastNameField;
    private String requestPhone;
    private String phoneHash;
    private String phoneCode;
    private Bundle currentParams;

    public LoginActivityRegisterView(Context context) {
        super(context);
    }

    public LoginActivityRegisterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LoginActivityRegisterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        firstNameField = (EditText)findViewById(R.id.login_first_name_field);
        firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
        lastNameField = (EditText)findViewById(R.id.login_last_name_field);
        lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));

        TextView textView = (TextView)findViewById(R.id.login_register_info);
        textView.setText(LocaleController.getString("RegisterText", R.string.RegisterText));

        TextView wrongNumber = (TextView) findViewById(R.id.changed_mind);
        wrongNumber.setText(LocaleController.getString("CancelRegistration", R.string.CancelRegistration));

        wrongNumber.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
                delegate.setPage(0, true, null, true);
            }
        });

        firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    lastNameField.requestFocus();
                    return true;
                }
                return false;
            }
        });

    }

    @Override
    public void onBackPressed() {
        currentParams = null;
    }

    @Override
    public String getHeaderName() {
        return LocaleController.getString("YourName", R.string.YourName);
    }

    @Override
    public void onShow() {
        super.onShow();
        if (firstNameField != null) {
            firstNameField.requestFocus();
            firstNameField.setSelection(firstNameField.length());
        }
    }

    @Override
    public void setParams(Bundle params) {
        if (params == null) {
            return;
        }
        firstNameField.setText("");
        lastNameField.setText("");
        requestPhone = params.getString("phoneFormated");
        phoneHash = params.getString("phoneHash");
        phoneCode = params.getString("code");
        currentParams = params;
    }

    @Override
    public void onNextPressed() {
        TLRPC.TL_auth_signUp req = new TLRPC.TL_auth_signUp();
        req.phone_code = phoneCode;
        req.phone_code_hash = phoneHash;
        req.phone_number = requestPhone;
        req.first_name = firstNameField.getText().toString();
        req.last_name = lastNameField.getText().toString();
        delegate.needShowProgress();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (delegate != null) {
                    delegate.needHideProgress();
                }
                if (error == null) {
                    final TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization)response;
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.TL_userSelf user = (TLRPC.TL_userSelf)res.user;
                            UserConfig.clearConfig();
                            MessagesStorage.getInstance().cleanUp();
                            MessagesController.getInstance().cleanUp();
                            ConnectionsManager.getInstance().cleanUp();
                            UserConfig.currentUser = user;
                            UserConfig.clientActivated = true;
                            UserConfig.clientUserId = user.id;
                            UserConfig.saveConfig(true);
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            //MessagesController.getInstance().uploadAndApplyUserAvatar(avatarPhotoBig);
                            MessagesController.getInstance().users.put(res.user.id, res.user);
                            ContactsController.getInstance().checkAppAccount();
                            if (delegate != null) {
                                delegate.needFinishActivity();
                            }
                            ConnectionsManager.getInstance().initPushConnection();
                        }
                    });
                } else {
                    if (delegate != null) {
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            delegate.needShowAlert(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            delegate.needShowAlert(LocaleController.getString("InvalidCode", R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            delegate.needShowAlert(LocaleController.getString("CodeExpired", R.string.CodeExpired));
                        } else if (error.text.contains("FIRSTNAME_INVALID")) {
                            delegate.needShowAlert(LocaleController.getString("InvalidFirstName", R.string.InvalidFirstName));
                        } else if (error.text.contains("LASTNAME_INVALID")) {
                            delegate.needShowAlert(LocaleController.getString("InvalidLastName", R.string.InvalidLastName));
                        } else {
                            delegate.needShowAlert(error.text);
                        }
                    }
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    @Override
    public void saveStateParams(Bundle bundle) {
        String first = firstNameField.getText().toString();
        if (first != null && first.length() != 0) {
            bundle.putString("registerview_first", first);
        }
        String last = lastNameField.getText().toString();
        if (last != null && last.length() != 0) {
            bundle.putString("registerview_last", last);
        }
        if (currentParams != null) {
            bundle.putBundle("registerview_params", currentParams);
        }
    }

    @Override
    public void restoreStateParams(Bundle bundle) {
        currentParams = bundle.getBundle("registerview_params");
        if (currentParams != null) {
            setParams(currentParams);
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
}
