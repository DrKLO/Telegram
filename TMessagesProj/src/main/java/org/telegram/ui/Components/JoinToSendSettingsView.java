package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

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

        joinHeaderCell = new HeaderCell(context, 23);
        joinHeaderCell.setText(LocaleController.getString(R.string.ChannelSettingsJoinTitle));
        joinHeaderCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        addView(joinHeaderCell);

        joinToSendCell = new TextCheckCell(context) {
//            @Override
//            public boolean onTouchEvent(MotionEvent event) {
//                if (event.getAction() == MotionEvent.ACTION_DOWN && !isEnabled()) {
//                    return true;
//                }
//                if (event.getAction() == MotionEvent.ACTION_UP && !isEnabled()) {
//                    new AlertDialog.Builder(context)
//                        .setTitle(LocaleController.getString(R.string.UserRestrictionsCantModify))
//                        .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChannelSettingsJoinToSendRestricted", R.string.ChannelSettingsJoinToSendRestricted, LocaleController.getString(R.string.EditAdminBanUsers))))
//                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
//                        .create()
//                        .show();
//                    return false;
//                }
//                return super.onTouchEvent(event);
//            }
        };
        joinToSendCell.setBackground(Theme.getSelectorDrawable(true));
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

        joinRequestCell = new TextCheckCell(context) {
//            @Override
//            public boolean onTouchEvent(MotionEvent event) {
//                if (event.getAction() == MotionEvent.ACTION_DOWN && !isEnabled()) {
//                    new AlertDialog.Builder(context)
//                        .setTitle(LocaleController.getString(R.string.UserRestrictionsCantModify))
//                        .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChannelSettingsJoinToSendRestricted", R.string.ChannelSettingsJoinToSendRestricted, LocaleController.getString(R.string.EditAdminBanUsers))))
//                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
//                        .create()
//                        .show();
//                    return false;
//                }
//                return super.onTouchEvent(event);
//            }
        };
        joinRequestCell.setBackground(Theme.getSelectorDrawable(true));
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

        joinToSendInfoCell = new TextInfoPrivacyCell(context);
        joinToSendInfoCell.setText(LocaleController.getString(R.string.ChannelSettingsJoinToSendInfo));
        addView(joinToSendInfoCell);

        joinRequestInfoCell = new TextInfoPrivacyCell(context);
        joinRequestInfoCell.setText(LocaleController.getString(R.string.ChannelSettingsJoinRequestInfo));
        addView(joinRequestInfoCell);

        toggleValue = isJoinToSend ? 1f : 0f;
        joinRequestCell.setVisibility(isJoinToSend ? View.VISIBLE : View.GONE);
        updateToggleValue(toggleValue);
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
        joinToSendCell.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            isJoinToSend = true;
            joinRequestCell.setVisibility(View.VISIBLE);
            updateToggleValue(1);
        }
        requestLayout();
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
        joinHeaderCell.layout(0, y, r - l, y += joinHeaderCell.getMeasuredHeight());
        if (joinToSendCell.getVisibility() == View.VISIBLE) {
            joinToSendCell.layout(0, y, r - l, y += joinToSendCell.getMeasuredHeight());
        }
        joinRequestCell.layout(0, y, r - l, y += joinRequestCell.getMeasuredHeight());
        joinToSendInfoCell.layout(0, y, r - l, y + joinToSendInfoCell.getMeasuredHeight());
        joinRequestInfoCell.layout(0, y, r - l, y + joinRequestInfoCell.getMeasuredHeight());
    }

    private final int MAXSPEC = MeasureSpec.makeMeasureSpec(999999, MeasureSpec.AT_MOST);

    private int calcHeight() {
        return (int) (
            joinHeaderCell.getMeasuredHeight() +
            (joinToSendCell.getVisibility() == View.VISIBLE ?
                joinToSendCell.getMeasuredHeight() + joinRequestCell.getMeasuredHeight() * toggleValue :
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