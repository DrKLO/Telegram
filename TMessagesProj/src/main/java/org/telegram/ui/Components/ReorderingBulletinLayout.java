package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.NonNull;

@SuppressLint("ViewConstructor")
public class ReorderingBulletinLayout extends Bulletin.SimpleLayout {

    private final ReorderingHintDrawable hintDrawable;

    public ReorderingBulletinLayout(@NonNull Context context, String text) {
        super(context);
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
