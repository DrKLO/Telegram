package org.telegram.ui.Components.emojiview;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.utils.GradientProtectionDrawable;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public class FoundStickerPackButtonContainer extends FrameLayout {
    private final Theme.ResourcesProvider resourcesProvider;
    private final GradientProtectionDrawable gradientProtectionDrawable = new GradientProtectionDrawable(WindowInsetsCompat.Side.BOTTOM);

    public FoundStickerPackButtonContainer(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradientProtectionDrawable.setBounds(0, 0, w, h);
        gradientProtectionDrawable.setInsets(0, 0, 0, getPaddingBottom() + dp(24));
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        gradientProtectionDrawable.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 0.65f));
        gradientProtectionDrawable.draw(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev) || true;
    }
}
