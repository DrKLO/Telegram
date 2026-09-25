package org.telegram.utils.camera.roundvideo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLES11Ext;
import android.util.Size;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.RLottieNative;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class RoundVideoOverlayRenderer {

    private static final int BLUR_SIZE = 48;
    private static final float BACKGROUND_BLUR_RADIUS = 4f;
    private static final float SWITCH_TARGET_BACKGROUND_BLUR_FRACTION = 1.15f;
    private static final int BACKGROUND_GAUSSIAN_KERNEL_RADIUS = 4;
    private static final float BACKGROUND_GAUSSIAN_SIGMA = 1.75f;
    private static final int SWITCH_GAUSSIAN_KERNEL_RADIUS = 9;
    private static final float SWITCH_GAUSSIAN_SIGMA = 4f;
    private static final int SWITCH_OLD_MIP_SIZE = 512;
    private static final float SWITCH_OLD_BLUR_STEP_SCALE = 0.625f;
    private static final float SWITCH_OLD_MIP_LOD_OFFSET = 1.25f;
    private static final float SWITCH_OLD_SHARP_BLEND_RADIUS = 2f;
    private static final long TEMPORAL_RESET_NS = 100_000_000L;
    private static final double TEMPORAL_TIME_CONSTANT_NS = 280_000_000.0;
    private static final int LOGO_FRAME_COUNT = 27;

    private static final int TEXTURE_BLUR = 0;
    private static final int TEXTURE_BLUR_TEMP = 1;
    private static final int TEXTURE_MASK = 2;
    private static final int TEXTURE_WATERMARK = 3;
    private static final int TEXTURE_SWITCH_OLD = 4;
    private static final int TEXTURE_SWITCH_OLD_MIP = 5;
    private static final int TEXTURE_SWITCH_NEW = 6;
    private static final int TEXTURE_SWITCH_NEW_BLUR = 7;
    private static final int TEXTURE_BLUR_WORK = 8;
    private static final int TEXTURE_SWITCH_NEW_BLUR_TEMP = 9;

    private static final int FRAME_BUFFER_SWITCH_OLD = 2;
    private static final int FRAME_BUFFER_SWITCH_OLD_MIP = 3;
    private static final int FRAME_BUFFER_SWITCH_NEW = 4;
    private static final int FRAME_BUFFER_SWITCH_NEW_BLUR = 5;
    private static final int FRAME_BUFFER_BLUR_WORK = 6;
    private static final int FRAME_BUFFER_SWITCH_NEW_BLUR_TEMP = 7;

    private static final int VERTEX_NORMAL = 0;
    private static final int VERTEX_WATERMARK = 12;

    private static final int TEXTURE_NORMAL = 0;
    private static final int TEXTURE_BUFFER_WATERMARK = 8;

    private static WatermarkAtlas cachedWatermark360;
    private static WatermarkAtlas cachedWatermark480;

    private static final String TEXTURE_VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    vTextureCoord = aTextureCoord.xy;\n" +
            "}\n";

    private static final String WATERMARK_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(1.0, 1.0, 1.0, " +
            "texture2D(sTexture, vTextureCoord).a);\n" +
            "}\n";

    private static final String MIX_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "varying vec2 vScreenTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform sampler2D bTexture;\n" +
            "uniform sampler2D mTexture;\n" +
            "void main() {\n" +
            "    vec3 sharp = texture2D(sTexture, vTextureCoord).rgb;\n" +
            "    vec3 blurred = texture2D(bTexture, vScreenTextureCoord).rgb * 0.25;\n" +
            "    float mask = texture2D(mTexture, vScreenTextureCoord).a;\n" +
            "    gl_FragColor = vec4(mix(blurred, sharp, mask), 1.0);\n" +
            "}\n";

    private static final String CAMERA_VERTEX_SHADER =
            "uniform mat4 uTextureMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "varying vec2 vScreenTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    vTextureCoord = (uTextureMatrix * vec4(aTextureCoord.xy, 0.0, 1.0)).xy;\n" +
            "    vScreenTextureCoord = aPosition.xy * 0.5 + 0.5;\n" +
            "}\n";

    private static final String CAMERA_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private static final String COPY_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private static final String TEMPORAL_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "varying vec2 vScreenTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform sampler2D hTexture;\n" +
            "uniform vec2 sampleStepX;\n" +
            "uniform vec2 sampleStepY;\n" +
            "uniform float historyWeight;\n" +
            "void main() {\n" +
            "    vec3 current = texture2D(sTexture, vTextureCoord).rgb * 0.25;\n" +
            "    current += (texture2D(sTexture, vTextureCoord + sampleStepX).rgb\n" +
            "            + texture2D(sTexture, vTextureCoord - sampleStepX).rgb\n" +
            "            + texture2D(sTexture, vTextureCoord + sampleStepY).rgb\n" +
            "            + texture2D(sTexture, vTextureCoord - sampleStepY).rgb) * 0.125;\n" +
            "    current += (texture2D(sTexture, vTextureCoord + sampleStepX + sampleStepY).rgb\n" +
            "            + texture2D(sTexture, vTextureCoord + sampleStepX - sampleStepY).rgb\n" +
            "            + texture2D(sTexture, vTextureCoord - sampleStepX + sampleStepY).rgb\n" +
            "            + texture2D(sTexture, vTextureCoord - sampleStepX - sampleStepY).rgb) * 0.0625;\n" +
            "    vec3 history = texture2D(hTexture, vScreenTextureCoord).rgb;\n" +
            "    float difference = dot(abs(current - history), vec3(0.333333));\n" +
            "    float motion = smoothstep(0.035, 0.18, difference);\n" +
            "    float weight = mix(historyWeight, historyWeight * 0.65, motion);\n" +
            "    gl_FragColor = vec4(mix(current, history, weight), 1.0);\n" +
            "}\n";

    private static final String TRANSITION_FRAGMENT_SHADER =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "uniform sampler2D omTexture;\n" +
            "uniform sampler2D nTexture;\n" +
            "uniform sampler2D nbTexture;\n" +
            "uniform sampler2D oldBackground;\n" +
            "uniform sampler2D newBackground;\n" +
            "uniform sampler2D maskTexture;\n" +
            "uniform vec2 oldBlurStep;\n" +
            "uniform float oldMipBias;\n" +
            "uniform float oldBlurMix;\n" +
            "uniform float newBlur;\n" +
            "uniform float mixValue;\n" +
            "vec4 sampleOldFrame() {\n" +
            "    vec2 x = vec2(oldBlurStep.x, 0.0);\n" +
            "    vec2 diagonalUp = vec2(oldBlurStep.x * 0.5, oldBlurStep.y * 0.8660254);\n" +
            "    vec2 diagonalDown = vec2(oldBlurStep.x * 0.5, -oldBlurStep.y * 0.8660254);\n" +
            "    vec4 color = sampleOldMip(omTexture, vTextureCoord, oldMipBias) * 0.25;\n" +
            "    color += (sampleOldMip(omTexture, vTextureCoord + x, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord - x, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord + diagonalUp, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord - diagonalUp, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord + diagonalDown, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord - diagonalDown, oldMipBias)) * 0.125;\n" +
            "    return mix(texture2D(sTexture, vTextureCoord), color, oldBlurMix);\n" +
            "}\n" +
            "void main() {\n" +
            "    vec4 oldFrame = sampleOldFrame();\n" +
            "    vec4 newFrame = mix(texture2D(nTexture, vTextureCoord),\n" +
            "            texture2D(nbTexture, vTextureCoord), newBlur);\n" +
            "    vec3 camera = mix(oldFrame.rgb, newFrame.rgb, mixValue);\n" +
            "    vec3 background = mix(texture2D(oldBackground, vTextureCoord).rgb,\n" +
            "            texture2D(newBackground, vTextureCoord).rgb, mixValue) * 0.25;\n" +
            "    float mask = texture2D(maskTexture, vTextureCoord).a;\n" +
            "    gl_FragColor = vec4(mix(background, camera, mask), 1.0);\n" +
            "}\n";

    private static final String SIMPLE_TRANSITION_FRAGMENT_SHADER =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "uniform sampler2D omTexture;\n" +
            "uniform sampler2D nTexture;\n" +
            "uniform sampler2D nbTexture;\n" +
            "uniform vec2 oldBlurStep;\n" +
            "uniform float oldMipBias;\n" +
            "uniform float oldBlurMix;\n" +
            "uniform float newBlur;\n" +
            "uniform float mixValue;\n" +
            "vec4 sampleOldFrame() {\n" +
            "    vec2 x = vec2(oldBlurStep.x, 0.0);\n" +
            "    vec2 diagonalUp = vec2(oldBlurStep.x * 0.5, oldBlurStep.y * 0.8660254);\n" +
            "    vec2 diagonalDown = vec2(oldBlurStep.x * 0.5, -oldBlurStep.y * 0.8660254);\n" +
            "    vec4 color = sampleOldMip(omTexture, vTextureCoord, oldMipBias) * 0.25;\n" +
            "    color += (sampleOldMip(omTexture, vTextureCoord + x, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord - x, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord + diagonalUp, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord - diagonalUp, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord + diagonalDown, oldMipBias)\n" +
            "            + sampleOldMip(omTexture, vTextureCoord - diagonalDown, oldMipBias)) * 0.125;\n" +
            "    return mix(texture2D(sTexture, vTextureCoord), color, oldBlurMix);\n" +
            "}\n" +
            "void main() {\n" +
            "    vec4 oldFrame = sampleOldFrame();\n" +
            "    vec4 newFrame = mix(texture2D(nTexture, vTextureCoord),\n" +
            "            texture2D(nbTexture, vTextureCoord), newBlur);\n" +
            "    gl_FragColor = mix(oldFrame, newFrame, mixValue);\n" +
            "}\n";

    private static final String GAUSSIAN_BLUR_FRAGMENT_SHADER =
            createGaussianBlurFragmentShader(
                    BACKGROUND_GAUSSIAN_KERNEL_RADIUS,
                    BACKGROUND_GAUSSIAN_SIGMA
            );
    private static final String SWITCH_GAUSSIAN_BLUR_FRAGMENT_SHADER =
            createGaussianBlurFragmentShader(
                    SWITCH_GAUSSIAN_KERNEL_RADIUS,
                    SWITCH_GAUSSIAN_SIGMA
            );

    private final int outputSize;
    private final int switchNewBlurSize;
    private Size inputSize;
    private int sourceCropSize;
    private final boolean compositionEnabled;
    private final boolean explicitTextureLod;
    private final CameraProgram cameraProgram;
    private final Program copyProgram;
    private final TemporalProgram temporalProgram;
    private final MixProgram mixProgram;
    private final TransitionProgram transitionProgram;
    private final GaussianBlurProgram gaussianBlurProgram;
    private final GaussianBlurProgram switchGaussianBlurProgram;
    private final Program watermarkProgram;
    private final FloatBuffer vertexBuffer;
    private final FloatBuffer textureBuffer;
    private FloatBuffer cameraTextureBuffer;
    private FloatBuffer rotatedCameraTextureBuffer;
    private final int[] frameBuffers = new int[8];
    private final int[] textures = new int[10];

    private int logoFrame;
    private int historyReadTexture = TEXTURE_BLUR;
    private int historyWriteTexture = TEXTURE_BLUR_TEMP;
    private int switchOldBackgroundTexture = TEXTURE_BLUR;
    private int switchNewBackgroundTexture = TEXTURE_BLUR_TEMP;
    private long lastHistoryFrameNs;
    private boolean historyValid;
    private boolean switchNewFrameCaptured;
    private float newSwitchBlurRadius = -1f;

    RoundVideoOverlayRenderer(
            int outputSize,
            @NonNull Size inputSize,
            int sourceCropSize,
            boolean compositionEnabled
    ) {
        this.outputSize = outputSize;
        switchNewBlurSize = Math.max(1, outputSize / 2);
        this.inputSize = inputSize;
        this.sourceCropSize = sourceCropSize;
        this.compositionEnabled = compositionEnabled;
        explicitTextureLod = supportsExplicitTextureLod();
        cameraProgram = new CameraProgram(CAMERA_FRAGMENT_SHADER);
        copyProgram = new Program(
                TEXTURE_VERTEX_SHADER,
                COPY_FRAGMENT_SHADER
        );
        temporalProgram = compositionEnabled ? new TemporalProgram() : null;
        mixProgram = compositionEnabled ? new MixProgram() : null;
        transitionProgram = new TransitionProgram(
                compositionEnabled,
                explicitTextureLod
        );
        gaussianBlurProgram = new GaussianBlurProgram();
        switchGaussianBlurProgram = new GaussianBlurProgram(
                SWITCH_GAUSSIAN_BLUR_FRAGMENT_SHADER
        );
        watermarkProgram = compositionEnabled
                ? new Program(TEXTURE_VERTEX_SHADER, WATERMARK_FRAGMENT_SHADER)
                : null;

        float[] textureData = new float[TEXTURE_BUFFER_WATERMARK + LOGO_FRAME_COUNT * 24];
        setTextureCoordinates(textureData, TEXTURE_NORMAL, 0f, 1f, 1f, 0f);

        float[] vertexData = new float[48];
        setVertexCoordinates(vertexData, VERTEX_NORMAL, -1f, 1f, 1f, -1f);
        cameraTextureBuffer = createCameraTextureBuffer(inputSize, sourceCropSize, false);
        rotatedCameraTextureBuffer = createCameraTextureBuffer(inputSize, sourceCropSize, true);

        GLES20.glGenTextures(textures.length, textures, 0);
        if (compositionEnabled) {
            createColorTexture(TEXTURE_BLUR, BLUR_SIZE, BLUR_SIZE, GLES20.GL_LINEAR);
            createColorTexture(TEXTURE_BLUR_TEMP, BLUR_SIZE, BLUR_SIZE, GLES20.GL_LINEAR);
            createMaskTexture();
            createWatermarkAtlas(vertexData, textureData);
            createColorTexture(TEXTURE_BLUR_WORK, BLUR_SIZE, BLUR_SIZE, GLES20.GL_LINEAR);
        }
        createColorTexture(
                TEXTURE_SWITCH_OLD,
                outputSize,
                outputSize,
                GLES20.GL_LINEAR
        );
        createColorTexture(
                TEXTURE_SWITCH_OLD_MIP,
                SWITCH_OLD_MIP_SIZE,
                SWITCH_OLD_MIP_SIZE,
                GLES20.GL_LINEAR
        );
        createColorTexture(TEXTURE_SWITCH_NEW, outputSize, outputSize, GLES20.GL_LINEAR);
        createColorTexture(
                TEXTURE_SWITCH_NEW_BLUR,
                switchNewBlurSize,
                switchNewBlurSize,
                GLES20.GL_LINEAR
        );
        createColorTexture(
                TEXTURE_SWITCH_NEW_BLUR_TEMP,
                switchNewBlurSize,
                switchNewBlurSize,
                GLES20.GL_LINEAR
        );
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glGenFramebuffers(frameBuffers.length, frameBuffers, 0);
        if (compositionEnabled) {
            attachFrameBuffer(0, TEXTURE_BLUR);
            attachFrameBuffer(1, TEXTURE_BLUR_TEMP);
            attachFrameBuffer(FRAME_BUFFER_BLUR_WORK, TEXTURE_BLUR_WORK);
        }
        attachFrameBuffer(FRAME_BUFFER_SWITCH_OLD, TEXTURE_SWITCH_OLD);
        attachFrameBuffer(FRAME_BUFFER_SWITCH_OLD_MIP, TEXTURE_SWITCH_OLD_MIP);
        attachFrameBuffer(FRAME_BUFFER_SWITCH_NEW, TEXTURE_SWITCH_NEW);
        attachFrameBuffer(FRAME_BUFFER_SWITCH_NEW_BLUR, TEXTURE_SWITCH_NEW_BLUR);
        attachFrameBuffer(
                FRAME_BUFFER_SWITCH_NEW_BLUR_TEMP,
                TEXTURE_SWITCH_NEW_BLUR_TEMP
        );
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        vertexBuffer = createBuffer(vertexData);
        textureBuffer = createBuffer(textureData);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);
    }

    void render(
            int cameraTexture,
            @NonNull float[] textureMatrix,
            long frameTimeNs,
            int outputWidth,
            int outputHeight
    ) {
        GLES20.glDisable(GLES20.GL_BLEND);
        if (compositionEnabled) {
            renderTemporalBackground(
                    cameraTexture,
                    textureMatrix,
                    historyWriteTexture,
                    getHistoryWeight(frameTimeNs)
            );
            renderBackgroundBlur(historyWriteTexture);
            int previousReadTexture = historyReadTexture;
            historyReadTexture = historyWriteTexture;
            historyWriteTexture = previousReadTexture;
            historyValid = true;
            lastHistoryFrameNs = frameTimeNs;
            renderMix(cameraTexture, textureMatrix, historyReadTexture, outputWidth, outputHeight);
        } else {
            renderRawCameraToOutput(cameraTexture, textureMatrix, outputWidth, outputHeight);
        }
        renderWatermarks();
    }

    void updateInputConfiguration(@NonNull Size inputSize, int sourceCropSize) {
        this.inputSize = inputSize;
        this.sourceCropSize = sourceCropSize;
        cameraTextureBuffer = createCameraTextureBuffer(inputSize, sourceCropSize, false);
        rotatedCameraTextureBuffer = createCameraTextureBuffer(inputSize, sourceCropSize, true);
        historyValid = false;
        lastHistoryFrameNs = 0;
    }

    void captureSwitchFrame(
            int cameraTexture,
            @NonNull float[] textureMatrix,
            boolean newFrame
    ) {
        GLES20.glDisable(GLES20.GL_BLEND);
        if (!newFrame) {
            setTextureMinFilter(TEXTURE_SWITCH_OLD_MIP, GLES20.GL_LINEAR);
        }
        renderRawCamera(
                cameraTexture,
                textureMatrix,
                newFrame ? FRAME_BUFFER_SWITCH_NEW : FRAME_BUFFER_SWITCH_OLD,
                outputSize,
                outputSize
        );
        if (newFrame) {
            switchNewBackgroundTexture = historyWriteTexture;
            if (compositionEnabled) {
                renderTemporalBackground(cameraTexture, textureMatrix, switchNewBackgroundTexture, 0f);
                renderBackgroundBlur(switchNewBackgroundTexture);
            }
            newSwitchBlurRadius = -1f;
            switchNewFrameCaptured = true;
        } else {
            renderTextureCopy(
                    TEXTURE_SWITCH_OLD,
                    FRAME_BUFFER_SWITCH_OLD_MIP,
                    SWITCH_OLD_MIP_SIZE,
                    SWITCH_OLD_MIP_SIZE
            );
            generateOldSwitchMipmaps();
            switchOldBackgroundTexture = historyReadTexture;
            switchNewBackgroundTexture = historyWriteTexture;
            switchNewFrameCaptured = false;
            newSwitchBlurRadius = -1f;
        }
    }

    float getSwitchTargetBlurRadiusPx() {
        return BACKGROUND_BLUR_RADIUS * outputSize / BLUR_SIZE
                * SWITCH_TARGET_BACKGROUND_BLUR_FRACTION;
    }

    private float getSwitchNewMaximumBlurRadiusPx() {
        return SWITCH_GAUSSIAN_KERNEL_RADIUS;
    }

    void renderSwitch(
            float oldBlurRadius,
            float newBlur,
            float mix,
            int outputWidth,
            int outputHeight
    ) {
        if (switchNewFrameCaptured) {
            renderNewSwitchBlur(Math.min(oldBlurRadius, getSwitchNewMaximumBlurRadiusPx()));
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, outputWidth, outputHeight);
        GLES20.glDisable(GLES20.GL_BLEND);
        bindProgram(transitionProgram, TEXTURE_NORMAL);
        bindTexture(0, TEXTURE_SWITCH_OLD, transitionProgram.textureHandle);
        bindTexture(1, TEXTURE_SWITCH_OLD_MIP, transitionProgram.oldMipTextureHandle);
        bindTexture(2, TEXTURE_SWITCH_NEW, transitionProgram.newTextureHandle);
        bindTexture(3, TEXTURE_SWITCH_NEW_BLUR, transitionProgram.newBlurTextureHandle);
        if (compositionEnabled) {
            bindTexture(4, switchOldBackgroundTexture, transitionProgram.oldBackgroundHandle);
            bindTexture(5, switchNewBackgroundTexture, transitionProgram.newBackgroundHandle);
            bindTexture(6, TEXTURE_MASK, transitionProgram.maskTextureHandle);
        }
        float oldBlurStep = Math.max(0f, oldBlurRadius)
                * SWITCH_OLD_BLUR_STEP_SCALE / outputSize;
        GLES20.glUniform2f(
                transitionProgram.oldBlurStepHandle,
                oldBlurStep,
                oldBlurStep
        );
        GLES20.glUniform1f(
                transitionProgram.oldMipBiasHandle,
                getSwitchOldMipParameter(oldBlurRadius)
        );
        GLES20.glUniform1f(
                transitionProgram.oldBlurMixHandle,
                clamp01(oldBlurRadius / SWITCH_OLD_SHARP_BLEND_RADIUS)
        );
        GLES20.glUniform1f(transitionProgram.newBlurHandle, newBlur);
        GLES20.glUniform1f(transitionProgram.mixHandle, mix);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(transitionProgram);
        renderWatermarks();
    }

    void finishCameraSwitch(long frameTimeNs) {
        if (!switchNewFrameCaptured) {
            return;
        }
        if (compositionEnabled) {
            historyReadTexture = switchNewBackgroundTexture;
            historyWriteTexture = switchOldBackgroundTexture;
            historyValid = true;
            lastHistoryFrameNs = frameTimeNs;
        }
        switchNewFrameCaptured = false;
    }

    void release() {
        cameraProgram.release();
        copyProgram.release();
        if (temporalProgram != null) temporalProgram.release();
        if (mixProgram != null) mixProgram.release();
        transitionProgram.release();
        gaussianBlurProgram.release();
        switchGaussianBlurProgram.release();
        if (watermarkProgram != null) watermarkProgram.release();
        GLES20.glDeleteTextures(textures.length, textures, 0);
        GLES20.glDeleteFramebuffers(frameBuffers.length, frameBuffers, 0);
    }

    private void renderTemporalBackground(
            int cameraTexture,
            float[] textureMatrix,
            int targetTexture,
            float historyWeight
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[targetTexture]);
        GLES20.glViewport(0, 0, BLUR_SIZE, BLUR_SIZE);
        bindCameraProgram(temporalProgram, textureMatrix);
        bindExternalTexture(0, cameraTexture, temporalProgram.textureHandle);
        bindTexture(1, historyReadTexture, temporalProgram.historyTextureHandle);

        boolean swapAxes = swapsTextureAxes(textureMatrix);
        int cropSize = getCropSize();
        float textureWidth = swapAxes ? inputSize.getHeight() : inputSize.getWidth();
        float textureHeight = swapAxes ? inputSize.getWidth() : inputSize.getHeight();
        float stepU = cropSize / textureWidth / BLUR_SIZE * 0.45f;
        float stepV = cropSize / textureHeight / BLUR_SIZE * 0.45f;
        GLES20.glUniform2f(
                temporalProgram.sampleStepXHandle,
                textureMatrix[0] * stepU,
                textureMatrix[1] * stepU
        );
        GLES20.glUniform2f(
                temporalProgram.sampleStepYHandle,
                textureMatrix[4] * stepV,
                textureMatrix[5] * stepV
        );
        GLES20.glUniform1f(temporalProgram.historyWeightHandle, historyWeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(temporalProgram);
    }

    private void renderMix(
            int cameraTexture,
            float[] textureMatrix,
            int backgroundTexture,
            int outputWidth,
            int outputHeight
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, outputWidth, outputHeight);
        bindCameraProgram(mixProgram, textureMatrix);
        bindExternalTexture(0, cameraTexture, mixProgram.textureHandle);
        bindTexture(1, backgroundTexture, mixProgram.blurredTextureHandle);
        bindTexture(2, TEXTURE_MASK, mixProgram.maskTextureHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(mixProgram);
    }

    private void renderWatermarks() {
        if (!compositionEnabled || watermarkProgram == null) return;
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int frame = logoFrame++ % LOGO_FRAME_COUNT;
        bindProgram(
                watermarkProgram,
                TEXTURE_BUFFER_WATERMARK + frame * 24,
                VERTEX_WATERMARK
        );
        bindTexture(0, TEXTURE_WATERMARK, watermarkProgram.textureHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 12);
        unbindProgram(watermarkProgram);

        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void renderRawCamera(
            int cameraTexture,
            float[] textureMatrix,
            int targetFrameBuffer,
            int width,
            int height
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[targetFrameBuffer]);
        GLES20.glViewport(0, 0, width, height);
        bindCameraProgram(cameraProgram, textureMatrix);
        bindExternalTexture(0, cameraTexture, cameraProgram.textureHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(cameraProgram);
    }

    private void renderRawCameraToOutput(
            int cameraTexture,
            float[] textureMatrix,
            int width,
            int height
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        bindCameraProgram(cameraProgram, textureMatrix);
        bindExternalTexture(0, cameraTexture, cameraProgram.textureHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(cameraProgram);
    }

    private void renderTextureCopy(
            int sourceTexture,
            int targetFrameBuffer,
            int width,
            int height
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[targetFrameBuffer]);
        GLES20.glViewport(0, 0, width, height);
        bindProgram(copyProgram, TEXTURE_NORMAL);
        bindTexture(0, sourceTexture, copyProgram.textureHandle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(copyProgram);
    }

    private void renderNewSwitchBlur(float radiusPixels) {
        if (Math.abs(newSwitchBlurRadius - radiusPixels) < 0.2f) {
            return;
        }
        float offset = radiusPixels
                / (SWITCH_GAUSSIAN_KERNEL_RADIUS * outputSize);
        renderSwitchGaussianBlurPass(
                TEXTURE_SWITCH_NEW,
                FRAME_BUFFER_SWITCH_NEW_BLUR_TEMP,
                switchNewBlurSize,
                switchNewBlurSize,
                offset,
                0f
        );
        renderSwitchGaussianBlurPass(
                TEXTURE_SWITCH_NEW_BLUR_TEMP,
                FRAME_BUFFER_SWITCH_NEW_BLUR,
                switchNewBlurSize,
                switchNewBlurSize,
                0f,
                offset
        );
        newSwitchBlurRadius = radiusPixels;
    }

    private void renderBackgroundBlur(int targetTexture) {
        float offset = BACKGROUND_BLUR_RADIUS
                / (BACKGROUND_GAUSSIAN_KERNEL_RADIUS * BLUR_SIZE);
        renderGaussianBlurPass(
                targetTexture,
                FRAME_BUFFER_BLUR_WORK,
                BLUR_SIZE,
                BLUR_SIZE,
                offset,
                0f
        );
        renderGaussianBlurPass(
                TEXTURE_BLUR_WORK,
                targetTexture,
                BLUR_SIZE,
                BLUR_SIZE,
                0f,
                offset
        );
    }

    private void renderGaussianBlurPass(
            int sourceTexture,
            int targetFrameBuffer,
            int width,
            int height,
            float offsetX,
            float offsetY
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[targetFrameBuffer]);
        GLES20.glViewport(0, 0, width, height);
        bindProgram(gaussianBlurProgram, TEXTURE_NORMAL);
        bindTexture(0, sourceTexture, gaussianBlurProgram.textureHandle);
        GLES20.glUniform2f(gaussianBlurProgram.offsetHandle, offsetX, offsetY);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(gaussianBlurProgram);
    }

    private void renderSwitchGaussianBlurPass(
            int sourceTexture,
            int targetFrameBuffer,
            int width,
            int height,
            float offsetX,
            float offsetY
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[targetFrameBuffer]);
        GLES20.glViewport(0, 0, width, height);
        bindProgram(switchGaussianBlurProgram, TEXTURE_NORMAL);
        bindTexture(0, sourceTexture, switchGaussianBlurProgram.textureHandle);
        GLES20.glUniform2f(switchGaussianBlurProgram.offsetHandle, offsetX, offsetY);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindProgram(switchGaussianBlurProgram);
    }

    private float getHistoryWeight(long frameTimeNs) {
        if (!historyValid || lastHistoryFrameNs == 0) {
            return 0f;
        }
        long deltaNs = frameTimeNs - lastHistoryFrameNs;
        if (deltaNs <= 0 || deltaNs > TEMPORAL_RESET_NS) {
            return 0f;
        }
        return (float) Math.exp(-deltaNs / TEMPORAL_TIME_CONSTANT_NS);
    }

    private void generateOldSwitchMipmaps() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[TEXTURE_SWITCH_OLD_MIP]);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR_MIPMAP_LINEAR
        );
    }

    private void setTextureMinFilter(int textureIndex, int filter) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[textureIndex]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
    }

    private float getSwitchOldMipParameter(float radiusPixels) {
        float sampleStepTexels = Math.max(1f, radiusPixels)
                * SWITCH_OLD_BLUR_STEP_SCALE
                * SWITCH_OLD_MIP_SIZE / outputSize;
        float desiredLod = Math.min(
                8f,
                (float) (Math.log(sampleStepTexels) / Math.log(2.0))
        ) + SWITCH_OLD_MIP_LOD_OFFSET;
        if (explicitTextureLod) return Math.max(0f, desiredLod);
        float implicitLod = (float) (
                Math.log(SWITCH_OLD_MIP_SIZE / (double) outputSize) / Math.log(2.0)
        );
        return Math.max(0f, desiredLod - implicitLod);
    }

    private static boolean supportsExplicitTextureLod() {
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        return extensions != null && extensions.contains("GL_EXT_shader_texture_lod");
    }

    private static String createTransitionFragmentShader(
            boolean compositionEnabled,
            boolean explicitTextureLod
    ) {
        String source = compositionEnabled
                ? TRANSITION_FRAGMENT_SHADER
                : SIMPLE_TRANSITION_FRAGMENT_SHADER;
        if (explicitTextureLod) {
            return "#extension GL_EXT_shader_texture_lod : require\n"
                    + source.replace("sampleOldMip", "texture2DLodEXT");
        }
        return source.replace("sampleOldMip", "texture2D");
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void bindProgram(Program program, int textureOffset) {
        bindProgram(program, textureOffset, VERTEX_NORMAL);
    }

    private void bindCameraProgram(CameraProgram program, float[] textureMatrix) {
        GLES20.glUseProgram(program.program);
        vertexBuffer.position(VERTEX_NORMAL);
        GLES20.glVertexAttribPointer(
                program.positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                12,
                vertexBuffer
        );
        FloatBuffer selectedTextureBuffer = swapsTextureAxes(textureMatrix)
                ? rotatedCameraTextureBuffer
                : cameraTextureBuffer;
        selectedTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(
                program.textureCoordinateHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                8,
                selectedTextureBuffer
        );
        GLES20.glUniformMatrix4fv(program.textureMatrixHandle, 1, false, textureMatrix, 0);
    }

    private void bindProgram(Program program, int textureOffset, int vertexOffset) {
        GLES20.glUseProgram(program.program);
        vertexBuffer.position(vertexOffset);
        GLES20.glVertexAttribPointer(
                program.positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                12,
                vertexBuffer
        );
        textureBuffer.position(textureOffset);
        GLES20.glVertexAttribPointer(
                program.textureCoordinateHandle,
                2,
                GLES20.GL_FLOAT,
                false,
                8,
                textureBuffer
        );
    }

    private static void unbindProgram(Program program) {
    }

    private void bindTexture(int unit, int textureIndex, int uniformHandle) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[textureIndex]);
    }

    private static void bindExternalTexture(int unit, int texture, int uniformHandle) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
    }

    private void attach(int textureIndex) {
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textures[textureIndex],
                0
        );
    }

    private void attachFrameBuffer(int frameBufferIndex, int textureIndex) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[frameBufferIndex]);
        attach(textureIndex);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete framebuffer: 0x"
                    + Integer.toHexString(status));
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void createColorTexture(int textureIndex, int width, int height, int filter) {
        configureTexture(textureIndex, filter);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
        );
    }

    private void createMaskTexture() {
        configureTexture(TEXTURE_MASK, GLES20.GL_LINEAR);
        ByteBuffer mask = ByteBuffer.allocateDirect(outputSize * outputSize);
        float center = outputSize * 0.5f;
        float radius = center + 2f;
        for (int y = 0; y < outputSize; y++) {
            float dy = y + 0.5f - center;
            for (int x = 0; x < outputSize; x++) {
                float dx = x + 0.5f - center;
                float coverage = radius + 0.5f - (float) Math.sqrt(dx * dx + dy * dy);
                mask.put((byte) Math.round(Math.max(0f, Math.min(1f, coverage)) * 255f));
            }
        }
        mask.position(0);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_ALPHA,
                outputSize,
                outputSize,
                0,
                GLES20.GL_ALPHA,
                GLES20.GL_UNSIGNED_BYTE,
                mask
        );
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
    }

    private void createWatermarkAtlas(float[] vertexData, float[] textureData) {
        configureTexture(TEXTURE_WATERMARK, GLES20.GL_LINEAR);
        WatermarkAtlas atlas = getWatermarkAtlas(outputSize);
        for (int frame = 0; frame < LOGO_FRAME_COUNT; frame++) {
            int x = frame % 8;
            int y = frame / 8;
            int textureOffset = TEXTURE_BUFFER_WATERMARK + frame * 24;
            setTriangleTextureCoordinates(
                    textureData,
                    textureOffset,
                    0f,
                    atlas.logoAtlasHeight / (float) atlas.height,
                    atlas.textSize / (float) atlas.width,
                    1f
            );
            setTriangleTextureCoordinates(
                    textureData,
                    textureOffset + 12,
                    atlas.frameSize * x / (float) atlas.width,
                    atlas.frameSize * y / (float) atlas.height,
                    atlas.frameSize * (x + 1) / (float) atlas.width,
                    atlas.frameSize * (y + 1) / (float) atlas.height
            );
        }

        float textScale = atlas.textSize / (float) outputSize;
        setTriangleVertexCoordinates(
                vertexData,
                VERTEX_WATERMARK,
                1f - textScale * 2f,
                -1f,
                1f,
                -1f + textScale * 2f
        );
        float logoScale = atlas.frameSize / (float) outputSize;
        setTriangleVertexCoordinates(
                vertexData,
                VERTEX_WATERMARK + 18,
                -1f,
                -1f,
                -1f + logoScale * 2f,
                -1f + logoScale * 2f
        );
        ByteBuffer pixels = atlas.pixels.duplicate();
        pixels.position(0);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_ALPHA,
                atlas.width,
                atlas.height,
                0,
                GLES20.GL_ALPHA,
                GLES20.GL_UNSIGNED_BYTE,
                pixels
        );
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
    }

    private static synchronized WatermarkAtlas getWatermarkAtlas(int outputSize) {
        WatermarkAtlas cached = outputSize == 360
                ? cachedWatermark360
                : outputSize == 480 ? cachedWatermark480 : null;
        if (cached != null) return cached;

        int textSize = Math.round(outputSize * 372f / 1536f);
        int logoSize = Math.round(outputSize * 0.2f);
        int logoOffset = Math.round(outputSize * 28f / 1536f);
        int frameSize = logoSize - logoOffset * 2;
        int logoAtlasWidth = frameSize * 8;
        int logoAtlasHeight = frameSize * 4;
        int atlasWidth = Math.max(logoAtlasWidth, textSize);
        int atlasHeight = logoAtlasHeight + textSize;

        RLottieNative lottie = RLottieNative.createFromRawJson(
                AndroidUtilities.readRes(R.raw.plane_logo_plain)
        );
        if (lottie == null) throw new IllegalStateException("Unable to load watermark animation");
        Bitmap frameBitmap = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888);
        Bitmap atlasBitmap = Bitmap.createBitmap(atlasWidth, atlasHeight, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(atlasBitmap);
        for (int frame = 0; frame < LOGO_FRAME_COUNT; frame++) {
            int x = frame % 8;
            int y = frame / 8;
            lottie.getFrame(frame * 2, frameBitmap, true);
            canvas.drawBitmap(frameBitmap, frameSize * x - logoOffset, frameSize * y - logoOffset, null);
        }

        Bitmap text = AndroidUtilities.getBitmapFromRaw(R.raw.round_blur_overlay_text);
        if (text != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(text, textSize, textSize, true);
            Bitmap alpha = scaled.extractAlpha();
            canvas.drawBitmap(alpha, 0f, logoAtlasHeight, null);
            alpha.recycle();
            scaled.recycle();
            text.recycle();
        }

        if (atlasBitmap.getRowBytes() != atlasWidth) {
            atlasBitmap.recycle();
            frameBitmap.recycle();
            lottie.recycle();
            throw new IllegalStateException("Unexpected watermark atlas stride");
        }
        ByteBuffer pixels = ByteBuffer.allocateDirect(atlasWidth * atlasHeight);
        atlasBitmap.copyPixelsToBuffer(pixels);
        pixels.position(0);
        WatermarkAtlas result = new WatermarkAtlas(
                atlasWidth,
                atlasHeight,
                textSize,
                frameSize,
                logoAtlasHeight,
                pixels
        );
        atlasBitmap.recycle();
        frameBitmap.recycle();
        lottie.recycle();
        if (outputSize == 360) cachedWatermark360 = result;
        else if (outputSize == 480) cachedWatermark480 = result;
        return result;
    }

    private void configureTexture(int textureIndex, int filter) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[textureIndex]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );
    }

    private static FloatBuffer createBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private static FloatBuffer createCameraTextureBuffer(
            Size inputSize,
            int sourceCropSize,
            boolean swapAxes
    ) {
        int cropSize = Math.min(
                sourceCropSize,
                Math.min(inputSize.getWidth(), inputSize.getHeight())
        );
        int textureWidth = swapAxes ? inputSize.getHeight() : inputSize.getWidth();
        int textureHeight = swapAxes ? inputSize.getWidth() : inputSize.getHeight();
        float halfWidth = cropSize / (2f * textureWidth);
        float halfHeight = cropSize / (2f * textureHeight);
        return createBuffer(new float[]{
                0.5f - halfWidth, 0.5f - halfHeight,
                0.5f + halfWidth, 0.5f - halfHeight,
                0.5f - halfWidth, 0.5f + halfHeight,
                0.5f + halfWidth, 0.5f + halfHeight
        });
    }

    private int getCropSize() {
        return Math.min(
                sourceCropSize,
                Math.min(inputSize.getWidth(), inputSize.getHeight())
        );
    }

    private static boolean swapsTextureAxes(float[] matrix) {
        return Math.abs(matrix[1]) > Math.abs(matrix[0]);
    }

    private static void setVertexCoordinates(
            float[] buffer,
            int offset,
            float left,
            float top,
            float right,
            float bottom
    ) {
        buffer[offset] = left;
        buffer[offset + 1] = bottom;
        buffer[offset + 2] = 0f;
        buffer[offset + 3] = right;
        buffer[offset + 4] = bottom;
        buffer[offset + 5] = 0f;
        buffer[offset + 6] = left;
        buffer[offset + 7] = top;
        buffer[offset + 8] = 0f;
        buffer[offset + 9] = right;
        buffer[offset + 10] = top;
        buffer[offset + 11] = 0f;
    }

    private static void setTextureCoordinates(
            float[] buffer,
            int offset,
            float left,
            float top,
            float right,
            float bottom
    ) {
        buffer[offset] = left;
        buffer[offset + 1] = bottom;
        buffer[offset + 2] = right;
        buffer[offset + 3] = bottom;
        buffer[offset + 4] = left;
        buffer[offset + 5] = top;
        buffer[offset + 6] = right;
        buffer[offset + 7] = top;
    }

    private static void setTriangleVertexCoordinates(
            float[] buffer,
            int offset,
            float left,
            float bottom,
            float right,
            float top
    ) {
        setVertex(buffer, offset, left, bottom);
        setVertex(buffer, offset + 3, right, bottom);
        setVertex(buffer, offset + 6, left, top);
        setVertex(buffer, offset + 9, left, top);
        setVertex(buffer, offset + 12, right, bottom);
        setVertex(buffer, offset + 15, right, top);
    }

    private static void setVertex(float[] buffer, int offset, float x, float y) {
        buffer[offset] = x;
        buffer[offset + 1] = y;
        buffer[offset + 2] = 0f;
    }

    private static void setTriangleTextureCoordinates(
            float[] buffer,
            int offset,
            float left,
            float top,
            float right,
            float bottom
    ) {
        setTextureCoordinate(buffer, offset, left, bottom);
        setTextureCoordinate(buffer, offset + 2, right, bottom);
        setTextureCoordinate(buffer, offset + 4, left, top);
        setTextureCoordinate(buffer, offset + 6, left, top);
        setTextureCoordinate(buffer, offset + 8, right, bottom);
        setTextureCoordinate(buffer, offset + 10, right, top);
    }

    private static void setTextureCoordinate(float[] buffer, int offset, float x, float y) {
        buffer[offset] = x;
        buffer[offset + 1] = y;
    }

    private static String createGaussianBlurFragmentShader(int radius, float sigma) {
        double weightSum = 0.0;
        for (int i = -radius; i <= radius; i++) {
            weightSum += Math.exp(-(i * i) / (2.0 * sigma * sigma));
        }
        StringBuilder shader = new StringBuilder(4096);
        shader.append("precision mediump float;\n")
                .append("varying vec2 vTextureCoord;\n")
                .append("uniform sampler2D sTexture;\n")
                .append("uniform vec2 texOffset;\n")
                .append("void main() {\n");
        float centerWeight = (float) (1.0 / weightSum);
        shader.append("    vec3 color = texture2D(sTexture, vTextureCoord).rgb * ")
                .append(Float.toString(centerWeight))
                .append(";\n");
        for (int i = 1; i <= radius; i += 2) {
            float firstWeight = (float) (
                    Math.exp(-(i * i) / (2.0 * sigma * sigma)) / weightSum
            );
            int second = i + 1;
            float secondWeight = second <= radius
                    ? (float) (Math.exp(-(second * second) / (2.0 * sigma * sigma)) / weightSum)
                    : 0f;
            float combinedWeight = firstWeight + secondWeight;
            float sampleOffset = secondWeight == 0f
                    ? i
                    : (i * firstWeight + second * secondWeight) / combinedWeight;
            shader.append("    color += (texture2D(sTexture, vTextureCoord + texOffset * ")
                    .append(Float.toString(sampleOffset))
                    .append(").rgb + texture2D(sTexture, vTextureCoord - texOffset * ")
                    .append(Float.toString(sampleOffset))
                    .append(").rgb) * ")
                    .append(Float.toString(combinedWeight))
                    .append(";\n");
        }
        shader.append("    gl_FragColor = vec4(color, 1.0);\n")
                .append("}\n");
        return shader.toString();
    }

    private static class GaussianBlurProgram extends Program {
        final int offsetHandle;

        GaussianBlurProgram() {
            super(TEXTURE_VERTEX_SHADER, GAUSSIAN_BLUR_FRAGMENT_SHADER);
            offsetHandle = GLES20.glGetUniformLocation(program, "texOffset");
        }

        GaussianBlurProgram(@NonNull String fragmentShader) {
            super(TEXTURE_VERTEX_SHADER, fragmentShader);
            offsetHandle = GLES20.glGetUniformLocation(program, "texOffset");
        }
    }

    private static final class WatermarkAtlas {
        final int width;
        final int height;
        final int textSize;
        final int frameSize;
        final int logoAtlasHeight;
        final ByteBuffer pixels;

        WatermarkAtlas(
                int width,
                int height,
                int textSize,
                int frameSize,
                int logoAtlasHeight,
                @NonNull ByteBuffer pixels
        ) {
            this.width = width;
            this.height = height;
            this.textSize = textSize;
            this.frameSize = frameSize;
            this.logoAtlasHeight = logoAtlasHeight;
            this.pixels = pixels;
        }
    }

    private static class CameraProgram extends Program {
        final int textureMatrixHandle;

        CameraProgram(String fragmentShaderSource) {
            super(CAMERA_VERTEX_SHADER, fragmentShaderSource);
            textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTextureMatrix");
        }
    }

    private static class TemporalProgram extends CameraProgram {
        final int historyTextureHandle;
        final int sampleStepXHandle;
        final int sampleStepYHandle;
        final int historyWeightHandle;

        TemporalProgram() {
            super(TEMPORAL_FRAGMENT_SHADER);
            historyTextureHandle = GLES20.glGetUniformLocation(program, "hTexture");
            sampleStepXHandle = GLES20.glGetUniformLocation(program, "sampleStepX");
            sampleStepYHandle = GLES20.glGetUniformLocation(program, "sampleStepY");
            historyWeightHandle = GLES20.glGetUniformLocation(program, "historyWeight");
            GLES20.glUseProgram(program);
            GLES20.glUniform1i(historyTextureHandle, 1);
        }
    }

    private static class MixProgram extends CameraProgram {
        final int blurredTextureHandle;
        final int maskTextureHandle;

        MixProgram() {
            super(MIX_FRAGMENT_SHADER);
            blurredTextureHandle = GLES20.glGetUniformLocation(program, "bTexture");
            maskTextureHandle = GLES20.glGetUniformLocation(program, "mTexture");
            GLES20.glUseProgram(program);
            GLES20.glUniform1i(blurredTextureHandle, 1);
            GLES20.glUniform1i(maskTextureHandle, 2);
        }
    }

    private static class TransitionProgram extends Program {
        final int oldMipTextureHandle;
        final int newTextureHandle;
        final int newBlurTextureHandle;
        final int oldBackgroundHandle;
        final int newBackgroundHandle;
        final int maskTextureHandle;
        final int oldBlurStepHandle;
        final int oldMipBiasHandle;
        final int oldBlurMixHandle;
        final int newBlurHandle;
        final int mixHandle;

        TransitionProgram(boolean compositionEnabled, boolean explicitTextureLod) {
            super(
                    TEXTURE_VERTEX_SHADER,
                    createTransitionFragmentShader(compositionEnabled, explicitTextureLod)
            );
            oldMipTextureHandle = GLES20.glGetUniformLocation(program, "omTexture");
            newTextureHandle = GLES20.glGetUniformLocation(program, "nTexture");
            newBlurTextureHandle = GLES20.glGetUniformLocation(program, "nbTexture");
            oldBackgroundHandle = GLES20.glGetUniformLocation(program, "oldBackground");
            newBackgroundHandle = GLES20.glGetUniformLocation(program, "newBackground");
            maskTextureHandle = GLES20.glGetUniformLocation(program, "maskTexture");
            oldBlurStepHandle = GLES20.glGetUniformLocation(program, "oldBlurStep");
            oldMipBiasHandle = GLES20.glGetUniformLocation(program, "oldMipBias");
            oldBlurMixHandle = GLES20.glGetUniformLocation(program, "oldBlurMix");
            newBlurHandle = GLES20.glGetUniformLocation(program, "newBlur");
            mixHandle = GLES20.glGetUniformLocation(program, "mixValue");
            GLES20.glUseProgram(program);
            GLES20.glUniform1i(oldMipTextureHandle, 1);
            GLES20.glUniform1i(newTextureHandle, 2);
            GLES20.glUniform1i(newBlurTextureHandle, 3);
            if (compositionEnabled) {
                GLES20.glUniform1i(oldBackgroundHandle, 4);
                GLES20.glUniform1i(newBackgroundHandle, 5);
                GLES20.glUniform1i(maskTextureHandle, 6);
            }
        }
    }

    private static class Program {
        final int program;
        final int vertexShader;
        final int fragmentShader;
        final int positionHandle;
        final int textureCoordinateHandle;
        final int textureHandle;

        Program(@NonNull String vertexShaderSource, @NonNull String fragmentShaderSource) {
            vertexShader = createShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource);
            fragmentShader = createShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource);
            program = createProgram(vertexShader, fragmentShader);
            positionHandle = 0;
            textureCoordinateHandle = 1;
            textureHandle = GLES20.glGetUniformLocation(program, "sTexture");
            GLES20.glUseProgram(program);
            GLES20.glUniform1i(textureHandle, 0);
        }

        void release() {
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
        }
    }

    private static int createShader(int type, @NonNull String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Unable to compile shader: " + log);
        }
        return shader;
    }

    private static int createProgram(int vertexShader, int fragmentShader) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glBindAttribLocation(program, 0, "aPosition");
        GLES20.glBindAttribLocation(program, 1, "aTextureCoord");
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Unable to link program: " + log);
        }
        return program;
    }
}
