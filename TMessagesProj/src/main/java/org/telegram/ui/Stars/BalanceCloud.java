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

import java.math.BigDecimal;
import java.math.MathContext;

public class BalanceCloud extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private long chatId = -1L;
    private final Theme.ResourcesProvider resourcesProvider;

    private final TextView textView1;
    private final LinkSpanDrawable.LinksTextView textView2;
    private AmountUtils.Currency currency;

    public BalanceCloud(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        this(context, currentAccount, -1L, AmountUtils.Currency.STARS, resourcesProvider);
    }

    public BalanceCloud(Context context, int currentAccount, long chatId, Theme.ResourcesProvider resourcesProvider) {
        this(context, currentAccount, chatId, AmountUtils.Currency.STARS, resourcesProvider);
    }

    public BalanceCloud(Context context, int currentAccount, long chatId, AmountUtils.Currency currency, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.chatId = chatId;
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

    public void setChatId(long chatId) {
        this.chatId = chatId;
        updateBalance(true);
    }

    private final ColoredImageSpan[] coloredImageSpansTon = new ColoredImageSpan[1];

    private void updateBalance(boolean animated) {
        final StarsController c = StarsController.getInstance(currentAccount, currency);
        final AmountUtils.Amount balance = c.getBalanceAmount();

        if (currency == AmountUtils.Currency.STARS) {
            textView1.setText(StarsIntroActivity.replaceStarsWithPlain(LocaleController.formatString(
                    chatId == -1L ? R.string.Gift2MessageStarsInfo : R.string.Gift2MessageChannelStarsInfo,
                    LocaleController.formatNumber(
                            chatId == -1L ? balance.asDecimal() : (int) BotStarsController.getInstance(currentAccount).getBotStarsBalance(-chatId).amount, ',')
                    ),
                    .60f
                    )
            );

            textView2.setTextColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
            textView2.setLinkTextColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
            textView2.setText(AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.Gift2MessageStarsInfoLink), () -> {
                new StarsIntroActivity.StarsOptionsSheet(getContext(), resourcesProvider).show();
            }), true, dp(8f / 3f), dp(1)));
            textView2.setVisibility(chatId == -1L ? VISIBLE : GONE);
        } else if (currency == AmountUtils.Currency.TON) {
            textView1.setText(StarsIntroActivity.replaceStarsWithPlain(true, AndroidUtilities.replaceTags(
                    chatId == -1L
                            ? LocaleController.formatString(R.string.Gift2MessageStarsInfoTON, balance.asDecimalString())
                            : LocaleController.formatString(R.string.Gift2MessageChannelStarsInfoTON, new BigDecimal(BotStarsController.getInstance(currentAccount).getBotStarsBalance(-chatId).amount).divide(BigDecimal.valueOf(1_000_000_000), MathContext.UNLIMITED).stripTrailingZeros().toPlainString())
            ), .60f, coloredImageSpansTon));
            coloredImageSpansTon[0].setColorKey(Theme.key_undo_cancelColor);

            final StringBuilder sb = new StringBuilder(10);
            sb.append('~');
            sb.append(BillingController.getInstance().formatCurrency((long) ((chatId == -1L ? balance.asDouble() : (double) BotStarsController.getInstance(currentAccount).getBotStarsBalance(-chatId).amount) * MessagesController.getInstance(currentAccount).config.tonUsdRate.get() * 100), "USD", 2));

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
