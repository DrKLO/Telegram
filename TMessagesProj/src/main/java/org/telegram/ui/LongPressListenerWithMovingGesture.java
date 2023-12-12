package org.telegram.ui;

import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.GestureDetector2;


public class LongPressListenerWithMovingGesture implements View.OnTouchListener {

    View view;
    ActionBarPopupWindow submenu;
    Rect rect = new Rect();
    boolean subItemClicked;
    boolean tapConfirmedOrCanceled;
    GestureDetector2 gestureDetector2 = new GestureDetector2(new GestureDetector2.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            if (view != null) {
                view.setPressed(true);
                view.setSelected(true);
                if (Build.VERSION.SDK_INT >= 21) {
                    if (Build.VERSION.SDK_INT == 21 && view.getBackground() != null) {
                        view.getBackground().setVisible(true, false);
                    }
                    view.drawableHotspotChanged(e.getX(), e.getY());
                }
            }
            return true;
        }

        @Override
        public void onUp(MotionEvent e) {
            if (view != null) {
                view.setPressed(false);
                view.setSelected(false);
                if (Build.VERSION.SDK_INT == 21 && view.getBackground() != null) {
                    view.getBackground().setVisible(false, false);
                }
            }
            if (selectedMenuView != null && !subItemClicked) {
                selectedMenuView.callOnClick();
                subItemClicked = true;
            }
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (tapConfirmedOrCanceled) {
                return false;
            }
            if (view != null) {
                view.callOnClick();
                tapConfirmedOrCanceled = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (view != null) {
                LongPressListenerWithMovingGesture.this.onLongPress();
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }
    });

    private int[] location = new int[2];
    private View selectedMenuView;

    public LongPressListenerWithMovingGesture() {
        gestureDetector2.setIsLongpressEnabled(true);
    }

    float startFromX;
    float startFromY;
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        view = v;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startFromX = event.getX();
            startFromY = event.getY();
            tapConfirmedOrCanceled = false;
        }
        gestureDetector2.onTouchEvent(event);
        if (submenu != null && !subItemClicked && event.getAction() == MotionEvent.ACTION_MOVE) {
            view.getLocationOnScreen(location);
            float x = event.getX() + location[0];
            float y = event.getY() + location[1];
            submenu.getContentView().getLocationOnScreen(location);
            x -= location[0];
            y -= location[1];
            selectedMenuView = null;
            ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) submenu.getContentView();
            for (int a = 0; a < popupLayout.getItemsCount(); a++) {
                View child = popupLayout.getItemAt(a);
                child.getHitRect(rect);
                Object tag = child.getTag();
                if (child.getVisibility() == View.VISIBLE && child.isClickable()) {
                    if (!rect.contains((int) x, (int) y)) {
                        child.setPressed(false);
                        child.setSelected(false);
                        if (Build.VERSION.SDK_INT == 21 && child.getBackground() != null) {
                            child.getBackground().setVisible(false, false);
                        }
                    } else {
                        child.setPressed(true);
                        child.setSelected(true);
                        if (Build.VERSION.SDK_INT >= 21) {
                            if (Build.VERSION.SDK_INT == 21 && child.getBackground() != null) {
                                child.getBackground().setVisible(true, false);
                            }
                            child.drawableHotspotChanged(x, y - child.getTop());
                        }
                        selectedMenuView = child;
                    }
                }
            }
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && Math.abs(event.getX() - startFromX) > AndroidUtilities.touchSlop * 2 || Math.abs(event.getY() - startFromY) > AndroidUtilities.touchSlop * 2) {
            tapConfirmedOrCanceled = true;
            view.setPressed(false);
            view.setSelected(false);
        }
        if (event.getAction() == MotionEvent.ACTION_UP && !subItemClicked && !tapConfirmedOrCanceled) {
            if (selectedMenuView != null) {
                selectedMenuView.callOnClick();
                subItemClicked = true;
            } else if (submenu == null && view != null) {
                view.callOnClick();
            }
        }
        return true;
    }

    public void setSubmenu(ActionBarPopupWindow actionBarPopupWindow) {
        submenu = actionBarPopupWindow;
    }

    public void onLongPress() {

    }
}
