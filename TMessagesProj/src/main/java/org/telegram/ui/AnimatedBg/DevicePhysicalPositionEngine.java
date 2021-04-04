package org.telegram.ui.AnimatedBg;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

public class DevicePhysicalPositionEngine implements SensorEventListener {

    private static final long SCALE = 10000;

    private final long[] rollBuffer = new long[100];
    private final long[] pitchBuffer = new long[100];

    private long rollBufferSum = 0;
    private long pitchBufferSum = 0;

    private int bufferIndex;
    private final WindowManager wm;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private boolean enabled;
    private Callback callback;

    public DevicePhysicalPositionEngine(Context context) {
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (accelerometer == null)
                return;
            if (enabled) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            } else {
                sensorManager.unregisterListener(this);
            }
        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int rotation = wm.getDefaultDisplay().getRotation();

        float x = event.values[0] / SensorManager.GRAVITY_EARTH;
        float y = event.values[1] / SensorManager.GRAVITY_EARTH;
        float z = event.values[2] / SensorManager.GRAVITY_EARTH;


        float pitch=(float)(Math.atan2(x, Math.sqrt(y*y+z*z))/Math.PI*2.0);
        float roll=(float)(Math.atan2(y, Math.sqrt(x*x+z*z))/Math.PI*2.0);

        switch (rotation) {
            case Surface.ROTATION_0:
                break;
            case Surface.ROTATION_90: {
                float tmp = pitch;
                pitch = roll;
                roll = tmp;
                break;
            }
            case Surface.ROTATION_180:
                roll = -roll;
                pitch = -pitch;
                break;
            case Surface.ROTATION_270: {
                float tmp = -pitch;
                pitch = roll;
                roll = tmp;
                break;
            }
        }
        rollBufferSum -= rollBuffer[bufferIndex];
        pitchBufferSum -= pitchBuffer[bufferIndex];
        rollBuffer[bufferIndex] = (long) (roll * SCALE);
        pitchBuffer[bufferIndex] = (long) (pitch * SCALE);
        rollBufferSum += rollBuffer[bufferIndex];
        pitchBufferSum += pitchBuffer[bufferIndex];
        bufferIndex = (bufferIndex + 1) % rollBuffer.length;

        roll = (float) rollBufferSum / SCALE / rollBuffer.length;
        pitch = (float) pitchBufferSum / SCALE / rollBuffer.length;
        if (roll > 1f) {
            roll = 2f - roll;
        } else if (roll < -1f) {
            roll = -2f - roll;
        }
        if (callback != null) {
            callback.onOffsetsChanged(pitch + roll);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public interface Callback {
        void onOffsetsChanged(float offset);
    }
}