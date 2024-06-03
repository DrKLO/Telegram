package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class BoostCounterSpan extends ReplacementSpan {
    private final Drawable boostProfileBadge;
    private final Drawable boostProfileBadge2;
    public boolean isRtl;

    public static Pair<SpannableString, BoostCounterSpan> create(View parent, TextPaint paint, int count) {
        SpannableString spannableString = new SpannableString("d");
        BoostCounterSpan span = new BoostCounterSpan(parent, paint, count);
        spannableString.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return new Pair<>(spannableString, span);
    }

    private final AnimatedTextView.AnimatedTextDrawable countText;
    private final TextPaint namePaint;
    private final View parent;
    private int currentCount;

    public BoostCounterSpan(View parent, TextPaint namePaint, int count) {
        this.namePaint = namePaint;
        this.parent = parent;
        countText = new AnimatedTextView.AnimatedTextDrawable(false, false, true);
        countText.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        countText.setCallback(parent);
        countText.setTextSize(dp(11.5f));
        countText.setTypeface(AndroidUtilities.bold());
        countText.setText("");
        countText.setGravity(Gravity.CENTER);
        boostProfileBadge = ContextCompat.getDrawable(parent.getContext(), R.drawable.mini_boost_profile_badge).mutate();
        boostProfileBadge2 = ContextCompat.getDrawable(parent.getContext(), R.drawable.mini_boost_profile_badge2).mutate();
        boostProfileBadge.setBounds(0, 0, boostProfileBadge.getIntrinsicWidth(), boostProfileBadge.getIntrinsicHeight());
        boostProfileBadge2.setBounds(0, 0, boostProfileBadge2.getIntrinsicWidth(), boostProfileBadge2.getIntrinsicHeight());
        setCount(count, false);
    }

    public void setCount(int count, boolean animated) {
        currentCount = count;
        countText.setText(count <= 1 ? "" : String.valueOf(count), animated);
    }

    public void setColor(int color) {
        countText.setTextColor(color);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        return getWidth();
    }

    public int getWidth() {
        return (int) (dp(16) + countText.getWidth());
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float _x, int top, int _y, int bottom, @NonNull Paint paint) {
        if (this.namePaint.getColor() != countText.getTextColor()) {
            countText.setTextColor(this.namePaint.getColor());
            boostProfileBadge.setColorFilter(new PorterDuffColorFilter(countText.getTextColor(), PorterDuff.Mode.MULTIPLY));
            boostProfileBadge2.setColorFilter(new PorterDuffColorFilter(countText.getTextColor(), PorterDuff.Mode.MULTIPLY));
        }
        canvas.save();
        canvas.translate(_x, -dp(0.2f));
        if (currentCount == 1) {
            canvas.translate(dp(1.5f), 0);
            boostProfileBadge.draw(canvas);
        } else {
            boostProfileBadge2.draw(canvas);
        }
        canvas.translate(dp(16), 0);
        AndroidUtilities.rectTmp2.set(0, 0, (int) countText.getCurrentWidth(), (int) countText.getHeight());
        countText.setBounds(AndroidUtilities.rectTmp2);
        countText.draw(canvas);
        canvas.restore();
    }
}
