/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProgressButton;

@SuppressLint("ViewConstructor")
public class InviteUserCell extends FrameLayout {

    private final BackupImageView avatarImageView;
    private final SimpleTextView nameTextView;
    private final SimpleTextView statusTextView;
    private final AvatarDrawable avatarDrawable;
    private @Nullable final ProgressButton button;
    private @Nullable final CheckBox2 checkBox;
    private ContactsController.Contact currentContact;
    private CharSequence currentName;

    public InviteUserCell(Context context, boolean needCheck) {
        super(context);
        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(23));
        addView(avatarImageView, LayoutHelper.createFrame(46, 46, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 13, 6, 13, 6));

        LinearLayout nameAndButton = new LinearLayout(context);
        nameAndButton.setOrientation(LinearLayout.HORIZONTAL);
        addView(nameAndButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL,
                (LocaleController.isRTL ? 0 : 72), 0, (LocaleController.isRTL ? 72 : 0), 0));

        FrameLayout namesLayout = new FrameLayout(context);
        nameAndButton.addView(namesLayout, LayoutHelper.createLinear(0, 58, 1f));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setTextSize(15);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        namesLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 9, 0, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(13);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        namesLayout.addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 33, 0, 0));

        if (needCheck) {
            button = null;
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40, 32, LocaleController.isRTL ? 39 : 0, 0));
        } else {
            checkBox = null;

            button = new ProgressButton(context);
            button.setText(getString(R.string.Invite));
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            button.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
            button.setBackgroundRoundRect(Theme.getColor(Theme.key_telegram_color), Theme.getColor(Theme.key_featuredStickers_addButtonPressed), 16);
            button.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
            nameAndButton.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, 0f, Gravity.CENTER_VERTICAL, 18, 0, 18, 0));
            button.setOnClickListener(v -> InviteUserCell.this.performClick());
        }
    }

    public void setUser(ContactsController.Contact contact, CharSequence name) {
        currentContact = contact;
        currentName = name;
        update(0);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox != null) {
            checkBox.setChecked(checked, animated);
        }
    }

    public ContactsController.Contact getContact() {
        return currentContact;
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void update(int mask) {
        if (currentContact == null) {
            return;
        }
        String newName = null;

        avatarDrawable.setInfo(currentContact.contact_id, currentContact.first_name, currentContact.last_name, null, null, null, false);


        if (currentName != null) {
            nameTextView.setText(currentName, true);
        } else {
            nameTextView.setText(ContactsController.formatName(currentContact.first_name, currentContact.last_name));
        }

        statusTextView.setTag(Theme.key_windowBackgroundWhiteGrayText);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        if (currentContact.imported > 0) {
            statusTextView.setText(LocaleController.formatPluralString("TelegramContacts", currentContact.imported));
        } else {
            statusTextView.setText(currentContact.phones.get(0));
        }

        avatarImageView.setImageDrawable(avatarDrawable);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
