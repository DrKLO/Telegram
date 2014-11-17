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

    public static final int didReceivedNewMessages = 1;
    public static final int updateInterfaces = 3;
    public static final int dialogsNeedReload = 4;
    public static final int closeChats = 5;
    public static final int messagesDeleted = 6;
    public static final int messagesRead = 7;
    public static final int messagesDidLoaded = 8;
    public static final int messageReceivedByAck = 9;
    public static final int messageReceivedByServer = 10;
    public static final int messageSendError = 11;
    public static final int contactsDidLoaded = 13;
    public static final int chatDidCreated = 15;
    public static final int chatDidFailCreate = 16;
    public static final int chatInfoDidLoaded = 17;
    public static final int mediaDidLoaded = 18;
    public static final int mediaCountDidLoaded = 20;
    public static final int encryptedChatUpdated = 21;
    public static final int messagesReadedEncrypted = 22;
    public static final int encryptedChatCreated = 23;
    public static final int userPhotosLoaded = 24;
    public static final int removeAllMessagesFromDialog = 25;
    public static final int notificationsSettingsUpdated = 26;
    public static final int pushMessagesUpdated = 27;
    public static final int blockedUsersDidLoaded = 28;
    public static final int openedChatChanged = 29;
    public static final int hideEmojiKeyboard = 30;
    public static final int stopEncodingService = 31;
    public static final int didCreatedNewDeleteTask = 32;
    public static final int mainUserInfoChanged = 33;
    public static final int privacyRulesUpdated = 34;

    public static final int wallpapersDidLoaded = 171;
    public static final int closeOtherAppActivities = 702;
    public static final int didUpdatedConnectionState = 703;
    public static final int didReceiveSmsCode = 998;
    public static final int emojiDidLoaded = 999;
    public static final int appDidLogout = 1234;

    public static final int FileDidUpload = 10000;
    public static final int FileDidFailUpload = 10001;
    public static final int FileUploadProgressChanged = 10002;
    public static final int FileLoadProgressChanged = 10003;
    public static final int FileDidLoaded = 10004;
    public static final int FileDidFailedLoad = 10005;
    public static final int FilePreparingStarted = 10006;
    public static final int FileNewChunkAvailable = 10007;
    public static final int FilePreparingFailed = 10008;

    public final static int audioProgressDidChanged = 50001;
    public final static int audioDidReset = 50002;
    public final static int recordProgressChanged = 50003;
    public final static int recordStarted = 50004;
    public final static int recordStartError = 50005;
    public final static int recordStopped = 50006;
    public final static int screenshotTook = 50007;
    public final static int albumsDidLoaded = 50008;
    public final static int audioDidSent = 50009;
    public final static int audioDidStarted = 50010;

    final private HashMap<Integer, ArrayList<Object>> observers = new HashMap<Integer, ArrayList<Object>>();

    final private HashMap<Integer, Object> removeAfterBroadcast = new HashMap<Integer, Object>();
    final private HashMap<Integer, Object> addAfterBroadcast = new HashMap<Integer, Object>();

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
        public abstract void didReceivedNotification(int id, Object... args);
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
                observers.put(id, (objects = new ArrayList<Object>()));
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
