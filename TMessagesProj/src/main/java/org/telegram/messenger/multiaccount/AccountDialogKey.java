package org.telegram.messenger.multiaccount;

/**
 * Identity of a conversation in the unified interface. Dialog, user, chat and message ids are only
 * unique inside one account, so every cross-account structure must be keyed by the pair.
 */
public final class AccountDialogKey {

    public final int account;
    public final long dialogId;

    public AccountDialogKey(int account, long dialogId) {
        this.account = account;
        this.dialogId = dialogId;
    }

    /** Stable 64-bit id for list adapters; collisions only cost an animation, never a wrong row. */
    public long stableId() {
        return dialogId * 31 + account + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccountDialogKey)) {
            return false;
        }
        AccountDialogKey other = (AccountDialogKey) o;
        return account == other.account && dialogId == other.dialogId;
    }

    @Override
    public int hashCode() {
        return 31 * account + (int) (dialogId ^ (dialogId >>> 32));
    }

    @Override
    public String toString() {
        return account + ":" + dialogId;
    }
}
