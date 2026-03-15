package org.telegram.messenger.auto;

import android.util.SparseIntArray;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.ConnectionsManager;

final class AutoSessionConnectionLease {

    private static final Object LOCK = new Object();
    private static final SparseIntArray ACCOUNT_LEASES = new SparseIntArray();

    private final int currentAccount;
    private boolean acquired;

    AutoSessionConnectionLease(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    void acquire() {
        synchronized (LOCK) {
            if (acquired) {
                return;
            }
            int activeLeases = ACCOUNT_LEASES.get(currentAccount, 0);
            ACCOUNT_LEASES.put(currentAccount, activeLeases + 1);
            acquired = true;
            ApplicationLoader.externalInterfacePaused = false;
            if (activeLeases == 0) {
                ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
            }
        }
    }

    void release() {
        synchronized (LOCK) {
            if (!acquired) {
                return;
            }
            int activeLeases = Math.max(0, ACCOUNT_LEASES.get(currentAccount, 0) - 1);
            if (activeLeases == 0) {
                ACCOUNT_LEASES.delete(currentAccount);
            } else {
                ACCOUNT_LEASES.put(currentAccount, activeLeases);
            }
            acquired = false;
            if (!hasAnyLease()) {
                ApplicationLoader.externalInterfacePaused = true;
            }
            if (activeLeases == 0 && ApplicationLoader.mainInterfacePaused) {
                ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
            }
        }
    }

    private static boolean hasAnyLease() {
        return ACCOUNT_LEASES.size() > 0;
    }
}
