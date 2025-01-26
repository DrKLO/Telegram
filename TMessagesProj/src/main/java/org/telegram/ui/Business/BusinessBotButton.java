package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ClickableAnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

public class BusinessBotButton extends FrameLayout {

    private final int currentAccount;

    private final AvatarDrawable avatarDrawable;
    private final BackupImageView avatarView;
    private final AnimatedTextView titleView;
    private final AnimatedTextView subtitleView;
    private final ClickableAnimatedTextView pauseButton;
    private final ImageView menuView;
    private boolean paused;

    private long dialogId;
    private long botId;
    private int flags;
    private String manageUrl;

    public BusinessBotButton(Context context, ChatActivity chatActivity, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = chatActivity.getCurrentAccount();
        paused = false;

        avatarView = new BackupImageView(context);
        TLRPC.User user = chatActivity.getMessagesController().getUser(botId);
        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(user);
        avatarView.setRoundRadius(dp(16));
        avatarView.setForUserOrChat(user, avatarDrawable);
        addView(avatarView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 0, 10, 0));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        titleView = new AnimatedTextView(context);
        titleView.adaptWidth = false;
        titleView.getDrawable().setHacks(true, true, false);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(dp(14));
        titleView.setText(UserObject.getUserName(user));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setEllipsizeByGradient(true);
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 17, 0, 0, 0, 1));

        subtitleView = new AnimatedTextView(context);
        subtitleView.adaptWidth = false;
        subtitleView.getDrawable().setHacks(true, true, false);
        subtitleView.setTextSize(dp(13));
        subtitleView.setText(LocaleController.getString(R.string.BizBotStatusManages));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleView.setEllipsizeByGradient(true);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 17));

        addView(textLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 52, 0, 49, 0));

        pauseButton = new ClickableAnimatedTextView(context);
        pauseButton.getDrawable().setHacks(true, true, true);
        pauseButton.setAnimationProperties(.75f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        pauseButton.setScaleProperty(.6f);
        pauseButton.setTypeface(AndroidUtilities.bold());
        pauseButton.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(dp(14), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), Theme.blendOver(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), Theme.multAlpha(Color.WHITE, .12f))));
        pauseButton.setTextSize(dp(14));
        pauseButton.setGravity(Gravity.RIGHT);
        pauseButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        pauseButton.setPadding(dp(13), 0, dp(13), 0);
        pauseButton.setOnClickListener(v -> {
            paused = !paused;
            pauseButton.setText(LocaleController.getString(paused ? R.string.BizBotStart : R.string.BizBotStop), true);
            subtitleView.cancelAnimation();
            subtitleView.setText(LocaleController.getString(paused ? R.string.BizBotStatusStopped : R.string.BizBotStatusManages), true);

            if (paused) {
                flags |= 1;
            } else {
                flags &=~ 1;
            }
            MessagesController.getNotificationsSettings(currentAccount).edit()
                .putInt("dialog_botflags" + dialogId, flags)
                .apply();

            TL_account.toggleConnectedBotPaused req = new TL_account.toggleConnectedBotPaused();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.paused = paused;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);


        });
        pauseButton.setOnWidthUpdatedListener(() -> {
            float padding = pauseButton.getPaddingLeft() + pauseButton.getDrawable().getCurrentWidth() + pauseButton.getPaddingRight() + dp(12);
            titleView.setRightPadding(padding);
            subtitleView.setRightPadding(padding);
        });
        pauseButton.setText(LocaleController.getString(paused ? R.string.BizBotStart : R.string.BizBotStop));
        addView(pauseButton, LayoutHelper.createFrame(64, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 49, 0));

        menuView = new ImageView(context);
        menuView.setScaleType(ImageView.ScaleType.CENTER);
        menuView.setImageResource(R.drawable.msg_mini_customize);
        menuView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        menuView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        menuView.setOnClickListener(e -> {
            ItemOptions itemOptions = ItemOptions.makeOptions(chatActivity.getLayoutContainer(), resourcesProvider, menuView);
            itemOptions.add(R.drawable.msg_cancel, LocaleController.getString(R.string.BizBotRemove), true, () -> {
                TL_account.disablePeerConnectedBot req = new TL_account.disablePeerConnectedBot();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);

                MessagesController.getNotificationsSettings(currentAccount).edit()
                    .remove("dialog_botid" + dialogId).remove("dialog_boturl" + dialogId).remove("dialog_botflags" + dialogId)
                    .apply();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.peerSettingsDidLoad, dialogId);

                BusinessChatbotController.getInstance(currentAccount).invalidate(false);

            }).makeMultiline(false);
            if (manageUrl != null) {
                itemOptions.add(R.drawable.msg_settings, LocaleController.getString(R.string.BizBotManage), () -> {
                    Browser.openUrl(getContext(), manageUrl);
                });
            }
            itemOptions.translate(dp(10), dp(7));
            itemOptions.setDimAlpha(0);
            itemOptions.show();
        });
        addView(menuView, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 8, 0, 9, 0));
    }

    public void set(
        long dialogId,
        long botId,
        String url,
        int flags
    ) {
        this.dialogId = dialogId;
        this.botId = botId;
        this.manageUrl = url;
        this.flags = flags;
        this.paused = (flags & 1) != 0;

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(botId);
        avatarDrawable.setInfo(user);
        avatarView.setForUserOrChat(user, avatarDrawable);
        titleView.setText(UserObject.getUserName(user));
        subtitleView.setText(LocaleController.getString(paused ? R.string.BizBotStatusStopped : R.string.BizBotStatusManages));
        pauseButton.setText(LocaleController.getString(paused ? R.string.BizBotStart : R.string.BizBotStop));
    }

}
