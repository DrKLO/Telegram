/*
 * This is the source code of Telegram for Android v. 6.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.video;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextEffects;
import org.telegram.ui.Components.FilterShaders;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;
import org.telegram.ui.Components.Paint.Views.LocationMarker;
import org.telegram.ui.Components.Paint.Views.PaintTextOptionsView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;

public class TextureRenderer {

    private FloatBuffer verticesBuffer;
    private FloatBuffer gradientVerticesBuffer;
    private FloatBuffer gradientTextureBuffer;
    private FloatBuffer textureBuffer;
    private FloatBuffer renderTextureBuffer;
    private FloatBuffer bitmapVerticesBuffer;

    private FloatBuffer blurVerticesBuffer;

    private FloatBuffer partsVerticesBuffer[];
    private FloatBuffer partsTextureBuffer;
    private ArrayList<StoryEntry.Part> parts;
    private int[] partsTexture;

    private boolean useMatrixForImagePath;

    float[] bitmapData = {
            -1.0f, 1.0f,
            1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
    };

    private FilterShaders filterShaders;
    private String paintPath;
    private String blurPath;
    private String imagePath;
    private int imageWidth, imageHeight;
    private ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
    private ArrayList<AnimatedEmojiDrawable> emojiDrawables;
    private int originalWidth;
    private int originalHeight;
    private int transformedWidth;
    private int transformedHeight;

    private BlurringShader blur;

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPosition;\n" +
            "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n";

    private static final String VERTEX_SHADER_300 =
        "#version 320 es\n" +
        "uniform mat4 uMVPMatrix;\n" +
        "uniform mat4 uSTMatrix;\n" +
        "in vec4 aPosition;\n" +
        "in vec4 aTextureCoord;\n" +
        "out vec2 vTextureCoord;\n" +
        "void main() {\n" +
        "  gl_Position = uMVPMatrix * aPosition;\n" +
        "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
        "}\n";

    private static final String FRAGMENT_EXTERNAL_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private static final String GRADIENT_FRAGMENT_SHADER =
        "precision highp float;\n" +
        "varying vec2 vTextureCoord;\n" +
        "uniform vec4 gradientTopColor;\n" +
        "uniform vec4 gradientBottomColor;\n" +
        "float interleavedGradientNoise(vec2 n) {\n" +
        "    return fract(52.9829189 * fract(.06711056 * n.x + .00583715 * n.y));\n" +
        "}\n" +
        "void main() {\n" +
        "  gl_FragColor = mix(gradientTopColor, gradientBottomColor, vTextureCoord.y + (.2 * interleavedGradientNoise(gl_FragCoord.xy) - .1));\n" +
        "}\n";

    private int NUM_FILTER_SHADER = -1;
    private int NUM_EXTERNAL_SHADER = -1;
    private int NUM_GRADIENT_SHADER = -1;

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private float[] mSTMatrixIdentity = new float[16];
    private int mTextureID;
    private int[] mProgram;
    private int[] muMVPMatrixHandle;
    private int[] muSTMatrixHandle;
    private int[] maPositionHandle;
    private int[] maTextureHandle;
    private int gradientTopColorHandle, gradientBottomColorHandle;
    private int texSizeHandle;
    // todo: HDR handles

    private int simpleShaderProgram;
    private int simplePositionHandle;
    private int simpleInputTexCoordHandle;
    private int simpleSourceImageHandle;

    private int blurShaderProgram;
    private int blurPositionHandle;
    private int blurInputTexCoordHandle;
    private int blurBlurImageHandle;
    private int blurMaskImageHandle;

    private int[] paintTexture;
    private int[] stickerTexture;
    private Bitmap stickerBitmap;
    private Canvas stickerCanvas;
    private float videoFps;

    private Bitmap roundBitmap;
    private Canvas roundCanvas;
    private final android.graphics.Rect roundSrc = new android.graphics.Rect();
    private final RectF roundDst = new RectF();
    private Path roundClipPath;

    private int imageOrientation;

    private boolean blendEnabled;

    private boolean isPhoto;

    private boolean firstFrame = true;
    Path path;
    Paint xRefPaint;
    Paint textColorPaint;

    private final MediaController.CropState cropState;
    private int[] blurTexture;

    private int gradientTopColor, gradientBottomColor;

    public TextureRenderer(
        MediaController.SavedFilterState savedFilterState,
        String image,
        String paint,
        String blurtex,
        ArrayList<VideoEditedInfo.MediaEntity> entities,
        MediaController.CropState cropState,
        int w, int h,
        int originalWidth, int originalHeight,
        int rotation,
        float fps,
        boolean photo,
        Integer gradientTopColor,
        Integer gradientBottomColor,
        StoryEntry.HDRInfo hdrInfo,
        ArrayList<StoryEntry.Part> parts
    ) {
        isPhoto = photo;
        this.parts = parts;

        float[] texData = {
                0.f, 0.f,
                1.f, 0.f,
                0.f, 1.f,
                1.f, 1.f,
        };

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start textureRenderer w = " + w + " h = " + h + " r = " + rotation + " fps = " + fps);
            if (cropState != null) {
                FileLog.d("cropState px = " + cropState.cropPx + " py = " + cropState.cropPy + " cScale = " + cropState.cropScale +
                        " cropRotate = " + cropState.cropRotate + " pw = " + cropState.cropPw + " ph = " + cropState.cropPh +
                        " tw = " + cropState.transformWidth + " th = " + cropState.transformHeight + " tr = " + cropState.transformRotation +
                        " mirror = " + cropState.mirrored);
            }
        }

        textureBuffer = ByteBuffer.allocateDirect(texData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureBuffer.put(texData).position(0);

        bitmapVerticesBuffer = ByteBuffer.allocateDirect(bitmapData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        bitmapVerticesBuffer.put(bitmapData).position(0);

        Matrix.setIdentityM(mSTMatrix, 0);
        Matrix.setIdentityM(mSTMatrixIdentity, 0);

        if (savedFilterState != null) {
            filterShaders = new FilterShaders(true, hdrInfo);
            filterShaders.setDelegate(FilterShaders.getFilterShadersDelegate(savedFilterState));
        }
        transformedWidth = w;
        transformedHeight = h;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        imagePath = image;
        paintPath = paint;
        blurPath = blurtex;
        mediaEntities = entities;
        videoFps = fps == 0 ? 30 : fps;
        this.cropState = cropState;

        int count = 0;
        NUM_EXTERNAL_SHADER = count++;
        if (gradientBottomColor != null && gradientTopColor != null) {
            NUM_GRADIENT_SHADER = count++;
        }
        if (filterShaders != null) {
            NUM_FILTER_SHADER = count++;
        }
        mProgram = new int[count];
        muMVPMatrixHandle = new int[count];
        muSTMatrixHandle = new int[count];
        maPositionHandle = new int[count];
        maTextureHandle = new int[count];

        Matrix.setIdentityM(mMVPMatrix, 0);
        int textureRotation = 0;
        if (gradientBottomColor != null && gradientTopColor != null) {
            final float[] verticesData = {
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f, 1.0f,
                1.0f, 1.0f
            };
            gradientVerticesBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            gradientVerticesBuffer.put(verticesData).position(0);
            final float[] textureData = {
                0, isPhoto ? 1 : 0,
                1, isPhoto ? 1 : 0,
                0, isPhoto ? 0 : 1,
                1, isPhoto ? 0 : 1
            };
            gradientTextureBuffer = ByteBuffer.allocateDirect(textureData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            gradientTextureBuffer.put(textureData).position(0);
            this.gradientTopColor = gradientTopColor;
            this.gradientBottomColor = gradientBottomColor;
        }
        if (cropState != null) {
            if (cropState.useMatrix != null) {
                useMatrixForImagePath = true;
                float[] verticesData = {
                    0, 0,
                    originalWidth, 0,
                    0, originalHeight,
                    originalWidth, originalHeight
                };
                cropState.useMatrix.mapPoints(verticesData);
                for (int a = 0; a < 4; a++) {
                    verticesData[a * 2] = verticesData[a * 2] / w * 2f - 1f;
                    verticesData[a * 2 + 1] = 1f - verticesData[a * 2 + 1] / h * 2f;
                }
                verticesBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                verticesBuffer.put(verticesData).position(0);
            } else {
                float[] verticesData = {
                        0, 0,
                        w, 0,
                        0, h,
                        w, h,
                };
                textureRotation = cropState.transformRotation;

                transformedWidth *= cropState.cropPw;
                transformedHeight *= cropState.cropPh;

                float angle = (float) (-cropState.cropRotate * (Math.PI / 180.0f));
                for (int a = 0; a < 4; a++) {
                    float x1 = verticesData[a * 2] - w / 2;
                    float y1 = verticesData[a * 2 + 1] - h / 2;
                    float x2 = (float) (x1 * Math.cos(angle) - y1 * Math.sin(angle) + cropState.cropPx * w) * cropState.cropScale;
                    float y2 = (float) (x1 * Math.sin(angle) + y1 * Math.cos(angle) - cropState.cropPy * h) * cropState.cropScale;
                    verticesData[a * 2] = x2 / transformedWidth * 2;
                    verticesData[a * 2 + 1] = y2 / transformedHeight * 2;
                }
                verticesBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                verticesBuffer.put(verticesData).position(0);
            }
        } else {
            float[] verticesData = {
                    -1.0f, -1.0f,
                    1.0f, -1.0f,
                    -1.0f, 1.0f,
                    1.0f, 1.0f,
            };
            verticesBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            verticesBuffer.put(verticesData).position(0);
        }
        float[] textureData;
        if (filterShaders != null) {
            if (textureRotation == 90) {
                textureData = new float[]{
                        1.f, 1.f,
                        1.f, 0.f,
                        0.f, 1.f,
                        0.f, 0.f
                };
            } else if (textureRotation == 180) {
                textureData = new float[]{
                        1.f, 0.f,
                        0.f, 0.f,
                        1.f, 1.f,
                        0.f, 1.f
                };
            } else if (textureRotation == 270) {
                textureData = new float[]{
                        0.f, 0.f,
                        0.f, 1.f,
                        1.f, 0.f,
                        1.f, 1.f
                };
            } else {
                textureData = new float[]{
                        0.f, 1.f,
                        1.f, 1.f,
                        0.f, 0.f,
                        1.f, 0.f
                };
            }
        } else {
            if (textureRotation == 90) {
                textureData = new float[]{
                        1.f, 0.f,
                        1.f, 1.f,
                        0.f, 0.f,
                        0.f, 1.f
                };
            } else if (textureRotation == 180) {
                textureData = new float[]{
                        1.f, 1.f,
                        0.f, 1.f,
                        1.f, 0.f,
                        0.f, 0.f
                };
            } else if (textureRotation == 270) {
                textureData = new float[]{
                        0.f, 1.f,
                        0.f, 0.f,
                        1.f, 1.f,
                        1.f, 0.f
                };
            } else {
                textureData = new float[]{
                        0.f, 0.f,
                        1.f, 0.f,
                        0.f, 1.f,
                        1.f, 1.f
                };
            }
        }
        if (!isPhoto && useMatrixForImagePath) {
            textureData[1] = 1f - textureData[1];
            textureData[3] = 1f - textureData[3];
            textureData[5] = 1f - textureData[5];
            textureData[7] = 1f - textureData[7];
        }
        if (cropState != null && cropState.mirrored) {
            for (int a = 0; a < 4; a++) {
                if (textureData[a * 2] > 0.5f) {
                    textureData[a * 2] = 0.0f;
                } else {
                    textureData[a * 2] = 1.0f;
                }
            }
        }
        renderTextureBuffer = ByteBuffer.allocateDirect(textureData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        renderTextureBuffer.put(textureData).position(0);
    }

    public int getTextureId() {
        return mTextureID;
    }

    private void drawGradient() {
        if (NUM_GRADIENT_SHADER < 0) {
            return;
        }
        GLES20.glUseProgram(mProgram[NUM_GRADIENT_SHADER]);

        GLES20.glVertexAttribPointer(maPositionHandle[NUM_GRADIENT_SHADER], 2, GLES20.GL_FLOAT, false, 8, gradientVerticesBuffer);
        GLES20.glEnableVertexAttribArray(maPositionHandle[NUM_GRADIENT_SHADER]);
        GLES20.glVertexAttribPointer(maTextureHandle[NUM_GRADIENT_SHADER], 2, GLES20.GL_FLOAT, false, 8, gradientTextureBuffer);
        GLES20.glEnableVertexAttribArray(maTextureHandle[NUM_GRADIENT_SHADER]);

        GLES20.glUniformMatrix4fv(muSTMatrixHandle[NUM_GRADIENT_SHADER], 1, false, mSTMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle[NUM_GRADIENT_SHADER], 1, false, mMVPMatrix, 0);

        GLES20.glUniform4f(gradientTopColorHandle, Color.red(gradientTopColor) / 255f, Color.green(gradientTopColor) / 255f, Color.blue(gradientTopColor) / 255f, Color.alpha(gradientTopColor) / 255f);
        GLES20.glUniform4f(gradientBottomColorHandle, Color.red(gradientBottomColor) / 255f, Color.green(gradientBottomColor) / 255f, Color.blue(gradientBottomColor) / 255f, Color.alpha(gradientBottomColor) / 255f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    public void drawFrame(SurfaceTexture st, long time) {
        boolean blurred = false;
        if (isPhoto) {
            drawGradient();
        } else {
            st.getTransformMatrix(mSTMatrix);
            if (BuildVars.LOGS_ENABLED && firstFrame) {
                StringBuilder builder = new StringBuilder();
                for (int a = 0; a < mSTMatrix.length; a++) {
                    builder.append(mSTMatrix[a]).append(", ");
                }
                FileLog.d("stMatrix = " + builder);
                firstFrame = false;
            }

            if (blendEnabled) {
                GLES20.glDisable(GLES20.GL_BLEND);
                blendEnabled = false;
            }

            int texture;
            int target;
            int index;
            float[] stMatrix;
            if (filterShaders != null) {
                filterShaders.onVideoFrameUpdate(mSTMatrix);

                GLES20.glViewport(0, 0, originalWidth, originalHeight);
                filterShaders.drawSkinSmoothPass();
                filterShaders.drawEnhancePass();
                filterShaders.drawSharpenPass();
                filterShaders.drawCustomParamsPass();
                blurred = filterShaders.drawBlurPass();

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                if (transformedWidth != originalWidth || transformedHeight != originalHeight) {
                    GLES20.glViewport(0, 0, transformedWidth, transformedHeight);
                }

                texture = filterShaders.getRenderTexture(blurred ? 0 : 1);
                index = NUM_FILTER_SHADER;
                target = GLES20.GL_TEXTURE_2D;
                stMatrix = mSTMatrixIdentity;
            } else {
                texture = mTextureID;
                index = NUM_EXTERNAL_SHADER;
                target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                stMatrix = mSTMatrix;
            }

            drawGradient();

            GLES20.glUseProgram(mProgram[index]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(target, texture);

            GLES20.glVertexAttribPointer(maPositionHandle[index], 2, GLES20.GL_FLOAT, false, 8, verticesBuffer);
            GLES20.glEnableVertexAttribArray(maPositionHandle[index]);
            GLES20.glVertexAttribPointer(maTextureHandle[index], 2, GLES20.GL_FLOAT, false, 8, renderTextureBuffer);
            GLES20.glEnableVertexAttribArray(maTextureHandle[index]);

            if (texSizeHandle != 0) {
                GLES20.glUniform2f(texSizeHandle, transformedWidth, transformedHeight);
            }

            GLES20.glUniformMatrix4fv(muSTMatrixHandle[index], 1, false, stMatrix, 0);
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle[index], 1, false, mMVPMatrix, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
        if (blur != null) {
            if (!blendEnabled) {
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                blendEnabled = true;
            }
            int tex = -1, w = 1, h = 1;
            if (imagePath != null && paintTexture != null) {
                tex = paintTexture[0];
                w = imageWidth;
                h = imageHeight;
            } else if (filterShaders != null) {
                tex = filterShaders.getRenderTexture(blurred ? 0 : 1);
                w = filterShaders.getRenderBufferWidth();
                h = filterShaders.getRenderBufferHeight();
            }
            if (tex != -1) {
                blur.draw(null, tex, w, h);

                GLES20.glViewport(0, 0, transformedWidth, transformedHeight);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

                GLES20.glUseProgram(blurShaderProgram);

                GLES20.glEnableVertexAttribArray(blurInputTexCoordHandle);
                GLES20.glVertexAttribPointer(blurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, gradientTextureBuffer);
                GLES20.glEnableVertexAttribArray(blurPositionHandle);
                GLES20.glVertexAttribPointer(blurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, blurVerticesBuffer);

                GLES20.glUniform1i(blurBlurImageHandle, 0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blur.getTexture());

                GLES20.glUniform1i(blurMaskImageHandle, 1);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[0]);

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }
        }
        if (isPhoto || paintTexture != null || stickerTexture != null || partsTexture != null) {
            GLES20.glUseProgram(simpleShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            GLES20.glUniform1i(simpleSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
            GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(simplePositionHandle);
        }
        if (paintTexture != null && imagePath != null) {
            for (int a = 0; a < 1; a++) {
                drawTexture(true, paintTexture[a], -10000, -10000, -10000, -10000, 0, false, useMatrixForImagePath && isPhoto && a == 0, -1);
            }
        }
        if (partsTexture != null) {
            for (int a = 0; a < partsTexture.length; a++) {
                drawTexture(true, partsTexture[a], -10000, -10000, -10000, -10000, 0, false, false, a);
            }
        }
        if (paintTexture != null) {
            for (int a = (imagePath != null ? 1 : 0); a < paintTexture.length; a++) {
                drawTexture(true, paintTexture[a], -10000, -10000, -10000, -10000, 0, false, useMatrixForImagePath && isPhoto && a == 0, -1);
            }
        }
        if (stickerTexture != null) {
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                drawEntity(mediaEntities.get(a), mediaEntities.get(a).color, time);
            }
        }
        GLES20.glFinish();
    }

    private void drawEntity(VideoEditedInfo.MediaEntity entity, int textColor, long time) {
        if (entity.ptr != 0) {
            if (entity.bitmap == null || entity.W <= 0 || entity.H <= 0) {
                return;
            }
            RLottieDrawable.getFrame(entity.ptr, (int) entity.currentFrame, entity.bitmap, entity.W, entity.H, entity.bitmap.getRowBytes(), true);
            applyRoundRadius(entity, entity.bitmap, (entity.subType & 8) != 0 ? textColor : 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, entity.bitmap, 0);
            entity.currentFrame += entity.framesPerDraw;
            if (entity.currentFrame >= entity.metadata[0]) {
                entity.currentFrame = 0;
            }
            drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
        } else if (entity.animatedFileDrawable != null) {
            int lastFrame = (int) entity.currentFrame;
            float scale = 1f;
            if (entity.type == VideoEditedInfo.MediaEntity.TYPE_ROUND) {
                long vstart, vend;
                if (isPhoto) {
                    vstart = 0;
                    vend = entity.roundDuration;
                } else {
                    vstart = entity.roundOffset;
                    vend = entity.roundOffset + (long) (entity.roundRight - entity.roundLeft);
                }
                final long ms = time / 1_000_000L;
                if (ms < vstart) {
                    scale = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(Utilities.clamp(1f - (vstart - ms) / 400f, 1, 0));
                } else if (ms > vend) {
                    scale = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(Utilities.clamp(1f - (ms - vend) / 400f, 1, 0));
                }

                if (scale > 0) {
                    long roundMs;
                    if (isPhoto) {
                        roundMs = Utilities.clamp(ms, entity.roundDuration, 0);
                    } else {
                        roundMs = Utilities.clamp(ms - entity.roundOffset + entity.roundLeft, entity.roundDuration, 0);
                    }
                    while (!entity.looped && entity.animatedFileDrawable.getProgressMs() < Math.min(roundMs, entity.animatedFileDrawable.getDurationMs())) {
                        int wasProgressMs = entity.animatedFileDrawable.getProgressMs();
                        entity.animatedFileDrawable.getNextFrame(false);
                        if (entity.animatedFileDrawable.getProgressMs() <= wasProgressMs && !(entity.animatedFileDrawable.getProgressMs() == 0 && wasProgressMs == 0)) {
                            entity.looped = true;
                            break;
                        }
                    }
                }
            } else {
                entity.currentFrame += entity.framesPerDraw;
                int currentFrame = (int) entity.currentFrame;
                while (lastFrame != currentFrame) {
                    entity.animatedFileDrawable.getNextFrame(true);
                    currentFrame--;
                }
            }
            Bitmap frameBitmap = entity.animatedFileDrawable.getBackgroundBitmap();
            if (frameBitmap != null) {
                Bitmap endBitmap;
                if (entity.type == VideoEditedInfo.MediaEntity.TYPE_ROUND) {
                    if (roundBitmap == null) {
                        final int side = Math.min(frameBitmap.getWidth(), frameBitmap.getHeight());
                        roundBitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
                        roundCanvas = new Canvas(roundBitmap);
                    }
                    if (roundBitmap != null) {
                        roundBitmap.eraseColor(Color.TRANSPARENT);
                        roundCanvas.save();
                        if (roundClipPath == null) {
                            roundClipPath = new Path();
                        }
                        roundClipPath.rewind();
                        roundClipPath.addCircle(roundBitmap.getWidth() / 2f, roundBitmap.getHeight() / 2f, roundBitmap.getWidth() / 2f * scale, Path.Direction.CW);
                        roundCanvas.clipPath(roundClipPath);
                        if (frameBitmap.getWidth() >= frameBitmap.getHeight()) {
                            roundSrc.set(
                                (frameBitmap.getWidth() - frameBitmap.getHeight()) / 2,
                                0,
                                frameBitmap.getWidth() - (frameBitmap.getWidth() - frameBitmap.getHeight()) / 2,
                                frameBitmap.getHeight()
                            );
                        } else {
                            roundSrc.set(
                                0,
                                (frameBitmap.getHeight() - frameBitmap.getWidth()) / 2,
                                frameBitmap.getWidth(),
                                frameBitmap.getHeight() - (frameBitmap.getHeight() - frameBitmap.getWidth()) / 2
                            );
                        }
                        roundDst.set(0, 0, roundBitmap.getWidth(), roundBitmap.getHeight());
                        roundCanvas.drawBitmap(frameBitmap, roundSrc, roundDst, null);
                        roundCanvas.restore();
                    }
                    endBitmap = roundBitmap;
                } else {
                    if (stickerCanvas == null && stickerBitmap != null) {
                        stickerCanvas = new Canvas(stickerBitmap);
                        if (stickerBitmap.getHeight() != frameBitmap.getHeight() || stickerBitmap.getWidth() != frameBitmap.getWidth()) {
                            stickerCanvas.scale(stickerBitmap.getWidth() / (float) frameBitmap.getWidth(), stickerBitmap.getHeight() / (float) frameBitmap.getHeight());
                        }
                    }
                    if (stickerBitmap != null) {
                        stickerBitmap.eraseColor(Color.TRANSPARENT);
                        stickerCanvas.drawBitmap(frameBitmap, 0, 0, null);
                        applyRoundRadius(entity, stickerBitmap, (entity.subType & 8) != 0 ? textColor : 0);
                    }
                    endBitmap = stickerBitmap;
                }
                if (endBitmap != null) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, endBitmap, 0);
                    drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
                }
            }
        } else {
            if (entity.bitmap != null) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, entity.bitmap, 0);
                drawTexture(false, stickerTexture[0], entity.x - entity.additionalWidth / 2f, entity.y - entity.additionalHeight / 2f, entity.width + entity.additionalWidth, entity.height + entity.additionalHeight, entity.rotation, entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO && (entity.subType & 2) != 0);
            }
            if (entity.entities != null && !entity.entities.isEmpty()) {
                for (int i = 0; i < entity.entities.size(); ++i) {
                    VideoEditedInfo.EmojiEntity e = entity.entities.get(i);
                    if (e == null) {
                        continue;
                    }
                    VideoEditedInfo.MediaEntity entity1 = e.entity;
                    if (entity1 == null) {
                        continue;
                    }
                    drawEntity(entity1, entity.color, time);
                }
            }
        }
    }

    private void applyRoundRadius(VideoEditedInfo.MediaEntity entity, Bitmap stickerBitmap, int color) {
        if (stickerBitmap == null || entity == null || entity.roundRadius == 0 && color == 0) {
            return;
        }
        if (entity.roundRadiusCanvas == null) {
            entity.roundRadiusCanvas = new Canvas(stickerBitmap);
        }
        if (entity.roundRadius != 0) {
            if (path == null) {
                path = new Path();
            }
            if (xRefPaint == null) {
                xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                xRefPaint.setColor(0xff000000);
                xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            }
            float rad = Math.min(stickerBitmap.getWidth(), stickerBitmap.getHeight()) * entity.roundRadius;
            path.rewind();
            RectF rect = new RectF(0, 0, stickerBitmap.getWidth(), stickerBitmap.getHeight());
            path.addRoundRect(rect, rad, rad, Path.Direction.CCW);
            path.toggleInverseFillType();
            entity.roundRadiusCanvas.drawPath(path, xRefPaint);
        }
        if (color != 0) {
            if (textColorPaint == null) {
                textColorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textColorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            }
            textColorPaint.setColor(color);
            entity.roundRadiusCanvas.drawRect(0, 0, stickerBitmap.getWidth(), stickerBitmap.getHeight(), textColorPaint);
        }
    }

    private void drawTexture(boolean bind, int texture) {
        drawTexture(bind, texture, -10000, -10000, -10000, -10000, 0, false);
    }

    private void drawTexture(boolean bind, int texture, float x, float y, float w, float h, float rotation, boolean mirror) {
        drawTexture(bind, texture, x, y, w, h, rotation, mirror, false, -1);
    }

    private void drawTexture(boolean bind, int texture, float x, float y, float w, float h, float rotation, boolean mirror, boolean useCropMatrix, int matrixIndex) {
        if (!blendEnabled) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            blendEnabled = true;
        }
        if (x <= -10000) {
            bitmapData[0] = -1.0f;
            bitmapData[1] = 1.0f;

            bitmapData[2] = 1.0f;
            bitmapData[3] = 1.0f;

            bitmapData[4] = -1.0f;
            bitmapData[5] = -1.0f;

            bitmapData[6] = 1.0f;
            bitmapData[7] = -1.0f;
        } else {
            x = x * 2 - 1.0f;
            y = (1.0f - y) * 2 - 1.0f;
            w = w * 2;
            h = h * 2;

            bitmapData[0] = x;
            bitmapData[1] = y;

            bitmapData[2] = x + w;
            bitmapData[3] = y;

            bitmapData[4] = x;
            bitmapData[5] = y - h;

            bitmapData[6] = x + w;
            bitmapData[7] = y - h;
        }
        float mx = (bitmapData[0] + bitmapData[2]) / 2;
        if (mirror) {
            float temp = bitmapData[2];
            bitmapData[2] = bitmapData[0];
            bitmapData[0] = temp;

            temp = bitmapData[6];
            bitmapData[6] = bitmapData[4];
            bitmapData[4] = temp;
        }
        if (rotation != 0) {
            float ratio = transformedWidth / (float) transformedHeight;
            float my = (bitmapData[5] + bitmapData[1]) / 2;
            for (int a = 0; a < 4; a++) {
                float x1 = bitmapData[a * 2    ] - mx;
                float y1 = (bitmapData[a * 2 + 1] - my) / ratio;
                bitmapData[a * 2    ] = (float) (x1 * Math.cos(rotation) - y1 * Math.sin(rotation)) + mx;
                bitmapData[a * 2 + 1] = (float) (x1 * Math.sin(rotation) + y1 * Math.cos(rotation)) * ratio + my;
            }
        }
        bitmapVerticesBuffer.put(bitmapData).position(0);
        GLES20.glVertexAttribPointer(simplePositionHandle, 2, GLES20.GL_FLOAT, false, 8, matrixIndex >= 0 ? partsVerticesBuffer[matrixIndex] : (useCropMatrix ? verticesBuffer : bitmapVerticesBuffer));
        GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
        GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, matrixIndex >= 0 ? partsTextureBuffer : (useCropMatrix ? renderTextureBuffer : textureBuffer));
        if (bind) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setBreakStrategy(EditTextOutline editText) {
        editText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
    }

    @SuppressLint("WrongConstant")
    public void surfaceCreated() {
        for (int a = 0; a < mProgram.length; a++) {
            String shader = null;
            if (a == NUM_EXTERNAL_SHADER) {
                shader = FRAGMENT_EXTERNAL_SHADER;
            } else if (a == NUM_FILTER_SHADER) {
                shader = FRAGMENT_SHADER;
            } else if (a == NUM_GRADIENT_SHADER) {
                shader = GRADIENT_FRAGMENT_SHADER;
            }
            if (shader == null) {
                continue;
            }
            mProgram[a] = createProgram(VERTEX_SHADER, shader, false);
            maPositionHandle[a] = GLES20.glGetAttribLocation(mProgram[a], "aPosition");
            maTextureHandle[a] = GLES20.glGetAttribLocation(mProgram[a], "aTextureCoord");
            muMVPMatrixHandle[a] = GLES20.glGetUniformLocation(mProgram[a], "uMVPMatrix");
            muSTMatrixHandle[a] = GLES20.glGetUniformLocation(mProgram[a], "uSTMatrix");
            if (a == NUM_GRADIENT_SHADER) {
                gradientTopColorHandle = GLES20.glGetUniformLocation(mProgram[a], "gradientTopColor");
                gradientBottomColorHandle = GLES20.glGetUniformLocation(mProgram[a], "gradientBottomColor");
            }
        }
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureID = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        if (blurPath != null && cropState != null && cropState.useMatrix != null) {
            blur = new BlurringShader();
            if (!blur.setup(transformedWidth / (float) transformedHeight, true, 0)) {
                blur = null;
            } else {
                blur.updateGradient(gradientTopColor, gradientBottomColor);
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postScale(originalWidth, originalHeight);
                matrix.postConcat(cropState.useMatrix);
                matrix.postScale(1f / transformedWidth, 1f / transformedHeight);
                android.graphics.Matrix imatrix = new android.graphics.Matrix();
                matrix.invert(imatrix);
                blur.updateTransform(imatrix);
            }

            Bitmap bitmap = BitmapFactory.decodeFile(blurPath);
            if (bitmap != null) {

                blurTexture = new int[1];
                GLES20.glGenTextures(1, blurTexture, 0);
                GLES20.glBindTexture(GL10.GL_TEXTURE_2D, blurTexture[0]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);

                bitmap.recycle();
            } else {
                blur = null;
            }

            if (blur != null) {
                final String fragShader =
                    "varying highp vec2 vTextureCoord;" +
                    "uniform sampler2D blurImage;" +
                    "uniform sampler2D maskImage;" +
                    "void main() {" +
                    "gl_FragColor = texture2D(blurImage, vTextureCoord) * texture2D(maskImage, vTextureCoord).a;" +
                    "}";
                int vertexShader = FilterShaders.loadShader(GLES20.GL_VERTEX_SHADER, FilterShaders.simpleVertexShaderCode);
                int fragmentShader = FilterShaders.loadShader(GLES20.GL_FRAGMENT_SHADER, fragShader);

                if (vertexShader != 0 && fragmentShader != 0) {
                    blurShaderProgram = GLES20.glCreateProgram();
                    GLES20.glAttachShader(blurShaderProgram, vertexShader);
                    GLES20.glAttachShader(blurShaderProgram, fragmentShader);
                    GLES20.glBindAttribLocation(blurShaderProgram, 0, "position");
                    GLES20.glBindAttribLocation(blurShaderProgram, 1, "inputTexCoord");

                    GLES20.glLinkProgram(blurShaderProgram);
                    int[] linkStatus = new int[1];
                    GLES20.glGetProgramiv(blurShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                    if (linkStatus[0] == 0) {
                        GLES20.glDeleteProgram(blurShaderProgram);
                        blurShaderProgram = 0;
                    } else {
                        blurPositionHandle = GLES20.glGetAttribLocation(blurShaderProgram, "position");
                        blurInputTexCoordHandle = GLES20.glGetAttribLocation(blurShaderProgram, "inputTexCoord");
                        blurBlurImageHandle = GLES20.glGetUniformLocation(blurShaderProgram, "blurImage");
                        blurMaskImageHandle = GLES20.glGetUniformLocation(blurShaderProgram, "maskImage");

                        float[] verticesData = {
                                -1.0f, 1.0f,
                                1.0f, 1.0f,
                                -1.0f, -1.0f,
                                1.0f, -1.0f,
                        };
                        blurVerticesBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                        blurVerticesBuffer.put(verticesData).position(0);
                    }
                } else {
                    blur = null;
                }
            }
        }
        if (filterShaders != null || imagePath != null || paintPath != null || mediaEntities != null || parts != null) {
            int vertexShader = FilterShaders.loadShader(GLES20.GL_VERTEX_SHADER, FilterShaders.simpleVertexShaderCode);
            int fragmentShader = FilterShaders.loadShader(GLES20.GL_FRAGMENT_SHADER, FilterShaders.simpleFragmentShaderCode);
            if (vertexShader != 0 && fragmentShader != 0) {
                simpleShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(simpleShaderProgram, vertexShader);
                GLES20.glAttachShader(simpleShaderProgram, fragmentShader);
                GLES20.glBindAttribLocation(simpleShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(simpleShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(simpleShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(simpleShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(simpleShaderProgram);
                    simpleShaderProgram = 0;
                } else {
                    simplePositionHandle = GLES20.glGetAttribLocation(simpleShaderProgram, "position");
                    simpleInputTexCoordHandle = GLES20.glGetAttribLocation(simpleShaderProgram, "inputTexCoord");
                    simpleSourceImageHandle = GLES20.glGetUniformLocation(simpleShaderProgram, "sTexture");
                }
            }
        }

        if (filterShaders != null) {
            filterShaders.create();
            filterShaders.setRenderData(null, 0, mTextureID, originalWidth, originalHeight);
        }
        if (imagePath != null || paintPath != null) {
            paintTexture = new int[(imagePath != null ? 1 : 0) + (paintPath != null ? 1 : 0)];
            GLES20.glGenTextures(paintTexture.length, paintTexture, 0);
            try {
                for (int a = 0; a < paintTexture.length; a++) {
                    String path;
                    int angle = 0, invert = 0;
                    if (a == 0 && imagePath != null) {
                        path = imagePath;
                        Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(path);
                        angle = orientation.first;
                        invert = orientation.second;
                    } else {
                        path = paintPath;
                    }
                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                    if (bitmap != null) {
                        if (a == 0 && imagePath != null && !useMatrixForImagePath) {
                            Bitmap newBitmap = Bitmap.createBitmap(transformedWidth, transformedHeight, Bitmap.Config.ARGB_8888);
                            newBitmap.eraseColor(0xff000000);
                            Canvas canvas = new Canvas(newBitmap);
                            float scale;
                            if (angle == 90 || angle == 270) {
                                scale = Math.max(bitmap.getHeight() / (float) transformedWidth, bitmap.getWidth() / (float) transformedHeight);
                            } else {
                                scale = Math.max(bitmap.getWidth() / (float) transformedWidth, bitmap.getHeight() / (float) transformedHeight);
                            }

                            android.graphics.Matrix matrix = new android.graphics.Matrix();
                            matrix.postTranslate(-bitmap.getWidth() / 2, -bitmap.getHeight() / 2);
                            matrix.postScale((invert == 1 ? -1.0f : 1.0f) / scale, (invert == 2 ? -1.0f : 1.0f) / scale);
                            matrix.postRotate(angle);
                            matrix.postTranslate(newBitmap.getWidth() / 2, newBitmap.getHeight() / 2);
                            canvas.drawBitmap(bitmap, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
                            bitmap = newBitmap;
                        }

                        if (a == 0 && imagePath != null) {
                            imageWidth = bitmap.getWidth();
                            imageHeight = bitmap.getHeight();
                        }

                        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, paintTexture[a]);
                        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (parts != null && !parts.isEmpty()) {
            partsTexture = new int[parts.size()];
            partsVerticesBuffer = new FloatBuffer[parts.size()];
            GLES20.glGenTextures(partsTexture.length, partsTexture, 0);
            try {
                for (int a = 0; a < partsTexture.length; a++) {
                    StoryEntry.Part part = parts.get(a);
                    String path = part.file.getAbsolutePath();

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, opts);
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = StoryEntry.calculateInSampleSize(opts, transformedWidth, transformedHeight);
                    Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
                    GLES20.glBindTexture(GL10.GL_TEXTURE_2D, partsTexture[a]);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);

                    final float[] verticesData = {
                        0, 0,
                        part.width, 0,
                        0, part.height,
                        part.width, part.height
                    };
                    part.matrix.mapPoints(verticesData);
                    for (int i = 0; i < 4; i++) {
                        verticesData[i * 2] = verticesData[i * 2] / transformedWidth * 2f - 1f;
                        verticesData[i * 2 + 1] = 1f - verticesData[i * 2 + 1] / transformedHeight * 2f;
                    }
                    partsVerticesBuffer[a] = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    partsVerticesBuffer[a].put(verticesData).position(0);
                }
            } catch (Throwable e2) {
                FileLog.e(e2);
            }

            final float[] textureData = {
                    0, 0,
                    1f, 0,
                    0, 1f,
                    1f, 1f
            };
            partsTextureBuffer = ByteBuffer.allocateDirect(textureData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            partsTextureBuffer.put(textureData).position(0);
        }
        if (mediaEntities != null) {
            try {
                stickerBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                stickerTexture = new int[1];
                GLES20.glGenTextures(1, stickerTexture, 0);
                GLES20.glBindTexture(GL10.GL_TEXTURE_2D, stickerTexture[0]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                    VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                    if (
                        entity.type == VideoEditedInfo.MediaEntity.TYPE_STICKER ||
                        entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO ||
                        entity.type == VideoEditedInfo.MediaEntity.TYPE_ROUND
                    ) {
                        initStickerEntity(entity);
                    } else if (entity.type == VideoEditedInfo.MediaEntity.TYPE_TEXT) {
                        EditTextOutline editText = new EditTextOutline(ApplicationLoader.applicationContext);
                        editText.getPaint().setAntiAlias(true);
                        editText.drawAnimatedEmojiDrawables = false;
                        editText.setBackgroundColor(Color.TRANSPARENT);
                        editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
                        Typeface typeface;
                        if (entity.textTypeface != null && (typeface = entity.textTypeface.getTypeface()) != null) {
                            editText.setTypeface(typeface);
                        }
                        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, entity.fontSize);
                        SpannableString text = new SpannableString(entity.text);
                        for (VideoEditedInfo.EmojiEntity e : entity.entities) {
                            if (e.documentAbsolutePath == null) {
                                continue;
                            }
                            e.entity = new VideoEditedInfo.MediaEntity();
                            e.entity.text = e.documentAbsolutePath;
                            e.entity.subType = e.subType;
                            AnimatedEmojiSpan span = new AnimatedEmojiSpan(0L, 1f, editText.getPaint().getFontMetricsInt()) {
                                @Override
                                public void draw(@NonNull Canvas canvas, CharSequence charSequence, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                                    super.draw(canvas, charSequence, start, end, x, top, y, bottom, paint);

                                    float tcx = entity.x + (editText.getPaddingLeft() + x + measuredSize / 2f) / entity.viewWidth * entity.width;
                                    float tcy = entity.y + (editText.getPaddingTop() + top + (bottom - top) / 2f) / entity.viewHeight * entity.height;

                                    if (entity.rotation != 0) {
                                        float mx = entity.x + entity.width / 2f;
                                        float my = entity.y + entity.height / 2f;
                                        float ratio = transformedWidth / (float) transformedHeight;
                                        float x1 = tcx - mx;
                                        float y1 = (tcy - my) / ratio;
                                        tcx = (float) (x1 * Math.cos(-entity.rotation) - y1 * Math.sin(-entity.rotation)) + mx;
                                        tcy = (float) (x1 * Math.sin(-entity.rotation) + y1 * Math.cos(-entity.rotation)) * ratio + my;
                                    }

                                    e.entity.width =  (float) measuredSize / entity.viewWidth * entity.width;
                                    e.entity.height = (float) measuredSize / entity.viewHeight * entity.height;
                                    e.entity.x = tcx - e.entity.width / 2f;
                                    e.entity.y = tcy - e.entity.height / 2f;
                                    e.entity.rotation = entity.rotation;

                                    if (e.entity.bitmap == null)
                                        initStickerEntity(e.entity);
                                }
                            };
                            text.setSpan(span, e.offset, e.offset + e.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        editText.setText(Emoji.replaceEmoji(text, editText.getPaint().getFontMetricsInt(), (int) (editText.getTextSize() * .8f), false));
                        editText.setTextColor(entity.color);
                        CharSequence text2 = editText.getText();
                        if (text2 instanceof Spanned) {
                            Emoji.EmojiSpan[] spans = ((Spanned) text2).getSpans(0, text2.length(), Emoji.EmojiSpan.class);
                            for (int i = 0; i < spans.length; ++i) {
                                spans[i].scale = .85f;
                            }
                        }


                        int gravity;
                        switch (entity.textAlign) {
                            default:
                            case PaintTextOptionsView.ALIGN_LEFT:
                                gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                                break;
                            case PaintTextOptionsView.ALIGN_CENTER:
                                gravity = Gravity.CENTER;
                                break;
                            case PaintTextOptionsView.ALIGN_RIGHT:
                                gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                                break;
                        }

                        editText.setGravity(gravity);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            int textAlign;
                            switch (entity.textAlign) {
                                default:
                                case PaintTextOptionsView.ALIGN_LEFT:
                                    textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_END : View.TEXT_ALIGNMENT_TEXT_START;
                                    break;
                                case PaintTextOptionsView.ALIGN_CENTER:
                                    textAlign = View.TEXT_ALIGNMENT_CENTER;
                                    break;
                                case PaintTextOptionsView.ALIGN_RIGHT:
                                    textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_TEXT_END;
                                    break;
                            }
                            editText.setTextAlignment(textAlign);
                        }

                        editText.setHorizontallyScrolling(false);
                        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                        editText.setFocusableInTouchMode(true);
                        editText.setInputType(editText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
                        if (Build.VERSION.SDK_INT >= 23) {
                            setBreakStrategy(editText);
                        }
                        if (entity.subType == 0) {
                            editText.setFrameColor(entity.color);
                            editText.setTextColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .721f ? Color.BLACK : Color.WHITE);
                        } else if (entity.subType == 1) {
                            editText.setFrameColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .25f ? 0x99000000 : 0x99ffffff);
                            editText.setTextColor(entity.color);
                        } else if (entity.subType == 2) {
                            editText.setFrameColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .25f ? Color.BLACK : Color.WHITE);
                            editText.setTextColor(entity.color);
                        } else if (entity.subType == 3) {
                            editText.setFrameColor(0);
                            editText.setTextColor(entity.color);
                        }

                        editText.measure(View.MeasureSpec.makeMeasureSpec(entity.viewWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(entity.viewHeight, View.MeasureSpec.EXACTLY));
                        editText.layout(0, 0, entity.viewWidth, entity.viewHeight);
                        entity.bitmap = Bitmap.createBitmap(entity.viewWidth, entity.viewHeight, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(entity.bitmap);
                        editText.draw(canvas);
                    } else if (entity.type == VideoEditedInfo.MediaEntity.TYPE_LOCATION) {
                        LocationMarker marker = new LocationMarker(ApplicationLoader.applicationContext, entity.density);
                        marker.setText(entity.text);
                        marker.setType(entity.subType, entity.color);
                        marker.setMaxWidth(entity.viewWidth);
                        if (entity.entities.size() == 1) {
                            marker.forceEmoji();
                        }
                        marker.measure(View.MeasureSpec.makeMeasureSpec(entity.viewWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(entity.viewHeight, View.MeasureSpec.EXACTLY));
                        marker.layout(0, 0, entity.viewWidth, entity.viewHeight);
                        float scale = entity.width * transformedWidth / entity.viewWidth;
                        int w = (int) (entity.viewWidth * scale), h = (int) (entity.viewHeight * scale), pad = 8;
                        entity.bitmap = Bitmap.createBitmap(w + pad + pad, h + pad + pad, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(entity.bitmap);
                        canvas.translate(pad, pad);
                        canvas.scale(scale, scale);
                        marker.draw(canvas);
                        entity.additionalWidth = (2 * pad) * scale / transformedWidth;
                        entity.additionalHeight = (2 * pad) * scale / transformedHeight;
                        if (entity.entities.size() == 1) {
                            VideoEditedInfo.EmojiEntity e = entity.entities.get(0);
                            e.entity = new VideoEditedInfo.MediaEntity();
                            e.entity.text = e.documentAbsolutePath;
                            e.entity.subType = e.subType;

                            RectF bounds = new RectF();
                            marker.getEmojiBounds(bounds);

                            float tcx = entity.x + (bounds.centerX()) / entity.viewWidth * entity.width;
                            float tcy = entity.y + (bounds.centerY()) / entity.viewHeight * entity.height;

                            if (entity.rotation != 0) {
                                float mx = entity.x + entity.width / 2f;
                                float my = entity.y + entity.height / 2f;
                                float ratio = transformedWidth / (float) transformedHeight;
                                float x1 = tcx - mx;
                                float y1 = (tcy - my) / ratio;
                                tcx = (float) (x1 * Math.cos(-entity.rotation) - y1 * Math.sin(-entity.rotation)) + mx;
                                tcy = (float) (x1 * Math.sin(-entity.rotation) + y1 * Math.cos(-entity.rotation)) * ratio + my;
                            }

                            e.entity.width =  (float) bounds.width() / entity.viewWidth * entity.width;
                            e.entity.height = (float) bounds.height() / entity.viewHeight * entity.height;
                            e.entity.width *= LocationMarker.SCALE;
                            e.entity.height *= LocationMarker.SCALE;
                            e.entity.x = tcx - e.entity.width / 2f;
                            e.entity.y = tcy - e.entity.height / 2f;
                            e.entity.rotation = entity.rotation;

                            initStickerEntity(e.entity);
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    private void initStickerEntity(VideoEditedInfo.MediaEntity entity) {
        entity.W = (int) (entity.width * transformedWidth);
        entity.H = (int) (entity.height * transformedHeight);
        if (entity.W > 512) {
            entity.H = (int) (entity.H / (float) entity.W * 512);
            entity.W = 512;
        }
        if (entity.H > 512) {
            entity.W = (int) (entity.W / (float) entity.H * 512);
            entity.H = 512;
        }
        if ((entity.subType & 1) != 0) {
            if (entity.W <= 0 || entity.H <= 0) {
                return;
            }
            entity.bitmap = Bitmap.createBitmap(entity.W, entity.H, Bitmap.Config.ARGB_8888);
            entity.metadata = new int[3];
            entity.ptr = RLottieDrawable.create(entity.text, null, entity.W, entity.H, entity.metadata, false, null, false, 0);
            entity.framesPerDraw = entity.metadata[1] / videoFps;
        } else if ((entity.subType & 4) != 0) {
            entity.looped = false;
            entity.animatedFileDrawable = new AnimatedFileDrawable(new File(entity.text), true, 0, 0, null, null, null, 0, UserConfig.selectedAccount, true, 512, 512, null);
            entity.framesPerDraw = entity.animatedFileDrawable.getFps() / videoFps;
            entity.currentFrame = 1;
            entity.animatedFileDrawable.getNextFrame(true);
            if (entity.type == VideoEditedInfo.MediaEntity.TYPE_ROUND) {
                entity.firstSeek = true;
            }
        } else {
            if (Build.VERSION.SDK_INT >= 19) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                if (entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO) {
                    opts.inMutable = true;
                }
                entity.bitmap = BitmapFactory.decodeFile(entity.text, opts);
            } else {
                try {
                    File path = new File(entity.text);
                    RandomAccessFile file = new RandomAccessFile(path, "r");
                    ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, path.length());
                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                    bmOptions.inJustDecodeBounds = true;
                    Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                    if (entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO) {
                        bmOptions.inMutable = true;
                    }
                    entity.bitmap = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);
                    Utilities.loadWebpImage(entity.bitmap, buffer, buffer.limit(), null, true);
                    file.close();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (entity.type == VideoEditedInfo.MediaEntity.TYPE_PHOTO && entity.bitmap != null) {
                entity.roundRadius = AndroidUtilities.dp(12) / (float) Math.min(entity.viewWidth, entity.viewHeight);
                Pair<Integer, Integer> orientation = AndroidUtilities.getImageOrientation(entity.text);
                entity.rotation -= Math.toRadians(orientation.first);
                if ((orientation.first / 90 % 2) == 1) {
                    float cx = entity.x + entity.width / 2f, cy = entity.y + entity.height / 2f;

                    float w = entity.width * transformedWidth / transformedHeight;
                    entity.width = entity.height * transformedHeight / transformedWidth;
                    entity.height = w;

                    entity.x = cx - entity.width / 2f;
                    entity.y = cy - entity.height / 2f;
                }
                applyRoundRadius(entity, entity.bitmap, 0);
            } else if (entity.bitmap != null) {
                float aspect = entity.bitmap.getWidth() / (float) entity.bitmap.getHeight();
                if (aspect > 1) {
                    float h = entity.height / aspect;
                    entity.y += (entity.height - h) / 2;
                    entity.height = h;
                } else if (aspect < 1) {
                    float w = entity.width * aspect;
                    entity.x += (entity.width - w) / 2;
                    entity.width = w;
                }
            }
        }
    }

    private int createProgram(String vertexSource, String fragmentSource, boolean is300) {
        if (is300) {
            int vertexShader = FilterShaders.loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = FilterShaders.loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }
            int program = GLES30.glCreateProgram();
            if (program == 0) {
                return 0;
            }
            GLES30.glAttachShader(program, vertexShader);
            GLES30.glAttachShader(program, pixelShader);
            GLES30.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES30.GL_TRUE) {
                GLES30.glDeleteProgram(program);
                program = 0;
            }
            return program;
        } else {
            int vertexShader = FilterShaders.loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = FilterShaders.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }
            int program = GLES20.glCreateProgram();
            if (program == 0) {
                return 0;
            }
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, pixelShader);
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }
    }

    public void release() {
        if (mediaEntities != null) {
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                if (entity.ptr != 0) {
                    RLottieDrawable.destroy(entity.ptr);
                }
                if (entity.animatedFileDrawable != null) {
                    entity.animatedFileDrawable.recycle();
                }
                if (entity.view instanceof EditTextEffects) {
                    ((EditTextEffects) entity.view).recycleEmojis();
                }
                if (entity.bitmap != null) {
                    entity.bitmap.recycle();
                    entity.bitmap = null;
                }
            }
        }
    }

    public void changeFragmentShader(String fragmentExternalShader, String fragmentShader, boolean is300) {
        if (NUM_EXTERNAL_SHADER >= 0 && NUM_EXTERNAL_SHADER < mProgram.length) {
            int newProgram = createProgram(is300 ? VERTEX_SHADER_300 : VERTEX_SHADER, fragmentExternalShader, is300);
            if (newProgram != 0) {
                GLES20.glDeleteProgram(mProgram[NUM_EXTERNAL_SHADER]);
                mProgram[NUM_EXTERNAL_SHADER] = newProgram;

                texSizeHandle = GLES20.glGetUniformLocation(newProgram, "texSize");
            }
        }
        if (NUM_FILTER_SHADER >= 0 && NUM_FILTER_SHADER < mProgram.length) {
            int newProgram = createProgram(is300 ? VERTEX_SHADER_300 : VERTEX_SHADER, fragmentShader, is300);
            if (newProgram != 0) {
                GLES20.glDeleteProgram(mProgram[NUM_FILTER_SHADER]);
                mProgram[NUM_FILTER_SHADER] = newProgram;
            }
        }
    }
}
