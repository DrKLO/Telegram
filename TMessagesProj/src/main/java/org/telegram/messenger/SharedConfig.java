/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseArray;
import android.webkit.WebView;

import androidx.annotation.IntDef;
import androidx.core.content.pm.ShortcutManagerCompat;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.SwipeGestureSettingsView;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SharedConfig {
    /**
     * V2: Ping and check time serialized
     */
    private final static int PROXY_SCHEMA_V2 = 2;
    private final static int PROXY_CURRENT_SCHEMA_VERSION = PROXY_SCHEMA_V2;

    public final static int PASSCODE_TYPE_PIN = 0,
            PASSCODE_TYPE_PASSWORD = 1;

    public static LiteMode getLiteMode() {
        if (liteMode == null) {
            liteMode = new LiteMode();
        }
        return liteMode;
    }

    public static boolean loopStickers() {
        return loopStickers && !getLiteMode().enabled;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            PASSCODE_TYPE_PIN,
            PASSCODE_TYPE_PASSWORD
    })
    public @interface PasscodeType {}

    public final static int SAVE_TO_GALLERY_FLAG_PEER = 1;
    public final static int SAVE_TO_GALLERY_FLAG_GROUP = 2;
    public final static int SAVE_TO_GALLERY_FLAG_CHANNELS = 4;

    @PushListenerController.PushType
    public static int pushType = PushListenerController.PUSH_TYPE_FIREBASE;
    public static String pushString = "";
    public static String pushStringStatus = "";
    public static long pushStringGetTimeStart;
    public static long pushStringGetTimeEnd;
    public static boolean pushStatSent;
    public static byte[] pushAuthKey;
    public static byte[] pushAuthKeyId;

    public static String directShareHash;

    @PasscodeType
    public static int passcodeType;
    public static String passcodeHash = "";
    public static long passcodeRetryInMs;
    public static long lastUptimeMillis;
    public static int badPasscodeTries;
    public static byte[] passcodeSalt = new byte[0];
    public static boolean appLocked;
    public static int autoLockIn = 60 * 60;

    public static boolean saveIncomingPhotos;
    public static boolean allowScreenCapture;
    public static int lastPauseTime;
    public static boolean isWaitingForPasscodeEnter;
    public static boolean useFingerprint = true;
    public static String lastUpdateVersion;
    public static int suggestStickers;
    public static boolean suggestAnimatedEmoji;
    public static boolean loopStickers;
    public static int keepMedia = CacheByChatsController.KEEP_MEDIA_ONE_MONTH; //deprecated
    public static int lastKeepMediaCheckTime;
    public static int lastLogsCheckTime;
    public static int searchMessagesAsListHintShows;
    public static int textSelectionHintShows;
    public static int scheduledOrNoSoundHintShows;
    public static int lockRecordAudioVideoHint;
    public static boolean forwardingOptionsHintShown;
    public static boolean searchMessagesAsListUsed;
    public static boolean stickersReorderingHintUsed;
    public static boolean disableVoiceAudioEffects;
    public static boolean forceDisableTabletMode;
    public static boolean useLNavigation;
    private static int lastLocalId = -210000;

    public static String storageCacheDir;

    private static String passportConfigJson = "";
    private static HashMap<String, String> passportConfigMap;
    public static int passportConfigHash;

    private static boolean configLoaded;
    private static final Object sync = new Object();
    private static final Object localIdSync = new Object();

//    public static int saveToGalleryFlags;
    public static int mapPreviewType = 2;
    public static boolean chatBubbles = Build.VERSION.SDK_INT >= 30;
    public static boolean autoplayGifs = true;
    public static boolean autoplayVideo = true;
    public static boolean raiseToSpeak = false;
    public static boolean recordViaSco = false;
    public static boolean customTabs = true;
    public static boolean directShare = true;
    public static boolean inappCamera = true;
    public static boolean roundCamera16to9 = true;
    public static boolean noSoundHintShowed = false;
    public static boolean streamMedia = true;
    public static boolean streamAllVideo = false;
    public static boolean streamMkv = false;
    public static boolean saveStreamMedia = true;
    public static boolean smoothKeyboard = true;
    public static boolean pauseMusicOnRecord = false;
    public static boolean chatBlur = true;
    public static boolean noiseSupression;
    public static boolean noStatusBar = true;
    public static boolean forceRtmpStream;
    public static boolean debugWebView;
    public static boolean sortContactsByName;
    public static boolean sortFilesByName;
    public static boolean shuffleMusic;
    public static boolean playOrderReversed;
    public static boolean hasCameraCache;
    public static boolean showNotificationsForAllAccounts = true;
    public static int repeatMode;
    public static boolean allowBigEmoji;
    public static boolean useSystemEmoji;
    public static int fontSize = 16;
    public static boolean fontSizeIsDefault;
    public static int bubbleRadius = 17;
    public static int ivFontSize = 16;
    public static boolean proxyRotationEnabled;
    public static int proxyRotationTimeout;
    public static int messageSeenHintCount;
    public static int emojiInteractionsHintCount;
    public static int dayNightThemeSwitchHintCount;

    public static TLRPC.TL_help_appUpdate pendingAppUpdate;
    public static int pendingAppUpdateBuildVersion;
    public static long lastUpdateCheckTime;

    public static boolean hasEmailLogin;

    private static int devicePerformanceClass;

    public static boolean drawDialogIcons;
    public static boolean useThreeLinesLayout;
    public static boolean archiveHidden;

    private static int chatSwipeAction;

    public static int distanceSystemType;
    public static int mediaColumnsCount = 3;
    public static int fastScrollHintCount = 3;
    public static boolean dontAskManageStorage;

    public static boolean translateChats = true;

    public static boolean isFloatingDebugActive;
    public static LiteMode liteMode;
    public static Set<String> usingFilePaths = Collections.newSetFromMap(new ConcurrentHashMap<>());

    static {
        loadConfig();
    }

    public static class ProxyInfo {

        public String address;
        public int port;
        public String username;
        public String password;
        public String secret;

        public long proxyCheckPingId;
        public long ping;
        public boolean checking;
        public boolean available;
        public long availableCheckTime;

        public ProxyInfo(String address, int port, String username, String password, String secret) {
            this.address = address;
            this.port = port;
            this.username = username;
            this.password = password;
            this.secret = secret;
            if (this.address == null) {
                this.address = "";
            }
            if (this.password == null) {
                this.password = "";
            }
            if (this.username == null) {
                this.username = "";
            }
            if (this.secret == null) {
                this.secret = "";
            }
        }

        public String getLink() {
            StringBuilder url = new StringBuilder(!TextUtils.isEmpty(secret) ? "https://t.me/proxy?" : "https://t.me/socks?");
            try {
                url.append("server=").append(URLEncoder.encode(address, "UTF-8")).append("&").append("port=").append(port);
                if (!TextUtils.isEmpty(username)) {
                    url.append("&user=").append(URLEncoder.encode(username, "UTF-8"));
                }
                if (!TextUtils.isEmpty(password)) {
                    url.append("&pass=").append(URLEncoder.encode(password, "UTF-8"));
                }
                if (!TextUtils.isEmpty(secret)) {
                    url.append("&secret=").append(URLEncoder.encode(secret, "UTF-8"));
                }
            } catch (UnsupportedEncodingException ignored) {}
            return url.toString();
        }
    }

    public static ArrayList<ProxyInfo> proxyList = new ArrayList<>();
    private static boolean proxyListLoaded;
    public static ProxyInfo currentProxy;

    public static void saveConfig() {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("saveIncomingPhotos", saveIncomingPhotos);
                editor.putString("passcodeHash1", passcodeHash);
                editor.putString("passcodeSalt", passcodeSalt.length > 0 ? Base64.encodeToString(passcodeSalt, Base64.DEFAULT) : "");
                editor.putBoolean("appLocked", appLocked);
                editor.putInt("passcodeType", passcodeType);
                editor.putLong("passcodeRetryInMs", passcodeRetryInMs);
                editor.putLong("lastUptimeMillis", lastUptimeMillis);
                editor.putInt("badPasscodeTries", badPasscodeTries);
                editor.putInt("autoLockIn", autoLockIn);
                editor.putInt("lastPauseTime", lastPauseTime);
                editor.putString("lastUpdateVersion2", lastUpdateVersion);
                editor.putBoolean("useFingerprint", useFingerprint);
                editor.putBoolean("allowScreenCapture", allowScreenCapture);
                editor.putString("pushString2", pushString);
                editor.putInt("pushType", pushType);
                editor.putBoolean("pushStatSent", pushStatSent);
                editor.putString("pushAuthKey", pushAuthKey != null ? Base64.encodeToString(pushAuthKey, Base64.DEFAULT) : "");
                editor.putInt("lastLocalId", lastLocalId);
                editor.putString("passportConfigJson", passportConfigJson);
                editor.putInt("passportConfigHash", passportConfigHash);
                editor.putBoolean("sortContactsByName", sortContactsByName);
                editor.putBoolean("sortFilesByName", sortFilesByName);
                editor.putInt("textSelectionHintShows", textSelectionHintShows);
                editor.putInt("scheduledOrNoSoundHintShows", scheduledOrNoSoundHintShows);
                editor.putBoolean("forwardingOptionsHintShown", forwardingOptionsHintShown);
                editor.putInt("lockRecordAudioVideoHint", lockRecordAudioVideoHint);
                editor.putString("storageCacheDir", !TextUtils.isEmpty(storageCacheDir) ? storageCacheDir : "");
                editor.putBoolean("proxyRotationEnabled", proxyRotationEnabled);
                editor.putInt("proxyRotationTimeout", proxyRotationTimeout);

                if (pendingAppUpdate != null) {
                    try {
                        SerializedData data = new SerializedData(pendingAppUpdate.getObjectSize());
                        pendingAppUpdate.serializeToStream(data);
                        String str = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                        editor.putString("appUpdate", str);
                        editor.putInt("appUpdateBuild", pendingAppUpdateBuildVersion);
                        data.cleanup();
                    } catch (Exception ignore) {

                    }
                } else {
                    editor.remove("appUpdate");
                }
                editor.putLong("appUpdateCheckTime", lastUpdateCheckTime);

                editor.apply();

                editor = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit();
                editor.putBoolean("hasEmailLogin", hasEmailLogin);
                editor.putBoolean("useLNavigation", useLNavigation);
                editor.putBoolean("floatingDebugActive", isFloatingDebugActive);
                editor.putBoolean("record_via_sco", recordViaSco);
                editor.apply();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static int getLastLocalId() {
        int value;
        synchronized (localIdSync) {
            value = lastLocalId--;
        }
        return value;
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded || ApplicationLoader.applicationContext == null) {
                return;
            }

            BackgroundActivityPrefs.prefs = ApplicationLoader.applicationContext.getSharedPreferences("background_activity", Context.MODE_PRIVATE);

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
            saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
            passcodeHash = preferences.getString("passcodeHash1", "");
            appLocked = preferences.getBoolean("appLocked", false);
            passcodeType = preferences.getInt("passcodeType", 0);
            passcodeRetryInMs = preferences.getLong("passcodeRetryInMs", 0);
            lastUptimeMillis = preferences.getLong("lastUptimeMillis", 0);
            badPasscodeTries = preferences.getInt("badPasscodeTries", 0);
            autoLockIn = preferences.getInt("autoLockIn", 60 * 60);
            lastPauseTime = preferences.getInt("lastPauseTime", 0);
            useFingerprint = preferences.getBoolean("useFingerprint", true);
            lastUpdateVersion = preferences.getString("lastUpdateVersion2", "3.5");
            allowScreenCapture = preferences.getBoolean("allowScreenCapture", false);
            lastLocalId = preferences.getInt("lastLocalId", -210000);
            pushString = preferences.getString("pushString2", "");
            pushType = preferences.getInt("pushType", PushListenerController.PUSH_TYPE_FIREBASE);
            pushStatSent = preferences.getBoolean("pushStatSent", false);
            passportConfigJson = preferences.getString("passportConfigJson", "");
            passportConfigHash = preferences.getInt("passportConfigHash", 0);
            storageCacheDir = preferences.getString("storageCacheDir", null);
            proxyRotationEnabled = preferences.getBoolean("proxyRotationEnabled", false);
            proxyRotationTimeout = preferences.getInt("proxyRotationTimeout", ProxyRotationController.DEFAULT_TIMEOUT_INDEX);
            String authKeyString = preferences.getString("pushAuthKey", null);
            if (!TextUtils.isEmpty(authKeyString)) {
                pushAuthKey = Base64.decode(authKeyString, Base64.DEFAULT);
            }

            if (passcodeHash.length() > 0 && lastPauseTime == 0) {
                lastPauseTime = (int) (SystemClock.elapsedRealtime() / 1000 - 60 * 10);
            }

            String passcodeSaltString = preferences.getString("passcodeSalt", "");
            if (passcodeSaltString.length() > 0) {
                passcodeSalt = Base64.decode(passcodeSaltString, Base64.DEFAULT);
            } else {
                passcodeSalt = new byte[0];
            }
            lastUpdateCheckTime = preferences.getLong("appUpdateCheckTime", System.currentTimeMillis());
            try {
                String update = preferences.getString("appUpdate", null);
                if (update != null) {
                    pendingAppUpdateBuildVersion = preferences.getInt("appUpdateBuild", BuildVars.BUILD_VERSION);
                    byte[] arr = Base64.decode(update, Base64.DEFAULT);
                    if (arr != null) {
                        SerializedData data = new SerializedData(arr);
                        pendingAppUpdate = (TLRPC.TL_help_appUpdate) TLRPC.help_AppUpdate.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }
                if (pendingAppUpdate != null) {
                    long updateTime = 0;
                    int updateVersion = 0;
                    String updateVersionString = null;
                    try {
                        PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                        updateVersion = packageInfo.versionCode;
                        updateVersionString = packageInfo.versionName;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (updateVersion == 0) {
                        updateVersion = BuildVars.BUILD_VERSION;
                    }
                    if (updateVersionString == null) {
                        updateVersionString = BuildVars.BUILD_VERSION_STRING;
                    }
                    if (pendingAppUpdateBuildVersion != updateVersion || pendingAppUpdate.version == null || updateVersionString.compareTo(pendingAppUpdate.version) >= 0 || BuildVars.DEBUG_PRIVATE_VERSION) {
                        pendingAppUpdate = null;
                        AndroidUtilities.runOnUIThread(SharedConfig::saveConfig);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SaveToGallerySettingsHelper.load(preferences);
            autoplayGifs = preferences.getBoolean("autoplay_gif", true);
            autoplayVideo = preferences.getBoolean("autoplay_video", true);
            mapPreviewType = preferences.getInt("mapPreviewType", 2);
            raiseToSpeak = preferences.getBoolean("raise_to_speak", false);
            recordViaSco = preferences.getBoolean("record_via_sco", false);
            customTabs = preferences.getBoolean("custom_tabs", true);
            directShare = preferences.getBoolean("direct_share", true);
            shuffleMusic = preferences.getBoolean("shuffleMusic", false);
            playOrderReversed = !shuffleMusic && preferences.getBoolean("playOrderReversed", false);
            inappCamera = preferences.getBoolean("inappCamera", true);
            hasCameraCache = preferences.contains("cameraCache");
            roundCamera16to9 = true;//preferences.getBoolean("roundCamera16to9", false);
            repeatMode = preferences.getInt("repeatMode", 0);
            fontSize = preferences.getInt("fons_size", AndroidUtilities.isTablet() ? 18 : 16);
            fontSizeIsDefault = !preferences.contains("fons_size");
            bubbleRadius = preferences.getInt("bubbleRadius", 17);
            ivFontSize = preferences.getInt("iv_font_size", fontSize);
            allowBigEmoji = preferences.getBoolean("allowBigEmoji", true);
            useSystemEmoji = preferences.getBoolean("useSystemEmoji", false);
            streamMedia = preferences.getBoolean("streamMedia", true);
            saveStreamMedia = preferences.getBoolean("saveStreamMedia", true);
            smoothKeyboard = preferences.getBoolean("smoothKeyboard2", true);
            pauseMusicOnRecord = preferences.getBoolean("pauseMusicOnRecord", false);
            chatBlur = preferences.getBoolean("chatBlur", true);
            forceDisableTabletMode = preferences.getBoolean("forceDisableTabletMode", false);
            streamAllVideo = preferences.getBoolean("streamAllVideo", BuildVars.DEBUG_VERSION);
            streamMkv = preferences.getBoolean("streamMkv", false);
            suggestStickers = preferences.getInt("suggestStickers", 0);
            suggestAnimatedEmoji = preferences.getBoolean("suggestAnimatedEmoji", true);
            sortContactsByName = preferences.getBoolean("sortContactsByName", false);
            sortFilesByName = preferences.getBoolean("sortFilesByName", false);
            noSoundHintShowed = preferences.getBoolean("noSoundHintShowed", false);
            directShareHash = preferences.getString("directShareHash2", null);
            useThreeLinesLayout = preferences.getBoolean("useThreeLinesLayout", false);
            archiveHidden = preferences.getBoolean("archiveHidden", false);
            distanceSystemType = preferences.getInt("distanceSystemType", 0);
            devicePerformanceClass = preferences.getInt("devicePerformanceClass", -1);
            loopStickers = preferences.getBoolean("loopStickers", true);
            keepMedia = preferences.getInt("keep_media", CacheByChatsController.KEEP_MEDIA_ONE_MONTH);
            noStatusBar = preferences.getBoolean("noStatusBar", true);
            forceRtmpStream = preferences.getBoolean("forceRtmpStream", false);
            debugWebView = preferences.getBoolean("debugWebView", false);
            lastKeepMediaCheckTime = preferences.getInt("lastKeepMediaCheckTime", 0);
            lastLogsCheckTime = preferences.getInt("lastLogsCheckTime", 0);
            searchMessagesAsListHintShows = preferences.getInt("searchMessagesAsListHintShows", 0);
            searchMessagesAsListUsed = preferences.getBoolean("searchMessagesAsListUsed", false);
            stickersReorderingHintUsed = preferences.getBoolean("stickersReorderingHintUsed", false);
            textSelectionHintShows = preferences.getInt("textSelectionHintShows", 0);
            scheduledOrNoSoundHintShows = preferences.getInt("scheduledOrNoSoundHintShows", 0);
            forwardingOptionsHintShown = preferences.getBoolean("forwardingOptionsHintShown", false);
            lockRecordAudioVideoHint = preferences.getInt("lockRecordAudioVideoHint", 0);
            disableVoiceAudioEffects = preferences.getBoolean("disableVoiceAudioEffects", false);
            noiseSupression = preferences.getBoolean("noiseSupression", false);
            chatSwipeAction = preferences.getInt("ChatSwipeAction", -1);
            messageSeenHintCount = preferences.getInt("messageSeenCount", 3);
            emojiInteractionsHintCount = preferences.getInt("emojiInteractionsHintCount", 3);
            dayNightThemeSwitchHintCount = preferences.getInt("dayNightThemeSwitchHintCount", 3);
            mediaColumnsCount = preferences.getInt("mediaColumnsCount", 3);
            fastScrollHintCount = preferences.getInt("fastScrollHintCount", 3);
            dontAskManageStorage = preferences.getBoolean("dontAskManageStorage", false);
            hasEmailLogin = preferences.getBoolean("hasEmailLogin", false);
            useLNavigation = preferences.getBoolean("useLNavigation", false);
            isFloatingDebugActive = preferences.getBoolean("floatingDebugActive", false);

            preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            showNotificationsForAllAccounts = preferences.getBoolean("AllAccounts", true);

            configLoaded = true;

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && debugWebView) {
                    WebView.setWebContentsDebuggingEnabled(true);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static void updateTabletConfig() {
        if (fontSizeIsDefault) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            fontSize = preferences.getInt("fons_size", AndroidUtilities.isTablet() ? 18 : 16);
            ivFontSize = preferences.getInt("iv_font_size", fontSize);
        }
    }

    public static void increaseBadPasscodeTries() {
        badPasscodeTries++;
        if (badPasscodeTries >= 3) {
            switch (badPasscodeTries) {
                case 3:
                    passcodeRetryInMs = 5000;
                    break;
                case 4:
                    passcodeRetryInMs = 10000;
                    break;
                case 5:
                    passcodeRetryInMs = 15000;
                    break;
                case 6:
                    passcodeRetryInMs = 20000;
                    break;
                case 7:
                    passcodeRetryInMs = 25000;
                    break;
                default:
                    passcodeRetryInMs = 30000;
                    break;
            }
            lastUptimeMillis = SystemClock.elapsedRealtime();
        }
        saveConfig();
    }

    public static boolean isPassportConfigLoaded() {
        return passportConfigMap != null;
    }

    public static void setPassportConfig(String json, int hash) {
        passportConfigMap = null;
        passportConfigJson = json;
        passportConfigHash = hash;
        saveConfig();
        getCountryLangs();
    }

    public static HashMap<String, String> getCountryLangs() {
        if (passportConfigMap == null) {
            passportConfigMap = new HashMap<>();
            try {
                JSONObject object = new JSONObject(passportConfigJson);
                Iterator<String> iter = object.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    passportConfigMap.put(key.toUpperCase(), object.getString(key).toUpperCase());
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return passportConfigMap;
    }

    public static boolean isAppUpdateAvailable() {
        if (pendingAppUpdate == null || pendingAppUpdate.document == null || !BuildVars.isStandaloneApp()) {
            return false;
        }
        int currentVersion;
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            currentVersion = pInfo.versionCode;
        } catch (Exception e) {
            FileLog.e(e);
            currentVersion = BuildVars.BUILD_VERSION;
        }
        return pendingAppUpdateBuildVersion == currentVersion;
    }

    public static boolean setNewAppVersionAvailable(TLRPC.TL_help_appUpdate update) {
        String updateVersionString = null;
        int versionCode = 0;
        try {
            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            versionCode = packageInfo.versionCode;
            updateVersionString = packageInfo.versionName;
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (versionCode == 0) {
            versionCode = BuildVars.BUILD_VERSION;
        }
        if (updateVersionString == null) {
            updateVersionString = BuildVars.BUILD_VERSION_STRING;
        }
        if (update.version == null || updateVersionString.compareTo(update.version) >= 0) {
            return false;
        }
        pendingAppUpdate = update;
        pendingAppUpdateBuildVersion = versionCode;
        saveConfig();
        return true;
    }

    public static boolean checkPasscode(String passcode) {
        if (passcodeSalt.length == 0) {
            boolean result = Utilities.MD5(passcode).equals(passcodeHash);
            if (result) {
                try {
                    passcodeSalt = new byte[16];
                    Utilities.random.nextBytes(passcodeSalt);
                    byte[] passcodeBytes = passcode.getBytes("UTF-8");
                    byte[] bytes = new byte[32 + passcodeBytes.length];
                    System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
                    System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                    System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                    passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                    saveConfig();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return result;
        } else {
            try {
                byte[] passcodeBytes = passcode.getBytes("UTF-8");
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                String hash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                return passcodeHash.equals(hash);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    public static void clearConfig() {
        saveIncomingPhotos = false;
        appLocked = false;
        passcodeType = PASSCODE_TYPE_PIN;
        passcodeRetryInMs = 0;
        lastUptimeMillis = 0;
        badPasscodeTries = 0;
        passcodeHash = "";
        passcodeSalt = new byte[0];
        autoLockIn = 60 * 60;
        lastPauseTime = 0;
        useFingerprint = true;
        isWaitingForPasscodeEnter = false;
        allowScreenCapture = false;
        lastUpdateVersion = BuildVars.BUILD_VERSION_STRING;
        textSelectionHintShows = 0;
        scheduledOrNoSoundHintShows = 0;
        lockRecordAudioVideoHint = 0;
        forwardingOptionsHintShown = false;
        messageSeenHintCount = 3;
        emojiInteractionsHintCount = 3;
        dayNightThemeSwitchHintCount = 3;
        saveConfig();
    }

    public static void setSuggestStickers(int type) {
        suggestStickers = type;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("suggestStickers", suggestStickers);
        editor.commit();
    }

    public static void setSearchMessagesAsListUsed(boolean value) {
        searchMessagesAsListUsed = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("searchMessagesAsListUsed", searchMessagesAsListUsed);
        editor.commit();
    }

    public static void setStickersReorderingHintUsed(boolean value) {
        stickersReorderingHintUsed = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("stickersReorderingHintUsed", stickersReorderingHintUsed);
        editor.commit();
    }

    public static void increaseTextSelectionHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("textSelectionHintShows", ++textSelectionHintShows);
        editor.commit();
    }

    public static void removeTextSelectionHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("textSelectionHintShows", 3);
        editor.commit();
    }

    public static void increaseScheduledOrNoSuoundHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("scheduledOrNoSoundHintShows", ++scheduledOrNoSoundHintShows);
        editor.commit();
    }

    public static void forwardingOptionsHintHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        forwardingOptionsHintShown = true;
        editor.putBoolean("forwardingOptionsHintShown", forwardingOptionsHintShown);
        editor.commit();
    }

    public static void removeScheduledOrNoSoundHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("scheduledOrNoSoundHintShows", 3);
        editor.commit();
    }

    public static void increaseLockRecordAudioVideoHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("lockRecordAudioVideoHint", ++lockRecordAudioVideoHint);
        editor.commit();
    }

    public static void removeLockRecordAudioVideoHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("lockRecordAudioVideoHint", 3);
        editor.commit();
    }

    public static void increaseSearchAsListHintShows() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("searchMessagesAsListHintShows", ++searchMessagesAsListHintShows);
        editor.commit();
    }

    public static void setKeepMedia(int value) {
        keepMedia = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("keep_media", keepMedia);
        editor.commit();
    }

    public static void checkLogsToDelete() {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        int time = (int) (System.currentTimeMillis() / 1000);
        if (Math.abs(time - lastLogsCheckTime) < 60 * 60) {
            return;
        }
        lastLogsCheckTime = time;
        Utilities.cacheClearQueue.postRunnable(() -> {
            long currentTime = time - 60 * 60 * 24 * 10;
            try {
                File sdCard = ApplicationLoader.applicationContext.getExternalFilesDir(null);
                File dir = new File(sdCard.getAbsolutePath() + "/logs");
                Utilities.clearDir(dir.getAbsolutePath(), 0, currentTime, false);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("lastLogsCheckTime", lastLogsCheckTime);
            editor.commit();
        });
    }

    public static void checkKeepMedia() {
        int time = (int) (System.currentTimeMillis() / 1000);
        if (!BuildVars.DEBUG_PRIVATE_VERSION && Math.abs(time - lastKeepMediaCheckTime) < 24 * 60 * 60) {
            return;
        }
        lastKeepMediaCheckTime = time;
        File cacheDir = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);

        Utilities.cacheClearQueue.postRunnable(() -> {
            long startTime = System.currentTimeMillis();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("checkKeepMedia start task");
            }
            boolean hasExceptions = false;
            ArrayList<CacheByChatsController> cacheByChatsControllers = new ArrayList<>();
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (UserConfig.getInstance(account).isClientActivated()) {
                    CacheByChatsController cacheByChatsController = UserConfig.getInstance(account).getMessagesController().getCacheByChatsController();
                    cacheByChatsControllers.add(cacheByChatsController);
                    if (cacheByChatsController.getKeepMediaExceptionsByDialogs().size() > 0) {
                        hasExceptions = true;
                    }
                }
            }

            int[] keepMediaByTypes = new int[3];
            boolean allKeepMediaTypesForever = true;
            long keepMediaMinSeconds = Long.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                keepMediaByTypes[i] = SharedConfig.getPreferences().getInt("keep_media_type_" + i, CacheByChatsController.getDefault(i));
                if (keepMediaByTypes[i] != CacheByChatsController.KEEP_MEDIA_FOREVER) {
                    allKeepMediaTypesForever = false;
                }
                long days = CacheByChatsController.getDaysInSeconds(keepMediaByTypes[i]);
                if (days < keepMediaMinSeconds) {
                    keepMediaMinSeconds = days;
                }
            }
            if (hasExceptions) {
                allKeepMediaTypesForever = false;
            }
            int autoDeletedFiles = 0;
            long autoDeletedFilesSize = 0;

            int deletedFilesBySize = 0;
            long deletedFilesBySizeSize = 0;
            int skippedFiles = 0;

            if (!allKeepMediaTypesForever) {
                //long currentTime = time - 60 * 60 * 24 * days;
                final SparseArray<File> paths = ImageLoader.getInstance().createMediaPaths();
                for (int a = 0; a < paths.size(); a++) {
                    boolean isCacheDir = false;
                    if (paths.keyAt(a) == FileLoader.MEDIA_DIR_CACHE) {
                        isCacheDir = true;
                    }
                    File dir = paths.valueAt(a);
                    try {
                        File[] files = dir.listFiles();
                        ArrayList<CacheByChatsController.KeepMediaFile> keepMediaFiles = new ArrayList<>();
                        for (int i = 0; i < files.length; i++) {
                            if (usingFilePaths.contains(files[i].getAbsolutePath())) {
                                continue;
                            }
                            keepMediaFiles.add(new CacheByChatsController.KeepMediaFile(files[i]));
                        }
                        for (int i = 0; i < cacheByChatsControllers.size(); i++) {
                            cacheByChatsControllers.get(i).lookupFiles(keepMediaFiles);
                        }
                        for (int i = 0; i < keepMediaFiles.size(); i++) {
                            CacheByChatsController.KeepMediaFile file = keepMediaFiles.get(i);
                            if (file.keepMedia == CacheByChatsController.KEEP_MEDIA_FOREVER) {
                                continue;
                            }
                            long seconds;
                            if (file.keepMedia >= 0) {
                                seconds = CacheByChatsController.getDaysInSeconds(file.keepMedia);
                            } else if (file.dialogType >= 0) {
                                seconds = CacheByChatsController.getDaysInSeconds(keepMediaByTypes[file.dialogType]);
                            } else if (isCacheDir) {
                                continue;
                            } else {
                                seconds = keepMediaMinSeconds;
                            }
                            if (seconds == Long.MAX_VALUE) {
                                continue;
                            }
                            long lastUsageTime = Utilities.getLastUsageFileTime(file.file.getAbsolutePath());
                            long timeLocal = time - seconds;
                            boolean needDelete = lastUsageTime < timeLocal;
                            if (needDelete) {
                                try {
                                    if (BuildVars.LOGS_ENABLED) {
                                        autoDeletedFiles++;
                                        autoDeletedFilesSize += file.file.length();
                                    }
                                    file.file.delete();
                                } catch (Exception exception) {
                                    FileLog.e(exception);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }

            int maxCacheGb = SharedConfig.getPreferences().getInt("cache_limit", Integer.MAX_VALUE);
            if (maxCacheGb != Integer.MAX_VALUE) {
                long maxCacheSize;
                if (maxCacheGb == 1) {
                    maxCacheSize = 1024L * 1024L * 300L;
                } else {
                    maxCacheSize = maxCacheGb * 1024L * 1024L * 1000L;
                }
                final SparseArray<File> paths = ImageLoader.getInstance().createMediaPaths();
                long totalSize = 0;
                for (int a = 0; a < paths.size(); a++) {
                    totalSize += Utilities.getDirSize(paths.valueAt(a).getAbsolutePath(), 0, true);
                }
                if (totalSize > maxCacheSize) {
                    ArrayList<FileInfoInternal> allFiles = new ArrayList<>();
                    for (int a = 0; a < paths.size(); a++) {
                        File dir = paths.valueAt(a);
                        fillFilesRecursive(dir, allFiles);
                    }
                    for (int i = 0; i < cacheByChatsControllers.size(); i++) {
                        cacheByChatsControllers.get(i).lookupFiles(allFiles);
                    }
                    Collections.sort(allFiles, (o1, o2) -> {
                        if (o2.lastUsageDate > o1.lastUsageDate) {
                            return -1;
                        } else if (o2.lastUsageDate < o1.lastUsageDate) {
                            return 1;
                        }
                        return 0;
                    });

                    for (int i = 0; i < allFiles.size(); i++) {
                        if (allFiles.get(i).keepMedia == CacheByChatsController.KEEP_MEDIA_FOREVER) {
                            continue;
                        }
                        if (allFiles.get(i).lastUsageDate <= 0) {
                            skippedFiles++;
                            continue;
                        }
                        long size = allFiles.get(i).file.length();
                        totalSize -= size;

                        try {
                            deletedFilesBySize++;
                            deletedFilesBySizeSize += size;
                            allFiles.get(i).file.delete();
                        } catch (Exception e) {

                        }

                        if (totalSize < maxCacheSize) {
                            break;
                        }
                    }
                }
            }

            File stickersPath = new File(cacheDir, "acache");
            if (stickersPath.exists()) {
                long currentTime = time - 60 * 60 * 24;
                try {
                    Utilities.clearDir(stickersPath.getAbsolutePath(), 0, currentTime, false);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            MessagesController.getGlobalMainSettings().edit()
                    .putInt("lastKeepMediaCheckTime", lastKeepMediaCheckTime)
                    .apply();

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("checkKeepMedia task end time " + (System.currentTimeMillis() - startTime) + "auto deleted info: files " + autoDeletedFiles + " size " + AndroidUtilities.formatFileSize(autoDeletedFilesSize) + "   deleted by size limit info: files " + deletedFilesBySize + " size " + AndroidUtilities.formatFileSize(deletedFilesBySizeSize) + " unknownTimeFiles " + skippedFiles);
            }
        });
    }

    private static void fillFilesRecursive(final File fromFolder, ArrayList<FileInfoInternal> fileInfoList) {
        if (fromFolder == null) {
            return;
        }
        File[] files = fromFolder.listFiles();
        if (files == null) {
            return;
        }
        for (final File fileEntry : files) {
            if (fileEntry.isDirectory()) {
                fillFilesRecursive(fileEntry, fileInfoList);
            } else {
                if (fileEntry.getName().equals(".nomedia")) {
                    continue;
                }
                if (usingFilePaths.contains(fileEntry.getAbsolutePath())) {
                    continue;
                }
                fileInfoList.add(new FileInfoInternal(fileEntry));
            }
        }
    }

    public static void toggleDisableVoiceAudioEffects() {
        disableVoiceAudioEffects = !disableVoiceAudioEffects;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("disableVoiceAudioEffects", disableVoiceAudioEffects);
        editor.commit();
    }

    public static void toggleNoiseSupression() {
        noiseSupression = !noiseSupression;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("noiseSupression", noiseSupression);
        editor.commit();
    }

    public static void toggleForceRTMPStream() {
        forceRtmpStream = !forceRtmpStream;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("forceRtmpStream", forceRtmpStream);
        editor.apply();
    }

    public static void toggleDebugWebView() {
        debugWebView = !debugWebView;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(debugWebView);
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("debugWebView", debugWebView);
        editor.apply();
    }

    public static void toggleNoStatusBar() {
        noStatusBar = !noStatusBar;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("noStatusBar", noStatusBar);
        editor.commit();
    }

    public static void toggleLoopStickers() {
        loopStickers = !loopStickers;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("loopStickers", loopStickers);
        editor.commit();
    }

    public static void toggleBigEmoji() {
        allowBigEmoji = !allowBigEmoji;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("allowBigEmoji", allowBigEmoji);
        editor.commit();
    }

    public static void toggleSuggestAnimatedEmoji() {
        suggestAnimatedEmoji = !suggestAnimatedEmoji;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("suggestAnimatedEmoji", suggestAnimatedEmoji);
        editor.commit();
    }

    public static void setPlaybackOrderType(int type) {
        if (type == 2) {
            shuffleMusic = true;
            playOrderReversed = false;
        } else if (type == 1) {
            playOrderReversed = true;
            shuffleMusic = false;
        } else {
            playOrderReversed = false;
            shuffleMusic = false;
        }
        MediaController.getInstance().checkIsNextMediaFileDownloaded();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("shuffleMusic", shuffleMusic);
        editor.putBoolean("playOrderReversed", playOrderReversed);
        editor.commit();
    }

    public static void setRepeatMode(int mode) {
        repeatMode = mode;
        if (repeatMode < 0 || repeatMode > 2) {
            repeatMode = 0;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("repeatMode", repeatMode);
        editor.commit();
    }

    public static void toggleSaveToGalleryFlag(int flag) {
//        if ((saveToGalleryFlags & flag) != 0) {
//            saveToGalleryFlags &= ~flag;
//        } else {
//            saveToGalleryFlags |= flag;
//        }
//        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
//        preferences.edit().putInt("save_gallery_flags", saveToGalleryFlags).apply();
//        ImageLoader.getInstance().checkMediaPaths();
//        ImageLoader.getInstance().getCacheOutQueue().postRunnable(() -> {
//            checkSaveToGalleryFiles();
//        });
    }

    public static void toggleAutoplayGifs() {
        autoplayGifs = !autoplayGifs;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("autoplay_gif", autoplayGifs);
        editor.commit();
    }

    public static void setUseThreeLinesLayout(boolean value) {
        useThreeLinesLayout = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("useThreeLinesLayout", useThreeLinesLayout);
        editor.commit();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload, true);
    }

    public static void toggleArchiveHidden() {
        archiveHidden = !archiveHidden;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("archiveHidden", archiveHidden);
        editor.commit();
    }

    public static void toggleAutoplayVideo() {
        autoplayVideo = !autoplayVideo;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("autoplay_video", autoplayVideo);
        editor.commit();
    }

    public static boolean isSecretMapPreviewSet() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        return preferences.contains("mapPreviewType");
    }

    public static void setSecretMapPreviewType(int value) {
        mapPreviewType = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("mapPreviewType", mapPreviewType);
        editor.commit();
    }

    public static void setNoSoundHintShowed(boolean value) {
        if (noSoundHintShowed == value) {
            return;
        }
        noSoundHintShowed = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("noSoundHintShowed", noSoundHintShowed);
        editor.commit();
    }

    public static void toogleRaiseToSpeak() {
        raiseToSpeak = !raiseToSpeak;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("raise_to_speak", raiseToSpeak);
        editor.commit();
    }

    public static void toggleCustomTabs() {
        customTabs = !customTabs;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("custom_tabs", customTabs);
        editor.commit();
    }

    public static void toggleDirectShare() {
        directShare = !directShare;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("direct_share", directShare);
        editor.commit();
        ShortcutManagerCompat.removeAllDynamicShortcuts(ApplicationLoader.applicationContext);
        MediaDataController.getInstance(UserConfig.selectedAccount).buildShortcuts();
    }

    public static void toggleStreamMedia() {
        streamMedia = !streamMedia;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("streamMedia", streamMedia);
        editor.commit();
    }

    public static void toggleSortContactsByName() {
        sortContactsByName = !sortContactsByName;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("sortContactsByName", sortContactsByName);
        editor.commit();
    }

    public static void toggleSortFilesByName() {
        sortFilesByName = !sortFilesByName;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("sortFilesByName", sortFilesByName);
        editor.commit();
    }

    public static void toggleStreamAllVideo() {
        streamAllVideo = !streamAllVideo;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("streamAllVideo", streamAllVideo);
        editor.commit();
    }

    public static void toggleStreamMkv() {
        streamMkv = !streamMkv;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("streamMkv", streamMkv);
        editor.commit();
    }

    public static void toggleSaveStreamMedia() {
        saveStreamMedia = !saveStreamMedia;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("saveStreamMedia", saveStreamMedia);
        editor.commit();
    }

    public static void toggleSmoothKeyboard() {
        smoothKeyboard = !smoothKeyboard;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("smoothKeyboard2", smoothKeyboard);
        editor.commit();
    }

    public static void togglePauseMusicOnRecord() {
        pauseMusicOnRecord = !pauseMusicOnRecord;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("pauseMusicOnRecord", pauseMusicOnRecord);
        editor.commit();
    }

    public static void toggleChatBlur() {
        chatBlur = !chatBlur;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("chatBlur", chatBlur);
        editor.commit();
    }

    public static void toggleForceDisableTabletMode() {
        forceDisableTabletMode = !forceDisableTabletMode;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("forceDisableTabletMode", forceDisableTabletMode);
        editor.commit();
    }

    public static void toggleInappCamera() {
        inappCamera = !inappCamera;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("inappCamera", inappCamera);
        editor.commit();
    }

    public static void toggleRoundCamera16to9() {
        roundCamera16to9 = !roundCamera16to9;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("roundCamera16to9", roundCamera16to9);
        editor.commit();
    }

    public static void setDistanceSystemType(int type) {
        distanceSystemType = type;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("distanceSystemType", distanceSystemType);
        editor.commit();
        LocaleController.resetImperialSystemType();
    }

    public static void loadProxyList() {
        if (proxyListLoaded) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String proxyAddress = preferences.getString("proxy_ip", "");
        String proxyUsername = preferences.getString("proxy_user", "");
        String proxyPassword = preferences.getString("proxy_pass", "");
        String proxySecret = preferences.getString("proxy_secret", "");
        int proxyPort = preferences.getInt("proxy_port", 1080);

        proxyListLoaded = true;
        proxyList.clear();
        currentProxy = null;
        String list = preferences.getString("proxy_list", null);
        if (!TextUtils.isEmpty(list)) {
            byte[] bytes = Base64.decode(list, Base64.DEFAULT);
            SerializedData data = new SerializedData(bytes);
            int count = data.readInt32(false);
            if (count == -1) { // V2 or newer
                int version = data.readByte(false);

                if (version == PROXY_SCHEMA_V2) {
                    count = data.readInt32(false);

                    for (int i = 0; i < count; i++) {
                        ProxyInfo info = new ProxyInfo(
                                data.readString(false),
                                data.readInt32(false),
                                data.readString(false),
                                data.readString(false),
                                data.readString(false));

                        info.ping = data.readInt64(false);
                        info.availableCheckTime = data.readInt64(false);

                        proxyList.add(info);
                        if (currentProxy == null && !TextUtils.isEmpty(proxyAddress)) {
                            if (proxyAddress.equals(info.address) && proxyPort == info.port && proxyUsername.equals(info.username) && proxyPassword.equals(info.password)) {
                                currentProxy = info;
                            }
                        }
                    }
                } else {
                    FileLog.e("Unknown proxy schema version: " + version);
                }
            } else {
                for (int a = 0; a < count; a++) {
                    ProxyInfo info = new ProxyInfo(
                            data.readString(false),
                            data.readInt32(false),
                            data.readString(false),
                            data.readString(false),
                            data.readString(false));
                    proxyList.add(info);
                    if (currentProxy == null && !TextUtils.isEmpty(proxyAddress)) {
                        if (proxyAddress.equals(info.address) && proxyPort == info.port && proxyUsername.equals(info.username) && proxyPassword.equals(info.password)) {
                            currentProxy = info;
                        }
                    }
                }
            }
            data.cleanup();
        }
        if (currentProxy == null && !TextUtils.isEmpty(proxyAddress)) {
            ProxyInfo info = currentProxy = new ProxyInfo(proxyAddress, proxyPort, proxyUsername, proxyPassword, proxySecret);
            proxyList.add(0, info);
        }
    }

    public static void saveProxyList() {
        List<ProxyInfo> infoToSerialize = new ArrayList<>(proxyList);
        Collections.sort(infoToSerialize, (o1, o2) -> {
            long bias1 = SharedConfig.currentProxy == o1 ? -200000 : 0;
            if (!o1.available) {
                bias1 += 100000;
            }
            long bias2 = SharedConfig.currentProxy == o2 ? -200000 : 0;
            if (!o2.available) {
                bias2 += 100000;
            }
            return Long.compare(o1.ping + bias1, o2.ping + bias2);
        });
        SerializedData serializedData = new SerializedData();
        serializedData.writeInt32(-1);
        serializedData.writeByte(PROXY_CURRENT_SCHEMA_VERSION);
        int count = infoToSerialize.size();
        serializedData.writeInt32(count);
        for (int a = 0; a < count; a++) {
            ProxyInfo info = infoToSerialize.get(a);
            serializedData.writeString(info.address != null ? info.address : "");
            serializedData.writeInt32(info.port);
            serializedData.writeString(info.username != null ? info.username : "");
            serializedData.writeString(info.password != null ? info.password : "");
            serializedData.writeString(info.secret != null ? info.secret : "");

            serializedData.writeInt64(info.ping);
            serializedData.writeInt64(info.availableCheckTime);
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putString("proxy_list", Base64.encodeToString(serializedData.toByteArray(), Base64.NO_WRAP)).commit();
        serializedData.cleanup();
    }

    public static ProxyInfo addProxy(ProxyInfo proxyInfo) {
        loadProxyList();
        int count = proxyList.size();
        for (int a = 0; a < count; a++) {
            ProxyInfo info = proxyList.get(a);
            if (proxyInfo.address.equals(info.address) && proxyInfo.port == info.port && proxyInfo.username.equals(info.username) && proxyInfo.password.equals(info.password) && proxyInfo.secret.equals(info.secret)) {
                return info;
            }
        }
        proxyList.add(proxyInfo);
        saveProxyList();
        return proxyInfo;
    }

    public static boolean isProxyEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) && currentProxy != null;
    }

    public static void deleteProxy(ProxyInfo proxyInfo) {
        if (currentProxy == proxyInfo) {
            currentProxy = null;
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("proxy_enabled", false);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("proxy_ip", "");
            editor.putString("proxy_pass", "");
            editor.putString("proxy_user", "");
            editor.putString("proxy_secret", "");
            editor.putInt("proxy_port", 1080);
            editor.putBoolean("proxy_enabled", false);
            editor.putBoolean("proxy_enabled_calls", false);
            editor.commit();
            if (enabled) {
                ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
            }
        }
        proxyList.remove(proxyInfo);
        saveProxyList();
    }

    public static void checkSaveToGalleryFiles() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
                File imagePath = new File(telegramPath, "Telegram Images");
                imagePath.mkdir();
                File videoPath = new File(telegramPath, "Telegram Video");
                videoPath.mkdir();

                if (!BuildVars.NO_SCOPED_STORAGE) {
                    if (imagePath.isDirectory()) {
                        new File(imagePath, ".nomedia").delete();
                    }
                    if (videoPath.isDirectory()) {
                        new File(videoPath, ".nomedia").delete();
                    }
                } else {
                    if (imagePath.isDirectory()) {
                        AndroidUtilities.createEmptyFile(new File(imagePath, ".nomedia"));
                    }
                    if (videoPath.isDirectory()) {
                        AndroidUtilities.createEmptyFile(new File(videoPath, ".nomedia"));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public static int getChatSwipeAction(int currentAccount) {
        if (chatSwipeAction >= 0) {
            if (chatSwipeAction == SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS && MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
                return SwipeGestureSettingsView.SWIPE_GESTURE_ARCHIVE;
            }
            return chatSwipeAction;
        } else if (!MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
            return SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS;

        }
        return SwipeGestureSettingsView.SWIPE_GESTURE_ARCHIVE;
    }

    public static void updateChatListSwipeSetting(int newAction) {
        chatSwipeAction = newAction;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("ChatSwipeAction", chatSwipeAction).apply();
    }

    public static void updateMessageSeenHintCount(int count) {
        messageSeenHintCount = count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("messageSeenCount", messageSeenHintCount).apply();
    }

    public static void updateEmojiInteractionsHintCount(int count) {
        emojiInteractionsHintCount = count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("emojiInteractionsHintCount", emojiInteractionsHintCount).apply();
    }


    public static void updateDayNightThemeSwitchHintCount(int count) {
        dayNightThemeSwitchHintCount = count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("dayNightThemeSwitchHintCount", dayNightThemeSwitchHintCount).apply();
    }

    public final static int PERFORMANCE_CLASS_LOW = 0;
    public final static int PERFORMANCE_CLASS_AVERAGE = 1;
    public final static int PERFORMANCE_CLASS_HIGH = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            PERFORMANCE_CLASS_LOW,
            PERFORMANCE_CLASS_AVERAGE,
            PERFORMANCE_CLASS_HIGH
    })
    public @interface PerformanceClass {}

    @PerformanceClass
    public static int getDevicePerformanceClass() {
        if (devicePerformanceClass == -1) {
            int androidVersion = Build.VERSION.SDK_INT;
            int cpuCount = ConnectionsManager.CPU_COUNT;
            int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
            int totalCpuFreq = 0;
            int freqResolved = 0;
            for (int i = 0; i < cpuCount; i++) {
                try {
                    RandomAccessFile reader = new RandomAccessFile(String.format(Locale.ENGLISH, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r");
                    String line = reader.readLine();
                    if (line != null) {
                        totalCpuFreq += Utilities.parseInt(line) / 1000;
                        freqResolved++;
                    }
                    reader.close();
                } catch (Throwable ignore) {}
            }
            int maxCpuFreq = freqResolved == 0 ? -1 : (int) Math.ceil(totalCpuFreq / (float) freqResolved);

            if (androidVersion < 21 || cpuCount <= 2 || memoryClass <= 100 || cpuCount <= 4 && maxCpuFreq != -1 && maxCpuFreq <= 1250 || cpuCount <= 4 && maxCpuFreq <= 1600 && memoryClass <= 128 && androidVersion <= 21 || cpuCount <= 4 && maxCpuFreq <= 1300 && memoryClass <= 128 && androidVersion <= 24) {
                devicePerformanceClass = PERFORMANCE_CLASS_LOW;
            } else if (cpuCount < 8 || memoryClass <= 160 || maxCpuFreq != -1 && maxCpuFreq <= 2050 || maxCpuFreq == -1 && cpuCount == 8 && androidVersion <= 23) {
                devicePerformanceClass = PERFORMANCE_CLASS_AVERAGE;
            } else {
                devicePerformanceClass = PERFORMANCE_CLASS_HIGH;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("device performance info selected_class = " + devicePerformanceClass + " (cpu_count = " + cpuCount + ", freq = " + maxCpuFreq + ", memoryClass = " + memoryClass + ", android version " + androidVersion + ", manufacture " + Build.MANUFACTURER + ")");
            }
        }

        return devicePerformanceClass;
    }

    public static void setMediaColumnsCount(int count) {
        if (mediaColumnsCount != count) {
            mediaColumnsCount = count;
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("mediaColumnsCount", mediaColumnsCount).apply();
        }
    }

    public static void setFastScrollHintCount(int count) {
        if (fastScrollHintCount != count) {
            fastScrollHintCount = count;
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("fastScrollHintCount", fastScrollHintCount).apply();
        }
    }

    public static void setDontAskManageStorage(boolean b) {
        dontAskManageStorage = b;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putBoolean("dontAskManageStorage", dontAskManageStorage).apply();
    }

    public static boolean canBlurChat() {
        return getDevicePerformanceClass() == PERFORMANCE_CLASS_HIGH;
    }

    public static boolean chatBlurEnabled() {
        return canBlurChat() && chatBlur;
    }

    public static class BackgroundActivityPrefs {
        private static SharedPreferences prefs;

        public static long getLastCheckedBackgroundActivity() {
            return prefs.getLong("last_checked", 0);
        }

        public static void setLastCheckedBackgroundActivity(long l) {
            prefs.edit().putLong("last_checked", l).apply();
        }

        public static int getDismissedCount() {
            return prefs.getInt("dismissed_count", 0);
        }

        public static void increaseDismissedCount() {
            prefs.edit().putInt("dismissed_count", getDismissedCount() + 1).apply();
        }
    }

    private static Boolean animationsEnabled;

    public static void setAnimationsEnabled(boolean b) {
        animationsEnabled = b;
    }

    public static boolean animationsEnabled() {
        if (animationsEnabled == null) {
            animationsEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        }
        return animationsEnabled;
    }

    public static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
    }

    private static class FileInfoInternal extends CacheByChatsController.KeepMediaFile {
        final long lastUsageDate;

        private FileInfoInternal(File file) {
            super(file);
            this.lastUsageDate = Utilities.getLastUsageFileTime(file.getAbsolutePath());
        }
    }

    public static class LiteMode {

        private boolean enabled;

        LiteMode() {
            loadPreference();
        }

        public boolean enabled() {
            return enabled;
        }

        public void toggleMode() {
            enabled = !enabled;
            savePreference();
            AnimatedEmojiDrawable.lightModeChanged();
        }

        private void loadPreference() {
            int flags = MessagesController.getGlobalMainSettings().getInt("light_mode", getDevicePerformanceClass() == PERFORMANCE_CLASS_LOW ? 1 : 0) ;
            enabled = (flags & 1) != 0;
        }

        public void savePreference() {
            int flags = 0;
            if (enabled) {
                flags |= 1;
            }
            MessagesController.getGlobalMainSettings().edit().putInt("light_mode", flags).apply();
        }

        public boolean animatedEmojiEnabled() {
            return !enabled;
        }
    }

    public static void lockFile(File file) {
        if (file == null) {
            return;
        }
        lockFile(file.getAbsolutePath());
    }

    public static void unlockFile(File file) {
        if (file == null) {
            return;
        }
        unlockFile(file.getAbsolutePath());
    }

    public static void lockFile(String file) {
        if (file == null) {
            return;
        }
        usingFilePaths.add(file);
    }

    public static void unlockFile(String file) {
        if (file == null) {
            return;
        }
        usingFilePaths.remove(file);
    }
}
