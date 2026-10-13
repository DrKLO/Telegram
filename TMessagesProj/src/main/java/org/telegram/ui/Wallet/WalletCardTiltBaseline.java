package org.telegram.ui.Wallet;

final class WalletCardTiltBaseline {

    private static final long RECENTER_DELAY_NANOS = 1_000_000_000L;
    private static final float STATIONARY_RADIUS_RATIO = 0.075f;
    private static final float RECENTER_TIME_CONSTANT_SECONDS = 1.0f;
    private static final float MAX_FRAME_SECONDS = 0.1f;

    private final float maxRotationDegrees;

    private boolean hasSensorPose;
    private float sensorRotationX;
    private float sensorRotationY;
    private float baselineRotationX;
    private float baselineRotationY;
    private float stationaryAnchorX;
    private float stationaryAnchorY;
    private long stationarySinceNanos;
    private long latestSensorTimestampNanos;
    private long lastFrameNanos;

    WalletCardTiltBaseline(float maxRotationDegrees) {
        this.maxRotationDegrees = maxRotationDegrees;
    }

    void reset() {
        hasSensorPose = false;
        stationarySinceNanos = 0L;
        latestSensorTimestampNanos = 0L;
        lastFrameNanos = 0L;
    }

    void setSensorRotation(float rotationX, float rotationY, long timestampNanos) {
        if (Float.isNaN(rotationX) || Float.isInfinite(rotationX) || Float.isNaN(rotationY) || Float.isInfinite(rotationY)) {
            return;
        }
        sensorRotationX = rotationX;
        sensorRotationY = rotationY;
        latestSensorTimestampNanos = timestampNanos;

        if (!hasSensorPose) {
            hasSensorPose = true;
            baselineRotationX = rotationX;
            baselineRotationY = rotationY;
            stationaryAnchorX = rotationX;
            stationaryAnchorY = rotationY;
            stationarySinceNanos = timestampNanos;
            return;
        }

        float movementX = rotationX - stationaryAnchorX;
        float movementY = rotationY - stationaryAnchorY;
        float stationaryRadius = maxRotationDegrees * STATIONARY_RADIUS_RATIO;
        float stationaryRadiusSquared = stationaryRadius * stationaryRadius;
        if (movementX * movementX + movementY * movementY
                > stationaryRadiusSquared) {
            stationaryAnchorX = rotationX;
            stationaryAnchorY = rotationY;
            stationarySinceNanos = timestampNanos;
        }
    }

    void update(long frameTimeNanos) {
        if (!hasSensorPose) {
            return;
        }
        if (lastFrameNanos == 0L) {
            lastFrameNanos = frameTimeNanos;
            return;
        }

        float frameSeconds = Math.min(
                MAX_FRAME_SECONDS,
                Math.max(0.0f, (frameTimeNanos - lastFrameNanos) * 0.000000001f)
        );
        lastFrameNanos = frameTimeNanos;
        if (latestSensorTimestampNanos - stationarySinceNanos
                < RECENTER_DELAY_NANOS) {
            return;
        }

        float amount = 1.0f - (float) Math.exp(
                -frameSeconds / RECENTER_TIME_CONSTANT_SECONDS
        );
        baselineRotationX += (sensorRotationX - baselineRotationX) * amount;
        baselineRotationY += (sensorRotationY - baselineRotationY) * amount;
    }

    float getRotationX() {
        return hasSensorPose
                ? clamp(sensorRotationX - baselineRotationX)
                : 0.0f;
    }

    float getRotationY() {
        return hasSensorPose
                ? clamp(sensorRotationY - baselineRotationY)
                : 0.0f;
    }

    private float clamp(float value) {
        return Math.max(-maxRotationDegrees, Math.min(maxRotationDegrees, value));
    }
}
