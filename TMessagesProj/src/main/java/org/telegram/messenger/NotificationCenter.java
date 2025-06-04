/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.os.SystemClock;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class NotificationCenter {

    private final static long EXPIRE_NOTIFICATIONS_TIME = 5017;
    private static int totalEvents = 1;

    public static final int didReceiveNewMessages = totalEvents++;
    public static final int updateInterfaces = totalEvents++;
    public static final int dialogsNeedReload = totalEvents++;
    public static final int closeChats = totalEvents++;
    public static final int messagesDeleted = totalEvents++;
    public static final int historyCleared = totalEvents++;
    public static final int messagesRead = totalEvents++;
    public static final int threadMessagesRead = totalEvents++;
    public static final int monoForumMessagesRead = totalEvents++;
    public static final int commentsRead = totalEvents++;
    public static final int changeRepliesCounter = totalEvents++;
    public static final int messagesDidLoad = totalEvents++;
    public static final int didLoadSponsoredMessages = totalEvents++;
    public static final int didLoadSendAsPeers = totalEvents++;
    public static final int updateDefaultSendAsPeer = totalEvents++;
    public static final int messagesDidLoadWithoutProcess = totalEvents++;
    public static final int loadingMessagesFailed = totalEvents++;
    public static final int messageReceivedByAck = totalEvents++;
    public static final int messageReceivedByServer = totalEvents++;
    public static final int messageReceivedByServer2 = totalEvents++;
    public static final int messageSendError = totalEvents++;
    public static final int forceImportContactsStart = totalEvents++;
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
    public static final int reloadDialogPhotos = totalEvents++;
    public static final int folderBecomeEmpty = totalEvents++;
    public static final int removeAllMessagesFromDialog = totalEvents++;
    public static final int notificationsSettingsUpdated = totalEvents++;
    public static final int blockedUsersDidLoad = totalEvents++;
    public static final int openedChatChanged = totalEvents++;
    public static final int didCreatedNewDeleteTask = totalEvents++;
    public static final int mainUserInfoChanged = totalEvents++;
    public static final int privacyRulesUpdated = totalEvents++;
    public static final int updateMessageMedia = totalEvents++;
    public static final int replaceMessagesObjects = totalEvents++;
    public static final int didSetPasscode = totalEvents++;
    public static final int passcodeDismissed = totalEvents++;
    public static final int twoStepPasswordChanged = totalEvents++;
    public static final int didSetOrRemoveTwoStepPassword = totalEvents++;
    public static final int didRemoveTwoStepPassword = totalEvents++;
    public static final int replyMessagesDidLoad = totalEvents++;
    public static final int didLoadPinnedMessages = totalEvents++;
    public static final int newSessionReceived = totalEvents++;
    public static final int didReceivedWebpages = totalEvents++;
    public static final int didReceivedWebpagesInUpdates = totalEvents++;
    public static final int stickersDidLoad = totalEvents++;
    public static final int diceStickersDidLoad = totalEvents++;
    public static final int featuredStickersDidLoad = totalEvents++;
    public static final int featuredEmojiDidLoad = totalEvents++;
    public static final int groupStickersDidLoad = totalEvents++;
    public static final int messagesReadContent = totalEvents++;
    public static final int botInfoDidLoad = totalEvents++;
    public static final int userInfoDidLoad = totalEvents++;
    public static final int pinnedInfoDidLoad = totalEvents++;
    public static final int botKeyboardDidLoad = totalEvents++;
    public static final int chatSearchResultsAvailable = totalEvents++;
    public static final int hashtagSearchUpdated = totalEvents++;
    public static final int chatSearchResultsLoading = totalEvents++;
    public static final int musicDidLoad = totalEvents++;
    public static final int moreMusicDidLoad = totalEvents++;
    public static final int needShowAlert = totalEvents++;
    public static final int needShowPlayServicesAlert = totalEvents++;
    public static final int didUpdateMessagesViews = totalEvents++;
    public static final int needReloadRecentDialogsSearch = totalEvents++;
    public static final int peerSettingsDidLoad = totalEvents++;
    public static final int wasUnableToFindCurrentLocation = totalEvents++;
    public static final int reloadHints = totalEvents++;
    public static final int reloadInlineHints = totalEvents++;
    public static final int reloadWebappsHints = totalEvents++;
    public static final int newDraftReceived = totalEvents++;
    public static final int recentDocumentsDidLoad = totalEvents++;
    public static final int needAddArchivedStickers = totalEvents++;
    public static final int archivedStickersCountDidLoad = totalEvents++;
    public static final int paymentFinished = totalEvents++;
    public static final int channelRightsUpdated = totalEvents++;
    public static final int openArticle = totalEvents++;
    public static final int articleClosed = totalEvents++;
    public static final int updateMentionsCount = totalEvents++;
    public static final int didUpdatePollResults = totalEvents++;
    public static final int chatOnlineCountDidLoad = totalEvents++;
    public static final int videoLoadingStateChanged = totalEvents++;
    public static final int newPeopleNearbyAvailable = totalEvents++;
    public static final int stopAllHeavyOperations = totalEvents++;
    public static final int startAllHeavyOperations = totalEvents++;
    public static final int stopSpoilers = totalEvents++;
    public static final int startSpoilers = totalEvents++;
    public static final int sendingMessagesChanged = totalEvents++;
    public static final int didUpdateReactions = totalEvents++;
    public static final int didUpdateExtendedMedia = totalEvents++;
    public static final int didVerifyMessagesStickers = totalEvents++;
    public static final int scheduledMessagesUpdated = totalEvents++;
    public static final int newSuggestionsAvailable = totalEvents++;
    public static final int didLoadChatInviter = totalEvents++;
    public static final int didLoadChatAdmins = totalEvents++;
    public static final int historyImportProgressChanged = totalEvents++;
    public static final int stickersImportProgressChanged = totalEvents++;
    public static final int stickersImportComplete = totalEvents++;
    public static final int dialogDeleted = totalEvents++;
    public static final int webViewResultSent = totalEvents++;
    public static final int voiceTranscriptionUpdate = totalEvents++;
    public static final int animatedEmojiDocumentLoaded = totalEvents++;
    public static final int recentEmojiStatusesUpdate = totalEvents++;
    public static final int updateSearchSettings = totalEvents++;
    public static final int updateTranscriptionLock = totalEvents++;
    public static final int businessMessagesUpdated = totalEvents++;
    public static final int quickRepliesUpdated = totalEvents++;
    public static final int quickRepliesDeleted = totalEvents++;
    public static final int bookmarkAdded = totalEvents++;
    public static final int starReactionAnonymousUpdate = totalEvents++;
    public static final int businessLinksUpdated = totalEvents++;
    public static final int businessLinkCreated = totalEvents++;
    public static final int needDeleteBusinessLink = totalEvents++;
    public static final int messageTranslated = totalEvents++;
    public static final int messageTranslating = totalEvents++;
    public static final int dialogIsTranslatable = totalEvents++;
    public static final int dialogTranslate = totalEvents++;
    public static final int didGenerateFingerprintKeyPair = totalEvents++;
    public static final int walletPendingTransactionsChanged = totalEvents++;
    public static final int walletSyncProgressChanged = totalEvents++;
    public static final int httpFileDidLoad = totalEvents++;
    public static final int httpFileDidFailedLoad = totalEvents++;
    public static final int didUpdateConnectionState = totalEvents++;

    public static final int fileUploaded = totalEvents++;
    public static final int fileUploadFailed = totalEvents++;
    public static final int fileUploadProgressChanged = totalEvents++;
    public static final int fileLoadProgressChanged = totalEvents++;
    public static final int fileLoaded = totalEvents++;
    public static final int fileLoadFailed = totalEvents++;
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
    public static final int recordPaused = totalEvents++;
    public static final int recordResumed = totalEvents++;
    public static final int screenshotTook = totalEvents++;
    public static final int albumsDidLoad = totalEvents++;
    public static final int audioDidSent = totalEvents++;
    public static final int audioRecordTooShort = totalEvents++;
    public static final int audioRouteChanged = totalEvents++;

    public static final int didStartedCall = totalEvents++;
    public static final int groupCallUpdated = totalEvents++;
    public static final int groupCallSpeakingUsersUpdated = totalEvents++;
    public static final int groupCallScreencastStateChanged = totalEvents++;
    public static final int activeGroupCallsUpdated = totalEvents++;
    public static final int applyGroupCallVisibleParticipants = totalEvents++;
    public static final int groupCallTypingsUpdated = totalEvents++;
    public static final int didEndCall = totalEvents++;
    public static final int closeInCallActivity = totalEvents++;
    public static final int groupCallVisibilityChanged = totalEvents++;

    public static final int appDidLogout = totalEvents++;

    public static final int configLoaded = totalEvents++;

    public static final int needDeleteDialog = totalEvents++;

    public static final int newEmojiSuggestionsAvailable = totalEvents++;

    public static final int themeUploadedToServer = totalEvents++;
    public static final int themeUploadError = totalEvents++;

    public static final int dialogFiltersUpdated = totalEvents++;
    public static final int filterSettingsUpdated = totalEvents++;
    public static final int suggestedFiltersLoaded = totalEvents++;

    public static final int updateBotMenuButton = totalEvents++;

    public static final int giftsToUserSent = totalEvents++;
    public static final int didStartedMultiGiftsSelector = totalEvents++;
    public static final int boostedChannelByUser = totalEvents++;
    public static final int boostByChannelCreated = totalEvents++;
    public static final int didUpdatePremiumGiftStickers = totalEvents++;
    public static final int didUpdatePremiumGiftFieldIcon = totalEvents++;
    public static final int storiesEnabledUpdate = totalEvents++;
    public static final int storiesBlocklistUpdate = totalEvents++;
    public static final int storiesLimitUpdate = totalEvents++;
    public static final int storiesSendAsUpdate = totalEvents++;
    public static final int unconfirmedAuthUpdate = totalEvents++;
    public static final int dialogPhotosUpdate = totalEvents++;
    public static final int channelRecommendationsLoaded = totalEvents++;
    public static final int savedMessagesDialogsUpdate = totalEvents++;
    public static final int savedReactionTagsUpdate = totalEvents++;
    public static final int userIsPremiumBlockedUpadted = totalEvents++;
    public static final int savedMessagesForwarded = totalEvents++;
    public static final int emojiKeywordsLoaded = totalEvents++;
    public static final int smsJobStatusUpdate = totalEvents++;
    public static final int storyQualityUpdate = totalEvents++;
    public static final int openBoostForUsersDialog = totalEvents++;
    public static final int groupRestrictionsUnlockedByBoosts = totalEvents++;
    public static final int chatWasBoostedByUser = totalEvents++;
    public static final int groupPackUpdated = totalEvents++;
    public static final int timezonesUpdated = totalEvents++;
    public static final int customStickerCreated = totalEvents++;
    public static final int premiumFloodWaitReceived = totalEvents++;
    public static final int availableEffectsUpdate = totalEvents++;
    public static final int starOptionsLoaded = totalEvents++;
    public static final int starGiftOptionsLoaded = totalEvents++;
    public static final int starGiveawayOptionsLoaded = totalEvents++;
    public static final int starBalanceUpdated = totalEvents++;
    public static final int starTransactionsLoaded = totalEvents++;
    public static final int starSubscriptionsLoaded = totalEvents++;
    public static final int factCheckLoaded = totalEvents++;
    public static final int botStarsUpdated = totalEvents++;
    public static final int botStarsTransactionsLoaded = totalEvents++;
    public static final int channelStarsUpdated = totalEvents++;
    public static final int webViewResolved = totalEvents++;
    public static final int updateAllMessages = totalEvents++;
    public static final int starGiftsLoaded = totalEvents++;
    public static final int starUserGiftsLoaded = totalEvents++;
    public static final int starGiftSoldOut = totalEvents++;
    public static final int updateStories = totalEvents++;
    public static final int botDownloadsUpdate = totalEvents++;
    public static final int channelSuggestedBotsUpdate = totalEvents++;
    public static final int channelConnectedBotsUpdate = totalEvents++;
    public static final int adminedChannelsLoaded = totalEvents++;
    public static final int messagesFeeUpdated = totalEvents++;
    public static final int commonChatsLoaded = totalEvents++;
    public static final int appConfigUpdated = totalEvents++;
    public static final int conferenceEmojiUpdated = totalEvents++;

    //global
    public static final int pushMessagesUpdated = totalEvents++;
    public static final int wallpapersDidLoad = totalEvents++;
    public static final int wallpapersNeedReload = totalEvents++;
    public static final int didReceiveSmsCode = totalEvents++;
    public static final int didReceiveCall = totalEvents++;
    public static final int emojiLoaded = totalEvents++;
    public static final int invalidateMotionBackground = totalEvents++;
    public static final int closeOtherAppActivities = totalEvents++;
    public static final int cameraInitied = totalEvents++;
    public static final int didReplacedPhotoInMemCache = totalEvents++;
    public static final int didSetNewTheme = totalEvents++;
    public static final int themeListUpdated = totalEvents++;
    public static final int didApplyNewTheme = totalEvents++;
    public static final int themeAccentListUpdated = totalEvents++;
    public static final int needCheckSystemBarColors = totalEvents++;
    public static final int needShareTheme = totalEvents++;
    public static final int needSetDayNightTheme = totalEvents++;
    public static final int goingToPreviewTheme = totalEvents++;
    public static final int locationPermissionGranted = totalEvents++;
    public static final int locationPermissionDenied = totalEvents++;
    public static final int reloadInterface = totalEvents++;
    public static final int suggestedLangpack = totalEvents++;
    public static final int didSetNewWallpapper = totalEvents++;
    public static final int proxySettingsChanged = totalEvents++;
    public static final int proxyCheckDone = totalEvents++;
    public static final int proxyChangedByRotation = totalEvents++;
    public static final int liveLocationsChanged = totalEvents++;
    public static final int newLocationAvailable = totalEvents++;
    public static final int liveLocationsCacheChanged = totalEvents++;
    public static final int notificationsCountUpdated = totalEvents++;
    public static final int playerDidStartPlaying = totalEvents++;
    public static final int closeSearchByActiveAction = totalEvents++;
    public static final int messagePlayingSpeedChanged = totalEvents++;
    public static final int screenStateChanged = totalEvents++;
    public static final int didClearDatabase = totalEvents++;
    public static final int voipServiceCreated = totalEvents++;
    public static final int webRtcMicAmplitudeEvent = totalEvents++;
    public static final int webRtcSpeakerAmplitudeEvent = totalEvents++;
    public static final int showBulletin = totalEvents++;
    public static final int appUpdateAvailable = totalEvents++;
    public static final int appUpdateLoading = totalEvents++;
    public static final int onDatabaseMigration = totalEvents++;
    public static final int onEmojiInteractionsReceived = totalEvents++;
    public static final int emojiPreviewThemesChanged = totalEvents++;
    public static final int reactionsDidLoad = totalEvents++;
    public static final int attachMenuBotsDidLoad = totalEvents++;
    public static final int chatAvailableReactionsUpdated = totalEvents++;
    public static final int dialogsUnreadReactionsCounterChanged = totalEvents++;
    public static final int onDatabaseOpened = totalEvents++;
    public static final int onDownloadingFilesChanged = totalEvents++;
    public static final int onActivityResultReceived = totalEvents++;
    public static final int onRequestPermissionResultReceived = totalEvents++;
    public static final int onUserRingtonesUpdated = totalEvents++;
    public static final int currentUserPremiumStatusChanged = totalEvents++;
    public static final int premiumPromoUpdated = totalEvents++;
    public static final int premiumStatusChangedGlobal = totalEvents++;
    public static final int currentUserShowLimitReachedDialog = totalEvents++;
    public static final int billingProductDetailsUpdated = totalEvents++;
    public static final int billingConfirmPurchaseError = totalEvents++;
    public static final int premiumStickersPreviewLoaded = totalEvents++;
    public static final int userEmojiStatusUpdated = totalEvents++;
    public static final int requestPermissions = totalEvents++;
    public static final int permissionsGranted = totalEvents++;
    public static final int activityPermissionsGranted = totalEvents++;
    public static final int topicsDidLoaded = totalEvents++;
    public static final int chatSwitchedForum = totalEvents++;
    public static final int didUpdateGlobalAutoDeleteTimer = totalEvents++;
    public static final int onDatabaseReset = totalEvents++;
    public static final int wallpaperSettedToUser = totalEvents++;
    public static final int storiesUpdated = totalEvents++;
    public static final int storiesListUpdated = totalEvents++;
    public static final int storiesDraftsUpdated = totalEvents++;
    public static final int chatlistFolderUpdate = totalEvents++;
    public static final int uploadStoryProgress = totalEvents++;
    public static final int uploadStoryEnd = totalEvents++;
    public static final int customTypefacesLoaded = totalEvents++;
    public static final int stealthModeChanged = totalEvents++;
    public static final int onReceivedChannelDifference = totalEvents++;
    public static final int storiesReadUpdated = totalEvents++;
    public static final int nearEarEvent = totalEvents++;

    public static boolean alreadyLogged;

    private final SparseArray<ArrayList<NotificationCenterDelegate>> observers = new SparseArray<>();
    private final SparseArray<ArrayList<NotificationCenterDelegate>> removeAfterBroadcast = new SparseArray<>();
    private final SparseArray<ArrayList<NotificationCenterDelegate>> addAfterBroadcast = new SparseArray<>();
    private final ArrayList<DelayedPost> delayedPosts = new ArrayList<>(10);
    private final ArrayList<Runnable> delayedRunnables  = new ArrayList<>(10);
    private final ArrayList<Runnable> delayedRunnablesTmp  = new ArrayList<>(10);
    private final ArrayList<DelayedPost> delayedPostsTmp = new ArrayList<>(10);
    private final ArrayList<PostponeNotificationCallback> postponeCallbackList = new ArrayList<>(10);

    private Runnable checkForExpiredNotifications;

    private int broadcasting = 0;

    private int animationInProgressCount;
    private int animationInProgressPointer = 1;

    HashSet<Integer> heavyOperationsCounter = new HashSet<>();

    private final SparseArray<AllowedNotifications> allowedNotifications = new SparseArray<>();

    public interface NotificationCenterDelegate {
        void didReceivedNotification(int id, int account, Object... args);
    }

    private static class DelayedPost {

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

    public int setAnimationInProgress(int oldIndex, int[] allowedNotifications) {
        return setAnimationInProgress(oldIndex, allowedNotifications, true);
    }

    public int setAnimationInProgress(int oldIndex, int[] allowedNotifications, boolean stopHeavyOperations) {
        onAnimationFinish(oldIndex);
        if (heavyOperationsCounter.isEmpty() && stopHeavyOperations) {
            getGlobalInstance().postNotificationName(stopAllHeavyOperations, 512);
        }

        animationInProgressCount++;
        animationInProgressPointer++;

        if (stopHeavyOperations) {
            heavyOperationsCounter.add(animationInProgressPointer);
        }
        AllowedNotifications notifications = new AllowedNotifications();
        notifications.allowedIds = allowedNotifications;
        this.allowedNotifications.put(animationInProgressPointer, notifications);
        if (checkForExpiredNotifications == null) {
            AndroidUtilities.runOnUIThread(checkForExpiredNotifications = this::checkForExpiredNotifications, EXPIRE_NOTIFICATIONS_TIME);
        }

        return animationInProgressPointer;
    }

    private void checkForExpiredNotifications() {
        checkForExpiredNotifications = null;
        if (this.allowedNotifications.size() == 0) {
            return;
        }
        long minTime = Long.MAX_VALUE;
        long currentTime = SystemClock.elapsedRealtime();
        ArrayList<Integer> expiredIndices = null;
        for (int i = 0; i < allowedNotifications.size(); i++) {
            AllowedNotifications allowedNotification = allowedNotifications.valueAt(i);
            if (currentTime - allowedNotification.time > 1000) {
                if (expiredIndices == null) {
                    expiredIndices = new ArrayList<>();
                }
                expiredIndices.add(allowedNotifications.keyAt(i));
            } else {
                minTime = Math.min(allowedNotification.time, minTime);
            }
        }
        if (expiredIndices != null) {
            for (int i = 0; i < expiredIndices.size(); i++) {
                onAnimationFinish(expiredIndices.get(i));
            }
        }
        if (minTime != Long.MAX_VALUE) {
            long time = EXPIRE_NOTIFICATIONS_TIME - (currentTime - minTime);
            AndroidUtilities.runOnUIThread(() -> checkForExpiredNotifications = this::checkForExpiredNotifications, Math.max(17, time));
        }
    }

    public void updateAllowedNotifications(int transitionAnimationIndex, int[] allowedNotifications) {
        AllowedNotifications notifications = this.allowedNotifications.get(transitionAnimationIndex);
        if (notifications != null) {
            notifications.allowedIds = allowedNotifications;
        }
    }

    public void onAnimationFinish(int index) {
        AllowedNotifications allowed = allowedNotifications.get(index);
        allowedNotifications.delete(index);
        if (allowed != null) {
            animationInProgressCount--;
            if (!heavyOperationsCounter.isEmpty()) {
                heavyOperationsCounter.remove(index);
                if (heavyOperationsCounter.isEmpty()) {
                    NotificationCenter.getGlobalInstance().postNotificationName(startAllHeavyOperations, 512);
                }
            }
            if (animationInProgressCount == 0) {
                runDelayedNotifications();
            }
        }
        if (checkForExpiredNotifications != null && allowedNotifications.size() == 0) {
            AndroidUtilities.cancelRunOnUIThread(checkForExpiredNotifications);
            checkForExpiredNotifications = null;
        }
    }

    public void runDelayedNotifications() {
        if (!delayedPosts.isEmpty()) {
            delayedPostsTmp.clear();
            delayedPostsTmp.addAll(delayedPosts);
            delayedPosts.clear();
            for (int a = 0; a < delayedPostsTmp.size(); a++) {
                DelayedPost delayedPost = delayedPostsTmp.get(a);
                postNotificationNameInternal(delayedPost.id, true, delayedPost.args);
            }
            delayedPostsTmp.clear();
        }

        if (!delayedRunnables.isEmpty()) {
            delayedRunnablesTmp.clear();
            delayedRunnablesTmp.addAll(delayedRunnables);
            delayedRunnables.clear();
            for (int a = 0; a < delayedRunnablesTmp.size(); a++) {
                AndroidUtilities.runOnUIThread(delayedRunnablesTmp.get(a));
            }
            delayedRunnablesTmp.clear();
        }
    }

    public boolean isAnimationInProgress() {
        return animationInProgressCount > 0;
    }

    public int getCurrentHeavyOperationFlags() {
        return currentHeavyOperationFlags;
    }

    public ArrayList<NotificationCenterDelegate> getObservers(int id) {
        return observers.get(id);
    }

    public void postNotificationNameOnUIThread(final int id, final Object... args) {
        AndroidUtilities.runOnUIThread(() -> postNotificationName(id, args));
    }

    public void postNotificationName(final int id, Object... args) {
        boolean allowDuringAnimation = id == startAllHeavyOperations || id == stopAllHeavyOperations || id == didReplacedPhotoInMemCache || id == closeChats || id == invalidateMotionBackground || id == needCheckSystemBarColors;
        ArrayList<Integer> expiredIndices = null;
        if (!allowDuringAnimation && allowedNotifications.size() > 0) {
            int size = allowedNotifications.size();
            int allowedCount = 0;
            long currentTime = SystemClock.elapsedRealtime();
            for (int i = 0; i < allowedNotifications.size(); i++) {
                AllowedNotifications allowedNotification = allowedNotifications.valueAt(i);
                if (currentTime - allowedNotification.time > EXPIRE_NOTIFICATIONS_TIME) {
                    if (expiredIndices == null) {
                        expiredIndices = new ArrayList<>();
                    }
                    expiredIndices.add(allowedNotifications.keyAt(i));
                }
                int[] allowed = allowedNotification.allowedIds;
                if (allowed != null) {
                    for (int a = 0; a < allowed.length; a++) {
                        if (allowed[a] == id) {
                            allowedCount++;
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
            allowDuringAnimation = size == allowedCount;
        }
        if (id == startAllHeavyOperations) {
            Integer flags = (Integer) args[0];
            currentHeavyOperationFlags &= ~flags;
        } else if (id == stopAllHeavyOperations) {
            Integer flags = (Integer) args[0];
            currentHeavyOperationFlags |= flags;
        }
        if (shouldDebounce(id, args) && BuildVars.DEBUG_VERSION) {
            postNotificationDebounced(id, args);
        } else {
            postNotificationNameInternal(id, allowDuringAnimation, args);
        }

        if (expiredIndices != null) {
            for (int i = 0; i < expiredIndices.size(); i++) {
                onAnimationFinish(expiredIndices.get(i));
            }
        }
    }

    SparseArray<Runnable> alreadyPostedRunnubles = new SparseArray<>();

    private void postNotificationDebounced(int id, Object[] args) {
        int hash = id + (Arrays.hashCode(args) << 16);
        if (alreadyPostedRunnubles.indexOfKey(hash) >= 0) {
            //skip
            return;
        }
        final Runnable runnable = () -> {
            postNotificationNameInternal(id, false, args);
            alreadyPostedRunnubles.remove(hash);
        };
        alreadyPostedRunnubles.put(hash, runnable);
        AndroidUtilities.runOnUIThread(runnable, 250);
    }

    private boolean shouldDebounce(int id, Object[] args) {
        return id == updateInterfaces;
    }

    @UiThread
    public void postNotificationNameInternal(int id, boolean allowDuringAnimation, Object... args) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("postNotificationName allowed only from MAIN thread");
            }
        }
        if (!allowDuringAnimation && isAnimationInProgress()) {
            DelayedPost delayedPost = new DelayedPost(id, args);
            delayedPosts.add(delayedPost);
            return;
        }
        if (!postponeCallbackList.isEmpty()) {
            for (int i = 0; i < postponeCallbackList.size(); i++) {
                if (postponeCallbackList.get(i).needPostpone(id, currentAccount, args)) {
                    delayedPosts.add(new DelayedPost(id, args));
                    return;
                }
            }
        }
        broadcasting++;
        ArrayList<NotificationCenterDelegate> objects = observers.get(id);
        if (objects != null && !objects.isEmpty()) {
            for (int a = 0; a < objects.size(); a++) {
                NotificationCenterDelegate obj = objects.get(a);
                obj.didReceivedNotification(id, currentAccount, args);
            }
        }
        broadcasting--;
        if (broadcasting == 0) {
            if (removeAfterBroadcast.size() != 0) {
                for (int a = 0; a < removeAfterBroadcast.size(); a++) {
                    int key = removeAfterBroadcast.keyAt(a);
                    ArrayList<NotificationCenterDelegate> arrayList = removeAfterBroadcast.get(key);
                    for (int b = 0; b < arrayList.size(); b++) {
                        removeObserver(arrayList.get(b), key);
                    }
                }
                removeAfterBroadcast.clear();
            }
            if (addAfterBroadcast.size() != 0) {
                for (int a = 0; a < addAfterBroadcast.size(); a++) {
                    int key = addAfterBroadcast.keyAt(a);
                    ArrayList<NotificationCenterDelegate> arrayList = addAfterBroadcast.get(key);
                    for (int b = 0; b < arrayList.size(); b++) {
                        addObserver(arrayList.get(b), key);
                    }
                }
                addAfterBroadcast.clear();
            }
        }
    }

    public void addObserver(NotificationCenterDelegate observer, int id) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("addObserver allowed only from MAIN thread");
            }
        }
        if (broadcasting != 0) {
            ArrayList<NotificationCenterDelegate> arrayList = addAfterBroadcast.get(id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                addAfterBroadcast.put(id, arrayList);
            }
            arrayList.add(observer);
            return;
        }
        ArrayList<NotificationCenterDelegate> objects = observers.get(id);
        if (objects == null) {
            observers.put(id, (objects = createArrayForId(id)));
        }
        if (objects.contains(observer)) {
            return;
        }
        objects.add(observer);
        if (BuildVars.DEBUG_VERSION && !alreadyLogged) {
            if (objects.size() > 1000) {
                alreadyLogged = true;
                FileLog.e(new RuntimeException("Total observers more than 1000, need check for memory leak. " + id), true);
            }
        }
    }

    private ArrayList<NotificationCenterDelegate> createArrayForId(int id) {
        // this notifications often add/remove
        // UniqArrayList for fast contains method check
        if (id == didReplacedPhotoInMemCache || id == stopAllHeavyOperations || id == startAllHeavyOperations) {
            return new UniqArrayList<>();
        }
        return new ArrayList<>();
    }

    public void removeObserver(NotificationCenterDelegate observer, int id) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("removeObserver allowed only from MAIN thread");
            }
        }
        if (broadcasting != 0) {
            ArrayList<NotificationCenterDelegate> arrayList = removeAfterBroadcast.get(id);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                removeAfterBroadcast.put(id, arrayList);
            }
            arrayList.add(observer);
            return;
        }
        ArrayList<NotificationCenterDelegate> objects = observers.get(id);
        if (objects != null) {
            objects.remove(observer);
        }
    }

    public boolean hasObservers(int id) {
        return observers.indexOfKey(id) >= 0;
    }

    public void addPostponeNotificationsCallback(PostponeNotificationCallback callback) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("PostponeNotificationsCallback allowed only from MAIN thread");
            }
        }
        if (!postponeCallbackList.contains(callback)) {
            postponeCallbackList.add(callback);
        }
    }

    public void removePostponeNotificationsCallback(PostponeNotificationCallback callback) {
        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() != ApplicationLoader.applicationHandler.getLooper().getThread()) {
                throw new RuntimeException("removePostponeNotificationsCallback allowed only from MAIN thread");
            }
        }
        if (postponeCallbackList.remove(callback)) {
            runDelayedNotifications();
        }
    }

    public interface PostponeNotificationCallback {
        boolean needPostpone(int id, int currentAccount, Object[] args);
    }

    public void doOnIdle(Runnable runnable) {
        if (isAnimationInProgress()) {
            delayedRunnables.add(runnable);
        } else {
            runnable.run();
        }
    }

    public void removeDelayed(Runnable runnable) {
        delayedRunnables.remove(runnable);
    }

    private static class AllowedNotifications {

        int[] allowedIds;
        final long time;

        private AllowedNotifications() {
            time = SystemClock.elapsedRealtime();
        }
    }

    public Runnable listenGlobal(View view, final int id, final Utilities.Callback<Object[]> callback) {
        if (view == null || callback == null) {
            return () -> {};
        }
        final NotificationCenterDelegate delegate = (_id, account, args) -> {
            if (_id == id) {
                callback.run(args);
            }
        };
        final View.OnAttachStateChangeListener viewListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                NotificationCenter.getGlobalInstance().addObserver(delegate, id);
            }
            @Override
            public void onViewDetachedFromWindow(View view) {
                NotificationCenter.getGlobalInstance().removeObserver(delegate, id);
            }
        };
        view.addOnAttachStateChangeListener(viewListener);

        return () -> {
            view.removeOnAttachStateChangeListener(viewListener);
            NotificationCenter.getGlobalInstance().removeObserver(delegate, id);
        };
    }

    public Runnable listen(View view, final int id, final Utilities.Callback<Object[]> callback) {
        if (view == null || callback == null) {
            return () -> {};
        }
        final NotificationCenterDelegate delegate = (_id, account, args) -> {
            if (_id == id) {
                callback.run(args);
            }
        };
        final View.OnAttachStateChangeListener viewListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                addObserver(delegate, id);
            }
            @Override
            public void onViewDetachedFromWindow(View view) {
                removeObserver(delegate, id);
            }
        };
        view.addOnAttachStateChangeListener(viewListener);

        return () -> {
            view.removeOnAttachStateChangeListener(viewListener);
            removeObserver(delegate, id);
        };
    }

    public static void listenEmojiLoading(View view) {
        getGlobalInstance().listenGlobal(view, NotificationCenter.emojiLoaded, args -> view.invalidate());
    }

    public void listenOnce(int id, Runnable callback) {
        final NotificationCenterDelegate[] observer = new NotificationCenterDelegate[1];
        observer[0] = (nid, account, args) -> {
            if (nid == id && observer[0] != null) {
                if (callback != null) {
                    callback.run();
                }
                removeObserver(observer[0], id);
                observer[0] = null;
            }
        };
        addObserver(observer[0], id);
    }

    private class UniqArrayList<T> extends ArrayList<T> {
        HashSet<T> set = new HashSet<>();

        @Override
        public boolean add(T t) {
            if (set.add(t)) {
                return super.add(t);
            }
            return false;
        }

        @Override
        public void add(int index, T element) {
            if (set.add(element)) {
                super.add(index, element);
            }
        }

        @Override
        public boolean addAll(@NonNull Collection<? extends T> c) {
            boolean modified = false;
            for (T t : c) {
                if (add(t)) {
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean addAll(int index, @NonNull Collection<? extends T> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T remove(int index) {
            T t = super.remove(index);
            if (t != null) {
                set.remove(t);
            }
            return t;
        }

        @Override
        public boolean remove(@Nullable Object o) {
            if (set.remove(o)) {
                return super.remove(o);
            }
            return false;
        }

        @Override
        public boolean removeAll(@NonNull Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(@Nullable Object o) {
            return set.contains(o);
        }

        @Override
        public void clear() {
            set.clear();
            super.clear();
        }
    }
}
