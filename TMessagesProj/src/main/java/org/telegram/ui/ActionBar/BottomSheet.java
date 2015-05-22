/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.android.AnimationCompat.AnimatorSetProxy;
import org.telegram.android.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.android.LocaleController;
import org.telegram.messenger.FileLog;
import com.aniways.anigram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

public class BottomSheet extends Dialog {

    private LinearLayout linearLayout;
    private FrameLayout container;
    private boolean dismissed;

    private DialogInterface.OnClickListener onClickListener;

    private CharSequence[] items;
    private View customView;
    private boolean overrideTabletWidth = true;

    private BottomSheetDelegate delegate;

    public interface BottomSheetDelegate {
        void onOpenAnimationEnd();
    }

    private static class BottomSheetRow extends FrameLayout {

        private TextView textView;

        public BottomSheetRow(Context context) {
            super(context);

            setBackgroundResource(R.drawable.list_selector);
            setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

            textView = new TextView(context);
            textView.setTextColor(0xff212121);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
        <item name="android:windowFrame">@null</item>
        <item name="android:textColor">@null</item>
        <item name="android:layout_width">fill_parent</item>
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowIsFloating">true</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowAnimationStyle">@style/BottomSheet.Animation</item>
        <item name="android:textColorPrimary">#DD000000</item>
        <item name="android:textColorSecondary">#8A000000</item>
        <item name="android:textColorHint">#42000000</item>
         */

        Window window = getWindow();
        window.setBackgroundDrawableResource(R.drawable.transparent);
        window.requestFeature(Window.FEATURE_NO_TITLE);

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
        containerView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        linearLayout.addView(containerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (items != null) {
            for (int a = 0; a < items.length; a++) {
                CharSequence charSequence = items[a];
                BottomSheetRow row = new BottomSheetRow(getContext());
                row.textView.setText(charSequence);
                containerView.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                row.setTag(a);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dismissWithButtonClick((Integer) v.getTag());
                    }
                });
            }
        }
        if (customView != null) {
            containerView.addView(customView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.dimAmount = 0.2f;
        params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        }
        getWindow().setAttributes(params);

        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
                animatorSetProxy.playTogether(
                        ObjectAnimatorProxy.ofFloat(linearLayout, "translationY", linearLayout.getHeight(), 0));
                animatorSetProxy.setDuration(180);
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

    private void dismissWithButtonClick(final int item) {
        if (dismissed) {
            return;
        }
        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
        animatorSetProxy.playTogether(
                ObjectAnimatorProxy.ofFloat(linearLayout, "translationY", linearLayout.getHeight() + AndroidUtilities.dp(10))
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
                ObjectAnimatorProxy.ofFloat(linearLayout, "translationY", linearLayout.getHeight() + AndroidUtilities.dp(10))
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

        public Builder setCustomView(View view) {
            bottomSheet.customView = view;
            return this;
        }

        public BottomSheet create() {
            return bottomSheet;
        }

        public BottomSheet show() {
            bottomSheet.show();
            return bottomSheet;
        }

        public BottomSheet setOverrideTabletWidth(boolean value) {
            bottomSheet.overrideTabletWidth = value;
            return bottomSheet;
        }
    }
}
