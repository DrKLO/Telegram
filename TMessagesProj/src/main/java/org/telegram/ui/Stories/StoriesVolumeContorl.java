package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedFloat;

public class StoriesVolumeContorl extends View {

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean isVisible;
    public StoriesVolumeContorl(Context context) {
        super(context);
        paint.setColor(Color.WHITE);
    }

    Runnable hideRunnuble = new Runnable() {
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
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            adjustVolume(true);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            adjustVolume(false);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void adjustVolume(boolean increase) {
        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        if (increase) {
            currentVolume++;
            if (currentVolume > maxVolume) {
                currentVolume = maxVolume;
            }
        } else {
            currentVolume--;
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
        AndroidUtilities.cancelRunOnUIThread(hideRunnuble);
        AndroidUtilities.runOnUIThread(hideRunnuble, 2000);
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
        AndroidUtilities.cancelRunOnUIThread(hideRunnuble);
        hideRunnuble.run();
    }
}
