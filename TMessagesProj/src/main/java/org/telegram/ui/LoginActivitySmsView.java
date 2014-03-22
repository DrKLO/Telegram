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
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.SlideView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class LoginActivitySmsView extends SlideView implements NotificationCenter.NotificationCenterDelegate {
    private String phoneHash;
    private String requestPhone;
    private String registered;
    private EditText codeField;
    private TextView confirmTextView;
    private TextView timeText;
    private Bundle currentParams;

    private Timer timeTimer;
    private final Integer timerSync = 1;
    private int time = 60000;
    private double lastCurrentTime;
    private boolean waitingForSms = false;

    public LoginActivitySmsView(Context context) {
        super(context);
    }

    public LoginActivitySmsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LoginActivitySmsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        confirmTextView = (TextView)findViewById(R.id.login_sms_confirm_text);
        codeField = (EditText)findViewById(R.id.login_sms_code_field);
        codeField.setHint(LocaleController.getString("Code", R.string.Code));
        timeText = (TextView)findViewById(R.id.login_time_text);
        TextView wrongNumber = (TextView) findViewById(R.id.wrong_number);
        wrongNumber.setText(LocaleController.getString("WrongNumber", R.string.WrongNumber));

        wrongNumber.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
                delegate.setPage(0, true, null, true);
            }
        });

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
    }

    @Override
    public String getHeaderName() {
        return getResources().getString(R.string.YourCode);
    }

    @Override
    public void setParams(Bundle params) {
        codeField.setText("");
        Utilities.setWaitingForSms(true);
        NotificationCenter.getInstance().addObserver(this, 998);
        currentParams = params;
        waitingForSms = true;
        String phone = params.getString("phone");
        requestPhone = params.getString("phoneFormated");
        phoneHash = params.getString("phoneHash");
        registered = params.getString("registered");
        time = params.getInt("calltime");

        String number = PhoneFormat.getInstance().format(phone);
        confirmTextView.setText(Html.fromHtml(String.format(ApplicationLoader.applicationContext.getResources().getString(R.string.SentSmsCode) + " <b>%s</b>", number)));

        Utilities.showKeyboard(codeField);
        codeField.requestFocus();

        try {
            synchronized(timerSync) {
                if (timeTimer != null) {
                    timeTimer.cancel();
                    timeTimer = null;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
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
                            synchronized(timerSync) {
                                if (timeTimer != null) {
                                    timeTimer.cancel();
                                    timeTimer = null;
                                }
                            }
                            TLRPC.TL_auth_sendCall req = new TLRPC.TL_auth_sendCall();
                            req.phone_number = requestPhone;
                            req.phone_code_hash = phoneHash;
                            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                @Override
                                public void run(TLObject response, TLRPC.TL_error error) {
                                }
                            }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    @Override
    public void onNextPressed() {
        waitingForSms = false;
        Utilities.setWaitingForSms(false);
        NotificationCenter.getInstance().removeObserver(this, 998);
        final TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
        req.phone_number = requestPhone;
        req.phone_code = codeField.getText().toString();
        req.phone_code_hash = phoneHash;
        try {
            synchronized(timerSync) {
                if (timeTimer != null) {
                    timeTimer.cancel();
                    timeTimer = null;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (delegate != null) {
            delegate.needShowProgress();
        }
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
                            if (delegate == null) {
                                return;
                            }
                            try {
                                synchronized(timerSync) {
                                    if (timeTimer != null) {
                                        timeTimer.cancel();
                                        timeTimer = null;
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            UserConfig.clearConfig();
                            MessagesStorage.getInstance().cleanUp();
                            MessagesController.getInstance().cleanUp();
                            ConnectionsManager.getInstance().cleanUp();
                            UserConfig.currentUser = res.user;
                            UserConfig.clientActivated = true;
                            UserConfig.clientUserId = res.user.id;
                            UserConfig.saveConfig(true);
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(UserConfig.currentUser);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance().users.put(res.user.id, res.user);
                            ContactsController.getInstance().checkAppAccount();
                            if (delegate != null) {
                                delegate.needFinishActivity();
                            }
                        }
                    });
                } else {
                    if (error.text.contains("PHONE_NUMBER_UNOCCUPIED") && registered == null) {
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                Bundle params = new Bundle();
                                params.putString("phoneFormated", requestPhone);
                                params.putString("phoneHash", phoneHash);
                                params.putString("code", req.phone_code);
                                delegate.setPage(2, true, params, false);
                                try {
                                    synchronized(timerSync) {
                                        if (timeTimer != null) {
                                            timeTimer.cancel();
                                            timeTimer = null;
                                        }
                                    }
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                    } else {
                        if (timeTimer == null) {
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
                                                synchronized(timerSync) {
                                                    if (timeTimer != null) {
                                                        timeTimer.cancel();
                                                        timeTimer = null;
                                                    }
                                                }
                                                TLRPC.TL_auth_sendCall req = new TLRPC.TL_auth_sendCall();
                                                req.phone_number = requestPhone;
                                                req.phone_code_hash = phoneHash;
                                                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                                    @Override
                                                    public void run(TLObject response, TLRPC.TL_error error) {
                                                    }
                                                }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                                            }
                                        }
                                    });
                                }
                            }, 0, 1000);
                        }
                        if (delegate != null) {
                            if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                delegate.needShowAlert(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                            } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                delegate.needShowAlert(LocaleController.getString("InvalidCode", R.string.InvalidCode));
                            } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                delegate.needShowAlert(LocaleController.getString("CodeExpired", R.string.CodeExpired));
                            } else {
                                delegate.needShowAlert(error.text);
                            }
                        }
                    }
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    @Override
    public void onBackPressed() {
        try {
            synchronized(timerSync) {
                if (timeTimer != null) {
                    timeTimer.cancel();
                    timeTimer = null;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        currentParams = null;
        Utilities.setWaitingForSms(false);
        NotificationCenter.getInstance().removeObserver(this, 998);
        waitingForSms = false;
    }

    @Override
    public void onDestroyActivity() {
        super.onDestroyActivity();
        Utilities.setWaitingForSms(false);
        NotificationCenter.getInstance().removeObserver(this, 998);
        try {
            synchronized(timerSync) {
                if (timeTimer != null) {
                    timeTimer.cancel();
                    timeTimer = null;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        waitingForSms = false;
    }

    @Override
    public void onShow() {
        super.onShow();
        if (codeField != null) {
            codeField.requestFocus();
            codeField.setSelection(codeField.length());
        }
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
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        return new SavedState(superState, currentParams);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        currentParams = savedState.params;
        if (currentParams != null) {
            setParams(currentParams);
        }
    }

    protected static class SavedState extends BaseSavedState {
        public Bundle params;

        private SavedState(Parcelable superState, Bundle p1) {
            super(superState);
            params = p1;
        }

        private SavedState(Parcel in) {
            super(in);
            params = in.readBundle();
        }

        @Override
        public void writeToParcel(Parcel destination, int flags) {
            super.writeToParcel(destination, flags);
            destination.writeBundle(params);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
