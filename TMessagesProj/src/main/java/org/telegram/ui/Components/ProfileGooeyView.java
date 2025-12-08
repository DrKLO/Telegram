package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
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
import org.telegram.messenger.NotchInfoUtils;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

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

    @Nullable
    public NotchInfoUtils.NotchInfo notchInfo;

    public ProfileGooeyView(Context context) {
        super(context);

        blackPaint.setColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            impl = new GPUImpl(SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 1f : 1.5f);
        } else if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) {
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

    private final class CPUImpl implements Impl {
        private final Paint filter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap[] bitmaps = new Bitmap[2];
        private final Canvas[] bitmapCanvas = new Canvas[bitmaps.length];
        private final Paint bitmapPaint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);

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

        private int optimizedH;

        @Override
        public void onSizeChanged(int w, int h) {
            for (Bitmap bm : bitmaps) {
                if (bm != null) {
                    bm.recycle();
                }
            }

            optimizedH = Math.min(dp(280), h);

            bitmaps[0] = Bitmap.createBitmap(w, optimizedH + dp(BLACK_KING_BAR), Bitmap.Config.ARGB_8888);
            bitmapCanvas[0] = new Canvas(bitmaps[0]);

            bitmaps[1] = Bitmap.createBitmap(w / 4, optimizedH / 4, Bitmap.Config.ARGB_8888);
            bitmapCanvas[1] = new Canvas(bitmaps[1]);
        }

        int getHeight() {
            return optimizedH;
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
        private final float factorMult;

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

        }

        private final RectF wholeOptimized = new RectF();

        @Override
        public void draw(Drawer drawer, @NonNull Canvas canvas) {
            if (!canvas.isHardwareAccelerated()) {
                return;
            }
            Canvas c;

            whole.set(0, 0, getWidth(), getHeight());
            if (getChildCount() > 0) {
                final View child = getChildAt(0);
                final float w = child.getWidth() * child.getScaleX();
                final float h = child.getHeight() * child.getScaleY();
                final float l = child.getX();
                final float t = child.getY();

                wholeOptimized.set(l, t, l + w, t + h);
                if (notchInfo != null) {
                    wholeOptimized.union(notchInfo.bounds);
                }
                wholeOptimized.inset(-dp(20), -dp(20));
                wholeOptimized.intersect(whole);
                wholeOptimized.top = 0;
                wholeOptimized.bottom += dp(BLACK_KING_BAR);
            } else {
                wholeOptimized.set(whole);
                wholeOptimized.bottom += dp(BLACK_KING_BAR);
            }

            final int width = (int) Math.ceil(wholeOptimized.width());
            final int height = (int) Math.ceil(wholeOptimized.height());
            final float left = wholeOptimized.left;
            final float top = wholeOptimized.top;

            node.setPosition(0, 0, width, height);
            blurNode.setPosition(0, 0, width, height);
            effectNode.setPosition(0, 0, width, height);
            effectNotchNode.setPosition(0, 0, width, height);
            wholeOptimized.set(0, 0, width, height);




            // Record everything into buffer
            c = node.beginRecording();
            c.translate(-left, -top);
            final float imageAlpha = 1f - ilerp(pullProgress, 0.5f, 1.0f);
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
                c.saveLayer(wholeOptimized, null);
                c.drawRenderNode(node);
                c.drawRect(wholeOptimized, blackNodePaint);
                c.restore();
            }
            final float h = lerp(0, dp(7) * gooScaleFactor, 0, 0.5f, pullProgress);
            if (getChildCount() > 0) {
                final View child = getChildAt(0);
                final float cx = child.getX() + child.getWidth() * child.getScaleX() / 2.0f - left;
                final float cy = child.getY() + child.getHeight() * child.getScaleY() / 2.0f + dp(BLACK_KING_BAR) - top;
                final float r = child.getWidth() / 2.0f * child.getScaleX();

                path.rewind();
                path.moveTo(cx - r, cy - (float) Math.cos(Math.PI / 4) * r);
                path.lineTo(cx, cy - r - h * 0.25f);
                path.lineTo(cx + r, cy - (float) Math.cos(Math.PI / 4) * r);
                path.close();
                c.drawPath(path, blackPaint);
            }
            c.saveLayerAlpha(wholeOptimized, (int) (0xFF * imageAlpha));
            c.drawRenderNode(node);
            c.restore();
            effectNode.endRecording();

            c = effectNotchNode.beginRecording();
            c.scale(1f / gooScaleFactor, 1f / gooScaleFactor, 0, 0);
            if (notchInfo != null) {
                c.translate(-left, -top);
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
                c.drawRect(0, 0, width, dp(BLACK_KING_BAR), blackPaint);

                path.rewind();
                path.moveTo((width - h) / 2f, dp(BLACK_KING_BAR));
                path.lineTo((width) / 2f, dp(BLACK_KING_BAR) + h);
                path.lineTo((width + h) / 2f, dp(BLACK_KING_BAR));
                path.close();
                c.drawPath(path, blackPaint);
            }
            effectNotchNode.endRecording();

            // Offset everything for black bar
            canvas.translate(0, -dp(BLACK_KING_BAR));
            canvas.save();
            canvas.translate(left, top);

            if (notchInfo != null) {
                canvas.clipRect(0, notchInfo.bounds.top, width, height);
            }

            // Filter alpha + fade, then draw
            canvas.saveLayer(wholeOptimized, null);
            canvas.saveLayer(wholeOptimized, filter);
            canvas.scale(gooScaleFactor, gooScaleFactor);
            canvas.drawRenderNode(effectNotchNode);
            canvas.drawRenderNode(effectNode);
            canvas.restore();
            canvas.drawRect(wholeOptimized, fadeToTop);
            canvas.restore();

            // Fade, draw blurred
            canvas.saveLayer(wholeOptimized, null);
            final float blurImageAlpha = imageAlpha * 0.75f;
            if (blurImageAlpha < 1) {
                canvas.saveLayer(wholeOptimized, null);
                if (blurIntensity != 0) {
                    canvas.saveLayer(wholeOptimized, filter);
                    canvas.scale(blurScaleFactor, blurScaleFactor);
                    canvas.drawRenderNode(blurNode);
                    canvas.restore();
                } else {
                    canvas.drawRenderNode(node);
                }
                canvas.drawRect(wholeOptimized, blackNodePaint);
                canvas.restore();
            }
            canvas.saveLayerAlpha(wholeOptimized, (int) (0xFF * blurImageAlpha));
            if (blurIntensity != 0) {
                canvas.saveLayer(wholeOptimized, filter);
                canvas.scale(blurScaleFactor, blurScaleFactor);
                canvas.drawRenderNode(blurNode);
                canvas.restore();
            } else {
                canvas.drawRenderNode(node);
            }
            canvas.restore();
            canvas.drawRect(wholeOptimized, fadeToBottom);
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
