package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/**
 * Reusable collapse/expand pill button: an animated "Expand"/"Collapse" label followed by a
 * chevron, with a show/hide scale and a press bounce. Extracted from {@link QuoteSpan} so the
 * same control can be reused outside of quotes.
 *
 * The caller owns positioning: it passes the bottom-right anchor to {@link #draw} on every frame,
 * receives the drawn (unscaled) rect back for hit-testing, and forwards touch state through
 * {@link #setPressed(boolean)} / {@link #isPressed()}.
 */
public class QuoteCollapseButton {

    private final AnimatedFloat scale;
    private final AnimatedTextView.AnimatedTextDrawable text;
    private final int textWidth;
    private boolean textCollapsed;
    private final QuoteSpan.ExpandDrawable drawable;
    private final ButtonBounce bounce;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean pressed;

    public QuoteCollapseButton(View view) {
        scale = new AnimatedFloat(view, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        drawable = new QuoteSpan.ExpandDrawable(view);
        bounce = new ButtonBounce(view);

        text = new AnimatedTextView.AnimatedTextDrawable();
        text.setTextSize(dp(11));
        text.setHacks(true, true, true);
        text.setCallback(view);
        text.setOverrideFullWidth((int) (AndroidUtilities.displaySize.x * .3f));
        textCollapsed = false;
        text.setText(getString(R.string.QuoteCollapse), false);
        textWidth = (int) Math.ceil(Math.max(
                text.getPaint().measureText(getString(R.string.QuoteExpand)),
                text.getPaint().measureText(getString(R.string.QuoteCollapse))
        ));
    }

    public boolean verifyDrawable(Drawable who) {
        return who == text || who == drawable;
    }

    /**
     * Full layout width including horizontal padding on both sides. Use this to reserve space,
     * not the animated width used while drawing.
     */
    public int width() {
        return (int) (dp(6 + 11.66f + 6) + textWidth + 2 * dp(3.333f));
    }

    /** Pill height, for callers that need to reserve vertical space around the button. */
    public int height() {
        return dp(17.66f);
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
        bounce.setPressed(pressed);
    }

    /**
     * Draws the button with its bottom-right corner anchored at ({@code right}, {@code bottom}).
     * The drawn (unscaled) rect is written into {@code bounds} for hit-testing regardless of the
     * current scale.
     *
     * @param collapsed current collapsed state, selects the label and chevron direction
     * @param visible   whether the button should be shown (drives the show/hide scale)
     * @return the applied scale, {@code 0} when fully hidden
     */
    public float draw(Canvas canvas, RectF bounds, float right, float bottom, int color, boolean collapsed, boolean visible) {
        if (collapsed != textCollapsed) {
            text.setText(getString((textCollapsed = collapsed) ? R.string.QuoteExpand : R.string.QuoteCollapse), true);
        }
        final int buttonWidth = (int) (dp(6 + 11.66f + 6) + text.getCurrentWidth());
        final int buttonHeight = dp(17.66f);
        bounds.set(right - buttonWidth, bottom - buttonHeight, right, bottom);

        final float s = scale.set(visible) * bounce.getScale(0.02f);
        if (s > 0) {
            backgroundPaint.setColor(ColorUtils.setAlphaComponent(color, 0x1e));
            canvas.save();
            canvas.scale(s, s, right, bottom);
            canvas.drawRoundRect(bounds, buttonHeight / 2f, buttonHeight / 2f, backgroundPaint);
            text.setBounds((int) (bounds.left + dp(6)), (int) bounds.top, (int) (bounds.right - dp(17.66f)), (int) bounds.bottom);
            text.setTextColor(color);
            text.draw(canvas);
            final int sz = dp(14);
            drawable.setBounds((int) (bounds.right - dp(3.33f) - sz), (int) (bounds.centerY() - sz / 2f + dp(.33f)), (int) (bounds.right - dp(3.33f)), (int) (bounds.centerY() + sz / 2f + dp(.33f)));
            drawable.setColor(color);
            drawable.setState(!collapsed);
            drawable.draw(canvas);
            canvas.restore();
        }
        return s;
    }
}