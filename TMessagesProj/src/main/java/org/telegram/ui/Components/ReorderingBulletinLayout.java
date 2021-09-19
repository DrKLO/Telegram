package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public class ReorderingBulletinLayout extends Bulletin.SimpleLayout {

    private final ReorderingHintDrawable hintDrawable;

    public ReorderingBulletinLayout(@NonNull Context context, String text, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        textView.setText(text);
        textView.setTranslationY(-1);
        imageView.setImageDrawable(hintDrawable = new ReorderingHintDrawable());
    }

    @Override
    protected void onEnterTransitionEnd() {
        super.onEnterTransitionEnd();
        hintDrawable.startAnimation();
    }

    @Override
    protected void onExitTransitionEnd() {
        super.onExitTransitionEnd();
        hintDrawable.resetAnimation();
    }
}
