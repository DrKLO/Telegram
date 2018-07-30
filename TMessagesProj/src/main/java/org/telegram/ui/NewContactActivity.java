/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.HintEditText;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import static android.widget.LinearLayout.HORIZONTAL;

public class NewContactActivity extends BaseFragment implements AdapterView.OnItemSelectedListener {

    private ActionBarMenuItem editDoneItem;
    private ContextProgressView editDoneItemProgress;
    private EditTextBoldCursor firstNameField;
    private EditTextBoldCursor lastNameField;
    private EditTextBoldCursor codeField;
    private HintEditText phoneField;
    private BackupImageView avatarImage;
    private TextView countryButton;
    private AvatarDrawable avatarDrawable;
    private AnimatorSet editDoneItemAnimation;
    private TextView textView;
    private View lineView;

    private ArrayList<String> countriesArray = new ArrayList<>();
    private HashMap<String, String> countriesMap = new HashMap<>();
    private HashMap<String, String> codesMap = new HashMap<>();
    private HashMap<String, String> phoneFormatMap = new HashMap<>();

    private boolean ignoreOnTextChange;
    private boolean ignoreOnPhoneChange;
    private int countryState;
    private boolean ignoreSelection;
    private boolean donePressed;

    private final static int done_button = 1;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (donePressed) {
                        return;
                    }
                    if (firstNameField.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(firstNameField, 2, 0);
                        return;
                    }
                    if (codeField.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(codeField, 2, 0);
                        return;
                    }
                    if (phoneField.length() == 0) {
                        Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            v.vibrate(200);
                        }
                        AndroidUtilities.shakeView(phoneField, 2, 0);
                        return;
                    }
                    donePressed = true;
                    showEditDoneProgress(true, true);
                    final TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
                    final TLRPC.TL_inputPhoneContact inputPhoneContact = new TLRPC.TL_inputPhoneContact();
                    inputPhoneContact.first_name = firstNameField.getText().toString();
                    inputPhoneContact.last_name = lastNameField.getText().toString();
                    inputPhoneContact.phone = "+" + codeField.getText().toString() + phoneField.getText().toString();
                    req.contacts.add(inputPhoneContact);
                    int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, final TLRPC.TL_error error) {
                            final TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    donePressed = false;
                                    if (res != null) {
                                        if (!res.users.isEmpty()) {
                                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                                            MessagesController.openChatOrProfileWith(res.users.get(0), null, NewContactActivity.this, 1, true);
                                        } else {
                                            if (getParentActivity() == null) {
                                                return;
                                            }
                                            showEditDoneProgress(false, true);
                                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                            builder.setMessage(LocaleController.formatString("ContactNotRegistered", R.string.ContactNotRegistered, ContactsController.formatName(inputPhoneContact.first_name, inputPhoneContact.last_name)));
                                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                            builder.setPositiveButton(LocaleController.getString("Invite", R.string.Invite), new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    try {
                                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.fromParts("sms", inputPhoneContact.phone, null));
                                                        intent.putExtra("sms_body", ContactsController.getInstance(currentAccount).getInviteText(1));
                                                        getParentActivity().startActivityForResult(intent, 500);
                                                    } catch (Exception e) {
                                                        FileLog.e(e);
                                                    }
                                                }
                                            });
                                            showDialog(builder.create());
                                        }
                                    } else {
                                        showEditDoneProgress(false, true);
                                        AlertsCreator.processError(currentAccount, error, NewContactActivity.this, req);
                                    }
                                }
                            });
                        }
                    }, ConnectionsManager.RequestFlagFailOnServerErrors);
                    ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
                }
            }
        });

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(5, "", "", false);

        ActionBarMenu menu = actionBar.createMenu();
        editDoneItem = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        editDoneItemProgress = new ContextProgressView(context, 1);
        editDoneItem.addView(editDoneItemProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        editDoneItemProgress.setVisibility(View.INVISIBLE);

        fragmentView = new ScrollView(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        ((ScrollView) fragmentView).addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        linearLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 0));

        avatarImage = new BackupImageView(context);
        avatarImage.setImageDrawable(avatarDrawable);
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(60, 60, Gravity.LEFT | Gravity.TOP, 0, 9, 0, 0));

        firstNameField = new EditTextBoldCursor(context);
        firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        firstNameField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        firstNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        firstNameField.setMaxLines(1);
        firstNameField.setLines(1);
        firstNameField.setSingleLine(true);
        firstNameField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        firstNameField.setGravity(Gravity.LEFT);
        firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
        firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        firstNameField.setCursorSize(AndroidUtilities.dp(20));
        firstNameField.setCursorWidth(1.5f);
        frameLayout.addView(firstNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 84, 0, 0, 0));
        firstNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_NEXT) {
                    lastNameField.requestFocus();
                    lastNameField.setSelection(lastNameField.length());
                    return true;
                }
                return false;
            }
        });
        firstNameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                avatarDrawable.setInfo(5, firstNameField.getText().toString(), lastNameField.getText().toString(), false);
                avatarImage.invalidate();
            }
        });

        lastNameField = new EditTextBoldCursor(context);
        lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        lastNameField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        lastNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        lastNameField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        lastNameField.setMaxLines(1);
        lastNameField.setLines(1);
        lastNameField.setSingleLine(true);
        lastNameField.setGravity(Gravity.LEFT);
        lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        lastNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
        lastNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        lastNameField.setCursorSize(AndroidUtilities.dp(20));
        lastNameField.setCursorWidth(1.5f);
        frameLayout.addView(lastNameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 34, Gravity.LEFT | Gravity.TOP, 84, 44, 0, 0));
        lastNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
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
        lastNameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                avatarDrawable.setInfo(5, firstNameField.getText().toString(), lastNameField.getText().toString(), false);
                avatarImage.invalidate();
            }
        });

        countryButton = new TextView(context);
        countryButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        countryButton.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6), 0);
        countryButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        countryButton.setMaxLines(1);
        countryButton.setSingleLine(true);
        countryButton.setEllipsize(TextUtils.TruncateAt.END);
        countryButton.setGravity(Gravity.LEFT | Gravity.CENTER_HORIZONTAL);
        countryButton.setBackgroundResource(R.drawable.spinner_states);
        linearLayout.addView(countryButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 0, 24, 0, 14));
        countryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CountrySelectActivity fragment = new CountrySelectActivity(true);
                fragment.setCountrySelectActivityDelegate(new CountrySelectActivity.CountrySelectActivityDelegate() {
                    @Override
                    public void didSelectCountry(String name, String shortName) {
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

        lineView = new View(context);
        lineView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        lineView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayLine));
        linearLayout.addView(lineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, -17.5f, 0, 0));

        LinearLayout linearLayout2 = new LinearLayout(context);
        linearLayout2.setOrientation(HORIZONTAL);
        linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 0));

        textView = new TextView(context);
        textView.setText("+");
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        linearLayout2.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        codeField = new EditTextBoldCursor(context);
        codeField.setInputType(InputType.TYPE_CLASS_PHONE);
        codeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        codeField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        codeField.setCursorSize(AndroidUtilities.dp(20));
        codeField.setCursorWidth(1.5f);
        codeField.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
        codeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        codeField.setMaxLines(1);
        codeField.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        codeField.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(5);
        codeField.setFilters(inputFilters);
        linearLayout2.addView(codeField, LayoutHelper.createLinear(55, 36, -9, 0, 16, 0));
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
        phoneField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        linearLayout2.addView(phoneField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
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
                if (i == EditorInfo.IME_ACTION_DONE) {
                    editDoneItem.performClick();
                    return true;
                }
                return false;
            }
        });

        HashMap<String, String> languageMap = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("countries.txt")));
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

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean animations = preferences.getBoolean("view_animations", true);
        if (!animations) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
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

    private void showEditDoneProgress(final boolean show, boolean animated) {
        if (editDoneItemAnimation != null) {
            editDoneItemAnimation.cancel();
        }
        if (!animated) {
            if (show) {
                editDoneItem.getImageView().setScaleX(0.1f);
                editDoneItem.getImageView().setScaleY(0.1f);
                editDoneItem.getImageView().setAlpha(0.0f);
                editDoneItemProgress.setScaleX(1.0f);
                editDoneItemProgress.setScaleY(1.0f);
                editDoneItemProgress.setAlpha(1.0f);
                editDoneItem.getImageView().setVisibility(View.INVISIBLE);
                editDoneItemProgress.setVisibility(View.VISIBLE);
                editDoneItem.setEnabled(false);
            } else {
                editDoneItemProgress.setScaleX(0.1f);
                editDoneItemProgress.setScaleY(0.1f);
                editDoneItemProgress.setAlpha(0.0f);
                editDoneItem.getImageView().setScaleX(1.0f);
                editDoneItem.getImageView().setScaleY(1.0f);
                editDoneItem.getImageView().setAlpha(1.0f);
                editDoneItem.getImageView().setVisibility(View.VISIBLE);
                editDoneItemProgress.setVisibility(View.INVISIBLE);
                editDoneItem.setEnabled(true);
            }
        } else {
            editDoneItemAnimation = new AnimatorSet();
            if (show) {
                editDoneItemProgress.setVisibility(View.VISIBLE);
                editDoneItem.setEnabled(false);
                editDoneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "alpha", 0.0f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "alpha", 1.0f));
            } else {
                editDoneItem.getImageView().setVisibility(View.VISIBLE);
                editDoneItem.setEnabled(true);
                editDoneItemAnimation.playTogether(
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(editDoneItemProgress, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(editDoneItem.getImageView(), "alpha", 1.0f));

            }
            editDoneItemAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (editDoneItemAnimation != null && editDoneItemAnimation.equals(animation)) {
                        if (!show) {
                            editDoneItemProgress.setVisibility(View.INVISIBLE);
                        } else {
                            editDoneItem.getImageView().setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (editDoneItemAnimation != null && editDoneItemAnimation.equals(animation)) {
                        editDoneItemAnimation = null;
                    }
                }
            });
            editDoneItemAnimation.setDuration(150);
            editDoneItemAnimation.start();
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                if (avatarImage != null) {
                    avatarDrawable.setInfo(5, firstNameField.getText().toString(), lastNameField.getText().toString(), false);
                    avatarImage.invalidate();
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),

                new ThemeDescription(lastNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(lastNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),

                new ThemeDescription(codeField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(codeField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(codeField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),

                new ThemeDescription(phoneField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(phoneField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText),
                new ThemeDescription(phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField),
                new ThemeDescription(phoneField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated),

                new ThemeDescription(textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(lineView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhiteGrayLine),

                new ThemeDescription(countryButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(editDoneItemProgress, 0, null, null, null, null, Theme.key_contextProgressInner2),
                new ThemeDescription(editDoneItemProgress, 0, null, null, null, null, Theme.key_contextProgressOuter2),

                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, cellDelegate, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),
        };
    }
}
