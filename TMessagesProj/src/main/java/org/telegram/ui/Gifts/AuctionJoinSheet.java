package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarGiftSheet.replaceUnderstood;

import android.content.Context;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;

public class AuctionJoinSheet extends BottomSheetWithRecyclerListView implements GiftAuctionController.OnAuctionUpdateListener {
    private final TL_stars.StarGift starGift;
    private final long giftId;
    private final LinearLayout linearLayout;

    private final static ButtonSpan.TextViewButtons[] ref = new ButtonSpan.TextViewButtons[1];

    private final ButtonSpan.TextViewButtons auctionRowStartTimeText;
    private final ButtonSpan.TextViewButtons auctionRowEndTimeText;
    private final ButtonSpan.TextViewButtons auctionRowCurrentRoundText;
    private final ButtonSpan.TextViewButtons auctionRowAveragePriceText;
    private final ButtonSpan.TextViewButtons auctionRowAvailabilityText;
    private final Utilities.Callback2<View, CharSequence> showHint;

    private final TableRow auctionRowCurrentRound;
    private final TableRow auctionRowAveragePrice;
    private final ButtonWithCounterView buttonView;

    private final LinkSpanDrawable.LinksTextView subtitleTextView;
    private final LinkSpanDrawable.LinksTextView itemsBought;

    private GiftAuctionController.Auction auction;

    private final static int share_link = 2;
    private final static int copy_link = 3;
    private final static int more_info = 4;

    private CharSequence emojiGiftText;

    private AuctionJoinSheet(Context context, Theme.ResourcesProvider resourcesProvider, long dialogId, TL_stars.StarGift starGift, Runnable closeParentSheet) {
        super(context, null, false, false, false, false, ActionBarType.FADING, resourcesProvider);
        this.starGift = starGift;
        this.giftId = starGift.id;
        topPadding = 0.2f;

        fixNavigationBar();

        final String title = starGift.title != null ? starGift.title : "Gift";

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        linearLayout.setClickable(true);

        ActionBar actionBar = new ActionBar(context, resourcesProvider);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon, resourcesProvider), false);
        actionBar.setOccupyStatusBar(false);
        initActionBar(actionBar, context, resourcesProvider, currentAccount, starGift);
        linearLayout.addView(actionBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, -56));



        GiftSheet.GiftCell giftCell = new GiftSheet.GiftCell(context, currentAccount, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }
        };
        giftCell.setPriorityAuction();
        giftCell.setStarsGift(starGift, false, false, false, false);
        giftCell.setImageSize(dp(100));
        giftCell.setImageLayer(7);
        giftCell.hidePrice();

        linearLayout.addView(giftCell, LayoutHelper.createLinear(130, 130, Gravity.CENTER, 0, 18, 0, 14));

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 6));

        subtitleTextView = new LinkSpanDrawable.LinksTextView(context);
        subtitleTextView.setGravity(Gravity.CENTER);
        subtitleTextView.setText(TextUtils.concat(
            AndroidUtilities.replaceTags(formatPluralString("Gift2AuctionInfo2", starGift.gifts_per_round, title)), " ",
            AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2AuctionInfoLearnMore), () -> {
                showMoreInfo(context, resourcesProvider, starGift);
            }), true, dp(8f / 3f), dp(1))
        ));
        subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleTextView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        linearLayout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 20));

        TableView tableView = new TableView(context, resourcesProvider);

        tableView.addRow(getString(R.string.Gift2AuctionTableStarted), "", ref);
        auctionRowStartTimeText = ref[0];

        tableView.addRow(getString(R.string.Gift2AuctionTableEnded), "", ref);
        auctionRowEndTimeText = ref[0];

        auctionRowCurrentRound = tableView.addRow(getString(R.string.Gift2AuctionTableCurrentRound), "", ref);
        auctionRowCurrentRoundText = ref[0];

        final FrameLayout tableLayout = new FrameLayout(getContext());
        tableLayout.setClipChildren(false);
        tableLayout.setClipToPadding(false);
        tableLayout.addView(tableView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL));

        final HintView2[] hintView = new HintView2[1];
        showHint = (view, text) -> {
            if (hintView[0] != null) {
                hintView[0].hide();
            }
            text = AndroidUtilities.replaceTags(text);
            float x = view.getX() + ((View) view.getParent()).getX() + ((View) ((View) view.getParent()).getParent()).getX();
            float y = view.getY() + ((View) view.getParent()).getY() + ((View) ((View) view.getParent()).getParent()).getY();
            if (view instanceof ButtonSpan.TextViewButtons) {
                final ButtonSpan.TextViewButtons textView2 = (ButtonSpan.TextViewButtons) view;
                final Layout layout = textView2.getLayout();
                final CharSequence viewText = layout.getText();
                if (viewText instanceof Spanned) {
                    ButtonSpan[] spans = ((Spanned) viewText).getSpans(0, viewText.length(), ButtonSpan.class);
                    if (spans.length > 0 && spans[0] != null) {
                        final int offset = ((Spanned) viewText).getSpanStart(spans[0]);
                        x += layout.getPrimaryHorizontal(offset) + spans[0].getSize() / 2;
                        y += layout.getLineTop(layout.getLineForOffset(offset));
                    }
                }
            }

            final HintView2 thisHintView = hintView[0] = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM);
            thisHintView.setMultilineText(true);
            thisHintView.setInnerPadding(11, 8, 11, 7);
            thisHintView.setRounding(10);
            thisHintView.setText(text);
            thisHintView.setOnHiddenListener(() -> AndroidUtilities.removeFromParent(thisHintView));
            thisHintView.setTranslationY(-dp(100) + y);
            thisHintView.setMaxWidthPx(dp(300));
            thisHintView.setPadding(dp(4), dp(4), dp(4), dp(4));
            thisHintView.setJointPx(0, x - dp(4));
            tableLayout.addView(thisHintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            thisHintView.show();
        };


        auctionRowAveragePrice = tableView.addRow(getString(R.string.GiftValueAveragePrice), "", ref);
        auctionRowAveragePrice.setOnClickListener(v -> showAveragePriceHint());
        auctionRowAveragePriceText = ref[0];

        tableView.addRow(getString(R.string.Gift2AuctionTableCurrentAvailability), "", ref);
        auctionRowAvailabilityText = ref[0];

        linearLayout.addView(tableLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 14, 18));

        boolean pending[] = new boolean[1];
        itemsBought = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
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
                if (auction != null) {
                    new AcquiredGiftsSheet(getContext(), resourcesProvider, auction, gifts).show();
                    dismiss();
                }
            });
        });
        ScaleStateListAnimator.apply(itemsBought, 0.02f, 1.5f);
        linearLayout.addView(itemsBought, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 14, 18));

        if (starGift.sticker != null) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("*");
            spannableStringBuilder.setSpan(
                new AnimatedEmojiSpan(starGift.sticker, itemsBought.getPaint().getFontMetricsInt()),
                0, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            emojiGiftText = spannableStringBuilder;
        } else {
            emojiGiftText = "";
        }

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        buttonView.setOnClickListener(v -> {
            if (auction != null && !auction.isFinished()) {
                new SendGiftSheet(context, currentAccount, auction.gift, dialogId, closeParentSheet, false, false) {
                    @Override
                    protected BulletinFactory getParentBulletinFactory() {
                        return BulletinFactory.of(this.container, this.resourcesProvider);
                    }
                }.show();
            }
            dismiss();
        });

        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16);
        lp.leftMargin += backgroundPaddingLeft;
        lp.rightMargin += backgroundPaddingLeft;
        containerView.addView(buttonView, lp);

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(16 + 48));
        adapter.update(false);

        auction = GiftAuctionController.getInstance(currentAccount).subscribeToGiftAuction(giftId, this);
        updateTable(false);
    }

    private void showAveragePriceHint() {
        if (auction != null && auction.auctionStateFinished != null && auction.gift.title != null) {
            showHint.run(auctionRowAveragePriceText, formatString(R.string.Gift2AveragePriceHint, auction.auctionStateFinished.average_price, auction.gift.title));
        }
    }

    private void updateTable(boolean animated) {
        if (auction != null && auction.auctionStateFinished != null) {
            auctionRowStartTimeText.setText(LocaleController.formatDateTime(auction.auctionStateFinished.start_date, true));
            auctionRowEndTimeText.setText(LocaleController.formatDateTime(auction.auctionStateFinished.end_date, true));

            SpannableStringBuilder ssb = new SpannableStringBuilder(StarsIntroActivity.replaceStarsWithPlain("⭐️ " + LocaleController.formatNumber(auction.auctionStateFinished.average_price, ','), .8f));
            ssb.append(" ").append(ButtonSpan.make("?", this::showAveragePriceHint, resourcesProvider));
            auctionRowAveragePriceText.setText(ssb);
        } else if (auction != null && auction.auctionStateActive != null) {
            auctionRowStartTimeText.setText(LocaleController.formatDateTime(auction.auctionStateActive.start_date, true));
            auctionRowEndTimeText.setText(LocaleController.formatDateTime(auction.auctionStateActive.end_date, true));
            auctionRowCurrentRoundText.setText(formatString(R.string.OfS,
                formatNumber(auction.auctionStateActive.current_round, ','),
                formatNumber(auction.auctionStateActive.total_rounds, ','))
            );

            final int timeLeft = auction.auctionStateActive.end_date - (ConnectionsManager.getInstance(currentAccount).getCurrentTime());

            buttonView.setSubText(formatString(R.string.Gift2AuctionTimeLeft, LocaleController.formatTTLString(timeLeft)), animated);
        }



        auctionRowAvailabilityText.setText(formatPluralString("Gift2Availability4Value",
                auction != null ? (auction.isFinished() ? 0 : auction.auctionStateActive != null ? auction.auctionStateActive.gifts_left : starGift.availability_remains):
                starGift.availability_remains,
            formatNumber(starGift.availability_total, ',')));




        final int boughtCount = auction.auctionUserState.acquired_count;
        if (boughtCount > 0) {
            itemsBought.setVisibility(View.VISIBLE);
            itemsBought.setText(TextUtils.concat(AndroidUtilities.replaceArrows(
                    LocaleController.formatPluralSpannable("Gift2AuctionsItemsBought", boughtCount, emojiGiftText),
                    true, dp(8f / 3f), dp(1))));
        } else {
            itemsBought.setVisibility(View.GONE);
        }

        if (auction != null && auction.auctionStateFinished != null || starGift.sold_out) {
            subtitleTextView.setText(getString(R.string.Gift2AuctionEnded));
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));

            auctionRowCurrentRound.setVisibility(View.GONE);
            auctionRowAveragePrice.setVisibility(View.VISIBLE);

            buttonView.setText(getString(R.string.OK), animated);
            buttonView.setSubText(null, animated);
        } else {
            auctionRowCurrentRound.setVisibility(View.VISIBLE);
            auctionRowAveragePrice.setVisibility(View.GONE);

            buttonView.setText(getString(R.string.Gift2AuctionJoin), animated);
        }
    }

    @Override
    public void onUpdate(GiftAuctionController.Auction auction) {
        this.auction = auction;
        updateTable(true);
    }

    @Override
    public void dismiss() {
        GiftAuctionController.getInstance(currentAccount).unsubscribeFromGiftAuction(giftId, this);
        super.dismiss();
    }




    @Override
    protected CharSequence getTitle() {
        return "";
    }

    private UniversalAdapter adapter;

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(-1, linearLayout));
    }

    public static void showMoreInfo(Context context, Theme.ResourcesProvider resourcesProvider, TL_stars.StarGift starGift) {
        if (context == null || starGift == null) {
            return;
        }

        BottomSheet.Builder b = new BottomSheet.Builder(context);
        Runnable dismiss = b.getDismissRunnable();

        final LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);

        ImageView backupImageView = new ImageView(context);

        backupImageView.setPadding(dp(17), dp(17), dp(17), dp(17));
        backupImageView.setImageResource(R.drawable.filled_gift_sell_24);
        ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());
        shapeDrawable.getPaint().setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        backupImageView.setBackground(shapeDrawable);

        linearLayout.addView(backupImageView, LayoutHelper.createLinear(80, 80, Gravity.CENTER, 0, 21, 0, 16));

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(getString(R.string.GiftAuctionInfoHeader));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 6));

        final TextView titleView2 = new TextView(context);
        titleView2.setGravity(Gravity.CENTER);
        titleView2.setText(getString(R.string.GiftAuctionInfoText));
        titleView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(titleView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 16));

        {
            PremiumFeatureCell cell = new PremiumFeatureCell(context, resourcesProvider);
            cell.title.setText(formatPluralString("GiftAuctionInfo1Header", starGift.gifts_per_round, starGift.gifts_per_round));
            cell.description.setText(formatPluralString("GiftAuctionInfo1Text", starGift.gifts_per_round, starGift.gifts_per_round));
            cell.nextIcon.setVisibility(View.GONE);
            cell.imageView.setImageResource(R.drawable.menu_top_bidders_24);
            cell.imageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, -2));
        }
        {
            PremiumFeatureCell cell = new PremiumFeatureCell(context, resourcesProvider);
            cell.title.setText(getString(R.string.GiftAuctionInfo2Header));
            cell.description.setText(formatPluralString("GiftAuctionInfo2Text", starGift.gifts_per_round));
            cell.nextIcon.setVisibility(View.GONE);
            cell.imageView.setImageResource(R.drawable.menu_carryover_24);
            cell.imageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, -2));
        }
        {
            PremiumFeatureCell cell = new PremiumFeatureCell(context, resourcesProvider);
            cell.title.setText(getString(R.string.GiftAuctionInfo3Header));
            cell.description.setText(getString(R.string.GiftAuctionInfo3Text));
            cell.nextIcon.setVisibility(View.GONE);
            cell.imageView.setImageResource(R.drawable.menu_bid_refund_24);
            cell.imageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, 8));
        }
        {
            ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
            button.setOnClickListener(v -> dismiss.run());
            button.setText(replaceUnderstood(getString(R.string.Understood)), false);
            linearLayout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 16, 10, 16, 8));
        }
        b.setCustomView(linearLayout);
        b.show();
    }



    public static void show(Context context,
                            Theme.ResourcesProvider resourcesProvider,
                            int currentAccount,
                            long dialogId,
                            long giftId,
                            Runnable closeParentSheet) {
        GiftAuctionController.getInstance(currentAccount).getOrRequestAuction(giftId, (auction, err) -> {
            if (auction != null) {
                show(context, resourcesProvider, currentAccount, dialogId, auction, closeParentSheet);
            }
        });
    }

    private static void show(Context context,
                            Theme.ResourcesProvider resourcesProvider,
                            int currentAccount,
                            long dialogId,
                            GiftAuctionController.Auction auction,
                            Runnable closeParentSheet) {
        if (auction == null) {
            return; // loading
        }

        final long selfPeer = UserConfig.getInstance(currentAccount).clientUserId;
        final long bidPeer = DialogObject.getPeerDialogId(auction.auctionUserState.peer);
        if (dialogId != bidPeer && dialogId != 0 && bidPeer != 0) {
            openAuctionTransferAlert(context, resourcesProvider, currentAccount, bidPeer, dialogId, () -> {
                new SendGiftSheet(context, currentAccount, auction.gift, dialogId, closeParentSheet, false, false)
                    .show();
            });
            return;
        }

        if (auction.auctionUserState.bid_date > 0 && !auction.isFinished()) {
            AuctionBidSheet auctionBidSheet = new AuctionBidSheet(context, resourcesProvider, null, auction);
            auctionBidSheet.setCloseParentSheet(closeParentSheet);
            auctionBidSheet.show();

            return;
        }

        new AuctionJoinSheet(context, resourcesProvider, dialogId, auction.gift, closeParentSheet).show();
    }

    public static void initActionBar(ActionBar actionBar, Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, TL_stars.StarGift starGift) {
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == copy_link || id == share_link) {
                    final String link = MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix + "/auction/" + starGift.auction_slug;
                    if (id == copy_link) {
                        AndroidUtilities.addToClipboard(link);
                    } else {
                        ShareAlert.createShareAlert(context, null, link, false, link, false).show();
                    }
                } else if (id == more_info) {
                    AuctionJoinSheet.showMoreInfo(context, resourcesProvider, starGift);
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.setContentDescription(getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        menuItem.addSubItem(more_info, R.drawable.msg_info, getString(R.string.MoreInfo));
        menuItem.addSubItem(copy_link, R.drawable.menu_feature_links, getString(R.string.CopyLink));
        menuItem.addSubItem(share_link, R.drawable.msg_share, getString(R.string.ShareLink));
    }




    private static void openAuctionTransferAlert(final Context context,
                                                 final Theme.ResourcesProvider resourcesProvider,
                                                 final int currentAccount,
                                                 final long fromDialogId,
                                                 final long toDialogId,
                                                 Runnable onConfirmed) {
        final TLObject fromObj;
        if (fromDialogId >= 0) {
            fromObj = MessagesController.getInstance(currentAccount).getUser(fromDialogId);
        } else {
            fromObj = MessagesController.getInstance(currentAccount).getChat(-fromDialogId);
        }

        final TLObject toObj;
        if (toDialogId >= 0) {
            toObj = MessagesController.getInstance(currentAccount).getUser(toDialogId);
        } else {
            toObj = MessagesController.getInstance(currentAccount).getChat(-toDialogId);
        }

        final LinearLayout topView = new LinearLayout(context);
        topView.setOrientation(LinearLayout.VERTICAL);
        topView.addView(new StarGiftSheet.UserToUserTransferTopView(context, fromObj, toObj), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, -4, 0, 0));
        {
            TextView titleTextView = new TextView(context);
            NotificationCenter.listenEmojiLoading(titleTextView);
            titleTextView.setText(getString(R.string.Gift2AuctionsChangeRecipient));
            titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            topView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 19, 24, 2));
        }
        {
            final TextView textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setText(AndroidUtilities.replaceTags(
                    formatString(R.string.Gift2AuctionsChangeRecipient2, DialogObject.getShortName(fromDialogId), DialogObject.getShortName(toDialogId))
            ));
            topView.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 4));
        }
        new AlertDialog.Builder(context, resourcesProvider)
            .setView(topView)
            .setPositiveButton(getString(R.string.Continue), (di, w) -> {
                onConfirmed.run();
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
            .show();
    }
}
