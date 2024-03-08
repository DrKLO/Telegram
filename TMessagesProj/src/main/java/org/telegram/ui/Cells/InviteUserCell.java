/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;

public class InviteUserCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private CheckBox2 checkBox;
    private AvatarDrawable avatarDrawable;
    private ContactsController.Contact currentContact;
    private CharSequence currentName;

    public InviteUserCell(Context context, boolean needCheck) {
        super(context);
        avatarDrawable = new AvatarDrawable();

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(50, 50, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 11, 11, LocaleController.isRTL ? 11 : 0, 0));

        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setTextSize(17);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 72, 14, LocaleController.isRTL ? 72 : 28, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(16);
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 28 : 72, 39, LocaleController.isRTL ? 72 : 28, 0));

        if (needCheck) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 40, 40, LocaleController.isRTL ? 39 : 0, 0));
        }
    }

    public void setUser(ContactsController.Contact contact, CharSequence name) {
        currentContact = contact;
        currentName = name;
        update(0);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public ContactsController.Contact getContact() {
        return currentContact;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(72), MeasureSpec.EXACTLY));
    }

    public void recycle() {
        avatarImageView.getImageReceiver().cancelLoadImage();
    }

    public void update(int mask) {
        if (currentContact == null) {
            return;
        }
        String newName = null;

        avatarDrawable.setInfo(currentContact.contact_id, currentContact.first_name, currentContact.last_name, null, null, null, true);


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
