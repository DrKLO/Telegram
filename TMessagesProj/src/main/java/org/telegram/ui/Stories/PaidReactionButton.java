package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_COLOR1;
import static org.telegram.ui.Stories.HighlightMessageSheet.getTierOption;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.blur3.StrokeDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;

import java.util.ArrayList;

public class PaidReactionButton extends View {

    public static class PaidReactionButtonEffectsView extends View {

        public final int currentAccount;
        public final RectF reactionBounds = new RectF();

        private boolean counterShown;
        private final AnimatedFloat counterAlpha = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedTextView.AnimatedTextDrawable counter = new AnimatedTextView.AnimatedTextDrawable();

        private Runnable hideCounterRunnable;

        private final ArrayList<RLottieDrawable> effects = new ArrayList<>();
        private final int[] effectAssets = new int[] {
                R.raw.star_reaction_effect1,
                R.raw.star_reaction_effect2,
                R.raw.star_reaction_effect3,
                R.raw.star_reaction_effect4,
                R.raw.star_reaction_effect5
        };

        private final ArrayList<Chip> chips = new ArrayList<>();

        private float focus;
        private ValueAnimator focusAnimator;
        public void focusTo(float dst, Runnable whenDone) {
            if (focusAnimator != null) {
                ValueAnimator anm = focusAnimator;
                focusAnimator = null;
                anm.cancel();
            }
            focusAnimator = ValueAnimator.ofFloat(focus, dst);
            focusAnimator.addUpdateListener(anm -> {
                focus = (float) anm.getAnimatedValue();
                invalidate();
            });
            focusAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    focus = dst;
                    invalidate();
                    if (animation == focusAnimator && whenDone != null) {
                        whenDone.run();
                    }
                }
            });
            focusAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            focusAnimator.setDuration(320);
            focusAnimator.start();
        }

        public void showCounter(long stars) {
            counter.cancelAnimation();
            counter.setText("+" + LocaleController.formatNumber(stars, ','));
            counterShown = true;
            AndroidUtilities.cancelRunOnUIThread(hideCounterRunnable);
            AndroidUtilities.runOnUIThread(hideCounterRunnable, 1500);
        }

        public void show() {
            hidden = false;
            focusTo(1f, null);
        }

        public PaidReactionButtonEffectsView(Context context, int currentAccount) {
            super(context);
            this.currentAccount = currentAccount;

            counter.setCallback(this);
            counter.setHacks(false, true, true);
            counter.setTextSize(dp(40));
            counter.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            counter.setShadowLayer(dp(12), 0, dp(3.5f), 0x00000000);
            counter.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            counter.setTextColor(0xFFFFFFFF);
            counter.setGravity(Gravity.CENTER);

            hideCounterRunnable = () -> {
                counterShown = false;
                invalidate();
                hide();
            };
        }

        public void updatePosition(PaidReactionButton btn) {
            reactionBounds.set(
                btn.getX() - getX(),
                btn.getY() - getY(),
                btn.getX() - getX() + btn.getWidth(),
                btn.getY() - getY() + btn.getHeight()
            );
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            final float s = AndroidUtilities.lerp(1, 1.8f, focus);
            final int effectSize = (int) (dp(90) * s);
            for (int i = 0; i < effects.size(); ++i) {
                RLottieDrawable drawable = effects.get(i);
                if (drawable.getCurrentFrame() >= drawable.getFramesCount()) {
                    effects.remove(i);
                    i--;
                    continue;
                }

                drawable.setBounds(
                    (int) (reactionBounds.left + dp(4 + 11) * s - effectSize / 2f),
                    (int) (reactionBounds.centerY() - effectSize / 2f),
                    (int) (reactionBounds.left + dp(4 + 11) * s + effectSize / 2f),
                    (int) (reactionBounds.centerY() + effectSize / 2f)
                );
                drawable.setAlpha((int) (0xFF * focus));
                drawable.draw(canvas);
            }

            final float cx = reactionBounds.centerX();
            final float cy = reactionBounds.top - dp(1);
//            canvas.save();
//            float t = counterAlpha.set(counterShown);
//            canvas.translate(0, counterShown ? dp(60) * (1f - t) : -dp(30) * (1f - t));
//            final float counterScale = AndroidUtilities.lerp(counterShown ? 1.8f : 1.3f, 1f, t);
//            canvas.scale(counterScale, counterScale, cx, cy);
//            counter.setAlpha((int) (0xFF * t));
//            counter.setShadowLayer(dp(12), 0, dp(3.5f), Theme.multAlpha(0xAA000000, t));
//            counter.setBounds(cx - dp(100), reactionBounds.top - dp(24 + 24), cx + dp(100), reactionBounds.top - dp(24));
//            counter.draw(canvas);
//            canvas.restore();

            canvas.save();
            canvas.translate(cx, cy);
            for (int i = 0; i < chips.size(); ++i) {
                if (chips.get(i).draw(canvas)) {
                    chips.get(i).detach();
                    chips.remove(i);
                    i--;
                }
            }
            canvas.restore();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == counter || super.verifyDrawable(who);
        }

        public void playEffect() {
            while (effects.size() > 4) {
                RLottieDrawable drawable = effects.remove(0);
                drawable.recycle(true);
            }
            final int asset = effectAssets[Utilities.fastRandom.nextInt(effectAssets.length)];
            RLottieDrawable drawable = new RLottieDrawable(asset, "" + asset, dp(70), dp(70));
            drawable.setMasterParent(this);
            drawable.setAllowDecodeSingleFrame(true);
            drawable.setAutoRepeat(0);
            drawable.start();
            effects.add(drawable);
            invalidate();
        }

        public void clearEffects() {
            for (RLottieDrawable effect : effects) {
                effect.recycle(true);
            }
            effects.clear();
        }

        public boolean hidden = true;
        public void hide() {
            hidden = true;
            AndroidUtilities.cancelRunOnUIThread(hideCounterRunnable);
            counter.setText("");
            counterShown = false;
            invalidate();
            focusTo(0f, () -> {
                clearEffects();
            });
        }

        public class Chip {

            public final long dialogId;
            public final int stars;
            private final float randomTranslation;
            private final float randomRotation;

            @Nullable
            private RLottieDrawable effect;

            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            private final AvatarDrawable avatarDrawable;
            private final ImageReceiver imageReceiver;
            private final Text text;

            private boolean isKilled;
            public final AnimatedFloat progress;
            public final AnimatedFloat killProgress;

            public Chip(View view, int currentAccount, long dialogId, int stars, int totalStars, boolean withEffect) {
                this.dialogId = dialogId;
                this.stars = stars;
                this.randomTranslation = Utilities.clamp01(Utilities.fastRandom.nextFloat());
                this.randomRotation = Utilities.clamp01(Utilities.fastRandom.nextFloat());

                if (withEffect) {
                    final int asset = effectAssets[Utilities.fastRandom.nextInt(effectAssets.length)];
                    effect = new RLottieDrawable(asset, "" + asset, dp(70), dp(70));
                    effect.setMasterParent(view);
                    effect.setAllowDecodeSingleFrame(true);
                    effect.setAutoRepeat(0);
                    effect.start();
                }

                final TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
                avatarDrawable = new AvatarDrawable();
                avatarDrawable.setInfo(object);
                imageReceiver = new ImageReceiver(view);
                imageReceiver.setImageCoords(dp(2), dp(2), dp(14), dp(14));
                imageReceiver.setRoundRadius(dp(7));
                imageReceiver.setForUserOrChat(object, avatarDrawable);
                view.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(@NonNull View view) {
                        imageReceiver.onAttachedToWindow();
                    }

                    @Override
                    public void onViewDetachedFromWindow(@NonNull View view) {
                        imageReceiver.onDetachedFromWindow();
                    }
                });
                if (view.isAttachedToWindow()) {
                    imageReceiver.onAttachedToWindow();
                }

//                backgroundPaint.setColor(getTierOption(totalStars, TIER_COLOR1));
                backgroundPaint.setColor(0xFFEEAC0D);

                final SpannableStringBuilder sb = new SpannableStringBuilder("⭐️");
                final ColoredImageSpan span = new ColoredImageSpan(R.drawable.star);
                span.spaceScaleX = 0.875f;
                sb.setSpan(span, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(" ");
                sb.append(LocaleController.formatNumber(stars, ','));
                text = new Text(sb, 10, AndroidUtilities.getTypeface("fonts/num.otf"));

                progress = new AnimatedFloat(view, 2000, new LinearInterpolator());
                progress.force(0.0f);
                progress.set(1.0f);

                killProgress = new AnimatedFloat(view, 350, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
            }

            public boolean draw(Canvas canvas) {
                final float progress = this.progress.set(1.0f);
                final float killProgress = this.killProgress.set(isKilled);
                final float w = dp(2 + 14 + 2 + 5) + text.getCurrentWidth();
                final float h = dp(18);

                final float alpha = lerp(
                    0f,
                    lerp(1f, 0f, killProgress),
                    Utilities.clamp01(Math.min(
                        ilerp(progress, 1.0f, 0.85f),
                        ilerp(progress, 0f, 0.12f)
                    ))
                );
                backgroundPaint.setAlpha((int) (0xFF * alpha));
                if (effect != null) {
                    effect.setAlpha((int) (0xFF * alpha));
                }
                imageReceiver.setAlpha(alpha);

                canvas.save();
                final float wave = (float) Math.sin(Math.pow(progress, 0.45f) * Math.PI * 3);
                canvas.translate(dp(4) * (2f * randomTranslation - 1f), 0);
                canvas.rotate(1.5f * (2f * randomRotation - 1f));
                canvas.translate(0, -dp(200) * (float) Math.pow(progress, 0.8f));
                canvas.translate(wave * dp(5) * (float) Math.pow(progress, 0.5f), 0);
                canvas.rotate(-6f * (float) (Math.sin((Math.pow(progress, 0.45f) - 0.15f) * Math.PI * 3) * Utilities.clamp01((float) Math.pow(progress, .2f))));
                final float scale = lerp(0.4f, 1.0f, alpha);
                canvas.scale(scale, scale);
                canvas.translate(-w / 2f, -h / 2f);

                canvas.drawRoundRect(0, 0, w, h, h / 2, h / 2, backgroundPaint);
                imageReceiver.draw(canvas);

                text.draw(canvas, dp(2 + 14 + 2), h / 2, 0xFFFFFFFF, alpha);
                canvas.restore();

                if (effect != null) {
                    canvas.save();
                    canvas.translate(dp(4) * (2f * randomTranslation - 1f), 0);
                    canvas.rotate(1.5f * (2f * randomRotation - 1f));
                    canvas.translate(0, -dp(200) * (float) Math.pow(progress, 0.8f));
                    canvas.translate(wave * dp(5) * (float) Math.pow(progress, 0.5f), 0);
                    final int effectSize = dp(90);
                    effect.setBounds(-effectSize / 2, -effectSize / 2 + dp(8), effectSize / 2, effectSize / 2 + dp(8));
                    effect.draw(canvas);
                    canvas.restore();
                }

                return progress >= 1.0f || killProgress >= 1.0f;
            }

            public void detach() {
                imageReceiver.onDetachedFromWindow();
            }

            public void kill() {
                isKilled = true;
            }

        }

        public void pushChip(long dialogId, int totalStars, int stars) {
            final Chip chip = new Chip(this, currentAccount, dialogId, stars, totalStars, chips.size() < 5);
            chips.add(chip);
            invalidate();
        }

        public void removeChipsFrom(long dialogId) {
            for (int i = 0; i < chips.size(); ++i) {
                if (chips.get(i).dialogId == dialogId) {
                    chips.get(i).kill();
                }
            }
        }

    }

    private final PaidReactionButtonEffectsView effectsView;

    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private final StarsReactionsSheet.Particles particles;
    private final ColoredImageSpan span;
    private final AnimatedFloat animatedFilled = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat animatedShowCounter = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    private final AnimatedTextView.AnimatedTextDrawable countText;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Drawable iconDrawable;
    private final StrokeDrawable strokeDrawable;

    public PaidReactionButton(Context context, PaidReactionButtonEffectsView effectsView, BlurredBackgroundColorProvider colorProvider) {
        super(context);
        this.effectsView = effectsView;

        ScaleStateListAnimator.apply(this);

        iconDrawable = context.getResources().getDrawable(R.drawable.star).mutate();

        strokeDrawable = new StrokeDrawable();
        strokeDrawable.setColorProvider(colorProvider);

        countText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        countText.setTextColor(0xFF697278);
        countText.setTextSize(dp(9));
        countText.setCallback(this);
        countText.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        countText.setAllowCancel(true);
        backgroundPaint.setColor(0xFF20242A);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        span = new ColoredImageSpan(R.drawable.star);
        span.setScale(1.8f, 1.8f);
        setCount(0);

        particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 50);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float size = dp(38);
        final float filled = animatedFilled.set(this.filled);
        final float showCounter = animatedShowCounter.set(stars > 0);

        rect.set(
            (getWidth() - size) / 2f,
            (getHeight() - size) / 2f,
            (getWidth() + size) / 2f,
            (getHeight() + size) / 2f
        );

        final int backgroundColor = ColorUtils.blendARGB(0xFF20242A, 0xFFF7A31D, filled);
        backgroundPaint.setColor(backgroundColor);

        strokeDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
        strokeDrawable.setBackgroundColor(backgroundColor);
        strokeDrawable.draw(canvas);

        final int iconSize = dp(20);
        iconDrawable.setBounds(
            (getWidth() - iconSize) / 2,
            (getHeight() - iconSize) / 2,
            (getWidth() + iconSize) / 2,
            (getHeight() + iconSize) / 2
        );
        iconDrawable.draw(canvas);

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, rect.height() / 2, rect.height() / 2, Path.Direction.CW);
        canvas.clipPath(clipPath);

        particles.setSpeed(lerp(5.0f, 15.0f, filled));
        particles.setBounds(rect);
        particles.process();
        particles.draw(canvas, 0xFFFFFFFF, lerp(0.5f, 1.0f, filled));
        invalidate();
        canvas.restore();

        if (showCounter > 0) {
            final float countTextWidth = Math.max(dp(12), dp(6) + countText.getCurrentWidth());
            final float s = countScale * countText.isNotEmpty() * showCounter;
            canvas.save();
            AndroidUtilities.rectTmp.set(getWidth() - countTextWidth, 0, getWidth(), dp(13));
            canvas.scale(s, s, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
            AndroidUtilities.rectTmp.inset(-dp(2), -dp(2));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.rectTmp.height() / 2, AndroidUtilities.rectTmp.height() / 2, clearPaint);

            AndroidUtilities.rectTmp.set(getWidth() - countTextWidth, 0, getWidth(), dp(13));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.rectTmp.height() / 2, AndroidUtilities.rectTmp.height() / 2, backgroundPaint);
            canvas.translate(AndroidUtilities.rectTmp.left + (countTextWidth - countText.getCurrentWidth()) / 2.0f, dp(6.33f));
            countText.setTextColor(ColorUtils.blendARGB(0xFF697278, 0xFFFFFFFF, filled));
            countText.draw(canvas);
            canvas.restore();
        }
    }

    private int stars;
    private boolean filled;
    public void setCount(int stars) {
        this.stars = stars;
        if (stars > 50_000) {
            countText.setText(AndroidUtilities.formatWholeNumber(stars, 0));
        } else {
            countText.setText(LocaleController.formatNumber(stars, ','));
        }
        invalidate();
        requestLayout();
    }

    public void setFilled(boolean filled) {
        if (this.filled == filled) return;

        this.filled = filled;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        effectsView.updatePosition(this);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return countText == who || super.verifyDrawable(who);
    }

    private long lastRippleTime;
    private float accumulatedRippleIntensity;
    private final int[] pos = new int[2];
    public void playEffect(long sendingStars) {
        effectsView.updatePosition(this);
        if (effectsView.hidden) {
            effectsView.show();
        }
        effectsView.playEffect();
        effectsView.showCounter(sendingStars);

        ripple();
    }

    private void ripple() {
        getLocationInWindow(pos);
        final long now = System.currentTimeMillis();
        if (now - lastRippleTime < 100) {
            accumulatedRippleIntensity += .5f;
        } else {
            accumulatedRippleIntensity *= Utilities.clamp(1f - (now - lastRippleTime - 100) / 200f, 1f, 0f);
            LaunchActivity.makeRipple(pos[0] + getWidth() / 2.0f, pos[1] + getHeight() / 2.0f, Utilities.clamp(accumulatedRippleIntensity, 0.9f, 0.3f));
            accumulatedRippleIntensity = 0;
            lastRippleTime = now;
        }
    }

    public void stopEffects() {
        effectsView.updatePosition(this);
        effectsView.hide();
    }

    private float countScale = 1;
    private ValueAnimator countAnimator;
    private void animateBounce() {
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
        countAnimator.setInterpolator(new OvershootInterpolator(2.5f));
        countAnimator.setDuration(200);
        countAnimator.start();
    }
}
