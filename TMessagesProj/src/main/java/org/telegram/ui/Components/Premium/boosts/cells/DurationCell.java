package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.BillingController;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class DurationCell extends BaseCell {

    protected final SimpleTextView totalTextView;
    private Object code;

    public DurationCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        imageView.setVisibility(GONE);

        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        totalTextView = new SimpleTextView(context);
        totalTextView.setTextSize(16);
        totalTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
        totalTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        addView(totalTextView);

        totalTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 20 : 0, 0, LocaleController.isRTL ? 0 : 20, 0));
    }

    public void setDuration(Object code, int months, int count, long price, CharSequence currency, boolean needDivider, boolean selected) {
        this.code = code;
        if (months >= 12) {
            titleTextView.setText(LocaleController.formatPluralString("Years", 1));
        } else {
            titleTextView.setText(LocaleController.formatPluralString("Months", months));
        }
        setSubtitle(BillingController.getInstance().formatCurrency(count > 0 ? (price / count) : price, currency.toString()) + " x " + count);
        totalTextView.setText(BillingController.getInstance().formatCurrency(count > 0 ? price : 0, currency.toString()));
        setDivider(needDivider);
        radioButton.setChecked(selected, false);
    }

    public Object getGifCode() {
        return code;
    }

    @Override
    protected boolean needCheck() {
        return true;
    }
}
