/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.SettingsSuggestionCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.SlideView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ChangePhoneActivity extends BaseFragment {

    private int currentViewNum = 0;
    private SlideView[] views = new SlideView[5];
    private AlertDialog progressDialog;
    private Dialog permissionsDialog;
    private ArrayList<String> permissionsItems = new ArrayList<>();
    private boolean checkPermissions = true;
    private View doneButton;

    private int scrollHeight;

    private final static int done_button = 1;

    private static class ProgressView extends View {

        private Paint paint = new Paint();
        private Paint paint2 = new Paint();
        private float progress;

        public ProgressView(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_login_progressInner));
            paint2.setColor(Theme.getColor(Theme.key_login_progressOuter));
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
                FileLog.e(e);
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
                    views[currentViewNum].onNextPressed(null);
                } else if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        ScrollView scrollView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                if (currentViewNum == 1 || currentViewNum == 2 || currentViewNum == 4) {
                    rectangle.bottom += AndroidUtilities.dp(40);
                }
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                scrollHeight = MeasureSpec.getSize(heightMeasureSpec) - AndroidUtilities.dp(30);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        scrollView.setFillViewport(true);
        fragmentView = scrollView;

        FrameLayout frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        views[0] = new PhoneView(context);
        views[1] = new LoginActivitySmsView(context, 1);
        views[2] = new LoginActivitySmsView(context, 2);
        views[3] = new LoginActivitySmsView(context, 3);
        views[4] = new LoginActivitySmsView(context, 4);

        for (int a = 0; a < views.length; a++) {
            views[a].setVisibility(a == 0 ? View.VISIBLE : View.GONE);
            frameLayout.addView(views[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, a == 0 ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 26 : 18, 30, AndroidUtilities.isTablet() ? 26 : 18, 0));
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
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 6) {
            checkPermissions = false;
            if (currentViewNum == 0) {
                views[currentViewNum].onNextPressed(null);
            }
        }
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        if (Build.VERSION.SDK_INT >= 23 && dialog == permissionsDialog && !permissionsItems.isEmpty()) {
            getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
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
            return true;
        } else {
            if (views[currentViewNum].onBackPressed(false)) {
                setPage(0, true, null, true);
            }
        }
        return false;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            views[currentViewNum].onShow();
        }
    }

    public void needShowProgress() {
        if (getParentActivity() == null || getParentActivity().isFinishing() || progressDialog != null) {
            return;
        }
        progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.show();
    }

    public void needHideProgress() {
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

    public void setPage(int page, boolean animated, Bundle params, boolean back) {
        if (page == 3) {
            doneButton.setVisibility(View.GONE);
        } else {
            if (page == 0) {
                checkPermissions = true;
            }
            doneButton.setVisibility(View.VISIBLE);
        }
        final SlideView outView = views[currentViewNum];
        final SlideView newView = views[page];
        currentViewNum = page;

        newView.setParams(params, false);
        actionBar.setTitle(newView.getHeaderName());
        newView.onShow();
        newView.setX(back ? -AndroidUtilities.displaySize.x : AndroidUtilities.displaySize.x);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.setDuration(300);
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(outView, "translationX", back ? AndroidUtilities.displaySize.x : -AndroidUtilities.displaySize.x),
                ObjectAnimator.ofFloat(newView, "translationX", 0));
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                newView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                outView.setVisibility(View.GONE);
                outView.setX(0);
            }
        });
        animatorSet.start();
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

        private EditTextBoldCursor codeField;
        private HintEditText phoneField;
        private TextView countryButton;
        private View view;
        private TextView textView;
        private TextView textView2;

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
            countryButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
            countryButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            countryButton.setMaxLines(1);
            countryButton.setSingleLine(true);
            countryButton.setEllipsize(TextUtils.TruncateAt.END);
            countryButton.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_HORIZONTAL);
            countryButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 7));
            addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 0, 0, 14));
            countryButton.setOnClickListener(view -> {
                CountrySelectActivity fragment = new CountrySelectActivity(true);
                fragment.setCountrySelectActivityDelegate((country) -> {
                    selectCountry(country.name);
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(phoneField), 300);
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                });
                presentFragment(fragment);
            });

            view = new View(context);
            view.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayLine));
            addView(view, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 4, -17.5f, 4, 0));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);
            addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

            textView = new TextView(context);
            textView.setText("+");
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            codeField = new EditTextBoldCursor(context);
            codeField.setInputType(InputType.TYPE_CLASS_PHONE);
            codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            codeField.setCursorSize(AndroidUtilities.dp(20));
            codeField.setCursorWidth(1.5f);
            codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
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
                                    textToSet = text.substring(a) + phoneField.getText().toString();
                                    codeField.setText(text = sub);
                                    break;
                                }
                            }
                            if (!ok) {
                                ignoreOnTextChange = true;
                                textToSet = text.substring(1) + phoneField.getText().toString();
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
            codeField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                    return true;
                }
                return false;
            });

            phoneField = new HintEditText(context);
            phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
            phoneField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            phoneField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
            phoneField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
            phoneField.setPadding(0, 0, 0, 0);
            phoneField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            phoneField.setCursorSize(AndroidUtilities.dp(20));
            phoneField.setCursorWidth(1.5f);
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
                        str = str.substring(0, actionPosition) + str.substring(actionPosition + 1);
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
                        phoneField.setSelection(Math.min(start, phoneField.length()));
                    }
                    phoneField.onTextChange();
                    ignoreOnPhoneChange = false;
                }
            });
            phoneField.setOnEditorActionListener((textView, i, keyEvent) -> {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    onNextPressed(null);
                    return true;
                }
                return false;
            });
            phoneField.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && phoneField.length() == 0) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                    codeField.dispatchKeyEvent(event);
                    return true;
                }
                return false;
            });

            textView2 = new TextView(context);
            textView2.setText(LocaleController.getString("ChangePhoneHelp", R.string.ChangePhoneHelp));
            textView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView2.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView2.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 28, 0, 10));

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
                FileLog.e(e);
            }

            Collections.sort(countriesArray, String::compareTo);

            String country = null;

            try {
                TelephonyManager telephonyManager = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {
                    country = telephonyManager.getSimCountryIso().toUpperCase();
                }
            } catch (Exception e) {
                FileLog.e(e);
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
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onNextPressed(String code) {
            if (getParentActivity() == null || nextPressed) {
                return;
            }
            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
            boolean simcardAvailable = tm.getSimState() != TelephonyManager.SIM_STATE_ABSENT && tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
            boolean allowCall = true;
            if (Build.VERSION.SDK_INT >= 23 && simcardAvailable) {
                allowCall = getParentActivity().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
                if (checkPermissions) {
                    permissionsItems.clear();
                    if (!allowCall) {
                        permissionsItems.add(Manifest.permission.READ_PHONE_STATE);
                    }
                    if (!permissionsItems.isEmpty()) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        if (preferences.getBoolean("firstlogin", true) || getParentActivity().shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                            preferences.edit().putBoolean("firstlogin", false).commit();
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                            builder.setMessage(LocaleController.getString("AllowReadCall", R.string.AllowReadCall));
                            permissionsDialog = showDialog(builder.create());
                        } else {
                            getParentActivity().requestPermissions(permissionsItems.toArray(new String[0]), 6);
                        }
                        return;
                    }
                }
            }

            if (countryState == 1) {
                AlertsCreator.showSimpleAlert(ChangePhoneActivity.this, LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                return;
            }
            if (codeField.length() == 0) {
                AlertsCreator.showSimpleAlert(ChangePhoneActivity.this, LocaleController.getString("InvalidPhoneNumber", R.string.InvalidPhoneNumber));
                return;
            }
            final TLRPC.TL_account_sendChangePhoneCode req = new TLRPC.TL_account_sendChangePhoneCode();
            String phone = PhoneFormat.stripExceptNumbers("" + codeField.getText() + phoneField.getText());
            req.phone_number = phone;
            req.settings = new TLRPC.TL_codeSettings();
            req.settings.allow_flashcall = simcardAvailable && allowCall;
            req.settings.allow_app_hash = ApplicationLoader.hasPlayServices;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            if (req.settings.allow_app_hash) {
                preferences.edit().putString("sms_hash", BuildVars.SMS_HASH).commit();
            } else {
                preferences.edit().remove("sms_hash").commit();
            }
            if (req.settings.allow_flashcall) {
                try {
                    @SuppressLint("HardwareIds") String number = tm.getLine1Number();
                    if (!TextUtils.isEmpty(number)) {
                        req.settings.current_number = PhoneNumberUtils.compare(phone, number);
                        if (!req.settings.current_number) {
                            req.settings.allow_flashcall = false;
                        }
                    } else {
                        req.settings.current_number = false;
                    }
                } catch (Exception e) {
                    req.settings.allow_flashcall = false;
                    FileLog.e(e);
                }
            }

            final Bundle params = new Bundle();
            params.putString("phone", "+" + codeField.getText() + " " + phoneField.getText());
            try {
                params.putString("ephone", "+" + PhoneFormat.stripExceptNumbers(codeField.getText().toString()) + " " + PhoneFormat.stripExceptNumbers(phoneField.getText().toString()));
            } catch (Exception e) {
                FileLog.e(e);
                params.putString("ephone", "+" + phone);
            }
            params.putString("phoneFormated", phone);
            nextPressed = true;
            needShowProgress();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                } else {
                    AlertsCreator.processError(currentAccount, error, ChangePhoneActivity.this, req, params.getString("phone"));
                }
                needHideProgress();
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
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

        private String phone;
        private String phoneHash;
        private String requestPhone;
        private String emailPhone;
        private LinearLayout codeFieldContainer;
        private EditTextBoldCursor[] codeField;
        private TextView confirmTextView;
        private TextView titleTextView;
        private ImageView blackImageView;
        private RLottieImageView blueImageView;
        private TextView timeText;
        private TextView problemText;
        private Bundle currentParams;
        private ProgressView progressView;

        RLottieDrawable hintDrawable;

        private Timer timeTimer;
        private Timer codeTimer;
        private final Object timerSync = new Object();
        private int time = 60000;
        private int codeTime = 15000;
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
            confirmTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            confirmTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            confirmTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            titleTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            titleTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

            if (currentType == 3) {
                confirmTextView.setGravity(Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.phone_activate);
                if (LocaleController.isRTL) {
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.LEFT | Gravity.CENTER_VERTICAL, 2, 2, 0, 0));
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 64 + 18, 0, 0, 0));
                } else {
                    frameLayout.addView(confirmTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 64 + 18, 0));
                    frameLayout.addView(imageView, LayoutHelper.createFrame(64, 76, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 2, 0, 2));
                }
            } else {
                confirmTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

                FrameLayout frameLayout = new FrameLayout(context);
                addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

                if (currentType == 1) {
                    blackImageView = new ImageView(context);
                    blackImageView.setImageResource(R.drawable.sms_devices);
                    blackImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(blackImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    blueImageView = new RLottieImageView(context);
                    blueImageView.setImageResource(R.drawable.sms_bubble);
                    blueImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionBackground), PorterDuff.Mode.MULTIPLY));
                    frameLayout.addView(blueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    titleTextView.setText(LocaleController.getString("SentAppCodeTitle", R.string.SentAppCodeTitle));
                } else {
                    blueImageView = new RLottieImageView(context);
                    hintDrawable = new RLottieDrawable(R.raw.sms_incoming_info, "" + R.raw.sms_incoming_info, AndroidUtilities.dp(64), AndroidUtilities.dp(64), true, null);
                    hintDrawable.setLayerColor("Bubble.**", Theme.getColor(Theme.key_chats_actionBackground));
                    hintDrawable.setLayerColor("Phone.**", Theme.getColor(Theme.key_chats_actionBackground));
                    blueImageView.setAnimation(hintDrawable);

                    frameLayout.addView(blueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

                    titleTextView.setText(LocaleController.getString("SentSmsCodeTitle", R.string.SentSmsCodeTitle));
                }
                addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));
                addView(confirmTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 17, 0, 0));
            }

            codeFieldContainer = new LinearLayout(context);
            codeFieldContainer.setOrientation(HORIZONTAL);
            addView(codeFieldContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_HORIZONTAL));
            if (currentType == 3) {
                codeFieldContainer.setVisibility(GONE);
            }

            timeText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            timeText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
            timeText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            if (currentType == 3) {
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

                progressView = new ProgressView(context);
                timeText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(progressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3, 0, 12, 0, 0));
            } else {
                timeText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
                timeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                timeText.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                addView(timeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            }

            problemText = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.AT_MOST));
                }
            };
            problemText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            problemText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            problemText.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(10));
            problemText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            problemText.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            if (currentType == 1) {
                problemText.setText(LocaleController.getString("DidNotGetTheCodeSms", R.string.DidNotGetTheCodeSms));
            } else {
                problemText.setText(LocaleController.getString("DidNotGetTheCode", R.string.DidNotGetTheCode));
            }
            addView(problemText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            problemText.setOnClickListener(v -> {
                if (nextPressed) {
                    return;
                }
                boolean email = nextType == 4 && currentType == 2 || nextType == 0;
                if (!email) {
                    resendCode();
                } else {
                    try {
                        PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                        String version = String.format(Locale.US, "%s (%d)", pInfo.versionName, pInfo.versionCode);

                        Intent mailer = new Intent(Intent.ACTION_SENDTO);
                        mailer.setData(Uri.parse("mailto:"));
                        mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{"sms@telegram.org"});
                        mailer.putExtra(Intent.EXTRA_SUBJECT, "Android registration/login issue " + version + " " + emailPhone);
                        mailer.putExtra(Intent.EXTRA_TEXT, "Phone: " + requestPhone + "\nApp version: " + version + "\nOS version: SDK " + Build.VERSION.SDK_INT + "\nDevice Name: " + Build.MANUFACTURER + Build.MODEL + "\nLocale: " + Locale.getDefault() + "\nError: " + lastError);
                        getContext().startActivity(Intent.createChooser(mailer, "Send email..."));
                    } catch (Exception e) {
                        AlertsCreator.showSimpleAlert(ChangePhoneActivity.this, LocaleController.getString("NoMailInstalled", R.string.NoMailInstalled));
                    }
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (currentType != 3 && blueImageView != null) {
                int innerHeight = blueImageView.getMeasuredHeight() + titleTextView.getMeasuredHeight() + confirmTextView.getMeasuredHeight() + AndroidUtilities.dp(18 + 17);
                int requiredHeight = AndroidUtilities.dp(80);
                int maxHeight = AndroidUtilities.dp(291);
                if (scrollHeight - innerHeight < requiredHeight) {
                    setMeasuredDimension(getMeasuredWidth(), innerHeight + requiredHeight);
                } else {
                    setMeasuredDimension(getMeasuredWidth(), Math.min(scrollHeight, maxHeight));
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (currentType != 3 && blueImageView != null) {
                int bottom = confirmTextView.getBottom();
                int height = getMeasuredHeight() - bottom;

                int h;
                if (problemText.getVisibility() == VISIBLE) {
                    h = problemText.getMeasuredHeight();
                    t = bottom + height - h;
                    problemText.layout(problemText.getLeft(), t, problemText.getRight(), t + h);
                } else if (timeText.getVisibility() == VISIBLE) {
                    h = timeText.getMeasuredHeight();
                    t = bottom + height - h;
                    timeText.layout(timeText.getLeft(), t, timeText.getRight(), t + h);
                } else {
                    t = bottom + height;
                }

                height = t - bottom;
                h = codeFieldContainer.getMeasuredHeight();
                t = (height - h) / 2 + bottom;
                codeFieldContainer.layout(codeFieldContainer.getLeft(), t, codeFieldContainer.getRight(), t + h);
            }
        }

        private void resendCode() {
            final Bundle params = new Bundle();
            params.putString("phone", phone);
            params.putString("ephone", emailPhone);
            params.putString("phoneFormated", requestPhone);

            nextPressed = true;
            needShowProgress();

            final TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                nextPressed = false;
                if (error == null) {
                    fillNextCodeParams(params, (TLRPC.TL_auth_sentCode) response);
                } else {
                    AlertDialog dialog = (AlertDialog) AlertsCreator.processError(currentAccount, error, ChangePhoneActivity.this, req);
                    if (dialog != null && error.text.contains("PHONE_CODE_EXPIRED")) {
                        dialog.setPositiveButtonListener((dialog1, which) -> {
                            onBackPressed(true);
                            finishFragment();
                        });
                    }
                }
                needHideProgress();
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public String getHeaderName() {
            if (currentType == 1) {
                return phone;
            } else {
                return LocaleController.getString("YourCode", R.string.YourCode);
            }
        }

        @Override
        public boolean needBackButton() {
            return true;
        }

        @Override
        public void onCancelPressed() {
            nextPressed = false;
        }

        @Override
        public void setParams(Bundle params, boolean restore) {
            if (params == null) {
                return;
            }
            waitingForEvent = true;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(true);
                NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didReceiveCall);
            }

            currentParams = params;
            phone = params.getString("phone");
            emailPhone = params.getString("ephone");
            requestPhone = params.getString("phoneFormated");
            phoneHash = params.getString("phoneHash");
            timeout = time = params.getInt("timeout");
            nextType = params.getInt("nextType");
            pattern = params.getString("pattern");
            length = params.getInt("length");
            if (length == 0) {
                length = 5;
            }

            if (codeField == null || codeField.length != length) {
                codeField = new EditTextBoldCursor[length];
                for (int a = 0; a < length; a++) {
                    final int num = a;
                    codeField[a] = new EditTextBoldCursor(getContext());
                    codeField[a].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    codeField[a].setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    codeField[a].setCursorSize(AndroidUtilities.dp(20));
                    codeField[a].setCursorWidth(1.5f);

                    Drawable pressedDrawable = getResources().getDrawable(R.drawable.search_dark_activated).mutate();
                    pressedDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), PorterDuff.Mode.MULTIPLY));

                    codeField[a].setBackgroundDrawable(pressedDrawable);
                    codeField[a].setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    codeField[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                    codeField[a].setMaxLines(1);
                    codeField[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    codeField[a].setPadding(0, 0, 0, 0);
                    codeField[a].setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                    if (currentType == 3) {
                        codeField[a].setEnabled(false);
                        codeField[a].setInputType(InputType.TYPE_NULL);
                        codeField[a].setVisibility(GONE);
                    } else {
                        codeField[a].setInputType(InputType.TYPE_CLASS_PHONE);
                    }
                    codeFieldContainer.addView(codeField[a], LayoutHelper.createLinear(34, 36, Gravity.CENTER_HORIZONTAL, 0, 0, a != length - 1 ? 7 : 0, 0));
                    codeField[a].addTextChangedListener(new TextWatcher() {

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
                            int len = s.length();
                            if (len >= 1) {
                                if (len > 1) {
                                    String text = s.toString();
                                    ignoreOnTextChange = true;
                                    for (int a = 0; a < Math.min(length - num, len); a++) {
                                        if (a == 0) {
                                            s.replace(0, len, text.substring(a, a + 1));
                                        } else {
                                            codeField[num + a].setText(text.substring(a, a + 1));
                                        }
                                    }
                                    ignoreOnTextChange = false;
                                }

                                if (num != length - 1) {
                                    codeField[num + 1].setSelection(codeField[num + 1].length());
                                    codeField[num + 1].requestFocus();
                                }
                                if ((num == length - 1 || num == length - 2 && len >= 2) && getCode().length() == length) {
                                    onNextPressed(null);
                                }
                            }
                        }
                    });
                    codeField[a].setOnKeyListener((v, keyCode, event) -> {
                        if (keyCode == KeyEvent.KEYCODE_DEL && codeField[num].length() == 0 && num > 0) {
                            codeField[num - 1].setSelection(codeField[num - 1].length());
                            codeField[num - 1].requestFocus();
                            codeField[num - 1].dispatchKeyEvent(event);
                            return true;
                        }
                        return false;
                    });
                    codeField[a].setOnEditorActionListener((textView, i, keyEvent) -> {
                        if (i == EditorInfo.IME_ACTION_NEXT) {
                            onNextPressed(null);
                            return true;
                        }
                        return false;
                    });
                }
            } else {
                for (int a = 0; a < codeField.length; a++) {
                    codeField[a].setText("");
                }
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
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentSmsCode", R.string.SentSmsCode, LocaleController.addNbsp(number)));
            } else if (currentType == 3) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallCode", R.string.SentCallCode, LocaleController.addNbsp(number)));
            } else if (currentType == 4) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("SentCallOnly", R.string.SentCallOnly, LocaleController.addNbsp(number)));
            }
            confirmTextView.setText(str);

            if (currentType != 3) {
                AndroidUtilities.showKeyboard(codeField[0]);
                codeField[0].requestFocus();
            } else {
                AndroidUtilities.hideKeyboard(codeField[0]);
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
                timeText.setText(LocaleController.formatString("CallText", R.string.CallText, 2, 0));
                problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
                createTimer();
            } else if (currentType == 4 && nextType == 2) {
                timeText.setText(LocaleController.formatString("SmsText", R.string.SmsText, 2, 0));
                problemText.setVisibility(time < 1000 ? VISIBLE : GONE);
                timeText.setVisibility(time < 1000 ? GONE : VISIBLE);
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
                    AndroidUtilities.runOnUIThread(() -> {
                        double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCodeTime;
                        lastCodeTime = currentTime;
                        codeTime -= diff;
                        if (codeTime <= 1000) {
                            problemText.setVisibility(VISIBLE);
                            timeText.setVisibility(GONE);
                            destroyCodeTimer();
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
                FileLog.e(e);
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
                    AndroidUtilities.runOnUIThread(() -> {
                        final double currentTime = System.currentTimeMillis();
                        double diff = currentTime - lastCurrentTime;
                        time -= diff;
                        lastCurrentTime = currentTime;
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
                                NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                                waitingForEvent = false;
                                destroyCodeTimer();
                                resendCode();
                            } else if (currentType == 2 || currentType == 4) {
                                if (nextType == 4 || nextType == 2) {
                                    if (nextType == 4) {
                                        timeText.setText(LocaleController.getString("Calling", R.string.Calling));
                                    } else {
                                        timeText.setText(LocaleController.getString("SendingSms", R.string.SendingSms));
                                    }
                                    createCodeTimer();
                                    TLRPC.TL_auth_resendCode req = new TLRPC.TL_auth_resendCode();
                                    req.phone_number = requestPhone;
                                    req.phone_code_hash = phoneHash;
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                        if (error != null && error.text != null) {
                                            AndroidUtilities.runOnUIThread(() -> lastError = error.text);
                                        }
                                    }, ConnectionsManager.RequestFlagFailOnServerErrors);
                                } else if (nextType == 3) {
                                    AndroidUtilities.setWaitingForSms(false);
                                    NotificationCenter.getGlobalInstance().removeObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                                    waitingForEvent = false;
                                    destroyCodeTimer();
                                    resendCode();
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
                FileLog.e(e);
            }
        }

        private String getCode() {
            if (codeField == null) {
                return "";
            }
            StringBuilder codeBuilder = new StringBuilder();
            for (int a = 0; a < codeField.length; a++) {
                codeBuilder.append(PhoneFormat.stripExceptNumbers(codeField[a].getText().toString()));
            }
            return codeBuilder.toString();
        }

        @Override
        public void onNextPressed(String code) {
            if (nextPressed) {
                return;
            }
            code = getCode();
            if (TextUtils.isEmpty(code)) {
                AndroidUtilities.shakeView(codeFieldContainer, 2, 0);
                return;
            }
            nextPressed = true;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            final TLRPC.TL_account_changePhone req = new TLRPC.TL_account_changePhone();
            req.phone_number = requestPhone;
            req.phone_code = code;
            req.phone_code_hash = phoneHash;
            destroyTimer();
            needShowProgress();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                needHideProgress();
                nextPressed = false;
                if (error == null) {
                    TLRPC.User user = (TLRPC.User) response;
                    destroyTimer();
                    destroyCodeTimer();
                    UserConfig.getInstance(currentAccount).setCurrentUser(user);
                    UserConfig.getInstance(currentAccount).saveConfig(true);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, true, true);
                    MessagesController.getInstance(currentAccount).putUser(user, false);
                    finishFragment();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                    getMessagesController().removeSuggestion(0, "VALIDATE_PHONE_NUMBER");
                } else {
                    lastError = error.text;
                    if (currentType == 3 && (nextType == 4 || nextType == 2) || currentType == 2 && (nextType == 4 || nextType == 3) || currentType == 4 && nextType == 2) {
                        createTimer();
                    }
                    if (currentType == 2) {
                        AndroidUtilities.setWaitingForSms(true);
                        NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveSmsCode);
                    } else if (currentType == 3) {
                        AndroidUtilities.setWaitingForCall(true);
                        NotificationCenter.getGlobalInstance().addObserver(LoginActivitySmsView.this, NotificationCenter.didReceiveCall);
                    }
                    waitingForEvent = true;
                    if (currentType != 3) {
                        AlertsCreator.processError(currentAccount, error, ChangePhoneActivity.this, req);
                    }
                    if (error.text.contains("PHONE_CODE_EMPTY") || error.text.contains("PHONE_CODE_INVALID")) {
                        for (int a = 0; a < codeField.length; a++) {
                            codeField[a].setText("");
                        }
                        codeField[0].requestFocus();
                    } else if (error.text.contains("PHONE_CODE_EXPIRED")) {
                        onBackPressed(true);
                        setPage(0, true, null, true);
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        }

        @Override
        public boolean onBackPressed(boolean force) {
            if (!force) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("StopVerification", R.string.StopVerification));
                builder.setPositiveButton(LocaleController.getString("Continue", R.string.Continue), null);
                builder.setNegativeButton(LocaleController.getString("Stop", R.string.Stop), (dialogInterface, i) -> {
                    onBackPressed(true);
                    setPage(0, true, null, true);
                });
                showDialog(builder.create());
                return false;
            }
            TLRPC.TL_auth_cancelCode req = new TLRPC.TL_auth_cancelCode();
            req.phone_number = requestPhone;
            req.phone_code_hash = phoneHash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagWithoutLogin);

            destroyTimer();
            destroyCodeTimer();
            currentParams = null;
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            return true;
        }

        @Override
        public void onDestroyActivity() {
            super.onDestroyActivity();
            if (currentType == 2) {
                AndroidUtilities.setWaitingForSms(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveSmsCode);
            } else if (currentType == 3) {
                AndroidUtilities.setWaitingForCall(false);
                NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didReceiveCall);
            }
            waitingForEvent = false;
            destroyTimer();
            destroyCodeTimer();
        }

        @Override
        public void onShow() {
            super.onShow();
            if (currentType == 3) {
                return;
            }
            if (hintDrawable != null) {
                hintDrawable.setCurrentFrame(0);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (codeField != null) {
                    for (int a = codeField.length - 1; a >= 0; a--) {
                        if (a == 0 || codeField[a].length() != 0) {
                            codeField[a].requestFocus();
                            codeField[a].setSelection(codeField[a].length());
                            AndroidUtilities.showKeyboard(codeField[a]);
                            break;
                        }
                    }
                }
                if (hintDrawable != null) {
                    hintDrawable.start();
                }
            }, 100);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (!waitingForEvent || codeField == null) {
                return;
            }
            if (id == NotificationCenter.didReceiveSmsCode) {
                codeField[0].setText("" + args[0]);
                onNextPressed(null);
            } else if (id == NotificationCenter.didReceiveCall) {
                String num = "" + args[0];
                if (!AndroidUtilities.checkPhonePattern(pattern, num)) {
                    return;
                }
                ignoreOnTextChange = true;
                codeField[0].setText(num);
                ignoreOnTextChange = false;
                onNextPressed(null);
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        PhoneView phoneView = (PhoneView) views[0];
        LoginActivitySmsView smsView1 = (LoginActivitySmsView) views[1];
        LoginActivitySmsView smsView2 = (LoginActivitySmsView) views[2];
        LoginActivitySmsView smsView3 = (LoginActivitySmsView) views[3];
        LoginActivitySmsView smsView4 = (LoginActivitySmsView) views[4];

        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = () -> {
            for (int i = 0; i < views.length; i++) {
                if (views[i] instanceof LoginActivity.LoginActivitySmsView) {
                    LoginActivity.LoginActivitySmsView smsView = (LoginActivity.LoginActivitySmsView) views[i];
                    if (smsView.hintDrawable != null) {
                        smsView.hintDrawable.setLayerColor("Bubble.**", Theme.getColor(Theme.key_chats_actionBackground));
                        smsView.hintDrawable.setLayerColor("Phone.**", Theme.getColor(Theme.key_chats_actionBackground));
                    }
                }
            }
        };

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        arrayList.add(new ThemeDescription(phoneView.countryButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.countryButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_listSelector));
        arrayList.add(new ThemeDescription(phoneView.view, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhiteGrayLine));
        arrayList.add(new ThemeDescription(phoneView.textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.codeField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(phoneView.codeField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        arrayList.add(new ThemeDescription(phoneView.phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        arrayList.add(new ThemeDescription(phoneView.textView2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));

        arrayList.add(new ThemeDescription(smsView1.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView1.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView1.codeField != null) {
            for (int a = 0; a < smsView1.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView1.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView1.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView1.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView1.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView1.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView1.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView1.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView1.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));

        arrayList.add(new ThemeDescription(smsView2.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView2.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView2.codeField != null) {
            for (int a = 0; a < smsView2.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView2.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView2.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView2.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView2.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView2.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView2.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView2.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView2.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));

        arrayList.add(new ThemeDescription(smsView3.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView3.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView3.codeField != null) {
            for (int a = 0; a < smsView3.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView3.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView3.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView3.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView3.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView3.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView3.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView3.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView3.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));

        arrayList.add(new ThemeDescription(smsView4.confirmTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView4.titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        if (smsView4.codeField != null) {
            for (int a = 0; a < smsView4.codeField.length; a++) {
                arrayList.add(new ThemeDescription(smsView4.codeField[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
                arrayList.add(new ThemeDescription(smsView4.codeField[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
            }
        }
        arrayList.add(new ThemeDescription(smsView4.timeText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));
        arrayList.add(new ThemeDescription(smsView4.problemText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        arrayList.add(new ThemeDescription(smsView4.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressInner));
        arrayList.add(new ThemeDescription(smsView4.progressView, 0, new Class[]{ProgressView.class}, new String[]{"paint"}, null, null, null, Theme.key_login_progressOuter));
        arrayList.add(new ThemeDescription(smsView4.blackImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(smsView4.blueImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionBackground));
        arrayList.add(new ThemeDescription(smsView4.blueImageView, 0, null, null, null, descriptionDelegate, Theme.key_chats_actionBackground));

        return arrayList;
    }
}
