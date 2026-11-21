package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Stories.recorder.HintView2;

/** What one tick and two ticks mean, over the ticks of a sent message: each line with its ticks drawn as they appear. */
public class ChecksHintView extends HintView2 {

    private final RLottieDrawable[] icons = new RLottieDrawable[2];
    private final int[] location = new int[2];

    public ChecksHintView(Context context) {
        super(context, DIRECTION_BOTTOM);
        setMultilineText(true);
        setTextAlign(Layout.Alignment.ALIGN_NORMAL);
        setDuration(3200);
        setHideByTouch(true);
        useScale(true);
        setInnerPadding(8, 6, 10, 6);
        setAnchorGap(0);

        final SpannableStringBuilder text = new SpannableStringBuilder();
        for (int a = 0; a < 2; a++) {
            icons[a] = new RLottieDrawable(a == 0 ? R.raw.ticks_single : R.raw.ticks_double, dp(24), dp(24));
            icons[a].setCallback(this);
            if (a > 0) {
                text.append("\n");
            }
            final int start = text.length();
            text.append("x");
            text.setSpan(new IconSpan(icons[a]), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(LocaleController.getString(a == 0 ? R.string.HintSent : R.string.HintRead));
        }
        setText(text);
    }

    /** Over the ticks of the cell, when there is room above it; false otherwise. */
    public boolean showForMessageCell(ChatMessageCell cell, boolean animated) {
        if (!(getParent() instanceof android.view.View)) {
            return false;
        }
        cell.getLocationInWindow(location);
        final int cellX = location[0], cellY = location[1];
        ((android.view.View) getParent()).getLocationInWindow(location);
        final float top = cellY - location[1];
        if (top <= dp(80)) {
            return false;
        }
        for (RLottieDrawable icon : icons) {
            icon.setCurrentFrame(0, false);
            icon.start();
        }
        showAt(cellX - location[0] + cell.getChecksX() + dp(5), top + cell.getChecksY() + dp(6), animated);
        return true;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        // called from the constructor of HintView2 too, before the icons are there
        return icons != null && (who == icons[0] || who == icons[1]) || super.verifyDrawable(who);
    }

    /** The ticks before the text of a line, the line as high as they are. */
    private static class IconSpan extends ReplacementSpan {
        private final Drawable drawable;

        IconSpan(Drawable drawable) {
            this.drawable = drawable;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            if (fm != null) {
                final Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
                final int center = (pfm.ascent + pfm.descent) / 2;
                fm.ascent = fm.top = Math.min(pfm.ascent, center - dp(12));
                fm.descent = fm.bottom = Math.max(pfm.descent, center + dp(12));
            }
            return dp(24 + 8);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            final Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
            final int cy = y + (pfm.ascent + pfm.descent) / 2;
            drawable.setBounds((int) x, cy - dp(12), (int) x + dp(24), cy + dp(12));
            drawable.setAlpha(paint.getAlpha());
            drawable.draw(canvas);
        }
    }
}
