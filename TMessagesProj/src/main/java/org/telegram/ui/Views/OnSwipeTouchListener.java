/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.GridView;
import android.widget.ListView;

public class OnSwipeTouchListener implements OnTouchListener {
    private float downX, downY;
    private boolean discard = false;

    public boolean onTouch(View v, MotionEvent event) {
        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN: {
                downX = event.getX();
                downY = event.getY();
                discard = false;
                return !(v instanceof ListView || v instanceof GridView);
            }
            case MotionEvent.ACTION_MOVE: {
                float upX = event.getX();
                float upY = event.getY();

                float deltaX = downX - upX;
                float deltaY = downY - upY;
                if (Math.abs(deltaY) > 40) {
                    discard = true;
                }

                if(!discard && Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 90) {
                    if(deltaX < 0) {
                        onSwipeRight();
                        return true;
                    }
                    if(deltaX > 0) {
                        onSwipeLeft();
                        return true;
                    }
                }

                break;
            }
            case MotionEvent.ACTION_UP: {
                onTouchUp(event);
                return false;
            }
        }
        return false;
    }


    /*private final GestureDetector gestureDetector = new GestureDetector(new GestureListener());

    public boolean onTouch(final View view, final MotionEvent motionEvent) {
        return gestureDetector.onTouchEvent(motionEvent);
    }

    private final class GestureListener extends SimpleOnGestureListener {

        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;
        private long lastTime = 0;

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }



        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            boolean result = false;
            try {
                int mask = e1.getActionMasked();
                Log.e("tmessages", "event1" + e1);
                Log.e("tmessages", "event2" + e2);
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                float velocityX = 0;
                if (lastTime != 0)
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight();
                        } else {
                            onSwipeLeft();
                        }
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }



//        @Override
//        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//            boolean result = false;
//            try {
//                float diffY = e2.getY() - e1.getY();
//                float diffX = e2.getX() - e1.getX();
//                if (Math.abs(diffX) > Math.abs(diffY)) {
//                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
//                        if (diffX > 0) {
//                            onSwipeRight();
//                        } else {
//                            onSwipeLeft();
//                        }
//                    }
//                } else {
//                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
//                        if (diffY > 0) {
//                            onSwipeBottom();
//                        } else {
//                            onSwipeTop();
//                        }
//                    }
//                }
//            } catch (Exception exception) {
//                exception.printStackTrace();
//            }
//            return result;
//        }
    }
*/
    public void onTouchUp(MotionEvent event) {

    }

    public void onSwipeRight() {

    }

    public void onSwipeLeft() {

    }
}
