package org.telegram.ui.Components;

import org.telegram.messenger.AndroidUtilities;

import java.util.HashMap;
import java.util.Map;

public class PostRunnableHolder {
    private final HashMap<Runnable, Runnable> wrappedRunnable = new HashMap<>();

    public void post(Runnable runnable) {
        post(runnable, 0);
    }

    public void post(Runnable runnable, long delay) {
        cancel(runnable);
        Runnable wrapper = () -> {
            runnable.run();
            wrappedRunnable.remove(runnable);
        };

        wrappedRunnable.put(runnable, wrapper);
        if (delay > 0) {
            AndroidUtilities.runOnUIThread(wrapper, delay);
        } else {
            AndroidUtilities.runOnUIThread(wrapper);
        }
    }

    public void cancel(Runnable runnable) {
        Runnable wrapper = wrappedRunnable.remove(runnable);
        if (wrapper != null) {
            AndroidUtilities.cancelRunOnUIThread(wrapper);
        }
    }

    public void clear() {
        for (Map.Entry<Runnable, Runnable> entry : wrappedRunnable.entrySet()) {
            AndroidUtilities.cancelRunOnUIThread(entry.getValue());
        }
        wrappedRunnable.clear();
    }
}
