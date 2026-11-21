package org.telegram.ui.Components;

import org.telegram.messenger.FileLog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;

@SuppressLint("ViewConstructor")
public class SelectSendAsPremiumHintBulletinLayout extends Bulletin.MultiLineLayout implements Theme.Colorable {

    public SelectSendAsPremiumHintBulletinLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, boolean channels, Runnable callback) {
        super(context, resourcesProvider);

        imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.msg_premium_prolfilestar));
        imageView.setColorFilter(new PorterDuffColorFilter(themedIconColor = getThemedColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        textView.setText(AndroidUtilities.replaceTags(LocaleController.getString(channels ? R.string.SelectSendAsPeerPremiumHint : R.string.SelectSendAsPeerPremiumHint)));

        Bulletin.UndoButton button = new Bulletin.UndoButton(context, true, resourcesProvider);
        button.setText(LocaleController.getString(R.string.SelectSendAsPeerPremiumOpen));
        button.setUndoAction(callback);
        setButton(button);
    }

    private int themedIconColor;

    @Override
    public void updateColors() {
        super.updateColors();
        try {
            final int color = getThemedColor(Theme.key_undo_infoColor);
            if (imageView != null && color != themedIconColor) {
                themedIconColor = color;
                imageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
            invalidate();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
