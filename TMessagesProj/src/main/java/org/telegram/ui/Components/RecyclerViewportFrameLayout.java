/*
 * This is the source code of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/** A RecyclerView item whose height always equals the usable RecyclerView viewport height. */
public class RecyclerViewportFrameLayout extends FrameLayout {

    private View viewportView;

    public RecyclerViewportFrameLayout(Context context) {
        super(context);
    }

    public void setViewportView(@Nullable View viewportView) {
        if (this.viewportView == viewportView) {
            return;
        }
        this.viewportView = viewportView;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final View viewport = viewportView != null ? viewportView : findRecyclerViewParent();
        final int viewportHeight = viewport == null ? 0 : Math.max(0,
                viewport.getMeasuredHeight() - viewport.getPaddingTop() - viewport.getPaddingBottom());
        if (viewportHeight > 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(viewportHeight, MeasureSpec.EXACTLY);
        }
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Nullable
    private RecyclerView findRecyclerViewParent() {
        ViewParent parent = getParent();
        while (parent instanceof View) {
            if (parent instanceof RecyclerView) {
                return (RecyclerView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
}
