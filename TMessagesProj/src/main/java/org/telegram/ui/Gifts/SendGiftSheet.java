package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatSpannable;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.GiftAuctionController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.EditEmojiTextCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.EditTextSuggestionsFix;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GiftPremiumBottomSheet;
import org.telegram.ui.Components.Premium.boosts.BoostDialogs;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.Premium.boosts.PremiumPreviewGiftSentBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.PreviewView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class SendGiftSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate, GiftAuctionController.OnAuctionUpdateListener {

    private final boolean self;
    private final int currentAccount;
    private final long dialogId;
    private final boolean forceUpgrade, forceNotUpgrade;
    private final TL_stars.StarGift starGift;
    private @Nullable GiftAuctionController.Auction auction;
    private final GiftPremiumBottomSheet.GiftTier premiumTier;
    private final String name;
    private final Runnable closeParentSheet;

    private final SizeNotifierFrameLayout chatView;
    private final LinearLayout chatLinearLayout;

    private final long send_paid_messages_stars;
//    private final ChatActionCell payActionCell;
    private final ChatActionCell actionCell;

    private final TLRPC.MessageAction action;
    private final MessageObject messageObject;

    private final LinearLayout buttonContainer;
    private final ButtonWithCounterView button;

    private final FrameLayout limitContainer;
    private final View limitProgressView;
    private final FrameLayout valueContainerView;
    private final TextView soldTextView, soldTextView2;
    private final TextView leftTextView, leftTextView2;

    public boolean anonymous;
    public boolean upgrade = false;
    public boolean useStars = false;

    private EditEmojiTextCell messageEdit;

    private UniversalAdapter adapter;

    private int shakeDp = -2;

    public final AnimationNotificationsLocker animationsLock = new AnimationNotificationsLocker();

    public SendGiftSheet(Context context, int currentAccount, TL_stars.StarGift gift, long dialogId, Runnable closeParentSheet, boolean forceUpgrade, boolean forceNotUpgrade) {
        this(context, currentAccount, gift, null, dialogId, closeParentSheet, forceUpgrade, forceNotUpgrade);
    }

    public SendGiftSheet(Context context, int currentAccount, GiftPremiumBottomSheet.GiftTier premiumTier, long dialogId, Runnable closeParentSheet) {
        this(context, currentAccount, null, premiumTier, dialogId, closeParentSheet, false, false);
    }

    private SendGiftSheet(Context context, int currentAccount, TL_stars.StarGift starGift, GiftPremiumBottomSheet.GiftTier premiumTier, long dialogId, Runnable closeParentSheet, boolean forceUpgrade, boolean forceNotUpgrade) {
        super(context, null, true, false, false, false, ActionBarType.SLIDING, null);

        self = dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
        setImageReceiverNumLevel(0, 4);
        fixNavigationBar();
//        setSlidingActionBar();
        headerPaddingTop = dp(4);
        headerPaddingBottom = dp(-10);
        if (self) {
            anonymous = true;
        }

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.starGift = starGift;
        if (starGift != null && starGift.auction) {
            auction = GiftAuctionController.getInstance(currentAccount).subscribeToGiftAuction(starGift.id, this);
        }

        this.premiumTier = premiumTier;
        this.closeParentSheet = closeParentSheet;
        this.forceUpgrade = forceUpgrade;
        this.forceNotUpgrade = forceNotUpgrade;
        if (forceUpgrade) {
            upgrade = true;
        } else if (forceNotUpgrade) {
            upgrade = false;
        }

        topPadding = 0.2f;

        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            this.name = UserObject.getForcedFirstName(user);
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            this.name = chat == null ? "" : chat.title;
        }

        actionCell = new ChatActionCell(context, false, resourcesProvider);
        actionCell.setDelegate(new ChatActionCell.ChatActionCellDelegate() {});

        chatView = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }
            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }
            @Override
            protected boolean useRootView() {
                return false;
            }
            int maxHeight = -1;
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (maxHeight != -1) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (getMeasuredHeight() < maxHeight) {
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.max(maxHeight, getMeasuredHeight()), MeasureSpec.AT_MOST);
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (maxHeight == -1) {
                    maxHeight = Math.max(maxHeight, getMeasuredHeight());
                }
            }
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                chatLinearLayout.setTranslationY(((b - t) - chatLinearLayout.getMeasuredHeight()) / 2f);
                actionCell.setVisiblePart(chatLinearLayout.getY() + actionCell.getY(), getBackgroundSizeY());
            }
        };
        chatView.setBackgroundImage(PreviewView.getBackgroundDrawable(null, currentAccount, dialogId, Theme.isCurrentThemeDark()), false);

        chatLinearLayout = new LinearLayout(context);
        chatLinearLayout.setOrientation(LinearLayout.VERTICAL);

        if (starGift != null) {
            TLRPC.TL_messageActionStarGift action = new TLRPC.TL_messageActionStarGift();
            action.gift = starGift;
            action.flags |= 2;
            action.message = new TLRPC.TL_textWithEntities();
            action.convert_stars = this.starGift.convert_stars;
            action.forceIn = true;
            this.action = action;
        } else if (premiumTier != null && premiumTier.giftCodeOption != null) {
            TLRPC.TL_messageActionGiftCode action = new TLRPC.TL_messageActionGiftCode();
            action.unclaimed = true;
            action.via_giveaway = false;
            action.months = premiumTier.getMonths();
            action.flags |= 4;
            action.currency = premiumTier.getCurrency();
            action.amount = premiumTier.getPrice();
            if (premiumTier.googlePlayProductDetails != null) {
                action.amount = (long) (action.amount * Math.pow(10, BillingController.getInstance().getCurrencyExp(action.currency) - 6));
            }
            action.flags |= 16;
            action.message = new TLRPC.TL_textWithEntities();
            this.action = action;
        } else if (premiumTier != null && premiumTier.giftOption != null) {
            TLRPC.TL_messageActionGiftPremium action = new TLRPC.TL_messageActionGiftPremium();
            action.months = premiumTier.getMonths();
            action.currency = premiumTier.getCurrency();
            action.amount = premiumTier.getPrice();
            if (premiumTier.googlePlayProductDetails != null) {
                action.amount = (long) (action.amount * Math.pow(10, BillingController.getInstance().getCurrencyExp(action.currency) - 6));
            }
            action.flags |= 2;
            action.message = new TLRPC.TL_textWithEntities();
            this.action = action;
        } else {
            throw new RuntimeException("SendGiftSheet with no star gift and no premium tier");
        }
        if (action instanceof TLRPC.TL_messageActionStarGift) {
            TLRPC.TL_messageActionStarGift thisAction = (TLRPC.TL_messageActionStarGift) action;
            thisAction.can_upgrade = upgrade || self && starGift != null && starGift.can_upgrade;
            thisAction.upgrade_stars = self ? 0 : upgrade ? this.starGift.upgrade_stars : 0;
            thisAction.convert_stars = upgrade ? 0 : this.starGift.convert_stars;
        }

        final TLRPC.TL_messageService message = new TLRPC.TL_messageService();
        message.id = 1;
        message.dialog_id = dialogId;
        message.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
        message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId   );
        message.action = action;

        send_paid_messages_stars = starGift != null ? MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(dialogId) : 0;

        messageObject = new MessageObject(currentAccount, message, false, false);
        actionCell.setMessageObject(messageObject, true);
        chatLinearLayout.addView(actionCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, send_paid_messages_stars > 0 ? 0 : 8, 0, 8));

        chatView.addView(chatLinearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        messageEdit = new EditEmojiTextCell(context, (SizeNotifierFrameLayout) containerView, getString(starGift != null ? R.string.Gift2Message : R.string.Gift2MessageOptional), true, MessagesController.getInstance(currentAccount).stargiftsMessageLengthMax, EditTextEmoji.STYLE_GIFT, resourcesProvider) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                TLRPC.TL_textWithEntities txt;
                if (action instanceof TLRPC.TL_messageActionStarGift) {
                    txt = ((TLRPC.TL_messageActionStarGift) action).message = new TLRPC.TL_textWithEntities();
                } else if (action instanceof TLRPC.TL_messageActionGiftCode) {
                    ((TLRPC.TL_messageActionGiftCode) action).flags |= 16;
                    txt = ((TLRPC.TL_messageActionGiftCode) action).message = new TLRPC.TL_textWithEntities();
                } else if (action instanceof TLRPC.TL_messageActionGiftPremium) {
                    ((TLRPC.TL_messageActionGiftPremium) action).flags |= 16;
                    txt = ((TLRPC.TL_messageActionGiftPremium) action).message = new TLRPC.TL_textWithEntities();
                } else return;
                CharSequence[] msg = new CharSequence[] { messageEdit.getText() };
                txt.entities = MediaDataController.getInstance(currentAccount).getEntities(msg, true);
                txt.text = msg[0].toString();
                messageObject.setType();
                actionCell.setMessageObject(messageObject, true);
                adapter.update(true);
                setButtonText(true);
            }

            @Override
            protected void onFocusChanged(boolean focused) {

            }
        };
        messageEdit.editTextEmoji.getEditText().addTextChangedListener(new EditTextSuggestionsFix());
        messageEdit.editTextEmoji.allowEmojisForNonPremium(true);
        messageEdit.setShowLimitWhenNear(50);
        setEditTextEmoji(messageEdit.editTextEmoji);
        messageEdit.setShowLimitOnFocus(true);
        messageEdit.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        messageEdit.setDivider(false);
        messageEdit.hideKeyboardOnEnter();
        messageEdit.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return .3f;
            }
        };
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayIncrement(40);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(48 + 10 + 10 + (starGift != null && starGift.limited ? 30 + 10 : 0)));
        adapter.update(false);

        buttonContainer = new LinearLayout(context);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        buttonContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        final View buttonShadow = new View(context);
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
        buttonContainer.addView(buttonShadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        final float limitedProgress = Utilities.clamp(starGift == null ? 0 : (float) starGift.availability_remains / starGift.availability_total, 1f, 0);
        limitContainer = new FrameLayout(context);
        limitContainer.setVisibility(starGift != null && starGift.limited ? View.VISIBLE : View.GONE);
        limitContainer.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider)));
        buttonContainer.addView(limitContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 30, 10, 10, 10, 0));

        leftTextView = new TextView(context);
        leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        leftTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        leftTextView.setTypeface(AndroidUtilities.bold());
        leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        if (starGift != null) {
            leftTextView.setText(LocaleController.formatPluralStringComma("Gift2AvailabilityLeft", starGift.availability_remains));
        }
        limitContainer.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 11, 0, 11, 0));

        soldTextView = new TextView(context);
        soldTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        soldTextView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        soldTextView.setTypeface(AndroidUtilities.bold());
        soldTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        if (starGift != null) {
            soldTextView.setText(LocaleController.formatPluralStringComma("Gift2AvailabilitySold", starGift.availability_total - starGift.availability_remains));
        }
        limitContainer.addView(soldTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT, 11, 0, 11, 0));

        limitProgressView = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (starGift == null) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    return;
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(widthMeasureSpec) * limitedProgress), MeasureSpec.EXACTLY), heightMeasureSpec);
            }
        };
        limitProgressView.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        limitContainer.addView(limitProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        valueContainerView = new FrameLayout(context) {
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

        leftTextView2 = new TextView(context);
        leftTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        leftTextView2.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        leftTextView2.setTypeface(AndroidUtilities.bold());
        leftTextView2.setTextColor(0xFFFFFFFF);
        if (starGift != null) {
            leftTextView2.setText(LocaleController.formatPluralStringComma("Gift2AvailabilityLeft", starGift.availability_remains));
        }
        valueContainerView.addView(leftTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 11, 0, 11, 0));

        soldTextView2 = new TextView(context);
        soldTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        soldTextView2.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        soldTextView2.setTypeface(AndroidUtilities.bold());
        soldTextView2.setTextColor(0xFFFFFFFF);
        if (starGift != null) {
            soldTextView2.setText(LocaleController.formatPluralStringComma("Gift2AvailabilitySold", starGift.availability_total - starGift.availability_remains));
        }
        valueContainerView.addView(soldTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT, 11, 0, 11, 0));

        button = new ButtonWithCounterView(context, resourcesProvider);
        setButtonText(false);
        buttonContainer.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10, 10, 10));
        button.setOnClickListener(v -> {
            if (button.isLoading()) return;

            if (auction != null) {
                final AuctionBidSheet.Params p = new AuctionBidSheet.Params(dialogId, anonymous, getMessage());
                AuctionBidSheet auctionSheet = new AuctionBidSheet(context, resourcesProvider, p, auction);
                auctionSheet.show();
                auctionSheet.setCloseParentSheet(closeParentSheet);

                AndroidUtilities.hideKeyboard(messageEdit);
                dismiss();
                if (!isDismissed) {
                    AndroidUtilities.runOnUIThread(this::dismiss, 500);
                }
                return;
            }

            button.setLoading(true);
            if (messageEdit.editTextEmoji.getEmojiPadding() > 0) {
                messageEdit.editTextEmoji.hidePopup(true);
            } else if (messageEdit.editTextEmoji.isKeyboardVisible()) {
                messageEdit.editTextEmoji.closeKeyboard();
            }
            if (starGift != null) {
                buyStarGift();
            } else {
                buyPremiumTier();
            }
        });

        layoutManager.setReverseLayout(reverseLayout = true);
        adapter.update(false);
        layoutManager.scrollToPositionWithOffset(adapter.getItemCount(), dp(200));

        recyclerListView.setOnItemClickListener((view, position) -> {
            final UItem item = adapter.getItem(reverseLayout ? position : position - 1);
            if (item == null) return;
            if (item.id == 1) {
                anonymous = !anonymous;
                if (action instanceof TLRPC.TL_messageActionStarGift) {
                    ((TLRPC.TL_messageActionStarGift) action).name_hidden = anonymous;
                }
                messageObject.updateMessageText();
                actionCell.setMessageObject(messageObject, true);
                adapter.update(true);
            } else if (item.id == 2) {
                if (forceUpgrade || forceNotUpgrade) {
                    AndroidUtilities.shakeViewSpring(view, shakeDp = -shakeDp);
                    return;
                }
                upgrade = !upgrade;
                if (action instanceof TLRPC.TL_messageActionStarGift) {
                    TLRPC.TL_messageActionStarGift thisAction = (TLRPC.TL_messageActionStarGift) action;
                    thisAction.can_upgrade = upgrade || self && starGift != null && starGift.can_upgrade;
                    thisAction.upgrade_stars = self ? 0 : upgrade ? this.starGift.upgrade_stars : 0;
                    thisAction.convert_stars = upgrade ? 0 : this.starGift.convert_stars;
                }
                messageObject.updateMessageText();
                actionCell.setMessageObject(messageObject, true);
                adapter.update(true);
                setButtonText(true);
            } else if (item.id == 3) {
                useStars = !useStars;
                if (action instanceof TLRPC.TL_messageActionGiftPremium) {
                    final TLRPC.TL_messageActionGiftPremium thisAction = (TLRPC.TL_messageActionGiftPremium) action;
                    if (useStars) {
                        thisAction.currency = "XTR";
                        thisAction.amount = premiumTier.getStarsPrice();
                    } else {
                        thisAction.currency = premiumTier.getCurrency();
                        thisAction.amount = premiumTier.getPrice();
                        if (premiumTier.googlePlayProductDetails != null) {
                            thisAction.amount = (long) (thisAction.amount * Math.pow(10, BillingController.getInstance().getCurrencyExp(thisAction.currency) - 6));
                        }
                    }
                } else if (action instanceof TLRPC.TL_messageActionGiftCode) {
                    final TLRPC.TL_messageActionGiftCode thisAction = (TLRPC.TL_messageActionGiftCode) action;
                    if (useStars) {
                        thisAction.currency = "XTR";
                        thisAction.amount = premiumTier.getStarsPrice();
                    } else {
                        thisAction.currency = premiumTier.getCurrency();
                        thisAction.amount = premiumTier.getPrice();
                        if (premiumTier.googlePlayProductDetails != null) {
                            thisAction.amount = (long) (thisAction.amount * Math.pow(10, BillingController.getInstance().getCurrencyExp(thisAction.currency) - 6));
                        }
                    }
                }
                messageObject.updateMessageText();
                actionCell.setMessageObject(messageObject, true);
                adapter.update(true);
                setButtonText(true);
            }
        });
        actionBar.setTitle(getTitle());
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starBalanceUpdated) {
            setButtonText(true);
            if (adapter != null && premiumTier != null) {
                adapter.update(true);
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
    }

    private void setButtonText(boolean animated) {
        if (auction != null) {
            button.setText(getString(R.string.Gift2AuctionPlaceABid), animated);
            final int timeLeft = auction.auctionStateActive != null ?
                Math.max(0, auction.auctionStateActive.end_date - (ConnectionsManager.getInstance(currentAccount).getCurrentTime())) : 0;
            button.setSubText(formatString(R.string.Gift2AuctionTimeLeft, LocaleController.formatTTLString(timeLeft)), animated);
        } else if (starGift != null) {
            final long balance = StarsController.getInstance(currentAccount).getBalance().amount;
            final long price = this.starGift.stars + (upgrade ? this.starGift.upgrade_stars : 0) + (TextUtils.isEmpty(messageEdit.getText()) ? 0 : send_paid_messages_stars);
            button.setText(StarsIntroActivity.replaceStars(LocaleController.formatPluralStringComma(self ? "Gift2SendSelf" : "Gift2Send", (int) price), cachedStarSpan), animated);
            if (StarsController.getInstance(currentAccount).balanceAvailable() && price > balance) {
                button.setSubText(LocaleController.formatPluralStringComma("Gift2SendYourBalance", (int) balance), animated);
            } else {
                button.setSubText(null, animated);
            }
        } else if (premiumTier != null) {
            if (useStars) {
                button.setText(StarsIntroActivity.replaceStars(LocaleController.formatString(R.string.Gift2SendPremiumStars, LocaleController.formatNumber(premiumTier.getStarsPrice(), ',')), 1.0f, cachedStarSpan), animated);
                cachedStarSpan[0].spaceScaleX = .85f;
            } else {
                button.setText(new SpannableStringBuilder(LocaleController.formatString(R.string.Gift2SendPremium, premiumTier.getFormattedPrice())), animated);
            }
            button.setSubText(null, animated);
        }
    }

    @Override
    public void onUpdate(GiftAuctionController.Auction auction) {
        this.auction = auction;
    }


    protected BulletinFactory getParentBulletinFactory() {
        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment == null) return null;
        return BulletinFactory.of(lastFragment);
    }

    private final ColoredImageSpan[] cachedStarSpan = new ColoredImageSpan[1];

    private TLRPC.TL_textWithEntities getMessage() {
        final long paidMessagesStarsPrice = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(dialogId);
        if (paidMessagesStarsPrice > 0) {
            return null;
        }
        if (action instanceof TLRPC.TL_messageActionStarGift) {
            return ((TLRPC.TL_messageActionStarGift) action).message;
        } else if (action instanceof TLRPC.TL_messageActionGiftCode) {
            return ((TLRPC.TL_messageActionGiftCode) action).message;
        } else if (action instanceof TLRPC.TL_messageActionGiftPremium) {
            return ((TLRPC.TL_messageActionGiftPremium) action).message;
        } else {
            return null;
        }
    }

    private void buyStarGift() {
        StarsController.getInstance(currentAccount).buyStarGift(
            this.starGift,
            anonymous,
            upgrade,
            dialogId,
            getMessage(),
            (status, err) -> {
                if (status) {
                    if (closeParentSheet != null) {
                        closeParentSheet.run();
                    }
                    AndroidUtilities.hideKeyboard(messageEdit);
                    dismiss();
                } else if ("STARGIFT_USAGE_LIMITED".equalsIgnoreCase(err)) {
                    AndroidUtilities.hideKeyboard(messageEdit);
                    dismiss();
                    StarsController.getInstance(currentAccount).makeStarGiftSoldOut(starGift);
                    return;
                } else if ("STARGIFT_USER_USAGE_LIMITED".equalsIgnoreCase(err)) {
                    AndroidUtilities.hideKeyboard(messageEdit);
                    dismiss();
                    BulletinFactory bulletinFactory = getParentBulletinFactory();
                    if (bulletinFactory == null || starGift == null || !starGift.limited_per_user) return;
                    bulletinFactory
                        .createSimpleMultiBulletin(starGift.getDocument(), AndroidUtilities.replaceTags(formatPluralStringComma("Gift2PerUserLimit", starGift.per_user_total)))
                        .show();
                    return;
                }
                button.setLoading(false);
            }
        );
    }

    private void buyPremiumTier() {
        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        if (user == null) {
            button.setLoading(false);
            return;
        }
        final Object option;
        if (useStars && premiumTier.isStarsPaymentAvailable()) {
            option = premiumTier.getStarsOption();
        } else {
            if (premiumTier.giftCodeOption != null) {
                option = premiumTier.giftCodeOption;
            } else if (premiumTier.giftOption != null) {
                option = premiumTier.giftOption;
            } else {
                button.setLoading(false);
                return;
            }
        }
        if (option instanceof TLRPC.TL_premiumGiftCodeOption) {
            final TLRPC.TL_premiumGiftCodeOption o = (TLRPC.TL_premiumGiftCodeOption) option;
            if ("XTR".equalsIgnoreCase(o.currency)) {
                StarsController.getInstance(currentAccount).buyPremiumGift(dialogId, o, getMessage(), (status, err) -> {
                    if (status) {
                        if (closeParentSheet != null) {
                            closeParentSheet.run();
                        }
                        AndroidUtilities.hideKeyboard(messageEdit);
                        dismiss();

                        AndroidUtilities.runOnUIThread(() -> PremiumPreviewGiftSentBottomSheet.show(new ArrayList<>(Arrays.asList(user))), 250);
                    } else if (!TextUtils.isEmpty(err)) {
                        BulletinFactory.of(topBulletinContainer, resourcesProvider)
                            .createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, err))
                            .show();
                    }
                    button.setLoading(false);
                });
            } else {
                final BaseFragment fragment = new BaseFragment() {
                    @Override
                    public Activity getParentActivity() {
                        Activity activity = getOwnerActivity();
                        if (activity == null) activity = LaunchActivity.instance;
                        if (activity == null)
                            activity = AndroidUtilities.findActivity(SendGiftSheet.this.getContext());
                        return activity;
                    }

                    @Override
                    public Theme.ResourcesProvider getResourceProvider() {
                        return SendGiftSheet.this.resourcesProvider;
                    }
                };
                BoostRepository.payGiftCode(new ArrayList<>(Arrays.asList(user)), o, null, getMessage(), fragment, result -> {
                    if (closeParentSheet != null) {
                        closeParentSheet.run();
                    }
                    dismiss();
                    NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.giftsToUserSent);
                    AndroidUtilities.runOnUIThread(() -> PremiumPreviewGiftSentBottomSheet.show(new ArrayList<>(Arrays.asList(user))), 250);

                    MessagesController.getInstance(currentAccount).getMainSettings().edit()
                        .putBoolean("show_gift_for_" + dialogId, true)
                        .putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "show_gift_for_" + dialogId, true)
                        .apply();
                }, error -> {
                    BoostDialogs.showToastError(getContext(), error);
                });
            }
        } else if (option instanceof TLRPC.TL_premiumGiftOption) {
            final TLRPC.TL_premiumGiftOption o = (TLRPC.TL_premiumGiftOption) option;
            if ("XTR".equalsIgnoreCase(o.currency)) {
                StarsController.getInstance(currentAccount).buyPremiumGift(dialogId, o, getMessage(), (status, err) -> {
                    if (status) {
                        if (closeParentSheet != null) {
                            closeParentSheet.run();
                        }
                        AndroidUtilities.hideKeyboard(messageEdit);
                        dismiss();

                        AndroidUtilities.runOnUIThread(() -> PremiumPreviewGiftSentBottomSheet.show(new ArrayList<>(Arrays.asList(user))), 250);
                    } else if (!TextUtils.isEmpty(err)) {
                        BulletinFactory.of(topBulletinContainer, resourcesProvider)
                            .createSimpleBulletin(R.raw.error, LocaleController.formatString(R.string.UnknownErrorCode, err))
                            .show();
                    }
                    button.setLoading(false);
                });
            } else if (BuildVars.useInvoiceBilling()) {
                final LaunchActivity activity = LaunchActivity.instance;
                if (activity != null) {
                    Uri uri = Uri.parse(o.bot_url);
                    if (uri.getHost().equals("t.me")) {
                        if (!uri.getPath().startsWith("/$") && !uri.getPath().startsWith("/invoice/")) {
                            activity.setNavigateToPremiumBot(true);
                        } else {
                            activity.setNavigateToPremiumGiftCallback(() -> onGiftSuccess(false));
                        }
                    }
                    Browser.openUrl(activity, premiumTier.giftOption.bot_url);
                    dismiss();
                }
            } else {
                if (BillingController.getInstance().isReady() && premiumTier.googlePlayProductDetails != null) {
                    TLRPC.TL_inputStorePaymentGiftPremium giftPremium = new TLRPC.TL_inputStorePaymentGiftPremium();
                    giftPremium.user_id = MessagesController.getInstance(currentAccount).getInputUser(user);
                    ProductDetails.OneTimePurchaseOfferDetails offerDetails = premiumTier.googlePlayProductDetails.getOneTimePurchaseOfferDetails();
                    giftPremium.currency = offerDetails.getPriceCurrencyCode();
                    giftPremium.amount = (long) ((offerDetails.getPriceAmountMicros() / Math.pow(10, 6)) * Math.pow(10, BillingController.getInstance().getCurrencyExp(giftPremium.currency)));

                    BillingController.getInstance().addResultListener(premiumTier.giftOption.store_product, billingResult -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            AndroidUtilities.runOnUIThread(() -> onGiftSuccess(true));
                        }
                    });

                    TLRPC.TL_payments_canPurchaseStore req = new TLRPC.TL_payments_canPurchaseStore();
                    req.purpose = giftPremium;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (response instanceof TLRPC.TL_boolTrue) {
                            BillingController.getInstance().launchBillingFlow(getBaseFragment().getParentActivity(), AccountInstance.getInstance(currentAccount), giftPremium, Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(premiumTier.googlePlayProductDetails)
                                    .build()));
                        } else if (error != null) {
                            AlertsCreator.processError(currentAccount, error, getBaseFragment(), req);
                        }
                    }));
                }
            }
        }
    }

    private void onGiftSuccess(boolean fromGooglePlay) {
        TLRPC.UserFull full = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
        final TLObject user = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
        if (full != null) {
            if (user instanceof TLRPC.User) {
                ((TLRPC.User) user).premium = true;
                MessagesController.getInstance(currentAccount).putUser((TLRPC.User) user, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoad, ((TLRPC.User) user).id, full);
            }
        }

        if (getBaseFragment() != null) {
            List<BaseFragment> fragments = new ArrayList<>(((LaunchActivity) getBaseFragment().getParentActivity()).getActionBarLayout().getFragmentStack());

            INavigationLayout layout = getBaseFragment().getParentLayout();
            ChatActivity lastChatActivity = null;
            for (BaseFragment fragment : fragments) {
                if (fragment instanceof ChatActivity) {
                    lastChatActivity = (ChatActivity) fragment;
                    if (lastChatActivity.getDialogId() != dialogId) {
                        fragment.removeSelfFromStack();
                    }
                } else if (fragment instanceof ProfileActivity) {
                    if (fromGooglePlay && layout.getLastFragment() == fragment) {
                        fragment.finishFragment();
                    } else {
                        fragment.removeSelfFromStack();
                    }
                }
            }
            if (lastChatActivity == null || lastChatActivity.getDialogId() != dialogId) {
                Bundle args = new Bundle();
                args.putLong("user_id", dialogId);
                layout.presentFragment(new ChatActivity(args), true);
            }
        }

        dismiss();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(self ? R.string.Gift2TitleSelf2 : R.string.Gift2Title);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final long paidMessagesStarsPrice = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(dialogId);
        items.add(UItem.asCustom(-1, chatView));
        if (paidMessagesStarsPrice <= 0) {
            items.add(UItem.asCustom(-2, messageEdit));
        }
        if (starGift != null) {
            if (starGift.can_upgrade && !self) {
                items.add(UItem.asShadow(-3, null));
                items.add(UItem.asCheck(2, StarsIntroActivity.replaceStarsWithPlain(formatString(self ? R.string.Gift2UpgradeSelf : R.string.Gift2Upgrade, (int) starGift.upgrade_stars), .78f)).setChecked(upgrade));
                items.add(UItem.asShadow(-5, forceNotUpgrade ? formatString(dialogId < 0 ? R.string.Gift2NoUpgradeChannelForcedInfo : R.string.Gift2NoUpgradeForcedInfo, name) : forceUpgrade ? formatString(dialogId < 0 ? R.string.Gift2UpgradeChannelForcedInfo : R.string.Gift2UpgradeForcedInfo, name) : AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(self ? getString(R.string.Gift2UpgradeSelfInfo) : formatString(dialogId >= 0 ? R.string.Gift2UpgradeInfo : R.string.Gift2UpgradeChannelInfo, name), () -> {
                    new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider)
                        .openAsLearnMore(starGift.id, name);
                }), true)).setEnabled(!forceUpgrade && !forceNotUpgrade));
            } else {
                items.add(UItem.asShadow(-5, null));
            }
            items.add(UItem.asCheck(1, getString(self ? R.string.Gift2HideSelf : R.string.Gift2Hide)).setChecked(anonymous));
            items.add(UItem.asShadow(-6, self ? getString(R.string.Gift2HideSelfInfo) : dialogId < 0 ? getString(R.string.Gift2HideChannelInfo) : formatString(R.string.Gift2HideInfo, name)));
        } else {
            if (paidMessagesStarsPrice <= 0) {
                items.add(UItem.asShadow(-3, formatString(R.string.Gift2MessagePremiumInfo, name)));
            }
            if (premiumTier != null && premiumTier.isStarsPaymentAvailable()) {
                items.add(UItem.asCheck(3, StarsIntroActivity.replaceStarsWithPlain(formatString(R.string.Gift2MessageStars, (int) premiumTier.getStarsPrice()), .78f)).setChecked(useStars));
                final long balance = StarsController.getInstance(currentAccount).getBalance().amount;
                SpannableStringBuilder boldBalance = new SpannableStringBuilder(LocaleController.formatNumber(balance, ','));
                boldBalance.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, boldBalance.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                items.add(UItem.asShadow(-7, TextUtils.concat(
                    StarsIntroActivity.replaceStarsWithPlain(formatSpannable(R.string.Gift2MessageStarsInfo, boldBalance), .66f),
                    " ",
                    AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.Gift2MessageStarsInfoLink), () -> {
                        new StarsIntroActivity.StarsOptionsSheet(getContext(), resourcesProvider).show();
                    }), true, dp(8f / 3f), dp(1))
                )));
            }
        }
        if (reverseLayout) Collections.reverse(items);
    }

    @Override
    public void show() {
        if (messageEdit != null) {
            messageEdit.editTextEmoji.onResume();
        }
        super.show();
    }

    boolean isDismissed = false;

    @Override
    public void dismiss() {
        if (messageEdit.editTextEmoji.getEmojiPadding() > 0) {
            messageEdit.editTextEmoji.hidePopup(true);
            return;
        } else if (messageEdit.editTextEmoji.isKeyboardVisible()) {
            messageEdit.editTextEmoji.closeKeyboard();
            return;
        }
        if (messageEdit != null) {
            messageEdit.editTextEmoji.onPause();
        }

        if (auction != null) {
            GiftAuctionController.getInstance(currentAccount).unsubscribeFromGiftAuction(auction.giftId, this);
        }

        isDismissed = true;
        super.dismiss();
    }

    @Override
    public void onBackPressed() {
        if (messageEdit.editTextEmoji.getEmojiPadding() > 0) {
            messageEdit.editTextEmoji.hidePopup(true);
            return;
        } else if (messageEdit.editTextEmoji.isKeyboardVisible()) {
            messageEdit.editTextEmoji.closeKeyboard();
            return;
        }
        super.onBackPressed();
    }
}
