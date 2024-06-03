package org.telegram.messenger;

public class AnimationNotificationsLocker {

    int currentAccount = UserConfig.selectedAccount;
    int notificationsIndex = -1;
    int globalNotificationsIndex = -1;

    boolean disabled;

    final int[] allowedNotifications;

    public AnimationNotificationsLocker() {
        this(null);
    }

    public AnimationNotificationsLocker(int[] allowedNotifications) {
        this.allowedNotifications = allowedNotifications;
    }

    public void lock() {
        if (disabled) {
            return;
        }
        int currentAccount = UserConfig.selectedAccount;
        if (this.currentAccount != currentAccount) {
            NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex);
            notificationsIndex = -1;
            this.currentAccount = currentAccount;
        }
        notificationsIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(notificationsIndex, allowedNotifications);
        globalNotificationsIndex = NotificationCenter.getGlobalInstance().setAnimationInProgress(globalNotificationsIndex, allowedNotifications);
    }

    public void unlock() {
        if (disabled) {
            return;
        }
        NotificationCenter.getInstance(currentAccount).onAnimationFinish(notificationsIndex);
        NotificationCenter.getGlobalInstance().onAnimationFinish(globalNotificationsIndex);
    }

    public void disable() {
        disabled = true;
    }

}
