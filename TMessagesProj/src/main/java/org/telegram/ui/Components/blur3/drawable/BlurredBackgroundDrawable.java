package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;

import java.lang.ref.WeakReference;
import java.util.Arrays;

public abstract class BlurredBackgroundDrawable extends Drawable {
    public BlurredBackgroundDrawable() {
        boundProps.strokeWidthTop = dpf2(1);
        boundProps.strokeWidthBottom = dpf2(2 / 3f);
    }

    protected float sourceOffsetX;
    protected float sourceOffsetY;

    public void setSourceOffset(float sourceOffsetX, float sourceOffsetY) {
        if (this.sourceOffsetX != sourceOffsetX || this.sourceOffsetY != sourceOffsetY) {
            this.sourceOffsetX = sourceOffsetX;
            this.sourceOffsetY = sourceOffsetY;
            onSourceOffsetChange(sourceOffsetX, sourceOffsetY);
        }
    }

    public void setPadding(int padding) {
        if (boundProps.padding != padding) {
            boundProps.padding = padding;
            boundProps.build();

            onBoundPropsChanged();
        }
    }

    public void setRadius(float radius) {
        Arrays.fill(boundProps.radii, radius);
        Arrays.fill(boundProps.shaderRadii, radius);
        boundProps.build();

        onBoundPropsChanged();
    }

    public void setRadius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        boundProps.radii[0] = boundProps.radii[1] = topLeft;
        boundProps.radii[2] = boundProps.radii[3] = topRight;
        boundProps.radii[4] = boundProps.radii[5] = bottomRight;
        boundProps.radii[6] = boundProps.radii[7] = bottomLeft;
        boundProps.build();

        onBoundPropsChanged();
    }

    public void setRadius(float topLeft, float topRight, float bottomRight, float bottomLeft, boolean forceBottomZero) {
        boundProps.radii[0] = boundProps.radii[1] = topLeft;
        boundProps.radii[2] = boundProps.radii[3] = topRight;
        boundProps.radii[4] = boundProps.radii[5] = forceBottomZero ? 0 : bottomRight;
        boundProps.radii[6] = boundProps.radii[7] = forceBottomZero ? 0 : bottomLeft;
        boundProps.shaderRadii[0] = boundProps.shaderRadii[1] = topLeft;
        boundProps.shaderRadii[2] = boundProps.shaderRadii[3] = topRight;
        boundProps.shaderRadii[4] = boundProps.shaderRadii[5] = bottomRight;
        boundProps.shaderRadii[6] = boundProps.shaderRadii[7] = bottomLeft;
        boundProps.build();

        onBoundPropsChanged();
    }

    public void setThickness(int thickness) {
        boundProps.liquidThickness = thickness;
        onBoundPropsChanged();
    }

    public void setIntensity(float intensity) {
        boundProps.liquidIntensity = intensity;
        onBoundPropsChanged();
    }

    public void setLiquidIndex(float index) {
        boundProps.liquidIndex = index;
        onBoundPropsChanged();
    }

    public void setFillAlpha(float multAlpha) {
        boundProps.fillAlpha = multAlpha;
        updateColors();
    }

    public Rect getPaddedBounds() {
        return boundProps.boundsWithPadding;
    }

    public Path getPath() {
        return boundProps.path;
    }

    @Override
    protected final void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        boundProps.bounds.set(bounds);
        boundProps.build();

        onBoundPropsChanged();
    }


    protected void onBoundPropsChanged() {}

    protected void onSourceOffsetChange(float sourceOffsetX, float sourceOffsetY) {}

    public abstract BlurredBackgroundSource getSource();

    public BlurredBackgroundSource getUnwrappedSource() {
        BlurredBackgroundSource source = getSource();
        while (source instanceof BlurredBackgroundSourceWrapped) {
            source = ((BlurredBackgroundSourceWrapped) source).getSource();
        }

        return source;
    }



    /* Colors */

    protected BlurredBackgroundColorProvider colorProvider;
    protected int shadowColor, backgroundColor, strokeColorTop, strokeColorBottom;

    public void setColorProvider(BlurredBackgroundColorProvider colorProvider) {
        this.colorProvider = colorProvider;
        updateColors();
    }

    @CallSuper
    public void updateColors() {
        if (colorProvider == null) return;

        backgroundColor = colorProvider.getBackgroundColor();
        shadowColor = colorProvider.getShadowColor();
        strokeColorTop = colorProvider.getStrokeColorTop();
        strokeColorBottom = colorProvider.getStrokeColorBottom();
    }



    /* Bound Props */
    private static final float[] tmpRadii = new float[8];
    protected final Props boundProps = new Props();

    protected static class Props {
        public final Rect bounds = new Rect();
        public final float[] radii = new float[8];
        public final float[] shaderRadii = new float[8];
        public int padding;
        public int liquidThickness;
        public float liquidIntensity = 0.75f;
        public float liquidIndex = 1.5f;

        public float fillAlpha = 1.0f;

        public float strokeWidthTop;
        public float strokeWidthBottom;

        public final Path path = new Path();
        public boolean radiiAreSame = true;

        public final Rect boundsWithPadding = new Rect();

        public final Path strokePathTop = new Path();
        public final Path strokePathBottom = new Path();

        public void build() {
            radiiAreSame = radiiAreSame(radii);

            boundsWithPadding.set(bounds);
            boundsWithPadding.inset(padding, padding);

            path.rewind();
            path.addRoundRect(
                boundsWithPadding.left,
                boundsWithPadding.top,
                boundsWithPadding.right,
                boundsWithPadding.bottom,
                radii, Path.Direction.CW);
            path.close();

            Arrays.fill(tmpRadii, 0);
            tmpRadii[0] = radii[0]; tmpRadii[1] = radii[1]; tmpRadii[2] = radii[2]; tmpRadii[3] = radii[3];
            strokePathTop.rewind();
            strokePathTop.addRoundRect(
                boundsWithPadding.left, boundsWithPadding.top, boundsWithPadding.right,
                Math.min(boundsWithPadding.top + radii[0], boundsWithPadding.bottom), tmpRadii, Path.Direction.CW);
            strokePathTop.addRoundRect(
                boundsWithPadding.left, boundsWithPadding.top + strokeWidthTop, boundsWithPadding.right,
                Math.min(boundsWithPadding.top + radii[0], boundsWithPadding.bottom), tmpRadii, Path.Direction.CCW);
            strokePathTop.close();

            Arrays.fill(tmpRadii, 0);
            tmpRadii[4] = radii[4]; tmpRadii[5] = radii[5]; tmpRadii[6] = radii[6]; tmpRadii[7] = radii[7];
            strokePathBottom.rewind();
            strokePathBottom.addRoundRect(
                boundsWithPadding.left, Math.max(boundsWithPadding.bottom - radii[4], boundsWithPadding.top),
                boundsWithPadding.right, boundsWithPadding.bottom, tmpRadii, Path.Direction.CW);
            strokePathBottom.addRoundRect(
                boundsWithPadding.left, Math.max(boundsWithPadding.bottom - radii[4], boundsWithPadding.top),
                boundsWithPadding.right, boundsWithPadding.bottom - strokeWidthBottom, tmpRadii, Path.Direction.CCW);
            strokePathBottom.close();
        }

        public void draw(Canvas canvas, Paint paint) {
            if (radiiAreSame) {
                canvas.drawRoundRect(
                    boundsWithPadding.left,
                    boundsWithPadding.top,
                    boundsWithPadding.right,
                    boundsWithPadding.bottom,
                    radii[0], radii[0], paint);
            } else {
                canvas.drawPath(path, paint);
            }
        }
    }



    /* Outline */

    private ViewOutlineProvider viewOutlineProvider;

    public ViewOutlineProvider getViewOutlineProvider() {
        if (viewOutlineProvider == null) {
            viewOutlineProvider = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    BlurredBackgroundDrawable.getOutline(outline, boundProps.boundsWithPadding, boundProps.radii);
                }
            };
        }

        return viewOutlineProvider;
    }

    private static Path tmpPath = new Path();
    protected static void getOutline(Outline outline, Rect rect, float[] radii) {
        final boolean radiiAreSame = radiiAreSame(radii);

        if (radiiAreSame) {
            outline.setRoundRect(rect, radii[0]);
        } else {
            if (tmpPath == null) {
                tmpPath = new Path();
            } else {
                tmpPath.rewind();
            }
            tmpPath.addRoundRect(
                rect.left, rect.top,
                rect.right, rect.bottom,
                radii, Path.Direction.CW
            );
            outline.setConvexPath(tmpPath);
        }
    }

    private static boolean radiiAreSame(float[] radii) {
        return radii[0] == radii[1]
            && radii[0] == radii[2]
            && radii[0] == radii[3]
            && radii[0] == radii[4]
            && radii[0] == radii[5]
            && radii[0] == radii[6]
            && radii[0] == radii[7];
    }

    protected int alpha = 255;

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }



    public static void drawStroke(Canvas canvas, float left, float top, float right, float bottom,
                                     float[] radii, float strokeWidth, boolean isTop, Paint paint) {

        final boolean radiiAreSame = isTop ?
            radii[0] == radii[1] && radii[1] == radii[2] && radii[2] == radii[3]:
            radii[4] == radii[5] && radii[5] == radii[6] && radii[6] == radii[7];

        final float strokeHalf = strokeWidth / 2f;

        if (isTop) {
            if (radiiAreSame) {
                canvas.save();
                if (canvas.clipRect(left, top, right, MathUtils.clamp(top + radii[0] * 2, top, bottom))) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top + strokeHalf,
                            right + strokeHalf,
                            bottom + strokeHalf,
                            radii[0], radii[0],
                            paint
                    );
                }
                canvas.restore();
            }
        } else {
            if (radiiAreSame) {
                canvas.save();
                if (canvas.clipRect(left, MathUtils.clamp(bottom - radii[4] * 2, top, bottom), right, bottom)) {
                    canvas.drawRoundRect(
                            left - strokeHalf,
                            top - strokeHalf,
                            right + strokeHalf,
                            bottom - strokeHalf,
                            radii[4], radii[4],
                            paint
                    );
                }
                canvas.restore();
            }
        }
    }

    public static void drawStroke(Canvas canvas, RectF rect,
                                     float radii, float strokeWidth, boolean isTop, Paint paint) {
        drawStroke(canvas, rect.left, rect.top, rect.right, rect.bottom, radii, strokeWidth, isTop, paint);
    }

    public static void drawStroke(Canvas canvas, float left, float top, float right, float bottom,
                                     float radii, float strokeWidth, boolean isTop, Paint paint) {
        final float strokeHalf = strokeWidth / 2f;
        canvas.save();
        if (isTop) {
            if (canvas.clipRect(left - strokeHalf, top, right + strokeHalf, MathUtils.clamp(top + radii, top, bottom))) {
                canvas.drawRoundRect(
                    left - strokeHalf,
                    top + strokeHalf,
                    right + strokeHalf,
                    bottom + strokeHalf,
                    radii, radii,
                    paint
                );
            }
        } else {
            if (canvas.clipRect(left - strokeHalf, MathUtils.clamp(bottom - radii, top, bottom), right + strokeHalf, bottom)) {
                canvas.drawRoundRect(
                    left - strokeHalf,
                    top - strokeHalf,
                    right + strokeHalf,
                    bottom - strokeHalf,
                    radii, radii,
                    paint
                );
            }
        }
        canvas.restore();
    }

    protected boolean inAppKeyboardOptimization;
    public void enableInAppKeyboardOptimization() {
        inAppKeyboardOptimization = true;
    }




    /* Universal */

    protected void drawSource(Canvas canvas, BlurredBackgroundSource source) {
        if (boundProps.boundsWithPadding.isEmpty()) {
            return;
        }

        if (source instanceof BlurredBackgroundSourceColor) {
            drawSourceColor(canvas, (BlurredBackgroundSourceColor) source);
        } else if (source instanceof BlurredBackgroundSourceBitmap) {
            drawSourceBitmap(canvas, (BlurredBackgroundSourceBitmap) source);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && source instanceof BlurredBackgroundSourceRenderNode) {
            drawSourceRenderNode(canvas, (BlurredBackgroundSourceRenderNode) source);
        } else if (source instanceof BlurredBackgroundSourceWrapped) {
            drawSource(canvas, ((BlurredBackgroundSourceWrapped) source).getSource());
        }
    }

    private final Paint backgroundColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint backgroundBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundBitmapFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundBitmapShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix bitmapShaderMatrix = new Matrix();
    private final WeakReference<Bitmap> bitmapInShader = new WeakReference<>(null);
    private @Nullable BitmapShader bitmapShader;

    {
        backgroundBitmapShadowPaint.setColor(0);
        backgroundBitmapPaint.setFilterBitmap(true);
    }

    private void drawSourceColor(Canvas canvas, BlurredBackgroundSourceColor source) {
        final int backgroundColor = Theme.multAlpha(ColorUtils.compositeColors(this.backgroundColor, source.getColor()), alpha / 255f);
        final int shadowColor = Theme.multAlpha(this.shadowColor, alpha / 255f);

        backgroundColorPaint.setShadowLayer(dpf2(1), 0f, dpf2(1 / 3f), shadowColor);
        backgroundColorPaint.setColor(backgroundColor);
        boundProps.draw(canvas, backgroundColorPaint);

        drawStrokeInternalIfNeeded(canvas);
    }

    private void drawSourceBitmap(Canvas canvas, BlurredBackgroundSourceBitmap source) {
        final Bitmap newBitmap = source.getBitmap();
        final Bitmap oldBitmap = bitmapInShader.get();

        if (newBitmap != oldBitmap) {
            if (newBitmap != null && !newBitmap.isRecycled()) {
                bitmapShader = new BitmapShader(newBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                backgroundBitmapPaint.setShader(bitmapShader);
            } else {
                bitmapShader = null;
                backgroundBitmapPaint.setShader(null);
            }
        }

        if (Color.alpha(shadowColor) > 0 && alpha == 255) {
            backgroundBitmapShadowPaint.setShadowLayer(dpf2(1), 0f, dpf2(1 / 3f), shadowColor);
            boundProps.draw(canvas, backgroundBitmapShadowPaint);
        }

        if (bitmapShader != null && newBitmap != null && !newBitmap.isRecycled() && alpha > 0) {
            bitmapShaderMatrix.set(source.getMatrix());
            bitmapShaderMatrix.postTranslate(-sourceOffsetX, -sourceOffsetY);
            bitmapShader.setLocalMatrix(bitmapShaderMatrix);
            backgroundBitmapPaint.setAlpha(alpha);
            boundProps.draw(canvas, backgroundBitmapPaint);
        }

        final int backgroundColor = Theme.multAlpha(this.backgroundColor, alpha / 255f);
        if (Color.alpha(backgroundColor) > 0) {
            backgroundBitmapFill.setColor(backgroundColor);
            boundProps.draw(canvas, backgroundBitmapFill);
        }

        drawStrokeInternalIfNeeded(canvas);
    }

    private void drawStrokeInternalIfNeeded(Canvas canvas) {
        final int strokeColorTop = Theme.multAlpha(this.strokeColorTop, alpha / 255f);
        final int strokeColorBottom = Theme.multAlpha(this.strokeColorBottom, alpha / 255f);

        if (Color.alpha(strokeColorTop) > 0) {
            paintStrokeFill.setColor(strokeColorTop);
            canvas.drawPath(boundProps.strokePathTop, paintStrokeFill);
        }
        if (Color.alpha(strokeColorBottom) > 0) {
            paintStrokeFill.setColor(strokeColorBottom);
            canvas.drawPath(boundProps.strokePathBottom, paintStrokeFill);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void drawSourceRenderNode(Canvas canvas, BlurredBackgroundSourceRenderNode source) {
        if (!canvas.isHardwareAccelerated()) {
            drawSource(canvas, source.getFallbackSource());
            return;
        }

        // todo: move from drawableRenderNode
    }
}
