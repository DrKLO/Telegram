package org.telegram.messenger;

public class MultiDispatchQueue extends Thread {

    private final DispatchQueuePool dispatchQueuePool;

    public MultiDispatchQueue(String threadName, int concurrencyLevel) {
        super(threadName);

        dispatchQueuePool = new DispatchQueuePool(concurrencyLevel);
    }

    public synchronized boolean postRunnable(Runnable runnable) {
        dispatchQueuePool.execute(runnable);
        return true;
    }

    public synchronized boolean postRunnable(Runnable runnable, long delay) {
        dispatchQueuePool.execute(runnable, delay);
        return true;
    }

    public void cancelRunnable(Runnable runnable) {
        dispatchQueuePool.cancelRunnable(runnable);
    }
}
