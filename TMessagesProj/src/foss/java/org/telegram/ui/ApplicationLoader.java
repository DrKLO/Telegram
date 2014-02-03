/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.view.ViewConfiguration;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BaseFragment;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ApplicationLoader extends Application {
    private AtomicInteger msgId = new AtomicInteger();
    private String regid;
    private String SENDER_ID = "760348033672";
    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    public static long lastPauseTime;
    public static Bitmap cachedWallpaper = null;
    public static Context applicationContext;

    public static ApplicationLoader Instance = null;

    public static ArrayList<BaseFragment> fragmentsStack = new ArrayList<BaseFragment>();

    @Override
    public void onCreate() {
        super.onCreate();
        Instance = this;

        java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");

        applicationContext = getApplicationContext();
        Utilities.getTypeface("fonts/rmedium.ttf");
        UserConfig.loadConfig();
        SharedPreferences preferences = getSharedPreferences("Notifications", MODE_PRIVATE);
        if (UserConfig.currentUser != null) {
            int value = preferences.getInt("version", 0);
            if (value != 15) {
                UserConfig.contactsHash = "";
                MessagesStorage.lastDateValue = 0;
                MessagesStorage.lastPtsValue = 0;
                MessagesStorage.lastSeqValue = 0;
                MessagesStorage.lastQtsValue = 0;
                UserConfig.saveConfig(false);
                MessagesStorage.Instance.cleanUp();
                ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                users.add(UserConfig.currentUser);
                MessagesStorage.Instance.putUsersAndChats(users, null, true, true);

                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("version", 15);
                editor.commit();
            } else {
                MessagesStorage init = MessagesStorage.Instance;
            }
            MessagesController.Instance.users.put(UserConfig.clientUserId, UserConfig.currentUser);
        } else {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("version", 15);
            editor.commit();
        }
        MessagesController.Instance.checkAppAccount();

        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if(menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        FileLog.d("tmessages", "This build doesn't support Google Play Services.");

        PhoneFormat format = PhoneFormat.Instance;

        lastPauseTime = System.currentTimeMillis();
        FileLog.e("tmessages", "start application with time " + lastPauseTime);
    }

    public static void resetLastPauseTime() {
        lastPauseTime = 0;
        ConnectionsManager.Instance.applicationMovedToForeground();
    }

    public static int getAppVersion() {
        try {
            PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

}
