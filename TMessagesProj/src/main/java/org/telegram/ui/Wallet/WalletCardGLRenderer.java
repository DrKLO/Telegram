package org.telegram.ui.Wallet;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.Matrix;

import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class WalletCardGLRenderer extends GLIconRenderer {

    private static final float FIELD_OF_VIEW = 10.4f;
    private static final float CAMERA_DISTANCE = 6.70f;
    private static final float CARD_ASPECT = 1.64f;
    private static final float MODEL_SCALE = 0.965f;
    private static final float MODEL_HALF_WIDTH = WalletCardGeometry.HALF_WIDTH;
    private static final float FRONT_FACE_X = -WalletCardGeometry.HALF_THICKNESS;
    private static final float BACK_FACE_X = WalletCardGeometry.HALF_THICKNESS;
    private static final float LIGHT_DIRECTION_X = 0.175f;
    private static final float LIGHT_DIRECTION_Y = 0.00f;
    private static final float LIGHT_DIRECTION_Z = 0.83f;
    private static final float LIGHT_ORBIT_CYCLES_PER_SECOND = 0.05f;
    private static final int[] TRANSIENT_ATTACHMENTS = { GLES30.GL_DEPTH };

    private final Context context;
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] viewModelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    // Publish all four coordinates together to the GL thread.
    private volatile float[] diamondBounds = new float[4];
    private volatile float diamondAlpha = 1f;

    void setDiamondAlpha(float alpha) {
        diamondAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    void setDiamondBounds(float x, float y, float width, float height) {
        float[] bounds = diamondBounds;
        if (bounds[0] != x || bounds[1] != y || bounds[2] != width || bounds[3] != height) {
            diamondBounds = new float[]{x, y, width, height};
        }
    }

    // Publish the two angles as one pose; GL must never read half of an update.
    private volatile float[] rotation = new float[2];
    private Bitmap pendingFrontBitmap;
    private Bitmap retainedFrontBitmap;
    private Bitmap spareFrontBitmap;
    private boolean frontTextureUploaded;
    private long submittedFrontRevision;
    private long pendingFrontRevision;
    private long uploadedFrontRevision;
    private final WalletCardFrameHistory frameHistory = new WalletCardFrameHistory();

    synchronized long nextFrontRevision() {
        return submittedFrontRevision + 1;
    }

    long presentedFrontRevision(long timestamp) {
        return frameHistory.revisionAt(timestamp);
    }

    synchronized Bitmap acquireFrontBitmap(int width, int height) {
        Bitmap bitmap = spareFrontBitmap;
        spareFrontBitmap = null;
        if (bitmap != null && (bitmap.getWidth() != width || bitmap.getHeight() != height)) {
            bitmap.recycle();
            bitmap = null;
        }
        return bitmap != null ? bitmap : Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    synchronized void submitFrontBitmap(Bitmap bitmap) {
        if (pendingFrontBitmap != null) recycleFrontBitmap(pendingFrontBitmap);
        pendingFrontBitmap = bitmap;
        pendingFrontRevision = ++submittedFrontRevision;
    }

    private void recycleFrontBitmap(Bitmap bitmap) {
        if (spareFrontBitmap != null) spareFrontBitmap.recycle();
        spareFrontBitmap = bitmap;
    }

    private void uploadFrontBitmap() {
        Bitmap bitmap;
        long revision;
        synchronized (this) {
            bitmap = pendingFrontBitmap;
            revision = pendingFrontRevision;
            pendingFrontBitmap = null;
        }
        if (bitmap != null) {
            // No lock while uploading: UI owns neither this bitmap nor the
            // retained bitmap until we return it to the spare pool below.
            card.setFrontBitmap(bitmap);
            synchronized (this) {
                if (retainedFrontBitmap != null) recycleFrontBitmap(retainedFrontBitmap);
                retainedFrontBitmap = bitmap;
            }
            uploadedFrontRevision = revision;
            frontTextureUploaded = true;
        } else if (!frontTextureUploaded && retainedFrontBitmap != null) {
            // Restore unchanged content after EGL context recreation.
            card.setFrontBitmap(retainedFrontBitmap);
            frontTextureUploaded = true;
        }
    }
    private volatile Bitmap engravingBitmap;
    private Bitmap uploadedEngravingBitmap;
    private volatile int cardIconResId;
    private int uploadedCardIconResId;
    private final int qrButtonRightPadding;
    private WalletCard3D card;
    volatile boolean frameDrawn;
    private boolean lightOrbitPaused = true;
    private long lightOrbitElapsedNanos;
    private long lightOrbitLastFrameNanos;
    private int surfaceWidth, surfaceHeight;

    WalletCardGLRenderer(
            Context context,
            int cardIconResId,
            int qrButtonRightPadding
    ) {
        super(context, FRAGMENT_STYLE, Icon3D.TYPE_STAR);
        this.context = context.getApplicationContext();
        this.cardIconResId = cardIconResId;
        this.qrButtonRightPadding = qrButtonRightPadding;
    }

    void setRotation(float rotationX, float rotationY) {
        rotation = new float[]{rotationX, rotationY};
    }

    synchronized void setLightOrbitPaused(boolean paused) {
        if (lightOrbitPaused == paused) return;
        lightOrbitPaused = paused;
        // Resume from the last displayed light position, without catching up.
        lightOrbitLastFrameNanos = 0;
    }

    private synchronized float getLightOrbitElapsedSeconds() {
        if (!lightOrbitPaused) {
            long now = System.nanoTime();
            if (lightOrbitLastFrameNanos != 0) {
                lightOrbitElapsedNanos += now - lightOrbitLastFrameNanos;
            }
            lightOrbitLastFrameNanos = now;
        }
        return lightOrbitElapsedNanos * 0.000000001f;
    }

    void setEngravingBitmap(Bitmap bitmap) {
        engravingBitmap = bitmap;
    }

    void setCardIcon(int drawableResId) {
        cardIconResId = drawableResId;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        frameDrawn = false;
        frameHistory.clear();
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        if (card != null) {
            card.destroy();
        }
        card = new WalletCard3D(context, cardIconResId, qrButtonRightPadding);
        uploadedCardIconResId = cardIconResId;
        uploadedEngravingBitmap = null;
        frontTextureUploaded = false;
        synchronized (this) {
            lightOrbitLastFrameNanos = 0;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        GLES20.glViewport(0, 0, width, height);
        setProjectionMatrix(projectionMatrix, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (card == null) {
            return;
        }
        int currentCardIconResId = cardIconResId;
        if (currentCardIconResId != uploadedCardIconResId) {
            card.setCardIcon(context, currentCardIconResId, qrButtonRightPadding);
            uploadedCardIconResId = currentCardIconResId;
        }
        Bitmap currentEngraving = engravingBitmap;
        if (currentEngraving != null
                && currentEngraving != uploadedEngravingBitmap) {
            card.setEngravingBitmap(currentEngraving);
            // The component and the 2D loading card also retain this bitmap.
            uploadedEngravingBitmap = currentEngraving;
        }
        uploadFrontBitmap();
        setViewMatrix(viewMatrix);
        final float[] pose = rotation;
        final float cardRotationX = pose[0];
        final float cardRotationY = pose[1];
        setModelMatrix(modelMatrix, cardRotationX, cardRotationY);

        Matrix.multiplyMM(
                viewModelMatrix,
                0,
                viewMatrix,
                0,
                modelMatrix,
                0
        );
        Matrix.multiplyMM(
                mvpMatrix,
                0,
                projectionMatrix,
                0,
                viewModelMatrix,
                0
        );
        float elapsedSeconds = getLightOrbitElapsedSeconds();
        float orbitPhase = elapsedSeconds
                * LIGHT_ORBIT_CYCLES_PER_SECOND
                * (float) (Math.PI * 2.0);
        float orbitAngle = (float) Math.sin(orbitPhase)
                * (float) (Math.PI * 0.5);
        float orbitCos = (float) Math.cos(orbitAngle);
        float orbitSin = (float) Math.sin(orbitAngle);
        float lightDirectionX = LIGHT_DIRECTION_X * orbitCos
                - LIGHT_DIRECTION_Y * orbitSin;
        float lightDirectionY = LIGHT_DIRECTION_X * orbitSin
                + LIGHT_DIRECTION_Y * orbitCos;
        card.setDiamondBounds(diamondBounds, diamondAlpha);
        card.draw(
                mvpMatrix,
                viewModelMatrix,
                lightDirectionX,
                lightDirectionY,
                LIGHT_DIRECTION_Z,
                cardRotationY * 2.0f - cardRotationX * 1.25f,
                surfaceWidth,
                surfaceHeight
        );
        // Only color is presented. Depth is cleared next frame, so tile-based
        // GPUs need not store the multisampled depth buffer to external memory.
        GLES30.glInvalidateFramebuffer(GLES20.GL_FRAMEBUFFER,
                TRANSIENT_ATTACHMENTS.length, TRANSIENT_ATTACHMENTS, 0);
        // GLIconTextureView swaps immediately after this method. Stamp that
        // buffer so UI handoffs wait for presentation, not merely an upload.
        final long timestamp = System.nanoTime();
        if (EGLExt.eglPresentationTimeANDROID(EGL14.eglGetCurrentDisplay(),
                EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), timestamp)) {
            frameHistory.record(timestamp, frontTextureUploaded ? uploadedFrontRevision : 0);
        }
        frameDrawn = frontTextureUploaded;
    }

    private static void setProjectionMatrix(
            float[] matrix,
            int width,
            int height
    ) {
        // WalletCardView gives the GL surface equal overscan on every side.
        // Recover the card's visible (non-overscan) dimensions from the
        // surface aspect, then widen the FOV by the overscan ratio. This keeps
        // the resting card exactly the same size while leaving room for both
        // pitched and yawed corners.
        float visibleWidth = (width - height)
                / (1.0f - 1.0f / CARD_ASPECT);
        if (visibleWidth <= 0.0f || visibleWidth > width) {
            visibleWidth = width;
        }
        float visibleHeight = visibleWidth / CARD_ASPECT;
        float overscanRatio = height / Math.max(1.0f, visibleHeight);
        float fieldOfView = (float) Math.toDegrees(
                2.0 * Math.atan(
                        Math.tan(Math.toRadians(FIELD_OF_VIEW * 0.5))
                                * overscanRatio
                )
        );
        Matrix.perspectiveM(
                matrix,
                0,
                fieldOfView,
                (float) width / Math.max(1, height),
                0.1f,
                20.0f
        );
    }

    private static void setViewMatrix(float[] matrix) {
        Matrix.setLookAtM(
                matrix,
                0,
                0, 0, CAMERA_DISTANCE,
                0, 0, 0,
                0, 1, 0
        );
    }

    private static void setModelMatrix(
            float[] matrix,
            float rotationX,
            float rotationY
    ) {
        Matrix.setIdentityM(matrix, 0);
        Matrix.rotateM(matrix, 0, -rotationX, 1, 0, 0);
        Matrix.rotateM(matrix, 0, -rotationY, 0, 1, 0);

        // The Blender card's faces point along X. Rotate them to face the camera,
        // retaining the calibrated camera-space offset of the card.
        Matrix.rotateM(matrix, 0, 90, 0, 1, 0);
        Matrix.translateM(matrix, 0, -0.02f, 0, 0);
        Matrix.scaleM(matrix, 0, MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    static final class FaceProjection {
        private final float[] projectionMatrix = new float[16];
        private final float[] viewMatrix = new float[16];
        private final float[] modelMatrix = new float[16];
        private final float[] viewModelMatrix = new float[16];
        private final float[] mvpMatrix = new float[16];
        private final float[] point = new float[4];
        private final float[] clipPoint = new float[4];

        void project(
                int width,
                int height,
                float rotationX,
                float rotationY,
                boolean back,
                float[] result
        ) {
            setProjectionMatrix(projectionMatrix, width, height);
            setViewMatrix(viewMatrix);
            setModelMatrix(modelMatrix, rotationX, rotationY);
            Matrix.multiplyMM(
                    viewModelMatrix,
                    0,
                    viewMatrix,
                    0,
                    modelMatrix,
                    0
            );
            Matrix.multiplyMM(
                    mvpMatrix,
                    0,
                    projectionMatrix,
                    0,
                    viewModelMatrix,
                    0
            );

            float faceX = back ? BACK_FACE_X : FRONT_FACE_X;
            float leftZ = back ? MODEL_HALF_WIDTH : -MODEL_HALF_WIDTH;
            float rightZ = -leftZ;
            projectPoint(faceX, WalletCardGeometry.TOP, leftZ, width, height, result, 0);
            projectPoint(faceX, WalletCardGeometry.TOP, rightZ, width, height, result, 2);
            projectPoint(faceX, WalletCardGeometry.BOTTOM, rightZ, width, height, result, 4);
            projectPoint(faceX, WalletCardGeometry.BOTTOM, leftZ, width, height, result, 6);
        }

        private void projectPoint(
                float x,
                float y,
                float z,
                int width,
                int height,
                float[] result,
                int offset
        ) {
            point[0] = x;
            point[1] = y;
            point[2] = z;
            point[3] = 1.0f;
            Matrix.multiplyMV(clipPoint, 0, mvpMatrix, 0, point, 0);
            float inverseW = 1.0f / clipPoint[3];
            result[offset] = (clipPoint[0] * inverseW * 0.5f + 0.5f) * width;
            result[offset + 1] = (0.5f - clipPoint[1] * inverseW * 0.5f) * height;
        }
    }
}
