package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ProductDetails;
import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.BuildVars;
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
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
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
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.bots.BotPreviewsEditContainer;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.PreviewView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class SendGiftSheet extends BottomSheetWithRecyclerListView {

    private final int currentAccount;
    private final long dialogId;
    private final TL_stars.StarGift starGift;
    private final GiftPremiumBottomSheet.GiftTier premiumTier;
    private final String name;
    private final Runnable closeParentSheet;

    private final SizeNotifierFrameLayout chatView;
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

    private EditEmojiTextCell messageEdit;

    private UniversalAdapter adapter;

    public final AnimationNotificationsLocker animationsLock = new AnimationNotificationsLocker();

    public SendGiftSheet(Context context, int currentAccount, TL_stars.StarGift gift, long dialogId, Runnable closeParentSheet) {
        this(context, currentAccount, gift, null, dialogId, closeParentSheet);
    }

    public SendGiftSheet(Context context, int currentAccount, GiftPremiumBottomSheet.GiftTier premiumTier, long dialogId, Runnable closeParentSheet) {
        this(context, currentAccount, null, premiumTier, dialogId, closeParentSheet);
    }

    private SendGiftSheet(Context context, int currentAccount, TL_stars.StarGift starGift, GiftPremiumBottomSheet.GiftTier premiumTier, long dialogId, Runnable closeParentSheet) {
        super(context, null, true, false, false, false, ActionBarType.SLIDING, null);

        setImageReceiverNumLevel(0, 4);
        fixNavigationBar();
//        setSlidingActionBar();
        headerPaddingTop = dp(4);
        headerPaddingBottom = dp(-10);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.starGift = starGift;
        this.premiumTier = premiumTier;
        this.closeParentSheet = closeParentSheet;

        topPadding = 0.2f;

        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        this.name = UserObject.getForcedFirstName((TLRPC.User) user);

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
                actionCell.setTranslationY(((b - t) - actionCell.getMeasuredHeight()) / 2f - dp(8));
                actionCell.setVisiblePart(actionCell.getY(), getBackgroundSizeY());
            }
        };
        chatView.setBackgroundImage(PreviewView.getBackgroundDrawable(null, currentAccount, dialogId, Theme.isCurrentThemeDark()), false);

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

        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
        message.id = 1;
        message.dialog_id = dialogId;
        message.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
        message.peer_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
        message.action = action;

        messageObject = new MessageObject(currentAccount, message, false, false);
        actionCell.setMessageObject(messageObject, true);

        chatView.addView(actionCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 8, 0, 8));

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
            }
        });

        buttonContainer = new LinearLayout(context);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        buttonContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        final View buttonShadow = new View(context);
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
        buttonContainer.addView(buttonShadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        final float limitedProgress = Utilities.clamp(starGift == null ? 0 : (float) starGift.availability_remains / starGift.availability_total, 0.97f, 0);
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
        if (starGift != null) {
            button.setText(StarsIntroActivity.replaceStars(LocaleController.formatPluralStringComma("Gift2Send", (int) this.starGift.stars)), false);
        } else if (premiumTier != null) {
            button.setText(LocaleController.formatString(R.string.Gift2SendPremium, premiumTier.getFormattedPrice()), false);
        }
        buttonContainer.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10, 10, 10));
        button.setOnClickListener(v -> {
            if (button.isLoading()) return;
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
    }

    private TLRPC.TL_textWithEntities getMessage() {
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
            AndroidUtilities.getActivity(getContext()),
            this.starGift,
            anonymous,
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
                }
                button.setLoading(false);
            }
        );
    }

    private void buyPremiumTier() {
        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        if (user == null) {
            button.setLoading(false);
            return;
        }
        if (premiumTier.giftCodeOption != null) {
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
            BoostRepository.payGiftCode(new ArrayList<>(Arrays.asList(user)), premiumTier.giftCodeOption, null, getMessage(), fragment, result -> {
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
            return;
        }
        if (BuildVars.useInvoiceBilling()) {
            final LaunchActivity activity = LaunchActivity.instance;
            if (activity != null) {
                Uri uri = Uri.parse(premiumTier.giftOption.bot_url);
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

                TLRPC.TL_payments_canPurchasePremium req = new TLRPC.TL_payments_canPurchasePremium();
                req.purpose = giftPremium;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(()->{
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
        return getString(R.string.Gift2Title);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(-1, chatView));
        items.add(UItem.asCustom(-2, messageEdit));
        if (starGift != null) {
            items.add(UItem.asShadow(-3, null));
            items.add(UItem.asCheck(1, getString(R.string.Gift2Hide)).setChecked(anonymous));
            items.add(UItem.asShadow(-4, formatString(R.string.Gift2HideInfo, name)));
        } else {
            items.add(UItem.asShadow(-3, formatString(R.string.Gift2MessagePremiumInfo, name)));
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
