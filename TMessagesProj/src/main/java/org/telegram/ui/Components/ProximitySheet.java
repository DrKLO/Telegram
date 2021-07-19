package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public class ProximitySheet extends FrameLayout {

    private VelocityTracker velocityTracker = null;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId = -1;
    private boolean maybeStartTracking = false;
    private boolean startedTracking = false;
    private AnimatorSet currentAnimation = null;
    private android.graphics.Rect rect = new Rect();
    private Paint backgroundPaint = new Paint();

    private boolean dismissed;

    private AnimatorSet currentSheetAnimation;
    private int currentSheetAnimationType;

    private ViewGroup containerView;

    private boolean useHardwareLayer = true;

    private int backgroundPaddingTop;
    private int backgroundPaddingLeft;

    private int touchSlop;
    private boolean useFastDismiss;
    private Interpolator openInterpolator = CubicBezierInterpolator.EASE_OUT_QUINT;

    private NumberPicker kmPicker;
    private NumberPicker mPicker;
    private onRadiusPickerChange onRadiusChange;
    private TextView buttonTextView;
    private TextView infoTextView;
    private boolean radiusSet;
    private TLRPC.User currentUser;
    private int totalWidth;
    private boolean useImperialSystem;

    private LinearLayout customView;

    private Runnable onDismissCallback;

    public interface onRadiusPickerChange {
        boolean run(boolean move, int param);
    }

    public ProximitySheet(Context context, TLRPC.User user, onRadiusPickerChange onRadius, onRadiusPickerChange onFinish, Runnable onDismiss) {
        super(context);
        setWillNotDraw(false);
        onDismissCallback = onDismiss;

        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();

        Rect padding = new Rect();
        Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        shadowDrawable.getPadding(padding);
        backgroundPaddingLeft = padding.left;

        containerView = new FrameLayout(getContext()) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        containerView.setBackgroundDrawable(shadowDrawable);
        containerView.setPadding(backgroundPaddingLeft, AndroidUtilities.dp(8) + padding.top - 1, backgroundPaddingLeft, 0);

        containerView.setVisibility(View.INVISIBLE);
        addView(containerView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        useImperialSystem = LocaleController.getUseImperialSystemType();
        currentUser = user;

        onRadiusChange = onRadius;

        kmPicker = new NumberPicker(context);
        kmPicker.setTextOffset(AndroidUtilities.dp(10));
        kmPicker.setItemCount(5);
        mPicker = new NumberPicker(context);
        mPicker.setItemCount(5);
        mPicker.setTextOffset(-AndroidUtilities.dp(10));

        customView = new LinearLayout(context) {

            boolean ignoreLayout = false;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                ignoreLayout = true;
                int count;
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    count = 3;
                } else {
                    count = 5;
                }
                kmPicker.setItemCount(count);
                mPicker.setItemCount(count);
                kmPicker.getLayoutParams().height = AndroidUtilities.dp(54) * count;
                mPicker.getLayoutParams().height = AndroidUtilities.dp(54) * count;
                ignoreLayout = false;
                int prewWidth = 0;
                totalWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (prewWidth != totalWidth) {
                    updateText(false, false);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        customView.setOrientation(LinearLayout.VERTICAL);

        FrameLayout titleLayout = new FrameLayout(context);
        customView.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString("LocationNotifiation", R.string.LocationNotifiation));
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        customView.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        long currentTime = System.currentTimeMillis();

        FrameLayout buttonContainer = new FrameLayout(context);

        infoTextView = new TextView(context);

        buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        linearLayout.addView(kmPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        kmPicker.setFormatter(value -> {
            if (useImperialSystem) {
                return LocaleController.formatString("MilesShort", R.string.MilesShort, value);
            } else {
                return LocaleController.formatString("KMetersShort", R.string.KMetersShort, value);
            }
        });
        kmPicker.setMinValue(0);
        kmPicker.setMaxValue(10);
        kmPicker.setWrapSelectorWheel(false);
        kmPicker.setTextOffset(AndroidUtilities.dp(20));
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {

            }
            updateText(true, true);
        };
        kmPicker.setOnValueChangedListener(onValueChangeListener);

        mPicker.setMinValue(0);
        mPicker.setMaxValue(10);
        mPicker.setWrapSelectorWheel(false);
        mPicker.setTextOffset(-AndroidUtilities.dp(20));
        linearLayout.addView(mPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        mPicker.setFormatter(value -> {
            if (useImperialSystem) {
                if (value == 1) {
                    return LocaleController.formatString("FootsShort", R.string.FootsShort, 250);
                } else {
                    if (value > 1) {
                        value--;
                    }
                    return String.format(Locale.US, ".%d", value);
                }
            } else {
                if (value == 1) {
                    return LocaleController.formatString("MetersShort", R.string.MetersShort, 50);
                } else {
                    if (value > 1) {
                        value--;
                    }
                    return LocaleController.formatString("MetersShort", R.string.MetersShort, value * 100);
                }
            }
        });
        mPicker.setOnValueChangedListener(onValueChangeListener);

        kmPicker.setValue(0);
        mPicker.setValue(6);

        customView.addView(buttonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));

        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setMaxLines(2);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        buttonContainer.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        buttonTextView.setOnClickListener(v -> {
            if (buttonTextView.getTag() != null) {
                return;
            }
            float value = getValue();
            if (onFinish.run(true, (int) Math.max(1, value))) {
                dismiss();
            }
        });

        infoTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        infoTextView.setGravity(Gravity.CENTER);
        infoTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        infoTextView.setAlpha(0.0f);
        infoTextView.setScaleX(0.5f);
        infoTextView.setScaleY(0.5f);
        buttonContainer.addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

        containerView.addView(customView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
    }

    public View getCustomView() {
        return customView;
    }

    public float getValue() {
        float value = kmPicker.getValue() * 1000;
        int second = mPicker.getValue();
        if (useImperialSystem) {
            if (second == 1) {
                value += 47.349f;
            } else {
                if (second > 1) {
                    second--;
                }
                value += second * 100;
            }
        } else {
            if (second == 1) {
                value += 50;
            } else {
                if (second > 1) {
                    second--;
                }
                value += second * 100;
            }
        }
        if (useImperialSystem) {
            value *= 1.60934f;
        }
        return value;
    }

    public boolean getRadiusSet() {
        return radiusSet;
    }

    public void setRadiusSet() {
        radiusSet = true;
    }

    public void updateText(boolean move, boolean animated) {
        float value = getValue();
        String distance = LocaleController.formatDistance(value, 2, useImperialSystem);
        if (onRadiusChange.run(move, (int) value) || currentUser == null) {
            if (currentUser == null) {
                buttonTextView.setText(LocaleController.formatString("LocationNotifiationButtonGroup", R.string.LocationNotifiationButtonGroup, distance));
            } else {
                String format = LocaleController.getString("LocationNotifiationButtonUser", R.string.LocationNotifiationButtonUser);
                int width = (int) Math.ceil(buttonTextView.getPaint().measureText(format));
                int restWidth = (int) ((totalWidth - AndroidUtilities.dp(32 + 62)) * 1.5f - width);
                CharSequence name = TextUtils.ellipsize(UserObject.getFirstName(currentUser), buttonTextView.getPaint(), Math.max(AndroidUtilities.dp(10), restWidth), TextUtils.TruncateAt.END);
                buttonTextView.setText(LocaleController.formatString("LocationNotifiationButtonUser", R.string.LocationNotifiationButtonUser, name, distance));
            }
            if (buttonTextView.getTag() != null) {
                buttonTextView.setTag(null);
                buttonTextView.animate().setDuration(180).alpha(1.0f).scaleX(1.0f).scaleY(1.0f).start();
                infoTextView.animate().setDuration(180).alpha(0.0f).scaleX(0.5f).scaleY(0.5f).start();
            }
        } else {
            infoTextView.setText(LocaleController.formatString("LocationNotifiationCloser", R.string.LocationNotifiationCloser, distance));
            if (buttonTextView.getTag() == null) {
                buttonTextView.setTag(1);
                buttonTextView.animate().setDuration(180).alpha(0.0f).scaleX(0.5f).scaleY(0.5f).start();
                infoTextView.animate().setDuration(180).alpha(1.0f).scaleX(1.0f).scaleY(1.0f).start();
            }
        }
    }

    private void checkDismiss(float velX, float velY) {
        float translationY = containerView.getTranslationY();
        boolean backAnimation = translationY < AndroidUtilities.getPixelsInCM(0.8f, false) && (velY < 3500 || Math.abs(velY) < Math.abs(velX)) || velY < 0 && Math.abs(velY) >= 3500;
        if (!backAnimation) {
            useFastDismiss = true;
            dismiss();
        } else {
            currentAnimation = new AnimatorSet();
            currentAnimation.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0));
            currentAnimation.setDuration((int) (150 * (Math.max(0, translationY) / AndroidUtilities.getPixelsInCM(0.8f, false))));
            currentAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
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

    boolean processTouchEvent(MotionEvent ev, boolean intercept) {
        if (dismissed) {
            return false;
        }
        if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && (!startedTracking && !maybeStartTracking && ev.getPointerCount() == 1)) {
            startedTrackingX = (int) ev.getX();
            startedTrackingY = (int) ev.getY();
            if (startedTrackingY < containerView.getTop() || startedTrackingX < containerView.getLeft() || startedTrackingX > containerView.getRight()) {
                requestDisallowInterceptTouchEvent(true);
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
        } else if (ev == null || ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
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
            startedTrackingPointerId = -1;
        }
        return !intercept && maybeStartTracking || startedTracking;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return dismissed || processTouchEvent(ev, false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        View rootView = getRootView();
        getWindowVisibleDisplayFrame(rect);

        setMeasuredDimension(width, height);

        containerView.measure(MeasureSpec.makeMeasureSpec(width + backgroundPaddingLeft * 2, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
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
        int t = (bottom - top) - containerView.getMeasuredHeight();
        int l = ((right - left) - containerView.getMeasuredWidth()) / 2;
        containerView.layout(l, t, l + containerView.getMeasuredWidth(), t + containerView.getMeasuredHeight());

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
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return dismissed || processTouchEvent(event, true);
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

    public void show() {
        dismissed = false;
        cancelSheetAnimation();
        containerView.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x + backgroundPaddingLeft * 2, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST));
        startOpenAnimation();
        updateText(true, false);
    }

    private void cancelSheetAnimation() {
        if (currentSheetAnimation != null) {
            currentSheetAnimation.cancel();
            currentSheetAnimation = null;
            currentSheetAnimationType = 0;
        }
    }

    private void startOpenAnimation() {
        if (dismissed) {
            return;
        }
        containerView.setVisibility(View.VISIBLE);

        if (Build.VERSION.SDK_INT >= 20 && useHardwareLayer) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        containerView.setTranslationY(containerView.getMeasuredHeight());
        currentSheetAnimationType = 1;
        currentSheetAnimation = new AnimatorSet();
        currentSheetAnimation.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0));
        currentSheetAnimation.setDuration(400);
        currentSheetAnimation.setStartDelay(20);
        currentSheetAnimation.setInterpolator(openInterpolator);
        currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    currentSheetAnimationType = 0;
                    if (useHardwareLayer) {
                        setLayerType(View.LAYER_TYPE_NONE, null);
                    }
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

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (dismissed) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void dismiss() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        cancelSheetAnimation();

        currentSheetAnimationType = 2;
        currentSheetAnimation = new AnimatorSet();
        currentSheetAnimation.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, containerView.getMeasuredHeight() + AndroidUtilities.dp(10)));
        if (useFastDismiss) {
            int height = containerView.getMeasuredHeight();
            currentSheetAnimation.setDuration(Math.max(60, (int) (250 * (height - containerView.getTranslationY()) / (float) height)));
            useFastDismiss = false;
        } else {
            currentSheetAnimation.setDuration(250);
        }
        currentSheetAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
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

    private void dismissInternal() {
        if (getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) getParent();
            parent.removeView(this);
        }
        onDismissCallback.run();
    }
}
