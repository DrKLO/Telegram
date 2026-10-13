package org.telegram.messenger.multiaccount;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Coordinates the "all accounts behave like one" mode.
 *
 * <p>Every logged-in account already owns an isolated set of controllers (connections, storage,
 * notifications, file loaders). This class only decides which of them are kept online and which
 * account is the <i>routing account</i>:
 * <ul>
 *     <li>{@link #getPrimaryAccount()} is the account the user explicitly chose. Settings, contacts and
 *     profile tabs use it, and it is the only value persisted as {@code selectedAccount}.</li>
 *     <li>{@link UserConfig#selectedAccount} follows the account of the screen on top of the navigation
 *     stack. A lot of legacy UI code reads {@code selectedAccount} directly, so keeping it equal to the
 *     account of the visible chat makes alerts, sheets, media viewers and child screens act on behalf of
 *     the right account without switching the whole interface.</li>
 * </ul>
 * All methods must be called on the UI thread.
 */
public final class MultiAccountController {

    private static final String PREF_ENABLED = "multiAccountUnified";

    private static volatile MultiAccountController instance;

    public static MultiAccountController getInstance() {
        MultiAccountController local = instance;
        if (local == null) {
            synchronized (MultiAccountController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new MultiAccountController();
                }
            }
        }
        return local;
    }

    private int primaryAccount = -1;
    private boolean foreground;
    private final boolean[] keptAlive = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private MultiAccountController() {
    }

    /** Called once after all {@link UserConfig}s are loaded. */
    public void init() {
        primaryAccount = UserConfig.selectedAccount;
        ensurePrimaryValid();
    }

    public static boolean isUnifiedEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_ENABLED, true);
    }

    public static void setUnifiedEnabled(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_ENABLED, enabled).apply();
        getInstance().onAccountsChanged();
    }

    /** Unified mode is in effect: enabled and at least two accounts are logged in. */
    public static boolean isActive() {
        return isUnifiedEnabled() && UserConfig.getActivatedAccountsCount() >= 2;
    }

    /** Logged-in accounts in a stable order (login order), the same order as the account switcher. */
    public static ArrayList<Integer> getActiveAccounts() {
        ArrayList<Integer> accounts = new ArrayList<>(UserConfig.MAX_ACCOUNT_COUNT);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(a);
            }
        }
        Collections.sort(accounts, (a1, a2) -> {
            int t1 = UserConfig.getInstance(a1).loginTime;
            int t2 = UserConfig.getInstance(a2).loginTime;
            return t1 != t2 ? Integer.compare(t1, t2) : Integer.compare(a1, a2);
        });
        return accounts;
    }

    /** Index of the account in {@link #getActiveAccounts()} order, used for stable colors. */
    public static int getAccountOrdinal(int account) {
        int index = getActiveAccounts().indexOf(account);
        return Math.max(index, 0);
    }

    public int getPrimaryAccount() {
        ensurePrimaryValid();
        return primaryAccount;
    }

    /** Primary account as stored, without replacing it when it was just logged out. */
    public int peekPrimaryAccount() {
        return primaryAccount;
    }

    /** The user explicitly chose an account (account switcher, login, settings). */
    public void setPrimaryAccount(int account) {
        if (!UserConfig.isValidAccount(account)) {
            return;
        }
        primaryAccount = account;
        route(account);
    }

    /** Value persisted as {@code selectedAccount}: transient routing must not survive restarts. */
    public static int getPersistentSelectedAccount() {
        MultiAccountController local = instance;
        if (local == null || local.primaryAccount < 0) {
            return UserConfig.selectedAccount;
        }
        return local.primaryAccount;
    }

    private void ensurePrimaryValid() {
        if (UserConfig.isValidAccount(primaryAccount)) {
            return;
        }
        if (UserConfig.isValidAccount(UserConfig.selectedAccount)) {
            primaryAccount = UserConfig.selectedAccount;
            return;
        }
        ArrayList<Integer> accounts = getActiveAccounts();
        if (!accounts.isEmpty()) {
            primaryAccount = accounts.get(0);
        } else if (primaryAccount < 0) {
            primaryAccount = UserConfig.selectedAccount;
        }
    }

    /**
     * Points {@link UserConfig#selectedAccount} at {@code account} without rebuilding the interface.
     * Has no effect unless unified mode is active (single-account behaviour stays untouched).
     */
    public void route(int account) {
        if (!UserConfig.isValidAccount(account) || UserConfig.selectedAccount == account) {
            return;
        }
        if (!isActive() && account != primaryAccount) {
            return;
        }
        UserConfig.selectedAccount = account;
        refreshKeepAlive();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.multiAccountRouteChanged, account);
    }

    /** A screen became the top of a navigation stack: route legacy account lookups to it. */
    public void onFragmentShown(BaseFragment fragment) {
        if (fragment == null || !isActive()) {
            return;
        }
        route(fragment.getCurrentAccount());
    }

    /** Accounts were added, removed or the unified mode was toggled. */
    public void onAccountsChanged() {
        ensurePrimaryValid();
        if (!isActive() && UserConfig.isValidAccount(primaryAccount) && UserConfig.selectedAccount != primaryAccount) {
            UserConfig.selectedAccount = primaryAccount;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.multiAccountRouteChanged, primaryAccount);
        }
        refreshKeepAlive();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.multiAccountStateChanged);
    }

    /**
     * The main interface was resumed or paused. While it is visible every logged-in account keeps its
     * network resumed in unified mode, so updates, read states and typing arrive for all of them, not
     * only for the selected one. In the background the regular push/sleep logic applies to each account.
     */
    public void setForeground(boolean value) {
        foreground = value;
        if (value) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    MessagesController.getInstance(a).ignoreSetOnline = false;
                }
            }
        }
        refreshKeepAlive();
    }

    public boolean isForeground() {
        return foreground;
    }

    private boolean shouldKeepAlive(int account) {
        if (!foreground || !UserConfig.getInstance(account).isClientActivated()) {
            return false;
        }
        return isActive() || account == UserConfig.selectedAccount;
    }

    /** Balances {@link ConnectionsManager#setAppPaused} calls so each account's resume counter stays exact. */
    public void refreshKeepAlive() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            boolean want = shouldKeepAlive(a);
            if (want == keptAlive[a]) {
                continue;
            }
            keptAlive[a] = want;
            try {
                ConnectionsManager.getInstance(a).setAppPaused(!want, false);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    /** Sum of the main-list unread counters of all logged-in accounts. */
    public static int getTotalUnreadCount() {
        int count = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                count += MessagesStorage.getInstance(a).getMainUnreadCount();
            }
        }
        return count;
    }

    /** Runs {@code action} with legacy lookups routed to {@code account}, then restores routing. */
    public void runInAccount(int account, Runnable action) {
        int previous = UserConfig.selectedAccount;
        boolean changed = isActive() && UserConfig.isValidAccount(account) && previous != account;
        if (changed) {
            UserConfig.selectedAccount = account;
        }
        try {
            action.run();
        } finally {
            if (changed && UserConfig.selectedAccount == account) {
                UserConfig.selectedAccount = previous;
            }
        }
    }
}
