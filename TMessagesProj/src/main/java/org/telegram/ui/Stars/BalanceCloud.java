package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.tlutils.AmountUtils;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

public class BalanceCloud extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    private final TextView textView1;
    private final LinkSpanDrawable.LinksTextView textView2;
    private AmountUtils.Currency currency;

    public BalanceCloud(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        this(context, currentAccount, AmountUtils.Currency.STARS, resourcesProvider);
    }

    public BalanceCloud(Context context, int currentAccount, AmountUtils.Currency currency, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        this.currency = currency;

        setOrientation(VERTICAL);
        setPadding(dp(18), dp(9), dp(18), dp(9));
        setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_undo_background, resourcesProvider)));

        textView1 = new TextView(context);
        textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView1.setTextColor(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider));
        textView1.setGravity(Gravity.CENTER);
        addView(textView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER, 0, 0, 0, 0));

        textView2 = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView2.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.Gift2MessageStarsInfoLink), () -> {
            new StarsIntroActivity.StarsOptionsSheet(context, resourcesProvider).show();
        }), true, dp(8f / 3f), dp(1)));
        textView2.setGravity(Gravity.CENTER);
        addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER, 0, 1, 0, 0));

        updateBalance(false);
    }

    public void setCurrency(AmountUtils.Currency currency, boolean animated) {
        if (this.currency != currency) {
            this.currency = currency;
            updateBalance(animated);
        }

    }

    private final ColoredImageSpan[] coloredImageSpansTon = new ColoredImageSpan[1];

    private void updateBalance(boolean animated) {
        final StarsController c = StarsController.getInstance(currentAccount, currency);
        final AmountUtils.Amount balance = c.getBalanceAmount();

        if (currency == AmountUtils.Currency.STARS) {
            textView1.setText(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatString(R.string.Gift2MessageStarsInfo, LocaleController.formatNumber(balance.asDecimal(), ',')), .60f));

            textView2.setTextColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
            textView2.setLinkTextColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
            textView2.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.Gift2MessageStarsInfoLink), () -> {
                new StarsIntroActivity.StarsOptionsSheet(getContext(), resourcesProvider).show();
            }), true, dp(8f / 3f), dp(1)));
        } else if (currency == AmountUtils.Currency.TON) {
            textView1.setText(StarsIntroActivity.replaceStarsWithPlain(true, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.Gift2MessageStarsInfoTON, balance.asDecimalString())), .60f, coloredImageSpansTon));
            coloredImageSpansTon[0].setColorKey(Theme.key_undo_cancelColor);

            final StringBuilder sb = new StringBuilder(10);
            sb.append('~');
            sb.append(BillingController.getInstance().formatCurrency((long) (balance.asDouble() * MessagesController.getInstance(currentAccount).config.tonUsdRate.get() * 100), "USD", 2));

            textView2.setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider), Theme.getColor(Theme.key_undo_background, resourcesProvider), 0.33f));
            textView2.setLinkTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider), Theme.getColor(Theme.key_undo_background, resourcesProvider), 0.33f));
            textView2.setText(sb);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return isEnabled() && super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateBalance(false);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.botStarsUpdated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starBalanceUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.botStarsUpdated);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starBalanceUpdated) {
            updateBalance(true);
        }
    }
}
