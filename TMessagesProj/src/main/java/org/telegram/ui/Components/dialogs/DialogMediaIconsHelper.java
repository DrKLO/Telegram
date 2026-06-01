package org.telegram.ui.Components.dialogs;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.SparseArray;

import androidx.annotation.DrawableRes;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;

public class DialogMediaIconsHelper {
    private static final SparseArray<ColoredImageSpan> spans = new SparseArray<>(6);

    public static CharSequence addDialogMediaSpan(CharSequence charSequence, @DrawableRes int iconRes, boolean biDiIsolate) {
        final SpannableStringBuilder ssb;

        if (charSequence instanceof SpannableStringBuilder) {
            ssb = (SpannableStringBuilder) charSequence;
        } else {
            ssb = new SpannableStringBuilder(charSequence);
        }

        if (biDiIsolate) {
            ssb.insert(0, "* \u2068");
        } else {
            ssb.insert(0, "* ");
        }

        ColoredImageSpan span = spans.get(iconRes);
        if (span == null) {
            span = new ColoredImageSpan(iconRes);
            span.setColorKey(Theme.key_telegram_color_text);
            spans.put(iconRes, span);
        }

        ssb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (biDiIsolate) {
            ssb.append('\u2069');
        }
        return ssb;
    }
}
