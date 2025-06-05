/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;

public class TextSettingsCell extends FrameLayout {

    private Theme.ResourcesProvider resourcesProvider;
    private TextView textView;
    private AnimatedTextView valueTextView;
    private ImageView imageView;
    private boolean imageViewIsColorful;
    private BackupImageView valueBackupImageView;
    private ImageView valueImageView;
    public boolean needDivider;
    private boolean canDisable;
    private boolean drawLoading;
    private int padding;

    private boolean incrementLoadingProgress;
    private float loadingProgress;
    private float drawLoadingProgress;
    private int loadingSize;
    private boolean measureDelay;
    private int changeProgressStartDelay;

    Paint paint;

    public TextSettingsCell(Context context) {
        this(context, 21);
    }

    public TextSettingsCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 21, resourcesProvider);
    }

    public TextSettingsCell(Context context, int padding) {
        this(context, padding, null);
    }

    public TextSettingsCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.padding = padding;

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        valueTextView = new AnimatedTextView(context, true, true, !LocaleController.isRTL);
        valueTextView.setAnimationProperties(.55f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        valueTextView.setTextSize(AndroidUtilities.dp(16));
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, padding, 0, padding, 0));

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));

        valueImageView = new ImageView(context);
        valueImageView.setScaleType(ImageView.ScaleType.CENTER);
        valueImageView.setVisibility(INVISIBLE);
        valueImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        addView(valueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding, 0, padding, 0));
    }

    public ImageView getValueImageView() {
        return valueImageView;
    }

    private boolean betterLayout = BuildVars.DEBUG_PRIVATE_VERSION;
    public void setBetterLayout(boolean betterLayout) {
        // I might break something with this, gonna need to further test
        this.betterLayout = betterLayout;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        int width = betterLayout ? availableWidth : availableWidth / 2;
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        }

        if (imageView.getVisibility() == VISIBLE) {
            if (imageViewIsColorful) {
                imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(28), MeasureSpec.EXACTLY));
            } else {
                imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
            }
            if (betterLayout) width -= imageView.getMeasuredWidth() + AndroidUtilities.dp(8);
        }

        if (valueBackupImageView != null) {
            valueBackupImageView.measure(MeasureSpec.makeMeasureSpec(valueBackupImageView.getLayoutParams().height, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(valueBackupImageView.getLayoutParams().width, MeasureSpec.EXACTLY));
            if (betterLayout) width -= valueBackupImageView.getMeasuredWidth() + AndroidUtilities.dp(8);
        }
        if (valueTextView.getVisibility() == VISIBLE) {
            valueTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            if (betterLayout) {
                width -= valueTextView.getMeasuredWidth() + AndroidUtilities.dp(8);
            } else {
                width = availableWidth - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8);
            }

            if (valueImageView.getVisibility() == VISIBLE) {
                MarginLayoutParams params = (MarginLayoutParams) valueImageView.getLayoutParams();
                if (LocaleController.isRTL) {
                    params.leftMargin = AndroidUtilities.dp(padding + 4) + valueTextView.getMeasuredWidth();
                } else {
                    params.rightMargin = AndroidUtilities.dp(padding + 4) + valueTextView.getMeasuredWidth();
                }
            }
        } else {
            width = availableWidth;
        }
        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (measureDelay && getParent() != null) {
            changeProgressStartDelay = (int) ((getTop() / (float) ((View) getParent()).getMeasuredHeight()) * 150f);
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public void setCanDisable(boolean value) {
        canDisable = value;
    }

    public AnimatedTextView getValueTextView() {
        return valueTextView;
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setTextValueColor(int color) {
        valueTextView.setTextColor(color);
    }

    public void setText(CharSequence text, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        valueImageView.setVisibility(INVISIBLE);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        setTextAndValue(text, value, false, divider);
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean animated, boolean divider) {
        textView.setText(text);
        valueImageView.setVisibility(INVISIBLE);
        if (value != null) {
            valueTextView.setText(value, animated);
            valueTextView.setVisibility(VISIBLE);
        } else {
            valueTextView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
        requestLayout();
    }

    public void setTextAndIcon(CharSequence text, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        if (resId != 0) {
            valueImageView.setVisibility(VISIBLE);
            valueImageView.setImageResource(resId);
        } else {
            valueImageView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setIcon(int resId) {
        MarginLayoutParams params = (MarginLayoutParams) textView.getLayoutParams();
        imageViewIsColorful = false;
        if (resId == 0) {
            imageView.setVisibility(GONE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(this.padding);
            } else {
                params.leftMargin = AndroidUtilities.dp(this.padding);
            }
        } else {
            imageView.setImageResource(resId);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            imageView.setBackground(null);
            imageView.setVisibility(VISIBLE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(71);
            } else {
                params.leftMargin = AndroidUtilities.dp(71);
            }
        }
    }

    public void setColorfulIcon(int resId, int color) {
        MarginLayoutParams params = (MarginLayoutParams) textView.getLayoutParams();
        imageViewIsColorful = true;
        if (resId == 0) {
            imageView.setVisibility(GONE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(this.padding);
            } else {
                params.leftMargin = AndroidUtilities.dp(this.padding);
            }
        } else {
            imageView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), color));
            imageView.setImageResource(resId);
            imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
            imageView.setVisibility(VISIBLE);
            if (LocaleController.isRTL) {
                params.rightMargin = AndroidUtilities.dp(71);
            } else {
                params.leftMargin = AndroidUtilities.dp(71);
            }
        }
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        setEnabled(value);
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", value ? 1.0f : 0.5f));
            if (valueTextView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueTextView, "alpha", value ? 1.0f : 0.5f));
            }
            if (valueImageView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueImageView, "alpha", value ? 1.0f : 0.5f));
            }
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            if (valueTextView.getVisibility() == VISIBLE) {
                valueTextView.setAlpha(value ? 1.0f : 0.5f);
            }
            if (valueImageView.getVisibility() == VISIBLE) {
                valueImageView.setAlpha(value ? 1.0f : 0.5f);
            }
        }
    }

    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        textView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        if (valueTextView.getVisibility() == VISIBLE) {
            valueTextView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        }
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (drawLoading || drawLoadingProgress != 0) {
            if (paint == null) {
                paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Theme.getColor(Theme.key_dialogSearchBackground, resourcesProvider));
            }
            //LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT;
            if (incrementLoadingProgress) {
                loadingProgress += 16 / 1000f;
                if (loadingProgress > 1f) {
                    loadingProgress = 1f;
                    incrementLoadingProgress = false;
                }
            } else {
                loadingProgress -= 16 / 1000f;
                if (loadingProgress < 0) {
                    loadingProgress = 0;
                    incrementLoadingProgress = true;
                }
            }

            if (changeProgressStartDelay > 0) {
                changeProgressStartDelay -= 15;
            } else if (drawLoading && drawLoadingProgress != 1f) {
                drawLoadingProgress += 16 / 150f;
                if (drawLoadingProgress > 1f) {
                    drawLoadingProgress = 1f;
                }
            } else if (!drawLoading && drawLoadingProgress != 0) {
                drawLoadingProgress -= 16 / 150f;
                if (drawLoadingProgress < 0) {
                    drawLoadingProgress = 0;
                }
            }

            float alpha = (0.6f + 0.4f * loadingProgress) * drawLoadingProgress;
            paint.setAlpha((int) (255 * alpha));
            int cy = getMeasuredHeight() >> 1;
            AndroidUtilities.rectTmp.set(getMeasuredWidth() - AndroidUtilities.dp(padding) - AndroidUtilities.dp(loadingSize), cy - AndroidUtilities.dp(3), getMeasuredWidth() - AndroidUtilities.dp(padding), cy + AndroidUtilities.dp(3));
            if (LocaleController.isRTL) {
                AndroidUtilities.rectTmp.left = getMeasuredWidth() - AndroidUtilities.rectTmp.left;
                AndroidUtilities.rectTmp.right = getMeasuredWidth() - AndroidUtilities.rectTmp.right;
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
            invalidate();
        }
        valueTextView.setAlpha(1f - drawLoadingProgress);
        super.dispatchDraw(canvas);

        if (needDivider) {
            int offset = AndroidUtilities.dp(imageView.getVisibility() == View.VISIBLE ? 71 : 20);
            canvas.drawLine(LocaleController.isRTL ? 0 : offset, getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? offset : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }


    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(textView.getText() + (valueTextView != null && valueTextView.getVisibility() == View.VISIBLE ? "\n" + valueTextView.getText() : ""));
        info.setEnabled(isEnabled());
    }

    public void setDrawLoading(boolean drawLoading, int size, boolean animated) {
        this.drawLoading = drawLoading;
        this.loadingSize = size;

        if (!animated) {
            drawLoadingProgress = drawLoading ? 1f : 0f;
        } else {
            measureDelay = true;
        }
        invalidate();
    }

    public BackupImageView getValueBackupImageView() {
        if (valueBackupImageView == null) {
            valueBackupImageView = new BackupImageView(getContext());
            addView(valueBackupImageView, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding, 0, padding, 0));
        }
        return valueBackupImageView;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (valueBackupImageView != null && valueBackupImageView.getImageReceiver() != null && valueBackupImageView.getImageReceiver().getDrawable() instanceof AnimatedEmojiDrawable) {
            ((AnimatedEmojiDrawable) valueBackupImageView.getImageReceiver().getDrawable()).removeView(this);
        }
    }

    public void updateRTL() {
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        removeView(textView);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        removeView(valueTextView);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, padding, 0, padding, 0));

        removeView(imageView);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));

        removeView(valueImageView);
        addView(valueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding, 0, padding, 0));
    }
}
