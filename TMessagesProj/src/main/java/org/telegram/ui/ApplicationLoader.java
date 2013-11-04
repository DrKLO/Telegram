/*
 * This is the source code of Telegram for Android v. 1.2.3.
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
import android.util.Log;
import android.view.ViewConfiguration;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLRPC;
import org.telegram.messenger.ConnectionsManager;
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
    private GoogleCloudMessaging gcm;
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
        Utilities.getTypeface("fonts/rlight.ttf");
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
            e.printStackTrace();
        }

        if (checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(applicationContext);

            if (regid.length() == 0) {
                registerInBackground();
            } else {
                sendRegistrationIdToBackend(false);
            }
        } else {
            Log.i("tmessages", "No valid Google Play Services APK found.");
        }

        PhoneFormat format = PhoneFormat.Instance;

        lastPauseTime = System.currentTimeMillis() - 5000;
        if (ConnectionsManager.DEBUG_VERSION) {
            Log.e("tmessages", "start application with time " + lastPauseTime);
        }
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        return resultCode == ConnectionResult.SUCCESS;
        /*if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i("tmessages", "This device is not supported.");
            }
            return false;
        }
        return true;*/
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.length() == 0) {
            Log.i("tmessages", "Registration not found.");
            return "";
        }
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i("tmessages", "App version changed.");
            return "";
        }
        return registrationId;
    }

    private SharedPreferences getGCMPreferences(Context context) {
        return getSharedPreferences(ApplicationLoader.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    private void registerInBackground() {
        AsyncTask<String, String, Boolean> task = new AsyncTask<String, String, Boolean>() {
            @Override
            protected Boolean doInBackground(String... objects) {
                String msg;
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(applicationContext);
                    }
                    regid = gcm.register(SENDER_ID);
                    sendRegistrationIdToBackend(true);
                    storeRegistrationId(applicationContext, regid);
                    return true;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                return false;
            }
        }.execute(null, null, null);
    }

    private void sendRegistrationIdToBackend(final boolean isNew) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                UserConfig.pushString = regid;
                UserConfig.registeredForPush = !isNew;
                UserConfig.saveConfig(false);
                MessagesController.Instance.registerForPush(regid);
            }
        });
    }

    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i("tmessages", "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }
}
