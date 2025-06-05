package org.telegram.ui.Components.Premium.boosts.cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class BoostTypeCell extends BaseCell {

    public static int TYPE_GIVEAWAY = 0;
    public static int TYPE_SPECIFIC_USERS = 1;

    public static int TYPE_PREMIUM = 2;
    public static int TYPE_STARS = 3;

    private int selectedType;

    public BoostTypeCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        titleTextView.setTypeface(AndroidUtilities.bold());
    }

    @Override
    protected void updateLayouts() {
        imageView.setLayoutParams(LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 57, 0, 57, 0));
        titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (109), 0, LocaleController.isRTL ? (109) : 20, 0));
        subtitleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : (109), 0, LocaleController.isRTL ? (109) : 20, 0));
        radioButton.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 16, 0, 15, 0));
    }

    public int getSelectedType() {
        return selectedType;
    }

    @Override
    protected boolean needCheck() {
        return true;
    }

    public void setType(int type, int count, TLRPC.User singleUser, boolean isSelected) {
        final boolean animated = selectedType == type;
        selectedType = type;
        if (type == TYPE_GIVEAWAY) {
            titleTextView.setText(getString(R.string.BoostingCreateGiveaway));
            setSubtitle(getString(R.string.BoostingWinnersRandomly));
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_GIFT);
            avatarDrawable.setColor(0xFF16A5F2, 0xFF1180F7);
            setDivider(true);
            setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        } else if (type == TYPE_SPECIFIC_USERS) {
            titleTextView.setText(LocaleController.getString(R.string.BoostingAwardSpecificUsers));
            if (count == 1 && singleUser != null) {
                CharSequence text = UserObject.getUserName(singleUser);
                text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                setSubtitle(withArrow(text));
            } else if (count > 0) {
                setSubtitle(withArrow(LocaleController.formatPluralString("Recipient", count)));
            } else {
                setSubtitle(withArrow(getString(R.string.BoostingSelectRecipients)));
            }
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_FILTER_GROUPS);
            avatarDrawable.setColor(0xFFC468F2, 0xFF965CFA);
            setDivider(false);
            setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
        } else if (type == TYPE_PREMIUM) {
            titleTextView.setText(getString(R.string.BoostingPremium));
            if (count == 1 && singleUser != null) {
                CharSequence text = UserObject.getUserName(singleUser);
                text = Emoji.replaceEmoji(text, subtitleTextView.getPaint().getFontMetricsInt(), false);
                setSubtitle(withArrow(text));
            } else if (count > 0) {
                setSubtitle(withArrow(LocaleController.formatPluralString("Recipient", count)));
            } else {
                setSubtitle(withArrow(getString(R.string.BoostingWinnersRandomly)));
            }
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2, resourcesProvider));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_PREMIUM);
            avatarDrawable.setColor(0xFFC468F2, 0xFF965CFA);
            setDivider(true);
            setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        } else if (type == TYPE_STARS) {
            titleTextView.setText(TextCell.applyNewSpan(getString(R.string.BoostingStars)));
            setSubtitle(getString(R.string.BoostingWinnersRandomly));
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_STARS);
            avatarDrawable.setColor(0xFFFDC21B, 0xFFF67447);
            setDivider(false);
            setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
        }
        radioButton.setChecked(isSelected, animated);
        imageView.setImageDrawable(avatarDrawable);
        imageView.setRoundRadius(dp(20));
    }
}
