/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.ActionBar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.AnimationCompat.ViewProxy;

import java.util.ArrayList;

public class ActionBarLayout extends FrameLayout {

    public static interface ActionBarLayoutDelegate {
        public abstract boolean onPreIme();
        public abstract boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ActionBarLayout layout);
        public abstract boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout);
        public abstract boolean needCloseLastFragment(ActionBarLayout layout);
        public abstract void onRebuildAllFragments(ActionBarLayout layout);
    }

    public class LinearLayoutContainer extends LinearLayout {

        public LinearLayoutContainer(Context context) {
            super(context);
            setOrientation(VERTICAL);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child instanceof ActionBar) {
                return super.drawChild(canvas, child, drawingTime);
            } else {
                boolean wasActionBar = false;
                int actionBarHeight = 0;
                int childCount = getChildCount();
                for (int a = 0; a < childCount; a++) {
                    View view = getChildAt(a);
                    if (view == child) {
                        continue;
                    }
                    if (view instanceof ActionBar && view.getVisibility() == VISIBLE) {
                        actionBarHeight = view.getMeasuredHeight();
                        wasActionBar = true;
                        break;
                    }
                }
                /*if (!wasActionBar) {
                    if (child instanceof ViewGroup) {
                        ViewGroup viewGroup = (ViewGroup) child;
                        childCount = viewGroup.getChildCount();
                        for (int a = 0; a < childCount; a++) {
                            View possibleActionBar = viewGroup.getChildAt(a);
                            if (possibleActionBar instanceof ActionBar) {
                                actionBarHeight = possibleActionBar.getMeasuredHeight();
                                break;
                            }
                        }
                    }
                }*/
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (actionBarHeight != 0 && headerShadowDrawable != null) {
                    headerShadowDrawable.setBounds(0, actionBarHeight, getMeasuredWidth(), actionBarHeight + headerShadowDrawable.getIntrinsicHeight());
                    headerShadowDrawable.draw(canvas);
                }
                return result;
            }
        }
    }

    private static Drawable headerShadowDrawable;
    private static Drawable layerShadowDrawable;
    private static Paint scrimPaint;

    private LinearLayoutContainer containerView;
    private LinearLayoutContainer containerViewBack;
    private DrawerLayoutContainer drawerLayoutContainer;
    private ActionBar currentActionBar;

    private AnimatorSetProxy currentAnimation;

    public float innerTranslationX;

    private boolean maybeStartTracking;
    protected boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    protected boolean animationInProgress;
    private VelocityTracker velocityTracker;
    private boolean beginTrackingSent;
    private boolean transitionAnimationInProgress;
    private long transitionAnimationStartTime;
    private boolean inActionMode;
    private int startedTrackingPointerId;
    private Runnable onCloseAnimationEndRunnable;
    private Runnable onOpenAnimationEndRunnable;
    private boolean useAlphaAnimations;
    private View backgroundView;
    private boolean removeActionBarExtraHeight;

    private String titleOverlayText;

    private ActionBarLayoutDelegate delegate = null;
    protected Activity parentActivity = null;

    public ArrayList<BaseFragment> fragmentsStack = null;

    public ActionBarLayout(Context context) {
        super(context);
        parentActivity = (Activity) context;

        if (layerShadowDrawable == null) {
            layerShadowDrawable = getResources().getDrawable(R.drawable.layer_shadow);
            headerShadowDrawable = getResources().getDrawable(R.drawable.header_shadow);
            scrimPaint = new Paint();
        }
    }

    public void init(ArrayList<BaseFragment> stack) {
        fragmentsStack = stack;
        containerViewBack = new LinearLayoutContainer(parentActivity);
        addView(containerViewBack);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerViewBack.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerViewBack.setLayoutParams(layoutParams);

        containerView = new LinearLayoutContainer(parentActivity);
        addView(containerView);
        layoutParams = (FrameLayout.LayoutParams) containerView.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerView.setLayoutParams(layoutParams);

        for (BaseFragment fragment : fragmentsStack) {
            fragment.setParentLayout(this);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onConfigurationChanged(newConfig);
        }
    }

    public void setInnerTranslationX(float value) {
        innerTranslationX = value;
        invalidate();
    }

    public float getInnerTranslationX() {
        return innerTranslationX;
    }

    public void onResume() {
        if (transitionAnimationInProgress) {
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
            if (onCloseAnimationEndRunnable != null) {
                onCloseAnimationEnd(false);
            } else if (onOpenAnimationEndRunnable != null) {
                onOpenAnimationEnd(false);
            }
        }
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onResume();
        }
    }

    public void onPause() {
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onPause();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return !(!animationInProgress && !checkTransitionAnimation()) || onTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        onTouchEvent(null);
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return delegate != null && delegate.onPreIme() || super.dispatchKeyEventPreIme(event);
        }
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int translationX = (int) innerTranslationX + getPaddingRight();
        int clipLeft = getPaddingLeft();
        int clipRight = width + getPaddingLeft();

        if (child == containerViewBack) {
            clipRight = translationX;
        } else if (child == containerView) {
            clipLeft = translationX;
        }

        final int restoreCount = canvas.save();
        if (!transitionAnimationInProgress) {
            canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        }
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);

        if (translationX != 0) {
            if (child == containerView) {
                final float alpha = Math.max(0, Math.min((width - translationX) / (float) AndroidUtilities.dp(20), 1.0f));
                layerShadowDrawable.setBounds(translationX - layerShadowDrawable.getIntrinsicWidth(), child.getTop(), translationX, child.getBottom());
                layerShadowDrawable.setAlpha((int) (0xff * alpha));
                layerShadowDrawable.draw(canvas);
            } else if (child == containerViewBack) {
                final float opacity = Math.min(0.8f, (width - translationX) / (float)width);
                scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * opacity) << 24);
                canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
            }
        }

        return result;
    }

    public void setDelegate(ActionBarLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    private void onSlideAnimationEnd(final boolean backAnimation) {
        if (!backAnimation) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onPause();
            lastFragment.onFragmentDestroy();
            lastFragment.setParentLayout(null);
            fragmentsStack.remove(fragmentsStack.size() - 1);

            LinearLayoutContainer temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;
            bringChildToFront(containerView);

            lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            currentActionBar = lastFragment.actionBar;
            lastFragment.onResume();
        } else {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
            lastFragment.onPause();
            if (lastFragment.fragmentView != null) {
                ViewGroup parent = (ViewGroup) lastFragment.fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(lastFragment.fragmentView);
                }
            }
            if (lastFragment.needAddActionBar() && lastFragment.actionBar != null) {
                ViewGroup parent = (ViewGroup) lastFragment.actionBar.getParent();
                if (parent != null) {
                    parent.removeView(lastFragment.actionBar);
                }
            }
        }
        containerViewBack.setVisibility(View.GONE);
        //AndroidUtilities.unlockOrientation(parentActivity);
        startedTracking = false;
        animationInProgress = false;

        ViewProxy.setTranslationX(containerView, 0);
        ViewProxy.setTranslationX(containerViewBack, 0);
        setInnerTranslationX(0);
    }

    private void prepareForMoving(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        startedTrackingX = (int) ev.getX();
        containerViewBack.setVisibility(View.VISIBLE);
        beginTrackingSent = false;

        BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        View fragmentView = lastFragment.createView(parentActivity.getLayoutInflater(), null);
        ViewGroup parent = (ViewGroup) fragmentView.getParent();
        if (parent != null) {
            parent.removeView(fragmentView);
        }
        if (lastFragment.needAddActionBar() && lastFragment.actionBar != null) {
            parent = (ViewGroup) lastFragment.actionBar.getParent();
            if (parent != null) {
                parent.removeView(lastFragment.actionBar);
            }
            if (removeActionBarExtraHeight) {
                lastFragment.actionBar.setOccupyStatusBar(false);
            }
            containerViewBack.addView(lastFragment.actionBar);
            lastFragment.actionBar.setTitleOverlayText(titleOverlayText);
        }
        containerViewBack.addView(fragmentView);
        ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        fragmentView.setLayoutParams(layoutParams);
        if (fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(0xffffffff);
        }
        lastFragment.onResume();

        //AndroidUtilities.lockOrientation(parentActivity);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!checkTransitionAnimation() && !inActionMode && !animationInProgress) {
            if (fragmentsStack.size() > 1) {
                if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                    BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (!currentFragment.swipeBackEnabled) {
                        return false;
                    }
                    startedTrackingPointerId = ev.getPointerId(0);
                    maybeStartTracking = true;
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    int dx = Math.max(0, (int) (ev.getX() - startedTrackingX));
                    int dy = Math.abs((int) ev.getY() - startedTrackingY);
                    velocityTracker.addMovement(ev);
                    if (maybeStartTracking && !startedTracking && dx >= AndroidUtilities.dp(10) && Math.abs(dx) / 3 > dy) {
                        prepareForMoving(ev);
                    } else if (startedTracking) {
                        if (!beginTrackingSent) {
                            if (parentActivity.getCurrentFocus() != null) {
                                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                            }
                            BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                            currentFragment.onBeginSlide();
                            beginTrackingSent = true;
                        }
                        ViewProxy.setTranslationX(containerView, dx);
                        setInnerTranslationX(dx);
                    }
                } else if (ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.computeCurrentVelocity(1000);
                    if (!startedTracking) {
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        if (velX >= 3500 && velX > velY) {
                            prepareForMoving(ev);
                            if (!beginTrackingSent) {
                                if (((Activity) getContext()).getCurrentFocus() != null) {
                                    AndroidUtilities.hideKeyboard(((Activity) getContext()).getCurrentFocus());
                                }
                                beginTrackingSent = true;
                            }
                        }
                    }
                    if (startedTracking) {
                        float x = ViewProxy.getX(containerView);
                        AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        final boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
                        float distToMove = 0;
                        if (!backAnimation) {
                            distToMove = containerView.getMeasuredWidth() - x;
                            animatorSet.playTogether(
                                    ObjectAnimatorProxy.ofFloat(containerView, "x", containerView.getMeasuredWidth()),
                                    ObjectAnimatorProxy.ofFloat(this, "innerTranslationX", (float)containerView.getMeasuredWidth())
                            );
                        } else {
                            distToMove = x;
                            animatorSet.playTogether(
                                    ObjectAnimatorProxy.ofFloat(containerView, "x", 0),
                                    ObjectAnimatorProxy.ofFloat(this, "innerTranslationX", 0.0f)
                            );
                        }

                        animatorSet.setDuration(Math.max((int) (200.0f / containerView.getMeasuredWidth() * distToMove), 50));
                        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animator) {
                                onSlideAnimationEnd(backAnimation);
                            }

                            @Override
                            public void onAnimationCancel(Object animator) {
                                onSlideAnimationEnd(backAnimation);
                            }
                        });
                        animatorSet.start();
                        animationInProgress = true;
                    } else {
                        maybeStartTracking = false;
                        startedTracking = false;
                    }
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                } else if (ev == null) {
                    maybeStartTracking = false;
                    startedTracking = false;
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

    public void onBackPressed() {
        if (startedTracking || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
            return;
        }
        if (currentActionBar != null && currentActionBar.isSearchFieldVisible) {
            currentActionBar.closeSearchField();
            return;
        }
        BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        if (lastFragment.onBackPressed()) {
            if (!fragmentsStack.isEmpty()) {
                closeLastFragment(true);
            }
        }
    }

    public void onLowMemory() {
        for (BaseFragment fragment : fragmentsStack) {
            fragment.onLowMemory();
        }
    }

    private void onAnimationEndCheck(boolean byCheck) {
        onCloseAnimationEnd(false);
        onOpenAnimationEnd(false);
        if (currentAnimation != null) {
            if (byCheck) {
                currentAnimation.cancel();
            }
            currentAnimation = null;
        }
        ViewProxy.setAlpha(this, 1.0f);
        ViewProxy.setAlpha(containerView, 1.0f);
        ViewProxy.setScaleX(containerView, 1.0f);
        ViewProxy.setScaleY(containerView, 1.0f);
        ViewProxy.setAlpha(containerViewBack, 1.0f);
        ViewProxy.setScaleX(containerViewBack, 1.0f);
        ViewProxy.setScaleY(containerViewBack, 1.0f);
    }

    public boolean checkTransitionAnimation() {
        if (transitionAnimationInProgress && transitionAnimationStartTime < System.currentTimeMillis() - 400) {
            transitionAnimationInProgress = false;
            onAnimationEndCheck(true);
        }
        return transitionAnimationInProgress;
    }

    private void presentFragmentInternalRemoveOld(boolean removeLast, final BaseFragment fragment) {
        if (fragment == null) {
            return;
        }
        fragment.onPause();
        if (removeLast) {
            fragment.onFragmentDestroy();
            fragment.setParentLayout(null);
            fragmentsStack.remove(fragment);
        } else {
            if (fragment.fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragment.fragmentView.getParent();
                if (parent != null) {
                    parent.removeView(fragment.fragmentView);
                }
            }
            if (fragment.needAddActionBar() && fragment.actionBar != null) {
                ViewGroup parent = (ViewGroup) fragment.actionBar.getParent();
                if (parent != null) {
                    parent.removeView(fragment.actionBar);
                }
            }
        }
        containerViewBack.setVisibility(View.GONE);
    }

    public boolean presentFragment(BaseFragment fragment) {
        return presentFragment(fragment, false, false, true);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return presentFragment(fragment, removeLast, false, true);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation, boolean check) {
        if (checkTransitionAnimation() || delegate != null && check && !delegate.needPresentFragment(fragment, removeLast, forceWithoutAnimation, this) || !fragment.onFragmentCreate()) {
            return false;
        }
        if (parentActivity.getCurrentFocus() != null) {
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.hideEmojiKeyboard);
        }
        boolean needAnimation = Build.VERSION.SDK_INT > 10 && !forceWithoutAnimation && parentActivity.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);

        final BaseFragment currentFragment = !fragmentsStack.isEmpty() ? fragmentsStack.get(fragmentsStack.size() - 1) : null;

        fragment.setParentLayout(this);
        View fragmentView = fragment.createView(parentActivity.getLayoutInflater(), null);
        if (fragment.needAddActionBar() && fragment.actionBar != null) {
            if (removeActionBarExtraHeight) {
                fragment.actionBar.setOccupyStatusBar(false);
            }
            ViewGroup parent = (ViewGroup) fragment.actionBar.getParent();
            if (parent != null) {
                parent.removeView(fragment.actionBar);
            }
            containerViewBack.addView(fragment.actionBar);
            fragment.actionBar.setTitleOverlayText(titleOverlayText);
        }

        containerViewBack.addView(fragmentView);
        ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        fragmentView.setLayoutParams(layoutParams);
        fragmentsStack.add(fragment);
        fragment.onResume();
        currentActionBar = fragment.actionBar;
        if (fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(0xffffffff);
        }

        LinearLayoutContainer temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setVisibility(View.VISIBLE);

        bringChildToFront(containerView);

        if (!needAnimation) {
            presentFragmentInternalRemoveOld(removeLast, currentFragment);
            if (backgroundView != null) {
                backgroundView.setVisibility(VISIBLE);
            }
        }

        if (needAnimation) {
            if (useAlphaAnimations && fragmentsStack.size() == 1) {
                presentFragmentInternalRemoveOld(removeLast, currentFragment);

                ArrayList<Object> animators = new ArrayList<Object>();
                animators.add(ObjectAnimatorProxy.ofFloat(this, "alpha", 0.0f, 1.0f));
                if (backgroundView != null) {
                    backgroundView.setVisibility(VISIBLE);
                    animators.add(ObjectAnimatorProxy.ofFloat(backgroundView, "alpha", 0.0f, 1.0f));
                }

                currentAnimation = new AnimatorSetProxy();
                currentAnimation.playTogether(animators);
                currentAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            } else {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                onOpenAnimationEndRunnable = new Runnable() {
                    @Override
                    public void run() {
                        presentFragmentInternalRemoveOld(removeLast, currentFragment);
                        fragment.onOpenAnimationEnd();
                        ViewProxy.setTranslationX(containerView, 0);
                    }
                };
                currentAnimation = new AnimatorSetProxy();
                currentAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(containerView, "alpha", 0.0f, 1.0f),
                        ObjectAnimatorProxy.ofFloat(containerView, "translationX", AndroidUtilities.dp(48), 0));
                currentAnimation.setInterpolator(new DecelerateInterpolator(1.5f));
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            }
        } else {
            if (backgroundView != null) {
                ViewProxy.setAlpha(backgroundView, 1.0f);
                backgroundView.setVisibility(VISIBLE);
            }
            fragment.onOpenAnimationEnd();
        }
        return true;
    }

    public boolean addFragmentToStack(BaseFragment fragment) {
        return addFragmentToStack(fragment, -1);
    }

    public boolean addFragmentToStack(BaseFragment fragment, int position) {
        if (delegate != null && !delegate.needAddFragmentToStack(fragment, this) || !fragment.onFragmentCreate()) {
            return false;
        }
        fragment.setParentLayout(this);
        if (position == -1) {
            fragmentsStack.add(fragment);
        } else {
            fragmentsStack.add(position, fragment);
        }
        return true;
    }

    private void closeLastFragmentInternalRemoveOld(BaseFragment fragment) {
        fragment.onPause();
        fragment.onFragmentDestroy();
        fragment.setParentLayout(null);
        fragmentsStack.remove(fragment);
        containerViewBack.setVisibility(View.GONE);
        bringChildToFront(containerView);
    }

    public void closeLastFragment(boolean animated) {
        if (delegate != null && !delegate.needCloseLastFragment(this) || checkTransitionAnimation()) {
            return;
        }
        if (parentActivity.getCurrentFocus() != null) {
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
        }
        boolean needAnimation = Build.VERSION.SDK_INT > 10 && animated && parentActivity.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);
        final BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        BaseFragment previousFragment = null;
        if (fragmentsStack.size() > 1) {
            previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        }

        if (previousFragment != null) {
            LinearLayoutContainer temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;
            containerView.setVisibility(View.VISIBLE);

            previousFragment.setParentLayout(this);
            View fragmentView = previousFragment.createView(parentActivity.getLayoutInflater(), null);
            if (previousFragment.needAddActionBar() && previousFragment.actionBar != null) {
                if (removeActionBarExtraHeight) {
                    previousFragment.actionBar.setOccupyStatusBar(false);
                }
                ViewGroup parent = (ViewGroup) previousFragment.actionBar.getParent();
                if (parent != null) {
                    parent.removeView(previousFragment.actionBar);
                }
                containerView.addView(previousFragment.actionBar);
                previousFragment.actionBar.setTitleOverlayText(titleOverlayText);
            }
            containerView.addView(fragmentView);
            ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            fragmentView.setLayoutParams(layoutParams);
            previousFragment.onResume();
            currentActionBar = previousFragment.actionBar;
            if (fragmentView.getBackground() == null) {
                fragmentView.setBackgroundColor(0xffffffff);
            }

            if (!needAnimation) {
                closeLastFragmentInternalRemoveOld(currentFragment);
            }

            if (needAnimation) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                onCloseAnimationEndRunnable = new Runnable() {
                    @Override
                    public void run() {
                        closeLastFragmentInternalRemoveOld(currentFragment);
                        ViewProxy.setTranslationX(containerViewBack, 0);
                    }
                };

                currentAnimation = new AnimatorSetProxy();
                currentAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(containerViewBack, "alpha", 1.0f, 0.0f),
                        ObjectAnimatorProxy.ofFloat(containerViewBack, "translationX", 0, AndroidUtilities.dp(48)));
                currentAnimation.setInterpolator(new DecelerateInterpolator(1.5f));
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            }
        } else {
            if (useAlphaAnimations) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;

                onCloseAnimationEndRunnable = new Runnable() {
                    @Override
                    public void run() {
                        removeFragmentFromStack(currentFragment);
                        setVisibility(GONE);
                        if (backgroundView != null) {
                            backgroundView.setVisibility(GONE);
                        }
                        if (drawerLayoutContainer != null) {
                            drawerLayoutContainer.setAllowOpenDrawer(true);
                        }
                    }
                };

                ArrayList<Object> animators = new ArrayList<Object>();
                animators.add(ObjectAnimatorProxy.ofFloat(this, "alpha", 1.0f, 0.0f));
                if (backgroundView != null) {
                    animators.add(ObjectAnimatorProxy.ofFloat(backgroundView, "alpha", 1.0f, 0.0f));
                }

                currentAnimation = new AnimatorSetProxy();
                currentAnimation.playTogether(animators);
                currentAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            } else {
                removeFragmentFromStack(currentFragment);
                setVisibility(GONE);
                if (backgroundView != null) {
                    backgroundView.setVisibility(GONE);
                }
            }
        }
    }

    public void showLastFragment() {
        if (fragmentsStack.isEmpty()) {
            return;
        }
        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        previousFragment.setParentLayout(this);
        View fragmentView = previousFragment.createView(parentActivity.getLayoutInflater(), null);
        if (previousFragment.needAddActionBar() && previousFragment.actionBar != null) {
            if (removeActionBarExtraHeight) {
                previousFragment.actionBar.setOccupyStatusBar(false);
            }
            ViewGroup parent = (ViewGroup) previousFragment.actionBar.getParent();
            if (parent != null) {
                parent.removeView(previousFragment.actionBar);
            }
            containerView.addView(previousFragment.actionBar);
            previousFragment.actionBar.setTitleOverlayText(titleOverlayText);
        }
        containerView.addView(fragmentView);
        ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        fragmentView.setLayoutParams(layoutParams);
        previousFragment.onResume();
        currentActionBar = previousFragment.actionBar;
        if (fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(0xffffffff);
        }
    }

    public void removeFragmentFromStack(BaseFragment fragment) {
        fragment.onPause();
        fragment.onFragmentDestroy();
        fragment.setParentLayout(null);
        fragmentsStack.remove(fragment);
    }

    public void removeAllFragments() {
        for (int a = 0; a < fragmentsStack.size(); a++) {
            removeFragmentFromStack(fragmentsStack.get(a));
            a--;
        }
    }

    public void rebuildAllFragmentViews(boolean last) {
        for (int a = 0; a < fragmentsStack.size() - (last ? 0 : 1); a++) {
            fragmentsStack.get(a).setParentLayout(null);
            fragmentsStack.get(a).setParentLayout(this);
        }
        if (delegate != null) {
            delegate.onRebuildAllFragments(this);
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !checkTransitionAnimation() && !startedTracking && currentActionBar != null) {
            currentActionBar.onMenuButtonPressed();
        }
        return super.onKeyUp(keyCode, event);
    }

    public void onActionModeStarted(ActionMode mode) {
        if (currentActionBar != null) {
            currentActionBar.setVisibility(GONE);
        }
        inActionMode = true;
    }

    public void onActionModeFinished(ActionMode mode) {
        if (currentActionBar != null) {
            currentActionBar.setVisibility(VISIBLE);
        }
        inActionMode = false;
    }

    private void onCloseAnimationEnd(boolean post) {
        if (transitionAnimationInProgress && onCloseAnimationEndRunnable != null) {
            transitionAnimationInProgress = false;
            transitionAnimationStartTime = 0;
            if (post) {
                new Handler().post(new Runnable() {
                    public void run() {
                        onCloseAnimationEndRunnable.run();
                        onCloseAnimationEndRunnable = null;
                    }
                });
            } else {
                onCloseAnimationEndRunnable.run();
                onCloseAnimationEndRunnable = null;
            }
        }
    }

    private void onOpenAnimationEnd(boolean post) {
        if (transitionAnimationInProgress && onOpenAnimationEndRunnable != null) {
            transitionAnimationInProgress = false;
            transitionAnimationStartTime = 0;
            if (post) {
                new Handler().post(new Runnable() {
                    public void run() {
                        onOpenAnimationEndRunnable.run();
                        onOpenAnimationEndRunnable = null;
                    }
                });
            } else {
                onOpenAnimationEndRunnable.run();
                onOpenAnimationEndRunnable = null;
            }
        }
    }

    public void startActivityForResult(final Intent intent, final int requestCode) {
        if (parentActivity == null) {
            return;
        }
        if (transitionAnimationInProgress) {
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
            if (onCloseAnimationEndRunnable != null) {
                onCloseAnimationEnd(false);
            } else if (onOpenAnimationEndRunnable != null) {
                onOpenAnimationEnd(false);
            }
            containerView.invalidate();
            if (intent != null) {
                parentActivity.startActivityForResult(intent, requestCode);
            }
        } else {
            if (intent != null) {
                parentActivity.startActivityForResult(intent, requestCode);
            }
        }
    }

    public void setUseAlphaAnimations(boolean value) {
        useAlphaAnimations = value;
    }

    public void setBackgroundView(View view) {
        backgroundView = view;
    }

    public void setDrawerLayoutContainer(DrawerLayoutContainer layout) {
        drawerLayoutContainer = layout;
    }

    public DrawerLayoutContainer getDrawerLayoutContainer() {
        return drawerLayoutContainer;
    }

    public void setRemoveActionBarExtraHeight(boolean value) {
        removeActionBarExtraHeight = value;
    }

    public void setTitleOverlayText(String text) {
        titleOverlayText = text;
        for (BaseFragment fragment : fragmentsStack) {
            if (fragment.actionBar != null) {
                fragment.actionBar.setTitleOverlayText(titleOverlayText);
            }
        }
    }
}
