/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.util.ArrayList;

public class ActionBarLayout extends FrameLayout {

    private class FrameLayoutAnimationListener extends FrameLayout {
        public FrameLayoutAnimationListener(Context context) {
            super(context);
        }

        @Override
        protected void onAnimationEnd() {
            super.onAnimationEnd();
            ActionBarLayout.this.onAnimationEndCheck();
        }
    }

    public static interface ActionBarLayoutDelegate {
        public abstract boolean onPreIme();
        public abstract void onOverlayShow(View view, BaseFragment fragment);
        public abstract boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ActionBarLayout layout);
        public abstract boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout);
        public abstract boolean needCloseLastFragment(ActionBarLayout layout);
        public abstract void onRebuildAllFragments(ActionBarLayout layout);
    }

    protected ActionBar actionBar;
    private FrameLayoutAnimationListener containerView;
    private FrameLayoutAnimationListener containerViewBack;
    private View shadowView;

    private Animation openAnimation;
    private Animation closeAnimation;
    private Animation alphaOpenAnimation;
    private Animation alphaOpenAnimation2;
    private Animation alphaCloseAnimation;
    private Animation alphaCloseAnimation2;

    private boolean maybeStartTracking = false;
    protected boolean startedTracking = false;
    private int startedTrackingX;
    private int startedTrackingY;
    protected boolean animationInProgress = false;
    private VelocityTracker velocityTracker = null;
    private boolean beginTrackingSent = false;
    private boolean transitionAnimationInProgress = false;
    private long transitionAnimationStartTime;
    private boolean inActionMode = false;
    private int startedTrackingPointerId;
    private Runnable onCloseAnimationEndRunnable = null;
    private Runnable onOpenAnimationEndRunnable = null;
    private boolean useAlphaAnimations = false;
    private View backgroundView;

    private ActionBarLayoutDelegate delegate = null;
    protected Activity parentActivity = null;

    public ArrayList<BaseFragment> fragmentsStack = null;

    public ActionBarLayout(Context context) {
        super(context);
        parentActivity = (Activity)context;
        try {
            openAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_in);
            closeAnimation = AnimationUtils.loadAnimation(context, R.anim.scale_out);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void init(ArrayList<BaseFragment> stack) {
        fragmentsStack = stack;
        containerViewBack = new FrameLayoutAnimationListener(parentActivity);
        addView(containerViewBack);

        containerView = new FrameLayoutAnimationListener(parentActivity);
        addView(containerView);

        shadowView = new FrameLayout(parentActivity);
        addView(shadowView);
        shadowView.setBackgroundResource(R.drawable.shadow);
        ViewGroup.LayoutParams layoutParams = shadowView.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(2);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        shadowView.setLayoutParams(layoutParams);
        shadowView.setVisibility(View.INVISIBLE);

        actionBar = new ActionBar(parentActivity);
        addView(actionBar);
        layoutParams = actionBar.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);

        for (BaseFragment fragment : fragmentsStack) {
            fragment.setParentLayout(this);
        }

        needLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onConfigurationChanged(newConfig);
        }
    }

    public void onResume() {
        fixLayout();
        if (transitionAnimationInProgress) {
            if (onCloseAnimationEndRunnable != null) {
                closeAnimation.cancel();
                onCloseAnimationEnd(false);
            } else if (onOpenAnimationEndRunnable != null) {
                openAnimation.cancel();
                onOpenAnimationEnd(false);
            }
        }
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onResume();

            actionBar.setCurrentActionBarLayer(lastFragment.actionBarLayer);
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
    protected void onAnimationEnd() {
        super.onAnimationEnd();
        onAnimationEndCheck();
    }

    public void setDelegate(ActionBarLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    private void onSlideAnimationEnd(boolean backAnimation) {
        containerView.setTranslationX(0);
        containerViewBack.setTranslationX(0);
        actionBar.stopMoving(backAnimation);
        shadowView.setVisibility(View.INVISIBLE);
        shadowView.setTranslationX(-AndroidUtilities.dp(2));
        if (!backAnimation) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onPause();
            lastFragment.onFragmentDestroy();
            lastFragment.setParentLayout(null);
            fragmentsStack.remove(fragmentsStack.size() - 1);

            FrameLayoutAnimationListener temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;
            ViewGroup parent = (ViewGroup)containerView.getParent();
            parent.removeView(containerView);
            parent.addView(containerView, 1);
            lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
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
        }
        containerViewBack.setVisibility(View.GONE);
        AndroidUtilities.unlockOrientation(parentActivity);
        startedTracking = false;
        animationInProgress = false;
    }

    private void prepareForMoving(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        startedTrackingX = (int) ev.getX();
        shadowView.setVisibility(View.VISIBLE);
        shadowView.setTranslationX(-AndroidUtilities.dp(2));
        containerViewBack.setVisibility(View.VISIBLE);
        beginTrackingSent = false;

        BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        actionBar.prepareForMoving(lastFragment.actionBarLayer);
        View fragmentView = lastFragment.createView(parentActivity.getLayoutInflater(), null);
        ViewGroup parentView = (ViewGroup)fragmentView.getParent();
        if (parentView != null) {
            parentView.removeView(fragmentView);
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

        AndroidUtilities.lockOrientation(parentActivity);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if(android.os.Build.VERSION.SDK_INT >= 11 && !checkTransitionAnimation() && !inActionMode && fragmentsStack.size() > 1 && !animationInProgress) {
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
                int dy = Math.abs((int)ev.getY() - startedTrackingY);
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
                    actionBar.moveActionBarByX(dx);
                    containerView.setTranslationX(dx);
                    shadowView.setTranslationX(dx - AndroidUtilities.dp(2));
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
                    }
                }
                if (startedTracking) {
                    float x = containerView.getX();
                    ArrayList<Animator> animators = new ArrayList<Animator>();
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    final boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
                    float distToMove = 0;
                    if (!backAnimation) {
                        distToMove = containerView.getMeasuredWidth() - x;
                        animators.add(ObjectAnimator.ofFloat(containerView, "x", containerView.getMeasuredWidth()));
                        animators.add(ObjectAnimator.ofFloat(shadowView, "x", containerView.getMeasuredWidth() - AndroidUtilities.dp(2)));
                    } else {
                        distToMove = x;
                        animators.add(ObjectAnimator.ofFloat(containerView, "x", 0));
                        animators.add(ObjectAnimator.ofFloat(shadowView, "x", -AndroidUtilities.dp(2)));
                    }
                    actionBar.setupAnimations(animators, backAnimation);

                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(animators);
                    animatorSet.setDuration(Math.max((int) (200.0f / containerView.getMeasuredWidth() * distToMove), 50));
                    animatorSet.start();
                    animationInProgress = true;
                    animatorSet.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            onSlideAnimationEnd(backAnimation);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {
                            onSlideAnimationEnd(backAnimation);
                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
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
            return startedTracking;
        }
        return false;
    }

    public ActionBar getActionBar() {
        return actionBar;
    }

    public void onBackPressed() {
        if (startedTracking || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
            return;
        }
        if (actionBar.currentLayer != null && actionBar.currentLayer.isSearchFieldVisible) {
            actionBar.currentLayer.closeSearchField();
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

    private void onAnimationEndCheck() {
        onCloseAnimationEnd(false);
        onOpenAnimationEnd(false);
    }

    public boolean checkTransitionAnimation() {
        if (transitionAnimationInProgress && transitionAnimationStartTime < System.currentTimeMillis() - 400) {
            transitionAnimationInProgress = false;
            onAnimationEndCheck();
        }
        return transitionAnimationInProgress;
    }

    private void fixLayout() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                needLayout();

                if (Build.VERSION.SDK_INT < 16) {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
    }

    public void needLayout() {
        WindowManager manager = (WindowManager)parentActivity.getSystemService(Context.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        int height = 0;
        if (actionBar.getVisibility() == View.VISIBLE) {
            if (!AndroidUtilities.isTablet() && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                height = AndroidUtilities.dp(40);
            } else {
                height = AndroidUtilities.dp(48);
            }
        }

        if (containerView != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerView.getLayoutParams();
            if (layoutParams.topMargin != height) {
                layoutParams.setMargins(0, height, 0, 0);
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                containerView.setLayoutParams(layoutParams);
            }
        }
        if (containerViewBack != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerViewBack.getLayoutParams();
            if (layoutParams.topMargin != height) {
                layoutParams.setMargins(0, height, 0, 0);
                layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                containerViewBack.setLayoutParams(layoutParams);
            }
        }
    }

    public ActionBar getInternalActionBar() {
        return actionBar;
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
        boolean needAnimation = openAnimation != null && !forceWithoutAnimation && parentActivity.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);
        if (useAlphaAnimations && fragmentsStack.size() == 0 && alphaOpenAnimation == null) {
            needAnimation = false;
        }

        final BaseFragment currentFragment = !fragmentsStack.isEmpty() ? fragmentsStack.get(fragmentsStack.size() - 1) : null;

        fragment.setParentLayout(this);
        View fragmentView = fragment.createView(parentActivity.getLayoutInflater(), null);
        containerViewBack.addView(fragmentView);
        ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        fragmentView.setLayoutParams(layoutParams);
        fragmentsStack.add(fragment);
        fragment.onResume();
        actionBar.setCurrentActionBarLayer(fragment.actionBarLayer);
        if (fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(0xffffffff);
        }

        FrameLayoutAnimationListener temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setVisibility(View.VISIBLE);
        ViewGroup parent = (ViewGroup)containerView.getParent();
        parent.removeView(containerView);
        parent.addView(containerView, 1);

        if (!needAnimation) {
            presentFragmentInternalRemoveOld(removeLast, currentFragment);
            if (backgroundView != null) {
                backgroundView.setVisibility(VISIBLE);
            }
        }

        if (needAnimation) {
            if (useAlphaAnimations && fragmentsStack.size() == 1) {
                presentFragmentInternalRemoveOld(removeLast, currentFragment);
                startAnimation(alphaOpenAnimation);
                if (backgroundView != null) {
                    backgroundView.setVisibility(VISIBLE);
                    backgroundView.startAnimation(alphaOpenAnimation2);
                }
            } else {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                onOpenAnimationEndRunnable = new Runnable() {
                    @Override
                    public void run() {
                        presentFragmentInternalRemoveOld(removeLast, currentFragment);
                        fragment.onOpenAnimationEnd();
                    }
                };
                openAnimation.reset();
                containerView.startAnimation(openAnimation);
            }
        } else {
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
        ViewGroup parent = (ViewGroup)containerView.getParent();
        parent.removeView(containerViewBack);
        parent.addView(containerViewBack, 0);
    }

    public void closeLastFragment(boolean animated) {
        if (delegate != null && !delegate.needCloseLastFragment(this) || checkTransitionAnimation()) {
            return;
        }
        if (parentActivity.getCurrentFocus() != null) {
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
        }
        boolean needAnimation = animated && closeAnimation != null && parentActivity.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);
        if (useAlphaAnimations && fragmentsStack.size() == 1 && alphaCloseAnimation == null) {
            needAnimation = false;
        }
        final BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        BaseFragment previousFragment = null;
        if (fragmentsStack.size() > 1) {
            previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        }

        if (previousFragment != null) {
            FrameLayoutAnimationListener temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;
            containerView.setVisibility(View.VISIBLE);

            previousFragment.setParentLayout(this);
            View fragmentView = previousFragment.createView(parentActivity.getLayoutInflater(), null);
            containerView.addView(fragmentView);
            ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            fragmentView.setLayoutParams(layoutParams);
            previousFragment.onResume();
            actionBar.setCurrentActionBarLayer(previousFragment.actionBarLayer);
            if (fragmentView.getBackground() == null) {
                fragmentView.setBackgroundColor(0xffffffff);
            }

            if (!needAnimation) {
                closeLastFragmentInternalRemoveOld(currentFragment);
            }

            if (needAnimation) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                closeAnimation.reset();
                onCloseAnimationEndRunnable = new Runnable() {
                    @Override
                    public void run() {
                        closeLastFragmentInternalRemoveOld(currentFragment);
                    }
                };
                containerViewBack.startAnimation(closeAnimation);
            }
        } else {
            if (needAnimation && useAlphaAnimations) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                alphaCloseAnimation.reset();
                alphaCloseAnimation2.reset();
                startAnimation(alphaCloseAnimation);
                if (backgroundView != null) {
                    backgroundView.startAnimation(alphaCloseAnimation2);
                }
                onCloseAnimationEndRunnable = new Runnable() {
                    @Override
                    public void run() {
                        removeFragmentFromStack(currentFragment);
                        setVisibility(GONE);
                        if (backgroundView != null) {
                            backgroundView.setVisibility(GONE);
                        }
                    }
                };
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
        containerView.addView(fragmentView);
        ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        fragmentView.setLayoutParams(layoutParams);
        previousFragment.onResume();
        actionBar.setCurrentActionBarLayer(previousFragment.actionBarLayer);
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

    public void showActionBar() {
        actionBar.setVisibility(View.VISIBLE);
        needLayout();
    }

    public void hideActionBar() {
        actionBar.setVisibility(View.GONE);
        needLayout();
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !checkTransitionAnimation() && !startedTracking) {
            actionBar.onMenuButtonPressed();
        }
        return super.onKeyUp(keyCode, event);
    }

    protected void onOverlayShow(View view, BaseFragment fragment) {
        if (delegate != null) {
            delegate.onOverlayShow(view, fragment);
        }
    }

    public void onActionModeStarted(ActionMode mode) {
        hideActionBar();
        inActionMode = true;
    }

    public void onActionModeFinished(ActionMode mode) {
        showActionBar();
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
            if (onCloseAnimationEndRunnable != null) {
                closeAnimation.cancel();
                onCloseAnimationEnd(false);
            } else if (onOpenAnimationEndRunnable != null) {
                openAnimation.cancel();
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
        if (useAlphaAnimations) {
            alphaOpenAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.icon_anim_fade_in);
            alphaOpenAnimation2 = AnimationUtils.loadAnimation(getContext(), R.anim.icon_anim_fade_in);
            alphaCloseAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.icon_anim_fade_out);
            alphaCloseAnimation2 = AnimationUtils.loadAnimation(getContext(), R.anim.icon_anim_fade_out);
        }
    }

    public void setBackgroundView(View view) {
        backgroundView = view;
    }
}
