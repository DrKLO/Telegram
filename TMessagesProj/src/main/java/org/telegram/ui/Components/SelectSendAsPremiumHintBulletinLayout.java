package org.telegram.ui.Components;

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

@SuppressLint("ViewConstructor")
public class SelectSendAsPremiumHintBulletinLayout extends Bulletin.MultiLineLayout {

    public SelectSendAsPremiumHintBulletinLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, boolean channels, Runnable callback) {
        super(context, resourcesProvider);

        imageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.msg_premium_prolfilestar));
        imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        textView.setText(AndroidUtilities.replaceTags(LocaleController.getString(channels ? R.string.SelectSendAsPeerPremiumHint : R.string.SelectSendAsPeerPremiumHint)));

        Bulletin.UndoButton button = new Bulletin.UndoButton(context, true, resourcesProvider);
        button.setText(LocaleController.getString(R.string.SelectSendAsPeerPremiumOpen));
        button.setUndoAction(callback);
        setButton(button);
    }
}
