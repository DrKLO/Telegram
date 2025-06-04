package org.telegram.ui.Stars;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Arrays;

public class StarsSuggestionSheet extends BottomSheet {

    private final int currentAccount;

    private final LinearLayout layout;
    private final FrameLayout topLayout;
    private final LinearLayout toptopLayout;
    private final StarsReactionsSheet.StarsSlider slider;
    private final TextView titleView;
    private final ImageView closeView;
    private final TextView statusView;
    private final ButtonWithCounterView buttonView;

    public long peer;
    public long lastSelectedPeer;
    private long selectedTime = -1;

    private final MessageObject messageObject;

    private final StarsReactionsSheet.BalanceCloud balanceCloud;

    @Override
    protected void appendOpenAnimator(boolean opening, ArrayList<Animator> animators) {
        animators.add(ObjectAnimator.ofFloat(balanceCloud, View.ALPHA, opening ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(balanceCloud, View.SCALE_X, opening ? 1.0f : 0.6f));
        animators.add(ObjectAnimator.ofFloat(balanceCloud, View.SCALE_Y, opening ? 1.0f : 0.6f));
    }

    @Override
    protected boolean isTouchOutside(float x, float y) {
        if (x >= balanceCloud.getX() && x <= balanceCloud.getX() + balanceCloud.getWidth() && y >= balanceCloud.getY() && y <= balanceCloud.getY() + balanceCloud.getHeight())
            return false;
        return super.isTouchOutside(x, y);
    }

    public StarsSuggestionSheet(
        Context context,
        int currentAccount,
        long dialogId,
        ChatActivity chatActivity,
        boolean sendEnabled,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, false, resourcesProvider);

        this.currentAccount = currentAccount;
        this.messageObject = null;

        balanceCloud = new StarsReactionsSheet.BalanceCloud(context, currentAccount, resourcesProvider);
        balanceCloud.setScaleX(0.6f);
        balanceCloud.setScaleY(0.6f);
        balanceCloud.setAlpha(0.0f);
        container.addView(balanceCloud, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));
        ScaleStateListAnimator.apply(balanceCloud);
        balanceCloud.setOnClickListener(v -> new StarsIntroActivity.StarsOptionsSheet(context, resourcesProvider).show());

        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        peer = StarsController.getInstance(currentAccount).getPaidReactionsDialogId(messageObject);
        lastSelectedPeer = peer == UserObject.ANONYMOUS ? selfId : peer;

        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        topLayout = new FrameLayout(context);
        layout.addView(topLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        slider = new StarsReactionsSheet.StarsSlider(context) {
            @Override
            public void onValueChanged(int value) {
                if (buttonView != null) {
                    buttonView.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.PostSuggestionsOffer, LocaleController.formatNumber(value, ',')), starRef), true);
                }
            }
        };
        int[] steps_arr = new int[] { 1, 50, 100, /*250,*/ 500, 1_000, 2_000, 5_000, 7_500, 10_000 };
        final long max = MessagesController.getInstance(currentAccount).starsPaidReactionAmountMax;
        ArrayList<Integer> steps = new ArrayList<>();
        for (int j : steps_arr) {
            if (j > max) {
                steps.add((int) max);
                break;
            }
            steps.add(j);
            if (j == max) break;
        }
        steps_arr = new int[ steps.size() ];
        for (int i = 0; i < steps.size(); ++i) steps_arr[i] = steps.get(i);
        slider.setSteps(100, steps_arr);
        if (sendEnabled) {
            topLayout.addView(slider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        toptopLayout = new LinearLayout(context);
        toptopLayout.setOrientation(LinearLayout.HORIZONTAL);
        topLayout.addView(toptopLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));
        titleView = new TextView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(ActionBar.getCurrentActionBarHeight(), MeasureSpec.EXACTLY));
            }
        };
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.PostSuggestionsOfferTitle));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        toptopLayout.addView(new Space(context), LayoutHelper.createLinear(48, 48, 0, Gravity.TOP | Gravity.LEFT));
        toptopLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.FILL, 4, 0, 2, 0));

        closeView = new ImageView(context);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setImageResource(R.drawable.ic_close_white);
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(closeView);
        closeView.setOnClickListener(v -> dismiss());

        toptopLayout.addView(closeView, LayoutHelper.createLinear(48, 48, 0, Gravity.TOP | Gravity.RIGHT, 0, 6, 6, 0));

        LinearLayout topLayoutTextLayout = new LinearLayout(context);
        topLayoutTextLayout.setOrientation(LinearLayout.VERTICAL);
        topLayout.addView(topLayoutTextLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, sendEnabled ? 135 + 44 : 45, 0, 0));

        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        statusView = new TextView(context);
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setSingleLine(false);
        statusView.setMaxLines(3);
        statusView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.PostSuggestionsOfferSubtitle, chat == null ? "" : chat.title)));
        if (sendEnabled) {
            topLayoutTextLayout.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 40,  0, 40, 0));
        }

        TextView statusView2 = new TextView(context);
        statusView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        statusView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusView2.setGravity(Gravity.CENTER);
        statusView2.setSingleLine(false);
        statusView2.setMaxLines(3);
        statusView2.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.PostSuggestionsOfferTime, chat == null ? "" : chat.title)));
        if (sendEnabled) {
            topLayoutTextLayout.addView(statusView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 40,  20, 40, 0));
        }

        /**/

        TextView dialogSelectorTitleView = new TextView(context);
        dialogSelectorTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        dialogSelectorTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        dialogSelectorTitleView.setGravity(Gravity.CENTER);
        dialogSelectorTitleView.setText(formatDateTime(selectedTime));
        dialogSelectorTitleView.setEllipsize(TextUtils.TruncateAt.END);

        FrameLayout dialogSelectorInnerLayout = new FrameLayout(context);
        dialogSelectorInnerLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogBackgroundGray, resourcesProvider), Theme.getColor(Theme.key_listSelector, resourcesProvider), 14, 14));
        ImageView dialogSelectorIconView = new ImageView(context);
        dialogSelectorIconView.setScaleType(ImageView.ScaleType.CENTER);
        dialogSelectorIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider), PorterDuff.Mode.SRC_IN));
        dialogSelectorIconView.setImageResource(R.drawable.arrows_select);
        dialogSelectorInnerLayout.addView(dialogSelectorTitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 24, 0));
        dialogSelectorInnerLayout.addView(dialogSelectorIconView, LayoutHelper.createFrame(18, 18, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 4, 0));
        layout.addView(dialogSelectorInnerLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, 0, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 20));
        ScaleStateListAnimator.apply(dialogSelectorInnerLayout);
        dialogSelectorInnerLayout.setOnClickListener(v -> AlertsCreator.createSuggestedMessageDatePickerDialog(context, selectedTime, (notify, scheduleDate) -> {
            if (notify) {
                selectedTime = scheduleDate;
                dialogSelectorTitleView.setText(formatDateTime(selectedTime));
            }
        }, resourcesProvider).show());

        /**/

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        if (sendEnabled) {
            layout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 0, 14, 0));
        }
        buttonView.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.PostSuggestionsOffer, LocaleController.formatNumber(50, ',')), starRef), true);
        buttonView.setOnClickListener(v -> {
            if (messageObject == null || chatActivity == null) {
                return;
            }
            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                AccountFrozenAlert.show(currentAccount);
                return;
            }

            final long totalStars = slider.getValue();
            final StarsController starsController = StarsController.getInstance(currentAccount);

            final Runnable send = () -> {
                StarsController.PendingPaidReactions pending = starsController.sendPaidReaction(messageObject, chatActivity, totalStars, false, true, peer);
                if (pending == null) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    sending = true;
                    pending.apply();
                    AndroidUtilities.runOnUIThread(this::dismiss, 240);
                });
            };

            if (starsController.balanceAvailable() && starsController.getBalance().amount < totalStars) {
                new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, totalStars, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, chat == null ? "" : chat.title, send).show();
            } else {
                send.run();
            }
        });

        LinkSpanDrawable.LinksTextView termsView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        termsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        termsView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        termsView.setText(AndroidUtilities.replaceSingleTag(getString(R.string.StarsReactionTerms), () ->
            Browser.openUrl(context, getString(R.string.StarsReactionTermsLink))));
        termsView.setGravity(Gravity.CENTER);
        termsView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        if (sendEnabled) {
            layout.addView(termsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 14, 14, 14, 12));
        }

        setCustomView(layout);
        slider.setValue(50);
    }

    private final ColoredImageSpan[] starRef = new ColoredImageSpan[1];

    private boolean sending;
    private boolean checkedVisiblity = false;
    private void checkVisibility() {
        if (checkedVisiblity) return;
        checkedVisiblity = true;
        if (messageObject == null) return;
        final Long currentPeer = messageObject.getMyPaidReactionPeer();
        if (currentPeer == null || currentPeer != peer) {
            messageObject.setMyPaidReactionDialogId(peer);

            final StarsController.MessageId key = StarsController.MessageId.from(messageObject);
            TLRPC.TL_messages_togglePaidReactionPrivacy req = new TLRPC.TL_messages_togglePaidReactionPrivacy();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(key.did);
            req.msg_id = key.mid;
            if (peer == 0) {
                req.privacy = new TL_stars.paidReactionPrivacyDefault();
            } else if (peer == UserObject.ANONYMOUS) {
                req.privacy = new TL_stars.paidReactionPrivacyAnonymous();
            } else {
                req.privacy = new TL_stars.paidReactionPrivacyPeer();
                req.privacy.peer = MessagesController.getInstance(currentAccount).getInputPeer(peer);
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.starReactionAnonymousUpdate, key.did, key.mid, peer);

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.TL_boolTrue) {
                    MessagesStorage.getInstance(currentAccount).putMessages(new ArrayList<>(Arrays.asList(messageObject.messageOwner)), true, true, true, 0, 0, 0);
                }
            });
        }
    }

    @Override
    public void dismiss() {
        if (!sending) checkVisibility();
        super.dismiss();
    }

    public void setValue(int value) {
        slider.setValue(value);
        if (buttonView != null) {
            buttonView.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.PostSuggestionsOffer, LocaleController.formatNumber(value, ',')), starRef), true);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        if (slider.isTracking()) return false;
        return super.canDismissWithSwipe();
    }

    private static String formatDateTime(long time) {
        if (time <= 0) {
            return LocaleController.getString(R.string.PostSuggestionsAnytime);
        } else {
            final String s = LocaleController.formatDateTime(time, true);
            if (!s.isEmpty())
                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
            return s;
        }
    }
}
