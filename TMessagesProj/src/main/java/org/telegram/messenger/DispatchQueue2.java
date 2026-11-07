package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * DispatchQueue that never blocks the posting thread while its own thread is starting:
 * tasks posted before the looper is ready are kept in a pending list and handed over
 * to the handler, in the same order and with the same timing, once the thread runs.
 * Only {@link #getHandlerSync()} waits for the thread.
 */
public class DispatchQueue2 extends Thread {

    private static final int THREAD_PRIORITY_DEFAULT = -1000;

    private static final int PENDING_RUNNABLE = 0;
    private static final int PENDING_FRONT_RUNNABLE = 1;
    private static final int PENDING_MESSAGE = 2;

    private static class Pending {
        final int type;
        final Runnable runnable;
        final Message message;
        final long uptime;

        Pending(int type, Runnable runnable, Message message, long uptime) {
            this.type = type;
            this.runnable = runnable;
            this.message = message;
            this.uptime = uptime;
        }
    }

    private static int indexPointer = 0;
    public final int index = indexPointer++;

    // published under lock right after the pending tasks are handed over, so a non-null handler
    // means every task posted before it is already in the looper's queue
    private volatile Handler handler;
    private final Object lock = new Object();
    private ArrayList<Pending> pending = new ArrayList<>(); // guarded by lock, null once the handler is published
    private boolean quitRequested; // guarded by lock
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    private volatile long lastTaskTime;
    private final int threadPriority;

    public DispatchQueue2(final String threadName) {
        this(threadName, true, THREAD_PRIORITY_DEFAULT);
    }

    public DispatchQueue2(final String threadName, boolean start) {
        this(threadName, start, THREAD_PRIORITY_DEFAULT);
    }

    public DispatchQueue2(final String threadName, boolean start, int priority) {
        this.threadPriority = priority;
        setName(threadName);
        if (start) {
            start();
        }
    }

    public boolean postRunnable(Runnable runnable) {
        lastTaskTime = SystemClock.elapsedRealtime();
        return postRunnable(runnable, 0);
    }

    public boolean postRunnable(Runnable runnable, long delay) {
        final Handler h = handler;
        if (h != null) {
            return delay <= 0 ? h.post(runnable) : h.postDelayed(runnable, delay);
        }
        final long uptime = SystemClock.uptimeMillis() + Math.max(0, delay);
        synchronized (lock) {
            if (pending != null) {
                pending.add(new Pending(PENDING_RUNNABLE, runnable, null, uptime));
                return true;
            }
        }
        return handler.postAtTime(runnable, uptime);
    }

    public boolean postToFrontRunnable(Runnable runnable) {
        final Handler h = handler;
        if (h != null) {
            return h.postAtFrontOfQueue(runnable);
        }
        synchronized (lock) {
            if (pending != null) {
                pending.add(new Pending(PENDING_FRONT_RUNNABLE, runnable, null, 0));
                return true;
            }
        }
        return handler.postAtFrontOfQueue(runnable);
    }

    public void sendMessage(Message msg, int delay) {
        final Handler h = handler;
        if (h != null) {
            h.sendMessageDelayed(msg, Math.max(0, delay));
            return;
        }
        final long uptime = SystemClock.uptimeMillis() + Math.max(0, delay);
        synchronized (lock) {
            if (pending != null) {
                pending.add(new Pending(PENDING_MESSAGE, null, msg, uptime));
                return;
            }
        }
        handler.sendMessageAtTime(msg, uptime);
    }

    public void cancelRunnable(Runnable runnable) {
        final Handler h = handler;
        if (h != null) {
            h.removeCallbacks(runnable);
            return;
        }
        synchronized (lock) {
            if (pending != null) {
                for (int i = pending.size() - 1; i >= 0; i--) {
                    if (pending.get(i).runnable == runnable) {
                        pending.remove(i);
                    }
                }
                return;
            }
        }
        handler.removeCallbacks(runnable);
    }

    public void cancelRunnables(Runnable[] runnables) {
        for (int i = 0; i < runnables.length; i++) {
            cancelRunnable(runnables[i]);
        }
    }

    public void cleanupQueue() {
        final Handler h = handler;
        if (h != null) {
            h.removeCallbacksAndMessages(null);
            return;
        }
        synchronized (lock) {
            if (pending != null) {
                pending.clear();
                return;
            }
        }
        handler.removeCallbacksAndMessages(null);
    }

    public void handleMessage(Message inputMessage) {

    }

    public long getLastTaskTime() {
        return lastTaskTime;
    }

    public void recycle() {
        final Handler h = handler;
        if (h != null) {
            h.getLooper().quit();
            return;
        }
        synchronized (lock) {
            if (pending != null) {
                quitRequested = true;
                return;
            }
        }
        handler.getLooper().quit();
    }

    public boolean isReady() {
        return handler != null;
    }

    // blocks until the thread is started, never call it from the main thread before the queue is surely ready
    public Handler getHandlerSync() {
        try {
            readyLatch.await();
        } catch (Exception e) {
            FileLog.e(e, false);
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        final Handler h = new Handler(Looper.myLooper(), msg -> {
            DispatchQueue2.this.handleMessage(msg);
            return true;
        });
        boolean quit;
        synchronized (lock) {
            for (int i = 0; i < pending.size(); i++) {
                final Pending p = pending.get(i);
                if (p.type == PENDING_RUNNABLE) {
                    h.postAtTime(p.runnable, p.uptime);
                } else if (p.type == PENDING_FRONT_RUNNABLE) {
                    h.postAtFrontOfQueue(p.runnable);
                } else {
                    h.sendMessageAtTime(p.message, p.uptime);
                }
            }
            pending = null;
            quit = quitRequested;
            handler = h;
        }
        readyLatch.countDown();
        if (quit) {
            Looper.myLooper().quit();
        }
        if (threadPriority != THREAD_PRIORITY_DEFAULT) {
            Process.setThreadPriority(threadPriority);
        }
        Looper.loop();
    }
}
