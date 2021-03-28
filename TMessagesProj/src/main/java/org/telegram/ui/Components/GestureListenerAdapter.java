package org.telegram.ui.Components;

import android.view.GestureDetector;
import android.view.MotionEvent;

public interface GestureListenerAdapter extends GestureDetector.OnGestureListener {

    @Override
    default boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    default void onShowPress(MotionEvent e) { }

    @Override
    default boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    default boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    default void onLongPress(MotionEvent e) { }

    @Override
    default boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }
}
