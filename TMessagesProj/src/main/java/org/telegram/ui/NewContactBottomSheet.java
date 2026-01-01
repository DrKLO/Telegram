/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static android.widget.LinearLayout.HORIZONTAL;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedPhoneNumberEditText;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.OutlineEditText;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class NewContactBottomSheet extends BottomSheet implements AdapterView.OnItemSelectedListener {

    private LinearLayout contentLayout;
    private ContextProgressView editDoneItemProgress;
    private OutlineEditText firstNameField;
    private OutlineEditText lastNameField;
    private FrameLayout qrButtonContainer;
    private View qrButtonSeparator;
    private ButtonWithCounterView qrButton;
    private OutlineEditText notesField;
    private OutlineTextContainerView phoneOutlineView;
    private TextView underPhoneTextView;

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
    private ImageView phoneStatusView;
    private CheckBox2 checkBox;
    private TextView checkTextView;
    private LinearLayout checkLayout;
    private String countryCodeForHint;
    private int wasCountryHintIndex;
    private TextView countryFlag;
    private TextView doneButton;
    private RadialProgressView progressView;
    private FrameLayout doneButtonContainer;
    private TextView plusTextView;

    private AccountInfo account;
    private LinkSpanDrawable.LinksTextView accountTextView;
    private OutlineTextContainerView accountOutlineView;

    public NewContactBottomSheet(BaseFragment parentFragment, Context context) {
        super(context, true);
        fixNavigationBar();
        waitingKeyboard = true;
        smoothKeyboardAnimationEnabled = true;
        classGuid = ConnectionsManager.generateClassGuid();
        this.parentFragment = parentFragment;
        setCustomView(createView(getContext()));
        setTitle(LocaleController.getString(R.string.NewContactTitle), true);
    }

    public View createView(Context context) {
        editDoneItemProgress = new ContextProgressView(context, 1);
        editDoneItemProgress.setVisibility(View.INVISIBLE);

        ScrollView fragmentView = new ScrollView(context);

        contentLayout = new LinearLayout(context);
        contentLayout.setPadding(dp(20), 0, dp(20), 0);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        contentLayout.setOnTouchListener((v, event) -> true);

        FrameLayout frameLayout = new FrameLayout(context);
        contentLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        firstNameField = new OutlineEditText(context);
        firstNameField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        firstNameField.setHint(LocaleController.getString(R.string.FirstName));
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
        lastNameField.setHint(LocaleController.getString(R.string.LastName));
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
        contentLayout.addView(phoneOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 0, 12, 0, 6));

        underPhoneTextView = new LinkSpanDrawable.LinksTextView(context);
        underPhoneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        underPhoneTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        underPhoneTextView.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
        contentLayout.addView(underPhoneTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 0));

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
        countryContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(6), 0, Theme.getColor(Theme.key_listSelector)));
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
        codeField.setCursorSize(dp(20));
        codeField.setCursorWidth(1.5f);
        codeField.setPadding(dp(10), 0, 0, 0);
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
                updatedTextPhone();
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
        params.width = Math.max(2, dp(0.5f));
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
        phoneField.setCursorSize(dp(20));
        phoneField.setCursorWidth(1.5f);
        phoneField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        phoneField.setMaxLines(1);
        phoneField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        phoneField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        phoneField.setBackground(null);
        phoneField.setContentDescription(LocaleController.getString(R.string.PhoneNumber));
        linearLayout.addView(phoneField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36));
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
                updatedTextPhone();
            }
        });
        phoneField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                doneButtonContainer.callOnClick();
                return true;
            }
            return false;
        });

        phoneStatusView = new ImageView(context);
        phoneStatusView.setScaleX(0.5f);
        phoneStatusView.setScaleY(0.5f);
        phoneStatusView.setAlpha(0.0f);
        phoneOutlineView.addView(phoneStatusView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 12, 0));

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(true);
        checkBox.setChecked(false, false);
        checkBox.setDrawBackgroundAsArc(10);

        checkTextView = new TextView(context);
        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        checkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        checkTextView.setText("Sync Contact to Phone");

        checkLayout = new LinearLayout(context);
        checkLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        checkLayout.addView(checkBox, LayoutHelper.createLinear(21, 21, Gravity.CENTER_VERTICAL, 0, 0, 9, 0));
        checkLayout.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        checkLayout.setOnClickListener(v -> {
            checkBox.setChecked(!checkBox.isChecked(), true);
            updateQrButtonVisible(true);
        });
        checkLayout.setTranslationY(dp(-21.33f));
        checkLayout.setPivotX(0);
        ScaleStateListAnimator.apply(checkLayout, .0125f, 1.2f);
        checkLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));
        contentLayout.addView(checkLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));

//        accountTextView = TextHelper.makeLinkTextView(context, 15, Theme.key_windowBackgroundWhiteBlackText, false, resourcesProvider);
//        accountTextView.setText("Phone");
//        accountOutlineView = new OutlineTextContainerView(context);
//        accountOutlineView.setText("Save To");
//        accountOutlineView.addView(accountTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 16, 16, 16, 16));
//        accountOutlineView.setForceUseCenter(true);
//        accountOutlineView.setFocusable(true);
//        accountOutlineView.setOnFocusChangeListener((v, hasFocus) -> accountOutlineView.animateSelection(hasFocus ? 1 : 0));
////        contentLayout.addView(accountOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 0, 8, 0, 8));
//        accountOutlineView.setOnClickListener(view -> {
//            final List<AccountInfo> infos = getAllAccounts(context);
//            final CharSequence[] items = new CharSequence[1 + infos.size()];
//            for (int i = 0; i < items.length; ++i) {
//                if (i == 0)
//                    items[i] = "Phone";
//                else {
//                    items[i] = infos.get(i - 1).name + ": " + infos.get(i - 1).type;
//                }
//            }
//            new AlertDialog.Builder(context, resourcesProvider)
//                .setItems(items, (di, w) -> {
//                    if (w == 0) {
//                        account = null;
//                        accountTextView.setText("Phone");
//                    } else {
//                        account = infos.get(w - 1);
//                        accountTextView.setText(account.name);
//                    }
//                })
//                .show();
//        });

        qrButtonContainer = new FrameLayout(context);
        qrButtonContainer.setTranslationY(dp(-21.33f / 2));
        contentLayout.addView(qrButtonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, -6));

        qrButtonSeparator = new View(context);
        qrButtonSeparator.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        qrButtonContainer.addView(qrButtonSeparator, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.TOP, 0, 6, 0, 0));

        qrButton = new ButtonWithCounterView(context, false, resourcesProvider);
        SpannableStringBuilder qrButtonText = new SpannableStringBuilder("QR");
        qrButtonText.setSpan(new ColoredImageSpan(R.drawable.header_qr_24), 0, qrButtonText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        qrButtonText.append("  ");
        qrButtonText.append("Add via QR Code");
        qrButton.setText(qrButtonText, false);
        qrButton.setOnClickListener(v -> {
            dismiss();
            CameraScanActivity.showAsSheet(LaunchActivity.instance, false, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
                @Override
                public void didFindQr(String text) {
                    final String username = Browser.extractUsername(text);
                    if (!TextUtils.isEmpty(username)) {
                        MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(username, peerId -> {
                            if (peerId == null || peerId == Long.MAX_VALUE) {
                                AndroidUtilities.runOnUIThread(() -> BulletinFactory.global().createSimpleBulletin(
                                    LocaleController.getString(R.string.ScanQrCode),
                                    LocaleController.getString(R.string.ErrorOccurred)
                                ).show());
                                return;
                            }

                            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                            if (lastFragment != null) {
                                lastFragment.presentFragment(ProfileActivity.of(peerId));
                            }
                        });
                    } else {
                        AndroidUtilities.runOnUIThread(() -> BulletinFactory.global().createSimpleBulletin(
                            LocaleController.getString(R.string.ScanQrCode),
                            LocaleController.getString(R.string.ErrorOccurred)
                        ).show());
                    }
                }
            });
        });
        qrButtonContainer.addView(qrButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 0, 12, 0, 0));

        notesField = new OutlineEditText(context);
        notesField.setBackground(null);
        notesField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        notesField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        notesField.setHint("Notes");
        qrButtonContainer.addView(notesField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.TOP, 0, 0, 0, 0));
        notesField.getEditText().setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                codeField.requestFocus();
                codeField.setSelection(codeField.length());
                return true;
            }
            return false;
        });
        updateQrButtonVisible(false);

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
        doneButton.setText(LocaleController.getString(R.string.CreateContact));
        doneButton.setTextColor(parentFragment.getThemedColor(Theme.key_featuredStickers_buttonText));
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        doneButton.setTypeface(AndroidUtilities.bold());

        progressView = new RadialProgressView(context);
        progressView.setSize(dp(20));
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

    private void updateBottomTranslation(boolean empty) {
        checkLayout.animate()
            .translationY(empty ? -dp(21.33f) : 0)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .setDuration(420)
            .start();
        qrButtonContainer.animate()
            .translationY(empty ? -dp(21.33f / 2) : 0)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .setDuration(420)
            .start();
    }
    private void updateQrButtonVisible(boolean animated) {
        final boolean visible = !checkBox.isChecked();

        if (animated) {
            qrButton.setVisibility(View.VISIBLE);
            qrButton.animate()
                .alpha(visible ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .withEndAction(() -> {
                    if (!visible) {
                        qrButton.setVisibility(View.INVISIBLE);
                    }
                })
                .start();
            qrButtonSeparator.setVisibility(View.VISIBLE);
            qrButtonSeparator.animate()
                .alpha(visible ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .withEndAction(() -> { if (!visible) qrButtonSeparator.setVisibility(View.INVISIBLE); })
                .start();
            notesField.setVisibility(View.VISIBLE);
            notesField.animate()
                .alpha(!visible ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .withEndAction(() -> { if (visible) notesField.setVisibility(View.INVISIBLE); })
                .start();
        } else {
            qrButton.animate().cancel();
            qrButton.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            qrButton.setAlpha(visible ? 1.0f : 0.0f);
            qrButtonSeparator.animate().cancel();
            qrButtonSeparator.setVisibility(visible ? View.VISIBLE : View.GONE);
            qrButtonSeparator.setAlpha(visible ? 1.0f : 0.0f);
            notesField.animate().cancel();
            notesField.setVisibility(!visible ? View.VISIBLE : View.INVISIBLE);
            notesField.setAlpha(!visible ? 1.0f : 0.0f);
        }
    }

    private void updatedTextPhone() {
        final String phone = (codeField.getText().toString() + phoneField.getText().toString()).replaceAll("[^\\d]+", "");
        boolean isFull = false;
        for (int i = Math.min(3, phone.length()); i >= 0; i--) {
            String code = phone.substring(0, i);

            List<CountrySelectActivity.Country> list = codesMap.get(code);
            if (list != null && !list.isEmpty()) {
                List<String> patterns = phoneFormatMap.get(code);

                if (patterns == null || patterns.isEmpty()) {
                    continue;
                }

                for (String pattern : patterns) {
                    String pat = pattern.replace(" ", "");
                    if (phone.length() - i >= pat.length()) {
                        isFull = true;
                        break;
                    }
                }
            }
            if (isFull) break;
        }

        if (!isFull) {
            if (!TextUtils.isEmpty(lastPhone)) {
                lastPhone = null;
                updatedPhone(null);
            }
            return;
        }

        if (!TextUtils.equals(lastPhone, phone)) {
            updatedPhone(lastPhone = phone);
            return;
        }
    }

    private String lastPhone;
    private int requestingPhoneId = -1;
    private void updatedPhone(final String phone) {
        if (requestingPhoneId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestingPhoneId, true);
            requestingPhoneId = -1;
        }

        if (TextUtils.isEmpty(phone)) {
            phoneStatusView.animate()
                .scaleX(0.5f).scaleY(0.5f)
                .alpha(0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .start();
            underPhoneTextView.setText("");
            updateBottomTranslation(true);
            return;
        }

        phoneStatusView.animate()
            .scaleX(1.0f).scaleY(1.0f)
            .alpha(1.0f)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .setDuration(420)
            .start();
        phoneStatusView.setImageDrawable(
            new CircularProgressDrawable(dp(30), dp(3), getThemedColor(Theme.key_dialogTextBlue))
        );
        underPhoneTextView.setText("");
        updateBottomTranslation(true);

        final Utilities.Callback<TLRPC.User> onUser = user -> {
            if (user == null) {
                phoneStatusView.setImageDrawable(null);
                underPhoneTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag("This phone number is not on Telegram. **Invite >**", () -> {
                    final Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("sms:+" + phone));
                    intent.putExtra("sms_body", LocaleController.formatString(R.string.InviteText2, "https://telegram.org/dl"));
                    getContext().startActivity(intent);
                }), true, dp(8f / 3f), dp(1)));
            } else {
                Drawable drawable = getContext().getResources().getDrawable(R.drawable.msg_text_check).mutate();
                drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlueIcon), PorterDuff.Mode.SRC_IN));
                phoneStatusView.setImageDrawable(drawable);
                if (user.contact) {
                    underPhoneTextView.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag("This phone number is already in your contacts. **View >**", () -> {
                        dismiss();

                        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            lastFragment.presentFragment(ProfileActivity.of(user.id));
                        }
                    }), true, dp(8f / 3f), dp(1)));
                } else {
                    underPhoneTextView.setText("This phone number is on Telegram.");
                }
            }
            updateBottomTranslation(false);
        };

        final TLRPC.TL_contact contact = ContactsController.getInstance(currentAccount).contactsByPhone.get(PhoneFormat.stripExceptNumbers(phone));
        if (contact != null) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
            if (user != null) {
                onUser.run(user);
            } else {
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    final TLRPC.User user2 = MessagesStorage.getInstance(currentAccount).getUser(contact.user_id);
                    AndroidUtilities.runOnUIThread(() -> onUser.run(user2));
                });
            }
        } else {
            final TLRPC.TL_contacts_resolvePhone req = new TLRPC.TL_contacts_resolvePhone();
            req.phone = PhoneFormat.stripExceptNumbers(phone);
            requestingPhoneId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                TLRPC.User user = null;
                if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                    final TLRPC.TL_contacts_resolvedPeer r = (TLRPC.TL_contacts_resolvedPeer) res;
                    MessagesController.getInstance(currentAccount).putUsers(r.users, false);
                    MessagesController.getInstance(currentAccount).putChats(r.chats, false);

                    long did = DialogObject.getPeerDialogId(r.peer);
                    if (did >= 0) {
                        user = MessagesController.getInstance(currentAccount).getUser(did);
                    }
                }
                onUser.run(user);
            }));
        }
    }

    private void doOnDone() {
        if (donePressed || parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        if (firstNameField.getEditText().length() == 0) {
            final Vibrator v = (Vibrator) parentFragment.getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(firstNameField);
            return;
        }
        if (codeField.length() == 0) {
            final Vibrator v = (Vibrator) parentFragment.getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(codeField);
            return;
        }
        if (phoneField.length() == 0) {
            final Vibrator v = (Vibrator) parentFragment.getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(200);
            }
            AndroidUtilities.shakeView(phoneField);
            return;
        }

        if (checkBox.isChecked()) {
            PermissionRequest.ensurePermission(R.raw.permission_request_contacts, R.string.PermissionNoContactsSaving, Manifest.permission.WRITE_CONTACTS, granted -> {
                if (granted) {
                    done();
                }
            });
        } else {
            done();
        }
    }

    private void done() {

        donePressed = true;
        showEditDoneProgress(true, true);

        final String phone = "+" + codeField.getText().toString() + phoneField.getText().toString();
        final String firstName = firstNameField.getEditText().getText().toString();
        final String lastName = lastNameField.getEditText().getText().toString();
        final String notes = notesField.getVisibility() == View.VISIBLE ? notesField.getEditText().getText().toString() : "";

        final TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
        final TLRPC.TL_inputPhoneContact inputPhoneContact = new TLRPC.TL_inputPhoneContact();
        inputPhoneContact.first_name = firstName;
        inputPhoneContact.last_name = lastName;
        inputPhoneContact.phone = phone;
        if (!TextUtils.isEmpty(notes)) {
            inputPhoneContact.flags |= 1;
            inputPhoneContact.note = new TLRPC.TL_textWithEntities();
            inputPhoneContact.note.text = notes;
        }
        req.contacts.add(inputPhoneContact);
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
            AndroidUtilities.runOnUIThread(() -> {
                donePressed = false;
                if (res != null) {
                    if (!res.users.isEmpty()) {
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).openChatOrProfileWith(res.users.get(0), null, parentFragment, 1, false);
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

        if (checkBox.isChecked()) {
            saveContact(getContext(), phone, firstName, lastName, null, account);
        }
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

    public NewContactBottomSheet setInitialPhoneNumber(String value, boolean withCountryCode) {
        initialPhoneNumber = value;
        initialPhoneNumberWithCountryCode = withCountryCode;

        if (!TextUtils.isEmpty(initialPhoneNumber)) {
            TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (initialPhoneNumber.startsWith("+")) {
                codeField.setText(initialPhoneNumber.substring(1));
            } else if (initialPhoneNumberWithCountryCode || user == null || TextUtils.isEmpty(user.phone)) {
                codeField.setText(initialPhoneNumber);
            } else {
                boolean foundCountry = false;
                String phone = user.phone;
                for (int a = 4; a >= 1; a--) {
                    String sub = phone.substring(0, a);
                    List<CountrySelectActivity.Country> country = codesMap.get(sub);
                    if (country != null && country.size() > 0) {
                        final String regionCode = country.get(0).code;
                        codeField.setText(regionCode);
                        if (regionCode.endsWith("0") && initialPhoneNumber.startsWith("0")) {
                            initialPhoneNumber = initialPhoneNumber.substring(1);
                        }
                        foundCountry = true;
                        break;
                    }
                }
                if (!foundCountry && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    final Context ctx = ApplicationLoader.applicationContext;
                    final String regionCode = (ctx != null) ? ctx.getSystemService(TelephonyManager.class).
                            getSimCountryIso().toUpperCase(Locale.US) : Locale.getDefault().getCountry();
                    codeField.setText(regionCode);
                    if (regionCode.endsWith("0") && initialPhoneNumber.startsWith("0")) {
                        initialPhoneNumber = initialPhoneNumber.substring(1);
                    }
                }
                phoneField.setText(initialPhoneNumber);
            }
            initialPhoneNumber = null;
        }

        return this;
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
        setCountryButtonText(Emoji.replaceEmoji(sb, countryFlag.getPaint().getFontMetricsInt(), false));
        countryCodeForHint = code;
        wasCountryHintIndex = -1;
        invalidateCountryHint();
    }

    private void setCountryButtonText(CharSequence cs) {
        if (TextUtils.isEmpty(cs)) {
            countryFlag.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationY(dp(30)).setDuration(150);
            plusTextView.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationX(-dp(30)).setDuration(150);
            codeField.animate().setInterpolator(CubicBezierInterpolator.DEFAULT).translationX(-dp(30)).setDuration(150);
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

    public static boolean saveContact(
        Context context,
        String phone,
        String firstName,
        String lastName,
        String notes,
        AccountInfo into
    ) {
        final ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        int rawContactIndex = 0;

        final ContentProviderOperation.Builder builder =
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);

        if (into != null) {
            builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, into.type);
            builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, into.name);
        } else {
            builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, (String) null);
            builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, (String) null);
        }

        ops.add(builder.build());

        ContentProviderOperation.Builder op =
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        if (!TextUtils.isEmpty(firstName)) {
            op = op.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName);
        }
        if (!TextUtils.isEmpty(lastName)) {
            op = op.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, lastName);
        }
        ops.add(op.build());

        if (phone != null && !phone.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build());
        }

        if (notes != null && !notes.isEmpty()) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                .build());
        }

        try {
            final ContentResolver resolver = context.getContentResolver();
            resolver.applyBatch(ContactsContract.AUTHORITY, ops);
            return true;
        } catch (RemoteException | OperationApplicationException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static ArrayList<AccountInfo> getAllAccounts(Context context) {
        final ArrayList<AccountInfo> accountList = new ArrayList<>();
        final AccountManager accountManager = AccountManager.get(context);
        final Account[] accounts = accountManager.getAccounts();
        for (Account account : accounts) {
//            if (canStoreContacts(context, account)) {
                final AccountInfo info = new AccountInfo();
                info.name = account.name;
                info.type = account.type;
                info.displayName = getDisplayNameForAccount(context, account);
                accountList.add(info);
//            }
        }
//        addSimAccounts(context, accountList);
        return accountList;
    }

    private static String getDisplayNameForAccount(Context context, Account account) {
        String accountType = account.type;
        String accountName = account.name;

        // For Google accounts, use email
        if (accountType.equals("com.google")) {
            return accountName; // This is typically the email
        }

        // For SIM accounts, try to get phone number
        if (accountType.contains("sim") || accountType.contains("usim")) {
            String phoneNumber = getSimPhoneNumber(context, accountName);
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                return phoneNumber;
            }
            // Fallback to account name which should have slot info
            return accountName != null && !accountName.isEmpty() ?
                    accountName : "SIM Card";
        }

        // For local/device account
        if (accountType.equals("com.android.localphone") ||
                accountName == null || accountName.isEmpty() ||
                accountName.equals("Device")) {
            return "Device";
        }

        // For other accounts (Exchange, Office365, etc.), use email/account name
        return accountName;
    }

    private static String getSimPhoneNumber(Context context, String accountName) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Try to get from SubscriptionManager
                SubscriptionManager subscriptionManager =
                        (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

                if (subscriptionManager != null) {
                    List<SubscriptionInfo> subscriptionInfoList =
                            subscriptionManager.getActiveSubscriptionInfoList();

                    if (subscriptionInfoList != null) {
                        for (SubscriptionInfo info : subscriptionInfoList) {
                            String number = info.getNumber();
                            if (number != null && !number.isEmpty()) {
                                return number;
                            }
                        }
                    }
                }
            }

            // Fallback to TelephonyManager
            TelephonyManager telephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

            if (telephonyManager != null) {
                String phoneNumber = telephonyManager.getLine1Number();
                if (phoneNumber != null && !phoneNumber.isEmpty()) {
                    return phoneNumber;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static class AccountInfo {
        public String name;
        public String type;
        public String displayName;

        @Override
        public String toString() {
            return "Account \""+displayName+"\" {name='" + name + "', type='" + type + "'}";
        }
    }
}
