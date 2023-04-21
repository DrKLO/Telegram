package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ProfileActivity;

public class ItemOptions {
    public static ItemOptions makeOptions(@NonNull BaseFragment fragment, @NonNull View scrimView) {
        return new ItemOptions(fragment, scrimView);
    }

    public static ItemOptions makeOptions(@NonNull ViewGroup container, @NonNull View scrimView) {
        return new ItemOptions(container, scrimView);
    }

    private ViewGroup container;
    private BaseFragment fragment;
    private Context context;
    private View scrimView;
    private Drawable scrimViewBackground;
    private int gravity = Gravity.RIGHT;

    private ActionBarPopupWindow actionBarPopupWindow;
    private final float[] point = new float[2];

    private float translateX, translateY;
    private int dimAlpha = Theme.isCurrentThemeDark() ? 0x66 : 0x33;

    private ActionBarPopupWindow.ActionBarPopupWindowLayout layout;

    private ItemOptions(BaseFragment fragment, View scrimView) {
        if (fragment.getContext() == null) {
            return;
        }

        this.fragment = fragment;
        this.context = fragment.getContext();
        this.scrimView = scrimView;

        init();
    }

    private ItemOptions(ViewGroup container, View scrimView) {
        if (container.getContext() == null) {
            return;
        }

        this.container = container;
        this.context = container.getContext();
        this.scrimView = scrimView;

        init();
    }

    private void init() {
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
        layout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow != null && actionBarPopupWindow.isShowing()) {
                actionBarPopupWindow.dismiss();
            }
        });
    }

    public ItemOptions addIf(boolean condition, int iconResId, CharSequence text, Runnable onClickListener) {
        if (!condition) {
            return this;
        }
        return add(iconResId, text, onClickListener);
    }

    public ItemOptions addIf(boolean condition, int iconResId, CharSequence text, boolean isRed, Runnable onClickListener) {
        if (!condition) {
            return this;
        }
        return add(iconResId, text, isRed, onClickListener);
    }

    public ItemOptions add(int iconResId, CharSequence text, Runnable onClickListener) {
        return add(iconResId, text, false, onClickListener);
    }

    public ItemOptions add(int iconResId, CharSequence text, boolean isRed, Runnable onClickListener) {
        if (context == null) {
            return this;
        }

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false);
        subItem.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18 + 8), 0);
        subItem.setTextAndIcon(text, iconResId);
        if (isRed) {
            subItem.setColors(Theme.getColor(Theme.key_text_RedRegular), Theme.getColor(Theme.key_text_RedRegular));
            subItem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular), .12f));
        }
        subItem.setOnClickListener(view1 -> {
            if (actionBarPopupWindow != null) {
                actionBarPopupWindow.dismiss();
            }
            if (onClickListener != null) {
                onClickListener.run();
            }
        });
        if (minWidthDp > 0) {
            subItem.setMinimumWidth(AndroidUtilities.dp(minWidthDp));
        }
        layout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        return this;
    }

    public ItemOptions setScrimViewBackground(Drawable drawable) {
        this.scrimViewBackground = drawable;
        return this;
    }

    public ItemOptions setGravity(int gravity) {
        this.gravity = gravity;
        return this;
    }

    public ItemOptions translate(float x, float y) {
        this.translateX += x;
        this.translateY += y;
        return this;
    }

    private int minWidthDp;

    public ItemOptions setMinWidth(int minWidthDp) {
        this.minWidthDp = minWidthDp;
        return this;
    }

    public ItemOptions setDimAlpha(int dimAlpha) {
        this.dimAlpha = dimAlpha;
        return this;
    }

    public int getItemsCount() {
        return layout.getItemsCount();
    }

    public ItemOptions show() {
        if (actionBarPopupWindow != null) {
            return this;
        }

        if (layout.getItemsCount() <= 0) {
            return this;
        }

        View first = layout.getItemAt(0), last = layout.getItemAt(layout.getItemsCount() - 1);
        if (first instanceof ActionBarMenuSubItem) {
            ((ActionBarMenuSubItem) first).updateSelectorBackground(true, first == last);
        }
        if (last instanceof ActionBarMenuSubItem) {
            ((ActionBarMenuSubItem) last).updateSelectorBackground(last == first, true);
        }

        if (minWidthDp > 0) {
            for (int i = 0; i < layout.getItemsCount(); ++i) {
                layout.getItemAt(i).setMinimumWidth(AndroidUtilities.dp(minWidthDp));
            }
        }

        ViewGroup container = this.container == null ? fragment.getParentLayout().getOverlayContainerView() : this.container;

        if (context == null || container == null) {
            return this;
        }

        float x = 0;
        float y = AndroidUtilities.displaySize.y / 2f;
        if (scrimView != null) {
            getPointOnScreen(scrimView, container, point);
            y = point[1];
        }

        final Bitmap cachedBitmap;
        final Paint cachedBitmapPaint;
        if (scrimView instanceof UserCell && fragment instanceof ProfileActivity) {
            cachedBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            cachedBitmap = Bitmap.createBitmap(scrimView.getWidth(), scrimView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cachedBitmap);
            scrimView.draw(canvas);
        } else {
            cachedBitmapPaint = null;
            cachedBitmap = null;
        }

        final float clipTop;
        if (scrimView != null && scrimView.getParent() instanceof View) {
            clipTop = ((View) scrimView.getParent()).getY() + scrimView.getY();
        } else {
            clipTop = 0;
        }

        final int dim = ColorUtils.setAlphaComponent(0x00000000, dimAlpha);

        View dimView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawColor(dim);

                if (cachedBitmap != null && scrimView.getParent() instanceof View) {
                    canvas.save();
                    if (clipTop < 1) {
                        canvas.clipRect(0, point[1] - clipTop + 1, getMeasuredWidth(), getMeasuredHeight());
                    }
                    canvas.translate(point[0], point[1]);

                    if (scrimViewBackground != null) {
                        scrimViewBackground.setBounds(0, 0, cachedBitmap.getWidth(), cachedBitmap.getHeight());
                        scrimViewBackground.draw(canvas);
                    }
                    canvas.drawBitmap(cachedBitmap, 0, 0, cachedBitmapPaint);
                    canvas.restore();
                } else if (scrimView != null && scrimView.getParent() instanceof View) {
                    canvas.save();
                    if (clipTop < 1) {
                        canvas.clipRect(0, point[1] - clipTop + 1, getMeasuredWidth(), getMeasuredHeight());
                    }
                    canvas.translate(point[0], point[1]);

                    if (scrimViewBackground != null) {
                        scrimViewBackground.setBounds(0, 0, scrimView.getWidth(), scrimView.getHeight());
                        scrimViewBackground.draw(canvas);
                    }
                    scrimView.draw(canvas);
                    canvas.restore();
                }
            }
        };

        ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
            dimView.invalidate();
            return true;
        };
        container.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        dimView.setAlpha(0);
        dimView.animate().alpha(1f).setDuration(150);
        layout.measure(View.MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), View.MeasureSpec.UNSPECIFIED));

        actionBarPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        actionBarPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                actionBarPopupWindow = null;
                dimView.animate().cancel();
                dimView.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (dimView.getParent() != null) {
                            container.removeView(dimView);
                        }
                        container.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                    }
                });
            }
        });
        actionBarPopupWindow.setOutsideTouchable(true);
        actionBarPopupWindow.setFocusable(true);
        actionBarPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        actionBarPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        actionBarPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        layout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow.isShowing()) {
                actionBarPopupWindow.dismiss(true);
            }
        });

        if (AndroidUtilities.isTablet()) {
            y += container.getPaddingTop();
            x -= container.getPaddingLeft();
        }
        int X;
        if (scrimView != null) {
            if (gravity == Gravity.RIGHT) {
                X = (int) (container.getMeasuredWidth() - layout.getMeasuredWidth() + container.getX() + x);
            } else {
                X = (int) (container.getX() + point[0]);
            }
        } else {
            X = (container.getWidth() - layout.getMeasuredWidth()) / 2; // in the center
        }
        int Y;
        if (scrimView != null) {
            if (y + layout.getMeasuredHeight() + AndroidUtilities.dp(16) > AndroidUtilities.displaySize.y) {
                // put above scrimView
                y -= scrimView.getMeasuredHeight();
                y -= layout.getMeasuredHeight();
            }
            Y = (int) (y + scrimView.getMeasuredHeight() + container.getY()); // under scrimView
        } else {
            Y = (container.getHeight() - layout.getMeasuredHeight()) / 2; // in the center
        }
        actionBarPopupWindow.showAtLocation(
            container,
            0,
            (int) (X + this.translateX),
            (int) (Y + this.translateY)
        );
        return this;
    }

    public void updateColors() {
        // TODO
    }

    public boolean isShown() {
        return actionBarPopupWindow != null && actionBarPopupWindow.isShowing();
    }

    public void dismiss() {
        if (actionBarPopupWindow != null) {
            actionBarPopupWindow.dismiss();
        }
    }

    public static void getPointOnScreen(View v, ViewGroup finalContainer, float[] point) {
        float x = 0;
        float y = 0;
        while (v != finalContainer) {
            y += v.getY();
            x += v.getX();
            if (v instanceof ScrollView) {
                x -= v.getScrollX();
                y -= v.getScrollY();
            }
            if (!(v.getParent() instanceof View)) {
                break;
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
}
