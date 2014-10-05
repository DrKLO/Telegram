/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.android.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Views.SlideView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class LoginActivitySmsView extends SlideView implements NotificationCenter.NotificationCenterDelegate {
    private String phoneHash;
    private String requestPhone;
    private String registered;
    private EditText codeField;
    private TextView confirmTextView;
    private TextView timeText;
    private TextView problemText;
    private Bundle currentParams;

    private Timer timeTimer;
    private Timer codeTimer;
    private static final Object timerSync = new Object();
    private volatile int time = 60000;
    private volatile int codeTime = 15000;
    private double lastCurrentTime;
    private double lastCodeTime;
    private boolean waitingForSms = false;
    private boolean nextPressed = false;
    private String lastError = "";

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
        problemText = (TextView)findViewById(R.id.login_problem);
        TextView wrongNumber = (TextView) findViewById(R.id.wrong_number);
        wrongNumber.setText(LocaleController.getString("WrongNumber", R.string.WrongNumber));
        problemText.setText(LocaleController.getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
        problemText.setVisibility(time < 1000 ? VISIBLE : GONE);

        wrongNumber.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
                delegate.setPage(0, true, null, true);
            }
        });

        problemText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                    String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                    Intent mailer = new Intent(Intent.ACTION_SEND);
                    mailer.setType("message/rfc822");
                    mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{"sms@telegram.org"});
                    mailer.putExtra(Intent.EXTRA_SUBJECT, "Android registration/login issue " + version + " " + requestPhone);
                    mailer.putExtra(Intent.EXTRA_TEXT, "Phone: " + requestPhone + "\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault() + "\nError: " + lastError);
                    getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
                } catch (Exception e) {
                    if (delegate != null) {
                        delegate.needShowAlert(LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
                    }
                }
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
        return LocaleController.getString("YourCode", R.string.YourCode);
    }

    @Override
    public void setParams(Bundle params) {
        if (params == null) {
            return;
        }
        codeField.setText("");
        AndroidUtilities.setWaitingForSms(true);
        NotificationCenter.getInstance().addObserver(this, 998);
        currentParams = params;
        waitingForSms = true;
        String phone = params.getString("phone");
        requestPhone = params.getString("phoneFormated");
        phoneHash = params.getString("phoneHash");
        registered = params.getString("registered");
        time = params.getInt("calltime");

        if (phone == null) {
            return;
        }

        String number = PhoneFormat.getInstance().format(phone);
        confirmTextView.setText(Html.fromHtml(String.format(LocaleController.getString("SentSmsCode", R.string.SentSmsCode) + " <b>%s</b>", number)));

        AndroidUtilities.showKeyboard(codeField);
        codeField.requestFocus();

        destroyTimer();
        destroyCodeTimer();
        timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 1, 0));
        lastCurrentTime = System.currentTimeMillis();
        problemText.setVisibility(time < 1000 ? VISIBLE : GONE);

        createTimer();
    }

    private void createCodeTimer() {
        if (codeTimer != null) {
            return;
        }
        codeTime = 15000;
        codeTimer = new Timer();
        lastCodeTime = System.currentTimeMillis();
        codeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                double currentTime = System.currentTimeMillis();
                double diff = currentTime - lastCodeTime;
                codeTime -= diff;
                lastCodeTime = currentTime;
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (codeTime <= 1000) {
                            problemText.setVisibility(VISIBLE);
                            destroyCodeTimer();
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    private void destroyCodeTimer() {
        try {
            synchronized(timerSync) {
                if (codeTimer != null) {
                    codeTimer.cancel();
                    codeTimer = null;
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void createTimer() {
        if (timeTimer != null) {
            return;
        }
        timeTimer = new Timer();
        timeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                double currentTime = System.currentTimeMillis();
                double diff = currentTime - lastCurrentTime;
                time -= diff;
                lastCurrentTime = currentTime;
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (time >= 1000) {
                            int minutes = time / 1000 / 60;
                            int seconds = time / 1000 - minutes * 60;
                            timeText.setText(LocaleController.formatString("CallText", R.string.CallText, minutes, seconds));
                        } else {
                            timeText.setText(LocaleController.getString("Calling", R.string.Calling));
                            destroyTimer();
                            createCodeTimer();
                            TLRPC.TL_auth_sendCall req = new TLRPC.TL_auth_sendCall();
                            req.phone_number = requestPhone;
                            req.phone_code_hash = phoneHash;
                            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                @Override
                                public void run(TLObject response, final TLRPC.TL_error error) {
                                    if (error != null && error.text != null) {
                                        AndroidUtilities.RunOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                lastError = error.text;
                                            }
                                        });
                                    }
                                }
                            }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors | RPCRequest.RPCRequestClassWithoutLogin);
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    private void destroyTimer() {
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

    @Override
    public void onNextPressed() {
        if (nextPressed) {
            return;
        }
        nextPressed = true;
        waitingForSms = false;
        AndroidUtilities.setWaitingForSms(false);
        NotificationCenter.getInstance().removeObserver(this, 998);
        final TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
        req.phone_number = requestPhone;
        req.phone_code = codeField.getText().toString();
        req.phone_code_hash = phoneHash;
        destroyTimer();
        if (delegate != null) {
            delegate.needShowProgress();
        }
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (delegate == null) {
                            return;
                        }
                        delegate.needHideProgress();
                        nextPressed = false;
                        if (error == null) {
                            TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization)response;
                            destroyTimer();
                            destroyCodeTimer();
                            UserConfig.clearConfig();
                            MessagesController.getInstance().cleanUp();
                            UserConfig.setCurrentUser(res.user);
                            UserConfig.saveConfig(true);
                            MessagesStorage.getInstance().cleanUp(true);
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(res.user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                            MessagesController.getInstance().putUser(res.user, false);
                            ContactsController.getInstance().checkAppAccount();
                            MessagesController.getInstance().getBlockedUsers(true);
                            delegate.needFinishActivity();
                            ConnectionsManager.getInstance().initPushConnection();
                        } else {
                            lastError = error.text;
                            if (error.text.contains("PHONE_NUMBER_UNOCCUPIED") && registered == null) {
                                Bundle params = new Bundle();
                                params.putString("phoneFormated", requestPhone);
                                params.putString("phoneHash", phoneHash);
                                params.putString("code", req.phone_code);
                                delegate.setPage(2, true, params, false);
                                destroyTimer();
                                destroyCodeTimer();
                            } else {
                                createTimer();
                                if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                    delegate.needShowAlert(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                    delegate.needShowAlert(LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                    delegate.needShowAlert(LocaleController.getString("CodeExpired", R.string.CodeExpired));
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    delegate.needShowAlert(LocaleController.getString("FloodWait", R.string.FloodWait));
                                } else {
                                    delegate.needShowAlert(error.text);
                                }
                            }
                        }
                    }
                });
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors | RPCRequest.RPCRequestClassWithoutLogin);
    }

    @Override
    public void onBackPressed() {
        destroyTimer();
        destroyCodeTimer();
        currentParams = null;
        AndroidUtilities.setWaitingForSms(false);
        NotificationCenter.getInstance().removeObserver(this, 998);
        waitingForSms = false;
    }

    @Override
    public void onDestroyActivity() {
        super.onDestroyActivity();
        AndroidUtilities.setWaitingForSms(false);
        NotificationCenter.getInstance().removeObserver(this, 998);
        destroyTimer();
        destroyCodeTimer();
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
            AndroidUtilities.RunOnUIThread(new Runnable() {
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
    public void saveStateParams(Bundle bundle) {
        String code = codeField.getText().toString();
        if (code != null && code.length() != 0) {
            bundle.putString("smsview_code", code);
        }
        if (currentParams != null) {
            bundle.putBundle("smsview_params", currentParams);
        }
        if (time != 0) {
            bundle.putInt("time", time);
        }
    }

    @Override
    public void restoreStateParams(Bundle bundle) {
        currentParams = bundle.getBundle("smsview_params");
        if (currentParams != null) {
            setParams(currentParams);
        }
        String code = bundle.getString("smsview_code");
        if (code != null) {
            codeField.setText(code);
        }
        Integer t = bundle.getInt("time");
        if (t != 0) {
            time = t;
        }
    }
}
