package org.telegram.ui.Components.Paint.Views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Reactions.ReactionImageHolder;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.Components.Size;
import org.telegram.ui.Stories.StoryReactionWidgetBackground;

import java.util.List;
import java.util.Objects;

public class ReactionWidgetEntityView extends EntityView {

    Size baseSize;
    StoryReactionWidgetBackground storyReactionWidgetBackground = new StoryReactionWidgetBackground(this);
    StoryReactionWidgetBackground outBackground = new StoryReactionWidgetBackground(this);
    ReactionImageHolder reactionHolder = new ReactionImageHolder(this);
    ReactionImageHolder nextReactionHolder = new ReactionImageHolder(this);
    ReactionsLayoutInBubble.VisibleReaction currentReaction;
    AnimatedFloat progressToNext = new AnimatedFloat(this);
    AnimatedFloat crossfadeBackgrounds = new AnimatedFloat(this);
    boolean mirror;
    private float drawScale = 1f;

    int filterColor;

    public ReactionWidgetEntityView(Context context, Point pos, Size baseSize) {
        super(context, pos);
        this.baseSize = baseSize;

        crossfadeBackgrounds.set(1f, true);
        progressToNext.set(1f, true);
        List<TLRPC.TL_availableReaction> availableReactions = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsList();
        reactionHolder.setVisibleReaction(currentReaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(findHeartReaction(availableReactions)));

        updatePosition();
    }

    private String findHeartReaction(List<TLRPC.TL_availableReaction> availableReactions) {
        for (int i = 0; i < availableReactions.size(); i++) {
            if (availableReactions.get(i).title.equals("Red Heart")) {
                return availableReactions.get(i).reaction;
            }
        }
        return availableReactions.get(0).reaction;
    }

    protected void updatePosition() {
        float halfWidth = baseSize.width / 2.0f;
        float halfHeight = baseSize.height / 2.0f;
        setX(getPositionX() - halfWidth);
        setY(getPositionY() - halfHeight);
        updateSelectionView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec((int) baseSize.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) baseSize.height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int padding = getPadding();
        float crossfade = crossfadeBackgrounds.set(1f);
        if (crossfade == 1f) {
            outBackground = null;
        }
        canvas.save();
        canvas.scale(drawScale, drawScale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
        if (outBackground != null) {
            outBackground.setAlpha((int) (255 * (1f - crossfade)));
            outBackground.setBounds(padding, padding, (int) baseSize.width - padding, (int) baseSize.height - padding);
            outBackground.draw(canvas);
        }
        storyReactionWidgetBackground.setAlpha((int) (255 * crossfade));
        storyReactionWidgetBackground.setBounds(padding, padding, (int) baseSize.width - padding, (int) baseSize.height - padding);
        storyReactionWidgetBackground.draw(canvas);
        float imageSize = storyReactionWidgetBackground.getBounds().width() * 0.61f;
        AndroidUtilities.rectTmp2.set(
                (int) (storyReactionWidgetBackground.getBounds().centerX() - imageSize / 2f),
                (int) (storyReactionWidgetBackground.getBounds().centerY() - imageSize / 2f),
                (int) (storyReactionWidgetBackground.getBounds().centerX() + imageSize / 2f),
                (int) (storyReactionWidgetBackground.getBounds().centerY() + imageSize / 2f)
        );

        float progress = progressToNext.set(1);
        reactionHolder.setBounds(AndroidUtilities.rectTmp2);
        nextReactionHolder.setBounds(AndroidUtilities.rectTmp2);
        reactionHolder.setColor(storyReactionWidgetBackground.isDarkStyle() ? Color.WHITE : Color.BLACK);

        if (progress == 1) {
            reactionHolder.draw(canvas);
        } else {
            canvas.save();
            canvas.scale(1f - progress, 1f - progress, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.top);
            nextReactionHolder.setAlpha(1f - progress);
            nextReactionHolder.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.scale(progress, progress, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.bottom);
            reactionHolder.setAlpha(progress);
            reactionHolder.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    public int getPadding() {
        return (int) ((baseSize.height - AndroidUtilities.dp(84)) / 2f);
    }

    @Override
    public Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new Rect();
        }
        float scale = parentView.getScaleX();

        float side = getMeasuredWidth() * (getScale() + 0.4f);
        return new Rect((getPositionX() - side / 2.0f) * scale, (getPositionY() - side / 2.0f) * scale, side * scale, side * scale);
    }

    @Override
    protected SelectionView createSelectionView() {
        return new StickerViewSelectionView(getContext());
    }

    public void setCurrentReaction(ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean animated) {
        if (Objects.equals(currentReaction, visibleReaction)) {
            return;
        }
        if (!animated) {
            currentReaction = visibleReaction;
            reactionHolder.setVisibleReaction(currentReaction);
            invalidate();
        } else {
            currentReaction = visibleReaction;
            nextReactionHolder.setVisibleReaction(currentReaction);
            ReactionImageHolder k = reactionHolder;
            reactionHolder = nextReactionHolder;
            nextReactionHolder = k;
            progressToNext.set(0, true);
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        reactionHolder.onAttachedToWindow(true);
        nextReactionHolder.onAttachedToWindow(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reactionHolder.onAttachedToWindow(false);
        nextReactionHolder.onAttachedToWindow(false);
    }

    public ReactionsLayoutInBubble.VisibleReaction getCurrentReaction() {
        return currentReaction;
    }

    public void mirror(boolean animate) {
        mirror = !mirror;
        if (!animate) {
            storyReactionWidgetBackground.setMirror(mirror, animate);
        } else {
            boolean[] mirrored = new boolean[] {false};
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
            animator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                if (progress < 0.5f) {
                    setRotationY(90 * (progress / 0.5f));
                    drawScale = 0.7f + 0.3f * (1f - (progress / 0.5f));
                    invalidate();
                } else {
                    if (!mirrored[0]) {
                        mirrored[0] = true;
                        storyReactionWidgetBackground.setMirror(mirror, false);
                    }
                    progress -= 0.5f;
                    setRotationY(-90 * (1f - progress / 0.5f));
                    drawScale = 0.7f + 0.3f * (progress / 0.5f);
                    invalidate();
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!mirrored[0]) {
                        mirrored[0] = true;
                        storyReactionWidgetBackground.setMirror(mirror, false);
                    }
                    setRotationY(0);
                    drawScale = 1f;
                }
            });
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            animator.setDuration(350);
            animator.start();
        }
    }

    public void changeStyle(boolean animated) {
        if (!animated) {
            storyReactionWidgetBackground.nextStyle();
        } else {
            outBackground = storyReactionWidgetBackground;
            storyReactionWidgetBackground = new StoryReactionWidgetBackground(this);
            if (!outBackground.isDarkStyle()) {
                storyReactionWidgetBackground.nextStyle();
            }
            storyReactionWidgetBackground.setMirror(mirror, false);
            storyReactionWidgetBackground.updateShadowLayer(getScaleX());
            crossfadeBackgrounds.set(0, true);
        }
        invalidate();
    }

    public boolean isMirrored() {
        return mirror;
    }

    public boolean isDark() {
        return storyReactionWidgetBackground.isDarkStyle();
    }

//    private void animateBounce() {
//        AnimatorSet animatorSet = new AnimatorSet();
//        ValueAnimator inAnimator = ValueAnimator.ofFloat(1, 1.05f);
//        inAnimator.setDuration(100);
//        inAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
//
//        ValueAnimator outAnimator = ValueAnimator.ofFloat(1.05f, 1f);
//        outAnimator.setDuration(250);
//        outAnimator.setInterpolator(new OvershootInterpolator());
//
//        ValueAnimator.AnimatorUpdateListener updater = animation -> {
//            bounceScale = (float) animation.getAnimatedValue();
//            invalidate();
//        };
//        setClipInParent(false);
//        inAnimator.addUpdateListener(updater);
//        outAnimator.addUpdateListener(updater);
//        animatorSet.playSequentially(inAnimator, outAnimator);
//        animatorSet.addListener(new AnimatorListenerAdapter() {
//            @Override
//            public void onAnimationEnd(Animator animation) {
//                bounceScale = 1f;
//                invalidate();
//                setClipInParent(true);
//            }
//        });
//        animatorSet.start();
//
//        if (animationRunnable != null) {
//            AndroidUtilities.cancelRunOnUIThread(animationRunnable);
//            animationRunnable.run();
//            animationRunnable = null;
//        }
//    }

    public class StickerViewSelectionView extends SelectionView {

        private RectF arcRect = new RectF();

        public StickerViewSelectionView(Context context) {
            super(context);
        }

        @Override
        protected int pointInsideHandle(float x, float y) {
            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dp(19.5f);

            float inset = radius + thickness;
            float middle = inset + (getMeasuredHeight() - inset * 2) / 2.0f;

            if (x > inset - radius && y > middle - radius && x < inset + radius && y < middle + radius) {
                return SELECTION_LEFT_HANDLE;
            } else if (x > inset + (getMeasuredWidth() - inset * 2) - radius && y > middle - radius && x < inset + (getMeasuredWidth() - inset * 2) + radius && y < middle + radius) {
                return SELECTION_RIGHT_HANDLE;
            }

            float selectionRadius = getMeasuredWidth() / 2.0f;

            if (Math.pow(x - selectionRadius, 2) + Math.pow(y - selectionRadius, 2) < Math.pow(selectionRadius, 2)) {
                return SELECTION_WHOLE_HANDLE;
            }

            return 0;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int count = canvas.getSaveCount();

            float alpha = getShowAlpha();
            if (alpha <= 0) {
                return;
            } else if (alpha < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            }

            float thickness = AndroidUtilities.dp(1.0f);
            float radius = AndroidUtilities.dpf2(5.66f);

            float inset = radius + thickness + AndroidUtilities.dp(15);
            float mainRadius = getMeasuredWidth() / 2 - inset;

            arcRect.set(inset, inset, inset + mainRadius * 2, inset + mainRadius * 2);
            canvas.drawArc(arcRect, 0, 180, false, paint);
            canvas.drawArc(arcRect, 180, 180, false, paint);

            canvas.drawCircle(inset, inset + mainRadius, radius, dotStrokePaint);
            canvas.drawCircle(inset, inset + mainRadius, radius - AndroidUtilities.dp(1), dotPaint);

            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius, dotStrokePaint);
            canvas.drawCircle(inset + mainRadius * 2, inset + mainRadius, radius - AndroidUtilities.dp(1), dotPaint);

            canvas.restoreToCount(count);
        }
    }

    @Override
    public boolean allowLongPressOnSelected() {
        return true;
    }

    @Override
    public void setScaleX(float scaleX) {
        if (getScaleX() != scaleX) {
            super.setScaleX(scaleX);
            storyReactionWidgetBackground.updateShadowLayer(scaleX);
            invalidate();
        }
    }

    @Override
    protected float getMaxScale() {
        return 1.8f;
    }

    @Override
    protected float getMinScale() {
        return 0.5f;
    }

    @Override
    protected boolean allowHaptic() {
        return false;
    }
}
