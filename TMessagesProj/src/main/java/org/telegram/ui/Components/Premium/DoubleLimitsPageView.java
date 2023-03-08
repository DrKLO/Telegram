package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class DoubleLimitsPageView extends FrameLayout implements PagerHeaderView {

    final RecyclerListView recyclerListView;
    final LinearLayoutManager layoutManager;
    DoubledLimitsBottomSheet.Adapter adapter;

    public DoubleLimitsPageView(Context context) {
        super(context);
        recyclerListView = new RecyclerListView(context);
        adapter = new DoubledLimitsBottomSheet.Adapter(UserConfig.selectedAccount, true);
        recyclerListView.setAdapter(adapter);
        recyclerListView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        recyclerListView.setClipToPadding(false);
        adapter.containerView = this;
        addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        adapter.measureGradient(getContext(), getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
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
