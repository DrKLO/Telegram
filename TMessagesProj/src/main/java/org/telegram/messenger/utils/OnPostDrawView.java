package org.telegram.messenger.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class OnPostDrawView extends View implements ViewTreeObserver.OnPreDrawListener {
    private final InvalidateCallback callback;
    private final boolean onPreDrawMode;
    
    public interface InvalidateCallback {
        void onPostDraw(int invalidateFlags);
    }

    private int invalidateFlags = 0;
    public void invalidate(int flags) {
        if (invalidateFlags == 0) {
            invalidate();
        }
        invalidateFlags |= flags;
    }

    public OnPostDrawView(Context context, boolean onPreDrawMode, InvalidateCallback callback) {
        super(context);
        this.callback = callback;
        this.onPreDrawMode = onPreDrawMode;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(LayoutHelper.measureSpecExactly(1), LayoutHelper.measureSpecExactly(1));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!onPreDrawMode) {
            callback.onPostDraw(invalidateFlags);
            invalidateFlags = 0;
        }
    }

    public void bringToFrontIfNeeded() {
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) parent;
            final int index = viewGroup.indexOfChild(this);
            if (index >= 0 && (index != viewGroup.getChildCount() - 1)) {
                viewGroup.bringChildToFront(this);
            }
        }
    }



    private ViewTreeObserver observer;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (onPreDrawMode) {
            observer = getViewTreeObserver();
            observer.addOnPreDrawListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
        }
        observer = null;
    }

    @Override
    public boolean onPreDraw() {
        if (onPreDrawMode && invalidateFlags != 0) {
            callback.onPostDraw(invalidateFlags);
            invalidateFlags = 0;
        }
        return true;
    }
}
