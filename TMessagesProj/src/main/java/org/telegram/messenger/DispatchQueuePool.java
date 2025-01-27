package org.telegram.messenger;

import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseIntArray;

import androidx.annotation.UiThread;

import org.telegram.ui.Components.Reactions.HwEmojis;

import java.util.LinkedList;

public class DispatchQueuePool {

    private final LinkedList<DispatchQueue> queues = new LinkedList<>();
    private final SparseIntArray busyQueuesMap = new SparseIntArray();
    private final LinkedList<DispatchQueue> busyQueues = new LinkedList<>();
    private int maxCount;
    private int createdCount;
    private int guid;
    private int totalTasksCount;
    private boolean cleanupScheduled;

    private final Runnable cleanupRunnable = new Runnable() {
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(() -> execute(runnable));
            return;
        }
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
        int count = busyQueuesMap.get(queue.index, 0);
        busyQueuesMap.put(queue.index, count + 1);
        if (HwEmojis.isHwEnabled()) {
            queue.setPriority(Thread.MIN_PRIORITY);
        } else if (queue.getPriority() != Thread.MAX_PRIORITY) {
            queue.setPriority(Thread.MAX_PRIORITY);
        }
        queue.postRunnable(() -> {
            runnable.run();
            AndroidUtilities.runOnUIThread(() -> {
                totalTasksCount--;
                int remainingTasksCount = busyQueuesMap.get(queue.index) - 1;
                if (remainingTasksCount == 0) {
                    busyQueuesMap.delete(queue.index);
                    busyQueues.remove(queue);
                    queues.add(queue);
                } else {
                    busyQueuesMap.put(queue.index, remainingTasksCount);
                }
            });
        });
    }
}
