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
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

public class ActionBarPopupWindow extends PopupWindow {

    private static Method layoutInScreenMethod;
    private static final Field superListenerField;
    private static final boolean allowAnimation = Build.VERSION.SDK_INT >= 18;
    private static DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private AnimatorSet windowAnimatorSet;
    private boolean animationEnabled = allowAnimation;
    private int dismissAnimationDuration = 150;
    private boolean isClosingAnimated;
    private int currentAccount = UserConfig.selectedAccount;
    private boolean pauseNotifications;
    private long outEmptyTime = -1;

    static {
        Field f = null;
        try {
            f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            /* ignored */
        }
        superListenerField = f;
    }

    private static final ViewTreeObserver.OnScrollChangedListener NOP = () -> {
        /* do nothing */
    };

    private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
    private ViewTreeObserver mViewTreeObserver;
    private int popupAnimationIndex = -1;

    public interface OnDispatchKeyEventListener {
        void onDispatchKeyEvent(KeyEvent keyEvent);
    }

    public static class ActionBarPopupWindowLayout extends FrameLayout {
        public final static int FLAG_USE_SWIPEBACK = 1;

        private OnDispatchKeyEventListener mOnDispatchKeyEventListener;
        private float backScaleX = 1;
        private float backScaleY = 1;
        private int backAlpha = 255;
        private int lastStartedChild = 0;
        private boolean shownFromBotton;
        private boolean animationEnabled = allowAnimation;
        private ArrayList<AnimatorSet> itemAnimators;
        private HashMap<View, Integer> positions = new HashMap<>();
        private int gapStartY = -1000000;
        private int gapEndY = -1000000;
        private Rect bgPaddings = new Rect();

        private PopupSwipeBackLayout swipeBackLayout;
        private ScrollView scrollView;
        protected LinearLayout linearLayout;

        private int backgroundColor = Color.WHITE;
        protected Drawable backgroundDrawable;

        private boolean fitItems;
        private final Theme.ResourcesProvider resourcesProvider;

        public ActionBarPopupWindowLayout(Context context) {
            this(context, null);
        }

        public ActionBarPopupWindowLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, R.drawable.popup_fixed_alert2, resourcesProvider);
        }

        public ActionBarPopupWindowLayout(Context context, int resId, Theme.ResourcesProvider resourcesProvider) {
            this(context, resId, resourcesProvider, 0);
        }

        public ActionBarPopupWindowLayout(Context context, int resId, Theme.ResourcesProvider resourcesProvider, int flags) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            backgroundDrawable = getResources().getDrawable(resId).mutate();
            if (backgroundDrawable != null) {
                backgroundDrawable.getPadding(bgPaddings);
            }
            setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

            setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
            setWillNotDraw(false);

            if ((flags & FLAG_USE_SWIPEBACK) > 0) {
                swipeBackLayout = new PopupSwipeBackLayout(context, resourcesProvider);
                addView(swipeBackLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }

            try {
                scrollView = new ScrollView(context);
                scrollView.setVerticalScrollBarEnabled(false);
                if (swipeBackLayout != null) {
                    swipeBackLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                } else addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            } catch (Throwable e) {
                FileLog.e(e);
            }

            linearLayout = new LinearLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    if (fitItems) {
                        int maxWidth = 0;
                        int fixWidth = 0;
                        gapStartY = -1000000;
                        gapEndY = -1000000;
                        ArrayList<View> viewsToFix = null;
                        for (int a = 0, N = getChildCount(); a < N; a++) {
                            View view = getChildAt(a);
                            if (view.getVisibility() == GONE) {
                                continue;
                            }
                            Object tag = view.getTag(R.id.width_tag);
                            Object tag2 = view.getTag(R.id.object_tag);
                            if (tag != null) {
                                view.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
                            }
                            measureChildWithMargins(view, widthMeasureSpec, 0, heightMeasureSpec, 0);
                            if (!(tag instanceof Integer) && tag2 == null) {
                                maxWidth = Math.max(maxWidth, view.getMeasuredWidth());
                                continue;
                            } else if (tag instanceof Integer) {
                                fixWidth = Math.max((Integer) tag, view.getMeasuredWidth());
                                gapStartY = view.getMeasuredHeight();
                                gapEndY = gapStartY + AndroidUtilities.dp(6);
                            }
                            if (viewsToFix == null) {
                                viewsToFix = new ArrayList<>();
                            }
                            viewsToFix.add(view);
                        }
                        if (viewsToFix != null) {
                            for (int a = 0, N = viewsToFix.size(); a < N; a++) {
                                viewsToFix.get(a).getLayoutParams().width = Math.max(maxWidth, fixWidth);
                            }
                        }
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            if (scrollView != null) {
                scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else if (swipeBackLayout != null) {
                swipeBackLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            } else {
                addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }
        }

        @Nullable
        public PopupSwipeBackLayout getSwipeBack() {
            return swipeBackLayout;
        }

        public int addViewToSwipeBack(View v) {
            swipeBackLayout.addView(v);
            return swipeBackLayout.getChildCount() - 1;
        }

        public void setFitItems(boolean value) {
            fitItems = value;
        }

        public void setShownFromBotton(boolean value) {
            shownFromBotton = value;
        }

        public void setDispatchKeyEventListener(OnDispatchKeyEventListener listener) {
            mOnDispatchKeyEventListener = listener;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public void setBackgroundColor(int color) {
            if (backgroundColor != color) {
                backgroundDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor = color, PorterDuff.Mode.MULTIPLY));
            }
        }

        @Keep
        public void setBackAlpha(int value) {
            backAlpha = value;
        }

        @Keep
        public int getBackAlpha() {
            return backAlpha;
        }

        @Keep
        public void setBackScaleX(float value) {
            backScaleX = value;
            invalidate();
        }

        @Keep
        public void setBackScaleY(float value) {
            backScaleY = value;
            if (animationEnabled) {
                int height = getMeasuredHeight() - AndroidUtilities.dp(16);
                if (shownFromBotton) {
                    for (int a = lastStartedChild; a >= 0; a--) {
                        View child = getItemAt(a);
                        if (child.getVisibility() != VISIBLE) {
                            continue;
                        }
                        Integer position = positions.get(child);
                        if (position != null && height - (position * AndroidUtilities.dp(48) + AndroidUtilities.dp(32)) > value * height) {
                            break;
                        }
                        lastStartedChild = a - 1;
                        startChildAnimation(child);
                    }
                } else {
                    int count = getItemsCount();
                    int h = 0;
                    for (int a = 0; a < count; a++) {
                        View child = getItemAt(a);
                        if (child.getVisibility() != VISIBLE) {
                            continue;
                        }
                        h += child.getMeasuredHeight();
                        if (a < lastStartedChild) {
                            continue;
                        }
                        Integer position = positions.get(child);
                        if (position != null && h - AndroidUtilities.dp(24) > value * height) {
                            break;
                        }
                        lastStartedChild = a + 1;
                        startChildAnimation(child);
                    }
                }
            }
            invalidate();
        }

        public void setBackgroundDrawable(Drawable drawable) {
            backgroundColor = Color.WHITE;
            backgroundDrawable = drawable;
            if (backgroundDrawable != null) {
                backgroundDrawable.getPadding(bgPaddings);
            }
        }

        private void startChildAnimation(View child) {
            if (animationEnabled) {
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(child, View.ALPHA, 0f, child.isEnabled() ? 1f : 0.5f),
                        ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, AndroidUtilities.dp(shownFromBotton ? 6 : -6), 0));
                animatorSet.setDuration(180);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        itemAnimators.remove(animatorSet);
                    }
                });
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.start();
                if (itemAnimators == null) {
                    itemAnimators = new ArrayList<>();
                }
                itemAnimators.add(animatorSet);
            }
        }

        public void setAnimationEnabled(boolean value) {
            animationEnabled = value;
        }

        @Override
        public void addView(View child) {
            linearLayout.addView(child);
        }

        public void addView(View child, LinearLayout.LayoutParams layoutParams) {
            linearLayout.addView(child, layoutParams);
        }

        public void removeInnerViews() {
            linearLayout.removeAllViews();
        }

        public float getBackScaleX() {
            return backScaleX;
        }

        public float getBackScaleY() {
            return backScaleY;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (mOnDispatchKeyEventListener != null) {
                mOnDispatchKeyEventListener.onDispatchKeyEvent(event);
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (backgroundDrawable != null) {
                int start = gapStartY - scrollView.getScrollY();
                int end = gapEndY - scrollView.getScrollY();
                for (int a = 0; a < 2; a++) {
                    if (a == 1 && start < -AndroidUtilities.dp(16)) {
                        break;
                    }
                    if (gapStartY != -1000000) {
                        canvas.save();
                        canvas.clipRect(0, bgPaddings.top, getMeasuredWidth(), getMeasuredHeight());
                    }
                    backgroundDrawable.setAlpha(backAlpha);
                    if (shownFromBotton) {
                        final int height = getMeasuredHeight();
                        backgroundDrawable.setBounds(0, (int) (height * (1.0f - backScaleY)), (int) (getMeasuredWidth() * backScaleX), height);
                    } else {
                        if (start > -AndroidUtilities.dp(16)) {
                            int h = (int) (getMeasuredHeight() * backScaleY);
                            if (a == 0) {
                                backgroundDrawable.setBounds(0, -scrollView.getScrollY() + (gapStartY != -1000000 ? AndroidUtilities.dp(1) : 0), (int) (getMeasuredWidth() * backScaleX), (gapStartY != -1000000 ? Math.min(h, start + AndroidUtilities.dp(16)) : h));
                            } else {
                                if (h < end) {
                                    if (gapStartY != -1000000) {
                                        canvas.restore();
                                    }
                                    continue;
                                }
                                backgroundDrawable.setBounds(0, end, (int) (getMeasuredWidth() * backScaleX), h);
                            }
                        } else {
                            backgroundDrawable.setBounds(0, gapStartY < 0 ? 0 : -AndroidUtilities.dp(16), (int) (getMeasuredWidth() * backScaleX), (int) (getMeasuredHeight() * backScaleY));
                        }
                    }
                    backgroundDrawable.draw(canvas);
                    if (gapStartY != -1000000) {
                        canvas.restore();
                    }
                }
            }
        }

        public Drawable getBackgroundDrawable() {
            return backgroundDrawable;
        }

        public int getItemsCount() {
            return linearLayout.getChildCount();
        }

        public View getItemAt(int index) {
            return linearLayout.getChildAt(index);
        }

        public void scrollToTop() {
            if (scrollView != null) {
                scrollView.scrollTo(0, 0);
            }
        }

        public void setupRadialSelectors(int color) {
            int count = linearLayout.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = linearLayout.getChildAt(a);
                child.setBackground(Theme.createRadSelectorDrawable(color, a == 0 ? 6 : 0, a == count - 1 ? 6 : 0));
            }
        }

        public void updateRadialSelectors() {
            int count = linearLayout.getChildCount();
            View firstVisible = null;
            View lastVisible = null;
            for (int a = 0; a < count; a++) {
                View child = linearLayout.getChildAt(a);
                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (firstVisible == null) {
                    firstVisible = child;
                }
                lastVisible = child;
            }

            boolean prevGap = false;
            for (int a = 0; a < count; a++) {
                View child = linearLayout.getChildAt(a);
                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                Object tag = child.getTag(R.id.object_tag);
                if (child instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) child).updateSelectorBackground(child == firstVisible || prevGap, child == lastVisible);
                }
                if (tag != null) {
                    prevGap = true;
                } else {
                    prevGap = false;
                }
            }
        }

        private int getThemedColor(String key) {
            Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
            return color != null ? color : Theme.getColor(key);
        }
    }

    public ActionBarPopupWindow() {
        super();
        init();
    }

    public ActionBarPopupWindow(Context context) {
        super(context);
        init();
    }

    public ActionBarPopupWindow(int width, int height) {
        super(width, height);
        init();
    }

    public ActionBarPopupWindow(View contentView) {
        super(contentView);
        init();
    }

    public ActionBarPopupWindow(View contentView, int width, int height, boolean focusable) {
        super(contentView, width, height, focusable);
        init();
    }

    public ActionBarPopupWindow(View contentView, int width, int height) {
        super(contentView, width, height);
        init();
    }

    public void setAnimationEnabled(boolean value) {
        animationEnabled = value;
    }

    @SuppressWarnings("PrivateAPI")
    public void setLayoutInScreen(boolean value) {
        try {
            if (layoutInScreenMethod == null) {
                layoutInScreenMethod = PopupWindow.class.getDeclaredMethod("setLayoutInScreenEnabled", boolean.class);
                layoutInScreenMethod.setAccessible(true);
            }
            layoutInScreenMethod.invoke(this, true);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void init() {
        if (superListenerField != null) {
            try {
                mSuperScrollListener = (ViewTreeObserver.OnScrollChangedListener) superListenerField.get(this);
                superListenerField.set(this, NOP);
            } catch (Exception e) {
                mSuperScrollListener = null;
            }
        }
    }

    public void setDismissAnimationDuration(int value) {
        dismissAnimationDuration = value;
    }

    private void unregisterListener() {
        if (mSuperScrollListener != null && mViewTreeObserver != null) {
            if (mViewTreeObserver.isAlive()) {
                mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
            }
            mViewTreeObserver = null;
        }
    }

    private void registerListener(View anchor) {
        if (mSuperScrollListener != null) {
            ViewTreeObserver vto = (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver() : null;
            if (vto != mViewTreeObserver) {
                if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                if ((mViewTreeObserver = vto) != null) {
                    vto.addOnScrollChangedListener(mSuperScrollListener);
                }
            }
        }
    }

    public void dimBehind() {
        View container = getContentView().getRootView();
        Context context = getContentView().getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
        p.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        p.dimAmount = 0.2f;
        wm.updateViewLayout(container, p);
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        try {
            super.showAsDropDown(anchor, xoff, yoff);
            registerListener(anchor);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void startAnimation() {
        if (animationEnabled) {
            if (windowAnimatorSet != null) {
                return;
            }

            ViewGroup viewGroup = (ViewGroup) getContentView();
            ActionBarPopupWindowLayout content = null;
            if (viewGroup instanceof ActionBarPopupWindowLayout) {
                content = (ActionBarPopupWindowLayout) viewGroup;
            } else {
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    if (viewGroup.getChildAt(i) instanceof ActionBarPopupWindowLayout) {
                        content = (ActionBarPopupWindowLayout) viewGroup.getChildAt(i);
                    }
                }
            }
            content.setTranslationY(0);
            content.setAlpha(1.0f);
            content.setPivotX(content.getMeasuredWidth());
            content.setPivotY(0);
            int count = content.getItemsCount();
            content.positions.clear();
            int visibleCount = 0;
            for (int a = 0; a < count; a++) {
                View child = content.getItemAt(a);
                child.setAlpha(0.0f);
                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                content.positions.put(child, visibleCount);
                visibleCount++;
            }
            if (content.shownFromBotton) {
                content.lastStartedChild = count - 1;
            } else {
                content.lastStartedChild = 0;
            }
            windowAnimatorSet = new AnimatorSet();
            windowAnimatorSet.playTogether(
                    ObjectAnimator.ofFloat(content, "backScaleY", 0.0f, 1.0f),
                    ObjectAnimator.ofInt(content, "backAlpha", 0, 255));
            windowAnimatorSet.setDuration(150 + 16 * visibleCount);
            windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    windowAnimatorSet = null;
                    ViewGroup viewGroup = (ViewGroup) getContentView();
                    ActionBarPopupWindowLayout content = null;
                    if (viewGroup instanceof ActionBarPopupWindowLayout) {
                        content = (ActionBarPopupWindowLayout) viewGroup;
                    } else {
                        for (int i = 0; i < viewGroup.getChildCount(); i++) {
                            if (viewGroup.getChildAt(i) instanceof ActionBarPopupWindowLayout) {
                                content = (ActionBarPopupWindowLayout) viewGroup.getChildAt(i);
                            }
                        }
                    }
                    int count = content.getItemsCount();
                    for (int a = 0; a < count; a++) {
                        View child = content.getItemAt(a);
                        child.setAlpha(child.isEnabled() ? 1f : 0.5f);
                    }
                }
            });
            windowAnimatorSet.start();
        }
    }

    @Override
    public void update(View anchor, int xoff, int yoff, int width, int height) {
        super.update(anchor, xoff, yoff, width, height);
        registerListener(anchor);
    }

    @Override
    public void update(View anchor, int width, int height) {
        super.update(anchor, width, height);
        registerListener(anchor);
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
        unregisterListener();
    }

    @Override
    public void dismiss() {
        dismiss(true);
    }

    public void setPauseNotifications(boolean value) {
        pauseNotifications = value;
    }

    public void dismiss(boolean animated) {
        setFocusable(false);
        if (windowAnimatorSet != null) {
            if (animated && isClosingAnimated) {
                return;
            }
            windowAnimatorSet.cancel();
            windowAnimatorSet = null;
        }
        isClosingAnimated = false;
        if (animationEnabled && animated) {
            isClosingAnimated = true;
            ViewGroup viewGroup = (ViewGroup) getContentView();
            ActionBarPopupWindowLayout content = null;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                if (viewGroup.getChildAt(i) instanceof ActionBarPopupWindowLayout) {
                    content = (ActionBarPopupWindowLayout) viewGroup.getChildAt(i);
                }
            }
            if (content != null) {
                if (content.itemAnimators != null && !content.itemAnimators.isEmpty()) {
                    for (int a = 0, N = content.itemAnimators.size(); a < N; a++) {
                        AnimatorSet animatorSet = content.itemAnimators.get(a);
                        animatorSet.removeAllListeners();
                        animatorSet.cancel();
                    }
                    content.itemAnimators.clear();
                }
            }
            windowAnimatorSet = new AnimatorSet();
            if (outEmptyTime > 0) {
                windowAnimatorSet.playTogether(ValueAnimator.ofFloat(0, 1f));
                windowAnimatorSet.setDuration(outEmptyTime);
            } else {
                windowAnimatorSet.playTogether(
                        ObjectAnimator.ofFloat(viewGroup, View.TRANSLATION_Y, AndroidUtilities.dp((content != null && content.shownFromBotton) ? 5 : -5)),
                        ObjectAnimator.ofFloat(viewGroup, View.ALPHA, 0.0f));
                windowAnimatorSet.setDuration(dismissAnimationDuration);
            }

            windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    windowAnimatorSet = null;
                    isClosingAnimated = false;
                    setFocusable(false);
                    try {
                        ActionBarPopupWindow.super.dismiss();
                    } catch (Exception ignore) {

                    }
                    unregisterListener();
                    if (pauseNotifications) {
                        NotificationCenter.getInstance(currentAccount).onAnimationFinish(popupAnimationIndex);
                    }
                }
            });
            if (pauseNotifications) {
                popupAnimationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(popupAnimationIndex, null);
            }
            windowAnimatorSet.start();
        } else {
            try {
                super.dismiss();
            } catch (Exception ignore) {

            }
            unregisterListener();
        }
    }

    public void setEmptyOutAnimation(long time) {
        outEmptyTime = time;
    }
}