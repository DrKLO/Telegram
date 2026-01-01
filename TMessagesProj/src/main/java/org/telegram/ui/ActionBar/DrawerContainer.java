package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;

public class DrawerContainer extends FrameLayout {
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int backgroundColor;
    private int navbarInset;

    public DrawerContainer(@NonNull Context context) {
        super(context);
        ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(width, height);

        RecyclerView listView = null;
        LayoutParams lp;
        int usedHeight = 0;

        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof RecyclerView) {
                listView = (RecyclerView) child;
            } else {
                lp = (LayoutParams) child.getLayoutParams();
                lp.bottomMargin = navbarInset;

                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                usedHeight += child.getMeasuredHeight();
            }
        }

        if (listView != null) {
            if (usedHeight == 0) {
                listView.setPadding(0, 0, 0, navbarInset);
            } else {
                listView.setPadding(0, 0, 0, 0);

                lp = (LayoutParams) listView.getLayoutParams();
                lp.bottomMargin = usedHeight + navbarInset;
            }

            measureChildWithMargins(listView, widthMeasureSpec, 0, heightMeasureSpec, 0);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (backgroundPaint.getAlpha() > 0) {
            canvas.drawRect(0, getMeasuredHeight() - navbarInset, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
        }
    }

    @Override
    public void setBackgroundColor(int color) {
        super.setBackgroundColor(color);
        backgroundColor = color;
        checkBackgroundColorPaint();
    }

    private void checkBackgroundColorPaint() {
        final float thirdButtonsFactor = AndroidUtilities.getNavigationBarThirdButtonsFactor(navbarInset);
        final int color = ColorUtils.compositeColors(0x20000000, backgroundColor);
        backgroundPaint.setColor(Theme.multAlpha(color, lerp(0.0f, 0.75f, thirdButtonsFactor)));
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        final int navbarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
        if (this.navbarInset != navbarInset) {
            this.navbarInset = navbarInset;
            checkBackgroundColorPaint();
            requestLayout();
        }

        return WindowInsetsCompat.CONSUMED;
    }
}
