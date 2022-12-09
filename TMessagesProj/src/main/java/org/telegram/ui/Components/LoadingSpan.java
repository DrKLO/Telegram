package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
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

    public LoadingSpan(View view, int size) {
        this.view = view;
        this.size = size;
        this.drawable = new LoadingDrawable(null);
        this.drawable.paint.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(4)));
    }

    public void setColorKeys(String colorKey1, String colorKey2) {
        this.drawable.colorKey1 = colorKey1;
        this.drawable.colorKey2 = colorKey2;
    }

    public void setColorKeys(String colorKey1, String colorKey2, Theme.ResourcesProvider resourcesProvider) {
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

    @Override
    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fontMetricsInt) {
        return size;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        drawable.setBounds((int) x, top + AndroidUtilities.dp(2), (int) x + size, bottom - AndroidUtilities.dp(2));
        drawable.draw(canvas);
        if (view != null) {
            view.invalidate();
        }
    }
}