package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

public class SellGiftEnterPriceSheet extends BottomSheet {
    private final OutlineTextContainerView starsCountEditOutline;
    private final EditTextBoldCursor starsCountEditField;
    private final TextView starsCountEditHint;

    private final AnimatedTextView titleView;
    private final ButtonWithCounterView buttonView;
    private final AnimatedTextView dollarsEqView;
    private final TextCheckbox2Cell radioButtonCell;
    private final ImageView iconStars;
    private final ImageView iconTon;

    private final AmountUtils.Amount inputAmountMinStars;
    private final AmountUtils.Amount inputAmountMaxStars;
    private final AmountUtils.Amount inputAmountMinTON;
    private final AmountUtils.Amount inputAmountMaxTON;
    private AmountUtils.Amount inputAmount;

    private static final int ERROR_FLAG_INCORRECT_INPUT = 1;
    private static final int ERROR_FLAG_AMOUNT_TOO_SMALL = 1 << 1;
    private static final int ERROR_FLAG_AMOUNT_TOO_BIG = 1 << 2;
    private static final int ERROR_FLAG_AMOUNT_NOT_ENOUGH = 1 << 3;
    private int inputAmountError;


    public SellGiftEnterPriceSheet(
        Context context,
        Theme.ResourcesProvider resourcesProvider,
        int currentAccount,
        AmountUtils.Amount startParams,
        Utilities.Callback<AmountUtils.Amount> callback
    ) {
        super(context, true, resourcesProvider);
        this.currentAccount = currentAccount;
        smoothKeyboardAnimationEnabled = true;
        waitingKeyboard = true;

        final AppGlobalConfig config = MessagesController.getInstance(currentAccount).config;
        inputAmountMinTON = AmountUtils.Amount.fromNano(Math.max(config.tonStarGiftResaleAmountMin.get(), 10_000_000L), AmountUtils.Currency.TON);
        inputAmountMaxTON = AmountUtils.Amount.fromNano(config.tonStarGiftResaleAmountMax.get(), AmountUtils.Currency.TON);
        inputAmountMinStars = AmountUtils.Amount.fromDecimal(config.starsStarGiftResaleAmountMin.get(), AmountUtils.Currency.STARS);
        inputAmountMaxStars = AmountUtils.Amount.fromDecimal(config.starsStarGiftResaleAmountMax.get(), AmountUtils.Currency.STARS);

        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));



        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        /* Header */

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        layout.addView(headerLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        titleView = new AnimatedTextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(dp(20));
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleView.setTypeface(AndroidUtilities.bold());
        // titleView.setEllipsize(TextUtils.TruncateAt.END);
        headerLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 1f, Gravity.FILL, 18 + 4, 0, 18 + 4, 0));

        /* Body */

        LinearLayout bodyLayout = new LinearLayout(context);
        bodyLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(bodyLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        {
            starsCountEditOutline = new OutlineTextContainerView(context);
            starsCountEditField = new EditTextBoldCursor(context);
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
            starsCountEditOutline.animateSelection(true, startParams != null && !startParams.isZero(), false);
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
            starsCountEditHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            starsCountEditHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            bodyLayout.addView(starsCountEditHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 33, 4, 33, 0));
        }
        {
            radioButtonCell = new TextCheckbox2Cell(context);
            radioButtonCell.setCheckboxGravityTop();
            radioButtonCell.setTextAndValue(getString(R.string.ResellGiftPriceOnlyTON), getString(R.string.ResellGiftPriceHintOnlyTON), true, false);
            radioButtonCell.setOnClickListener(v -> {
                final AmountUtils.Currency newCurrency = inputAmount.currency == AmountUtils.Currency.TON ?
                        AmountUtils.Currency.STARS :
                        AmountUtils.Currency.TON;

                setAmount(AmountUtils.Amount.fromNano(0, newCurrency), true, false, true);
                starsCountEditField.setText("");
            });

            bodyLayout.addView(radioButtonCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 16, 0, 16));
        }

        /* Footer */

        LinearLayout footerLayout = new LinearLayout(context);
        footerLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(footerLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        buttonView = new ButtonWithCounterView(context, resourcesProvider);
        buttonView.setOnClickListener(v -> {
            if (!buttonView.isEnabled() || buttonView.isLoading()) {
                return;
            }
            buttonView.setLoading(true);
            callback.run(inputAmount);
        });

        buttonView.setText(getString(R.string.ResellGiftButton), false);
        footerLayout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 18, 0, 18, 8));

        if (startParams != null) {
            setAmount(AmountUtils.Amount.fromNano(startParams.asNano(), startParams.currency), !startParams.isZero(), true, false);
        } else {
            setAmount(AmountUtils.Amount.fromNano(0, AmountUtils.Currency.STARS), false, true, false);
        }
        setCustomView(layout);

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
    }


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

        if (getInputAmountMax().asNano() < inputAmount.asNano()) {
            inputAmountError |= ERROR_FLAG_AMOUNT_TOO_BIG;
        }
        if (!inputAmount.isZero() && getInputAmountMin().asNano() > inputAmount.asNano()) {
            inputAmountError |= ERROR_FLAG_AMOUNT_TOO_SMALL;
        }



        final boolean currencyChanged = force || oldAmount.currency != inputAmount.currency;
        final boolean amountChanged = force || oldAmount.asNano() != inputAmount.asNano();
        final boolean amountErrorChanged = force || oldAmountError != inputAmountError;

        if (amountErrorChanged) {
            starsCountEditOutline.animateError((inputAmountError & (~ERROR_FLAG_AMOUNT_NOT_ENOUGH)) == 0 ? 0 : 1);
        }
        if (currencyChanged) {
            onCurrencyChanged(animated);
        }
        if (currencyChanged || amountErrorChanged) {
            checkAmountInputText(animated);
        }
        if (currencyChanged || amountChanged || amountErrorChanged) {
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
        if (inputAmount.currency == AmountUtils.Currency.STARS) {
            titleView.setText(getString(R.string.ResellGiftTitle), animated);

            starsCountEditField.setInputType(InputType.TYPE_CLASS_NUMBER);
            starsCountEditField.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(Long.toString(getInputAmountMax().asDecimal()).length())
            });
        } else if (inputAmount.currency == AmountUtils.Currency.TON) {
            titleView.setText(getString(R.string.ResellGiftTitleTON), animated);

            starsCountEditField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            starsCountEditField.setFilters(new InputFilter[]{
                    new InputFilter.LengthFilter(Long.toString(getInputAmountMax().asDecimal()).length() + 3)
            });
        }
        radioButtonCell.checkbox.setChecked(inputAmount.currency == AmountUtils.Currency.TON, animated);

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
    }

    private void checkButtonEnabled(boolean animated) {
        final boolean newEnabled = inputAmountError == 0 && (inputAmount.asNano() > 0);
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
        if ((inputAmountError & ERROR_FLAG_AMOUNT_TOO_BIG) != 0) {
            starsCountEditOutline.setText(LocaleController.formatString(R.string.ResellGiftPriceTooMuch, getInputAmountMax().formatAsDecimalSpaced()));
        } else if ((inputAmountError & ERROR_FLAG_AMOUNT_TOO_SMALL) != 0) {
            starsCountEditOutline.setText(LocaleController.formatString(R.string.ResellGiftPriceTooSmall, getInputAmountMin().formatAsDecimalSpaced()));
        } else {
            final int key = inputAmount.currency == AmountUtils.Currency.STARS ?
                    R.string.ResellGiftPriceTitle:
                    R.string.ResellGiftPriceTitleTON;

            starsCountEditOutline.setText(getString(key));
        }
    }

    private void checkRateText(boolean animated) {
        final AppGlobalConfig config = MessagesController.getInstance(currentAccount).config;

        if (inputAmount.currency == AmountUtils.Currency.STARS) {
            final AmountUtils.Amount amount = inputAmount.applyPerMille(config.starsStarGiftResaleCommissionPermille.get());
            final CharSequence s = AndroidUtilities.replaceTags(LocaleController.formatPluralString("ResellGiftInfo", (int) amount.asDecimal()));
            starsCountEditHint.setText(s);
        } else if (inputAmount.currency == AmountUtils.Currency.TON) {
            final AmountUtils.Amount amount = inputAmount.applyPerMille(config.tonStarGiftResaleCommissionPermille.get());
            final CharSequence s = AndroidUtilities.replaceTags(LocaleController.formatString(R.string.ResellGiftInfoTON, amount.asDecimalString()));
            starsCountEditHint.setText(s);
        }


        final StringBuilder sb = new StringBuilder(10).append('~');

        final double rate = inputAmount.currency == AmountUtils.Currency.TON ?
                (MessagesController.getInstance(currentAccount).config.tonUsdRate.get()):
                (MessagesController.getInstance(currentAccount).starsUsdWithdrawRate1000 * 0.00001);

        sb.append(BillingController.getInstance().formatCurrency((long) (inputAmount.asDouble() * rate * 100), "USD", 2));

        dollarsEqView.setText(sb, animated);
    }

    private AmountUtils.Amount getInputAmountMin() {
        return inputAmount.currency == AmountUtils.Currency.TON ? inputAmountMinTON : inputAmountMinStars;
    }

    private AmountUtils.Amount getInputAmountMax() {
        return inputAmount.currency == AmountUtils.Currency.TON ? inputAmountMaxTON : inputAmountMaxStars;
    }

    @Override
    public void show() {
        super.show();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(starsCountEditField), 50);
    }
}
