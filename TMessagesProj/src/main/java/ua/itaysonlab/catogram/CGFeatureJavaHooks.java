package ua.itaysonlab.catogram;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

public class CGFeatureJavaHooks {
    private static void getPointOnScreen(FrameLayout frameLayout, FrameLayout finalContainer, float[] point) {
        float x = 0;
        float y = 0;
        View v = frameLayout;
        while (v != finalContainer) {
            y += v.getY();
            x += v.getX();
            if (v instanceof ScrollView) {
                y -= v.getScrollY();
            }
            v = (View) v.getParent();
            if (!(v instanceof ViewGroup)) {
                return;
            }
        }
        x -= finalContainer.getPaddingLeft();
        y -= finalContainer.getPaddingTop();
        point[0] = x;
        point[1] = y;
    }

    public static ActionBarPopupWindow createPopupWindow(FrameLayout container, FrameLayout callerContainer, Context context, List<PopupItem> items) {
        ActionBarPopupWindow.ActionBarPopupWindowLayout layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);

        for (PopupItem item : items) {
            ActionBarMenuSubItem subItem;
            subItem = new ActionBarMenuSubItem(context, false, true);
            subItem.setTextAndIcon(item.text, item.icon);
            //subItem.setColors(Theme.getColor(Theme.key_windowBackgroundWhiteRedText), Theme.getColor(Theme.key_windowBackgroundWhiteRedText));
            subItem.setOnClickListener(item.onClick);
            layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        if (container != null) {
            float x = 0;
            float y;

            float[] point = new float[2];
            getPointOnScreen(callerContainer, container, point);
            y = point[1];

            final FrameLayout finalContainer = container;
            View dimView = new View(context) {

                @Override
                protected void onDraw(Canvas canvas) {
                    canvas.drawColor(0x33000000);
                    getPointOnScreen(callerContainer, finalContainer, point);
                    canvas.save();
                    canvas.translate(point[0], point[1]);
                    callerContainer.draw(canvas);
                    canvas.restore();
                }
            };
            container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            dimView.setAlpha(0);
            dimView.animate().alpha(1f).setDuration(150);
            layout.measure(View.MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), View.MeasureSpec.UNSPECIFIED));

            ActionBarPopupWindow actionBarPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    dimView.animate().cancel();
                    dimView.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (dimView.getParent() != null) {
                                finalContainer.removeView(dimView);
                            }
                        }
                    });
                }
            };

            actionBarPopupWindow.setOutsideTouchable(true);
            actionBarPopupWindow.setClippingEnabled(true);
            actionBarPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
            actionBarPopupWindow.setFocusable(true);
            actionBarPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            actionBarPopupWindow.getContentView().setFocusableInTouchMode(true);

            layout.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow.isShowing()) {
                    actionBarPopupWindow.dismiss(true);
                }
            });

            if (AndroidUtilities.isTablet()) {
                y += container.getPaddingTop();
                x -= container.getPaddingLeft();
            }

            actionBarPopupWindow.showAtLocation(container, 0, (int) (container.getMeasuredWidth() - layout.getMeasuredWidth() - AndroidUtilities.dp(16) + container.getX() + x), (int) (y + callerContainer.getMeasuredHeight() + container.getY()));
            return actionBarPopupWindow;
        } else {
            return null;
        }
    }

    public static class PopupItem {
        public String text;
        public int icon;
        public View.OnClickListener onClick;

        public PopupItem(String text, int icon, View.OnClickListener onClick) {
            this.text = text;
            this.icon = icon;
            this.onClick = onClick;
        }
    }
}
