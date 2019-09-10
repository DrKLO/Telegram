/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import androidx.annotation.UiThread;
import android.util.SparseArray;

import java.util.ArrayList;

public class NotificationCenter {

    private static int totalEvents = 1;

    public static final int didReceiveNewMessages = totalEvents++;
    public static final int updateInterfaces = totalEvents++;
    public static final int dialogsNeedReload = totalEvents++;
    public static final int closeChats = totalEvents++;
    public static final int messagesDeleted = totalEvents++;
    public static final int historyCleared = totalEvents++;
    public static final int messagesRead = totalEvents++;
    public static final int messagesDidLoad = totalEvents++;
    public static final int messageReceivedByAck = totalEvents++;
    public static final int messageReceivedByServer = totalEvents++;
    public static final int messageSendError = totalEvents++;
    public static final int contactsDidLoad = totalEvents++;
    public static final int contactsImported = totalEvents++;
    public static final int hasNewContactsToImport = totalEvents++;
    public static final int chatDidCreated = totalEvents++;
    public static final int chatDidFailCreate = totalEvents++;
    public static final int chatInfoDidLoad = totalEvents++;
    public static final int chatInfoCantLoad = totalEvents++;
    public static final int mediaDidLoad = totalEvents++;
    public static final int mediaCountDidLoad = totalEvents++;
    public static final int mediaCountsDidLoad = totalEvents++;
    public static final int encryptedChatUpdated = totalEvents++;
    public static final int messagesReadEncrypted = totalEvents++;
    public static final int encryptedChatCreated = totalEvents++;
    public static final int dialogPhotosLoaded = totalEvents++;
    public static final int folderBecomeEmpty = totalEvents++;
    public static final int removeAllMessagesFromDialog = totalEvents++;
    public static final int notificationsSettingsUpdated = totalEvents++;
    public static final int blockedUsersDidLoad = totalEvents++;
    public static final int openedChatChanged = totalEvents++;
    public static final int didCreatedNewDeleteTask = totalEvents++;
    public static final int mainUserInfoChanged = totalEvents++;
    public static final int privacyRulesUpdated = totalEvents++;
    public static final int updateMessageMedia = totalEvents++;
    public static final int recentImagesDidLoad = totalEvents++;
    public static final int replaceMessagesObjects = totalEvents++;
    public static final int didSetPasscode = totalEvents++;
    public static final int didSetTwoStepPassword = totalEvents++;
    public static final int didRemoveTwoStepPassword = totalEvents++;
    public static final int replyMessagesDidLoad = totalEvents++;
    public static final int pinnedMessageDidLoad = totalEvents++;
    public static final int newSessionReceived = totalEvents++;
    public static final int didReceivedWebpages = totalEvents++;
    public static final int didReceivedWebpagesInUpdates = totalEvents++;
    public static final int stickersDidLoad = totalEvents++;
    public static final int featuredStickersDidLoad = totalEvents++;
    public static final int groupStickersDidLoad = totalEvents++;
    public static final int messagesReadContent = totalEvents++;
    public static final int botInfoDidLoad = totalEvents++;
    public static final int userInfoDidLoad = totalEvents++;
    public static final int botKeyboardDidLoad = totalEvents++;
    public static final int chatSearchResultsAvailable = totalEvents++;
    public static final int chatSearchResultsLoading = totalEvents++;
    public static final int musicDidLoad = totalEvents++;
    public static final int needShowAlert = totalEvents++;
    public static final int needShowPlayServicesAlert = totalEvents++;
    public static final int didUpdatedMessagesViews = totalEvents++;
    public static final int needReloadRecentDialogsSearch = totalEvents++;
    public static final int peerSettingsDidLoad = totalEvents++;
    public static final int wasUnableToFindCurrentLocation = totalEvents++;
    public static final int reloadHints = totalEvents++;
    public static final int reloadInlineHints = totalEvents++;
    public static final int newDraftReceived = totalEvents++;
    public static final int recentDocumentsDidLoad = totalEvents++;
    public static final int needReloadArchivedStickers = totalEvents++;
    public static final int archivedStickersCountDidLoad = totalEvents++;
    public static final int paymentFinished = totalEvents++;
    public static final int channelRightsUpdated = totalEvents++;
    public static final int openArticle = totalEvents++;
    public static final int updateMentionsCount = totalEvents++;
    public static final int didUpdatePollResults = totalEvents++;
    public static final int chatOnlineCountDidLoad = totalEvents++;
    public static final int videoLoadingStateChanged = totalEvents++;
    public static final int newPeopleNearbyAvailable = totalEvents++;
    public static final int stopAllHeavyOperations = totalEvents++;
    public static final int startAllHeavyOperations = totalEvents++;
    public static final int sendingMessagesChanged = totalEvents++;
    public static final int didUpdateReactions = totalEvents++;
    public static final int scheduledMessagesUpdated = totalEvents++;

    public static final int httpFileDidLoad = totalEvents++;
    public static final int httpFileDidFailedLoad = totalEvents++;

    public static final int didUpdateConnectionState = totalEvents++;

    public static final int FileDidUpload = totalEvents++;
    public static final int FileDidFailUpload = totalEvents++;
    public static final int FileUploadProgressChanged = totalEvents++;
    public static final int FileLoadProgressChanged = totalEvents++;
    public static final int fileDidLoad = totalEvents++;
    public static final int fileDidFailToLoad = totalEvents++;
    public static final int filePreparingStarted = totalEvents++;
    public static final int fileNewChunkAvailable = totalEvents++;
    public static final int filePreparingFailed = totalEvents++;

    public static final int dialogsUnreadCounterChanged = totalEvents++;

    public static final int messagePlayingProgressDidChanged = totalEvents++;
    public static final int messagePlayingDidReset = totalEvents++;
    public static final int messagePlayingPlayStateChanged = totalEvents++;
    public static final int messagePlayingDidStart = totalEvents++;
    public static final int messagePlayingDidSeek = totalEvents++;
    public static final int messagePlayingGoingToStop = totalEvents++;
    public static final int recordProgressChanged = totalEvents++;
    public static final int recordStarted = totalEvents++;
    public static final int recordStartError = totalEvents++;
    public static final int recordStopped = totalEvents++;
    public static final int screenshotTook = totalEvents++;
    public static final int albumsDidLoad = totalEvents++;
    public static final int audioDidSent = totalEvents++;
    public static final int audioRecordTooShort = totalEvents++;
    public static final int audioRouteChanged = totalEvents++;

    public static final int didStartedCall = totalEvents++;
    public static final int didEndedCall = totalEvents++;
    public static final int closeInCallActivity = totalEvents++;

    public static final int appDidLogout = totalEvents++;

    public static final int configLoaded = totalEvents++;

    public static final int needDeleteDialog = totalEvents++;

    public static final int newEmojiSuggestionsAvailable = totalEvents++;

    public static final int themeUploadedToServer = totalEvents++;
    public static final int themeUploadError = totalEvents++;

    //global
    public static final int pushMessagesUpdated = totalEvents++;
    public static final int stopEncodingService = totalEvents++;
    public static final int wallpapersDidLoad = totalEvents++;
    public static final int wallpapersNeedReload = totalEvents++;
    public static final int didReceiveSmsCode = totalEvents++;
    public static final int didReceiveCall = totalEvents++;
    public static final int emojiDidLoad = totalEvents++;
    public static final int closeOtherAppActivities = totalEvents++;
    public static final int cameraInitied = totalEvents++;
    public static final int didReplacedPhotoInMemCache = totalEvents++;
    public static final int didSetNewTheme = totalEvents++;
    public static final int themeListUpdated = totalEvents++;
    public static final int needSetDayNightTheme = totalEvents++;
    public static final int goingToPreviewTheme = totalEvents++;
    public static final int locationPermissionGranted = totalEvents++;
    public static final int reloadInterface = totalEvents++;
    public static final int suggestedLangpack = totalEvents++;
    public static final int didSetNewWallpapper = totalEvents++;
    public static final int proxySettingsChanged = totalEvents++;
    public static final int proxyCheckDone = totalEvents++;
    public static final int liveLocationsChanged = totalEvents++;
    public static final int newLocationAvailable = totalEvents++;
    public static final int liveLocationsCacheChanged = totalEvents++;
    public static final int notificationsCountUpdated = totalEvents++;
    public static final int playerDidStartPlaying = totalEvents++;
    public static final int closeSearchByActiveAction = totalEvents++;

    private SparseArray<ArrayList<Object>> observers = new SparseArray<>();
    private SparseArray<ArrayList<Object>> removeAfterBroadcast = new SparseArray<>();
    private SparseArray<ArrayList<Object>> addAfterBroadcast = new SparseArray<>();
    private ArrayList<DelayedPost> delayedPosts = new ArrayList<>(10);

    private int broadcasting = 0;
    private boolean animationInProgress;

    private int[] allowedNotifications;

    public interface NotificationCenterDelegate {
        void didReceivedNotification(int id, int account, Object... args);
    }

    private class DelayedPost {

        private DelayedPost(int id, Object[] args) {
            this.id = id;
            this.args = args;
        }

        private int id;
        private Object[] args;
    }

    private int currentAccount;
    private int currentHeavyOperationFlags;
    private static volatile NotificationCenter[] Instance = new NotificationCenter[UserConfig.MAX_ACCOUNT_COUNT];
    private static volatile NotificationCenter globalInstance;

    @UiThread
    public static NotificationCenter getInstance(int num) {
        NotificationCenter localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (NotificationCenter.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new NotificationCenter(num);
                }
            }
        }
        return localInstance;
    }

    @UiThread
    public static NotificationCenter getGlobalInstance() {
        NotificationCenter localInstance = globalInstance;
        if (localInstance == null) {
            synchronized (NotificationCenter.class) {
                localInstance = globalInstance;
                if (localInstance == null) {
                    globalInstance = localInstance = new NotificationCenter(-1);
                }
            }
        }
        return localInstance;
    }

    public NotificationCenter(int account) {
        currentAccount = account;
    }

    public void setAllowedNotificationsDutingAnimation(int[] notifications) {
        allowedNotifications = notifications;
    }

    public void setAnimationInProgress(boolean value) {
        if (value) {
            NotificationCenter.getGlobalInstance().postNotificationName(stopAllHeavyOperations, 512);
        } else {
            NotificationCenter.getGlobalInstance().postNotificationName(startAllHeavyOperations, 512);
        }
        animationInProgress = value;
        if (!animationInProgress && !delayedPosts.isEmpty()) {
            for (int a = 0; a < delayedPosts.size(); a++) {
                DelayedPost delayedPost = delayedPosts.get(a);
                postNotificationNameInternal(delayedPost.id, true, delayedPost.args);
            }
            delayedPosts.clear();
        }
    }

    public boolean isAnimationInProgress() {
        return animationInProgress;
    }

    public int getCurrentHeavyOperationFlags() {
        return currentHeavyOperationFlags;
    }

    public void postNotificationName(int id, Object... args) {
        boolean allowDuringAnimation = id == startAllHeavyOperations || id == stopAllHeavyOperations;
        if (!allowDuringAnimation && allowedNotifications != null) {
            for (int a = 0; a < allowedNotifications.length; a++) {
                if (allowedNotifications[a] == id) {
                    allowDuringAnimation = true;
                    break;
                }
            }
        }
        if (id == startAllHeavyOperations) {
            Integer flags = (Integer) args[0];
            currentHeavyOperationFlags &=~ flags;
        } else if (id == stopAllHeavyOperations) {
            Integer flags = (Integer) args[0];
            currentHeavyOperationFlags |= flags;
        }
        postNotificationNameInternal(id, allowDuringAnimation, args);
    }

    @UiThread
    public void postNotificationNameInternal(int id, boolean allowDuringAnimation, Object... args) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("postNotificationName allowed only from MAIN thread");
            }
        }
        if (!allowDuringAnimation && animationInProgress) {
            DelayedPost delayedPost = new DelayedPost(id, args);
            delayedPosts.add(delayedPost);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("delay post notification " + id + " with args count = " + args.length);
            }
            return;
        }
        broadcasting++;
        ArrayList<Object> objects = observers.get(id);
        if (objects != null && !objects.isEmpty()) {
            for (int a = 0; a < objects.size(); a++) {
                Object obj = objects.get(a);
                ((NotificationCenterDelegate) obj).didReceivedNotification(id, currentAccount, args);
            }
        }
        broadcasting--;
        if (broadcasting == 0) {
            if (removeAfterBroadcast.size() != 0) {
                for (int a = 0; a < removeAfterBroadcast.size(); a++) {
                    int key = removeAfterBroadcast.keyAt(a);
                    ArrayList<Object> arrayList = removeAfterBroadcast.get(key);
                    for (int b = 0; b < arrayList.size(); b++) {
                        removeObserver(arrayList.get(b), key);
                    }
                }
                removeAfterBroadcast.clear();
            }
            if (addAfterBroadcast.size() != 0) {
                for (int a = 0; a < addAfterBroadcast.size(); a++) {
                    int key = addAfterBroadcast.keyAt(a);
                    ArrayList<Object> arrayList = addAfterBroadcast.get(key);
                    for (int b = 0; b < arrayList.size(); b++) {
                        addObserver(arrayList.get(b), key);
                    }
                }
                addAfterBroadcast.clear();
            }
        }
    }

    public void addObserver(Object observer, int id) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("addObserver allowed only from MAIN thread");
            }
        }
        if (broadcasting != 0) {
            ArrayList<Object> arrayList = addAfterBroadcast.get(id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                addAfterBroadcast.put(id, arrayList);
            }
            arrayList.add(observer);
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

    public void removeObserver(Object observer, int id) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("removeObserver allowed only from MAIN thread");
            }
        }
        if (broadcasting != 0) {
            ArrayList<Object> arrayList = removeAfterBroadcast.get(id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                removeAfterBroadcast.put(id, arrayList);
            }
            arrayList.add(observer);
            return;
        }
        ArrayList<Object> objects = observers.get(id);
        if (objects != null) {
            objects.remove(observer);
        }
    }

    public boolean hasObservers(int id) {
        return observers.indexOfKey(id) >= 0;
    }
}
