/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static android.widget.LinearLayout.HORIZONTAL;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedPhoneNumberEditText;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineEditText;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RadialProgressView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class NewContactBottomSheet extends BottomSheet implements AdapterView.OnItemSelectedListener {

    private LinearLayout contentLayout;
    private ContextProgressView editDoneItemProgress;
    private OutlineEditText firstNameField;
    private OutlineEditText lastNameField;
    private OutlineTextContainerView phoneOutlineView;

    private ArrayList<CountrySelectActivity.Country> countriesArray = new ArrayList<>();
    private HashMap<String, List<CountrySelectActivity.Country>> codesMap = new HashMap<>();
    private HashMap<String, List<String>> phoneFormatMap = new HashMap<>();

    private boolean ignoreOnTextChange;
    private boolean ignoreOnPhoneChange;
    private boolean ignoreOnPhoneChangePaste;
    private boolean ignoreSelection;
    private boolean donePressed;
    private String initialPhoneNumber;
    private boolean initialPhoneNumberWithCountryCode;
    private String initialFirstName;
    private String initialLastName;

    BaseFragment parentFragment;
    int classGuid;
    private AnimatedPhoneNumberEditText codeField;
    private View codeDividerView;
    private AnimatedPhoneNumberEditText phoneField;
    private String countryCodeForHint;
    private int wasCountryHintIndex;
    private TextView countryFlag;
    private TextView doneButton;
    private RadialProgressView progressView;
    private FrameLayout doneButtonContainer;
    private TextView plusTextView;

    public NewContactBottomSheet(BaseFragment parentFragment, Context context) {
        super(context, true);
        waitingKeyboard = true;
        smoothKeyboardAnimationEnabled = true;
        classGuid = ConnectionsManager.generateClassGuid();
        this.parentFragment = parentFragment;
        setCustomView(createView(getContext()));
        setTitle(LocaleController.getString("NewContactTitle", R.string.NewContactTitle), true);
    }

    public View createView(Context context) {
        editDoneItemProgress = new ContextProgressView(context, 1);
        editDoneItemProgress.setVisibility(View.INVISIBLE);

        ScrollView fragmentView = new ScrollView(context);

        contentLayout = new LinearLayout(context);
        contentLayout.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        contentLayout.setOnTouchListener((v, event) -> true);

        FrameLayout frameLayout = new FrameLayout(context);
        contentLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));


        firstNameField = new OutlineEditText(context);
        firstNameField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
        if (initialFirstName != null) {
            firstNameField.getEditText().setText(initialFirstName);
            initialFirstName = null;
        }
        frameLayout.addView(firstNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        firstNameField.getEditText().setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                lastNameField.requestFocus();
                lastNameField.getEditText().setSelection(lastNameField.getEditText().length());
                return true;
            }
            return false;
        });

        lastNameField = new OutlineEditText(context);
        lastNameField.setBackground(null);
        lastNameField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        lastNameField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
        if (initialLastName != null) {
            lastNameField.getEditText().setText(initialLastName);
            initialLastName = null;
        }
        frameLayout.addView(lastNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 68, 0, 0));
        lastNameField.getEditText().setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                codeField.requestFocus();
                codeField.setSelection(codeField.length());
                return true;
            }
            return false;
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(HORIZONTAL);

        phoneOutlineView = new OutlineTextContainerView(context);
        phoneOutlineView.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 8, 16, 8));
        phoneOutlineView.setText(LocaleController.getString(R.string.PhoneNumber));
        contentLayout.addView(phoneOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 0, 12, 0, 8));


        FrameLayout countryContainer = new FrameLayout(context);
        countryFlag = new TextView(context) {

            final NotificationCenter.NotificationCenterDelegate delegate = (id, account, args) -> invalidate();

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                NotificationCenter.getGlobalInstance().addObserver(delegate, NotificationCenter.emojiLoaded);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                NotificationCenter.getGlobalInstance().removeObserver(delegate, NotificationCenter.emojiLoaded);
            }
        };

        countryFlag.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        countryFlag.setFocusable(false);
        countryFlag.setGravity(Gravity.CENTER);
        countryContainer.setOnClickListener((v) -> {
            CountrySelectActivity countrySelectActivity = new CountrySelectActivity(true);
            countrySelectActivity.setCountrySelectActivityDelegate(new CountrySelectActivity.CountrySelectActivityDelegate() {
                @Override
                public void didSelectCountry(CountrySelectActivity.Country country) {
                    selectCountry(country);
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(phoneField), 300);
                    phoneField.requestFocus();
                    phoneField.setSelection(phoneField.length());
                }
            });
            parentFragment.showAsSheet(countrySelectActivity);
        });
        countryContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), 0, Theme.getColor(Theme.key_listSelector)));
        countryContainer.addView(countryFlag, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        linearLayout.addView(countryContainer, LayoutHelper.createLinear(42, LayoutHelper.MATCH_PARENT));

        plusTextView = new TextView(context);
        plusTextView.setText("+");
        plusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        plusTextView.setFocusable(false);
        linearLayout.addView(plusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        codeField = new AnimatedPhoneNumberEditText(context) {
            @Override
            protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
                phoneOutlineView.animateSelection(focused || phoneField.isFocused() ? 1f : 0f);
            }
        };
        codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setInputType(InputType.TYPE_CLASS_PHONE);
        codeField.setCursorSize(AndroidUtilities.dp(20));
        codeField.setCursorWidth(1.5f);
        codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
        codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        codeField.setMaxLines(1);
        codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        codeField.setBackground(null);
        codeField.setContentDescription(LocaleController.getString(R.string.LoginAccessibilityCountryCode));
        linearLayout.addView(codeField, LayoutHelper.createLinear(55, 36, -9, 0, 0, 0));
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
                    setCountryButtonText(null);
                    phoneField.setHintText(null);
                } else {
                    CountrySelectActivity.Country country;
                    boolean ok = false;
                    String textToSet = null;
                    if (text.length() > 4) {
                        for (int a = 4; a >= 1; a--) {
                            String sub = text.substring(0, a);

                            List<CountrySelectActivity.Country> list = codesMap.get(sub);
                            if (list == null) {
                                country = null;
                            } else if (list.size() > 1) {
                                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                                String lastMatched = preferences.getString("phone_code_last_matched_" + sub, null);

                                country = list.get(list.size() - 1);
                                if (lastMatched != null) {
                                    for (CountrySelectActivity.Country c : countriesArray) {
                                        if (Objects.equals(c.shortname, lastMatched)) {
                                            country = c;
                                            break;
                                        }
                                    }
                                }
                            } else {
                                country = list.get(0);
                            }

                            if (country != null) {
                                ok = true;
                                textToSet = text.substring(a) + phoneField.getText().toString();
                                codeField.setText(text = sub);
                                break;
                            }
                        }
                        if (!ok) {
                            textToSet = text.substring(1) + phoneField.getText().toString();
                            codeField.setText(text = text.substring(0, 1));
                        }
                    }

                    CountrySelectActivity.Country lastMatchedCountry = null;
                    int matchedCountries = 0;
                    for (CountrySelectActivity.Country c : countriesArray) {
                        if (c.code.startsWith(text)) {
                            matchedCountries++;
                            if (c.code.equals(text)) {
                                lastMatchedCountry = c;
                            }
                        }
                    }
                    if (matchedCountries == 1 && lastMatchedCountry != null && textToSet == null) {
                        textToSet = text.substring(lastMatchedCountry.code.length()) + phoneField.getText().toString();
                        codeField.setText(text = lastMatchedCountry.code);
                    }

                    List<CountrySelectActivity.Country> list = codesMap.get(text);
                    if (list == null) {
                        country = null;
                    } else if (list.size() > 1) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        String lastMatched = preferences.getString("phone_code_last_matched_" + text, null);

                        country = list.get(list.size() - 1);
                        if (lastMatched != null) {
                            for (CountrySelectActivity.Country c : countriesArray) {
                                if (Objects.equals(c.shortname, lastMatched)) {
                                    country = c;
                                    break;
                                }
                            }
                        }
                    } else {
                        country = list.get(0);
                    }

                    if (country != null) {
                        ignoreSelection = true;
                        setCountryHint(text, country);
                    } else {
                        setCountryButtonText(null);
                        phoneField.setHintText(null);
                    }
                    if (!ok) {
                        codeField.setSelection(codeField.getText().length());
                    }
                    if (textToSet != null && textToSet.length() != 0) {
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
        codeDividerView = new View(context);
        LinearLayout.LayoutParams params = LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 4, 8, 12, 8);
        params.width = Math.max(2, AndroidUtilities.dp(0.5f));
        linearLayout.addView(codeDividerView, params);

        phoneField = new AnimatedPhoneNumberEditText(context) {

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL && phoneField.length() == 0) {
                    codeField.requestFocus();
                    codeField.setSelection(codeField.length());
                    codeField.dispatchKeyEvent(event);
                }
                return super.onKeyDown(keyCode, event);
            }

            @Override
            protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
                phoneOutlineView.animateSelection(focused || codeField.isFocused() ? 1f : 0f);
            }
        };
        phoneField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        phoneField.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneField.setPadding(0, 0, 0, 0);
        phoneField.setCursorSize(AndroidUtilities.dp(20));
        phoneField.setCursorWidth(1.5f);
        phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        phoneField.setMaxLines(1);
        phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        phoneField.setBackground(null);
        phoneField.setContentDescription(LocaleController.getString(R.string.PhoneNumber));
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
                if (!LoginActivity.ENABLE_PASTED_TEXT_PROCESSING || ignoreOnPhoneChange || ignoreOnPhoneChangePaste) {
                    return;
                }

                String str = s.toString().substring(start, start + count).replaceAll("[^\\d]+", "");
                if (str.isEmpty()) {
                    return;
                }

                ignoreOnPhoneChangePaste = true;
                for (int i = Math.min(3, str.length()); i >= 0; i--) {
                    String code = str.substring(0, i);

                    List<CountrySelectActivity.Country> list = codesMap.get(code);
                    if (list != null && !list.isEmpty()) {
                        List<String> patterns = phoneFormatMap.get(code);

                        if (patterns == null || patterns.isEmpty()) {
                            continue;
                        }

                        for (String pattern : patterns) {
                            String pat = pattern.replace(" ", "");
                            if (pat.length() == str.length() - i) {
                                codeField.setText(code);
                                ignoreOnTextChange = true;
                                phoneField.setText(str.substring(i));
                                ignoreOnTextChange = false;

                                afterTextChanged(phoneField.getText());
                                phoneField.setSelection(phoneField.getText().length(), phoneField.getText().length());
                                break;
                            }
                        }
                    }
                }
                ignoreOnPhoneChangePaste = false;
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
                s.replace(0, s.length(), builder);
                if (start >= 0) {
                    phoneField.setSelection(Math.min(start, phoneField.length()));
                }
                phoneField.onTextChange();
                ignoreOnPhoneChange = false;
            }
        });
        phoneField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                doneButtonContainer.callOnClick();
                return true;
            }
            return false;
        });

        HashMap<String, String> languageMap = new HashMap<>();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                CountrySelectActivity.Country countryWithCode = new CountrySelectActivity.Country();
                countryWithCode.name = args[2];
                countryWithCode.code = args[0];
                countryWithCode.shortname = args[1];
                countriesArray.add(0, countryWithCode);

                List<CountrySelectActivity.Country> countryList = codesMap.get(args[0]);
                if (countryList == null) {
                    codesMap.put(args[0], countryList = new ArrayList<>());
                }
                countryList.add(countryWithCode);

                if (args.length > 3) {
                    phoneFormatMap.put(args[0], Collections.singletonList(args[3]));
                }
                languageMap.put(args[1], args[2]);
            }
            reader.close();
        } catch (Exception e) {
            FileLog.e(e);
        }

        Collections.sort(countriesArray, Comparator.comparing(o -> o.name));

        if (!TextUtils.isEmpty(initialPhoneNumber)) {
            TLRPC.User user = parentFragment.getUserConfig().getCurrentUser();
            if (initialPhoneNumber.startsWith("+")) {
                codeField.setText(initialPhoneNumber.substring(1));
            } else if (initialPhoneNumberWithCountryCode || user == null || TextUtils.isEmpty(user.phone)) {
                codeField.setText(initialPhoneNumber);
            } else {
                String phone = user.phone;
                for (int a = 4; a >= 1; a--) {
                    String sub = phone.substring(0, a);
                    List<CountrySelectActivity.Country> country = codesMap.get(sub);
                    if (country != null) {
                        codeField.setText(sub);
                        break;
                    }
                }
                phoneField.setText(initialPhoneNumber);
            }
            initialPhoneNumber = null;
        } else {
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
                    CountrySelectActivity.Country country1 = null;
                    for (int i = 0; i < countriesArray.size(); i++) {
                        if (Objects.equals(countriesArray.get(i).name, countryName)) {
                            country1 = countriesArray.get(i);
                            break;

                        }
                    }
                    if (country1 != null) {
                        codeField.setText(country1.code);
                    }
                }
            }
            if (codeField.length() == 0) {
                phoneField.setHintText(null);
            }
        }


        doneButtonContainer = new FrameLayout(getContext());
        doneButton = new TextView(context);
        doneButton.setEllipsize(TextUtils.TruncateAt.END);
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setLines(1);
        doneButton.setSingleLine(true);
        doneButton.setText(LocaleController.getString("CreateContact", R.string.CreateContact));
        doneButton.setTextColor(parentFragment.getThemedColor(Theme.key_featuredStickers_buttonText));
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        doneButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        progressView = new RadialProgressView(context);
        progressView.setSize(AndroidUtilities.dp(20));
        progressView.setProgressColor(parentFragment.getThemedColor(Theme.key_featuredStickers_buttonText));
        doneButtonContainer.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        doneButtonContainer.addView(progressView, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        contentLayout.addView(doneButtonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 0, 16, 0, 16));

        AndroidUtilities.updateViewVisibilityAnimated(doneButton, true, 1f, false);
        AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 1f, false);
        doneButtonContainer.setBackground(Theme.AdaptiveRipple.filledRect(parentFragment.getThemedColor(Theme.key_featuredStickers_addButton), 6));
        doneButtonContainer.setOnClickListener(v -> doOnDone());

        plusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeDividerView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField));
        return fragmentView;
    }

    private void doOnDone() {
        if (donePressed || parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        if (firstNameField.getEditText().length() == 0) {
            Vibrator v = (Vibrator) parentFragment.getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(firstNameField);
            return;
        }
        if (codeField.length() == 0) {
            Vibrator v = (Vibrator) parentFragment.getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(codeField);
            return;
        }
        if (phoneField.length() == 0) {
            Vibrator v = (Vibrator) parentFragment.getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(phoneField);
            return;
        }
        donePressed = true;
        showEditDoneProgress(true, true);
        final TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
        final TLRPC.TL_inputPhoneContact inputPhoneContact = new TLRPC.TL_inputPhoneContact();
        inputPhoneContact.first_name = firstNameField.getEditText().getText().toString();
        inputPhoneContact.last_name = lastNameField.getEditText().getText().toString();
        inputPhoneContact.phone = "+" + codeField.getText().toString() + phoneField.getText().toString();
        req.contacts.add(inputPhoneContact);
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
            AndroidUtilities.runOnUIThread(() -> {
                donePressed = false;
                if (res != null) {
                    if (!res.users.isEmpty()) {
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.openChatOrProfileWith(res.users.get(0), null, parentFragment, 1, true);
                        dismiss();
                    } else {
                        if (parentFragment.getParentActivity() == null) {
                            return;
                        }
                        showEditDoneProgress(false, true);
                        AlertsCreator.createContactInviteDialog(parentFragment, inputPhoneContact.first_name, inputPhoneContact.last_name, inputPhoneContact.phone);
                    }
                } else {
                    showEditDoneProgress(false, true);
                    AlertsCreator.processError(currentAccount, error, parentFragment, req);
                }
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public void show() {
        super.show();
        firstNameField.getEditText().requestFocus();
        firstNameField.getEditText().setSelection(firstNameField.getEditText().length());
        AndroidUtilities.runOnUIThread(() -> {
            AndroidUtilities.showKeyboard(firstNameField.getEditText());
        }, 50);
    }

    private void showEditDoneProgress(boolean show, boolean animated) {
        AndroidUtilities.updateViewVisibilityAnimated(doneButton, !show, 0.5f, animated);
        AndroidUtilities.updateViewVisibilityAnimated(progressView, show, 0.5f, animated);
    }

    public static String getPhoneNumber(Context context, TLRPC.User user, String number, boolean withCoutryCode) {
        HashMap<String, String> codesMap = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                codesMap.put(args[0], args[2]);
            }
            reader.close();
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (number.startsWith("+")) {
            return number;
        } else if (withCoutryCode || user == null || TextUtils.isEmpty(user.phone)) {
            return "+" + number;
        } else {
            String phone = user.phone;
            for (int a = 4; a >= 1; a--) {
                String sub = phone.substring(0, a);
                String country = codesMap.get(sub);
                if (country != null) {
                    return "+" + sub + number;
                }
            }
            return number;
        }
    }

    public void setInitialPhoneNumber(String value, boolean withCountryCode) {
        initialPhoneNumber = value;
        initialPhoneNumberWithCountryCode = withCountryCode;

        if (!TextUtils.isEmpty(initialPhoneNumber)) {
            TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (initialPhoneNumber.startsWith("+")) {
                codeField.setText(initialPhoneNumber.substring(1));
            } else if (initialPhoneNumberWithCountryCode || user == null || TextUtils.isEmpty(user.phone)) {
                codeField.setText(initialPhoneNumber);
            } else {
                String phone = user.phone;
                for (int a = 4; a >= 1; a--) {
                    String sub = phone.substring(0, a);
                    List<CountrySelectActivity.Country> country = codesMap.get(sub);
                    if (country != null && country.size() > 0) {
                        codeField.setText(country.get(0).code);
                        break;
                    }
                }
                phoneField.setText(initialPhoneNumber);
            }
            initialPhoneNumber = null;
        }
    }

    public void setInitialName(String firstName, String lastName) {
        if (firstNameField != null) {
            firstNameField.getEditText().setText(firstName);
        } else {
            initialFirstName = firstName;
        }
        if (lastNameField != null) {
            lastNameField.getEditText().setText(lastName);
        } else {
            initialLastName = lastName;
        }
    }

    private void setCountryHint(String code, CountrySelectActivity.Country country) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String flag = LocaleController.getLanguageFlag(country.shortname);
        if (flag != null) {
            sb.append(flag);
        }
        setCountryButtonText(Emoji.replaceEmoji(sb, countryFlag.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
        countryCodeForHint = code;
        wasCountryHintIndex = -1;
        invalidateCountryHint();
    }

    private void setCountryButtonText(CharSequence cs) {
        if (TextUtils.isEmpty(cs)) {
            countryFlag.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationY(AndroidUtilities.dp(30)).setDuration(150);
            plusTextView.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationX(-AndroidUtilities.dp(30)).setDuration(150);
            codeField.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationX(-AndroidUtilities.dp(30)).setDuration(150);
        } else {
            countryFlag.animate().setInterpolator(AndroidUtilities.overshootInterpolator).translationY(0).setDuration(350).start();
            plusTextView.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationX(0).setDuration(150);
            codeField.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationX(0).setDuration(150);
            countryFlag.setText(cs);
        }
    }


    private void invalidateCountryHint() {
        String code = countryCodeForHint;
        String str = phoneField.getText() != null ? phoneField.getText().toString().replace(" ", "") : "";

        if (phoneFormatMap.get(code) != null && !phoneFormatMap.get(code).isEmpty()) {
            int index = -1;
            List<String> patterns = phoneFormatMap.get(code);
            if (!str.isEmpty()) {
                for (int i = 0; i < patterns.size(); i++) {
                    String pattern = patterns.get(i);
                    if (str.startsWith(pattern.replace(" ", "").replace("X", "").replace("0", ""))) {
                        index = i;
                        break;
                    }
                }
            }
            if (index == -1) {
                for (int i = 0; i < patterns.size(); i++) {
                    String pattern = patterns.get(i);
                    if (pattern.startsWith("X") || pattern.startsWith("0")) {
                        index = i;
                        break;
                    }
                }
                if (index == -1) {
                    index = 0;
                }
            }

            if (wasCountryHintIndex != index) {
                String hint = phoneFormatMap.get(code).get(index);
                int ss = phoneField.getSelectionStart(), se = phoneField.getSelectionEnd();
                phoneField.setHintText(hint != null ? hint.replace('X', '0') : null);
                phoneField.setSelection(ss, se);
                wasCountryHintIndex = index;
            }
        } else if (wasCountryHintIndex != -1) {
            int ss = phoneField.getSelectionStart(), se = phoneField.getSelectionEnd();
            phoneField.setHintText(null);
            phoneField.setSelection(ss, se);
            wasCountryHintIndex = -1;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (ignoreSelection) {
            ignoreSelection = false;
            return;
        }
        ignoreOnTextChange = true;
        CountrySelectActivity.Country country = countriesArray.get(i);
        codeField.setText(country.code);
        ignoreOnTextChange = false;
    }

    public void selectCountry(CountrySelectActivity.Country country) {
        ignoreOnTextChange = true;
        String code = country.code;
        codeField.setText(code);
        setCountryHint(code, country);
        ignoreOnTextChange = false;
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(codeField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(codeField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(codeField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(phoneField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(phoneField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(editDoneItemProgress, 0, null, null, null, null, Theme.key_contextProgressInner2));
        themeDescriptions.add(new ThemeDescription(editDoneItemProgress, 0, null, null, null, null, Theme.key_contextProgressOuter2));

        return themeDescriptions;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        AndroidUtilities.runOnUIThread(() -> {
            AndroidUtilities.hideKeyboard(contentLayout);
        }, 50);

    }
}
