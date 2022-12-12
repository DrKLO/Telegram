package org.telegram.messenger;

// or use a ThreadPoolExecutor like in DispatchQueuePriority
public class MultiDispatchQueue extends Thread {

    // private final DispatchQueuePool dispatchQueuePool;


    private final int numQueues;
    private final DispatchQueue[] queues;
    private int curQueue = 0;

    // TODO replace by DispatchQueuePool

    public MultiDispatchQueue(String threadName, int concurrencyLevel) {
        super(threadName);

        //   dispatchQueuePool = new DispatchQueuePool(concurrencyLevel);

        numQueues = concurrencyLevel;

        queues = new DispatchQueue[numQueues];

        for(int i = 0; i < numQueues; i++) {
            queues[i] = new DispatchQueue(threadName + "-multi-" + i, true);
        }

    }

    public synchronized boolean postRunnable(Runnable runnable) {
        boolean ret = queues[curQueue].postRunnable(runnable);
        curQueue = (curQueue + 1) % numQueues;
        return ret;
        // dispatchQueuePool.execute(runnable);
        //  return true;
    }

    // TODO
    public synchronized boolean postRunnable(Runnable runnable, long delay) {
        boolean ret = queues[curQueue].postRunnable(runnable, delay);
        curQueue = (curQueue + 1) % numQueues;
        return ret;
        //  dispatchQueuePool.execute(runnable);
        // return true;
    }

    public void cancelRunnable(Runnable runnable) {
        //give it to all threads, one will work?
    }
}

