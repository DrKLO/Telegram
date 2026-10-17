package org.telegram.ui.Stars;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

/** A donor's own coarse rank, without names, avatars or a public donor list. */
public final class StarsDonorPercentileView extends LinearLayout {
    private final AnimatedTextView number;
    private final TextView title;
    private int percentile;
    private boolean preview;
    private final int minimumNumberHeight;

    public StarsDonorPercentileView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setTag("stars_donor_percentile");
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(8));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        title = new TextView(context);
        title.setText(LocaleController.getString(R.string.StarsChannelPercentileTop));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        title.setGravity(Gravity.CENTER);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(title, LayoutHelper.createLinear(-1, -2));

        number = new AnimatedTextView(context, false, true, true);
        number.setTypeface(AndroidUtilities.bold());
        number.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 48, getResources().getDisplayMetrics()));
        number.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
        number.setGravity(Gravity.CENTER);
        number.setAnimationProperties(.25f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        number.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        minimumNumberHeight = (int) Math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 60, getResources().getDisplayMetrics()));
        addView(number, new LinearLayout.LayoutParams(-1, minimumNumberHeight));

        TextView caption = new TextView(context);
        caption.setText(LocaleController.getString(R.string.StarsChannelPercentilePrivate));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        caption.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        caption.setGravity(Gravity.CENTER);
        caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(caption, LayoutHelper.createLinear(-1, -2));
        setVisibility(GONE);
    }

    public void setPercentile(int value, boolean animated) {
        setPercentile(value, animated, false);
    }

    public void setPercentile(int value, boolean animated, boolean preview) {
        if (value == percentile && this.preview == preview) return;
        int previous = percentile;
        this.preview = preview;
        title.setText(LocaleController.getString(preview
            ? R.string.StarsChannelPercentileProjected : R.string.StarsChannelPercentileTop));
        boolean reveal = percentile == 0 && value > 0;
        percentile = value;
        animate().cancel();
        boolean motion = animated && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
        String valueText = value > 0 ? LocaleController.formatNumber(value, ',') + "%" : "";
        // Use the same rolling digits as Telegram's counters, following rank direction.
        number.setText(valueText, motion && !reveal, value < previous);
        // Android's nonlinear font scaling can make a fixed sp box shorter than its text.
        int height = Math.max(minimumNumberHeight, (int) Math.ceil(number.getDrawable().getHeight()));
        if (number.getLayoutParams().height != height) {
            number.getLayoutParams().height = height;
            number.requestLayout();
        }
        setContentDescription(value > 0 ? title.getText()
            + " " + valueText + " " + LocaleController.getString(R.string.StarsChannelPercentilePrivate) : null);
        setVisibility(value > 0 ? VISIBLE : GONE);
        setAlpha(1f);
        setTranslationY(0f);
        if (reveal && motion) {
            setAlpha(0f);
            setTranslationY(AndroidUtilities.dp(6));
            animate().alpha(1f).translationY(0f).setDuration(240)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        }
    }

    public int getPercentile() { return percentile; }
    public boolean isPreview() { return preview; }

    @Override protected void onDetachedFromWindow() {
        animate().cancel();
        setAlpha(1f);
        setTranslationY(0f);
        super.onDetachedFromWindow();
    }
}
