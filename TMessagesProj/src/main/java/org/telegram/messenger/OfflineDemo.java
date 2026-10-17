package org.telegram.messenger;

import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.RequestDelegateTimestamp;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

/** Data seam for the credential-free APK. Never enabled in a live client build. */
public final class OfflineDemo {
    public interface DataSource {
        TLRPC.User self();
        TLObject respond(int account, TLObject request);
        void recordTransaction(int account, TL_stars.StarsTransaction transaction, boolean live);
    }

    private static DataSource source;
    private static java.lang.ref.WeakReference<org.telegram.ui.ActionBar.INavigationLayout> navigation = new java.lang.ref.WeakReference<>(null);

    public static void setNavigation(org.telegram.ui.ActionBar.INavigationLayout layout) {
        if (enabled()) navigation = new java.lang.ref.WeakReference<>(layout);
    }

    public static org.telegram.ui.ActionBar.BaseFragment lastFragment() {
        org.telegram.ui.ActionBar.INavigationLayout layout = navigation.get();
        return enabled() && layout != null ? layout.getLastFragment() : null;
    }

    public static boolean enabled() { return BuildConfig.FEATURES_LAB_OFFLINE; }

    public static void install(DataSource dataSource) {
        if (!enabled()) throw new IllegalStateException("Offline data requires the offline build");
        source = dataSource;
    }

    public static TLRPC.User self(int account) {
        return enabled() && account == 0 && source != null ? source.self() : null;
    }

    public static void request(int account, TLObject request, RequestDelegate completion,
                               RequestDelegateTimestamp timestampCompletion) {
        AndroidUtilities.runOnUIThread(() -> {
            TLObject response = source == null ? null : source.respond(account, request);
            TLRPC.TL_error error = null;
            if (response == null) {
                error = new TLRPC.TL_error();
                error.code = 400;
                error.text = "OFFLINE_DEMO_UNAVAILABLE";
            }
            if (completion != null) completion.run(response, error);
            if (timestampCompletion != null) timestampCompletion.run(response, error, System.currentTimeMillis());
        }, 80);
    }

    public static void recordTransaction(int account, TL_stars.StarsTransaction transaction, boolean live) {
        if (enabled() && source != null) source.recordTransaction(account, transaction, live);
    }

    private OfflineDemo() {}
}
