package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.LauncherIconController;

@SuppressLint("ViewConstructor")
public class AppIconBulletinLayout extends Bulletin.ButtonLayout {

    public final AppIconsSelectorCell.AdaptiveIconImageView imageView = new AppIconsSelectorCell.AdaptiveIconImageView(getContext());
    public final TextView textView = new TextView(getContext());

    public AppIconBulletinLayout(@NonNull Context context, LauncherIconController.LauncherIcon icon, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        addView(imageView, LayoutHelper.createFrameRelatively(30, 30, Gravity.START | Gravity.CENTER_VERTICAL, 12, 8, 12, 8));

        textView.setGravity(Gravity.START);
        textView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        textView.setTextColor(getThemedColor(Theme.key_undo_infoColor));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(Typeface.SANS_SERIF);
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 16, 0));

        imageView.setImageDrawable(ContextCompat.getDrawable(context, icon.background));
        imageView.setOuterPadding(AndroidUtilities.dp(8));
        imageView.setBackgroundOuterPadding(AndroidUtilities.dp(24));
        imageView.setForeground(icon.foreground);
        textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AppIconChangedTo, LocaleController.getString(icon.title))));
    }
}
