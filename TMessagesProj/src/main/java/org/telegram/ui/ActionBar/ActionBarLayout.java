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
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.Keep;

import android.os.SystemClock;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class ActionBarLayout extends FrameLayout {

    public interface ActionBarLayoutDelegate {
        boolean onPreIme();

        boolean needPresentFragment(BaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ActionBarLayout layout);

        boolean needAddFragmentToStack(BaseFragment fragment, ActionBarLayout layout);

        boolean needCloseLastFragment(ActionBarLayout layout);

        void onRebuildAllFragments(ActionBarLayout layout, boolean last);
    }

    public class LayoutContainer extends FrameLayout {

        private Rect rect = new Rect();
        private boolean isKeyboardVisible;

        private int fragmentPanTranslationOffset;
        private Paint backgroundPaint = new Paint();
        private int backgroundColor;

        public LayoutContainer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child instanceof ActionBar) {
                return super.drawChild(canvas, child, drawingTime);
            } else {
                int actionBarHeight = 0;
                int actionBarY = 0;
                int childCount = getChildCount();
                for (int a = 0; a < childCount; a++) {
                    View view = getChildAt(a);
                    if (view == child) {
                        continue;
                    }
                    if (view instanceof ActionBar && view.getVisibility() == VISIBLE) {
                        if (((ActionBar) view).getCastShadows()) {
                            actionBarHeight = view.getMeasuredHeight();
                            actionBarY = (int) view.getY();
                        }
                        break;
                    }
                }
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (actionBarHeight != 0 && headerShadowDrawable != null) {
                    headerShadowDrawable.setBounds(0, actionBarY + actionBarHeight, getMeasuredWidth(), actionBarY + actionBarHeight + headerShadowDrawable.getIntrinsicHeight());
                    headerShadowDrawable.draw(canvas);
                }
                return result;
            }
        }

        @Override
        public boolean hasOverlappingRendering() {
            if (Build.VERSION.SDK_INT >= 28) {
                return true;
            }
            return false;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int count = getChildCount();
            int actionBarHeight = 0;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (child instanceof ActionBar) {
                    child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                    actionBarHeight = child.getMeasuredHeight();
                    break;
                }
            }
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof ActionBar)) {
                    if (child.getFitsSystemWindows()) {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, actionBarHeight);
                    }
                }
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int count = getChildCount();
            int actionBarHeight = 0;
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (child instanceof ActionBar) {
                    actionBarHeight = child.getMeasuredHeight();
                    child.layout(0, 0, child.getMeasuredWidth(), actionBarHeight);
                    break;
                }
            }
            for (int a = 0; a < count; a++) {
                View child = getChildAt(a);
                if (!(child instanceof ActionBar)) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) child.getLayoutParams();
                    if (child.getFitsSystemWindows()) {
                        child.layout(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.leftMargin + child.getMeasuredWidth(), layoutParams.topMargin + child.getMeasuredHeight());
                    } else {
                        child.layout(layoutParams.leftMargin, layoutParams.topMargin + actionBarHeight, layoutParams.leftMargin + child.getMeasuredWidth(), layoutParams.topMargin + actionBarHeight + child.getMeasuredHeight());
                    }
                }
            }

            View rootView = getRootView();
            getWindowVisibleDisplayFrame(rect);
            int usableViewHeight = rootView.getHeight() - (rect.top != 0 ? AndroidUtilities.statusBarHeight : 0) - AndroidUtilities.getViewInset(rootView);
            isKeyboardVisible = usableViewHeight - (rect.bottom - rect.top) > 0;
            if (waitingForKeyboardCloseRunnable != null && !containerView.isKeyboardVisible && !containerViewBack.isKeyboardVisible) {
                AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
                waitingForKeyboardCloseRunnable.run();
                waitingForKeyboardCloseRunnable = null;
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if ((inPreviewMode || transitionAnimationPreviewMode) && (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
                return false;
            }
            //
            try {
                return (!inPreviewMode || this != containerView) && super.dispatchTouchEvent(ev);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (fragmentPanTranslationOffset != 0) {
                int color = Theme.getColor(Theme.key_windowBackgroundWhite);
                if (backgroundColor != color) {
                    backgroundPaint.setColor(backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite));
                }
                canvas.drawRect(0, getMeasuredHeight() - fragmentPanTranslationOffset - 3, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }
            super.onDraw(canvas);
        }

        public void setFragmentPanTranslationOffset(int fragmentPanTranslationOffset) {
            this.fragmentPanTranslationOffset = fragmentPanTranslationOffset;
            invalidate();
        }
    }

    public static class ThemeAnimationSettings {

        public final Theme.ThemeInfo theme;
        public final int accentId;
        public final boolean nightTheme;
        public final boolean instant;
        public boolean onlyTopFragment;
        public boolean applyTheme = true;
        public Runnable afterStartDescriptionsAddedRunnable;
        public Runnable beforeAnimationRunnable;
        public Runnable afterAnimationRunnable;
        public onAnimationProgress animationProgress;
        public long duration = 200;
        public Theme.ResourcesProvider resourcesProvider;

        public ThemeAnimationSettings(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant) {
            this.theme = theme;
            this.accentId = accentId;
            this.nightTheme = nightTheme;
            this.instant = instant;
        }

        public interface onAnimationProgress {
            void setProgress(float p);
        }
    }

    private static Drawable headerShadowDrawable;
    private static Drawable layerShadowDrawable;
    private static Paint scrimPaint;

    private Runnable waitingForKeyboardCloseRunnable;
    private Runnable delayedOpenAnimationRunnable;

    private boolean inBubbleMode;

    private boolean inPreviewMode;
    private boolean previewOpenAnimationInProgress;
    private ColorDrawable previewBackgroundDrawable;

    private LayoutContainer containerView;
    private LayoutContainer containerViewBack;
    private DrawerLayoutContainer drawerLayoutContainer;
    private ActionBar currentActionBar;

    private BaseFragment newFragment;
    private BaseFragment oldFragment;

    private AnimatorSet currentAnimation;
    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
    private AccelerateDecelerateInterpolator accelerateDecelerateInterpolator = new AccelerateDecelerateInterpolator();

    public float innerTranslationX;

    private boolean maybeStartTracking;
    protected boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    protected boolean animationInProgress;
    private VelocityTracker velocityTracker;
    private View layoutToIgnore;
    private boolean beginTrackingSent;
    private boolean transitionAnimationInProgress;
    private boolean transitionAnimationPreviewMode;
    private ArrayList<int[]> animateStartColors = new ArrayList<>();
    private ArrayList<int[]> animateEndColors = new ArrayList<>();

    StartColorsProvider startColorsProvider = new StartColorsProvider();
    public Theme.MessageDrawable messageDrawableOutStart;
    public Theme.MessageDrawable messageDrawableOutMediaStart;
    public ThemeAnimationSettings.onAnimationProgress animationProgressListener;

    private ArrayList<ArrayList<ThemeDescription>> themeAnimatorDescriptions = new ArrayList<>();
    private ArrayList<ThemeDescription> presentingFragmentDescriptions;
    private ArrayList<ThemeDescription.ThemeDescriptionDelegate> themeAnimatorDelegate = new ArrayList<>();
    private AnimatorSet themeAnimatorSet;
    private float themeAnimationValue;
    private boolean animateThemeAfterAnimation;
    private Theme.ThemeInfo animateSetThemeAfterAnimation;
    private boolean animateSetThemeNightAfterAnimation;
    private int animateSetThemeAccentIdAfterAnimation;
    private boolean rebuildAfterAnimation;
    private boolean rebuildLastAfterAnimation;
    private boolean showLastAfterAnimation;
    private long transitionAnimationStartTime;
    private boolean inActionMode;
    private int startedTrackingPointerId;
    private Runnable onCloseAnimationEndRunnable;
    private Runnable onOpenAnimationEndRunnable;
    private boolean useAlphaAnimations;
    private View backgroundView;
    private boolean removeActionBarExtraHeight;
    private Runnable animationRunnable;

    private float animationProgress;
    private long lastFrameTime;

    private String titleOverlayText;
    private int titleOverlayTextId;
    private Runnable overlayAction;

    private ActionBarLayoutDelegate delegate;
    protected Activity parentActivity;

    public ArrayList<BaseFragment> fragmentsStack;
    private Rect rect = new Rect();
    private boolean delayedAnimationResumed;

    public ActionBarLayout(Context context) {
        super(context);
        parentActivity = (Activity) context;

        if (layerShadowDrawable == null) {
            layerShadowDrawable = getResources().getDrawable(R.drawable.layer_shadow);
            headerShadowDrawable = getResources().getDrawable(R.drawable.header_shadow).mutate();
            scrimPaint = new Paint();
        }
    }

    public void init(ArrayList<BaseFragment> stack) {
        fragmentsStack = stack;
        containerViewBack = new LayoutContainer(parentActivity);
        addView(containerViewBack);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerViewBack.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerViewBack.setLayoutParams(layoutParams);

        containerView = new LayoutContainer(parentActivity);
        addView(containerView);
        layoutParams = (FrameLayout.LayoutParams) containerView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
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
            for (int a = 0, N = fragmentsStack.size(); a < N; a++) {
                BaseFragment fragment = fragmentsStack.get(a);
                fragment.onConfigurationChanged(newConfig);
                if (fragment.visibleDialog instanceof BottomSheet) {
                    ((BottomSheet) fragment.visibleDialog).onConfigurationChanged(newConfig);
                }
            }
        }
    }

    public void drawHeaderShadow(Canvas canvas, int y) {
        drawHeaderShadow(canvas, 255, y);
    }

    public void setInBubbleMode(boolean value) {
        inBubbleMode = value;
    }

    public boolean isInBubbleMode() {
        return inBubbleMode;
    }

    public void drawHeaderShadow(Canvas canvas, int alpha, int y) {
        if (headerShadowDrawable != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (headerShadowDrawable.getAlpha() != alpha) {
                    headerShadowDrawable.setAlpha(alpha);
                }
            } else {
                headerShadowDrawable.setAlpha(alpha);
            }
            headerShadowDrawable.setBounds(0, y, getMeasuredWidth(), y + headerShadowDrawable.getIntrinsicHeight());
            headerShadowDrawable.draw(canvas);
        }
    }

    @Keep
    public void setInnerTranslationX(float value) {
        innerTranslationX = value;
        invalidate();

        if (fragmentsStack.size() >= 2 && containerView.getMeasuredWidth() > 0) {
            float progress = value / containerView.getMeasuredWidth();
            BaseFragment prevFragment = fragmentsStack.get(fragmentsStack.size() - 2);
            prevFragment.onSlideProgress(false, progress);
            BaseFragment currFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            if (currFragment.isBeginToShow()) {
                int currNavigationBarColor = currFragment.getNavigationBarColor();
                int prevNavigationBarColor = prevFragment.getNavigationBarColor();
                if (currNavigationBarColor != prevNavigationBarColor) {
                    float ratio = MathUtils.clamp(2f * progress, 0f, 1f);
                    currFragment.setNavigationBarColor(ColorUtils.blendARGB(currNavigationBarColor, prevNavigationBarColor, ratio));
                }
            }
        }
    }

    @Keep
    public float getInnerTranslationX() {
        return innerTranslationX;
    }

    public void dismissDialogs() {
        if (!fragmentsStack.isEmpty()) {
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.dismissCurrentDialog();
        }
    }

    public void onResume() {
        if (transitionAnimationInProgress) {
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
            if (animationRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                animationRunnable = null;
            }
            if (waitingForKeyboardCloseRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
                waitingForKeyboardCloseRunnable = null;
            }
            if (onCloseAnimationEndRunnable != null) {
                onCloseAnimationEnd();
            } else if (onOpenAnimationEndRunnable != null) {
                onOpenAnimationEnd();
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
        return animationInProgress || checkTransitionAnimation() || onTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        onTouchEvent(null);
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            return delegate != null && delegate.onPreIme() || super.dispatchKeyEventPreIme(event);
        }
        return super.dispatchKeyEventPreIme(event);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (drawerLayoutContainer != null && drawerLayoutContainer.isDrawCurrentPreviewFragmentAbove()) {
            if (inPreviewMode || transitionAnimationPreviewMode || previewOpenAnimationInProgress) {
                if (child == (oldFragment != null && oldFragment.inPreviewMode ? containerViewBack : containerView)) {
                    drawerLayoutContainer.invalidate();
                    return false;
                }
            }
        }

        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int translationX = (int) innerTranslationX + getPaddingRight();
        int clipLeft = getPaddingLeft();
        int clipRight = width + getPaddingLeft();

        if (child == containerViewBack) {
            clipRight = translationX + AndroidUtilities.dp(1);
        } else if (child == containerView) {
            clipLeft = translationX;
        }

        final int restoreCount = canvas.save();
        if (!transitionAnimationInProgress && !inPreviewMode) {
            canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        }
        if ((inPreviewMode || transitionAnimationPreviewMode) && child == containerView) {
            drawPreviewDrawables(canvas, containerView);
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
                float opacity = Math.min(0.8f, (width - translationX) / (float) width);
                if (opacity < 0) {
                    opacity = 0;
                }
                scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * opacity) << 24);
                canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
            }
        }

        return result;
    }

    public float getCurrentPreviewFragmentAlpha() {
        if (inPreviewMode || transitionAnimationPreviewMode || previewOpenAnimationInProgress) {
            return (oldFragment != null && oldFragment.inPreviewMode ? containerViewBack : containerView).getAlpha();
        } else {
            return 0f;
        }
    }

    public void drawCurrentPreviewFragment(Canvas canvas, Drawable foregroundDrawable) {
        if (inPreviewMode || transitionAnimationPreviewMode || previewOpenAnimationInProgress) {
            final ViewGroup v = oldFragment != null && oldFragment.inPreviewMode ? containerViewBack : containerView;
            drawPreviewDrawables(canvas, v);
            if (v.getAlpha() < 1f) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (v.getAlpha() * 255), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.concat(v.getMatrix());
            v.draw(canvas);
            if (foregroundDrawable != null) {
                final View child = v.getChildAt(0);
                if (child != null) {
                    final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                    final Rect rect = new Rect();
                    child.getLocalVisibleRect(rect);
                    rect.offset(lp.leftMargin, lp.topMargin);
                    rect.top += Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight - 1 : 0;
                    foregroundDrawable.setAlpha((int) (v.getAlpha() * 255));
                    foregroundDrawable.setBounds(rect);
                    foregroundDrawable.draw(canvas);
                }
            }
            canvas.restore();
        }
    }

    private void drawPreviewDrawables(Canvas canvas, ViewGroup containerView) {
        View view = containerView.getChildAt(0);
        if (view != null) {
            previewBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            previewBackgroundDrawable.draw(canvas);
            int x = (getMeasuredWidth() - AndroidUtilities.dp(24)) / 2;
            int y = (int) (view.getTop() + containerView.getTranslationY() - AndroidUtilities.dp(12 + (Build.VERSION.SDK_INT < 21 ? 20 : 0)));
            Theme.moveUpDrawable.setBounds(x, y, x + AndroidUtilities.dp(24), y + AndroidUtilities.dp(24));
            Theme.moveUpDrawable.draw(canvas);
        }
    }

    public void setDelegate(ActionBarLayoutDelegate actionBarLayoutDelegate) {
        delegate = actionBarLayoutDelegate;
    }

    private void onSlideAnimationEnd(final boolean backAnimation) {
        if (!backAnimation) {
            if (fragmentsStack.size() < 2) {
                return;
            }
            BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.prepareFragmentToSlide(true, false);
            lastFragment.onPause();
            lastFragment.onFragmentDestroy();
            lastFragment.setParentLayout(null);

            fragmentsStack.remove(fragmentsStack.size() - 1);

            LayoutContainer temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;
            bringChildToFront(containerView);

            lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            currentActionBar = lastFragment.actionBar;
            lastFragment.onResume();
            lastFragment.onBecomeFullyVisible();
            lastFragment.prepareFragmentToSlide(false, false);

            layoutToIgnore = containerView;
        } else {
            if (fragmentsStack.size() >= 2) {
                BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                lastFragment.prepareFragmentToSlide(true, false);

                lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
                lastFragment.prepareFragmentToSlide(false, false);
                lastFragment.onPause();
                if (lastFragment.fragmentView != null) {
                    ViewGroup parent = (ViewGroup) lastFragment.fragmentView.getParent();
                    if (parent != null) {
                        lastFragment.onRemoveFromParent();
                        parent.removeViewInLayout(lastFragment.fragmentView);
                    }
                }
                if (lastFragment.actionBar != null && lastFragment.actionBar.shouldAddToContainer()) {
                    ViewGroup parent = (ViewGroup) lastFragment.actionBar.getParent();
                    if (parent != null) {
                        parent.removeViewInLayout(lastFragment.actionBar);
                    }
                }
            }
            layoutToIgnore = null;
        }
        containerViewBack.setVisibility(View.INVISIBLE);
        startedTracking = false;
        animationInProgress = false;
        containerView.setTranslationX(0);
        containerViewBack.setTranslationX(0);
        setInnerTranslationX(0);
    }

    private void prepareForMoving(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        layoutToIgnore = containerViewBack;
        startedTrackingX = (int) ev.getX();
        containerViewBack.setVisibility(View.VISIBLE);
        beginTrackingSent = false;

        BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        View fragmentView = lastFragment.fragmentView;
        if (fragmentView == null) {
            fragmentView = lastFragment.createView(parentActivity);
        }
        ViewGroup parent = (ViewGroup) fragmentView.getParent();
        if (parent != null) {
            lastFragment.onRemoveFromParent();
            parent.removeView(fragmentView);
        }
        containerViewBack.addView(fragmentView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fragmentView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;
        fragmentView.setLayoutParams(layoutParams);
        if (lastFragment.actionBar != null && lastFragment.actionBar.shouldAddToContainer()) {
            parent = (ViewGroup) lastFragment.actionBar.getParent();
            if (parent != null) {
                parent.removeView(lastFragment.actionBar);
            }
            if (removeActionBarExtraHeight) {
                lastFragment.actionBar.setOccupyStatusBar(false);
            }
            containerViewBack.addView(lastFragment.actionBar);
            lastFragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
        }
        if (!lastFragment.hasOwnBackground && fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
        lastFragment.onResume();
        if (themeAnimatorSet != null) {
            presentingFragmentDescriptions = lastFragment.getThemeDescriptions();
        }

        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        currentFragment.prepareFragmentToSlide(true, true);
        lastFragment.prepareFragmentToSlide(false, true);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (!checkTransitionAnimation() && !inActionMode && !animationInProgress) {
            if (fragmentsStack.size() > 1) {
                if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
                    BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (!currentFragment.isSwipeBackEnabled(ev)) {
                        maybeStartTracking = false;
                        startedTracking = false;
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
                    if (!transitionAnimationInProgress && !inPreviewMode && maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                        if (currentFragment.canBeginSlide() && findScrollingChild(this, ev.getX(), ev.getY()) == null) {
                            prepareForMoving(ev);
                        } else {
                            maybeStartTracking = false;
                        }
                    } else if (startedTracking) {
                        if (!beginTrackingSent) {
                            if (parentActivity.getCurrentFocus() != null) {
                                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                            }
                            BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                            currentFragment.onBeginSlide();
                            beginTrackingSent = true;
                        }
                        containerView.setTranslationX(dx);
                        setInnerTranslationX(dx);
                    }
                } else if (ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.computeCurrentVelocity(1000);
                    BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (!inPreviewMode && !transitionAnimationPreviewMode && !startedTracking && currentFragment.isSwipeBackEnabled(ev)) {
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        if (velX >= 3500 && velX > Math.abs(velY) && currentFragment.canBeginSlide()) {
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
                        float x = containerView.getX();
                        AnimatorSet animatorSet = new AnimatorSet();
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        final boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
                        float distToMove;
                        if (!backAnimation) {
                            distToMove = containerView.getMeasuredWidth() - x;
                            int duration = Math.max((int) (200.0f / containerView.getMeasuredWidth() * distToMove), 50);
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, containerView.getMeasuredWidth()).setDuration(duration),
                                    ObjectAnimator.ofFloat(this, "innerTranslationX", (float) containerView.getMeasuredWidth()).setDuration(duration)
                            );
                        } else {
                            distToMove = x;
                            int duration = Math.max((int) (200.0f / containerView.getMeasuredWidth() * distToMove), 50);
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0).setDuration(duration),
                                    ObjectAnimator.ofFloat(this, "innerTranslationX", 0.0f).setDuration(duration)
                            );
                        }

                        Animator customTransition = currentFragment.getCustomSlideTransition(false, backAnimation, distToMove);
                        if (customTransition != null) {
                            animatorSet.playTogether(customTransition);
                        }

                        BaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
                        if (lastFragment != null) {
                            customTransition = lastFragment.getCustomSlideTransition(false, backAnimation, distToMove);
                            if (customTransition != null) {
                                animatorSet.playTogether(customTransition);
                            }
                        }

                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                onSlideAnimationEnd(backAnimation);
                            }
                        });
                        animatorSet.start();
                        animationInProgress = true;
                        layoutToIgnore = containerViewBack;
                    } else {
                        maybeStartTracking = false;
                        startedTracking = false;
                        layoutToIgnore = null;
                    }
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                } else if (ev == null) {
                    maybeStartTracking = false;
                    startedTracking = false;
                    layoutToIgnore = null;
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
        if (transitionAnimationPreviewMode || startedTracking || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
            return;
        }
        if (GroupCallPip.onBackPressed()) {
            return;
        }
        if (currentActionBar != null && !currentActionBar.isActionModeShowed() && currentActionBar.isSearchFieldVisible) {
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
        onCloseAnimationEnd();
        onOpenAnimationEnd();
        if (waitingForKeyboardCloseRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
            waitingForKeyboardCloseRunnable = null;
        }
        if (currentAnimation != null) {
            if (byCheck) {
                currentAnimation.cancel();
            }
            currentAnimation = null;
        }
        if (animationRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(animationRunnable);
            animationRunnable = null;
        }
        setAlpha(1.0f);
        containerView.setAlpha(1.0f);
        containerView.setScaleX(1.0f);
        containerView.setScaleY(1.0f);
        containerViewBack.setAlpha(1.0f);
        containerViewBack.setScaleX(1.0f);
        containerViewBack.setScaleY(1.0f);
    }

    public BaseFragment getLastFragment() {
        if (fragmentsStack.isEmpty()) {
            return null;
        }
        return fragmentsStack.get(fragmentsStack.size() - 1);
    }

    public boolean checkTransitionAnimation() {
        if (transitionAnimationPreviewMode) {
            return false;
        }
        if (transitionAnimationInProgress && transitionAnimationStartTime < System.currentTimeMillis() - 1500) {
            onAnimationEndCheck(true);
        }
        return transitionAnimationInProgress;
    }

    public boolean isPreviewOpenAnimationInProgress() {
        return previewOpenAnimationInProgress;
    }

    public boolean isTransitionAnimationInProgress() {
        return transitionAnimationInProgress || animationInProgress;
    }

    private void presentFragmentInternalRemoveOld(boolean removeLast, final BaseFragment fragment) {
        if (fragment == null) {
            return;
        }
        fragment.onBecomeFullyHidden();
        fragment.onPause();
        if (removeLast) {
            fragment.onFragmentDestroy();
            fragment.setParentLayout(null);
            fragmentsStack.remove(fragment);
        } else {
            if (fragment.fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragment.fragmentView.getParent();
                if (parent != null) {
                    fragment.onRemoveFromParent();
                    try {
                        parent.removeViewInLayout(fragment.fragmentView);
                    } catch (Exception e) {
                        FileLog.e(e);
                        try {
                            parent.removeView(fragment.fragmentView);
                        } catch (Exception e2) {
                            FileLog.e(e2);
                        }
                    }
                }
            }
            if (fragment.actionBar != null && fragment.actionBar.shouldAddToContainer()) {
                ViewGroup parent = (ViewGroup) fragment.actionBar.getParent();
                if (parent != null) {
                    parent.removeViewInLayout(fragment.actionBar);
                }
            }
        }
        containerViewBack.setVisibility(View.INVISIBLE);
    }

    public boolean presentFragmentAsPreview(BaseFragment fragment) {
        return presentFragment(fragment, false, false, true, true);
    }

    public boolean presentFragment(BaseFragment fragment) {
        return presentFragment(fragment, false, false, true, false);
    }

    public boolean presentFragment(BaseFragment fragment, boolean removeLast) {
        return presentFragment(fragment, removeLast, false, true, false);
    }

    private void startLayoutAnimation(final boolean open, final boolean first, final boolean preview) {
        if (first) {
            animationProgress = 0.0f;
            lastFrameTime = System.nanoTime() / 1000000;
        }
        AndroidUtilities.runOnUIThread(animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (animationRunnable != this) {
                    return;
                }
                animationRunnable = null;
                if (first) {
                    transitionAnimationStartTime = System.currentTimeMillis();
                }
                long newTime = System.nanoTime() / 1000000;
                long dt = newTime - lastFrameTime;
                if (dt > 18) {
                    dt = 18;
                }
                lastFrameTime = newTime;
                animationProgress += dt / 150.0f;
                if (animationProgress > 1.0f) {
                    animationProgress = 1.0f;
                }
                if (newFragment != null) {
                    newFragment.onTransitionAnimationProgress(true, animationProgress);
                }
                if (oldFragment != null) {
                    oldFragment.onTransitionAnimationProgress(false, animationProgress);
                }
                Integer oldNavigationBarColor = oldFragment != null ? oldFragment.getNavigationBarColor() : null;
                Integer newNavigationBarColor = newFragment != null ? newFragment.getNavigationBarColor() : null;
                if (newFragment != null && !newFragment.inPreviewMode && oldNavigationBarColor != null) {
                    float ratio = MathUtils.clamp(2f * animationProgress - (open ? 1f : 0f), 0f, 1f);
                    newFragment.setNavigationBarColor(ColorUtils.blendARGB(oldNavigationBarColor, newNavigationBarColor, ratio));
                }
                float interpolated = decelerateInterpolator.getInterpolation(animationProgress);
                if (open) {
                    containerView.setAlpha(interpolated);
                    if (preview) {
                        containerView.setScaleX(0.9f + 0.1f * interpolated);
                        containerView.setScaleY(0.9f + 0.1f * interpolated);
                        previewBackgroundDrawable.setAlpha((int) (0x2e * interpolated));
                        Theme.moveUpDrawable.setAlpha((int) (255 * interpolated));
                        containerView.invalidate();
                        invalidate();
                    } else {
                        containerView.setTranslationX(AndroidUtilities.dp(48) * (1.0f - interpolated));
                    }
                } else {
                    containerViewBack.setAlpha(1.0f - interpolated);
                    if (preview) {
                        containerViewBack.setScaleX(0.9f + 0.1f * (1.0f - interpolated));
                        containerViewBack.setScaleY(0.9f + 0.1f * (1.0f - interpolated));
                        previewBackgroundDrawable.setAlpha((int) (0x2e * (1.0f - interpolated)));
                        Theme.moveUpDrawable.setAlpha((int) (255 * (1.0f - interpolated)));
                        containerView.invalidate();
                        invalidate();
                    } else {
                        containerViewBack.setTranslationX(AndroidUtilities.dp(48) * interpolated);
                    }
                }
                if (animationProgress < 1) {
                    startLayoutAnimation(open, false, preview);
                } else {
                    onAnimationEndCheck(false);
                }
            }
        });
    }

    public void resumeDelayedFragmentAnimation() {
        delayedAnimationResumed = true;
        if (delayedOpenAnimationRunnable == null || waitingForKeyboardCloseRunnable != null) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
        delayedOpenAnimationRunnable.run();
        delayedOpenAnimationRunnable = null;
    }

    public boolean isInPreviewMode() {
        return inPreviewMode || transitionAnimationPreviewMode;
    }

    public boolean presentFragment(final BaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation, boolean check, final boolean preview) {
        if (fragment == null || checkTransitionAnimation() || delegate != null && check && !delegate.needPresentFragment(fragment, removeLast, forceWithoutAnimation, this) || !fragment.onFragmentCreate()) {
            return false;
        }
        fragment.setInPreviewMode(preview);
        if (parentActivity.getCurrentFocus() != null && fragment.hideKeyboardOnShow() && !preview) {
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
        }
        boolean needAnimation = preview || !forceWithoutAnimation && MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);

        final BaseFragment currentFragment = !fragmentsStack.isEmpty() ? fragmentsStack.get(fragmentsStack.size() - 1) : null;

        fragment.setParentLayout(this);
        View fragmentView = fragment.fragmentView;
        if (fragmentView == null) {
            fragmentView = fragment.createView(parentActivity);
        } else {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                fragment.onRemoveFromParent();
                parent.removeView(fragmentView);
            }
        }
        containerViewBack.addView(fragmentView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fragmentView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        if (preview) {
            int height = fragment.getPreviewHeight();
            int statusBarHeight = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            if (height > 0 && height < getMeasuredHeight() - statusBarHeight) {
                layoutParams.height = height;
                layoutParams.topMargin = statusBarHeight + (getMeasuredHeight() - statusBarHeight - height) / 2;
            } else {
                layoutParams.topMargin = layoutParams.bottomMargin = AndroidUtilities.dp(46);
                layoutParams.topMargin += AndroidUtilities.statusBarHeight;
            }
            layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(8);
        } else {
            layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;
        }
        fragmentView.setLayoutParams(layoutParams);
        if (fragment.actionBar != null && fragment.actionBar.shouldAddToContainer()) {
            if (removeActionBarExtraHeight) {
                fragment.actionBar.setOccupyStatusBar(false);
            }
            ViewGroup parent = (ViewGroup) fragment.actionBar.getParent();
            if (parent != null) {
                parent.removeView(fragment.actionBar);
            }
            containerViewBack.addView(fragment.actionBar);
            fragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
        }
        fragmentsStack.add(fragment);
        fragment.onResume();
        currentActionBar = fragment.actionBar;
        if (!fragment.hasOwnBackground && fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }

        LayoutContainer temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setVisibility(View.VISIBLE);
        setInnerTranslationX(0);
        containerView.setTranslationY(0);

        if (preview) {
            if (Build.VERSION.SDK_INT >= 21) {
                fragmentView.setOutlineProvider(new ViewOutlineProvider() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, AndroidUtilities.statusBarHeight, view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dp(6));
                    }
                });
                fragmentView.setClipToOutline(true);
                fragmentView.setElevation(AndroidUtilities.dp(4));
            }
            if (previewBackgroundDrawable == null) {
                previewBackgroundDrawable = new ColorDrawable(0x2e000000);
            }
            previewBackgroundDrawable.setAlpha(0);
            Theme.moveUpDrawable.setAlpha(0);
        }

        bringChildToFront(containerView);
        if (!needAnimation) {
            presentFragmentInternalRemoveOld(removeLast, currentFragment);
            if (backgroundView != null) {
                backgroundView.setVisibility(VISIBLE);
            }
        }

        if (themeAnimatorSet != null) {
            presentingFragmentDescriptions = fragment.getThemeDescriptions();
        }

        if (needAnimation || preview) {
            if (useAlphaAnimations && fragmentsStack.size() == 1) {
                presentFragmentInternalRemoveOld(removeLast, currentFragment);

                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;
                onOpenAnimationEndRunnable = () -> {
                    if (currentFragment != null) {
                        currentFragment.onTransitionAnimationEnd(false, false);
                    }
                    fragment.onTransitionAnimationEnd(true, false);
                    fragment.onBecomeFullyVisible();
                };
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f));
                if (backgroundView != null) {
                    backgroundView.setVisibility(VISIBLE);
                    animators.add(ObjectAnimator.ofFloat(backgroundView, View.ALPHA, 0.0f, 1.0f));
                }
                if (currentFragment != null) {
                    currentFragment.onTransitionAnimationStart(false, false);
                }
                fragment.onTransitionAnimationStart(true, false);
                currentAnimation = new AnimatorSet();
                currentAnimation.playTogether(animators);
                currentAnimation.setInterpolator(accelerateDecelerateInterpolator);
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            } else {
                transitionAnimationPreviewMode = preview;
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;
                onOpenAnimationEndRunnable = () -> {
                    if (preview) {
                        inPreviewMode = true;
                        transitionAnimationPreviewMode = false;
                        containerView.setScaleX(1.0f);
                        containerView.setScaleY(1.0f);
                    } else {
                        presentFragmentInternalRemoveOld(removeLast, currentFragment);
                        containerView.setTranslationX(0);
                    }
                    if (currentFragment != null) {
                        currentFragment.onTransitionAnimationEnd(false, false);
                    }
                    fragment.onTransitionAnimationEnd(true, false);
                    fragment.onBecomeFullyVisible();
                };
                boolean noDelay;
                if (noDelay = !fragment.needDelayOpenAnimation()) {
                    if (currentFragment != null) {
                        currentFragment.onTransitionAnimationStart(false, false);
                    }
                    fragment.onTransitionAnimationStart(true, false);
                }

                delayedAnimationResumed = false;
                oldFragment = currentFragment;
                newFragment = fragment;
                AnimatorSet animation = null;
                if (!preview) {
                    animation = fragment.onCustomTransitionAnimation(true, () -> onAnimationEndCheck(false));
                }
                if (animation == null) {
                    containerView.setAlpha(0.0f);
                    if (preview) {
                        containerView.setTranslationX(0.0f);
                        containerView.setScaleX(0.9f);
                        containerView.setScaleY(0.9f);
                    } else {
                        containerView.setTranslationX(48.0f);
                        containerView.setScaleX(1.0f);
                        containerView.setScaleY(1.0f);
                    }
                    if (containerView.isKeyboardVisible || containerViewBack.isKeyboardVisible) {
                        if (currentFragment != null && !preview) {
                            currentFragment.saveKeyboardPositionBeforeTransition();
                        }
                        waitingForKeyboardCloseRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (waitingForKeyboardCloseRunnable != this) {
                                    return;
                                }
                                waitingForKeyboardCloseRunnable = null;
                                if (noDelay) {
                                    if (currentFragment != null) {
                                        currentFragment.onTransitionAnimationStart(false, false);
                                    }
                                    fragment.onTransitionAnimationStart(true, false);
                                    startLayoutAnimation(true, true, preview);
                                } else if (delayedOpenAnimationRunnable != null) {
                                    AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
                                    if (delayedAnimationResumed) {
                                        delayedOpenAnimationRunnable.run();
                                    } else {
                                        AndroidUtilities.runOnUIThread(delayedOpenAnimationRunnable, 200);
                                    }
                                }
                            }
                        };
                        if (fragment.needDelayOpenAnimation()) {
                            delayedOpenAnimationRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (delayedOpenAnimationRunnable != this) {
                                        return;
                                    }
                                    delayedOpenAnimationRunnable = null;
                                    if (currentFragment != null) {
                                        currentFragment.onTransitionAnimationStart(false, false);
                                    }
                                    fragment.onTransitionAnimationStart(true, false);
                                    startLayoutAnimation(true, true, preview);
                                }
                            };
                        }
                        AndroidUtilities.runOnUIThread(waitingForKeyboardCloseRunnable, SharedConfig.smoothKeyboard ? 250 : 200);
                    } else if (fragment.needDelayOpenAnimation()) {
                        delayedOpenAnimationRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (delayedOpenAnimationRunnable != this) {
                                    return;
                                }
                                delayedOpenAnimationRunnable = null;
                                fragment.onTransitionAnimationStart(true, false);
                                startLayoutAnimation(true, true, preview);
                            }
                        };
                        AndroidUtilities.runOnUIThread(delayedOpenAnimationRunnable, 200);
                    } else {
                        startLayoutAnimation(true, true, preview);
                    }
                } else {
                    if (!preview && containerView.isKeyboardVisible || containerViewBack.isKeyboardVisible && currentFragment != null) {
                        currentFragment.saveKeyboardPositionBeforeTransition();
                    }
                    currentAnimation = animation;
                }
            }
        } else {
            if (backgroundView != null) {
                backgroundView.setAlpha(1.0f);
                backgroundView.setVisibility(VISIBLE);
            }
            if (currentFragment != null) {
                currentFragment.onTransitionAnimationStart(false, false);
                currentFragment.onTransitionAnimationEnd(false, false);
            }
            fragment.onTransitionAnimationStart(true, false);
            fragment.onTransitionAnimationEnd(true, false);
            fragment.onBecomeFullyVisible();
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
            if (!fragmentsStack.isEmpty()) {
                BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                previousFragment.onPause();
                if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
                    ViewGroup parent = (ViewGroup) previousFragment.actionBar.getParent();
                    if (parent != null) {
                        parent.removeView(previousFragment.actionBar);
                    }
                }
                if (previousFragment.fragmentView != null) {
                    ViewGroup parent = (ViewGroup) previousFragment.fragmentView.getParent();
                    if (parent != null) {
                        previousFragment.onRemoveFromParent();
                        parent.removeView(previousFragment.fragmentView);
                    }
                }
            }
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
        containerViewBack.setVisibility(View.INVISIBLE);
        containerViewBack.setTranslationY(0);
        bringChildToFront(containerView);
    }

    public void movePreviewFragment(float dy) {
        if (!inPreviewMode || transitionAnimationPreviewMode) {
            return;
        }
        float currentTranslation = containerView.getTranslationY();
        float nextTranslation = -dy;
        if (nextTranslation > 0) {
            nextTranslation = 0;
        } else if (nextTranslation < -AndroidUtilities.dp(60)) {
            previewOpenAnimationInProgress = true;
            inPreviewMode = false;
            nextTranslation = 0;

            BaseFragment prevFragment = fragmentsStack.get(fragmentsStack.size() - 2);
            BaseFragment fragment = fragmentsStack.get(fragmentsStack.size() - 1);

            if (Build.VERSION.SDK_INT >= 21) {
                fragment.fragmentView.setOutlineProvider(null);
                fragment.fragmentView.setClipToOutline(false);
            }
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fragment.fragmentView.getLayoutParams();
            layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;
            layoutParams.height = LayoutHelper.MATCH_PARENT;
            fragment.fragmentView.setLayoutParams(layoutParams);

            presentFragmentInternalRemoveOld(false, prevFragment);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(fragment.fragmentView, View.SCALE_X, 1.0f, 1.05f, 1.0f),
                    ObjectAnimator.ofFloat(fragment.fragmentView, View.SCALE_Y, 1.0f, 1.05f, 1.0f));
            animatorSet.setDuration(200);
            animatorSet.setInterpolator(new CubicBezierInterpolator(0.42, 0.0, 0.58, 1.0));
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    previewOpenAnimationInProgress = false;
                    fragment.onPreviewOpenAnimationEnd();
                }
            });
            animatorSet.start();
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

            fragment.setInPreviewMode(false);
        }
        if (currentTranslation != nextTranslation) {
            containerView.setTranslationY(nextTranslation);
            invalidate();
        }
    }

    public void finishPreviewFragment() {
        if (!inPreviewMode && !transitionAnimationPreviewMode) {
            return;
        }
        if (delayedOpenAnimationRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
            delayedOpenAnimationRunnable = null;
        }
        closeLastFragment(true);
    }

    public void closeLastFragment(boolean animated) {
        if (delegate != null && !delegate.needCloseLastFragment(this) || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
            return;
        }
        if (parentActivity.getCurrentFocus() != null) {
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
        }
        setInnerTranslationX(0);
        boolean needAnimation = inPreviewMode || transitionAnimationPreviewMode || animated && MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        final BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        BaseFragment previousFragment = null;
        if (fragmentsStack.size() > 1) {
            previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        }

        if (previousFragment != null) {
            LayoutContainer temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;

            previousFragment.setParentLayout(this);
            View fragmentView = previousFragment.fragmentView;
            if (fragmentView == null) {
                fragmentView = previousFragment.createView(parentActivity);
            }

            if (!inPreviewMode) {
                containerView.setVisibility(View.VISIBLE);
                ViewGroup parent = (ViewGroup) fragmentView.getParent();
                if (parent != null) {
                    previousFragment.onRemoveFromParent();
                    try {
                        parent.removeView(fragmentView);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                containerView.addView(fragmentView);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fragmentView.getLayoutParams();
                layoutParams.width = LayoutHelper.MATCH_PARENT;
                layoutParams.height = LayoutHelper.MATCH_PARENT;
                layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;
                fragmentView.setLayoutParams(layoutParams);
                if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
                    if (removeActionBarExtraHeight) {
                        previousFragment.actionBar.setOccupyStatusBar(false);
                    }
                    parent = (ViewGroup) previousFragment.actionBar.getParent();
                    if (parent != null) {
                        parent.removeView(previousFragment.actionBar);
                    }
                    containerView.addView(previousFragment.actionBar);
                    previousFragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
                }
            }

            newFragment = previousFragment;
            oldFragment = currentFragment;
            previousFragment.onTransitionAnimationStart(true, true);
            currentFragment.onTransitionAnimationStart(false, true);
            previousFragment.onResume();
            if (themeAnimatorSet != null) {
                presentingFragmentDescriptions = previousFragment.getThemeDescriptions();
            }
            currentActionBar = previousFragment.actionBar;
            if (!previousFragment.hasOwnBackground && fragmentView.getBackground() == null) {
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }

            if (!needAnimation) {
                closeLastFragmentInternalRemoveOld(currentFragment);
            }

            if (needAnimation) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;
                final BaseFragment previousFragmentFinal = previousFragment;
                onCloseAnimationEndRunnable = () -> {
                    if (inPreviewMode || transitionAnimationPreviewMode) {
                        containerViewBack.setScaleX(1.0f);
                        containerViewBack.setScaleY(1.0f);
                        inPreviewMode = false;
                        transitionAnimationPreviewMode = false;
                    } else {
                        containerViewBack.setTranslationX(0);
                    }
                    closeLastFragmentInternalRemoveOld(currentFragment);
                    currentFragment.onTransitionAnimationEnd(false, true);
                    previousFragmentFinal.onTransitionAnimationEnd(true, true);
                    previousFragmentFinal.onBecomeFullyVisible();
                };
                AnimatorSet animation = null;
                if (!inPreviewMode && !transitionAnimationPreviewMode) {
                    animation = currentFragment.onCustomTransitionAnimation(false, () -> onAnimationEndCheck(false));
                }
                if (animation == null) {
                    if (!inPreviewMode && (containerView.isKeyboardVisible || containerViewBack.isKeyboardVisible)) {
                        waitingForKeyboardCloseRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (waitingForKeyboardCloseRunnable != this) {
                                    return;
                                }
                                waitingForKeyboardCloseRunnable = null;
                                startLayoutAnimation(false, true, false);
                            }
                        };
                        AndroidUtilities.runOnUIThread(waitingForKeyboardCloseRunnable, 200);
                    } else {
                        startLayoutAnimation(false, true, inPreviewMode || transitionAnimationPreviewMode);
                    }
                } else {
                    currentAnimation = animation;
                    if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().isShowing()) {
                        Bulletin.getVisibleBulletin().hide();
                    }
                }
            } else {
                currentFragment.onTransitionAnimationEnd(false, true);
                previousFragment.onTransitionAnimationEnd(true, true);
                previousFragment.onBecomeFullyVisible();
            }
        } else {
            if (useAlphaAnimations) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;

                onCloseAnimationEndRunnable = () -> {
                    removeFragmentFromStackInternal(currentFragment);
                    setVisibility(GONE);
                    if (backgroundView != null) {
                        backgroundView.setVisibility(GONE);
                    }
                    if (drawerLayoutContainer != null) {
                        drawerLayoutContainer.setAllowOpenDrawer(true, false);
                    }
                };

                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(this, View.ALPHA, 1.0f, 0.0f));
                if (backgroundView != null) {
                    animators.add(ObjectAnimator.ofFloat(backgroundView, View.ALPHA, 1.0f, 0.0f));
                }

                currentAnimation = new AnimatorSet();
                currentAnimation.playTogether(animators);
                currentAnimation.setInterpolator(accelerateDecelerateInterpolator);
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        transitionAnimationStartTime = System.currentTimeMillis();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            } else {
                removeFragmentFromStackInternal(currentFragment);
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
        for (int a = 0; a < fragmentsStack.size() - 1; a++) {
            BaseFragment previousFragment = fragmentsStack.get(a);
            if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
                ViewGroup parent = (ViewGroup) previousFragment.actionBar.getParent();
                if (parent != null) {
                    parent.removeView(previousFragment.actionBar);
                }
            }
            if (previousFragment.fragmentView != null) {
                ViewGroup parent = (ViewGroup) previousFragment.fragmentView.getParent();
                if (parent != null) {
                    previousFragment.onPause();
                    previousFragment.onRemoveFromParent();
                    parent.removeView(previousFragment.fragmentView);
                }
            }
        }
        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        previousFragment.setParentLayout(this);
        View fragmentView = previousFragment.fragmentView;
        if (fragmentView == null) {
            fragmentView = previousFragment.createView(parentActivity);
        } else {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                previousFragment.onRemoveFromParent();
                parent.removeView(fragmentView);
            }
        }
        containerView.addView(fragmentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (previousFragment.actionBar != null && previousFragment.actionBar.shouldAddToContainer()) {
            if (removeActionBarExtraHeight) {
                previousFragment.actionBar.setOccupyStatusBar(false);
            }
            ViewGroup parent = (ViewGroup) previousFragment.actionBar.getParent();
            if (parent != null) {
                parent.removeView(previousFragment.actionBar);
            }
            containerView.addView(previousFragment.actionBar);
            previousFragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, overlayAction);
        }
        previousFragment.onResume();
        currentActionBar = previousFragment.actionBar;
        if (!previousFragment.hasOwnBackground && fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }
    }

    private void removeFragmentFromStackInternal(BaseFragment fragment) {
        fragment.onPause();
        fragment.onFragmentDestroy();
        fragment.setParentLayout(null);
        fragmentsStack.remove(fragment);
    }

    public void removeFragmentFromStack(int num) {
        if (num >= fragmentsStack.size()) {
            return;
        }
        removeFragmentFromStackInternal(fragmentsStack.get(num));
    }

    public void removeFragmentFromStack(BaseFragment fragment) {
        if (useAlphaAnimations && fragmentsStack.size() == 1 && AndroidUtilities.isTablet()) {
            closeLastFragment(true);
        } else {
            if (delegate != null && fragmentsStack.size() == 1 && AndroidUtilities.isTablet()) {
                delegate.needCloseLastFragment(this);
            }
            removeFragmentFromStackInternal(fragment);
        }
    }

    public void removeAllFragments() {
        for (int a = 0; a < fragmentsStack.size(); a++) {
            removeFragmentFromStackInternal(fragmentsStack.get(a));
            a--;
        }
    }

    @Keep
    public void setThemeAnimationValue(float value) {
        themeAnimationValue = value;
        for (int j = 0, N = themeAnimatorDescriptions.size(); j < N; j++) {
            ArrayList<ThemeDescription> descriptions = themeAnimatorDescriptions.get(j);
            int[] startColors = animateStartColors.get(j);
            int[] endColors = animateEndColors.get(j);
            int rE, gE, bE, aE, rS, gS, bS, aS, a, r, g, b;
            for (int i = 0, N2 = descriptions.size(); i < N2; i++) {
                rE = Color.red(endColors[i]);
                gE = Color.green(endColors[i]);
                bE = Color.blue(endColors[i]);
                aE = Color.alpha(endColors[i]);

                rS = Color.red(startColors[i]);
                gS = Color.green(startColors[i]);
                bS = Color.blue(startColors[i]);
                aS = Color.alpha(startColors[i]);

                a = Math.min(255, (int) (aS + (aE - aS) * value));
                r = Math.min(255, (int) (rS + (rE - rS) * value));
                g = Math.min(255, (int) (gS + (gE - gS) * value));
                b = Math.min(255, (int) (bS + (bE - bS) * value));
                int color = Color.argb(a, r, g, b);
                ThemeDescription description = descriptions.get(i);
                description.setAnimatedColor(color);
                description.setColor(color, false, false);
            }
        }
        for (int j = 0, N = themeAnimatorDelegate.size(); j < N; j++) {
            ThemeDescription.ThemeDescriptionDelegate delegate = themeAnimatorDelegate.get(j);
            if (delegate != null) {
                delegate.didSetColor();
                delegate.onAnimationProgress(value);
            }
        }
        if (presentingFragmentDescriptions != null) {
            for (int i = 0, N = presentingFragmentDescriptions.size(); i < N; i++) {
                ThemeDescription description = presentingFragmentDescriptions.get(i);
                String key = description.getCurrentKey();
                description.setColor(Theme.getColor(key), false, false);
            }
        }
        if (animationProgressListener != null) {
            animationProgressListener.setProgress(value);
        }
    }

    @Keep
    public float getThemeAnimationValue() {
        return themeAnimationValue;
    }

    private void addStartDescriptions(ArrayList<ThemeDescription> descriptions) {
        if (descriptions == null) {
            return;
        }
        themeAnimatorDescriptions.add(descriptions);
        int[] startColors = new int[descriptions.size()];
        animateStartColors.add(startColors);
        for (int a = 0, N = descriptions.size(); a < N; a++) {
            ThemeDescription description = descriptions.get(a);
            startColors[a] = description.getSetColor();
            ThemeDescription.ThemeDescriptionDelegate delegate = description.setDelegateDisabled();
            if (delegate != null && !themeAnimatorDelegate.contains(delegate)) {
                themeAnimatorDelegate.add(delegate);
            }
        }
    }

    private void addEndDescriptions(ArrayList<ThemeDescription> descriptions) {
        if (descriptions == null) {
            return;
        }
        int[] endColors = new int[descriptions.size()];
        animateEndColors.add(endColors);
        for (int a = 0, N = descriptions.size(); a < N; a++) {
            endColors[a] = descriptions.get(a).getSetColor();
        }
    }

    public void animateThemedValues(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant) {
        animateThemedValues(new ThemeAnimationSettings(theme, accentId, nightTheme, instant));
    }

    public void animateThemedValues(ThemeAnimationSettings settings) {
        if (transitionAnimationInProgress || startedTracking) {
            animateThemeAfterAnimation = true;
            animateSetThemeAfterAnimation = settings.theme;
            animateSetThemeNightAfterAnimation = settings.nightTheme;
            animateSetThemeAccentIdAfterAnimation = settings.accentId;
            return;
        }
        if (themeAnimatorSet != null) {
            themeAnimatorSet.cancel();
            themeAnimatorSet = null;
        }
        boolean startAnimation = false;
        int fragmentCount = settings.onlyTopFragment ? 1 : 2;
        for (int i = 0; i < fragmentCount; i++) {
            BaseFragment fragment;
            if (i == 0) {
                fragment = getLastFragment();
            } else {
                if (!inPreviewMode && !transitionAnimationPreviewMode || fragmentsStack.size() <= 1) {
                    continue;
                }
                fragment = fragmentsStack.get(fragmentsStack.size() - 2);
            }
            if (fragment != null) {
                startAnimation = true;
                if (settings.resourcesProvider != null) {
                    if (messageDrawableOutStart == null) {
                        messageDrawableOutStart = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false, startColorsProvider);
                        messageDrawableOutStart.isCrossfadeBackground = true;
                        messageDrawableOutMediaStart = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, true, false, startColorsProvider);
                        messageDrawableOutMediaStart.isCrossfadeBackground = true;
                    }
                    startColorsProvider.saveColors(settings.resourcesProvider);
                }
                ArrayList<ThemeDescription> descriptions = fragment.getThemeDescriptions();
                addStartDescriptions(descriptions);
                if (fragment.visibleDialog instanceof BottomSheet) {
                    BottomSheet sheet = (BottomSheet) fragment.visibleDialog;
                    addStartDescriptions(sheet.getThemeDescriptions());
                } else if (fragment.visibleDialog instanceof AlertDialog) {
                    AlertDialog dialog = (AlertDialog) fragment.visibleDialog;
                    addStartDescriptions(dialog.getThemeDescriptions());
                }
                if (i == 0) {
                    if (settings.applyTheme) {
                        if (settings.accentId != -1) {
                            settings.theme.setCurrentAccentId(settings.accentId);
                            Theme.saveThemeAccents(settings.theme, true, false, true, false);
                        }
                        Theme.applyTheme(settings.theme, settings.nightTheme);
                    }
                    if (settings.afterStartDescriptionsAddedRunnable != null) {
                        settings.afterStartDescriptionsAddedRunnable.run();
                    }
                }
                addEndDescriptions(descriptions);
                if (fragment.visibleDialog instanceof BottomSheet) {
                    addEndDescriptions(((BottomSheet) fragment.visibleDialog).getThemeDescriptions());
                } else if (fragment.visibleDialog instanceof AlertDialog) {
                    addEndDescriptions(((AlertDialog) fragment.visibleDialog).getThemeDescriptions());
                }
            }
        }
        if (startAnimation) {
            int count = fragmentsStack.size() - (inPreviewMode || transitionAnimationPreviewMode ? 2 : 1);
            for (int a = 0; a < count; a++) {
                BaseFragment fragment = fragmentsStack.get(a);
                fragment.clearViews();
                fragment.setParentLayout(this);
            }
            if (settings.instant) {
                setThemeAnimationValue(1.0f);
                themeAnimatorDescriptions.clear();
                animateStartColors.clear();
                animateEndColors.clear();
                themeAnimatorDelegate.clear();
                presentingFragmentDescriptions = null;
                if (settings.afterAnimationRunnable != null) {
                    settings.afterAnimationRunnable.run();
                }
                return;
            }
            Theme.setAnimatingColor(true);
            if (settings.beforeAnimationRunnable != null) {
                settings.beforeAnimationRunnable.run();
            }
            animationProgressListener = settings.animationProgress;
            if (animationProgressListener != null) {
                animationProgressListener.setProgress(0);
            }
            themeAnimatorSet = new AnimatorSet();
            themeAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(themeAnimatorSet)) {
                        themeAnimatorDescriptions.clear();
                        animateStartColors.clear();
                        animateEndColors.clear();
                        themeAnimatorDelegate.clear();
                        Theme.setAnimatingColor(false);
                        presentingFragmentDescriptions = null;
                        themeAnimatorSet = null;
                        if (settings.afterAnimationRunnable != null) {
                            settings.afterAnimationRunnable.run();
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (animation.equals(themeAnimatorSet)) {
                        themeAnimatorDescriptions.clear();
                        animateStartColors.clear();
                        animateEndColors.clear();
                        themeAnimatorDelegate.clear();
                        Theme.setAnimatingColor(false);
                        presentingFragmentDescriptions = null;
                        themeAnimatorSet = null;
                        if (settings.afterAnimationRunnable != null) {
                            settings.afterAnimationRunnable.run();
                        }
                    }
                }
            });
            themeAnimatorSet.playTogether(ObjectAnimator.ofFloat(this, "themeAnimationValue", 0.0f, 1.0f));
            themeAnimatorSet.setDuration(settings.duration);
            themeAnimatorSet.start();
        }
    }

    public void rebuildAllFragmentViews(boolean last, boolean showLastAfter) {
        if (transitionAnimationInProgress || startedTracking) {
            rebuildAfterAnimation = true;
            rebuildLastAfterAnimation = last;
            showLastAfterAnimation = showLastAfter;
            return;
        }
        int size = fragmentsStack.size();
        if (!last) {
            size--;
        }
        if (inPreviewMode) {
            size--;
        }
        for (int a = 0; a < size; a++) {
            fragmentsStack.get(a).clearViews();
            fragmentsStack.get(a).setParentLayout(this);
        }
        if (delegate != null) {
            delegate.onRebuildAllFragments(this, last);
        }
        if (showLastAfter) {
            showLastFragment();
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && !checkTransitionAnimation() && !startedTracking && currentActionBar != null) {
            currentActionBar.onMenuButtonPressed();
        }
        return super.onKeyUp(keyCode, event);
    }

    public void onActionModeStarted(Object mode) {
        if (currentActionBar != null) {
            currentActionBar.setVisibility(GONE);
        }
        inActionMode = true;
    }

    public void onActionModeFinished(Object mode) {
        if (currentActionBar != null) {
            currentActionBar.setVisibility(VISIBLE);
        }
        inActionMode = false;
    }

    private void onCloseAnimationEnd() {
        if (transitionAnimationInProgress && onCloseAnimationEndRunnable != null) {
            transitionAnimationInProgress = false;
            layoutToIgnore = null;
            transitionAnimationPreviewMode = false;
            transitionAnimationStartTime = 0;
            newFragment = null;
            oldFragment = null;
            Runnable endRunnable = onCloseAnimationEndRunnable;
            onCloseAnimationEndRunnable = null;
            endRunnable.run();
            checkNeedRebuild();
            checkNeedRebuild();
        }
    }

    private void checkNeedRebuild() {
        if (rebuildAfterAnimation) {
            rebuildAllFragmentViews(rebuildLastAfterAnimation, showLastAfterAnimation);
            rebuildAfterAnimation = false;
        } else if (animateThemeAfterAnimation) {
            animateThemedValues(animateSetThemeAfterAnimation, animateSetThemeAccentIdAfterAnimation, animateSetThemeNightAfterAnimation, false);
            animateSetThemeAfterAnimation = null;
            animateThemeAfterAnimation = false;
        }
    }

    private void onOpenAnimationEnd() {
        if (transitionAnimationInProgress && onOpenAnimationEndRunnable != null) {
            transitionAnimationInProgress = false;
            layoutToIgnore = null;
            transitionAnimationPreviewMode = false;
            transitionAnimationStartTime = 0;
            newFragment = null;
            oldFragment = null;
            Runnable endRunnable = onOpenAnimationEndRunnable;
            onOpenAnimationEndRunnable = null;
            endRunnable.run();
            checkNeedRebuild();
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
                onCloseAnimationEnd();
            } else if (onOpenAnimationEndRunnable != null) {
                onOpenAnimationEnd();
            }
            containerView.invalidate();
        }
        if (intent != null) {
            parentActivity.startActivityForResult(intent, requestCode);
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

    public void setTitleOverlayText(String title, int titleId, Runnable action) {
        titleOverlayText = title;
        titleOverlayTextId = titleId;
        overlayAction = action;
        for (int a = 0; a < fragmentsStack.size(); a++) {
            BaseFragment fragment = fragmentsStack.get(a);
            if (fragment.actionBar != null) {
                fragment.actionBar.setTitleOverlayText(titleOverlayText, titleOverlayTextId, action);
            }
        }
    }

    public boolean extendActionMode(Menu menu) {
        return !fragmentsStack.isEmpty() && fragmentsStack.get(fragmentsStack.size() - 1).extendActionMode(menu);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setFragmentPanTranslationOffset(int offset) {
        if (containerView != null) {
            containerView.setFragmentPanTranslationOffset(offset);
        }
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

    private class StartColorsProvider implements Theme.ResourcesProvider {

        HashMap<String, Integer> colors = new HashMap<>();

        String[] keysToSave = new String[]{
                Theme.key_chat_outBubble,
                Theme.key_chat_outBubbleGradient1,
                Theme.key_chat_outBubbleGradient2,
                Theme.key_chat_outBubbleGradient3,
                Theme.key_chat_outBubbleGradientAnimated,
                Theme.key_chat_outBubbleShadow
        };

        @Override
        public Integer getColor(String key) {
            return colors.get(key);
        }

        @Override
        public Integer getCurrentColor(String key) {
            return colors.get(key);
        }

        public void saveColors(Theme.ResourcesProvider fragmentResourceProvider) {
            colors.clear();
            for (String key : keysToSave) {
                colors.put(key, fragmentResourceProvider.getCurrentColor(key));
            }
        }
    }
}
