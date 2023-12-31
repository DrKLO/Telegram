package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class ButtonWithCounterView extends FrameLayout {

    private Theme.ResourcesProvider resourcesProvider;

    private final Paint paint;
    public final AnimatedTextView.AnimatedTextDrawable text;
    public final AnimatedTextView.AnimatedTextDrawable subText;
    private final AnimatedTextView.AnimatedTextDrawable countText;
    private float countAlpha;
    private final AnimatedFloat countAlphaAnimated = new AnimatedFloat(350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final View rippleView;
    private final boolean filled;

    public ButtonWithCounterView(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, true, resourcesProvider);
    }

    public ButtonWithCounterView(Context context, boolean filled, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.filled = filled;
        this.resourcesProvider = resourcesProvider;

        ScaleStateListAnimator.apply(this, .02f, 1.2f);

        rippleView = new View(context);
        rippleView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 8, 8));
        addView(rippleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (filled) {
            setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)));
        }

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));

        text = new AnimatedTextView.AnimatedTextDrawable(true, true, false);
        text.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        text.setCallback(this);
        text.setTextSize(dp(14));
        if (filled) {
            text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        }
        text.setTextColor(Theme.getColor(filled ? Theme.key_featuredStickers_buttonText : Theme.key_featuredStickers_addButton, resourcesProvider));
        text.setGravity(Gravity.CENTER_HORIZONTAL);

        subText = new AnimatedTextView.AnimatedTextDrawable(true, true, false);
        subText.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        subText.setCallback(this);
        subText.setTextSize(dp(12));
        subText.setTextColor(Theme.getColor(filled ? Theme.key_featuredStickers_buttonText : Theme.key_featuredStickers_addButton, resourcesProvider));
        subText.setGravity(Gravity.CENTER_HORIZONTAL);

        countText = new AnimatedTextView.AnimatedTextDrawable(false, false, true);
        countText.setAnimationProperties(.3f, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        countText.setCallback(this);
        countText.setTextSize(dp(12));
        countText.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        countText.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        countText.setText("");
        countText.setGravity(Gravity.CENTER_HORIZONTAL);

        setWillNotDraw(false);
    }

    public void updateColors() {
        rippleView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 8, 8));
        text.setTextColor(Theme.getColor(filled ? Theme.key_featuredStickers_buttonText : Theme.key_featuredStickers_addButton, resourcesProvider));
        subText.setTextColor(Theme.getColor(filled ? Theme.key_featuredStickers_buttonText : Theme.key_featuredStickers_addButton, resourcesProvider));
        countText.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
    }

    public void setCounterColor(int color) {
        countText.setTextColor(color);
        counterDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }

    private boolean countFilled = true;
    public void setCountFilled(boolean filled) {
        countFilled = filled;
        countText.setTextSize(dp(countFilled ? 12 : 14));
        countText.setTextColor(
                countFilled ?
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) :
                text.getTextColor()
        );
    }

    private int timerSeconds = 0;
    private Runnable tick;
    public void setTimer(int seconds, Runnable whenTimerUp) {
        AndroidUtilities.cancelRunOnUIThread(tick);

        setCountFilled(false);
        setCount(timerSeconds = seconds, false);
        setShowZero(false);
        AndroidUtilities.runOnUIThread(tick = () -> {
            timerSeconds--;
            setCount(timerSeconds, true);
            if (timerSeconds > 0) {
                AndroidUtilities.runOnUIThread(tick, 1000);
            } else {
                setClickable(true);
                if (whenTimerUp != null) {
                    whenTimerUp.run();
                }
            }
        }, 1000);
    }
    public boolean isTimerActive() {
        return timerSeconds > 0;
    }

    public void setText(CharSequence newText, boolean animated) {
        setText(newText, animated, true);
    }

    public void setText(CharSequence newText, boolean animated, boolean moveDown) {
        if (animated) {
            text.cancelAnimation();
        }
        text.setText(newText, animated, moveDown);
        setContentDescription(newText);
        invalidate();
    }

    private float subTextT = 0f;
    private ValueAnimator subTextVisibleAnimator;
    private boolean subTextVisible;

    public boolean isSubTextVisible() {
        return subTextVisible;
    }

    private void cleanSubTextVisibleAnimator(){
        if (subTextVisibleAnimator != null) {
            subTextVisibleAnimator.cancel();
            subTextVisibleAnimator = null;
        }
    }

    public void setSubText(CharSequence newText, boolean animated) {
        boolean isNewTextVisible = newText != null;
        if (animated) {
            subText.cancelAnimation();
        }

        setContentDescription(newText);
        invalidate();
        if (subTextVisible && !isNewTextVisible) {
            cleanSubTextVisibleAnimator();
            subTextVisibleAnimator = ValueAnimator.ofFloat(subTextT, 0f);
            subTextVisibleAnimator.addUpdateListener(anm -> {
                subTextT = (float) anm.getAnimatedValue();
                invalidate();
            });
            subTextVisibleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    subTextVisible = false;
                    subText.setText(null, false);
                }
            });
            subTextVisibleAnimator.setDuration(200);
            subTextVisibleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            subTextVisibleAnimator.start();
        } else {
            subText.setText(newText, animated);
        }

        if (!subTextVisible && isNewTextVisible) {
            subTextVisible = true;
            cleanSubTextVisibleAnimator();
            subTextVisibleAnimator = ValueAnimator.ofFloat(subTextT, 1f);
            subTextVisibleAnimator.addUpdateListener(anm -> {
                subTextT = (float) anm.getAnimatedValue();
                invalidate();
            });
            subTextVisibleAnimator.setDuration(200);
            subTextVisibleAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            subTextVisibleAnimator.start();
        }
    }

    private float loadingT = 0;
    private boolean loading;
    private ValueAnimator loadingAnimator;
    public void setLoading(boolean loading) {
        if (this.loading != loading) {
            if (loadingAnimator != null) {
                loadingAnimator.cancel();
                loadingAnimator = null;
            }

            loadingAnimator = ValueAnimator.ofFloat(loadingT, (this.loading = loading) ? 1 : 0);
            loadingAnimator.addUpdateListener(anm -> {
                loadingT = (float) anm.getAnimatedValue();
                invalidate();
            });
            loadingAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loadingT = loading ? 1 : 0;
                    invalidate();
                }
            });
            loadingAnimator.setDuration(320);
            loadingAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            loadingAnimator.start();
        }
    }

    public boolean isLoading() {
        return loading;
    }

    private float countScale = 1;
    private ValueAnimator countAnimator;
    private void animateCount() {
        if (countAnimator != null) {
            countAnimator.cancel();
            countAnimator = null;
        }

        countAnimator = ValueAnimator.ofFloat(0, 1);
        countAnimator.addUpdateListener(anm -> {
            countScale = Math.max(1, (float) anm.getAnimatedValue());
            invalidate();
        });
        countAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                countScale = 1;
                invalidate();
            }
        });
        countAnimator.setInterpolator(new OvershootInterpolator(2.0f));
        countAnimator.setDuration(200);
        countAnimator.start();
    }

    private int lastCount;
    private boolean showZero;
    private boolean withCounterIcon;
    private Drawable counterDrawable;

    public void withCounterIcon() {
        withCounterIcon = true;
        counterDrawable = ContextCompat.getDrawable(getContext(), R.drawable.mini_boost_button).mutate();
        counterDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), PorterDuff.Mode.SRC_IN));
    }

    public void setShowZero(boolean showZero) {
        this.showZero = showZero;
    }

    public void setCount(int count, boolean animated) {
        if (animated) {
            countText.cancelAnimation();
        }
        if (animated && count != lastCount && count > 0 && lastCount > 0) {
            animateCount();
        }
        lastCount = count;
        countAlpha = count != 0 || showZero ? 1f : 0f;
        countText.setText("" + count, animated);
        invalidate();
    }

    public void setCount(String count, boolean animated) {
        if (animated) {
            countText.cancelAnimation();
            animateCount();
        }
        lastCount = -1;
        countAlpha = !TextUtils.isEmpty(count) || showZero ? 1f : 0f;
        countText.setText(count, animated);
        invalidate();
    }

    private float enabledT = 1;
    private boolean enabled = true;
    private ValueAnimator enabledAnimator;

    @Override
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            if (enabledAnimator != null) {
                enabledAnimator.cancel();
                enabledAnimator = null;
            }

            enabledAnimator = ValueAnimator.ofFloat(enabledT, (this.enabled = enabled) ? 1 : 0);
            enabledAnimator.addUpdateListener(anm -> {
                enabledT = (float) anm.getAnimatedValue();
                invalidate();
            });
            enabledAnimator.start();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return text == who || subText == who || countText == who || super.verifyDrawable(who);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return false;
    }

    private CircularProgressDrawable loadingDrawable;

    private int globalAlpha = 255;
    private final int subTextAlpha = 200;

    protected float calculateCounterWidth(float width, float percent) {
        return width * percent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rippleView.draw(canvas);

        if (loadingT > 0) {
            if (loadingDrawable == null) {
                loadingDrawable = new CircularProgressDrawable(text.getTextColor());
            }
            int y = (int) ((1f - loadingT) * dp(24));
            loadingDrawable.setBounds(0, y, getWidth(), y + getHeight());
            loadingDrawable.setAlpha((int) (0xFF * loadingT));
            loadingDrawable.draw(canvas);
            invalidate();
        }

        if (loadingT < 1) {
            boolean restore = false;
            if (loadingT != 0) {
                canvas.save();
                canvas.translate(0, (int) (loadingT * dp(-24)));
                canvas.scale(1, 1f - .4f * loadingT);
                restore = true;
            }
            float textWidth = text.getCurrentWidth();
            float countAlpha = countAlphaAnimated.set(this.countAlpha);

            float lightningWidth = withCounterIcon ? AndroidUtilities.dp(12) : 0;
            float width = textWidth + lightningWidth + calculateCounterWidth((dp(5.66f + 5 + 5) + countText.getCurrentWidth()), countAlpha);
            AndroidUtilities.rectTmp2.set(
                    (int) ((getMeasuredWidth() - width - getWidth()) / 2f),
                    (int) ((getMeasuredHeight() - text.getHeight()) / 2f - dp(1)),
                    (int) ((getMeasuredWidth() - width + getWidth()) / 2f + textWidth),
                    (int) ((getMeasuredHeight() + text.getHeight()) / 2f - dp(1))
            );
            AndroidUtilities.rectTmp2.offset(0, (int) (-dp(7) * subTextT));
            text.setAlpha((int) (globalAlpha * (1f - loadingT) * AndroidUtilities.lerp(.5f, 1f, enabledT)));
            text.setBounds(AndroidUtilities.rectTmp2);
            text.draw(canvas);

            if (subTextVisible) {
                float subTextWidth = subText.getCurrentWidth();
                width = subTextWidth;
                AndroidUtilities.rectTmp2.set(
                        (int) ((getMeasuredWidth() - width - getWidth()) / 2f),
                        (int) ((getMeasuredHeight() - subText.getHeight()) / 2f - dp(1)),
                        (int) ((getMeasuredWidth() - width + getWidth()) / 2f + subTextWidth),
                        (int) ((getMeasuredHeight() + subText.getHeight()) / 2f - dp(1))
                );
                AndroidUtilities.rectTmp2.offset(0, dp(11));
                canvas.save();
                float scale = AndroidUtilities.lerp(.1f, 1f, subTextT);
                canvas.scale(scale, scale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.bottom);
                subText.setAlpha((int) (subTextAlpha * (1f - loadingT) * subTextT * AndroidUtilities.lerp(.5f, 1f, enabledT)));
                subText.setBounds(AndroidUtilities.rectTmp2);
                subText.draw(canvas);
                canvas.restore();
            }

            AndroidUtilities.rectTmp2.set(
                    (int) ((getMeasuredWidth() - width) / 2f + textWidth + dp(countFilled ? 5 : 2)),
                    (int) ((getMeasuredHeight() - dp(18)) / 2f),
                    (int) ((getMeasuredWidth() - width) / 2f + textWidth + dp((countFilled ? 5 : 2) + 4 + 4) + Math.max(dp(9), countText.getCurrentWidth() + lightningWidth)),
                    (int) ((getMeasuredHeight() + dp(18)) / 2f)
            );
            AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);

            if (countScale != 1) {
                canvas.save();
                canvas.scale(countScale, countScale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
            }
            if (countFilled) {
                paint.setAlpha((int) (globalAlpha * (1f - loadingT) * countAlpha * countAlpha * AndroidUtilities.lerp(.5f, 1f, enabledT)));
                int radius = withCounterIcon ? dp(4) : dp(10);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, paint);
            }

            int countLength = countText.getText() != null ? countText.getText().length() : 0;
            AndroidUtilities.rectTmp2.offset(-dp(countLength > 1 ? .3f : 0), -dp(.4f));
            countText.setAlpha((int) (globalAlpha * (1f - loadingT) * countAlpha * (countFilled ? 1 : .5f)));
            countText.setBounds(AndroidUtilities.rectTmp2);
            canvas.save();
            if (countFilled && withCounterIcon) {
                counterDrawable.setAlpha((int) (globalAlpha * (1f - loadingT) * countAlpha * 1));
                counterDrawable.setBounds(
                        dp(1) + AndroidUtilities.rectTmp2.left,
                        dp(2) + AndroidUtilities.rectTmp2.top,
                        dp(1) + AndroidUtilities.rectTmp2.left + counterDrawable.getIntrinsicWidth(),
                        dp(2) + AndroidUtilities.rectTmp2.top + counterDrawable.getIntrinsicHeight());
                counterDrawable.draw(canvas);
                canvas.translate(lightningWidth / 2, 0);
            }
            countText.draw(canvas);
            canvas.restore();
            if (countScale != 1) {
                canvas.restore();
            }
            if (restore) {
                canvas.restore();
            }
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
//        info.setContentDescription(text.getText() + (lastCount > 0 ? ", " + LocaleController.formatPluralString("Chats", lastCount) : ""));
    }

    public void setTextAlpha(float v) {
        text.setAlpha((int) (v * 255));
    }

    public void setGlobalAlpha(float v) {
        globalAlpha = ((int) (v * 255));
    }
}
