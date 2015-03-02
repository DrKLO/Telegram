/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Adapters.BaseSectionsAdapter;

public class SectionsListView extends ListView implements AbsListView.OnScrollListener {

    private View pinnedHeader;
    private OnScrollListener mOnScrollListener;
    private BaseSectionsAdapter mAdapter;
    private int currentStartSection = -1;

    public SectionsListView(Context context) {
        super(context);
        super.setOnScrollListener(this);
    }

    public SectionsListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setOnScrollListener(this);
    }

    public SectionsListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        super.setOnScrollListener(this);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (mAdapter == adapter) {
            return;
        }
        pinnedHeader = null;
        if (adapter instanceof BaseSectionsAdapter) {
            mAdapter = (BaseSectionsAdapter) adapter;
        } else {
            mAdapter = null;
        }
        super.setAdapter(adapter);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mOnScrollListener != null) {
            mOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
        if (mAdapter == null) {
            return;
        }

        if (mAdapter.getCount() == 0) {
            return;
        }

        int startSection = mAdapter.getSectionForPosition(firstVisibleItem);
        if (currentStartSection != startSection || pinnedHeader == null) {
            pinnedHeader = getSectionHeaderView(startSection, pinnedHeader);
            currentStartSection = startSection;
        }

        int count = mAdapter.getCountForSection(startSection);

        int pos = mAdapter.getPositionInSectionForPosition(firstVisibleItem);
        if (pos == count - 1) {
            View child = getChildAt(0);
            int headerHeight = pinnedHeader.getHeight();
            int headerTop = 0;
            if (child != null) {
                int available = child.getTop() + child.getHeight();
                if (available < headerHeight) {
                    headerTop = available - headerHeight;
                }
            } else {
                headerTop = -AndroidUtilities.dp(100);
            }
            if (headerTop < 0) {
                pinnedHeader.setTag(headerTop);
            } else {
                pinnedHeader.setTag(0);
            }
        } else {
            pinnedHeader.setTag(0);
        }

        invalidate();
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChanged(view, scrollState);
        }
    }

    private View getSectionHeaderView(int section, View oldView) {
        boolean shouldLayout = oldView == null;
        View view = mAdapter.getSectionHeaderView(section, oldView, this);
        if (shouldLayout) {
            ensurePinnedHeaderLayout(view, false);
        }
        return view;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mAdapter == null || pinnedHeader == null) {
            return;
        }
        ensurePinnedHeaderLayout(pinnedHeader, true);
    }

    private void ensurePinnedHeaderLayout(View header, boolean forceLayout) {
        if (header.isLayoutRequested() || forceLayout) {
            int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            try {
                header.measure(widthSpec, heightSpec);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mAdapter == null || pinnedHeader == null) {
            return;
        }
        int saveCount = canvas.save();
        int top = (Integer)pinnedHeader.getTag();
        canvas.translate(LocaleController.isRTL ? getWidth() - pinnedHeader.getWidth() : 0, top);
        canvas.clipRect(0, 0, getWidth(), pinnedHeader.getMeasuredHeight());
        pinnedHeader.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        mOnScrollListener = l;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        super.setOnItemClickListener(listener);
    }
}
