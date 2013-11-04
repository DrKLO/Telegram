/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.SlideFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class ScreenSlidePageFragmentSms extends SlideFragment implements NotificationCenter.NotificationCenterDelegate {
    private String phoneHash;
    private String requestPhone;
    private String registered;
    private EditText codeField;
    private TextView confirmTextView;
    private TextView timeText;
    private HashMap<String, String> currentParams;

    private Timer timeTimer;
    private int time = 60000;
    private double lastCurrentTime;
    private boolean waitingForSms = false;

    @SuppressWarnings("unchecked")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.login_sms_layout, container, false);

        Typeface typeface = Utilities.getTypeface("fonts/rlight.ttf");
        confirmTextView = (TextView)rootView.findViewById(R.id.login_sms_confirm_text);
        confirmTextView.setTypeface(typeface);
        codeField = (EditText)rootView.findViewById(R.id.login_sms_code_field);
        timeText = (TextView)rootView.findViewById(R.id.login_time_text);
        timeText.setTypeface(typeface);

        codeField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    if (delegate != null) {
                        delegate.onNextAction();
                    }
                    return true;
                }
                return false;
            }
        });

        if (savedInstanceState != null) {
            currentParams = (HashMap<String, String>)savedInstanceState.getSerializable("params");
            if (currentParams != null) {
                setParams(currentParams);
            }
        }

        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        delegate = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        NotificationCenter.Instance.removeObserver(this, 998);
        if (timeTimer != null) {
            timeTimer.cancel();
            timeTimer = null;
        }
    }

    @Override
    public String getHeaderName() {
        return getResources().getString(R.string.YourCode);
    }

    @Override
    public void setParams(HashMap<String, String> params) {
        codeField.setText("");
        NotificationCenter.Instance.addObserver(this, 998);
        currentParams = params;
        waitingForSms = true;
        String phone = params.get("phone");
        requestPhone = params.get("phoneFormated");
        phoneHash = params.get("phoneHash");
        registered = params.get("registered");

        String number = PhoneFormat.Instance.format(phone);
        confirmTextView.setText(Html.fromHtml(String.format(ApplicationLoader.applicationContext.getResources().getString(R.string.SentSmsCode) + " <b>%s</b>", number)));

        Utilities.showKeyboard(codeField);
        codeField.requestFocus();

        time = 60000;
        if (timeTimer != null) {
            timeTimer.cancel();
            timeTimer = null;
        }
        timeText.setText(String.format("%s 1:00", ApplicationLoader.applicationContext.getResources().getString(R.string.CallText)));
        lastCurrentTime = System.currentTimeMillis();
        timeTimer = new Timer();
        timeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                double currentTime = System.currentTimeMillis();
                double diff = currentTime - lastCurrentTime;
                time -= diff;
                lastCurrentTime = currentTime;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (time >= 1000) {
                            int minutes = time / 1000 / 60;
                            int seconds = time / 1000 - minutes * 60;
                            timeText.setText(String.format("%s %d:%02d", ApplicationLoader.applicationContext.getResources().getString(R.string.CallText), minutes, seconds));
                        } else {
                            timeText.setText(ApplicationLoader.applicationContext.getResources().getString(R.string.Calling));
                            if (timeTimer != null) {
                                timeTimer.cancel();
                                timeTimer = null;
                            }
                            TLRPC.TL_auth_sendCall req = new TLRPC.TL_auth_sendCall();
                            req.phone_number = requestPhone;
                            req.phone_code_hash = phoneHash;
                            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                }
                            }, null, true, RPCRequest.RPCRequestClassGeneric);
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    @Override
    public void onNextPressed() {
        waitingForSms = false;
        NotificationCenter.Instance.removeObserver(this, 998);
        final TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
        req.phone_number = requestPhone;
        req.phone_code = codeField.getText().toString();
        req.phone_code_hash = phoneHash;
        delegate.needShowProgress();
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
                            if (delegate == null) {
                                return;
                            }
                            if (timeTimer != null) {
                                timeTimer.cancel();
                                timeTimer = null;
                            }
                            UserConfig.clearConfig();
                            MessagesStorage.Instance.cleanUp();
                            MessagesController.Instance.cleanUp();
                            ConnectionsManager.Instance.cleanUp();
                            UserConfig.currentUser = res.user;
                            UserConfig.clientActivated = true;
                            UserConfig.clientUserId = res.user.id;
                            UserConfig.saveConfig(true);
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(UserConfig.currentUser);
                            MessagesStorage.Instance.putUsersAndChats(users, null, true, true);
                            MessagesController.Instance.users.put(res.user.id, res.user);
                            MessagesController.Instance.checkAppAccount();
                            delegate.needFinishActivity();
                        }
                    });
                } else {
                    if (error.text.contains("PHONE_NUMBER_UNOCCUPIED") && registered == null) {
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                HashMap<String, String> params = new HashMap<String, String>();
                                params.put("phoneFormated", requestPhone);
                                params.put("phoneHash", phoneHash);
                                params.put("code", req.phone_code);
                                delegate.didProceed(params);
                                delegate.needSlidePager(2, true);
                                if (timeTimer != null) {
                                    timeTimer.cancel();
                                    timeTimer = null;
                                }
                            }
                        });
                    } else {
                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                            delegate.needShowAlert(ApplicationLoader.applicationContext.getString(R.string.InvalidPhoneNumber));
                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                            delegate.needShowAlert(ApplicationLoader.applicationContext.getString(R.string.InvalidCode));
                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                            delegate.needShowAlert(ApplicationLoader.applicationContext.getString(R.string.CodeExpired));
                        } else {
                            delegate.needShowAlert(error.text);
                        }
                    }
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    @Override
    public void onBackPressed() {
        if (timeTimer != null) {
            timeTimer.cancel();
            timeTimer = null;
        }
        NotificationCenter.Instance.removeObserver(this, 998);
        waitingForSms = false;
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == 998) {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (!waitingForSms) {
                        return;
                    }
                    if (codeField != null) {
                        codeField.setText("" + args[0]);
                        onNextPressed();
                    }
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("params", currentParams);
    }
}
