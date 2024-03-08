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
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class BottomSheet extends Dialog {
    private final static boolean AVOID_SYSTEM_CUTOUT_FULLSCREEN = false;

    protected int currentAccount = UserConfig.selectedAccount;
    protected ViewGroup containerView;
    protected ContainerView container;
    protected boolean keyboardVisible;
    protected int keyboardHeight;
    private WindowInsets lastInsets;
    public boolean drawNavigationBar;
    public boolean drawDoubleNavigationBar;
    public boolean scrollNavBar;
    public boolean occupyNavigationBar;
    protected boolean waitingKeyboard;

    protected boolean useSmoothKeyboard;

    protected Runnable startAnimationRunnable;
    private int layoutCount;

    private boolean dismissed;
    private int tag;

    private boolean allowDrawContent = true;

    protected boolean useHardwareLayer = true;

    private DialogInterface.OnClickListener onClickListener;

    private CharSequence[] items;
    private int[] itemIcons;
    private View customView;
    private CharSequence title;
    private boolean bigTitle;
    private boolean multipleLinesTitle;
    private int bottomInset;
    private int leftInset;
    private int rightInset;
    protected boolean fullWidth;
    protected boolean isFullscreen;
    private boolean fullHeight;
    private int cellType;
    private Integer selectedPos;
    protected ColorDrawable backDrawable = new ColorDrawable(0xff000000) {
        @Override
        public void setAlpha(int alpha) {
            super.setAlpha(alpha);
            container.invalidate();
        }
    };

    protected boolean useLightStatusBar = true;
    protected boolean useLightNavBar;

    protected int behindKeyboardColorKey = Theme.key_dialogBackground;
    protected int behindKeyboardColor;

    private boolean canDismissWithSwipe = true;
    private boolean canDismissWithTouchOutside = true;

    private boolean allowCustomAnimation = true;
    private boolean showWithoutAnimation;
    boolean showing;

    private int statusBarHeight = AndroidUtilities.statusBarHeight;

    protected boolean calcMandatoryInsets;

    private int touchSlop;
    private boolean useFastDismiss;
    protected Interpolator openInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;

    private TextView titleView;

    private boolean focusable;

    protected boolean dimBehind = true;
    protected int dimBehindAlpha = 51;

    protected boolean allowNestedScroll = true;

    protected Drawable shadowDrawable;
    protected int backgroundPaddingTop;
    protected int backgroundPaddingLeft;

    private boolean applyTopPadding = true;
    private boolean applyBottomPadding = true;

    private ArrayList<BottomSheetCell> itemViews = new ArrayList<>();

    private Runnable dismissRunnable = this::dismiss;

    protected BottomSheetDelegateInterface delegate;

    protected AnimatorSet currentSheetAnimation;
    protected int currentSheetAnimationType;
    protected ValueAnimator navigationBarAnimation;
    protected float navigationBarAlpha = 0;

    protected View nestedScrollChild;
    private boolean disableScroll;
    private float currentPanTranslationY;

    protected int navBarColorKey = Theme.key_windowBackgroundGray;
    protected int navBarColor;

    private OnDismissListener onHideListener;
    protected Theme.ResourcesProvider resourcesProvider;
    protected boolean isPortrait;
    public boolean pauseAllHeavyOperations = true;
    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    protected int playingImagesLayerNum;
    protected int openedLayerNum;
    private boolean skipDismissAnimation;

    public void setDisableScroll(boolean b) {
        disableScroll = b;
    }

    private ValueAnimator keyboardContentAnimator;
    public boolean smoothKeyboardAnimationEnabled;
    private boolean openNoDelay;

    private float hideSystemVerticalInsetsProgress;
    public boolean useBackgroundTopPadding = true;
    protected int customViewGravity = Gravity.LEFT | Gravity.TOP;
    private boolean transitionFromRight;

    public void transitionFromRight(boolean transitionFromRight) {
        this.transitionFromRight = transitionFromRight;
    }

    public void onOpenAnimationEnd() {

    }

    public class ContainerView extends FrameLayout implements NestedScrollingParent {

        private VelocityTracker velocityTracker = null;
        private int startedTrackingX;
        private int startedTrackingY;
        private int startedTrackingPointerId = -1;
        private boolean maybeStartTracking = false;
        private boolean startedTracking = false;
        private AnimatorSet currentAnimation = null;
        private NestedScrollingParentHelper nestedScrollingParentHelper;
        private Rect rect = new Rect();
        private Paint backgroundPaint = new Paint();
        private boolean keyboardChanged;

        public ContainerView(Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
            setWillNotDraw(false);
        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
            return !(nestedScrollChild != null && child != nestedScrollChild) &&
                    !dismissed && allowNestedScroll && nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL && !canDismissWithSwipe();
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
            if (dismissed || !allowNestedScroll) {
                return;
            }
            cancelCurrentAnimation();
        }

        @Override
        public void onStopNestedScroll(View target) {
            nestedScrollingParentHelper.onStopNestedScroll(target);
            if (dismissed || !allowNestedScroll) {
                return;
            }
            float currentTranslation = containerView.getTranslationY();
            checkDismiss(0, 0);
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
            if (dismissed || !allowNestedScroll) {
                return;
            }
            cancelCurrentAnimation();
            if (dyUnconsumed != 0) {
                float currentTranslation = containerView.getTranslationY();
                currentTranslation -= dyUnconsumed;
                if (currentTranslation < 0) {
                    currentTranslation = 0;
                }
                containerView.setTranslationY(currentTranslation);
                container.invalidate();
            }
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
            if (dismissed || !allowNestedScroll) {
                return;
            }
            cancelCurrentAnimation();
            float currentTranslation = containerView.getTranslationY();
            if (currentTranslation > 0 && dy > 0) {
                currentTranslation -= dy;
                consumed[1] = dy;
                if (currentTranslation < 0) {
                    currentTranslation = 0;
                }
                containerView.setTranslationY(currentTranslation);
                container.invalidate();
            }
        }

        @Override
        public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
            return false;
        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            return false;
        }

        @Override
        public int getNestedScrollAxes() {
            return nestedScrollingParentHelper.getNestedScrollAxes();
        }

        private void checkDismiss(float velX, float velY) {
            float translationY = containerView.getTranslationY();
            boolean backAnimation = translationY < AndroidUtilities.getPixelsInCM(0.8f, false) && (velY < 3500 || Math.abs(velY) < Math.abs(velX)) || velY < 0 && Math.abs(velY) >= 3500;
            if (!backAnimation) {
                boolean allowOld = allowCustomAnimation;
                allowCustomAnimation = false;
                useFastDismiss = true;
                dismiss();
                allowCustomAnimation = allowOld;
            } else {
                currentAnimation = new AnimatorSet();
                ValueAnimator invalidateContainer = ValueAnimator.ofFloat(0, 1);
                invalidateContainer.addUpdateListener(a -> {
                    if (container != null) {
                        container.invalidate();
                    }
                });
                currentAnimation.playTogether(
                    ObjectAnimator.ofFloat(containerView, "translationY", 0),
                    invalidateContainer
                );
                currentAnimation.setDuration((int) (250 * (Math.max(0, translationY) / AndroidUtilities.getPixelsInCM(0.8f, false))));
                currentAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentAnimation != null && currentAnimation.equals(animation)) {
                            currentAnimation = null;
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    }
                });
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                currentAnimation.start();
            }
        }

        private void cancelCurrentAnimation() {
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
        }

        private float y = 0f;
        private float swipeBackX = 0f;
        private boolean allowedSwipeToBack;
        public boolean processTouchEvent(MotionEvent ev, boolean intercept) {
            if (dismissed) {
                return false;
            }
            if (onContainerTouchEvent(ev)) {
                return true;
            }
            if (canSwipeToBack(ev) || allowedSwipeToBack) {
                if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && (!startedTracking && !maybeStartTracking && ev.getPointerCount() == 1)) {
                    allowedSwipeToBack = true;
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    startedTrackingPointerId = ev.getPointerId(0);
                    maybeStartTracking = true;
                    cancelCurrentAnimation();
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    float dx = ev.getX() - startedTrackingX;
                    float dy = ev.getY() - startedTrackingY;
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.addMovement(ev);
                    if (!disableScroll && maybeStartTracking && !startedTracking && (dx > 0 && dx / 3.0f > Math.abs(dy) && Math.abs(dx) >= touchSlop)) {
                        startedTrackingX = (int) ev.getX();
                        maybeStartTracking = false;
                        startedTracking = true;
                    } else if (startedTracking) {
                        swipeBackX += dx;
                        containerView.setTranslationX(Math.max(swipeBackX, 0));
                        startedTrackingX = (int) ev.getX();
                        container.invalidate();
                    }
                } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    containerView.setTranslationX(0);
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    final boolean backAnimation = swipeBackX < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
                    if (backAnimation) {
                        swipeBackX = Math.max(swipeBackX, 0);
                        ValueAnimator animator = ValueAnimator.ofFloat(swipeBackX, 0);
                        animator.addUpdateListener(animation -> {
                            swipeBackX = (float) animation.getAnimatedValue();
                            setTranslationX(swipeBackX);
                        });
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                swipeBackX = 0;
                                setTranslationX(0);
                            }
                        });
                        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        animator.setDuration(220);
                        animator.start();
                    } else {
                        ValueAnimator animator = ValueAnimator.ofFloat(swipeBackX, getMeasuredWidth());
                        animator.addUpdateListener(animation -> {
                            swipeBackX = (float) animation.getAnimatedValue();
                            setTranslationX(swipeBackX);
                        });
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                skipDismissAnimation = true;
                                dismiss();
                            }
                        });
                        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        animator.setDuration(150);
                        animator.start();
                    }
                    maybeStartTracking = false;
                    startedTracking = false;
                    startedTrackingPointerId = -1;
                    allowedSwipeToBack = false;
                }
            } else {
                if (canDismissWithTouchOutside() && ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && (!startedTracking && !maybeStartTracking && ev.getPointerCount() == 1)) {
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    if (startedTrackingY < containerView.getTop() || startedTrackingX < containerView.getLeft() || startedTrackingX > containerView.getRight()) {
                        onDismissWithTouchOutside();
                        return true;
                    }
                    onScrollUpBegin(y);
                    startedTrackingPointerId = ev.getPointerId(0);
                    maybeStartTracking = true;
                    cancelCurrentAnimation();
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (canDismissWithSwipe() && ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    float dx = Math.abs((int) (ev.getX() - startedTrackingX));
                    float dy = (int) ev.getY() - startedTrackingY;
                    boolean canScrollUp = onScrollUp(y + dy);
                    velocityTracker.addMovement(ev);
                    if (!disableScroll && maybeStartTracking && !startedTracking && (dy > 0 && dy / 3.0f > Math.abs(dx) && Math.abs(dy) >= touchSlop)) {
                        startedTrackingY = (int) ev.getY();
                        maybeStartTracking = false;
                        startedTracking = true;
                        requestDisallowInterceptTouchEvent(true);
                    } else if (startedTracking) {
                        y += dy;
                        if (!canScrollUp)
                            y = Math.max(y, 0);
                        containerView.setTranslationY(Math.max(y, 0));
                        startedTrackingY = (int) ev.getY();
                        container.invalidate();
                    }
                } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.computeCurrentVelocity(1000);
                    onScrollUpEnd(y);
                    if (startedTracking || y > 0) {
                        checkDismiss(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
                    } else {
                        maybeStartTracking = false;
                    }
                    startedTracking = false;
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    startedTrackingPointerId = -1;
                }
            }
            return (!intercept && maybeStartTracking) || startedTracking || !(canDismissWithSwipe() || canSwipeToBack(ev));
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            return processTouchEvent(ev, false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int containerHeight = height;
            View rootView = getRootView();
            getWindowVisibleDisplayFrame(rect);
            int oldKeyboardHeight = keyboardHeight;
            if (rect.bottom != 0 && rect.top != 0) {
                int usableViewHeight = (int) (rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight * (1f - hideSystemVerticalInsetsProgress) : 0) - AndroidUtilities.getViewInset(rootView) * (1f - hideSystemVerticalInsetsProgress));
                keyboardHeight = Math.max(0, usableViewHeight - (rect.bottom - rect.top));
                if (keyboardHeight < AndroidUtilities.dp(20)) {
                    keyboardHeight = 0;
                }
                bottomInset -= keyboardHeight;
            } else {
                keyboardHeight = 0;
            }
            if (oldKeyboardHeight != keyboardHeight) {
                keyboardChanged = true;
            }
            keyboardVisible = keyboardHeight > AndroidUtilities.dp(20);
            if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                bottomInset = lastInsets.getSystemWindowInsetBottom();
                leftInset = lastInsets.getSystemWindowInsetLeft();
                rightInset = lastInsets.getSystemWindowInsetRight();
                if (Build.VERSION.SDK_INT >= 29) {
                    bottomInset += getAdditionalMandatoryOffsets();
                }
                if (keyboardVisible && rect.bottom != 0 && rect.top != 0) {
                    bottomInset -= keyboardHeight;
                }
                if (!drawNavigationBar && !occupyNavigationBar) {
                    containerHeight -= getBottomInset();
                }
            }
            setMeasuredDimension(width, containerHeight);
            if (lastInsets != null && Build.VERSION.SDK_INT >= 21 && !occupyNavigationBar) {
                int inset = (int) (lastInsets.getSystemWindowInsetBottom() * (1f - hideSystemVerticalInsetsProgress));
                if (Build.VERSION.SDK_INT >= 29) {
                    inset += getAdditionalMandatoryOffsets();
                }
                height -= inset;
            }
            if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                width -= getRightInset() + getLeftInset();
            }
            isPortrait = width < height;

            if (containerView != null) {
                if (!fullWidth) {
                    int widthSpec;
                    if (AndroidUtilities.isTablet()) {
                        widthSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f) + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY);
                    } else {
                        widthSpec = MeasureSpec.makeMeasureSpec((getBottomSheetWidth(isPortrait, width, height)) + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY);
                    }
                    containerView.measure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
                } else {
                    containerView.measure(MeasureSpec.makeMeasureSpec(width + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
                }
            }
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE || child == containerView) {
                    continue;
                }
                if (!onCustomMeasure(child, width, height)) {
                    measureChildWithMargins(child, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY), 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutCount--;
            if (containerView != null) {
                int t = (bottom - top) - containerView.getMeasuredHeight();
                if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                    left += getLeftInset();
                    right -= getRightInset();
                    if (useSmoothKeyboard) {
                        t = 0;
                    } else if (!occupyNavigationBar) {
                        t -= lastInsets.getSystemWindowInsetBottom() * (1f - hideSystemVerticalInsetsProgress) - (drawNavigationBar ? 0 : getBottomInset());
                        if (Build.VERSION.SDK_INT >= 29) {
                            t -= getAdditionalMandatoryOffsets();
                        }
                    }
                }
                int l = ((right - left) - containerView.getMeasuredWidth()) / 2;
                if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                    l += getLeftInset();
                }
                if (smoothKeyboardAnimationEnabled && startAnimationRunnable == null && keyboardChanged && !dismissed && containerView.getTop() != t) {
                    containerView.setTranslationY(containerView.getTop() - t);
                    if (keyboardContentAnimator != null) {
                        keyboardContentAnimator.cancel();
                    }
                    keyboardContentAnimator = ValueAnimator.ofFloat(containerView.getTranslationY(), 0);
                    keyboardContentAnimator.addUpdateListener(valueAnimator -> {
                        containerView.setTranslationY((Float) valueAnimator.getAnimatedValue());
                        invalidate();
                    });
                    keyboardContentAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            containerView.setTranslationY(0);
                            invalidate();
                        }
                    });
                    keyboardContentAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                    keyboardContentAnimator.start();
                }
                containerView.layout(l, t, l + containerView.getMeasuredWidth(), t + containerView.getMeasuredHeight());
            }

            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE || child == containerView) {
                    continue;
                }
                if (!onCustomLayout(child, left, top, right, bottom - (drawNavigationBar ? getBottomInset() : 0))) {
                    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (right - left - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = right - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.CENTER_VERTICAL:
                            childTop = (bottom - top - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = (bottom - top) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }
                    if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                        childLeft += getLeftInset();
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }
            }
            if (layoutCount == 0 && startAnimationRunnable != null && !waitingKeyboard) {
                AndroidUtilities.cancelRunOnUIThread(startAnimationRunnable);
                startAnimationRunnable.run();
                startAnimationRunnable = null;
            }
            if (waitingKeyboard && keyboardVisible) {
                if (startAnimationRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(startAnimationRunnable);
                    startAnimationRunnable.run();
                }
                waitingKeyboard = false;
            }
            keyboardChanged = false;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (canDismissWithSwipe() || canSwipeToBack(event)) {
                return processTouchEvent(event, true);
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (maybeStartTracking && !startedTracking) {
                onTouchEvent(null);
            }
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (navBarColorKey >= 0) {
                    backgroundPaint.setColor(getThemedColor(navBarColorKey));
                } else {
                    backgroundPaint.setColor(navBarColor);
                }
            } else {
                backgroundPaint.setColor(0xff000000);
            }
            if (drawDoubleNavigationBar && !shouldOverlayCameraViewOverNavBar()) {
                drawNavigationBar(canvas, 1f);
            }
            if (backgroundPaint.getAlpha() < 255 && drawNavigationBar) {
                float translation = 0;
                if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && getAdditionalMandatoryOffsets() > 0) {
                    float dist = containerView.getMeasuredHeight() - containerView.getTranslationY();
                    translation = Math.max(0, getBottomInset() - dist);
                }
                int navBarHeight = drawNavigationBar ? getBottomInset() : 0;
                canvas.save();
                canvas.clipRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, Region.Op.DIFFERENCE);
                super.dispatchDraw(canvas);
                canvas.restore();
            } else {
                super.dispatchDraw(canvas);
            }
            if (!shouldOverlayCameraViewOverNavBar()) {
                drawNavigationBar(canvas, (drawDoubleNavigationBar ? 0.7f * navigationBarAlpha : 1f));
            }
            if (drawNavigationBar && rightInset != 0 && rightInset > leftInset && fullWidth && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                canvas.drawRect(containerView.getRight() - backgroundPaddingLeft, containerView.getTranslationY(), containerView.getRight() + rightInset, getMeasuredHeight(), backgroundPaint);
            }

            if (drawNavigationBar && leftInset != 0 && leftInset > rightInset && fullWidth && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                canvas.drawRect(0, containerView.getTranslationY(), containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight(), backgroundPaint);
            }

            if (containerView.getTranslationY() < 0) {
                backgroundPaint.setColor(behindKeyboardColorKey >= 0 ? getThemedColor(behindKeyboardColorKey) : behindKeyboardColor);
                canvas.drawRect(containerView.getLeft() + backgroundPaddingLeft, containerView.getY() + containerView.getMeasuredHeight(), containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight(), backgroundPaint);
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child instanceof CameraView) {
                if (shouldOverlayCameraViewOverNavBar()) {
                    drawNavigationBar(canvas, 1f);
                }
                return super.drawChild(canvas, child, drawingTime);
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean restore = false;
            if (backgroundPaint.getAlpha() < 255 && drawNavigationBar) {
                float translation = 0;
                if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && getAdditionalMandatoryOffsets() > 0) {
                    float dist = containerView.getMeasuredHeight() - containerView.getTranslationY();
                    translation = Math.max(0, getBottomInset() - dist);
                }
                int navBarHeight = drawNavigationBar ? getBottomInset() : 0;
                canvas.save();
                canvas.clipRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, Region.Op.DIFFERENCE);
                restore = true;
            }
            super.onDraw(canvas);
            if (drawNavigationBar && lastInsets != null && keyboardHeight != 0) {
                backgroundPaint.setColor(behindKeyboardColorKey >= 0 ? getThemedColor(behindKeyboardColorKey) : behindKeyboardColor);
                canvas.drawRect(containerView.getLeft() + backgroundPaddingLeft, getMeasuredHeight() - keyboardHeight - (drawNavigationBar ? getBottomInset() : 0), containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() - (drawNavigationBar ? getBottomInset() : 0), backgroundPaint);
            }
            onContainerDraw(canvas);
            if (restore) {
                canvas.restore();
            }
        }

        public void drawNavigationBar(Canvas canvas, float alpha) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (navBarColorKey >= 0) {
                    backgroundPaint.setColor(getThemedColor(navBarColorKey));
                } else {
                    backgroundPaint.setColor(navBarColor);
                }
            } else {
                backgroundPaint.setColor(0xff000000);
            }
            if (transitionFromRight && containerView.getVisibility() != View.VISIBLE) {
                return;
            }
            if ((drawNavigationBar && bottomInset != 0) || currentPanTranslationY != 0) {
                float translation = 0;
                int navBarHeight = drawNavigationBar ? getBottomInset() : 0;
                if (scrollNavBar || Build.VERSION.SDK_INT >= 29 && getAdditionalMandatoryOffsets() > 0) {
                    if (drawDoubleNavigationBar) {
                        translation = Math.max(0, Math.min(navBarHeight - currentPanTranslationY, containerView.getTranslationY()));
                    } else {
                        float dist = containerView.getMeasuredHeight() - containerView.getTranslationY();
                        translation = Math.max(0, getBottomInset() - dist);
                    }
                }
                int wasAlpha = backgroundPaint.getAlpha();
                if (transitionFromRight) {
                    alpha *= containerView.getAlpha();
                }
                int left = transitionFromRight ? (int) containerView.getX() : containerView.getLeft();
                if (alpha < 1f) {
                    backgroundPaint.setAlpha((int) (wasAlpha * alpha));
                }
                canvas.drawRect(left + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, backgroundPaint);
                backgroundPaint.setAlpha(wasAlpha);

                if (overlayDrawNavBarColor != 0) {
                    backgroundPaint.setColor(overlayDrawNavBarColor);
                    wasAlpha = backgroundPaint.getAlpha();
                    if (alpha < 1f) {
                        backgroundPaint.setAlpha((int) (wasAlpha * alpha));
                        translation = 0;
                    }
                    canvas.drawRect(left  + backgroundPaddingLeft, getMeasuredHeight() - navBarHeight + translation - currentPanTranslationY, containerView.getRight() - backgroundPaddingLeft, getMeasuredHeight() + translation, backgroundPaint);
                    backgroundPaint.setAlpha(wasAlpha);
                }
            }
        }
    }

    protected int getBottomSheetWidth(boolean isPortrait, int width,int height) {
        return isPortrait ? width : (int) Math.max(width * 0.8f, Math.min(AndroidUtilities.dp(480), width));
    }

    protected boolean shouldOverlayCameraViewOverNavBar() {
        return false;
    }

    public void setHideSystemVerticalInsets(boolean hideSystemVerticalInsets) {
        ValueAnimator animator = ValueAnimator.ofFloat(hideSystemVerticalInsetsProgress, hideSystemVerticalInsets ? 1f : 0f).setDuration(180);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(animation -> {
            hideSystemVerticalInsetsProgress = (float) animation.getAnimatedValue();
            container.requestLayout();
            containerView.requestLayout();
        });
        animator.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private int getAdditionalMandatoryOffsets() {
        if (!calcMandatoryInsets || lastInsets == null) {
            return 0;
        }
        Insets insets = lastInsets.getSystemGestureInsets();
        return !keyboardVisible && drawNavigationBar && insets != null && (insets.left != 0 || insets.right != 0) ? insets.bottom : 0;
    }

    public boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    public interface BottomSheetDelegateInterface {
        void onOpenAnimationStart();
        void onOpenAnimationEnd();
        boolean canDismiss();
    }

    public void setCalcMandatoryInsets(boolean value) {
        calcMandatoryInsets = value;
        drawNavigationBar = value;
    }

    public static class BottomSheetDelegate implements BottomSheetDelegateInterface {
        @Override
        public void onOpenAnimationStart() {

        }

        @Override
        public void onOpenAnimationEnd() {

        }

        @Override
        public boolean canDismiss() {
            return true;
        }
    }

    public static class BottomSheetCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private TextView textView;
        private ImageView imageView;
        private ImageView imageView2;
        int currentType;

        public BottomSheetCell(Context context, int type) {
            this(context, type, null);
        }

        public BottomSheetCell(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            currentType = type;
            if (type != Builder.CELL_TYPE_CALL) {
                setBackgroundDrawable(Theme.getSelectorDrawable(false, resourcesProvider));
            }
            //setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(56, 48, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            imageView2 = new ImageView(context);
            imageView2.setScaleType(ImageView.ScaleType.CENTER);
            imageView2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_radioBackgroundChecked, resourcesProvider), PorterDuff.Mode.SRC_IN));
            addView(imageView2, LayoutHelper.createFrame(56, 48, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            if (type == 0 || type == Builder.CELL_TYPE_CALL) {
                textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
            } else if (type == 1) {
                textView.setGravity(Gravity.CENTER);
                textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            } else if (type == 2) {
                textView.setGravity(Gravity.CENTER);
                textView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setBackground(Theme.AdaptiveRipple.filledRect(getThemedColor(Theme.key_featuredStickers_addButton), 6));
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 16, 16, 16));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int height = currentType == 2 ? 80 : 48;
            if (currentType == 0) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(height), MeasureSpec.EXACTLY));
        }

        public void setTextColor(int color) {
            textView.setTextColor(color);
        }

        public void setIconColor(int color) {
            imageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }

        public void setGravity(int gravity) {
            textView.setGravity(gravity);
        }

        public void setTextAndIcon(CharSequence text, int icon) {
            setTextAndIcon(text, icon, null, false);
        }

        public void setTextAndIcon(CharSequence text, Drawable icon) {
            setTextAndIcon(text, 0, icon, false);
        }

        public void setTextAndIcon(CharSequence text, int icon, Drawable drawable, boolean bigTitle) {
            textView.setText(text);
            if (icon != 0 || drawable != null) {
                if (drawable != null) {
                    imageView.setImageDrawable(drawable);
                } else {
                    imageView.setImageResource(icon);
                }
                imageView.setVisibility(VISIBLE);
                if (bigTitle) {
                    textView.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 21 : 72), 0, AndroidUtilities.dp(LocaleController.isRTL ? 72 : 21), 0);
                    imageView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(5), 0, LocaleController.isRTL ? AndroidUtilities.dp(5) : 5, 0);
                } else {
                    textView.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 16 : 72), 0, AndroidUtilities.dp(LocaleController.isRTL ? 72 : 16), 0);
                    imageView.setPadding(0, 0, 0, 0);
                }
            } else {
                imageView.setVisibility(INVISIBLE);
                textView.setPadding(AndroidUtilities.dp(bigTitle ? 21 : 16), 0, AndroidUtilities.dp(bigTitle ? 21 : 16), 0);
            }
        }

        private boolean checked;
        public void setChecked(boolean checked) {
            imageView2.setImageResource((this.checked = checked) ? R.drawable.checkbig : 0);
        }

        public boolean isChecked() {
            return checked;
        }

        public TextView getTextView() {
            return textView;
        }

        public ImageView getImageView() {
            return imageView;
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        public boolean isSelected = false;

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (isSelected) {
                info.setSelected(true);
            }
        }
    }

    public void setAllowNestedScroll(boolean value) {
        allowNestedScroll = value;
        if (!allowNestedScroll) {
            containerView.setTranslationY(0);
        }
    }

    public BottomSheet(Context context, boolean needFocus) {
        this(context, needFocus, null);
    }
    
    public BottomSheet(Context context, boolean needFocus, Theme.ResourcesProvider resourcesProvider) {
        super(context, R.style.TransparentDialog);
        this.resourcesProvider = resourcesProvider;

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        } else if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();

        Rect padding = new Rect();
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        shadowDrawable.getPadding(padding);
        backgroundPaddingLeft = padding.left;
        backgroundPaddingTop = padding.top;

        container = new ContainerView(getContext()) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                try {
                    return allowDrawContent && super.drawChild(canvas, child, drawingTime);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return true;
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                mainContainerDispatchDraw(canvas);
            }

            @Override
            protected void onConfigurationChanged(Configuration newConfig) {
                lastInsets = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    container.requestApplyInsets();
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Bulletin.addDelegate(this, new Bulletin.Delegate() {
                    @Override
                    public int getTopOffset(int tag) {
                        return AndroidUtilities.statusBarHeight;
                    }
                });
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                Bulletin.removeDelegate(this);
            }
        };
        container.setBackgroundDrawable(backDrawable);
        focusable = needFocus;
        if (Build.VERSION.SDK_INT >= 21) {
            container.setFitsSystemWindows(true);
            container.setOnApplyWindowInsetsListener((v, insets) -> {
                int newTopInset = insets.getSystemWindowInsetTop();
                if ((newTopInset != 0 || AndroidUtilities.isInMultiwindow) && statusBarHeight != newTopInset) {
                    statusBarHeight = newTopInset;
                }
                lastInsets = insets;
                v.requestLayout();
                onInsetsChanged();
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return insets.consumeSystemWindowInsets();
                }
            });
            if (Build.VERSION.SDK_INT >= 30) {
                container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            } else {
                container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }
        }

        backDrawable.setAlpha(0);
    }

    protected void onInsetsChanged() {

    }

    protected void mainContainerDispatchDraw(Canvas canvas) {

    }

    public void fixNavigationBar() {
        fixNavigationBar(getThemedColor(Theme.key_windowBackgroundGray));
    }

    public void fixNavigationBar(int bgColor) {
        drawNavigationBar = !occupyNavigationBar;
        drawDoubleNavigationBar = true;
        scrollNavBar = true;
        navBarColorKey = -1;
        setOverlayNavBarColor(navBarColor = bgColor);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(container, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (useLightStatusBar && Build.VERSION.SDK_INT >= 23) {
            int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
            if (color == 0xffffffff) {
                int flags = container.getSystemUiVisibility();
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                container.setSystemUiVisibility(flags);
            }
        }
        if (useLightNavBar && Build.VERSION.SDK_INT >= 26) {
            AndroidUtilities.setLightNavigationBar(getWindow(), false);
        }

        if (containerView == null) {
            containerView = new FrameLayout(getContext()) {
                @Override
                public boolean hasOverlappingRendering() {
                    return false;
                }

                @Override
                public void setTranslationY(float translationY) {
                    super.setTranslationY(translationY);
                    onContainerTranslationYChanged(translationY);
                }
            };
            containerView.setBackgroundDrawable(shadowDrawable);
            containerView.setPadding(backgroundPaddingLeft, (applyTopPadding ? AndroidUtilities.dp(8) : 0) + backgroundPaddingTop - 1, backgroundPaddingLeft, (applyBottomPadding ? AndroidUtilities.dp(8) : 0));
        }
        containerView.setVisibility(View.INVISIBLE);
        container.addView(containerView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        int topOffset = 0;
        if (title != null) {
            titleView = new TextView(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (multipleLinesTitle) {
                        int topOffset = getMeasuredHeight();
                        if (customView != null) {
                            ((ViewGroup.MarginLayoutParams) customView.getLayoutParams()).topMargin = topOffset;
                        } else if (containerView != null) {
                            for (int i = 1; i < containerView.getChildCount(); ++i) {
                                View child = containerView.getChildAt(i);
                                if (child instanceof BottomSheetCell) {
                                    ((ViewGroup.MarginLayoutParams) child.getLayoutParams()).topMargin = topOffset;
                                    topOffset += AndroidUtilities.dp(48);
                                }
                            }
                        }
                    }
                }
            };
            int height = 48;
            titleView.setText(title);
            if (bigTitle) {
                titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(multipleLinesTitle ? 14 : 6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
            } else {
                titleView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                titleView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(multipleLinesTitle ? 8 : 0), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
            }
            if (multipleLinesTitle) {
                titleView.setSingleLine(false);
                titleView.setMaxLines(5);
                titleView.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                titleView.setLines(1);
                titleView.setSingleLine(true);
                titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            }
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            containerView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, multipleLinesTitle ? ViewGroup.LayoutParams.WRAP_CONTENT : height));
            titleView.setOnTouchListener((v, event) -> true);
            topOffset += height;
        }
        if (customView != null) {
            if (customView.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) customView.getParent();
                viewGroup.removeView(customView);
            }
            if (!useBackgroundTopPadding) {
                containerView.setClipToPadding(false);
                containerView.setClipChildren(false);
                container.setClipToPadding(false);
                container.setClipChildren(false);
                containerView.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, customViewGravity, 0, topOffset, 0, 0));
                ((ViewGroup.MarginLayoutParams) customView.getLayoutParams()).topMargin = -backgroundPaddingTop + AndroidUtilities.dp(topOffset);
            } else {
                containerView.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, customViewGravity, 0, topOffset, 0, 0));
            }
        } else {
            if (items != null) {
                FrameLayout rowLayout = null;
                int lastRowLayoutNum = 0;
                for (int a = 0; a < items.length; a++) {
                    if (items[a] == null) {
                        continue;
                    }
                    BottomSheetCell cell = new BottomSheetCell(getContext(), cellType, resourcesProvider);
                    cell.setTextAndIcon(items[a], itemIcons != null ? itemIcons[a] : 0, null, bigTitle);
                    containerView.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, 0, topOffset, 0, 0));
                    topOffset += 48;
                    cell.setTag(a);
                    cell.setOnClickListener(v -> dismissWithButtonClick((Integer) v.getTag()));
                    itemViews.add(cell);
                }
            }
        }

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        if (focusable) {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        if (isFullscreen) {
            if (Build.VERSION.SDK_INT >= 21) {
                params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            }
            params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    public void setUseLightStatusBar(boolean value) {
        useLightStatusBar = value;
        if (Build.VERSION.SDK_INT >= 23) {
            int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
            int flags = container.getSystemUiVisibility();
            if (useLightStatusBar && color == 0xffffffff) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &=~ View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            container.setSystemUiVisibility(flags);
        }
    }

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusable(boolean value) {
        if (focusable == value) {
            return;
        }
        focusable = value;
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        if (focusable) {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            params.flags &=~ WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        window.setAttributes(params);
    }

    public void setShowWithoutAnimation(boolean value) {
        showWithoutAnimation = value;
    }

    public void setBackgroundColor(int color) {
        shadowDrawable.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
    }

    @Override
    public void show() {
        super.show();
        setShowing(true);
        if (focusable) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dismissed = false;
        cancelSheetAnimation();
        containerView.measure(
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x + backgroundPaddingLeft * 2, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST)
        );
        if (showWithoutAnimation) {
            backDrawable.setAlpha(dimBehind ? dimBehindAlpha : 0);
            containerView.setTranslationY(0);
            return;
        }
        backDrawable.setAlpha(0);
        layoutCount = 2;
        containerView.setTranslationY((Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight * (1f - hideSystemVerticalInsetsProgress) : 0) + containerView.getMeasuredHeight() + (scrollNavBar ? getBottomInset() : 0));
        long delay = openNoDelay ? 0 : 150;
        if (waitingKeyboard) {
            delay = 500;
        }
        AndroidUtilities.runOnUIThread(startAnimationRunnable = new Runnable() {
            @Override
            public void run() {
                if (startAnimationRunnable != this || dismissed) {
                    return;
                }
                startAnimationRunnable = null;
                startOpenAnimation();
            }
        }, delay);
    }

    public ColorDrawable getBackDrawable() {
        return backDrawable;
    }

    public int getBackgroundPaddingTop() {
        return backgroundPaddingTop ;
    }

    public void setAllowDrawContent(boolean value) {
        if (allowDrawContent != value) {
            allowDrawContent = value;
            container.setBackgroundDrawable(allowDrawContent ? backDrawable : null);
            container.invalidate();
        }
    }

    protected boolean canDismissWithSwipe() {
        return canDismissWithSwipe;
    }

    public void setCanDismissWithSwipe(boolean value) {
        canDismissWithSwipe = value;
    }

    protected boolean onContainerTouchEvent(MotionEvent event) {
        return false;
    }
    protected boolean onScrollUp(float translationY) {
        return false;
    }
    protected void onScrollUpEnd(float translationY) {
    }
    protected void onScrollUpBegin(float translationY) {}

    public void setCustomView(View view) {
        customView = view;
    }

    public void setTitle(CharSequence value) {
        setTitle(value, false);
    }

    public void setTitle(CharSequence value, boolean big) {
        title = value;
        bigTitle = big;
    }

    public void setApplyTopPadding(boolean value) {
        applyTopPadding = value;
    }

    public void setApplyBottomPadding(boolean value) {
        applyBottomPadding = value;
    }

    protected boolean onCustomMeasure(View view, int width, int height) {
        return false;
    }

    protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        return false;
    }

    protected void onDismissWithTouchOutside() {
        dismiss();
    }

    protected boolean canDismissWithTouchOutside() {
        return canDismissWithTouchOutside;
    }

    public void setCanDismissWithTouchOutside(boolean value) {
        canDismissWithTouchOutside = value;
    }

    public TextView getTitleView() {
        return titleView;
    }

    protected void onContainerTranslationYChanged(float translationY) {

    }

    protected void cancelSheetAnimation() {
        if (currentSheetAnimation != null) {
            currentSheetAnimation.cancel();
            currentSheetAnimation = null;
        }
        currentSheetAnimationType = 0;
    }

    public void setOnHideListener(OnDismissListener listener) {
        onHideListener = listener;
    }

    protected int getTargetOpenTranslationY() {
        return 0;
    }

    private void startOpenAnimation() {
        if (dismissed) {
            return;
        }
        containerView.setVisibility(View.VISIBLE);

        if (!onCustomOpenAnimation()) {
            if (Build.VERSION.SDK_INT >= 20 && useHardwareLayer) {
                container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            if (transitionFromRight) {
                containerView.setTranslationX(AndroidUtilities.dp(48));
                containerView.setAlpha(0);
                containerView.setTranslationY(0);
            } else {
                containerView.setTranslationY(getContainerViewHeight() + keyboardHeight + AndroidUtilities.dp(10) + (scrollNavBar ? getBottomInset() : 0));
            }
            currentSheetAnimationType = 1;
            if (navigationBarAnimation != null) {
                navigationBarAnimation.cancel();
            }
            navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 1f);
            navigationBarAnimation.addUpdateListener(a -> {
                navigationBarAlpha = (float) a.getAnimatedValue();
                if (container != null) {
                    container.invalidate();
                }
            });
            currentSheetAnimation = new AnimatorSet();
            currentSheetAnimation.playTogether(
                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(containerView, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0),
                    ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, dimBehind ? dimBehindAlpha : 0),
                    navigationBarAnimation
            );
            if (transitionFromRight) {
                currentSheetAnimation.setDuration(250);
                currentSheetAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            } else {
                currentSheetAnimation.setDuration(400);
                currentSheetAnimation.setInterpolator(openInterpolator);
            }
            currentSheetAnimation.setStartDelay(waitingKeyboard ? 0 : 20);
            currentSheetAnimation.setInterpolator(openInterpolator);
            int finalAccount = currentAccount;
            notificationsLocker.lock();
            currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                        currentSheetAnimation = null;
                        currentSheetAnimationType = 0;
                        onOpenAnimationEnd();
                        if (delegate != null) {
                            delegate.onOpenAnimationEnd();
                        }
                        if (useHardwareLayer) {
                            container.setLayerType(View.LAYER_TYPE_NONE, null);
                        }

                        if (isFullscreen) {
                            WindowManager.LayoutParams params = getWindow().getAttributes();
                            params.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                            getWindow().setAttributes(params);
                        }
                    }
                    if (pauseAllHeavyOperations) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    }
                    notificationsLocker.unlock();
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                        currentSheetAnimation = null;
                        currentSheetAnimationType = 0;
                    }
                }
            });
            if (pauseAllHeavyOperations) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            }
            currentSheetAnimation.start();
        }
    }

    public void setDelegate(BottomSheetDelegateInterface bottomSheetDelegate) {
        delegate = bottomSheetDelegate;
    }

    public ContainerView getContainer() {
        return container;
    }

    public ViewGroup getSheetContainer() {
        return containerView;
    }

    public int getTag() {
        return tag;
    }

    public void setDimBehind(boolean value) {
        dimBehind = value;
    }

    public void setDimBehindAlpha(int value) {
        dimBehindAlpha = value;
    }

    public void setItemText(int item, CharSequence text) {
        if (item < 0 || item >= itemViews.size()) {
            return;
        }
        BottomSheetCell cell = itemViews.get(item);
        cell.textView.setText(text);
    }

    public void setItemColor(int item, int color, int icon) {
        if (item < 0 || item >= itemViews.size()) {
            return;
        }
        BottomSheetCell cell = itemViews.get(item);
        cell.textView.setTextColor(color);
        cell.imageView.setColorFilter(new PorterDuffColorFilter(icon, PorterDuff.Mode.MULTIPLY));
    }

    public ArrayList<BottomSheetCell> getItemViews() {
        return itemViews;
    }

    public void setItems(CharSequence[] i, int[] icons, final OnClickListener listener) {
        items = i;
        itemIcons = icons;
        onClickListener = listener;
    }

    public void setTitleColor(int color) {
        if (titleView == null) {
            return;
        }
        titleView.setTextColor(color);
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void dismissWithButtonClick(final int item) {
        if (dismissed) {
            return;
        }
        dismissed = true;
        cancelSheetAnimation();
        currentSheetAnimationType = 2;
        currentSheetAnimation = new AnimatorSet();
        currentSheetAnimation.playTogether(
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, getContainerViewHeight() + keyboardHeight + AndroidUtilities.dp(10) + (scrollNavBar ? getBottomInset() : 0)),
                ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0)
        );
        currentSheetAnimation.setDuration(cellType == Builder.CELL_TYPE_CALL ? 330 : 180);
        currentSheetAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    currentSheetAnimationType = 0;
                    if (onClickListener != null) {
                        onClickListener.onClick(BottomSheet.this, item);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (onHideListener != null) {
                            onHideListener.onDismiss(BottomSheet.this);
                        }
                        try {
                            dismissInternal();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    currentSheetAnimationType = 0;
                }
            }
        });
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
        currentSheetAnimation.start();

        if (cellType == Builder.CELL_TYPE_CALL && selectedPos != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int color1 = getItemViews().get(selectedPos).getTextView().getCurrentTextColor();
            int color2 = getItemViews().get(item).getTextView().getCurrentTextColor();
            ValueAnimator animator = ValueAnimator.ofArgb(color1, color2);
            animator.addUpdateListener(a -> {
                int color = (int) a.getAnimatedValue();
                setItemColor(selectedPos, color, color);
            });
            animator.setDuration(130);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.start();
            ValueAnimator animator2 = ValueAnimator.ofArgb(color2, color1);
            animator2.addUpdateListener(a -> {
                int color = (int) a.getAnimatedValue();
                setItemColor(item, color, color);
            });
            animator2.setDuration(130);
            animator2.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator2.start();
        }
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (dismissed) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void onDismissAnimationStart() {}

    public int getContainerViewHeight() {
        if (containerView == null) {
            return 0;
        }
        return containerView.getMeasuredHeight();
    }

    protected boolean canSwipeToBack(MotionEvent event) {
        return false;
    }

    @Override
    public void dismiss() {
        if (delegate != null && !delegate.canDismiss()) {
            return;
        }
        if (dismissed) {
            return;
        }
        dismissed = true;
        if (onHideListener != null) {
            onHideListener.onDismiss(this);
        }
        cancelSheetAnimation();
        long duration = 0;
        onDismissAnimationStart();
        if (skipDismissAnimation) {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    dismissInternal();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } else {
            if (!allowCustomAnimation || !onCustomCloseAnimation()) {
                currentSheetAnimationType = 2;
                if (navigationBarAnimation != null) {
                    navigationBarAnimation.cancel();
                }
                navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 0f);
                navigationBarAnimation.addUpdateListener(a -> {
                    navigationBarAlpha = (float) a.getAnimatedValue();
                    if (container != null) {
                        container.invalidate();
                    }
                });
                currentSheetAnimation = new AnimatorSet();
                ArrayList<Animator> animators = new ArrayList<>();
                if (containerView != null) {
                    if (transitionFromRight) {
                        animators.add(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, AndroidUtilities.dp(48)));
                        animators.add(ObjectAnimator.ofFloat(containerView, View.ALPHA, 0));
                    } else {
                        animators.add(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, getContainerViewHeight() + keyboardHeight + AndroidUtilities.dp(10) + (scrollNavBar ? getBottomInset() : 0)));
                    }
                }
                animators.add(ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0));
                animators.add(navigationBarAnimation);
                currentSheetAnimation.playTogether(animators);

                if (transitionFromRight) {
                    currentSheetAnimation.setDuration(200);
                    currentSheetAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
                } else {
                    currentSheetAnimation.setDuration(duration = 250);
                    currentSheetAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                }
                currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                            currentSheetAnimation = null;
                            currentSheetAnimationType = 0;
                            AndroidUtilities.runOnUIThread(() -> {
                                try {
                                    dismissInternal();
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            });
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                            currentSheetAnimation = null;
                            currentSheetAnimationType = 0;
                        }
                    }
                });
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                currentSheetAnimation.start();
            }
        }

        Bulletin bulletin = Bulletin.getVisibleBulletin();
        if (bulletin != null && bulletin.isShowing() && bulletin.hideAfterBottomSheet) {
            if (duration > 0) {
                bulletin.hide((long) (duration * 0.6f));
            } else {
                bulletin.hide();
            }
        }
        setShowing(false);
    }

    public int getSheetAnimationType() {
        return currentSheetAnimationType;
    }

    public void dismissInternal() {
        try {
            super.dismiss();
        } catch (Exception e) {
            //ignore: not attached to window manager
            FileLog.e(e, false);
        }
    }

    protected boolean onCustomCloseAnimation() {
        return false;
    }

    protected boolean onCustomOpenAnimation() {
        return false;
    }

    public static class Builder {

        public static int CELL_TYPE_CALL = 4;

        private BottomSheet bottomSheet;

        public Builder(Context context) {
            this(context, false);
        }

        public Builder(Context context, int bgColor) {
            this(context, false, null, bgColor);
        }

        public Builder(Context context, boolean needFocus) {
            this(context, needFocus, null);
        }

        public Builder(Context context, boolean needFocus, int bgColor) {
            this(context, needFocus, null, bgColor);
        }

        public Builder(Context context, boolean needFocus, Theme.ResourcesProvider resourcesProvider) {
            bottomSheet = new BottomSheet(context, needFocus, resourcesProvider);
            bottomSheet.fixNavigationBar();
        }

        public Builder(Context context, boolean needFocus, Theme.ResourcesProvider resourcesProvider, int bgColor) {
            bottomSheet = new BottomSheet(context, needFocus, resourcesProvider);
            bottomSheet.setBackgroundColor(bgColor);
            bottomSheet.fixNavigationBar(bgColor);
        }

        public Builder setItems(CharSequence[] items, final OnClickListener onClickListener) {
            bottomSheet.items = items;
            bottomSheet.onClickListener = onClickListener;
            return this;
        }

        public Builder setItems(CharSequence[] items, int[] icons, final OnClickListener onClickListener) {
            bottomSheet.items = items;
            bottomSheet.itemIcons = icons;
            bottomSheet.onClickListener = onClickListener;
            return this;
        }

        public Builder setCustomView(View view) {
            bottomSheet.customView = view;
            return this;
        }

        public Builder setCustomView(View view, int gravity) {
            bottomSheet.customView = view;
            bottomSheet.customViewGravity = gravity;
            return this;
        }

        public View getCustomView() {
            return bottomSheet.customView;
        }

        public Builder setTitle(CharSequence title) {
            return setTitle(title, false);
        }

        public Builder setTitle(CharSequence title, boolean big) {
            bottomSheet.title = title;
            bottomSheet.bigTitle = big;
            return this;
        }

        public Builder selectedPos(Integer pos) {
            bottomSheet.selectedPos = pos;
            return this;
        }

        public Builder setCellType(int cellType) {
            bottomSheet.cellType = cellType;
            return this;
        }

        public Builder setTitleMultipleLines(boolean allowMultipleLines) {
            bottomSheet.multipleLinesTitle = allowMultipleLines;
            return this;
        }

        public BottomSheet create() {
            return bottomSheet;
        }

        public BottomSheet setDimBehind(boolean value) {
            bottomSheet.dimBehind = value;
            return bottomSheet;
        }

        public BottomSheet show() {
            bottomSheet.show();
            return bottomSheet;
        }

        public Builder setTag(int tag) {
            bottomSheet.tag = tag;
            return this;
        }

        public Builder setUseHardwareLayer(boolean value) {
            bottomSheet.useHardwareLayer = value;
            return this;
        }

        public Builder setDelegate(BottomSheetDelegate delegate) {
            bottomSheet.setDelegate(delegate);
            return this;
        }

        public Builder setApplyTopPadding(boolean value) {
            bottomSheet.applyTopPadding = value;
            return this;
        }

        public Builder setApplyBottomPadding(boolean value) {
            bottomSheet.applyBottomPadding = value;
            return this;
        }

        public Runnable getDismissRunnable() {
            return bottomSheet.dismissRunnable;
        }

        public BottomSheet setUseFullWidth(boolean value) {
            bottomSheet.fullWidth = value;
            return bottomSheet;
        }

        public BottomSheet setUseFullscreen(boolean value) {
            bottomSheet.isFullscreen = value;
            return bottomSheet;
        }

        public Builder setOnPreDismissListener(OnDismissListener onDismissListener) {
            bottomSheet.setOnHideListener(onDismissListener);
            return this;
        }
    }

    public int getLeftInset() {
        if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
            float inset;
            if (AVOID_SYSTEM_CUTOUT_FULLSCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lastInsets.getDisplayCutout() != null) {
                inset = lastInsets.getDisplayCutout().getSafeInsetLeft() + (lastInsets.getSystemWindowInsetLeft() - lastInsets.getDisplayCutout().getSafeInsetLeft()) * (1f - hideSystemVerticalInsetsProgress);
            } else {
                inset = lastInsets.getSystemWindowInsetLeft() * (1f - hideSystemVerticalInsetsProgress);
            }
            return (int) inset;
        }
        return 0;
    }

    public int getRightInset() {
        if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
            float inset;
            if (AVOID_SYSTEM_CUTOUT_FULLSCREEN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && lastInsets.getDisplayCutout() != null) {
                inset = lastInsets.getDisplayCutout().getSafeInsetRight() + (lastInsets.getSystemWindowInsetRight() - lastInsets.getDisplayCutout().getSafeInsetRight()) * (1f - hideSystemVerticalInsetsProgress);
            } else {
                inset = lastInsets.getSystemWindowInsetRight() * (1f - hideSystemVerticalInsetsProgress);
            }
            return (int) inset;
        }
        return 0;
    }

    public int getStatusBarHeight() {
        return (int) (statusBarHeight * (1f - hideSystemVerticalInsetsProgress));
    }

    public int getBottomInset() {
        return (int) (bottomInset * (1f - hideSystemVerticalInsetsProgress));
    }

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {

    }

    public void onContainerDraw(Canvas canvas) {

    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return null;
    }

    public void setCurrentPanTranslationY(float currentPanTranslationY) {
        this.currentPanTranslationY = currentPanTranslationY;
        container.invalidate();
    }

    private int overlayDrawNavBarColor;

    public void setOverlayNavBarColor(int color) {
        overlayDrawNavBarColor = color;
        if (container != null) {
            container.invalidate();
        }

//        if (Color.alpha(color) > 120) {
//            AndroidUtilities.setLightStatusBar(getWindow(), false);
//            AndroidUtilities.setLightNavigationBar(getWindow(), false);
//        } else {
//            AndroidUtilities.setLightStatusBar(getWindow(), !useLightStatusBar);
//            AndroidUtilities.setLightNavigationBar(getWindow(), !useLightNavBar);
//        }
        AndroidUtilities.setNavigationBarColor(getWindow(), overlayDrawNavBarColor);
        AndroidUtilities.setLightNavigationBar(getWindow(), AndroidUtilities.computePerceivedBrightness(overlayDrawNavBarColor) > .721);
    }

    public ViewGroup getContainerView() {
        return containerView;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setOpenNoDelay(boolean openNoDelay) {
        this.openNoDelay = openNoDelay;
    }

    public int getBackgroundPaddingLeft() {
        return this.backgroundPaddingLeft;
    }

    private void setShowing(boolean showing) {
        if (this.showing == showing) {
            return;
        }
        this.showing = showing;
        if (openedLayerNum > 0) {
            if (showing) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, openedLayerNum);
            } else {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, openedLayerNum);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    public void setImageReceiverNumLevel(int playingImages, int onShowing) {
        this.playingImagesLayerNum = playingImages;
        this.openedLayerNum = onShowing;
    }
}
