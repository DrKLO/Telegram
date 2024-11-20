package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Keep;

import org.telegram.messenger.Utilities;

@Keep
public abstract class IUpdateButton extends FrameLayout {
    @Keep
    public IUpdateButton(Context context) {
        super(context);
    }
    @Keep
    public void onTranslationUpdate(Utilities.Callback<Float> onTranslationUpdate) {}
    @Keep
    public void update(boolean animated) {}
}
