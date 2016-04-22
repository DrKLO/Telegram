/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PhotoEditToolCell;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

@SuppressLint("NewApi")
public class PhotoFilterView extends FrameLayout {

    private final int[] tintShadowColors = new int[] {
            0x00000000,
            0xffff4d4d,
            0xfff48022,
            0xffffcd00,
            0xff81d281,
            0xff71c5d6,
            0xff0072bc,
            0xff662d91
    };

    private final int[] tintHighlighsColors = new int[] {
            0x00000000,
            0xffef9286,
            0xffeacea2,
            0xfff2e17c,
            0xffa4edae,
            0xff89dce5,
            0xff2e8bc8,
            0xffcd98e5
    };

    private boolean showOriginal;

    private float previousValue;
    private int previousValueInt;
    private int previousValueInt2;

    private int selectedTintMode;

    private int selectedTool = -1;
    private int enhanceTool = 0;
    private int exposureTool = 1;
    private int contrastTool = 2;
    private int warmthTool = 3;
    private int saturationTool = 4;
    private int tintTool = 5;
    private int fadeTool = 6;
    private int highlightsTool = 7;
    private int shadowsTool = 8;
    private int vignetteTool = 9;
    private int grainTool = 10;
    private int blurTool = 11;
    private int sharpenTool = 12;
    private int curvesTool = 13;

    private float enhanceValue = 0; //0 100
    private float exposureValue = 0; //-100 100
    private float contrastValue = 0; //-100 100
    private float warmthValue = 0; //-100 100
    private float saturationValue = 0; //-100 100
    private float fadeValue = 0; // 0 100
    private int tintShadowsColor = 0; //0 0xffffffff
    private int tintHighlightsColor = 0; //0 0xffffffff
    private float highlightsValue = 0; //-100 100
    private float shadowsValue = 0; //-100 100
    private float vignetteValue = 0; //0 100
    private float grainValue = 0; //0 100
    private int blurType = 0; //0 none, 1 radial, 2 linear
    private float sharpenValue = 0; //0 100
    private CurvesToolValue curvesToolValue = new CurvesToolValue();

    private final static int curveGranularity = 100;
    private final static int curveDataStep = 2;

    private float blurExcludeSize = 0.35f;
    private Point blurExcludePoint = new Point(0.5f, 0.5f);
    private float blurExcludeBlurSize = 0.15f;
    private float blurAngle = (float) Math.PI / 2.0f;

    private ToolsAdapter toolsAdapter;
    private PhotoEditorSeekBar valueSeekBar;
    private FrameLayout toolsView;
    private FrameLayout editView;
    private TextView paramTextView;
    private TextView infoTextView;
    private TextView valueTextView;
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
    private FrameLayout tintLayout;
    private TextView tintShadowsButton;
    private TextView tintHighlightsButton;
    private LinearLayout tintButtonsContainer;
    private FrameLayout curveLayout;
    private TextView[] curveTextView = new TextView[4];

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

        /*
        private static final String toolsFragmentShaderCode =
                "varying highp vec2 texCoord;" +
                "uniform sampler2D sourceImage;" +
                "uniform highp float width;" +
                "uniform highp float height;" +
                "uniform lowp float rgbCurveValues[200];" +
                "uniform lowp float redCurveValues[200];" +
                "uniform lowp float greenCurveValues[200];" +
                "uniform lowp float blueCurveValues[200];" +
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
                    "highp vec3 rgb;" +
                    "if (hsl.y == 0.0) {" +
                        "rgb = vec3(hsl.z);" +
                    "} else {" +
                        "highp float f2;" +
                        "if (hsl.z < 0.5) {" +
                            "f2 = hsl.z * (1.0 + hsl.y);" +
                        "} else {" +
                            "f2 = (hsl.z + hsl.y) - (hsl.y * hsl.z);" +
                        "}" +
                        "highp float f1 = 2.0 * hsl.z - f2;" +
                        "rgb.r = hueToRgb(f1, f2, hsl.x + (1.0/3.0));" +
                        "rgb.g = hueToRgb(f1, f2, hsl.x);" +
                        "rgb.b = hueToRgb(f1, f2, hsl.x - (1.0/3.0));" +
                    "}" +
                    "return rgb;" +
                "}" +
                "highp vec3 rgbToYuv(highp vec3 inP) {" +
                    "highp vec3 outP;" +
                    "outP.r = getLuma(inP);" +
                    "outP.g = (1.0 / 1.772) * (inP.b - outP.r);" +
                    "outP.b = (1.0 / 1.402) * (inP.r - outP.r);" +
                    "return outP;" +
                "}" +
                "lowp vec3 yuvToRgb(highp vec3 inP) {" +
                    "highp float y = inP.r;" +
                    "highp float u = inP.g;" +
                    "highp float v = inP.b;" +
                    "lowp vec3 outP;" +
                    "outP.r = 1.402 * v + y;" +
                    "outP.g = (y - (0.299 * 1.402 / 0.587) * v - (0.114 * 1.772 / 0.587) * u);" +
                    "outP.b = 1.772 * u + y;" +
                    "return outP;" +
                "}" +
                "lowp float easeInOutSigmoid(lowp float value, lowp float strength) {" +
                    "lowp float t = 1.0 / (1.0 - strength);" +
                    "if (value > 0.5) {" +
                        "return 1.0 - pow(2.0 - 2.0 * value, t) * 0.5;" +
                    "} else {" +
                        "return pow(2.0 * value, t) * 0.5;" +
                    "}" +
                "}" +
                "lowp vec3 applyLuminanceCurve(lowp vec3 pixel) {" +
                    "int index = int(clamp(pixel.z / (1.0 / 200.0), 0.0, 199.0));" +
                    "highp float value = rgbCurveValues[index];" +
                    "highp float grayscale = (smoothstep(0.0, 0.1, pixel.z) * (1.0 - smoothstep(0.8, 1.0, pixel.z)));" +
                    "highp float saturation = mix(0.0, pixel.y, grayscale);" +
                    "pixel.y = saturation;" +
                    "pixel.z = value;" +
                    "return pixel;" +
                "}" +
                "lowp vec3 applyRGBCurve(lowp vec3 pixel) {" +
                    "int index = int(clamp(pixel.r / (1.0 / 200.0), 0.0, 199.0));" +
                    "highp float value = redCurveValues[index];" +
                    "pixel.r = value;" +
                    "index = int(clamp(pixel.g / (1.0 / 200.0), 0.0, 199.0));" +
                    "value = greenCurveValues[index];" +
                    "pixel.g = clamp(value, 0.0, 1.0);" +
                    "index = int(clamp(pixel.b / (1.0 / 200.0), 0.0, 199.0));" +
                    "value = blueCurveValues[index];" +
                    "pixel.b = clamp(value, 0.0, 1.0);" +
                    "return pixel;" +
                "}" +
                "highp vec3 fadeAdjust(highp vec3 color, highp float fadeVal) {" +
                    "highp vec3 co1 = vec3(-0.9772);" +
                    "highp vec3 co2 = vec3(1.708);" +
                    "highp vec3 co3 = vec3(-0.1603);" +
                    "highp vec3 co4 = vec3(0.2878);" +
                    "highp vec3 comp1 = co1 * pow(vec3(color), vec3(3.0));" +
                    "highp vec3 comp2 = co2 * pow(vec3(color), vec3(2.0));" +
                    "highp vec3 comp3 = co3 * vec3(color);" +
                    "highp vec3 comp4 = co4;" +
                    "highp vec3 finalComponent = comp1 + comp2 + comp3 + comp4;" +
                    "highp vec3 difference = finalComponent - color;" +
                    "highp vec3 scalingValue = vec3(0.9);" +
                    "highp vec3 faded = color + (difference * scalingValue);" +
                    "return (color * (1.0 - fadeVal)) + (faded * fadeVal);" +
                "}" +
                "lowp vec3 tintRaiseShadowsCurve(lowp vec3 color) {" +
                    "highp vec3 co1 = vec3(-0.003671);" +
                    "highp vec3 co2 = vec3(0.3842);" +
                    "highp vec3 co3 = vec3(0.3764);" +
                    "highp vec3 co4 = vec3(0.2515);" +
                    "highp vec3 comp1 = co1 * pow(color, vec3(3.0));" +
                    "highp vec3 comp2 = co2 * pow(color, vec3(2.0));" +
                    "highp vec3 comp3 = co3 * color;" +
                    "highp vec3 comp4 = co4;" +
                    "return comp1 + comp2 + comp3 + comp4;" +
                "}" +
                "lowp vec3 tintShadows(lowp vec3 texel, lowp vec3 tintColor, lowp float tintAmount) {" +
                    "highp vec3 raisedShadows = tintRaiseShadowsCurve(texel);" +
                    "highp vec3 tintedShadows = mix(texel, raisedShadows, tintColor);" +
                    "highp vec3 tintedShadowsWithAmount = mix(texel, tintedShadows, tintAmount);" +
                    "return clamp(tintedShadowsWithAmount, 0.0, 1.0);" +
                "} " +
                "lowp vec3 tintHighlights(lowp vec3 texel, lowp vec3 tintColor, lowp float tintAmount) {" +
                    "lowp vec3 loweredHighlights = vec3(1.0) - tintRaiseShadowsCurve(vec3(1.0) - texel);" +
                    "lowp vec3 tintedHighlights = mix(texel, loweredHighlights, (vec3(1.0) - tintColor));" +
                    "lowp vec3 tintedHighlightsWithAmount = mix(texel, tintedHighlights, tintAmount);" +
                    "return clamp(tintedHighlightsWithAmount, 0.0, 1.0);" +
                "}" +
                "highp vec4 rnm(in highp vec2 tc) {" +
                    "highp float noise = sin(dot(tc,vec2(12.9898,78.233))) * 43758.5453;" +
                    "highp float noiseR = fract(noise)*2.0-1.0;" +
                    "highp float noiseG = fract(noise*1.2154)*2.0-1.0;" +
                    "highp float noiseB = fract(noise*1.3453)*2.0-1.0;" +
                    "highp float noiseA = fract(noise*1.3647)*2.0-1.0;" +
                    "return vec4(noiseR,noiseG,noiseB,noiseA);" +
                "}" +
                "highp float fade(in highp float t) {" +
                    "return t*t*t*(t*(t*6.0-15.0)+10.0);" +
                "}" +
                "highp float pnoise3D(in highp vec3 p) {" +
                    "highp vec3 pi = permTexUnit*floor(p)+permTexUnitHalf;" +
                    "highp vec3 pf = fract(p);" +
                    "highp float perm00 = rnm(pi.xy).a;" +
                    "highp vec3 grad000 = rnm(vec2(perm00, pi.z)).rgb * 4.0 - 1.0;" +
                    "highp float n000 = dot(grad000, pf);" +
                    "highp vec3 grad001 = rnm(vec2(perm00, pi.z + permTexUnit)).rgb * 4.0 - 1.0;" +
                    "highp float n001 = dot(grad001, pf - vec3(0.0, 0.0, 1.0));" +
                    "highp float perm01 = rnm(pi.xy + vec2(0.0, permTexUnit)).a;" +
                    "highp vec3 grad010 = rnm(vec2(perm01, pi.z)).rgb * 4.0 - 1.0;" +
                    "highp float n010 = dot(grad010, pf - vec3(0.0, 1.0, 0.0));" +
                    "highp vec3 grad011 = rnm(vec2(perm01, pi.z + permTexUnit)).rgb * 4.0 - 1.0;" +
                    "highp float n011 = dot(grad011, pf - vec3(0.0, 1.0, 1.0));" +
                    "highp float perm10 = rnm(pi.xy + vec2(permTexUnit, 0.0)).a;" +
                    "highp vec3 grad100 = rnm(vec2(perm10, pi.z)).rgb * 4.0 - 1.0;" +
                    "highp float n100 = dot(grad100, pf - vec3(1.0, 0.0, 0.0));" +
                    "highp vec3 grad101 = rnm(vec2(perm10, pi.z + permTexUnit)).rgb * 4.0 - 1.0;" +
                    "highp float n101 = dot(grad101, pf - vec3(1.0, 0.0, 1.0));" +
                    "highp float perm11 = rnm(pi.xy + vec2(permTexUnit, permTexUnit)).a;" +
                    "highp vec3 grad110 = rnm(vec2(perm11, pi.z)).rgb * 4.0 - 1.0;" +
                    "highp float n110 = dot(grad110, pf - vec3(1.0, 1.0, 0.0));" +
                    "highp vec3 grad111 = rnm(vec2(perm11, pi.z + permTexUnit)).rgb * 4.0 - 1.0;" +
                    "highp float n111 = dot(grad111, pf - vec3(1.0, 1.0, 1.0));" +
                    "highp vec4 n_x = mix(vec4(n000, n001, n010, n011), vec4(n100, n101, n110, n111), fade(pf.x));" +
                    "highp vec2 n_xy = mix(n_x.xy, n_x.zw, fade(pf.y));" +
                    "highp float n_xyz = mix(n_xy.x, n_xy.y, fade(pf.z));" +
                    "return n_xyz;" +
                "}" +
                "lowp vec2 coordRot(in lowp vec2 tc, in lowp float angle) {" +
                    "lowp float rotX = ((tc.x * 2.0 - 1.0) * cos(angle)) - ((tc.y * 2.0 - 1.0) * sin(angle));" +
                    "lowp float rotY = ((tc.y * 2.0 - 1.0) * cos(angle)) + ((tc.x * 2.0 - 1.0) * sin(angle));" +
                    "rotX = rotX * 0.5 + 0.5;" +
                    "rotY = rotY * 0.5 + 0.5;" +
                    "return vec2(rotX,rotY);" +
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
         */

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
                FileLog.e("tmessages", GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private boolean initGL() {
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                FileLog.e("tmessages", "eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                finish();
                return false;
            }

            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                FileLog.e("tmessages", "eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
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
                FileLog.e("tmessages", "eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                finish();
                return false;
            } else if (configsCount[0] > 0) {
                eglConfig = configs[0];
            } else {
                FileLog.e("tmessages", "eglConfig not initialized");
                finish();
                return false;
            }

            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            if (eglContext == null) {
                FileLog.e("tmessages", "eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
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
                FileLog.e("tmessages", "createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                finish();
                return false;
            }
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                FileLog.e("tmessages", "eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
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
                    FileLog.e("tmessages", "link error = " + infoLog);*/
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
                    FileLog.e("tmessages", e);
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
                        FileLog.e("tmessages", "eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
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
            final Semaphore semaphore = new Semaphore(0);
            final Bitmap object[] = new Bitmap[1];
            try {
                postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
                        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[blured ? 0 : 1], 0);
                        GLES20.glClear(0);
                        object[0] = getRenderBufferBitmap();
                        semaphore.release();
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                        GLES20.glClear(0);
                    }
                });
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
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
            postRunnable(new Runnable() {
                @Override
                public void run() {
                    if (!needUpdateBlurTexture) {
                        needUpdateBlurTexture = updateBlur;
                    }
                    long newTime = System.currentTimeMillis();
                    if (Math.abs(lastRenderCallTime - newTime) > 30) {
                        lastRenderCallTime = newTime;
                        drawRunnable.run();
                        //cancelRunnable(drawRunnable);
                        //postRunnable(drawRunnable, 30);
                    }
                }
            });
        }
    }

    public PhotoFilterView(Context context, Bitmap bitmap, int rotation) {
        super(context);

        bitmapToEdit = bitmap;
        orientation = rotation;

        textureView = new TextureView(context);
        if (Build.VERSION.SDK_INT == 14 || Build.VERSION.SDK_INT == 15) {
            //setLayerType(LAYER_TYPE_HARDWARE, null);
            //textureView.setLayerType(LAYER_TYPE_HARDWARE, null);
        }
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        textureView.setVisibility(INVISIBLE);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (eglThread == null && surface != null) {
                    eglThread = new EGLThread(surface, bitmapToEdit);
                    eglThread.setSurfaceTextureSize(width, height);
                    eglThread.requestRender(true);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
                if (eglThread != null) {
                    eglThread.setSurfaceTextureSize(width, height);
                    eglThread.requestRender(false);
                    eglThread.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            if (eglThread != null) {
                                eglThread.requestRender(false);
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
        addView(toolsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 126, Gravity.LEFT | Gravity.BOTTOM));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xff1a1a1a);
        toolsView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        cancelTextView = new TextView(context);
        cancelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelTextView.setTextColor(0xffffffff);
        cancelTextView.setGravity(Gravity.CENTER);
        cancelTextView.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, false));
        cancelTextView.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        cancelTextView.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(cancelTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        doneTextView = new TextView(context);
        doneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneTextView.setTextColor(0xff51bdf3);
        doneTextView.setGravity(Gravity.CENTER);
        doneTextView.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, false));
        doneTextView.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        doneTextView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());
        doneTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        frameLayout.addView(doneTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        recyclerListView = new RecyclerListView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 9) {
            recyclerListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        }
        recyclerListView.setAdapter(toolsAdapter = new ToolsAdapter(context));
        toolsView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.LEFT | Gravity.TOP));
        recyclerListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                selectedTool = position;
                if (position == enhanceTool) {
                    previousValue = enhanceValue;
                    valueSeekBar.setMinMax(0, 100);
                    paramTextView.setText(LocaleController.getString("Enhance", R.string.Enhance));
                } else if (position == highlightsTool) {
                    previousValue = highlightsValue;
                    valueSeekBar.setMinMax(-100, 100);
                    paramTextView.setText(LocaleController.getString("Highlights", R.string.Highlights));
                } else if (position == contrastTool) {
                    previousValue = contrastValue;
                    valueSeekBar.setMinMax(-100, 100);
                    paramTextView.setText(LocaleController.getString("Contrast", R.string.Contrast));
                } else if (position == exposureTool) {
                    previousValue = exposureValue;
                    valueSeekBar.setMinMax(-100, 100);
                    paramTextView.setText(LocaleController.getString("Exposure", R.string.Exposure));
                } else if (position == warmthTool) {
                    previousValue = warmthValue;
                    valueSeekBar.setMinMax(-100, 100);
                    paramTextView.setText(LocaleController.getString("Warmth", R.string.Warmth));
                } else if (position == saturationTool) {
                    previousValue = saturationValue;
                    valueSeekBar.setMinMax(-100, 100);
                    paramTextView.setText(LocaleController.getString("Saturation", R.string.Saturation));
                } else if (position == vignetteTool) {
                    previousValue = vignetteValue;
                    valueSeekBar.setMinMax(0, 100);
                    paramTextView.setText(LocaleController.getString("Vignette", R.string.Vignette));
                } else if (position == shadowsTool) {
                    previousValue = shadowsValue;
                    valueSeekBar.setMinMax(-100, 100);
                    paramTextView.setText(LocaleController.getString("Shadows", R.string.Shadows));
                } else if (position == grainTool) {
                    previousValue = grainValue;
                    valueSeekBar.setMinMax(0, 100);
                    paramTextView.setText(LocaleController.getString("Grain", R.string.Grain));
                } else if (position == fadeTool) {
                    previousValue = fadeValue;
                    valueSeekBar.setMinMax(0, 100);
                    paramTextView.setText(LocaleController.getString("Fade", R.string.Fade));
                } else if (position == sharpenTool) {
                    previousValue = sharpenValue;
                    valueSeekBar.setMinMax(0, 100);
                    paramTextView.setText(LocaleController.getString("Sharpen", R.string.Sharpen));
                } else if (position == blurTool) {
                    previousValueInt = blurType;
                } else if (position == tintTool) {
                    previousValueInt = tintShadowsColor;
                    previousValueInt2 = tintHighlightsColor;
                } else if (position == curvesTool) {
                    curvesToolValue.luminanceCurve.saveValues();
                    curvesToolValue.redCurve.saveValues();
                    curvesToolValue.greenCurve.saveValues();
                    curvesToolValue.blueCurve.saveValues();
                }
                valueSeekBar.setProgress((int) previousValue, false);
                updateValueTextView();
                switchToOrFromEditMode();
            }
        });

        editView = new FrameLayout(context);
        editView.setVisibility(GONE);
        addView(editView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 126, Gravity.LEFT | Gravity.BOTTOM));

        frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xff1a1a1a);
        editView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.edit_cancel);
        imageView.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, false));
        imageView.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), 0);
        frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        imageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedTool == enhanceTool) {
                    enhanceValue = previousValue;
                } else if (selectedTool == highlightsTool) {
                    highlightsValue = previousValue;
                } else if (selectedTool == contrastTool) {
                    contrastValue = previousValue;
                } else if (selectedTool == exposureTool) {
                    exposureValue = previousValue;
                } else if (selectedTool == warmthTool) {
                    warmthValue = previousValue;
                } else if (selectedTool == saturationTool) {
                    saturationValue = previousValue;
                } else if (selectedTool == vignetteTool) {
                    vignetteValue = previousValue;
                } else if (selectedTool == shadowsTool) {
                    shadowsValue = previousValue;
                } else if (selectedTool == grainTool) {
                    grainValue = previousValue;
                } else if (selectedTool == sharpenTool) {
                    sharpenValue = previousValue;
                } else if (selectedTool == fadeTool) {
                    fadeValue = previousValue;
                } else if (selectedTool == blurTool) {
                    blurType = previousValueInt;
                } else if (selectedTool == tintTool) {
                    tintShadowsColor = previousValueInt;
                    tintHighlightsColor = previousValueInt2;
                } else if (selectedTool == curvesTool) {
                    curvesToolValue.luminanceCurve.restoreValues();
                    curvesToolValue.redCurve.restoreValues();
                    curvesToolValue.greenCurve.restoreValues();
                    curvesToolValue.blueCurve.restoreValues();
                }
                if (eglThread != null) {
                    eglThread.requestRender(selectedTool != blurTool);
                }
                switchToOrFromEditMode();
            }
        });

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.edit_doneblue);
        imageView.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, false));
        imageView.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(1), AndroidUtilities.dp(22), 0);
        frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
        imageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toolsAdapter.notifyDataSetChanged();
                switchToOrFromEditMode();
            }
        });

        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        infoTextView.setTextColor(0xffffffff);
        frameLayout.addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 0));

        paramTextView = new TextView(context);
        paramTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        paramTextView.setTextColor(0xff808080);
        frameLayout.addView(paramTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 26, 0, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        valueTextView.setTextColor(0xffffffff);
        frameLayout.addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 3, 0, 0));

        valueSeekBar = new PhotoEditorSeekBar(context);
        valueSeekBar.setDelegate(new PhotoEditorSeekBar.PhotoEditorSeekBarDelegate() {
            @Override
            public void onProgressChanged() {
                int progress = valueSeekBar.getProgress();
                if (selectedTool == enhanceTool) {
                    enhanceValue = progress;
                } else if (selectedTool == highlightsTool) {
                    highlightsValue = progress;
                } else if (selectedTool == contrastTool) {
                    contrastValue = progress;
                } else if (selectedTool == exposureTool) {
                    exposureValue = progress;
                } else if (selectedTool == warmthTool) {
                    warmthValue = progress;
                } else if (selectedTool == saturationTool) {
                    saturationValue = progress;
                } else if (selectedTool == vignetteTool) {
                    vignetteValue = progress;
                } else if (selectedTool == shadowsTool) {
                    shadowsValue = progress;
                } else if (selectedTool == grainTool) {
                    grainValue = progress;
                } else if (selectedTool == sharpenTool) {
                    sharpenValue = progress;
                }  else if (selectedTool == fadeTool) {
                    fadeValue = progress;
                }
                updateValueTextView();
                if (eglThread != null) {
                    eglThread.requestRender(true);
                }
            }
        });
        editView.addView(valueSeekBar, LayoutHelper.createFrame(AndroidUtilities.isTablet() ? 498 : LayoutHelper.MATCH_PARENT, 60, AndroidUtilities.isTablet() ? Gravity.CENTER_HORIZONTAL | Gravity.TOP : Gravity.LEFT | Gravity.TOP, 14, 10, 14, 0));

        curveLayout = new FrameLayout(context);
        editView.addView(curveLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78, Gravity.CENTER_HORIZONTAL));

        LinearLayout curveTextViewContainer = new LinearLayout(context);
        curveTextViewContainer.setOrientation(LinearLayout.HORIZONTAL);
        curveLayout.addView(curveTextViewContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL));

        for (int a = 0; a < 4; a++) {
            curveTextView[a] = new TextView(context);
            curveTextView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            curveTextView[a].setGravity(Gravity.CENTER_VERTICAL);
            curveTextView[a].setTag(a);
            if (a == 0) {
                curveTextView[a].setText(LocaleController.getString("CurvesAll", R.string.CurvesAll).toUpperCase());
            } else if (a == 1) {
                curveTextView[a].setText(LocaleController.getString("CurvesRed", R.string.CurvesRed).toUpperCase());
            } else if (a == 2) {
                curveTextView[a].setText(LocaleController.getString("CurvesGreen", R.string.CurvesGreen).toUpperCase());
            } else if (a == 3) {
                curveTextView[a].setText(LocaleController.getString("CurvesBlue", R.string.CurvesBlue).toUpperCase());
            }
            curveTextView[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            curveTextViewContainer.addView(curveTextView[a], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, a == 0 ? 0 : 30, 0, 0, 0));
            curveTextView[a].setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    int num = (Integer) v.getTag();
                    for (int a = 0; a < 4; a++) {
                        curveTextView[a].setTextColor(a == num ? 0xffffffff : 0xff808080);
                    }
                    curvesToolValue.activeType = num;
                    curvesControl.invalidate();
                }
            });
        }

        tintLayout = new FrameLayout(context);
        editView.addView(tintLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78, Gravity.CENTER_HORIZONTAL));

        LinearLayout tintTextViewContainer = new LinearLayout(context);
        tintTextViewContainer.setOrientation(LinearLayout.HORIZONTAL);
        tintLayout.addView(tintTextViewContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.CENTER_HORIZONTAL));

        tintShadowsButton = new TextView(context);
        tintShadowsButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tintShadowsButton.setGravity(Gravity.CENTER_VERTICAL);
        tintShadowsButton.setText(LocaleController.getString("TintShadows", R.string.TintShadows).toUpperCase());
        tintShadowsButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tintTextViewContainer.addView(tintShadowsButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28));
        tintShadowsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTintMode = 0;
                updateSelectedTintButton(true);
            }
        });

        tintHighlightsButton = new TextView(context);
        tintHighlightsButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tintHighlightsButton.setGravity(Gravity.CENTER_VERTICAL);
        tintHighlightsButton.setText(LocaleController.getString("TintHighlights", R.string.TintHighlights).toUpperCase());
        tintHighlightsButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tintTextViewContainer.addView(tintHighlightsButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 28, 100, 0, 0, 0));
        tintHighlightsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTintMode = 1;
                updateSelectedTintButton(true);
            }
        });

        tintButtonsContainer = new LinearLayout(context);
        tintButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tintButtonsContainer.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        tintLayout.addView(tintButtonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 24, 0, 0));
        for (int a = 0; a < tintShadowColors.length; a++) {
            RadioButton radioButton = new RadioButton(context);
            radioButton.setSize(AndroidUtilities.dp(20));
            radioButton.setTag(a);
            tintButtonsContainer.addView(radioButton, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f / tintShadowColors.length));
            radioButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    RadioButton radioButton = (RadioButton) v;
                    if (selectedTintMode == 0) {
                        tintShadowsColor = tintShadowColors[(Integer) radioButton.getTag()];
                    } else {
                        tintHighlightsColor = tintHighlighsColors[(Integer) radioButton.getTag()];
                    }
                    updateSelectedTintButton(true);
                    if (eglThread != null) {
                        eglThread.requestRender(false);
                    }
                }
            });
        }

        blurLayout = new FrameLayout(context);
        editView.addView(blurLayout, LayoutHelper.createFrame(280, 60, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

        blurOffButton = new TextView(context);
        blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_off_active, 0, 0);
        blurOffButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurOffButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurOffButton.setTextColor(0xff51bdf3);
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
        blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_radial, 0, 0);
        blurRadialButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurRadialButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurRadialButton.setTextColor(0xffffffff);
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
        blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_linear, 0, 0);
        blurLinearButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurLinearButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurLinearButton.setTextColor(0xffffffff);
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
    }

    private void updateSelectedBlurType() {
        if (blurType == 0) {
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_off_active, 0, 0);
            blurOffButton.setTextColor(0xff51bdf3);
            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_radial, 0, 0);
            blurRadialButton.setTextColor(0xffffffff);
            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_linear, 0, 0);
            blurLinearButton.setTextColor(0xffffffff);
        } else if (blurType == 1) {
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_off, 0, 0);
            blurOffButton.setTextColor(0xffffffff);
            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_radial_active, 0, 0);
            blurRadialButton.setTextColor(0xff51bdf3);
            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_linear, 0, 0);
            blurLinearButton.setTextColor(0xffffffff);
        } else if (blurType == 2) {
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_off, 0, 0);
            blurOffButton.setTextColor(0xffffffff);
            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_radial, 0, 0);
            blurRadialButton.setTextColor(0xffffffff);
            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.blur_linear_active, 0, 0);
            blurLinearButton.setTextColor(0xff51bdf3);
        }
    }

    private void updateSelectedTintButton(boolean animated) {
        tintHighlightsButton.setTextColor(selectedTintMode == 1 ? 0xffffffff : 0xff808080);
        tintShadowsButton.setTextColor(selectedTintMode == 1 ? 0xff808080 : 0xffffffff);
        int childCount = tintButtonsContainer.getChildCount();
        for (int a = 0; a < childCount; a++) {
            View child = tintButtonsContainer.getChildAt(a);
            if (child instanceof RadioButton) {
                RadioButton radioButton = (RadioButton) child;
                int num = (Integer) radioButton.getTag();
                int color1 = selectedTintMode == 0 ? tintShadowsColor : tintHighlightsColor;
                int color2 = selectedTintMode == 0 ? tintShadowColors[num] : tintHighlighsColors[num];
                radioButton.setChecked(color1 == color2, animated);
                radioButton.setColor(num == 0 ? 0xffffffff : (selectedTintMode == 0 ? tintShadowColors[num] : tintHighlighsColors[num]), num == 0 ? 0xffffffff : (selectedTintMode == 0 ? tintShadowColors[num] : tintHighlighsColors[num]));
            }
        }
    }

    private void updateValueTextView() {
        int value = 0;
        if (selectedTool == enhanceTool) {
            value = (int) enhanceValue;
        } else if (selectedTool == highlightsTool) {
            value = (int) highlightsValue;
        } else if (selectedTool == contrastTool) {
            value = (int) contrastValue;
        } else if (selectedTool == exposureTool) {
            value = (int) exposureValue;
        } else if (selectedTool == warmthTool) {
            value = (int) warmthValue;
        } else if (selectedTool == saturationTool) {
            value = (int) saturationValue;
        } else if (selectedTool == vignetteTool) {
            value = (int) vignetteValue;
        } else if (selectedTool == shadowsTool) {
            value = (int) shadowsValue;
        } else if (selectedTool == grainTool) {
            value = (int) grainValue;
        } else if (selectedTool == sharpenTool) {
            value = (int) sharpenValue;
        }  else if (selectedTool == fadeTool) {
            value = (int) fadeValue;
        }
        if (value > 0) {
            valueTextView.setText("+" + value);
        } else {
            valueTextView.setText("" + value);
        }
    }

    public boolean hasChanges() {
        return enhanceValue != 0 || contrastValue != 0 || highlightsValue != 0 || exposureValue != 0 || warmthValue != 0 || saturationValue != 0 || vignetteValue != 0 ||
                shadowsValue != 0 || grainValue != 0 || sharpenValue != 0 || fadeValue != 0 || tintHighlightsColor != 0 || tintShadowsColor != 0 || !curvesToolValue.shouldBeSkipped();
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

    public void switchToOrFromEditMode() {
        final View viewFrom;
        final View viewTo;
        if (editView.getVisibility() == GONE) {
            viewFrom = toolsView;
            viewTo = editView;

            if (selectedTool == blurTool || selectedTool == tintTool || selectedTool == curvesTool) {
                blurLayout.setVisibility(selectedTool == blurTool ? VISIBLE : INVISIBLE);
                tintLayout.setVisibility(selectedTool == tintTool ? VISIBLE : INVISIBLE);
                curveLayout.setVisibility(selectedTool == curvesTool ? VISIBLE : INVISIBLE);
                if (selectedTool == blurTool) {
                    infoTextView.setText(LocaleController.getString("Blur", R.string.Blur));
                    if (blurType != 0) {
                        blurControl.setVisibility(VISIBLE);
                    }
                } else if (selectedTool == curvesTool) {
                    infoTextView.setText(LocaleController.getString("Curves", R.string.Curves));
                    curvesControl.setVisibility(VISIBLE);
                    curvesToolValue.activeType = 0;
                    for (int a = 0; a < 4; a++) {
                        curveTextView[a].setTextColor(a == 0 ? 0xffffffff : 0xff808080);
                    }
                } else {
                    selectedTintMode = 0;
                    updateSelectedTintButton(false);
                    infoTextView.setText(LocaleController.getString("Tint", R.string.Tint));
                }
                infoTextView.setVisibility(VISIBLE);
                valueSeekBar.setVisibility(INVISIBLE);
                paramTextView.setVisibility(INVISIBLE);
                valueTextView.setVisibility(INVISIBLE);
                updateSelectedBlurType();
            } else {
                tintLayout.setVisibility(INVISIBLE);
                curveLayout.setVisibility(INVISIBLE);
                blurLayout.setVisibility(INVISIBLE);
                valueSeekBar.setVisibility(VISIBLE);
                infoTextView.setVisibility(INVISIBLE);
                paramTextView.setVisibility(VISIBLE);
                valueTextView.setVisibility(VISIBLE);
                blurControl.setVisibility(INVISIBLE);
                curvesControl.setVisibility(INVISIBLE);
            }
        } else {
            selectedTool = -1;
            viewFrom = editView;
            viewTo = toolsView;
            blurControl.setVisibility(INVISIBLE);
            curvesControl.setVisibility(INVISIBLE);
        }

        AnimatorSetProxy animatorSet = new AnimatorSetProxy();
        animatorSet.playTogether(
                ObjectAnimatorProxy.ofFloat(viewFrom, "translationY", 0, AndroidUtilities.dp(126))
        );
        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Object animation) {
                viewFrom.clearAnimation();
                viewFrom.setVisibility(GONE);
                viewTo.setVisibility(VISIBLE);
                ViewProxy.setTranslationY(viewTo, AndroidUtilities.dp(126));

                AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                animatorSet.playTogether(
                        ObjectAnimatorProxy.ofFloat(viewTo, "translationY", 0)
                );
                animatorSet.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        viewTo.clearAnimation();
                        if (selectedTool == enhanceTool) {
                            checkEnhance();
                        }
                    }
                });
                animatorSet.setDuration(200);
                animatorSet.start();
            }
        });
        animatorSet.setDuration(200);
        animatorSet.start();
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
        viewHeight -= AndroidUtilities.dp(14 + 140);

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
        int bitmapY = (int) Math.ceil((viewHeight - bitmapH) / 2 + AndroidUtilities.dp(14));

        LayoutParams layoutParams = (LayoutParams) textureView.getLayoutParams();
        layoutParams.leftMargin = bitmapX;
        layoutParams.topMargin = bitmapY;
        layoutParams.width = (int) bitmapW;
        layoutParams.height = (int) bitmapH;
        textureView.setLayoutParams(layoutParams);
        curvesControl.setActualArea(bitmapX, bitmapY, layoutParams.width, layoutParams.height);

        blurControl.setActualAreaSize(layoutParams.width, layoutParams.height);
        layoutParams = (LayoutParams) blurControl.getLayoutParams();
        layoutParams.height = viewHeight + AndroidUtilities.dp(28);
        blurControl.setLayoutParams(layoutParams);

        layoutParams = (LayoutParams) curvesControl.getLayoutParams();
        layoutParams.height = viewHeight + AndroidUtilities.dp(28);
        curvesControl.setLayoutParams(layoutParams);

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
            recyclerListView.setLayoutParams(layoutParams);
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

    public FrameLayout getEditView() {
        return editView;
    }

    public TextView getDoneTextView() {
        return doneTextView;
    }

    public TextView getCancelTextView() {
        return cancelTextView;
    }

    public void setEditViewFirst() {
        selectedTool = 0;
        previousValue = enhanceValue;
        enhanceValue = 50;
        valueSeekBar.setMinMax(0, 100);
        paramTextView.setText(LocaleController.getString("Enhance", R.string.Enhance));
        editView.setVisibility(VISIBLE);
        toolsView.setVisibility(GONE);
        valueSeekBar.setProgress(50, false);
        updateValueTextView();
    }

    private void checkEnhance() {
        if (enhanceValue == 0) {
            AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
            animatorSetProxy.setDuration(200);
            animatorSetProxy.playTogether(ObjectAnimatorProxy.ofInt(valueSeekBar, "progress", 50));
            animatorSetProxy.start();
        }
    }

    public class ToolsAdapter extends RecyclerView.Adapter {

        private Context mContext;

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        public ToolsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return 14;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            PhotoEditToolCell view = new PhotoEditToolCell(mContext);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            Holder holder = (Holder) viewHolder;
            if (i == enhanceTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_enhance, LocaleController.getString("Enhance", R.string.Enhance), enhanceValue);
            } else if (i == highlightsTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_highlights, LocaleController.getString("Highlights", R.string.Highlights), highlightsValue);
            } else if (i == contrastTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_contrast, LocaleController.getString("Contrast", R.string.Contrast), contrastValue);
            } else if (i == exposureTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_brightness, LocaleController.getString("Exposure", R.string.Exposure), exposureValue);
            } else if (i == warmthTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_warmth, LocaleController.getString("Warmth", R.string.Warmth), warmthValue);
            } else if (i == saturationTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_saturation, LocaleController.getString("Saturation", R.string.Saturation), saturationValue);
            } else if (i == vignetteTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_vignette, LocaleController.getString("Vignette", R.string.Vignette), vignetteValue);
            } else if (i == shadowsTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_shadows, LocaleController.getString("Shadows", R.string.Shadows), shadowsValue);
            } else if (i == grainTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_grain, LocaleController.getString("Grain", R.string.Grain), grainValue);
            } else if (i == sharpenTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_details, LocaleController.getString("Sharpen", R.string.Sharpen), sharpenValue);
            } else if (i == tintTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_tint, LocaleController.getString("Tint", R.string.Tint), tintHighlightsColor != 0 || tintShadowsColor != 0 ? "◆" : "");
            } else if (i == fadeTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_fade, LocaleController.getString("Fade", R.string.Fade), fadeValue);
            } else if (i == curvesTool) {
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_curve, LocaleController.getString("Curves", R.string.Curves), curvesToolValue.shouldBeSkipped() ? "" : "◆");
            } else if (i == blurTool) {
                String value = "";
                if (blurType == 1) {
                    value = "R";
                } else if (blurType == 2) {
                    value = "L";
                }
                ((PhotoEditToolCell) holder.itemView).setIconAndTextAndValue(R.drawable.tool_blur, LocaleController.getString("Blur", R.string.Blur), value);
            }
        }
    }
}
