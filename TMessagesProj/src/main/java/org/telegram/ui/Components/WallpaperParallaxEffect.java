/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2019.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;

public class WallpaperParallaxEffect implements SensorEventListener {

	private float[] rollBuffer = new float[3], pitchBuffer = new float[3];
	private int bufferOffset;
	private WindowManager wm;
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private boolean enabled;
	private Callback callback;

	public WallpaperParallaxEffect(Context context) {
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

	public float getScale(int boundsWidth, int boundsHeight) {
		int offset = AndroidUtilities.dp(16);
		return Math.max(((float) boundsWidth + offset * 2) / (float) boundsWidth, ((float) boundsHeight + offset * 2) / (float) boundsHeight);
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
		rollBuffer[bufferOffset] = roll;
		pitchBuffer[bufferOffset] = pitch;
		bufferOffset = (bufferOffset + 1) % rollBuffer.length;
		roll = pitch = 0;
		for (int i = 0; i < rollBuffer.length; i++) {
			roll += rollBuffer[i];
			pitch += pitchBuffer[i];
		}
		roll /= rollBuffer.length;
		pitch /= rollBuffer.length;
		if (roll > 1f) {
			roll = 2f - roll;
		} else if (roll < -1f) {
			roll = -2f - roll;
		}
		int offsetX = Math.round(pitch * AndroidUtilities.dpf2(16));
		int offsetY = Math.round(roll * AndroidUtilities.dpf2(16));
		if (callback != null)
			callback.onOffsetsChanged(offsetX, offsetY);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public interface Callback {
		void onOffsetsChanged(int offsetX, int offsetY);
	}
}
