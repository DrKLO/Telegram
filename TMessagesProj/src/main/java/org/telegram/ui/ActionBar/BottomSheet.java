/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
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
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.android.AnimationCompat.AnimatorSetProxy;
import org.telegram.android.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.android.AnimationCompat.ViewProxy;
import org.telegram.android.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class BottomSheet extends Dialog {

    private LinearLayout linearLayout;
    private FrameLayout container;

    private boolean dismissed;
    private int tag;

    private DialogInterface.OnClickListener onClickListener;

    private CharSequence[] items;
    private int[] itemIcons;
    private View customView;
    private CharSequence title;
    private boolean overrideTabletWidth = true;
    private boolean isGrid;
    private ColorDrawable backgroundDrawable = new ColorDrawable(0xff000000);

    private int revealX;
    private int revealY;
    private boolean useRevealAnimation;

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private AccelerateInterpolator accelerateInterpolator = new AccelerateInterpolator();

    private ArrayList<BottomSheetCell> itemViews = new ArrayList<>();

    private BottomSheetDelegate delegate;

    public interface BottomSheetDelegate {
        void onOpenAnimationStart();
        void onOpenAnimationEnd();
    }

    private static class BottomSheetCell extends FrameLayout {

        private TextView textView;
        private ImageView imageView;
        private boolean isGrid;

        public BottomSheetCell(Context context, boolean grid) {
            super(context);
            isGrid = grid;

            setBackgroundResource(R.drawable.list_selector);
            if (!grid) {
                setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            }

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            if (grid) {
                addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 8, 0, 0));
            } else {
                addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
            }

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            if (grid) {
                textView.setTextColor(0xff757575);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 60, 0, 0));
            } else {
                textView.setTextColor(0xff212121);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(isGrid ? MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(96), MeasureSpec.EXACTLY) : widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(isGrid ? 80 : 48), MeasureSpec.EXACTLY));
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

    public BottomSheet(Context context) {
        super(context);

        container = new FrameLayout(getContext());
        container.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                dismiss();
                return false;
            }
        });
        container.setBackgroundDrawable(backgroundDrawable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setBackgroundDrawableResource(R.drawable.transparent);
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.setWindowAnimations(R.style.DialogNoAnimation);

        setContentView(container);

        linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        if (AndroidUtilities.isTablet() && !overrideTabletWidth) {
            container.addView(linearLayout, 0, LayoutHelper.createFrame(320, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        } else {
            container.addView(linearLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        }

        View shadow = new View(getContext());
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        linearLayout.addView(shadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3));

        LinearLayout containerView = new LinearLayout(getContext());
        containerView.setBackgroundColor(0xffffffff);
        containerView.setOrientation(LinearLayout.VERTICAL);
        containerView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(isGrid ? 16 : 8));
        linearLayout.addView(containerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (title != null) {
            TextView titleView = new TextView(getContext());
            titleView.setLines(1);
            titleView.setSingleLine(true);
            titleView.setText(title);
            titleView.setTextColor(0xff757575);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
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
                BottomSheetCell cell = new BottomSheetCell(getContext(), isGrid);
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

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        }
        getWindow().setAttributes(params);

        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                if (useRevealAnimation) {
                    int finalRadius = Math.max(AndroidUtilities.displaySize.x, container.getHeight());
                    Animator anim = ViewAnimationUtils.createCircularReveal(container, revealX, revealY, 0, finalRadius);
                    anim.setDuration(400);
                    anim.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            if (delegate != null) {
                                delegate.onOpenAnimationStart();
                            }
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (delegate != null) {
                                delegate.onOpenAnimationEnd();
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    anim.start();
                } else {
                    //startLayoutAnimation(true, true);
                    ViewProxy.setTranslationY(linearLayout, linearLayout.getHeight());
                    backgroundDrawable.setAlpha(0);
                    AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
                    animatorSetProxy.playTogether(
                            ObjectAnimatorProxy.ofFloat(linearLayout, "translationY", 0),
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
                        }
                    });
                    animatorSetProxy.start();
                }
            }
        });
    }

    private float animationProgress;
    private long lastFrameTime;
    private void startLayoutAnimation(final boolean open, final boolean first) {
        if (first) {
            animationProgress = 0.0f;
            lastFrameTime = System.nanoTime() / 1000000;
            if (Build.VERSION.SDK_INT >= 11) {
                container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                long newTime = System.nanoTime() / 1000000;
                long dt = newTime - lastFrameTime;
                FileLog.e("tmessages", "dt = " + dt);
                if (dt > 16) {
                    dt = 16;
                }
                lastFrameTime = newTime;
                animationProgress += dt / 200.0f;
                if (animationProgress > 1.0f) {
                    animationProgress = 1.0f;
                }

                if (open) {
                    float interpolated = decelerateInterpolator.getInterpolation(animationProgress);
                    ViewProxy.setTranslationY(linearLayout, linearLayout.getHeight() * (1.0f - interpolated));
                    backgroundDrawable.setAlpha((int) (51 * interpolated));
                } else {
                    float interpolated = accelerateInterpolator.getInterpolation(animationProgress);
                    ViewProxy.setTranslationY(linearLayout, linearLayout.getHeight() * interpolated);
                    backgroundDrawable.setAlpha((int) (51 * (1.0f - interpolated)));
                }
                if (animationProgress < 1) {
                    startLayoutAnimation(open, false);
                } else {
                    if (open && delegate != null) {
                        delegate.onOpenAnimationEnd();
                    }
                }
            }
        });
    }

    public void setDelegate(BottomSheetDelegate delegate) {
        this.delegate = delegate;
    }

    public FrameLayout getContainer() {
        return container;
    }

    public LinearLayout getSheetContainer() {
        return linearLayout;
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

    private void dismissWithButtonClick(final int item) {
        if (dismissed) {
            return;
        }
        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
        animatorSetProxy.playTogether(
                ObjectAnimatorProxy.ofFloat(linearLayout, "translationY", linearLayout.getHeight() + AndroidUtilities.dp(10)),
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

            @Override
            public void onAnimationCancel(Object animation) {
                onAnimationEnd(animation);
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
        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
        animatorSetProxy.playTogether(
                ObjectAnimatorProxy.ofFloat(linearLayout, "translationY", linearLayout.getHeight() + AndroidUtilities.dp(10)),
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

            @Override
            public void onAnimationCancel(Object animation) {
                onAnimationEnd(animation);
            }
        });
        animatorSetProxy.start();
    }

    public static class Builder {

        private BottomSheet bottomSheet;

        public Builder(Context context) {
            bottomSheet = new BottomSheet(context);
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

        public Builder setRevealAnimation(int x, int y) {
            bottomSheet.revealX = x;
            bottomSheet.revealY = y;
            bottomSheet.useRevealAnimation = true;
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

        public BottomSheet setOverrideTabletWidth(boolean value) {
            bottomSheet.overrideTabletWidth = value;
            return bottomSheet;
        }
    }
}
