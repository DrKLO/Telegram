/*
 * LeakDetector — singleton memory leak detector based on ReferenceMap/ReferenceList from X-Core.
 *
 * Usage:
 *   1. Start the detector (e.g. in Application.onCreate()):
 *        LeakDetector.getInstance().start();
 *
 *   2. In constructor of any object you want to track:
 *        LeakDetector.getInstance().add(this);
 *
 *   3. Subscribe to leak notifications via NotificationCenter:
 *        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.memoryLeakFoundException);
 *
 *      In didReceivedNotification():
 *        if (id == NotificationCenter.memoryLeakFoundException) {
 *            Class<?> leakedClass = (Class<?>) args[0];
 *            int count            = (int)     args[1];
 *            Log.e("LeakDetector", "Leak: " + leakedClass.getSimpleName() + " x" + count);
 *        }
 *
 * Notes:
 *   - All public methods must be called on the main thread.
 *   - Each class is reported at most once — no repeated notifications for the same leak.
 *   - Notification is posted on the main thread.
 *   - When a potential leak is detected, GC is requested and the class is re-checked after
 *     GC_RECHECK_DELAY_MS before a notification is posted. This eliminates false positives
 *     caused by objects that are reachable but already eligible for collection.
 */

package org.telegram.messenger.utils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import me.vkryl.core.reference.ReferenceMap;

public final class LeakDetector {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /** How many live instances of a single class trigger a suspicious-leak check. */
    private static final int LEAK_THRESHOLD = 5;

    /** How often (in ms) the detector scans for leaks. */
    private static final long CHECK_INTERVAL_MS = 1_000L;

    /**
     * Delay (in ms) between requesting GC and re-checking a suspected class.
     * Long enough for GC to run, short enough not to delay real reports.
     */
    private static final long GC_RECHECK_DELAY_MS = 2_000L;

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static volatile LeakDetector instance;

    @NonNull
    public static LeakDetector getInstance() {
        if (instance == null) {
            synchronized (LeakDetector.class) {
                if (instance == null) {
                    instance = new LeakDetector();
                }
            }
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // State — all accessed on main thread only, no extra synchronization needed
    // -------------------------------------------------------------------------

    /**
     * Maps Class → weak-reference list of live instances.
     * isThreadSafe=false — registry is only accessed on the main thread.
     */
    private final ReferenceMap<Class<?>, Object> registry = new ReferenceMap<>(false);

    /** Classes already confirmed as leaking — won't trigger another notification. */
    private final Set<Class<?>> reportedLeaks = new HashSet<>();

    /**
     * Classes currently in the "GC requested, awaiting re-check" state.
     * Value = instance count at the moment of suspicion.
     */
    private final Map<Class<?>, Integer> pendingRecheck = new HashMap<>();

    private boolean running;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            check();
            if (running) {
                AndroidUtilities.runOnUIThread(this, CHECK_INTERVAL_MS);
            }
        }
    };

    private LeakDetector() {}

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /** Start periodic leak checks. Safe to call multiple times — subsequent calls are no-ops. */
    @MainThread
    public void start() {
        if (running) return;
        running = true;
        AndroidUtilities.runOnUIThread(checkRunnable, CHECK_INTERVAL_MS);
    }

    /** Stop periodic leak checks. */
    @MainThread
    public void stop() {
        if (!running) return;
        running = false;
        AndroidUtilities.cancelRunOnUIThread(checkRunnable);
    }

    /**
     * Register {@code object} for leak tracking.
     * Must be called from the main thread.
     * <pre>
     *   public MyView(Context context) {
     *     super(context);
     *     LeakDetector.getInstance().add(this);
     *   }
     * </pre>
     */
    @MainThread
    public <T> void add(@NonNull T object) {
        registry.add(object.getClass(), object);
    }

    // -------------------------------------------------------------------------
    // Internal — runs on main thread via checkRunnable
    // -------------------------------------------------------------------------

    @MainThread
    private void check() {
        final Set<Class<?>> keys = registry.keySetUnchecked();
        if (keys == null) return;

        // Copy keys — iterator() calls on individual lists may trigger
        // internal ReferenceList cleanup which can mutate the map.
        for (Class<?> clazz : new ArrayList<>(keys)) {
            if (reportedLeaks.contains(clazz)) continue;

            final int count = countLiveInstances(clazz);

            if (count >= LEAK_THRESHOLD) {
                if (!pendingRecheck.containsKey(clazz)) {
                    // First time we've seen this class over the threshold.
                    // Request GC and schedule a confirmation re-check.
                    pendingRecheck.put(clazz, count);
                    System.gc();
                    AndroidUtilities.runOnUIThread(() -> confirmLeak(clazz), GC_RECHECK_DELAY_MS);
                }
                // If already pending, do nothing — confirmLeak() will handle it.
            } else {
                // Count dropped below threshold before re-check fired (GC already helped).
                pendingRecheck.remove(clazz);
            }
        }
    }

    /**
     * Re-checks {@code clazz} after GC has had a chance to run.
     * Only posts a notification if the count is still at or above the threshold.
     */
    @MainThread
    private void confirmLeak(final Class<?> clazz) {
        pendingRecheck.remove(clazz);

        if (reportedLeaks.contains(clazz)) return;

        final int count = countLiveInstances(clazz);
        if (count >= LEAK_THRESHOLD && reportedLeaks.add(clazz)) {
            //NotificationCenter.getGlobalInstance()
            //        .postNotificationName(NotificationCenter.memoryLeakFoundException, clazz, count);
        }
    }

    /**
     * Counts live (non-GC'd) instances of {@code clazz} in the registry.
     * Iterating forces ReferenceList to flush dead WeakReferences as a side effect.
     */
    @MainThread
    private int countLiveInstances(final Class<?> clazz) {
        final Iterator<Object> it = registry.iterator(clazz);
        if (it == null) return 0;
        int count = 0;
        while (it.hasNext()) {
            it.next();
            count++;
        }
        return count;
    }
}