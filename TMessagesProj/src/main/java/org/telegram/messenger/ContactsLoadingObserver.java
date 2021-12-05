package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;

public final class ContactsLoadingObserver {

    public static void observe(Callback callback, long expirationTime) {
        new ContactsLoadingObserver(callback).start(expirationTime);
    }

    public interface Callback {
        void onResult(boolean contactsLoaded);
    }

    private final NotificationCenter.NotificationCenterDelegate observer = (id, account, args) -> {
        if (id == NotificationCenter.contactsDidLoad) {
            onContactsLoadingStateUpdated(account, false);
        }
    };

    private final Handler handler;
    private final Callback callback;
    private final NotificationCenter notificationCenter;
    private final ContactsController contactsController;
    private final Runnable releaseRunnable;
    private final int currentAccount;

    private boolean released;

    private ContactsLoadingObserver(Callback callback) {
        this.callback = callback;
        currentAccount = UserConfig.selectedAccount;
        releaseRunnable = () -> onContactsLoadingStateUpdated(currentAccount, true);
        contactsController = ContactsController.getInstance(currentAccount);
        notificationCenter = NotificationCenter.getInstance(currentAccount);
        handler = new Handler(Looper.myLooper());
    }

    public void start(long expirationTime) {
        if (!onContactsLoadingStateUpdated(currentAccount, false)) {
            notificationCenter.addObserver(observer, NotificationCenter.contactsDidLoad);
            handler.postDelayed(releaseRunnable, expirationTime);
        }
    }

    public void release() {
        if (!released) {
            if (notificationCenter != null) {
                notificationCenter.removeObserver(observer, NotificationCenter.contactsDidLoad);
            }
            if (handler != null) {
                handler.removeCallbacks(releaseRunnable);
            }
            released = true;
        }
    }

    private boolean onContactsLoadingStateUpdated(int account, boolean force) {
        if (!released) {
            final boolean contactsLoaded = contactsController.contactsLoaded;
            if (contactsLoaded || force) {
                release();
                callback.onResult(contactsLoaded);
                return true;
            }
        }
        return false;
    }
}
