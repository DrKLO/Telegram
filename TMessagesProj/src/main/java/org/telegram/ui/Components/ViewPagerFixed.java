package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class ViewPagerFixed extends FrameLayout {

    private Theme.ResourcesProvider resourcesProvider;
    int currentPosition;
    int nextPosition;
    protected View[] viewPages;
    private int[] viewTypes;
    private int[] pageIds;

    protected SparseArray<View> viewsByType = new SparseArray<>();

    private int startedTrackingPointerId;
    private int startedTrackingX;
    private int startedTrackingY;
    private VelocityTracker velocityTracker;

    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private float additionalOffset;
    private boolean backAnimation;
    private int maximumVelocity;
    private boolean startedTracking;
    private boolean maybeStartTracking;
    private static final Interpolator interpolator = t -> {
        --t;
        return t * t * t * t * t + 1.0F;
    };

    private final float touchSlop;

    private Adapter adapter;
    TabsView tabsView;

    ValueAnimator.AnimatorUpdateListener updateTabProgress = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            if (tabsAnimationInProgress) {
                float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                if (tabsView != null) {
                    tabsView.selectTab(nextPosition, currentPosition, 1f - scrollProgress);
                }
            }
        }
    };
    private Rect rect = new Rect();
    private boolean allowDisallowInterceptTouch = true;

    public ViewPagerFixed(@NonNull Context context) {
        this(context, null);
    }

    public ViewPagerFixed(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        touchSlop = AndroidUtilities.getPixelsInCM(0.3f, true);
        maximumVelocity = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();

        viewTypes = new int[2];
        viewPages = new View[2];
        setClipChildren(true);
    }

    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
        viewTypes[0] = adapter.getItemViewType(currentPosition);
        viewPages[0] = adapter.createView(viewTypes[0]);
        adapter.bindView(viewPages[0], currentPosition, viewTypes[0]);
        addView(viewPages[0]);
        viewPages[0].setVisibility(View.VISIBLE);
        fillTabs(false);
    }

    protected void onTabPageSelected(int position) {

    }

    protected int tabMarginDp() {
        return 16;
    }

    public TabsView createTabsView(boolean hasStableIds, int selectorType) {
        tabsView = new TabsView(getContext(), hasStableIds, selectorType, resourcesProvider) {
            @Override
            public void selectTab(int currentPosition, int nextPosition, float progress) {
                super.selectTab(currentPosition, nextPosition, progress);
                onTabPageSelected(progress <= 0.5f ? currentPosition : nextPosition);
            }
        };
        tabsView.tabMarginDp = tabMarginDp();
        tabsView.setDelegate(new TabsView.TabsViewDelegate() {
            @Override
            public void onPageSelected(int page, boolean forward) {
                animatingForward = forward;
                nextPosition = page;
                updateViewForIndex(1);

                onTabPageSelected(page);
                int trasnlationX = viewPages[0] != null ? viewPages[0].getMeasuredWidth() : 0;
                if (forward) {
                    viewPages[1].setTranslationX(trasnlationX);
                } else {
                    viewPages[1].setTranslationX(-trasnlationX);
                }
            }

            @Override
            public void onPageScrolled(float progress) {
                if (progress == 1f) {
                    if (viewPages[1] != null) {
                        swapViews();
                        viewsByType.put(viewTypes[1], viewPages[1]);
                        removeView(viewPages[1]);
                        viewPages[0].setTranslationX(0);
                        viewPages[1] = null;
                    }
                    return;
                }
                if (viewPages[1] == null) {
                    return;
                }
                if (animatingForward) {
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (1f - progress));
                    viewPages[0].setTranslationX(-viewPages[0].getMeasuredWidth() * progress);
                } else {
                    viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth() * (1f - progress));
                    viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * progress);
                }
            }

            @Override
            public void onSamePageSelected() {

            }

            @Override
            public boolean canPerformActions() {
                return !tabsAnimationInProgress && !startedTracking;
            }

            @Override
            public void invalidateBlur() {
                ViewPagerFixed.this.invalidateBlur();
            }
        });
        fillTabs(false);
        return tabsView;
    }

    protected void invalidateBlur() {

    }


    private void updateViewForIndex(int index) {
        int adapterPosition = index == 0 ? currentPosition : nextPosition;
        if (adapterPosition < 0 || adapterPosition >= adapter.getItemCount()) {
            return;
        }
        if (viewPages[index] == null) {
            viewTypes[index] = adapter.getItemViewType(adapterPosition);
            View v = viewsByType.get(viewTypes[index]);
            if (v == null) {
                v = adapter.createView(viewTypes[index]);
            } else {
                viewsByType.remove(viewTypes[index]);
            }
            if (v.getParent() != null) {
                ViewGroup parent = (ViewGroup) v.getParent();
                parent.removeView(v);
            }
            addView(v);
            viewPages[index] = v;
            adapter.bindView(viewPages[index], adapterPosition, viewTypes[index]);
            viewPages[index].setVisibility(View.VISIBLE);
        } else {
            if (viewTypes[index] == adapter.getItemViewType(adapterPosition)) {
                adapter.bindView(viewPages[index], adapterPosition, viewTypes[index]);
                viewPages[index].setVisibility(View.VISIBLE);
            } else {
                viewsByType.put(viewTypes[index], viewPages[index]);
                viewPages[index].setVisibility(View.GONE);
                removeView(viewPages[index]);
                viewTypes[index] = adapter.getItemViewType(adapterPosition);
                View v = viewsByType.get(viewTypes[index]);
                if (v == null) {
                    v = adapter.createView(viewTypes[index]);
                } else {
                    viewsByType.remove(viewTypes[index]);
                }
                addView(v);
                viewPages[index] = v;
                viewPages[index].setVisibility(View.VISIBLE);
                adapter.bindView(viewPages[index], adapterPosition, adapter.getItemViewType(adapterPosition));
            }
        }
    }

    protected void onBack() {

    }

    private float backProgress;
    protected boolean onBackProgress(float progress) {
        return false;
    }

    protected void fillTabs(boolean animated) {
        if (adapter != null && tabsView != null) {
            tabsView.removeTabs();
            for (int i = 0; i < adapter.getItemCount(); i++) {
                tabsView.addTab(adapter.getItemId(i), adapter.getItemTitle(i));
            }
            if (animated) {
                TransitionManager.beginDelayedTransition(tabsView.listView, TransitionExt.createSimpleTransition());
            }
            tabsView.finishAddingTabs();
        }
    }

    private boolean prepareForMoving(MotionEvent ev, boolean forward) {
        if ((!forward && currentPosition == 0 && !onBackProgress(backProgress = 0)) || (forward && currentPosition == adapter.getItemCount() - 1)) {
            return false;
        }

        getParent().requestDisallowInterceptTouchEvent(true);
        maybeStartTracking = false;
        startedTracking = true;
        startedTrackingX = (int) (ev.getX() + additionalOffset);
        if (tabsView != null) {
            tabsView.setEnabled(false);
        }

        animatingForward = forward;
        nextPosition = currentPosition + (forward ? 1 : -1);
        updateViewForIndex(1);
        if (viewPages[1] != null) {
            if (forward) {
                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
            } else {
                viewPages[1].setTranslationX(-viewPages[0].getMeasuredWidth());
            }
        }
        return true;
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (tabsView != null && tabsView.isAnimatingIndicator()) {
            return false;
        }
        if (checkTabsAnimationInProgress()) {
            return true;
        }
        onTouchEvent(ev);
        return startedTracking;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (allowDisallowInterceptTouch && maybeStartTracking && !startedTracking) {
            onTouchEvent(null);
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (tabsView != null && tabsView.animatingIndicator) {
            return false;
        }
        if (ev != null) {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(ev);
        }
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && checkTabsAnimationInProgress()) {
            startedTracking = true;
            startedTrackingPointerId = ev.getPointerId(0);
            startedTrackingX = (int) ev.getX();
            if (animatingForward) {
                if (startedTrackingX < viewPages[0].getMeasuredWidth() + viewPages[0].getTranslationX()) {
                    additionalOffset = viewPages[0].getTranslationX();
                } else {
                    swapViews();
                    animatingForward = false;
                    additionalOffset = viewPages[0].getTranslationX();
                }
            } else if (viewPages[1] != null) {
                if (startedTrackingX < viewPages[1].getMeasuredWidth() + viewPages[1].getTranslationX()) {
                    swapViews();
                    animatingForward = true;
                    additionalOffset = viewPages[0].getTranslationX();
                } else {
                    additionalOffset = viewPages[0].getTranslationX();
                }
            }
            tabsAnimation.removeAllListeners();
            tabsAnimation.cancel();
            tabsAnimationInProgress = false;
        } else if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
            additionalOffset = 0;
        }

        if (!startedTracking && ev != null) {
            View child = findScrollingChild(this, ev.getX(), ev.getY());
            if (child != null && (child.canScrollHorizontally(1) || child.canScrollHorizontally(-1))) {
                return false;
            }
        }
        if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
            startedTrackingPointerId = ev.getPointerId(0);
            maybeStartTracking = true;
            startedTrackingX = (int) ev.getX();
            startedTrackingY = (int) ev.getY();
        } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
            int dx = (int) (ev.getX() - startedTrackingX + additionalOffset);
            int dy = Math.abs((int) ev.getY() - startedTrackingY);
            if (startedTracking && (animatingForward && dx > 0 || !animatingForward && dx < 0)) {
                if (!prepareForMoving(ev, dx < 0)) {
                    maybeStartTracking = true;
                    startedTracking = false;
                    viewPages[0].setTranslationX(0);
                    if (viewPages[1] != null) {
                        viewPages[1].setTranslationX(animatingForward ? viewPages[0].getMeasuredWidth() : -viewPages[0].getMeasuredWidth());
                    }
                    if (tabsView != null) {
                        tabsView.selectTab(currentPosition, 0, 0);
                    }
                }
            }
            if (maybeStartTracking && !startedTracking) {
                int dxLocal = (int) (ev.getX() - startedTrackingX);
                if (Math.abs(dxLocal) >= touchSlop && Math.abs(dxLocal) > dy) {
                    prepareForMoving(ev, dx < 0);
                }
            } else if (startedTracking) {
                float scrollProgress = Math.abs(dx) / (float) viewPages[0].getMeasuredWidth();
                if (nextPosition == -1) {
                    onBackProgress(backProgress = scrollProgress);
                } else {
                    viewPages[0].setTranslationX(dx);
                    if (viewPages[1] != null) {
                        if (animatingForward) {
                            viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() + dx);
                        } else {
                            viewPages[1].setTranslationX(dx - viewPages[0].getMeasuredWidth());
                        }
                    }
                }
                if (tabsView != null) {
                    tabsView.selectTab(nextPosition, currentPosition, 1f - scrollProgress);
                }
            }
        } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
            }
            float velX;
            float velY;
            if (ev != null && ev.getAction() != MotionEvent.ACTION_CANCEL) {
                velX = velocityTracker.getXVelocity();
                velY = velocityTracker.getYVelocity();
                if (!startedTracking) {
                    if (Math.abs(velX) >= 3000 && Math.abs(velX) > Math.abs(velY)) {
                        prepareForMoving(ev, velX < 0);
                    }
                }
            } else {
                velX = 0;
                velY = 0;
            }
            if (startedTracking) {
                float x = viewPages[0].getX();
                tabsAnimation = new AnimatorSet();
                if (additionalOffset != 0) {
                    if (Math.abs(velX) > 1500) {
                        backAnimation = animatingForward ? velX > 0 : velX < 0;
                    } else {
                        if (animatingForward) {
                            if (viewPages[1] != null) {
                                backAnimation = (viewPages[1].getX() > (viewPages[0].getMeasuredWidth() >> 1));
                            } else {
                                backAnimation = false;
                            }
                        } else {
                            backAnimation = (viewPages[0].getX() < (viewPages[0].getMeasuredWidth() >> 1));
                        }
                    }
                } else {
                    backAnimation = Math.abs(x) < viewPages[0].getMeasuredWidth() / 3.0f && (Math.abs(velX) < 3500 || Math.abs(velX) < Math.abs(velY));
                }
                float distToMove;
                float dx = 0;
                if (backAnimation) {
                    dx = Math.abs(x);
                    if (animatingForward) {
                        tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, viewPages[1].getMeasuredWidth()));
                        }
                    } else {
                        tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, -viewPages[1].getMeasuredWidth()));
                        }
                    }
                } else if (nextPosition >= 0) {
                    dx = viewPages[0].getMeasuredWidth() - Math.abs(x);
                    if (animatingForward) {
                        tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, -viewPages[0].getMeasuredWidth()));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0));
                        }
                    } else {
                        tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, viewPages[0].getMeasuredWidth()));
                        if (viewPages[1] != null) {
                            tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, 0));
                        }
                    }
                }
                if (nextPosition < 0) {
                    ValueAnimator backAnimator = ValueAnimator.ofFloat(backProgress, backAnimation ? 0f : 1f);
                    backAnimator.addUpdateListener(anm -> {
                        onBackProgress(backProgress = (float) anm.getAnimatedValue());
                    });
                    tabsAnimation.playTogether(backAnimator);
                }
                ValueAnimator animator = ValueAnimator.ofFloat(0,1f);
                animator.addUpdateListener(updateTabProgress);
                tabsAnimation.playTogether(animator);
                tabsAnimation.setInterpolator(interpolator);

                int width = getMeasuredWidth();
                int halfWidth = width / 2;
                float distanceRatio = Math.min(1.0f, 1.0f * dx / (float) width);
                float distance = (float) halfWidth + (float) halfWidth * distanceInfluenceForSnapDuration(distanceRatio);
                velX = Math.abs(velX);
                int duration;
                if (velX > 0) {
                    duration = 4 * Math.round(1000.0f * Math.abs(distance / velX));
                } else {
                    float pageDelta = dx / getMeasuredWidth();
                    duration = (int) ((pageDelta + 1.0f) * 100.0f);
                }
                duration = Math.max(150, Math.min(duration, 600));

                tabsAnimation.setDuration(duration);
                tabsAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animator) {
                        tabsAnimation = null;
                        if (nextPosition < 0) {
                            onBack();
                        }
                        if (viewPages[1] != null) {
                            if (!backAnimation) {
                                swapViews();
                            }

                            viewsByType.put(viewTypes[1], viewPages[1]);
                            removeView(viewPages[1]);
                            viewPages[1].setVisibility(View.GONE);
                            viewPages[1] = null;
                        }
                        tabsAnimationInProgress = false;
                        maybeStartTracking = false;
                        if (tabsView != null) {
                            tabsView.setEnabled(true);
                        }
                    }
                });
                tabsAnimation.start();
                tabsAnimationInProgress = true;
                startedTracking = false;
            } else {
                maybeStartTracking = false;
                if (tabsView != null) {
                    tabsView.setEnabled(true);
                }
            }
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
        return startedTracking || maybeStartTracking;
    }

    private void swapViews() {
        View page = viewPages[0];
        viewPages[0] = viewPages[1];
        viewPages[1] = page;
        int p = currentPosition;
        currentPosition = nextPosition;
        nextPosition = p;
        p = viewTypes[0];
        viewTypes[0] = viewTypes[1];
        viewTypes[1] = p;

        onItemSelected(viewPages[0], viewPages[1], currentPosition, nextPosition);
    }

    void updatePages() {

    }


    public boolean checkTabsAnimationInProgress() {
        if (tabsAnimationInProgress) {
            boolean cancel = false;
            if (backAnimation) {
                if (Math.abs(viewPages[0].getTranslationX()) < 1) {
                    viewPages[0].setTranslationX(0);
                    if (viewPages[1] != null) {
                        viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? 1 : -1));
                    }
                    cancel = true;
                }
            } else if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                if (viewPages[1] != null) {
                    viewPages[1].setTranslationX(0);
                }
                cancel = true;
            }
            if (cancel) {
                //showScrollbars(true);
                if (tabsAnimation != null) {
                    tabsAnimation.cancel();
                    tabsAnimation = null;
                }
                tabsAnimationInProgress = false;
            }
            return tabsAnimationInProgress;
        }
        return false;
    }

    public static float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5F;
        f *= 0.47123894F;
        return (float) Math.sin(f);
    }

    public void setPosition(int position) {
        if (tabsAnimation != null) {
            tabsAnimation.cancel();
        }
        if (viewPages[1] != null) {
            viewsByType.put(viewTypes[1], viewPages[1]);
            removeView(viewPages[1]);
            viewPages[1] = null;
        }
        if (currentPosition != position) {
            int oldPosition = currentPosition;
            currentPosition = position;
            View oldView = viewPages[0];
            updateViewForIndex(0);
            onItemSelected(viewPages[0], oldView, currentPosition, oldPosition);
            viewPages[0].setTranslationX(0);
            if (tabsView != null) {
                tabsView.selectTab(position, 0, 1f);
            }
        }
    }

    protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {

    }

    public View[] getViewPages() {
        return viewPages;
    }

    public boolean isCurrentTabFirst() {
        return currentPosition == 0;
    }

    public void rebuild(boolean animated) {
        onTouchEvent(null);
        if (!adapter.hasStableId()) {
            animated = false;
        }
        if (tabsAnimation != null) {
            tabsAnimation.cancel();
            tabsAnimation = null;
        }
        if (viewPages[1] != null) {
            removeView(viewPages[1]);
            viewPages[1] = null;
        }
        viewPages[1] = viewPages[0];

        int oldId = viewPages[1] == null || viewPages[1].getTag() == null ? 0 : (int) viewPages[1].getTag();
        boolean toRight = true;
        if (adapter.getItemCount() == 0) {
            if (viewPages[1] != null) {
                removeView(viewPages[1]);
                viewPages[1] = null;
            }
            if (viewPages[0] != null) {
                removeView(viewPages[0]);
                viewPages[0] = null;
            }
            return;
        }
        if (currentPosition > adapter.getItemCount() - 1) {
            currentPosition = adapter.getItemCount() - 1;
        }
        if (currentPosition < 0) {
            currentPosition = 0;
        }
        viewTypes[0] = adapter.getItemViewType(currentPosition);
        viewPages[0] = adapter.createView(viewTypes[0]);
        adapter.bindView(viewPages[0], currentPosition, viewTypes[0]);
        addView(viewPages[0]);
        viewPages[0].setVisibility(View.VISIBLE);


        int newId = viewPages[0].getTag() == null ? 0 : (int) viewPages[0].getTag();
        if (newId == oldId) {
            animated = false;
        }

        if (animated) {
            tabsView.saveFromValues();
        }
        fillTabs(animated);
        if (animated) {
            tabsAnimation = new AnimatorSet();
            if (viewPages[1] != null) {
                viewPages[1].setTranslationX(0);
            }
            if (!toRight) {
                if (viewPages[0] != null) {
                    viewPages[0].setTranslationX(getMeasuredWidth());
                }
                if (viewPages[1] != null) {
                    tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, -getMeasuredWidth()));
                }
            } else {
                if (viewPages[0] != null) {
                    viewPages[0].setTranslationX(-getMeasuredWidth());
                }
                if (viewPages[1] != null) {
                    tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[1], View.TRANSLATION_X, getMeasuredWidth()));
                }
            }
            if (viewPages[0] != null) {
                tabsAnimation.playTogether(ObjectAnimator.ofFloat(viewPages[0], View.TRANSLATION_X, 0));
            }

            tabsView.indicatorProgress2 = 0;
            tabsView.listView.invalidateViews();
            tabsView.invalidate();
            ValueAnimator animator = ValueAnimator.ofFloat(0,1f);
            animator.addUpdateListener(animation -> {
                updateTabProgress.onAnimationUpdate(animation);
                tabsView.indicatorProgress2 = (float) animation.getAnimatedValue();
                tabsView.listView.invalidateViews();
                tabsView.invalidate();
            });
            tabsAnimation.playTogether(animator);
            tabsAnimation.setInterpolator(interpolator);
            tabsAnimation.setDuration(220);
            tabsAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    tabsAnimation = null;
                    if (viewPages[1] != null) {
                        removeView(viewPages[1]);
                        viewPages[1] = null;
                    }
                    tabsAnimationInProgress = false;
                    if (tabsView != null) {
                        tabsView.setEnabled(true);
                        tabsView.animatingIndicator = false;
                        tabsView.indicatorProgress2 = 1f;
                        tabsView.listView.invalidateViews();
                        tabsView.invalidate();
                    }
                }
            });
            tabsView.setEnabled(false);
            tabsAnimationInProgress = true;
            tabsAnimation.start();

        } else {
            if (viewPages[1] != null) {
                removeView(viewPages[1]);
                viewPages[1] = null;
            }
        }
    }

    public abstract static class Adapter {
        public abstract int getItemCount();
        public abstract View createView(int viewType);
        public abstract void bindView(View view, int position, int viewType);

        public int getItemId(int position) {
            return position;
        }

        public String getItemTitle(int position) {
            return "";
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public boolean hasStableId() {
            return false;
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (direction == 0) {
            return false;
        }
        if (tabsAnimationInProgress || startedTracking) {
            return true;
        }
        boolean forward = direction > 0;
        if ((!forward && currentPosition == 0) || (forward && currentPosition == adapter.getItemCount() - 1)) {
            return false;
        }
        return true;
    }

    public View getCurrentView() {
        return viewPages[0];
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public static class TabsView extends FrameLayout {

        private float overrideFromX;
        private float overrideFromW;
        private float indicatorProgress2 = 1f;

        public interface TabsViewDelegate {
            void onPageSelected(int page, boolean forward);
            void onPageScrolled(float progress);
            void onSamePageSelected();
            void invalidateBlur();
            boolean canPerformActions();
        }

        private static class Tab {
            public int id;
            public String title;
            public int titleWidth;
            public int counter;
            public float alpha = 1f;

            public Tab(int i, String t) {
                id = i;
                title = t;
            }

            public int getWidth(boolean store, TextPaint textPaint) {
                int width = titleWidth = (int) Math.ceil(textPaint.measureText(title));
                return Math.max(AndroidUtilities.dp(40), width);
            }

            public boolean setTitle(String newTitle) {
                if (TextUtils.equals(title, newTitle)) {
                    return false;
                }
                title = newTitle;
                return true;
            }
        }

        public class TabView extends View {

            private Tab currentTab;
            private int textHeight;
            private int tabWidth;
            private int currentPosition;
            private RectF rect = new RectF();
            private String currentText;
            private StaticLayout textLayout;
            private int textOffsetX;

            public TabView(Context context) {
                super(context);
            }

            public void setTab(Tab tab, int position) {
                currentTab = tab;
                currentPosition = position;
                setContentDescription(tab.title);
                setAlpha(tab.alpha);
                requestLayout();
            }

            public int getId() {
                return currentTab.id;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int w = currentTab.getWidth(false, textPaint) + AndroidUtilities.dp(tabMarginDp * 2) + additionalTabWidth;
                setMeasuredDimension(w, MeasureSpec.getSize(heightMeasureSpec));
            }

            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(Canvas canvas) {
                if (currentTab.id != Integer.MAX_VALUE && editingAnimationProgress != 0) {
                    canvas.save();
                    float p = editingAnimationProgress * (currentPosition % 2 == 0 ? 1.0f : -1.0f);
                    canvas.translate(AndroidUtilities.dp(0.66f) * p, 0);
                    canvas.rotate(p, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
                }
                int key;
                int animateToKey;
                int otherKey;
                int animateToOtherKey;
                int unreadKey;
                int unreadOtherKey;
                int id1;
                int id2;
                if (manualScrollingToId != -1) {
                    id1 = manualScrollingToId;
                    id2 = selectedTabId;
                } else {
                    id1 = selectedTabId;
                    id2 = previousId;
                }
                if (currentTab.id == id1) {
                    key = activeTextColorKey;
                    otherKey = unactiveTextColorKey;
                    unreadKey = Theme.key_chats_tabUnreadActiveBackground;
                    unreadOtherKey = Theme.key_chats_tabUnreadUnactiveBackground;
                } else {
                    key = unactiveTextColorKey;
                    otherKey = activeTextColorKey;
                    unreadKey = Theme.key_chats_tabUnreadUnactiveBackground;
                    unreadOtherKey = Theme.key_chats_tabUnreadActiveBackground;
                }

                if ((animatingIndicator || manualScrollingToId != -1) && (currentTab.id == id1 || currentTab.id == id2)) {
                    textPaint.setColor(ColorUtils.blendARGB(Theme.getColor(otherKey, resourcesProvider), Theme.getColor(key, resourcesProvider), animatingIndicatorProgress));
                } else {
                    textPaint.setColor(Theme.getColor(key, resourcesProvider));
                }


                int counterWidth;
                int countWidth;
                String counterText;
                if (currentTab.counter > 0) {
                    counterText = String.format("%d", currentTab.counter);
                    counterWidth = (int) Math.ceil(textCounterPaint.measureText(counterText));
                    countWidth = Math.max(AndroidUtilities.dp(10), counterWidth) + AndroidUtilities.dp(10);
                } else {
                    counterText = null;
                    counterWidth = 0;
                    countWidth = 0;
                }

                if (currentTab.id != Integer.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0)) {
                    countWidth = (int) (countWidth + (AndroidUtilities.dp(20) - countWidth) * editingStartAnimationProgress);
                }

                tabWidth = currentTab.titleWidth + (countWidth != 0 ? countWidth + AndroidUtilities.dp(6 * (counterText != null ? 1.0f : editingStartAnimationProgress)) : 0);
                int textX = (getMeasuredWidth() - tabWidth) / 2;
                if (!TextUtils.equals(currentTab.title, currentText)) {
                    currentText = currentTab.title;
                    CharSequence text = Emoji.replaceEmoji(currentText, textPaint.getFontMetricsInt(), AndroidUtilities.dp(15), false);
                    textLayout = new StaticLayout(text, textPaint, AndroidUtilities.dp(400), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0, false);
                    textHeight = textLayout.getHeight();
                    textOffsetX = (int) -textLayout.getLineLeft(0);
                }
                if (textLayout != null) {
                    canvas.save();
                    canvas.translate(textX + textOffsetX, (getMeasuredHeight() - textHeight) / 2 + 1);
                    textLayout.draw(canvas);
                    canvas.restore();
                }

                if (counterText != null || currentTab.id != Integer.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0)) {
                    textCounterPaint.setColor(Theme.getColor(backgroundColorKey, resourcesProvider));
                    if (Theme.hasThemeKey(unreadKey) && Theme.hasThemeKey(unreadOtherKey)) {
                        int color1 = Theme.getColor(unreadKey, resourcesProvider);
                        if ((animatingIndicator || manualScrollingToPosition != -1) && (currentTab.id == id1 || currentTab.id == id2)) {
                            int color3 = Theme.getColor(unreadOtherKey, resourcesProvider);
                            counterPaint.setColor(ColorUtils.blendARGB(color3, color1, animatingIndicatorProgress));
                        } else {
                            counterPaint.setColor(color1);
                        }
                    } else {
                        counterPaint.setColor(textPaint.getColor());
                    }

                    int x = textX + currentTab.titleWidth + AndroidUtilities.dp(6);
                    int countTop = (getMeasuredHeight() - AndroidUtilities.dp(20)) / 2;

                    if (currentTab.id != Integer.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0) && counterText == null) {
                        counterPaint.setAlpha((int) (editingStartAnimationProgress * 255));
                    } else {
                        counterPaint.setAlpha(255);
                    }

                    rect.set(x, countTop, x + countWidth, countTop + AndroidUtilities.dp(20));
                    canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, counterPaint);

                    if (counterText != null) {
                        if (currentTab.id != Integer.MAX_VALUE) {
                            textCounterPaint.setAlpha((int) (255 * (1.0f - editingStartAnimationProgress)));
                        }
                        canvas.drawText(counterText, rect.left + (rect.width() - counterWidth) / 2, countTop + AndroidUtilities.dp(14.5f), textCounterPaint);
                    }
                    if (currentTab.id != Integer.MAX_VALUE && (isEditing || editingStartAnimationProgress != 0)) {
                        deletePaint.setColor(textCounterPaint.getColor());
                        deletePaint.setAlpha((int) (255 * editingStartAnimationProgress));
                        int side = AndroidUtilities.dp(3);
                        canvas.drawLine(rect.centerX() - side, rect.centerY() - side, rect.centerX() + side, rect.centerY() + side, deletePaint);
                        canvas.drawLine(rect.centerX() - side, rect.centerY() + side, rect.centerX() + side, rect.centerY() - side, deletePaint);
                    }
                }
                if (currentTab.id != Integer.MAX_VALUE && editingAnimationProgress != 0) {
                    canvas.restore();
                }
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setSelected(currentTab != null && selectedTabId != -1 && currentTab.id == selectedTabId);
            }
        }

        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private TextPaint textCounterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private Paint deletePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private ArrayList<Tab> tabs = new ArrayList<>();

        private Bitmap crossfadeBitmap;
        private Paint crossfadePaint = new Paint();
        private float crossfadeAlpha;
        private boolean commitCrossfade;

        private boolean isEditing;
        private long lastEditingAnimationTime;
        private boolean editingForwardAnimation;
        private float editingAnimationProgress;
        private float editingStartAnimationProgress;

        public int tabMarginDp = 16;
        private boolean orderChanged;

        private boolean ignoreLayout;

        private RecyclerListView listView;
        private LinearLayoutManager layoutManager;
        private ListAdapter adapter;

        private TabsViewDelegate delegate;

        private int currentPosition;
        private int selectedTabId = -1;
        private int allTabsWidth;

        private int additionalTabWidth;

        private boolean animatingIndicator;
        private float animatingIndicatorProgress;
        private int manualScrollingToPosition = -1;
        private int manualScrollingToId = -1;

        private int scrollingToChild = -1;
        private GradientDrawable selectorDrawable;

        private int tabLineColorKey = Theme.key_profile_tabSelectedLine;
        private int activeTextColorKey = Theme.key_profile_tabSelectedText;
        private int unactiveTextColorKey = Theme.key_profile_tabText;
        private int selectorColorKey = Theme.key_profile_tabSelector;
        private int backgroundColorKey = Theme.key_actionBarDefault;

        private int prevLayoutWidth;

        private boolean invalidated;

        private boolean isInHiddenMode;
        private float hideProgress;

        private CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT_QUINT;

        private SparseIntArray positionToId = new SparseIntArray(5);
        private SparseIntArray idToPosition = new SparseIntArray(5);
        private SparseIntArray positionToWidth = new SparseIntArray(5);
        private SparseIntArray positionToX = new SparseIntArray(5);

        private boolean animationRunning;
        private long lastAnimationTime;
        private float animationTime;
        private int previousPosition;
        private int previousId;
        private Runnable animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!animatingIndicator) {
                    return;
                }
                long newTime = SystemClock.elapsedRealtime();
                long dt = (newTime - lastAnimationTime);
                if (dt > 17) {
                    dt = 17;
                }
                animationTime += dt / 200.0f;
                setAnimationIdicatorProgress(interpolator.getInterpolation(animationTime));
                if (animationTime > 1.0f) {
                    animationTime = 1.0f;
                }
                if (animationTime < 1.0f) {
                    AndroidUtilities.runOnUIThread(animationRunnable);
                } else {
                    animatingIndicator = false;
                    setEnabled(true);
                    if (delegate != null) {
                        delegate.onPageScrolled(1.0f);
                    }
                }
            }
        };

        private Theme.ResourcesProvider resourcesProvider;

        ValueAnimator tabsAnimator;
        private float animationValue;

        public TabsView(Context context) {
            this(context, false, 8, null);
        }

        public TabsView(Context context, boolean hasStableIds, int tabsSelectorType, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            textCounterPaint.setTextSize(AndroidUtilities.dp(13));
            textCounterPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textPaint.setTextSize(AndroidUtilities.dp(15));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            deletePaint.setStyle(Paint.Style.STROKE);
            deletePaint.setStrokeCap(Paint.Cap.ROUND);
            deletePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));

            selectorDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null);
            float rad = AndroidUtilities.dpf2(3);
            selectorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
            selectorDrawable.setColor(Theme.getColor(tabLineColorKey, resourcesProvider));

            setHorizontalScrollBarEnabled(false);
            listView = new RecyclerListView(context) {

                @Override
                public void addView(View child, int index, ViewGroup.LayoutParams params) {
                    super.addView(child, index, params);
                    if (isInHiddenMode) {
                        child.setScaleX(0.3f);
                        child.setScaleY(0.3f);
                        child.setAlpha(0);
                    } else {
                        child.setScaleX(1f);
                        child.setScaleY(1f);
                        child.setAlpha(1f);
                    }
                }

                @Override
                public void setAlpha(float alpha) {
                    super.setAlpha(alpha);
                    TabsView.this.invalidate();
                }

                @Override
                protected boolean canHighlightChildAt(View child, float x, float y) {
                    if (isEditing) {
                        TabView tabView = (TabView) child;
                        int side = AndroidUtilities.dp(6);
                        if (tabView.rect.left - side < x && tabView.rect.right + side > x) {
                            return false;
                        }
                    }
                    return super.canHighlightChildAt(child, x, y);
                }
            };
            if (hasStableIds) {
                listView.setItemAnimator(null);
            } else {
                ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
            }

            listView.setSelectorType(tabsSelectorType);
            if (tabsSelectorType == 3) {
                listView.setSelectorRadius(0);
            } else {
                listView.setSelectorRadius(6);
            }
            listView.setSelectorDrawableColor(Theme.getColor(selectorColorKey, resourcesProvider));
            listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false) {
                @Override
                public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                    LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                        @Override
                        protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                            int dx = calculateDxToMakeVisible(targetView, getHorizontalSnapPreference());
                            if (dx > 0 || dx == 0 && targetView.getLeft() - AndroidUtilities.dp(21) < 0) {
                                dx += AndroidUtilities.dp(60);
                            } else if (dx < 0 || dx == 0 && targetView.getRight() + AndroidUtilities.dp(21) > getMeasuredWidth()) {
                                dx -= AndroidUtilities.dp(60);
                            }

                            final int dy = calculateDyToMakeVisible(targetView, getVerticalSnapPreference());
                            final int distance = (int) Math.sqrt(dx * dx + dy * dy);
                            final int time = Math.max(180, calculateTimeForDeceleration(distance));
                            if (time > 0) {
                                action.update(-dx, -dy, time, mDecelerateInterpolator);
                            }
                        }
                    };
                    linearSmoothScroller.setTargetPosition(position);
                    startSmoothScroll(linearSmoothScroller);
                }

                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull RecyclerView.Recycler recycler, @NonNull RecyclerView.State state, @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(recycler, state, info);
                    if (isInHiddenMode) {
                        info.setVisibleToUser(false);
                    }
                }
            });
            listView.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
            listView.setClipToPadding(false);
            listView.setDrawSelectorBehind(true);
            adapter = new ListAdapter(context);
            adapter.setHasStableIds(hasStableIds);
            listView.setAdapter(adapter);
            listView.setOnItemClickListener((view, position, x, y) -> {
                if (!delegate.canPerformActions()) {
                    return;
                }
                TabView tabView = (TabView) view;
                if (position == currentPosition && delegate != null) {
                    delegate.onSamePageSelected();
                    return;
                }
                scrollToTab(tabView.currentTab.id, position);
            });
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    invalidate();
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        public void setDelegate(TabsViewDelegate filterTabsViewDelegate) {
            delegate = filterTabsViewDelegate;
        }

        public boolean isAnimatingIndicator() {
            return animatingIndicator;
        }

        public int getCurrentPosition() {
            return currentPosition;
        }

        public int getPreviousPosition() {
            return previousPosition;
        }

        public float getAnimatingIndicatorProgress() {
            return animatingIndicatorProgress;
        }

        public void scrollToTab(int id, int position) {
            boolean scrollingForward = currentPosition < position;
            scrollingToChild = -1;
            previousPosition = currentPosition;
            previousId = selectedTabId;
            currentPosition = position;
            selectedTabId = id;

            if (tabsAnimator != null) {
                tabsAnimator.cancel();
            }
            if (animatingIndicator) {
                animatingIndicator = false;
            }

            animationTime = 0;
            animatingIndicatorProgress = 0;
            animatingIndicator = true;
            setEnabled(false);


            if (delegate != null) {
                delegate.onPageSelected(position, scrollingForward);
            }
            scrollToChild(position);
            tabsAnimator = ValueAnimator.ofFloat(0,1f);
            tabsAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float progress = (float) valueAnimator.getAnimatedValue();
                    setAnimationIdicatorProgress(progress);
                    if (delegate != null) {
                        delegate.onPageScrolled(progress);
                    }
                }
            });
            tabsAnimator.setDuration(250);
            tabsAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            tabsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatingIndicator = false;
                    setEnabled(true);
                    if (delegate != null) {
                        delegate.onPageScrolled(1.0f);
                    }
                    invalidate();
                }
            });
            tabsAnimator.start();
        }

        public void setAnimationIdicatorProgress(float value) {
            animatingIndicatorProgress = value;
            listView.invalidateViews();
            invalidate();
            if (delegate != null) {
                delegate.onPageScrolled(value);
            }
        }

        public Drawable getSelectorDrawable() {
            return selectorDrawable;
        }

        public RecyclerListView getTabsContainer() {
            return listView;
        }

        public int getNextPageId(boolean forward) {
            return positionToId.get(currentPosition + (forward ? 1 : -1), -1);
        }

        public void addTab(int id, String text) {
            int position = tabs.size();
            if (position == 0 && selectedTabId == -1) {
                selectedTabId = id;
            }
            positionToId.put(position, id);
            idToPosition.put(id, position);
            if (selectedTabId != -1 && selectedTabId == id) {
                currentPosition = position;
            }
            Tab tab = new Tab(id, text);
            allTabsWidth += tab.getWidth(true, textPaint) + AndroidUtilities.dp(tabMarginDp * 2);
            tabs.add(tab);
        }

        public void removeTabs() {
            tabs.clear();
            positionToId.clear();
            idToPosition.clear();
            positionToWidth.clear();
            positionToX.clear();
            allTabsWidth = 0;
        }

        public void finishAddingTabs() {
            adapter.notifyDataSetChanged();
        }

        public int getCurrentTabId() {
            return selectedTabId;
        }

        public int getFirstTabId() {
            return positionToId.get(0, 0);
        }

        private void updateTabsWidths() {
            positionToX.clear();
            positionToWidth.clear();
            int xOffset = AndroidUtilities.dp(7);
            for (int a = 0, N = tabs.size(); a < N; a++) {
                int tabWidth = tabs.get(a).getWidth(false, textPaint);
                positionToWidth.put(a, tabWidth);
                positionToX.put(a, xOffset + additionalTabWidth / 2);
                xOffset += tabWidth + AndroidUtilities.dp(tabMarginDp * 2) + additionalTabWidth;
            }
        }

        float lastDrawnIndicatorX;
        float lastDrawnIndicatorW;
        private void saveFromValues() {
            overrideFromX = lastDrawnIndicatorX;
            overrideFromW = lastDrawnIndicatorW;
        }


        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean result = super.drawChild(canvas, child, drawingTime);
            if (child == listView) {
                final int height = getMeasuredHeight();
                boolean invalidate = false;
                if (isInHiddenMode && hideProgress != 1f) {
                    hideProgress += 0.1f;
                    if (hideProgress > 1f) {
                        hideProgress = 1f;
                    }
                    invalidate();
                } else if (!isInHiddenMode && hideProgress != 0) {
                    hideProgress -= 0.12f;
                    if (hideProgress < 0) {
                        hideProgress = 0;
                    }
                    invalidate();
                }
                selectorDrawable.setAlpha((int) (255 * listView.getAlpha()));
                int indicatorX = 0;
                int indicatorWidth = 0;
                if (animatingIndicator || manualScrollingToPosition != -1) {
                    int position = layoutManager.findFirstVisibleItemPosition();
                    if (position != RecyclerListView.NO_POSITION) {
                        RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(position);
                        if (holder != null) {
                            int idx1;
                            int idx2;
                            if (animatingIndicator) {
                                idx1 = previousPosition;
                                idx2 = currentPosition;
                            } else {
                                idx1 = currentPosition;
                                idx2 = manualScrollingToPosition;
                            }
                            int prevX = positionToX.get(idx1);
                            int newX = positionToX.get(idx2);
                            int prevW = positionToWidth.get(idx1);
                            int newW = positionToWidth.get(idx2);
                            if (additionalTabWidth != 0) {
                                indicatorX = (int) (prevX + (newX - prevX) * animatingIndicatorProgress) + AndroidUtilities.dp(tabMarginDp);
                            } else {
                                int x = positionToX.get(position);
                                indicatorX = (int) (prevX + (newX - prevX) * animatingIndicatorProgress) - (x - holder.itemView.getLeft()) + AndroidUtilities.dp(tabMarginDp);
                            }
                            indicatorWidth = (int) (prevW + (newW - prevW) * animatingIndicatorProgress);
                        }
                    }
                } else {
                    RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(currentPosition);
                    if (holder != null) {
                        TabView tabView = (TabView) holder.itemView;
                        indicatorWidth = Math.max(AndroidUtilities.dp(40), tabView.tabWidth);
                        indicatorX = (int) (tabView.getX() + (tabView.getMeasuredWidth() - indicatorWidth) / 2);
                    }
                }
                if (indicatorWidth != 0) {
                    lastDrawnIndicatorX = indicatorX;
                    lastDrawnIndicatorW = indicatorWidth;
                    if (indicatorProgress2 != 1f) {
                        indicatorX = (int) AndroidUtilities.lerp(lastDrawnIndicatorX, indicatorX, indicatorProgress2);
                        indicatorWidth = (int) AndroidUtilities.lerp(lastDrawnIndicatorW, indicatorWidth, indicatorProgress2);
                    }
                    selectorDrawable.setBounds(indicatorX, (int) (height - AndroidUtilities.dpr(4) + hideProgress * AndroidUtilities.dpr(4)), indicatorX + indicatorWidth, (int) (height + hideProgress * AndroidUtilities.dpr(4)));
                    selectorDrawable.draw(canvas);
                }
                if (crossfadeBitmap != null) {
                    crossfadePaint.setAlpha((int) (crossfadeAlpha * 255));
                    canvas.drawBitmap(crossfadeBitmap, 0, 0, crossfadePaint);
                }
            }

            return result;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (!tabs.isEmpty()) {
                int width = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(7) - AndroidUtilities.dp(7);
                int prevWidth = additionalTabWidth;
                if (tabs.size() == 1) {
                    additionalTabWidth = 0;
                } else {
                    additionalTabWidth = allTabsWidth < width ? (width - allTabsWidth) / tabs.size() : 0;
                }
                if (prevWidth != additionalTabWidth) {
                    ignoreLayout = true;
                    adapter.notifyDataSetChanged();
                    ignoreLayout = false;
                }
                updateTabsWidths();
                invalidated = false;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        public void updateColors() {
            selectorDrawable.setColor(Theme.getColor(tabLineColorKey, resourcesProvider));
            listView.invalidateViews();
            listView.invalidate();
            invalidate();
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        private void scrollToChild(int position) {
            if (tabs.isEmpty() || scrollingToChild == position || position < 0 || position >= tabs.size()) {
                return;
            }
            scrollingToChild = position;
            listView.smoothScrollToPosition(position);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);

            if (prevLayoutWidth != r - l) {
                prevLayoutWidth = r - l;
                scrollingToChild = -1;
                if (animatingIndicator) {
                    AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                    animatingIndicator = false;
                    setEnabled(true);
                    if (delegate != null) {
                        delegate.onPageScrolled(1.0f);
                    }
                }
            }
        }
        public void selectTab(int currentPosition, int nextPosition, float progress) {
            if (progress < 0) {
                progress = 0;
            } else if (progress > 1.0f) {
                progress = 1.0f;
            }

            this.currentPosition = currentPosition;
            selectedTabId = positionToId.get(currentPosition);

            if (progress > 0) {
                manualScrollingToPosition = nextPosition;
                manualScrollingToId = positionToId.get(nextPosition);
            } else {
                manualScrollingToPosition = -1;
                manualScrollingToId = -1;
            }
            animatingIndicatorProgress = progress;
            listView.invalidateViews();
            invalidate();
            scrollToChild(currentPosition);

            if (progress >= 1.0f) {
                manualScrollingToPosition = -1;
                manualScrollingToId = -1;
                this.currentPosition = nextPosition;
                selectedTabId = positionToId.get(nextPosition);
            }
            if (delegate != null) {
                delegate.invalidateBlur();
            }
        }

        public void selectTabWithId(int id, float progress) {
            int position = idToPosition.get(id, -1);
            if (position < 0) {
                return;
            }
            if (progress < 0) {
                progress = 0;
            } else if (progress > 1.0f) {
                progress = 1.0f;
            }

            if (progress > 0) {
                manualScrollingToPosition = position;
                manualScrollingToId = id;
            } else {
                manualScrollingToPosition = -1;
                manualScrollingToId = -1;
            }
            animatingIndicatorProgress = progress;
            listView.invalidateViews();
            invalidate();
            scrollToChild(position);

            if (progress >= 1.0f) {
                manualScrollingToPosition = -1;
                manualScrollingToId = -1;
                currentPosition = position;
                selectedTabId = id;
            }
        }

        private int getChildWidth(TextView child) {
            Layout layout = child.getLayout();
            if (layout != null) {
                int w = (int) Math.ceil(layout.getLineWidth(0)) + AndroidUtilities.dp(2);
                if (child.getCompoundDrawables()[2] != null) {
                    w += child.getCompoundDrawables()[2].getIntrinsicWidth() + AndroidUtilities.dp(6);
                }
                return w;
            } else {
                return child.getMeasuredWidth();
            }
        }

        public void onPageScrolled(int position, int first) {
            if (currentPosition == position) {
                return;
            }
            currentPosition = position;
            if (position >= tabs.size()) {
                return;
            }
            if (first == position && position > 1) {
                scrollToChild(position - 1);
            } else {
                scrollToChild(position);
            }
            invalidate();
        }

        public boolean isEditing() {
            return isEditing;
        }

        public void setIsEditing(boolean value) {
            isEditing = value;
            editingForwardAnimation = true;
            listView.invalidateViews();
            invalidate();
            if (!isEditing && orderChanged) {
                MessagesStorage.getInstance(UserConfig.selectedAccount).saveDialogFiltersOrder();
                TLRPC.TL_messages_updateDialogFiltersOrder req = new TLRPC.TL_messages_updateDialogFiltersOrder();
                ArrayList<MessagesController.DialogFilter> filters = MessagesController.getInstance(UserConfig.selectedAccount).dialogFilters;
                for (int a = 0, N = filters.size(); a < N; a++) {
                    MessagesController.DialogFilter filter = filters.get(a);
                    req.order.add(filters.get(a).id);
                }
                ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {

                });
                orderChanged = false;
            }
        }

        private class ListAdapter extends RecyclerListView.SelectionAdapter {

            private Context mContext;

            public ListAdapter(Context context) {
                mContext = context;
            }

            @Override
            public int getItemCount() {
                return tabs.size();
            }

            @Override
            public long getItemId(int i) {
                return tabs.get(i).id;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(new TabView(mContext));
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                TabView tabView = (TabView) holder.itemView;
                tabView.setTab(tabs.get(position), position);
            }

            @Override
            public int getItemViewType(int i) {
                return 0;
            }

        }

        public void hide(boolean hide, boolean animated) {
            isInHiddenMode = hide;

            if (animated) {
                for (int i = 0; i < listView.getChildCount(); i++) {
                    listView.getChildAt(i).animate().alpha(hide ? 0 : 1f).scaleX(hide ? 0 : 1f).scaleY(hide ? 0 : 1f).setInterpolator(CubicBezierInterpolator.DEFAULT).setDuration(220).start();
                }
            } else {
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View v = listView.getChildAt(i);
                    v.setScaleX(hide ? 0 : 1f);
                    v.setScaleY(hide ? 0 : 1f);
                    v.setAlpha(hide ? 0 : 1f);
                }
                hideProgress = hide ? 1 : 0;
            }
            invalidate();
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

    public void drawForBlur(Canvas blurCanvas) {
        for (int i = 0 ; i < viewPages.length; i++) {
            if (viewPages[i] != null && viewPages[i].getVisibility() == View.VISIBLE) {
                RecyclerListView recyclerListView = findRecyclerView(viewPages[i]);
                if (recyclerListView != null) {
                    for (int j = 0; j < recyclerListView.getChildCount(); j++) {
                        View child = recyclerListView.getChildAt(j);
                        if (child.getY() < AndroidUtilities.dp(203) + AndroidUtilities.dp(100)) {
                            int restore = blurCanvas.save();
                            blurCanvas.translate(viewPages[i].getX(), getY() + viewPages[i].getY() + recyclerListView.getY() + child.getY());
                            child.draw(blurCanvas);
                            blurCanvas.restoreToCount(restore);
                        }
                    }
                }
            }
        }
    }

    private RecyclerListView findRecyclerView(View view) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof RecyclerListView) {
                    return (RecyclerListView) child;
                } else if (child instanceof ViewGroup) {
                    findRecyclerView(child);
                }
            }
        }
        return null;
    }


    public void setAllowDisallowInterceptTouch(boolean allowDisallowInterceptTouch) {
        this.allowDisallowInterceptTouch = allowDisallowInterceptTouch;
    }
}
