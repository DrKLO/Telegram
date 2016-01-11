/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SlideView;
import org.telegram.ui.Components.TypefaceSpan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ChangePhoneActivity extends BaseFragment {

    private int currentViewNum = 0;
    private SlideView[] views = new SlideView[2];
    private ProgressDialog progressDialog;

    private final static int done_button = 1;

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        for (SlideView v : views) {
            if (v != null) {
                v.onDestroyActivity();
            }
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
        actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == done_button) {
                    views[currentViewNum].onNextPressed();
                } else if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        fragmentView = new ScrollView(context);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout);
        ScrollView.LayoutParams layoutParams = (ScrollView.LayoutParams) frameLayout.getLayoutParams();
        layoutParams.width = ScrollView.LayoutParams.MATCH_PARENT;
        layoutParams.height = ScrollView.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        frameLayout.setLayoutParams(layoutParams);

        views[0] = new PhoneView(context);
        views[0].setVisibility(View.VISIBLE);
        frameLayout.addView(views[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 30, 16, 0));

        views[1] = new LoginActivitySmsView(context);
        views[1].setVisibility(View.GONE);
        frameLayout.addView(views[1], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 16, 30, 16, 0));

        try {
            if (views[0] == null || views[1] == null) {
                FrameLayout parent = (FrameLayout) ((ScrollView) fragmentView).getChildAt(0);
                for (int a = 0; a < views.length; a++) {
                    if (views[a] == null) {
                        views[a] = (SlideView) parent.getChildAt(a);
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        actionBar.setTitle(views[0].getHeaderName());

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public boolean onBackPressed() {
        if (currentViewNum == 0) {
            for (SlideView v : views) {
                if (v != null) {
                    v.onDestroyActivity();
                }
            }
            return true;
        } else if (currentViewNum == 1) {
            setPage(0, true, null, true);
        }
        return false;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            views[currentViewNum].onShow();
        }
    }

    public void needShowAlert(final String text) {
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setMessage(text);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    public void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new ProgressDialog(getParentActivity());
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    public void needHideProgress() {
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

    public void setPage(int page, boolean animated, Bundle params, boolean back) {
        if(android.os.Build.VERSION.SDK_INT > 10) {
            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;

            newView.setParams(params);
            actionBar.setTitle(newView.getHeaderName());
            newView.onShow();
            ViewProxy.setX(newView, back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);

            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
            animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
            animatorSet.setDuration(300);
            animatorSet.playTogether(
                    ObjectAnimatorProxy.ofFloat(outView, "translationX", back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x),
                    ObjectAnimatorProxy.ofFloat(newView, "translationX", 0));
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationStart(Object animation) {
                    newView.setVisibility(View.VISIBLE);
                }

                @SuppressLint("NewApi")
                @Override
                public void onAnimationEnd(Object animation) {
                    outView.setVisibility(View.GONE);
                    outView.setX(0);
                }
            });
            animatorSet.start();
        } else {
            views[currentViewNum].setVisibility(View.GONE);
            currentViewNum = page;
            views[page].setParams(params);
            views[page].setVisibility(View.VISIBLE);
            actionBar.setTitle(views[page].getHeaderName());
            views[page].onShow();
        }
    }

    public class PhoneView extends SlideView implements AdapterView.OnItemSelectedListener {

        private EditText codeField;
        private HintEditText phoneField;
        private TextView countryButton;

        private int countryState = 0;

        private ArrayList<String> countriesArray = new ArrayList<>();
        private HashMap<String, String> countriesMap = new HashMap<>();
        private HashMap<String, String> codesMap = new HashMap<>();
        private HashMap<String, String> phoneFormatMap = new HashMap<>();

        private boolean ignoreSelection = false;
        private boolean ignoreOnTextChange = false;
        private boolean ignoreOnPhoneChange = false;
        private boolean nextPressed = false;

        public PhoneView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            countryButton = new TextView(context);
            countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            countryButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), 0);
            countryButton.setTextColor(0xff212121);
            countryButton.setMaxLines(1);
            countryButton.setSingleLine(true);
            countryButton.setEllipsize(TextUtils.TruncateAt.END);
            countryButton.setGravity(Gravity.LEFT | Gravity.CENTER_HORIZONTAL);
            countryButton.setBackgroundResource(R.drawable.spinner_states);
            addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 20, 0, 20, 14));
            countryButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    CountrySelectActivity fragment = new CountrySelectActivity();
                    fragment.setCountrySelectActivityDelegate(new CountrySelectActivity.CountrySelectActivityDelegate() {
                        @Override
                        public void didSelectCountry(String name) {
                            selectCountry(name);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    AndroidUtilities.showKeyboard(phoneField);
                                }
                            }, 300);
                            phoneField.requestFocus();
                            phoneField.setSelection(phoneField.length());
                        }
                    });
                    presentFragment(fragment);
                }
            });

            View view = new View(context);
            view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            view.setBackgroundColor(0xffdbdbdb);
            addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 24, -17.5f, 24, 0));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

            TextView textView = new TextView(context);
            textView.setText("+");
            textView.setTextColor(0xff212121);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 24, 0, 0, 0));

            codeField = new EditText(context);
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setTextColor(0xff212121);
            AndroidUtilities.clearCursorDrawable(codeField);
            codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(5);
            codeField.setFilters(inputFilters);
            linearLayout.addView(codeField, LayoutHelper.createLinear(55, 36, -9, 0, 16, 0));
            codeField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                }

                @Override
                public void afterTextChanged(Editable editable) {
                    if (ignoreOnTextChange) {
                        ignoreOnTextChange = false;
                        return;
                    }
                    ignoreOnTextChange = true;
                    String text = PhoneFormat.stripExceptNumbers(codeField.getText().toString());
                    codeField.setText(text);
                    if (text.length() == 0) {
                        countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                        phoneField.setHintText(null);
                        countryState = 1;
                    } else {
                        String country;
                        boolean ok = false;
                        String textToSet = null;
                        if (text.length() > 4) {
                            ignoreOnTextChange = true;
                            for (int a = 4; a >= 1; a--) {
                                String sub = text.substring(0, a);
                                country = codesMap.get(sub);
                                if (country != null) {
                                    ok = true;
                                    textToSet = text.substring(a, text.length()) + phoneField.getText().toString();
                                    codeField.setText(text = sub);
                                    break;
                                }
                            }
                            if (!ok) {
                                ignoreOnTextChange = true;
                                textToSet = text.substring(1, text.length()) + phoneField.getText().toString();
                                codeField.setText(text = text.substring(0, 1));
                            }
                        }
                        country = codesMap.get(text);
                        if (country != null) {
                            int index = countriesArray.indexOf(country);
                            if (index != -1) {
                                ignoreSelection = true;
                                countryButton.setText(countriesArray.get(index));
                                String hint = phoneFormatMap.get(text);
                                phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
                                countryState = 0;
                            } else {
                                countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                                phoneField.setHintText(null);
                                countryState = 2;
                            }
                        } else {
                            countryButton.setText(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                            phoneField.setHintText(null);
                            countryState = 2;
                        }
                        if (!ok) {
                            codeField.setSelection(codeField.getText().length());
                        }
                        if (textToSet != null) {
                            phoneField.requestFocus();
                            phoneField.setText(textToSet);
                            phoneField.setSelection(phoneField.length());
                        }
                    }
                }
            });
            codeField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        phoneField.requestFocus();
                        phoneField.setSelection(phoneField.length());
                        return true;
                    }
                    return false;
                }
            });

            phoneField = new HintEditText(context);
            phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
            phoneField.setTextColor(0xff212121);
            phoneField.setHintTextColor(0xff979797);
            phoneField.setPadding(0, 0, 0, 0);
            AndroidUtilities.clearCursorDrawable(phoneField);
            phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            phoneField.setMaxLines(1);
            phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            linearLayout.addView(phoneField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 0, 24, 0));
            phoneField.addTextChangedListener(new TextWatcher() {

                private int characterAction = -1;
                private int actionPosition;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (count == 0 && after == 1) {
                        characterAction = 1;
                    } else if (count == 1 && after == 0) {
                        if (s.charAt(start) == ' ' && start > 0) {
                            characterAction = 3;
                            actionPosition = start - 1;
                        } else {
                            characterAction = 2;
                        }
                    } else {
                        characterAction = -1;
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreOnPhoneChange) {
                        return;
                    }
                    int start = phoneField.getSelectionStart();
                    String phoneChars = "0123456789";
                    String str = phoneField.getText().toString();
                    if (characterAction == 3) {
                        str = str.substring(0, actionPosition) + str.substring(actionPosition + 1, str.length());
                        start--;
                    }
                    StringBuilder builder = new StringBuilder(str.length());
                    for (int a = 0; a < str.length(); a++) {
                        String ch = str.substring(a, a + 1);
                        if (phoneChars.contains(ch)) {
                            builder.append(ch);
                        }
                    }
                    ignoreOnPhoneChange = true;
                    String hint = phoneField.getHintText();
                    if (hint != null) {
                        for (int a = 0; a < builder.length(); a++) {
                            if (a < hint.length()) {
                                if (hint.charAt(a) == ' ') {
                                    builder.insert(a, ' ');
                                    a++;
                                    if (start == a && characterAction != 2 && characterAction != 3) {
                                        start++;
                                    }
                                }
                            } else {
                                builder.insert(a, ' ');
                                if (start == a + 1 && characterAction != 2 && characterAction != 3) {
                                    start++;
                                }
                                break;
                            }
                        }
                    }
                    phoneField.setText(builder);
                    if (start >= 0) {
                        phoneField.setSelection(start <= phoneField.length() ? start : phoneField.length());
                    }
                    phoneField.onTextChange();
                    ignoreOnPhoneChange = false;
                }
            });
            phoneField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        onNextPressed();
                        return true;
                    }
                    return false;
                }
            });

            textView = new TextView(context);
            textView.setText(LocaleController.getString("ChangePhoneHelp", R.string.ChangePhoneHelp));
            textView.setTextColor(0xff757575);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.LEFT);
            textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 24, 28, 24, 10));

            HashMap<String, String> languageMap = new HashMap<>();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().getAssets().open("countries.txt")));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] args = line.split(";");
                    countriesArray.add(0, args[2]);
                    countriesMap.put(args[2], args[0]);
                    codesMap.put(args[0], args[2]);
                    if (args.length > 3) {
                        phoneFormatMap.put(args[0], args[3]);
                    }
                    languageMap.put(args[1], args[2]);
                }
                reader.close();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            Collections.sort(countriesArray, new Comparator<String>() {
                @Override
                public int compare(String lhs, String rhs) {
                    return lhs.compareTo(rhs);
                }
            });

            String country = null;

            try {
                TelephonyManager telephonyManager = (TelephonyManager)ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    country = telephonyManager.getSimCountryIso().toUpperCase();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            if (country != null) {
                String countryName = languageMap.get(country);
                if (countryName != null) {
                    int index = countriesArray.indexOf(countryName);
                    if (index != -1) {
                        codeField.setText(countriesMap.get(countryName));
                        countryState = 0;
                    }
                }
            }
            if (codeField.length() == 0) {
                countryButton.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                phoneField.setHintText(null);
                countryState = 1;
            }

            if (codeField.length() != 0) {
                AndroidUtilities.showKeyboard(phoneField);
                phoneField.requestFocus();
                phoneField.setSelection(phoneField.length());
            } else {
                AndroidUtilities.showKeyboard(codeField);
                codeField.requestFocus();
            }
        }

        public void selectCountry(String name) {
            int index = countriesArray.indexOf(name);
            if (index != -1) {
                ignoreOnTextChange = true;
                String code = countriesMap.get(name);
                codeField.setText(code);
                countryButton.setText(name);
                String hint = phoneFormatMap.get(code);
                phoneField.setHintText(hint != null ? hint.replace('X', '–') : null);
                countryState = 0;
            }
        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            if (ignoreSelection) {
                ignoreSelection = false;
                return;
            }
            ignoreOnTextChange = true;
            String str = countriesArray.get(i);
            codeField.setText(countriesMap.get(str));
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onNextPressed() {
            if (nextPressed) {
                return;
            }
            if (countryState == 1) {
                needShowAlert(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                return;
            } else if (countryState == 2 && !BuildVars.DEBUG_VERSION) {
                needShowAlert(LocaleController.getString("WrongCountry", R.string.WrongCountry));
                return;
            }
            if (codeField.length() == 0) {
                needShowAlert(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                return;
            }
            TLRPC.TL_account_sendChangePhoneCode req = new TLRPC.TL_account_sendChangePhoneCode();
            String phone = PhoneFormat.stripExceptNumbers("" + codeField.getText() + phoneField.getText());
            req.phone_number = phone;
            final String phone2 = "+" + codeField.getText() + " " + phoneField.getText();

            final Bundle params = new Bundle();
            params.putString("phone", phone2);
            params.putString("phoneFormated", phone);
            nextPressed = true;
            needShowProgress();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            nextPressed = false;
                            if (error == null) {
                                TLRPC.TL_account_sentChangePhoneCode res = (TLRPC.TL_account_sentChangePhoneCode)response;
                                params.putString("phoneHash", res.phone_code_hash);
                                params.putInt("calltime", res.send_call_timeout * 1000);
                                setPage(1, true, params, false);
                            } else {
                                if (error.text != null) {
                                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                        needShowAlert(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                        needShowAlert(LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                        needShowAlert(LocaleController.getString("CodeExpired", R.string.CodeExpired));
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        needShowAlert(LocaleController.getString("FloodWait", R.string.FloodWait));
                                    } else if (error.text.startsWith("PHONE_NUMBER_OCCUPIED")) {
                                        needShowAlert(LocaleController.formatString("ChangePhoneNumberOccupied", R.string.ChangePhoneNumberOccupied, phone2));
                                    } else {
                                        needShowAlert(LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
                                    }
                                }
                            }
                            needHideProgress();
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public void onShow() {
            super.onShow();
            if (phoneField != null) {
                if (codeField.length() != 0) {
                    AndroidUtilities.showKeyboard(phoneField);
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                } else {
                    AndroidUtilities.showKeyboard(codeField);
                    codeField.requestFocus();
                }
            }
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("ChangePhoneNewNumber", R.string.ChangePhoneNewNumber);
        }
    }

    public class LoginActivitySmsView extends SlideView implements NotificationCenter.NotificationCenterDelegate {

        private String phoneHash;
        private String requestPhone;
        private EditText codeField;
        private TextView confirmTextView;
        private TextView timeText;
        private Bundle currentParams;

        private Timer timeTimer;
        private Timer codeTimer;
        private final Object timerSync = new Object();
        private volatile int time = 60000;
        private volatile int codeTime = 15000;
        private double lastCurrentTime;
        private double lastCodeTime;
        private boolean ignoreOnTextChange;
        private boolean waitingForSms = false;
        private boolean nextPressed = false;
        private String lastError = "";

        public LoginActivitySmsView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(0xff757575);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(Gravity.LEFT);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(confirmTextView);
            LayoutParams layoutParams = (LayoutParams) confirmTextView.getLayoutParams();
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            layoutParams.gravity = Gravity.LEFT;
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            confirmTextView.setLayoutParams(layoutParams);

            codeField = new EditText(context);
            codeField.setTextColor(0xff212121);
            codeField.setHint(LocaleController.getString("Code", R.string.Code));
            AndroidUtilities.clearCursorDrawable(codeField);
            codeField.setHintTextColor(0xff979797);
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setMaxLines(1);
            codeField.setPadding(0, 0, 0, 0);
            addView(codeField);
            layoutParams = (LayoutParams) codeField.getLayoutParams();
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.height = AndroidUtilities.dp(36);
            layoutParams.gravity = Gravity.CENTER_HORIZONTAL;
            layoutParams.topMargin = AndroidUtilities.dp(20);
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            codeField.setLayoutParams(layoutParams);
            codeField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreOnTextChange) {
                        return;
                    }
                    if (codeField.length() == 5) {
                        onNextPressed();
                    }
                }
            });
            codeField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (i == EditorInfo.IME_ACTION_NEXT) {
                        onNextPressed();
                        return true;
                    }
                    return false;
                }
            });

            timeText = new TextView(context);
            timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            timeText.setTextColor(0xff757575);
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            timeText.setGravity(Gravity.LEFT);
            addView(timeText);
            layoutParams = (LayoutParams) timeText.getLayoutParams();
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            layoutParams.gravity = Gravity.LEFT;
            layoutParams.topMargin = AndroidUtilities.dp(30);
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            timeText.setLayoutParams(layoutParams);

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
            addView(linearLayout);
            layoutParams = (LayoutParams) linearLayout.getLayoutParams();
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.height = LayoutHelper.MATCH_PARENT;
            linearLayout.setLayoutParams(layoutParams);

            TextView wrongNumber = new TextView(context);
            wrongNumber.setGravity(Gravity.LEFT | Gravity.CENTER_HORIZONTAL);
            wrongNumber.setTextColor(0xff4d83b3);
            wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
            linearLayout.addView(wrongNumber);
            layoutParams = (LayoutParams) wrongNumber.getLayoutParams();
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            layoutParams.bottomMargin = AndroidUtilities.dp(10);
            layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.rightMargin = AndroidUtilities.dp(24);
            wrongNumber.setLayoutParams(layoutParams);
            wrongNumber.setText(LocaleController.getString("WrongNumber", R.string.WrongNumber));
            wrongNumber.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBackPressed();
                    setPage(0, true, null, true);
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
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            currentParams = params;
            waitingForSms = true;
            String phone = params.getString("phone");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            time = params.getInt("calltime");

            if (phone == null) {
                return;
            }

            String number = PhoneFormat.getInstance().format(phone);
            String str = String.format(Locale.US, LocaleController.getString("SentSmsCode", R.string.SentSmsCode) + " %s", number);
            try {
                SpannableStringBuilder stringBuilder = new SpannableStringBuilder(str);
                TypefaceSpan span = new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                int idx = str.indexOf(number);
                stringBuilder.setSpan(span, idx, idx + number.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                confirmTextView.setText(stringBuilder);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                confirmTextView.setText(str);
            }

            AndroidUtilities.showKeyboard(codeField);
            codeField.requestFocus();

            destroyTimer();
            destroyCodeTimer();
            timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 1, 0));
            lastCurrentTime = System.currentTimeMillis();

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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (codeTime <= 1000) {
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
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
                                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                    @Override
                                    public void run(TLObject response, final TLRPC.TL_error error) {
                                        if (error != null && error.text != null) {
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    lastError = error.text;
                                                }
                                            });
                                        }
                                    }
                                }, ConnectionsManager.RequestFlagFailOnServerErrors);
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
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            final TLRPC.TL_account_changePhone req = new TLRPC.TL_account_changePhone();
            req.phone_number = requestPhone;
            req.phone_code = codeField.getText().toString();
            req.phone_code_hash = phoneHash;
            destroyTimer();
            needShowProgress();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            needHideProgress();
                            nextPressed = false;
                            if (error == null) {
                                TLRPC.User user = (TLRPC.User) response;
                                destroyTimer();
                                destroyCodeTimer();
                                UserConfig.setCurrentUser(user);
                                UserConfig.saveConfig(true);
                                ArrayList<TLRPC.User> users = new ArrayList<>();
                                users.add(user);
                                MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                                MessagesController.getInstance().putUser(user, false);
                                finishFragment();
                            } else {
                                lastError = error.text;
                                createTimer();
                                if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                    needShowAlert(LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                    needShowAlert(LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                    needShowAlert(LocaleController.getString("CodeExpired", R.string.CodeExpired));
                                } else if (error.text.startsWith("FLOOD_WAIT")) {
                                    needShowAlert(LocaleController.getString("FloodWait", R.string.FloodWait));
                                } else {
                                    needShowAlert(error.text);
                                }
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public void onBackPressed() {
            destroyTimer();
            destroyCodeTimer();
            currentParams = null;
            AndroidUtilities.setWaitingForSms(false);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            waitingForSms = false;
        }

        @Override
        public void onDestroyActivity() {
            super.onDestroyActivity();
            AndroidUtilities.setWaitingForSms(false);
            NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
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
            if (id == NotificationCenter.didReceiveSmsCode) {
                if (!waitingForSms) {
                    return;
                }
                if (codeField != null) {
                    ignoreOnTextChange = true;
                    codeField.setText("" + args[0]);
                    onNextPressed();
                }
            }
        }
    }
}
