/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import androidx.core.app.NotificationManagerCompat;

public class MessagesController extends BaseController implements NotificationCenter.NotificationCenterDelegate {

    private ConcurrentHashMap<Integer, TLRPC.Chat> chats = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.EncryptedChat> encryptedChats = new ConcurrentHashMap<>(10, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.User> users = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<String, TLObject> objectsByUsernames = new ConcurrentHashMap<>(100, 1.0f, 2);

    private ArrayList<Integer> joiningToChannels = new ArrayList<>();

    private SparseArray<TLRPC.ExportedChatInvite> exportedChats = new SparseArray<>();

    public ArrayList<TLRPC.RecentMeUrl> hintDialogs = new ArrayList<>();
    private SparseArray<ArrayList<TLRPC.Dialog>> dialogsByFolder = new SparseArray<>();
    protected ArrayList<TLRPC.Dialog> allDialogs = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsForward = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsServerOnly = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsCanAddUsers = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsChannelsOnly = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsUsersOnly = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsGroupsOnly = new ArrayList<>();
    public int unreadUnmutedDialogs;
    public ConcurrentHashMap<Long, Integer> dialogs_read_inbox_max = new ConcurrentHashMap<>(100, 1.0f, 2);
    public ConcurrentHashMap<Long, Integer> dialogs_read_outbox_max = new ConcurrentHashMap<>(100, 1.0f, 2);
    public LongSparseArray<TLRPC.Dialog> dialogs_dict = new LongSparseArray<>();
    public LongSparseArray<MessageObject> dialogMessage = new LongSparseArray<>();
    public LongSparseArray<MessageObject> dialogMessagesByRandomIds = new LongSparseArray<>();
    public LongSparseArray<Integer> deletedHistory = new LongSparseArray<>();
    public SparseArray<MessageObject> dialogMessagesByIds = new SparseArray<>();
    public ConcurrentHashMap<Long, ArrayList<PrintingUser>> printingUsers = new ConcurrentHashMap<>(20, 1.0f, 2);
    public LongSparseArray<CharSequence> printingStrings = new LongSparseArray<>();
    public LongSparseArray<Integer> printingStringsTypes = new LongSparseArray<>();
    public SparseArray<LongSparseArray<Boolean>> sendingTypings = new SparseArray<>();
    public ConcurrentHashMap<Integer, Integer> onlinePrivacy = new ConcurrentHashMap<>(20, 1.0f, 2);
    private int lastPrintingStringCount;

    private boolean dialogsInTransaction;

    private LongSparseArray<Boolean> loadingPeerSettings = new LongSparseArray<>();

    private ArrayList<Long> createdDialogIds = new ArrayList<>();
    private ArrayList<Long> createdScheduledDialogIds = new ArrayList<>();
    private ArrayList<Long> createdDialogMainThreadIds = new ArrayList<>();
    private ArrayList<Long> visibleDialogMainThreadIds = new ArrayList<>();
    private ArrayList<Long> visibleScheduledDialogMainThreadIds = new ArrayList<>();

    private SparseIntArray shortPollChannels = new SparseIntArray();
    private SparseIntArray needShortPollChannels = new SparseIntArray();
    private SparseIntArray shortPollOnlines = new SparseIntArray();
    private SparseIntArray needShortPollOnlines = new SparseIntArray();

    private LongSparseArray<TLRPC.Dialog> deletingDialogs = new LongSparseArray<>();
    private LongSparseArray<TLRPC.Dialog> clearingHistoryDialogs = new LongSparseArray<>();

    public boolean loadingBlockedUsers = false;
    public SparseIntArray blockedUsers = new SparseIntArray();
    public int totalBlockedCount = -1;
    public boolean blockedEndReached;

    private SparseArray<ArrayList<Integer>> channelViewsToSend = new SparseArray<>();
    private LongSparseArray<SparseArray<MessageObject>> pollsToCheck = new LongSparseArray<>();
    private int pollsToCheckSize;
    private long lastViewsCheckTime;

    private SparseArray<ArrayList<TLRPC.Updates>> updatesQueueChannels = new SparseArray<>();
    private SparseLongArray updatesStartWaitTimeChannels = new SparseLongArray();
    private SparseIntArray channelsPts = new SparseIntArray();
    private SparseBooleanArray gettingDifferenceChannels = new SparseBooleanArray();

    private SparseBooleanArray gettingUnknownChannels = new SparseBooleanArray();
    private LongSparseArray<Boolean> gettingUnknownDialogs = new LongSparseArray<>();
    private SparseBooleanArray checkingLastMessagesDialogs = new SparseBooleanArray();

    private ArrayList<TLRPC.Updates> updatesQueueSeq = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueuePts = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueueQts = new ArrayList<>();
    private long updatesStartWaitTimeSeq;
    private long updatesStartWaitTimePts;
    private long updatesStartWaitTimeQts;
    private SparseArray<TLRPC.UserFull> fullUsers = new SparseArray<>();
    private SparseArray<TLRPC.ChatFull> fullChats = new SparseArray<>();
    private ArrayList<Integer> loadingFullUsers = new ArrayList<>();
    private ArrayList<Integer> loadedFullUsers = new ArrayList<>();
    private ArrayList<Integer> loadingFullChats = new ArrayList<>();
    private ArrayList<Integer> loadingFullParticipants = new ArrayList<>();
    private ArrayList<Integer> loadedFullParticipants = new ArrayList<>();
    private ArrayList<Integer> loadedFullChats = new ArrayList<>();
    private SparseArray<SparseArray<String>> channelAdmins = new SparseArray<>();
    private SparseIntArray loadingChannelAdmins = new SparseIntArray();

    private SparseIntArray migratedChats = new SparseIntArray();

    private HashMap<String, ArrayList<MessageObject>> reloadingWebpages = new HashMap<>();
    private LongSparseArray<ArrayList<MessageObject>> reloadingWebpagesPending = new LongSparseArray<>();
    private HashMap<String, ArrayList<MessageObject>> reloadingScheduledWebpages = new HashMap<>();
    private LongSparseArray<ArrayList<MessageObject>> reloadingScheduledWebpagesPending = new LongSparseArray<>();

    private LongSparseArray<Long> lastScheduledServerQueryTime = new LongSparseArray<>();

    private LongSparseArray<ArrayList<Integer>> reloadingMessages = new LongSparseArray<>();

    private ArrayList<ReadTask> readTasks = new ArrayList<>();
    private LongSparseArray<ReadTask> readTasksMap = new LongSparseArray<>();

    private boolean gettingNewDeleteTask;
    private int currentDeletingTaskTime;
    private ArrayList<Integer> currentDeletingTaskMids;
    private int currentDeletingTaskChannelId;
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

    private int loadingNotificationSettings;
    private boolean loadingNotificationSignUpSettings;

    private int nextProxyInfoCheckTime;
    private boolean checkingProxyInfo;
    private int checkingProxyInfoRequestId;
    private int lastCheckProxyId;
    private TLRPC.Dialog proxyDialog;
    private boolean isLeftProxyChannel;
    private long proxyDialogId;
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

    private HashMap<String, Theme.ThemeInfo> uploadingThemes = new HashMap<>();

    private String uploadingWallpaper;
    private boolean uploadingWallpaperBlurred;
    private boolean uploadingWallpaperMotion;

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
    public String mapKey;
    public int maxMessageLength;
    public int maxCaptionLength;
    public boolean blockedCountry;
    public boolean defaultP2pContacts;
    public boolean preloadFeaturedStickers;
    public float animatedEmojisZoom;
    public String venueSearchBot;
    public String gifSearchBot;
    public String imageSearchBot;
    public String dcDomainName;
    public int webFileDatacenterId;
    public String suggestedLangCode;
    private String installReferer;

    private SharedPreferences notificationsPreferences;
    private SharedPreferences mainPreferences;
    private SharedPreferences emojiPreferences;

    private class UserActionUpdatesSeq extends TLRPC.Updates {

    }

    private class UserActionUpdatesPts extends TLRPC.Updates {

    }

    public static final int UPDATE_MASK_NAME = 1;
    public static final int UPDATE_MASK_AVATAR = 2;
    public static final int UPDATE_MASK_STATUS = 4;
    public static final int UPDATE_MASK_CHAT_AVATAR = 8;
    public static final int UPDATE_MASK_CHAT_NAME = 16;
    public static final int UPDATE_MASK_CHAT_MEMBERS = 32;
    public static final int UPDATE_MASK_USER_PRINT = 64;
    public static final int UPDATE_MASK_USER_PHONE = 128;
    public static final int UPDATE_MASK_READ_DIALOG_MESSAGE = 256;
    public static final int UPDATE_MASK_SELECT_DIALOG = 512;
    public static final int UPDATE_MASK_PHONE = 1024;
    public static final int UPDATE_MASK_NEW_MESSAGE = 2048;
    public static final int UPDATE_MASK_SEND_STATE = 4096;
    public static final int UPDATE_MASK_CHAT = 8192;
    //public static final int UPDATE_MASK_CHAT_ADMINS = 16384;
    public static final int UPDATE_MASK_MESSAGE_TEXT = 32768;
    public static final int UPDATE_MASK_CHECK = 65536;
    public static final int UPDATE_MASK_REORDER = 131072;
    public static final int UPDATE_MASK_ALL = UPDATE_MASK_AVATAR | UPDATE_MASK_STATUS | UPDATE_MASK_NAME | UPDATE_MASK_CHAT_AVATAR | UPDATE_MASK_CHAT_NAME | UPDATE_MASK_CHAT_MEMBERS | UPDATE_MASK_USER_PRINT | UPDATE_MASK_USER_PHONE | UPDATE_MASK_READ_DIALOG_MESSAGE | UPDATE_MASK_PHONE;

    private class ReadTask {
        public long dialogId;
        public int maxId;
        public int maxDate;
        public long sendRequestTime;
    }

    public static class PrintingUser {
        public long lastTime;
        public int userId;
        public TLRPC.SendMessageAction action;
    }

    private final Comparator<TLRPC.Dialog> dialogComparator = (dialog1, dialog2) -> {
        if (dialog1 instanceof TLRPC.TL_dialogFolder && !(dialog2 instanceof TLRPC.TL_dialogFolder)) {
            return -1;
        } else if (!(dialog1 instanceof TLRPC.TL_dialogFolder) && dialog2 instanceof TLRPC.TL_dialogFolder) {
            return 1;
        } else if (!dialog1.pinned && dialog2.pinned) {
            return 1;
        } else if (dialog1.pinned && !dialog2.pinned) {
            return -1;
        } else if (dialog1.pinned && dialog2.pinned) {
            if (dialog1.pinnedNum < dialog2.pinnedNum) {
                return 1;
            } else if (dialog1.pinnedNum > dialog2.pinnedNum) {
                return -1;
            } else {
                return 0;
            }
        }
        TLRPC.DraftMessage draftMessage = getMediaDataController().getDraft(dialog1.id);
        int date1 = draftMessage != null && draftMessage.date >= dialog1.last_message_date ? draftMessage.date : dialog1.last_message_date;
        draftMessage = getMediaDataController().getDraft(dialog2.id);
        int date2 = draftMessage != null && draftMessage.date >= dialog2.last_message_date ? draftMessage.date : dialog2.last_message_date;
        if (date1 < date2) {
            return 1;
        } else if (date1 > date2) {
            return -1;
        }
        return 0;
    };

    private final Comparator<TLRPC.Update> updatesComparator = (lhs, rhs) -> {
        int ltype = getUpdateType(lhs);
        int rtype = getUpdateType(rhs);
        if (ltype != rtype) {
            return AndroidUtilities.compare(ltype, rtype);
        } else if (ltype == 0) {
            return AndroidUtilities.compare(getUpdatePts(lhs), getUpdatePts(rhs));
        } else if (ltype == 1) {
            return AndroidUtilities.compare(getUpdateQts(lhs), getUpdateQts(rhs));
        } else if (ltype == 2) {
            int lChannel = getUpdateChannelId(lhs);
            int rChannel = getUpdateChannelId(rhs);
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
            getNotificationCenter().addObserver(messagesController, NotificationCenter.FileDidUpload);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.FileDidFailUpload);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.fileDidLoad);
            getNotificationCenter().addObserver(messagesController, NotificationCenter.fileDidFailToLoad);
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
        maxPinnedDialogsCount = mainPreferences.getInt("maxPinnedDialogsCount", 5);
        maxFolderPinnedDialogsCount = mainPreferences.getInt("maxFolderPinnedDialogsCount", 100);
        maxMessageLength = mainPreferences.getInt("maxMessageLength", 4096);
        maxCaptionLength = mainPreferences.getInt("maxCaptionLength", 1024);
        mapProvider = mainPreferences.getInt("mapProvider", 0);
        availableMapProviders = mainPreferences.getInt("availableMapProviders", 3);
        mapKey = mainPreferences.getString("pk", null);
        installReferer = mainPreferences.getString("installReferer", null);
        defaultP2pContacts = mainPreferences.getBoolean("defaultP2pContacts", false);
        revokeTimeLimit = mainPreferences.getInt("revokeTimeLimit", revokeTimeLimit);
        revokeTimePmLimit = mainPreferences.getInt("revokeTimePmLimit", revokeTimePmLimit);
        canRevokePmInbox = mainPreferences.getBoolean("canRevokePmInbox", canRevokePmInbox);
        preloadFeaturedStickers = mainPreferences.getBoolean("preloadFeaturedStickers", false);
        proxyDialogId = mainPreferences.getLong("proxy_dialog", 0);
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
                TLRPC.TL_jsonObject object = (TLRPC.TL_jsonObject) response;
                for (int a = 0, N = object.value.size(); a < N; a++) {
                    TLRPC.TL_jsonObjectValue value = object.value.get(a);
                    if ("emojies_animated_zoom".equals(value.key) && value.value instanceof TLRPC.TL_jsonNumber) {
                        TLRPC.TL_jsonNumber number = (TLRPC.TL_jsonNumber) value.value;
                        if (animatedEmojisZoom != number.value) {
                            animatedEmojisZoom = (float) number.value;
                            editor.putFloat("animatedEmojisZoom", animatedEmojisZoom);
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    editor.commit();
                }
            }
            loadingAppConfig = false;
        }));
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
            defaultP2pContacts = config.default_p2p_contacts;
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
            if (suggestedLangCode == null || !suggestedLangCode.equals(config.suggested_lang_code)) {
                suggestedLangCode = config.suggested_lang_code;
                LocaleController.getInstance().loadRemoteLanguages(currentAccount);
            }
            Theme.loadRemoteThemes(currentAccount, false);
            Theme.checkCurrentRemoteTheme(false);

            if (config.static_maps_provider == null) {
                config.static_maps_provider = "google";
            }

            mapKey = null;
            mapProvider = 0;
            availableMapProviders = 0;
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
            editor.putBoolean("defaultP2pContacts", defaultP2pContacts);
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

    public TLRPC.InputUser getInputUser(int user_id) {
        TLRPC.User user = getInstance(UserConfig.selectedAccount).getUser(user_id);
        return getInputUser(user);
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

    public TLRPC.InputChannel getInputChannel(int chatId) {
        return getInputChannel(getChat(chatId));
    }

    public TLRPC.InputPeer getInputPeer(int id) {
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

    public TLRPC.Peer getPeer(int id) {
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
        if (id == NotificationCenter.FileDidUpload) {
            final String location = (String) args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile) args[1];

            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                req.file = file;
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
                        } else if (smallSize != null) {
                            user.photo.photo_small = smallSize.location;
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
                final TLRPC.TL_wallPaperSettings settings = new TLRPC.TL_wallPaperSettings();
                settings.blur = uploadingWallpaperBlurred;
                settings.motion = uploadingWallpaperMotion;
                req.settings = settings;
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) response;
                    File path = new File(ApplicationLoader.getFilesDirFixed(), uploadingWallpaperBlurred ? "wallpaper_original.jpg" : "wallpaper.jpg");
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
                            SharedPreferences preferences = getGlobalMainSettings();
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putLong("selectedBackground2", wallPaper.id);
                            editor.putString("selectedBackgroundSlug", wallPaper.slug);
                            editor.commit();
                            ArrayList<TLRPC.WallPaper> wallpapers = new ArrayList<>();
                            wallpapers.add(wallPaper);
                            getMessagesStorage().putWallpapers(wallpapers, 2);
                            TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 320);
                            if (image != null) {
                                String newKey = image.location.volume_id + "_" + image.location.local_id + "@100_100";
                                String oldKey = Utilities.MD5(path.getAbsolutePath()) + "@100_100";
                                ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForDocument(image, wallPaper.document), false);
                            }
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.wallpapersNeedReload, wallPaper.id);
                        }
                    });
                });
            } else {
                Theme.ThemeInfo themeInfo = uploadingThemes.get(location);
                if (themeInfo != null) {
                    if (location.equals(themeInfo.uploadingThumb)) {
                        themeInfo.uploadedThumb = file;
                        themeInfo.uploadingThumb = null;
                    } else if (location.equals(themeInfo.uploadingFile)) {
                        themeInfo.uploadedFile = file;
                        themeInfo.uploadingFile = null;
                    }

                    if (themeInfo.uploadedFile != null && themeInfo.uploadedThumb != null) {
                        File f = new File(location);
                        TLRPC.TL_account_uploadTheme req = new TLRPC.TL_account_uploadTheme();
                        req.mime_type = "application/x-tgtheme-android";
                        req.file_name = "theme.attheme";
                        req.file = themeInfo.uploadedFile;
                        req.file.name = "theme.attheme";
                        req.thumb = themeInfo.uploadedThumb;
                        req.thumb.name = "theme-preview.jpg";
                        req.flags |= 1;
                        themeInfo.uploadedFile = null;
                        themeInfo.uploadedThumb = null;
                        getConnectionsManager().sendRequest(req, (response, error) -> {
                            int index = themeInfo.name.lastIndexOf(".attheme");
                            String n = index > 0 ? themeInfo.name.substring(0, index) : themeInfo.name;
                            if (response != null) {
                                TLRPC.Document document = (TLRPC.Document) response;
                                TLRPC.TL_inputDocument inputDocument = new TLRPC.TL_inputDocument();
                                inputDocument.access_hash = document.access_hash;
                                inputDocument.id = document.id;
                                inputDocument.file_reference = document.file_reference;
                                if (themeInfo.info == null || !themeInfo.info.creator) {
                                    TLRPC.TL_account_createTheme req2 = new TLRPC.TL_account_createTheme();
                                    req2.document = inputDocument;
                                    req2.slug = themeInfo.info != null && !TextUtils.isEmpty(themeInfo.info.slug) ? themeInfo.info.slug : "";
                                    req2.title = n;
                                    getConnectionsManager().sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (response1 instanceof TLRPC.TL_theme) {
                                            Theme.setThemeUploadInfo(themeInfo, (TLRPC.TL_theme) response1, false);
                                            installTheme(themeInfo, themeInfo == Theme.getCurrentNightTheme());
                                            getNotificationCenter().postNotificationName(NotificationCenter.themeUploadedToServer, themeInfo);
                                        } else {
                                            getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, themeInfo);
                                        }
                                    }));
                                } else {
                                    TLRPC.TL_account_updateTheme req2 = new TLRPC.TL_account_updateTheme();
                                    TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
                                    inputTheme.id = themeInfo.info.id;
                                    inputTheme.access_hash = themeInfo.info.access_hash;
                                    req2.theme = inputTheme;

                                    req2.slug = themeInfo.info.slug;
                                    req2.flags |= 1;

                                    req2.title = n;
                                    req2.flags |= 2;

                                    req2.document = inputDocument;
                                    req2.flags |= 4;

                                    req2.format = "android";
                                    getConnectionsManager().sendRequest(req2, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                        if (response1 instanceof TLRPC.TL_theme) {
                                            Theme.setThemeUploadInfo(themeInfo, (TLRPC.TL_theme) response1, false);
                                            getNotificationCenter().postNotificationName(NotificationCenter.themeUploadedToServer, themeInfo);
                                        } else {
                                            getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, themeInfo);
                                        }
                                    }));
                                }
                            } else {
                                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.themeUploadError, themeInfo));
                            }
                        });
                    }
                    uploadingThemes.remove(location);
                }
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            final String location = (String) args[0];
            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                uploadingAvatar = null;
            } else if (uploadingWallpaper != null && uploadingWallpaper.equals(location)) {
                uploadingWallpaper = null;
            } else {
                Theme.ThemeInfo themeInfo = uploadingThemes.remove(location);
                if (themeInfo != null) {
                    themeInfo.uploadedFile = null;
                    themeInfo.uploadedThumb = null;
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
            dialogMessagesByIds.remove(msgId);
            if (obj != null) {
                dialogMessagesByIds.put(newMsgId, obj);
            }
            int lowerId = (int) (long) did;
            if (lowerId < 0) {
                TLRPC.ChatFull chatFull = fullChats.get(-lowerId);
                TLRPC.Chat chat = getChat(-lowerId);
                if (chat != null && !ChatObject.hasAdminRights(chat) && chatFull != null && chatFull.slowmode_seconds != 0) {
                    chatFull.slowmode_next_send_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + chatFull.slowmode_seconds;
                    chatFull.flags |= 262144;
                    getMessagesStorage().updateChatInfo(chatFull, false);
                }
            }
        } else if (id == NotificationCenter.updateMessageMedia) {
            TLRPC.Message message = (TLRPC.Message) args[0];
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

    public void cleanup() {
        getContactsController().cleanup();
        MediaController.getInstance().cleanup();
        getNotificationsController().cleanup();
        getSendMessagesHelper().cleanup();
        getSecretChatHelper().cleanup();
        getLocationController().cleanup();
        getMediaDataController().cleanup();

        DialogsActivity.dialogsLoaded[currentAccount] = false;

        SharedPreferences.Editor editor = notificationsPreferences.edit();
        editor.clear().commit();
        editor = emojiPreferences.edit();
        editor.putLong("lastGifLoadTime", 0).putLong("lastStickersLoadTime", 0).putLong("lastStickersLoadTimeMask", 0).putLong("lastStickersLoadTimeFavs", 0).commit();
        editor = mainPreferences.edit();
        editor.remove("archivehint").remove("archivehint_l").remove("gifhint").remove("soundHint").remove("dcDomainName2").remove("webFileDatacenterId").commit();

        lastScheduledServerQueryTime.clear();
        reloadingWebpages.clear();
        reloadingWebpagesPending.clear();
        reloadingScheduledWebpages.clear();
        reloadingScheduledWebpagesPending.clear();
        dialogs_dict.clear();
        dialogs_read_inbox_max.clear();
        loadingPinnedDialogs.clear();
        dialogs_read_outbox_max.clear();
        exportedChats.clear();
        fullUsers.clear();
        fullChats.clear();
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
        dialogsCanAddUsers.clear();
        dialogsChannelsOnly.clear();
        dialogsGroupsOnly.clear();
        dialogsUsersOnly.clear();
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

        Utilities.stageQueue.postRunnable(() -> {
            readTasks.clear();
            readTasksMap.clear();
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
        blockedUsers.clear();
        sendingTypings.clear();
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
        nextProxyInfoCheckTime = 0;
        checkingProxyInfo = false;
        loadingUnreadDialogs = false;

        currentDeletingTaskTime = 0;
        currentDeletingTaskMids = null;
        currentDeletingTaskChannelId = 0;
        gettingNewDeleteTask = false;
        loadingBlockedUsers = false;
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
        uploadingThemes.clear();
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
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public TLRPC.User getUser(Integer id) {
        return users.get(id);
    }

    public TLObject getUserOrChat(String username) {
        if (username == null || username.length() == 0) {
            return null;
        }
        return objectsByUsernames.get(username.toLowerCase());
    }

    public ConcurrentHashMap<Integer, TLRPC.User> getUsers() {
        return users;
    }

    public ConcurrentHashMap<Integer, TLRPC.Chat> getChats() {
        return chats;
    }

    public TLRPC.Chat getChat(Integer id) {
        return chats.get(id);
    }

    public TLRPC.EncryptedChat getEncryptedChat(Integer id) {
        return encryptedChats.get(id);
    }

    public TLRPC.EncryptedChat getEncryptedChatDB(int chat_id, boolean created) {
        TLRPC.EncryptedChat chat = encryptedChats.get(chat_id);
        if (chat == null || created && (chat instanceof TLRPC.TL_encryptedChatWaiting || chat instanceof TLRPC.TL_encryptedChatRequested)) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            ArrayList<TLObject> result = new ArrayList<>();
            getMessagesStorage().getEncryptedChat(chat_id, countDownLatch, result);
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

    public boolean isDialogVisible(long dialog_id, boolean scheduled) {
        return scheduled ? visibleScheduledDialogMainThreadIds.contains(dialog_id) : visibleDialogMainThreadIds.contains(dialog_id);
    }

    public void setLastVisibleDialogId(final long dialog_id, boolean scheduled, final boolean set) {
        ArrayList<Long> arrayList = scheduled ? visibleScheduledDialogMainThreadIds : visibleDialogMainThreadIds;
        if (set) {
            if (arrayList.contains(dialog_id)) {
                return;
            }
            arrayList.add(dialog_id);
        } else {
            arrayList.remove(dialog_id);
        }
    }

    public void setLastCreatedDialogId(final long dialogId, boolean scheduled, final boolean set) {
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

    public TLRPC.ExportedChatInvite getExportedInvite(int chat_id) {
        return exportedChats.get(chat_id);
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
                    if (user.photo != null) {
                        oldUser.photo = user.photo;
                        oldUser.flags |= 32;
                    } else {
                        oldUser.flags = oldUser.flags &~ 32;
                        oldUser.photo = null;
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
                user.min = false;
                if (oldUser.bot) {
                    if (oldUser.username != null) {
                        user.username = oldUser.username;
                        user.flags |= 8;
                    } else {
                        user.flags = user.flags & ~8;
                        user.username = null;
                    }
                }
                if (oldUser.photo != null) {
                    user.photo = oldUser.photo;
                    user.flags |= 32;
                } else {
                    user.flags = user.flags &~ 32;
                    user.photo = null;
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
                }
            } else {
                chats.put(chat.id, chat);
            }
        } else {
            if (!fromCache) {
                if (oldChat != null) {
                    if (chat.version != oldChat.version) {
                        loadedFullChats.remove((Integer) chat.id);
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
                chat.min = false;
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

    public TLRPC.UserFull getUserFull(int uid) {
        return fullUsers.get(uid);
    }

    public TLRPC.ChatFull getChatFull(int chatId) {
        return fullChats.get(chatId);
    }

    public void cancelLoadFullUser(int uid) {
        loadingFullUsers.remove((Integer) uid);
    }

    public void cancelLoadFullChat(int cid) {
        loadingFullChats.remove((Integer) cid);
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
                TLRPC.InputPeer inputPeer = getInputPeer((int) dialogs.get(a).id);
                if (inputPeer instanceof TLRPC.TL_inputPeerChannel && inputPeer.access_hash == 0) {
                    continue;
                }
                TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                inputDialogPeer.peer = inputPeer;
                req.peers.add(inputDialogPeer);
            }
        } else {
            TLRPC.InputPeer inputPeer = getInputPeer((int) did);
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
                    if (dialog.read_inbox_max_id == 0) {
                        dialog.read_inbox_max_id = 1;
                    }
                    if (dialog.read_outbox_max_id == 0) {
                        dialog.read_outbox_max_id = 1;
                    }
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
                    if (value == 0) {
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

    public String getAdminRank(int chatId, int uid) {
        SparseArray<String> array = channelAdmins.get(chatId);
        if (array == null) {
            return null;
        }
        return array.get(uid);
    }

    public boolean isChannelAdminsLoaded(int chatId) {
        return channelAdmins.get(chatId) != null;
    }

    public void loadChannelAdmins(final int chatId, final boolean cache) {
        int loadTime = loadingChannelAdmins.get(chatId);
        if (SystemClock.uptimeMillis() - loadTime < 60) {
            return;
        }
        loadingChannelAdmins.put(chatId, (int) (SystemClock.uptimeMillis() / 1000));
        if (cache) {
            getMessagesStorage().loadChannelAdmins(chatId);
        } else {
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            /*SparseArray<String> array = channelAdmins.get(chatId);
            if (array != null) {
                ArrayList<Integer> values = new ArrayList<>();
                for (int a = 0; a < array.size(); a++) {
                    values.add(array.keyAt(a));
                }
                Collections.sort(values);
                long acc = 0;
                for (int a = 0; a < values.size(); a++) {
                    acc = ((acc * 20261) + 0x80000000L + values.get(a)) % 0x80000000L;
                }
                req.hash = (int) acc;
            }*/
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

    public void processLoadedAdminsResponse(int chatId, TLRPC.TL_channels_channelParticipants participants) {
        final SparseArray<String> array1 = new SparseArray<>(participants.participants.size());
        for (int a = 0; a < participants.participants.size(); a++) {
            TLRPC.ChannelParticipant participant = participants.participants.get(a);
            array1.put(participant.user_id, participant.rank != null ? participant.rank : "");
        }
        processLoadedChannelAdmins(array1, chatId, false);
    }

    public void processLoadedChannelAdmins(final SparseArray<String> array, final int chatId, final boolean cache) {
        if (!cache) {
            getMessagesStorage().putChannelAdmins(chatId, array);
        }
        AndroidUtilities.runOnUIThread(() -> {
            channelAdmins.put(chatId, array);
            if (cache) {
                loadingChannelAdmins.delete(chatId);
                loadChannelAdmins(chatId, false);
            }
        });
    }

    public void loadFullChat(final int chat_id, final int classGuid, boolean force) {
        boolean loaded = loadedFullChats.contains(chat_id);
        if (loadingFullChats.contains(chat_id) || !force && loaded) {
            return;
        }
        loadingFullChats.add(chat_id);
        TLObject request;
        final long dialog_id = -chat_id;
        final TLRPC.Chat chat = getChat(chat_id);
        if (ChatObject.isChannel(chat)) {
            TLRPC.TL_channels_getFullChannel req = new TLRPC.TL_channels_getFullChannel();
            req.channel = getInputChannel(chat);
            request = req;
            if (chat.megagroup) {
                loadChannelAdmins(chat_id, !loaded);
            }
        } else {
            TLRPC.TL_messages_getFullChat req = new TLRPC.TL_messages_getFullChat();
            req.chat_id = chat_id;
            request = req;
            if (dialogs_read_inbox_max.get(dialog_id) == null || dialogs_read_outbox_max.get(dialog_id) == null) {
                reloadDialogsReadValue(null, dialog_id);
            }
        }
        int reqId = getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error == null) {
                final TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull) response;
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                getMessagesStorage().updateChatInfo(res.full_chat, false);

                if (ChatObject.isChannel(chat)) {
                    Integer value = dialogs_read_inbox_max.get(dialog_id);
                    if (value == null) {
                        value = getMessagesStorage().getDialogReadMax(false, dialog_id);
                    }

                    dialogs_read_inbox_max.put(dialog_id, Math.max(res.full_chat.read_inbox_max_id, value));
                    if (value == 0) {
                        ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                        TLRPC.TL_updateReadChannelInbox update = new TLRPC.TL_updateReadChannelInbox();
                        update.channel_id = chat_id;
                        update.max_id = res.full_chat.read_inbox_max_id;
                        arrayList.add(update);
                        processUpdateArray(arrayList, null, null, false, 0);
                    }

                    value = dialogs_read_outbox_max.get(dialog_id);
                    if (value == null) {
                        value = getMessagesStorage().getDialogReadMax(true, dialog_id);
                    }
                    dialogs_read_outbox_max.put(dialog_id, Math.max(res.full_chat.read_outbox_max_id, value));
                    if (value == 0) {
                        ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                        TLRPC.TL_updateReadChannelOutbox update = new TLRPC.TL_updateReadChannelOutbox();
                        update.channel_id = chat_id;
                        update.max_id = res.full_chat.read_outbox_max_id;
                        arrayList.add(update);
                        processUpdateArray(arrayList, null, null, false, 0);
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    fullChats.put(chat_id, res.full_chat);
                    applyDialogNotificationsSettings(-chat_id, res.full_chat.notify_settings);
                    for (int a = 0; a < res.full_chat.bot_info.size(); a++) {
                        TLRPC.BotInfo botInfo = res.full_chat.bot_info.get(a);
                        getMediaDataController().putBotInfo(botInfo);
                    }
                    exportedChats.put(chat_id, res.full_chat.exported_invite);
                    loadingFullChats.remove((Integer) chat_id);
                    loadedFullChats.add(chat_id);

                    putUsers(res.users, false);
                    putChats(res.chats, false);
                    if (res.full_chat.stickerset != null) {
                        getMediaDataController().getGroupStickerSetById(res.full_chat.stickerset);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, res.full_chat, classGuid, false, null);
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    checkChannelError(error.text, chat_id);
                    loadingFullChats.remove((Integer) chat_id);
                });
            }
        });
        if (classGuid != 0) {
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void loadFullUser(final TLRPC.User user, final int classGuid, boolean force) {
        if (user == null || loadingFullUsers.contains(user.id) || !force && loadedFullUsers.contains(user.id)) {
            return;
        }
        loadingFullUsers.add(user.id);
        TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
        req.id = getInputUser(user);
        long dialog_id = user.id;
        if (dialogs_read_inbox_max.get(dialog_id) == null || dialogs_read_outbox_max.get(dialog_id) == null) {
            reloadDialogsReadValue(null, dialog_id);
        }
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.UserFull userFull = (TLRPC.UserFull) response;
                getMessagesStorage().updateUserInfo(userFull, false);

                AndroidUtilities.runOnUIThread(() -> {
                    savePeerSettings(userFull.user.id, userFull.settings, false);

                    applyDialogNotificationsSettings(user.id, userFull.notify_settings);
                    if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                        getMediaDataController().putBotInfo(userFull.bot_info);
                    }
                    int index = blockedUsers.indexOfKey(user.id);
                    if (userFull.blocked) {
                        if (index < 0) {
                            blockedUsers.put(user.id, 1);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    } else {
                        if (index >= 0) {
                            blockedUsers.removeAt(index);
                            getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                        }
                    }
                    fullUsers.put(user.id, userFull);
                    loadingFullUsers.remove((Integer) user.id);
                    loadedFullUsers.add(user.id);
                    String names = user.first_name + user.last_name + user.username;
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(userFull.user);
                    putUsers(users, false);
                    getMessagesStorage().putUsersAndChats(users, null, false, true);
                    if (names != null && !names.equals(userFull.user.first_name + userFull.user.last_name + userFull.user.username)) {
                        getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_NAME);
                    }
                    if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                        getNotificationCenter().postNotificationName(NotificationCenter.botInfoDidLoad, userFull.bot_info, classGuid);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, user.id, userFull, null);
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> loadingFullUsers.remove((Integer) user.id));
            }
        });
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void reloadMessages(final ArrayList<Integer> mids, final long dialog_id, boolean scheduled) {
        if (mids.isEmpty()) {
            return;
        }
        TLObject request;
        final ArrayList<Integer> result = new ArrayList<>();
        final TLRPC.Chat chat = ChatObject.getChatByDialog(dialog_id, currentAccount);
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
        ArrayList<Integer> arrayList = reloadingMessages.get(dialog_id);
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
            reloadingMessages.put(dialog_id, arrayList);
        }
        arrayList.addAll(result);
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error == null) {
                TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;

                final SparseArray<TLRPC.User> usersLocal = new SparseArray<>();
                for (int a = 0; a < messagesRes.users.size(); a++) {
                    TLRPC.User u = messagesRes.users.get(a);
                    usersLocal.put(u.id, u);
                }
                final SparseArray<TLRPC.Chat> chatsLocal = new SparseArray<>();
                for (int a = 0; a < messagesRes.chats.size(); a++) {
                    TLRPC.Chat c = messagesRes.chats.get(a);
                    chatsLocal.put(c.id, c);
                }

                Integer inboxValue = dialogs_read_inbox_max.get(dialog_id);
                if (inboxValue == null) {
                    inboxValue = getMessagesStorage().getDialogReadMax(false, dialog_id);
                    dialogs_read_inbox_max.put(dialog_id, inboxValue);
                }

                Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                if (outboxValue == null) {
                    outboxValue = getMessagesStorage().getDialogReadMax(true, dialog_id);
                    dialogs_read_outbox_max.put(dialog_id, outboxValue);
                }

                final ArrayList<MessageObject> objects = new ArrayList<>();
                for (int a = 0; a < messagesRes.messages.size(); a++) {
                    TLRPC.Message message = messagesRes.messages.get(a);
                    if (chat != null && chat.megagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }
                    message.dialog_id = dialog_id;
                    if (!scheduled) {
                        message.unread = (message.out ? outboxValue : inboxValue) < message.id;
                    }
                    objects.add(new MessageObject(currentAccount, message, usersLocal, chatsLocal, true));
                }

                ImageLoader.saveMessagesThumbs(messagesRes.messages);
                getMessagesStorage().putMessages(messagesRes, dialog_id, -1, 0, false, scheduled);

                AndroidUtilities.runOnUIThread(() -> {
                    ArrayList<Integer> arrayList1 = reloadingMessages.get(dialog_id);
                    if (arrayList1 != null) {
                        arrayList1.removeAll(result);
                        if (arrayList1.isEmpty()) {
                            reloadingMessages.remove(dialog_id);
                        }
                    }
                    MessageObject dialogObj = dialogMessage.get(dialog_id);
                    if (dialogObj != null) {
                        for (int a = 0; a < objects.size(); a++) {
                            MessageObject obj = objects.get(a);
                            if (dialogObj != null && dialogObj.getId() == obj.getId()) {
                                dialogMessage.put(dialog_id, obj);
                                if (obj.messageOwner.to_id.channel_id == 0) {
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
                    getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, objects);
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
        editor.commit();
        if ((int) dialogId != 0) {
            TLRPC.TL_messages_hidePeerSettingsBar req = new TLRPC.TL_messages_hidePeerSettingsBar();
            if (currentUser != null) {
                req.peer = getInputPeer(currentUser.id);
            } else if (currentChat != null) {
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
        if ((int) dialogId == 0) {
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
            TLRPC.TL_account_reportPeer req = new TLRPC.TL_account_reportPeer();
            if (currentChat != null) {
                req.peer = getInputPeer(-currentChat.id);
            } else if (currentUser != null) {
                req.peer = getInputPeer(currentUser.id);
            }
            if (geo) {
                req.reason = new TLRPC.TL_inputReportReasonGeoIrrelevant();
            } else {
                req.reason = new TLRPC.TL_inputReportReasonSpam();
            }
            getConnectionsManager().sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    private void savePeerSettings(long dialogId, TLRPC.TL_peerSettings settings, boolean update) {
        if (settings == null || notificationsPreferences.getInt("dialog_bar_vis3" + dialogId, 0) == 3) {
            return;
        }
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        boolean bar_hidden = !settings.report_spam && !settings.add_contact && !settings.block_contact && !settings.share_contact && !settings.report_geo;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("peer settings loaded for " + dialogId + " add = " + settings.add_contact + " block = " + settings.block_contact + " spam = " + settings.report_spam + " share = " + settings.share_contact + " geo = " + settings.report_geo +  " hide = " + bar_hidden);
        }
        editor.putInt("dialog_bar_vis3" + dialogId, bar_hidden ? 1 : 2);
        editor.putBoolean("dialog_bar_share" + dialogId, settings.share_contact);
        editor.putBoolean("dialog_bar_report" + dialogId, settings.report_spam);
        editor.putBoolean("dialog_bar_add" + dialogId, settings.add_contact);
        editor.putBoolean("dialog_bar_block" + dialogId, settings.block_contact);
        editor.putBoolean("dialog_bar_exception" + dialogId, settings.need_contacts_exception);
        editor.putBoolean("dialog_bar_location" + dialogId, settings.report_geo);
        editor.commit();
        getNotificationCenter().postNotificationName(NotificationCenter.peerSettingsDidLoad, dialogId);
    }

    public void loadPeerSettings(TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (currentUser == null && currentChat == null) {
            return;
        }
        final long dialogId;
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
        } else if (currentChat != null) {
            req.peer = getInputPeer(-currentChat.id);
        }
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingPeerSettings.remove(dialogId);
            if (response != null) {
                savePeerSettings(dialogId, (TLRPC.TL_peerSettings) response, false);
            }
        }));
    }

    protected void processNewChannelDifferenceParams(int pts, int pts_count, int channelId) {
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
            boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId);
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

    protected void processNewDifferenceParams(int seq, int pts, int date, int pts_count) {
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

    public void didAddedNewTask(final int minDate, final SparseArray<ArrayList<Long>> mids) {
        Utilities.stageQueue.postRunnable(() -> {
            if (currentDeletingTaskMids == null && !gettingNewDeleteTask || currentDeletingTaskTime != 0 && minDate < currentDeletingTaskTime) {
                getNewDeleteTask(null, 0);
            }
        });
        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didCreatedNewDeleteTask, mids));
    }

    public void getNewDeleteTask(final ArrayList<Integer> oldTask, final int channelId) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingNewDeleteTask = true;
            getMessagesStorage().getNewTask(oldTask, channelId);
        });
    }

    private boolean checkDeletingTask(boolean runnable) {
        int currentServerTime = getConnectionsManager().getCurrentTime();

        if (currentDeletingTaskMids != null && (runnable || currentDeletingTaskTime != 0 && currentDeletingTaskTime <= currentServerTime)) {
            currentDeletingTaskTime = 0;
            if (currentDeleteTaskRunnable != null && !runnable) {
                Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            }
            currentDeleteTaskRunnable = null;
            final ArrayList<Integer> mids = new ArrayList<>(currentDeletingTaskMids);
            AndroidUtilities.runOnUIThread(() -> {
                if (!mids.isEmpty() && mids.get(0) > 0) {
                    getMessagesStorage().emptyMessagesMedia(mids);
                } else {
                    deleteMessages(mids, null, null, 0, 0, false, false);
                }
                Utilities.stageQueue.postRunnable(() -> {
                    getNewDeleteTask(mids, currentDeletingTaskChannelId);
                    currentDeletingTaskTime = 0;
                    currentDeletingTaskMids = null;
                });
            });
            return true;
        }
        return false;
    }

    public void processLoadedDeleteTask(final int taskTime, final ArrayList<Integer> messages, final int channelId) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingNewDeleteTask = false;
            if (messages != null) {
                currentDeletingTaskTime = taskTime;
                currentDeletingTaskMids = messages;

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
            }
        });
    }

    public void loadDialogPhotos(final int did, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (fromCache) {
            getMessagesStorage().getDialogPhotos(did, count, max_id, classGuid);
        } else {
            if (did > 0) {
                TLRPC.User user = getUser(did);
                if (user == null) {
                    return;
                }
                TLRPC.TL_photos_getUserPhotos req = new TLRPC.TL_photos_getUserPhotos();
                req.limit = count;
                req.offset = 0;
                req.max_id = (int) max_id;
                req.user_id = getInputUser(user);
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.photos_Photos res = (TLRPC.photos_Photos) response;
                        processLoadedUserPhotos(res, did, count, max_id, false, classGuid);
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            } else if (did < 0) {
                TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.filter = new TLRPC.TL_inputMessagesFilterChatPhotos();
                req.limit = count;
                req.offset_id = (int) max_id;
                req.q = "";
                req.peer = getInputPeer(did);
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.messages_Messages messages = (TLRPC.messages_Messages) response;
                        TLRPC.TL_photos_photos res = new TLRPC.TL_photos_photos();
                        res.count = messages.count;
                        res.users.addAll(messages.users);
                        for (int a = 0; a < messages.messages.size(); a++) {
                            TLRPC.Message message = messages.messages.get(a);
                            if (message.action == null || message.action.photo == null) {
                                continue;
                            }
                            res.photos.add(message.action.photo);
                        }
                        processLoadedUserPhotos(res, did, count, max_id, false, classGuid);
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public void blockUser(int user_id) {
        final TLRPC.User user = getUser(user_id);
        if (user == null || blockedUsers.indexOfKey(user_id) >= 0) {
            return;
        }
        blockedUsers.put(user_id, 1);
        if (user.bot) {
            getMediaDataController().removeInline(user_id);
        } else {
            getMediaDataController().removePeer(user_id);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
        TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
        req.id = getInputUser(user);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public void setUserBannedRole(final int chatId, TLRPC.User user, TLRPC.TL_chatBannedRights rights, final boolean isChannel, final BaseFragment parentFragment) {
        if (user == null || rights == null) {
            return;
        }
        final TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
        req.channel = getInputChannel(chatId);
        req.user_id = getInputUser(user);
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

    public void setChannelSlowMode(int chatId, int seconds) {
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

    public void setDefaultBannedRole(final int chatId, TLRPC.TL_chatBannedRights rights, final boolean isChannel, final BaseFragment parentFragment) {
        if (rights == null) {
            return;
        }
        final TLRPC.TL_messages_editChatDefaultBannedRights req = new TLRPC.TL_messages_editChatDefaultBannedRights();
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

    public void setUserAdminRole(final int chatId, TLRPC.User user, TLRPC.TL_chatAdminRights rights, String rank, final boolean isChannel, final BaseFragment parentFragment, boolean addingNew) {
        if (user == null || rights == null) {
            return;
        }
        TLRPC.Chat chat = getChat(chatId);
        if (ChatObject.isChannel(chat)) {
            final TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
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
            req.is_admin = rights.change_info || rights.delete_messages || rights.ban_users || rights.invite_users || rights.pin_messages || rights.add_admins;
            RequestDelegate requestDelegate = (response, error) -> {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
                } else {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, parentFragment, req, false));
                }
            };
            if (req.is_admin && addingNew) {
                addUserToChat(chatId, user, null, 0, null, parentFragment, () -> getConnectionsManager().sendRequest(req, requestDelegate));
            } else {
                getConnectionsManager().sendRequest(req, requestDelegate);
            }
        }
    }

    public void unblockUser(int user_id) {
        TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
        final TLRPC.User user = getUser(user_id);
        if (user == null) {
            return;
        }
        blockedUsers.delete(user.id);
        req.id = getInputUser(user);
        getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public void getBlockedUsers(boolean reset) {
        if (!getUserConfig().isClientActivated() || loadingBlockedUsers) {
            return;
        }
        loadingBlockedUsers = true;
        TLRPC.TL_contacts_getBlocked req = new TLRPC.TL_contacts_getBlocked();
        req.offset = reset ? 0 : blockedUsers.size();
        req.limit = reset ? 20 : 100;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.contacts_Blocked res = (TLRPC.contacts_Blocked) response;
                putUsers(res.users, false);
                getMessagesStorage().putUsersAndChats(res.users, null, true, true);
                if (reset) {
                    blockedUsers.clear();
                }
                totalBlockedCount = Math.max(res.count, res.blocked.size());
                blockedEndReached = res.blocked.size() < req.limit;
                for (int a = 0, N = res.blocked.size(); a < N; a++) {
                    TLRPC.TL_contactBlocked blocked = res.blocked.get(a);
                    blockedUsers.put(blocked.user_id, 1);
                }
                loadingBlockedUsers = false;
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
                    user1.photo = (TLRPC.UserProfilePhoto) response;
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

    public void processLoadedUserPhotos(final TLRPC.photos_Photos res, final int did, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (!fromCache) {
            getMessagesStorage().putUsersAndChats(res.users, null, true, true);
            getMessagesStorage().putDialogPhotos(did, res);
        } else if (res == null || res.photos.isEmpty()) {
            loadDialogPhotos(did, count, max_id, false, classGuid);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            putUsers(res.users, fromCache);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogPhotosLoaded, did, count, fromCache, classGuid, res.photos);
        });
    }

    public void uploadAndApplyUserAvatar(TLRPC.FileLocation location) {
        if (location == null) {
            return;
        }
        uploadingAvatar = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + location.volume_id + "_" + location.local_id + ".jpg";
        getFileLoader().uploadFile(uploadingAvatar, false, true, ConnectionsManager.FileTypePhoto);
    }

    public void saveTheme(Theme.ThemeInfo themeInfo, boolean night, boolean unsave) {
        if (themeInfo.info != null) {
            TLRPC.TL_account_saveTheme req = new TLRPC.TL_account_saveTheme();
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.id = themeInfo.info.id;
            inputTheme.access_hash = themeInfo.info.access_hash;
            req.theme = inputTheme;
            req.unsave = unsave;
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
        if (!unsave) {
            installTheme(themeInfo, night);
        }
    }

    public void installTheme(Theme.ThemeInfo themeInfo, boolean night) {
        TLRPC.TL_account_installTheme req = new TLRPC.TL_account_installTheme();
        req.dark = night;
        if (themeInfo.info != null) {
            req.format = "android";
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.id = themeInfo.info.id;
            inputTheme.access_hash = themeInfo.info.access_hash;
            req.theme = inputTheme;
            req.flags |= 2;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });

        if (!TextUtils.isEmpty(themeInfo.slug)) {
            TLRPC.TL_account_installWallPaper req2 = new TLRPC.TL_account_installWallPaper();
            TLRPC.TL_inputWallPaperSlug inputWallPaperSlug = new TLRPC.TL_inputWallPaperSlug();
            inputWallPaperSlug.slug = themeInfo.slug;
            req2.wallpaper = inputWallPaperSlug;
            req2.settings = new TLRPC.TL_wallPaperSettings();
            req2.settings.blur = themeInfo.isBlured;
            req2.settings.motion = themeInfo.isMotion;
            getConnectionsManager().sendRequest(req2, (response, error) -> {

            });
        }
    }

    public void saveThemeToServer(Theme.ThemeInfo themeInfo) {
        if (themeInfo == null) {
            return;
        }
        if (uploadingThemes.containsKey(themeInfo.pathToFile)) {
            return;
        }
        uploadingThemes.put(themeInfo.pathToFile, themeInfo);
        Utilities.globalQueue.postRunnable(() -> {
            String thumbPath = Theme.createThemePreviewImage(themeInfo);
            AndroidUtilities.runOnUIThread(() -> {
                if (thumbPath == null) {
                    uploadingThemes.remove(themeInfo.pathToFile);
                    return;
                }
                themeInfo.uploadingFile = themeInfo.pathToFile;
                themeInfo.uploadingThumb = thumbPath;
                uploadingThemes.put(thumbPath, themeInfo);
                File f = new File(themeInfo.pathToFile);
                long l = f.length();
                File f2 = new File(thumbPath);
                long l2 = f2.length();
                getFileLoader().uploadFile(themeInfo.pathToFile, false, true, ConnectionsManager.FileTypeFile);
                getFileLoader().uploadFile(thumbPath, false, true, ConnectionsManager.FileTypePhoto);
            });
        });
    }

    public void saveWallpaperToServer(File path, long wallPaperId, String slug, long accessHash, boolean isBlurred, boolean isMotion, int backgroundColor, float intesity, boolean install, long taskId) {
        if (uploadingWallpaper != null) {
            File finalPath = new File(ApplicationLoader.getFilesDirFixed(), uploadingWallpaperBlurred ? "wallpaper_original.jpg" : "wallpaper.jpg");
            if (path != null && (path.getAbsolutePath().equals(uploadingWallpaper) || path.equals(finalPath))) {
                uploadingWallpaperMotion = isMotion;
                uploadingWallpaperBlurred = isBlurred;
                return;
            }
            getFileLoader().cancelUploadFile(uploadingWallpaper, false);
            uploadingWallpaper = null;
        }
        if (path != null) {
            uploadingWallpaper = path.getAbsolutePath();
            uploadingWallpaperMotion = isMotion;
            uploadingWallpaperBlurred = isBlurred;
            getFileLoader().uploadFile(uploadingWallpaper, false, true, ConnectionsManager.FileTypePhoto);
        } else if (accessHash != 0) {
            TLRPC.TL_inputWallPaper inputWallPaper = new TLRPC.TL_inputWallPaper();
            inputWallPaper.id = wallPaperId;
            inputWallPaper.access_hash = accessHash;

            TLRPC.TL_wallPaperSettings settings = new TLRPC.TL_wallPaperSettings();
            settings.blur = isBlurred;
            settings.motion = isMotion;
            if (backgroundColor != 0) {
                settings.background_color = backgroundColor;
                settings.flags |= 1;
                settings.intensity = (int) (intesity * 100);
                settings.flags |= 8;
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

            final long newTaskId;
            if (taskId != 0) {
                newTaskId = taskId;
            } else {
                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(1024);
                    data.writeInt32(19);
                    data.writeInt64(wallPaperId);
                    data.writeInt64(accessHash);
                    data.writeBool(isBlurred);
                    data.writeBool(isMotion);
                    data.writeInt32(backgroundColor);
                    data.writeDouble(intesity);
                    data.writeBool(install);
                    if (slug != null) {
                        data.writeString(slug);
                    } else {
                        data.writeString("");
                    }
                    data.limit(data.position());
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = getMessagesStorage().createPendingTask(data);
            }

            getConnectionsManager().sendRequest(req, (response, error) -> {
                getMessagesStorage().removePendingTask(newTaskId);
                if (!install && uploadingWallpaper != null) {
                    SharedPreferences preferences = getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putLong("selectedBackground2", wallPaperId);
                    if (!TextUtils.isEmpty(slug)) {
                        editor.putString("selectedBackgroundSlug", slug);
                    } else {
                        editor.remove("selectedBackgroundSlug");
                    }
                    editor.commit();
                }
            });
        }
    }

    public void markChannelDialogMessageAsDeleted(ArrayList<Integer> messages, final int channelId) {
        MessageObject obj = dialogMessage.get((long) -channelId);
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

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, final long dialogId, final int channelId, boolean forAll, boolean scheduled) {
        deleteMessages(messages, randoms, encryptedChat, dialogId, channelId, forAll, scheduled, 0, null);
    }

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, final long dialogId, final int channelId, boolean forAll, boolean scheduled, long taskId, TLObject taskRequest) {
        if ((messages == null || messages.isEmpty()) && taskRequest == null) {
            return;
        }
        ArrayList<Integer> toSend = null;
        if (taskId == 0) {
            toSend = new ArrayList<>();
            for (int a = 0; a < messages.size(); a++) {
                Integer mid = messages.get(a);
                if (mid > 0) {
                    toSend.add(mid);
                }
            }
            if (scheduled) {
                getMessagesStorage().markMessagesAsDeleted(messages, true, channelId, false, true);
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
                    markChannelDialogMessageAsDeleted(messages, channelId);
                }
                getMessagesStorage().markMessagesAsDeleted(messages, true, channelId, forAll, false);
                getMessagesStorage().updateDialogsWithDeletedMessages(messages, null, true, channelId);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, messages, channelId, scheduled);
        }

        final long newTaskId;
        if (scheduled) {
            TLRPC.TL_messages_deleteScheduledMessages req;

            if (taskRequest != null) {
                req = (TLRPC.TL_messages_deleteScheduledMessages) taskRequest;
                newTaskId = taskId;
            } else {
                req = new TLRPC.TL_messages_deleteScheduledMessages();
                req.id = toSend;
                req.peer = getInputPeer((int) dialogId);

                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(16 + req.getObjectSize());
                    data.writeInt32(18);
                    data.writeInt64(dialogId);
                    data.writeInt32(channelId);
                    req.serializeToStream(data);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    processUpdates(updates, false);
                }
                if (newTaskId != 0) {
                    MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
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
                    data = new NativeByteBuffer(8 + req.getObjectSize());
                    data.writeInt32(7);
                    data.writeInt32(channelId);
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
            if (taskRequest != null) {
                req = (TLRPC.TL_messages_deleteMessages) taskRequest;
                newTaskId = taskId;
            } else {
                req = new TLRPC.TL_messages_deleteMessages();
                req.id = toSend;
                req.revoke = forAll;

                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(8 + req.getObjectSize());
                    data.writeInt32(7);
                    data.writeInt32(channelId);
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

    public void pinMessage(TLRPC.Chat chat, TLRPC.User user, int id, boolean notify) {
        if (chat == null && user == null) {
            return;
        }
        TLRPC.TL_messages_updatePinnedMessage req = new TLRPC.TL_messages_updatePinnedMessage();
        req.peer = getInputPeer(chat != null ? -chat.id : user.id);
        req.id = id;
        req.silent = !notify;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
            }
        });
    }

    public void deleteUserChannelHistory(final TLRPC.Chat chat, final TLRPC.User user, int offset) {
        if (offset == 0) {
            getMessagesStorage().deleteUserChannelHistory(chat.id, user.id);
        }
        TLRPC.TL_channels_deleteUserHistory req = new TLRPC.TL_channels_deleteUserHistory();
        req.channel = getInputChannel(chat);
        req.user_id = getInputUser(user);
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

    private void removeDialog(TLRPC.Dialog dialog) {
        if (dialog == null) {
            return;
        }
        long did = dialog.id;
        if (dialogsServerOnly.remove(dialog) && DialogObject.isChannel(dialog)) {
            Utilities.stageQueue.postRunnable(() -> {
                channelsPts.delete(-(int) did);
                shortPollChannels.delete(-(int) did);
                needShortPollChannels.delete(-(int) did);
                shortPollOnlines.delete(-(int) did);
                needShortPollOnlines.delete(-(int) did);
            });
        }
        allDialogs.remove(dialog);
        dialogsCanAddUsers.remove(dialog);
        dialogsChannelsOnly.remove(dialog);
        dialogsGroupsOnly.remove(dialog);
        dialogsUsersOnly.remove(dialog);
        dialogsForward.remove(dialog);
        dialogs_dict.remove(did);
        dialogs_read_inbox_max.remove(did);
        dialogs_read_outbox_max.remove(did);

        ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.get(dialog.folder_id);
        if (dialogs != null) {
            dialogs.remove(dialog);
        }
    }

    public void deleteDialog(final long did, final int onlyHistory) {
        deleteDialog(did, onlyHistory, false);
    }

    public void deleteDialog(final long did, final int onlyHistory, boolean revoke) {
        deleteDialog(did, true, onlyHistory, 0, revoke, null, 0);
    }

    public void setDialogsInTransaction(boolean transaction) {
        dialogsInTransaction = transaction;
        if (!transaction) {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
        }
    }

    protected void deleteDialog(final long did, final boolean first, final int onlyHistory, final int max_id, boolean revoke, TLRPC.InputPeer peer, final long taskId) {
        if (onlyHistory == 2) {
            getMessagesStorage().deleteDialog(did, onlyHistory);
            return;
        }
        if (onlyHistory == 0 || onlyHistory == 3) {
            getMediaDataController().uninstallShortcut(did);
        }
        int lower_part = (int) did;
        int high_id = (int) (did >> 32);
        int max_id_delete = max_id;

        if (first) {
            boolean isProxyDialog = false;
            getMessagesStorage().deleteDialog(did, onlyHistory);
            TLRPC.Dialog dialog = dialogs_dict.get(did);
            if (onlyHistory == 0 || onlyHistory == 3) {
                getNotificationsController().deleteNotificationChannel(did);
            }
            if (dialog != null) {
                if (max_id_delete == 0) {
                    max_id_delete = Math.max(0, dialog.top_message);
                    max_id_delete = Math.max(max_id_delete, dialog.read_inbox_max_id);
                    max_id_delete = Math.max(max_id_delete, dialog.read_outbox_max_id);
                }
                if (onlyHistory == 0 || onlyHistory == 3) {
                    if (isProxyDialog = (proxyDialog != null && proxyDialog.id == did)) {
                        isLeftProxyChannel = true;
                        if (proxyDialog.id < 0) {
                            TLRPC.Chat chat = getChat(-(int) proxyDialog.id);
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
                if (!isProxyDialog) {
                    int lastMessageId;
                    MessageObject object = dialogMessage.get(dialog.id);
                    dialogMessage.remove(dialog.id);
                    if (object != null) {
                        lastMessageId = object.getId();
                        dialogMessagesByIds.remove(object.getId());
                    } else {
                        lastMessageId = dialog.top_message;
                        object = dialogMessagesByIds.get(dialog.top_message);
                        dialogMessagesByIds.remove(dialog.top_message);
                    }
                    if (object != null && object.messageOwner.random_id != 0) {
                        dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                    }
                    if (onlyHistory == 1 && lower_part != 0 && lastMessageId > 0) {
                        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
                        message.id = dialog.top_message;
                        message.out = getUserConfig().getClientUserId() == did;
                        message.from_id = getUserConfig().getClientUserId();
                        message.flags |= 256;
                        message.action = new TLRPC.TL_messageActionHistoryClear();
                        message.date = dialog.last_message_date;
                        message.dialog_id = lower_part;
                        if (lower_part > 0) {
                            message.to_id = new TLRPC.TL_peerUser();
                            message.to_id.user_id = lower_part;
                        } else {
                            TLRPC.Chat chat = getChat(-lower_part);
                            if (ChatObject.isChannel(chat)) {
                                message.to_id = new TLRPC.TL_peerChannel();
                                message.to_id.channel_id = -lower_part;
                            } else {
                                message.to_id = new TLRPC.TL_peerChat();
                                message.to_id.chat_id = -lower_part;
                            }
                        }
                        final MessageObject obj = new MessageObject(currentAccount, message, createdDialogIds.contains(message.dialog_id));
                        final ArrayList<MessageObject> objArr = new ArrayList<>();
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
            if (!dialogsInTransaction) {
                if (isProxyDialog) {
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                } else {
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    getNotificationCenter().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, false);
                }
            }
            getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().removeNotificationsForDialog(did)));
        }

        if (onlyHistory == 3) {
            return;
        }

        if (lower_part != 0) {
            if (peer == null) {
                peer = getInputPeer(lower_part);
            }
            if (peer == null) {
                return;
            }

            final long newTaskId;
            if (!(peer instanceof TLRPC.TL_inputPeerChannel) || onlyHistory != 0) {
                if (max_id_delete > 0 && max_id_delete != Integer.MAX_VALUE) {
                    deletedHistory.put(did, max_id_delete);
                }

                if (taskId == 0) {
                    NativeByteBuffer data = null;
                    try {
                        data = new NativeByteBuffer(4 + 8 + 4 + 4 + 4 + 4 + peer.getObjectSize());
                        data.writeInt32(13);
                        data.writeInt64(did);
                        data.writeBool(first);
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
                    AndroidUtilities.runOnUIThread(() -> deletedHistory.remove(did));
                }, ConnectionsManager.RequestFlagInvokeAfter);
            } else {
                TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
                req.peer = peer;
                req.max_id = (onlyHistory == 0 ? Integer.MAX_VALUE : max_id_delete);
                req.just_clear = onlyHistory != 0;
                req.revoke = revoke;
                final int max_id_delete_final = max_id_delete;
                final TLRPC.InputPeer peerFinal = peer;
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (newTaskId != 0) {
                        getMessagesStorage().removePendingTask(newTaskId);
                    }
                    if (error == null) {
                        TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                        if (res.offset > 0) {
                            deleteDialog(did, false, onlyHistory, max_id_delete_final, revoke, peerFinal, 0);
                        }
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                        getMessagesStorage().onDeleteQueryComplete(did);
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            }
        } else {
            if (onlyHistory == 1) {
                getSecretChatHelper().sendClearHistoryMessage(getEncryptedChat(high_id), null);
            } else {
                getSecretChatHelper().declineSecretChat(high_id);
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
            if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
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
            if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
                getFileRefController().requestReference(parentObject, req);
            }
        });
    }

    public void loadChannelParticipants(final Integer chat_id) {
        if (loadingFullParticipants.contains(chat_id) || loadedFullParticipants.contains(chat_id)) {
            return;
        }
        loadingFullParticipants.add(chat_id);

        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = getInputChannel(chat_id);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = 0;
        req.limit = 32;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                putUsers(res.users, false);
                getMessagesStorage().putUsersAndChats(res.users, null, true, true);
                getMessagesStorage().updateChannelUsers(chat_id, res.participants);
                loadedFullParticipants.add(chat_id);
            }
            loadingFullParticipants.remove(chat_id);
        }));
    }

    public void processChatInfo(int chat_id, final TLRPC.ChatFull info, final ArrayList<TLRPC.User> usersArr, final boolean fromCache, boolean force, final boolean byChannelUsers, final MessageObject pinnedMessageObject) {
        AndroidUtilities.runOnUIThread(() -> {
            if (fromCache && chat_id > 0 && !byChannelUsers) {
                loadFullChat(chat_id, 0, force);
            }
            if (info != null) {
                if (fullChats.get(chat_id) == null) {
                    fullChats.put(chat_id, info);
                }
                putUsers(usersArr, fromCache);
                if (info.stickerset != null) {
                    getMediaDataController().getGroupStickerSetById(info.stickerset);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, byChannelUsers, pinnedMessageObject);
            }
        });
    }

    public void loadUserInfo(TLRPC.User user, boolean force, int classGuid) {
        getMessagesStorage().loadUserInfo(user, force, classGuid);
    }

    public void processUserInfo(TLRPC.User user, final TLRPC.UserFull info, final boolean fromCache, boolean force, final MessageObject pinnedMessageObject, int classGuid) {
        AndroidUtilities.runOnUIThread(() -> {
            if (fromCache) {
                loadFullUser(user, classGuid, force);
            }
            if (info != null) {
                if (fullUsers.get(user.id) == null) {
                    fullUsers.put(user.id, info);
                    if (info.blocked) {
                        blockedUsers.put(user.id, 1);
                    } else {
                        blockedUsers.delete(user.id);
                    }
                }
                getNotificationCenter().postNotificationName(NotificationCenter.userInfoDidLoad, user.id, info, pinnedMessageObject);
            }
        });
    }

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();

        checkDeletingTask(false);
        checkReadTasks();

        if (getUserConfig().isClientActivated()) {
            if (getConnectionsManager().getPauseTime() == 0 && ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePausedStageQueue) {
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
                    int key = updatesQueueChannels.keyAt(a);
                    long updatesStartWaitTime = updatesStartWaitTimeChannels.valueAt(a);
                    if (updatesStartWaitTime + 1500 < currentTime) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("QUEUE CHANNEL " + key + " UPDATES WAIT TIMEOUT - CHECK QUEUE");
                        }
                        processChannelsUpdatesQueue(key, 0);
                    }
                }
            }

            for (int a = 0; a < 3; a++) {
                if (getUpdatesStartTime(a) != 0 && getUpdatesStartTime(a) + 1500 < currentTime) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d(a + " QUEUE UPDATES WAIT TIMEOUT - CHECK QUEUE");
                    }
                    processUpdatesQueue(a, 0);
                }
            }
        }
        if (Math.abs(System.currentTimeMillis() - lastViewsCheckTime) >= 5000) {
            lastViewsCheckTime = System.currentTimeMillis();
            if (channelViewsToSend.size() != 0) {
                for (int a = 0; a < channelViewsToSend.size(); a++) {
                    final int key = channelViewsToSend.keyAt(a);
                    final TLRPC.TL_messages_getMessagesViews req = new TLRPC.TL_messages_getMessagesViews();
                    req.peer = getInputPeer(key);
                    req.id = channelViewsToSend.valueAt(a);
                    req.increment = a == 0;
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        if (response != null) {
                            TLRPC.Vector vector = (TLRPC.Vector) response;
                            final SparseArray<SparseIntArray> channelViews = new SparseArray<>();
                            SparseIntArray array = channelViews.get(key);
                            if (array == null) {
                                array = new SparseIntArray();
                                channelViews.put(key, array);
                            }
                            for (int a1 = 0; a1 < req.id.size(); a1++) {
                                if (a1 >= vector.objects.size()) {
                                    break;
                                }
                                array.put(req.id.get(a1), (Integer) vector.objects.get(a1));
                            }
                            getMessagesStorage().putChannelViews(channelViews, req.peer instanceof TLRPC.TL_inputPeerChannel);
                            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didUpdatedMessagesViews, channelViews));
                        }
                    });
                }
                channelViewsToSend.clear();
            }
            if (pollsToCheckSize > 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    long time = SystemClock.uptimeMillis();
                    for (int a = 0, N = pollsToCheck.size(); a < N; a++) {
                        SparseArray<MessageObject> array = pollsToCheck.valueAt(a);
                        if (array == null) {
                            continue;
                        }
                        for (int b = 0, N2 = array.size(); b < N2; b++) {
                            MessageObject messageObject = array.valueAt(b);
                            if (Math.abs(time - messageObject.pollLastCheckTime) < 30000) {
                                if (!messageObject.pollVisibleOnScreen) {
                                    array.remove(messageObject.getId());
                                    N2--;
                                    b--;
                                }
                            } else {
                                messageObject.pollLastCheckTime = time;
                                TLRPC.TL_messages_getPollResults req = new TLRPC.TL_messages_getPollResults();
                                req.peer = getInputPeer((int) messageObject.getDialogId());
                                req.msg_id = messageObject.getId();
                                getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (error == null) {
                                        processUpdates((TLRPC.Updates) response, false);
                                    }
                                });
                            }
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
            ArrayList<Integer> toRemove = null;
            int currentServerTime = getConnectionsManager().getCurrentTime();
            for (ConcurrentHashMap.Entry<Integer, Integer> entry : onlinePrivacy.entrySet()) {
                if (entry.getValue() < currentServerTime - 30) {
                    if (toRemove == null) {
                        toRemove = new ArrayList<>();
                    }
                    toRemove.add(entry.getKey());
                }
            }
            if (toRemove != null) {
                for (Integer uid : toRemove) {
                    onlinePrivacy.remove(uid);
                }
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS));
            }
        }
        if (shortPollChannels.size() != 0) {
            for (int a = 0; a < shortPollChannels.size(); a++) {
                int key = shortPollChannels.keyAt(a);
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
            long time = SystemClock.uptimeMillis() / 1000;
            for (int a = 0; a < shortPollOnlines.size(); a++) {
                int key = shortPollOnlines.keyAt(a);
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
            ArrayList<Long> keys = new ArrayList<>(printingUsers.keySet());
            for (int b = 0; b < keys.size(); b++) {
                long key = keys.get(b);
                ArrayList<PrintingUser> arr = printingUsers.get(key);
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
                    printingUsers.remove(key);
                    keys.remove(b);
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
        checkProxyInfoInternal(false);
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
                final TLRPC.TL_help_termsOfServiceUpdate res = (TLRPC.TL_help_termsOfServiceUpdate) response;
                nextTosCheckTime = res.expires;
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.needShowAlert, 4, res.terms_of_service));
            } else {
                nextTosCheckTime = getConnectionsManager().getCurrentTime() + 60 * 60;
            }
            notificationsPreferences.edit().putInt("nextTosCheckTime", nextTosCheckTime).commit();
        });
    }

    public void checkProxyInfo(final boolean reset) {
        Utilities.stageQueue.postRunnable(() -> checkProxyInfoInternal(reset));
    }

    private void checkProxyInfoInternal(boolean reset) {
        if (reset && checkingProxyInfo) {
            checkingProxyInfo = false;
        }
        if (!reset && nextProxyInfoCheckTime > getConnectionsManager().getCurrentTime() || checkingProxyInfo) {
            return;
        }
        if (checkingProxyInfoRequestId != 0) {
            getConnectionsManager().cancelRequest(checkingProxyInfoRequestId, true);
            checkingProxyInfoRequestId = 0;
        }
        SharedPreferences preferences = getGlobalMainSettings();
        boolean enabled = preferences.getBoolean("proxy_enabled", false);
        String proxyAddress = preferences.getString("proxy_ip", "");
        String proxySecret = preferences.getString("proxy_secret", "");
        int removeCurrent = 0;
        if (proxyDialogId != 0 && proxyDialogAddress != null && !proxyDialogAddress.equals(proxyAddress + proxySecret)) {
            removeCurrent = 1;
        }
        lastCheckProxyId++;
        if (enabled && !TextUtils.isEmpty(proxyAddress) && !TextUtils.isEmpty(proxySecret)) {
            checkingProxyInfo = true;
            int checkProxyId = lastCheckProxyId;
            TLRPC.TL_help_getProxyData req = new TLRPC.TL_help_getProxyData();
            checkingProxyInfoRequestId = getConnectionsManager().sendRequest(req, (response, error) -> {
                if (checkProxyId != lastCheckProxyId) {
                    return;
                }
                boolean noDialog = false;
                if (response instanceof TLRPC.TL_help_proxyDataEmpty) {
                    TLRPC.TL_help_proxyDataEmpty res = (TLRPC.TL_help_proxyDataEmpty) response;
                    nextProxyInfoCheckTime = res.expires;
                    noDialog = true;
                } else if (response instanceof TLRPC.TL_help_proxyDataPromo) {
                    final TLRPC.TL_help_proxyDataPromo res = (TLRPC.TL_help_proxyDataPromo) response;

                    final long did;
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
                    proxyDialogId = did;
                    proxyDialogAddress = proxyAddress + proxySecret;
                    getGlobalMainSettings().edit().putLong("proxy_dialog", proxyDialogId).putString("proxyDialogAddress", proxyDialogAddress).commit();
                    nextProxyInfoCheckTime = res.expires;
                    if (!noDialog) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (proxyDialog != null && did != proxyDialog.id) {
                                removeProxyDialog();
                            }
                            proxyDialog = dialogs_dict.get(did);

                            if (proxyDialog != null) {
                                checkingProxyInfo = false;
                                sortDialogs(null);
                                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                            } else {
                                final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
                                final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();
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
                                checkingProxyInfoRequestId = getConnectionsManager().sendRequest(req1, (response1, error1) -> {
                                    if (checkProxyId != lastCheckProxyId) {
                                        return;
                                    }
                                    checkingProxyInfoRequestId = 0;
                                    final TLRPC.TL_messages_peerDialogs res2 = (TLRPC.TL_messages_peerDialogs) response1;
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

                                            if (proxyDialog != null) {
                                                int lowerId = (int) proxyDialog.id;
                                                if (lowerId < 0) {
                                                    TLRPC.Chat chat = getChat(-lowerId);
                                                    if (ChatObject.isNotInChat(chat) || chat.restricted) {
                                                        removeDialog(proxyDialog);
                                                    }
                                                } else {
                                                    removeDialog(proxyDialog);
                                                }
                                            }

                                            proxyDialog = res2.dialogs.get(0);
                                            proxyDialog.id = did;
                                            proxyDialog.folder_id = 0;
                                            if (DialogObject.isChannel(proxyDialog)) {
                                                channelsPts.put(-(int) proxyDialog.id, proxyDialog.pts);
                                            }
                                            Integer value = dialogs_read_inbox_max.get(proxyDialog.id);
                                            if (value == null) {
                                                value = 0;
                                            }
                                            dialogs_read_inbox_max.put(proxyDialog.id, Math.max(value, proxyDialog.read_inbox_max_id));
                                            value = dialogs_read_outbox_max.get(proxyDialog.id);
                                            if (value == null) {
                                                value = 0;
                                            }
                                            dialogs_read_outbox_max.put(proxyDialog.id, Math.max(value, proxyDialog.read_outbox_max_id));
                                            dialogs_dict.put(did, proxyDialog);
                                            if (!res2.messages.isEmpty()) {
                                                final SparseArray<TLRPC.User> usersDict1 = new SparseArray<>();
                                                final SparseArray<TLRPC.Chat> chatsDict1 = new SparseArray<>();
                                                for (int a = 0; a < res2.users.size(); a++) {
                                                    TLRPC.User u = res2.users.get(a);
                                                    usersDict1.put(u.id, u);
                                                }
                                                for (int a = 0; a < res2.chats.size(); a++) {
                                                    TLRPC.Chat c = res2.chats.get(a);
                                                    chatsDict1.put(c.id, c);
                                                }
                                                MessageObject messageObject = new MessageObject(currentAccount, res2.messages.get(0), usersDict1, chatsDict1, false);
                                                dialogMessage.put(did, messageObject);
                                                if (proxyDialog.last_message_date == 0) {
                                                    proxyDialog.last_message_date = messageObject.messageOwner.date;
                                                }
                                            }
                                            sortDialogs(null);
                                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                                        });
                                    } else {
                                        AndroidUtilities.runOnUIThread(() -> {
                                            if (proxyDialog != null) {
                                                int lowerId = (int) proxyDialog.id;
                                                if (lowerId < 0) {
                                                    TLRPC.Chat chat = getChat(-lowerId);
                                                    if (ChatObject.isNotInChat(chat) || chat.restricted) {
                                                        removeDialog(proxyDialog);
                                                    }
                                                } else {
                                                    removeDialog(proxyDialog);
                                                }
                                                proxyDialog = null;
                                                sortDialogs(null);
                                                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                                            }
                                        });
                                    }
                                    checkingProxyInfo = false;
                                });
                            }
                        });
                    }
                } else {
                    nextProxyInfoCheckTime = getConnectionsManager().getCurrentTime() + 60 * 60;
                    noDialog = true;
                }
                if (noDialog) {
                    proxyDialogId = 0;
                    getGlobalMainSettings().edit().putLong("proxy_dialog", proxyDialogId).remove("proxyDialogAddress").commit();
                    checkingProxyInfoRequestId = 0;
                    checkingProxyInfo = false;
                    AndroidUtilities.runOnUIThread(this::removeProxyDialog);
                }
            });
        } else {
            removeCurrent = 2;
        }
        if (removeCurrent != 0) {
            proxyDialogId = 0;
            proxyDialogAddress = null;
            getGlobalMainSettings().edit().putLong("proxy_dialog", proxyDialogId).remove("proxyDialogAddress").commit();
            nextProxyInfoCheckTime = getConnectionsManager().getCurrentTime() + 60 * 60;
            if (removeCurrent == 2) {
                checkingProxyInfo = false;
                if (checkingProxyInfoRequestId != 0) {
                    getConnectionsManager().cancelRequest(checkingProxyInfoRequestId, true);
                    checkingProxyInfoRequestId = 0;
                }
            }
            AndroidUtilities.runOnUIThread(this::removeProxyDialog);
        }
    }

    private void removeProxyDialog() {
        if (proxyDialog == null) {
            return;
        }
        int lowerId = (int) proxyDialog.id;
        if (lowerId < 0) {
            TLRPC.Chat chat = getChat(-lowerId);
            if (ChatObject.isNotInChat(chat) || chat.restricted) {
                removeDialog(proxyDialog);
            }
        } else {
            removeDialog(proxyDialog);
        }
        proxyDialog = null;
        sortDialogs(null);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public boolean isProxyDialog(long did, boolean checkLeft) {
        return proxyDialog != null && proxyDialog.id == did && (!checkLeft || isLeftProxyChannel);
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
        final LongSparseArray<CharSequence> newPrintingStrings = new LongSparseArray<>();
        final LongSparseArray<Integer> newPrintingStringsTypes = new LongSparseArray<>();

        for (HashMap.Entry<Long, ArrayList<PrintingUser>> entry : printingUsers.entrySet()) {
            long key = entry.getKey();
            ArrayList<PrintingUser> arr = entry.getValue();

            int lower_id = (int) key;

            if (lower_id > 0 || lower_id == 0 || arr.size() == 1) {
                PrintingUser pu = arr.get(0);
                TLRPC.User user = getUser(pu.userId);
                if (user == null) {
                    continue;
                }
                if (pu.action instanceof TLRPC.TL_sendMessageRecordAudioAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsRecordingAudio", R.string.IsRecordingAudio, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("RecordingAudio", R.string.RecordingAudio));
                    }
                    newPrintingStringsTypes.put(key, 1);
                } else if (pu.action instanceof TLRPC.TL_sendMessageRecordRoundAction || pu.action instanceof TLRPC.TL_sendMessageUploadRoundAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsRecordingRound", R.string.IsRecordingRound, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("RecordingRound", R.string.RecordingRound));
                    }
                    newPrintingStringsTypes.put(key, 4);
                } else if (pu.action instanceof TLRPC.TL_sendMessageUploadAudioAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsSendingAudio", R.string.IsSendingAudio, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("SendingAudio", R.string.SendingAudio));
                    }
                    newPrintingStringsTypes.put(key, 2);
                } else if (pu.action instanceof TLRPC.TL_sendMessageUploadVideoAction || pu.action instanceof TLRPC.TL_sendMessageRecordVideoAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsSendingVideo", R.string.IsSendingVideo, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("SendingVideoStatus", R.string.SendingVideoStatus));
                    }
                    newPrintingStringsTypes.put(key, 2);
                } else if (pu.action instanceof TLRPC.TL_sendMessageUploadDocumentAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsSendingFile", R.string.IsSendingFile, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("SendingFile", R.string.SendingFile));
                    }
                    newPrintingStringsTypes.put(key, 2);
                } else if (pu.action instanceof TLRPC.TL_sendMessageUploadPhotoAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsSendingPhoto", R.string.IsSendingPhoto, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("SendingPhoto", R.string.SendingPhoto));
                    }
                    newPrintingStringsTypes.put(key, 2);
                } else if (pu.action instanceof TLRPC.TL_sendMessageGamePlayAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsSendingGame", R.string.IsSendingGame, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("SendingGame", R.string.SendingGame));
                    }
                    newPrintingStringsTypes.put(key, 3);
                } else {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsTypingGroup", R.string.IsTypingGroup, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("Typing", R.string.Typing));
                    }
                    newPrintingStringsTypes.put(key, 0);
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
                        newPrintingStrings.put(key, LocaleController.formatString("IsTypingGroup", R.string.IsTypingGroup, label.toString()));
                    } else {
                        if (arr.size() > 2) {
                            String plural = LocaleController.getPluralString("AndMoreTypingGroup", arr.size() - 2);
                            try {
                                newPrintingStrings.put(key, String.format(plural, label.toString(), arr.size() - 2));
                            } catch (Exception e) {
                                newPrintingStrings.put(key, "LOC_ERR: AndMoreTypingGroup");
                            }
                        } else {
                            newPrintingStrings.put(key, LocaleController.formatString("AreTypingGroup", R.string.AreTypingGroup, label.toString()));
                        }
                    }
                    newPrintingStringsTypes.put(key, 0);
                }
            }
        }

        lastPrintingStringCount = newPrintingStrings.size();

        AndroidUtilities.runOnUIThread(() -> {
            printingStrings = newPrintingStrings;
            printingStringsTypes = newPrintingStringsTypes;
        });
    }

    public void cancelTyping(int action, long dialog_id) {
        LongSparseArray<Boolean> typings = sendingTypings.get(action);
        if (typings != null) {
            typings.remove(dialog_id);
        }
    }

    public void sendTyping(final long dialog_id, final int action, int classGuid) {
        if (dialog_id == 0) {
            return;
        }
        LongSparseArray<Boolean> typings = sendingTypings.get(action);
        if (typings != null && typings.get(dialog_id) != null) {
            return;
        }
        if (typings == null) {
            typings = new LongSparseArray<>();
            sendingTypings.put(action, typings);
        }
        int lower_part = (int) dialog_id;
        int high_id = (int) (dialog_id >> 32);
        if (lower_part != 0) {
            TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
            req.peer = getInputPeer(lower_part);
            if (req.peer instanceof TLRPC.TL_inputPeerChannel) {
                TLRPC.Chat chat = getChat(req.peer.channel_id);
                if (chat == null || !chat.megagroup) {
                    return;
                }
            }
            if (req.peer == null) {
                return;
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
            }
            typings.put(dialog_id, true);
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                LongSparseArray<Boolean> typings1 = sendingTypings.get(action);
                if (typings1 != null) {
                    typings1.remove(dialog_id);
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
            if (classGuid != 0) {
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            }
        } else {
            if (action != 0) {
                return;
            }
            TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_setEncryptedTyping req = new TLRPC.TL_messages_setEncryptedTyping();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.typing = true;
                typings.put(dialog_id, true);
                int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    LongSparseArray<Boolean> typings12 = sendingTypings.get(action);
                    if (typings12 != null) {
                        typings12.remove(dialog_id);
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
                if (classGuid != 0) {
                    getConnectionsManager().bindRequestToGuid(reqId, classGuid);
                }
            }
        }
    }

    protected void removeDeletedMessagesFromArray(final long dialog_id, ArrayList<TLRPC.Message> messages) {
        int maxDeletedId = deletedHistory.get(dialog_id, 0);
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

    public void loadMessages(final long dialog_id, final int count, final int max_id, final int offset_date, boolean fromCache, int midDate, final int classGuid, final int load_type, final int last_message_id, final boolean isChannel, boolean scheduled, final int loadIndex) {
        loadMessages(dialog_id, count, max_id, offset_date, fromCache, midDate, classGuid, load_type, last_message_id, isChannel, scheduled, loadIndex, 0, 0, 0, false, 0);
    }

    public void loadMessages(final long dialog_id, final int count, final int max_id, final int offset_date, final boolean fromCache, final int midDate, final int classGuid, final int load_type, final int last_message_id, final boolean isChannel, boolean scheduled, final int loadIndex, final int first_unread, final int unread_count, final int last_date, final boolean queryFromServer, final int mentionsCount) {
        loadMessagesInternal(dialog_id, count, max_id, offset_date, fromCache, midDate, classGuid, load_type, last_message_id, isChannel, scheduled, loadIndex, first_unread, unread_count, last_date, queryFromServer, mentionsCount, true);
    }

    private void loadMessagesInternal(final long dialog_id, final int count, final int max_id, final int offset_date, final boolean fromCache, final int minDate, final int classGuid, final int load_type, final int last_message_id, final boolean isChannel, boolean scheduled, final int loadIndex, final int first_unread, final int unread_count, final int last_date, final boolean queryFromServer, final int mentionsCount, boolean loadDialog) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load messages in chat " + dialog_id + " count " + count + " max_id " + max_id + " cache " + fromCache + " mindate = " + minDate + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " scheduled " + scheduled + " index " + loadIndex + " firstUnread " + first_unread + " unread_count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        }
        int lower_part = (int) dialog_id;
        if (fromCache || lower_part == 0) {
            getMessagesStorage().getMessages(dialog_id, count, max_id, offset_date, minDate, classGuid, load_type, isChannel, scheduled, loadIndex);
        } else {
            if (scheduled) {
                TLRPC.TL_messages_getScheduledHistory req = new TLRPC.TL_messages_getScheduledHistory();
                req.peer = getInputPeer(lower_part);
                req.hash = minDate;
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (response != null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
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
                        processLoadedMessages(res, dialog_id, count, mid, offset_date, false, classGuid, first_unread, last_message_id, unread_count, last_date, load_type, isChannel, false, true, loadIndex, queryFromServer, mentionsCount);
                    }
                });
                ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
            } else {
                if (loadDialog && (load_type == 3 || load_type == 2) && last_message_id == 0) {
                    TLRPC.TL_messages_getPeerDialogs req = new TLRPC.TL_messages_getPeerDialogs();
                    TLRPC.InputPeer inputPeer = getInputPeer((int) dialog_id);
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
                                    getMessagesStorage().putDialogs(dialogs, 0);
                                }

                                loadMessagesInternal(dialog_id, count, max_id, offset_date, false, minDate, classGuid, load_type, dialog.top_message, isChannel, false, loadIndex, first_unread, dialog.unread_count, last_date, queryFromServer, dialog.unread_mentions_count, false);
                            }
                        }
                    });
                    return;
                }
                TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
                req.peer = getInputPeer(lower_part);
                if (load_type == 4) {
                    req.add_offset = -count + 5;
                } else if (load_type == 3) {
                    req.add_offset = -count / 2;
                } else if (load_type == 1) {
                    req.add_offset = -count - 1;
                } else if (load_type == 2 && max_id != 0) {
                    req.add_offset = -count + 6;
                } else {
                    if (lower_part < 0 && max_id != 0) {
                        TLRPC.Chat chat = getChat(-lower_part);
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
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        removeDeletedMessagesFromArray(dialog_id, res.messages);
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
                        processLoadedMessages(res, dialog_id, count, mid, offset_date, false, classGuid, first_unread, last_message_id, unread_count, last_date, load_type, isChannel, false, false, loadIndex, queryFromServer, mentionsCount);
                    }
                });
                getConnectionsManager().bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public void reloadWebPages(final long dialog_id, HashMap<String, ArrayList<MessageObject>> webpagesToReload, boolean scheduled) {
        HashMap<String, ArrayList<MessageObject>> map = scheduled ? reloadingScheduledWebpages : reloadingWebpages;
        LongSparseArray<ArrayList<MessageObject>> array = scheduled ? reloadingScheduledWebpagesPending : reloadingWebpagesPending;

        for (HashMap.Entry<String, ArrayList<MessageObject>> entry : webpagesToReload.entrySet()) {
            final String url = entry.getKey();
            final ArrayList<MessageObject> messages = entry.getValue();
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
                    getMessagesStorage().putMessages(messagesRes, dialog_id, -2, 0, false, scheduled);
                    getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList1);
                }
            }));
        }
    }

    public void processLoadedMessages(final TLRPC.messages_Messages messagesRes, final long dialog_id, final int count, final int max_id, final int offset_date, final boolean isCache, final int classGuid,
                                      final int first_unread, final int last_message_id, final int unread_count, final int last_date, final int load_type, final boolean isChannel, final boolean isEnd, final boolean scheduled, final int loadIndex, final boolean queryFromServer, final int mentionsCount) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("processLoadedMessages size " + messagesRes.messages.size() + " in chat " + dialog_id + " count " + count + " max_id " + max_id + " cache " + isCache + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " isChannel " + isChannel + " index " + loadIndex + " firstUnread " + first_unread + " unread_count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        }
        Utilities.stageQueue.postRunnable(() -> {
            boolean createDialog = false;
            boolean isMegagroup = false;
            if (messagesRes instanceof TLRPC.TL_messages_channelMessages) {
                int channelId = -(int) dialog_id;
                if (!scheduled) {
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
                for (int a = 0; a < messagesRes.chats.size(); a++) {
                    TLRPC.Chat chat = messagesRes.chats.get(a);
                    if (chat.id == channelId) {
                        isMegagroup = chat.megagroup;
                        break;
                    }
                }
            }
            int lower_id = (int) dialog_id;
            int high_id = (int) (dialog_id >> 32);
            if (!isCache) {
                ImageLoader.saveMessagesThumbs(messagesRes.messages);
            }
            if (high_id != 1 && lower_id != 0 && isCache && (messagesRes.messages.size() == 0 || scheduled && (SystemClock.uptimeMillis() - lastScheduledServerQueryTime.get(dialog_id, 0L)) > 60 * 1000)) {
                int hash;
                if (scheduled) {
                    lastScheduledServerQueryTime.put(dialog_id, SystemClock.uptimeMillis());
                    long h = 0;
                    for (int a = 0, N = messagesRes.messages.size(); a < N; a++) {
                        TLRPC.Message message = messagesRes.messages.get(a);
                        if (message.id < 0) {
                            continue;
                        }
                        h = ((h * 20261) + 0x80000000L + message.id) % 0x80000000L;
                        h = ((h * 20261) + 0x80000000L + message.edit_date) % 0x80000000L;
                        h = ((h * 20261) + 0x80000000L + message.date) % 0x80000000L;
                    }
                    hash = (int) h - 1;
                } else {
                    hash = 0;
                }
                AndroidUtilities.runOnUIThread(() -> loadMessages(dialog_id, count, load_type == 2 && queryFromServer ? first_unread : max_id, offset_date, false, hash, classGuid, load_type, last_message_id, isChannel, scheduled, loadIndex, first_unread, unread_count, last_date, queryFromServer, mentionsCount));
                if (messagesRes.messages.isEmpty()) {
                    return;
                }
            }
            final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
            final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();
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
                Integer inboxValue = dialogs_read_inbox_max.get(dialog_id);
                if (inboxValue == null) {
                    inboxValue = getMessagesStorage().getDialogReadMax(false, dialog_id);
                    dialogs_read_inbox_max.put(dialog_id, inboxValue);
                }

                Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                if (outboxValue == null) {
                    outboxValue = getMessagesStorage().getDialogReadMax(true, dialog_id);
                    dialogs_read_outbox_max.put(dialog_id, outboxValue);
                }

                for (int a = 0; a < size; a++) {
                    TLRPC.Message message = messagesRes.messages.get(a);
                    if (isMegagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }

                    if (!scheduled) {
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
                            message.unread = (message.out ? outboxValue : inboxValue) < message.id;
                        }
                    }
                }
                getMessagesStorage().putMessages(messagesRes, dialog_id, load_type, max_id, createDialog, scheduled);
            }

            final ArrayList<MessageObject> objects = new ArrayList<>();
            final ArrayList<Integer> messagesToReload = new ArrayList<>();
            final HashMap<String, ArrayList<MessageObject>> webpagesToReload = new HashMap<>();
            TLRPC.InputChannel inputChannel = null;
            for (int a = 0; a < size; a++) {
                TLRPC.Message message = messagesRes.messages.get(a);
                message.dialog_id = dialog_id;
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, true);
                messageObject.scheduled = scheduled;
                objects.add(messageObject);
                if (isCache) {
                    if (message.legacy && message.layer < TLRPC.LAYER) {
                        messagesToReload.add(message.id);
                    } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                        if (message.media.bytes != null && (message.media.bytes.length == 0 || message.media.bytes.length == 1 && message.media.bytes[0] < TLRPC.LAYER)) {
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
            AndroidUtilities.runOnUIThread(() -> {
                putUsers(messagesRes.users, isCache);
                putChats(messagesRes.chats, isCache);
                int first_unread_final;
                if (scheduled) {
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
                if (scheduled && count == 1) {
                    getNotificationCenter().postNotificationName(NotificationCenter.scheduledMessagesUpdated, dialog_id, objects.size());
                }
                getNotificationCenter().postNotificationName(NotificationCenter.messagesDidLoad, dialog_id, count, objects, isCache, first_unread_final, last_message_id, unread_count, last_date, load_type, isEnd, classGuid, loadIndex, max_id, mentionsCount, scheduled);
                if (!messagesToReload.isEmpty()) {
                    reloadMessages(messagesToReload, dialog_id, scheduled);
                }
                if (!webpagesToReload.isEmpty()) {
                    reloadWebPages(dialog_id, webpagesToReload, scheduled);
                }
            });
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
                break;
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
        int[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(folderId);
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

        final long newTaskId;
        if (taskId == 0) {
            boolean added = false;
            int selfUserId = getUserConfig().getClientUserId();
            int size = 0;
            for (int a = 0, N = dialogIds.size(); a < N; a++) {
                long dialogId = dialogIds.get(a);
                if (!DialogObject.isPeerDialogId(dialogId) && !DialogObject.isSecretDialogId(dialogId)) {
                    continue;
                }
                if (folderId == 1 && (dialogId == selfUserId || dialogId == 777000 || isProxyDialog(dialogId, false))) {
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
                if (DialogObject.isSecretDialogId(dialogId)) {
                    getMessagesStorage().setDialogsFolderId(null, null, dialogId, folderId);
                } else {
                    TLRPC.TL_inputFolderPeer folderPeer = new TLRPC.TL_inputFolderPeer();
                    folderPeer.folder_id = folderId;
                    folderPeer.peer = getInputPeer((int) dialogId);
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

    public void loadDialogs(final int folderId, final int offset, final int count, boolean fromCache) {
        loadDialogs(folderId, offset, count, fromCache, null);
    }

    public void loadDialogs(final int folderId, final int offset, final int count, boolean fromCache, Runnable onEmptyCallback) {
        if (loadingDialogs.get(folderId) || resetingDialogs) {
            return;
        }
        loadingDialogs.put(folderId, true);
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("folderId = " + folderId + " load cacheOffset = " + offset + " count = " + count + " cache = " + fromCache);
        }
        if (fromCache) {
            getMessagesStorage().getDialogs(folderId, offset == 0 ? 0 : nextDialogsCacheOffset.get(folderId, 0), count);
        } else {
            TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
            req.limit = count;
            req.exclude_pinned = true;
            if (folderId != 0) {
                req.flags |= 2;
                req.folder_id = folderId;
            }
            int[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(folderId);
            if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1) {
                if (dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] == Integer.MAX_VALUE) {
                    dialogsEndReached.put(folderId, true);
                    serverDialogsEndReached.put(folderId, true);
                    loadingDialogs.put(folderId, false);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    return;
                }
                req.offset_id = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId];
                req.offset_date = dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetDate];
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
                    req.offset_peer.access_hash = ((long) dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetAccess_1]) | ((long) dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetAccess_1] << 32);
                }
            } else {
                boolean found = false;
                ArrayList<TLRPC.Dialog> dialogs = getDialogs(folderId);
                for (int a = dialogs.size() - 1; a >= 0; a--) {
                    TLRPC.Dialog dialog = dialogs.get(a);
                    if (dialog.pinned) {
                        continue;
                    }
                    int lower_id = (int) dialog.id;
                    int high_id = (int) (dialog.id >> 32);
                    if (lower_id != 0 && high_id != 1 && dialog.top_message > 0) {
                        MessageObject message = dialogMessage.get(dialog.id);
                        if (message != null && message.getId() > 0) {
                            req.offset_date = message.messageOwner.date;
                            req.offset_id = message.messageOwner.id;
                            int id;
                            if (message.messageOwner.to_id.channel_id != 0) {
                                id = -message.messageOwner.to_id.channel_id;
                            } else if (message.messageOwner.to_id.chat_id != 0) {
                                id = -message.messageOwner.to_id.chat_id;
                            } else {
                                id = message.messageOwner.to_id.user_id;
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
                    final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
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
                if (editor1 == null) {
                    editor1 = preferences.edit();
                }
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
                } else if (a == 2) {
                    req.peer = new TLRPC.TL_inputNotifyBroadcasts();
                }
                final int type = a;
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
                        } else if (type == 2) {
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

    protected void loadUnknownDialog(final TLRPC.InputPeer peer, final long taskId) {
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
        final long newTaskId;
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

    private void resetDialogs(boolean query, final int seq, final int newPts, final int date, final int qts) {
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

            final LongSparseArray<TLRPC.Dialog> new_dialogs_dict = new LongSparseArray<>();
            final LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
            final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
            final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();

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
                if (message.to_id.channel_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                    if (chat != null && chat.left) {
                        continue;
                    }
                    if (chat != null && chat.megagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }
                } else if (message.to_id.chat_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.to_id.chat_id);
                    if (chat != null && chat.migrated_to != null) {
                        continue;
                    }
                }
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false);
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
                    TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                    if (chat != null && chat.left) {
                        continue;
                    }
                    channelsPts.put(-(int) d.id, d.pts);
                } else if ((int) d.id < 0) {
                    TLRPC.Chat chat = chatsDict.get(-(int) d.id);
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

    protected void completeDialogsReset(final TLRPC.messages_Dialogs dialogsRes, final int messagesCount, final int seq, final int newPts, final int date, final int qts, final LongSparseArray<TLRPC.Dialog> new_dialogs_dict, final LongSparseArray<MessageObject> new_dialogMessage, final TLRPC.Message lastMessage) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingDifference = false;
            getMessagesStorage().setLastPtsValue(newPts);
            getMessagesStorage().setLastDateValue(date);
            getMessagesStorage().setLastQtsValue(qts);
            getDifference();

            AndroidUtilities.runOnUIThread(() -> {
                resetingDialogs = false;
                applyDialogsNotificationsSettings(dialogsRes.dialogs);
                if (!getUserConfig().draftsLoaded) {
                    getMediaDataController().loadDrafts();
                }

                putUsers(dialogsRes.users, false);
                putChats(dialogsRes.chats, false);

                for (int a = 0; a < allDialogs.size(); a++) {
                    TLRPC.Dialog oldDialog = allDialogs.get(a);
                    if (!DialogObject.isSecretDialogId(oldDialog.id)) {
                        dialogs_dict.remove(oldDialog.id);
                        MessageObject messageObject = dialogMessage.get(oldDialog.id);
                        dialogMessage.remove(oldDialog.id);
                        if (messageObject != null) {
                            dialogMessagesByIds.remove(messageObject.getId());
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
                        getMediaDataController().saveDraft(value.id, value.draft, null, false);
                    }
                    dialogs_dict.put(key, value);
                    MessageObject messageObject = new_dialogMessage.get(value.id);
                    dialogMessage.put(key, messageObject);
                    if (messageObject != null && messageObject.messageOwner.to_id.channel_id == 0) {
                        dialogMessagesByIds.put(messageObject.getId(), messageObject);
                        if (messageObject.messageOwner.random_id != 0) {
                            dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
                        }
                    }
                }

                allDialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    allDialogs.add(dialogs_dict.valueAt(a));
                }
                sortDialogs(null);
                dialogsEndReached.put(0, true);
                serverDialogsEndReached.put(0, false);

                dialogsEndReached.put(1, true);
                serverDialogsEndReached.put(1, false);

                int totalDialogsLoadCount = getUserConfig().getTotalDialogsCount(0);
                int[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(0);
                if (totalDialogsLoadCount < 400 && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1 && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != Integer.MAX_VALUE) {
                    loadDialogs(0, 100, 0, false);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            });
        });
    }

    private void migrateDialogs(final int offset, final int offsetDate, final int offsetUser, final int offsetChat, final int offsetChannel, final long accessPeer) {
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
                final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
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
                            if (dialog.folder_id != folder_id) {
                                continue;
                            }
                            dialogHashMap.remove(did);
                            if (dialog != null) {
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
                        }
                        cursor.dispose();
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("migrate found missing dialogs " + dialogsRes.dialogs.size());
                        }
                        cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT min(date) FROM dialogs WHERE date != 0 AND did >> 32 IN (0, -1)");
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
                        if (lastMessage.to_id.channel_id != 0) {
                            getUserConfig().migrateOffsetChannelId = lastMessage.to_id.channel_id;
                            getUserConfig().migrateOffsetChatId = 0;
                            getUserConfig().migrateOffsetUserId = 0;
                            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                TLRPC.Chat chat = dialogsRes.chats.get(a);
                                if (chat.id == getUserConfig().migrateOffsetChannelId) {
                                    getUserConfig().migrateOffsetAccess = chat.access_hash;
                                    break;
                                }
                            }
                        } else if (lastMessage.to_id.chat_id != 0) {
                            getUserConfig().migrateOffsetChatId = lastMessage.to_id.chat_id;
                            getUserConfig().migrateOffsetChannelId = 0;
                            getUserConfig().migrateOffsetUserId = 0;
                            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                TLRPC.Chat chat = dialogsRes.chats.get(a);
                                if (chat.id == getUserConfig().migrateOffsetChatId) {
                                    getUserConfig().migrateOffsetAccess = chat.access_hash;
                                    break;
                                }
                            }
                        } else if (lastMessage.to_id.user_id != 0) {
                            getUserConfig().migrateOffsetUserId = lastMessage.to_id.user_id;
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

    public void processLoadedDialogs(final TLRPC.messages_Dialogs dialogsRes, final ArrayList<TLRPC.EncryptedChat> encChats, final int folderId, final int offset, final int count, final int loadType, final boolean resetEnd, final boolean migrate, final boolean fromCache) {
        Utilities.stageQueue.postRunnable(() -> {
            if (!firstGettingTask) {
                getNewDeleteTask(null, 0);
                firstGettingTask = true;
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("loaded folderId " + folderId + " loadType " + loadType + " count " + dialogsRes.dialogs.size());
            }
            int[] dialogsLoadOffset = getUserConfig().getDialogLoadOffsets(folderId);
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

            final LongSparseArray<TLRPC.Dialog> new_dialogs_dict = new LongSparseArray<>();
            final SparseArray<TLRPC.EncryptedChat> enc_chats_dict;
            final LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
            final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
            final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();

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
                if (message.to_id.channel_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                    if (chat != null && chat.left && (proxyDialogId == 0 || proxyDialogId != -chat.id)) {
                        continue;
                    }
                    if (chat != null && chat.megagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }
                } else if (message.to_id.chat_id != 0) {
                    TLRPC.Chat chat = chatsDict.get(message.to_id.chat_id);
                    if (chat != null && chat.migrated_to != null) {
                        continue;
                    }
                }
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false);
                new_dialogMessage.put(messageObject.getDialogId(), messageObject);
            }

            if (!fromCache && !migrate && dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId] != -1 && loadType == 0) {
                int totalDialogsLoadCount = getUserConfig().getTotalDialogsCount(folderId);
                int dialogsLoadOffsetId;
                int dialogsLoadOffsetDate = 0;
                int dialogsLoadOffsetChannelId = 0;
                int dialogsLoadOffsetChatId = 0;
                int dialogsLoadOffsetUserId = 0;
                long dialogsLoadOffsetAccess = 0;
                if (lastMessage != null && lastMessage.id != dialogsLoadOffset[UserConfig.i_dialogsLoadOffsetId]) {
                    totalDialogsLoadCount += dialogsRes.dialogs.size();
                    dialogsLoadOffsetId = lastMessage.id;
                    dialogsLoadOffsetDate = lastMessage.date;
                    if (lastMessage.to_id.channel_id != 0) {
                        dialogsLoadOffsetChannelId = lastMessage.to_id.channel_id;
                        dialogsLoadOffsetChatId = 0;
                        dialogsLoadOffsetUserId = 0;
                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                            if (chat.id == dialogsLoadOffsetChannelId) {
                                dialogsLoadOffsetAccess = chat.access_hash;
                                break;
                            }
                        }
                    } else if (lastMessage.to_id.chat_id != 0) {
                        dialogsLoadOffsetChatId = lastMessage.to_id.chat_id;
                        dialogsLoadOffsetChannelId = 0;
                        dialogsLoadOffsetUserId = 0;
                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                            if (chat.id == dialogsLoadOffsetChatId) {
                                dialogsLoadOffsetAccess = chat.access_hash;
                                break;
                            }
                        }
                    } else if (lastMessage.to_id.user_id != 0) {
                        dialogsLoadOffsetUserId = lastMessage.to_id.user_id;
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

            final ArrayList<TLRPC.Dialog> dialogsToReload = new ArrayList<>();
            for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                TLRPC.Dialog d = dialogsRes.dialogs.get(a);
                DialogObject.initDialog(d);
                if (d.id == 0) {
                    continue;
                }
                int lower_id = (int) d.id;
                int high_id = (int) (d.id >> 32);
                if (lower_id == 0 && enc_chats_dict != null) {
                    if (enc_chats_dict.get(high_id) == null) {
                        continue;
                    }
                }
                if (proxyDialogId != 0 && proxyDialogId == d.id) {
                    proxyDialog = d;
                }
                if (d.last_message_date == 0) {
                    MessageObject mess = new_dialogMessage.get(d.id);
                    if (mess != null) {
                        d.last_message_date = mess.messageOwner.date;
                    }
                }
                boolean allowCheck = true;
                if (DialogObject.isChannel(d)) {
                    TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                    if (chat != null) {
                        if (!chat.megagroup) {
                            allowCheck = false;
                        }
                        if (chat.left && (proxyDialogId == 0 || proxyDialogId != d.id)) {
                            continue;
                        }
                    }
                    channelsPts.put(-(int) d.id, d.pts);
                } else if ((int) d.id < 0) {
                    TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                    if (chat != null && chat.migrated_to != null) {
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
                getMessagesStorage().putDialogs(dialogsRes, 0);
            }
            if (loadType == DIALOGS_LOAD_TYPE_CHANNEL) {
                TLRPC.Chat chat = dialogsRes.chats.get(0);
                getChannelDifference(chat.id);
                checkChannelInviter(chat.id);
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (loadType != DIALOGS_LOAD_TYPE_CACHE) {
                    applyDialogsNotificationsSettings(dialogsRes.dialogs);
                    if (!getUserConfig().draftsLoaded) {
                        getMediaDataController().loadDrafts();
                    }
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
                    TLRPC.Dialog currentDialog;
                    if (loadType != DIALOGS_LOAD_TYPE_UNKNOWN) {
                        currentDialog = dialogs_dict.get(key);
                    } else {
                        currentDialog = null;
                    }
                    if (migrate && currentDialog != null) {
                        currentDialog.folder_id = value.folder_id;
                    }
                    if (migrate && lastDialogDate != 0 && value.last_message_date < lastDialogDate) {
                        continue;
                    }
                    if (loadType != DIALOGS_LOAD_TYPE_CACHE && value.draft instanceof TLRPC.TL_draftMessage) {
                        getMediaDataController().saveDraft(value.id, value.draft, null, false);
                    }
                    if (value.folder_id != folderId) {
                        archivedDialogsCount++;
                    }
                    if (currentDialog == null) {
                        added = true;
                        dialogs_dict.put(key, value);
                        MessageObject messageObject = new_dialogMessage.get(value.id);
                        dialogMessage.put(key, messageObject);
                        if (messageObject != null && messageObject.messageOwner.to_id.channel_id == 0) {
                            dialogMessagesByIds.put(messageObject.getId(), messageObject);
                            if (messageObject.messageOwner.random_id != 0) {
                                dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
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
                                MessageObject messageObject = new_dialogMessage.get(value.id);
                                dialogMessage.put(key, messageObject);
                                if (messageObject != null && messageObject.messageOwner.to_id.channel_id == 0) {
                                    dialogMessagesByIds.put(messageObject.getId(), messageObject);
                                    if (messageObject != null && messageObject.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
                                    }
                                }
                                if (oldMsg != null) {
                                    dialogMessagesByIds.remove(oldMsg.getId());
                                    if (oldMsg.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                    }
                                }
                            }
                        } else {
                            MessageObject newMsg = new_dialogMessage.get(value.id);
                            if (oldMsg.deleted || newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(key, newMsg);
                                if (newMsg != null && newMsg.messageOwner.to_id.channel_id == 0) {
                                    dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                    if (newMsg != null && newMsg.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                    }
                                }
                                dialogMessagesByIds.remove(oldMsg.getId());
                                if (oldMsg.messageOwner.random_id != 0) {
                                    dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                }
                            }
                        }
                    }
                }

                allDialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    allDialogs.add(dialogs_dict.valueAt(a));
                }
                sortDialogs(migrate ? chatsDict : null);

                if (loadType != DIALOGS_LOAD_TYPE_CHANNEL && loadType != DIALOGS_LOAD_TYPE_UNKNOWN) {
                    if (!migrate) {
                        dialogsEndReached.put(folderId, (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && loadType == 0);
                        if (archivedDialogsCount > 0 && archivedDialogsCount < 20 && folderId == 0) {
                            dialogsEndReached.put(1, true);
                            int[] dialogsLoadOffsetArchived = getUserConfig().getDialogLoadOffsets(folderId);
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
                int[] dialogsLoadOffset2 = getUserConfig().getDialogLoadOffsets(folderId);
                if (!fromCache && !migrate && totalDialogsLoadCount < 400 && dialogsLoadOffset2[UserConfig.i_dialogsLoadOffsetId] != -1 && dialogsLoadOffset2[UserConfig.i_dialogsLoadOffsetId] != Integer.MAX_VALUE) {
                    loadDialogs(0, 100, folderId, false);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);

                if (migrate) {
                    getUserConfig().migrateOffsetId = offset;
                    getUserConfig().saveConfig(false);
                    migratingDialogs = false;
                    getNotificationCenter().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
                } else {
                    generateUpdateMessage();
                    if (!added && loadType == DIALOGS_LOAD_TYPE_CACHE) {
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

    private void applyDialogNotificationsSettings(long dialog_id, TLRPC.PeerNotifySettings notify_settings) {
        if (notify_settings == null) {
            return;
        }
        int currentValue = notificationsPreferences.getInt("notify2_" + dialog_id, -1);
        int currentValue2 = notificationsPreferences.getInt("notifyuntil_" + dialog_id, 0);
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        boolean updated = false;
        TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
        if (dialog != null) {
            dialog.notify_settings = notify_settings;
        }
        if ((notify_settings.flags & 2) != 0) {
            editor.putBoolean("silent_" + dialog_id, notify_settings.silent);
        } else {
            editor.remove("silent_" + dialog_id);
        }
        if ((notify_settings.flags & 4) != 0) {
            if (notify_settings.mute_until > getConnectionsManager().getCurrentTime()) {
                int until = 0;
                if (notify_settings.mute_until > getConnectionsManager().getCurrentTime() + 60 * 60 * 24 * 365) {
                    if (currentValue != 2) {
                        updated = true;
                        editor.putInt("notify2_" + dialog_id, 2);
                        if (dialog != null) {
                            dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                        }
                    }
                } else {
                    if (currentValue != 3 || currentValue2 != notify_settings.mute_until) {
                        updated = true;
                        editor.putInt("notify2_" + dialog_id, 3);
                        editor.putInt("notifyuntil_" + dialog_id, notify_settings.mute_until);
                        if (dialog != null) {
                            dialog.notify_settings.mute_until = until;
                        }
                    }
                    until = notify_settings.mute_until;
                }
                getMessagesStorage().setDialogFlags(dialog_id, ((long) until << 32) | 1);
                getNotificationsController().removeNotificationsForDialog(dialog_id);
            } else {
                if (currentValue != 0 && currentValue != 1) {
                    updated = true;
                    if (dialog != null) {
                        dialog.notify_settings.mute_until = 0;
                    }
                    editor.putInt("notify2_" + dialog_id, 0);
                }
                getMessagesStorage().setDialogFlags(dialog_id, 0);
            }
        } else {
            if (currentValue != -1) {
                updated = true;
                if (dialog != null) {
                    dialog.notify_settings.mute_until = 0;
                }
                editor.remove("notify2_" + dialog_id);
            }
            getMessagesStorage().setDialogFlags(dialog_id, 0);
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
                int dialog_id;
                if (dialog.peer.user_id != 0) {
                    dialog_id = dialog.peer.user_id;
                } else if (dialog.peer.chat_id != 0) {
                    dialog_id = -dialog.peer.chat_id;
                } else {
                    dialog_id = -dialog.peer.channel_id;
                }
                if ((dialog.notify_settings.flags & 2) != 0) {
                    editor.putBoolean("silent_" + dialog_id, dialog.notify_settings.silent);
                } else {
                    editor.remove("silent_" + dialog_id);
                }
                if ((dialog.notify_settings.flags & 4) != 0) {
                    if (dialog.notify_settings.mute_until > getConnectionsManager().getCurrentTime()) {
                        if (dialog.notify_settings.mute_until > getConnectionsManager().getCurrentTime() + 60 * 60 * 24 * 365) {
                            editor.putInt("notify2_" + dialog_id, 2);
                            dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                        } else {
                            editor.putInt("notify2_" + dialog_id, 3);
                            editor.putInt("notifyuntil_" + dialog_id, dialog.notify_settings.mute_until);
                        }
                    } else {
                        editor.putInt("notify2_" + dialog_id, 0);
                    }
                } else {
                    editor.remove("notify2_" + dialog_id);
                }
            }
        }
        if (editor != null) {
            editor.commit();
        }
    }

    public void reloadMentionsCountForChannels(final ArrayList<Integer> arrayList) {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < arrayList.size(); a++) {
                final long dialog_id = -arrayList.get(a);
                TLRPC.TL_messages_getUnreadMentions req = new TLRPC.TL_messages_getUnreadMentions();
                req.peer = getInputPeer((int) dialog_id);
                req.limit = 1;
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    if (res != null) {
                        int newCount;
                        if (res.count != 0) {
                            newCount = res.count;
                        } else {
                            newCount = res.messages.size();
                        }
                        getMessagesStorage().resetMentionsCount(dialog_id, newCount);
                    }
                }));
            }
        });
    }

    public void processDialogsUpdateRead(final LongSparseArray<Integer> dialogsToUpdate, final LongSparseArray<Integer> dialogsMentionsToUpdate) {
        AndroidUtilities.runOnUIThread(() -> {
            if (dialogsToUpdate != null) {
                for (int a = 0; a < dialogsToUpdate.size(); a++) {
                    long dialogId = dialogsToUpdate.keyAt(a);
                    TLRPC.Dialog currentDialog = dialogs_dict.get(dialogId);
                    if (currentDialog != null) {
                        int prevCount = currentDialog.unread_count;
                        currentDialog.unread_count = dialogsToUpdate.valueAt(a);
                        if (prevCount != 0 && currentDialog.unread_count == 0 && !isDialogMuted(dialogId)) {
                            unreadUnmutedDialogs--;
                        } else if (prevCount == 0 && !currentDialog.unread_mark && currentDialog.unread_count != 0 && !isDialogMuted(dialogId)) {
                            unreadUnmutedDialogs++;
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
                    }
                }
            }
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
            if (dialogsToUpdate != null) {
                getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
            }
        });
    }

    protected void checkLastDialogMessage(final TLRPC.Dialog dialog, final TLRPC.InputPeer peer, long taskId) {
        final int lower_id = (int) dialog.id;
        if (lower_id == 0 || checkingLastMessagesDialogs.indexOfKey(lower_id) >= 0) {
            return;
        }
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer == null ? getInputPeer(lower_id) : peer;
        if (req.peer == null) {
            return;
        }
        req.limit = 1;
        checkingLastMessagesDialogs.put(lower_id, true);

        final long newTaskId;
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
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                removeDeletedMessagesFromArray(lower_id, res.messages);
                if (!res.messages.isEmpty()) {
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
                    processDialogsUpdate(dialogs, null);
                    getMessagesStorage().putMessages(res.messages, true, true, false, getDownloadController().getAutodownloadMask(), true);
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.Dialog currentDialog = dialogs_dict.get(dialog.id);
                        if (currentDialog != null && currentDialog.top_message == 0) {
                            deleteDialog(dialog.id, 3);
                        }
                    });
                }
            }
            if (newTaskId != 0) {
                getMessagesStorage().removePendingTask(newTaskId);
            }
            AndroidUtilities.runOnUIThread(() -> checkingLastMessagesDialogs.delete(lower_id));
        });
    }

    public void processDialogsUpdate(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats) {
        Utilities.stageQueue.postRunnable(() -> {
            final LongSparseArray<TLRPC.Dialog> new_dialogs_dict = new LongSparseArray<>();
            final LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
            final SparseArray<TLRPC.User> usersDict = new SparseArray<>(dialogsRes.users.size());
            final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>(dialogsRes.chats.size());
            final LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>();

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
                if (proxyDialogId == 0 || proxyDialogId != message.dialog_id) {
                    if (message.to_id.channel_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                        if (chat != null && chat.left) {
                            continue;
                        }
                    } else if (message.to_id.chat_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.to_id.chat_id);
                        if (chat != null && chat.migrated_to != null) {
                            continue;
                        }
                    }
                }
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false);
                new_dialogMessage.put(messageObject.getDialogId(), messageObject);
            }
            for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                TLRPC.Dialog d = dialogsRes.dialogs.get(a);
                DialogObject.initDialog(d);
                if (proxyDialogId == 0 || proxyDialogId != d.id) {
                    if (DialogObject.isChannel(d)) {
                        TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                        if (chat != null && chat.left) {
                            continue;
                        }
                    } else if ((int) d.id < 0) {
                        TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                        if (chat != null && chat.migrated_to != null) {
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
                    TLRPC.Dialog value = new_dialogs_dict.valueAt(a);
                    TLRPC.Dialog currentDialog = dialogs_dict.get(key);
                    if (currentDialog == null) {
                        int offset = nextDialogsCacheOffset.get(value.folder_id, 0) + 1;
                        nextDialogsCacheOffset.put(value.folder_id, offset);
                        dialogs_dict.put(key, value);
                        MessageObject messageObject = new_dialogMessage.get(value.id);
                        dialogMessage.put(key, messageObject);
                        if (messageObject != null && messageObject.messageOwner.to_id.channel_id == 0) {
                            dialogMessagesByIds.put(messageObject.getId(), messageObject);
                            if (messageObject.messageOwner.random_id != 0) {
                                dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
                            }
                        }
                    } else {
                        currentDialog.unread_count = value.unread_count;
                        if (currentDialog.unread_mentions_count != value.unread_mentions_count) {
                            currentDialog.unread_mentions_count = value.unread_mentions_count;
                            if (createdDialogMainThreadIds.contains(currentDialog.id)) {
                                getNotificationCenter().postNotificationName(NotificationCenter.updateMentionsCount, currentDialog.id, currentDialog.unread_mentions_count);
                            }
                        }
                        MessageObject oldMsg = dialogMessage.get(key);
                        if (oldMsg == null || currentDialog.top_message > 0) {
                            if (oldMsg != null && oldMsg.deleted || value.top_message > currentDialog.top_message) {
                                dialogs_dict.put(key, value);
                                MessageObject messageObject = new_dialogMessage.get(value.id);
                                dialogMessage.put(key, messageObject);
                                if (messageObject != null && messageObject.messageOwner.to_id.channel_id == 0) {
                                    dialogMessagesByIds.put(messageObject.getId(), messageObject);
                                    if (messageObject.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
                                    }
                                }
                                if (oldMsg != null) {
                                    dialogMessagesByIds.remove(oldMsg.getId());
                                    if (oldMsg.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                    }
                                }
                                if (messageObject == null) {
                                    checkLastDialogMessage(value, null, 0);
                                }
                            }
                        } else {
                            MessageObject newMsg = new_dialogMessage.get(value.id);
                            if (oldMsg.deleted || newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(key, newMsg);
                                if (newMsg != null && newMsg.messageOwner.to_id.channel_id == 0) {
                                    dialogMessagesByIds.put(newMsg.getId(), newMsg);
                                    if (newMsg.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.put(newMsg.messageOwner.random_id, newMsg);
                                    }
                                }
                                dialogMessagesByIds.remove(oldMsg.getId());
                                if (oldMsg.messageOwner.random_id != 0) {
                                    dialogMessagesByRandomIds.remove(oldMsg.messageOwner.random_id);
                                }
                            }
                        }
                    }
                }

                allDialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    allDialogs.add(dialogs_dict.valueAt(a));
                }
                sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
            });
        });
    }

    public void addToViewsQueue(MessageObject messageObject) {
        Utilities.stageQueue.postRunnable(() -> {
            int peer = (int) messageObject.getDialogId();
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
        for (int a = 0, N = visibleObjects.size(); a < N; a++) {
            MessageObject messageObject = visibleObjects.get(a);
            if (messageObject.type != MessageObject.TYPE_POLL) {
                continue;
            }
            int id = messageObject.getId();
            MessageObject object = array.get(id);
            if (object != null) {
                object.pollVisibleOnScreen = true;
            } else {
                array.put(id, messageObject);
            }
        }
    }

    public void markMessageContentAsRead(final MessageObject messageObject) {
        if (messageObject.scheduled) {
            return;
        }
        ArrayList<Long> arrayList = new ArrayList<>();
        long messageId = messageObject.getId();
        if (messageObject.messageOwner.to_id.channel_id != 0) {
            messageId |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
        }
        if (messageObject.messageOwner.mentioned) {
            getMessagesStorage().markMentionMessageAsRead(messageObject.getId(), messageObject.messageOwner.to_id.channel_id, messageObject.getDialogId());
        }
        arrayList.add(messageId);
        getMessagesStorage().markMessagesContentAsRead(arrayList, 0);
        getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, arrayList);
        if (messageObject.getId() < 0) {
            markMessageAsRead(messageObject.getDialogId(), messageObject.messageOwner.random_id, Integer.MIN_VALUE);
        } else {
            if (messageObject.messageOwner.to_id.channel_id != 0) {
                TLRPC.TL_channels_readMessageContents req = new TLRPC.TL_channels_readMessageContents();
                req.channel = getInputChannel(messageObject.messageOwner.to_id.channel_id);
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

    public void markMentionMessageAsRead(final int mid, final int channelId, final long did) {
        getMessagesStorage().markMentionMessageAsRead(mid, channelId, did);
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

    public void markMessageAsRead(final int mid, final int channelId, TLRPC.InputChannel inputChannel, int ttl, long taskId) {
        if (mid == 0 || ttl <= 0) {
            return;
        }
        if (channelId != 0 && inputChannel == null) {
            inputChannel = getInputChannel(channelId);
            if (inputChannel == null) {
                return;
            }
        }
        final long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(16 + (inputChannel != null ? inputChannel.getObjectSize() : 0));
                data.writeInt32(11);
                data.writeInt32(mid);
                data.writeInt32(channelId);
                data.writeInt32(ttl);
                if (channelId != 0) {
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
        getMessagesStorage().createTaskForMid(mid, channelId, time, time, ttl, false);
        if (channelId != 0) {
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

    public void markMessageAsRead(final long dialog_id, final long random_id, int ttl) {
        if (random_id == 0 || dialog_id == 0 || ttl <= 0 && ttl != Integer.MIN_VALUE) {
            return;
        }
        int lower_part = (int) dialog_id;
        int high_id = (int) (dialog_id >> 32);
        if (lower_part != 0) {
            return;
        }
        TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
        if (chat == null) {
            return;
        }
        ArrayList<Long> random_ids = new ArrayList<>();
        random_ids.add(random_id);
        getSecretChatHelper().sendMessagesReadMessage(chat, random_ids, null);
        if (ttl > 0) {
            int time = getConnectionsManager().getCurrentTime();
            getMessagesStorage().createTaskForSecretChat(chat.id, time, time, 0, random_ids);
        }
    }

    private void completeReadTask(ReadTask task) {
        int lower_part = (int) task.dialogId;
        int high_id = (int) (task.dialogId >> 32);

        if (lower_part != 0) {
            TLRPC.InputPeer inputPeer = getInputPeer(lower_part);
            TLObject req;
            if (inputPeer instanceof TLRPC.TL_inputPeerChannel) {
                TLRPC.TL_channels_readHistory request = new TLRPC.TL_channels_readHistory();
                request.channel = getInputChannel(-lower_part);
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
            TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
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
    }

    public void markDialogAsReadNow(final long dialogId) {
        Utilities.stageQueue.postRunnable(() -> {
            ReadTask currentReadTask = readTasksMap.get(dialogId);
            if (currentReadTask == null) {
                return;
            }
            completeReadTask(currentReadTask);
            readTasks.remove(currentReadTask);
            readTasksMap.remove(dialogId);
        });
    }

    public void markMentionsAsRead(long dialogId) {
        if ((int) dialogId == 0) {
            return;
        }
        getMessagesStorage().resetMentionsCount(dialogId, 0);
        TLRPC.TL_messages_readMentions req = new TLRPC.TL_messages_readMentions();
        req.peer = getInputPeer((int) dialogId);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
    }

    public void markDialogAsRead(final long dialogId, final int maxPositiveId, final int maxNegativeId, final int maxDate, final boolean popup, final int countDiff, final boolean readNow, final int scheduledCount) {
        int lower_part = (int) dialogId;
        int high_id = (int) (dialogId >> 32);
        boolean createReadTask;

        boolean countMessages = getNotificationsController().showBadgeMessages;
        if (lower_part != 0) {
            if (maxPositiveId == 0) {
                return;
            }
            long maxMessageId = maxPositiveId;
            long minMessageId = maxNegativeId;
            boolean isChannel = false;
            if (lower_part < 0) {
                TLRPC.Chat chat = getChat(-lower_part);
                if (ChatObject.isChannel(chat)) {
                    maxMessageId |= ((long) -lower_part) << 32;
                    minMessageId |= ((long) -lower_part) << 32;
                    isChannel = true;
                }
            }
            Integer value = dialogs_read_inbox_max.get(dialogId);
            if (value == null) {
                value = 0;
            }
            dialogs_read_inbox_max.put(dialogId, Math.max(value, maxPositiveId));

            getMessagesStorage().processPendingRead(dialogId, maxMessageId, minMessageId, isChannel, scheduledCount);
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
                    if (dialog.folder_id != 0) {
                        TLRPC.Dialog folder = dialogs_dict.get(DialogObject.makeFolderDialogId(dialog.folder_id));
                        if (folder != null) {
                            if (countMessages) {
                                if (isDialogMuted(dialog.id)) {
                                    folder.unread_count -= (prevCount - dialog.unread_count);
                                } else {
                                    folder.unread_mentions_count -= (prevCount - dialog.unread_count);
                                }
                            } else if (dialog.unread_count == 0) {
                                if (isDialogMuted(dialog.id)) {
                                    folder.unread_count--;
                                } else {
                                    folder.unread_mentions_count--;
                                }
                            }
                        }
                    }

                    if ((prevCount != 0 || dialog.unread_mark) && dialog.unread_count == 0 && !isDialogMuted(dialogId)) {
                        unreadUnmutedDialogs--;
                    }
                    if (dialog.unread_mark) {
                        dialog.unread_mark = false;
                        getMessagesStorage().setDialogUnread(dialog.id, false);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                }
                if (!popup) {
                    getNotificationsController().processReadMessages(null, dialogId, 0, maxPositiveId, false);
                    LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>(1);
                    dialogsToUpdate.put(dialogId, 0);
                    getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
                } else {
                    getNotificationsController().processReadMessages(null, dialogId, 0, maxPositiveId, true);
                    LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>(1);
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

            TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
            getMessagesStorage().processPendingRead(dialogId, maxPositiveId, maxNegativeId, false, scheduledCount);
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
                    if (dialog.folder_id != 0) {
                        TLRPC.Dialog folder = dialogs_dict.get(DialogObject.makeFolderDialogId(dialog.folder_id));
                        if (folder != null) {
                            if (countMessages) {
                                if (isDialogMuted(dialog.id)) {
                                    folder.unread_count -= (prevCount - dialog.unread_count);
                                } else {
                                    folder.unread_mentions_count -= (prevCount - dialog.unread_count);
                                }
                            } else if (dialog.unread_count == 0) {
                                if (isDialogMuted(dialog.id)) {
                                    folder.unread_count--;
                                } else {
                                    folder.unread_mentions_count--;
                                }
                            }
                        }
                    }
                    if ((prevCount != 0 || dialog.unread_mark) && dialog.unread_count == 0 && !isDialogMuted(dialogId)) {
                        unreadUnmutedDialogs--;
                    }
                    if (dialog.unread_mark) {
                        dialog.unread_mark = false;
                        getMessagesStorage().setDialogUnread(dialog.id, false);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                }
                LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>(1);
                dialogsToUpdate.put(dialogId, 0);
                getNotificationsController().processDialogsUpdateRead(dialogsToUpdate);
            }));

            if (chat != null && chat.ttl > 0) {
                int serverTime = Math.max(getConnectionsManager().getCurrentTime(), maxDate);
                getMessagesStorage().createTaskForSecretChat(chat.id, serverTime, serverTime, 0, null);
            }
        }

        if (createReadTask) {
            Utilities.stageQueue.postRunnable(() -> {
                ReadTask currentReadTask = readTasksMap.get(dialogId);
                if (currentReadTask == null) {
                    currentReadTask = new ReadTask();
                    currentReadTask.dialogId = dialogId;
                    currentReadTask.sendRequestTime = SystemClock.elapsedRealtime() + 5000;
                    if (!readNow) {
                        readTasksMap.put(dialogId, currentReadTask);
                        readTasks.add(currentReadTask);
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

    public int createChat(String title, ArrayList<Integer> selectedContacts, final String about, int type, Location location, String locationAddress, final BaseFragment fragment) {
        if (type == ChatObject.CHAT_TYPE_CHAT) {
            final TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
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
                final TLRPC.Updates updates = (TLRPC.Updates) response;
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
        } else if (type == ChatObject.CHAT_TYPE_CHANNEL || type == ChatObject.CHAT_TYPE_MEGAGROUP) {
            final TLRPC.TL_channels_createChannel req = new TLRPC.TL_channels_createChannel();
            req.title = title;
            req.about = about != null ? about : "";
            if (type == ChatObject.CHAT_TYPE_MEGAGROUP) {
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
                final TLRPC.Updates updates = (TLRPC.Updates) response;
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

    public void convertToMegaGroup(final Context context, int chat_id, BaseFragment fragment, MessagesStorage.IntCallback convertRunnable) {
        TLRPC.TL_messages_migrateChat req = new TLRPC.TL_messages_migrateChat();
        req.chat_id = chat_id;
        final AlertDialog progressDialog = new AlertDialog(context, 3);
        final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!((Activity) context).isFinishing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                });
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
                    if (!((Activity) context).isFinishing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        AlertsCreator.processError(currentAccount, error, fragment, req, false);
                    }
                });
            }
        });
        progressDialog.setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(reqId, true));
        try {
            progressDialog.show();
        } catch (Exception e) {
            //don't promt
        }
    }

    public void addUsersToChannel(int chat_id, ArrayList<TLRPC.InputUser> users, final BaseFragment fragment) {
        if (users == null || users.isEmpty()) {
            return;
        }
        final TLRPC.TL_channels_inviteToChannel req = new TLRPC.TL_channels_inviteToChannel();
        req.channel = getInputChannel(chat_id);
        req.users = users;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, fragment, req, true));
                return;
            }
            processUpdates((TLRPC.Updates) response, false);
        });
    }

    public void toogleChannelSignatures(int chat_id, boolean enabled) {
        TLRPC.TL_channels_toggleSignatures req = new TLRPC.TL_channels_toggleSignatures();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT));
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void toogleChannelInvitesHistory(int chat_id, boolean enabled) {
        TLRPC.TL_channels_togglePreHistoryHidden req = new TLRPC.TL_channels_togglePreHistoryHidden();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT));
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void updateChatAbout(int chat_id, final String about, final TLRPC.ChatFull info) {
        if (info == null) {
            return;
        }
        TLRPC.TL_messages_editChatAbout req = new TLRPC.TL_messages_editChatAbout();
        req.peer = getInputPeer(-chat_id);
        req.about = about;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                AndroidUtilities.runOnUIThread(() -> {
                    info.about = about;
                    getMessagesStorage().updateChatInfo(info, false);
                    getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, null);
                });
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void updateChannelUserName(final int chat_id, final String userName) {
        TLRPC.TL_channels_updateUsername req = new TLRPC.TL_channels_updateUsername();
        req.channel = getInputChannel(chat_id);
        req.username = userName;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.Chat chat = getChat(chat_id);
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

    public boolean isJoiningChannel(final int chat_id) {
        return joiningToChannels.contains(chat_id);
    }

    public void addUserToChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info, int count_fwd, String botHash, final BaseFragment fragment, final Runnable onFinishRunnable) {
        if (user == null) {
            return;
        }

        if (chat_id > 0) {
            final TLObject request;

            final boolean isChannel = ChatObject.isChannel(chat_id, currentAccount);
            final boolean isMegagroup = isChannel && getChat(chat_id).megagroup;
            final TLRPC.InputUser inputUser = getInputUser(user);
            if (botHash == null || isChannel && !isMegagroup) {
                if (isChannel) {
                    if (inputUser instanceof TLRPC.TL_inputUserSelf) {
                        if (joiningToChannels.contains(chat_id)) {
                            return;
                        }
                        TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
                        req.channel = getInputChannel(chat_id);
                        request = req;
                        joiningToChannels.add(chat_id);
                    } else {
                        TLRPC.TL_channels_inviteToChannel req = new TLRPC.TL_channels_inviteToChannel();
                        req.channel = getInputChannel(chat_id);
                        req.users.add(inputUser);
                        request = req;
                    }
                } else {
                    TLRPC.TL_messages_addChatUser req = new TLRPC.TL_messages_addChatUser();
                    req.chat_id = chat_id;
                    req.fwd_limit = count_fwd;
                    req.user_id = inputUser;
                    request = req;
                }
            } else {
                TLRPC.TL_messages_startBot req = new TLRPC.TL_messages_startBot();
                req.bot = inputUser;
                if (isChannel) {
                    req.peer = getInputPeer(-chat_id);
                } else {
                    req.peer = new TLRPC.TL_inputPeerChat();
                    req.peer.chat_id = chat_id;
                }
                req.start_param = botHash;
                req.random_id = Utilities.random.nextLong();
                request = req;
            }

            getConnectionsManager().sendRequest(request, (response, error) -> {
                if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                    AndroidUtilities.runOnUIThread(() -> joiningToChannels.remove((Integer) chat_id));
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
                        generateJoinMessage(chat_id, true);
                    }
                    AndroidUtilities.runOnUIThread(() -> loadFullChat(chat_id, 0, true), 1000);
                }
                if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                    getMessagesStorage().updateDialogsWithDeletedMessages(new ArrayList<>(), null, true, chat_id);
                }
                if (onFinishRunnable != null) {
                    AndroidUtilities.runOnUIThread(onFinishRunnable);
                }
            });
        } else {
            if (info instanceof TLRPC.TL_chatFull) {
                for (int a = 0; a < info.participants.participants.size(); a++) {
                    if (info.participants.participants.get(a).user_id == user.id) {
                        return;
                    }
                }

                TLRPC.Chat chat = getChat(chat_id);
                chat.participants_count++;
                ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<>();
                chatArrayList.add(chat);
                getMessagesStorage().putUsersAndChats(null, chatArrayList, true, true);

                TLRPC.TL_chatParticipant newPart = new TLRPC.TL_chatParticipant();
                newPart.user_id = user.id;
                newPart.inviter_id = getUserConfig().getClientUserId();
                newPart.date = getConnectionsManager().getCurrentTime();
                info.participants.participants.add(0, newPart);
                getMessagesStorage().updateChatInfo(info, true);
                getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, null);
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
            }
        }
    }

    public void deleteUserFromChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info) {
        deleteUserFromChat(chat_id, user, info, false, false);
    }

    public void deleteUserFromChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info, boolean forceDelete, boolean revoke) {
        if (user == null) {
            return;
        }
        if (chat_id > 0) {
            final TLRPC.InputUser inputUser = getInputUser(user);
            TLObject request;
            TLRPC.Chat chat = getChat(chat_id);
            final boolean isChannel = ChatObject.isChannel(chat);
            if (isChannel) {
                if (inputUser instanceof TLRPC.TL_inputUserSelf) {
                    if (chat.creator && forceDelete) {
                        TLRPC.TL_channels_deleteChannel req = new TLRPC.TL_channels_deleteChannel();
                        req.channel = getInputChannel(chat);
                        request = req;
                    } else {
                        TLRPC.TL_channels_leaveChannel req = new TLRPC.TL_channels_leaveChannel();
                        req.channel = getInputChannel(chat);
                        request = req;
                    }
                } else {
                    TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
                    req.channel = getInputChannel(chat);
                    req.user_id = inputUser;
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
                TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
                req.chat_id = chat_id;
                req.user_id = getInputUser(user);
                request = req;
            }
            if (user.id == getUserConfig().getClientUserId()) {
                deleteDialog(-chat_id, 0, revoke);
            }
            getConnectionsManager().sendRequest(request, (response, error) -> {
                if (error != null) {
                    return;
                }
                final TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
                if (isChannel && !(inputUser instanceof TLRPC.TL_inputUserSelf)) {
                    AndroidUtilities.runOnUIThread(() -> loadFullChat(chat_id, 0, true), 1000);
                }
            }, ConnectionsManager.RequestFlagInvokeAfter);
        } else {
            if (info instanceof TLRPC.TL_chatFull) {
                TLRPC.Chat chat = getChat(chat_id);
                chat.participants_count--;
                ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<>();
                chatArrayList.add(chat);
                getMessagesStorage().putUsersAndChats(null, chatArrayList, true, true);

                boolean changed = false;
                for (int a = 0; a < info.participants.participants.size(); a++) {
                    TLRPC.ChatParticipant p = info.participants.participants.get(a);
                    if (p.user_id == user.id) {
                        info.participants.participants.remove(a);
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    getMessagesStorage().updateChatInfo(info, true);
                    getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, false, null);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
            }
        }
    }

    public void changeChatTitle(int chat_id, String title) {
        if (chat_id > 0) {
            TLObject request;
            if (ChatObject.isChannel(chat_id, currentAccount)) {
                TLRPC.TL_channels_editTitle req = new TLRPC.TL_channels_editTitle();
                req.channel = getInputChannel(chat_id);
                req.title = title;
                request = req;
            } else {
                TLRPC.TL_messages_editChatTitle req = new TLRPC.TL_messages_editChatTitle();
                req.chat_id = chat_id;
                req.title = title;
                request = req;
            }
            getConnectionsManager().sendRequest(request, (response, error) -> {
                if (error != null) {
                    return;
                }
                processUpdates((TLRPC.Updates) response, false);
            }, ConnectionsManager.RequestFlagInvokeAfter);
        } else {
            TLRPC.Chat chat = getChat(chat_id);
            chat.title = title;
            ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<>();
            chatArrayList.add(chat);
            getMessagesStorage().putUsersAndChats(null, chatArrayList, true, true);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_NAME);
        }
    }

    public void changeChatAvatar(int chat_id, TLRPC.InputFile uploadedAvatar, TLRPC.FileLocation smallSize, TLRPC.FileLocation bigSize) {
        TLObject request;
        if (ChatObject.isChannel(chat_id, currentAccount)) {
            TLRPC.TL_channels_editPhoto req = new TLRPC.TL_channels_editPhoto();
            req.channel = getInputChannel(chat_id);
            if (uploadedAvatar != null) {
                req.photo = new TLRPC.TL_inputChatUploadedPhoto();
                req.photo.file = uploadedAvatar;
            } else {
                req.photo = new TLRPC.TL_inputChatPhotoEmpty();
            }
            request = req;
        } else {
            TLRPC.TL_messages_editChatPhoto req = new TLRPC.TL_messages_editChatPhoto();
            req.chat_id = chat_id;
            if (uploadedAvatar != null) {
                req.photo = new TLRPC.TL_inputChatUploadedPhoto();
                req.photo.file = uploadedAvatar;
            } else {
                req.photo = new TLRPC.TL_inputChatPhotoEmpty();
            }
            request = req;
        }
        getConnectionsManager().sendRequest(request, (response, error) -> {
            if (error != null) {
                return;
            }
            TLRPC.Updates updates = (TLRPC.Updates) response;
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
            }
            processUpdates(updates, false);
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
        getNotificationCenter().postNotificationName(NotificationCenter.appDidLogout);
        getMessagesStorage().cleanup(false);
        cleanup();
        getContactsController().deleteUnknownAppAccounts();
    }

    public void generateUpdateMessage() {
        if (BuildVars.DEBUG_VERSION || SharedConfig.lastUpdateVersion == null || SharedConfig.lastUpdateVersion.equals(BuildVars.BUILD_VERSION_STRING)) {
            return;
        }
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
                int uid = userConfig.getClientUserId();
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

    private void processChannelsUpdatesQueue(int channelId, int state) {
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
                    return;
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("HOLE IN CHANNEL " + channelId + " UPDATES QUEUE - getChannelDifference ");
                    }
                    updatesStartWaitTimeChannels.delete(channelId);
                    updatesQueueChannels.remove(channelId);
                    getChannelDifference(channelId);
                    return;
                }
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
                        return;
                    } else {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("HOLE IN UPDATES QUEUE - getDifference");
                        }
                        setUpdatesStartTime(type, 0);
                        updatesQueue.clear();
                        getDifference();
                        return;
                    }
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

    protected void loadUnknownChannel(final TLRPC.Chat channel, final long taskId) {
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
        final long newTaskId;
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

    public void startShortPoll(TLRPC.Chat chat, final boolean stop) {
        Utilities.stageQueue.postRunnable(() -> {
            if (stop) {
                needShortPollChannels.delete(chat.id);
                if (chat.megagroup) {
                    needShortPollOnlines.delete(chat.id);
                }
            } else {
                needShortPollChannels.put(chat.id, 0);
                if (shortPollChannels.indexOfKey(chat.id) < 0) {
                    getChannelDifference(chat.id, 3, 0, null);
                }
                if (chat.megagroup) {
                    needShortPollOnlines.put(chat.id, 0);
                    if (shortPollOnlines.indexOfKey(chat.id) < 0) {
                        shortPollOnlines.put(chat.id, 0);
                    }
                }
            }
        });
    }

    private void getChannelDifference(final int channelId) {
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

    protected void getChannelDifference(final int channelId, final int newDialogType, final long taskId, TLRPC.InputChannel inputChannel) {
        boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId);
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
        if (inputChannel == null || inputChannel.access_hash == 0) {
            if (taskId != 0) {
                getMessagesStorage().removePendingTask(taskId);
            }
            return;
        }
        final long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(12 + inputChannel.getObjectSize());
                data.writeInt32(6);
                data.writeInt32(channelId);
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
                final TLRPC.updates_ChannelDifference res = (TLRPC.updates_ChannelDifference) response;

                final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
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
                final TLRPC.Chat channelFinal = channel;

                final ArrayList<TLRPC.TL_updateMessageID> msgUpdates = new ArrayList<>();
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
                        final SparseArray<long[]> corrected = new SparseArray<>();
                        for (TLRPC.TL_updateMessageID update : msgUpdates) {
                            long[] ids = getMessagesStorage().updateMessageStateAndId(update.random_id, null, update.id, 0, false, channelId, -1);
                            if (ids != null) {
                                corrected.put(update.id, ids);
                            }
                        }

                        if (corrected.size() != 0) {
                            AndroidUtilities.runOnUIThread(() -> {
                                for (int a = 0; a < corrected.size(); a++) {
                                    int newId = corrected.keyAt(a);
                                    long[] ids = corrected.valueAt(a);
                                    int oldId = (int) ids[1];
                                    getSendMessagesHelper().processSentMessage(oldId);
                                    getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newId, null, ids[0], 0L, -1, false);
                                }
                            });
                        }
                    }

                    Utilities.stageQueue.postRunnable(() -> {
                        if (res instanceof TLRPC.TL_updates_channelDifference || res instanceof TLRPC.TL_updates_channelDifferenceEmpty) {
                            if (!res.new_messages.isEmpty()) {
                                final LongSparseArray<ArrayList<MessageObject>> messages = new LongSparseArray<>();
                                ImageLoader.saveMessagesThumbs(res.new_messages);

                                final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                                long dialog_id = -channelId;
                                Integer inboxValue = dialogs_read_inbox_max.get(dialog_id);
                                if (inboxValue == null) {
                                    inboxValue = getMessagesStorage().getDialogReadMax(false, dialog_id);
                                    dialogs_read_inbox_max.put(dialog_id, inboxValue);
                                }

                                Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                                if (outboxValue == null) {
                                    outboxValue = getMessagesStorage().getDialogReadMax(true, dialog_id);
                                    dialogs_read_outbox_max.put(dialog_id, outboxValue);
                                }

                                for (int a = 0; a < res.new_messages.size(); a++) {
                                    TLRPC.Message message = res.new_messages.get(a);
                                    message.unread = !(channelFinal != null && channelFinal.left || (message.out ? outboxValue : inboxValue) >= message.id || message.action instanceof TLRPC.TL_messageActionChannelCreate);
                                    if (channelFinal != null && channelFinal.megagroup) {
                                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                    }

                                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, createdDialogIds.contains(dialog_id));
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
                            long dialog_id = -channelId;

                            Integer inboxValue = dialogs_read_inbox_max.get(dialog_id);
                            if (inboxValue == null) {
                                inboxValue = getMessagesStorage().getDialogReadMax(false, dialog_id);
                                dialogs_read_inbox_max.put(dialog_id, inboxValue);
                            }

                            Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                            if (outboxValue == null) {
                                outboxValue = getMessagesStorage().getDialogReadMax(true, dialog_id);
                                dialogs_read_outbox_max.put(dialog_id, outboxValue);
                            }

                            for (int a = 0; a < res.messages.size(); a++) {
                                TLRPC.Message message = res.messages.get(a);
                                message.dialog_id = -channelId;
                                message.unread = !(message.action instanceof TLRPC.TL_messageActionChannelCreate || channelFinal != null && channelFinal.left || (message.out ? outboxValue : inboxValue) >= message.id);
                                if (channelFinal != null && channelFinal.megagroup) {
                                    message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                }
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

    private void checkChannelError(String text, int channelId) {
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

    public void getDifference(int pts, final int date, final int qts, boolean slice) {
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
                final TLRPC.updates_Difference res = (TLRPC.updates_Difference) response;
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

                    final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
                    final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();
                    for (int a = 0; a < res.users.size(); a++) {
                        TLRPC.User user = res.users.get(a);
                        usersDict.put(user.id, user);
                    }
                    for (int a = 0; a < res.chats.size(); a++) {
                        TLRPC.Chat chat = res.chats.get(a);
                        chatsDict.put(chat.id, chat);
                    }

                    final ArrayList<TLRPC.TL_updateMessageID> msgUpdates = new ArrayList<>();
                    if (!res.other_updates.isEmpty()) {
                        for (int a = 0; a < res.other_updates.size(); a++) {
                            TLRPC.Update upd = res.other_updates.get(a);
                            if (upd instanceof TLRPC.TL_updateMessageID) {
                                msgUpdates.add((TLRPC.TL_updateMessageID) upd);
                                res.other_updates.remove(a);
                                a--;
                            } else if (getUpdateType(upd) == 2) {
                                int channelId = getUpdateChannelId(upd);
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
                            final SparseArray<long[]> corrected = new SparseArray<>();
                            for (int a = 0; a < msgUpdates.size(); a++) {
                                TLRPC.TL_updateMessageID update = msgUpdates.get(a);
                                long[] ids = getMessagesStorage().updateMessageStateAndId(update.random_id, null, update.id, 0, false, 0, -1);
                                if (ids != null) {
                                    corrected.put(update.id, ids);
                                }
                            }

                            if (corrected.size() != 0) {
                                AndroidUtilities.runOnUIThread(() -> {
                                    for (int a = 0; a < corrected.size(); a++) {
                                        int newId = corrected.keyAt(a);
                                        long[] ids = corrected.valueAt(a);
                                        int oldId = (int) ids[1];
                                        getSendMessagesHelper().processSentMessage(oldId);
                                        getNotificationCenter().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newId, null, ids[0], 0L, -1, false);
                                    }
                                });
                            }
                        }

                        Utilities.stageQueue.postRunnable(() -> {
                            if (!res.new_messages.isEmpty() || !res.new_encrypted_messages.isEmpty()) {
                                final LongSparseArray<ArrayList<MessageObject>> messages = new LongSparseArray<>();
                                for (int b = 0; b < res.new_encrypted_messages.size(); b++) {
                                    TLRPC.EncryptedMessage encryptedMessage = res.new_encrypted_messages.get(b);
                                    ArrayList<TLRPC.Message> decryptedMessages = getSecretChatHelper().decryptMessage(encryptedMessage);
                                    if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                                        res.new_messages.addAll(decryptedMessages);
                                    }
                                }

                                ImageLoader.saveMessagesThumbs(res.new_messages);

                                final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                                int clientUserId = getUserConfig().getClientUserId();
                                for (int a = 0; a < res.new_messages.size(); a++) {
                                    TLRPC.Message message = res.new_messages.get(a);
                                    if (message.dialog_id == 0) {
                                        if (message.to_id.chat_id != 0) {
                                            message.dialog_id = -message.to_id.chat_id;
                                        } else {
                                            if (message.to_id.user_id == getUserConfig().getClientUserId()) {
                                                message.to_id.user_id = message.from_id;
                                            }
                                            message.dialog_id = message.to_id.user_id;
                                        }
                                    }

                                    if ((int) message.dialog_id != 0) {
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

                                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, createdDialogIds.contains(message.dialog_id));

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

    public void markDialogAsUnread(long did, TLRPC.InputPeer peer, long taskId) {
        TLRPC.Dialog dialog = dialogs_dict.get(did);
        if (dialog != null) {
            dialog.unread_mark = true;
            if (dialog.unread_count == 0 && !isDialogMuted(did)) {
                unreadUnmutedDialogs++;
            }
            getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
            getMessagesStorage().setDialogUnread(did, true);
        }
        int lower_id = (int) did;
        if (lower_id != 0) {
            TLRPC.TL_messages_markDialogUnread req = new TLRPC.TL_messages_markDialogUnread();
            req.unread = true;
            if (peer == null) {
                peer = getInputPeer(lower_id);
            }
            if (peer instanceof TLRPC.TL_inputPeerEmpty) {
                return;
            }
            TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
            inputDialogPeer.peer = peer;
            req.peer = inputDialogPeer;

            final long newTaskId;
            if (taskId == 0) {
                NativeByteBuffer data = null;
                try {
                    data = new NativeByteBuffer(12 + peer.getObjectSize());
                    data.writeInt32(9);
                    data.writeInt64(did);
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
                final TLRPC.Vector vector = (TLRPC.Vector) response;
                for (int a = 0, size = vector.objects.size(); a < size; a++) {
                    TLRPC.DialogPeer peer = (TLRPC.DialogPeer) vector.objects.get(a);
                    if (peer instanceof TLRPC.TL_dialogPeer) {
                        TLRPC.TL_dialogPeer dialogPeer = (TLRPC.TL_dialogPeer) peer;
                        long did;
                        if (dialogPeer.peer.user_id != 0) {
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

        final long newTaskId;
        if (taskId == 0) {
            ArrayList<TLRPC.Dialog> dialogs = getDialogs(folderId);
            if (dialogs.isEmpty()) {
                return;
            }

            int size = 0;
            for (int a = 0, N = dialogs.size(); a < N; a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                if (dialog instanceof TLRPC.TL_dialogFolder) {
                    continue;
                }
                if (!dialog.pinned) {
                    break;
                }
                getMessagesStorage().setDialogPinned(dialog.id, dialog.pinnedNum);
                if ((int) dialog.id != 0) {
                    TLRPC.InputPeer inputPeer = getInputPeer((int) dialogs.get(a).id);
                    TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                    inputDialogPeer.peer = inputPeer;
                    req.order.add(inputDialogPeer);
                    size += inputDialogPeer.getObjectSize();
                }
            }

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

    public boolean pinDialog(long did, boolean pin, TLRPC.InputPeer peer, long taskId) {
        int lower_id = (int) did;
        TLRPC.Dialog dialog = dialogs_dict.get(did);
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
                    break;
                }
                maxPinnedNum = Math.max(d.pinnedNum, maxPinnedNum);
            }
            dialog.pinnedNum = maxPinnedNum + 1;
        } else {
            dialog.pinnedNum = 0;
        }
        sortDialogs(null);
        if (!pin && dialogs.get(dialogs.size() - 1) == dialog && !dialogsEndReached.get(folderId)) {
            dialogs.remove(dialogs.size() - 1);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        if (lower_id != 0) {
            if (taskId != -1) {
                TLRPC.TL_messages_toggleDialogPin req = new TLRPC.TL_messages_toggleDialogPin();
                req.pinned = pin;
                if (peer == null) {
                    peer = getInputPeer(lower_id);
                }
                if (peer instanceof TLRPC.TL_inputPeerEmpty) {
                    return false;
                }
                TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                inputDialogPeer.peer = peer;
                req.peer = inputDialogPeer;

                final long newTaskId;
                if (taskId == 0) {
                    NativeByteBuffer data = null;
                    try {
                        data = new NativeByteBuffer(16 + peer.getObjectSize());
                        data.writeInt32(4);
                        data.writeInt64(did);
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
        getMessagesStorage().setDialogPinned(did, dialog.pinnedNum);
        return true;
    }

    public void loadPinnedDialogs(final int folderId, final long newDialogId, final ArrayList<Long> order) {
        if (loadingPinnedDialogs.indexOfKey(folderId) >= 0 || getUserConfig().isPinnedDialogsLoaded(folderId)) {
            return;
        }
        loadingPinnedDialogs.put(folderId, 1);
        TLRPC.TL_messages_getPinnedDialogs req = new TLRPC.TL_messages_getPinnedDialogs();
        req.folder_id = folderId;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                final TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                ArrayList<TLRPC.Dialog> newPinnedDialogs = new ArrayList<>(res.dialogs);
                fetchFolderInLoadedPinnedDialogs(res);
                final TLRPC.TL_messages_dialogs toCache = new TLRPC.TL_messages_dialogs();
                toCache.users.addAll(res.users);
                toCache.chats.addAll(res.chats);
                toCache.dialogs.addAll(res.dialogs);
                toCache.messages.addAll(res.messages);

                final LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
                final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
                final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();

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
                    if (message.to_id.channel_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                        if (chat != null && chat.left) {
                            continue;
                        }
                    } else if (message.to_id.chat_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.to_id.chat_id);
                        if (chat != null && chat.migrated_to != null) {
                            continue;
                        }
                    }
                    MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false);
                    new_dialogMessage.put(messageObject.getDialogId(), messageObject);
                }
                boolean firstIsFolder = !newPinnedDialogs.isEmpty() && newPinnedDialogs.get(0) instanceof TLRPC.TL_dialogFolder;
                for (int a = 0, N = newPinnedDialogs.size(); a < N; a++) {
                    TLRPC.Dialog d = newPinnedDialogs.get(a);
                    d.pinned = true;
                    DialogObject.initDialog(d);
                    if (DialogObject.isChannel(d)) {
                        TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                        if (chat != null && chat.left) {
                            continue;
                        }
                    } else if ((int) d.id < 0) {
                        TLRPC.Chat chat = chatsDict.get(-(int) d.id);
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
                        if ((int) dialog.id == 0) {
                            if (pinnedNum < newPinnedDialogs.size()) {
                                newPinnedDialogs.add(pinnedNum, dialog);
                            } else {
                                newPinnedDialogs.add(dialog);
                            }
                            pinnedNum++;
                            continue;
                        }
                        if (!dialog.pinned) {
                            break;
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
                        for (int a = 0, N = newPinnedDialogs.size(); a < N; a++) {
                            TLRPC.Dialog dialog = newPinnedDialogs.get(a);
                            dialog.pinnedNum = (N - a) + maxPinnedNum;

                            pinnedDialogs.add(dialog.id);
                            TLRPC.Dialog d = dialogs_dict.get(dialog.id);
                            if (d != null) {
                                d.pinned = true;
                                d.pinnedNum = dialog.pinnedNum;
                                getMessagesStorage().setDialogPinned(dialog.id, dialog.pinnedNum);
                            } else {
                                added = true;
                                dialogs_dict.put(dialog.id, dialog);
                                MessageObject messageObject = new_dialogMessage.get(dialog.id);
                                dialogMessage.put(dialog.id, messageObject);
                                if (messageObject != null && messageObject.messageOwner.to_id.channel_id == 0) {
                                    dialogMessagesByIds.put(messageObject.getId(), messageObject);
                                    if (messageObject.messageOwner.random_id != 0) {
                                        dialogMessagesByRandomIds.put(messageObject.messageOwner.random_id, messageObject);
                                    }
                                }
                            }

                            changed = true;
                        }
                    }
                    if (changed) {
                        if (added) {
                            allDialogs.clear();
                            for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                                allDialogs.add(dialogs_dict.valueAt(a));
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

    public void generateJoinMessage(final int chat_id, boolean ignoreLeft) {
        TLRPC.Chat chat = getChat(chat_id);
        if (chat == null || !ChatObject.isChannel(chat_id, currentAccount) || (chat.left || chat.kicked) && !ignoreLeft) {
            return;
        }

        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
        message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.local_id = message.id = getUserConfig().getNewMessageId();
        message.date = getConnectionsManager().getCurrentTime();
        message.from_id = getUserConfig().getClientUserId();
        message.to_id = new TLRPC.TL_peerChannel();
        message.to_id.channel_id = chat_id;
        message.dialog_id = -chat_id;
        message.post = true;
        message.action = new TLRPC.TL_messageActionChatAddUser();
        message.action.users.add(getUserConfig().getClientUserId());
        if (chat.megagroup) {
            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
        }
        getUserConfig().saveConfig(false);

        final ArrayList<MessageObject> pushMessages = new ArrayList<>();
        final ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();

        messagesArr.add(message);
        MessageObject obj = new MessageObject(currentAccount, message, true);
        pushMessages.add(obj);

        getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(pushMessages, true, false, null)));
        getMessagesStorage().putMessages(messagesArr, true, true, false, 0, false);

        AndroidUtilities.runOnUIThread(() -> {
            updateInterfaceWithMessages(-chat_id, pushMessages, false);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        });
    }

    protected void deleteMessagesByPush(long dialogId, ArrayList<Integer> ids, int channelId) {
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
                    MessageObject obj = dialogMessage.get((long) -channelId);
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
            ArrayList<Long> dialogIds = getMessagesStorage().markMessagesAsDeleted(ids, false, channelId, true, false);
            getMessagesStorage().updateDialogsWithDeletedMessages(ids, dialogIds, false, channelId);
        });
    }

    public void checkChannelInviter(final int chat_id) {
        AndroidUtilities.runOnUIThread(() -> {
            final TLRPC.Chat chat = getChat(chat_id);
            if (chat == null || !ChatObject.isChannel(chat_id, currentAccount) || chat.creator) {
                return;
            }
            TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
            req.channel = getInputChannel(chat_id);
            req.user_id = new TLRPC.TL_inputUserSelf();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                final TLRPC.TL_channels_channelParticipant res = (TLRPC.TL_channels_channelParticipant) response;
                if (res != null && res.participant instanceof TLRPC.TL_channelParticipantSelf && res.participant.inviter_id != getUserConfig().getClientUserId()) {
                    if (chat.megagroup && getMessagesStorage().isMigratedChat(chat.id)) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> putUsers(res.users, false));
                    getMessagesStorage().putUsersAndChats(res.users, null, true, true);

                    TLRPC.TL_messageService message = new TLRPC.TL_messageService();
                    message.media_unread = true;
                    message.unread = true;
                    message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    message.post = true;
                    if (chat.megagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }
                    message.local_id = message.id = getUserConfig().getNewMessageId();
                    message.date = res.participant.date;
                    message.action = new TLRPC.TL_messageActionChatAddUser();
                    message.from_id = res.participant.inviter_id;
                    message.action.users.add(getUserConfig().getClientUserId());
                    message.to_id = new TLRPC.TL_peerChannel();
                    message.to_id.channel_id = chat_id;
                    message.dialog_id = -chat_id;
                    getUserConfig().saveConfig(false);

                    final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                    final ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();

                    ConcurrentHashMap<Integer, TLRPC.User> usersDict = new ConcurrentHashMap<>();
                    for (int a = 0; a < res.users.size(); a++) {
                        TLRPC.User user = res.users.get(a);
                        usersDict.put(user.id, user);
                    }

                    messagesArr.add(message);
                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, true);
                    pushMessages.add(obj);

                    getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> getNotificationsController().processNewMessages(pushMessages, true, false, null)));
                    getMessagesStorage().putMessages(messagesArr, true, true, false, 0, false);

                    AndroidUtilities.runOnUIThread(() -> {
                        updateInterfaceWithMessages(-chat_id, pushMessages, false);
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                    });
                }
            });
        });
    }

    private int getUpdateType(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateReadMessagesContents || update instanceof TLRPC.TL_updateReadHistoryInbox ||
                update instanceof TLRPC.TL_updateReadHistoryOutbox || update instanceof TLRPC.TL_updateDeleteMessages || update instanceof TLRPC.TL_updateWebPage ||
                update instanceof TLRPC.TL_updateEditMessage || update instanceof TLRPC.TL_updateFolderPeers) {
            return 0;
        } else if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
            return 1;
        } else if (update instanceof TLRPC.TL_updateNewChannelMessage || update instanceof TLRPC.TL_updateDeleteChannelMessages || update instanceof TLRPC.TL_updateEditChannelMessage ||
                update instanceof TLRPC.TL_updateChannelWebPage) {
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

    private static int getUpdateChannelId(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateNewChannelMessage) {
            return ((TLRPC.TL_updateNewChannelMessage) update).message.to_id.channel_id;
        } else if (update instanceof TLRPC.TL_updateEditChannelMessage) {
            return ((TLRPC.TL_updateEditChannelMessage) update).message.to_id.channel_id;
        } else if (update instanceof TLRPC.TL_updateReadChannelOutbox) {
            return ((TLRPC.TL_updateReadChannelOutbox) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelMessageViews) {
            return ((TLRPC.TL_updateChannelMessageViews) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelTooLong) {
            return ((TLRPC.TL_updateChannelTooLong) update).channel_id;
        } else if (update instanceof TLRPC.TL_updateChannelPinnedMessage) {
            return ((TLRPC.TL_updateChannelPinnedMessage) update).channel_id;
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
        } else {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("trying to get unknown update channel_id for " + update);
            }
            return 0;
        }
    }

    public void processUpdates(final TLRPC.Updates updates, boolean fromQueue) {
        ArrayList<Integer> needGetChannelsDiff = null;
        boolean needGetDiff = false;
        boolean needReceivedQueue = false;
        boolean updateStatus = false;
        if (updates instanceof TLRPC.TL_updateShort) {
            ArrayList<TLRPC.Update> arr = new ArrayList<>();
            arr.add(updates.update);
            processUpdateArray(arr, null, null, false, updates.date);
        } else if (updates instanceof TLRPC.TL_updateShortChatMessage || updates instanceof TLRPC.TL_updateShortMessage) {
            final int user_id = updates instanceof TLRPC.TL_updateShortChatMessage ? updates.from_id : updates.user_id;
            TLRPC.User user = getUser(user_id);
            TLRPC.User user2 = null;
            TLRPC.User user3 = null;
            TLRPC.Chat channel = null;

            if (user == null || user.min) {
                user = getMessagesStorage().getUserSync(user_id);
                if (user != null && user.min) {
                    user = null;
                }
                putUser(user, true);
            }

            boolean needFwdUser = false;
            if (updates.fwd_from != null) {
                if (updates.fwd_from.from_id != 0) {
                    user2 = getUser(updates.fwd_from.from_id);
                    if (user2 == null) {
                        user2 = getMessagesStorage().getUserSync(updates.fwd_from.from_id);
                        putUser(user2, true);
                    }
                    needFwdUser = true;
                }
                if (updates.fwd_from.channel_id != 0) {
                    channel = getChat(updates.fwd_from.channel_id);
                    if (channel == null) {
                        channel = getMessagesStorage().getChatSync(updates.fwd_from.channel_id);
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
                        int uid = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
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
            if (user != null && user.status != null && user.status.expires <= 0 && Math.abs(getConnectionsManager().getCurrentTime() - updates.date) < 30) {
                onlinePrivacy.put(user.id, updates.date);
                updateStatus = true;
            }

            if (missingData) {
                needGetDiff = true;
            } else {
                if (getMessagesStorage().getLastPtsValue() + updates.pts_count == updates.pts) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = updates.id;
                    int clientUserId = getUserConfig().getClientUserId();
                    if (updates instanceof TLRPC.TL_updateShortMessage) {
                        if (updates.out) {
                            message.from_id = clientUserId;
                        } else {
                            message.from_id = user_id;
                        }
                        message.to_id = new TLRPC.TL_peerUser();
                        message.to_id.user_id = user_id;
                        message.dialog_id = user_id;
                    } else {
                        message.from_id = user_id;
                        message.to_id = new TLRPC.TL_peerChat();
                        message.to_id.chat_id = updates.chat_id;
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
                    message.reply_to_msg_id = updates.reply_to_msg_id;
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
                    final MessageObject obj = new MessageObject(currentAccount, message, createdDialogIds.contains(message.dialog_id));
                    final ArrayList<MessageObject> objArr = new ArrayList<>();
                    objArr.add(obj);
                    ArrayList<TLRPC.Message> arr = new ArrayList<>();
                    arr.add(message);
                    if (updates instanceof TLRPC.TL_updateShortMessage) {
                        final boolean printUpdate = !updates.out && updatePrintingUsersWithNewMessages(updates.user_id, objArr);
                        if (printUpdate) {
                            updatePrintingStrings();
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (printUpdate) {
                                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }
                            updateInterfaceWithMessages(user_id, objArr, false);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                        });
                    } else {
                        final boolean printUpdate = updatePrintingUsersWithNewMessages(-updates.chat_id, objArr);
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
            SparseArray<TLRPC.Chat> minChannels = null;
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
                                minChannels = new SparseArray<>();
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
                        int channelId = message.to_id.channel_id;
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
                            if (gettingDifference || updatesStartWaitTimePts == 0 || updatesStartWaitTimePts != 0 && Math.abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
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
                            if (gettingDifference || updatesStartWaitTimeQts == 0 || updatesStartWaitTimeQts != 0 && Math.abs(System.currentTimeMillis() - updatesStartWaitTimeQts) <= 1500) {
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
                        int channelId = getUpdateChannelId(update);
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
                                boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId);
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
                int key = updatesQueueChannels.keyAt(a);
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

    public boolean processUpdateArray(ArrayList<TLRPC.Update> updates, final ArrayList<TLRPC.User> usersArr, final ArrayList<TLRPC.Chat> chatsArr, boolean fromGetDifference, int date) {
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
        ArrayList<TLRPC.Message> scheduledMessagesArr = null;
        LongSparseArray<ArrayList<MessageObject>> editingMessages = null;
        SparseArray<SparseIntArray> channelViews = null;
        SparseLongArray markAsReadMessagesInbox = null;
        SparseLongArray markAsReadMessagesOutbox = null;
        ArrayList<Long> markAsReadMessages = null;
        SparseIntArray markAsReadEncrypted = null;
        SparseArray<ArrayList<Integer>> deletedMessages = null;
        SparseArray<ArrayList<Integer>> scheduledDeletedMessages = null;
        SparseIntArray clearHistoryMessages = null;
        ArrayList<TLRPC.ChatParticipants> chatInfoToUpdate = null;
        ArrayList<TLRPC.Update> updatesOnMainThread = null;
        ArrayList<TLRPC.TL_updateEncryptedMessagesRead> tasks = null;
        ArrayList<Integer> contactsIds = null;

        boolean checkForUsers = true;
        ConcurrentHashMap<Integer, TLRPC.User> usersDict;
        ConcurrentHashMap<Integer, TLRPC.Chat> chatsDict;
        if (usersArr != null) {
            usersDict = new ConcurrentHashMap<>();
            for (int a = 0, size = usersArr.size(); a < size; a++) {
                TLRPC.User user = usersArr.get(a);
                usersDict.put(user.id, user);
            }
        } else {
            checkForUsers = false;
            usersDict = users;
        }
        if (chatsArr != null) {
            chatsDict = new ConcurrentHashMap<>();
            for (int a = 0, size = chatsArr.size(); a < size; a++) {
                TLRPC.Chat chat = chatsArr.get(a);
                chatsDict.put(chat.id, chat);
            }
        } else {
            checkForUsers = false;
            chatsDict = chats;
        }
        if (fromGetDifference) {
            checkForUsers = false;
        }

        if (usersArr != null || chatsArr != null) {
            AndroidUtilities.runOnUIThread(() -> {
                putUsers(usersArr, false);
                putChats(chatsArr, false);
            });
        }

        int interfaceUpdateMask = 0;

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
                        FileLog.d(baseUpdate + " channelId = " + message.to_id.channel_id);
                    }
                    if (!message.out && message.from_id == getUserConfig().getClientUserId()) {
                        message.out = true;
                    }
                }
                TLRPC.Chat chat = null;
                int chat_id = 0;
                int user_id = 0;
                if (message.to_id.channel_id != 0) {
                    chat_id = message.to_id.channel_id;
                } else if (message.to_id.chat_id != 0) {
                    chat_id = message.to_id.chat_id;
                } else if (message.to_id.user_id != 0) {
                    user_id = message.to_id.user_id;
                }
                if (chat_id != 0) {
                    chat = chatsDict.get(chat_id);
                    if (chat == null) {
                        chat = getChat(chat_id);
                    }
                    if (chat == null) {
                        chat = getMessagesStorage().getChatSync(chat_id);
                        putChat(chat, true);
                    }
                }
                if (checkForUsers) {
                    if (chat_id != 0) {
                        if (chat == null) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("not found chat " + chat_id);
                            }
                            return false;
                        }
                    }

                    int count = 3 + message.entities.size();
                    for (int a = 0; a < count; a++) {
                        boolean allowMin = false;
                        if (a != 0) {
                            if (a == 1) {
                                user_id = message.from_id;
                                if (message.post) {
                                    allowMin = true;
                                }
                            } else if (a == 2) {
                                user_id = message.fwd_from != null ? message.fwd_from.from_id : 0;
                            } else {
                                TLRPC.MessageEntity entity = message.entities.get(a - 3);
                                user_id = entity instanceof TLRPC.TL_messageEntityMentionName ? ((TLRPC.TL_messageEntityMentionName) entity).user_id : 0;
                            }
                        }
                        if (user_id > 0) {
                            TLRPC.User user = usersDict.get(user_id);
                            if (user == null || !allowMin && user.min) {
                                user = getUser(user_id);
                            }
                            if (user == null || !allowMin && user.min) {
                                user = getMessagesStorage().getUserSync(user_id);
                                if (user != null && !allowMin && user.min) {
                                    user = null;
                                }
                                putUser(user, true);
                            }
                            if (user == null) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("not found user " + user_id);
                                }
                                return false;
                            }
                            if (a == 1 && user.status != null && user.status.expires <= 0 && Math.abs(getConnectionsManager().getCurrentTime() - message.date) < 30) {
                                onlinePrivacy.put(user_id, message.date);
                                interfaceUpdateMask |= UPDATE_MASK_STATUS;
                            }
                        }
                    }
                }
                if (chat != null && chat.megagroup) {
                    message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                }

                if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    TLRPC.User user = usersDict.get(message.action.user_id);
                    if (user != null && user.bot) {
                        message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                        message.flags |= 64;
                    } else if (message.from_id == getUserConfig().getClientUserId() && message.action.user_id == getUserConfig().getClientUserId()) {
                        continue;
                    }
                }

                ImageLoader.saveMessageThumbs(message);

                int clientUserId = getUserConfig().getClientUserId();
                if (message.to_id.chat_id != 0) {
                    message.dialog_id = -message.to_id.chat_id;
                } else if (message.to_id.channel_id != 0) {
                    message.dialog_id = -message.to_id.channel_id;
                } else {
                    if (message.to_id.user_id == clientUserId) {
                        message.to_id.user_id = message.from_id;
                    }
                    message.dialog_id = message.to_id.user_id;
                }

                if (baseUpdate instanceof TLRPC.TL_updateNewScheduledMessage) {
                    if (scheduledMessagesArr == null) {
                        scheduledMessagesArr = new ArrayList<>();
                    }
                    scheduledMessagesArr.add(message);

                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, createdScheduledDialogIds.contains(message.dialog_id));
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

                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, createdDialogIds.contains(message.dialog_id));
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
                    if ((!obj.isOut() || obj.messageOwner.from_scheduled) && obj.isUnread()) {
                        if (pushMessages == null) {
                            pushMessages = new ArrayList<>();
                        }
                        pushMessages.add(obj);
                    }
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateReadMessagesContents) {
                TLRPC.TL_updateReadMessagesContents update = (TLRPC.TL_updateReadMessagesContents) baseUpdate;
                if (markAsReadMessages == null) {
                    markAsReadMessages = new ArrayList<>();
                }
                for (int a = 0, size = update.messages.size(); a < size; a++) {
                    long id = update.messages.get(a);
                    markAsReadMessages.add(id);
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelReadMessagesContents) {
                TLRPC.TL_updateChannelReadMessagesContents update = (TLRPC.TL_updateChannelReadMessagesContents) baseUpdate;
                if (markAsReadMessages == null) {
                    markAsReadMessages = new ArrayList<>();
                }
                for (int a = 0, size = update.messages.size(); a < size; a++) {
                    long id = update.messages.get(a);
                    id |= ((long) update.channel_id) << 32;
                    markAsReadMessages.add(id);
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateReadHistoryInbox) {
                TLRPC.TL_updateReadHistoryInbox update = (TLRPC.TL_updateReadHistoryInbox) baseUpdate;
                long dialog_id;
                if (markAsReadMessagesInbox == null) {
                    markAsReadMessagesInbox = new SparseLongArray();
                }
                if (update.peer.chat_id != 0) {
                    markAsReadMessagesInbox.put(-update.peer.chat_id, (long) update.max_id);
                    dialog_id = -update.peer.chat_id;
                } else {
                    markAsReadMessagesInbox.put(update.peer.user_id, (long) update.max_id);
                    dialog_id = update.peer.user_id;
                }
                Integer value = dialogs_read_inbox_max.get(dialog_id);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(false, dialog_id);
                }
                dialogs_read_inbox_max.put(dialog_id, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateReadHistoryOutbox) {
                TLRPC.TL_updateReadHistoryOutbox update = (TLRPC.TL_updateReadHistoryOutbox) baseUpdate;
                long dialog_id;
                if (markAsReadMessagesOutbox == null) {
                    markAsReadMessagesOutbox = new SparseLongArray();
                }
                if (update.peer.chat_id != 0) {
                    markAsReadMessagesOutbox.put(-update.peer.chat_id, (long) update.max_id);
                    dialog_id = -update.peer.chat_id;
                } else {
                    markAsReadMessagesOutbox.put(update.peer.user_id, (long) update.max_id);
                    dialog_id = update.peer.user_id;
                }
                Integer value = dialogs_read_outbox_max.get(dialog_id);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(true, dialog_id);
                }
                dialogs_read_outbox_max.put(dialog_id, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateDeleteMessages) {
                TLRPC.TL_updateDeleteMessages update = (TLRPC.TL_updateDeleteMessages) baseUpdate;
                if (deletedMessages == null) {
                    deletedMessages = new SparseArray<>();
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
                    scheduledDeletedMessages = new SparseArray<>();
                }
                ArrayList<Integer> arrayList;
                int id;
                if (update.peer instanceof TLRPC.TL_peerChannel) {
                    arrayList = scheduledDeletedMessages.get(id = update.peer.channel_id);
                } else {
                    arrayList = scheduledDeletedMessages.get(id = 0);
                }
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    scheduledDeletedMessages.put(id, arrayList);
                }
                arrayList.addAll(update.messages);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserTyping || baseUpdate instanceof TLRPC.TL_updateChatUserTyping) {
                int user_id;
                int chat_id;
                TLRPC.SendMessageAction action;
                if (baseUpdate instanceof TLRPC.TL_updateUserTyping) {
                    TLRPC.TL_updateUserTyping update = (TLRPC.TL_updateUserTyping) baseUpdate;
                    user_id = update.user_id;
                    action = update.action;
                    chat_id = 0;
                } else {
                    TLRPC.TL_updateChatUserTyping update = (TLRPC.TL_updateChatUserTyping) baseUpdate;
                    chat_id = update.chat_id;
                    user_id = update.user_id;
                    action = update.action;
                }
                if (user_id != getUserConfig().getClientUserId()) {
                    long uid = -chat_id;
                    if (uid == 0) {
                        uid = user_id;
                    }
                    ArrayList<PrintingUser> arr = printingUsers.get(uid);
                    if (action instanceof TLRPC.TL_sendMessageCancelAction) {
                        if (arr != null) {
                            for (int a = 0, size = arr.size(); a < size; a++) {
                                PrintingUser pu = arr.get(a);
                                if (pu.userId == user_id) {
                                    arr.remove(a);
                                    printChanged = true;
                                    break;
                                }
                            }
                            if (arr.isEmpty()) {
                                printingUsers.remove(uid);
                            }
                        }
                    } else {
                        if (arr == null) {
                            arr = new ArrayList<>();
                            printingUsers.put(uid, arr);
                        }
                        boolean exist = false;
                        for (PrintingUser u : arr) {
                            if (u.userId == user_id) {
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
                            newUser.userId = user_id;
                            newUser.lastTime = currentTime;
                            newUser.action = action;
                            arr.add(newUser);
                            printChanged = true;
                        }
                    }
                    if (Math.abs(getConnectionsManager().getCurrentTime() - date) < 30) {
                        onlinePrivacy.put(user_id, date);
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
                    long uid = ((long) cid) << 32;
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
                        MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, createdDialogIds.contains(uid));
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
                    long uid = ((long) update.chat_id) << 32;
                    ArrayList<PrintingUser> arr = printingUsers.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        printingUsers.put(uid, arr);
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
            } else if (baseUpdate instanceof TLRPC.TL_updateUserBlocked) {
                final TLRPC.TL_updateUserBlocked finalUpdate = (TLRPC.TL_updateUserBlocked) baseUpdate;
                getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    if (finalUpdate.blocked) {
                        if (blockedUsers.indexOfKey(finalUpdate.user_id) < 0) {
                            blockedUsers.put(finalUpdate.user_id, 1);
                        }
                    } else {
                        blockedUsers.delete(finalUpdate.user_id);
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.blockedUsersDidLoad);
                }));
            } else if (baseUpdate instanceof TLRPC.TL_updateNotifySettings) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateServiceNotification) {
                final TLRPC.TL_updateServiceNotification update = (TLRPC.TL_updateServiceNotification) baseUpdate;
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
                    newMessage.from_id = 777000;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = getUserConfig().getClientUserId();
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
                    MessageObject obj = new MessageObject(currentAccount, newMessage, usersDict, chatsDict, createdDialogIds.contains(newMessage.dialog_id));
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
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);

                TLRPC.TL_updateFolderPeers update = (TLRPC.TL_updateFolderPeers) baseUpdate;
                getMessagesStorage().setDialogsFolderId(update.folder_peers, null, 0, 0);
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
                int channelPts = channelsPts.get(update.channel_id);
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
                long message_id = update.max_id;
                message_id |= ((long) update.channel_id) << 32;
                long dialog_id = -update.channel_id;
                if (markAsReadMessagesInbox == null) {
                    markAsReadMessagesInbox = new SparseLongArray();
                }
                markAsReadMessagesInbox.put(-update.channel_id, message_id);
                Integer value = dialogs_read_inbox_max.get(dialog_id);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(false, dialog_id);
                }
                dialogs_read_inbox_max.put(dialog_id, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateReadChannelOutbox) {
                TLRPC.TL_updateReadChannelOutbox update = (TLRPC.TL_updateReadChannelOutbox) baseUpdate;
                long message_id = update.max_id;
                message_id |= ((long) update.channel_id) << 32;
                long dialog_id = -update.channel_id;
                if (markAsReadMessagesOutbox == null) {
                    markAsReadMessagesOutbox = new SparseLongArray();
                }
                markAsReadMessagesOutbox.put(-update.channel_id, message_id);
                Integer value = dialogs_read_outbox_max.get(dialog_id);
                if (value == null) {
                    value = getMessagesStorage().getDialogReadMax(true, dialog_id);
                }
                dialogs_read_outbox_max.put(dialog_id, Math.max(value, update.max_id));
            } else if (baseUpdate instanceof TLRPC.TL_updateDeleteChannelMessages) {
                TLRPC.TL_updateDeleteChannelMessages update = (TLRPC.TL_updateDeleteChannelMessages) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                if (deletedMessages == null) {
                    deletedMessages = new SparseArray<>();
                }
                ArrayList<Integer> arrayList = deletedMessages.get(update.channel_id);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(update.channel_id, arrayList);
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
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelMessageViews) {
                TLRPC.TL_updateChannelMessageViews update = (TLRPC.TL_updateChannelMessageViews) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                if (channelViews == null) {
                    channelViews = new SparseArray<>();
                }
                SparseIntArray array = channelViews.get(update.channel_id);
                if (array == null) {
                    array = new SparseIntArray();
                    channelViews.put(update.channel_id, array);
                }
                array.put(update.id, update.views);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatParticipantAdmin) {
                TLRPC.TL_updateChatParticipantAdmin update = (TLRPC.TL_updateChatParticipantAdmin) baseUpdate;
                getMessagesStorage().updateChatInfo(update.chat_id, update.user_id, 2, update.is_admin ? 1 : 0, update.version);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatDefaultBannedRights) {
                TLRPC.TL_updateChatDefaultBannedRights update = (TLRPC.TL_updateChatDefaultBannedRights) baseUpdate;
                int chatId;
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
                int clientUserId = getUserConfig().getClientUserId();
                if (baseUpdate instanceof TLRPC.TL_updateEditChannelMessage) {
                    message = ((TLRPC.TL_updateEditChannelMessage) baseUpdate).message;
                    TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                    if (chat == null) {
                        chat = getChat(message.to_id.channel_id);
                    }
                    if (chat == null) {
                        chat = getMessagesStorage().getChatSync(message.to_id.channel_id);
                        putChat(chat, true);
                    }
                    if (chat != null && chat.megagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }
                } else {
                    message = ((TLRPC.TL_updateEditMessage) baseUpdate).message;
                    if (message.dialog_id == clientUserId) {
                        message.unread = false;
                        message.media_unread = false;
                        message.out = true;
                    }
                }
                if (!message.out && message.from_id == getUserConfig().getClientUserId()) {
                    message.out = true;
                }
                if (!fromGetDifference) {
                    for (int a = 0, count = message.entities.size(); a < count; a++) {
                        TLRPC.MessageEntity entity = message.entities.get(a);
                        if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                            int user_id = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
                            TLRPC.User user = usersDict.get(user_id);
                            if (user == null || user.min) {
                                user = getUser(user_id);
                            }
                            if (user == null || user.min) {
                                user = getMessagesStorage().getUserSync(user_id);
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

                if (message.to_id.chat_id != 0) {
                    message.dialog_id = -message.to_id.chat_id;
                } else if (message.to_id.channel_id != 0) {
                    message.dialog_id = -message.to_id.channel_id;
                } else {
                    if (message.to_id.user_id == getUserConfig().getClientUserId()) {
                        message.to_id.user_id = message.from_id;
                    }
                    message.dialog_id = message.to_id.user_id;
                }

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

                MessageObject obj = new MessageObject(currentAccount, message, usersDict, chatsDict, createdDialogIds.contains(message.dialog_id));

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
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelPinnedMessage) {
                TLRPC.TL_updateChannelPinnedMessage update = (TLRPC.TL_updateChannelPinnedMessage) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                getMessagesStorage().updateChatPinnedMessage(update.channel_id, update.id);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatPinnedMessage) {
                TLRPC.TL_updateChatPinnedMessage update = (TLRPC.TL_updateChatPinnedMessage) baseUpdate;
                getMessagesStorage().updateChatPinnedMessage(update.chat_id, update.id);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserPinnedMessage) {
                TLRPC.TL_updateUserPinnedMessage update = (TLRPC.TL_updateUserPinnedMessage) baseUpdate;
                getMessagesStorage().updateUserPinnedMessage(update.user_id, update.id);
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
            } else if (baseUpdate instanceof TLRPC.TL_updateLangPack) {
                TLRPC.TL_updateLangPack update = (TLRPC.TL_updateLangPack) baseUpdate;
                AndroidUtilities.runOnUIThread(() -> LocaleController.getInstance().saveRemoteLocaleStringsForCurrentLocale(update.difference, currentAccount));
            } else if (baseUpdate instanceof TLRPC.TL_updateLangPackTooLong) {
                TLRPC.TL_updateLangPackTooLong update = (TLRPC.TL_updateLangPackTooLong) baseUpdate;
                LocaleController.getInstance().reloadCurrentRemoteLocale(currentAccount, update.lang_code);
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
                    clearHistoryMessages = new SparseIntArray();
                }
                int currentValue = clearHistoryMessages.get(update.channel_id);
                if (currentValue == 0 || currentValue < update.available_min_id) {
                    clearHistoryMessages.put(update.channel_id, update.available_min_id);
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateDialogUnreadMark) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateMessagePoll) {
                TLRPC.TL_updateMessagePoll update = (TLRPC.TL_updateMessagePoll) baseUpdate;
                long time = getSendMessagesHelper().getVoteSendTime(update.poll_id);
                if (Math.abs(SystemClock.uptimeMillis() - time) < 600) {
                    continue;
                }
                getMessagesStorage().updateMessagePollResults(update.poll_id, update.poll, update.results);
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
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
                getMessagesStorage().updateMessageReactions(dialogId, update.msg_id, update.peer.channel_id, update.reactions);
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

        final int interfaceUpdateMaskFinal = interfaceUpdateMask;
        final boolean printChangedArg = printChanged;

        if (contactsIds != null) {
            getContactsController().processContactsUpdates(contactsIds, usersDict);
        }

        if (pushMessages != null) {
            final ArrayList<MessageObject> pushMessagesFinal = pushMessages;
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
        }

        if (channelViews != null) {
            getMessagesStorage().putChannelViews(channelViews, true);
        }

        final LongSparseArray<ArrayList<MessageObject>> editingMessagesFinal = editingMessages;
        final SparseArray<SparseIntArray> channelViewsFinal = channelViews;
        final LongSparseArray<TLRPC.WebPage> webPagesFinal = webPages;
        final LongSparseArray<ArrayList<MessageObject>> messagesFinal = messages;
        final LongSparseArray<ArrayList<MessageObject>> scheduledMessagesFinal = scheduledMessages;
        final ArrayList<TLRPC.ChatParticipants> chatInfoToUpdateFinal = chatInfoToUpdate;
        final ArrayList<Integer> contactsIdsFinal = contactsIds;
        final ArrayList<TLRPC.Update> updatesOnMainThreadFinal = updatesOnMainThread;
        AndroidUtilities.runOnUIThread(() -> {
            int updateMask = interfaceUpdateMaskFinal;
            boolean forceDialogsUpdate = false;

            if (updatesOnMainThreadFinal != null) {
                ArrayList<TLRPC.User> dbUsers = new ArrayList<>();
                ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<>();
                SharedPreferences.Editor editor = null;
                for (int a = 0, size = updatesOnMainThreadFinal.size(); a < size; a++) {
                    final TLRPC.Update baseUpdate = updatesOnMainThreadFinal.get(a);
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
                        final TLRPC.User currentUser = getUser(update.user_id);

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
                        final TLRPC.User toDbUser = new TLRPC.TL_user();
                        toDbUser.id = update.user_id;
                        toDbUser.status = update.status;
                        dbUsersStatus.add(toDbUser);
                        if (update.user_id == getUserConfig().getClientUserId()) {
                            getNotificationsController().setLastOnlineFromOtherDevice(update.status.expires);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserName) {
                        TLRPC.TL_updateUserName update = (TLRPC.TL_updateUserName) baseUpdate;
                        final TLRPC.User currentUser = getUser(update.user_id);
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
                        final TLRPC.User toDbUser = new TLRPC.TL_user();
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
                    } else if (baseUpdate instanceof TLRPC.TL_updateFolderPeers) {
                        TLRPC.TL_updateFolderPeers update = (TLRPC.TL_updateFolderPeers) baseUpdate;
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
                        forceDialogsUpdate = true;
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserPhoto) {
                        TLRPC.TL_updateUserPhoto update = (TLRPC.TL_updateUserPhoto) baseUpdate;
                        final TLRPC.User currentUser = getUser(update.user_id);
                        if (currentUser != null) {
                            currentUser.photo = update.photo;
                        }
                        final TLRPC.User toDbUser = new TLRPC.TL_user();
                        toDbUser.id = update.user_id;
                        toDbUser.photo = update.photo;
                        dbUsers.add(toDbUser);
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserPhone) {
                        TLRPC.TL_updateUserPhone update = (TLRPC.TL_updateUserPhone) baseUpdate;
                        final TLRPC.User currentUser = getUser(update.user_id);
                        if (currentUser != null) {
                            currentUser.phone = update.phone;
                            Utilities.phoneBookQueue.postRunnable(() -> getContactsController().addContactToPhoneBook(currentUser, true));
                            if (UserObject.isUserSelf(currentUser)) {
                                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                            }
                        }
                        final TLRPC.User toDbUser = new TLRPC.TL_user();
                        toDbUser.id = update.user_id;
                        toDbUser.phone = update.phone;
                        dbUsers.add(toDbUser);
                    } else if (baseUpdate instanceof TLRPC.TL_updateNotifySettings) {
                        TLRPC.TL_updateNotifySettings update = (TLRPC.TL_updateNotifySettings) baseUpdate;
                        if (update.notify_settings instanceof TLRPC.TL_peerNotifySettings) {
                            if (editor == null) {
                                editor = notificationsPreferences.edit();
                            }
                            int currentTime1 = getConnectionsManager().getCurrentTime();
                            if (update.peer instanceof TLRPC.TL_notifyPeer) {
                                TLRPC.TL_notifyPeer notifyPeer = (TLRPC.TL_notifyPeer) update.peer;
                                long dialog_id;
                                if (notifyPeer.peer.user_id != 0) {
                                    dialog_id = notifyPeer.peer.user_id;
                                } else if (notifyPeer.peer.chat_id != 0) {
                                    dialog_id = -notifyPeer.peer.chat_id;
                                } else {
                                    dialog_id = -notifyPeer.peer.channel_id;
                                }
                                TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
                                if (dialog != null) {
                                    dialog.notify_settings = update.notify_settings;
                                }
                                if ((update.notify_settings.flags & 2) != 0) {
                                    editor.putBoolean("silent_" + dialog_id, update.notify_settings.silent);
                                } else {
                                    editor.remove("silent_" + dialog_id);
                                }
                                if ((update.notify_settings.flags & 4) != 0) {
                                    if (update.notify_settings.mute_until > currentTime1) {
                                        int until = 0;
                                        if (update.notify_settings.mute_until > currentTime1 + 60 * 60 * 24 * 365) {
                                            editor.putInt("notify2_" + dialog_id, 2);
                                            if (dialog != null) {
                                                update.notify_settings.mute_until = Integer.MAX_VALUE;
                                            }
                                        } else {
                                            until = update.notify_settings.mute_until;
                                            editor.putInt("notify2_" + dialog_id, 3);
                                            editor.putInt("notifyuntil_" + dialog_id, update.notify_settings.mute_until);
                                            if (dialog != null) {
                                                update.notify_settings.mute_until = until;
                                            }
                                        }
                                        getMessagesStorage().setDialogFlags(dialog_id, ((long) until << 32) | 1);
                                        getNotificationsController().removeNotificationsForDialog(dialog_id);
                                    } else {
                                        if (dialog != null) {
                                            update.notify_settings.mute_until = 0;
                                        }
                                        editor.putInt("notify2_" + dialog_id, 0);
                                        getMessagesStorage().setDialogFlags(dialog_id, 0);
                                    }
                                } else {
                                    if (dialog != null) {
                                        update.notify_settings.mute_until = 0;
                                    }
                                    editor.remove("notify2_" + dialog_id);
                                    getMessagesStorage().setDialogFlags(dialog_id, 0);
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
                                    editor.putInt("EnableGroup2", update.notify_settings.mute_until);
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
                                    editor.putInt("EnableAll2", update.notify_settings.mute_until);
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
                                    editor.putInt("EnableChannel2", update.notify_settings.mute_until);
                                }
                            }
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateChannel) {
                        final TLRPC.TL_updateChannel update = (TLRPC.TL_updateChannel) baseUpdate;
                        TLRPC.Dialog dialog = dialogs_dict.get(-(long) update.channel_id);
                        TLRPC.Chat chat = getChat(update.channel_id);
                        if (chat != null) {
                            if (dialog == null && chat instanceof TLRPC.TL_channel && !chat.left) {
                                Utilities.stageQueue.postRunnable(() -> getChannelDifference(update.channel_id, 1, 0, null));
                            } else if (chat.left && dialog != null && (proxyDialog == null || proxyDialog.id != dialog.id)) {
                                deleteDialog(dialog.id, 0);
                            }
                        }
                        updateMask |= UPDATE_MASK_CHAT;
                        loadFullChat(update.channel_id, 0, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateChatDefaultBannedRights) {
                        TLRPC.TL_updateChatDefaultBannedRights update = (TLRPC.TL_updateChatDefaultBannedRights) baseUpdate;
                        int chatId;
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
                        getMediaDataController().saveDraft(did, update.draft, null, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateReadFeaturedStickers) {
                        getMediaDataController().markFaturedStickersAsRead(false);
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
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !NotificationManagerCompat.from(ApplicationLoader.applicationContext).areNotificationsEnabled()) {
                                if (BuildVars.LOGS_ENABLED)
                                    FileLog.d("Ignoring incoming call because notifications are disabled in system");
                                continue;
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
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    ApplicationLoader.applicationContext.startForegroundService(intent);
                                } else {
                                    ApplicationLoader.applicationContext.startService(intent);
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
                        Theme.setThemeUploadInfo(null, theme, true);
                    }
                }
                if (editor != null) {
                    editor.commit();
                    getNotificationCenter().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                }
                getMessagesStorage().updateUsers(dbUsersStatus, true, true, true);
                getMessagesStorage().updateUsers(dbUsers, false, true, true);
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
                            long dialog_id = 0;
                            if (webpage instanceof TLRPC.TL_webPage || webpage instanceof TLRPC.TL_webPageEmpty) {
                                for (int a = 0, size2 = arrayList.size(); a < size2; a++) {
                                    arrayList.get(a).messageOwner.media.webpage = webpage;
                                    if (a == 0) {
                                        dialog_id = arrayList.get(a).getDialogId();
                                        ImageLoader.saveMessageThumbs(arrayList.get(a).messageOwner);
                                    }
                                    arr.add(arrayList.get(a).messageOwner);
                                }
                            } else {
                                array.put(webpage.id, arrayList);
                            }
                            if (!arr.isEmpty()) {
                                getMessagesStorage().putMessages(arr, true, true, false, getDownloadController().getAutodownloadMask(), i == 1);
                                getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList);
                            }
                        }
                    }
                }
            }

            boolean updateDialogs = false;
            if (messagesFinal != null) {
                for (int a = 0, size = messagesFinal.size(); a < size; a++) {
                    long key = messagesFinal.keyAt(a);
                    ArrayList<MessageObject> value = messagesFinal.valueAt(a);
                    updateInterfaceWithMessages(key, value, false);
                }
                updateDialogs = true;
            } else if (forceDialogsUpdate) {
                sortDialogs(null);
                updateDialogs = true;
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
                    long dialog_id = editingMessagesFinal.keyAt(b);
                    ArrayList<MessageObject> arrayList = editingMessagesFinal.valueAt(b);
                    MessageObject oldObject = dialogMessage.get(dialog_id);
                    if (oldObject != null) {
                        for (int a = 0, size2 = arrayList.size(); a < size2; a++) {
                            MessageObject newMessage = arrayList.get(a);
                            if (oldObject.getId() == newMessage.getId()) {
                                dialogMessage.put(dialog_id, newMessage);
                                if (newMessage.messageOwner.to_id != null && newMessage.messageOwner.to_id.channel_id == 0) {
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
                    getMediaDataController().loadReplyMessagesForMessages(arrayList, dialog_id, false);
                    getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList, false);
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
            if (channelViewsFinal != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.didUpdatedMessagesViews, channelViewsFinal);
            }
            if (updateMask != 0) {
                getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, updateMask);
            }
        });

        final SparseLongArray markAsReadMessagesInboxFinal = markAsReadMessagesInbox;
        final SparseLongArray markAsReadMessagesOutboxFinal = markAsReadMessagesOutbox;
        final ArrayList<Long> markAsReadMessagesFinal = markAsReadMessages;
        final SparseIntArray markAsReadEncryptedFinal = markAsReadEncrypted;
        final SparseArray<ArrayList<Integer>> deletedMessagesFinal = deletedMessages;
        final SparseArray<ArrayList<Integer>> scheduledDeletedMessagesFinal = scheduledDeletedMessages;
        final SparseIntArray clearHistoryMessagesFinal = clearHistoryMessages;
        getMessagesStorage().getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            int updateMask = 0;
            if (markAsReadMessagesInboxFinal != null || markAsReadMessagesOutboxFinal != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.messagesRead, markAsReadMessagesInboxFinal, markAsReadMessagesOutboxFinal);
                if (markAsReadMessagesInboxFinal != null) {
                    getNotificationsController().processReadMessages(markAsReadMessagesInboxFinal, 0, 0, 0, false);
                    SharedPreferences.Editor editor = notificationsPreferences.edit();
                    for (int b = 0, size = markAsReadMessagesInboxFinal.size(); b < size; b++) {
                        int key = markAsReadMessagesInboxFinal.keyAt(b);
                        int messageId = (int) markAsReadMessagesInboxFinal.valueAt(b);
                        TLRPC.Dialog dialog = dialogs_dict.get((long) key);
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
                        int key = markAsReadMessagesOutboxFinal.keyAt(b);
                        int messageId = (int) markAsReadMessagesOutboxFinal.valueAt(b);
                        TLRPC.Dialog dialog = dialogs_dict.get((long) key);
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
                    long dialog_id = (long) (key) << 32;
                    TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
                    if (dialog != null) {
                        MessageObject message = dialogMessage.get(dialog_id);
                        if (message != null && message.messageOwner.date <= value) {
                            message.setIsRead();
                            updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                        }
                    }
                }
            }
            if (markAsReadMessagesFinal != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.messagesReadContent, markAsReadMessagesFinal);
            }
            if (deletedMessagesFinal != null) {
                for (int a = 0, size = deletedMessagesFinal.size(); a < size; a++) {
                    int key = deletedMessagesFinal.keyAt(a);
                    ArrayList<Integer> arrayList = deletedMessagesFinal.valueAt(a);
                    if (arrayList == null) {
                        continue;
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.messagesDeleted, arrayList, key, false);
                    if (key == 0) {
                        for (int b = 0, size2 = arrayList.size(); b < size2; b++) {
                            Integer id = arrayList.get(b);
                            MessageObject obj = dialogMessagesByIds.get(id);
                            if (obj != null) {
                                obj.deleted = true;
                            }
                        }
                    } else {
                        MessageObject obj = dialogMessage.get((long) -key);
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
                    int key = scheduledDeletedMessagesFinal.keyAt(a);
                    ArrayList<Integer> arrayList = scheduledDeletedMessagesFinal.valueAt(a);
                    if (arrayList == null) {
                        continue;
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDeleted, arrayList, key, true);
                }
            }
            if (clearHistoryMessagesFinal != null) {
                for (int a = 0, size = clearHistoryMessagesFinal.size(); a < size; a++) {
                    int key = clearHistoryMessagesFinal.keyAt(a);
                    int id = clearHistoryMessagesFinal.valueAt(a);
                    long did = (long) -key;
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
        if (markAsReadMessagesInbox != null || markAsReadMessagesOutbox != null || markAsReadEncrypted != null || markAsReadMessages != null) {
            if (markAsReadMessagesInbox != null || markAsReadMessages != null) {
                getMessagesStorage().updateDialogsWithReadMessages(markAsReadMessagesInbox, markAsReadMessagesOutbox, markAsReadMessages, true);
            }
            getMessagesStorage().markMessagesAsRead(markAsReadMessagesInbox, markAsReadMessagesOutbox, markAsReadEncrypted, true);
        }
        if (markAsReadMessages != null) {
            getMessagesStorage().markMessagesContentAsRead(markAsReadMessages, getConnectionsManager().getCurrentTime());
        }
        if (deletedMessages != null) {
            for (int a = 0, size = deletedMessages.size(); a < size; a++) {
                final int key = deletedMessages.keyAt(a);
                final ArrayList<Integer> arrayList = deletedMessages.valueAt(a);
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = getMessagesStorage().markMessagesAsDeleted(arrayList, false, key, true, false);
                    getMessagesStorage().updateDialogsWithDeletedMessages(arrayList, dialogIds, false, key);
                });
            }
        }
        if (scheduledDeletedMessages != null) {
            for (int a = 0, size = scheduledDeletedMessages.size(); a < size; a++) {
                final int key = scheduledDeletedMessages.keyAt(a);
                final ArrayList<Integer> arrayList = scheduledDeletedMessages.valueAt(a);
                MessagesStorage.getInstance(currentAccount).markMessagesAsDeleted(arrayList, true, key, false, true);
            }
        }
        if (clearHistoryMessages != null) {
            for (int a = 0, size = clearHistoryMessages.size(); a < size; a++) {
                final int key = clearHistoryMessages.keyAt(a);
                final int id = clearHistoryMessages.valueAt(a);
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = getMessagesStorage().markMessagesAsDeleted(key, id, false, true);
                    getMessagesStorage().updateDialogsWithDeletedMessages(new ArrayList<>(), dialogIds, false, key);
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

    public boolean isDialogMuted(long dialog_id) {
        int mute_type = notificationsPreferences.getInt("notify2_" + dialog_id, -1);
        if (mute_type == -1) {
            return !getNotificationsController().isGlobalNotificationsEnabled(dialog_id);
        }
        if (mute_type == 2) {
            return true;
        } else if (mute_type == 3) {
            int mute_until = notificationsPreferences.getInt("notifyuntil_" + dialog_id, 0);
            if (mute_until >= getConnectionsManager().getCurrentTime()) {
                return true;
            }
        }
        return false;
    }

    private boolean updatePrintingUsersWithNewMessages(long uid, ArrayList<MessageObject> messages) {
        if (uid > 0) {
            ArrayList<PrintingUser> arr = printingUsers.get(uid);
            if (arr != null) {
                printingUsers.remove(uid);
                return true;
            }
        } else if (uid < 0) {
            ArrayList<Integer> messagesUsers = new ArrayList<>();
            for (MessageObject message : messages) {
                if (!messagesUsers.contains(message.messageOwner.from_id)) {
                    messagesUsers.add(message.messageOwner.from_id);
                }
            }

            ArrayList<PrintingUser> arr = printingUsers.get(uid);
            boolean changed = false;
            if (arr != null) {
                for (int a = 0; a < arr.size(); a++) {
                    PrintingUser user = arr.get(a);
                    if (messagesUsers.contains(user.userId)) {
                        arr.remove(a);
                        a--;
                        if (arr.isEmpty()) {
                            printingUsers.remove(uid);
                        }
                        changed = true;
                    }
                }
            }
            if (changed) {
                return true;
            }
        }
        return false;
    }

    protected void updateInterfaceWithMessages(final long uid, final ArrayList<MessageObject> messages, boolean scheduled) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        boolean isEncryptedChat = ((int) uid) == 0;
        MessageObject lastMessage = null;
        int channelId = 0;
        boolean updateRating = false;
        boolean hasNotOutMessage = false;
        if (!scheduled) {
            for (int a = 0; a < messages.size(); a++) {
                MessageObject message = messages.get(a);
                if (lastMessage == null || (!isEncryptedChat && message.getId() > lastMessage.getId() || (isEncryptedChat || message.getId() < 0 && lastMessage.getId() < 0) && message.getId() < lastMessage.getId()) || message.messageOwner.date > lastMessage.messageOwner.date) {
                    lastMessage = message;
                    if (message.messageOwner.to_id.channel_id != 0) {
                        channelId = message.messageOwner.to_id.channel_id;
                    }
                }
                if (!hasNotOutMessage && !message.isOut()) {
                    hasNotOutMessage = true;
                }
                if (message.isOut() && !message.isSending() && !message.isForwarded()) {
                    if (message.isNewGif()) {
                        getMediaDataController().addRecentGif(message.messageOwner.media.document, message.messageOwner.date);
                    } else if (!message.isAnimatedEmoji() && (message.isSticker() || message.isAnimatedSticker())) {
                        getMediaDataController().addRecentSticker(MediaDataController.TYPE_IMAGE, message, message.messageOwner.media.document, message.messageOwner.date, false);
                    }
                }
                if (message.isOut() && message.isSent()) {
                    updateRating = true;
                }
            }
        }
        getMediaDataController().loadReplyMessagesForMessages(messages, uid, scheduled);
        getNotificationCenter().postNotificationName(NotificationCenter.didReceiveNewMessages, uid, messages, scheduled);

        if (lastMessage == null || scheduled) {
            return;
        }
        TLRPC.TL_dialog dialog = (TLRPC.TL_dialog) dialogs_dict.get(uid);
        if (lastMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            if (dialog != null) {
                allDialogs.remove(dialog);
                dialogsServerOnly.remove(dialog);
                dialogsCanAddUsers.remove(dialog);
                dialogsChannelsOnly.remove(dialog);
                dialogsGroupsOnly.remove(dialog);
                dialogsUsersOnly.remove(dialog);
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
                dialogMessagesByIds.remove(dialog.top_message);
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = 0;
                getNotificationsController().removeNotificationsForDialog(dialog.id);
                getNotificationCenter().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
            }
            return;
        }

        boolean changed = false;

        if (dialog == null) {
            TLRPC.Chat chat = getChat(channelId);
            if (channelId != 0 && chat == null || chat != null && ChatObject.isNotInChat(chat)) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("not found dialog with id " + uid + " dictCount = " + dialogs_dict.size() + " allCount = " + allDialogs.size());
            }
            dialog = new TLRPC.TL_dialog();
            dialog.id = uid;
            dialog.unread_count = 0;
            dialog.top_message = lastMessage.getId();
            dialog.last_message_date = lastMessage.messageOwner.date;
            dialog.flags = ChatObject.isChannel(chat) ? 1 : 0;
            dialogs_dict.put(uid, dialog);
            allDialogs.add(dialog);
            dialogMessage.put(uid, lastMessage);
            if (lastMessage.messageOwner.to_id.channel_id == 0) {
                dialogMessagesByIds.put(lastMessage.getId(), lastMessage);
                if (lastMessage.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.put(lastMessage.messageOwner.random_id, lastMessage);
                }
            }
            changed = true;

            //int offset = nextDialogsCacheOffset.get(dialog.folder_id, 0) + 1;
            //nextDialogsCacheOffset.put(dialog.folder_id, offset);

            TLRPC.Dialog dialogFinal = dialog;
            getMessagesStorage().getDialogFolderId(uid, param -> {
                if (param != -1) {
                    if (param != 0) {
                        dialogFinal.folder_id = param;
                        sortDialogs(null);
                        getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload, true);
                    }
                } else {
                    int lowerId = (int) uid;
                    if (lowerId != 0) {
                        loadUnknownDialog(getInputPeer(lowerId), 0);
                    }
                }
            });
        } else {
            if (hasNotOutMessage && dialog.folder_id == 1 && !isDialogMuted(dialog.id)) {
                dialog.folder_id = 0;
                dialog.pinned = false;
                dialog.pinnedNum = 0;
                getMessagesStorage().setDialogsFolderId(null, null, dialog.id, 0);
                changed = true;
            }
            if ((dialog.top_message > 0 && lastMessage.getId() > 0 && lastMessage.getId() > dialog.top_message) ||
                    (dialog.top_message < 0 && lastMessage.getId() < 0 && lastMessage.getId() < dialog.top_message) ||
                    dialogMessage.indexOfKey(uid) < 0 || dialog.top_message < 0 || dialog.last_message_date <= lastMessage.messageOwner.date) {
                MessageObject object = dialogMessagesByIds.get(dialog.top_message);
                dialogMessagesByIds.remove(dialog.top_message);
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = lastMessage.getId();
                dialog.last_message_date = lastMessage.messageOwner.date;
                changed = true;
                dialogMessage.put(uid, lastMessage);
                if (lastMessage.messageOwner.to_id.channel_id == 0) {
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
            getMediaDataController().increasePeerRaiting(uid);
        }
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

    public void sortDialogs(SparseArray<TLRPC.Chat> chatsDict) {
        dialogsServerOnly.clear();
        dialogsCanAddUsers.clear();
        dialogsChannelsOnly.clear();
        dialogsGroupsOnly.clear();
        dialogsUsersOnly.clear();
        dialogsForward.clear();
        for (int a = 0; a < dialogsByFolder.size(); a++) {
            ArrayList<TLRPC.Dialog> arrayList = dialogsByFolder.valueAt(a);
            if (arrayList != null) {
                arrayList.clear();
            }
        }
        unreadUnmutedDialogs = 0;
        boolean selfAdded = false;
        int selfId = getUserConfig().getClientUserId();
        Collections.sort(allDialogs, dialogComparator);
        isLeftProxyChannel = true;
        if (proxyDialog != null && proxyDialog.id < 0) {
            TLRPC.Chat chat = getChat(-(int) proxyDialog.id);
            if (chat != null && !chat.left) {
                isLeftProxyChannel = false;
            }
        }
        boolean countMessages = getNotificationsController().showBadgeMessages;
        for (int a = 0, N = allDialogs.size(); a < N; a++) {
            TLRPC.Dialog d = allDialogs.get(a);
            int high_id = (int) (d.id >> 32);
            int lower_id = (int) d.id;
            if (d instanceof TLRPC.TL_dialog) {
                boolean canAddToForward = true;
                if (lower_id != 0 && high_id != 1) {
                    dialogsServerOnly.add(d);
                    if (DialogObject.isChannel(d)) {
                        TLRPC.Chat chat = getChat(-lower_id);
                        if (chat != null && (chat.megagroup && (chat.admin_rights != null && (chat.admin_rights.post_messages || chat.admin_rights.add_admins)) || chat.creator)) {
                            dialogsCanAddUsers.add(d);
                        }
                        if (chat != null && chat.megagroup) {
                            dialogsGroupsOnly.add(d);
                        } else {
                            dialogsChannelsOnly.add(d);
                            canAddToForward = ChatObject.hasAdminRights(chat) && ChatObject.canPost(chat);
                        }
                    } else if (lower_id < 0) {
                        if (chatsDict != null) {
                            TLRPC.Chat chat = chatsDict.get(-lower_id);
                            if (chat != null && chat.migrated_to != null) {
                                allDialogs.remove(a);
                                a--;
                                N--;
                                continue;
                            }
                        }
                        dialogsCanAddUsers.add(d);
                        dialogsGroupsOnly.add(d);
                    } else if (lower_id > 0 && lower_id != selfId) {
                        dialogsUsersOnly.add(d);
                    }
                }
                if (canAddToForward && d.folder_id == 0) {
                    if (lower_id == selfId) {
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
            if (proxyDialog != null && d.id == proxyDialog.id && isLeftProxyChannel) {
                allDialogs.remove(a);
                a--;
                N--;
                continue;
            }
            addDialogToItsFolder(-1, d, countMessages);
        }
        if (proxyDialog != null && isLeftProxyChannel) {
            allDialogs.add(0, proxyDialog);
            addDialogToItsFolder(-2, proxyDialog, countMessages);
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

    private void addDialogToItsFolder(int index, TLRPC.Dialog dialog, boolean countMessages) {
        int folderId;
        if (dialog instanceof TLRPC.TL_dialogFolder) {
            folderId = 0;
            dialog.unread_count = 0;
            dialog.unread_mentions_count = 0;
        } else {
            folderId = dialog.folder_id;
        }
        ArrayList<TLRPC.Dialog> dialogs = dialogsByFolder.get(folderId);
        if (dialogs == null) {
            dialogs = new ArrayList<>();
            dialogsByFolder.put(folderId, dialogs);
        }
        if (folderId != 0 && dialog.unread_count != 0) {
            TLRPC.Dialog folder = dialogs_dict.get(DialogObject.makeFolderDialogId(folderId));
            if (folder != null) {
                if (countMessages) {
                    if (isDialogMuted(dialog.id)) {
                        folder.unread_count += dialog.unread_count;
                    } else {
                        folder.unread_mentions_count += dialog.unread_count;
                    }
                } else {
                    if (isDialogMuted(dialog.id)) {
                        folder.unread_count++;
                    } else {
                        folder.unread_mentions_count++;
                    }
                }
            }
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
            if ("all".equals(reason.platform) || "android".equals(reason.platform)) {
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

    public boolean checkCanOpenChat(final Bundle bundle, final BaseFragment fragment, MessageObject originalMessage) {
        if (bundle == null || fragment == null) {
            return true;
        }
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        int user_id = bundle.getInt("user_id", 0);
        int chat_id = bundle.getInt("chat_id", 0);
        int messageId = bundle.getInt("message_id", 0);
        if (user_id != 0) {
            user = getUser(user_id);
        } else if (chat_id != 0) {
            chat = getChat(chat_id);
        }
        if (user == null && chat == null) {
            return true;
        }
        String reason = null;
        if (chat != null) {
            reason = getRestrictionReason(chat.restriction_reason);
        } else if (user != null) {
            reason = getRestrictionReason(user.restriction_reason);
        }
        if (reason != null) {
            showCantOpenAlert(fragment, reason);
            return false;
        }
        if (messageId != 0 && originalMessage != null && chat != null && chat.access_hash == 0) {
            int did = (int) originalMessage.getDialogId();
            if (did != 0) {
                final AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), 3);
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
                final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
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
                    if (fragment != null) {
                        fragment.setVisibleDialog(null);
                    }
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
        String reason = null;
        if (chat != null) {
            reason = getRestrictionReason(chat.restriction_reason);
        } else if (user != null) {
            reason = getRestrictionReason(user.restriction_reason);
            if (user.bot) {
                type = 1;
                closeLast = true;
            }
        }
        if (reason != null) {
            showCantOpenAlert(fragment, reason);
        } else {
            Bundle args = new Bundle();
            if (chat != null) {
                args.putInt("chat_id", chat.id);
            } else {
                args.putInt("user_id", user.id);
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

    public void openByUserName(String username, final BaseFragment fragment, final int type) {
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
            final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(fragment.getParentActivity(), 3)};

            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
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
                    if (fragment != null && fragment.getParentActivity() != null) {
                        try {
                            Toast.makeText(fragment.getParentActivity(), LocaleController.getString("NoUsernameFound", R.string.NoUsernameFound), Toast.LENGTH_SHORT).show();
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
}
