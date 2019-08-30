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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import androidx.annotation.Keep;
import android.text.Layout;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class ScrollSlidingTextTabStrip extends HorizontalScrollView {

    public interface ScrollSlidingTabStripDelegate {
        void onPageSelected(int page, boolean forward);
        void onPageScrolled(float progress);
    }

    private LinearLayout tabsContainer;
    private ScrollSlidingTabStripDelegate delegate;

    private boolean useSameWidth;

    private int tabCount;
    private int currentPosition;
    private int selectedTabId = -1;
    private int allTextWidth;

    private int indicatorX;
    private int indicatorWidth;

    private int prevLayoutWidth;

    private int animateIndicatorStartX;
    private int animateIndicatorStartWidth;
    private int animateIndicatorToX;
    private int animateIndicatorToWidth;
    private boolean animatingIndicator;
    private float animationIdicatorProgress;

    private GradientDrawable selectorDrawable;

    private String tabLineColorKey = Theme.key_actionBarTabLine;
    private String activeTextColorKey = Theme.key_actionBarTabActiveText;
    private String unactiveTextColorKey = Theme.key_actionBarTabUnactiveText;
    private String selectorColorKey = Theme.key_actionBarTabSelector;

    private CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT_QUINT;

    private SparseIntArray positionToId = new SparseIntArray(5);
    private SparseIntArray idToPosition = new SparseIntArray(5);
    private SparseIntArray positionToWidth = new SparseIntArray(5);

    private boolean animationRunning;
    private long lastAnimationTime;
    private float animationTime;
    private int previousPosition;
    private Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!animatingIndicator) {
                return;
            }
            long newTime = SystemClock.elapsedRealtime();
            long dt = (newTime - lastAnimationTime);
            if (dt > 17) {
                dt = 17;
            }
            animationTime += dt / 200.0f;
            setAnimationIdicatorProgress(interpolator.getInterpolation(animationTime));
            if (animationTime > 1.0f) {
                animationTime = 1.0f;
            }
            if (animationTime < 1.0f) {
                AndroidUtilities.runOnUIThread(animationRunnable);
            } else {
                animatingIndicator = false;
                setEnabled(true);
                if (delegate != null) {
                    delegate.onPageScrolled(1.0f);
                }
            }
        }
    };

    public ScrollSlidingTextTabStrip(Context context) {
        super(context);

        selectorDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null);
        float rad = AndroidUtilities.dpf2(3);
        selectorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        selectorDrawable.setColor(Theme.getColor(tabLineColorKey));

        setFillViewport(true);
        setWillNotDraw(false);

        setHorizontalScrollBarEnabled(false);
        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(tabsContainer);
    }

    public void setDelegate(ScrollSlidingTabStripDelegate scrollSlidingTabStripDelegate) {
        delegate = scrollSlidingTabStripDelegate;
    }

    public boolean isAnimatingIndicator() {
        return animatingIndicator;
    }

    private void setAnimationProgressInernal(TextView newTab, TextView prevTab, float value) {
        int newColor = Theme.getColor(activeTextColorKey);
        int prevColor = Theme.getColor(unactiveTextColorKey);

        int r1 = Color.red(newColor);
        int g1 = Color.green(newColor);
        int b1 = Color.blue(newColor);
        int a1 = Color.alpha(newColor);
        int r2 = Color.red(prevColor);
        int g2 = Color.green(prevColor);
        int b2 = Color.blue(prevColor);
        int a2 = Color.alpha(prevColor);

        prevTab.setTextColor(Color.argb((int) (a1 + (a2 - a1) * value), (int) (r1 + (r2 - r1) * value), (int) (g1 + (g2 - g1) * value), (int) (b1 + (b2 - b1) * value)));
        newTab.setTextColor(Color.argb((int) (a2 + (a1 - a2) * value), (int) (r2 + (r1 - r2) * value), (int) (g2 + (g1 - g2) * value), (int) (b2 + (b1 - b2) * value)));

        indicatorX = (int) (animateIndicatorStartX + (animateIndicatorToX - animateIndicatorStartX) * value);
        indicatorWidth = (int) (animateIndicatorStartWidth + (animateIndicatorToWidth - animateIndicatorStartWidth) * value);
        invalidate();
    }

    @Keep
    public void setAnimationIdicatorProgress(float value) {
        animationIdicatorProgress = value;

        TextView newTab = (TextView) tabsContainer.getChildAt(currentPosition);
        TextView prevTab = (TextView) tabsContainer.getChildAt(previousPosition);
        setAnimationProgressInernal(newTab, prevTab, value);

        if (delegate != null) {
            delegate.onPageScrolled(value);
        }
    }

    public void setUseSameWidth(boolean value) {
        useSameWidth = value;
    }

    public Drawable getSelectorDrawable() {
        return selectorDrawable;
    }

    public View getTabsContainer() {
        return tabsContainer;
    }

    public float getAnimationIdicatorProgress() {
        return animationIdicatorProgress;
    }

    public int getNextPageId(boolean forward) {
        return positionToId.get(currentPosition + (forward ? 1 : -1), -1);
    }

    public void removeTabs() {
        positionToId.clear();
        idToPosition.clear();
        positionToWidth.clear();
        tabsContainer.removeAllViews();
        allTextWidth = 0;
        tabCount = 0;
    }

    public int getTabsCount() {
        return tabCount;
    }

    public boolean hasTab(int id) {
        return idToPosition.get(id, -1) != -1;
    }

    public void addTextTab(final int id, CharSequence text) {
        int position = tabCount++;
        if (position == 0 && selectedTabId == -1) {
            selectedTabId = id;
        }
        positionToId.put(position, id);
        idToPosition.put(id, position);
        if (selectedTabId != -1 && selectedTabId == id) {
            currentPosition = position;
            prevLayoutWidth = 0;
        }
        TextView tab = new TextView(getContext());
        tab.setGravity(Gravity.CENTER);
        tab.setText(text);
        tab.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(selectorColorKey), 3));
        tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tab.setSingleLine(true);
        tab.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tab.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        tab.setOnClickListener(v -> {
            int position1 = tabsContainer.indexOfChild(v);
            if (position1 < 0 || position1 == currentPosition) {
                return;
            }
            boolean scrollingForward = currentPosition < position1;
            previousPosition = currentPosition;
            currentPosition = position1;
            selectedTabId = id;

            if (animatingIndicator) {
                AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                animatingIndicator = false;
            }

            animationTime = 0;
            animatingIndicator = true;
            animateIndicatorStartX = indicatorX;
            animateIndicatorStartWidth = indicatorWidth;

            TextView nextChild = (TextView) v;
            animateIndicatorToWidth = getChildWidth(nextChild);
            animateIndicatorToX = nextChild.getLeft() + (nextChild.getMeasuredWidth() - animateIndicatorToWidth) / 2;
            setEnabled(false);

            AndroidUtilities.runOnUIThread(animationRunnable, 16);

            if (delegate != null) {
                delegate.onPageSelected(id, scrollingForward);
            }
            scrollToChild(position1);
        });
        int tabWidth = (int) Math.ceil(tab.getPaint().measureText(text, 0, text.length())) + AndroidUtilities.dp(16);
        allTextWidth += tabWidth;
        positionToWidth.put(position, tabWidth);
        tabsContainer.addView(tab, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT));
    }

    public void finishAddingTabs() {
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            TextView tab = (TextView) tabsContainer.getChildAt(a);
            tab.setTag(currentPosition == a ? activeTextColorKey : unactiveTextColorKey);
            tab.setTextColor(Theme.getColor(currentPosition == a ? activeTextColorKey : unactiveTextColorKey));
        }
    }

    public void setColors(String line, String active, String unactive, String selector) {
        tabLineColorKey = line;
        activeTextColorKey = active;
        unactiveTextColorKey = unactive;
        selectorColorKey = selector;
        selectorDrawable.setColor(Theme.getColor(tabLineColorKey));
    }

    public int getCurrentTabId() {
        return selectedTabId;
    }

    public void setInitialTabId(int id) {
        selectedTabId = id;
    }

    public int getFirstTabId() {
        return positionToId.get(0, 0);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == tabsContainer) {
            final int height = getMeasuredHeight();
            selectorDrawable.setBounds(indicatorX, height - AndroidUtilities.dpr(4), indicatorX + indicatorWidth, height);
            selectorDrawable.draw(canvas);
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = tabsContainer.getChildAt(a);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) child.getLayoutParams();
            if (allTextWidth > width) {
                layoutParams.weight = 0;
                layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            } else if (useSameWidth) {
                layoutParams.weight = 1.0f / count;
                layoutParams.width = 0;
            } else {
                layoutParams.weight = 1.0f / allTextWidth * positionToWidth.get(a);
                layoutParams.width = 0;
            }
        }
        if (allTextWidth > width) {
            tabsContainer.setWeightSum(0.0f);
        } else {
            tabsContainer.setWeightSum(1.0f);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void scrollToChild(int position) {
        if (tabCount == 0) {
            return;
        }
        TextView child = (TextView) tabsContainer.getChildAt(position);
        if (child == null) {
            return;
        }
        int currentScrollX = getScrollX();
        int left = child.getLeft();
        int width = child.getMeasuredWidth();
        if (left < currentScrollX) {
            smoothScrollTo(left, 0);
        } else if (left + width > currentScrollX + getWidth()) {
            smoothScrollTo(left + width, 0);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (prevLayoutWidth != r - l) {
            prevLayoutWidth = r - l;
            if (animatingIndicator) {
                AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                animatingIndicator = false;
                setEnabled(true);
                if (delegate != null) {
                    delegate.onPageScrolled(1.0f);
                }
            }
            TextView child = (TextView) tabsContainer.getChildAt(currentPosition);
            if (child != null) {
                indicatorWidth = getChildWidth(child);
                indicatorX = child.getLeft() + (child.getMeasuredWidth() - indicatorWidth) / 2;
            }
        }
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = tabsContainer.getChildAt(a);
            child.setEnabled(enabled);
        }
    }

    public void selectTabWithId(int id, float progress) {
        int position = idToPosition.get(id, -1);
        if (position < 0) {
            return;
        }
        if (progress < 0) {
            progress = 0;
        } else if (progress > 1.0f) {
            progress = 1.0f;
        }
        TextView child = (TextView) tabsContainer.getChildAt(currentPosition);
        TextView nextChild = (TextView) tabsContainer.getChildAt(position);
        if (child != null && nextChild != null) {
            animateIndicatorStartWidth = getChildWidth(child);
            animateIndicatorStartX = child.getLeft() + (child.getMeasuredWidth() - animateIndicatorStartWidth) / 2;
            animateIndicatorToWidth = getChildWidth(nextChild);
            animateIndicatorToX = nextChild.getLeft() + (nextChild.getMeasuredWidth() - animateIndicatorToWidth) / 2;
            setAnimationProgressInernal(nextChild, child, progress);
        }
        if (progress >= 1.0f) {
            currentPosition = position;
            selectedTabId = id;
        }
    }

    private int getChildWidth(TextView child) {
        Layout layout = child.getLayout();
        if (layout != null) {
            return (int) Math.ceil(layout.getLineWidth(0)) + AndroidUtilities.dp(2);
        } else {
            return child.getMeasuredWidth();
        }
    }

    public void onPageScrolled(int position, int first) {
        if (currentPosition == position) {
            return;
        }
        currentPosition = position;
        if (position >= tabsContainer.getChildCount()) {
            return;
        }
        for (int a = 0; a < tabsContainer.getChildCount(); a++) {
            tabsContainer.getChildAt(a).setSelected(a == position);
        }
        if (first == position && position > 1) {
            scrollToChild(position - 1);
        } else {
            scrollToChild(position);
        }
        invalidate();
    }
}
