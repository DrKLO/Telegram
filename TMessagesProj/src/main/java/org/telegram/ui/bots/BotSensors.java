package org.telegram.ui.bots;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.web.BotWebViewContainer;

public class BotSensors {

    private final SensorManager sensorManager;

    private Sensor accelerometer;
    private long accelerometerDesiredRefreshRate;
    private Sensor gyroscope;
    private long gyroscopeDesiredRefreshRate;
    private Sensor orientationMagnetometer;
    private Sensor orientationAccelerometer;
    private long absoluteOrientationDesiredRefreshRate;
    private Sensor rotation;
    private long relativeOrientationDesiredRefreshRate;

    public BotSensors(Context context, long bot_id) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    private BotWebViewContainer.MyWebView webView;
    public void attachWebView(BotWebViewContainer.MyWebView webView) {
        this.webView = webView;
    }
    public void detachWebView(BotWebViewContainer.MyWebView webView) {
        if (this.webView == webView) {
            this.webView = null;
            pause();
        }
    }

    public boolean startAccelerometer(long refresh_rate) {
        if (sensorManager == null) return false;
        if (accelerometer != null) return true;
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) return false;
        accelerometerDesiredRefreshRate = refresh_rate;
        if (!paused) {
            sensorManager.registerListener(accelerometerListener, accelerometer, getSensorDelay(refresh_rate));
        }
        return true;
    }

    public boolean stopAccelerometer() {
        if (sensorManager == null) return false;
        if (accelerometer == null) return true;
        if (!paused) {
            sensorManager.unregisterListener(accelerometerListener, accelerometer);
        }
        if (accelerometerListenerPostponed != null) {
            AndroidUtilities.cancelRunOnUIThread(accelerometerListenerPostponed);
            accelerometerListenerPostponed = null;
        }
        accelerometer = null;
        return true;
    }

    public boolean startGyroscope(long refresh_rate) {
        if (sensorManager == null) return false;
        if (gyroscope != null) return true;
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope == null) return false;
        gyroscopeDesiredRefreshRate = refresh_rate;
        if (!paused) {
            sensorManager.registerListener(gyroscopeListener, gyroscope, getSensorDelay(refresh_rate));
        }
        return true;
    }

    public boolean stopGyroscope() {
        if (sensorManager == null) return false;
        if (gyroscope == null) return true;
        if (!paused) {
            sensorManager.unregisterListener(gyroscopeListener, gyroscope);
        }
        if (gyroscopeListenerPostponed != null) {
            AndroidUtilities.cancelRunOnUIThread(gyroscopeListenerPostponed);
            gyroscopeListenerPostponed = null;
        }
        gyroscope = null;
        return true;
    }

    public boolean startOrientation(boolean absolute, long refresh_rate) {
        if (sensorManager == null) return false;
        if (absolute) {
            if (rotation != null) {
                if (relativeOrientationListenerPostponed != null) {
                    AndroidUtilities.cancelRunOnUIThread(relativeOrientationListenerPostponed);
                    relativeOrientationListenerPostponed = null;
                }
                if (!paused) {
                    if (rotation != null) {
                        sensorManager.unregisterListener(relativeOrientationListener, rotation);
                    }
                }
                rotation = null;
            }
            if (orientationMagnetometer != null && orientationAccelerometer != null) return true;
            orientationAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            orientationMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (orientationAccelerometer == null || orientationMagnetometer == null) return false;
            absoluteOrientationDesiredRefreshRate = refresh_rate;
            if (!paused) {
                sensorManager.registerListener(absoluteOrientationListener, orientationAccelerometer, getSensorDelay(refresh_rate));
                sensorManager.registerListener(absoluteOrientationListener, orientationMagnetometer, getSensorDelay(refresh_rate));
            }
        } else {
            if (orientationMagnetometer != null || orientationAccelerometer != null) {
                if (absoluteOrientationListenerPostponed != null) {
                    AndroidUtilities.cancelRunOnUIThread(absoluteOrientationListenerPostponed);
                    absoluteOrientationListenerPostponed = null;
                }
                if (!paused) {
                    if (orientationAccelerometer != null) {
                        sensorManager.unregisterListener(absoluteOrientationListener, orientationAccelerometer);
                    }
                    if (orientationMagnetometer != null) {
                        sensorManager.unregisterListener(absoluteOrientationListener, orientationMagnetometer);
                    }
                }
                orientationAccelerometer = null;
                orientationMagnetometer = null;
            }
            if (rotation != null) return true;
            rotation = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (rotation == null) return false;
            relativeOrientationDesiredRefreshRate = refresh_rate;
            if (!paused) {
                sensorManager.registerListener(relativeOrientationListener, rotation, getSensorDelay(refresh_rate));
            }
        }
        return true;
    }

    public boolean stopOrientation() {
        if (sensorManager == null) return false;
        if (orientationAccelerometer == null && orientationMagnetometer == null && rotation == null) return true;
        if (!paused) {
            if (orientationAccelerometer != null) {
                sensorManager.unregisterListener(absoluteOrientationListener, orientationAccelerometer);
            }
            if (orientationMagnetometer != null) {
                sensorManager.unregisterListener(absoluteOrientationListener, orientationMagnetometer);
            }
            if (rotation != null) {
                sensorManager.unregisterListener(relativeOrientationListener, rotation);
            }
        }
        if (absoluteOrientationListenerPostponed != null) {
            AndroidUtilities.cancelRunOnUIThread(absoluteOrientationListenerPostponed);
            absoluteOrientationListenerPostponed = null;
        }
        if (relativeOrientationListenerPostponed != null) {
            AndroidUtilities.cancelRunOnUIThread(relativeOrientationListenerPostponed);
            relativeOrientationListenerPostponed = null;
        }
        orientationAccelerometer = null;
        orientationMagnetometer = null;
        rotation = null;
        return true;
    }

    public void stopAll() {
        stopOrientation();
        stopGyroscope();
        stopAccelerometer();
    }

    // SENSOR_DELAY_NORMAL — 160ms
    // SENSOR_DELAY_UI — 60ms
    // SENSOR_DELAY_GAME — 20ms
    private static int getSensorDelay(long refresh_rate) {
        if (refresh_rate >= 160) return SensorManager.SENSOR_DELAY_NORMAL;
        if (refresh_rate >= 60) return SensorManager.SENSOR_DELAY_UI;
        return SensorManager.SENSOR_DELAY_GAME;
    }

    private boolean paused;

    public void pause() {
        if (paused) return;
        paused = true;

        if (sensorManager != null) {
            if (accelerometer != null) {
                sensorManager.unregisterListener(accelerometerListener, accelerometer);
            }
            if (accelerometerListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(accelerometerListenerPostponed);
                accelerometerListenerPostponed = null;
            }
            if (gyroscope != null) {
                sensorManager.unregisterListener(gyroscopeListener, gyroscope);
            }
            if (gyroscopeListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(gyroscopeListenerPostponed);
                gyroscopeListenerPostponed = null;
            }
            if (orientationAccelerometer != null) {
                sensorManager.unregisterListener(absoluteOrientationListener, orientationAccelerometer);
            }
            if (orientationMagnetometer != null) {
                sensorManager.unregisterListener(absoluteOrientationListener, orientationMagnetometer);
            }
            if (absoluteOrientationListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(absoluteOrientationListenerPostponed);
                absoluteOrientationListenerPostponed = null;
            }
            if (rotation != null) {
                sensorManager.unregisterListener(relativeOrientationListener, rotation);
            }
            if (relativeOrientationListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(relativeOrientationListenerPostponed);
                relativeOrientationListenerPostponed = null;
            }
        }
    }

    public void resume() {
        if (!paused) return;
        paused = false;

        if (sensorManager != null) {
            if (accelerometer != null) {
                sensorManager.registerListener(accelerometerListener, accelerometer, getSensorDelay(accelerometerDesiredRefreshRate));
            }
            if (gyroscope != null) {
                sensorManager.registerListener(gyroscopeListener, gyroscope, getSensorDelay(gyroscopeDesiredRefreshRate));
            }
            if (orientationAccelerometer != null) {
                sensorManager.registerListener(absoluteOrientationListener, orientationAccelerometer, getSensorDelay(absoluteOrientationDesiredRefreshRate));
            }
            if (orientationMagnetometer != null) {
                sensorManager.registerListener(absoluteOrientationListener, orientationMagnetometer, getSensorDelay(absoluteOrientationDesiredRefreshRate));
            }
            if (rotation != null) {
                sensorManager.registerListener(relativeOrientationListener, rotation, getSensorDelay(relativeOrientationDesiredRefreshRate));
            }
        }
    }

    private Runnable accelerometerListenerPostponed;
    private final SensorEventListener accelerometerListener = new SensorEventListener() {
        private float[] xyz;
        private long lastTime;
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (accelerometerListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(accelerometerListenerPostponed);
                accelerometerListenerPostponed = null;
            }
            if (paused || webView == null) return;
            final long now = System.currentTimeMillis();
            final long diff = now - lastTime;
            xyz = event.values;
            if (diff < accelerometerDesiredRefreshRate) {
                AndroidUtilities.runOnUIThread(accelerometerListenerPostponed = this::post, accelerometerDesiredRefreshRate - diff);
                return;
            }
            post();
        }

        public void post() {
            if (webView == null) return;
            if (xyz == null) return;
            lastTime = System.currentTimeMillis();
            try {
                JSONObject eventData = new JSONObject();
                eventData.put("x", -xyz[0]);
                eventData.put("y", -xyz[1]);
                eventData.put("z", -xyz[2]);
                webView.evaluateJS("window.Telegram.WebView.receiveEvent('" + "accelerometer_changed" + "', " + eventData + ");");
            } catch (Exception e) {}
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private Runnable gyroscopeListenerPostponed;
    private final SensorEventListener gyroscopeListener = new SensorEventListener() {

        private long lastTime;
        private float[] captured = new float[3];


        @Override
        public void onSensorChanged(SensorEvent event) {
            if (gyroscopeListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(gyroscopeListenerPostponed);
                gyroscopeListenerPostponed = null;
            }
            if (paused || webView == null) return;
            captured[0] += event.values[0];
            captured[1] += event.values[1];
            captured[2] += event.values[2];
            final long now = System.currentTimeMillis();
            final long diff = now - lastTime;
            if (diff < gyroscopeDesiredRefreshRate) {
                AndroidUtilities.runOnUIThread(gyroscopeListenerPostponed = this::post, gyroscopeDesiredRefreshRate - diff);
                return;
            }
            post();
        }

        public void post() {
            if (webView == null) return;
            lastTime = System.currentTimeMillis();
            final float[] xyz = captured;
            try {
                JSONObject eventData = new JSONObject();
                eventData.put("x", xyz[0]);
                eventData.put("y", xyz[1]);
                eventData.put("z", xyz[2]);
                // web api:
//                eventData.put("x", xyz[2]);
//                eventData.put("y", xyz[0]);
//                eventData.put("z", xyz[1]);
                webView.evaluateJS("window.Telegram.WebView.receiveEvent('" + "gyroscope_changed" + "', " + eventData + ");");
            } catch (Exception e) {}
            captured[0] = 0;
            captured[1] = 0;
            captured[2] = 0;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private Runnable absoluteOrientationListenerPostponed;
    private final SensorEventListener absoluteOrientationListener = new SensorEventListener() {
        private long lastTime;

        private float[] gravity;
        private float[] geomagnetic;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (absoluteOrientationListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(absoluteOrientationListenerPostponed);
                absoluteOrientationListenerPostponed = null;
            }
            if (paused || webView == null) return;
            final long now = System.currentTimeMillis();
            final long diff = now - lastTime;
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                gravity = event.values;
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                geomagnetic = event.values;
            if (diff < absoluteOrientationDesiredRefreshRate) {
                AndroidUtilities.runOnUIThread(absoluteOrientationListenerPostponed = this::post, absoluteOrientationDesiredRefreshRate - diff);
                return;
            }
            post();
        }

        public void post() {
            if (gravity == null || geomagnetic == null) return;
            if (webView == null) return;
            lastTime = System.currentTimeMillis();
            float R[] = new float[9];
            float I[] = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                try {
                    JSONObject eventData = new JSONObject();
                    eventData.put("absolute", true);
                    eventData.put("alpha", -orientation[0]);
                    eventData.put("beta",  -orientation[1]);
                    eventData.put("gamma", orientation[2]);
                    webView.evaluateJS("window.Telegram.WebView.receiveEvent('" + "device_orientation_changed" + "', " + eventData + ");");
                } catch (Exception e) {}
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private Runnable relativeOrientationListenerPostponed;
    private final SensorEventListener relativeOrientationListener = new SensorEventListener() {
        private long lastTime;
        private float[] values;
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (relativeOrientationListenerPostponed != null) {
                AndroidUtilities.cancelRunOnUIThread(relativeOrientationListenerPostponed);
                relativeOrientationListenerPostponed = null;
            }
            if (paused || webView == null) return;
            final long now = System.currentTimeMillis();
            final long diff = now - lastTime;
            if (diff < relativeOrientationDesiredRefreshRate) {
                AndroidUtilities.runOnUIThread(relativeOrientationListenerPostponed = this::post, relativeOrientationDesiredRefreshRate - diff);
                return;
            }
            if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                values = event.values;
            }
            post();
        }

        private float[] mDeviceRotationMatrix;
        private float[] mTruncatedRotationVector;

        public void post() {
            if (values == null) return;
            if (webView == null) return;
            lastTime = System.currentTimeMillis();
            if (mDeviceRotationMatrix == null) {
                mDeviceRotationMatrix = new float[9];
            }
            if (mTruncatedRotationVector == null) {
                mTruncatedRotationVector = new float[4];
            }
            if (values.length > 4) {
                // On some Samsung devices SensorManager.getRotationMatrixFromVector
                // appears to throw an exception if rotation vector has length > 4.
                // For the purposes of this class the first 4 values of the
                // rotation vector are sufficient (see crbug.com/335298 for details).
                System.arraycopy(values, 0, mTruncatedRotationVector, 0, 4);
                SensorManager.getRotationMatrixFromVector(mDeviceRotationMatrix, mTruncatedRotationVector);
            } else {
                SensorManager.getRotationMatrixFromVector(mDeviceRotationMatrix, values);
            }
            float orientation[] = new float[3];
            SensorManager.getOrientation(mDeviceRotationMatrix, orientation);
            try {
                JSONObject eventData = new JSONObject();
                eventData.put("absolute", false);
                eventData.put("alpha", -orientation[0]);
                eventData.put("beta",  -orientation[1]);
                eventData.put("gamma", orientation[2]);
                webView.evaluateJS("window.Telegram.WebView.receiveEvent('" + "device_orientation_changed" + "', " + eventData + ");");
            } catch (Exception e) {}
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

}
