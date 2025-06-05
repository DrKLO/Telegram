package org.telegram.ui.Components.Paint;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES20;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Size;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.microedition.khronos.opengles.GL10;

public class Painting {

    public interface PaintingDelegate {
        void contentChanged();
        void strokeCommited();
        UndoStore requestUndoStore();
        DispatchQueue requestDispatchQueue();
    }

    public static class PaintingData {
        public Bitmap bitmap;
        public ByteBuffer data;

        PaintingData(Bitmap b, ByteBuffer buffer) {
            bitmap = b;
            data = buffer;
        }
    }

    private PaintingDelegate delegate;
    private Path activePath;
    private Shape activeShape;
    private Shape helperShape;
    private RenderState renderState;
    private RenderView renderView;
    private Size size;
    private RectF activeStrokeBounds;
    private Brush brush;
    private HashMap<Integer, Texture> brushTextures = new HashMap<>();
    private Texture bitmapTexture;
    private Texture originalBitmapTexture;
    private ByteBuffer vertexBuffer;
    private ByteBuffer textureBuffer;
    private int reusableFramebuffer;
    private int paintTexture;
    private int helperTexture;
    private Map<String, Shader> shaders;
    private int suppressChangesCounter;
    private int[] buffers = new int[1];
    private ByteBuffer dataBuffer;

    private boolean paused;
    private Slice backupSlice;

    private float[] projection;
    private float[] renderProjection;

    private Texture bluredTexture;
    private Bitmap imageBitmap;
    private int imageBitmapRotation;
    private Bitmap bluredBitmap;

    private Texture bitmapBlurTexture;
    public boolean hasBlur;

    private final BlurringShader.BlurManager blurManager;

    public Painting(Size sz, Bitmap originalBitmap, int originalRotation, BlurringShader.BlurManager blurManager) {
        renderState = new RenderState();
        this.blurManager = blurManager;

        size = sz;
        imageBitmap = originalBitmap;
        imageBitmapRotation = originalRotation;

        dataBuffer = ByteBuffer.allocateDirect((int) size.width * (int) size.height * 4);

        projection = GLMatrix.LoadOrtho(0, size.width, 0, size.height, -1.0f, 1.0f);

        if (vertexBuffer == null) {
            vertexBuffer = ByteBuffer.allocateDirect(8 * 4);
            vertexBuffer.order(ByteOrder.nativeOrder());
        }
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(size.width);
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(0.0f);
        vertexBuffer.putFloat(size.height);
        vertexBuffer.putFloat(size.width);
        vertexBuffer.putFloat(size.height);
        vertexBuffer.rewind();

        if (textureBuffer == null) {
            textureBuffer = ByteBuffer.allocateDirect(8 * 4);
            textureBuffer.order(ByteOrder.nativeOrder());
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(0.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.putFloat(1.0f);
            textureBuffer.rewind();
        }
    }

    public boolean masking = false;

    public Painting asMask() {
        this.masking = true;
        return this;
    }

    public void setDelegate(PaintingDelegate paintingDelegate) {
        delegate = paintingDelegate;
    }

    public void setRenderView(RenderView view) {
        renderView = view;
    }

    public Size getSize() {
        return size;
    }

    public RectF getBounds() {
        return new RectF(0.0f, 0.0f, size.width, size.height);
    }

    private boolean isSuppressingChanges() {
        return suppressChangesCounter > 0;
    }

    private void beginSuppressingChanges() {
        suppressChangesCounter++;
    }

    private void endSuppressingChanges() {
        suppressChangesCounter--;
    }

    public void setBitmap(Bitmap bitmap) {
        if (bitmapTexture == null) {
            bitmapTexture = new Texture(bitmap);
        }
    }

    public void setBitmap(Bitmap bitmap, Bitmap blurBitmap) {
        if (bitmapTexture == null) {
            bitmapTexture = new Texture(bitmap);
        }
        if (bitmapBlurTexture == null) {
            bitmapBlurTexture = new Texture(blurBitmap);
        }
        if (masking && originalBitmapTexture == null) {
            originalBitmapTexture = new Texture(imageBitmap);
        }
    }

    private boolean helperShown;
    private float helperAlpha, helperApplyAlpha;
    private ValueAnimator helperAnimator, helperApplyAnimator;

    public void setHelperShape(Shape shape) {
        if (helperApplyAnimator != null) {
            return;
        }
        renderView.performInContext(() -> {
            if (shape != null && helperTexture == 0) {
                helperTexture = Texture.generateTexture(size);
            }

            if (helperShown != (shape != null)) {
                helperShown = shape != null;
                if (helperAnimator != null) {
                    helperAnimator.cancel();
                    helperAnimator = null;
                }

                helperAnimator = ValueAnimator.ofFloat(helperAlpha, helperShown ? 1f : 0f);
                helperAnimator.addUpdateListener(anm -> {
                    renderView.performInContext(() -> {
                        helperAlpha = (float) anm.getAnimatedValue();
                        if (delegate != null) {
                            delegate.contentChanged();
                        }
                    });
                });
                helperAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        helperAnimator = null;
                        renderView.performInContext(() -> {
                            if (delegate != null) {
                                delegate.contentChanged();
                            }
                        });
                    }
                });
                helperAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                helperAnimator.start();

                helperShape = shape;
                if (delegate != null) {
                    delegate.contentChanged();
                }

                if (helperShown) {
                    BotWebViewVibrationEffect.SELECTION_CHANGE.vibrate();
                }
            } else if (shape != helperShape) {
                helperShape = shape;
                if (delegate != null) {
                    delegate.contentChanged();
                }
            }
        });
    }

    public boolean applyHelperShape() {
        if (helperShape == null || !helperShown || helperTexture == 0) {
            return false;
        }

        if (helperApplyAnimator != null) {
            helperApplyAnimator.cancel();
        }
        helperApplyAnimator = ValueAnimator.ofFloat(0, 1);
        helperApplyAnimator.addUpdateListener(anm -> {
            renderView.performInContext(() -> {
                helperApplyAlpha = (float) anm.getAnimatedValue();
                if (delegate != null) {
                    delegate.contentChanged();
                }
            });
        });
        helperApplyAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                renderView.performInContext(() -> {
                    if (helperShape == null) {
                        helperApplyAnimator = null;
                        return;
                    }

                    int color = renderView.getCurrentColor();
                    paintStrokeInternal(activePath, false, false);
                    Slice pathSlice = commitPathInternal(activePath, color, new RectF(activeStrokeBounds));
                    clearStrokeInternal();

                    Shape shape = helperShape;
                    shape.getBounds(activeStrokeBounds = new RectF());
                    Slice shapeSlice = commitShapeInternal(shape, color, new RectF(activeStrokeBounds));

                    restoreSliceInternal(shapeSlice, false);
                    restoreSliceInternal(pathSlice, false);

                    commitShapeInternal(shape, color, null);

                    helperShape = null;
                    helperApplyAlpha = 0;
                    helperApplyAnimator = null;
                });
            }
        });
        helperApplyAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        helperApplyAnimator.setDuration(350);
        helperApplyAnimator.start();

        BotWebViewVibrationEffect.IMPACT_RIGID.vibrate();
        return true;
    }

    public void paintShape(final Shape shape, Runnable action) {
        if (shape == null) {
            return;
        }
        renderView.performInContext(() -> {
            activeShape = shape;

            if (activeStrokeBounds == null) {
                activeStrokeBounds = new RectF();
            }
            activeShape.getBounds(activeStrokeBounds);
            if (delegate != null) {
                delegate.contentChanged();
            }

            if (action != null) {
                action.run();
            }
        });
    }

    public void paintStroke(final Path path, final boolean clearBuffer, final boolean clearAll, final Runnable action) {
        if (helperApplyAnimator != null) {
            return;
        }
        renderView.performInContext(() -> {
            paintStrokeInternal(path, clearBuffer, clearAll);

            if (action != null) {
                action.run();
            }
        });
    }

    private void paintStrokeInternal(final Path path, final boolean clearBuffer, final boolean clearAll) {
        activePath = path;
        if (path == null) {
            return;
        }

        RectF bounds = null;

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, getPaintTexture(), 0);

        Utils.HasGLError();

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glViewport(0, 0, (int) size.width, (int) size.height);

            if (clearBuffer) {
                GLES20.glClearColor(0, 0, 0, 0);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            }

            if (shaders == null) {
                return;
            }

            Brush brush = path.getBrush();
            Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_BRUSH));
            if (shader == null) {
                return;
            }

            GLES20.glUseProgram(shader.program);
            Texture brushTexture = brushTextures.get(brush.getStampResId());
            if (brushTexture == null) {
                brushTexture = new Texture(brush.getStamp());
                brushTextures.put(brush.getStampResId(), brushTexture);
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, brushTexture.texture());
            GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(projection));
            GLES20.glUniform1i(shader.getUniform("texture"), 0);

            if (!clearAll) {
                renderState.viewportScale = renderView.getScaleX();
            } else {
                renderState.viewportScale = 1f;
            }
            bounds = Render.RenderPath(path, renderState, clearAll);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (delegate != null) {
            delegate.contentChanged();
        }

        if (activeStrokeBounds != null) {
            activeStrokeBounds.union(bounds);
        } else {
            activeStrokeBounds = bounds;
        }
    }

    public void commitShape(Shape shape, final int color) {
        if (shape == null || shaders == null) {
            return;
        }
        renderView.performInContext(() -> {
            commitShapeInternal(shape, color, activeStrokeBounds);
            activeStrokeBounds = null;
        });
    }

    private Slice commitShapeInternal(Shape shape, int color, RectF bounds) {
        Brush brush = shape.brush;
        if (brush == null) {
            brush = this.brush;
        }

        Slice undoSlice = registerUndo(bounds, blurManager != null && brush instanceof Brush.Blurer);

        beginSuppressingChanges();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, getTexture(), 0);

        GLES20.glViewport(0, 0, (int) size.width, (int) size.height);

        Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_COMPOSITE));
        if (shader == null) {
            return null;
        }

        GLES20.glUseProgram(shader.program);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(projection));
        GLES20.glUniform1i(shader.getUniform("texture"), 0);
        GLES20.glUniform1i(shader.getUniform("mask"), 1);
        Shader.SetColorUniform(shader.getUniform("color"), color);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPaintTexture());

        if (brush instanceof Brush.Blurer && bluredTexture != null) {
            GLES20.glUniform1i(shader.getUniform("blured"), 2);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bluredTexture.texture());
        }

        if (brush instanceof Brush.Shape) {
            GLES20.glUniform1i(shader.getUniform("type"), shape.getType());
            GLES20.glUniform2f(shader.getUniform("resolution"), size.width, size.height);
            GLES20.glUniform2f(shader.getUniform("center"), shape.centerX, shape.centerY);
            GLES20.glUniform2f(shader.getUniform("radius"), shape.radiusX, shape.radiusY);
            GLES20.glUniform1f(shader.getUniform("thickness"), shape.thickness);
            GLES20.glUniform1f(shader.getUniform("rounding"), shape.rounding);
            GLES20.glUniform2f(shader.getUniform("middle"), shape.middleX, shape.middleY);
            GLES20.glUniform1f(shader.getUniform("rotation"), shape.rotation);
            GLES20.glUniform1i(shader.getUniform("fill"), shape.fill ? 1 : 0);
            GLES20.glUniform1f(shader.getUniform("arrowTriangleLength"), shape.arrowTriangleLength);
            GLES20.glUniform1i(shader.getUniform("composite"), 1);
            GLES20.glUniform1i(shader.getUniform("clear"), 0);
        }

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ZERO);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (delegate != null && !isSuppressingChanges()) {
            delegate.contentChanged();
        }

        endSuppressingChanges();

        renderState.reset();

        helperApplyAlpha = 0f;
        helperShown = false;
        helperAlpha = 0f;
        helperShape = null;

        activePath = null;
        activeShape = null;

        return undoSlice;
    }

    public void commitPath(final Path path, final int color) {
        commitPath(path, color, true, null);
    }

    public void commitPath(final Path path, final int color, final boolean registerUndo, Runnable action) {
        if (shaders == null || brush == null) {
            return;
        }
        renderView.performInContext(() -> {
            commitPathInternal(path, color, registerUndo ? activeStrokeBounds : null);

            if (registerUndo) {
                activeStrokeBounds = null;
            }

            if (action != null) {
                action.run();
            }
        });
    }

    private Slice commitPathInternal(final Path path, final int color, RectF bounds) {
        Brush brush = this.brush;
        if (path != null) {
            brush = path.getBrush();
        }

        Slice undoSlice;
        if (blurManager != null && (brush instanceof Brush.Blurer || brush instanceof Brush.Eraser)) {
            undoSlice = registerDoubleUndo(bounds, hasBlur);
            hasBlur = brush instanceof Brush.Blurer;
        } else {
            undoSlice = registerUndo(bounds, false);
        }

        beginSuppressingChanges();

        int count = 1;
        if (blurManager != null && (brush instanceof Brush.Blurer || brush instanceof Brush.Eraser)) {
            // - eraser erases both from def. texture and blur texture
            // - blurer blurs in blur texture and erases anything else in def. texture
            count = 2;
        }
        for (int a = 0; a < count; ++a) {

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
            int tex = getTexture();
            if (blurManager != null && (brush instanceof Brush.Blurer && a == 0 || brush instanceof Brush.Eraser && a == 1)) {
                tex = bitmapBlurTexture != null ? bitmapBlurTexture.texture() : 0;
            }
            if (a == 1 && brush instanceof Brush.Blurer) {
                brush = new Brush.Eraser();
            }
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0);

            GLES20.glViewport(0, 0, (int) size.width, (int) size.height);

            Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_COMPOSITE));
            if (shader == null) {
                return null;
            }

            GLES20.glUseProgram(shader.program);

            GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(projection));
            GLES20.glUniform1i(shader.getUniform("texture"), 0);
            GLES20.glUniform1i(shader.getUniform("mask"), 1);
            Shader.SetColorUniform(shader.getUniform("color"), ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * brush.getOverrideAlpha())));

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPaintTexture());

            Object lock = null;
            if (brush instanceof Brush.Blurer) {
                GLES20.glUniform1i(shader.getUniform("blured"), 2);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                if (blurManager != null) {
                    lock = blurManager.getTextureLock();
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurManager.getTexture());
                } else {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bluredTexture.texture());
                }
            }

            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ZERO);

            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
            GLES20.glEnableVertexAttribArray(0);
            GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(1);

            if (lock != null) {
                synchronized (lock) {
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                }
            } else {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (!isSuppressingChanges() && delegate != null) {
            delegate.contentChanged();
        }

        endSuppressingChanges();
        renderState.reset();

        activePath = null;
        activeShape = null;

        return undoSlice;
    }

    public void clearStroke() {
        clearStroke(null);
    }

    public void clearStroke(Runnable action) {
        renderView.performInContext(() -> {
            clearStrokeInternal();

            if (action != null) {
                action.run();
            }
        });
    }

    public void clearShape() {
        renderView.performInContext(() -> {
            activeShape = null;
            if (delegate != null) {
                delegate.contentChanged();
            }
        });
    }

    private void clearStrokeInternal() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, getReusableFramebuffer());
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, getPaintTexture(), 0);

        Utils.HasGLError();

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glViewport(0, 0, (int) size.width, (int) size.height);
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (delegate != null) {
            delegate.contentChanged();
        }
        renderState.reset();
        activeStrokeBounds = null;
        activePath = null;

        helperApplyAlpha = 0f;
    }

    private Slice registerUndo(RectF rect, boolean blurTex) {
        if (rect == null) {
            return null;
        }

        boolean intersect = rect.setIntersect(rect, getBounds());
        if (!intersect) {
            return null;
        }

        final Slice slice = new Slice(getPaintingData(rect, true, blurTex, false).data, blurTex ? 1 : 0, rect, delegate.requestDispatchQueue());
        delegate.requestUndoStore().registerUndo(UUID.randomUUID(), () -> restoreSlice(slice));

        return slice;
    }

    private Slice registerDoubleUndo(RectF rect, boolean hadBlur) {
        if (rect == null) {
            return null;
        }

        boolean intersect = rect.setIntersect(rect, getBounds());
        if (!intersect) {
            return null;
        }

        final Slice slice1 = new Slice(getPaintingData(rect, true, false, false).data, 0, rect, delegate.requestDispatchQueue());
        final Slice slice2 = new Slice(getPaintingData(rect, true, true, false).data, 1, rect, delegate.requestDispatchQueue());
        delegate.requestUndoStore().registerUndo(UUID.randomUUID(), () -> {
            restoreSlice(slice1);
            restoreSlice(slice2);
            hasBlur = hadBlur;
        });

        return slice1; // ehm...
    }

    private void restoreSlice(final Slice slice) {
        renderView.performInContext(() -> {
            restoreSliceInternal(slice, true);
        });
    }

    private void restoreSliceInternal(final Slice slice, boolean forget) {
        if (slice == null) {
            return;
        }

        ByteBuffer buffer = slice.getData();

        int tex = getTexture();
        if (slice.getTexture() == 1 && bitmapBlurTexture != null) {
            tex = bitmapBlurTexture.texture();
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, slice.getX(), slice.getY(), slice.getWidth(), slice.getHeight(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        if (!isSuppressingChanges() && delegate != null) {
            delegate.contentChanged();
        }

        if (forget) {
            slice.cleanResources();
        }
    }

    public void setRenderProjection(float[] proj) {
        renderProjection = proj;
    }

    public void render() {
        if (shaders == null) {
            return;
        }

        if (bitmapBlurTexture != null) {
            renderBlur();
        }

        if (activePath != null) {
            renderBlitPath(getPaintTexture(), activePath, 1f - .5f * helperAlpha - .5f * helperApplyAlpha);
        } else if (activeShape != null) {
            renderBlitShape(getTexture(), getPaintTexture(), activeShape, 1f);
        } else {
            renderBlit(getTexture(), 1f);
        }

        if (helperTexture != 0 && helperShape != null && helperAlpha > 0) {
            renderBlitShape(helperTexture, getPaintTexture(), helperShape, .5f * helperAlpha + .5f * helperApplyAlpha);
        }
    }

    private void renderBlur() {
        if (blurManager == null || bitmapBlurTexture == null || !hasBlur) {
            return;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        Shader shader = shaders.get("videoBlur");
        if (shader == null) {
            return;
        }

        GLES20.glUseProgram(shader.program);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(renderProjection));
        GLES20.glUniform1f(shader.getUniform("flipy"), 0f);

        GLES20.glUniform1i(shader.getUniform("texture"), 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapBlurTexture.texture());
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

        GLES20.glUniform1i(shader.getUniform("blured"), 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurManager.getTexture());

        if (activePath != null && this.brush instanceof Brush.Eraser) {
            GLES20.glUniform1f(shader.getUniform("eraser"), 1f);
            GLES20.glUniform1i(shader.getUniform("mask"), 2);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getPaintTexture());
        } else {
            GLES20.glUniform1f(shader.getUniform("eraser"), 0f);
        }

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ZERO);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        synchronized (blurManager.getTextureLock()) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
    }

    private void renderBlitShape(int toTexture, int mask, Shape shape, float alpha) {
        if (shape == null) {
            return;
        }
        Brush brush = this.brush;
        if (shape.brush != null && toTexture == helperTexture) {
            brush = shape.brush;
        }
        if (brush == null || renderView == null) {
            return;
        }

        Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_BLIT));
        if (shader == null) {
            return;
        }

        GLES20.glUseProgram(shader.program);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(renderProjection));
        GLES20.glUniform1i(shader.getUniform("texture"), 0);
        GLES20.glUniform1i(shader.getUniform("mask"), 1);
        int color = renderView.getCurrentColor();
        color = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * alpha));
        Shader.SetColorUniform(shader.getUniform("color"), color);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, toTexture);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mask);

        if (brush instanceof Brush.Shape) {
            GLES20.glUniform1i(shader.getUniform("type"), ((Brush.Shape) brush).getShapeShaderType());
            GLES20.glUniform2f(shader.getUniform("resolution"), size.width, size.height);
            GLES20.glUniform2f(shader.getUniform("center"), shape.centerX, shape.centerY);
            GLES20.glUniform2f(shader.getUniform("radius"), shape.radiusX, shape.radiusY);
            GLES20.glUniform1f(shader.getUniform("thickness"), shape.thickness);
            GLES20.glUniform1f(shader.getUniform("rounding"), shape.rounding);
            GLES20.glUniform2f(shader.getUniform("middle"), shape.middleX, shape.middleY);
            GLES20.glUniform1f(shader.getUniform("rotation"), shape.rotation);
            GLES20.glUniform1i(shader.getUniform("fill"), shape.fill ? 1 : 0);
            GLES20.glUniform1f(shader.getUniform("arrowTriangleLength"), shape.arrowTriangleLength);
            GLES20.glUniform1i(shader.getUniform("composite"), 0);
            GLES20.glUniform1i(shader.getUniform("clear"), shape == helperShape ? 1 : 0);
        }

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        Utils.HasGLError();
    }

    private void renderBlitPath(int mask, Path path, float alpha) {
        if (path == null) {
            return;
        }
        Brush brush = path.getBrush();
        if (brush == null) {
            brush = this.brush;
        }

        final boolean masking = this.masking && (brush instanceof Brush.Radial || brush instanceof Brush.Eraser);
        Shader shader = shaders.get(brush.getShaderName(Brush.PAINT_TYPE_BLIT) + (masking ? "_masking" : ""));
        if (shader == null) {
            return;
        }

        GLES20.glUseProgram(shader.program);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(renderProjection));
        GLES20.glUniform1i(shader.getUniform("texture"), 0);
        GLES20.glUniform1i(shader.getUniform("mask"), 1);
        int color = path.getColor();
        color = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * brush.getOverrideAlpha() * alpha));
        Shader.SetColorUniform(shader.getUniform("color"), color);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mask);

        if (masking) {
            GLES20.glUniform1i(shader.getUniform("otexture"), 2);
            GLES20.glUniform1f(shader.getUniform("preview"), 0.4f);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, originalBitmapTexture.texture());
        }

        Object lock = null;
        if (brush instanceof Brush.Blurer) {
            GLES20.glUniform1i(shader.getUniform("blured"), 2);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            if (blurManager != null) {
                lock = blurManager.getTextureLock();
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurManager.getTexture());
            } else if (bluredTexture != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bluredTexture.texture());
            }
        }
        
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        if (lock != null) {
            synchronized (lock) {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }
        } else {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        Utils.HasGLError();
    }

    private void renderBlit(int texture, float alpha) {
        Shader shader = shaders.get(masking ? "maskingBlit" : "blit");
        if (texture == 0 || shader == null) {
            return;
        }

        GLES20.glUseProgram(shader.program);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(renderProjection));
        GLES20.glUniform1f(shader.getUniform("alpha"), alpha);

        if (masking) {
            GLES20.glUniform1i(shader.getUniform("texture"), 1);
            GLES20.glUniform1i(shader.getUniform("mask"), 0);
            GLES20.glUniform1f(shader.getUniform("preview"), 0.4f);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, originalBitmapTexture.texture());
        } else {
            GLES20.glUniform1i(shader.getUniform("texture"), 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        }

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        Utils.HasGLError();
    }

    public PaintingData getPaintingData(RectF rect, boolean undo) {
        return getPaintingData(rect, undo, false, false);
    }

    public PaintingData getPaintingData(RectF rect, boolean undo, boolean onlyBlur, boolean includeBlur) {
        int minX = (int) rect.left;
        int minY = (int) rect.top;
        int width = (int) rect.width();
        int height = (int) rect.height();

        GLES20.glGenFramebuffers(1, buffers, 0);
        int framebuffer = buffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);

        GLES20.glGenTextures(1, buffers, 0);
        int texture = buffers[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);

        GLES20.glViewport(0, 0, (int) size.width, (int) size.height);

        if (shaders == null) {
            return null;
        }
        Shader shader = shaders.get(undo ? "nonPremultipliedBlit" : (masking ? "maskingBlit" : "blit"));
        if (shader == null) {
            return null;
        }
        GLES20.glUseProgram(shader.program);

        Matrix translate = new Matrix();
        translate.preTranslate(-minX, -minY);
        float[] effective = GLMatrix.LoadGraphicsMatrix(translate);
        float[] finalProjection = GLMatrix.MultiplyMat4f(projection, effective);

        GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(finalProjection));

        if (!undo && masking) {
            GLES20.glUniform1i(shader.getUniform("texture"), 1);
            GLES20.glUniform1i(shader.getUniform("mask"), 0);
            GLES20.glUniform1f(shader.getUniform("preview"), 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, onlyBlur && bitmapBlurTexture != null ? bitmapBlurTexture.texture() : getTexture());

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, originalBitmapTexture.texture());
        } else {
            GLES20.glUniform1i(shader.getUniform("texture"), 0);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, onlyBlur && bitmapBlurTexture != null ? bitmapBlurTexture.texture() : getTexture());
        }

        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ZERO);

        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(1);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if (includeBlur && !onlyBlur) {
            shader = shaders.get("videoBlur");
            if (shader != null && blurManager != null) {
                GLES20.glUseProgram(shader.program);

                GLES20.glUniformMatrix4fv(shader.getUniform("mvpMatrix"), 1, false, FloatBuffer.wrap(finalProjection));
                GLES20.glUniform1f(shader.getUniform("flipy"), 0f);

                GLES20.glUniform1i(shader.getUniform("texture"), 0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapBlurTexture.texture());
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

                GLES20.glUniform1i(shader.getUniform("blured"), 1);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurManager.getTexture());

                GLES20.glUniform1f(shader.getUniform("eraser"), 0f);

                GLES20.glUniform1i(shader.getUniform("mask"), 2);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, getTexture());

                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

                GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
                GLES20.glEnableVertexAttribArray(0);
                GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(1);

                synchronized (blurManager.getTextureLock()) {
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                }
            }
        }

        dataBuffer.limit(width * height * 4);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, dataBuffer);

        PaintingData data;
        if (undo) {
            data = new PaintingData(null, dataBuffer);
        } else {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(dataBuffer);

            data = new PaintingData(bitmap, null);
        }

        dataBuffer.rewind();

        buffers[0] = framebuffer;
        GLES20.glDeleteFramebuffers(1, buffers, 0);

        buffers[0] = texture;
        GLES20.glDeleteTextures(1, buffers, 0);

        return data;
    }

    private Paint imageBitmapPaint;
    public void setBrush(Brush value) {
        brush = value;

        if (value instanceof Brush.Blurer && imageBitmap != null && blurManager == null) {
            int w = imageBitmap.getWidth(), h = imageBitmap.getHeight();
            if (imageBitmapRotation == 90 || imageBitmapRotation == 270 || imageBitmapRotation == -90) {
                int pH = h;
                h = w;
                w = pH;
            }
            float SCALE = 8;
            if (bluredBitmap == null) {
                bluredBitmap = Bitmap.createBitmap((int) (w / SCALE), (int) (h / SCALE), Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(bluredBitmap);
            canvas.save();
            canvas.scale(1f / SCALE, 1f / SCALE);
            if (imageBitmapPaint != null) {
                imageBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            canvas.save();
            canvas.rotate(imageBitmapRotation);
            if (imageBitmapRotation == 90) {
                canvas.translate(0, -w);
            } else if (imageBitmapRotation == 180) {
                canvas.translate(-w, -h);
            } else if (imageBitmapRotation == 270) {
                canvas.translate(-h, 0);
            }
            canvas.drawBitmap(imageBitmap, 0, 0, imageBitmapPaint);
            canvas.restore();
            if (renderView != null) {
                Bitmap bitmap = renderView.getResultBitmap(false, false);
                if (bitmap != null) {
                    canvas.scale((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
                    canvas.drawBitmap(bitmap, 0, 0, imageBitmapPaint);
                    bitmap.recycle();
                }
            }
            Utilities.stackBlurBitmap(bluredBitmap, (int) SCALE);
            if (bluredTexture != null) {
                bluredTexture.cleanResources(false);
            }
            bluredTexture = new Texture(bluredBitmap);
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void onPause(final Runnable completionRunnable) {
        renderView.performInContext(() -> {
            paused = true;
            PaintingData data = getPaintingData(getBounds(), true, false, false); // TODO: save also blur bitmap?
            backupSlice = new Slice(data.data, 0, getBounds(), delegate.requestDispatchQueue());

            cleanResources(false);

            if (completionRunnable != null)
                completionRunnable.run();
        });
    }

    public void onResume() {
        restoreSlice(backupSlice);
        backupSlice = null;
        paused = false;
    }

    public void cleanResources(boolean recycle) {
        if (reusableFramebuffer != 0) {
            buffers[0] = reusableFramebuffer;
            GLES20.glDeleteFramebuffers(1, buffers, 0);
            reusableFramebuffer = 0;
        }

        if (bitmapTexture != null) {
            bitmapTexture.cleanResources(recycle);
        }
        if (bitmapBlurTexture != null) {
            bitmapBlurTexture.cleanResources(recycle);
        }

        if (paintTexture != 0) {
            buffers[0] = paintTexture;
            GLES20.glDeleteTextures(1, buffers, 0);
            paintTexture = 0;
        }

        for (Texture texture : brushTextures.values()) {
            if (texture != null) {
                texture.cleanResources(true);
            }
        }
        brushTextures.clear();

        if (helperTexture != 0) {
            buffers[0] = helperTexture;
            GLES20.glDeleteTextures(1, buffers, 0);
            helperTexture = 0;
        }

        if (bluredTexture != null) {
            bluredTexture.cleanResources(true);
        }
        if (originalBitmapTexture != null) {
            originalBitmapTexture.cleanResources(true);
        }

        if (shaders != null) {
            for (Shader shader : shaders.values()) {
                shader.cleanResources();
            }
            shaders = null;
        }
    }

    private int getReusableFramebuffer() {
        if (reusableFramebuffer == 0) {
            int[] buffers = new int[1];
            GLES20.glGenFramebuffers(1, buffers, 0);
            reusableFramebuffer = buffers[0];

            Utils.HasGLError();
        }
        return reusableFramebuffer;
    }

    private int getTexture() {
        if (bitmapTexture != null) {
            return bitmapTexture.texture();
        }
        return 0;
    }

    private int getPaintTexture() {
        if (paintTexture == 0) {
            paintTexture = Texture.generateTexture(size);
        }
        return paintTexture;
    }

    public void setupShaders() {
        shaders = ShaderSet.setup();
    }
}
