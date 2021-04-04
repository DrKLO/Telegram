package org.telegram.ui.AnimatedBg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;

import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MessagesController;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class AnimatedBgGLSurfaceView extends GLSurfaceView {

    private final BgGlRenderer renderer;
    public final AnimatorEngine animatorEngine;
    private final DevicePhysicalPositionEngine physicalPositionEngine;
    private SnapshotListener snapshotListener;

    private boolean attachedToWindow;
    private boolean enabledGravityProcessing = false;

    public AnimatedBgGLSurfaceView(Context context) {
        this(context, null);
    }

    public AnimatedBgGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (BuildConfig.DEBUG) {
            setDebugFlags(DEBUG_CHECK_GL_ERROR | DEBUG_LOG_GL_CALLS);
        }
        getHolder().setFormat(PixelFormat.RGBA_8888);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setEGLConfigChooser(new SimpleEGLConfigChooser());
        renderer = new BgGlRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        animatorEngine = new AnimatorEngine(points -> requestRender());
        physicalPositionEngine = new DevicePhysicalPositionEngine(context);
        physicalPositionEngine.setCallback(animatorEngine::setGravityOffset);
        updateColors();
    }

    public boolean isEnabledGravityProcessing() {
        return enabledGravityProcessing;
    }

    public void setEnabledGravityProcessing(boolean enabledGravityProcessing) {
        if (this.enabledGravityProcessing == enabledGravityProcessing) {
            return;
        }
        this.enabledGravityProcessing = enabledGravityProcessing;
        updatePhysicalPositionEngine();
    }

    private void updatePhysicalPositionEngine() {
        physicalPositionEngine.setEnabled(attachedToWindow && enabledGravityProcessing);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        updatePhysicalPositionEngine();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        updatePhysicalPositionEngine();
    }

    public void animateToNext(MessagesController.AnimationConfig animationConfig) {
        animateToNext(animationConfig, null);
    }

    public void animateToNext(MessagesController.AnimationConfig animationConfig, AnimatorEngine.FinishMoveAnimationListener listener) {
        if (!attachedToWindow) {
            return;
        }
        animatorEngine.animateToNext(animationConfig, listener);
    }

    public void requestSnapshot(SnapshotListener snapshotListener) {
        this.snapshotListener = snapshotListener;
        requestRender();
    }

    private void onSnapshotReady(Bitmap bitmap) {
        if (snapshotListener != null) {
            snapshotListener.onSnapshotReady(bitmap);
        }
        cancelRequestSnapshot();
    }

    public void cancelRequestSnapshot() {
        snapshotListener = null;
    }

    public void updateColors() {
        MessagesController controller = MessagesController.getInstance(0);
        animatorEngine.points[0].setColor(controller.bgColor1);
        animatorEngine.points[1].setColor(controller.bgColor4);
        animatorEngine.points[2].setColor(controller.bgColor3);
        animatorEngine.points[3].setColor(controller.bgColor2);
        requestRender();
    }

    public interface SnapshotListener {

        void onSnapshotReady(Bitmap bitmap);
    }

    class BgGlRenderer implements Renderer {

        private FrameBuffer circlesFrameBuffer;
        private FrameBuffer blur1FrameBuffer;
        private FrameBuffer blur2FrameBuffer;
        private FrameBuffer smoothFrameBuffer;
        private BlurSprite2D blurSprite;
        private ProcedureDitheredSprite procedureDitheredSprite;

        private TransformableTintTextureSprite2D transformSprite;

        private Texture gradientCircleTexture;
        private Texture circleTexture;

        private int width, height;

        private final float[] white = new float[]{1f, 1f, 1f};
        private final float[] transformMatrix = new float[16];
        private final float[] screenSize = new float[2];

        private final RectF ovalRect = new RectF(0, 0, 64, 64);

        int circleFboSize = 128;
        int fboSize = 32;
        int smoothFboSize = 256;

        //for snapshot
        private final int screenshotSize = smoothFboSize * smoothFboSize;
        private ByteBuffer bb;
        private final int[] pixelsBuffer = new int[screenshotSize];

        private final int[] drawOrder = new int[]{0, 3, 2, 1};

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glEnable(GLES20.GL_DITHER);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

            circlesFrameBuffer = new FrameBuffer(circleFboSize, circleFboSize);
            blur1FrameBuffer = new FrameBuffer(fboSize, fboSize);
            blur2FrameBuffer = new FrameBuffer(fboSize, fboSize);
            smoothFrameBuffer = new FrameBuffer(smoothFboSize, smoothFboSize);
            blurSprite = new BlurSprite2D(BlurSprite2DShader.newInstance());
            transformSprite = new TransformableTintTextureSprite2D(
                    TransformableTintTextureSprite2DShader.newInstance());
            procedureDitheredSprite =
                    new ProcedureDitheredSprite(ProcedureDitheredSpriteShader.newInstance());
            gradientCircleTexture = Texture.load(genRadialGradientCircleTexture());
            circleTexture = Texture.load(genCircleTexture());
            bb = ByteBuffer.allocateDirect(screenshotSize * 4);
            bb.order(ByteOrder.nativeOrder());
        }

        private Bitmap genCircleTexture() {
            Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setAntiAlias(true);
            canvas.drawOval(ovalRect, paint);
            return bitmap;
        }

        private Bitmap genRadialGradientCircleTexture() {
            Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int[] colors = {
                    Color.argb(255, 255, 255, 255),
                    Color.argb(255, 255, 255, 255),
                    Color.argb(180, 255, 255, 255),
                    Color.argb(0, 255, 255, 255)
            };
            RadialGradient gradient = new RadialGradient(
                    32f, 32f, 32f, colors,
                    new float[]{0f, 0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP
            );
            Paint paint = new Paint();
            paint.setShader(gradient);
            canvas.drawOval(ovalRect, paint);
            return bitmap;
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            this.width = width;
            this.height = height;
            updateColors();
        }

        private void applyMatrixTransform(PointF position, float width, float height) {
            Matrix.setIdentityM(transformMatrix, 0);
            Matrix.translateM(
                    transformMatrix,
                    0,
                    (position.x - 0.5f) * 2,
                    (position.y - 0.5f) * 2,
                    0
            );
            Matrix.scaleM(transformMatrix, 0, width, height, 1);
        }

        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            circlesFrameBuffer.activate();
            GLES20.glColorMask(true, true, true, true);
            GLES20.glViewport(0, 0, circleFboSize, circleFboSize);
            GLES20.glClearColor(
                    animatorEngine.points[0].floatColor[0],
                    animatorEngine.points[0].floatColor[1],
                    animatorEngine.points[0].floatColor[2], 1.0f
            );
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            int i;
            for (int index : drawOrder) {
                AnimatorEngine.PointInfo pointInfo = animatorEngine.points[index];
                applyMatrixTransform(
                        pointInfo.bgPosition, pointInfo.rBgHorizontal * 2, pointInfo.rBgVertical * 2
                );
                transformSprite.draw(circleTexture, transformMatrix, pointInfo.floatColor);
            }
            for (int index : drawOrder) {
                AnimatorEngine.PointInfo pointInfo = animatorEngine.points[index];
                applyMatrixTransform(
                        pointInfo.position, pointInfo.rHorizontal * 2, pointInfo.rVertical * 2
                );
                transformSprite.draw(gradientCircleTexture, transformMatrix, pointInfo.floatColor);
            }

            circlesFrameBuffer.flush();

            GLES20.glViewport(0, 0, fboSize, fboSize);
            blur2FrameBuffer.activate();
            blurSprite.draw(circlesFrameBuffer.bufferTexture, 2f, 128);
            blur2FrameBuffer.flush();
            for (i = 0; i < 10; i++) {
                blur1FrameBuffer.activate();
                blurSprite.draw(blur2FrameBuffer.bufferTexture, 2f, 128);
                blur1FrameBuffer.flush();
                blur2FrameBuffer.activate();
                blurSprite.draw(blur1FrameBuffer.bufferTexture, 2f, 128);
                blur2FrameBuffer.flush();
            }
            blur1FrameBuffer.activate();
            blurSprite.draw(blur2FrameBuffer.bufferTexture, 2f, 128);
            blur1FrameBuffer.flush();
            GLES20.glViewport(0, 0, smoothFboSize, smoothFboSize);
            smoothFrameBuffer.activate();
            screenSize[0] = smoothFboSize;
            screenSize[1] = smoothFboSize;
            procedureDitheredSprite.draw(blur2FrameBuffer.bufferTexture, screenSize);
            if (snapshotListener != null) {
                takeSnapshot();
            }
            smoothFrameBuffer.flush();

            GLES20.glViewport(0, 0, width, height);
            Matrix.setIdentityM(transformMatrix, 0);
            screenSize[0] = width;
            screenSize[1] = height;
            procedureDitheredSprite.draw(smoothFrameBuffer.bufferTexture, screenSize);
        }

        private void takeSnapshot() {
            final Bitmap bitmap =
                    Bitmap.createBitmap(smoothFboSize, smoothFboSize, Bitmap.Config.ARGB_8888);
            saveFrame(bitmap);
            post(() -> onSnapshotReady(bitmap));
        }

        private Bitmap saveFrame(Bitmap target) {
            GLES20.glReadPixels(
                    0, 0, smoothFboSize, smoothFboSize,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb
            );
            bb.asIntBuffer().get(pixelsBuffer);
            for (int i = 0; i < screenshotSize; ++i) {
                // The alpha and green channels' positions are preserved while the      red and blue are swapped
                pixelsBuffer[i] =
                        ((pixelsBuffer[i] & 0xff00ff00)) | ((pixelsBuffer[i] & 0x000000ff) << 16) | ((pixelsBuffer[i] & 0x00ff0000) >> 16);
            }
            if (target == null) {
                target = Bitmap.createBitmap(smoothFboSize, smoothFboSize, Bitmap.Config.ARGB_8888);
            }

            target.setPixels(pixelsBuffer, 0, smoothFboSize, 0, 0, smoothFboSize, smoothFboSize);
            return target;
        }
    }
}