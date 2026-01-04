package org.telegram.messenger.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class OnPostDrawView extends View {
    private final InvalidateCallback callback;
    
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

    public OnPostDrawView(Context context, InvalidateCallback callback) {
        super(context);
        this.callback = callback;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(LayoutHelper.measureSpecExactly(0), LayoutHelper.measureSpecExactly(0));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        callback.onPostDraw(invalidateFlags);
        invalidateFlags = 0;
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
}
