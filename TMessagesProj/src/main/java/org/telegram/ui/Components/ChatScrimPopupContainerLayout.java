package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.ui.ActionBar.ActionBarPopupWindow;

public class ChatScrimPopupContainerLayout extends LinearLayout {

    public View reactionsLayout;
    public ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout;

    public ChatScrimPopupContainerLayout(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (reactionsLayout != null && popupWindowLayout != null && popupWindowLayout.getSwipeBack() != null && reactionsLayout.getLayoutParams().width != LayoutHelper.WRAP_CONTENT) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int widthDiff = popupWindowLayout.getSwipeBack().getMeasuredWidth() - popupWindowLayout.getSwipeBack().getChildAt(0).getMeasuredWidth();
            ((LayoutParams)reactionsLayout.getLayoutParams()).rightMargin = widthDiff;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
