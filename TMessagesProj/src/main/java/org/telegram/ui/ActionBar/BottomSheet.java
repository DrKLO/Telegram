/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class BottomSheet extends Dialog {

    private LinearLayout containerView;
    private FrameLayout container;
    private WindowInsets lastInsets;

    private boolean dismissed;
    private int tag;

    private boolean disableBackground;

    private DialogInterface.OnClickListener onClickListener;

    private CharSequence[] items;
    private int[] itemIcons;
    private View customView;
    private CharSequence title;
    private boolean fullWidth;
    private boolean isGrid;
    private ColorDrawable backgroundDrawable = new ColorDrawable(0xff000000);

    private boolean focusable;

    private Paint ciclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Drawable shadowDrawable;
    protected static int backgroundPaddingTop;
    protected static int backgroundPaddingLeft;

    private boolean useRevealAnimation;
    private float revealRadius;
    private int revealX;
    private int revealY;
    private boolean applyTopPadding = true;
    private boolean applyBottomPadding = true;

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private AccelerateInterpolator accelerateInterpolator = new AccelerateInterpolator();

    private ArrayList<BottomSheetCell> itemViews = new ArrayList<>();

    private BottomSheetDelegateInterface delegate;

    public interface BottomSheetDelegateInterface {
        void onOpenAnimationStart();

        void onOpenAnimationEnd();

        void onRevealAnimationStart(boolean open);

        void onRevealAnimationEnd(boolean open);

        void onRevealAnimationProgress(boolean open, float radius, int x, int y);

        View getRevealView();
    }

    public static class BottomSheetDelegate implements BottomSheetDelegateInterface {
        @Override
        public void onOpenAnimationStart() {

        }

        @Override
        public void onOpenAnimationEnd() {

        }

        @Override
        public void onRevealAnimationStart(boolean open) {

        }

        @Override
        public void onRevealAnimationEnd(boolean open) {

        }

        @Override
        public void onRevealAnimationProgress(boolean open, float radius, int x, int y) {

        }

        @Override
        public View getRevealView() {
            return null;
        }
    }

    public static class BottomSheetCell extends FrameLayout {

        private TextView textView;
        private ImageView imageView;
        private boolean isGrid;

        public BottomSheetCell(Context context, int type) {
            super(context);
            isGrid = type == 1;

            setBackgroundResource(R.drawable.list_selector);
            if (type != 1) {
                setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            }

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            if (type == 1) {
                addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));
            } else {
                addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
            }

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            if (type == 1) {
                textView.setTextColor(0xff757575);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 60, 0, 0));
            } else if (type == 0) {
                textView.setTextColor(0xff212121);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
            } else if (type == 2) {
                textView.setGravity(Gravity.CENTER);
                textView.setTextColor(0xff212121);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(isGrid ? MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(96), MeasureSpec.EXACTLY) : widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(isGrid ? 80 : 48), MeasureSpec.EXACTLY));
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
                if (!isGrid) {
                    textView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(56), 0, LocaleController.isRTL ? AndroidUtilities.dp(56) : 0, 0);
                }
            } else {
                imageView.setVisibility(INVISIBLE);
                textView.setPadding(0, 0, 0, 0);
            }
        }
    }

    public BottomSheet(Context context, boolean needFocus) {
        super(context, R.style.TransparentDialog);

        if (Build.VERSION.SDK_INT >= 21 && !"N".equals(Build.VERSION.CODENAME)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        Rect padding = new Rect();
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow);
        shadowDrawable.getPadding(padding);
        backgroundPaddingLeft = padding.left;
        backgroundPaddingTop = padding.top;

        container = new FrameLayout(getContext()) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                    width -= lastInsets.getSystemWindowInsetRight() + lastInsets.getSystemWindowInsetLeft();
                }

                setMeasuredDimension(width, height);
                boolean isPortrait = width < height;

                if (containerView != null) {
                    int left = useRevealAnimation && Build.VERSION.SDK_INT <= 19 ? 0 : backgroundPaddingLeft;
                    if (!fullWidth) {
                        int widthSpec;
                        if (AndroidUtilities.isTablet()) {
                            widthSpec = MeasureSpec.makeMeasureSpec((int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.8f) + left * 2, MeasureSpec.EXACTLY);
                        } else {
                            widthSpec = MeasureSpec.makeMeasureSpec(isPortrait ? width + left * 2 : (int) Math.max(width * 0.8f, Math.min(AndroidUtilities.dp(480), width)) + left * 2, MeasureSpec.EXACTLY);
                        }
                        if (lastInsets != null && Build.VERSION.SDK_INT >= 21 && focusable) {
                            containerView.getLayoutParams();
                            containerView.measure(widthSpec, MeasureSpec.makeMeasureSpec(height - lastInsets.getSystemWindowInsetBottom(), MeasureSpec.AT_MOST));
                        } else {
                            containerView.measure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
                        }
                    } else {
                        containerView.measure(MeasureSpec.makeMeasureSpec(width + left * 2, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
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
                if (containerView != null) {
                    int t = (bottom - top) - containerView.getMeasuredHeight();
                    if (lastInsets != null && Build.VERSION.SDK_INT >= 21) {
                        left += lastInsets.getSystemWindowInsetLeft();
                        right += lastInsets.getSystemWindowInsetLeft();
                        if (focusable) {
                            t -= lastInsets.getSystemWindowInsetBottom();
                        }
                    }
                    int l = ((right - left) - containerView.getMeasuredWidth()) / 2;
                    containerView.layout(l, t, l + containerView.getMeasuredWidth(), t + getMeasuredHeight());
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
            }
        };
        container.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                dismiss();
                return false;
            }
        });
        container.setBackgroundDrawable(backgroundDrawable);
        focusable = needFocus;
        if (Build.VERSION.SDK_INT >= 21 && !"N".equals(Build.VERSION.CODENAME)) {
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(container, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ciclePaint.setColor(0xffffffff);

        containerView = new LinearLayout(getContext()) {

            @Override
            protected void onDraw(Canvas canvas) {
                if (useRevealAnimation && Build.VERSION.SDK_INT <= 19) {
                    canvas.drawCircle(revealX, revealY, revealRadius, ciclePaint);
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                FileLog.e("tmessages", "container on layout");
            }
        };
        if (Build.VERSION.SDK_INT >= 21) {
            containerView.setFitsSystemWindows(true);
        }
        containerView.setVisibility(View.INVISIBLE);
        backgroundDrawable.setAlpha(0);
        containerView.setWillNotDraw(false);
        containerView.setOrientation(LinearLayout.VERTICAL);
        container.addView(containerView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

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
            containerView.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            titleView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
        }

        if (customView != null) {
            if (customView.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) customView.getParent();
                viewGroup.removeView(customView);
            }
            containerView.addView(customView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        if (items != null) {
            if (customView != null) {
                FrameLayout frameLayout = new FrameLayout(getContext());
                frameLayout.setPadding(0, AndroidUtilities.dp(8), 0, 0);
                containerView.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16));

                View lineView = new View(getContext());
                lineView.setBackgroundColor(0xffd2d2d2);
                frameLayout.addView(lineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            FrameLayout rowLayout = null;
            int lastRowLayoutNum = 0;
            for (int a = 0; a < items.length; a++) {
                BottomSheetCell cell = new BottomSheetCell(getContext(), isGrid ? 1 : 0);
                cell.setTextAndIcon(items[a], itemIcons != null ? itemIcons[a] : 0);
                if (isGrid) {
                    int row = a / 3;
                    if (rowLayout == null || lastRowLayoutNum != row) {
                        rowLayout = new FrameLayout(getContext());
                        lastRowLayoutNum = row;
                        containerView.addView(rowLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 80, 0, lastRowLayoutNum != 0 ? 8 : 0, 0, 0));
                        rowLayout.setOnTouchListener(new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                return true;
                            }
                        });
                    }
                    int col = a % 3;
                    int gravity;
                    if (col == 0) {
                        gravity = Gravity.LEFT | Gravity.TOP;
                    } else if (col == 1) {
                        gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    } else {
                        gravity = Gravity.RIGHT | Gravity.TOP;
                    }
                    rowLayout.addView(cell, LayoutHelper.createFrame(96, 80, gravity));
                } else {
                    containerView.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                }
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

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        if (!focusable) {
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        if (Build.VERSION.SDK_INT < 21) {
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        window.setAttributes(params);
    }

    @Override
    public void show() {
        super.show();
        if (focusable) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dismissed = false;
        if ((Build.VERSION.SDK_INT >= 21 || !useRevealAnimation) && !disableBackground) {
            containerView.setBackgroundDrawable(shadowDrawable);
        } else {
            containerView.setBackgroundDrawable(null);
        }
        int left = useRevealAnimation && Build.VERSION.SDK_INT <= 19 || disableBackground ? 0 : backgroundPaddingLeft;
        int top = useRevealAnimation && Build.VERSION.SDK_INT <= 19 || disableBackground ? 0 : backgroundPaddingTop;
        containerView.setPadding(left, (applyTopPadding ? AndroidUtilities.dp(8) : 0) + top, left, (applyBottomPadding ? AndroidUtilities.dp(isGrid ? 16 : 8) : 0));
        if (Build.VERSION.SDK_INT >= 21) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    startOpenAnimation();
                }
            });
        } else {
            startOpenAnimation();
        }
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

    public void setDisableBackground(boolean value) {
        disableBackground = value;
    }

    protected void setRevealRadius(float radius) {
        revealRadius = radius;
        delegate.onRevealAnimationProgress(!dismissed, radius, revealX, revealY);
        if (Build.VERSION.SDK_INT <= 19) {
            containerView.invalidate();
        }
    }

    protected float getRevealRadius() {
        return revealRadius;
    }

    @SuppressLint("NewApi")
    private void startRevealAnimation(final boolean open) {
        ViewProxy.setTranslationY(containerView, 0);

        final AnimatorSet animatorSet = new AnimatorSet();

        View view = delegate.getRevealView();
        if (view.getVisibility() == View.VISIBLE && ((ViewGroup) view.getParent()).getVisibility() == View.VISIBLE) {
            final int coords[] = new int[2];
            view.getLocationInWindow(coords);
            float top;
            if (Build.VERSION.SDK_INT <= 19) {
                top = AndroidUtilities.displaySize.y - containerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight;
            } else {
                top = containerView.getY();
            }
            revealX = coords[0] + view.getMeasuredWidth() / 2;
            revealY = (int) (coords[1] + view.getMeasuredHeight() / 2 - top);
            if (Build.VERSION.SDK_INT <= 19) {
                revealY -= AndroidUtilities.statusBarHeight;
            }
        } else {
            revealX = AndroidUtilities.displaySize.x / 2 + backgroundPaddingLeft;
            revealY = (int) (AndroidUtilities.displaySize.y - containerView.getY());
        }

        int corners[][] = new int[][]{
                {0, 0},
                {0, containerView.getMeasuredHeight()},
                {containerView.getMeasuredWidth(), 0},
                {containerView.getMeasuredWidth(), containerView.getMeasuredHeight()}
        };
        int finalRevealRadius = 0;
        for (int a = 0; a < 4; a++) {
            finalRevealRadius = Math.max(finalRevealRadius, (int) Math.ceil(Math.sqrt((revealX - corners[a][0]) * (revealX - corners[a][0]) + (revealY - corners[a][1]) * (revealY - corners[a][1]))));
        }
        int finalRevealX = revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth();

        ArrayList<Animator> animators = new ArrayList<>(3);
        animators.add(ObjectAnimator.ofFloat(this, "revealRadius", open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
        animators.add(ObjectAnimator.ofInt(backgroundDrawable, "alpha", open ? 51 : 0));
        if (Build.VERSION.SDK_INT >= 21) {
            containerView.setElevation(AndroidUtilities.dp(10));
            try {
                animators.add(ViewAnimationUtils.createCircularReveal(containerView, finalRevealX, revealY, open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            animatorSet.setDuration(300);
        } else {
            if (!open) {
                animatorSet.setDuration(200);
                containerView.setPivotX(revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth());
                containerView.setPivotY(revealY);
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleX", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleY", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "alpha", 0.0f));
            } else {
                animatorSet.setDuration(250);
                containerView.setScaleX(1);
                containerView.setScaleY(1);
                containerView.setAlpha(1);
                if (Build.VERSION.SDK_INT <= 19) {
                    animatorSet.setStartDelay(20);
                }
            }
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (delegate != null) {
                    delegate.onRevealAnimationStart(open);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (delegate != null) {
                    delegate.onRevealAnimationEnd(open);
                }
                containerView.invalidate();
                if (Build.VERSION.SDK_INT >= 11) {
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                if (!open) {
                    containerView.setVisibility(View.INVISIBLE);
                    try {
                        BottomSheet.super.dismiss();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }
        });
        animatorSet.start();
    }

    private void startOpenAnimation() {
        if (containerView.getMeasuredHeight() == 0) {
            containerView.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.y, View.MeasureSpec.AT_MOST));
        }
        backgroundDrawable.setAlpha(0);
        containerView.setVisibility(View.VISIBLE);
        if (useRevealAnimation) {
            if (Build.VERSION.SDK_INT >= 20) {
                containerView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            startRevealAnimation(true);
        } else {
            if (Build.VERSION.SDK_INT >= 20) {
                container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
            ViewProxy.setTranslationY(containerView, containerView.getMeasuredHeight());
            backgroundDrawable.setAlpha(0);
            AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
            animatorSetProxy.playTogether(
                    ObjectAnimatorProxy.ofFloat(containerView, "translationY", 0),
                    ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 51));
            animatorSetProxy.setDuration(200);
            animatorSetProxy.setStartDelay(20);
            animatorSetProxy.setInterpolator(new DecelerateInterpolator());
            animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    if (delegate != null) {
                        delegate.onOpenAnimationEnd();
                    }
                    if (Build.VERSION.SDK_INT >= 11) {
                        container.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                }
            });
            animatorSetProxy.start();
        }
    }

    public void setDelegate(BottomSheetDelegate delegate) {
        this.delegate = delegate;
    }

    public FrameLayout getContainer() {
        return container;
    }

    public LinearLayout getSheetContainer() {
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

    public void dismissWithButtonClick(final int item) {
        if (dismissed) {
            return;
        }
        dismissed = true;
        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
        animatorSetProxy.playTogether(
                ObjectAnimatorProxy.ofFloat(containerView, "translationY", containerView.getMeasuredHeight() + AndroidUtilities.dp(10)),
                ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0)
        );
        animatorSetProxy.setDuration(180);
        animatorSetProxy.setInterpolator(new AccelerateInterpolator());
        animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Object animation) {
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
        });
        animatorSetProxy.start();
    }

    @Override
    public void dismiss() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        if (useRevealAnimation) {
            backgroundDrawable.setAlpha(51);
            startRevealAnimation(false);
        } else {
            AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
            animatorSetProxy.playTogether(
                    ObjectAnimatorProxy.ofFloat(containerView, "translationY", containerView.getMeasuredHeight() + AndroidUtilities.dp(10)),
                    ObjectAnimatorProxy.ofInt(backgroundDrawable, "alpha", 0)
            );
            animatorSetProxy.setDuration(180);
            animatorSetProxy.setInterpolator(new AccelerateInterpolator());
            animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
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
            });
            animatorSetProxy.start();
        }
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

        public Builder setUseRevealAnimation() {
            if (Build.VERSION.SDK_INT >= 18 && !AndroidUtilities.isTablet()) {
                bottomSheet.useRevealAnimation = true;
            }
            return this;
        }

        public Builder setDelegate(BottomSheetDelegate delegate) {
            bottomSheet.setDelegate(delegate);
            return this;
        }

        public Builder setIsGrid(boolean value) {
            bottomSheet.isGrid = value;
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
