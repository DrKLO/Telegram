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
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.EditTextEffects;
import org.telegram.ui.Components.FilterShaders;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;
import org.telegram.ui.Components.Paint.Views.PaintTextOptionsView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Rect;

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
    private FloatBuffer textureBuffer;
    private FloatBuffer renderTextureBuffer;
    private FloatBuffer bitmapVerticesBuffer;

    float[] bitmapData = {
            -1.0f, 1.0f,
            1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
    };

    private FilterShaders filterShaders;
    private String paintPath;
    private String imagePath;
    private ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
    private ArrayList<AnimatedEmojiDrawable> emojiDrawables;
    private int originalWidth;
    private int originalHeight;
    private int transformedWidth;
    private int transformedHeight;

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

    private static final String FRAGMENT_EXTERNAL_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private float[] mSTMatrixIdentity = new float[16];
    private int mTextureID;
    private int[] mProgram;
    private int[] muMVPMatrixHandle;
    private int[] muSTMatrixHandle;
    private int[] maPositionHandle;
    private int[] maTextureHandle;

    private int simpleShaderProgram;
    private int simplePositionHandle;
    private int simpleInputTexCoordHandle;
    private int simpleSourceImageHandle;

    private int[] paintTexture;
    private int[] stickerTexture;
    private Bitmap stickerBitmap;
    private Canvas stickerCanvas;
    private float videoFps;

    private int imageOrientation;

    private boolean blendEnabled;

    private boolean isPhoto;

    private boolean firstFrame = true;
    Path path;
    Paint xRefPaint;

    public TextureRenderer(MediaController.SavedFilterState savedFilterState, String image, String paint, ArrayList<VideoEditedInfo.MediaEntity> entities, MediaController.CropState cropState, int w, int h, int originalWidth, int originalHeight, int rotation, float fps, boolean photo) {
        isPhoto = photo;

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
            filterShaders = new FilterShaders(true);
            filterShaders.setDelegate(FilterShaders.getFilterShadersDelegate(savedFilterState));
        }
        transformedWidth = w;
        transformedHeight = h;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
        imagePath = image;
        paintPath = paint;
        mediaEntities = entities;
        videoFps = fps == 0 ? 30 : fps;

        int count;
        if (filterShaders != null) {
            count = 2;
        } else {
            count = 1;
        }
        mProgram = new int[count];
        muMVPMatrixHandle = new int[count];
        muSTMatrixHandle = new int[count];
        maPositionHandle = new int[count];
        maTextureHandle = new int[count];

        Matrix.setIdentityM(mMVPMatrix, 0);
        int textureRotation = 0;
        if (cropState != null) {
            float[] verticesData = {
                    0, 0,
                    w, 0,
                    0, h,
                    w, h,
            };
            textureRotation = cropState.transformRotation;
            if (textureRotation == 90 || textureRotation == 270) {
                int temp = originalWidth;
                originalWidth = originalHeight;
                originalHeight = temp;
            }

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

    public void drawFrame(SurfaceTexture st) {
        if (isPhoto) {
            GLES20.glUseProgram(simpleShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            GLES20.glUniform1i(simpleSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
            GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(simplePositionHandle);
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
                boolean blurred = filterShaders.drawBlurPass();

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                if (transformedWidth != originalWidth || transformedHeight != originalHeight) {
                    GLES20.glViewport(0, 0, transformedWidth, transformedHeight);
                }

                texture = filterShaders.getRenderTexture(blurred ? 0 : 1);
                index = 1;
                target = GLES20.GL_TEXTURE_2D;
                stMatrix = mSTMatrixIdentity;
            } else {
                texture = mTextureID;
                index = 0;
                target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
                stMatrix = mSTMatrix;
            }

            GLES20.glUseProgram(mProgram[index]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(target, texture);

            GLES20.glVertexAttribPointer(maPositionHandle[index], 2, GLES20.GL_FLOAT, false, 8, verticesBuffer);
            GLES20.glEnableVertexAttribArray(maPositionHandle[index]);
            GLES20.glVertexAttribPointer(maTextureHandle[index], 2, GLES20.GL_FLOAT, false, 8, renderTextureBuffer);
            GLES20.glEnableVertexAttribArray(maTextureHandle[index]);

            GLES20.glUniformMatrix4fv(muSTMatrixHandle[index], 1, false, stMatrix, 0);
            GLES20.glUniformMatrix4fv(muMVPMatrixHandle[index], 1, false, mMVPMatrix, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
        if (paintTexture != null || stickerTexture != null) {
            GLES20.glUseProgram(simpleShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            GLES20.glUniform1i(simpleSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
            GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(simplePositionHandle);
        }
        if (paintTexture != null) {
            for (int a = 0; a < paintTexture.length; a++) {
                drawTexture(true, paintTexture[a]);
            }
        }
        if (stickerTexture != null) {
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                if (entity.ptr != 0) {
                    RLottieDrawable.getFrame(entity.ptr, (int) entity.currentFrame, stickerBitmap, 512, 512, stickerBitmap.getRowBytes(), true);
                    applyRoundRadius(entity, stickerBitmap);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, stickerBitmap, 0);
                    entity.currentFrame += entity.framesPerDraw;
                    if (entity.currentFrame >= entity.metadata[0]) {
                        entity.currentFrame = 0;
                    }
                    drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
                } else if (entity.animatedFileDrawable != null) {
                    int lastFrame = (int) entity.currentFrame;
                    entity.currentFrame += entity.framesPerDraw;
                    int currentFrame = (int) entity.currentFrame;
                    while (lastFrame != currentFrame) {
                        entity.animatedFileDrawable.getNextFrame();
                        currentFrame--;
                    }
                    Bitmap frameBitmap = entity.animatedFileDrawable.getBackgroundBitmap();
                    if (frameBitmap != null) {
                        if (stickerCanvas == null && stickerBitmap != null) {
                            stickerCanvas = new Canvas(stickerBitmap);
                            if (stickerBitmap.getHeight() != frameBitmap.getHeight() || stickerBitmap.getWidth() != frameBitmap.getWidth()) {
                                stickerCanvas.scale(stickerBitmap.getWidth() / (float) frameBitmap.getWidth(), stickerBitmap.getHeight() / (float) frameBitmap.getHeight());
                            }
                        }
                        if (stickerBitmap != null) {
                            stickerBitmap.eraseColor(Color.TRANSPARENT);
                            stickerCanvas.drawBitmap(frameBitmap, 0, 0, null);
                            applyRoundRadius(entity, stickerBitmap);
                            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, stickerBitmap, 0);
                            drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
                        }
                    }
                } else if (entity.view != null && entity.canvas != null && entity.bitmap != null) {
                    entity.bitmap.eraseColor(Color.TRANSPARENT);
                    int lastFrame = (int) entity.currentFrame;
                    entity.currentFrame += entity.framesPerDraw;
                    int currentFrame = (int) entity.currentFrame;
                    EditTextEffects editTextEffects = (EditTextEffects) entity.view;
                    editTextEffects.incrementFrames(currentFrame - lastFrame);
                    entity.view.draw(entity.canvas);
                    applyRoundRadius(entity, entity.bitmap);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, entity.bitmap, 0);
                    drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
                } else {
                    if (entity.bitmap != null) {
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, entity.bitmap, 0);
                        drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
                    }
                }
            }
        }
        GLES20.glFinish();
    }

    private void applyRoundRadius(VideoEditedInfo.MediaEntity entity, Bitmap stickerBitmap) {
        if (stickerBitmap == null || entity == null || entity.roundRadius == 0) {
            return;
        }
        if (entity.roundRadiusCanvas == null) {
            entity.roundRadiusCanvas = new Canvas(stickerBitmap);
        }
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

    private void drawTexture(boolean bind, int texture) {
        drawTexture(bind, texture, -10000, -10000, -10000, -10000, 0, false);
    }

    private void drawTexture(boolean bind, int texture, float x, float y, float w, float h, float rotation, boolean mirror) {
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
        GLES20.glVertexAttribPointer(simplePositionHandle, 2, GLES20.GL_FLOAT, false, 8, bitmapVerticesBuffer);

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
            mProgram[a] = createProgram(VERTEX_SHADER, a == 0 ? FRAGMENT_EXTERNAL_SHADER : FRAGMENT_SHADER);
            maPositionHandle[a] = GLES20.glGetAttribLocation(mProgram[a], "aPosition");
            maTextureHandle[a] = GLES20.glGetAttribLocation(mProgram[a], "aTextureCoord");
            muMVPMatrixHandle[a] = GLES20.glGetUniformLocation(mProgram[a], "uMVPMatrix");
            muSTMatrixHandle[a] = GLES20.glGetUniformLocation(mProgram[a], "uSTMatrix");
        }
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureID = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        if (filterShaders != null || imagePath != null || paintPath != null || mediaEntities != null) {
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
                    simpleSourceImageHandle = GLES20.glGetUniformLocation(simpleShaderProgram, "sourceImage");
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
                        if (a == 0 && imagePath != null) {
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
                    if (entity.type == 0) {
                        if ((entity.subType & 1) != 0) {
                            entity.metadata = new int[3];
                            entity.ptr = RLottieDrawable.create(entity.text, null, 512, 512, entity.metadata, false, null, false, 0);
                            entity.framesPerDraw = entity.metadata[1] / videoFps;
                        } else if ((entity.subType & 4) != 0) {
                            entity.animatedFileDrawable = new AnimatedFileDrawable(new File(entity.text), true, 0, 0, null, null, null, 0, UserConfig.selectedAccount, true, 512, 512, null);
                            entity.framesPerDraw = videoFps / 30f;
                            entity.currentFrame = 0;
                        } else {
                            if (Build.VERSION.SDK_INT >= 19) {
                                entity.bitmap = BitmapFactory.decodeFile(entity.text);
                            } else {
                                File path = new File(entity.text);
                                RandomAccessFile file = new RandomAccessFile(path, "r");
                                ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, path.length());
                                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                                bmOptions.inJustDecodeBounds = true;
                                Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                                entity.bitmap = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);
                                Utilities.loadWebpImage(entity.bitmap, buffer, buffer.limit(), null, true);
                                file.close();
                            }
                            if (entity.bitmap != null) {
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
                    } else if (entity.type == 1) {
                        EditTextOutline editText = new EditTextOutline(ApplicationLoader.applicationContext) {
                            {
                                animatedEmojiOffsetX = AndroidUtilities.dp(8);
                                animatedEmojiRawDraw = true;
                                animatedEmojiRawDrawFps = (int) videoFps;
                            }
                        };
                        editText.setBackgroundColor(Color.TRANSPARENT);
                        editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
                        Typeface typeface;
                        if (entity.textTypeface != null && (typeface = entity.textTypeface.getTypeface()) != null) {
                            editText.setTypeface(typeface);
                        }
                        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, entity.fontSize);
                        SpannableString text = new SpannableString(entity.text);
                        boolean containsAnimated = false;
                        for (VideoEditedInfo.EmojiEntity e : entity.entities) {
                            containsAnimated = true;
                            AnimatedEmojiSpan span;
                            if (e.document != null) {
                                span = new AnimatedEmojiSpan(e.document, editText.getPaint().getFontMetricsInt());
                            } else {
                                span = new AnimatedEmojiSpan(e.document_id, editText.getPaint().getFontMetricsInt());
                            }
                            span.cacheType = AnimatedEmojiDrawable.CACHE_TYPE_RENDERING_VIDEO;
                            span.documentAbsolutePath = e.documentAbsolutePath;
                            text.setSpan(span, e.offset, e.offset + e.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        editText.setText(Emoji.replaceEmoji(text, editText.getPaint().getFontMetricsInt(), (int) (editText.getTextSize() * .8f), false));
                        editText.setTextColor(entity.color);

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
                        if ((entity.subType & 1) != 0) {
                            editText.setTextColor(0xffffffff);
                            editText.setStrokeColor(entity.color);
                            editText.setFrameColor(0);
                            editText.setShadowLayer(0, 0, 0, 0);
                        } else if ((entity.subType & 4) != 0) {
                            editText.setTextColor(0xff000000);
                            editText.setStrokeColor(0);
                            editText.setFrameColor(entity.color);
                            editText.setShadowLayer(0, 0, 0, 0);
                        } else {
                            editText.setTextColor(entity.color);
                            editText.setStrokeColor(0);
                            editText.setFrameColor(0);
                            editText.setShadowLayer(5, 0, 1, 0x66000000);
                        }

                        editText.measure(View.MeasureSpec.makeMeasureSpec(entity.viewWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(entity.viewHeight, View.MeasureSpec.EXACTLY));
                        editText.layout(0, 0, entity.viewWidth, entity.viewHeight);
                        entity.bitmap = Bitmap.createBitmap(entity.viewWidth, entity.viewHeight, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(entity.bitmap);
                        editText.draw(canvas);
                        if (containsAnimated) {
                            entity.view = editText;
                            entity.canvas = canvas;
                            entity.framesPerDraw = videoFps / 30f;
                            entity.currentFrame = 0;
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    private int createProgram(String vertexSource, String fragmentSource) {
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
            }
        }
    }

    public void changeFragmentShader(String fragmentExternalShader, String fragmentShader) {
        GLES20.glDeleteProgram(mProgram[0]);
        mProgram[0] = createProgram(VERTEX_SHADER, fragmentExternalShader);
        if (mProgram.length > 1) {
            mProgram[1] = createProgram(VERTEX_SHADER, fragmentShader);
        }

    }
}
