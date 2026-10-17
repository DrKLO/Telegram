package org.telegram.features;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Stars.PaidReactionUndo;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsPrivacy;
import org.telegram.ui.Stars.StarsPrivacyView;
import org.telegram.ui.Stars.StarsChannelPrivacyActivity;
import org.telegram.ui.Stars.StarsPrivacyActivity;
import org.telegram.ui.Stars.StarsPrivacyMockApi;
import org.telegram.ui.Stars.StarsReactionsSheet;

import java.util.ArrayList;

/** Offline fixtures rendered through the production settings, reaction sheet and history rows. */
public final class StarsPrivacyPreview extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private static final long CHANNEL = 7_770_001;
    private MessageObject post;
    private ChatMessageCell messageCell;
    private TextSettingsCell privacyCell;
    private TextSettingsCell channelCell;
    private TextInfoPrivacyCell percentileCell;
    private LinearLayout history;
    private StarsPrivacyView historyPrivacyView;
    private FrameLayout root;
    private PaidReactionUndo pending;
    private Bulletin bulletin;
    private Bulletin.TimerView timer;
    private long pendingStars;
    private long pendingPeer;
    private boolean pendingLive;
    public int completedPayments;
    public long lastPaymentPeer;
    private final Runnable finishPending = this::finishPending;

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.StarsPrivacyTitle));
        actionBar.setSubtitle(LocaleController.getString(R.string.StarsPrivacyDemoInfo));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        root = new FrameLayout(context);
        root.setTag(this);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scroll = new ScrollView(context);
        root.addView(scroll, LayoutHelper.createFrame(-1, -1));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        privacyCell = new TextSettingsCell(context);
        privacyCell.setTag("stars_privacy_settings");
        privacyCell.setBackground(Theme.getSelectorDrawable(true));
        privacyCell.setOnClickListener(v -> presentFragment(new StarsPrivacyActivity(currentAccount)));
        content.addView(privacyCell, LayoutHelper.createLinear(-1, -2));
        channelCell = new TextSettingsCell(context);
        channelCell.setTag("stars_channel_settings");
        channelCell.setBackground(Theme.getSelectorDrawable(true));
        channelCell.setOnClickListener(v -> presentFragment(new StarsChannelPrivacyActivity(currentAccount, -CHANNEL)));
        content.addView(channelCell, LayoutHelper.createLinear(-1, -2));
        TextInfoPrivacyCell hint = new TextInfoPrivacyCell(context);
        hint.setText(LocaleController.getString(R.string.StarsPrivacyDemoHint));
        content.addView(hint, LayoutHelper.createLinear(-1, -2));

        makeFixtures();
        messageCell = new ChatMessageCell(context, currentAccount);
        messageCell.setTag("stars_privacy_message");
        messageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            @Override public boolean canPerformActions() { return true; }
            @Override public void didPressReaction(ChatMessageCell cell, TLRPC.ReactionCount reaction, boolean longpress, float x, float y) {
                if (!(reaction.reaction instanceof TLRPC.TL_reactionPaid)) return;
                if (longpress) showReactionSheet();
                else sendSampleReaction(1);
            }
        });
        messageCell.setMessageObject(post, null, false, false, true);
        content.addView(messageCell, LayoutHelper.createLinear(-1, -2));

        percentileCell = new TextInfoPrivacyCell(context);
        percentileCell.setTag("stars_donor_percentile");
        content.addView(percentileCell, LayoutHelper.createLinear(-1, -2));
        TextSettingsCell live = new TextSettingsCell(context);
        live.setTag("stars_privacy_live");
        live.setTextAndValue(LocaleController.getString(R.string.StarsPrivacyLiveReaction), LocaleController.getString(R.string.StarsPrivacyPreviewLabel), false);
        live.setBackground(Theme.getSelectorDrawable(true));
        live.setOnClickListener(v -> showLiveReactionSheet());
        content.addView(live, LayoutHelper.createLinear(-1, -2));

        TextSettingsCell buy = new TextSettingsCell(context);
        buy.setTag("stars_purchase_preview");
        buy.setBackground(Theme.getSelectorDrawable(true));
        buy.setTextAndValue(LocaleController.getString(R.string.StarsBuy), LocaleController.getString(R.string.StarsPrivacyPreviewLabel), false);
        buy.setOnClickListener(v -> showPurchaseSheet());
        content.addView(buy, LayoutHelper.createLinear(-1, -2));

        historyPrivacyView = new StarsPrivacyView(context, currentAccount, this);
        content.addView(historyPrivacyView, LayoutHelper.createLinear(-1, -2, 0, 12, 0, 0));
        HeaderCell header = new HeaderCell(context);
        header.setText(LocaleController.getString(R.string.StarsTransactionsAll));
        header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content.addView(header, LayoutHelper.createLinear(-1, -2));
        history = new LinearLayout(context);
        history.setOrientation(LinearLayout.VERTICAL);
        history.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content.addView(history, LayoutHelper.createLinear(-1, -2));
        addTransaction("FL-20261017-0001", -25, 1792227600);
        addTransaction("FL-20261017-0002", -10, 1792225800);
        fragmentView = root;
        refresh();
        return root;
    }

    public StarsIntroActivity.StarsOptionsSheet showPurchaseSheet() {
        ArrayList<TL_stars.TL_starsTopupOption> options = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TL_stars.TL_starsTopupOption option = new TL_stars.TL_starsTopupOption();
            option.stars = new long[] {50, 100, 250}[i];
            option.amount = new long[] {99, 199, 499}[i];
            option.currency = "EUR";
            options.add(option);
        }
        StarsIntroActivity.StarsOptionsSheet sheet = new StarsIntroActivity.StarsOptionsSheet(getContext(), getResourceProvider()) {
            @Override protected ArrayList<TL_stars.TL_starsTopupOption> getOptions() { return options; }
            @Override protected CharSequence getPurchaseTerms() { return LocaleController.getString(R.string.StarsPurchasePreviewInfo); }
            @Override public void onItemClick(UItem item, UniversalAdapter adapter) {
                if (item.object instanceof TL_stars.TL_starsTopupOption) {
                    // Preview taps never enter billing or activate an account.
                    BulletinFactory.of(getContainer(), getResourceProvider())
                        .createSimpleBulletin(R.raw.stars_topup, LocaleController.getString(R.string.StarsPurchasePreviewInfo)).show();
                } else {
                    super.onItemClick(item, adapter);
                }
            }
        };
        sheet.show();
        return sheet;
    }

    private void makeFixtures() {
        TLRPC.TL_channel channel = new TLRPC.TL_channel();
        channel.id = CHANNEL;
        channel.title = "Design Notes";
        channel.broadcast = true;
        MessagesController.getInstance(currentAccount).putChat(channel, true);
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 101;
        message.date = 1792227600;
        message.message = "Small details make a difference.\n\nA quieter way to support the channels you love.";
        message.peer_id = new TLRPC.TL_peerChannel();
        message.peer_id.channel_id = CHANNEL;
        message.from_id = message.peer_id;
        message.post = true;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.reactions = new TLRPC.TL_messageReactions();
        TLRPC.TL_reactionCount reaction = new TLRPC.TL_reactionCount();
        reaction.reaction = new TLRPC.TL_reactionPaid();
        reaction.count = 125;
        reaction.chosen = true; // Retain raw server state; private provenance controls presentation.
        message.reactions.results.add(reaction);
        for (int i = 0; i < 3; i++) {
            TLRPC.TL_user user = new TLRPC.TL_user();
            user.id = 8_880_001 + i;
            user.first_name = new String[] {"Alex", "Sam", "Robin"}[i];
            MessagesController.getInstance(currentAccount).putUser(user, true);
            TLRPC.TL_messageReactor reactor = new TLRPC.TL_messageReactor();
            reactor.peer_id = new TLRPC.TL_peerUser();
            reactor.peer_id.user_id = user.id;
            reactor.count = 50 - 10 * i;
            message.reactions.top_reactors.add(reactor);
        }
        post = new MessageObject(currentAccount, message, false, true);
    }

    private void addTransaction(String id, long amount, int date) {
        TL_stars.TL_starsTransaction transaction = new TL_stars.TL_starsTransaction();
        transaction.id = id;
        transaction.date = date;
        transaction.amount = TL_stars.StarsAmount.ofStars(amount);
        transaction.peer = new TL_stars.TL_starsTransactionPeer();
        transaction.peer.peer = post.messageOwner.peer_id;
        transaction.reaction = true;
        transaction.msg_id = post.getId();
        addTransaction(transaction);
    }

    private void addTransaction(TL_stars.StarsTransaction transaction) {
        StarsIntroActivity.StarsTransactionView row = new StarsIntroActivity.StarsTransactionView(getContext(), currentAccount, getResourceProvider());
        row.setTag(transaction.id);
        row.set(transaction, false, true);
        row.setBackground(Theme.getSelectorDrawable(true));
        row.setOnClickListener(v -> StarsIntroActivity.showTransactionSheet(getContext(), false, 0, currentAccount, transaction, getResourceProvider()));
        row.setOnLongClickListener(v -> {
            org.telegram.ui.Stars.StarsHistoryPrivacy.confirm(getContext(), currentAccount, transaction, getResourceProvider(), null);
            return true;
        });
        history.addView(row, LayoutHelper.createLinear(-1, -2));
        if (history.isAttachedToWindow()) {
            row.setAlpha(0f);
            row.animate().alpha(1f).setDuration(220).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
    }

    public StarsReactionsSheet showReactionSheet() {
        StarsReactionsSheet sheet = new StarsReactionsSheet(getContext(), currentAccount, -CHANNEL, null, post,
            post.messageOwner.reactions.top_reactors, true, false, 0, getResourceProvider());
        sheet.setMessageCell(null, post.getId(), messageCell);
        sheet.setOnSend((peer, stars) -> {
            sendSampleReaction(stars, peer);
            return post.getId();
        });
        sheet.setOfflinePreview();
        showDialog(sheet);
        return sheet;
    }

    public StarsReactionsSheet showLiveReactionSheet() {
        StarsReactionsSheet sheet = new StarsReactionsSheet(getContext(), currentAccount, -CHANNEL, null, null,
            post.messageOwner.reactions.top_reactors, true, true, UserObject.ANONYMOUS, getResourceProvider());
        sheet.setOnSend((peer, stars) -> {
            sendSampleLiveReaction(stars);
            sheet.setPendingReactionTarget(bulletin.getLayout());
            return 0;
        });
        sheet.setOfflinePreview();
        showDialog(sheet);
        return sheet;
    }

    public void sendSampleLiveReaction(long amount) {
        cancelPending();
        pendingLive = true;
        sendSampleReaction(amount);
    }

    public void sendSampleReaction(long amount) {
        sendSampleReaction(amount, 0);
    }

    private void sendSampleReaction(long amount, long requestedPeer) {
        if (pending == null) {
            pending = new PaidReactionUndo();
            pendingStars = 0;
            pendingPeer = StarsPrivacy.resolvePeer(currentAccount, -CHANNEL, requestedPeer);
            Bulletin.TwoLineAnimatedLottieLayout layout = new Bulletin.TwoLineAnimatedLottieLayout(getContext(), getResourceProvider());
            layout.setAnimation(R.raw.stars_topup);
            layout.titleTextView.setText(LocaleController.getString(pendingLive ? R.string.StarsPrivacyLivePreview : StarsPrivacyMockApi.isChannelPrivate(currentAccount, -CHANNEL) ? R.string.StarsChannelPending : pendingPeer == UserObject.ANONYMOUS ? R.string.StarsPrivacyPending : R.string.StarsPendingTitle));
            Bulletin.UndoButton undo = new Bulletin.UndoButton(getContext(), true, false, getResourceProvider());
            undo.setText(LocaleController.getString(R.string.StarsSentUndo));
            undo.setUndoAction(this::cancelPending);
            undo.setTag("stars_privacy_undo");
            timer = new Bulletin.TimerView(getContext(), getResourceProvider());
            undo.addView(timer, LayoutHelper.createFrame(20, 20, android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            undo.undoTextView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(30), AndroidUtilities.dp(8));
            layout.setButton(undo);
            bulletin = BulletinFactory.of(this).create(layout, -1);
            bulletin.hideAfterBottomSheet = false;
            final Bulletin createdBulletin = bulletin;
            bulletin.setOnHideListener(() -> {
                if (bulletin == createdBulletin) cancelPending();
            });
            bulletin.show(true);
        }
        pendingStars += amount;
        post.messageOwner.reactions.results.get(0).count += amount;
        ((Bulletin.TwoLineAnimatedLottieLayout) bulletin.getLayout()).subtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("StarsSentText", (int) pendingStars)), true);
        pending.restart(SystemClock.elapsedRealtime());
        timer.setTimeLeft(PaidReactionUndo.DURATION_MS);
        AndroidUtilities.cancelRunOnUIThread(finishPending);
        AndroidUtilities.runOnUIThread(finishPending, PaidReactionUndo.DURATION_MS);
        messageCell.forceResetMessageObject();
        messageCell.reactionsLayoutInBubble.animateReaction(ReactionsLayoutInBubble.VisibleReaction.asStar());
    }

    private void finishPending() {
        if (pending == null) return;
        if (!pending.commit(SystemClock.elapsedRealtime())) {
            AndroidUtilities.runOnUIThread(finishPending, pending.remaining(SystemClock.elapsedRealtime()));
            return;
        }
        completedPayments++;
        lastPaymentPeer = StarsPrivacy.resolvePeer(currentAccount, -CHANNEL, pendingPeer);
        if (pendingLive) {
            StarsPrivacyMockApi.Receipt receipt = StarsPrivacyMockApi.sendAnonymousLiveReaction(pendingStars);
            addTransaction(receipt.asTransaction());
        } else if (StarsPrivacyMockApi.isChannelPrivate(currentAccount, -CHANNEL)) {
            addTransaction(StarsPrivacyMockApi.sendChannelReaction(currentAccount, -CHANNEL, post.getId(), pendingStars).asTransaction());
        } else if (lastPaymentPeer == UserObject.ANONYMOUS) {
            StarsPrivacyMockApi.protectPost(currentAccount, -CHANNEL, post.getId());
            addTransaction(StarsPrivacyMockApi.sendAnonymousLiveReaction(pendingStars).asTransaction());
        } else {
            addTransaction(StarsPrivacyMockApi.sendPublicReaction(currentAccount, -CHANNEL, post.getId(), pendingStars));
        }
        pendingLive = false;
        pending = null;
        pendingStars = 0;
        bulletin.hide();
        refresh();
    }

    private void cancelPending() {
        if (pending == null) return;
        pending.cancel();
        pending = null;
        AndroidUtilities.cancelRunOnUIThread(finishPending);
        post.messageOwner.reactions.results.get(0).count -= pendingStars;
        pendingStars = 0;
        pendingLive = false;
        if (bulletin != null) bulletin.hide();
        messageCell.forceResetMessageObject();
    }

    private void refresh() {
        if (privacyCell == null) return;
        privacyCell.setTextAndValue(LocaleController.getString(R.string.StarsPrivacySwitch), LocaleController.getString(StarsPrivacy.isEnabled(currentAccount) ? R.string.StarsPrivacyOn : R.string.StarsPrivacyOff), true);
        channelCell.setTextAndValue(LocaleController.getString(R.string.StarsChannelPrivacy), LocaleController.getString(StarsPrivacyMockApi.isChannelPrivate(currentAccount, -CHANNEL) ? R.string.StarsPrivacyOn : R.string.StarsPrivacyOff), false);
        int bucket = StarsPrivacy.isEnabled(currentAccount) ? 0 : StarsPrivacyMockApi.donorPercentile(currentAccount, -CHANNEL, post.getId());
        boolean revealPercentile = bucket > 0 && percentileCell.getVisibility() != View.VISIBLE;
        percentileCell.animate().cancel();
        percentileCell.setVisibility(bucket == 0 ? View.GONE : View.VISIBLE);
        percentileCell.setAlpha(revealPercentile ? 0f : 1f);
        if (bucket > 0) percentileCell.setText(LocaleController.formatString(R.string.StarsChannelTopPercent, bucket));
        if (revealPercentile) percentileCell.animate().alpha(1f).setDuration(200).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        messageCell.forceResetMessageObject();
    }

    @Override public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starsPrivacyChanged);
        return super.onFragmentCreate();
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (pending != null && pendingPeer != UserObject.ANONYMOUS && StarsPrivacy.isPrivate(currentAccount, -CHANNEL)) cancelPending();
        refresh();
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override public void onPause() { cancelPending(); if (historyPrivacyView != null) historyPrivacyView.hideFeedback(); super.onPause(); }
    @Override public void onFragmentDestroy() { cancelPending(); NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starsPrivacyChanged); super.onFragmentDestroy(); }
}
