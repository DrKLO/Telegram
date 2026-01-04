package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
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

public class ProfileGooeyView extends FrameLayout {
    private static final float AVATAR_SIZE_DP = 100;
    private static final float BLACK_KING_BAR = 32;

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
        public void draw(Drawer drawer, Canvas canvas) {
            canvas.save();
            canvas.translate(0, -dp(BLACK_KING_BAR));
            drawer.draw(canvas);
            canvas.restore();
        }
    }

    private final class CPUImpl implements Impl {
        private Bitmap bitmap;
        private Canvas bitmapCanvas;
        private final Paint bitmapPaint = new Paint();
        private final Paint bitmapPaint2 = new Paint();

        private int optimizedH;
        private int optimizedW;

        private int bitmapOrigW, bitmapOrigH;
        private final float scaleConst = 5f;

        {
            bitmapPaint.setFlags(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            bitmapPaint.setFilterBitmap(true);
            bitmapPaint2.setFlags(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            bitmapPaint2.setFilterBitmap(true);
            bitmapPaint2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            bitmapPaint.setColorFilter(new ColorMatrixColorFilter(new float[] {
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 60, -7500
            }));
        }

        @Override
        public void onSizeChanged(int w, int h) {
            if (bitmap != null) {
                bitmap.recycle();
            }

            optimizedW = Math.min(dp(120), w);
            optimizedH = Math.min(dp(220), h);

            bitmapOrigW = optimizedW;
            bitmapOrigH = optimizedH + dp(BLACK_KING_BAR);
            bitmap = Bitmap.createBitmap((int) (bitmapOrigW / scaleConst), (int) (bitmapOrigH / scaleConst), Bitmap.Config.ARGB_8888);

            bitmapCanvas = new Canvas(bitmap);
        }

        @Override
        public void draw(Drawer drawer, Canvas canvas) {
            if (bitmap == null) return;

            final float v = (MathUtils.clamp(blurIntensity, 0.2f, 0.3f) - 0.2f) / (0.3f - 0.2f);
            final int alpha = (int) ((1f - v) * 0xFF);
            final float optimizedOffsetX = (getWidth() - optimizedW) / 2f;

            // Offset everything for black bar
            canvas.save();
            canvas.translate(0, -dp(BLACK_KING_BAR));

            if (alpha != 255) {
                bitmap.eraseColor(0);

                bitmapCanvas.save();
                bitmapCanvas.scale((float) bitmap.getWidth() / bitmapOrigW, (float) bitmap.getHeight() / bitmapOrigH);
                bitmapCanvas.translate(-optimizedOffsetX, 0);
                drawer.draw(bitmapCanvas);
                bitmapCanvas.restore();


                bitmapCanvas.save();
                bitmapCanvas.scale((float) bitmap.getWidth() / bitmapOrigW, (float) bitmap.getHeight() / bitmapOrigH);
                if (notchInfo != null) {
                    bitmapCanvas.save();
                    bitmapCanvas.translate(-optimizedOffsetX, dp(BLACK_KING_BAR));
                    if (notchInfo.isLikelyCircle) {
                        float rad = Math.min(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                        bitmapCanvas.drawCircle(notchInfo.bounds.centerX(), notchInfo.bounds.bottom - notchInfo.bounds.width() / 2f, rad, blackPaint);
                    } else if (notchInfo.isAccurate) {
                        bitmapCanvas.drawPath(notchInfo.path, blackPaint);
                    } else {
                        float rad = Math.max(notchInfo.bounds.width(), notchInfo.bounds.height()) / 2f;
                        bitmapCanvas.drawRoundRect(notchInfo.bounds, rad, rad, blackPaint);
                    }
                    bitmapCanvas.restore();
                } else {
                    bitmapCanvas.drawRect(0, 0, optimizedW, dp(BLACK_KING_BAR), blackPaint);
                }
                bitmapCanvas.restore();


                // Blur buffer
                Utilities.stackBlurBitmap(bitmap, (int) (intensity * 2 / scaleConst));

                // Filter alpha + fade, then draw
                canvas.save();
                canvas.translate(optimizedOffsetX, 0);
                canvas.saveLayer(0, 0, bitmapOrigW, bitmapOrigH, null);
                canvas.scale((float) bitmapOrigW / bitmap.getWidth(), (float) bitmapOrigH / bitmap.getHeight());
                canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                canvas.drawBitmap(bitmap, 0, 0, bitmapPaint2);
                canvas.restore();
                canvas.restore();
            }

            // Fade, draw blurred
            if (alpha != 0) {
                if (alpha != 255) {
                    canvas.saveLayerAlpha(optimizedOffsetX, 0, optimizedOffsetX + optimizedW, optimizedH, alpha);
                }
                drawer.draw(canvas);
                if (alpha != 255) {
                    canvas.restore();
                }
            }

            canvas.restore();
        }

        @Override
        public void release() {
            if (bitmap != null) {
                bitmap.recycle();
            }
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
            } else {
                wholeOptimized.set(whole);
            }
            wholeOptimized.bottom += dp(BLACK_KING_BAR);

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
            final int imageAlphaNoClamp = (int) ((1f - ilerp(pullProgress, 0.5f, 1.0f)) * 255);
            final int imageAlpha = MathUtils.clamp(imageAlphaNoClamp, 0, 255);
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
            if (imageAlpha < 255) {
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
            if (imageAlpha > 0) {
                if (imageAlpha != 255) {
                    c.saveLayerAlpha(wholeOptimized, imageAlpha);
                }
                c.drawRenderNode(node);
                if (imageAlpha != 255) {
                    c.restore();
                }
            }
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
            canvas.save();
            canvas.translate(left, top - dp(BLACK_KING_BAR));

            if (notchInfo != null) {
                canvas.clipRect(0, notchInfo.bounds.top, width, height);
            }

            // Filter alpha + fade, then draw
            canvas.saveLayer(wholeOptimized, filter);
            canvas.scale(gooScaleFactor, gooScaleFactor);
            canvas.drawRenderNode(effectNotchNode);
            canvas.drawRenderNode(effectNode);
            canvas.restore();

            // Fade, draw blurred
            final int blurImageAlpha = MathUtils.clamp(imageAlphaNoClamp * 3 / 4, 0, 255);
            if (blurImageAlpha < 255) {
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

            if (blurImageAlpha > 0) {
                if (blurImageAlpha != 255) {
                    canvas.saveLayerAlpha(wholeOptimized, blurImageAlpha);
                }
                if (blurIntensity != 0) {
                    canvas.saveLayer(wholeOptimized, filter);
                    canvas.scale(blurScaleFactor, blurScaleFactor);
                    canvas.drawRenderNode(blurNode);
                    canvas.restore();
                } else {
                    canvas.drawRenderNode(node);
                }
                if (blurImageAlpha != 255) {
                    canvas.restore();
                }
            }

            canvas.restore();
        }
    }

    private interface Impl {
        default void setIntensity(float intensity) {}
        default void setBlurIntensity(float intensity) {}
        default void onSizeChanged(int w, int h) {}
        void draw(Drawer drawer, Canvas canvas);
        default void release() {}
    }

    private interface Drawer {
        void draw(Canvas canvas);
    }
}
