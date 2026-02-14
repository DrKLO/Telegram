package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

public class SearchTabsAndFiltersLayout extends FrameLayout {
    private final Path clipPath = new Path();
    private BlurredBackgroundDrawable blurredBackgroundDrawable;

    public SearchTabsAndFiltersLayout(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipPath.rewind();
        clipPath.addRoundRect(dp(9), dp(9), w - dp(9), h - dp(9),
                dp(16), dp(16), Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public void setBlurredBackground(BlurredBackgroundDrawable drawable) {
        setBackground(blurredBackgroundDrawable = drawable);
    }

    public void updateColors() {
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.updateColors();
        }
    }
}
