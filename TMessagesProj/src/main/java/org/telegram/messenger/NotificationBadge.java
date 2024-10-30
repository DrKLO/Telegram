/*
 https://github.com/leolin310148/ShortcutBadger
 */

package org.telegram.messenger;

import android.annotation.TargetApi;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class NotificationBadge {

    private static final List<Class<? extends Badger>> BADGERS = new LinkedList<>();
    private static boolean initied;
    private static Badger badger;
    private static ComponentName componentName;

    public interface Badger {
        void executeBadge(int badgeCount);

        List<String> getSupportLaunchers();
    }

    public static class AdwHomeBadger implements Badger {

        public static final String INTENT_UPDATE_COUNTER = "org.adw.launcher.counter.SEND";
        public static final String PACKAGENAME = "PNAME";
        public static final String CLASSNAME = "CNAME";
        public static final String COUNT = "COUNT";

        @Override
        public void executeBadge(int badgeCount) {

            final Intent intent = new Intent(INTENT_UPDATE_COUNTER);
            intent.putExtra(PACKAGENAME, componentName.getPackageName());
            intent.putExtra(CLASSNAME, componentName.getClassName());
            intent.putExtra(COUNT, badgeCount);
            if (canResolveBroadcast(intent)) {
                AndroidUtilities.runOnUIThread(() -> ApplicationLoader.applicationContext.sendBroadcast(intent));
            }
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList(
                    "org.adw.launcher",
                    "org.adwfreak.launcher"
            );
        }
    }

    public static class ApexHomeBadger implements Badger {

        private static final String INTENT_UPDATE_COUNTER = "com.anddoes.launcher.COUNTER_CHANGED";
        private static final String PACKAGENAME = "package";
        private static final String COUNT = "count";
        private static final String CLASS = "class";

        @Override
        public void executeBadge(int badgeCount) {

            final Intent intent = new Intent(INTENT_UPDATE_COUNTER);
            intent.putExtra(PACKAGENAME, componentName.getPackageName());
            intent.putExtra(COUNT, badgeCount);
            intent.putExtra(CLASS, componentName.getClassName());
            if (canResolveBroadcast(intent)) {
                AndroidUtilities.runOnUIThread(() -> ApplicationLoader.applicationContext.sendBroadcast(intent));
            }
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList("com.anddoes.launcher");
        }
    }

    public static class AsusHomeBadger implements Badger {

        private static final String INTENT_ACTION = "android.intent.action.BADGE_COUNT_UPDATE";
        private static final String INTENT_EXTRA_BADGE_COUNT = "badge_count";
        private static final String INTENT_EXTRA_PACKAGENAME = "badge_count_package_name";
        private static final String INTENT_EXTRA_ACTIVITY_NAME = "badge_count_class_name";

        @Override
        public void executeBadge(int badgeCount) {
            final Intent intent = new Intent(INTENT_ACTION);
            intent.putExtra(INTENT_EXTRA_BADGE_COUNT, badgeCount);
            intent.putExtra(INTENT_EXTRA_PACKAGENAME, componentName.getPackageName());
            intent.putExtra(INTENT_EXTRA_ACTIVITY_NAME, componentName.getClassName());
            intent.putExtra("badge_vip_count", 0);
            if (canResolveBroadcast(intent)) {
                AndroidUtilities.runOnUIThread(() -> ApplicationLoader.applicationContext.sendBroadcast(intent));
            }
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList("com.asus.launcher");
        }
    }

    public static class DefaultBadger implements Badger {
        private static final String INTENT_ACTION = "android.intent.action.BADGE_COUNT_UPDATE";
        private static final String INTENT_EXTRA_BADGE_COUNT = "badge_count";
        private static final String INTENT_EXTRA_PACKAGENAME = "badge_count_package_name";
        private static final String INTENT_EXTRA_ACTIVITY_NAME = "badge_count_class_name";

        @Override
        public void executeBadge(int badgeCount) {
            final Intent intent = new Intent(INTENT_ACTION);
            intent.putExtra(INTENT_EXTRA_BADGE_COUNT, badgeCount);
            intent.putExtra(INTENT_EXTRA_PACKAGENAME, componentName.getPackageName());
            intent.putExtra(INTENT_EXTRA_ACTIVITY_NAME, componentName.getClassName());
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ApplicationLoader.applicationContext.sendBroadcast(intent);
                } catch (Exception ignore) {

                }
            });
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList(
                    "fr.neamar.kiss",
                    "com.quaap.launchtime",
                    "com.quaap.launchtime_official"
            );
        }
    }

    public static class HuaweiHomeBadger implements Badger {

        @Override
        public void executeBadge(int badgeCount) {
            final Bundle localBundle = new Bundle();
            localBundle.putString("package", ApplicationLoader.applicationContext.getPackageName());
            localBundle.putString("class", componentName.getClassName());
            localBundle.putInt("badgenumber", badgeCount);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ApplicationLoader.applicationContext.getContentResolver().call(Uri.parse("content://com.huawei.android.launcher.settings/badge/"), "change_badge", null, localBundle);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList(
                    "com.huawei.android.launcher"
            );
        }
    }

    public static class NewHtcHomeBadger implements Badger {

        public static final String INTENT_UPDATE_SHORTCUT = "com.htc.launcher.action.UPDATE_SHORTCUT";
        public static final String INTENT_SET_NOTIFICATION = "com.htc.launcher.action.SET_NOTIFICATION";
        public static final String PACKAGENAME = "packagename";
        public static final String COUNT = "count";
        public static final String EXTRA_COMPONENT = "com.htc.launcher.extra.COMPONENT";
        public static final String EXTRA_COUNT = "com.htc.launcher.extra.COUNT";

        @Override
        public void executeBadge(int badgeCount) {

            final Intent intent1 = new Intent(INTENT_SET_NOTIFICATION);
            intent1.putExtra(EXTRA_COMPONENT, componentName.flattenToShortString());
            intent1.putExtra(EXTRA_COUNT, badgeCount);

            final Intent intent = new Intent(INTENT_UPDATE_SHORTCUT);
            intent.putExtra(PACKAGENAME, componentName.getPackageName());
            intent.putExtra(COUNT, badgeCount);

            if (canResolveBroadcast(intent1) || canResolveBroadcast(intent)) {
                AndroidUtilities.runOnUIThread(() -> {
                    ApplicationLoader.applicationContext.sendBroadcast(intent1);
                    ApplicationLoader.applicationContext.sendBroadcast(intent);
                });
            }
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList("com.htc.launcher");
        }
    }

    public static class NovaHomeBadger implements Badger {

        private static final String CONTENT_URI = "content://com.teslacoilsw.notifier/unread_count";
        private static final String COUNT = "count";
        private static final String TAG = "tag";

        @Override
        public void executeBadge(int badgeCount) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(TAG, componentName.getPackageName() + "/" + componentName.getClassName());
            contentValues.put(COUNT, badgeCount);
            ApplicationLoader.applicationContext.getContentResolver().insert(Uri.parse(CONTENT_URI), contentValues);
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList("com.teslacoilsw.launcher");
        }
    }

    public static class OPPOHomeBader implements Badger {

        private static final String PROVIDER_CONTENT_URI = "content://com.android.badge/badge";
        private static final String INTENT_ACTION = "com.oppo.unsettledevent";
        private static final String INTENT_EXTRA_PACKAGENAME = "pakeageName";
        private static final String INTENT_EXTRA_BADGE_COUNT = "number";
        private static final String INTENT_EXTRA_BADGE_UPGRADENUMBER = "upgradeNumber";
        private static final String INTENT_EXTRA_BADGEUPGRADE_COUNT = "app_badge_count";
        private int mCurrentTotalCount = -1;

        @Override
        public void executeBadge(int badgeCount) {
            if (mCurrentTotalCount == badgeCount) {
                return;
            }
            mCurrentTotalCount = badgeCount;
            executeBadgeByContentProvider(badgeCount);
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Collections.singletonList("com.oppo.launcher");
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        private void executeBadgeByContentProvider(int badgeCount) {
            try {
                Bundle extras = new Bundle();
                extras.putInt(INTENT_EXTRA_BADGEUPGRADE_COUNT, badgeCount);
                ApplicationLoader.applicationContext.getContentResolver().call(Uri.parse(PROVIDER_CONTENT_URI), "setAppBadgeCount", null, extras);
            } catch (Throwable ignored) {

            }
        }
    }

    public static class SamsungHomeBadger implements Badger {
        private static final String CONTENT_URI = "content://com.sec.badge/apps?notify=true";
        private static final String[] CONTENT_PROJECTION = new String[]{"_id","class"};

        private static DefaultBadger defaultBadger;

        @Override
        public void executeBadge(int badgeCount) {
            try {
                if (defaultBadger == null) {
                    defaultBadger = new DefaultBadger();
                }
                defaultBadger.executeBadge(badgeCount);
            } catch (Exception ignore) {

            }

            Uri mUri = Uri.parse(CONTENT_URI);
            ContentResolver contentResolver = ApplicationLoader.applicationContext.getContentResolver();
            Cursor cursor = null;
            try {
                cursor = contentResolver.query(mUri, CONTENT_PROJECTION, "package=?", new String[]{componentName.getPackageName()}, null);
                if (cursor != null) {
                    String entryActivityName = componentName.getClassName();
                    boolean entryActivityExist = false;
                    while (cursor.moveToNext()) {
                        int id = cursor.getInt(0);
                        ContentValues contentValues = getContentValues(componentName, badgeCount, false);
                        contentResolver.update(mUri, contentValues, "_id=?", new String[]{String.valueOf(id)});
                        if (entryActivityName.equals(cursor.getString(cursor.getColumnIndex("class")))) {
                            entryActivityExist = true;
                        }
                    }

                    if (!entryActivityExist) {
                        ContentValues contentValues = getContentValues(componentName, badgeCount, true);
                        contentResolver.insert(mUri, contentValues);
                    }
                }
            } finally {
                close(cursor);
            }
        }

        private ContentValues getContentValues(ComponentName componentName, int badgeCount, boolean isInsert) {
            ContentValues contentValues = new ContentValues();
            if (isInsert) {
                contentValues.put("package", componentName.getPackageName());
                contentValues.put("class", componentName.getClassName());
            }

            contentValues.put("badgecount", badgeCount);

            return contentValues;
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList(
                    "com.sec.android.app.launcher",
                    "com.sec.android.app.twlauncher"
            );
        }
    }

    public static class SonyHomeBadger implements Badger {

        private static final String INTENT_ACTION = "com.sonyericsson.home.action.UPDATE_BADGE";
        private static final String INTENT_EXTRA_PACKAGE_NAME = "com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME";
        private static final String INTENT_EXTRA_ACTIVITY_NAME = "com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME";
        private static final String INTENT_EXTRA_MESSAGE = "com.sonyericsson.home.intent.extra.badge.MESSAGE";
        private static final String INTENT_EXTRA_SHOW_MESSAGE = "com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE";

        private static final String PROVIDER_CONTENT_URI = "content://com.sonymobile.home.resourceprovider/badge";
        private static final String PROVIDER_COLUMNS_BADGE_COUNT = "badge_count";
        private static final String PROVIDER_COLUMNS_PACKAGE_NAME = "package_name";
        private static final String PROVIDER_COLUMNS_ACTIVITY_NAME = "activity_name";
        private static final String SONY_HOME_PROVIDER_NAME = "com.sonymobile.home.resourceprovider";
        private final Uri BADGE_CONTENT_URI = Uri.parse(PROVIDER_CONTENT_URI);

        private static AsyncQueryHandler mQueryHandler;

        @Override
        public void executeBadge(int badgeCount) {
            if (sonyBadgeContentProviderExists()) {
                executeBadgeByContentProvider(badgeCount);
            } else {
                executeBadgeByBroadcast(badgeCount);
            }
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList("com.sonyericsson.home", "com.sonymobile.home");
        }

        private static void executeBadgeByBroadcast(int badgeCount) {
            final Intent intent = new Intent(INTENT_ACTION);
            intent.putExtra(INTENT_EXTRA_PACKAGE_NAME, componentName.getPackageName());
            intent.putExtra(INTENT_EXTRA_ACTIVITY_NAME, componentName.getClassName());
            intent.putExtra(INTENT_EXTRA_MESSAGE, String.valueOf(badgeCount));
            intent.putExtra(INTENT_EXTRA_SHOW_MESSAGE, badgeCount > 0);
            AndroidUtilities.runOnUIThread(() -> ApplicationLoader.applicationContext.sendBroadcast(intent));
        }

        private void executeBadgeByContentProvider(int badgeCount) {
            if (badgeCount < 0) {
                return;
            }

            if (mQueryHandler == null) {
                mQueryHandler = new AsyncQueryHandler(ApplicationLoader.applicationContext.getApplicationContext().getContentResolver()) {

                    @Override
                    public void handleMessage(Message msg) {
                        try {
                            super.handleMessage(msg);
                        } catch (Throwable ignore) {

                        }
                    }
                };
            }
            insertBadgeAsync(badgeCount, componentName.getPackageName(), componentName.getClassName());
        }

        private void insertBadgeAsync(int badgeCount, String packageName, String activityName) {
            final ContentValues contentValues = new ContentValues();
            contentValues.put(PROVIDER_COLUMNS_BADGE_COUNT, badgeCount);
            contentValues.put(PROVIDER_COLUMNS_PACKAGE_NAME, packageName);
            contentValues.put(PROVIDER_COLUMNS_ACTIVITY_NAME, activityName);
            mQueryHandler.startInsert(0, null, BADGE_CONTENT_URI, contentValues);
        }

        private static boolean sonyBadgeContentProviderExists() {
            boolean exists = false;
            ProviderInfo info = ApplicationLoader.applicationContext.getPackageManager().resolveContentProvider(SONY_HOME_PROVIDER_NAME, 0);
            if (info != null) {
                exists = true;
            }
            return exists;
        }
    }

    public static class XiaomiHomeBadger implements Badger {

        public static final String INTENT_ACTION = "android.intent.action.APPLICATION_MESSAGE_UPDATE";
        public static final String EXTRA_UPDATE_APP_COMPONENT_NAME = "android.intent.extra.update_application_component_name";
        public static final String EXTRA_UPDATE_APP_MSG_TEXT = "android.intent.extra.update_application_message_text";

        @Override
        public void executeBadge(int badgeCount) {
            try {
                Class miuiNotificationClass = Class.forName("android.app.MiuiNotification");
                Object miuiNotification = miuiNotificationClass.newInstance();
                Field field = miuiNotification.getClass().getDeclaredField("messageCount");
                field.setAccessible(true);
                field.set(miuiNotification, String.valueOf(badgeCount == 0 ? "" : badgeCount));
            } catch (Throwable e) {
                final Intent localIntent = new Intent(INTENT_ACTION);
                localIntent.putExtra(EXTRA_UPDATE_APP_COMPONENT_NAME, componentName.getPackageName() + "/" + componentName.getClassName());
                localIntent.putExtra(EXTRA_UPDATE_APP_MSG_TEXT, String.valueOf(badgeCount == 0 ? "" : badgeCount));
                if (canResolveBroadcast(localIntent)) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            ApplicationLoader.applicationContext.sendBroadcast(localIntent);
                        }
                    });
                }
            }
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList(
                    "com.miui.miuilite",
                    "com.miui.home",
                    "com.miui.miuihome",
                    "com.miui.miuihome2",
                    "com.miui.mihome",
                    "com.miui.mihome2"
            );
        }
    }

    public static class ZukHomeBadger implements Badger {

        private final Uri CONTENT_URI = Uri.parse("content://com.android.badge/badge");

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @Override
        public void executeBadge(int badgeCount) {
            final Bundle extra = new Bundle();
            extra.putInt("app_badge_count", badgeCount);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ApplicationLoader.applicationContext.getContentResolver().call(CONTENT_URI, "setAppBadgeCount", null, extra);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Collections.singletonList("com.zui.launcher");
        }
    }

    public static class VivoHomeBadger implements Badger {

        @Override
        public void executeBadge(int badgeCount) {
            Intent intent = new Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM");
            intent.setPackage("com.vivo.launcher");
            intent.putExtra("packageName", ApplicationLoader.applicationContext.getPackageName());
            intent.putExtra("className", componentName.getClassName());
            intent.putExtra("notificationNum", badgeCount);
            ApplicationLoader.applicationContext.sendBroadcast(intent);
        }

        @Override
        public List<String> getSupportLaunchers() {
            return Arrays.asList("com.vivo.launcher");
        }
    }

    static {
        BADGERS.add(AdwHomeBadger.class);
        BADGERS.add(ApexHomeBadger.class);
        BADGERS.add(NewHtcHomeBadger.class);
        BADGERS.add(NovaHomeBadger.class);
        BADGERS.add(SonyHomeBadger.class);
        BADGERS.add(XiaomiHomeBadger.class);
        BADGERS.add(AsusHomeBadger.class);
        BADGERS.add(HuaweiHomeBadger.class);
        BADGERS.add(OPPOHomeBader.class);
        BADGERS.add(SamsungHomeBadger.class);
        BADGERS.add(ZukHomeBadger.class);
        BADGERS.add(VivoHomeBadger.class);
    }

    public static boolean applyCount(int badgeCount) {
        try {
            if (badger == null && !initied) {
                initBadger();
                initied = true;
            }
            if (badger == null) {
                return false;
            }
            badger.executeBadge(badgeCount);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean initBadger() {
        Context context = ApplicationLoader.applicationContext;
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent == null) {
            return false;
        }

        componentName = launchIntent.getComponent();

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null) {
            String currentHomePackage = resolveInfo.activityInfo.packageName;
            for (Class<? extends Badger> b : BADGERS) {
                Badger shortcutBadger = null;
                try {
                    shortcutBadger = b.newInstance();
                } catch (Exception ignored) {
                }
                if (shortcutBadger != null && shortcutBadger.getSupportLaunchers().contains(currentHomePackage)) {
                    badger = shortcutBadger;
                    break;
                }
            }
            if (badger != null) {
                return true;
            }
        }

        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfos != null) {
            for (int a = 0; a < resolveInfos.size(); a++) {
                resolveInfo = resolveInfos.get(a);
                String currentHomePackage = resolveInfo.activityInfo.packageName;

                for (Class<? extends Badger> b : BADGERS) {
                    Badger shortcutBadger = null;
                    try {
                        shortcutBadger = b.newInstance();
                    } catch (Exception ignored) {
                    }
                    if (shortcutBadger != null && shortcutBadger.getSupportLaunchers().contains(currentHomePackage)) {
                        badger = shortcutBadger;
                        break;
                    }
                }
                if (badger != null) {
                    break;
                }
            }
        }

        if (badger == null) {
            if (Build.MANUFACTURER.equalsIgnoreCase("Xiaomi")) {
                badger = new XiaomiHomeBadger();
            } else if (Build.MANUFACTURER.equalsIgnoreCase("ZUK")) {
                badger = new ZukHomeBadger();
            } else if (Build.MANUFACTURER.equalsIgnoreCase("OPPO")) {
                badger = new OPPOHomeBader();
            } else if (Build.MANUFACTURER.equalsIgnoreCase("VIVO")) {
                badger = new VivoHomeBadger();
            } else {
                badger = new DefaultBadger();
            }
        }

        return true;
    }

    private static boolean canResolveBroadcast(Intent intent) {
        PackageManager packageManager = ApplicationLoader.applicationContext.getPackageManager();
        List<ResolveInfo> receivers = packageManager.queryBroadcastReceivers(intent, 0);
        return receivers != null && receivers.size() > 0;
    }

    public static void close(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Throwable ignore) {

        }
    }
}
