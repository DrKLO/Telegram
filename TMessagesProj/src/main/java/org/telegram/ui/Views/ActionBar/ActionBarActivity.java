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
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class ActionBarActivity extends Activity {

    protected ActionBar actionBar;
    private FrameLayout containerView;
    private FrameLayout containerViewBack;
    private FrameLayout contentView;
    private View shadowView;

    private Animation openAnimation;
    private Animation closeAnimation;

    private boolean startedTracking = false;
    private int startedTrackingX;
    private int prevOrientation = -10;
    private boolean animationInProgress = false;
    private VelocityTracker velocityTracker = null;
    private boolean beginTrackingSent = false;
    private boolean transitionAnimationInProgress = false;
    private long transitionAnimationStartTime;
    private boolean inActionMode = false;

    public static ArrayList<BaseFragment> fragmentsStack = new ArrayList<BaseFragment>();

    protected void onCreateFinish(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            openAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_in);
            closeAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_out);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        setTheme(R.style.Theme_TMessages);
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);

        contentView = new FrameLayout(this);
        setContentView(contentView, new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        containerViewBack = new FrameLayout(this);
        contentView.addView(containerViewBack);

        containerView = new FrameLayout(this);
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
        actionBar.setBackgroundResource(R.color.header);
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
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onResume();

            actionBar.setCurrentActionBarLayer(lastFragment.actionBarLayer);
            onShowFragment();
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

            FrameLayout temp = containerView;
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
        try {
            if (prevOrientation != -10) {
                setRequestedOrientation(prevOrientation);
                prevOrientation = -10;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        startedTracking = false;
        animationInProgress = false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if(android.os.Build.VERSION.SDK_INT >= 11 && !checkTransitionAnimation() && !inActionMode) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && ev.getX() <= Utilities.dp(6) && fragmentsStack.size() > 1) {
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

                try {
                    prevOrientation = getRequestedOrientation();
                    WindowManager manager = (WindowManager)getSystemService(Activity.WINDOW_SERVICE);
                    if (manager != null && manager.getDefaultDisplay() != null) {
                        int rotation = manager.getDefaultDisplay().getRotation();
                        if (rotation == Surface.ROTATION_270) {
                            if (Build.VERSION.SDK_INT >= 9) {
                                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                            } else {
                                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            }
                        } else if (rotation == Surface.ROTATION_90) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        } else if (rotation == Surface.ROTATION_0) {
                            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        } else {
                            if (Build.VERSION.SDK_INT >= 9) {
                                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
            } else if (startedTracking && !animationInProgress) {
                if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!beginTrackingSent) {
                        if (getCurrentFocus() != null) {
                            Utilities.hideKeyboard(getCurrentFocus());
                        }
                        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                        currentFragment.onBeginSlide();
                        beginTrackingSent = true;
                    }
                    velocityTracker.addMovement(ev);
                    int dx = Math.max(0, (int) (ev.getX() - startedTrackingX));
                    actionBar.moveActionBarByX(dx);
                    containerView.setX(dx);
                    shadowView.setX(dx - Utilities.dp(2));
                } else if (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) {
                    velocityTracker.computeCurrentVelocity(1000);
                    float x = containerView.getX();
                    ArrayList<Animator> animators = new ArrayList<Animator>();
                    final boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && velocityTracker.getXVelocity() < 6000;
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
                    animatorSet.setDuration((int)(200.0f / containerView.getMeasuredWidth() * distToMove));
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
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
            return startedTracking || super.dispatchTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
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
                closeLastFragment();
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

    private void presentFragmentInternalRemoveOld(boolean removeLast, BaseFragment fragment) {
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
    }

    public boolean presentFragment(BaseFragment fragment) {
        return presentFragment(fragment, false, false);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return presentFragment(fragment, removeLast, false);
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation) {
        if (!fragment.onFragmentCreate() || checkTransitionAnimation()) {
            return false;
        }
        if (getCurrentFocus() != null) {
            Utilities.hideKeyboard(getCurrentFocus());
        }
        boolean needAnimation = openAnimation != null && !forceWithoutAnimation && getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);

        final BaseFragment currentFragment = !fragmentsStack.isEmpty() ? fragmentsStack.get(fragmentsStack.size() - 1) : null;
        if (!needAnimation) {
            presentFragmentInternalRemoveOld(removeLast, currentFragment);
        }

        fragment.setParentActivity(this);
        View fragmentView = fragment.createView(getLayoutInflater(), null);
        containerView.addView(fragmentView);
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
        onShowFragment();
        if (needAnimation) {
            transitionAnimationStartTime = System.currentTimeMillis();
            transitionAnimationInProgress = true;
            openAnimation.reset();
            openAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    transitionAnimationInProgress = false;
                    transitionAnimationStartTime = 0;
                    fragment.onOpenAnimationEnd();
                    presentFragmentInternalRemoveOld(removeLast, currentFragment);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            fragmentView.startAnimation(openAnimation);
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
    }

    public void closeLastFragment() {
        if (fragmentsStack.size() <= 1 || checkTransitionAnimation()) {
            return;
        }
        if (getCurrentFocus() != null) {
            Utilities.hideKeyboard(getCurrentFocus());
        }
        boolean needAnimation = openAnimation != null && getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("view_animations", true);
        final BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        if (!needAnimation) {
            closeLastFragmentInternalRemoveOld(currentFragment);
        }

        previousFragment.setParentActivity(this);
        View fragmentView = previousFragment.createView(getLayoutInflater(), null);
        containerView.addView(fragmentView, 0);
        ViewGroup.LayoutParams layoutParams = fragmentView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        fragmentView.setLayoutParams(layoutParams);
        previousFragment.onResume();
        actionBar.setCurrentActionBarLayer(previousFragment.actionBarLayer);
        if (fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(0xffffffff);
        }
        onShowFragment();
        if (needAnimation) {
            transitionAnimationStartTime = System.currentTimeMillis();
            transitionAnimationInProgress = true;
            closeAnimation.reset();
            closeAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    transitionAnimationInProgress = false;
                    transitionAnimationStartTime = 0;
                    closeLastFragmentInternalRemoveOld(currentFragment);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            currentFragment.fragmentView.startAnimation(closeAnimation);
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
        onShowFragment();
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

    protected void onShowFragment() {

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
}
