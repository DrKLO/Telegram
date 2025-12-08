package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceArrows;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatSpannable;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarGiftSheet.replaceUnderstood;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.PremiumFeatureCell;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;

public class AuctionWearingSheet extends BottomSheetWithRecyclerListView implements GiftAuctionController.OnAuctionUpdateListener {
    private final TL_stars.StarGift starGift;
    private final long giftId;
    private final FrameLayout headerContainer;
    private final LinearLayout linearLayout;

    private final StarGiftSheet.TopView topView;
    private final TextView giftNameTextView;
    private final GiftSheet.GiftCell giftCell2;
    private final ButtonWithCounterView buttonView;

    private GiftAuctionController.Auction auction;

    public AuctionWearingSheet(Context context, Theme.ResourcesProvider resourcesProvider,
                                long dialogId, TL_stars.StarGift starGift,
                                ArrayList<TL_stars.StarGiftAttribute> previewAttributes,
                                Runnable closeParentSheet, boolean isInfo) {
        super(context, null, false, false, false, false, ActionBarType.FADING, resourcesProvider);
        this.starGift = starGift;
        this.giftId = starGift.id;
        headerMoveTop = dp(6);
        topPadding = 0.2f;

        setBackgroundColor(getBackgroundColor());
        fixNavigationBar();

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setClipChildren(false);
        linearLayout.setClipToPadding(false);
        linearLayout.setClickable(true);

        headerContainer = new FrameLayout(context) {
            RectF rectF = new RectF();
            RectF rectF2 = new RectF();

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (!ViewPositionWatcher.computeRectInParent(topView.imageLayout, this, rectF)) return;
                if (!ViewPositionWatcher.computeRectInParent(giftNameTextView, this, rectF2)) return;

                final float x = rectF2.right - dp(32);
                final float y = rectF2.centerY() - dp(16);
                if (!rectF.isEmpty()) {
                    canvas.save();
                    canvas.translate(x, y);
                    canvas.scale(dp(32) / rectF.width(), dp(32) / rectF.height());
                    topView.imageLayout.draw(canvas);
                    canvas.restore();
                }
            }
        };
        linearLayout.addView(headerContainer);



        buttonView = new ButtonWithCounterView(context, resourcesProvider);

        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16);
        lp.leftMargin += backgroundPaddingLeft;
        lp.rightMargin += backgroundPaddingLeft;
        containerView.addView(buttonView, lp);

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(16 + 48));
        adapter.update(false);

        final int topHeightDp = isInfo ? 220: 208;
        auction = GiftAuctionController.getInstance(currentAccount).subscribeToGiftAuction(giftId, this);
        topView = new StarGiftSheet.TopView(context, resourcesProvider, this::onBackPressed, v -> {}, v -> {}, v -> {}, v -> {}, v -> {}, v -> {}) {
            @Override
            public float getRealHeight() {
                return dp(topHeightDp);
            }

            @Override
            public int getFinalHeight() {
                return dp(topHeightDp);
            }

            Path path = new Path();
            float[] r = new float[8];

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);

                r[0] = r[1] = r[2] = r[3] = dp(12);
                path.rewind();
                path.addRoundRect(0, 0, w, h, r, Path.Direction.CW);
            }

            @Override
            protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
                if (child == imageLayout) {
                    return true;
                }

                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                canvas.save();
                canvas.clipPath(path);
                super.dispatchDraw(canvas);
                canvas.restore();
            }

            @Override
            protected void updateButtonsBackgrounds(int color) {
                super.updateButtonsBackgrounds(color);
                giftCell2.setRibbonColor(color);
            }

            @Override
            public void invalidate() {
                super.invalidate();
                if (giftCell2 != null) {
                    giftCell2.invalidate();
                }
            }
        };
        topView.onSwitchPage(new StarGiftSheet.PageTransition(StarGiftSheet.PAGE_UPGRADE, StarGiftSheet.PAGE_UPGRADE, 1.0f));
        topView.setPreviewingAttributes(previewAttributes);
        topView.hideCloseButton();
        headerContainer.addView(topView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, topHeightDp, Gravity.TOP));

        BackupImageView avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(45));
        headerContainer.addView(avatarImageView, LayoutHelper.createFrame(90, 90, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 42, 0, 0));

        TLObject userOrChat;
        AvatarDrawable avatarDrawable;
        if (dialogId == 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
            userOrChat = user;
            avatarDrawable = new AvatarDrawable(user);
        } else if (dialogId > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            userOrChat = user;
            avatarDrawable = new AvatarDrawable(user);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            userOrChat = chat;
            avatarDrawable = new AvatarDrawable(chat);
        }
        avatarImageView.setForUserOrChat(userOrChat, avatarDrawable);

        giftNameTextView = new TextView(context);
        giftNameTextView.setTypeface(AndroidUtilities.bold());
        giftNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
        giftNameTextView.setText(DialogObject.getShortName(dialogId != 0 ? dialogId : UserConfig.getInstance(currentAccount).getClientUserId()));
        giftNameTextView.setGravity(Gravity.CENTER);
        giftNameTextView.setTextColor(Color.WHITE);
        giftNameTextView.setPadding(0, 0, dp(36), 0);
        giftNameTextView.setSingleLine();
        giftNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        giftNameTextView.setMaxLines(1);
        headerContainer.addView(giftNameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 40));

        TextView giftStatusTextView = new TextView(context);
        giftStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        giftStatusTextView.setPadding(dp(8), dp(8), dp(8), dp(8));
        giftStatusTextView.setGravity(Gravity.CENTER);
        giftStatusTextView.setTextColor(0xAFFFFFFF);
        if (isInfo) {
            giftStatusTextView.setText(getString(R.string.GiftAuctionWearInfoOnline));
        } else {
            giftStatusTextView.setText(replaceArrows(getString(R.string.Gift2AuctionLearnMore3), false, dp(8f / 3f), dp(1)));
            giftStatusTextView.setOnClickListener(v -> {
                new AuctionWearingSheet(context, resourcesProvider, dialogId, starGift, previewAttributes, null, true).show();
            });
            ScaleStateListAnimator.apply(giftStatusTextView, 0.02f, 1.5f);
        }
        headerContainer.addView(giftStatusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 12));



        LinearLayout horizontalLayout = new LinearLayout(context);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setGravity(Gravity.CENTER);
        horizontalLayout.setClickable(true);

        GiftSheet.GiftCell giftCell = new GiftSheet.GiftCell(context, currentAccount, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }
        };
        giftCell.setPriorityAuction();
        giftCell.setStarsGift(starGift, true, false, false, false);
        giftCell.setImageSize(dp(84));
        giftCell.setImageLayer(7);
        giftCell.hidePrice();
        giftCell.cardBackground.setStrokeColors(null);
        giftCell.setRibbonTextOneOf(auction.gift.availability_total);
        horizontalLayout.addView(giftCell, LayoutHelper.createLinear(116, 116, 0f));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.ic_ab_back);
        imageView.setScaleX(-1);
        imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
        horizontalLayout.addView(imageView, LayoutHelper.createLinear(24, 24, 0f, Gravity.CENTER_VERTICAL, 12, 0, 12, 0));

        giftCell2 = new GiftSheet.GiftCell(context, currentAccount, resourcesProvider) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return false;
            }

            RectF rectF = new RectF();
            RectF rectF2 = new RectF();

            Path path = new Path();

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                path.rewind();
                rectF.set(0, 0, w, h);
                rectF.inset(dp(GiftSheet.CardBackground.PADDING_HORIZONTAL_DP), dp(GiftSheet.CardBackground.PADDING_VERTICAL_DP));
                path.addRoundRect(rectF, dp(11), dp(11), Path.Direction.CW);
            }

            @Override
            protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
                boolean r = super.drawChild(canvas, child, drawingTime);

                if (child == card) {
                    if (!ViewPositionWatcher.computeRectInParent(
                            AuctionWearingSheet.this.topView.imageLayout,
                            AuctionWearingSheet.this.headerContainer, rectF)) return true;
                    if (!ViewPositionWatcher.computeRectInParent(card, this, rectF2))
                        return true;

                    final float x = rectF2.centerX() - dp(40);
                    final float y = rectF2.centerY() - dp(40);
                    if (!rectF.isEmpty()) {

                        canvas.save();
                        canvas.clipPath(path);
                        canvas.scale(0.6f, 0.6f, rectF2.centerX(), rectF2.centerY());
                        canvas.translate(rectF2.centerX() - topView.getWidth() / 2f, rectF2.centerY() - topView.getHeight() / 2f);
                        topView.drawBackground(canvas, topView.getWidth() / 2f, dp(104), topView.getWidth(), topView.getHeight());
                        topView.drawPattern(canvas, topView.getWidth() / 2f, dp(104), topView.getWidth(), topView.getHeight());
                        canvas.restore();

                        canvas.save();
                        canvas.translate(x, y);
                        canvas.scale(dp(80) / rectF.width(), dp(80) / rectF.height());
                        topView.imageLayout.draw(canvas);
                        canvas.restore();
                    }
                }

                return r;
            }
        };
        giftCell2.removeImage();
        giftCell2.setPriorityAuction();
        giftCell2.setStarsGift(starGift, true, false, false, false);
        giftCell2.setImageSize(dp(100));
        giftCell2.setImageLayer(7);
        giftCell2.hidePrice();
        giftCell2.cardBackground.setStrokeColors(null);
        giftCell2.setRibbonTextOneOf(auction.gift.availability_total);
        giftCell2.setRibbonText(getString(R.string.Gift2AuctionUpgradedShort));
        horizontalLayout.addView(giftCell2, LayoutHelper.createLinear(116, 116, 0f));


        TextView hint1 = new TextView(context);
        hint1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint1.setGravity(Gravity.CENTER);
        hint1.setText(getString(R.string.Gift2WearingHint));
        hint1.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));

        FrameLayout limitContainer = null;
        {
            final float limitedProgress = Utilities.clamp((float) starGift.availability_remains / starGift.availability_total, 1f, 0);
            limitContainer = new FrameLayout(context);
            limitContainer.setBackground(Theme.createRoundRectDrawable(dp(14), ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.2f)));

            TextView leftTextView = new TextView(context);
            leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            leftTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            leftTextView.setTypeface(AndroidUtilities.bold());
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            leftTextView.setText(LocaleController.formatPluralStringComma("Gift2AvailabilityLeft", starGift.availability_remains));
            limitContainer.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 11, 0, 11, 0));

            TextView soldTextView = new TextView(context);
            soldTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            soldTextView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            soldTextView.setTypeface(AndroidUtilities.bold());
            soldTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            soldTextView.setText(LocaleController.formatPluralStringComma("Gift2AvailabilitySold", starGift.availability_total - starGift.availability_remains));
            limitContainer.addView(soldTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT, 11, 0, 11, 0));

            View limitProgressView = new View(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(widthMeasureSpec) * limitedProgress), MeasureSpec.EXACTLY), heightMeasureSpec);
                }
            };
            limitProgressView.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
            limitContainer.addView(limitProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            FrameLayout valueContainerView = new FrameLayout(context) {
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    canvas.save();
                    canvas.clipRect(0, 0, getWidth() * limitedProgress, getHeight());
                    super.dispatchDraw(canvas);
                    canvas.restore();
                }
            };
            valueContainerView.setWillNotDraw(false);
            limitContainer.addView(valueContainerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));


            TextView leftTextView2 = new TextView(context);
            leftTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            leftTextView2.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            leftTextView2.setTypeface(AndroidUtilities.bold());
            leftTextView2.setTextColor(0xFFFFFFFF);
            leftTextView2.setText(LocaleController.formatPluralStringComma("Gift2AvailabilityLeft", starGift.availability_remains));
            valueContainerView.addView(leftTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 11, 0, 11, 0));

            TextView soldTextView2 = new TextView(context);
            soldTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            soldTextView2.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            soldTextView2.setTypeface(AndroidUtilities.bold());
            soldTextView2.setTextColor(0xFFFFFFFF);
            soldTextView2.setText(LocaleController.formatPluralStringComma("Gift2AvailabilitySold", starGift.availability_total - starGift.availability_remains));
            valueContainerView.addView(soldTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT, 11, 0, 11, 0));
        }

        LinkSpanDrawable.LinksTextView hint2 = null;
        if (auction != null && auction.auctionStateActive != null) {
            hint2 = new LinkSpanDrawable.LinksTextView(context);
            hint2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            hint2.setGravity(Gravity.CENTER);
            hint2.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            hint2.setText(AndroidUtilities.replaceTags(formatSpannable(R.string.Gift2AuctionInfo3,
                formatNumber(starGift.availability_total, ','),
                auction.auctionStateActive.total_rounds,
                starGift.gifts_per_round,
                AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2AuctionInfoLearnMore), () -> AuctionJoinSheet.showMoreInfo(context, resourcesProvider, starGift)), true, dp(8f / 3f), dp(1))
            )));
            hint2.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        }

        if (isInfo) {
            showWearingMoreInfo(context, resourcesProvider, linearLayout, starGift);
            buttonView.setText(replaceUnderstood(getString(R.string.Understood)), false);
            buttonView.setOnClickListener(v -> {
                dismiss();
            });
        } else {
            linearLayout.addView(horizontalLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 20, 0, 10));
            linearLayout.addView(hint1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 40, 0, 40, 15));
            linearLayout.addView(limitContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28, 14, 18, 14, 10));
            if (hint2 != null) {
                linearLayout.addView(hint2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 40, 0, 40, 32));
            }

            final int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            if (auction != null && auction.isUpcoming(currentTime)) {
                buttonView.setText(getString(R.string.Gift2AuctionPlaceAEarlyBid), false);
            } else {
                buttonView.setText(getString(R.string.Gift2AuctionPlaceABid), false);
            }

            if (auction != null && auction.auctionStateActive != null) {
                if (auction.isUpcoming(currentTime)) {
                    final int timeLeft = auction.auctionStateActive.start_date - currentTime;
                    buttonView.setSubText(formatString(R.string.Gift2AuctionStartsIn, LocaleController.formatTTLString(timeLeft)), false);
                } else {
                    final int timeLeft = auction.auctionStateActive.end_date - currentTime;
                    buttonView.setSubText(formatString(R.string.Gift2AuctionTimeLeft, LocaleController.formatTTLString(timeLeft)), false);
                }
            }

            buttonView.setOnClickListener(v -> {
                final AuctionBidSheet.Params p = new AuctionBidSheet.Params(dialogId, true, null);
                AuctionBidSheet auctionSheet = new AuctionBidSheet(context, resourcesProvider, p, auction);
                auctionSheet.show();
                auctionSheet.setCloseParentSheet(closeParentSheet);
                dismiss();
            });
        }

        updateTable(false);
    }

    private void updateTable(boolean animated) {

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


    private int getBackgroundColor() {
        return ColorUtils.blendARGB(
                getThemedColor(Theme.key_dialogBackgroundGray),
                getThemedColor(Theme.key_dialogBackground), 0.1f);
    }


    private static void showWearingMoreInfo(Context context, Theme.ResourcesProvider resourcesProvider, LinearLayout linearLayout, TL_stars.StarGift starGift) {
        if (context == null || starGift == null) {
            return;
        }

        final TextView titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setText(formatString(R.string.GiftAuctionWearInfoHeader, starGift.title));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 14, 20, 6));

        final TextView titleView2 = new TextView(context);
        titleView2.setGravity(Gravity.CENTER);
        titleView2.setText(getString(R.string.GiftAuctionWearInfoText));
        titleView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView2.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(titleView2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 16));

        {
            PremiumFeatureCell cell = new PremiumFeatureCell(context, resourcesProvider);
            cell.title.setText(getString(R.string.GiftAuctionWearInfo1Header));
            cell.description.setText(getString(R.string.GiftAuctionWearInfo1Text));
            cell.nextIcon.setVisibility(View.GONE);
            cell.imageView.setImageResource(R.drawable.msg_emoji_gem);
            cell.imageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, -2));
        }
        {
            PremiumFeatureCell cell = new PremiumFeatureCell(context, resourcesProvider);
            cell.title.setText(getString(R.string.GiftAuctionWearInfo2Header));
            cell.description.setText(getString(R.string.GiftAuctionWearInfo2Text));
            cell.nextIcon.setVisibility(View.GONE);
            cell.imageView.setImageResource(R.drawable.menu_feature_cover_24);
            cell.imageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, -2));
        }
        {
            PremiumFeatureCell cell = new PremiumFeatureCell(context, resourcesProvider);
            cell.title.setText(getString(R.string.GiftAuctionWearInfo3Header));
            cell.description.setText(getString(R.string.GiftAuctionWearInfo3Text));
            cell.nextIcon.setVisibility(View.GONE);
            cell.imageView.setImageResource(R.drawable.menu_verification);
            cell.imageView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, 14));
        }
    }
}
