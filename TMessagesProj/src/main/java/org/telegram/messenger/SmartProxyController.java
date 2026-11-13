package org.telegram.messenger;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmartProxyController implements NotificationCenter.NotificationCenterDelegate {

    private static final SmartProxyController INSTANCE = new SmartProxyController();

    // Known Telegram Datacenter IPs for fast direct connection checks
    private static final String[] TELEGRAM_DC_IPS = new String[] {
            "149.154.167.51", // DC2 (Europe)
            "149.154.167.91", // DC4 (Europe)
            "149.154.175.53"  // DC1 (USA)
    };
    private static final int DC_PORT = 443;
    private static final int DIRECT_CHECK_TIMEOUT_MS = 2500;
    private static final long DIRECT_CHECK_CACHE_MS = 30_000; // 30 sec cache

    private boolean isProxySuppressed = false;
    private Boolean lastDirectCheckResult = null;
    private long lastDirectCheckTime = 0;
    private final AtomicBoolean isCheckingDirect = new AtomicBoolean(false);

    private ConnectivityManager.NetworkCallback networkCallback;

    public static SmartProxyController getInstance() {
        return INSTANCE;
    }

    public static void init() {
        INSTANCE.initInternal();
    }

    private void initInternal() {
        registerNetworkCallback();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.didUpdateConnectionState);
        }

        AndroidUtilities.runOnUIThread(() -> evaluateAndApply(false), 1000);
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        onNetworkChanged();
                    }

                    @Override
                    public void onLost(Network network) {
                        onNetworkChanged();
                    }

                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                        onNetworkChanged();
                    }
                };
                cm.registerDefaultNetworkCallback(networkCallback);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void onNetworkChanged() {
        lastDirectCheckResult = null;
        lastDirectCheckTime = 0;
        AndroidUtilities.runOnUIThread(() -> evaluateAndApply(true));
    }

    public static boolean isVpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true;
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Network[] allNetworks = cm.getAllNetworks();
                if (allNetworks != null) {
                    for (Network network : allNetworks) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return false;
    }

    public void evaluateAndApply(boolean networkChanged) {
        if (!SharedConfig.isProxyEnabled() || SharedConfig.currentProxy == null || !SharedConfig.currentProxy.settings.isValid()) {
            if (isProxySuppressed) {
                isProxySuppressed = false;
            }
            return;
        }

        // Feature 1: Disable proxy when system VPN is active
        if (SharedConfig.disableProxyOnVpn && isVpnActive()) {
            suppressProxy(true);
            return;
        }

        // Feature 2: Smart direct fallback (detect if Telegram servers are directly reachable without proxy)
        if (SharedConfig.smartDirectFallback) {
            long now = SystemClock.elapsedRealtime();
            if (lastDirectCheckResult != null && (now - lastDirectCheckTime < DIRECT_CHECK_CACHE_MS)) {
                if (Boolean.TRUE.equals(lastDirectCheckResult)) {
                    suppressProxy(true);
                    return;
                } else {
                    suppressProxy(false);
                    return;
                }
            }

            checkDirectConnectionAsync();
            return;
        }

        // Normal operation: proxy should be active
        suppressProxy(false);
    }

    private void checkDirectConnectionAsync() {
        if (!isCheckingDirect.compareAndSet(false, true)) {
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            boolean reachable = false;
            for (String ip : TELEGRAM_DC_IPS) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(ip, DC_PORT), DIRECT_CHECK_TIMEOUT_MS);
                    reachable = true;
                    break;
                } catch (Throwable ignored) {
                }
            }

            final boolean directAvailable = reachable;
            AndroidUtilities.runOnUIThread(() -> {
                isCheckingDirect.set(false);
                lastDirectCheckResult = directAvailable;
                lastDirectCheckTime = SystemClock.elapsedRealtime();

                if (SharedConfig.isProxyEnabled() && SharedConfig.smartDirectFallback) {
                    suppressProxy(directAvailable);
                }
            });
        });
    }

    private void suppressProxy(boolean suppress) {
        if (isProxySuppressed == suppress) {
            return;
        }
        isProxySuppressed = suppress;

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("SmartProxyController: suppressProxy = " + suppress);
        }

        if (suppress) {
            // Temporarily disable proxy in native layer
            ConnectionsManager.setProxySettings(false, null);
        } else {
            // Restore configured proxy
            if (SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null && SharedConfig.currentProxy.settings.isValid()) {
                ConnectionsManager.setProxySettings(true, SharedConfig.currentProxy.settings);
            }
        }
    }

    public boolean isProxySuppressed() {
        return isProxySuppressed;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged) {
            AndroidUtilities.runOnUIThread(() -> evaluateAndApply(false));
        } else if (id == NotificationCenter.didUpdateConnectionState && account == UserConfig.selectedAccount) {
            if (SharedConfig.isProxyEnabled() && (SharedConfig.disableProxyOnVpn || SharedConfig.smartDirectFallback)) {
                int state = ConnectionsManager.getInstance(account).getConnectionState();
                if (state == ConnectionsManager.ConnectionStateConnectingToProxy && isProxySuppressed) {
                    // Safety check: ensure proxy remains suppressed if condition met
                    ConnectionsManager.setProxySettings(false, null);
                }
            }
        }
    }
}
