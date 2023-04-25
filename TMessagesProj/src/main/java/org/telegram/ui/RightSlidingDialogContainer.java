package org.telegram.ui;

import static org.telegram.ui.ActionBar.ActionBarLayout.findScrollingChild;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

public abstract class RightSlidingDialogContainer extends FrameLayout {

    BaseFragment currentFragment;
    View currentFragmentView;
    View currentFragmentFullscreenView;
    ActionBar currentActionBarView;

    float openedProgress = 0;
    boolean isOpenned;
    ValueAnimator openAnimator;
    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private int currentAccount = UserConfig.selectedAccount;
    public static long fragmentDialogId;
    boolean isPaused;
    public int fragmentViewPadding;
    private boolean replaceAnimationInProgress;
    INavigationLayout navigationLayout;

    public RightSlidingDialogContainer(@NonNull Context context) {
        super(context);
    }

    public void presentFragment(INavigationLayout navigationLayout, BaseFragment fragment) {
        if (isPaused) {
            return;
        }
        this.navigationLayout = navigationLayout;
        if (fragment.onFragmentCreate()) {
            fragment.setInPreviewMode(true);
            fragment.setParentLayout(navigationLayout);
            View view = fragment.createView(getContext());

            fragment.onResume();
            addView(currentFragmentView = view);
            BaseFragment oldFragment = currentFragment;
            if (fragment instanceof BaseFragmentWithFullscreen) {
                currentFragmentFullscreenView = ((BaseFragmentWithFullscreen) fragment).getFullscreenView();
                addView(currentFragmentFullscreenView);
            }
            currentFragment = fragment;

            fragmentDialogId = 0;
            if (currentFragment instanceof TopicsFragment) {
                fragmentDialogId = -((TopicsFragment) currentFragment).chatId;
            }
            if (fragment.getActionBar() != null) {
                addView(currentActionBarView = fragment.getActionBar());
                currentActionBarView.listenToBackgroundUpdate(this::invalidate);
            }


            if (oldFragment != null) {
                animateReplace(oldFragment);
            } else if (!isOpenned) {
                isOpenned = true;
                if (!SharedConfig.animationsEnabled()) {
                    openAnimationStarted(true);
                    fragment.onTransitionAnimationStart(true, false);
                    fragment.onTransitionAnimationEnd(true, false);
                    openedProgress = 1f;
                    updateOpenAnimationProgress();
                    openAnimationFinished();
                    return;
                }
                notificationsLocker.lock();
                openAnimator = ValueAnimator.ofFloat(0, 1f);
                openedProgress = 0;
                openAnimationStarted(true);
                updateOpenAnimationProgress();
                fragment.onTransitionAnimationStart(true, false);
                openAnimator.addUpdateListener(animation -> {
                    openedProgress = (float) animation.getAnimatedValue();
                    updateOpenAnimationProgress();
                });
                openAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (openAnimator == null) {
                            return;
                        }
                        openAnimator = null;
                        notificationsLocker.unlock();
                        fragment.onTransitionAnimationEnd(true, false);
                        openedProgress = 1f;
                        updateOpenAnimationProgress();
                        openAnimationFinished();
                    }
                });
                openAnimator.setDuration(250);
                openAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                openAnimator.setStartDelay(SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH ? 50 : 150);
                openAnimator.start();
            }

            fragment.setPreviewDelegate(() -> finishPreview());
        }
    }

    SpringAnimation replaceAnimation;
    float replaceProgress;
    BaseFragment replacingFragment;

    private void animateReplace(BaseFragment oldFragment) {
        BaseFragment currentFragment = this.currentFragment;

        if (!SharedConfig.animationsEnabled()) {
            currentFragment.onTransitionAnimationStart(true, false);
            currentFragment.onTransitionAnimationEnd(true, false);
            setReplaceProgress(oldFragment, currentFragment, 1f);
            replaceAnimationInProgress = false;
            replacingFragment = null;
            oldFragment.onPause();
            oldFragment.onFragmentDestroy();
            removeView(oldFragment.getFragmentView());
            removeView(oldFragment.getActionBar());
            notificationsLocker.unlock();
            return;
        }
        if (replaceAnimation != null) {
            replaceAnimation.cancel();
        }
        currentFragment.onTransitionAnimationStart(true, false);
        replacingFragment = oldFragment;
        replaceAnimationInProgress = true;
        notificationsLocker.lock();
        replaceAnimation = new SpringAnimation(new FloatValueHolder(0f));
        replaceAnimation.setSpring(new SpringForce(1000f)
                .setStiffness(400f)
                .setDampingRatio(1f));

        setReplaceProgress(oldFragment, currentFragment, 0f);
        replaceAnimation.addUpdateListener((animation, value, velocity) -> {
            replaceProgress = value / 1000f;
            invalidate();
        });
        replaceAnimation.addEndListener((animation, canceled, value, velocity) -> {
            if (replaceAnimation == null) {
                return;
            }
            replaceAnimation = null;
            currentFragment.onTransitionAnimationEnd(true, false);
            setReplaceProgress(oldFragment, currentFragment, 1f);
            replaceAnimationInProgress = false;
            replacingFragment = null;
            oldFragment.onPause();
            oldFragment.onFragmentDestroy();
            removeView(oldFragment.getFragmentView());
            removeView(oldFragment.getActionBar());
            notificationsLocker.unlock();
        });
        replaceAnimation.start();
    }

    private void setReplaceProgress(BaseFragment oldFragment, BaseFragment currentFragment, float p) {
        if (oldFragment == null && currentFragment == null) {
            return;
        }
        int width;
        if (oldFragment != null) {
            width = oldFragment.getFragmentView().getMeasuredWidth();
        } else {
            width = currentFragment.getFragmentView().getMeasuredWidth();
        }
        if (oldFragment != null) {
            if (oldFragment.getFragmentView() != null) {
                oldFragment.getFragmentView().setAlpha(1f - p);
                oldFragment.getFragmentView().setTranslationX(width * 0.6f * p);
            }
            oldFragment.setPreviewOpenedProgress(1f - p);
        }

        if (currentFragment != null) {
            if (currentFragment.getFragmentView() != null) {
                currentFragment.getFragmentView().setAlpha(1f);
                currentFragment.getFragmentView().setTranslationX(width * (1f - p));
            }
            currentFragment.setPreviewReplaceProgress(p);
        }
    }

    protected void updateOpenAnimationProgress() {
        if (replaceAnimationInProgress || !hasFragment()) {
            return;
        }
        setOpenProgress(openedProgress);
        if (currentFragmentView != null) {
            currentFragmentView.setTranslationX((getMeasuredWidth() - AndroidUtilities.dp(getRightPaddingSize())) * (1f - openedProgress));
        }
        if (currentActionBarView != null) {
            currentActionBarView.setTranslationX(AndroidUtilities.dp(48) * (1f - openedProgress));
        }
        if (currentFragment != null) {
            currentFragment.setPreviewOpenedProgress(openedProgress);
        }
        invalidate();
    }

    int lastSize;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int stausBarHeight = getOccupyStatusbar() ?  AndroidUtilities.statusBarHeight : 0;
        if (currentFragmentView != null) {
            LayoutParams layoutParams = (LayoutParams) currentFragmentView.getLayoutParams();
            layoutParams.leftMargin = AndroidUtilities.dp(getRightPaddingSize());
            layoutParams.topMargin = ActionBar.getCurrentActionBarHeight() + stausBarHeight + fragmentViewPadding;
        }
        if (currentActionBarView != null) {
            LayoutParams layoutParams = (LayoutParams) currentActionBarView.getLayoutParams();
            layoutParams.topMargin = stausBarHeight;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = getMeasuredHeight() + getMeasuredWidth() << 16;
        if (lastSize != size) {
            lastSize = size;
            updateOpenAnimationProgress();
        }
    }

    void setOpenProgress(float progress) {

    }

    public boolean hasFragment() {
        return currentFragment != null;
    }

    public void finishPreview() {
        if (!isOpenned) {
            return;
        }
        openAnimationStarted(false);
        finishPreviewInernal();
    }

    public void finishPreviewInernal() {
        isOpenned = false;
        if (!SharedConfig.animationsEnabled()) {
            openedProgress = 0;
            updateOpenAnimationProgress();
            if (currentFragment != null) {
                currentFragment.onPause();
                currentFragment.onFragmentDestroy();
                removeAllViews();
                currentFragment = null;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
            }

            openAnimationFinished();

            return;
        }
        notificationsLocker.lock();
        openAnimator = ValueAnimator.ofFloat(openedProgress, 0);
        openAnimator.addUpdateListener(animation -> {
            openedProgress = (float) animation.getAnimatedValue();
            updateOpenAnimationProgress();
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (openAnimator == null) {
                    return;
                }
                openAnimator = null;
                openedProgress = 0;
                updateOpenAnimationProgress();
                notificationsLocker.unlock();
                if (currentFragment != null) {
                    currentFragment.onPause();
                    currentFragment.onFragmentDestroy();
                    removeAllViews();
                    currentFragment = null;
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
                }

                openAnimationFinished();

            }
        });
        openAnimator.setDuration(250);
        openAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        openAnimator.start();
    }

    public interface BaseFragmentWithFullscreen {
        View getFullscreenView();
    }

    private int startedTrackingPointerId;
    private boolean maybeStartTracking;
    protected boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private VelocityTracker velocityTracker;
    public boolean enabled = true;
    float swipeBackX;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (navigationLayout != null && navigationLayout.isInPreviewMode()) {
            return false;
        }
        if (hasFragment() && enabled) {
            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
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
                if (maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
//                    BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (findScrollingChild(this, ev.getX(), ev.getY()) == null) {
                        prepareForMoving(ev);
                    } else {
                        maybeStartTracking = false;
                    }
                } else if (startedTracking) {
//                    if (!beginTrackingSent) {
//                        if (parentActivity.getCurrentFocus() != null) {
//                            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
//                        }
//                        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
//                        currentFragment.onBeginSlide();
//                        beginTrackingSent = true;
//                    }
//                    containerView.setTranslationX(dx);
                    swipeBackX = dx;
                    openedProgress = Utilities.clamp(1f - swipeBackX / getMeasuredWidth(), 1f, 0);
                    updateOpenAnimationProgress();
//                    setInnerTranslationX(dx);
                }
            } else if (ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.computeCurrentVelocity(1000);

                if (startedTracking) {
                    float x = swipeBackX;
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    final boolean backAnimation = x < getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);

                    if (!backAnimation) {
                        finishPreviewInernal();
                    } else {
                        openAnimator = ValueAnimator.ofFloat(openedProgress, 1f);
                        openAnimator.addUpdateListener(animation -> {
                            openedProgress = (float) animation.getAnimatedValue();
                            updateOpenAnimationProgress();
                        });
                        openAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (openAnimator == null) {
                                    return;
                                }
                                openAnimator = null;
                                openAnimationFinished();
                            }
                        });
                        openAnimator.setDuration(250);
                        openAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        openAnimator.start();
                    }
                }
                maybeStartTracking = false;
                startedTracking = false;

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

    private void prepareForMoving(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        startedTrackingX = (int) ev.getX();
        openAnimationStarted(false);
    }

    float currentTop;
    public void setCurrentTop(int top) {
        currentTop = top;
        if (currentFragmentView != null) {
            currentFragmentView.setTranslationY(top - currentFragmentView.getTop() + fragmentViewPadding);
        }
        if (currentFragmentFullscreenView != null) {
            currentFragmentFullscreenView.setTranslationY(top - currentFragmentFullscreenView.getTop());
        }
    }

    public void openAnimationFinished() {

    }

    abstract boolean getOccupyStatusbar();

    public void openAnimationStarted(boolean open) {

    }

    public long getCurrentFragmetDialogId() {
        return fragmentDialogId;
    }

    public static int getRightPaddingSize() {
        return SharedConfig.useThreeLinesLayout ? 74 : 76;
    }

    public View getFragmentView() {
        return currentFragmentView;
    }

    public void onPause() {
        isPaused = true;
        if (currentFragment != null) {
            currentFragment.onPause();
        }
    }

    public void onResume() {
        isPaused = false;
        if (currentFragment != null) {
            currentFragment.onResume();
        }
    }

    public BaseFragment getFragment() {
        return currentFragment;
    }

    private Paint actionModePaint;

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == currentActionBarView && currentActionBarView.getActionMode() != null && currentActionBarView.getActionMode().getAlpha() == 1f) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (replaceAnimationInProgress) {
            setReplaceProgress(replacingFragment, currentFragment, replaceProgress);
            invalidate();
        }
        super.dispatchDraw(canvas);
        float alpha = openedProgress * Math.max(currentActionBarView == null || currentActionBarView.getActionMode() == null ? 0 : currentActionBarView.getActionMode().getAlpha(), currentActionBarView == null ? 0 : currentActionBarView.searchFieldVisibleAlpha);
        if (currentFragment != null && currentActionBarView != null && alpha > 0) {
            if (actionModePaint == null) {
                actionModePaint = new Paint();
            }
            actionModePaint.setColor(Theme.getColor(Theme.key_actionBarActionModeDefault));
            if (alpha == 1) {
                canvas.save();
            } else {
                canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), currentTop, (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
            }
            canvas.drawRect(0, 0, getMeasuredWidth(), currentTop, actionModePaint);

            canvas.translate(currentActionBarView.getX(), currentActionBarView.getY());

            canvas.save();
            canvas.translate(currentActionBarView.getBackButton().getX(),  currentActionBarView.getBackButton().getY());
            currentActionBarView.getBackButton().draw(canvas);
            canvas.restore();

            if (currentActionBarView.getActionMode() != null) {
                if (alpha != openedProgress * currentActionBarView.getActionMode().getAlpha()) {
                    currentActionBarView.draw(canvas);
                    canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), currentTop, (int) (255 * currentActionBarView.getActionMode().getAlpha()), Canvas.ALL_SAVE_FLAG);
                    currentActionBarView.getActionMode().draw(canvas);
                    canvas.restore();
                } else {
                    currentActionBarView.getActionMode().draw(canvas);
                }
            } else {
                currentActionBarView.draw(canvas);
            }
            canvas.restore();
            invalidate();
        }
    }

    public boolean isActionModeShowed() {
        return currentFragment != null && currentActionBarView != null && currentActionBarView.isActionModeShowed();
    }

    public void setFragmentViewPadding(int fragmentViewPadding) {
        this.fragmentViewPadding = fragmentViewPadding;
    }

    public void setTransitionPaddingBottom(int transitionPadding) {
        if (currentFragment instanceof TopicsFragment) {
            ((TopicsFragment)currentFragment).setTransitionPadding(transitionPadding);
        }
    }

    @Override
    public void removeViewInLayout(View view) {
        super.removeViewInLayout(view);
        if (view == currentFragmentView) {
            finishPreview();
        }
    }

    @Override
    public void removeView(View view) {
        super.removeView(view);
        if (view == currentFragmentView) {
            finishPreview();
        }
    }
}
