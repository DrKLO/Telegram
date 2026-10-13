package org.telegram.ui.Wallet;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.util.WeakHashMap;

/** Shared, relative device tilt, active only while transfer cards are being drawn. */
final class WalletCardMotion implements SensorEventListener {
    private static WalletCardMotion instance;
    private final SensorManager manager;
    private final Sensor sensor;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WeakHashMap<View, Long> viewers = new WeakHashMap<>();
    private final float[] matrix = new float[9], remapped = new float[9], orientation = new float[3];
    private boolean listening, calibrated;
    private float basePitch, baseRoll;
    private int rotation = -1;
    float x, y;

    static WalletCardMotion get(Context context) {
        if (instance == null) instance = new WalletCardMotion(context.getApplicationContext());
        return instance;
    }

    private WalletCardMotion(Context context) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Sensor selected = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (selected == null && manager != null) selected = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensor = selected;
    }

    void drawn(View view) {
        if (sensor == null || !view.isAttachedToWindow() || !view.isShown() || view.getWindowVisibility() != View.VISIBLE) return;
        viewers.put(view, SystemClock.uptimeMillis());
        if (!listening) {
            calibrated = false;
            listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, handler);
            if (listening) handler.postDelayed(cleanup, 250);
        }
    }

    void detach(View view) {
        viewers.remove(view);
        if (viewers.isEmpty()) stop();
    }

    private final Runnable cleanup = new Runnable() {
        @Override public void run() {
            long now = SystemClock.uptimeMillis();
            viewers.entrySet().removeIf(e -> !e.getKey().isAttachedToWindow() || !e.getKey().isShown()
                    || e.getKey().getWindowVisibility() != View.VISIBLE || now - e.getValue() > 300);
            if (viewers.isEmpty()) stop();
            else handler.postDelayed(this, 250);
        }
    };

    private void stop() {
        if (listening) manager.unregisterListener(this);
        listening = calibrated = false;
        handler.removeCallbacks(cleanup);
        x = y = 0;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        int nextRotation = windows.getDefaultDisplay().getRotation();
        if (rotation != nextRotation) { calibrated = false; rotation = nextRotation; }
        int axisX = SensorManager.AXIS_X, axisY = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) { axisX = SensorManager.AXIS_Y; axisY = SensorManager.AXIS_MINUS_X; }
        else if (rotation == Surface.ROTATION_180) { axisX = SensorManager.AXIS_MINUS_X; axisY = SensorManager.AXIS_MINUS_Y; }
        else if (rotation == Surface.ROTATION_270) { axisX = SensorManager.AXIS_MINUS_Y; axisY = SensorManager.AXIS_X; }
        SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped);
        SensorManager.getOrientation(remapped, orientation);
        if (!calibrated) {
            basePitch = orientation[1]; baseRoll = orientation[2]; calibrated = true;
        }
        float targetX = clamp((float) Math.IEEEremainder(orientation[2] - baseRoll, Math.PI * 2) / .45f);
        float targetY = clamp((float) Math.IEEEremainder(orientation[1] - basePitch, Math.PI * 2) / .45f);
        x += (targetX - x) * .12f;
        y += (targetY - y) * .12f;
        for (View parent : viewers.keySet()) parent.invalidate();
    }

    private static float clamp(float value) { return Math.max(-1, Math.min(1, value)); }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
