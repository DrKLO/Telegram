package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;

public class CustomNavigationBar extends View {
    private static final boolean USE_INSETS = Build.VERSION.SDK_INT >= 35;

    private final Paint paint = new Paint();
    private int height;

    public CustomNavigationBar(Context context) {
        super(context);

        if (USE_INSETS) {
            ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
        }
    }

    public void setColor(int color) {
        if (paint.getColor() != color) {
            paint.setColor(color);
            invalidate();
        }
    }

    public int getColor() {
        return paint.getColor();
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final int height = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        if (this.height != height) {
            this.height = height;
            requestLayout();
        }

        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!USE_INSETS) {
            height = AndroidUtilities.navigationBarHeight;
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
    }
}
