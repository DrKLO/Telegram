package org.telegram.messenger.utils;

import org.telegram.messenger.AndroidUtilities;

public class CountdownTimer {
    private final Callback callback;
    private long seconds;
    private boolean isRunning;
    private final Runnable doUpdate = this::update;

    public CountdownTimer(Callback callback) {
        this.callback = callback;
    }

    public void start(long seconds) {
        if (isRunning && this.seconds == seconds) {
            return;
        }

        this.seconds = seconds;

        if (seconds <= 0) {
            stop();
            return;
        }

        isRunning = true;
        AndroidUtilities.cancelRunOnUIThread(doUpdate);
        AndroidUtilities.runOnUIThread(doUpdate, 1000);
    }

    public void stop() {
        isRunning = false;
        AndroidUtilities.cancelRunOnUIThread(doUpdate);
    }

    public boolean isRunning() {
        return isRunning;
    }



    private void update() {
        if (seconds > 0) {
            seconds -= 1;
            callback.onTimerUpdate(seconds);
        }

        if (seconds <= 0) {
            isRunning = false;
        }

        if (isRunning) {
            AndroidUtilities.runOnUIThread(doUpdate, 1000);
        }
    }

    public interface Callback {
        void onTimerUpdate(long seconds);
    }
}
