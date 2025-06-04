/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AttachableDrawable;
import org.telegram.ui.Components.EffectsTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;

import java.util.ArrayList;
import java.util.Map;

public class AlertDialog extends Dialog implements Drawable.Callback, NotificationCenter.NotificationCenterDelegate {

    public static final int ALERT_TYPE_MESSAGE = 0;
    public static final int ALERT_TYPE_LOADING = 2;
    public static final int ALERT_TYPE_SPINNER = 3;

    private int customWidth = -1;
    private boolean customMaxHeight;
    private View customView;
    private View bottomView;
    private View aboveMessageView;
    private int customViewHeight = LayoutHelper.WRAP_CONTENT;
    private SpoilersTextView titleTextView;
    private TextView secondTitleTextView;
    private TextView subtitleTextView;
    private TextView messageTextView;
    private FrameLayout progressViewContainer;
    private FrameLayout titleContainer;
    private TextView progressViewTextView;
    private ScrollView contentScrollView;
    private LinearLayout scrollContainer;
    private ViewTreeObserver.OnScrollChangedListener onScrollChangedListener;
    private BitmapDrawable[] shadow = new BitmapDrawable[2];
    private boolean[] shadowVisibility = new boolean[2];
    private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
    private int customViewOffset = 12;
    private boolean withCancelDialog;

    private int dialogButtonColorKey = Theme.key_dialogButton;

    private OnCancelListener onCancelListener;

    private AlertDialog cancelDialog;

    private int lastScreenWidth;

    private OnClickListener onClickListener;
    private OnDismissListener onDismissListener;
    private Utilities.Callback<Runnable> overridenDissmissListener;

    private CharSequence[] items;
    private int[] itemIcons;
    private CharSequence title;
    private CharSequence secondTitle;
    private CharSequence subtitle;
    private CharSequence message;
    private int topResId;
    private View topView;
    private boolean topAnimationIsNew;
    private int topAnimationId;
    private int topAnimationSize;
    private Map<String, Integer> topAnimationLayerColors;
    private int topHeight = 132;
    private Drawable topDrawable;
    private int topBackgroundColor;
    private int progressViewStyle;
    private int currentProgress;

    private boolean messageTextViewClickable = true;

    private boolean canCacnel = true;

    private boolean dismissDialogByButtons = true;
    private boolean drawBackground;
    private boolean notDrawBackgroundOnTopView;
    private RLottieImageView topImageView;
    private CharSequence positiveButtonText;
    private OnButtonClickListener positiveButtonListener;
    private CharSequence negativeButtonText;
    private OnButtonClickListener negativeButtonListener;
    private CharSequence neutralButtonText;
    private OnButtonClickListener neutralButtonListener;
    protected ViewGroup buttonsLayout;
    private LineProgressView lineProgressView;
    private TextView lineProgressViewPercent;
    private OnButtonClickListener onBackButtonListener;
    private int[] containerViewLocation = new int[2];

    public interface OnButtonClickListener {
        void onClick(AlertDialog dialog, int which);
    }

    private boolean checkFocusable = true;

    private Drawable shadowDrawable;
    private Rect backgroundPaddings;

    private float blurOpacity;
    private Bitmap blurBitmap;
    private Matrix blurMatrix;
    private BitmapShader blurShader;
    private Paint blurPaint;
    private Paint dimBlurPaint;

    private boolean focusable;

    private boolean verticalButtons;

    private Runnable dismissRunnable = this::dismiss;
    private Runnable showRunnable = () -> {
        if (isShowing()) {
            return;
        }
        try {
            show();
        } catch (Exception ignore) {

        }
    };

    private ArrayList<AlertDialogCell> itemViews = new ArrayList<>();
    private float aspectRatio;
    private boolean dimEnabled = true;
    private float dimAlpha = 0.5f;
    private boolean dimCustom = false;
    private final Theme.ResourcesProvider resourcesProvider;
    private boolean topAnimationAutoRepeat = true;
    private boolean blurredBackground;
    private boolean blurredNativeBackground;
    private int backgroundColor;
    float blurAlpha = 0.8f;
    private boolean blurBehind;
    private int additioanalHorizontalPadding;

    public void setBlurParams(float blurAlpha, boolean blurBehind, boolean blurBackground) {
        this.blurAlpha = blurAlpha;
        this.blurBehind = blurBehind;
        this.blurredBackground = blurBackground;
    }

    protected boolean supportsNativeBlur() {
        return false; // Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LaunchActivity.systemBlurEnabled;
    }

    public void redPositive() {
        TextView button = (TextView) getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(getThemedColor(Theme.key_text_RedBold));
        }
    }

    public static class AlertDialogCell extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private TextView textView;
        private ImageView imageView;

        public AlertDialogCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 2));
            setPadding(dp(23), 0, dp(23), 0);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
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
                textView.setPadding(LocaleController.isRTL ? 0 : dp(56), 0, LocaleController.isRTL ? dp(56) : 0, 0);
            } else {
                imageView.setVisibility(INVISIBLE);
                textView.setPadding(0, 0, 0, 0);
            }
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }
    }

    public AlertDialog(Context context, int progressStyle) {
        this(context, progressStyle, null);
    }
    
    public AlertDialog(Context context, int progressStyle, Theme.ResourcesProvider resourcesProvider) {
        super(context, R.style.TransparentDialog);
        this.resourcesProvider = resourcesProvider;

        progressViewStyle = progressStyle;
        backgroundColor = getThemedColor(Theme.key_dialogBackground);
        final boolean isDark = AndroidUtilities.computePerceivedBrightness(backgroundColor) < 0.721f;
        blurredNativeBackground = supportsNativeBlur() && progressViewStyle == ALERT_TYPE_MESSAGE;
        blurredBackground = (blurredNativeBackground || !supportsNativeBlur() && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) && isDark;

        backgroundPaddings = new Rect();
        if (progressStyle != ALERT_TYPE_SPINNER || blurredBackground) {
            shadowDrawable = context.getResources().getDrawable(R.drawable.popup_fixed_alert3).mutate();
            blurOpacity = progressStyle == ALERT_TYPE_SPINNER ? 0.55f : (isDark ? 0.80f : 0.985f);
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
            shadowDrawable.getPadding(backgroundPaddings);
        }
        withCancelDialog = progressViewStyle == ALERT_TYPE_SPINNER;
    }

    private long shownAt;

    @Override
    public void show() {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        dismissed = false;
        super.show();
        if (progressViewContainer != null && progressViewStyle == ALERT_TYPE_SPINNER) {
            progressViewContainer.setScaleX(0);
            progressViewContainer.setScaleY(0);
            progressViewContainer.animate()
                .scaleX(1f).scaleY(1f)
                .setInterpolator(new OvershootInterpolator(1.3f))
                .setDuration(190)
                .start();
        }
        shownAt = System.currentTimeMillis();
    }

    public void setCancelDialog(boolean enable) {
        withCancelDialog = enable;
    }

    public class AlertDialogView extends LinearLayout {
        public AlertDialogView(Context context) {
            super(context);
        }

        private boolean inLayout;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (withCancelDialog) {
                showCancelAlert();
                return false;
            }
            return super.onTouchEvent(event) || true;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (withCancelDialog) {
                showCancelAlert();
                return false;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (progressViewStyle == ALERT_TYPE_SPINNER) {
                progressViewContainer.measure(MeasureSpec.makeMeasureSpec(dp(86), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(86), MeasureSpec.EXACTLY));
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            } else {
                inLayout = true;
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                if (customWidth > 0) {
                    width = customWidth + backgroundPaddings.left + backgroundPaddings.right;
                }

                int maxContentHeight;
                int availableHeight = maxContentHeight = height - getPaddingTop() - getPaddingBottom();
                int availableWidth = width - getPaddingLeft() - getPaddingRight();

                int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(availableWidth - dp(48), MeasureSpec.EXACTLY);
                int childFullWidthMeasureSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY);
                LayoutParams layoutParams;

                if (buttonsLayout != null) {
                    int count = buttonsLayout.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = buttonsLayout.getChildAt(a);
                        if (child instanceof TextView) {
                            TextView button = (TextView) child;
                            button.setMaxWidth(dp((availableWidth - dp(24)) / 2));
                        }
                    }
                    buttonsLayout.measure(childFullWidthMeasureSpec, heightMeasureSpec);
                    layoutParams = (LayoutParams) buttonsLayout.getLayoutParams();
                    availableHeight -= buttonsLayout.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                }
                if (secondTitleTextView != null) {
                    secondTitleTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(childWidthMeasureSpec), MeasureSpec.AT_MOST), heightMeasureSpec);
                }
                if (titleTextView != null) {
                    if (secondTitleTextView != null) {
                        titleTextView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(childWidthMeasureSpec) - secondTitleTextView.getMeasuredWidth() - dp(8), MeasureSpec.EXACTLY), heightMeasureSpec);
                    } else {
                        titleTextView.measure(childWidthMeasureSpec, heightMeasureSpec);
                    }
                }
                if (titleContainer != null) {
                    titleContainer.measure(childWidthMeasureSpec, heightMeasureSpec);
                    layoutParams = (LayoutParams) titleContainer.getLayoutParams();
                    availableHeight -= titleContainer.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                }
                if (subtitleTextView != null) {
                    subtitleTextView.measure(childWidthMeasureSpec, heightMeasureSpec);
                    layoutParams = (LayoutParams) subtitleTextView.getLayoutParams();
                    availableHeight -= subtitleTextView.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                }
                if (topImageView != null) {
                    topImageView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(topHeight), MeasureSpec.EXACTLY));
                    availableHeight -= topImageView.getMeasuredHeight();
                }
                if (topView != null) {
                    int w = width;
                    int h;
                    if (aspectRatio == 0) {
                        float scale = w / 936.0f;
                        h = (int) (354 * scale);
                    } else {
                        h = (int) (w * aspectRatio);
                    }
                    topView.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                    topView.getLayoutParams().height = h;
                    availableHeight -= topView.getMeasuredHeight();
                }
                if (progressViewStyle == ALERT_TYPE_MESSAGE) {
                    layoutParams = (LayoutParams) contentScrollView.getLayoutParams();

                    if (customView != null) {
                        layoutParams.topMargin = titleTextView == null && messageTextView.getVisibility() == GONE && items == null ? dp(16) : 0;
                        layoutParams.bottomMargin = buttonsLayout == null ? dp(8) : 0;
                    } else if (items != null) {
                        layoutParams.topMargin = titleTextView == null && messageTextView.getVisibility() == GONE ? dp(8) : 0;
                        layoutParams.bottomMargin = dp(8);
                    } else if (messageTextView.getVisibility() == VISIBLE) {
                        layoutParams.topMargin = titleTextView == null ? dp(19) : 0;
                        layoutParams.bottomMargin = dp(20);
                    }

                    availableHeight -= layoutParams.bottomMargin + layoutParams.topMargin;
                    contentScrollView.measure(childFullWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                    availableHeight -= contentScrollView.getMeasuredHeight();
                } else {
                    if (progressViewContainer != null) {
                        progressViewContainer.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                        layoutParams = (LayoutParams) progressViewContainer.getLayoutParams();
                        availableHeight -= progressViewContainer.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                    } else if (messageTextView != null) {
                        messageTextView.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                        if (messageTextView.getVisibility() != GONE) {
                            layoutParams = (LayoutParams) messageTextView.getLayoutParams();
                            availableHeight -= messageTextView.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                        }
                    }
                    if (lineProgressView != null) {
                        lineProgressView.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(4), MeasureSpec.EXACTLY));
                        layoutParams = (LayoutParams) lineProgressView.getLayoutParams();
                        availableHeight -= lineProgressView.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;

                        lineProgressViewPercent.measure(childWidthMeasureSpec, MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                        layoutParams = (LayoutParams) lineProgressViewPercent.getLayoutParams();
                        availableHeight -= lineProgressViewPercent.getMeasuredHeight() + layoutParams.bottomMargin + layoutParams.topMargin;
                    }
                }

                setMeasuredDimension(width, maxContentHeight - availableHeight + getPaddingTop() + getPaddingBottom() - (topAnimationIsNew ? dp(8) : 0));
                inLayout = false;

                if (lastScreenWidth != AndroidUtilities.displaySize.x) {
                    AndroidUtilities.runOnUIThread(() -> {
                        lastScreenWidth = AndroidUtilities.displaySize.x;
                        final int calculatedWidth = AndroidUtilities.displaySize.x - dp(56);
                        int maxWidth;
                        if (AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isSmallTablet()) {
                                maxWidth = dp(446);
                            } else {
                                maxWidth = dp(496);
                            }
                        } else {
                            maxWidth = dp(356);
                        }

                        Window window = getWindow();
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                        params.copyFrom(window.getAttributes());
                        params.width = Math.min(maxWidth, calculatedWidth) + backgroundPaddings.left + backgroundPaddings.right;
                        try {
                            window.setAttributes(params);
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    });
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (progressViewStyle == ALERT_TYPE_SPINNER) {
                int x = (r - l - progressViewContainer.getMeasuredWidth()) / 2;
                int y = (b - t - progressViewContainer.getMeasuredHeight()) / 2;
                progressViewContainer.layout(x, y, x + progressViewContainer.getMeasuredWidth(), y + progressViewContainer.getMeasuredHeight());
            } else if (contentScrollView != null) {
                if (onScrollChangedListener == null) {
                    onScrollChangedListener = () -> {
                        runShadowAnimation(0, titleTextView != null && contentScrollView.getScrollY() > scrollContainer.getTop());
                        runShadowAnimation(1, buttonsLayout != null && contentScrollView.getScrollY() + contentScrollView.getHeight() < scrollContainer.getBottom());
                        contentScrollView.invalidate();
                    };
                    contentScrollView.getViewTreeObserver().addOnScrollChangedListener(onScrollChangedListener);
                }
                onScrollChangedListener.onScrollChanged();
            }

            getLocationOnScreen(containerViewLocation);
            if (blurMatrix != null && blurShader != null) {
                blurMatrix.reset();
                blurMatrix.postScale(8f, 8f);
                blurMatrix.postTranslate(-containerViewLocation[0], -containerViewLocation[1]);
                blurShader.setLocalMatrix(blurMatrix);
            }
        }

        @Override
        public void requestLayout() {
            if (inLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }

        private AnimatedFloat blurPaintAlpha = new AnimatedFloat(0, this);
        private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas canvas) {
            if (blurredBackground && !blurredNativeBackground) {
                float r;
                if (progressViewStyle == ALERT_TYPE_SPINNER && progressViewContainer != null) {
                    r = dp(18);
                    float w = progressViewContainer.getWidth() * progressViewContainer.getScaleX();
                    float h = progressViewContainer.getHeight() * progressViewContainer.getScaleY();
                    AndroidUtilities.rectTmp.set(
                            (getWidth() - w) / 2f,
                            (getHeight() - h) / 2f,
                            (getWidth() + w) / 2f,
                            (getHeight() + h) / 2f
                    );
                } else {
                    r = dp(10);
                    AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getMeasuredHeight() - getPaddingBottom());
                }

                // draw blur of background
                float blurAlpha = blurPaintAlpha.set(blurPaint != null ? 1f : 0f);
                if (blurPaint != null) {
                    blurPaint.setAlpha((int) (0xFF * blurAlpha));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, blurPaint);
                }

                // draw dim above blur
                if (dimBlurPaint == null) {
                    dimBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    dimBlurPaint.setColor(ColorUtils.setAlphaComponent(0xff000000, (int) (0xFF * dimAlpha)));
                }
                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, dimBlurPaint);

                // draw background
                backgroundPaint.setColor(backgroundColor);
                backgroundPaint.setAlpha((int) (backgroundPaint.getAlpha() * (blurAlpha * (blurOpacity - 1f) + 1f)));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
            }
            super.draw(canvas);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (drawBackground && !blurredBackground) {
                shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                if (topView != null && notDrawBackgroundOnTopView) {
                    int clipTop = topView.getBottom();
                    canvas.save();
                    canvas.clipRect(0, clipTop, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);
                    canvas.restore();
                } else {
                    shadowDrawable.draw(canvas);
                }
            }
            super.dispatchDraw(canvas);
        }
    }

    private boolean needStarsBalance;
    public AlertDialog setShowStarsBalance(boolean show) {
        needStarsBalance = show;
        return this;
    }

    private FrameLayout fullscreenContainerView;
    private StarsReactionsSheet.BalanceCloud starsBalanceCloud;

    private AlertDialogView containerView;
    public AlertDialogView getContainerView() {
        return containerView;
    }

    protected View inflateContent(boolean setContent) {
        containerView = new AlertDialogView(getContext());
        containerView.setOrientation(LinearLayout.VERTICAL);
        if ((blurredBackground || progressViewStyle == ALERT_TYPE_SPINNER) && progressViewStyle != ALERT_TYPE_LOADING) {
            containerView.setBackgroundDrawable(null);
            containerView.setPadding(0, 0, 0, 0);
            if (blurredBackground && !blurredNativeBackground) {
                containerView.setWillNotDraw(false);
            }
            drawBackground = false;
        } else {
            if (notDrawBackgroundOnTopView) {
                Rect rect = new Rect();
                shadowDrawable.getPadding(rect);
                containerView.setPadding(rect.left, rect.top, rect.right, rect.bottom);
                drawBackground = true;
            } else {
                containerView.setBackgroundDrawable(null);
                containerView.setPadding(0, 0, 0, 0);
                containerView.setBackgroundDrawable(shadowDrawable);
                drawBackground = false;
            }
        }
        containerView.setFitsSystemWindows(Build.VERSION.SDK_INT >= 21);
        View rootView = containerView;
        if (needStarsBalance) {
            if (fullscreenContainerView == null) {
                fullscreenContainerView = new FrameLayout(getContext());
                fullscreenContainerView.setOnClickListener(v -> {
                    dismiss();
                });
//                fullscreenContainerView.setFitsSystemWindows(Build.VERSION.SDK_INT >= 21);
            }
            if (starsBalanceCloud == null) {
                starsBalanceCloud = new StarsReactionsSheet.BalanceCloud(getContext(), UserConfig.selectedAccount, resourcesProvider);
                ScaleStateListAnimator.apply(starsBalanceCloud);
                starsBalanceCloud.setOnClickListener(v -> {
                    new StarsIntroActivity.StarsOptionsSheet(getContext(), resourcesProvider).show();
                });
            }
            AndroidUtilities.removeFromParent(containerView);
            AndroidUtilities.removeFromParent(starsBalanceCloud);
            fullscreenContainerView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            fullscreenContainerView.addView(starsBalanceCloud, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));
            rootView = fullscreenContainerView;
        }
        if (setContent) {
            if (needStarsBalance) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                lp.gravity = Gravity.FILL;
                setContentView(rootView, lp);
            } else if (customWidth > 0) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.CENTER;
                setContentView(rootView, lp);
            } else {
                setContentView(rootView);
            }
        }

        final boolean hasButtons = positiveButtonText != null || negativeButtonText != null || neutralButtonText != null;

        if (topResId != 0 || topAnimationId != 0 || topDrawable != null) {
            topImageView = new RLottieImageView(getContext());
            if (topDrawable != null) {
                topImageView.setImageDrawable(topDrawable);
                if (topDrawable instanceof AttachableDrawable) {
                    final AttachableDrawable d = (AttachableDrawable) topDrawable;
                    topImageView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(@NonNull View v) {
                            d.onAttachedToWindow(null);
                        }
                        @Override
                        public void onViewDetachedFromWindow(@NonNull View v) {
                            d.onDetachedFromWindow(null);
                        }
                    });
                    d.setParent(topImageView);
                }
            } else if (topResId != 0) {
                topImageView.setImageResource(topResId);
            } else {
                topImageView.setAutoRepeat(topAnimationAutoRepeat);
                topImageView.setAnimation(topAnimationId, topAnimationSize, topAnimationSize);
                if (topAnimationLayerColors != null) {
                    RLottieDrawable drawable = topImageView.getAnimatedDrawable();
                    for (Map.Entry<String, Integer> en : topAnimationLayerColors.entrySet()) {
                        drawable.setLayerColor(en.getKey(), en.getValue());
                    }
                }
                topImageView.playAnimation();
            }
            topImageView.setScaleType(ImageView.ScaleType.CENTER);
            if (topAnimationIsNew) {
                GradientDrawable d = new GradientDrawable();
                d.setColor(topBackgroundColor);
                d.setCornerRadius(dp(128));
                topImageView.setBackground(new Drawable() {
                    int size = topAnimationSize + dp(52);

                    @Override
                    public void draw(@NonNull Canvas canvas) {
                        d.setBounds((int) ((topImageView.getWidth() - size) / 2f), (int) ((topImageView.getHeight() - size) / 2f), (int) ((topImageView.getWidth() + size) / 2f), (int) ((topImageView.getHeight() + size) / 2f));
                        d.draw(canvas);
                    }

                    @Override
                    public void setAlpha(int alpha) {
                        d.setAlpha(alpha);
                    }

                    @Override
                    public void setColorFilter(@Nullable ColorFilter colorFilter) {
                        d.setColorFilter(colorFilter);
                    }

                    @Override
                    public int getOpacity() {
                        return d.getOpacity();
                    }
                });
                topHeight = 92;
            } else {
                topImageView.setBackground(Theme.createRoundRectDrawable(dp(10), 0, topBackgroundColor));
            }
            if (topAnimationIsNew) {
                topImageView.setTranslationY(dp(16));
            } else {
                topImageView.setTranslationY(0);
            }
            topImageView.setPadding(0, 0, 0, 0);
            containerView.addView(topImageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, topHeight, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        } else if (topView != null) {
            topView.setPadding(0, 0, 0, 0);
            containerView.addView(topView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, topHeight, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        }

        if (title != null) {
            titleContainer = new FrameLayout(getContext());
            containerView.addView(titleContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : 0, 24, 0, 24, 0));

            titleTextView = new SpoilersTextView(getContext(), false);
            NotificationCenter.listenEmojiLoading(titleTextView);
            titleTextView.cacheType = AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
            titleTextView.setText(title);
            titleTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setGravity((topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            titleContainer.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 19, 0, topAnimationIsNew ? 4 : (subtitle != null ? 2 : (items != null ? 14 : 10))));
        }

        if (secondTitle != null && title != null) {
            secondTitleTextView = new TextView(getContext());
            secondTitleTextView.setText(secondTitle);
            secondTitleTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            secondTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            secondTitleTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
            titleContainer.addView(secondTitleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 0, 21, 0, 0));
        }

        if (subtitle != null) {
            subtitleTextView = new TextView(getContext());
            subtitleTextView.setText(subtitle);
            subtitleTextView.setTextColor(getThemedColor(Theme.key_dialogIcon));
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            containerView.addView(subtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 0, 24, items != null ? 14 : 10));
        }

        if (progressViewStyle == ALERT_TYPE_MESSAGE) {
            shadow[0] = (BitmapDrawable) getContext().getResources().getDrawable(R.drawable.header_shadow).mutate();
            shadow[1] = (BitmapDrawable) getContext().getResources().getDrawable(R.drawable.header_shadow_reverse).mutate();
            shadow[0].setAlpha(0);
            shadow[1].setAlpha(0);
            shadow[0].setCallback(this);
            shadow[1].setCallback(this);

            contentScrollView = new ScrollView(getContext()) {
                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    if (shadow[0].getPaint().getAlpha() != 0) {
                        shadow[0].setBounds(0, getScrollY(), getMeasuredWidth(), getScrollY() + dp(3));
                        shadow[0].draw(canvas);
                    }
                    if (shadow[1].getPaint().getAlpha() != 0) {
                        shadow[1].setBounds(0, getScrollY() + getMeasuredHeight() - dp(3), getMeasuredWidth(), getScrollY() + getMeasuredHeight());
                        shadow[1].draw(canvas);
                    }
                    return result;
                }
            };
            contentScrollView.setVerticalScrollBarEnabled(false);
            AndroidUtilities.setScrollViewEdgeEffectColor(contentScrollView, getThemedColor(Theme.key_dialogScrollGlow));
            containerView.addView(contentScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            scrollContainer = new LinearLayout(getContext());
            scrollContainer.setOrientation(LinearLayout.VERTICAL);
            contentScrollView.addView(scrollContainer, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        messageTextView = new EffectsTextView(getContext());
        NotificationCenter.listenEmojiLoading(messageTextView);
        messageTextView.setTextColor(getThemedColor(topAnimationIsNew ? Theme.key_windowBackgroundWhiteGrayText : Theme.key_dialogTextBlack));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        messageTextView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        if (!messageTextViewClickable) {
            messageTextView.setClickable(false);
            messageTextView.setEnabled(false);
        }
        messageTextView.setGravity((topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        if (progressViewStyle == ALERT_TYPE_LOADING) {
            containerView.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, title == null ? 19 : 0, 24, 20));

            lineProgressView = new LineProgressView(getContext());
            lineProgressView.setProgress(currentProgress / 100.0f, false);
            lineProgressView.setProgressColor(getThemedColor(Theme.key_dialogLineProgress));
            lineProgressView.setBackColor(getThemedColor(Theme.key_dialogLineProgressBackground));
            containerView.addView(lineProgressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 4, Gravity.LEFT | Gravity.CENTER_VERTICAL, 24, 0, 24, 0));

            lineProgressViewPercent = new TextView(getContext());
            lineProgressViewPercent.setTypeface(AndroidUtilities.bold());
            lineProgressViewPercent.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            lineProgressViewPercent.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            lineProgressViewPercent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            containerView.addView(lineProgressViewPercent, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 23, 4, 23, 24));
            updateLineProgressTextView();
        } else if (progressViewStyle == ALERT_TYPE_SPINNER) {
            setCanceledOnTouchOutside(false);
            setCancelable(false);

            progressViewContainer = new FrameLayout(getContext());
            backgroundColor = getThemedColor(Theme.key_dialog_inlineProgressBackground);
            if (!(blurredBackground && !blurredNativeBackground)) {
                progressViewContainer.setBackgroundDrawable(Theme.createRoundRectDrawable(dp(18), backgroundColor));
            }
            containerView.addView(progressViewContainer, LayoutHelper.createLinear(86, 86, Gravity.CENTER));

            RadialProgressView progressView = new RadialProgressView(getContext(), resourcesProvider);
            progressView.setSize(dp(32));
            progressView.setProgressColor(getThemedColor(Theme.key_dialog_inlineProgress));
            progressViewContainer.addView(progressView, LayoutHelper.createFrame(86, 86, Gravity.CENTER));
        } else {
            if (aboveMessageView != null) {
                scrollContainer.addView(aboveMessageView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 4, 22, 12));
            }
            scrollContainer.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (topAnimationIsNew ? Gravity.CENTER_HORIZONTAL : LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 0, 24, customView != null || items != null ? customViewOffset : 0));
            if (bottomView != null) {
                scrollContainer.addView(bottomView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 12, 22, 0));
            }
        }
        if (!TextUtils.isEmpty(message)) {
            messageTextView.setText(message);
            messageTextView.setVisibility(View.VISIBLE);
        } else {
            messageTextView.setVisibility(View.GONE);
        }

        if (items != null) {
            for (int a = 0; a < items.length; a++) {
                if (items[a] == null) {
                    continue;
                }
                AlertDialogCell cell = new AlertDialogCell(getContext(), resourcesProvider);
                cell.setTextAndIcon(items[a], itemIcons != null ? itemIcons[a] : 0);
                cell.setTag(a);
                itemViews.add(cell);
                scrollContainer.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                cell.setOnClickListener(v -> {
                    if (onClickListener != null) {
                        onClickListener.onClick(AlertDialog.this, (Integer) v.getTag());
                    }
                    dismiss();
                });
            }
        }
        if (customView != null) {
            if (customView.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) customView.getParent();
                viewGroup.removeView(customView);
            }
            scrollContainer.addView(customView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, customViewHeight));
        }
        if (hasButtons) {
            if (!verticalButtons) {
                int buttonsWidth = 0;
                TextPaint paint = new TextPaint();
                paint.setTextSize(dp(16));
                if (positiveButtonText != null) {
                    if (buttonsWidth > 0) buttonsWidth += dp(8);
                    buttonsWidth += paint.measureText(positiveButtonText, 0, positiveButtonText.length()) + dp(12 + 12);
                }
                if (negativeButtonText != null) {
                    if (buttonsWidth > 0) buttonsWidth += dp(8);
                    buttonsWidth += paint.measureText(negativeButtonText, 0, negativeButtonText.length()) + dp(12 + 12);
                }
                if (neutralButtonText != null) {
                    if (buttonsWidth > 0) buttonsWidth += dp(8);
                    buttonsWidth += paint.measureText(neutralButtonText, 0, neutralButtonText.length()) + dp(12 + 12);
                }
                if (buttonsWidth > AndroidUtilities.displaySize.x - dp(110)) {
                    verticalButtons = true;
                }
            }
            if (verticalButtons) {
                LinearLayout linearLayout = new LinearLayout(getContext());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                buttonsLayout = linearLayout;
            } else {
                buttonsLayout = new FrameLayout(getContext()) {
                    @Override
                    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                        int count = getChildCount();
                        View positiveButton = null;
                        int width = right - left;
                        for (int a = 0; a < count; a++) {
                            View child = getChildAt(a);
                            Integer tag = (Integer) child.getTag();
                            if (tag != null) {
                                if (tag == Dialog.BUTTON_POSITIVE) {
                                    positiveButton = child;
                                    if (LocaleController.isRTL) {
                                        child.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    } else {
                                        child.layout(width - getPaddingRight() - child.getMeasuredWidth(), getPaddingTop(), width - getPaddingRight(), getPaddingTop() + child.getMeasuredHeight());
                                    }
                                } else if (tag == Dialog.BUTTON_NEGATIVE) {
                                    if (LocaleController.isRTL) {
                                        int x = getPaddingLeft();
                                        if (positiveButton != null) {
                                            x += positiveButton.getMeasuredWidth() + dp(8);
                                        }
                                        child.layout(x, getPaddingTop(), x + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    } else {
                                        int x = width - getPaddingRight() - child.getMeasuredWidth();
                                        if (positiveButton != null) {
                                            x -= positiveButton.getMeasuredWidth() + dp(8);
                                        }
                                        child.layout(x, getPaddingTop(), x + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    }
                                } else if (tag == Dialog.BUTTON_NEUTRAL) {
                                    if (LocaleController.isRTL) {
                                        child.layout(width - getPaddingRight() - child.getMeasuredWidth(), getPaddingTop(), width - getPaddingRight(), getPaddingTop() + child.getMeasuredHeight());
                                    } else {
                                        child.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + child.getMeasuredWidth(), getPaddingTop() + child.getMeasuredHeight());
                                    }
                                }
                            } else {
                                int w = child.getMeasuredWidth();
                                int h = child.getMeasuredHeight();
                                int l;
                                int t;
                                if (positiveButton != null) {
                                    l = positiveButton.getLeft() + (positiveButton.getMeasuredWidth() - w) / 2;
                                    t = positiveButton.getTop() + (positiveButton.getMeasuredHeight() - h) / 2;
                                } else {
                                    l = t = 0;
                                }
                                child.layout(l, t, l + w, t + h);
                            }
                        }
                    }

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                        int totalWidth = 0;
                        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                        int count = getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = getChildAt(a);
                            if (child instanceof TextView && child.getTag() != null) {
                                totalWidth += child.getMeasuredWidth();
                            }
                        }
                        if (totalWidth > availableWidth) {
                            View negative = findViewWithTag(BUTTON_NEGATIVE);
                            View neutral = findViewWithTag(BUTTON_NEUTRAL);
                            if (negative != null && neutral != null) {
                                if (negative.getMeasuredWidth() < neutral.getMeasuredWidth()) {
                                    neutral.measure(MeasureSpec.makeMeasureSpec(neutral.getMeasuredWidth() - (totalWidth - availableWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(neutral.getMeasuredHeight(), MeasureSpec.EXACTLY));
                                } else {
                                    negative.measure(MeasureSpec.makeMeasureSpec(negative.getMeasuredWidth() - (totalWidth - availableWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(negative.getMeasuredHeight(), MeasureSpec.EXACTLY));
                                }
                            }
                        }
                    }
                };
            }
            if (bottomView != null) {
                buttonsLayout.setPadding(dp(16), 0, dp(16), dp(4));
                buttonsLayout.setTranslationY(-dp(6));
            } else {
                buttonsLayout.setPadding(dp(8), dp(8), dp(8), dp(8));
            }
            containerView.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
            if (topAnimationIsNew) {
                buttonsLayout.setTranslationY(-dp(8));
            }

            if (positiveButtonText != null) {
                TextViewWithLoading textView = new TextViewWithLoading(getContext()) {
                    @Override
                    public void setEnabled(boolean enabled) {
                        super.setEnabled(enabled);
                        setAlpha(enabled ? 1.0f : 0.5f);
                    }

                    @Override
                    public void setTextColor(int color) {
                        super.setTextColor(color);
                        setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(dp(6), color));
                    }
                };
                textView.setMinWidth(dp(64));
                textView.setTag(Dialog.BUTTON_POSITIVE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(getThemedColor(dialogButtonColorKey));
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setText(positiveButtonText);
                textView.setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(dp(6), getThemedColor(dialogButtonColorKey)));
                textView.setPadding(dp(12), 0, dp(12), 0);
                if (verticalButtons) {
                    buttonsLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT));
                } else {
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
                }
                textView.setOnClickListener(v -> {
                    if (textView.isLoading()) return;
                    if (positiveButtonListener != null) {
                        positiveButtonListener.onClick(AlertDialog.this, Dialog.BUTTON_POSITIVE);
                    }
                    if (dismissDialogByButtons) {
                        dismiss();
                    }
                });
            }

            if (negativeButtonText != null) {
                TextViewWithLoading textView = new TextViewWithLoading(getContext()) {
                    @Override
                    public void setEnabled(boolean enabled) {
                        super.setEnabled(enabled);
                        setAlpha(enabled ? 1.0f : 0.5f);
                    }

                    @Override
                    public void setTextColor(int color) {
                        super.setTextColor(color);
                        setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(dp(6), color));
                    }
                };
                textView.setMinWidth(dp(64));
                textView.setTag(Dialog.BUTTON_NEGATIVE);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(getThemedColor(dialogButtonColorKey));
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                textView.setText(negativeButtonText.toString());
                textView.setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(dp(6), getThemedColor(dialogButtonColorKey)));
                textView.setPadding(dp(12), 0, dp(12), 0);
                if (verticalButtons) {
                    buttonsLayout.addView(textView, 0, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT));
                } else {
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.RIGHT));
                }
                textView.setOnClickListener(v -> {
                    if (textView.isLoading()) return;
                    if (negativeButtonListener != null) {
                        negativeButtonListener.onClick(AlertDialog.this, Dialog.BUTTON_NEGATIVE);
                    }
                    if (dismissDialogByButtons) {
                        cancel();
                    }
                });
            }

            if (neutralButtonText != null) {
                TextViewWithLoading textView = new TextViewWithLoading(getContext()) {
                    @Override
                    public void setEnabled(boolean enabled) {
                        super.setEnabled(enabled);
                        setAlpha(enabled ? 1.0f : 0.5f);
                    }

                    @Override
                    public void setTextColor(int color) {
                        super.setTextColor(color);
                        setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(dp(6), color));
                    }
                };
                textView.setMinWidth(dp(64));
                textView.setTag(Dialog.BUTTON_NEUTRAL);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                textView.setTextColor(getThemedColor(dialogButtonColorKey));
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setSingleLine(true);
                textView.setText(neutralButtonText.toString());
                textView.setBackground(Theme.getRoundRectSelectorDrawable(dp(6), getThemedColor(dialogButtonColorKey)));
                textView.setPadding(dp(12), 0, dp(12), 0);
                if (verticalButtons) {
                    buttonsLayout.addView(textView, 1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT));
                } else {
                    buttonsLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.LEFT));
                }
                textView.setOnClickListener(v -> {
                    if (textView.isLoading()) return;
                    if (neutralButtonListener != null) {
                        neutralButtonListener.onClick(AlertDialog.this, Dialog.BUTTON_NEGATIVE);
                    }
                    if (dismissDialogByButtons) {
                        dismiss();
                    }
                });
            }

            if (verticalButtons) {
                for (int i = 1; i < buttonsLayout.getChildCount(); i++) {
                    ((ViewGroup.MarginLayoutParams) buttonsLayout.getChildAt(i).getLayoutParams()).topMargin = dp(6);
                }
            }
        }

        Window window = getWindow();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        if (needStarsBalance) {
//            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;

            window.setWindowAnimations(R.style.DialogNoAnimation);
        } else if (progressViewStyle == ALERT_TYPE_SPINNER) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
        } else {
            if (dimEnabled && !dimCustom) {
                params.dimAmount = dimAlpha;
                params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            } else {
                params.dimAmount = 0f;
                params.flags ^= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            }

            lastScreenWidth = AndroidUtilities.displaySize.x;
            final int calculatedWidth = AndroidUtilities.displaySize.x - dp(48) - additioanalHorizontalPadding * 2;
            int maxWidth;
            if (AndroidUtilities.isTablet()) {
                if (AndroidUtilities.isSmallTablet()) {
                    maxWidth = dp(446);
                } else {
                    maxWidth = dp(496);
                }
            } else {
                maxWidth = dp(356);
            }

            params.width = Math.min(maxWidth, calculatedWidth) + backgroundPaddings.left + backgroundPaddings.right;
        }
        if (customView == null || !checkFocusable || !canTextInput(customView)) {
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }

        if (blurredBackground) {
            if (supportsNativeBlur()) {
                if (progressViewStyle == ALERT_TYPE_MESSAGE) {
                    blurredNativeBackground = true;
                    window.setBackgroundBlurRadius(50);
                    float rad = dp(12);
                    ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
                    shapeDrawable.getPaint().setColor(ColorUtils.setAlphaComponent(backgroundColor, (int) (blurAlpha * 255)));
                    window.setBackgroundDrawable(shapeDrawable);
                    if (blurBehind) {
                        params.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                        params.setBlurBehindRadius(20);
                    }
                }
            } else {
                AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
                    if (bitmap == null) {
                        return;
                    }
                    if (blurPaint == null) {
                        blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    }
                    blurBitmap = bitmap;
                    blurShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    blurPaint.setShader(blurShader);
                    blurMatrix = new Matrix();
                    blurMatrix.postScale(8f, 8f);
                    blurMatrix.postTranslate(-containerViewLocation[0], -containerViewLocation[1]);
                    blurShader.setLocalMatrix(blurMatrix);
                    containerView.invalidate();
                }, 8);
            }
        }

        window.setAttributes(params);

        return rootView;
    }

    @NonNull
    public Browser.Progress makeButtonLoading(int type) {
        final View button = getButton(type);
        dismissDialogByButtons = false;
        return new Browser.Progress(() -> {
            if (button instanceof TextViewWithLoading) {
                ((TextViewWithLoading) button).setLoading(true, true);
            }
        }, () -> {
            if (button instanceof TextViewWithLoading) {
                ((TextViewWithLoading) button).setLoading(false, true);
            }
            dismiss();
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inflateContent(true);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (onBackButtonListener != null) {
            onBackButtonListener.onClick(AlertDialog.this, AlertDialog.BUTTON_NEGATIVE);
        }
    }

    public void setFocusable(boolean value) {
        if (focusable == value) {
            return;
        }
        focusable = value;
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        if (focusable) {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            params.flags &=~ WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        window.setAttributes(params);
    }

    public void setBackgroundColor(int color) {
        backgroundColor = color;
        if (shadowDrawable != null) {
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
        }
    }

    public void setTextColor(int color) {
        if (titleTextView != null) {
            titleTextView.setTextColor(color);
        }
        if (messageTextView != null) {
            messageTextView.setTextColor(color);
        }
    }

    public void setTextSize(int titleSizeDp, int messageSizeDp) {
        if (titleTextView != null) {
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, titleSizeDp);
        }
        if (messageTextView != null) {
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, messageSizeDp);
        }
    }

    public void setMessageLineSpacing(float spaceDp) {
        if (messageTextView != null) {
            messageTextView.setLineSpacing(dp(spaceDp), 1.0f);
        }
    }

    public void showCancelAlert() {
        if (!canCacnel || cancelDialog != null) {
            return;
        }
        Builder builder = new Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.StopLoadingTitle));
        builder.setMessage(LocaleController.getString(R.string.StopLoading));
        builder.setPositiveButton(LocaleController.getString(R.string.WaitMore), null);
        builder.setNegativeButton(LocaleController.getString(R.string.Stop), (dialogInterface, i) -> {
            if (onCancelListener != null) {
                onCancelListener.onCancel(AlertDialog.this);
            }
            dismiss();
        });
        builder.setOnDismissListener(dialog -> cancelDialog = null);
        try {
            cancelDialog = builder.show();
        } catch (Exception ignore) {

        }
    }

    private void runShadowAnimation(final int num, final boolean show) {
        if (show && !shadowVisibility[num] || !show && shadowVisibility[num]) {
            shadowVisibility[num] = show;
            if (shadowAnimation[num] != null) {
                shadowAnimation[num].cancel();
            }
            shadowAnimation[num] = new AnimatorSet();
            if (shadow[num] != null) {
                shadowAnimation[num].playTogether(ObjectAnimator.ofInt(shadow[num], "alpha", show ? 255 : 0));
            }
            shadowAnimation[num].setDuration(150);
            shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        shadowAnimation[num] = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        shadowAnimation[num] = null;
                    }
                }
            });
            try {
                shadowAnimation[num].start();
            } catch (Exception e) {
                FileLog.e(e);
            }

        }
    }

    public void setDismissDialogByButtons(boolean value) {
        dismissDialogByButtons = value;
    }

    public void setProgress(int progress) {
        currentProgress = progress;
        if (lineProgressView != null) {
            lineProgressView.setProgress(progress / 100.0f, true);
            updateLineProgressTextView();
        }
    }

    private void updateLineProgressTextView() {
        lineProgressViewPercent.setText(String.format("%d%%", currentProgress));
    }

    public void setCanCancel(boolean value) {
        canCacnel = value;
    }

    private boolean canTextInput(View v) {
        if (v.onCheckIsTextEditor()) {
            return true;
        }
        if (!(v instanceof ViewGroup)) {
            return false;
        }
        ViewGroup vg = (ViewGroup) v;
        int i = vg.getChildCount();
        while (i > 0) {
            i--;
            v = vg.getChildAt(i);
            if (canTextInput(v)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (messageTextView != null) {
                messageTextView.invalidate();
            }
        }
    }

    public void dismissUnless(long minDuration) {
        long currentShowDuration = System.currentTimeMillis() - shownAt;
        if (currentShowDuration < minDuration) {
            AndroidUtilities.runOnUIThread(this::dismiss, currentShowDuration - minDuration);
        } else {
            dismiss();
        }
    }

    private boolean dismissed;
    public boolean isDismissed() {
        return dismissed;
    }

    @Override
    public void dismiss() {
        if (overridenDissmissListener != null) {
            Utilities.Callback<Runnable> listener = overridenDissmissListener;
            overridenDissmissListener = null;
            listener.run(this::dismiss);
            return;
        }
        if (dismissed) return;
        dismissed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (onDismissListener != null) {
            onDismissListener.onDismiss(this);
        }
        if (cancelDialog != null) {
            cancelDialog.dismiss();
        }
        try {
            super.dismiss();
        } catch (Throwable ignore) {

        }
        AndroidUtilities.cancelRunOnUIThread(showRunnable);

        if (blurShader != null && blurBitmap != null) {
            blurBitmap.recycle();
            blurShader = null;
            blurPaint = null;
            blurBitmap = null;
        }
    }

    @Override
    public void setCanceledOnTouchOutside(boolean cancel) {
        super.setCanceledOnTouchOutside(cancel);
    }

    public void setTopImage(int resId, int backgroundColor) {
        topResId = resId;
        topBackgroundColor = backgroundColor;
    }

    public void setTopAnimation(int resId, int backgroundColor) {
        setTopAnimation(resId, 94, backgroundColor);
    }
    public void setTopAnimation(int resId, int size, int backgroundColor) {
        topAnimationId = resId;
        topAnimationSize = size;
        topBackgroundColor = backgroundColor;
    }

    public void setTopHeight(int value) {
        topHeight = value;
    }

    public void setTopImage(Drawable drawable, int backgroundColor) {
        topDrawable = drawable;
        topBackgroundColor = backgroundColor;
    }

    public void setTitle(CharSequence text) {
        title = text;
        if (titleTextView != null) {
            titleTextView.setText(text);
        }
    }

    public void setSecondTitle(CharSequence text) {
        secondTitle = text;
    }

    public void setPositiveButton(CharSequence text, final OnButtonClickListener listener) {
        positiveButtonText = text;
        positiveButtonListener = listener;
    }

    public void setNegativeButton(CharSequence text, final OnButtonClickListener listener) {
        negativeButtonText = text;
        negativeButtonListener = listener;
    }

    public void setNeutralButton(CharSequence text, final OnButtonClickListener listener) {
        neutralButtonText = text;
        neutralButtonListener = listener;
    }

    public void setItemColor(int item, int color, int icon) {
        if (item < 0 || item >= itemViews.size()) {
            return;
        }
        AlertDialogCell cell = itemViews.get(item);
        cell.textView.setTextColor(color);
        cell.imageView.setColorFilter(new PorterDuffColorFilter(icon, PorterDuff.Mode.MULTIPLY));
    }

    public int getItemsCount() {
        return itemViews.size();
    }

    public void setMessage(CharSequence text) {
        message = text;
        if (messageTextView != null) {
            if (!TextUtils.isEmpty(message)) {
                messageTextView.setText(message);
                messageTextView.setVisibility(View.VISIBLE);
            } else {
                messageTextView.setVisibility(View.GONE);
            }
        }
    }

    public void setMessageTextViewClickable(boolean value) {
        messageTextViewClickable = value;
    }

    public void setButton(int type, CharSequence text, final OnButtonClickListener listener) {
        switch (type) {
            case BUTTON_NEUTRAL:
                neutralButtonText = text;
                neutralButtonListener = listener;
                break;
            case BUTTON_NEGATIVE:
                negativeButtonText = text;
                negativeButtonListener = listener;
                break;
            case BUTTON_POSITIVE:
                positiveButtonText = text;
                positiveButtonListener = listener;
                break;
        }
    }

    public View getButton(int type) {
        if (buttonsLayout != null) {
            return buttonsLayout.findViewWithTag(type);
        }
        return null;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        contentScrollView.invalidate();
        scrollContainer.invalidate();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        if (contentScrollView != null) {
            contentScrollView.postDelayed(what, when);
        }
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        if (contentScrollView != null) {
            contentScrollView.removeCallbacks(what);
        }
    }

    @Override
    public void setOnCancelListener(OnCancelListener listener) {
        onCancelListener = listener;
        super.setOnCancelListener(listener);
    }

    public void setPositiveButtonListener(final OnButtonClickListener listener) {
        positiveButtonListener = listener;
    }

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void showDelayed(long delay) {
        AndroidUtilities.cancelRunOnUIThread(showRunnable);
        AndroidUtilities.runOnUIThread(showRunnable, delay);
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return null;
    }

    public ViewGroup getButtonsLayout() {
        return buttonsLayout;
    }

    public static class Builder {

        private AlertDialog alertDialog;

        protected Builder(AlertDialog alert) {
            alertDialog = alert;
        }

        public Builder(Context context) {
            this(context, null);
        }

        public Builder(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, 0, resourcesProvider);
        }

        public Builder(Context context, int progressViewStyle, Theme.ResourcesProvider resourcesProvider) {
            if (context == null) {
                context = AndroidUtilities.findActivity(LaunchActivity.instance);
                if (context == null) context = ApplicationLoader.applicationContext;
            }
            alertDialog = createAlertDialog(context, progressViewStyle, resourcesProvider);
        }

        protected AlertDialog createAlertDialog(Context context, int progressViewStyle, Theme.ResourcesProvider resourcesProvider) {
            return new AlertDialog(context, progressViewStyle, resourcesProvider);
        }

        public Context getContext() {
            return alertDialog.getContext();
        }

        public Builder forceVerticalButtons() {
            alertDialog.verticalButtons = true;
            return this;
        }

        public Builder setItems(CharSequence[] items, final OnClickListener onClickListener) {
            alertDialog.items = items;
            alertDialog.onClickListener = onClickListener;
            return this;
        }

        public Builder setCheckFocusable(boolean value) {
            alertDialog.checkFocusable = value;
            return this;
        }

        public Builder setItems(CharSequence[] items, int[] icons, final OnClickListener onClickListener) {
            alertDialog.items = items;
            alertDialog.itemIcons = icons;
            alertDialog.onClickListener = onClickListener;
            return this;
        }

        public Builder setView(View view) {
            return setView(view, LayoutHelper.WRAP_CONTENT);
        }

        public Builder setView(View view, int height) {
            alertDialog.customView = view;
            alertDialog.customViewHeight = height;
            return this;
        }

        public Builder setWidth(int width) {
            alertDialog.customWidth = width;
            return this;
        }

        public Builder aboveMessageView(View view) {
            alertDialog.aboveMessageView = view;
            return this;
        }

        public Builder addBottomView(View view) {
            alertDialog.bottomView = view;
            return this;
        }

        public Builder setTitle(CharSequence title) {
            alertDialog.title = title;
            return this;
        }

        public Builder setSubtitle(CharSequence subtitle) {
            alertDialog.subtitle = subtitle;
            return this;
        }

        @Keep
        public Builder setTopImage(int resId, int backgroundColor) {
            alertDialog.topResId = resId;
            alertDialog.topBackgroundColor = backgroundColor;
            return this;
        }

        public Builder setTopView(View view) {
            alertDialog.topView = view;
            return this;
        }

        public Builder setDialogButtonColorKey(int key) {
            alertDialog.dialogButtonColorKey = key;
            return this;
        }

        public Builder setTopAnimation(int resId, int size, boolean autoRepeat, int backgroundColor) {
            return setTopAnimation(resId, size, autoRepeat, backgroundColor, null);
        }

        public Builder setTopAnimation(int resId, int size, boolean autoRepeat, int backgroundColor, Map<String, Integer> layerColors) {
            alertDialog.topAnimationId = resId;
            alertDialog.topAnimationSize = size;
            alertDialog.topAnimationAutoRepeat = autoRepeat;
            alertDialog.topBackgroundColor = backgroundColor;
            alertDialog.topAnimationLayerColors = layerColors;
            return this;
        }

        public Builder setTopAnimationIsNew(boolean isNew) {
            alertDialog.topAnimationIsNew = isNew;
            return this;
        }

        public Builder setTopAnimation(int resId, int backgroundColor) {
            return setTopAnimation(resId, 94, true, backgroundColor);
        }

        public Builder setTopImage(Drawable drawable, int backgroundColor) {
            alertDialog.topDrawable = drawable;
            alertDialog.topBackgroundColor = backgroundColor;
            return this;
        }

        public Builder setMessage(CharSequence message) {
            alertDialog.message = message;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, final OnButtonClickListener listener) {
            alertDialog.positiveButtonText = text;
            alertDialog.positiveButtonListener = listener;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, final OnButtonClickListener listener) {
            alertDialog.negativeButtonText = text;
            alertDialog.negativeButtonListener = listener;
            return this;
        }

        public Builder setNeutralButton(CharSequence text, final OnButtonClickListener listener) {
            alertDialog.neutralButtonText = text;
            alertDialog.neutralButtonListener = listener;
            return this;
        }

        public Builder setOnBackButtonListener(final OnButtonClickListener listener) {
            alertDialog.onBackButtonListener = listener;
            return this;
        }

        public Builder setOnCancelListener(OnCancelListener listener) {
            alertDialog.setOnCancelListener(listener);
            return this;
        }

        public Builder setCustomViewOffset(int offset) {
            alertDialog.customViewOffset = offset;
            return this;
        }

        public Builder setMessageTextViewClickable(boolean value) {
            alertDialog.messageTextViewClickable = value;
            return this;
        }

        public AlertDialog create() {
            return alertDialog;
        }

        private final boolean[] red = new boolean[3];
        public Builder makeRed(int button) {
            int index = (-button) - 1;
            if (index >= 0 && index < red.length) {
                red[index] = true;
            }
            return this;
        }

        public AlertDialog show() {
            alertDialog.show();
            for (int i = 0; i < red.length; i++) {
                if (!red[i]) continue;
                TextView button = (TextView) alertDialog.getButton(-(i + 1));
                if (button != null) {
                    button.setTextColor(alertDialog.getThemedColor(Theme.key_text_RedBold));
                }
            }
            return alertDialog;
        }

        public Runnable getDismissRunnable() {
            return alertDialog.dismissRunnable;
        }

        public Builder setOnDismissListener(OnDismissListener onDismissListener) {
            alertDialog.setOnDismissListener(onDismissListener);
            return this;
        }

        public void setTopViewAspectRatio(float aspectRatio) {
            alertDialog.aspectRatio = aspectRatio;
        }

        public Builder setDimEnabled(boolean dimEnabled) {
            alertDialog.dimEnabled = dimEnabled;
            return this;
        }

        public Builder setDimAlpha(float dimAlpha) {
            alertDialog.dimAlpha = dimAlpha;
            return this;
        }

        public void notDrawBackgroundOnTopView(boolean b) {
            alertDialog.notDrawBackgroundOnTopView = b;
            alertDialog.blurredBackground = false;
        }

        public void setButtonsVertical(boolean vertical) {
            alertDialog.verticalButtons = vertical;
        }

        public Builder setOnPreDismissListener(OnDismissListener onDismissListener) {
            alertDialog.onDismissListener = onDismissListener;
            return this;
        }

        public Builder overrideDismissListener(Utilities.Callback<Runnable> overridenDissmissListener) {
            alertDialog.overridenDissmissListener = overridenDissmissListener;
            return this;
        }

        public Builder setBlurredBackground(boolean b) {
            alertDialog.blurredBackground = b;
            return this;
        }

        public Builder setAdditionalHorizontalPadding(int padding) {
            alertDialog.additioanalHorizontalPadding = padding;
            return this;
        }

        public Builder makeCustomMaxHeight() {
            alertDialog.customMaxHeight = true;
            return this;
        }
    }
}
