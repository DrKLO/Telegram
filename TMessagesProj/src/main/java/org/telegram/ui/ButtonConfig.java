package org.telegram.ui;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class ButtonConfig {
    public final @DrawableRes int iconRes;
    public final @StringRes int labelRes;
    public final View.OnClickListener onClick;
    public final View.OnLongClickListener onLongClick; // qo‘shildi

    public ButtonConfig(@DrawableRes int iconRes, @StringRes int labelRes, View.OnClickListener onClick) {
        this(iconRes, labelRes, onClick, null);
    }

    public ButtonConfig(@DrawableRes int iconRes, @StringRes int labelRes, View.OnClickListener onClick, View.OnLongClickListener onLongClick) {
        this.iconRes = iconRes;
        this.labelRes = labelRes;
        this.onClick = onClick;
        this.onLongClick = onLongClick;
    }
}
