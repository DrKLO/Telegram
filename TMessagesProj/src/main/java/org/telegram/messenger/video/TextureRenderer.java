/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.video;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.ui.Components.FilterShaders;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;
import org.telegram.ui.Components.RLottieDrawable;

import javax.microedition.khronos.opengles.GL10;

import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

public class TextureRenderer {

    private FloatBuffer verticesBuffer;
    private FloatBuffer textureBuffer;
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
    private int videoWidth;
    private int videoHeight;

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

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];
    private int mTextureID = -12345;
    private int mProgram;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    private int simpleShaderProgram;
    private int simplePositionHandle;
    private int simpleInputTexCoordHandle;
    private int simpleSourceImageHandle;

    private int rotationAngle;

    private int[] paintTexture;
    private int[] stickerTexture;
    private Bitmap stickerBitmap;
    private float videoFps;

    private int imageOrientation;

    private boolean blendEnabled;

    private boolean isPhoto;

    public TextureRenderer(int rotation, MediaController.SavedFilterState savedFilterState, String image, String paint, ArrayList<VideoEditedInfo.MediaEntity> entities, int w, int h, float fps, boolean photo) {
        rotationAngle = rotation;
        isPhoto = photo;
        float[] verticesData = {
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f, 1.0f,
                1.0f, 1.0f,
        };
        float[] texData = {
                0.f, 0.f,
                1.f, 0.f,
                0.f, 1.f,
                1.f, 1.f,
        };

        verticesBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        verticesBuffer.put(verticesData).position(0);

        textureBuffer = ByteBuffer.allocateDirect(texData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureBuffer.put(texData).position(0);

        verticesBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        verticesBuffer.put(verticesData).position(0);

        bitmapVerticesBuffer = ByteBuffer.allocateDirect(bitmapData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        bitmapVerticesBuffer.put(bitmapData).position(0);

        Matrix.setIdentityM(mSTMatrix, 0);

        if (savedFilterState != null) {
            filterShaders = new FilterShaders(true);
            filterShaders.setDelegate(FilterShaders.getFilterShadersDelegate(savedFilterState));
        }
        videoWidth = w;
        videoHeight = h;
        imagePath = image;
        paintPath = paint;
        mediaEntities = entities;
        videoFps = fps == 0 ? 30 : fps;
    }

    public int getTextureId() {
        return mTextureID;
    }

    public void drawFrame(SurfaceTexture st, boolean invert) {
        checkGlError("onDrawFrame start");
        if (isPhoto) {
            GLES20.glUseProgram(simpleShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            GLES20.glUniform1i(simpleSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
            GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(simplePositionHandle);
        } else {
            st.getTransformMatrix(mSTMatrix);

            if (blendEnabled) {
                GLES20.glDisable(GLES20.GL_BLEND);
                blendEnabled = false;
            }
            if (filterShaders != null) {
                filterShaders.onVideoFrameUpdate(mSTMatrix);

                GLES20.glViewport(0, 0, videoWidth, videoHeight);
                filterShaders.drawEnhancePass();
                filterShaders.drawSharpenPass();
                filterShaders.drawCustomParamsPass();
                boolean blurred = filterShaders.drawBlurPass();

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

                GLES20.glUseProgram(simpleShaderProgram);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, filterShaders.getRenderTexture(blurred ? 0 : 1));

                GLES20.glUniform1i(simpleSourceImageHandle, 0);
                GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
                GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, filterShaders.getTextureBuffer());
                GLES20.glEnableVertexAttribArray(simplePositionHandle);
                GLES20.glVertexAttribPointer(simplePositionHandle, 2, GLES20.GL_FLOAT, false, 8, filterShaders.getVertexBuffer());
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            } else {
                if (invert) {
                    mSTMatrix[5] = -mSTMatrix[5];
                    mSTMatrix[13] = 1.0f - mSTMatrix[13];
                }
                GLES20.glUseProgram(mProgram);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

                GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 8, verticesBuffer);
                GLES20.glEnableVertexAttribArray(maPositionHandle);
                GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(maTextureHandle);

                GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
                GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                if (paintTexture != null || stickerTexture != null) {
                    GLES20.glUseProgram(simpleShaderProgram);
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                    GLES20.glUniform1i(simpleSourceImageHandle, 0);
                    GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
                    GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                    GLES20.glEnableVertexAttribArray(simplePositionHandle);
                }
            }
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
                    RLottieDrawable.getFrame(entity.ptr, (int) entity.currentFrame, stickerBitmap, 512, 512, stickerBitmap.getRowBytes());
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, stickerBitmap, 0);
                    entity.currentFrame += entity.framesPerDraw;
                    if (entity.currentFrame >= entity.metadata[0]) {
                        entity.currentFrame = 0;
                    }
                    drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
                } else if (entity.bitmap != null) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTexture[0]);
                    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, entity.bitmap, 0);
                    drawTexture(false, stickerTexture[0], entity.x, entity.y, entity.width, entity.height, entity.rotation, (entity.subType & 2) != 0);
                }
            }
        }
        GLES20.glFinish();
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
            float ratio = videoWidth / (float) videoHeight;
            float my = (bitmapData[5] + bitmapData[1]) / 2;
            for (int a = 0; a < 4; a++) {
                float x1 = bitmapData[a * 2    ] - mx;
                float y1 = (bitmapData[a * 2 + 1] - my) / ratio;
                bitmapData[a * 2    ] = (float) (x1 * Math.cos(rotation) - y1 * Math.sin(rotation)) + mx;
                bitmapData[a * 2 + 1] = (float) (x1 * Math.sin(rotation) + y1 * Math.cos(rotation)) * ratio + my;
            }
        }
        bitmapVerticesBuffer.put(bitmapData).position(0);
        GLES20.glVertexAttribPointer(maPositionHandle, 2, GLES20.GL_FLOAT, false, 8, bitmapVerticesBuffer);

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
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureID = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        checkGlError("glBindTexture mTextureID");
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameter");

        Matrix.setIdentityM(mMVPMatrix, 0);
        if (rotationAngle != 0) {
            Matrix.rotateM(mMVPMatrix, 0, rotationAngle, 0, 0, 1);
        }

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
            filterShaders.setRenderData(null, 0, mTextureID, videoWidth, videoHeight);
        }
        if (imagePath != null || paintPath != null) {
            paintTexture = new int[(imagePath != null ? 1 : 0) + (paintPath != null ? 1 : 0)];
            GLES20.glGenTextures(paintTexture.length, paintTexture, 0);
            try {
                for (int a = 0; a < paintTexture.length; a++) {
                    String path;
                    int angle = 0;
                    if (a == 0 && imagePath != null) {
                        path = imagePath;
                        try {
                            ExifInterface exif = new ExifInterface(path);
                            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                            switch (orientation) {
                                case ExifInterface.ORIENTATION_ROTATE_90:
                                    angle = 90;
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_180:
                                    angle = 180;
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    angle = 270;
                                    break;
                            }
                        } catch (Throwable ignore) {

                        }
                    } else {
                        path = paintPath;
                    }
                    Bitmap bitmap = BitmapFactory.decodeFile(path);
                    if (bitmap != null) {
                        if (a == 0 && imagePath != null) {
                            Bitmap newBitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888);
                            newBitmap.eraseColor(0xff000000);
                            Canvas canvas = new Canvas(newBitmap);
                            float scale;
                            if (angle == 90 || angle == 270) {
                                scale = Math.max(bitmap.getHeight() / (float) videoWidth, bitmap.getWidth() / (float) videoHeight);
                            } else {
                                scale = Math.max(bitmap.getWidth() / (float) videoWidth, bitmap.getHeight() / (float) videoHeight);
                            }

                            android.graphics.Matrix matrix = new android.graphics.Matrix();
                            matrix.postTranslate(-bitmap.getWidth() / 2, -bitmap.getHeight() / 2);
                            matrix.postScale(1.0f / scale, 1.0f / scale);
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
                            entity.ptr = RLottieDrawable.create(entity.text, 512, 512, entity.metadata, false, null, false);
                            entity.framesPerDraw = entity.metadata[1] / videoFps;
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
                        EditTextOutline editText = new EditTextOutline(ApplicationLoader.applicationContext);
                        editText.setBackgroundColor(Color.TRANSPARENT);
                        editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
                        editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, entity.fontSize);
                        editText.setText(entity.text);
                        editText.setTextColor(entity.color);
                        editText.setTypeface(null, Typeface.BOLD);
                        editText.setGravity(Gravity.CENTER);
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
        checkGlError("glCreateProgram");
        if (program == 0) {
            return 0;
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }

    public void checkGlError(String op) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    public void release() {
        if (mediaEntities != null) {
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                if (entity.ptr != 0) {
                    RLottieDrawable.destroy(entity.ptr);
                }
            }
        }
    }
}
