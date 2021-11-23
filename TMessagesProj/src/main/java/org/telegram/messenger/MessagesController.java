/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import androidx.collection.LongSparseArray;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.messenger.support.LongSparseLongArray;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.JoinCallAlert;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.EditWidgetActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Components.SwipeGestureSettingsView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class MessagesController extends BaseController implements NotificationCenter.NotificationCenterDelegate {

    private ConcurrentHashMap<Long, TLRPC.Chat> chats = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.EncryptedChat> encryptedChats = new ConcurrentHashMap<>(10, 1.0f, 2);
    private ConcurrentHashMap<Long, TLRPC.User> users = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<String, TLObject> objectsByUsernames = new ConcurrentHashMap<>(100, 1.0f, 2);

    private HashMap<Long, TLRPC.Chat> activeVoiceChatsMap = new HashMap<>();

    private ArrayList<Long> joiningToChannels = new ArrayList<>();

    private LongSparseArray<TLRPC.TL_chatInviteExported> exportedChats = new LongSparseArray<>();

    public ArrayList<TLRPC.RecentMeUrl> hintDialogs = new ArrayList<>();
    public SparseArray<ArrayList<TLRPC.Dialog>> dialogsByFolder = new SparseArray<>();
    protected ArrayList<TLRPC.Dialog> allDialogs = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsForward = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsServerOnly = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsCanAddUsers = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsChannelsOnly = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsUsersOnly = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsForBlock = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsGroupsOnly = new ArrayList<>();
    public DialogFilter[] selectedDialogFilter = new DialogFilter[2];
    private int dialogsLoadedTillDate = Integer.MAX_VALUE;
    public int unreadUnmutedDialogs;
    public ConcurrentHashMap<Long, Integer> dialogs_read_inbox_max = new ConcurrentHashMap<>(100, 1.0f, 2);
    public ConcurrentHashMap<Long, Integer> dialogs_read_outbox_max = new ConcurrentHashMap<>(100, 1.0f, 2);
    public LongSparseArray<TLRPC.Dialog> dialogs_dict = new LongSparseArray<>();
    public LongSparseArray<MessageObject> dialogMessage = new LongSparseArray<>();
    public LongSparseArray<MessageObject> dialogMessagesByRandomIds = new LongSparseArray<>();
    public LongSparseIntArray deletedHistory = new LongSparseIntArray();
    public SparseArray<MessageObject> dialogMessagesByIds = new SparseArray<>();
    public ConcurrentHashMap<Long, ConcurrentHashMap<Integer, ArrayList<PrintingUser>>> printingUsers = new ConcurrentHashMap<>(20, 1.0f, 2);
    public LongSparseArray<SparseArray<CharSequence>> printingStrings = new LongSparseArray<>();
    public LongSparseArray<SparseArray<Integer>> printingStringsTypes = new LongSparseArray<>();
    public LongSparseArray<SparseArray<Boolean>>[] sendingTypings = new LongSparseArray[12];
    public ConcurrentHashMap<Long, Integer> onlinePrivacy = new ConcurrentHashMap<>(20, 1.0f, 2);
    private int lastPrintingStringCount;

    private boolean dialogsInTransaction;

    private LongSparseArray<Boolean> loadingPeerSettings = new LongSparseArray<>();

    private ArrayList<Long> createdDialogIds = new ArrayList<>();
    private ArrayList<Long> createdScheduledDialogIds = new ArrayList<>();
    private ArrayList<Long> createdDialogMainThreadIds = new ArrayList<>();
    private ArrayList<Long> visibleDialogMainThreadIds = new ArrayList<>();
    private ArrayList<Long> visibleScheduledDialogMainThreadIds = new ArrayList<>();

    private LongSparseIntArray shortPollChannels = new LongSparseIntArray();
    private LongSparseArray<ArrayList<Integer>> needShortPollChannels = new LongSparseArray<>();
    private LongSparseIntArray shortPollOnlines = new LongSparseIntArray();
    private LongSparseArray<ArrayList<Integer>> needShortPollOnlines = new LongSparseArray<>();

    private LongSparseArray<TLRPC.Dialog> deletingDialogs = new LongSparseArray<>();
    private LongSparseArray<TLRPC.Dialog> clearingHistoryDialogs = new LongSparseArray<>();

    public boolean loadingBlockedPeers = false;
    public LongSparseIntArray blockePeers = new LongSparseIntArray();
    public int totalBlockedCount = -1;
    public boolean blockedEndReached;

    private LongSparseArray<ArrayList<Integer>> channelViewsToSend = new LongSparseArray<>();
    private LongSparseArray<SparseArray<MessageObject>> pollsToCheck = new LongSparseArray<>();
    private int pollsToCheckSize;
    private long lastViewsCheckTime;

    public ArrayList<DialogFilter> dialogFilters = new ArrayList<>();
    public SparseArray<DialogFilter> dialogFiltersById = new SparseArray<>();
    private boolean loadingSuggestedFilters;
    private boolean loadingRemoteFilters;
    public boolean dialogFiltersLoaded;
    public ArrayList<TLRPC.TL_dialogFilterSuggested> suggestedFilters = new ArrayList<>();

    private LongSparseArray<ArrayList<TLRPC.Updates>> updatesQueueChannels = new LongSparseArray<>();
    private LongSparseLongArray updatesStartWaitTimeChannels = new LongSparseLongArray();
    private LongSparseIntArray channelsPts = new LongSparseIntArray();
    private LongSparseArray<Boolean> gettingDifferenceChannels = new LongSparseArray<>();
    private LongSparseArray<Boolean> gettingChatInviters = new LongSparseArray<>();

    private LongSparseArray<Boolean> gettingUnknownChannels = new LongSparseArray<>();
    private LongSparseArray<Boolean> gettingUnknownDialogs = new LongSparseArray<>();
    private LongSparseArray<Boolean> checkingLastMessagesDialogs = new LongSparseArray<>();

    private ArrayList<TLRPC.Updates> updatesQueueSeq = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueuePts = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueueQts = new ArrayList<>();
    private long updatesStartWaitTimeSeq;
    private long updatesStartWaitTimePts;
    private long updatesStartWaitTimeQts;
    private LongSparseArray<TLRPC.UserFull> fullUsers = new LongSparseArray<>();
    private LongSparseArray<TLRPC.ChatFull> fullChats = new LongSparseArray<>();
    private LongSparseArray<ChatObject.Call> groupCalls = new LongSparseArray<>();
    private LongSparseArray<ChatObject.Call> groupCallsByChatId = new LongSparseArray<>();
    private ArrayList<Long> loadingFullUsers = new ArrayList<>();
    private ArrayList<Long> loadedFullUsers = new ArrayList<>();
    private ArrayList<Long> loadingFullChats = new ArrayList<>();
    private ArrayList<Long> loadingGroupCalls = new ArrayList<>();
    private ArrayList<Long> loadingFullParticipants = new ArrayList<>();
    private ArrayList<Long> loadedFullParticipants = new ArrayList<>();
    private ArrayList<Long> loadedFullChats = new ArrayList<>();
    private LongSparseArray<LongSparseArray<TLRPC.ChannelParticipant>> channelAdmins = new LongSparseArray<>();
    private LongSparseIntArray loadingChannelAdmins = new LongSparseIntArray();

    private SparseIntArray migratedChats = new SparseIntArray();

    private LongSparseArray<SponsoredMessagesInfo> sponsoredMessages = new LongSparseArray<>();

    private HashMap<String, ArrayList<MessageObject>> reloadingWebpages = new HashMap<>();
    private LongSparseArray<ArrayList<MessageObject>> reloadingWebpagesPending = new LongSparseArray<>();
    private HashMap<String, ArrayList<MessageObject>> reloadingScheduledWebpages = new HashMap<>();
    private LongSparseArray<ArrayList<MessageObject>> reloadingScheduledWebpagesPending = new LongSparseArray<>();

    private LongSparseArray<Long> lastScheduledServerQueryTime = new LongSparseArray<>();
    private LongSparseArray<Long> lastServerQueryTime = new LongSparseArray<>();

    private LongSparseArray<ArrayList<Integer>> reloadingMessages = new LongSparseArray<>();

    private ArrayList<ReadTask> readTasks = new ArrayList<>();
    private LongSparseArray<ReadTask> readTasksMap = new LongSparseArray<>();
    private ArrayList<ReadTask> repliesReadTasks = new ArrayList<>();
    private HashMap<String, ReadTask> threadsReadTasksMap = new HashMap<>();

    private boolean gettingNewDeleteTask;
    private int currentDeletingTaskTime;
    private LongSparseArray<ArrayList<Integer>> currentDeletingTaskMids;
    private LongSparseArray<ArrayList<Integer>> currentDeletingTaskMediaMids;
    private Runnable currentDeleteTaskRunnable;

    public boolean dialogsLoaded;
    private SparseIntArray nextDialogsCacheOffset = new SparseIntArray();
    private SparseBooleanArray loadingDialogs = new SparseBooleanArray();
    private SparseBooleanArray dialogsEndReached = new SparseBooleanArray();
    private SparseBooleanArray serverDialogsEndReached = new SparseBooleanArray();

    private boolean loadingUnreadDialogs;
    private boolean migratingDialogs;
    public boolean gettingDifference;
    private boolean getDifferenceFirstSync = true;
    public boolean updatingState;
    public boolean firstGettingTask;
    public boolean registeringForPush;
    private long lastPushRegisterSendTime;
    private boolean resetingDialogs;
    private TLRPC.TL_messages_peerDialogs resetDialogsPinned;
    private TLRPC.messages_Dialogs resetDialogsAll;
    private SparseIntArray loadingPinnedDialogs = new SparseIntArray();

    public ArrayList<FaqSearchResult> faqSearchArray = new ArrayList<>();
    public TLRPC.WebPage faqWebPage;

    private int loadingNotificationSettings;
    private boolean loadingNotificationSignUpSettings;

    private int nextPromoInfoCheckTime;
    private boolean checkingPromoInfo;
    private int checkingPromoInfoRequestId;
    private int lastCheckPromoId;
    private TLRPC.Dialog promoDialog;
    private boolean isLeftPromoChannel;
    private long promoDialogId;
    public int promoDialogType;
    public String promoPsaMessage;
    public String promoPsaType;
    private String proxyDialogAddress;

    private boolean checkingTosUpdate;
    private int nextTosCheckTime;

    public int secretWebpagePreview;
    public boolean suggestContacts = true;

    private volatile static long lastThemeCheckTime;
    private Runnable themeCheckRunnable = Theme::checkAutoNightThemeConditions;

    private volatile static long lastPasswordCheckTime;
    private Runnable passwordCheckRunnable = () -> getUserConfig().checkSavedPassword();

    private long lastStatusUpdateTime;
    private int statusRequest;
    private int statusSettingState;
    private boolean offlineSent;
    private String uploadingAvatar;

    private HashMap<String, Object> uploadingThemes = new HashMap<>();

    private String uploadingWallpaper;
    private Theme.OverrideWallpaperInfo uploadingWallpaperInfo;

    private boolean loadingAppConfig;

    public boolean enableJoined;
    public String linkPrefix;
    public int maxGroupCount;
    public int maxBroadcastCount = 100;
    public int maxMegagroupCount;
    public int minGroupConvertSize = 200;
    public int maxEditTime;
    public int ratingDecay;
    public int revokeTimeLimit;
    public int revokeTimePmLimit;
    public boolean canRevokePmInbox;
    public int maxRecentStickersCount;
    public int maxFaveStickersCount;
    public int maxRecentGifsCount;
    public int callReceiveTimeout;
    public int callRingTimeout;
    public int callConnectTimeout;
    public int callPacketTimeout;
    public int maxPinnedDialogsCount;
    public int maxFolderPinnedDialogsCount;
    public int mapProvider;
    public int availableMapProviders;
    public int updateCheckDelay;
    public String mapKey;
    public int maxMessageLength;
    public int maxCaptionLength;
    public int roundVideoSize;
    public int roundVideoBitrate;
    public int roundAudioBitrate;
    public boolean blockedCountry;
    public boolean preloadFeaturedStickers;
    public String youtubePipType;
    public boolean keepAliveService;
    public boolean backgroundConnection;
    public float animatedEmojisZoom;
    public boolean filtersEnabled;
    public boolean showFiltersTooltip;
    public String venueSearchBot;
    public String gifSearchBot;
    public String imageSearchBot;
    public String dcDomainName;
    public int webFileDatacenterId;
    public String suggestedLangCode;
    public boolean qrLoginCamera;
    public boolean saveGifsWithStickers;
    private String installReferer;
    public Set<String> pendingSuggestions;
    public Set<String> exportUri;
    public Set<String> exportGroupUri;
    public Set<String> exportPrivateUri;
    public boolean autoarchiveAvailable;
    public int groipCallVideoMaxParticipants;
    public boolean suggestStickersApiOnly;
    public ArrayList<String> gifSearchEmojies = new ArrayList<>();
    public HashSet<String> diceEmojies;
    public Set<String> autologinDomains;
    public Set<String> authDomains;
    public String autologinToken;
    public HashMap<String, DiceFrameSuccess> diceSuccess = new HashMap<>();
    public HashMap<String, EmojiSound> emojiSounds = new HashMap<>();
    public HashMap<Long, ArrayList<TLRPC.TL_sendMessageEmojiInteraction>> emojiInteractions = new HashMap<>();

    private SharedPreferences notificationsPreferences;
    private SharedPreferences mainPreferences;
    private SharedPreferences emojiPreferences;

    public volatile boolean ignoreSetOnline;

    private class SponsoredMessagesInfo {
        private ArrayList<MessageObject> messages;
        private long loadTime;
        private boolean loading;
    }

    public static class FaqSearchResult {

        public String title;
        public String[] path;
        public String url;
        public int num;

        public FaqSearchResult(String t, String[] p, String u) {
            title = t;
            path = p;
            url = u;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof FaqSearchResult)) {
                return false;
            }
            FaqSearchResult result = (FaqSearchResult) obj;
            return title.equals(result.title);
        }

        @Override
        public String toString() {
            SerializedData data = new SerializedData();
            data.writeInt32(num);
            data.writeInt32(0);
            data.writeString(title);
            data.writeInt32(path != null ? path.length : 0);
            if (path != null) {
                for (int a = 0; a < path.length; a++) {
                    data.writeString(path[a]);
                }
            }
            data.writeString(url);
            return Utilities.bytesToHex(data.toByteArray());
        }
    }

    public static class EmojiSound {
        public long id;
        public long accessHash;
        public byte[] fileReference;

        public EmojiSound(long i, long ah, String fr) {
            id = i;
            accessHash = ah;
            fileReference = Base64.decode(fr, Base64.URL_SAFE);
        }

        public EmojiSound(long i, long ah, byte[] fr) {
            id = i;
            accessHash = ah;
            fileReference = fr;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EmojiSound)) {
                return false;
            }
            EmojiSound emojiSound = (EmojiSound) obj;
            return id == emojiSound.id && accessHash == emojiSound.accessHash && Arrays.equals(fileReference, emojiSound.fileReference);
        }
    }

    public void clearQueryTime() {
        lastServerQueryTime.clear();
        lastScheduledServerQueryTime.clear();
    }

    public static class DiceFrameSuccess {
        public int frame;
        public int num;

        public DiceFrameSuccess(int f, int n) {
            frame = f;
            num = n;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DiceFrameSuccess)) {
                return false;
            }
            DiceFrameSuccess frameSuccess = (DiceFrameSuccess) obj;
            return frame == frameSuccess.frame && num == frameSuccess.num;
        }
    }

    private static class UserActionUpdatesSeq extends TLRPC.Updates {

    }

    private static class UserActionUpdatesPts extends TLRPC.Updates {

    }

    public static int UPDATE_MASK_NAME = 1;
    public static int UPDATE_MASK_AVATAR = 2;
    public static int UPDATE_MASK_STATUS = 4;
    public static int UPDATE_MASK_CHAT_AVATAR = 8;
    public static int UPDATE_MASK_CHAT_NAME = 16;
    public static int UPDATE_MASK_CHAT_MEMBERS = 32;
    public static int UPDATE_MASK_USER_PRINT = 64;
    public static int UPDATE_MASK_USER_PHONE = 128;
    public static int UPDATE_MASK_READ_DIALOG_MESSAGE = 256;
    public static int UPDATE_MASK_SELECT_DIALOG = 512;
    public static int UPDATE_MASK_PHONE = 1024;
    public static int UPDATE_MASK_NEW_MESSAGE = 2048;
    public static int UPDATE_MASK_SEND_STATE = 4096;
    public static int UPDATE_MASK_CHAT = 8192;
    //public static int UPDATE_MASK_CHAT_ADMINS = 16384;
    public static int UPDATE_MASK_MESSAGE_TEXT = 32768;
    public static int UPDATE_MASK_CHECK = 65536;
    public static int UPDATE_MASK_REORDER = 131072;
    public static int UPDATE_MASK_EMOJI_INTERACTIONS = 262144;
    public static int UPDATE_MASK_ALL = UPDATE_MASK_AVATAR | UPDATE_MASK_STATUS | UPDATE_MASK_NAME | UPDATE_MASK_CHAT_AVATAR | UPDATE_MASK_CHAT_NAME | UPDATE_MASK_CHAT_MEMBERS | UPDATE_MASK_USER_PRINT | UPDATE_MASK_USER_PHONE | UPDATE_MASK_READ_DIALOG_MESSAGE | UPDATE_MASK_PHONE;

    public static int PROMO_TYPE_PROXY = 0;
    public static int PROMO_TYPE_PSA = 1;
    public static int PROMO_TYPE_OTHER = 2;

    private static class ReadTask {
        public long dialogId;
        public long replyId;
        public int maxId;
        public int maxDate;
        public long sendRequestTime;
    }

    public static class PrintingUser {
        public long lastTime;
        public long userId;
        public TLRPC.SendMessageAction action;
    }

    public static int DIALOG_FILTER_FLAG_CONTACTS           = 0x00000001;
    public static int DIALOG_FILTER_FLAG_NON_CONTACTS       = 0x00000002;
    public static int DIALOG_FILTER_FLAG_GROUPS             = 0x00000004;
    public static int DIALOG_FILTER_FLAG_CHANNELS           = 0x00000008;
    public static int DIALOG_FILTER_FLAG_BOTS               = 0x00000010;
    public static int DIALOG_FILTER_FLAG_EXCLUDE_MUTED      = 0x00000020;
    public static int DIALOG_FILTER_FLAG_EXCLUDE_READ       = 0x00000040;
    public static int DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED   = 0x00000080;
    public static int DIALOG_FILTER_FLAG_ONLY_ARCHIVED      = 0x00000100;
    public static int DIALOG_FILTER_FLAG_ALL_CHATS          = DIALOG_FILTER_FLAG_CONTACTS | DIALOG_FILTER_FLAG_NON_CONTACTS | DIALOG_FILTER_FLAG_GROUPS | DIALOG_FILTER_FLAG_CHANNELS | DIALOG_FILTER_FLAG_BOTS;

    public static class DialogFilter {
        public int id;
        public String name;
        public int unreadCount;
        public volatile int pendingUnreadCount;
        public int order;
        public int flags;
        public ArrayList<Long> alwaysShow = new ArrayList<>();
        public ArrayList<Long> neverShow = new ArrayList<>();
        public LongSparseIntArray pinnedDialogs = new LongSparseIntArray();
        public ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();

        private static int dialogFilterPointer = 10;
        public int localId = dialogFilterPointer++;

        public boolean includesDialog(AccountInstance accountInstance, long dialogId) {
            MessagesController messagesController = accountInstance.getMessagesController();
            TLRPC.Dialog dialog = messagesController.dialogs_dict.get(dialogId);
            if (dialog == null) {
                return false;
            }
            return includesDialog(accountInstance, dialogId, dialog);
        }

        public boolean includesDialog(AccountInstance accountInstance, long dialogId, TLRPC.Dialog d) {
            if (neverShow.contains(dialogId)) {
                return false;
            }
            if (alwaysShow.contains(dialogId)) {
                return true;
            }
            if (d.folder_id != 0 && (flags & DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED) != 0) {
                return false;
            }
            MessagesController messagesController = accountInstance.getMessagesController();
            ContactsController contactsController = accountInstance.getContactsController();
            boolean skip = false;
            if ((flags & DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 && messagesController.isDialogMuted(d.id) && d.unread_mentions_count == 0 ||
                    (flags & DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0 && d.unread_count == 0 && !d.unread_mark && d.unread_mentions_count == 0) {
                return false;
            }
            if (dialogId > 0) {
                TLRPC.User user = messagesController.getUser(dialogId);
                if (user != null) {
                    if (!user.bot) {
                        if (user.self || user.contact || contactsController.isContact(dialogId)) {
                            if ((flags & DIALOG_FILTER_FLAG_CONTACTS) != 0) {
                                return true;
                            }
                        } else {
                            if ((flags & DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
                                return true;
                            }
                        }
                    } else {
                        if ((flags & DIALOG_FILTER_FLAG_BOTS) != 0) {
                            return true;
                        }
                    }
                }
            } else if (dialogId < 0) {
                TLRPC.Chat chat = messagesController.getChat(-dialogId);
                if (chat != null) {
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        if ((flags & DIALOG_FILTER_FLAG_CHANNELS) != 0) {
                            return true;
                        }
                    } else {
                        if ((flags & DIALOG_FILTER_FLAG_GROUPS) != 0) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public boolean alwaysShow(int currentAccount, TLRPC.Dialog dialog) {
            if (dialog == null) {
                return false;
            }

            long dialogId = dialog.id;

            if (DialogObject.isEncryptedDialog(dialog.id)) {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                if (encryptedChat != null) {
                    dialogId = encryptedChat.user_id;
                }
            }

            return alwaysShow.contains(dialogId);
        }
    }

    private DialogFilter sortingDialogFilter;
    private Comparator<TLRPC.Dialog> dialogDateComparator = (dialog1, dialog2) -> {
        int pinnedNum1 = sortingDialogFilter.pinnedDialogs.get(dialog1.id, Integer.MIN_VALUE);
        int pinnedNum2 = sortingDialogFilter.pinnedDialogs.get(dialog2.id, Integer.MIN_VALUE);
        if (dialog1 instanceof TLRPC.TL_dialogFolder && !(dialog2 instanceof TLRPC.TL_dialogFolder)) {
            return -1;
        } else if (!(dialog1 instanceof TLRPC.TL_dialogFolder) && dialog2 instanceof TLRPC.TL_dialogFolder) {
            return 1;
        } else if (pinnedNum1 == Integer.MIN_VALUE && pinnedNum2 != Integer.MIN_VALUE) {
            return 1;
        } else if (pinnedNum1 != Integer.MIN_VALUE && pinnedNum2 == Integer.MIN_VALUE) {
            return -1;
        } else if (pinnedNum1 != Integer.MIN_VALUE) {
            if (pinnedNum1 > pinnedNum2) {
                return 1;
            } else if (pinnedNum1 < pinnedNum2) {
                return -1;
            } else {
                return 0;
            }
        }
        MediaDataController mediaDataController = getMediaDataController();
        long date1 = DialogObject.getLastMessageOrDraftDate(dialog1, mediaDataController.getDraft(dialog1.id, 0));
        long date2 = DialogObject.getLastMessageOrDraftDate(dialog2, mediaDataController.getDraft(dialog2.id, 0));
        if (date1 < date2) {
            return 1;
        } else if (date1 > date2) {
            return -1;
        }
        return 0;
    };

    private Comparator<TLRPC.Dialog> dialogComparator = (dialog1, dialog2) -> {
        if (dialog1 instanceof TLRPC.TL_dialogFolder && !(dialog2 instanceof TLRPC.TL_dialogFolder)) {
            return -1;
        } else if (!(dialog1 instanceof TLRPC.TL_dialogFolder) && dialog2 instanceof TLRPC.TL_dialogFolder) {
            return 1;
        } else if (!dialog1.pinned && dialog2.pinned) {
            return 1;
        } else if (dialog1.pinned && !dialog2.pinned) {
            return -1;
        } else if (dialog1.pinned) {
            if (dialog1.pinnedNum < dialog2.pinnedNum) {
                return 1;
            } else if (dialog1.pinnedNum > dialog2.pinnedNum) {
                return -1;
            } else {
                return 0;
            }
        }
        MediaDataController mediaDataController = getMediaDataController();
        long date1 = DialogObject.getLastMessageOrDraftDate(dialog1, mediaDataController.getDraft(dialog1.id, 0));
        long date2 = DialogObject.getLastMessageOrDraftDate(dialog2, mediaDataController.getDraft(dialog2.id, 0));
        if (date1 < date2) {
            return 1;
        } else if (date1 > date2) {
            return -1;
        }
        return 0;
    };

    private Comparator<TLRPC.Update> updatesComparator = (lhs, rhs) -> {
        int ltype = getUpdateType(lhs);
        int rtype = getUpdateType(rhs);
        if (ltype != rtype) {
            return AndroidUtilities.compare(ltype, rtype);
        } else if (ltype == 0) {
            return AndroidUtilities.compare(getUpdatePts(lhs), getUpdatePts(rhs));
        } else if (ltype == 1) {
            return AndroidUtilities.compare(getUpdateQts(lhs), getUpdateQts(rhs));
        } else if (ltype == 2) {
            long lChannel = getUpdateChannelId(lhs);
            long rChannel = getUpdateChannelId(rhs);
            if (lChannel == rChannel) {
                return AndroidUtilities.compare(getUpdatePts(lhs), getUpdatePts(rhs));
            } else {
                return AndroidUtilities.compare(lChannel, rChannel);
            }
        }
        return 0;
    };

    private static volatile MessagesController[] Instance = new MessagesController[UserConfig.MAX_ACCOUNT_COUNT];
    public static MessagesController getInstance(int num) {
        MessagesController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (MessagesController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new MessagesController(num);
                }
            }
        }
        return localInstance;
    }

    public static SharedPreferences getNotificationsSettings(int account) {
        return getInstance(account).notificationsPreferences;
    }

    public static SharedPreferences getGlobalNotificationsSettings() {
        return getInstance(0).notificationsPreferences;
    }

    public static SharedPreferences getMainSettings(int account) {
        return getInstance(account).mainPreferences;
    }

    public static SharedPreferences getGlobalMainSettings() {
        return getInstance(0).mainPreferences;
    }

    public static SharedPreferences getEmojiSettings(int account) {
        return getInstance(account).emojiPreferences;
    }

    public static SharedPreferences getGlobalEmojiSettings() {
        return getInstance(0).emojiPreferences;
    }

    public MessagesController(int num) {
        super(num);
        currentAccount = num;
        ImageLoader.getInstance();
        getMessagesStorage();
        getLocationController();
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController messagesController = getMessagesController();
            getNotificationCenter().addObserver(messagesController, NotificationCenter.fileUploaded);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.fileUploadFailed);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.fileLoaded);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.fileLoadFailed);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.messageReceivedByServer);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.updateMessageMedia);
        });
        addSupportUser();
        if (currentAccount == 0) {
            notificationsPreferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            mainPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            emojiPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        } else {
            notificationsPreferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications" + currentAccount, Activity.MODE_PRIVATE);
            mainPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig" + currentAccount, Activity.MODE_PRIVATE);
            emojiPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji" + currentAccount, Activity.MODE_PRIVATE);
        }

        enableJoined = notificationsPreferences.getBoolean("EnableContactJoined", true);
        secretWebpagePreview = mainPreferences.getInt("secretWebpage2", 2);
        maxGroupCount = mainPreferences.getInt("maxGroupCount", 200);
        maxMegagroupCount = mainPreferences.getInt("maxMegagroupCount", 10000);
        maxRecentGifsCount = mainPreferences.getInt("maxRecentGifsCount", 200);
        maxRecentStickersCount = mainPreferences.getInt("maxRecentStickersCount", 30);
        maxFaveStickersCount = mainPreferences.getInt("maxFaveStickersCount", 5);
        maxEditTime = mainPreferences.getInt("maxEditTime", 3600);
        ratingDecay = mainPreferences.getInt("ratingDecay", 2419200);
        linkPrefix = mainPreferences.getString("linkPrefix", "t.me");
        callReceiveTimeout = mainPreferences.getInt("callReceiveTimeout", 20000);
        callRingTimeout = mainPreferences.getInt("callRingTimeout", 90000);
        callConnectTimeout = mainPreferences.getInt("callConnectTimeout", 30000);
        callPacketTimeout = mainPreferences.getInt("callPacketTimeout", 10000);
        updateCheckDelay = mainPreferences.getInt("updateCheckDelay", 24 * 60 * 60);
        maxPinnedDialogsCount = mainPreferences.getInt("maxPinnedDialogsCount", 5);
        maxFolderPinnedDialogsCount = mainPreferences.getInt("maxFolderPinnedDialogsCount", 100);
        maxMessageLength = mainPreferences.getInt("maxMessageLength", 4096);
        maxCaptionLength = mainPreferences.getInt("maxCaptionLength", 1024);
        mapProvider = mainPreferences.getInt("mapProvider", 0);
        availableMapProviders = mainPreferences.getInt("availableMapProviders", 3);
        mapKey = mainPreferences.getString("pk", null);
        installReferer = mainPreferences.getString("installReferer", null);
        revokeTimeLimit = mainPreferences.getInt("revokeTimeLimit", revokeTimeLimit);
        revokeTimePmLimit = mainPreferences.getInt("revokeTimePmLimit", revokeTimePmLimit);
        canRevokePmInbox = mainPreferences.getBoolean("canRevokePmInbox", canRevokePmInbox);
        preloadFeaturedStickers = mainPreferences.getBoolean("preloadFeaturedStickers", false);
        youtubePipType = mainPreferences.getString("youtubePipType", "disabled");
        keepAliveService = mainPreferences.getBoolean("keepAliveService", false);
        backgroundConnection = mainPreferences.getBoolean("keepAliveService", false);
        promoDialogId = mainPreferences.getLong("proxy_dialog", 0);
        nextPromoInfoCheckTime = mainPreferences.getInt("nextPromoInfoCheckTime", 0);
        promoDialogType = mainPreferences.getInt("promo_dialog_type", 0);
        promoPsaMessage = mainPreferences.getString("promo_psa_message", null);
        promoPsaType = mainPreferences.getString("promo_psa_type", null);
        proxyDialogAddress = mainPreferences.getString("proxyDialogAddress", null);
        nextTosCheckTime = notificationsPreferences.getInt("nextTosCheckTime", 0);
        venueSearchBot = mainPreferences.getString("venueSearchBot", "foursquare");
        gifSearchBot = mainPreferences.getString("gifSearchBot", "gif");
        imageSearchBot = mainPreferences.getString("imageSearchBot", "pic");
        blockedCountry = mainPreferences.getBoolean("blockedCountry", false);
        dcDomainName = mainPreferences.getString("dcDomainName2", ConnectionsManager.native_isTestBackend(currentAccount) != 0 ? "tapv3.stel.com" : "apv3.stel.com");
        webFileDatacenterId = mainPreferences.getInt("webFileDatacenterId", ConnectionsManager.native_isTestBackend(currentAccount) != 0 ? 2 : 4);
        suggestedLangCode = mainPreferences.getString("suggestedLangCode", "en");
        animatedEmojisZoom = mainPreferences.getFloat("animatedEmojisZoom", 0.625f);
        qrLoginCamera = mainPreferences.getBoolean("qrLoginCamera", false);
        saveGifsWithStickers = mainPreferences.getBoolean("saveGifsWithStickers", false);
        filtersEnabled = mainPreferences.getBoolean("filtersEnabled", false);
        showFiltersTooltip = mainPreferences.getBoolean("showFiltersTooltip", false);
        autoarchiveAvailable = mainPreferences.getBoolean("autoarchiveAvailable", false);
        groipCallVideoMaxParticipants = mainPreferences.getInt("groipCallVideoMaxParticipants", 30);
        suggestStickersApiOnly = mainPreferences.getBoolean("suggestStickersApiOnly", false);
        roundVideoSize = mainPreferences.getInt("roundVideoSize", 384);
        roundVideoBitrate = mainPreferences.getInt("roundVideoBitrate", 1000);
        roundAudioBitrate = mainPreferences.getInt("roundAudioBitrate", 64);
        pendingSuggestions = mainPreferences.getStringSet("pendingSuggestions", null);
        if (pendingSuggestions != null) {
            pendingSuggestions = new HashSet<>(pendingSuggestions);
        } else {
            pendingSuggestions = new HashSet<>();
        }

        exportUri = mainPreferences.getStringSet("exportUri2", null);
        if (exportUri != null) {
            exportUri = new HashSet<>(exportUri);
        } else {
            exportUri = new HashSet<>();
            exportUri.add("content://(\\d+@)?com\\.whatsapp\\.provider\\.media/export_chat/");
            exportUri.add("content://(\\d+@)?com\\.whatsapp\\.w4b\\.provider\\.media/export_chat/");
            exportUri.add("content://jp\\.naver\\.line\\.android\\.line\\.common\\.FileProvider/export-chat/");
            exportUri.add(".*WhatsApp.*\\.txt$");
        }

        exportGroupUri = mainPreferences.getStringSet("exportGroupUri", null);
        if (exportGroupUri != null) {
            exportGroupUri = new HashSet<>(exportGroupUri);
        } else {
            exportGroupUri = new HashSet<>();
            exportGroupUri.add("@g.us/");
        }

        exportPrivateUri = mainPreferences.getStringSet("exportPrivateUri", null);
        if (exportPrivateUri != null) {
            exportPrivateUri = new HashSet<>(exportPrivateUri);
        } else {
            exportPrivateUri = new HashSet<>();
            exportPrivateUri.add("@s.whatsapp.net/");
        }

        autologinDomains = mainPreferences.getStringSet("autologinDomains", null);
        if (autologinDomains != null) {
            autologinDomains = new HashSet<>(autologinDomains);
        } else {
            autologinDomains = new HashSet<>();
        }

        authDomains = mainPreferences.getStringSet("authDomains", null);
        if (authDomains != null) {
            authDomains = new HashSet<>(authDomains);
        } else {
            authDomains = new HashSet<>();
        }

        autologinToken = mainPreferences.getString("autologinToken", null);

        Set<String> emojies = mainPreferences.getStringSet("diceEmojies", null);
        if (emojies == null) {
            diceEmojies = new HashSet<>();
            diceEmojies.add("\uD83C\uDFB2");
            diceEmojies.add("\uD83C\uDFAF");
        } else {
            diceEmojies = new HashSet<>(emojies);
        }
        String text = mainPreferences.getString("diceSuccess", null);
        if (text == null) {
            diceSuccess.put("\uD83C\uDFAF", new DiceFrameSuccess(62, 6));
        } else {
            try {
                byte[] bytes = Base64.decode(text, Base64.DEFAULT);
                if (bytes != null) {
                    SerializedData data = new SerializedData(bytes);
                    int count = data.readInt32(true);
                    for (int a = 0; a < count; a++) {
                        diceSuccess.put(data.readString(true), new DiceFrameSuccess(data.readInt32(true), data.readInt32(true)));
                    }
                    data.cleanup();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        text = mainPreferences.getString("emojiSounds", null);
        if (text != null) {
            try {
                byte[] bytes = Base64.decode(text, Base64.DEFAULT);
                if (bytes != null) {
                    SerializedData data = new SerializedData(bytes);
                    int count = data.readInt32(true);
                    for (int a = 0; a < count; a++) {
                        emojiSounds.put(data.readString(true), new EmojiSound(data.readInt64(true), data.readInt64(true), data.readByteArray(true)));
                    }
                    data.cleanup();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        text = mainPreferences.getString("gifSearchEmojies", null);
        if (text == null) {
            gifSearchEmojies.add("👍");
            gifSearchEmojies.add("👎");
            gifSearchEmojies.add("😍");
            gifSearchEmojies.add("😂");
            gifSearchEmojies.add("😮");
            gifSearchEmojies.add("🙄");
            gifSearchEmojies.add("😥");
            gifSearchEmojies.add("😡");
            gifSearchEmojies.add("🥳");
            gifSearchEmojies.add("😎");
        } else {
            try {
                byte[] bytes = Base64.decode(text, Base64.DEFAULT);
                if (bytes != null) {
                    SerializedData data = new SerializedData(bytes);
                    int count = data.readInt32(true);
                    for (int a = 0; a < count; a++) {
                        gifSearchEmojies.add(data.readString(true));
                    }
                    data.cleanup();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void sendLoadPeersRequest(TLObject req, ArrayList<TLObject> requests, TLRPC.messages_Dialogs pinnedDialogs, TLRPC.messages_Dialogs pinnedRemoteDialogs, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<DialogFilter> filtersToSave, SparseArray<DialogFilter> filtersToDelete, ArrayList<Integer> filtersOrder, HashMap<Integer, HashSet<Long>> filterDialogRemovals, HashMap<Integer, HashSet<Long>> filterUserRemovals, HashSet<Integer> filtersUnreadCounterReset) {
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_messages_chats) {
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                chats.addAll(res.chats);
            } else if (response instanceof TLRPC.Vector) {
                TLRPC.Vector res = (TLRPC.Vector) response;
                for (int a = 0, N = res.objects.size(); a < N; a++) {
                    TLRPC.User user = (TLRPC.User) res.objects.get(a);
                    users.add(user);
                }
            } else if (response instanceof TLRPC.TL_messages_peerDialogs) {
                TLRPC.TL_messages_peerDialogs peerDialogs = (TLRPC.TL_messages_peerDialogs) response;
                pinnedDialogs.dialogs.addAll(peerDialogs.dialogs);
                pinnedDialogs.messages.addAll(peerDialogs.messages);
                pinnedRemoteDialogs.dialogs.addAll(peerDialogs.dialogs);
                pinnedRemoteDialogs.messages.addAll(peerDialogs.messages);
                users.addAll(peerDialogs.users);
                chats.addAll(peerDialogs.chats);
            }
            requests.remove(req);
            if (requests.isEmpty()) {
                getMessagesStorage().processLoadedFilterPeers(pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
            }
        });
    }

    protected void loadFilterPeers(HashMap<Long, TLRPC.InputPeer> dialogsToLoadMap, HashMap<Long, TLRPC.InputPeer> usersToLoadMap, HashMap<Long, TLRPC.InputPeer> chatsToLoadMap, TLRPC.messages_Dialogs pinnedDialogs, TLRPC.messages_Dialogs pinnedRemoteDialogs, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<DialogFilter> filtersToSave, SparseArray<DialogFilter> filtersToDelete, ArrayList<Integer> filtersOrder, HashMap<Integer, HashSet<Long>> filterDialogRemovals, HashMap<Integer, HashSet<Long>> filterUserRemovals, HashSet<Integer> filtersUnreadCounterReset) {
        Utilities.stageQueue.postRunnable(() -> {
            ArrayList<TLObject> requests = new ArrayList<>();
            TLRPC.TL_users_getUsers req = null;
            for (HashMap.Entry<Long, TLRPC.InputPeer> entry : usersToLoadMap.entrySet()) {
                if (req == null) {
                    req = new TLRPC.TL_users_getUsers();
                    requests.add(req);
                }
                req.id.add(getInputUser(entry.getValue()));
                if (req.id.size() == 100) {
                    sendLoadPeersRequest(req, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
                    req = null;
                }
            }
            if (req != null) {
                sendLoadPeersRequest(req, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
            }
            TLRPC.TL_messages_getChats req2 = null;
            TLRPC.TL_channels_getChannels req3 = null;
            for (HashMap.Entry<Long, TLRPC.InputPeer> entry : chatsToLoadMap.entrySet()) {
                TLRPC.InputPeer inputPeer = entry.getValue();
                if (inputPeer.chat_id != 0) {
                    if (req2 == null) {
                        req2 = new TLRPC.TL_messages_getChats();
                        requests.add(req2);
                    }
                    req2.id.add(entry.getKey());
                    if (req2.id.size() == 100) {
                        sendLoadPeersRequest(req2, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
                        req2 = null;
                    }
                } else if (inputPeer.channel_id != 0) {
                    if (req3 == null) {
                        req3 = new TLRPC.TL_channels_getChannels();
                        requests.add(req3);
                    }
                    req3.id.add(getInputChannel(inputPeer));
                    if (req3.id.size() == 100) {
                        sendLoadPeersRequest(req3, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
                        req3 = null;
                    }
                }
            }
            if (req2 != null) {
                sendLoadPeersRequest(req2, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
            }
            if (req3 != null) {
                sendLoadPeersRequest(req3, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
            }

            TLRPC.TL_messages_getPeerDialogs req4 = null;
            for (HashMap.Entry<Long, TLRPC.InputPeer> entry : dialogsToLoadMap.entrySet()) {
                if (req4 == null) {
                    req4 = new TLRPC.TL_messages_getPeerDialogs();
                    requests.add(req4);
                }
                TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                inputDialogPeer.peer = entry.getValue();
                req4.peers.add(inputDialogPeer);
                if (req4.peers.size() == 100) {
                    sendLoadPeersRequest(req4, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
                    req4 = null;
                }
            }
            if (req4 != null) {
                sendLoadPeersRequest(req4, requests, pinnedDialogs, pinnedRemoteDialogs, users, chats, filtersToSave, filtersToDelete, filtersOrder, filterDialogRemovals, filterUserRemovals, filtersUnreadCounterReset);
            }
        });
    }

    protected void processLoadedDialogFilters(ArrayList<DialogFilter> filters, TLRPC.messages_Dialogs pinnedDialogs, TLRPC.messages_Dialogs pinnedRemoteDialogs, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, ArrayList<TLRPC.EncryptedChat> encryptedChats, int remote) {
        Utilities.stageQueue.postRunnable(() -> {

            LongSparseArray<TLRPC.Dialog> new_dialogs_dict = new LongSparseArray<>();
            SparseArray<TLRPC.EncryptedChat> enc_chats_dict;
            LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
            LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
            LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();

            for (int a = 0; a < pinnedDialogs.users.size(); a++) {
                TLRPC.User u = pinnedDialogs.users.get(a);
                usersDict.put(u.id, u);
            }
            for (int a = 0; a < pinnedDialogs.chats.size(); a++) {
                TLRPC.Chat c = pinnedDialogs.chats.get(a);
                chatsDict.put(c.id, c);
            }
            if (encryptedChats != null) {
                enc_chats_dict = new SparseArray<>();
                for (int a = 0, N = encryptedChats.size(); a < N; a++) {
                    TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
                    enc_chats_dict.put(encryptedChat.id, encryptedChat);
                }
            } else {
                enc_chats_dict = null;
            }

            for (int a = 0; a < pinnedDialogs.messages.size(); a++) {
                TLRPC.Message message = pinnedDialogs.messages.get(a);
                if (message.peer_id.channel_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.peer_id.channel_id);
                    if (chat != null && chat.left && (promoDialogId == 0 || promoDialogId != -chat.id)) {
                        continue;
                    }
                } else if (message.peer_id.chat_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.peer_id.chat_id);
                    if (chat != null && chat.migrated_to != null) {
                        continue;
                    }
                }
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false, true);
                new_dialogMessage.put(messageObject.getDialogId(), messageObject);
            }

            for (int a = 0; a < pinnedDialogs.dialogs.size(); a++) {
                TLRPC.Dialog d = pinnedDialogs.dialogs.get(a);
                DialogObject.initDialog(d);
                if (d.id == 0) {
                    continue;
                }
                if (DialogObject.isEncryptedDialog(d.id) && enc_chats_dict != null) {
                    if (enc_chats_dict.get(DialogObject.getEncryptedChatId(d.id)) == null) {
                        continue;
                    }
                }
                if (promoDialogId != 0 && promoDialogId == d.id) {
                    promoDialog = d;
                }
                if (d.last_message_date == 0) {
                    MessageObject mess = new_dialogMessage.get(d.id);
                    if (mess != null) {
                        d.last_message_date = mess.messageOwner.date;
                    }
                }
                if (DialogObject.isChannel(d)) {
                    TLRPC.Chat chat = chatsDict.get(-d.id);
                    if (chat != null) {
                        if (chat.left && (promoDialogId == 0 || promoDialogId != d.id)) {
                            continue;
                        }
                    }
                    channelsPts.put(-d.id, d.pts);
                } else if (d.id < 0) {
                    TLRPC.Chat chat = chatsDict.get(-d.id);
                    if (chat != null && chat.migrated_to != null) {
                        continue;
                    }
                }
                new_dialogs_dict.put(d.id, d);

                Integer value = dialogs_read_inbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_inbox_max.put(d.id, Math.max(value, d.read_inbox_max_id));

                value = dialogs_read_outbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_outbox_max.put(d.id, Math.max(value, d.read_outbox_max_id));
            }

            if (pinnedRemoteDialogs != null && !pinnedRemoteDialogs.dialogs.isEmpty()) {
                ImageLoader.saveMessagesThumbs(pinnedRemoteDialogs.messages);
                for (int a = 0; a < pinnedRemoteDialogs.messages.size(); a++) {
                    TLRPC.Message message = pinnedRemoteDialogs.messages.get(a);
                    if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                        TLRPC.User user = usersDict.get(message.action.user_id);
                        if (user != null && user.bot) {
                            message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                            message.flags |= 64;
                        }
                    }

                    if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                        message.unread = false;
                        message.media_unread = false;
                    } else {
                        ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                        Integer value = read_max.get(message.dialog_id);
                        if (value == null) {
                            value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                            read_max.put(message.dialog_id, value);
                        }
                        message.unread = value < message.id;
                    }
                }
                getMessagesStorage().putDialogs(pinnedRemoteDialogs, 0);
            }


            AndroidUtilities.runOnUIThread(() -> {
                if (remote != 2) {
                    dialogFilters = filters;
                    dialogFiltersById.clear();
                    for (int a = 0, N = dialogFilters.size(); a < N; a++) {
                        DialogFilter filter = dialogFilters.get(a);
                        dialogFiltersById.put(filter.id, filter);
                    }
                    Collections.sort(dialogFilters, (o1, o2) -> {
                        if (o1.order > o2.order) {
                            return 1;
                        } else if (o1.order < o2.order) {
                            return -1;
                        }
                        return 0;
                    });
                    putUsers(users, true);
                    putChats(chats, true);
                    dialogFiltersLoaded = true;
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
                    if (remote == 0) {
                        loadRemoteFilters(false);
                    }

                    if (pinnedRemoteDialogs != null && !pinnedRemoteDialogs.dialogs.isEmpty()) {
                        applyDialogsNotificationsSettings(pinnedRemoteDialogs.dialogs);
                    }

                    if (encryptedChats != null) {
                        for (int a = 0; a < encryptedChats.size(); a++) {
                            TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
                            if (encryptedChat instanceof TLRPC.TL_encryptedChat && AndroidUtilities.getMyLayerVersion(encryptedChat.layer) < SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
                                getSecretChatHelper().sendNotifyLayerMessage(encryptedChat, null);
                            }
                            putEncryptedChat(encryptedChat, true);
                        }
                    }

                    for (int a = 0; a < new_dialogs_dict.size(); a++) {
                        long key = new_dialogs_dict.keyAt(a);
                        TLRPC.Dialog value = new_dialogs_dict.valueAt(a);
                        TLRPC.Dialog currentDialog = dialogs_dict.get(key);

                        if (pinnedRemoteDialogs != null && pinnedRemoteDialogs.dialogs.contains(value)) {
                            if (value.draft instanceof TLRPC.TL_draftMessage) {
                                getMediaDataController().saveDraft(value.id, 0, value.draft, null, false);
                            }
                            if (currentDialog != null) {
                                currentDialog.notify_settings = value.notify_settings;
                            }
                        }

                        MessageObject newMsg = new_dialogMessage.get(value.id);
                        if (currentDialog == null) {
                            dialogs_dict.put(key, value);
                            dialogMessage.put(key, newMsg);
                            if (newMsg != null && newMsg.messageOwner.peer_id.channel_id == 0) {
                                dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                if (newMsg.messageOwner.random_id != 0) {
                                    dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                }
                            }
                        } else {
                            currentDialog.pinned = value.pinned;
                            currentDialog.pinnedNum = value.pinnedNum;
                            MessageObject oldMsg = dialogMessage.get(key);
                            if (oldMsg != null && oldMsg.deleted || oldMsg == null || currentDialog.top_message > 0) {
                                if (value.top_message >= currentDialog.top_message) {
                                    dialogs_dict.put(key, value);
                                    dialogMessage.put(key, newMsg);
                                    if (oldMsg != null) {
                                        if (oldMsg.messageOwner.peer_id.channel_id == 0) {
                                            dialogMessagesByIds.remove(oldMsg.getId());
                                        }
                                        if (oldMsg.messageOwner.random_id != 0) {
                                            dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                        }
                                    }
                                    if (newMsg != null && newMsg.messageOwner.peer_id.channel_id == 0) {
                                        if (oldMsg != null && oldMsg.getId() == newMsg.getId()) {
                                            newMsg.deleted = oldMsg.deleted;
                                        }
                                        dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                        if (newMsg.messageOwner.random_id != 0) {
                                            dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                        }
                                    }
                                }
                            } else {
                                if (newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                    dialogs_dict.put(key, value);
                                    dialogMessage.put(key, newMsg);
                                    if (oldMsg.messageOwner.peer_id.channel_id == 0) {
                                        dialogMessagesByIds.remove(oldMsg.getId());
                                    }
                                    if (newMsg != null) {
                                        if (oldMsg.getId() == newMsg.getId()) {
                                            newMsg.deleted = oldMsg.deleted;
                                        }
                                        if (newMsg.messageOwner.peer_id.channel_id == 0) {
                                            dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                            if (newMsg.messageOwner.random_id != 0) {
                                                dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                            }
                                        }
                                    }
                                    if (oldMsg.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                    }
                                }
                            }
                        }
                    }

                    allDialogs.clear();
                    for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                        TLRPC.Dialog dialog = dialogs_dict.valueAt(a);
                        if (deletingDialogs.indexOfKey(dialog.id) >= 0) {
                            continue;
                        }
                        allDialogs.add(dialog);
                    }
                    sortDialogs(null);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                }
                if (remote != 0) {
                    getUserConfig().filtersLoaded = true;
                    getUserConfig().saveConfig(false);
                    loadingRemoteFilters = false;
                    getNotificationCenter().postNotificationName(NotificationCenter.filterSettingsUpdated);
                }
            });
        });
    }

    public void loadSuggestedFilters() {
        if (loadingSuggestedFilters) {
            return;
        }
        loadingSuggestedFilters = true;

        TLRPC.TL_messages_getSuggestedDialogFilters req = new TLRPC.TL_messages_getSuggestedDialogFilters();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingSuggestedFilters = false;
            suggestedFilters.clear();
            if (response instanceof TLRPC.Vector) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                for (int a = 0, N = vector.objects.size(); a < N; a++) {
                    suggestedFilters.add((TLRPC.TL_dialogFilterSuggested) vector.objects.get(a));
                }
            }
            getNotificationCenter().postNotificationName(NotificationCenter.suggestedFiltersLoaded);
        }));
    }

    public void loadRemoteFilters(boolean force) {
        if (loadingRemoteFilters || !getUserConfig().isClientActivated() || !force && getUserConfig().filtersLoaded) {
            return;
        }
        if (force) {
            getUserConfig().filtersLoaded = false;
            getUserConfig().saveConfig(false);
        }
        TLRPC.TL_messages_getDialogFilters req = new TLRPC.TL_messages_getDialogFilters();
        getConnectionsManager().sendRequest(req, (response, error) -> {
           if (response instanceof TLRPC.Vector) {
               getMessagesStorage().checkLoadedRemoteFilters((TLRPC.Vector) response);
           } else {
               AndroidUtilities.runOnUIThread(() -> loadingRemoteFilters = false);
           }
        });
    }

    public void selectDialogFilter(DialogFilter filter, int index) {
        if (selectedDialogFilter[index] == filter) {
            return;
        }
        DialogFilter prevFilter = selectedDialogFilter[index];
        selectedDialogFilter[index] = filter;
        if (selectedDialogFilter[index == 0 ? 1 : 0] == filter) {
            selectedDialogFilter[index == 0 ? 1 : 0] = null;
        }
        if (selectedDialogFilter[index] == null) {
            if (prevFilter != null) {
                prevFilter.dialogs.clear();
            }
        } else {
            sortDialogs(null);
        }
    }

    public void onFilterUpdate(DialogFilter filter) {
        for (int a = 0; a < 2; a++) {
            if (selectedDialogFilter[a] == filter) {
                sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                break;
            }
        }
    }

    public void addFilter(DialogFilter filter, boolean atBegin) {
        if (atBegin) {
            int order = 254;
            for (int a = 0, N = dialogFilters.size(); a < N; a++) {
                order = Math.min(order, dialogFilters.get(a).order);
            }
            filter.order = order - 1;
            dialogFilters.add(0, filter);
        } else {
            int order = 0;
            for (int a = 0, N = dialogFilters.size(); a < N; a++) {
                order = Math.max(order, dialogFilters.get(a).order);
            }
            filter.order = order + 1;
            dialogFilters.add(filter);
        }
        dialogFiltersById.put(filter.id, filter);
        if (dialogFilters.size() == 1 && SharedConfig.getChatSwipeAction(currentAccount) != SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS) {
            SharedConfig.updateChatListSwipeSetting(SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS);
        }
    }

    public void removeFilter(DialogFilter filter) {
        dialogFilters.remove(filter);
        dialogFiltersById.remove(filter.id);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
    }

    private void loadAppConfig() {
        if (loadingAppConfig) {
            return;
        }
        loadingAppConfig = true;
        TLRPC.TL_help_getAppConfig req = new TLRPC.TL_help_getAppConfig();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_jsonObject) {
                SharedPreferences.Editor editor = mainPreferences.edit();
                boolean changed = false;
                boolean keelAliveChanged = false;
                TLRPC.TL_jsonObject object = (TLRPC.TL_jsonObject) response;
                for (int a = 0, N = object.value.size(); a < N; a++) {
                    TLRPC.TL_jsonObjectValue value = object.value.get(a);
                    switch (value.key) {
                        case "emojies_animated_zoom": {
                            if (value.value instanceof TLRPC.TL_jsonNumber) {
                                TLRPC.TL_jsonNumber number = (TLRPC.TL_jsonNumber) value.value;
                                if (animatedEmojisZoom != number.value) {
                                    animatedEmojisZoom = (float) number.value;
                                    editor.putFloat("animatedEmojisZoom", animatedEmojisZoom);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "dialog_filters_enabled": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != filtersEnabled) {
                                    filtersEnabled = bool.value;
                                    editor.putBoolean("filtersEnabled", filtersEnabled);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "dialog_filters_tooltip": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != showFiltersTooltip) {
                                    showFiltersTooltip = bool.value;
                                    editor.putBoolean("showFiltersTooltip", showFiltersTooltip);
                                    changed = true;
                                    getNotificationCenter().postNotificationName(NotificationCenter.filterSettingsUpdated);
                                }
                            }
                            break;
                        }
                        case "youtube_pip": {
                            if (value.value instanceof TLRPC.TL_jsonString) {
                                TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) value.value;
                                if (!string.value.equals(youtubePipType)) {
                                    youtubePipType = string.value;
                                    editor.putString("youtubePipType", youtubePipType);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "background_connection": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != backgroundConnection) {
                                    backgroundConnection = bool.value;
                                    editor.putBoolean("backgroundConnection", backgroundConnection);
                                    changed = true;
                                    keelAliveChanged = true;
                                }
                            }
                            break;
                        }
                        case "keep_alive_service": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != keepAliveService) {
                                    keepAliveService = bool.value;
                                    editor.putBoolean("keepAliveService", keepAliveService);
                                    changed = true;
                                    keelAliveChanged = true;
                                }
                            }
                            break;
                        }
                        case "qr_login_camera": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != qrLoginCamera) {
                                    qrLoginCamera = bool.value;
                                    editor.putBoolean("qrLoginCamera", qrLoginCamera);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "save_gifs_with_stickers": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != saveGifsWithStickers) {
                                    saveGifsWithStickers = bool.value;
                                    editor.putBoolean("saveGifsWithStickers", saveGifsWithStickers);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "url_auth_domains": {
                            HashSet<String> newDomains = new HashSet<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newDomains.add(string.value);
                                    }
                                }
                            }
                            if (!authDomains.equals(newDomains)) {
                                authDomains = newDomains;
                                editor.putStringSet("authDomains", authDomains);
                                changed = true;
                            }
                            break;
                        }
                        case "autologin_domains" : {
                            HashSet<String> newDomains = new HashSet<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newDomains.add(string.value);
                                    }
                                }
                            }
                            if (!autologinDomains.equals(newDomains)) {
                                autologinDomains = newDomains;
                                editor.putStringSet("autologinDomains", autologinDomains);
                                changed = true;
                            }
                            break;
                        }
                        case "autologin_token" : {
                            if (value.value instanceof TLRPC.TL_jsonString) {
                                TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) value.value;
                                if (!string.value.equals(autologinToken)) {
                                    autologinToken = string.value;
                                    editor.putString("autologinToken", autologinToken);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "emojies_send_dice": {
                            HashSet<String> newEmojies = new HashSet<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newEmojies.add(string.value.replace("\uFE0F", ""));
                                    }
                                }
                            }
                            if (!diceEmojies.equals(newEmojies)) {
                                diceEmojies = newEmojies;
                                editor.putStringSet("diceEmojies", diceEmojies);
                                changed = true;
                            }
                            break;
                        }
                        case "gif_search_emojies": {
                            ArrayList<String> newEmojies = new ArrayList<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newEmojies.add(string.value.replace("\uFE0F", ""));
                                    }
                                }
                            }
                            if (!gifSearchEmojies.equals(newEmojies)) {
                                gifSearchEmojies = newEmojies;
                                SerializedData serializedData = new SerializedData();
                                serializedData.writeInt32(gifSearchEmojies.size());
                                for (int b = 0, N2 = gifSearchEmojies.size(); b < N2; b++) {
                                    serializedData.writeString(gifSearchEmojies.get(b));
                                }
                                editor.putString("gifSearchEmojies", Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT));
                                serializedData.cleanup();
                                changed = true;
                            }
                            break;
                        }
                        case "emojies_send_dice_success": {
                            try {
                                HashMap<String, DiceFrameSuccess> newEmojies = new HashMap<>();
                                if (value.value instanceof TLRPC.TL_jsonObject) {
                                    TLRPC.TL_jsonObject jsonObject = (TLRPC.TL_jsonObject) value.value;
                                    for (int b = 0, N2 = jsonObject.value.size(); b < N2; b++) {
                                        TLRPC.TL_jsonObjectValue val = jsonObject.value.get(b);
                                        if (val.value instanceof TLRPC.TL_jsonObject) {
                                            TLRPC.TL_jsonObject jsonObject2 = (TLRPC.TL_jsonObject) val.value;
                                            int n = Integer.MAX_VALUE;
                                            int f = Integer.MAX_VALUE;
                                            for (int c = 0, N3 = jsonObject2.value.size(); c < N3; c++) {
                                                TLRPC.TL_jsonObjectValue val2 = jsonObject2.value.get(c);
                                                if (val2.value instanceof TLRPC.TL_jsonNumber) {
                                                    if ("value".equals(val2.key)) {
                                                        n = (int) ((TLRPC.TL_jsonNumber) val2.value).value;
                                                    } else if ("frame_start".equals(val2.key)) {
                                                        f = (int) ((TLRPC.TL_jsonNumber) val2.value).value;
                                                    }
                                                }
                                            }
                                            if (f != Integer.MAX_VALUE && n != Integer.MAX_VALUE) {
                                                newEmojies.put(val.key.replace("\uFE0F", ""), new DiceFrameSuccess(f, n));
                                            }
                                        }
                                    }
                                }
                                if (!diceSuccess.equals(newEmojies)) {
                                    diceSuccess = newEmojies;
                                    SerializedData serializedData = new SerializedData();
                                    serializedData.writeInt32(diceSuccess.size());
                                    for (HashMap.Entry<String, DiceFrameSuccess> entry : diceSuccess.entrySet()) {
                                        serializedData.writeString(entry.getKey());
                                        DiceFrameSuccess frameSuccess = entry.getValue();
                                        serializedData.writeInt32(frameSuccess.frame);
                                        serializedData.writeInt32(frameSuccess.num);
                                    }
                                    editor.putString("diceSuccess", Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT));
                                    serializedData.cleanup();
                                    changed = true;
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            break;
                        }
                        case "autoarchive_setting_available": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != autoarchiveAvailable) {
                                    autoarchiveAvailable = bool.value;
                                    editor.putBoolean("autoarchiveAvailable", autoarchiveAvailable);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "groupcall_video_participants_max": {
                            if (value.value instanceof TLRPC.TL_jsonNumber) {
                                TLRPC.TL_jsonNumber number = (TLRPC.TL_jsonNumber) value.value;
                                if (number.value != groipCallVideoMaxParticipants) {
                                    groipCallVideoMaxParticipants = (int) number.value;
                                    editor.putInt("groipCallVideoMaxParticipants", groipCallVideoMaxParticipants);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "inapp_update_check_delay": {
                            if (value.value instanceof TLRPC.TL_jsonNumber) {
                                TLRPC.TL_jsonNumber number = (TLRPC.TL_jsonNumber) value.value;
                                if (number.value != updateCheckDelay) {
                                    updateCheckDelay = (int) number.value;
                                    editor.putInt("updateCheckDelay", updateCheckDelay);
                                    changed = true;
                                }
                            } else if (value.value instanceof TLRPC.TL_jsonString) {
                                TLRPC.TL_jsonString number = (TLRPC.TL_jsonString) value.value;
                                int delay = Utilities.parseInt(number.value);
                                if (delay != updateCheckDelay) {
                                    updateCheckDelay = delay;
                                    editor.putInt("updateCheckDelay", updateCheckDelay);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "round_video_encoding": {
                            if (value.value instanceof TLRPC.TL_jsonObject) {
                                TLRPC.TL_jsonObject jsonObject = (TLRPC.TL_jsonObject) value.value;
                                for (int b = 0, N2 = jsonObject.value.size(); b < N2; b++) {
                                    TLRPC.TL_jsonObjectValue value2 = jsonObject.value.get(b);
                                    switch (value2.key) {
                                        case "diameter": {
                                            if (value2.value instanceof TLRPC.TL_jsonNumber) {
                                                TLRPC.TL_jsonNumber number = (TLRPC.TL_jsonNumber) value2.value;
                                                if (number.value != roundVideoSize) {
                                                    roundVideoSize = (int) number.value;
                                                    editor.putInt("roundVideoSize", roundVideoSize);
                                                    changed = true;
                                                }
                                            }
                                            break;
                                        }
                                        case "video_bitrate": {
                                            if (value2.value instanceof TLRPC.TL_jsonNumber) {
                                                TLRPC.TL_jsonNumber number = (TLRPC.TL_jsonNumber) value2.value;
                                                if (number.value != roundVideoBitrate) {
                                                    roundVideoBitrate = (int) number.value;
                                                    editor.putInt("roundVideoBitrate", roundVideoBitrate);
                                                    changed = true;
                                                }
                                            }
                                            break;
                                        }
                                        case "audio_bitrate": {
                                            if (value2.value instanceof TLRPC.TL_jsonNumber) {
                                                TLRPC.TL_jsonNumber number = (TLRPC.TL_jsonNumber) value2.value;
                                                if (number.value != roundAudioBitrate) {
                                                    roundAudioBitrate = (int) number.value;
                                                    editor.putInt("roundAudioBitrate", roundAudioBitrate);
                                                    changed = true;
                                                }
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                        }
                        case "stickers_emoji_suggest_only_api": {
                            if (value.value instanceof TLRPC.TL_jsonBool) {
                                TLRPC.TL_jsonBool bool = (TLRPC.TL_jsonBool) value.value;
                                if (bool.value != suggestStickersApiOnly) {
                                    suggestStickersApiOnly = bool.value;
                                    editor.putBoolean("suggestStickersApiOnly", suggestStickersApiOnly);
                                    changed = true;
                                }
                            }
                            break;
                        }
                        case "export_regex": {
                            HashSet<String> newExport = new HashSet<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newExport.add(string.value);
                                    }
                                }
                            }
                            if (!exportUri.equals(newExport)) {
                                exportUri = newExport;
                                editor.putStringSet("exportUri2", exportUri);
                                changed = true;
                            }
                            break;
                        }
                        case "export_group_urls": {
                            HashSet<String> newExport = new HashSet<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newExport.add(string.value);
                                    }
                                }
                            }
                            if (!exportGroupUri.equals(newExport)) {
                                exportGroupUri = newExport;
                                editor.putStringSet("exportGroupUri", exportGroupUri);
                                changed = true;
                            }
                            break;
                        }
                        case "export_private_urls": {
                            HashSet<String> newExport = new HashSet<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newExport.add(string.value);
                                    }
                                }
                            }
                            if (!exportPrivateUri.equals(newExport)) {
                                exportPrivateUri = newExport;
                                editor.putStringSet("exportPrivateUri", exportPrivateUri);
                                changed = true;
                            }
                            break;
                        }
                        case "pending_suggestions": {
                            HashSet<String> newSuggestions = new HashSet<>();
                            if (value.value instanceof TLRPC.TL_jsonArray) {
                                TLRPC.TL_jsonArray array = (TLRPC.TL_jsonArray) value.value;
                                for (int b = 0, N2 = array.value.size(); b < N2; b++) {
                                    TLRPC.JSONValue val = array.value.get(b);
                                    if (val instanceof TLRPC.TL_jsonString) {
                                        TLRPC.TL_jsonString string = (TLRPC.TL_jsonString) val;
                                        newSuggestions.add(string.value);
                                    }
                                }
                            }
                            if (!pendingSuggestions.equals(newSuggestions)) {
                                pendingSuggestions = newSuggestions;
                                editor.putStringSet("pendingSuggestions", pendingSuggestions);
                                getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
                                changed = true;
                            }
                            break;
                        }
                        case "emojies_sounds": {
                            try {
                                HashMap<String, EmojiSound> newEmojies = new HashMap<>();
                                if (value.value instanceof TLRPC.TL_jsonObject) {
                                    TLRPC.TL_jsonObject jsonObject = (TLRPC.TL_jsonObject) value.value;
                                    for (int b = 0, N2 = jsonObject.value.size(); b < N2; b++) {
                                        TLRPC.TL_jsonObjectValue val = jsonObject.value.get(b);
                                        if (val.value instanceof TLRPC.TL_jsonObject) {
                                            TLRPC.TL_jsonObject jsonObject2 = (TLRPC.TL_jsonObject) val.value;
                                            long i = 0;
                                            long ah = 0;
                                            String fr = null;
                                            for (int c = 0, N3 = jsonObject2.value.size(); c < N3; c++) {
                                                TLRPC.TL_jsonObjectValue val2 = jsonObject2.value.get(c);
                                                if (val2.value instanceof TLRPC.TL_jsonString) {
                                                    if ("id".equals(val2.key)) {
                                                        i = Utilities.parseLong(((TLRPC.TL_jsonString) val2.value).value);
                                                    } else if ("access_hash".equals(val2.key)) {
                                                        ah = Utilities.parseLong(((TLRPC.TL_jsonString) val2.value).value);
                                                    } else if ("file_reference_base64".equals(val2.key)) {
                                                        fr = ((TLRPC.TL_jsonString) val2.value).value;
                                                    }
                                                }
                                            }
                                            if (i != 0 && ah != 0 && fr != null) {
                                                newEmojies.put(val.key.replace("\uFE0F", ""), new EmojiSound(i, ah, fr));
                                            }
                                        }
                                    }
                                }
                                if (!emojiSounds.equals(newEmojies)) {
                                    emojiSounds = newEmojies;
                                    SerializedData serializedData = new SerializedData();
                                    serializedData.writeInt32(emojiSounds.size());
                                    for (HashMap.Entry<String, EmojiSound> entry : emojiSounds.entrySet()) {
                                        serializedData.writeString(entry.getKey());
                                        EmojiSound emojiSound = entry.getValue();
                                        serializedData.writeInt64(emojiSound.id);
                                        serializedData.writeInt64(emojiSound.accessHash);
                                        serializedData.writeByteArray(emojiSound.fileReference);
                                    }
                                    editor.putString("emojiSounds", Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT));
                                    serializedData.cleanup();
                                    changed = true;
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            break;
                        }
                    }
                }
                if (changed) {
                    editor.commit();
                }
                if (keelAliveChanged) {
                    ApplicationLoader.startPushService();
                    ConnectionsManager connectionsManager = getConnectionsManager();
                    connectionsManager.setPushConnectionEnabled(connectionsManager.isPushConnectionEnabled());
                }
            }
            loadingAppConfig = false;
        }));
    }

    public void removeSuggestion(long did, String suggestion) {
        if (TextUtils.isEmpty(suggestion)) {
            return;
        }
        if (did == 0) {
            if (pendingSuggestions.remove(suggestion)) {
                SharedPreferences.Editor editor = mainPreferences.edit();
                editor.putStringSet("pendingSuggestions", pendingSuggestions);
                editor.commit();
                getNotificationCenter().postNotificationName(NotificationCenter.newSuggestionsAvailable);
            } else {
                return;
            }
        }
        TLRPC.TL_help_dismissSuggestion req = new TLRPC.TL_help_dismissSuggestion();
        req.suggestion = suggestion;
        if (did == 0) {
            req.peer = new TLRPC.TL_inputPeerEmpty();
        } else {
            req.peer = getInputPeer(did);
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public void updateConfig(final TLRPC.TL_config config) {
        AndroidUtilities.runOnUIThread(() -> {
            getDownloadController().loadAutoDownloadConfig(false);
            loadAppConfig();
            maxMegagroupCount = config.megagroup_size_max;
            maxGroupCount = config.chat_size_max;
            maxEditTime = config.edit_time_limit;
            ratingDecay = config.rating_e_decay;
            maxRecentGifsCount = config.saved_gifs_limit;
            maxRecentStickersCount = config.stickers_recent_limit;
            maxFaveStickersCount = config.stickers_faved_limit;
            revokeTimeLimit = config.revoke_time_limit;
            revokeTimePmLimit = config.revoke_pm_time_limit;
            canRevokePmInbox = config.revoke_pm_inbox;
            linkPrefix = config.me_url_prefix;
            if (linkPrefix.endsWith("/")) {
                linkPrefix = linkPrefix.substring(0, linkPrefix.length() - 1);
            }
            if (linkPrefix.startsWith("https://")) {
                linkPrefix = linkPrefix.substring(8);
            } else if (linkPrefix.startsWith("http://")) {
                linkPrefix = linkPrefix.substring(7);
            }
            callReceiveTimeout = config.call_receive_timeout_ms;
            callRingTimeout = config.call_ring_timeout_ms;
            callConnectTimeout = config.call_connect_timeout_ms;
            callPacketTimeout = config.call_packet_timeout_ms;
            maxPinnedDialogsCount = config.pinned_dialogs_count_max;
            maxFolderPinnedDialogsCount = config.pinned_infolder_count_max;
            maxMessageLength = config.message_length_max;
            maxCaptionLength = config.caption_length_max;
            preloadFeaturedStickers = config.preload_featured_stickers;
            if (config.venue_search_username != null) {
                venueSearchBot = config.venue_search_username;
            }
            if (config.gif_search_username != null) {
                gifSearchBot = config.gif_search_username;
            }
            if (imageSearchBot != null) {
                imageSearchBot = config.img_search_username;
            }
            blockedCountry = config.blocked_mode;
            dcDomainName = config.dc_txt_domain_name;
            webFileDatacenterId = config.webfile_dc_id;
            if (config.suggested_lang_code != null && (suggestedLangCode == null || !suggestedLangCode.equals(config.suggested_lang_code))) {
                suggestedLangCode = config.suggested_lang_code;
                LocaleController.getInstance().loadRemoteLanguages(currentAccount);
            }
            Theme.loadRemoteThemes(currentAccount, false);
            Theme.checkCurrentRemoteTheme(false);

            if (config.static_maps_provider == null) {
                config.static_maps_provider = "telegram";
            }

            mapKey = null;
            mapProvider = 2;
            availableMapProviders = 0;
            FileLog.d("map providers = " + config.static_maps_provider);
            String[] providers = config.static_maps_provider.split(",");
            for (int a = 0; a < providers.length; a++) {
                String[] mapArgs = providers[a].split("\\+");
                if (mapArgs.length > 0) {
                    String[] typeAndKey = mapArgs[0].split(":");
                    if (typeAndKey.length > 0) {
                        if ("yandex".equals(typeAndKey[0])) {
                            if (a == 0) {
                                if (mapArgs.length > 1) {
                                    mapProvider = 3;
                                } else {
                                    mapProvider = 1;
                                }
                            }
                            availableMapProviders |= 4;
                        } else if ("google".equals(typeAndKey[0])) {
                            if (a == 0) {
                                if (mapArgs.length > 1) {
                                    mapProvider = 4;
                                }
                            }
                            availableMapProviders |= 1;
                        } else if ("telegram".equals(typeAndKey[0])) {
                            if (a == 0) {
                                mapProvider = 2;
                            }
                            availableMapProviders |= 2;
                        }
                        if (typeAndKey.length > 1) {
                            mapKey = typeAndKey[1];
                        }
                    }
                }
            }

            SharedPreferences.Editor editor = mainPreferences.edit();
            editor.putInt("maxGroupCount", maxGroupCount);
            editor.putInt("maxMegagroupCount", maxMegagroupCount);
            editor.putInt("maxEditTime", maxEditTime);
            editor.putInt("ratingDecay", ratingDecay);
            editor.putInt("maxRecentGifsCount", maxRecentGifsCount);
            editor.putInt("maxRecentStickersCount", maxRecentStickersCount);
            editor.putInt("maxFaveStickersCount", maxFaveStickersCount);
            editor.putInt("callReceiveTimeout", callReceiveTimeout);
            editor.putInt("callRingTimeout", callRingTimeout);
            editor.putInt("callConnectTimeout", callConnectTimeout);
            editor.putInt("callPacketTimeout", callPacketTimeout);
            editor.putString("linkPrefix", linkPrefix);
            editor.putInt("maxPinnedDialogsCount", maxPinnedDialogsCount);
            editor.putInt("maxFolderPinnedDialogsCount", maxFolderPinnedDialogsCount);
            editor.putInt("maxMessageLength", maxMessageLength);
            editor.putInt("maxCaptionLength", maxCaptionLength);
            editor.putBoolean("preloadFeaturedStickers", preloadFeaturedStickers);
            editor.putInt("revokeTimeLimit", revokeTimeLimit);
            editor.putInt("revokeTimePmLimit", revokeTimePmLimit);
            editor.putInt("mapProvider", mapProvider);
            if (mapKey != null) {
                editor.putString("pk", mapKey);
            } else {
                editor.remove("pk");
            }
            editor.putBoolean("canRevokePmInbox", canRevokePmInbox);
            editor.putBoolean("blockedCountry", blockedCountry);
            editor.putString("venueSearchBot", venueSearchBot);
            editor.putString("gifSearchBot", gifSearchBot);
            editor.putString("imageSearchBot", imageSearchBot);
            editor.putString("dcDomainName2", dcDomainName);
            editor.putInt("webFileDatacenterId", webFileDatacenterId);
            editor.putString("suggestedLangCode", suggestedLangCode);
            editor.commit();

            LocaleController.getInstance().checkUpdateForCurrentRemoteLocale(currentAccount, config.lang_pack_version, config.base_lang_pack_version);
            getNotificationCenter().postNotificationName(NotificationCenter.configLoaded);
        });
    }

    public void addSupportUser() {
        TLRPC.TL_userForeign_old2 user = new TLRPC.TL_userForeign_old2();
        user.phone = "333";
        user.id = 333000;
        user.first_name = "Telegram";
        user.last_name = "";
        user.status = null;
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        putUser(user, true);

        user = new TLRPC.TL_userForeign_old2();
        user.phone = "42777";
        user.id = 777000;
        user.verified = true;
        user.first_name = "Telegram";
        user.last_name = "Notifications";
        user.status = null;
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        putUser(user, true);
    }

    public TLRPC.InputUser getInputUser(TLRPC.User user) {
        if (user == null) {
            return new TLRPC.TL_inputUserEmpty();
        }
        TLRPC.InputUser inputUser;
        if (user.id == getUserConfig().getClientUserId()) {
            inputUser = new TLRPC.TL_inputUserSelf();
        } else {
            inputUser = new TLRPC.TL_inputUser();
            inputUser.user_id = user.id;
            inputUser.access_hash = user.access_hash;
        }
        return inputUser;
    }

    public TLRPC.InputUser getInputUser(TLRPC.InputPeer peer) {
        if (peer == null) {
            return new TLRPC.TL_inputUserEmpty();
        }
        if (peer instanceof TLRPC.TL_inputPeerSelf) {
            return new TLRPC.TL_inputUserSelf();
        }
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = peer.user_id;
        inputUser.access_hash = peer.access_hash;
        return inputUser;
    }

    public TLRPC.InputUser getInputUser(long userId) {
        return getInputUser(getUser(userId));
    }

    public static TLRPC.InputChannel getInputChannel(TLRPC.Chat chat) {
        if (chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden) {
            TLRPC.InputChannel inputChat = new TLRPC.TL_inputChannel();
            inputChat.channel_id = chat.id;
            inputChat.access_hash = chat.access_hash;
            return inputChat;
        } else {
            return new TLRPC.TL_inputChannelEmpty();
        }
    }

    public static TLRPC.InputChannel getInputChannel(TLRPC.InputPeer peer) {
        TLRPC.TL_inputChannel inputChat = new TLRPC.TL_inputChannel();
        inputChat.channel_id = peer.channel_id;
        inputChat.access_hash = peer.access_hash;
        return inputChat;
    }

    public TLRPC.InputChannel getInputChannel(long chatId) {
        return getInputChannel(getChat(chatId));
    }

    public TLRPC.InputPeer getInputPeer(TLRPC.Peer peer) {
        TLRPC.InputPeer inputPeer;
        if (peer instanceof TLRPC.TL_peerChat) {
            inputPeer = new TLRPC.TL_inputPeerChat();
            inputPeer.chat_id = peer.chat_id;
        } else if (peer instanceof TLRPC.TL_peerChannel) {
            inputPeer = new TLRPC.TL_inputPeerChannel();
            inputPeer.channel_id = peer.channel_id;
            TLRPC.Chat chat = getChat(peer.channel_id);
            if (chat != null) {
                inputPeer.access_hash = chat.access_hash;
            }
        } else {
            inputPeer = new TLRPC.TL_inputPeerUser();
            inputPeer.user_id = peer.user_id;
            TLRPC.User user = getUser(peer.user_id);
            if (user != null) {
                inputPeer.access_hash = user.access_hash;
            }
        }
        return inputPeer;
    }

    public TLRPC.InputPeer getInputPeer(long id) {
        TLRPC.InputPeer inputPeer;
        if (id < 0) {
            TLRPC.Chat chat = getChat(-id);
            if (ChatObject.isChannel(chat)) {
                inputPeer = new TLRPC.TL_inputPeerChannel();
                inputPeer.channel_id = -id;
                inputPeer.access_hash = chat.access_hash;
            } else {
                inputPeer = new TLRPC.TL_inputPeerChat();
                inputPeer.chat_id = -id;
            }
        } else {
            TLRPC.User user = getUser(id);
            inputPeer = new TLRPC.TL_inputPeerUser();
            inputPeer.user_id = id;
            if (user != null) {
                inputPeer.access_hash = user.access_hash;
            }
        }
        return inputPeer;
    }

    public static TLRPC.InputPeer getInputPeer(TLRPC.Chat chat) {
        TLRPC.InputPeer inputPeer;
        if (ChatObject.isChannel(chat)) {
            inputPeer = new TLRPC.TL_inputPeerChannel();
            inputPeer.channel_id = chat.id;
            inputPeer.access_hash = chat.access_hash;
        } else {
            inputPeer = new TLRPC.TL_inputPeerChat();
            inputPeer.chat_id = chat.id;
        }
        return inputPeer;
    }

    public static TLRPC.InputPeer getInputPeer(TLRPC.User user) {
        TLRPC.InputPeer inputPeer = new TLRPC.TL_inputPeerUser();
        inputPeer.user_id = user.id;
        inputPeer.access_hash = user.access_hash;
        return inputPeer;
    }

    public TLRPC.Peer getPeer(long id) {
        TLRPC.Peer inputPeer;
        if (id < 0) {
            TLRPC.Chat chat = getChat(-id);
            if (chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden) {
                inputPeer = new TLRPC.TL_peerChannel();
                inputPeer.channel_id = -id;
            } else {
                inputPeer = new TLRPC.TL_peerChat();
                inputPeer.chat_id = -id;
            }
        } else {
            TLRPC.User user = getUser(id);
            inputPeer = new TLRPC.TL_peerUser();
            inputPeer.user_id = id;
        }
        return inputPeer;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploaded) {
            String location = (String) args[0];
            TLRPC.InputFile file = (TLRPC.InputFile) args[1];

            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                req.file = file;
                req.flags |= 1;
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.User user = getUser(getUserConfig().getClientUserId());
                        if (user == null) {
                            user = getUserConfig().getCurrentUser();
                            putUser(user, true);
                        } else {
                            getUserConfig().setCurrentUser(user);
                        }
                        if (user == null) {
                            return;
                        }
                        TLRPC.TL_photos_photo photo = (TLRPC.TL_photos_photo) response;
                        ArrayList<TLRPC.PhotoSize> sizes = photo.photo.sizes;
                        TLRPC.PhotoSize smallSize = FileLoader.getClosestPhotoSizeWithSize(sizes, 100);
                        TLRPC.PhotoSize bigSize = FileLoader.getClosestPhotoSizeWithSize(sizes, 1000);
                        user.photo = new TLRPC.TL_userProfilePhoto();
                        user.photo.photo_id = photo.photo.id;
                        if (smallSize != null) {
                            user.photo.photo_small = smallSize.location;
                        }
                        if (bigSize != null) {
                            user.photo.photo_big = bigSize.location;
                        }
                        getMessagesStorage().clearUserPhotos(user.id);
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        getMessagesStorage().putUsersAndChats(users, null, false, true);
                        AndroidUtilities.runOnUIThread(() -> {
                            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_AVATAR);
                            getUserConfig().saveConfig(true);
                        });
                    }
                });
            } else if (uploadingWallpaper != null && uploadingWallpaper.equals(location)) {
                TLRPC.TL_account_uploadWallPaper req = new TLRPC.TL_account_uploadWallPaper();
                req.file = file;
                req.mime_type = "image/jpeg";
                Theme.OverrideWallpaperInfo overrideWallpaperInfo = uploadingWallpaperInfo;
                TLRPC.TL_wallPaperSettings settings = new TLRPC.TL_wallPaperSettings();
                settings.blur = overrideWallpaperInfo.isBlurred;
                settings.motion = overrideWallpaperInfo.isMotion;
                req.settings = settings;
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    TLRPC.WallPaper wallPaper = (TLRPC.WallPaper) response;
                    File path = new File(ApplicationLoader.getFilesDirFixed(), overrideWallpaperInfo.originalFileName);
                    if (wallPaper != null) {
                        try {
                            AndroidUtilities.copyFile(path, FileLoader.getPathToAttach(wallPaper.document, true));
                        } catch (Exception ignore) {

                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (uploadingWallpaper != null && wallPaper != null) {
                            wallPaper.settings = settings;
                            wallPaper.flags |= 4;
                            overrideWallpaperInfo.slug = wallPaper.slug;
                            overrideWallpaperInfo.saveOverrideWallpaper();
                            ArrayList<TLRPC.WallPaper> wallpapers = new ArrayList<>();
                            wallpapers.add(wallPaper);
                            getMessagesStorage().putWallpapers(wallpapers, 2);
                            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 320);
                            if (image != null) {
                                String newKey = image.location.volume_id + "_" + image.location.local_id + "@100_100";
                                String oldKey = Utilities.MD5(path.getAbsolutePath()) + "@100_100";
                                ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForDocument(image, wallPaper.document), false);
                            }
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.wallpapersNeedReload, wallPaper.slug);
                        }
                    });
                });
            } else {
                Object object = uploadingThemes.get(location);
                Theme.ThemeInfo themeInfo;
                Theme.ThemeAccent accent;

                TLRPC.InputFile uploadedThumb;
                TLRPC.InputFile uploadedFile;
                if (object instanceof Theme.ThemeInfo) {
                    themeInfo = (Theme.ThemeInfo) object;
                    accent = null;
                    if (location.equals(themeInfo.uploadingThumb)) {
                        themeInfo.uploadedThumb = file;
                        themeInfo.uploadingThumb = null;
                    } else if (location.equals(themeInfo.uploadingFile)) {
                        themeInfo.uploadedFile = file;
                        themeInfo.uploadingFile = null;
                    }
                    uploadedThumb = themeInfo.uploadedThumb;
                    uploadedFile = themeInfo.uploadedFile;
                } else if (object instanceof Theme.ThemeAccent) {
                    accent = (Theme.ThemeAccent) object;
                    if (location.equals(accent.uploadingThumb)) {
                        accent.uploadedThumb = file;
                        accent.uploadingThumb = null;
                    } else if (location.equals(accent.uploadingFile)) {
                        accent.uploadedFile = file;
                        accent.uploadingFile = null;
                    }
                    themeInfo = accent.parentTheme;
                    uploadedThumb = accent.uploadedThumb;
                    uploadedFile = accent.uploadedFile;
                } else {
                    themeInfo = null;
                    accent = null;
                    uploadedThumb = null;
                    uploadedFile = null;
                }
                uploadingThemes.remove(location);

                if (uploadedFile != null && uploadedThumb != null) {
                    File f = new File(location);
                    TLRPC.TL_account_uploadTheme req = new TLRPC.TL_account_uploadTheme();
                    req.mime_type = "application/x-tgtheme-android";
                    req.file_name = "theme.attheme";
                    req.file = uploadedFile;
                    req.file.name = "theme.attheme";
                    req.thumb = uploadedThumb;
                    req.thumb.name = "theme-preview.jpg";
                    req.flags |= 1;
                    TLRPC.TL_theme info;
                    TLRPC.TL_inputThemeSettings settings;
                    if (accent != null) {
                        accent.uploadedFile = null;
                        accent.uploadedThumb = null;
                        info = accent.info;
                        settings = new TLRPC.TL_inputThemeSettings();
                        settings.base_theme = Theme.getBaseThemeByKey(themeInfo.name);
                        settings.accent_color = accent.accentColor;
                        if (accent.accentColor2 != 0) {
                            settings.flags |= 8;
                            settings.outbox_accent_color = accent.accentColor2;
                        }
                        if (accent.myMessagesAccentColor != 0) {
                            settings.message_colors.add(accent.myMessagesAccentColor);
                            settings.flags |= 1;
                            if (accent.myMessagesGradientAccentColor1 != 0) {
                                settings.message_colors.add(accent.myMessagesGradientAccentColor1);
                                if (accent.myMessagesGradientAccentColor2 != 0) {
                                    settings.message_colors.add(accent.myMessagesGradientAccentColor2);
                                    if (accent.myMessagesGradientAccentColor3 != 0) {
                                        settings.message_colors.add(accent.myMessagesGradientAccentColor3);
                                    }
                                }
                            }
                            settings.message_colors_animated = accent.myMessagesAnimated;
                        }
                        settings.flags |= 2;
                        settings.wallpaper_settings = new TLRPC.TL_wallPaperSettings();
                        if (!TextUtils.isEmpty(accent.patternSlug)) {
                            TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                            inputWallPaperSlug.slug = accent.patternSlug;
                            settings.wallpaper = inputWallPaperSlug;
                            settings.wallpaper_settings.intensity = (int) (accent.patternIntensity * 100);
                            settings.wallpaper_settings.flags |= 8;
                        } else {
                            TLRPC.TL_inputWallPaperNoFile inputWallPaperNoFile = new TLRPC.TL_inputWallPaperNoFile();
                            inputWallPaperNoFile.id = 0;
                            settings.wallpaper = inputWallPaperNoFile;
                        }
                        settings.wallpaper_settings.motion = accent.patternMotion;
                        if (accent.backgroundOverrideColor != 0) {
                            settings.wallpaper_settings.background_color = (int) accent.backgroundOverrideColor;
                            settings.wallpaper_settings.flags |= 1;
                        }
                        if (accent.backgroundGradientOverrideColor1 != 0) {
                            settings.wallpaper_settings.second_background_color = (int) accent.backgroundGradientOverrideColor1;
                            settings.wallpaper_settings.flags |= 16;
                            settings.wallpaper_settings.rotation = AndroidUtilities.getWallpaperRotation(accent.backgroundRotation, true);
                        }
                        if (accent.backgroundGradientOverrideColor2 != 0) {
                            settings.wallpaper_settings.third_background_color = (int) accent.backgroundGradientOverrideColor2;
                            settings.wallpaper_settings.flags |= 32;
                        }
                        if (accent.backgroundGradientOverrideColor3 != 0) {
                            settings.wallpaper_settings.fourth_background_color = (int) accent.backgroundGradientOverrideColor3;
                            settings.wallpaper_settings.flags |= 64;
                        }
                    } else {
                        themeInfo.uploadedFile = null;
                        themeInfo.uploadedThumb = null;
                        info = themeInfo.info;
                        settings = null;
                    }
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        String title = info != null ? info.title : themeInfo.getName();
                        int index = title.lastIndexOf(".attheme");
                        String n = index > 0 ? title.substring(0, index) : title;
                        if (response != null) {
                            TLRPC.Document document = (TLRPC.Document) response;
                            TLRPC.TL_inputDocument inputDocument = new TLRPC.TL_inputDocument();
                            inputDocument.access_hash = document.access_hash;
                            inputDocument.id = document.id;
                            inputDocument.file_reference = document.file_reference;
                            if (info == null || !info.creator) {
                                TLRPC.TL_account_createTheme req2 = new TLRPC.TL_account_createTheme();
                                req2.document = inputDocument;
                                req2.flags |= 4;
                                req2.slug = info != null && !TextUtils.isEmpty(info.slug) ? info.slug : "";
                                req2.title = n;
                                if (settings != null) {
                                    req2.settings = settings;
                                    req2.flags |= 8;
                                }
                                getConnectionsManager().sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (response1 instanceof TLRPC.TL_theme) {
                                        Theme.setThemeUploadInfo(themeInfo, accent, (TLRPC.TL_theme) response1, currentAccount, false);
                                        installTheme(themeInfo, accent, themeInfo == Theme.getCurrentNightTheme());
                                        getNotificationCenter().postNotificationName(NotificationCenter.themeUploadedToServer, themeInfo, accent);
                                    } else {
                                        getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, themeInfo, accent);
                                    }
                                }));
                            } else {
                                TLRPC.TL_account_updateTheme req2 = new TLRPC.TL_account_updateTheme();
                                TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
                                inputTheme.id = info.id;
                                inputTheme.access_hash = info.access_hash;
                                req2.theme = inputTheme;

                                req2.slug = info.slug;
                                req2.flags |= 1;

                                req2.title = n;
                                req2.flags |= 2;

                                req2.document = inputDocument;
                                req2.flags |= 4;

                                if (settings != null) {
                                    req2.settings = settings;
                                    req2.flags |= 8;
                                }

                                req2.format = "android";
                                getConnectionsManager().sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                    if (response1 instanceof TLRPC.TL_theme) {
                                        Theme.setThemeUploadInfo(themeInfo, accent, (TLRPC.TL_theme) response1, currentAccount, false);
                                        getNotificationCenter().postNotificationName(NotificationCenter.themeUploadedToServer, themeInfo, accent);
                                    } else {
                                        getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, themeInfo, accent);
                                    }
                                }));
                            }
                        } else {
                            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, themeInfo, accent));
                        }
                    });
                }
            }
        } else if (id == NotificationCenter.fileUploadFailed) {
            String location = (String) args[0];
            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                uploadingAvatar = null;
            } else if (uploadingWallpaper != null && uploadingWallpaper.equals(location)) {
                uploadingWallpaper = null;
                uploadingWallpaperInfo = null;
            } else {
                Object object = uploadingThemes.remove(location);
                if (object instanceof Theme.ThemeInfo) {
                    Theme.ThemeInfo themeInfo = (Theme.ThemeInfo) object;
                    themeInfo.uploadedFile = null;
                    themeInfo.uploadedThumb = null;
                    getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, themeInfo, null);
                } else if (object instanceof Theme.ThemeAccent) {
                    Theme.ThemeAccent accent = (Theme.ThemeAccent) object;
                    accent.uploadingThumb = null;
                    getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, accent.parentTheme, accent);
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Boolean scheduled = (Boolean) args[6];
            if (scheduled) {
                return;
            }
            Integer msgId = (Integer) args[0];
            Integer newMsgId = (Integer) args[1];
            Long did = (Long) args[3];
            MessageObject obj = dialogMessage.get(did);
            if (obj != null && (obj.getId() == msgId || obj.messageOwner.local_id == msgId)) {
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
            }
            TLRPC.Dialog dialog = dialogs_dict.get(did);
            if (dialog != null && dialog.top_message == msgId) {
                dialog.top_message = newMsgId;
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
            obj = dialogMessagesByIds.get(msgId);
            if (obj != null) {
                dialogMessagesByIds.remove(msgId);
                dialogMessagesByIds.put(newMsgId, obj);
            }
            if (DialogObject.isChatDialog(did)) {
                TLRPC.ChatFull chatFull = fullChats.get(-did);
                TLRPC.Chat chat = getChat(-did);
                if (chat != null && !ChatObject.hasAdminRights(chat) && chatFull != null && chatFull.slowmode_seconds != 0) {
                    chatFull.slowmode_next_send_date = getConnectionsManager().getCurrentTime() + chatFull.slowmode_seconds;
                    chatFull.flags |= 262144;
                    getMessagesStorage().updateChatInfo(chatFull, false);
                }
            }
        } else if (id == NotificationCenter.updateMessageMedia) {
            TLRPC.Message message = (TLRPC.Message) args[0];
            if (message.peer_id.channel_id == 0) {
                MessageObject existMessageObject = dialogMessagesByIds.get(message.id);
                if (existMessageObject != null) {
                    existMessageObject.messageOwner.media = message.media;
                    if (message.media.ttl_seconds != 0 && (message.media.photo instanceof TLRPC.TL_photoEmpty || message.media.document instanceof TLRPC.TL_documentEmpty)) {
                        existMessageObject.setType();
                        getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    }
                }
            }
        }
    }

    public void cleanup() {
        getContactsController().cleanup();
        MediaController.getInstance().cleanup();
        getNotificationsController().cleanup();
        getSendMessagesHelper().cleanup();
        getSecretChatHelper().cleanup();
        getLocationController().cleanup();
        getMediaDataController().cleanup();

        showFiltersTooltip = false;

        DialogsActivity.dialogsLoaded[currentAccount] = false;

        SharedPreferences.Editor editor = notificationsPreferences.edit();
        editor.clear().commit();
        editor = emojiPreferences.edit();
        editor.putLong("lastGifLoadTime", 0).putLong("lastStickersLoadTime", 0).putLong("lastStickersLoadTimeMask", 0).putLong("lastStickersLoadTimeFavs", 0).commit();
        editor = mainPreferences.edit();
        editor.remove("archivehint").remove("proximityhint").remove("archivehint_l").remove("gifhint").remove("reminderhint").remove("soundHint").remove("dcDomainName2").remove("webFileDatacenterId").remove("themehint").remove("showFiltersTooltip").commit();

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("shortcut_widget", Activity.MODE_PRIVATE);
        SharedPreferences.Editor widgetEditor = null;
        AppWidgetManager appWidgetManager = null;
        ArrayList<Integer> chatsWidgets = null;
        ArrayList<Integer> contactsWidgets = null;
        Map<String, ?> values = preferences.getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("account")) {
                Integer value = (Integer) entry.getValue();
                if (value == currentAccount) {
                    int widgetId = Utilities.parseInt(key);
                    if (widgetEditor == null) {
                        widgetEditor = preferences.edit();
                        appWidgetManager = AppWidgetManager.getInstance(ApplicationLoader.applicationContext);
                    }
                    widgetEditor.putBoolean("deleted" + widgetId, true);
                    if (preferences.getInt("type" + widgetId, 0) == EditWidgetActivity.TYPE_CHATS) {
                        if (chatsWidgets == null) {
                            chatsWidgets = new ArrayList<>();
                        }
                        chatsWidgets.add(widgetId);
                    } else {
                        if (contactsWidgets == null) {
                            contactsWidgets = new ArrayList<>();
                        }
                        contactsWidgets.add(widgetId);
                    }
                }
            }
        }
        if (widgetEditor != null) {
            widgetEditor.commit();
        }
        if (chatsWidgets != null) {
            for (int a = 0, N = chatsWidgets.size(); a < N; a++) {
                ChatsWidgetProvider.updateWidget(ApplicationLoader.applicationContext, appWidgetManager, chatsWidgets.get(a));
            }
        }
        if (contactsWidgets != null) {
            for (int a = 0, N = contactsWidgets.size(); a < N; a++) {
                ContactsWidgetProvider.updateWidget(ApplicationLoader.applicationContext, appWidgetManager, contactsWidgets.get(a));
            }
        }

        lastScheduledServerQueryTime.clear();
        lastServerQueryTime.clear();
        reloadingWebpages.clear();
        reloadingWebpagesPending.clear();
        reloadingScheduledWebpages.clear();
        reloadingScheduledWebpagesPending.clear();
        sponsoredMessages.clear();
        dialogs_dict.clear();
        dialogs_read_inbox_max.clear();
        loadingPinnedDialogs.clear();
        dialogs_read_outbox_max.clear();
        exportedChats.clear();
        fullUsers.clear();
        fullChats.clear();
        activeVoiceChatsMap.clear();
        loadingGroupCalls.clear();
        groupCallsByChatId.clear();
        dialogsByFolder.clear();
        unreadUnmutedDialogs = 0;
        joiningToChannels.clear();
        migratedChats.clear();
        channelViewsToSend.clear();
        pollsToCheck.clear();
        pollsToCheckSize = 0;
        dialogsServerOnly.clear();
        dialogsForward.clear();
        allDialogs.clear();
        dialogsLoadedTillDate = Integer.MAX_VALUE;
        dialogsCanAddUsers.clear();
        dialogsChannelsOnly.clear();
        dialogsGroupsOnly.clear();
        dialogsUsersOnly.clear();
        dialogsForBlock.clear();
        dialogMessagesByIds.clear();
        dialogMessagesByRandomIds.clear();
        channelAdmins.clear();
        loadingChannelAdmins.clear();
        users.clear();
        objectsByUsernames.clear();
        chats.clear();
        dialogMessage.clear();
        deletedHistory.clear();
        printingUsers.clear();
        printingStrings.clear();
        printingStringsTypes.clear();
        onlinePrivacy.clear();
        loadingPeerSettings.clear();
        deletingDialogs.clear();
        clearingHistoryDialogs.clear();
        lastPrintingStringCount = 0;
        selectedDialogFilter[0] = selectedDialogFilter[1] = null;
        dialogFilters.clear();
        dialogFiltersById.clear();
        loadingSuggestedFilters = false;
        loadingRemoteFilters = false;
        suggestedFilters.clear();
        gettingAppChangelog = false;
        dialogFiltersLoaded = false;
        ignoreSetOnline = false;

        Utilities.stageQueue.postRunnable(() -> {
            readTasks.clear();
            readTasksMap.clear();
            repliesReadTasks.clear();
            threadsReadTasksMap.clear();
            updatesQueueSeq.clear();
            updatesQueuePts.clear();
            updatesQueueQts.clear();
            gettingUnknownChannels.clear();
            gettingUnknownDialogs.clear();
            updatesStartWaitTimeSeq = 0;
            updatesStartWaitTimePts = 0;
            updatesStartWaitTimeQts = 0;
            createdDialogIds.clear();
            createdScheduledDialogIds.clear();
            gettingDifference = false;
            resetDialogsPinned = null;
            resetDialogsAll = null;
        });
        createdDialogMainThreadIds.clear();
        visibleDialogMainThreadIds.clear();
        visibleScheduledDialogMainThreadIds.clear();
        blockePeers.clear();
        for (int a = 0; a < sendingTypings.length; a++) {
            if (sendingTypings[a] == null) {
                continue;
            }
            sendingTypings[a].clear();
        }
        loadingFullUsers.clear();
        loadedFullUsers.clear();
        reloadingMessages.clear();
        loadingFullChats.clear();
        loadingFullParticipants.clear();
        loadedFullParticipants.clear();
        loadedFullChats.clear();

        dialogsLoaded = false;
        nextDialogsCacheOffset.clear();
        loadingDialogs.clear();
        dialogsEndReached.clear();
        serverDialogsEndReached.clear();

        loadingAppConfig = false;

        checkingTosUpdate = false;
        nextTosCheckTime = 0;
        nextPromoInfoCheckTime = 0;
        checkingPromoInfo = false;
        loadingUnreadDialogs = false;

        currentDeletingTaskTime = 0;
        currentDeletingTaskMids = null;
        currentDeletingTaskMediaMids = null;
        gettingNewDeleteTask = false;
        loadingBlockedPeers = false;
        totalBlockedCount = -1;
        blockedEndReached = false;
        firstGettingTask = false;
        updatingState = false;
        resetingDialogs = false;
        lastStatusUpdateTime = 0;
        offlineSent = false;
        registeringForPush = false;
        getDifferenceFirstSync = true;
        uploadingAvatar = null;
        uploadingWallpaper = null;
        uploadingWallpaperInfo = null;
        uploadingThemes.clear();
        gettingChatInviters.clear();
        statusRequest = 0;
        statusSettingState = 0;

        Utilities.stageQueue.postRunnable(() -> {
            getConnectionsManager().setIsUpdating(false);
            updatesQueueChannels.clear();
            updatesStartWaitTimeChannels.clear();
            gettingDifferenceChannels.clear();
            channelsPts.clear();
            shortPollChannels.clear();
            needShortPollChannels.clear();
            shortPollOnlines.clear();
            needShortPollOnlines.clear();
        });

        if (currentDeleteTaskRunnable != null) {
            Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            currentDeleteTaskRunnable = null;
        }

        addSupportUser();
        getNotificationCenter().postNotificationName(NotificationCenter.suggestedFiltersLoaded);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public TLRPC.User getUser(Long id) {
        if (id == 0) {
            return UserConfig.getInstance(currentAccount).getCurrentUser();
        }
        return users.get(id);
    }

    public TLObject getUserOrChat(String username) {
        if (username == null || username.length() == 0) {
            return null;
        }
        return objectsByUsernames.get(username.toLowerCase());
    }

    public ConcurrentHashMap<Long, TLRPC.User> getUsers() {
        return users;
    }

    public ConcurrentHashMap<Long, TLRPC.Chat> getChats() {
        return chats;
    }

    public TLRPC.Chat getChat(Long id) {
        return chats.get(id);
    }

    public TLRPC.EncryptedChat getEncryptedChat(Integer id) {
        return encryptedChats.get(id);
    }

    public TLRPC.EncryptedChat getEncryptedChatDB(int chatId, boolean created) {
        TLRPC.EncryptedChat chat = encryptedChats.get(chatId);
        if (chat == null || created && (chat instanceof TLRPC.TL_encryptedChatWaiting || chat instanceof TLRPC.TL_encryptedChatRequested)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            ArrayList<TLObject> result = new ArrayList<>();
            getMessagesStorage().getEncryptedChat(chatId, countDownLatch, result);
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (result.size() == 2) {
                chat = (TLRPC.EncryptedChat) result.get(0);
                TLRPC.User user = (TLRPC.User) result.get(1);
                putEncryptedChat(chat, false);
                putUser(user, true);
            }
        }
        return chat;
    }

    public boolean isDialogVisible(long dialogId, boolean scheduled) {
        return scheduled ? visibleScheduledDialogMainThreadIds.contains(dialogId) : visibleDialogMainThreadIds.contains(dialogId);
    }

    public void setLastVisibleDialogId(final long dialogId, boolean scheduled, boolean set) {
        ArrayList<Long> arrayList = scheduled ? visibleScheduledDialogMainThreadIds : visibleDialogMainThreadIds;
        if (set) {
            if (arrayList.contains(dialogId)) {
                return;
            }
            arrayList.add(dialogId);
        } else {
            arrayList.remove(dialogId);
        }
    }

    public void setLastCreatedDialogId(final long dialogId, boolean scheduled, boolean set) {
        if (!scheduled) {
            ArrayList<Long> arrayList = createdDialogMainThreadIds;
            if (set) {
                if (arrayList.contains(dialogId)) {
                    return;
                }
                arrayList.add(dialogId);
            } else {
                arrayList.remove(dialogId);

                SparseArray<MessageObject> array = pollsToCheck.get(dialogId);
                if (array != null) {
                    for (int a = 0, N = array.size(); a < N; a++) {
                        MessageObject object = array.valueAt(a);
                        object.pollVisibleOnScreen = false;
                    }
                }
            }
        }
        Utilities.stageQueue.postRunnable(() -> {
            ArrayList<Long> arrayList2 = scheduled ? createdScheduledDialogIds : createdDialogIds;
            if (set) {
                if (arrayList2.contains(dialogId)) {
                    return;
                }
                arrayList2.add(dialogId);
            } else {
                arrayList2.remove(dialogId);
            }
        });
    }

    public TLRPC.TL_chatInviteExported getExportedInvite(long chatId) {
        return exportedChats.get(chatId);
    }

    public boolean putUser(TLRPC.User user, boolean fromCache) {
        if (user == null) {
            return false;
        }
        fromCache = fromCache && user.id / 1000 != 333 && user.id != 777000;
        TLRPC.User oldUser = users.get(user.id);
        if (oldUser == user) {
            return false;
        }
        if (oldUser != null && !TextUtils.isEmpty(oldUser.username)) {
            objectsByUsernames.remove(oldUser.username.toLowerCase());
        }
        if (!TextUtils.isEmpty(user.username)) {
            objectsByUsernames.put(user.username.toLowerCase(), user);
        }
        if (user.min) {
            if (oldUser != null) {
                if (!fromCache) {
                    if (user.bot) {
                        if (user.username != null) {
                            oldUser.username = user.username;
                            oldUser.flags |= 8;
                        } else {
                            oldUser.flags = oldUser.flags & ~8;
                            oldUser.username = null;
                        }
                    }
                    if (user.apply_min_photo) {
                        if (user.photo != null) {
                            oldUser.photo = user.photo;
                            oldUser.flags |= 32;
                        } else {
                            oldUser.flags = oldUser.flags & ~32;
                            oldUser.photo = null;
                        }
                    }
                }
            } else {
                users.put(user.id, user);
            }
        } else {
            if (!fromCache) {
                users.put(user.id, user);
                if (user.id == getUserConfig().getClientUserId()) {
                    getUserConfig().setCurrentUser(user);
                    getUserConfig().saveConfig(true);
                }
                if (oldUser != null && user.status != null && oldUser.status != null && user.status.expires != oldUser.status.expires) {
                    return true;
                }
            } else if (oldUser == null) {
                users.put(user.id, user);
            } else if (oldUser.min) {
                if (oldUser.bot) {
                    if (oldUser.username != null) {
                        user.username = oldUser.username;
                        user.flags |= 8;
                    } else {
                        user.flags = user.flags & ~8;
                        user.username = null;
                    }
                }
                if (oldUser.apply_min_photo) {
                    if (oldUser.photo != null) {
                        user.photo = oldUser.photo;
                        user.flags |= 32;
                    } else {
                        user.flags = user.flags & ~32;
                        user.photo = null;
                    }
                }
                users.put(user.id, user);
            }
        }
        return false;
    }

    public void putUsers(ArrayList<TLRPC.User> users, boolean fromCache) {
        if (users == null || users.isEmpty()) {
            return;
        }
        boolean updateStatus = false;
        int count = users.size();
        for (int a = 0; a < count; a++) {
            TLRPC.User user = users.get(a);
            if (putUser(user, fromCache)) {
                updateStatus = true;
            }
        }
        if (updateStatus) {
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS));
        }
    }

    public void putChat(final TLRPC.Chat chat, boolean fromCache) {
        if (chat == null) {
            return;
        }
        TLRPC.Chat oldChat = chats.get(chat.id);
        if (oldChat == chat) {
            return;
        }
        if (oldChat != null && !TextUtils.isEmpty(oldChat.username)) {
            objectsByUsernames.remove(oldChat.username.toLowerCase());
        }
        if (!TextUtils.isEmpty(chat.username)) {
            objectsByUsernames.put(chat.username.toLowerCase(), chat);
        }
        if (chat.min) {
            if (oldChat != null) {
                if (!fromCache) {
                    oldChat.title = chat.title;
                    oldChat.photo = chat.photo;
                    oldChat.broadcast = chat.broadcast;
                    oldChat.verified = chat.verified;
                    oldChat.megagroup = chat.megagroup;
                    oldChat.call_not_empty = chat.call_not_empty;
                    oldChat.call_active = chat.call_active;
                    if (chat.default_banned_rights != null) {
                        oldChat.default_banned_rights = chat.default_banned_rights;
                        oldChat.flags |= 262144;
                    }
                    if (chat.admin_rights != null) {
                        oldChat.admin_rights = chat.admin_rights;
                        oldChat.flags |= 16384;
                    }
                    if (chat.banned_rights != null) {
                        oldChat.banned_rights = chat.banned_rights;
                        oldChat.flags |= 32768;
                    }
                    if (chat.username != null) {
                        oldChat.username = chat.username;
                        oldChat.flags |= 64;
                    } else {
                        oldChat.flags = oldChat.flags &~ 64;
                        oldChat.username = null;
                    }
                    if (chat.participants_count != 0) {
                        oldChat.participants_count = chat.participants_count;
                    }
                    addOrRemoveActiveVoiceChat(oldChat);
                }
            } else {
                chats.put(chat.id, chat);
                addOrRemoveActiveVoiceChat(chat);
            }
        } else {
            if (!fromCache) {
                if (oldChat != null) {
                    if (chat.version != oldChat.version) {
                        loadedFullChats.remove(chat.id);
                    }
                    if (oldChat.participants_count != 0 && chat.participants_count == 0) {
                        chat.participants_count = oldChat.participants_count;
                        chat.flags |= 131072;
                    }

                    int oldFlags = oldChat.banned_rights != null ? oldChat.banned_rights.flags : 0;
                    int newFlags = chat.banned_rights != null ? chat.banned_rights.flags : 0;
                    int oldFlags2 = oldChat.default_banned_rights != null ? oldChat.default_banned_rights.flags : 0;
                    int newFlags2 = chat.default_banned_rights != null ? chat.default_banned_rights.flags : 0;
                    oldChat.default_banned_rights = chat.default_banned_rights;
                    if (oldChat.default_banned_rights == null) {
                        oldChat.flags &=~ 262144;
                    } else {
                        oldChat.flags |= 262144;
                    }
                    oldChat.banned_rights = chat.banned_rights;
                    if (oldChat.banned_rights == null) {
                        oldChat.flags &=~ 32768;
                    } else {
                        oldChat.flags |= 32768;
                    }
                    oldChat.admin_rights = chat.admin_rights;
                    if (oldChat.admin_rights == null) {
                        oldChat.flags &=~ 16384;
                    } else {
                        oldChat.flags |= 16384;
                    }
                    if (oldFlags != newFlags || oldFlags2 != newFlags2) {
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.channelRightsUpdated, chat));
                    }
                }
                chats.put(chat.id, chat);
            } else if (oldChat == null) {
                chats.put(chat.id, chat);
            } else if (oldChat.min) {
                chat.title = oldChat.title;
                chat.photo = oldChat.photo;
                chat.broadcast = oldChat.broadcast;
                chat.verified = oldChat.verified;
                chat.megagroup = oldChat.megagroup;

                if (oldChat.default_banned_rights != null) {
                    chat.default_banned_rights = oldChat.default_banned_rights;
                    chat.flags |= 262144;
                }
                if (oldChat.admin_rights != null) {
                    chat.admin_rights = oldChat.admin_rights;
                    chat.flags |= 16384;
                }
                if (oldChat.banned_rights != null) {
                    chat.banned_rights = oldChat.banned_rights;
                    chat.flags |= 32768;
                }
                if (oldChat.username != null) {
                    chat.username = oldChat.username;
                    chat.flags |= 64;
                } else {
                    chat.flags = chat.flags &~ 64;
                    chat.username = null;
                }
                if (oldChat.participants_count != 0 && chat.participants_count == 0) {
                    chat.participants_count = oldChat.participants_count;
                    chat.flags |= 131072;
                }
                chats.put(chat.id, chat);
            }
            addOrRemoveActiveVoiceChat(chat);
        }
    }

    public void putChats(ArrayList<TLRPC.Chat> chats, boolean fromCache) {
        if (chats == null || chats.isEmpty()) {
            return;
        }
        int count = chats.size();
        for (int a = 0; a < count; a++) {
            TLRPC.Chat chat = chats.get(a);
            putChat(chat, fromCache);
        }
    }

    private void addOrRemoveActiveVoiceChat(TLRPC.Chat chat) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            AndroidUtilities.runOnUIThread(() -> addOrRemoveActiveVoiceChatInternal(chat));
        } else {
            addOrRemoveActiveVoiceChatInternal(chat);
        }
    }

    private void addOrRemoveActiveVoiceChatInternal(TLRPC.Chat chat) {
        TLRPC.Chat currentChat = activeVoiceChatsMap.get(chat.id);
        if (chat.call_active && chat.call_not_empty && chat.migrated_to == null && !ChatObject.isNotInChat(chat)) {
            if (currentChat != null) {
                return;
            }
            activeVoiceChatsMap.put(chat.id, chat);
            getNotificationCenter().postNotificationName(NotificationCenter.activeGroupCallsUpdated);
        } else {
            if (currentChat == null) {
                return;
            }
            activeVoiceChatsMap.remove(chat.id);
            getNotificationCenter().postNotificationName(NotificationCenter.activeGroupCallsUpdated);
        }
    }

    public ArrayList<Long> getActiveGroupCalls() {
        return new ArrayList<>(activeVoiceChatsMap.keySet());
    }

    public void setReferer(String referer) {
        if (referer == null) {
            return;
        }
        installReferer = referer;
        mainPreferences.edit().putString("installReferer", referer).commit();
    }

    public void putEncryptedChat(TLRPC.EncryptedChat encryptedChat, boolean fromCache) {
        if (encryptedChat == null) {
            return;
        }
        if (fromCache) {
            encryptedChats.putIfAbsent(encryptedChat.id, encryptedChat);
        } else {
            encryptedChats.put(encryptedChat.id, encryptedChat);
        }
    }

    public void putEncryptedChats(ArrayList<TLRPC.EncryptedChat> encryptedChats, boolean fromCache) {
        if (encryptedChats == null || encryptedChats.isEmpty()) {
            return;
        }
        int count = encryptedChats.size();
        for (int a = 0; a < count; a++) {
            TLRPC.EncryptedChat encryptedChat = encryptedChats.get(a);
            putEncryptedChat(encryptedChat, fromCache);
        }
    }

    public TLRPC.UserFull getUserFull(long uid) {
        return fullUsers.get(uid);
    }

    public TLRPC.ChatFull getChatFull(long chatId) {
        return fullChats.get(chatId);
    }

    public void putGroupCall(long chatId, ChatObject.Call call) {
        groupCalls.put(call.call.id, call);
        groupCallsByChatId.put(chatId, call);
        TLRPC.ChatFull chatFull = getChatFull(chatId);
        if (chatFull != null) {
            chatFull.call = call.getInputGroupCall();
        }
        getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, call.call.id, false);
        loadFullChat(chatId, 0, true);
    }

    public ChatObject.Call getGroupCall(long chatId, boolean load) {
        return getGroupCall(chatId, load, null);
    }

    public ChatObject.Call getGroupCall(long chatId, boolean load, Runnable onLoad) {
        TLRPC.ChatFull chatFull = getChatFull(chatId);
        if (chatFull == null || chatFull.call == null) {
            return null;
        }
        ChatObject.Call result = groupCalls.get(chatFull.call.id);
        if (result == null && load && !loadingGroupCalls.contains(chatId)) {
            loadingGroupCalls.add(chatId);
            if (chatFull.call != null) {
                TLRPC.TL_phone_getGroupCall req = new TLRPC.TL_phone_getGroupCall();
                req.call = chatFull.call;
                req.limit = 20;
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response != null) {
                        TLRPC.TL_phone_groupCall groupCall = (TLRPC.TL_phone_groupCall) response;
                        putUsers(groupCall.users, false);
                        putChats(groupCall.chats, false);

                        ChatObject.Call call = new ChatObject.Call();
                        call.setCall(getAccountInstance(), chatId, groupCall);
                        groupCalls.put(groupCall.call.id, call);
                        groupCallsByChatId.put(chatId, call);
                        getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, chatId, groupCall.call.id, false);
                        if (onLoad != null) {
                            onLoad.run();
                        }
                    }
                    loadingGroupCalls.remove(chatId);
                }));
            }
        }
        if (result != null && result.call instanceof TLRPC.TL_groupCallDiscarded) {
            return null;
        }
        return result;
    }

    public void cancelLoadFullUser(long userId) {
        loadingFullUsers.remove(userId);
    }

    public void cancelLoadFullChat(long chatId) {
        loadingFullChats.remove(chatId);
    }

    protected void clearFullUsers() {
        loadedFullUsers.clear();
        loadedFullChats.clear();
    }

    private void reloadDialogsReadValue(ArrayList<TLRPC.Dialog> dialogs, long did) {
        if (did == 0 && (dialogs == null || dialogs.isEmpty())) {
            return;
        }
        TLRPC.TL_messages_getPeerDialogs req = new TLRPC.TL_messages_getPeerDialogs();
        if (dialogs != null) {
            for (int a = 0; a < dialogs.size(); a++) {
                TLRPC.InputPeer inputPeer = getInputPeer(dialogs.get(a).id);
                if (inputPeer instanceof TLRPC.TL_inputPeerChannel && inputPeer.access_hash == 0) {
                    continue;
                }
                TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                inputDialogPeer.peer = inputPeer;
                req.peers.add(inputDialogPeer);
            }
        } else {
            TLRPC.InputPeer inputPeer = getInputPeer(did);
            if (inputPeer instanceof TLRPC.TL_inputPeerChannel && inputPeer.access_hash == 0) {
                return;
            }
            TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
            inputDialogPeer.peer = inputPeer;
            req.peers.add(inputDialogPeer);
        }
        if (req.peers.isEmpty()) {
            return;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                for (int a = 0; a < res.dialogs.size(); a++) {
                    TLRPC.Dialog dialog = res.dialogs.get(a);
                    DialogObject.initDialog(dialog);

                    Integer value = dialogs_read_inbox_max.get(dialog.id);
                    if (value == null) {
                        value = 0;
                    }
                    dialogs_read_inbox_max.put(dialog.id, Math.max(dialog.read_inbox_max_id, value));
                    if (value == 0) {
                        if (dialog.peer.channel_id != 0) {
                            TLRPC.TL_updateReadChannelInbox update = new TLRPC.TL_updateReadChannelInbox();
                            update.channel_id = dialog.peer.channel_id;
                            update.max_id = dialog.read_inbox_max_id;
                            arrayList.add(update);
                        } else {
                            TLRPC.TL_updateReadHistoryInbox update = new TLRPC.TL_updateReadHistoryInbox();
                            update.peer = dialog.peer;
                            update.max_id = dialog.read_inbox_max_id;
                            arrayList.add(update);
                        }
                    }

                    value = dialogs_read_outbox_max.get(dialog.id);
                    if (value == null) {
                        value = 0;
                    }
                    dialogs_read_outbox_max.put(dialog.id, Math.max(dialog.read_outbox_max_id, value));
                    if (dialog.read_outbox_max_id > value) {
                        if (dialog.peer.channel_id != 0) {
                            TLRPC.TL_updateReadChannelOutbox update = new TLRPC.TL_updateReadChannelOutbox();
                            update.channel_id = dialog.peer.channel_id;
                            update.max_id = dialog.read_outbox_max_id;
                            arrayList.add(update);
                        } else {
                            TLRPC.TL_updateReadHistoryOutbox update = new TLRPC.TL_updateReadHistoryOutbox();
                            update.peer = dialog.peer;
                            update.max_id = dialog.read_outbox_max_id;
                            arrayList.add(update);
                        }
                    }
                }
                if (!arrayList.isEmpty()) {
                    processUpdateArray(arrayList, null, null, false, 0);
                }
            }
        });
    }

    public TLRPC.ChannelParticipant getAdminInChannel(long uid, long chatId) {
        LongSparseArray<TLRPC.ChannelParticipant> array = channelAdmins.get(chatId);
        if (array == null) {
            return null;
        }
        return array.get(uid);
    }

    public String getAdminRank(long chatId, long uid) {
        LongSparseArray<TLRPC.ChannelParticipant> array = channelAdmins.get(chatId);
        if (array == null) {
            return null;
        }
        TLRPC.ChannelParticipant participant = array.get(uid);
        if (participant == null) {
            return null;
        }
        return participant.rank != null ? participant.rank : "";
    }

    public boolean isChannelAdminsLoaded(long chatId) {
        return channelAdmins.get(chatId) != null;
    }

    public void loadChannelAdmins(long chatId, boolean cache) {
        int loadTime = loadingChannelAdmins.get(chatId);
        if (SystemClock.elapsedRealtime() - loadTime < 60) {
            return;
        }
        loadingChannelAdmins.put(chatId, (int) (SystemClock.elapsedRealtime() / 1000));
        if (cache) {
            getMessagesStorage().loadChannelAdmins(chatId);
        } else {
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            req.channel = getInputChannel(chatId);
            req.limit = 100;
            req.filter = new TLRPC.TL_channelParticipantsAdmins();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_channels_channelParticipants) {
                    processLoadedAdminsResponse(chatId, (TLRPC.TL_channels_channelParticipants) response);
                }
            });
        }
    }

    public void processLoadedAdminsResponse(long chatId, TLRPC.TL_channels_channelParticipants participants) {
        LongSparseArray<TLRPC.ChannelParticipant> array1 = new LongSparseArray<>(participants.participants.size());
        for (int a = 0; a < participants.participants.size(); a++) {
            TLRPC.ChannelParticipant participant = participants.participants.get(a);
            array1.put(MessageObject.getPeerId(participant.peer), participant);
        }
        processLoadedChannelAdmins(array1, chatId, false);
    }

    public void processLoadedChannelAdmins(final LongSparseArray<TLRPC.ChannelParticipant> array, long chatId, boolean cache) {
        if (!cache) {
            getMessagesStorage().putChannelAdmins(chatId, array);
        }
        AndroidUtilities.runOnUIThread(() -> {
            channelAdmins.put(chatId, array);
            if (cache) {
                loadingChannelAdmins.delete(chatId);
                loadChannelAdmins(chatId, false);
                getNotificationCenter().postNotificationName(NotificationCenter.didLoadChatAdmins, chatId);
            }
        });
    }

    public void loadFullChat(long chatId, int classGuid, boolean force) {
        boolean loaded = loadedFullChats.contains(chatId);
        if (loadingFullChats.contains(chatId) || !force && loaded) {
            return;
        }
        loadingFullChats.add(chatId);
        TLObject request;
        long dialogId = -chatId;
        TLRPC.Chat chat = getChat(chatId);
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_getFullChannel req = new TLRPC.TL_channels_getFullChannel();
            req.channel = getInputChannel(chat);
            request = req;
            loadChannelAdmins(chatId, !loaded);
        } else {
            TLRPC.TL_messages_getFullChat req = new TLRPC.TL_messages_getFullChat();
            req.chat_id = chatId;
            request = req;
            if (dialogs_read_inbox_max.get(dialogId) == null || dialogs_read_outbox_max.get(dialogId) == null) {
                reloadDialogsReadValue(null, dialogId);
            }
        }
        int reqId = getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error == null) {
                TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull) response;
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                getMessagesStorage().updateChatInfo(res.full_chat, false);

                if (ChatObject.isChannel(chat)) {
                    Integer value = dialogs_read_inbox_max.get(dialogId);
                    if (value == null) {
                        value = getMessagesStorage().getDialogReadMax(false, dialogId);
                    }

                    dialogs_read_inbox_max.put(dialogId, Math.max(res.full_chat.read_inbox_max_id, value));
                    if (res.full_chat.read_inbox_max_id > value) {
                        ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                        TLRPC.TL_updateReadChannelInbox update = new TLRPC.TL_updateReadChannelInbox();
                        update.channel_id = chatId;
                        update.max_id = res.full_chat.read_inbox_max_id;
                        arrayList.add(update);
                        processUpdateArray(arrayList, null, null, false, 0);
                    }

                    value = dialogs_read_outbox_max.get(dialogId);
                    if (value == null) {
                        value = getMessagesStorage().getDialogReadMax(true, dialogId);
                    }
                    dialogs_read_outbox_max.put(dialogId, Math.max(res.full_chat.read_outbox_max_id, value));
                    if (res.full_chat.read_outbox_max_id > value) {
                        ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                        TLRPC.TL_updateReadChannelOutbox update = new TLRPC.TL_updateReadChannelOutbox();
                        update.channel_id = chatId;
                        update.max_id = res.full_chat.read_outbox_max_id;
                        arrayList.add(update);
                        processUpdateArray(arrayList, null, null, false, 0);
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.ChatFull old = fullChats.get(chatId);
                    if (old != null) {
                        res.full_chat.inviterId = old.inviterId;
                    }
                    fullChats.put(chatId, res.full_chat);
                    applyDialogNotificationsSettings(-chatId, res.full_chat.notify_settings);
                    for (int a = 0; a < res.full_chat.bot_info.size(); a++) {
                        TLRPC.BotInfo botInfo = res.full_chat.bot_info.get(a);
                        getMediaDataController().putBotInfo(-chatId, botInfo);
                    }
                    int index = blockePeers.indexOfKey(-chatId);
                    if (res.full_chat.blocked) {
                        if (index < 0) {
                            blockePeers.put(-chatId, 1);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    } else {
                        if (index >= 0) {
                            blockePeers.removeAt(index);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    }
                    exportedChats.put(chatId, res.full_chat.exported_invite);
                    loadingFullChats.remove(chatId);
                    loadedFullChats.add(chatId);

                    putUsers(res.users, false);
                    putChats(res.chats, false);
                    if (res.full_chat.stickerset != null) {
                        getMediaDataController().getGroupStickerSetById(res.full_chat.stickerset);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, res.full_chat, classGuid, false, true);
                    if ((res.full_chat.flags & 2048) != 0) {
                        TLRPC.Dialog dialog = dialogs_dict.get(-chatId);
                        if (dialog != null && dialog.folder_id != res.full_chat.folder_id) {
                            dialog.folder_id = res.full_chat.folder_id;
                            sortDialogs(null);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    checkChannelError(error.text, chatId);
                    loadingFullChats.remove(chatId);
                });
            }
        });
        if (classGuid != 0) {
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void loadFullUser(final TLRPC.User user, int classGuid, boolean force) {
        if (user == null || loadingFullUsers.contains(user.id) || !force && loadedFullUsers.contains(user.id)) {
            return;
        }
        loadingFullUsers.add(user.id);
        TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
        req.id = getInputUser(user);
        long dialogId = user.id;
        if (dialogs_read_inbox_max.get(dialogId) == null || dialogs_read_outbox_max.get(dialogId) == null) {
            reloadDialogsReadValue(null, dialogId);
        }
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.TL_users_userFull res = (TLRPC.TL_users_userFull) response;
                TLRPC.UserFull userFull = res.full_user;
                putUsers(res.users, false);
                putChats(res.chats, false);
                res.full_user.user = getUser(res.full_user.id);
                getMessagesStorage().updateUserInfo(userFull, false);

                AndroidUtilities.runOnUIThread(() -> {
                    savePeerSettings(userFull.user.id, userFull.settings, false);

                    applyDialogNotificationsSettings(user.id, userFull.notify_settings);
                    if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                        getMediaDataController().putBotInfo(user.id, userFull.bot_info);
                    }
                    int index = blockePeers.indexOfKey(user.id);
                    if (userFull.blocked) {
                        if (index < 0) {
                            blockePeers.put(user.id, 1);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    } else {
                        if (index >= 0) {
                            blockePeers.removeAt(index);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    }
                    fullUsers.put(user.id, userFull);
                    loadingFullUsers.remove(user.id);
                    loadedFullUsers.add(user.id);
                    String names = user.first_name + user.last_name + user.username;
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(userFull.user);
                    putUsers(users, false);
                    getMessagesStorage().putUsersAndChats(users, null, false, true);
                    if (!names.equals(userFull.user.first_name + userFull.user.last_name + userFull.user.username)) {
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_NAME);
                    }
                    if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                        getNotificationCenter().postNotificationName(NotificationCenter.botInfoDidLoad, userFull.bot_info, classGuid);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, user.id, userFull);
                    if ((userFull.flags & 2048) != 0) {
                        TLRPC.Dialog dialog = dialogs_dict.get(user.id);
                        if (dialog != null && dialog.folder_id != userFull.folder_id) {
                            dialog.folder_id = userFull.folder_id;
                            sortDialogs(null);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> loadingFullUsers.remove(user.id));
            }
        });
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void reloadMessages(ArrayList<Integer> mids, long dialogId, boolean scheduled) {
        if (mids.isEmpty()) {
            return;
        }
        TLObject request;
        ArrayList<Integer> result = new ArrayList<>();
        TLRPC.Chat chat;
        if (DialogObject.isChatDialog(dialogId)) {
            chat = getChat(-dialogId);
        } else {
            chat = null;
        }
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
            req.channel = getInputChannel(chat);
            req.id = result;
            request = req;
        } else {
            TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
            req.id = result;
            request = req;
        }
        ArrayList<Integer> arrayList = reloadingMessages.get(dialogId);
        for (int a = 0; a < mids.size(); a++) {
            Integer mid = mids.get(a);
            if (arrayList != null && arrayList.contains(mid)) {
                continue;
            }
            result.add(mid);
        }
        if (result.isEmpty()) {
            return;
        }
        if (arrayList == null) {
            arrayList = new ArrayList<>();
            reloadingMessages.put(dialogId, arrayList);
        }
        arrayList.addAll(result);
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error == null) {
                TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;

                LongSparseArray<TLRPC.User> usersLocal = new LongSparseArray<>();
                for (int a = 0; a < messagesRes.users.size(); a++) {
                    TLRPC.User u = messagesRes.users.get(a);
                    usersLocal.put(u.id, u);
                }
                LongSparseArray<TLRPC.Chat> chatsLocal = new LongSparseArray<>();
                for (int a = 0; a < messagesRes.chats.size(); a++) {
                    TLRPC.Chat c = messagesRes.chats.get(a);
                    chatsLocal.put(c.id, c);
                }

                Integer inboxValue = dialogs_read_inbox_max.get(dialogId);
                if (inboxValue == null) {
                    inboxValue = getMessagesStorage().getDialogReadMax(false, dialogId);
                    dialogs_read_inbox_max.put(dialogId, inboxValue);
                }

                Integer outboxValue = dialogs_read_outbox_max.get(dialogId);
                if (outboxValue == null) {
                    outboxValue = getMessagesStorage().getDialogReadMax(true, dialogId);
                    dialogs_read_outbox_max.put(dialogId, outboxValue);
                }

                ArrayList<MessageObject> objects = new ArrayList<>();
                for (int a = 0; a < messagesRes.messages.size(); a++) {
                    TLRPC.Message message = messagesRes.messages.get(a);
                    message.dialog_id = dialogId;
                    if (!scheduled) {
                        message.unread = (message.out ? outboxValue : inboxValue) < message.id;
                    }
                    objects.add(new MessageObject(currentAccount, message, usersLocal, chatsLocal, true, true));
                }

                ImageLoader.saveMessagesThumbs(messagesRes.messages);
                getMessagesStorage().putMessages(messagesRes, dialogId, -1, 0, false, scheduled);

                AndroidUtilities.runOnUIThread(() -> {
                    ArrayList<Integer> arrayList1 = reloadingMessages.get(dialogId);
                    if (arrayList1 != null) {
                        arrayList1.removeAll(result);
                        if (arrayList1.isEmpty()) {
                            reloadingMessages.remove(dialogId);
                        }
                    }
                    MessageObject dialogObj = dialogMessage.get(dialogId);
                    if (dialogObj != null) {
                        for (int a = 0; a < objects.size(); a++) {
                            MessageObject obj = objects.get(a);
                            if (dialogObj.getId() == obj.getId()) {
                                dialogMessage.put(dialogId, obj);
                                if (obj.messageOwner.peer_id.channel_id == 0) {
                                    MessageObject obj2 = dialogMessagesByIds.get(obj.getId());
                                    dialogMessagesByIds.remove(obj.getId());
                                    if (obj2 != null) {
                                        dialogMessagesByIds.put(obj2.getId(), obj2);
                                    }
                                }
                                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                                break;
                            }
                        }
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, objects);
                });
            }
        });
    }

    public void hidePeerSettingsBar(final long dialogId, TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (currentUser == null && currentChat == null) {
            return;
        }
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        editor.putInt("dialog_bar_vis3" + dialogId, 3);
        editor.remove("dialog_bar_invite" + dialogId);
        editor.commit();
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.TL_messages_hidePeerSettingsBar req = new TLRPC.TL_messages_hidePeerSettingsBar();
            if (currentUser != null) {
                req.peer = getInputPeer(currentUser.id);
            } else {
                req.peer = getInputPeer(-currentChat.id);
            }
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
    }

    public void reportSpam(final long dialogId, TLRPC.User currentUser, TLRPC.Chat currentChat, TLRPC.EncryptedChat currentEncryptedChat, boolean geo) {
        if (currentUser == null && currentChat == null && currentEncryptedChat == null) {
            return;
        }
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        editor.putInt("dialog_bar_vis3" + dialogId, 3);
        editor.commit();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            if (currentEncryptedChat == null || currentEncryptedChat.access_hash == 0) {
                return;
            }
            TLRPC.TL_messages_reportEncryptedSpam req = new TLRPC.TL_messages_reportEncryptedSpam();
            req.peer = new TLRPC.TL_inputEncryptedChat();
            req.peer.chat_id = currentEncryptedChat.id;
            req.peer.access_hash = currentEncryptedChat.access_hash;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } else {
            if (geo) {
                TLRPC.TL_account_reportPeer req = new TLRPC.TL_account_reportPeer();
                if (currentChat != null) {
                    req.peer = getInputPeer(-currentChat.id);
                } else if (currentUser != null) {
                    req.peer = getInputPeer(currentUser.id);
                }
                req.message = "";
                req.reason = new TLRPC.TL_inputReportReasonGeoIrrelevant();
                getConnectionsManager().sendRequest(req, (response, error) -> {

                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            } else {
                TLRPC.TL_messages_reportSpam req = new TLRPC.TL_messages_reportSpam();
                if (currentChat != null) {
                    req.peer = getInputPeer(-currentChat.id);
                } else if (currentUser != null) {
                    req.peer = getInputPeer(currentUser.id);
                }
                getConnectionsManager().sendRequest(req, (response, error) -> {

                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        }
    }

    private void savePeerSettings(long dialogId, TLRPC.TL_peerSettings settings, boolean update) {
        if (settings == null || notificationsPreferences.getInt("dialog_bar_vis3" + dialogId, 0) == 3) {
            return;
        }
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        boolean bar_hidden = !settings.report_spam && !settings.add_contact && !settings.block_contact && !settings.share_contact && !settings.report_geo && !settings.invite_members;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("peer settings loaded for " + dialogId + " add = " + settings.add_contact + " block = " + settings.block_contact + " spam = " + settings.report_spam + " share = " + settings.share_contact + " geo = " + settings.report_geo +  " hide = " + bar_hidden + " distance = " + settings.geo_distance + " invite = " + settings.invite_members);
        }
        editor.putInt("dialog_bar_vis3" + dialogId, bar_hidden ? 1 : 2);
        editor.putBoolean("dialog_bar_share" + dialogId, settings.share_contact);
        editor.putBoolean("dialog_bar_report" + dialogId, settings.report_spam);
        editor.putBoolean("dialog_bar_add" + dialogId, settings.add_contact);
        editor.putBoolean("dialog_bar_block" + dialogId, settings.block_contact);
        editor.putBoolean("dialog_bar_exception" + dialogId, settings.need_contacts_exception);
        editor.putBoolean("dialog_bar_location" + dialogId, settings.report_geo);
        editor.putBoolean("dialog_bar_archived" + dialogId, settings.autoarchived);
        editor.putBoolean("dialog_bar_invite" + dialogId, settings.invite_members);
        if (notificationsPreferences.getInt("dialog_bar_distance" + dialogId, -1) != -2) {
            if ((settings.flags & 64) != 0) {
                editor.putInt("dialog_bar_distance" + dialogId, settings.geo_distance);
            } else {
                editor.remove("dialog_bar_distance" + dialogId);
            }
        }
        editor.apply();
        getNotificationCenter().postNotificationName(NotificationCenter.peerSettingsDidLoad, dialogId);
    }

    public void loadPeerSettings(TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (currentUser == null && currentChat == null) {
            return;
        }
        long dialogId;
        if (currentUser != null) {
            dialogId = currentUser.id;
        } else {
            dialogId = -currentChat.id;
        }
        if (loadingPeerSettings.indexOfKey(dialogId) >= 0) {
            return;
        }
        loadingPeerSettings.put(dialogId, true);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("request spam button for " + dialogId);
        }
        int vis = notificationsPreferences.getInt("dialog_bar_vis3" + dialogId, 0);
        if (vis == 1 || vis == 3) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("dialog bar already hidden for " + dialogId);
            }
            return;
        }
        TLRPC.TL_messages_getPeerSettings req = new TLRPC.TL_messages_getPeerSettings();
        if (currentUser != null) {
            req.peer = getInputPeer(currentUser.id);
        } else {
            req.peer = getInputPeer(-currentChat.id);
        }
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingPeerSettings.remove(dialogId);
            if (response != null) {
                TLRPC.TL_messages_peerSettings res = (TLRPC.TL_messages_peerSettings) response;
                TLRPC.TL_peerSettings settings = res.settings;
                putUsers(res.users, false);
                putChats(res.chats, false);

                savePeerSettings(dialogId,  settings, false);
            }
        }));
    }

    protected void processNewChannelDifferenceParams(int pts, int pts_count, long channelId) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("processNewChannelDifferenceParams pts = " + pts + " pts_count = " + pts_count + " channeldId = " + channelId);
        }
        int channelPts = channelsPts.get(channelId);
        if (channelPts == 0) {
            channelPts = getMessagesStorage().getChannelPtsSync(channelId);
            if (channelPts == 0) {
                channelPts = 1;
            }
            channelsPts.put(channelId, channelPts);
        }
        if (channelPts + pts_count == pts) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("APPLY CHANNEL PTS");
            }
            channelsPts.put(channelId, pts);
            getMessagesStorage().saveChannelPts(channelId, pts);
        } else if (channelPts != pts) {
            long updatesStartWaitTime = updatesStartWaitTimeChannels.get(channelId);
            boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId, false);
            if (gettingDifferenceChannel || updatesStartWaitTime == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("ADD CHANNEL UPDATE TO QUEUE pts = " + pts + " pts_count = " + pts_count);
                }
                if (updatesStartWaitTime == 0) {
                    updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis());
                }
                UserActionUpdatesPts updates = new UserActionUpdatesPts();
                updates.pts = pts;
                updates.pts_count = pts_count;
                updates.chat_id = channelId;
                ArrayList<TLRPC.Updates> arrayList = updatesQueueChannels.get(channelId);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    updatesQueueChannels.put(channelId, arrayList);
                }
                arrayList.add(updates);
            } else {
                getChannelDifference(channelId);
            }
        }
    }

    public void processNewDifferenceParams(int seq, int pts, int date, int pts_count) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("processNewDifferenceParams seq = " + seq + " pts = " + pts + " date = " + date + " pts_count = " + pts_count);
        }
        if (pts != -1) {
            if (getMessagesStorage().getLastPtsValue() + pts_count == pts) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("APPLY PTS");
                }
                getMessagesStorage().setLastPtsValue(pts);
                getMessagesStorage().saveDiffParams(getMessagesStorage().getLastSeqValue(), getMessagesStorage().getLastPtsValue(), getMessagesStorage().getLastDateValue(), getMessagesStorage().getLastQtsValue());
            } else if (getMessagesStorage().getLastPtsValue() != pts) {
                if (gettingDifference || updatesStartWaitTimePts == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("ADD UPDATE TO QUEUE pts = " + pts + " pts_count = " + pts_count);
                    }
                    if (updatesStartWaitTimePts == 0) {
                        updatesStartWaitTimePts = System.currentTimeMillis();
                    }
                    UserActionUpdatesPts updates = new UserActionUpdatesPts();
                    updates.pts = pts;
                    updates.pts_count = pts_count;
                    updatesQueuePts.add(updates);
                } else {
                    getDifference();
                }
            }
        }
        if (seq != -1) {
            if (getMessagesStorage().getLastSeqValue() + 1 == seq) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("APPLY SEQ");
                }
                getMessagesStorage().setLastSeqValue(seq);
                if (date != -1) {
                    getMessagesStorage().setLastDateValue(date);
                }
                getMessagesStorage().saveDiffParams(getMessagesStorage().getLastSeqValue(), getMessagesStorage().getLastPtsValue(), getMessagesStorage().getLastDateValue(), getMessagesStorage().getLastQtsValue());
            } else if (getMessagesStorage().getLastSeqValue() != seq) {
                if (gettingDifference || updatesStartWaitTimeSeq == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimeSeq) <= 1500) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("ADD UPDATE TO QUEUE seq = " + seq);
                    }
                    if (updatesStartWaitTimeSeq == 0) {
                        updatesStartWaitTimeSeq = System.currentTimeMillis();
                    }
                    UserActionUpdatesSeq updates = new UserActionUpdatesSeq();
                    updates.seq = seq;
                    updatesQueueSeq.add(updates);
                } else {
                    getDifference();
                }
            }
        }
    }

    public void didAddedNewTask(int minDate, long dialogId, SparseArray<ArrayList<Integer>> mids) {
        Utilities.stageQueue.postRunnable(() -> {
            if (currentDeletingTaskMids == null && currentDeletingTaskMediaMids == null && !gettingNewDeleteTask || currentDeletingTaskTime != 0 && minDate < currentDeletingTaskTime) {
                getNewDeleteTask(null, null);
            }
        });
        if (mids != null) {
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didCreatedNewDeleteTask, dialogId, mids));
        }
    }

    public void getNewDeleteTask(LongSparseArray<ArrayList<Integer>> oldTask, LongSparseArray<ArrayList<Integer>> oldTaskMedia) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingNewDeleteTask = true;
            getMessagesStorage().getNewTask(oldTask, oldTaskMedia);
        });
    }

    private boolean checkDeletingTask(boolean runnable) {
        int currentServerTime = getConnectionsManager().getCurrentTime();

        if ((currentDeletingTaskMids != null || currentDeletingTaskMediaMids != null) && (runnable || currentDeletingTaskTime != 0 && currentDeletingTaskTime <= currentServerTime)) {
            currentDeletingTaskTime = 0;
            if (currentDeleteTaskRunnable != null && !runnable) {
                Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            }
            currentDeleteTaskRunnable = null;
            LongSparseArray<ArrayList<Integer>> task = currentDeletingTaskMids != null ? currentDeletingTaskMids.clone() : null;
            LongSparseArray<ArrayList<Integer>> taskMedia = currentDeletingTaskMediaMids != null ? currentDeletingTaskMediaMids.clone() : null;
            AndroidUtilities.runOnUIThread(() -> {
                if (task != null) {
                    for (int a = 0, N = task.size(); a < N; a++) {
                        ArrayList<Integer> mids = task.valueAt(a);
                        deleteMessages(mids, null, null, task.keyAt(a), true, false, !mids.isEmpty() && mids.get(0) > 0);
                    }
                }
                if (taskMedia != null) {
                    for (int a = 0, N = taskMedia.size(); a < N; a++) {
                        getMessagesStorage().emptyMessagesMedia(taskMedia.keyAt(a), taskMedia.valueAt(a));
                    }
                }
                Utilities.stageQueue.postRunnable(() -> {
                    getNewDeleteTask(task, taskMedia);
                    currentDeletingTaskTime = 0;
                    currentDeletingTaskMids = null;
                    currentDeletingTaskMediaMids = null;
                });
            });
            return true;
        }
        return false;
    }

    public void processLoadedDeleteTask(int taskTime, LongSparseArray<ArrayList<Integer>> task, LongSparseArray<ArrayList<Integer>> taskMedia) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingNewDeleteTask = false;
            if (task != null || taskMedia != null) {
                currentDeletingTaskTime = taskTime;
                currentDeletingTaskMids = task;
                currentDeletingTaskMediaMids = taskMedia;

                if (currentDeleteTaskRunnable != null) {
                    Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
                    currentDeleteTaskRunnable = null;
                }

                if (!checkDeletingTask(false)) {
                    currentDeleteTaskRunnable = () -> checkDeletingTask(true);
                    int currentServerTime = getConnectionsManager().getCurrentTime();
                    Utilities.stageQueue.postRunnable(currentDeleteTaskRunnable, (long) Math.abs(currentServerTime - currentDeletingTaskTime) * 1000);
                }
            } else {
                currentDeletingTaskTime = 0;
                currentDeletingTaskMids = null;
                currentDeletingTaskMediaMids = null;
            }
        });
    }

    public void loadDialogPhotos(long did, int count, int maxId, boolean fromCache, int classGuid) {
        if (fromCache) {
            getMessagesStorage().getDialogPhotos(did, count, maxId, classGuid);
        } else {
            if (did > 0) {
                TLRPC.User user = getUser(did);
                if (user == null) {
                    return;
                }
                TLRPC.TL_photos_getUserPhotos req = new TLRPC.TL_photos_getUserPhotos();
                req.limit = count;
                req.offset = 0;
                req.max_id = maxId;
                req.user_id = getInputUser(user);
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.photos_Photos res = (TLRPC.photos_Photos) response;
                        processLoadedUserPhotos(res, null, did, count, maxId, false, classGuid);
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            } else if (did < 0) {
                TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.filter = new TLRPC.TL_inputMessagesFilterChatPhotos();
                req.limit = count;
                req.offset_id = maxId;
                req.q = "";
                req.peer = getInputPeer(did);
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                        TLRPC.TL_photos_photos res = new TLRPC.TL_photos_photos();
                        ArrayList<TLRPC.Message> arrayList = new ArrayList<>();
                        res.count = messages.count;
                        res.users.addAll(messages.users);
                        for (int a = 0; a < messages.messages.size(); a++) {
                            TLRPC.Message message = messages.messages.get(a);
                            if (message.action == null || message.action.photo == null) {
                                continue;
                            }
                            res.photos.add(message.action.photo);
                            arrayList.add(message);
                        }
                        processLoadedUserPhotos(res, arrayList, did, count, maxId, false, classGuid);
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public void blockPeer(long id) {
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (id > 0) {
            user = getUser(id);
            if (user == null) {
                return;
            }
        } else {
            chat = getChat(-id);
            if (chat == null) {
                return;
            }
        }
        if (blockePeers.indexOfKey(id) >= 0) {
            return;
        }
        blockePeers.put(id, 1);
        if (user != null) {
            if (user.bot) {
                getMediaDataController().removeInline(id);
            } else {
                getMediaDataController().removePeer(id);
            }
        }
        if (totalBlockedCount >= 0) {
            totalBlockedCount++;
        }
        getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
        TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
        if (user != null) {
            req.id = getInputPeer(user);
        } else {
            req.id = getInputPeer(chat);
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public void setParticipantBannedRole(long chatId, TLRPC.User user, TLRPC.Chat chat, TLRPC.TL_chatBannedRights rights, boolean isChannel, BaseFragment parentFragment) {
        if (user == null && chat == null || rights == null) {
            return;
        }
        TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
        req.channel = getInputChannel(chatId);
        if (user != null) {
            req.participant = getInputPeer(user);
        } else {
            req.participant = getInputPeer(chat);
        }
        req.banned_rights = rights;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, parentFragment, req, isChannel));
            }
        });
    }

    public void setChannelSlowMode(long chatId, int seconds) {
        TLRPC.TL_channels_toggleSlowMode req = new TLRPC.TL_channels_toggleSlowMode();
        req.seconds = seconds;
        req.channel = getInputChannel(chatId);
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
            }
        });
    }

    public void setDefaultBannedRole(long chatId, TLRPC.TL_chatBannedRights rights, boolean isChannel, BaseFragment parentFragment) {
        if (rights == null) {
            return;
        }
        TLRPC.TL_messages_editChatDefaultBannedRights req = new TLRPC.TL_messages_editChatDefaultBannedRights();
        req.peer = getInputPeer(-chatId);
        req.banned_rights = rights;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, parentFragment, req, isChannel));
            }
        });
    }

    public void setUserAdminRole(long chatId, TLRPC.User user, TLRPC.TL_chatAdminRights rights, String rank, boolean isChannel, BaseFragment parentFragment, boolean addingNew) {
        if (user == null || rights == null) {
            return;
        }
        TLRPC.Chat chat = getChat(chatId);
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
            req.channel = getInputChannel(chat);
            req.user_id = getInputUser(user);
            req.admin_rights = rights;
            req.rank = rank;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    processUpdates((TLRPC.Updates) response, false);
                    AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
                } else {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, parentFragment, req, isChannel));
                }
            });
        } else {
            TLRPC.TL_messages_editChatAdmin req = new TLRPC.TL_messages_editChatAdmin();
            req.chat_id = chatId;
            req.user_id = getInputUser(user);
            req.is_admin = rights.change_info || rights.delete_messages || rights.ban_users || rights.invite_users || rights.pin_messages || rights.add_admins || rights.manage_call;
            RequestDelegate requestDelegate = (response, error) -> {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
                } else {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, parentFragment, req, false));
                }
            };
            if (req.is_admin && addingNew) {
                addUserToChat(chatId, user, 0, null, parentFragment, () -> getConnectionsManager().sendRequest(req, requestDelegate));
            } else {
                getConnectionsManager().sendRequest(req, requestDelegate);
            }
        }
    }

    public void unblockPeer(long id) {
        TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (id > 0) {
            user = getUser(id);
            if (user == null) {
                return;
            }
        } else {
            chat = getChat(-id);
            if (chat == null) {
                return;
            }
        }
        totalBlockedCount--;
        blockePeers.delete(id);
        if (user != null) {
            req.id = getInputPeer(user);
        } else {
            req.id = getInputPeer(chat);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public void getBlockedPeers(boolean reset) {
        if (!getUserConfig().isClientActivated() || loadingBlockedPeers) {
            return;
        }
        loadingBlockedPeers = true;
        TLRPC.TL_contacts_getBlocked req = new TLRPC.TL_contacts_getBlocked();
        req.offset = reset ? 0 : blockePeers.size();
        req.limit = reset ? 20 : 100;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.contacts_Blocked res = (TLRPC.contacts_Blocked) response;
                putUsers(res.users, false);
                putChats(res.chats, false);
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                if (reset) {
                    blockePeers.clear();
                }
                totalBlockedCount = Math.max(res.count, res.blocked.size());
                blockedEndReached = res.blocked.size() < req.limit;
                for (int a = 0, N = res.blocked.size(); a < N; a++) {
                    TLRPC.TL_peerBlocked blocked = res.blocked.get(a);
                    blockePeers.put(MessageObject.getPeerId(blocked.peer_id), 1);
                }
                loadingBlockedPeers = false;
                getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
            }
        }));
    }

    public void deleteUserPhoto(TLRPC.InputPhoto photo) {
        if (photo == null) {
            TLRPC.TL_photos_updateProfilePhoto req = new TLRPC.TL_photos_updateProfilePhoto();
            req.id = new TLRPC.TL_inputPhotoEmpty();
            getUserConfig().getCurrentUser().photo = new TLRPC.TL_userProfilePhotoEmpty();
            TLRPC.User user = getUser(getUserConfig().getClientUserId());
            if (user == null) {
                user = getUserConfig().getCurrentUser();
            }
            if (user == null) {
                return;
            }
            user.photo = getUserConfig().getCurrentUser().photo;
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL);
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_photos_photo photos_photo = (TLRPC.TL_photos_photo) response;
                    TLRPC.User user1 = getUser(getUserConfig().getClientUserId());
                    if (user1 == null) {
                        user1 = getUserConfig().getCurrentUser();
                        putUser(user1, false);
                    } else {
                        getUserConfig().setCurrentUser(user1);
                    }
                    if (user1 == null) {
                        return;
                    }
                    getMessagesStorage().clearUserPhotos(user1.id);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user1);
                    getMessagesStorage().putUsersAndChats(users, null, false, true);
                    if (photos_photo.photo instanceof TLRPC.TL_photo) {
                        user1.photo = new TLRPC.TL_userProfilePhoto();
                        user1.photo.has_video = !photos_photo.photo.video_sizes.isEmpty();
                        user1.photo.photo_id = photos_photo.photo.id;
                        user1.photo.photo_small = FileLoader.getClosestPhotoSizeWithSize(photos_photo.photo.sizes, 150).location;
                        user1.photo.photo_big = FileLoader.getClosestPhotoSizeWithSize(photos_photo.photo.sizes, 800).location;
                        user1.photo.dc_id = photos_photo.photo.dc_id;
                    } else {
                        user1.photo = new TLRPC.TL_userProfilePhotoEmpty();
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL);
                        getUserConfig().saveConfig(true);
                    });
                }
            });
        } else {
            TLRPC.TL_photos_deletePhotos req = new TLRPC.TL_photos_deletePhotos();
            req.id.add(photo);
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
    }

    public void processLoadedUserPhotos(final TLRPC.photos_Photos res, ArrayList<TLRPC.Message> messages, long did, int count, int maxId, boolean fromCache, int classGuid) {
        if (!fromCache) {
            getMessagesStorage().putUsersAndChats(res.users, null, true, true);
            getMessagesStorage().putDialogPhotos(did, res, messages);
        } else if (res == null || res.photos.isEmpty()) {
            loadDialogPhotos(did, count, maxId, false, classGuid);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            putUsers(res.users, fromCache);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogPhotosLoaded, did, count, fromCache, classGuid, res.photos, messages);
        });
    }

    public void uploadAndApplyUserAvatar(TLRPC.FileLocation location) {
        if (location == null) {
            return;
        }
        uploadingAvatar = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + location.volume_id + "_" + location.local_id + ".jpg";
        getFileLoader().uploadFile(uploadingAvatar, false, true, ConnectionsManager.FileTypePhoto);
    }

    public void saveTheme(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, boolean night, boolean unsave) {
        TLRPC.TL_theme info = accent != null ? accent.info : themeInfo.info;
        if (info != null) {
            TLRPC.TL_account_saveTheme req = new TLRPC.TL_account_saveTheme();
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.id = info.id;
            inputTheme.access_hash = info.access_hash;
            req.theme = inputTheme;
            req.unsave = unsave;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
            getConnectionsManager().resumeNetworkMaybe();
        }
        if (!unsave) {
            installTheme(themeInfo, accent, night);
        }
    }

    public void installTheme(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, boolean night) {
        TLRPC.TL_theme info = accent != null ? accent.info : themeInfo.info;
        String slug = accent != null ? accent.patternSlug : themeInfo.slug;
        boolean isBlured = accent == null && themeInfo.isBlured;
        boolean isMotion = accent != null ? accent.patternMotion : themeInfo.isMotion;

        TLRPC.TL_account_installTheme req = new TLRPC.TL_account_installTheme();
        req.dark = night;
        if (info != null) {
            req.format = "android";
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.id = info.id;
            inputTheme.access_hash = info.access_hash;
            req.theme = inputTheme;
            req.flags |= 2;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });

        if (!TextUtils.isEmpty(slug)) {
            TLRPC.TL_account_installWallPaper req2 = new TLRPC.TL_account_installWallPaper();
            TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
            inputWallPaperSlug.slug = slug;
            req2.wallpaper = inputWallPaperSlug;
            req2.settings = new TLRPC.TL_wallPaperSettings();
            req2.settings.blur = isBlured;
            req2.settings.motion = isMotion;
            getConnectionsManager().sendRequest(req2, (response, error) -> {

            });
        }
    }

    public void saveThemeToServer(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent) {
        if (themeInfo == null) {
            return;
        }
        String key;
        File pathToWallpaper;
        if (accent != null) {
            key = accent.saveToFile().getAbsolutePath();
            pathToWallpaper = accent.getPathToWallpaper();
        } else {
            key = themeInfo.pathToFile;
            pathToWallpaper = null;
        }
        if (key == null) {
            return;
        }
        if (uploadingThemes.containsKey(key)) {
            return;
        }
        uploadingThemes.put(key, accent != null ? accent : themeInfo);
        Utilities.globalQueue.postRunnable(() -> {
            String thumbPath = Theme.createThemePreviewImage(key, pathToWallpaper != null ? pathToWallpaper.getAbsolutePath() : null, accent);
            AndroidUtilities.runOnUIThread(() -> {
                if (thumbPath == null) {
                    uploadingThemes.remove(key);
                    return;
                }
                uploadingThemes.put(thumbPath, accent != null ? accent : themeInfo);
                if (accent == null) {
                    themeInfo.uploadingFile = key;
                    themeInfo.uploadingThumb = thumbPath;
                } else {
                    accent.uploadingFile = key;
                    accent.uploadingThumb = thumbPath;
                }
                getFileLoader().uploadFile(key, false, true, ConnectionsManager.FileTypeFile);
                getFileLoader().uploadFile(thumbPath, false, true, ConnectionsManager.FileTypePhoto);
            });
        });
    }

    public void saveWallpaperToServer(File path, Theme.OverrideWallpaperInfo info, boolean install, long taskId) {
        if (uploadingWallpaper != null) {
            File finalPath = new File(ApplicationLoader.getFilesDirFixed(), info.originalFileName);
            if (path != null && (path.getAbsolutePath().equals(uploadingWallpaper) || path.equals(finalPath))) {
                uploadingWallpaperInfo = info;
                return;
            }
            getFileLoader().cancelFileUpload(uploadingWallpaper, false);
            uploadingWallpaper = null;
            uploadingWallpaperInfo = null;
        }
        if (path != null) {
            uploadingWallpaper = path.getAbsolutePath();
            uploadingWallpaperInfo = info;
            getFileLoader().uploadFile(uploadingWallpaper, false, true, ConnectionsManager.FileTypePhoto);
        } else if (!info.isDefault() && !info.isColor() && info.wallpaperId > 0 && !info.isTheme()) {
            TLRPC.InputWallPaper inputWallPaper;
            if (info.wallpaperId > 0) {
                TLRPC.TL_inputWallPaper inputWallPaperId = new TLRPC.TL_inputWallPaper();
                inputWallPaperId.id = info.wallpaperId;
                inputWallPaperId.access_hash = info.accessHash;
                inputWallPaper = inputWallPaperId;
            } else {
                TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
                inputWallPaperSlug.slug = info.slug;
                inputWallPaper = inputWallPaperSlug;
            }

            TLRPC.TL_wallPaperSettings settings = new TLRPC.TL_wallPaperSettings();
            settings.blur = info.isBlurred;
            settings.motion = info.isMotion;
            if (info.color != 0) {
                settings.background_color = info.color & 0x00ffffff;
                settings.flags |= 1;
                settings.intensity = (int) (info.intensity * 100);
                settings.flags |= 8;
            }
            if (info.gradientColor1 != 0) {
                settings.second_background_color = info.gradientColor1 & 0x00ffffff;
                settings.rotation = AndroidUtilities.getWallpaperRotation(info.rotation, true);
                settings.flags |= 16;
            }
            if (info.gradientColor2 != 0) {
                settings.third_background_color = info.gradientColor2 & 0x00ffffff;
                settings.flags |= 32;
            }
            if (info.gradientColor3 != 0) {
                settings.fourth_background_color = info.gradientColor3 & 0x00ffffff;
                settings.flags |= 64;
            }

            TLObject req;
            if (install) {
                TLRPC.TL_account_installWallPaper request = new TLRPC.TL_account_installWallPaper();
                request.wallpaper = inputWallPaper;
                request.settings = settings;
                req = request;
            } else {
                TLRPC.TL_account_saveWallPaper request = new TLRPC.TL_account_saveWallPaper();
                request.wallpaper = inputWallPaper;
                request.settings = settings;
                req = request;
            }

            long newTaskId;
            if (taskId != 0) {
                newTaskId = taskId;
            } else {
                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(1024);
                    data.writeInt32(21);
                    data.writeBool(info.isBlurred);
                    data.writeBool(info.isMotion);
                    data.writeInt32(info.color);
                    data.writeInt32(info.gradientColor1);
                    data.writeInt32(info.rotation);
                    data.writeDouble(info.intensity);
                    data.writeBool(install);
                    data.writeString(info.slug);
                    data.writeString(info.originalFileName);
                    data.limit(data.position());
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = getMessagesStorage().createPendingTask(data);
            }

            getConnectionsManager().sendRequest(req, (response, error) -> getMessagesStorage().removePendingTask(newTaskId));
        }
        if ((info.isColor() || info.gradientColor2 != 0) && info.wallpaperId <= 0) {
            TLRPC.WallPaper wallPaper;
            if (info.isColor()) {
                wallPaper = new TLRPC.TL_wallPaperNoFile();
            } else {
                wallPaper = new TLRPC.TL_wallPaper();
                wallPaper.slug = info.slug;
                wallPaper.document = new TLRPC.TL_documentEmpty();
            }
            if (info.wallpaperId == 0) {
                wallPaper.id = Utilities.random.nextLong();
                if (wallPaper.id > 0) {
                    wallPaper.id = -wallPaper.id;
                }
            } else {
                wallPaper.id = info.wallpaperId;
            }
            wallPaper.dark = MotionBackgroundDrawable.isDark(info.color, info.gradientColor1, info.gradientColor2, info.gradientColor3);
            wallPaper.flags |= 4;
            wallPaper.settings = new TLRPC.TL_wallPaperSettings();
            wallPaper.settings.blur = info.isBlurred;
            wallPaper.settings.motion = info.isMotion;
            if (info.color != 0) {
                wallPaper.settings.background_color = info.color;
                wallPaper.settings.flags |= 1;
                wallPaper.settings.intensity = (int) (info.intensity * 100);
                wallPaper.settings.flags |= 8;
            }
            if (info.gradientColor1 != 0) {
                wallPaper.settings.second_background_color = info.gradientColor1;
                wallPaper.settings.rotation = AndroidUtilities.getWallpaperRotation(info.rotation, true);
                wallPaper.settings.flags |= 16;
            }
            if (info.gradientColor2 != 0) {
                wallPaper.settings.third_background_color = info.gradientColor2;
                wallPaper.settings.flags |= 32;
            }
            if (info.gradientColor3 != 0) {
                wallPaper.settings.fourth_background_color = info.gradientColor3;
                wallPaper.settings.flags |= 64;
            }
            ArrayList<TLRPC.WallPaper> arrayList = new ArrayList<>();
            arrayList.add(wallPaper);
            getMessagesStorage().putWallpapers(arrayList, -3);
            getMessagesStorage().getWallpapers();
        }
    }

    public void markDialogMessageAsDeleted(long dialogId, ArrayList<Integer> messages) {
        MessageObject obj = dialogMessage.get(dialogId);
        if (obj != null) {
            for (int a = 0; a < messages.size(); a++) {
                Integer id = messages.get(a);
                if (obj.getId() == id) {
                    obj.deleted = true;
                    break;
                }
            }
        }
    }

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, long dialogId, boolean forAll, boolean scheduled) {
        deleteMessages(messages, randoms, encryptedChat, dialogId, forAll, scheduled, false, 0, null);
    }

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, long dialogId, boolean forAll, boolean scheduled, boolean cacheOnly) {
        deleteMessages(messages, randoms, encryptedChat, dialogId, forAll, scheduled, cacheOnly, 0, null);
    }

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, long dialogId, boolean forAll, boolean scheduled, boolean cacheOnly, long taskId, TLObject taskRequest) {
        if ((messages == null || messages.isEmpty()) && taskId == 0) {
            return;
        }
        ArrayList<Integer> toSend = null;
        long channelId;
        if (taskId == 0) {
            if (dialogId != 0 && DialogObject.isChatDialog(dialogId)) {
                TLRPC.Chat chat = getChat(-dialogId);
                channelId = ChatObject.isChannel(chat) ? chat.id : 0;
            } else {
                channelId = 0;
            }
            if (!cacheOnly) {
                toSend = new ArrayList<>();
                for (int a = 0, N = messages.size(); a < N; a++) {
                    Integer mid = messages.get(a);
                    if (mid > 0) {
                        toSend.add(mid);
                    }
                }
            }
            if (scheduled) {
                getMessagesStorage().markMessagesAsDeleted(dialogId, messages, true, false, true);
            } else {
                if (channelId == 0) {
                    for (int a = 0; a < messages.size(); a++) {
                        Integer id = messages.get(a);
                        MessageObject obj = dialogMessagesByIds.get(id);
                        if (obj != null) {
                            obj.deleted = true;
                        }
                    }
                } else {
                    markDialogMessageAsDeleted(dialogId, messages);
                }
                getMessagesStorage().markMessagesAsDeleted(dialogId, messages, true, forAll, false);
                getMessagesStorage().updateDialogsWithDeletedMessages(dialogId, channelId, messages, null, true);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, messages, channelId, scheduled);
        } else {
            if (taskRequest instanceof TLRPC.TL_channels_deleteMessages) {
                channelId = ((TLRPC.TL_channels_deleteMessages) taskRequest).channel.channel_id;
            } else {
                channelId = 0;
            }
        }
        if (cacheOnly) {
            return;
        }

        long newTaskId;
        if (scheduled) {
            TLRPC.TL_messages_deleteScheduledMessages req;

            if (taskRequest instanceof TLRPC.TL_messages_deleteScheduledMessages) {
                req = (TLRPC.TL_messages_deleteScheduledMessages) taskRequest;
                newTaskId = taskId;
            } else {
                req = new TLRPC.TL_messages_deleteScheduledMessages();
                req.id = toSend;
                req.peer = getInputPeer(dialogId);

                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(12 + req.getObjectSize());
                    data.writeInt32(24);
                    data.writeInt64(dialogId);
                    req.serializeToStream(data);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = getMessagesStorage().createPendingTask(data);
            }

            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    processUpdates(updates, false);
                }
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            });
        } else if (channelId != 0) {
            TLRPC.TL_channels_deleteMessages req;
            if (taskRequest != null) {
                req = (TLRPC.TL_channels_deleteMessages) taskRequest;
                newTaskId = taskId;
            } else {
                req = new TLRPC.TL_channels_deleteMessages();
                req.id = toSend;
                req.channel = getInputChannel(channelId);

                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(12 + req.getObjectSize());
                    data.writeInt32(24);
                    data.writeInt64(dialogId);
                    req.serializeToStream(data);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = getMessagesStorage().createPendingTask(data);
            }

            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewChannelDifferenceParams(res.pts, res.pts_count, channelId);
                }
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            });
        } else {
            if (randoms != null && encryptedChat != null && !randoms.isEmpty()) {
                getSecretChatHelper().sendMessagesDeleteMessage(encryptedChat, randoms, null);
            }
            TLRPC.TL_messages_deleteMessages req;
            if (taskRequest instanceof TLRPC.TL_messages_deleteMessages) {
                req = (TLRPC.TL_messages_deleteMessages) taskRequest;
                newTaskId = taskId;
            } else {
                req = new TLRPC.TL_messages_deleteMessages();
                req.id = toSend;
                req.revoke = forAll;

                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(12 + req.getObjectSize());
                    data.writeInt32(24);
                    data.writeInt64(dialogId);
                    req.serializeToStream(data);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = getMessagesStorage().createPendingTask(data);
            }

            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            });
        }
    }

    public void unpinAllMessages(TLRPC.Chat chat, TLRPC.User user) {
        if (chat == null && user == null) {
            return;
        }
        TLRPC.TL_messages_unpinAllMessages req = new TLRPC.TL_messages_unpinAllMessages();
        req.peer = getInputPeer(chat != null ? -chat.id : user.id);
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                if (ChatObject.isChannel(chat)) {
                    processNewChannelDifferenceParams(res.pts, res.pts_count, chat.id);
                } else {
                    processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
                ArrayList<Integer> ids = new ArrayList<>();
                getMessagesStorage().updatePinnedMessages(chat != null ? -chat.id : user.id, null, false, 0, 0, false, null);
            }
        });
    }

    public void pinMessage(TLRPC.Chat chat, TLRPC.User user, int id, boolean unpin, boolean oneSide, boolean notify) {
        if (chat == null && user == null) {
            return;
        }
        TLRPC.TL_messages_updatePinnedMessage req = new TLRPC.TL_messages_updatePinnedMessage();
        req.peer = getInputPeer(chat != null ? -chat.id : user.id);
        req.id = id;
        req.unpin = unpin;
        req.silent = !notify;
        req.pm_oneside = oneSide;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(id);
                getMessagesStorage().updatePinnedMessages(chat != null ? -chat.id : user.id, ids, !unpin, -1, 0, false, null);
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
            }
        });
    }

    public void deleteUserChannelHistory(TLRPC.Chat chat, TLRPC.User user, int offset) {
        if (offset == 0) {
            getMessagesStorage().deleteUserChatHistory(-chat.id, user.id);
        }
        TLRPC.TL_channels_deleteParticipantHistory req = new TLRPC.TL_channels_deleteParticipantHistory();
        req.channel = getInputChannel(chat);
        req.participant = getInputPeer(user);
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                if (res.offset > 0) {
                    deleteUserChannelHistory(chat, user, res.offset);
                }
                processNewChannelDifferenceParams(res.pts, res.pts_count, chat.id);
            }
        });
    }

    public ArrayList<TLRPC.Dialog> getAllDialogs() {
        return allDialogs;
    }

    public boolean isDialogsEndReached(int folderId) {
        return dialogsEndReached.get(folderId);
    }

    public boolean isLoadingDialogs(int folderId) {
        return loadingDialogs.get(folderId);
    }

    public boolean isServerDialogsEndReached(int folderId) {
        return serverDialogsEndReached.get(folderId);
    }

    public boolean hasHiddenArchive() {
        return SharedConfig.archiveHidden && dialogs_dict.get(DialogObject.makeFolderDialogId(1)) != null;
    }

    public ArrayList<TLRPC.Dialog> getDialogs(int folderId) {
        ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.get(folderId);
        if (dialogs == null) {
            return new ArrayList<>();
        }
        return dialogs;
    }

    public int getTotalDialogsCount() {
        int count = 0;
        ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.get(0);
        if (dialogs != null) {
            count += dialogs.size();
        }
        return count;
    }

    public void putAllNeededDraftDialogs() {
        LongSparseArray<SparseArray<TLRPC.DraftMessage>> drafts = getMediaDataController().getDrafts();
        for (int i = 0, size = drafts.size(); i < size; i++) {
            SparseArray<TLRPC.DraftMessage> threads = drafts.valueAt(i);
            TLRPC.DraftMessage draftMessage = threads.get(0);
            if (draftMessage == null) {
                continue;
            }
            putDraftDialogIfNeed(drafts.keyAt(i), draftMessage);
        }
    }

    public void putDraftDialogIfNeed(long dialogId, TLRPC.DraftMessage draftMessage) {
        if (dialogs_dict.indexOfKey(dialogId) < 0) {
            MediaDataController mediaDataController = getMediaDataController();
            int dialogsCount = allDialogs.size();
            if (dialogsCount > 0) {
                TLRPC.Dialog dialog = allDialogs.get(dialogsCount - 1);
                long minDate = DialogObject.getLastMessageOrDraftDate(dialog, mediaDataController.getDraft(dialog.id, 0));
                if (draftMessage.date < minDate) {
                    return;
                }
            }
            TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
            dialog.id = dialogId;
            dialog.draft = draftMessage;
            dialog.folder_id = mediaDataController.getDraftFolderId(dialogId);
            dialog.flags = dialogId < 0 && ChatObject.isChannel(getChat(-dialogId)) ? 1 : 0;
            dialogs_dict.put(dialogId, dialog);
            allDialogs.add(dialog);
            sortDialogs(null);
        }
    }

    public void removeDraftDialogIfNeed(long dialogId) {
        TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
        if (dialog != null && dialog.top_message == 0) {
            dialogs_dict.remove(dialog.id);
            allDialogs.remove(dialog);
        }
    }

    private void removeDialog(TLRPC.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        long did = dialog.id;
        if (dialogsServerOnly.remove(dialog) && DialogObject.isChannel(dialog)) {
            Utilities.stageQueue.postRunnable(() -> {
                channelsPts.delete(-did);
                shortPollChannels.delete(-did);
                needShortPollChannels.delete(-did);
                shortPollOnlines.delete(-did);
                needShortPollOnlines.delete(-did);
            });
        }
        allDialogs.remove(dialog);
        dialogsCanAddUsers.remove(dialog);
        dialogsChannelsOnly.remove(dialog);
        dialogsGroupsOnly.remove(dialog);
        dialogsUsersOnly.remove(dialog);
        dialogsForBlock.remove(dialog);
        dialogsForward.remove(dialog);
        for (int a = 0; a < selectedDialogFilter.length; a++) {
            if (selectedDialogFilter[a] != null) {
                selectedDialogFilter[a].dialogs.remove(dialog);
            }
        }
        dialogs_dict.remove(did);

        ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.get(dialog.folder_id);
        if (dialogs != null) {
            dialogs.remove(dialog);
        }
    }

    public void hidePromoDialog() {
        if (promoDialog == null) {
            return;
        }
        TLRPC.TL_help_hidePromoData req = new TLRPC.TL_help_hidePromoData();
        req.peer = getInputPeer(promoDialog.id);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
        Utilities.stageQueue.postRunnable(() -> {
            promoDialogId = 0;
            proxyDialogAddress = null;
            nextPromoInfoCheckTime = getConnectionsManager().getCurrentTime() + 60 * 60;
            getGlobalMainSettings().edit().putLong("proxy_dialog", promoDialogId).remove("proxyDialogAddress").putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime).commit();
        });
        removePromoDialog();
    }

    public void deleteDialog(final long did, int onlyHistory) {
        deleteDialog(did, onlyHistory, false);
    }

    public void deleteDialog(final long did, int onlyHistory, boolean revoke) {
        deleteDialog(did, 1, onlyHistory, 0, revoke, null, 0);
    }

    public void setDialogHistoryTTL(long did, int ttl) {
        TLRPC.TL_messages_setHistoryTTL req = new TLRPC.TL_messages_setHistoryTTL();
        req.peer = getInputPeer(did);
        req.period = ttl;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
            }
        });
        TLRPC.ChatFull chatFull = null;
        TLRPC.UserFull userFull = null;
        if (did > 0) {
            userFull = getUserFull(did);
            if (userFull == null) {
                return;
            }
            userFull.ttl_period = ttl;
            userFull.flags |= 16384;
        } else {
            chatFull = getChatFull(-did);
            if (chatFull == null) {
                return;
            }
            chatFull.ttl_period = ttl;
            if (chatFull instanceof TLRPC.TL_channelFull) {
                chatFull.flags |= 16777216;
            } else {
                chatFull.flags |= 16384;
            }
        }
        if (chatFull != null) {
            getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
        } else {
            getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, did, userFull);
        }
    }

    public void setDialogsInTransaction(boolean transaction) {
        dialogsInTransaction = transaction;
        if (!transaction) {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        }
    }

    protected void deleteDialog(long did, int first, int onlyHistory, int max_id, boolean revoke, TLRPC.InputPeer peer, long taskId) {
        if (onlyHistory == 2) {
            getMessagesStorage().deleteDialog(did, onlyHistory);
            return;
        }
        if (first == 1 && max_id == 0) {
            TLRPC.InputPeer peerFinal = peer;
            getMessagesStorage().getDialogMaxMessageId(did, (param) -> {
                deleteDialog(did, 2, onlyHistory, Math.max(0, param), revoke, peerFinal, taskId);
                checkIfFolderEmpty(1);
            });
            return;
        }
        if (onlyHistory == 0 || onlyHistory == 3) {
            getMediaDataController().uninstallShortcut(did);
        }
        int max_id_delete = max_id;

        if (first != 0) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("delete dialog with id " + did);
            }
            boolean isPromoDialog = false;
            getMessagesStorage().deleteDialog(did, onlyHistory);
            TLRPC.Dialog dialog = dialogs_dict.get(did);
            if (onlyHistory == 0 || onlyHistory == 3) {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogDeleted, did);
                getNotificationsController().deleteNotificationChannel(did);
                JoinCallAlert.processDeletedChat(currentAccount, did);
            }
            if (onlyHistory == 0) {
                getMediaDataController().cleanDraft(did, 0, false);
            }
            if (dialog != null) {
                if (first == 2) {
                    max_id_delete = Math.max(0, dialog.top_message);
                    max_id_delete = Math.max(max_id_delete, dialog.read_inbox_max_id);
                    max_id_delete = Math.max(max_id_delete, dialog.read_outbox_max_id);
                }
                if (onlyHistory == 0 || onlyHistory == 3) {
                    if (isPromoDialog = (promoDialog != null && promoDialog.id == did)) {
                        isLeftPromoChannel = true;
                        if (promoDialog.id < 0) {
                            TLRPC.Chat chat = getChat(-promoDialog.id);
                            if (chat != null) {
                                chat.left = true;
                            }
                        }
                        sortDialogs(null);
                    } else {
                        removeDialog(dialog);
                        int offset = nextDialogsCacheOffset.get(dialog.folder_id, 0);
                        if (offset > 0) {
                            nextDialogsCacheOffset.put(dialog.folder_id, offset - 1);
                        }
                    }
                } else {
                    dialog.unread_count = 0;
                }
                if (!isPromoDialog) {
                    int lastMessageId;
                    MessageObject object = dialogMessage.get(dialog.id);
                    dialogMessage.remove(dialog.id);
                    if (object != null) {
                        lastMessageId = object.getId();
                        if (object.messageOwner.peer_id.channel_id == 0) {
                            dialogMessagesByIds.remove(object.getId());
                        }
                    } else {
                        lastMessageId = dialog.top_message;
                        object = dialogMessagesByIds.get(dialog.top_message);
                        if (object != null && object.messageOwner.peer_id.channel_id == 0) {
                            dialogMessagesByIds.remove(dialog.top_message);
                        }
                    }
                    if (object != null && object.messageOwner.random_id != 0) {
                        dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                    }
                    if (onlyHistory == 1 && !DialogObject.isEncryptedDialog(did) && lastMessageId > 0) {
                        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
                        message.id = dialog.top_message;
                        message.out = getUserConfig().getClientUserId() == did;
                        message.from_id = new TLRPC.TL_peerUser();
                        message.from_id.user_id = getUserConfig().getClientUserId();
                        message.flags |= 256;
                        message.action = new TLRPC.TL_messageActionHistoryClear();
                        message.date = dialog.last_message_date;
                        message.dialog_id = did;
                        message.peer_id = getPeer(did);
                        boolean isDialogCreated = createdDialogIds.contains(message.dialog_id);
                        MessageObject obj = new MessageObject(currentAccount, message, isDialogCreated, isDialogCreated);
                        ArrayList<MessageObject> objArr = new ArrayList<>();
                        objArr.add(obj);
                        ArrayList<TLRPC.Message> arr = new ArrayList<>();
                        arr.add(message);
                        updateInterfaceWithMessages(did, objArr, false);
                        getMessagesStorage().putMessages(arr, false, true, false, 0, false);
                    } else {
                        dialog.top_message = 0;
                    }
                }
            }
            if (first == 2) {
                Integer max = dialogs_read_inbox_max.get(did);
                if (max != null) {
                    max_id_delete = Math.max(max, max_id_delete);
                }
                max = dialogs_read_outbox_max.get(did);
                if (max != null) {
                    max_id_delete = Math.max(max, max_id_delete);
                }
            }

            if (!dialogsInTransaction) {
                if (isPromoDialog) {
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                } else {
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    getNotificationCenter().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, false, null);
                }
            }
            getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().removeNotificationsForDialog(did)));
        }

        if (onlyHistory == 3) {
            return;
        }

        if (!DialogObject.isEncryptedDialog(did)) {
            if (peer == null) {
                peer = getInputPeer(did);
            }
            if (peer == null) {
                return;
            }

            long newTaskId;
            if (!(peer instanceof TLRPC.TL_inputPeerChannel) || onlyHistory != 0) {
                if (max_id_delete > 0 && max_id_delete != Integer.MAX_VALUE) {
                    int current = deletedHistory.get(did, 0);
                    deletedHistory.put(did, Math.max(current, max_id_delete));
                }

                if (taskId == 0) {
                    NativeByteBuffer data = null;
                    try {
                        data = new NativeByteBuffer(4 + 8 + 4 + 4 + 4 + 4 + peer.getObjectSize());
                        data.writeInt32(13);
                        data.writeInt64(did);
                        data.writeBool(first != 0);
                        data.writeInt32(onlyHistory);
                        data.writeInt32(max_id_delete);
                        data.writeBool(revoke);
                        peer.serializeToStream(data);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    newTaskId = getMessagesStorage().createPendingTask(data);
                } else {
                    newTaskId = taskId;
                }
            } else {
                newTaskId = taskId;
            }

            if (peer instanceof TLRPC.TL_inputPeerChannel) {
                if (onlyHistory == 0) {
                    if (newTaskId != 0) {
                        getMessagesStorage().removePendingTask(newTaskId);
                    }
                    return;
                }
                TLRPC.TL_channels_deleteHistory req = new TLRPC.TL_channels_deleteHistory();
                req.channel = new TLRPC.TL_inputChannel();
                req.channel.channel_id = peer.channel_id;
                req.channel.access_hash = peer.access_hash;
                req.max_id = max_id_delete > 0 ? max_id_delete : Integer.MAX_VALUE;
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (newTaskId != 0) {
                        getMessagesStorage().removePendingTask(newTaskId);
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            } else {
                TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
                req.peer = peer;
                req.max_id = max_id_delete > 0 ? max_id_delete : Integer.MAX_VALUE;
                req.just_clear = onlyHistory != 0;
                req.revoke = revoke;
                int max_id_delete_final = max_id_delete;
                TLRPC.InputPeer peerFinal = peer;
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (newTaskId != 0) {
                        getMessagesStorage().removePendingTask(newTaskId);
                    }
                    if (error == null) {
                        TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                        if (res.offset > 0) {
                            deleteDialog(did, 0, onlyHistory, max_id_delete_final, revoke, peerFinal, 0);
                        }
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                        getMessagesStorage().onDeleteQueryComplete(did);
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            }
        } else {
            int encryptedId = DialogObject.getEncryptedChatId(did);
            if (onlyHistory == 1) {
                getSecretChatHelper().sendClearHistoryMessage(getEncryptedChat(encryptedId), null);
            } else {
                getSecretChatHelper().declineSecretChat(encryptedId, revoke);
            }
        }
    }

    public void saveGif(Object parentObject, TLRPC.Document document) {
        if (parentObject == null || !MessageObject.isGifDocument(document)) {
            return;
        }
        TLRPC.TL_messages_saveGif req = new TLRPC.TL_messages_saveGif();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.id.file_reference = document.file_reference;
        if (req.id.file_reference == null) {
            req.id.file_reference = new byte[0];
        }
        req.unsave = false;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null && FileRefController.isFileRefError(error.text)) {
                getFileRefController().requestReference(parentObject, req);
            }
        });
    }

    public void saveRecentSticker(Object parentObject, TLRPC.Document document, boolean asMask) {
        if (parentObject == null || document == null) {
            return;
        }
        TLRPC.TL_messages_saveRecentSticker req = new TLRPC.TL_messages_saveRecentSticker();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.id.file_reference = document.file_reference;
        if (req.id.file_reference == null) {
            req.id.file_reference = new byte[0];
        }
        req.unsave = false;
        req.attached = asMask;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null && FileRefController.isFileRefError(error.text)) {
                getFileRefController().requestReference(parentObject, req);
            }
        });
    }

    public void loadChannelParticipants(Long chatId) {
        if (loadingFullParticipants.contains(chatId) || loadedFullParticipants.contains(chatId)) {
            return;
        }
        loadingFullParticipants.add(chatId);

        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = getInputChannel(chatId);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = 0;
        req.limit = 32;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                putUsers(res.users, false);
                putChats(res.chats, false);
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                getMessagesStorage().updateChannelUsers(chatId, res.participants);
                loadedFullParticipants.add(chatId);
            }
            loadingFullParticipants.remove(chatId);
        }));
    }

    public void putChatFull(TLRPC.ChatFull chatFull) {
        fullChats.put(chatFull.id, chatFull);
    }

    public void processChatInfo(long chatId, TLRPC.ChatFull info, ArrayList<TLRPC.User> usersArr, boolean fromCache, boolean force, boolean byChannelUsers, ArrayList<Integer> pinnedMessages, HashMap<Integer, MessageObject> pinnedMessagesMap, int totalPinnedCount, boolean pinnedEndReached) {
        AndroidUtilities.runOnUIThread(() -> {
            if (fromCache && chatId > 0 && !byChannelUsers) {
                loadFullChat(chatId, 0, force);
            }
            if (info != null) {
                if (fullChats.get(chatId) == null) {
                    fullChats.put(chatId, info);
                }
                putUsers(usersArr, fromCache);
                if (info.stickerset != null) {
                    getMediaDataController().getGroupStickerSetById(info.stickerset);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, byChannelUsers, false);
            }
            if (pinnedMessages != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.pinnedInfoDidLoad, -chatId, pinnedMessages, pinnedMessagesMap, totalPinnedCount, pinnedEndReached);
            }
        });
    }

    public void loadUserInfo(TLRPC.User user, boolean force, int classGuid) {
        loadUserInfo(user, force, classGuid, 0);
    }

    public void loadUserInfo(TLRPC.User user, boolean force, int classGuid, int fromMessageId) {
        getMessagesStorage().loadUserInfo(user, force, classGuid, fromMessageId);
    }

    public void processUserInfo(TLRPC.User user, TLRPC.UserFull info, boolean fromCache, boolean force, int classGuid, ArrayList<Integer> pinnedMessages, HashMap<Integer, MessageObject> pinnedMessagesMap, int totalPinnedCount, boolean pinnedEndReached) {
        AndroidUtilities.runOnUIThread(() -> {
            if (fromCache) {
                loadFullUser(user, classGuid, force);
            }
            if (info != null) {
                if (fullUsers.get(user.id) == null) {
                    fullUsers.put(user.id, info);

                    int index = blockePeers.indexOfKey(user.id);
                    if (info.blocked) {
                        if (index < 0) {
                            blockePeers.put(user.id, 1);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    } else {
                        if (index >= 0) {
                            blockePeers.removeAt(index);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    }
                }
                getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, user.id, info);
            }
            if (pinnedMessages != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.pinnedInfoDidLoad, user.id, pinnedMessages, pinnedMessagesMap, totalPinnedCount, pinnedEndReached);
            }
        });
    }

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();

        checkDeletingTask(false);
        checkReadTasks();

        if (getUserConfig().isClientActivated()) {
            if (!ignoreSetOnline && getConnectionsManager().getPauseTime() == 0 && ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePausedStageQueue) {
                if (ApplicationLoader.mainInterfacePausedStageQueueTime != 0 && Math.abs(ApplicationLoader.mainInterfacePausedStageQueueTime - System.currentTimeMillis()) > 1000) {
                    if (statusSettingState != 1 && (lastStatusUpdateTime == 0 || Math.abs(System.currentTimeMillis() - lastStatusUpdateTime) >= 55000 || offlineSent)) {
                        statusSettingState = 1;

                        if (statusRequest != 0) {
                            getConnectionsManager().cancelRequest(statusRequest, true);
                        }

                        TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                        req.offline = false;
                        statusRequest = getConnectionsManager().sendRequest(req, (response, error) -> {
                            if (error == null) {
                                lastStatusUpdateTime = System.currentTimeMillis();
                                offlineSent = false;
                                statusSettingState = 0;
                            } else {
                                if (lastStatusUpdateTime != 0) {
                                    lastStatusUpdateTime += 5000;
                                }
                            }
                            statusRequest = 0;
                        });
                    }
                }
            } else if (statusSettingState != 2 && !offlineSent && Math.abs(System.currentTimeMillis() - getConnectionsManager().getPauseTime()) >= 2000) {
                statusSettingState = 2;
                if (statusRequest != 0) {
                    getConnectionsManager().cancelRequest(statusRequest, true);
                }
                TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                req.offline = true;
                statusRequest = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        offlineSent = true;
                    } else {
                        if (lastStatusUpdateTime != 0) {
                            lastStatusUpdateTime += 5000;
                        }
                    }
                    statusRequest = 0;
                });
            }

            if (updatesQueueChannels.size() != 0) {
                for (int a = 0; a < updatesQueueChannels.size(); a++) {
                    long key = updatesQueueChannels.keyAt(a);
                    long updatesStartWaitTime = updatesStartWaitTimeChannels.valueAt(a);
                    if (Math.abs(currentTime - updatesStartWaitTime) >= 1500) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("QUEUE CHANNEL " + key + " UPDATES WAIT TIMEOUT - CHECK QUEUE");
                        }
                        processChannelsUpdatesQueue(key, 0);
                    }
                }
            }

            for (int a = 0; a < 3; a++) {
                if (getUpdatesStartTime(a) != 0 && Math.abs(currentTime - getUpdatesStartTime(a)) >= 1500) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d(a + " QUEUE UPDATES WAIT TIMEOUT - CHECK QUEUE");
                    }
                    processUpdatesQueue(a, 0);
                }
            }
        }
        int currentServerTime = getConnectionsManager().getCurrentTime();
        if (Math.abs(System.currentTimeMillis() - lastViewsCheckTime) >= 5000) {
            lastViewsCheckTime = System.currentTimeMillis();
            if (channelViewsToSend.size() != 0) {
                for (int a = 0; a < channelViewsToSend.size(); a++) {
                    long key = channelViewsToSend.keyAt(a);
                    TLRPC.TL_messages_getMessagesViews req = new TLRPC.TL_messages_getMessagesViews();
                    req.peer = getInputPeer(key);
                    req.id = channelViewsToSend.valueAt(a);
                    req.increment = a == 0;
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        if (response != null) {
                            TLRPC.TL_messages_messageViews res = (TLRPC.TL_messages_messageViews) response;
                            LongSparseArray<SparseIntArray> channelViews = new LongSparseArray<>();
                            LongSparseArray<SparseIntArray> channelForwards = new LongSparseArray<>();
                            LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies = new LongSparseArray<>();
                            SparseIntArray views = channelViews.get(key);
                            SparseIntArray forwards = channelForwards.get(key);
                            SparseArray<TLRPC.MessageReplies> replies = channelReplies.get(key);

                            for (int a1 = 0; a1 < req.id.size(); a1++) {
                                if (a1 >= res.views.size()) {
                                    break;
                                }
                                TLRPC.TL_messageViews messageViews = res.views.get(a1);
                                if ((messageViews.flags & 1) != 0) {
                                    if (views == null) {
                                        views = new SparseIntArray();
                                        channelViews.put(key, views);
                                    }
                                    views.put(req.id.get(a1), messageViews.views);
                                }
                                if ((messageViews.flags & 2) != 0) {
                                    if (forwards == null) {
                                        forwards = new SparseIntArray();
                                        channelForwards.put(key, forwards);
                                    }
                                    forwards.put(req.id.get(a1), messageViews.forwards);
                                }
                                if ((messageViews.flags & 4) != 0) {
                                    if (replies == null) {
                                        replies = new SparseArray<>();
                                        channelReplies.put(key, replies);
                                    }
                                    replies.put(req.id.get(a1), messageViews.replies);
                                }
                            }
                            getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                            getMessagesStorage().putChannelViews(channelViews, channelForwards, channelReplies, false);
                            AndroidUtilities.runOnUIThread(() -> {
                                putUsers(res.users, false);
                                putChats(res.chats, false);
                                getNotificationCenter().postNotificationName(NotificationCenter.didUpdateMessagesViews, channelViews, channelForwards, channelReplies, false);
                            });
                        }
                    });
                }
                channelViewsToSend.clear();
            }
            if (pollsToCheckSize > 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    long time = SystemClock.elapsedRealtime();
                    int minExpireTime = Integer.MAX_VALUE;
                    for (int a = 0, N = pollsToCheck.size(); a < N; a++) {
                        SparseArray<MessageObject> array = pollsToCheck.valueAt(a);
                        if (array == null) {
                            continue;
                        }
                        for (int b = 0, N2 = array.size(); b < N2; b++) {
                            MessageObject messageObject = array.valueAt(b);
                            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                            int timeout = 30000;
                            boolean expired;
                            if (expired = (mediaPoll.poll.close_date != 0 && !mediaPoll.poll.closed)) {
                                if (mediaPoll.poll.close_date <= currentServerTime) {
                                    timeout = 1000;
                                } else {
                                    minExpireTime = Math.min(minExpireTime, mediaPoll.poll.close_date - currentServerTime);
                                }
                            }
                            if (Math.abs(time - messageObject.pollLastCheckTime) < timeout) {
                                if (!messageObject.pollVisibleOnScreen && !expired) {
                                    array.remove(messageObject.getId());
                                    N2--;
                                    b--;
                                }
                            } else {
                                messageObject.pollLastCheckTime = time;
                                TLRPC.TL_messages_getPollResults req = new TLRPC.TL_messages_getPollResults();
                                req.peer = getInputPeer(messageObject.getDialogId());
                                req.msg_id = messageObject.getId();
                                getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (error == null) {
                                        TLRPC.Updates updates = (TLRPC.Updates) response;
                                        if (expired) {
                                            for (int i = 0; i < updates.updates.size(); i++) {
                                                TLRPC.Update update = updates.updates.get(i);
                                                if (update instanceof TLRPC.TL_updateMessagePoll) {
                                                    TLRPC.TL_updateMessagePoll messagePoll = (TLRPC.TL_updateMessagePoll) update;
                                                    if (messagePoll.poll != null && !messagePoll.poll.closed) {
                                                        lastViewsCheckTime = System.currentTimeMillis() - 4000;
                                                    }
                                                }
                                            }
                                        }
                                        processUpdates(updates, false);
                                    }
                                });
                            }
                        }
                        if (minExpireTime < 5) {
                            lastViewsCheckTime = Math.min(lastViewsCheckTime, System.currentTimeMillis() - (5 - minExpireTime) * 1000);
                        }
                        if (array.size() == 0) {
                            pollsToCheck.remove(pollsToCheck.keyAt(a));
                            N--;
                            a--;
                        }
                    }
                    pollsToCheckSize = pollsToCheck.size();
                });
            }
        }
        if (!onlinePrivacy.isEmpty()) {
            ArrayList<Long> toRemove = null;
            for (ConcurrentHashMap.Entry<Long, Integer> entry : onlinePrivacy.entrySet()) {
                if (entry.getValue() < currentServerTime - 30) {
                    if (toRemove == null) {
                        toRemove = new ArrayList<>();
                    }
                    toRemove.add(entry.getKey());
                }
            }
            if (toRemove != null) {
                for (Long uid : toRemove) {
                    onlinePrivacy.remove(uid);
                }
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS));
            }
        }
        if (shortPollChannels.size() != 0) {
            for (int a = 0; a < shortPollChannels.size(); a++) {
                long key = shortPollChannels.keyAt(a);
                int timeout = shortPollChannels.valueAt(a);
                if (timeout < System.currentTimeMillis() / 1000) {
                    shortPollChannels.delete(key);
                    a--;
                    if (needShortPollChannels.indexOfKey(key) >= 0) {
                        getChannelDifference(key);
                    }
                }
            }
        }
        if (shortPollOnlines.size() != 0) {
            long time = SystemClock.elapsedRealtime() / 1000;
            for (int a = 0; a < shortPollOnlines.size(); a++) {
                long key = shortPollOnlines.keyAt(a);
                int timeout = shortPollOnlines.valueAt(a);
                if (timeout < time) {
                    if (needShortPollChannels.indexOfKey(key) >= 0) {
                        shortPollOnlines.put(key, (int) (time + 60 * 5));
                    } else {
                        shortPollOnlines.delete(key);
                        a--;
                    }
                    TLRPC.TL_messages_getOnlines req = new TLRPC.TL_messages_getOnlines();
                    req.peer = getInputPeer(-key);
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        if (response != null) {
                            TLRPC.TL_chatOnlines res = (TLRPC.TL_chatOnlines) response;
                            getMessagesStorage().updateChatOnlineCount(key, res.onlines);
                            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.chatOnlineCountDidLoad, key, res.onlines));
                        }
                    });
                }
            }
        }
        if (!printingUsers.isEmpty() || lastPrintingStringCount != printingUsers.size()) {
            boolean updated = false;
            ArrayList<Long> dialogKeys = new ArrayList<>(printingUsers.keySet());
            for (int b = 0; b < dialogKeys.size(); b++) {
                Long dialogKey = dialogKeys.get(b);
                ConcurrentHashMap<Integer, ArrayList<PrintingUser>> threads = printingUsers.get(dialogKey);
                if (threads != null) {
                    ArrayList<Integer> threadKeys = new ArrayList<>(threads.keySet());
                    for (int c = 0; c < threadKeys.size(); c++) {
                        Integer threadKey = threadKeys.get(c);
                        ArrayList<PrintingUser> arr = threads.get(threadKey);
                        if (arr != null) {
                            for (int a = 0; a < arr.size(); a++) {
                                PrintingUser user = arr.get(a);
                                int timeToRemove;
                                if (user.action instanceof TLRPC.TL_sendMessageGamePlayAction) {
                                    timeToRemove = 30000;
                                } else {
                                    timeToRemove = 5900;
                                }
                                if (user.lastTime + timeToRemove < currentTime) {
                                    updated = true;
                                    arr.remove(user);
                                    a--;
                                }
                            }
                        }
                        if (arr == null || arr.isEmpty()) {
                            threads.remove(threadKey);
                            threadKeys.remove(c);
                            c--;
                        }
                    }
                }
                if (threads == null || threads.isEmpty()) {
                    printingUsers.remove(dialogKey);
                    dialogKeys.remove(b);
                    b--;
                }
            }

            updatePrintingStrings();

            if (updated) {
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT));
            }
        }
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED && Math.abs(currentTime - lastThemeCheckTime) >= 60) {
            AndroidUtilities.runOnUIThread(themeCheckRunnable);
            lastThemeCheckTime = currentTime;
        }
        if (getUserConfig().savedPasswordHash != null && Math.abs(currentTime - lastPasswordCheckTime) >= 60) {
            AndroidUtilities.runOnUIThread(passwordCheckRunnable);
            lastPasswordCheckTime = currentTime;
        }
        if (lastPushRegisterSendTime != 0 && Math.abs(SystemClock.elapsedRealtime() - lastPushRegisterSendTime) >= 3 * 60 * 60 * 1000) {
            GcmPushListenerService.sendRegistrationToServer(SharedConfig.pushString);
        }
        getLocationController().update();
        checkPromoInfoInternal(false);
        checkTosUpdate();
    }

    private void checkTosUpdate() {
        if (nextTosCheckTime > getConnectionsManager().getCurrentTime() || checkingTosUpdate || !getUserConfig().isClientActivated()) {
            return;
        }
        checkingTosUpdate = true;
        TLRPC.TL_help_getTermsOfServiceUpdate req = new TLRPC.TL_help_getTermsOfServiceUpdate();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            checkingTosUpdate = false;
            if (response instanceof TLRPC.TL_help_termsOfServiceUpdateEmpty) {
                TLRPC.TL_help_termsOfServiceUpdateEmpty res = (TLRPC.TL_help_termsOfServiceUpdateEmpty) response;
                nextTosCheckTime = res.expires;
            } else if (response instanceof TLRPC.TL_help_termsOfServiceUpdate) {
                TLRPC.TL_help_termsOfServiceUpdate res = (TLRPC.TL_help_termsOfServiceUpdate) response;
                nextTosCheckTime = res.expires;
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.needShowAlert, 4, res.terms_of_service));
            } else {
                nextTosCheckTime = getConnectionsManager().getCurrentTime() + 60 * 60;
            }
            notificationsPreferences.edit().putInt("nextTosCheckTime", nextTosCheckTime).commit();
        });
    }

    public void checkPromoInfo(final boolean reset) {
        Utilities.stageQueue.postRunnable(() -> checkPromoInfoInternal(reset));
    }

    private void checkPromoInfoInternal(boolean reset) {
        if (reset && checkingPromoInfo) {
            checkingPromoInfo = false;
        }
        if (!reset && nextPromoInfoCheckTime > getConnectionsManager().getCurrentTime() || checkingPromoInfo) {
            return;
        }
        if (checkingPromoInfoRequestId != 0) {
            getConnectionsManager().cancelRequest(checkingPromoInfoRequestId, true);
            checkingPromoInfoRequestId = 0;
        }
        SharedPreferences preferences = getGlobalMainSettings();
        boolean enabled = preferences.getBoolean("proxy_enabled", false);
        String proxyAddress = preferences.getString("proxy_ip", "");
        String proxySecret = preferences.getString("proxy_secret", "");
        int removeCurrent = 0;
        if (promoDialogId != 0 && promoDialogType == PROMO_TYPE_PROXY && proxyDialogAddress != null && !proxyDialogAddress.equals(proxyAddress + proxySecret)) {
            removeCurrent = 1;
        }
        lastCheckPromoId++;

        checkingPromoInfo = true;
        int checkPromoId = lastCheckPromoId;
        TLRPC.TL_help_getPromoData req = new TLRPC.TL_help_getPromoData();
        checkingPromoInfoRequestId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (checkPromoId != lastCheckPromoId) {
                return;
            }
            boolean noDialog = false;
            if (response instanceof TLRPC.TL_help_promoDataEmpty) {
                TLRPC.TL_help_promoDataEmpty res = (TLRPC.TL_help_promoDataEmpty) response;
                nextPromoInfoCheckTime = res.expires;
                noDialog = true;
            } else if (response instanceof TLRPC.TL_help_promoData) {
                TLRPC.TL_help_promoData res = (TLRPC.TL_help_promoData) response;

                long did;
                if (res.peer.user_id != 0) {
                    did = res.peer.user_id;
                } else if (res.peer.chat_id != 0) {
                    did = -res.peer.chat_id;
                    for (int a = 0; a < res.chats.size(); a++) {
                        TLRPC.Chat chat = res.chats.get(a);
                        if (chat.id == res.peer.chat_id) {
                            if (chat.kicked || chat.restricted) {
                                noDialog = true;
                            }
                            break;
                        }
                    }
                } else {
                    did = -res.peer.channel_id;
                    for (int a = 0; a < res.chats.size(); a++) {
                        TLRPC.Chat chat = res.chats.get(a);
                        if (chat.id == res.peer.channel_id) {
                            if (chat.kicked || chat.restricted) {
                                noDialog = true;
                            }
                            break;
                        }
                    }
                }
                promoDialogId = did;
                if (res.proxy) {
                    promoDialogType = PROMO_TYPE_PROXY;
                } else if (!TextUtils.isEmpty(res.psa_type)) {
                    promoDialogType = PROMO_TYPE_PSA;
                    promoPsaType = res.psa_type;
                } else {
                    promoDialogType = PROMO_TYPE_OTHER;
                }
                proxyDialogAddress = proxyAddress + proxySecret;
                promoPsaMessage = res.psa_message;
                nextPromoInfoCheckTime = res.expires;
                SharedPreferences.Editor editor = getGlobalMainSettings().edit();
                editor.putLong("proxy_dialog", promoDialogId);
                editor.putString("proxyDialogAddress", proxyDialogAddress);
                editor.putInt("promo_dialog_type", promoDialogType);
                if (promoPsaMessage != null) {
                    editor.putString("promo_psa_message", promoPsaMessage);
                } else {
                    editor.remove("promo_psa_message");
                }
                if (promoPsaType != null) {
                    editor.putString("promo_psa_type", promoPsaType);
                } else {
                    editor.remove("promo_psa_type");
                }
                editor.putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime);
                editor.commit();

                if (!noDialog) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (promoDialog != null && did != promoDialog.id) {
                            removePromoDialog();
                        }
                        promoDialog = dialogs_dict.get(did);

                        if (promoDialog != null) {
                            checkingPromoInfo = false;
                            sortDialogs(null);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                        } else {
                            LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
                            LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
                            for (int a = 0; a < res.users.size(); a++) {
                                TLRPC.User u = res.users.get(a);
                                usersDict.put(u.id, u);
                            }
                            for (int a = 0; a < res.chats.size(); a++) {
                                TLRPC.Chat c = res.chats.get(a);
                                chatsDict.put(c.id, c);
                            }

                            TLRPC.TL_messages_getPeerDialogs req1 = new TLRPC.TL_messages_getPeerDialogs();
                            TLRPC.TL_inputDialogPeer peer = new TLRPC.TL_inputDialogPeer();
                            if (res.peer.user_id != 0) {
                                peer.peer = new TLRPC.TL_inputPeerUser();
                                peer.peer.user_id = res.peer.user_id;
                                TLRPC.User user = usersDict.get(res.peer.user_id);
                                if (user != null) {
                                    peer.peer.access_hash = user.access_hash;
                                }
                            } else if (res.peer.chat_id != 0) {
                                peer.peer = new TLRPC.TL_inputPeerChat();
                                peer.peer.chat_id = res.peer.chat_id;
                                TLRPC.Chat chat = chatsDict.get(res.peer.chat_id);
                                if (chat != null) {
                                    peer.peer.access_hash = chat.access_hash;
                                }
                            } else {
                                peer.peer = new TLRPC.TL_inputPeerChannel();
                                peer.peer.channel_id = res.peer.channel_id;
                                TLRPC.Chat chat = chatsDict.get(res.peer.channel_id);
                                if (chat != null) {
                                    peer.peer.access_hash = chat.access_hash;
                                }
                            }

                            req1.peers.add(peer);
                            checkingPromoInfoRequestId = getConnectionsManager().sendRequest(req1, (response1, error1) -> {
                                if (checkPromoId != lastCheckPromoId) {
                                    return;
                                }
                                checkingPromoInfoRequestId = 0;
                                TLRPC.TL_messages_peerDialogs res2 = (TLRPC.TL_messages_peerDialogs) response1;
                                if (res2 != null && !res2.dialogs.isEmpty()) {
                                    getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                                    TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                                    dialogs.chats = res2.chats;
                                    dialogs.users = res2.users;
                                    dialogs.dialogs = res2.dialogs;
                                    dialogs.messages = res2.messages;
                                    getMessagesStorage().putDialogs(dialogs, 2);
                                    AndroidUtilities.runOnUIThread(() -> {
                                        putUsers(res.users, false);
                                        putChats(res.chats, false);
                                        putUsers(res2.users, false);
                                        putChats(res2.chats, false);

                                        if (promoDialog != null) {
                                            if (promoDialog.id < 0) {
                                                TLRPC.Chat chat = getChat(-promoDialog.id);
                                                if (ChatObject.isNotInChat(chat) || chat.restricted) {
                                                    removeDialog(promoDialog);
                                                }
                                            } else {
                                                removeDialog(promoDialog);
                                            }
                                        }

                                        promoDialog = res2.dialogs.get(0);
                                        promoDialog.id = did;
                                        promoDialog.folder_id = 0;
                                        if (DialogObject.isChannel(promoDialog)) {
                                            channelsPts.put(-promoDialog.id, promoDialog.pts);
                                        }
                                        Integer value = dialogs_read_inbox_max.get(promoDialog.id);
                                        if (value == null) {
                                            value = 0;
                                        }
                                        dialogs_read_inbox_max.put(promoDialog.id, Math.max(value, promoDialog.read_inbox_max_id));
                                        value = dialogs_read_outbox_max.get(promoDialog.id);
                                        if (value == null) {
                                            value = 0;
                                        }
                                        dialogs_read_outbox_max.put(promoDialog.id, Math.max(value, promoDialog.read_outbox_max_id));
                                        dialogs_dict.put(did, promoDialog);
                                        if (!res2.messages.isEmpty()) {
                                            LongSparseArray<TLRPC.User> usersDict1 = new LongSparseArray<>();
                                            LongSparseArray<TLRPC.Chat> chatsDict1 = new LongSparseArray<>();
                                            for (int a = 0; a < res2.users.size(); a++) {
                                                TLRPC.User u = res2.users.get(a);
                                                usersDict1.put(u.id, u);
                                            }
                                            for (int a = 0; a < res2.chats.size(); a++) {
                                                TLRPC.Chat c = res2.chats.get(a);
                                                chatsDict1.put(c.id, c);
                                            }
                                            MessageObject messageObject = new MessageObject(currentAccount, res2.messages.get(0), usersDict1, chatsDict1, false, true);
                                            dialogMessage.put(did, messageObject);
                                            if (promoDialog.last_message_date == 0) {
                                                promoDialog.last_message_date = messageObject.messageOwner.date;
                                            }
                                        }
                                        sortDialogs(null);
                                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                                    });
                                } else {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        if (promoDialog != null) {
                                            if (promoDialog.id < 0) {
                                                TLRPC.Chat chat = getChat(-promoDialog.id);
                                                if (ChatObject.isNotInChat(chat) || chat.restricted) {
                                                    removeDialog(promoDialog);
                                                }
                                            } else {
                                                removeDialog(promoDialog);
                                            }
                                            promoDialog = null;
                                            sortDialogs(null);
                                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                                        }
                                    });
                                }
                                checkingPromoInfo = false;
                            });
                        }
                    });
                }
            } else {
                nextPromoInfoCheckTime = getConnectionsManager().getCurrentTime() + 60 * 60;
                noDialog = true;
            }
            if (noDialog) {
                promoDialogId = 0;
                getGlobalMainSettings().edit().putLong("proxy_dialog", promoDialogId).remove("proxyDialogAddress").putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime).commit();
                checkingPromoInfoRequestId = 0;
                checkingPromoInfo = false;
                AndroidUtilities.runOnUIThread(this::removePromoDialog);
            }
        });
        if (removeCurrent != 0) {
            promoDialogId = 0;
            proxyDialogAddress = null;
            nextPromoInfoCheckTime = getConnectionsManager().getCurrentTime() + 60 * 60;
            getGlobalMainSettings().edit().putLong("proxy_dialog", promoDialogId).remove("proxyDialogAddress").putInt("nextPromoInfoCheckTime", nextPromoInfoCheckTime).commit();
            AndroidUtilities.runOnUIThread(this::removePromoDialog);
        }
    }

    private void removePromoDialog() {
        if (promoDialog == null) {
            return;
        }
        if (promoDialog.id < 0) {
            TLRPC.Chat chat = getChat(-promoDialog.id);
            if (ChatObject.isNotInChat(chat) || chat.restricted) {
                removeDialog(promoDialog);
            }
        } else {
            removeDialog(promoDialog);
        }
        promoDialog = null;
        sortDialogs(null);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public boolean isPromoDialog(long did, boolean checkLeft) {
        return promoDialog != null && promoDialog.id == did && (!checkLeft || isLeftPromoChannel);
    }

    private String getUserNameForTyping(TLRPC.User user) {
        if (user == null) {
            return "";
        }
        if (user.first_name != null && user.first_name.length() > 0) {
            return user.first_name;
        } else if (user.last_name != null && user.last_name.length() > 0) {
            return user.last_name;
        }
        return "";
    }

    private void updatePrintingStrings() {
        LongSparseArray<SparseArray<CharSequence>> newStrings = new LongSparseArray<>();
        LongSparseArray<SparseArray<Integer>> newTypes = new LongSparseArray<>();

        for (HashMap.Entry<Long, ConcurrentHashMap<Integer, ArrayList<PrintingUser>>> dialogEntry : printingUsers.entrySet()) {
            Long key = dialogEntry.getKey();
            boolean isEncryptedChat = DialogObject.isEncryptedDialog(key);
            ConcurrentHashMap<Integer, ArrayList<PrintingUser>> threads = dialogEntry.getValue();

            for (HashMap.Entry<Integer, ArrayList<PrintingUser>> threadEntry : threads.entrySet()) {
                Integer threadId = threadEntry.getKey();
                ArrayList<PrintingUser> arr = threadEntry.getValue();

                SparseArray<CharSequence> newPrintingStrings = new SparseArray<>();
                SparseArray<Integer> newPrintingStringsTypes = new SparseArray<>();
                newStrings.put(key, newPrintingStrings);
                newTypes.put(key, newPrintingStringsTypes);

                if (key > 0 || isEncryptedChat || arr.size() == 1) {
                    PrintingUser pu = arr.get(0);
                    TLRPC.User user = getUser(pu.userId);
                    if (user == null) {
                        continue;
                    }
                    if (pu.action instanceof TLRPC.TL_sendMessageRecordAudioAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsRecordingAudio", R.string.IsRecordingAudio, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("RecordingAudio", R.string.RecordingAudio));
                        }
                        newPrintingStringsTypes.put(threadId, 1);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageRecordRoundAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsRecordingRound", R.string.IsRecordingRound, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("RecordingRound", R.string.RecordingRound));
                        }
                        newPrintingStringsTypes.put(threadId, 4);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageUploadRoundAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingVideo", R.string.IsSendingVideo, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SendingVideoStatus", R.string.SendingVideoStatus));
                        }
                        newPrintingStringsTypes.put(threadId, 4);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageUploadAudioAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingAudio", R.string.IsSendingAudio, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SendingAudio", R.string.SendingAudio));
                        }
                        newPrintingStringsTypes.put(threadId, 2);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageUploadVideoAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingVideo", R.string.IsSendingVideo, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SendingVideoStatus", R.string.SendingVideoStatus));
                        }
                        newPrintingStringsTypes.put(threadId, 2);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageRecordVideoAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsRecordingVideo", R.string.IsRecordingVideo, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("RecordingVideoStatus", R.string.RecordingVideoStatus));
                        }
                        newPrintingStringsTypes.put(threadId, 2);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageUploadDocumentAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingFile", R.string.IsSendingFile, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SendingFile", R.string.SendingFile));
                        }
                        newPrintingStringsTypes.put(threadId, 2);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageUploadPhotoAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingPhoto", R.string.IsSendingPhoto, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SendingPhoto", R.string.SendingPhoto));
                        }
                        newPrintingStringsTypes.put(threadId, 2);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageGamePlayAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSendingGame", R.string.IsSendingGame, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SendingGame", R.string.SendingGame));
                        }
                        newPrintingStringsTypes.put(threadId, 3);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageGeoLocationAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSelectingLocation", R.string.IsSelectingLocation, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SelectingLocation", R.string.SelectingLocation));
                        }
                        newPrintingStringsTypes.put(threadId, 0);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageChooseContactAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsSelectingContact", R.string.IsSelectingContact, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("SelectingContact", R.string.SelectingContact));
                        }
                        newPrintingStringsTypes.put(threadId, 0);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageEmojiInteractionSeen) {
                        String emoji = ((TLRPC.TL_sendMessageEmojiInteractionSeen) pu.action).emoticon;
                        String printingString;
                        if (key < 0 && !isEncryptedChat) {
                            printingString= LocaleController.formatString("IsEnjoyngAnimations", R.string.IsEnjoyngAnimations, getUserNameForTyping(user), emoji);
                        } else {
                            printingString = LocaleController.formatString("EnjoyngAnimations", R.string.EnjoyngAnimations, emoji);
                        }
                        newPrintingStrings.put(threadId, printingString);
                        newPrintingStringsTypes.put(threadId, 5);
                    } else if (pu.action instanceof TLRPC.TL_sendMessageChooseStickerAction) {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsChoosingSticker", R.string.IsChoosingSticker, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("ChoosingSticker", R.string.ChoosingSticker));
                        }
                        newPrintingStringsTypes.put(threadId, 5);
                    } else {
                        if (key < 0 && !isEncryptedChat) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsTypingGroup", R.string.IsTypingGroup, getUserNameForTyping(user)));
                        } else {
                            newPrintingStrings.put(threadId, LocaleController.getString("Typing", R.string.Typing));
                        }
                        newPrintingStringsTypes.put(threadId, 0);
                    }
                } else {
                    int count = 0;
                    StringBuilder label = new StringBuilder();
                    for (PrintingUser pu : arr) {
                        TLRPC.User user = getUser(pu.userId);
                        if (user != null) {
                            if (label.length() != 0) {
                                label.append(", ");
                            }
                            label.append(getUserNameForTyping(user));
                            count++;
                        }
                        if (count == 2) {
                            break;
                        }
                    }
                    if (label.length() != 0) {
                        if (count == 1) {
                            newPrintingStrings.put(threadId, LocaleController.formatString("IsTypingGroup", R.string.IsTypingGroup, label.toString()));
                        } else {
                            if (arr.size() > 2) {
                                String plural = LocaleController.getPluralString("AndMoreTypingGroup", arr.size() - 2);
                                try {
                                    newPrintingStrings.put(threadId, String.format(plural, label.toString(), arr.size() - 2));
                                } catch (Exception e) {
                                    newPrintingStrings.put(threadId, "LOC_ERR: AndMoreTypingGroup");
                                }
                            } else {
                                newPrintingStrings.put(threadId, LocaleController.formatString("AreTypingGroup", R.string.AreTypingGroup, label.toString()));
                            }
                        }
                        newPrintingStringsTypes.put(threadId, 0);
                    }
                }
            }
        }

        lastPrintingStringCount = newStrings.size();

        AndroidUtilities.runOnUIThread(() -> {
            printingStrings = newStrings;
            printingStringsTypes = newTypes;
        });
    }

    public void cancelTyping(int action, long dialogId, int threadMsgId) {
        if (action < 0 || action >= sendingTypings.length || sendingTypings[action] == null) {
            return;
        }
        LongSparseArray<SparseArray<Boolean>> dialogs = sendingTypings[action];
        SparseArray<Boolean> threads = dialogs.get(dialogId);
        if (threads == null) {
            return;
        }
        threads.remove(threadMsgId);
        if (threads.size() == 0) {
            dialogs.remove(dialogId);
        }
    }

    public boolean sendTyping(long dialogId, int threadMsgId, int action, int classGuid) {
        return sendTyping(dialogId, threadMsgId, action, null, classGuid);
    }
    public boolean sendTyping(long dialogId, int threadMsgId, int action, String emojicon, int classGuid) {
        if (action < 0 || action >= sendingTypings.length || dialogId == 0) {
            return false;
        }
        if (dialogId < 0) {
            if (ChatObject.shouldSendAnonymously(getChat(-dialogId))) {
                return false;
            }
        } else {
            TLRPC.User user = getUser(dialogId);
            if (user != null) {
                if (user.id == getUserConfig().getClientUserId()) {
                    return false;
                }
                if (user.status != null && user.status.expires != -100 && !onlinePrivacy.containsKey(user.id)) {
                    int time = getConnectionsManager().getCurrentTime();
                    if (user.status.expires <= time - 30) {
                        return false;
                    }
                }
            }
        }
        LongSparseArray<SparseArray<Boolean>> dialogs = sendingTypings[action];
        if (dialogs == null) {
            dialogs = sendingTypings[action] = new LongSparseArray<>();
        }
        SparseArray<Boolean> threads = dialogs.get(dialogId);
        if (threads == null) {
            dialogs.put(dialogId, threads = new SparseArray<>());
        }
        if (threads.get(threadMsgId) != null) {
            return false;
        }
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
            if (threadMsgId != 0) {
                req.top_msg_id = threadMsgId;
                req.flags |= 1;
            }
            req.peer = getInputPeer(dialogId);
            if (req.peer instanceof TLRPC.TL_inputPeerChannel) {
                TLRPC.Chat chat = getChat(req.peer.channel_id);
                if (chat == null || !chat.megagroup) {
                    return false;
                }
            }
            if (req.peer == null) {
                return false;
            }
            if (action == 0) {
                req.action = new TLRPC.TL_sendMessageTypingAction();
            } else if (action == 1) {
                req.action = new TLRPC.TL_sendMessageRecordAudioAction();
            } else if (action == 2) {
                req.action = new TLRPC.TL_sendMessageCancelAction();
            } else if (action == 3) {
                req.action = new TLRPC.TL_sendMessageUploadDocumentAction();
            } else if (action == 4) {
                req.action = new TLRPC.TL_sendMessageUploadPhotoAction();
            } else if (action == 5) {
                req.action = new TLRPC.TL_sendMessageUploadVideoAction();
            } else if (action == 6) {
                req.action = new TLRPC.TL_sendMessageGamePlayAction();
            } else if (action == 7) {
                req.action = new TLRPC.TL_sendMessageRecordRoundAction();
            } else if (action == 8) {
                req.action = new TLRPC.TL_sendMessageUploadRoundAction();
            } else if (action == 9) {
                req.action = new TLRPC.TL_sendMessageUploadAudioAction();
            } else if (action == 10) {
                req.action = new TLRPC.TL_sendMessageChooseStickerAction();
            } else if (action == 11) {
                TLRPC.TL_sendMessageEmojiInteractionSeen interactionSeen = new TLRPC.TL_sendMessageEmojiInteractionSeen();
                interactionSeen.emoticon = emojicon;
                req.action = interactionSeen;
            }
            threads.put(threadMsgId, true);
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> cancelTyping(action, dialogId, threadMsgId)), ConnectionsManager.RequestFlagFailOnServerErrors);
            if (classGuid != 0) {
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            }
        } else {
            if (action != 0) {
                return false;
            }
            TLRPC.EncryptedChat chat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_setEncryptedTyping req = new TLRPC.TL_messages_setEncryptedTyping();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.typing = true;
                threads.put(threadMsgId, true);
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> cancelTyping(action, dialogId, threadMsgId)), ConnectionsManager.RequestFlagFailOnServerErrors);
                if (classGuid != 0) {
                    getConnectionsManager().bindRequestToGuid(reqId, classGuid);
                }
            }
        }
        return true;
    }

    protected void removeDeletedMessagesFromArray(final long dialogId, ArrayList<TLRPC.Message> messages) {
        int maxDeletedId = deletedHistory.get(dialogId, 0);
        if (maxDeletedId == 0) {
            return;
        }
        for (int a = 0, N = messages.size(); a < N; a++) {
            TLRPC.Message message = messages.get(a);
            if (message.id <= maxDeletedId) {
                messages.remove(a);
                a--;
                N--;
            }
        }
    }

    public void loadMessages(long dialogId, long mergeDialogId, boolean loadInfo, int count, int max_id, int offset_date, boolean fromCache, int midDate, int classGuid, int load_type, int last_message_id, int mode, int threadMessageId, int replyFirstUnread, int loadIndex) {
        loadMessages(dialogId, mergeDialogId, loadInfo, count, max_id, offset_date, fromCache, midDate, classGuid, load_type, last_message_id, mode, threadMessageId, loadIndex, threadMessageId != 0 ? replyFirstUnread : 0, 0, 0, false, 0);
    }

    public void loadMessages(long dialogId, long mergeDialogId, boolean loadInfo, int count, int max_id, int offset_date, boolean fromCache, int midDate, int classGuid, int load_type, int last_message_id, int mode, int threadMessageId, int loadIndex, int first_unread, int unread_count, int last_date, boolean queryFromServer, int mentionsCount) {
        loadMessagesInternal(dialogId, mergeDialogId, loadInfo, count, max_id, offset_date, fromCache, midDate, classGuid, load_type, last_message_id, mode, threadMessageId, loadIndex, first_unread, unread_count, last_date, queryFromServer, mentionsCount, true, true);
    }

    private void loadMessagesInternal(long dialogId, long mergeDialogId, boolean loadInfo, int count, int max_id, int offset_date, boolean fromCache, int minDate, int classGuid, int load_type, int last_message_id, int mode, int threadMessageId, int loadIndex, int first_unread, int unread_count, int last_date, boolean queryFromServer, int mentionsCount, boolean loadDialog, boolean processMessages) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load messages in chat " + dialogId + " count " + count + " max_id " + max_id + " cache " + fromCache + " mindate = " + minDate + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " mode " + mode + " index " + loadIndex + " firstUnread " + first_unread + " unread_count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        }
        if (threadMessageId == 0 && mode != 2 && (fromCache || DialogObject.isEncryptedDialog(dialogId))) {
            getMessagesStorage().getMessages(dialogId, mergeDialogId, loadInfo, count, max_id, offset_date, minDate, classGuid, load_type, mode == 1, threadMessageId, loadIndex, processMessages);
        } else {
            if (threadMessageId != 0) {
                if (mode != 0) {
                    return;
                }
                TLRPC.TL_messages_getReplies req = new TLRPC.TL_messages_getReplies();
                req.peer = getInputPeer(dialogId);
                req.msg_id = threadMessageId;
                req.offset_date = offset_date;
                if (load_type == 4) {
                    req.add_offset = -count + 5;
                } else if (load_type == 3) {
                    req.add_offset = -count / 2;
                } else if (load_type == 1) {
                    req.add_offset = -count - 1;
                } else if (load_type == 2 && max_id != 0) {
                    req.add_offset = -count + 10;
                } else {
                    if (dialogId < 0 && max_id != 0) {
                        TLRPC.Chat chat = getChat(-dialogId);
                        if (ChatObject.isChannel(chat)) {
                            req.add_offset = -1;
                            req.limit += 1;
                        }
                    }
                }
                req.limit = count;
                req.offset_id = max_id;
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (response != null) {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        if (res.messages.size() > count) {
                            res.messages.remove(0);
                        }
                        int mid = max_id;
                        int fnid = 0;
                        if (!res.messages.isEmpty()) {
                            if (offset_date != 0) {
                                mid = res.messages.get(res.messages.size() - 1).id;
                                for (int a = res.messages.size() - 1; a >= 0; a--) {
                                    TLRPC.Message message = res.messages.get(a);
                                    if (message.date > offset_date) {
                                        mid = message.id;
                                        break;
                                    }
                                }
                            } else if (first_unread != 0 && load_type == 2 && max_id > 0) {
                                for (int a = res.messages.size() - 1; a >= 0; a--) {
                                    TLRPC.Message message = res.messages.get(a);
                                    if (message.id > first_unread && !message.out) {
                                        fnid = message.id;
                                        break;
                                    }
                                }
                            }
                        }
                        processLoadedMessages(res, res.messages.size(), dialogId, mergeDialogId, count, mid, offset_date, false, classGuid, fnid, last_message_id, unread_count, last_date, load_type, false, 0, threadMessageId, loadIndex, queryFromServer, mentionsCount, processMessages);
                    } else {
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.loadingMessagesFailed, classGuid, req, error));
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            } else if (mode == 2) {

            } else if (mode == 1) {
                TLRPC.TL_messages_getScheduledHistory req = new TLRPC.TL_messages_getScheduledHistory();
                req.peer = getInputPeer(dialogId);
                req.hash = minDate;
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (response != null) {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        if (res instanceof TLRPC.TL_messages_messagesNotModified) {
                            return;
                        }
                        int mid = max_id;
                        if (offset_date != 0 && !res.messages.isEmpty()) {
                            mid = res.messages.get(res.messages.size() - 1).id;
                            for (int a = res.messages.size() - 1; a >= 0; a--) {
                                TLRPC.Message message = res.messages.get(a);
                                if (message.date > offset_date) {
                                    mid = message.id;
                                    break;
                                }
                            }
                        }
                        processLoadedMessages(res, res.messages.size(), dialogId, mergeDialogId, count, mid, offset_date, false, classGuid, first_unread, last_message_id, unread_count, last_date, load_type, false, mode, threadMessageId, loadIndex, queryFromServer, mentionsCount, processMessages);
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            } else {
                if (loadDialog && (load_type == 3 || load_type == 2) && last_message_id == 0) {
                    TLRPC.TL_messages_getPeerDialogs req = new TLRPC.TL_messages_getPeerDialogs();
                    TLRPC.InputPeer inputPeer = getInputPeer(dialogId);
                    TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                    inputDialogPeer.peer = inputPeer;
                    req.peers.add(inputDialogPeer);

                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        if (response != null) {
                            TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                            if (!res.dialogs.isEmpty()) {
                                TLRPC.Dialog dialog = res.dialogs.get(0);

                                if (dialog.top_message != 0) {
                                    TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                                    dialogs.chats = res.chats;
                                    dialogs.users = res.users;
                                    dialogs.dialogs = res.dialogs;
                                    dialogs.messages = res.messages;
                                    getMessagesStorage().putDialogs(dialogs, 2);
                                }

                                loadMessagesInternal(dialogId, mergeDialogId, loadInfo, count, max_id, offset_date, false, minDate, classGuid, load_type, dialog.top_message, 0, threadMessageId, loadIndex, first_unread, dialog.unread_count, last_date, queryFromServer, dialog.unread_mentions_count, false, processMessages);
                            }
                        } else {
                            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.loadingMessagesFailed, classGuid, req, error));
                        }
                    });
                    return;
                }
                TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
                req.peer = getInputPeer(dialogId);
                if (load_type == 4) {
                    req.add_offset = -count + 5;
                } else if (load_type == 3) {
                    req.add_offset = -count / 2;
                } else if (load_type == 1) {
                    req.add_offset = -count - 1;
                } else if (load_type == 2 && max_id != 0) {
                    req.add_offset = -count + 6;
                } else {
                    if (dialogId < 0 && max_id != 0) {
                        TLRPC.Chat chat = getChat(-dialogId);
                        if (ChatObject.isChannel(chat)) {
                            req.add_offset = -1;
                            req.limit += 1;
                        }
                    }
                }
                req.limit = count;
                req.offset_id = max_id;
                req.offset_date = offset_date;
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (response != null) {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        removeDeletedMessagesFromArray(dialogId, res.messages);
                        if (res.messages.size() > count) {
                            res.messages.remove(0);
                        }
                        int mid = max_id;
                        if (offset_date != 0 && !res.messages.isEmpty()) {
                            mid = res.messages.get(res.messages.size() - 1).id;
                            for (int a = res.messages.size() - 1; a >= 0; a--) {
                                TLRPC.Message message = res.messages.get(a);
                                if (message.date > offset_date) {
                                    mid = message.id;
                                    break;
                                }
                            }
                        }
                        processLoadedMessages(res, res.messages.size(), dialogId, mergeDialogId, count, mid, offset_date, false, classGuid, first_unread, last_message_id, unread_count, last_date, load_type, false, 0, threadMessageId, loadIndex, queryFromServer, mentionsCount, processMessages);
                    } else {
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.loadingMessagesFailed, classGuid, req, error));
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public void reloadWebPages(final long dialogId, HashMap<String, ArrayList<MessageObject>> webpagesToReload, boolean scheduled) {
        HashMap<String, ArrayList<MessageObject>> map = scheduled ? reloadingScheduledWebpages : reloadingWebpages;
        LongSparseArray<ArrayList<MessageObject>> array = scheduled ? reloadingScheduledWebpagesPending : reloadingWebpagesPending;

        for (HashMap.Entry<String, ArrayList<MessageObject>> entry : webpagesToReload.entrySet()) {
            String url = entry.getKey();
            ArrayList<MessageObject> messages = entry.getValue();
            ArrayList<MessageObject> arrayList = map.get(url);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                map.put(url, arrayList);
            }
            arrayList.addAll(messages);
            TLRPC.TL_messages_getWebPagePreview req = new TLRPC.TL_messages_getWebPagePreview();
            req.message = url;
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                ArrayList<MessageObject> arrayList1 = map.remove(url);
                if (arrayList1 == null) {
                    return;
                }
                TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                if (!(response instanceof TLRPC.TL_messageMediaWebPage)) {
                    for (int a = 0; a < arrayList1.size(); a++) {
                        arrayList1.get(a).messageOwner.media.webpage = new TLRPC.TL_webPageEmpty();
                        messagesRes.messages.add(arrayList1.get(a).messageOwner);
                    }
                } else {
                    TLRPC.TL_messageMediaWebPage media = (TLRPC.TL_messageMediaWebPage) response;
                    if (media.webpage instanceof TLRPC.TL_webPage || media.webpage instanceof TLRPC.TL_webPageEmpty) {
                        for (int a = 0; a < arrayList1.size(); a++) {
                            arrayList1.get(a).messageOwner.media.webpage = media.webpage;
                            if (a == 0) {
                                ImageLoader.saveMessageThumbs(arrayList1.get(a).messageOwner);
                            }
                            messagesRes.messages.add(arrayList1.get(a).messageOwner);
                        }
                    } else {
                        array.put(media.webpage.id, arrayList1);
                    }
                }
                if (!messagesRes.messages.isEmpty()) {
                    getMessagesStorage().putMessages(messagesRes, dialogId, -2, 0, false, scheduled);
                    getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, arrayList1);
                }
            }));
        }
    }

    public void processLoadedMessages(TLRPC.messages_Messages messagesRes, int resCount, long dialogId, long mergeDialogId, int count, int max_id, int offset_date, boolean isCache, int classGuid,
                                        int first_unread, int last_message_id, int unread_count, int last_date, int load_type, boolean isEnd, int mode, int threadMessageId, int loadIndex, boolean queryFromServer, int mentionsCount, boolean needProcess) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("processLoadedMessages size " + messagesRes.messages.size() + " in chat " + dialogId + " count " + count + " max_id " + max_id + " cache " + isCache + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " index " + loadIndex + " firstUnread " + first_unread + " unread_count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        }
        long startProcessTime = SystemClock.elapsedRealtime();
        boolean createDialog = false;
        if (messagesRes instanceof TLRPC.TL_messages_channelMessages) {
            long channelId = -dialogId;
            if (mode == 0 && threadMessageId == 0) {
                int channelPts = channelsPts.get(channelId);
                if (channelPts == 0) {
                    channelPts = getMessagesStorage().getChannelPtsSync(channelId);
                    if (channelPts == 0) {
                        channelsPts.put(channelId, messagesRes.pts);
                        createDialog = true;
                        if (needShortPollChannels.indexOfKey(channelId) >= 0 && shortPollChannels.indexOfKey(channelId) < 0) {
                            getChannelDifference(channelId, 2, 0, null);
                        } else {
                            getChannelDifference(channelId);
                        }
                    }
                }
            }
        }
        if (!isCache) {
            ImageLoader.saveMessagesThumbs(messagesRes.messages);
        }
        boolean isInitialLoading = offset_date == 0 && max_id == 0;
        boolean reload;
        if (mode == 1) {
            reload = ((SystemClock.elapsedRealtime() - lastScheduledServerQueryTime.get(dialogId, 0L)) > 60 * 1000);
        } else {
            reload = resCount == 0 && (!isInitialLoading || (SystemClock.elapsedRealtime() - lastServerQueryTime.get(dialogId, 0L)) > 60 * 1000);
        }
        if (!DialogObject.isEncryptedDialog(dialogId) && isCache && reload) {
            int hash;
            if (mode == 2) {
                hash = 0;
            } else if (mode == 1) {
                lastScheduledServerQueryTime.put(dialogId, SystemClock.elapsedRealtime());
                long h = 0;
                for (int a = 0, N = messagesRes.messages.size(); a < N; a++) {
                    TLRPC.Message message = messagesRes.messages.get(a);
                    if (message.id < 0) {
                        continue;
                    }
                    h = MediaDataController.calcHash(h, message.id);
                    h = MediaDataController.calcHash(h, message.edit_date);
                    h = MediaDataController.calcHash(h, message.date);
                }
                hash = (int) h - 1;
            } else {
                lastServerQueryTime.put(dialogId, SystemClock.elapsedRealtime());
                hash = 0;
            }
            AndroidUtilities.runOnUIThread(() -> loadMessagesInternal(dialogId, mergeDialogId, false, count, load_type == 2 && queryFromServer ? first_unread : max_id, offset_date, false, hash, classGuid, load_type, last_message_id, mode, threadMessageId, loadIndex, first_unread, unread_count, last_date, queryFromServer, mentionsCount, true, needProcess));
            if (messagesRes.messages.isEmpty()) {
                return;
            }
        }
        LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
        LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
        for (int a = 0; a < messagesRes.users.size(); a++) {
            TLRPC.User u = messagesRes.users.get(a);
            usersDict.put(u.id, u);
        }
        for (int a = 0; a < messagesRes.chats.size(); a++) {
            TLRPC.Chat c = messagesRes.chats.get(a);
            chatsDict.put(c.id, c);
        }
        int size = messagesRes.messages.size();
        if (!isCache) {
            Integer inboxValue = dialogs_read_inbox_max.get(dialogId);
            if (inboxValue == null) {
                inboxValue = getMessagesStorage().getDialogReadMax(false, dialogId);
                dialogs_read_inbox_max.put(dialogId, inboxValue);
            }

            Integer outboxValue = dialogs_read_outbox_max.get(dialogId);
            if (outboxValue == null) {
                outboxValue = getMessagesStorage().getDialogReadMax(true, dialogId);
                dialogs_read_outbox_max.put(dialogId, outboxValue);
            }

            for (int a = 0; a < size; a++) {
                TLRPC.Message message = messagesRes.messages.get(a);

                if (mode == 0) {
                    if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                        TLRPC.User user = usersDict.get(message.action.user_id);
                        if (user != null && user.bot) {
                            message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                            message.flags |= 64;
                        }
                    }

                    if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                        message.unread = false;
                        message.media_unread = false;
                    } else if (threadMessageId == 0) {
                        message.unread = (message.out ? outboxValue : inboxValue) < message.id;
                    } else {
                        message.unread = true;
                    }
                }
            }
            if (threadMessageId == 0) {
                getMessagesStorage().putMessages(messagesRes, dialogId, load_type, max_id, createDialog, mode == 1);
            }
        }

        if (!needProcess && DialogObject.isEncryptedDialog(dialogId)) {
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.messagesDidLoadWithoutProcess, classGuid, messagesRes.messages.size(), isCache, isEnd, last_message_id));
            return;
        }
        ArrayList<MessageObject> objects = new ArrayList<>();
        ArrayList<Integer> messagesToReload = new ArrayList<>();
        HashMap<String, ArrayList<MessageObject>> webpagesToReload = new HashMap<>();
        TLRPC.InputChannel inputChannel = null;
        long fileProcessTime = 0;
        for (int a = 0; a < size; a++) {
            TLRPC.Message message = messagesRes.messages.get(a);
            message.dialog_id = dialogId;
            long checkFileTime = SystemClock.elapsedRealtime();
            MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, true, true);
            fileProcessTime += (SystemClock.elapsedRealtime() - checkFileTime);
            messageObject.scheduled = mode == 1;
            objects.add(messageObject);
            if (isCache) {
                if (message.legacy && message.layer < TLRPC.LAYER) {
                    messagesToReload.add(message.id);
                } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                    if (message.media.bytes != null && (message.media.bytes.length == 0 || message.media.bytes.length == 1 && message.media.bytes[0] < TLRPC.LAYER || message.media.bytes.length == 4 && Utilities.bytesToInt(message.media.bytes) < TLRPC.LAYER)) {
                        messagesToReload.add(message.id);
                    }
                }
                if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                    if (message.media.webpage instanceof TLRPC.TL_webPagePending && message.media.webpage.date <= getConnectionsManager().getCurrentTime()) {
                        messagesToReload.add(message.id);
                    } else if (message.media.webpage instanceof TLRPC.TL_webPageUrlPending) {
                        ArrayList<MessageObject> arrayList = webpagesToReload.get(message.media.webpage.url);
                        if (arrayList == null) {
                            arrayList = new ArrayList<>();
                            webpagesToReload.put(message.media.webpage.url, arrayList);
                        }
                        arrayList.add(messageObject);
                    }
                }
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("process time = " + (SystemClock.elapsedRealtime() - startProcessTime) + " file time = " + fileProcessTime + " for dialog = " + dialogId);
        }
        AndroidUtilities.runOnUIThread(() -> {
            putUsers(messagesRes.users, isCache);
            putChats(messagesRes.chats, isCache);
            int first_unread_final;
            if (mode == 1) {
                first_unread_final = 0;
            } else {
                first_unread_final = Integer.MAX_VALUE;
                if (queryFromServer && load_type == 2) {
                    for (int a = 0; a < messagesRes.messages.size(); a++) {
                        TLRPC.Message message = messagesRes.messages.get(a);
                        if ((!message.out || message.from_scheduled) && message.id > first_unread && message.id < first_unread_final) {
                            first_unread_final = message.id;
                        }
                    }
                }
                if (first_unread_final == Integer.MAX_VALUE) {
                    first_unread_final = first_unread;
                }
            }
            if (mode == 1 && count == 1) {
                getNotificationCenter().postNotificationName(NotificationCenter.scheduledMessagesUpdated, dialogId, objects.size());
            }

            if (!DialogObject.isEncryptedDialog(dialogId)) {
                int finalFirst_unread_final = first_unread_final;
                getMediaDataController().loadReplyMessagesForMessages(objects, dialogId, mode == 1, () -> {
                    if (!needProcess) {
                        getNotificationCenter().postNotificationName(NotificationCenter.messagesDidLoadWithoutProcess, classGuid, resCount, isCache, isEnd, last_message_id);
                    } else {
                        getNotificationCenter().postNotificationName(NotificationCenter.messagesDidLoad, dialogId, count, objects, isCache, finalFirst_unread_final, last_message_id, unread_count, last_date, load_type, isEnd, classGuid, loadIndex, max_id, mentionsCount, mode);
                    }
                });
            } else {
                getNotificationCenter().postNotificationName(NotificationCenter.messagesDidLoad, dialogId, count, objects, isCache, first_unread_final, last_message_id, unread_count, last_date, load_type, isEnd, classGuid, loadIndex, max_id, mentionsCount, mode);
            }

            if (!messagesToReload.isEmpty()) {
                reloadMessages(messagesToReload, dialogId, mode == 1);
            }
            if (!webpagesToReload.isEmpty()) {
                reloadWebPages(dialogId, webpagesToReload, mode == 1);
            }
        });
    }

    public void loadHintDialogs() {
        if (!hintDialogs.isEmpty() || TextUtils.isEmpty(installReferer)) {
            return;
        }
        TLRPC.TL_help_getRecentMeUrls req = new TLRPC.TL_help_getRecentMeUrls();
        req.referer = installReferer;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    /*installReferer = null;
                    mainPreferences.edit().remove("installReferer").commit();*/

                    TLRPC.TL_help_recentMeUrls res = (TLRPC.TL_help_recentMeUrls) response;
                    putUsers(res.users, false);
                    putChats(res.chats, false);
                    hintDialogs.clear();
                    hintDialogs.addAll(res.urls);

                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                });
            }
        });
    }

    private TLRPC.TL_dialogFolder ensureFolderDialogExists(int folderId, boolean[] folderCreated) {
        if (folderId == 0) {
            return null;
        }
        long folderDialogId = DialogObject.makeFolderDialogId(folderId);
        TLRPC.Dialog dialog = dialogs_dict.get(folderDialogId);
        if (dialog instanceof TLRPC.TL_dialogFolder) {
            if (folderCreated != null) {
                folderCreated[0] = false;
            }
            return (TLRPC.TL_dialogFolder) dialog;
        }
        if (folderCreated != null) {
            folderCreated[0] = true;
        }
        TLRPC.TL_dialogFolder dialogFolder = new TLRPC.TL_dialogFolder();
        dialogFolder.id = folderDialogId;
        dialogFolder.peer = new TLRPC.TL_peerUser();
        dialogFolder.folder = new TLRPC.TL_folder();
        dialogFolder.folder.id = folderId;
        dialogFolder.folder.title = LocaleController.getString("ArchivedChats", R.string.ArchivedChats);
        dialogFolder.pinned = true;

        int maxPinnedNum = 0;
        for (int a = 0; a < allDialogs.size(); a++) {
            TLRPC.Dialog d = allDialogs.get(a);
            if (!d.pinned) {
                if (d.id != promoDialogId) {
                    break;
                }
                continue;
            }
            maxPinnedNum = Math.max(d.pinnedNum, maxPinnedNum);
        }
        dialogFolder.pinnedNum = maxPinnedNum + 1;

        TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
        dialogs.dialogs.add(dialogFolder);
        getMessagesStorage().putDialogs(dialogs, 1);

        dialogs_dict.put(folderDialogId, dialogFolder);
        allDialogs.add(0, dialogFolder);
        return dialogFolder;
    }

    private void removeFolder(int folderId) {
        long dialogId = DialogObject.makeFolderDialogId(folderId);
        TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
        if (dialog == null) {
            return;
        }
        dialogs_dict.remove(dialogId);
        allDialogs.remove(dialog);
        sortDialogs(null);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        getNotificationCenter().postNotificationName(NotificationCenter.folderBecomeEmpty, folderId);
    }

    protected void onFolderEmpty(int folderId) {
        long[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(folderId);
        if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] == Integer.MAX_VALUE) {
            removeFolder(folderId);
        } else {
            loadDialogs(folderId, 0, 10, false, () -> removeFolder(folderId));
        }
    }

    public void checkIfFolderEmpty(int folderId) {
        if (folderId == 0) {
            return;
        }
        getMessagesStorage().checkIfFolderEmpty(folderId);
    }

    public int addDialogToFolder(long dialogId, int folderId, int pinnedNum, long taskId) {
        ArrayList<Long> arrayList = new ArrayList<>(1);
        arrayList.add(dialogId);
        return addDialogToFolder(arrayList, folderId, pinnedNum, null, taskId);
    }

    public int addDialogToFolder(ArrayList<Long> dialogIds, int folderId, int pinnedNum, ArrayList<TLRPC.TL_inputFolderPeer> peers, long taskId) {
        TLRPC.TL_folders_editPeerFolders req = new TLRPC.TL_folders_editPeerFolders();
        boolean[] folderCreated = null;

        long newTaskId;
        if (taskId == 0) {
            boolean added = false;
            long selfUserId = getUserConfig().getClientUserId();
            int size = 0;
            for (int a = 0, N = dialogIds.size(); a < N; a++) {
                long dialogId = dialogIds.get(a);
                if (!DialogObject.isChatDialog(dialogId) && !DialogObject.isUserDialog(dialogId) && !DialogObject.isEncryptedDialog(dialogId)) {
                    continue;
                }
                if (folderId == 1 && (dialogId == selfUserId || dialogId == 777000 || isPromoDialog(dialogId, false))) {
                    continue;
                }
                TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
                if (dialog == null) {
                    continue;
                }
                added = true;
                dialog.folder_id = folderId;
                if (pinnedNum > 0) {
                    dialog.pinned = true;
                    dialog.pinnedNum = pinnedNum;
                } else {
                    dialog.pinned = false;
                    dialog.pinnedNum = 0;
                }
                if (folderCreated == null) {
                    folderCreated = new boolean[1];
                    ensureFolderDialogExists(folderId, folderCreated);
                }
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    getMessagesStorage().setDialogsFolderId(null, null, dialogId, folderId);
                } else {
                    TLRPC.TL_inputFolderPeer folderPeer = new TLRPC.TL_inputFolderPeer();
                    folderPeer.folder_id = folderId;
                    folderPeer.peer = getInputPeer(dialogId);
                    req.folder_peers.add(folderPeer);
                    size += folderPeer.getObjectSize();
                }
            }
            if (!added) {
                return 0;
            }
            sortDialogs(null);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);

            if (size != 0) {
                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(4 + 4 + 4 + size);
                    data.writeInt32(17);
                    data.writeInt32(folderId);
                    data.writeInt32(req.folder_peers.size());
                    for (int a = 0, N = req.folder_peers.size(); a < N; a++) {
                        req.folder_peers.get(a).serializeToStream(data);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = getMessagesStorage().createPendingTask(data);
            } else {
                newTaskId = 0;
            }
        } else {
            req.folder_peers = peers;
            newTaskId = taskId;
        }
        if (!req.folder_peers.isEmpty()) {
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    processUpdates((TLRPC.Updates) response, false);
                }
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            });
            getMessagesStorage().setDialogsFolderId(null, req.folder_peers, 0, folderId);
        }
        return folderCreated == null ? 0 : (folderCreated[0] ? 2 : 1);
    }

    public void loadDialogs(final int folderId, int offset, int count, boolean fromCache) {
        loadDialogs(folderId, offset, count, fromCache, null);
    }

    public void loadDialogs(final int folderId, int offset, int count, boolean fromCache, Runnable onEmptyCallback) {
        if (loadingDialogs.get(folderId) || resetingDialogs) {
            return;
        }
        loadingDialogs.put(folderId, true);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("folderId = " + folderId + " load cacheOffset = " + offset + " count = " + count + " cache = " + fromCache);
        }
        if (fromCache) {
            getMessagesStorage().getDialogs(folderId, offset == 0 ? 0 : nextDialogsCacheOffset.get(folderId, 0), count, folderId == 0 && offset == 0);
        } else {
            TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
            req.limit = count;
            req.exclude_pinned = true;
            if (folderId != 0) {
                req.flags |= 2;
                req.folder_id = folderId;
            }
            long[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(folderId);
            if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1) {
                if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] == Integer.MAX_VALUE) {
                    dialogsEndReached.put(folderId, true);
                    serverDialogsEndReached.put(folderId, true);
                    loadingDialogs.put(folderId, false);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    return;
                }
                req.offset_id = (int) dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId];
                req.offset_date = (int) dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetDate];
                if (req.offset_id == 0) {
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                } else {
                    if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetChannelId] != 0) {
                        req.offset_peer = new TLRPC.TL_inputPeerChannel();
                        req.offset_peer.channel_id = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetChannelId];
                    } else if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetUserId] != 0) {
                        req.offset_peer = new TLRPC.TL_inputPeerUser();
                        req.offset_peer.user_id = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetUserId];
                    } else {
                        req.offset_peer = new TLRPC.TL_inputPeerChat();
                        req.offset_peer.chat_id = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetChatId];
                    }
                    req.offset_peer.access_hash = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetAccess];
                }
            } else {
                boolean found = false;
                ArrayList<TLRPC.Dialog> dialogs = getDialogs(folderId);
                for (int a = dialogs.size() - 1; a >= 0; a--) {
                    TLRPC.Dialog dialog = dialogs.get(a);
                    if (dialog.pinned) {
                        continue;
                    }
                    if (!DialogObject.isEncryptedDialog(dialog.id) && dialog.top_message > 0) {
                        MessageObject message = dialogMessage.get(dialog.id);
                        if (message != null && message.getId() > 0) {
                            req.offset_date = message.messageOwner.date;
                            req.offset_id = message.messageOwner.id;
                            long id;
                            if (message.messageOwner.peer_id.channel_id != 0) {
                                id = -message.messageOwner.peer_id.channel_id;
                            } else if (message.messageOwner.peer_id.chat_id != 0) {
                                id = -message.messageOwner.peer_id.chat_id;
                            } else {
                                id = message.messageOwner.peer_id.user_id;
                            }
                            req.offset_peer = getInputPeer(id);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                }
            }
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                    processLoadedDialogs(dialogsRes, null, folderId, 0, count, 0, false, false, false);
                    if (onEmptyCallback != null && dialogsRes.dialogs.isEmpty()) {
                        AndroidUtilities.runOnUIThread(onEmptyCallback);
                    }
                }
            });
        }
    }

    public void loadGlobalNotificationsSettings() {
        if (loadingNotificationSettings == 0 && !getUserConfig().notificationsSettingsLoaded) {
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            SharedPreferences.Editor editor1 = null;
            if (preferences.contains("EnableGroup")) {
                boolean enabled = preferences.getBoolean("EnableGroup", true);
                editor1 = preferences.edit();
                if (!enabled) {
                    editor1.putInt("EnableGroup2", Integer.MAX_VALUE);
                    editor1.putInt("EnableChannel2", Integer.MAX_VALUE);
                }
                editor1.remove("EnableGroup").commit();
            }
            if (preferences.contains("EnableAll")) {
                boolean enabled = preferences.getBoolean("EnableAll", true);
                if (editor1 == null) {
                    editor1 = preferences.edit();
                }
                if (!enabled) {
                    editor1.putInt("EnableAll2", Integer.MAX_VALUE);
                }
                editor1.remove("EnableAll").commit();
            }
            if (editor1 != null) {
                editor1.commit();
            }

            loadingNotificationSettings = 3;
            for (int a = 0; a < 3; a++) {
                TLRPC.TL_account_getNotifySettings req = new TLRPC.TL_account_getNotifySettings();
                if (a == 0) {
                    req.peer = new TLRPC.TL_inputNotifyChats();
                } else if (a == 1) {
                    req.peer = new TLRPC.TL_inputNotifyUsers();
                } else {
                    req.peer = new TLRPC.TL_inputNotifyBroadcasts();
                }
                int type = a;
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response != null) {
                        loadingNotificationSettings--;
                        TLRPC.TL_peerNotifySettings notify_settings = (TLRPC.TL_peerNotifySettings) response;
                        SharedPreferences.Editor editor = notificationsPreferences.edit();
                        if (type == 0) {
                            if ((notify_settings.flags & 1) != 0) {
                                editor.putBoolean("EnablePreviewGroup", notify_settings.show_previews);
                            }
                            if ((notify_settings.flags & 2) != 0) {
                            /*if (notify_settings.silent) {
                                editor.putString("GroupSoundPath", "NoSound");
                            } else {
                                editor.remove("GroupSoundPath");
                            }*/
                            }
                            if ((notify_settings.flags & 4) != 0) {
                                editor.putInt("EnableGroup2", notify_settings.mute_until);
                            }
                        } else if (type == 1) {
                            if ((notify_settings.flags & 1) != 0) {
                                editor.putBoolean("EnablePreviewAll", notify_settings.show_previews);
                            }
                            if ((notify_settings.flags & 2) != 0) {
                            /*if (notify_settings.silent) {
                                editor.putString("GlobalSoundPath", "NoSound");
                            } else {
                                editor.remove("GlobalSoundPath");
                            }*/
                            }
                            if ((notify_settings.flags & 4) != 0) {
                                editor.putInt("EnableAll2", notify_settings.mute_until);
                            }
                        } else {
                            if ((notify_settings.flags & 1) != 0) {
                                editor.putBoolean("EnablePreviewChannel", notify_settings.show_previews);
                            }
                            if ((notify_settings.flags & 2) != 0) {
                            /*if (notify_settings.silent) {
                                editor.putString("ChannelSoundPath", "NoSound");
                            } else {
                                editor.remove("ChannelSoundPath");
                            }*/
                            }
                            if ((notify_settings.flags & 4) != 0) {
                                editor.putInt("EnableChannel2", notify_settings.mute_until);
                            }
                        }
                        editor.commit();
                        if (loadingNotificationSettings == 0) {
                            getUserConfig().notificationsSettingsLoaded = true;
                            getUserConfig().saveConfig(false);
                        }
                    }
                }));
            }
        }
        if (!getUserConfig().notificationsSignUpSettingsLoaded) {
            loadSignUpNotificationsSettings();
        }
    }

    public void loadSignUpNotificationsSettings() {
        if (!loadingNotificationSignUpSettings) {
            loadingNotificationSignUpSettings = true;
            TLRPC.TL_account_getContactSignUpNotification req = new TLRPC.TL_account_getContactSignUpNotification();
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loadingNotificationSignUpSettings = false;
                SharedPreferences.Editor editor = notificationsPreferences.edit();
                enableJoined = response instanceof TLRPC.TL_boolFalse;
                editor.putBoolean("EnableContactJoined", enableJoined);
                editor.commit();
                getUserConfig().notificationsSignUpSettingsLoaded = true;
                getUserConfig().saveConfig(false);
            }));
        }
    }

    public void forceResetDialogs() {
        resetDialogs(true, getMessagesStorage().getLastSeqValue(), getMessagesStorage().getLastPtsValue(), getMessagesStorage().getLastDateValue(), getMessagesStorage().getLastQtsValue());
        getNotificationsController().deleteAllNotificationChannels();
    }

    protected void loadUnknownDialog(final TLRPC.InputPeer peer, long taskId) {
        if (peer == null) {
            return;
        }
        long dialogId = DialogObject.getPeerDialogId(peer);
        if (gettingUnknownDialogs.indexOfKey(dialogId) >= 0) {
            return;
        }
        gettingUnknownDialogs.put(dialogId, true);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load unknown dialog " + dialogId);
        }

        TLRPC.TL_messages_getPeerDialogs req = new TLRPC.TL_messages_getPeerDialogs();
        TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
        inputDialogPeer.peer = peer;
        req.peers.add(inputDialogPeer);
        long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(4 + peer.getObjectSize());
                data.writeInt32(15);
                peer.serializeToStream(data);
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }

        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                if (!res.dialogs.isEmpty()) {
                    TLRPC.TL_dialog dialog = (TLRPC.TL_dialog) res.dialogs.get(0);
                    TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                    dialogs.dialogs.addAll(res.dialogs);
                    dialogs.messages.addAll(res.messages);
                    dialogs.users.addAll(res.users);
                    dialogs.chats.addAll(res.chats);
                    processLoadedDialogs(dialogs, null, dialog.folder_id, 0, 1, DIALOGS_LOAD_TYPE_UNKNOWN, false, false, false);
                }
            }
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
            gettingUnknownDialogs.delete(dialogId);
        });
    }

    private void fetchFolderInLoadedPinnedDialogs(TLRPC.TL_messages_peerDialogs res) {
        for (int a = 0, N = res.dialogs.size(); a < N; a++) {
            TLRPC.Dialog dialog = res.dialogs.get(a);
            if (dialog instanceof TLRPC.TL_dialogFolder) {
                TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder) dialog;
                long folderTopDialogId = DialogObject.getPeerDialogId(dialog.peer);
                if (dialogFolder.top_message == 0 || folderTopDialogId == 0) {
                    res.dialogs.remove(dialogFolder);
                    continue;
                }
                for (int b = 0, N2 = res.messages.size(); b < N2; b++) {
                    TLRPC.Message message = res.messages.get(b);
                    long messageDialogId = MessageObject.getDialogId(message);
                    if (folderTopDialogId == messageDialogId && dialog.top_message == message.id) {
                        TLRPC.TL_dialog newDialog = new TLRPC.TL_dialog();
                        newDialog.peer = dialog.peer;
                        newDialog.top_message = dialog.top_message;
                        newDialog.folder_id = dialogFolder.folder.id;
                        newDialog.flags |= 16;
                        res.dialogs.add(newDialog);

                        TLRPC.InputPeer inputPeer;
                        if (dialog.peer instanceof TLRPC.TL_peerChannel) {
                            inputPeer = new TLRPC.TL_inputPeerChannel();
                            inputPeer.channel_id = dialog.peer.channel_id;
                            for (int c = 0, N3 = res.chats.size(); c < N3; c++) {
                                TLRPC.Chat chat = res.chats.get(c);
                                if (chat.id == inputPeer.channel_id) {
                                    inputPeer.access_hash = chat.access_hash;
                                    break;
                                }
                            }
                        } else if (dialog.peer instanceof TLRPC.TL_peerChat) {
                            inputPeer = new TLRPC.TL_inputPeerChat();
                            inputPeer.chat_id = dialog.peer.chat_id;
                        } else {
                            inputPeer = new TLRPC.TL_inputPeerUser();
                            inputPeer.user_id = dialog.peer.user_id;
                            for (int c = 0, N3 = res.users.size(); c < N3; c++) {
                                TLRPC.User user = res.users.get(c);
                                if (user.id == inputPeer.user_id) {
                                    inputPeer.access_hash = user.access_hash;
                                    break;
                                }
                            }
                        }
                        loadUnknownDialog(inputPeer, 0);
                        break;
                    }
                }
                break;
            }
        }
    }

    private void resetDialogs(boolean query, int seq, int newPts, int date, int qts) {
        if (query) {
            if (resetingDialogs) {
                return;
            }
            getUserConfig().setPinnedDialogsLoaded(1, false);
            resetingDialogs = true;
            TLRPC.TL_messages_getPinnedDialogs req = new TLRPC.TL_messages_getPinnedDialogs();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    resetDialogsPinned = (TLRPC.TL_messages_peerDialogs) response;
                    for (int a = 0; a < resetDialogsPinned.dialogs.size(); a++) {
                        TLRPC.Dialog d = resetDialogsPinned.dialogs.get(a);
                        d.pinned = true;
                    }
                    resetDialogs(false, seq, newPts, date, qts);
                }
            });
            TLRPC.TL_messages_getDialogs req2 = new TLRPC.TL_messages_getDialogs();
            req2.limit = 100;
            req2.exclude_pinned = true;
            req2.offset_peer = new TLRPC.TL_inputPeerEmpty();
            getConnectionsManager().sendRequest(req2, (response, error) -> {
                if (error == null) {
                    resetDialogsAll = (TLRPC.messages_Dialogs) response;
                    resetDialogs(false, seq, newPts, date, qts);
                }
            });
        } else if (resetDialogsPinned != null && resetDialogsAll != null) {
            int messagesCount = resetDialogsAll.messages.size();
            int dialogsCount = resetDialogsAll.dialogs.size();
            fetchFolderInLoadedPinnedDialogs(resetDialogsPinned);
            resetDialogsAll.dialogs.addAll(resetDialogsPinned.dialogs);
            resetDialogsAll.messages.addAll(resetDialogsPinned.messages);
            resetDialogsAll.users.addAll(resetDialogsPinned.users);
            resetDialogsAll.chats.addAll(resetDialogsPinned.chats);

            LongSparseArray<TLRPC.Dialog> new_dialogs_dict = new LongSparseArray<>();
            LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
            LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
            LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();

            for (int a = 0; a < resetDialogsAll.users.size(); a++) {
                TLRPC.User u = resetDialogsAll.users.get(a);
                usersDict.put(u.id, u);
            }
            for (int a = 0; a < resetDialogsAll.chats.size(); a++) {
                TLRPC.Chat c = resetDialogsAll.chats.get(a);
                chatsDict.put(c.id, c);
            }

            TLRPC.Message lastMessage = null;
            for (int a = 0; a < resetDialogsAll.messages.size(); a++) {
                TLRPC.Message message = resetDialogsAll.messages.get(a);
                if (a < messagesCount) {
                    if (lastMessage == null || message.date < lastMessage.date) {
                        lastMessage = message;
                    }
                }
                if (message.peer_id.channel_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.peer_id.channel_id);
                    if (chat != null && chat.left) {
                        continue;
                    }
                } else if (message.peer_id.chat_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.peer_id.chat_id);
                    if (chat != null && chat.migrated_to != null) {
                        continue;
                    }
                }
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false, true);
                new_dialogMessage.put(messageObject.getDialogId(), messageObject);
            }

            for (int a = 0; a < resetDialogsAll.dialogs.size(); a++) {
                TLRPC.Dialog d = resetDialogsAll.dialogs.get(a);
                DialogObject.initDialog(d);
                if (d.id == 0) {
                    continue;
                }
                if (d.last_message_date == 0) {
                    MessageObject mess = new_dialogMessage.get(d.id);
                    if (mess != null) {
                        d.last_message_date = mess.messageOwner.date;
                    }
                }
                if (DialogObject.isChannel(d)) {
                    TLRPC.Chat chat = chatsDict.get(-d.id);
                    if (chat != null && chat.left) {
                        continue;
                    }
                    channelsPts.put(-d.id, d.pts);
                } else if (DialogObject.isChatDialog(d.id)) {
                    TLRPC.Chat chat = chatsDict.get(-d.id);
                    if (chat != null && chat.migrated_to != null) {
                        continue;
                    }
                }
                new_dialogs_dict.put(d.id, d);

                Integer value = dialogs_read_inbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_inbox_max.put(d.id, Math.max(value, d.read_inbox_max_id));

                value = dialogs_read_outbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_outbox_max.put(d.id, Math.max(value, d.read_outbox_max_id));
            }

            ImageLoader.saveMessagesThumbs(resetDialogsAll.messages);
            for (int a = 0; a < resetDialogsAll.messages.size(); a++) {
                TLRPC.Message message = resetDialogsAll.messages.get(a);
                if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    TLRPC.User user = usersDict.get(message.action.user_id);
                    if (user != null && user.bot) {
                        message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                        message.flags |= 64;
                    }
                }

                if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                    message.unread = false;
                    message.media_unread = false;
                } else {
                    ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                    Integer value = read_max.get(message.dialog_id);
                    if (value == null) {
                        value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                        read_max.put(message.dialog_id, value);
                    }
                    message.unread = value < message.id;
                }
            }

            getMessagesStorage().resetDialogs(resetDialogsAll, messagesCount, seq, newPts, date, qts, new_dialogs_dict, new_dialogMessage, lastMessage, dialogsCount);
            resetDialogsPinned = null;
            resetDialogsAll = null;
        }
    }

    protected void completeDialogsReset(final TLRPC.messages_Dialogs dialogsRes, int messagesCount, int seq, int newPts, int date, int qts, LongSparseArray<TLRPC.Dialog> new_dialogs_dict, LongSparseArray<MessageObject> new_dialogMessage, TLRPC.Message lastMessage) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingDifference = false;
            getMessagesStorage().setLastPtsValue(newPts);
            getMessagesStorage().setLastDateValue(date);
            getMessagesStorage().setLastQtsValue(qts);
            getDifference();

            AndroidUtilities.runOnUIThread(() -> {
                resetingDialogs = false;
                applyDialogsNotificationsSettings(dialogsRes.dialogs);

                MediaDataController mediaDataController = getMediaDataController();
                mediaDataController.clearAllDrafts(false);
                mediaDataController.loadDraftsIfNeed();

                putUsers(dialogsRes.users, false);
                putChats(dialogsRes.chats, false);

                for (int a = 0; a < allDialogs.size(); a++) {
                    TLRPC.Dialog oldDialog = allDialogs.get(a);
                    if (!DialogObject.isEncryptedDialog(oldDialog.id)) {
                        dialogs_dict.remove(oldDialog.id);
                        MessageObject messageObject = dialogMessage.get(oldDialog.id);
                        dialogMessage.remove(oldDialog.id);
                        if (messageObject != null) {
                            if (messageObject.messageOwner.peer_id.channel_id == 0) {
                                dialogMessagesByIds.remove(messageObject.getId());
                            }
                            if (messageObject.messageOwner.random_id != 0) {
                                dialogMessagesByRandomIds.remove(messageObject.messageOwner.random_id);
                            }
                        }
                    }
                }

                for (int a = 0; a < new_dialogs_dict.size(); a++) {
                    long key = new_dialogs_dict.keyAt(a);
                    TLRPC.Dialog value = new_dialogs_dict.valueAt(a);
                    if (value.draft instanceof TLRPC.TL_draftMessage) {
                        mediaDataController.saveDraft(value.id, 0, value.draft, null, false);
                    }
                    dialogs_dict.put(key, value);
                    MessageObject messageObject = new_dialogMessage.get(value.id);
                    dialogMessage.put(key, messageObject);
                    if (messageObject != null && messageObject.messageOwner.peer_id.channel_id == 0) {
                        dialogMessagesByIds.put(messageObject.getId(), messageObject);
                        dialogsLoadedTillDate = Math.min(dialogsLoadedTillDate, messageObject.messageOwner.date);
                        if (messageObject.messageOwner.random_id != 0) {
                            dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
                        }
                    }
                }

                allDialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    TLRPC.Dialog dialog = dialogs_dict.valueAt(a);
                    if (deletingDialogs.indexOfKey(dialog.id) >= 0) {
                        continue;
                    }
                    allDialogs.add(dialog);
                }
                sortDialogs(null);
                dialogsEndReached.put(0, true);
                serverDialogsEndReached.put(0, false);

                dialogsEndReached.put(1, true);
                serverDialogsEndReached.put(1, false);

                int totalDialogsLoadCount = getUserConfig().getTotalDialogsCount(0);
                long[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(0);
                if (totalDialogsLoadCount < 400 && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1 && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != Integer.MAX_VALUE) {
                    loadDialogs(0, 0, 100, false);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            });
        });
    }

    private void migrateDialogs(int offset, int offsetDate, long offsetUser, long offsetChat, long offsetChannel, long accessPeer) {
        if (migratingDialogs || offset == -1) {
            return;
        }
        migratingDialogs = true;

        TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
        req.exclude_pinned = true;
        req.limit = 100;
        req.offset_id = offset;
        req.offset_date = offsetDate;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start migrate with id " + offset + " date " + LocaleController.getInstance().formatterStats.format((long) offsetDate * 1000));
        }
        if (offset == 0) {
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        } else {
            if (offsetChannel != 0) {
                req.offset_peer = new TLRPC.TL_inputPeerChannel();
                req.offset_peer.channel_id = offsetChannel;
            } else if (offsetUser != 0) {
                req.offset_peer = new TLRPC.TL_inputPeerUser();
                req.offset_peer.user_id = offsetUser;
            } else {
                req.offset_peer = new TLRPC.TL_inputPeerChat();
                req.offset_peer.chat_id = offsetChat;
            }
            req.offset_peer.access_hash = accessPeer;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    try {
                        int offsetId;
                        int totalDialogsLoadCount = getUserConfig().getTotalDialogsCount(0);
                        getUserConfig().setTotalDialogsCount(0, totalDialogsLoadCount + dialogsRes.dialogs.size());
                        TLRPC.Message lastMessage = null;
                        for (int a = 0; a < dialogsRes.messages.size(); a++) {
                            TLRPC.Message message = dialogsRes.messages.get(a);
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("search migrate id " + message.id + " date " + LocaleController.getInstance().formatterStats.format((long) message.date * 1000));
                            }
                            if (lastMessage == null || message.date < lastMessage.date) {
                                lastMessage = message;
                            }
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("migrate step with id " + lastMessage.id + " date " + LocaleController.getInstance().formatterStats.format((long) lastMessage.date * 1000));
                        }
                        if (dialogsRes.dialogs.size() >= 100) {
                            offsetId = lastMessage.id;
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("migrate stop due to not 100 dialogs");
                            }
                            for (int i = 0; i < 2; i++) {
                                getUserConfig().setDialogsLoadOffset(i,
                                        Integer.MAX_VALUE,
                                        getUserConfig().migrateOffsetDate,
                                        getUserConfig().migrateOffsetUserId,
                                        getUserConfig().migrateOffsetChatId,
                                        getUserConfig().migrateOffsetChannelId,
                                        getUserConfig().migrateOffsetAccess);
                            }
                            offsetId = -1;
                        }

                        StringBuilder dids = new StringBuilder(dialogsRes.dialogs.size() * 12);
                        LongSparseArray<TLRPC.Dialog> dialogHashMap = new LongSparseArray<>();
                        for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                            TLRPC.Dialog dialog = dialogsRes.dialogs.get(a);
                            DialogObject.initDialog(dialog);
                            if (dids.length() > 0) {
                                dids.append(",");
                            }
                            dids.append(dialog.id);
                            dialogHashMap.put(dialog.id, dialog);
                        }
                        SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT did, folder_id FROM dialogs WHERE did IN (%s)", dids.toString()));
                        while (cursor.next()) {
                            long did = cursor.longValue(0);
                            int folder_id = cursor.intValue(1);
                            TLRPC.Dialog dialog = dialogHashMap.get(did);
                            if (dialog != null) {
                                if (dialog.folder_id != folder_id) {
                                    continue;
                                }
                                dialogsRes.dialogs.remove(dialog);
                                for (int a = 0; a < dialogsRes.messages.size(); a++) {
                                    TLRPC.Message message = dialogsRes.messages.get(a);
                                    if (MessageObject.getDialogId(message) != did) {
                                        continue;
                                    }
                                    dialogsRes.messages.remove(a);
                                    a--;
                                    if (message.id == dialog.top_message) {
                                        dialog.top_message = 0;
                                        break;
                                    }
                                }
                            }
                            dialogHashMap.remove(did);
                        }
                        cursor.dispose();
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("migrate found missing dialogs " + dialogsRes.dialogs.size());
                        }
                        cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT min(date) FROM dialogs WHERE date != 0 AND did >> 32 NOT IN (536870912, 1073741824)");
                        if (cursor.next()) {
                            int date = Math.max(1441062000, cursor.intValue(0));
                            for (int a = 0; a < dialogsRes.messages.size(); a++) {
                                TLRPC.Message message = dialogsRes.messages.get(a);
                                if (message.date < date) {
                                    if (offset != -1) {
                                        for (int i = 0; i < 2; i++) {
                                            getUserConfig().setDialogsLoadOffset(i,
                                                    getUserConfig().migrateOffsetId,
                                                    getUserConfig().migrateOffsetDate,
                                                    getUserConfig().migrateOffsetUserId,
                                                    getUserConfig().migrateOffsetChatId,
                                                    getUserConfig().migrateOffsetChannelId,
                                                    getUserConfig().migrateOffsetAccess);
                                        }
                                        offsetId = -1;
                                        if (BuildVars.LOGS_ENABLED) {
                                            FileLog.d("migrate stop due to reached loaded dialogs " + LocaleController.getInstance().formatterStats.format((long) date * 1000));
                                        }
                                    }
                                    dialogsRes.messages.remove(a);
                                    a--;
                                    long did = MessageObject.getDialogId(message);
                                    TLRPC.Dialog dialog = dialogHashMap.get(did);
                                    dialogHashMap.remove(did);
                                    if (dialog != null) {
                                        dialogsRes.dialogs.remove(dialog);
                                    }
                                }
                            }
                            if (lastMessage != null && lastMessage.date < date && offset != -1) {
                                for (int i = 0; i < 2; i++) {
                                    getUserConfig().setDialogsLoadOffset(i,
                                            getUserConfig().migrateOffsetId,
                                            getUserConfig().migrateOffsetDate,
                                            getUserConfig().migrateOffsetUserId,
                                            getUserConfig().migrateOffsetChatId,
                                            getUserConfig().migrateOffsetChannelId,
                                            getUserConfig().migrateOffsetAccess);
                                }
                                offsetId = -1;
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("migrate stop due to reached loaded dialogs " + LocaleController.getInstance().formatterStats.format((long) date * 1000));
                                }
                            }
                        }
                        cursor.dispose();

                        getUserConfig().migrateOffsetDate = lastMessage.date;
                        if (lastMessage.peer_id.channel_id != 0) {
                            getUserConfig().migrateOffsetChannelId = lastMessage.peer_id.channel_id;
                            getUserConfig().migrateOffsetChatId = 0;
                            getUserConfig().migrateOffsetUserId = 0;
                            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                TLRPC.Chat chat = dialogsRes.chats.get(a);
                                if (chat.id == getUserConfig().migrateOffsetChannelId) {
                                    getUserConfig().migrateOffsetAccess = chat.access_hash;
                                    break;
                                }
                            }
                        } else if (lastMessage.peer_id.chat_id != 0) {
                            getUserConfig().migrateOffsetChatId = lastMessage.peer_id.chat_id;
                            getUserConfig().migrateOffsetChannelId = 0;
                            getUserConfig().migrateOffsetUserId = 0;
                            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                TLRPC.Chat chat = dialogsRes.chats.get(a);
                                if (chat.id == getUserConfig().migrateOffsetChatId) {
                                    getUserConfig().migrateOffsetAccess = chat.access_hash;
                                    break;
                                }
                            }
                        } else if (lastMessage.peer_id.user_id != 0) {
                            getUserConfig().migrateOffsetUserId = lastMessage.peer_id.user_id;
                            getUserConfig().migrateOffsetChatId = 0;
                            getUserConfig().migrateOffsetChannelId = 0;
                            for (int a = 0; a < dialogsRes.users.size(); a++) {
                                TLRPC.User user = dialogsRes.users.get(a);
                                if (user.id == getUserConfig().migrateOffsetUserId) {
                                    getUserConfig().migrateOffsetAccess = user.access_hash;
                                    break;
                                }
                            }
                        }

                        processLoadedDialogs(dialogsRes, null, 0, offsetId, 0, 0, false, true, false);
                    } catch (Exception e) {
                        FileLog.e(e);
                        AndroidUtilities.runOnUIThread(() -> migratingDialogs = false);
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> migratingDialogs = false);
            }
        });
    }

    private int DIALOGS_LOAD_TYPE_CACHE = 1;
    private int DIALOGS_LOAD_TYPE_CHANNEL = 2;
    private int DIALOGS_LOAD_TYPE_UNKNOWN = 3;

    public void processLoadedDialogs(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats, int folderId, int offset, int count, int loadType, boolean resetEnd, boolean migrate, boolean fromCache) {
        Utilities.stageQueue.postRunnable(() -> {
            if (!firstGettingTask) {
                getNewDeleteTask(null, null);
                firstGettingTask = true;
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("loaded folderId " + folderId + " loadType " + loadType + " count " + dialogsRes.dialogs.size());
            }
            long[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(folderId);
            if (loadType == DIALOGS_LOAD_TYPE_CACHE && dialogsRes.dialogs.size() == 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(dialogsRes.users, true);
                    loadingDialogs.put(folderId, false);
                    if (resetEnd) {
                        dialogsEndReached.put(folderId, false);
                        serverDialogsEndReached.put(folderId, false);
                    } else if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] == Integer.MAX_VALUE) {
                        dialogsEndReached.put(folderId, true);
                        serverDialogsEndReached.put(folderId, true);
                    } else {
                        loadDialogs(folderId, 0, count, false);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                });
                return;
            }

            LongSparseArray<TLRPC.Dialog> new_dialogs_dict = new LongSparseArray<>();
            SparseArray<TLRPC.EncryptedChat> enc_chats_dict;
            LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
            LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
            LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();

            for (int a = 0; a < dialogsRes.users.size(); a++) {
                TLRPC.User u = dialogsRes.users.get(a);
                usersDict.put(u.id, u);
            }
            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                TLRPC.Chat c = dialogsRes.chats.get(a);
                chatsDict.put(c.id, c);
            }
            if (encChats != null) {
                enc_chats_dict = new SparseArray<>();
                for (int a = 0, N = encChats.size(); a < N; a++) {
                    TLRPC.EncryptedChat encryptedChat = encChats.get(a);
                    enc_chats_dict.put(encryptedChat.id, encryptedChat);
                }
            } else {
                enc_chats_dict = null;
            }
            if (loadType == DIALOGS_LOAD_TYPE_CACHE) {
                nextDialogsCacheOffset.put(folderId, offset + count);
            }

            TLRPC.Message lastMessage = null;
            for (int a = 0; a < dialogsRes.messages.size(); a++) {
                TLRPC.Message message = dialogsRes.messages.get(a);
                if (lastMessage == null || message.date < lastMessage.date) {
                    lastMessage = message;
                }
                if (message.peer_id.channel_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.peer_id.channel_id);
                    if (chat != null && chat.left && (promoDialogId == 0 || promoDialogId != -chat.id)) {
                        continue;
                    }
                } else if (message.peer_id.chat_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.peer_id.chat_id);
                    if (chat != null && chat.migrated_to != null) {
                        continue;
                    }
                }
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false, true);
                new_dialogMessage.put(messageObject.getDialogId(), messageObject);
            }

            if (!fromCache && !migrate && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1 && loadType == 0) {
                int totalDialogsLoadCount = getUserConfig().getTotalDialogsCount(folderId);
                int dialogsLoadOffsetId;
                int dialogsLoadOffsetDate = 0;
                long dialogsLoadOffsetChannelId = 0;
                long dialogsLoadOffsetChatId = 0;
                long dialogsLoadOffsetUserId = 0;
                long dialogsLoadOffsetAccess = 0;
                if (lastMessage != null && lastMessage.id != dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId]) {
                    totalDialogsLoadCount += dialogsRes.dialogs.size();
                    dialogsLoadOffsetId = lastMessage.id;
                    dialogsLoadOffsetDate = lastMessage.date;
                    if (lastMessage.peer_id.channel_id != 0) {
                        dialogsLoadOffsetChannelId = lastMessage.peer_id.channel_id;
                        dialogsLoadOffsetChatId = 0;
                        dialogsLoadOffsetUserId = 0;
                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                            if (chat.id == dialogsLoadOffsetChannelId) {
                                dialogsLoadOffsetAccess = chat.access_hash;
                                break;
                            }
                        }
                    } else if (lastMessage.peer_id.chat_id != 0) {
                        dialogsLoadOffsetChatId = lastMessage.peer_id.chat_id;
                        dialogsLoadOffsetChannelId = 0;
                        dialogsLoadOffsetUserId = 0;
                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                            if (chat.id == dialogsLoadOffsetChatId) {
                                dialogsLoadOffsetAccess = chat.access_hash;
                                break;
                            }
                        }
                    } else if (lastMessage.peer_id.user_id != 0) {
                        dialogsLoadOffsetUserId = lastMessage.peer_id.user_id;
                        dialogsLoadOffsetChatId = 0;
                        dialogsLoadOffsetChannelId = 0;
                        for (int a = 0; a < dialogsRes.users.size(); a++) {
                            TLRPC.User user = dialogsRes.users.get(a);
                            if (user.id == dialogsLoadOffsetUserId) {
                                dialogsLoadOffsetAccess = user.access_hash;
                                break;
                            }
                        }
                    }
                } else {
                    dialogsLoadOffsetId = Integer.MAX_VALUE;
                }
                getUserConfig().setDialogsLoadOffset(folderId,
                        dialogsLoadOffsetId,
                        dialogsLoadOffsetDate,
                        dialogsLoadOffsetUserId,
                        dialogsLoadOffsetChatId,
                        dialogsLoadOffsetChannelId,
                        dialogsLoadOffsetAccess);
                getUserConfig().setTotalDialogsCount(folderId, totalDialogsLoadCount);
                getUserConfig().saveConfig(false);
            }

            ArrayList<TLRPC.Dialog> dialogsToReload = new ArrayList<>();
            for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                TLRPC.Dialog d = dialogsRes.dialogs.get(a);
                DialogObject.initDialog(d);
                if (d.id == 0) {
                    continue;
                }
                if (DialogObject.isEncryptedDialog(d.id) && enc_chats_dict != null) {
                    if (enc_chats_dict.get(DialogObject.getEncryptedChatId(d.id)) == null) {
                        continue;
                    }
                }
                if (promoDialogId != 0 && promoDialogId == d.id) {
                    promoDialog = d;
                }
                if (d.last_message_date == 0) {
                    MessageObject mess = new_dialogMessage.get(d.id);
                    if (mess != null) {
                        d.last_message_date = mess.messageOwner.date;
                    }
                }
                boolean allowCheck = true;
                if (DialogObject.isChannel(d)) {
                    TLRPC.Chat chat = chatsDict.get(-d.id);
                    if (chat != null) {
                        if (!chat.megagroup) {
                            allowCheck = false;
                        }
                        if (ChatObject.isNotInChat(chat) && (promoDialogId == 0 || promoDialogId != d.id)) {
                            continue;
                        }
                    }
                    channelsPts.put(-d.id, d.pts);
                } else if (DialogObject.isChatDialog(d.id)) {
                    TLRPC.Chat chat = chatsDict.get(-d.id);
                    if (chat != null && (chat.migrated_to != null || ChatObject.isNotInChat(chat))) {
                        continue;
                    }
                }
                new_dialogs_dict.put(d.id, d);

                if (allowCheck && loadType == DIALOGS_LOAD_TYPE_CACHE && (d.read_outbox_max_id == 0 || d.read_inbox_max_id == 0) && d.top_message != 0) {
                    dialogsToReload.add(d);
                }

                Integer value = dialogs_read_inbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_inbox_max.put(d.id, Math.max(value, d.read_inbox_max_id));

                value = dialogs_read_outbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_outbox_max.put(d.id, Math.max(value, d.read_outbox_max_id));
            }

            if (loadType != DIALOGS_LOAD_TYPE_CACHE) {
                ImageLoader.saveMessagesThumbs(dialogsRes.messages);

                for (int a = 0; a < dialogsRes.messages.size(); a++) {
                    TLRPC.Message message = dialogsRes.messages.get(a);
                    if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                        TLRPC.User user = usersDict.get(message.action.user_id);
                        if (user != null && user.bot) {
                            message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                            message.flags |= 64;
                        }
                    }

                    if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                        message.unread = false;
                        message.media_unread = false;
                    } else {
                        ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                        Integer value = read_max.get(message.dialog_id);
                        if (value == null) {
                            value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                            read_max.put(message.dialog_id, value);
                        }
                        message.unread = value < message.id;
                    }
                }
                getMessagesStorage().putDialogs(dialogsRes, loadType == DIALOGS_LOAD_TYPE_UNKNOWN ? 3 : 0);
            }
            if (loadType == DIALOGS_LOAD_TYPE_CHANNEL) {
                TLRPC.Chat chat = dialogsRes.chats.get(0);
                getChannelDifference(chat.id);
                AndroidUtilities.runOnUIThread(() -> checkChatInviter(chat.id, true));
            }

            TLRPC.Message lastMessageFinal = lastMessage;
            AndroidUtilities.runOnUIThread(() -> {
                if (lastMessageFinal != null) {
                    dialogsLoadedTillDate = Math.min(dialogsLoadedTillDate, lastMessageFinal.date);
                } else {
                    dialogsLoadedTillDate = Integer.MIN_VALUE;
                }
                if (loadType != DIALOGS_LOAD_TYPE_CACHE) {
                    applyDialogsNotificationsSettings(dialogsRes.dialogs);
                    getMediaDataController().loadDraftsIfNeed();
                }
                putUsers(dialogsRes.users, loadType == DIALOGS_LOAD_TYPE_CACHE);
                putChats(dialogsRes.chats, loadType == DIALOGS_LOAD_TYPE_CACHE);
                if (encChats != null) {
                    for (int a = 0; a < encChats.size(); a++) {
                        TLRPC.EncryptedChat encryptedChat = encChats.get(a);
                        if (encryptedChat instanceof TLRPC.TL_encryptedChat && AndroidUtilities.getMyLayerVersion(encryptedChat.layer) < SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
                            getSecretChatHelper().sendNotifyLayerMessage(encryptedChat, null);
                        }
                        putEncryptedChat(encryptedChat, true);
                    }
                }
                if (!migrate && loadType != DIALOGS_LOAD_TYPE_UNKNOWN && loadType != DIALOGS_LOAD_TYPE_CHANNEL) {
                    loadingDialogs.put(folderId, false);
                }
                boolean added = false;
                dialogsLoaded = true;

                int archivedDialogsCount = 0;
                int lastDialogDate = migrate && !allDialogs.isEmpty() ? allDialogs.get(allDialogs.size() - 1).last_message_date : 0;
                for (int a = 0; a < new_dialogs_dict.size(); a++) {
                    long key = new_dialogs_dict.keyAt(a);
                    TLRPC.Dialog value = new_dialogs_dict.valueAt(a);
                    TLRPC.Dialog currentDialog = dialogs_dict.get(key);
                    if (migrate && currentDialog != null) {
                        currentDialog.folder_id = value.folder_id;
                    }
                    if (migrate && lastDialogDate != 0 && value.last_message_date < lastDialogDate) {
                        continue;
                    }
                    if (loadType != DIALOGS_LOAD_TYPE_CACHE && value.draft instanceof TLRPC.TL_draftMessage) {
                        getMediaDataController().saveDraft(value.id, 0, value.draft, null, false);
                    }
                    if (value.folder_id != folderId) {
                        archivedDialogsCount++;
                    }
                    MessageObject newMsg = new_dialogMessage.get(value.id);
                    if (currentDialog == null) {
                        added = true;
                        dialogs_dict.put(key, value);
                        dialogMessage.put(key, newMsg);
                        if (newMsg != null && newMsg.messageOwner.peer_id.channel_id == 0) {
                            dialogMessagesByIds.put(newMsg.getId(), newMsg);
                            if (newMsg.messageOwner.random_id != 0) {
                                dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                            }
                        }
                    } else {
                        if (loadType != DIALOGS_LOAD_TYPE_CACHE) {
                            currentDialog.notify_settings = value.notify_settings;
                        }
                        currentDialog.pinned = value.pinned;
                        currentDialog.pinnedNum = value.pinnedNum;
                        MessageObject oldMsg = dialogMessage.get(key);
                        if (oldMsg != null && oldMsg.deleted || oldMsg == null || currentDialog.top_message > 0) {
                            if (value.top_message >= currentDialog.top_message) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(key, newMsg);
                                if (oldMsg != null) {
                                    if (oldMsg.messageOwner.peer_id.channel_id == 0) {
                                        dialogMessagesByIds.remove(oldMsg.getId());
                                    }
                                    if (oldMsg.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                    }
                                }
                                if (newMsg != null) {
                                    if (oldMsg != null && oldMsg.getId() == newMsg.getId()) {
                                        newMsg.deleted = oldMsg.deleted;
                                    }
                                    if (newMsg.messageOwner.peer_id.channel_id == 0) {
                                        dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                        if (newMsg.messageOwner.random_id != 0) {
                                            dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                        }
                                    }
                                }
                            }
                        } else {
                            if (newMsg == null && oldMsg.getId() > 0 || newMsg != null && newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(key, newMsg);
                                if (oldMsg.messageOwner.peer_id.channel_id == 0) {
                                    dialogMessagesByIds.remove(oldMsg.getId());
                                }
                                if (newMsg != null) {
                                    if (newMsg.messageOwner.peer_id.channel_id == 0) {
                                        dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                        if (newMsg.messageOwner.random_id != 0) {
                                            dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                        }
                                    }
                                }
                                if (oldMsg.messageOwner.random_id != 0) {
                                    dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                }
                            }
                        }
                    }
                }

                allDialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    TLRPC.Dialog dialog = dialogs_dict.valueAt(a);
                    if (deletingDialogs.indexOfKey(dialog.id) >= 0) {
                        continue;
                    }
                    allDialogs.add(dialog);
                }
                sortDialogs(migrate ? chatsDict : null);
                
                putAllNeededDraftDialogs();

                if (loadType != DIALOGS_LOAD_TYPE_CHANNEL && loadType != DIALOGS_LOAD_TYPE_UNKNOWN) {
                    if (!migrate) {
                        dialogsEndReached.put(folderId, (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && loadType == 0);
                        if (archivedDialogsCount > 0 && archivedDialogsCount < 20 && folderId == 0) {
                            dialogsEndReached.put(1, true);
                            long[] dialogsLoadOffsetArchived = getUserConfig().getDialogLoadOffsets(folderId);
                            if (dialogsLoadOffsetArchived[UserConfig.i_dialogsLoadOffsetId] == Integer.MAX_VALUE) {
                                serverDialogsEndReached.put(1, true);
                            }
                        }
                        if (!fromCache) {
                            serverDialogsEndReached.put(folderId, (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && loadType == 0);
                        }
                    }
                }
                int totalDialogsLoadCount = getUserConfig().getTotalDialogsCount(folderId);
                long[] dialogsLoadOffset2 = getUserConfig().getDialogLoadOffsets(folderId);
                if (!fromCache && !migrate && totalDialogsLoadCount < 400 && dialogsLoadOffset2[UserConfig.i_dialogsLoadOffsetId] != -1 && dialogsLoadOffset2[UserConfig.i_dialogsLoadOffsetId] != Integer.MAX_VALUE) {
                    loadDialogs(folderId, 0, 100, false);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);

                if (migrate) {
                    getUserConfig().migrateOffsetId = offset;
                    getUserConfig().saveConfig(false);
                    migratingDialogs = false;
                    getNotificationCenter().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
                } else {
                    generateUpdateMessage();
                    if (!added && loadType == DIALOGS_LOAD_TYPE_CACHE && dialogsEndReached.get(folderId)) {
                        loadDialogs(folderId, 0, count, false);
                    }
                }
                migrateDialogs(getUserConfig().migrateOffsetId, getUserConfig().migrateOffsetDate, getUserConfig().migrateOffsetUserId, getUserConfig().migrateOffsetChatId, getUserConfig().migrateOffsetChannelId, getUserConfig().migrateOffsetAccess);
                if (!dialogsToReload.isEmpty()) {
                    reloadDialogsReadValue(dialogsToReload, 0);
                }
                loadUnreadDialogs();
            });
        });
    }

    private void applyDialogNotificationsSettings(long dialogId, TLRPC.PeerNotifySettings notify_settings) {
        if (notify_settings == null) {
            return;
        }
        int currentValue = notificationsPreferences.getInt("notify2_" + dialogId, -1);
        int currentValue2 = notificationsPreferences.getInt("notifyuntil_" + dialogId, 0);
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        boolean updated = false;
        TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
        if (dialog != null) {
            dialog.notify_settings = notify_settings;
        }
        if ((notify_settings.flags & 2) != 0) {
            editor.putBoolean("silent_" + dialogId, notify_settings.silent);
        } else {
            editor.remove("silent_" + dialogId);
        }
        if ((notify_settings.flags & 4) != 0) {
            if (notify_settings.mute_until > getConnectionsManager().getCurrentTime()) {
                int until = 0;
                if (notify_settings.mute_until > getConnectionsManager().getCurrentTime() + 60 * 60 * 24 * 365) {
                    if (currentValue != 2) {
                        updated = true;
                        editor.putInt("notify2_" + dialogId, 2);
                        if (dialog != null) {
                            dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                        }
                    }
                } else {
                    if (currentValue != 3 || currentValue2 != notify_settings.mute_until) {
                        updated = true;
                        editor.putInt("notify2_" + dialogId, 3);
                        editor.putInt("notifyuntil_" + dialogId, notify_settings.mute_until);
                        if (dialog != null) {
                            dialog.notify_settings.mute_until = until;
                        }
                    }
                    until = notify_settings.mute_until;
                }
                getMessagesStorage().setDialogFlags(dialogId, ((long) until << 32) | 1);
                getNotificationsController().removeNotificationsForDialog(dialogId);
            } else {
                if (currentValue != 0 && currentValue != 1) {
                    updated = true;
                    if (dialog != null) {
                        dialog.notify_settings.mute_until = 0;
                    }
                    editor.putInt("notify2_" + dialogId, 0);
                }
                getMessagesStorage().setDialogFlags(dialogId, 0);
            }
        } else {
            if (currentValue != -1) {
                updated = true;
                if (dialog != null) {
                    dialog.notify_settings.mute_until = 0;
                }
                editor.remove("notify2_" + dialogId);
            }
            getMessagesStorage().setDialogFlags(dialogId, 0);
        }
        editor.commit();
        if (updated) {
            getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
        }
    }

    private void applyDialogsNotificationsSettings(ArrayList<TLRPC.Dialog> dialogs) {
        SharedPreferences.Editor editor = null;
        for (int a = 0; a < dialogs.size(); a++) {
            TLRPC.Dialog dialog = dialogs.get(a);
            if (dialog.peer != null && dialog.notify_settings instanceof TLRPC.TL_peerNotifySettings) {
                if (editor == null) {
                    editor = notificationsPreferences.edit();
                }
                long dialogId = MessageObject.getPeerId(dialog.peer);
                if ((dialog.notify_settings.flags & 2) != 0) {
                    editor.putBoolean("silent_" + dialogId, dialog.notify_settings.silent);
                } else {
                    editor.remove("silent_" + dialogId);
                }
                if ((dialog.notify_settings.flags & 4) != 0) {
                    if (dialog.notify_settings.mute_until > getConnectionsManager().getCurrentTime()) {
                        if (dialog.notify_settings.mute_until > getConnectionsManager().getCurrentTime() + 60 * 60 * 24 * 365) {
                            editor.putInt("notify2_" + dialogId, 2);
                            dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                        } else {
                            editor.putInt("notify2_" + dialogId, 3);
                            editor.putInt("notifyuntil_" + dialogId, dialog.notify_settings.mute_until);
                        }
                    } else {
                        editor.putInt("notify2_" + dialogId, 0);
                    }
                } else {
                    editor.remove("notify2_" + dialogId);
                }
            }
        }
        if (editor != null) {
            editor.commit();
        }
    }

    public void reloadMentionsCountForChannel(TLRPC.InputPeer peer, long taskId) {
        long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(4 + peer.getObjectSize());
                data.writeInt32(22);
                peer.serializeToStream(data);
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        TLRPC.TL_messages_getUnreadMentions req = new TLRPC.TL_messages_getUnreadMentions();
        req.peer = peer;
        req.limit = 1;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            if (res != null) {
                int newCount;
                if (res.count != 0) {
                    newCount = res.count;
                } else {
                    newCount = res.messages.size();
                }
                getMessagesStorage().resetMentionsCount(-peer.channel_id, newCount);
            }
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
        });
    }

    public void reloadMentionsCountForChannels(final ArrayList<Long> arrayList) {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < arrayList.size(); a++) {
                long dialogId = -arrayList.get(a);
                reloadMentionsCountForChannel(getInputPeer(dialogId), 0);
            }
        });
    }

    public void processDialogsUpdateRead(final LongSparseIntArray dialogsToUpdate, LongSparseIntArray dialogsMentionsToUpdate) {
        AndroidUtilities.runOnUIThread(() -> {
            boolean filterDialogsChanged = false;
            if (dialogsToUpdate != null) {
                for (int a = 0; a < dialogsToUpdate.size(); a++) {
                    long dialogId = dialogsToUpdate.keyAt(a);
                    TLRPC.Dialog currentDialog = dialogs_dict.get(dialogId);
                    if (currentDialog != null) {
                        int prevCount = currentDialog.unread_count;
                        currentDialog.unread_count = dialogsToUpdate.valueAt(a);
                        if (prevCount != 0 && currentDialog.unread_count == 0) {
                            if (!isDialogMuted(dialogId)) {
                                unreadUnmutedDialogs--;
                            }
                            if (!filterDialogsChanged) {
                                for (int b = 0; b < selectedDialogFilter.length; b++) {
                                    if (selectedDialogFilter[b] != null && (selectedDialogFilter[b].flags & DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                                        filterDialogsChanged = true;
                                        break;
                                    }
                                }
                            }
                        } else if (prevCount == 0 && !currentDialog.unread_mark && currentDialog.unread_count != 0) {
                            if (!isDialogMuted(dialogId)) {
                                unreadUnmutedDialogs++;
                            }
                            if (!filterDialogsChanged) {
                                for (int b = 0; b < selectedDialogFilter.length; b++) {
                                    if (selectedDialogFilter[b] != null && (selectedDialogFilter[b].flags & DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                                        filterDialogsChanged = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (dialogsMentionsToUpdate != null) {
                for (int a = 0; a < dialogsMentionsToUpdate.size(); a++) {
                    long dialogId = dialogsMentionsToUpdate.keyAt(a);
                    TLRPC.Dialog currentDialog = dialogs_dict.get(dialogId);
                    if (currentDialog != null) {
                        currentDialog.unread_mentions_count = dialogsMentionsToUpdate.valueAt(a);
                        if (createdDialogMainThreadIds.contains(currentDialog.id)) {
                            getNotificationCenter().postNotificationName(NotificationCenter.updateMentionsCount, currentDialog.id, currentDialog.unread_mentions_count);
                        }
                        if (!filterDialogsChanged) {
                            for (int b = 0; b < selectedDialogFilter.length; b++) {
                                if (selectedDialogFilter[b] != null && ((selectedDialogFilter[b].flags & DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0 || (selectedDialogFilter[b].flags & DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0)) {
                                    filterDialogsChanged = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (filterDialogsChanged) {
                sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
            if (dialogsToUpdate != null) {
                getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
            }
        });
    }

    protected void checkLastDialogMessage(TLRPC.Dialog dialog, TLRPC.InputPeer peer, long taskId) {
        if (DialogObject.isEncryptedDialog(dialog.id) || checkingLastMessagesDialogs.indexOfKey(dialog.id) >= 0) {
            return;
        }
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer == null ? getInputPeer(dialog.id) : peer;
        if (req.peer == null) {
            return;
        }
        req.limit = 1;
        checkingLastMessagesDialogs.put(dialog.id, true);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("checkLastDialogMessage for " + dialog.id);
        }

        long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(60 + req.peer.getObjectSize());
                data.writeInt32(14);
                data.writeInt64(dialog.id);
                data.writeInt32(dialog.top_message);
                data.writeInt32(dialog.read_inbox_max_id);
                data.writeInt32(dialog.read_outbox_max_id);
                data.writeInt32(dialog.unread_count);
                data.writeInt32(dialog.last_message_date);
                data.writeInt32(dialog.pts);
                data.writeInt32(dialog.flags);
                data.writeBool(dialog.pinned);
                data.writeInt32(dialog.pinnedNum);
                data.writeInt32(dialog.unread_mentions_count);
                data.writeBool(dialog.unread_mark);
                data.writeInt32(dialog.folder_id);
                req.peer.serializeToStream(data);
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }

        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                removeDeletedMessagesFromArray(dialog.id, res.messages);
                if (!res.messages.isEmpty()) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("checkLastDialogMessage for " + dialog.id + " has message");
                    }
                    TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                    TLRPC.Message newMessage = res.messages.get(0);
                    TLRPC.Dialog newDialog = new TLRPC.TL_dialog();
                    newDialog.flags = dialog.flags;
                    newDialog.top_message = newMessage.id;
                    newDialog.last_message_date = newMessage.date;
                    newDialog.notify_settings = dialog.notify_settings;
                    newDialog.pts = dialog.pts;
                    newDialog.unread_count = dialog.unread_count;
                    newDialog.unread_mark = dialog.unread_mark;
                    newDialog.unread_mentions_count = dialog.unread_mentions_count;
                    newDialog.read_inbox_max_id = dialog.read_inbox_max_id;
                    newDialog.read_outbox_max_id = dialog.read_outbox_max_id;
                    newDialog.pinned = dialog.pinned;
                    newDialog.pinnedNum = dialog.pinnedNum;
                    newDialog.folder_id = dialog.folder_id;
                    newMessage.dialog_id = newDialog.id = dialog.id;
                    dialogs.users.addAll(res.users);
                    dialogs.chats.addAll(res.chats);
                    dialogs.dialogs.add(newDialog);
                    dialogs.messages.addAll(res.messages);
                    dialogs.count = 1;
                    processDialogsUpdate(dialogs, null, false);
                    getMessagesStorage().putMessages(res.messages, true, true, false, getDownloadController().getAutodownloadMask(), true, false);
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("checkLastDialogMessage for " + dialog.id + " has not message");
                        }
                        if (getMediaDataController().getDraft(dialog.id, 0) == null) {
                            TLRPC.Dialog currentDialog = dialogs_dict.get(dialog.id);
                            if (currentDialog == null) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("checkLastDialogMessage for " + dialog.id + " current dialog not found");
                                }
                                getMessagesStorage().isDialogHasTopMessage(dialog.id, () -> deleteDialog(dialog.id, 3));
                            } else {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("checkLastDialogMessage for " + dialog.id + " current dialog top message " + currentDialog.top_message);
                                }
                                if (currentDialog.top_message == 0) {
                                    deleteDialog(dialog.id, 3);
                                }
                            }
                        }
                    });
                }
            }
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
            AndroidUtilities.runOnUIThread(() -> checkingLastMessagesDialogs.delete(dialog.id));
        });
    }

    public void processDialogsUpdate(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats, boolean fromCache) {
        Utilities.stageQueue.postRunnable(() -> {
            LongSparseArray<TLRPC.Dialog> new_dialogs_dict = new LongSparseArray<>();
            LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
            LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>(dialogsRes.users.size());
            LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>(dialogsRes.chats.size());
            LongSparseIntArray dialogsToUpdate = new LongSparseIntArray();

            for (int a = 0; a < dialogsRes.users.size(); a++) {
                TLRPC.User u = dialogsRes.users.get(a);
                usersDict.put(u.id, u);
            }
            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                TLRPC.Chat c = dialogsRes.chats.get(a);
                chatsDict.put(c.id, c);
            }

            for (int a = 0; a < dialogsRes.messages.size(); a++) {
                TLRPC.Message message = dialogsRes.messages.get(a);
                if (promoDialogId == 0 || promoDialogId != message.dialog_id) {
                    if (message.peer_id.channel_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.peer_id.channel_id);
                        if (chat != null && ChatObject.isNotInChat(chat)) {
                            continue;
                        }
                    } else if (message.peer_id.chat_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.peer_id.chat_id);
                        if (chat != null && (chat.migrated_to != null || ChatObject.isNotInChat(chat))) {
                            continue;
                        }
                    }
                }
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false, true);
                new_dialogMessage.put(messageObject.getDialogId(), messageObject);
            }
            for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                TLRPC.Dialog d = dialogsRes.dialogs.get(a);
                DialogObject.initDialog(d);
                if (promoDialogId == 0 || promoDialogId != d.id) {
                    if (DialogObject.isChannel(d)) {
                        TLRPC.Chat chat = chatsDict.get(-d.id);
                        if (chat != null && ChatObject.isNotInChat(chat)) {
                            continue;
                        }
                    } else if (DialogObject.isChatDialog(d.id)) {
                        TLRPC.Chat chat = chatsDict.get(-d.id);
                        if (chat != null && (chat.migrated_to != null || ChatObject.isNotInChat(chat))) {
                            continue;
                        }
                    }
                }
                if (d.last_message_date == 0) {
                    MessageObject mess = new_dialogMessage.get(d.id);
                    if (mess != null) {
                        d.last_message_date = mess.messageOwner.date;
                    }
                }
                new_dialogs_dict.put(d.id, d);
                dialogsToUpdate.put(d.id, d.unread_count);

                Integer value = dialogs_read_inbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_inbox_max.put(d.id, Math.max(value, d.read_inbox_max_id));

                value = dialogs_read_outbox_max.get(d.id);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_outbox_max.put(d.id, Math.max(value, d.read_outbox_max_id));
            }

            AndroidUtilities.runOnUIThread(() -> {
                putUsers(dialogsRes.users, true);
                putChats(dialogsRes.chats, true);

                for (int a = 0; a < new_dialogs_dict.size(); a++) {
                    long key = new_dialogs_dict.keyAt(a);
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("processDialogsUpdate " + key);
                    }
                    TLRPC.Dialog value = new_dialogs_dict.valueAt(a);
                    TLRPC.Dialog currentDialog = dialogs_dict.get(key);
                    MessageObject newMsg = new_dialogMessage.get(value.id);
                    if (currentDialog == null) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("processDialogsUpdate dialog null");
                        }
                        int offset = nextDialogsCacheOffset.get(value.folder_id, 0) + 1;
                        nextDialogsCacheOffset.put(value.folder_id, offset);
                        dialogs_dict.put(key, value);
                        dialogMessage.put(key, newMsg);
                        if (newMsg == null) {
                            if (fromCache) {
                                checkLastDialogMessage(value, null, 0);
                            }
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("processDialogsUpdate new message is null");
                            }
                        } else if (newMsg.messageOwner.peer_id.channel_id == 0) {
                            dialogMessagesByIds.put(newMsg.getId(), newMsg);
                            dialogsLoadedTillDate = Math.min(dialogsLoadedTillDate, newMsg.messageOwner.date);
                            if (newMsg.messageOwner.random_id != 0) {
                                dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                            }
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("processDialogsUpdate new message not null");
                            }
                        }
                    } else {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("processDialogsUpdate dialog not null");
                        }
                        currentDialog.unread_count = value.unread_count;
                        if (currentDialog.unread_mentions_count != value.unread_mentions_count) {
                            currentDialog.unread_mentions_count = value.unread_mentions_count;
                            if (createdDialogMainThreadIds.contains(currentDialog.id)) {
                                getNotificationCenter().postNotificationName(NotificationCenter.updateMentionsCount, currentDialog.id, currentDialog.unread_mentions_count);
                            }
                        }
                        MessageObject oldMsg = dialogMessage.get(key);
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("processDialogsUpdate oldMsg " + oldMsg + " old top_message = " + currentDialog.top_message + " new top_message = " + value.top_message);
                            FileLog.d("processDialogsUpdate oldMsgDeleted " + (oldMsg != null && oldMsg.deleted));
                        }
                        if (oldMsg == null || currentDialog.top_message > 0) {
                            if (oldMsg != null && oldMsg.deleted || value.top_message > currentDialog.top_message) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(key, newMsg);
                                if (oldMsg != null && oldMsg.messageOwner.peer_id.channel_id == 0) {
                                    dialogMessagesByIds.remove(oldMsg.getId());
                                    if (oldMsg.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                    }
                                }
                                if (newMsg != null) {
                                    if (oldMsg != null && oldMsg.getId() == newMsg.getId()) {
                                        newMsg.deleted = oldMsg.deleted;
                                    }
                                    if (newMsg.messageOwner.peer_id.channel_id == 0) {
                                        dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                        dialogsLoadedTillDate = Math.min(dialogsLoadedTillDate, newMsg.messageOwner.date);
                                        if (newMsg.messageOwner.random_id != 0) {
                                            dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                        }
                                    }
                                }
                            }
                            if (fromCache && newMsg == null) {
                                checkLastDialogMessage(value, null, 0);
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("processDialogsUpdate new message is null");
                                }
                            }
                        } else {
                            if (oldMsg.deleted || newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(key, newMsg);
                                if (oldMsg.messageOwner.peer_id.channel_id == 0) {
                                    dialogMessagesByIds.remove(oldMsg.getId());
                                }
                                if (newMsg != null) {
                                    if (oldMsg.getId() == newMsg.getId()) {
                                        newMsg.deleted = oldMsg.deleted;
                                    }
                                    if (newMsg.messageOwner.peer_id.channel_id == 0) {
                                        dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                        dialogsLoadedTillDate = Math.min(dialogsLoadedTillDate, newMsg.messageOwner.date);
                                        if (newMsg.messageOwner.random_id != 0) {
                                            dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                        }
                                    }
                                }
                                if (oldMsg.messageOwner.random_id != 0) {
                                    dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                }
                            }
                        }
                    }
                }

                allDialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    TLRPC.Dialog dialog = dialogs_dict.valueAt(a);
                    if (deletingDialogs.indexOfKey(dialog.id) >= 0) {
                        continue;
                    }
                    allDialogs.add(dialog);
                }
                sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
            });
        });
    }

    public void addToViewsQueue(MessageObject messageObject) {
        Utilities.stageQueue.postRunnable(() -> {
            long peer = messageObject.getDialogId();
            int id = messageObject.getId();
            ArrayList<Integer> ids = channelViewsToSend.get(peer);
            if (ids == null) {
                ids = new ArrayList<>();
                channelViewsToSend.put(peer, ids);
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        });
    }

    public void addToPollsQueue(long dialogId, ArrayList<MessageObject> visibleObjects) {
        SparseArray<MessageObject> array = pollsToCheck.get(dialogId);
        if (array == null) {
            array = new SparseArray<>();
            pollsToCheck.put(dialogId, array);
            pollsToCheckSize++;
        }
        for (int a = 0, N = array.size(); a < N; a++) {
            MessageObject object = array.valueAt(a);
            object.pollVisibleOnScreen = false;
        }
        int time = getConnectionsManager().getCurrentTime();
        int minExpireTime = Integer.MAX_VALUE;
        boolean hasExpiredPolls = false;
        for (int a = 0, N = visibleObjects.size(); a < N; a++) {
            MessageObject messageObject = visibleObjects.get(a);
            if (messageObject.type != MessageObject.TYPE_POLL) {
                continue;
            }
            TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
            if (!mediaPoll.poll.closed && mediaPoll.poll.close_date != 0) {
                if (mediaPoll.poll.close_date <= time) {
                    hasExpiredPolls = true;
                } else {
                    minExpireTime = Math.min(minExpireTime, mediaPoll.poll.close_date - time);
                }
            }
            int id = messageObject.getId();
            MessageObject object = array.get(id);
            if (object != null) {
                object.pollVisibleOnScreen = true;
            } else {
                array.put(id, messageObject);
            }
        }
        if (hasExpiredPolls) {
            lastViewsCheckTime = 0;
        } else if (minExpireTime < 5) {
            lastViewsCheckTime = Math.min(lastViewsCheckTime, System.currentTimeMillis() - (5 - minExpireTime) * 1000);
        }
    }

    public void markMessageContentAsRead(MessageObject messageObject) {
        if (messageObject.scheduled) {
            return;
        }
        ArrayList<Integer> arrayList = new ArrayList<>();
        if (messageObject.messageOwner.mentioned) {
            getMessagesStorage().markMentionMessageAsRead(-messageObject.messageOwner.peer_id.channel_id, messageObject.getId(), messageObject.getDialogId());
        }
        arrayList.add(messageObject.getId());
        long dialogId = messageObject.getDialogId();
        getMessagesStorage().markMessagesContentAsRead(dialogId, arrayList, 0);
        getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, dialogId, arrayList);
        if (messageObject.getId() < 0) {
            markMessageAsRead(messageObject.getDialogId(), messageObject.messageOwner.random_id, Integer.MIN_VALUE);
        } else {
            if (messageObject.messageOwner.peer_id.channel_id != 0) {
                TLRPC.TL_channels_readMessageContents req = new TLRPC.TL_channels_readMessageContents();
                req.channel = getInputChannel(messageObject.messageOwner.peer_id.channel_id);
                if (req.channel == null) {
                    return;
                }
                req.id.add(messageObject.getId());
                getConnectionsManager().sendRequest(req, (response, error) -> {

                });
            } else {
                TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
                req.id.add(messageObject.getId());
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    }
                });
            }
        }
    }

    public void markMentionMessageAsRead(int mid, long channelId, long did) {
        getMessagesStorage().markMentionMessageAsRead(-channelId, mid, did);
        if (channelId != 0) {
            TLRPC.TL_channels_readMessageContents req = new TLRPC.TL_channels_readMessageContents();
            req.channel = getInputChannel(channelId);
            if (req.channel == null) {
                return;
            }
            req.id.add(mid);
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        } else {
            TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
            req.id.add(mid);
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
            });
        }
    }

    public void markMessageAsRead2(long dialogId, int mid, TLRPC.InputChannel inputChannel, int ttl, long taskId) {
        if (mid == 0 || ttl <= 0) {
            return;
        }
        if (DialogObject.isChatDialog(dialogId) && inputChannel == null) {
            inputChannel = getInputChannel(dialogId);
            if (inputChannel == null) {
                return;
            }
        }
        long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(20 + (inputChannel != null ? inputChannel.getObjectSize() : 0));
                data.writeInt32(23);
                data.writeInt64(dialogId);
                data.writeInt32(mid);
                data.writeInt32(ttl);
                if (inputChannel != null) {
                    inputChannel.serializeToStream(data);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        int time = getConnectionsManager().getCurrentTime();
        getMessagesStorage().createTaskForMid(dialogId, mid, time, time, ttl, false);
        if (inputChannel != null) {
            TLRPC.TL_channels_readMessageContents req = new TLRPC.TL_channels_readMessageContents();
            req.channel = inputChannel;
            req.id.add(mid);
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            });
        } else {
            TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
            req.id.add(mid);
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            });
        }
    }

    public void markMessageAsRead(long dialogId, long randomId, int ttl) {
        if (randomId == 0 || dialogId == 0 || ttl <= 0 && ttl != Integer.MIN_VALUE) {
            return;
        }
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        TLRPC.EncryptedChat chat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
        if (chat == null) {
            return;
        }
        ArrayList<Long> randomIds = new ArrayList<>();
        randomIds.add(randomId);
        getSecretChatHelper().sendMessagesReadMessage(chat, randomIds, null);
        if (ttl > 0) {
            int time = getConnectionsManager().getCurrentTime();
            getMessagesStorage().createTaskForSecretChat(chat.id, time, time, 0, randomIds);
        }
    }

    private void completeReadTask(ReadTask task) {
        if (task.replyId != 0) {
            TLRPC.TL_messages_readDiscussion req = new TLRPC.TL_messages_readDiscussion();
            req.msg_id = (int) task.replyId;
            req.peer = getInputPeer(task.dialogId);
            req.read_max_id = task.maxId;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        } else if (!DialogObject.isEncryptedDialog(task.dialogId)) {
            TLRPC.InputPeer inputPeer = getInputPeer(task.dialogId);
            TLObject req;
            if (inputPeer instanceof TLRPC.TL_inputPeerChannel) {
                TLRPC.TL_channels_readHistory request = new TLRPC.TL_channels_readHistory();
                request.channel = getInputChannel(-task.dialogId);
                request.max_id = task.maxId;
                req = request;
            } else {
                TLRPC.TL_messages_readHistory request = new TLRPC.TL_messages_readHistory();
                request.peer = inputPeer;
                request.max_id = task.maxId;
                req = request;
            }
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    if (response instanceof TLRPC.TL_messages_affectedMessages) {
                        TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    }
                }
            });
        } else {
            TLRPC.EncryptedChat chat = getEncryptedChat(DialogObject.getEncryptedChatId(task.dialogId));
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_readEncryptedHistory req = new TLRPC.TL_messages_readEncryptedHistory();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.max_date = task.maxDate;
                getConnectionsManager().sendRequest(req, (response, error) -> {

                });
            }
        }
    }

    private void checkReadTasks() {
        long time = SystemClock.elapsedRealtime();
        for (int a = 0, size = readTasks.size(); a < size; a++) {
            ReadTask task = readTasks.get(a);
            if (task.sendRequestTime > time) {
                continue;
            }
            completeReadTask(task);
            readTasks.remove(a);
            readTasksMap.remove(task.dialogId);
            a--;
            size--;
        }
        for (int a = 0, size = repliesReadTasks.size(); a < size; a++) {
            ReadTask task = repliesReadTasks.get(a);
            if (task.sendRequestTime > time) {
                continue;
            }
            completeReadTask(task);
            repliesReadTasks.remove(a);
            threadsReadTasksMap.remove(task.dialogId + "_" + task.replyId);
            a--;
            size--;
        }
    }

    public void markDialogAsReadNow(long dialogId, int replyId) {
        Utilities.stageQueue.postRunnable(() -> {
            if (replyId != 0) {
                String key = dialogId + "_" + replyId;
                ReadTask currentReadTask = threadsReadTasksMap.get(key);
                if (currentReadTask == null) {
                    return;
                }
                completeReadTask(currentReadTask);
                repliesReadTasks.remove(currentReadTask);
                threadsReadTasksMap.remove(key);
            } else {
                ReadTask currentReadTask = readTasksMap.get(dialogId);
                if (currentReadTask == null) {
                    return;
                }
                completeReadTask(currentReadTask);
                readTasks.remove(currentReadTask);
                readTasksMap.remove(dialogId);
            }
        });
    }

    public void markMentionsAsRead(long dialogId) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        getMessagesStorage().resetMentionsCount(dialogId, 0);
        TLRPC.TL_messages_readMentions req = new TLRPC.TL_messages_readMentions();
        req.peer = getInputPeer(dialogId);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public void markDialogAsRead(long dialogId, int maxPositiveId, int maxNegativeId, int maxDate, boolean popup, int threadId, int countDiff, boolean readNow, int scheduledCount) {
        boolean createReadTask;

        if (threadId != 0) {
            createReadTask = maxPositiveId != Integer.MAX_VALUE;
        } else {
            boolean countMessages = getNotificationsController().showBadgeMessages;
            if (!DialogObject.isEncryptedDialog(dialogId)) {
                if (maxPositiveId == 0) {
                    return;
                }
                Integer value = dialogs_read_inbox_max.get(dialogId);
                if (value == null) {
                    value = 0;
                }
                dialogs_read_inbox_max.put(dialogId, Math.max(value, maxPositiveId));

                getMessagesStorage().processPendingRead(dialogId, maxPositiveId, maxNegativeId, scheduledCount);
                getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
                    if (dialog != null) {
                        int prevCount = dialog.unread_count;
                        if (countDiff == 0 || maxPositiveId >= dialog.top_message) {
                            dialog.unread_count = 0;
                        } else {
                            dialog.unread_count = Math.max(dialog.unread_count - countDiff, 0);
                            if (maxPositiveId != Integer.MIN_VALUE && dialog.unread_count > dialog.top_message - maxPositiveId) {
                                dialog.unread_count = dialog.top_message - maxPositiveId;
                            }
                        }

                        boolean wasUnread;
                        if (wasUnread = dialog.unread_mark) {
                            dialog.unread_mark = false;
                            getMessagesStorage().setDialogUnread(dialog.id, false);
                        }
                        if ((prevCount != 0 || wasUnread) && dialog.unread_count == 0) {
                            if (!isDialogMuted(dialogId)) {
                                unreadUnmutedDialogs--;
                            }
                            for (int b = 0; b < selectedDialogFilter.length; b++) {
                                if (selectedDialogFilter[b] != null && (selectedDialogFilter[b].flags & DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                                    sortDialogs(null);
                                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                                    break;
                                }
                            }
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                    }
                    if (!popup) {
                        getNotificationsController().processReadMessages(null, dialogId, 0, maxPositiveId, false);
                        LongSparseIntArray dialogsToUpdate = new LongSparseIntArray(1);
                        dialogsToUpdate.put(dialogId, 0);
                        getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
                    } else {
                        getNotificationsController().processReadMessages(null, dialogId, 0, maxPositiveId, true);
                        LongSparseIntArray dialogsToUpdate = new LongSparseIntArray(1);
                        dialogsToUpdate.put(dialogId, -1);
                        getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
                    }
                }));

                createReadTask = maxPositiveId != Integer.MAX_VALUE;
            } else {
                if (maxDate == 0) {
                    return;
                }
                createReadTask = true;

                TLRPC.EncryptedChat chat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                getMessagesStorage().processPendingRead(dialogId, maxPositiveId, maxNegativeId, scheduledCount);
                getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    getNotificationsController().processReadMessages(null, dialogId, maxDate, 0, popup);
                    TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
                    if (dialog != null) {
                        int prevCount = dialog.unread_count;
                        if (countDiff == 0 || maxNegativeId <= dialog.top_message) {
                            dialog.unread_count = 0;
                        } else {
                            dialog.unread_count = Math.max(dialog.unread_count - countDiff, 0);
                            if (maxNegativeId != Integer.MAX_VALUE && dialog.unread_count > maxNegativeId - dialog.top_message) {
                                dialog.unread_count = maxNegativeId - dialog.top_message;
                            }
                        }
                        boolean wasUnread;
                        if (wasUnread = dialog.unread_mark) {
                            dialog.unread_mark = false;
                            getMessagesStorage().setDialogUnread(dialog.id, false);
                        }
                        if ((prevCount != 0 || wasUnread) && dialog.unread_count == 0) {
                            if (!isDialogMuted(dialogId)) {
                                unreadUnmutedDialogs--;
                            }
                            for (int b = 0; b < selectedDialogFilter.length; b++) {
                                if (selectedDialogFilter[b] != null && (selectedDialogFilter[b].flags & DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                                    sortDialogs(null);
                                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                                    break;
                                }
                            }
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                    }
                    LongSparseIntArray dialogsToUpdate = new LongSparseIntArray(1);
                    dialogsToUpdate.put(dialogId, 0);
                    getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
                }));

                if (chat != null && chat.ttl > 0) {
                    int serverTime = Math.max(getConnectionsManager().getCurrentTime(), maxDate);
                    getMessagesStorage().createTaskForSecretChat(chat.id, maxDate, serverTime, 0, null);
                }
            }
        }

        if (createReadTask) {
            Utilities.stageQueue.postRunnable(() -> {
                ReadTask currentReadTask;
                if (threadId != 0) {
                    currentReadTask = threadsReadTasksMap.get(dialogId + "_" + threadId);
                } else {
                    currentReadTask = readTasksMap.get(dialogId);
                }
                if (currentReadTask == null) {
                    currentReadTask = new ReadTask();
                    currentReadTask.dialogId = dialogId;
                    currentReadTask.replyId = threadId;
                    currentReadTask.sendRequestTime = SystemClock.elapsedRealtime() + 5000;
                    if (!readNow) {
                        if (threadId != 0) {
                            threadsReadTasksMap.put(dialogId + "_" + threadId, currentReadTask);
                            repliesReadTasks.add(currentReadTask);
                        } else {
                            readTasksMap.put(dialogId, currentReadTask);
                            readTasks.add(currentReadTask);
                        }
                    }
                }
                currentReadTask.maxDate = maxDate;
                currentReadTask.maxId = maxPositiveId;
                if (readNow) {
                    completeReadTask(currentReadTask);
                }
            });
        }
    }

    public int createChat(String title, ArrayList<Long> selectedContacts, String about, int type, boolean forImport, Location location, String locationAddress, BaseFragment fragment) {
        if (type == ChatObject.CHAT_TYPE_CHAT && !forImport) {
            TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
            req.title = title;
            for (int a = 0; a < selectedContacts.size(); a++) {
                TLRPC.User user = getUser(selectedContacts.get(a));
                if (user == null) {
                    continue;
                }
                req.users.add(getInputUser(user));
            }
            return getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        AlertsCreator.processError(currentAccount, error, fragment, req);
                        getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                    });
                    return;
                }
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(updates.users, false);
                    putChats(updates.chats, false);
                    if (updates.chats != null && !updates.chats.isEmpty()) {
                        getNotificationCenter().postNotificationName(NotificationCenter.chatDidCreated, updates.chats.get(0).id);
                    } else {
                        getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } else if (forImport || type == ChatObject.CHAT_TYPE_CHANNEL || type == ChatObject.CHAT_TYPE_MEGAGROUP) {
            TLRPC.TL_channels_createChannel req = new TLRPC.TL_channels_createChannel();
            req.title = title;
            req.about = about != null ? about : "";
            req.for_import = forImport;
            if (forImport || type == ChatObject.CHAT_TYPE_MEGAGROUP) {
                req.megagroup = true;
            } else {
                req.broadcast = true;
            }
            if (location != null) {
                req.geo_point = new TLRPC.TL_inputGeoPoint();
                req.geo_point.lat = location.getLatitude();
                req.geo_point._long = location.getLongitude();
                req.address = locationAddress;
                req.flags |= 4;
            }
            return getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        AlertsCreator.processError(currentAccount, error, fragment, req);
                        getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                    });
                    return;
                }
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(updates.users, false);
                    putChats(updates.chats, false);
                    if (updates.chats != null && !updates.chats.isEmpty()) {
                        getNotificationCenter().postNotificationName(NotificationCenter.chatDidCreated, updates.chats.get(0).id);
                    } else {
                        getNotificationCenter().postNotificationName(NotificationCenter.chatDidFailCreate);
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
        return 0;
    }

    public void convertToMegaGroup(Context context, long chatId, BaseFragment fragment, MessagesStorage.LongCallback convertRunnable) {
        TLRPC.TL_messages_migrateChat req = new TLRPC.TL_messages_migrateChat();
        req.chat_id = chatId;
        AlertDialog progressDialog = context != null ? new AlertDialog(context, 3) : null;
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                if (context != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                }
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        for (int a = 0; a < updates.chats.size(); a++) {
                            TLRPC.Chat chat = updates.chats.get(a);
                            if (ChatObject.isChannel(chat)) {
                                convertRunnable.run(chat.id);
                                break;
                            }
                        }
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        convertRunnable.run(0);
                    }
                    if (context != null) {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            AlertsCreator.processError(currentAccount, error, fragment, req, false);
                        }
                    }
                });
            }
        });
        if (progressDialog != null) {
            progressDialog.setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.show();
            } catch (Exception ignore) {

            }
        }
    }

    public void convertToGigaGroup(final Context context, TLRPC.Chat chat, BaseFragment fragment, MessagesStorage.BooleanCallback convertRunnable) {
        TLRPC.TL_channels_convertToGigagroup req = new TLRPC.TL_channels_convertToGigagroup();
        req.channel = getInputChannel(chat);
        AlertDialog progressDialog = context != null ? new AlertDialog(context, 3) : null;
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                if (context != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                }
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        convertRunnable.run(true);
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    if (convertRunnable != null) {
                        convertRunnable.run(false);
                    }
                    if (context != null) {
                        if (!((Activity) context).isFinishing()) {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            AlertsCreator.processError(currentAccount, error, fragment, req, false);
                        }
                    }
                });
            }
        });
        if (progressDialog != null) {
            progressDialog.setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.showDelayed(400);
            } catch (Exception ignore) {

            }
        }
    }

    public void addUsersToChannel(long chatId, ArrayList<TLRPC.InputUser> users, BaseFragment fragment) {
        if (users == null || users.isEmpty()) {
            return;
        }
        TLRPC.TL_channels_inviteToChannel req = new TLRPC.TL_channels_inviteToChannel();
        req.channel = getInputChannel(chatId);
        req.users = users;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, fragment, req, true));
                return;
            }
            processUpdates((TLRPC.Updates) response, false);
        });
    }

    public void toogleChannelSignatures(long chatId, boolean enabled) {
        TLRPC.TL_channels_toggleSignatures req = new TLRPC.TL_channels_toggleSignatures();
        req.channel = getInputChannel(chatId);
        req.enabled = enabled;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT));
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void toogleChannelInvitesHistory(long chatId, boolean enabled) {
        TLRPC.TL_channels_togglePreHistoryHidden req = new TLRPC.TL_channels_togglePreHistoryHidden();
        req.channel = getInputChannel(chatId);
        req.enabled = enabled;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT));
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void updateChatAbout(long chatId, String about, TLRPC.ChatFull info) {
        TLRPC.TL_messages_editChatAbout req = new TLRPC.TL_messages_editChatAbout();
        req.peer = getInputPeer(-chatId);
        req.about = about;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue && info != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    info.about = about;
                    getMessagesStorage().updateChatInfo(info, false);
                    getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, false);
                });
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void updateChannelUserName(long chatId, String userName) {
        TLRPC.TL_channels_updateUsername req = new TLRPC.TL_channels_updateUsername();
        req.channel = getInputChannel(chatId);
        req.username = userName;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.Chat chat = getChat(chatId);
                    if (userName.length() != 0) {
                        chat.flags |= TLRPC.CHAT_FLAG_IS_PUBLIC;
                    } else {
                        chat.flags &= ~TLRPC.CHAT_FLAG_IS_PUBLIC;
                    }
                    chat.username = userName;
                    ArrayList<TLRPC.Chat> arrayList = new ArrayList<>();
                    arrayList.add(chat);
                    getMessagesStorage().putUsersAndChats(null, arrayList, true, true);
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT);
                });
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void sendBotStart(final TLRPC.User user, String botHash) {
        if (user == null) {
            return;
        }
        TLRPC.TL_messages_startBot req = new TLRPC.TL_messages_startBot();
        req.bot = getInputUser(user);
        req.peer = getInputPeer(user.id);
        req.start_param = botHash;
        req.random_id = Utilities.random.nextLong();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            processUpdates((TLRPC.Updates) response, false);
        });
    }

    public boolean isJoiningChannel(long chatId) {
        return joiningToChannels.contains(chatId);
    }

    public void addUserToChat(long chatId, TLRPC.User user, int forwardCount, String botHash, BaseFragment fragment, Runnable onFinishRunnable) {
        if (user == null) {
            return;
        }

        TLObject request;

        boolean isChannel = ChatObject.isChannel(chatId, currentAccount);
        boolean isMegagroup = isChannel && getChat(chatId).megagroup;
        TLRPC.InputUser inputUser = getInputUser(user);
        if (botHash == null || isChannel && !isMegagroup) {
            if (isChannel) {
                if (inputUser instanceof TLRPC.TL_inputUserSelf) {
                    if (joiningToChannels.contains(chatId)) {
                        return;
                    }
                    TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
                    req.channel = getInputChannel(chatId);
                    request = req;
                    joiningToChannels.add(chatId);
                } else {
                    TLRPC.TL_channels_inviteToChannel req = new TLRPC.TL_channels_inviteToChannel();
                    req.channel = getInputChannel(chatId);
                    req.users.add(inputUser);
                    request = req;
                }
            } else {
                TLRPC.TL_messages_addChatUser req = new TLRPC.TL_messages_addChatUser();
                req.chat_id = chatId;
                req.fwd_limit = forwardCount;
                req.user_id = inputUser;
                request = req;
            }
        } else {
            TLRPC.TL_messages_startBot req = new TLRPC.TL_messages_startBot();
            req.bot = inputUser;
            if (isChannel) {
                req.peer = getInputPeer(-chatId);
            } else {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = chatId;
            }
            req.start_param = botHash;
            req.random_id = Utilities.random.nextLong();
            request = req;
        }

        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                AndroidUtilities.runOnUIThread(() -> joiningToChannels.remove(chatId));
            }
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    AlertsCreator.processError(currentAccount, error, fragment, request, isChannel && !isMegagroup);
                    if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT);
                    }
                });
                return;
            }
            boolean hasJoinMessage = false;
            TLRPC.Updates updates = (TLRPC.Updates) response;
            for (int a = 0; a < updates.updates.size(); a++) {
                TLRPC.Update update = updates.updates.get(a);
                if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                    if (((TLRPC.TL_updateNewChannelMessage) update).message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                        hasJoinMessage = true;
                        break;
                    }
                }
            }
            processUpdates(updates, false);
            if (isChannel) {
                if (!hasJoinMessage && inputUser instanceof TLRPC.TL_inputUserSelf) {
                    generateJoinMessage(chatId, true);
                }
                AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
            }
            if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                getMessagesStorage().updateDialogsWithDeletedMessages(-chatId, chatId, new ArrayList<>(), null, true);
            }
            if (onFinishRunnable != null) {
                AndroidUtilities.runOnUIThread(onFinishRunnable);
            }
        });
    }

    public void deleteParticipantFromChat(long chatId, TLRPC.User user, TLRPC.ChatFull info) {
        deleteParticipantFromChat(chatId, user, null, info, false, false);
    }

    public void deleteParticipantFromChat(long chatId, TLRPC.User user, TLRPC.Chat chat, TLRPC.ChatFull info, boolean forceDelete, boolean revoke) {
        if (user == null && chat == null) {
            return;
        }
        TLRPC.InputPeer inputPeer;
        if (user != null) {
            inputPeer = getInputPeer(user);
        } else {
            inputPeer = getInputPeer(chat);
        }
        TLObject request;
        TLRPC.Chat ownerChat = getChat(chatId);
        boolean isChannel = ChatObject.isChannel(ownerChat);
        if (isChannel) {
            if (UserObject.isUserSelf(user)) {
                if (ownerChat.creator && forceDelete) {
                    TLRPC.TL_channels_deleteChannel req = new TLRPC.TL_channels_deleteChannel();
                    req.channel = getInputChannel(ownerChat);
                    request = req;
                } else {
                    TLRPC.TL_channels_leaveChannel req = new TLRPC.TL_channels_leaveChannel();
                    req.channel = getInputChannel(ownerChat);
                    request = req;
                }
            } else {
                TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
                req.channel = getInputChannel(ownerChat);
                req.participant = inputPeer;
                req.banned_rights = new TLRPC.TL_chatBannedRights();
                req.banned_rights.view_messages = true;
                req.banned_rights.send_media = true;
                req.banned_rights.send_messages = true;
                req.banned_rights.send_stickers = true;
                req.banned_rights.send_gifs = true;
                req.banned_rights.send_games = true;
                req.banned_rights.send_inline = true;
                req.banned_rights.embed_links = true;
                req.banned_rights.pin_messages = true;
                req.banned_rights.send_polls = true;
                req.banned_rights.invite_users = true;
                req.banned_rights.change_info = true;
                request = req;
            }
        } else {
            if (forceDelete) {
                TLRPC.TL_messages_deleteChat req = new TLRPC.TL_messages_deleteChat();
                req.chat_id = chatId;
                getConnectionsManager().sendRequest(req, (response, error) -> {

                });
                return;
            }
            TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
            req.chat_id = chatId;
            req.user_id = getInputUser(user);
            req.revoke_history = true;
            request = req;
        }
        if (UserObject.isUserSelf(user)) {
            deleteDialog(-chatId, 0, revoke);
        }
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error != null) {
                return;
            }
            TLRPC.Updates updates = (TLRPC.Updates) response;
            processUpdates(updates, false);
            if (isChannel && !UserObject.isUserSelf(user)) {
                AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void changeChatTitle(long chatId, String title) {
        TLObject request;
        if (ChatObject.isChannel(chatId, currentAccount)) {
            TLRPC.TL_channels_editTitle req = new TLRPC.TL_channels_editTitle();
            req.channel = getInputChannel(chatId);
            req.title = title;
            request = req;
        } else {
            TLRPC.TL_messages_editChatTitle req = new TLRPC.TL_messages_editChatTitle();
            req.chat_id = chatId;
            req.title = title;
            request = req;
        }
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error != null) {
                return;
            }
            processUpdates((TLRPC.Updates) response, false);
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void changeChatAvatar(long chatId, TLRPC.TL_inputChatPhoto oldPhoto, TLRPC.InputFile inputPhoto, TLRPC.InputFile inputVideo, double videoStartTimestamp, String videoPath, TLRPC.FileLocation smallSize, TLRPC.FileLocation bigSize, Runnable callback) {
        TLObject request;
        TLRPC.InputChatPhoto inputChatPhoto;
        if (oldPhoto != null) {
            inputChatPhoto = oldPhoto;
        } else if (inputPhoto != null || inputVideo != null) {
            TLRPC.TL_inputChatUploadedPhoto uploadedPhoto = new TLRPC.TL_inputChatUploadedPhoto();
            if (inputPhoto != null) {
                uploadedPhoto.file = inputPhoto;
                uploadedPhoto.flags |= 1;
            }
            if (inputVideo != null) {
                uploadedPhoto.video = inputVideo;
                uploadedPhoto.flags |= 2;
                uploadedPhoto.video_start_ts = videoStartTimestamp;
                uploadedPhoto.flags |= 4;
            }
            inputChatPhoto = uploadedPhoto;
        } else {
            inputChatPhoto = new TLRPC.TL_inputChatPhotoEmpty();
        }
        if (ChatObject.isChannel(chatId, currentAccount)) {
            TLRPC.TL_channels_editPhoto req = new TLRPC.TL_channels_editPhoto();
            req.channel = getInputChannel(chatId);
            req.photo = inputChatPhoto;
            request = req;
        } else {
            TLRPC.TL_messages_editChatPhoto req = new TLRPC.TL_messages_editChatPhoto();
            req.chat_id = chatId;
            req.photo = inputChatPhoto;
            request = req;
        }
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error != null) {
                return;
            }
            TLRPC.Updates updates = (TLRPC.Updates) response;
            if (oldPhoto == null) {
                TLRPC.Photo photo = null;
                for (int a = 0, N = updates.updates.size(); a < N; a++) {
                    TLRPC.Update update = updates.updates.get(a);
                    if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                        TLRPC.Message message = ((TLRPC.TL_updateNewChannelMessage) update).message;
                        if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto && message.action.photo instanceof TLRPC.TL_photo) {
                            photo = message.action.photo;
                            break;
                        }
                    } else if (update instanceof TLRPC.TL_updateNewMessage) {
                        TLRPC.Message message = ((TLRPC.TL_updateNewMessage) update).message;
                        if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto && message.action.photo instanceof TLRPC.TL_photo) {
                            photo = message.action.photo;
                            break;
                        }
                    }
                }
                if (photo != null) {
                    TLRPC.PhotoSize small = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 150);
                    TLRPC.VideoSize videoSize = photo.video_sizes.isEmpty() ? null : photo.video_sizes.get(0);
                    if (small != null && smallSize != null) {
                        File destFile = FileLoader.getPathToAttach(small, true);
                        File src = FileLoader.getPathToAttach(smallSize, true);
                        src.renameTo(destFile);
                        String oldKey = smallSize.volume_id + "_" + smallSize.local_id + "@50_50";
                        String newKey = small.location.volume_id + "_" + small.location.local_id + "@50_50";
                        ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForPhoto(small, photo), true);
                    }
                    TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800);
                    if (big != null && bigSize != null) {
                        File destFile = FileLoader.getPathToAttach(big, true);
                        File src = FileLoader.getPathToAttach(bigSize, true);
                        src.renameTo(destFile);
                    }
                    if (videoSize != null && videoPath != null) {
                        File destFile = FileLoader.getPathToAttach(videoSize, "mp4", true);
                        File src = new File(videoPath);
                        src.renameTo(destFile);
                    }
                }
            }
            processUpdates(updates, false);
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.run();
                }
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_AVATAR);
            });
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void unregistedPush() {
        if (getUserConfig().registeredForPush && SharedConfig.pushString.length() == 0) {
            TLRPC.TL_account_unregisterDevice req = new TLRPC.TL_account_unregisterDevice();
            req.token = SharedConfig.pushString;
            req.token_type = 2;
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig userConfig = UserConfig.getInstance(a);
                if (a != currentAccount && userConfig.isClientActivated()) {
                    req.other_uids.add(userConfig.getClientUserId());
                }
            }
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
    }

    public void performLogout(int type) {
        if (type == 1) {
            unregistedPush();
            TLRPC.TL_auth_logOut req = new TLRPC.TL_auth_logOut();
            getConnectionsManager().sendRequest(req, (response, error) -> getConnectionsManager().cleanup(false));
        } else {
            getConnectionsManager().cleanup(type == 2);
        }
        getUserConfig().clearConfig();

        boolean shouldHandle = true;
        ArrayList<NotificationCenter.NotificationCenterDelegate> observers = getNotificationCenter().getObservers(NotificationCenter.appDidLogout);
        if (observers != null) {
            for (int a = 0, N = observers.size(); a < N; a++) {
                if (observers.get(a) instanceof LaunchActivity) {
                    shouldHandle = false;
                    break;
                }
            }
        }
        if (shouldHandle) {
            if (UserConfig.selectedAccount == currentAccount) {
                int account = -1;
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        account = a;
                        break;
                    }
                }
                if (account != -1) {
                    UserConfig.selectedAccount = account;
                    UserConfig.getInstance(0).saveConfig(false);
                    LaunchActivity.clearFragments();
                }
            }
        }
        getNotificationCenter().postNotificationName(NotificationCenter.appDidLogout);
        getMessagesStorage().cleanup(false);
        cleanup();
        getContactsController().deleteUnknownAppAccounts();
    }

    private boolean gettingAppChangelog;
    public void generateUpdateMessage() {
        if (gettingAppChangelog || BuildVars.DEBUG_VERSION || SharedConfig.lastUpdateVersion == null || SharedConfig.lastUpdateVersion.equals(BuildVars.BUILD_VERSION_STRING)) {
            return;
        }
        gettingAppChangelog = true;
        TLRPC.TL_help_getAppChangelog req = new TLRPC.TL_help_getAppChangelog();
        req.prev_app_version = SharedConfig.lastUpdateVersion;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                SharedConfig.lastUpdateVersion = BuildVars.BUILD_VERSION_STRING;
                SharedConfig.saveConfig();
            }
            if (response instanceof TLRPC.Updates) {
                processUpdates((TLRPC.Updates) response, false);
            }
        });
    }

    public void registerForPush(final String regid) {
        if (TextUtils.isEmpty(regid) || registeringForPush || getUserConfig().getClientUserId() == 0) {
            return;
        }
        if (getUserConfig().registeredForPush && regid.equals(SharedConfig.pushString)) {
            return;
        }
        registeringForPush = true;
        lastPushRegisterSendTime = SystemClock.elapsedRealtime();
        if (SharedConfig.pushAuthKey == null) {
            SharedConfig.pushAuthKey = new byte[256];
            Utilities.random.nextBytes(SharedConfig.pushAuthKey);
            SharedConfig.saveConfig();
        }
        TLRPC.TL_account_registerDevice req = new TLRPC.TL_account_registerDevice();
        req.token_type = 2;
        req.token = regid;
        req.no_muted = false;
        req.secret = SharedConfig.pushAuthKey;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            UserConfig userConfig = UserConfig.getInstance(a);
            if (a != currentAccount && userConfig.isClientActivated()) {
                long uid = userConfig.getClientUserId();
                req.other_uids.add(uid);
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("add other uid = " + uid + " for account " + currentAccount);
                }
            }
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("account " + currentAccount + " registered for push");
                }
                getUserConfig().registeredForPush = true;
                SharedConfig.pushString = regid;
                getUserConfig().saveConfig(false);
            }
            AndroidUtilities.runOnUIThread(() -> registeringForPush = false);
        });
    }

    public void loadCurrentState() {
        if (updatingState) {
            return;
        }
        updatingState = true;
        TLRPC.TL_updates_getState req = new TLRPC.TL_updates_getState();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            updatingState = false;
            if (error == null) {
                TLRPC.TL_updates_state res = (TLRPC.TL_updates_state) response;
                getMessagesStorage().setLastDateValue(res.date);
                getMessagesStorage().setLastPtsValue(res.pts);
                getMessagesStorage().setLastSeqValue(res.seq);
                getMessagesStorage().setLastQtsValue(res.qts);
                for (int a = 0; a < 3; a++) {
                    processUpdatesQueue(a, 2);
                }
                getMessagesStorage().saveDiffParams(getMessagesStorage().getLastSeqValue(), getMessagesStorage().getLastPtsValue(), getMessagesStorage().getLastDateValue(), getMessagesStorage().getLastQtsValue());
            } else {
                if (error.code != 401) {
                    loadCurrentState();
                }
            }
        });
    }

    private int getUpdateSeq(TLRPC.Updates updates) {
        if (updates instanceof TLRPC.TL_updatesCombined) {
            return updates.seq_start;
        } else {
            return updates.seq;
        }
    }

    private void setUpdatesStartTime(int type, long time) {
        if (type == 0) {
            updatesStartWaitTimeSeq = time;
        } else if (type == 1) {
            updatesStartWaitTimePts = time;
        } else if (type == 2) {
            updatesStartWaitTimeQts = time;
        }
    }

    public long getUpdatesStartTime(int type) {
        if (type == 0) {
            return updatesStartWaitTimeSeq;
        } else if (type == 1) {
            return updatesStartWaitTimePts;
        } else if (type == 2) {
            return updatesStartWaitTimeQts;
        }
        return 0;
    }

    private int isValidUpdate(TLRPC.Updates updates, int type) {
        if (type == 0) {
            int seq = getUpdateSeq(updates);
            if (getMessagesStorage().getLastSeqValue() + 1 == seq || getMessagesStorage().getLastSeqValue() == seq) {
                return 0;
            } else if (getMessagesStorage().getLastSeqValue() < seq) {
                return 1;
            } else {
                return 2;
            }
        } else if (type == 1) {
            if (updates.pts <= getMessagesStorage().getLastPtsValue()) {
                return 2;
            } else if (getMessagesStorage().getLastPtsValue() + updates.pts_count == updates.pts) {
                return 0;
            } else {
                return 1;
            }
        } else if (type == 2) {
            if (updates.pts <= getMessagesStorage().getLastQtsValue()) {
                return 2;
            } else if (getMessagesStorage().getLastQtsValue() + updates.updates.size() == updates.pts) {
                return 0;
            } else {
                return 1;
            }
        }
        return 0;
    }

    private void processChannelsUpdatesQueue(long channelId, int state) {
        ArrayList<TLRPC.Updates> updatesQueue = updatesQueueChannels.get(channelId);
        if (updatesQueue == null) {
            return;
        }
        int channelPts = channelsPts.get(channelId);
        if (updatesQueue.isEmpty() || channelPts == 0) {
            updatesQueueChannels.remove(channelId);
            return;
        }
        Collections.sort(updatesQueue, (updates, updates2) -> AndroidUtilities.compare(updates.pts, updates2.pts));
        boolean anyProceed = false;
        if (state == 2) {
            channelsPts.put(channelId, updatesQueue.get(0).pts);
        }
        for (int a = 0; a < updatesQueue.size(); a++) {
            TLRPC.Updates updates = updatesQueue.get(a);
            int updateState;
            if (updates.pts <= channelPts) {
                updateState = 2;
            } else if (channelPts + updates.pts_count == updates.pts) {
                updateState = 0;
            } else {
                updateState = 1;
            }
            if (updateState == 0) {
                processUpdates(updates, true);
                anyProceed = true;
                updatesQueue.remove(a);
                a--;
            } else if (updateState == 1) {
                long updatesStartWaitTime = updatesStartWaitTimeChannels.get(channelId);
                if (updatesStartWaitTime != 0 && (anyProceed || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500)) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("HOLE IN CHANNEL " + channelId + " UPDATES QUEUE - will wait more time");
                    }
                    if (anyProceed) {
                        updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis());
                    }
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("HOLE IN CHANNEL " + channelId + " UPDATES QUEUE - getChannelDifference ");
                    }
                    updatesStartWaitTimeChannels.delete(channelId);
                    updatesQueueChannels.remove(channelId);
                    getChannelDifference(channelId);
                }
                return;
            } else {
                updatesQueue.remove(a);
                a--;
            }
        }
        updatesQueueChannels.remove(channelId);
        updatesStartWaitTimeChannels.delete(channelId);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("UPDATES CHANNEL " + channelId + " QUEUE PROCEED - OK");
        }
    }

    private void processUpdatesQueue(int type, int state) {
        ArrayList<TLRPC.Updates> updatesQueue = null;
        if (type == 0) {
            updatesQueue = updatesQueueSeq;
            Collections.sort(updatesQueue, (updates, updates2) -> AndroidUtilities.compare(getUpdateSeq(updates), getUpdateSeq(updates2)));
        } else if (type == 1) {
            updatesQueue = updatesQueuePts;
            Collections.sort(updatesQueue, (updates, updates2) -> AndroidUtilities.compare(updates.pts, updates2.pts));
        } else if (type == 2) {
            updatesQueue = updatesQueueQts;
            Collections.sort(updatesQueue, (updates, updates2) -> AndroidUtilities.compare(updates.pts, updates2.pts));
        }
        if (updatesQueue != null && !updatesQueue.isEmpty()) {
            boolean anyProceed = false;
            if (state == 2) {
                TLRPC.Updates updates = updatesQueue.get(0);
                if (type == 0) {
                    getMessagesStorage().setLastSeqValue(getUpdateSeq(updates));
                } else if (type == 1) {
                    getMessagesStorage().setLastPtsValue(updates.pts);
                } else {
                    getMessagesStorage().setLastQtsValue(updates.pts);
                }
            }
            for (int a = 0; a < updatesQueue.size(); a++) {
                TLRPC.Updates updates = updatesQueue.get(a);
                int updateState = isValidUpdate(updates, type);
                if (updateState == 0) {
                    processUpdates(updates, true);
                    anyProceed = true;
                    updatesQueue.remove(a);
                    a--;
                } else if (updateState == 1) {
                    if (getUpdatesStartTime(type) != 0 && (anyProceed || Math.abs(System.currentTimeMillis() - getUpdatesStartTime(type)) <= 1500)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("HOLE IN UPDATES QUEUE - will wait more time");
                        }
                        if (anyProceed) {
                            setUpdatesStartTime(type, System.currentTimeMillis());
                        }
                    } else {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("HOLE IN UPDATES QUEUE - getDifference");
                        }
                        setUpdatesStartTime(type, 0);
                        updatesQueue.clear();
                        getDifference();
                    }
                    return;
                } else {
                    updatesQueue.remove(a);
                    a--;
                }
            }
            updatesQueue.clear();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("UPDATES QUEUE PROCEED - OK");
            }
        }
        setUpdatesStartTime(type, 0);
    }

    protected void loadUnknownChannel(final TLRPC.Chat channel, long taskId) {
        if (!(channel instanceof TLRPC.TL_channel) || gettingUnknownChannels.indexOfKey(channel.id) >= 0) {
            return;
        }
        if (channel.access_hash == 0) {
            if (taskId != 0) {
                getMessagesStorage().removePendingTask(taskId);
            }
            return;
        }
        TLRPC.TL_inputPeerChannel inputPeer = new TLRPC.TL_inputPeerChannel();
        inputPeer.channel_id = channel.id;
        inputPeer.access_hash = channel.access_hash;

        gettingUnknownChannels.put(channel.id, true);

        TLRPC.TL_messages_getPeerDialogs req = new TLRPC.TL_messages_getPeerDialogs();
        TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
        inputDialogPeer.peer = inputPeer;
        req.peers.add(inputDialogPeer);
        long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(4 + channel.getObjectSize());
                data.writeInt32(0);
                channel.serializeToStream(data);
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                if (!res.dialogs.isEmpty() && !res.chats.isEmpty()) {
                    TLRPC.TL_dialog dialog = (TLRPC.TL_dialog) res.dialogs.get(0);
                    TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                    dialogs.dialogs.addAll(res.dialogs);
                    dialogs.messages.addAll(res.messages);
                    dialogs.users.addAll(res.users);
                    dialogs.chats.addAll(res.chats);
                    processLoadedDialogs(dialogs, null, dialog.folder_id, 0, 1, DIALOGS_LOAD_TYPE_CHANNEL, false, false, false);
                }
            }
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
            gettingUnknownChannels.delete(channel.id);
        });
    }

    public void startShortPoll(TLRPC.Chat chat, int guid, boolean stop) {
        if (chat == null) {
            return;
        }
        Utilities.stageQueue.postRunnable(() -> {
            ArrayList<Integer> guids = needShortPollChannels.get(chat.id);
            ArrayList<Integer> onlineGuids = needShortPollOnlines.get(chat.id);
            if (stop) {
                if (guids != null) {
                    guids.remove((Integer) guid);
                }
                if (guids == null || guids.isEmpty()) {
                    needShortPollChannels.delete(chat.id);
                }
                if (chat.megagroup) {
                    if (onlineGuids != null) {
                        onlineGuids.remove((Integer) guid);
                    }
                    if (onlineGuids == null || onlineGuids.isEmpty()) {
                        needShortPollOnlines.delete(chat.id);
                    }
                }
            } else {
                if (guids == null) {
                    guids = new ArrayList<>();
                    needShortPollChannels.put(chat.id, guids);
                }
                if (!guids.contains(guid)) {
                    guids.add(guid);
                }
                if (shortPollChannels.indexOfKey(chat.id) < 0) {
                    getChannelDifference(chat.id, 3, 0, null);
                }
                if (chat.megagroup) {
                    if (onlineGuids == null) {
                        onlineGuids = new ArrayList<>();
                        needShortPollOnlines.put(chat.id, onlineGuids);
                    }
                    if (!onlineGuids.contains(guid)) {
                        onlineGuids.add(guid);
                    }
                    if (shortPollOnlines.indexOfKey(chat.id) < 0) {
                        shortPollOnlines.put(chat.id, 0);
                    }
                }
            }
        });
    }

    private void getChannelDifference(long channelId) {
        getChannelDifference(channelId, 0, 0, null);
    }

    public static boolean isSupportUser(TLRPC.User user) {
        return user != null && (user.support || user.id == 777000 ||
                user.id == 333000 || user.id == 4240000 || user.id == 4244000 ||
                user.id == 4245000 || user.id == 4246000 || user.id == 410000 ||
                user.id == 420000 || user.id == 431000 || user.id == 431415000 ||
                user.id == 434000 || user.id == 4243000 || user.id == 439000 ||
                user.id == 449000 || user.id == 450000 || user.id == 452000 ||
                user.id == 454000 || user.id == 4254000 || user.id == 455000 ||
                user.id == 460000 || user.id == 470000 || user.id == 479000 ||
                user.id == 796000 || user.id == 482000 || user.id == 490000 ||
                user.id == 496000 || user.id == 497000 || user.id == 498000 ||
                user.id == 4298000);
    }

    protected void getChannelDifference(long channelId, int newDialogType, long taskId, TLRPC.InputChannel inputChannel) {
        boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId, false);
        if (gettingDifferenceChannel) {
            return;
        }
        int limit = 100;
        int channelPts;
        if (newDialogType == 1) {
            channelPts = channelsPts.get(channelId);
            if (channelPts != 0) {
                return;
            }
            channelPts = 1;
            limit = 1;
        } else {
            channelPts = channelsPts.get(channelId);
            if (channelPts == 0) {
                channelPts = getMessagesStorage().getChannelPtsSync(channelId);
                if (channelPts != 0) {
                    channelsPts.put(channelId, channelPts);
                }
                if (channelPts == 0 && (newDialogType == 2 || newDialogType == 3)) {
                    return;
                }
            }
            if (channelPts == 0) {
                return;
            }
        }

        if (inputChannel == null) {
            TLRPC.Chat chat = getChat(channelId);
            if (chat == null) {
                chat = getMessagesStorage().getChatSync(channelId);
                if (chat != null) {
                    putChat(chat, true);
                }
            }
            inputChannel = getInputChannel(chat);
        }
        if (inputChannel.access_hash == 0) {
            if (taskId != 0) {
                getMessagesStorage().removePendingTask(taskId);
            }
            return;
        }
        long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(16 + inputChannel.getObjectSize());
                data.writeInt32(25);
                data.writeInt64(channelId);
                data.writeInt32(newDialogType);
                inputChannel.serializeToStream(data);
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            newTaskId = taskId;
        }

        gettingDifferenceChannels.put(channelId, true);
        TLRPC.TL_updates_getChannelDifference req = new TLRPC.TL_updates_getChannelDifference();
        req.channel = inputChannel;
        req.filter = new TLRPC.TL_channelMessagesFilterEmpty();
        req.pts = channelPts;
        req.limit = limit;
        req.force = newDialogType != 3;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start getChannelDifference with pts = " + channelPts + " channelId = " + channelId);
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.updates_ChannelDifference res = (TLRPC.updates_ChannelDifference) response;

                LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
                for (int a = 0; a < res.users.size(); a++) {
                    TLRPC.User user = res.users.get(a);
                    usersDict.put(user.id, user);
                }
                TLRPC.Chat channel = null;
                for (int a = 0; a < res.chats.size(); a++) {
                    TLRPC.Chat chat = res.chats.get(a);
                    if (chat.id == channelId) {
                        channel = chat;
                        break;
                    }
                }
                TLRPC.Chat channelFinal = channel;

                ArrayList<TLRPC.TL_updateMessageID> msgUpdates = new ArrayList<>();
                if (!res.other_updates.isEmpty()) {
                    for (int a = 0; a < res.other_updates.size(); a++) {
                        TLRPC.Update upd = res.other_updates.get(a);
                        if (upd instanceof TLRPC.TL_updateMessageID) {
                            msgUpdates.add((TLRPC.TL_updateMessageID) upd);
                            res.other_updates.remove(a);
                            a--;
                        }
                    }
                }

                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(res.users, false);
                    putChats(res.chats, false);
                });

                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    if (!msgUpdates.isEmpty()) {
                        SparseArray<long[]> corrected = new SparseArray<>();
                        for (TLRPC.TL_updateMessageID update : msgUpdates) {
                            long[] ids = getMessagesStorage().updateMessageStateAndId(update.random_id, -channelId, null, update.id, 0, false, -1);
                            if (ids != null) {
                                corrected.put(update.id, ids);
                            }
                        }

                        if (corrected.size() != 0) {
                            AndroidUtilities.runOnUIThread(() -> {
                                for (int a = 0; a < corrected.size(); a++) {
                                    int newId = corrected.keyAt(a);
                                    long[] ids = corrected.valueAt(a);
                                    getSendMessagesHelper().processSentMessage((int) ids[1]);
                                    getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, (int) ids[1], newId, null, ids[0], 0L, -1, false);
                                }
                            });
                        }
                    }

                    Utilities.stageQueue.postRunnable(() -> {
                        if (res instanceof TLRPC.TL_updates_channelDifference || res instanceof TLRPC.TL_updates_channelDifferenceEmpty) {
                            if (!res.new_messages.isEmpty()) {
                                LongSparseArray<ArrayList<MessageObject>> messages = new LongSparseArray<>();
                                ImageLoader.saveMessagesThumbs(res.new_messages);

                                ArrayList<MessageObject> pushMessages = new ArrayList<>();
                                long dialogId = -channelId;
                                Integer inboxValue = dialogs_read_inbox_max.get(dialogId);
                                if (inboxValue == null) {
                                    inboxValue = getMessagesStorage().getDialogReadMax(false, dialogId);
                                    dialogs_read_inbox_max.put(dialogId, inboxValue);
                                }

                                Integer outboxValue = dialogs_read_outbox_max.get(dialogId);
                                if (outboxValue == null) {
                                    outboxValue = getMessagesStorage().getDialogReadMax(true, dialogId);
                                    dialogs_read_outbox_max.put(dialogId, outboxValue);
                                }

                                for (int a = 0; a < res.new_messages.size(); a++) {
                                    TLRPC.Message message = res.new_messages.get(a);
                                    if (message instanceof TLRPC.TL_messageEmpty) {
                                        continue;
                                    }
                                    message.unread = !(channelFinal != null && channelFinal.left || (message.out ? outboxValue : inboxValue) >= message.id || message.action instanceof TLRPC.TL_messageActionChannelCreate);

                                    boolean isDialogCreated = createdDialogIds.contains(dialogId);
                                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, isDialogCreated, isDialogCreated);
                                    if ((!obj.isOut() || obj.messageOwner.from_scheduled) && obj.isUnread()) {
                                        pushMessages.add(obj);
                                    }

                                    long uid = -channelId;
                                    ArrayList<MessageObject> arr = messages.get(uid);
                                    if (arr == null) {
                                        arr = new ArrayList<>();
                                        messages.put(uid, arr);
                                    }
                                    arr.add(obj);
                                }

                                AndroidUtilities.runOnUIThread(() -> {
                                    for (int a = 0; a < messages.size(); a++) {
                                        long key = messages.keyAt(a);
                                        ArrayList<MessageObject> value = messages.valueAt(a);
                                        updateInterfaceWithMessages(key, value, false);
                                    }
                                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                                });
                                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                    if (!pushMessages.isEmpty()) {
                                        AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(pushMessages, true, false, null));
                                    }
                                    getMessagesStorage().putMessages(res.new_messages, true, false, false, getDownloadController().getAutodownloadMask(), false);
                                });
                            }

                            if (!res.other_updates.isEmpty()) {
                                processUpdateArray(res.other_updates, res.users, res.chats, true, 0);
                            }
                            processChannelsUpdatesQueue(channelId, 1);
                            getMessagesStorage().saveChannelPts(channelId, res.pts);
                        } else if (res instanceof TLRPC.TL_updates_channelDifferenceTooLong) {
                            long dialogId = -channelId;

                            Integer inboxValue = dialogs_read_inbox_max.get(dialogId);
                            if (inboxValue == null) {
                                inboxValue = getMessagesStorage().getDialogReadMax(false, dialogId);
                                dialogs_read_inbox_max.put(dialogId, inboxValue);
                            }

                            Integer outboxValue = dialogs_read_outbox_max.get(dialogId);
                            if (outboxValue == null) {
                                outboxValue = getMessagesStorage().getDialogReadMax(true, dialogId);
                                dialogs_read_outbox_max.put(dialogId, outboxValue);
                            }

                            for (int a = 0; a < res.messages.size(); a++) {
                                TLRPC.Message message = res.messages.get(a);
                                message.dialog_id = -channelId;
                                message.unread = !(message.action instanceof TLRPC.TL_messageActionChannelCreate || channelFinal != null && channelFinal.left || (message.out ? outboxValue : inboxValue) >= message.id);
                            }
                            getMessagesStorage().overwriteChannel(channelId, (TLRPC.TL_updates_channelDifferenceTooLong) res, newDialogType);
                        }
                        gettingDifferenceChannels.delete(channelId);
                        channelsPts.put(channelId, res.pts);

                        if ((res.flags & 2) != 0) {
                            shortPollChannels.put(channelId, (int) (System.currentTimeMillis() / 1000) + res.timeout);
                        }
                        if (!res.isFinal) {
                            getChannelDifference(channelId);
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("received channel difference with pts = " + res.pts + " channelId = " + channelId);
                            FileLog.d("new_messages = " + res.new_messages.size() + " messages = " + res.messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                        }

                        if (newTaskId != 0) {
                            getMessagesStorage().removePendingTask(newTaskId);
                        }
                    });
                });
            } else if (error != null) {
                AndroidUtilities.runOnUIThread(() -> checkChannelError(error.text, channelId));
                gettingDifferenceChannels.delete(channelId);
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            }
        });
    }

    private void checkChannelError(String text, long channelId) {
        switch (text) {
            case "CHANNEL_PRIVATE":
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 0);
                break;
            case "CHANNEL_PUBLIC_GROUP_NA":
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 1);
                break;
            case "USER_BANNED_IN_CHANNEL":
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 2);
                break;
        }
    }

    public void getDifference() {
        getDifference(getMessagesStorage().getLastPtsValue(), getMessagesStorage().getLastDateValue(), getMessagesStorage().getLastQtsValue(), false);
    }

    public void getDifference(int pts, int date, int qts, boolean slice) {
        registerForPush(SharedConfig.pushString);
        if (getMessagesStorage().getLastPtsValue() == 0) {
            loadCurrentState();
            return;
        }
        if (!slice && gettingDifference) {
            return;
        }
        gettingDifference = true;
        TLRPC.TL_updates_getDifference req = new TLRPC.TL_updates_getDifference();
        req.pts = pts;
        req.date = date;
        req.qts = qts;
        if (getDifferenceFirstSync) {
            req.flags |= 1;
            if (ApplicationLoader.isConnectedOrConnectingToWiFi()) {
                req.pts_total_limit = 5000;
            } else {
                req.pts_total_limit = 1000;
            }
            getDifferenceFirstSync = false;
        }
        if (req.date == 0) {
            req.date = getConnectionsManager().getCurrentTime();
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start getDifference with date = " + date + " pts = " + pts + " qts = " + qts);
        }
        getConnectionsManager().setIsUpdating(true);
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.updates_Difference res = (TLRPC.updates_Difference) response;
                if (res instanceof TLRPC.TL_updates_differenceTooLong) {
                    AndroidUtilities.runOnUIThread(() -> {
                        loadedFullUsers.clear();
                        loadedFullChats.clear();
                        resetDialogs(true, getMessagesStorage().getLastSeqValue(), res.pts, date, qts);
                    });
                } else {
                    if (res instanceof TLRPC.TL_updates_differenceSlice) {
                        getDifference(res.intermediate_state.pts, res.intermediate_state.date, res.intermediate_state.qts, true);
                    }

                    LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
                    LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
                    for (int a = 0; a < res.users.size(); a++) {
                        TLRPC.User user = res.users.get(a);
                        usersDict.put(user.id, user);
                    }
                    for (int a = 0; a < res.chats.size(); a++) {
                        TLRPC.Chat chat = res.chats.get(a);
                        chatsDict.put(chat.id, chat);
                    }

                    ArrayList<TLRPC.TL_updateMessageID> msgUpdates = new ArrayList<>();
                    if (!res.other_updates.isEmpty()) {
                        for (int a = 0; a < res.other_updates.size(); a++) {
                            TLRPC.Update upd = res.other_updates.get(a);
                            if (upd instanceof TLRPC.TL_updateMessageID) {
                                msgUpdates.add((TLRPC.TL_updateMessageID) upd);
                                res.other_updates.remove(a);
                                a--;
                            } else if (getUpdateType(upd) == 2) {
                                long channelId = getUpdateChannelId(upd);
                                int channelPts = channelsPts.get(channelId);
                                if (channelPts == 0) {
                                    channelPts = getMessagesStorage().getChannelPtsSync(channelId);
                                    if (channelPts != 0) {
                                        channelsPts.put(channelId, channelPts);
                                    }
                                }
                                if (channelPts != 0 && getUpdatePts(upd) <= channelPts) {
                                    res.other_updates.remove(a);
                                    a--;
                                }
                            }
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        loadedFullUsers.clear();
                        loadedFullChats.clear();
                        putUsers(res.users, false);
                        putChats(res.chats, false);
                    });

                    getMessagesStorage().getStorageQueue().postRunnable(() -> {
                        getMessagesStorage().putUsersAndChats(res.users, res.chats, true, false);
                        if (!msgUpdates.isEmpty()) {
                            SparseArray<long[]> corrected = new SparseArray<>();
                            for (int a = 0; a < msgUpdates.size(); a++) {
                                TLRPC.TL_updateMessageID update = msgUpdates.get(a);
                                long[] ids = getMessagesStorage().updateMessageStateAndId(update.random_id, 0, null, update.id, 0, false, -1);
                                if (ids != null) {
                                    corrected.put(update.id, ids);
                                }
                            }

                            if (corrected.size() != 0) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    for (int a = 0; a < corrected.size(); a++) {
                                        int newId = corrected.keyAt(a);
                                        long[] ids = corrected.valueAt(a);
                                        getSendMessagesHelper().processSentMessage((int) ids[1]);
                                        getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, (int) ids[1], newId, null, ids[0], 0L, -1, false);
                                    }
                                });
                            }
                        }

                        Utilities.stageQueue.postRunnable(() -> {
                            if (!res.new_messages.isEmpty() || !res.new_encrypted_messages.isEmpty()) {
                                LongSparseArray<ArrayList<MessageObject>> messages = new LongSparseArray<>();
                                for (int b = 0; b < res.new_encrypted_messages.size(); b++) {
                                    TLRPC.EncryptedMessage encryptedMessage = res.new_encrypted_messages.get(b);
                                    ArrayList<TLRPC.Message> decryptedMessages = getSecretChatHelper().decryptMessage(encryptedMessage);
                                    if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                                        res.new_messages.addAll(decryptedMessages);
                                    }
                                }

                                ImageLoader.saveMessagesThumbs(res.new_messages);

                                ArrayList<MessageObject> pushMessages = new ArrayList<>();
                                long clientUserId = getUserConfig().getClientUserId();
                                for (int a = 0; a < res.new_messages.size(); a++) {
                                    TLRPC.Message message = res.new_messages.get(a);
                                    if (message instanceof TLRPC.TL_messageEmpty) {
                                        continue;
                                    }
                                    MessageObject.getDialogId(message);

                                    if (!DialogObject.isEncryptedDialog(message.dialog_id)) {
                                        if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                                            TLRPC.User user = usersDict.get(message.action.user_id);
                                            if (user != null && user.bot) {
                                                message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                                                message.flags |= 64;
                                            }
                                        }
                                        if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                                            message.unread = false;
                                            message.media_unread = false;
                                        } else {
                                            ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                                            Integer value = read_max.get(message.dialog_id);
                                            if (value == null) {
                                                value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                                                read_max.put(message.dialog_id, value);
                                            }
                                            message.unread = value < message.id;
                                        }
                                    }
                                    if (message.dialog_id == clientUserId) {
                                        message.unread = false;
                                        message.media_unread = false;
                                        message.out = true;
                                    }

                                    boolean isDialogCreated = createdDialogIds.contains(message.dialog_id);
                                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated);

                                    if ((!obj.isOut() || obj.messageOwner.from_scheduled) && obj.isUnread()) {
                                        pushMessages.add(obj);
                                    }

                                    ArrayList<MessageObject> arr = messages.get(message.dialog_id);
                                    if (arr == null) {
                                        arr = new ArrayList<>();
                                        messages.put(message.dialog_id, arr);
                                    }
                                    arr.add(obj);
                                }

                                AndroidUtilities.runOnUIThread(() -> {
                                    for (int a = 0; a < messages.size(); a++) {
                                        long key = messages.keyAt(a);
                                        ArrayList<MessageObject> value = messages.valueAt(a);
                                        updateInterfaceWithMessages(key, value, false);
                                    }
                                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                                });
                                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                    if (!pushMessages.isEmpty()) {
                                        AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(pushMessages, !(res instanceof TLRPC.TL_updates_differenceSlice), false, null));
                                    }
                                    getMessagesStorage().putMessages(res.new_messages, true, false, false, getDownloadController().getAutodownloadMask(), false);
                                });

                                getSecretChatHelper().processPendingEncMessages();
                            }

                            if (!res.other_updates.isEmpty()) {
                                processUpdateArray(res.other_updates, res.users, res.chats, true, 0);
                            }

                            if (res instanceof TLRPC.TL_updates_difference) {
                                gettingDifference = false;
                                getMessagesStorage().setLastSeqValue(res.state.seq);
                                getMessagesStorage().setLastDateValue(res.state.date);
                                getMessagesStorage().setLastPtsValue(res.state.pts);
                                getMessagesStorage().setLastQtsValue(res.state.qts);
                                getConnectionsManager().setIsUpdating(false);
                                for (int a = 0; a < 3; a++) {
                                    processUpdatesQueue(a, 1);
                                }
                            } else if (res instanceof TLRPC.TL_updates_differenceSlice) {
                                getMessagesStorage().setLastDateValue(res.intermediate_state.date);
                                getMessagesStorage().setLastPtsValue(res.intermediate_state.pts);
                                getMessagesStorage().setLastQtsValue(res.intermediate_state.qts);
                            } else if (res instanceof TLRPC.TL_updates_differenceEmpty) {
                                gettingDifference = false;
                                getMessagesStorage().setLastSeqValue(res.seq);
                                getMessagesStorage().setLastDateValue(res.date);
                                getConnectionsManager().setIsUpdating(false);
                                for (int a = 0; a < 3; a++) {
                                    processUpdatesQueue(a, 1);
                                }
                            }
                            getMessagesStorage().saveDiffParams(getMessagesStorage().getLastSeqValue(), getMessagesStorage().getLastPtsValue(), getMessagesStorage().getLastDateValue(), getMessagesStorage().getLastQtsValue());
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("received difference with date = " + getMessagesStorage().getLastDateValue() + " pts = " + getMessagesStorage().getLastPtsValue() + " seq = " + getMessagesStorage().getLastSeqValue() + " messages = " + res.new_messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                            }
                        });
                    });
                }
            } else {
                gettingDifference = false;
                getConnectionsManager().setIsUpdating(false);
            }
        });
    }

    public void markDialogAsUnread(long dialogId, TLRPC.InputPeer peer, long taskId) {
        TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
        if (dialog != null) {
            dialog.unread_mark = true;
            if (dialog.unread_count == 0 && !isDialogMuted(dialogId)) {
                unreadUnmutedDialogs++;
            }
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
            getMessagesStorage().setDialogUnread(dialogId, true);
            for (int b = 0; b < selectedDialogFilter.length; b++) {
                if (selectedDialogFilter[b] != null && (selectedDialogFilter[b].flags & DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                    sortDialogs(null);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    break;
                }
            }
        }
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.TL_messages_markDialogUnread req = new TLRPC.TL_messages_markDialogUnread();
            req.unread = true;
            if (peer == null) {
                peer = getInputPeer(dialogId);
            }
            if (peer instanceof TLRPC.TL_inputPeerEmpty) {
                return;
            }
            TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
            inputDialogPeer.peer = peer;
            req.peer = inputDialogPeer;

            long newTaskId;
            if (taskId == 0) {
                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(12 + peer.getObjectSize());
                    data.writeInt32(9);
                    data.writeInt64(dialogId);
                    peer.serializeToStream(data);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = getMessagesStorage().createPendingTask(data);
            } else {
                newTaskId = taskId;
            }

            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (newTaskId != 0) {
                    getMessagesStorage().removePendingTask(newTaskId);
                }
            });
        }
    }

    public void loadUnreadDialogs() {
        if (loadingUnreadDialogs || getUserConfig().unreadDialogsLoaded) {
            return;
        }
        loadingUnreadDialogs = true;
        TLRPC.TL_messages_getDialogUnreadMarks req = new TLRPC.TL_messages_getDialogUnreadMarks();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                for (int a = 0, size = vector.objects.size(); a < size; a++) {
                    TLRPC.DialogPeer peer = (TLRPC.DialogPeer) vector.objects.get(a);
                    if (peer instanceof TLRPC.TL_dialogPeer) {
                        TLRPC.TL_dialogPeer dialogPeer = (TLRPC.TL_dialogPeer) peer;
                        long did;
                        if (dialogPeer.peer.user_id != 0) {
                            did = dialogPeer.peer.user_id;
                        } else if (dialogPeer.peer.chat_id != 0) {
                            did = -dialogPeer.peer.chat_id;
                        } else {
                            did = -dialogPeer.peer.channel_id;
                        }
                        getMessagesStorage().setDialogUnread(did, true);
                        TLRPC.Dialog dialog = dialogs_dict.get(did);
                        if (dialog != null && !dialog.unread_mark) {
                            dialog.unread_mark = true;
                            if (dialog.unread_count == 0 && !isDialogMuted(did)) {
                                unreadUnmutedDialogs++;
                            }
                        }
                    }
                }
                getUserConfig().unreadDialogsLoaded = true;
                getUserConfig().saveConfig(false);
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                loadingUnreadDialogs = false;
            }
        }));
    }

    public void reorderPinnedDialogs(int folderId, ArrayList<TLRPC.InputDialogPeer> order, long taskId) {
        TLRPC.TL_messages_reorderPinnedDialogs req = new TLRPC.TL_messages_reorderPinnedDialogs();
        req.folder_id = folderId;
        req.force = true;

        long newTaskId;
        if (taskId == 0) {
            ArrayList<TLRPC.Dialog> dialogs = getDialogs(folderId);
            if (dialogs.isEmpty()) {
                return;
            }

            int size = 0;
            ArrayList<Long> dids = new ArrayList<>();
            ArrayList<Integer> pinned = new ArrayList<>();
            for (int a = 0, N = dialogs.size(); a < N; a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                if (dialog instanceof TLRPC.TL_dialogFolder) {
                    continue;
                }
                if (!dialog.pinned) {
                    if (dialog.id != promoDialogId) {
                        break;
                    }
                    continue;
                }
                dids.add(dialog.id);
                pinned.add(dialog.pinnedNum);
                if (!DialogObject.isEncryptedDialog(dialog.id)) {
                    TLRPC.InputPeer inputPeer = getInputPeer(dialog.id);
                    TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                    inputDialogPeer.peer = inputPeer;
                    req.order.add(inputDialogPeer);
                    size += inputDialogPeer.getObjectSize();
                }
            }
            getMessagesStorage().setDialogsPinned(dids, pinned);

            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(4 + 4 + 4 + size);
                data.writeInt32(16);
                data.writeInt32(folderId);
                data.writeInt32(req.order.size());
                for (int a = 0, N = req.order.size(); a < N; a++) {
                    req.order.get(a).serializeToStream(data);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = getMessagesStorage().createPendingTask(data);
        } else {
            req.order = order;
            newTaskId = taskId;
        }

        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
        });
    }

    public boolean pinDialog(long dialogId, boolean pin, TLRPC.InputPeer peer, long taskId) {
        TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
        if (dialog == null || dialog.pinned == pin) {
            return dialog != null;
        }
        int folderId = dialog.folder_id;
        ArrayList<TLRPC.Dialog> dialogs = getDialogs(folderId);
        dialog.pinned = pin;
        if (pin) {
            int maxPinnedNum = 0;
            for (int a = 0; a < dialogs.size(); a++) {
                TLRPC.Dialog d = dialogs.get(a);
                if (d instanceof TLRPC.TL_dialogFolder) {
                    continue;
                }
                if (!d.pinned) {
                    if (d.id != promoDialogId) {
                        break;
                    }
                    continue;
                }
                maxPinnedNum = Math.max(d.pinnedNum, maxPinnedNum);
            }
            dialog.pinnedNum = maxPinnedNum + 1;
        } else {
            dialog.pinnedNum = 0;
        }
        sortDialogs(null);
        if (!pin && !dialogs.isEmpty() && dialogs.get(dialogs.size() - 1) == dialog && !dialogsEndReached.get(folderId)) {
            dialogs.remove(dialogs.size() - 1);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        if (!DialogObject.isEncryptedDialog(dialogId)) {
            if (taskId != -1) {
                TLRPC.TL_messages_toggleDialogPin req = new TLRPC.TL_messages_toggleDialogPin();
                req.pinned = pin;
                if (peer == null) {
                    peer = getInputPeer(dialogId);
                }
                if (peer instanceof TLRPC.TL_inputPeerEmpty) {
                    return false;
                }
                TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                inputDialogPeer.peer = peer;
                req.peer = inputDialogPeer;

                long newTaskId;
                if (taskId == 0) {
                    NativeByteBuffer data = null;
                    try {
                        data = new NativeByteBuffer(16 + peer.getObjectSize());
                        data.writeInt32(4);
                        data.writeInt64(dialogId);
                        data.writeBool(pin);
                        peer.serializeToStream(data);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    newTaskId = getMessagesStorage().createPendingTask(data);
                } else {
                    newTaskId = taskId;
                }

                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (newTaskId != 0) {
                        getMessagesStorage().removePendingTask(newTaskId);
                    }
                });
            }
        }
        getMessagesStorage().setDialogPinned(dialogId, dialog.pinnedNum);
        return true;
    }

    public void loadPinnedDialogs(final int folderId, long newDialogId, ArrayList<Long> order) {
        if (loadingPinnedDialogs.indexOfKey(folderId) >= 0 || getUserConfig().isPinnedDialogsLoaded(folderId)) {
            return;
        }
        loadingPinnedDialogs.put(folderId, 1);
        TLRPC.TL_messages_getPinnedDialogs req = new TLRPC.TL_messages_getPinnedDialogs();
        req.folder_id = folderId;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                ArrayList<TLRPC.Dialog> newPinnedDialogs = new ArrayList<>(res.dialogs);
                fetchFolderInLoadedPinnedDialogs(res);
                TLRPC.TL_messages_dialogs toCache = new TLRPC.TL_messages_dialogs();
                toCache.users.addAll(res.users);
                toCache.chats.addAll(res.chats);
                toCache.dialogs.addAll(res.dialogs);
                toCache.messages.addAll(res.messages);

                LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
                LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
                LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();

                for (int a = 0; a < res.users.size(); a++) {
                    TLRPC.User u = res.users.get(a);
                    usersDict.put(u.id, u);
                }
                for (int a = 0; a < res.chats.size(); a++) {
                    TLRPC.Chat c = res.chats.get(a);
                    chatsDict.put(c.id, c);
                }

                for (int a = 0; a < res.messages.size(); a++) {
                    TLRPC.Message message = res.messages.get(a);
                    if (message.peer_id.channel_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.peer_id.channel_id);
                        if (chat != null && chat.left) {
                            continue;
                        }
                    } else if (message.peer_id.chat_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.peer_id.chat_id);
                        if (chat != null && chat.migrated_to != null) {
                            continue;
                        }
                    }
                    MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false, true);
                    new_dialogMessage.put(messageObject.getDialogId(), messageObject);
                }
                boolean firstIsFolder = !newPinnedDialogs.isEmpty() && newPinnedDialogs.get(0) instanceof TLRPC.TL_dialogFolder;
                for (int a = 0, N = newPinnedDialogs.size(); a < N; a++) {
                    TLRPC.Dialog d = newPinnedDialogs.get(a);
                    d.pinned = true;
                    DialogObject.initDialog(d);
                    if (DialogObject.isChannel(d)) {
                        TLRPC.Chat chat = chatsDict.get(-d.id);
                        if (chat != null && chat.left) {
                            continue;
                        }
                    } else if (DialogObject.isChatDialog(d.id)) {
                        TLRPC.Chat chat = chatsDict.get(-d.id);
                        if (chat != null && chat.migrated_to != null) {
                            continue;
                        }
                    }
                    if (d.last_message_date == 0) {
                        MessageObject mess = new_dialogMessage.get(d.id);
                        if (mess != null) {
                            d.last_message_date = mess.messageOwner.date;
                        }
                    }

                    Integer value = dialogs_read_inbox_max.get(d.id);
                    if (value == null) {
                        value = 0;
                    }
                    dialogs_read_inbox_max.put(d.id, Math.max(value, d.read_inbox_max_id));

                    value = dialogs_read_outbox_max.get(d.id);
                    if (value == null) {
                        value = 0;
                    }
                    dialogs_read_outbox_max.put(d.id, Math.max(value, d.read_outbox_max_id));
                }

                getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    loadingPinnedDialogs.delete(folderId);
                    applyDialogsNotificationsSettings(newPinnedDialogs);
                    boolean changed = false;
                    boolean added = false;
                    int maxPinnedNum = 0;

                    ArrayList<TLRPC.Dialog> dialogs = getDialogs(folderId);

                    int pinnedNum = firstIsFolder ? 1 : 0;
                    for (int a = 0; a < dialogs.size(); a++) {
                        TLRPC.Dialog dialog = dialogs.get(a);
                        if (dialog instanceof TLRPC.TL_dialogFolder) {
                            continue;
                        }
                        if (DialogObject.isEncryptedDialog(dialog.id)) {
                            if (pinnedNum < newPinnedDialogs.size()) {
                                newPinnedDialogs.add(pinnedNum, dialog);
                            } else {
                                newPinnedDialogs.add(dialog);
                            }
                            pinnedNum++;
                            continue;
                        }
                        if (!dialog.pinned) {
                            if (dialog.id != promoDialogId) {
                                break;
                            }
                            continue;
                        }
                        maxPinnedNum = Math.max(dialog.pinnedNum, maxPinnedNum);
                        dialog.pinned = false;
                        dialog.pinnedNum = 0;
                        changed = true;
                        pinnedNum++;
                    }

                    ArrayList<Long> pinnedDialogs = new ArrayList<>();
                    if (!newPinnedDialogs.isEmpty()) {
                        putUsers(res.users, false);
                        putChats(res.chats, false);
                        ArrayList<Long> dids = new ArrayList<>();
                        ArrayList<Integer> pinned = new ArrayList<>();
                        for (int a = 0, N = newPinnedDialogs.size(); a < N; a++) {
                            TLRPC.Dialog dialog = newPinnedDialogs.get(a);
                            dialog.pinnedNum = (N - a) + maxPinnedNum;

                            pinnedDialogs.add(dialog.id);
                            TLRPC.Dialog d = dialogs_dict.get(dialog.id);
                            if (d != null) {
                                d.pinned = true;
                                d.pinnedNum = dialog.pinnedNum;
                                dids.add(dialog.id);
                                pinned.add(dialog.pinnedNum);
                            } else {
                                added = true;
                                dialogs_dict.put(dialog.id, dialog);
                                MessageObject messageObject = new_dialogMessage.get(dialog.id);
                                dialogMessage.put(dialog.id, messageObject);
                                if (messageObject != null && messageObject.messageOwner.peer_id.channel_id == 0) {
                                    dialogMessagesByIds.put(messageObject.getId(), messageObject);
                                    dialogsLoadedTillDate = Math.min(dialogsLoadedTillDate, messageObject.messageOwner.date);
                                    if (messageObject.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
                                    }
                                }
                            }

                            changed = true;
                        }
                        getMessagesStorage().setDialogsPinned(dids, pinned);
                    }
                    if (changed) {
                        if (added) {
                            allDialogs.clear();
                            for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                                TLRPC.Dialog dialog = dialogs_dict.valueAt(a);
                                if (deletingDialogs.indexOfKey(dialog.id) >= 0) {
                                    continue;
                                }
                                allDialogs.add(dialog);
                            }
                        }
                        sortDialogs(null);
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    }
                    getMessagesStorage().unpinAllDialogsExceptNew(pinnedDialogs, folderId);
                    getMessagesStorage().putDialogs(toCache, 1);
                    getUserConfig().setPinnedDialogsLoaded(folderId, true);
                    getUserConfig().saveConfig(false);
                }));
            }
        });
    }

    public void generateJoinMessage(long chatId, boolean ignoreLeft) {
        TLRPC.Chat chat = getChat(chatId);
        if (chat == null || !ChatObject.isChannel(chatId, currentAccount) || (chat.left || chat.kicked) && !ignoreLeft) {
            return;
        }

        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
        message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.local_id = message.id = getUserConfig().getNewMessageId();
        message.date = getConnectionsManager().getCurrentTime();
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = getUserConfig().getClientUserId();
        message.peer_id = new TLRPC.TL_peerChannel();
        message.peer_id.channel_id = chatId;
        message.dialog_id = -chatId;
        message.post = true;
        message.action = new TLRPC.TL_messageActionChatAddUser();
        message.action.users.add(getUserConfig().getClientUserId());
        getUserConfig().saveConfig(false);

        ArrayList<MessageObject> pushMessages = new ArrayList<>();
        ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();

        messagesArr.add(message);
        MessageObject obj = new MessageObject(currentAccount, message, true, false);
        pushMessages.add(obj);

        getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(pushMessages, true, false, null)));
        getMessagesStorage().putMessages(messagesArr, true, true, false, 0, false);

        AndroidUtilities.runOnUIThread(() -> {
            updateInterfaceWithMessages(-chatId, pushMessages, false);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        });
    }

    protected void deleteMessagesByPush(long dialogId, ArrayList<Integer> ids, long channelId) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, ids, channelId, false);
                if (channelId == 0) {
                    for (int b = 0, size2 = ids.size(); b < size2; b++) {
                        Integer id = ids.get(b);
                        MessageObject obj = dialogMessagesByIds.get(id);
                        if (obj != null) {
                            obj.deleted = true;
                        }
                    }
                } else {
                    MessageObject obj = dialogMessage.get(-channelId);
                    if (obj != null) {
                        for (int b = 0, size2 = ids.size(); b < size2; b++) {
                            if (obj.getId() == ids.get(b)) {
                                obj.deleted = true;
                                break;
                            }
                        }
                    }
                }
            });
            getMessagesStorage().deletePushMessages(dialogId, ids);
            ArrayList<Long> dialogIds = getMessagesStorage().markMessagesAsDeleted(dialogId, ids, false, true, false);
            getMessagesStorage().updateDialogsWithDeletedMessages(dialogId, channelId, ids, dialogIds, false);
        });
    }

    public void checkChatInviter(long chatId, boolean createMessage) {
        TLRPC.Chat chat = getChat(chatId);
        if (!ChatObject.isChannel(chat) || chat.creator || gettingChatInviters.indexOfKey(chatId) >= 0) {
            return;
        }
        gettingChatInviters.put(chatId, true);
        TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
        req.channel = getInputChannel(chatId);
        req.participant = getInputPeer(getUserConfig().getClientUserId());
        getConnectionsManager().sendRequest(req, (response, error) -> {
            TLRPC.TL_channels_channelParticipant res = (TLRPC.TL_channels_channelParticipant) response;
            if (res != null && res.participant instanceof TLRPC.TL_channelParticipantSelf) {
                TLRPC.TL_channelParticipantSelf selfParticipant = (TLRPC.TL_channelParticipantSelf) res.participant;
                if (selfParticipant.inviter_id != getUserConfig().getClientUserId() || selfParticipant.via_invite) {
                    if (chat.megagroup && getMessagesStorage().isMigratedChat(chat.id)) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        putUsers(res.users, false);
                        putChats(res.chats, false);
                    });
                    getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);

                    ArrayList<MessageObject> pushMessages;
                    if (createMessage && Math.abs(getConnectionsManager().getCurrentTime() - res.participant.date) < 24 * 60 * 60 && !getMessagesStorage().hasInviteMeMessage(chatId)) {
                        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
                        message.media_unread = true;
                        message.unread = true;
                        message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                        message.post = true;
                        message.local_id = message.id = getUserConfig().getNewMessageId();
                        message.date = res.participant.date;
                        if (selfParticipant.inviter_id != getUserConfig().getClientUserId()) {
                            message.action = new TLRPC.TL_messageActionChatAddUser();
                        } else if (selfParticipant.via_invite) {
                            message.action = new TLRPC.TL_messageActionChatJoinedByRequest();
                        }
                        message.from_id = new TLRPC.TL_peerUser();
                        message.from_id.user_id = res.participant.inviter_id;
                        message.action.users.add(getUserConfig().getClientUserId());
                        message.peer_id = new TLRPC.TL_peerChannel();
                        message.peer_id.channel_id = chatId;
                        message.dialog_id = -chatId;
                        getUserConfig().saveConfig(false);

                        pushMessages = new ArrayList<>();
                        ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();

                        ConcurrentHashMap<Long, TLRPC.User> usersDict = new ConcurrentHashMap<>();
                        for (int a = 0; a < res.users.size(); a++) {
                            TLRPC.User user = res.users.get(a);
                            usersDict.put(user.id, user);
                        }

                        messagesArr.add(message);
                        MessageObject obj = new MessageObject(currentAccount, message, usersDict, true, false);
                        pushMessages.add(obj);
                        getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(pushMessages, true, false, null)));
                        getMessagesStorage().putMessages(messagesArr, true, true, false, 0, false);
                    } else {
                        pushMessages = null;
                    }

                    getMessagesStorage().saveChatInviter(chatId, res.participant.inviter_id);

                    AndroidUtilities.runOnUIThread(() -> {
                        gettingChatInviters.delete(chatId);
                        if (pushMessages != null) {
                            updateInterfaceWithMessages(-chatId, pushMessages, false);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.didLoadChatInviter, chatId, res.participant.inviter_id);
                    });
                }
            }
        });
    }

    private int getUpdateType(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateReadMessagesContents || update instanceof TLRPC.TL_updateReadHistoryInbox ||
                update instanceof TLRPC.TL_updateReadHistoryOutbox || update instanceof TLRPC.TL_updateDeleteMessages || update instanceof TLRPC.TL_updateWebPage ||
                update instanceof TLRPC.TL_updateEditMessage || update instanceof TLRPC.TL_updateFolderPeers || update instanceof TLRPC.TL_updatePinnedMessages) {
            return 0;
        } else if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
            return 1;
        } else if (update instanceof TLRPC.TL_updateNewChannelMessage || update instanceof TLRPC.TL_updateDeleteChannelMessages || update instanceof TLRPC.TL_updateEditChannelMessage ||
                update instanceof TLRPC.TL_updateChannelWebPage || update instanceof TLRPC.TL_updatePinnedChannelMessages) {
            return 2;
        } else {
            return 3;
        }
    }

    private static int getUpdatePts(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateDeleteMessages) {
            return ((TLRPC.TL_updateDeleteMessages) update).pts;
        } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
            return ((TLRPC.TL_updateNewChannelMessage) update).pts;
        } else if (update instanceof TLRPC.TL_updateReadHistoryOutbox) {
            return ((TLRPC.TL_updateReadHistoryOutbox) update).pts;
        } else if (update instanceof TLRPC.TL_updateNewMessage) {
            return ((TLRPC.TL_updateNewMessage) update).pts;
        } else if (update instanceof TLRPC.TL_updateEditMessage) {
            return ((TLRPC.TL_updateEditMessage) update).pts;
        } else if (update instanceof TLRPC.TL_updateWebPage) {
            return ((TLRPC.TL_updateWebPage) update).pts;
        } else if (update instanceof TLRPC.TL_updateReadHistoryInbox) {
            return ((TLRPC.TL_updateReadHistoryInbox) update).pts;
        } else if (update instanceof TLRPC.TL_updateChannelWebPage) {
            return ((TLRPC.TL_updateChannelWebPage) update).pts;
        } else if (update instanceof TLRPC.TL_updateDeleteChannelMessages) {
            return ((TLRPC.TL_updateDeleteChannelMessages) update).pts;
        } else if (update instanceof TLRPC.TL_updateEditChannelMessage) {
            return ((TLRPC.TL_updateEditChannelMessage) update).pts;
        } else if (update instanceof TLRPC.TL_updateReadMessagesContents) {
            return ((TLRPC.TL_updateReadMessagesContents) update).pts;
        } else if (update instanceof TLRPC.TL_updateChannelTooLong) {
            return ((TLRPC.TL_updateChannelTooLong) update).pts;
        } else if (update instanceof TLRPC.TL_updateFolderPeers) {
            return ((TLRPC.TL_updateFolderPeers) update).pts;
        } else if (update instanceof TLRPC.TL_updatePinnedChannelMessages) {
            return ((TLRPC.TL_updatePinnedChannelMessages) update).pts;
        } else if (update instanceof TLRPC.TL_updatePinnedMessages) {
            return ((TLRPC.TL_updatePinnedMessages) update).pts;
        } else {
            return 0;
        }
    }

    private static int getUpdatePtsCount(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateDeleteMessages) {
            return ((TLRPC.TL_updateDeleteMessages) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateNewChannelMessage) {
            return ((TLRPC.TL_updateNewChannelMessage) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateReadHistoryOutbox) {
            return ((TLRPC.TL_updateReadHistoryOutbox) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateNewMessage) {
            return ((TLRPC.TL_updateNewMessage) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateEditMessage) {
            return ((TLRPC.TL_updateEditMessage) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateWebPage) {
            return ((TLRPC.TL_updateWebPage) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateReadHistoryInbox) {
            return ((TLRPC.TL_updateReadHistoryInbox) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateChannelWebPage) {
            return ((TLRPC.TL_updateChannelWebPage) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateDeleteChannelMessages) {
            return ((TLRPC.TL_updateDeleteChannelMessages) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateEditChannelMessage) {
            return ((TLRPC.TL_updateEditChannelMessage) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateReadMessagesContents) {
            return ((TLRPC.TL_updateReadMessagesContents) update).pts_count;
        } else if (update instanceof TLRPC.TL_updateFolderPeers) {
            return ((TLRPC.TL_updateFolderPeers) update).pts_count;
        } else if (update instanceof TLRPC.TL_updatePinnedChannelMessages) {
            return ((TLRPC.TL_updatePinnedChannelMessages) update).pts_count;
        } else if (update instanceof TLRPC.TL_updatePinnedMessages) {
            return ((TLRPC.TL_updatePinnedMessages) update).pts_count;
        } else {
            return 0;
        }
    }

    private static int getUpdateQts(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
            return ((TLRPC.TL_updateNewEncryptedMessage) update).qts;
        } else {
            return 0;
        }
    }

    public static long getUpdateChannelId(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateNewChannelMessage) {
            return ((TLRPC.TL_updateNewChannelMessage) update).message.peer_id.channel_id;
        } else if (update instanceof TLRPC.TL_updateEditChannelMessage) {
            return ((TLRPC.TL_updateEditChannelMessage) update).message.peer_id.channel_id;
        } else if (update instanceof TLRPC.TL_updateReadChannelOutbox) {
            return ((TLRPC.TL_updateReadChannelOutbox) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelMessageViews) {
            return ((TLRPC.TL_updateChannelMessageViews) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelMessageForwards) {
            return ((TLRPC.TL_updateChannelMessageForwards) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelTooLong) {
            return ((TLRPC.TL_updateChannelTooLong) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelReadMessagesContents) {
            return ((TLRPC.TL_updateChannelReadMessagesContents) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelAvailableMessages) {
            return ((TLRPC.TL_updateChannelAvailableMessages) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannel) {
            return ((TLRPC.TL_updateChannel) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelWebPage) {
            return ((TLRPC.TL_updateChannelWebPage) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateDeleteChannelMessages) {
            return ((TLRPC.TL_updateDeleteChannelMessages) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateReadChannelInbox) {
            return ((TLRPC.TL_updateReadChannelInbox) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateReadChannelDiscussionInbox) {
            return ((TLRPC.TL_updateReadChannelDiscussionInbox) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateReadChannelDiscussionOutbox) {
            return ((TLRPC.TL_updateReadChannelDiscussionOutbox) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelUserTyping) {
            return ((TLRPC.TL_updateChannelUserTyping) update).channel_id;
        } else if (update instanceof TLRPC.TL_updatePinnedChannelMessages) {
            return ((TLRPC.TL_updatePinnedChannelMessages) update).channel_id;
        } else {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("trying to get unknown update channel_id for " + update);
            }
            return 0;
        }
    }

    public void processUpdates(final TLRPC.Updates updates, boolean fromQueue) {
        ArrayList<Long> needGetChannelsDiff = null;
        boolean needGetDiff = false;
        boolean needReceivedQueue = false;
        boolean updateStatus = false;
        if (updates instanceof TLRPC.TL_updateShort) {
            ArrayList<TLRPC.Update> arr = new ArrayList<>();
            arr.add(updates.update);
            processUpdateArray(arr, null, null, false, updates.date);
        } else if (updates instanceof TLRPC.TL_updateShortChatMessage || updates instanceof TLRPC.TL_updateShortMessage) {
            long userId = updates instanceof TLRPC.TL_updateShortChatMessage ? updates.from_id : updates.user_id;
            TLRPC.User user = getUser(userId);
            TLRPC.User user2 = null;
            TLRPC.User user3 = null;
            TLRPC.Chat channel = null;

            if (user == null || user.min) {
                user = getMessagesStorage().getUserSync(userId);
                if (user != null && user.min) {
                    user = null;
                }
                putUser(user, true);
            }

            boolean needFwdUser = false;
            if (updates.fwd_from != null) {
                if (updates.fwd_from.from_id instanceof TLRPC.TL_peerUser) {
                    user2 = getUser(updates.fwd_from.from_id.user_id);
                    if (user2 == null) {
                        user2 = getMessagesStorage().getUserSync(updates.fwd_from.from_id.user_id);
                        putUser(user2, true);
                    }
                    needFwdUser = true;
                } else if (updates.fwd_from.from_id instanceof TLRPC.TL_peerChannel) {
                    channel = getChat(updates.fwd_from.from_id.channel_id);
                    if (channel == null) {
                        channel = getMessagesStorage().getChatSync(updates.fwd_from.from_id.channel_id);
                        putChat(channel, true);
                    }
                    needFwdUser = true;
                } else if (updates.fwd_from.from_id instanceof TLRPC.TL_peerChat) {
                    channel = getChat(updates.fwd_from.from_id.chat_id);
                    if (channel == null) {
                        channel = getMessagesStorage().getChatSync(updates.fwd_from.from_id.chat_id);
                        putChat(channel, true);
                    }
                    needFwdUser = true;
                }
            }

            boolean needBotUser = false;
            if (updates.via_bot_id != 0) {
                user3 = getUser(updates.via_bot_id);
                if (user3 == null) {
                    user3 = getMessagesStorage().getUserSync(updates.via_bot_id);
                    putUser(user3, true);
                }
                needBotUser = true;
            }

            boolean missingData;
            if (updates instanceof TLRPC.TL_updateShortMessage) {
                missingData = user == null || needFwdUser && user2 == null && channel == null || needBotUser && user3 == null;
            } else {
                TLRPC.Chat chat = getChat(updates.chat_id);
                if (chat == null) {
                    chat = getMessagesStorage().getChatSync(updates.chat_id);
                    putChat(chat, true);
                }
                missingData = chat == null || user == null || needFwdUser && user2 == null && channel == null || needBotUser && user3 == null;
            }
            if (!missingData && !updates.entities.isEmpty()) {
                for (int a = 0; a < updates.entities.size(); a++) {
                    TLRPC.MessageEntity entity = updates.entities.get(a);
                    if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                        long uid = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
                        TLRPC.User entityUser = getUser(uid);
                        if (entityUser == null || entityUser.min) {
                            entityUser = getMessagesStorage().getUserSync(uid);
                            if (entityUser != null && entityUser.min) {
                                entityUser = null;
                            }
                            if (entityUser == null) {
                                missingData = true;
                                break;
                            }
                            putUser(user, true);
                        }
                    }
                }
            }
            if (!updates.out && user != null && user.status != null && user.status.expires <= 0 && Math.abs(getConnectionsManager().getCurrentTime() - updates.date) < 30) {
                onlinePrivacy.put(user.id, updates.date);
                updateStatus = true;
            }

            if (missingData) {
                needGetDiff = true;
            } else {
                if (getMessagesStorage().getLastPtsValue() + updates.pts_count == updates.pts) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = updates.id;
                    long clientUserId = getUserConfig().getClientUserId();
                    if (updates instanceof TLRPC.TL_updateShortMessage) {
                        message.from_id = new TLRPC.TL_peerUser();
                        if (updates.out) {
                            message.from_id.user_id = clientUserId;
                        } else {
                            message.from_id.user_id = userId;
                        }
                        message.peer_id = new TLRPC.TL_peerUser();
                        message.peer_id.user_id = userId;
                        message.dialog_id = userId;
                    } else {
                        message.from_id = new TLRPC.TL_peerUser();
                        message.from_id.user_id = userId;
                        message.peer_id = new TLRPC.TL_peerChat();
                        message.peer_id.chat_id = updates.chat_id;
                        message.dialog_id = -updates.chat_id;
                    }

                    message.fwd_from = updates.fwd_from;
                    message.silent = updates.silent;
                    message.out = updates.out;
                    message.mentioned = updates.mentioned;
                    message.media_unread = updates.media_unread;
                    message.entities = updates.entities;
                    message.message = updates.message;
                    message.date = updates.date;
                    message.via_bot_id = updates.via_bot_id;
                    message.flags = updates.flags | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    message.reply_to = updates.reply_to;
                    message.ttl_period = updates.ttl_period;
                    message.media = new TLRPC.TL_messageMediaEmpty();

                    ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                    Integer value = read_max.get(message.dialog_id);
                    if (value == null) {
                        value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                        read_max.put(message.dialog_id, value);
                    }
                    message.unread = value < message.id;

                    if (message.dialog_id == clientUserId) {
                        message.unread = false;
                        message.media_unread = false;
                        message.out = true;
                    }

                    getMessagesStorage().setLastPtsValue(updates.pts);
                    boolean isDialogCreated = createdDialogIds.contains(message.dialog_id);
                    MessageObject obj = new MessageObject(currentAccount, message, isDialogCreated, isDialogCreated);
                    ArrayList<MessageObject> objArr = new ArrayList<>();
                    objArr.add(obj);
                    ArrayList<TLRPC.Message> arr = new ArrayList<>();
                    arr.add(message);
                    if (updates instanceof TLRPC.TL_updateShortMessage) {
                        boolean printUpdate = !updates.out && updatePrintingUsersWithNewMessages(updates.user_id, objArr);
                        if (printUpdate) {
                            updatePrintingStrings();
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (printUpdate) {
                                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }
                            updateInterfaceWithMessages(userId, objArr, false);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        });
                    } else {
                        boolean printUpdate = updatePrintingUsersWithNewMessages(-updates.chat_id, objArr);
                        if (printUpdate) {
                            updatePrintingStrings();
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (printUpdate) {
                                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }

                            updateInterfaceWithMessages(-updates.chat_id, objArr, false);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        });
                    }
                    if (!obj.isOut()) {
                        getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(objArr, true, false, null)));
                    }
                    getMessagesStorage().putMessages(arr, false, true, false, 0, false);
                } else if (getMessagesStorage().getLastPtsValue() != updates.pts) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("need get diff short message, pts: " + getMessagesStorage().getLastPtsValue() + " " + updates.pts + " count = " + updates.pts_count);
                    }
                    if (gettingDifference || updatesStartWaitTimePts == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
                        if (updatesStartWaitTimePts == 0) {
                            updatesStartWaitTimePts = System.currentTimeMillis();
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("add to queue");
                        }
                        updatesQueuePts.add(updates);
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesCombined || updates instanceof TLRPC.TL_updates) {
            LongSparseArray<TLRPC.Chat> minChannels = null;
            for (int a = 0; a < updates.chats.size(); a++) {
                TLRPC.Chat chat = updates.chats.get(a);
                if (chat instanceof TLRPC.TL_channel) {
                    if (chat.min) {
                        TLRPC.Chat existChat = getChat(chat.id);
                        if (existChat == null || existChat.min) {
                            TLRPC.Chat cacheChat = getMessagesStorage().getChatSync(updates.chat_id);
                            putChat(cacheChat, true);
                            existChat = cacheChat;
                        }
                        if (existChat == null || existChat.min) {
                            if (minChannels == null) {
                                minChannels = new LongSparseArray<>();
                            }
                            minChannels.put(chat.id, chat);
                        }
                    }
                }
            }
            if (minChannels != null) {
                for (int a = 0; a < updates.updates.size(); a++) {
                    TLRPC.Update update = updates.updates.get(a);
                    if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                        TLRPC.Message message = ((TLRPC.TL_updateNewChannelMessage) update).message;
                        long channelId = message.peer_id.channel_id;
                        if (minChannels.indexOfKey(channelId) >= 0) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("need get diff because of min channel " + channelId);
                            }
                            needGetDiff = true;
                            break;
                        }
                        /*if (message.fwd_from != null && message.fwd_from.channel_id != 0) {
                            channelId = message.fwd_from.channel_id;
                            if (minChannels.containsKey(channelId)) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.e("need get diff because of min forward channel " + channelId);
                                }
                                needGetDiff = true;
                                break;
                            }
                        }*/
                    }
                }
            }
            if (!needGetDiff) {
                getMessagesStorage().putUsersAndChats(updates.users, updates.chats, true, true);
                Collections.sort(updates.updates, updatesComparator);
                for (int a = 0; a < updates.updates.size(); a++) {
                    TLRPC.Update update = updates.updates.get(a);
                    if (getUpdateType(update) == 0) {
                        TLRPC.TL_updates updatesNew = new TLRPC.TL_updates();
                        updatesNew.updates.add(update);
                        updatesNew.pts = getUpdatePts(update);
                        updatesNew.pts_count = getUpdatePtsCount(update);
                        for (int b = a + 1; b < updates.updates.size(); b++) {
                            TLRPC.Update update2 = updates.updates.get(b);
                            int pts2 = getUpdatePts(update2);
                            int count2 = getUpdatePtsCount(update2);
                            if (getUpdateType(update2) == 0 && updatesNew.pts + count2 == pts2) {
                                updatesNew.updates.add(update2);
                                updatesNew.pts = pts2;
                                updatesNew.pts_count += count2;
                                updates.updates.remove(b);
                                b--;
                            } else {
                                break;
                            }
                        }
                        if (getMessagesStorage().getLastPtsValue() + updatesNew.pts_count == updatesNew.pts) {
                            if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats, false, updates.date)) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("need get diff inner TL_updates, pts: " + getMessagesStorage().getLastPtsValue() + " " + updates.seq);
                                }
                                needGetDiff = true;
                            } else {
                                getMessagesStorage().setLastPtsValue(updatesNew.pts);
                            }
                        } else if (getMessagesStorage().getLastPtsValue() != updatesNew.pts) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d(update + " need get diff, pts: " + getMessagesStorage().getLastPtsValue() + " " + updatesNew.pts + " count = " + updatesNew.pts_count);
                            }
                            if (gettingDifference || updatesStartWaitTimePts == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
                                if (updatesStartWaitTimePts == 0) {
                                    updatesStartWaitTimePts = System.currentTimeMillis();
                                }
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("add to queue");
                                }
                                updatesQueuePts.add(updatesNew);
                            } else {
                                needGetDiff = true;
                            }
                        }
                    } else if (getUpdateType(update) == 1) {
                        TLRPC.TL_updates updatesNew = new TLRPC.TL_updates();
                        updatesNew.updates.add(update);
                        updatesNew.pts = getUpdateQts(update);
                        for (int b = a + 1; b < updates.updates.size(); b++) {
                            TLRPC.Update update2 = updates.updates.get(b);
                            int qts2 = getUpdateQts(update2);
                            if (getUpdateType(update2) == 1 && updatesNew.pts + 1 == qts2) {
                                updatesNew.updates.add(update2);
                                updatesNew.pts = qts2;
                                updates.updates.remove(b);
                                b--;
                            } else {
                                break;
                            }
                        }
                        if (getMessagesStorage().getLastQtsValue() == 0 || getMessagesStorage().getLastQtsValue() + updatesNew.updates.size() == updatesNew.pts) {
                            processUpdateArray(updatesNew.updates, updates.users, updates.chats, false, updates.date);
                            getMessagesStorage().setLastQtsValue(updatesNew.pts);
                            needReceivedQueue = true;
                        } else if (getMessagesStorage().getLastPtsValue() != updatesNew.pts) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d(update + " need get diff, qts: " + getMessagesStorage().getLastQtsValue() + " " + updatesNew.pts);
                            }
                            if (gettingDifference || updatesStartWaitTimeQts == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimeQts) <= 1500) {
                                if (updatesStartWaitTimeQts == 0) {
                                    updatesStartWaitTimeQts = System.currentTimeMillis();
                                }
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("add to queue");
                                }
                                updatesQueueQts.add(updatesNew);
                            } else {
                                needGetDiff = true;
                            }
                        }
                    } else if (getUpdateType(update) == 2) {
                        long channelId = getUpdateChannelId(update);
                        boolean skipUpdate = false;
                        int channelPts = channelsPts.get(channelId);
                        if (channelPts == 0) {
                            channelPts = getMessagesStorage().getChannelPtsSync(channelId);
                            if (channelPts == 0) {
                                for (int c = 0; c < updates.chats.size(); c++) {
                                    TLRPC.Chat chat = updates.chats.get(c);
                                    if (chat.id == channelId) {
                                        loadUnknownChannel(chat, 0);
                                        skipUpdate = true;
                                        break;
                                    }
                                }
                            } else {
                                channelsPts.put(channelId, channelPts);
                            }
                        }
                        TLRPC.TL_updates updatesNew = new TLRPC.TL_updates();
                        updatesNew.updates.add(update);
                        updatesNew.pts = getUpdatePts(update);
                        updatesNew.pts_count = getUpdatePtsCount(update);
                        for (int b = a + 1; b < updates.updates.size(); b++) {
                            TLRPC.Update update2 = updates.updates.get(b);
                            int pts2 = getUpdatePts(update2);
                            int count2 = getUpdatePtsCount(update2);
                            if (getUpdateType(update2) == 2 && channelId == getUpdateChannelId(update2) && updatesNew.pts + count2 == pts2) {
                                updatesNew.updates.add(update2);
                                updatesNew.pts = pts2;
                                updatesNew.pts_count += count2;
                                updates.updates.remove(b);
                                b--;
                            } else {
                                break;
                            }
                        }
                        if (!skipUpdate) {
                            if (channelPts + updatesNew.pts_count == updatesNew.pts) {
                                if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats, false, updates.date)) {
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("need get channel diff inner TL_updates, channel_id = " + channelId);
                                    }
                                    if (needGetChannelsDiff == null) {
                                        needGetChannelsDiff = new ArrayList<>();
                                    } else if (!needGetChannelsDiff.contains(channelId)) {
                                        needGetChannelsDiff.add(channelId);
                                    }
                                } else {
                                    channelsPts.put(channelId, updatesNew.pts);
                                    getMessagesStorage().saveChannelPts(channelId, updatesNew.pts);
                                }
                            } else if (channelPts != updatesNew.pts) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d(update + " need get channel diff, pts: " + channelPts + " " + updatesNew.pts + " count = " + updatesNew.pts_count + " channelId = " + channelId);
                                }
                                long updatesStartWaitTime = updatesStartWaitTimeChannels.get(channelId);
                                boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId, false);
                                if (gettingDifferenceChannel || updatesStartWaitTime == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
                                    if (updatesStartWaitTime == 0) {
                                        updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis());
                                    }
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("add to queue");
                                    }
                                    ArrayList<TLRPC.Updates> arrayList = updatesQueueChannels.get(channelId);
                                    if (arrayList == null) {
                                        arrayList = new ArrayList<>();
                                        updatesQueueChannels.put(channelId, arrayList);
                                    }
                                    arrayList.add(updatesNew);
                                } else {
                                    if (needGetChannelsDiff == null) {
                                        needGetChannelsDiff = new ArrayList<>();
                                    } else if (!needGetChannelsDiff.contains(channelId)) {
                                        needGetChannelsDiff.add(channelId);
                                    }
                                }
                            }
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("need load unknown channel = " + channelId);
                            }
                        }
                    } else {
                        break;
                    }
                    updates.updates.remove(a);
                    a--;
                }

                boolean processUpdate;
                if (updates instanceof TLRPC.TL_updatesCombined) {
                    processUpdate = getMessagesStorage().getLastSeqValue() + 1 == updates.seq_start || getMessagesStorage().getLastSeqValue() == updates.seq_start;
                } else {
                    processUpdate = getMessagesStorage().getLastSeqValue() + 1 == updates.seq || updates.seq == 0 || updates.seq == getMessagesStorage().getLastSeqValue();
                }
                if (processUpdate) {
                    processUpdateArray(updates.updates, updates.users, updates.chats, false, updates.date);
                    if (updates.seq != 0) {
                        if (updates.date != 0) {
                            getMessagesStorage().setLastDateValue(updates.date);
                        }
                        getMessagesStorage().setLastSeqValue(updates.seq);
                    }
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        if (updates instanceof TLRPC.TL_updatesCombined) {
                            FileLog.d("need get diff TL_updatesCombined, seq: " + getMessagesStorage().getLastSeqValue() + " " + updates.seq_start);
                        } else {
                            FileLog.d("need get diff TL_updates, seq: " + getMessagesStorage().getLastSeqValue() + " " + updates.seq);
                        }
                    }

                    if (gettingDifference || updatesStartWaitTimeSeq == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimeSeq) <= 1500) {
                        if (updatesStartWaitTimeSeq == 0) {
                            updatesStartWaitTimeSeq = System.currentTimeMillis();
                        }
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("add TL_updates/Combined to queue");
                        }
                        updatesQueueSeq.add(updates);
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesTooLong) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("need get diff TL_updatesTooLong");
            }
            needGetDiff = true;
        } else if (updates instanceof UserActionUpdatesSeq) {
            getMessagesStorage().setLastSeqValue(updates.seq);
        } else if (updates instanceof UserActionUpdatesPts) {
            if (updates.chat_id != 0) {
                channelsPts.put(updates.chat_id, updates.pts);
                getMessagesStorage().saveChannelPts(updates.chat_id, updates.pts);
            } else {
                getMessagesStorage().setLastPtsValue(updates.pts);
            }
        }
        getSecretChatHelper().processPendingEncMessages();
        if (!fromQueue) {
            for (int a = 0; a < updatesQueueChannels.size(); a++) {
                long key = updatesQueueChannels.keyAt(a);
                if (needGetChannelsDiff != null && needGetChannelsDiff.contains(key)) {
                    getChannelDifference(key);
                } else {
                    processChannelsUpdatesQueue(key, 0);
                }
            }
            if (needGetDiff) {
                getDifference();
            } else {
                for (int a = 0; a < 3; a++) {
                    processUpdatesQueue(a, 0);
                }
            }
        }
        if (needReceivedQueue) {
            TLRPC.TL_messages_receivedQueue req = new TLRPC.TL_messages_receivedQueue();
            req.max_qts = getMessagesStorage().getLastQtsValue();
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
        if (updateStatus) {
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS));
        }
        getMessagesStorage().saveDiffParams(getMessagesStorage().getLastSeqValue(), getMessagesStorage().getLastPtsValue(), getMessagesStorage().getLastDateValue(), getMessagesStorage().getLastQtsValue());
    }

    private boolean applyFoldersUpdates(ArrayList<TLRPC.TL_updateFolderPeers> folderUpdates) {
        if (folderUpdates == null) {
            return false;
        }
        boolean updated = false;
        for (int a = 0, size = folderUpdates.size(); a < size; a++) {
            TLRPC.TL_updateFolderPeers update = folderUpdates.get(a);
            for (int b = 0, size2 = update.folder_peers.size(); b < size2; b++) {
                TLRPC.TL_folderPeer folderPeer = update.folder_peers.get(b);
                long dialogId = DialogObject.getPeerDialogId(folderPeer.peer);
                TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
                if (dialog == null) {
                    continue;
                }
                if (dialog.folder_id != folderPeer.folder_id) {
                    dialog.pinned = false;
                    dialog.pinnedNum = 0;
                    dialog.folder_id = folderPeer.folder_id;
                    ensureFolderDialogExists(folderPeer.folder_id, null);
                }
            }
            updated = true;
            getMessagesStorage().setDialogsFolderId(folderUpdates.get(a).folder_peers, null, 0, 0);
        }
        return updated;
    }

    public boolean processUpdateArray(ArrayList<TLRPC.Update> updates, ArrayList<TLRPC.User> usersArr, ArrayList<TLRPC.Chat> chatsArr, boolean fromGetDifference, int date) {
        if (updates.isEmpty()) {
            if (usersArr != null || chatsArr != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(usersArr, false);
                    putChats(chatsArr, false);
                });
            }
            return true;
        }
        long currentTime = System.currentTimeMillis();
        boolean printChanged = false;

        LongSparseArray<ArrayList<MessageObject>> messages = null;
        LongSparseArray<ArrayList<MessageObject>> scheduledMessages = null;
        LongSparseArray<TLRPC.WebPage> webPages = null;
        ArrayList<MessageObject> pushMessages = null;
        ArrayList<TLRPC.Message> messagesArr = null;
        ArrayList<TLRPC.TL_sendMessageEmojiInteraction> emojiInteractions = null;
        ArrayList<TLRPC.Message> scheduledMessagesArr = null;
        LongSparseArray<ArrayList<MessageObject>> editingMessages = null;
        LongSparseArray<SparseIntArray> channelViews = null;
        LongSparseArray<SparseIntArray> channelForwards = null;
        LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies = null;
        LongSparseIntArray markAsReadMessagesInbox = null;
        LongSparseIntArray markAsReadMessagesOutbox = null;
        LongSparseArray<ArrayList<Integer>> markContentAsReadMessages = null;
        SparseIntArray markAsReadEncrypted = null;
        LongSparseArray<ArrayList<Integer>> deletedMessages = null;
        LongSparseArray<ArrayList<Integer>> scheduledDeletedMessages = null;
        LongSparseArray<ArrayList<Long>> groupSpeakingActions = null;
        LongSparseIntArray importingActions = null;
        LongSparseIntArray clearHistoryMessages = null;
        ArrayList<TLRPC.ChatParticipants> chatInfoToUpdate = null;
        ArrayList<TLRPC.Update> updatesOnMainThread = null;
        ArrayList<TLRPC.TL_updateFolderPeers> folderUpdates = null;
        ArrayList<TLRPC.TL_updateEncryptedMessagesRead> tasks = null;
        ArrayList<Long> contactsIds = null;
        ArrayList<ImageLoader.MessageThumb> messageThumbs = null;

        ConcurrentHashMap<Long, TLRPC.User> usersDict;
        ConcurrentHashMap<Long, TLRPC.Chat> chatsDict;
        if (usersArr != null) {
            usersDict = new ConcurrentHashMap<>();
            for (int a = 0, size = usersArr.size(); a < size; a++) {
                TLRPC.User user = usersArr.get(a);
                usersDict.put(user.id, user);
            }
        } else {
            usersDict = users;
        }
        if (chatsArr != null) {
            chatsDict = new ConcurrentHashMap<>();
            for (int a = 0, size = chatsArr.size(); a < size; a++) {
                TLRPC.Chat chat = chatsArr.get(a);
                chatsDict.put(chat.id, chat);
            }
        } else {
            chatsDict = chats;
        }

        if (usersArr != null || chatsArr != null) {
            AndroidUtilities.runOnUIThread(() -> {
                putUsers(usersArr, false);
                putChats(chatsArr, false);
            });
        }

        int interfaceUpdateMask = 0;
        long clientUserId = getUserConfig().getClientUserId();

        for (int c = 0, size3 = updates.size(); c < size3; c++) {
            TLRPC.Update baseUpdate = updates.get(c);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("process update " + baseUpdate);
            }
            if (baseUpdate instanceof TLRPC.TL_updateNewMessage || baseUpdate instanceof TLRPC.TL_updateNewChannelMessage || baseUpdate instanceof TLRPC.TL_updateNewScheduledMessage) {
                TLRPC.Message message;
                if (baseUpdate instanceof TLRPC.TL_updateNewMessage) {
                    message = ((TLRPC.TL_updateNewMessage) baseUpdate).message;
                } else if (baseUpdate instanceof TLRPC.TL_updateNewScheduledMessage) {
                    message = ((TLRPC.TL_updateNewScheduledMessage) baseUpdate).message;
                } else {
                    message = ((TLRPC.TL_updateNewChannelMessage) baseUpdate).message;
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d(baseUpdate + " channelId = " + message.peer_id.channel_id);
                    }
                    if (!message.out && message.from_id instanceof TLRPC.TL_peerUser && message.from_id.user_id == getUserConfig().getClientUserId()) {
                        message.out = true;
                    }
                }
                if (message instanceof TLRPC.TL_messageEmpty) {
                    continue;
                }
                TLRPC.Chat chat = null;
                long chatId = 0;
                long userId = 0;
                if (message.peer_id.channel_id != 0) {
                    chatId = message.peer_id.channel_id;
                } else if (message.peer_id.chat_id != 0) {
                    chatId = message.peer_id.chat_id;
                } else if (message.peer_id.user_id != 0) {
                    userId = message.peer_id.user_id;
                }
                if (chatId != 0) {
                    chat = chatsDict.get(chatId);
                    if (chat == null || chat.min) {
                        chat = getChat(chatId);
                    }
                    if (chat == null || chat.min) {
                        chat = getMessagesStorage().getChatSync(chatId);
                        putChat(chat, true);
                    }
                }
                if (!fromGetDifference) {
                    if (chatId != 0) {
                        if (chat == null) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("not found chat " + chatId);
                            }
                            return false;
                        }
                    }

                    int count = 3 + message.entities.size();
                    for (int a = 0; a < count; a++) {
                        boolean allowMin = false;
                        if (a != 0) {
                            if (a == 1) {
                                userId = message.from_id instanceof TLRPC.TL_peerUser ? message.from_id.user_id : 0;
                                if (message.post) {
                                    allowMin = true;
                                }
                            } else if (a == 2) {
                                userId = message.fwd_from != null && message.fwd_from.from_id instanceof TLRPC.TL_peerUser ? message.fwd_from.from_id.user_id : 0;
                            } else {
                                TLRPC.MessageEntity entity = message.entities.get(a - 3);
                                userId = entity instanceof TLRPC.TL_messageEntityMentionName ? ((TLRPC.TL_messageEntityMentionName) entity).user_id : 0;
                            }
                        }
                        if (userId > 0) {
                            TLRPC.User user = usersDict.get(userId);
                            if (user == null || !allowMin && user.min) {
                                user = getUser(userId);
                            }
                            if (user == null || !allowMin && user.min) {
                                user = getMessagesStorage().getUserSync(userId);
                                if (user != null && !allowMin && user.min) {
                                    user = null;
                                }
                                putUser(user, true);
                            }
                            if (user == null) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("not found user " + userId);
                                }
                                return false;
                            }
                            if (!message.out && a == 1 && user.status != null && user.status.expires <= 0 && Math.abs(getConnectionsManager().getCurrentTime() - message.date) < 30) {
                                onlinePrivacy.put(userId, message.date);
                                interfaceUpdateMask |= UPDATE_MASK_STATUS;
                            }
                        }
                    }
                }

                if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    TLRPC.User user = usersDict.get(message.action.user_id);
                    if (user != null && user.bot) {
                        message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                        message.flags |= 64;
                    } else if (message.from_id instanceof TLRPC.TL_peerUser && message.from_id.user_id == clientUserId && message.action.user_id == clientUserId) {
                        continue;
                    }
                }

                ImageLoader.saveMessageThumbs(message);

                MessageObject.getDialogId(message);
                if (baseUpdate instanceof TLRPC.TL_updateNewChannelMessage && message.reply_to != null && !(message.action instanceof TLRPC.TL_messageActionPinMessage)) {
                    if (channelReplies == null) {
                        channelReplies = new LongSparseArray<>();
                    }
                    SparseArray<TLRPC.MessageReplies> replies = channelReplies.get(message.dialog_id);
                    if (replies == null) {
                        replies = new SparseArray<>();
                        channelReplies.put(message.dialog_id, replies);
                    }
                    int id = message.reply_to.reply_to_top_id != 0 ? message.reply_to.reply_to_top_id : message.reply_to.reply_to_msg_id;
                    TLRPC.MessageReplies messageReplies = replies.get(id);
                    if (messageReplies == null) {
                        messageReplies = new TLRPC.TL_messageReplies();
                        replies.put(id, messageReplies);
                    }
                    if (message.from_id != null) {
                        messageReplies.recent_repliers.add(0, message.from_id);
                    }
                    messageReplies.replies++;
                }

                if (createdDialogIds.contains(message.dialog_id) && message.grouped_id == 0) {
                    ImageLoader.MessageThumb messageThumb = ImageLoader.generateMessageThumb(message);
                    if (messageThumb != null) {
                        if (messageThumbs == null) {
                            messageThumbs = new ArrayList<>();
                        }
                        messageThumbs.add(messageThumb);
                    }
                }

                if (baseUpdate instanceof TLRPC.TL_updateNewScheduledMessage) {
                    if (scheduledMessagesArr == null) {
                        scheduledMessagesArr = new ArrayList<>();
                    }
                    scheduledMessagesArr.add(message);

                    boolean isDialogCreated = createdScheduledDialogIds.contains(message.dialog_id);
                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated);
                    obj.scheduled = true;

                    if (scheduledMessages == null) {
                        scheduledMessages = new LongSparseArray<>();
                    }
                    ArrayList<MessageObject> arr = scheduledMessages.get(message.dialog_id);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        scheduledMessages.put(message.dialog_id, arr);
                    }
                    arr.add(obj);
                } else {
                    if (messagesArr == null) {
                        messagesArr = new ArrayList<>();
                    }
                    messagesArr.add(message);

                    ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                    Integer value = read_max.get(message.dialog_id);
                    if (value == null) {
                        value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                        read_max.put(message.dialog_id, value);
                    }
                    message.unread = !(value >= message.id || chat != null && ChatObject.isNotInChat(chat) || message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate);
                    if (message.dialog_id == clientUserId) {
                        if (!message.from_scheduled) {
                            message.unread = false;
                        }
                        message.media_unread = false;
                        message.out = true;
                    }

                    boolean isDialogCreated = createdDialogIds.contains(message.dialog_id);
                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated);
                    if (obj.type == 11) {
                        interfaceUpdateMask |= UPDATE_MASK_CHAT_AVATAR;
                    } else if (obj.type == 10) {
                        interfaceUpdateMask |= UPDATE_MASK_CHAT_NAME;
                    }
                    if (messages == null) {
                        messages = new LongSparseArray<>();
                    }
                    ArrayList<MessageObject> arr = messages.get(message.dialog_id);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        messages.put(message.dialog_id, arr);
                    }
                    arr.add(obj);
                    if ((!obj.isOut() || obj.messageOwner.from_scheduled) && obj.isUnread() && (chat == null || !ChatObject.isNotInChat(chat) && !chat.min)) {
                        if (pushMessages == null) {
                            pushMessages = new ArrayList<>();
                        }
                        pushMessages.add(obj);
                    }
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateReadMessagesContents) {
                TLRPC.TL_updateReadMessagesContents update = (TLRPC.TL_updateReadMessagesContents) baseUpdate;
                if (markContentAsReadMessages == null) {
                    markContentAsReadMessages = new LongSparseArray<>();
                }
                ArrayList<Integer> ids = markContentAsReadMessages.get(0);
                if (ids == null) {
                    ids = new ArrayList<>();
                    markContentAsReadMessages.put(0, ids);
                }
                ids.addAll(update.messages);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelReadMessagesContents) {
                TLRPC.TL_updateChannelReadMessagesContents update = (TLRPC.TL_updateChannelReadMessagesContents) baseUpdate;
                if (markContentAsReadMessages == null) {
                    markContentAsReadMessages = new LongSparseArray<>();
                }
                long dialogId = -update.channel_id;
                ArrayList<Integer> ids = markContentAsReadMessages.get(dialogId);
                if (ids == null) {
                    ids = new ArrayList<>();
                    markContentAsReadMessages.put(dialogId, ids);
                }
                ids.addAll(update.messages);
            } else if (baseUpdate instanceof TLRPC.TL_updateReadHistoryInbox) {
                TLRPC.TL_updateReadHistoryInbox update = (TLRPC.TL_updateReadHistoryInbox) baseUpdate;
                long dialogId;
                if (markAsReadMessagesInbox == null) {
                    markAsReadMessagesInbox = new LongSparseIntArray();
                }
                if (update.peer.chat_id != 0) {
                    markAsReadMessagesInbox.put(-update.peer.chat_id, update.max_id);
                    dialogId = -update.peer.chat_id;
                } else {
                    markAsReadMessagesInbox.put(update.peer.user_id, update.max_id);
                    dialogId = update.peer.user_id;
                }
                Integer value = dialogs_read_inbox_max.get(dialogId);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(false, dialogId);
                }
                dialogs_read_inbox_max.put(dialogId, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateReadHistoryOutbox) {
                TLRPC.TL_updateReadHistoryOutbox update = (TLRPC.TL_updateReadHistoryOutbox) baseUpdate;
                long dialogId;
                if (markAsReadMessagesOutbox == null) {
                    markAsReadMessagesOutbox = new LongSparseIntArray();
                }
                if (update.peer.chat_id != 0) {
                    markAsReadMessagesOutbox.put(-update.peer.chat_id, update.max_id);
                    dialogId = -update.peer.chat_id;
                } else {
                    markAsReadMessagesOutbox.put(update.peer.user_id, update.max_id);
                    dialogId = update.peer.user_id;
                    TLRPC.User user = getUser(update.peer.user_id);
                    if (user != null && user.status != null && user.status.expires <= 0 && Math.abs(getConnectionsManager().getCurrentTime() - date) < 30) {
                        onlinePrivacy.put(update.peer.user_id, date);
                        interfaceUpdateMask |= UPDATE_MASK_STATUS;
                    }
                }
                Integer value = dialogs_read_outbox_max.get(dialogId);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(true, dialogId);
                }
                dialogs_read_outbox_max.put(dialogId, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateDeleteMessages) {
                TLRPC.TL_updateDeleteMessages update = (TLRPC.TL_updateDeleteMessages) baseUpdate;
                if (deletedMessages == null) {
                    deletedMessages = new LongSparseArray<>();
                }
                ArrayList<Integer> arrayList = deletedMessages.get(0);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(0, arrayList);
                }
                arrayList.addAll(update.messages);
            } else if (baseUpdate instanceof TLRPC.TL_updateDeleteScheduledMessages) {
                TLRPC.TL_updateDeleteScheduledMessages update = (TLRPC.TL_updateDeleteScheduledMessages) baseUpdate;

                if (scheduledDeletedMessages == null) {
                    scheduledDeletedMessages = new LongSparseArray<>();
                }
                long id = MessageObject.getPeerId(update.peer);
                ArrayList<Integer> arrayList = scheduledDeletedMessages.get(MessageObject.getPeerId(update.peer));
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    scheduledDeletedMessages.put(id, arrayList);
                }
                arrayList.addAll(update.messages);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserTyping || baseUpdate instanceof TLRPC.TL_updateChatUserTyping || baseUpdate instanceof TLRPC.TL_updateChannelUserTyping) {
                long userId;
                long chatId;
                int threadId;
                TLRPC.SendMessageAction action;
                if (baseUpdate instanceof TLRPC.TL_updateChannelUserTyping) {
                    TLRPC.TL_updateChannelUserTyping update = (TLRPC.TL_updateChannelUserTyping) baseUpdate;
                    if (update.from_id.user_id != 0) {
                        userId = update.from_id.user_id;
                    } else if (update.from_id.channel_id != 0) {
                        userId = -update.from_id.channel_id;
                    } else {
                        userId = -update.from_id.chat_id;
                    }
                    chatId = update.channel_id;
                    action = update.action;
                    threadId = update.top_msg_id;
                } else if (baseUpdate instanceof TLRPC.TL_updateUserTyping) {
                    TLRPC.TL_updateUserTyping update = (TLRPC.TL_updateUserTyping) baseUpdate;
                    userId = update.user_id;
                    action = update.action;
                    chatId = 0;
                    threadId = 0;
                    if (update.action instanceof TLRPC.TL_sendMessageEmojiInteraction) {
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.onEmojiInteractionsReceived, update.user_id, update.action));
                        continue;
                    }

//                    if (update.action instanceof TLRPC.TL_sendMessageEmojiInteraction) {
//                        if (emojiInteractions == null) {
//                            emojiInteractions = new ArrayList<>();
//                        }
//                        emojiInteractions.add(update.action);
//                    }
                } else {
                    TLRPC.TL_updateChatUserTyping update = (TLRPC.TL_updateChatUserTyping) baseUpdate;
                    chatId = update.chat_id;
                    if (update.from_id.user_id != 0) {
                        userId = update.from_id.user_id;
                    } else if (update.from_id.channel_id != 0) {
                        userId = -update.from_id.channel_id;
                    } else {
                        userId = -update.from_id.chat_id;
                    }
                    action = update.action;
                    threadId = 0;

                    if (update.action instanceof TLRPC.TL_sendMessageEmojiInteraction) {
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.onEmojiInteractionsReceived, -update.chat_id, update.action));
                        continue;
                    }

//                    if (update.action instanceof TLRPC.TL_sendMessageEmojiInteraction) {
//                        if (emojiInteractions == null) {
//                            emojiInteractions = new ArrayList<>();
//                        }
//                        emojiInteractions.add(update.action);
//                    }
                }
                long uid = -chatId;
                if (uid == 0) {
                    uid = userId;
                }
                if (action instanceof TLRPC.TL_sendMessageHistoryImportAction) {
                    if (importingActions == null) {
                        importingActions = new LongSparseIntArray();
                    }
                    TLRPC.TL_sendMessageHistoryImportAction importAction = (TLRPC.TL_sendMessageHistoryImportAction) action;
                    importingActions.put(uid, importAction.progress);
                } else if (userId != getUserConfig().getClientUserId()) {
                    if (action instanceof TLRPC.TL_speakingInGroupCallAction) {
                        if (chatId != 0) {
                            if (groupSpeakingActions == null) {
                                groupSpeakingActions = new LongSparseArray<>();
                            }
                            ArrayList<Long> uids = groupSpeakingActions.get(chatId);
                            if (uids == null) {
                                uids = new ArrayList<>();
                                groupSpeakingActions.put(chatId, uids);
                            }
                            uids.add(userId);
                        }
                    } else {
                        ConcurrentHashMap<Integer, ArrayList<PrintingUser>> threads = printingUsers.get(uid);
                        ArrayList<PrintingUser> arr = threads != null ? threads.get(threadId) : null;
                        if (action instanceof TLRPC.TL_sendMessageCancelAction) {
                            if (arr != null) {
                                for (int a = 0, size = arr.size(); a < size; a++) {
                                    PrintingUser pu = arr.get(a);
                                    if (pu.userId == userId) {
                                        arr.remove(a);
                                        printChanged = true;
                                        break;
                                    }
                                }
                                if (arr.isEmpty()) {
                                    threads.remove(threadId);
                                    if (threads.isEmpty()) {
                                        printingUsers.remove(uid);
                                    }
                                }
                            }
                        } else {
                            if (threads == null) {
                                threads = new ConcurrentHashMap<>();
                                printingUsers.put(uid, threads);
                            }
                            if (arr == null) {
                                arr = new ArrayList<>();
                                threads.put(threadId, arr);
                            }
                            boolean exist = false;
                            for (PrintingUser u : arr) {
                                if (u.userId == userId) {
                                    exist = true;
                                    u.lastTime = currentTime;
                                    if (u.action.getClass() != action.getClass()) {
                                        printChanged = true;
                                    }
                                    u.action = action;
                                    break;
                                }
                            }
                            if (!exist) {
                                PrintingUser newUser = new PrintingUser();
                                newUser.userId = userId;
                                newUser.lastTime = currentTime;
                                newUser.action = action;
                                arr.add(newUser);
                                printChanged = true;
                            }
                        }
                    }
                    if (Math.abs(getConnectionsManager().getCurrentTime() - date) < 30) {
                        onlinePrivacy.put(userId, date);
                    }
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateChatParticipants) {
                TLRPC.TL_updateChatParticipants update = (TLRPC.TL_updateChatParticipants) baseUpdate;
                interfaceUpdateMask |= UPDATE_MASK_CHAT_MEMBERS;
                if (chatInfoToUpdate == null) {
                    chatInfoToUpdate = new ArrayList<>();
                }
                chatInfoToUpdate.add(update.participants);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserStatus) {
                interfaceUpdateMask |= UPDATE_MASK_STATUS;
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserName) {
                interfaceUpdateMask |= UPDATE_MASK_NAME;
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserPhoto) {
                TLRPC.TL_updateUserPhoto update = (TLRPC.TL_updateUserPhoto) baseUpdate;
                interfaceUpdateMask |= UPDATE_MASK_AVATAR;
                getMessagesStorage().clearUserPhotos(update.user_id);
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserPhone) {
                interfaceUpdateMask |= UPDATE_MASK_PHONE;
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updatePeerSettings) {
                TLRPC.TL_updatePeerSettings update = (TLRPC.TL_updatePeerSettings) baseUpdate;
                if (contactsIds == null) {
                    contactsIds = new ArrayList<>();
                }
                if (update.peer instanceof TLRPC.TL_peerUser) {
                    TLRPC.User user = usersDict.get(update.peer.user_id);
                    if (user != null) {
                        if (user.contact) {
                            int idx = contactsIds.indexOf(-update.peer.user_id);
                            if (idx != -1) {
                                contactsIds.remove(idx);
                            }
                            if (!contactsIds.contains(update.peer.user_id)) {
                                contactsIds.add(update.peer.user_id);
                            }
                        } else {
                            int idx = contactsIds.indexOf(update.peer.user_id);
                            if (idx != -1) {
                                contactsIds.remove(idx);
                            }
                            if (!contactsIds.contains(update.peer.user_id)) {
                                contactsIds.add(-update.peer.user_id);
                            }
                        }
                    }
                }
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateNewEncryptedMessage) {
                ArrayList<TLRPC.Message> decryptedMessages = getSecretChatHelper().decryptMessage(((TLRPC.TL_updateNewEncryptedMessage) baseUpdate).message);
                if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                    int cid = ((TLRPC.TL_updateNewEncryptedMessage) baseUpdate).message.chat_id;
                    long uid = DialogObject.makeEncryptedDialogId(cid);
                    if (messages == null) {
                        messages = new LongSparseArray<>();
                    }
                    ArrayList<MessageObject> arr = messages.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        messages.put(uid, arr);
                    }
                    for (int a = 0, size = decryptedMessages.size(); a < size; a++) {
                        TLRPC.Message message = decryptedMessages.get(a);
                        ImageLoader.saveMessageThumbs(message);
                        if (messagesArr == null) {
                            messagesArr = new ArrayList<>();
                        }
                        messagesArr.add(message);
                        boolean isDialogCreated = createdDialogIds.contains(uid);
                        MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated);
                        arr.add(obj);
                        if (pushMessages == null) {
                            pushMessages = new ArrayList<>();
                        }
                        pushMessages.add(obj);
                    }
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateEncryptedChatTyping) {
                TLRPC.TL_updateEncryptedChatTyping update = (TLRPC.TL_updateEncryptedChatTyping) baseUpdate;
                TLRPC.EncryptedChat encryptedChat = getEncryptedChatDB(update.chat_id, true);
                if (encryptedChat != null) {
                    long uid = DialogObject.makeEncryptedDialogId(update.chat_id);
                    ConcurrentHashMap<Integer, ArrayList<PrintingUser>> threads = printingUsers.get(uid);
                    if (threads == null) {
                        threads = new ConcurrentHashMap<>();
                        printingUsers.put(uid, threads);
                    }
                    ArrayList<PrintingUser> arr = threads.get(0);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        threads.put(0, arr);
                    }
                    boolean exist = false;
                    for (int a = 0, size = arr.size(); a < size; a++) {
                        PrintingUser u = arr.get(a);
                        if (u.userId == encryptedChat.user_id) {
                            exist = true;
                            u.lastTime = currentTime;
                            u.action = new TLRPC.TL_sendMessageTypingAction();
                            break;
                        }
                    }
                    if (!exist) {
                        PrintingUser newUser = new PrintingUser();
                        newUser.userId = encryptedChat.user_id;
                        newUser.lastTime = currentTime;
                        newUser.action = new TLRPC.TL_sendMessageTypingAction();
                        arr.add(newUser);
                        printChanged = true;
                    }
                    if (Math.abs(getConnectionsManager().getCurrentTime() - date) < 30) {
                        onlinePrivacy.put(encryptedChat.user_id, date);
                    }
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateEncryptedMessagesRead) {
                TLRPC.TL_updateEncryptedMessagesRead update = (TLRPC.TL_updateEncryptedMessagesRead) baseUpdate;
                if (markAsReadEncrypted == null) {
                    markAsReadEncrypted = new SparseIntArray();
                }
                markAsReadEncrypted.put(update.chat_id, update.max_date);
                if (tasks == null) {
                    tasks = new ArrayList<>();
                }
                tasks.add((TLRPC.TL_updateEncryptedMessagesRead) baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatParticipantAdd) {
                TLRPC.TL_updateChatParticipantAdd update = (TLRPC.TL_updateChatParticipantAdd) baseUpdate;
                getMessagesStorage().updateChatInfo(update.chat_id, update.user_id, 0, update.inviter_id, update.version);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatParticipantDelete) {
                TLRPC.TL_updateChatParticipantDelete update = (TLRPC.TL_updateChatParticipantDelete) baseUpdate;
                getMessagesStorage().updateChatInfo(update.chat_id, update.user_id, 1, 0, update.version);
            } else if (baseUpdate instanceof TLRPC.TL_updateDcOptions || baseUpdate instanceof TLRPC.TL_updateConfig) {
                getConnectionsManager().updateDcSettings();
            } else if (baseUpdate instanceof TLRPC.TL_updateEncryption) {
                getSecretChatHelper().processUpdateEncryption((TLRPC.TL_updateEncryption) baseUpdate, usersDict);
            } else if (baseUpdate instanceof TLRPC.TL_updatePeerBlocked) {
                TLRPC.TL_updatePeerBlocked finalUpdate = (TLRPC.TL_updatePeerBlocked) baseUpdate;
                getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    long id = MessageObject.getPeerId(finalUpdate.peer_id);
                    if (finalUpdate.blocked) {
                        if (blockePeers.indexOfKey(id) < 0) {
                            blockePeers.put(id, 1);
                        }
                    } else {
                        blockePeers.delete(id);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                }));
            } else if (baseUpdate instanceof TLRPC.TL_updateNotifySettings) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateServiceNotification) {
                TLRPC.TL_updateServiceNotification update = (TLRPC.TL_updateServiceNotification) baseUpdate;
                if (update.popup && update.message != null && update.message.length() > 0) {
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.needShowAlert, 2, update.message, update.type));
                }
                if ((update.flags & 2) != 0) {
                    TLRPC.TL_message newMessage = new TLRPC.TL_message();
                    newMessage.local_id = newMessage.id = getUserConfig().getNewMessageId();
                    getUserConfig().saveConfig(false);
                    newMessage.unread = true;
                    newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    if (update.inbox_date != 0) {
                        newMessage.date = update.inbox_date;
                    } else {
                        newMessage.date = (int) (System.currentTimeMillis() / 1000);
                    }
                    newMessage.from_id = new TLRPC.TL_peerUser();
                    newMessage.from_id.user_id = 777000;
                    newMessage.peer_id = new TLRPC.TL_peerUser();
                    newMessage.peer_id.user_id = getUserConfig().getClientUserId();
                    newMessage.dialog_id = 777000;
                    if (update.media != null) {
                        newMessage.media = update.media;
                        newMessage.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                    }
                    newMessage.message = update.message;
                    if (update.entities != null) {
                        newMessage.entities = update.entities;
                        newMessage.flags |= 128;
                    }

                    if (messagesArr == null) {
                        messagesArr = new ArrayList<>();
                    }
                    messagesArr.add(newMessage);
                    boolean isDialogCreated = createdDialogIds.contains(newMessage.dialog_id);
                    MessageObject obj = new MessageObject(currentAccount, newMessage, usersDict, chatsDict, isDialogCreated, isDialogCreated);
                    if (messages == null) {
                        messages = new LongSparseArray<>();
                    }
                    ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        messages.put(newMessage.dialog_id, arr);
                    }
                    arr.add(obj);
                    if (pushMessages == null) {
                        pushMessages = new ArrayList<>();
                    }
                    pushMessages.add(obj);
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateDialogPinned) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updatePinnedDialogs) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateFolderPeers) {
                TLRPC.TL_updateFolderPeers update = (TLRPC.TL_updateFolderPeers) baseUpdate;
                if (folderUpdates == null) {
                    folderUpdates = new ArrayList<>();
                }
                folderUpdates.add(update);
            } else if (baseUpdate instanceof TLRPC.TL_updatePrivacy) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateWebPage) {
                TLRPC.TL_updateWebPage update = (TLRPC.TL_updateWebPage) baseUpdate;
                if (webPages == null) {
                    webPages = new LongSparseArray<>();
                }
                webPages.put(update.webpage.id, update.webpage);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelWebPage) {
                TLRPC.TL_updateChannelWebPage update = (TLRPC.TL_updateChannelWebPage) baseUpdate;
                if (webPages == null) {
                    webPages = new LongSparseArray<>();
                }
                webPages.put(update.webpage.id, update.webpage);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelTooLong) {
                TLRPC.TL_updateChannelTooLong update = (TLRPC.TL_updateChannelTooLong) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                int channelPts = channelsPts.get(update.channel_id, 0);
                if (channelPts == 0) {
                    channelPts = getMessagesStorage().getChannelPtsSync(update.channel_id);
                    if (channelPts == 0) {
                        TLRPC.Chat chat = chatsDict.get(update.channel_id);
                        if (chat == null || chat.min) {
                            chat = getChat(update.channel_id);
                        }
                        if (chat == null || chat.min) {
                            chat = getMessagesStorage().getChatSync(update.channel_id);
                            putChat(chat, true);
                        }
                        if (chat != null && !chat.min) {
                            loadUnknownChannel(chat, 0);
                        }
                    } else {
                        channelsPts.put(update.channel_id, channelPts);
                    }
                }
                if (channelPts != 0) {
                    if ((update.flags & 1) != 0) {
                        if (update.pts > channelPts) {
                            getChannelDifference(update.channel_id);
                        }
                    } else {
                        getChannelDifference(update.channel_id);
                    }
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateReadChannelInbox) {
                TLRPC.TL_updateReadChannelInbox update = (TLRPC.TL_updateReadChannelInbox) baseUpdate;
                if (markAsReadMessagesInbox == null) {
                    markAsReadMessagesInbox = new LongSparseIntArray();
                }
                long dialogId = -update.channel_id;
                markAsReadMessagesInbox.put(dialogId, update.max_id);
                Integer value = dialogs_read_inbox_max.get(dialogId);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(false, dialogId);
                }
                dialogs_read_inbox_max.put(dialogId, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateReadChannelOutbox) {
                TLRPC.TL_updateReadChannelOutbox update = (TLRPC.TL_updateReadChannelOutbox) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                if (markAsReadMessagesOutbox == null) {
                    markAsReadMessagesOutbox = new LongSparseIntArray();
                }
                long dialogId = -update.channel_id;
                markAsReadMessagesOutbox.put(dialogId, update.max_id);
                Integer value = dialogs_read_outbox_max.get(dialogId);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(true, dialogId);
                }
                dialogs_read_outbox_max.put(dialogId, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateDeleteChannelMessages) {
                TLRPC.TL_updateDeleteChannelMessages update = (TLRPC.TL_updateDeleteChannelMessages) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                if (deletedMessages == null) {
                    deletedMessages = new LongSparseArray<>();
                }
                long dialogId = -update.channel_id;
                ArrayList<Integer> arrayList = deletedMessages.get(dialogId);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(dialogId, arrayList);
                }
                arrayList.addAll(update.messages);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannel) {
                if (BuildVars.LOGS_ENABLED) {
                    TLRPC.TL_updateChannel update = (TLRPC.TL_updateChannel) baseUpdate;
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateChat) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelMessageViews) {
                TLRPC.TL_updateChannelMessageViews update = (TLRPC.TL_updateChannelMessageViews) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                if (channelViews == null) {
                    channelViews = new LongSparseArray<>();
                }
                long dialogId = -update.channel_id;
                SparseIntArray array = channelViews.get(dialogId);
                if (array == null) {
                    array = new SparseIntArray();
                    channelViews.put(dialogId, array);
                }
                array.put(update.id, update.views);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelMessageForwards) {
                TLRPC.TL_updateChannelMessageForwards update = (TLRPC.TL_updateChannelMessageForwards) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                if (channelForwards == null) {
                    channelForwards = new LongSparseArray<>();
                }
                long dialogId = -update.channel_id;
                SparseIntArray array = channelForwards.get(dialogId);
                if (array == null) {
                    array = new SparseIntArray();
                    channelForwards.put(dialogId, array);
                }
                array.put(update.id, update.forwards);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatParticipantAdmin) {
                TLRPC.TL_updateChatParticipantAdmin update = (TLRPC.TL_updateChatParticipantAdmin) baseUpdate;
                getMessagesStorage().updateChatInfo(update.chat_id, update.user_id, 2, update.is_admin ? 1 : 0, update.version);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatDefaultBannedRights) {
                TLRPC.TL_updateChatDefaultBannedRights update = (TLRPC.TL_updateChatDefaultBannedRights) baseUpdate;
                long chatId;
                if (update.peer.channel_id != 0) {
                    chatId = update.peer.channel_id;
                } else {
                    chatId = update.peer.chat_id;
                }
                getMessagesStorage().updateChatDefaultBannedRights(chatId, update.default_banned_rights, update.version);
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateStickerSets) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateStickerSetsOrder) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateNewStickerSet) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateDraftMessage) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateSavedGifs) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateEditChannelMessage || baseUpdate instanceof TLRPC.TL_updateEditMessage) {
                TLRPC.Message message;
                if (baseUpdate instanceof TLRPC.TL_updateEditChannelMessage) {
                    message = ((TLRPC.TL_updateEditChannelMessage) baseUpdate).message;
                    TLRPC.Chat chat = chatsDict.get(message.peer_id.channel_id);
                    if (chat == null) {
                        chat = getChat(message.peer_id.channel_id);
                    }
                    if (chat == null) {
                        chat = getMessagesStorage().getChatSync(message.peer_id.channel_id);
                        putChat(chat, true);
                    }
                } else {
                    message = ((TLRPC.TL_updateEditMessage) baseUpdate).message;
                    if (message.dialog_id == clientUserId) {
                        message.unread = false;
                        message.media_unread = false;
                        message.out = true;
                    }
                }
                if (!message.out && message.from_id instanceof TLRPC.TL_peerUser && message.from_id.user_id == clientUserId) {
                    message.out = true;
                }
                if (!fromGetDifference) {
                    for (int a = 0, count = message.entities.size(); a < count; a++) {
                        TLRPC.MessageEntity entity = message.entities.get(a);
                        if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                            long userId = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
                            TLRPC.User user = usersDict.get(userId);
                            if (user == null || user.min) {
                                user = getUser(userId);
                            }
                            if (user == null || user.min) {
                                user = getMessagesStorage().getUserSync(userId);
                                if (user != null && user.min) {
                                    user = null;
                                }
                                putUser(user, true);
                            }
                            if (user == null) {
                                return false;
                            }
                        }
                    }
                }

                MessageObject.getDialogId(message);

                ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                Integer value = read_max.get(message.dialog_id);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(message.out, message.dialog_id);
                    read_max.put(message.dialog_id, value);
                }
                message.unread = value < message.id;
                if (message.dialog_id == clientUserId) {
                    message.out = true;
                    message.unread = false;
                    message.media_unread = false;
                }
                if (message.out && message.message == null) {
                    message.message = "";
                    message.attachPath = "";
                }

                ImageLoader.saveMessageThumbs(message);

                boolean isDialogCreated = createdDialogIds.contains(message.dialog_id);
                MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, isDialogCreated, isDialogCreated);

                LongSparseArray<ArrayList<MessageObject>> array;
                if (editingMessages == null) {
                    editingMessages = new LongSparseArray<>();
                }
                array = editingMessages;
                ArrayList<MessageObject> arr = array.get(message.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<>();
                    array.put(message.dialog_id, arr);
                }
                arr.add(obj);
            } else if (baseUpdate instanceof TLRPC.TL_updatePinnedChannelMessages) {
                TLRPC.TL_updatePinnedChannelMessages update = (TLRPC.TL_updatePinnedChannelMessages) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                getMessagesStorage().updatePinnedMessages(-update.channel_id, update.messages, update.pinned, -1, 0, false, null);
            } else if (baseUpdate instanceof TLRPC.TL_updatePinnedMessages) {
                TLRPC.TL_updatePinnedMessages update = (TLRPC.TL_updatePinnedMessages) baseUpdate;
                getMessagesStorage().updatePinnedMessages(MessageObject.getPeerId(update.peer), update.messages, update.pinned, -1, 0, false, null);
            } else if (baseUpdate instanceof TLRPC.TL_updateReadFeaturedStickers) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updatePhoneCall) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateGroupCallParticipants) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateGroupCall) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateGroupCallConnection) {

            } else if (baseUpdate instanceof TLRPC.TL_updateBotCommands) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updatePhoneCallSignalingData) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateLangPack) {
                TLRPC.TL_updateLangPack update = (TLRPC.TL_updateLangPack) baseUpdate;
                AndroidUtilities.runOnUIThread(() -> LocaleController.getInstance().saveRemoteLocaleStringsForCurrentLocale(update.difference, currentAccount));
            } else if (baseUpdate instanceof TLRPC.TL_updateLangPackTooLong) {
                TLRPC.TL_updateLangPackTooLong update = (TLRPC.TL_updateLangPackTooLong) baseUpdate;
                LocaleController.getInstance().reloadCurrentRemoteLocale(currentAccount, update.lang_code, false);
            } else if (baseUpdate instanceof TLRPC.TL_updateFavedStickers) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateContactsReset) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelAvailableMessages) {
                TLRPC.TL_updateChannelAvailableMessages update = (TLRPC.TL_updateChannelAvailableMessages) baseUpdate;
                if (clearHistoryMessages == null) {
                    clearHistoryMessages = new LongSparseIntArray();
                }
                long dialogId = -update.channel_id;
                int currentValue = clearHistoryMessages.get(dialogId, 0);
                if (currentValue == 0 || currentValue < update.available_min_id) {
                    clearHistoryMessages.put(dialogId, update.available_min_id);
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateDialogUnreadMark) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateMessagePoll) {
                TLRPC.TL_updateMessagePoll update = (TLRPC.TL_updateMessagePoll) baseUpdate;
                long time = getSendMessagesHelper().getVoteSendTime(update.poll_id);
                if (Math.abs(SystemClock.elapsedRealtime() - time) < 600) {
                    continue;
                }
                getMessagesStorage().updateMessagePollResults(update.poll_id, update.poll, update.results);
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateMessageReactions) {
                TLRPC.TL_updateMessageReactions update = (TLRPC.TL_updateMessageReactions) baseUpdate;
                long dialogId = MessageObject.getPeerId(update.peer);
                getMessagesStorage().updateMessageReactions(dialogId, update.msg_id, update.reactions);
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updatePeerLocated) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateTheme) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateGeoLiveViewed) {
                getLocationController().setNewLocationEndWatchTime();
            } else if (baseUpdate instanceof TLRPC.TL_updateDialogFilter) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateDialogFilterOrder) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateDialogFilters) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateReadChannelDiscussionInbox) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateReadChannelDiscussionOutbox) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updatePeerHistoryTTL) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updatePendingJoinRequests) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            }
        }
        if (messages != null) {
            for (int a = 0, size = messages.size(); a < size; a++) {
                long key = messages.keyAt(a);
                ArrayList<MessageObject> value = messages.valueAt(a);
                if (updatePrintingUsersWithNewMessages(key, value)) {
                    printChanged = true;
                }
            }
        }

        if (printChanged) {
            updatePrintingStrings();
        }

        int interfaceUpdateMaskFinal = interfaceUpdateMask;
        boolean printChangedArg = printChanged;

        if (contactsIds != null) {
            getContactsController().processContactsUpdates(contactsIds, usersDict);
        }

        if (pushMessages != null) {
            ArrayList<MessageObject> pushMessagesFinal = pushMessages;
            getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(pushMessagesFinal, true, false, null)));
        }

        if (scheduledMessagesArr != null) {
            getMessagesStorage().putMessages(scheduledMessagesArr, true, true, false, getDownloadController().getAutodownloadMask(), true);
        }

        if (messagesArr != null) {
            getStatsController().incrementReceivedItemsCount(ApplicationLoader.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, messagesArr.size());
            getMessagesStorage().putMessages(messagesArr, true, true, false, getDownloadController().getAutodownloadMask(), false);
        }
        if (editingMessages != null) {
            for (int b = 0, size = editingMessages.size(); b < size; b++) {
                TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                ArrayList<MessageObject> messageObjects = editingMessages.valueAt(b);
                for (int a = 0, size2 = messageObjects.size(); a < size2; a++) {
                    messagesRes.messages.add(messageObjects.get(a).messageOwner);
                }
                getMessagesStorage().putMessages(messagesRes, editingMessages.keyAt(b), -2, 0, false, false);
            }
            LongSparseArray<ArrayList<MessageObject>> editingMessagesFinal = editingMessages;
            getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().processEditedMessages(editingMessagesFinal)));
        }

        if (channelViews != null || channelForwards != null || channelReplies != null) {
            getMessagesStorage().putChannelViews(channelViews, channelForwards, channelReplies, true);
        }
        if (folderUpdates != null) {
            for (int a = 0, size = folderUpdates.size(); a < size; a++) {
                getMessagesStorage().setDialogsFolderId(folderUpdates.get(a).folder_peers, null, 0, 0);
            }
        }

        LongSparseArray<ArrayList<MessageObject>> editingMessagesFinal = editingMessages;
        LongSparseArray<SparseIntArray> channelViewsFinal = channelViews;
        LongSparseArray<SparseIntArray> channelForwardsFinal = channelForwards;
        LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelRepliesFinal = channelReplies;
        LongSparseArray<TLRPC.WebPage> webPagesFinal = webPages;
        LongSparseArray<ArrayList<MessageObject>> messagesFinal = messages;
        LongSparseArray<ArrayList<MessageObject>> scheduledMessagesFinal = scheduledMessages;
        ArrayList<TLRPC.ChatParticipants> chatInfoToUpdateFinal = chatInfoToUpdate;
        ArrayList<Long> contactsIdsFinal = contactsIds;
        ArrayList<TLRPC.Update> updatesOnMainThreadFinal = updatesOnMainThread;
        ArrayList<ImageLoader.MessageThumb> updateMessageThumbs = messageThumbs;
        ArrayList<TLRPC.TL_updateFolderPeers> folderUpdatesFinal = folderUpdates;
        LongSparseArray<ArrayList<Long>> groupSpeakingActionsFinal = groupSpeakingActions;
        LongSparseIntArray importingActionsFinal = importingActions;

        AndroidUtilities.runOnUIThread(() -> {
            int updateMask = interfaceUpdateMaskFinal;
            boolean forceDialogsUpdate = false;
            int updateDialogFiltersFlags = 0;

            if (updatesOnMainThreadFinal != null) {
                ArrayList<TLRPC.User> dbUsers = new ArrayList<>();
                ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<>();
                SharedPreferences.Editor editor = null;
                for (int a = 0, size = updatesOnMainThreadFinal.size(); a < size; a++) {
                    TLRPC.Update baseUpdate = updatesOnMainThreadFinal.get(a);
                    if (baseUpdate instanceof TLRPC.TL_updatePrivacy) {
                        TLRPC.TL_updatePrivacy update = (TLRPC.TL_updatePrivacy) baseUpdate;
                        if (update.key instanceof TLRPC.TL_privacyKeyStatusTimestamp) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyChatInvite) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_INVITE);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyPhoneCall) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_CALLS);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyPhoneP2P) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_P2P);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyProfilePhoto) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_PHOTO);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyForwards) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_FORWARDS);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyPhoneNumber) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_PHONE);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyAddedByPhone) {
                            getContactsController().setPrivacyRules(update.rules, ContactsController.PRIVACY_RULES_TYPE_ADDED_BY_PHONE);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserStatus) {
                        TLRPC.TL_updateUserStatus update = (TLRPC.TL_updateUserStatus) baseUpdate;
                        TLRPC.User currentUser = getUser(update.user_id);

                        if (update.status instanceof TLRPC.TL_userStatusRecently) {
                            update.status.expires = -100;
                        } else if (update.status instanceof TLRPC.TL_userStatusLastWeek) {
                            update.status.expires = -101;
                        } else if (update.status instanceof TLRPC.TL_userStatusLastMonth) {
                            update.status.expires = -102;
                        }
                        if (currentUser != null) {
                            currentUser.id = update.user_id;
                            currentUser.status = update.status;
                        }
                        TLRPC.User toDbUser = new TLRPC.TL_user();
                        toDbUser.id = update.user_id;
                        toDbUser.status = update.status;
                        dbUsersStatus.add(toDbUser);
                        if (update.user_id == getUserConfig().getClientUserId()) {
                            getNotificationsController().setLastOnlineFromOtherDevice(update.status.expires);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserName) {
                        TLRPC.TL_updateUserName update = (TLRPC.TL_updateUserName) baseUpdate;
                        TLRPC.User currentUser = getUser(update.user_id);
                        if (currentUser != null) {
                            if (!UserObject.isContact(currentUser)) {
                                currentUser.first_name = update.first_name;
                                currentUser.last_name = update.last_name;
                            }
                            if (!TextUtils.isEmpty(currentUser.username)) {
                                objectsByUsernames.remove(currentUser.username);
                            }
                            if (TextUtils.isEmpty(update.username)) {
                                objectsByUsernames.put(update.username, currentUser);
                            }
                            currentUser.username = update.username;
                        }
                        TLRPC.User toDbUser = new TLRPC.TL_user();
                        toDbUser.id = update.user_id;
                        toDbUser.first_name = update.first_name;
                        toDbUser.last_name = update.last_name;
                        toDbUser.username = update.username;
                        dbUsers.add(toDbUser);
                    } else if (baseUpdate instanceof TLRPC.TL_updateDialogPinned) {
                        TLRPC.TL_updateDialogPinned update = (TLRPC.TL_updateDialogPinned) baseUpdate;
                        long did;
                        if (update.peer instanceof TLRPC.TL_dialogPeer) {
                            TLRPC.TL_dialogPeer dialogPeer = (TLRPC.TL_dialogPeer) update.peer;
                            did = DialogObject.getPeerDialogId(dialogPeer.peer);
                        } else {
                            did = 0;
                        }
                        if (!pinDialog(did, update.pinned, null, -1)) {
                            getUserConfig().setPinnedDialogsLoaded(update.folder_id, false);
                            getUserConfig().saveConfig(false);
                            loadPinnedDialogs(update.folder_id, did, null);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updatePinnedDialogs) {
                        TLRPC.TL_updatePinnedDialogs update = (TLRPC.TL_updatePinnedDialogs) baseUpdate;
                        getUserConfig().setPinnedDialogsLoaded(update.folder_id, false);
                        getUserConfig().saveConfig(false);
                        ArrayList<Long> order;
                        if ((update.flags & 1) != 0) {
                            order = new ArrayList<>();
                            ArrayList<TLRPC.DialogPeer> peers = update.order;
                            for (int b = 0, size2 = peers.size(); b < size2; b++) {
                                long did;
                                TLRPC.DialogPeer dialogPeer = peers.get(b);
                                if (dialogPeer instanceof TLRPC.TL_dialogPeer) {
                                    TLRPC.Peer peer = ((TLRPC.TL_dialogPeer) dialogPeer).peer;
                                    if (peer.user_id != 0) {
                                        did = peer.user_id;
                                    } else if (peer.chat_id != 0) {
                                        did = -peer.chat_id;
                                    } else {
                                        did = -peer.channel_id;
                                    }
                                } else {
                                    did = 0;
                                }
                                order.add(did);
                            }
                        } else {
                            order = null;
                        }
                        loadPinnedDialogs(update.folder_id, 0, order);
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserPhoto) {
                        TLRPC.TL_updateUserPhoto update = (TLRPC.TL_updateUserPhoto) baseUpdate;
                        TLRPC.User currentUser = getUser(update.user_id);
                        if (currentUser != null) {
                            currentUser.photo = update.photo;
                        }
                        TLRPC.User toDbUser = new TLRPC.TL_user();
                        toDbUser.id = update.user_id;
                        toDbUser.photo = update.photo;
                        dbUsers.add(toDbUser);
                        if (UserObject.isUserSelf(currentUser)) {
                            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserPhone) {
                        TLRPC.TL_updateUserPhone update = (TLRPC.TL_updateUserPhone) baseUpdate;
                        TLRPC.User currentUser = getUser(update.user_id);
                        if (currentUser != null) {
                            currentUser.phone = update.phone;
                            Utilities.phoneBookQueue.postRunnable(() -> getContactsController().addContactToPhoneBook(currentUser, true));
                            if (UserObject.isUserSelf(currentUser)) {
                                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                            }
                        }
                        TLRPC.User toDbUser = new TLRPC.TL_user();
                        toDbUser.id = update.user_id;
                        toDbUser.phone = update.phone;
                        dbUsers.add(toDbUser);
                    } else if (baseUpdate instanceof TLRPC.TL_updateNotifySettings) {
                        TLRPC.TL_updateNotifySettings update = (TLRPC.TL_updateNotifySettings) baseUpdate;
                        if (update.notify_settings instanceof TLRPC.TL_peerNotifySettings) {
                            updateDialogFiltersFlags |= DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                            if (editor == null) {
                                editor = notificationsPreferences.edit();
                            }
                            int currentTime1 = getConnectionsManager().getCurrentTime();
                            if (update.peer instanceof TLRPC.TL_notifyPeer) {
                                TLRPC.TL_notifyPeer notifyPeer = (TLRPC.TL_notifyPeer) update.peer;
                                long dialogId;
                                if (notifyPeer.peer.user_id != 0) {
                                    dialogId = notifyPeer.peer.user_id;
                                } else if (notifyPeer.peer.chat_id != 0) {
                                    dialogId = -notifyPeer.peer.chat_id;
                                } else {
                                    dialogId = -notifyPeer.peer.channel_id;
                                }
                                TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
                                if (dialog != null) {
                                    dialog.notify_settings = update.notify_settings;
                                }
                                if ((update.notify_settings.flags & 2) != 0) {
                                    editor.putBoolean("silent_" + dialogId, update.notify_settings.silent);
                                } else {
                                    editor.remove("silent_" + dialogId);
                                }
                                if ((update.notify_settings.flags & 4) != 0) {
                                    if (update.notify_settings.mute_until > currentTime1) {
                                        int until = 0;
                                        if (update.notify_settings.mute_until > currentTime1 + 60 * 60 * 24 * 365) {
                                            editor.putInt("notify2_" + dialogId, 2);
                                            if (dialog != null) {
                                                update.notify_settings.mute_until = Integer.MAX_VALUE;
                                            }
                                        } else {
                                            until = update.notify_settings.mute_until;
                                            editor.putInt("notify2_" + dialogId, 3);
                                            editor.putInt("notifyuntil_" + dialogId, update.notify_settings.mute_until);
                                            if (dialog != null) {
                                                update.notify_settings.mute_until = until;
                                            }
                                        }
                                        getMessagesStorage().setDialogFlags(dialogId, ((long) until << 32) | 1);
                                        getNotificationsController().removeNotificationsForDialog(dialogId);
                                    } else {
                                        if (dialog != null) {
                                            update.notify_settings.mute_until = 0;
                                        }
                                        editor.putInt("notify2_" + dialogId, 0);
                                        getMessagesStorage().setDialogFlags(dialogId, 0);
                                    }
                                } else {
                                    if (dialog != null) {
                                        update.notify_settings.mute_until = 0;
                                    }
                                    editor.remove("notify2_" + dialogId);
                                    getMessagesStorage().setDialogFlags(dialogId, 0);
                                }
                            } else if (update.peer instanceof TLRPC.TL_notifyChats) {
                                if ((update.notify_settings.flags & 1) != 0) {
                                    editor.putBoolean("EnablePreviewGroup", update.notify_settings.show_previews);
                                }
                                if ((update.notify_settings.flags & 2) != 0) {
                                    /*if (update.notify_settings.silent) {
                                        editor.putString("GroupSoundPath", "NoSound");
                                    } else {
                                        editor.remove("GroupSoundPath");
                                    }*/
                                }
                                if ((update.notify_settings.flags & 4) != 0) {
                                    if (notificationsPreferences.getInt("EnableGroup2", 0) != update.notify_settings.mute_until) {
                                        editor.putInt("EnableGroup2", update.notify_settings.mute_until);
                                        editor.putBoolean("overwrite_group", true);
                                        AndroidUtilities.runOnUIThread(() -> getNotificationsController().deleteNotificationChannelGlobal(NotificationsController.TYPE_GROUP));
                                    }
                                }
                            } else if (update.peer instanceof TLRPC.TL_notifyUsers) {
                                if ((update.notify_settings.flags & 1) != 0) {
                                    editor.putBoolean("EnablePreviewAll", update.notify_settings.show_previews);
                                }
                                if ((update.notify_settings.flags & 2) != 0) {
                                    /*if (update.notify_settings.silent) {
                                        editor.putString("GlobalSoundPath", "NoSound");
                                    } else {
                                        editor.remove("GlobalSoundPath");
                                    }*/
                                }
                                if ((update.notify_settings.flags & 4) != 0) {
                                    if (notificationsPreferences.getInt("EnableAll2", 0) != update.notify_settings.mute_until) {
                                        editor.putInt("EnableAll2", update.notify_settings.mute_until);
                                        editor.putBoolean("overwrite_private", true);
                                        AndroidUtilities.runOnUIThread(() -> getNotificationsController().deleteNotificationChannelGlobal(NotificationsController.TYPE_PRIVATE));
                                    }
                                }
                            } else if (update.peer instanceof TLRPC.TL_notifyBroadcasts) {
                                if ((update.notify_settings.flags & 1) != 0) {
                                    editor.putBoolean("EnablePreviewChannel", update.notify_settings.show_previews);
                                }
                                if ((update.notify_settings.flags & 2) != 0) {
                                    /*if (update.notify_settings.silent) {
                                        editor.putString("ChannelSoundPath", "NoSound");
                                    } else {
                                        editor.remove("ChannelSoundPath");
                                    }*/
                                }
                                if ((update.notify_settings.flags & 4) != 0) {
                                    if (notificationsPreferences.getInt("EnableChannel2", 0) != update.notify_settings.mute_until) {
                                        editor.putInt("EnableChannel2", update.notify_settings.mute_until);
                                        editor.putBoolean("overwrite_channel", true);
                                        AndroidUtilities.runOnUIThread(() -> getNotificationsController().deleteNotificationChannelGlobal(NotificationsController.TYPE_CHANNEL));
                                    }
                                }
                            }
                            getMessagesStorage().updateMutedDialogsFiltersCounters();
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateChannel) {
                        TLRPC.TL_updateChannel update = (TLRPC.TL_updateChannel) baseUpdate;
                        TLRPC.Dialog dialog = dialogs_dict.get(-update.channel_id);
                        TLRPC.Chat chat = getChat(update.channel_id);
                        if (chat != null) {
                            if (dialog == null && chat instanceof TLRPC.TL_channel && !chat.left) {
                                Utilities.stageQueue.postRunnable(() -> getChannelDifference(update.channel_id, 1, 0, null));
                            } else if (ChatObject.isNotInChat(chat) && dialog != null && (promoDialog == null || promoDialog.id != dialog.id)) {
                                deleteDialog(dialog.id, 0);
                            }
                            if (chat instanceof TLRPC.TL_channelForbidden || chat.kicked) {
                                ChatObject.Call call = getGroupCall(chat.id, false);
                                if (call != null) {
                                    TLRPC.TL_updateGroupCall updateGroupCall = new TLRPC.TL_updateGroupCall();
                                    updateGroupCall.chat_id = chat.id;
                                    updateGroupCall.call = new TLRPC.TL_groupCallDiscarded();
                                    updateGroupCall.call.id = call.call.id;
                                    updateGroupCall.call.access_hash = call.call.access_hash;
                                    call.processGroupCallUpdate(updateGroupCall);
                                    if (VoIPService.getSharedInstance() != null) {
                                        VoIPService.getSharedInstance().onGroupCallUpdated(updateGroupCall.call);
                                    }
                                }
                            }
                        }
                        updateMask |= UPDATE_MASK_CHAT;
                        loadFullChat(update.channel_id, 0, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateChat) {
                        TLRPC.TL_updateChat update = (TLRPC.TL_updateChat) baseUpdate;
                        TLRPC.Chat chat = getChat(update.chat_id);
                        if (chat != null && (chat instanceof TLRPC.TL_chatForbidden || chat.kicked)) {
                            ChatObject.Call call = getGroupCall(chat.id, false);
                            if (call != null) {
                                TLRPC.TL_updateGroupCall updateGroupCall = new TLRPC.TL_updateGroupCall();
                                updateGroupCall.chat_id = chat.id;
                                updateGroupCall.call = new TLRPC.TL_groupCallDiscarded();
                                updateGroupCall.call.id = call.call.id;
                                updateGroupCall.call.access_hash = call.call.access_hash;
                                call.processGroupCallUpdate(updateGroupCall);
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().onGroupCallUpdated(updateGroupCall.call);
                                }
                            }
                            TLRPC.Dialog dialog = dialogs_dict.get(-chat.id);
                            if (dialog != null) {
                                deleteDialog(dialog.id, 0);
                            }
                        }
                        updateMask |= UPDATE_MASK_CHAT;
                        loadFullChat(update.chat_id, 0, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateChatDefaultBannedRights) {
                        TLRPC.TL_updateChatDefaultBannedRights update = (TLRPC.TL_updateChatDefaultBannedRights) baseUpdate;
                        long chatId;
                        if (update.peer.channel_id != 0) {
                            chatId = update.peer.channel_id;
                        } else {
                            chatId = update.peer.chat_id;
                        }
                        TLRPC.Chat chat = getChat(chatId);
                        if (chat != null) {
                            chat.default_banned_rights = update.default_banned_rights;
                            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.channelRightsUpdated, chat));
                        }
                    }  else if (baseUpdate instanceof TLRPC.TL_updateBotCommands) {
                        TLRPC.TL_updateBotCommands update = (TLRPC.TL_updateBotCommands) baseUpdate;
                        getMediaDataController().updateBotInfo(MessageObject.getPeerId(update.peer), update);
                    } else if (baseUpdate instanceof TLRPC.TL_updateStickerSets) {
                        TLRPC.TL_updateStickerSets update = (TLRPC.TL_updateStickerSets) baseUpdate;
                        getMediaDataController().loadStickers(MediaDataController.TYPE_IMAGE, false, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateStickerSetsOrder) {
                        TLRPC.TL_updateStickerSetsOrder update = (TLRPC.TL_updateStickerSetsOrder) baseUpdate;
                        getMediaDataController().reorderStickers(update.masks ? MediaDataController.TYPE_MASK : MediaDataController.TYPE_IMAGE, ((TLRPC.TL_updateStickerSetsOrder) baseUpdate).order);
                    } else if (baseUpdate instanceof TLRPC.TL_updateFavedStickers) {
                        getMediaDataController().loadRecents(MediaDataController.TYPE_FAVE, false, false, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateContactsReset) {
                        getContactsController().forceImportContacts();
                    } else if (baseUpdate instanceof TLRPC.TL_updateNewStickerSet) {
                        TLRPC.TL_updateNewStickerSet update = (TLRPC.TL_updateNewStickerSet) baseUpdate;
                        getMediaDataController().addNewStickerSet(update.stickerset);
                    } else if (baseUpdate instanceof TLRPC.TL_updateSavedGifs) {
                        SharedPreferences.Editor editor2 = emojiPreferences.edit();
                        editor2.putLong("lastGifLoadTime", 0).commit();
                    } else if (baseUpdate instanceof TLRPC.TL_updateRecentStickers) {
                        SharedPreferences.Editor editor2 = emojiPreferences.edit();
                        editor2.putLong("lastStickersLoadTime", 0).commit();
                    } else if (baseUpdate instanceof TLRPC.TL_updateDraftMessage) {
                        TLRPC.TL_updateDraftMessage update = (TLRPC.TL_updateDraftMessage) baseUpdate;
                        forceDialogsUpdate = true;
                        long did;
                        TLRPC.Peer peer = ((TLRPC.TL_updateDraftMessage) baseUpdate).peer;
                        if (peer.user_id != 0) {
                            did = peer.user_id;
                        } else if (peer.channel_id != 0) {
                            did = -peer.channel_id;
                        } else {
                            did = -peer.chat_id;
                        }
                        getMediaDataController().saveDraft(did, 0, update.draft, null, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateReadFeaturedStickers) {
                        getMediaDataController().markFaturedStickersAsRead(false);
                    } else if (baseUpdate instanceof TLRPC.TL_updatePhoneCallSignalingData) {
                        TLRPC.TL_updatePhoneCallSignalingData data = (TLRPC.TL_updatePhoneCallSignalingData) baseUpdate;
                        VoIPService svc = VoIPService.getSharedInstance();
                        if (svc != null) {
                            svc.onSignalingData(data);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateGroupCallParticipants) {
                        TLRPC.TL_updateGroupCallParticipants update = (TLRPC.TL_updateGroupCallParticipants) baseUpdate;
                        ChatObject.Call call = groupCalls.get(update.call.id);
                        if (call != null) {
                            call.processParticipantsUpdate(update, false);
                        }
                        if (VoIPService.getSharedInstance() != null) {
                            VoIPService.getSharedInstance().onGroupCallParticipantsUpdate(update);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateGroupCall) {
                        TLRPC.TL_updateGroupCall update = (TLRPC.TL_updateGroupCall) baseUpdate;
                        ChatObject.Call call = groupCalls.get(update.call.id);
                        if (call != null) {
                            call.processGroupCallUpdate(update);
                            TLRPC.Chat chat = getChat(call.chatId);
                            if (chat != null) {
                                chat.call_active = update.call instanceof TLRPC.TL_groupCall;
                            }
                        } else {
                            TLRPC.ChatFull chatFull = getChatFull(update.chat_id);
                            if (chatFull != null && (chatFull.call == null || chatFull.call != null && chatFull.call.id != update.call.id)) {
                                loadFullChat(update.chat_id, 0, true);
                            }
                        }
                        if (VoIPService.getSharedInstance() != null) {
                            VoIPService.getSharedInstance().onGroupCallUpdated(update.call);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updatePhoneCall) {
                        TLRPC.TL_updatePhoneCall upd = (TLRPC.TL_updatePhoneCall) baseUpdate;
                        TLRPC.PhoneCall call = upd.phone_call;
                        VoIPService svc = VoIPService.getSharedInstance();
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("Received call in update: " + call);
                            FileLog.d("call id " + call.id);
                        }
                        if (call instanceof TLRPC.TL_phoneCallRequested) {
                            if (call.date + callRingTimeout / 1000 < getConnectionsManager().getCurrentTime()) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("ignoring too old call");
                                }
                                continue;
                            }
                            boolean notificationsDisabled = false;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !NotificationManagerCompat.from(ApplicationLoader.applicationContext).areNotificationsEnabled()) {
                                notificationsDisabled = true;
                                if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("Ignoring incoming call because notifications are disabled in system");
                                    }
                                    continue;
                                }
                            }
                            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                            if (svc != null || VoIPService.callIShouldHavePutIntoIntent != null || tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("Auto-declining call " + call.id + " because there's already active one");
                                }
                                TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
                                req.peer = new TLRPC.TL_inputPhoneCall();
                                req.peer.access_hash = call.access_hash;
                                req.peer.id = call.id;
                                req.reason = new TLRPC.TL_phoneCallDiscardReasonBusy();
                                getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (response != null) {
                                        TLRPC.Updates updates1 = (TLRPC.Updates) response;
                                        processUpdates(updates1, false);
                                    }
                                });
                                continue;
                            }
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("Starting service for call " + call.id);
                            }
                            VoIPService.callIShouldHavePutIntoIntent = call;
                            Intent intent = new Intent(ApplicationLoader.applicationContext, VoIPService.class);
                            intent.putExtra("is_outgoing", false);
                            intent.putExtra("user_id", call.participant_id == getUserConfig().getClientUserId() ? call.admin_id : call.participant_id);
                            intent.putExtra("account", currentAccount);
                            intent.putExtra("notifications_disabled", notificationsDisabled);
                            try {
                                if (!notificationsDisabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    ApplicationLoader.applicationContext.startForegroundService(intent);
                                } else {
                                    ApplicationLoader.applicationContext.startService(intent);
                                }
                                if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
                                    ignoreSetOnline = true;
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        } else {
                            if (svc != null && call != null) {
                                svc.onCallUpdated(call);
                            } else if (VoIPService.callIShouldHavePutIntoIntent != null) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("Updated the call while the service is starting");
                                }
                                if (call.id == VoIPService.callIShouldHavePutIntoIntent.id) {
                                    VoIPService.callIShouldHavePutIntoIntent = call;
                                }
                            }
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateDialogUnreadMark) {
                        TLRPC.TL_updateDialogUnreadMark update = (TLRPC.TL_updateDialogUnreadMark) baseUpdate;
                        long did;
                        if (update.peer instanceof TLRPC.TL_dialogPeer) {
                            TLRPC.TL_dialogPeer dialogPeer = (TLRPC.TL_dialogPeer) update.peer;
                            if (dialogPeer.peer.user_id != 0) {
                                did = dialogPeer.peer.user_id;
                            } else if (dialogPeer.peer.chat_id != 0) {
                                did = -dialogPeer.peer.chat_id;
                            } else {
                                did = -dialogPeer.peer.channel_id;
                            }
                        } else {
                            did = 0;
                        }
                        getMessagesStorage().setDialogUnread(did, update.unread);
                        TLRPC.Dialog dialog = dialogs_dict.get(did);
                        if (dialog != null && dialog.unread_mark != update.unread) {
                            dialog.unread_mark = update.unread;
                            if (dialog.unread_count == 0 && !isDialogMuted(did)) {
                                if (dialog.unread_mark) {
                                    unreadUnmutedDialogs++;
                                } else {
                                    unreadUnmutedDialogs--;
                                }
                            }
                            updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                            updateDialogFiltersFlags |= DIALOG_FILTER_FLAG_EXCLUDE_READ;
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateMessagePoll) {
                        TLRPC.TL_updateMessagePoll update = (TLRPC.TL_updateMessagePoll) baseUpdate;
                        getNotificationCenter().postNotificationName(NotificationCenter.didUpdatePollResults, update.poll_id, update.poll, update.results);
                    } else if (baseUpdate instanceof TLRPC.TL_updatePeerSettings) {
                        TLRPC.TL_updatePeerSettings update = (TLRPC.TL_updatePeerSettings) baseUpdate;
                        long dialogId;
                        if (update.peer instanceof TLRPC.TL_peerUser) {
                            dialogId = update.peer.user_id;
                        } else if (update.peer instanceof TLRPC.TL_peerChat) {
                            dialogId = -update.peer.chat_id;
                        } else {
                            dialogId = -update.peer.channel_id;
                        }
                        savePeerSettings(dialogId, update.settings, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updatePeerLocated) {
                        getNotificationCenter().postNotificationName(NotificationCenter.newPeopleNearbyAvailable, baseUpdate);
                    } else if (baseUpdate instanceof TLRPC.TL_updateMessageReactions) {
                        TLRPC.TL_updateMessageReactions update = (TLRPC.TL_updateMessageReactions) baseUpdate;
                        long dialogId;
                        if (update.peer.chat_id != 0) {
                            dialogId = -update.peer.chat_id;
                        } else if (update.peer.channel_id != 0) {
                            dialogId = -update.peer.channel_id;
                        } else {
                            dialogId = update.peer.user_id;
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.didUpdateReactions, dialogId, update.msg_id, update.reactions);
                    } else if (baseUpdate instanceof TLRPC.TL_updateTheme) {
                        TLRPC.TL_updateTheme update = (TLRPC.TL_updateTheme) baseUpdate;
                        TLRPC.TL_theme theme = (TLRPC.TL_theme) update.theme;
                        Theme.setThemeUploadInfo(null, null, theme, currentAccount, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateDialogFilter) {
                        loadRemoteFilters(true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateDialogFilterOrder) {
                        loadRemoteFilters(true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateDialogFilters) {
                        loadRemoteFilters(true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateReadChannelDiscussionInbox) {
                        TLRPC.TL_updateReadChannelDiscussionInbox update = (TLRPC.TL_updateReadChannelDiscussionInbox) baseUpdate;
                        getNotificationCenter().postNotificationName(NotificationCenter.threadMessagesRead, -update.channel_id, update.top_msg_id, update.read_max_id, 0);
                        if ((update.flags & 1) != 0) {
                            getMessagesStorage().updateRepliesMaxReadId(update.broadcast_id, update.broadcast_post, update.read_max_id, true);
                            getNotificationCenter().postNotificationName(NotificationCenter.commentsRead, update.broadcast_id, update.broadcast_post, update.read_max_id);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateReadChannelDiscussionOutbox) {
                        TLRPC.TL_updateReadChannelDiscussionOutbox update = (TLRPC.TL_updateReadChannelDiscussionOutbox) baseUpdate;
                        getNotificationCenter().postNotificationName(NotificationCenter.threadMessagesRead, -update.channel_id, update.top_msg_id, 0, update.read_max_id);
                    } else if (baseUpdate instanceof TLRPC.TL_updatePeerHistoryTTL) {
                        TLRPC.TL_updatePeerHistoryTTL updatePeerHistoryTTL = (TLRPC.TL_updatePeerHistoryTTL) baseUpdate;
                        long peerId = MessageObject.getPeerId(updatePeerHistoryTTL.peer);
                        TLRPC.ChatFull chatFull = null;
                        TLRPC.UserFull userFull = null;
                        if (peerId > 0) {
                            userFull = getUserFull(peerId);
                            if (userFull != null) {
                                userFull.ttl_period = updatePeerHistoryTTL.ttl_period;
                                if (userFull.ttl_period == 0) {
                                    userFull.flags &=~ 16384;
                                } else {
                                    userFull.flags |= 16384;
                                }
                            }
                        } else {
                            chatFull = getChatFull(-peerId);
                            if (chatFull != null) {
                                chatFull.ttl_period = updatePeerHistoryTTL.ttl_period;
                                if (chatFull instanceof TLRPC.TL_channelFull) {
                                    if (chatFull.ttl_period == 0) {
                                        chatFull.flags &= ~16777216;
                                    } else {
                                        chatFull.flags |= 16777216;
                                    }
                                } else {
                                    if (chatFull.ttl_period == 0) {
                                        chatFull.flags &= ~16384;
                                    } else {
                                        chatFull.flags |= 16384;
                                    }
                                }
                            }
                        }
                        if (chatFull != null) {
                            getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
                            getMessagesStorage().updateChatInfo(chatFull, false);
                        } else if (userFull != null) {
                            getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, peerId, userFull);
                            getMessagesStorage().updateUserInfo(userFull, false);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updatePendingJoinRequests) {
                        TLRPC.TL_updatePendingJoinRequests update = (TLRPC.TL_updatePendingJoinRequests) baseUpdate;
                        getMemberRequestsController().onPendingRequestsUpdated(update);
                    }
                }
                if (editor != null) {
                    editor.commit();
                    getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                }
                getMessagesStorage().updateUsers(dbUsersStatus, true, true, true);
                getMessagesStorage().updateUsers(dbUsers, false, true, true);
            }
            if (groupSpeakingActionsFinal != null) {
                for (int a = 0, N = groupSpeakingActionsFinal.size(); a < N; a++) {
                    long chatId = groupSpeakingActionsFinal.keyAt(a);
                    ChatObject.Call call = groupCallsByChatId.get(chatId);
                    if (call != null) {
                        call.processTypingsUpdate(getAccountInstance(), groupSpeakingActionsFinal.valueAt(a), date);
                    }
                }
            }
            if (importingActionsFinal != null) {
                for (int a = 0, N = importingActionsFinal.size(); a < N; a++) {
                    long did = importingActionsFinal.keyAt(a);
                    SendMessagesHelper.ImportingHistory importingHistory = getSendMessagesHelper().getImportingHistory(did);
                    if (importingHistory == null) {
                        continue;
                    }
                    importingHistory.setImportProgress(importingActionsFinal.valueAt(a));
                }
            }
            if (webPagesFinal != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.didReceivedWebpagesInUpdates, webPagesFinal);
                for (int i = 0; i < 2; i++) {
                    HashMap<String, ArrayList<MessageObject>> map = i == 1 ? reloadingScheduledWebpages : reloadingWebpages;
                    LongSparseArray<ArrayList<MessageObject>> array = i == 1 ? reloadingScheduledWebpagesPending : reloadingWebpagesPending;

                    for (int b = 0, size = webPagesFinal.size(); b < size; b++) {
                        long key = webPagesFinal.keyAt(b);
                        ArrayList<MessageObject> arrayList = array.get(key);
                        array.remove(key);
                        if (arrayList != null) {
                            TLRPC.WebPage webpage = webPagesFinal.valueAt(b);
                            ArrayList<TLRPC.Message> arr = new ArrayList<>();
                            long dialogId = 0;
                            if (webpage instanceof TLRPC.TL_webPage || webpage instanceof TLRPC.TL_webPageEmpty) {
                                for (int a = 0, size2 = arrayList.size(); a < size2; a++) {
                                    arrayList.get(a).messageOwner.media.webpage = webpage;
                                    if (a == 0) {
                                        dialogId = arrayList.get(a).getDialogId();
                                        ImageLoader.saveMessageThumbs(arrayList.get(a).messageOwner);
                                    }
                                    arr.add(arrayList.get(a).messageOwner);
                                }
                            } else {
                                array.put(webpage.id, arrayList);
                            }
                            if (!arr.isEmpty()) {
                                getMessagesStorage().putMessages(arr, true, true, false, getDownloadController().getAutodownloadMask(), i == 1);
                                getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, arrayList);
                            }
                        }
                    }
                }
            }
            if (updateDialogFiltersFlags != 0) {
                for (int a = 0; a < selectedDialogFilter.length; a++) {
                    if (selectedDialogFilter[a] != null && (selectedDialogFilter[a].flags & updateDialogFiltersFlags) != 0) {
                        forceDialogsUpdate = true;
                        break;
                    }
                }
            }

            boolean updateDialogs = false;
            if (messagesFinal != null) {
                boolean sorted = false;
                for (int a = 0, size = messagesFinal.size(); a < size; a++) {
                    long key = messagesFinal.keyAt(a);
                    ArrayList<MessageObject> value = messagesFinal.valueAt(a);
                    if (updateInterfaceWithMessages(key, value, false)) {
                        sorted = true;
                    }
                }
                boolean applied = applyFoldersUpdates(folderUpdatesFinal);
                if (applied || !sorted && forceDialogsUpdate) {
                    sortDialogs(null);
                }
                updateDialogs = true;
            } else {
                boolean applied = applyFoldersUpdates(folderUpdatesFinal);
                if (forceDialogsUpdate || applied) {
                    sortDialogs(null);
                    updateDialogs = true;
                }
            }
            if (scheduledMessagesFinal != null) {
                for (int a = 0, size = scheduledMessagesFinal.size(); a < size; a++) {
                    long key = scheduledMessagesFinal.keyAt(a);
                    ArrayList<MessageObject> value = scheduledMessagesFinal.valueAt(a);
                    updateInterfaceWithMessages(key, value, true);
                }
            }
            if (editingMessagesFinal != null) {
                for (int b = 0, size = editingMessagesFinal.size(); b < size; b++) {
                    long dialogId = editingMessagesFinal.keyAt(b);
                    ArrayList<MessageObject> arrayList = editingMessagesFinal.valueAt(b);
                    MessageObject oldObject = dialogMessage.get(dialogId);
                    if (oldObject != null) {
                        for (int a = 0, size2 = arrayList.size(); a < size2; a++) {
                            MessageObject newMessage = arrayList.get(a);
                            if (oldObject.getId() == newMessage.getId()) {
                                dialogMessage.put(dialogId, newMessage);
                                if (newMessage.messageOwner.peer_id != null && newMessage.messageOwner.peer_id.channel_id == 0) {
                                    dialogMessagesByIds.put(newMessage.getId(), newMessage);
                                }
                                updateDialogs = true;
                                break;
                            } else if (oldObject.getDialogId() == newMessage.getDialogId() && oldObject.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage && oldObject.replyMessageObject != null && oldObject.replyMessageObject.getId() == newMessage.getId()) {
                                oldObject.replyMessageObject = newMessage;
                                oldObject.generatePinMessageText(null, null);
                                updateDialogs = true;
                                break;
                            }
                        }
                    }
                    getMediaDataController().loadReplyMessagesForMessages(arrayList, dialogId, false, null);
                    getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialogId, arrayList, false);
                }
            }
            if (updateDialogs) {
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            }

            if (printChangedArg) {
                updateMask |= UPDATE_MASK_USER_PRINT;
            }
            if (contactsIdsFinal != null) {
                updateMask |= UPDATE_MASK_NAME;
                updateMask |= UPDATE_MASK_USER_PHONE;
            }
            if (chatInfoToUpdateFinal != null) {
                for (int a = 0, size = chatInfoToUpdateFinal.size(); a < size; a++) {
                    TLRPC.ChatParticipants info = chatInfoToUpdateFinal.get(a);
                    getMessagesStorage().updateChatParticipants(info);
                }
            }
            if (channelViewsFinal != null || channelForwardsFinal != null || channelRepliesFinal != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.didUpdateMessagesViews, channelViewsFinal, channelForwardsFinal, channelRepliesFinal, true);
            }
            if (updateMask != 0) {
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, updateMask);
            }

            if (updateMessageThumbs != null) {
                ImageLoader.getInstance().putThumbsToCache(updateMessageThumbs);
            }
        });

        LongSparseIntArray markAsReadMessagesInboxFinal = markAsReadMessagesInbox;
        LongSparseIntArray markAsReadMessagesOutboxFinal = markAsReadMessagesOutbox;
        LongSparseArray<ArrayList<Integer>> markContentAsReadMessagesFinal = markContentAsReadMessages;
        SparseIntArray markAsReadEncryptedFinal = markAsReadEncrypted;
        LongSparseArray<ArrayList<Integer>> deletedMessagesFinal = deletedMessages;
        LongSparseArray<ArrayList<Integer>> scheduledDeletedMessagesFinal = scheduledDeletedMessages;
        LongSparseIntArray clearHistoryMessagesFinal = clearHistoryMessages;
        getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            int updateMask = 0;
            if (markAsReadMessagesInboxFinal != null || markAsReadMessagesOutboxFinal != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.messagesRead, markAsReadMessagesInboxFinal, markAsReadMessagesOutboxFinal);
                if (markAsReadMessagesInboxFinal != null) {
                    getNotificationsController().processReadMessages(markAsReadMessagesInboxFinal, 0, 0, 0, false);
                    SharedPreferences.Editor editor = notificationsPreferences.edit();
                    for (int b = 0, size = markAsReadMessagesInboxFinal.size(); b < size; b++) {
                        long key = markAsReadMessagesInboxFinal.keyAt(b);
                        int messageId = markAsReadMessagesInboxFinal.valueAt(b);
                        TLRPC.Dialog dialog = dialogs_dict.get(key);
                        if (dialog != null && dialog.top_message > 0 && dialog.top_message <= messageId) {
                            MessageObject obj = dialogMessage.get(dialog.id);
                            if (obj != null && !obj.isOut()) {
                                obj.setIsRead();
                                updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                            }
                        }
                        if (key != getUserConfig().getClientUserId()) {
                            editor.remove("diditem" + key);
                            editor.remove("diditemo" + key);
                        }
                    }
                    editor.commit();
                }
                if (markAsReadMessagesOutboxFinal != null) {
                    for (int b = 0, size = markAsReadMessagesOutboxFinal.size(); b < size; b++) {
                        long key = markAsReadMessagesOutboxFinal.keyAt(b);
                        int messageId = markAsReadMessagesOutboxFinal.valueAt(b);
                        TLRPC.Dialog dialog = dialogs_dict.get(key);
                        if (dialog != null && dialog.top_message > 0 && dialog.top_message <= messageId) {
                            MessageObject obj = dialogMessage.get(dialog.id);
                            if (obj != null && obj.isOut()) {
                                obj.setIsRead();
                                updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                            }
                        }
                    }
                }
            }
            if (markAsReadEncryptedFinal != null) {
                for (int a = 0, size = markAsReadEncryptedFinal.size(); a < size; a++) {
                    int key = markAsReadEncryptedFinal.keyAt(a);
                    int value = markAsReadEncryptedFinal.valueAt(a);
                    getNotificationCenter().postNotificationName(NotificationCenter.messagesReadEncrypted, key, value);
                    long dialogId = DialogObject.makeEncryptedDialogId(key);
                    TLRPC.Dialog dialog = dialogs_dict.get(dialogId);
                    if (dialog != null) {
                        MessageObject message = dialogMessage.get(dialogId);
                        if (message != null && message.messageOwner.date <= value) {
                            message.setIsRead();
                            updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                        }
                    }
                }
            }
            if (markContentAsReadMessagesFinal != null) {
                for (int a = 0, size = markContentAsReadMessagesFinal.size(); a < size; a++) {
                    long key = markContentAsReadMessagesFinal.keyAt(a);
                    ArrayList<Integer> value = markContentAsReadMessagesFinal.valueAt(a);
                    getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, key, value);
                }
            }
            if (deletedMessagesFinal != null) {
                for (int a = 0, size = deletedMessagesFinal.size(); a < size; a++) {
                    long dialogId = deletedMessagesFinal.keyAt(a);
                    ArrayList<Integer> arrayList = deletedMessagesFinal.valueAt(a);
                    if (arrayList == null) {
                        continue;
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, arrayList, -dialogId, false);
                    if (dialogId == 0) {
                        for (int b = 0, size2 = arrayList.size(); b < size2; b++) {
                            Integer id = arrayList.get(b);
                            MessageObject obj = dialogMessagesByIds.get(id);
                            if (obj != null) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("mark messages " + obj.getId() + " deleted");
                                }
                                obj.deleted = true;
                            }
                        }
                    } else {
                        MessageObject obj = dialogMessage.get(dialogId);
                        if (obj != null) {
                            for (int b = 0, size2 = arrayList.size(); b < size2; b++) {
                                if (obj.getId() == arrayList.get(b)) {
                                    obj.deleted = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                getNotificationsController().removeDeletedMessagesFromNotifications(deletedMessagesFinal);
            }
            if (scheduledDeletedMessagesFinal != null) {
                for (int a = 0, size = scheduledDeletedMessagesFinal.size(); a < size; a++) {
                    long key = scheduledDeletedMessagesFinal.keyAt(a);
                    ArrayList<Integer> arrayList = scheduledDeletedMessagesFinal.valueAt(a);
                    if (arrayList == null) {
                        continue;
                    }

                    getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, arrayList, DialogObject.isChatDialog(key) && ChatObject.isChannel(getChat(-key)) ? -key : 0, true);
                }
            }
            if (clearHistoryMessagesFinal != null) {
                for (int a = 0, size = clearHistoryMessagesFinal.size(); a < size; a++) {
                    long key = clearHistoryMessagesFinal.keyAt(a);
                    int id = clearHistoryMessagesFinal.valueAt(a);
                    long did = -key;
                    getNotificationCenter().postNotificationName(NotificationCenter.historyCleared, did, id);
                    MessageObject obj = dialogMessage.get(did);
                    if (obj != null) {
                        if (obj.getId() <= id) {
                            obj.deleted = true;
                            break;
                        }
                    }
                }
                getNotificationsController().removeDeletedHisoryFromNotifications(clearHistoryMessagesFinal);
            }
            if (updateMask != 0) {
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, updateMask);
            }
        }));

        if (webPages != null) {
            getMessagesStorage().putWebPages(webPages);
        }
        if (markAsReadMessagesInbox != null || markAsReadMessagesOutbox != null || markAsReadEncrypted != null || markContentAsReadMessages != null) {
            if (markAsReadMessagesInbox != null || markAsReadMessagesOutbox != null || markContentAsReadMessages != null) {
                getMessagesStorage().updateDialogsWithReadMessages(markAsReadMessagesInbox, markAsReadMessagesOutbox, markContentAsReadMessages, true);
            }
            getMessagesStorage().markMessagesAsRead(markAsReadMessagesInbox, markAsReadMessagesOutbox, markAsReadEncrypted, true);
        }
        if (markContentAsReadMessages != null) {
            int time = getConnectionsManager().getCurrentTime();
            for (int a = 0, size = markContentAsReadMessages.size(); a < size; a++) {
                long key = markContentAsReadMessages.keyAt(a);
                ArrayList<Integer> arrayList = markContentAsReadMessages.valueAt(a);
                getMessagesStorage().markMessagesContentAsRead(key, arrayList, time);
            }
        }
        if (deletedMessages != null) {
            for (int a = 0, size = deletedMessages.size(); a < size; a++) {
                long key = deletedMessages.keyAt(a);
                ArrayList<Integer> arrayList = deletedMessages.valueAt(a);
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = getMessagesStorage().markMessagesAsDeleted(key, arrayList, false, true, false);
                    getMessagesStorage().updateDialogsWithDeletedMessages(key, -key, arrayList, dialogIds, false);
                });
            }
        }
        if (scheduledDeletedMessages != null) {
            for (int a = 0, size = scheduledDeletedMessages.size(); a < size; a++) {
                long key = scheduledDeletedMessages.keyAt(a);
                ArrayList<Integer> arrayList = scheduledDeletedMessages.valueAt(a);
                getMessagesStorage().markMessagesAsDeleted(key, arrayList, true, false, true);
            }
        }
        if (clearHistoryMessages != null) {
            for (int a = 0, size = clearHistoryMessages.size(); a < size; a++) {
                long key = clearHistoryMessages.keyAt(a);
                int id = clearHistoryMessages.valueAt(a);
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = getMessagesStorage().markMessagesAsDeleted(key, id, false, true);
                    getMessagesStorage().updateDialogsWithDeletedMessages(key, -key, new ArrayList<>(), dialogIds, false);
                });
            }
        }
        if (tasks != null) {
            for (int a = 0, size = tasks.size(); a < size; a++) {
                TLRPC.TL_updateEncryptedMessagesRead update = tasks.get(a);
                getMessagesStorage().createTaskForSecretChat(update.chat_id, update.max_date, update.date, 1, null);
            }
        }

        return true;
    }

    public boolean isDialogMuted(long dialogId) {
        return isDialogMuted(dialogId, null);
    }

    public boolean isDialogMuted(long dialogId, TLRPC.Chat chat) {
        int mute_type = notificationsPreferences.getInt("notify2_" + dialogId, -1);
        if (mute_type == -1) {
            Boolean forceChannel;
            if (chat != null) {
                forceChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            } else {
                forceChannel = null;
            }
            return !getNotificationsController().isGlobalNotificationsEnabled(dialogId, forceChannel);
        }
        if (mute_type == 2) {
            return true;
        } else if (mute_type == 3) {
            int mute_until = notificationsPreferences.getInt("notifyuntil_" + dialogId, 0);
            if (mute_until >= getConnectionsManager().getCurrentTime()) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<MessageObject> getSponsoredMessages(long dialogId) {
        SponsoredMessagesInfo info = sponsoredMessages.get(dialogId);
        if (info != null && (info.loading || Math.abs(SystemClock.elapsedRealtime() - info.loadTime) <= 5 * 60 * 1000)) {
            return info.messages;
        }
        TLRPC.Chat chat = getChat(-dialogId);
        if (!ChatObject.isChannel(chat) || chat.megagroup || chat.gigagroup) {
            return null;
        }
        info = new SponsoredMessagesInfo();
        info.loading = true;
        sponsoredMessages.put(dialogId, info);
        SponsoredMessagesInfo infoFinal = info;
        TLRPC.TL_channels_getSponsoredMessages req = new TLRPC.TL_channels_getSponsoredMessages();
        req.channel = getInputChannel(chat);
        getConnectionsManager().sendRequest(req, (response, error) -> {
            ArrayList<MessageObject> result;
            if (response != null) {
                TLRPC.TL_messages_sponsoredMessages res = (TLRPC.TL_messages_sponsoredMessages) response;
                if (res.messages.isEmpty()) {
                    result = null;
                } else {
                    result = new ArrayList<>();
                    AndroidUtilities.runOnUIThread(() -> {
                        putUsers(res.users, false);
                        putChats(res.chats, false);
                    });
                    final LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
                    final LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();

                    for (int a = 0; a < res.users.size(); a++) {
                        TLRPC.User u = res.users.get(a);
                        usersDict.put(u.id, u);
                    }
                    for (int a = 0; a < res.chats.size(); a++) {
                        TLRPC.Chat c = res.chats.get(a);
                        chatsDict.put(c.id, c);
                    }

                    int messageId = -10000000;
                    for (int a = 0, N = res.messages.size(); a < N; a++) {
                        TLRPC.TL_sponsoredMessage sponsoredMessage = res.messages.get(a);
                        TLRPC.TL_message message = new TLRPC.TL_message();
                        message.message = sponsoredMessage.message;
                        if (!sponsoredMessage.entities.isEmpty()) {
                            message.entities = sponsoredMessage.entities;
                            message.flags |= 128;
                        }
                        message.peer_id = getPeer(dialogId);
                        message.from_id = sponsoredMessage.from_id;
                        message.flags |= 256;
                        message.date = getConnectionsManager().getCurrentTime();
                        message.id = messageId--;
                        MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, true, true);
                        messageObject.sponsoredId = sponsoredMessage.random_id;
                        messageObject.botStartParam = sponsoredMessage.start_param;
                        messageObject.sponsoredChannelPost = sponsoredMessage.channel_post;
                        result.add(messageObject);
                    }
                }
            } else {
                result = null;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (result == null) {
                    sponsoredMessages.remove(dialogId);
                } else {
                    infoFinal.loadTime = SystemClock.elapsedRealtime();
                    infoFinal.messages = result;
                    getNotificationCenter().postNotificationName(NotificationCenter.didLoadSponsoredMessages, dialogId, result);
                }
            });
        });
        return null;
    }

    public CharSequence getPrintingString(long dialogId, int threadId, boolean isDialog) {
        if (isDialog && DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = getUser(dialogId);
            if (user != null && user.status != null && user.status.expires < 0) {
                return null;
            }
        }
        SparseArray<CharSequence> threads = printingStrings.get(dialogId);
        if (threads == null) {
            return null;
        }
        return threads.get(threadId);
    }

    public Integer getPrintingStringType(long dialogId, int threadId) {
        SparseArray<Integer> threads = printingStringsTypes.get(dialogId);
        if (threads == null) {
            return null;
        }
        return threads.get(threadId);
    }

    private boolean updatePrintingUsersWithNewMessages(long uid, ArrayList<MessageObject> messages) {
        if (uid > 0) {
            ConcurrentHashMap<Integer, ArrayList<PrintingUser>> arr = printingUsers.get(uid);
            if (arr != null) {
                printingUsers.remove(uid);
                return true;
            }
        } else if (uid < 0) {
            ArrayList<Long> messagesUsers = new ArrayList<>();
            for (MessageObject message : messages) {
                if (message.isFromUser() && !messagesUsers.contains(message.messageOwner.from_id.user_id)) {
                    messagesUsers.add(message.messageOwner.from_id.user_id);
                }
            }

            ConcurrentHashMap<Integer, ArrayList<PrintingUser>> threads = printingUsers.get(uid);
            boolean changed = false;
            if (threads != null) {
                ArrayList<Integer> threadsToRemove = null;
                for (HashMap.Entry<Integer, ArrayList<PrintingUser>> entry : threads.entrySet()) {
                    Integer threadId = entry.getKey();
                    ArrayList<PrintingUser> arr = entry.getValue();
                    for (int a = 0; a < arr.size(); a++) {
                        PrintingUser user = arr.get(a);
                        if (messagesUsers.contains(user.userId)) {
                            arr.remove(a);
                            a--;
                            if (arr.isEmpty()) {
                                if (threadsToRemove == null) {
                                    threadsToRemove = new ArrayList<>();
                                }
                                threadsToRemove.add(threadId);
                            }
                            changed = true;
                        }
                    }
                }
                if (threadsToRemove != null) {
                    for (int a = 0, N = threadsToRemove.size(); a < N; a++) {
                        threads.remove(threadsToRemove.get(a));
                    }
                    if (threads.isEmpty()) {
                        printingUsers.remove(uid);
                    }
                }
            }
            if (changed) {
                return true;
            }
        }
        return false;
    }

    protected boolean updateInterfaceWithMessages(long dialogId, ArrayList<MessageObject> messages, boolean scheduled) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }

        boolean isEncryptedChat = DialogObject.isEncryptedDialog(dialogId);
        MessageObject lastMessage = null;
        long channelId = 0;
        boolean updateRating = false;
        boolean hasNotOutMessage = false;
        if (!scheduled) {
            for (int a = 0; a < messages.size(); a++) {
                MessageObject message = messages.get(a);
                if (lastMessage == null || (!isEncryptedChat && message.getId() > lastMessage.getId() || (isEncryptedChat || message.getId() < 0 && lastMessage.getId() < 0) && message.getId() < lastMessage.getId()) || message.messageOwner.date > lastMessage.messageOwner.date) {
                    lastMessage = message;
                    if (message.messageOwner.peer_id.channel_id != 0) {
                        channelId = message.messageOwner.peer_id.channel_id;
                    }
                }
                if (message.messageOwner.action instanceof TLRPC.TL_messageActionGroupCall) {
                    TLRPC.ChatFull chatFull = getChatFull(message.messageOwner.peer_id.channel_id);
                    if (chatFull != null && (chatFull.call == null || chatFull.call.id != message.messageOwner.action.call.id)) {
                        loadFullChat(message.messageOwner.peer_id.channel_id, 0, true);
                    }
                }
                if (!hasNotOutMessage && !message.isOut()) {
                    hasNotOutMessage = true;
                }
                if (message.isOut() && !message.isSending() && !message.isForwarded()) {
                    if (message.isNewGif()) {
                        boolean save;
                        if (MessageObject.isDocumentHasAttachedStickers(message.messageOwner.media.document)) {
                            save = getMessagesController().saveGifsWithStickers;
                        } else {
                            save = true;
                        }
                        if (save) {
                            getMediaDataController().addRecentGif(message.messageOwner.media.document, message.messageOwner.date);
                        }
                    } else if (!message.isAnimatedEmoji() && (message.isSticker() || message.isAnimatedSticker())) {
                        getMediaDataController().addRecentSticker(MediaDataController.TYPE_IMAGE, message, message.messageOwner.media.document, message.messageOwner.date, false);
                    }
                }
                if (message.isOut() && message.isSent()) {
                    updateRating = true;
                }
            }
        }
        getMediaDataController().loadReplyMessagesForMessages(messages, dialogId, scheduled, null);
        getNotificationCenter().postNotificationName(NotificationCenter.didReceiveNewMessages, dialogId, messages, scheduled);

        if (lastMessage == null || scheduled) {
            return false;
        }
        TLRPC.TL_dialog dialog = (TLRPC.TL_dialog) dialogs_dict.get(dialogId);
        if (lastMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            if (dialog != null) {
                allDialogs.remove(dialog);
                dialogsServerOnly.remove(dialog);
                dialogsCanAddUsers.remove(dialog);
                dialogsChannelsOnly.remove(dialog);
                dialogsGroupsOnly.remove(dialog);
                for (int a = 0; a < selectedDialogFilter.length; a++) {
                    if (selectedDialogFilter[a] != null) {
                        selectedDialogFilter[a].dialogs.remove(dialog);
                    }
                }
                dialogsUsersOnly.remove(dialog);
                dialogsForBlock.remove(dialog);
                dialogsForward.remove(dialog);
                dialogs_dict.remove(dialog.id);
                dialogs_read_inbox_max.remove(dialog.id);
                dialogs_read_outbox_max.remove(dialog.id);
                int offset = nextDialogsCacheOffset.get(dialog.folder_id, 0);
                if (offset > 0) {
                    nextDialogsCacheOffset.put(dialog.folder_id, offset - 1);
                }
                dialogMessage.remove(dialog.id);
                ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.get(dialog.folder_id);
                if (dialogs != null) {
                    dialogs.remove(dialog);
                }
                MessageObject object = dialogMessagesByIds.get(dialog.top_message);
                if (object != null && object.messageOwner.peer_id.channel_id == 0) {
                    dialogMessagesByIds.remove(dialog.top_message);
                }
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = 0;
                getNotificationsController().removeNotificationsForDialog(dialog.id);
                getNotificationCenter().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
            }
            if (DialogObject.isChatDialog(dialogId)) {
                ChatObject.Call call = getGroupCall(-dialogId, false);
                if (call != null) {
                    TLRPC.Chat chat = getChat(lastMessage.messageOwner.action.channel_id);
                    if (chat != null) {
                        call.migrateToChat(chat);
                    }
                }
            }
            return false;
        }

        boolean changed = false;

        if (dialog == null) {
            TLRPC.Chat chat = getChat(channelId);
            if (channelId != 0 && chat == null || chat != null && (ChatObject.isNotInChat(chat) || chat.min)) {
                return false;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("not found dialog with id " + dialogId + " dictCount = " + dialogs_dict.size() + " allCount = " + allDialogs.size());
            }
            dialog = new TLRPC.TL_dialog();
            dialog.id = dialogId;
            int mid = dialog.top_message = lastMessage.getId();
            dialog.last_message_date = lastMessage.messageOwner.date;
            dialog.flags = ChatObject.isChannel(chat) ? 1 : 0;
            dialogs_dict.put(dialogId, dialog);
            allDialogs.add(dialog);
            dialogMessage.put(dialogId, lastMessage);
            if (lastMessage.messageOwner.peer_id.channel_id == 0) {
                dialogMessagesByIds.put(lastMessage.getId(), lastMessage);
                if (lastMessage.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.put(lastMessage.messageOwner.random_id, lastMessage);
                }
            }
            changed = true;

            TLRPC.Dialog dialogFinal = dialog;
            getMessagesStorage().getDialogFolderId(dialogId, param -> {
                if (param != -1) {
                    if (param != 0) {
                        dialogFinal.folder_id = param;
                        sortDialogs(null);
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                    }
                } else if (mid > 0) {
                    if (!DialogObject.isEncryptedDialog(dialogId)) {
                        loadUnknownDialog(getInputPeer(dialogId), 0);
                    }
                }
            });
        } else {
            if ((dialog.top_message > 0 && lastMessage.getId() > 0 && lastMessage.getId() > dialog.top_message) ||
                    (dialog.top_message < 0 && lastMessage.getId() < 0 && lastMessage.getId() < dialog.top_message) ||
                    dialogMessage.indexOfKey(dialogId) < 0 || dialog.top_message < 0 || dialog.last_message_date <= lastMessage.messageOwner.date) {
                MessageObject object = dialogMessagesByIds.get(dialog.top_message);
                if (object != null && object.messageOwner.peer_id.channel_id == 0) {
                    dialogMessagesByIds.remove(dialog.top_message);
                }
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = lastMessage.getId();
                dialog.last_message_date = lastMessage.messageOwner.date;
                changed = true;
                dialogMessage.put(dialogId, lastMessage);
                if (lastMessage.messageOwner.peer_id.channel_id == 0) {
                    dialogMessagesByIds.put(lastMessage.getId(), lastMessage);
                    if (lastMessage.messageOwner.random_id != 0) {
                        dialogMessagesByRandomIds.put(lastMessage.messageOwner.random_id, lastMessage);
                    }
                }
            }
        }

        if (changed) {
            sortDialogs(null);
        }

        if (updateRating) {
            getMediaDataController().increasePeerRaiting(dialogId);
        }
        return changed;
    }

    public void addDialogAction(long did, boolean clean) {
        TLRPC.Dialog dialog = dialogs_dict.get(did);
        if (dialog == null) {
            return;
        }
        if (clean) {
            clearingHistoryDialogs.put(did, dialog);
        } else {
            deletingDialogs.put(did, dialog);
            allDialogs.remove(dialog);
            sortDialogs(null);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
    }

    public void removeDialogAction(long did, boolean clean, boolean apply) {
        TLRPC.Dialog dialog = dialogs_dict.get(did);
        if (dialog == null) {
            return;
        }
        if (clean) {
            clearingHistoryDialogs.remove(did);
        } else {
            deletingDialogs.remove(did);
            if (!apply) {
                allDialogs.add(dialog);
                sortDialogs(null);
            }
        }
        if (!apply) {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        }
    }

    public boolean isClearingDialog(long did) {
        return clearingHistoryDialogs.get(did) != null;
    }

    public void sortDialogs(LongSparseArray<TLRPC.Chat> chatsDict) {
        dialogsServerOnly.clear();
        dialogsCanAddUsers.clear();
        dialogsChannelsOnly.clear();
        dialogsGroupsOnly.clear();
        for (int a = 0; a < selectedDialogFilter.length; a++) {
            if (selectedDialogFilter[a] != null) {
                selectedDialogFilter[a].dialogs.clear();
            }
        }
        dialogsUsersOnly.clear();
        dialogsForBlock.clear();
        dialogsForward.clear();
        for (int a = 0; a < dialogsByFolder.size(); a++) {
            ArrayList<TLRPC.Dialog> arrayList = dialogsByFolder.valueAt(a);
            if (arrayList != null) {
                arrayList.clear();
            }
        }
        unreadUnmutedDialogs = 0;
        boolean selfAdded = false;
        long selfId = getUserConfig().getClientUserId();
        if (selectedDialogFilter[0] != null || selectedDialogFilter[1] != null) {
            for (int b = 0; b < selectedDialogFilter.length; b++) {
                sortingDialogFilter = selectedDialogFilter[b];
                if (sortingDialogFilter == null) {
                    continue;
                }
                Collections.sort(allDialogs, dialogDateComparator);
                ArrayList<TLRPC.Dialog> dialogsByFilter = sortingDialogFilter.dialogs;

                for (int a = 0, N = allDialogs.size(); a < N; a++) {
                    TLRPC.Dialog d = allDialogs.get(a);
                    if (d instanceof TLRPC.TL_dialog) {
                        long dialogId = d.id;
                        if (DialogObject.isEncryptedDialog(dialogId)) {
                            TLRPC.EncryptedChat encryptedChat = getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                            if (encryptedChat != null) {
                                dialogId = encryptedChat.user_id;
                            }
                        }
                        if (sortingDialogFilter.includesDialog(getAccountInstance(), dialogId, d)) {
                            dialogsByFilter.add(d);
                        }
                    }
                }
            }
        }

        Collections.sort(allDialogs, dialogComparator);
        isLeftPromoChannel = true;
        if (promoDialog != null && promoDialog.id < 0) {
            TLRPC.Chat chat = getChat(-promoDialog.id);
            if (chat != null && !chat.left) {
                isLeftPromoChannel = false;
            }
        }
        for (int a = 0, N = allDialogs.size(); a < N; a++) {
            TLRPC.Dialog d = allDialogs.get(a);
            if (d instanceof TLRPC.TL_dialog) {
                MessageObject messageObject = dialogMessage.get(d.id);
                if (messageObject != null && messageObject.messageOwner.date < dialogsLoadedTillDate) {
                    continue;
                }
                boolean canAddToForward = true;
                if (!DialogObject.isEncryptedDialog(d.id)) {
                    dialogsServerOnly.add(d);
                    if (DialogObject.isChannel(d)) {
                        TLRPC.Chat chat = getChat(-d.id);
                        if (chat != null && chat.megagroup && (chat.admin_rights != null && (chat.admin_rights.post_messages || chat.admin_rights.add_admins) || chat.creator)) {
                            dialogsCanAddUsers.add(d);
                        }
                        if (chat != null && chat.megagroup) {
                            dialogsGroupsOnly.add(d);
                            canAddToForward = !chat.gigagroup || ChatObject.hasAdminRights(chat);
                        } else {
                            dialogsChannelsOnly.add(d);
                            canAddToForward = ChatObject.hasAdminRights(chat) && ChatObject.canPost(chat);
                        }
                    } else if (d.id < 0) {
                        if (chatsDict != null) {
                            TLRPC.Chat chat = chatsDict.get(-d.id);
                            if (chat != null && chat.migrated_to != null) {
                                allDialogs.remove(a);
                                a--;
                                N--;
                                continue;
                            }
                        }
                        dialogsCanAddUsers.add(d);
                        dialogsGroupsOnly.add(d);
                    } else if (d.id != selfId) {
                        dialogsUsersOnly.add(d);
                        if (!UserObject.isReplyUser(d.id)) {
                            dialogsForBlock.add(d);
                        }
                    }
                }
                if (canAddToForward && d.folder_id == 0) {
                    if (d.id == selfId) {
                        dialogsForward.add(0, d);
                        selfAdded = true;
                    } else {
                        dialogsForward.add(d);
                    }
                }
            }
            if ((d.unread_count != 0 || d.unread_mark) && !isDialogMuted(d.id)) {
                unreadUnmutedDialogs++;
            }
            if (promoDialog != null && d.id == promoDialog.id && isLeftPromoChannel) {
                allDialogs.remove(a);
                a--;
                N--;
                continue;
            }
            addDialogToItsFolder(-1, d);
        }
        if (promoDialog != null && isLeftPromoChannel) {
            allDialogs.add(0, promoDialog);
            addDialogToItsFolder(-2, promoDialog);
        }
        if (!selfAdded) {
            TLRPC.User user = getUserConfig().getCurrentUser();
            if (user != null) {
                TLRPC.Dialog dialog = new TLRPC.TL_dialog();
                dialog.id = user.id;
                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                dialog.peer = new TLRPC.TL_peerUser();
                dialog.peer.user_id = user.id;
                dialogsForward.add(0, dialog);
            }
        }
        for (int a = 0; a < dialogsByFolder.size(); a++) {
            int folderId = dialogsByFolder.keyAt(a);
            ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.valueAt(a);
            if (dialogs.isEmpty()) {
                dialogsByFolder.remove(folderId);
            }
        }
    }

    private void addDialogToItsFolder(int index, TLRPC.Dialog dialog) {
        int folderId;
        if (dialog instanceof TLRPC.TL_dialogFolder) {
            folderId = 0;
        } else {
            folderId = dialog.folder_id;
        }
        ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.get(folderId);
        if (dialogs == null) {
            dialogs = new ArrayList<>();
            dialogsByFolder.put(folderId, dialogs);
        }
        if (index == -1) {
            dialogs.add(dialog);
        } else if (index == -2) {
            if (dialogs.isEmpty() || !(dialogs.get(0) instanceof TLRPC.TL_dialogFolder)) {
                dialogs.add(0, dialog);
            } else {
                dialogs.add(1, dialog);
            }
        } else {
            dialogs.add(index, dialog);
        }
    }

    public static String getRestrictionReason(ArrayList<TLRPC.TL_restrictionReason> reasons) {
        if (reasons.isEmpty()) {
            return null;
        }
        for (int a = 0, N = reasons.size(); a < N; a++) {
            TLRPC.TL_restrictionReason reason = reasons.get(a);
            if ("all".equals(reason.platform) || !BuildVars.isStandaloneApp() && !BuildVars.isBetaApp() && "android".equals(reason.platform)) {
                return reason.text;
            }
        }
        return null;
    }

    private static void showCantOpenAlert(BaseFragment fragment, String reason) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setMessage(reason);
        fragment.showDialog(builder.create());
    }

    public boolean checkCanOpenChat(Bundle bundle, BaseFragment fragment) {
        return checkCanOpenChat(bundle, fragment, null);
    }

    public boolean checkCanOpenChat(Bundle bundle, BaseFragment fragment, MessageObject originalMessage) {
        if (bundle == null || fragment == null) {
            return true;
        }
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        long userId = bundle.getLong("user_id", 0);
        long chatId = bundle.getLong("chat_id", 0);
        int messageId = bundle.getInt("message_id", 0);
        if (userId != 0) {
            user = getUser(userId);
        } else if (chatId != 0) {
            chat = getChat(chatId);
        }
        if (user == null && chat == null) {
            return true;
        }
        String reason;
        if (chat != null) {
            reason = getRestrictionReason(chat.restriction_reason);
        } else {
            reason = getRestrictionReason(user.restriction_reason);
        }
        if (reason != null) {
            showCantOpenAlert(fragment, reason);
            return false;
        }
        if (messageId != 0 && originalMessage != null && chat != null && chat.access_hash == 0) {
            long did = originalMessage.getDialogId();
            if (!DialogObject.isEncryptedDialog(did)) {
                AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), 3);
                TLObject req;
                if (did < 0) {
                    chat = getChat(-did);
                }
                if (did > 0 || !ChatObject.isChannel(chat)) {
                    TLRPC.TL_messages_getMessages request = new TLRPC.TL_messages_getMessages();
                    request.id.add(originalMessage.getId());
                    req = request;
                } else {
                    chat = getChat(-did);
                    TLRPC.TL_channels_getMessages request = new TLRPC.TL_channels_getMessages();
                    request.channel = getInputChannel(chat);
                    request.id.add(originalMessage.getId());
                    req = request;
                }
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            putUsers(res.users, false);
                            putChats(res.chats, false);
                            getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                            fragment.presentFragment(new ChatActivity(bundle), true);
                        });
                    }
                });
                progressDialog.setOnCancelListener(dialog -> {
                    getConnectionsManager().cancelRequest(reqId, true);
                    fragment.setVisibleDialog(null);
                });
                fragment.setVisibleDialog(progressDialog);
                progressDialog.show();
                return false;
            }
        }
        return true;
    }

    public static void openChatOrProfileWith(TLRPC.User user, TLRPC.Chat chat, BaseFragment fragment, int type, boolean closeLast) {
        if (user == null && chat == null || fragment == null) {
            return;
        }
        String reason;
        if (chat != null) {
            reason = getRestrictionReason(chat.restriction_reason);
        } else {
            reason = getRestrictionReason(user.restriction_reason);
            if (type != 3 && user.bot) {
                type = 1;
                closeLast = true;
            }
        }
        if (reason != null) {
            showCantOpenAlert(fragment, reason);
        } else {
            Bundle args = new Bundle();
            if (chat != null) {
                args.putLong("chat_id", chat.id);
            } else {
                args.putLong("user_id", user.id);
            }
            if (type == 0) {
                fragment.presentFragment(new ProfileActivity(args));
            } else if (type == 2) {
                fragment.presentFragment(new ChatActivity(args), true, true);
            } else {
                fragment.presentFragment(new ChatActivity(args), closeLast);
            }
        }
    }

    public void openByUserName(String username, BaseFragment fragment, int type) {
        if (username == null || fragment == null) {
            return;
        }
        TLObject object = getUserOrChat(username);
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (object instanceof TLRPC.User) {
            user = (TLRPC.User) object;
            if (user.min) {
                user = null;
            }
        } else if (object instanceof TLRPC.Chat) {
            chat = (TLRPC.Chat) object;
            if (chat.min) {
                chat = null;
            }
        }
        if (user != null) {
            openChatOrProfileWith(user, null, fragment, type, false);
        } else if (chat != null) {
            openChatOrProfileWith(null, chat, fragment, 1, false);
        } else {
            if (fragment.getParentActivity() == null) {
                return;
            }
            AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(fragment.getParentActivity(), 3)};

            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    progressDialog[0].dismiss();
                } catch (Exception ignored) {

                }
                progressDialog[0] = null;
                fragment.setVisibleDialog(null);
                if (error == null) {
                    TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                    putUsers(res.users, false);
                    putChats(res.chats, false);
                    getMessagesStorage().putUsersAndChats(res.users, res.chats, false, true);
                    if (!res.chats.isEmpty()) {
                        openChatOrProfileWith(null, res.chats.get(0), fragment, 1, false);
                    } else if (!res.users.isEmpty()) {
                        openChatOrProfileWith(res.users.get(0), null, fragment, type, false);
                    }
                } else {
                    if (fragment.getParentActivity() != null) {
                        try {
                            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString("NoUsernameFound", R.string.NoUsernameFound)).show();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }));
            AndroidUtilities.runOnUIThread(() -> {
                if (progressDialog[0] == null) {
                    return;
                }
                progressDialog[0].setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(reqId, true));
                fragment.showDialog(progressDialog[0]);
            }, 500);
        }
    }

    public void ensureMessagesLoaded(long dialogId, int messageId, MessagesLoadedCallback callback) {
        SharedPreferences sharedPreferences = MessagesController.getNotificationsSettings(currentAccount);
        if (messageId == 0) {
            messageId = sharedPreferences.getInt("diditem" + dialogId, 0);
        }
        int finalMessageId = messageId;
        int classGuid = ConnectionsManager.generateClassGuid();

        long chatId;
        if (DialogObject.isChatDialog(dialogId)) {
            chatId = -dialogId;
        } else {
            chatId = 0;
        }

        TLRPC.Chat currentChat;

        if (chatId != 0) {
            currentChat = getMessagesController().getChat(chatId);
            if (currentChat == null) {
                MessagesStorage messagesStorage = getMessagesStorage();
                messagesStorage.getStorageQueue().postRunnable(() -> {
                    TLRPC.Chat chat = messagesStorage.getChat(chatId);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (chat != null) {
                            getMessagesController().putChat(chat, true);
                            ensureMessagesLoaded(dialogId, finalMessageId, callback);
                        } else {
                            if (callback != null) {
                                callback.onError();
                            }
                        }
                    });
                });
                return;
            }
        }

        int count = AndroidUtilities.isTablet() ? 30 : 20;

        NotificationCenter.NotificationCenterDelegate delegate = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                if (id == NotificationCenter.messagesDidLoadWithoutProcess && (Integer) args[0] == classGuid) {
                    int size = (int) args[1];
                    boolean isCache = (boolean) args[2];
                    boolean isEnd = (boolean) args[3];
                    int lastMessageId = (int) args[4];
                    if ((size < count / 2 && !isEnd) && isCache) {
                        if (finalMessageId != 0) {
                            loadMessagesInternal(dialogId, 0, false, count, finalMessageId, 0, false, 0, classGuid, 3, lastMessageId, 0, 0, 0, 0, 0, 0, false, 0, true, false);
                        } else {
                            loadMessagesInternal(dialogId, 0, false, count, finalMessageId, 0, false, 0, classGuid, 2, lastMessageId, 0, 0, 0, 0, 0, 0, false, 0, true, false);
                        }
                    } else {
                        getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoadWithoutProcess);
                        getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
                        if (callback != null) {
                            callback.onMessagesLoaded(isCache);
                        }
                    }
                } else if (id == NotificationCenter.loadingMessagesFailed && (Integer) args[0] == classGuid) {
                    getNotificationCenter().removeObserver(this, NotificationCenter.messagesDidLoadWithoutProcess);
                    getNotificationCenter().removeObserver(this, NotificationCenter.loadingMessagesFailed);
                    if (callback != null) {
                        callback.onError();
                    }
                }
            }
        };


        getNotificationCenter().addObserver(delegate, NotificationCenter.messagesDidLoadWithoutProcess);
        getNotificationCenter().addObserver(delegate, NotificationCenter.loadingMessagesFailed);

        if (messageId != 0) {
            loadMessagesInternal(dialogId, 0, true, count, finalMessageId, 0, true, 0, classGuid, 3, 0, 0, 0, 0, 0, 0, 0, false, 0, true, false);
        } else {
            loadMessagesInternal(dialogId, 0, true, count, finalMessageId, 0, true, 0, classGuid, 2, 0, 0, 0, 0, 0, 0, 0, false, 0, true, false);
        }
    }

    public int getChatPendingRequestsOnClosed(long chatId) {
        return mainPreferences.getInt("chatPendingRequests" + chatId, 0);
    }

    public void setChatPendingRequestsOnClose(long chatId, int count) {
        mainPreferences.edit()
                .putInt("chatPendingRequests" + chatId, count)
                .apply();
    }

    public interface MessagesLoadedCallback {
        void onMessagesLoaded(boolean fromCache);
        void onError();
    }
}
