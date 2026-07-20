package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;

public class JoinToSendSettingsView extends LinearLayout {

    public HeaderCell joinHeaderCell;
    public TextCheckCell joinToSendCell;
    public TextCheckCell joinRequestCell;
    public TextInfoPrivacyCell joinToSendInfoCell;
    public TextInfoPrivacyCell joinRequestInfoCell;

    public boolean isJoinToSend, isJoinRequest;
    private TLRPC.Chat currentChat;

    public JoinToSendSettingsView(Context context, TLRPC.Chat currentChat) {
        super(context);
        this.currentChat = currentChat;

        isJoinToSend = currentChat.join_to_send;
        isJoinRequest = currentChat.join_request;

        setOrientation(LinearLayout.VERTICAL);

        joinHeaderCell = new HeaderCell(context, 20);
        joinHeaderCell.setText(LocaleController.getString(R.string.ChannelSettingsJoinTitle));
        joinHeaderCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        addView(joinHeaderCell);

        joinToSendCell = new TextCheckCell(context, 20);
        joinToSendCell.setTextAndCheck(LocaleController.getString(R.string.ChannelSettingsJoinToSend), isJoinToSend, isJoinToSend);
        joinToSendCell.setEnabled(currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.ban_users);
        joinToSendCell.setOnClickListener(e -> {
            final boolean oldValue = isJoinToSend, newValue = !isJoinToSend;
            final boolean oldJoinToRequest = isJoinRequest;
            if (onJoinToSendToggle(newValue, () -> AndroidUtilities.runOnUIThread(() -> {
                setJoinRequest(oldJoinToRequest);
                setJoinToSend(oldValue);
            }))) {
                setJoinRequest(false);
                setJoinToSend(newValue);
            }
        });
        addView(joinToSendCell);

        joinRequestCell = new TextCheckCell(context, 20);
        joinRequestCell.setTextAndCheck(LocaleController.getString(R.string.ChannelSettingsJoinRequest), isJoinRequest, false);
        joinRequestCell.setPivotY(0);
        joinRequestCell.setEnabled(currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.ban_users);
        joinRequestCell.setOnClickListener(e -> {
            final boolean oldValue = isJoinRequest, newValue = !isJoinRequest;
            if (onJoinRequestToggle(newValue, () -> AndroidUtilities.runOnUIThread(() -> {
                setJoinRequest(oldValue);
            }))) {
                setJoinRequest(newValue);
            }
        });
        addView(joinRequestCell);

        joinToSendInfoCell = new TextInfoPrivacyCell(context, 12);
        joinToSendInfoCell.setText(LocaleController.getString(R.string.ChannelSettingsJoinToSendInfo));
        addView(joinToSendInfoCell);

        joinRequestInfoCell = new TextInfoPrivacyCell(context, 12);
        joinRequestInfoCell.setText(LocaleController.getString(R.string.ChannelSettingsJoinRequestInfo));
        addView(joinRequestInfoCell);

        toggleValue = isJoinToSend ? 1f : 0f;
        joinRequestCell.setVisibility(isJoinToSend ? View.VISIBLE : View.GONE);
        updateToggleValue(toggleValue);
    }

    public float getBottomInfoMargin() {
        return joinToSendInfoCell.getAlpha() * joinToSendInfoCell.getHeight() + joinRequestInfoCell.getAlpha() * joinRequestInfoCell.getHeight();
    }

    public void setChat(TLRPC.Chat chat) {
        this.currentChat = chat;
        joinToSendCell.setEnabled(currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.ban_users);
        joinRequestCell.setEnabled(currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.ban_users);
    }

    public boolean onJoinToSendToggle(boolean newValue, Runnable cancel) {
        return true;
    }
    public boolean onJoinRequestToggle(boolean newValue, Runnable cancel) {
        return true;
    }

    private ValueAnimator toggleAnimator;
    private float toggleValue;

    private void updateToggleValue(float value) {
        toggleValue = value;
        joinRequestCell.setAlpha(value);
        joinRequestCell.setTranslationY((1f - value) * -AndroidUtilities.dp(16));
        joinRequestCell.setScaleY(1f - (1f - value) * .1f);
        int joinRequestCellHeight = joinRequestCell.getMeasuredHeight() <= 0 ? AndroidUtilities.dp(50) : joinRequestCell.getMeasuredHeight();
        joinToSendInfoCell.setAlpha(1f - value);
        joinToSendInfoCell.setTranslationY(-joinRequestCellHeight * (1f - value) + -AndroidUtilities.dp(4) * value);
        joinRequestInfoCell.setAlpha(value);
        joinRequestInfoCell.setTranslationY(-joinRequestCellHeight * (1f - value) + AndroidUtilities.dp(4) * (1f - value));
        requestLayout();
    }

    public void showJoinToSend(boolean show) {
        joinHeaderCell.setVisibility(show ? View.VISIBLE : View.GONE);
        joinToSendCell.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            isJoinToSend = true;
            joinRequestCell.setVisibility(View.VISIBLE);
            updateToggleValue(1);
        }
        requestLayout();
    }

    public void setFullInfo(BaseFragment fragment, TLRPC.ChatFull chatFull) {
        final boolean isChannel = ChatObject.isChannelAndNotMegaGroup(currentChat);
        final boolean isPublic = ChatObject.isPublic(currentChat);

        if (chatFull != null && chatFull.guard_bot_id != 0) {
            final String name = "@" + DialogObject.getPublicUsername(MessagesController.getInstance(UserConfig.selectedAccount).getUser(chatFull.guard_bot_id));
            joinRequestInfoCell.setText(AndroidUtilities.replaceSingleLink(LocaleController.formatString(
                isChannel ?
                    R.string.ChannelSettingsJoinRequestInfoManagedBy :
                    (isPublic ?
                        R.string.GroupPublicSettingsJoinRequestInfoManagedBy :
                        R.string.GroupPrivateSettingsJoinRequestInfoManagedBy), name
            ), Theme.getColor(Theme.key_telegram_color_text), () -> {
                Bundle bundle = new Bundle();
                bundle.putLong("user_id", chatFull.guard_bot_id);
                fragment.presentFragment(new ProfileActivity(bundle));
            }));
        } else {
            joinRequestInfoCell.setText(LocaleController.getString(isChannel ?
                R.string.ChannelSettingsJoinRequestInfo2 :
                (isPublic ?
                    R.string.GroupPublicSettingsJoinRequestInfo2:
                    R.string.GroupPrivateSettingsJoinRequestInfo2)
            ));
        }
    }

    public void setJoinRequest(boolean newJoinRequest) {
        isJoinRequest = newJoinRequest;
        joinRequestCell.setChecked(newJoinRequest);
    }

    public void setJoinToSend(boolean newJoinToSend) {
        isJoinToSend = newJoinToSend;

        joinToSendCell.setChecked(isJoinToSend);
        joinToSendCell.setDivider(isJoinToSend);
        joinRequestCell.setChecked(isJoinRequest);

        if (toggleAnimator != null) {
            toggleAnimator.cancel();
        }
        toggleAnimator = ValueAnimator.ofFloat(toggleValue, isJoinToSend ? 1 : 0);
        toggleAnimator.setDuration(200);
        toggleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        toggleAnimator.addUpdateListener(a -> updateToggleValue(toggleValue = (float) a.getAnimatedValue()));
        toggleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isJoinToSend) {
                    joinRequestCell.setVisibility(View.GONE);
                }
            }
        });
        joinRequestCell.setVisibility(View.VISIBLE);
        toggleAnimator.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int y = 0;
        if (joinToSendCell.getVisibility() == View.VISIBLE) {
            joinHeaderCell.layout(0, y, r - l, y += joinHeaderCell.getMeasuredHeight());
            joinToSendCell.layout(0, y, r - l, y += joinToSendCell.getMeasuredHeight());
        }
        joinRequestCell.layout(0, y, r - l, y += joinRequestCell.getMeasuredHeight());
        joinToSendInfoCell.layout(0, y, r - l, y + joinToSendInfoCell.getMeasuredHeight());
        joinRequestInfoCell.layout(0, y, r - l, y + joinRequestInfoCell.getMeasuredHeight());
    }

    private final int MAXSPEC = MeasureSpec.makeMeasureSpec(999999, MeasureSpec.AT_MOST);

    private int calcHeight() {
        return (int) (
            (joinToSendCell.getVisibility() == View.VISIBLE ?
                joinHeaderCell.getMeasuredHeight() + joinToSendCell.getMeasuredHeight() + joinRequestCell.getMeasuredHeight() * toggleValue :
                joinRequestCell.getMeasuredHeight()
            ) +
            AndroidUtilities.lerp(joinToSendInfoCell.getMeasuredHeight(), joinRequestInfoCell.getMeasuredHeight(), toggleValue)
        );
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        joinHeaderCell.measure(widthMeasureSpec, MAXSPEC);
        joinToSendCell.measure(widthMeasureSpec, MAXSPEC);
        joinRequestCell.measure(widthMeasureSpec, MAXSPEC);
        joinToSendInfoCell.measure(widthMeasureSpec, MAXSPEC);
        joinRequestInfoCell.measure(widthMeasureSpec, MAXSPEC);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(calcHeight(), MeasureSpec.EXACTLY));
    }
}