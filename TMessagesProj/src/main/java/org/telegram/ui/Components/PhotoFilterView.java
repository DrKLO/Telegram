/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PhotoEditRadioCell;
import org.telegram.ui.Cells.PhotoEditToolCell;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

@SuppressLint("NewApi")
public class PhotoFilterView extends FrameLayout {

    private final static int curveGranularity = 100;
    private final static int curveDataStep = 2;

    private boolean showOriginal;

    private int enhanceTool = 0;
    private int exposureTool = 1;
    private int contrastTool = 2;
    private int saturationTool = 3;
    private int warmthTool = 4;
    private int fadeTool = 5;
    private int highlightsTool = 6;
    private int shadowsTool = 7;
    private int vignetteTool = 8;
    private int grainTool = 9;
    private int sharpenTool = 10;
    private int tintShadowsTool = 11;
    private int tintHighlightsTool = 12;

    private float enhanceValue; //0 100
    private float exposureValue; //-100 100
    private float contrastValue; //-100 100
    private float warmthValue; //-100 100
    private float saturationValue; //-100 100
    private float fadeValue; // 0 100
    private int tintShadowsColor; //0 0xffffffff
    private int tintHighlightsColor; //0 0xffffffff
    private float highlightsValue; //-100 100
    private float shadowsValue; //-100 100
    private float vignetteValue; //0 100
    private float grainValue; //0 100
    private int blurType; //0 none, 1 radial, 2 linear
    private float sharpenValue; //0 100
    private CurvesToolValue curvesToolValue;
    private float blurExcludeSize;
    private Point blurExcludePoint;
    private float blurExcludeBlurSize;
    private float blurAngle;

    private MediaController.SavedFilterState lastState;

    private FrameLayout toolsView;
    private TextView doneTextView;
    private TextView cancelTextView;
    private TextureView textureView;
    private EGLThread eglThread;
    private RecyclerListView recyclerListView;
    private FrameLayout blurLayout;
    private PhotoFilterBlurControl blurControl;
    private PhotoFilterCurvesControl curvesControl;
    private TextView blurOffButton;
    private TextView blurRadialButton;
    private TextView blurLinearButton;
    private FrameLayout curveLayout;
    private RadioButton[] curveRadioButton = new RadioButton[4];

    private int selectedTool;

    private ImageView tuneItem;
    private ImageView blurItem;
    private ImageView curveItem;

    private Bitmap bitmapToEdit;
    private int orientation;

    public static class CurvesValue {

        public float blacksLevel = 0.0f;
        public float shadowsLevel = 25.0f;
        public float midtonesLevel = 50.0f;
        public float highlightsLevel = 75.0f;
        public float whitesLevel = 100.0f;

        public float previousBlacksLevel = 0.0f;
        public float previousShadowsLevel = 25.0f;
        public float previousMidtonesLevel = 50.0f;
        public float previousHighlightsLevel = 75.0f;
        public float previousWhitesLevel = 100.0f;

        public float[] cachedDataPoints;

        public float[] getDataPoints() {
            if (cachedDataPoints == null) {
                interpolateCurve();
            }
            return cachedDataPoints;
        }

        public void saveValues() {
            previousBlacksLevel = blacksLevel;
            previousShadowsLevel = shadowsLevel;
            previousMidtonesLevel = midtonesLevel;
            previousHighlightsLevel = highlightsLevel;
            previousWhitesLevel = whitesLevel;
        }

        public void restoreValues() {
            blacksLevel = previousBlacksLevel;
            shadowsLevel = previousShadowsLevel;
            midtonesLevel = previousMidtonesLevel;
            highlightsLevel = previousHighlightsLevel;
            whitesLevel = previousWhitesLevel;
            interpolateCurve();
        }

        public float[] interpolateCurve() {
            float[] points = new float[] {
                    -0.001f, blacksLevel / 100.0f,
                    0.0f, blacksLevel / 100.0f,
                    0.25f, shadowsLevel / 100.0f,
                    0.5f, midtonesLevel / 100.0f,
                    0.75f, highlightsLevel / 100.0f,
                    1f, whitesLevel / 100.0f,
                    1.001f, whitesLevel / 100.0f
            };

            ArrayList<Float> dataPoints = new ArrayList<>(100);
            ArrayList<Float> interpolatedPoints = new ArrayList<>(100);

            interpolatedPoints.add(points[0]);
            interpolatedPoints.add(points[1]);

            for (int index = 1; index < points.length / 2 - 2; index++) {
                float point0x = points[(index - 1) * 2];
                float point0y = points[(index - 1) * 2 + 1];
                float point1x = points[(index) * 2];
                float point1y = points[(index) * 2 + 1];
                float point2x = points[(index + 1) * 2];
                float point2y = points[(index + 1) * 2 + 1];
                float point3x = points[(index + 2) * 2];
                float point3y = points[(index + 2) * 2 + 1];


                for (int i = 1; i < curveGranularity; i++) {
                    float t = (float) i * (1.0f / (float) curveGranularity);
                    float tt = t * t;
                    float ttt = tt * t;

                    float pix = 0.5f * (2 * point1x + (point2x - point0x) * t + (2 * point0x - 5 * point1x + 4 * point2x - point3x) * tt + (3 * point1x - point0x - 3 * point2x + point3x) * ttt);
                    float piy = 0.5f * (2 * point1y + (point2y - point0y) * t + (2 * point0y - 5 * point1y + 4 * point2y - point3y) * tt + (3 * point1y - point0y - 3 * point2y + point3y) * ttt);

                    piy = Math.max(0, Math.min(1, piy));

                    if (pix > point0x) {
                        interpolatedPoints.add(pix);
                        interpolatedPoints.add(piy);
                    }

                    if ((i - 1) % curveDataStep == 0) {
                        dataPoints.add(piy);
                    }
                }
                interpolatedPoints.add(point2x);
                interpolatedPoints.add(point2y);
            }
            interpolatedPoints.add(points[12]);
            interpolatedPoints.add(points[13]);

            cachedDataPoints = new float[dataPoints.size()];
            for (int a = 0; a < cachedDataPoints.length; a++) {
                cachedDataPoints[a] = dataPoints.get(a);
            }
            float[] retValue = new float[interpolatedPoints.size()];
            for (int a = 0; a < retValue.length; a++) {
                retValue[a] = interpolatedPoints.get(a);
            }
            return retValue;
        }

        public boolean isDefault() {
            return Math.abs(blacksLevel - 0) < 0.00001 && Math.abs(shadowsLevel - 25) < 0.00001 && Math.abs(midtonesLevel - 50) < 0.00001 && Math.abs(highlightsLevel - 75) < 0.00001 && Math.abs(whitesLevel - 100) < 0.00001;
        }
    }

    public static class CurvesToolValue {

        public CurvesValue luminanceCurve = new CurvesValue();
        public CurvesValue redCurve = new CurvesValue();
        public CurvesValue greenCurve = new CurvesValue();
        public CurvesValue blueCurve = new CurvesValue();
        public ByteBuffer curveBuffer = null;

        public int activeType;

        public final static int CurvesTypeLuminance = 0;
        public final static int CurvesTypeRed = 1;
        public final static int CurvesTypeGreen = 2;
        public final static int CurvesTypeBlue = 3;

        public CurvesToolValue() {
            curveBuffer = ByteBuffer.allocateDirect(200 * 4);
            curveBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        public void fillBuffer() {
            curveBuffer.position(0);
            float[] luminanceCurveData = luminanceCurve.getDataPoints();
            float[] redCurveData = redCurve.getDataPoints();
            float[] greenCurveData = greenCurve.getDataPoints();
            float[] blueCurveData = blueCurve.getDataPoints();
            for (int a = 0; a < 200; a++) {
                curveBuffer.put((byte) (redCurveData[a] * 255));
                curveBuffer.put((byte) (greenCurveData[a] * 255));
                curveBuffer.put((byte) (blueCurveData[a] * 255));
                curveBuffer.put((byte) (luminanceCurveData[a] * 255));
            }
            curveBuffer.position(0);
        }

        public boolean shouldBeSkipped() {
            return luminanceCurve.isDefault() && redCurve.isDefault() && greenCurve.isDefault() && blueCurve.isDefault();
        }
    }

    public class EGLThread extends DispatchQueue {

        private final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private final int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLConfig eglConfig;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private GL gl;
        private boolean initied;
        private boolean needUpdateBlurTexture = true;

        private Bitmap currentBitmap;

        private int rgbToHsvShaderProgram;
        private int rgbToHsvPositionHandle;
        private int rgbToHsvInputTexCoordHandle;
        private int rgbToHsvSourceImageHandle;

        private int enhanceShaderProgram;
        private int enhancePositionHandle;
        private int enhanceInputTexCoordHandle;
        private int enhanceSourceImageHandle;
        private int enhanceIntensityHandle;
        private int enhanceInputImageTexture2Handle;

        private int toolsShaderProgram;
        private int positionHandle;
        private int inputTexCoordHandle; //"varying vec2 texCoord;" +
        private int sourceImageHandle; //"uniform sampler2D sourceImage;" +
        private int shadowsHandle; //"uniform float shadows;" +
        private int highlightsHandle; //"uniform float highlights;" +
        private int exposureHandle; //"uniform float exposure;" +
        private int contrastHandle; //"uniform float contrast;" +
        private int saturationHandle; //"uniform float saturation;" +
        private int warmthHandle; //"uniform float warmth;" +
        private int vignetteHandle; //"uniform float vignette;" +
        private int grainHandle; //"uniform float grain;" +
        private int widthHandle; //"uniform float width;" +
        private int heightHandle; //"uniform float height;" +

        private int curvesImageHandle; //"uniform sampler2D curvesImage;" +
        private int skipToneHandle; //"uniform lowp float skipTone;" +
        private int fadeAmountHandle; //"uniform lowp float fadeAmount;" +
        private int shadowsTintIntensityHandle; //"uniform lowp float shadowsTintIntensity;" +
        private int highlightsTintIntensityHandle; //"uniform lowp float highlightsTintIntensity;" +
        private int shadowsTintColorHandle; //"uniform lowp vec3 shadowsTintColor;" +
        private int highlightsTintColorHandle; //"uniform lowp vec3 highlightsTintColor;" +

        private int blurShaderProgram;
        private int blurPositionHandle;
        private int blurInputTexCoordHandle;
        private int blurSourceImageHandle;
        private int blurWidthHandle;
        private int blurHeightHandle;

        private int linearBlurShaderProgram;
        private int linearBlurPositionHandle;
        private int linearBlurInputTexCoordHandle;
        private int linearBlurSourceImageHandle;
        private int linearBlurSourceImage2Handle;
        private int linearBlurExcludeSizeHandle;
        private int linearBlurExcludePointHandle;
        private int linearBlurExcludeBlurSizeHandle;
        private int linearBlurAngleHandle;
        private int linearBlurAspectRatioHandle;

        private int radialBlurShaderProgram;
        private int radialBlurPositionHandle;
        private int radialBlurInputTexCoordHandle;
        private int radialBlurSourceImageHandle;
        private int radialBlurSourceImage2Handle;
        private int radialBlurExcludeSizeHandle;
        private int radialBlurExcludePointHandle;
        private int radialBlurExcludeBlurSizeHandle;
        private int radialBlurAspectRatioHandle;

        private int sharpenShaderProgram;
        private int sharpenHandle;
        private int sharpenWidthHandle;
        private int sharpenHeightHandle;
        private int sharpenPositionHandle;
        private int sharpenInputTexCoordHandle;
        private int sharpenSourceImageHandle;

        private int simpleShaderProgram;
        private int simplePositionHandle;
        private int simpleInputTexCoordHandle;
        private int simpleSourceImageHandle;

        private int[] enhanceTextures = new int[2];
        private int[] renderTexture = new int[3];
        private int[] renderFrameBuffer = new int[3];
        private int[] curveTextures = new int[1];
        private boolean hsvGenerated;
        private int renderBufferWidth;
        private int renderBufferHeight;
        private volatile int surfaceWidth;
        private volatile int surfaceHeight;

        private FloatBuffer vertexBuffer;
        private FloatBuffer textureBuffer;
        private FloatBuffer vertexInvertBuffer;

        private boolean blured;

        private final static int PGPhotoEnhanceHistogramBins = 256;
        private final static int PGPhotoEnhanceSegments = 4;

        private long lastRenderCallTime;

        private static final String radialBlurFragmentShaderCode =
                "varying highp vec2 texCoord;" +
                "uniform sampler2D sourceImage;" +
                "uniform sampler2D inputImageTexture2;" +
                "uniform lowp float excludeSize;" +
                "uniform lowp vec2 excludePoint;" +
                "uniform lowp float excludeBlurSize;" +
                "uniform highp float aspectRatio;" +
                "void main() {" +
                    "lowp vec4 sharpImageColor = texture2D(sourceImage, texCoord);" +
                    "lowp vec4 blurredImageColor = texture2D(inputImageTexture2, texCoord);" +
                    "highp vec2 texCoordToUse = vec2(texCoord.x, (texCoord.y * aspectRatio + 0.5 - 0.5 * aspectRatio));" +
                    "highp float distanceFromCenter = distance(excludePoint, texCoordToUse);" +
                    "gl_FragColor = mix(sharpImageColor, blurredImageColor, smoothstep(excludeSize - excludeBlurSize, excludeSize, distanceFromCenter));" +
                "}";

        private static final String linearBlurFragmentShaderCode =
                "varying highp vec2 texCoord;" +
                "uniform sampler2D sourceImage;" +
                "uniform sampler2D inputImageTexture2;" +
                "uniform lowp float excludeSize;" +
                "uniform lowp vec2 excludePoint;" +
                "uniform lowp float excludeBlurSize;" +
                "uniform highp float angle;" +
                "uniform highp float aspectRatio;" +
                "void main() {" +
                    "lowp vec4 sharpImageColor = texture2D(sourceImage, texCoord);" +
                    "lowp vec4 blurredImageColor = texture2D(inputImageTexture2, texCoord);" +
                    "highp vec2 texCoordToUse = vec2(texCoord.x, (texCoord.y * aspectRatio + 0.5 - 0.5 * aspectRatio));" +
                    "highp float distanceFromCenter = abs((texCoordToUse.x - excludePoint.x) * aspectRatio * cos(angle) + (texCoordToUse.y - excludePoint.y) * sin(angle));" +
                    "gl_FragColor = mix(sharpImageColor, blurredImageColor, smoothstep(excludeSize - excludeBlurSize, excludeSize, distanceFromCenter));" +
                "}";

        private static final String blurVertexShaderCode =
                "attribute vec4 position;" +
                "attribute vec4 inputTexCoord;" +
                "uniform highp float texelWidthOffset;" +
                "uniform highp float texelHeightOffset;" +
                "varying vec2 blurCoordinates[9];" +
                "void main() {" +
                    "gl_Position = position;" +
                    "vec2 singleStepOffset = vec2(texelWidthOffset, texelHeightOffset);" +
                    "blurCoordinates[0] = inputTexCoord.xy;" +
                    "blurCoordinates[1] = inputTexCoord.xy + singleStepOffset * 1.458430;" +
                    "blurCoordinates[2] = inputTexCoord.xy - singleStepOffset * 1.458430;" +
                    "blurCoordinates[3] = inputTexCoord.xy + singleStepOffset * 3.403985;" +
                    "blurCoordinates[4] = inputTexCoord.xy - singleStepOffset * 3.403985;" +
                    "blurCoordinates[5] = inputTexCoord.xy + singleStepOffset * 5.351806;" +
                    "blurCoordinates[6] = inputTexCoord.xy - singleStepOffset * 5.351806;" +
                    "blurCoordinates[7] = inputTexCoord.xy + singleStepOffset * 7.302940;" +
                    "blurCoordinates[8] = inputTexCoord.xy - singleStepOffset * 7.302940;" +
                "}";

        private static final String blurFragmentShaderCode =
                "uniform sampler2D sourceImage;" +
                "varying highp vec2 blurCoordinates[9];" +
                "void main() {" +
                    "lowp vec4 sum = vec4(0.0);" +
                    "sum += texture2D(sourceImage, blurCoordinates[0]) * 0.133571;" +
                    "sum += texture2D(sourceImage, blurCoordinates[1]) * 0.233308;" +
                    "sum += texture2D(sourceImage, blurCoordinates[2]) * 0.233308;" +
                    "sum += texture2D(sourceImage, blurCoordinates[3]) * 0.135928;" +
                    "sum += texture2D(sourceImage, blurCoordinates[4]) * 0.135928;" +
                    "sum += texture2D(sourceImage, blurCoordinates[5]) * 0.051383;" +
                    "sum += texture2D(sourceImage, blurCoordinates[6]) * 0.051383;" +
                    "sum += texture2D(sourceImage, blurCoordinates[7]) * 0.012595;" +
                    "sum += texture2D(sourceImage, blurCoordinates[8]) * 0.012595;" +
                    "gl_FragColor = sum;" +
                "}";

        private static final String rgbToHsvFragmentShaderCode =
                "precision highp float;" +
                "varying vec2 texCoord;" +
                "uniform sampler2D sourceImage;" +
                "vec3 rgb_to_hsv(vec3 c) {" +
                    "vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);" +
                    "vec4 p = c.g < c.b ? vec4(c.bg, K.wz) : vec4(c.gb, K.xy);" +
                    "vec4 q = c.r < p.x ? vec4(p.xyw, c.r) : vec4(c.r, p.yzx);" +
                    "float d = q.x - min(q.w, q.y);" +
                    "float e = 1.0e-10;" +
                    "return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);" +
                "}" +
                "void main() {" +
                    "vec4 texel = texture2D(sourceImage, texCoord);" +
                    "gl_FragColor = vec4(rgb_to_hsv(texel.rgb), texel.a);" +
                "}";

        private static final String enhanceFragmentShaderCode =
                "precision highp float;" +
                "varying vec2 texCoord;" +
                "uniform sampler2D sourceImage;" +
                "uniform sampler2D inputImageTexture2;" +
                "uniform float intensity;" +
                "float enhance(float value) {" +
                    "const vec2 offset = vec2(0.001953125, 0.03125);" +
                    "value = value + offset.x;" +
                    "vec2 coord = (clamp(texCoord, 0.125, 1.0 - 0.125001) - 0.125) * 4.0;" +
                    "vec2 frac = fract(coord);" +
                    "coord = floor(coord);" +
                    "float p00 = float(coord.y * 4.0 + coord.x) * 0.0625 + offset.y;" +
                    "float p01 = float(coord.y * 4.0 + coord.x + 1.0) * 0.0625 + offset.y;" +
                    "float p10 = float((coord.y + 1.0) * 4.0 + coord.x) * 0.0625 + offset.y;" +
                    "float p11 = float((coord.y + 1.0) * 4.0 + coord.x + 1.0) * 0.0625 + offset.y;" +
                    "vec3 c00 = texture2D(inputImageTexture2, vec2(value, p00)).rgb;" +
                    "vec3 c01 = texture2D(inputImageTexture2, vec2(value, p01)).rgb;" +
                    "vec3 c10 = texture2D(inputImageTexture2, vec2(value, p10)).rgb;" +
                    "vec3 c11 = texture2D(inputImageTexture2, vec2(value, p11)).rgb;" +
                    "float c1 = ((c00.r - c00.g) / (c00.b - c00.g));" +
                    "float c2 = ((c01.r - c01.g) / (c01.b - c01.g));" +
                    "float c3 = ((c10.r - c10.g) / (c10.b - c10.g));" +
                    "float c4 = ((c11.r - c11.g) / (c11.b - c11.g));" +
                    "float c1_2 = mix(c1, c2, frac.x);" +
                    "float c3_4 = mix(c3, c4, frac.x);" +
                    "return mix(c1_2, c3_4, frac.y);" +
                "}" +
                "vec3 hsv_to_rgb(vec3 c) {" +
                    "vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);" +
                    "vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);" +
                    "return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);" +
                "}" +
                "void main() {" +
                    "vec4 texel = texture2D(sourceImage, texCoord);" +
                    "vec4 hsv = texel;" +
                    "hsv.y = min(1.0, hsv.y * 1.2);" +
                    "hsv.z = min(1.0, enhance(hsv.z) * 1.1);" +
                    "gl_FragColor = vec4(hsv_to_rgb(mix(texel.xyz, hsv.xyz, intensity)), texel.w);" +
                "}";

        private static final String simpleVertexShaderCode =
                "attribute vec4 position;" +
                "attribute vec2 inputTexCoord;" +
                "varying vec2 texCoord;" +
                "void main() {" +
                    "gl_Position = position;" +
                    "texCoord = inputTexCoord;" +
                "}";

        private static final String simpleFragmentShaderCode =
                "varying highp vec2 texCoord;" +
                "uniform sampler2D sourceImage;" +
                "void main() {" +
                    "gl_FragColor = texture2D(sourceImage, texCoord);" +
                "}";

        private static final String sharpenVertexShaderCode =
                "attribute vec4 position;" +
                "attribute vec2 inputTexCoord;" +
                "varying vec2 texCoord;" +

                "uniform highp float inputWidth;" +
                "uniform highp float inputHeight;" +
                "varying vec2 leftTexCoord;" +
                "varying vec2 rightTexCoord;" +
                "varying vec2 topTexCoord;" +
                "varying vec2 bottomTexCoord;" +

                "void main() {" +
                    "gl_Position = position;" +
                    "texCoord = inputTexCoord;" +
                    "highp vec2 widthStep = vec2(1.0 / inputWidth, 0.0);" +
                    "highp vec2 heightStep = vec2(0.0, 1.0 / inputHeight);" +
                    "leftTexCoord = inputTexCoord - widthStep;" +
                    "rightTexCoord = inputTexCoord + widthStep;" +
                    "topTexCoord = inputTexCoord + heightStep;" +
                    "bottomTexCoord = inputTexCoord - heightStep;" +
                "}";

        private static final String sharpenFragmentShaderCode =
                "precision highp float;" +
                "varying vec2 texCoord;" +
                "varying vec2 leftTexCoord;" +
                "varying vec2 rightTexCoord;" +
                "varying vec2 topTexCoord;" +
                "varying vec2 bottomTexCoord;" +
                "uniform sampler2D sourceImage;" +
                "uniform float sharpen;" +

                "void main() {" +
                    "vec4 result = texture2D(sourceImage, texCoord);" +

                    "vec3 leftTextureColor = texture2D(sourceImage, leftTexCoord).rgb;" +
                    "vec3 rightTextureColor = texture2D(sourceImage, rightTexCoord).rgb;" +
                    "vec3 topTextureColor = texture2D(sourceImage, topTexCoord).rgb;" +
                    "vec3 bottomTextureColor = texture2D(sourceImage, bottomTexCoord).rgb;" +
                    "result.rgb = result.rgb * (1.0 + 4.0 * sharpen) - (leftTextureColor + rightTextureColor + topTextureColor + bottomTextureColor) * sharpen;" +

                    "gl_FragColor = result;" +
                "}";

        private static final String toolsFragmentShaderCode =
                "varying highp vec2 texCoord;" +
                "uniform sampler2D sourceImage;" +
                "uniform highp float width;" +
                "uniform highp float height;" +
                "uniform sampler2D curvesImage;" +
                "uniform lowp float skipTone;" +
                "uniform lowp float shadows;" +
                "const mediump vec3 hsLuminanceWeighting = vec3(0.3, 0.3, 0.3);" +
                "uniform lowp float highlights;" +
                "uniform lowp float contrast;" +
                "uniform lowp float fadeAmount;" +
                "const mediump vec3 satLuminanceWeighting = vec3(0.2126, 0.7152, 0.0722);" +
                "uniform lowp float saturation;" +
                "uniform lowp float shadowsTintIntensity;" +
                "uniform lowp float highlightsTintIntensity;" +
                "uniform lowp vec3 shadowsTintColor;" +
                "uniform lowp vec3 highlightsTintColor;" +
                "uniform lowp float exposure;" +
                "uniform lowp float warmth;" +
                "uniform lowp float grain;" +
                "const lowp float permTexUnit = 1.0 / 256.0;" +
                "const lowp float permTexUnitHalf = 0.5 / 256.0;" +
                "const lowp float grainsize = 2.3;" +
                "uniform lowp float vignette;" +
                "highp float getLuma(highp vec3 rgbP) {" +
                    "return (0.299 * rgbP.r) + (0.587 * rgbP.g) + (0.114 * rgbP.b);" +
                "}" +
                "lowp vec3 rgbToHsv(lowp vec3 c) {" +
                    "highp vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);" +
                    "highp vec4 p = c.g < c.b ? vec4(c.bg, K.wz) : vec4(c.gb, K.xy);" +
                    "highp vec4 q = c.r < p.x ? vec4(p.xyw, c.r) : vec4(c.r, p.yzx);" +
                    "highp float d = q.x - min(q.w, q.y);" +
                    "highp float e = 1.0e-10;" +
                    "return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);" +
                "}" +
                "lowp vec3 hsvToRgb(lowp vec3 c) {" +
                    "highp vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);" +
                    "highp vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);" +
                    "return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);" +
                "}" +
                "highp vec3 rgbToHsl(highp vec3 color) {" +
                    "highp vec3 hsl;" +
                    "highp float fmin = min(min(color.r, color.g), color.b);" +
                    "highp float fmax = max(max(color.r, color.g), color.b);" +
                    "highp float delta = fmax - fmin;" +
                    "hsl.z = (fmax + fmin) / 2.0;" +
                    "if (delta == 0.0) {" +
                        "hsl.x = 0.0;" +
                        "hsl.y = 0.0;" +
                    "} else {" +
                        "if (hsl.z < 0.5) {" +
                            "hsl.y = delta / (fmax + fmin);" +
                        "} else {" +
                            "hsl.y = delta / (2.0 - fmax - fmin);" +
                        "}" +
                        "highp float deltaR = (((fmax - color.r) / 6.0) + (delta / 2.0)) / delta;" +
                        "highp float deltaG = (((fmax - color.g) / 6.0) + (delta / 2.0)) / delta;" +
                        "highp float deltaB = (((fmax - color.b) / 6.0) + (delta / 2.0)) / delta;" +
                        "if (color.r == fmax) {" +
                            "hsl.x = deltaB - deltaG;" +
                        "} else if (color.g == fmax) {" +
                            "hsl.x = (1.0 / 3.0) + deltaR - deltaB;" +
                        "} else if (color.b == fmax) {" +
                            "hsl.x = (2.0 / 3.0) + deltaG - deltaR;" +
                        "}" +
                        "if (hsl.x < 0.0) {" +
                            "hsl.x += 1.0;" +
                        "} else if (hsl.x > 1.0) {" +
                            "hsl.x -= 1.0;" +
                        "}" +
                    "}" +
                    "return hsl;" +
                "}" +
                "highp float hueToRgb(highp float f1, highp float f2, highp float hue) {" +
                    "if (hue < 0.0) {" +
                        "hue += 1.0;" +
                    "} else if (hue > 1.0) {" +
                        "hue -= 1.0;" +
                    "}" +
                    "highp float res;" +
                    "if ((6.0 * hue) < 1.0) {" +
                        "res = f1 + (f2 - f1) * 6.0 * hue;" +
                    "} else if ((2.0 * hue) < 1.0) {" +
                        "res = f2;" +
                    "} else if ((3.0 * hue) < 2.0) {" +
                        "res = f1 + (f2 - f1) * ((2.0 / 3.0) - hue) * 6.0;" +
                    "} else {" +
                        "res = f1;" +
                    "} return res;" +
                "}" +
                "highp vec3 hslToRgb(highp vec3 hsl) {" +
                    "if (hsl.y == 0.0) {" +
                        "return vec3(hsl.z);" +
                    "} else {" +
                        "highp float f2;" +
                        "if (hsl.z < 0.5) {" +
                            "f2 = hsl.z * (1.0 + hsl.y);" +
                        "} else {" +
                            "f2 = (hsl.z + hsl.y) - (hsl.y * hsl.z);" +
                        "}" +
                        "highp float f1 = 2.0 * hsl.z - f2;" +
                        "return vec3(hueToRgb(f1, f2, hsl.x + (1.0/3.0)), hueToRgb(f1, f2, hsl.x), hueToRgb(f1, f2, hsl.x - (1.0/3.0)));" +
                    "}" +
                "}" +
                "highp vec3 rgbToYuv(highp vec3 inP) {" +
                    "highp float luma = getLuma(inP);" +
                    "return vec3(luma, (1.0 / 1.772) * (inP.b - luma), (1.0 / 1.402) * (inP.r - luma));" +
                "}" +
                "lowp vec3 yuvToRgb(highp vec3 inP) {" +
                    "return vec3(1.402 * inP.b + inP.r, (inP.r - (0.299 * 1.402 / 0.587) * inP.b - (0.114 * 1.772 / 0.587) * inP.g), 1.772 * inP.g + inP.r);" +
                "}" +
                "lowp float easeInOutSigmoid(lowp float value, lowp float strength) {" +
                    "if (value > 0.5) {" +
                        "return 1.0 - pow(2.0 - 2.0 * value, 1.0 / (1.0 - strength)) * 0.5;" +
                    "} else {" +
                        "return pow(2.0 * value, 1.0 / (1.0 - strength)) * 0.5;" +
                    "}" +
                "}" +
                "lowp vec3 applyLuminanceCurve(lowp vec3 pixel) {" +
                    "highp float index = floor(clamp(pixel.z / (1.0 / 200.0), 0.0, 199.0));" +
                    "pixel.y = mix(0.0, pixel.y, smoothstep(0.0, 0.1, pixel.z) * (1.0 - smoothstep(0.8, 1.0, pixel.z)));" +
                    "pixel.z = texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).a;" +
                    "return pixel;" +
                "}" +
                "lowp vec3 applyRGBCurve(lowp vec3 pixel) {" +
                    "highp float index = floor(clamp(pixel.r / (1.0 / 200.0), 0.0, 199.0));" +
                    "pixel.r = texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).r;" +
                    "index = floor(clamp(pixel.g / (1.0 / 200.0), 0.0, 199.0));" +
                    "pixel.g = clamp(texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).g, 0.0, 1.0);" +
                    "index = floor(clamp(pixel.b / (1.0 / 200.0), 0.0, 199.0));" +
                    "pixel.b = clamp(texture2D(curvesImage, vec2(1.0 / 200.0 * index, 0)).b, 0.0, 1.0);" +
                    "return pixel;" +
                "}" +
                "highp vec3 fadeAdjust(highp vec3 color, highp float fadeVal) {" +
                    "return (color * (1.0 - fadeVal)) + ((color + (vec3(-0.9772) * pow(vec3(color), vec3(3.0)) + vec3(1.708) * pow(vec3(color), vec3(2.0)) + vec3(-0.1603) * vec3(color) + vec3(0.2878) - color * vec3(0.9))) * fadeVal);" +
                "}" +
                "lowp vec3 tintRaiseShadowsCurve(lowp vec3 color) {" +
                    "return vec3(-0.003671) * pow(color, vec3(3.0)) + vec3(0.3842) * pow(color, vec3(2.0)) + vec3(0.3764) * color + vec3(0.2515);" +
                "}" +
                "lowp vec3 tintShadows(lowp vec3 texel, lowp vec3 tintColor, lowp float tintAmount) {" +
                    "return clamp(mix(texel, mix(texel, tintRaiseShadowsCurve(texel), tintColor), tintAmount), 0.0, 1.0);" +
                "} " +
                "lowp vec3 tintHighlights(lowp vec3 texel, lowp vec3 tintColor, lowp float tintAmount) {" +
                    "return clamp(mix(texel, mix(texel, vec3(1.0) - tintRaiseShadowsCurve(vec3(1.0) - texel), (vec3(1.0) - tintColor)), tintAmount), 0.0, 1.0);" +
                "}" +
                "highp vec4 rnm(in highp vec2 tc) {" +
                    "highp float noise = sin(dot(tc, vec2(12.9898, 78.233))) * 43758.5453;" +
                    "return vec4(fract(noise), fract(noise * 1.2154), fract(noise * 1.3453), fract(noise * 1.3647)) * 2.0 - 1.0;" +
                "}" +
                "highp float fade(in highp float t) {" +
                    "return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);" +
                "}" +
                "highp float pnoise3D(in highp vec3 p) {" +
                    "highp vec3 pi = permTexUnit * floor(p) + permTexUnitHalf;" +
                    "highp vec3 pf = fract(p);" +
                    "highp float perm = rnm(pi.xy).a;" +
                    "highp float n000 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf);" +
                    "highp float n001 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(0.0, 0.0, 1.0));" +
                    "perm = rnm(pi.xy + vec2(0.0, permTexUnit)).a;" +
                    "highp float n010 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf - vec3(0.0, 1.0, 0.0));" +
                    "highp float n011 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(0.0, 1.0, 1.0));" +
                    "perm = rnm(pi.xy + vec2(permTexUnit, 0.0)).a;" +
                    "highp float n100 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf - vec3(1.0, 0.0, 0.0));" +
                    "highp float n101 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(1.0, 0.0, 1.0));" +
                    "perm = rnm(pi.xy + vec2(permTexUnit, permTexUnit)).a;" +
                    "highp float n110 = dot(rnm(vec2(perm, pi.z)).rgb * 4.0 - 1.0, pf - vec3(1.0, 1.0, 0.0));" +
                    "highp float n111 = dot(rnm(vec2(perm, pi.z + permTexUnit)).rgb * 4.0 - 1.0, pf - vec3(1.0, 1.0, 1.0));" +
                    "highp vec4 n_x = mix(vec4(n000, n001, n010, n011), vec4(n100, n101, n110, n111), fade(pf.x));" +
                    "highp vec2 n_xy = mix(n_x.xy, n_x.zw, fade(pf.y));" +
                    "return mix(n_xy.x, n_xy.y, fade(pf.z));" +
                "}" +
                "lowp vec2 coordRot(in lowp vec2 tc, in lowp float angle) {" +
                    "return vec2(((tc.x * 2.0 - 1.0) * cos(angle) - (tc.y * 2.0 - 1.0) * sin(angle)) * 0.5 + 0.5, ((tc.y * 2.0 - 1.0) * cos(angle) + (tc.x * 2.0 - 1.0) * sin(angle)) * 0.5 + 0.5);" +
                "}" +
                "void main() {" +
                    "lowp vec4 source = texture2D(sourceImage, texCoord);" +
                    "lowp vec4 result = source;" +
                    "const lowp float toolEpsilon = 0.005;" +
                    "if (skipTone < toolEpsilon) {" +
                        "result = vec4(applyRGBCurve(hslToRgb(applyLuminanceCurve(rgbToHsl(result.rgb)))), result.a);" +
                    "}" +
                    "mediump float hsLuminance = dot(result.rgb, hsLuminanceWeighting);" +
                    "mediump float shadow = clamp((pow(hsLuminance, 1.0 / shadows) + (-0.76) * pow(hsLuminance, 2.0 / shadows)) - hsLuminance, 0.0, 1.0);" +
                    "mediump float highlight = clamp((1.0 - (pow(1.0 - hsLuminance, 1.0 / (2.0 - highlights)) + (-0.8) * pow(1.0 - hsLuminance, 2.0 / (2.0 - highlights)))) - hsLuminance, -1.0, 0.0);" +
                    "lowp vec3 hsresult = vec3(0.0, 0.0, 0.0) + ((hsLuminance + shadow + highlight) - 0.0) * ((result.rgb - vec3(0.0, 0.0, 0.0)) / (hsLuminance - 0.0));" +
                    "mediump float contrastedLuminance = ((hsLuminance - 0.5) * 1.5) + 0.5;" +
                    "mediump float whiteInterp = contrastedLuminance * contrastedLuminance * contrastedLuminance;" +
                    "mediump float whiteTarget = clamp(highlights, 1.0, 2.0) - 1.0;" +
                    "hsresult = mix(hsresult, vec3(1.0), whiteInterp * whiteTarget);" +
                    "mediump float invContrastedLuminance = 1.0 - contrastedLuminance;" +
                    "mediump float blackInterp = invContrastedLuminance * invContrastedLuminance * invContrastedLuminance;" +
                    "mediump float blackTarget = 1.0 - clamp(shadows, 0.0, 1.0);" +
                    "hsresult = mix(hsresult, vec3(0.0), blackInterp * blackTarget);" +
                    "result = vec4(hsresult.rgb, result.a);" +
                    "result = vec4(clamp(((result.rgb - vec3(0.5)) * contrast + vec3(0.5)), 0.0, 1.0), result.a);" +
                    "if (abs(fadeAmount) > toolEpsilon) {" +
                        "result.rgb = fadeAdjust(result.rgb, fadeAmount);" +
                    "}" +
                    "lowp float satLuminance = dot(result.rgb, satLuminanceWeighting);" +
                    "lowp vec3 greyScaleColor = vec3(satLuminance);" +
                    "result = vec4(clamp(mix(greyScaleColor, result.rgb, saturation), 0.0, 1.0), result.a);" +
                    "if (abs(shadowsTintIntensity) > toolEpsilon) {" +
                        "result.rgb = tintShadows(result.rgb, shadowsTintColor, shadowsTintIntensity * 2.0);" +
                    "}" +
                    "if (abs(highlightsTintIntensity) > toolEpsilon) {" +
                        "result.rgb = tintHighlights(result.rgb, highlightsTintColor, highlightsTintIntensity * 2.0);" +
                    "}" +
                    "if (abs(exposure) > toolEpsilon) {" +
                        "mediump float mag = exposure * 1.045;" +
                        "mediump float exppower = 1.0 + abs(mag);" +
                        "if (mag < 0.0) {" +
                            "exppower = 1.0 / exppower;" +
                        "}" +
                        "result.r = 1.0 - pow((1.0 - result.r), exppower);" +
                        "result.g = 1.0 - pow((1.0 - result.g), exppower);" +
                        "result.b = 1.0 - pow((1.0 - result.b), exppower);" +
                    "}" +
                    "if (abs(warmth) > toolEpsilon) {" +
                        "highp vec3 yuvVec;" +
                        "if (warmth > 0.0 ) {" +
                            "yuvVec = vec3(0.1765, -0.1255, 0.0902);" +
                        "} else {" +
                            "yuvVec = -vec3(0.0588, 0.1569, -0.1255);" +
                        "}" +
                        "highp vec3 yuvColor = rgbToYuv(result.rgb);" +
                        "highp float luma = yuvColor.r;" +
                        "highp float curveScale = sin(luma * 3.14159);" +
                        "yuvColor += 0.375 * warmth * curveScale * yuvVec;" +
                        "result.rgb = yuvToRgb(yuvColor);" +
                    "}" +
                    "if (abs(grain) > toolEpsilon) {" +
                        "highp vec3 rotOffset = vec3(1.425, 3.892, 5.835);" +
                        "highp vec2 rotCoordsR = coordRot(texCoord, rotOffset.x);" +
                        "highp vec3 noise = vec3(pnoise3D(vec3(rotCoordsR * vec2(width / grainsize, height / grainsize),0.0)));" +
                        "lowp vec3 lumcoeff = vec3(0.299,0.587,0.114);" +
                        "lowp float luminance = dot(result.rgb, lumcoeff);" +
                        "lowp float lum = smoothstep(0.2, 0.0, luminance);" +
                        "lum += luminance;" +
                        "noise = mix(noise,vec3(0.0),pow(lum,4.0));" +
                        "result.rgb = result.rgb + noise * grain;" +
                    "}" +
                    "if (abs(vignette) > toolEpsilon) {" +
                        "const lowp float midpoint = 0.7;" +
                        "const lowp float fuzziness = 0.62;" +
                        "lowp float radDist = length(texCoord - 0.5) / sqrt(0.5);" +
                        "lowp float mag = easeInOutSigmoid(radDist * midpoint, fuzziness) * vignette * 0.645;" +
                        "result.rgb = mix(pow(result.rgb, vec3(1.0 / (1.0 - mag))), vec3(0.0), mag * mag);" +
                    "}" +
                    "gl_FragColor = result;" +
                "}";

        public EGLThread(SurfaceTexture surface, Bitmap bitmap) {
            super("EGLThread");
            surfaceTexture = surface;
            currentBitmap = bitmap;
        }

        private int loadShader(int type, String shaderCode) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, shaderCode);
            GLES20.glCompileShader(shader);
            int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e(GLES20.glGetShaderInfoLog(shader));
                }
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private boolean initGL() {
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = new int[] {
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
            if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            } else if (configsCount[0] > 0) {
                eglConfig = configs[0];
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglConfig not initialized");
                }
                finish();
                return false;
            }

            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            if (eglContext == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            if (surfaceTexture instanceof SurfaceTexture) {
                eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            } else {
                finish();
                return false;
            }

            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            gl = eglContext.getGL();


            float squareCoordinates[] = {
                    -1.0f, 1.0f,
                    1.0f, 1.0f,
                    -1.0f, -1.0f,
                    1.0f, -1.0f};

            ByteBuffer bb = ByteBuffer.allocateDirect(squareCoordinates.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(squareCoordinates);
            vertexBuffer.position(0);

            float squareCoordinates2[] = {
                    -1.0f, -1.0f,
                    1.0f, -1.0f,
                    -1.0f, 1.0f,
                    1.0f, 1.0f};

            bb = ByteBuffer.allocateDirect(squareCoordinates2.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexInvertBuffer = bb.asFloatBuffer();
            vertexInvertBuffer.put(squareCoordinates2);
            vertexInvertBuffer.position(0);

            float textureCoordinates[] = {
                    0.0f, 0.0f,
                    1.0f, 0.0f,
                    0.0f, 1.0f,
                    1.0f, 1.0f,
            };

            bb = ByteBuffer.allocateDirect(textureCoordinates.length * 4);
            bb.order(ByteOrder.nativeOrder());
            textureBuffer = bb.asFloatBuffer();
            textureBuffer.put(textureCoordinates);
            textureBuffer.position(0);

            GLES20.glGenTextures(1, curveTextures, 0);
            GLES20.glGenTextures(2, enhanceTextures, 0);

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, toolsFragmentShaderCode);

            if (vertexShader != 0 && fragmentShader != 0) {
                toolsShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(toolsShaderProgram, vertexShader);
                GLES20.glAttachShader(toolsShaderProgram, fragmentShader);
                GLES20.glBindAttribLocation(toolsShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(toolsShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(toolsShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(toolsShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    /*String infoLog = GLES20.glGetProgramInfoLog(toolsShaderProgram);
                    FileLog.e("link error = " + infoLog);*/
                    GLES20.glDeleteProgram(toolsShaderProgram);
                    toolsShaderProgram = 0;
                } else {
                    positionHandle = GLES20.glGetAttribLocation(toolsShaderProgram, "position");
                    inputTexCoordHandle = GLES20.glGetAttribLocation(toolsShaderProgram, "inputTexCoord");
                    sourceImageHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "sourceImage");
                    shadowsHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "shadows");
                    highlightsHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "highlights");
                    exposureHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "exposure");
                    contrastHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "contrast");
                    saturationHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "saturation");
                    warmthHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "warmth");
                    vignetteHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "vignette");
                    grainHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "grain");
                    widthHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "width");
                    heightHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "height");
                    curvesImageHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "curvesImage");
                    skipToneHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "skipTone");
                    fadeAmountHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "fadeAmount");
                    shadowsTintIntensityHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "shadowsTintIntensity");
                    highlightsTintIntensityHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "highlightsTintIntensity");
                    shadowsTintColorHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "shadowsTintColor");
                    highlightsTintColorHandle = GLES20.glGetUniformLocation(toolsShaderProgram, "highlightsTintColor");
                }
            } else {
                finish();
                return false;
            }

            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, sharpenVertexShaderCode);
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, sharpenFragmentShaderCode);

            if (vertexShader != 0 && fragmentShader != 0) {
                sharpenShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(sharpenShaderProgram, vertexShader);
                GLES20.glAttachShader(sharpenShaderProgram, fragmentShader);
                GLES20.glBindAttribLocation(sharpenShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(sharpenShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(sharpenShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(sharpenShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(sharpenShaderProgram);
                    sharpenShaderProgram = 0;
                } else {
                    sharpenPositionHandle = GLES20.glGetAttribLocation(sharpenShaderProgram, "position");
                    sharpenInputTexCoordHandle = GLES20.glGetAttribLocation(sharpenShaderProgram, "inputTexCoord");
                    sharpenSourceImageHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "sourceImage");
                    sharpenWidthHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "inputWidth");
                    sharpenHeightHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "inputHeight");
                    sharpenHandle = GLES20.glGetUniformLocation(sharpenShaderProgram, "sharpen");
                }
            } else {
                finish();
                return false;
            }

            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, blurVertexShaderCode);
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, blurFragmentShaderCode);

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
                    blurSourceImageHandle = GLES20.glGetUniformLocation(blurShaderProgram, "sourceImage");
                    blurWidthHandle = GLES20.glGetUniformLocation(blurShaderProgram, "texelWidthOffset");
                    blurHeightHandle = GLES20.glGetUniformLocation(blurShaderProgram, "texelHeightOffset");
                }
            } else {
                finish();
                return false;
            }

            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, linearBlurFragmentShaderCode);

            if (vertexShader != 0 && fragmentShader != 0) {
                linearBlurShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(linearBlurShaderProgram, vertexShader);
                GLES20.glAttachShader(linearBlurShaderProgram, fragmentShader);
                GLES20.glBindAttribLocation(linearBlurShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(linearBlurShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(linearBlurShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(linearBlurShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(linearBlurShaderProgram);
                    linearBlurShaderProgram = 0;
                } else {
                    linearBlurPositionHandle = GLES20.glGetAttribLocation(linearBlurShaderProgram, "position");
                    linearBlurInputTexCoordHandle = GLES20.glGetAttribLocation(linearBlurShaderProgram, "inputTexCoord");
                    linearBlurSourceImageHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "sourceImage");
                    linearBlurSourceImage2Handle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "inputImageTexture2");
                    linearBlurExcludeSizeHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "excludeSize");
                    linearBlurExcludePointHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "excludePoint");
                    linearBlurExcludeBlurSizeHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "excludeBlurSize");
                    linearBlurAngleHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "angle");
                    linearBlurAspectRatioHandle = GLES20.glGetUniformLocation(linearBlurShaderProgram, "aspectRatio");
                }
            } else {
                finish();
                return false;
            }

            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, radialBlurFragmentShaderCode);

            if (vertexShader != 0 && fragmentShader != 0) {
                radialBlurShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(radialBlurShaderProgram, vertexShader);
                GLES20.glAttachShader(radialBlurShaderProgram, fragmentShader);
                GLES20.glBindAttribLocation(radialBlurShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(radialBlurShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(radialBlurShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(radialBlurShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(radialBlurShaderProgram);
                    radialBlurShaderProgram = 0;
                } else {
                    radialBlurPositionHandle = GLES20.glGetAttribLocation(radialBlurShaderProgram, "position");
                    radialBlurInputTexCoordHandle = GLES20.glGetAttribLocation(radialBlurShaderProgram, "inputTexCoord");
                    radialBlurSourceImageHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "sourceImage");
                    radialBlurSourceImage2Handle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "inputImageTexture2");
                    radialBlurExcludeSizeHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "excludeSize");
                    radialBlurExcludePointHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "excludePoint");
                    radialBlurExcludeBlurSizeHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "excludeBlurSize");
                    radialBlurAspectRatioHandle = GLES20.glGetUniformLocation(radialBlurShaderProgram, "aspectRatio");
                }
            } else {
                finish();
                return false;
            }

            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, rgbToHsvFragmentShaderCode);
            if (vertexShader != 0 && fragmentShader != 0) {
                rgbToHsvShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(rgbToHsvShaderProgram, vertexShader);
                GLES20.glAttachShader(rgbToHsvShaderProgram, fragmentShader);
                GLES20.glBindAttribLocation(rgbToHsvShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(rgbToHsvShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(rgbToHsvShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(rgbToHsvShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(rgbToHsvShaderProgram);
                    rgbToHsvShaderProgram = 0;
                } else {
                    rgbToHsvPositionHandle = GLES20.glGetAttribLocation(rgbToHsvShaderProgram, "position");
                    rgbToHsvInputTexCoordHandle = GLES20.glGetAttribLocation(rgbToHsvShaderProgram, "inputTexCoord");
                    rgbToHsvSourceImageHandle = GLES20.glGetUniformLocation(rgbToHsvShaderProgram, "sourceImage");
                }
            } else {
                finish();
                return false;
            }

            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, enhanceFragmentShaderCode);
            if (vertexShader != 0 && fragmentShader != 0) {
                enhanceShaderProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(enhanceShaderProgram, vertexShader);
                GLES20.glAttachShader(enhanceShaderProgram, fragmentShader);
                GLES20.glBindAttribLocation(enhanceShaderProgram, 0, "position");
                GLES20.glBindAttribLocation(enhanceShaderProgram, 1, "inputTexCoord");

                GLES20.glLinkProgram(enhanceShaderProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(enhanceShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(enhanceShaderProgram);
                    enhanceShaderProgram = 0;
                } else {
                    enhancePositionHandle = GLES20.glGetAttribLocation(enhanceShaderProgram, "position");
                    enhanceInputTexCoordHandle = GLES20.glGetAttribLocation(enhanceShaderProgram, "inputTexCoord");
                    enhanceSourceImageHandle = GLES20.glGetUniformLocation(enhanceShaderProgram, "sourceImage");
                    enhanceIntensityHandle = GLES20.glGetUniformLocation(enhanceShaderProgram, "intensity");
                    enhanceInputImageTexture2Handle = GLES20.glGetUniformLocation(enhanceShaderProgram, "inputImageTexture2");
                }
            } else {
                finish();
                return false;
            }

            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
            fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, simpleFragmentShaderCode);
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
            } else {
                finish();
                return false;
            }

            if (currentBitmap != null) {
                loadTexture(currentBitmap);
            }

            return true;
        }

        public void finish() {
            if (eglSurface != null) {
                egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = null;
            }
            if (eglContext != null) {
                egl10.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }
            if (eglDisplay != null) {
                egl10.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
        }

        private void drawEnhancePass() {
            if (!hsvGenerated) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
                GLES20.glClear(0);

                GLES20.glUseProgram(rgbToHsvShaderProgram);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
                GLES20.glUniform1i(rgbToHsvSourceImageHandle, 0);
                GLES20.glEnableVertexAttribArray(rgbToHsvInputTexCoordHandle);
                GLES20.glVertexAttribPointer(rgbToHsvInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(rgbToHsvPositionHandle);
                GLES20.glVertexAttribPointer(rgbToHsvPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                ByteBuffer hsvBuffer = ByteBuffer.allocateDirect(renderBufferWidth * renderBufferHeight * 4);
                GLES20.glReadPixels(0, 0, renderBufferWidth, renderBufferHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, hsvBuffer);

                GLES20.glBindTexture(GL10.GL_TEXTURE_2D, enhanceTextures[0]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, hsvBuffer);

                ByteBuffer buffer = null;
                try {
                    buffer = ByteBuffer.allocateDirect(PGPhotoEnhanceSegments * PGPhotoEnhanceSegments * PGPhotoEnhanceHistogramBins * 4);
                    Utilities.calcCDT(hsvBuffer, renderBufferWidth, renderBufferHeight, buffer);
                } catch (Exception e) {
                    FileLog.e(e);
                }

                GLES20.glBindTexture(GL10.GL_TEXTURE_2D, enhanceTextures[1]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 16, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

                hsvGenerated = true;
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);
            GLES20.glClear(0);

            GLES20.glUseProgram(enhanceShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, enhanceTextures[0]);
            GLES20.glUniform1i(enhanceSourceImageHandle, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, enhanceTextures[1]);
            GLES20.glUniform1i(enhanceInputImageTexture2Handle, 1);
            if (showOriginal) {
                GLES20.glUniform1f(enhanceIntensityHandle, 0);
            } else {
                GLES20.glUniform1f(enhanceIntensityHandle, getEnhanceValue());
            }

            GLES20.glEnableVertexAttribArray(enhanceInputTexCoordHandle);
            GLES20.glVertexAttribPointer(enhanceInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(enhancePositionHandle);
            GLES20.glVertexAttribPointer(enhancePositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        private void drawSharpenPass() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
            GLES20.glClear(0);

            GLES20.glUseProgram(sharpenShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
            GLES20.glUniform1i(sharpenSourceImageHandle, 0);
            if (showOriginal) {
                GLES20.glUniform1f(sharpenHandle, 0);
            } else {
                GLES20.glUniform1f(sharpenHandle, getSharpenValue());
            }
            GLES20.glUniform1f(sharpenWidthHandle, renderBufferWidth);
            GLES20.glUniform1f(sharpenHeightHandle, renderBufferHeight);
            GLES20.glEnableVertexAttribArray(sharpenInputTexCoordHandle);
            GLES20.glVertexAttribPointer(sharpenInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(sharpenPositionHandle);
            GLES20.glVertexAttribPointer(sharpenPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        private void drawCustomParamsPass() {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);
            GLES20.glClear(0);

            GLES20.glUseProgram(toolsShaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
            GLES20.glUniform1i(sourceImageHandle, 0);
            if (showOriginal) {
                GLES20.glUniform1f(shadowsHandle, 1);
                GLES20.glUniform1f(highlightsHandle, 1);
                GLES20.glUniform1f(exposureHandle, 0);
                GLES20.glUniform1f(contrastHandle, 1);
                GLES20.glUniform1f(saturationHandle, 1);
                GLES20.glUniform1f(warmthHandle, 0);
                GLES20.glUniform1f(vignetteHandle, 0);
                GLES20.glUniform1f(grainHandle, 0);
                GLES20.glUniform1f(fadeAmountHandle, 0);
                GLES20.glUniform3f(highlightsTintColorHandle, 0, 0, 0);
                GLES20.glUniform1f(highlightsTintIntensityHandle, 0);
                GLES20.glUniform3f(shadowsTintColorHandle, 0, 0, 0);
                GLES20.glUniform1f(shadowsTintIntensityHandle, 0);
                GLES20.glUniform1f(skipToneHandle, 1);
            } else {
                GLES20.glUniform1f(shadowsHandle, getShadowsValue());
                GLES20.glUniform1f(highlightsHandle, getHighlightsValue());
                GLES20.glUniform1f(exposureHandle, getExposureValue());
                GLES20.glUniform1f(contrastHandle, getContrastValue());
                GLES20.glUniform1f(saturationHandle, getSaturationValue());
                GLES20.glUniform1f(warmthHandle, getWarmthValue());
                GLES20.glUniform1f(vignetteHandle, getVignetteValue());
                GLES20.glUniform1f(grainHandle, getGrainValue());
                GLES20.glUniform1f(fadeAmountHandle, getFadeValue());
                GLES20.glUniform3f(highlightsTintColorHandle, (tintHighlightsColor >> 16 & 0xff) / 255.0f, (tintHighlightsColor >> 8 & 0xff) / 255.0f, (tintHighlightsColor & 0xff) / 255.0f);
                GLES20.glUniform1f(highlightsTintIntensityHandle, getTintHighlightsIntensityValue());
                GLES20.glUniform3f(shadowsTintColorHandle, (tintShadowsColor >> 16 & 0xff) / 255.0f, (tintShadowsColor >> 8 & 0xff) / 255.0f, (tintShadowsColor & 0xff) / 255.0f);
                GLES20.glUniform1f(shadowsTintIntensityHandle, getTintShadowsIntensityValue());
                boolean skipTone = curvesToolValue.shouldBeSkipped();
                GLES20.glUniform1f(skipToneHandle, skipTone ? 1.0f : 0.0f);
                if (!skipTone) {
                    curvesToolValue.fillBuffer();
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                    GLES20.glBindTexture(GL10.GL_TEXTURE_2D, curveTextures[0]);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 200, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, curvesToolValue.curveBuffer);
                    GLES20.glUniform1i(curvesImageHandle, 1);
                }
            }

            GLES20.glUniform1f(widthHandle, renderBufferWidth);
            GLES20.glUniform1f(heightHandle, renderBufferHeight);
            GLES20.glEnableVertexAttribArray(inputTexCoordHandle);
            GLES20.glVertexAttribPointer(inputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        private boolean drawBlurPass() {
            if (showOriginal || blurType == 0) {
                return false;
            }
            if (needUpdateBlurTexture) {
                GLES20.glUseProgram(blurShaderProgram);
                GLES20.glUniform1i(blurSourceImageHandle, 0);
                GLES20.glEnableVertexAttribArray(blurInputTexCoordHandle);
                GLES20.glVertexAttribPointer(blurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(blurPositionHandle);
                GLES20.glVertexAttribPointer(blurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
                GLES20.glClear(0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
                GLES20.glUniform1f(blurWidthHandle, 0.0f);
                GLES20.glUniform1f(blurHeightHandle, 1.0f / renderBufferHeight);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[2]);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[2], 0);
                GLES20.glClear(0);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
                GLES20.glUniform1f(blurWidthHandle, 1.0f / renderBufferWidth);
                GLES20.glUniform1f(blurHeightHandle, 0.0f);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                needUpdateBlurTexture = false;
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
            GLES20.glClear(0);
            if (blurType == 1) {
                GLES20.glUseProgram(radialBlurShaderProgram);
                GLES20.glUniform1i(radialBlurSourceImageHandle, 0);
                GLES20.glUniform1i(radialBlurSourceImage2Handle, 1);
                GLES20.glUniform1f(radialBlurExcludeSizeHandle, blurExcludeSize);
                GLES20.glUniform1f(radialBlurExcludeBlurSizeHandle, blurExcludeBlurSize);
                GLES20.glUniform2f(radialBlurExcludePointHandle, blurExcludePoint.x, blurExcludePoint.y);
                GLES20.glUniform1f(radialBlurAspectRatioHandle, (float) renderBufferHeight / (float) renderBufferWidth);
                GLES20.glEnableVertexAttribArray(radialBlurInputTexCoordHandle);
                GLES20.glVertexAttribPointer(radialBlurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(radialBlurPositionHandle);
                GLES20.glVertexAttribPointer(radialBlurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
            } else if (blurType == 2) {
                GLES20.glUseProgram(linearBlurShaderProgram);
                GLES20.glUniform1i(linearBlurSourceImageHandle, 0);
                GLES20.glUniform1i(linearBlurSourceImage2Handle, 1);
                GLES20.glUniform1f(linearBlurExcludeSizeHandle, blurExcludeSize);
                GLES20.glUniform1f(linearBlurExcludeBlurSizeHandle, blurExcludeBlurSize);
                GLES20.glUniform1f(linearBlurAngleHandle, blurAngle);
                GLES20.glUniform2f(linearBlurExcludePointHandle, blurExcludePoint.x, blurExcludePoint.y);
                GLES20.glUniform1f(linearBlurAspectRatioHandle, (float) renderBufferHeight / (float) renderBufferWidth);
                GLES20.glEnableVertexAttribArray(linearBlurInputTexCoordHandle);
                GLES20.glVertexAttribPointer(linearBlurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(linearBlurPositionHandle);
                GLES20.glVertexAttribPointer(linearBlurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
            }

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[2]);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            return true;
        }

        private Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!initied) {
                    return;
                }

                if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                        }
                        return;
                    }
                }

                GLES20.glViewport(0, 0, renderBufferWidth, renderBufferHeight);
                drawEnhancePass();
                drawSharpenPass();
                drawCustomParamsPass();
                blured = drawBlurPass();

                //onscreen draw
                GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glClear(0);

                GLES20.glUseProgram(simpleShaderProgram);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[blured ? 0 : 1]);
                GLES20.glUniform1i(simpleSourceImageHandle, 0);
                GLES20.glEnableVertexAttribArray(simpleInputTexCoordHandle);
                GLES20.glVertexAttribPointer(simpleInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(simplePositionHandle);
                GLES20.glVertexAttribPointer(simplePositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                egl10.eglSwapBuffers(eglDisplay, eglSurface);
            }
        };

        private Bitmap getRenderBufferBitmap() {
            ByteBuffer buffer = ByteBuffer.allocateDirect(renderBufferWidth * renderBufferHeight * 4);
            GLES20.glReadPixels(0, 0, renderBufferWidth, renderBufferHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
            Bitmap bitmap = Bitmap.createBitmap(renderBufferWidth, renderBufferHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;
        }

        public Bitmap getTexture() {
            if (!initied) {
                return null;
            }
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final Bitmap object[] = new Bitmap[1];
            try {
                postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
                        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[blured ? 0 : 1], 0);
                        GLES20.glClear(0);
                        object[0] = getRenderBufferBitmap();
                        countDownLatch.countDown();
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                        GLES20.glClear(0);
                    }
                });
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            return object[0];
        }

        private Bitmap createBitmap(Bitmap bitmap, int w, int h, float scale) {
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postRotate(orientation);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        private void loadTexture(Bitmap bitmap) {
            renderBufferWidth = bitmap.getWidth();
            renderBufferHeight = bitmap.getHeight();
            float maxSize = AndroidUtilities.getPhotoSize();
            if (renderBufferWidth > maxSize || renderBufferHeight > maxSize || orientation % 360 != 0) {
                float scale = 1;
                if (renderBufferWidth > maxSize || renderBufferHeight > maxSize) {
                    float scaleX = maxSize / bitmap.getWidth();
                    float scaleY = maxSize / bitmap.getHeight();
                    if (scaleX < scaleY) {
                        renderBufferWidth = (int) maxSize;
                        renderBufferHeight = (int) (bitmap.getHeight() * scaleX);
                        scale = scaleX;
                    } else {
                        renderBufferHeight = (int) maxSize;
                        renderBufferWidth = (int) (bitmap.getWidth() * scaleY);
                        scale = scaleY;
                    }
                }

                if (orientation % 360 == 90 || orientation % 360 == 270) {
                    int temp = renderBufferWidth;
                    renderBufferWidth = renderBufferHeight;
                    renderBufferHeight = temp;
                }

                currentBitmap = createBitmap(bitmap, renderBufferWidth, renderBufferHeight, scale);
            }
            GLES20.glGenFramebuffers(3, renderFrameBuffer, 0);
            GLES20.glGenTextures(3, renderTexture, 0);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, renderTexture[1]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, currentBitmap, 0);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[2]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        }

        public void shutdown() {
            postRunnable(new Runnable() {
                @Override
                public void run() {
                    finish();
                    currentBitmap = null;
                    Looper looper = Looper.myLooper();
                    if (looper != null) {
                        looper.quit();
                    }
                }
            });
        }

        public void setSurfaceTextureSize(int width, int height) {
            surfaceWidth = width;
            surfaceHeight = height;
        }

        @Override
        public void run() {
            initied = initGL();
            super.run();
        }

        public void requestRender(final boolean updateBlur) {
            requestRender(updateBlur, false);
        }

        public void requestRender(final boolean updateBlur, final boolean force) {
            postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (!needUpdateBlurTexture) {
                        needUpdateBlurTexture = updateBlur;
                    }
                    long newTime = System.currentTimeMillis();
                    if (force || Math.abs(lastRenderCallTime - newTime) > 30) {
                        lastRenderCallTime = newTime;
                        drawRunnable.run();
                        //cancelRunnable(drawRunnable);
                        //postRunnable(drawRunnable, 30);
                    }
                }
            });
        }
    }

    public PhotoFilterView(Context context, Bitmap bitmap, int rotation, MediaController.SavedFilterState state) {
        super(context);

        if (state != null) {
            enhanceValue = state.enhanceValue;
            exposureValue = state.exposureValue;
            contrastValue = state.contrastValue;
            warmthValue = state.warmthValue;
            saturationValue = state.saturationValue;
            fadeValue = state.fadeValue;
            tintShadowsColor = state.tintShadowsColor;
            tintHighlightsColor = state.tintHighlightsColor;
            highlightsValue = state.highlightsValue;
            shadowsValue = state.shadowsValue;
            vignetteValue = state.vignetteValue;
            grainValue = state.grainValue;
            blurType = state.blurType;
            sharpenValue = state.sharpenValue;
            curvesToolValue = state.curvesToolValue;
            blurExcludeSize = state.blurExcludeSize;
            blurExcludePoint = state.blurExcludePoint;
            blurExcludeBlurSize = state.blurExcludeBlurSize;
            blurAngle = state.blurAngle;
            lastState = state;
        } else {
            curvesToolValue = new CurvesToolValue();
            blurExcludeSize = 0.35f;
            blurExcludePoint = new Point(0.5f, 0.5f);
            blurExcludeBlurSize = 0.15f;
            blurAngle = (float) Math.PI / 2.0f;
        }
        bitmapToEdit = bitmap;
        orientation = rotation;

        textureView = new TextureView(context);
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        textureView.setVisibility(INVISIBLE);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (eglThread == null && surface != null) {
                    eglThread = new EGLThread(surface, bitmapToEdit);
                    eglThread.setSurfaceTextureSize(width, height);
                    eglThread.requestRender(true, true);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
                if (eglThread != null) {
                    eglThread.setSurfaceTextureSize(width, height);
                    eglThread.requestRender(false, true);
                    eglThread.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            if (eglThread != null) {
                                eglThread.requestRender(false, true);
                            }
                        }
                    });
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (eglThread != null) {
                    eglThread.shutdown();
                    eglThread = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        blurControl = new PhotoFilterBlurControl(context);
        blurControl.setVisibility(INVISIBLE);
        addView(blurControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        blurControl.setDelegate(new PhotoFilterBlurControl.PhotoFilterLinearBlurControlDelegate() {
            @Override
            public void valueChanged(Point centerPoint, float falloff, float size, float angle) {
                blurExcludeSize = size;
                blurExcludePoint = centerPoint;
                blurExcludeBlurSize = falloff;
                blurAngle = angle;
                if (eglThread != null) {
                    eglThread.requestRender(false);
                }
            }
        });

        curvesControl = new PhotoFilterCurvesControl(context, curvesToolValue);
        curvesControl.setDelegate(new PhotoFilterCurvesControl.PhotoFilterCurvesControlDelegate() {
            @Override
            public void valueChanged() {
                if (eglThread != null) {
                    eglThread.requestRender(false);
                }
            }
        });
        curvesControl.setVisibility(INVISIBLE);
        addView(curvesControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        toolsView = new FrameLayout(context);
        addView(toolsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 186, Gravity.LEFT | Gravity.BOTTOM));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xff000000);
        toolsView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        cancelTextView = new TextView(context);
        cancelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelTextView.setTextColor(0xffffffff);
        cancelTextView.setGravity(Gravity.CENTER);
        cancelTextView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        cancelTextView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        cancelTextView.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(cancelTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        doneTextView = new TextView(context);
        doneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneTextView.setTextColor(0xff51bdf3);
        doneTextView.setGravity(Gravity.CENTER);
        doneTextView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        doneTextView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        doneTextView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());
        doneTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(doneTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        LinearLayout linearLayout = new LinearLayout(context);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

        tuneItem = new ImageView(context);
        tuneItem.setScaleType(ImageView.ScaleType.CENTER);
        tuneItem.setImageResource(R.drawable.photo_tools);
        tuneItem.setColorFilter(new PorterDuffColorFilter(0xff6cc3ff, PorterDuff.Mode.MULTIPLY));
        tuneItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        linearLayout.addView(tuneItem, LayoutHelper.createLinear(56, 48));
        tuneItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTool = 0;
                tuneItem.setColorFilter(new PorterDuffColorFilter(0xff6cc3ff, PorterDuff.Mode.MULTIPLY));
                blurItem.setColorFilter(null);
                curveItem.setColorFilter(null);
                switchMode();
            }
        });

        blurItem = new ImageView(context);
        blurItem.setScaleType(ImageView.ScaleType.CENTER);
        blurItem.setImageResource(R.drawable.tool_blur);
        blurItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        linearLayout.addView(blurItem, LayoutHelper.createLinear(56, 48));
        blurItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTool = 1;
                tuneItem.setColorFilter(null);
                blurItem.setColorFilter(new PorterDuffColorFilter(0xff6cc3ff, PorterDuff.Mode.MULTIPLY));
                curveItem.setColorFilter(null);
                switchMode();
            }
        });

        curveItem = new ImageView(context);
        curveItem.setScaleType(ImageView.ScaleType.CENTER);
        curveItem.setImageResource(R.drawable.tool_curve);
        curveItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        linearLayout.addView(curveItem, LayoutHelper.createLinear(56, 48));
        curveItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTool = 2;
                tuneItem.setColorFilter(null);
                blurItem.setColorFilter(null);
                curveItem.setColorFilter(new PorterDuffColorFilter(0xff6cc3ff, PorterDuff.Mode.MULTIPLY));
                switchMode();
            }
        });

        recyclerListView = new RecyclerListView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(new ToolsAdapter(context));
        toolsView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 120, Gravity.LEFT | Gravity.TOP));

        curveLayout = new FrameLayout(context);
        curveLayout.setVisibility(INVISIBLE);
        toolsView.addView(curveLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78, Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        LinearLayout curveTextViewContainer = new LinearLayout(context);
        curveTextViewContainer.setOrientation(LinearLayout.HORIZONTAL);
        curveLayout.addView(curveTextViewContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        for (int a = 0; a < 4; a++) {
            FrameLayout frameLayout1 = new FrameLayout(context);
            frameLayout1.setTag(a);

            curveRadioButton[a] = new RadioButton(context);
            curveRadioButton[a].setSize(AndroidUtilities.dp(20));
            frameLayout1.addView(curveRadioButton[a], LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            TextView curveTextView = new TextView(context);
            curveTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            curveTextView.setGravity(Gravity.CENTER_VERTICAL);
            if (a == 0) {
                String str = LocaleController.getString("CurvesAll", R.string.CurvesAll);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xffffffff);
                curveRadioButton[a].setColor(0xffffffff, 0xffffffff);
            } else if (a == 1) {
                String str = LocaleController.getString("CurvesRed", R.string.CurvesRed);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xffe64d4d);
                curveRadioButton[a].setColor(0xffe64d4d, 0xffe64d4d);
            } else if (a == 2) {
                String str = LocaleController.getString("CurvesGreen", R.string.CurvesGreen);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xff5abb5f);
                curveRadioButton[a].setColor(0xff5abb5f, 0xff5abb5f);
            } else if (a == 3) {
                String str = LocaleController.getString("CurvesBlue", R.string.CurvesBlue);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xff3dadee);
                curveRadioButton[a].setColor(0xff3dadee, 0xff3dadee);
            }
            frameLayout1.addView(curveTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

            curveTextViewContainer.addView(frameLayout1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, a == 0 ? 0 : 30, 0, 0, 0));
            frameLayout1.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    int num = (Integer) v.getTag();
                    curvesToolValue.activeType = num;
                    for (int a = 0; a < 4; a++) {
                        curveRadioButton[a].setChecked(a == num, true);
                    }
                    curvesControl.invalidate();
                }
            });
        }

        blurLayout = new FrameLayout(context);
        blurLayout.setVisibility(INVISIBLE);
        toolsView.addView(blurLayout, LayoutHelper.createFrame(280, 60, Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        blurOffButton = new TextView(context);
        blurOffButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurOffButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurOffButton.setGravity(Gravity.CENTER_HORIZONTAL);
        blurOffButton.setText(LocaleController.getString("BlurOff", R.string.BlurOff));
        blurLayout.addView(blurOffButton, LayoutHelper.createFrame(80, 60));
        blurOffButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                blurType = 0;
                updateSelectedBlurType();
                blurControl.setVisibility(INVISIBLE);
                if (eglThread != null) {
                    eglThread.requestRender(false);
                }
            }
        });

        blurRadialButton = new TextView(context);
        blurRadialButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurRadialButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurRadialButton.setGravity(Gravity.CENTER_HORIZONTAL);
        blurRadialButton.setText(LocaleController.getString("BlurRadial", R.string.BlurRadial));
        blurLayout.addView(blurRadialButton, LayoutHelper.createFrame(80, 80, Gravity.LEFT | Gravity.TOP, 100, 0, 0, 0));
        blurRadialButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                blurType = 1;
                updateSelectedBlurType();
                blurControl.setVisibility(VISIBLE);
                blurControl.setType(1);
                if (eglThread != null) {
                    eglThread.requestRender(false);
                }
            }
        });

        blurLinearButton = new TextView(context);
        blurLinearButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurLinearButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurLinearButton.setGravity(Gravity.CENTER_HORIZONTAL);
        blurLinearButton.setText(LocaleController.getString("BlurLinear", R.string.BlurLinear));
        blurLayout.addView(blurLinearButton, LayoutHelper.createFrame(80, 80, Gravity.LEFT | Gravity.TOP, 200, 0, 0, 0));
        blurLinearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                blurType = 2;
                updateSelectedBlurType();
                blurControl.setVisibility(VISIBLE);
                blurControl.setType(0);
                if (eglThread != null) {
                    eglThread.requestRender(false);
                }
            }
        });

        updateSelectedBlurType();

        if (Build.VERSION.SDK_INT >= 21) {
            ((LayoutParams) textureView.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight;
            ((LayoutParams) curvesControl.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight;
        }
    }

    private void updateSelectedBlurType() {
        if (blurType == 0) {
            Drawable drawable = blurOffButton.getContext().getResources().getDrawable(R.drawable.blur_off).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(0xff51bdf3, PorterDuff.Mode.MULTIPLY));
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            blurOffButton.setTextColor(0xff51bdf3);

            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_radial, 0, 0);
            blurRadialButton.setTextColor(0xffffffff);

            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_linear, 0, 0);
            blurLinearButton.setTextColor(0xffffffff);
        } else if (blurType == 1) {
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_off, 0, 0);
            blurOffButton.setTextColor(0xffffffff);

            Drawable drawable = blurOffButton.getContext().getResources().getDrawable(R.drawable.blur_radial).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(0xff51bdf3, PorterDuff.Mode.MULTIPLY));
            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            blurRadialButton.setTextColor(0xff51bdf3);

            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_linear, 0, 0);
            blurLinearButton.setTextColor(0xffffffff);
        } else if (blurType == 2) {
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_off, 0, 0);
            blurOffButton.setTextColor(0xffffffff);

            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_radial, 0, 0);
            blurRadialButton.setTextColor(0xffffffff);

            Drawable drawable = blurOffButton.getContext().getResources().getDrawable(R.drawable.blur_linear).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(0xff51bdf3, PorterDuff.Mode.MULTIPLY));
            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            blurLinearButton.setTextColor(0xff51bdf3);
        }
    }

    public MediaController.SavedFilterState getSavedFilterState() {
        MediaController.SavedFilterState state = new MediaController.SavedFilterState();
        state.enhanceValue = enhanceValue;
        state.exposureValue = exposureValue;
        state.contrastValue = contrastValue;
        state.warmthValue = warmthValue;
        state.saturationValue = saturationValue;
        state.fadeValue = fadeValue;
        state.tintShadowsColor = tintShadowsColor;
        state.tintHighlightsColor = tintHighlightsColor;
        state.highlightsValue = highlightsValue;
        state.shadowsValue = shadowsValue;
        state.vignetteValue = vignetteValue;
        state.grainValue = grainValue;
        state.blurType = blurType;
        state.sharpenValue = sharpenValue;
        state.curvesToolValue = curvesToolValue;
        state.blurExcludeSize = blurExcludeSize;
        state.blurExcludePoint = blurExcludePoint;
        state.blurExcludeBlurSize = blurExcludeBlurSize;
        state.blurAngle = blurAngle;
        return state;
    }

    public boolean hasChanges() {
        if (lastState != null) {
            return enhanceValue != lastState.enhanceValue ||
                    contrastValue != lastState.contrastValue ||
                    highlightsValue != lastState.highlightsValue ||
                    exposureValue != lastState.exposureValue ||
                    warmthValue != lastState.warmthValue ||
                    saturationValue != lastState.saturationValue ||
                    vignetteValue != lastState.vignetteValue ||
                    shadowsValue != lastState.shadowsValue ||
                    grainValue != lastState.grainValue ||
                    sharpenValue != lastState.sharpenValue ||
                    fadeValue != lastState.fadeValue ||
                    tintHighlightsColor != lastState.tintHighlightsColor ||
                    tintShadowsColor != lastState.tintShadowsColor ||
                    !curvesToolValue.shouldBeSkipped();
        } else {
            return enhanceValue != 0 || contrastValue != 0 || highlightsValue != 0 || exposureValue != 0 || warmthValue != 0 || saturationValue != 0 || vignetteValue != 0 ||
                    shadowsValue != 0 || grainValue != 0 || sharpenValue != 0 || fadeValue != 0 || tintHighlightsColor != 0 || tintShadowsColor != 0 || !curvesToolValue.shouldBeSkipped();
        }
    }

    public void onTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            LayoutParams layoutParams = (LayoutParams) textureView.getLayoutParams();
            if (layoutParams != null && event.getX() >= layoutParams.leftMargin && event.getY() >= layoutParams.topMargin && event.getX() <= layoutParams.leftMargin + layoutParams.width && event.getY() <= layoutParams.topMargin + layoutParams.height) {
                setShowOriginal(true);
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            setShowOriginal(false);
        }
    }

    private void setShowOriginal(boolean value) {
        if (showOriginal == value) {
            return;
        }
        showOriginal = value;
        if (eglThread != null) {
            eglThread.requestRender(false);
        }
    }

    public void switchMode() {
        if (selectedTool == 0) {
            blurControl.setVisibility(INVISIBLE);
            blurLayout.setVisibility(INVISIBLE);
            curveLayout.setVisibility(INVISIBLE);
            curvesControl.setVisibility(INVISIBLE);

            recyclerListView.setVisibility(VISIBLE);
        } else if (selectedTool == 1) {
            recyclerListView.setVisibility(INVISIBLE);
            curveLayout.setVisibility(INVISIBLE);
            curvesControl.setVisibility(INVISIBLE);

            blurLayout.setVisibility(VISIBLE);
            if (blurType != 0) {
                blurControl.setVisibility(VISIBLE);
            }
            updateSelectedBlurType();
        } else if (selectedTool == 2) {
            recyclerListView.setVisibility(INVISIBLE);
            blurLayout.setVisibility(INVISIBLE);
            blurControl.setVisibility(INVISIBLE);

            curveLayout.setVisibility(VISIBLE);
            curvesControl.setVisibility(VISIBLE);
            curvesToolValue.activeType = 0;
            for (int a = 0; a < 4; a++) {
                curveRadioButton[a].setChecked(a == 0, false);
            }
        }
    }

    public void shutdown() {
        if (eglThread != null) {
            eglThread.shutdown();
            eglThread = null;
        }
        textureView.setVisibility(GONE);
    }

    public void init() {
        textureView.setVisibility(VISIBLE);
    }

    public Bitmap getBitmap() {
        return eglThread != null ? eglThread.getTexture() : null;
    }

    private void fixLayout(int viewWidth, int viewHeight) {
        if (bitmapToEdit == null) {
            return;
        }

        viewWidth -= AndroidUtilities.dp(28);
        viewHeight -= AndroidUtilities.dp(14 + 140 + 60) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);

        float bitmapW;
        float bitmapH;
        if (orientation % 360 == 90 || orientation % 360 == 270) {
            bitmapW = bitmapToEdit.getHeight();
            bitmapH = bitmapToEdit.getWidth();
        } else {
            bitmapW = bitmapToEdit.getWidth();
            bitmapH = bitmapToEdit.getHeight();
        }
        float scaleX = viewWidth / bitmapW;
        float scaleY = viewHeight / bitmapH;
        if (scaleX > scaleY) {
            bitmapH = viewHeight;
            bitmapW = (int) Math.ceil(bitmapW * scaleY);
        } else {
            bitmapW = viewWidth;
            bitmapH = (int) Math.ceil(bitmapH * scaleX);
        }

        int bitmapX = (int) Math.ceil((viewWidth - bitmapW) / 2 + AndroidUtilities.dp(14));
        int bitmapY = (int) Math.ceil((viewHeight - bitmapH) / 2 + AndroidUtilities.dp(14) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0));

        LayoutParams layoutParams = (LayoutParams) textureView.getLayoutParams();
        layoutParams.leftMargin = bitmapX;
        layoutParams.topMargin = bitmapY;
        layoutParams.width = (int) bitmapW;
        layoutParams.height = (int) bitmapH;
        curvesControl.setActualArea(bitmapX, bitmapY - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0), layoutParams.width, layoutParams.height);

        blurControl.setActualAreaSize(layoutParams.width, layoutParams.height);
        layoutParams = (LayoutParams) blurControl.getLayoutParams();
        layoutParams.height = viewHeight + AndroidUtilities.dp(38);

        layoutParams = (LayoutParams) curvesControl.getLayoutParams();
        layoutParams.height = viewHeight + AndroidUtilities.dp(28);

        if (AndroidUtilities.isTablet()) {
            int total = AndroidUtilities.dp(86) * 10;
            layoutParams = (FrameLayout.LayoutParams) recyclerListView.getLayoutParams();
            if (total < viewWidth) {
                layoutParams.width = total;
                layoutParams.leftMargin = (viewWidth - total) / 2;
            } else {
                layoutParams.width = LayoutHelper.MATCH_PARENT;
                layoutParams.leftMargin = 0;
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        fixLayout(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private float getShadowsValue() {
        return (shadowsValue * 0.55f + 100.0f) / 100.0f;
    }

    private float getHighlightsValue() {
        return (highlightsValue * 0.75f + 100.0f) / 100.0f;
    }

    private float getEnhanceValue() {
        return (enhanceValue / 100.0f);
    }

    private float getExposureValue() {
        return (exposureValue / 100.0f);
    }

    private float getContrastValue() {
        return (contrastValue / 100.0f) * 0.3f + 1;
    }

    private float getWarmthValue() {
        return warmthValue / 100.0f;
    }

    private float getVignetteValue() {
        return vignetteValue / 100.0f;
    }

    private float getSharpenValue() {
        return 0.11f + sharpenValue / 100.0f * 0.6f;
    }

    private float getGrainValue() {
        return grainValue / 100.0f * 0.04f;
    }

    private float getFadeValue() {
        return fadeValue / 100.0f;
    }

    private float getTintHighlightsIntensityValue() {
        float tintHighlightsIntensity = 50.0f;
        return tintHighlightsColor == 0 ? 0 : tintHighlightsIntensity / 100.0f;
    }

    private float getTintShadowsIntensityValue() {
        float tintShadowsIntensity = 50.0f;
        return tintShadowsColor == 0 ? 0 : tintShadowsIntensity / 100.0f;
    }

    private float getSaturationValue() {
        float parameterValue = (saturationValue / 100.0f);
        if (parameterValue > 0) {
            parameterValue *= 1.05f;
        }
        return parameterValue + 1;
    }

    public FrameLayout getToolsView() {
        return toolsView;
    }

    public TextView getDoneTextView() {
        return doneTextView;
    }

    public TextView getCancelTextView() {
        return cancelTextView;
    }

    public class ToolsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ToolsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return 13;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View view;
            if (i == 0) {
                PhotoEditToolCell cell = new PhotoEditToolCell(mContext);
                view = cell;
                cell.setSeekBarDelegate(new PhotoEditorSeekBar.PhotoEditorSeekBarDelegate() {
                    @Override
                    public void onProgressChanged(int i, int progress) {
                        if (i == enhanceTool) {
                            enhanceValue = progress;
                        } else if (i == highlightsTool) {
                            highlightsValue = progress;
                        } else if (i == contrastTool) {
                            contrastValue = progress;
                        } else if (i == exposureTool) {
                            exposureValue = progress;
                        } else if (i == warmthTool) {
                            warmthValue = progress;
                        } else if (i == saturationTool) {
                            saturationValue = progress;
                        } else if (i == vignetteTool) {
                            vignetteValue = progress;
                        } else if (i == shadowsTool) {
                            shadowsValue = progress;
                        } else if (i == grainTool) {
                            grainValue = progress;
                        } else if (i == sharpenTool) {
                            sharpenValue = progress;
                        }  else if (i == fadeTool) {
                            fadeValue = progress;
                        }
                        if (eglThread != null) {
                            eglThread.requestRender(true);
                        }
                    }
                });
            } else {
                view = new PhotoEditRadioCell(mContext);
                view.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PhotoEditRadioCell cell = (PhotoEditRadioCell) v;
                        Integer row = (Integer) cell.getTag();
                        if (row == tintShadowsTool) {
                            tintShadowsColor = cell.getCurrentColor();
                        } else {
                            tintHighlightsColor = cell.getCurrentColor();
                        }
                        if (eglThread != null) {
                            eglThread.requestRender(false);
                        }
                    }
                });
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
            switch (holder.getItemViewType()) {
                case 0: {
                    PhotoEditToolCell cell = (PhotoEditToolCell) holder.itemView;
                    cell.setTag(i);
                    if (i == enhanceTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Enhance", R.string.Enhance), enhanceValue, 0, 100);
                    } else if (i == highlightsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Highlights", R.string.Highlights), highlightsValue, -100, 100);
                    } else if (i == contrastTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Contrast", R.string.Contrast), contrastValue, -100, 100);
                    } else if (i == exposureTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Exposure", R.string.Exposure), exposureValue, -100, 100);
                    } else if (i == warmthTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Warmth", R.string.Warmth), warmthValue, -100, 100);
                    } else if (i == saturationTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Saturation", R.string.Saturation), saturationValue, -100, 100);
                    } else if (i == vignetteTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Vignette", R.string.Vignette), vignetteValue, 0, 100);
                    } else if (i == shadowsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Shadows", R.string.Shadows), shadowsValue, -100, 100);
                    } else if (i == grainTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Grain", R.string.Grain), grainValue, 0, 100);
                    } else if (i == sharpenTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Sharpen", R.string.Sharpen), sharpenValue, 0, 100);
                    } else if (i == fadeTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("Fade", R.string.Fade), fadeValue, 0, 100);
                    }
                    break;
                }
                case 1: {
                    PhotoEditRadioCell cell = (PhotoEditRadioCell) holder.itemView;
                    cell.setTag(i);
                    if (i == tintShadowsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("TintShadows", R.string.TintShadows), 0, tintShadowsColor);
                    } else if (i == tintHighlightsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString("TintHighlights", R.string.TintHighlights), 0, tintHighlightsColor);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == tintShadowsTool || position == tintHighlightsTool) {
                return 1;
            }
            return 0;
        }
    }
}
