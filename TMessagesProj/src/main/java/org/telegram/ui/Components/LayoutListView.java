/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;

public class LayoutListView extends ListView {

    public interface OnInterceptTouchEventListener {
        boolean onInterceptTouchEvent(MotionEvent event);
    }

    private OnInterceptTouchEventListener onInterceptTouchEventListener;
    private int height = -1;
    private int forceTop = Integer.MIN_VALUE;

    public LayoutListView(Context context) {
        super(context);
    }

    public LayoutListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LayoutListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnInterceptTouchEventListener(OnInterceptTouchEventListener listener) {
        onInterceptTouchEventListener = listener;
    }

    public void setForceTop(int value) {
        forceTop = value;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (onInterceptTouchEventListener != null) {
            return onInterceptTouchEventListener.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View v = getChildAt(getChildCount() - 1);
        int scrollTo = getLastVisiblePosition();
        if (v != null && height > 0 && changed && ((bottom - top) < height)) {
            int lastTop = forceTop == Integer.MIN_VALUE ? (bottom - top) - (height - v.getTop()) - getPaddingTop() : forceTop;
            forceTop = Integer.MIN_VALUE;
            setSelectionFromTop(scrollTo, lastTop);
            super.onLayout(changed, left, top, right, bottom);

//            post(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        setSelectionFromTop(scrollTo, lastTop);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
        } else {
            if (forceTop != Integer.MIN_VALUE) {
                setSelectionFromTop(scrollTo, forceTop);
                forceTop = Integer.MIN_VALUE;
            }
            try {
                super.onLayout(changed, left, top, right, bottom);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        height = (bottom - top);
    }
}
