/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.ActionBar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;

public class DrawerLayoutContainer extends FrameLayout {

    private static final int MIN_DRAWER_MARGIN = 64;

    private ViewGroup drawerLayout;
    private ActionBarLayout parentActionBarLayout;

    private boolean maybeStartTracking = false;
    private boolean startedTracking = false;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId;
    private VelocityTracker velocityTracker = null;
    private boolean beginTrackingSent;
    private AnimatorSetProxy currentAnimation = null;

    private Paint scrimPaint = new Paint();

    private Object lastInsets;
    private boolean inLayout;
    private int minDrawerMargin;
    private float scrimOpacity;
    private Drawable shadowLeft;
    private boolean allowOpenDrawer;

    private float drawerPosition = 0;
    private boolean drawerOpened = false;

    public DrawerLayoutContainer(Context context) {
        super(context);

        minDrawerMargin = (int) (MIN_DRAWER_MARGIN * AndroidUtilities.density + 0.5f);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setFocusableInTouchMode(true);

        if (Build.VERSION.SDK_INT >= 21) {
            setFitsSystemWindows(true);
            configureApplyInsets(this);
        }

        shadowLeft = getResources().getDrawable(R.drawable.menu_shadow);
    }

    private class InsetsListener implements View.OnApplyWindowInsetsListener {
        @Override
        public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
            final DrawerLayoutContainer drawerLayout = (DrawerLayoutContainer) v;
            drawerLayout.setChildInsets(insets, insets.getSystemWindowInsetTop() > 0);
            return insets.consumeSystemWindowInsets();
        }
    }

    private void configureApplyInsets(View drawerLayout) {
        if (Build.VERSION.SDK_INT >= 21) {
            drawerLayout.setOnApplyWindowInsetsListener(new InsetsListener());
            drawerLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    private void dispatchChildInsets(View child, Object insets, int drawerGravity) {
        WindowInsets wi = (WindowInsets) insets;
        if (drawerGravity == Gravity.LEFT) {
            wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(), 0, wi.getSystemWindowInsetBottom());
        } else if (drawerGravity == Gravity.RIGHT) {
            wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
        }
        child.dispatchApplyWindowInsets(wi);
    }

    private void applyMarginInsets(MarginLayoutParams lp, Object insets, int drawerGravity, boolean topOnly) {
        WindowInsets wi = (WindowInsets) insets;
        if (drawerGravity == Gravity.LEFT) {
            wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(), 0, wi.getSystemWindowInsetBottom());
        } else if (drawerGravity == Gravity.RIGHT) {
            wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
        }
        lp.leftMargin = wi.getSystemWindowInsetLeft();
        lp.topMargin = topOnly ? 0 : wi.getSystemWindowInsetTop();
        lp.rightMargin = wi.getSystemWindowInsetRight();
        lp.bottomMargin = wi.getSystemWindowInsetBottom();
    }

    private int getTopInset(Object insets) {
        if (Build.VERSION.SDK_INT >= 21) {
            return insets != null ? ((WindowInsets) insets).getSystemWindowInsetTop() : 0;
        }
        return 0;
    }

    private void setChildInsets(Object insets, boolean draw) {
        lastInsets = insets;
        setWillNotDraw(!draw && getBackground() == null);
        requestLayout();
    }

    public void setDrawerLayout(ViewGroup layout) {
        drawerLayout = layout;
        addView(drawerLayout);
        if (Build.VERSION.SDK_INT >= 21) {
            drawerLayout.setFitsSystemWindows(true);
        }
    }

    public void moveDrawerByX(float dx) {
        setDrawerPosition(drawerPosition + dx);
    }

    public void setDrawerPosition(float value) {
        drawerPosition = value;
        if (drawerPosition > drawerLayout.getMeasuredWidth()) {
            drawerPosition = drawerLayout.getMeasuredWidth();
        } else if (drawerPosition < 0) {
            drawerPosition = 0;
        }
        requestLayout();

        final int newVisibility = drawerPosition > 0 ? VISIBLE : INVISIBLE;
        if (drawerLayout.getVisibility() != newVisibility) {
            drawerLayout.setVisibility(newVisibility);
        }
        setScrimOpacity(drawerPosition / (float)drawerLayout.getMeasuredWidth());
    }

    public float getDrawerPosition() {
        return drawerPosition;
    }

    public void cancelCurrentAnimation() {
        if (currentAnimation != null) {
            currentAnimation.cancel();
            currentAnimation = null;
        }
    }

    public void openDrawer(boolean fast) {
        if (AndroidUtilities.isTablet() && parentActionBarLayout != null && parentActionBarLayout.parentActivity != null) {
            AndroidUtilities.hideKeyboard(parentActionBarLayout.parentActivity.getCurrentFocus());
        }
        cancelCurrentAnimation();
        AnimatorSetProxy animatorSet = new AnimatorSetProxy();
        animatorSet.playTogether(
                ObjectAnimatorProxy.ofFloat(this, "drawerPosition", drawerLayout.getMeasuredWidth())
        );
        animatorSet.setInterpolator(new DecelerateInterpolator());
        if (fast) {
            animatorSet.setDuration(Math.max((int) (200.0f / drawerLayout.getMeasuredWidth() * (drawerLayout.getMeasuredWidth() - drawerPosition)), 50));
        } else {
            animatorSet.setDuration(300);
        }
        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Object animator) {
                onDrawerAnimationEnd(true);
            }

            @Override
            public void onAnimationCancel(Object animator) {
                onDrawerAnimationEnd(true);
            }
        });
        animatorSet.start();
        currentAnimation = animatorSet;
    }

    public void closeDrawer(boolean fast) {
        cancelCurrentAnimation();
        AnimatorSetProxy animatorSet = new AnimatorSetProxy();
        animatorSet.playTogether(
                ObjectAnimatorProxy.ofFloat(this, "drawerPosition", 0)
        );
        animatorSet.setInterpolator(new DecelerateInterpolator());
        if (fast) {
            animatorSet.setDuration(Math.max((int) (200.0f / drawerLayout.getMeasuredWidth() * drawerPosition), 50));
        } else {
            animatorSet.setDuration(300);
        }
        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Object animator) {
                onDrawerAnimationEnd(false);
            }

            @Override
            public void onAnimationCancel(Object animator) {
                onDrawerAnimationEnd(false);
            }
        });
        animatorSet.start();
    }

    private void onDrawerAnimationEnd(boolean opened) {
        startedTracking = false;
        currentAnimation = null;
        drawerOpened = opened;
        if (!opened) {
            if (drawerLayout instanceof ListView) {
                ((ListView)drawerLayout).setSelectionFromTop(0, 0);
            }
        }
    }

    private void setScrimOpacity(float value) {
        scrimOpacity = value;
        invalidate();
    }

    private float getScrimOpacity() {
        return scrimOpacity;
    }

    public View getDrawerLayout() {
        return drawerLayout;
    }

    public void setParentActionBarLayout(ActionBarLayout layout) {
        parentActionBarLayout = layout;
    }

    public void setAllowOpenDrawer(boolean value) {
        allowOpenDrawer = value;
        if (!allowOpenDrawer && drawerPosition != 0) {
            setDrawerPosition(0);
            onDrawerAnimationEnd(false);
        }
    }

    private void prepareForDrawerOpen(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        if (ev != null) {
            startedTrackingX = (int) ev.getX();
        }
        beginTrackingSent = false;
    }

    public boolean isDrawerOpened() {
        return drawerOpened;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!parentActionBarLayout.checkTransitionAnimation()) {
            if (drawerOpened && ev != null && ev.getX() > drawerPosition && !startedTracking) {
                if (ev.getAction() == MotionEvent.ACTION_UP) {
                    closeDrawer(false);
                }
                return true;
            }
            if (allowOpenDrawer && parentActionBarLayout.fragmentsStack.size() == 1) {
                if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && !startedTracking && !maybeStartTracking) {
                    startedTrackingPointerId = ev.getPointerId(0);
                    maybeStartTracking = true;
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    cancelCurrentAnimation();
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    float dx = (int) (ev.getX() - startedTrackingX);
                    float dy = Math.abs((int) ev.getY() - startedTrackingY);
                    velocityTracker.addMovement(ev);
                    if (maybeStartTracking && !startedTracking && (dx > 0 && dx / 3.0f > Math.abs(dy) || dx < 0 && Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= AndroidUtilities.dp(10))) {
                        prepareForDrawerOpen(ev);
                        startedTrackingX = (int) ev.getX();
                        requestDisallowInterceptTouchEvent(true);
                    } else if (startedTracking) {
                        if (!beginTrackingSent) {
                            if (((Activity)getContext()).getCurrentFocus() != null) {
                                AndroidUtilities.hideKeyboard(((Activity)getContext()).getCurrentFocus());
                            }
                            beginTrackingSent = true;
                        }
                        moveDrawerByX(dx);
                        startedTrackingX = (int) ev.getX();
                    }
                } else if (ev == null || ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.computeCurrentVelocity(1000);
                    /*if (!startedTracking) {
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        if (Math.abs(velX) >= 3500 && Math.abs(velX) > Math.abs(velY)) {
                            prepareForDrawerOpen(ev);
                            if (!beginTrackingSent) {
                                if (((Activity)getContext()).getCurrentFocus() != null) {
                                    AndroidUtilities.hideKeyboard(((Activity)getContext()).getCurrentFocus());
                                }
                                beginTrackingSent = true;
                            }
                        }
                    }*/
                    if (startedTracking || drawerPosition != 0 && drawerPosition != drawerLayout.getMeasuredWidth()) {
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        boolean backAnimation = drawerPosition < drawerLayout.getMeasuredWidth() / 2.0f && (velX < 3500 || Math.abs(velX) < Math.abs(velY)) || velX < 0 && Math.abs(velX) >= 3500;
                        if (!backAnimation) {
                            openDrawer(!drawerOpened && Math.abs(velX) >= 3500);
                        } else {
                            closeDrawer(drawerOpened && Math.abs(velX) >= 3500);
                        }
                        startedTracking = false;
                    } else {
                        maybeStartTracking = false;
                        startedTracking = false;
                    }
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
            }
            return startedTracking;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return parentActionBarLayout.checkTransitionAnimation() || onTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (maybeStartTracking && !startedTracking) {
            onTouchEvent(null);
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        final int width = r - l;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (drawerLayout != child) {
                child.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight());
            } else {
                child.layout(-child.getMeasuredWidth() + (int)drawerPosition, lp.topMargin, (int)drawerPosition, lp.topMargin + child.getMeasuredHeight());
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
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);

        final boolean applyInsets = lastInsets != null && Build.VERSION.SDK_INT >= 21;

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (applyInsets) {
                if (child.getFitsSystemWindows()) {
                    dispatchChildInsets(child, lastInsets, lp.gravity);
                } else {
                    applyMarginInsets(lp, lastInsets, lp.gravity, Build.VERSION.SDK_INT >= 21);
                }
            }

            if (drawerLayout != child) {
                final int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
                final int contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
                child.measure(contentWidthSpec, contentHeightSpec);
            } else {
                child.setPadding(0, 0, 0, 0);
                final int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec, minDrawerMargin + lp.leftMargin + lp.rightMargin, lp.width);
                final int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height);
                child.measure(drawerWidthSpec, drawerHeightSpec);
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final int height = getHeight();
        final boolean drawingContent = child != drawerLayout;
        int clipLeft = 0, clipRight = getWidth();

        final int restoreCount = canvas.save();
        if (drawingContent) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View v = getChildAt(i);
                if (v == child || v.getVisibility() != VISIBLE || v != drawerLayout || v.getHeight() < height) {
                    continue;
                }

                final int vright = v.getRight();
                if (vright > clipLeft) {
                    clipLeft = vright;
                }
            }
            canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        }
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);

        if (scrimOpacity > 0 && drawingContent) {
            scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * scrimOpacity) << 24);
            canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
        } else if (shadowLeft != null) {
            final float alpha = Math.max(0, Math.min(drawerPosition / AndroidUtilities.dp(20), 1.0f));
            if (alpha != 0) {
                shadowLeft.setBounds((int)drawerPosition, child.getTop(), (int)drawerPosition + shadowLeft.getIntrinsicWidth(), child.getBottom());
                shadowLeft.setAlpha((int) (0xff * alpha));
                shadowLeft.draw(canvas);
            }
        }
        return result;
    }
}
