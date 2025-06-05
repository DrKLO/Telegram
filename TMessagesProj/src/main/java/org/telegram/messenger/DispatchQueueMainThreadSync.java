package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.util.ArrayList;

public class DispatchQueueMainThreadSync extends Thread {

    private volatile Handler handler = null;
    private boolean isRunning;
    private boolean isRecycled;
    private long lastTaskTime;
    private static int indexPointer = 0;
    public final int index = indexPointer++;
    private ArrayList<PostponedTask> postponedTasks = new ArrayList<>();

    public DispatchQueueMainThreadSync(final String threadName) {
        this(threadName, true);
    }

    public DispatchQueueMainThreadSync(final String threadName, boolean start) {
        setName(threadName);
        if (start) {
            start();
        }
    }

    public void sendMessage(Message msg, int delay) {
       checkThread();
        if (isRecycled) {
            return;
        }
        if (!isRunning) {
            postponedTasks.add(new PostponedTask(msg, delay));
            return;
        }
        if (delay <= 0) {
            handler.sendMessage(msg);
        } else {
            handler.sendMessageDelayed(msg, delay);
        }
    }

    private void checkThread() {
        if (BuildVars.DEBUG_PRIVATE_VERSION && Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
//            throw new IllegalStateException("Disaptch thread");
        }
    }

    public void cancelRunnable(Runnable runnable) {
        checkThread();
        if (isRunning) {
            handler.removeCallbacks(runnable);
        } else {
            for (int i = 0; i < postponedTasks.size(); i++) {
                if (postponedTasks.get(i).runnable == runnable) {
                    postponedTasks.remove(i);
                    i--;
                }
            }
        }
    }

    public void cancelRunnables(Runnable[] runnables) {
        checkThread();
        for (int i = 0; i < runnables.length; i++) {
            cancelRunnable(runnables[i]);
        }
    }

    public boolean postRunnable(Runnable runnable) {
        checkThread();
        lastTaskTime = SystemClock.elapsedRealtime();
        return postRunnable(runnable, 0);
    }

    public boolean postRunnable(Runnable runnable, long delay) {
        checkThread();
        if (isRecycled) {
            return false;
        }
        if (!isRunning) {
            postponedTasks.add(new PostponedTask(runnable, delay));
            return true;
        }
        if (delay <= 0) {
            return handler.post(runnable);
        } else {
            return handler.postDelayed(runnable, delay);
        }
    }

    public void cleanupQueue() {
        checkThread();
        postponedTasks.clear();
        handler.removeCallbacksAndMessages(null);
    }

    public void handleMessage(Message inputMessage) {

    }

    public long getLastTaskTime() {
        return lastTaskTime;
    }

    public void recycle() {
        checkThread();
        postRunnable(() -> {
            handler.getLooper().quit();
        });
        isRecycled = true;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler(Looper.myLooper(), msg -> {
            DispatchQueueMainThreadSync.this.handleMessage(msg);
            return true;
        });
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                isRunning = true;
                for (int i = 0; i < postponedTasks.size(); i++) {
                    postponedTasks.get(i).run();
                }
                postponedTasks.clear();
            }
        });
        Looper.loop();
    }

    public boolean isReady() {
        return isRunning;
    }

    public Handler getHandler() {
        return handler;
    }

    private class PostponedTask {
        Message message;
        Runnable runnable;
        long delay;

        public PostponedTask(Message msg, int delay) {
            this.message = msg;
            this.delay = delay;
        }

        public PostponedTask(Runnable runnable, long delay) {
            this.runnable = runnable;
            this.delay = delay;
        }

        public void run() {
            if (runnable != null) {
                postRunnable(runnable, delay);
            } else {
                sendMessage(message, (int) delay);
            }
        }
    }
}
