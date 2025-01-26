/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.browser;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.CustomTabsCopyReceiver;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.ShareBroadcastReceiver;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.customtabs.CustomTabsCallback;
import org.telegram.messenger.support.customtabs.CustomTabsClient;
import org.telegram.messenger.support.customtabs.CustomTabsIntent;
import org.telegram.messenger.support.customtabs.CustomTabsServiceConnection;
import org.telegram.messenger.support.customtabs.CustomTabsSession;
import org.telegram.messenger.support.customtabsclient.shared.CustomTabsHelper;
import org.telegram.messenger.support.customtabsclient.shared.ServiceConnection;
import org.telegram.messenger.support.customtabsclient.shared.ServiceConnectionCallback;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheetTabs;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.web.RestrictedDomainsList;

import java.lang.ref.WeakReference;
import java.net.IDN;
import java.net.URLEncoder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Browser {

    private static WeakReference<CustomTabsSession> customTabsCurrentSession;
    private static CustomTabsSession customTabsSession;
    private static CustomTabsClient customTabsClient;
    private static CustomTabsServiceConnection customTabsServiceConnection;
    private static String customTabsPackageToBind;
    private static WeakReference<Activity> currentCustomTabsActivity;

    private static CustomTabsSession getCurrentSession() {
        return customTabsCurrentSession == null ? null : customTabsCurrentSession.get();
    }

    private static void setCurrentSession(CustomTabsSession session) {
        customTabsCurrentSession = new WeakReference<>(session);
    }

    private static CustomTabsSession getSession() {
        if (customTabsClient == null) {
            customTabsSession = null;
        } else if (customTabsSession == null) {
            customTabsSession = customTabsClient.newSession(new NavigationCallback());
            setCurrentSession(customTabsSession);
        }
        return customTabsSession;
    }

    public static void bindCustomTabsService(Activity activity) {
        Activity currentActivity = currentCustomTabsActivity == null ? null : currentCustomTabsActivity.get();
        if (currentActivity != null && currentActivity != activity) {
            unbindCustomTabsService(currentActivity);
        }
        if (customTabsClient != null) {
            return;
        }
        currentCustomTabsActivity = new WeakReference<>(activity);
        try {
            if (TextUtils.isEmpty(customTabsPackageToBind)) {
                customTabsPackageToBind = CustomTabsHelper.getPackageNameToUse(activity);
                if (customTabsPackageToBind == null) {
                    return;
                }
            }
            customTabsServiceConnection = new ServiceConnection(new ServiceConnectionCallback() {
                @Override
                public void onServiceConnected(CustomTabsClient client) {
                    customTabsClient = client;
                    if (SharedConfig.customTabs) {
                        if (customTabsClient != null) {
                            try {
                                customTabsClient.warmup(0);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }

                @Override
                public void onServiceDisconnected() {
                    customTabsClient = null;
                }
            });
            if (!CustomTabsClient.bindCustomTabsService(activity, customTabsPackageToBind, customTabsServiceConnection)) {
                customTabsServiceConnection = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void unbindCustomTabsService(Activity activity) {
        if (customTabsServiceConnection == null) {
            return;
        }
        Activity currentActivity = currentCustomTabsActivity == null ? null : currentCustomTabsActivity.get();
        if (currentActivity == activity) {
            currentCustomTabsActivity.clear();
        }
        try {
            activity.unbindService(customTabsServiceConnection);
        } catch (Exception ignore) {

        }
        customTabsClient = null;
        customTabsSession = null;
    }

    private static class NavigationCallback extends CustomTabsCallback {
        @Override
        public void onNavigationEvent(int navigationEvent, Bundle extras) {

        }
    }

    public static void openUrl(Context context, String url) {
        if (url == null) {
            return;
        }
        openUrl(context, Uri.parse(url), true);
    }

    public static void openUrlInSystemBrowser(Context context, String url) {
        if (url == null) {
            return;
        }
        openUrl(context, Uri.parse(url), false, true, false, null, null, false, false, false);
    }

    public static void openUrl(Context context, Uri uri) {
        openUrl(context, uri, true);
    }

    public static void openUrl(Context context, String url, boolean allowCustom) {
        if (context == null || url == null) {
            return;
        }
        openUrl(context, Uri.parse(url), allowCustom);
    }

    public static void openUrl(Context context, Uri uri, boolean allowCustom) {
        openUrl(context, uri, allowCustom, true);
    }

    public static void openUrl(final Context context, final String url, final boolean allowCustom, boolean tryTelegraph) {
        openUrl(context, Uri.parse(url), allowCustom, tryTelegraph);
    }

    public static boolean isTelegraphUrl(String url, boolean equals) {
        return isTelegraphUrl(url, equals, false);
    }
    public static boolean isTelegraphUrl(String url, boolean equals, boolean forceHttps) {
        if (equals) {
            return url.equals("telegra.ph") || url.equals("te.legra.ph") || url.equals("graph.org");
        }
        return url.matches("^(https" + (forceHttps ? "" : "?") + "://)?(te\\.?legra\\.ph|graph\\.org)(/.*|$)"); // telegra.ph, te.legra.ph, graph.org
    }

    public static String extractUsername(String link) {
        if (link == null || TextUtils.isEmpty(link)) {
            return null;
        }
        if (link.startsWith("@")) {
            return link.substring(1);
        }
        if (link.startsWith("t.me/")) {
            return link.substring(5);
        }
        if (link.startsWith("http://t.me/")) {
            return link.substring(12);
        }
        if (link.startsWith("https://t.me/")) {
            return link.substring(13);
        }
        Matcher prefixMatcher = LaunchActivity.PREFIX_T_ME_PATTERN.matcher(link);
        if (prefixMatcher.find()) {
            return prefixMatcher.group(1);
        }
        return null;
    }

    public static boolean urlMustNotHaveConfirmation(String url) {
        return (
            isTelegraphUrl(url, false, true) ||
            url.matches("^(https://)?t\\.me/iv\\??(/.*|$)") || // t.me/iv?
            url.matches("^(https://)?telegram\\.org/(blog|tour)(/.*|$)") || // telegram.org/blog, telegram.org/tour
            url.matches("^(https://)?fragment\\.com(/.*|$)") // fragment.com
        );
    }

    public static class Progress {

        private Runnable onInitListener;
        private Runnable onCancelListener;
        private Runnable onEndListener;

        public Progress() {

        }

        public Progress(Runnable init, Runnable end) {
            this.onInitListener = init;
            this.onEndListener = end;
        }

        public void init() {
            if (onInitListener != null) {
                onInitListener.run();
                onInitListener = null;
            }
        }
        public void end() {
            end(false);
        }
        public void end(boolean replaced) {
            if (onEndListener != null) {
                onEndListener.run();
            }
        }

        public void cancel() {
            cancel(false);
        }
        public void cancel(boolean replaced) {
            if (onCancelListener != null) {
                onCancelListener.run();
            }
            end(replaced);
        }

        public Progress onCancel(Runnable onCancelListener) {
            this.onCancelListener = onCancelListener;
            return this;
        }

        public Progress onEnd(Runnable onEndListener) {
            this.onEndListener = onEndListener;
            return this;
        }
    }

    public static void openUrl(final Context context, Uri uri, final boolean allowCustom, boolean tryTelegraph) {
        openUrl(context, uri, allowCustom, tryTelegraph, false, null, null, false, true, false);
    }

    public static void openUrl(final Context context, Uri uri, final boolean allowCustom, boolean tryTelegraph, Progress inCaseLoading) {
        openUrl(context, uri, allowCustom, tryTelegraph, false, inCaseLoading, null, false, true, false);
    }

    public static void openUrl(final Context context, Uri uri, boolean _allowCustom, boolean tryTelegraph, boolean forceNotInternalForApps, Progress inCaseLoading, String browser, boolean allowIntent, boolean allowInAppBrowser, boolean forceRequest) {
        if (context == null || uri == null) {
            return;
        }
        final int currentAccount = UserConfig.selectedAccount;
        boolean[] forceBrowser = new boolean[]{false};
        boolean internalUri = isInternalUri(uri, forceBrowser);
        String browserPackage = getBrowserPackageName(browser);
        if (browserPackage != null) {
            tryTelegraph = false;
            _allowCustom = false;
        }
        final boolean allowCustom = _allowCustom;
        if (tryTelegraph) {
            try {
                String host = AndroidUtilities.getHostAuthority(uri);
                if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() != null && (isTelegraphUrl(host, true) || "telegram.org".equalsIgnoreCase(host) && (uri.toString().toLowerCase().contains("telegram.org/faq") || uri.toString().toLowerCase().contains("telegram.org/privacy") || uri.toString().toLowerCase().contains("telegram.org/blog")))) {
                    final AlertDialog[] progressDialog = new AlertDialog[] {
                        new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER)
                    };

                    Uri finalUri = uri;
                    TL_account.getWebPagePreview req = new TL_account.getWebPagePreview();
                    req.message = uri.toString();
                    final int reqId = ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (inCaseLoading != null) {
                            inCaseLoading.end();
                        } else {
                            try {
                                progressDialog[0].dismiss();
                            } catch (Throwable ignore) {}
                            progressDialog[0] = null;
                        }

                        boolean ok = false;
                        if (response instanceof TL_account.webPagePreview) {
                            final TL_account.webPagePreview preview = (TL_account.webPagePreview) response;
                            MessagesController.getInstance(currentAccount).putUsers(preview.users, false);
                            if (preview.media instanceof TLRPC.TL_messageMediaWebPage) {
                                TLRPC.TL_messageMediaWebPage webPage = (TLRPC.TL_messageMediaWebPage) preview.media;
                                if (webPage.webpage instanceof TLRPC.TL_webPage && webPage.webpage.cached_page != null) {
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.openArticle, webPage.webpage, finalUri.toString());
                                    ok = true;
                                }
                            }
                        }
                        if (!ok) {
                            openUrl(context, finalUri, allowCustom, false);
                        }
                    }));
                    if (inCaseLoading != null) {
                        inCaseLoading.init();
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (progressDialog[0] == null) {
                                return;
                            }
                            try {
                                progressDialog[0].setOnCancelListener(dialog -> ConnectionsManager.getInstance(UserConfig.selectedAccount).cancelRequest(reqId, true));
                                progressDialog[0].show();
                            } catch (Exception ignore) {}
                        }, 1000);
                    }
                    return;
                }
            } catch (Exception ignore) {

            }
        }
        try {
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
            if ("http".equals(scheme) || "https".equals(scheme)) {
                try {
                    uri = uri.normalizeScheme();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            String host = AndroidUtilities.getHostAuthority(uri.toString().toLowerCase());
            if (AccountInstance.getInstance(currentAccount).getMessagesController().autologinDomains.contains(host)) {
                String token = "autologin_token=" + URLEncoder.encode(AccountInstance.getInstance(UserConfig.selectedAccount).getMessagesController().autologinToken, "UTF-8");
                String url = uri.toString();
                int idx = url.indexOf("://");
                String path = idx >= 0 && idx <= 5 && !url.substring(0, idx).contains(".") ? url.substring(idx + 3) : url;
                String fragment = uri.getEncodedFragment();
                String finalPath = fragment == null ? path : path.substring(0, path.indexOf("#" + fragment));
                if (finalPath.indexOf('?') >= 0) {
                    finalPath += "&" + token;
                } else {
                    finalPath += "?" + token;
                }
                if (fragment != null) {
                    finalPath += "#" + fragment;
                }
                uri = Uri.parse("https://" + finalPath);
            }
            if (allowCustom && !SharedConfig.inappBrowser && SharedConfig.customTabs && !internalUri && !scheme.equals("tel") && !isTonsite(uri.toString())) {
                if (forceBrowser[0] || !openInExternalApp(context, uri.toString(), false) || !hasAppToOpen(context, uri.toString())) {
                    if (MessagesController.getInstance(currentAccount).authDomains.contains(host)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ApplicationLoader.applicationContext.startActivity(intent);
                        return;
                    }

                    Intent share = new Intent(ApplicationLoader.applicationContext, ShareBroadcastReceiver.class);
                    share.setAction(Intent.ACTION_SEND);

                    PendingIntent copy = PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 0, new Intent(ApplicationLoader.applicationContext, CustomTabsCopyReceiver.class), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

                    CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(getSession());

                    builder.addMenuItem(LocaleController.getString(R.string.CopyLink), copy);

                    builder.setToolbarColor(Theme.getColor(Theme.key_actionBarBrowser));
                    builder.setShowTitle(true);
                    builder.setActionButton(BitmapFactory.decodeResource(context.getResources(), R.drawable.msg_filled_shareout), LocaleController.getString(R.string.ShareFile), PendingIntent.getBroadcast(ApplicationLoader.applicationContext, 0, share, PendingIntent.FLAG_MUTABLE ), true);

                    CustomTabsIntent intent = builder.build();
                    intent.setUseNewTask();
                    intent.launchUrl(context, uri);
                    return;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            final boolean inappBrowser = (
                allowInAppBrowser && BubbleActivity.instance == null &&
                SharedConfig.inappBrowser &&
                TextUtils.isEmpty(browserPackage) &&
                !RestrictedDomainsList.getInstance().isRestricted(AndroidUtilities.getHostAuthority(uri, true)) &&
                (uri.getScheme() == null || "https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) || "tonsite".equals(uri.getScheme()))
                ||
                isTonsite(uri.toString())
            );
            final boolean isIntentScheme = uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("intent");
            if (internalUri && LaunchActivity.instance != null) {
                openAsInternalIntent(LaunchActivity.instance, uri.toString(), forceNotInternalForApps, forceRequest, inCaseLoading);
            } else {
                if (inappBrowser) {
                    if (!openInExternalApp(context, uri.toString(), allowIntent)) {
                        if (uri != null && uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("intent")) {
                            final Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                            final String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                            if (!TextUtils.isEmpty(fallbackUrl)) {
                                uri = Uri.parse(fallbackUrl);
                            }
                        }
                        openInTelegramBrowser(context, uri.toString(), inCaseLoading);
                    }
                } else {
                    openInExternalBrowser(context, uri.toString(), allowIntent, browserPackage);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean openAsInternalIntent(Context context, String url) {
        return openAsInternalIntent(context, url, false, false, null);
    }
    public static boolean openAsInternalIntent(Context context, String url, Browser.Progress progress) {
        return openAsInternalIntent(context, url, false, false, progress);
    }
    public static boolean openAsInternalIntent(Context context, String url,  boolean forceNotInternalForApps) {
        return openAsInternalIntent(context, url, forceNotInternalForApps, false, null);
    }
    public static boolean openAsInternalIntent(Context context, String url, boolean forceNotInternalForApps, boolean forceRequest, Progress progress) {
        if (url == null) return false;
        LaunchActivity activity = null;
        if (AndroidUtilities.findActivity(context) instanceof LaunchActivity) {
            activity = (LaunchActivity) AndroidUtilities.findActivity(context);
        } else if (LaunchActivity.instance != null) {
            activity = LaunchActivity.instance;
        } else {
            return false;
        }
        if (activity == null) return false;
        final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        ComponentName componentName = new ComponentName(context.getPackageName(), LaunchActivity.class.getName());
        intent.setComponent(componentName);
        intent.putExtra(android.provider.Browser.EXTRA_CREATE_NEW_TAB, true);
        intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        intent.putExtra(LaunchActivity.EXTRA_FORCE_NOT_INTERNAL_APPS, forceNotInternalForApps);
        intent.putExtra(LaunchActivity.EXTRA_FORCE_REQUEST, forceRequest);
        activity.onNewIntent(intent, progress);
        return true;
    }

    public static boolean openInTelegramBrowser(Context context, String url, Browser.Progress progress) {
        if (LaunchActivity.instance != null) {
            BottomSheetTabs tabs = LaunchActivity.instance.getBottomSheetTabs();
            if (tabs != null && tabs.tryReopenTab(url) != null) {
                return true;
            }
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment != null && fragment.getParentLayout() instanceof ActionBarLayout) {
            fragment = ((ActionBarLayout) fragment.getParentLayout()).getSheetFragment();
        }
        if (fragment == null) {
            return false;
        }
        fragment.createArticleViewer(false).open(url, progress);
        return true;
    }

    public static boolean openInExternalBrowser(Context context, String url, boolean allowIntent) {
        return openInExternalBrowser(context, url, allowIntent, null);
    }
    public static boolean openInExternalBrowser(Context context, String url, boolean allowIntent, String browser) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            final boolean isIntentScheme = uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("intent");
            if (isIntentScheme && !allowIntent) return false;
            final Intent intent = isIntentScheme ?
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) :
                    new Intent(Intent.ACTION_VIEW, uri);
            if (!TextUtils.isEmpty(browser)) {
                intent.setPackage(browser);
            }
            intent.putExtra(android.provider.Browser.EXTRA_CREATE_NEW_TAB, true);
            intent.putExtra(android.provider.Browser.EXTRA_APPLICATION_ID, context.getPackageName());
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isTonsite(String url) {
        String domain = AndroidUtilities.getHostAuthority(url, true);
        if (domain != null && (domain.endsWith(".ton") || domain.endsWith(".adnl"))) {
            return true;
        }
        Uri uri = Uri.parse(url);
        if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("tonsite")) {
            return true;
        }
        return false;
    }

    private static Pattern domainPattern;
    public static boolean isTonsitePunycode(String url) {
        if (domainPattern == null) domainPattern = Pattern.compile("^[a-zA-Z0-9\\-\\_\\.]+\\.[a-zA-Z0-9\\-\\_]+$");
        String domain = AndroidUtilities.getHostAuthority(url, true);
        if (domain != null && (domain.endsWith(".ton") || domain.endsWith(".adnl"))) {
            return !domainPattern.matcher(domain).matches();
        }
        Uri uri = Uri.parse(url);
        if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("tonsite")) {
            return !domainPattern.matcher(uri.getScheme()).matches();
        }
        return false;
    }

    public static boolean openInExternalApp(Context context, String url, boolean allowIntent) {
        if (url == null) return false;
        try {
            if (isTonsite(url) || isInternalUrl(url, null)) return false;
            Uri uri = Uri.parse(url);
            url = Browser.replace(
                uri,
                uri.getScheme() == null ? "https" : uri.getScheme(),
                    null, uri.getHost() != null ? uri.getHost().toLowerCase() : uri.getHost(),
                TextUtils.isEmpty(uri.getPath()) ? "/" : uri.getPath()
            );
            uri = Uri.parse(url);
            final boolean isIntentScheme = url.startsWith("intent://") || uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("intent");
            if (isIntentScheme && !allowIntent) return false;
            final Intent intent = isIntentScheme ?
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) :
                new Intent(Intent.ACTION_VIEW, uri);
            if (!isIntentScheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
            } else if (!isIntentScheme && !hasAppToOpen(context, url)) {
                return false;
            }
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            FileLog.e(e, false);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean hasAppToOpen(Context context, String url) {
        if (url == null) return false;

        String[] browserPackageNames = null;
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
            List<ResolveInfo> list = context.getPackageManager().queryIntentActivities(browserIntent, 0);
            if (list != null && !list.isEmpty()) {
                browserPackageNames = new String[list.size()];
                for (int a = 0; a < list.size(); a++) {
                    browserPackageNames[a] = list.get(a).activityInfo.packageName;
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("default browser name = " + browserPackageNames[a]);
                    }
                }
            }
        } catch (Exception ignore) {}

        List<ResolveInfo> allActivities = null;
        try {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            allActivities = context.getPackageManager().queryIntentActivities(viewIntent, 0);
            if (browserPackageNames != null) {
                for (int a = 0; a < allActivities.size(); a++) {
                    for (int b = 0; b < browserPackageNames.length; b++) {
                        if (browserPackageNames[b].equals(allActivities.get(a).activityInfo.packageName)) {
                            allActivities.remove(a);
                            a--;
                            break;
                        }
                    }
                }
            } else {
                for (int a = 0; a < allActivities.size(); a++) {
                    final String packageName = allActivities.get(a).activityInfo.packageName.toLowerCase();
                    if (isBrowserPackageName(packageName)) {
                        allActivities.remove(a);
                        a--;
                    }
                }
            }
            if (BuildVars.LOGS_ENABLED) {
                for (int a = 0; a < allActivities.size(); a++) {
                    FileLog.d("device has " + allActivities.get(a).activityInfo.packageName + " to open " + url);
                }
            }
        } catch (Exception ignore) {}

        return allActivities != null && !allActivities.isEmpty();
    }

    public static boolean isInternalUrl(String url, boolean[] forceBrowser) {
        return isInternalUri(Uri.parse(url), false, forceBrowser);
    }

    public static boolean isInternalUrl(String url, boolean all, boolean[] forceBrowser) {
        return isInternalUri(Uri.parse(url), all, forceBrowser);
    }

    public static boolean isPassportUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            url = url.toLowerCase();
            if (url.startsWith("tg:passport") || url.startsWith("tg://passport") || url.startsWith("tg:secureid") || url.contains("resolve") && url.contains("domain=telegrampassport")) {
                return true;
            }
        } catch (Throwable ignore) {

        }
        return false;
    }

    public static boolean isTMe(String url) {
        try {
            final String linkPrefix = MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix;
            return TextUtils.equals(AndroidUtilities.getHostAuthority(url), linkPrefix);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isInternalUri(Uri uri, boolean[] forceBrowser) {
        return isInternalUri(uri, false, forceBrowser);
    }

    public static boolean isInternalUri(Uri uri, boolean all, boolean[] forceBrowser) {
        String host = AndroidUtilities.getHostAuthority(uri);
        host = host != null ? host.toLowerCase() : "";

        if (MessagesController.getInstance(UserConfig.selectedAccount).authDomains.contains(host)) {
            if (forceBrowser != null) {
                forceBrowser[0] = true;
            }
            return false;
        }

        Matcher prefixMatcher = LaunchActivity.PREFIX_T_ME_PATTERN.matcher(host);
        if (prefixMatcher.find()) {
            uri = Uri.parse("https://t.me/" + prefixMatcher.group(1) + (TextUtils.isEmpty(uri.getPath()) ? "" : "/" + uri.getPath()) + (TextUtils.isEmpty(uri.getQuery()) ? "" : "?" + uri.getQuery()));

            host = uri.getHost();
            host = host != null ? host.toLowerCase() : "";
        }

        if ("ton".equals(uri.getScheme())) {
            try {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, uri);
                List<ResolveInfo> allActivities = ApplicationLoader.applicationContext.getPackageManager().queryIntentActivities(viewIntent, 0);
                if (allActivities != null && allActivities.size() >= 1) {
                    return false;
                }
            } catch (Exception ignore) {

            }
            return true;
        } else if ("tg".equals(uri.getScheme())) {
            return true;
        } else if ("telegram.dog".equals(host)) {
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                if (all) {
                    return true;
                }
                path = path.substring(1).toLowerCase();
                if (path.startsWith("blog") || path.equals("iv") || path.startsWith("faq") || path.equals("apps") || path.startsWith("s/")) {
                    if (forceBrowser != null) {
                        forceBrowser[0] = true;
                    }
                    return false;
                }
                return true;
            }
        } else if ("telegram.me".equals(host) || "t.me".equals(host)) {
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                if (all) {
                    return true;
                }
                path = path.substring(1).toLowerCase();
                if (path.equals("iv") || path.startsWith("s/")) {
                    if (forceBrowser != null) {
                        forceBrowser[0] = true;
                    }
                    return false;
                }
                return true;
            }
        } else if ("telegram.org".equals(host) && uri != null && uri.getPath() != null && uri.getPath().startsWith("/blog/")) {
            return true;
        } else if (all) {
            if (host.endsWith("telegram.org") || host.endsWith("telegra.ph") || host.endsWith("telesco.pe")) {
                return true;
            }
        }
        return false;
    }

    public static String getBrowserPackageName(String browser) {
        if (browser == null) return null;
        switch (browser) {
            case "google-chrome":
            case "chrome":
                return "com.android.chrome";
            case "mozilla-firefox":
            case "firefox":
                return "org.mozilla.firefox";
            case "microsoft-edge":
            case "edge":
                return "com.microsoft.emmx";
            case "opera":
                return "com.opera.browser";
            case "opera-mini":
                return "com.opera.mini.native";
            case "brave":
            case "brave-browser":
                return "com.brave.browser";
            case "duckduckgo":
            case "duckduckgo-browser":
                return "com.duckduckgo.mobile.android";
            case "samsung":
            case "samsung-browser":
                return "com.sec.android.app.sbrowser";
            case "vivaldi":
            case "vivaldi-browser":
                return "com.vivaldi.browser";
            case "kiwi":
            case "kiwi-browser":
                return "com.kiwibrowser.browser";
            case "uc":
            case "uc-browser":
                return "com.UCMobile.intl";
            case "tor":
            case "tor-browser":
                return "org.torproject.torbrowser";
        }
        return null;
    }

    public static boolean isBrowserPackageName(String name) {
        return name != null && (
            name.contains("browser") ||
            name.contains("chrome") ||
            name.contains("firefox") ||
            "com.microsoft.emmx".equals(name) ||
            "com.opera.mini.native".equals(name) ||
            "com.duckduckgo.mobile.android".equals(name) ||
            "com.UCMobile.intl".equals(name)
        );
    }

    public static boolean isPunycodeAllowed(String host) {
        if (host == null) return true;
        String[] levels = host.split("\\.");
        if (levels.length <= 0) return true;
        String topLevel = levels[levels.length - 1];
        return topLevel.startsWith("xn--");
    }

    public static String IDN_toUnicode(String host) {
        try {
            host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (!isPunycodeAllowed(host)) return host;
        try {
            host = IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return host;
    }

    public static String replaceHostname(Uri originalUri, String newHostname, String newScheme) {
        return replace(originalUri, newScheme, null, newHostname, null);
    }

    public static String replace(Uri originalUri, String newScheme, String newUserInfo, String newHostname, String newPath) {
        final StringBuilder modifiedUriBuilder = new StringBuilder();
        final String scheme = newScheme == null ? originalUri.getScheme() : newScheme;
        if (scheme != null) {
            modifiedUriBuilder.append(scheme).append("://");
        }
        if (newUserInfo == null) {
            if (originalUri.getUserInfo() != null) {
                modifiedUriBuilder.append(originalUri.getUserInfo()).append("@");
            }
        } else if (!TextUtils.isEmpty(newUserInfo)) {
            modifiedUriBuilder.append(newUserInfo).append("@");
        }
        if (newHostname == null) {
            if (originalUri.getHost() != null) {
                modifiedUriBuilder.append(originalUri.getHost());
            }
        } else {
            modifiedUriBuilder.append(newHostname);
        }
        if (originalUri.getPort() != -1) {
            modifiedUriBuilder.append(":").append(originalUri.getPort());
        }
        if (newPath != null) {
            modifiedUriBuilder.append(newPath);
        } else if (originalUri.getPath() != null) {
            modifiedUriBuilder.append(originalUri.getPath());
        }
        if (originalUri.getQuery() != null) {
            modifiedUriBuilder.append("?").append(originalUri.getQuery());
        }
        if (originalUri.getFragment() != null) {
            modifiedUriBuilder.append("#").append(originalUri.getFragment());
        }
        return modifiedUriBuilder.toString();
    }

}
