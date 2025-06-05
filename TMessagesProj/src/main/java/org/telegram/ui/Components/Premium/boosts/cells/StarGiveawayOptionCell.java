package org.telegram.ui.Components.Premium.boosts.cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BillingController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.RadioButton;

public class StarGiveawayOptionCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private RadioButton radioButton;

    private final Drawable starDrawableOutline;
    private final Drawable starDrawable;

    private AnimatedTextView titleView;
    private AnimatedTextView subtitleView;
    private TextView priceView;

    private SpannableString loading1, loading2;

    public StarGiveawayOptionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        starDrawableOutline = context.getResources().getDrawable(R.drawable.star_small_outline).mutate();
        starDrawableOutline.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), PorterDuff.Mode.SRC_IN));
        starDrawable = context.getResources().getDrawable(R.drawable.star_small_inner).mutate();
        setWillNotDraw(false);

        titleView = new AnimatedTextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(dp(16));
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20, Gravity.LEFT | Gravity.TOP, 64, 8, 80, 0));

        loading1 = new SpannableString("x");
        loading1.setSpan(new LoadingSpan(titleView, dp(90)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        subtitleView = new AnimatedTextView(context, false, true, true);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleView.setTextSize(dp(13));
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 14, Gravity.LEFT | Gravity.TOP, 64, 31, 80, 0));

        loading2 = new SpannableString("x");
        loading2.setSpan(new LoadingSpan(subtitleView, dp(70)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        priceView = new TextView(context);
        priceView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        priceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        priceView.setGravity(Gravity.RIGHT);
        addView(priceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 19, 0));

        radioButton = new RadioButton(context);
        radioButton.setSize(dp(20));
        radioButton.setColor(Theme.getColor(Theme.key_checkboxDisabled, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
        addView(radioButton, LayoutHelper.createFrame(20, 20, Gravity.LEFT | Gravity.CENTER_VERTICAL, 22, 0, 0, 0));
    }

    private TL_stars.TL_starsGiveawayOption currentOption;
    private long currentOptionStarsPerUser;

    public void setOption(TL_stars.TL_starsGiveawayOption option, int index, long per_user_stars, boolean selected, boolean needDivider) {
        final boolean animated = currentOption == option;
        radioButton.setChecked(selected, animated);

        currentOption = option;
        currentOptionStarsPerUser = per_user_stars;

        if (animated) {
            subtitleView.cancelAnimation();
        }

        if (option == null) {
            titleView.setText(loading1, false);
            subtitleView.setText(loading2, animated);
            priceView.setText("");
        } else {
            titleView.setText(LocaleController.formatPluralStringComma("GiveawayStars", (int) option.stars, ' '), false);
            subtitleView.setText(LocaleController.formatPluralStringComma("BoostingStarOptionPerUser", (int) per_user_stars, ','), animated);
            priceView.setText(BillingController.getInstance().formatCurrency(option.amount, option.currency));
        }

        starsCount = 1 + index;
        if (!animated) {
            animatedStarsCount.set(starsCount, true);
        }
        invalidate();

    }

    public TL_stars.TL_starsGiveawayOption getOption() {
        return currentOption;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY)
        );
    }

    private int starsCount;
    private final AnimatedFloat animatedStarsCount = new AnimatedFloat(this, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final float starsCount = animatedStarsCount.set(this.starsCount);
        final float rtl = 1f; // LocaleController.isRTL ? -1f : 1f;
        final float wsize = dp(24), hsize = dp(24);
        final float pad = dp(2.5f);
        final float sx = dp(64);
        final float sy = dp(8);
        for (int i = (int) Math.ceil(starsCount) - 1; i >= 0; --i) {
            final float alpha = Utilities.clamp(starsCount - i, 1f, 0f);
            final float x = sx + (i - 1 - (1f - alpha)) * pad * rtl;
            final float y = sy;
            starDrawableOutline.setBounds((int) x, (int) y, (int) (x + wsize), (int) (y + hsize));
            starDrawableOutline.setAlpha((int) (0xFF * alpha));
            starDrawableOutline.draw(canvas);
            starDrawable.setBounds((int) x, (int) y, (int) (x + wsize), (int) (y + hsize));
            starDrawable.setAlpha((int) (0xFF * alpha));
            starDrawable.draw(canvas);
        }
        titleView.setTranslationX(dp(22) + pad * starsCount);

    }
}
