package org.telegram.ui.Components.Premium.boosts.cells.selector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.cells.BaseCell;

@SuppressLint("ViewConstructor")
public class SelectorCountryCell extends BaseCell {

    private final CheckBox2 checkBox;
    private TLRPC.TL_help_country country;

    public SelectorCountryCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        titleTextView.setTypeface(AndroidUtilities.bold());
        radioButton.setVisibility(View.GONE);
        imageView.setVisibility(View.GONE);
        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_checkboxDisabled, Theme.key_dialogRoundCheckBoxCheck);
        checkBox.setDrawUnchecked(true);
        checkBox.setDrawBackgroundAsArc(10);
        addView(checkBox);
        checkBox.setChecked(false, false);
        checkBox.setLayoutParams(LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 13, 0, 14, 0));
    }

    protected void updateLayouts() {
        titleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : 52, 0, LocaleController.isRTL ? 52 : 20, 0));
        subtitleTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 20 : 52, 0, LocaleController.isRTL ? 52 : 20, 0));
        radioButton.setLayoutParams(LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 15 : 20, 0, LocaleController.isRTL ? 20 : 15, 0));
    }

    @Override
    protected int getFullHeight() {
        return 44;
    }

    @Override
    protected int dividerPadding() {
        return 22;
    }

    public void setCountry(TLRPC.TL_help_country country, boolean divider) {
        this.country = country;
        titleTextView.setText(Emoji.replaceEmoji(getCountryNameWithFlag(country), titleTextView.getPaint().getFontMetricsInt(), false));
        setDivider(divider);
    }


    private CharSequence getCountryNameWithFlag(TLRPC.TL_help_country country) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String flag = LocaleController.getLanguageFlag(country.iso2);
        if (flag != null) {
            sb.append(flag).append(" ");
            sb.setSpan(new SpaceDrawable(16), flag.length(), flag.length() + 1, 0);
        } else {
            sb.append(" ");
            sb.setSpan(new SpaceDrawable(34), 0, 1, 0);
        }
        sb.append(country.default_name);
        return sb;
    }

    public TLRPC.TL_help_country getCountry() {
        return country;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox.getVisibility() == View.VISIBLE) {
            checkBox.setChecked(checked, animated);
        }
    }

    public void setCheckboxAlpha(float alpha, boolean animated) {
        if (animated) {
            if (Math.abs(checkBox.getAlpha() - alpha) > .1) {
                checkBox.animate().cancel();
                checkBox.animate().alpha(alpha).start();
            }
        } else {
            checkBox.animate().cancel();
            checkBox.setAlpha(alpha);
        }
    }

    @Override
    protected boolean needCheck() {
        return true;
    }

    private static class SpaceDrawable extends ReplacementSpan {
        private final int size;

        public SpaceDrawable(int size) {
            this.size = size;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            return AndroidUtilities.dp(size);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {

        }
    }
}
