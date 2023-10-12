package org.telegram.messenger;

import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class CacheFetcher<Args, R> {

    protected void getRemote(int currentAccount, Args arguments, long hash, Utilities.Callback4<Boolean, R, Long, Boolean> onResult) {
        // Implement this function
    }

    // Not specifying getLocal and setLocal would mean that data is cached only in RAM
    protected void getLocal(int currentAccount, Args arguments, Utilities.Callback2<Long, R> onResult) {
        // Implement this function
        onResult.run(0L, null);
    }

    protected void setLocal(int currentAccount, Args arguments, R data, long hash) {
        // Implement this function
    }

    protected boolean useCache(Args arguments) {
        return true;
    }

    private final long requestRemotelyTimeout = 4 * 60 * 1000;

    private HashMap<Pair<Integer, Args>, R> cachedResults;
    private HashMap<Pair<Integer, Args>, ArrayList<Utilities.Callback<R>>> loadingCallbacks;
    private HashMap<Pair<Integer, Args>, Long> lastRequestedRemotely;

    public void fetch(int currentAccount, Args arguments, Utilities.Callback<R> onResult) {
        final Pair<Integer, Args> key = new Pair<>(currentAccount, arguments);

        if (isLoading(key)) {
            saveCallback(key, onResult);
            return;
        }

        R cached = getCachedResult(key);
        if (cached != null && !shouldRequest(key)) {
            if (onResult != null) {
                onResult.run(cached);
            }
            return;
        }

        saveCallback(key, onResult);
        getLocal(currentAccount, arguments, (hash, data) -> {
            if (shouldRequest(key)) {
                getRemote(currentAccount, arguments, hash, (notModified, remoteData, newHash, requestSuccess) -> {
                    if (requestSuccess) {
                        saveLastRequested(key);
                    }
                    if (notModified) {
                        cacheResult(key, data);
                        callCallbacks(key, data);
                    } else {
                        if (remoteData != null) {
                            setLocal(currentAccount, arguments, remoteData, newHash);
                            cacheResult(key, remoteData);
                        }
                        callCallbacks(key, remoteData);
                    }
                });
            } else {
                cacheResult(key, data);
                callCallbacks(key, data);
            }
        });
    }

    private R getCachedResult(Pair<Integer, Args> key) {
        if (cachedResults == null) {
            return null;
        }
        return cachedResults.get(key);
    }

    private void cacheResult(Pair<Integer, Args> key, R result) {
        if (!useCache(key.second)) {
            return;
        }
        if (cachedResults == null) {
            cachedResults = new HashMap<>();
        }
        cachedResults.put(key, result);
    }

    private void saveLastRequested(Pair<Integer, Args> key) {
        if (lastRequestedRemotely == null) {
            lastRequestedRemotely = new HashMap<>();
        }
        lastRequestedRemotely.put(key, System.currentTimeMillis());
    }

    private boolean shouldRequest(Pair<Integer, Args> key) {
        Long lastRequested = lastRequestedRemotely != null ? lastRequestedRemotely.get(key) : null;
        return lastRequested == null || System.currentTimeMillis() - lastRequested >= requestRemotelyTimeout;
    }

    public void forceRequest(int currentAccount, Args args) {
        if (lastRequestedRemotely == null) {
            return;
        }
        lastRequestedRemotely.remove(new Pair<>(currentAccount, args));
    }

    private boolean isLoading(Pair<Integer, Args> key) {
        return loadingCallbacks != null && loadingCallbacks.get(key) != null;
    }

    private void saveCallback(Pair<Integer, Args> key, Utilities.Callback<R> callback) {
        if (callback == null) {
            return;
        }
        if (loadingCallbacks == null) {
            loadingCallbacks = new HashMap<>();
        }
        ArrayList<Utilities.Callback<R>> callbacks = loadingCallbacks.get(key);
        if (callbacks == null) {
            loadingCallbacks.put(key, callbacks = new ArrayList<>());
        }
        callbacks.add(callback);
    }

    private void callCallbacks(Pair<Integer, Args> key, R result) {
        if (loadingCallbacks == null) {
            return;
        }

        final ArrayList<Utilities.Callback<R>> callbacks = loadingCallbacks.get(key);
        if (callbacks == null) {
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            for (Utilities.Callback<R> callback: callbacks) {
                callback.run(result);
            }
            callbacks.clear();
        });

        loadingCallbacks.remove(key);
    }
}