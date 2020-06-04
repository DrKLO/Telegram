package org.telegram.messenger;

import android.os.SystemClock;

import java.util.HashMap;
import java.util.LinkedList;

import androidx.annotation.UiThread;

public class DispatchQueuePool {

    private LinkedList<DispatchQueue> queues = new LinkedList<>();
    private HashMap<DispatchQueue, Integer> busyQueuesMap = new HashMap<>();
    private LinkedList<DispatchQueue> busyQueues = new LinkedList<>();
    private int maxCount;
    private int createdCount;
    private int guid;
    private int totalTasksCount;
    private boolean cleanupScheduled;

    private Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            if (!queues.isEmpty()) {
                long currentTime = SystemClock.elapsedRealtime();
                for (int a = 0, N = queues.size(); a < N; a++) {
                    DispatchQueue queue = queues.get(a);
                    if (queue.getLastTaskTime() < currentTime - 30000) {
                        queue.recycle();
                        queues.remove(a);
                        createdCount--;
                        a--;
                        N--;
                    }
                }
            }
            if (!queues.isEmpty() || !busyQueues.isEmpty()) {
                AndroidUtilities.runOnUIThread(this, 30000);
                cleanupScheduled = true;
            } else {
                cleanupScheduled = false;
            }
        }
    };

    public DispatchQueuePool(int count) {
        maxCount = count;
        guid = Utilities.random.nextInt();
    }

    @UiThread
    public void execute(Runnable runnable) {
        DispatchQueue queue;
        if (!busyQueues.isEmpty() && (totalTasksCount / 2 <= busyQueues.size() || queues.isEmpty() && createdCount >= maxCount)) {
            queue = busyQueues.remove(0);
        } else if (queues.isEmpty()) {
            queue = new DispatchQueue("DispatchQueuePool" + guid + "_" + Utilities.random.nextInt());
            queue.setPriority(Thread.MAX_PRIORITY);
            createdCount++;
        } else {
            queue = queues.remove(0);
        }
        if (!cleanupScheduled) {
            AndroidUtilities.runOnUIThread(cleanupRunnable, 30000);
            cleanupScheduled = true;
        }
        totalTasksCount++;
        busyQueues.add(queue);
        Integer count = busyQueuesMap.get(queue);
        if (count == null) {
            count = 0;
        }
        busyQueuesMap.put(queue, count + 1);
        queue.postRunnable(() -> {
            runnable.run();
            AndroidUtilities.runOnUIThread(() -> {
                totalTasksCount--;
                int remainingTasksCount = busyQueuesMap.get(queue) - 1;
                if (remainingTasksCount == 0) {
                    busyQueuesMap.remove(queue);
                    busyQueues.remove(queue);
                    queues.add(queue);
                } else {
                    busyQueuesMap.put(queue, remainingTasksCount);
                }
            });
        });
    }
}
