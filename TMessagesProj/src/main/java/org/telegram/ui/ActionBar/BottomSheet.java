/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimatorListenerAdapterProxy;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class BottomSheet extends Dialog {

    protected ViewGroup containerView;
    private ContainerView container;
    private WindowInsets lastInsets;

    private Runnable startAnimationRunnable;
    private int layoutCount;

    private boolean dismissed;
    private int tag;

    private DialogInterface.OnClickListener onClickListener;

    private CharSequence[] items;
    private int[] itemIcons;
    private View customView;
    private CharSequence title;
    private boolean fullWidth;
    protected ColorDrawable backDrawable = new ColorDrawable(0xff000000);

    private boolean allowCustomAnimation = true;

    private int touchSlop;
    private boolean useFastDismiss;

    private boolean focusable;

    protected Paint ciclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Drawable shadowDrawable;
    protected static int backgroundPaddingTop;
    protected static int backgroundPaddingLeft;

    private boolean applyTopPadding = true;
    private boolean applyBottomPadding = true;

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private AccelerateInterpolator accelerateInterpolator = new AccelerateInterpolator();

    private ArrayList<BottomSheetCell> itemViews = new ArrayList<>();

    private BottomSheetDelegateInterface delegate;

    protected AnimatorSet currentSheetAnimation;

    private class ContainerView extends FrameLayout implements NestedScrollingParent {

        private VelocityTracker velocityTracker = null;
        private int startedTrackingX;
        private int startedTrackingY;
        private int startedTrackingPointerId;
        private boolean maybeStartTracking = false;
        private boolean startedTracking = false;
        private AnimatorSet currentAnimation = null;
        private NestedScrollingParentHelper nestedScrollingParentHelper;

        public ContainerView(Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
            return !dismissed && nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL && !canDismissWithSwipe();
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int nestedScrollAxes) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
            if (dismissed) {
                return;
            }
            cancelCurrentAnimation();
        }

        @Override
        public void onStopNestedScroll(View target) {
            nestedScrollingParentHelper.onStopNestedScroll(target);
            if (dismissed) {
                return;
            }
            float currentTranslation = containerView.getTranslationY();
            checkDismiss(0, 0);
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
            if (dismissed) {
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
            }
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
            if (dismissed) {
                return;
            }
            cancelCurrentAnimation();
            float currentTranslation = containerView.getTranslationY();
            if (currentTranslation > 0 && dy > 0) {
                currentTranslation -= dy;
                consumed[1] = dy;
                if (currentTranslation < 0) {
                    currentTranslation = 0;
                    consumed[1] += currentTranslation;
                }
                containerView.setTranslationY(currentTranslation);
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
                currentAnimation.playTogether(ObjectAnimator.ofFloat(containerView, "translationY", 0));
                currentAnimation.setDuration((int) (150 * (translationY / AndroidUtilities.getPixelsInCM(0.8f, false))));
                currentAnimation.setInterpolator(new DecelerateInterpolator());
                currentAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentAnimation != null && currentAnimation.equals(animation)) {
                            currentAnimation = null;
                        }
                    }
                });
                currentAnimation.start();
            }
        }

        private void cancelCurrentAnimation() {
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (dismissed) {
                return false;
            }
            if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && !startedTracking && !maybeStartTracking) {
                startedTrackingX = (int) ev.getX();
                startedTrackingY = (int) ev.getY();
                if (startedTrackingY < containerView.getTop() || startedTrackingX < containerView.getLeft() || startedTrackingX > containerView.getRight()) {
                    dismiss();
                    return true;
                }
                startedTrackingPointerId = ev.getPointerId(0);
                maybeStartTracking = true;
                cancelCurrentAnimation();
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                float dx = Math.abs((int) (ev.getX() - startedTrackingX));
                float dy = (int) ev.getY() - startedTrackingY;
                velocityTracker.addMovement(ev);
                if (maybeStartTracking && !startedTracking && (dy > 0 && dy / 3.0f > Math.abs(dx) && Math.abs(dy) >= touchSlop)) {
                    startedTrackingY = (int) ev.getY();
                    maybeStartTracking = false;
                    startedTracking = true;
                    requestDisallowInterceptTouchEvent(true);
                } else if (startedTracking) {
                    float translationY = containerView.getTranslationY();
                    translationY += dy;
                    if (translationY < 0) {
                        translationY = 0;
                    }
                    containerView.setTranslationY(translationY);
                    startedTrackingY = (int) ev.getY();
                }
            } else if (ev == null || ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.computeCurrentVelocity(1000);
                float translationY = containerView.getTranslationY();
                if (startedTracking || translationY != 0) {
                    checkDismiss(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
                    startedTracking = false;
                } else {
                    maybeStartTracking = false;
                    startedTracking = false;
                }
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            }
            return startedTracking;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                width -= lastInsets.getSystemWindowInsetRight() + lastInsets.getSystemWindowInsetLeft();
                height -= lastInsets.getSystemWindowInsetBottom();
            }

            setMeasuredDimension(width, height);
            boolean isPortrait = width < height;

            if (containerView != null) {
                if (!fullWidth) {
                    int widthSpec;
                    if (AndroidUtilities.isTablet()) {
                        widthSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f) + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY);
                    } else {
                        widthSpec = MeasureSpec.makeMeasureSpec(isPortrait ? width + backgroundPaddingLeft * 2 : (int) Math.max(width * 0.8f, Math.min(AndroidUtilities.dp(480), width)) + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY);
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
                measureChildWithMargins(child, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY), 0);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            layoutCount--;
            if (containerView != null) {
                int t = (bottom - top) - containerView.getMeasuredHeight();
                if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                    left += lastInsets.getSystemWindowInsetLeft();
                    right += lastInsets.getSystemWindowInsetLeft();
                }
                int l = ((right - left) - containerView.getMeasuredWidth()) / 2;
                containerView.layout(l, t, l + containerView.getMeasuredWidth(), t + containerView.getMeasuredHeight());
            }

            final int count = getChildCount();
            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE || child == containerView) {
                    continue;
                }
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
                    case Gravity.TOP:
                        childTop = lp.topMargin;
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = (bottom - top - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = (bottom - top) - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
            if (layoutCount == 0 && startAnimationRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(startAnimationRunnable);
                startAnimationRunnable.run();
                startAnimationRunnable = null;
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (canDismissWithSwipe()) {
                return onTouchEvent(event);
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
    }

    public interface BottomSheetDelegateInterface {
        void onOpenAnimationStart();
        void onOpenAnimationEnd();
    }

    public static class BottomSheetDelegate implements BottomSheetDelegateInterface {
        @Override
        public void onOpenAnimationStart() {

        }

        @Override
        public void onOpenAnimationEnd() {

        }
    }

    public static class BottomSheetCell extends FrameLayout {

        private TextView textView;
        private ImageView imageView;

        public BottomSheetCell(Context context, int type) {
            super(context);

            setBackgroundResource(R.drawable.list_selector);
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            if (type == 0) {
                textView.setTextColor(0xff212121);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
            } else if (type == 1) {
                textView.setGravity(Gravity.CENTER);
                textView.setTextColor(0xff212121);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }

        public void setTextColor(int color) {
            textView.setTextColor(color);
        }

        public void setGravity(int gravity) {
            textView.setGravity(gravity);
        }

        public void setTextAndIcon(CharSequence text, int icon) {
            textView.setText(text);
            if (icon != 0) {
                imageView.setImageResource(icon);
                imageView.setVisibility(VISIBLE);
                textView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(56), 0, LocaleController.isRTL ? AndroidUtilities.dp(56) : 0, 0);
            } else {
                imageView.setVisibility(INVISIBLE);
                textView.setPadding(0, 0, 0, 0);
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    public BottomSheet(Context context, boolean needFocus) {
        super(context, R.style.TransparentDialog);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();

        Rect padding = new Rect();
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow);
        shadowDrawable.getPadding(padding);
        backgroundPaddingLeft = padding.left;
        backgroundPaddingTop = padding.top;

        container = new ContainerView(getContext());
        container.setBackgroundDrawable(backDrawable);
        focusable = needFocus;
        if (Build.VERSION.SDK_INT >= 21) {
            container.setFitsSystemWindows(true);
            container.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @SuppressLint("NewApi")
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    lastInsets = insets;
                    v.requestLayout();
                    return insets.consumeSystemWindowInsets();
                }
            });
            container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        ciclePaint.setColor(0xffffffff);
        backDrawable.setAlpha(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(container, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (containerView == null) {
            containerView = new FrameLayout(getContext()) {
                @Override
                public boolean hasOverlappingRendering() {
                    return false;
                }
            };
            containerView.setBackgroundDrawable(shadowDrawable);
            containerView.setPadding(backgroundPaddingLeft, (applyTopPadding ? AndroidUtilities.dp(8) : 0) + backgroundPaddingTop, backgroundPaddingLeft, (applyBottomPadding ? AndroidUtilities.dp(8) : 0));
        }
        if (Build.VERSION.SDK_INT >= 21) {
            containerView.setFitsSystemWindows(true);
        }
        containerView.setVisibility(View.INVISIBLE);
        container.addView(containerView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        if (customView != null) {
            if (customView.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) customView.getParent();
                viewGroup.removeView(customView);
            }
            containerView.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        } else {
            int topOffset = 0;
            if (title != null) {
                TextView titleView = new TextView(getContext());
                titleView.setLines(1);
                titleView.setSingleLine(true);
                titleView.setText(title);
                titleView.setTextColor(0xff757575);
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                titleView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(8));
                titleView.setGravity(Gravity.CENTER_VERTICAL);
                containerView.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
                titleView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return true;
                    }
                });
                topOffset += 48;
            }
            if (items != null) {
                FrameLayout rowLayout = null;
                int lastRowLayoutNum = 0;
                for (int a = 0; a < items.length; a++) {
                    BottomSheetCell cell = new BottomSheetCell(getContext(), 0);
                    cell.setTextAndIcon(items[a], itemIcons != null ? itemIcons[a] : 0);
                    containerView.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, 0, topOffset, 0, 0));
                    topOffset += 48;
                    cell.setTag(a);
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dismissWithButtonClick((Integer) v.getTag());
                        }
                    });
                    itemViews.add(cell);
                }
            }
        }

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        if (!focusable) {
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        window.setAttributes(params);
    }

    @Override
    public void show() {
        super.show();
        if (focusable) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dismissed = false;
        cancelSheetAnimation();
        if (containerView.getMeasuredHeight() == 0) {
            containerView.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST));
        }
        backDrawable.setAlpha(0);
        if (Build.VERSION.SDK_INT >= 18) {
            layoutCount = 2;
            AndroidUtilities.runOnUIThread(startAnimationRunnable = new Runnable() {
                @Override
                public void run() {
                    if (startAnimationRunnable != this) {
                        return;
                    }
                    startAnimationRunnable = null;
                    startOpenAnimation();
                }
            }, 150);
        } else {
            startOpenAnimation();
        }
    }

    protected boolean canDismissWithSwipe() {
        return true;
    }

    public void setCustomView(View view) {
        customView = view;
    }

    public void setTitle(CharSequence value) {
        title = value;
    }

    public void setApplyTopPadding(boolean value) {
        applyTopPadding = value;
    }

    public void setApplyBottomPadding(boolean value) {
        applyBottomPadding = value;
    }

    private void cancelSheetAnimation() {
        if (currentSheetAnimation != null) {
            currentSheetAnimation.cancel();
            currentSheetAnimation = null;
        }
    }

    private void startOpenAnimation() {
        containerView.setVisibility(View.VISIBLE);

        if (!onCustomOpenAnimation()) {
            if (Build.VERSION.SDK_INT >= 20) {
                container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            containerView.setTranslationY(containerView.getMeasuredHeight());
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(containerView, "translationY", 0),
                    ObjectAnimator.ofInt(backDrawable, "alpha", 51));
            animatorSet.setDuration(200);
            animatorSet.setStartDelay(20);
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                        currentSheetAnimation = null;
                        if (delegate != null) {
                            delegate.onOpenAnimationEnd();
                        }
                        container.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                        currentSheetAnimation = null;
                    }
                }
            });
            animatorSet.start();
            currentSheetAnimation = animatorSet;
        }
    }

    public void setDelegate(BottomSheetDelegateInterface bottomSheetDelegate) {
        delegate = bottomSheetDelegate;
    }

    public FrameLayout getContainer() {
        return container;
    }

    public ViewGroup getSheetContainer() {
        return containerView;
    }

    public int getTag() {
        return tag;
    }

    public void setItemText(int item, CharSequence text) {
        if (item < 0 || item >= itemViews.size()) {
            return;
        }
        BottomSheetCell cell = itemViews.get(item);
        cell.textView.setText(text);
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
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(containerView, "translationY", containerView.getMeasuredHeight() + AndroidUtilities.dp(10)),
                ObjectAnimator.ofInt(backDrawable, "alpha", 0)
        );
        animatorSet.setDuration(180);
        animatorSet.setInterpolator(new AccelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    if (onClickListener != null) {
                        onClickListener.onClick(BottomSheet.this, item);
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                BottomSheet.super.dismiss();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    });
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                }
            }
        });
        animatorSet.start();
        currentSheetAnimation = animatorSet;
    }

    @Override
    public void dismiss() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        cancelSheetAnimation();
        if (!allowCustomAnimation || !onCustomCloseAnimation()) {
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(containerView, "translationY", containerView.getMeasuredHeight() + AndroidUtilities.dp(10)),
                    ObjectAnimator.ofInt(backDrawable, "alpha", 0)
            );
            if (useFastDismiss) {
                int height = containerView.getMeasuredHeight();
                animatorSet.setDuration(Math.max(60, (int) (180 * (height - containerView.getTranslationY()) / (float) height)));
                useFastDismiss = false;
            } else {
                animatorSet.setDuration(180);
            }
            animatorSet.setInterpolator(new AccelerateInterpolator());
            animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                        currentSheetAnimation = null;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    dismissInternal();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        });
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                        currentSheetAnimation = null;
                    }
                }
            });
            animatorSet.start();
            currentSheetAnimation = animatorSet;
        }
    }

    protected void dismissInternal() {
        super.dismiss();
    }

    protected boolean onCustomCloseAnimation() {
        return false;
    }

    protected boolean onCustomOpenAnimation() {
        return false;
    }

    public static class Builder {

        private BottomSheet bottomSheet;

        public Builder(Context context) {
            bottomSheet = new BottomSheet(context, false);
        }

        public Builder(Context context, boolean needFocus) {
            bottomSheet = new BottomSheet(context, needFocus);
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

        public Builder setTitle(CharSequence title) {
            bottomSheet.title = title;
            return this;
        }

        public BottomSheet create() {
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

        public BottomSheet setUseFullWidth(boolean value) {
            bottomSheet.fullWidth = value;
            return bottomSheet;
        }
    }
}
