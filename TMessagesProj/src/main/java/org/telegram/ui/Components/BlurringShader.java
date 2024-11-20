package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;

public class BlurringShader {

    private FilterGLThread parent;

    public BlurringShader() {
        this(null);
    }

    public BlurringShader(FilterGLThread parent) {
        this.parent = parent;
    }

    public void invalidate() {
        if (this.parent != null) {
            this.parent.requestRender(false);
        }
    }

    private int width = 1, height = 1;
    private int padding = 0;

    private Program[] program = new Program[2];

    private FloatBuffer posBuffer;
    private FloatBuffer padPosBuffer;
    private FloatBuffer uvBuffer;

    private static class Program {
        int gl;

        int posHandle;
        int uvHandle;
        int matrixHandle;
        int texHandle;
        int szHandle;
        int texSzHandle;
        int gradientTopHandle;
        int gradientBottomHandle;
        int stepHandle;
        int flipyHandle;
        int videoMatrixHandle;
        int hasVideoMatrixHandle;

        public Program(int gl) {
            this.gl = gl;
            posHandle = GLES20.glGetAttribLocation(gl, "p");
            uvHandle = GLES20.glGetAttribLocation(gl, "inputuv");
            matrixHandle = GLES20.glGetUniformLocation(gl, "matrix");
            texHandle = GLES20.glGetUniformLocation(gl, "tex");
            szHandle = GLES20.glGetUniformLocation(gl, "sz");
            texSzHandle = GLES20.glGetUniformLocation(gl, "texSz");
            gradientTopHandle = GLES20.glGetUniformLocation(gl, "gtop");
            gradientBottomHandle = GLES20.glGetUniformLocation(gl, "gbottom");
            stepHandle = GLES20.glGetUniformLocation(gl, "step");
            videoMatrixHandle = GLES20.glGetUniformLocation(gl, "videoMatrix");
            hasVideoMatrixHandle = GLES20.glGetUniformLocation(gl, "hasVideoMatrix");
            flipyHandle = GLES20.glGetUniformLocation(gl, "flipy");
        }
    }

    private boolean setupTransform;
    private final float[] m3x3 = new float[9];
    private final float[] matrix = new float[16];
    private final Object matrixLock = new Object();

    private int gradientTop, gradientBottom;

    private final Object bitmapLock = new Object();
    private ByteBuffer buffer;
    private Bitmap bitmap;
    private boolean bitmapAvailable;

    private final int[] framebuffer = new int[3];
    private final int[] texture = new int[3];

    public boolean setup(float aspectRatio, boolean needsUiBitmap, int padding) {

        final float scale = 1.5f;
        final float density = 9 * scale * 16 * scale;
        width = (int) Math.round(Math.sqrt(density * aspectRatio));
        height = (int) Math.round(Math.sqrt(density / aspectRatio));
        this.padding = padding;

        if (!setupTransform) {
            updateTransform(new Matrix(), 1, 1);
        }

        float[] posCoords = {
            -1.f,  1.f,
            1.f,   1.f,
            -1.f, -1.f,
            1.f,  -1.f
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(posCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        posBuffer = bb.asFloatBuffer();
        posBuffer.put(posCoords);
        posBuffer.position(0);

        for (int i = 0; i < 4; ++i) {
            posCoords[2 * i]     *= (width -  padding) / (float) width;
            posCoords[2 * i + 1] *= (height - padding) / (float) height;
        }

        bb = ByteBuffer.allocateDirect(posCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        padPosBuffer = bb.asFloatBuffer();
        padPosBuffer.put(posCoords);
        padPosBuffer.position(0);

        float[] texCoords = {
            0.f, 1.f,
            1.f, 1.f,
            0.f, 0.f,
            1.f, 0.f
        };

        bb = ByteBuffer.allocateDirect(texCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        uvBuffer = bb.asFloatBuffer();
        uvBuffer.put(texCoords);
        uvBuffer.position(0);

        String vertexShaderSource = AndroidUtilities.readRes(R.raw.blur_vrt);
        String fragmentShaderSource = AndroidUtilities.readRes(R.raw.blur_frg);
        if (vertexShaderSource == null || fragmentShaderSource == null) {
            return false;
        }

        for (int a = 0; a < 2; ++a) {
            if (a == 1) {
                fragmentShaderSource = "#extension GL_OES_EGL_image_external : require\n" + fragmentShaderSource.replace("sampler2D tex", "samplerExternalOES tex");
            }
            final int vertexShader = FilterShaders.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource);
            final int fragmentShader = FilterShaders.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource);
            if (vertexShader == 0 || fragmentShader == 0) {
                return false;
            }
            final int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glBindAttribLocation(program, 0, "p");
            GLES20.glBindAttribLocation(program, 1, "inputuv");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(program);
                return false;
            }
            this.program[a] = new Program(program);
        }

        GLES20.glGenFramebuffers(3, framebuffer, 0);
        GLES20.glGenTextures(3, texture, 0);

        for (int a = 0; a < 3; ++a) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[a]);
            final int w = width + (a == 2 ? 2 * padding : 0);
            final int h = height + (a == 2 ? 2 * padding : 0);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[a]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture[a], 0);

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                return false;
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        if (needsUiBitmap) {
            bitmap = Bitmap.createBitmap(2 * padding + width, 2 * padding + height, Bitmap.Config.ARGB_8888);
            buffer = ByteBuffer.allocateDirect((2 * padding + width) * (2 * padding + height) * 4);
        }

        return true;
    }

    public void draw(float[] oesMatrix, int texture, int textureWidth, int textureHeight) {
        final boolean oes = oesMatrix != null;
        Program p = program[oes ? 1 : 0];
        if (p == null) {
            return;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(p.gl);

        GLES20.glUniform1i(p.texHandle, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        if (oes) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        }

        GLES20.glEnableVertexAttribArray(p.uvHandle);
        GLES20.glVertexAttribPointer(p.uvHandle, 2, GLES20.GL_FLOAT, false, 8, uvBuffer);
        GLES20.glEnableVertexAttribArray(p.posHandle);
        GLES20.glVertexAttribPointer(p.posHandle, 2, GLES20.GL_FLOAT, false, 8, posBuffer);
        GLES20.glUniform2f(p.szHandle, width, height);
        GLES20.glUniform2f(p.texSzHandle, textureWidth, textureHeight);
        GLES20.glUniform1i(p.stepHandle, 0);
        GLES20.glUniform1f(p.flipyHandle, oes ? 1f : 0f);
        if (oes) {
            GLES20.glUniformMatrix4fv(p.videoMatrixHandle, 1, false, oesMatrix, 0);
        }
        GLES20.glUniform1f(p.hasVideoMatrixHandle, oes ? 1 : 0);

        org.telegram.ui.Components.Paint.Shader.SetColorUniform(p.gradientTopHandle, gradientTop);
        org.telegram.ui.Components.Paint.Shader.SetColorUniform(p.gradientBottomHandle, gradientBottom);

        synchronized (matrixLock) {
            GLES20.glUniformMatrix4fv(p.matrixHandle, 1, false, this.matrix, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        if (oes) {
            p = program[0];
            if (p == null) {
                return;
            }
            GLES20.glUseProgram(p.gl);

            GLES20.glEnableVertexAttribArray(p.uvHandle);
            GLES20.glVertexAttribPointer(p.uvHandle, 2, GLES20.GL_FLOAT, false, 8, uvBuffer);
            GLES20.glEnableVertexAttribArray(p.posHandle);
            GLES20.glVertexAttribPointer(p.posHandle, 2, GLES20.GL_FLOAT, false, 8, posBuffer);
            GLES20.glUniform2f(p.szHandle, width, height);
            GLES20.glUniform2f(p.texSzHandle, textureWidth, textureHeight);
            GLES20.glUniform1i(p.stepHandle, 0);

            org.telegram.ui.Components.Paint.Shader.SetColorUniform(p.gradientTopHandle, gradientTop);
            org.telegram.ui.Components.Paint.Shader.SetColorUniform(p.gradientBottomHandle, gradientBottom);

            GLES20.glUniform1f(p.flipyHandle, 0f);

            synchronized (matrixLock) {
                GLES20.glUniformMatrix4fv(p.matrixHandle, 1, false, this.matrix, 0);
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[1]);
        GLES20.glUniform1i(p.stepHandle, 1);

        GLES20.glUniform1i(p.texHandle, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.texture[0]);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[2]);
        GLES20.glViewport(0, 0, width + 2 * padding, height + 2 * padding);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnableVertexAttribArray(p.posHandle);
        GLES20.glVertexAttribPointer(p.posHandle, 2, GLES20.GL_FLOAT, false, 8, padPosBuffer);
        GLES20.glUniform1i(p.stepHandle, 2);

        GLES20.glUniform1i(p.texHandle, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.texture[1]);

        Object lock = null;
        if (currentManager != null) {
            lock = currentManager.getTextureLock();
        }
        if (lock != null) {
            synchronized (lock) {
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }
        } else {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        if (buffer != null) {
            buffer.rewind();
            GLES20.glReadPixels(0, 0, width + 2 * padding, height + 2 * padding, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            synchronized (bitmapLock) {
                bitmap.copyPixelsFromBuffer(buffer);
                bitmapAvailable = true;
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        AndroidUtilities.cancelRunOnUIThread(this.invalidateViews);
        AndroidUtilities.runOnUIThread(this.invalidateViews);
    }

    public int getTexture() {
        return texture[2];
    }

    public Bitmap getBitmap() {
        synchronized (bitmapLock) {
            if (!bitmapAvailable) {
                return null;
            }
            return bitmap;
        }
    }

    public void resetBitmap() {
        synchronized (bitmapLock) {
            bitmapAvailable = false;
        }
    }

    public void updateGradient(int top, int bottom) {
        gradientTop = top;
        gradientBottom = bottom;
    }

    private BlurManager currentManager;
    public void setBlurManager(BlurManager manager) {
        if (currentManager != null) {
            currentManager.setShader(null);
        }
        this.currentManager = manager;
        if (currentManager != null) {
            currentManager.setShader(this);
        }
    }

    private final Runnable invalidateViews = () -> {
        if (currentManager != null) {
            currentManager.invalidate();
        }
    };

    private final Matrix iMatrix = new Matrix();
    public void updateTransform(Matrix matrix, int w, int h) {
        matrix.invert(iMatrix);
        iMatrix.preScale(w, h);
        iMatrix.postScale(1f / w, 1f / h);
        updateTransform(iMatrix);
    }

    public void updateTransform(Matrix matrix) {
        setupTransform = true;
        matrix.getValues(this.m3x3);
        synchronized (matrixLock) {
            this.matrix[0] = m3x3[0];
            this.matrix[1] = m3x3[3];
            this.matrix[2] = 0;
            this.matrix[3] = m3x3[6];

            this.matrix[4] = m3x3[1];
            this.matrix[5] = m3x3[4];
            this.matrix[6] = 0;
            this.matrix[7] = m3x3[7];

            this.matrix[8] = 0;
            this.matrix[9] = 0;
            this.matrix[10] = 1;
            this.matrix[11] = 0;

            this.matrix[12] = m3x3[2];
            this.matrix[13] = m3x3[5];
            this.matrix[14] = 0;
            this.matrix[15] = m3x3[8];
        }
    }

    public static class BlurManager {

        public int padding;
        private final View view;
        private final ArrayList<View> parents = new ArrayList<>();
        private final ArrayList<StoryBlurDrawer> holders = new ArrayList<>();
        private final ArrayList<Runnable> invalidateHolders = new ArrayList<>();

        private final Object contextLock = new Object();
        private EGLContext context;

        private final Object textureLock = new Object();

        public BlurManager(View parentView) {
            this.view = parentView;
            if (view.isAttachedToWindow()) {
                updateParents();
            }
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    updateParents();
                }
                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    parents.clear();
                }
            });
        }

        public EGLContext getParentContext() {
            synchronized (contextLock) {
                if (context != null) {
                    return context;
                }
                return EGL10.EGL_NO_CONTEXT;
            }
        }

        public void acquiredContext(EGLContext newContext) {
            synchronized (contextLock) {
                if (context == null) {
                    context = newContext;
                }
            }
        }

        public void destroyedContext(EGLContext oldContext) {
            synchronized (contextLock) {
                if (context == oldContext) {
                    context = null;
                }
            }
        }

        public Object getTextureLock() {
            return textureLock;
        }

        public int getTexture() {
            if (currentShader != null) {
                return currentShader.getTexture();
            }
            return -1;
        }

        private void updateParents() {
            parents.clear();
            View view = this.view;
            while (view != null) {
                parents.add(0, view);
                if (!(view.getParent() instanceof View)) {
                    break;
                }
                view = (View) view.getParent();
            }
        }

        private BlurringShader currentShader;
        public void setShader(BlurringShader shader) {
            if (currentShader == shader) {
                return;
            }
            currentShader = shader;
            if (currentShader != null) {
                invalidate();
            }
        }

        public void invalidate() {
            for (StoryBlurDrawer holder : holders) {
                holder.view.invalidate();
            }
            for (Runnable invalidate : invalidateHolders) {
                invalidate.run();
            }
        }

        public void attach(StoryBlurDrawer holder) {
            holders.add(holder);
        }

        public void detach(StoryBlurDrawer holder) {
            holders.remove(holder);
            if (invalidateHolders.isEmpty() && holders.isEmpty()) {
                thumbBlurer.destroy();
            }
        }

        public void attach(Runnable invalidate) {
            invalidateHolders.add(invalidate);
        }

        public void detach(Runnable invalidate) {
            invalidateHolders.remove(invalidate);
            if (invalidateHolders.isEmpty() && holders.isEmpty()) {
                thumbBlurer.destroy();
            }
        }

        public Bitmap getBitmap() {
            if (currentShader == null) {
                return fallbackBitmap;
            }
            Bitmap blurBitmap = currentShader.getBitmap();
            return blurBitmap == null ? fallbackBitmap : blurBitmap;
        }

        private void invalidateFallbackBlur() {
            fallbackBitmap = thumbBlurer.thumbBitmap;
            invalidate();
        }

        private final ThumbBlurer thumbBlurer = new ThumbBlurer(0, this::invalidateFallbackBlur);
        private Bitmap fallbackBitmap;

        private int i = 0;
        public void setFallbackBlur(Bitmap bitmap, int orientation) {
            setFallbackBlur(bitmap, orientation, false);
        }
        public void setFallbackBlur(Bitmap bitmap, int orientation, boolean recycleAfter) {
            fallbackBitmap = thumbBlurer.getBitmap(bitmap, "" + i++, orientation, 0, recycleAfter);
        }

        public void resetBitmap() {
            if (currentShader != null) {
                currentShader.resetBitmap();
            }
        }
    }

    public static class ThumbBlurer {

        private String thumbKey;
        private Bitmap thumbBitmap;

        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int padding;
        private final Runnable invalidate;

        private Runnable generate;

        public ThumbBlurer() {
            this(1);
        }

        public ThumbBlurer(int padding) {
            this.padding = padding;
            this.invalidate = null;
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        public ThumbBlurer(int padding, Runnable invalidate) {
            this.padding = padding;
            this.invalidate = invalidate;
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        public void destroy() {
            thumbKey = null;
            if (generate != null) {
                Utilities.globalQueue.cancelRunnable(generate);
            }
            if (thumbBitmap != null && !thumbBitmap.isRecycled()) {
                thumbBitmap.recycle();
            }
            thumbBitmap = null;
        }

        public Bitmap getBitmap(Bitmap bitmap, String key, int orientation, int invert, boolean recycleAfter) {
            if (bitmap == null || bitmap.isRecycled()) {
                return null;
            }
            if (TextUtils.equals(thumbKey, key)) {
                if (thumbBitmap != null) {
                    return thumbBitmap;
                } else if (generate != null) {
                    return null;
                }
            }
            if (generate != null) {
                Utilities.globalQueue.cancelRunnable(generate);
            }
            thumbKey = key;
            Utilities.globalQueue.postRunnable(generate = () -> {
                if (bitmap == null || bitmap.isRecycled()) {
                    return;
                }
                final float aspectRatio = bitmap.getWidth() / (float) bitmap.getHeight();
                final float scale = 1.5f;
                final float density = 9 * scale * 16 * scale;
                int width = (int) Math.round(Math.sqrt(density * aspectRatio));
                int height = (int) Math.round(Math.sqrt(density / aspectRatio));
                int fwidth, fheight;
                if (orientation == 90 || orientation == 270) {
                    fwidth = height;
                    fheight = width;
                } else {
                    fwidth = width;
                    fheight = height;
                }
                Bitmap resultBitmap = Bitmap.createBitmap(2 * padding + fwidth, 2 * padding + fheight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(resultBitmap);
                final Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                final Rect dst = new Rect(padding, padding, padding + width, padding + height);
                canvas.translate(padding + fwidth / 2f, padding + fheight / 2f);
                if (invert == 1) {
                    canvas.scale(-1, 1);
                } else if (invert == 2) {
                    canvas.scale(1, -1);
                }
                canvas.rotate(orientation);
                canvas.translate(-padding - width / 2f, -padding - height / 2f);
                try {
                    canvas.drawBitmap(bitmap, src, dst, null);
                } catch (Exception e) {}
                Utilities.stackBlurBitmap(resultBitmap, 6);
                if (padding > 0) {
                    // clear borders
                    canvas.drawRect(0, 0, width + padding, padding, clearPaint); // top
                    canvas.drawRect(0, padding, padding, padding + height, clearPaint);
                    canvas.drawRect(padding + width, padding, padding + width + padding, padding + height, clearPaint);
                    canvas.drawRect(0, padding + height, padding + width + padding, padding + height + padding, clearPaint);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (TextUtils.equals(thumbKey, key)) {
                        generate = null;
                        if (thumbBitmap != null) {
                            thumbBitmap.recycle();
                        }
                        thumbBitmap = resultBitmap;
                        if (invalidate != null) {
                            invalidate.run();
                        }
                    } else {
                        resultBitmap.recycle();
                    }

                    if (recycleAfter) {
                        bitmap.recycle();
                    }
                });
            });
            return thumbBitmap;
        }

        public Bitmap getBitmap(ImageReceiver imageReceiver) {
            if (imageReceiver == null) {
                return null;
            }
            return getBitmap(imageReceiver.getBitmap(), imageReceiver.getImageKey(), imageReceiver.getOrientation(), imageReceiver.getInvert(), false);
        }

        public Bitmap getBitmap(ImageReceiver.BitmapHolder bitmapHolder) {
            if (bitmapHolder == null) {
                return null;
            }
            return getBitmap(bitmapHolder.bitmap, bitmapHolder.getKey(), bitmapHolder.orientation, 0, false);
        }
    }

    public static class StoryBlurDrawer {

        public static final int BLUR_TYPE_BACKGROUND = 0;
        public static final int BLUR_TYPE_CAPTION = 1;
        public static final int BLUR_TYPE_CAPTION_XFER = 2;
        public static final int BLUR_TYPE_AUDIO_BACKGROUND = 3;
        public static final int BLUR_TYPE_AUDIO_WAVEFORM_BACKGROUND = 4;
        public static final int BLUR_TYPE_MENU_BACKGROUND = 5;
        public static final int BLUR_TYPE_SHADOW = 6;
        public static final int BLUR_TYPE_EMOJI_VIEW = 7;
        public static final int BLUR_TYPE_REPLY_BACKGROUND = 8;
        public static final int BLUR_TYPE_REPLY_TEXT_XFER = 9;
        public static final int BLUR_TYPE_ACTION_BACKGROUND = 10;

        private final BlurManager manager;
        private final View view;

        public RenderNode renderNode;
        public final ColorMatrix colorMatrix;

        private boolean animateBitmapChange;
        private boolean oldPaintSet;
        private float oldPaintAlpha;
        public Paint oldPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final int type;

        public StoryBlurDrawer(@Nullable BlurManager manager, @NonNull View view, int type) {
            this(manager, view, type, false);
        }

        public StoryBlurDrawer(@Nullable BlurManager manager, @NonNull View view, int type, boolean animateBitmapChange) {
            this.manager = manager;
            this.view = view;
            this.type = type;
            this.animateBitmapChange = animateBitmapChange;

            colorMatrix = new ColorMatrix();
            if (type == BLUR_TYPE_BACKGROUND) {
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.45f);
            } else if (type == BLUR_TYPE_MENU_BACKGROUND) {
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                oldPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.3f);
            } else if (type == BLUR_TYPE_CAPTION_XFER) {
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                oldPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, +.4f);
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.3f);
//                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, 1.4f);
            } else if (type == BLUR_TYPE_CAPTION) {
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.35f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, +.7f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, 1.5f);
            } else if (type == BLUR_TYPE_AUDIO_BACKGROUND) {
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.5f);
            } else if (type == BLUR_TYPE_AUDIO_WAVEFORM_BACKGROUND) {
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.6f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, +.3f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, 1.2f);
            } else if (type == BLUR_TYPE_SHADOW) {
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.4f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, 0.35f);
            } else if (type == BLUR_TYPE_EMOJI_VIEW) {
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.5f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, .95f);
            } else if (type == BLUR_TYPE_REPLY_BACKGROUND) {
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, -.15f);
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.47f);
            } else if (type == BLUR_TYPE_REPLY_TEXT_XFER) {
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                oldPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, +.4f);
                AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, +.45f);
//                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, 1.4f);
            } else if (type == BLUR_TYPE_ACTION_BACKGROUND) {
                colorMatrix.setSaturation(1.6f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, wasDark ? .97f : .92f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, wasDark ? +.12f : -.06f);
            }
            paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            oldPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

            if (this.view.isAttachedToWindow() && manager != null) {
                manager.attach(this);
            }
            this.view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    if (manager != null) {
                        manager.attach(StoryBlurDrawer.this);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    if (manager != null) {
                        manager.detach(StoryBlurDrawer.this);
                    }
                    recycle();
                }
            });
        }

        private boolean customOffset;
        private float customOffsetX, customOffsetY;
        public StoryBlurDrawer setOffset(float ox, float oy) {
            customOffset = true;
            customOffsetX = ox;
            customOffsetY = oy;
            return this;
        }

        private Bitmap lastBitmap;
        private BitmapShader bitmapShader;
        private final Matrix matrix = new Matrix();
        RectF bounds = new RectF();

        private void updateBounds() {
            Bitmap bitmap = manager.getBitmap();
            if (bitmap == null) {
                return;
            }
            if (bitmapShader == null || lastBitmap != bitmap) {
                bitmapShader = new BitmapShader(lastBitmap = bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                paint.setShader(bitmapShader);
            }
            float sx = bounds.width() / (float) lastBitmap.getWidth();
            float sy = bounds.height() / (float) lastBitmap.getHeight();

            matrix.reset();
            matrix.postTranslate(bounds.left, bounds.top);
            matrix.preScale(sx, sy);

            bitmapShader.setLocalMatrix(matrix);
        }

        public void setBounds(float left, float top, float right, float bottom) {
            AndroidUtilities.rectTmp.set(left, top, right, bottom);
            setBounds(AndroidUtilities.rectTmp);
        }

        public void setBounds(RectF bounds) {
            if (this.bounds.top == bounds.top && this.bounds.bottom == bounds.bottom && this.bounds.left == bounds.left && this.bounds.right == bounds.right) {
                return;
            }
            this.bounds.set(bounds);
            updateBounds();
        }

        private boolean wasDark = false;
        public StoryBlurDrawer adapt(boolean isDark) {
            if (wasDark != isDark) {
                wasDark = isDark;
                if (type == BLUR_TYPE_ACTION_BACKGROUND) {
                    final ColorMatrix colorMatrix = new ColorMatrix();
                    colorMatrix.setSaturation(1.6f);
                    AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, wasDark ? .97f : .92f);
                    AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, wasDark ? +.12f : -.06f);
                    paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                    oldPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                }
            }
            return this;
        }

        @Nullable
        public Paint getPaint(float alpha) {
            return getPaint(alpha, 0, 0);
        }

        @Nullable
        public Paint getPaint(float alpha, float tx, float ty) {
            if (manager == null) {
                return null;
            }
            Bitmap bitmap = manager.getBitmap();
            if (bitmap == null) {
                return null;
            }

            if (bitmapShader == null || lastBitmap != bitmap) {
                if (animateBitmapChange && bitmapShader != null && lastBitmap != null && !lastBitmap.isRecycled() && !bitmap.isRecycled()) {
                    Paint tempPaint = paint;
                    paint = oldPaint;
                    oldPaint = tempPaint;
                    oldPaintSet = true;
                    animateOldPaint();
                }
                bitmapShader = new BitmapShader(lastBitmap = bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                paint.setShader(bitmapShader);
            }

            if (!setupMatrix(bitmap.getWidth(), bitmap.getHeight())) {
                return null;
            }
            matrix.postTranslate(-tx, -ty);
            bitmapShader.setLocalMatrix(matrix);
            paint.setAlpha((int) (0xFF * alpha));

            return paint;
        }

        private Paint[] tempPaints;
        public Paint[] getPaints(float alpha, float tx, float ty) {
            Paint paint2 = getPaint(alpha, tx, ty);
            Paint paint1 = oldPaintSet ? oldPaint : null;
            if (paint2 != null && oldPaintSet) {
                paint2.setAlpha((int) (0xFF * (1f - oldPaintAlpha) * alpha));
            }
            if (paint1 != null) {
                paint1.setAlpha((int) (0xFF * alpha));
            }
            if (tempPaints == null) {
                tempPaints = new Paint[2];
            }
            tempPaints[0] = paint1;
            tempPaints[1] = paint2;
            return tempPaints;
        }

        private ValueAnimator crossfadeAnimator;
        private void animateOldPaint() {
            if (crossfadeAnimator != null) {
                crossfadeAnimator.cancel();
                crossfadeAnimator = null;
            }

            oldPaintAlpha = 1f;
            crossfadeAnimator = ValueAnimator.ofFloat(oldPaintAlpha, 0f);
            crossfadeAnimator.addUpdateListener(anm -> {
                oldPaintAlpha = (float) anm.getAnimatedValue();
                this.view.invalidate();
            });
            crossfadeAnimator.start();
        }

        private void recycle() {
            lastBitmap = null;
            paint.setShader(bitmapShader = null);
        }

        private boolean setupMatrix(int bitmapWidth, int bitmapHeight) {
            matrix.reset();
            if (customOffset) {
                matrix.postTranslate(-customOffsetX, -customOffsetY);
            } else {
                View view = this.view;
                do {
                    matrix.preScale(1f / view.getScaleX(), 1f / view.getScaleY(), view.getPivotX(), view.getPivotY());
                    matrix.preRotate(-view.getRotation(), view.getPivotX(), view.getPivotY());
                    matrix.preTranslate(-view.getX(), -view.getY());
                    if (view.getParent() instanceof View) {
                        view = (View) view.getParent();
                    } else {
                        break;
                    }
                } while (view != null && manager != null && !manager.parents.contains(view));

                if (manager != null && manager.view != view) {
                    int index = manager.parents.indexOf(view) + 1;
                    while (index >= 0 && index < manager.parents.size()) {
                        View child = manager.parents.get(index);
                        if (child == null) {
                            continue;
                        }
                        matrix.postTranslate(child.getX(), child.getY());
                        matrix.postScale(1f / child.getScaleX(), 1f / child.getScaleY(), child.getPivotX(), child.getPivotY());
                        matrix.postRotate(child.getRotation(), child.getPivotX(), child.getPivotY());
                        index++;
                    }
                }
            }

            if (manager != null && manager.view != null) {
                matrix.preScale((float) manager.view.getWidth() / bitmapWidth, (float) manager.view.getHeight() / bitmapHeight);
            }
            return true;
        }

        public Drawable makeDrawable(float offsetX, float offsetY, Drawable base, float r) {
            return new Drawable() {

                float alpha = 1f;
                private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Rect rect = new Rect();

                @Nullable
                private Paint getPaint() {
                    if (manager == null) {
                        return null;
                    }
                    Bitmap bitmap = manager.getBitmap();
                    if (bitmap == null) {
                        return null;
                    }

                    if (bitmapShader == null || lastBitmap != bitmap) {
                        bitmapShader = new BitmapShader(lastBitmap = bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        paint.setShader(bitmapShader);
                    }

                    matrix.reset();
                    matrix.postTranslate(-customOffsetX - offsetX, -customOffsetY - offsetY);
                    if (manager.view != null) {
                        matrix.preScale((float) manager.view.getWidth() / bitmap.getWidth(), (float) manager.view.getHeight() / bitmap.getHeight());
                    }
                    bitmapShader.setLocalMatrix(matrix);
                    paint.setAlpha((int) (0xFF * alpha));
                    return paint;
                }

                @Override
                public void draw(@NonNull Canvas canvas) {
                    Paint paint = getPaint();
                    Rect bounds = getBounds();
                    if (paint != null) {
                        if (base != null) {
                            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);
                            base.setBounds(bounds);
                            base.draw(canvas);
                            canvas.drawRect(bounds, paint);
                            canvas.restore();
                            getPadding(rect);
                            AndroidUtilities.rectTmp.set(
                                bounds.left + rect.left,
                                bounds.top + rect.top,
                                bounds.right - rect.right,
                                bounds.bottom - rect.bottom
                            );
                            dimPaint.setColor(0x66000000);
                            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, dimPaint);
                        } else {
                            if (r > 0) {
                                AndroidUtilities.rectTmp.set(bounds);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);
                            } else {
                                canvas.drawRect(bounds, paint);
                            }
                            dimPaint.setColor(0x66000000);
                            if (r > 0) {
                                AndroidUtilities.rectTmp.set(bounds);
                                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, dimPaint);
                            } else {
                                canvas.drawRect(bounds, dimPaint);
                            }
                        }
                    } else if (base != null) {
                        base.setBounds(bounds);
                        base.draw(canvas);
                    } else {
                        dimPaint.setColor(-14145495);
                        if (r > 0) {
                            AndroidUtilities.rectTmp.set(bounds);
                            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, dimPaint);
                        } else {
                            canvas.drawRect(bounds, dimPaint);
                        }
                    }
                }

                @Override
                public void setAlpha(int alpha) {
                    this.alpha = alpha / (float) 0xFF;
                }

                @Override
                public void setColorFilter(@Nullable ColorFilter colorFilter) {}

                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSPARENT;
                }

                @Override
                public boolean getPadding(@NonNull Rect padding) {
                    if (base != null) {
                        return base.getPadding(padding);
                    } else {
                        padding.set(0, 0, 0, 0);
                        return true;
                    }
                }
            };
        }
    }
}
