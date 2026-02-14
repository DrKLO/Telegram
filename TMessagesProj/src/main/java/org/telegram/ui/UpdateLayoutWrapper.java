package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class UpdateLayoutWrapper extends ViewGroup {
    public static final int HEIGHT = 44;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private View updateLayout;

    public UpdateLayoutWrapper(@NonNull Context context) {
        super(context);
        setClipToPadding(false);
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateLayout = child;
    }

    public boolean isUpdateLayoutVisible() {
        return updateLayout != null && updateLayout.getVisibility() == VISIBLE;
    }

    private boolean lastUpdateLayoutVisible;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final boolean layoutVisible = isUpdateLayoutVisible();
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = layoutVisible ? (dp(HEIGHT) + getPaddingBottom()) : 0;

        setMeasuredDimension(width, height);

        final int measureSpecW = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        final int measureSpecH = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(measureSpecW, measureSpecH);
        }

        if (lastUpdateLayoutVisible != layoutVisible) {
            lastUpdateLayoutVisible = layoutVisible;
            ViewCompat.requestApplyInsets(this);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.setPadding(left, top, right, bottom);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final int navigationHeight = getPaddingBottom();
        final float alpha = AndroidUtilities.getNavigationBarThirdButtonsFactor(0.1f, 0.75f, navigationHeight);

        final int backgroundColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        final int navigationColor = ColorUtils.compositeColors(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite), alpha), backgroundColor);


        paint.setColor(backgroundColor);
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - navigationHeight, paint);

        paint.setColor(navigationColor);
        canvas.drawRect(0, getMeasuredHeight() - navigationHeight, getMeasuredWidth(), getMeasuredHeight(), paint);

        super.dispatchDraw(canvas);
    }
}
