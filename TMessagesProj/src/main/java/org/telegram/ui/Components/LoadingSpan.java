package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class LoadingSpan extends ReplacementSpan {

    private int size;
    private View view;
    private LoadingDrawable drawable;

    public int yOffset;

    public LoadingSpan(View view, int size) {
        this(view, size, AndroidUtilities.dp(2));
    }

    public LoadingSpan(View view, int size, int yOffset) {
        this(view, size, yOffset, null);
    }

    public LoadingSpan(View view, int size, int yOffset, Theme.ResourcesProvider resourcesProvider) {
        this.view = view;
        this.size = size;
        this.yOffset = yOffset;
        this.drawable = new LoadingDrawable(resourcesProvider);
        this.drawable.setRadiiDp(4);
    }

    public void setColorKeys(int colorKey1, int colorKey2) {
        this.drawable.colorKey1 = colorKey1;
        this.drawable.colorKey2 = colorKey2;
    }

    public void setColorKeys(int colorKey1, int colorKey2, Theme.ResourcesProvider resourcesProvider) {
        this.drawable.resourcesProvider = resourcesProvider;
        this.drawable.colorKey1 = colorKey1;
        this.drawable.colorKey2 = colorKey2;
    }

    public void setColors(int color1, int color2) {
        this.drawable.color1 = color1;
        this.drawable.color2 = color2;
    }

    public void setView(View view) {
        this.view = view;
    }

    private Paint paint;
    @Override
    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fontMetricsInt) {
        this.paint = paint;
        if (paint != null && this.drawable.color1 == null && this.drawable.color2 == null) {
            drawable.setColors(
                Theme.multAlpha(paint.getColor(), .1f),
                Theme.multAlpha(paint.getColor(), .25f)
            );
        }
        return size;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        drawable.setBounds((int) x, top + yOffset, (int) x + size, bottom - AndroidUtilities.dp(2) + yOffset);
        if (paint != null) {
            drawable.setAlpha(paint.getAlpha());
        }
        drawable.draw(canvas);
        if (view != null) {
            view.invalidate();
        }
    }
}