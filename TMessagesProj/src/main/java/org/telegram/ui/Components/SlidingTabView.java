/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;

public class SlidingTabView extends LinearLayout {

    public interface SlidingTabViewDelegate {
        void didSelectTab(int tab);
    }

    private SlidingTabViewDelegate delegate;
    private int selectedTab = 0;
    private int tabCount = 0;
    private float tabWidth = 0;
    private float tabX = 0;
    private float animateTabXTo = 0;
    private Paint paint = new Paint();
    private long startAnimationTime = 0;
    private long totalAnimationDiff = 0;
    private float startAnimationX = 0;
    private DecelerateInterpolator interpolator;

    private void init() {
        setOrientation(HORIZONTAL);
        setWeightSum(100);
        paint.setColor(0xffffffff);
        interpolator = new DecelerateInterpolator();
    }

    public SlidingTabView(Context context) {
        super(context);
        init();
    }

    public SlidingTabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SlidingTabView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public SlidingTabView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void addTextTab(final int position, String title) {
        TextView tab = new TextView(getContext());
        tab.setText(title);
        tab.setFocusable(true);
        tab.setGravity(Gravity.CENTER);
        tab.setSingleLine();
        tab.setTextColor(0xffffffff);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        tab.setBackgroundResource(R.drawable.bar_selector_picker);

        tab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                didSelectTab(position);
            }
        });
        addView(tab);
        LayoutParams layoutParams = (LayoutParams)tab.getLayoutParams();
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.width = 0;
        layoutParams.weight = 50;
        tab.setLayoutParams(layoutParams);

        tabCount++;
    }

    public void setDelegate(SlidingTabViewDelegate delegate) {
        this.delegate = delegate;
    }

    public int getSeletedTab() {
        return selectedTab;
    }

    private void didSelectTab(int tab) {
        if (selectedTab == tab) {
            return;
        }
        selectedTab = tab;
        animateToTab(tab);
        if (delegate != null) {
            delegate.didSelectTab(tab);
        }
    }

    private void animateToTab(int tab) {
        animateTabXTo = tab * tabWidth;
        startAnimationX = tabX;
        totalAnimationDiff = 0;
        startAnimationTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        tabWidth = (r - l) / (float)tabCount;
        animateTabXTo = tabX = tabWidth * selectedTab;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (tabX != animateTabXTo) {
            long dt = System.currentTimeMillis() - startAnimationTime;
            startAnimationTime = System.currentTimeMillis();
            totalAnimationDiff += dt;
            if (totalAnimationDiff > 200) {
                totalAnimationDiff = 200;
                tabX = animateTabXTo;
            } else {
                tabX = startAnimationX + interpolator.getInterpolation(totalAnimationDiff / 200.0f) * (animateTabXTo - startAnimationX);
                invalidate();
            }
        }

        canvas.drawRect(tabX, getHeight() - AndroidUtilities.dp(2), (tabX + tabWidth), getHeight(), paint);
    }
}
