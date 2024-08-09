/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.PasscodeView;

public class DrawerLayoutContainer extends FrameLayout {

    private static final int MIN_DRAWER_MARGIN = 64;

    private FrameLayout drawerLayout;
    private View navigationBar;
    private Paint navigationBarPaint = new Paint();
    private View drawerListView;
    private INavigationLayout parentActionBarLayout;

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
    private Paint backgroundPaint = new Paint();

    private int behindKeyboardColor;

    private boolean hasCutout;

    private Object lastInsets;
    private boolean inLayout;
    private int minDrawerMargin;
    private float scrimOpacity;
    private Drawable shadowLeft;
    private boolean allowOpenDrawer;
    private boolean allowOpenDrawerBySwipe = true;

    private float drawerPosition;
    private boolean drawerOpened;
    private boolean allowDrawContent = true;

    private boolean firstLayout = true;

    private BitmapDrawable previewBlurDrawable;
    private PreviewForegroundDrawable previewForegroundDrawable;
    private boolean drawCurrentPreviewFragmentAbove;
    private float startY;
    private boolean keyboardVisibility;
    private int imeHeight;

    public DrawerLayoutContainer(Context context) {
        super(context);

        minDrawerMargin = (int) (MIN_DRAWER_MARGIN * AndroidUtilities.density + 0.5f);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setFocusableInTouchMode(true);

        if (Build.VERSION.SDK_INT >= 21) {
            setFitsSystemWindows(true);
            setOnApplyWindowInsetsListener((v, insets) -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    boolean newKeyboardVisibility = insets.isVisible(WindowInsets.Type.ime());
                    int imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
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
                lastInsets = insets;
                drawerLayoutContainer.setWillNotDraw(insets.getSystemWindowInsetTop() <= 0 && getBackground() == null);

                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    hasCutout = cutout != null && cutout.getBoundingRects().size() != 0;
                }
                invalidate();
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return insets.consumeSystemWindowInsets();
                }
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

    public void setDrawerLayout(FrameLayout layout, View drawerListView) {
        drawerLayout = layout;
        this.drawerListView = drawerListView;
        addView(drawerLayout);
        drawerLayout.setVisibility(INVISIBLE);
        drawerListView.setVisibility(GONE);
        if (Build.VERSION.SDK_INT >= 21) {
            drawerLayout.setFitsSystemWindows(true);
        }
        AndroidUtilities.runOnUIThread(() -> {
            drawerListView.setVisibility(View.VISIBLE);
        }, 2500);
    }

    public void moveDrawerByX(float dx) {
        setDrawerPosition(drawerPosition + dx);
    }

    @Keep
    public void setDrawerPosition(float value) {
        if (drawerLayout == null) {
            return;
        }
        drawerPosition = value;
        if (drawerPosition > drawerLayout.getMeasuredWidth()) {
            drawerPosition = drawerLayout.getMeasuredWidth();
        } else if (drawerPosition < 0) {
            drawerPosition = 0;
        }
        drawerLayout.setTranslationX(drawerPosition);
        if (drawerPosition > 0 && drawerListView != null && drawerListView.getVisibility() != View.VISIBLE) {
            drawerListView.setVisibility(View.VISIBLE);
        }

        final int newVisibility = drawerPosition > 0 ? VISIBLE : INVISIBLE;
        if (drawerLayout.getVisibility() != newVisibility) {
            drawerLayout.setVisibility(newVisibility);
        }
        if (!parentActionBarLayout.getFragmentStack().isEmpty()) {
            BaseFragment currentFragment = parentActionBarLayout.getFragmentStack().get(0);
            if (drawerPosition == drawerLayout.getMeasuredWidth()) {
                currentFragment.setProgressToDrawerOpened(1f);
            } else if (drawerPosition == 0) {
                currentFragment.setProgressToDrawerOpened(0);
            } else {
                currentFragment.setProgressToDrawerOpened(drawerPosition / drawerLayout.getMeasuredWidth());
            }
        }
        setScrimOpacity(drawerPosition / (float) drawerLayout.getMeasuredWidth());
    }

    @Keep
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
        if (!allowOpenDrawer || drawerLayout == null) {
            return;
        }
        if (AndroidUtilities.isTablet() && parentActionBarLayout != null && parentActionBarLayout.getParentActivity() != null) {
            AndroidUtilities.hideKeyboard(parentActionBarLayout.getParentActivity().getCurrentFocus());
        }
        cancelCurrentAnimation();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, "drawerPosition", drawerLayout.getMeasuredWidth()));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        if (fast) {
            animatorSet.setDuration(Math.max((int) (200.0f / drawerLayout.getMeasuredWidth() * (drawerLayout.getMeasuredWidth() - drawerPosition)), 50));
        } else {
            animatorSet.setDuration(250);
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
        if (drawerLayout == null) {
            return;
        }
        cancelCurrentAnimation();
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, "drawerPosition", 0)
        );
        animatorSet.setInterpolator(new DecelerateInterpolator());
        if (fast) {
            animatorSet.setDuration(Math.max((int) (200.0f / drawerLayout.getMeasuredWidth() * drawerPosition), 50));
        } else {
            animatorSet.setDuration(250);
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
        if (Build.VERSION.SDK_INT >= 19) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != drawerLayout) {
                    child.setImportantForAccessibility(opened ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                }
            }
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private void setScrimOpacity(float value) {
        scrimOpacity = value;
        invalidate();
    }

    private float getScrimOpacity() {
        return scrimOpacity;
    }

    public FrameLayout getDrawerLayout() {
        return drawerLayout;
    }

    public void setParentActionBarLayout(INavigationLayout layout) {
        parentActionBarLayout = layout;
    }

    public void presentFragment(BaseFragment fragment) {
        if (parentActionBarLayout != null) {
            parentActionBarLayout.presentFragment(fragment);
        }
        closeDrawer(false);
    }

    public INavigationLayout getParentActionBarLayout() {
        return parentActionBarLayout;
    }

    public void openStatusSelect() {
        
    }

    public void closeDrawer() {
        if (drawerPosition != 0) {
            setDrawerPosition(0);
            onDrawerAnimationEnd(false);
        }
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

    public boolean isAllowOpenDrawer() {
        return allowOpenDrawer;
    }

    public void setAllowOpenDrawerBySwipe(boolean value) {
        allowOpenDrawerBySwipe = value;
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

    public boolean isDrawCurrentPreviewFragmentAbove() {
        return drawCurrentPreviewFragmentAbove;
    }

    public void setDrawCurrentPreviewFragmentAbove(boolean drawCurrentPreviewFragmentAbove) {
        if (this.drawCurrentPreviewFragmentAbove != drawCurrentPreviewFragmentAbove) {
            this.drawCurrentPreviewFragmentAbove = drawCurrentPreviewFragmentAbove;
            if (drawCurrentPreviewFragmentAbove) {
                createBlurDrawable();
                previewForegroundDrawable = new PreviewForegroundDrawable();
            } else {
                startY = 0;
                previewBlurDrawable = null;
                previewForegroundDrawable = null;
            }
            invalidate();
        }
    }

    private void createBlurDrawable() {
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        int w = (int) (measuredWidth / 6.0f);
        int h = (int) (measuredHeight / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        previewBlurDrawable = new BitmapDrawable(bitmap);
        previewBlurDrawable.setBounds(0, 0, measuredWidth, measuredHeight);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (drawCurrentPreviewFragmentAbove && parentActionBarLayout != null) {
            final int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) {
                if (startY == 0) {
                    startY = ev.getY();
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    super.dispatchTouchEvent(event);
                    event.recycle();
                } else {
                    parentActionBarLayout.movePreviewFragment(startY - ev.getY());
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                parentActionBarLayout.finishPreviewFragment();
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (drawerLayout != null && !parentActionBarLayout.checkTransitionAnimation()) {
            if (drawerOpened && ev != null && ev.getX() > drawerPosition && !startedTracking) {
                if (ev.getAction() == MotionEvent.ACTION_UP) {
                    closeDrawer(false);
                }
                return true;
            }

            if ((allowOpenDrawerBySwipe || drawerOpened) && allowOpenDrawer && parentActionBarLayout.getFragmentStack().size() == 1 && (parentActionBarLayout.getLastFragment().getLastStoryViewer() == null || !parentActionBarLayout.getLastFragment().getLastStoryViewer().attachedToParent())) {
                if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && !startedTracking && !maybeStartTracking) {
                   View scrollingChild = findScrollingChild(this, ev.getX(),ev.getY());
                   if (scrollingChild != null) {
                       return false;
                   }
                    parentActionBarLayout.getView().getHitRect(rect);
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
                    if (maybeStartTracking && !startedTracking && (dx > 0 && dx / 3.0f > Math.abs(dy) && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.2f, true) || drawerOpened && dx < 0 && Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.4f, true))) {
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
            } else {
                if (ev == null || ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
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

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        int n = parent.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (rect.contains((int) x, (int) y)) {
                if (child.canScrollHorizontally(-1)) {
                    return child;
                } else if (child instanceof ViewGroup) {
                    View v = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
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
                    child.layout(-child.getMeasuredWidth(), lp.topMargin + getPaddingTop(), 0, lp.topMargin + child.getMeasuredHeight() + +getPaddingTop());
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
                if (getLayoutParams() instanceof MarginLayoutParams) {
                    setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
                }
                heightSize = AndroidUtilities.displaySize.y;
            } else {
                if (getLayoutParams() instanceof MarginLayoutParams) {
                    setPadding(0, 0, 0, 0);
                }
            }
            inLayout = false;
        } else {
            int newSize = heightSize - AndroidUtilities.statusBarHeight;
            if (newSize > 0 && newSize < 4096) {
                AndroidUtilities.displaySize.y = newSize;
            }
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
                final int contentHeightSpec;
                if (lp.height > 0) {
                    contentHeightSpec = lp.height;
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
            } else {
                child.setPadding(0, 0, 0, 0);
                final int drawerWidthSpec = getChildMeasureSpec(widthMeasureSpec, minDrawerMargin + lp.leftMargin + lp.rightMargin, lp.width);
                final int drawerHeightSpec = getChildMeasureSpec(heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height);
                child.measure(drawerWidthSpec, drawerHeightSpec);
            }
        }

        if (navigationBar != null) {
            if (navigationBar.getParent() == null) {
                ((FrameLayout) AndroidUtilities.findActivity(getContext()).getWindow().getDecorView()).addView(navigationBar);
            }
            if (navigationBar.getLayoutParams().height != AndroidUtilities.navigationBarHeight || ((LayoutParams)navigationBar.getLayoutParams()).topMargin != MeasureSpec.getSize(heightMeasureSpec)) {
                navigationBar.getLayoutParams().height = AndroidUtilities.navigationBarHeight;
                ((LayoutParams)navigationBar.getLayoutParams()).topMargin = MeasureSpec.getSize(heightMeasureSpec);
                navigationBar.requestLayout();
            }
        }
    }

    public void setBehindKeyboardColor(int color) {
        behindKeyboardColor = color;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (drawCurrentPreviewFragmentAbove && parentActionBarLayout != null) {
            if (previewBlurDrawable != null) {
                previewBlurDrawable.setAlpha((int) (parentActionBarLayout.getCurrentPreviewFragmentAlpha() * 255));
                previewBlurDrawable.draw(canvas);
            }
            parentActionBarLayout.drawCurrentPreviewFragment(canvas, Build.VERSION.SDK_INT >= 21 ? previewForegroundDrawable : null);
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

                final int vright = (int) Math.ceil(v.getX()) + v.getMeasuredWidth();
                if (vright > clipLeft) {
                    clipLeft = vright;
                }
            }
            if (clipLeft != 0) {
                canvas.clipRect(clipLeft - AndroidUtilities.dp(1), 0, clipRight, getHeight());
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
    protected void onDraw(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
            WindowInsets insets = (WindowInsets) lastInsets;

            int bottomInset = insets.getSystemWindowInsetBottom();
            if (bottomInset > 0) {
                backgroundPaint.setColor(behindKeyboardColor);
                canvas.drawRect(0, getMeasuredHeight() - bottomInset, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }

            if (hasCutout) {
                backgroundPaint.setColor(0xff000000);
                int left = insets.getSystemWindowInsetLeft();
                if (left != 0) {
                    canvas.drawRect(0, 0, left, getMeasuredHeight(), backgroundPaint);
                }
                int right = insets.getSystemWindowInsetRight();
                if (right != 0) {
                    canvas.drawRect(right, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
                }
            }
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(View child, AccessibilityEvent event) {
        if (drawerOpened && child != drawerLayout) {
            return false;
        }
        return super.onRequestSendAccessibilityEvent(child, event);
    }

    public void setNavigationBarColor(int color) {
        navigationBarPaint.setColor(color);
        if (navigationBar != null) {
            navigationBar.invalidate();
        }
    }

    public int getNavigationBarColor() {
        return navigationBarPaint.getColor();
    }

    public View createNavigationBar() {
        navigationBar = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), navigationBarPaint);
            }
        };
        navigationBarPaint.setColor(0xff000000);
        return navigationBar;
    }

    private static class PreviewForegroundDrawable extends Drawable {

        private final GradientDrawable topDrawable;
        private final GradientDrawable bottomDrawable;

        public PreviewForegroundDrawable() {
            super();
            topDrawable = new GradientDrawable();
            topDrawable.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_actionBarDefault));
            topDrawable.setCornerRadius(AndroidUtilities.dp(6));
            bottomDrawable = new GradientDrawable();
            bottomDrawable.setStroke(1, Theme.getColor(Theme.key_divider));
            bottomDrawable.setCornerRadius(AndroidUtilities.dp(6));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();

            canvas.save();
            canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.top + ActionBar.getCurrentActionBarHeight());
            topDrawable.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.clipRect(bounds.left, bounds.top + ActionBar.getCurrentActionBarHeight(), bounds.right, bounds.bottom);
            bottomDrawable.draw(canvas);
            canvas.restore();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            topDrawable.setBounds(bounds);
            bottomDrawable.setBounds(bounds);
        }

        @Override
        public void setAlpha(int i) {
            topDrawable.setAlpha(i);
            bottomDrawable.setAlpha(i);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
