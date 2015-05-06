/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.support.widget.RecyclerView;

public class RecyclerExListView extends RecyclerView {

    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private RecyclerView.OnScrollListener onScrollListener;
    private OnInterceptTouchListener onInterceptTouchListener;
    private View emptyView;
    private Runnable selectChildRunnable;

    private GestureDetector mGestureDetector;
    private View currentChildView;
    private int currentChildPosition;
    private boolean interceptedByChild;
    private boolean wasPressed;

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnItemLongClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnInterceptTouchListener {
        boolean onInterceptTouchEvent(MotionEvent event);
    }

    private class RecyclerListViewItemClickListener implements RecyclerView.OnItemTouchListener {

        public RecyclerListViewItemClickListener(Context context) {
            mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    if (currentChildView != null && onItemClickListener != null) {
                        currentChildView.playSoundEffect(SoundEffectConstants.CLICK);
                        onItemClickListener.onItemClick(currentChildView, currentChildPosition);
                        if (selectChildRunnable != null) {
                            currentChildView.setPressed(true);
                            final View view = currentChildView;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (view != null) {
                                        view.setPressed(false);
                                    }
                                }
                            }, ViewConfiguration.getPressedStateDuration());
                            AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
                            selectChildRunnable = null;
                            currentChildView = null;
                            interceptedByChild = false;
                        }
                    }
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (currentChildView != null && onItemLongClickListener != null) {
                        currentChildView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        onItemLongClickListener.onItemClick(currentChildView, currentChildPosition);
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent e) {
            int action = e.getActionMasked();
            boolean isScrollIdle = RecyclerExListView.this.getScrollState() == RecyclerExListView.SCROLL_STATE_IDLE;

            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && currentChildView == null && isScrollIdle) {
                currentChildView = view.findChildViewUnder(e.getX(), e.getY());
                currentChildPosition = -1;
                if (currentChildView != null) {
                    currentChildPosition = view.getChildPosition(currentChildView);
                    MotionEvent childEvent = MotionEvent.obtain(0, 0, e.getActionMasked(), e.getX() - currentChildView.getLeft(), e.getY() - currentChildView.getTop(), 0);
                    if (currentChildView.onTouchEvent(childEvent)) {
                        interceptedByChild = true;
                    }
                    childEvent.recycle();
                }
            }

            if (currentChildView != null && !interceptedByChild) {
                mGestureDetector.onTouchEvent(e);
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (!interceptedByChild && currentChildView != null) {
                    selectChildRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (selectChildRunnable != null && currentChildView != null) {
                                currentChildView.setPressed(true);
                                selectChildRunnable = null;
                            }
                        }
                    };
                    AndroidUtilities.runOnUIThread(selectChildRunnable, ViewConfiguration.getTapTimeout());
                }
            } else if (currentChildView != null && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL || !isScrollIdle)) {
                if (selectChildRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
                    selectChildRunnable = null;
                }
                currentChildView.setPressed(false);
                currentChildView = null;
                interceptedByChild = false;
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView view, MotionEvent e) {

        }
    }

    private AdapterDataObserver observer = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty();
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty();
        }
    };

    public void init(Context context) {
        super.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState != SCROLL_STATE_IDLE && currentChildView != null) {
                    if (selectChildRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
                        selectChildRunnable = null;
                    }
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    mGestureDetector.onTouchEvent(event);
                    currentChildView.onTouchEvent(event);
                    event.recycle();
                    currentChildView.setPressed(false);
                    currentChildView = null;
                    interceptedByChild = false;
                }
                if (onScrollListener != null) {
                    onScrollListener.onScrollStateChanged(recyclerView, newState);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (onScrollListener != null) {
                    onScrollListener.onScrolled(recyclerView, dx, dy);
                }
            }
        });
        addOnItemTouchListener(new RecyclerListViewItemClickListener(context));
    }

    public RecyclerExListView(Context context) {
        super(context);

        /*setVerticalScrollBarEnabled(true);
        try {
            TypedArray a = context.getTheme().obtainStyledAttributes(new int[0]);
            Method initializeScrollbars = android.view.View.class.getDeclaredMethod("initializeScrollbars", TypedArray.class);
            initializeScrollbars.invoke(this, a);
            a.recycle();
        } catch (Throwable e) {
            FileLog.e("tmessages", e);
        }*/

        init(context);
    }

    public RecyclerExListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RecyclerExListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        onItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        onItemLongClickListener = listener;
    }

    public void setEmptyView(View view) {
        if (emptyView == view) {
            return;
        }
        emptyView = view;
        checkIfEmpty();
    }

    public View getEmptyView() {
        return emptyView;
    }

    public void invalidateViews() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            getChildAt(a).invalidate();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return onInterceptTouchListener != null && onInterceptTouchListener.onInterceptTouchEvent(e) || super.onInterceptTouchEvent(e);
    }

    private void checkIfEmpty() {
        if (emptyView == null || getAdapter() == null) {
            return;
        }
        boolean emptyViewVisible = getAdapter().getItemCount() == 0;
        emptyView.setVisibility(emptyViewVisible ? VISIBLE : INVISIBLE);
        setVisibility(emptyViewVisible ? INVISIBLE : VISIBLE);
    }

    @Override
    public void setOnScrollListener(OnScrollListener listener) {
        onScrollListener = listener;
    }

    public void setOnInterceptTouchListener(OnInterceptTouchListener listener) {
        onInterceptTouchListener = listener;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        final Adapter oldAdapter = getAdapter();
        if (oldAdapter != null) {
            oldAdapter.unregisterAdapterDataObserver(observer);
        }
        super.setAdapter(adapter);
        if (adapter != null) {
            adapter.registerAdapterDataObserver(observer);
        }
        checkIfEmpty();
    }

    @Override
    public void stopScroll() {
        try {
            super.stopScroll();
        } catch (NullPointerException exception) {
            /**
             *  The mLayout has been disposed of before the
             *  RecyclerView and this stops the application
             *  from crashing.
             */
        }
    }
}
