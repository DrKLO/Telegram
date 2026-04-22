package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class LoadingSpan extends ReplacementSpan {

    public int size;
    private View view;
    private LoadingDrawable drawable;

    public int yOffset;
    private float scaleY = 1f;
    public float height = -1;
    public float alpha = 1.0f;
    public boolean fullWidth = false;

    public LoadingSpan(View view, int size) {
        this(view, size, dp(2));
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

    public LoadingSpan setHeight(float height) {
        this.height = height;
        return this;
    }
    public LoadingSpan setAlpha(float alpha) {
        this.alpha = alpha;
        return this;
    }
    public LoadingSpan setFullWidth(boolean fullWidth) {
        this.fullWidth = fullWidth;
        return this;
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

    public void setScaleY(float scaleY) {
        this.scaleY = scaleY;
    }

    public void setView(View view) {
        this.view = view;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fm) {
        final Paint.FontMetrics paintFontMetrics = paint.getFontMetrics();
        if (fm != null) {
            fm.ascent = (int) paintFontMetrics.ascent;
            fm.bottom = (int) paintFontMetrics.bottom;
            fm.descent = (int) paintFontMetrics.descent;
            fm.leading = (int) paintFontMetrics.leading;
            fm.top = (int) paintFontMetrics.top;
        }
        if (paint != null && this.drawable.color1 == null && this.drawable.color2 == null) {
            drawable.setColors(
                Theme.multAlpha(paint.getColor(), .1f),
                Theme.multAlpha(paint.getColor(), .25f)
            );
        }
        if (fullWidth && view != null && view.getMeasuredWidth() > 0) {
            return view.getMeasuredWidth() - view.getPaddingLeft() - view.getPaddingRight() - size;
        }
        return size;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        int size = this.size;
        if (fullWidth && view != null && view.getMeasuredWidth() > 0) {
            size = view.getMeasuredWidth() - view.getPaddingLeft() - view.getPaddingRight() - this.size;
        }
        if (height > 0) {
            final float cy = (top + bottom) / 2f;
            drawable.setBounds((int) x, (int) (cy - height / 2f), (int) x + size, (int) (cy + height / 2f));
        } else {
            drawable.setBounds(
                (int) x,
                (int) (top + (bottom - dp(2) - top) / 2f * (1f - scaleY) + yOffset),
                (int) x + size,
                (int) (bottom - dp(2) - ((bottom - dp(2)) - top) / 2f * (1f - scaleY) + yOffset)
            );
        }
        drawable.setAlpha((int) ((paint == null ? 0xFF : paint.getAlpha()) * alpha));
        drawable.draw(canvas);
        if (view != null) {
            view.invalidate();
        }
    }
}