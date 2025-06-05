package org.telegram.ui.Components.Reactions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

@SuppressLint("ViewConstructor")
public class UpdateReactionsButton extends ButtonWithCounterView {

    private SpannableStringBuilder lock;

    public UpdateReactionsButton(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    public UpdateReactionsButton(Context context, boolean filled, Theme.ResourcesProvider resourcesProvider) {
        super(context, filled, resourcesProvider);
    }

    public void setDefaultState() {
        setText(new SpannableStringBuilder(LocaleController.getString(R.string.ReactionUpdateReactionsBtn)), false);
        lock = new SpannableStringBuilder("l");
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.mini_switch_lock);
        coloredImageSpan.setTopOffset(1);
        lock.setSpan(coloredImageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public void setLvlRequiredState(int lvl) {
        SpannableStringBuilder buttonLockedText = new SpannableStringBuilder();
        buttonLockedText.append(lock).append(LocaleController.formatPluralString("ReactionLevelRequiredBtn", lvl));
        setSubText(buttonLockedText, true);
    }

    public void removeLvlRequiredState() {
        setSubText(null, true);
    }
}
