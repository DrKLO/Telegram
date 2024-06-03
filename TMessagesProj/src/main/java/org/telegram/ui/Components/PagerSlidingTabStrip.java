/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class PagerSlidingTabStrip extends HorizontalScrollView {

    public interface IconTabProvider {
        Drawable getPageIconDrawable(int position);
        void customOnDraw(Canvas canvas, View view, int position);
        boolean canScrollToTab(int position);
        int getTabPadding(int position);
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

    private int indicatorColor = 0xff666666;
    private int underlineColor = 0x1a000000;

    private boolean shouldExpand = false;

    private int scrollOffset = AndroidUtilities.dp(52);
    private int indicatorHeight = AndroidUtilities.dp(8);
    private int underlineHeight = AndroidUtilities.dp(2);
    private int dividerPadding = AndroidUtilities.dp(12);
    private int tabPadding = AndroidUtilities.dp(24);

    private Theme.ResourcesProvider resourcesProvider;
    private int lastScrollX = 0;

    public PagerSlidingTabStrip(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

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
                Drawable drawable = ((IconTabProvider) pager.getAdapter()).getPageIconDrawable(i);
                if (drawable != null) {
                    addIconTab(i, drawable, pager.getAdapter().getPageTitle(i));
                } else {
                    addTab(i, pager.getAdapter().getPageTitle(i));
                }
            } else {
                addTab(i, pager.getAdapter().getPageTitle(i));
            }
        }
        updateTabStyles();
        getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                currentPosition = pager.getCurrentItem();
                scrollToChild(currentPosition, 0);
            }
        });
    }

    public View getTab(int position) {
        if (position < 0 || position >= tabsContainer.getChildCount()) {
            return null;
        }
        return tabsContainer.getChildAt(position);
    }

    private void addIconTab(final int position, Drawable drawable, CharSequence contentDescription) {
        ImageView tab = new ImageView(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (pager.getAdapter() instanceof IconTabProvider) {
                    ((IconTabProvider) pager.getAdapter()).customOnDraw(canvas, this, position);
                }
            }

            @Override
            public void setSelected(boolean selected) {
                super.setSelected(selected);
                Drawable background = getBackground();
                if (Build.VERSION.SDK_INT >= 21 && background != null) {
                    int color = getThemedColor(selected ? Theme.key_chat_emojiPanelIconSelected : Theme.key_chat_emojiBottomPanelIcon);
                    Theme.setSelectorDrawableColor(background, Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), true);
                }
            }
        };
        tab.setFocusable(true);
        if (Build.VERSION.SDK_INT >= 21) {
            RippleDrawable rippleDrawable = (RippleDrawable) Theme.createSelectorDrawable(getThemedColor(Theme.key_chat_emojiBottomPanelIcon), Theme.RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(18));
            Theme.setRippleDrawableForceSoftware(rippleDrawable);
            tab.setBackground(rippleDrawable);
        }
        tab.setImageDrawable(drawable);
        tab.setScaleType(ImageView.ScaleType.CENTER);
        tab.setOnClickListener(v -> {
            if (pager.getAdapter() instanceof IconTabProvider) {
                if (!((IconTabProvider) pager.getAdapter()).canScrollToTab(position)) {
                    return;
                }
            }
            pager.setCurrentItem(position, false);
        });
        tabsContainer.addView(tab);
        tab.setSelected(position == currentPosition);
        tab.setContentDescription(contentDescription);
    }

    private void addTab(final int position, CharSequence text) {
        TextTab tab = new TextTab(getContext(), position);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tab.setTypeface(AndroidUtilities.bold());
        tab.setTextColor(getThemedColor(Theme.key_chat_emojiPanelBackspace));
        tab.setFocusable(true);
        tab.setGravity(Gravity.CENTER);
//        if (Build.VERSION.SDK_INT >= 21) {
//            RippleDrawable rippleDrawable = (RippleDrawable) Theme.createSelectorDrawable(getThemedColor(Theme.key_chat_emojiBottomPanelIcon), Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE);
//            Theme.setRippleDrawableForceSoftware(rippleDrawable);
//            tab.setBackground(rippleDrawable);
//        }
        tab.setText(text);
        tab.setOnClickListener(v -> {
            if (pager.getAdapter() instanceof IconTabProvider) {
                if (!((IconTabProvider) pager.getAdapter()).canScrollToTab(position)) {
                    return;
                }
            }
            pager.setCurrentItem(position, false);
        });
        tab.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        tabsContainer.addView(tab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 10, 0, 10, 0));
        tab.setSelected(position == currentPosition);
    }


    private void updateTabStyles() {
        for (int i = 0; i < tabCount; i++) {
            View v = tabsContainer.getChildAt(i);
            v.setLayoutParams(defaultTabLayoutParams);
            if (shouldExpand) {
                v.setPadding(0, 0, 0, 0);
                v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1.0F));
            } else if (pager.getAdapter() instanceof IconTabProvider) {
                int padding = ((IconTabProvider) pager.getAdapter()).getTabPadding(i);
                v.setPadding(padding, 0, padding, 0);
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
        View child = tabsContainer.getChildAt(position);
        if (child == null) {
            return;
        }
        int newScrollX = child.getLeft() + offset;
        if (position > 0 || offset > 0) {
            newScrollX -= scrollOffset;
        }
        if (newScrollX != lastScrollX) {
            lastScrollX = newScrollX;
            scrollTo(newScrollX, 0);
        }
    }

    private AnimatedFloat lineLeftAnimated = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private AnimatedFloat lineRightAnimated = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void onDraw(Canvas canvas) {

        if (isInEditMode() || tabCount == 0) {
            super.onDraw(canvas);
            return;
        }

        final int height = getHeight();

        if (underlineHeight != 0) {
            rectPaint.setColor(underlineColor);
            AndroidUtilities.rectTmp.set(0, height - underlineHeight, tabsContainer.getWidth(), height);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, underlineHeight / 2f, underlineHeight / 2f, rectPaint);
        }

        View currentTab = tabsContainer.getChildAt(currentPosition);
        if (currentTab != null) {
            float lineLeft = currentTab.getLeft() + currentTab.getPaddingLeft();
            float lineRight = currentTab.getRight() - currentTab.getPaddingRight();

            if (currentPositionOffset > 0f && currentPosition < tabCount - 1) {
                View nextTab = tabsContainer.getChildAt(currentPosition + 1);
                final float nextTabLeft = nextTab.getLeft() + nextTab.getPaddingLeft();
                final float nextTabRight = nextTab.getRight() - nextTab.getPaddingRight();

                lineLeft = (currentPositionOffset * nextTabLeft + (1f - currentPositionOffset) * lineLeft);
                lineRight = (currentPositionOffset * nextTabRight + (1f - currentPositionOffset) * lineRight);

                lineLeftAnimated.set(lineLeft, true);
                lineRightAnimated.set(lineRight, true);

                if (currentTab instanceof TextTab) {
                    ((TextTab) currentTab).setSelectedProgress(1f - currentPositionOffset);
                }
                if (nextTab instanceof TextTab) {
                    ((TextTab) nextTab).setSelectedProgress(currentPositionOffset);
                }
            } else {
                lineLeft = lineLeftAnimated.set(lineLeft);
                lineRight = lineRightAnimated.set(lineRight);
            }

            if (indicatorHeight != 0) {
                rectPaint.setColor(indicatorColor);
                AndroidUtilities.rectTmp.set(lineLeft - AndroidUtilities.dp(12), AndroidUtilities.dp(6), lineRight  + AndroidUtilities.dp(12), height - AndroidUtilities.dp(6));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.rectTmp.height() / 2f, AndroidUtilities.rectTmp.height() / 2f, rectPaint);
            }
        }

        super.onDraw(canvas);
    }

    private class PageListener implements OnPageChangeListener {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            currentPosition = position;
            currentPositionOffset = positionOffset;
            View child = tabsContainer.getChildAt(position);
            if (child != null) {
                scrollToChild(position, (int) (positionOffset * tabsContainer.getChildAt(position).getWidth()));
                invalidate();
                if (delegatePageListener != null) {
                    delegatePageListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
                }
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

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void onSizeChanged(int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
        if (!shouldExpand) {
            post(PagerSlidingTabStrip.this::notifyDataSetChanged);
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

    private class TextTab extends TextView {

        final int position;
        public TextTab(Context context, int position) {
            super(context);
            this.position = position;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (pager.getAdapter() instanceof IconTabProvider) {
                ((IconTabProvider) pager.getAdapter()).customOnDraw(canvas, this, position);
            }
        }

        @Override
        public void setSelected(boolean selected) {
            super.setSelected(selected);
            Drawable background = getBackground();
            if (Build.VERSION.SDK_INT >= 21 && background != null) {
                int color = getThemedColor(selected ? Theme.key_chat_emojiPanelIconSelected : Theme.key_chat_emojiBottomPanelIcon);
                Theme.setSelectorDrawableColor(background, Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), true);
            }
            setTextColor(getThemedColor(selected ? Theme.key_chat_emojiPanelIconSelected : Theme.key_chat_emojiPanelBackspace));
        }

        public void setSelectedProgress(float progress) {
            int selectedColor = getThemedColor(Theme.key_chat_emojiPanelIconSelected);
            int color = getThemedColor(Theme.key_chat_emojiPanelBackspace);
            setTextColor(ColorUtils.blendARGB(color, selectedColor, progress));
        }
    };
}
