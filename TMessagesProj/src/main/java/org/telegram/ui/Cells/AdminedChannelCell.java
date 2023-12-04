/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.URLSpanNoUnderline;

public class AdminedChannelCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private SimpleTextView nameTextView;
    private SimpleTextView statusTextView;
    private AvatarDrawable avatarDrawable;
    private ImageView deleteButton;
    private TLRPC.Chat currentChannel;
    private boolean isLast;
    private int currentAccount = UserConfig.selectedAccount;
    CheckBox2 checkBox;

    public AdminedChannelCell(Context context, OnClickListener onClickListener, boolean needCheck, int padding) {
        super(context);

        avatarDrawable = new AvatarDrawable();
        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(24));
        addView(avatarImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12 + padding, 6, LocaleController.isRTL ? 12 + padding : 0, 6));

        if (needCheck) {
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(3);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 42 + padding,  32, LocaleController.isRTL ? 42 + padding: 0, 0));
        }

        int leftPadding = onClickListener == null ? 24 : 62;
        nameTextView = new SimpleTextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(17);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? leftPadding : 73 + padding, 9.5f, LocaleController.isRTL ? 73 + padding: leftPadding, 0));

        statusTextView = new SimpleTextView(context);
        statusTextView.setTextSize(14);
        statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        statusTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? leftPadding : 73 + padding, 32.5f, LocaleController.isRTL ? 73 + padding : leftPadding, 6));

        if (onClickListener != null) {
            deleteButton = new ImageView(context);
            deleteButton.setScaleType(ImageView.ScaleType.CENTER);
            deleteButton.setImageResource(R.drawable.msg_panel_clear);
            deleteButton.setOnClickListener(onClickListener);
            deleteButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
            deleteButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.MULTIPLY));
            addView(deleteButton, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 7 : 0, 6, LocaleController.isRTL ? 0 : 7, 0));
        }
    }

    public void setChannel(TLRPC.Chat channel, boolean last) {
        final String url = MessagesController.getInstance(currentAccount).linkPrefix + "/";
        currentChannel = channel;
        avatarDrawable.setInfo(currentAccount, channel);
        nameTextView.setText(channel.title);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(url + ChatObject.getPublicUsername(channel));
        stringBuilder.setSpan(new URLSpanNoUnderline(""), url.length(), stringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        statusTextView.setText(stringBuilder);
        avatarImageView.setForUserOrChat(channel, avatarDrawable);
        isLast = last;
    }

    public void update() {
        avatarDrawable.setInfo(currentAccount, currentChannel);
        avatarImageView.invalidate();
    }

    public TLRPC.Chat getCurrentChannel() {
        return currentChannel;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60 + (isLast ? 12 : 0)), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public SimpleTextView getNameTextView() {
        return nameTextView;
    }

    public SimpleTextView getStatusTextView() {
        return statusTextView;
    }

    public ImageView getDeleteButton() {
        return deleteButton;
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }
}
