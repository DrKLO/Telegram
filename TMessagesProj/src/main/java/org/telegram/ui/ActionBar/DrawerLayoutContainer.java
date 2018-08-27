/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Keep;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class DrawerLayoutContainer extends FrameLayout {

    private static final int MIN_DRAWER_MARGIN = 64;

    private ViewGroup drawerLayout;
    private ActionBarLayout parentActionBarLayout;

    private boolean maybeStartTracking;
    private boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId;
    private VelocityTracker velocityTracker;
    private boolean beginTrackingSent;
    private AnimatorSet currentAnimation;

    private Rect rect = new Rect();

    private int paddingTop;

    private Paint scrimPaint = new Paint();

    private Object lastInsets;
    private boolean inLayout;
    private int minDrawerMargin;
    private float scrimOpacity;
    private Drawable shadowLeft;
    private boolean allowOpenDrawer;

    private float drawerPosition;
    private boolean drawerOpened;
    private boolean allowDrawContent = true;

    public DrawerLayoutContainer(Context context) {
        super(context);

        minDrawerMargin = (int) (MIN_DRAWER_MARGIN * AndroidUtilities.density + 0.5f);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setFocusableInTouchMode(true);

        if (Build.VERSION.SDK_INT >= 21) {
            setFitsSystemWindows(true);
            setOnApplyWindowInsetsListener((v, insets) -> {
                final DrawerLayoutContainer drawerLayout = (DrawerLayoutContainer) v;
                AndroidUtilities.statusBarHeight = insets.getSystemWindowInsetTop();
                lastInsets = insets;
                drawerLayout.setWillNotDraw(insets.getSystemWindowInsetTop() <= 0 && getBackground() == null);
                drawerLayout.requestLayout();
                return insets.consumeSystemWindowInsets();
            });
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        shadowLeft = getResources().getDrawable(R.drawable.menu_shadow);
    }

    @SuppressLint("NewApi")
    private void dispatchChildInsets(View child, Object insets, int drawerGravity) {
        WindowInsets wi = (WindowInsets) insets;
        if (drawerGravity == Gravity.LEFT) {
            wi = wi.replaceSystemWindowInsets(wi.getSystemWindowInsetLeft(), wi.getSystemWindowInsetTop(), 0, wi.getSystemWindowInsetBottom());
        } else if (drawerGravity == Gravity.RIGHT) {
            wi = wi.replaceSystemWindowInsets(0, wi.getSystemWindowInsetTop(), wi.getSystemWindowInsetRight(), wi.getSystemWindowInsetBottom());
        }
        child.dispatchApplyWindowInsets(wi);
    }

    @SuppressLint("NewApi")
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

    @Keep
    public void setDrawerPosition(float value) {
        drawerPosition = value;
        if (drawerPosition > drawerLayout.getMeasuredWidth()) {
            drawerPosition = drawerLayout.getMeasuredWidth();
        } else if (drawerPosition < 0) {
            drawerPosition = 0;
        }
        drawerLayout.setTranslationX(drawerPosition);

        final int newVisibility = drawerPosition > 0 ? VISIBLE : GONE;
        if (drawerLayout.getVisibility() != newVisibility) {
            drawerLayout.setVisibility(newVisibility);
        }
        setScrimOpacity(drawerPosition / (float) drawerLayout.getMeasuredWidth());
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
        if (!allowOpenDrawer) {
            return;
        }
        if (AndroidUtilities.isTablet() && parentActionBarLayout != null && parentActionBarLayout.parentActivity != null) {
            AndroidUtilities.hideKeyboard(parentActionBarLayout.parentActivity.getCurrentFocus());
        }
        cancelCurrentAnimation();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, "drawerPosition", drawerLayout.getMeasuredWidth()));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        if (fast) {
            animatorSet.setDuration(Math.max((int) (200.0f / drawerLayout.getMeasuredWidth() * (drawerLayout.getMeasuredWidth() - drawerPosition)), 50));
        } else {
            animatorSet.setDuration(300);
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                onDrawerAnimationEnd(true);
            }
        });
        animatorSet.start();
        currentAnimation = animatorSet;
    }

    public void closeDrawer(boolean fast) {
        cancelCurrentAnimation();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, "drawerPosition", 0)
        );
        animatorSet.setInterpolator(new DecelerateInterpolator());
        if (fast) {
            animatorSet.setDuration(Math.max((int) (200.0f / drawerLayout.getMeasuredWidth() * drawerPosition), 50));
        } else {
            animatorSet.setDuration(300);
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
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
                ((ListView) drawerLayout).setSelectionFromTop(0, 0);
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

    public void setAllowOpenDrawer(boolean value, boolean animated) {
        allowOpenDrawer = value;
        if (!allowOpenDrawer && drawerPosition != 0) {
            if (!animated) {
                setDrawerPosition(0);
                onDrawerAnimationEnd(false);
            } else {
                closeDrawer(true);
            }
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

    public void setAllowDrawContent(boolean value) {
        if (allowDrawContent != value) {
            allowDrawContent = value;
            invalidate();
        }
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
                    parentActionBarLayout.getHitRect(rect);
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    if (rect.contains(startedTrackingX, startedTrackingY)) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        cancelCurrentAnimation();
                        if (velocityTracker != null) {
                            velocityTracker.clear();
                        }
                    }
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    float dx = (int) (ev.getX() - startedTrackingX);
                    float dy = Math.abs((int) ev.getY() - startedTrackingY);
                    velocityTracker.addMovement(ev);
                    if (maybeStartTracking && !startedTracking && (dx > 0 && dx / 3.0f > Math.abs(dy) && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.2f, true) || dx < 0 && Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.4f, true))) {
                        prepareForDrawerOpen(ev);
                        startedTrackingX = (int) ev.getX();
                        requestDisallowInterceptTouchEvent(true);
                    } else if (startedTracking) {
                        if (!beginTrackingSent) {
                            if (((Activity) getContext()).getCurrentFocus() != null) {
                                AndroidUtilities.hideKeyboard(((Activity) getContext()).getCurrentFocus());
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
                    if (startedTracking || drawerPosition != 0 && drawerPosition != drawerLayout.getMeasuredWidth()) {
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        boolean backAnimation = drawerPosition < drawerLayout.getMeasuredWidth() / 2.0f && (velX < 3500 || Math.abs(velX) < Math.abs(velY)) || velX < 0 && Math.abs(velX) >= 3500;
                        if (!backAnimation) {
                            openDrawer(!drawerOpened && Math.abs(velX) >= 3500);
                        } else {
                            closeDrawer(drawerOpened && Math.abs(velX) >= 3500);
                        }
                    }
                    startedTracking = false;
                    maybeStartTracking = false;
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
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (BuildVars.DEBUG_VERSION) {
                if (drawerLayout != child) {
                    child.layout(lp.leftMargin, lp.topMargin + getPaddingTop(), lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
                } else {
                    child.layout(-child.getMeasuredWidth(), lp.topMargin + getPaddingTop(), 0, lp.topMargin + child.getMeasuredHeight() +  + getPaddingTop());
                }
            } else {
                try {
                    if (drawerLayout != child) {
                        child.layout(lp.leftMargin, lp.topMargin + getPaddingTop(), lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
                    } else {
                        child.layout(-child.getMeasuredWidth(), lp.topMargin + getPaddingTop(), 0, lp.topMargin + child.getMeasuredHeight() + +getPaddingTop());
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        inLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!inLayout) {
            /*
            if (BuildVars.LOGS_ENABLED) {
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                for (int a = 0; a < elements.length; a++) {
                    FileLog.d("on " + elements[a]);
                }
            }*/
            super.requestLayout();
        }
    }

    @SuppressLint("NewApi")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);
        if (Build.VERSION.SDK_INT < 21) {
            inLayout = true;
            if (heightSize == AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight) {
                if (getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
                }
                heightSize = AndroidUtilities.displaySize.y;
            } else {
                if (getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    setPadding(0, 0, 0, 0);
                }
            }
            inLayout = false;
        }

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
                } else if (child.getTag() == null) {
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
        if (!allowDrawContent) {
            return false;
        }
        final int height = getHeight();
        final boolean drawingContent = child != drawerLayout;
        int lastVisibleChild = 0;
        int clipLeft = 0, clipRight = getWidth();

        final int restoreCount = canvas.save();
        if (drawingContent) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View v = getChildAt(i);
                if (v.getVisibility() == VISIBLE && v != drawerLayout) {
                    lastVisibleChild = i;
                }
                if (v == child || v.getVisibility() != VISIBLE || v != drawerLayout || v.getHeight() < height) {
                    continue;
                }

                final int vright = v.getRight();
                if (vright > clipLeft) {
                    clipLeft = vright;
                }
            }
            if (clipLeft != 0) {
                canvas.clipRect(clipLeft, 0, clipRight, getHeight());
            }
        }
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);

        if (scrimOpacity > 0 && drawingContent) {
            if (indexOfChild(child) == lastVisibleChild) {
                scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * scrimOpacity) << 24);
                canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
            }
        } else if (shadowLeft != null) {
            final float alpha = Math.max(0, Math.min(drawerPosition / AndroidUtilities.dp(20), 1.0f));
            if (alpha != 0) {
                shadowLeft.setBounds((int) drawerPosition, child.getTop(), (int) drawerPosition + shadowLeft.getIntrinsicWidth(), child.getBottom());
                shadowLeft.setAlpha((int) (0xff * alpha));
                shadowLeft.draw(canvas);
            }
        }
        return result;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
