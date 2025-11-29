package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.formatDurationNoHours;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.LongSparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.CountdownTimer;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.List;

public class ActiveAuctionsSheet extends BottomSheetWithRecyclerListView implements GiftAuctionController.OnActiveAuctionsUpdateListeners {
    private final UItem headerItem;
    private final LongSparseArray<ActiveAuctionCell> activeAuctionCells = new LongSparseArray<>();
    private List<GiftAuctionController.Auction> activeAuctions = new ArrayList<>();

    public ActiveAuctionsSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, false, false, false, false, ActionBarType.SLIDING, resourcesProvider);

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        GiftAuctionController.getInstance(currentAccount).subscribeToActiveAuctionsUpdates(this);

        // timer = new CountdownTimer(this::updateCountdownCell);

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);

        fixNavigationBar();

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        linearLayout.setClickable(true);
        headerItem = UItem.asCustom(-1, linearLayout);


        recyclerListView.setPadding(backgroundPaddingLeft, dp(9), backgroundPaddingLeft, dp(9));
        // recyclerListView.setOnItemClickListener((view, position) -> {});



        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter.update(false);

        List<GiftAuctionController.Auction> auctions = GiftAuctionController.getInstance(currentAccount).getActiveAuctions();
        for (GiftAuctionController.Auction auction: auctions) {
            ActiveAuctionCell cell = new ActiveAuctionCell(context, resourcesProvider, auction);
            cell.buttonView.setOnClickListener(v -> {
                new AuctionBidSheet(context, resourcesProvider, null, auction).show();
                dismiss();
            });
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            activeAuctionCells.put(auction.giftId, cell);
        }

        onActiveAuctionsUpdate(auctions);
    }

    @Override
    public void onActiveAuctionsUpdate(List<GiftAuctionController.Auction> auctions) {
        activeAuctions = new ArrayList<>(auctions);
        actionBar.setTitle(getTitle());

        for (GiftAuctionController.Auction auction : auctions) {
            int nextRoundAt = 0;
            if (auction.auctionStateActive != null) {
                nextRoundAt = auction.auctionStateActive.next_round_at;
            }

            ActiveAuctionCell cell = activeAuctionCells.get(auction.giftId);
            if (cell != null) {
                cell.updateStatus(isOpenAnimationEnd);
                final int remaining = Math.max(0, nextRoundAt - ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                cell.updateButton(remaining, isOpenAnimationEnd);
                cell.timer.start(remaining);
            }
        }
    }



    private boolean isOpenAnimationEnd;

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        isOpenAnimationEnd = true;
    }

    @Override
    public void dismiss() {
        GiftAuctionController.getInstance(currentAccount).unsubscribeFromActiveAuctionsUpdates(this);
        super.dismiss();
    }

    @Override
    protected CharSequence getTitle() {
        if (activeAuctions == null) {
            return null;
        }
        return formatString(R.string.Gift2ActiveAuctionsActiveAuctionsTitle, activeAuctions.size());
    }

    private UniversalAdapter adapter;

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(headerItem);
    }

    private static class ActiveAuctionCell extends FrameLayout {

        private final ButtonWithCounterView buttonView;
        private final AnimatedTextView titleView;
        private final AnimatedTextView messageView;
        private final GiftAuctionController.Auction auction;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final CountdownTimer timer = new CountdownTimer(r -> updateButton(r, true));

        public ActiveAuctionCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, GiftAuctionController.Auction auction) {
            super(context);
            this.auction = auction;

            setPadding(dp(14), dp(9), dp(14), dp(9));

            paint.setShadowLayer(dp(1), 0, 0, 0x20000000);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            buttonView = new ButtonWithCounterView(context, resourcesProvider);
            buttonView.setTextHacks(false, true, true, true);

            RLottieImageView backupImageView = new RLottieImageView(context);
            titleView = new AnimatedTextView(context);
            titleView.setTextSize(dp(14));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            messageView = new AnimatedTextView(context);
            messageView.setTextSize(dp(12));

            if (auction.gift.sticker != null) {
                backupImageView.setAnimation(auction.gift.sticker, 44, 44);
            }

            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 64, 15, 15, 0));
            addView(messageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 17, Gravity.TOP | Gravity.LEFT, 64, 15 + 19, 15, 0));
            addView(backupImageView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.LEFT, 14, 11, 0, 0));
            addView(buttonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.BOTTOM, 15, 0, 15, 15));

            updateStatus(false);
        }

        private final ColoredImageSpan cs = new ColoredImageSpan(R.drawable.filled_gift_sell_24);

        private void updateButton(long remaining, boolean animated) {
            CharSequence text = formatDurationNoHours((int) remaining, false);

            SpannableStringBuilder ssb = new SpannableStringBuilder("*");
            ssb.setSpan(cs, 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append("  ");
            ssb.append(getString(R.string.Gift2ActiveAuctionsActiveRaiseBid));
            ssb.append("  ");
            ssb.append(text);
            // ssb.setSpan(colorSpan, ssb.length() - text.length(), ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            buttonView.setText(ssb, animated);
        }

        private final ColoredImageSpan[] spanRefStars = new ColoredImageSpan[1];

        public void updateStatus(boolean animated) {
            if (auction.auctionStateActive != null) {
                titleView.setText(formatString(R.string.Gift2ActiveAuctionsActiveRound,
                    formatNumber(auction.auctionStateActive.current_round, ','),
                    formatNumber(auction.auctionStateActive.total_rounds, ',')), animated);
            }

            final CharSequence bid = "⭐️" + formatNumber(auction.auctionUserState.bid_amount, ',');
            final GiftAuctionController.Auction.BidStatus bidStatus = auction.getBidStatus();
            if (bidStatus.isOutbid()) {
                messageView.setText(StarsIntroActivity.replaceStarsWithPlain(AndroidUtilities.replaceTags(formatString(
                    R.string.Gift2ActiveAuctionsActiveBidOutbid, bid)), .66f, spanRefStars), animated);
                messageView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            } else {
                messageView.setText(StarsIntroActivity.replaceStarsWithPlain(AndroidUtilities.replaceTags(formatString(
                    R.string.Gift2ActiveAuctionsActiveBidActive, bid, auction.getApproximatedMyPlace()
                )), .66f, spanRefStars), animated);
                messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            timer.stop();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, LayoutHelper.measureSpecExactlyDp(128 + 18));
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            canvas.drawRoundRect(dp(14), dp(9), getMeasuredWidth() - dp(14), getMeasuredHeight() - dp(9), dp(8), dp(8), paint);
            super.dispatchDraw(canvas);
        }
    }
}
