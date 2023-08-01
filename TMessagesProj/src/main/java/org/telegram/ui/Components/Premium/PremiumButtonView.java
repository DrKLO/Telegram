package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CounterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

public class PremiumButtonView extends FrameLayout {

    private Paint paintOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float progress;
    private boolean inc;
    public AnimatedTextView buttonTextView;
    public AnimatedTextView overlayTextView;
    private int radius;

    private boolean showOverlay;
    private float overlayProgress;
    public FrameLayout buttonLayout;
    ValueAnimator overlayAnimator;

    Path path = new Path();
    CellFlickerDrawable flickerDrawable;
    private boolean drawOverlayColor;

    RLottieImageView iconView;

    private boolean isButtonTextSet;

    private boolean isFlickerDisabled;
    CounterView counterView;

    public PremiumButtonView(@NonNull Context context, boolean createOverlayTextView) {
        this(context, AndroidUtilities.dp(8), createOverlayTextView);
    }

    public PremiumButtonView(@NonNull Context context, int radius, boolean createOverlayTextView) {
        super(context);
        this.radius = radius;

        flickerDrawable = new CellFlickerDrawable();
        flickerDrawable.animationSpeedScale = 1.2f;
        flickerDrawable.drawFrame = false;
        flickerDrawable.repeatProgress = 4f;
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonTextView = new AnimatedTextView(context);
        buttonTextView.setAnimationProperties(.35f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(Color.WHITE);
        buttonTextView.setTextSize(AndroidUtilities.dp(14));
        buttonTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));

        iconView = new RLottieImageView(context);
        iconView.setColorFilter(Color.WHITE);
        iconView.setVisibility(View.GONE);

        buttonLayout = new FrameLayout(context);
        buttonLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        buttonLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius, Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 120)));

        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        linearLayout.addView(iconView, LayoutHelper.createLinear(24, 24, 0, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));
        addView(buttonLayout);

        if (createOverlayTextView) {
            overlayTextView = new AnimatedTextView(context, true, true, true);
            overlayTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            overlayTextView.setGravity(Gravity.CENTER);
            overlayTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            overlayTextView.setTextSize(AndroidUtilities.dp(14));
            overlayTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            overlayTextView.getDrawable().setAllowCancel(true);
            overlayTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 120)));
            addView(overlayTextView);

            paintOverlayPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            updateOverlayProgress();
        }
    }

    public RLottieImageView getIconView() {
        return iconView;
    }
    public AnimatedTextView getTextView() {
        return buttonTextView;
    }

    AnimatedFloat counterOffset = new AnimatedFloat(this);
    AnimatedFloat counterOffset2 = new AnimatedFloat(this);

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (counterView != null) {
            counterOffset.set((counterView.counterDrawable.getWidth() * 0.85f + AndroidUtilities.dp(3)) / 2f);
            counterOffset2.set(getMeasuredWidth() / 2f + (overlayTextView.getDrawable().getWidth()) / 2f + AndroidUtilities.dp(3));
            overlayTextView.setTranslationX(-counterOffset.get());
            counterView.setTranslationX(counterOffset2.get() - counterOffset.get());
        } else {
            if (overlayTextView != null) {
                overlayTextView.setTranslationX(0);
            }
        }
        AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        if (overlayProgress != 1f || !drawOverlayColor) {
            if (inc) {
                progress += 16f / 1000f;
                if (progress > 3) {
                    inc = false;
                }
            } else {
                progress -= 16f / 1000f;
                if (progress < 1) {
                    inc = true;
                }
            }
            PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), -getMeasuredWidth() * 0.1f * progress, 0);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, PremiumGradient.getInstance().getMainGradientPaint());
            invalidate();
        }

        if (!BuildVars.IS_BILLING_UNAVAILABLE && !isFlickerDisabled) {
            flickerDrawable.setParentWidth(getMeasuredWidth());
            flickerDrawable.draw(canvas, AndroidUtilities.rectTmp, radius, null);
        }

        if (overlayProgress != 0 && drawOverlayColor) {
            paintOverlayPaint.setAlpha((int) (255 * overlayProgress));
            if (overlayProgress != 1f) {
                path.rewind();
                path.addCircle(getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, Math.max(getMeasuredWidth(), getMeasuredHeight()) * 1.4f * overlayProgress, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(path);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, paintOverlayPaint);
                canvas.restore();
            } else {
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, paintOverlayPaint);
            }

        }

        super.dispatchDraw(canvas);
    }

    public void setOverlayText(String text, boolean drawOverlayColor, boolean animated) {
        showOverlay = true;
        this.drawOverlayColor = drawOverlayColor;
        overlayTextView.setText(text, animated);
        updateOverlay(animated);
    }


    private void updateOverlay(boolean animated) {
        if (overlayAnimator != null) {
            overlayAnimator.removeAllListeners();
            overlayAnimator.cancel();
        }
        if (!animated) {
            overlayProgress = showOverlay ? 1f : 0;
            updateOverlayProgress();
            return;
        }
        overlayAnimator = ValueAnimator.ofFloat(overlayProgress, showOverlay ? 1f : 0);
        overlayAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                overlayProgress = (float) animation.getAnimatedValue();
                updateOverlayProgress();
            }
        });
        overlayAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                overlayProgress = showOverlay ? 1f : 0f;
                updateOverlayProgress();
            }
        });
        overlayAnimator.setDuration(250);
        overlayAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        overlayAnimator.start();
    }

    private void updateOverlayProgress() {
        overlayTextView.setAlpha(overlayProgress);
        overlayTextView.setTranslationY(AndroidUtilities.dp(12) * (1f - overlayProgress));
        buttonLayout.setAlpha(1f - overlayProgress);
        buttonLayout.setTranslationY(-AndroidUtilities.dp(12) * (overlayProgress));
        buttonLayout.setVisibility(overlayProgress == 1f ? View.INVISIBLE : View.VISIBLE);
        overlayTextView.setVisibility(overlayProgress == 0 ? View.INVISIBLE : View.VISIBLE);
        invalidate();
    }

    public void clearOverlayText() {
        showOverlay = false;
        updateOverlay(true);
    }

    public void setIcon(int id) {
        iconView.setAnimation(id, 24, 24);
        flickerDrawable.progress = 2f;
        flickerDrawable.setOnRestartCallback(() -> {
            iconView.getAnimatedDrawable().setCurrentFrame(0, true);
            iconView.playAnimation();
        });
        invalidate();
        iconView.setVisibility(View.VISIBLE);
    }

    public void hideIcon() {
        flickerDrawable.setOnRestartCallback(null);
        iconView.setVisibility(View.GONE);
    }

    public void setFlickerDisabled(boolean flickerDisabled) {
        isFlickerDisabled = flickerDisabled;
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        buttonLayout.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return buttonLayout.isEnabled();
    }

    public void setButton(String text, View.OnClickListener clickListener) {
        setButton(text, clickListener, false);
    }

    public void setButton(String text, View.OnClickListener clickListener, boolean animated) {
        if (!isButtonTextSet && animated) {
            animated = true;
        }
        isButtonTextSet = true;
        if (animated && buttonTextView.isAnimating()) {
            buttonTextView.cancelAnimation();
        }
        buttonTextView.setText(text, animated);
        buttonLayout.setOnClickListener(clickListener);
    }

    public void checkCounterView() {
        if (counterView == null) {
            counterView = new CounterView(getContext(), null);
            counterView.setGravity(Gravity.LEFT);
            counterView.setColors(Theme.key_featuredStickers_addButton, Theme.key_featuredStickers_buttonText);
            counterView.counterDrawable.circleScale = 0.8f;
            setClipChildren(false);
            addView(counterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.CENTER_VERTICAL));
        }
    }
}
