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
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class ContactAddActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private View doneButton;
    private EditTextBoldCursor firstNameField;
    private EditTextBoldCursor lastNameField;
    private BackupImageView avatarImage;
    private TextView nameTextView;
    private TextView onlineTextView;
    private AvatarDrawable avatarDrawable;
    private TextView infoTextView;
    private CheckBoxCell checkBoxCell;

    private long user_id;
    private boolean addContact;
    private boolean needAddException;
    private String phone;

    private ContactAddActivityDelegate delegate;

    private final static int done_button = 1;

    public interface ContactAddActivityDelegate {
        void didAddToContacts();
    }

    public ContactAddActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        user_id = getArguments().getLong("user_id", 0);
        phone = getArguments().getString("phone");
        addContact = getArguments().getBoolean("addContact", false);
        needAddException = MessagesController.getNotificationsSettings(currentAccount).getBoolean("dialog_bar_exception" + user_id, false);
        TLRPC.User user = null;
        if (user_id != 0) {
            user = getMessagesController().getUser(user_id);
        }
        return user != null && super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (addContact) {
            actionBar.setTitle(LocaleController.getString("NewContact", R.string.NewContact));
        } else {
            actionBar.setTitle(LocaleController.getString("EditName", R.string.EditName));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (firstNameField.getText().length() != 0) {
                        TLRPC.User user = getMessagesController().getUser(user_id);
                        user.first_name = firstNameField.getText().toString();
                        user.last_name = lastNameField.getText().toString();
                        getContactsController().addContact(user, checkBoxCell != null && checkBoxCell.isChecked());
                        SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                        preferences.edit().putInt("dialog_bar_vis3" + user_id, 3).commit();
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                        getNotificationCenter().postNotificationName(NotificationCenter.peerSettingsDidLoad, user_id);
                        finishFragment();
                        if (delegate != null) {
                            delegate.didAddToContacts();
                        }
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItem(done_button, LocaleController.getString("Done", R.string.Done).toUpperCase());

        fragmentView = new ScrollView(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        ((ScrollView) fragmentView).addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        linearLayout.setOnTouchListener((v, event) -> true);

        FrameLayout frameLayout = new FrameLayout(context);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 24, 24, 0));

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(30));
        frameLayout.addView(avatarImage, LayoutHelper.createFrame(60, 60, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 80, 3, LocaleController.isRTL ? 80 : 0, 0));

        onlineTextView = new TextView(context);
        onlineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setLines(1);
        onlineTextView.setMaxLines(1);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        frameLayout.addView(onlineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 80, 32, LocaleController.isRTL ? 80 : 0, 0));

        firstNameField = new EditTextBoldCursor(context);
        firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        firstNameField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        firstNameField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        firstNameField.setBackgroundDrawable(Theme.createEditTextDrawable(context, false));
        firstNameField.setMaxLines(1);
        firstNameField.setLines(1);
        firstNameField.setSingleLine(true);
        firstNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
        firstNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        firstNameField.setCursorSize(AndroidUtilities.dp(20));
        firstNameField.setCursorWidth(1.5f);
        linearLayout.addView(firstNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24, 24, 24, 0));
        firstNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                lastNameField.requestFocus();
                lastNameField.setSelection(lastNameField.length());
                return true;
            }
            return false;
        });
        firstNameField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            boolean focued;
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!paused && !hasFocus && focued) {
                    FileLog.d("changed");
                }
                focued = hasFocus;
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
        lastNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        lastNameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
        lastNameField.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        lastNameField.setCursorSize(AndroidUtilities.dp(20));
        lastNameField.setCursorWidth(1.5f);
        linearLayout.addView(lastNameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, 24, 16, 24, 0));
        lastNameField.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                doneButton.performClick();
                return true;
            }
            return false;
        });

        TLRPC.User user = getMessagesController().getUser(user_id);
        if (user != null) {
            if (user.phone == null) {
                if (phone != null) {
                    user.phone = PhoneFormat.stripExceptNumbers(phone);
                }
            }
            firstNameField.setText(user.first_name);
            firstNameField.setSelection(firstNameField.length());
            lastNameField.setText(user.last_name);
        }

        infoTextView = new TextView(context);
        infoTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        if (addContact) {
            if (!needAddException || TextUtils.isEmpty(user.phone)) {
                linearLayout.addView(infoTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 18, 24, 0));
            }

            if (needAddException) {
                checkBoxCell = new CheckBoxCell(getParentActivity(), 0);
                checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                checkBoxCell.setText(LocaleController.formatString("SharePhoneNumberWith", R.string.SharePhoneNumberWith, UserObject.getFirstName(user)), "", true, false);
                checkBoxCell.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
                checkBoxCell.setOnClickListener(v -> checkBoxCell.setChecked(!checkBoxCell.isChecked(), true));
                linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
            }
        }

        return fragmentView;
    }

    public void setDelegate(ContactAddActivityDelegate contactAddActivityDelegate) {
        delegate = contactAddActivityDelegate;
    }

    private void updateAvatarLayout() {
        if (nameTextView == null) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(user_id);
        if (user == null) {
            return;
        }
        if (TextUtils.isEmpty(user.phone)) {
            nameTextView.setText(LocaleController.getString("MobileHidden", R.string.MobileHidden));
            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("MobileHiddenExceptionInfo", R.string.MobileHiddenExceptionInfo, UserObject.getFirstName(user))));
        } else {
            nameTextView.setText(PhoneFormat.getInstance().format("+" + user.phone));
            if (needAddException) {
                infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("MobileVisibleInfo", R.string.MobileVisibleInfo, UserObject.getFirstName(user))));
            }
        }
        onlineTextView.setText(LocaleController.formatUserStatus(currentAccount, user));
        avatarImage.setForUserOrChat(user, avatarDrawable = new AvatarDrawable(user));
    }

    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateAvatarLayout();
            }
        }
    }

    boolean paused;
    @Override
    public void onPause() {
        super.onPause();
        paused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAvatarLayout();
        if (firstNameField != null) {
            firstNameField.requestFocus();
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean animations = preferences.getBoolean("view_animations", true);
            if (!animations) {
                AndroidUtilities.showKeyboard(firstNameField);
            }
        }
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
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (avatarImage != null) {
                TLRPC.User user = getMessagesController().getUser(user_id);
                if (user == null) {
                    return;
                }
                avatarDrawable.setInfo(user);
                avatarImage.invalidate();
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(nameTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(onlineTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(firstNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(lastNameField, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(infoTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(null, 0, null, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        return themeDescriptions;
    }
}
