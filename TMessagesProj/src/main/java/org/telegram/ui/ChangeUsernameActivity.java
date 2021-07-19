/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
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
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class ChangeUsernameActivity extends BaseFragment {

    private EditTextBoldCursor firstNameField;
    private View doneButton;
    private TextView checkTextView;
    private TextView helpTextView;

    private int checkReqId;
    private String lastCheckName;
    private Runnable checkRunnable;
    private boolean lastNameAvailable;
    private boolean ignoreCheck;
    private CharSequence infoText;

    private final static int done_button = 1;

    public class LinkSpan extends ClickableSpan {

        private String url;

        public LinkSpan(String value) {
            url = value;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }

        @Override
        public void onClick(View widget) {
            try {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("label", url);
                clipboard.setPrimaryClip(clip);
                if (BulletinFactory.canShowBulletin(ChangeUsernameActivity.this)) {
                    BulletinFactory.createCopyLinkBulletin(ChangeUsernameActivity.this).show();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                boolean result = super.onTouchEvent(widget, buffer, event);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    Selection.removeSelection(buffer);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Username", R.string.Username));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    saveName();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        if (user == null) {
            user = UserConfig.getInstance(currentAccount).getCurrentUser();
        }

        fragmentView = new LinearLayout(context);
        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView.setOnTouchListener((v, event) -> true);

        firstNameField = new EditTextBoldCursor(context);
        firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        firstNameField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        firstNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        firstNameField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        firstNameField.setMaxLines(1);
        firstNameField.setLines(1);
        firstNameField.setPadding(0, 0, 0, 0);
        firstNameField.setSingleLine(true);
        firstNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        firstNameField.setHint(LocaleController.getString("UsernamePlaceholder", R.string.UsernamePlaceholder));
        firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        firstNameField.setCursorSize(AndroidUtilities.dp(20));
        firstNameField.setCursorWidth(1.5f);
        firstNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE && doneButton != null) {
                doneButton.performClick();
                return true;
            }
            return false;
        });
        firstNameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                if (ignoreCheck) {
                    return;
                }
                checkUserName(firstNameField.getText().toString(), false);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String name = firstNameField.getText().toString();
                if (name.startsWith("@")) {
                    name = name.substring(1);
                }
                if (name.length() > 0) {
                    String url = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/" + name;
                    String text = LocaleController.formatString("UsernameHelpLink", R.string.UsernameHelpLink, url);
                    int index = text.indexOf(url);
                    SpannableStringBuilder textSpan = new SpannableStringBuilder(text);
                    if (index >= 0) {
                        textSpan.setSpan(new LinkSpan(url), index, index + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    helpTextView.setText(TextUtils.concat(infoText, "\n\n", textSpan));
                } else {
                    helpTextView.setText(infoText);
                }
            }
        });

        linearLayout.addView(firstNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24, 24, 24, 0));

        checkTextView = new TextView(context);
        checkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        checkTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        linearLayout.addView(checkTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 12, 24, 0));

        helpTextView = new TextView(context);
        helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        helpTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
        helpTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        helpTextView.setText(infoText = AndroidUtilities.replaceTags(LocaleController.getString("UsernameHelp", R.string.UsernameHelp)));
        helpTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        helpTextView.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection));
        helpTextView.setMovementMethod(new LinkMovementMethodMy());
        linearLayout.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 24, 10, 24, 0));

        checkTextView.setVisibility(View.GONE);

        if (user != null && user.username != null && user.username.length() > 0) {
            ignoreCheck = true;
            firstNameField.setText(user.username);
            firstNameField.setSelection(firstNameField.length());
            ignoreCheck = false;
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

    private boolean checkUserName(String name, boolean alert) {
        if (name != null && name.startsWith("@")) {
            name = name.substring(1);
        }
        if (!TextUtils.isEmpty(name)) {
            checkTextView.setVisibility(View.VISIBLE);
        } else {
            checkTextView.setVisibility(View.GONE);
        }
        if (alert && name.length() == 0) {
            return true;
        }
        if (checkRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            checkRunnable = null;
            lastCheckName = null;
            if (checkReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkReqId, true);
            }
        }
        lastNameAvailable = false;
        if (name != null) {
            if (name.startsWith("_") || name.endsWith("_")) {
                checkTextView.setText(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                checkTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                return false;
            }
            for (int a = 0; a < name.length(); a++) {
                char ch = name.charAt(a);
                if (a == 0 && ch >= '0' && ch <= '9') {
                    if (alert) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalidStartNumber", R.string.UsernameInvalidStartNumber));
                    } else {
                        checkTextView.setText(LocaleController.getString("UsernameInvalidStartNumber", R.string.UsernameInvalidStartNumber));
                        checkTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                    }
                    return false;
                }
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    if (alert) {
                        AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                    } else {
                        checkTextView.setText(LocaleController.getString("UsernameInvalid", R.string.UsernameInvalid));
                        checkTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                        checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                    }
                    return false;
                }
            }
        }
        if (name == null || name.length() < 5) {
            if (alert) {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
            } else {
                checkTextView.setText(LocaleController.getString("UsernameInvalidShort", R.string.UsernameInvalidShort));
                checkTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
            }
            return false;
        }
        if (name.length() > 32) {
            if (alert) {
                AlertsCreator.showSimpleAlert(this, LocaleController.getString("UsernameInvalidLong", R.string.UsernameInvalidLong));
            } else {
                checkTextView.setText(LocaleController.getString("UsernameInvalidLong", R.string.UsernameInvalidLong));
                checkTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
            }
            return false;
        }

        if (!alert) {
            String currentName = UserConfig.getInstance(currentAccount).getCurrentUser().username;
            if (currentName == null) {
                currentName = "";
            }
            if (name.equals(currentName)) {
                checkTextView.setText(LocaleController.formatString("UsernameAvailable", R.string.UsernameAvailable, name));
                checkTextView.setTag(Theme.key_windowBackgroundWhiteGreenText);
                checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
                return true;
            }

            checkTextView.setText(LocaleController.getString("UsernameChecking", R.string.UsernameChecking));
            checkTextView.setTag(Theme.key_windowBackgroundWhiteGrayText8);
            checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText8));
            lastCheckName = name;
            final String nameFinal = name;
            checkRunnable = () -> {
                TLRPC.TL_account_checkUsername req = new TLRPC.TL_account_checkUsername();
                req.username = nameFinal;
                checkReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    checkReqId = 0;
                    if (lastCheckName != null && lastCheckName.equals(nameFinal)) {
                        if (error == null && response instanceof TLRPC.TL_boolTrue) {
                            checkTextView.setText(LocaleController.formatString("UsernameAvailable", R.string.UsernameAvailable, nameFinal));
                            checkTextView.setTag(Theme.key_windowBackgroundWhiteGreenText);
                            checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
                            lastNameAvailable = true;
                        } else {
                            checkTextView.setText(LocaleController.getString("UsernameInUse", R.string.UsernameInUse));
                            checkTextView.setTag(Theme.key_windowBackgroundWhiteRedText4);
                            checkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText4));
                            lastNameAvailable = false;
                        }
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            };
            AndroidUtilities.runOnUIThread(checkRunnable, 300);
        }
        return true;
    }

    private void saveName() {
        String newName = firstNameField.getText().toString();
        if (newName.startsWith("@")) {
            newName = newName.substring(1);
        }
        if (!checkUserName(newName, true)) {
            return;
        }
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (getParentActivity() == null || user == null) {
            return;
        }
        String currentName = user.username;
        if (currentName == null) {
            currentName = "";
        }
        if (currentName.equals(newName)) {
            finishFragment();
            return;
        }

        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);

        final TLRPC.TL_account_updateUsername req = new TLRPC.TL_account_updateUsername();
        req.username = newName;

        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                final TLRPC.User user1 = (TLRPC.User) response;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user1);
                    MessagesController.getInstance(currentAccount).putUsers(users, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true);
                    UserConfig.getInstance(currentAccount).saveConfig(true);
                    finishFragment();
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    AlertsCreator.processError(currentAccount, error, ChangeUsernameActivity.this, req);
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);

        progressDialog.setOnCancelListener(dialog -> ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true));
        progressDialog.show();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(helpTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));

        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteRedText4));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGreenText));
        themeDescriptions.add(new ThemeDescription(checkTextView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText8));

        return themeDescriptions;
    }
}
