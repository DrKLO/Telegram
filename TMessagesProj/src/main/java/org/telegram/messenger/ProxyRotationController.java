package org.telegram.messenger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Controller responsible for managing proxy rotation in Telegram
public class ProxyRotationController implements NotificationCenter.NotificationCenterDelegate {
    // Singleton instance
    private final static ProxyRotationController INSTANCE = new ProxyRotationController();

    // Default timeout index used when no specific value is configured
    public final static int DEFAULT_TIMEOUT_INDEX = 1;

    // List of predefined timeout values (in seconds) for proxy rotation intervals
    public final static List<Integer> ROTATION_TIMEOUTS = Arrays.asList(5, 10, 15, 30, 60);

    // Indicates whether a proxy check is currently ongoing
    private boolean isCurrentlyChecking;

    // Flag to prevent switching to multiple proxies during a single check run
    private volatile boolean hasSwitched = false;

    // Runnable to check all proxies and switch if necessary
    private Runnable checkProxyAndSwitchRunnable = () -> {
        isCurrentlyChecking = true;
        hasSwitched = false;

        int currentAccount = UserConfig.selectedAccount;
        boolean startedCheck = false;

        for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
            SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);

            // ⚠️ Skip proxy if it's already being checked or recently checked (<2 minutes ago)
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
                continue;
            }

            startedCheck = true;
            proxyInfo.checking = true;

            // Starts ping check for this proxy
            proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(
                proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret,

                // Callback once ping completes
                time -> AndroidUtilities.runOnUIThread(() -> {
                    proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                    proxyInfo.checking = false;

                    if (time == -1) {
                        proxyInfo.available = false;
                        proxyInfo.ping = 0;
                    } else {
                        proxyInfo.ping = time;
                        proxyInfo.available = true;

                        // ✅ Immediately switch to this proxy if it's the first working one
                        if (!hasSwitched) {
                            hasSwitched = true;
                            switchToProxy(proxyInfo);
                        }
                    }

                    // Notify UI and listeners that the check is done
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
                })
            );
        }

        if (!startedCheck) {
            // ⚠️ No eligible proxy found to check; fallback to best available
            isCurrentlyChecking = false;
            switchToAvailable();
        }
    };

    // Public entry point for setting up the controller
    public static void init() {
        INSTANCE.initInternal();
    }

    // Apply the given proxy configuration and notify system components
    private void switchToProxy(SharedConfig.ProxyInfo info) {
        if (info == null || info == SharedConfig.currentProxy || !info.available) return;

        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putString("proxy_ip", info.address);
        editor.putString("proxy_pass", info.password);
        editor.putString("proxy_user", info.username);
        editor.putInt("proxy_port", info.port);
        editor.putString("proxy_secret", info.secret);
        editor.putBoolean("proxy_enabled", true);

        // If MTProto proxy is used, disable calls
        if (!info.secret.isEmpty()) {
            editor.putBoolean("proxy_enabled_calls", false);
        }

        editor.apply();

        // Update in-memory proxy info
        SharedConfig.currentProxy = info;

        // Notify listeners that settings and proxy have changed
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyChangedByRotation);

        // Apply settings to connection manager
        ConnectionsManager.setProxySettings(
            true,
            info.address,
            info.port,
            info.username,
            info.password,
            info.secret
        );
    }

    // Fallback method to switch to the best available proxy based on ping
    private void switchToAvailable() {
        isCurrentlyChecking = false;
        hasSwitched = false; // 🔧 Reset switch flag after fallback

        if (!SharedConfig.proxyRotationEnabled) return;

        List<SharedConfig.ProxyInfo> sortedList = new ArrayList<>(SharedConfig.proxyList);
        Collections.sort(sortedList, (o1, o2) -> Long.compare(o1.ping, o2.ping));

        for (SharedConfig.ProxyInfo info : sortedList) {
            // ⚠️ Skip current, unavailable, or still checking proxies
            if (info == SharedConfig.currentProxy || info.checking || !info.available) continue;

            switchToProxy(info);
            hasSwitched = true; // 🔧 Prevent further switching
            break;
        }
    }

    // Internal setup for observers to listen for relevant events
    private void initInternal() {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
    }

    // Reacts to relevant system notifications
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyCheckDone) {
            // ✅ This is handled in the proxy ping callback above
            if (!SharedConfig.isProxyEnabled() || !SharedConfig.proxyRotationEnabled ||
                SharedConfig.proxyList.size() <= 1 || !isCurrentlyChecking) {
                return;
            }

        } else if (id == NotificationCenter.proxySettingsChanged) {
            // ⚠️ Cancel rotation if manual proxy change happened
            AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);

        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (!SharedConfig.isProxyEnabled() && !SharedConfig.proxyRotationEnabled ||
                SharedConfig.proxyList.size() <= 1) {
                return;
            }

            int state = ConnectionsManager.getInstance(account).getConnectionState();

            if (state == ConnectionsManager.ConnectionStateConnectingToProxy) {
                // ✅ Schedule proxy check if not already checking
                if (!isCurrentlyChecking) {
                    AndroidUtilities.runOnUIThread(
                        checkProxyAndSwitchRunnable,
                        ROTATION_TIMEOUTS.get(SharedConfig.proxyRotationTimeout) * 1000L
                    );
                }
            } else {
                // 🔄 Cancel check if connection state is no longer "connecting to proxy"
                AndroidUtilities.cancelRunOnUIThread(checkProxyAndSwitchRunnable);
            }
        }
    }
}









    //private int proxiesBeingChecked = 0;

    /**
    checkProxyAndSwitchRunnable = () -> {
    isCurrentlyChecking = true;
    hasSwitched = false;
    proxiesBeingChecked = 0;

    int currentAccount = UserConfig.selectedAccount;
    boolean startedCheck = false;

    for (int i = 0; i < SharedConfig.proxyList.size(); i++) {
        SharedConfig.ProxyInfo proxyInfo = SharedConfig.proxyList.get(i);

        if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000) {
            continue;
        }

        startedCheck = true;
        proxyInfo.checking = true;
        proxiesBeingChecked++;

        proxyInfo.proxyCheckPingId = ConnectionsManager.getInstance(currentAccount).checkProxy(
            proxyInfo.address, proxyInfo.port, proxyInfo.username, proxyInfo.password, proxyInfo.secret,

            time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;

                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = Long.MAX_VALUE;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }

                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);

                proxiesBeingChecked--;
                if (proxiesBeingChecked == 0) {
                    // ✅ All checks done — switch to lowest ping
                    switchToBestAvailable();
                }
            })
        );
    }

    if (!startedCheck) {
        isCurrentlyChecking = false;
        switchToAvailable();
    }
};
    */

    /**
    private void switchToBestAvailable() {
    isCurrentlyChecking = false;

    if (!SharedConfig.proxyRotationEnabled) return;

    List<SharedConfig.ProxyInfo> availableProxies = new ArrayList<>();
    for (SharedConfig.ProxyInfo info : SharedConfig.proxyList) {
        if (info.available && info != SharedConfig.currentProxy) {
            availableProxies.add(info);
        }
    }

    if (availableProxies.isEmpty()) return;

    SharedConfig.ProxyInfo bestProxy = Collections.min(availableProxies, (a, b) -> Long.compare(a.ping, b.ping));

    switchToProxy(bestProxy);
    hasSwitched = true;
    }
    */
