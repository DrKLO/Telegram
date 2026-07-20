package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class ChatbotSheet extends BottomSheetWithRecyclerListView {

    private final TL_account.TL_connectedBot bot;
    private final TLRPC.User user;

    private final BusinessRecipientsHelper recipientsHelper;
    private final LinearLayout topView;
    private final AvatarDrawable avatarDrawable;
    private final BackupImageView imageView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView linkView;

    private final FrameLayout buttonContainer;
    private final ButtonWithCounterView terminateButton;
    private final ButtonWithCounterView updateButton;

    public ChatbotSheet(Context context, TL_account.TL_connectedBot bot, Runnable onTerminated, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, false, false, false, ActionBarType.SLIDING, resourcesProvider);
        this.bot = bot;
        this.user = MessagesController.getInstance(currentAccount).getUser(bot.bot_id);

        headerMoveTop = dp(36);
        topPadding = 0.15f;

        recipientsHelper = new BusinessRecipientsHelper(context, currentAccount, () -> {
            if (adapter != null) {
                adapter.update(true);
            }
            checkDone(true);
        }, resourcesProvider);
        exclude = bot.recipients.exclude_selected;
        recipientsHelper.setValue(bot.recipients);

        topView = new LinearLayout(context);
        topView.setOrientation(LinearLayout.VERTICAL);

        avatarDrawable = new AvatarDrawable();

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(40));
        avatarDrawable.setInfo(user);
        imageView.setForUserOrChat(user, avatarDrawable);

        topView.addView(imageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(UserObject.getUserName(user));
        topView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 15.66f, 32, 3.66f));
        actionBar.setTitle(UserObject.getUserName(user));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setText(getString(R.string.SessionBot));
        topView.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 3.66f));

        final String username = UserObject.getPublicUsername(user);
        if (!TextUtils.isEmpty(username)) {
            linkView = new TextView(context);
            linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            linkView.setTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
            linkView.setText("@" + username);
            linkView.setGravity(Gravity.CENTER);
            topView.addView(linkView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 0, 32, 18));
        } else {
            linkView = null;
        }

        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundGray));
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(48 + 12 + 12));
        recyclerListView.setSections();
        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter.getItem(position - 1);
            if (item == null) return;
            onItemClick(item, view, position);
        });

        buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(dp(12), dp(6), dp(12), dp(12));
        buttonContainer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
                Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.0f),
                getThemedColor(Theme.key_windowBackgroundGray),
                getThemedColor(Theme.key_windowBackgroundGray)
        }));

        terminateButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        terminateButton.setColor(getThemedColor(Theme.key_color_red));
        terminateButton.setText(getString(R.string.TerminateSession));
        terminateButton.setOnClickListener(v -> {
            if (terminateButton.isLoading()) return;
            terminateButton.setLoading(true);

            final TL_account.updateConnectedBot req = new TL_account.updateConnectedBot();
            req.deleted = true;
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(bot.bot_id);
            req.recipients = new TL_account.TL_inputBusinessBotRecipients();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    BusinessChatbotController.getInstance(currentAccount).invalidate(true);
                    if (onTerminated != null) {
                        onTerminated.run();
                    }
                    dismiss();
                });
            });
        });
        buttonContainer.addView(terminateButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        updateButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        updateButton.setText(getString(R.string.BusinessBotUpdate));
        updateButton.setOnClickListener(v -> {
            if (updateButton.isLoading()) return;
            updateButton.setLoading(true);

            final TL_account.updateConnectedBot req = new TL_account.updateConnectedBot();
            req.bot = MessagesController.getInstance(currentAccount).getInputUser(bot.bot_id);
            req.recipients = recipientsHelper.getBotInputValue();
            final TL_account.TL_businessBotRecipients settings = recipientsHelper.getBotValue();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    BusinessChatbotController.getInstance(currentAccount).invalidate(true);
                    dismiss();

                    bot.recipients = settings;

                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    if (lastFragment != null) {
                        BulletinFactory.of(lastFragment)
                            .createSimpleBulletin(R.raw.contact_check, formatString(R.string.BusinessBotUpdated, UserObject.getUserName(user)))
                            .show();
                    }
                });
            });
        });
        buttonContainer.addView(updateButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        checkDone(false);

        FrameLayout.LayoutParams buttonContainerLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM);
        buttonContainerLayoutParams.leftMargin += backgroundPaddingLeft;
        buttonContainerLayoutParams.rightMargin += backgroundPaddingLeft;
        containerView.addView(buttonContainer, buttonContainerLayoutParams);

        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        recyclerListView.setItemAnimator(itemAnimator);

        if (adapter != null) {
            adapter.update(false);
        }
    }

    @Override
    protected void onActionBarAlpha(float alpha) {
        final SimpleTextView titleView = actionBar.getTitleTextView();
        if (titleView != null) {
            titleView.setAlpha(alpha);
        }
    }

    private UniversalAdapter adapter;

    @Override
    protected CharSequence getTitle() {
        return null;
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private static int ids = 0;
    private static final int RADIO_EXCLUDE = --ids;
    private static final int RADIO_INCLUDE = --ids;

    public boolean exclude;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        adapter.itemsOffset = 1;
        items.add(UItem.asCustomShadow(-5, topView));
        if (bot != null) {
            if (TLObject.hasFlag(bot.flags, TLObject.FLAG_0) || TLObject.hasFlag(bot.flags, TLObject.FLAG_1) || TLObject.hasFlag(bot.flags, TLObject.FLAG_2)) {
                items.add(UItem.asHeader(getString(R.string.SessionBotConnectedFrom)));
                if (TLObject.hasFlag(bot.flags, TLObject.FLAG_0)) {
                    items.add(UItem.asButton(1, getString(R.string.SessionBotDevice), bot.device));
                }
                if (TLObject.hasFlag(bot.flags, TLObject.FLAG_2)) {
                    items.add(UItem.asButton(2, getString(R.string.SessionBotLocation), bot.location));
                }
                if (TLObject.hasFlag(bot.flags, TLObject.FLAG_1)) {
                    items.add(UItem.asButton(3, getString(R.string.SessionBotDate), LocaleController.formatDateTime(bot.date, false)));
                }
                items.add(UItem.asShadow(null));
            }

            adapter.whiteSectionStart();
            items.add(UItem.asHeader(getString(R.string.BusinessBotChats2)));
            items.add(UItem.asRadio(RADIO_EXCLUDE, getString(R.string.BusinessChatsAllPrivateExcept2)).setChecked(exclude));
            items.add(UItem.asRadio(RADIO_INCLUDE, getString(R.string.BusinessChatsOnlySelected2)).setChecked(!exclude));
            adapter.whiteSectionEnd();

            items.add(UItem.asShadow(null));
            if (recipientsHelper != null) {
                recipientsHelper.fillItems(items, adapter, true);
            }
            items.add(UItem.asShadow(getString(R.string.BusinessBotChatsInfo2)));
        }
    }

    private void onItemClick(UItem item, View view, int position) {
        if (recipientsHelper.onClick(item)) return;
        if (item.id == RADIO_EXCLUDE) {
            recipientsHelper.setExclude(exclude = true);

            adapter.update(true);
            checkDone(true);
        } else if (item.id == RADIO_INCLUDE) {
            recipientsHelper.setExclude(exclude = false);
            adapter.update(true);
            checkDone(true);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        if (recipientsHelper != null && recipientsHelper.hasChanges()) return false;
        return super.canDismissWithSwipe();
    }

    @Override
    protected boolean canDismissWithTouchOutside() {
        if (recipientsHelper != null && recipientsHelper.hasChanges()) return false;
        return super.canDismissWithTouchOutside();
    }

    private Boolean hadChanges;
    private void checkDone(boolean animated) {
        final boolean hasChanges = recipientsHelper != null && recipientsHelper.hasChanges();
        if (hadChanges != null && hadChanges == hasChanges) return;
        hadChanges = hasChanges;
        if (!animated) {
            updateButton.setVisibility(hasChanges ? View.VISIBLE : View.GONE);
            updateButton.animate().cancel();
            updateButton.setAlpha(hasChanges ? 1.0f : 0.0f);
            updateButton.setScaleX(hasChanges ? 1.0f : 0.8f);
            updateButton.setScaleY(hasChanges ? 1.0f : 0.8f);
            terminateButton.setVisibility(!hasChanges ? View.VISIBLE : View.GONE);
            terminateButton.animate().cancel();
            terminateButton.setAlpha(!hasChanges ? 1.0f : 0.0f);
            terminateButton.setScaleX(!hasChanges ? 1.0f : 0.8f);
            terminateButton.setScaleY(!hasChanges ? 1.0f : 0.8f);
        } else {
            updateButton.setVisibility(View.VISIBLE);
            updateButton.animate()
                .alpha(hasChanges ? 1.0f : 0.0f)
                .scaleX(hasChanges ? 1.0f : 0.8f)
                .scaleY(hasChanges ? 1.0f : 0.8f)
                .setDuration(320)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> { if (!hasChanges) updateButton.setVisibility(View.GONE); })
                .start();
            terminateButton.setVisibility(View.VISIBLE);
            terminateButton.animate()
                .alpha(!hasChanges ? 1.0f : 0.0f)
                .scaleX(!hasChanges ? 1.0f : 0.8f)
                .scaleY(!hasChanges ? 1.0f : 0.8f)
                .setDuration(320)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction(() -> { if (hasChanges) terminateButton.setVisibility(View.GONE); })
                .start();
        }
    }
}
