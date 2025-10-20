package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.NotchInfoUtils;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

import java.util.Arrays;

public class ProfileGooeyView extends FrameLayout {
    private static final float AVATAR_SIZE_DP = 100;
    private static final float BLACK_KING_BAR = 32;

    private final Paint fadeToTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadeToBottom = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Impl impl;
    private float intensity;
    private float pullProgress;
    private float blurIntensity;
    private boolean enabled;

    private final boolean isSamsung;

    @Nullable
    public NotchInfoUtils.NotchInfo notchInfo;

    public ProfileGooeyView(Context context) {
        super(context);

        isSamsung = Build.MANUFACTURER.equalsIgnoreCase("samsung");
        blackPaint.setColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            impl = new GPUImpl(SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 1f : 1.5f);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) {
            impl = new CPUImpl();
        } else {
            impl = new NoopImpl();
        }
        setIntensity(15f);
        setBlurIntensity(0f);
        setWillNotDraw(false);
    }

    public float getEndOffset(boolean occupyStatusBar, float avatarScale) {
        if (notchInfo != null) {
            return -(dp(16) + (notchInfo.isLikelyCircle ? notchInfo.bounds.width() + notchInfo.bounds.width() * getAvatarEndScale() : notchInfo.bounds.height() - notchInfo.bounds.top));
        }

        return -((occupyStatusBar ? AndroidUtilities.statusBarHeight : 0) + dp(16) + dp(AVATAR_SIZE_DP));
    }

    public float getAvatarEndScale() {
        if (notchInfo != null) {
            float f;
            if (notchInfo.isLikelyCircle) {
                f = (notchInfo.bounds.width() - dp(2)) / dp(AVATAR_SIZE_DP);
            } else {
                f = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / dp(AVATAR_SIZE_DP);
            }
            return Math.min(0.8f, f);
        }

        return 0.8f;
    }

    public boolean hasNotchInfo() {
        return notchInfo != null;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
        impl.setIntensity(intensity);
        invalidate();
    }

    public void setPullProgress(float pullProgress) {
        this.pullProgress = pullProgress;
        invalidate();
    }

    public void setBlurIntensity(float intensity) {
        this.blurIntensity = intensity;
        impl.setBlurIntensity(intensity);
        invalidate();
    }

    public void setGooeyEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        notchInfo = NotchInfoUtils.getInfo(getContext());
        if (notchInfo != null && (notchInfo.gravity != Gravity.CENTER) || getWidth() > getHeight()) {
            notchInfo = null;
        }

        impl.onSizeChanged(w, h);

        fadeToTop.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadeToTop.setShader(new LinearGradient(getWidth() / 2f, 0, getWidth() / 2f, dp(50), new int[] {0xFF000000, 0xFFFFFFFF}, new float[]{0.15f, 1f}, Shader.TileMode.CLAMP));

        fadeToBottom.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadeToBottom.setShader(new LinearGradient(getWidth() / 2f, 0, getWidth() / 2f, dp(50), new int[] {0xFFFFFFFF, 0xFF000000}, new float[]{0.25f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!enabled) {
            super.draw(canvas);
            return;
        }
        impl.draw(c -> {
            c.save();
            c.translate(0, dp(BLACK_KING_BAR));
            super.draw(c);
            c.restore();
        }, canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        impl.release();
    }

    private final static class NoopImpl implements Impl {

        @Override
        public void setIntensity(float intensity) {}

        @Override
        public void setBlurIntensity(float intensity) {}

        @Override
        public void onSizeChanged(int w, int h) {}

        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            canvas.save();
            canvas.translate(0, -dp(BLACK_KING_BAR));
            drawer.draw(canvas);
            canvas.restore();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final class CPUImpl implements Impl {
        private Paint filter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap[] bitmaps = new Bitmap[2];
        private Canvas[] bitmapCanvas = new Canvas[bitmaps.length];
        private Paint bitmapPaint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);

        @Override
        public void setIntensity(float intensity) {
            filter.setColorFilter(new ColorMatrixColorFilter(new float[] {
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 4f * intensity, -125 * 4 * intensity
            }));
        }

        @Override
        public void setBlurIntensity(float intensity) {}

        @Override
        public void onSizeChanged(int w, int h) {
            for (Bitmap bm : bitmaps) {
                if (bm != null) {
                    bm.recycle();
                }
            }
            bitmaps[0] = Bitmap.createBitmap(w, h + dp(BLACK_KING_BAR), Bitmap.Config.ARGB_8888);
            bitmapCanvas[0] = new Canvas(bitmaps[0]);

            bitmaps[1] = Bitmap.createBitmap(w / 4, h / 4, Bitmap.Config.ARGB_8888);
            bitmapCanvas[1] = new Canvas(bitmaps[1]);
        }

        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            if (bitmaps[0] == null) return;

            // Draw into buffers
            for (int i = 0; i < 2; i++) {
                bitmaps[i].eraseColor(0);
            }
            drawer.draw(bitmapCanvas[0]);

            float scX = bitmaps[1].getWidth() / (float) bitmaps[0].getWidth(),
                scY = bitmaps[1].getWidth() / (float) bitmaps[0].getWidth();
            bitmapCanvas[1].save();
            bitmapCanvas[1].scale(scX, scY);
            bitmapCanvas[1].drawBitmap(bitmaps[0], 0, 0, null);
            if (notchInfo != null) {
                bitmapCanvas[1].translate(0, dp(BLACK_KING_BAR));
                if (notchInfo.isLikelyCircle) {
                    float rad = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    bitmapCanvas[1].drawCircle(notchInfo.bounds.centerX(), notchInfo.bounds.bottom - notchInfo.bounds.width() / 2f, rad, blackPaint);
                } else if (notchInfo.isAccurate) {
                    bitmapCanvas[1].drawPath(notchInfo.path, blackPaint);
                } else {
                    float rad = Math.max(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    bitmapCanvas[1].drawRoundRect(notchInfo.bounds, rad, rad, blackPaint);
                }
            } else {
                bitmapCanvas[1].drawRect(0, 0, getWidth(), dp(BLACK_KING_BAR), blackPaint);
            }
            bitmapCanvas[1].restore();

            // Blur buffer
            Utilities.stackBlurBitmap(bitmaps[1], (int) (intensity / 2));

            // Offset everything for black bar
            canvas.translate(0, -dp(BLACK_KING_BAR));
            canvas.save();

            // Filter alpha + fade, then draw
            canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.saveLayer(0, 0, getWidth(), getHeight(), filter);
            canvas.scale(1f / scX, 1f / scY);
            canvas.drawBitmap(bitmaps[1], 0, 0, bitmapPaint);
            canvas.restore();
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToBottom);
            canvas.restore();

            // Fade, draw blurred
            canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
            canvas.saveLayer(0, 0, getWidth(), getHeight(), filter);
            float v = (MathUtils.clamp(blurIntensity, 0.22f, 0.24f) - 0.22f) / (0.24f - 0.22f);
            bitmapPaint.setAlpha((int) (v * 0xFF));
            canvas.scale(1f / scX,  1f / scY);
            canvas.drawBitmap(bitmaps[1], 0, 0, bitmapPaint);
            canvas.restore();
            if (v != 1) {
                bitmapPaint.setAlpha((int) ((1f - v) * 0xFF));
                canvas.drawBitmap(bitmaps[0], 0, 0, bitmapPaint);
            }
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToTop);
            canvas.restore();

            canvas.restore();
        }

        @Override
        public void release() {
            for (Bitmap bm : bitmaps) {
                if (bm != null) {
                    bm.recycle();
                }
            }
            Arrays.fill(bitmaps, null);
            Arrays.fill(bitmapCanvas, null);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private final class GPUImpl implements Impl {
        private final Paint filter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RenderNode node = new RenderNode("render");
        private final RenderNode effectNotchNode = new RenderNode("effectNotch");
        private final RenderNode effectNode = new RenderNode("effect");
        private final RenderNode blurNode = new RenderNode("blur");
        private float factorMult;

        private final RectF whole = new RectF();
        private final RectF temp = new RectF();

        private final Paint blackNodePaint = new Paint();

        private GPUImpl(float factorMult) {
            this.factorMult = factorMult;

            blackNodePaint.setColor(Color.BLACK);
            blackNodePaint.setBlendMode(BlendMode.SRC_IN);
        }

        @Override
        public void setIntensity(float intensity) {
            effectNode.setRenderEffect(RenderEffect.createBlurEffect(intensity, intensity, Shader.TileMode.CLAMP));
            effectNotchNode.setRenderEffect(RenderEffect.createBlurEffect(intensity, intensity, Shader.TileMode.CLAMP));
            filter.setColorFilter(new ColorMatrixColorFilter(new float[] {
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 51, 51 * -125
            }));
        }

        @Override
        public void setBlurIntensity(float blurIntensity) {
            if (blurIntensity == 0) {
                blurNode.setRenderEffect(null);
                return;
            }
            blurNode.setRenderEffect(RenderEffect.createBlurEffect(blurIntensity * intensity / factorMult, blurIntensity * intensity / factorMult, Shader.TileMode.DECAL));
        }

        @Override
        public void onSizeChanged(int w, int h) {
            int off = dp(BLACK_KING_BAR);
            node.setPosition(0, 0, w, h + off);
            effectNode.setPosition(0, 0, w, h + off);
            effectNotchNode.setPosition(0, 0, w, h + off);
            blurNode.setPosition(0, 0, w, h + off);
        }

        @Override
        public void draw(Drawer drawer, @NonNull Canvas canvas) {
            if (!canvas.isHardwareAccelerated()) {
                return;
            }
            Canvas c;

            // Record everything into buffer
            c = node.beginRecording();
            final float imageAlpha = 1f - ilerp(pullProgress, 0.5f, 1.0f);
            whole.set(0, 0, getWidth(), getHeight());
            drawer.draw(c);
            node.endRecording();

            // Blur only buffer
            float blurScaleFactor = factorMult / 4f + 1f + blurIntensity * 0.5f * factorMult + (factorMult - 1f) * 2f;
            c = blurNode.beginRecording();
            c.scale(1f / blurScaleFactor, 1f / blurScaleFactor, 0, 0);
            c.drawRenderNode(node);
            blurNode.endRecording();

            // Blur + filter buffer
            float gooScaleFactor = 2f + factorMult;
            c = effectNode.beginRecording();
            c.scale(1f / gooScaleFactor, 1f / gooScaleFactor, 0, 0);
            if (imageAlpha < 1) {
                c.saveLayer(whole, null);
                c.drawRenderNode(node);
                c.drawRect(whole, blackNodePaint);
                c.restore();
            }
            final float h = lerp(0, dp(7) * gooScaleFactor, 0, 0.5f, pullProgress);
            if (getChildCount() > 0) {
                final View child = getChildAt(0);
                final float cx = child.getX() + child.getWidth() * child.getScaleX() / 2.0f;
                final float cy = child.getY() + child.getHeight() * child.getScaleY() / 2.0f + dp(BLACK_KING_BAR);
                final float r = child.getWidth() / 2.0f * child.getScaleX();

                path.rewind();
                path.moveTo(cx - r, cy - (float) Math.cos(Math.PI / 4) * r);
                path.lineTo(cx, cy - r - h * 0.25f);
                path.lineTo(cx + r, cy - (float) Math.cos(Math.PI / 4) * r);
                path.close();
                c.drawPath(path, blackPaint);
            }
            c.saveLayerAlpha(whole, (int) (0xFF * imageAlpha));
            c.drawRenderNode(node);
            c.restore();
            effectNode.endRecording();

            c = effectNotchNode.beginRecording();
            c.scale(1f / gooScaleFactor, 1f / gooScaleFactor, 0, 0);
            if (notchInfo != null) {
                c.translate(0, dp(BLACK_KING_BAR));
                if (notchInfo.isLikelyCircle) {
                    float rad = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    final float cy = notchInfo.bounds.bottom - notchInfo.bounds.width() / 2f;
                    c.drawCircle(notchInfo.bounds.centerX(), cy, rad, blackPaint);

                    path.rewind();
                    path.moveTo(notchInfo.bounds.centerX() - h / 2f, cy);
                    path.lineTo(notchInfo.bounds.centerX(), cy + rad + h);
                    path.lineTo(notchInfo.bounds.centerX() + h / 2f, cy);
                    path.close();
                    c.drawPath(path, blackPaint);
                } else if (notchInfo.isAccurate) {
                    c.drawPath(notchInfo.path, blackPaint);
                } else {
                    float rad = Math.max(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                    temp.set(notchInfo.bounds);
                    c.drawRoundRect(temp, rad, rad, blackPaint);

                    path.rewind();
                    path.moveTo(temp.centerX() - h / 2f, temp.bottom);
                    path.lineTo(temp.centerX(), temp.bottom + h);
                    path.lineTo(temp.centerX() + h / 2f, temp.bottom);
                    path.close();
                    c.drawPath(path, blackPaint);
                }
            } else {
                c.drawRect(0, 0, getWidth(), dp(BLACK_KING_BAR), blackPaint);

                path.rewind();
                path.moveTo((getWidth() - h) / 2f, dp(BLACK_KING_BAR));
                path.lineTo((getWidth()) / 2f, dp(BLACK_KING_BAR) + h);
                path.lineTo((getWidth() + h) / 2f, dp(BLACK_KING_BAR));
                path.close();
                c.drawPath(path, blackPaint);
            }
            effectNotchNode.endRecording();

            // Offset everything for black bar
            canvas.translate(0, -dp(BLACK_KING_BAR));
            canvas.save();

            if (notchInfo != null) {
                canvas.clipRect(0, notchInfo.bounds.top, getWidth(), getHeight());
            }

            // Filter alpha + fade, then draw
            canvas.saveLayer(0, 0, getWidth() * gooScaleFactor, getHeight() * gooScaleFactor, null);
            canvas.saveLayer(0, 0, getWidth() * gooScaleFactor, getHeight() * gooScaleFactor, filter);
            canvas.scale(gooScaleFactor, gooScaleFactor);
            canvas.drawRenderNode(effectNotchNode);
            canvas.drawRenderNode(effectNode);
            canvas.restore();
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToTop);
            canvas.restore();

            // Fade, draw blurred
            canvas.saveLayer(0, 0, getWidth() * blurScaleFactor, getHeight() * blurScaleFactor, null);
            final float blurImageAlpha = imageAlpha * 0.75f;
            if (blurImageAlpha < 1) {
                canvas.saveLayer(whole, null);
                if (blurIntensity != 0) {
                    canvas.saveLayer(0, 0, getWidth() * blurScaleFactor, getHeight() * blurScaleFactor, filter);
                    canvas.scale(blurScaleFactor, blurScaleFactor);
                    canvas.drawRenderNode(blurNode);
                    canvas.restore();
                } else {
                    canvas.drawRenderNode(node);
                }
                canvas.drawRect(whole, blackNodePaint);
                canvas.restore();
            }
            canvas.saveLayerAlpha(whole, (int) (0xFF * blurImageAlpha));
            if (blurIntensity != 0) {
                canvas.saveLayer(0, 0, getWidth() * blurScaleFactor, getHeight() * blurScaleFactor, filter);
                canvas.scale(blurScaleFactor, blurScaleFactor);
                canvas.drawRenderNode(blurNode);
                canvas.restore();
            } else {
                canvas.drawRenderNode(node);
            }
            canvas.restore();
            canvas.drawRect(0, 0, getWidth(), getHeight(), fadeToBottom);
            canvas.restore();

            canvas.restore();
        }
    }

    private interface Impl {
        void setIntensity(float intensity);
        void setBlurIntensity(float intensity);
        void onSizeChanged(int w, int h);
        void draw(Drawer drawer, Canvas canvas);
        default void release() {}
    }

    private interface Drawer {
        void draw(Canvas canvas);
    }
}
