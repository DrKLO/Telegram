/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RadialProgressView;

public class ChatLoadingCell extends FrameLayout {

    private FrameLayout frameLayout;
    private RadialProgressView progressBar;
    private Theme.ResourcesProvider resourcesProvider;

    public ChatLoadingCell(Context context, View parent, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        frameLayout = new FrameLayout(context) {
            private final RectF rect = new RectF();
            @Override
            protected void dispatchDraw(Canvas canvas) {
                rect.set(0, 0, getWidth(), getHeight());
                applyServiceShaderMatrix();
                canvas.drawRoundRect(rect, dp(18), dp(18), getThemedPaint(Theme.key_paint_chatActionBackground));
                if (hasGradientService()) {
                    canvas.drawRoundRect(rect, dp(18), dp(18), getThemedPaint(Theme.key_paint_chatActionBackgroundDarken));
                }

                super.dispatchDraw(canvas);
            }
        };
        frameLayout.setWillNotDraw(false);
        addView(frameLayout, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        progressBar = new RadialProgressView(context, resourcesProvider);
        progressBar.setSize(dp(28));
        progressBar.setProgressColor(getThemedColor(Theme.key_chat_serviceText));
        frameLayout.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
    }

    public boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

    private float viewTop;
    private int backgroundHeight;
    public void applyServiceShaderMatrix() {
        applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, getX(), viewTop);
    }

    private void applyServiceShaderMatrix(int measuredWidth, int backgroundHeight, float x, float viewTop) {
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop);
        } else {
            Theme.applyServiceShaderMatrix(measuredWidth, backgroundHeight, x, viewTop);
        }
    }

    public void setVisiblePart(float viewTop, int backgroundHeight) {
        if (this.viewTop != viewTop) {
            invalidate();
        }
        this.viewTop = viewTop;
        this.backgroundHeight = backgroundHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(44), MeasureSpec.EXACTLY));
    }

    public void setProgressVisible(boolean value) {
        frameLayout.setVisibility(value ? VISIBLE : INVISIBLE);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }
}
