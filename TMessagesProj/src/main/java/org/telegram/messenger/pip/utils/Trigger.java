package org.telegram.messenger.pip.utils;

import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.ApplicationLoader;

import java.util.concurrent.atomic.AtomicBoolean;

public class Trigger implements Runnable {

    public interface Callback {
        void run(boolean byTimeout);
    }

    private final Handler handler;
    private final Callback action;
    private final Runnable timeoutRunnable;
    private final AtomicBoolean triggered = new AtomicBoolean(false);

    private Trigger(Handler handler, Callback action, long timeoutMs) {
        this.handler = handler;
        this.action = action;
        this.timeoutRunnable = () -> {
            if (triggered.compareAndSet(false, true)) {
                action.run(true);
            }
        };

        if (timeoutMs > 0) {
            handler.postDelayed(timeoutRunnable, timeoutMs);
        }
    }

    public static Trigger run(Callback action, long timeoutMs) {
        return new Trigger(ApplicationLoader.applicationHandler, action, timeoutMs);
    }

    public static Trigger run(Handler handler, Callback action, long timeoutMs) {
        return new Trigger(handler, action, timeoutMs);
    }

    @Override
    public void run() {
        if (triggered.compareAndSet(false, true)) {
            handler.removeCallbacks(timeoutRunnable);
            if (Looper.myLooper() == handler.getLooper()) {
                action.run(false);
            } else {
                handler.post(() -> action.run(false));
            }
        }
    }

    public void cancel() {
        if (triggered.compareAndSet(false, true)) {
            handler.removeCallbacks(timeoutRunnable);
        }
    }
}
