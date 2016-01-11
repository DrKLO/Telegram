/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;

public class ScrollSlidingTabStrip extends HorizontalScrollView {

    public interface ScrollSlidingTabStripDelegate {
        void onPageSelected(int page);
    }

    private LinearLayout.LayoutParams defaultTabLayoutParams;
    private LinearLayout tabsContainer;
    private ScrollSlidingTabStripDelegate delegate;

    private int tabCount;

    private int currentPosition = 0;

    private Paint rectPaint;

    private int indicatorColor = 0xff666666;
    private int underlineColor = 0x1a000000;

    private int scrollOffset = AndroidUtilities.dp(52);
    private int underlineHeight = AndroidUtilities.dp(2);
    private int dividerPadding = AndroidUtilities.dp(12);
    private int tabPadding = AndroidUtilities.dp(24);

    private int lastScrollX = 0;

    public ScrollSlidingTabStrip(Context context) {
        super(context);

        setFillViewport(true);
        setWillNotDraw(false);

        setHorizontalScrollBarEnabled(false);
        tabsContainer = new LinearLayout(context);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(tabsContainer);

        rectPaint = new Paint();
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Style.FILL);

        defaultTabLayoutParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(52), LayoutHelper.MATCH_PARENT);
    }

    public void setDelegate(ScrollSlidingTabStripDelegate scrollSlidingTabStripDelegate) {
        delegate = scrollSlidingTabStripDelegate;
    }

    public void removeTabs() {
        tabsContainer.removeAllViews();
        tabCount = 0;
        currentPosition = 0;
    }

    public void selectTab(int num) {
        if (num < 0 || num >= tabCount) {
            return;
        }
        View tab = tabsContainer.getChildAt(num);
        if (Build.VERSION.SDK_INT >= 15) {
            tab.callOnClick();
        } else {
            tab.performClick();
        }
    }

    public void addIconTab(int resId) {
        final int position = tabCount++;
        ImageView tab = new ImageView(getContext());
        tab.setFocusable(true);
        tab.setImageResource(resId);
        tab.setScaleType(ImageView.ScaleType.CENTER);
        tab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                delegate.onPageSelected(position);
            }
        });
        tabsContainer.addView(tab);
        tab.setSelected(position == currentPosition);
    }

    public void addStickerTab(TLRPC.Document sticker) {
        final int position = tabCount++;
        FrameLayout tab = new FrameLayout(getContext());
        tab.setFocusable(true);
        tab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                delegate.onPageSelected(position);
            }
        });
        tabsContainer.addView(tab);
        tab.setSelected(position == currentPosition);
        BackupImageView imageView = new BackupImageView(getContext());
        if (sticker != null && sticker.thumb != null) {
            imageView.setImage(sticker.thumb.location, null, "webp", null);
        }
        imageView.setAspectFit(true);
        tab.addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));
    }

    public void updateTabStyles() {
        for (int i = 0; i < tabCount; i++) {
            View v = tabsContainer.getChildAt(i);
            v.setLayoutParams(defaultTabLayoutParams);
        }
    }

    private void scrollToChild(int position) {
        if (tabCount == 0 || tabsContainer.getChildAt(position) == null) {
            return;
        }
        int newScrollX = tabsContainer.getChildAt(position).getLeft();
        if (position > 0) {
            newScrollX -= scrollOffset;
        }
        int currentScrollX = getScrollX();
        if (newScrollX != lastScrollX) {
            if (newScrollX < currentScrollX) {
                lastScrollX = newScrollX;
                smoothScrollTo(lastScrollX, 0);
            } else if (newScrollX + scrollOffset > currentScrollX + getWidth() - scrollOffset * 2) {
                lastScrollX = newScrollX - getWidth() + scrollOffset * 3;
                smoothScrollTo(lastScrollX, 0);
            }
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (isInEditMode() || tabCount == 0) {
            return;
        }

        final int height = getHeight();

        rectPaint.setColor(underlineColor);
        canvas.drawRect(0, height - underlineHeight, tabsContainer.getWidth(), height, rectPaint);

        View currentTab = tabsContainer.getChildAt(currentPosition);
        float lineLeft = 0;
        float lineRight = 0;
        if (currentTab != null) {
            lineLeft = currentTab.getLeft();
            lineRight = currentTab.getRight();
        }

        rectPaint.setColor(indicatorColor);
        canvas.drawRect(lineLeft, 0, lineRight, height, rectPaint);
    }

    public int getCurrentPosition() {
        return currentPosition;
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

    public void setIndicatorColor(int indicatorColor) {
        this.indicatorColor = indicatorColor;
        invalidate();
    }

    public void setUnderlineColor(int underlineColor) {
        this.underlineColor = underlineColor;
        invalidate();
    }

    public void setUnderlineColorResource(int resId) {
        this.underlineColor = getResources().getColor(resId);
        invalidate();
    }

    public void setUnderlineHeight(int underlineHeightPx) {
        this.underlineHeight = underlineHeightPx;
        invalidate();
    }
}
