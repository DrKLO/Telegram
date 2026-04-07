/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

public class DrawerLayoutContainer extends FrameLayout {

    private INavigationLayout parentActionBarLayout;

    private final Paint backgroundPaint = new Paint();

    private int behindKeyboardColor;

    private boolean inLayout;

    public boolean allowDrawContent = true;

    private boolean firstLayout = true;

    private boolean keyboardVisibility;
    private int imeHeight;

    /** @noinspection deprecation*/
    public DrawerLayoutContainer(Context context) {
        super(context);

        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setFocusableInTouchMode(true);

        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                boolean newKeyboardVisibility = insets.isVisible(WindowInsetsCompat.Type.ime());
                int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                if (keyboardVisibility != newKeyboardVisibility || this.imeHeight != imeHeight) {
                    keyboardVisibility = newKeyboardVisibility;
                    this.imeHeight = imeHeight;
                    requestLayout();
                }
            }
            final DrawerLayoutContainer drawerLayoutContainer = (DrawerLayoutContainer) v;
            if (AndroidUtilities.statusBarHeight != insets.getSystemWindowInsetTop()) {
                drawerLayoutContainer.requestLayout();
            }
            int newTopInset = insets.getSystemWindowInsetTop();
            if ((newTopInset != 0 || AndroidUtilities.isInMultiwindow || firstLayout) && AndroidUtilities.statusBarHeight != newTopInset) {
                AndroidUtilities.statusBarHeight = newTopInset;
            }
            firstLayout = false;
            drawerLayoutContainer.setWillNotDraw(insets.getSystemWindowInsetTop() <= 0 && getBackground() == null);

            invalidate();

            return onApplyWindowInsets(v, insets);
        });
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public void setParentActionBarLayout(INavigationLayout layout) {
        parentActionBarLayout = layout;
    }

    public void setAllowDrawContent(boolean value) {
        if (allowDrawContent != value) {
            allowDrawContent = value;
            invalidate();
        }
    }

    public boolean isDrawCurrentPreviewFragmentAbove() {
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return parentActionBarLayout.checkTransitionAnimation();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            try {
                child.layout(lp.leftMargin, lp.topMargin + getPaddingTop(), lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
            } catch (Exception e) {
                FileLog.e(e);
                if (BuildVars.DEBUG_VERSION) {
                    throw e;
                }
            }
        }
        inLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!inLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);
        final int newSize = heightSize
            - AndroidUtilities.statusBarHeight
            - AndroidUtilities.navigationBarHeight;

        if (newSize > 0 && newSize < 4096) {
            AndroidUtilities.displaySize.y = newSize;
        }

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
            final int contentHeightSpec;
            if (lp.height > 0) {
                contentHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            } else {
                contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
            }
            if (child instanceof ActionBarLayout) {
                ActionBarLayout actionBarLayout = (ActionBarLayout) child;
                //fix keyboard measuring
                if (actionBarLayout.storyViewerAttached()) {
                    child.forceLayout();
                }
            }
            child.measure(contentWidthSpec, contentHeightSpec);
        }
    }

    public void setBehindKeyboardColor(int color) {
        behindKeyboardColor = color;
        invalidate();
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (!allowDrawContent) {
            return false;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (lastWindowInsetsCompat == null) {
            return;
        }

        final Insets insets = lastWindowInsetsCompat.getInsets(WindowInsetsCompat.Type.ime()
            | WindowInsetsCompat.Type.systemBars());

        if (insets.bottom > 0) {
            backgroundPaint.setColor(behindKeyboardColor);
            canvas.drawRect(
                0,
                getMeasuredHeight() - insets.bottom,
                getMeasuredWidth(),
                getMeasuredHeight(),
                internalNavbarPaint
            );
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private final Paint internalNavbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Paint getInternalNavbarPaint() {
        return internalNavbarPaint;
    }

    public void setInternalNavigationBarColor(int color) {
        if (internalNavbarPaint.getColor() != color) {
            internalNavbarPaint.setColor(color);
            invalidate();

            for (int a = 0, N = getChildCount(); a < N; a++) {
                getChildAt(a).invalidate();
            }
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (lastWindowInsetsCompat != null) {
            dispatchApplyWindowInsetsInternal(child, lastWindowInsetsCompat);
        }
    }

    private @Nullable WindowInsetsCompat lastWindowInsetsCompat;

    private void dispatchApplyWindowInsetsInternal(View child, WindowInsetsCompat insets) {
        boolean canApplyInsets = child instanceof ActionBarLayout || child.getTag() == null;
        if (!canApplyInsets) {
            return;
        }

        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final Insets systemInsetsWithIme = insets.getInsets(WindowInsetsCompat.Type.ime()
                | WindowInsetsCompat.Type.systemBars());

        final boolean changed = lp.topMargin != 0 || lp.bottomMargin != 0
                || lp.leftMargin != systemInsetsWithIme.left
                || lp.rightMargin != systemInsetsWithIme.right;

        if (changed) {
            lp.leftMargin = systemInsetsWithIme.left;
            lp.topMargin = 0;
            lp.rightMargin = systemInsetsWithIme.right;
            lp.bottomMargin = 0;

            child.requestLayout();
        }

        final WindowInsetsCompat consumed = insets.inset(
                lp.leftMargin, lp.topMargin,
                lp.rightMargin, lp.bottomMargin);

        ViewCompat.dispatchApplyWindowInsets(child, consumed);
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        lastWindowInsetsCompat = insets;

        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            dispatchApplyWindowInsetsInternal(child, insets);
        }

        invalidate();
        return WindowInsetsCompat.CONSUMED;
    }
}
