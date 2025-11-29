package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.List;

public class AcquiredGiftsSheet extends BottomSheetWithRecyclerListView {
    private final List<TL_stars.TL_StarGiftAuctionAcquiredGift> gifts;
    private final GiftAuctionController.Auction auction;

    public AcquiredGiftsSheet(Context context, Theme.ResourcesProvider resourcesProvider, GiftAuctionController.Auction auction, List<TL_stars.TL_StarGiftAuctionAcquiredGift> gifts) {
        super(context, null, false, false, false, false, ActionBarType.SLIDING, resourcesProvider);
        this.auction = auction;
        this.gifts = gifts;
        topPadding = 0.2f;

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);

        actionBar.setTitle(getTitle());
        fixNavigationBar();

        recyclerListView.setPadding(backgroundPaddingLeft, dp(9), backgroundPaddingLeft, dp(16 + 48));
        recyclerListView.setOnItemClickListener((view, position) -> {});
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        ButtonWithCounterView buttonView = new ButtonWithCounterView(context, resourcesProvider);
        buttonView.setOnClickListener(v -> dismiss());
        buttonView.setText(getString(R.string.OK), false);
        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16);
        lp.leftMargin += backgroundPaddingLeft;
        lp.rightMargin += backgroundPaddingLeft;
        containerView.addView(buttonView, lp);

        adapter.update(false);
    }

    @Override
    protected CharSequence getTitle() {
        if (gifts == null) {
            return null;
        }
        return formatPluralString("Gift2AuctionsAcquiredGifts", gifts.size());
    }

    private UniversalAdapter adapter;

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (gifts == null) {
            return;
        }
        for (TL_stars.TL_StarGiftAuctionAcquiredGift gift : gifts) {
            items.add(AcquiredGiftsCell.Factory.as(gift, auction, (v) -> {
                openProfile(DialogObject.getPeerDialogId(gift.peer));
            }));
        }
        items.add(UItem.asSpace(dp(16)));
    }

    private void openProfile(long did) {
        dismiss();
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment != null) {
            if (UserObject.isService(did)) return;
            Bundle args = new Bundle();
            if (did > 0) {
                args.putLong("user_id", did);
                if (did == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    args.putBoolean("my_profile", true);
                }
            } else {
                args.putLong("chat_id", -did);
            }
            args.putBoolean("open_gifts", true);
            lastFragment.presentFragment(new ProfileActivity(args));
        }
    }

    private static class AcquiredGiftsCell extends FrameLayout {
        private final Theme.ResourcesProvider resourcesProvider;
        private final int currentAccount;

        public AcquiredGiftsCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            setPadding(dp(18), dp(9), dp(18), dp(9));
        }

        private void bind(GiftAuctionController.Auction auction, TL_stars.TL_StarGiftAuctionAcquiredGift gift, View.OnClickListener listener) {
            removeAllViews();

            SpannableStringBuilder ssb = new SpannableStringBuilder("*");
            ssb.setSpan(new AnimatedEmojiSpan(
                auction.giftDocumentId, Theme.chat_actionTextPaint.getFontMetricsInt()),
                0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            ssb.append(' ');
            ssb.append(formatString(R.string.Gift2AuctionsAcquiredRound, gift.round));
            ssb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);


            SpannableStringBuilder ssb1 = new SpannableStringBuilder();
            final CharSequence bid = "⭐️" + formatNumber(gift.bid_amount, ',');
            ssb1.append(StarsIntroActivity.replaceStarsWithPlain(bid, 0.75f));

            String top = formatString(R.string.Gift2AuctionsAcquiredTop, gift.pos);

            TableView tableView = new TableView(getContext(), resourcesProvider);
            tableView.addFullRow(ssb).setFilled(true);
            tableView.addRowUser(getString(R.string.Gift2AuctionsAcquiredRecipient), currentAccount, DialogObject.getPeerDialogId(gift.peer), () -> listener.onClick(this));
            tableView.addRowDateTime(getString(R.string.Gift2AuctionsAcquiredDate), gift.date);
            tableView.addRow(getString(R.string.Gift2AuctionsAcquiredAcceptedBid), ssb1, top, null);

            addView(tableView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        private static class Factory extends UItem.UItemFactory<AcquiredGiftsCell> {
            static { setup(new Factory()); }

            @Override
            public AcquiredGiftsCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                AcquiredGiftsCell cell = new AcquiredGiftsCell(context, resourcesProvider, currentAccount);
                cell.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return cell;
            }

            @Override
            public boolean isClickable() {
                return false;
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((AcquiredGiftsCell) view).bind(
                    (GiftAuctionController.Auction) item.object2,
                    (TL_stars.TL_StarGiftAuctionAcquiredGift) item.object, item.clickCallback);
            }

            public static UItem as(TL_stars.TL_StarGiftAuctionAcquiredGift gift, GiftAuctionController.Auction auction, OnClickListener listener) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = gift;
                item.object2 = auction;
                item.clickCallback = listener;
                return item;
            }
        }
    }
}
