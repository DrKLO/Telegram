package org.telegram.ui.Stars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.CombinedDrawable;

/** Telegram's existing lock glyph, inset to match the surrounding reaction text. */
public final class StarsPrivacyIcon {
    public static Drawable create(Context context, int size, int color) {
        Drawable lock = context.getResources().getDrawable(R.drawable.wallet_lock).mutate();
        lock.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        CombinedDrawable drawable = new CombinedDrawable(new ColorDrawable(Color.TRANSPARENT), lock);
        drawable.setCustomSize(AndroidUtilities.dp(size), AndroidUtilities.dp(size));
        drawable.setIconSize(AndroidUtilities.dp(size * .68f), AndroidUtilities.dp(size * .68f));
        return drawable;
    }

    private StarsPrivacyIcon() {}
}
