package org.telegram;

import org.telegram.messenger.FileLog;

import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DispatchQueuePriority {

    private final ThreadPoolExecutor threadPoolExecutor;
    private volatile CountDownLatch pauseLatch;

    public DispatchQueuePriority(String threadName) {
        this.threadPoolExecutor = new ThreadPoolExecutor(
                1, 
                1, 
                60, 
                TimeUnit.SECONDS, 
                new PriorityBlockingQueue<>(10, new PriorityRunnableComparator())
        ) {
            @Override
            protected void beforeExecute(Thread t, Runnable r) {
                awaitPauseLatch();
            }
        };
    }

    private void awaitPauseLatch() {
        if (pauseLatch != null) {
            try {
                pauseLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                FileLog.e(e);
            }
        }
    }

    public static Runnable wrap(Runnable runnable, int priority) {
        return priority == 1 ? runnable : new PriorityRunnable(priority, runnable);
    }

    public void postRunnable(Runnable runnable) {
        threadPoolExecutor.execute(runnable);
    }

    public Runnable postRunnable(Runnable runnable, int priority) {
        if (priority != 1) {
            runnable = new PriorityRunnable(priority, runnable);
        }
        postRunnable(runnable);
        return runnable;
    }

    public void cancelRunnable(Runnable runnable) {
        if (runnable != null) {
            threadPoolExecutor.remove(runnable);
        }
    }

    public void pause() {
        if (pauseLatch == null) {
            pauseLatch = new CountDownLatch(1);
        }
    }

    public void resume() {
        CountDownLatch latch = pauseLatch;
        if (latch != null) {
            latch.countDown();
            pauseLatch = null; // Allow future pauses
        }
    }

    private static class PriorityRunnable implements Runnable {
        final int priority;
        final Runnable runnable;

        private PriorityRunnable(int priority, Runnable runnable) {
            this.priority = priority;
            this.runnable = runnable;
        }

        @Override
        public void run() {
            runnable.run();
        }
    }

    private static class PriorityRunnableComparator implements Comparator<Runnable> {
        @Override
        public int compare(Runnable o1, Runnable o2) {
            int priority1 = getPriority(o1);
            int priority2 = getPriority(o2);
            return Integer.compare(priority2, priority1); // Higher priority first
        }

        private int getPriority(Runnable runnable) {
            return (runnable instanceof PriorityRunnable)
                    ? ((PriorityRunnable) runnable).priority 
                    : 1; // Default priority
        }
    }
}
