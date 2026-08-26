package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.telegram.ui.Components.ForegroundDetector;

public class ANRDetector implements ForegroundDetector.Listener {

    private static final long TIMEOUT_MS = 5000;

    private static final int MSG_UI_PING = 1;

    private final Object lock = new Object();

    private final Handler mainHandler;
    private final Thread detectorThread;
    private final Runnable anrDetected;

    private volatile boolean foreground;
    private volatile boolean destroyed;

    /**
     * Changes on every foreground/background transition.
     */
    private volatile int generation;

    /**
     * Monotonically increasing id of each UI ping.
     */
    private int nextPingId;

    /**
     * Last ping actually executed by the main thread.
     */
    private volatile int acknowledgedPingId = -1;

    /**
     * Prevents reporting the same freeze repeatedly.
     *
     * Reset only after the main thread becomes responsive again.
     */
    private volatile boolean anrReported;

    public ANRDetector(Runnable anrDetected) {
        this.anrDetected = anrDetected;

        mainHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what != MSG_UI_PING) {
                    return;
                }

                acknowledgedPingId = msg.arg1;

                /*
                 * If we previously detected an ANR, execution of this
                 * message means that the UI thread has recovered.
                 */
                anrReported = false;
            }
        };

        ForegroundDetector foregroundDetector = ForegroundDetector.getInstance();

        foreground = foregroundDetector.isForeground();

        foregroundDetector.addListener(this);

        detectorThread = new Thread(this::run, "ANRDetector");
        detectorThread.start();
    }

    private void run() {
        while (true) {
            final int checkGeneration;
            final int pingId;

            synchronized (lock) {
                /*
                 * Do absolutely nothing while the application is in
                 * background.
                 */
                while (!foreground && !destroyed) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ignore) {
                    }
                }

                if (destroyed) {
                    return;
                }

                checkGeneration = generation;
                pingId = ++nextPingId;
            }

            /*
             * Post a ping to the main thread.
             */
            Message message = mainHandler.obtainMessage(
                    MSG_UI_PING,
                    pingId,
                    checkGeneration
            );
            message.sendToTarget();

            try {
                Thread.sleep(TIMEOUT_MS);
            } catch (InterruptedException ignore) {
                /*
                 * Usually means foreground/background transition
                 * or destroy().
                 */
                continue;
            }

            if (destroyed) {
                return;
            }

            /*
             * Ignore this check if application state changed while
             * we were waiting.
             */
            if (!foreground || generation != checkGeneration) {
                continue;
            }

            /*
             * Main thread processed our ping in time.
             */
            if (acknowledgedPingId == pingId) {
                continue;
            }

            /*
             * UI thread did not process the ping within TIMEOUT_MS.
             *
             * Report only once until the UI actually recovers.
             */
            if (!anrReported) {
                anrReported = true;

                try {
                    anrDetected.run();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
    }

    @Override
    public void onBecameForeground() {
        synchronized (lock) {
            if (destroyed) {
                return;
            }

            generation++;
            foreground = true;

            /*
             * A previous ANR belongs to the previous foreground
             * session.
             */
            anrReported = false;

            lock.notifyAll();
        }

        /*
         * Interrupt any timeout that may belong to an old generation.
         */
        detectorThread.interrupt();
    }

    @Override
    public void onBecameBackground() {
        synchronized (lock) {
            if (destroyed) {
                return;
            }

            generation++;
            foreground = false;
        }

        /*
         * Old UI pings are no longer interesting.
         */
        mainHandler.removeMessages(MSG_UI_PING);

        /*
         * Wake the detector from Thread.sleep() immediately so it can
         * enter lock.wait() and consume no periodic CPU in background.
         */
        detectorThread.interrupt();
    }

    public void destroy() {
        synchronized (lock) {
            if (destroyed) {
                return;
            }

            destroyed = true;
            foreground = false;
            generation++;

            lock.notifyAll();
        }

        ForegroundDetector.getInstance().removeListener(this);

        mainHandler.removeMessages(MSG_UI_PING);

        detectorThread.interrupt();
    }
}