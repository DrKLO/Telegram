package org.telegram.messenger;

import android.os.SystemClock;
import android.util.SparseIntArray;

import androidx.annotation.UiThread;

import org.telegram.ui.Components.Reactions.HwEmojis;

import java.util.ArrayList;

public class DispatchQueuePoolBackground {

    private ArrayList<DispatchQueue> queues = new ArrayList<>(10);
    private SparseIntArray busyQueuesMap = new SparseIntArray();
    private ArrayList<DispatchQueue> busyQueues = new ArrayList<>(10);
    private int maxCount;
    private int createdCount;
    private int guid;
    private int totalTasksCount;
    private boolean cleanupScheduled;

    public static final String THREAD_PREFIX = "DispatchQueuePoolThreadSafety_";

    static ArrayList<Runnable> updateTaskCollection;
    private static DispatchQueuePoolBackground backgroundQueue;


    private Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            if (!queues.isEmpty()) {
                long currentTime = SystemClock.elapsedRealtime();
                for (int a = 0; a < queues.size(); a++) {
                    DispatchQueue queue = queues.get(a);
                    if (queue.getLastTaskTime() < currentTime - 30000) {
                        queue.recycle();
                        queues.remove(a);
                        createdCount--;
                        a--;
                    }
                }
            }
            if (!queues.isEmpty() || !busyQueues.isEmpty()) {
                Utilities.globalQueue.postRunnable(this, 30000);
                cleanupScheduled = true;
            } else {
                cleanupScheduled = false;
            }
        }
    };

    private DispatchQueuePoolBackground(int count) {
        maxCount = count;
        guid = Utilities.random.nextInt();
    }

    private void execute(ArrayList<Runnable> runnables) {
        for (int i = 0; i < runnables.size(); i++) {
            Runnable runnable = runnables.get(i);
            if (runnable == null) {
                continue;
            }
            DispatchQueue queue;
            if (!busyQueues.isEmpty() && (totalTasksCount / 2 <= busyQueues.size() || queues.isEmpty() && createdCount >= maxCount)) {
                queue = busyQueues.remove(0);
            } else if (queues.isEmpty()) {
                queue = new DispatchQueue(THREAD_PREFIX + guid + "_" + Utilities.random.nextInt());
                queue.setPriority(Thread.MAX_PRIORITY);
                createdCount++;
            } else {
                queue = queues.remove(0);
            }
            if (!cleanupScheduled) {
                Utilities.globalQueue.postRunnable(cleanupRunnable, 30000);
                cleanupScheduled = true;
            }
            totalTasksCount++;
            busyQueues.add(queue);
            int count = busyQueuesMap.get(queue.index, 0);
            busyQueuesMap.put(queue.index, count + 1);
            if(HwEmojis.isHwEnabled()) {
                queue.setPriority(Thread.MIN_PRIORITY);
            } else if (queue.getPriority() != Thread.MAX_PRIORITY) {
                queue.setPriority(Thread.MAX_PRIORITY);
            }
            queue.postRunnable(() -> {
                runnable.run();
                Utilities.globalQueue.postRunnable(() -> {
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

    private final static ArrayList<ArrayList<Runnable>> freeCollections = new ArrayList<>();

    private static final Runnable finishCollectUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            finishCollectUpdateRunnables();
        }
    };

    @UiThread
    public static void execute(Runnable runnable) {
        execute(runnable, false);
    }
    @UiThread
    public static void execute(Runnable runnable, boolean now) {
        if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
            if (BuildVars.DEBUG_VERSION) {
                FileLog.e(new RuntimeException("wrong thread"));
            }
            return;
        }
        if (updateTaskCollection == null) {
            if (!freeCollections.isEmpty()) {
                updateTaskCollection = freeCollections.remove(freeCollections.size() - 1);
            } else {
                updateTaskCollection = new ArrayList<>(100);
            }
            if (!now) {
                AndroidUtilities.runOnUIThread(finishCollectUpdateRunnable);
            }
        }

        updateTaskCollection.add(runnable);
        if (now) {
            AndroidUtilities.cancelRunOnUIThread(finishCollectUpdateRunnable);
            finishCollectUpdateRunnable.run();
        }
    }

    private static void finishCollectUpdateRunnables() {
        if (updateTaskCollection == null || updateTaskCollection.isEmpty()) {
            updateTaskCollection = null;
            return;
        }
        ArrayList<Runnable> arrayList = updateTaskCollection;
        updateTaskCollection = null;
        if (backgroundQueue == null) {
            backgroundQueue = new DispatchQueuePoolBackground(Math.max(1, Runtime.getRuntime().availableProcessors()));
        }
        Utilities.globalQueue.postRunnable(() -> {
            backgroundQueue.execute(arrayList);
            arrayList.clear();
            AndroidUtilities.runOnUIThread(() -> {
                freeCollections.add(arrayList);
            });
        });

    }
}
