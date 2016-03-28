/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Adapters.BaseSectionsAdapter;

import java.util.ArrayList;

public class LetterSectionsListView extends ListView implements AbsListView.OnScrollListener {

    private ArrayList<View> headers = new ArrayList<>();
    private ArrayList<View> headersCache = new ArrayList<>();
    private OnScrollListener mOnScrollListener;
    private BaseSectionsAdapter mAdapter;
    private int currentFirst = -1;
    private int currentVisible = -1;
    private int startSection;
    private int sectionsCount;

    public LetterSectionsListView(Context context) {
        super(context);
        super.setOnScrollListener(this);
    }

    public LetterSectionsListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        super.setOnScrollListener(this);
    }

    public LetterSectionsListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        super.setOnScrollListener(this);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        if (mAdapter == adapter) {
            return;
        }
        headers.clear();
        headersCache.clear();
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

        headersCache.addAll(headers);
        headers.clear();

        if (mAdapter.getCount() == 0) {
            return;
        }

        if (currentFirst != firstVisibleItem || currentVisible != visibleItemCount) {
            currentFirst = firstVisibleItem;
            currentVisible = visibleItemCount;

            sectionsCount = 1;
            startSection = mAdapter.getSectionForPosition(firstVisibleItem);
            int itemNum = firstVisibleItem + mAdapter.getCountForSection(startSection) - mAdapter.getPositionInSectionForPosition(firstVisibleItem);
            while (true) {
                if (itemNum >= firstVisibleItem + visibleItemCount) {
                    break;
                }
                itemNum += mAdapter.getCountForSection(startSection + sectionsCount);
                sectionsCount++;
            }
        }

        int itemNum = firstVisibleItem;
        for (int a = startSection; a < startSection + sectionsCount; a++) {
            View header = null;
            if (!headersCache.isEmpty()) {
                header = headersCache.get(0);
                headersCache.remove(0);
            }
            header = getSectionHeaderView(a, header);
            headers.add(header);
            int count = mAdapter.getCountForSection(a);
            if (a == startSection) {
                int pos = mAdapter.getPositionInSectionForPosition(itemNum);
                if (pos == count - 1) {
                    header.setTag(-header.getHeight());
                } else if (pos == count - 2) {
                    View child = getChildAt(itemNum - firstVisibleItem);
                    int headerTop;
                    if (child != null) {
                        headerTop = child.getTop();
                    } else {
                        headerTop = -AndroidUtilities.dp(100);
                    }
                    if (headerTop < 0) {
                        header.setTag(headerTop);
                    } else {
                        header.setTag(0);
                    }
                } else {
                    header.setTag(0);
                }
                itemNum += count - mAdapter.getPositionInSectionForPosition(firstVisibleItem);
            } else {
                View child = getChildAt(itemNum - firstVisibleItem);
                if (child != null) {
                    header.setTag(child.getTop());
                } else {
                    header.setTag(-AndroidUtilities.dp(100));
                }
                itemNum += count;
            }
        }
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
        if (mAdapter == null || headers.isEmpty()) {
            return;
        }
        for (View header : headers) {
            ensurePinnedHeaderLayout(header, true);
        }
    }

    private void ensurePinnedHeaderLayout(View header, boolean forceLayout) {
        if (header.isLayoutRequested() || forceLayout) {
            ViewGroup.LayoutParams layoutParams = header.getLayoutParams();
            int heightSpec = MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY);
            int widthSpec = MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY);
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
        if (mAdapter == null || headers.isEmpty()) {
            return;
        }
        for (View header : headers) {
            int saveCount = canvas.save();
            int top = (Integer)header.getTag();
            canvas.translate(LocaleController.isRTL ? getWidth() - header.getWidth() : 0, top);
            canvas.clipRect(0, 0, getWidth(), header.getMeasuredHeight());
            if (top < 0) {
                canvas.saveLayerAlpha(0, top, header.getWidth(), top + canvas.getHeight(), (int)(255 * (1.0f + (float)top / (float)header.getMeasuredHeight())), Canvas.HAS_ALPHA_LAYER_SAVE_FLAG);
            }
            header.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        mOnScrollListener = l;
    }

    public void setOnItemClickListener(LetterSectionsListView.OnItemClickListener listener) {
        super.setOnItemClickListener(listener);
    }
}
