/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

public class ContactAddActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private View doneButton;
    private EditText firstNameField;
    private EditText lastNameField;
    private BackupImageView avatarImage;
    private TextView nameTextView;
    private TextView onlineTextView;

    private int user_id;
    private boolean addContact;
    private String phone = null;

    private final static int done_button = 1;

    public ContactAddActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        user_id = getArguments().getInt("user_id", 0);
        phone = getArguments().getString("phone");
        addContact = getArguments().getBoolean("addContact", false);
        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
        return user != null && super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (addContact) {
            actionBar.setTitle(LocaleController.getString("AddContactTitle", R.string.AddContactTitle));
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
                        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
                        user.first_name = firstNameField.getText().toString();
                        user.last_name = lastNameField.getText().toString();
                        ContactsController.getInstance().addContact(user);
                        finishFragment();
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                        preferences.edit().putInt("spam3_" + user_id, 1).commit();
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        fragmentView = new ScrollView(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        ((ScrollView) fragmentView).addView(linearLayout);
        ScrollView.LayoutParams layoutParams2 = (ScrollView.LayoutParams) linearLayout.getLayoutParams();
        layoutParams2.width = ScrollView.LayoutParams.MATCH_PARENT;
        layoutParams2.height = ScrollView.LayoutParams.WRAP_CONTENT;
        linearLayout.setLayoutParams(layoutParams2);
        linearLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        linearLayout.addView(frameLayout);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(24);
        layoutParams.leftMargin = AndroidUtilities.dp(24);
        layoutParams.rightMargin = AndroidUtilities.dp(24);
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        frameLayout.setLayoutParams(layoutParams);

        avatarImage = new BackupImageView(context);
        avatarImage.setRoundRadius(AndroidUtilities.dp(30));
        frameLayout.addView(avatarImage);
        FrameLayout.LayoutParams layoutParams3 = (FrameLayout.LayoutParams) avatarImage.getLayoutParams();
        layoutParams3.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        layoutParams3.width = AndroidUtilities.dp(60);
        layoutParams3.height = AndroidUtilities.dp(60);
        avatarImage.setLayoutParams(layoutParams3);

        nameTextView = new TextView(context);
        nameTextView.setTextColor(0xff212121);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(nameTextView);
        layoutParams3 = (FrameLayout.LayoutParams) nameTextView.getLayoutParams();
        layoutParams3.width = LayoutHelper.WRAP_CONTENT;
        layoutParams3.height = LayoutHelper.WRAP_CONTENT;
        layoutParams3.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 80);
        layoutParams3.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 80 : 0);
        layoutParams3.topMargin = AndroidUtilities.dp(3);
        layoutParams3.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        nameTextView.setLayoutParams(layoutParams3);

        onlineTextView = new TextView(context);
        onlineTextView.setTextColor(0xff999999);
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        onlineTextView.setLines(1);
        onlineTextView.setMaxLines(1);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        frameLayout.addView(onlineTextView);
        layoutParams3 = (FrameLayout.LayoutParams) onlineTextView.getLayoutParams();
        layoutParams3.width = LayoutHelper.WRAP_CONTENT;
        layoutParams3.height = LayoutHelper.WRAP_CONTENT;
        layoutParams3.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 80);
        layoutParams3.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 80 : 0);
        layoutParams3.topMargin = AndroidUtilities.dp(32);
        layoutParams3.gravity = (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP;
        onlineTextView.setLayoutParams(layoutParams3);

        firstNameField = new EditText(context);
        firstNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        firstNameField.setHintTextColor(0xff979797);
        firstNameField.setTextColor(0xff212121);
        firstNameField.setMaxLines(1);
        firstNameField.setLines(1);
        firstNameField.setSingleLine(true);
        firstNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        firstNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        firstNameField.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        firstNameField.setHint(LocaleController.getString("FirstName", R.string.FirstName));
        AndroidUtilities.clearCursorDrawable(firstNameField);
        linearLayout.addView(firstNameField);
        layoutParams = (LinearLayout.LayoutParams) firstNameField.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(24);
        layoutParams.height = AndroidUtilities.dp(36);
        layoutParams.leftMargin = AndroidUtilities.dp(24);
        layoutParams.rightMargin = AndroidUtilities.dp(24);
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        firstNameField.setLayoutParams(layoutParams);
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

        lastNameField = new EditText(context);
        lastNameField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        lastNameField.setHintTextColor(0xff979797);
        lastNameField.setTextColor(0xff212121);
        lastNameField.setMaxLines(1);
        lastNameField.setLines(1);
        lastNameField.setSingleLine(true);
        lastNameField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        lastNameField.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        lastNameField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        lastNameField.setHint(LocaleController.getString("LastName", R.string.LastName));
        AndroidUtilities.clearCursorDrawable(lastNameField);
        linearLayout.addView(lastNameField);
        layoutParams = (LinearLayout.LayoutParams) lastNameField.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(16);
        layoutParams.height = AndroidUtilities.dp(36);
        layoutParams.leftMargin = AndroidUtilities.dp(24);
        layoutParams.rightMargin = AndroidUtilities.dp(24);
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        lastNameField.setLayoutParams(layoutParams);
        lastNameField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    doneButton.performClick();
                    return true;
                }
                return false;
            }
        });

        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
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

        return fragmentView;
    }

    private void updateAvatarLayout() {
        if (nameTextView == null) {
            return;
        }
        TLRPC.User user = MessagesController.getInstance().getUser(user_id);
        if (user == null) {
            return;
        }
        nameTextView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        onlineTextView.setText(LocaleController.formatUserStatus(user));

        TLRPC.FileLocation photo = null;
        if (user.photo != null) {
            photo = user.photo.photo_small;
        }
        avatarImage.setImage(photo, "50_50", new AvatarDrawable(user));
    }

    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateAvatarLayout();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAvatarLayout();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
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
}
