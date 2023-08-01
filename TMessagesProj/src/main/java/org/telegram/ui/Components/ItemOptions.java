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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ProfileActivity;

public class ItemOptions {

    public static ItemOptions makeOptions(@NonNull BaseFragment fragment, @NonNull View scrimView) {
        return new ItemOptions(fragment, scrimView);
    }

    public static ItemOptions makeOptions(@NonNull ViewGroup container, @NonNull View scrimView) {
        return makeOptions(container, null, scrimView);
    }

    public static ItemOptions makeOptions(@NonNull ViewGroup container, @Nullable Theme.ResourcesProvider resourcesProvider, @NonNull View scrimView) {
        return new ItemOptions(container, resourcesProvider, scrimView);
    }

    private ViewGroup container;
    private BaseFragment fragment;
    private Theme.ResourcesProvider resourcesProvider;

    private Context context;
    private View scrimView;
    private Drawable scrimViewBackground;
    private int gravity = Gravity.RIGHT;

    private ActionBarPopupWindow actionBarPopupWindow;
    private final float[] point = new float[2];

    private float translateX, translateY;
    private int dimAlpha;

    private View dimView;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    private android.graphics.Rect viewAdditionalOffsets = new android.graphics.Rect();
    private ViewGroup layout;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout lastLayout;

    private ItemOptions(BaseFragment fragment, View scrimView) {
        if (fragment.getContext() == null) {
            return;
        }

        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();
        this.context = fragment.getContext();
        this.scrimView = scrimView;
        this.dimAlpha = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) > .705 ? 0x66 : 0x33;

        if (fragment.getFragmentView() != null) {
            //discard all scrolls/gestures
            fragment.getFragmentView().getRootView().dispatchTouchEvent(AndroidUtilities.emptyMotionEvent());
        }
        init();
    }

    private ItemOptions(ViewGroup container, Theme.ResourcesProvider resourcesProvider, View scrimView) {
        if (container.getContext() == null) {
            return;
        }

        this.container = container;
        this.resourcesProvider = resourcesProvider;
        this.context = container.getContext();
        this.scrimView = scrimView;
        this.dimAlpha = AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) > .705 ? 0x66 : 0x33;

        init();
    }

    private void init() {
        lastLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, resourcesProvider);
        lastLayout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow != null && actionBarPopupWindow.isShowing()) {
                actionBarPopupWindow.dismiss();
            }
        });
        layout = lastLayout;
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

    public ItemOptions addIf(boolean condition, int iconResId, CharSequence text, Runnable onClickListener, Consumer<ActionBarMenuSubItem> onVewCreated) {
        if (!condition) {
            return this;
        }
        return add(iconResId, text, Theme.key_actionBarDefaultSubmenuItemIcon, Theme.key_actionBarDefaultSubmenuItem, onClickListener, onVewCreated);
    }

    public ItemOptions add(int iconResId, CharSequence text, Runnable onClickListener) {
        return add(iconResId, text, false, onClickListener);
    }

    public ItemOptions add(int iconResId, CharSequence text, boolean isRed, Runnable onClickListener) {
        return add(iconResId, text, isRed ? Theme.key_text_RedRegular : Theme.key_actionBarDefaultSubmenuItemIcon, isRed ? Theme.key_text_RedRegular : Theme.key_actionBarDefaultSubmenuItem, onClickListener, null);
    }

    public ItemOptions add(int iconResId, CharSequence text, int color, Runnable onClickListener) {
        return add(iconResId, text, color, color, onClickListener, null);
    }

    public ItemOptions add(int iconResId, CharSequence text, int iconColorKey, int textColorKey, Runnable onClickListener, Consumer<ActionBarMenuSubItem> onViewCreated) {
        if (context == null) {
            return this;
        }

        ActionBarMenuSubItem subItem = new ActionBarMenuSubItem(context, false, false, resourcesProvider);
        subItem.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18 + (LocaleController.isRTL ? 0 : 8)), 0);
        subItem.setTextAndIcon(text, iconResId);

        subItem.setColors(Theme.getColor(textColorKey, resourcesProvider), Theme.getColor(iconColorKey, resourcesProvider));
        subItem.setSelectorColor(Theme.multAlpha(Theme.getColor(textColorKey, resourcesProvider), .12f));

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
        if (onViewCreated != null) {
            onViewCreated.accept(subItem);
        }

        lastLayout.addView(subItem, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        return this;
    }

    public ItemOptions putPremiumLock(Runnable onLockClick) {
        if (onLockClick == null || context == null || lastLayout.getItemsCount() <= 0) {
            return this;
        }
        View lastChild = lastLayout.getItemAt(lastLayout.getItemsCount() - 1);
        if (!(lastChild instanceof ActionBarMenuSubItem)) {
            return this;
        }
        ActionBarMenuSubItem lastSubItem = (ActionBarMenuSubItem) lastChild;
        lastSubItem.setRightIcon(R.drawable.msg_mini_lock3);
        lastSubItem.getRightIcon().setAlpha(.4f);
        lastSubItem.setOnClickListener(view1 -> {
            if (onLockClick != null) {
                onLockClick.run();
            }
        });
        return this;
    }

    public ItemOptions addGap() {
        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, resourcesProvider);
        gap.setTag(R.id.fit_width_tag, 1);
        lastLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        return this;
    }

    public ItemOptions addSpaceGap() {
        if (layout == lastLayout) {
            layout = new LinearLayout(context);
            ((LinearLayout) layout).setOrientation(LinearLayout.VERTICAL);
            layout.addView(lastLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        layout.addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        lastLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, resourcesProvider);
        lastLayout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && actionBarPopupWindow != null && actionBarPopupWindow.isShowing()) {
                actionBarPopupWindow.dismiss();
            }
        });
        layout.addView(lastLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return this;
    }

    public ItemOptions addView() {
        return this;
    }

    public ItemOptions addText(CharSequence text, int textSizeDp) {
        final TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(8), AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        textView.setText(text);
        textView.setTag(R.id.fit_width_tag, 1);
        textView.setMaxWidth(AndroidUtilities.dp(200));
        lastLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
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
        if (lastLayout == layout) {
            return lastLayout.getItemsCount();
        } else {
            int itemsCount = 0;
            for (int j = 0; j < layout.getChildCount() - 1; ++j) {
                View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
                if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                    itemsCount += popupLayout.getItemsCount();
                }
            }
            return itemsCount;
        }
    }

    public ItemOptions show() {
        if (actionBarPopupWindow != null) {
            return this;
        }

        if (getItemsCount() <= 0) {
            return this;
        }

        for (int j = 0; j < layout.getChildCount() - 1; ++j) {
            View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
            if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                if (popupLayout.getItemsCount() <= 0) {
                    continue;
                }
                View first = popupLayout.getItemAt(0), last = popupLayout.getItemAt(popupLayout.getItemsCount() - 1);
                if (first instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) first).updateSelectorBackground(true, first == last);
                }
                if (last instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) last).updateSelectorBackground(last == first, true);
                }
            }
        }

        if (minWidthDp > 0) {
            for (int j = 0; j < layout.getChildCount() - 1; ++j) {
                View child = j == layout.getChildCount() - 1 ? lastLayout : layout.getChildAt(j);
                if (child instanceof ActionBarPopupWindow.ActionBarPopupWindowLayout) {
                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = (ActionBarPopupWindow.ActionBarPopupWindowLayout) child;
                    for (int i = 0; i < popupLayout.getItemsCount(); ++i) {
                        popupLayout.getItemAt(i).setMinimumWidth(AndroidUtilities.dp(minWidthDp));
                    }
                }
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
            cachedBitmap = Bitmap.createBitmap(scrimView.getWidth() + viewAdditionalOffsets.width(), scrimView.getHeight() + viewAdditionalOffsets.height(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cachedBitmap);
            canvas.translate(viewAdditionalOffsets.left, viewAdditionalOffsets.top);
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


        View dimViewLocal = dimView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawColor(dim);

                if (cachedBitmap != null && scrimView.getParent() instanceof View) {
                    canvas.save();
                    if (clipTop < 1) {
                        canvas.clipRect(-viewAdditionalOffsets.left, -viewAdditionalOffsets.top + point[1] - clipTop + 1, getMeasuredWidth() + viewAdditionalOffsets.right, getMeasuredHeight() + viewAdditionalOffsets.bottom);
                    }
                    canvas.translate(point[0], point[1]);

                    if (scrimViewBackground != null) {
                        scrimViewBackground.setBounds( -viewAdditionalOffsets.left, -viewAdditionalOffsets.top, scrimView.getWidth() + viewAdditionalOffsets.right, scrimView.getHeight() + viewAdditionalOffsets.bottom);
                        scrimViewBackground.draw(canvas);
                    }
                    canvas.drawBitmap(cachedBitmap, -viewAdditionalOffsets.left, -viewAdditionalOffsets.top, cachedBitmapPaint);
                    canvas.restore();
                } else if (scrimView != null && scrimView.getParent() instanceof View) {
                    canvas.save();
                    if (clipTop < 1) {
                        canvas.clipRect(-viewAdditionalOffsets.left, -viewAdditionalOffsets.top + point[1] - clipTop + 1, getMeasuredWidth() + viewAdditionalOffsets.right, getMeasuredHeight() + viewAdditionalOffsets.bottom);
                    }
                    canvas.translate(point[0], point[1]);

                    if (scrimViewBackground != null) {
                        scrimViewBackground.setBounds( -viewAdditionalOffsets.left, -viewAdditionalOffsets.top, scrimView.getWidth() + viewAdditionalOffsets.right, scrimView.getHeight() + viewAdditionalOffsets.bottom);
                        scrimViewBackground.draw(canvas);
                    }
                    scrimView.draw(canvas);
                    canvas.restore();
                }
            }
        };

        preDrawListener = () -> {
            dimViewLocal.invalidate();
            return true;
        };
        container.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        container.addView(dimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        dimView.setAlpha(0);
        dimView.animate().alpha(1f).setDuration(150);
        layout.measure(View.MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(container.getMeasuredHeight(), View.MeasureSpec.UNSPECIFIED));

        actionBarPopupWindow = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                ItemOptions.this.dismissDim(container);
            }
        };
        actionBarPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                actionBarPopupWindow = null;
                dismissDim(container);
            }
        });
        actionBarPopupWindow.setOutsideTouchable(true);
        actionBarPopupWindow.setFocusable(true);
        actionBarPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        actionBarPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        actionBarPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        actionBarPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        if (AndroidUtilities.isTablet()) {
            y += container.getPaddingTop();
            x -= container.getPaddingLeft();
        }
        int X;
        if (scrimView != null) {
            if (gravity == Gravity.RIGHT) {
                X = (int) (point[0] + scrimView.getMeasuredWidth() - layout.getMeasuredWidth() + container.getX());
            } else {
                X = (int) (container.getX() + point[0]);
            }
        } else {
            X = (container.getWidth() - layout.getMeasuredWidth()) / 2; // at the center
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
            Y = (container.getHeight() - layout.getMeasuredHeight()) / 2; // at the center
        }
        actionBarPopupWindow.showAtLocation(
            container,
            0,
            (int) (X + this.translateX),
            (int) (Y + this.translateY)
        );
        return this;
    }

    private void dismissDim(ViewGroup container) {
        if (dimView == null) {
            return;
        }
        View dimViewFinal = dimView;
        dimView = null;
        dimViewFinal.animate().cancel();
        dimViewFinal.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.removeFromParent(dimViewFinal);
                container.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            }
        });
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

    public ItemOptions setViewAdditionalOffsets(int left, int top, int right, int bottom) {
        viewAdditionalOffsets.set(left, top, right, bottom);
        return this;
    }
}
