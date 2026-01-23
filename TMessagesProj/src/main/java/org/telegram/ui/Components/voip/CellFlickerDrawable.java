package org.telegram.ui.Components.voip;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SvgHelper;

public class CellFlickerDrawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader gradientShader;

    private Paint paintOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader gradientShader2;
    int size;

    int parentWidth;
    public float progress;
    long lastUpdateTime;

    Matrix matrix = new Matrix();

    public boolean repeatEnabled = true;
    public boolean drawFrame = true;
    public boolean frameInside = false;
    public float repeatProgress = 1.2f;
    public float animationSpeedScale = 1f;
    View parentView;
    Runnable onRestartCallback;

    public CellFlickerDrawable() {
        this(64, 204, 160);
    }

    public CellFlickerDrawable(int a1, int a) {
        this(a1, a, 160);
    }
    public CellFlickerDrawable(int a1, int a2, int size) {
        this.size = AndroidUtilities.dp(size);
        gradientShader = new LinearGradient(0, 0, this.size, 0, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, a1), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        gradientShader2 = new LinearGradient(0, 0, this.size, 0, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, a2), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        paint.setShader(gradientShader);
        paintOutline.setShader(gradientShader2);
        paintOutline.setStyle(Paint.Style.STROKE);
        paintOutline.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void setStrokeWidth(float width) {
        paintOutline.setStrokeWidth(width);
    }

    public void setColors(int color) {
        setColors(color, 64, 204);
    }

    public void setColors(int color, int alpha1, int alpha2) {
        gradientShader = new LinearGradient(0, 0, size, 0, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(color, alpha1), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        gradientShader2 = new LinearGradient(0, 0, size, 0, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(color, alpha2), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
        paint.setShader(gradientShader);
        paintOutline.setShader(gradientShader2);
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }

    public void draw(Canvas canvas, RectF rectF, float rad, View view) {
        update(view);
        canvas.drawRoundRect(rectF, rad, rad, paint);
        if (drawFrame) {
            if (frameInside) {
                rectF.inset(paintOutline.getStrokeWidth() / 2f, paintOutline.getStrokeWidth() / 2f);
            }
            canvas.drawRoundRect(rectF, rad, rad, paintOutline);
        }
    }

    public void draw(Canvas canvas, Path path, View view) {
        update(view);
        canvas.drawPath(path, paint);
        if (drawFrame) {
            canvas.drawPath(path, paintOutline);
        }
    }

    private void update(View view) {
        if (repeatEnabled || progress < 1) {
            if (view != null) {
                view.invalidate();
            }
            long currentTime = System.currentTimeMillis();
            if (lastUpdateTime != 0) {
                long dt = currentTime - lastUpdateTime;
                if (dt > 10) {
                    progress += (dt / 1200f) * animationSpeedScale;
                    if (progress > repeatProgress) {
                        progress = 0;
                        if (onRestartCallback != null) {
                            onRestartCallback.run();
                        }
                    }
                    lastUpdateTime = currentTime;
                }
            } else {
                lastUpdateTime = currentTime;
            }
        }

        float x = (parentWidth + size * 2) * progress - size;
        matrix.reset();
        matrix.setTranslate(x, 0);
        gradientShader.setLocalMatrix(matrix);
        gradientShader2.setLocalMatrix(matrix);
    }

    public void draw(Canvas canvas, GroupCallMiniTextureView view) {
        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime != 0) {
            long dt = currentTime - lastUpdateTime;
            if (dt > 10) {
                progress += dt / 500f;
                if (progress > 4f) {
                    progress = 0;
                    if (onRestartCallback != null) {
                        onRestartCallback.run();
                    }
                }
                lastUpdateTime = currentTime;
            }
        } else {
            lastUpdateTime = currentTime;
        }

        if (progress > 1f) {
            return;
        }

        float x = (parentWidth + size * 2) * progress - size - view.getX();
        matrix.setTranslate(x, 0);
        gradientShader.setLocalMatrix(matrix);
        gradientShader2.setLocalMatrix(matrix);

        AndroidUtilities.rectTmp.set(view.textureView.currentClipHorizontal, view.textureView.currentClipVertical, view.textureView.getMeasuredWidth() - view.textureView.currentClipHorizontal, view.textureView.getMeasuredHeight() - view.textureView.currentClipVertical);
        canvas.drawRect(AndroidUtilities.rectTmp, paint);
        if (drawFrame) {
            if (frameInside) {
                AndroidUtilities.rectTmp.inset(paintOutline.getStrokeWidth() / 2f, paintOutline.getStrokeWidth() / 2f);
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, view.textureView.roundRadius, view.textureView.roundRadius, paintOutline);
        }
    }

    public void setParentWidth(int parentWidth) {
        this.parentWidth = parentWidth;
    }

    public DrawableInterface getDrawableInterface(View parentView, SvgHelper.SvgDrawable drawable) {
        this.parentView = parentView;
        return new DrawableInterface(drawable);
    }


    public void setOnRestartCallback(Runnable runnable) {
        onRestartCallback = runnable;
    }

    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        paintOutline.setAlpha(alpha);
    }

    public class DrawableInterface extends Drawable {

        public float radius;
        SvgHelper.SvgDrawable svgDrawable;

        public DrawableInterface(SvgHelper.SvgDrawable drawable) {
            svgDrawable = drawable;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            setParentWidth(getBounds().width());
            AndroidUtilities.rectTmp.set(getBounds());
            CellFlickerDrawable.this.draw(canvas, AndroidUtilities.rectTmp, radius, null);
            if (svgDrawable != null) {
                svgDrawable.setPaint(paint);
                float x = (parentWidth + size * 2) * progress - size;
                int drawableSize = (int) (parentWidth * 0.5f);
                float s = svgDrawable.getScale(getBounds().width(), getBounds().height());
                matrix.reset();
                matrix.setScale(1f / s, 0, size / 2f, 0);
                matrix.setTranslate(x - svgDrawable.getBounds().left - size / s, 0);

                gradientShader.setLocalMatrix(matrix);
                svgDrawable.setBounds(
                        getBounds().centerX() - drawableSize / 2, getBounds().centerY() - drawableSize / 2,
                        getBounds().centerX() + drawableSize / 2, getBounds().centerY() + drawableSize / 2
                );
                svgDrawable.draw(canvas);
            }
            parentView.invalidate();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            paintOutline.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
