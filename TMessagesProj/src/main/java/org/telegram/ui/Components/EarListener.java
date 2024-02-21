package org.telegram.ui.Components;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.PhotoViewer;

public class EarListener implements SensorEventListener {

    private final Context context;
    private final SensorManager sensorManager;
    private final PowerManager powerManager;
    private final AudioManager audioManager;

    private Sensor proximitySensor;
    private Sensor accelerometerSensor;
    private Sensor linearSensor;
    private Sensor gravitySensor;
    private PowerManager.WakeLock proximityWakeLock;

    public EarListener(@NonNull Context context) {
        this.context = context;

        sensorManager = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (linearSensor == null || gravitySensor == null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("gravity or linear sensor not found");
            }
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            linearSensor = null;
            gravitySensor = null;
        }

        powerManager = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
        proximityWakeLock = powerManager.newWakeLock(0x00000020, "telegram:proximity_lock2");

        audioManager = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
    }

    private boolean attached;
    public void attach() {
        if (!attached) {
            if (gravitySensor != null) {
                sensorManager.registerListener(this, gravitySensor, 30000);
            }
            if (linearSensor != null) {
                sensorManager.registerListener(this, linearSensor, 30000);
            }
            if (accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, 30000);
            }
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (proximityWakeLock != null && !disableWakeLockWhenNotUsed()) {
                proximityWakeLock.acquire();
            }
            attached = true;
        }
    }

    public void detach() {
        if (attached) {
            if (gravitySensor != null) {
                sensorManager.unregisterListener(this, gravitySensor);
            }
            if (linearSensor != null) {
                sensorManager.unregisterListener(this, linearSensor);
            }
            if (accelerometerSensor != null) {
                sensorManager.unregisterListener(this, accelerometerSensor);
            }
            sensorManager.unregisterListener(this, proximitySensor);
            if (proximityWakeLock != null && proximityWakeLock.isHeld()) {
                proximityWakeLock.release();
            }
            attached = false;
        }
    }

    private boolean raised;

    public boolean isRaised() {
        return raised;
    }

    private VideoPlayer currentPlayer;
    public void attachPlayer(VideoPlayer player) {
        currentPlayer = player;
        updateRaised();
    }

    protected void updateRaised() {
        if (currentPlayer == null) {
            return;
        }
        currentPlayer.setStreamType(raised ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
    }

    private boolean accelerometerVertical;
    private long lastAccelerometerDetected;
    private int raisedToTop;
    private int raisedToTopSign;
    private int raisedToBack;
    private int countLess;
    private long timeSinceRaise;
    private long lastTimestamp = 0;
    private boolean proximityTouched;
    private boolean proximityHasDifferentValues;
    private float lastProximityValue = -100;
    private float previousAccValue;
    private float[] gravity = new float[3];
    private float[] gravityFast = new float[3];
    private float[] linearAcceleration = new float[3];

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!attached || VoIPService.getSharedInstance() != null) {
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("proximity changed to " + event.values[0] + " max value = " + event.sensor.getMaximumRange());
            }
            if (lastProximityValue != event.values[0]) {
                proximityHasDifferentValues = true;
            }
            lastProximityValue = event.values[0];
            if (proximityHasDifferentValues) {
                proximityTouched = isNearToSensor(event.values[0]);
            }
        } else if (event.sensor == accelerometerSensor) {
            final double alpha = lastTimestamp == 0 ? 0.98f : 1.0 / (1.0 + (event.timestamp - lastTimestamp) / 1000000000.0);
            final float alphaFast = 0.8f;
            lastTimestamp = event.timestamp;
            gravity[0] = (float) (alpha * gravity[0] + (1.0 - alpha) * event.values[0]);
            gravity[1] = (float) (alpha * gravity[1] + (1.0 - alpha) * event.values[1]);
            gravity[2] = (float) (alpha * gravity[2] + (1.0 - alpha) * event.values[2]);
            gravityFast[0] = (alphaFast * gravity[0] + (1.0f - alphaFast) * event.values[0]);
            gravityFast[1] = (alphaFast * gravity[1] + (1.0f - alphaFast) * event.values[1]);
            gravityFast[2] = (alphaFast * gravity[2] + (1.0f - alphaFast) * event.values[2]);

            linearAcceleration[0] = event.values[0] - gravity[0];
            linearAcceleration[1] = event.values[1] - gravity[1];
            linearAcceleration[2] = event.values[2] - gravity[2];
        } else if (event.sensor == linearSensor) {
            linearAcceleration[0] = event.values[0];
            linearAcceleration[1] = event.values[1];
            linearAcceleration[2] = event.values[2];
        } else if (event.sensor == gravitySensor) {
            gravityFast[0] = gravity[0] = event.values[0];
            gravityFast[1] = gravity[1] = event.values[1];
            gravityFast[2] = gravity[2] = event.values[2];
        }
        final float minDist = 15.0f;
        final int minCount = 6;
        final int countLessMax = 10;
        if (event.sensor == linearSensor || event.sensor == gravitySensor || event.sensor == accelerometerSensor) {
            float val = gravity[0] * linearAcceleration[0] + gravity[1] * linearAcceleration[1] + gravity[2] * linearAcceleration[2];
            if (raisedToBack != minCount) {
                if (val > 0 && previousAccValue > 0 || val < 0 && previousAccValue < 0) {
                    boolean goodValue;
                    int sign;
                    if (val > 0) {
                        goodValue = val > minDist;
                        sign = 1;
                    } else {
                        goodValue = val < -minDist;
                        sign = 2;
                    }
                    if (raisedToTopSign != 0 && raisedToTopSign != sign) {
                        if (raisedToTop == minCount && goodValue) {
                            if (raisedToBack < minCount) {
                                raisedToBack++;
                                if (raisedToBack == minCount) {
                                    raisedToTop = 0;
                                    raisedToTopSign = 0;
                                    countLess = 0;
                                    timeSinceRaise = System.currentTimeMillis();
                                    if (BuildVars.LOGS_ENABLED && BuildVars.DEBUG_PRIVATE_VERSION) {
                                        FileLog.d("motion detected");
                                    }
                                }
                            }
                        } else {
                            if (!goodValue) {
                                countLess++;
                            }
                            if (countLess == countLessMax || raisedToTop != minCount || raisedToBack != 0) {
                                raisedToTop = 0;
                                raisedToTopSign = 0;
                                raisedToBack = 0;
                                countLess = 0;
                            }
                        }
                    } else {
                        if (goodValue && raisedToBack == 0 && (raisedToTopSign == 0 || raisedToTopSign == sign)) {
                            if (raisedToTop < minCount && !proximityTouched) {
                                raisedToTopSign = sign;
                                raisedToTop++;
                                if (raisedToTop == minCount) {
                                    countLess = 0;
                                }
                            }
                        } else {
                            if (!goodValue) {
                                countLess++;
                            }
                            if (raisedToTopSign != sign || countLess == countLessMax || raisedToTop != minCount || raisedToBack != 0) {
                                raisedToBack = 0;
                                raisedToTop = 0;
                                raisedToTopSign = 0;
                                countLess = 0;
                            }
                        }
                    }
                }
            }
            previousAccValue = val;
            accelerometerVertical = gravityFast[1] > 2.5f && Math.abs(gravityFast[2]) < 4.0f && Math.abs(gravityFast[0]) > 1.5f;
        }
        if (raisedToBack == minCount || accelerometerVertical) {
            lastAccelerometerDetected = System.currentTimeMillis();
        }
        final boolean accelerometerDetected = raisedToBack == minCount || accelerometerVertical || System.currentTimeMillis() - lastAccelerometerDetected < 60;
        final boolean wakelockAllowed = accelerometerDetected && !forbidRaiseToListen() && !VoIPService.isAnyKindOfCallActive() && !PhotoViewer.getInstance().isVisible();
        if (proximityWakeLock != null && disableWakeLockWhenNotUsed()) {
            final boolean held = proximityWakeLock.isHeld();
            if (held && !wakelockAllowed) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("wake lock releasing");
                }
                proximityWakeLock.release();
            } else if (!held && wakelockAllowed) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("wake lock acquiring");
                }
                proximityWakeLock.acquire();
            }
        }
        if (proximityTouched && wakelockAllowed) {
            if (!raised) {
                raised = true;
                updateRaised();
            }
            raisedToBack = 0;
            raisedToTop = 0;
            raisedToTopSign = 0;
            countLess = 0;
        } else if (proximityTouched && ((accelerometerSensor == null || linearSensor == null) && gravitySensor == null) && !VoIPService.isAnyKindOfCallActive()) {
            if (!raised) {
                raised = true;
                updateRaised();
            }
        } else if (!proximityTouched) {
            if (raised) {
                raised = false;
                updateRaised();
            }
        }
        if (timeSinceRaise != 0 && raisedToBack == minCount && Math.abs(System.currentTimeMillis() - timeSinceRaise) > 1000) {
            raisedToBack = 0;
            raisedToTop = 0;
            raisedToTopSign = 0;
            countLess = 0;
            timeSinceRaise = 0;
        }
    }

    private boolean isNearToSensor(float value) {
        return value < 5.0f && value != proximitySensor.getMaximumRange();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private boolean disableWakeLockWhenNotUsed() {
        // Pixel devices cap phone fps to 60 when wake lock is held
        // Samsung devices do much worse proximity detection when wake lock is not held

        // Solution: enable wake lock only when accelerometer detects raising, except for Samsung
        return !Build.MANUFACTURER.equalsIgnoreCase("samsung");
    }

    protected boolean forbidRaiseToListen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (AudioDeviceInfo device : devices) {
                    final int type = device.getType();
                    if ((
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    ) && device.isSink()) {
                        return true;
                    }
                }
                return false;
            } else {
                return audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
}
