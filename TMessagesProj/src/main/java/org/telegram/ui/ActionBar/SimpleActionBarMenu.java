package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;
import java.util.HashMap;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.core.lambda.Destroyable;

/**
 * Row of buttons of {@link SimpleActionBar}, used for the regular menu and for the action mode.
 * The bar draws a glass pill of {@link #getPillWidth()} behind the visible items.
 */
public class SimpleActionBarMenu extends LinearLayout {
    final SimpleActionBar parentBar;
    final boolean isActionMode;

    SimpleActionBarMenu(Context context, SimpleActionBar bar, boolean isActionMode) {
        super(context);
        this.parentBar = bar;
        this.isActionMode = isActionMode;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
    }

    public SimpleActionBarMenuItem addItem(int id, int icon) {
        return addItemAt(-1, id, icon, null, null, dp(48), null);
    }

    public SimpleActionBarMenuItem addItem(int id, int icon, Theme.ResourcesProvider resourcesProvider) {
        return addItem(id, icon);
    }

    public SimpleActionBarMenuItem addItem(int id, Drawable drawable) {
        return addItemAt(-1, id, 0, null, drawable, dp(48), null);
    }

    public SimpleActionBarMenuItem addItem(int id, CharSequence text) {
        return addItemAt(-1, id, 0, text, null, 0, null);
    }

    public SimpleActionBarMenuItem addItemWithWidth(int id, int icon, int width) {
        return addItemAt(-1, id, icon, null, null, width, null);
    }

    public SimpleActionBarMenuItem addItemWithWidth(int id, int icon, int width, CharSequence title) {
        return addItemAt(-1, id, icon, null, null, width, title);
    }

    public SimpleActionBarMenuItem addItemWithWidth(int id, Drawable drawable, int width, CharSequence title) {
        return addItemAt(-1, id, 0, null, drawable, width, title);
    }

    public SimpleActionBarMenuItem addItemAt(int index, int id, int icon, CharSequence text, Drawable drawable, int width, CharSequence title) {
        final SimpleActionBarMenuItem item = new SimpleActionBarMenuItem(getContext(), this,
            parentBar.getItemsBackgroundColor(isActionMode), parentBar.getItemsColor(isActionMode), text != null, parentBar.resourcesProvider);
        item.setTag(id);
        final LinearLayout.LayoutParams lp;
        if (text != null) {
            item.textView.setText(text);
            lp = new LinearLayout.LayoutParams(width != 0 ? width : ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = lp.rightMargin = dp(6);
        } else {
            if (drawable instanceof RLottieDrawable) {
                item.iconView.setAnimation((RLottieDrawable) drawable);
            } else if (drawable != null) {
                item.iconView.setImageDrawable(drawable);
            } else if (icon != 0) {
                item.iconView.setImageResource(icon);
            }
            lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = lp.rightMargin = -dp(5);
        }
        addView(item, index, lp);
        item.setOnClickListener(v -> {
            if (item.hasSubMenu()) {
                if (parentBar.canOpenMenu()) {
                    item.toggleSubMenu();
                }
            } else if (item.isSearchField()) {
                parentBar.openSearchField(true, true);
            } else {
                onItemClick(id);
            }
        });
        if (title != null) {
            item.setContentDescription(title);
        }
        return item;
    }

    public SimpleActionBarMenuItem getItem(int id) {
        final View view = findViewWithTag(id);
        return view instanceof SimpleActionBarMenuItem ? (SimpleActionBarMenuItem) view : null;
    }

    public void clearItems() {
        removeAllViews();
    }

    public void onItemClick(int id) {
        parentBar.onItemClick(id);
    }

    public void hideAllPopupMenus() {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                ((ActionBarMenuItem) view).closeSubMenu();
            }
        }
    }

    public void onMenuButtonPressed() {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem && view.getVisibility() == VISIBLE) {
                final ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.hasSubMenu()) {
                    item.toggleSubMenu();
                    break;
                }
            }
        }
    }

    public void updateItemsColors() {
        final int color = parentBar.getItemsColor(isActionMode);
        final int selector = parentBar.getItemsBackgroundColor(isActionMode);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            if (view instanceof ActionBarMenuItem) {
                final ActionBarMenuItem item = (ActionBarMenuItem) view;
                item.setIconColor(color);
                if (item.textView != null) {
                    item.textView.setTextColor(color);
                }
                item.setBackground(Theme.createSelectorDrawable(selector, item.textView != null ? 5 : 1));
            }
        }
    }

    /* Glass pill: the buttons stay anchored to the right edge, appear and disappear in place, the rest slide */

    private final HashMap<View, Slot> slots = new HashMap<>();
    private final ArrayList<Slot> visibleSlots = new ArrayList<>();
    private final ListAnimator<Slot> listAnimator = new ListAnimator<>(animator -> onSlotsChanged(), CubicBezierInterpolator.EASE_OUT_QUINT, SimpleActionBar.DURATION);
    private boolean skipNextAnimation = true;

    public float getPillWidth() {
        final ListAnimator.Metadata metadata = listAnimator.getMetadata();
        return metadata.getTotalWidth() + dp(8) * metadata.getTotalVisibility();
    }

    /** Width of the pill after the running animation, known before the next layout. */
    public float getTargetPillWidth() {
        float width = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            if (isSlotVisible(view)) {
                width += getSlotWidth(view);
            }
        }
        return width > 0 ? width + dp(8) : 0;
    }

    boolean canAnimateItems() {
        return isLaidOut() && parentBar.isAnimationsAllowed() && (!isActionMode || parentBar.isActionModeShowed() && parentBar.getActionModeFactor() > 0);
    }

    private static void hideSlotView(View view) {
        if (view instanceof SimpleActionBarMenuItem) {
            ((SimpleActionBarMenuItem) view).onHideFinished();
        } else {
            view.setVisibility(GONE);
        }
    }

    void onItemShownChanged(boolean animated) {
        if (!animated || !parentBar.isAnimationsAllowed()) {
            skipNextAnimation = true;
        }
        requestLayout();
    }

    private static boolean isSlotVisible(View view) {
        return view.getVisibility() != GONE && (!(view instanceof SimpleActionBarMenuItem) || ((SimpleActionBarMenuItem) view).isItemShown());
    }

    private static int getSlotWidth(View view) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) view.getLayoutParams();
        final int width = view.getMeasuredWidth() > 0 || lp.width <= 0 ? view.getMeasuredWidth() : lp.width;
        return width + lp.leftMargin + lp.rightMargin;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        slots.put(child, new Slot(child));
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        slots.remove(child);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        visibleSlots.clear();
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            final Slot slot = slots.get(view);
            if (slot != null && isSlotVisible(view)) {
                visibleSlots.add(slot);
            }
        }
        listAnimator.reset(visibleSlots, !skipNextAnimation && isLaidOut());
        skipNextAnimation = false;
        applySlots();
        // a hidden item left without an entry (hidden while the menu was not laid out) must not stay visible
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            final Slot slot = slots.get(view);
            if (slot != null && !slot.inAnimator && view.getVisibility() != GONE && !isSlotVisible(view)) {
                hideSlotView(view);
            }
        }
        parentBar.invalidate();
    }

    private void onSlotsChanged() {
        applySlots();
        parentBar.invalidate();
    }

    private void applySlots() {
        for (Slot slot : slots.values()) {
            slot.inAnimator = false;
        }
        final float right = getWidth() - getPaddingRight();
        final float totalWidth = listAnimator.getMetadata().getTotalWidth();
        // a pill narrower than a button shrinks as a circle (see SimpleActionBar#computeMenuPill), the buttons with it
        final float pillScale = Math.min(1f, getPillWidth() / dp(46));
        for (ListAnimator.Entry<Slot> entry : listAnimator) {
            final Slot slot = entry.item;
            final View view = slot.view;
            final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) view.getLayoutParams();
            final RectF rect = entry.getRectF();
            slot.inAnimator = true;
            // the last buttons do not slide out with the shrinking width, they stay in the circle
            view.setTranslationX(right - Math.max(totalWidth, rect.right) + rect.left + lp.leftMargin - view.getLeft());
            slot.setVisibilityFactor(entry.getVisibility(), pillScale);
        }
    }

    private static class Slot implements ListAnimator.Measurable, Destroyable {
        final View view;
        boolean inAnimator;
        private float visibilityFactor = 1f;
        private float pillScale = 1f;
        private float baseAlpha = 1f;

        Slot(View view) {
            this.view = view;
        }

        /** Keeps the alpha set by the screen (a disabled button): untouched while fully shown, multiplied while fading. */
        void setVisibilityFactor(float factor, float pillScale) {
            if (visibilityFactor == factor && this.pillScale == pillScale) {
                return;
            }
            if (visibilityFactor == 1f) {
                baseAlpha = view.getAlpha();
            }
            visibilityFactor = factor;
            this.pillScale = pillScale;
            view.setAlpha(baseAlpha * factor);
            final float scale = Math.min(lerp(0.6f, 1f, factor), pillScale);
            view.setScaleX(scale);
            view.setScaleY(scale);
        }

        @Override
        public void performDestroy() {
            if (!isSlotVisible(view)) {
                hideSlotView(view);
                setVisibilityFactor(1f, 1f);
            }
        }

        @Override
        public int getWidth() {
            return getSlotWidth(view);
        }

        @Override
        public int getHeight() {
            return view.getMeasuredHeight();
        }

        @Override
        public int hashCode() {
            return view.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Slot && ((Slot) obj).view == view;
        }
    }
}
