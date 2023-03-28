package org.telegram.ui.Components.Premium;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyStubSpan;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class LimitPreviewView extends LinearLayout {

    public int gradientTotalHeight;
    boolean wasAnimation;
    CounterView limitIcon;

    boolean inc;
    float progress;

    int icon;

    TextView premiumCount;
    TextView defaultCount;
    private float position;
    private View parentVideForGradient;
    PremiumGradient.PremiumGradientTools staticGradient;
    int gradientYOffset;
    boolean wasHaptic;
    boolean animationCanPlay = true;
    LinearLayout limitsContainer;
    private boolean premiumLocked;

    @SuppressLint("SetTextI18n")
    public LimitPreviewView(@NonNull Context context, int icon, int currentValue, int premiumLimit) {
        super(context);
        this.icon = icon;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        if (icon != 0) {
            setPadding(0, AndroidUtilities.dp(16), 0, 0);
            limitIcon = new CounterView(context);

            setIconValue(currentValue);

            limitIcon.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(6), AndroidUtilities.dp(24), AndroidUtilities.dp(14));
            addView(limitIcon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT));
        }
        limitsContainer = new LinearLayout(context) {

            Paint grayPaint = new Paint();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                grayPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), grayPaint);

                canvas.save();
                canvas.clipRect(getMeasuredWidth() / 2f, 0, getMeasuredWidth(), getMeasuredHeight());
                Paint paint = PremiumGradient.getInstance().getMainGradientPaint();
                if (parentVideForGradient != null) {
                    View parent = parentVideForGradient;
                    if (staticGradient != null) {
                        paint = staticGradient.paint;
                        staticGradient.gradientMatrixLinear(gradientTotalHeight, -gradientYOffset);
                    } else {
                        float y = 0;
                        View child = this;
                        while (child != parent) {
                            y += child.getY();
                            child = (View) child.getParent();
                        }
                        PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, parent.getMeasuredWidth(), parent.getMeasuredHeight(), getGlobalXOffset() - getLeft(), -y);
                    }

                } else {
                    PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, LimitPreviewView.this.getMeasuredWidth(), LimitPreviewView.this.getMeasuredHeight(), getGlobalXOffset() - getLeft(), -getTop());
                }
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
                canvas.restore();
                if (staticGradient == null) {
                    invalidate();
                }
                super.dispatchDraw(canvas);
            }
        };
        limitsContainer.setOrientation(LinearLayout.HORIZONTAL);

        FrameLayout limitLayout = new FrameLayout(context);

        TextView freeTextView = new TextView(context);
        freeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        freeTextView.setText(LocaleController.getString("LimitFree", R.string.LimitFree));
        freeTextView.setGravity(Gravity.CENTER_VERTICAL);
        freeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        freeTextView.setPadding(AndroidUtilities.dp(12), 0, 0, 0);

        defaultCount = new TextView(context);
        defaultCount.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        defaultCount.setText(Integer.toString(premiumLimit));
        defaultCount.setGravity(Gravity.CENTER_VERTICAL);
        defaultCount.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        limitLayout.addView(freeTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT, 0, 0, 36, 0));
        limitLayout.addView(defaultCount, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.RIGHT, 0, 0, 12, 0));

        limitsContainer.addView(limitLayout, LayoutHelper.createLinear(0, 30, 1f));

        FrameLayout limitLayout2 = new FrameLayout(context);

        TextView limitTextView = new TextView(context);
        limitTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        limitTextView.setText(LocaleController.getString("LimitPremium", R.string.LimitPremium));
        limitTextView.setGravity(Gravity.CENTER_VERTICAL);
        limitTextView.setTextColor(Color.WHITE);
        limitTextView.setPadding(AndroidUtilities.dp(12), 0, 0, 0);

        premiumCount = new TextView(context);
        premiumCount.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        premiumCount.setText(Integer.toString(premiumLimit));
        premiumCount.setGravity(Gravity.CENTER_VERTICAL);
        premiumCount.setTextColor(Color.WHITE);

        limitLayout2.addView(limitTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT, 0, 0, 36, 0));
        limitLayout2.addView(premiumCount, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.RIGHT, 0, 0, 12, 0));

        limitsContainer.addView(limitLayout2, LayoutHelper.createLinear(0, 30, 1f));

        addView(limitsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 14, icon == 0 ? 0 : 12, 14, 0));
    }

    public void setIconValue(int currentValue) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("d ").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
        spannableStringBuilder.append(Integer.toString(currentValue));
        limitIcon.setText(spannableStringBuilder);
    }

    private float getGlobalXOffset() {
        return -LimitPreviewView.this.getMeasuredWidth() * 0.1f * progress - LimitPreviewView.this.getMeasuredWidth() * 0.2f;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (staticGradient == null) {
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
            invalidate();
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!wasAnimation && limitIcon != null && animationCanPlay && !premiumLocked) {
            int padding = AndroidUtilities.dp(14);
            float fromX = 0;
            float toX = padding + (getMeasuredWidth() - padding * 2) * position - limitIcon.getMeasuredWidth() / 2f;
            float fromProgressCenter = 0.5f;
            float toProgressCenter = 0.5f;
            if (toX > getMeasuredWidth() - padding - limitIcon.getMeasuredWidth()) {
                toX = getMeasuredWidth() - padding - limitIcon.getMeasuredWidth();
                toProgressCenter = 1f;
            }
            limitIcon.setAlpha(1f);
            limitIcon.setTranslationX(fromX);
            limitIcon.setPivotX(limitIcon.getMeasuredWidth() / 2f);
            limitIcon.setPivotY(limitIcon.getMeasuredHeight());
            limitIcon.setScaleX(0);
            limitIcon.setScaleY(0);
            limitIcon.createAnimationLayouts();

            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
            float finalToX = toX;
            float finalToProgressCenter = toProgressCenter;
            valueAnimator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                float moveValue = Math.min(1f, v);
                if (v > 1f) {
                    if (!wasHaptic) {
                        wasHaptic = true;
                        limitIcon.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    limitIcon.setRotation((v - 1f) * 60);
                } else {
                    limitIcon.setRotation(0);
                }
                limitIcon.setTranslationX(fromX * (1f - moveValue) + finalToX * moveValue);
                float arrowCenter = fromProgressCenter * (1f - moveValue) + finalToProgressCenter * moveValue;
                limitIcon.setArrowCenter(arrowCenter);
                float scale = Math.min(1, moveValue * 2f);
                limitIcon.setScaleX(scale);
                limitIcon.setScaleY(scale);
                limitIcon.setPivotX(limitIcon.getMeasuredWidth() * arrowCenter);
            });

            valueAnimator.setInterpolator(new OvershootInterpolator());
            valueAnimator.setDuration(1000);
            valueAnimator.setStartDelay(200);
            valueAnimator.start();

            wasAnimation = true;
        } else if (premiumLocked) {
            int padding = AndroidUtilities.dp(14);
            float toX = padding + (getMeasuredWidth() - padding * 2) * 0.5f - limitIcon.getMeasuredWidth() / 2f;
            if (!wasAnimation && animationCanPlay) {
                wasAnimation = true;
                limitIcon.animate().alpha(1).scaleX(1).scaleY(1).setDuration(200).setInterpolator(new OvershootInterpolator()).start();
            } else if (!wasAnimation) {
                limitIcon.setAlpha(0);
                limitIcon.setScaleX(0);
                limitIcon.setScaleY(0);
            } else {
                limitIcon.setAlpha(1f);
                limitIcon.setScaleX(1f);
                limitIcon.setScaleY(1f);
            }
            limitIcon.setTranslationX(toX);
        } else if (limitIcon != null){
            limitIcon.setAlpha(0);
        }
    }

    public void setType(int type) {
        if (type == LimitReachedBottomSheet.TYPE_LARGE_FILE) {
            if (limitIcon != null) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                spannableStringBuilder.append("d ").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
                spannableStringBuilder.append(UserConfig.getInstance(UserConfig.selectedAccount).isPremium() ? "4 GB" : "2 GB");
                limitIcon.setText(spannableStringBuilder);
            }
            premiumCount.setText("4 GB");
        } else if (type == LimitReachedBottomSheet.TYPE_ADD_MEMBERS_RESTRICTED) {
            if (limitIcon != null) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                spannableStringBuilder.append("d").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
                limitIcon.setText(spannableStringBuilder);
            }
            premiumCount.setText("");
        }
    }

    public void setBagePosition(float position) {
        this.position = position;
    }

    public void setParentViewForGradien(ViewGroup containerView) {
        parentVideForGradient = containerView;
    }

    public void setStaticGradinet(PremiumGradient.PremiumGradientTools gradientTools) {
        staticGradient = gradientTools;
    }

    public void setDelayedAnimation() {
        animationCanPlay = false;
    }

    public void startDelayedAnimation() {
        animationCanPlay = true;
        requestLayout();
    }

    public void setPremiumLocked() {
        limitsContainer.setVisibility(View.GONE);
        limitIcon.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(3), AndroidUtilities.dp(24), AndroidUtilities.dp(3));
        premiumLocked = true;
    }

    private class LimitTextView extends LinearLayout {

        public LimitTextView(Context context) {
            super(context);
        }

    }

    private class CounterView extends View {

        Path path = new Path();
        PathEffect pathEffect = new CornerPathEffect(AndroidUtilities.dp(6));
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        StaticLayout textLayout;
        float textWidth;
        CharSequence text;

        ArrayList<AnimatedLayout> animatedLayouts = new ArrayList<AnimatedLayout>();
        StaticLayout animatedStableLayout;
        boolean animationInProgress;

        float arrowCenter;
        boolean invalidatePath;

        public CounterView(Context context) {
            super(context);
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textPaint.setTextSize(AndroidUtilities.dp(22));
            textPaint.setColor(Color.WHITE);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            textWidth = textPaint.measureText(text, 0, text.length());
            textLayout = new StaticLayout(text, textPaint, (int) textWidth + AndroidUtilities.dp(12), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            setMeasuredDimension((int) (textWidth + getPaddingRight() + getPaddingLeft()), AndroidUtilities.dp(44) + AndroidUtilities.dp(8));
            updatePath();
        }

        private void updatePath() {
            int h = getMeasuredHeight() - AndroidUtilities.dp(8);
            float widthHalf = getMeasuredWidth() * arrowCenter;
            float x2 = Utilities.clamp(widthHalf + AndroidUtilities.dp(8), getMeasuredWidth(), 0);
            float x3 =  Utilities.clamp(widthHalf + AndroidUtilities.dp(10), getMeasuredWidth(), 0);

            path.rewind();
            path.moveTo(widthHalf - AndroidUtilities.dp(24), h - h / 2f - AndroidUtilities.dp(2));
            path.lineTo(widthHalf - AndroidUtilities.dp(24), h);
            path.lineTo(widthHalf - AndroidUtilities.dp(8), h);
            path.lineTo(widthHalf, h + AndroidUtilities.dp(8));
            if (arrowCenter < 0.7f) {
                path.lineTo(x2, h);
            }
            path.lineTo(x3, h);
            path.lineTo(x3, h - h / 2f - AndroidUtilities.dp(2));
            path.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int h = getMeasuredHeight() - AndroidUtilities.dp(8);
            if (premiumLocked) {
                h = getMeasuredHeight();
                PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, LimitPreviewView.this.getMeasuredWidth(), LimitPreviewView.this.getMeasuredHeight(), getGlobalXOffset() - getX(), -getTop());
                AndroidUtilities.rectTmp.set(0, AndroidUtilities.dp(3), getMeasuredWidth(), h - AndroidUtilities.dp(3));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, PremiumGradient.getInstance().getPremiumLocakedPaint());
            } else {
                if (invalidatePath) {
                    invalidatePath = false;
                    updatePath();
                }
                PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, LimitPreviewView.this.getMeasuredWidth(), LimitPreviewView.this.getMeasuredHeight(), getGlobalXOffset() - getX(), -getTop());
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), h);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, PremiumGradient.getInstance().getMainGradientPaint());
                PremiumGradient.getInstance().getMainGradientPaint().setPathEffect(pathEffect);
                canvas.drawPath(path, PremiumGradient.getInstance().getMainGradientPaint());
                PremiumGradient.getInstance().getMainGradientPaint().setPathEffect(null);
                invalidate();
            }

            float x = (getMeasuredWidth() - textLayout.getWidth()) / 2f;
            float y = (h - textLayout.getHeight()) / 2f;
            if (!animationInProgress) {
                if (textLayout != null) {
                    canvas.save();
                    canvas.translate(x, y);
                    textLayout.draw(canvas);
                    canvas.restore();
                }
            } else {
                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(8));
                if (animatedStableLayout != null) {
                    canvas.save();
                    canvas.translate(x, y);
                    animatedStableLayout.draw(canvas);
                    canvas.restore();
                }
                for (int i = 0; i < animatedLayouts.size(); i++) {
                    AnimatedLayout animatedLayout = animatedLayouts.get(i);
                    canvas.save();
                    if (animatedLayout.direction) {
                        canvas.translate(x + animatedLayout.x, y - h * 10 * animatedLayout.progress + h * (10 - animatedLayout.staticLayouts.size()));
                        for (int j = 0; j < animatedLayout.staticLayouts.size(); j++) {
                            canvas.translate(0, h);
                            animatedLayout.staticLayouts.get(j).draw(canvas);
                        }
                    } else {
                        canvas.translate(x + animatedLayout.x, y + h * 10 * animatedLayout.progress - h * (10 - animatedLayout.staticLayouts.size()));
                        for (int j = 0; j < animatedLayout.staticLayouts.size(); j++) {
                            canvas.translate(0, -h);
                            animatedLayout.staticLayouts.get(j).draw(canvas);
                        }
                    }
                    canvas.restore();
                }

                canvas.restore();
            }
        }

        @Override
        public void setTranslationX(float translationX) {
            if (translationX != getTranslationX()) {
                super.setTranslationX(translationX);
                invalidate();
            }
        }

        void createAnimationLayouts() {
            animatedLayouts.clear();
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);

            boolean direction = true;
            int directionCount = 0;
            for (int i = 0; i < text.length(); i++) {
                if (Character.isDigit(text.charAt(i))) {
                    AnimatedLayout animatedLayout = new AnimatedLayout();
                    animatedLayouts.add(animatedLayout);
                    animatedLayout.x = textLayout.getSecondaryHorizontal(i);
                    animatedLayout.direction = direction;
                    if (directionCount >= 1) {
                        direction = !direction;
                        directionCount = 0;
                    }
                    directionCount++;

                    int digit = text.charAt(i) - '0';
                    if (digit == 0) {
                        digit = 10;
                    }
                    for (int j = 1; j <= digit; j++) {
                        int k = j;
                        if (k == 10) {
                            k = 0;
                        }
                        String str = "" + k;
                        StaticLayout staticLayout = new StaticLayout(str, textPaint, (int) textWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        animatedLayout.staticLayouts.add(staticLayout);
                    }
                    spannableStringBuilder.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                }
            }
            animatedStableLayout = new StaticLayout(spannableStringBuilder, textPaint, (int) textWidth + AndroidUtilities.dp(12), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            for (int i = 0; i < animatedLayouts.size(); i++) {
                animationInProgress = true;
                AnimatedLayout layout = animatedLayouts.get(i);
                layout.valueAnimator = ValueAnimator.ofFloat(0, 1f);
                layout.valueAnimator.addUpdateListener(animation -> {
                    layout.progress = (float) animation.getAnimatedValue();
                    invalidate();
                });
                layout.valueAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        layout.valueAnimator = null;
                        checkAnimationComplete();
                    }
                });
                layout.valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                layout.valueAnimator.setDuration(750);
                layout.valueAnimator.setStartDelay((animatedLayouts.size() - 1 - i) * 60L);
                layout.valueAnimator.start();
            }
        }

        private void checkAnimationComplete() {
            for (int i = 0; i < animatedLayouts.size(); i++) {
                if (animatedLayouts.get(i).valueAnimator != null) {
                    return;
                }
            }
            animatedLayouts.clear();
            animationInProgress = false;
            invalidate();
        }

        public void setText(CharSequence text) {
            this.text = text;
        }

        public void setArrowCenter(float v) {
            if (arrowCenter != v) {
                arrowCenter = v;
                invalidatePath = true;
                invalidate();
            }
        }

        private class AnimatedLayout {
            ArrayList<StaticLayout> staticLayouts = new ArrayList<>();
            float progress;
            public boolean direction;
            float x;
            ValueAnimator valueAnimator;
        }
    }
}
