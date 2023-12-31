package org.telegram.ui.Components.voip;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class ImageWithWavesView extends FrameLayout {
    private final AvatarWavesDrawable avatarWavesDrawable;
    private final BackupImageView backupImageView;
    private AnimatorSet animatorSet;
    private boolean isConnectedCalled;
    private boolean isMuted;
    private final boolean allowAnimations;

    public ImageWithWavesView(Context context) {
        super(context);
        avatarWavesDrawable = new AvatarWavesDrawable(AndroidUtilities.dp(104), AndroidUtilities.dp(111), AndroidUtilities.dp(12), 8);
        avatarWavesDrawable.setAmplitude(3f);
        avatarWavesDrawable.setShowWaves(true, this);
        backupImageView = new BackupImageView(context);
        addView(backupImageView, LayoutHelper.createFrame(135, 135, Gravity.CENTER));
        setWillNotDraw(false);
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, View.SCALE_X, 1.f, 1.05f, 1f, 1.05f, 1f),
                ObjectAnimator.ofFloat(this, View.SCALE_Y, 1.f, 1.05f, 1f, 1.05f, 1f)
        );
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animatorSet.setDuration(3000);
        allowAnimations = LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS);
        if (allowAnimations) {
            animatorSet.start();
        }
        setClipChildren(false);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, String ext, Drawable thumb, Object parentObject) {
        backupImageView.setImage(imageLocation, imageFilter, ext, thumb, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        backupImageView.setImage(imageLocation, imageFilter, thumb, parentObject);
    }

    public void setRoundRadius(int value) {
        backupImageView.setRoundRadius(value);
    }

    public void setShowWaves(boolean showWaves) {
        avatarWavesDrawable.setShowWaves(showWaves, this);
    }

    public void setMute(boolean isMuted, boolean isFast) {
        if (this.isMuted != isMuted) {
            this.isMuted = isMuted;
            if (isMuted) {
                avatarWavesDrawable.setAmplitude(3f);
            }
            avatarWavesDrawable.setMuteToStatic(isMuted, isFast, this);
        }
    }

    public void setAmplitude(double value) {
        if (isMuted) return;
        if (value > 1.5f) {
            avatarWavesDrawable.setAmplitude(value);
        } else {
            avatarWavesDrawable.setAmplitude(0);
        }
    }

    public void onConnected() {
        if (isConnectedCalled) {
            return;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        isConnectedCalled = true;
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, View.SCALE_X, this.getScaleX(), 1.05f, 1f),
                ObjectAnimator.ofFloat(this, View.SCALE_Y, this.getScaleY(), 1.05f, 1f)
        );
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animatorSet.setDuration(400);
        animatorSet.start();
    }

    public void onNeedRating() {
        setShowWaves(false);
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, View.ALPHA, this.getAlpha(), 1f),
                ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, this.getTranslationY(), -(float) AndroidUtilities.dp(24)),
                ObjectAnimator.ofFloat(this, View.SCALE_X, this.getScaleX(), 0.9f, 1f),
                ObjectAnimator.ofFloat(this, View.SCALE_Y, this.getScaleY(), 0.9f, 1f)
        );
        animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorSet.setDuration(300);
        animatorSet.setStartDelay(250);
        animatorSet.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(allowAnimations) {
            avatarWavesDrawable.update();
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            avatarWavesDrawable.draw(canvas, cx, cy, this);
        }
        super.onDraw(canvas);
    }

    public static class AvatarWavesDrawable {

        float amplitude;
        float animateToAmplitude;
        float animateAmplitudeDiff;
        float wavesEnter = 0f;
        boolean showWaves;

        private final VoipBlobDrawable blobDrawable;
        private final VoipBlobDrawable blobDrawable2;

        public boolean muteToStatic = false;
        public float muteToStaticProgress = 1f;

        private ValueAnimator animator;
        private int muteToStaticInvalidationCount;

        public AvatarWavesDrawable(int minRadius, int maxRadius, int diff, int n) {
            blobDrawable = new VoipBlobDrawable(n - 1);
            blobDrawable2 = new VoipBlobDrawable(n);
            blobDrawable.minRadius = minRadius;
            blobDrawable.maxRadius = maxRadius;
            blobDrawable2.minRadius = minRadius - diff;
            blobDrawable2.maxRadius = maxRadius - diff;
            blobDrawable.generateBlob();
            blobDrawable2.generateBlob();
            blobDrawable.paint.setColor(Color.WHITE);
            blobDrawable.paint.setAlpha(20);
            blobDrawable2.paint.setColor(Color.WHITE);
            blobDrawable2.paint.setAlpha(36);
        }

        public void update() {
            if (animateToAmplitude != amplitude) {
                amplitude += animateAmplitudeDiff * 16;
                if (animateAmplitudeDiff > 0) {
                    if (amplitude > animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                } else {
                    if (amplitude < animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                }
            }

            if (showWaves && wavesEnter != 1f) {
                wavesEnter += 16 / 350f;
                if (wavesEnter > 1f) {
                    wavesEnter = 1f;
                }
            } else if (!showWaves && wavesEnter != 0) {
                wavesEnter -= 16 / 350f;
                if (wavesEnter < 0f) {
                    wavesEnter = 0f;
                }
            }
        }

        public void draw(Canvas canvas, float cx, float cy, View parentView) {
            float scaleBlob = 0.8f + 0.4f * amplitude;
            if (showWaves || wavesEnter != 0) {
                canvas.save();
                float wavesEnter = CubicBezierInterpolator.DEFAULT.getInterpolation(this.wavesEnter);

                canvas.scale(scaleBlob * wavesEnter, scaleBlob * wavesEnter, cx, cy);

                blobDrawable.update(amplitude, 1f, muteToStaticProgress);
                blobDrawable.draw(cx, cy, canvas, blobDrawable.paint);

                blobDrawable2.update(amplitude, 1f, muteToStaticProgress);
                blobDrawable2.draw(cx, cy, canvas, blobDrawable.paint);
                canvas.restore();
            }

            if (muteToStatic && muteToStaticInvalidationCount == 0) {
                return;
            }

            if (muteToStaticInvalidationCount != 0) {
                muteToStaticInvalidationCount--;
            }

            if (wavesEnter != 0) {
                parentView.invalidate();
            }
        }

        public void setShowWaves(boolean show, View parentView) {
            if (showWaves != show) {
                parentView.invalidate();
            }
            showWaves = show;
        }

        public void setAmplitude(double value) {
            float amplitude = (float) value / 80f;
            if (!showWaves) {
                amplitude = 0;
            }
            if (amplitude > 1f) {
                amplitude = 1f;
            } else if (amplitude < 0) {
                amplitude = 0;
            }
            animateToAmplitude = amplitude;
            animateAmplitudeDiff = (animateToAmplitude - this.amplitude) / 200;
        }

        public void setMuteToStatic(boolean mute, boolean isFast, View parentView) {
            if (muteToStatic != mute) {
                muteToStatic = mute;
                if (animator != null) {
                    animator.removeAllUpdateListeners();
                    animator.cancel();
                }
                if (mute) {
                    animator = ValueAnimator.ofFloat(muteToStaticProgress, 0f);
                    muteToStaticInvalidationCount = (int) (2000 / AndroidUtilities.screenRefreshTime);
                } else {
                    muteToStaticInvalidationCount = 0;
                    animator = ValueAnimator.ofFloat(muteToStaticProgress, 1f);
                }
                animator.addUpdateListener(a -> muteToStaticProgress = (float) a.getAnimatedValue());
                if (isFast) {
                    animator.setDuration(150);
                } else {
                    animator.setDuration(1000);
                }
                animator.start();
                parentView.invalidate();
            }
        }
    }
}
