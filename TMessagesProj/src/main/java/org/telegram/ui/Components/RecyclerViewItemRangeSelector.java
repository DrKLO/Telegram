package org.telegram.ui.Components;

import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

import androidx.recyclerview.widget.RecyclerView;

public class RecyclerViewItemRangeSelector implements RecyclerView.OnItemTouchListener {

    private RecyclerView recyclerView;

    private int lastDraggedIndex = -1;
    private int initialSelection;
    private boolean dragSelectActive;

    private int hotspotTopBoundStart;
    private int hotspotTopBoundEnd;
    private int hotspotBottomBoundStart;
    private int hotspotBottomBoundEnd;
    private boolean inTopHotspot;
    private boolean inBottomHotspot;

    private int autoScrollVelocity;
    private boolean isAutoScrolling;

    private int hotspotHeight = AndroidUtilities.dp(80);
    private int hotspotOffsetTop;
    private int hotspotOffsetBottom;

    private RecyclerViewItemRangeSelectorDelegate delegate;

    private static final int AUTO_SCROLL_DELAY = 15;

    public interface RecyclerViewItemRangeSelectorDelegate {
        int getItemCount();
        void setSelected(View view, int index, boolean selected);
        boolean isSelected(int index);
        boolean isIndexSelectable(int index);
        void onStartStopSelection(boolean start);
    }

    private Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (recyclerView == null) {
                return;
            }
            if (inTopHotspot) {
                recyclerView.scrollBy(0, -autoScrollVelocity);
                AndroidUtilities.runOnUIThread(this);
            } else if (inBottomHotspot) {
                recyclerView.scrollBy(0, autoScrollVelocity);
                AndroidUtilities.runOnUIThread(this);
            }
        }
    };

    public RecyclerViewItemRangeSelector(RecyclerViewItemRangeSelectorDelegate recyclerViewItemRangeSelectorDelegate) {
        delegate = recyclerViewItemRangeSelectorDelegate;
    }

    private void disableAutoScroll() {
        hotspotHeight = -1;
        hotspotOffsetTop = -1;
        hotspotOffsetBottom = -1;
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        boolean adapterIsEmpty = rv.getAdapter() == null || rv.getAdapter().getItemCount() == 0;
        boolean result = dragSelectActive && !adapterIsEmpty;

        if (result) {
            recyclerView = rv;

            if (hotspotHeight > -1) {
                hotspotTopBoundStart = hotspotOffsetTop;
                hotspotTopBoundEnd = hotspotOffsetTop + hotspotHeight;
                hotspotBottomBoundStart = rv.getMeasuredHeight() - hotspotHeight - hotspotOffsetBottom;
                hotspotBottomBoundEnd = rv.getMeasuredHeight() - hotspotOffsetBottom;
            }
        }

        if (result && e.getAction() == MotionEvent.ACTION_UP) {
            onDragSelectionStop();
        }
        return result;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        View v = rv.findChildViewUnder(e.getX(), e.getY());
        int itemPosition;
        if (v != null) {
            itemPosition = rv.getChildAdapterPosition(v);
        } else {
            itemPosition = RecyclerView.NO_POSITION;
        }
        float y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_UP: {
                onDragSelectionStop();
                return;
            }
            case MotionEvent.ACTION_MOVE: {
                if (hotspotHeight > -1) {
                    if (y >= hotspotTopBoundStart && y <= hotspotTopBoundEnd) {
                        inBottomHotspot = false;
                        if (!inTopHotspot) {
                            inTopHotspot = true;
                            AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                            AndroidUtilities.runOnUIThread(autoScrollRunnable);
                        }
                        float simulatedFactor = (hotspotTopBoundEnd - hotspotTopBoundStart);
                        float simulatedY = y - hotspotTopBoundStart;
                        autoScrollVelocity = (int) (simulatedFactor - simulatedY) / 2;
                    } else if (y >= hotspotBottomBoundStart && y <= hotspotBottomBoundEnd) {
                        inTopHotspot = false;
                        if (!inBottomHotspot) {
                            inBottomHotspot = true;
                            AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                            AndroidUtilities.runOnUIThread(autoScrollRunnable);
                        }
                        float simulatedY = y + hotspotBottomBoundEnd;
                        float simulatedFactor = (hotspotBottomBoundStart + hotspotBottomBoundEnd);
                        autoScrollVelocity = (int) (simulatedY - simulatedFactor) / 2;
                    } else if (inTopHotspot || inBottomHotspot) {
                        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                        inTopHotspot = false;
                        inBottomHotspot = false;
                    }
                }

                if (itemPosition != RecyclerView.NO_POSITION) {
                    if (lastDraggedIndex == itemPosition) {
                        return;
                    }
                    lastDraggedIndex = itemPosition;
                    delegate.setSelected(v, lastDraggedIndex, !delegate.isSelected(lastDraggedIndex));
                    return;
                }
                break;
            }
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }

    public boolean setIsActive(View view, boolean active, int selection, boolean select) {
        if (active && dragSelectActive) {
            return false;
        }

        lastDraggedIndex = -1;
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        inTopHotspot = false;
        inBottomHotspot = false;

        if (!active) {
            initialSelection = -1;
            return false;
        }

        if (!delegate.isIndexSelectable(selection)) {
            dragSelectActive = false;
            initialSelection = -1;
            return false;
        }

        delegate.onStartStopSelection(true);
        delegate.setSelected(view, initialSelection, select);
        dragSelectActive = active;
        lastDraggedIndex = initialSelection = selection;

        return true;
    }

    private void onDragSelectionStop() {
        dragSelectActive = false;
        inTopHotspot = false;
        inBottomHotspot = false;
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        delegate.onStartStopSelection(false);
    }
}
