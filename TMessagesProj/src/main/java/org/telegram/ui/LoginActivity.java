/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SlideView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class LoginActivity extends BaseFragment {

    private int currentViewNum = 0;
    private SlideView[] views = new SlideView[8];
    private ProgressDialog progressDialog;
    private Dialog permissionsDialog;
    private ArrayList<String> permissionsItems = new ArrayList<>();
    private boolean checkPermissions = true;
    private View doneButton;

    private final static int done_button = 1;

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        for (int a = 0; a < views.length; a++) {
            if (views[a] != null) {
                views[a].onDestroyActivity();
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
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString("AppName", R.string.AppName));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == done_button) {
                    views[currentViewNum].onNextPressed();
                } else if (id == -1) {
                    onBackPressed();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        fragmentView = new ScrollView(context);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        views[0] = new PhoneView(context);
        views[1] = new LoginActivitySmsView(context, 1);
        views[2] = new LoginActivitySmsView(context, 2);
        views[3] = new LoginActivitySmsView(context, 3);
        views[4] = new LoginActivitySmsView(context, 4);
        views[5] = new LoginActivityRegisterView(context);
        views[6] = new LoginActivityPasswordView(context);
        views[7] = new LoginActivityRecoverView(context);

        for (int a = 0; a < views.length; a++) {
            views[a].setVisibility(a == 0 ? View.VISIBLE : View.GONE);
            frameLayout.addView(views[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, a == 0 ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 26 : 18, 30, AndroidUtilities.isTablet() ? 26 : 18, 0));
        }

        Bundle savedInstanceState = loadCurrentState();
        if (savedInstanceState != null) {
            currentViewNum = savedInstanceState.getInt("currentViewNum", 0);
            if (currentViewNum >= 1 && currentViewNum <= 4) {
                int time = savedInstanceState.getInt("open");
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    currentViewNum = 0;
                    savedInstanceState = null;
                    clearCurrentState();
                }
            }
        }
        actionBar.setTitle(views[currentViewNum].getHeaderName());
        for (int a = 0; a < views.length; a++) {
            if (savedInstanceState != null) {
                if (a >= 1 && a <= 4) {
                    if (a == currentViewNum) {
                        views[a].restoreStateParams(savedInstanceState);
                    }
                } else {
                    views[a].restoreStateParams(savedInstanceState);
                }
            }
            if (currentViewNum == a) {
                actionBar.setBackButtonImage(views[a].needBackButton() ? R.drawable.ic_ab_back : 0);
                views[a].setVisibility(View.VISIBLE);
                views[a].onShow();
                if (a == 3) {
                    doneButton.setVisibility(View.GONE);
                }
            } else {
                views[a].setVisibility(View.GONE);
            }
        }

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        try {
            if (currentViewNum >= 1 && currentViewNum <= 4 && views[currentViewNum] instanceof LoginActivitySmsView) {
                int time = ((LoginActivitySmsView) views[currentViewNum]).openTime;
                if (time != 0 && Math.abs(System.currentTimeMillis() / 1000 - time) >= 24 * 60 * 60) {
                    views[currentViewNum].onBackPressed();
                    setPage(0, false, null, true);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 6) {
            checkPermissions = false;
            if (currentViewNum == 0) {
                views[currentViewNum].onNextPressed();
            }
        }
    }

    private Bundle loadCurrentState() {
        try {
            Bundle bundle = new Bundle();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
            Map<String, ?> params = preferences.getAll();
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String[] args = key.split("_\\|_");
                if (args.length == 1) {
                    if (value instanceof String) {
                        bundle.putString(key, (String) value);
                    } else if (value instanceof Integer) {
                        bundle.putInt(key, (Integer) value);
                    }
                } else if (args.length == 2) {
                    Bundle inner = bundle.getBundle(args[0]);
                    if (inner == null) {
                        inner = new Bundle();
                        bundle.putBundle(args[0], inner);
                    }
                    if (value instanceof String) {
                        inner.putString(args[1], (String) value);
                    } else if (value instanceof Integer) {
                        inner.putInt(args[1], (Integer) value);
                    }
                }
            }
            return bundle;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return null;
    }

    private void clearCurrentState() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }

    private void putBundleToEditor(Bundle bundle, SharedPreferences.Editor editor, String prefix) {
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            Object obj = bundle.get(key);
            if (obj instanceof String) {
                if (prefix != null) {
                    editor.putString(prefix + "_|_" + key, (String) obj);
                } else {
                    editor.putString(key, (String) obj);
                }
            } else if (obj instanceof Integer) {
                if (prefix != null) {
                    editor.putInt(prefix + "_|_" + key, (Integer) obj);
                } else {
                    editor.putInt(key, (Integer) obj);
                }
            } else if (obj instanceof Bundle) {
                putBundleToEditor((Bundle) obj, editor, key);
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (Build.VERSION.SDK_INT >= 23 && dialog == permissionsDialog && !permissionsItems.isEmpty() && getParentActivity() != null) {
            getParentActivity().requestPermissions(permissionsItems.toArray(new String[permissionsItems.size()]), 6);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (currentViewNum == 0) {
            for (int a = 0; a < views.length; a++) {
                if (views[a] != null) {
                    views[a].onDestroyActivity();
                }
            }
            clearCurrentState();
            return true;
        } else if (currentViewNum == 6) {
            views[currentViewNum].onBackPressed();
            setPage(0, true, null, true);
        } else if (currentViewNum == 7) {
            views[currentViewNum].onBackPressed();
            setPage(6, true, null, true);
        }
        return false;
    }

    public void needShowAlert(String title, String text) {
        if (text == null || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
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
        if (page == 3) {
            doneButton.setVisibility(View.GONE);
        } else {
            if (page == 0) {
                checkPermissions = true;
            }
            doneButton.setVisibility(View.VISIBLE);
        }
        if (android.os.Build.VERSION.SDK_INT > 13 && animated) {
            final SlideView outView = views[currentViewNum];
            final SlideView newView = views[page];
            currentViewNum = page;
            actionBar.setBackButtonImage(newView.needBackButton() ? R.drawable.ic_ab_back : 0);

            newView.setParams(params);
            actionBar.setTitle(newView.getHeaderName());
            newView.onShow();
            newView.setX(back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);
            outView.animate().setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                }

                @SuppressLint("NewApi")
                @Override
                public void onAnimationEnd(Animator animator) {
                    outView.setVisibility(View.GONE);
                    outView.setX(0);
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }
            }).setDuration(300).translationX(back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x).start();
            newView.animate().setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    newView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                }

                @Override
                public void onAnimationCancel(Animator animator) {
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }
            }).setDuration(300).translationX(0).start();
        } else {
            actionBar.setBackButtonImage(views[page].needBackButton() ? R.drawable.ic_ab_back : 0);
            views[currentViewNum].setVisibility(View.GONE);
            currentViewNum = page;
            views[page].setParams(params);
            views[page].setVisibility(View.VISIBLE);
            actionBar.setTitle(views[page].getHeaderName());
            views[page].onShow();
        }
    }

    @Override
    public void saveSelfArgs(Bundle outState) {
        try {
            Bundle bundle = new Bundle();
            bundle.putInt("currentViewNum", currentViewNum);
            for (int a = 0; a <= currentViewNum; a++) {
                SlideView v = views[a];
                if (v != null) {
                    v.saveStateParams(bundle);
                }
            }
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("logininfo2", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.clear();
            putBundleToEditor(bundle, editor, null);
            editor.commit();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void needFinishActivity() {
        clearCurrentState();
        presentFragment(new DialogsActivity(null), true);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
    }

    private void fillNextCodeParams(Bundle params, TLRPC.TL_auth_sentCode res) {
        params.putString("phoneHash", res.phone_code_hash);
        if (res.next_type instanceof TLRPC.TL_auth_codeTypeCall) {
            params.putInt("nextType", 4);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeFlashCall) {
            params.putInt("nextType", 3);
        } else if (res.next_type instanceof TLRPC.TL_auth_codeTypeSms) {
            params.putInt("nextType", 2);
        }
        if (res.type instanceof TLRPC.TL_auth_sentCodeTypeApp) {
            params.putInt("type", 1);
            params.putInt("length", res.type.length);
            setPage(1, true, params, false);
        } else {
            if (res.timeout == 0) {
                res.timeout = 60;
            }
            params.putInt("timeout", res.timeout * 1000);
            if (res.type instanceof TLRPC.TL_auth_sentCodeTypeCall) {
                params.putInt("type", 4);
                params.putInt("length", res.type.length);
                setPage(4, true, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeFlashCall) {
                params.putInt("type", 3);
                params.putString("pattern", res.type.pattern);
                setPage(3, true, params, false);
            } else if (res.type instanceof TLRPC.TL_auth_sentCodeTypeSms) {
                params.putInt("type", 2);
                params.putInt("length", res.type.length);
                setPage(2, true, params, false);
            }
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
            countryButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            countryButton.setBackgroundResource(R.drawable.spinner_states);
            addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 0, 0, 14));
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
            addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 4, -17.5f, 4, 0));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

            TextView textView = new TextView(context);
            textView.setText("+");
            textView.setTextColor(0xff212121);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

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
                    ignoreOnTextChange = false;
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
            linearLayout.addView(phoneField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
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
            textView.setText(LocaleController.getString("StartText", R.string.StartText));
            textView.setTextColor(0xff757575);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 28, 0, 10));

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
                TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
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
                phoneField.requestFocus();
                phoneField.setSelection(phoneField.length());
            } else {
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
                ignoreOnTextChange = false;
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
            ignoreOnTextChange = false;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onNextPressed() {
            if (getParentActivity() == null || nextPressed) {
                return;
            }
            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            boolean simcardAvailable = tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
            boolean allowCall = true;
            if (Build.VERSION.SDK_INT >= 23 && simcardAvailable) {
                allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                boolean allowSms = getParentActivity().checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
                if (checkPermissions) {
                    permissionsItems.clear();
                    if (!allowCall) {
                        permissionsItems.add(Manifest.permission.READ_PHONE_STATE);
                    }
                    if (!allowSms) {
                        permissionsItems.add(Manifest.permission.RECEIVE_SMS);
                    }
                    if (!permissionsItems.isEmpty()) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        if (preferences.getBoolean("firstlogin", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
                            preferences.edit().putBoolean("firstlogin", false).commit();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            if (permissionsItems.size() == 2) {
                                builder.setMessage(LocaleController.getString("AllowReadCallAndSms", R.string.AllowReadCallAndSms));
                            } else if (!allowSms) {
                                builder.setMessage(LocaleController.getString("AllowReadSms", R.string.AllowReadSms));
                            } else {
                                builder.setMessage(LocaleController.getString("AllowReadCall", R.string.AllowReadCall));
                            }
                            permissionsDialog = showDialog(builder.create());
                        } else {
                            getParentActivity().requestPermissions(permissionsItems.toArray(new String[permissionsItems.size()]), 6);
                        }
                        return;
                    }
                }
            }

            if (countryState == 1) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                return;
            } else if (countryState == 2 && !BuildVars.DEBUG_VERSION && !codeField.getText().toString().equals("999")) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("WrongCountry", R.string.WrongCountry));
                return;
            }
            if (codeField.length() == 0) {
                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                return;
            }

            ConnectionsManager.getInstance().cleanUp();
            TLRPC.TL_auth_sendCode req = new TLRPC.TL_auth_sendCode();
            String phone = PhoneFormat.stripExceptNumbers("" + codeField.getText() + phoneField.getText());
            ConnectionsManager.getInstance().applyCountryPortNumber(phone);
            req.api_hash = BuildVars.APP_HASH;
            req.api_id = BuildVars.APP_ID;
            req.phone_number = phone;
            req.lang_code = LocaleController.getLocaleStringIso639();
            if (req.lang_code.length() == 0) {
                req.lang_code = "en";
            }
            req.allow_flashcall = simcardAvailable && allowCall;
            if (req.allow_flashcall) {
                String number = tm.getLine1Number();
                req.current_number = number != null && number.length() != 0 && (phone.contains(number) || number.contains(phone));
            }

            final Bundle params = new Bundle();
            params.putString("phone", "+" + codeField.getText() + phoneField.getText());
            try {
                params.putString("ephone", "+" + PhoneFormat.stripExceptNumbers(codeField.getText().toString()) + " " + PhoneFormat.stripExceptNumbers(phoneField.getText().toString()));
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                params.putString("ephone", "+" + phone);
            }
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
                                fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                            } else {
                                if (error.text != null) {
                                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                                    } else if (error.code != -1000) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                    }
                                }
                            }
                            needHideProgress();
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagTryDifferentDc | ConnectionsManager.RequestFlagEnableUnauthorized);
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
            return LocaleController.getString("YourPhone", R.string.YourPhone);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("phoneview_code", code);
            }
            String phone = phoneField.getText().toString();
            if (phone.length() != 0) {
                bundle.putString("phoneview_phone", phone);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            String code = bundle.getString("phoneview_code");
            if (code != null) {
                codeField.setText(code);
            }
            String phone = bundle.getString("phoneview_phone");
            if (phone != null) {
                phoneField.setText(phone);
            }
        }
    }

    public class LoginActivitySmsView extends SlideView implements NotificationCenter.NotificationCenterDelegate {

        private class ProgressView extends View {

            private Paint paint = new Paint();
            private Paint paint2 = new Paint();
            private float progress;

            public ProgressView(Context context) {
                super(context);
                paint.setColor(0xffe1eaf2);
                paint2.setColor(0xff62a0d0);
            }

            public void setProgress(float value) {
                progress = value;
                invalidate();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int start = (int) (getMeasuredWidth() * progress);
                canvas.drawRect(0, 0, start, getMeasuredHeight(), paint2);
                canvas.drawRect(start, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
            }
        }

        private String phone;
        private String phoneHash;
        private String requestPhone;
        private String emailPhone;
        private EditText codeField;
        private TextView confirmTextView;
        private TextView timeText;
        private TextView problemText;
        private Bundle currentParams;
        private ProgressView progressView;

        private Timer timeTimer;
        private Timer codeTimer;
        private int openTime;
        private final Object timerSync = new Object();
        private volatile int time = 60000;
        private volatile int codeTime = 15000;
        private double lastCurrentTime;
        private double lastCodeTime;
        private boolean ignoreOnTextChange;
        private boolean waitingForEvent;
        private boolean nextPressed;
        private String lastError = "";
        private int currentType;
        private int nextType;
        private String pattern = "*";
        private int length;
        private int timeout;

        public LoginActivitySmsView(Context context, final int type) {
            super(context);

            currentType = type;
            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(0xff757575);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);

            if (currentType == 3) {
                FrameLayout frameLayout = new FrameLayout(context);

                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.phone_activate);
                if (LocaleController.isRTL) {
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.LEFT | Gravity.CENTER_VERTICAL, 2, 2, 0, 0));
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 64 + 18, 0, 0, 0));
                } else {
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 64 + 18, 0));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 2, 0, 2));
                }
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            } else {
                addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            }

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
            addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
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
                    if (length != 0 && codeField.length() == length) {
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
            if (currentType == 3) {
                codeField.setEnabled(false);
                codeField.setInputType(InputType.TYPE_NULL);
                codeField.setVisibility(GONE);
            }

            timeText = new TextView(context);
            timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            timeText.setTextColor(0xff757575);
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            timeText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 30, 0, 0));

            if (currentType == 3) {
                progressView = new ProgressView(context);
                addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3, 0, 12, 0, 0));
            }

            problemText = new TextView(context);
            problemText.setText(LocaleController.getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
            problemText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            problemText.setTextColor(0xff4d83b3);
            problemText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            problemText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(12));
            addView(problemText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 20, 0, 0));
            problemText.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (nextPressed) {
                        return;
                    }
                    if (nextType != 0 && nextType != 4) {
                        resendCode();
                    } else {
                        try {
                            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                            String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                            Intent mailer = new Intent(Intent.ACTION_SEND);
                            mailer.setType("message/rfc822");
                            mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{"sms@stel.com"});
                            mailer.putExtra(Intent.EXTRA_SUBJECT, "Android registration/login issue " + version + " " + emailPhone);
                            mailer.putExtra(Intent.EXTRA_TEXT, "Phone: " + requestPhone + "\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault() + "\nError: " + lastError);
                            getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
                        } catch (Exception e) {
                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
                        }
                    }
                }
            });

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            TextView wrongNumber = new TextView(context);
            wrongNumber.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            wrongNumber.setTextColor(0xff4d83b3);
            wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
            linearLayout.addView(wrongNumber, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 0, 0, 10));
            wrongNumber.setText(LocaleController.getString("WrongNumber", R.string.WrongNumber));
            wrongNumber.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    TLRPC.TL_auth_cancelCode req = new TLRPC.TL_auth_cancelCode();
                    req.phone_number = requestPhone;
                    req.phone_code_hash = phoneHash;
                    ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {

                        }
                    }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    onBackPressed();
                    setPage(0, true, null, true);
                }
            });
        }

        private void resendCode() {
            final Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);

            nextPressed = true;
            needShowProgress();

            TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            nextPressed = false;
                            if (error == null) {
                                fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                            } else {
                                if (error.text != null) {
                                    if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                    } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                        onBackPressed();
                                        setPage(0, true, null, true);
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                                    } else if (error.code != -1000) {
                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                                    }
                                }
                            }
                            needHideProgress();
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
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
            waitingForEvent = true;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(true);
                NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(true);
                NotificationCenter.getInstance().addObserver(this, NotificationCenter.didReceiveCall);
            }

            currentParams = params;
            phone = params.getString("phone");
            emailPhone = params.getString("ephone");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            timeout = time = params.getInt("timeout");
            openTime = (int) (System.currentTimeMillis() / 1000);
            nextType = params.getInt("nextType");
            pattern = params.getString("pattern");
            length = params.getInt("length");

            if (length != 0) {
                InputFilter[] inputFilters = new InputFilter[1];
                inputFilters[0] = new InputFilter.LengthFilter(length);
                codeField.setFilters(inputFilters);
            } else {
                codeField.setFilters(new InputFilter[0]);
            }
            if (progressView != null) {
                progressView.setVisibility(nextType != 0 ? VISIBLE : GONE);
            }

            if (phone == null) {
                return;
            }

            String number = PhoneFormat.getInstance().format(phone);
            CharSequence str = "";
            if (currentType == 1) {
                str = AndroidUtilities.replaceTags(LocaleController.getString("SentAppCode", R.string.SentAppCode));
            } else if (currentType == 2) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentSmsCode", R.string.SentSmsCode, number));
            } else if (currentType == 3) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallCode", R.string.SentCallCode, number));
            } else if (currentType == 4) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallOnly", R.string.SentCallOnly, number));
            }
            confirmTextView.setText(str);

            if (currentType != 3) {
                AndroidUtilities.showKeyboard(codeField);
                codeField.requestFocus();
            } else {
                AndroidUtilities.hideKeyboard(codeField);
            }

            destroyTimer();
            destroyCodeTimer();

            lastCurrentTime = System.currentTimeMillis();
            if (currentType == 1) {
                problemText.setVisibility(VISIBLE);
                timeText.setVisibility(GONE);
            } else if (currentType == 3 && (nextType == 4 || nextType == 2)) {
                problemText.setVisibility(GONE);
                timeText.setVisibility(VISIBLE);
                if (nextType == 4) {
                    timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 1, 0));
                } else if (nextType == 2) {
                    timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 1, 0));
                }
                createTimer();
            } else if (currentType == 2 && (nextType == 4 || nextType == 3)) {
                timeText.setVisibility(VISIBLE);
                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 2, 0));
                problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                createTimer();
            } else {
                timeText.setVisibility(GONE);
                problemText.setVisibility(GONE);
                createCodeTimer();
            }
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
                synchronized (timerSync) {
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
                    if (timeTimer == null) {
                        return;
                    }
                    final double currentTime = System.currentTimeMillis();
                    double diff = currentTime - lastCurrentTime;
                    time -= diff;
                    lastCurrentTime = currentTime;
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (time >= 1000) {
                                int minutes = time / 1000 / 60;
                                int seconds = time / 1000 - minutes * 60;
                                if (nextType == 4 || nextType == 3) {
                                    timeText.setText(LocaleController.formatString("CallText", R.string.CallText, minutes, seconds));
                                } else if (nextType == 2) {
                                    timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, minutes, seconds));
                                }
                                if (progressView != null) {
                                    progressView.setProgress(1.0f - (float) time / (float) timeout);
                                }
                            } else {
                                if (progressView != null) {
                                    progressView.setProgress(1.0f);
                                }
                                destroyTimer();
                                if (currentType == 3) {
                                    AndroidUtilities.setWaitingForCall(false);
                                    NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveCall);
                                    waitingForEvent = false;
                                    destroyCodeTimer();
                                    resendCode();
                                } else if (currentType == 2) {
                                    if (nextType == 4) {
                                        timeText.setText(LocaleController.getString("Calling", R.string.Calling));
                                        createCodeTimer();
                                        TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
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
                                        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                                    } else if (nextType == 3) {
                                        AndroidUtilities.setWaitingForSms(false);
                                        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
                                        waitingForEvent = false;
                                        destroyCodeTimer();
                                        resendCode();
                                    }
                                }
                            }
                        }
                    });
                }
            }, 0, 1000);
        }

        private void destroyTimer() {
            try {
                synchronized (timerSync) {
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
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            final TLRPC.TL_auth_signIn req = new TLRPC.TL_auth_signIn();
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
                            nextPressed = false;
                            if (error == null) {
                                needHideProgress();
                                TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization) response;
                                ConnectionsManager.getInstance().setUserId(res.user.id);
                                destroyTimer();
                                destroyCodeTimer();
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
                                needFinishActivity();
                            } else {
                                lastError = error.text;

                                if (error.text.contains("PHONE_NUMBER_UNOCCUPIED")) {
                                    needHideProgress();
                                    Bundle params = new Bundle();
                                    params.putString("phoneFormated", requestPhone);
                                    params.putString("phoneHash", phoneHash);
                                    params.putString("code", req.phone_code);
                                    setPage(5, true, params, false);
                                    destroyTimer();
                                    destroyCodeTimer();
                                } else if (error.text.contains("SESSION_PASSWORD_NEEDED")) {
                                    TLRPC.TL_account_getPassword req2 = new TLRPC.TL_account_getPassword();
                                    ConnectionsManager.getInstance().sendRequest(req2, new RequestDelegate() {
                                        @Override
                                        public void run(final TLObject response, final TLRPC.TL_error error) {
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    needHideProgress();
                                                    if (error == null) {
                                                        TLRPC.TL_account_password password = (TLRPC.TL_account_password) response;
                                                        Bundle bundle = new Bundle();
                                                        bundle.putString("current_salt", Utilities.bytesToHex(password.current_salt));
                                                        bundle.putString("hint", password.hint);
                                                        bundle.putString("email_unconfirmed_pattern", password.email_unconfirmed_pattern);
                                                        bundle.putString("phoneFormated", requestPhone);
                                                        bundle.putString("phoneHash", phoneHash);
                                                        bundle.putString("code", req.phone_code);
                                                        bundle.putInt("has_recovery", password.has_recovery ? 1 : 0);
                                                        setPage(6, true, bundle, false);
                                                    } else {
                                                        needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                                    }
                                                }
                                            });
                                        }
                                    }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                                    destroyTimer();
                                    destroyCodeTimer();
                                } else {
                                    needHideProgress();
                                    if (currentType == 3 && (nextType == 4 || nextType == 2) || currentType == 2 && (nextType == 4 || nextType == 3)) {
                                        createTimer();
                                    }
                                    if (currentType == 2) {
                                        AndroidUtilities.setWaitingForSms(true);
                                        NotificationCenter.getInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                                    } else if (currentType == 3) {
                                        AndroidUtilities.setWaitingForCall(true);
                                        NotificationCenter.getInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                                    }
                                    waitingForEvent = true;
                                    if (currentType != 3) {
                                        if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                        } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                        } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                            onBackPressed();
                                            setPage(0, true, null, true);
                                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                                        } else if (error.text.startsWith("FLOOD_WAIT")) {
                                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("FloodWait", R.string.FloodWait));
                                        } else {
                                            needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text);
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public void onBackPressed() {
            destroyTimer();
            destroyCodeTimer();
            currentParams = null;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
        }

        @Override
        public void onDestroyActivity() {
            super.onDestroyActivity();
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            destroyTimer();
            destroyCodeTimer();
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
            if (!waitingForEvent || codeField == null) {
                return;
            }
            if (id == NotificationCenter.didReceiveSmsCode) {
                ignoreOnTextChange = true;
                codeField.setText("" + args[0]);
                ignoreOnTextChange = false;
                onNextPressed();
            } else if (id == NotificationCenter.didReceiveCall) {
                String num = "" + args[0];
                if (!pattern.equals("*")) {
                    String patternNumbers = pattern.replace("*", "");
                    if (!num.contains(patternNumbers)) {
                        return;
                    }
                }
                ignoreOnTextChange = true;
                codeField.setText(num);
                ignoreOnTextChange = false;
                onNextPressed();
            }
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("smsview_code_" + currentType, code);
            }
            if (currentParams != null) {
                bundle.putBundle("smsview_params_" + currentType, currentParams);
            }
            if (time != 0) {
                bundle.putInt("time", time);
            }
            if (openTime != 0) {
                bundle.putInt("open", openTime);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("smsview_params_" + currentType);
            if (currentParams != null) {
                setParams(currentParams);
            }
            String code = bundle.getString("smsview_code_" + currentType);
            if (code != null) {
                codeField.setText(code);
            }
            int t = bundle.getInt("time");
            if (t != 0) {
                time = t;
            }
            int t2 = bundle.getInt("open");
            if (t2 != 0) {
                openTime = t2;
            }
        }
    }

    public class LoginActivityPasswordView extends SlideView {

        private EditText codeField;
        private TextView confirmTextView;
        private TextView resetAccountButton;
        private TextView resetAccountText;

        private Bundle currentParams;
        private boolean nextPressed;
        private byte[] current_salt;
        private String hint;
        private String email_unconfirmed_pattern;
        private boolean has_recovery;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;

        public LoginActivityPasswordView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(0xff757575);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setText(LocaleController.getString("LoginPasswordText", R.string.LoginPasswordText));
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            codeField = new EditText(context);
            codeField.setTextColor(0xff212121);
            AndroidUtilities.clearCursorDrawable(codeField);
            codeField.setHintTextColor(0xff979797);
            codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setPadding(0, 0, 0, 0);
            codeField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            codeField.setTypeface(Typeface.DEFAULT);
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
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

            TextView cancelButton = new TextView(context);
            cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            cancelButton.setTextColor(0xff4d83b3);
            cancelButton.setText(LocaleController.getString("ForgotPassword", R.string.ForgotPassword));
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
            cancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (has_recovery) {
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
                                                    Bundle bundle = new Bundle();
                                                    bundle.putString("email_unconfirmed_pattern", res.email_pattern);
                                                    setPage(7, true, bundle, false);
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
                                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                            } else {
                                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                            }
                                        }
                                    }
                                });
                            }
                        }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
                    } else {
                        resetAccountText.setVisibility(VISIBLE);
                        resetAccountButton.setVisibility(VISIBLE);
                        AndroidUtilities.hideKeyboard(codeField);
                        needShowAlert(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle), LocaleController.getString("RestorePasswordNoEmailText", R.string.RestorePasswordNoEmailText));
                    }
                }
            });

            resetAccountButton = new TextView(context);
            resetAccountButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountButton.setTextColor(0xffff6666);
            resetAccountButton.setVisibility(GONE);
            resetAccountButton.setText(LocaleController.getString("ResetMyAccount", R.string.ResetMyAccount));
            resetAccountButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            resetAccountButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            resetAccountButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(resetAccountButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 34, 0, 0));
            resetAccountButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("ResetMyAccountWarningText", R.string.ResetMyAccountWarningText));
                    builder.setTitle(LocaleController.getString("ResetMyAccountWarning", R.string.ResetMyAccountWarning));
                    builder.setPositiveButton(LocaleController.getString("ResetMyAccountWarningReset", R.string.ResetMyAccountWarningReset), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            needShowProgress();
                            TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                            req.reason = "Forgot password";
                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                @Override
                                public void run(TLObject response, final TLRPC.TL_error error) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            needHideProgress();
                                            if (error == null) {
                                                Bundle params = new Bundle();
                                                params.putString("phoneFormated", requestPhone);
                                                params.putString("phoneHash", phoneHash);
                                                params.putString("code", phoneCode);
                                                setPage(5, true, params, false);
                                            } else {
                                                needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                            }
                                        }
                                    });
                                }
                            }, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            });

            resetAccountText = new TextView(context);
            resetAccountText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            resetAccountText.setVisibility(GONE);
            resetAccountText.setTextColor(0xff757575);
            resetAccountText.setText(LocaleController.getString("ResetMyAccountText", R.string.ResetMyAccountText));
            resetAccountText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            resetAccountText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(resetAccountText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 7, 0, 14));
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void setParams(Bundle params) {
            if (params == null) {
                return;
            }
            if (params.isEmpty()) {
                resetAccountButton.setVisibility(VISIBLE);
                resetAccountText.setVisibility(VISIBLE);
                AndroidUtilities.hideKeyboard(codeField);
                return;
            }
            resetAccountButton.setVisibility(GONE);
            resetAccountText.setVisibility(GONE);
            codeField.setText("");
            currentParams = params;
            current_salt = Utilities.hexToBytes(currentParams.getString("current_salt"));
            hint = currentParams.getString("hint");
            has_recovery = currentParams.getInt("has_recovery") == 1;
            email_unconfirmed_pattern = currentParams.getString("email_unconfirmed_pattern");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            phoneCode = params.getString("code");

            AndroidUtilities.showKeyboard(codeField);
            codeField.requestFocus();


            if (hint != null && hint.length() > 0) {
                codeField.setHint(hint);
            } else {
                codeField.setHint(LocaleController.getString("LoginPassword", R.string.LoginPassword));
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
                codeField.setText("");
            }
            AndroidUtilities.shakeView(confirmTextView, 2, 0);
        }

        @Override
        public void onNextPressed() {
            if (nextPressed) {
                return;
            }

            String oldPassword = codeField.getText().toString();
            if (oldPassword.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;
            byte[] oldPasswordBytes = null;
            try {
                oldPasswordBytes = oldPassword.getBytes("UTF-8");
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            needShowProgress();
            byte[] hash = new byte[current_salt.length * 2 + oldPasswordBytes.length];
            System.arraycopy(current_salt, 0, hash, 0, current_salt.length);
            System.arraycopy(oldPasswordBytes, 0, hash, current_salt.length, oldPasswordBytes.length);
            System.arraycopy(current_salt, 0, hash, hash.length - current_salt.length, current_salt.length);

            final TLRPC.TL_auth_checkPassword req = new TLRPC.TL_auth_checkPassword();
            req.password_hash = Utilities.computeSHA256(hash, 0, hash.length);
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            needHideProgress();
                            nextPressed = false;
                            if (error == null) {
                                TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization) response;
                                ConnectionsManager.getInstance().setUserId(res.user.id);
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
                                needFinishActivity();
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
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                } else {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                }
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onBackPressed() {
            currentParams = null;
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
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code.length() != 0) {
                bundle.putString("passview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("passview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("passview_params");
            if (currentParams != null) {
                setParams(currentParams);
            }
            String code = bundle.getString("passview_code");
            if (code != null) {
                codeField.setText(code);
            }
        }
    }

    public class LoginActivityRecoverView extends SlideView {

        private EditText codeField;
        private TextView confirmTextView;
        private TextView cancelButton;

        private Bundle currentParams;
        private boolean nextPressed;
        private String email_unconfirmed_pattern;

        public LoginActivityRecoverView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            confirmTextView = new TextView(context);
            confirmTextView.setTextColor(0xff757575);
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            confirmTextView.setText(LocaleController.getString("RestoreEmailSentInfo", R.string.RestoreEmailSentInfo));
            addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            codeField = new EditText(context);
            codeField.setTextColor(0xff212121);
            AndroidUtilities.clearCursorDrawable(codeField);
            codeField.setHintTextColor(0xff979797);
            codeField.setHint(LocaleController.getString("PasswordCode", R.string.PasswordCode));
            codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            codeField.setMaxLines(1);
            codeField.setPadding(0, 0, 0, 0);
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            codeField.setTypeface(Typeface.DEFAULT);
            codeField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(codeField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));
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

            cancelButton = new TextView(context);
            cancelButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
            cancelButton.setTextColor(0xff4d83b3);
            cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelButton.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            cancelButton.setPadding(0, AndroidUtilities.dp(14), 0, 0);
            addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 0, 0, 14));
            cancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setMessage(LocaleController.getString("RestoreEmailTroubleText", R.string.RestoreEmailTroubleText));
                    builder.setTitle(LocaleController.getString("RestorePasswordNoEmailTitle", R.string.RestorePasswordNoEmailTitle));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            setPage(6, true, new Bundle(), true);
                        }
                    });
                    Dialog dialog = showDialog(builder.create());
                    if (dialog != null) {
                        dialog.setCanceledOnTouchOutside(false);
                        dialog.setCancelable(false);
                    }
                }
            });
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public String getHeaderName() {
            return LocaleController.getString("LoginPassword", R.string.LoginPassword);
        }

        @Override
        public void setParams(Bundle params) {
            if (params == null) {
                return;
            }
            codeField.setText("");
            currentParams = params;
            email_unconfirmed_pattern = currentParams.getString("email_unconfirmed_pattern");
            cancelButton.setText(LocaleController.formatString("RestoreEmailTrouble", R.string.RestoreEmailTrouble, email_unconfirmed_pattern));

            AndroidUtilities.showKeyboard(codeField);
            codeField.requestFocus();
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
                codeField.setText("");
            }
            AndroidUtilities.shakeView(confirmTextView, 2, 0);
        }

        @Override
        public void onNextPressed() {
            if (nextPressed) {
                return;
            }

            String oldPassword = codeField.getText().toString();
            if (oldPassword.length() == 0) {
                onPasscodeError(false);
                return;
            }
            nextPressed = true;

            String code = codeField.getText().toString();
            if (code.length() == 0) {
                onPasscodeError(false);
                return;
            }
            needShowProgress();
            TLRPC.TL_auth_recoverPassword req = new TLRPC.TL_auth_recoverPassword();
            req.code = code;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            needHideProgress();
                            nextPressed = false;
                            if (error == null) {
                                TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization) response;
                                ConnectionsManager.getInstance().setUserId(res.user.id);
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
                                needFinishActivity();
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
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.formatString("FloodWaitTime", R.string.FloodWaitTime, timeString));
                                } else {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                }
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);
        }

        @Override
        public void onBackPressed() {
            currentParams = null;
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
        public void saveStateParams(Bundle bundle) {
            String code = codeField.getText().toString();
            if (code != null && code.length() != 0) {
                bundle.putString("recoveryview_code", code);
            }
            if (currentParams != null) {
                bundle.putBundle("recoveryview_params", currentParams);
            }
        }

        @Override
        public void restoreStateParams(Bundle bundle) {
            currentParams = bundle.getBundle("recoveryview_params");
            if (currentParams != null) {
                setParams(currentParams);
            }
            String code = bundle.getString("recoveryview_code");
            if (code != null) {
                codeField.setText(code);
            }
        }
    }

    public class LoginActivityRegisterView extends SlideView {

        private EditText firstNameField;
        private EditText lastNameField;
        private String requestPhone;
        private String phoneHash;
        private String phoneCode;
        private Bundle currentParams;
        private boolean nextPressed = false;

        public LoginActivityRegisterView(Context context) {
            super(context);

            setOrientation(VERTICAL);

            TextView textView = new TextView(context);
            textView.setText(LocaleController.getString("RegisterText", R.string.RegisterText));
            textView.setTextColor(0xff757575);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 8, 0, 0));

            firstNameField = new EditText(context);
            firstNameField.setHintTextColor(0xff979797);
            firstNameField.setTextColor(0xff212121);
            AndroidUtilities.clearCursorDrawable(firstNameField);
            firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
            firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            firstNameField.setMaxLines(1);
            firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            addView(firstNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 26, 0, 0));
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

            lastNameField = new EditText(context);
            lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
            lastNameField.setHintTextColor(0xff979797);
            lastNameField.setTextColor(0xff212121);
            AndroidUtilities.clearCursorDrawable(lastNameField);
            lastNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            lastNameField.setMaxLines(1);
            lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            addView(lastNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 10, 0, 0));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            TextView wrongNumber = new TextView(context);
            wrongNumber.setText(LocaleController.getString("CancelRegistration", R.string.CancelRegistration));
            wrongNumber.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            wrongNumber.setTextColor(0xff4d83b3);
            wrongNumber.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            wrongNumber.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            wrongNumber.setPadding(0, AndroidUtilities.dp(24), 0, 0);
            linearLayout.addView(wrongNumber, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 0, 0, 10));
            wrongNumber.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("AreYouSureRegistration", R.string.AreYouSureRegistration));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            onBackPressed();
                            setPage(0, true, null, true);
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
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
            if (nextPressed) {
                return;
            }
            nextPressed = true;
            TLRPC.TL_auth_signUp req = new TLRPC.TL_auth_signUp();
            req.phone_code = phoneCode;
            req.phone_code_hash = phoneHash;
            req.phone_number = requestPhone;
            req.first_name = firstNameField.getText().toString();
            req.last_name = lastNameField.getText().toString();
            needShowProgress();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            nextPressed = false;
                            needHideProgress();
                            if (error == null) {
                                final TLRPC.TL_auth_authorization res = (TLRPC.TL_auth_authorization) response;
                                ConnectionsManager.getInstance().setUserId(res.user.id);
                                UserConfig.clearConfig();
                                MessagesController.getInstance().cleanUp();
                                UserConfig.setCurrentUser(res.user);
                                UserConfig.saveConfig(true);
                                MessagesStorage.getInstance().cleanUp(true);
                                ArrayList<TLRPC.User> users = new ArrayList<>();
                                users.add(res.user);
                                MessagesStorage.getInstance().putUsersAndChats(users, null, true, true);
                                //MessagesController.getInstance().uploadAndApplyUserAvatar(avatarPhotoBig);
                                MessagesController.getInstance().putUser(res.user, false);
                                ContactsController.getInstance().checkAppAccount();
                                MessagesController.getInstance().getBlockedUsers(true);
                                needFinishActivity();
                            } else {
                                if (error.text.contains("PHONE_NUMBER_INVALID")) {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                                } else if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidCode", R.string.InvalidCode));
                                } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("CodeExpired", R.string.CodeExpired));
                                } else if (error.text.contains("FIRSTNAME_INVALID")) {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidFirstName", R.string.InvalidFirstName));
                                } else if (error.text.contains("LASTNAME_INVALID")) {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), LocaleController.getString("InvalidLastName", R.string.InvalidLastName));
                                } else {
                                    needShowAlert(LocaleController.getString("AppName", R.string.AppName), error.text);
                                }
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagWithoutLogin | ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public void saveStateParams(Bundle bundle) {
            String first = firstNameField.getText().toString();
            if (first.length() != 0) {
                bundle.putString("registerview_first", first);
            }
            String last = lastNameField.getText().toString();
            if (last.length() != 0) {
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
}
