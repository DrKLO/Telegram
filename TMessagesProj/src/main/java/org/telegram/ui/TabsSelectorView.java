package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.ui.Components.glass.GlassTabView;

import me.vkryl.android.animator.FactorAnimator;

public class TabsSelectorView extends ViewGroup implements FactorAnimator.Target {
    public GlassTabView[] tabs;

    public TabsSelectorView(Context context) {
        super(context);
        updateColors();
    }

    public void updateColors() {
    }

    private int selectedTab = -1;

    public void selectTab(int tab, boolean animated, boolean updatePos) {
        if (selectedTab != tab) {
            if (selectedTab >= 0 && selectedTab < tabs.length) {
                tabs[selectedTab].setSelected(false, animated);
            }
            if (tab >= 0 && tab < tabs.length) {
                tabs[tab].setSelected(true, animated);
            }
            selectedTab = tab;
        }
    }

    public void setGestureSelectedOverride(float value, boolean allow) {
        for (int a = 0; a < tabs.length; a++) {
            final float visibility = Math.max(0, 1f - Math.abs(a - value));
            tabs[a].setGestureSelectedOverride(visibility, allow);
        }
        invalidate();
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
    }







    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        measureTabTexts();

        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        final int minTotalWidthForTabs = Math.min(dp(320), maxTotalWidthForTabs);
        final int tabPadding = dp(16);

        final int minTabTextWidthIfEq = (minTotalWidthForTabs / visibleChildCount) - tabPadding * 2;
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / visibleChildCount) - tabPadding * 2;


        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }

            final float w = tabsTextWidth[a];
            if (w > maxTabTextWidthIfEq) {
                tabsTextWidthWithMargin[a] = tabsTextWidth[a] + dp(13) * 2;
            } else {
                tabsTextWidthWithMargin[a] = tabsTextWidth[a] + dp(16) * 2;
            }
            tabsWeight[a] = tabsTextWidthWithMargin[a] > (maxTabTextWidthIfEq + dp(16) * 2) ? 0 : 1;

            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (totalWeight == 0) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsWeight[a] = getChildAt(a).getVisibility() != View.GONE ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs) {
            final float shrinkW = totalWeight - maxTotalWidthForTabs;
            final float m = shrinkW / totalWidth;

            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] *= m;
            }
        } else if (totalWidth < minTotalWidthForTabs) {
            final float growW = minTotalWidthForTabs - totalWidth;
            final float growP = growW / totalWeight;

            //boolean needStage2 = false;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final float maxGrow = maxTabTextWidthIfEq - tabsTextWidthWithMargin[a];
                //if (tabsWeight[a] > 0 && growP * tabsWeight[a] > maxGrow) {
                //    needStage2 = true;
                //    tabsTextWidthWithMargin[a] = maxTabTextWidthIfEq;
                //} else {
                    tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
                //}
            }

            /*if (needStage2) {
                totalWidth = 0;
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    totalWidth += tabsTextWidthWithMargin[a];
                }

                final float m = minTotalWidthForTabs / totalWidth;
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    tabsTextWidthWithMargin[a] *= m;
                }
            }*/
        }

        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            tabsLeftPos[a] = l;
            l += tabsWidth[a];
        }

        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }





/*
        if (biggestTabTextWidth > maxTabTextWidthIfEq) {
            setMeasuredDimension(0, 0);

        } else {
            // all tabs have equal width

            final int tabWidth = (Math.max(biggestTabTextWidth, minTabTextWidthIfEq) + tabPadding * 2);
            final int measuredWidth = tabWidth * visibleChildCount + getPaddingLeft() + getPaddingRight();
            setMeasuredDimension(measuredWidth, height);

            int index = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);

                child.measure(
                    MeasureSpec.makeMeasureSpec(tabWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));

                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }

                tabsLeftPos[a] = (tabWidth * index);
                index++;
            }
        }
 */
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            final int top = getPaddingTop();
            final int left = getPaddingLeft() + tabsLeftPos[a];
            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());
        }
    }



    public interface Tab {
        float measureTextWidth();
    }



    // fills tabsTextWidth[] and return visible child count;

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;

    private int[] tabsLeftPos;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts() {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() == View.GONE) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof TabsSelectorView.Tab) {
                tabWidth = ((TabsSelectorView.Tab) child).measureTextWidth();
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }
}
