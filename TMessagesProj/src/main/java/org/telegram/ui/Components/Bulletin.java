package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Property;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.util.Consumer;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Bulletin {

    public static final int DURATION_SHORT = 1500;
    public static final int DURATION_LONG = 2750;
    public static final int DURATION_PROLONG = 5000;

    public static final int TYPE_STICKER = 0;
    public static final int TYPE_ERROR = 1;
    public static final int TYPE_BIO_CHANGED = 2;
    public static final int TYPE_NAME_CHANGED = 3;
    public static final int TYPE_ERROR_SUBTITLE = 4;
    public static final int TYPE_APP_ICON = 5;
    public static final int TYPE_SUCCESS = 6;

    public int tag;
    public int hash;
    private View.OnLayoutChangeListener containerLayoutListener;
    private SpringAnimation bottomOffsetSpring;

    public static Bulletin make(@NonNull FrameLayout containerLayout, @NonNull Layout contentLayout, int duration) {
        if (containerLayout == null) return new EmptyBulletin();
        return new Bulletin(null, containerLayout, contentLayout, duration);
    }

    public Bulletin setOnClickListener(View.OnClickListener onClickListener) {
        if (layout != null) {
            layout.setOnClickListener(onClickListener);
        }
        return this;
    }

    @SuppressLint("RtlHardcoded")
    public static Bulletin make(@Nullable BaseFragment fragment, @NonNull Layout contentLayout, int duration) {
        if (fragment == null) {
            return new EmptyBulletin();
        }
        if (fragment instanceof ChatActivity) {
            contentLayout.setWideScreenParams(ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        } else if (fragment instanceof DialogsActivity) {
            contentLayout.setWideScreenParams(ViewGroup.LayoutParams.MATCH_PARENT, Gravity.NO_GRAVITY);
        }
        return new Bulletin(fragment, fragment.getLayoutContainer(), contentLayout, duration);
    }

    public static Bulletin find(@NonNull FrameLayout containerLayout) {
        for (int i = 0, size = containerLayout.getChildCount(); i < size; i++) {
            final View view = containerLayout.getChildAt(i);
            if (view instanceof Layout) {
                return ((Layout) view).bulletin;
            }
        }
        return null;
    }

    public static void hide(@NonNull FrameLayout containerLayout) {
        hide(containerLayout, true);
    }

    public static void hide(@NonNull FrameLayout containerLayout, boolean animated) {
        final Bulletin bulletin = find(containerLayout);
        if (bulletin != null) {
            bulletin.hide(animated && isTransitionsEnabled(), 0);
        }
    }

    private static final HashMap<FrameLayout, Delegate> delegates = new HashMap<>();
    private static final HashMap<BaseFragment, Delegate> fragmentDelegates = new HashMap<>();

    @SuppressLint("StaticFieldLeak")
    private static Bulletin visibleBulletin;

    private final Layout layout;
    private final ParentLayout parentLayout;
    private final BaseFragment containerFragment;
    private final FrameLayout containerLayout;
    private final Runnable hideRunnable = this::hide;
    private int duration;

    private boolean showing;
    private boolean canHide;
    private boolean loaded = true;
    public int currentBottomOffset;
    public int lastBottomOffset;
    private Delegate currentDelegate;
    private Layout.Transition layoutTransition;
    public boolean hideAfterBottomSheet = true;

    private Bulletin() {
        layout = null;
        parentLayout = null;
        containerFragment = null;
        containerLayout = null;
    }

    private Bulletin(BaseFragment fragment, @NonNull FrameLayout containerLayout, @NonNull Layout layout, int duration) {
        this.layout = layout;
        this.loaded = !(this.layout instanceof LoadingLayout);
        this.parentLayout = new ParentLayout(layout) {
            @Override
            protected void onPressedStateChanged(boolean pressed) {
                setCanHide(!pressed);
                if (containerLayout.getParent() != null) {
                    containerLayout.getParent().requestDisallowInterceptTouchEvent(pressed);
                }
            }

            @Override
            protected void onHide() {
                hide();
            }
        };
        this.containerFragment = fragment;
        this.containerLayout = containerLayout;
        this.duration = duration;
    }

    public static Bulletin getVisibleBulletin() {
        return visibleBulletin;
    }

    public static void hideVisible() {
        if (visibleBulletin != null) {
            visibleBulletin.hide();
        }
    }

    public static void hideVisible(ViewGroup container) {
        if (visibleBulletin != null && visibleBulletin.containerLayout == container) {
            visibleBulletin.hide();
        }
    }

    public Bulletin setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public Bulletin hideAfterBottomSheet(boolean hide) {
        this.hideAfterBottomSheet = hide;
        return this;
    }

    public Bulletin setTag(int tag) {
        this.tag = tag;
        return this;
    }

    public Bulletin show() {
        return show(false);
    }

    public Bulletin show(boolean top) {
        if (!showing && containerLayout != null) {
            showing = true;
            layout.setTop(top);

            CharSequence text = layout.getAccessibilityText();
            if (text != null) {
                AndroidUtilities.makeAccessibilityAnnouncement(text);
            }

            if (layout.getParent() != parentLayout) {
                throw new IllegalStateException("Layout has incorrect parent");
            }

            if (visibleBulletin != null) {
                visibleBulletin.hide();
            }
            visibleBulletin = this;
            layout.onAttach(this);

            containerLayout.addOnLayoutChangeListener(containerLayoutListener = (v, left, top1, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (currentDelegate != null && !currentDelegate.allowLayoutChanges()) {
                    return;
                }
                if (!top) {
                    int newOffset = currentDelegate != null ? currentDelegate.getBottomOffset(tag) : 0;
                    if (lastBottomOffset != newOffset) {
                        if (bottomOffsetSpring == null || !bottomOffsetSpring.isRunning()) {
                            bottomOffsetSpring = new SpringAnimation(new FloatValueHolder(lastBottomOffset))
                                    .setSpring(new SpringForce()
                                            .setFinalPosition(newOffset)
                                            .setStiffness(900f)
                                            .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
                            bottomOffsetSpring.addUpdateListener((animation, value, velocity) -> {
                                lastBottomOffset = (int) value;
                                updatePosition();
                            });
                            bottomOffsetSpring.addEndListener((animation, canceled, value, velocity) -> {
                                if (bottomOffsetSpring == animation) {
                                    bottomOffsetSpring = null;
                                }
                            });
                        } else {
                            bottomOffsetSpring.getSpring().setFinalPosition(newOffset);
                        }
                        bottomOffsetSpring.start();
                    }
                }
            });

            layout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int t, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    layout.removeOnLayoutChangeListener(this);
                    if (showing) {
                        layout.onShow();
                        currentDelegate = findDelegate(containerFragment, containerLayout);
                        if (bottomOffsetSpring == null || !bottomOffsetSpring.isRunning()) {
                            lastBottomOffset = currentDelegate != null ? currentDelegate.getBottomOffset(tag) : 0;
                        }
                        if (currentDelegate != null) {
                            currentDelegate.onShow(Bulletin.this);
                        }
                        if (isTransitionsEnabled()) {
                            ensureLayoutTransitionCreated();
                            layout.transitionRunningEnter = true;
                            layout.delegate = currentDelegate;
                            layout.invalidate();
                            layoutTransition.animateEnter(layout, layout::onEnterTransitionStart, () -> {
                                layout.transitionRunningEnter = false;
                                layout.onEnterTransitionEnd();
                                setCanHide(true);
                            }, offset -> {
                                if (currentDelegate != null && !top) {
                                    currentDelegate.onBottomOffsetChange(layout.getHeight() - offset);
                                }
                            }, currentBottomOffset);
                        } else {
                            if (currentDelegate != null && !top) {
                                currentDelegate.onBottomOffsetChange(layout.getHeight() - currentBottomOffset);
                            }
                            updatePosition();
                            layout.onEnterTransitionStart();
                            layout.onEnterTransitionEnd();
                            setCanHide(true);
                        }
                    }
                }
            });

            layout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    layout.removeOnAttachStateChangeListener(this);
                    hide(false, 0);
                }
            });

            containerLayout.addView(parentLayout);
        }
        return this;
    }

    public void setCanHide(boolean canHide) {
        canHide = canHide && loaded;
        if (this.canHide != canHide && layout != null) {
            this.canHide = canHide;
            if (canHide) {
                if (duration >= 0) {
                    layout.postDelayed(hideRunnable, duration);
                }
            } else {
                layout.removeCallbacks(hideRunnable);
            }
        }
    }

    private Runnable onHideListener;

    public Bulletin setOnHideListener(Runnable listener) {
        this.onHideListener = listener;
        return this;
    }

    private void ensureLayoutTransitionCreated() {
        if (layout != null && layoutTransition == null) {
            layoutTransition = layout.createTransition();
        }
    }

    public void hide() {
        hide(isTransitionsEnabled(), 0);
    }

    public void hide(long duration) {
        hide(isTransitionsEnabled(), duration);
    }

    public void hide(boolean animated, long duration) {
        if (layout == null) {
            return;
        }
        if (showing) {
            showing = false;

            if (visibleBulletin == this) {
                visibleBulletin = null;
            }

            int bottomOffset = currentBottomOffset;
            currentBottomOffset = 0;

            if (ViewCompat.isLaidOut(layout)) {
                layout.removeCallbacks(hideRunnable);
                if (animated) {
                    layout.transitionRunningExit = true;
                    layout.delegate = currentDelegate;
                    layout.invalidate();
                    if (duration >= 0) {
                        Layout.DefaultTransition transition = new Layout.DefaultTransition();
                        transition.duration = duration;
                        layoutTransition = transition;
                    } else {
                        ensureLayoutTransitionCreated();
                    }
                    layoutTransition.animateExit(layout, layout::onExitTransitionStart, () -> {
                        if (currentDelegate != null && !layout.top) {
                            currentDelegate.onBottomOffsetChange(0);
                            currentDelegate.onHide(this);
                        }
                        layout.transitionRunningExit = false;
                        layout.onExitTransitionEnd();
                        layout.onHide();
                        containerLayout.removeView(parentLayout);
                        containerLayout.removeOnLayoutChangeListener(containerLayoutListener);
                        layout.onDetach();

                        if (onHideListener != null) {
                            onHideListener.run();
                        }
                    }, offset -> {
                        if (currentDelegate != null && !layout.top) {
                            currentDelegate.onBottomOffsetChange(layout.getHeight() - offset);
                        }
                    }, bottomOffset);
                    return;
                }
            }

            if (currentDelegate != null && !layout.top) {
                currentDelegate.onBottomOffsetChange(0);
                currentDelegate.onHide(this);
            }
            layout.onExitTransitionStart();
            layout.onExitTransitionEnd();
            layout.onHide();
            if (containerLayout != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    containerLayout.removeView(parentLayout);
                    containerLayout.removeOnLayoutChangeListener(containerLayoutListener);
                });
            }
            layout.onDetach();

            if (onHideListener != null) {
                onHideListener.run();
            }
        }
    }

    public boolean isShowing() {
        return showing;
    }

    public Layout getLayout() {
        return layout;
    }

    private static boolean isTransitionsEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("view_animations", true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    }

    public void updatePosition() {
        if (layout != null) {
            layout.updatePosition();
        }
    }

    @Retention(SOURCE)
    @IntDef(value = {ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT})
    private @interface WidthDef {
    }

    @Retention(SOURCE)
    @SuppressLint("RtlHardcoded")
    @IntDef(value = {Gravity.LEFT, Gravity.RIGHT, Gravity.CENTER_HORIZONTAL, Gravity.NO_GRAVITY})
    private @interface GravityDef {
    }

    public static abstract class ParentLayout extends FrameLayout {

        private final Layout layout;
        private final Rect rect = new Rect();
        private final GestureDetector gestureDetector;

        private boolean pressed;
        private float translationX;
        private boolean hideAnimationRunning;
        private boolean needLeftAlphaAnimation;
        private boolean needRightAlphaAnimation;

        public ParentLayout(Layout layout) {
            super(layout.getContext());
            this.layout = layout;
            gestureDetector = new GestureDetector(layout.getContext(), new GestureDetector.SimpleOnGestureListener() {

                @Override
                public boolean onDown(MotionEvent e) {
                    if (!hideAnimationRunning) {
                        needLeftAlphaAnimation = layout.isNeedSwipeAlphaAnimation(true);
                        needRightAlphaAnimation = layout.isNeedSwipeAlphaAnimation(false);
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    layout.setTranslationX(translationX -= distanceX);
                    if (translationX == 0 || (translationX < 0f && needLeftAlphaAnimation) || (translationX > 0f && needRightAlphaAnimation)) {
                        layout.setAlpha(1f - Math.abs(translationX) / layout.getWidth());
                    }
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (Math.abs(velocityX) > 2000f) {
                        final boolean needAlphaAnimation = (velocityX < 0f && needLeftAlphaAnimation) || (velocityX > 0f && needRightAlphaAnimation);

                        final SpringAnimation springAnimation = new SpringAnimation(layout, DynamicAnimation.TRANSLATION_X, Math.signum(velocityX) * layout.getWidth() * 2f);
                        if (!needAlphaAnimation) {
                            springAnimation.addEndListener((animation, canceled, value, velocity) -> onHide());
                            springAnimation.addUpdateListener(((animation, value, velocity) -> {
                                if (Math.abs(value) > layout.getWidth()) {
                                    animation.cancel();
                                }
                            }));
                        }
                        springAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
                        springAnimation.getSpring().setStiffness(100f);
                        springAnimation.setStartVelocity(velocityX);
                        springAnimation.start();

                        if (needAlphaAnimation) {
                            final SpringAnimation springAnimation2 = new SpringAnimation(layout, DynamicAnimation.ALPHA, 0f);
                            springAnimation2.addEndListener((animation, canceled, value, velocity) -> onHide());
                            springAnimation2.addUpdateListener(((animation, value, velocity) -> {
                                if (value <= 0f) {
                                    animation.cancel();
                                }
                            }));
                            springAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);
                            springAnimation.getSpring().setStiffness(10f);
                            springAnimation.setStartVelocity(velocityX);
                            springAnimation2.start();
                        }

                        hideAnimationRunning = true;
                        return true;
                    }
                    return false;
                }
            });
            gestureDetector.setIsLongpressEnabled(false);
            addView(layout);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (pressed || inLayoutHitRect(event.getX(), event.getY())) {
                gestureDetector.onTouchEvent(event);
                final int actionMasked = event.getActionMasked();
                if (actionMasked == MotionEvent.ACTION_DOWN) {
                    if (!pressed && !hideAnimationRunning) {
                        layout.animate().cancel();
                        translationX = layout.getTranslationX();
                        onPressedStateChanged(pressed = true);
                    }
                } else if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
                    if (pressed) {
                        if (!hideAnimationRunning) {
                            if (Math.abs(translationX) > layout.getWidth() / 3f) {
                                final float tx = Math.signum(translationX) * layout.getWidth();
                                final boolean needAlphaAnimation = (translationX < 0f && needLeftAlphaAnimation) || (translationX > 0f && needRightAlphaAnimation);
                                layout.animate().translationX(tx).alpha(needAlphaAnimation ? 0f : 1f).setDuration(200).setInterpolator(AndroidUtilities.accelerateInterpolator).withEndAction(() -> {
                                    if (layout.getTranslationX() == tx) {
                                        onHide();
                                    }
                                }).start();
                            } else {
                                layout.animate().translationX(0).alpha(1f).setDuration(200).start();
                            }
                        }
                        onPressedStateChanged(pressed = false);
                    }
                }
                return true;
            }
            return false;
        }

        private boolean inLayoutHitRect(float x, float y) {
            layout.getHitRect(rect);
            return rect.contains((int) x, (int) y);
        }

        protected abstract void onPressedStateChanged(boolean pressed);

        protected abstract void onHide();
    }

    //region Offset Providers
    public static void addDelegate(@NonNull BaseFragment fragment, @NonNull Delegate delegate) {
        fragmentDelegates.put(fragment, delegate);
    }

    public static void addDelegate(@NonNull FrameLayout containerLayout, @NonNull Delegate delegate) {
        delegates.put(containerLayout, delegate);
    }

    private static Delegate findDelegate(BaseFragment probableFragment, FrameLayout probableContainer) {
        Delegate delegate;
        if ((delegate = fragmentDelegates.get(probableFragment)) != null) {
            return delegate;
        }
        if ((delegate = delegates.get(probableContainer)) != null) {
            return delegate;
        }
        return null;
    }

    public static void removeDelegate(@NonNull BaseFragment fragment) {
        fragmentDelegates.remove(fragment);
    }

    public static void removeDelegate(@NonNull FrameLayout containerLayout) {
        delegates.remove(containerLayout);
    }

    public interface Delegate {

        default int getBottomOffset(int tag) {
            return 0;
        }

        default boolean bottomOffsetAnimated() {
            return true;
        }

        default int getLeftPadding() {
            return 0;
        }

        default int getRightPadding() {
            return 0;
        }

        default int getTopOffset(int tag) {
            return 0;
        }

        default boolean clipWithGradient(int tag) {
            return false;
        }

        default void onBottomOffsetChange(float offset) {
        }

        default void onShow(Bulletin bulletin) {
        }

        default void onHide(Bulletin bulletin) {
        }

        default boolean allowLayoutChanges() {
            return true;
        }
    }
    //endregion

    //region Layouts
    public abstract static class Layout extends FrameLayout {

        private final List<Callback> callbacks = new ArrayList<>();
        public boolean transitionRunningEnter;
        public boolean transitionRunningExit;
        Delegate delegate;
        public float inOutOffset;

        protected Bulletin bulletin;
        Drawable background;
        public boolean top;

        public boolean isTransitionRunning() {
            return transitionRunningEnter || transitionRunningExit;
        }

        @WidthDef
        private int wideScreenWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
        @GravityDef
        private int wideScreenGravity = Gravity.CENTER_HORIZONTAL;
        private final Theme.ResourcesProvider resourcesProvider;

        public Layout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setMinimumHeight(dp(48));
            setBackground(getThemedColor(Theme.key_undo_background));
            updateSize();
            setPadding(dp(8), dp(8), dp(8), dp(8));
            setWillNotDraw(false);
            ScaleStateListAnimator.apply(this, .02f, 1.5f);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return background == who || super.verifyDrawable(who);
        }

        protected void setBackground(int color) {
            setBackground(color, 10);
        }

        public void setBackground(int color, int rounding) {
            background = Theme.createRoundRectDrawable(dp(rounding), color);
        }

        public final static FloatPropertyCompat<Layout> IN_OUT_OFFSET_Y = new FloatPropertyCompat<Layout>("offsetY") {
            @Override
            public float getValue(Layout object) {
                return object.inOutOffset;
            }

            @Override
            public void setValue(Layout object, float value) {
                object.setInOutOffset(value);
            }
        };

        public final static Property<Layout, Float> IN_OUT_OFFSET_Y2 = new AnimationProperties.FloatProperty<Layout>("offsetY") {

            @Override
            public Float get(Layout layout) {
                return layout.inOutOffset;
            }

            @Override
            public void setValue(Layout object, float value) {
                object.setInOutOffset(value);
            }
        };

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            updateSize();
        }

        public void setTop(boolean top) {
            this.top = top;
            updateSize();
        }

        private void updateSize() {
            final boolean isWideScreen = isWideScreen();
            setLayoutParams(LayoutHelper.createFrame(isWideScreen ? wideScreenWidth : LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, isWideScreen ? (top ? Gravity.TOP : Gravity.BOTTOM) | wideScreenGravity : (top ? Gravity.TOP : Gravity.BOTTOM)));
        }

        private boolean isWideScreen() {
            return AndroidUtilities.isTablet() || AndroidUtilities.displaySize.x >= AndroidUtilities.displaySize.y;
        }

        private void setWideScreenParams(@WidthDef int width, @GravityDef int gravity) {
            boolean changed = false;

            if (wideScreenWidth != width) {
                wideScreenWidth = width;
                changed = true;
            }

            if (wideScreenGravity != gravity) {
                wideScreenGravity = gravity;
                changed = true;
            }

            if (isWideScreen() && changed) {
                updateSize();
            }
        }

        @SuppressLint("RtlHardcoded")
        private boolean isNeedSwipeAlphaAnimation(boolean swipeLeft) {
            if (!isWideScreen() || wideScreenWidth == ViewGroup.LayoutParams.MATCH_PARENT) {
                return false;
            }
            if (wideScreenGravity == Gravity.CENTER_HORIZONTAL) {
                return true;
            }
            if (swipeLeft) {
                return wideScreenGravity == Gravity.RIGHT;
            } else {
                return wideScreenGravity != Gravity.RIGHT;
            }
        }

        protected CharSequence getAccessibilityText() {
            return null;
        }

        public Bulletin getBulletin() {
            return bulletin;
        }

        public boolean isAttachedToBulletin() {
            return bulletin != null;
        }

        @CallSuper
        protected void onAttach(@NonNull Bulletin bulletin) {
            this.bulletin = bulletin;
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onAttach(this, bulletin);
            }
        }

        @CallSuper
        protected void onDetach() {
            this.bulletin = null;
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onDetach(this);
            }
        }

        @CallSuper
        protected void onShow() {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onShow(this);
            }
        }

        @CallSuper
        protected void onHide() {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onHide(this);
            }
        }

        @CallSuper
        protected void onEnterTransitionStart() {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onEnterTransitionStart(this);
            }
        }

        @CallSuper
        protected void onEnterTransitionEnd() {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onEnterTransitionEnd(this);
            }
        }

        @CallSuper
        protected void onExitTransitionStart() {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onExitTransitionStart(this);
            }
        }

        @CallSuper
        protected void onExitTransitionEnd() {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onExitTransitionEnd(this);
            }
        }

        //region Callbacks
        public void addCallback(@NonNull Callback callback) {
            callbacks.add(callback);
        }

        public void removeCallback(@NonNull Callback callback) {
            callbacks.remove(callback);
        }

        public void updatePosition() {
            float translation = 0;
            if (delegate != null) {
                if (top) {
                    translation -= delegate.getTopOffset(bulletin != null ? bulletin.tag : 0);
                } else {
                    translation += getBottomOffset();
                }
            }
            setTranslationY(-translation + inOutOffset * (top ? -1 : 1));
        }

        public float getBottomOffset() {
            if (bulletin != null && (delegate == null || delegate.bottomOffsetAnimated()) && bulletin.bottomOffsetSpring != null && bulletin.bottomOffsetSpring.isRunning()) {
                return bulletin.lastBottomOffset;
            }
            return delegate.getBottomOffset(bulletin != null ? bulletin.tag : 0);
        }

        public interface Callback {
            default void onAttach(@NonNull Layout layout, @NonNull Bulletin bulletin) {
            }

            default void onDetach(@NonNull Layout layout) {
            }

            default void onShow(@NonNull Layout layout) {
            }

            default void onHide(@NonNull Layout layout) {
            }

            default void onEnterTransitionStart(@NonNull Layout layout) {
            }

            default void onEnterTransitionEnd(@NonNull Layout layout) {
            }

            default void onExitTransitionStart(@NonNull Layout layout) {
            }

            default void onExitTransitionEnd(@NonNull Layout layout) {
            }
        }
        //endregion

        //region Transitions
        @NonNull
        public Transition createTransition() {
            return new SpringTransition();
        }

        public interface Transition {
            void animateEnter(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset);

            void animateExit(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset);
        }

        public static class DefaultTransition implements Transition {

            long duration = 255;

            @Override
            public void animateEnter(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset) {
                layout.setInOutOffset(layout.getMeasuredHeight());
                if (onUpdate != null) {
                    onUpdate.accept(layout.getTranslationY());
                }
                final ObjectAnimator animator = ObjectAnimator.ofFloat(layout, IN_OUT_OFFSET_Y2, 0);
                animator.setDuration(duration);
                animator.setInterpolator(Easings.easeOutQuad);
                if (startAction != null || endAction != null) {
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (startAction != null) {
                                startAction.run();
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (endAction != null) {
                                endAction.run();
                            }
                        }
                    });
                }
                if (onUpdate != null) {
                    animator.addUpdateListener(a -> onUpdate.accept(layout.getTranslationY()));
                }
                animator.start();
            }

            @Override
            public void animateExit(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset) {
                final ObjectAnimator animator = ObjectAnimator.ofFloat(layout, IN_OUT_OFFSET_Y2, layout.getHeight());
                animator.setDuration(175);
                animator.setInterpolator(Easings.easeInQuad);
                if (startAction != null || endAction != null) {
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (startAction != null) {
                                startAction.run();
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (endAction != null) {
                                endAction.run();
                            }
                        }
                    });
                }
                if (onUpdate != null) {
                    animator.addUpdateListener(a -> onUpdate.accept(layout.getTranslationY()));
                }
                animator.start();
            }
        }

        public static class SpringTransition implements Transition {

            private static final float DAMPING_RATIO = 0.8f;
            private static final float STIFFNESS = 400f;

            @Override
            public void animateEnter(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset) {
                layout.setInOutOffset(layout.getMeasuredHeight());
                if (onUpdate != null) {
                    onUpdate.accept(layout.getTranslationY());
                }
                final SpringAnimation springAnimation = new SpringAnimation(layout, IN_OUT_OFFSET_Y, 0);
                springAnimation.getSpring().setDampingRatio(DAMPING_RATIO);
                springAnimation.getSpring().setStiffness(STIFFNESS);
                if (endAction != null) {
                    springAnimation.addEndListener((animation, canceled, value, velocity) -> {
                        layout.setInOutOffset(0);
                        if (!canceled) {
                            endAction.run();
                        }
                    });
                }
                if (onUpdate != null) {
                    springAnimation.addUpdateListener((animation, value, velocity) -> onUpdate.accept(layout.getTranslationY()));
                }
                springAnimation.start();
                if (startAction != null) {
                    startAction.run();
                }
            }

            @Override
            public void animateExit(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset) {
                final SpringAnimation springAnimation = new SpringAnimation(layout, IN_OUT_OFFSET_Y, layout.getHeight());
                springAnimation.getSpring().setDampingRatio(DAMPING_RATIO);
                springAnimation.getSpring().setStiffness(STIFFNESS);
                if (endAction != null) {
                    springAnimation.addEndListener((animation, canceled, value, velocity) -> {
                        if (!canceled) {
                            endAction.run();
                        }
                    });
                }
                if (onUpdate != null) {
                    springAnimation.addUpdateListener((animation, value, velocity) -> onUpdate.accept(layout.getTranslationY()));
                }
                springAnimation.start();
                if (startAction != null) {
                    startAction.run();
                }
            }
        }

        private void setInOutOffset(float offset) {
            inOutOffset = offset;
            updatePosition();
        }

        private LinearGradient clipGradient;
        private Matrix clipMatrix;
        private Paint clipPaint;

        protected int getMeasuredBackgroundHeight() {
            return getMeasuredHeight();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (bulletin == null) {
                return;
            }
            background.setBounds(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredBackgroundHeight() - getPaddingBottom());
            if (isTransitionRunning() && delegate != null) {
                final float top = delegate.getTopOffset(bulletin.tag) - getY();
                final float bottom = ((View) getParent()).getMeasuredHeight() - getBottomOffset() - getY();
                final boolean clip = delegate.clipWithGradient(bulletin.tag);
                canvas.save();
                canvas.clipRect(0, top, getMeasuredWidth(), bottom);
                if (clip) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                background.draw(canvas);
                super.dispatchDraw(canvas);
                if (clip) {
                    if (clipPaint == null) {
                        clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        clipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                        clipGradient = new LinearGradient(0, 0, 0, dp(8), this.top ? new int[]{0xff000000, 0} : new int[]{0, 0xff000000}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                        clipMatrix = new Matrix();
                        clipGradient.setLocalMatrix(clipMatrix);
                        clipPaint.setShader(clipGradient);
                    }
                    canvas.save();
                    clipMatrix.reset();
                    clipMatrix.postTranslate(0, this.top ? top : bottom - dp(8));
                    clipGradient.setLocalMatrix(clipMatrix);
                    if (this.top) {
                        canvas.drawRect(0, top, getWidth(), top + dp(8), clipPaint);
                    } else {
                        canvas.drawRect(0, bottom - dp(8), getWidth(), bottom, clipPaint);
                    }
                    canvas.restore();
                    canvas.restore();
                }
                canvas.restore();
                invalidate();
            } else {
                background.draw(canvas);
                super.dispatchDraw(canvas);
            }
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }
        //endregion
    }

    @SuppressLint("ViewConstructor")
    public static class ButtonLayout extends Layout {

        private Button button;
        public TimerView timerView;

        private int childrenMeasuredWidth;
        Theme.ResourcesProvider resourcesProvider;

        public ButtonLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            this.resourcesProvider = resourcesProvider;
        }

        private boolean wrapWidth;

        public void setWrapWidth() {
            wrapWidth = true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            childrenMeasuredWidth = 0;
            if (wrapWidth) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (button != null && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                setMeasuredDimension(childrenMeasuredWidth + button.getMeasuredWidth(), getMeasuredHeight());
            }
        }

        @Override
        protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
            if (button != null && child != button) {
                widthUsed += button.getMeasuredWidth() - dp(12);
            }
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
            if (child != button) {
                final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                childrenMeasuredWidth = Math.max(childrenMeasuredWidth, lp.leftMargin + lp.rightMargin + child.getMeasuredWidth());
            }
        }

        public Button getButton() {
            return button;
        }

        public void setButton(Button button) {
            if (this.button != null) {
                removeCallback(this.button);
                removeView(this.button);
            }
            this.button = button;
            if (button != null) {
                addCallback(button);
                addView(button, 0, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
            }
        }

        public void setTimer() {
            timerView = new TimerView(getContext(), resourcesProvider);
            timerView.timeLeft = 5000;
            addView(timerView, LayoutHelper.createFrameRelatively(20, 20, Gravity.START | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
        }
    }

    public static class SimpleLayout extends ButtonLayout {

        public final ImageView imageView;
        public final LinkSpanDrawable.LinksTextView textView;

        public SimpleLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            final int undoInfoColor = getThemedColor(Theme.key_undo_infoColor);

            imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(undoInfoColor, PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 16, 12, 16, 12));

            textView = new LinkSpanDrawable.LinksTextView(context);
            textView.setDisablePaddingsOffsetY(true);
            textView.setSingleLine();
            textView.setTextColor(undoInfoColor);
            textView.setTypeface(Typeface.SANS_SERIF);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 16, 0));
        }

        public CharSequence getAccessibilityText() {
            return textView.getText();
        }
    }

    @SuppressLint("ViewConstructor")
    public static class MultiLineLayout extends ButtonLayout {

        public final BackupImageView imageView = new BackupImageView(getContext());
        public final TextView textView = new TextView(getContext());

        public MultiLineLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            addView(imageView, LayoutHelper.createFrameRelatively(30, 30, Gravity.START | Gravity.CENTER_VERTICAL, 12, 8, 12, 8));

            textView.setGravity(Gravity.START);
            textView.setPadding(0, dp(8), 0, dp(8));
            textView.setTextColor(getThemedColor(Theme.key_undo_infoColor));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setTypeface(Typeface.SANS_SERIF);
            addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 16, 0));
        }

        public CharSequence getAccessibilityText() {
            return textView.getText();
        }
    }

    @SuppressLint("ViewConstructor")
    public static class TwoLineLayout extends ButtonLayout {

        public final BackupImageView imageView;
        public final TextView titleTextView;
        public final TextView subtitleTextView;
        private final LinearLayout linearLayout;

        public TwoLineLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            final int undoInfoColor = getThemedColor(Theme.key_undo_infoColor);

            addView(imageView = new BackupImageView(context), LayoutHelper.createFrameRelatively(29, 29, Gravity.START | Gravity.CENTER_VERTICAL, 12, 12, 12, 12));

            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 54, 8, 12, 8));

            titleTextView = new TextView(context);
            titleTextView.setSingleLine();
            titleTextView.setTextColor(undoInfoColor);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleTextView.setTypeface(AndroidUtilities.bold());
            linearLayout.addView(titleTextView);

            subtitleTextView = new TextView(context);
            subtitleTextView.setMaxLines(2);
            subtitleTextView.setTextColor(undoInfoColor);
            subtitleTextView.setLinkTextColor(getThemedColor(Theme.key_undo_cancelColor));
            subtitleTextView.setMovementMethod(new LinkMovementMethod());
            subtitleTextView.setTypeface(Typeface.SANS_SERIF);
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            linearLayout.addView(subtitleTextView);
        }

        public CharSequence getAccessibilityText() {
            return titleTextView.getText() + ".\n" + subtitleTextView.getText();
        }

        public void hideImage() {
            imageView.setVisibility(GONE);
            ((MarginLayoutParams) linearLayout.getLayoutParams()).setMarginStart(dp(12));
        }
    }

    public static class TwoLineLottieLayout extends ButtonLayout {

        public final RLottieImageView imageView;
        public final LinkSpanDrawable.LinksTextView titleTextView;
        public final LinkSpanDrawable.LinksTextView subtitleTextView;
        private final LinearLayout linearLayout;

        private final int textColor;

        public TwoLineLottieLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            this.textColor = getThemedColor(Theme.key_undo_infoColor);
            setBackground(getThemedColor(Theme.key_undo_background));

            imageView = new RLottieImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrameRelatively(56, 48, Gravity.START | Gravity.CENTER_VERTICAL));

            final int undoInfoColor = getThemedColor(Theme.key_undo_infoColor);
            final int undoLinkColor = getThemedColor(Theme.key_undo_cancelColor);

            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 52, 8, 8, 8));

            titleTextView = new LinkSpanDrawable.LinksTextView(context);
            titleTextView.setPadding(dp(4), 0, dp(4), 0);
            titleTextView.setSingleLine();
            titleTextView.setTextColor(undoInfoColor);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleTextView.setTypeface(AndroidUtilities.bold());
            linearLayout.addView(titleTextView);

            subtitleTextView = new LinkSpanDrawable.LinksTextView(context);
            subtitleTextView.setPadding(dp(4), 0, dp(4), 0);
            subtitleTextView.setTextColor(undoInfoColor);
            subtitleTextView.setLinkTextColor(undoLinkColor);
            subtitleTextView.setTypeface(Typeface.SANS_SERIF);
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            linearLayout.addView(subtitleTextView);
        }

        @Override
        protected void onShow() {
            super.onShow();
            imageView.playAnimation();
        }

        public void setAnimation(int resId, String... layers) {
            setAnimation(resId, 32, 32, layers);
        }

        public void setAnimation(int resId, int w, int h, String... layers) {
            imageView.setAnimation(resId, w, h);
            for (String layer : layers) {
                imageView.setLayerColor(layer + ".**", textColor);
            }
        }

        public void setAnimation(TLRPC.Document document, int w, int h, String... layers) {
            imageView.setAutoRepeat(true);
            imageView.setAnimation(document, w, h);
            for (String layer : layers) {
                imageView.setLayerColor(layer + ".**", textColor);
            }
        }

        public CharSequence getAccessibilityText() {
            return titleTextView.getText() + ".\n" + subtitleTextView.getText();
        }

        public void hideImage() {
            imageView.setVisibility(GONE);
            ((MarginLayoutParams) linearLayout.getLayoutParams()).setMarginStart(dp(10));
        }
    }

    public static class TwoLineAnimatedLottieLayout extends ButtonLayout {

        public final RLottieImageView imageView;
        public final LinkSpanDrawable.LinksTextView titleTextView;
        public final AnimatedTextView subtitleTextView;
        private final LinearLayout linearLayout;

        private final int textColor;

        public TwoLineAnimatedLottieLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            this.textColor = getThemedColor(Theme.key_undo_infoColor);
            setBackground(getThemedColor(Theme.key_undo_background));

            imageView = new RLottieImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrameRelatively(56, 48, Gravity.START | Gravity.CENTER_VERTICAL));

            final int undoInfoColor = getThemedColor(Theme.key_undo_infoColor);
            final int undoLinkColor = getThemedColor(Theme.key_undo_cancelColor);

            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 52, 8, 8, 8));

            titleTextView = new LinkSpanDrawable.LinksTextView(context);
            titleTextView.setPadding(dp(4), 0, dp(4), 0);
            titleTextView.setSingleLine();
            titleTextView.setTextColor(undoInfoColor);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleTextView.setTypeface(AndroidUtilities.bold());
            linearLayout.addView(titleTextView);

            subtitleTextView = new AnimatedTextView(context, false, true, true);
            subtitleTextView.setPadding(dp(4), 0, dp(4), 0);
            subtitleTextView.setTextColor(undoInfoColor);
            subtitleTextView.setTypeface(Typeface.SANS_SERIF);
            subtitleTextView.setTextSize(dp(13));
            linearLayout.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(6)));
        }

        public void setSubtitle(CharSequence text, boolean animated) {
            subtitleTextView.setText(text, animated);
        }

        @Override
        protected void onShow() {
            super.onShow();
            imageView.playAnimation();
        }

        public void setAnimation(int resId, String... layers) {
            setAnimation(resId, 32, 32, layers);
        }

        public void setAnimation(int resId, int w, int h, String... layers) {
            imageView.setAnimation(resId, w, h);
            for (String layer : layers) {
                imageView.setLayerColor(layer + ".**", textColor);
            }
        }

        public CharSequence getAccessibilityText() {
            return titleTextView.getText() + ".\n" + subtitleTextView.getText();
        }

        public void hideImage() {
            imageView.setVisibility(GONE);
            ((MarginLayoutParams) linearLayout.getLayoutParams()).setMarginStart(dp(10));
        }
    }

    public static class LottieLayoutWithReactions extends LottieLayout implements NotificationCenter.NotificationCenterDelegate {

        private ReactionsContainerLayout reactionsContainerLayout;
        private SparseLongArray newMessagesByIds;
        private final BaseFragment fragment;
        private final int messagesCount;

        public LottieLayoutWithReactions(BaseFragment fragment, int messagesCount) {
            super(fragment.getContext(), fragment.getResourceProvider());
            this.fragment = fragment;
            this.messagesCount = messagesCount;
            init();
        }

        private Bulletin bulletin;
        public void setBulletin(Bulletin b) {
            this.bulletin = b;
        }

        public void init() {
            textView.setLayoutParams(LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.TOP, 56, 6, 8, 0));
            imageView.setLayoutParams(LayoutHelper.createFrameRelatively(56, 48, Gravity.START | Gravity.TOP));
            reactionsContainerLayout = new ReactionsContainerLayout(ReactionsContainerLayout.TYPE_TAGS, fragment, getContext(), fragment.getCurrentAccount(), fragment.getResourceProvider()) {
                @Override
                protected void onShownCustomEmojiReactionDialog() {
                    Bulletin bulletin = Bulletin.getVisibleBulletin();
                    if (bulletin != null) {
                        bulletin.setCanHide(false);
                    }
                    reactionsContainerLayout.getReactionsWindow().windowView.setOnClickListener(v -> {
                        hideReactionsDialog();
                        Bulletin.hideVisible();
                    });
                }

                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        if (bulletin != null) {
                            bulletin.setCanHide(false);
                        }
                    } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                        if (bulletin != null) {
                            bulletin.setCanHide(true);
                        }
                    }
                    return super.dispatchTouchEvent(ev);
                }
            };
            reactionsContainerLayout.setPadding(dp(4), dp(24), dp(4), dp(0));
            reactionsContainerLayout.setDelegate(new ReactionsContainerLayout.ReactionsContainerDelegate() {
                @Override
                public void onReactionClicked(View view, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent) {
                    if (newMessagesByIds == null) {
                        return;
                    }
                    final long selfId = UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();
                    boolean isFragmentSavedMessages = fragment instanceof ChatActivity && ((ChatActivity) fragment).getDialogId() == selfId;
                    int lastMessageId = 0;
                    for (int i = 0; i < newMessagesByIds.size(); i++) {
                        int key = newMessagesByIds.keyAt(i);
                        TLRPC.Message message = new TLRPC.Message();
                        message.dialog_id = fragment.getUserConfig().getClientUserId();
                        message.id = key;
                        MessageObject messageObject = new MessageObject(fragment.getCurrentAccount(), message, false, false);
                        ArrayList<ReactionsLayoutInBubble.VisibleReaction> visibleReactions = new ArrayList<>();
                        visibleReactions.add(visibleReaction);
                        fragment.getSendMessagesHelper().sendReaction(messageObject, visibleReactions, visibleReaction, false, false, fragment, null);
                        lastMessageId = message.id;
                    }
                    hideReactionsDialog();
                    Bulletin.hideVisible();
                    showTaggedReactionToast(visibleReaction, fragment.getCurrentAccount(), lastMessageId, !isFragmentSavedMessages);
                }

                private void showTaggedReactionToast(ReactionsLayoutInBubble.VisibleReaction visibleReaction, int currentAccount, int messageId, boolean addViewButton) {
                    AndroidUtilities.runOnUIThread(() -> {
                        BaseFragment baseFragment = LaunchActivity.getLastFragment();
                        TLRPC.Document document;
                        if (visibleReaction.documentId == 0) {
                            TLRPC.TL_availableReaction availableReaction = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(visibleReaction.emojicon);
                            if (availableReaction == null) {
                                return;
                            }
                            document = availableReaction.activate_animation;
                        } else {
                            document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, visibleReaction.documentId);
                        }

                        if (document == null || baseFragment == null) {
                            return;
                        }

                        BulletinFactory.of(baseFragment).createMessagesTaggedBulletin(messagesCount, document, addViewButton ? () -> {
                            Bundle args = new Bundle();
                            args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                            args.putInt("message_id", messageId);
                            baseFragment.presentFragment(new ChatActivity(args));
                        } : null).show(true);
                    }, 300);
                }
            });
            reactionsContainerLayout.setTop(true);
            reactionsContainerLayout.setClipChildren(false);
            reactionsContainerLayout.setClipToPadding(false);
            reactionsContainerLayout.setVisibility(View.VISIBLE);
            reactionsContainerLayout.setBubbleOffset(-dp(80));
            reactionsContainerLayout.setHint(LocaleController.getString(R.string.SavedTagReactionsHint));
            addView(reactionsContainerLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 92.5f, Gravity.CENTER_HORIZONTAL, 0, 36, 0, 0));
            reactionsContainerLayout.setMessage(null, null, true);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getInstance(UserConfig.selectedAccount).addObserver(this, NotificationCenter.savedMessagesForwarded);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(UserConfig.selectedAccount).removeObserver(this, NotificationCenter.savedMessagesForwarded);
        }

        public void hideReactionsDialog() {
            if (reactionsContainerLayout.getReactionsWindow() != null) {
                reactionsContainerLayout.dismissWindow();
                if (reactionsContainerLayout.getReactionsWindow().containerView != null) {
                    reactionsContainerLayout.getReactionsWindow().containerView.animate().alpha(0).setDuration(180).start();
                }
            }
        }

        @Override
        protected int getMeasuredBackgroundHeight() {
            return textView.getMeasuredHeight() + dp(30);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.savedMessagesForwarded) {
                newMessagesByIds = (SparseLongArray) args[0];
            }
        }
    }

    public static class LottieLayout extends ButtonLayout {

        public RLottieImageView imageView;
        public TextView textView;

        private int textColor;

        public LottieLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            imageView = new RLottieImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrameRelatively(56, 48, Gravity.START | Gravity.CENTER_VERTICAL));

            textView = new LinkSpanDrawable.LinksTextView(context) {
                {
                    setDisablePaddingsOffset(true);
                }

                @Override
                public void setText(CharSequence text, BufferType type) {
                    text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), dp(13), false);
                    super.setText(text, type);
                }
            };
            NotificationCenter.listenEmojiLoading(textView);
            textView.setSingleLine();
            textView.setTypeface(Typeface.SANS_SERIF);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setPadding(0, dp(8), 0, dp(8));
            addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 8, 0));

            textView.setLinkTextColor(getThemedColor(Theme.key_undo_cancelColor));
            setTextColor(getThemedColor(Theme.key_undo_infoColor));
            setBackground(getThemedColor(Theme.key_undo_background));
        }

        public LottieLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, int backgroundColor, int textColor) {
            this(context, resourcesProvider);
            setBackground(backgroundColor);
            setTextColor(textColor);
        }

        public void setTextColor(int textColor) {
            this.textColor = textColor;
            textView.setTextColor(textColor);
        }

        @Override
        protected void onShow() {
            super.onShow();
            imageView.playAnimation();
        }

        public void setAnimation(int resId, String... layers) {
            setAnimation(resId, 32, 32, layers);
        }

        public void setAnimation(int resId, int w, int h, String... layers) {
            imageView.setAnimation(resId, w, h);
            for (String layer : layers) {
                imageView.setLayerColor(layer + ".**", textColor);
            }
        }

        public void setAnimation(TLRPC.Document document, int w, int h, String... layers) {
            imageView.setAutoRepeat(true);
            imageView.setAnimation(document, w, h);
            for (String layer : layers) {
                imageView.setLayerColor(layer + ".**", textColor);
            }
        }

        public void setIconPaddingBottom(int paddingBottom) {
            imageView.setLayoutParams(LayoutHelper.createFrameRelatively(56, 48 - paddingBottom, Gravity.START | Gravity.CENTER_VERTICAL, 0, 0, 0, paddingBottom));
        }

        public CharSequence getAccessibilityText() {
            return textView.getText();
        }
    }

    public static interface LoadingLayout {
        void onTextLoaded(CharSequence text);
    }

    public static class LoadingLottieLayout extends LottieLayout implements LoadingLayout {

        public LinkSpanDrawable.LinksTextView textLoadingView;

        public LoadingLottieLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            textLoadingView = new LinkSpanDrawable.LinksTextView(context);
            textLoadingView.setDisablePaddingsOffset(true);
            textLoadingView.setSingleLine();
            textLoadingView.setTypeface(Typeface.SANS_SERIF);
            textLoadingView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            textLoadingView.setEllipsize(TextUtils.TruncateAt.END);
            textLoadingView.setPadding(0, dp(8), 0, dp(8));
            textView.setVisibility(View.GONE);
            addView(textLoadingView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 8, 0));

            setTextColor(getThemedColor(Theme.key_undo_infoColor));
        }

        public LoadingLottieLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, int backgroundColor, int textColor) {
            this(context, resourcesProvider);
            setBackground(backgroundColor);
            setTextColor(textColor);
        }

        @Override
        public void setTextColor(int textColor) {
            super.setTextColor(textColor);
            if (textLoadingView != null) {
                textLoadingView.setTextColor(textColor);
            }
        }

        public void onTextLoaded(CharSequence text) {
            textView.setText(text);
            AndroidUtilities.updateViewShow(textLoadingView, false, false, true);
            AndroidUtilities.updateViewShow(textView, true, false, true);
        }
    }

    public static class UsersLayout extends ButtonLayout {

        public AvatarsImageView avatarsImageView;
        public TextView textView;
        public TextView subtitleView;
        LinearLayout linearLayout;

        public UsersLayout(@NonNull Context context, boolean subtitle, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);

            avatarsImageView = new AvatarsImageView(context, false);
            avatarsImageView.setStyle(AvatarsDrawable.STYLE_MESSAGE_SEEN);
            avatarsImageView.setAvatarsTextSize(dp(18));
            addView(avatarsImageView, LayoutHelper.createFrameRelatively(24 + 12 + 12 + 8, 48, Gravity.START | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

            if (!subtitle) {
                textView = new LinkSpanDrawable.LinksTextView(context) {
                    @Override
                    public void setText(CharSequence text, BufferType type) {
                        text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), dp(13), false);
                        super.setText(text, type);
                    }
                };
                NotificationCenter.listenEmojiLoading(textView);
                textView.setTypeface(Typeface.SANS_SERIF);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setPadding(0, dp(8), 0, dp(8));
                textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 12 + 56 + 2, 0, 12, 0));
            } else {
                linearLayout = new LinearLayout(getContext());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 18 + 56 + 2, 6, 12, 6));

                textView = new LinkSpanDrawable.LinksTextView(context) {
                    @Override
                    public void setText(CharSequence text, BufferType type) {
                        text = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), dp(13), false);
                        super.setText(text, type);
                    }
                };
                NotificationCenter.listenEmojiLoading(textView);
                textView.setTypeface(Typeface.SANS_SERIF);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setMaxLines(1);
                linearLayout.addView(textView);

                subtitleView = new LinkSpanDrawable.LinksTextView(context);
                subtitleView.setTypeface(Typeface.SANS_SERIF);
                subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                subtitleView.setEllipsize(TextUtils.TruncateAt.END);
                subtitleView.setSingleLine(false);
                subtitleView.setMaxLines(3);
                subtitleView.setLinkTextColor(getThemedColor(Theme.key_undo_cancelColor));
                linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0));
            }
            textView.setLinkTextColor(getThemedColor(Theme.key_undo_cancelColor));

            setTextColor(getThemedColor(Theme.key_undo_infoColor));
            setBackground(getThemedColor(Theme.key_undo_background));
        }

        public UsersLayout(@NonNull Context context, Theme.ResourcesProvider resourcesProvider, int backgroundColor, int textColor) {
            this(context, false, resourcesProvider);
            setBackground(backgroundColor);
            setTextColor(textColor);
        }

        public void setTextColor(int textColor) {
            textView.setTextColor(textColor);
            if (subtitleView != null) {
                subtitleView.setTextColor(textColor);
            }
        }

        @Override
        protected void onShow() {
            super.onShow();
        }

        public CharSequence getAccessibilityText() {
            return textView.getText();
        }
    }
    //endregion

    //region Buttons
    @SuppressLint("ViewConstructor")
    public abstract static class Button extends FrameLayout implements Layout.Callback {

        public Button(@NonNull Context context) {
            super(context);
        }

        @Override
        public void onAttach(@NonNull Layout layout, @NonNull Bulletin bulletin) {
        }

        @Override
        public void onDetach(@NonNull Layout layout) {
        }

        @Override
        public void onShow(@NonNull Layout layout) {
        }

        @Override
        public void onHide(@NonNull Layout layout) {
        }

        @Override
        public void onEnterTransitionStart(@NonNull Layout layout) {
        }

        @Override
        public void onEnterTransitionEnd(@NonNull Layout layout) {
        }

        @Override
        public void onExitTransitionStart(@NonNull Layout layout) {
        }

        @Override
        public void onExitTransitionEnd(@NonNull Layout layout) {
        }
    }

    @SuppressLint("ViewConstructor")
    public static final class UndoButton extends Button {

        private final Theme.ResourcesProvider resourcesProvider;
        private Runnable undoAction;
        private Runnable delayedAction;

        private Bulletin bulletin;
        public TextView undoTextView;
        private boolean isUndone;

        public UndoButton(@NonNull Context context, boolean text) {
            this(context, text, null);
        }

        public UndoButton(@NonNull Context context, boolean text, Theme.ResourcesProvider resourcesProvider) {
            this(context, text, !text, resourcesProvider);
        }

        public UndoButton(@NonNull Context context, boolean text, boolean icon, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            final int undoCancelColor = getThemedColor(Theme.key_undo_cancelColor);

            if (text) {
                undoTextView = new TextView(context);
                undoTextView.setBackground(Theme.createSelectorDrawable((undoCancelColor & 0x00ffffff) | 0x19000000, Theme.RIPPLE_MASK_ROUNDRECT_6DP));
                undoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                undoTextView.setTypeface(AndroidUtilities.bold());
                undoTextView.setTextColor(undoCancelColor);
                undoTextView.setText(LocaleController.getString(R.string.Undo));
                undoTextView.setGravity(Gravity.CENTER_VERTICAL);
                ViewHelper.setPaddingRelative(undoTextView, icon ? 34 : 12, 8, 12, 8);
                addView(undoTextView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 8, 0));
            }

            if (icon) {
                final ImageView undoImageView = new ImageView(getContext());
                undoImageView.setImageResource(R.drawable.chats_undo);
                undoImageView.setColorFilter(new PorterDuffColorFilter(undoCancelColor, PorterDuff.Mode.MULTIPLY));
                if (!text) {
                    undoImageView.setBackground(Theme.createSelectorDrawable((undoCancelColor & 0x00ffffff) | 0x19000000));
                }
                ViewHelper.setPaddingRelative(undoImageView, 0, 12, 0, 12);
                addView(undoImageView, LayoutHelper.createFrameRelatively(56, 48, Gravity.CENTER_VERTICAL));
            }

            setOnClickListener(v -> undo());
        }

        public UndoButton setText(CharSequence text) {
            if (undoTextView != null) {
                undoTextView.setText(text);
            }
            return this;
        }

        public void undo() {
            if (bulletin != null) {
                isUndone = true;
                if (undoAction != null) {
                    undoAction.run();
                }
                if (bulletin != null) {
                    bulletin.hide();
                }
            }
        }

        @Override
        public void onAttach(@NonNull Layout layout, @NonNull Bulletin bulletin) {
            this.bulletin = bulletin;
        }

        @Override
        public void onDetach(@NonNull Layout layout) {
            this.bulletin = null;
            if (delayedAction != null && !isUndone) {
                delayedAction.run();
            }
        }

        public UndoButton setUndoAction(Runnable undoAction) {
            this.undoAction = undoAction;
            return this;
        }

        public UndoButton setDelayedAction(Runnable delayedAction) {
            this.delayedAction = delayedAction;
            return this;
        }

        protected int getThemedColor(int key) {
            if (resourcesProvider != null) {
                return resourcesProvider.getColor(key);
            }
            return Theme.getColor(key);
        }
    }

    // TODO: possibility of loading icon as well
    public void onLoaded(CharSequence text) {
        loaded = true;
        if (layout instanceof LoadingLayout) {
            ((LoadingLayout) layout).onTextLoaded(text);
        }
        setCanHide(true);
    }

    //endregion

    public static class EmptyBulletin extends Bulletin {

        public EmptyBulletin() {
            super();
        }

        @Override
        public Bulletin show() {
            return this;
        }
    }

    public static class TimerView extends View {

        private final Paint progressPaint;
        public long timeLeft;
        private int prevSeconds;
        private String timeLeftString;
        private int textWidth;

        StaticLayout timeLayout;
        StaticLayout timeLayoutOut;
        int textWidthOut;

        float timeReplaceProgress = 1f;

        private TextPaint textPaint;
        private long lastUpdateTime;
        RectF rect = new RectF();

        public TimerView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(dp(12));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));

            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeWidth(dp(2));
            progressPaint.setStrokeCap(Paint.Cap.ROUND);

            setColor(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider));
        }

        public void setColor(int color) {
            textPaint.setColor(color);
            progressPaint.setColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int newSeconds = timeLeft > 0 ? (int) Math.ceil(timeLeft / 1000.0f) : 0;
            rect.set(dp(1), dp(1), getMeasuredWidth() - dp(1), getMeasuredHeight() - dp(1));
            if (prevSeconds != newSeconds) {
                prevSeconds = newSeconds;
                timeLeftString = String.valueOf(Math.max(0, newSeconds));
                if (timeLayout != null) {
                    timeLayoutOut = timeLayout;
                    timeReplaceProgress = 0;
                    textWidthOut = textWidth;
                }
                textWidth = (int) Math.ceil(textPaint.measureText(timeLeftString));
                timeLayout = new StaticLayout(timeLeftString, textPaint, Integer.MAX_VALUE, android.text.Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }

            if (timeReplaceProgress < 1f) {
                timeReplaceProgress += 16f / 150f;
                if (timeReplaceProgress > 1f) {
                    timeReplaceProgress = 1f;
                } else {
                    invalidate();
                }
            }

            int alpha = textPaint.getAlpha();

            if (timeLayoutOut != null && timeReplaceProgress < 1f) {
                textPaint.setAlpha((int) (alpha * (1f - timeReplaceProgress)));
                canvas.save();
                canvas.translate(rect.centerX() - textWidthOut / 2f, rect.centerY() - timeLayoutOut.getHeight() / 2f + dp(10) * timeReplaceProgress - dp(.5f));
                timeLayoutOut.draw(canvas);
                textPaint.setAlpha(alpha);
                canvas.restore();
            }

            if (timeLayout != null) {
                if (timeReplaceProgress != 1f) {
                    textPaint.setAlpha((int) (alpha * timeReplaceProgress));
                }
                canvas.save();
                canvas.translate(rect.centerX() - textWidth / 2f, rect.centerY() - timeLayout.getHeight() / 2f - dp(10) * (1f - timeReplaceProgress) - dp(.5f));
                timeLayout.draw(canvas);
                if (timeReplaceProgress != 1f) {
                    textPaint.setAlpha(alpha);
                }
                canvas.restore();
            }

            canvas.drawArc(rect, -90, -360 * (Math.max(0, timeLeft) / 5000.0f), false, progressPaint);

            if (lastUpdateTime != 0) {
                long newTime = System.currentTimeMillis();
                long dt = newTime - lastUpdateTime;
                timeLeft -= dt;
                lastUpdateTime = newTime;
            } else {
                lastUpdateTime = System.currentTimeMillis();
            }
            invalidate();
        }
    }

    // to make bulletin above everything
    // use as BulletinFactory.of(BulletinWindow.make(context), resourcesProvider)...
    public static class BulletinWindow extends Dialog {

        public static BulletinWindowLayout make(Context context, Delegate delegate) {
            return new BulletinWindow(context, delegate).container;
        }

        public static BulletinWindowLayout make(Context context) {
            return new BulletinWindow(context, null).container;
        }

        private final BulletinWindowLayout container;

        private BulletinWindow(Context context, Delegate delegate) {
            super(context);
            setContentView(
                    container = new BulletinWindowLayout(context),
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );
            if (Build.VERSION.SDK_INT >= 21) {
                container.setFitsSystemWindows(true);
                container.setOnApplyWindowInsetsListener((v, insets) -> {
                    applyInsets(insets);
                    v.requestLayout();
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

            addDelegate(container, new Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return delegate == null ? 0 : delegate.getBottomOffset(tag);
                }

                @Override
                public int getTopOffset(int tag) {
                    return delegate == null ? AndroidUtilities.statusBarHeight : delegate.getTopOffset(tag);
                }

                @Override
                public boolean clipWithGradient(int tag) {
                    return delegate != null && delegate.clipWithGradient(tag);
                }
            });

            try {
                Window window = getWindow();
                window.setWindowAnimations(R.style.DialogNoAnimation);
                window.setBackgroundDrawable(null);
                params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.dimAmount = 0;
                params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    params.flags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
                }
                params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                if (Build.VERSION.SDK_INT >= 21) {
                    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
                }
                params.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                if (Build.VERSION.SDK_INT >= 28) {
                    params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }
                window.setAttributes(params);
                AndroidUtilities.setLightNavigationBar(window, AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundGray)) > 0.721f);
            } catch (Exception ignore) {}
        }

        @Override
        public void show() {
            if (!AndroidUtilities.isSafeToShow(getContext())) return;
            super.show();
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
        private void applyInsets(WindowInsets insets) {
            if (container != null) {
                container.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                );
            }
        }

        private WindowManager.LayoutParams params;

        public class BulletinWindowLayout extends FrameLayout {
            public BulletinWindowLayout(Context context) {
                super(context);
            }

            @Override
            public void addView(View child) {
                super.addView(child);
                BulletinWindow.this.show();
            }

            @Override
            public void removeView(View child) {
                super.removeView(child);
                try {
                    BulletinWindow.this.dismiss();
                } catch (Exception ignore) {

                }
                removeDelegate(container);
            }

            public void setTouchable(boolean touchable) {
                if (params == null) {
                    return;
                }
                if (!touchable) {
                    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                } else {
                    params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                }
                BulletinWindow.this.getWindow().setAttributes(params);
            }

            @Nullable
            public WindowManager.LayoutParams getLayout() {
                return params;
            }

            public void updateLayout() {
                BulletinWindow.this.getWindow().setAttributes(params);
            }
        }
    }
}
