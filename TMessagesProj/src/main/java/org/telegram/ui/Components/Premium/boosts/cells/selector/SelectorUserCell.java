package org.telegram.ui.Components.Premium.boosts.cells.selector;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.cells.BaseCell;
import org.telegram.ui.Components.StatusBadgeComponent;

import java.util.Date;

@SuppressLint("ViewConstructor")
public class SelectorUserCell extends BaseCell {

    private final boolean[] isOnline = new boolean[1];
    @Nullable
    private final CheckBox2 checkBox;
    private final ImageView optionsView;
    private TLRPC.User user;
    private TLRPC.Chat chat;
    private TL_stories.TL_myBoost boost;
    StatusBadgeComponent statusBadgeComponent;

    public SelectorUserCell(Context context, boolean needCheck, Theme.ResourcesProvider resourcesProvider, boolean isGreen) {
        super(context, resourcesProvider);
        statusBadgeComponent = new StatusBadgeComponent(this);
        titleTextView.setTypeface(AndroidUtilities.bold());

        radioButton.setVisibility(View.GONE);
        if (needCheck) {
            checkBox = new CheckBox2(context, 21, resourcesProvider);
            if (isGreen) {
                checkBox.setColor(Theme.key_checkbox, Theme.key_checkboxDisabled, Theme.key_dialogRoundCheckBoxCheck);
            } else {
                checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_checkboxDisabled, Theme.key_dialogRoundCheckBoxCheck);
            }
            checkBox.setDrawUnchecked(true);
            checkBox.setDrawBackgroundAsArc(10);
            addView(checkBox);
            checkBox.setChecked(false, false);
            checkBox.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 13, 0, 14, 0));
            updateLayouts();
        } else {
            checkBox = null;
        }

        optionsView = new ImageView(context);
        optionsView.setScaleType(ImageView.ScaleType.CENTER);
        optionsView.setImageResource(R.drawable.ic_ab_other);
        optionsView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_inMenu, resourcesProvider), PorterDuff.Mode.SRC_IN));
        addView(optionsView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 12, 0, 12, 0));
    }

    public void setOptions(View.OnClickListener listener) {
        if (listener != null) {
            optionsView.setVisibility(View.VISIBLE);
            optionsView.setOnClickListener(listener);
        } else {
            optionsView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        statusBadgeComponent.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        statusBadgeComponent.onDetachedFromWindow();
    }

    public TLRPC.User getUser() {
        return user;
    }

    public TLRPC.Chat getChat() {
        return chat;
    }

    public TL_stories.TL_myBoost getBoost() {
        return boost;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox == null) return;
        if (checkBox.getVisibility() == View.VISIBLE) {
            checkBox.setChecked(checked, animated);
        }
    }

    public void setCheckboxAlpha(float alpha, boolean animated) {
        if (checkBox == null) return;
        if (animated) {
            if (Math.abs(checkBox.getAlpha() - alpha) > .1) {
                checkBox.animate().cancel();
                checkBox.animate().alpha(alpha).start();
            }
        } else {
            checkBox.animate().cancel();
            checkBox.setAlpha(alpha);
        }
    }

    public void setUser(TLRPC.User user) {
        optionsView.setVisibility(View.GONE);
        this.user = user;
        this.chat = null;
        avatarDrawable.setInfo(user);
        imageView.setRoundRadius(dp(20));
        imageView.setForUserOrChat(user, avatarDrawable);
        titleTextView.setText(UserObject.getUserName(user));
        isOnline[0] = false;
        setSubtitle(LocaleController.formatUserStatus(UserConfig.selectedAccount, user, isOnline));
        subtitleTextView.setTextColor(Theme.getColor(isOnline[0] ? Theme.key_dialogTextBlue2 : Theme.key_dialogTextGray3, resourcesProvider));
        if (checkBox != null) {
            checkBox.setAlpha(1f);
        }
        titleTextView.setRightDrawable(statusBadgeComponent.updateDrawable(user, Theme.getColor(Theme.key_chats_verifiedBackground), false));
    }

    public void setChat(TLRPC.Chat chat, int participants_count) {
        optionsView.setVisibility(View.GONE);

        this.chat = chat;
        this.user = null;
        avatarDrawable.setInfo(chat);
        imageView.setRoundRadius(dp(ChatObject.isForum(chat) ? 12 : 20));
        imageView.setForUserOrChat(chat, avatarDrawable);

        titleTextView.setText(chat.title);

        String subtitle;
        if (participants_count <= 0) {
            participants_count = chat.participants_count;
        }
        boolean isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
        if (participants_count >= 1) {
            subtitle = LocaleController.formatPluralString(isChannel? "Subscribers" : "Members", participants_count);
        } else {
            subtitle = LocaleController.getString(isChannel ? R.string.DiscussChannel : R.string.AccDescrGroup);
        }
        setSubtitle(subtitle);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
        setCheckboxAlpha(participants_count > 200 ? .3f : 1f, false);
    }

    public void setBoost(TL_stories.TL_myBoost boost) {
        optionsView.setVisibility(View.GONE);

        this.boost = boost;
        this.chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-DialogObject.getPeerDialogId(boost.peer));

        avatarDrawable.setInfo(chat);
        imageView.setRoundRadius(dp(20));
        imageView.setForUserOrChat(chat, avatarDrawable);

        titleTextView.setText(chat.title);

        subtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
        setSubtitle(LocaleController.formatString("BoostExpireOn", R.string.BoostExpireOn, LocaleController.getInstance().getFormatterBoostExpired().format(new Date(boost.expires * 1000L))));

        if (boost.cooldown_until_date > 0) {
            long diff = boost.cooldown_until_date * 1000L - System.currentTimeMillis();
            setSubtitle(LocaleController.formatString("BoostingAvailableIn", R.string.BoostingAvailableIn, buildCountDownTime(diff)));
            titleTextView.setAlpha(0.65f);
            subtitleTextView.setAlpha(0.65f);
            setCheckboxAlpha(0.3f, false);
        } else {
            titleTextView.setAlpha(1f);
            subtitleTextView.setAlpha(1f);
            setCheckboxAlpha(1f, false);
        }
    }

    public void updateTimer() {
        if (boost.cooldown_until_date > 0) {
            long diff = boost.cooldown_until_date * 1000L - System.currentTimeMillis();
            setSubtitle(LocaleController.formatString("BoostingAvailableIn", R.string.BoostingAvailableIn, buildCountDownTime(diff)));
            titleTextView.setAlpha(0.65f);
            subtitleTextView.setAlpha(0.65f);
            setCheckboxAlpha(0.3f, false);
        } else {
            setSubtitle(LocaleController.formatString("BoostExpireOn", R.string.BoostExpireOn, LocaleController.getInstance().getFormatterBoostExpired().format(new Date(boost.expires * 1000L))));
            if (titleTextView.getAlpha() < 1f) {
                titleTextView.animate().alpha(1f).start();
                subtitleTextView.animate().alpha(1f).start();
                setCheckboxAlpha(1f, true);
            } else {
                titleTextView.setAlpha(1f);
                subtitleTextView.setAlpha(1f);
                setCheckboxAlpha(1f, false);
            }
        }
    }

    private String buildCountDownTime(long diff) {
        long oneHourMs = 3600 * 1000;
        long oneMinuteMs = 60 * 1000;
        long oneSecondMs = 1000;
        long hours = diff / oneHourMs;
        long minutes = (diff % oneHourMs) / oneMinuteMs;
        long seconds = ((diff % oneHourMs) % oneMinuteMs) / oneSecondMs;

        StringBuilder stringBuilder = new StringBuilder();
        if (hours > 0) {
            stringBuilder.append(String.format("%02d", hours));
            stringBuilder.append(":");
        }
        stringBuilder.append(String.format("%02d", minutes));
        stringBuilder.append(":");
        stringBuilder.append(String.format("%02d", seconds));
        return stringBuilder.toString();
    }

    @Override
    protected boolean needCheck() {
        return checkBox != null;
    }
}