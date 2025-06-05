package org.telegram.ui.Components;

import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;

public abstract class CustomPopupMenu {

    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
    ActionBarPopupWindow popupWindow;
    boolean isShowing;

    public CustomPopupMenu(Context context, Theme.ResourcesProvider resourcesProvider, boolean containsSwipeBack) {
        popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert2, resourcesProvider, containsSwipeBack ? ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK : 0);
        popupLayout.setAnimationEnabled(false);
        popupLayout.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (popupWindow != null && popupWindow.isShowing()) {
                    v.getHitRect(AndroidUtilities.rectTmp2);
                    if (!AndroidUtilities.rectTmp2.contains((int) event.getX(), (int) event.getY())) {
                        popupWindow.dismiss();
                    }
                }
            }
            return false;
        });
        popupLayout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        });
        popupLayout.setShownFromBottom(false);

        onCreate(popupLayout);

        popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindow.setAnimationEnabled(false);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        popupWindow.setOnDismissListener(() -> {
            onDismissed();
            isShowing = false;
        });
    }

    public void show(View anchorView, int x, int y) {
        isShowing = true;
        popupWindow.showAsDropDown(anchorView, x, y);
    }


    protected abstract void onCreate(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout);

    protected abstract void onDismissed();

    public void dismiss() {
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return isShowing;
    }
}
