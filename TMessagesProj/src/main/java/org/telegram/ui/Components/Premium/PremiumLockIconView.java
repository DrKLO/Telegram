package org.telegram.ui.Components.Premium;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

public class PremiumLockIconView extends ImageView {

    public static int TYPE_REACTIONS = 0;
    public static int TYPE_STICKERS_PREMIUM_LOCKED = 1;

    private final int type;
    public boolean isEnter;
    private float[] colorFloat = new float[3];
    StarParticlesView.Drawable starParticles;
    private boolean locked;
    private Theme.ResourcesProvider resourcesProvider;
    boolean attachedToWindow;

    public PremiumLockIconView(Context context, int type) {
        this(context, type, null);
    }

    public PremiumLockIconView(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.type = type;
        this.resourcesProvider = resourcesProvider;
        setImageResource(type == TYPE_REACTIONS ? R.drawable.msg_premium_lock2 : R.drawable.msg_mini_premiumlock);
        if (type == TYPE_REACTIONS) {
            starParticles = new StarParticlesView.Drawable(5);
            starParticles.updateColors();
            starParticles.roundEffect = false;
            starParticles.size3 = starParticles.size2 = 4;
            starParticles.size1 = 2;
            starParticles.speedScale = 0.1f;
            starParticles.init();
        }
    }

    boolean colorRetrieved = false;
    int currentColor = Color.WHITE;
    int color1, color2;
    Shader shader = null;

    Path path = new Path();
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint oldShaderPaint;
    ImageReceiver imageReceiver;
    AnimatedEmojiDrawable emojiDrawable;
    float shaderCrossfadeProgress = 1f;
    boolean waitingImage;
    boolean wasDrawn;

    @Nullable
    CellFlickerDrawable cellFlickerDrawable;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (type == TYPE_REACTIONS) {
            path.rewind();
            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            path.addCircle(AndroidUtilities.rectTmp.width() / 2f, AndroidUtilities.rectTmp.centerY(), AndroidUtilities.rectTmp.width() / 2f, Path.Direction.CW);
            AndroidUtilities.rectTmp.set(getMeasuredWidth() / 2f + AndroidUtilities.dp(2.5f), getMeasuredHeight() / 2f + AndroidUtilities.dpf2(5.7f), getMeasuredWidth() - AndroidUtilities.dpf2(0.2f), getMeasuredHeight());
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(2f), AndroidUtilities.dp(2f), Path.Direction.CW);
            path.close();

            starParticles.rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            starParticles.rect.inset(AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        } else {
            updateGradient();
        }
    }

    public void setColor(int color) {
        colorRetrieved = true;
        if (currentColor != color) {
            currentColor = color;
            if (type == TYPE_REACTIONS) {
                paint.setColor(color);
            } else {
                updateGradient();
            }
            invalidate();
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (waitingImage) {
            if (imageReceiver != null && imageReceiver.getBitmap() != null) {
                waitingImage = false;
                setColor(AndroidUtilities.getDominantColor(imageReceiver.getBitmap()));
            } else if (emojiDrawable != null) {
                int color = AnimatedEmojiDrawable.getDominantColor(emojiDrawable);
                if (color != 0) {
                    waitingImage = false;
                    setColor(color);
                } else {
                    invalidate();
                }
            } else {
                invalidate();
            }
        }
        if (paint != null) {
            if (type == TYPE_REACTIONS) {
                if (currentColor != 0) {
                    canvas.drawPath(path, paint);
                } else {
                    PremiumGradient.getInstance().updateMainGradientMatrix(0, 0, getMeasuredWidth(), getMeasuredHeight(), -AndroidUtilities.dp(24), 0);
                    canvas.drawPath(path, PremiumGradient.getInstance().getMainGradientPaint());
                }
                if (cellFlickerDrawable == null) {
                    cellFlickerDrawable = new CellFlickerDrawable();
                }
                cellFlickerDrawable.setParentWidth(getMeasuredWidth() / 2);
                cellFlickerDrawable.drawFrame = false;
                cellFlickerDrawable.draw(canvas, path, this);
                canvas.save();
                canvas.clipPath(path);
                starParticles.onDraw(canvas);
                canvas.restore();
                invalidate();
            } else {
                float cx = getMeasuredWidth() / 2f;
                float cy = getMeasuredHeight() / 2f;
                if (oldShaderPaint == null) {
                    shaderCrossfadeProgress = 1f;
                }
                if (shaderCrossfadeProgress != 1f) {
                    paint.setAlpha((int) (255 * shaderCrossfadeProgress));
                    canvas.drawCircle(cx, cy, cx, oldShaderPaint);
                    canvas.drawCircle(cx, cy, cx, paint);
                    shaderCrossfadeProgress += 16 / 150f;
                    if (shaderCrossfadeProgress > 1f) {
                        shaderCrossfadeProgress = 1f;
                        oldShaderPaint = null;
                    }
                    invalidate();
                    paint.setAlpha(255);
                } else {
                    canvas.drawCircle(cx, cy, cx, paint);
                }
            }
        }
        super.onDraw(canvas);
        wasDrawn = true;
    }

    public void setImageReceiver(ImageReceiver imageReceiver) {
        this.imageReceiver = imageReceiver;
        if (imageReceiver != null) {
            waitingImage = true;
            invalidate();
        }
    }

    public void setAnimatedEmojiDrawable(AnimatedEmojiDrawable emojiDrawable) {
        this.emojiDrawable = emojiDrawable;
        if (emojiDrawable != null) {
            waitingImage = true;
            invalidate();
        }
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    private void updateGradient() {
        if (!attachedToWindow) {
            return;
        }
        if (getMeasuredHeight() != 0 && getMeasuredWidth() != 0) {
            int c1 = currentColor;
            int c2;
            Color.colorToHSV(c1, colorFloat);
            colorFloat[1] *= locked ? 2 : 1;
            if (colorFloat[2] > 0.7f) {
                colorFloat[2] = 0.7f;
            }
            int baseColor = Color.HSVToColor(colorFloat);

            c2 = ColorUtils.blendARGB(baseColor, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 0.5f);
            c1 = ColorUtils.blendARGB(baseColor, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 0.4f);

            if (shader == null || color1 != c1 || color2 != c2) {
                if (wasDrawn) {
                    oldShaderPaint = paint;
                    oldShaderPaint.setAlpha(255);
                    shaderCrossfadeProgress = 0;
                }
                paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shader = new LinearGradient(0, getMeasuredHeight(), 0, 0, new int[]{color1 = c1, color2 = c2}, null, Shader.TileMode.CLAMP);
                paint.setShader(shader);
                invalidate();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        if (type != TYPE_REACTIONS) {
            updateGradient();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        if (paint != null) {
            paint.setShader(null);
            paint = null;
        }
        shader = null;
        wasDrawn = false;
    }

    public void setWaitingImage() {
        waitingImage = true;
        wasDrawn = false;
        invalidate();
    }

    public boolean ready() {
        return colorRetrieved;
    }

    public void play(int delay) {
        isEnter = true;
        if (cellFlickerDrawable != null) {
            cellFlickerDrawable.progress = 0;
            cellFlickerDrawable.repeatEnabled = false;
        }
        invalidate();
        animate().scaleX(1.1f).scaleY(1.1f).setStartDelay(delay).setInterpolator(AndroidUtilities.overshootInterpolator).setDuration(300);
    }

    public void resetAnimation() {
        isEnter = false;
        setScaleX(0);
        setScaleY(0);
    }

    public void setLocked(boolean locked) {
        if (type != TYPE_REACTIONS) {
            setImageResource(locked ? R.drawable.msg_mini_premiumlock : R.drawable.msg_mini_stickerstar);
        }
    }
}
