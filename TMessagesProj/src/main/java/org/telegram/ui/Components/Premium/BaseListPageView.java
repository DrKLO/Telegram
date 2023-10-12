package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public abstract class BaseListPageView extends FrameLayout implements PagerHeaderView {

    final Theme.ResourcesProvider resourcesProvider;
    final RecyclerListView recyclerListView;
    final LinearLayoutManager layoutManager;
    RecyclerView.Adapter adapter;

    public BaseListPageView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        recyclerListView = new RecyclerListView(context, resourcesProvider);
        recyclerListView.setNestedScrollingEnabled(true);
      //  recyclerListView.setOverScrollMode(OVER_SCROLL_NEVER);
        adapter = createAdapter();
        recyclerListView.setAdapter(adapter);
        recyclerListView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        recyclerListView.setClipToPadding(false);
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public abstract RecyclerView.Adapter createAdapter();

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
        if (dividerPaint == null) {
            dividerPaint = Theme.dividerPaint;
        }
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, dividerPaint);
    }

    @Override
    public void setOffset(float translationX) {
        float progress = Math.abs(translationX / getMeasuredWidth());
        if (progress == 1f) {
            if (recyclerListView.findViewHolderForAdapterPosition(0) == null || recyclerListView.findViewHolderForAdapterPosition(0).itemView.getTop() != recyclerListView.getPaddingTop()) {
                recyclerListView.scrollToPosition(0);
            }
        }
    }

    public void setTopOffset(int topOffset) {
        recyclerListView.setPadding(0, topOffset, 0, 0);
    }
}
