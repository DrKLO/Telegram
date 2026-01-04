package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.formatSpannable;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatNumber;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarGiftSheet.addAttributeRow;
import static org.telegram.ui.Stars.StarsController.findAttribute;
import static org.telegram.ui.Stars.StarsIntroActivity.replaceStars;
import static org.telegram.ui.Stars.StarsIntroActivity.replaceStarsWithPlain;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonSpan;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.HorizontalRoundTabsLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TableView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.TON.TONIntroActivity;

import java.util.ArrayList;

public class GiftOfferSheet extends BottomSheetWithRecyclerListView {
    private final @Nullable BalanceCloud balanceCloud;
    private final TL_stars.TL_starGiftUnique giftUnique;
    private final String giftName;
    private final long dialogId;

    private final @Nullable HorizontalRoundTabsLayout currencyTabsView;
    private final OutlineTextContainerView starsCountEditOutline;
    private final EditTextBoldCursor starsCountEditField;
    private final TextView starsCountEditHint;

    private final EditTextBoldCursor publishingTimeField;
    private final TextView publishingTimeHint;
    private final ButtonWithCounterView buttonView;
    private final AnimatedTextView dollarsEqView;
    private final ImageView iconStars;
    private final ImageView iconTon;

    private final AmountUtils.AmountLimits inputAmountLimits = new AmountUtils.AmountLimits();
    private AmountUtils.Amount inputAmount;

    private int selectedDuration;

    private static final int ERROR_FLAG_INCORRECT_INPUT = 1;
    private static final int ERROR_FLAG_AMOUNT_TOO_SMALL = 1 << 1;
    private static final int ERROR_FLAG_AMOUNT_TOO_BIG = 1 << 2;
    private static final int ERROR_FLAG_AMOUNT_NOT_ENOUGH = 1 << 3;

    private static final int[] ALLOWED_DURATIONS = { /*120,*/ 21600, 43200, 86400, 129600, 172800, 259200 };

    private int inputAmountError;
    private boolean balanceCloudVisible;

    @Override
    protected boolean isTouchOutside(float x, float y) {
        if (balanceCloudVisible && balanceCloud != null && x >= balanceCloud.getX() && x <= balanceCloud.getX() + balanceCloud.getWidth() && y >= balanceCloud.getY() && y <= balanceCloud.getY() + balanceCloud.getHeight())
            return false;
        return super.isTouchOutside(x, y);
    }

    private final Runnable closeParentSheet;

    public GiftOfferSheet(
        Context context,
        int currentAccount,
        long dialogId,
        TL_stars.TL_starGiftUnique giftUnique,
        Theme.ResourcesProvider resourcesProvider,
        Runnable closeParentSheet
    ) {
        super(context, null, true, false,
            false, false, ActionBarType.SLIDING, resourcesProvider);
        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);
        topPadding = 0.2f;

        this.dialogId = dialogId;
        this.giftUnique = giftUnique;
        this.giftName = giftUnique.title + " #" + LocaleController.formatNumber(giftUnique.num, ',');
        this.closeParentSheet = closeParentSheet;

        waitingKeyboard = true;
        smoothKeyboardAnimationEnabled = true;

        boolean allowTON = StarsController.getTonInstance(currentAccount).canUseTon();

        if (dialogId > 0) {
            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            if (userFull == null) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (user != null) {
                    MessagesController.getInstance(currentAccount).loadFullUser(user, 0, false);
                }
            }
        }

        final AppGlobalConfig config = MessagesController.getInstance(currentAccount).config;

        final AmountUtils.Amount minOfferStars = AmountUtils.Amount.fromDecimal(giftUnique.offer_min_stars, AmountUtils.Currency.STARS);
        final AmountUtils.Amount maxOfferStars = AmountUtils.Amount.fromDecimal(Math.max(
            minOfferStars.asDecimal() * 2, config.starsSuggestedPostAmountMax.get()), AmountUtils.Currency.STARS);

        final AmountUtils.Amount minOfferTon = minOfferStars.convertTo(AmountUtils.Currency.TON).round(2);
        final AmountUtils.Amount maxOfferTon = AmountUtils.Amount.fromNano(Math.max(
            minOfferTon.asNano() * 2, config.tonSuggestedPostAmountMax.get()), AmountUtils.Currency.TON);

        inputAmountLimits.set(minOfferStars, maxOfferStars);
        inputAmountLimits.set(minOfferTon, maxOfferTon);

        balanceCloud = new BalanceCloud(context, currentAccount, resourcesProvider);
        balanceCloud.setScaleX(0.6f);
        balanceCloud.setScaleY(0.6f);
        balanceCloud.setAlpha(0.0f);
        balanceCloud.setEnabled(false);
        balanceCloud.setClickable(false);
        container.addView(balanceCloud, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));
        ScaleStateListAnimator.apply(balanceCloud);
        balanceCloud.setOnClickListener(v -> {
            if (inputAmount.currency == AmountUtils.Currency.STARS) {
                new StarsIntroActivity.StarsOptionsSheet(context, resourcesProvider).show();
            }
        });

        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        LinearLayout layout = new LinearLayout(context);
        layout.setClickable(true);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, dp(12), 0, dp(16));



        starsCountEditField = new EditTextBoldCursor(context);

        /* Tabs */

        if (allowTON) {
            currencyTabsView = new HorizontalRoundTabsLayout(context);
            ArrayList<CharSequence> tabs = new ArrayList<>();
            tabs.add(getString(R.string.SuggestedOfferStars));
            tabs.add(getString(R.string.SuggestedOfferTON));
            currencyTabsView.setTabs(tabs, x -> {
                final AmountUtils.Currency currency = x == 0 ?
                        AmountUtils.Currency.STARS :
                        AmountUtils.Currency.TON;

                setAmount(AmountUtils.Amount.fromNano(0, currency), true, false, true);
                starsCountEditField.setText("");
            });
            layout.addView(currencyTabsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 18, 0, 18, 18));
        } else {
            currencyTabsView = null;
        }

        /* Body */

        LinearLayout bodyLayout = new LinearLayout(context);
        bodyLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(bodyLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));



        {
            starsCountEditOutline = new OutlineTextContainerView(context);
            starsCountEditField.setCursorSize(dp(20));
            starsCountEditField.setCursorWidth(1.5f);
            starsCountEditField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            starsCountEditField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            starsCountEditField.setMaxLines(1);
            starsCountEditField.setBackground(null);
            starsCountEditField.setPadding(dp(42), dp(16), dp(16), dp(16));
            starsCountEditField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            starsCountEditField.requestFocus();

            starsCountEditOutline.setLeftPadding(dp(28));
            starsCountEditOutline.attachEditText(starsCountEditField);
            starsCountEditOutline.animateSelection(true, false, false);
            starsCountEditOutline.setForceUseCenter2(true);

            starsCountEditField.setOnFocusChangeListener((v, hasFocus) ->
                starsCountEditOutline.animateSelection(hasFocus, !TextUtils.isEmpty(starsCountEditField.getText())));

            starsCountEditOutline.addView(starsCountEditField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
            bodyLayout.addView(starsCountEditOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 18, 0, 18, 0));

            iconStars = new ImageView(context);
            iconStars.setImageResource(R.drawable.star_small_inner);
            starsCountEditOutline.addView(iconStars, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));

            iconTon = new ImageView(context);
            iconTon.setImageResource(R.drawable.ton);
            iconTon.setColorFilter(0xFF3391d4);
            starsCountEditOutline.addView(iconTon, LayoutHelper.createFrame(22, 22, Gravity.LEFT | Gravity.CENTER_VERTICAL, 14, 0, 0, 0));

            dollarsEqView = new AnimatedTextView(context);
            dollarsEqView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            dollarsEqView.setTextSize(dp(13));
            dollarsEqView.setGravity(Gravity.RIGHT);
            starsCountEditOutline.addView(dollarsEqView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

            starsCountEditHint = new TextView(context);
            starsCountEditHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            bodyLayout.addView(starsCountEditHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 33, 4, 33, 0));
        }
        {
            publishingTimeField = new EditTextBoldCursor(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    return false;
                }
            };
            publishingTimeField.setCursorSize(dp(20));
            publishingTimeField.setCursorWidth(1.5f);
            publishingTimeField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            publishingTimeField.setMaxLines(1);
            publishingTimeField.setBackground(null);
            publishingTimeField.setPadding(dp(16), dp(16), dp(16), dp(16));
            publishingTimeField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            publishingTimeField.setFocusable(false);
            publishingTimeField.setClickable(false);
            publishingTimeField.setEnabled(false);

            OutlineTextContainerView publishingTimeOutline = new OutlineTextContainerView(context);
            publishingTimeOutline.setText(getString(R.string.GiftOfferDuration));
            publishingTimeOutline.attachEditText(publishingTimeField);
            publishingTimeOutline.addView(publishingTimeField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 48, 0));
            ScaleStateListAnimator.apply(publishingTimeOutline, .02f, 1.2f);
            publishingTimeOutline.setOnClickListener(v -> {
                int index = 0;
                String[] positions = new String[ALLOWED_DURATIONS.length];
                for (int a = 0; a < ALLOWED_DURATIONS.length; a++) {
                    positions[a] = formatPluralString("GiftOfferHours", ALLOWED_DURATIONS[a] / 3600);
                    if (ALLOWED_DURATIONS[a] == selectedDuration) {
                        index = a;
                    }
                }

                AlertsCreator.createCustomPicker(context, getString(R.string.GiftOfferDuration), index, positions, s -> {
                    setSelectedDuration(ALLOWED_DURATIONS[s], true);
                });
            });
            bodyLayout.addView(publishingTimeOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 58, 18, 18, 18, 0));

            ImageView iconArrow = new ImageView(context);
            iconArrow.setImageResource(R.drawable.arrow_more);
            iconArrow.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage, resourcesProvider), PorterDuff.Mode.SRC_IN));
            publishingTimeOutline.addView(iconArrow, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

            publishingTimeHint = new TextView(context);
            publishingTimeHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            publishingTimeHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);

            bodyLayout.addView(publishingTimeHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 33, 4, 33, 0));
        }



        /* Footer */

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        buttonView.setOnClickListener(v -> {
            if (!buttonView.isEnabled()) {
                return;
            }
            if (MessagesController.getInstance(currentAccount).isFrozen()) {
                AccountFrozenAlert.show(currentAccount);
                return;
            }

            final StarsController starsController = StarsController.getInstance(currentAccount, inputAmount.currency);
            final AmountUtils.Amount balance = starsController.balanceAvailable() ?
                AmountUtils.Amount.of(starsController.getBalance()) : null;

            if ((balance == null || balance.asNano() < inputAmount.asNano())) {
                if (inputAmount.currency == AmountUtils.Currency.STARS) {
                    new StarsIntroActivity.StarsNeededSheet(context, resourcesProvider, inputAmount.asDecimal(), StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_BUY_RESALE, null, null, dialogId).show();
                } else if (inputAmount.currency == AmountUtils.Currency.TON){
                    new TONIntroActivity.StarsNeededSheet(context, resourcesProvider, inputAmount, true, null).show();
                }
            } else {
                openConfirmAlert();
            }
        });

        setAmount(AmountUtils.Amount.fromNano(0, AmountUtils.Currency.STARS), false, true, false);
        setSelectedDuration(86400, false);

        starsCountEditField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                final boolean isEmpty = s == null || s.toString().isEmpty() || ".".equals(s.toString());

                if (!isEmpty) {
                    String str = s.toString();

                    int dotIndex = str.indexOf('.');
                    if (dotIndex >= 0) {
                        int decimals = str.length() - dotIndex - 1;
                        if (decimals > 2) {
                            s.delete(dotIndex + 2 + 1, str.length());
                        }
                    }
                }

                final AmountUtils.Amount newAmount = !isEmpty ?
                        AmountUtils.Amount.fromDecimal(s.toString(), inputAmount.currency):
                        AmountUtils.Amount.fromNano(0, inputAmount.currency);

                setAmount(newAmount, false, false, true);
                starsCountEditOutline.animateSelection(starsCountEditField.isFocused(), !TextUtils.isEmpty(starsCountEditField.getText()));
            }
        });



        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 16, 16, 16);
        lp.leftMargin += backgroundPaddingLeft;
        lp.rightMargin += backgroundPaddingLeft;
        containerView.addView(buttonView, lp);

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, dp(16 + 48));
        recyclerListView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        mainItem = UItem.asCustom(layout);
        adapter.update(false);
    }

    private void setSelectedDuration(int selectedDuration, boolean animated) {
        if (this.selectedDuration != selectedDuration) {
            this.selectedDuration = selectedDuration;
            this.publishingTimeField.setText(formatPluralString("GiftOfferHours", selectedDuration / 3600));
        }
        checkButtonEnabled(animated);
    }

    private final ColoredImageSpan[] spanRefStars = new ColoredImageSpan[1];
    private final ColoredImageSpan[] spanRefTon = new ColoredImageSpan[1];

    private void setAmount(@Nullable AmountUtils.Amount amount, boolean updateEditField, boolean force, boolean animated) {
        AmountUtils.Amount oldAmount = inputAmount;
        int oldAmountError = inputAmountError;

        inputAmountError = 0;
        if (amount != null) {
            inputAmount = amount;
        } else {
            inputAmount = AmountUtils.Amount.fromNano(0, inputAmount.currency);
            inputAmountError |= ERROR_FLAG_INCORRECT_INPUT;
        }

        if (inputAmountLimits.getMax(inputAmount.currency).asNano() < inputAmount.asNano()) {
            inputAmountError |= ERROR_FLAG_AMOUNT_TOO_BIG;
        }
        if (!inputAmount.isZero() && inputAmountLimits.getMin(inputAmount.currency).asNano() > inputAmount.asNano()) {
            inputAmountError |= ERROR_FLAG_AMOUNT_TOO_SMALL;
        }

        final boolean currencyChanged = force || oldAmount.currency != inputAmount.currency;
        final boolean amountChanged = force || oldAmount.asNano() != inputAmount.asNano();
        final boolean amountErrorChanged = force || oldAmountError != inputAmountError;

        if (currencyChanged) {
            onCurrencyChanged(animated);
        }
        if (currencyChanged || amountErrorChanged) {
            checkAmountInputText(animated);
            checkAmountInputTextHint(animated);
        }
        if (currencyChanged || amountChanged || amountErrorChanged) {
            checkButtonOfferText(animated);
            checkButtonEnabled(animated);
        }
        if (currencyChanged || amountChanged) {
            checkRateText(animated);
        }

        if (updateEditField && amountChanged) {
            String textToSet = inputAmount.asDecimalString();
            starsCountEditField.setText(textToSet);
            starsCountEditField.setSelection(textToSet.length());
        }
    }

    private void onCurrencyChanged(boolean animated) {
        if (currencyTabsView != null) {
            currencyTabsView.setSelectedIndex(inputAmount.currency == AmountUtils.Currency.STARS ? 0 : 1, animated);
        }

        final String userName = DialogObject.getShortName(dialogId);
        if (inputAmount.currency == AmountUtils.Currency.STARS) {
            publishingTimeHint.setText(replaceTags(formatString(R.string.GiftOfferDurationInfoStars, userName)));

            starsCountEditField.setInputType(InputType.TYPE_CLASS_NUMBER);
            starsCountEditField.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(Long.toString(inputAmountLimits.getMax(inputAmount.currency).asDecimal()).length())
            });
        } else if (inputAmount.currency == AmountUtils.Currency.TON) {
            publishingTimeHint.setText(replaceTags(formatString(R.string.GiftOfferDurationInfoTON, userName)));

            starsCountEditField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            starsCountEditField.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(Long.toString(inputAmountLimits.getMax(inputAmount.currency).asDecimal()).length() + 3)
            });
        }

        if (animated) {
            iconStars.animate()
                    .alpha(inputAmount.currency == AmountUtils.Currency.STARS ? 1f : 0f)
                    .scaleX(inputAmount.currency == AmountUtils.Currency.STARS ? 1f : 0f)
                    .scaleY(inputAmount.currency == AmountUtils.Currency.STARS ? 1f : 0f)
                    .setDuration(180L)
                    .start();
            iconTon.animate()
                    .alpha(inputAmount.currency == AmountUtils.Currency.TON ? 1f : 0f)
                    .scaleX(inputAmount.currency == AmountUtils.Currency.TON ? 1f : 0f)
                    .scaleY(inputAmount.currency == AmountUtils.Currency.TON ? 1f : 0f)
                    .setDuration(180L)
                    .start();
        } else {
            iconStars.setAlpha(inputAmount.currency == AmountUtils.Currency.STARS ? 1f : 0f);
            iconTon.setAlpha(inputAmount.currency == AmountUtils.Currency.TON ? 1f : 0f);
        }

        if (balanceCloud != null) {
            balanceCloud.setCurrency(inputAmount.currency, animated);
        }
    }

    private boolean isFullyVisible;

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        isFullyVisible = true;
        checkBalanceCloudVisibility();
    }

    @Override
    protected void onContainerTranslationYChanged(float translationY) {
        super.onContainerTranslationYChanged(translationY);
        checkBalanceCloudVisibility();
    }

    private void checkBalanceCloudVisibility() {
        final boolean balanceCloudVisible = isFullyVisible && !isDismissed() && balanceCloud != null && containerView.getY() > dp(32) || currencyTabsView == null;
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

    private void checkButtonOfferText(boolean animated) {
        final boolean isTon = inputAmount.currency == AmountUtils.Currency.TON;
        buttonView.setText(StarsIntroActivity.replaceStars(isTon,
            LocaleController.formatString(R.string.GiftOfferButtonStars, isTon ? inputAmount.asDecimalString() :
                LocaleController.formatNumber(inputAmount.asDecimal(), ',')),
            isTon ? spanRefTon: spanRefStars
        ), animated);
    }

    private void checkButtonEnabled(boolean animated) {
        final boolean newEnabled = inputAmountError == 0 && inputAmount.asNano() > 0;
        if (buttonView.isEnabled() != newEnabled) {
            this.buttonView.setEnabled(newEnabled);
            this.buttonView.setClickable(newEnabled);
            if (animated) {
                this.buttonView.animate().alpha(newEnabled ? 1f : 0.6f).setDuration(180L).start();
            } else {
                this.buttonView.setAlpha(newEnabled ? 1f : 0.6f);
            }
        }
    }

    private void checkAmountInputText(boolean ignoredAnimated) {
        final int key = inputAmount.currency == AmountUtils.Currency.STARS ?
                R.string.GiftOfferStarsToOffer :
                R.string.GiftOfferTONToOffer;

        starsCountEditOutline.setText(getString(key));
    }

    private void checkAmountInputTextHint(boolean ignoredAnimated) {
        final AmountUtils.Currency currency = inputAmount.currency;

        if ((inputAmountError & ERROR_FLAG_AMOUNT_TOO_BIG) != 0) {
            final int key = currency == AmountUtils.Currency.STARS ?
                R.string.GiftOfferStarsToOfferInfoIsHigh :
                R.string.GiftOfferTONToOfferInfoIsHigh;
            starsCountEditHint.setText(replaceTags(formatString(key,
                inputAmountLimits.getMax(currency).asFormatString(), giftName)));
        } else if ((inputAmountError & ERROR_FLAG_AMOUNT_TOO_SMALL) != 0) {
            final int key = currency == AmountUtils.Currency.STARS ?
                R.string.GiftOfferStarsToOfferInfoIsLow :
                R.string.GiftOfferTONToOfferInfoIsLow;
            starsCountEditHint.setText(replaceTags(formatString(key,
                inputAmountLimits.getMin(currency).asFormatString(), giftName)));
        } else {
            final int key = inputAmount.currency == AmountUtils.Currency.STARS ?
                R.string.GiftOfferStarsToOfferInfo :
                R.string.GiftOfferTONToOfferInfo;

            starsCountEditHint.setText(replaceTags(formatString(key, giftName)));
        }

        starsCountEditHint.setTextColor(getThemedColor((inputAmountError & (~ERROR_FLAG_AMOUNT_NOT_ENOUGH)) == 0 ?
            Theme.key_windowBackgroundWhiteGrayText : Theme.key_text_RedBold));
    }

    private void checkRateText(boolean animated) {
        final StringBuilder sb = new StringBuilder(10).append('~');

        final double rate = inputAmount.currency == AmountUtils.Currency.TON ?
                (MessagesController.getInstance(currentAccount).config.tonUsdRate.get()):
                (MessagesController.getInstance(currentAccount).starsUsdWithdrawRate1000 * 0.00001);

        sb.append(BillingController.getInstance().formatCurrency((long) (inputAmount.asDouble() * rate * 100), "USD", 2));

        dollarsEqView.setText(sb, animated);
    }

    @Override
    public void show() {
        super.show();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(starsCountEditField), 50);
    }

    /* * */

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.GiftOfferToBuyTitle);
    }

    private UniversalAdapter adapter;
    private final UItem mainItem;

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (mainItem != null) {
            items.add(mainItem);
        }
    }



    private void openConfirmAlert() {
        final String amountFmt = inputAmount.asFormatString();
        // final String amountFmtFee = getFee().asFormatString();
        // final String amountFmtFull = getFullOfferWithFee().asFormatString();
        final boolean isTon = inputAmount.currency == AmountUtils.Currency.TON;

        final LinearLayout topView = new LinearLayout(getContext());
        topView.setOrientation(LinearLayout.VERTICAL);

        final TextView titleView = new TextView(getContext());
        titleView.setText(getString(R.string.GiftOfferConfirmSend));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        topView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 14));

        final TextView textView = new TextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(AndroidUtilities.replaceTags(inputAmount.currency == AmountUtils.Currency.STARS ?
            formatString(R.string.GiftOfferTransferInfoTextStars, amountFmt, DialogObject.getShortName(dialogId), giftName) :
            formatString(R.string.GiftOfferTransferInfoTextTON, amountFmt, DialogObject.getShortName(dialogId), giftName)
        ));
        topView.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 4));
        final TableView tableView = new TableView(getContext(), resourcesProvider);

        final long paywall = MessagesController.getInstance(currentAccount).getSendPaidMessagesStars(dialogId);
        final AmountUtils.Amount paywallAmount = AmountUtils.Amount.fromDecimal(paywall, AmountUtils.Currency.STARS);

        tableView.addRow(
            getString(R.string.GiftOfferRowOffer),
            replaceStarsWithPlain(isTon, formatString(R.string.GiftOfferAmount, amountFmt), 0.8f));
        if (paywall > 0) {
            tableView.addRow(getString(R.string.GiftOfferRowFee),
                replaceStarsWithPlain(formatString(R.string.GiftOfferAmount, paywallAmount.asFormatString()), 0.8f));
        }
        tableView.addRow(
            getString(R.string.GiftOfferRowDuration),
            formatPluralString("GiftOfferHours", selectedDuration / 3600));
        topView.addView(tableView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 23, 16, 23, 4));

        final long randomId = SendMessagesHelper.getInstance(currentAccount).getNextRandomId();

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        if (paywall == 0) {
            ssb.append(replaceStars(isTon, formatString(R.string.GiftOfferPay, amountFmt)));
        } else {
            if (isTon) {
                final CharSequence tonFmt = replaceStars(true, formatString(R.string.GiftOfferPayMultiPart, amountFmt));
                final CharSequence starsFmt = replaceStars(formatString(R.string.GiftOfferPayMultiPart, paywallAmount.asFormatString()));
                ssb.append(LocaleController.formatSpannable(R.string.GiftOfferPayMulti, tonFmt, starsFmt));
            } else {
                String fmt = AmountUtils.Amount.fromNano(inputAmount.asNano() + paywallAmount.asNano(), AmountUtils.Currency.STARS).asFormatString();
                ssb.append(replaceStars(formatString(R.string.GiftOfferPay, fmt)));
            }
        }

        new AlertDialog.Builder(getContext(), resourcesProvider)
            .setView(topView)
            .setPositiveButton(ssb, (di, w) -> {
                if (paywall > 0) {
                    final StarsController starsController = StarsController.getInstance(currentAccount, AmountUtils.Currency.STARS);
                    final AmountUtils.Amount balance = starsController.balanceAvailable() ? AmountUtils.Amount.of(starsController.getBalance()) : null;
                    final AmountUtils.Amount needed;
                    if (isTon) {
                        needed = AmountUtils.Amount.fromDecimal(paywall, AmountUtils.Currency.STARS);
                    } else {
                        needed = AmountUtils.Amount.fromNano(inputAmount.asNano() + paywallAmount.asNano(), AmountUtils.Currency.STARS);
                    }
                    if ((balance == null || balance.asNano() < needed.asNano())) {
                        new StarsIntroActivity.StarsNeededSheet(getContext(), resourcesProvider, needed.asDecimal(), StarsIntroActivity.StarsNeededSheet.TYPE_STAR_GIFT_BUY_RESALE, null, null, dialogId).show();
                        return;
                    }
                }


                final Browser.Progress progress = di.makeButtonLoading(AlertDialog.BUTTON_POSITIVE);
                progress.init();

                TL_payments.TL_sendStarGiftOffer req = new TL_payments.TL_sendStarGiftOffer();
                req.price = inputAmount.toTl();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                req.duration = selectedDuration;
                req.slug = giftUnique.slug;
                req.random_id = randomId;
                if (paywall > 0) {
                    req.flags |= TLObject.FLAG_0;
                    req.allow_paid_stars = paywall;
                }

                ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, (res, err) -> {
                    if (res != null && err == null) {
                        MessagesController.getInstance(currentAccount).processUpdates(res, false);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (closeParentSheet != null) {
                            closeParentSheet.run();
                        }
                        progress.end();
                        di.dismiss();
                        dismiss();

                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            if (res != null) {
                                BulletinFactory.of(lastFragment)
                                        .createSimpleBulletin(R.raw.forward, getString(R.string.GiftOfferSentTitle), AndroidUtilities.replaceTags(formatString(R.string.GiftOfferSentText, giftName, DialogObject.getShortName(dialogId))))
                                        .ignoreDetach()
                                        .show();
                            } else {
                                BulletinFactory.of(lastFragment).showForError(err);
                            }
                        }
                    });
                });
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
            .setShowStarsBalance(true)
            .show();
    }

    public static void openOfferAcceptAlert(BaseFragment fragment, Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, long dialogId, int msgId, TLRPC.TL_messageActionStarGiftPurchaseOffer offer) {
        final AmountUtils.Amount amount = AmountUtils.Amount.ofSafe(offer.price);
        final AmountUtils.Amount amountWithFee = getAmountMinusFee(currentAccount, amount);

        final TL_stars.StarGift gift = offer.gift;
        final String giftName = gift.title + " #" + LocaleController.formatNumber(gift.num, ',');

        final TLObject obj;
        if (dialogId >= 0) {
            obj = MessagesController.getInstance(currentAccount).getUser(dialogId);
        } else {
            obj = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        }


        final String amountFmt = amount.asFormatString();
        final String amountMinusFeeFmt = amountWithFee.asFormatString();

        final boolean isTon = amount.currency == AmountUtils.Currency.TON;

        final LinearLayout topView = new LinearLayout(context);
        topView.setOrientation(LinearLayout.VERTICAL);
        topView.addView(new StarGiftSheet.GiftTransferTopView(context, gift, obj), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, -4, 0, 0));

        final TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(AndroidUtilities.replaceTags(amount.currency == AmountUtils.Currency.STARS ?
                formatString(R.string.GiftOfferTransferInfoTextSellStars, amountFmt, DialogObject.getShortName(dialogId), giftName, amountMinusFeeFmt) :
                formatString(R.string.GiftOfferTransferInfoTextSellTON, amountFmt, DialogObject.getShortName(dialogId), giftName, amountMinusFeeFmt)
        ));
        topView.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 4));

        final FrameLayout tableLayout = new FrameLayout(context);
        tableLayout.setClipChildren(false);
        tableLayout.setClipToPadding(false);
        final TableView tableView = new TableView(context, resourcesProvider);
        tableLayout.addView(tableView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));


        /**/

        addAttributeRow(tableView, findAttribute(gift.attributes, TL_stars.starGiftAttributeModel.class));
        addAttributeRow(tableView, findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class));
        addAttributeRow(tableView, findAttribute(gift.attributes, TL_stars.starGiftAttributePattern.class));

        topView.addView(tableLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 23, 16, 23, 4));

        final double exp = Math.pow(10, BillingController.getInstance().getCurrencyExp("USD"));
        final double usd = gift.value_usd_amount / exp;
        final AmountUtils.Amount value = AmountUtils.Amount.fromUsd(usd, amount.currency);
        if (value.asDouble() > 0 && gift.value_usd_amount > 0) {
            final CharSequence buttonHint;
            final int percent;
            final boolean badBrice;
            if (value.asNano() >= amountWithFee.asNano()) {
                percent = (int) Math.round((1 - amountWithFee.asDouble() / value.asDouble()) * 100);
                buttonHint = replaceTags(formatString(R.string.GiftOfferAmountLowerHint2, percent + "%", gift.title));
                badBrice = percent > 10;
            } else {
                percent = (int) Math.round((amountWithFee.asDouble() / value.asDouble() - 1) * 100);
                buttonHint = replaceTags(formatString(R.string.GiftOfferAmountHigherHint2, percent + "%", gift.title));
                badBrice = false;
            }

            final TextView hintView = new TextView(context);
            hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            hintView.setGravity(Gravity.CENTER);
            hintView.setText(buttonHint);
            hintView.setTextColor(Theme.getColor(badBrice ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            topView.addView(hintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 40, 12, 40, 9));
        }







        new AlertDialog.Builder(context, resourcesProvider)
                .setView(topView)
                .setPositiveButton(replaceStars(isTon, formatString(R.string.GiftOfferSellFor, amountMinusFeeFmt)), (di, w) -> {
                    final Browser.Progress progress = di.makeButtonLoading(AlertDialog.BUTTON_POSITIVE);
                    progress.init();

                    TL_payments.TL_resolveStarGiftOffer req = new TL_payments.TL_resolveStarGiftOffer();
                    req.offer_msg_id = msgId;

                    ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, (res, err) -> {
                        if (res != null && err == null) {
                            MessagesController.getInstance(currentAccount).processUpdates(res, false);
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (fragment != null && err != null) {
                                BulletinFactory.of(fragment).showForError(err);
                            }
                            if (fragment instanceof ChatActivity && err == null) {
                                ((ChatActivity) fragment).startFireworks();
                            }

                            progress.end();
                            di.dismiss();
                        });
                    });
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create()
                .show();
    }

    private static AmountUtils.Amount getAmountMinusFee(int currentAccount, AmountUtils.Amount amount) {
        final AmountUtils.Currency currency = amount.currency;
        final int permille = currency == AmountUtils.Currency.STARS ?
            MessagesController.getInstance(currentAccount).config.starsStarGiftResaleCommissionPermille.get():
            MessagesController.getInstance(currentAccount).config.tonStarGiftResaleCommissionPermille.get();

        return AmountUtils.Amount.fromNano(amount.asNano() * permille / 1000, currency);
    }
}
