package org.telegram.ui.Wallet;

import android.opengl.Matrix;

/** Pure matrix math shared by the Canvas face and its simulated depth; no GL context. */
final class WalletCardProjection {
    private static final float FIELD_OF_VIEW = 10.4f;
    private static final float CAMERA_DISTANCE = 6.70f;
    private static final float CARD_ASPECT = 1.64f;
    private static final float MODEL_SCALE = 0.965f;
    private static final float MODEL_HALF_WIDTH = WalletCardGeometry.HALF_WIDTH;
    private static final float FRONT_FACE_X = -WalletCardGeometry.HALF_THICKNESS;
    private static final float BACK_FACE_X = WalletCardGeometry.HALF_THICKNESS;
    private static void setProjectionMatrix(
            float[] matrix,
            int width,
            int height
    ) {
        // The projection allows equal overscan on every side.
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

        /** Project matching front/back contour points using the MVP prepared by project(). */
        void projectDepthContour(int width, int height, float[] uv, float[] front, float[] back, float[] depth) {
            for (int i = 0; i < uv.length / 2; i++) {
                float y = WalletCardGeometry.TOP + (WalletCardGeometry.BOTTOM - WalletCardGeometry.TOP) * uv[i * 2 + 1];
                float z = (uv[i * 2] * 2 - 1) * MODEL_HALF_WIDTH;
                float frontDepth = projectPoint(FRONT_FACE_X, y, z, width, height, front, i * 2);
                float backDepth = projectPoint(BACK_FACE_X, y, z, width, height, back, i * 2);
                depth[i] = (frontDepth + backDepth) * .5f;
            }
        }

        private float projectPoint(
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
            return clipPoint[2] * inverseW;
        }
    
}
