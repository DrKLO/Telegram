package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class DiscountSpan extends ReplacementSpan {

    public static CharSequence applySpan(CharSequence str, int discount) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder("d ");
        spannableStringBuilder.append(str);
        DiscountSpan span = new DiscountSpan(11, discount);
        span.setColor(Theme.getColor(Theme.key_premiumGradient1));
        spannableStringBuilder.setSpan(span, 0, 1, 0);
        return spannableStringBuilder;
    }

    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    StaticLayout layout;
    float width, height;
    int discount;
    private int color;

    public DiscountSpan(float textSize, int discount) {
        textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        bgPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(dp(textSize));
        this.discount = discount;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void makeLayout() {
        if (layout == null) {
            layout = new StaticLayout(LocaleController.formatString(R.string.GiftPremiumOptionDiscount, discount), textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            width = layout.getLineWidth(0);
            height = layout.getHeight();
        }
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        makeLayout();
        return (int) (dp(13) + width);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float _x, int top, int _y, int bottom, @NonNull Paint paint) {
        makeLayout();
        int color = this.color;
        if (color == 0) {
            color = paint.getColor();
        }
        bgPaint.setColor(color);
        textPaint.setColor(AndroidUtilities.computePerceivedBrightness(color) > .721f ? Color.BLACK : Color.WHITE);
        float x = _x + dp(10), y = _y - height + dp(2f);
        AndroidUtilities.rectTmp.set(x, y, x + width, y + height);
        float r = dp(4f);
        AndroidUtilities.rectTmp.inset(dp(-4.5f), dp(-1.66f));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, bgPaint);
        canvas.save();
        canvas.translate(x, y);
        layout.draw(canvas);
        canvas.restore();
    }
}
