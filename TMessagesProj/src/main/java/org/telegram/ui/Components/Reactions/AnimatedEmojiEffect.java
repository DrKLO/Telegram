package org.telegram.ui.Components.Reactions;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;

public class AnimatedEmojiEffect {

    public AnimatedEmojiDrawable animatedEmojiDrawable;
    Rect bounds = new Rect();

    ArrayList<Particle> particles = new ArrayList<>();
    View parentView;

    long startTime;
    boolean longAnimation;
    boolean firsDraw = true;
    int currentAccount;
    boolean showGeneric;

    ImageReceiver effectImageReceiver;
    int animationIndex = -1;

    private AnimatedEmojiEffect(AnimatedEmojiDrawable animatedEmojiDrawable, int currentAccount, boolean longAnimation, boolean showGeneric) {
        this.animatedEmojiDrawable = animatedEmojiDrawable;
        this.longAnimation = longAnimation;
        this.currentAccount = currentAccount;
        this.showGeneric = showGeneric;
        startTime = System.currentTimeMillis();
        if (!longAnimation && showGeneric && LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_CHAT)) {
            effectImageReceiver = new ImageReceiver();
        }
    }

    public static AnimatedEmojiEffect createFrom(AnimatedEmojiDrawable animatedEmojiDrawable, boolean longAnimation, boolean showGeneric) {
        AnimatedEmojiEffect emojiEffect = new AnimatedEmojiEffect(animatedEmojiDrawable, UserConfig.selectedAccount, longAnimation, showGeneric);
        return emojiEffect;
    }

    public void setBounds(int l, int t, int r, int b) {
        bounds.set(l, t, r, b);
        if (effectImageReceiver != null) {
            effectImageReceiver.setImageCoords(bounds);
        }
    }

    long lastGenerateTime;

    public void draw(Canvas canvas) {
        if (!longAnimation) {
            if (firsDraw) {
                for (int i = 0; i < 7; i++) {
                    Particle particle = new Particle();
                    particle.generate();
                    particles.add(particle);
                }
            }
        } else {
            long currentTime = System.currentTimeMillis();
            if (particles.size() < 12 && (currentTime - startTime < 1500) && currentTime - startTime > 200) {
                if (currentTime - lastGenerateTime > 50 && Utilities.fastRandom.nextInt() % 6 == 0) {
                    Particle particle = new Particle();
                    particle.generate();
                    particles.add(particle);
                    lastGenerateTime = currentTime;
                }
            }
        }
        if (effectImageReceiver != null && showGeneric) {
            effectImageReceiver.draw(canvas);
        }


        for (int i = 0; i < particles.size(); i++) {
            particles.get(i).draw(canvas);
            if (particles.get(i).progress >= 1f) {
                particles.remove(i);
                i--;
            }
        }
        if (parentView != null) {
            parentView.invalidate();
        }
        firsDraw = false;
    }

    public boolean done() {
        return System.currentTimeMillis() - startTime > 2500;
    }

    public void setView(View view) {
        animatedEmojiDrawable.addView(view);
        parentView = view;
        if (effectImageReceiver != null && showGeneric) {
            effectImageReceiver.onAttachedToWindow();
            TLRPC.Document document = animatedEmojiDrawable.getDocument();
            String emojicon = MessageObject.findAnimatedEmojiEmoticon(document, null);
            boolean imageSet = false;
            if (emojicon != null) {
                TLRPC.TL_availableReaction reaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(emojicon);
                if (reaction != null && reaction.around_animation != null) {
                    effectImageReceiver.setImage(ImageLocation.getForDocument(reaction.around_animation), ReactionsEffectOverlay.getFilterForAroundAnimation(), null, null, reaction.around_animation, 0);
                    imageSet = true;
                }
            }
            if (!imageSet) {
                String packName = UserConfig.getInstance(currentAccount).genericAnimationsStickerPack;
                TLRPC.TL_messages_stickerSet set = null;
                if (packName != null) {
                    set = MediaDataController.getInstance(currentAccount).getStickerSetByName(packName);
                    if (set == null) {
                        set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(packName);
                    }
                }
                if (set != null) {
                    imageSet = true;
                    if (animationIndex < 0) {
                        animationIndex = Math.abs(Utilities.fastRandom.nextInt() % set.documents.size());
                    }
                    effectImageReceiver.setImage(ImageLocation.getForDocument(set.documents.get(animationIndex)), "60_60", null, null, set.documents.get(animationIndex), 0);
                }
            }

            if (imageSet) {
                if (effectImageReceiver.getLottieAnimation() != null) {
                    effectImageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                }
                effectImageReceiver.setAutoRepeat(0);
            } else {
                RLottieDrawable rLottieDrawable = new RLottieDrawable(R.raw.custom_emoji_reaction, "" + R.raw.custom_emoji_reaction, AndroidUtilities.dp(60), AndroidUtilities.dp(60), false, null);
                effectImageReceiver.setImageBitmap(rLottieDrawable);
            }
        }
    }

    public void removeView(View view) {
        animatedEmojiDrawable.removeView(view);
        if (effectImageReceiver != null) {
            effectImageReceiver.onDetachedFromWindow();
            effectImageReceiver.clearImage();
        }
    }

    private class Particle {
        float fromX, fromY;
        float toX;
        float toY1;
        float toY2;
        float fromSize;
        float toSize;
        float progress;
        long duration;

        boolean mirror;
        float randomRotation;

        public void generate() {
            progress = 0;
            float bestDistance = 0;
            float bestX = randX();
            float bestY = randY();
            for (int k = 0; k < 20; k++) {
                float randX = randX();
                float randY = randY();
                float minDistance = Integer.MAX_VALUE;
                for (int j = 0; j < particles.size(); j++) {
                    float rx = particles.get(j).toX - randX;
                    float ry = particles.get(j).toY1 - randY;

                    float distance = rx * rx + ry * ry;

                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
                if (minDistance > bestDistance) {
                    bestDistance = minDistance;
                    bestX = randX;
                    bestY = randY;
                }
            }

            float pivotX = longAnimation ? 0.8f : 0.5f;
            toX = bestX;
            if (toX > bounds.width() * pivotX) {
                fromX = bounds.width() * pivotX;// + bounds.width() * 0.1f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);
            } else {
                fromX = bounds.width() * pivotX;// - bounds.width() * 0.3f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);
                if (toX > fromX) {
                    toX = fromX - 0.1f;
                }
            }

            fromY = bounds.height() * 0.45f + bounds.height() * 0.1f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);


            if (longAnimation) {
                fromSize = bounds.width() * 0.05f + bounds.width() * 0.1f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);
                toSize = fromSize * (1.5f + 1.5f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f));
                toY1 = fromSize / 2f + (bounds.height() * 0.1f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f));
                toY2 = bounds.height() + fromSize;
                duration = 1000 + Math.abs(Utilities.fastRandom.nextInt() % 600);
            } else {
                fromSize = bounds.width() * 0.05f + bounds.width() * 0.1f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);
                toSize = fromSize * (1.5f + 0.5f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f));
                toY1 = bestY;
                toY2 = toY1 + bounds.height();
                duration = 1800;
            }
            duration /= 1.75f;
            mirror = Utilities.fastRandom.nextBoolean();
            randomRotation = 20 * ((Utilities.fastRandom.nextInt() % 100) / 100f);
        }

        private float randY() {
            return (bounds.height() * 0.5f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f));
        }

        private long randDuration() {
            return 1000 + Math.abs(Utilities.fastRandom.nextInt() % 900);
        }

        private float randX() {
            if (longAnimation) {
                return bounds.width() * -0.25f + bounds.width() * 1.5f * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);
            } else {
                return bounds.width() * (Math.abs(Utilities.fastRandom.nextInt() % 100) / 100f);
            }
        }

        public void draw(Canvas canvas) {
            progress += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / duration;
            progress = Utilities.clamp(progress, 1f, 0f);
            float progressInternal = CubicBezierInterpolator.EASE_OUT.getInterpolation(progress);
            float cx = AndroidUtilities.lerp(fromX, toX, progressInternal);
            float cy;
            float k = longAnimation ? 0.3f : 0.3f;
            float k1 = 1f - k;
            if (progress < k) {
                cy = AndroidUtilities.lerp(fromY, toY1, CubicBezierInterpolator.EASE_OUT.getInterpolation(progress / k));
            } else {
                cy = AndroidUtilities.lerp(toY1, toY2, CubicBezierInterpolator.EASE_IN.getInterpolation((progress - k) / k1));
            }

            float size = AndroidUtilities.lerp(fromSize, toSize, progressInternal);
            float outAlpha = 1f;
            if (!longAnimation) {
                float bottomBound = bounds.height() * 0.8f;
                if (cy > bottomBound) {
                    outAlpha = 1f - Utilities.clamp(((cy - bottomBound) / AndroidUtilities.dp(16)), 1f, 0f);
                }
            }
            float sizeHalf = size / 2f * outAlpha;
            canvas.save();
            if (mirror) {
                canvas.scale(-1f, 1f, cx, cy);
            }
            canvas.rotate(randomRotation, cx, cy);
            animatedEmojiDrawable.setAlpha((int) (255 * outAlpha * Utilities.clamp(progress / 0.2f, 1f, 0f)));
            animatedEmojiDrawable.setBounds((int) (cx - sizeHalf), (int) (cy - sizeHalf), (int) (cx + sizeHalf), (int) (cy + sizeHalf));
            animatedEmojiDrawable.draw(canvas);
            animatedEmojiDrawable.setAlpha(255);
            canvas.restore();
        }
    }
}
