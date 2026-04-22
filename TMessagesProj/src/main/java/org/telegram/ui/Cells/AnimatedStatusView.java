package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Reactions.AnimatedEmojiEffect;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;

public class AnimatedStatusView extends View {
    private int stateSize;
    private int effectsSize;
    private int renderedEffectsSize;

    private int animationUniq;
    private ArrayList<Object> animations = new ArrayList<>();

    public AnimatedStatusView(Context context, int stateSize, int effectsSize) {
        super(context);
        this.stateSize = stateSize;
        this.effectsSize = effectsSize;
        this.renderedEffectsSize = effectsSize;
    }

    public AnimatedStatusView(Context context, int stateSize, int effectsSize, int renderedEffectsSize) {
        super(context);
        this.stateSize = stateSize;
        this.effectsSize = effectsSize;
        this.renderedEffectsSize = renderedEffectsSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(Math.max(renderedEffectsSize, Math.max(stateSize, effectsSize))), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(Math.max(renderedEffectsSize, Math.max(stateSize, effectsSize))), MeasureSpec.EXACTLY)
        );
    }

    private float y1, y2;

    public void translate(float x, float y) {
        setTranslationX(x - getMeasuredWidth() / 2f);
        setTranslationY((this.y1 = y - getMeasuredHeight() / 2f) + this.y2);
    }

    public void translateY2(float y) {
        setTranslationY(this.y1 + (this.y2 = y));
    }

    @Override
    public void dispatchDraw(@NonNull Canvas canvas) {
        final int renderedEffectsSize = AndroidUtilities.dp(this.renderedEffectsSize);
        final int effectsSize = AndroidUtilities.dp(this.effectsSize);
        for (int i = 0; i < animations.size(); ++i) {
            Object animation = animations.get(i);
            if (animation instanceof ImageReceiver) {
                ImageReceiver imageReceiver = (ImageReceiver) animation;
                imageReceiver.setImageCoords(
                        (getMeasuredWidth() - effectsSize) / 2f,
                        (getMeasuredHeight() - effectsSize) / 2f,
                        effectsSize,
                        effectsSize
                );
                imageReceiver.draw(canvas);
//                    if (imageReceiver.getLottieAnimation() != null && imageReceiver.getLottieAnimation().isRunning() && imageReceiver.getLottieAnimation().isLastFrame()) {
//                        imageReceiver.onDetachedFromWindow();
//                        animations.remove(imageReceiver);
//                    }
            } else if (animation instanceof AnimatedEmojiEffect) {
                AnimatedEmojiEffect effect = (AnimatedEmojiEffect) animation;
                effect.setBounds(
                        (int) ((getMeasuredWidth() - renderedEffectsSize) / 2f),
                        (int) ((getMeasuredHeight() - renderedEffectsSize) / 2f),
                        (int) ((getMeasuredWidth() + renderedEffectsSize) / 2f),
                        (int) ((getMeasuredHeight() + renderedEffectsSize) / 2f)
                );
                effect.draw(canvas);
                if (effect.isDone()) {
                    effect.removeView(this);
                    animations.remove(effect);
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detach();
    }

    private void detach() {
        if (!animations.isEmpty()) {
            for (Object obj : animations) {
                if (obj instanceof ImageReceiver) {
                    ((ImageReceiver) obj).onDetachedFromWindow();
                } else if (obj instanceof AnimatedEmojiEffect) {
                    ((AnimatedEmojiEffect) obj).removeView(this);
                }
            }
        }
        animations.clear();
    }

    public void animateChange(ReactionsLayoutInBubble.VisibleReaction react) {
        if (react == null) {
            detach();
            return;
        }

        TLRPC.Document document = null;
        TLRPC.TL_availableReaction r = null;
        if (react.emojicon != null) {
            r = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(react.emojicon);
        }
        if (r == null) {
            document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, react.documentId);
            if (document != null) {
                String emojicon = MessageObject.findAnimatedEmojiEmoticon(document, null);
                if (emojicon != null) {
                    r = MediaDataController.getInstance(UserConfig.selectedAccount).getReactionsMap().get(emojicon);
                }
            }
        }
        if (document == null && r != null) {
            ImageReceiver imageReceiver = new ImageReceiver();
            imageReceiver.setParentView(this);
            imageReceiver.setUniqKeyPrefix(Integer.toString(animationUniq++));
            imageReceiver.setImage(ImageLocation.getForDocument(r.around_animation), effectsSize + "_" + effectsSize + "_nolimit", null, "tgs", r, 1);
            imageReceiver.setAutoRepeat(0);
            imageReceiver.onAttachedToWindow();
            animations.add(imageReceiver);
            invalidate();
        } else {
            AnimatedEmojiDrawable drawable;
            if (document == null) {
                drawable = AnimatedEmojiDrawable.make(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, UserConfig.selectedAccount, react.documentId);
            } else {
                drawable = AnimatedEmojiDrawable.make(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, UserConfig.selectedAccount, document);
            }
            if (color != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }
            AnimatedEmojiEffect effect = AnimatedEmojiEffect.createFrom(drawable, false, !drawable.canOverrideColor());
            effect.setView(this);
            animations.add(effect);
            invalidate();
        }
    }

    private Integer color;

    public void setColor(int color) {
        this.color = color;
        final ColorFilter colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        final ColorFilter colorFilterEmoji = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
        for (int i = 0; i < animations.size(); ++i) {
            Object animation = animations.get(i);
            if (animation instanceof ImageReceiver) {
                ((ImageReceiver) animation).setColorFilter(colorFilter);
            } else if (animation instanceof AnimatedEmojiEffect) {
                ((AnimatedEmojiEffect) animation).animatedEmojiDrawable.setColorFilter(colorFilterEmoji);
            }
        }
    }
}
