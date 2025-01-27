package org.telegram.ui.Components.Premium;

import static android.graphics.Canvas.ALL_SAVE_FLAG;
import static org.telegram.messenger.AndroidUtilities.dp;

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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyStubSpan;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class LimitPreviewView extends LinearLayout {

    public interface DarkGradientProvider {
        Paint setDarkGradientLocation(float x, float y);
    }

    private float percent;
    private final int premiumLimit;
    private int currentValue;
    public int gradientTotalHeight;
    boolean wasAnimation;
    CounterView limitIcon;

    boolean inc;
    float progress;
    int width1;

    int icon;

    TextView premiumCount;
    TextView defaultCount;
    private float position;
    private View parentVideForGradient;
    PremiumGradient.PremiumGradientTools staticGradient;
    int gradientYOffset;
    boolean wasHaptic;
    boolean animationCanPlay = true;
    FrameLayout limitsContainer;
    private boolean premiumLocked;
    private final TextView defaultText;
    private final TextView premiumText;
    private boolean isBoostsStyle;
    private boolean isSimpleStyle;

    Theme.ResourcesProvider resourcesProvider;
    private boolean animateIncrease;
    private int animateIncreaseWidth;
    float limitIconRotation;
    public boolean isStatistic;
    public boolean invalidationEnabled = true;
    private DarkGradientProvider darkGradientProvider;

    public LimitPreviewView(@NonNull Context context, int icon, int currentValue, int premiumLimit, Theme.ResourcesProvider resourcesProvider) {
        this(context, icon, currentValue, premiumLimit, .5f, resourcesProvider);
    }

    @SuppressLint("SetTextI18n")
    public LimitPreviewView(@NonNull Context context, int icon, int currentValue, int premiumLimit, float inputPercent, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.percent = MathUtils.clamp(inputPercent, 0.1f, 0.9f);
        this.icon = icon;
        this.currentValue = currentValue;
        this.premiumLimit = premiumLimit;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        if (icon != 0) {
            setPadding(0, dp(16), 0, 0);
            limitIcon = new CounterView(context);

            setIconValue(currentValue, false);

            limitIcon.setPadding(dp(19), dp(6), dp(19), dp(14));
            addView(limitIcon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.LEFT));
        }

        final FrameLayout defaultLayout = new TextViewHolder(context, true);

        defaultText = new TextView(context);
        defaultText.setTypeface(AndroidUtilities.bold());
        defaultText.setText(LocaleController.getString(R.string.LimitFree));
        defaultText.setGravity(Gravity.CENTER_VERTICAL);
        defaultText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));

        defaultCount = new TextView(context);
        defaultCount.setTypeface(AndroidUtilities.bold());
        defaultCount.setText(String.format("%d", premiumLimit));
        defaultCount.setGravity(Gravity.CENTER_VERTICAL);
        defaultCount.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));

        if (LocaleController.isRTL) {
            defaultLayout.addView(defaultText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.RIGHT, 12, 0, 12, 0));
            defaultLayout.addView(defaultCount, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.LEFT, 12, 0, 12, 0));
        } else {
            defaultLayout.addView(defaultText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT, 12, 0, 12, 0));
            defaultLayout.addView(defaultCount, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.RIGHT, 12, 0, 12, 0));
        }

        final FrameLayout premiumLayout = new TextViewHolder(context, false);

        premiumText = new TextView(context);
        premiumText.setTypeface(AndroidUtilities.bold());
        premiumText.setText(LocaleController.getString(R.string.LimitPremium));
        premiumText.setGravity(Gravity.CENTER_VERTICAL);
        premiumText.setTextColor(Color.WHITE);

        premiumCount = new TextView(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
            }

            @Override
            public void setTextColor(int color) {
                super.setTextColor(color);
            }

            @Override
            public void setText(CharSequence text, BufferType type) {
                super.setText(text, type);
            }
        };
        premiumCount.setTypeface(AndroidUtilities.bold());
        premiumCount.setText(String.format("%d", premiumLimit));
        premiumCount.setGravity(Gravity.CENTER_VERTICAL);
        premiumCount.setTextColor(Color.WHITE);

        if (LocaleController.isRTL) {
            premiumLayout.addView(premiumText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.RIGHT, 12, 0, 12, 0));
            premiumLayout.addView(premiumCount, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.LEFT, 12, 0, 12, 0));
        } else {
            premiumLayout.addView(premiumText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.LEFT, 12, 0, 12, 0));
            premiumLayout.addView(premiumCount, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.RIGHT, 12, 0, 12, 0));
        }

        limitsContainer = new FrameLayout(context) {

            Paint grayPaint = new Paint();
            Paint whitePaint = new Paint();

            {
                whitePaint.setColor(Color.WHITE);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (isBoostsStyle) {
                    if (isStatistic) {
                        grayPaint.setColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));
                    } else {
                        grayPaint.setColor(Theme.getColor(Theme.key_graySection, resourcesProvider));
                    }
                } else {
                    grayPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
                }
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());

                if (hasDarkGradientProvider()) {
                    Paint p = darkGradientProvider.setDarkGradientLocation((((ViewGroup) getParent()).getX() + getX()), (((ViewGroup) getParent()).getY() + getY()));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), p);
                } else {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), grayPaint);
                }

                canvas.save();
                if (!isBoostsStyle) {
                    canvas.clipRect(width1, 0, getMeasuredWidth(), getMeasuredHeight());
                }
                Paint paint = hasDarkGradientProvider() ? whitePaint : PremiumGradient.getInstance().getMainGradientPaint();
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
                if (isBoostsStyle) {
                    AndroidUtilities.rectTmp.set(0, 0, width1, getMeasuredHeight());
                }
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(6), dp(6), paint);
                canvas.restore();
                if (staticGradient == null && invalidationEnabled) {
                    invalidate();
                }
                super.dispatchDraw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (getChildCount() == 2) {
                    final int width = MeasureSpec.getSize(widthMeasureSpec);
                    final int height = MeasureSpec.getSize(heightMeasureSpec);
                    defaultLayout.measure(
                            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                    );
                    final int minWidth1 = Math.max(defaultLayout.getMeasuredWidth(), dp(24) + defaultText.getMeasuredWidth() + (defaultCount.getVisibility() == View.VISIBLE ? dp(24) + defaultCount.getMeasuredWidth() : 0));
                    premiumLayout.measure(
                            MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                    );
                    if (isBoostsStyle) {
                        if (percent == 0) {
                            width1 = 0;
                            premiumCount.setTextColor(hasDarkGradientProvider() ? Color.WHITE : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                            defaultText.setTextColor(hasDarkGradientProvider() ? Color.WHITE : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                        } else if (percent < 1f) {
                            float leftWidth = defaultLayout.getMeasuredWidth() - AndroidUtilities.dp(8);
                            float rightWidth = premiumLayout.getMeasuredWidth() - AndroidUtilities.dp(8);
                            float availableWidth = width - leftWidth - rightWidth;
                            width1 = (int) (leftWidth + availableWidth * percent);
                            premiumCount.setTextColor(hasDarkGradientProvider() ? Color.WHITE : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                            defaultText.setTextColor(Color.WHITE);
                        } else {
                            width1 = width;
                            premiumCount.setTextColor(Color.WHITE);
                            defaultText.setTextColor(Color.WHITE);
                        }
                    } else {
                        final int minWidth2 = Math.max(premiumLayout.getMeasuredWidth(), dp(24) + premiumText.getMeasuredWidth() + (premiumCount.getVisibility() == View.VISIBLE ? dp(24) + premiumCount.getMeasuredWidth() : 0));
                        width1 = (int) Utilities.clamp(width * percent, width - minWidth2, minWidth1);
                        defaultLayout.measure(
                                MeasureSpec.makeMeasureSpec(width1, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                        );
                        premiumLayout.measure(
                                MeasureSpec.makeMeasureSpec(width - width1, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                        );
                    }
                    setMeasuredDimension(width, height);
                } else {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (getChildCount() == 2) {
                    View child1 = getChildAt(0);
                    View child2 = getChildAt(1);
                    final int w = child1.getMeasuredWidth();
                    child1.layout(0, 0, w, b - t);
                    child2.layout(w, 0, r - l, b - t);
                } else {
                    super.onLayout(changed, l, t, r, b);
                }
            }
        };
        limitsContainer.addView(defaultLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30));
        limitsContainer.addView(premiumLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30));
        addView(limitsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 30, 0, 0, 14, icon == 0 ? 0 : 12, 14, 0));
    }

    public void setDarkGradientProvider(DarkGradientProvider darkGradientProvider) {
        this.darkGradientProvider = darkGradientProvider;
    }

    private boolean hasDarkGradientProvider() {
        return darkGradientProvider != null;
    }

    public void setIconValue(int currentValue, boolean animated) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("d").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
        spannableStringBuilder.append(" ").setSpan(new RelativeSizeSpan(0.8f), 1, 2, 0);
        spannableStringBuilder.append(Integer.toString(currentValue));
        limitIcon.setText(spannableStringBuilder, animated);
        limitIcon.requestLayout();
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

    private ValueAnimator arrowAnimator;
    private boolean animatingRotation;
    private boolean lastProgressCenter;

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (animateIncrease || (!wasAnimation && limitIcon != null && animationCanPlay && !premiumLocked)) {
            int padding = dp(14);
            boolean animateIncreaseFinal = animateIncrease;
            animateIncrease = false;
            float fromX = animateIncreaseFinal ? limitIcon.getTranslationX() : 0;
            float toX = padding + Math.max(width1, (getMeasuredWidth() - padding * 2) * position) - limitIcon.getMeasuredWidth() / 2f;
            float fromProgressCenter = 0.5f;
            float toProgressCenter = 0.5f;
            if (isSimpleStyle) {
                fromProgressCenter = limitIcon.getArrowCenter();
                toX = Utilities.clamp(toX, getMeasuredWidth() - padding - limitIcon.getMeasuredWidth(), padding);
                if (width1 <= 0) {
                    toProgressCenter = 0f;
                } else if (width1 >= getMeasuredWidth() - padding * 2) {
                    toProgressCenter = 1f;
                } else {
                    toProgressCenter = Utilities.clamp((float) (width1 - (toX - padding)) / limitIcon.getMeasuredWidth(), 1f, 0f);
                }
            } else {
                if (toX < padding) {
                    toX = padding;
                    fromProgressCenter = toProgressCenter = 0f;
                }
                if (toX > getMeasuredWidth() - padding - limitIcon.getMeasuredWidth()) {
                    toX = getMeasuredWidth() - padding - limitIcon.getMeasuredWidth();
                    toProgressCenter = 1f;
                }
            }
            limitIcon.setAlpha(1f);
            limitIcon.setTranslationX(fromX);
            limitIcon.setPivotX(limitIcon.getMeasuredWidth() / 2f);
            limitIcon.setPivotY(limitIcon.getMeasuredHeight());
            if (!animateIncreaseFinal) {
                limitIcon.setScaleX(0);
                limitIcon.setScaleY(0);
                limitIcon.createAnimationLayouts();
            }

            arrowAnimator = ValueAnimator.ofFloat(0, 1f);
            float finalToX = toX;
            float finalToProgressCenter = toProgressCenter;
            float toWidth = width1;
            if (animateIncreaseFinal) {
                width1 = animateIncreaseWidth;
            }
            float finalFromProgressCenter = fromProgressCenter;
            final boolean animatingRotate = !animatingRotation;
            animatingRotation = true;
            arrowAnimator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                float moveValue = Math.min(1f, v);
                if (v > 1f && animatingRotate) {
                    if (!wasHaptic) {
                        wasHaptic = true;
                        try {
                            limitIcon.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        } catch (Exception ignored) {}
                    }
                    limitIcon.setRotation(limitIconRotation + (v - 1f) * 60);
                } else if (!animatingRotation) {
                    limitIcon.setRotation(limitIconRotation);
                }
                if (animation == arrowAnimator) {
                    limitIcon.setTranslationX(fromX * (1f - moveValue) + finalToX * moveValue);
                    float arrowCenter = finalFromProgressCenter * (1f - moveValue) + finalToProgressCenter * moveValue;
                    limitIcon.setArrowCenter(arrowCenter);
                    limitIcon.setPivotX(limitIcon.getMeasuredWidth() * arrowCenter);
                }
                float scale = Math.min(1, moveValue * 2f);
                if (!animateIncreaseFinal) {
                    limitIcon.setScaleX(scale);
                    limitIcon.setScaleY(scale);
                } else {
                    width1 = (int) AndroidUtilities.lerp(animateIncreaseWidth, toWidth, moveValue);
                    limitsContainer.invalidate();
                }
            });
            arrowAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animatingRotate) {
                        animatingRotation = false;
                    }
                }
            });

            arrowAnimator.setInterpolator(new OvershootInterpolator());
            if (animateIncreaseFinal) {
                ValueAnimator valueAnimator1 = ValueAnimator.ofFloat(0, 1f);
                valueAnimator1.addUpdateListener(animation -> {
                    float p = (float) animation.getAnimatedValue();
                    float k = 0.5f;
                    float angle = -7;
                    limitIconRotation = p < k ? p / k * angle : angle * (1f - (p - k) / (1f - k));
                });
                valueAnimator1.setDuration(500);
                valueAnimator1.start();
                arrowAnimator.setDuration(600);
            } else {
                arrowAnimator.setDuration(1000);
                arrowAnimator.setStartDelay(200);
            }
            arrowAnimator.start();

            wasAnimation = true;
        } else if (isBoostsStyle) {
            limitIcon.setAlpha(1f);
            limitIcon.setScaleX(1f);
            limitIcon.setScaleY(1f);
        } else if (premiumLocked) {
            int padding = dp(14);
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
        } else if (limitIcon != null) {
            limitIcon.setAlpha(0);
        }
    }

    public void setType(int type) {
        if (type == LimitReachedBottomSheet.TYPE_LARGE_FILE) {
            if (limitIcon != null) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                spannableStringBuilder.append("d ").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
                spannableStringBuilder.append(UserConfig.getInstance(UserConfig.selectedAccount).isPremium() ? "4 GB" : "2 GB");
                limitIcon.setText(spannableStringBuilder, false);
            }
            premiumCount.setText("4 GB");
        } else if (type == LimitReachedBottomSheet.TYPE_ADD_MEMBERS_RESTRICTED) {
            if (limitIcon != null) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                spannableStringBuilder.append("d").setSpan(new ColoredImageSpan(icon), 0, 1, 0);
                limitIcon.setText(spannableStringBuilder, false);
            }
            premiumCount.setText("");
        }
    }

    public void setBagePosition(float position) {
        this.position = MathUtils.clamp(position, 0.1f, 0.9f);
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
        if (limitIcon != null) {
            limitIcon.setPadding(dp(24), dp(3), dp(24), dp(3));
        }
        premiumLocked = true;
    }

    public void setBoosts(TL_stories.TL_premium_boostsStatus boosts, boolean boosted) {
        int k = boosts.current_level_boosts;
        boolean isZeroLevelBoosts = boosts.current_level_boosts == boosts.boosts;
        if ((isZeroLevelBoosts && boosted) || boosts.next_level_boosts == 0) {
            percent = 1f;
            defaultText.setText(LocaleController.formatString("BoostsLevel", R.string.BoostsLevel, boosts.level - 1));
            premiumCount.setText(LocaleController.formatString("BoostsLevel", R.string.BoostsLevel, boosts.level));
        } else {
            percent = MathUtils.clamp((boosts.boosts - k) / (float) (boosts.next_level_boosts - k), 0, 1f);
            defaultText.setText(LocaleController.formatString("BoostsLevel", R.string.BoostsLevel, boosts.level));
            premiumCount.setText(LocaleController.formatString("BoostsLevel", R.string.BoostsLevel, boosts.level + 1));
        }
        ((FrameLayout.LayoutParams) premiumCount.getLayoutParams()).gravity = Gravity.RIGHT;
        setType(LimitReachedBottomSheet.TYPE_BOOSTS);
        defaultCount.setVisibility(View.GONE);
        premiumText.setVisibility(View.GONE);

        premiumCount.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        defaultText.setTextColor(Color.WHITE);

        setIconValue(boosts.boosts, false);
        isBoostsStyle = true;
    }

    @Keep
    public void setStatus(int currentLevel, int maxLevel, boolean animated) {
        if (currentValue == currentLevel) {
            animated = false;
        }
        currentValue = currentLevel;
        percent = MathUtils.clamp(currentLevel / (float) maxLevel, 0, 1f);
        if (animated) {
            animateIncrease = true;
            animateIncreaseWidth = width1;
            limitsContainer.requestLayout();
            requestLayout();
        }
        ((FrameLayout.LayoutParams) premiumCount.getLayoutParams()).gravity = Gravity.RIGHT;
        defaultCount.setVisibility(View.GONE);
        premiumText.setVisibility(View.GONE);

        defaultText.setText("0");
        premiumCount.setText("" + maxLevel);

        setIconValue(currentLevel, false);
        isBoostsStyle = true;
        isSimpleStyle = true;
    }

    public void increaseCurrentValue(int boosts, int value, int maxValue) {
        currentValue++;
        percent = MathUtils.clamp(value / (float) maxValue, 0f, 1f);
        animateIncrease = true;
        animateIncreaseWidth = width1;
        setIconValue(boosts, true);
        limitsContainer.requestLayout();
        requestLayout();
    }

    private class TextViewHolder extends FrameLayout {

        private final Paint paint = new Paint();
        private final boolean isLeft;

        public TextViewHolder(@NonNull Context context, boolean isLeft) {
            super(context);
            setLayerType(LAYER_TYPE_HARDWARE, null);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            this.isLeft = isLeft;
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child instanceof TextView) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                boolean leftGradient = percent != 0 && percent <= 1f && isLeft;
                boolean rightGradient = percent == 1f && !isLeft;
                if ((leftGradient || rightGradient) && hasDarkGradientProvider()) {
                    canvas.saveLayer(child.getLeft(), child.getTop(), child.getRight(), child.getBottom(), paint, ALL_SAVE_FLAG);
                    Paint p = darkGradientProvider.setDarkGradientLocation((((ViewGroup) getParent()).getX() + getX()), (((ViewGroup) getParent()).getY() + getY()));
                    canvas.drawRect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom(), p);
                    canvas.restore();
                    invalidate();
                }
                return result;
            }
            return super.drawChild(canvas, child, drawingTime);
        }
    }

    private class CounterView extends View {

        Path path = new Path();
        PathEffect pathEffect = new CornerPathEffect(dp(6));
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        StaticLayout textLayout;
        float textWidth;
        CharSequence text;

        ArrayList<AnimatedLayout> animatedLayouts = new ArrayList<AnimatedLayout>();
        StaticLayout animatedStableLayout;
        boolean animationInProgress;

        float arrowCenter;
        boolean invalidatePath;
        Paint dstOutPaint = new Paint();
        Paint overlayPaint = new Paint();

        public CounterView(Context context) {
            super(context);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(dp(22));
            textPaint.setColor(Color.WHITE);
            dstOutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            overlayPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            textWidth = textPaint.measureText(text, 0, text.length());
            textLayout = new StaticLayout(text, textPaint, (int) textWidth + dp(12), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            setMeasuredDimension((int) (textWidth + getPaddingRight() + getPaddingLeft()), dp(44) + dp(8));
            updatePath();
        }

        private void updatePath() {
            int h = getMeasuredHeight() - dp(8);
            float widthHalf = getMeasuredWidth() * arrowCenter;
            float x2 = Utilities.clamp(widthHalf + dp(8), getMeasuredWidth(), 0);
            float x3 = Utilities.clamp(widthHalf + dp(10), getMeasuredWidth(), dp(24));
            float x4 = Utilities.clamp(widthHalf - dp(arrowCenter < 0.7f ? 10 : 24), getMeasuredWidth(), 0);
            float x5 = Utilities.clamp(widthHalf - dp(8), getMeasuredWidth(), 0);

            path.rewind();
            path.moveTo(x4, h - h / 2f - dp(2));
            path.lineTo(x4, h);
            path.lineTo(x5, h);
            path.lineTo(widthHalf, h + dp(8));
            if (arrowCenter < 0.7f) {
                path.lineTo(x2, h);
            }
            path.lineTo(x3, h);
            path.lineTo(x3, h - h / 2f - dp(2));
            path.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int h = getMeasuredHeight() - dp(8);
            if (premiumLocked) {
                h = getMeasuredHeight();
                PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, LimitPreviewView.this.getMeasuredWidth(), LimitPreviewView.this.getMeasuredHeight(), getGlobalXOffset() - getX(), -getTop());
                AndroidUtilities.rectTmp.set(0, dp(3), getMeasuredWidth(), h - dp(3));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, PremiumGradient.getInstance().getPremiumLocakedPaint());
            } else {
                if (invalidatePath) {
                    invalidatePath = false;
                    updatePath();
                }
                PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, LimitPreviewView.this.getMeasuredWidth(), LimitPreviewView.this.getMeasuredHeight(), getGlobalXOffset() - getX(), -getTop());
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), h);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, h / 2f, h / 2f, hasDarkGradientProvider() ? textPaint : PremiumGradient.getInstance().getMainGradientPaint());
                PremiumGradient.getInstance().getMainGradientPaint().setPathEffect(pathEffect);
                if (hasDarkGradientProvider()) {
                    textPaint.setPathEffect(pathEffect);
                }
                canvas.drawPath(path, hasDarkGradientProvider() ? textPaint : PremiumGradient.getInstance().getMainGradientPaint());
                PremiumGradient.getInstance().getMainGradientPaint().setPathEffect(null);
                if (hasDarkGradientProvider()) {
                    textPaint.setPathEffect(null);
                }
                if (invalidationEnabled) {
                    invalidate();
                }
            }

            if (hasDarkGradientProvider()) {
                canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), dstOutPaint, ALL_SAVE_FLAG);
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
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - dp(8));
                if (animatedStableLayout != null) {
                    canvas.save();
                    canvas.translate(x, y);
                    animatedStableLayout.draw(canvas);
                    canvas.restore();
                }
                for (int i = 0; i < animatedLayouts.size(); i++) {
                    AnimatedLayout animatedLayout = animatedLayouts.get(i);
                    canvas.save();
                    if (animatedLayout.replace) {
                        canvas.translate(x + animatedLayout.x, y + h * (animatedLayout.progress) - h * (1 - animatedLayout.staticLayouts.size()));
                        for (int j = 0; j < animatedLayout.staticLayouts.size(); j++) {
                            canvas.translate(0, -h);
                            animatedLayout.staticLayouts.get(j).draw(canvas);
                        }
                    } else if (animatedLayout.direction) {
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

            if (hasDarkGradientProvider()) {
                canvas.restore();
                canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), overlayPaint, ALL_SAVE_FLAG);
                Paint p = darkGradientProvider.setDarkGradientLocation(getX(), getY());
                canvas.drawRect(dp(12), dp(10), getMeasuredWidth() - dp(12), getMeasuredHeight() - dp(10), p);
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
            if (isBoostsStyle && currentValue == 0) {
                return;
            }
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
            animatedStableLayout = new StaticLayout(spannableStringBuilder, textPaint, (int) textWidth + dp(12), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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


        void createAnimationLayoutsDiff(CharSequence oldText) {
            if (textLayout == null) {
                return;
            }
            animatedLayouts.clear();
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
            int directionCount = 0;
            for (int i = text.length() - 1; i >= 0; i--) {
                char oldChar = i < oldText.length() ? oldText.charAt(i) : ' ';
                if (oldChar != text.charAt(i) && Character.isDigit(text.charAt(i))) {
                    AnimatedLayout animatedLayout = new AnimatedLayout();
                    animatedLayouts.add(animatedLayout);
                    animatedLayout.x = textLayout.getSecondaryHorizontal(i);
                    animatedLayout.replace = true;
                    if (directionCount >= 1) {
                        directionCount = 0;
                    }
                    directionCount++;

                    StaticLayout staticLayoutOld = new StaticLayout("" + oldChar, textPaint, (int) textWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    animatedLayout.staticLayouts.add(staticLayoutOld);

                    StaticLayout staticLayout = new StaticLayout("" + text.charAt(i), textPaint, (int) textWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    animatedLayout.staticLayouts.add(staticLayout);
                    spannableStringBuilder.setSpan(new EmptyStubSpan(), i, i + 1, 0);
                }
            }
            animatedStableLayout = new StaticLayout(spannableStringBuilder, textPaint, (int) textWidth + dp(12), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
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
                layout.valueAnimator.setDuration(250);
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

        public void setText(CharSequence text, boolean animated) {
            if (!animated) {
                this.text = text;
            } else {
                CharSequence oldText = this.text;
                this.text = text;
                createAnimationLayoutsDiff(oldText);
            }
        }

        public void setArrowCenter(float v) {
            if (arrowCenter != v) {
                arrowCenter = v;
                invalidatePath = true;
                invalidate();
            }
        }

        public float getArrowCenter() {
            return arrowCenter;
        }

        private class AnimatedLayout {
            public boolean replace;
            ArrayList<StaticLayout> staticLayouts = new ArrayList<>();
            float progress;
            public boolean direction;
            float x;
            ValueAnimator valueAnimator;
        }
    }
}
