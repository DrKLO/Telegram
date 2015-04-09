/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import java.util.ArrayList;
import java.util.HashMap;

public class NotificationCenter {

    private static int totalEvents = 1;

    public static final int didReceivedNewMessages = totalEvents++;
    public static final int updateInterfaces = totalEvents++;
    public static final int dialogsNeedReload = totalEvents++;
    public static final int closeChats = totalEvents++;
    public static final int messagesDeleted = totalEvents++;
    public static final int messagesRead = totalEvents++;
    public static final int messagesDidLoaded = totalEvents++;
    public static final int messageReceivedByAck = totalEvents++;
    public static final int messageReceivedByServer = totalEvents++;
    public static final int messageSendError = totalEvents++;
    public static final int contactsDidLoaded = totalEvents++;
    public static final int chatDidCreated = totalEvents++;
    public static final int chatDidFailCreate = totalEvents++;
    public static final int chatInfoDidLoaded = totalEvents++;
    public static final int mediaDidLoaded = totalEvents++;
    public static final int mediaCountDidLoaded = totalEvents++;
    public static final int encryptedChatUpdated = totalEvents++;
    public static final int messagesReadedEncrypted = totalEvents++;
    public static final int encryptedChatCreated = totalEvents++;
    public static final int userPhotosLoaded = totalEvents++;
    public static final int removeAllMessagesFromDialog = totalEvents++;
    public static final int notificationsSettingsUpdated = totalEvents++;
    public static final int pushMessagesUpdated = totalEvents++;
    public static final int blockedUsersDidLoaded = totalEvents++;
    public static final int openedChatChanged = totalEvents++;
    public static final int hideEmojiKeyboard = totalEvents++;
    public static final int stopEncodingService = totalEvents++;
    public static final int didCreatedNewDeleteTask = totalEvents++;
    public static final int mainUserInfoChanged = totalEvents++;
    public static final int privacyRulesUpdated = totalEvents++;
    public static final int updateMessageMedia = totalEvents++;
    public static final int recentImagesDidLoaded = totalEvents++;
    public static final int replaceMessagesObjects = totalEvents++;
    public static final int didSetPasscode = totalEvents++;
    public static final int didSetTwoStepPassword = totalEvents++;
    public static final int screenStateChanged = totalEvents++;
    public static final int appSwitchedToForeground = totalEvents++;
    public static final int didLoadedReplyMessages = totalEvents++;
    public static final int newSessionReceived = totalEvents++;
    public static final int didReceivedWebpages = totalEvents++;
    public static final int didReceivedWebpagesInUpdates = totalEvents++;

    public static final int httpFileDidLoaded = totalEvents++;
    public static final int httpFileDidFailedLoad = totalEvents++;

    public static final int messageThumbGenerated = totalEvents++;

    public static final int wallpapersDidLoaded = totalEvents++;
    public static final int closeOtherAppActivities = totalEvents++;
    public static final int didUpdatedConnectionState = totalEvents++;
    public static final int didReceiveSmsCode = totalEvents++;
    public static final int emojiDidLoaded = totalEvents++;
    public static final int appDidLogout = totalEvents++;

    public static final int FileDidUpload = totalEvents++;
    public static final int FileDidFailUpload = totalEvents++;
    public static final int FileUploadProgressChanged = totalEvents++;
    public static final int FileLoadProgressChanged = totalEvents++;
    public static final int FileDidLoaded = totalEvents++;
    public static final int FileDidFailedLoad = totalEvents++;
    public static final int FilePreparingStarted = totalEvents++;
    public static final int FileNewChunkAvailable = totalEvents++;
    public static final int FilePreparingFailed = totalEvents++;

    public static final int audioProgressDidChanged = totalEvents++;
    public static final int audioDidReset = totalEvents++;
    public static final int recordProgressChanged = totalEvents++;
    public static final int recordStarted = totalEvents++;
    public static final int recordStartError = totalEvents++;
    public static final int recordStopped = totalEvents++;
    public static final int screenshotTook = totalEvents++;
    public static final int albumsDidLoaded = totalEvents++;
    public static final int audioDidSent = totalEvents++;
    public static final int audioDidStarted = totalEvents++;
    public static final int audioRouteChanged = totalEvents++;

    final private HashMap<Integer, ArrayList<Object>> observers = new HashMap<>();

    final private HashMap<Integer, Object> removeAfterBroadcast = new HashMap<>();
    final private HashMap<Integer, Object> addAfterBroadcast = new HashMap<>();

    private int broadcasting = 0;

    private static volatile NotificationCenter Instance = null;
    public static NotificationCenter getInstance() {
        NotificationCenter localInstance = Instance;
        if (localInstance == null) {
            synchronized (NotificationCenter.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new NotificationCenter();
                }
            }
        }
        return localInstance;
    }

    public interface NotificationCenterDelegate {
        void didReceivedNotification(int id, Object... args);
    }

    public void postNotificationName(int id, Object... args) {
        synchronized (observers) {
            broadcasting++;
            ArrayList<Object> objects = observers.get(id);
            if (objects != null) {
                for (Object obj : objects) {
                    ((NotificationCenterDelegate)obj).didReceivedNotification(id, args);
                }
            }
            broadcasting--;
            if (broadcasting == 0) {
                if (!removeAfterBroadcast.isEmpty()) {
                    for (HashMap.Entry<Integer, Object> entry : removeAfterBroadcast.entrySet()) {
                        removeObserver(entry.getValue(), entry.getKey());
                    }
                    removeAfterBroadcast.clear();
                }
                if (!addAfterBroadcast.isEmpty()) {
                    for (HashMap.Entry<Integer, Object> entry : addAfterBroadcast.entrySet()) {
                        addObserver(entry.getValue(), entry.getKey());
                    }
                    addAfterBroadcast.clear();
                }
            }
        }
    }

    public void addObserver(Object observer, int id) {
        synchronized (observers) {
            if (broadcasting != 0) {
                addAfterBroadcast.put(id, observer);
                return;
            }
            ArrayList<Object> objects = observers.get(id);
            if (objects == null) {
                observers.put(id, (objects = new ArrayList<>()));
            }
            if (objects.contains(observer)) {
                return;
            }
            objects.add(observer);
        }
    }

    public void removeObserver(Object observer, int id) {
        synchronized (observers) {
            if (broadcasting != 0) {
                removeAfterBroadcast.put(id, observer);
                return;
            }
            ArrayList<Object> objects = observers.get(id);
            if (objects != null) {
                objects.remove(observer);
                if (objects.size() == 0) {
                    observers.remove(id);
                }
            }
        }
    }
}
