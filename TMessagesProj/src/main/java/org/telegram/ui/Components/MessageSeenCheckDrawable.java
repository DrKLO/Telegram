package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;

public class MessageSeenCheckDrawable {

    private CharSequence lastSpanned;
    private int lastColor;
    private Drawable drawable;
    private float lastDensity;

    private int resId;
    private String colorKey;

    private int w = -1, h = -1;
    private float oy = 4.66f;

    public MessageSeenCheckDrawable(int resId, String colorKey) {
        this.resId = resId;
        this.colorKey = colorKey;
    }

    public MessageSeenCheckDrawable(int resId, String colorKey, int w, int h) {
        this(resId, colorKey);
        this.w = w;
        this.h = h;
    }

    public MessageSeenCheckDrawable(int resId, String colorKey, int w, int h, float oy) {
        this(resId, colorKey);
        this.w = w;
        this.h = h;
        this.oy = oy;
    }

    public CharSequence getSpanned(Context context) {
        if (lastSpanned != null && drawable != null && AndroidUtilities.density == lastDensity) {
            if (lastColor != Theme.getColor(colorKey)) {
                drawable.setColorFilter(new PorterDuffColorFilter(lastColor = Theme.getColor(colorKey), PorterDuff.Mode.SRC_IN));
            }
            return lastSpanned;
        }
        if (context == null) {
            return null;
        }
        SpannableStringBuilder str = new SpannableStringBuilder("v ");
        lastDensity = AndroidUtilities.density;
        drawable = context.getResources().getDrawable(resId).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(lastColor = Theme.getColor(colorKey), PorterDuff.Mode.SRC_IN));
        final int w = this.w <= 0 ? drawable.getIntrinsicWidth() : AndroidUtilities.dp(this.w);
        final int h = this.h <= 0 ? drawable.getIntrinsicHeight() : AndroidUtilities.dp(this.h);
        final int oy = AndroidUtilities.dp(this.oy);
        drawable.setBounds(0, oy, w, oy + h);
        str.setSpan(new ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        str.setSpan(new DialogCell.FixedWidthSpan(AndroidUtilities.dp(2)), 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return lastSpanned = str;
    }
}