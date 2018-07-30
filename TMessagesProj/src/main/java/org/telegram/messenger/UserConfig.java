/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.util.Base64;

import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.File;

public class UserConfig {

    public static int selectedAccount;
    public final static int MAX_ACCOUNT_COUNT = 3;

    private final Object sync = new Object();
    private boolean configLoaded;
    private TLRPC.User currentUser;
    public boolean registeredForPush;
    public int lastSendMessageId = -210000;
    public int lastBroadcastId = -1;
    public int contactsSavedCount;
    public int clientUserId;
    public boolean blockedUsersLoaded;
    public int lastContactsSyncTime;
    public int lastHintsSyncTime;
    public boolean draftsLoaded;
    public boolean pinnedDialogsLoaded = true;
    public boolean unreadDialogsLoaded = true;
    public TLRPC.TL_account_tmpPassword tmpPassword;
    public int ratingLoadTime;
    public int botRatingLoadTime;
    public boolean contactsReimported;
    public int migrateOffsetId = -1;
    public int migrateOffsetDate = -1;
    public int migrateOffsetUserId = -1;
    public int migrateOffsetChatId = -1;
    public int migrateOffsetChannelId = -1;
    public long migrateOffsetAccess = -1;
    public int totalDialogsLoadCount = 0;
    public int dialogsLoadOffsetId = 0;
    public int dialogsLoadOffsetDate = 0;
    public int dialogsLoadOffsetUserId = 0;
    public int dialogsLoadOffsetChatId = 0;
    public int dialogsLoadOffsetChannelId = 0;
    public long dialogsLoadOffsetAccess = 0;
    public boolean notificationsSettingsLoaded;
    public boolean syncContacts = true;
    public boolean suggestContacts = true;
    public boolean hasSecureData;
    public int loginTime;
    public TLRPC.TL_help_termsOfService unacceptedTermsOfService;
    public TLRPC.TL_help_appUpdate pendingAppUpdate;
    public int pendingAppUpdateBuildVersion;
    public long pendingAppUpdateInstallTime;
    public long lastUpdateCheckTime;

    public volatile byte[] savedPasswordHash;
    public volatile byte[] savedSaltedPassword;
    public volatile long savedPasswordTime;

    private int currentAccount;
    private static volatile UserConfig[] Instance = new UserConfig[UserConfig.MAX_ACCOUNT_COUNT];
    public static UserConfig getInstance(int num) {
        UserConfig localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (UserConfig.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new UserConfig(num);
                }
            }
        }
        return localInstance;
    }

    public static int getActivatedAccountsCount() {
        int count = 0;
        for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
            if (getInstance(a).isClientActivated()) {
                count++;
            }
        }
        return count;
    }

    public UserConfig(int instance) {
        currentAccount = instance;
    }

    public int getNewMessageId() {
        int id;
        synchronized (sync) {
            id = lastSendMessageId;
            lastSendMessageId--;
        }
        return id;
    }

    public void saveConfig(boolean withFile) {
        saveConfig(withFile, null);
    }

    public void saveConfig(boolean withFile, File oldFile) {
        synchronized (sync) {
            try {
                SharedPreferences preferences;
                if (currentAccount == 0) {
                    preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                } else {
                    preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfig" + currentAccount, Context.MODE_PRIVATE);
                }
                SharedPreferences.Editor editor = preferences.edit();
                if (currentAccount == 0) {
                    editor.putInt("selectedAccount", selectedAccount);
                }
                editor.putBoolean("registeredForPush", registeredForPush);
                editor.putInt("lastSendMessageId", lastSendMessageId);
                editor.putInt("contactsSavedCount", contactsSavedCount);
                editor.putInt("lastBroadcastId", lastBroadcastId);
                editor.putBoolean("blockedUsersLoaded", blockedUsersLoaded);
                editor.putInt("lastContactsSyncTime", lastContactsSyncTime);
                editor.putInt("lastHintsSyncTime", lastHintsSyncTime);
                editor.putBoolean("draftsLoaded", draftsLoaded);
                editor.putBoolean("pinnedDialogsLoaded", pinnedDialogsLoaded);
                editor.putBoolean("unreadDialogsLoaded", unreadDialogsLoaded);
                editor.putInt("ratingLoadTime", ratingLoadTime);
                editor.putInt("botRatingLoadTime", botRatingLoadTime);
                editor.putBoolean("contactsReimported", contactsReimported);
                editor.putInt("loginTime", loginTime);
                editor.putBoolean("syncContacts", syncContacts);
                editor.putBoolean("suggestContacts", suggestContacts);
                editor.putBoolean("hasSecureData", hasSecureData);
                editor.putBoolean("notificationsSettingsLoaded", notificationsSettingsLoaded);

                editor.putInt("3migrateOffsetId", migrateOffsetId);
                if (migrateOffsetId != -1) {
                    editor.putInt("3migrateOffsetDate", migrateOffsetDate);
                    editor.putInt("3migrateOffsetUserId", migrateOffsetUserId);
                    editor.putInt("3migrateOffsetChatId", migrateOffsetChatId);
                    editor.putInt("3migrateOffsetChannelId", migrateOffsetChannelId);
                    editor.putLong("3migrateOffsetAccess", migrateOffsetAccess);
                }

                if (unacceptedTermsOfService != null) {
                    try {
                        SerializedData data = new SerializedData(unacceptedTermsOfService.getObjectSize());
                        unacceptedTermsOfService.serializeToStream(data);
                        String str = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                        editor.putString("terms", str);
                        data.cleanup();
                    } catch (Exception ignore) {

                    }
                } else {
                    editor.remove("terms");
                }

                if (currentAccount == 0) {
                    if (pendingAppUpdate != null) {
                        try {
                            SerializedData data = new SerializedData(pendingAppUpdate.getObjectSize());
                            pendingAppUpdate.serializeToStream(data);
                            String str = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                            editor.putString("appUpdate", str);
                            editor.putInt("appUpdateBuild", pendingAppUpdateBuildVersion);
                            editor.putLong("appUpdateTime", pendingAppUpdateInstallTime);
                            editor.putLong("appUpdateCheckTime", lastUpdateCheckTime);
                            data.cleanup();
                        } catch (Exception ignore) {

                        }
                    } else {
                        editor.remove("appUpdate");
                    }
                }

                editor.putInt("2totalDialogsLoadCount", totalDialogsLoadCount);
                editor.putInt("2dialogsLoadOffsetId", dialogsLoadOffsetId);
                editor.putInt("2dialogsLoadOffsetDate", dialogsLoadOffsetDate);
                editor.putInt("2dialogsLoadOffsetUserId", dialogsLoadOffsetUserId);
                editor.putInt("2dialogsLoadOffsetChatId", dialogsLoadOffsetChatId);
                editor.putInt("2dialogsLoadOffsetChannelId", dialogsLoadOffsetChannelId);
                editor.putLong("2dialogsLoadOffsetAccess", dialogsLoadOffsetAccess);

                SharedConfig.saveConfig();

                if (tmpPassword != null) {
                    SerializedData data = new SerializedData();
                    tmpPassword.serializeToStream(data);
                    String string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                    editor.putString("tmpPassword", string);
                    data.cleanup();
                } else {
                    editor.remove("tmpPassword");
                }

                if (currentUser != null) {
                    if (withFile) {
                        SerializedData data = new SerializedData();
                        currentUser.serializeToStream(data);
                        String string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                        editor.putString("user", string);
                        data.cleanup();
                    }
                } else {
                    editor.remove("user");
                }

                editor.commit();
                if (oldFile != null) {
                    oldFile.delete();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public boolean isClientActivated() {
        synchronized (sync) {
            return currentUser != null;
        }
    }

    public int getClientUserId() {
        synchronized (sync) {
            return currentUser != null ? currentUser.id : 0;
        }
    }

    public String getClientPhone() {
        synchronized (sync) {
            return currentUser != null && currentUser.phone != null ? currentUser.phone : "";
        }
    }

    public TLRPC.User getCurrentUser() {
        synchronized (sync) {
            return currentUser;
        }
    }

    public void setCurrentUser(TLRPC.User user) {
        synchronized (sync) {
            currentUser = user;
            clientUserId = user.id;
        }
    }

    public void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }
            SharedPreferences preferences;
            if (currentAccount == 0) {
                preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                selectedAccount = preferences.getInt("selectedAccount", 0);
            } else {
                preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfig" + currentAccount, Context.MODE_PRIVATE);
            }
            registeredForPush = preferences.getBoolean("registeredForPush", false);
            lastSendMessageId = preferences.getInt("lastSendMessageId", -210000);
            contactsSavedCount = preferences.getInt("contactsSavedCount", 0);
            lastBroadcastId = preferences.getInt("lastBroadcastId", -1);
            blockedUsersLoaded = preferences.getBoolean("blockedUsersLoaded", false);
            lastContactsSyncTime = preferences.getInt("lastContactsSyncTime", (int) (System.currentTimeMillis() / 1000) - 23 * 60 * 60);
            lastHintsSyncTime = preferences.getInt("lastHintsSyncTime", (int) (System.currentTimeMillis() / 1000) - 25 * 60 * 60);
            draftsLoaded = preferences.getBoolean("draftsLoaded", false);
            pinnedDialogsLoaded = preferences.getBoolean("pinnedDialogsLoaded", false);
            unreadDialogsLoaded = preferences.getBoolean("unreadDialogsLoaded", false);
            contactsReimported = preferences.getBoolean("contactsReimported", false);
            ratingLoadTime = preferences.getInt("ratingLoadTime", 0);
            botRatingLoadTime = preferences.getInt("botRatingLoadTime", 0);
            loginTime = preferences.getInt("loginTime", currentAccount);
            syncContacts = preferences.getBoolean("syncContacts", true);
            suggestContacts = preferences.getBoolean("suggestContacts", true);
            hasSecureData = preferences.getBoolean("hasSecureData", false);
            notificationsSettingsLoaded = preferences.getBoolean("notificationsSettingsLoaded", false);

            try {
                String terms = preferences.getString("terms", null);
                if (terms != null) {
                    byte[] arr = Base64.decode(terms, Base64.DEFAULT);
                    if (arr != null) {
                        SerializedData data = new SerializedData(arr);
                        unacceptedTermsOfService = TLRPC.TL_help_termsOfService.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (currentAccount == 0) {
                lastUpdateCheckTime = preferences.getLong("appUpdateCheckTime", System.currentTimeMillis());
                try {
                    String update = preferences.getString("appUpdate", null);
                    if (update != null) {
                        pendingAppUpdateBuildVersion = preferences.getInt("appUpdateBuild", BuildVars.BUILD_VERSION);
                        pendingAppUpdateInstallTime = preferences.getLong("appUpdateTime", System.currentTimeMillis());
                        byte[] arr = Base64.decode(update, Base64.DEFAULT);
                        if (arr != null) {
                            SerializedData data = new SerializedData(arr);
                            pendingAppUpdate = (TLRPC.TL_help_appUpdate) TLRPC.help_AppUpdate.TLdeserialize(data, data.readInt32(false), false);
                            data.cleanup();
                        }
                    }
                    if (pendingAppUpdate != null) {
                        long updateTime = 0;
                        try {
                            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                            updateTime = Math.max(packageInfo.lastUpdateTime, packageInfo.firstInstallTime);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (pendingAppUpdateBuildVersion != BuildVars.BUILD_VERSION || pendingAppUpdateInstallTime < updateTime) {
                            pendingAppUpdate = null;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    saveConfig(false);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            migrateOffsetId = preferences.getInt("3migrateOffsetId", 0);
            if (migrateOffsetId != -1) {
                migrateOffsetDate = preferences.getInt("3migrateOffsetDate", 0);
                migrateOffsetUserId = preferences.getInt("3migrateOffsetUserId", 0);
                migrateOffsetChatId = preferences.getInt("3migrateOffsetChatId", 0);
                migrateOffsetChannelId = preferences.getInt("3migrateOffsetChannelId", 0);
                migrateOffsetAccess = preferences.getLong("3migrateOffsetAccess", 0);
            }

            dialogsLoadOffsetId = preferences.getInt("2dialogsLoadOffsetId", -1);
            totalDialogsLoadCount = preferences.getInt("2totalDialogsLoadCount", 0);
            dialogsLoadOffsetDate = preferences.getInt("2dialogsLoadOffsetDate", -1);
            dialogsLoadOffsetUserId = preferences.getInt("2dialogsLoadOffsetUserId", -1);
            dialogsLoadOffsetChatId = preferences.getInt("2dialogsLoadOffsetChatId", -1);
            dialogsLoadOffsetChannelId = preferences.getInt("2dialogsLoadOffsetChannelId", -1);
            dialogsLoadOffsetAccess = preferences.getLong("2dialogsLoadOffsetAccess", -1);

            String string = preferences.getString("tmpPassword", null);
            if (string != null) {
                byte[] bytes = Base64.decode(string, Base64.DEFAULT);
                if (bytes != null) {
                    SerializedData data = new SerializedData(bytes);
                    tmpPassword = TLRPC.TL_account_tmpPassword.TLdeserialize(data, data.readInt32(false), false);
                    data.cleanup();
                }
            }

            string = preferences.getString("user", null);
            if (string != null) {
                byte[] bytes = Base64.decode(string, Base64.DEFAULT);
                if (bytes != null) {
                    SerializedData data = new SerializedData(bytes);
                    currentUser = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                    data.cleanup();
                }
            }
            if (currentUser != null) {
                clientUserId = currentUser.id;
            }
            configLoaded = true;
        }
    }

    public void savePassword(byte[] hash, byte[] salted) {
        savedPasswordTime = SystemClock.elapsedRealtime();
        savedPasswordHash = hash;
        savedSaltedPassword = salted;
    }

    public void checkSavedPassword() {
        if (savedSaltedPassword == null && savedPasswordHash == null || Math.abs(SystemClock.elapsedRealtime() - savedPasswordTime) < 30 * 60 * 1000) {
            return;
        }
        resetSavedPassword();
    }

    public void resetSavedPassword() {
        savedPasswordTime = 0;
        if (savedPasswordHash != null) {
            for (int a = 0; a < savedPasswordHash.length; a++) {
                savedPasswordHash[a] = 0;
            }
            savedPasswordHash = null;
        }
        if (savedSaltedPassword != null) {
            for (int a = 0; a < savedSaltedPassword.length; a++) {
                savedSaltedPassword[a] = 0;
            }
            savedSaltedPassword = null;
        }
    }

    public void clearConfig() {
        currentUser = null;
        clientUserId = 0;
        registeredForPush = false;
        contactsSavedCount = 0;
        lastSendMessageId = -210000;
        lastBroadcastId = -1;
        blockedUsersLoaded = false;
        notificationsSettingsLoaded = false;
        migrateOffsetId = -1;
        migrateOffsetDate = -1;
        migrateOffsetUserId = -1;
        migrateOffsetChatId = -1;
        migrateOffsetChannelId = -1;
        migrateOffsetAccess = -1;
        dialogsLoadOffsetId = 0;
        totalDialogsLoadCount = 0;
        dialogsLoadOffsetDate = 0;
        dialogsLoadOffsetUserId = 0;
        dialogsLoadOffsetChatId = 0;
        dialogsLoadOffsetChannelId = 0;
        dialogsLoadOffsetAccess = 0;
        ratingLoadTime = 0;
        botRatingLoadTime = 0;
        draftsLoaded = true;
        contactsReimported = true;
        syncContacts = true;
        suggestContacts = true;
        pinnedDialogsLoaded = false;
        unreadDialogsLoaded = true;
        unacceptedTermsOfService = null;
        pendingAppUpdate = null;
        hasSecureData = false;
        loginTime = (int) (System.currentTimeMillis() / 1000);
        lastContactsSyncTime = (int) (System.currentTimeMillis() / 1000) - 23 * 60 * 60;
        lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000) - 25 * 60 * 60;
        resetSavedPassword();
        boolean hasActivated = false;
        for (int a = 0; a < MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                hasActivated = true;
                break;
            }
        }
        if (!hasActivated) {
            SharedConfig.clearConfig();
        }
        saveConfig(true);
    }
}
