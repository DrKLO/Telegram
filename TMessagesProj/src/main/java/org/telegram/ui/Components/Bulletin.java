package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;
import androidx.core.view.ViewCompat;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Bulletin {

    public static final int DURATION_SHORT = 1500;
    public static final int DURATION_LONG = 2750;

    public static Bulletin make(@NonNull FrameLayout containerLayout, @NonNull Layout contentLayout, int duration) {
        return new Bulletin(containerLayout, contentLayout, duration);
    }

    public static Bulletin make(@NonNull BaseFragment fragment, @NonNull Layout contentLayout, int duration) {
        return new Bulletin(fragment.getLayoutContainer(), contentLayout, duration);
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
            bulletin.hide(animated && isTransitionsEnabled());
        }
    }

    private static final HashMap<FrameLayout, Delegate> delegates = new HashMap<>();

    @SuppressLint("StaticFieldLeak")
    private static Bulletin visibleBulletin;

    private final Layout layout;
    private final FrameLayout parentLayout;
    private final FrameLayout containerLayout;
    private final int duration;

    private boolean showing;
    private Runnable exitRunnable;
    private int currentBottomOffset;
    private Delegate currentDelegate;
    private Layout.Transition layoutTransition;

    private Bulletin(@NonNull FrameLayout containerLayout, @NonNull Layout layout, int duration) {
        this.layout = layout;
        this.parentLayout = new FrameLayout(layout.getContext());
        this.parentLayout.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        this.containerLayout = containerLayout;
        this.duration = duration;
    }

    public void show() {
        if (!showing) {
            showing = true;

            Preconditions.checkState(layout.getParent() == parentLayout, "Layout has incorrect parent");

            if (visibleBulletin != null) {
                visibleBulletin.hide();
            }
            visibleBulletin = this;
            layout.onAttach(this);

            layout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    layout.removeOnLayoutChangeListener(this);
                    if (showing) {
                        layout.onShow();
                        currentDelegate = delegates.get(containerLayout);
                        currentBottomOffset = currentDelegate != null ? currentDelegate.getBottomOffset() : 0;
                        if (isTransitionsEnabled()) {
                            if (currentBottomOffset != 0) {
                                ViewCompat.setClipBounds(parentLayout, new Rect(left, top - currentBottomOffset, right, bottom - currentBottomOffset));
                            } else {
                                ViewCompat.setClipBounds(parentLayout, null);
                            }
                            ensureLayoutTransitionCreated();
                            layoutTransition.animateEnter(layout, layout::onEnterTransitionStart, () -> {
                                layout.onEnterTransitionEnd();
                                layout.postDelayed(exitRunnable = Bulletin.this::hide, duration);
                            }, offset -> {
                                if (currentDelegate != null) {
                                    currentDelegate.onOffsetChange(layout.getHeight() - offset);
                                }
                            }, currentBottomOffset);
                        } else {
                            if (currentDelegate != null) {
                                currentDelegate.onOffsetChange(layout.getHeight() - currentBottomOffset);
                            }
                            layout.setTranslationY(-currentBottomOffset);
                            layout.onEnterTransitionStart();
                            layout.onEnterTransitionEnd();
                            layout.postDelayed(exitRunnable = Bulletin.this::hide, duration);
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
                    hide(false);
                }
            });

            containerLayout.addView(parentLayout);
        }
    }

    private void ensureLayoutTransitionCreated() {
        if (layoutTransition == null) {
            layoutTransition = layout.createTransition();
        }
    }

    public void hide() {
        hide(isTransitionsEnabled());
    }

    private void hide(boolean animated) {
        if (showing) {
            showing = false;

            if (visibleBulletin == this) {
                visibleBulletin = null;
            }

            int bottomOffset = currentBottomOffset;
            currentBottomOffset = 0;

            if (ViewCompat.isLaidOut(layout)) {
                if (exitRunnable != null) {
                    layout.removeCallbacks(exitRunnable);
                    exitRunnable = null;
                }
                if (animated) {
                    ensureLayoutTransitionCreated();
                    layoutTransition.animateExit(layout, layout::onExitTransitionStart, () -> {
                        if (currentDelegate != null) {
                            currentDelegate.onOffsetChange(0);
                        }
                        layout.onExitTransitionEnd();
                        layout.onHide();
                        containerLayout.removeView(parentLayout);
                        layout.onDetach();
                    }, offset -> {
                        if (currentDelegate != null) {
                            currentDelegate.onOffsetChange(layout.getHeight() - offset);
                        }
                    }, bottomOffset);
                    return;
                }
            }

            if (currentDelegate != null) {
                currentDelegate.onOffsetChange(0);
            }
            layout.onExitTransitionStart();
            layout.onExitTransitionEnd();
            layout.onHide();
            if (containerLayout != null) {
                containerLayout.removeView(parentLayout);
            }
            layout.onDetach();
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

    //region Offset Providers
    public static void addDelegate(@NonNull BaseFragment fragment, @NonNull Delegate delegate) {
        final FrameLayout containerLayout = fragment.getLayoutContainer();
        if (containerLayout != null) {
            addDelegate(containerLayout, delegate);
        }
    }

    public static void addDelegate(@NonNull FrameLayout containerLayout, @NonNull Delegate delegate) {
        delegates.put(containerLayout, delegate);
    }

    public static void removeDelegate(@NonNull BaseFragment fragment) {
        final FrameLayout containerLayout = fragment.getLayoutContainer();
        if (containerLayout != null) {
            removeDelegate(containerLayout);
        }
    }

    public static void removeDelegate(@NonNull FrameLayout containerLayout) {
        delegates.remove(containerLayout);
    }

    public interface Delegate {

        default int getBottomOffset() {
            return 0;
        }

        default void onOffsetChange(float offset) {
        }
    }
    //endregion

    //region Layouts
    public abstract static class Layout extends FrameLayout {

        private final List<Callback> callbacks = new ArrayList<>();

        protected Bulletin bulletin;

        public Layout(@NonNull Context context) {
            this(context, Theme.getColor(Theme.key_undo_background));
        }

        public Layout(@NonNull Context context, @ColorInt int backgroundColor) {
            super(context);
            setMinimumHeight(AndroidUtilities.dp(48));
            setBackground(new InsetDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), backgroundColor), AndroidUtilities.dp(8)));
            updateSize();
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            updateSize();
        }

        private void updateSize() {
            final boolean isPortrait = AndroidUtilities.displaySize.x < AndroidUtilities.displaySize.y;
            final boolean matchParentWidth = !AndroidUtilities.isTablet() && isPortrait;
            setMinimumWidth(matchParentWidth ? 0 : AndroidUtilities.dp(344));
            setLayoutParams(LayoutHelper.createFrame(matchParentWidth ? LayoutHelper.MATCH_PARENT : LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            return true;
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

        public interface Callback {

            void onAttach(@NonNull Layout layout, @NonNull Bulletin bulletin);

            void onDetach(@NonNull Layout layout);

            void onShow(@NonNull Layout layout);

            void onHide(@NonNull Layout layout);

            void onEnterTransitionStart(@NonNull Layout layout);

            void onEnterTransitionEnd(@NonNull Layout layout);

            void onExitTransitionStart(@NonNull Layout layout);

            void onExitTransitionEnd(@NonNull Layout layout);
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

            @Override
            public void animateEnter(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset) {
                final ObjectAnimator animator = ObjectAnimator.ofFloat(layout, View.TRANSLATION_Y, layout.getHeight(), -bottomOffset);
                animator.setDuration(225);
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
                    animator.addUpdateListener(a -> onUpdate.accept((Float) a.getAnimatedValue()));
                }
                animator.start();
            }

            @Override
            public void animateExit(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset) {
                final ObjectAnimator animator = ObjectAnimator.ofFloat(layout, View.TRANSLATION_Y, layout.getTranslationY(), layout.getHeight());
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
                    animator.addUpdateListener(a -> onUpdate.accept((Float) a.getAnimatedValue()));
                }
                animator.start();
            }
        }

        public static class SpringTransition implements Transition {

            private static final float DAMPING_RATIO = 0.8f;
            private static final float STIFFNESS = 400f;

            @Override
            public void animateEnter(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate, int bottomOffset) {
                final int translationY = layout.getHeight() - bottomOffset;
                layout.setTranslationY(translationY);
                onUpdate.accept((float) translationY);
                final SpringAnimation springAnimation = new SpringAnimation(layout, SpringAnimation.TRANSLATION_Y, -bottomOffset);
                springAnimation.getSpring().setDampingRatio(DAMPING_RATIO);
                springAnimation.getSpring().setStiffness(STIFFNESS);
                if (endAction != null) {
                    springAnimation.addEndListener(new DynamicAnimation.OnAnimationEndListener() {
                        @Override
                        public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                            if (!canceled) {
                                endAction.run();
                            }
                        }
                    });
                }
                if (onUpdate != null) {
                    springAnimation.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
                        @Override
                        public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                            onUpdate.accept(value);
                        }
                    });
                }
                springAnimation.start();
                if (startAction != null) {
                    startAction.run();
                }
            }

            @Override
            public void animateExit(@NonNull Layout layout, @Nullable Runnable startAction, @Nullable Runnable endAction, @Nullable Consumer<Float> onUpdate,int bottomOffset) {
                final SpringAnimation springAnimation = new SpringAnimation(layout, SpringAnimation.TRANSLATION_Y, layout.getHeight() - bottomOffset);
                springAnimation.getSpring().setDampingRatio(DAMPING_RATIO);
                springAnimation.getSpring().setStiffness(STIFFNESS);
                if (endAction != null) {
                    springAnimation.addEndListener(new DynamicAnimation.OnAnimationEndListener() {
                        @Override
                        public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                            if (!canceled) {
                                endAction.run();
                            }
                        }
                    });
                }
                if (onUpdate != null) {
                    springAnimation.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
                        @Override
                        public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                            onUpdate.accept(value);
                        }
                    });
                }
                springAnimation.start();
                if (startAction != null) {
                    startAction.run();
                }
            }
        }
        //endregion
    }

    @SuppressLint("ViewConstructor")
    public static class ButtonLayout extends Layout {

        private Button button;

        private int childrenMeasuredWidth;

        public ButtonLayout(@NonNull Context context) {
            super(context);
        }

        public ButtonLayout(@NonNull Context context, int backgroundColor) {
            super(context, backgroundColor);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            childrenMeasuredWidth = 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (button != null && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                setMeasuredDimension(childrenMeasuredWidth + button.getMeasuredWidth(), getMeasuredHeight());
            }
        }

        @Override
        protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
            if (button != null && child != button) {
                widthUsed += button.getMeasuredWidth() - AndroidUtilities.dp(12);
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
    }

    public static class SimpleLayout extends ButtonLayout {

        public final ImageView imageView;
        public final TextView textView;

        public SimpleLayout(@NonNull Context context) {
            super(context);

            final int undoInfoColor = Theme.getColor(Theme.key_undo_infoColor);

            imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(undoInfoColor, PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 16, 12, 16, 12));

            textView = new TextView(context);
            textView.setSingleLine();
            textView.setTextColor(undoInfoColor);
            textView.setTypeface(Typeface.SANS_SERIF);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 16, 0));
        }
    }

    @SuppressLint("ViewConstructor")
    public static class TwoLineLayout extends ButtonLayout {

        public final BackupImageView imageView;
        public final TextView titleTextView;
        public final TextView subtitleTextView;

        public TwoLineLayout(@NonNull Context context) {
            super(context);

            final int undoInfoColor = Theme.getColor(Theme.key_undo_infoColor);

            addView(imageView = new BackupImageView(context), LayoutHelper.createFrameRelatively(29, 29, Gravity.START | Gravity.CENTER_VERTICAL, 12, 12, 12, 12));

            final LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            addView(linearLayout, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 54, 8, 12, 8));

            titleTextView = new TextView(context);
            titleTextView.setSingleLine();
            titleTextView.setTextColor(undoInfoColor);
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            linearLayout.addView(titleTextView);

            subtitleTextView = new TextView(context);
            subtitleTextView.setMaxLines(2);
            subtitleTextView.setTextColor(undoInfoColor);
            subtitleTextView.setTypeface(Typeface.SANS_SERIF);
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            linearLayout.addView(subtitleTextView);
        }
    }

    public static class LottieLayout extends ButtonLayout {

        public final RLottieImageView imageView;
        public final TextView textView;

        private final int textColor;

        public LottieLayout(@NonNull Context context) {
            this(context, Theme.getColor(Theme.key_undo_background), Theme.getColor(Theme.key_undo_infoColor));
        }

        public LottieLayout(@NonNull Context context, @ColorInt int backgroundColor, @ColorInt int textColor) {
            super(context, backgroundColor);
            this.textColor = textColor;

            imageView = new RLottieImageView(context);
            addView(imageView, LayoutHelper.createFrameRelatively(28, 28, Gravity.START | Gravity.CENTER_VERTICAL, 14, 10, 14, 10));

            textView = new TextView(context);
            textView.setSingleLine();
            textView.setTextColor(textColor);
            textView.setTypeface(Typeface.SANS_SERIF);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 0, 16, 0));
        }

        @Override
        protected void onShow() {
            super.onShow();
            imageView.playAnimation();
        }

        public void setAnimation(int resId, String... layers) {
            imageView.setAnimation(resId, 28, 28);
            for (int i = 0; i < layers.length; i++) {
                imageView.setLayerColor(layers[i] + ".**", textColor);
            }
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

    public static final class UndoButton extends Button {

        private Runnable undoAction;
        private Runnable delayedAction;

        private Bulletin bulletin;
        private boolean isUndone;

        public UndoButton(@NonNull Context context) {
            super(context);

            final int undoCancelColor = Theme.getColor(Theme.key_undo_cancelColor);

            final ImageView undoImageView = new ImageView(getContext());
            undoImageView.setOnClickListener(v -> undo());
            undoImageView.setImageResource(R.drawable.chats_undo);
            undoImageView.setColorFilter(new PorterDuffColorFilter(undoCancelColor, PorterDuff.Mode.MULTIPLY));
            undoImageView.setBackground(Theme.createSelectorDrawable((undoCancelColor & 0x00ffffff) | 0x19000000));
            ViewHelper.setPaddingRelative(undoImageView, 0, 12, 0, 12);
            addView(undoImageView, LayoutHelper.createFrameRelatively(56, 48, Gravity.CENTER_VERTICAL));
        }

        public void undo() {
            if (bulletin != null) {
                isUndone = true;
                if (undoAction != null) {
                    undoAction.run();
                }
                bulletin.hide();
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
    }
    //endregion
}
