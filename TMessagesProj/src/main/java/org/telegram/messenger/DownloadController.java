/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.util.LongSparseArray;
import android.util.SparseArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

public class DownloadController implements NotificationCenter.NotificationCenterDelegate {

    public interface FileDownloadProgressListener {
        void onFailedDownload(String fileName);
        void onSuccessDownload(String fileName);
        void onProgressDownload(String fileName, float progress);
        void onProgressUpload(String fileName, float progress, boolean isEncrypted);
        int getObserverTag();
    }

    public static final int AUTODOWNLOAD_MASK_PHOTO = 1;
    public static final int AUTODOWNLOAD_MASK_AUDIO = 2;
    public static final int AUTODOWNLOAD_MASK_VIDEO = 4;
    public static final int AUTODOWNLOAD_MASK_DOCUMENT = 8;
    public static final int AUTODOWNLOAD_MASK_MUSIC = 16;
    public static final int AUTODOWNLOAD_MASK_GIF = 32;
    public static final int AUTODOWNLOAD_MASK_VIDEOMESSAGE = 64;
    public boolean globalAutodownloadEnabled;
    public int mobileDataDownloadMask[] = new int[4];
    public int wifiDownloadMask[] = new int[4];
    public int roamingDownloadMask[] = new int[4];
    public int mobileMaxFileSize[] = new int[7];
    public int wifiMaxFileSize[] = new int[7];
    public int roamingMaxFileSize[] = new int[7];
    private int lastCheckMask = 0;
    private ArrayList<DownloadObject> photoDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> audioDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> videoMessageDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> documentDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> musicDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> gifDownloadQueue = new ArrayList<>();
    private ArrayList<DownloadObject> videoDownloadQueue = new ArrayList<>();
    private HashMap<String, DownloadObject> downloadQueueKeys = new HashMap<>();

    private HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>> loadingFileObservers = new HashMap<>();
    private HashMap<String, ArrayList<MessageObject>> loadingFileMessagesObservers = new HashMap<>();
    private SparseArray<String> observersByTag = new SparseArray<>();
    private boolean listenerInProgress = false;
    private HashMap<String, FileDownloadProgressListener> addLaterArray = new HashMap<>();
    private ArrayList<FileDownloadProgressListener> deleteLaterArray = new ArrayList<>();
    private int lastTag = 0;

    private int currentAccount;
    private static volatile DownloadController Instance[] = new DownloadController[UserConfig.MAX_ACCOUNT_COUNT];

    private LongSparseArray<Long> typingTimes = new LongSparseArray<>();

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
        currentAccount = instance;
        SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
        for (int a = 0; a < 4; a++) {
            String key = "mobileDataDownloadMask" + (a == 0 ? "" : a);
            if (a == 0 || preferences.contains(key)) {
                mobileDataDownloadMask[a] = preferences.getInt(key, AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO | AUTODOWNLOAD_MASK_MUSIC | AUTODOWNLOAD_MASK_GIF | AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                wifiDownloadMask[a] = preferences.getInt("wifiDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO | AUTODOWNLOAD_MASK_MUSIC | AUTODOWNLOAD_MASK_GIF | AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                roamingDownloadMask[a] = preferences.getInt("roamingDownloadMask" + (a == 0 ? "" : a), 0);
            } else {
                mobileDataDownloadMask[a] = mobileDataDownloadMask[0];
                wifiDownloadMask[a] = wifiDownloadMask[0];
                roamingDownloadMask[a] = roamingDownloadMask[0];
            }
        }
        for (int a = 0; a < 7; a++) {
            int sdefault;
            if (a == 1) {
                sdefault = 2 * 1024 * 1024;
            } else if (a == 6) {
                sdefault = 5 * 1024 * 1024;
            } else {
                sdefault = 10 * 1024 * 1024;
            }
            mobileMaxFileSize[a] = preferences.getInt("mobileMaxDownloadSize" + a, sdefault);
            wifiMaxFileSize[a] = preferences.getInt("wifiMaxDownloadSize" + a, sdefault);
            roamingMaxFileSize[a] = preferences.getInt("roamingMaxDownloadSize" + a, sdefault);
        }
        globalAutodownloadEnabled = preferences.getBoolean("globalAutodownloadEnabled", true);

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationCenter.getInstance(currentAccount).addObserver(DownloadController.this, NotificationCenter.FileDidFailedLoad);
                NotificationCenter.getInstance(currentAccount).addObserver(DownloadController.this, NotificationCenter.FileDidLoaded);
                NotificationCenter.getInstance(currentAccount).addObserver(DownloadController.this, NotificationCenter.FileLoadProgressChanged);
                NotificationCenter.getInstance(currentAccount).addObserver(DownloadController.this, NotificationCenter.FileUploadProgressChanged);
                NotificationCenter.getInstance(currentAccount).addObserver(DownloadController.this, NotificationCenter.httpFileDidLoaded);
                NotificationCenter.getInstance(currentAccount).addObserver(DownloadController.this, NotificationCenter.httpFileDidFailedLoad);
            }
        });

        BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkAutodownloadSettings();
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        ApplicationLoader.applicationContext.registerReceiver(networkStateReceiver, filter);

        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            checkAutodownloadSettings();
        }
    }

    public static int maskToIndex(int mask) {
        if (mask == AUTODOWNLOAD_MASK_PHOTO) {
            return 0;
        } else if (mask == AUTODOWNLOAD_MASK_AUDIO) {
            return 1;
        } else if (mask == AUTODOWNLOAD_MASK_VIDEO) {
            return 2;
        } else if (mask == AUTODOWNLOAD_MASK_DOCUMENT) {
            return 3;
        } else if (mask == AUTODOWNLOAD_MASK_MUSIC) {
            return 4;
        } else if (mask == AUTODOWNLOAD_MASK_GIF) {
            return 5;
        } else if (mask == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
            return 6;
        }
        return 0;
    }

    public void cleanup() {
        photoDownloadQueue.clear();
        audioDownloadQueue.clear();
        videoMessageDownloadQueue.clear();
        documentDownloadQueue.clear();
        videoDownloadQueue.clear();
        musicDownloadQueue.clear();
        gifDownloadQueue.clear();
        downloadQueueKeys.clear();
        typingTimes.clear();
    }

    protected int getAutodownloadMask() {
        if (!globalAutodownloadEnabled) {
            return 0;
        }
        int result = 0;
        int masksArray[];
        if (ConnectionsManager.isConnectedToWiFi()) {
            masksArray = wifiDownloadMask;
        } else if (ConnectionsManager.isRoaming()) {
            masksArray = roamingDownloadMask;
        } else {
            masksArray = mobileDataDownloadMask;
        }
        for (int a = 0; a < 4; a++) {
            int mask = 0;
            if ((masksArray[a] & AUTODOWNLOAD_MASK_PHOTO) != 0) {
                mask |= AUTODOWNLOAD_MASK_PHOTO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_AUDIO) != 0) {
                mask |= AUTODOWNLOAD_MASK_AUDIO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEOMESSAGE;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_VIDEO) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEO;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                mask |= AUTODOWNLOAD_MASK_DOCUMENT;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_MUSIC) != 0) {
                mask |= AUTODOWNLOAD_MASK_MUSIC;
            }
            if ((masksArray[a] & AUTODOWNLOAD_MASK_GIF) != 0) {
                mask |= AUTODOWNLOAD_MASK_GIF;
            }
            result |= mask << (a * 8);
        }
        return result;
    }

    protected int getAutodownloadMaskAll() {
        if (!globalAutodownloadEnabled) {
            return 0;
        }
        int mask = 0;
        for (int a = 0; a < 4; a++) {
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0) {
                mask |= AUTODOWNLOAD_MASK_PHOTO;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0) {
                mask |= AUTODOWNLOAD_MASK_AUDIO;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEOMESSAGE;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0) {
                mask |= AUTODOWNLOAD_MASK_VIDEO;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                mask |= AUTODOWNLOAD_MASK_DOCUMENT;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0) {
                mask |= AUTODOWNLOAD_MASK_MUSIC;
            }
            if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0) {
                mask |= AUTODOWNLOAD_MASK_GIF;
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
        if ((currentMask & AUTODOWNLOAD_MASK_PHOTO) != 0) {
            if (photoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_PHOTO);
            }
        } else {
            for (int a = 0; a < photoDownloadQueue.size(); a++) {
                DownloadObject downloadObject = photoDownloadQueue.get(a);
                FileLoader.getInstance(currentAccount).cancelLoadFile((TLRPC.PhotoSize) downloadObject.object);
            }
            photoDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_AUDIO) != 0) {
            if (audioDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_AUDIO);
            }
        } else {
            for (int a = 0; a < audioDownloadQueue.size(); a++) {
                DownloadObject downloadObject = audioDownloadQueue.get(a);
                FileLoader.getInstance(currentAccount).cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            audioDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
            if (videoMessageDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
            }
        } else {
            for (int a = 0; a < videoMessageDownloadQueue.size(); a++) {
                DownloadObject downloadObject = videoMessageDownloadQueue.get(a);
                FileLoader.getInstance(currentAccount).cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            videoMessageDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
            if (documentDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_DOCUMENT);
            }
        } else {
            for (int a = 0; a < documentDownloadQueue.size(); a++) {
                DownloadObject downloadObject = documentDownloadQueue.get(a);
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance(currentAccount).cancelLoadFile(document);
            }
            documentDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_VIDEO) != 0) {
            if (videoDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEO);
            }
        } else {
            for (int a = 0; a < videoDownloadQueue.size(); a++) {
                DownloadObject downloadObject = videoDownloadQueue.get(a);
                FileLoader.getInstance(currentAccount).cancelLoadFile((TLRPC.Document) downloadObject.object);
            }
            videoDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_MUSIC) != 0) {
            if (musicDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_MUSIC);
            }
        } else {
            for (int a = 0; a < musicDownloadQueue.size(); a++) {
                DownloadObject downloadObject = musicDownloadQueue.get(a);
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance(currentAccount).cancelLoadFile(document);
            }
            musicDownloadQueue.clear();
        }
        if ((currentMask & AUTODOWNLOAD_MASK_GIF) != 0) {
            if (gifDownloadQueue.isEmpty()) {
                newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_GIF);
            }
        } else {
            for (int a = 0; a < gifDownloadQueue.size(); a++) {
                DownloadObject downloadObject = gifDownloadQueue.get(a);
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance(currentAccount).cancelLoadFile(document);
            }
            gifDownloadQueue.clear();
        }

        int mask = getAutodownloadMaskAll();
        if (mask == 0) {
            MessagesStorage.getInstance(currentAccount).clearDownloadQueue(0);
        } else {
            if ((mask & AUTODOWNLOAD_MASK_PHOTO) == 0) {
                MessagesStorage.getInstance(currentAccount).clearDownloadQueue(AUTODOWNLOAD_MASK_PHOTO);
            }
            if ((mask & AUTODOWNLOAD_MASK_AUDIO) == 0) {
                MessagesStorage.getInstance(currentAccount).clearDownloadQueue(AUTODOWNLOAD_MASK_AUDIO);
            }
            if ((mask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) == 0) {
                MessagesStorage.getInstance(currentAccount).clearDownloadQueue(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
            }
            if ((mask & AUTODOWNLOAD_MASK_VIDEO) == 0) {
                MessagesStorage.getInstance(currentAccount).clearDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
            }
            if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) == 0) {
                MessagesStorage.getInstance(currentAccount).clearDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
            }
            if ((mask & AUTODOWNLOAD_MASK_MUSIC) == 0) {
                MessagesStorage.getInstance(currentAccount).clearDownloadQueue(AUTODOWNLOAD_MASK_MUSIC);
            }
            if ((mask & AUTODOWNLOAD_MASK_GIF) == 0) {
                MessagesStorage.getInstance(currentAccount).clearDownloadQueue(AUTODOWNLOAD_MASK_GIF);
            }
        }
    }

    public boolean canDownloadMedia(MessageObject messageObject) {
        return canDownloadMedia(messageObject.messageOwner);
    }

    public boolean canDownloadMedia(TLRPC.Message message) {
        if (!globalAutodownloadEnabled) {
            return false;
        }
        int type;
        if (MessageObject.isPhoto(message)) {
            type = AUTODOWNLOAD_MASK_PHOTO;
        } else if (MessageObject.isVoiceMessage(message)) {
            type = AUTODOWNLOAD_MASK_AUDIO;
        } else if (MessageObject.isRoundVideoMessage(message)) {
            type = AUTODOWNLOAD_MASK_VIDEOMESSAGE;
        } else if (MessageObject.isVideoMessage(message)) {
            type = AUTODOWNLOAD_MASK_VIDEO;
        } else if (MessageObject.isMusicMessage(message)) {
            type = AUTODOWNLOAD_MASK_MUSIC;
        } else if (MessageObject.isGifMessage(message)) {
            type = AUTODOWNLOAD_MASK_GIF;
        } else {
            type = AUTODOWNLOAD_MASK_DOCUMENT;
        }
        int mask;
        int index;
        int maxSize;
        TLRPC.Peer peer = message.to_id;
        if (peer != null) {
            if (peer.user_id != 0) {
                if (ContactsController.getInstance(currentAccount).contactsDict.containsKey(peer.user_id)) {
                    index = 0;
                } else {
                    index = 1;
                }
            } else if (peer.chat_id != 0) {
                index = 2;
            } else {
                if (MessageObject.isMegagroup(message)) {
                    index = 2;
                } else {
                    index = 3;
                }
            }
        } else {
            index = 1;
        }
        if (ConnectionsManager.isConnectedToWiFi()) {
            mask = wifiDownloadMask[index];
            maxSize = wifiMaxFileSize[maskToIndex(type)];
        } else if (ConnectionsManager.isRoaming()) {
            mask = roamingDownloadMask[index];
            maxSize = roamingMaxFileSize[maskToIndex(type)];
        } else {
            mask = mobileDataDownloadMask[index];
            maxSize = mobileMaxFileSize[maskToIndex(type)];
        }
        return (type == AUTODOWNLOAD_MASK_PHOTO || MessageObject.getMessageSize(message) <= maxSize) && (mask & type) != 0;
    }

    protected int getCurrentDownloadMask() {
        if (!globalAutodownloadEnabled) {
            return 0;
        }
        if (ConnectionsManager.isConnectedToWiFi()) {
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= wifiDownloadMask[a];
            }
            return mask;
        } else if (ConnectionsManager.isRoaming()) {
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= roamingDownloadMask[a];
            }
            return mask;
        } else {
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                mask |= mobileDataDownloadMask[a];
            }
            return mask;
        }
    }

    protected void processDownloadObjects(int type, ArrayList<DownloadObject> objects) {
        if (objects.isEmpty()) {
            return;
        }
        ArrayList<DownloadObject> queue = null;
        if (type == AUTODOWNLOAD_MASK_PHOTO) {
            queue = photoDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_AUDIO) {
            queue = audioDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
            queue = videoMessageDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_VIDEO) {
            queue = videoDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_DOCUMENT) {
            queue = documentDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_MUSIC) {
            queue = musicDownloadQueue;
        } else if (type == AUTODOWNLOAD_MASK_GIF) {
            queue = gifDownloadQueue;
        }
        for (int a = 0; a < objects.size(); a++) {
            DownloadObject downloadObject = objects.get(a);
            String path;
            if (downloadObject.object instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                path = FileLoader.getAttachFileName(document);
            } else {
                path = FileLoader.getAttachFileName(downloadObject.object);
            }
            if (downloadQueueKeys.containsKey(path)) {
                continue;
            }

            boolean added = true;
            if (downloadObject.object instanceof TLRPC.PhotoSize) {
                FileLoader.getInstance(currentAccount).loadFile((TLRPC.PhotoSize) downloadObject.object, null, downloadObject.secret ? 2 : 0);
            } else if (downloadObject.object instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                FileLoader.getInstance(currentAccount).loadFile(document, false, downloadObject.secret ? 2 : 0);
            } else {
                added = false;
            }
            if (added) {
                queue.add(downloadObject);
                downloadQueueKeys.put(path, downloadObject);
            }
        }
    }

    protected void newDownloadObjectsAvailable(int downloadMask) {
        int mask = getCurrentDownloadMask();
        if ((mask & AUTODOWNLOAD_MASK_PHOTO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_PHOTO) != 0 && photoDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance(currentAccount).getDownloadQueue(AUTODOWNLOAD_MASK_PHOTO);
        }
        if ((mask & AUTODOWNLOAD_MASK_AUDIO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_AUDIO) != 0 && audioDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance(currentAccount).getDownloadQueue(AUTODOWNLOAD_MASK_AUDIO);
        }
        if ((mask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 && (downloadMask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 && videoMessageDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance(currentAccount).getDownloadQueue(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
        }
        if ((mask & AUTODOWNLOAD_MASK_VIDEO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_VIDEO) != 0 && videoDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance(currentAccount).getDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
        }
        if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && (downloadMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && documentDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance(currentAccount).getDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
        }
        if ((mask & AUTODOWNLOAD_MASK_MUSIC) != 0 && (downloadMask & AUTODOWNLOAD_MASK_MUSIC) != 0 && musicDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance(currentAccount).getDownloadQueue(AUTODOWNLOAD_MASK_MUSIC);
        }
        if ((mask & AUTODOWNLOAD_MASK_GIF) != 0 && (downloadMask & AUTODOWNLOAD_MASK_GIF) != 0 && gifDownloadQueue.isEmpty()) {
            MessagesStorage.getInstance(currentAccount).getDownloadQueue(AUTODOWNLOAD_MASK_GIF);
        }
    }

    private void checkDownloadFinished(String fileName, int state) {
        DownloadObject downloadObject = downloadQueueKeys.get(fileName);
        if (downloadObject != null) {
            downloadQueueKeys.remove(fileName);
            if (state == 0 || state == 2) {
                MessagesStorage.getInstance(currentAccount).removeFromDownloadQueue(downloadObject.id, downloadObject.type, false /*state != 0*/);
            }
            if (downloadObject.type == AUTODOWNLOAD_MASK_PHOTO) {
                photoDownloadQueue.remove(downloadObject);
                if (photoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_PHOTO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_AUDIO) {
                audioDownloadQueue.remove(downloadObject);
                if (audioDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_AUDIO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
                videoMessageDownloadQueue.remove(downloadObject);
                if (videoMessageDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_VIDEO) {
                videoDownloadQueue.remove(downloadObject);
                if (videoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEO);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_DOCUMENT) {
                documentDownloadQueue.remove(downloadObject);
                if (documentDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_DOCUMENT);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_MUSIC) {
                musicDownloadQueue.remove(downloadObject);
                if (musicDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_MUSIC);
                }
            } else if (downloadObject.type == AUTODOWNLOAD_MASK_GIF) {
                gifDownloadQueue.remove(downloadObject);
                if (gifDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_GIF);
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

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.FileDidFailedLoad || id == NotificationCenter.httpFileDidFailedLoad) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                for (int a = 0, size = arrayList.size(); a < size; a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onFailedDownload(fileName);
                        observersByTag.remove(reference.get().getObserverTag());
                    }
                }
                loadingFileObservers.remove(fileName);
            }
            listenerInProgress = false;
            processLaterArrays();
            checkDownloadFinished(fileName, (Integer) args[1]);
        } else if (id == NotificationCenter.FileDidLoaded || id == NotificationCenter.httpFileDidLoaded) {
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
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Float progress = (Float) args[1];
                for (int a = 0, size = arrayList.size(); a < size; a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onProgressDownload(fileName, progress);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
        } else if (id == NotificationCenter.FileUploadProgressChanged) {
            listenerInProgress = true;
            String fileName = (String) args[0];
            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList != null) {
                Float progress = (Float) args[1];
                Boolean enc = (Boolean) args[2];
                for (int a = 0, size = arrayList.size(); a < size; a++) {
                    WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                    if (reference.get() != null) {
                        reference.get().onProgressUpload(fileName, progress, enc);
                    }
                }
            }
            listenerInProgress = false;
            processLaterArrays();
            try {
                ArrayList<SendMessagesHelper.DelayedMessage> delayedMessages = SendMessagesHelper.getInstance(currentAccount).getDelayedMessages(fileName);
                if (delayedMessages != null) {
                    for (int a = 0; a < delayedMessages.size(); a++) {
                        SendMessagesHelper.DelayedMessage delayedMessage = delayedMessages.get(a);
                        if (delayedMessage.encryptedChat == null) {
                            long dialog_id = delayedMessage.peer;
                            if (delayedMessage.type == 4) {
                                Long lastTime = typingTimes.get(dialog_id);
                                if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                    MessageObject messageObject = (MessageObject) delayedMessage.extraHashMap.get(fileName + "_i");
                                    if (messageObject != null && messageObject.isVideo()) {
                                        MessagesController.getInstance(currentAccount).sendTyping(dialog_id, 5, 0);
                                    } else {
                                        MessagesController.getInstance(currentAccount).sendTyping(dialog_id, 4, 0);
                                    }
                                    typingTimes.put(dialog_id, System.currentTimeMillis());
                                }
                            } else {
                                Long lastTime = typingTimes.get(dialog_id);
                                TLRPC.Document document = delayedMessage.obj.getDocument();
                                if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                    if (delayedMessage.obj.isRoundVideo()) {
                                        MessagesController.getInstance(currentAccount).sendTyping(dialog_id, 8, 0);
                                    } else if (delayedMessage.obj.isVideo()) {
                                        MessagesController.getInstance(currentAccount).sendTyping(dialog_id, 5, 0);
                                    } else if (delayedMessage.obj.isVoice()) {
                                        MessagesController.getInstance(currentAccount).sendTyping(dialog_id, 9, 0);
                                    } else if (delayedMessage.obj.getDocument() != null) {
                                        MessagesController.getInstance(currentAccount).sendTyping(dialog_id, 3, 0);
                                    } else if (delayedMessage.location != null) {
                                        MessagesController.getInstance(currentAccount).sendTyping(dialog_id, 4, 0);
                                    }
                                    typingTimes.put(dialog_id, System.currentTimeMillis());
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
}
