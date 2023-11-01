package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedFloat;

public class StoriesVolumeControl extends View {

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean isVisible;
    public StoriesVolumeControl(Context context) {
        super(context);
        paint.setColor(Color.WHITE);
    }

    Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            isVisible = false;
            invalidate();
        }
    };
    AnimatedFloat progressToVisible = new AnimatedFloat(this);
    AnimatedFloat volumeProgress = new AnimatedFloat(this);

    float currentProgress;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            adjustVolume(true);
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            adjustVolume(false);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    // unmutes only if muted
    public void unmute() {
        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int minVolume = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        }
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (currentVolume <= minVolume) {
            adjustVolume(true);
        } else if (!isVisible) {
            currentProgress = currentVolume / (float) maxVolume;
            volumeProgress.set(currentProgress, true);
            isVisible = true;
            invalidate();
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            AndroidUtilities.runOnUIThread(hideRunnable, 2000);
        }
    }

    private void adjustVolume(boolean increase) {
        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        int step = (int) Math.max(1, maxVolume / 15f);

        if (increase) {
            currentVolume += step;
            if (currentVolume > maxVolume) {
                currentVolume = maxVolume;
            }
        } else {
            currentVolume -= step;
            if (currentVolume < 0) {
                currentVolume = 0;
            }
        }


        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
        currentProgress = currentVolume / (float) maxVolume;
        if (!isVisible) {
            volumeProgress.set(currentProgress, true);
        }
        invalidate();
        isVisible = true;
        AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        AndroidUtilities.runOnUIThread(hideRunnable, 2000);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        volumeProgress.set(currentProgress);
        progressToVisible.set(isVisible ? 1 : 0);
        if (progressToVisible.get() != 0) {
            float rad =  getMeasuredHeight() / 2f;
            paint.setAlpha((int) (255 * progressToVisible.get()));
            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth() * volumeProgress.get(), getMeasuredHeight());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint);
        }
    }

    public void hide() {
        AndroidUtilities.cancelRunOnUIThread(hideRunnable);
        hideRunnable.run();
    }
}
