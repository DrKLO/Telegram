/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.util.Pair;
import android.util.SparseArray;

import org.telegram.tgnet.TLRPC;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

import androidx.collection.LongSparseArray;

public class DownloadController extends BaseController implements NotificationCenter.NotificationCenterDelegate {

    public interface FileDownloadProgressListener {
        void onFailedDownload(String fileName, boolean canceled);
        void onSuccessDownload(String fileName);
        void onProgressDownload(String fileName, long downloadSize, long totalSize);
        void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted);
        int getObserverTag();
    }

    public static final int AUTODOWNLOAD_TYPE_PHOTO = 1;
    public static final int AUTODOWNLOAD_TYPE_AUDIO = 2;
    public static final int AUTODOWNLOAD_TYPE_VIDEO = 4;
    public static final int AUTODOWNLOAD_TYPE_DOCUMENT = 8;

    public static final int PRESET_NUM_CONTACT = 0;
    public static final int PRESET_NUM_PM = 1;
    public static final int PRESET_NUM_GROUP = 2;
    public static final int PRESET_NUM_CHANNEL = 3;

    public static final int PRESET_SIZE_NUM_PHOTO = 0;
    public static final int PRESET_SIZE_NUM_VIDEO = 1;
    public static final int PRESET_SIZE_NUM_DOCUMENT = 2;
    public static final int PRESET_SIZE_NUM_AUDIO = 3;

    private int lastCheckMask = 0;
    private ArrayList<DownloadObject> photoDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> audioDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> documentDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> videoDownloadQueue = new ArrayList<>();
    private HashMap<String, DownloadObject> downloadQueueKeys = new HashMap<>();
    private HashMap<Pair<Long, Integer>, DownloadObject> downloadQueuePairs = new HashMap<>();

    private HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>> loadingFileObservers = new HashMap<>();
    private HashMap<String, ArrayList<MessageObject>> loadingFileMessagesObservers = new HashMap<>();
    private SparseArray<String> observersByTag = new SparseArray<>();
    private boolean listenerInProgress = false;
    private HashMap<String, FileDownloadProgressListener> addLaterArray = new HashMap<>();
    private ArrayList<FileDownloadProgressListener> deleteLaterArray = new ArrayList<>();
    private int lastTag = 0;

    private boolean loadingAutoDownloadConfig;

    private LongSparseArray<Long> typingTimes = new LongSparseArray<>();

    public static class Preset {
        public int[] mask = new int[4];
        public int[] sizes = new int[4];
        public boolean preloadVideo;
        public boolean preloadMusic;
        public boolean lessCallData;
        public boolean enabled;
        public int maxVideoBitrate;

        public Preset(int[] m, int p, int v, int f, boolean pv, boolean pm, boolean e, boolean l, int bitrate) {
            System.arraycopy(m, 0, mask, 0, mask.length);
            sizes[PRESET_SIZE_NUM_PHOTO] = p;
            sizes[PRESET_SIZE_NUM_VIDEO] = v;
            sizes[PRESET_SIZE_NUM_DOCUMENT] = f;
            sizes[PRESET_SIZE_NUM_AUDIO] = 512 * 1024;
            preloadVideo = pv;
            preloadMusic = pm;
            lessCallData = l;
            maxVideoBitrate = bitrate;
            enabled = e;
        }

        public Preset(String str, String deafultValue) {
            String[] args = str.split("_");
            String[] defaultArgs = null;
            if (args.length >= 11) {
                mask[0] = Utilities.parseInt(args[0]);
                mask[1] = Utilities.parseInt(args[1]);
                mask[2] = Utilities.parseInt(args[2]);
                mask[3] = Utilities.parseInt(args[3]);
                sizes[PRESET_SIZE_NUM_PHOTO] = Utilities.parseInt(args[4]);
                sizes[PRESET_SIZE_NUM_VIDEO] = Utilities.parseInt(args[5]);
                sizes[PRESET_SIZE_NUM_DOCUMENT] = Utilities.parseInt(args[6]);
                sizes[PRESET_SIZE_NUM_AUDIO] = Utilities.parseInt(args[7]);
                preloadVideo = Utilities.parseInt(args[8]) == 1;
                preloadMusic = Utilities.parseInt(args[9]) == 1;
                enabled = Utilities.parseInt(args[10]) == 1;
                if (args.length >= 12) {
                    lessCallData = Utilities.parseInt(args[11]) == 1;
                } else {
                    defaultArgs = deafultValue.split("_");
                    lessCallData = Utilities.parseInt(defaultArgs[11]) == 1;
                }

                if (args.length >= 13) {
                    maxVideoBitrate = Utilities.parseInt(args[12]);
                } else {
                    if (defaultArgs == null) {
                        defaultArgs = deafultValue.split("_");
                    }
                    maxVideoBitrate = Utilities.parseInt(defaultArgs[12]);
                }
            }
        }

        public void set(Preset preset) {
            System.arraycopy(preset.mask, 0, mask, 0, mask.length);
            System.arraycopy(preset.sizes, 0, sizes, 0, sizes.length);
            preloadVideo = preset.preloadVideo;
            preloadMusic = preset.preloadMusic;
            lessCallData = preset.lessCallData;
            maxVideoBitrate = preset.maxVideoBitrate;
        }

        public void set(TLRPC.TL_autoDownloadSettings settings) {
            preloadMusic = settings.audio_preload_next;
            preloadVideo = settings.video_preload_large;
            lessCallData = settings.phonecalls_less_data;
            maxVideoBitrate = settings.video_upload_maxbitrate;
            sizes[PRESET_SIZE_NUM_PHOTO] = Math.max(500 * 1024, settings.photo_size_max);
            sizes[PRESET_SIZE_NUM_VIDEO] = Math.max(500 * 1024, settings.video_size_max);
            sizes[PRESET_SIZE_NUM_DOCUMENT] = Math.max(500 * 1024, settings.file_size_max);
            for (int a = 0; a < mask.length; a++) {
                if (settings.photo_size_max != 0 && !settings.disabled) {
                    mask[a] |= AUTODOWNLOAD_TYPE_PHOTO;
                } else {
                    mask[a] &=~ AUTODOWNLOAD_TYPE_PHOTO;
                }
                if (settings.video_size_max != 0 && !settings.disabled) {
                    mask[a] |= AUTODOWNLOAD_TYPE_VIDEO;
                } else {
                    mask[a] &=~ AUTODOWNLOAD_TYPE_VIDEO;
                }
                if (settings.file_size_max != 0 && !settings.disabled) {
                    mask[a] |= AUTODOWNLOAD_TYPE_DOCUMENT;
                } else {
                    mask[a] &=~ AUTODOWNLOAD_TYPE_DOCUMENT;
                }
            }
        }

        @Override
        public String toString() {
            return mask[0] + "_" + mask[1] + "_" + mask[2] + "_" + mask[3] +
                    "_" + sizes[PRESET_SIZE_NUM_PHOTO] +
                    "_" + sizes[PRESET_SIZE_NUM_VIDEO] +
                    "_" + sizes[PRESET_SIZE_NUM_DOCUMENT] +
                    "_" + sizes[PRESET_SIZE_NUM_AUDIO] +
                    "_" + (preloadVideo ? 1 : 0) +
                    "_" + (preloadMusic ? 1 : 0) +
                    "_" + (enabled ? 1 : 0) +
                    "_" + (lessCallData ? 1 : 0) +
                    "_" + maxVideoBitrate;
        }

        public boolean equals(Preset obj) {
            return mask[0] == obj.mask[0] &&
                    mask[1] == obj.mask[1] &&
                    mask[2] == obj.mask[2] &&
                    mask[3] == obj.mask[3] &&
                    sizes[0] == obj.sizes[0] &&
                    sizes[1] == obj.sizes[1] &&
                    sizes[2] == obj.sizes[2] &&
                    sizes[3] == obj.sizes[3] &&
                    preloadVideo == obj.preloadVideo &&
                    preloadMusic == obj.preloadMusic &&
                    maxVideoBitrate == obj.maxVideoBitrate;
        }

        public boolean isEnabled() {
            for (int a = 0; a < mask.length; a++) {
                if (mask[a] != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    public Preset lowPreset;
    public Preset mediumPreset;
    public Preset highPreset;
    public Preset mobilePreset;
    public Preset wifiPreset;
    public Preset roamingPreset;
    public int currentMobilePreset;
    public int currentWifiPreset;
    public int currentRoamingPreset;
    
    private static volatile DownloadController[] Instance = new DownloadController[UserConfig.MAX_ACCOUNT_COUNT];

    public static DownloadController getInstance(int num) {
        DownloadController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (DownloadController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new DownloadController(num);
                }
            }
        }
        return localInstance;
    }

    public DownloadController(int instance) {
        super(instance);
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        String defaultLow = "1_1_1_1_1048576_512000_512000_524288_0_0_1_1_50";
        String defaultMedium = "13_13_13_13_1048576_10485760_1048576_524288_1_1_1_0_100";
        String defaultHigh = "13_13_13_13_1048576_15728640_3145728_524288_1_1_1_0_100";
        lowPreset = new Preset(preferences.getString("preset0", defaultLow), defaultLow);
        mediumPreset = new Preset(preferences.getString("preset1", defaultMedium), defaultMedium);
        highPreset = new Preset(preferences.getString("preset2", defaultHigh), defaultHigh);
        boolean newConfig;
        if ((newConfig = preferences.contains("newConfig")) || !getUserConfig().isClientActivated()) {
            mobilePreset = new Preset(preferences.getString("mobilePreset", defaultMedium), defaultMedium);
            wifiPreset = new Preset(preferences.getString("wifiPreset", defaultHigh), defaultHigh);
            roamingPreset = new Preset(preferences.getString("roamingPreset", defaultLow), defaultLow);
            currentMobilePreset = preferences.getInt("currentMobilePreset", 3);
            currentWifiPreset = preferences.getInt("currentWifiPreset", 3);
            currentRoamingPreset = preferences.getInt("currentRoamingPreset", 3);
            if (!newConfig) {
                preferences.edit().putBoolean("newConfig", true).commit();
            }
        } else {
            int[] mobileDataDownloadMask = new int[4];
            int[] wifiDownloadMask = new int[4];
            int[] roamingDownloadMask = new int[4];
            int[] mobileMaxFileSize = new int[7];
            int[] wifiMaxFileSize = new int[7];
            int[] roamingMaxFileSize = new int[7];

            for (int a = 0; a < 4; a++) {
                String key = "mobileDataDownloadMask" + (a == 0 ? "" : a);
                if (a == 0 || preferences.contains(key)) {
                    mobileDataDownloadMask[a] = preferences.getInt(key, AUTODOWNLOAD_TYPE_PHOTO | AUTODOWNLOAD_TYPE_VIDEO | AUTODOWNLOAD_TYPE_DOCUMENT);
                    wifiDownloadMask[a] = preferences.getInt("wifiDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_TYPE_PHOTO | AUTODOWNLOAD_TYPE_VIDEO | AUTODOWNLOAD_TYPE_DOCUMENT);
                    roamingDownloadMask[a] = preferences.getInt("roamingDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_TYPE_PHOTO);
                } else {
                    mobileDataDownloadMask[a] = mobileDataDownloadMask[0];
                    wifiDownloadMask[a] = wifiDownloadMask[0];
                    roamingDownloadMask[a] = roamingDownloadMask[0];
                }
            }

            mobileMaxFileSize[2] = preferences.getInt("mobileMaxDownloadSize" + 2, mediumPreset.sizes[PRESET_SIZE_NUM_VIDEO]);
            mobileMaxFileSize[3] = preferences.getInt("mobileMaxDownloadSize" + 3, mediumPreset.sizes[PRESET_SIZE_NUM_DOCUMENT]);
            wifiMaxFileSize[2] = preferences.getInt("wifiMaxDownloadSize" + 2, highPreset.sizes[PRESET_SIZE_NUM_VIDEO]);
            wifiMaxFileSize[3] = preferences.getInt("wifiMaxDownloadSize" + 3, highPreset.sizes[PRESET_SIZE_NUM_DOCUMENT]);
            roamingMaxFileSize[2] = preferences.getInt("roamingMaxDownloadSize" + 2, lowPreset.sizes[PRESET_SIZE_NUM_VIDEO]);
            roamingMaxFileSize[3] = preferences.getInt("roamingMaxDownloadSize" + 3, lowPreset.sizes[PRESET_SIZE_NUM_DOCUMENT]);

            boolean globalAutodownloadEnabled = preferences.getBoolean("globalAutodownloadEnabled", true);
            mobilePreset = new Preset(mobileDataDownloadMask, mediumPreset.sizes[PRESET_SIZE_NUM_PHOTO], mobileMaxFileSize[2], mobileMaxFileSize[3], true, true, globalAutodownloadEnabled, false, 100);
            wifiPreset = new Preset(wifiDownloadMask, highPreset.sizes[PRESET_SIZE_NUM_PHOTO], wifiMaxFileSize[2], wifiMaxFileSize[3], true, true, globalAutodownloadEnabled, false, 100);
            roamingPreset = new Preset(roamingDownloadMask, lowPreset.sizes[PRESET_SIZE_NUM_PHOTO], roamingMaxFileSize[2], roamingMaxFileSize[3], false, false, globalAutodownloadEnabled, true, 50);

            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("newConfig", true);
            editor.putString("mobilePreset", mobilePreset.toString());
            editor.putString("wifiPreset", wifiPreset.toString());
            editor.putString("roamingPreset", roamingPreset.toString());
            editor.putInt("currentMobilePreset", currentMobilePreset = 3);
            editor.putInt("currentWifiPreset", currentWifiPreset = 3);
            editor.putInt("currentRoamingPreset", currentRoamingPreset = 3);
            editor.commit();
        }

        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().addObserver(DownloadController.this, NotificationCenter.fileLoadFailed);
            getNotificationCenter().addObserver(DownloadController.this, NotificationCenter.fileLoaded);
            getNotificationCenter().addObserver(DownloadController.this, NotificationCenter.fileLoadProgressChanged);
            getNotificationCenter().addObserver(DownloadController.this, NotificationCenter.fileUploadProgressChanged);
            getNotificationCenter().addObserver(DownloadController.this, NotificationCenter.httpFileDidLoad);
            getNotificationCenter().addObserver(DownloadController.this, NotificationCenter.httpFileDidFailedLoad);
            loadAutoDownloadConfig(false);
        });

        BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkAutodownloadSettings();
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        ApplicationLoader.applicationContext.registerReceiver(networkStateReceiver, filter);

        if (getUserConfig().isClientActivated()) {
            checkAutodownloadSettings();
        }
    }

    public void loadAutoDownloadConfig(boolean force) {
        if (loadingAutoDownloadConfig || !force && Math.abs(System.currentTimeMillis() - getUserConfig().autoDownloadConfigLoadTime) < 24 * 60 * 60 * 1000) {
            return;
        }
        loadingAutoDownloadConfig = true;
        TLRPC.TL_account_getAutoDownloadSettings req = new TLRPC.TL_account_getAutoDownloadSettings();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingAutoDownloadConfig = false;
            getUserConfig().autoDownloadConfigLoadTime = System.currentTimeMillis();
            getUserConfig().saveConfig(false);
            if (response != null) {
                TLRPC.TL_account_autoDownloadSettings res = (TLRPC.TL_account_autoDownloadSettings) response;
                lowPreset.set(res.low);
                mediumPreset.set(res.medium);
                highPreset.set(res.high);
                for (int a = 0; a < 3; a++) {
                    Preset preset;
                    if (a == 0) {
                        preset = mobilePreset;
                    } else if (a == 1) {
                        preset = wifiPreset;
                    } else {
                        preset = roamingPreset;
                    }
                    if (preset.equals(lowPreset)) {
                        preset.set(res.low);
                    } else if (preset.equals(mediumPreset)) {
                        preset.set(res.medium);
                    } else if (preset.equals(highPreset)) {
                        preset.set(res.high);
                    }
                }

                SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                editor.putString("mobilePreset", mobilePreset.toString());
                editor.putString("wifiPreset", wifiPreset.toString());
                editor.putString("roamingPreset", roamingPreset.toString());
                editor.putString("preset0", lowPreset.toString());
                editor.putString("preset1", mediumPreset.toString());
                editor.putString("preset2", highPreset.toString());
                editor.commit();
                String str1 = lowPreset.toString();
                String str2 = mediumPreset.toString();
                String str3 = highPreset.toString();
                checkAutodownloadSettings();
            }
        }));
    }

    public Preset getCurrentMobilePreset() {
        if (currentMobilePreset == 0) {
            return lowPreset;
        } else if (currentMobilePreset == 1) {
            return mediumPreset;
        } else if (currentMobilePreset == 2) {
            return highPreset;
        } else {
            return mobilePreset;
        }
    }

    public Preset getCurrentWiFiPreset() {
        if (currentWifiPreset == 0) {
            return lowPreset;
        } else if (currentWifiPreset == 1) {
            return mediumPreset;
        } else if (currentWifiPreset == 2) {
            return highPreset;
        } else {
            return wifiPreset;
        }
    }

    public Preset getCurrentRoamingPreset() {
        if (currentRoamingPreset == 0) {
            return lowPreset;
        } else if (currentRoamingPreset == 1) {
            return mediumPreset;
        } else if (currentRoamingPreset == 2) {
            return highPreset;
        } else {
            return roamingPreset;
        }
    }

    public static int typeToIndex(int type) {
        if (type == AUTODOWNLOAD_TYPE_PHOTO) {
            return PRESET_SIZE_NUM_PHOTO;
        } else if (type == AUTODOWNLOAD_TYPE_AUDIO) {
            return PRESET_SIZE_NUM_DOCUMENT;
        } else if (type == AUTODOWNLOAD_TYPE_VIDEO) {
            return PRESET_SIZE_NUM_VIDEO;
        } else if (type == AUTODOWNLOAD_TYPE_DOCUMENT) {
            return PRESET_SIZE_NUM_DOCUMENT;
        }
        return PRESET_SIZE_NUM_PHOTO;
    }

    public void cleanup() {
        photoDownloadQueue.clear();
        audioDownloadQueue.clear();
        documentDownloadQueue.clear();
        videoDownloadQueue.clear();
        downloadQueueKeys.clear();
        downloadQueuePairs.clear();
        typingTimes.clear();
    }

    public int getMaxVideoBitrate() {
        int networkType = ApplicationLoader.getAutodownloadNetworkType();
        if (networkType == StatsController.TYPE_WIFI) {
            return getCurrentWiFiPreset().maxVideoBitrate;
        } else if (networkType == StatsController.TYPE_ROAMING) {
            return getCurrentRoamingPreset().maxVideoBitrate;
        } else {
            return getCurrentMobilePreset().maxVideoBitrate;
        }
    }

    public int getAutodownloadMask() {
        int result = 0;
        int[] masksArray;
        int networkType = ApplicationLoader.getAutodownloadNetworkType();
        if (networkType == StatsController.TYPE_WIFI) {
            if (!wifiPreset.enabled) {
                return 0;
            }
            masksArray = getCurrentWiFiPreset().mask;
        } else if (networkType == StatsController.TYPE_ROAMING) {
            if (!roamingPreset.enabled) {
                return 0;
            }
            masksArray = getCurrentRoamingPreset().mask;
        } else {
            if (!mobilePreset.enabled) {
                return 0;
            }
            masksArray = getCurrentMobilePreset().mask;
        }
        for (int a = 0; a < masksArray.length; a++) {
            int mask = 0;
            if ((masksArray[a] & AUTODOWNLOAD_TYPE_PHOTO) != 0) {
                mask |= AUTODOWNLOAD_TYPE_PHOTO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_TYPE_AUDIO) != 0) {
                mask |= AUTODOWNLOAD_TYPE_AUDIO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_TYPE_VIDEO) != 0) {
                mask |= AUTODOWNLOAD_TYPE_VIDEO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_TYPE_DOCUMENT) != 0) {
                mask |= AUTODOWNLOAD_TYPE_DOCUMENT;
            }
            result |= mask << (a * 8);
        }
        return result;
    }

    protected int getAutodownloadMaskAll() {
        if (!mobilePreset.enabled && !roamingPreset.enabled && !wifiPreset.enabled) {
            return 0;
        }
        int mask = 0;
        for (int a = 0; a < 4; a++) {
            if ((getCurrentMobilePreset().mask[a] & AUTODOWNLOAD_TYPE_PHOTO) != 0 || (getCurrentWiFiPreset().mask[a] & AUTODOWNLOAD_TYPE_PHOTO) != 0 || (getCurrentRoamingPreset().mask[a] & AUTODOWNLOAD_TYPE_PHOTO) != 0) {
                mask |= AUTODOWNLOAD_TYPE_PHOTO;
            }
            if ((getCurrentMobilePreset().mask[a] & AUTODOWNLOAD_TYPE_AUDIO) != 0 || (getCurrentWiFiPreset().mask[a] & AUTODOWNLOAD_TYPE_AUDIO) != 0 || (getCurrentRoamingPreset().mask[a] & AUTODOWNLOAD_TYPE_AUDIO) != 0) {
                mask |= AUTODOWNLOAD_TYPE_AUDIO;
            }
            if ((getCurrentMobilePreset().mask[a] & AUTODOWNLOAD_TYPE_VIDEO) != 0 || (getCurrentWiFiPreset().mask[a] & AUTODOWNLOAD_TYPE_VIDEO) != 0 || (getCurrentRoamingPreset().mask[a] & AUTODOWNLOAD_TYPE_VIDEO) != 0) {
                mask |= AUTODOWNLOAD_TYPE_VIDEO;
            }
            if ((getCurrentMobilePreset().mask[a] & AUTODOWNLOAD_TYPE_DOCUMENT) != 0 || (getCurrentWiFiPreset().mask[a] & AUTODOWNLOAD_TYPE_DOCUMENT) != 0 || (getCurrentRoamingPreset().mask[a] & AUTODOWNLOAD_TYPE_DOCUMENT) != 0) {
                mask |= AUTODOWNLOAD_TYPE_DOCUMENT;
            }
        }
        return mask;
    }

    public void checkAutodownloadSettings() {
        int currentMask = getCurrentDownloadMask();
        if (currentMask == lastCheckMask) {
            return;
        }
        lastCheckMask = currentMask;
        if ((currentMask & AUTODOWNLOAD_TYPE_PHOTO) != 0) {
            if (photoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_PHOTO);
            }
        } else {
            for (int a = 0; a < photoDownloadQueue.size(); a++) {
                DownloadObject downloadObject = photoDownloadQueue.get(a);
                if (downloadObject.object instanceof TLRPC.Photo) {
                    TLRPC.Photo photo = (TLRPC.Photo) downloadObject.object;
                    TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                    getFileLoader().cancelLoadFile(photoSize);
                } else if (downloadObject.object instanceof TLRPC.Document) {
                    getFileLoader().cancelLoadFile((TLRPC.Document) downloadObject.object);
                }
            }
            photoDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_TYPE_AUDIO) != 0) {
            if (audioDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_AUDIO);
            }
        } else {
            for (int a = 0; a < audioDownloadQueue.size(); a++) {
                DownloadObject downloadObject = audioDownloadQueue.get(a);
                getFileLoader().cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            audioDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_TYPE_DOCUMENT) != 0) {
            if (documentDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_DOCUMENT);
            }
        } else {
            for (int a = 0; a < documentDownloadQueue.size(); a++) {
                DownloadObject downloadObject = documentDownloadQueue.get(a);
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                getFileLoader().cancelLoadFile(document);
            }
            documentDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_TYPE_VIDEO) != 0) {
            if (videoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_VIDEO);
            }
        } else {
            for (int a = 0; a < videoDownloadQueue.size(); a++) {
                DownloadObject downloadObject = videoDownloadQueue.get(a);
                getFileLoader().cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            videoDownloadQueue.clear();
        }
        int mask = getAutodownloadMaskAll();
        if (mask == 0) {
            getMessagesStorage().clearDownloadQueue(0);
        } else {
            if ((mask & AUTODOWNLOAD_TYPE_PHOTO) == 0) {
                getMessagesStorage().clearDownloadQueue(AUTODOWNLOAD_TYPE_PHOTO);
            }
            if ((mask & AUTODOWNLOAD_TYPE_AUDIO) == 0) {
                getMessagesStorage().clearDownloadQueue(AUTODOWNLOAD_TYPE_AUDIO);
            }
            if ((mask & AUTODOWNLOAD_TYPE_VIDEO) == 0) {
                getMessagesStorage().clearDownloadQueue(AUTODOWNLOAD_TYPE_VIDEO);
            }
            if ((mask & AUTODOWNLOAD_TYPE_DOCUMENT) == 0) {
                getMessagesStorage().clearDownloadQueue(AUTODOWNLOAD_TYPE_DOCUMENT);
            }
        }
    }

    public boolean canDownloadMedia(MessageObject messageObject) {
        return canDownloadMedia(messageObject.messageOwner) == 1;
    }

    public boolean canDownloadMedia(int type, int size) {
        Preset preset;
        int networkType = ApplicationLoader.getAutodownloadNetworkType();
        if (networkType == StatsController.TYPE_WIFI) {
            if (!wifiPreset.enabled) {
                return false;
            }
            preset = getCurrentWiFiPreset();

        } else if (networkType == StatsController.TYPE_ROAMING) {
            if (!roamingPreset.enabled) {
                return false;
            }
            preset = getCurrentRoamingPreset();
        } else {
            if (!mobilePreset.enabled) {
                return false;
            }
            preset = getCurrentMobilePreset();
        }
        int mask = preset.mask[1];
        int maxSize = preset.sizes[typeToIndex(type)];
        return (type == AUTODOWNLOAD_TYPE_PHOTO || size != 0 && size <= maxSize) && (type == AUTODOWNLOAD_TYPE_AUDIO || (mask & type) != 0);
    }

    public int canDownloadMedia(TLRPC.Message message) {
        if (message == null) {
            return 0;
        }
        int type;
        boolean isVideo;
        if ((isVideo = MessageObject.isVideoMessage(message)) || MessageObject.isGifMessage(message) || MessageObject.isRoundVideoMessage(message) || MessageObject.isGameMessage(message)) {
            type = AUTODOWNLOAD_TYPE_VIDEO;
        } else if (MessageObject.isVoiceMessage(message)) {
            type = AUTODOWNLOAD_TYPE_AUDIO;
        } else if (MessageObject.isPhoto(message) || MessageObject.isStickerMessage(message) || MessageObject.isAnimatedStickerMessage(message)) {
            type = AUTODOWNLOAD_TYPE_PHOTO;
        } else if (MessageObject.getDocument(message) != null) {
            type = AUTODOWNLOAD_TYPE_DOCUMENT;
        } else {
            return 0;
        }
        int index;
        TLRPC.Peer peer = message.peer_id;
        if (peer != null) {
            if (peer.user_id != 0) {
                if (getContactsController().contactsDict.containsKey(peer.user_id)) {
                    index = 0;
                } else {
                    index = 1;
                }
            } else if (peer.chat_id != 0) {
                if (message.from_id instanceof TLRPC.TL_peerUser && getContactsController().contactsDict.containsKey(message.from_id.user_id)) {
                    index = 0;
                } else {
                    index = 2;
                }
            } else {
                TLRPC.Chat chat = message.peer_id.channel_id != 0 ? getMessagesController().getChat(message.peer_id.channel_id) : null;
                if (ChatObject.isChannel(chat) && chat.megagroup) {
                    if (message.from_id instanceof TLRPC.TL_peerUser && getContactsController().contactsDict.containsKey(message.from_id.user_id)) {
                        index = 0;
                    } else {
                        index = 2;
                    }
                } else {
                    index = 3;
                }
            }
        } else {
            index = 1;
        }
        Preset preset;
        int networkType = ApplicationLoader.getAutodownloadNetworkType();
        if (networkType == StatsController.TYPE_WIFI) {
            if (!wifiPreset.enabled) {
                return 0;
            }
            preset = getCurrentWiFiPreset();

        } else if (networkType == StatsController.TYPE_ROAMING) {
            if (!roamingPreset.enabled) {
                return 0;
            }
            preset = getCurrentRoamingPreset();
        } else {
            if (!mobilePreset.enabled) {
                return 0;
            }
            preset = getCurrentMobilePreset();
        }
        int mask = preset.mask[index];
        int maxSize;
        if (type == AUTODOWNLOAD_TYPE_AUDIO) {
            maxSize = Math.max(512 * 1024, preset.sizes[typeToIndex(type)]);
        } else {
            maxSize = preset.sizes[typeToIndex(type)];
        }
        int size = MessageObject.getMessageSize(message);
        if (isVideo && preset.preloadVideo && size > maxSize && maxSize > 2 * 1024 * 1024) {
            return (mask & type) != 0 ? 2 : 0;
        } else {
            return (type == AUTODOWNLOAD_TYPE_PHOTO || size != 0 && size <= maxSize) && (type == AUTODOWNLOAD_TYPE_AUDIO || (mask & type) != 0) ? 1 : 0;
        }
    }

    protected boolean canDownloadNextTrack() {
        int networkType = ApplicationLoader.getAutodownloadNetworkType();
        if (networkType == StatsController.TYPE_WIFI) {
            return wifiPreset.enabled && getCurrentWiFiPreset().preloadMusic;
        } else if (networkType == StatsController.TYPE_ROAMING) {
            return roamingPreset.enabled && getCurrentRoamingPreset().preloadMusic;
        } else {
            return mobilePreset.enabled && getCurrentMobilePreset().preloadMusic;
        }
    }

    public int getCurrentDownloadMask() {
        int networkType = ApplicationLoader.getAutodownloadNetworkType();
        if (networkType == StatsController.TYPE_WIFI) {
            if (!wifiPreset.enabled) {
                return 0;
            }
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= getCurrentWiFiPreset().mask[a];
            }
            return mask;
        } else if (networkType == StatsController.TYPE_ROAMING) {
            if (!roamingPreset.enabled) {
                return 0;
            }
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= getCurrentRoamingPreset().mask[a];
            }
            return mask;
        } else {
            if (!mobilePreset.enabled) {
                return 0;
            }
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= getCurrentMobilePreset().mask[a];
            }
            return mask;
        }
    }

    public void savePresetToServer(int type) {
        TLRPC.TL_account_saveAutoDownloadSettings req = new TLRPC.TL_account_saveAutoDownloadSettings();
        Preset preset;
        boolean enabled;
        if (type == 0) {
            preset = getCurrentMobilePreset();
            enabled = mobilePreset.enabled;
        } else if (type == 1) {
            preset = getCurrentWiFiPreset();
            enabled = wifiPreset.enabled;
        } else {
            preset = getCurrentRoamingPreset();
            enabled = roamingPreset.enabled;
        }
        req.settings = new TLRPC.TL_autoDownloadSettings();
        req.settings.audio_preload_next = preset.preloadMusic;
        req.settings.video_preload_large = preset.preloadVideo;
        req.settings.phonecalls_less_data = preset.lessCallData;
        req.settings.video_upload_maxbitrate = preset.maxVideoBitrate;
        req.settings.disabled = !enabled;
        boolean photo = false;
        boolean video = false;
        boolean document = false;
        for (int a = 0; a < preset.mask.length; a++) {
            if ((preset.mask[a] & AUTODOWNLOAD_TYPE_PHOTO) != 0) {
                photo = true;
            }
            if ((preset.mask[a] & AUTODOWNLOAD_TYPE_VIDEO) != 0) {
                video = true;
            }
            if ((preset.mask[a] & AUTODOWNLOAD_TYPE_DOCUMENT) != 0) {
                document = true;
            }
            if (photo && video && document) {
                break;
            }
        }
        req.settings.photo_size_max = photo ? preset.sizes[PRESET_SIZE_NUM_PHOTO] : 0;
        req.settings.video_size_max = video ? preset.sizes[PRESET_SIZE_NUM_VIDEO] : 0;
        req.settings.file_size_max = document ? preset.sizes[PRESET_SIZE_NUM_DOCUMENT] : 0;
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    protected void cancelDownloading(ArrayList<Pair<Long, Integer>> arrayList) {
        for (int a = 0, N = arrayList.size(); a < N; a++) {
            Pair<Long, Integer> pair = arrayList.get(a);
            DownloadObject downloadObject = downloadQueuePairs.get(pair);
            if (downloadObject == null) {
                continue;
            }
            if (downloadObject.object instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                getFileLoader().cancelLoadFile(document, true);
            } else if (downloadObject.object instanceof TLRPC.Photo) {
                TLRPC.Photo photo = (TLRPC.Photo) downloadObject.object;
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                if (photoSize != null) {
                    getFileLoader().cancelLoadFile(photoSize, true);
                }
            }
        }
    }

    protected void processDownloadObjects(int type, ArrayList<DownloadObject> objects) {
        if (objects.isEmpty()) {
            return;
        }
        ArrayList<DownloadObject> queue;
        if (type == AUTODOWNLOAD_TYPE_PHOTO) {
            queue = photoDownloadQueue;
        } else if (type == AUTODOWNLOAD_TYPE_AUDIO) {
            queue = audioDownloadQueue;
        } else if (type == AUTODOWNLOAD_TYPE_VIDEO) {
            queue = videoDownloadQueue;
        } else {
            queue = documentDownloadQueue;
        }
        for (int a = 0; a < objects.size(); a++) {
            DownloadObject downloadObject = objects.get(a);
            String path;
            TLRPC.PhotoSize photoSize = null;
            if (downloadObject.object instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                path = FileLoader.getAttachFileName(document);
            } else if (downloadObject.object instanceof TLRPC.Photo) {
                TLRPC.Photo photo = (TLRPC.Photo) downloadObject.object;
                photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                path = FileLoader.getAttachFileName(photoSize);
            } else {
                path = null;
            }
            if (path == null || downloadQueueKeys.containsKey(path)) {
                continue;
            }
            boolean added = true;
            if (photoSize != null) {
                TLRPC.Photo photo = (TLRPC.Photo) downloadObject.object;
                int cacheType;
                if (downloadObject.secret) {
                    cacheType = 2;
                } else if (downloadObject.forceCache) {
                    cacheType = 1;
                } else {
                    cacheType = 0;
                }
                getFileLoader().loadFile(ImageLocation.getForPhoto(photoSize, photo), downloadObject.parent, null, 0, cacheType);
            } else if (downloadObject.object instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                getFileLoader().loadFile(document, downloadObject.parent, 0, downloadObject.secret ? 2 : 0);
            } else {
                added = false;
            }
            if (added) {
                queue.add(downloadObject);
                downloadQueueKeys.put(path, downloadObject);
                downloadQueuePairs.put(new Pair<>(downloadObject.id, downloadObject.type), downloadObject);
            }
        }
    }

    protected void newDownloadObjectsAvailable(int downloadMask) {
        int mask = getCurrentDownloadMask();
        if ((mask & AUTODOWNLOAD_TYPE_PHOTO) != 0 && (downloadMask & AUTODOWNLOAD_TYPE_PHOTO) != 0 && photoDownloadQueue.isEmpty()) {
            getMessagesStorage().getDownloadQueue(AUTODOWNLOAD_TYPE_PHOTO);
        }
        if ((mask & AUTODOWNLOAD_TYPE_AUDIO) != 0 && (downloadMask & AUTODOWNLOAD_TYPE_AUDIO) != 0 && audioDownloadQueue.isEmpty()) {
            getMessagesStorage().getDownloadQueue(AUTODOWNLOAD_TYPE_AUDIO);
        }
        if ((mask & AUTODOWNLOAD_TYPE_VIDEO) != 0 && (downloadMask & AUTODOWNLOAD_TYPE_VIDEO) != 0 && videoDownloadQueue.isEmpty()) {
            getMessagesStorage().getDownloadQueue(AUTODOWNLOAD_TYPE_VIDEO);
        }
        if ((mask & AUTODOWNLOAD_TYPE_DOCUMENT) != 0 && (downloadMask & AUTODOWNLOAD_TYPE_DOCUMENT) != 0 && documentDownloadQueue.isEmpty()) {
            getMessagesStorage().getDownloadQueue(AUTODOWNLOAD_TYPE_DOCUMENT);
        }
    }

    private void checkDownloadFinished(String fileName, int state) {
        DownloadObject downloadObject = downloadQueueKeys.get(fileName);
        if (downloadObject != null) {
            downloadQueueKeys.remove(fileName);
            downloadQueuePairs.remove(new Pair<>(downloadObject.id, downloadObject.type));
            if (state == 0 || state == 2) {
                getMessagesStorage().removeFromDownloadQueue(downloadObject.id, downloadObject.type, false /*state != 0*/);
            }
            if (downloadObject.type == AUTODOWNLOAD_TYPE_PHOTO) {
                photoDownloadQueue.remove(downloadObject);
                if (photoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_PHOTO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_TYPE_AUDIO) {
                audioDownloadQueue.remove(downloadObject);
                if (audioDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_AUDIO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_TYPE_VIDEO) {
                videoDownloadQueue.remove(downloadObject);
                if (videoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_VIDEO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_TYPE_DOCUMENT) {
                documentDownloadQueue.remove(downloadObject);
                if (documentDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_TYPE_DOCUMENT);
                }
            }
        }
    }

    public int generateObserverTag() {
        return lastTag++;
    }

    public void addLoadingFileObserver(String fileName, FileDownloadProgressListener observer) {
        addLoadingFileObserver(fileName, null, observer);
    }

    public void addLoadingFileObserver(String fileName, MessageObject messageObject, FileDownloadProgressListener observer) {
        if (listenerInProgress) {
            addLaterArray.put(fileName, observer);
            return;
        }
        removeLoadingFileObserver(observer);

        ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            loadingFileObservers.put(fileName, arrayList);
        }
        arrayList.add(new WeakReference<>(observer));
        if (messageObject != null) {
            ArrayList<MessageObject> messageObjects = loadingFileMessagesObservers.get(fileName);
            if (messageObjects == null) {
                messageObjects = new ArrayList<>();
                loadingFileMessagesObservers.put(fileName, messageObjects);
            }
            messageObjects.add(messageObject);
        }

        observersByTag.put(observer.getObserverTag(), fileName);
    }

    public void removeLoadingFileObserver(FileDownloadProgressListener observer) {
        if (listenerInProgress) {
            deleteLaterArray.add(observer);
            return;
        }
        String fileName = observersByTag.get(observer.getObserverTag());
        if (fileName != null) {
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (int a = 0; a < arrayList.size(); a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() == null || reference.get() == observer) {
                        arrayList.remove(a);
                        a--;
                    }
                }
                if (arrayList.isEmpty()) {
                    loadingFileObservers.remove(fileName);
                }
            }
            observersByTag.remove(observer.getObserverTag());
        }
    }

    private void processLaterArrays() {
        for (HashMap.Entry<String, FileDownloadProgressListener> listener : addLaterArray.entrySet()) {
            addLoadingFileObserver(listener.getKey(), listener.getValue());
        }
        addLaterArray.clear();
        for (FileDownloadProgressListener listener : deleteLaterArray) {
            removeLoadingFileObserver(listener);
        }
        deleteLaterArray.clear();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoadFailed || id == NotificationCenter.httpFileDidFailedLoad) {
            String fileName = (String) args[0];
            Integer canceled = (Integer) args[1];
            listenerInProgress = true;
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (int a = 0, size = arrayList.size(); a < size; a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onFailedDownload(fileName, canceled == 1);
                        if (canceled != 1) {
                            observersByTag.remove(reference.get().getObserverTag());
                        }
                    }
                }
                if (canceled != 1) {
                    loadingFileObservers.remove(fileName);
                }
            }
            listenerInProgress = false;
            processLaterArrays();
            checkDownloadFinished(fileName, canceled);
        } else if (id == NotificationCenter.fileLoaded || id == NotificationCenter.httpFileDidLoad) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<MessageObject> messageObjects = loadingFileMessagesObservers.get(fileName);
            if (messageObjects != null) {
                for (int a = 0, size = messageObjects.size(); a < size; a++) {
                    MessageObject messageObject = messageObjects.get(a);
                    messageObject.mediaExists = true;
                }
                loadingFileMessagesObservers.remove(fileName);
            }
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (int a = 0, size = arrayList.size(); a < size; a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onSuccessDownload(fileName);
                        observersByTag.remove(reference.get().getObserverTag());
                    }
                }
                loadingFileObservers.remove(fileName);
            }
            listenerInProgress = false;
            processLaterArrays();
            checkDownloadFinished(fileName, 0);
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                for (int a = 0, size = arrayList.size(); a < size; a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onProgressDownload(fileName, loadedSize, totalSize);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
        } else if (id == NotificationCenter.fileUploadProgressChanged) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                Boolean enc = (Boolean) args[3];
                for (int a = 0, size = arrayList.size(); a < size; a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onProgressUpload(fileName, loadedSize, totalSize, enc);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
            try {
                ArrayList<SendMessagesHelper.DelayedMessage> delayedMessages = getSendMessagesHelper().getDelayedMessages(fileName);
                if (delayedMessages != null) {
                    for (int a = 0; a < delayedMessages.size(); a++) {
                        SendMessagesHelper.DelayedMessage delayedMessage = delayedMessages.get(a);
                        if (delayedMessage.encryptedChat == null) {
                            long dialogId = delayedMessage.peer;
                            int topMessageId = delayedMessage.topMessageId;
                            Long lastTime = typingTimes.get(dialogId);
                            if (delayedMessage.type == 4) {
                                if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                    MessageObject messageObject = (MessageObject) delayedMessage.extraHashMap.get(fileName + "_i");
                                    if (messageObject != null && messageObject.isVideo()) {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 5, 0);
                                    } else if (messageObject != null && messageObject.getDocument() != null) {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 3, 0);
                                    } else {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 4, 0);
                                    }
                                    typingTimes.put(dialogId, System.currentTimeMillis());
                                }
                            } else {
                                TLRPC.Document document = delayedMessage.obj.getDocument();
                                if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                    if (delayedMessage.obj.isRoundVideo()) {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 8, 0);
                                    } else if (delayedMessage.obj.isVideo()) {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 5, 0);
                                    } else if (delayedMessage.obj.isVoice()) {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 9, 0);
                                    } else if (delayedMessage.obj.getDocument() != null) {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 3, 0);
                                    } else if (delayedMessage.photoSize != null) {
                                        getMessagesController().sendTyping(dialogId, topMessageId, 4, 0);
                                    }
                                    typingTimes.put(dialogId, System.currentTimeMillis());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static float getProgress(long[] progressSizes) {
        if (progressSizes == null || progressSizes.length < 2 || progressSizes[1] == 0) {
            return 0f;
        }
        return Math.min(1f, progressSizes[0] / (float) progressSizes[1]);
    }
}
