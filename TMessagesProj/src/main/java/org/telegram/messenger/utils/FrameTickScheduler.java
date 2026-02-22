package org.telegram.messenger.utils;

import android.view.Choreographer;

import org.telegram.messenger.AndroidUtilities;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class FrameTickScheduler {
    private static final Choreographer.FrameCallback callback = FrameTickScheduler::doFrame;
    private static final Map<Runnable, Sub> subs = new WeakHashMap<>();

    private static boolean running = false;
    private static long frameCounter = 0;

    public static int getFrameSparseness(int targetFps) {
        return normN(Math.round(AndroidUtilities.screenRefreshRate / targetFps));
    }

    public static void subscribe(Runnable r, int targetFps) {
        subscribe(r, getFrameSparseness(targetFps), 0);
    }

    public static void subscribe(Runnable r, int n, int i) {
        if (r == null || n <= 0) return;
        if (!subs.containsKey(r)) {
            subs.put(r, new Sub(r, normN(n), normI(i, n)));
            ensureRunning();
        }
    }

    public static void unsubscribe(Runnable r) {
        subs.remove(r);
        checkStop();
    }

    private static void doFrame(long frameTimeNanos) {
        frameCounter++;

        for (Sub s : subs.values()) {
            if ((frameCounter % s.n) == s.i) {
                s.action.run();
            }
        }

        if (running) {
            Choreographer.getInstance().postFrameCallback(callback);
        }
    }

    private static void ensureRunning() {
        if (!running) {
            running = true;
            Choreographer.getInstance().postFrameCallback(callback);
        }
    }

    private static void checkStop() {
        if (subs.isEmpty()) {
            running = false;
        }
    }

    private static int normN(int n) { return Math.max(1, n); }
    private static int normI(int i, int n) {
        int m = i % n;
        return (m < 0) ? (m + n) : m;
    }

    private static class Sub {
        final Runnable action;
        final int n, i;

        Sub(Runnable action, int n, int i) {
            this.action = action;
            this.n = n;
            this.i = i;
        }
    }

    private FrameTickScheduler() {

    }
}
