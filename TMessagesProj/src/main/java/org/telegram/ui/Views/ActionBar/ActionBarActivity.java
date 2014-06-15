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
import android.os.Bundle;
import android.os.Handler;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class ActionBarActivity extends Activity {

    private class FrameLayoutAnimationListener extends FrameLayout {
        public FrameLayoutAnimationListener(Context context) {
            super(context);
        }

        @Override
        protected void onAnimationEnd() {
            super.onAnimationEnd();
            ActionBarActivity.this.onAnimationEnd();
        }
    }

    protected ActionBar actionBar;
    private FrameLayoutAnimationListener containerView;
    private FrameLayoutAnimationListener containerViewBack;
    protected FrameLayout contentView;
    private View shadowView;

    private Animation openAnimation;
    private Animation closeAnimation;

    private boolean maybeStartTracking = false;
    protected boolean startedTracking = false;
    private int startedTrackingX;
    protected boolean animationInProgress = false;
    private VelocityTracker velocityTracker = null;
    private boolean beginTrackingSent = false;
    private boolean transitionAnimationInProgress = false;
    private long transitionAnimationStartTime;
    private boolean inActionMode = false;
    private int startedTrackingPointerId;
    private Runnable onCloseAnimationEndRunnable = null;
    private Runnable onOpenAnimationEndRunnable = null;

    private class FrameLayoutTouch extends FrameLayout {
        public FrameLayoutTouch(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return !(!animationInProgress && !checkTransitionAnimation()) || ((ActionBarActivity) getContext()).onTouchEvent(ev);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            ((ActionBarActivity)getContext()).onTouchEvent(null);
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                return ((ActionBarActivity)getContext()).onPreIme() || super.dispatchKeyEventPreIme(event);
            }
            return super.dispatchKeyEventPreIme(event);
        }

        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            return super.onKeyPreIme(keyCode, event);
        }
    }

    public static ArrayList<BaseFragment> fragmentsStack = new ArrayList<BaseFragment>();

    protected void onCreateFinish(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        try {
            openAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_in);
            closeAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_out);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        setTheme(R.style.Theme_TMessages);
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);

        contentView = new FrameLayoutTouch(this);
        setContentView(contentView, new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        containerViewBack = new FrameLayoutAnimationListener(this);
        contentView.addView(containerViewBack);

        containerView = new FrameLayoutAnimationListener(this);
        contentView.addView(containerView);

        shadowView = new FrameLayout(this);
        contentView.addView(shadowView);
        shadowView.setBackgroundResource(R.drawable.shadow);
        ViewGroup.LayoutParams layoutParams = shadowView.getLayoutParams();
        layoutParams.width = Utilities.dp(2);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        shadowView.setLayoutParams(layoutParams);
        shadowView.setVisibility(View.INVISIBLE);

        actionBar = new ActionBar(this);
        contentView.addView(actionBar);
        layoutParams = actionBar.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        actionBar.setLayoutParams(layoutParams);

        for (BaseFragment fragment : fragmentsStack) {
            fragment.setParentActivity(this);
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

    @Override
    protected void onResume() {
        super.onResume();
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

    @Override
    protected void onPause() {
        super.onPause();
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onPause();
        }
    }

    private void onSlideAnimationEnd(boolean backAnimation) {
        containerView.setX(0);
        containerViewBack.setX(0);
        actionBar.stopMoving(backAnimation);
        shadowView.setVisibility(View.INVISIBLE);
        shadowView.setX(-Utilities.dp(2));
        if (!backAnimation) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onPause();
            lastFragment.onFragmentDestroy();
            lastFragment.setParentActivity(null);
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
        Utilities.unlockOrientation(this);
        startedTracking = false;
        animationInProgress = false;
    }

    private void prepareForMoving(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        startedTrackingX = (int) ev.getX();
        shadowView.setVisibility(View.VISIBLE);
        shadowView.setX(-Utilities.dp(2));
        containerViewBack.setVisibility(View.VISIBLE);
        beginTrackingSent = false;

        BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        actionBar.prepareForMoving(lastFragment.actionBarLayer);
        View fragmentView = lastFragment.createView(getLayoutInflater(), null);
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

        Utilities.lockOrientation(this);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if(android.os.Build.VERSION.SDK_INT >= 11 && !checkTransitionAnimation() && !inActionMode && fragmentsStack.size() > 1 && !animationInProgress) {
            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
                startedTrackingPointerId = ev.getPointerId(0);
                maybeStartTracking = true;
                startedTrackingX = (int) ev.getX();
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                int dx = Math.max(0, (int) (ev.getX() - startedTrackingX));
                velocityTracker.addMovement(ev);
                if (maybeStartTracking && !startedTracking && dx >= Utilities.dp(10)) {
                    prepareForMoving(ev);
                } else if (startedTracking) {
                    if (!beginTrackingSent) {
                        if (getCurrentFocus() != null) {
                            Utilities.hideKeyboard(getCurrentFocus());
                        }
                        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                        currentFragment.onBeginSlide();
                        beginTrackingSent = true;
                    }
                    actionBar.moveActionBarByX(dx);
                    containerView.setX(dx);
                    shadowView.setX(dx - Utilities.dp(2));
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
                        animators.add(ObjectAnimator.ofFloat(shadowView, "x", containerView.getMeasuredWidth() - Utilities.dp(2)));
                    } else {
                        distToMove = x;
                        animators.add(ObjectAnimator.ofFloat(containerView, "x", 0));
                        animators.add(ObjectAnimator.ofFloat(shadowView, "x", -Utilities.dp(2)));
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

    @Override
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
            if (fragmentsStack.size() == 1) {
                fragmentsStack.get(0).onFragmentDestroy();
                fragmentsStack.clear();
                onFinish();
                finish();
            } else if (!fragmentsStack.isEmpty()) {
                closeLastFragment(true);
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        for (BaseFragment fragment : fragmentsStack) {
            fragment.onLowMemory();
        }
    }

    public boolean checkTransitionAnimation() {
        if (transitionAnimationInProgress && transitionAnimationStartTime < System.currentTimeMillis() - 400) {
            transitionAnimationInProgress = false;
            onAnimationEnd();
        }
        return transitionAnimationInProgress;
    }

    private void fixLayout() {
        if (contentView != null) {
            ViewTreeObserver obs = contentView.getViewTreeObserver();
            obs.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    needLayout();

                    if (Build.VERSION.SDK_INT < 16) {
                        contentView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        contentView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }
    }

    public void needLayout() {
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        int height = 0;
        if (actionBar.getVisibility() == View.VISIBLE) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                height = Utilities.dp(40);
            } else {
                height = Utilities.dp(48);
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
            fragment.setParentActivity(null);
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
        return presentFragment(fragment, false, false);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return presentFragment(fragment, removeLast, false);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        if (checkTransitionAnimation() || !fragment.onFragmentCreate()) {
            return false;
        }
        if (getCurrentFocus() != null) {
            Utilities.hideKeyboard(getCurrentFocus());
        }
        boolean needAnimation = openAnimation != null && !forceWithoutAnimation && getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);

        final BaseFragment currentFragment = !fragmentsStack.isEmpty() ? fragmentsStack.get(fragmentsStack.size() - 1) : null;

        fragment.setParentActivity(this);
        View fragmentView = fragment.createView(getLayoutInflater(), null);
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
        }

        if (needAnimation) {
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
        } else {
            fragment.onOpenAnimationEnd();
        }
        return true;
    }

    public boolean addFragmentToStack(BaseFragment fragment) {
        if (!fragment.onFragmentCreate()) {
            return false;
        }
        fragment.setParentActivity(this);
        fragmentsStack.add(fragment);
        return true;
    }

    private void closeLastFragmentInternalRemoveOld(BaseFragment fragment) {
        fragment.onPause();
        fragment.onFragmentDestroy();
        fragment.setParentActivity(null);
        fragmentsStack.remove(fragment);
        containerViewBack.setVisibility(View.GONE);
        ViewGroup parent = (ViewGroup)containerView.getParent();
        parent.removeView(containerViewBack);
        parent.addView(containerViewBack, 0);
    }

    public void closeLastFragment(boolean animated) {
        if (fragmentsStack.size() <= 1 || checkTransitionAnimation()) {
            return;
        }
        if (getCurrentFocus() != null) {
            Utilities.hideKeyboard(getCurrentFocus());
        }
        boolean needAnimation = animated && closeAnimation != null && getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);
        final BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);

        FrameLayoutAnimationListener temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setVisibility(View.VISIBLE);

        previousFragment.setParentActivity(this);
        View fragmentView = previousFragment.createView(getLayoutInflater(), null);
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
    }

    public void showLastFragment() {
        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        previousFragment.setParentActivity(this);
        View fragmentView = previousFragment.createView(getLayoutInflater(), null);
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
        fragment.onFragmentDestroy();
        fragment.setParentActivity(null);
        fragmentsStack.remove(fragment);
    }

    public void rebuildAllFragmentViews() {
        for (int a = 0; a < fragmentsStack.size() - 1; a++) {
            fragmentsStack.get(a).setParentActivity(null);
            fragmentsStack.get(a).setParentActivity(this);
        }
    }

    protected void onFinish() {

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
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void onOverlayShow(View view, BaseFragment fragment) {

    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        hideActionBar();
        inActionMode = true;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        showActionBar();
        inActionMode = false;
    }

    public boolean onPreIme() {
        return false;
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

    private void onAnimationEnd() {
        onCloseAnimationEnd(false);
        onOpenAnimationEnd(false);
    }

    @Override
    public void startActivityForResult(final Intent intent, final int requestCode) {
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
                try {
                    ActionBarActivity.super.startActivityForResult(intent, requestCode);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        } else {
            if (intent != null) {
                try {
                    super.startActivityForResult(intent, requestCode);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }
}
