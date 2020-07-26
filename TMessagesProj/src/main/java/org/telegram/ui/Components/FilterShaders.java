package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.Utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Locale;

import javax.microedition.khronos.opengles.GL10;

public class FilterShaders {

    private static final String YUCIHighPassSkinSmoothingMaskBoostFilterFragmentShaderCode =
            "precision lowp float;" +
            "varying highp vec2 texCoord;" +
            "uniform sampler2D sourceImage;" +
            "void main() {" +
                "vec4 color = texture2D(sourceImage, texCoord);" +
                "float hardLightColor = color.b;" +
                "for (int i = 0; i < 3; ++i)" +
                "{" +
                    "if (hardLightColor < 0.5) {" +
                        "hardLightColor = hardLightColor * hardLightColor * 2.0;" +
                    "} else {" +
                        "hardLightColor = 1.0 - (1.0 - hardLightColor) * (1.0 - hardLightColor) * 2.0;" +
                    "}" +
                "}" +
                "float k = 255.0 / (164.0 - 75.0);" +
                "hardLightColor = (hardLightColor - 75.0 / 255.0) * k;" +
                "gl_FragColor = vec4(vec3(hardLightColor), color.a);" +
            "}";

    private static final String highpassSkinSmoothingCompositingFilterFragmentShaderString =
            "%1$s\n" +
            "precision lowp float;" +
            "varying highp vec2 texCoord;" +
            "varying highp vec2 texCoord2;" +
            "uniform %2$s sourceImage;" +
            "uniform sampler2D toneCurveTexture;" +
            "uniform sampler2D inputImageTexture3;" +
            "uniform lowp float mixturePercent;" +
            "void main() {" +
                "vec4 image = texture2D(sourceImage, texCoord);" +
                "vec4 mask = texture2D(inputImageTexture3, texCoord2);" +
                "float redCurveValue = texture2D(toneCurveTexture, vec2(image.r, 0.0)).r;" +
                "float greenCurveValue = texture2D(toneCurveTexture, vec2(image.g, 0.0)).g;" +
                "float blueCurveValue = texture2D(toneCurveTexture, vec2(image.b, 0.0)).b;" +
                "vec4 result = vec4(redCurveValue, greenCurveValue, blueCurveValue, image.a);" +
                "vec4 tone = mix(image, result, mixturePercent);" +
                "gl_FragColor = vec4(mix(image.rgb, tone.rgb, 1.0 - mask.b), 1.0);" +
            "}";

    private static final String greenAndBlueChannelOverlayFragmentShaderCode =
            "%1$s\n" +
            "precision lowp float;" +
            "varying highp vec2 texCoord;" +
            "uniform %2$s sourceImage;" +
            "void main() {" +
                "vec4 inp = texture2D(sourceImage, texCoord);" +
                "vec4 image = vec4(inp.rgb * pow(2.0, -1.0), inp.w);" +
                "vec4 base = vec4(image.g, image.g, image.g, 1.0);" +
                "vec4 overlay = vec4(image.b, image.b, image.b, 1.0);" +
                "float ba = 2.0 * overlay.b * base.b + overlay.b * (1.0 - base.a) + base.b * (1.0 - overlay.a);" +
                "gl_FragColor = vec4(ba,ba,ba,image.a);" +
            "}";

    private static final String stillImageHighPassFilterFragmentShaderCode =
            "precision lowp float;" +
            "varying highp vec2 texCoord;" +
            "varying highp vec2 texCoord2;" +
            "uniform sampler2D sourceImage;" +
            "uniform sampler2D inputImageTexture2;" +
            "void main() {" +
                "vec4 image = texture2D(sourceImage, texCoord);" +
                "vec4 blurredImage = texture2D(inputImageTexture2, texCoord2);" +
                "gl_FragColor = vec4((image.rgb - blurredImage.rgb + vec3(0.5,0.5,0.5)), image.a);" +
            "}";

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
    
    private static String vertexShaderForOptimizedBlurOfRadius(int blurRadius, float sigma) {
        float[] standardGaussianWeights = new float[blurRadius + 1];
        float sumOfWeights = 0.0f;
        for (int currentGaussianWeightIndex = 0; currentGaussianWeightIndex < blurRadius + 1; currentGaussianWeightIndex++) {
            standardGaussianWeights[currentGaussianWeightIndex] = (float) ((1.0 / Math.sqrt(2.0 * Math.PI * Math.pow(sigma, 2.0))) * Math.exp(-Math.pow(currentGaussianWeightIndex, 2.0) / (2.0 * Math.pow(sigma, 2.0))));
            if (currentGaussianWeightIndex == 0) {
                sumOfWeights += standardGaussianWeights[currentGaussianWeightIndex];
            } else {
                sumOfWeights += 2.0 * standardGaussianWeights[currentGaussianWeightIndex];
            }
        }
        for (int currentGaussianWeightIndex = 0; currentGaussianWeightIndex < blurRadius + 1; currentGaussianWeightIndex++) {
            standardGaussianWeights[currentGaussianWeightIndex] = standardGaussianWeights[currentGaussianWeightIndex] / sumOfWeights;
        }
        int numberOfOptimizedOffsets = Math.min(blurRadius / 2 + (blurRadius % 2), 7);
        float[] optimizedGaussianOffsets = new float[numberOfOptimizedOffsets];
        for (int currentOptimizedOffset = 0; currentOptimizedOffset < numberOfOptimizedOffsets; currentOptimizedOffset++) {
            float firstWeight = standardGaussianWeights[currentOptimizedOffset * 2 + 1];
            float secondWeight = standardGaussianWeights[currentOptimizedOffset * 2 + 2];
            float optimizedWeight = firstWeight + secondWeight;
            optimizedGaussianOffsets[currentOptimizedOffset] = (firstWeight * (currentOptimizedOffset * 2 + 1) + secondWeight * (currentOptimizedOffset * 2 + 2)) / optimizedWeight;
        }
        StringBuilder shaderString = new StringBuilder();
        shaderString.append("attribute vec4 position;\n");
        shaderString.append("attribute vec4 inputTexCoord;\n");
        shaderString.append("uniform float texelWidthOffset;\n");
        shaderString.append("uniform float texelHeightOffset;\n");
        shaderString.append(String.format(Locale.US, "varying vec2 blurCoordinates[%d];\n", 1 + (numberOfOptimizedOffsets * 2)));
        shaderString.append("void main()\n");
        shaderString.append("{\n");
        shaderString.append("gl_Position = position;\n");
        shaderString.append("vec2 singleStepOffset = vec2(texelWidthOffset, texelHeightOffset);\n");
        shaderString.append("blurCoordinates[0] = inputTexCoord.xy;\n");
        for (int currentOptimizedOffset = 0; currentOptimizedOffset < numberOfOptimizedOffsets; currentOptimizedOffset++) {
            shaderString.append(String.format(Locale.US,
                    "blurCoordinates[%d] = inputTexCoord.xy + singleStepOffset * %f;\n" +
                            "blurCoordinates[%d] = inputTexCoord.xy - singleStepOffset * %f;\n", ((currentOptimizedOffset * 2) + 1), optimizedGaussianOffsets[currentOptimizedOffset], ((currentOptimizedOffset * 2) + 2), optimizedGaussianOffsets[currentOptimizedOffset]));
        }
        shaderString.append("}");
        return shaderString.toString();
    }

    private static String fragmentShaderForOptimizedBlurOfRadius(int blurRadius, float sigma) {
        float[] standardGaussianWeights = new float[blurRadius + 1];
        float sumOfWeights = 0.0f;
        for (int currentGaussianWeightIndex = 0; currentGaussianWeightIndex < blurRadius + 1; currentGaussianWeightIndex++) {
            standardGaussianWeights[currentGaussianWeightIndex] = (float) ((1.0 / Math.sqrt(2.0 * Math.PI * Math.pow(sigma, 2.0))) * Math.exp(-Math.pow(currentGaussianWeightIndex, 2.0) / (2.0 * Math.pow(sigma, 2.0))));
            if (currentGaussianWeightIndex == 0) {
                sumOfWeights += standardGaussianWeights[currentGaussianWeightIndex];
            } else {
                sumOfWeights += 2.0 * standardGaussianWeights[currentGaussianWeightIndex];
            }
        }
        for (int currentGaussianWeightIndex = 0; currentGaussianWeightIndex < blurRadius + 1; currentGaussianWeightIndex++) {
            standardGaussianWeights[currentGaussianWeightIndex] = standardGaussianWeights[currentGaussianWeightIndex] / sumOfWeights;
        }
        int numberOfOptimizedOffsets = Math.min(blurRadius / 2 + (blurRadius % 2), 7);
        int trueNumberOfOptimizedOffsets = blurRadius / 2 + (blurRadius % 2);

        StringBuilder shaderString = new StringBuilder();

        shaderString.append("uniform sampler2D sourceImage;\n");
        shaderString.append("uniform highp float texelWidthOffset;\n");
        shaderString.append("uniform highp float texelHeightOffset;\n");
        shaderString.append(String.format(Locale.US, "varying highp vec2 blurCoordinates[%d];\n", 1 + (numberOfOptimizedOffsets * 2)));
        shaderString.append("void main()\n");
        shaderString.append("{\n");
        shaderString.append("lowp vec4 sum = vec4(0.0);\n");
        shaderString.append(String.format(Locale.US, "sum += texture2D(sourceImage, blurCoordinates[0]) * %f;\n", standardGaussianWeights[0]));

        for (int currentBlurCoordinateIndex = 0; currentBlurCoordinateIndex < numberOfOptimizedOffsets; currentBlurCoordinateIndex++) {
            float firstWeight = standardGaussianWeights[currentBlurCoordinateIndex * 2 + 1];
            float secondWeight = standardGaussianWeights[currentBlurCoordinateIndex * 2 + 2];
            float optimizedWeight = firstWeight + secondWeight;
            shaderString.append(String.format(Locale.US, "sum += texture2D(sourceImage, blurCoordinates[%d]) * %f;\n", ((currentBlurCoordinateIndex * 2) + 1), optimizedWeight));
            shaderString.append(String.format(Locale.US, "sum += texture2D(sourceImage, blurCoordinates[%d]) * %f;\n", ((currentBlurCoordinateIndex * 2) + 2), optimizedWeight));
        }

        if (trueNumberOfOptimizedOffsets > numberOfOptimizedOffsets) {
            shaderString.append("highp vec2 singleStepOffset = vec2(texelWidthOffset, texelHeightOffset);\n");

            for (int currentOverlowTextureRead = numberOfOptimizedOffsets; currentOverlowTextureRead < trueNumberOfOptimizedOffsets; currentOverlowTextureRead++) {
                float firstWeight = standardGaussianWeights[currentOverlowTextureRead * 2 + 1];
                float secondWeight = standardGaussianWeights[currentOverlowTextureRead * 2 + 2];
                float optimizedWeight = firstWeight + secondWeight;
                float optimizedOffset = (firstWeight * (currentOverlowTextureRead * 2 + 1) + secondWeight * (currentOverlowTextureRead * 2 + 2)) / optimizedWeight;
                shaderString.append(String.format(Locale.US, "sum += texture2D(sourceImage, blurCoordinates[0] + singleStepOffset * %f) * %f;\n", optimizedOffset, optimizedWeight));
                shaderString.append(String.format(Locale.US, "sum += texture2D(sourceImage, blurCoordinates[0] - singleStepOffset * %f) * %f;\n", optimizedOffset, optimizedWeight));
            }
        }

        shaderString.append("gl_FragColor = sum;\n");
        shaderString.append("}\n");

        return shaderString.toString();
    }

    private static class BlurProgram {

        private String vertexShaderCode;
        private String fragmentShaderCode;

        public int blurShaderProgram;
        public int blurPositionHandle;
        public int blurInputTexCoordHandle;
        public int blurSourceImageHandle;
        public int blurWidthHandle;
        public int blurHeightHandle;

        public BlurProgram(float radius, float sigma, boolean calc) {
            int calculatedSampleRadius;
            if (calc) {
                sigma = Math.round(radius);
                calculatedSampleRadius = 0;
                if (sigma >= 1) {
                    float minimumWeightToFindEdgeOfSamplingArea = 1.0f / 256.0f;
                    calculatedSampleRadius = (int) Math.floor(Math.sqrt(-2.0 * Math.pow(sigma, 2.0) * Math.log(minimumWeightToFindEdgeOfSamplingArea * Math.sqrt(2.0 * Math.PI * Math.pow(sigma, 2.0)))));
                    calculatedSampleRadius += calculatedSampleRadius % 2;
                }
            } else {
                calculatedSampleRadius = (int) radius;
            }
            fragmentShaderCode = fragmentShaderForOptimizedBlurOfRadius(calculatedSampleRadius, sigma);
            vertexShaderCode = vertexShaderForOptimizedBlurOfRadius(calculatedSampleRadius, sigma);
        }

        public void destroy() {
            if (blurShaderProgram != 0) {
                GLES20.glDeleteProgram(blurShaderProgram);
                blurShaderProgram = 0;
            }
        }

        public boolean create() {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

            if (vertexShader == 0 || fragmentShader == 0) {
                return false;
            }
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
            return true;
        }
    }

    private static final String simpleVertexVideoShaderCode =
            "attribute vec4 position;" +
            "uniform mat4 videoMatrix;" +
            "attribute vec4 inputTexCoord;" +
            "varying vec2 texCoord;" +
            "void main() {" +
                "gl_Position = position;" +
                "texCoord = vec2(videoMatrix * inputTexCoord).xy;" +
            "}";

    private static final String simpleTwoTextureVertexVideoShaderCode =
            "attribute vec4 position;" +
            "uniform mat4 videoMatrix;" +
            "attribute vec4 inputTexCoord;" +
            "varying vec2 texCoord;" +
            "varying vec2 texCoord2;" +
            "void main() {" +
                "gl_Position = position;" +
                "texCoord = vec2(videoMatrix * inputTexCoord).xy;" +
                "texCoord2 = inputTexCoord.xy;" +
            "}";

    private static final String rgbToHsvFragmentShaderCode =
            "%1$s\n" +
            "precision highp float;" +
            "varying vec2 texCoord;" +
            "uniform %2$s sourceImage;" +
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

    public static final String simpleVertexShaderCode =
            "attribute vec4 position;" +
            "attribute vec2 inputTexCoord;" +
            "varying vec2 texCoord;" +
            "void main() {" +
                "gl_Position = position;" +
                "texCoord = inputTexCoord;" +
            "}";

    public static final String simpleTwoTextureVertexShaderCode =
            "attribute vec4 position;" +
            "attribute vec2 inputTexCoord;" +
            "varying vec2 texCoord;" +
            "varying vec2 texCoord2;" +
            "void main() {" +
                "gl_Position = position;" +
                "texCoord = inputTexCoord;" +
                "texCoord2 = inputTexCoord;" +
            "}";

    public static final String simpleFragmentShaderCode =
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
                "}" +
                "return res;" +
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

    public interface FilterShadersDelegate {
        boolean shouldShowOriginal();
        float getShadowsValue();
        float getSoftenSkinValue();
        float getHighlightsValue();
        float getEnhanceValue();
        float getExposureValue();
        float getContrastValue();
        float getWarmthValue();
        float getVignetteValue();
        float getSharpenValue();
        float getGrainValue();
        float getFadeValue();
        float getTintHighlightsIntensityValue();
        float getTintShadowsIntensityValue();
        float getSaturationValue();
        int getTintHighlightsColor();
        int getTintShadowsColor();
        int getBlurType();
        float getBlurExcludeSize();
        float getBlurExcludeBlurSize();
        float getBlurAngle();
        Point getBlurExcludePoint();
        boolean shouldDrawCurvesPass();
        ByteBuffer fillAndGetCurveBuffer();
    }

    private static class ToneCurve {

        private float[] rgbCompositeCurve;
        private float[] redCurve;
        private float[] greenCurve;
        private float[] blueCurve;
        private int[] curveTexture = new int[1];

        public ToneCurve() {
            ArrayList<PointF> defaultCurve = new ArrayList<>();
            defaultCurve.add(new PointF(0.0f, 0.0f));
            defaultCurve.add(new PointF(0.5f, 0.5f));
            defaultCurve.add(new PointF(1.0f, 1.0f));

            ArrayList<PointF> rgbDefaultCurve = new ArrayList<>();
            rgbDefaultCurve.add(new PointF(0.0f, 0.0f));
            rgbDefaultCurve.add(new PointF(0.47f, 0.57f));
            rgbDefaultCurve.add(new PointF(1.0f, 1.0f));

            rgbCompositeCurve = getPreparedSplineCurve(rgbDefaultCurve);
            redCurve = greenCurve = blueCurve = getPreparedSplineCurve(defaultCurve);
            updateToneCurveTexture();
        }

        private float[] getPreparedSplineCurve(ArrayList<PointF> points) {
            for (int i = 0, N = points.size(); i < N; i++) {
                PointF point = points.get(i);
                point.x *= 255;
                point.y *= 255;
            }

            ArrayList<PointF> splinePoints = splineCurve(points);

            PointF firstSplinePoint = splinePoints.get(0);
            if (firstSplinePoint.x > 0) {
                for (int i = (int) firstSplinePoint.x; i >= 0; i--) {
                    splinePoints.add(0, new PointF(i, 0));
                }
            }

            PointF lastSplinePoint = splinePoints.get(splinePoints.size() - 1);
            if (lastSplinePoint.x < 255) {
                for (int i = (int) lastSplinePoint.x + 1; i <= 255; i++) {
                    splinePoints.add(new PointF(i, 255));
                }
            }

            float[] preparedSplinePoints = new float[splinePoints.size()];
            for (int i = 0, N = splinePoints.size(); i < N; i++) {
                PointF newPoint = splinePoints.get(i);
                float distance = (float) Math.sqrt(Math.pow((newPoint.x - newPoint.y), 2.0));
                if (newPoint.x > newPoint.y) {
                    distance = -distance;
                }
                preparedSplinePoints[i] = distance;
            }

            return preparedSplinePoints;
        }

        private ArrayList<PointF> splineCurve(ArrayList<PointF> points) {
            double[] sd = secondDerivative(points);
            int n = sd.length;
            if (n < 1) {
                return null;
            }
            ArrayList<PointF> output = new ArrayList<>(n + 1);
            for (int i = 0; i < n - 1; i++) {
                PointF cur = points.get(i);
                PointF next = points.get(i + 1);
                for (int x = (int) cur.x; x < (int) next.x; x++) {
                    double t = (double) (x - cur.x) / (next.x - cur.x);
                    double a = 1 - t;
                    double h = next.x - cur.x;
                    float y = (float) (a * cur.y + t * next.y + (h * h / 6) * ((a * a * a - a) * sd[i] + (t * t * t - t) * sd[i + 1]));
                    if (y > 255.0f) {
                        y = 255.0f;
                    } else if (y < 0.0f) {
                        y = 0.0f;
                    }
                    output.add(new PointF(x, y));
                }
            }
            output.add(points.get(points.size() - 1));
            return output;
        }

        private double[] secondDerivative(ArrayList<PointF> points) {
            int n = points.size();
            if ((n <= 0) || (n == 1)) {
                return null;
            }

            double[][] matrix = new double[n][3];
            double[] result = new double[n];
            matrix[0][1] = 1;
            matrix[0][0] = 0;
            matrix[0][2] = 0;

            for (int i = 1; i < n - 1; i++) {
                PointF P1 = points.get(i - 1);
                PointF P2 = points.get(i);
                PointF P3 = points.get(i + 1);

                matrix[i][0] = (double) (P2.x - P1.x) / 6;
                matrix[i][1] = (double) (P3.x - P1.x) / 3;
                matrix[i][2] = (double) (P3.x - P2.x) / 6;
                result[i] = (double) (P3.y - P2.y) / (P3.x - P2.x) - (double) (P2.y - P1.y) / (P2.x - P1.x);
            }

            result[0] = 0;
            result[n - 1] = 0;

            matrix[n - 1][1] = 1;
            matrix[n - 1][0] = 0;
            matrix[n - 1][2] = 0;

            for (int i = 1; i < n; i++) {
                double k = matrix[i][0] / matrix[i - 1][1];
                matrix[i][1] -= k * matrix[i - 1][2];
                matrix[i][0] = 0;
                result[i] -= k * result[i - 1];
            }

            for (int i = n - 2; i >= 0; i--) {
                double k = matrix[i][2] / matrix[i + 1][1];
                matrix[i][1] -= k * matrix[i + 1][0];
                matrix[i][2] = 0;
                result[i] -= k * result[i + 1];
            }

            double[] output = new double[n];
            for (int i = 0; i < n; i++) {
                output[i] = result[i] / matrix[i][1];
            }
            return output;
        }

        private void updateToneCurveTexture() {
            GLES20.glGenTextures(1, curveTexture, 0);
            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, curveTexture[0]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

            ByteBuffer curveBuffer = ByteBuffer.allocateDirect(256 * 4);
            curveBuffer.order(ByteOrder.LITTLE_ENDIAN);

            if (redCurve.length >= 256 && greenCurve.length >= 256 && blueCurve.length >= 256 && rgbCompositeCurve.length >= 256) {
                for (int currentCurveIndex = 0; currentCurveIndex < 256; currentCurveIndex++) {
                    int r = (int) Math.min(Math.max(currentCurveIndex + redCurve[currentCurveIndex], 0), 255);
                    int g = (int) Math.min(Math.max(currentCurveIndex + greenCurve[currentCurveIndex], 0), 255);
                    int b = (int) Math.min(Math.max(currentCurveIndex + blueCurve[currentCurveIndex], 0), 255);

                    curveBuffer.put((byte) Math.min(Math.max(b + rgbCompositeCurve[b], 0), 255));
                    curveBuffer.put((byte) Math.min(Math.max(g + rgbCompositeCurve[g], 0), 255));
                    curveBuffer.put((byte) Math.min(Math.max(r + rgbCompositeCurve[r], 0), 255));
                    curveBuffer.put((byte) 255);
                }
                curveBuffer.position(0);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, curveBuffer);
            }
        }
        
        public int getCurveTexture() {
            return curveTexture[0];
        }
    }

    private boolean needUpdateBlurTexture = true;
    private boolean needUpdateSkinTexture = true;

    private boolean blurTextureCreated;
    private boolean skinTextureCreated;

    private BlurProgram skinBlurProgram;
    private float lastRadius;
    private BlurProgram blurProgram;

    private boolean skinPassDrawn;
    private int greenAndBlueChannelOverlayProgram;
    private int greenAndBlueChannelOverlayPositionHandle;
    private int greenAndBlueChannelOverlayInputTexCoordHandle;
    private int greenAndBlueChannelOverlaySourceImageHandle;
    private int greenAndBlueChannelOverlayMatrixHandle;
    private ToneCurve toneCurve;

    private int highPassProgram;
    private int highPassPositionHandle;
    private int highPassInputTexCoordHandle;
    private int highPassSourceImageHandle;
    private int highPassInputImageHandle;

    private int compositeProgram;
    private int compositePositionHandle;
    private int compositeInputTexCoordHandle;
    private int compositeSourceImageHandle;
    private int compositeInputImageHandle;
    private int compositeCurveImageHandle;
    private int compositeMixtureHandle;
    private int compositeMatrixHandle;

    private int boostProgram;
    private int boostPositionHandle;
    private int boostInputTexCoordHandle;
    private int boostSourceImageHandle;

    private int[] rgbToHsvShaderProgram = new int[2];
    private int[] rgbToHsvPositionHandle = new int[2];
    private int[] rgbToHsvInputTexCoordHandle = new int[2];
    private int[] rgbToHsvSourceImageHandle = new int[2];
    private int rgbToHsvMatrixHandle;

    private int enhanceShaderProgram;
    private int enhancePositionHandle;
    private int enhanceInputTexCoordHandle;
    private int enhanceSourceImageHandle;
    private int enhanceIntensityHandle;
    private int enhanceInputImageTexture2Handle;

    private int toolsShaderProgram;
    private int positionHandle;
    private int inputTexCoordHandle;
    private int sourceImageHandle;
    private int shadowsHandle;
    private int highlightsHandle;
    private int exposureHandle;
    private int contrastHandle;
    private int saturationHandle;
    private int warmthHandle;
    private int vignetteHandle;
    private int grainHandle;
    private int widthHandle;
    private int heightHandle;

    private int curvesImageHandle;
    private int skipToneHandle;
    private int fadeAmountHandle;
    private int shadowsTintIntensityHandle;
    private int highlightsTintIntensityHandle;
    private int shadowsTintColorHandle;
    private int highlightsTintColorHandle;

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

    private int videoTexture;
    private float[] videoMatrix;
    private int videoFramesCount;

    private int[] enhanceTextures = new int[2];
    private int[] enhanceFrameBuffer = new int[1];
    private int[] renderTexture = new int[4];
    private int[] bitmapTextre = new int[1];
    private int[] renderFrameBuffer;
    private int[] curveTextures = new int[1];
    private boolean hsvGenerated;
    private int renderBufferWidth;
    private int renderBufferHeight;

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private FloatBuffer vertexInvertBuffer;

    private ByteBuffer hsvBuffer;
    private ByteBuffer cdtBuffer;
    private ByteBuffer calcBuffer;

    private final static int PGPhotoEnhanceHistogramBins = 256;
    private final static int PGPhotoEnhanceSegments = 4;

    private FilterShadersDelegate delegate;

    private boolean isVideo;

    public FilterShaders(boolean video) {
        isVideo = video;

        float[] squareCoordinates = {
                -1.0f, 1.0f,
                1.0f, 1.0f,
                -1.0f, -1.0f,
                1.0f, -1.0f};

        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoordinates.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoordinates);
        vertexBuffer.position(0);

        float[] squareCoordinates2 = {
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f, 1.0f,
                1.0f, 1.0f};

        bb = ByteBuffer.allocateDirect(squareCoordinates2.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexInvertBuffer = bb.asFloatBuffer();
        vertexInvertBuffer.put(squareCoordinates2);
        vertexInvertBuffer.position(0);

        float[] textureCoordinates = {
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
    }

    public void setDelegate(FilterShadersDelegate filterShadersDelegate) {
        delegate = filterShadersDelegate;
    }

    public boolean create() {
        GLES20.glGenTextures(1, curveTextures, 0);
        GLES20.glGenTextures(2, enhanceTextures, 0);
        GLES20.glGenFramebuffers(1, enhanceFrameBuffer, 0);

        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, enhanceTextures[1]);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, curveTextures[0]);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        int[] linkStatus = new int[1];

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, toolsFragmentShaderCode);

        if (vertexShader != 0 && fragmentShader != 0) {
            toolsShaderProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(toolsShaderProgram, vertexShader);
            GLES20.glAttachShader(toolsShaderProgram, fragmentShader);
            GLES20.glBindAttribLocation(toolsShaderProgram, 0, "position");
            GLES20.glBindAttribLocation(toolsShaderProgram, 1, "inputTexCoord");

            GLES20.glLinkProgram(toolsShaderProgram);
            GLES20.glGetProgramiv(toolsShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
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
            return false;
        }

        blurProgram = new BlurProgram(8, 3.0f, false);
        if (!blurProgram.create()) {
            return false;
        }

        String extension = isVideo ? "#extension GL_OES_EGL_image_external : require" : "";
        String sampler2D = isVideo ? "samplerExternalOES" : "sampler2D";

        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, linearBlurFragmentShaderCode);

        if (vertexShader != 0 && fragmentShader != 0) {
            linearBlurShaderProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(linearBlurShaderProgram, vertexShader);
            GLES20.glAttachShader(linearBlurShaderProgram, fragmentShader);
            GLES20.glBindAttribLocation(linearBlurShaderProgram, 0, "position");
            GLES20.glBindAttribLocation(linearBlurShaderProgram, 1, "inputTexCoord");

            GLES20.glLinkProgram(linearBlurShaderProgram);
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
            return false;
        }

        for (int a = 0; a < (isVideo ? 2 : 1); a++) {
            if (a == 1 && isVideo) {
                vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexVideoShaderCode);
                fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, String.format(Locale.US, rgbToHsvFragmentShaderCode, extension, sampler2D));
            } else {
                vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
                fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, String.format(Locale.US, rgbToHsvFragmentShaderCode, "", "sampler2D"));
            }
            if (vertexShader != 0 && fragmentShader != 0) {
                rgbToHsvShaderProgram[a] = GLES20.glCreateProgram();
                GLES20.glAttachShader(rgbToHsvShaderProgram[a], vertexShader);
                GLES20.glAttachShader(rgbToHsvShaderProgram[a], fragmentShader);
                GLES20.glBindAttribLocation(rgbToHsvShaderProgram[a], 0, "position");
                GLES20.glBindAttribLocation(rgbToHsvShaderProgram[a], 1, "inputTexCoord");

                GLES20.glLinkProgram(rgbToHsvShaderProgram[a]);
                GLES20.glGetProgramiv(rgbToHsvShaderProgram[a], GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(rgbToHsvShaderProgram[a]);
                    rgbToHsvShaderProgram[a] = 0;
                } else {
                    rgbToHsvPositionHandle[a] = GLES20.glGetAttribLocation(rgbToHsvShaderProgram[a], "position");
                    rgbToHsvInputTexCoordHandle[a] = GLES20.glGetAttribLocation(rgbToHsvShaderProgram[a], "inputTexCoord");
                    rgbToHsvSourceImageHandle[a] = GLES20.glGetUniformLocation(rgbToHsvShaderProgram[a], "sourceImage");
                    if (a == 1) {
                        rgbToHsvMatrixHandle = GLES20.glGetUniformLocation(rgbToHsvShaderProgram[a], "videoMatrix");
                    }
                }
            } else {
                return false;
            }
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
            return false;
        }

        if (isVideo) {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexVideoShaderCode);
        } else {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
        }
        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, String.format(Locale.US, greenAndBlueChannelOverlayFragmentShaderCode, extension, sampler2D));
        if (vertexShader != 0 && fragmentShader != 0) {
            greenAndBlueChannelOverlayProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(greenAndBlueChannelOverlayProgram, vertexShader);
            GLES20.glAttachShader(greenAndBlueChannelOverlayProgram, fragmentShader);
            GLES20.glBindAttribLocation(greenAndBlueChannelOverlayProgram, 0, "position");
            GLES20.glBindAttribLocation(greenAndBlueChannelOverlayProgram, 1, "inputTexCoord");

            GLES20.glLinkProgram(greenAndBlueChannelOverlayProgram);
            GLES20.glGetProgramiv(greenAndBlueChannelOverlayProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(greenAndBlueChannelOverlayProgram);
                greenAndBlueChannelOverlayProgram = 0;
            } else {
                greenAndBlueChannelOverlayPositionHandle = GLES20.glGetAttribLocation(greenAndBlueChannelOverlayProgram, "position");
                greenAndBlueChannelOverlayInputTexCoordHandle = GLES20.glGetAttribLocation(greenAndBlueChannelOverlayProgram, "inputTexCoord");
                greenAndBlueChannelOverlaySourceImageHandle = GLES20.glGetUniformLocation(greenAndBlueChannelOverlayProgram, "sourceImage");
                if (isVideo) {
                    greenAndBlueChannelOverlayMatrixHandle = GLES20.glGetUniformLocation(greenAndBlueChannelOverlayProgram, "videoMatrix");
                }
            }
        } else {
            return false;
        }

        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, stillImageHighPassFilterFragmentShaderCode);
        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleTwoTextureVertexShaderCode);
        if (vertexShader != 0 && fragmentShader != 0) {
            highPassProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(highPassProgram, vertexShader);
            GLES20.glAttachShader(highPassProgram, fragmentShader);
            GLES20.glBindAttribLocation(highPassProgram, 0, "position");
            GLES20.glBindAttribLocation(highPassProgram, 1, "inputTexCoord");

            GLES20.glLinkProgram(highPassProgram);
            GLES20.glGetProgramiv(highPassProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(highPassProgram);
                highPassProgram = 0;
            } else {
                highPassPositionHandle = GLES20.glGetAttribLocation(highPassProgram, "position");
                highPassInputTexCoordHandle = GLES20.glGetAttribLocation(highPassProgram, "inputTexCoord");
                highPassSourceImageHandle = GLES20.glGetUniformLocation(highPassProgram, "sourceImage");
                highPassInputImageHandle = GLES20.glGetUniformLocation(highPassProgram, "inputImageTexture2");
            }
        } else {
            return false;
        }

        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, YUCIHighPassSkinSmoothingMaskBoostFilterFragmentShaderCode);
        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleVertexShaderCode);
        if (vertexShader != 0 && fragmentShader != 0) {
            boostProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(boostProgram, vertexShader);
            GLES20.glAttachShader(boostProgram, fragmentShader);
            GLES20.glBindAttribLocation(boostProgram, 0, "position");
            GLES20.glBindAttribLocation(boostProgram, 1, "inputTexCoord");

            GLES20.glLinkProgram(boostProgram);
            GLES20.glGetProgramiv(boostProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(boostProgram);
                boostProgram = 0;
            } else {
                boostPositionHandle = GLES20.glGetAttribLocation(boostProgram, "position");
                boostInputTexCoordHandle = GLES20.glGetAttribLocation(boostProgram, "inputTexCoord");
                boostSourceImageHandle = GLES20.glGetUniformLocation(boostProgram, "sourceImage");
            }
        } else {
            return false;
        }

        if (isVideo) {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleTwoTextureVertexVideoShaderCode);
        } else {
            vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, simpleTwoTextureVertexShaderCode);
        }
        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, String.format(Locale.US, highpassSkinSmoothingCompositingFilterFragmentShaderString, extension, sampler2D));
        if (vertexShader != 0 && fragmentShader != 0) {
            compositeProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(compositeProgram, vertexShader);
            GLES20.glAttachShader(compositeProgram, fragmentShader);
            GLES20.glBindAttribLocation(compositeProgram, 0, "position");
            GLES20.glBindAttribLocation(compositeProgram, 1, "inputTexCoord");

            GLES20.glLinkProgram(compositeProgram);
            GLES20.glGetProgramiv(compositeProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(compositeProgram);
                compositeProgram = 0;
            } else {
                compositePositionHandle = GLES20.glGetAttribLocation(compositeProgram, "position");
                compositeInputTexCoordHandle = GLES20.glGetAttribLocation(compositeProgram, "inputTexCoord");
                compositeSourceImageHandle = GLES20.glGetUniformLocation(compositeProgram, "sourceImage");
                compositeInputImageHandle = GLES20.glGetUniformLocation(compositeProgram, "inputImageTexture3");
                compositeCurveImageHandle = GLES20.glGetUniformLocation(compositeProgram, "toneCurveTexture");
                compositeMixtureHandle = GLES20.glGetUniformLocation(compositeProgram, "mixturePercent");
                if (isVideo) {
                    compositeMatrixHandle = GLES20.glGetUniformLocation(compositeProgram, "videoMatrix");
                }
            }
        } else {
            return false;
        }

        toneCurve = new ToneCurve();

        return true;
    }

    public void setRenderData(Bitmap currentBitmap, int orientation, int videoTex, int w, int h) {
        loadTexture(currentBitmap, orientation, w, h);
        videoTexture = videoTex;

        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, enhanceTextures[0]);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
    }

    public static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(GLES20.glGetShaderInfoLog(shader));
                FileLog.e("shader code:\n " + shaderCode);
            }
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    public void drawEnhancePass() {
        boolean updateFrame;
        if (isVideo) {
            updateFrame = true;
        } else {
            updateFrame = !hsvGenerated;
        }
        if (updateFrame) {
            int index;
            if (isVideo && !skinPassDrawn) {
                GLES20.glUseProgram(rgbToHsvShaderProgram[1]);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture);
                GLES20.glUniformMatrix4fv(rgbToHsvMatrixHandle, 1, false, videoMatrix, 0);
                index = 1;
            } else {
                GLES20.glUseProgram(rgbToHsvShaderProgram[0]);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, skinPassDrawn ? renderTexture[1] : bitmapTextre[0]);
                index = 0;
            }

            GLES20.glUniform1i(rgbToHsvSourceImageHandle[index], 0);
            GLES20.glEnableVertexAttribArray(rgbToHsvInputTexCoordHandle[index]);
            GLES20.glVertexAttribPointer(rgbToHsvInputTexCoordHandle[index], 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(rgbToHsvPositionHandle[index]);
            GLES20.glVertexAttribPointer(rgbToHsvPositionHandle[index], 2, GLES20.GL_FLOAT, false, 8, isVideo ? vertexInvertBuffer : vertexBuffer);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, enhanceFrameBuffer[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, enhanceTextures[0], 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

        if (!hsvGenerated) {
            int newCapacity = renderBufferWidth * renderBufferHeight * 4;
            if (hsvBuffer == null || newCapacity > hsvBuffer.capacity()) {
                hsvBuffer = ByteBuffer.allocateDirect(newCapacity);
            }
            if (cdtBuffer == null) {
                cdtBuffer = ByteBuffer.allocateDirect(PGPhotoEnhanceSegments * PGPhotoEnhanceSegments * PGPhotoEnhanceHistogramBins * 4);
            }
            if (calcBuffer == null) {
                calcBuffer = ByteBuffer.allocateDirect(PGPhotoEnhanceSegments * PGPhotoEnhanceSegments * 2 * 4 * (1 + PGPhotoEnhanceHistogramBins));
            }
            GLES20.glReadPixels(0, 0, renderBufferWidth, renderBufferHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, hsvBuffer);
            Utilities.calcCDT(hsvBuffer, renderBufferWidth, renderBufferHeight, cdtBuffer, calcBuffer);

            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, enhanceTextures[1]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 16, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, cdtBuffer);

            if (!isVideo) {
                hsvBuffer = null;
                cdtBuffer = null;
                calcBuffer = null;
            }
            hsvGenerated = true;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);

        GLES20.glUseProgram(enhanceShaderProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, enhanceTextures[0]);
        GLES20.glUniform1i(enhanceSourceImageHandle, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, enhanceTextures[1]);
        GLES20.glUniform1i(enhanceInputImageTexture2Handle, 1);
        if (delegate == null || delegate.shouldShowOriginal()) {
            GLES20.glUniform1f(enhanceIntensityHandle, 0);
        } else {
            GLES20.glUniform1f(enhanceIntensityHandle, delegate.getEnhanceValue());
        }

        GLES20.glEnableVertexAttribArray(enhanceInputTexCoordHandle);
        GLES20.glVertexAttribPointer(enhanceInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(enhancePositionHandle);
        GLES20.glVertexAttribPointer(enhancePositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    public void drawSharpenPass() {
        if (isVideo) {
            return;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);

        GLES20.glUseProgram(sharpenShaderProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
        GLES20.glUniform1i(sharpenSourceImageHandle, 0);
        if (delegate == null || delegate.shouldShowOriginal()) {
            GLES20.glUniform1f(sharpenHandle, 0);
        } else {
            GLES20.glUniform1f(sharpenHandle, delegate.getSharpenValue());
        }
        GLES20.glUniform1f(sharpenWidthHandle, renderBufferWidth);
        GLES20.glUniform1f(sharpenHeightHandle, renderBufferHeight);
        GLES20.glEnableVertexAttribArray(sharpenInputTexCoordHandle);
        GLES20.glVertexAttribPointer(sharpenInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(sharpenPositionHandle);
        GLES20.glVertexAttribPointer(sharpenPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    public void drawCustomParamsPass() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[isVideo ? 0 : 1]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[isVideo ? 0 : 1], 0);

        GLES20.glUseProgram(toolsShaderProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[isVideo ? 1 : 0]);
        GLES20.glUniform1i(sourceImageHandle, 0);
        if (delegate == null || delegate.shouldShowOriginal()) {
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
            GLES20.glUniform1f(shadowsHandle, delegate.getShadowsValue());
            GLES20.glUniform1f(highlightsHandle, delegate.getHighlightsValue());
            GLES20.glUniform1f(exposureHandle, delegate.getExposureValue());
            GLES20.glUniform1f(contrastHandle, delegate.getContrastValue());
            GLES20.glUniform1f(saturationHandle, delegate.getSaturationValue());
            GLES20.glUniform1f(warmthHandle, delegate.getWarmthValue());
            GLES20.glUniform1f(vignetteHandle, delegate.getVignetteValue());
            GLES20.glUniform1f(grainHandle, delegate.getGrainValue());
            GLES20.glUniform1f(fadeAmountHandle, delegate.getFadeValue());
            int tintHighlightsColor = delegate.getTintHighlightsColor();
            int tintShadowsColor = delegate.getTintShadowsColor();
            GLES20.glUniform3f(highlightsTintColorHandle, (tintHighlightsColor >> 16 & 0xff) / 255.0f, (tintHighlightsColor >> 8 & 0xff) / 255.0f, (tintHighlightsColor & 0xff) / 255.0f);
            GLES20.glUniform1f(highlightsTintIntensityHandle, delegate.getTintHighlightsIntensityValue());
            GLES20.glUniform3f(shadowsTintColorHandle, (tintShadowsColor >> 16 & 0xff) / 255.0f, (tintShadowsColor >> 8 & 0xff) / 255.0f, (tintShadowsColor & 0xff) / 255.0f);
            GLES20.glUniform1f(shadowsTintIntensityHandle, delegate.getTintShadowsIntensityValue());
            boolean shouldDrawCurvesPass = delegate.shouldDrawCurvesPass();
            GLES20.glUniform1f(skipToneHandle, shouldDrawCurvesPass ? 0.0f : 1.0f);
            if (shouldDrawCurvesPass) {
                ByteBuffer curveBuffer = delegate.fillAndGetCurveBuffer();
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
                GLES20.glBindTexture(GL10.GL_TEXTURE_2D, curveTextures[0]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 200, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, curveBuffer);
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

    public boolean drawSkinSmoothPass() {
        if (delegate == null || delegate.shouldShowOriginal() || delegate.getSoftenSkinValue() <= 0.0f || renderBufferWidth <= 0 || renderBufferHeight <= 0) {
            if (skinPassDrawn) {
                hsvGenerated = false;
                skinPassDrawn = false;
            }
            return false;
        }
        if (needUpdateSkinTexture || isVideo) {
            float rad = renderBufferWidth * 0.006f;
            if (skinBlurProgram == null || Math.abs(lastRadius - rad) > 0.0001) {
                if (skinBlurProgram != null) {
                    skinBlurProgram.destroy();
                }
                lastRadius = rad;
                skinBlurProgram = new BlurProgram(lastRadius, 2.0f, true);
                skinBlurProgram.create();
            }

            if (!skinTextureCreated) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[3]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                skinTextureCreated = true;
            }

            //green blue pass
            GLES20.glUseProgram(greenAndBlueChannelOverlayProgram);
            GLES20.glUniform1i(greenAndBlueChannelOverlaySourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(greenAndBlueChannelOverlayInputTexCoordHandle);
            GLES20.glVertexAttribPointer(greenAndBlueChannelOverlayInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(greenAndBlueChannelOverlayPositionHandle);
            if (isVideo) {
                GLES20.glUniformMatrix4fv(greenAndBlueChannelOverlayMatrixHandle, 1, false, videoMatrix, 0);
            }
            GLES20.glVertexAttribPointer(greenAndBlueChannelOverlayPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            if (isVideo) {
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture);
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTextre[0]);
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            //blur pass
            GLES20.glUseProgram(skinBlurProgram.blurShaderProgram);
            GLES20.glUniform1i(skinBlurProgram.blurSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(skinBlurProgram.blurInputTexCoordHandle);
            GLES20.glVertexAttribPointer(skinBlurProgram.blurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(skinBlurProgram.blurPositionHandle);
            GLES20.glVertexAttribPointer(skinBlurProgram.blurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
            GLES20.glUniform1f(skinBlurProgram.blurWidthHandle, 0.0f);
            GLES20.glUniform1f(skinBlurProgram.blurHeightHandle, 1.0f / renderBufferHeight);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[3]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[3], 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
            GLES20.glUniform1f(skinBlurProgram.blurWidthHandle, 1.0f / renderBufferWidth);
            GLES20.glUniform1f(skinBlurProgram.blurHeightHandle, 0.0f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            //high pass
            GLES20.glUseProgram(highPassProgram);
            GLES20.glUniform1i(highPassSourceImageHandle, 0);
            GLES20.glUniform1i(highPassInputImageHandle, 1);
            GLES20.glEnableVertexAttribArray(highPassInputTexCoordHandle);
            GLES20.glVertexAttribPointer(highPassInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(highPassPositionHandle);
            GLES20.glVertexAttribPointer(highPassPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[3]);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            //boost pass
            GLES20.glUseProgram(boostProgram);
            GLES20.glUniform1i(boostSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(boostInputTexCoordHandle);
            GLES20.glVertexAttribPointer(boostInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(boostPositionHandle);
            GLES20.glVertexAttribPointer(boostPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[3]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[3], 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            needUpdateSkinTexture = false;
        }
        skinPassDrawn = true;
        hsvGenerated = false;
        GLES20.glUseProgram(compositeProgram);
        GLES20.glUniform1i(compositeSourceImageHandle, 0);
        GLES20.glUniform1i(compositeInputImageHandle, 1);
        GLES20.glUniform1i(compositeCurveImageHandle, 2);
        GLES20.glUniform1f(compositeMixtureHandle, delegate.getSoftenSkinValue());
        GLES20.glEnableVertexAttribArray(compositeInputTexCoordHandle);
        GLES20.glVertexAttribPointer(compositeInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
        GLES20.glEnableVertexAttribArray(compositePositionHandle);
        GLES20.glVertexAttribPointer(compositePositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);
        if (isVideo) {
            GLES20.glUniformMatrix4fv(compositeMatrixHandle, 1, false, videoMatrix, 0);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[1]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[1], 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        if (isVideo) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapTextre[0]);
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[3]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, toneCurve.getCurveTexture());
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        return true;
    }

    public boolean drawBlurPass() {
        int blurType = delegate != null ? delegate.getBlurType() : 0;
        if (isVideo || delegate == null || delegate.shouldShowOriginal() || blurType == 0) {
            return false;
        }
        if (needUpdateBlurTexture) {
            if (!blurTextureCreated) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[2]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                blurTextureCreated = true;
            }

            GLES20.glUseProgram(blurProgram.blurShaderProgram);
            GLES20.glUniform1i(blurProgram.blurSourceImageHandle, 0);
            GLES20.glEnableVertexAttribArray(blurProgram.blurInputTexCoordHandle);
            GLES20.glVertexAttribPointer(blurProgram.blurInputTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(blurProgram.blurPositionHandle);
            GLES20.glVertexAttribPointer(blurProgram.blurPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexInvertBuffer);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[1]);
            GLES20.glUniform1f(blurProgram.blurWidthHandle, 0.0f);
            GLES20.glUniform1f(blurProgram.blurHeightHandle, 1.0f / renderBufferHeight);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[2]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[2], 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTexture[0]);
            GLES20.glUniform1f(blurProgram.blurWidthHandle, 1.0f / renderBufferWidth);
            GLES20.glUniform1f(blurProgram.blurHeightHandle, 0.0f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            needUpdateBlurTexture = false;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderFrameBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture[0], 0);
        if (blurType == 1) {
            GLES20.glUseProgram(radialBlurShaderProgram);
            GLES20.glUniform1i(radialBlurSourceImageHandle, 0);
            GLES20.glUniform1i(radialBlurSourceImage2Handle, 1);
            GLES20.glUniform1f(radialBlurExcludeSizeHandle, delegate.getBlurExcludeSize());
            GLES20.glUniform1f(radialBlurExcludeBlurSizeHandle, delegate.getBlurExcludeBlurSize());
            Point blurExcludePoint = delegate.getBlurExcludePoint();
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
            GLES20.glUniform1f(linearBlurExcludeSizeHandle, delegate.getBlurExcludeSize());
            GLES20.glUniform1f(linearBlurExcludeBlurSizeHandle, delegate.getBlurExcludeBlurSize());
            GLES20.glUniform1f(linearBlurAngleHandle, delegate.getBlurAngle());
            Point blurExcludePoint = delegate.getBlurExcludePoint();
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

    public void onVideoFrameUpdate(float[] m) {
        videoMatrix = m;
        //videoFramesCount++;
        hsvGenerated = false;
        /*if (videoFramesCount >= 30) {
            hsvGenerated = false;
            videoFramesCount = 0;
        }*/
    }

    private Bitmap createBitmap(Bitmap bitmap, int orientation, float scale) {
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postRotate(orientation);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void loadTexture(Bitmap bitmap, int orientation, int w, int h) {
        renderBufferWidth = w;
        renderBufferHeight = h;

        if (renderFrameBuffer == null) {
            renderFrameBuffer = new int[4];
            GLES20.glGenFramebuffers(4, renderFrameBuffer, 0);
            GLES20.glGenTextures(4, renderTexture, 0);
        }

        if (bitmap != null && !bitmap.isRecycled()) {
            GLES20.glGenTextures(1, bitmapTextre, 0);

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

                bitmap = createBitmap(bitmap, orientation, scale);
            }

            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, bitmapTextre[0]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        }

        for (int a = 0; a < 2; a++) {
            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, renderTexture[a]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, renderBufferWidth, renderBufferHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        }
    }

    public FloatBuffer getTextureBuffer() {
        return textureBuffer;
    }

    public FloatBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    public FloatBuffer getVertexInvertBuffer() {
        return vertexInvertBuffer;
    }

    public int getRenderBufferWidth() {
        return renderBufferWidth;
    }

    public int getRenderBufferHeight() {
        return renderBufferHeight;
    }

    public int getRenderTexture(int index) {
        if (isVideo) {
            return renderTexture[index == 0 ? 1 : 0];
        }
        return renderTexture[index];
    }

    public int getRenderFrameBuffer() {
        return renderFrameBuffer != null ? renderFrameBuffer[isVideo ? 0 : 1] : 0;
    }

    public void requestUpdateBlurTexture() {
        needUpdateBlurTexture = true;
        needUpdateSkinTexture = true;
    }

    public static FilterShadersDelegate getFilterShadersDelegate(MediaController.SavedFilterState lastState) {
        return new FilterShadersDelegate() {
            @Override
            public boolean shouldShowOriginal() {
                return false;
            }

            @Override
            public float getSoftenSkinValue() {
                return (lastState.softenSkinValue / 100.0f);
            }

            @Override
            public float getShadowsValue() {
                return (lastState.shadowsValue * 0.55f + 100.0f) / 100.0f;
            }

            @Override
            public float getHighlightsValue() {
                return (lastState.highlightsValue * 0.75f + 100.0f) / 100.0f;
            }

            @Override
            public float getEnhanceValue() {
                return (lastState.enhanceValue / 100.0f);
            }

            @Override
            public float getExposureValue() {
                return (lastState.exposureValue / 100.0f);
            }

            @Override
            public float getContrastValue() {
                return (lastState.contrastValue / 100.0f) * 0.3f + 1;
            }

            @Override
            public float getWarmthValue() {
                return lastState.warmthValue / 100.0f;
            }

            @Override
            public float getVignetteValue() {
                return lastState.vignetteValue / 100.0f;
            }

            @Override
            public float getSharpenValue() {
                return 0.11f + lastState.sharpenValue / 100.0f * 0.6f;
            }

            @Override
            public float getGrainValue() {
                return lastState.grainValue / 100.0f * 0.04f;
            }

            @Override
            public float getFadeValue() {
                return lastState.fadeValue / 100.0f;
            }

            @Override
            public float getTintHighlightsIntensityValue() {
                float tintHighlightsIntensity = 50.0f;
                return lastState.tintHighlightsColor == 0 ? 0 : tintHighlightsIntensity / 100.0f;
            }

            @Override
            public float getTintShadowsIntensityValue() {
                float tintShadowsIntensity = 50.0f;
                return lastState.tintShadowsColor == 0 ? 0 : tintShadowsIntensity / 100.0f;
            }

            @Override
            public float getSaturationValue() {
                float parameterValue = (lastState.saturationValue / 100.0f);
                if (parameterValue > 0) {
                    parameterValue *= 1.05f;
                }
                return parameterValue + 1;
            }

            @Override
            public int getTintHighlightsColor() {
                return lastState.tintHighlightsColor;
            }

            @Override
            public int getTintShadowsColor() {
                return lastState.tintShadowsColor;
            }

            @Override
            public int getBlurType() {
                return lastState.blurType;
            }

            @Override
            public float getBlurExcludeSize() {
                return lastState.blurExcludeSize;
            }

            @Override
            public float getBlurExcludeBlurSize() {
                return lastState.blurExcludeBlurSize;
            }

            @Override
            public float getBlurAngle() {
                return lastState.blurAngle;
            }

            @Override
            public Point getBlurExcludePoint() {
                return lastState.blurExcludePoint;
            }

            @Override
            public boolean shouldDrawCurvesPass() {
                return !lastState.curvesToolValue.shouldBeSkipped();
            }

            @Override
            public ByteBuffer fillAndGetCurveBuffer() {
                lastState.curvesToolValue.fillBuffer();
                return lastState.curvesToolValue.curveBuffer;
            }
        };
    }
}
