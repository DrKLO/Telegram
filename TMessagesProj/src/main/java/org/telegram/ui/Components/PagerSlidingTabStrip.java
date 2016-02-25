/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class PagerSlidingTabStrip extends HorizontalScrollView {

    public interface IconTabProvider {
        int getPageIconResId(int position);
    }

    private LinearLayout.LayoutParams defaultTabLayoutParams;

    private final PageListener pageListener = new PageListener();
    public OnPageChangeListener delegatePageListener;

    private LinearLayout tabsContainer;
    private ViewPager pager;

    private int tabCount;

    private int currentPosition = 0;
    private float currentPositionOffset = 0f;

    private Paint rectPaint;

    private int indicatorColor = 0xFF666666;
    private int underlineColor = 0x1A000000;

    private boolean shouldExpand = false;

    private int scrollOffset = AndroidUtilities.dp(52);
    private int indicatorHeight = AndroidUtilities.dp(8);
    private int underlineHeight = AndroidUtilities.dp(2);
    private int dividerPadding = AndroidUtilities.dp(12);
    private int tabPadding = AndroidUtilities.dp(24);

    private int lastScrollX = 0;

    public PagerSlidingTabStrip(Context context) {
        super(context);

        setFillViewport(true);
        setWillNotDraw(false);

        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(tabsContainer);

        rectPaint = new Paint();
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Style.FILL);

        defaultTabLayoutParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutHelper.MATCH_PARENT);
    }

    public void setViewPager(ViewPager pager) {
        this.pager = pager;
        if (pager.getAdapter() == null) {
            throw new IllegalStateException("ViewPager does not have adapter instance.");
        }
        pager.setOnPageChangeListener(pageListener);
        notifyDataSetChanged();
    }

    public void setOnPageChangeListener(OnPageChangeListener listener) {
        this.delegatePageListener = listener;
    }

    public void notifyDataSetChanged() {
        tabsContainer.removeAllViews();
        tabCount = pager.getAdapter().getCount();
        for (int i = 0; i < tabCount; i++) {
            if (pager.getAdapter() instanceof IconTabProvider) {
                addIconTab(i, ((IconTabProvider) pager.getAdapter()).getPageIconResId(i));
            }
        }
        updateTabStyles();
        getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < 16) {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                currentPosition = pager.getCurrentItem();
                scrollToChild(currentPosition, 0);
            }
        });
    }

    private void addIconTab(final int position, int resId) {
        ImageView tab = new ImageView(getContext());
        tab.setFocusable(true);
        //tab.setImageResource(resId);
        tab.setImageDrawable(setImageButtonState(position));
        tab.setScaleType(ImageView.ScaleType.CENTER);
        tab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pager.setCurrentItem(position);
            }
        });
        tabsContainer.addView(tab);
        tab.setSelected(position == currentPosition);
    }
    //Plus
    private StateListDrawable setImageButtonState(int index)
    {
        Drawable nonactiveTab = getResources().getDrawable(icons[index]);
        Drawable filteredNonactiveTab = nonactiveTab.getConstantState().newDrawable();
        Drawable activeTab = getResources().getDrawable(icons_active[index]);
        Drawable filteredActiveTab = activeTab.getConstantState().newDrawable();

        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int tabColor = themePrefs.getInt("chatEmojiViewTabColor", AndroidUtilities.getIntDarkerColor("themeColor", -0x15));
        int iconColor = themePrefs.getInt("chatEmojiViewTabIconColor", 0xffa8a8a8);

        int focused = android.R.attr.state_focused;
        int selected = android.R.attr.state_selected;
        int pressed = android.R.attr.state_pressed;

        final FilterableStateListDrawable selectorDrawable = new FilterableStateListDrawable();

        selectorDrawable.addState(new int[] {-focused, -selected, -pressed}, filteredNonactiveTab, new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        selectorDrawable.addState(new int[] {-focused, -selected, -pressed}, nonactiveTab);
        selectorDrawable.addState(new int[] {-focused, selected, -pressed}, filteredActiveTab, new PorterDuffColorFilter(tabColor, PorterDuff.Mode.SRC_IN));
        selectorDrawable.addState(new int[] {-focused, selected, -pressed}, activeTab);
        selectorDrawable.addState(new int[] {pressed}, filteredActiveTab, new PorterDuffColorFilter(tabColor, PorterDuff.Mode.SRC_IN));
        selectorDrawable.addState(new int[] {pressed}, activeTab);
        selectorDrawable.addState(new int[]{focused, -selected, -pressed}, filteredNonactiveTab, new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        selectorDrawable.addState(new int[]{focused, -selected, -pressed}, nonactiveTab);
        selectorDrawable.addState(new int[]{focused, selected, -pressed}, filteredNonactiveTab, new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        selectorDrawable.addState(new int[]{focused, selected, -pressed}, nonactiveTab);

        return selectorDrawable;
    }

    public class FilterableStateListDrawable extends StateListDrawable {

        private int currIdx = -1;
        private int childrenCount = 0;
        private SparseArray<ColorFilter> filterMap;

        public FilterableStateListDrawable() {
            super();
            filterMap = new SparseArray<>();
        }

        @Override
        public void addState(int[] stateSet, Drawable drawable) {
            super.addState(stateSet, drawable);
            childrenCount++;
        }

        public void addState(int[] stateSet, Drawable drawable, ColorFilter colorFilter) {
            // this is a new custom method, does not exist in parent class
            int currChild = childrenCount;
            addState(stateSet, drawable);
            filterMap.put(currChild, colorFilter);
        }

        @Override
        public boolean selectDrawable(int idx) {
            if (currIdx != idx) {
                setColorFilter(getColorFilterForIdx(idx));
            }
            boolean result = super.selectDrawable(idx);
            // check if the drawable has been actually changed to the one I expect
            if (getCurrent() != null) {
                currIdx = result ? idx : currIdx;
                if (!result) {
                    // it has not been changed, meaning, back to previous filter
                    setColorFilter(getColorFilterForIdx(currIdx));
                }
            } else if (getCurrent() == null) {
                currIdx = -1;
                setColorFilter(null);
            }
            return result;
        }

        private ColorFilter getColorFilterForIdx(int idx) {
            return filterMap != null ? filterMap.get(idx) : null;
        }
    }

    private int[] icons = {
            R.drawable.ic_smiles_recent,
            R.drawable.ic_smiles_smile,
            R.drawable.ic_smiles_flower,
            R.drawable.ic_smiles_bell,
            R.drawable.ic_smiles_car,
            R.drawable.ic_smiles_grid,
            R.drawable.ic_smiles_sticker};

    private int[] icons_active = {
            R.drawable.ic_smiles_recent_active,
            R.drawable.ic_smiles_smile_active,
            R.drawable.ic_smiles_flower_active,
            R.drawable.ic_smiles_bell_active,
            R.drawable.ic_smiles_car_active,
            R.drawable.ic_smiles_grid_active,
            R.drawable.ic_smiles_sticker_active};
/*
    private void paintTabIcons(int i){
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int tabColor = themePrefs.getInt("chatEmojiViewTabColor", AndroidUtilities.getIntDarkerColor("themeColor", -0x15));
        Drawable icon_active = getResources().getDrawable(icons_active[i]);
        icon_active.setColorFilter(tabColor, PorterDuff.Mode.SRC_IN);
        //Drawable icon = getResources().getDrawable(icons[i]);
        //int iconColor = themePrefs.getInt("chatEmojiViewTabIconColor", 0xffa8a8a8);
        //icon.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
        //iv.setImageDrawable(icon);
    }*/

    private void updateTabStyles() {
        for (int i = 0; i < tabCount; i++) {
            View v = tabsContainer.getChildAt(i);
            v.setLayoutParams(defaultTabLayoutParams);
            if (shouldExpand) {
                v.setPadding(0, 0, 0, 0);
                v.setLayoutParams(new LinearLayout.LayoutParams(-1, -1, 1.0F));
            } else {
                v.setPadding(tabPadding, 0, tabPadding, 0);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!shouldExpand || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            return;
        }
        int myWidth = getMeasuredWidth();
        tabsContainer.measure(MeasureSpec.EXACTLY | myWidth, heightMeasureSpec);
    }

    private void scrollToChild(int position, int offset) {
        if (tabCount == 0) {
            return;
        }
        int newScrollX = tabsContainer.getChildAt(position).getLeft() + offset;
        if (position > 0 || offset > 0) {
            newScrollX -= scrollOffset;
        }
        if (newScrollX != lastScrollX) {
            lastScrollX = newScrollX;
            scrollTo(newScrollX, 0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode() || tabCount == 0) {
            return;
        }

        final int height = getHeight();

        // draw underline
        rectPaint.setColor(underlineColor);
        canvas.drawRect(0, height - underlineHeight, tabsContainer.getWidth(), height, rectPaint);

        // default: line below current tab
        View currentTab = tabsContainer.getChildAt(currentPosition);
        float lineLeft = currentTab.getLeft();
        float lineRight = currentTab.getRight();

        // if there is an offset, start interpolating left and right coordinates between current and next tab
        if (currentPositionOffset > 0f && currentPosition < tabCount - 1) {

            View nextTab = tabsContainer.getChildAt(currentPosition + 1);
            final float nextTabLeft = nextTab.getLeft();
            final float nextTabRight = nextTab.getRight();

            lineLeft = (currentPositionOffset * nextTabLeft + (1f - currentPositionOffset) * lineLeft);
            lineRight = (currentPositionOffset * nextTabRight + (1f - currentPositionOffset) * lineRight);
        }

        // draw indicator line
        rectPaint.setColor(indicatorColor);
        canvas.drawRect(lineLeft, height - indicatorHeight, lineRight, height, rectPaint);
    }

    private class PageListener implements OnPageChangeListener {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            currentPosition = position;
            currentPositionOffset = positionOffset;
            scrollToChild(position, (int) (positionOffset * tabsContainer.getChildAt(position).getWidth()));
            invalidate();
            if (delegatePageListener != null) {
                delegatePageListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (state == ViewPager.SCROLL_STATE_IDLE) {
                scrollToChild(pager.getCurrentItem(), 0);
            }
            if (delegatePageListener != null) {
                delegatePageListener.onPageScrollStateChanged(state);
            }
        }

        @Override
        public void onPageSelected(int position) {
            if (delegatePageListener != null) {
                delegatePageListener.onPageSelected(position);
            }
            for (int a = 0; a < tabsContainer.getChildCount(); a++) {
                tabsContainer.getChildAt(a).setSelected(a == position);
            }
        }
    }

    public void onSizeChanged(int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
        if (!shouldExpand) {
            post(new Runnable() {
                public void run() {
                    PagerSlidingTabStrip.this.notifyDataSetChanged();
                }
            });
        }
    }

    public void setIndicatorColor(int indicatorColor) {
        this.indicatorColor = indicatorColor;
        invalidate();
    }

    public void setIndicatorColorResource(int resId) {
        this.indicatorColor = getResources().getColor(resId);
        invalidate();
    }

    public int getIndicatorColor() {
        return this.indicatorColor;
    }

    public void setIndicatorHeight(int indicatorLineHeightPx) {
        this.indicatorHeight = indicatorLineHeightPx;
        invalidate();
    }

    public int getIndicatorHeight() {
        return indicatorHeight;
    }

    public void setUnderlineColor(int underlineColor) {
        this.underlineColor = underlineColor;
        invalidate();
    }

    public void setUnderlineColorResource(int resId) {
        this.underlineColor = getResources().getColor(resId);
        invalidate();
    }

    public int getUnderlineColor() {
        return underlineColor;
    }

    public void setUnderlineHeight(int underlineHeightPx) {
        this.underlineHeight = underlineHeightPx;
        invalidate();
    }

    public int getUnderlineHeight() {
        return underlineHeight;
    }

    public void setDividerPadding(int dividerPaddingPx) {
        this.dividerPadding = dividerPaddingPx;
        invalidate();
    }

    public int getDividerPadding() {
        return dividerPadding;
    }

    public void setScrollOffset(int scrollOffsetPx) {
        this.scrollOffset = scrollOffsetPx;
        invalidate();
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setShouldExpand(boolean shouldExpand) {
        this.shouldExpand = shouldExpand;
        tabsContainer.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        updateTabStyles();
        requestLayout();
    }

    public boolean getShouldExpand() {
        return shouldExpand;
    }

    public void setTabPaddingLeftRight(int paddingPx) {
        this.tabPadding = paddingPx;
        updateTabStyles();
    }

    public int getTabPaddingLeftRight() {
        return tabPadding;
    }
}
