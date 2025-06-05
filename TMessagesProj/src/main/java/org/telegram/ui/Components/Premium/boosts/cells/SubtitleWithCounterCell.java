package org.telegram.ui.Components.Premium.boosts.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class SubtitleWithCounterCell extends org.telegram.ui.Cells.HeaderCell {

    private final AnimatedTextView counterTextView;

    public SubtitleWithCounterCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        counterTextView = new AnimatedTextView(context, true, true, true);
        counterTextView.setAnimationProperties(.45f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
        counterTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        counterTextView.setTextSize(dp(15));
        counterTextView.setTypeface(AndroidUtilities.bold());
        counterTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
        addView(counterTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 24, 0, 24, 0));
        setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
    }

    public void updateCounter(boolean animated, int count) {
        CharSequence text = count <= 0 ? "" : LocaleController.formatPluralString("BoostingBoostsCountTitle", count, count);
        counterTextView.cancelAnimation();
        counterTextView.setText(text, animated);
    }
}
