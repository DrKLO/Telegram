package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

public class BluredView extends View {

    public final BlurBehindDrawable drawable;

    public BluredView(Context context, View parentView, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        drawable = new BlurBehindDrawable(parentView, this, 1, resourcesProvider);
        drawable.setAnimateAlpha(false);
        drawable.show(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawable.draw(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        drawable.checkSizes();
    }

    public void update() {
        drawable.invalidate();
    }

    public boolean fullyDrawing() {
        return drawable.isFullyDrawing() && getVisibility() == View.VISIBLE;
    }
}
