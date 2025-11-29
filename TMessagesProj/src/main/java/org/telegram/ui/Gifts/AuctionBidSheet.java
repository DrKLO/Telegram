package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.AndroidUtilities.shakeView;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_COLOR1;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_COLOR2;
import static org.telegram.ui.Stories.HighlightMessageSheet.getTierOption;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.utils.CountdownTimer;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.BalanceCloud;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class AuctionBidSheet extends BottomSheetWithRecyclerListView implements GiftAuctionController.OnAuctionUpdateListener {

    private final long giftId;
    private final UItem headerItem;

    private final BalanceCloud balanceCloud;
    private final ButtonWithCounterView buttonView;

    private final CountdownTimer timer;
    private final StarsReactionsSheet.StarsSlider slider;
    private final InfoCell minimumBidCell;
    private final InfoCell nextRoundCell;
    private final InfoCell giftsLeftCell;

    private final HeaderCell selfBidderHeader;
    private final BidderCell selfBidderCell;

    private final BidderCell[] topBidderCells = new BidderCell[3];
    private final FrameLayout bulletinContainer;

    private @NonNull GiftAuctionController.Auction auction;

    private final Params params;

    public static class Params {
        public final long dialogId;
        public final boolean hideName;
        public final TLRPC.TL_textWithEntities message;

        public Params(long dialogId, boolean hideName, TLRPC.TL_textWithEntities message) {
            this.dialogId = dialogId;
            this.hideName = hideName;
            this.message = message;
        }
    }

    public AuctionBidSheet(Context context, Theme.ResourcesProvider resourcesProvider, @Nullable Params params, GiftAuctionController.Auction auction) {
        super(context, null, false, false, false, false, ActionBarType.SLIDING, resourcesProvider);
        this.auction = auction;
        this.params = params;
        this.giftId = auction.giftId;
        centerTitle = true;
        topPadding = 0.2f;

        auction = GiftAuctionController.getInstance(currentAccount).subscribeToGiftAuction(giftId, this);
        timer = new CountdownTimer(this::updateCountdownCell);

        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);

        fixNavigationBar();
        AuctionJoinSheet.initActionBar(actionBar, context, resourcesProvider, currentAccount, auction.gift);

        TLRPC.User userSelf = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        linearLayout.setClickable(true);
        headerItem = UItem.asCustom(-1, linearLayout);


        slider = new StarsReactionsSheet.StarsSlider(context, resourcesProvider) {
            @Override
            public void onValueChanged(int value) {
                super.onValueChanged(value);
                onSliderValueChanged(value);
            }

            @Override
            public void setValue(int value) {
                super.setValue(value);
                onSliderValueChanged(value);
            }

            @Override
            protected boolean onTapCustom(float x, float y) {
                if (getProgress() > 0.99 || x > getMeasuredWidth() * 0.9f) {
                    showCustomPlaceABid();
                    return true;
                }

                return false;
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if ((event.getAction() == MotionEvent.ACTION_DOWN) && (event.getY() > (getMeasuredHeight() - dp(48)))) {
                    return false;
                }

                return super.dispatchTouchEvent(event);
            }
        };
        slider.drawPlus = true;

        setSliderValues();

        linearLayout.addView(slider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 0, -40, 0, -48));


        LinearLayout horizontalLayout = new LinearLayout(context);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);

        minimumBidCell = new InfoCell(context, resourcesProvider);
        minimumBidCell.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), getThemedColor(Theme.key_windowBackgroundGray), ColorUtils.compositeColors(getThemedColor(Theme.key_listSelector), getThemedColor(Theme.key_windowBackgroundGray))));
        minimumBidCell.setOnClickListener(v -> {
            slider.setValueAnimated((int) this.auction.getMinimumBid());
        });
        minimumBidCell.titleView.setText(getString(R.string.Gift2AuctionBidInfoMinimumBid));
        nextRoundCell = new InfoCell(context, resourcesProvider);
        nextRoundCell.setBackground(Theme.createRoundRectDrawable(dp(12), getThemedColor(Theme.key_windowBackgroundGray)));
        nextRoundCell.titleView.setText(getString(R.string.Gift2AuctionBidInfoUntilNextRound));
        giftsLeftCell = new InfoCell(context, resourcesProvider);
        giftsLeftCell.setBackground(Theme.createRoundRectDrawable(dp(12), getThemedColor(Theme.key_windowBackgroundGray)));
        giftsLeftCell.titleView.setText(getString(R.string.Gift2AuctionBidInfoLeft));

        horizontalLayout.addView(minimumBidCell, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        horizontalLayout.addView(new View(context), LayoutHelper.createLinear(10, LayoutHelper.MATCH_PARENT, 0f));
        horizontalLayout.addView(nextRoundCell, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
        horizontalLayout.addView(new View(context), LayoutHelper.createLinear(10, LayoutHelper.MATCH_PARENT, 0f));
        horizontalLayout.addView(giftsLeftCell, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

        linearLayout.addView(horizontalLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 16, 0, 16, 15));

        if (auction.auctionUserState.acquired_count > 0) {
            boolean[] pending = new boolean[1];
            LinkSpanDrawable.LinksTextView itemsBought = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);

            itemsBought.setGravity(Gravity.CENTER);
            itemsBought.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            itemsBought.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
            itemsBought.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
            itemsBought.setOnClickListener((v) -> {
                if (pending[0]) {
                    return;
                }
                pending[0] = true;
                GiftAuctionController.getInstance(currentAccount).getOrRequestAcquiredGifts(giftId, gifts -> {
                    pending[0] = false;
                    new AcquiredGiftsSheet(getContext(), resourcesProvider, this.auction, gifts).show();
                });
            });

            SpannableStringBuilder emoji = new SpannableStringBuilder("*");
            emoji.setSpan(
                new AnimatedEmojiSpan(auction.giftDocumentId, itemsBought.getPaint().getFontMetricsInt()),
                0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            itemsBought.setText(TextUtils.concat(AndroidUtilities.replaceArrows(
                LocaleController.formatPluralSpannable("Gift2AuctionsItemsBought", auction.auctionUserState.acquired_count, emoji),
                true, dp(8f / 3f), dp(1))));

            ScaleStateListAnimator.apply(itemsBought, 0.02f, 1.5f);
            linearLayout.addView(itemsBought, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 4, 16, 4));
        }

        selfBidderHeader = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 0, 0, false, true, resourcesProvider);
        linearLayout.addView(selfBidderHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 0));

        selfBidderCell = new BidderCell(context, resourcesProvider);
        selfBidderCell.placeTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        selfBidderCell.setUser(userSelf, false);
        linearLayout.addView(selfBidderCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, -7));

        HeaderCell topBiddersHeader = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, 0, false, resourcesProvider);
        topBiddersHeader.setText(getString(R.string.Gift2AuctionTop3Winners));
        linearLayout.addView(topBiddersHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        for (int a = 0; a < topBidderCells.length; a++) {
            topBidderCells[a] = new BidderCell(context, resourcesProvider);
            topBidderCells[a].setPlace(a + 1, true, false);
            topBidderCells[a].setBackground(Theme.getSelectorDrawable(false));
            topBidderCells[a].drawDivider = a < 2;
            topBidderCells[a].setOnClickListener(v -> {});
            linearLayout.addView(topBidderCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }



        buttonView = new ButtonWithCounterView(context, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (!isEnabled()) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }
        };

        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16);
        lp.leftMargin += backgroundPaddingLeft;
        lp.rightMargin += backgroundPaddingLeft;
        containerView.addView(buttonView, lp);

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(16 + 48));
        recyclerListView.setOnItemClickListener((view, position) -> {

        });


        if (auction.auctionUserState.bid_amount > 0) {
            slider.setValue((int) auction.auctionUserState.bid_amount);
        } else {
            slider.setValue((int) auction.getMinimumBid());
        }

        updateTable(false);


        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        balanceCloud = new BalanceCloud(context, currentAccount, resourcesProvider);
        balanceCloud.setScaleX(0.6f);
        balanceCloud.setScaleY(0.6f);
        balanceCloud.setAlpha(0.0f);
        balanceCloud.setEnabled(false);
        balanceCloud.setClickable(false);
        container.addView(balanceCloud, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));
        ScaleStateListAnimator.apply(balanceCloud);
        balanceCloud.setOnClickListener(v -> new StarsIntroActivity.StarsOptionsSheet(context, resourcesProvider).show());

        bulletinContainer = new FrameLayout(context);
        container.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.TOP));

        adapter.update(false);
    }

    private void onSliderValueChanged(int value) {
        slider.setColor(
            getTierOption(currentAccount, value, TIER_COLOR1),
            getTierOption(currentAccount, value, TIER_COLOR2), true);

        updateSelfBidderCell(isOpenAnimationEnd);
        updateSelfBidderHeader(isOpenAnimationEnd);
        updateButtonText(isOpenAnimationEnd);
        checkSliderSubText();
    }

    private void checkSliderSubText() {
        final int sliderValue = slider.getValue();
        if (slider.getProgress() > 0.99f) {
            slider.setCounterSubText(getString(R.string.Gift2AuctionTapToBidMore), true);
        } else if (sliderValue == auction.auctionUserState.bid_amount) {
            slider.setCounterSubText(getString(R.string.Gift2AuctionYourBid), true);
        } else {
            slider.setCounterSubText(null, true);
        }
    }

    private void setSliderValues() {
        int minimumBid, maximumBid;

        long minBid = auction.getMinimumBid();
        long myBid = auction.getCurrentMyBid();
        long topBid = auction.getCurrentTopBid();

        /*if (myBid <= 0) {
            minimumBid = (int) minBid * 3 / 4;
        } else {
            minimumBid = (int) Math.min(minBid, myBid) * 3 / 4;
        }
        */
        minimumBid = 50; // Math.min(5000, minimumBid);
        maximumBid = topBid > 100_000 ? (((int) topBid * 3 / 2000) * 1000) : (topBid > 30000 ? 100000 : 50000);

        int[] steps_arr = new int[] { 50, 100, 500, 1_000, 2_000, 5_000, 7_500, 10_000, 25_000, 50_000, 100_000, 500_000, 1_000_000, 5_000_000, 10_000_000 };
        ArrayList<Integer> steps = new ArrayList<>();
        boolean wasSkipped = false;
        for (int i = 0; i < steps_arr.length; ++i) {
            if (steps_arr[i] < minimumBid) {
                wasSkipped = true;
                continue;
            }

            if (steps_arr[i] == minimumBid) {
                wasSkipped = false;
            }

            if (steps_arr[i] > maximumBid) {
                steps.add((int) maximumBid);
                break;
            }
            steps.add(steps_arr[i]);
            if (steps_arr[i] == maximumBid) break;
        }
        if (wasSkipped) {
            steps.add(0, minimumBid);
        }

        if (steps.size() < 2) {
            steps.clear();
            steps.add(1);
            steps.add(10000);
        }

        steps_arr = new int[ steps.size() ];
        for (int i = 0; i < steps.size(); ++i) steps_arr[i] = steps.get(i);
        slider.setSteps(100, steps_arr);
    }

    private @Nullable Runnable closeParentSheet;
    private long lastRecipientDialogId;
    private long lastAcquiredCount;
    private boolean isFirstCheck = true;

    private void checkAuctionParams() {
        final long dialogId = DialogObject.getPeerDialogId(auction.auctionUserState.peer);
        final long acquiredCount = auction.auctionUserState.acquired_count;

        if (lastAcquiredCount < acquiredCount && !isFirstCheck) {
            final BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment != null && lastRecipientDialogId != 0) {
                final ChatActivity chatActivity = ChatActivity.of(lastRecipientDialogId);
                chatActivity.whenFullyVisible(chatActivity::startFireworks);
                fragment.presentFragment(chatActivity);
                if (closeParentSheet != null) {
                    closeParentSheet.run();
                }
                dismiss();
            }
        }

        if (dialogId != 0) {
            lastRecipientDialogId = dialogId;
        }

        lastAcquiredCount = acquiredCount;
        isFirstCheck = false;
    }

    public void setCloseParentSheet(@Nullable Runnable closeParentSheet) {
        this.closeParentSheet = closeParentSheet;
    }

    private final ColoredImageSpan[] refS = new ColoredImageSpan[1];
    private AnimatedEmojiSpan animatedEmojiSpan;
    private void updateTable(boolean animated) {
        minimumBidCell.infoView.setText(StarsIntroActivity.replaceStarsWithPlain(
            "⭐️" + LocaleController.formatNumberWithMillion((int) auction.getMinimumBid(), ','), 0.78f, refS), animated);

        if (auction.auctionStateActive != null) {
            final int timeLeft = Math.max(0, auction.auctionStateActive.next_round_at - (ConnectionsManager.getInstance(currentAccount).getCurrentTime()));
            timer.start(timeLeft);
            updateCountdownCell(timeLeft, animated);

            if (animatedEmojiSpan == null && auction.gift.sticker != null) {
                animatedEmojiSpan = new AnimatedEmojiSpan(auction.gift.sticker.id, giftsLeftCell.infoView.getPaint().getFontMetricsInt());
            }

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (animatedEmojiSpan != null) {
                ssb.append("* ");
                ssb.setSpan(animatedEmojiSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append(formatNumber(auction.auctionStateActive.gifts_left, ','));

            giftsLeftCell.infoView.setText(ssb, animated);

            final int topBidders = Math.min(topBidderCells.length, auction.auctionStateActive.top_bidders.size());
            if (topBidders > 0) {
                for (int a = 0; a < topBidders; a++) {
                    final int place = a + 1;
                    final long userId = auction.auctionStateActive.top_bidders.get(a);
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    if (user != null) {
                        topBidderCells[a].setUser(user, animated);
                    }
                    topBidderCells[a].setBid(auction.approximateBidAmountFromPlace(place), animated);
                    topBidderCells[a].setOnClickListener(v -> openProfile(userId));
                }
            }
        }

        slider.setStarsTop(auction.approximateBidAmountFromPlace(auction.gift.gifts_per_round) + 1);
        slider.setTopText(formatPluralString("StarsReactionTopX", auction.gift.gifts_per_round));

        updateSelfBidderCell(animated);
        updateSelfBidderHeader(animated);
        updateButtonText(animated);
        checkSliderSubText();
        checkAuctionParams();
    }

    private void openProfile(long did) {
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
        if (closeParentSheet != null) {
            closeParentSheet.run();
        }
        dismiss();
    }

    private final BoolAnimator winningColor = new BoolAnimator(0, this::onColorFactorChanged, CubicBezierInterpolator.EASE_OUT_QUINT, 380);
    private final BoolAnimator outbidColor = new BoolAnimator(0, this::onColorFactorChanged, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private void onColorFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
        final int color = ColorUtils.blendARGB(ColorUtils.blendARGB(
            getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader),
            getThemedColor(Theme.key_text_RedBold),
            outbidColor.getFloatValue()
        ), getThemedColor(Theme.key_color_green), winningColor.getFloatValue());
        selfBidderHeader.setTextColor(color);
        selfBidderCell.placeTextView.setTextColor(color);
    }

    private void updateSelfBidderHeader(boolean animated) {
        boolean winning = false, outbid = false;
        GiftAuctionController.Auction.BidStatus bidStatus = auction.getBidStatus();
        if (slider.getValue() > auction.auctionUserState.bid_amount) {
            selfBidderHeader.setText(getString(R.string.Gift2AuctionBidStatusFuture), animated);
        } else if (bidStatus == GiftAuctionController.Auction.BidStatus.OUTBID) {
            selfBidderHeader.setText(getString(R.string.Gift2AuctionBidStatusOutbid), animated);
            outbid = true;
        } else if (bidStatus == GiftAuctionController.Auction.BidStatus.RETURNED) {
            selfBidderHeader.setText(getString(R.string.Gift2AuctionBidStatusOutbid), animated);
            outbid = true;
        } else if (bidStatus == GiftAuctionController.Auction.BidStatus.WINNING) {
            selfBidderHeader.setText(getString(R.string.Gift2AuctionBidStatusWinning), animated);
            winning = true;
        } else {
            selfBidderHeader.setText(getString(R.string.Gift2AuctionBidStatusFuture), animated);
        }

        winningColor.setValue(winning, animated);
        outbidColor.setValue(outbid, animated);
    }

    private void updateSelfBidderCell(boolean animated) {
        final long amount = slider.getValue();
        final int myPlace = auction.getApproximatedMyPlace();
        final int place = auction.approximatePlaceFromStars(amount);
        selfBidderCell.setBid(Math.max(amount, auction.getCurrentMyBid()), false);
        selfBidderCell.setPlace(myPlace > 0 ? Math.min(myPlace, place) : place, false, animated);
    }

    private void updateCountdownCell(long value) {
        updateCountdownCell(value, isOpenAnimationEnd);
    }

    private void updateCountdownCell(long value, boolean animated) {
        nextRoundCell.infoView.setText(formatDuration(value), animated);
    }

    @Override
    public void onDismissAnimationStart() {
        super.onDismissAnimationStart();
        isOpenAnimationEnd = false;
        checkBalanceCloudVisibility();
        Bulletin.removeDelegate(container);
    }

    private final ColoredImageSpan[] spanRefStars = new ColoredImageSpan[1];
    private void updateButtonText(boolean animated) {
        final int myBid = slider.getValue();
        if (myBid == auction.getCurrentMyBid()) {
            buttonView.setText(getString(R.string.OK), animated);
            buttonView.setOnClickListener(v -> dismiss());
        } else {
            buttonView.setText(StarsIntroActivity.replaceStars(
                formatString(R.string.Gift2AuctionPlaceBid, formatNumber(myBid, ',')),
                spanRefStars
            ), animated);
            buttonView.setOnClickListener(v -> {
                final int myValue = slider.getValue();
                final int minimumBid = (int) auction.getMinimumBid();
                if (myValue < minimumBid) {
                    // slider.setValueAnimated(minimumBid);
                    shakeView(buttonView);
                    BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(
                        R.raw.info, replaceTags(formatPluralString("Gift2AuctionMinimumBidIncreased", minimumBid))
                    ).show();
                } else {
                    sendBid(myValue);
                }
            });
        }
    }

    @Override
    public void onUpdate(GiftAuctionController.Auction auction) {
        this.auction = auction;
        updateTable(isOpenAnimationEnd);
    }


    private boolean isOpenAnimationEnd;

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        isOpenAnimationEnd = true;
        checkBalanceCloudVisibility();
        Bulletin.addDelegate(container, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return dp(48 + 16);
            }
        });
    }

    @Override
    public void dismiss() {
        GiftAuctionController.getInstance(currentAccount).unsubscribeFromGiftAuction(giftId, this);
        timer.stop();
        super.dismiss();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.Gift2AuctionPlaceABidTitle);
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
        items.add(UItem.asSpace(dp(16)));
    }

    public static String formatDuration(long diff) {
        if (diff >= 3600) {
            return AndroidUtilities.formatFullDuration((int) diff);
        } else {
            return AndroidUtilities.formatDurationNoHours((int) diff, true);
        }
    }

    private boolean balanceCloudVisible;

    @Override
    protected void onContainerTranslationYChanged(float translationY) {
        super.onContainerTranslationYChanged(translationY);
        checkBalanceCloudVisibility();
    }

    private void checkBalanceCloudVisibility() {
        final boolean balanceCloudVisible = isOpenAnimationEnd && !isDismissed();
        if (this.balanceCloudVisible != balanceCloudVisible)  {
            this.balanceCloudVisible = balanceCloudVisible;
            if (balanceCloud != null) {
                balanceCloud.setEnabled(balanceCloudVisible);
                balanceCloud.setClickable(balanceCloudVisible);
                balanceCloud.animate()
                        .scaleX(balanceCloudVisible ? 1f : 0.6f)
                        .scaleY(balanceCloudVisible ? 1f : 0.6f)
                        .alpha(balanceCloudVisible ? 1f : 0f)
                        .setDuration(180L)
                        .start();
            }
        }
    }


    private boolean bidIsPending;

    private void sendBid(int amount) {
        if (bidIsPending) {
            return;
        }

        final long currentBid = auction.auctionUserState.bid_amount;
        final long starsForSend = currentBid > 0 ? (amount - currentBid) : (amount);

        if (StarsController.getInstance(currentAccount).balanceAvailable()) {
            final long totalStars = StarsController.getInstance(currentAccount).getBalance(false);
            if (totalStars < starsForSend) {
                new StarsIntroActivity.StarsNeededSheet(getContext(),
                    resourcesProvider, starsForSend, StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_BUY_RESALE, null, null, 0).show();
                return;
            }
        }

        bidIsPending = true;
        buttonView.setLoading(true);

        GiftAuctionController.getInstance(currentAccount).sendBid(giftId, params, amount, (res, err) -> {
            buttonView.setLoading(false);
            bidIsPending = false;
            if (res != null) {
                showBidSuccessBulletin(currentBid > 0);
                StarsController.getInstance(currentAccount).getBalance(false, null, true);
            }
            if (err != null) {
                updateBulletinContainerPosition();
                BulletinFactory.of(bulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.error, formatString(R.string.UnknownErrorCode, err)).show();
            }
        });
    }

    private void showBidSuccessBulletin(boolean increased) {
        final Bulletin.TwoLineLayout layout = new Bulletin.TwoLineLayout(getContext(), resourcesProvider);
        layout.imageView.setImageResource(R.drawable.filled_gift_sell_24);
        layout.titleTextView.setText(getString(increased ? R.string.Gift2AuctionsBidHasBeenIncreased : R.string.Gift2AuctionsBidHasBeenPlaced));
        layout.titleTextView.setSingleLine(true);
        layout.titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        layout.titleTextView.setMaxLines(1);
        layout.titleTextView.setTypeface(AndroidUtilities.bold());
        layout.subtitleTextView.setText(formatString(R.string.Gift2AuctionPlaceACustomBidHint, auction.gift.gifts_per_round));
        layout.subtitleTextView.setSingleLine(false);
        layout.subtitleTextView.setMaxLines(5);

        updateBulletinContainerPosition();
        BulletinFactory.of(bulletinContainer, resourcesProvider)
            .create(layout, Bulletin.DURATION_LONG)
            .show();
    }

    private void updateBulletinContainerPosition() {
        if (shadowDrawable == null || containerView == null || bulletinContainer == null) {
            return;
        }

        bulletinContainer.setTranslationY(Math.max(0, shadowDrawable.getBounds().top + containerView.getY() - bulletinContainer.getMeasuredHeight() + dp(10)));
    }


    private void showCustomPlaceABid() {
        final Context context = getContext();
        final Activity activity = AndroidUtilities.findActivity(context);
        final BaseFragment fragment = LaunchActivity.getLastFragment();

        final View currentFocus = activity != null ? activity.getCurrentFocus() : null;
        final AlertDialog[] dialog = new AlertDialog[1];
        final View[] buttonPositive = new View[1];

        final AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString(R.string.Gift2AuctionPlaceACustomBid));
        builder.setMessage(formatString(R.string.Gift2AuctionPlaceACustomBidHint, auction.gift.gifts_per_round));

        Drawable drawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();

        EditTextCaption editText = new EditTextCaption(context, resourcesProvider) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                drawable.setBounds(0, dp(8), dp(20), dp(28));
                drawable.draw(canvas);
            }
        };
        //editText.lineYFix = true;

        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourcesProvider));
        editText.setHintText(getString(R.string.Gift2AuctionPlaceACustomBidHint2));
        editText.setFocusable(true);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourcesProvider), Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setBackgroundDrawable(null);
        editText.hintLayoutOffset = dp(24);
        editText.setPadding(dp(24), dp(6), 0, dp(6));

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    final int amount = Integer.parseInt(s.toString());
                    final boolean enabled = amount >= auction.getMinimumBid();
                    buttonPositive[0].animate().alpha(enabled ? 1 : 0.6f).setDuration(180L).start();
                    buttonPositive[0].setEnabled(enabled);
                    buttonPositive[0].setClickable(enabled);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        });

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));
        builder.makeCustomMaxHeight();
        builder.setView(container);
        builder.setWidth(dp(300));

        builder.setPositiveButton(getString(R.string.Gift2AuctionPlaceABid), (dialogInterface, i) -> {
            String text = editText.getText().toString().trim();
            try {
                int amount = Integer.parseInt(text);
                sendBid(amount);
                slider.setValue(amount);
            } catch (Throwable t) {
                AndroidUtilities.shakeView(editText);
                FileLog.e(t);
                return;
            }
            dialogInterface.dismiss();
        });
        builder.setNegativeButton(getString(R.string.Cancel), (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });

        dialog[0] = builder.create();
        if (fragment != null) {
            AndroidUtilities.requestAdjustNothing(activity, fragment.getClassGuid());
        }
        dialog[0].setOnDismissListener(d -> {
            AndroidUtilities.hideKeyboard(editText);
            if (fragment != null) {
                AndroidUtilities.requestAdjustResize(activity, fragment.getClassGuid());
            }
        });
        dialog[0].setOnShowListener(d -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
        dialog[0].show();

        buttonPositive[0] = dialog[0].getButton(DialogInterface.BUTTON_POSITIVE);
        buttonPositive[0].setAlpha(0.6f);

        dialog[0].setDismissDialogByButtons(false);
        editText.setSelection(editText.getText().length());
    }

    private static class InfoCell extends FrameLayout {
        public final AnimatedTextView infoView;
        public final TextView titleView;

        public InfoCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            infoView = new AnimatedTextView(context);
            infoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            infoView.setTextSize(dp(17));
            infoView.setTypeface(AndroidUtilities.bold());
            layout.addView(infoView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 23, Gravity.CENTER_HORIZONTAL));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setSingleLine();
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }
    }

    private static class BidderCell extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {
        private final BackupImageView backupImageView;
        private final AnimatedTextView placeTextView;
        private final AnimatedTextView nameTextView;
        private final AnimatedTextView bidTextView;

        public BidderCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(HORIZONTAL);

            nameTextView = new AnimatedTextView(context);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            nameTextView.setTextSize(dp(15));
            nameTextView.setPadding(dp(12), 0, dp(12), 0);
            nameTextView.setEllipsizeByGradient(true);
            bidTextView = new AnimatedTextView(context);
            bidTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            bidTextView.setTextSize(dp(15));
            backupImageView = new BackupImageView(context);

            placeTextView = new AnimatedTextView(context);
            placeTextView.setTextSize(dp(15));
            placeTextView.setPadding(dp(20), 0, 0, 0);
            placeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            placeTextView.setTypeface(AndroidUtilities.bold());
            placeTextView.setGravity(Gravity.CENTER);

            addView(placeTextView, LayoutHelper.createLinear(66, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL));
            addView(backupImageView, LayoutHelper.createLinear(32, 32, 0f, Gravity.CENTER_VERTICAL));
            addView(nameTextView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));
            addView(bidTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL, 0, 0, 20, 0));
        }

        private final ColoredImageSpan[] ref = new ColoredImageSpan[1];

        public void setUser(TLRPC.User user, boolean animated) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            backupImageView.setForUserOrChat(user, avatarDrawable);
            backupImageView.setRoundRadius(dp(16));
            nameTextView.setText(UserObject.getUserName(user));
        }

        public void setPlace(int place, boolean useEmoji, boolean animated) {
            if (useEmoji && place <= 3) {
                if (place == 1) {
                    placeTextView.setText(Emoji.replaceWithRestrictedEmoji(
                        "\uD83E\uDD47", placeTextView.getPaint().getFontMetricsInt(), null), animated);
                } else if (place == 2) {
                    placeTextView.setText(Emoji.replaceWithRestrictedEmoji(
                        "\uD83E\uDD48", placeTextView.getPaint().getFontMetricsInt(), null), animated);
                } else if (place == 3) {
                    placeTextView.setText(Emoji.replaceWithRestrictedEmoji(
                        "\uD83E\uDD49", placeTextView.getPaint().getFontMetricsInt(), null), animated);
                }
            } else {
                if (place >= 10000) {
                    placeTextView.setTextSize(dp(12));
                } else if (place >= 1000) {
                    placeTextView.setTextSize(dp(14));
                } else {
                    placeTextView.setTextSize(dp(15));
                }
                placeTextView.setText(Integer.toString(place), animated);
            }
        }

        public void setBid(long bid, boolean animated) {
            bidTextView.setText(StarsIntroActivity.replaceStarsWithPlain("⭐️" + LocaleController.formatNumber((int) bid, ','), 0.78f, ref), animated);
        }

        private boolean drawDivider;

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (drawDivider) {
                canvas.drawLine(dp(112), getMeasuredHeight() - 1,
                        getMeasuredWidth() - dp(16), getMeasuredHeight(), Theme.dividerPaint);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(52), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            placeTextView.invalidate();
        }
    }
}
